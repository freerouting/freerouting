# Freerouting Score Calculation Revision & Versioning Plan

**Document Status:** Research & Implementation Plan  
**Date:** September 2026  
**Author:** Freerouting AI Assistant & Core Engineering Team  
**Target Delivery:** Post-Optimizer Unification Roadmap  

---

## 1. Motivation & Problem Statement

In the current Freerouting score formula, all penalties and costs are normalized against a fixed maximum:

$$\text{maximumScore} = N_{\text{connections}} \times \text{unroutedNetPenalty}$$

where `DEFAULT_UNROUTED_NET_PENALTY = 5_000_000.0F`.

On a fully-routed board ($N_{\text{unrouted}} = 0$) with zero clearance violations ($N_{\text{violations}} = 0$), the remaining cost terms are:
- `viaCosts` $\approx 50$ per via
- `defaultPreferredDirectionTraceCost` $\approx 1.0$ per mm

These cost values are **4 to 6 orders of magnitude smaller** than the penalty headroom. As a consequence:
1. Every fully-routed, DRC-clean board scores **$\approx 1000.00$** regardless of how many vias it has or how long its traces are.
2. An optimizer pass that removes 10 vias or shrinks wire length by 15% produces a score improvement in the range of $+0.0001\%$, which rounds away at typical display precisions and can fall below float accuracy.
3. The post-routing optimizer terminates prematurely because it cannot register a score improvement above standard convergence thresholds (e.g. $1.0\%$).

We need a revised score calculation that **makes wire length and via count reductions measurably visible**, while preserving the fundamental hierarchy:
$$\text{Unrouted Item Count} \gg \text{Clearance Violations} \text{ (and violation length)} \gg \text{Via Count} > \text{Trace Length}$$

---

## 2. Versioning & Historical Score Comparability

A primary constraint is **historical score comparability**:
Thousands of benchmark runs (DAC 2020, PCBench, issue fixtures) and telemetry records from earlier Freerouting releases (v1.9, v2.1, v2.3, v2.4) exist with historical scores. Changing the formula unconditionally would break regression comparisons against older baselines.

### 2.1 Explicit Score Versioning Enum

We introduce an explicit scoring version enum in `app.freerouting.settings.ScoringVersion`:

```java
package app.freerouting.settings;

public enum ScoringVersion {
  /**
   * Legacy score formula (Freerouting <= v2.4):
   * MaxScore = N_connections * 5,000,000.
   * Collapses to 1000.00 for all fully-routed, DRC-clean boards.
   */
  V1_LEGACY,

  /**
   * Layered score formula (Freerouting v2.5+):
   * Component 1: Completion & DRC score (0 to 800)
   * Component 2: Quality bonus for via and trace optimization (0 to 200)
   * Supports bidirectional conversion / back-projection to V1.
   */
  V2_LAYERED
}
```

### 2.2 Configuration & Settings Integration

In `ScoringSettings.java`:
- `@SerializedName("scoring_version") public ScoringVersion scoringVersion;` (default `V2_LAYERED` in `DefaultSettings`).
- CLI argument: `--scoring-version v1` or `-sv v1` allows running benchmarks in legacy comparability mode.
- REST API / Job settings support passing `"scoring_version": "V1_LEGACY"`.

### 2.3 Dual Score Reporting & Back-Projection

To ensure historical logs and benchmark spreadsheets remain comparable without manual conversion:
1. `BoardStatistics` will provide both representations:
   - `getNormalizedScore(ScoringSettings)`: Returns the score using the active `ScoringVersion` (defaults to V2).
   - `getLegacyNormalizedScore(ScoringSettings)`: Always computes and returns the V1 score.
   - `getLayeredNormalizedScore(ScoringSettings)`: Always computes and returns the V2 score.
2. In `RoutingResultManifest`, telemetry payloads, and summary logs:
   - `score`: Active version score.
   - `score_v1_legacy`: Explicit legacy score for 1-to-1 comparison with historical v1.9 / v2.3 / v2.4 benchmark runs.
   - `score_v2_layered`: Explicit layered score showing completion + quality breakdown.
3. Telemetry and logger output will format:
   `"score: 842.50 [v2] (legacy v1: 999.98, unrouted: 0, violations: 0)"`

---

## 3. Mathematical Formulation (Scoring V2: Layered Model)

The normalized score is partitioned into two additive components:

$$\boxed{\text{score}_{\text{v2}} = \text{completionScore} + \text{qualityBonus}}$$

Bounded strictly to $[0, 1000.0]$.

### 3.1 Component 1: Completion & DRC Score ($0 \le \text{completionScore} \le 800$)

Encodes routability and design rule correctness. It occupies the majority of the scale ($0$ to $800$ points).

$$\text{completionScore} = 800 \times \left(1 - \frac{N_{\text{unrouted}}}{N_{\text{conn}}}\right) \times \left(1 - \min\left(1.0, \frac{L_{\text{violation\_mm}}}{L_{\text{violation\_cap}}}\right)\right)$$

Where:
- $N_{\text{unrouted}} / N_{\text{conn}}$ is the fraction of incomplete connections. Any unrouted net knocks out a substantial portion of the 800 points.
- $L_{\text{violation\_mm}}$ is the **sum of clearance violation penetration lengths in millimeters** across all violating item pairs (rather than a simple discrete violation count).
  - *Physical rationale:* A $0.005\text{ mm}$ slight touch is penalized far less than a $3.5\text{ mm}$ overlapping trace.
  - In the event violation length is unavailable (e.g. fast pass without detailed geometry clipping), fallback is:
    $$\min\left(1.0, \frac{N_{\text{violations}} \times \text{penalty}_{\text{drc}}}{800}\right)$$
- $L_{\text{violation\_cap}}$ is a normalizing saturation ceiling (default $50.0\text{ mm}$).

### 3.2 Component 2: Quality Bonus ($0 \le \text{qualityBonus} \le 200$)

Rewards trace length and via reduction. **Strict prerequisite:** The quality bonus is strictly zero if $N_{\text{unrouted}} > 0$ or $N_{\text{violations}} > 0$. Quality considerations cannot compensate for incomplete routing or clearance failures.

$$\text{qualityBonus} = \begin{cases} 
0 & \text{if } N_{\text{unrouted}} > 0 \text{ or } N_{\text{violations}} > 0 \\
200 \times \left( w_v \cdot \Delta_{\text{via}} + w_t \cdot \Delta_{\text{trace}} \right) & \text{otherwise}
\end{cases}$$

Where:
- Reference baselines ($V_{\text{ref}}$, $T_{\text{ref}}$) are captured at the end of the initial routing stage (before optimization passes begin).
- Relative via improvement: $\Delta_{\text{via}} = \max\left(0.0, \frac{V_{\text{ref}} - N_{\text{vias}}}{V_{\text{ref}}}\right)$
- Relative trace length improvement: $\Delta_{\text{trace}} = \max\left(0.0, \frac{T_{\text{ref}} - L_{\text{traces\_mm}}}{T_{\text{ref}}}\right)$
- Default weights: $w_v = 0.65$ (via reduction priority), $w_t = 0.35$ (trace length reduction priority), where $w_v + w_t = 1.0$.

### 3.3 Theoretical & Real-World Score Mapping

| Board Routing State | Legacy V1 Score | Revised V2 Score | Interpretation |
|---|---|---|---|
| Unrouted board (0% routed) | 0.00 | 0.00 | Complete unrouted baseline |
| 50% routed, 0 violations | ~500.00 | ~400.00 | Routing in progress |
| 100% routed, 2 DRC violations ($0.4\text{ mm}$) | ~999.90 | ~793.60 | High completion, DRC issues block quality bonus |
| 100% routed, 0 violations (Pre-Optimizer) | 1000.00 | 800.00 | Complete and clean routing baseline |
| Post-Optimizer: 10% vias eliminated | 1000.00 | 813.00 | Measurable optimizer progress (+1.6%) |
| Post-Optimizer: 25% vias + 8% trace length reduced | 1000.00 | 838.10 | Substantial optimization (+4.7%) |
| Asymptotic Perfect Theoretical Board | 1000.00 | 1000.00 | Ideal minimum copper / via ceiling |

---

## 4. Priority Invariants & Hierarchy Proofs

1. **Completion Dominance ($P_1$):**
   A board with 1 unrouted net out of 20 has a completion score of $\le \frac{19}{20} \times 800 = 760.00$, and quality bonus $= 0$. Total score $\le 760.00$.
   Any fully routed, clean board starts at $800.00$. Therefore, **completing all nets always beats any amount of optimization**.
2. **DRC Dominance ($P_2$):**
   A board with 1 clearance violation has quality bonus $= 0$ and completion score $< 800.00$. Clean boards start at $800.00 + \text{bonus} \ge 800.00$. Therefore, **fixing a DRC violation always beats optimizing wires with violations present**.
3. **Quality Visibility ($P_3$):**
   On a clean board, eliminating 5 vias on a 40-via design yields:
   $$\Delta \text{score} = 200 \times 0.65 \times \frac{5}{40} = +16.25 \text{ points}$$
   $16.25$ points out of $800.00$ is a **$+2.03\%$ relative improvement**. This cleanly exceeds standard optimizer termination thresholds ($1.0\%$), enabling multi-pass optimization loops to detect meaningful improvements and continue converging.

---

## 5. Architectural Implementation Plan

### Phase 1: Settings & Enum Additions
- [ ] Create `app.freerouting.settings.ScoringVersion` enum (`V1_LEGACY`, `V2_LAYERED`).
- [ ] In `ScoringSettings`:
  - Add `scoringVersion` field.
  - Add `completionMaxScore` (default 800.0f).
  - Add `qualityBonusMaxScore` (default 200.0f).
  - Add `qualityViaWeight` (default 0.65f).
  - Add `qualityTraceWeight` (default 0.35f).
  - Add `violationLengthCapMm` (default 50.0f).
- [ ] In `DefaultSettings`: Register defaults.

### Phase 2: Board Statistics Extension
- [ ] In `BoardStatisticsClearanceViolations`:
  - Add `public float totalLengthMm;`
  - Populate during DRC check by summing overlap/penetration distances.
- [ ] In `BoardStatistics`:
  - Implement `calculateScoreV1Legacy(ScoringSettings)`.
  - Implement `calculateScoreV2Layered(ScoringSettings)`.
  - Dispatch in `getNormalizedScore(ScoringSettings)` based on `scoringSettings.scoringVersion`.
  - Implement helper accessors `getLegacyNormalizedScore()` and `getLayeredNormalizedScore()`.

### Phase 3: Routing Pipeline Reference Capture
- [ ] In `RoutingPipeline`:
  - After `autorouter.runBatchLoop()` and `board.finishAutoroute()`, record:
    - `job.routerSettings.scoring.referenceViaCount = stats.items.viaCount;`
    - `job.routerSettings.scoring.referenceTraceLengthMm = stats.traces.totalLengthMm;`
  - If optimizer runs standalone without previous autoroute stage, establish reference from the incoming board state prior to pass 1.

### Phase 4: Telemetry & Manifest Integration
- [ ] Update `RoutingResultManifest` to serialize both `score` (active version) and `scoreV1Legacy`.
- [ ] In `FRLogger` / job completion summaries: Include both scores when V2 is active for human cross-reference.

### Phase 5: Test Suite & Fixture Adaptation
- [ ] Add `ScoreCalculationVersionTest`:
  - Validates V1 vs V2 behavior on known fixture boards.
  - Tests mathematical monotonicity: unrouted > violation > clean > optimized.
- [ ] Ensure existing fixture tests asserting `exactIncompleteConnections(0)` continue to pass without hardcoding rigid float scores (or configure test settings with `ScoringVersion.V1_LEGACY` where historical score matching is explicitly asserted).

---

## 6. Acceptance Criteria

1. **Deterministic & Monotonic:** For identical inputs, score output is identical across threads.
2. **Backward Compatibility:** When `scoringVersion = V1_LEGACY`, score output is byte-identical to pre-revision Freerouting releases.
3. **No False Ceilings:** Clean fully-routed boards score $800.00 + \text{bonus} < 1000.00$, leaving visible headroom for optimizer improvements.
4. **Zero-Violation Invariant:** A board with any unrouted connection or clearance violation strictly scores $< 800.00$.
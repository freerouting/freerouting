# Freerouting Score Calculation Revision & Versioning Plan

**Document Status:** Research & Implementation Plan  
**Date:** September 2026 (Revised)  
**Author:** Freerouting AI Assistant & Core Engineering Team  
**Target Delivery:** Post-Optimizer Unification Roadmap  

---

## 1. Motivation & Core Architectural Pivot

Historically, Freerouting attempted to use a single monolithic score equation across both the **Router** and the **Optimizer**:

$$\text{score} = \frac{\max(0,\ M - P_{\text{unrouted}} - P_{\text{violations}} - P_{\text{bends}} - C_{\text{traces}} - C_{\text{vias}})}{M} \times 1000$$

This coupled two fundamentally different optimization regimes into one broken metric:

1. **The Router's Objective:** Topological routability and design rule correctness. It must connect 100% of airwires and eliminate all clearance violations. Trace length and via counts are tertiary noise during maze expansion. In the legacy formula, because $M = N_{\text{conn}} \times 5{,}000{,}000$, the wire length and via terms were negligible ($\approx 0.0001\%$), yet their presence mathematically prevented a clean 100% completion board from cleanly reading as fully resolved.
2. **The Optimizer's Objective:** Post-routing geometric refinement. The board enters the optimizer already routed. The optimizer does **not** route new connections; its sole job is to **minimize via count** and **shorten trace length** while **strictly maintaining or reducing clearance violations to zero**. When evaluated under the router's formula, the optimizer's progress is invisible because the score sits permanently pinned at $\approx 1000.00$.

### The Architectural Decision: Stage-Dedicated Scoring Separation

We decouple the scoring systems into two distinct, specialized evaluators:

1. **Router Score (Topology & DRC):** Evaluates connection completion and clearance violations. Trace length and via count are completely removed from this score. A fully routed, violation-free board achieves **1000.00 / 1000.00**.
2. **Optimizer Score (Quality & Efficiency):** Evaluates post-routing wire length and via optimization. Unrouted items are irrelevant (the router handles connectivity). It treats clearance violations as a strict gating condition (clearance violation increase $\implies$ instant rejection), and scores purely on **total via count** and **total wire length**.

---

## 2. Router Score Specification (Pure Routability & DRC)

The Router score focuses exclusively on routability and design rules.

### 2.1 Formula

$$\boxed{\text{score}_{\text{router}} = \max\left(0.0,\ 1000.0 \times \left(1.0 - \frac{N_{\text{unrouted}}}{N_{\text{conn}}}\right) - P_{\text{drc}}\right)}$$

Where:
- $N_{\text{unrouted}}$ is the number of incomplete connections.
- $N_{\text{conn}}$ is the total connection count on the board.
- $P_{\text{drc}}$ is the clearance violation penalty:
  $$P_{\text{drc}} = N_{\text{violations}} \times \text{penalty}_{\text{per\_violation}} + \min\left(200.0, L_{\text{violation\_mm}} \times \text{penalty}_{\text{length}}\right)$$
  - Any clearance violation immediately drops the board below $1000.00$.
- **Removed from Router Score:**
  - `traces.totalLength` / `traces.totalLengthMm` $\implies$ completely removed.
  - `vias.totalCount` $\implies$ completely removed.
  - `bends.totalCount` $\implies$ completely removed.

### 2.2 Key Properties
- **Exact Milestone Scores:**
  - 0% routed $\implies \mathbf{0.00}$
  - 50% routed, 0 violations $\implies \mathbf{500.00}$
  - 100% routed, 0 violations $\implies \mathbf{1000.00}$
- Any board with unrouted items or clearance violations is strictly $< 1000.00$.
- No trace length or via variance will ever obscure routing completion progress.

---

## 3. Optimizer Score Specification (Via & Wire Length Quality)

The Optimizer operates on a board to improve electrical and manufacturing quality.

### 3.1 Gating Rules (DRC Invariant)
The optimizer's primary directive is: **Do no harm.**
1. **Unrouted Net Invariant:** An optimizer step or pass must never create an unrouted connection. If an item fails to reroute, the transaction is aborted.
2. **DRC Non-Regression Invariant:**
   $$\text{violations}_{\text{candidate}} \le \text{violations}_{\text{baseline}}$$
   If candidate routing introduces even a single new clearance violation, the candidate score is $-\infty$ (rejected immediately).

### 3.2 Reference Baselines & Formula
When the optimizer starts (at pass 0 / initial board state), it captures reference figures from the incoming board:
- $V_{\text{ref}} = \text{vias.totalCount}$
- $L_{\text{ref}} = \text{traces.totalLengthMm}$

The Optimizer Score evaluates relative improvement on a clean $0$ to $1000.0$ scale:

$$\boxed{\text{score}_{\text{opt}} = 500.0 \times \left(1.0 + w_v \cdot \Delta_{\text{vias}} + w_t \cdot \Delta_{\text{trace}}\right)}$$

Where:
- $\Delta_{\text{vias}} = \frac{V_{\text{ref}} - V_{\text{current}}}{V_{\text{ref}}}$ (relative via reduction)
- $\Delta_{\text{trace}} = \frac{L_{\text{ref}} - L_{\text{current}}}{L_{\text{ref}}}$ (relative wire length reduction)
- Default weights: $w_v = 0.60$ (via reduction priority), $w_t = 0.40$ (wire length reduction priority), where $w_v + w_t = 1.0$.

### 3.3 Dynamic Range & Threshold Behavior
- **Baseline Board (Incoming from Router):**
  $\Delta_{\text{vias}} = 0$, $\Delta_{\text{trace}} = 0 \implies \mathbf{score = 500.00}$.
- **Optimized Board (e.g. 20% via reduction, 5% length reduction):**
  $$\text{score} = 500.0 \times (1.0 + 0.60 \times 0.20 + 0.40 \times 0.05) = 500.0 \times (1.0 + 0.12 + 0.02) = \mathbf{570.00}$$
  Improvement: $\frac{570 - 500}{500} = \mathbf{+14.0\%}$ relative progress!
- **Benefits for Convergence:**
  - Pass improvements are now in the healthy range of $0.5\%$ to $15.0\%$, making convergence thresholds like `-oit 1.0%` or `-oit 5.0%` work reliably and predictably.
  - The optimizer never terminates immediately due to false "1000.00 max score reached" clamping.
  - If a pass degrades either vias or length without compensating, score drops below baseline and `bestBoard` restoration triggers.

---

## 4. Versioning & Backward Compatibility

Because automated scripts, CI benchmarks, and historical logs compare scores against Freerouting v1.9–v2.4, scoring versioning is supported:

### 4.1 Scoring Version Enumeration

```java
package app.freerouting.settings;

public enum ScoringVersion {
  /**
   * Legacy monolithic scoring (<= v2.4):
   * Shared formula for router and optimizer normalized to 1000 via connection penalty.
   */
  V1_LEGACY,

  /**
   * Decoupled scoring (v2.5+):
   * Router uses pure routability/DRC score.
   * Optimizer uses dedicated via/wire quality score.
   */
  V2_DECOUPLED
}
```

### 4.2 Configuration
- Settings key: `scoring.scoring_version` (default: `V2_DECOUPLED`).
- CLI flag: `--scoring-version v1` (alias `-sv v1`) for running historical baseline comparisons.
- In `BoardStatistics`:
  - `getRouterScore(ScoringSettings)`: Router completion score.
  - `getOptimizerScore(OptimizerScoringReference, ScoringSettings)`: Optimizer quality score.
  - `getLegacyNormalizedScore(ScoringSettings)`: Legacy monolithic formula.
  - `getNormalizedScore(ScoringSettings)`: Dispatches to active version logic.

### 4.3 Manifest & Telemetry Output
Result manifests (`RoutingResultManifest`) and JSON logs will explicitly report both values:
```json
{
  "routing_score": 1000.0,
  "optimization_score": 582.4,
  "legacy_score_v1": 999.98,
  "unrouted_count": 0,
  "clearance_violations": 0,
  "via_count": 42,
  "trace_length_mm": 1845.2
}
```

---

## 5. Architectural Implementation Steps

### Phase 1: Settings & Data Structures
- [ ] Add `ScoringVersion` enum (`V1_LEGACY`, `V2_DECOUPLED`).
- [ ] Add `OptimizerScoringSettings` (or nest in `OptimizerSettings`):
  - `qualityViaWeight` (default 0.60).
  - `qualityTraceWeight` (default 0.40).
  - `baselineScore` (default 500.0f).
- [ ] Add `OptimizerScoringReference` data record:
  - Holds `referenceViaCount`, `referenceTraceLengthMm`, `referenceViolations`.

### Phase 2: Engine Scoring Decoupling
- [ ] In `BatchAutorouter` / `AutorouteBatchLoop`:
  - Score board using `BoardStatistics.getRouterScore()`.
  - Checkpoint and detect progress using pure routability + DRC.
- [ ] In `BatchOptimizer`:
  - At stage start, initialize `OptimizerScoringReference` from incoming board.
  - Score candidate passes using `BoardStatistics.getOptimizerScore()`.
  - Early-exit check verifies if pass improvement $< \text{threshold}$ against the optimizer scale.

### Phase 3: Manifest, Logging & Event Bridge
- [ ] In `RoutingJob`:
  - Maintain `job.routerScore` and `job.optimizerScore`.
- [ ] In `FRLogger`:
  - Router logs: `"Auto-routing pass #N completed with router score 985.20 (2 unrouted, 0 violations)"`.
  - Optimizer logs: `"Optimizer pass #N completed with optimizer score 534.50 (+6.9% quality improvement, 38 vias [-4], 1420mm [-35mm])"`.

### Phase 4: Validation & Testing
- [ ] `RouterScoringTest`:
  - Asserts router score is 1000.00 for clean fully-routed boards regardless of via count or length.
  - Asserts unrouted nets or violations strictly decrease score.
- [ ] `OptimizerScoringTest`:
  - Asserts incoming baseline scores exactly 500.00.
  - Asserts removing vias or shortening traces monotonically increases score.
  - Asserts any new clearance violation invalidates the candidate.
- [ ] `ScoringBackwardCompatibilityTest`:
  - Asserts `V1_LEGACY` yields exact historical scores.
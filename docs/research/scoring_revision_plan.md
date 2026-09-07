# Freerouting Dedicated Scoring Architecture & Versioning Plan

**Document Status:** Approved Architecture Plan  
**Date:** September 2026 (Revised with Benchmarks Calibration & Theoretical Lower Bounds)  
**Author:** Freerouting AI Assistant & Core Engineering Team  
**Target Delivery:** Post-Optimizer Unification Roadmap  

---

## 1. Executive Summary & Evaluation of Requirements

We evaluate the two revised core requirements and confirm their mathematical and algorithmic soundness:

### 1.1 Requirement 1: Router Score Revision & Versioning
- **Requirement:** The router score incorporates three primary terms:
  1. `unrouted_connections` (count of unconnected airlines)
  2. `clearance_violations` (discrete count of DRC violation pairs)
  3. `total_violation_um` (cumulative clearance violation penetration depth in micrometers)
- **Evaluation & Verdict:** **Strongly Approved with Versioning.**
  - Incorporating `total_violation_um` directly bridges the gap between discrete DRC counts and physical severity: a $1\,\mu\text{m}$ border touch is penalized proportionally less than a $250\,\mu\text{m}$ copper overlap.
  - Because `total_violation_um` introduces a continuous geometric magnitude not present in releases $\le$ v2.4, this constitutes a **substantive change** to the router's numerical scale.
  - **Explicit scoring versioning (`ROUTER_V1_LEGACY` vs `ROUTER_V2_CONTINUOUS`) is therefore necessary and justified** to preserve regression comparability with historical PCBench and DAC2020 run results.

### 1.2 Requirement 2: Optimizer Score via Theoretical Lower Bounds (0 .. 1000)
- **Requirement:** The optimizer score represents distance from a theoretical "perfect board" ($1000.00$), where:
  - $L_{\text{min}}$ = Theoretical minimum wire length (e.g. Euclidean/Manhattan Minimum Spanning Tree / HPWL).
  - $V_{\text{min}}$ = Theoretical minimum via count (e.g. 0 vias for planar single-layer reachable nets, or $0$ for SMD-to-SMD same-layer).
  - $B_{\text{min}}$ = Theoretical minimum bend count (e.g. 0 bends for collinear pins, 1 bend for $L$-routes, 2 for $Z$-routes).
  - Each excess unit ($L - L_{\text{min}}$, $V - V_{\text{min}}$, $B - B_{\text{min}}$) incurs a calibrated penalty deducted from $1000.00$.
- **Evaluation & Verdict:** **Brilliant & Algorithmic Best Practice.**
  - In EDA routing research, normalizing against an absolute unroutable lower bound (such as Minimum Spanning Tree wirelength or Half-Perimeter Wirelength HPWL) is standard practice because it establishes an **invariant absolute scale**:
    - $1000.00$ = The ideal theoretical board (Euclidean straight lines, zero vias, zero doglegs).
    - $850.00$ = A high-quality industrial layout (typically $1.2\times$ to $1.4\times$ MST length with minimal necessary layer transitions).
    - $500.00$ = A cluttered routing with excessive detours and redundant via transitions.
  - Because $L_{\text{min}}$, $V_{\text{min}}$, and $B_{\text{min}}$ are computed directly from the pin placements in the board geometry, this score does **not** shift during optimization passes and provides an absolute quality metric.

### 1.3 Calibration Strategy: Empirical Derivation via `benchmarks.json`
- Rather than guessing magic weights, the coefficients for wire length ($\lambda_L$), via count ($\lambda_V$), and bend count ($\lambda_B$) as well as DRC penalties will be **statistically calibrated** from the empirical distributions in `scripts/benchmark/results/benchmarks.json` across thousands of historical runs.

---

## 2. Router Score Specification (ROUTER_V2)

The Router score evaluates topological completeness and physical design-rule clearance integrity.

### 2.1 Theoretical Formulation

$$\boxed{\text{score}_{\text{router}} = \max\left(0.0,\ 1000.0 - \text{penalty}_{\text{unrouted}} - \text{penalty}_{\text{drc}}\right)}$$

#### Unrouted Penalty:
$$\text{penalty}_{\text{unrouted}} = 1000.0 \times \left(\frac{N_{\text{unrouted}}}{N_{\text{conn}}}\right)$$
- If all connections are open: penalty $= 1000.0 \implies \text{score} = 0.00$.
- If $N_{\text{unrouted}} = 0$: penalty $= 0.0$.

#### Clearance Violation Penalty:
$$\text{penalty}_{\text{drc}} = N_{\text{violations}} \times C_{\text{count}} + \min\left(P_{\text{drc\_cap}},\ \frac{\sum L_{\text{violation\_um}}}{U_{\text{scale}}}\right)$$
- $N_{\text{violations}}$: discrete count of deduplicated clearance violation pairs.
- $\sum L_{\text{violation\_um}}$: total sum of violation depths in micrometers ($\mu\text{m}$), where for each violation pair $(i, j)$:
  $$L_{\text{violation\_um}} = \max\left(0.0,\ \text{expectedClearance} - \text{actualClearance}\right) \times 10^3$$
- $C_{\text{count}}$: fixed penalty per violation instance.
- $U_{\text{scale}}$: scaling denominator derived from board pitch distribution in `benchmarks.json`.
- $P_{\text{drc\_cap}}$: upper bound cap on clearance penalty (e.g. $400.0$).

### 2.2 Versioning & Backward Compatibility
```java
public enum RouterScoringVersion {
  /**
   * V1 Legacy (<= v2.4):
   * Monolithic formula normalized via (M - P_unrouted - P_drc - C_traces - C_vias) / M * 1000.
   */
  V1_LEGACY,

  /**
   * V2 Continuous (v2.5+):
   * Pure routability with discrete clearance count + continuous violation depth (um).
   */
  V2_CONTINUOUS
}
```

---

## 3. Optimizer Score Specification (OPTIMIZER_V1)

The Optimizer operates on routed boards to reduce physical parasitic costs toward the theoretical ideal.

### 3.1 Establishing the Theoretical Minimums ("Perfect Board")

From the component placement and netlist (which are fixed during routing), we establish the theoretical lower bounds:

1. **Theoretical Minimum Wire Length ($L_{\text{min}}$):**
   For each net $n \in \text{Nets}$, compute the Euclidean Minimum Spanning Tree (EMST) or Rectilinear Steiner Minimum Tree (RSMT) over its pin coordinates:
   $$L_{\text{min}} = \sum_{n \in \text{Nets}} \text{MST}_{\text{length}}(n)$$
   - *Property:* No valid routing in 2D/3D Euclidean space can ever be shorter than $L_{\text{min}}$.
   - *Efficiency:* Fast Kruskal/Prim over net pin sets ($O(P \log P)$ per net, computed once at board load).

2. **Theoretical Minimum Via Count ($V_{\text{min}}$):**
   - For 2-pin nets on the same layer: $0$ vias.
   - For nets with pins on different layers: at least $1$ via per layer transition required.
   - For single-layer reachable topologies: $V_{\text{min}} = 0$.
   - In practice, $V_{\text{min}} = \sum \text{inter\_layer\_transitions\_required} \approx 0$ for 2-layer SMD-dominant boards.

3. **Theoretical Minimum Bend Count ($B_{\text{min}}$):**
   - Direct straight-line connection on grid: $0$ bends.
   - Orthogonal Manhattan connection: at least $1$ bend per non-collinear pin pair.
   - In practice, $B_{\text{min}} = \sum_{n} (N_{\text{pins\_in\_net}} - 1)$ for non-collinear pins.

### 3.2 Normalized Formula ($0.0 \dots 1000.0$)

Starting from a perfect theoretical score of $1000.00$, we deduct penalties for excess wire length, excess vias, and excess bends:

$$\boxed{\text{score}_{\text{opt}} = \max\left(0.0,\ 1000.0 - \Delta L_{\text{penalty}} - \Delta V_{\text{penalty}} - \Delta B_{\text{penalty}}\right)}$$

Where:
- **Excess Wire Length Penalty:**
  $$\Delta L_{\text{penalty}} = W_L \times \left(\frac{L_{\text{actual}} - L_{\text{min}}}{L_{\text{min}}}\right)$$
  - Note: In typical good PCB routing, $L_{\text{actual}} / L_{\text{min}} \in [1.15, 1.60]$ (i.e. $15\%$ to $60\%$ wire detour due to obstacles).
- **Excess Via Penalty:**
  $$\Delta V_{\text{penalty}} = W_V \times (V_{\text{actual}} - V_{\text{min}})$$
- **Excess Bend Penalty:**
  $$\Delta B_{\text{penalty}} = W_B \times \max\left(0,\ B_{\text{actual}} - B_{\text{min}}\right)$$

### 3.3 Strict Invariant (Clearance Non-Regression)
$$\text{If } N_{\text{violations\_current}} > N_{\text{violations\_baseline}} \implies \text{score}_{\text{opt}} = -\infty \text{ (immediate rollback)}$$
The optimizer score only applies to valid, non-regressing board states.

---

## 4. Empirical Calibration Using `benchmarks.json`

Rather than arbitrarily choosing $W_L, W_V, W_B, C_{\text{count}}, U_{\text{scale}}$, we use the historical distribution in `scripts/benchmark/results/benchmarks.json`:

### 4.1 Dataset Profile in `benchmarks.json`
- Over 300,000 lines of JSON records spanning:
  - PCBench KiCad test boards (Tiers A, B, C, D).
  - Internal issue fixtures.
  - Across multiple Freerouting engine versions (v1.9.0, v2.2.4, v2.3.0, v2.4.0).
- Relevant extracted metrics per run:
  - `quality.total_nets`
  - `quality.unrouted_connections`
  - `quality.clearance_violations`
  - `quality.avg_violation_um`, `max_violation_um`, `min_violation_um`
  - `fixture.board_width_mm`, `board_height_mm`, `layer_count`

### 4.2 Calibration Methodology
A Python/PowerShell analysis script (`scripts/benchmark/calibrate_scoring_weights.py` or `.ps1`) will:
1. Parse all completed runs in `benchmarks.json`.
2. Compute the distribution quantiles (p10, p50, p90) for:
   - Wire length overhead ratio: $(L_{\text{actual}} - L_{\text{min}}) / L_{\text{min}}$
   - Via density per net: $V_{\text{actual}} / N_{\text{nets}}$
   - Bend density per trace: $B_{\text{actual}} / N_{\text{traces}}$
   - Violation depths: $L_{\text{violation\_um}}$
3. Calibrate $W_L, W_V, W_B$ so that:
   - Median routed board before optimization scores in the range **$750.00 \dots 850.00$**.
   - Highly optimized production layouts approach **$900.00 \dots 950.00$**.
   - Poor/congested layouts score in the **$400.00 \dots 600.00$** range.
   - The theoretical unreachable optimum sits at **$1000.00$**.

---

## 5. Architectural Implementation Roadmap

### Phase 1: Scoring Versioning Enum & Settings
- [ ] Add `RouterScoringVersion` enum (`V1_LEGACY`, `V2_CONTINUOUS`) in `app.freerouting.settings`.
- [ ] Add `routerScoringVersion` to `ScoringSettings` (default `V2_CONTINUOUS`).
- [ ] Support `--router-scoring-version v1` CLI flag for backward compatibility testing.

### Phase 2: Board Geometry Lower Bounds
- [ ] In `BasicBoard` / `BoardStatistics`:
  - Implement `computeMinimumSpanningTreeLength()` ($L_{\text{min}}$).
  - Implement `computeTheoreticalMinimumVias()` ($V_{\text{min}}$).
  - Implement `computeTheoreticalMinimumBends()` ($B_{\text{min}}$).
  - Cache results upon board load (since pin coordinates are static).

### Phase 3: Total Violation Depth Calculation
- [ ] In `DesignRulesChecker` / `ClearanceViolation`:
  - Implement `getTotalClearanceViolationDepthUm()`:
    $$\sum \max(0.0, \text{expectedClearance} - \text{actualClearance}) \times 10^3$$

### Phase 4: Calibration Script
- [ ] Create `scripts/benchmark/calibrate_scoring_weights.py` to process `benchmarks.json` and output optimal weight constants.

### Phase 5: Decoupled Scoring Implementations
- [ ] In `BoardStatistics`:
  - `getRouterScore(ScoringSettings)`: Implements Section 2.
  - `getOptimizerScore(ScoringSettings)`: Implements Section 3.
  - `getNormalizedScore(ScoringSettings)`: Routes to V1 or V2 based on `routerScoringVersion`.

### Phase 6: Engine Integration & Verification
- [ ] `BatchAutorouter` uses `getRouterScore()`.
- [ ] `BatchOptimizer` uses `getOptimizerScore()`.
- [ ] Parameterized tests verify:
  - Theoretical perfect board scores 1000.00.
  - Monotonicity: reducing vias/wires/bends strictly increases optimizer score.
  - DRC increases strictly abort optimizer candidates.
  - V1 legacy mode reproduces historical benchmark scores within float delta.
# Freerouting Dedicated Scoring Architecture Plan

**Document Status:** Research & Implementation Plan  
**Date:** September 2026 (Revised & Streamlined)  
**Author:** Freerouting AI Assistant & Core Engineering Team  
**Target Delivery:** Post-Optimizer Unification Roadmap  

---

## 1. Motivation & Architectural Decision

Historically, Freerouting attempted to use a single monolithic score equation across both the **Router** and the **Optimizer**:

$$\text{score} = \frac{\max(0,\ M - P_{\text{unrouted}} - P_{\text{violations}} - P_{\text{bends}} - C_{\text{traces}} - C_{\text{vias}})}{M} \times 1000$$

where $M = N_{\text{connections}} \times 5{,}000{,}000.0\text{F}$.

### 1.1 Why Monolithic Scoring Failed Both Stages
1. **In the Router:** The wire length ($1.0/\text{mm}$) and via cost ($50/\text{via}$) terms are tiny compared to $M$ ($< 0.0001\%$), but their subtraction mathematically prevented a fully routed, DRC-clean board from reaching a clean $1000.00$ (landing instead at $999.98$ or $999.99$).
2. **In the Optimizer:** The board arrives with 100% routing. The optimizer does not create or destroy nets; it refines layout geometry. Evaluated under the router formula, an optimization pass eliminating 15% of vias and 10% of trace length altered the score by $+0.0001\%$, which rounds away at standard precision, causing the optimizer to stop immediately as "no improvement detected".

### 1.2 Decision: Stage-Dedicated Scoring (No Versioning Needed)
- **Do we need scoring versioning?** **No.** Removing the negligible trace length, via count, and bend terms from the router's formula changes historical router scores by **$< 0.01\%$**. A board scoring $999.98$ in v1.9–v2.4 now cleanly scores $1000.00$. The router score remains directly comparable across all versions without any versioning flags or dual-score complexity.
- **The Router Score:** Pure routability and clearance rules. Normalized to $[0.0, 1000.0]$.
- **The Optimizer Score:** Dedicated post-routing quality metric focusing strictly on **via count**, **total wire length**, and **bending count/penalty**, while enforcing zero new clearance violations. Normalized to $[0.0, 1000.0]$.

---

## 2. Router Score Specification (Routability & DRC)

The Router score measures topological completion and clearance rule adherence. Trace length, via count, and bends are excluded.

### 2.1 Formula

$$\boxed{\text{score}_{\text{router}} = \max\left(0.0,\ 1000.0 \times \left(1.0 - \frac{N_{\text{unrouted}}}{N_{\text{conn}}}\right) - P_{\text{drc}}\right)}$$

Where:
- $N_{\text{unrouted}}$ is the incomplete connection count.
- $N_{\text{conn}}$ is the total connection count on the board.
- $P_{\text{drc}}$ is the clearance violation penalty:
  $$P_{\text{drc}} = \min\left(1000.0,\ N_{\text{violations}} \times 100.0 + \min\left(200.0,\ L_{\text{violation\_mm}} \times 10.0\right)\right)$$
  - Any clearance violation strictly prevents a board from scoring $1000.00$.

### 2.2 Milestone Values
- **0% routed:** $\mathbf{0.00}$
- **50% routed, 0 violations:** $\mathbf{500.00}$
- **100% routed, 1 violation:** $\le \mathbf{890.00}$
- **100% routed, 0 violations:** $\mathbf{1000.00}$ (exact milestone)

---

## 3. Optimizer Score Specification (Vias, Length & Bending)

The Optimizer takes a routed board and minimizes physical parasitics and manufacturing complexity.

### 3.1 Hard Gating Invariants (DRC & Connectivity)
The optimizer's primary directive is non-regression:
1. **Zero Unrouted Nets:** If candidate rerouting leaves any connection unrouted, candidate is rejected immediately.
2. **Clearance Violation Invariant:**
   $$\text{violations}_{\text{candidate}} \le \text{violations}_{\text{baseline}}$$
   If candidate routing introduces even a single new clearance violation, candidate score is $-\infty$ (rejected immediately). Clearance violations must remain at 0 (or strictly decrease if the input had violations).

### 3.2 Normalized Metric ($0.0 \dots 1000.0$)

The optimizer score maps the quality of the layout onto the full $[0.0, 1000.0]$ range.

When the optimizer stage begins, it records the reference baseline from the incoming board:
- $V_{\text{ref}} = \text{vias.totalCount}$
- $L_{\text{ref}} = \text{traces.totalLengthMm}$
- $B_{\text{ref}} = \text{bends.totalCount}$

At any pass or candidate evaluation:
- Relative via reduction: $\Delta_{\text{via}} = \frac{V_{\text{ref}} - V_{\text{current}}}{V_{\text{ref}}}$
- Relative wire length reduction: $\Delta_{\text{trace}} = \frac{L_{\text{ref}} - L_{\text{current}}}{L_{\text{ref}}}$
- Relative bend reduction: $\Delta_{\text{bend}} = \frac{B_{\text{ref}} - B_{\text{current}}}{B_{\text{ref}}}$

#### Scoring Equation:

$$\boxed{\text{score}_{\text{opt}} = \max\left(0.0,\ \min\left(1000.0,\ 500.0 + 500.0 \times \left(w_v \cdot \Delta_{\text{via}} + w_t \cdot \Delta_{\text{trace}} + w_b \cdot \Delta_{\text{bend}}\right)\right)\right)}$$

Where default weights are:
- $w_v = 0.50$ (via reduction — highest priority in multilayer PCB design)
- $w_t = 0.35$ (total wire length reduction — trace impedance & delay minimization)
- $w_b = 0.15$ (bend count reduction — eliminating unnecessary corners and acid traps)
- $w_v + w_t + w_b = 1.0$

### 3.3 Dynamic Range & Behavior
- **Baseline Board (Incoming from Router):**
  $\Delta_{\text{via}} = 0$, $\Delta_{\text{trace}} = 0$, $\Delta_{\text{bend}} = 0 \implies \mathbf{score = 500.00}$.
- **Optimized Board (e.g. 15% fewer vias, 6% shorter traces, 10% fewer bends):**
  $$\Delta = 0.50 \times 0.15 + 0.35 \times 0.06 + 0.15 \times 0.10 = 0.075 + 0.021 + 0.015 = 0.111$$
  $$\text{score}_{\text{opt}} = 500.0 + 500.0 \times 0.111 = \mathbf{555.50}$$
  Relative pass improvement: $\frac{555.50 - 500.0}{500.0} = \mathbf{+11.1\%}$!
- **Degraded Candidate:**
  If an attempted reroute adds vias or adds extra meandering loops without net benefit, $\Delta < 0 \implies \text{score} < 500.00$. The candidate is rejected and `bestBoard` is preserved.
- **Convergence Guarding:**
  A threshold such as `-oit 1.0%` now checks meaningful geometric progress rather than floating-point noise.

---

## 4. Implementation Steps (For Future Implementation)

### Phase 1: Settings
- [ ] In `ScoringSettings.java`:
  - Retain existing fields for router DRC weights.
  - Add optimizer quality weights:
    - `qualityViaWeight` (default `0.50f`)
    - `qualityTraceWeight` (default `0.35f`)
    - `qualityBendWeight` (default `0.15f`)
    - `optimizerBaseScore` (default `500.0f`)

### Phase 2: Board Statistics Methods
- [ ] In `BoardStatistics.java`:
  - `getRouterScore(ScoringSettings)`:
    Implements $\text{score}_{\text{router}}$ ($0 \dots 1000$).
  - `getOptimizerScore(OptimizerScoringReference, ScoringSettings)`:
    Implements $\text{score}_{\text{opt}}$ ($0 \dots 1000$).
  - Keep `getNormalizedScore(ScoringSettings)` returning `getRouterScore()` so all standard telemetry, CLI status lines, and external callers retain seamless compatibility.

### Phase 3: Engine Integration
- [ ] In `BatchAutorouter` / `AutorouteBatchLoop`:
  - Use `getRouterScore()`.
- [ ] In `BatchOptimizer`:
  - Capture `OptimizerScoringReference(vias, lengthMm, bends)` before pass 1.
  - Evaluate passes and candidates using `getOptimizerScore(ref, scoringSettings)`.
  - Termination threshold (`optimizationImprovementThreshold`) checks delta on the optimizer score scale.

### Phase 4: Verification & Tests
- [ ] `RouterScoreTest`:
  - Fully routed clean board produces exactly 1000.00.
  - Adding vias or length does NOT alter router score.
  - DRC violation or unrouted net reduces router score.
- [ ] `OptimizerScoreTest`:
  - Baseline board scores 500.00.
  - Removing vias, wire length, or bends increases score towards 1000.00.
  - Adding any clearance violation invalidates candidate.
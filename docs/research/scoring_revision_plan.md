# Freerouting Dedicated Scoring Architecture & Versioning Plan

**Document Status:** Implementation Plan
**Date:** September 8, 2026 (Revised after benchmark-schema review)
**Author:** Freerouting AI Assistant & Core Engineering Team  
**Target Delivery:** Post-Optimizer Unification Roadmap  

---

## 1. Executive Summary & Design Requirements

The scoring revision has two separate consumers with different objectives. They must not
share an algorithm, a version selector, or an implicit set of weights:

1. **Router score:** prioritizes connection completion and clearance integrity while
   autorouting.
2. **Optimizer score:** ranks already-routed candidate boards by physical efficiency while
   preserving DRC and connectivity invariants.

Both scores remain normalized to `0.0 .. 1000.0`, but this common display range does not
mean that their numerical values are interchangeable.

### 1.1 Router scoring requirements

The router score incorporates:

1. `unrouted_connections` (the number of incomplete connection edges)
2. `clearance_violations` (deduplicated DRC violation pairs)
3. `total_violation_um` (the sum of positive clearance shortfalls in micrometres)

The DRC terms distinguish a small border touch from a severe overlap. The violation count
and total depth must be computed from the comprehensive
`DesignRulesChecker.getAllClearanceViolations()` result, with each pair counted once.

The router algorithm is versioned independently:

```java
public enum RouterScoringVersion {
  V1_LEGACY,
  V2_CONTINUOUS
}
```

`V1_LEGACY` preserves the historical monolithic score for compatibility. `V2_CONTINUOUS`
is the new completion-plus-clearance score and must not silently replace the legacy
algorithm in historical comparisons.

### 1.2 Optimizer scoring requirements

The optimizer score evaluates excess physical cost relative to board-specific,
computable lower bounds:

- $L_{\text{min}}$: a geometry-consistent lower bound for routed wire length.
- $V_{\text{min}}$: a topology- and layer-aware lower bound for via transitions.
- $B_{\text{min}}$: a routing-model-aware lower bound for bends.

These are lower bounds, not necessarily exact theoretical optima. In particular, an MST
is not an exact Steiner minimum tree, and the proposed bend and via estimates must not be
described as exact for arbitrary multi-pin nets.

The optimizer algorithm is versioned independently:

```java
public enum OptimizerScoringVersion {
  V1_LEGACY,
  V2_LOWER_BOUND
}
```

`V1_LEGACY` represents the current shared score behavior. `V2_LOWER_BOUND` is the
lower-bound-based optimizer score introduced by this plan.

### 1.3 Independent configurable settings

Router and optimizer weights must be represented by separate configurable settings
objects. The implementation may retain a compatibility wrapper for the existing
`ScoringSettings` serialization name, but the effective configuration must have two
independent sections:

- `RouterScoringSettings`
  - scoring version
  - unrouted-connection penalty
  - clearance-violation-count penalty
  - clearance-violation-depth weight and scale
  - clearance penalty cap
- `OptimizerScoringSettings`
  - scoring version
  - excess wire-length weight
  - excess via weight
  - excess bend weight
  - normalization floors/caps needed for zero-length or zero-count boards

Every configurable field must remain nullable in settings source objects so
`SettingsMerger` can distinguish “no opinion” from an explicit override. Every effective
default must be assigned by `DefaultSettings.getSettings()` and backed by a named
`DEFAULT_*` constant in
`src/main/java/app/freerouting/settings/sources/DefaultSettings.java`. Formula code must
not contain fallback magic numbers.

The numerical defaults for the new V2 terms must be selected only after the benchmark
data contract below is complete. Until then, compatibility defaults may preserve the
current behavior; the plan does not introduce uncalibrated magic constants.

---

## 2. Router Score Specification

The router score evaluates topological completeness and physical design-rule clearance
integrity. It is not the optimizer objective.

### 2.1 Theoretical Formulation

$$\boxed{\text{score}_{\text{router}} = \max\left(0.0,\ 1000.0 - \text{penalty}_{\text{unrouted}} - \text{penalty}_{\text{drc}}\right)}$$

#### Unrouted Penalty:
$$\text{penalty}_{\text{unrouted}} = W_{\text{unrouted}} \times \left(\frac{N_{\text{unrouted}}}{N_{\text{conn}}}\right)$$
- If all connections are open, the normalized ratio is $1.0$; the configurable
  $W_{\text{unrouted}}$ determines the resulting penalty.
- If $N_{\text{unrouted}} = 0$: penalty $= 0.0$.

#### Clearance Violation Penalty:
$$\text{penalty}_{\text{drc}} =
N_{\text{violations}} \times W_{\text{count}} +
\min\left(P_{\text{drc\_cap}},\
W_{\text{depth}} \times \frac{\sum L_{\text{violation\_um}}}{U_{\text{scale}}}\right)$$
- $N_{\text{violations}}$: discrete count of deduplicated clearance violation pairs.
- $\sum L_{\text{violation\_um}}$: total sum of violation depths in micrometers ($\mu\text{m}$), where for each violation pair $(i, j)$:
  $$L_{\text{violation\_um}} = \max\left(0.0,\ \text{expectedClearance} - \text{actualClearance}\right) \times 10^3$$
- $W_{\text{unrouted}}$, $W_{\text{count}}$, $W_{\text{depth}}$, $U_{\text{scale}}$, and
  $P_{\text{drc\_cap}}$: configurable router-scoring values.

`U_scale` must represent a physically meaningful clearance-depth scale, not a board-pitch
distribution. The unit conversion must be performed exactly once, using the board's
coordinate resolution.

The zero-connection case must be explicitly defined. A board with no required
connections and no violations should receive the perfect router score, not `NaN` or an
accidental zero.

### 2.2 Versioning & Backward Compatibility

`BoardStatistics.getRouterScore(RouterScoringSettings)` is the only score used by
autorouter progress, routing-pass comparisons, and router-facing result fields.
`getNormalizedScore()` must not remain an ambiguous shared entry point for both phases.
If retained temporarily, it must be a compatibility adapter with documented semantics.
---

## 3. Optimizer Score Specification

The Optimizer operates on routed boards to reduce physical parasitic costs toward the theoretical ideal.

### 3.1 Establishing Computable Lower Bounds ("Perfect Board")

From the component placement and netlist (which are fixed during routing), we establish the theoretical lower bounds:

1. **Computable Lower Bound Wire Length ($L_{\text{min}}$):**
   For each net $n \in \text{Nets}$, compute a metric-consistent lower bound such as a
   Manhattan MST or an RSMT approximation over its pin coordinates:
   $$L_{\text{min}} = \sum_{n \in \text{Nets}} \text{MST}_{\text{length}}(n)$$
   - The selected metric must match the allowed trace geometry and unit system.
   - Cache the result once per board/netlist; obstacles may make the bound optimistic.

2. **Computable Lower Bound Via Count ($V_{\text{min}}$):**
   - derive a lower bound from pad layer requirements, permitted via spans, and connected
     layer components;
   - do not assume one via per pin-layer transition for arbitrary multi-pin nets;
   - use $0$ for same-layer, single-layer-reachable nets where appropriate.

3. **Computable Lower Bound Bend Count ($B_{\text{min}}$):**
   - derive a lower bound from the actual trace geometry model and net topology;
   - do not use `N_pins - 1` as a universal formula;
   - handle collinear, diagonal, multi-pin, and layer-changing cases explicitly.

### 3.2 Normalized Formula ($0.0 \dots 1000.0$)

Starting from a perfect theoretical score of $1000.00$, we deduct penalties for excess wire length, excess vias, and excess bends:

$$\boxed{\text{score}_{\text{opt}} = \max\left(0.0,\ 1000.0 - \Delta L_{\text{penalty}} - \Delta V_{\text{penalty}} - \Delta B_{\text{penalty}}\right)}$$

Where:
- **Excess Wire Length Penalty:**
  $$\Delta L_{\text{penalty}} =
  W_L \times \max\left(0,\frac{L_{\text{actual}} - L_{\text{min}}}
  {\max(L_{\text{min}},L_{\text{floor}})}\right)$$
  - Note: In typical good PCB routing, $L_{\text{actual}} / L_{\text{min}} \in [1.15, 1.60]$ (i.e. $15\%$ to $60\%$ wire detour due to obstacles).
- **Excess Via Penalty:**
  $$\Delta V_{\text{penalty}} = W_V \times \max(0,V_{\text{actual}} - V_{\text{min}})$$
- **Excess Bend Penalty:**
  $$\Delta B_{\text{penalty}} = W_B \times \max(0,B_{\text{actual}} - B_{\text{min}})$$

$W_L$, $W_V$, $W_B$, and $L_{\text{floor}}$ are configurable optimizer-scoring values.
The score must be defined for empty boards and for nets whose lower-bound length is zero.

### 3.3 Strict Invariant (Clearance Non-Regression)
Clearance and connectivity are acceptance gates, not merely optimizer weights:

$$
\text{if current DRC or connectivity is worse than baseline, reject the candidate}
$$

The implementation should return an explicit candidate-comparison result or rejection
reason instead of encoding rejection as floating-point $-\infty$. The comparison must use
the comprehensive DRC result and should compare both violation count and total violation
depth where count alone cannot express a regression.

`BoardStatistics.getOptimizerScore(OptimizerScoringSettings)` is the only score used by
`BatchOptimizer`. It must never be called by autorouter pass selection.

---

## 4. Empirical Calibration Using `benchmarks.json`

The current `benchmarks.json` is a useful router-regression dataset, but it is not yet
sufficient to identify optimizer weights. The file currently contains 5,851 run records
with routability, DRC summaries, timing, memory, and fixture geometry. It does not persist
the routed wire length, via count, or bend count required by the optimizer equation.

The benchmark exporter currently extracts a subset of the result manifest. Both the
current tree and `src_v19` already compute most of the required physical statistics:
trace length, bends, vias, and comprehensive DRC violations. The missing work is to
persist the same raw fields in both manifests and benchmark records.

The v1.9 extension is telemetry-only. `src_v19/` remains a frozen compatibility reference:
do not port the new scoring algorithms, settings, version enums, or optimizer behavior
into it. Only the minimum statistics and manifest changes required for benchmark schema
parity are permitted.

### 4.1 Dataset Profile in `benchmarks.json`
- Run records spanning:
  - PCBench KiCad test boards (Tiers A, B, C, D).
  - Internal issue fixtures.
  - Multiple Freerouting engine versions.
- Relevant extracted metrics per run:
  - `binary.version_label`, `binary.git_sha`
  - `fixture.relative_path`, `fixture.sha256`, `fixture.tier`
  - `quality.total_nets`, `quality.max_connections`
  - `quality.unrouted_connections`
  - `quality.clearance_violations`
  - `quality.total_violation_um`, `avg_violation_um`, `max_violation_um`, `min_violation_um`
  - `quality.trace_length_mm`, `via_count`, `bend_count`
  - `fixture.board_width_mm`, `board_height_mm`, `layer_count`

### 4.2 Calibration Methodology
Before creating a calibration script, the benchmark/result schema must be extended to
include:

1. A router-final snapshot containing all router-score inputs.
2. An optimizer-initial snapshot and optimizer-final snapshot containing all
   optimizer-score inputs.
3. A `schema_version` and explicit `exit.state`/`exit.code` handling.
4. Null values preserved as missing; failed and timed-out runs must not be converted to
   zero-quality observations.
5. The same raw metric names and units from current and v1.9 manifests.

The later calibration workflow will:

1. Filter to successful runs with complete required metrics.
2. Pair runs by fixture identity (`fixture.sha256` where available).
3. Stratify by tier, layer count, and net-count range rather than pooling unlike boards.
4. Report missingness and p10/p50/p90/p95 distributions for every input and normalized
   excess term.
5. Use paired before/after optimizer results and repeated-run noise measurements to fit
   weights.
6. Reserve held-out fixtures for validation.
7. Check monotonicity, score saturation, routing completion, full DRC results, and
   optimizer decision agreement before changing defaults.

Historical `quality_score` values are comparison baselines only. They are not independent
targets for fitting the new weights because they were produced by the existing scoring
formula.

### 4.3 v1.9 Raw-Metric and Score-Source Compatibility

The v1.9 manifest must expose the same raw board-statistics paths and units as the
current manifest:

- `board_statistics.connections.maximum_count`
- `board_statistics.connections.incomplete_count`
- `board_statistics.traces.total_length_mm`
- `board_statistics.vias.total_count`
- `board_statistics.bends.total_count`
- `board_statistics.clearance_violations.total_count`
- `board_statistics.clearance_violations.total_violation_um`
- `board_statistics.clearance_violations.min_violation_um`
- `board_statistics.clearance_violations.max_violation_um`
- `board_statistics.clearance_violations.avg_violation_um`

`src_v19` already calculates the trace, via, bend, and most clearance fields. The
telemetry extension must add the total violation depth and keep the existing v1.9
algorithms unchanged. The benchmark parser must then flatten these fields identically
for current and v1.9 runs; it must never reconstruct total depth as
`average × count` when the raw field is absent.

The score source must be explicit in the benchmark record. The benchmark harness is
intended to calculate `quality_score` with the current scoring implementation for every
version, including v1.9. Because the v1.9 `RoutingResultManifest` currently also emits a
native `normalized_score`, the harness must either overwrite that value with the
current-version result or store the native value separately as
`legacy_native_score`. It must not silently treat the v1.9 manifest score as the
current-version benchmark score.

Add a manifest/schema parity test that validates field names, JSON types, units, and
missing-value behavior for current and v1.9 result manifests. The test should not require
the metric values to match, since the two routers can produce different boards.

---

## 5. Architectural Implementation Roadmap

### Phase 1: Independent scoring contracts and settings
- [ ] Add `RouterScoringVersion` (`V1_LEGACY`, `V2_CONTINUOUS`).
- [ ] Add `OptimizerScoringVersion` (`V1_LEGACY`, `V2_LOWER_BOUND`).
- [ ] Define independent router and optimizer scoring settings/configuration sections.
- [ ] Define all score weights as nullable configurable fields.
- [ ] Add named `DEFAULT_*` constants and assign every effective default only in
  `DefaultSettings.getSettings()`.
- [ ] Preserve a compatibility serialization path for the existing scoring settings where
  required.
- [ ] Expose independent version selectors and weight overrides through the supported
  settings sources, including CLI/API compatibility requirements.

### Phase 2: Separate score APIs and engine integration
- [ ] Add `BoardStatistics.getRouterScore(RouterScoringSettings)`.
- [ ] Add `BoardStatistics.getOptimizerScore(OptimizerScoringSettings)`.
- [ ] Update `BatchAutorouter` and router history/pass comparisons to use only the router
  score.
- [ ] Update `BatchOptimizer` and optimizer candidate comparisons to use only the
  optimizer score.
- [ ] Update result/API/UI fields so their score meaning is explicit.
- [ ] Keep legacy adapters only where needed for backward compatibility.

### Phase 3: Board geometry lower bounds
- [ ] In `BasicBoard` / `BoardStatistics`:
  - Implement a geometry-consistent lower bound for wire length.
  - Implement a topology- and layer-aware lower bound for vias.
  - Implement a routing-model-aware lower bound for bends.
  - Cache results upon board load because pin coordinates and the netlist are static.
  - Define zero-length, empty-net, and mixed-layer edge cases.

### Phase 4: Total violation depth and benchmark schema
- [ ] In `DesignRulesChecker` / `BoardStatistics`:
  - Persist `totalViolationUm` alongside count, minimum, maximum, and average:
    $$\sum \max(0.0, \text{expectedClearance} - \text{actualClearance}) \times 10^3$$
- [ ] Extend current `RoutingResultManifest` and the benchmark exporter with router and
  optimizer snapshots.
- [ ] Extend only the raw-statistics and manifest-schema portions of `src_v19`; do not
  port scoring algorithms, scoring settings, or optimizer behavior.
- [ ] Make current and v1.9 manifests expose identical raw metric paths, names, and units.
- [ ] Make the benchmark harness record the score source and use the current-version score
  for every version under comparison.
- [ ] Add a benchmark schema version and preserve missing values.

### Phase 5: Calibration design (script intentionally deferred)
- [ ] Specify the calibration report and candidate-weight search method.
- [ ] Define paired, stratified, and held-out evaluation sets.
- [ ] Define the repeated-run noise floor and acceptance thresholds.
- [ ] Do **not** create the calibration script in this phase.

### Phase 6: Calibrated defaults and compatibility
- [ ] Select V2 defaults from the completed benchmark analysis.
- [ ] Define the defaults as `DEFAULT_*` constants in `DefaultSettings.java`.
- [ ] Verify settings-source precedence and CLI/API serialization.
- [ ] Verify V1 compatibility against fixture-level golden results.

### Phase 7: Verification
- [ ] Parameterized tests verify:
  - router and optimizer scores are independent;
  - both versions select the intended algorithm;
  - perfect router and optimizer boards score 1000.00;
  - zero-connection boards produce a defined score;
  - reducing vias/wires/bends strictly increases optimizer score;
  - DRC/connectivity regressions reject optimizer candidates;
  - V1 legacy mode reproduces fixture-level historical results within a documented
    tolerance;
  - current and v1.9 manifests pass raw-metric schema parity checks;
  - benchmark records identify the score source and do not use a native v1.9 score as
    the current-version score;
  - full DRC uses `DesignRulesChecker.getAllClearanceViolations()`;
  - no routing completion or clearance regression is introduced.

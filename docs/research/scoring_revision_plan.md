# Freerouting Dedicated Scoring Architecture & Versioning Plan

**Document Status:** Implementation Plan
**Date:** September 9, 2026 (ETA dropped; V2 replay and bound-edge fixtures)
**Author:** Freerouting AI Assistant & Core Engineering Team
**Target Delivery:** Post-Optimizer Unification Roadmap

---

## 1. Executive Summary & Design Requirements

Scoring has three independent layers. They must not share an algorithm, a version
selector, or an implicit set of weights:

1. **Maze-search costs** (unchanged role): `viaCosts`, `planeViaCosts`,
   `startRipupCosts`, and preferred/undesired direction trace costs drive A* / maze
   expansion. They are routing-control parameters, not board-score terms.
2. **Router score:** connection completion and clearance integrity while autorouting.
3. **Optimizer score:** physical efficiency of an already-accepted board (wire, vias,
   bends) relative to computable lower bounds.

Both board scores remain on a `0.0 .. 1000.0` display scale. The numbers are not
interchangeable.

**Complexity \(C\) is the scoring size scale.** \(C = \max(1,\ P \times L)\) and
\(D = C\). Via, bend, and DRC penalties divide by \(D\); unrouted stays a
connection fraction; length excess uses \(L_{\min}\). There is **no ETA
polynomial** and no live conversion through `cpu_score`. Job-time estimates are
out of scope.

The **current** tree defaults to V2 for both router and optimizer scores, with a
settings / CLI override to run V1 legacy. **`src_v19` keeps its existing scoring
algorithm unchanged.** v1.9 only gains raw-metric telemetry so `benchmarks.json` can
store the same physical fields. Offline V2 replay onto those stored actuals +
current-tree bounds does not require a reroute.

### 0.1 End-of-day progress summary — September 8, 2026

Completed today:

- Implemented the independent router and optimizer scoring versions and settings
  split. The current tree now defaults to `V2_CONTINUOUS` for router scoring and
  `V2_LOWER_BOUND` for optimizer scoring; V1 remains an explicit compatibility
  override. The frozen `src_v19` scoring algorithm remains unchanged.
- Implemented and exercised the V2 lower-bound path: Manhattan MST wire-length
  bounds, layer-switch via bounds, bend bounds, difficulty scaling, and the
  optimizer candidate gate. A more-complete but lower-scoring candidate is no
  longer an automatic win.
- Added current and v1.9 phase telemetry: before/after snapshots, native phase
  scores, current-version DRC replay scores, CPU seconds, allocation and heap
  metrics, bounds, and schema-v5 benchmark records.
- Hardened benchmark persistence and analysis: robust median CPU scoring, the
  1000x persisted CPU-score scale reduction, relative paths, two-decimal float
  output, legacy CPU fallbacks, host-version backfilling, and PowerShell 5.1
  compatibility.
- Fixed benchmark-runner reliability issues found during the first long run:
  JVM `-D` arguments are now placed before `-jar`; path-normalization counters
  are not emitted as stray output; and benchmark JSON replacement uses unique
  absolute temporary/backup paths with Windows-safe atomic replacement.
- Rebuilt and started the all-tier benchmark workflow. The initial run completed
  17 fixtures successfully. At the current serial settings, the full 1,177-run
  workload reported an estimate of roughly 61 hours per binary, which is an
  operational constraint for tomorrow's calibration workflow.
- Required Java formatting, Checkstyle, rewrite-recipe, translation-context, and
  focused scoring tests passed. Changes were committed in `83e51252` and
  `4ef80ec9`.

Status after 9 September 2026:

- Current (`v2.4.2-SNAPSHOT`) and v1.9 (`v1.9.0`) PCBench corpus runs completed
  (current 362 boards across A–D; v1.9 169 tier-A boards; 169 paired fixtures).
- V1 ±10 historical-score parity is **not required**: the only V1 formula
  change that landed is weighted too low to matter.
- Internal ETA / time-polynomial \(D\) is **dropped**. Scoring uses \(D = C\).
- Remaining calibration is **optimizer** excess weights and
  `optimizer.optimizationImprovementThreshold`. Router V2 is already
  complete-by-construction (open board → 0; clean complete board → 1000 minus
  DRC/\(D\)). DRC count/depth weights can stay until a real saturation problem
  shows up after \(D = C\).
- Optional Phase 8 settings-hierarchy refactor stays deferred.

### 1.1 Router scoring requirements

The V2 router score incorporates:

1. `unrouted_connections` (incomplete connection edges)
2. `clearance_violations` (deduplicated DRC pairs from
   `DesignRulesChecker.getAllClearanceViolations()`)
3. `total_violation_um` (sum of positive clearance shortfalls in micrometres)

```java
public enum RouterScoringVersion {
  V1_LEGACY,      // current monolithic score (unrouted + DRC + length + vias + bends)
  V2_CONTINUOUS   // completion + discrete DRC count + continuous violation depth
}
```

Default in `DefaultSettings`: `V2_CONTINUOUS`. `V1_LEGACY` remains independently
overridable for compatibility via the settings path (`router.scoring.version`) and the short CLI
flag (`--router-scoring-version v1`). Router and optimizer version enums are **not** forced to stay
in sync.

### 1.2 Optimizer scoring requirements

The V2 optimizer score measures excess physical cost versus fixture-derived lower
bounds:

- \(L_{\min}\): Manhattan MST over each net’s pin (and other terminal) coordinates,
  summed across nets. Obstacles may make it optimistic. Steiner routes can beat MST,
  so excess is clamped at 0. **Recommended / working choice** (see §3.1).
- \(V_{\min}\): per-net minimum via *items* from pin/terminal layer occupancy and
  allowed via spans (layer switches). No conduction-area special case: a pour is
  just another terminal on its layer. Mixed-layer nets therefore need vias and will
  not reach 1000.
- \(B_{\min}\): Manhattan one-bend lower bound for non-collinear 2-pin connections.
  **Actual** bend count is polyline corners; **45° and 90° corners count equally**
  (no 45° discount).

```java
public enum OptimizerScoringVersion {
  V1_LEGACY,       // current combined score used as optimizer objective
  V2_LOWER_BOUND   // excess length / vias / bends vs stored lower bounds
}
```

Default in `DefaultSettings`: `V2_LOWER_BOUND`. `V1_LEGACY` remains independently
overridable for compatibility via the settings path (`optimizer.scoring.version`) and the short CLI
flag (`--optimizer-scoring-version v1`). A convenience `--scoring-version v1` may set **both** to
V1 without coupling the two enums in code.

### 1.3 Independent configurable settings

Keep maze-search cost fields on the existing routing-cost settings object so maze
behavior does not change when V2 board-score weights are calibrated.

Add two independent nullable settings sections (names may wrap today’s
`RoutingCostSettings` for serialization compatibility, but merge must copy nested
fields explicitly — `ReflectionUtil.copyFields` is same-class only):

- `RouterScoreSettings`
  - `version` (`RouterScoringVersion`)
  - unrouted-connection weight
  - clearance-violation-count weight
  - clearance-violation-depth weight and depth scale
- `OptimizerScoreSettings`
  - `version` (`OptimizerScoringVersion`)
  - excess wire-length weight
  - excess via weight
  - excess bend weight
  - length floor and difficulty-scale floor
  - (no \(\alpha\) until a density residual is proven necessary)

Every scoring field stays nullable in source objects. Effective defaults are assigned
only in `DefaultSettings.getSettings()` via named `DEFAULT_*` constants. Formula code
must not hide magic numbers.

V2 is now the current `DefaultSettings` selection. Optimizer excess weights are
\(W_L=1000\), \(W_V=2000\), \(W_B=500\). Router defaults are \(W_1=1000/3\),
\(W_2=2000/3\) (split \(F=0.5\)), \(W_C=25\), \(W_D=300\),
\(U_{\text{scale}}=1000\,\mu\text{m}\). V1 remains available as an explicit
compatibility override.

### 1.4 Decided product rules

- Conduction areas are not a scoring special case. Via-to-pour boards need not reach
  1000.
- \(L_{\text{actual}}\) is `traces.totalLengthMm` (includes jogs, neckdowns, fanout).
- \(B_{\text{actual}}\) is polyline corner/bend count already computed by
  `BoardStatistics`. 45° corners are bends, same as 90°.
- Optimizer candidate gate (optimizer-score ranking; completeness is not an
  automatic win):
  1. If `incompleteCount` is **higher** → reject (`CONNECTIVITY_REGRESSION`).
     A less-complete board is never installed.
  2. Else if clearance **count** is higher → reject (`DRC_COUNT_REGRESSION`).
  3. Else if optimizer score is strictly higher → accept.
  4. Else keep incumbent (including ties).
  - A more-complete but uglier candidate (fewer incompletes, lower optimizer
    score) is **not** an automatic win; it loses on score.
  - Violation **depth is ignored** for the gate.
  - Return an explicit accept/reject reason; do not encode rejection as \(-\infty\).
  - Completeness and DRC stay as vetoes only because they are **not** terms in
    the optimizer formula. Ranking among legal candidates is by optimizer score.
  - **Intended:** a timeout or rip-up that increases incompletes is rejected even if
    DRC or optimizer score improved.
- `src_v19` scoring stays as-is. Do not port V2 algorithms, version enums, or optimizer
  gates into v1.9.
- Current Freerouting uses V2 by default; legacy via setting/CLI.
- Persist lower bounds in result / benchmark JSON so scores can be recomputed later
  without rerouting. Later, replay current V2 onto v1.9 rows from stored actuals +
  bounds (v1.9 engine score stays native).
- Weight calibration and V2 score comparison use **current vs v1.9** only. Do not
  require V2 scores for 2.2.4 or 2.3.0.
- Optimizer stop: remove the “score is already close to 1000” stop. Stop when relative
  pass improvement is below `optimizer.optimizationImprovementThreshold` (existing
  setting, default `0.01`; kept after V2).
- v1.9 writes optimizer-**initial** and optimizer-**final** raw snapshots (telemetry
  only; scoring algorithm unchanged).
- Optimizer via/bend and router DRC terms are divided by difficulty \(D\) derived
  from kernel \(C\) (§3.0). Length excess uses \(L_{\min}\), not \(D\). No penalty
  caps; weights are chosen so typical incomplete/dirty boards stay \(> 0\).
- Optimizer ties keep the incumbent.
- \(L_{\min}\) is Manhattan MST.
- `getNormalizedScore()` is a deprecated alias of `getRouterScore`. API keeps
  `normalized_score` as the router score and adds `optimizer_score`.
- Tests for optimizer/router 1000 use a named synthetic fixture
  `SyntheticPerfectTwoPin` (§3.2), not a pour-heavy PCBench board.

---

## 2. Router Score Specification

The router score is the only score used by `BatchAutorouter`, `BoardHistory`, and
router-facing result fields.

### 2.1 V2 formulation

$$\boxed{\text{score}_{\text{router}} = \max\left(0.0,\ 1000.0 - \text{penalty}_{\text{unrouted}} - \text{penalty}_{\text{drc}}\right)}$$

#### Unrouted penalty

$$\text{penalty}_{\text{unrouted}} = W_1 \times o_1 + W_2 \times o_2$$

with split \(F = 0.5\) (`unrouted_free_fraction`) and open fraction
\(f = N_{\text{unrouted}} / N_{\text{conn}}\):

$$
o_1 = \min\left(1,\ \max\left(0,\ \frac{f - F}{1 - F}\right)\right)
\qquad
o_2 = \min\left(1,\ \frac{f}{F}\right)
$$

- \(N_{\text{conn}}\) is `connections.maximumCount`, never fixture `net_count`.
- \(o_1\) is how much of the first half is still open; \(o_2\) is how much of the
  last half is still open. At \(f=1\): both 1. At \(f=0.5\): \(o_1=0\), \(o_2=1\).
  At \(f=0\): both 0.
- Defaults \(W_1 = 1000/3\), \(W_2 = 2000/3\) so the last half is worth twice the
  first half, a fully open board scores 0, and a half-done board scores
  \(1000 - W_2 \approx 333\).
- If \(N_{\text{unrouted}} = 0\): penalty \(= 0\).
- If \(N_{\text{conn}} = 0\) and there are no violations: score \(= 1000\) (defined;
  not `NaN` / 0).
- If \(N_{\text{conn}} = 0\) and there are violations: apply only the DRC penalty.

#### Clearance penalty

$$
L_{\text{violation\_um}} = \max(0,\ \text{expectedClearance} - \text{actualClearance})
\times \text{boardUnitToUmFactor}
$$

Convert **once** with the board unit and DSN resolution (the same factor
`BoardStatistics` already uses for `min`/`max`/`avg` violation µm). Do **not**
multiply by a hard-coded \(10^3\).

No DRC cap. Count and depth terms use difficulty \(D\) from kernel \(C\)
(§3.0), not raw counts. Unrouted stays a **fraction** of \(N_{\text{conn}}\)
and does **not** divide by \(D\).

$$
\text{penalty}_{\text{drc}} =
W_{\text{count}} \times \frac{N_{\text{violations}}}{\max(D, D_{\text{floor}})}
+ W_{\text{depth}} \times \frac{\sum L_{\text{violation\_um}}}
{U_{\text{scale}} \times \max(D, D_{\text{floor}})}
$$

\(U_{\text{scale}}\) is a physical µm scale (named `DEFAULT_*` in
`DefaultSettings`). Weights are set so typical incomplete/dirty boards stay
above 0; the outer \(\max(0,\ 1000 - \cdots)\) remains only as a numeric floor.

### 2.2 V1 compatibility

`RouterScoringVersion.V1_LEGACY` is today’s combined formula (unrouted, DRC count,
bends, length mm, via count).

Call-site policy:

- New Java code calls `getRouterScore` / `getOptimizerScore` only.
- `@Deprecated getNormalizedScore()` delegates to `getRouterScore` (not optimizer).
- API/result JSON: keep `normalized_score` as the **router** score; add
  `optimizer_score`. Do not blend the two.

---

## 3. Optimizer Score Specification

### 3.0 Complexity kernel \(C\) and difficulty \(D\)

One extra via on a 10-connection board must not be treated like one extra via on
an 800-connection dense board. **Board area alone is the wrong axis.** A large
sparse board is easy; a small dense one is hard.

**One kernel, scoring only:**

| Symbol | Role | Units |
|---|---|---|
| \(C\) | \(P \times L\) | pin-layers |
| \(D\) | Score denominator \(= \max(1,\ C)\) | pin-layers |

Live `cpu_score` is hardware telemetry only. It is **not** part of \(D\) and is
not used to estimate runtime.

**\(\alpha\):** unused. Revisit only if via-excess\(/D\) still trends with density
after length/via fields exist.

#### Kernel (decided)

$$
C = \max(1,\ P \times L)
$$

\(P\) = pin count from `BoardStatistics.items.pin_count` (not DSN regex).
\(L\) = signal layer count. Fixture `net_count` from `\ (net ` is unreliable
(often 0) and is **not** \(C\). \(N_{\text{conn}}\) stays the unrouted-fraction
denominator only; it is not used inside \(D\).

$$
D = \max(1,\ C)
$$

\(A\) is still persisted as `board_area_cm2` for analysis; it is **not** a
scoring axis. \(D_{\text{floor}} = 1\). Unrouted remains
\(N_{\text{unrouted}} / N_{\text{conn}}\). \(\Delta L\) uses \(L_{\min}\), not
\(D\). Only vias, bends, and DRC divide by \(D\).

Persist \(C\), \(D\), \(P\), \(L\), \(N_{\text{conn}}\), \(L_{\min}\), \(A\), and
\(S\) on the run JSON.

#### What the clean extract already showed (supersedes older net/area CPU notes)

`docs/research/optimizer_unification_clean.json` (357 clean
optimizer-unification runs). Working CSV:
`docs/research/optimizer_unification_clean.csv` (36 runs with
`cpu_seconds >= 30`).

Univariate Pearson vs CPU: pins 0.68, components 0.57, layers 0.50 (Spearman
−0.11), nets 0.38, area 0.15. Pins×layers is the kernel; area is only a spread
discount on **time**, not a scoring axis. Historical `quality_score` ≈ 1000 on
this set and is not a difficulty label.

### 3.1 Computable lower bounds

Computed from placement + netlist (fixture geometry). Cache per board load. Persist
on the result manifest and in `benchmarks.json` as:

- `bounds.min_trace_length_mm` (\(L_{\min}\))
- `bounds.min_via_count` (\(V_{\min}\))
- `bounds.min_bend_count` (\(B_{\min}\))

The current engine writes these into `RoutingResultManifest`. For v1.9 runs, the
harness also stores them so current V2 can be **replayed later** from JSON without
rerouting. v1.9 does not implement V2 scoring.

No conduction-area / plane exception: a `ConductionArea` is a terminal on its layer.

#### \(L_{\min}\) — Manhattan MST

For each net, take terminal XY coordinates (pins and conduction areas). Compute a
Manhattan (\(L_1\)) minimum spanning tree; sum over nets; convert to mm.

- **Why not Euclidean MST:** legal traces are orthogonal / 45°, so \(L_1\) matches
  copper length better. Euclidean is a looser bound (always \(\le\) Manhattan).
- **Why not RSMT:** Rectilinear Steiner can be shorter than MST (T-junctions). It is
  a tighter bound but NP-hard; heuristics add code and time. PCB nets are small, but
  MST is simpler and stable. Clamp \(\Delta L\) at 0 because Steiner routing can beat
  MST.
- **Cost:** Prim/Kruskal \(O(k^2)\) per net for \(k\) terminals — negligible vs
  routing. HPWL is faster (\(O(k)\)) but too weak for multi-pin nets.

#### \(V_{\min}\) — minimum layer switches (via items)

Sum over nets. For each net:

1. Collect distinct signal layers occupied by terminals (pins and pours).
2. If that set has size \(\le 1\): contribute \(0\).
3. Else compute the minimum number of via *items* needed to join those layers given
   the board’s via spans:
   - If a through-via (or any via span that covers all occupied layers) exists:
     contribute \(1\) (one via object can join all layers at a point).
   - Else: greedy cover of the occupied-layer set by allowed via spans (approximately
     “occupied layers minus 1” when only adjacent-layer vias exist).

This is a topological lower bound, not a congestion-aware one. Fanout and
well-separated islands can need more vias than \(V_{\min}\).

#### \(B_{\min}\) and \(B_{\text{actual}}\) — 45° counts as a bend

\(B_{\text{actual}}\) = `bends.totalCount` (polyline internal corners). **Every
corner counts**, including 45°. Do not down-weight or exclude 45° relative to 90°.

For each net, use the same MST topology as \(L_{\min}\). For each tree edge:

- If the two terminals share \(x\) or \(y\) (axis-collinear): \(B_{\min}\)
  contribution \(0\).
- Else: \(1\) (one Manhattan \(L\)). A straight 45° segment can beat this (0
  corners vs 1); clamp \(\Delta B\) at 0.

Multi-pin nets: sum those edge bounds (tree has \(k-1\) edges).

### 3.2 V2 formulation

\(L_{\text{actual}}\) = `traces.totalLengthMm`.
\(V_{\text{actual}}\) = `vias.totalCount`.
\(B_{\text{actual}}\) = `bends.totalCount` (polyline corners, 45° and 90°).

Via and bend excess use \(D\) (§3.0). Length uses \(L_{\min}\).

$$\boxed{\text{score}_{\text{opt}} = \max\left(0.0,\ 1000.0 - \Delta L - \Delta V - \Delta B\right)}$$

$$
\Delta L = W_L \times \max\left(0,\frac{L_{\text{actual}} - L_{\min}}
{\max(L_{\min}, L_{\text{floor}})}\right)
$$

$$
\Delta V = W_V \times \frac{\max(0,\ V_{\text{actual}} - V_{\min})}
{\max(D, D_{\text{floor}})}
$$

$$
\Delta B = W_B \times \frac{\max(0,\ B_{\text{actual}} - B_{\min})}
{\max(D, D_{\text{floor}})}
$$

A legal production board is **not** required to score 1000.

**Synthetic 1000 fixture (`SyntheticPerfectTwoPin`):** one signal layer, one net,
two pins sharing an axis, no obstacles, no pours, no vias required. Expected
\(L_{\text{actual}} = L_{\min}\), \(V = V_{\min} = 0\), \(B = B_{\min} = 0\), no
DRC, no incompletes. Both V2 scores = 1000. Implemented as a unit-test board,
not a PCBench file.

### 3.3 Candidate comparison (decided)

Against the optimizer baseline (best accepted board / pass start):

1. If `incompleteCount_current > incompleteCount_baseline` → reject
   (`CONNECTIVITY_REGRESSION`).
2. Else if `violationCount_current > violationCount_baseline` → reject
   (`DRC_COUNT_REGRESSION`).
3. Else if `score_opt_current > score_opt_baseline` → accept.
4. Else keep incumbent (including ties), including the more-complete but
   uglier case (fewer incompletes, worse optimizer score).

Depth is not used in this gate. Counts come from
`DesignRulesChecker.getAllClearanceViolations()` (deduplicated).

`BatchOptimizer` uses only `getOptimizerScore` after the gate.

### 3.4 Stop condition (decided)

Remove the stop that treats a score near 1000 as “nothing left to do.”

Keep / use `optimizer.optimizationImprovementThreshold` (default `0.01`). V2
scores remain 0–1000; the stop is `(after - before) / before`, so 1% still maps
to about 8–10 points on a typical finished board. Maze-search costs stay
independent of V2 board-score weights.

### 3.5 Internal ETA — dropped

Runtime estimation through \(D \cdot S_{\text{cal}} / S\) is out of scope.
Do not compute or display an autorouter ETA. `cpu_score` may still be
measured at startup for hardware logs and benchmark records. Scoring uses
\(D = C\) only as in §2.1 / §3.2. Maze costs never see \(D\).

---

## 4. Empirical Calibration Using `benchmarks.json`

Today’s file (~5,851 runs) is useful for routability / DRC regression. Historical
v1/v2 rows may not contain length, vias, bends, total violation depth, or lower
bounds, but schema-v5 records now persist the available actuals, lower bounds,
and phase before/after snapshots with score/CPU telemetry.
Both trees already **compute** most actuals; the remaining gap is collecting
schema-v5 current/v1.9 pairs.

### 4.1 Scoring inputs and phase snapshots

The current router score requires connection completeness, clearance-violation
count and depth, and (for the legacy formula) bends, trace length, and vias,
plus the `RoutingCostSettings` weights. V2 router scoring additionally requires
board difficulty (`P`, signal layers, area, `C`, and `D`) and the
`RouterScoreSettings` weights. The optimizer score requires the physical
actuals, lower bounds (`L_min`, `V_min`, `B_min`), difficulty, and
`OptimizerScoreSettings`; its candidate gate separately vetoes connectivity and
DRC-count regressions.

Each phase now has `before` and `after` objects. Each object contains the
captured board statistics, the phase score, the score source, and (when a
current replay is available) separate current router/optimizer score fields.
Native v1.9 scores remain labelled `v19_native`; current DRC replay scores are
labelled `current_drc_replay` and never replace the native score.

### 4.2 v1.9 telemetry (native algorithm unchanged)

Allowed `src_v19` edits: statistics/manifest fields **and** a pre-optimizer snapshot
write (control flow around the existing optimize stage only). Do not reformat or
refactor the rest of `src_v19/`. Do not change v1.9 `calculateNormalizedScore()`.

Required raw paths (same names and units as current):

- `board_statistics.connections.maximum_count`
- `board_statistics.connections.incomplete_count`
- `board_statistics.traces.total_length_mm`
- `board_statistics.vias.total_count`
- `board_statistics.bends.total_count`
- `board_statistics.clearance_violations.total_count`
- `board_statistics.clearance_violations.total_violation_um` (**add**; do not infer as
  average × count)
- min / max / avg violation µm (already present)
- `board_statistics.items.pin_count`
- `board_statistics.nets.total_count` (not DSN `\ (net ` regex)
- `board_statistics.layers.signal_count`

v1.9 `maximum_count` is still the legacy `pins - nets` formula. Treat it as
**v1.9-native** when comparing raw fields; do not pretend it matches current
`maximumCount`. Router V2 ratios for **current** runs must use current
`maximumCount`.

### 4.3 Benchmark record contract

Flatten into `quality` / `bounds` (names illustrative):

- identity: `binary.version_label`, `binary.git_sha`, `fixture.relative_path`,
  `fixture.sha256`, `fixture.tier`
- connections: `max_connections`, `unrouted_connections` (from manifest statistics)
- DRC: count, `total_violation_um`, min / max / avg µm
- physical: `trace_length_mm`, `via_count`, `bend_count`, `pin_count`
- bounds: `min_trace_length_mm`, `min_via_count`, `min_bend_count`, `complexity_c`,
  `difficulty_d`, `board_area_mm2` (from board bounding box, never leave 0 if the
  outline exists)
- scores: `native_score` (binary’s own formula); later
  `current_v2_router_score` / `current_v2_optimizer_score` from offline replay
- `score_source` (`v19_native` | `current_v1` | `current_v2` | `replay_v2`)
- host: `system.cpu_score` (`RuntimeEnvironment.cpuScore`), CPU name/cores
- `schema_version`, `exit.state`, `exit.code`

Preserve nulls. Failed and timed-out runs are not zero-filled.

Pair **current vs v1.9** by `fixture.relative_path`. Use `fixture.sha256` to detect
DSN drift, not as the primary join key.

Do not require V2 rescoring of 2.2.4 or 2.3.0.

#### Offline V2 replay (exact fields)

Recompute without a board only if all of these are present (else skip / null):

- actuals: `incomplete_count`, `max_connections`, DRC count,
  `total_violation_um`, `trace_length_mm`, `via_count`, `bend_count`
- bounds: \(L_{\min}\), \(V_{\min}\), \(B_{\min}\)
- \(C\) inputs: \(P\), \(L\), \(N_{\text{conn}}\), \(A_{\text{mm2}}\)
- `boardUnitToUmFactor` (or unit + resolution)
- scoring version + weight snapshot (`DEFAULT_*` or settings used)

### 4.4 Calibration workflow (script still deferred)

After the schema exists:

1. Successful current and v1.9 runs with complete required fields.
2. Pair by `relative_path`; drop or flag sha mismatch.
3. Stratify by tier, layer count, and `max_connections` (not `total_nets`).
4. Report missingness and p10/p50/p90/p95 for every input and excess term.
5. Use optimizer initial vs final snapshots when the optimizer actually ran; many
   historical rows have optimizer N/A — those cannot teach optimizer weights.
6. Held-out fixtures; check saturation on Tier D, completion, and DRC count.
7. Historical `quality_score` / v1.9 `normalized_score` are native baselines, not
   V2 fitting targets.
8. Do **not** refit a runtime ETA. \(D = C\) is the scoring scale.

Do **not** create the calibration script in the schema/settings phases.

---

## 5. Architectural Implementation Roadmap

Scoring (Phases 1–4, 6–7) must not import runtime-ETA terms into `BatchAutorouter` or
`BatchOptimizer`. `cpu_score` telemetry remains Phase 0 only.

### Implementation tracking

| Item | Phase | Status | Acceptance evidence |
|---|---:|---|---|
| Persist robust median `cpu_score` at startup | 0 | ✅ done | `RuntimeEnvironmentTest`; startup hardware log |
| Persist `cpu_score` in current result manifests | 0 | ✅ done | `RoutingResultManifestTest`; `cpu_score` JSON field |
| Carry `cpu_score` into new benchmark `system` records | 0 | ✅ done | `run-benchmarks.ps1` + manifest/log parser path |
| Allow historical null/missing `system.cpu_score` and derive `cpu_score_effective` | 0 | ✅ done | Validator permits legacy gaps; harness derives current-machine fallback |
| Persist and backfill DSN `fixture.host_version` | 0 | ✅ done | Parser reads `(host_version ...)`; harness migrates historical blanks |
| Version enriched benchmark records as schema v5 | 0 | ✅ done | New records require v5 phase snapshot fields; v1–v4 records remain historical |
| Persist phase before/after snapshots, scores, and CPU seconds | 0 | ✅ done | Current and v1.9 manifests expose native snapshots; benchmark records add replay scores |
| Persist total DRC shortfall in current/v1.9 statistics | 0 | ✅ done | `total_violation_um` in both statistics models |
| Normalize board inputs from `BoardStatistics` | 0 | ✅ done | Benchmark records use manifest statistics, not DSN counts |
| Persist board-only difficulty inputs \(P,L,C,D,A\) | 0–3 | ✅ done | Manifest `difficulty`, bounds, and board area fields |
| Persist remaining raw current/v1.9 routing metrics | 0 | ☐ next | Manifest parity test and replay fixture |
| Rename search-cost settings to `RoutingCostSettings` | 1 | ✅ done | Type rename and focused settings tests |
| Split router and optimizer scoring APIs/settings | 1–2 | ✅ implemented | Independent version settings, score APIs, and legacy aliases |
| Add optimizer baseline/pass score telemetry | 1–2 | ✅ done | `BatchOptimizer` logs and determinism fixture |
| Add lower bounds and V2 formulas | 3–4 | ✅ implemented | Current-tree bounds and V2 paths; offline replay script |
| Calibrate optimizer weights and threshold | 5–6 | ✅ done | Replay CSVs; keep `improvement_threshold` 0.01 (relative) |
| Complete regression and parity verification | 7 | ✅ tests | Maze independence, SyntheticPerfectTwoPin, full DRC, V1 CLI merge |
| Optional settings-hierarchy refactor | 8 | ✅ confirmed | Keep JSON root `router`; nest `autorouter`; `max_threads` stays parent |

### Phase 0: Schema and v1.9 raw telemetry

- [x] Persist `totalViolationUm` in current and v1.9 `BoardStatistics`.
- [x] Persist lower bounds and difficulty inputs (\(C\), \(P\), \(L\),
  \(N_{\text{conn}}\), \(L_{\min}\), \(A\), \(D\)) in the current
  `RoutingResultManifest`; expose bounds both at manifest level and under
  `board_statistics`.
- [x] Attach current-tree fixture bounds to matching v1.9 benchmark rows in
  the harness (without changing the frozen v1.9 engine); rows remain null when
  no matching current-tree run is available.
- [x] Compute current-tree \(L_{\min}\), \(V_{\min}\), and \(B_{\min}\) from
  board terminals and serialize them under `board_statistics.bounds`.
- [x] Add the same lower-bound fields to v1.9 harness records through the
  benchmark harness; values remain unavailable if no matching current run exists.
- [x] Verify lower-bound schema parity before replay, including explicit handling
  of unavailable values (validator + `schema_v5_pair_walkthrough.py`; do not
  rewrite the full historical `benchmarks.json`).
- [x] Export pin count, signal layer count, and net count from `BoardStatistics`,
  not DSN regex. Fix area from the board outline bounding box.
- [x] Export router-final actuals; optimizer-initial and optimizer-final snapshots
  on **current and v1.9** when the optimizer runs.
- [ ] Flatten the same raw fields into `benchmarks.json`; preserve nulls (partial
  current-board-statistics flattening is implemented).
- [x] Persist `system.cpu_score` on each **new** benchmark run (same value as
  `RuntimeEnvironment.cpuScore`).
- [x] Persist `fixture.host_version` from the DSN `(host_version ...)` field and
  backfill historical blank values when the source fixture is available.
- [x] Allow legacy rows to retain a null or missing `system.cpu_score`; do not
  zero-fill historical telemetry.
- [x] Derive `system.cpu_score_effective` from the current machine's score when
  a legacy row needs a CPU value for calculation.
- [x] Version the enriched benchmark record shape as `schema_version: 5`;
  schema v1–v4 records are historical and may omit newer fields such as
  `settings`, `bounds`, `drc`, and phase score/CPU telemetry.
- [x] Persist `phases.*.before` and `phases.*.after` snapshots, their calculated
  phase scores, and `cpu_seconds`; current and v1.9 native scores are retained.
- [x] Include full BoardStatistics snapshots at current fanout/autorouter/
  optimizer boundaries and v1.9 autorouter/optimizer boundaries.
- [x] Record current-version router and optimizer replay scores from the DRC
  pass as separate fields on benchmark rows; never overwrite native v1.9 scores.
- [x] Normalize persisted CPU scores to the scaled unit (raw throughput divided
  by 1000), including historical values used by the benchmark harness.
- [x] Emit benchmark and result-manifest floating-point values with exactly two
  decimal places while retaining integer fields as integers.
- [x] Populate schema-v5 fields for newly executed current and v1.9 runs;
  after both stages complete, backfill every available bounds field from the
  matching current-tree run into the v1.9 row.
- [x] Add current-version DRC replay router and optimizer scores to v1.9
  benchmark rows without overwriting `v19_native` scores.
- [ ] Verify current/v1.9 schema parity with at least one paired schema-v5
  fixture; the existing dataset validates per-run but contains no such pairs.
- [x] Update `docs/settings.md` and `docs/architecture.md` when settings/APIs land
  (canonical equations and symbol glossary: `docs/scoring.md`).

### Phase 1: Settings split and versions

- [x] `RouterScoringVersion` / `OptimizerScoringVersion`.
- [x] Independent nullable router and optimizer scoring settings; maze costs stay
  search-only.
- [x] `DEFAULT_*` in `DefaultSettings`; V2 is the default for both score formulas,
  with V1 available through explicit settings/CLI overrides.
- [x] Independent CLI/settings: `router.scoring.version`,
  `optimizer.scoring.version`, short `--router-scoring-version` /
  `--optimizer-scoring-version`, and convenience `--scoring-version` for both.
- [x] Nested merge that does not break `SettingsMerger`.

### Phase 2: V1-preserving API split

- [x] `getRouterScore` / `getOptimizerScore` (optimizer formula still uses the
  legacy implementation).
- [x] Point all router/history paths at router score; `BatchOptimizer` at optimizer
  score + the decided score-ranking gate (connectivity/DRC vetoes only).
- [x] Remove optimizer “close to 1000” stop; keep improvement-threshold stop
  (recalibrate default later).
- [x] Explicit score fields: keep API `normalized_score` = router score; add
  `optimizer_score`. Deprecate `getNormalizedScore()` as a router-score alias.
- [x] V1 ±10 historical-score parity is **not required** (V1 change is negligible).

### Phase 3: Lower bounds (current tree only)

- [x] Wire, via, and bend lower bounds (§3.1) for current-tree statistics and
  write them to JSON. Cache-on-load and v1.9 harness support remain.
- [x] Zero-length / empty-net / mixed-layer definitions
  (`fixtures/scoring-*.dsn` + `BoardStatisticsBoundsCalculatorTest`).
- [x] No conduction-area conditional (pours count as layer terminals).
- [x] 45° corners included in \(B_{\text{actual}}\).

### Phase 4: V2 formulas on current tree

- [x] Implement §2 / §3 V2 using settings weights (router and optimizer paths
  are implemented).
- [ ] Calibrate **optimizer** V2 weights and the improvement threshold from
  current-vs-v1.9 replay (router weights stay at `DEFAULT_*` unless DRC
  saturates).
- [x] Default V2 on; V1 via setting/CLI.
- [x] Offline replay of current V2 onto stored current and v1.9 JSON
  (`scripts/benchmark/replay_v2_scores.py`).

### Phase 5: Calibration design (no script yet)

- [ ] Report shape, held-out set, noise floor, saturation checks.
- [ ] Optionally check whether via-excess\(/D\) is flat across density; only then
  consider a residual density factor.
- [x] ETA refit cancelled; \(D = C\).

### Phase 6: Calibrated defaults

- [x] Activate V2 `DEFAULT_*` values; refine them from current-vs-v1.9 analysis.
- [x] Recalibrate `optimizer.optimizationImprovementThreshold`.
  Kept at `0.01` (relative to the incumbent optimizer score). V2 did not change
  the 0–1000 scale of that comparison.
- [x] Verify settings precedence and V1 CLI fallback.

### Phase 7: Verification

- [x] Router and optimizer scores are independent; maze costs unchanged when V2
  weights change (`ScoringMazeCostIndependenceTest`).
- [x] Version flags select V1 vs V2.
- [x] `SyntheticPerfectTwoPin` scores 1000 on both V2 scores; pour boards are not
  required to.
- [x] Zero-connection boards are defined (`BoardStatisticsTest`).
- [x] Gate: more incompletes or higher DRC count reject; ranking is by
  optimizer score (more-complete uglier boards do not auto-win); equal scores
  keep the incumbent. Timeout rip-up that adds incompletes is rejected.
- [x] Optimizer stops on improvement threshold, not proximity to 1000. The
  improvement threshold stays `0.01` (relative); see `DefaultSettings`.
- [x] V1 ±10 parity dropped (not needed).
- [x] Raw-metric schema parity current vs v1.9 including pre/post optimizer snapshots;
  v1.9 score algorithm unchanged. Walkthrough: `schema_v5_pair_walkthrough.py`
  / `docs/research/schema_v5_current_v19_pair.md`. Historical rows stay unstamped;
  new records are schema 5.
- [x] V2 replay from JSON does not require a reroute (`replay_v2_scores.py`).
- [x] Full DRC uses `getAllClearanceViolations()` (`BoardStatistics` live path and
  `BoardStatisticsClearancePathTest`).
- [x] No completion / DRC-count regression vs the previous current default (offline
  replay of existing PCBench snapshots; no new overnight route).

### Phase 8: Settings-hierarchy refactor (confirmed)

Purpose: make stage ownership clearer without changing routing behavior or scoring
semantics. JSON root stays **`router`**. Do not rename it to `routing` in this
phase.

Canonical nest:

```
router.fanout.*
router.autorouter.*     // new AutorouterSettings, composition not inheritance
router.optimizer.*
router.scoring          // maze / search costs
router.router_scoring
router.optimizer_scoring
```

`AutorouterSettings` holds stage execution only: `enabled`, `algorithm`,
`max_passes`, `max_items`, `save_intermediate_stages`, `ignore_net_classes`.
Java callers use `routerSettings.autorouter.maxPasses` (no parent field).

Stay on the **parent** `RouterSettings`:

- `max_threads` (shared by autorouter pass parallelism and optimizer GUI workers)
- engine policy: `viasAllowed`, `automaticNeckdown`, `strictDrc`, `neckWidthUm`,
  `tracePullTightAccuracy`, copper/hole clearances, layers
- job extras: `job_timeout`, `result_json`
- maze costs and both V2 score objects

Do **not** add a `policy`/`engine` nest in this phase. Do not fold scores into
`autorouter`.

Compatibility: Gson `alternate` cannot map `router.max_passes` →
`router.autorouter.max_passes`. Use an explicit read-side bridge. Flat CLI
(`--router.max_passes`, `-mp`) and env (`FREEROUTING__ROUTER__MAX_PASSES`) still
apply, and **warn** that those paths will be removed soon. Nested keys
(`--router.autorouter.max_passes`, `FREEROUTING__ROUTER__AUTOROUTER__MAX_PASSES`)
are the supported form. Canonical JSON **writes** the nested shape only.
The v1.9 `StartupOptions` parser also accepts `--router.autorouter.*` and maps
those keys onto its flat knobs so one benchmark command line works for both jars.

Surfaces in the same change: GUI autoroute-parameter widgets, OpenAPI /
`docs/API/API_v1.md`, in-repo scripts (benchmark runner, PCBench calibrate,
compare-versions, fanout benchmarks, autopilot spikes), `docs/settings.md`,
`docs/architecture.md`.

PCBench **quality corpus does not need a rerun or JSON migration**. Outcomes
are independent of the settings path. Update harness CLI flags in the same PR.
Cache keys include git/jar SHA, so a new commit can cache-miss `scoring-revision`
without invalidating historical 2.2.4 / 2.3.0 / 2.4.0-RC1 rows.

- [x] Confirm that the refactor should proceed after scoring Phases 0–7.
- [x] Introduce `AutorouterSettings` by composition.
- [x] Move autorouter-stage execution fields (not `max_threads`).
- [x] Keep `fanout` and `optimizer` as separate stage settings; keep engine
  policy and `max_threads` on the parent.
- [x] Keep the serialized root name `router`.
- [x] Read-side bridge for flat `router.max_passes` / `enabled` / `algorithm` /
  `max_items` / `save_intermediate_stages` / `ignore_net_classes`; warn on CLI
  and env use of those keys.
- [x] Round-trip, legacy-read, precedence, CLI, GUI, API, merge, and script
  tests before removing the bridge.
- [x] Update docs and all settings-path consumers after compatibility tests
  pass.

---

## 6. Remaining decisions

Closed: D2 no DRC cap; completeness is not an automatic win over a worse
optimizer score (veto only if completeness or DRC count regresses); D3 keep
incumbent on score ties; D4 later V2 replay; D5 Manhattan MST; D6 layer-switch
\(V_{\min}\) + Manhattan one-bend \(B_{\min}\) with 45° counted in actuals; D7 v1.9
pre/post optimizer snapshots; D8 optimizer weights \(W_L=1000\), \(W_V=2000\), \(W_B=500\);
D9 deprecated alias + `optimizer_score`; CLI allows both settings-path keys and
short flags; router and optimizer versions are independent; timeout/rip-up that
increases incompletes is rejected; improvement threshold stays 0.01 (relative); \(D = C = \max(1,\ P \times L)\); ETA dropped; V1
±10 parity is not required; \(C = P \times L\) (not \(N_{\text{conn}}\)). D10: do
not use inheritance for score settings;
do not add `CommonScoreSettings` yet; keep router and optimizer score settings
independent. D11: keep DRC settings separate from router settings because DRC
report configuration and board design rules have different ownership and
lifecycle.

### Still to decide

No structural scoring decision remains open. Phase 8 is confirmed: keep
`router`, nest `autorouter`, leave `max_threads` on the parent, require
`routerSettings.autorouter.maxPasses` in Java, warn on deprecated flat CLI/env
keys, retarget GUI / OpenAPI / scripts. A later `policy`/`engine` nest is optional
and out of scope here.

Optimizer \(W_L=1000\), \(W_V=2000\), \(W_B=500\) (D8).
`optimizationImprovementThreshold` is still 0.01 (relative). Chosen router
defaults are \(W_1=1000/3\), \(W_2=2000/3\), \(W_C=25\), \(W_D=300\).

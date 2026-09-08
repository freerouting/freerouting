# Freerouting Dedicated Scoring Architecture & Versioning Plan

**Document Status:** Implementation Plan
**Date:** September 8, 2026 (Revised with decided defaults and remaining open questions)
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

**Complexity \(C\), difficulty \(D\), and internal ETA share one polynomial**
(§3.0 / §3.5). \(D = \max(1,\ \widehat{t}_{\text{cal}})\), i.e. proportional to
estimated single-thread time after removing host speed. Live `cpu_score` \(S\)
is used only to convert that to seconds on this machine. ETA is **internal**
(logs / research / future job sizing), not shown in the GUI or API yet.

The **current** tree defaults to V2 for both router and optimizer scores, with a
settings / CLI override to run V1 legacy. **`src_v19` keeps its existing scoring
algorithm unchanged.** v1.9 only gains raw-metric telemetry so `benchmarks.json` can
store the same physical fields.

Live GUI/API ETA is out of scope. Phase 0 persists `cpu_score` and board
inputs so internal ETA / \(D\) can be computed. Do not display an ETA in the UI.

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

Target default in `DefaultSettings`: `V2_CONTINUOUS`. The implementation currently
retains `V1_LEGACY` until the V2 formula and calibration gates pass. Independently overridable via
settings path (`router.scoring.version`) and a short CLI flag
(`--router-scoring-version v1`). Router and optimizer version enums are **not**
forced to stay in sync.

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

Target default in `DefaultSettings`: `V2_LOWER_BOUND`. The implementation currently
retains `V1_LEGACY` until lower bounds and replay calibration are complete. Independently overridable via
settings path (`optimizer.scoring.version`) and a short CLI flag
(`--optimizer-scoring-version v1`). A convenience `--scoring-version v1` may set
**both** to V1 without coupling the two enums in code.

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

V1 defaults remain the current `DefaultSettings` values. V2 weights ship first as
named uncalibrated `DEFAULT_*` placeholders; they will be set manually after test
runs, then optionally refined from current-vs-v1.9 JSON replay.

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
  setting, default `0.01`). **Recalibrate that default after V2 scores land.**
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

$$\text{penalty}_{\text{unrouted}} = W_{\text{unrouted}} \times \left(\frac{N_{\text{unrouted}}}{N_{\text{conn}}}\right)$$

- \(N_{\text{conn}}\) is `connections.maximumCount`, never fixture `net_count`.
- If \(N_{\text{unrouted}} = 0\): penalty \(= 0\).
- If \(N_{\text{conn}} = 0\) and there are no violations: score \(= 1000\) (defined;
  not `NaN` / 0).
- If \(N_{\text{conn}} = 0\) and there are violations: apply only the DRC penalty.
- Default \(W_{\text{unrouted}} = 1000\) so a fully open board with no DRC scores 0.
  The field remains configurable.

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

**One polynomial, two uses:**

| Symbol | Role | Units | Live \(S\)? |
|---|---|---|---|
| \(C\) | \(P \times L\) (decided) | pin-layers | No |
| \(\widehat{t}_{\text{cal}}\) | Estimated CPU seconds on the calibration host | seconds | No |
| \(D\) | Score denominator \(= \max(1,\ \widehat{t}_{\text{cal}})\) | cal-host seconds | **No** |
| \(\widehat{t}(S)\) | Internal ETA \(= D \cdot S_{\text{cal}} / S\) | seconds | Yes |
| \(W\) | \(D \cdot S_{\text{cal}}\) | iterations | Constant \(S_{\text{cal}}\) only |

**“\(D \propto\) estimated time / CPU score.”** \(S\) is *throughput* (higher =
faster). Host-independent work is \(\widehat{t} \cdot S\), not \(\widehat{t}/S\)
(the latter would make a board look easier on a faster CPU). That work in
calibration-host seconds is \(\widehat{t}_{\text{cal}}\), which we use as \(D\).

**\(\alpha\):** unused while \(D\) is this time polynomial. It was a leftover
density factor in the older form \(C(1+\alpha L_{\min}/\sqrt{A})\). Revisit only
if via-excess\(/D\) still trends with density after length/via fields exist.

#### Kernel (decided)

$$
C = \max(1,\ P \times L)
$$

\(P\) = pin count from `BoardStatistics.items.pin_count` (not DSN regex).
\(L\) = signal layer count. Fixture `net_count` from `\ (net ` is unreliable
(often 0) and is **not** \(C\). \(N_{\text{conn}}\) stays the unrouted-fraction
denominator only; it is not used inside \(D\).

$$
D = \max\bigl(1,\ 19.64 + 0.0810 \cdot C - 0.131 \cdot A\bigr)
$$

\(A\) is `board_area_cm2`. \(D_{\text{floor}} = 1\). Unrouted remains
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

Keep / use `optimizer.optimizationImprovementThreshold` (default `0.01`). After
V2 changes the meaning of a 1% score move, **recalibrate this default**. Maze-search
costs stay independent of V2 board-score weights.

### 3.5 Internal single-thread ETA (same polynomial as \(D\))

**Internal only** — logs, research, job sizing. Not shown in GUI or API.

Measured \(S =\) `RuntimeEnvironment.cpuScore` (iterations/ms). Always use the
**measured** \(S\) at runtime.

**Calibration host** (same machine as the optimizer-unification benchmarks), new
median method, four runs: 415743, 416586, 417002, 419664.

$$
S_{\text{cal}} = 416794
$$

(median of those four). Spread is ~1%. Do **not** mix with the old 15 ms scores
(~28 000–34 000); the kernel and timing changed.

$$
\widehat{t}(S) = D \cdot \frac{S_{\text{cal}}}{S}
$$

Scoring uses \(D\) only as in §2.1 / §3.2. Maze costs never see \(D\) or \(S\).

#### CPU micro-benchmark robustness

`measureCpuScore()` is a geometric kernel (bbox overlap, orientation, Manhattan
steps), not SPEC. To reduce the 15 ms turbo/GC noise:

- discard a ~20 ms warmup
- take 5 samples of ~40 ms
- return the **median** iterations/ms
- persist that value on each result manifest and on `system.cpu_score` in
  `benchmarks.json`

It still will not match a Cinebench-class bench. If later samples disagree with
routing time more than ~2× across hosts, replace the kernel — keep the median
protocol.

#### Experiment (36 boards, `cpu_seconds >= 30`)

Duration excluded. Nets and additive layer/component terms dropped.

$$
\widehat{t}_{\text{cal}} = \max\bigl(0,\ 19.64 + 0.0810 \cdot C - 0.131 \cdot A\bigr)
= D \quad \text{(once floored at 1)}
$$

\(R^2 = 0.78\), MAE 17.5 s. Predicts completed-job CPU seconds (autorouter +
optimizer) on the calibration host, not wall clock or timeouts.

\(S_{\text{cal}} = 416794\) (median of four local runs of the new scorer).
On that host \(\widehat{t}(S) \approx D\).

---

## 4. Empirical Calibration Using `benchmarks.json`

Today’s file (~5,851 runs) is useful for routability / DRC regression. It does not
yet persist length, vias, bends, total violation depth, or lower bounds. Both trees
already **compute** most actuals; the gap is persisting them.

### 4.1 v1.9 telemetry (no scoring port)

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

### 4.2 Benchmark record contract

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

### 4.3 Calibration workflow (script still deferred)

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
8. Refit ETA/\(D\) coefficients on clean boards with persisted \(S\).

Do **not** create the calibration script in the schema/settings phases.

---

## 5. Architectural Implementation Roadmap

Scoring (Phases 1–4, 6–7) must not import ETA/\(W\) into `BatchAutorouter` or
`BatchOptimizer`. Telemetry for \(C\)/\(S\) is Phase 0 only.

### Implementation tracking

| Item | Phase | Status | Acceptance evidence |
|---|---:|---|---|
| Persist robust median `cpu_score` at startup | 0 | ✅ done | `RuntimeEnvironmentTest`; startup hardware log |
| Persist `cpu_score` in current result manifests | 0 | ✅ done | `RoutingResultManifestTest`; `cpu_score` JSON field |
| Carry `cpu_score` into new benchmark `system` records | 0 | ✅ done | `run-benchmarks.ps1` + manifest/log parser path |
| Backfill or explicitly classify historical rows missing `system.cpu_score` | 0 | ☐ pending | Validator passes with documented legacy missingness policy |
| Persist total DRC shortfall in current/v1.9 statistics | 0 | ✅ done | `total_violation_um` in both statistics models |
| Normalize board inputs from `BoardStatistics` | 0 | ✅ done | Benchmark records use manifest statistics, not DSN counts |
| Persist board-only difficulty inputs \(P,L,C,D,A\) | 0–3 | ◐ scaffolded | Manifest `difficulty` and board area fields |
| Persist remaining raw current/v1.9 routing metrics | 0 | ☐ next | Manifest parity test and replay fixture |
| Rename search-cost settings to `RoutingCostSettings` | 1 | ✅ done | Type rename and focused settings tests |
| Split router and optimizer scoring APIs/settings | 1–2 | ◐ scaffolded | Independent version settings and legacy score aliases |
| Add optimizer baseline/pass score telemetry | 1–2 | ✅ done | `BatchOptimizer` logs and determinism fixture |
| Add lower bounds and V2 formulas | 3–4 | ◐ scaffolded | Current-tree bounds/V2 path; replay calibration pending |
| Calibrate weights and optimizer threshold | 5–6 | ☐ pending | Held-out current-v1.9 report |
| Complete regression and parity verification | 7 | ☐ pending | Required Gradle gates and fixture results |
| Optional settings-hierarchy refactor | 8 | ☐ confirmation required | Explicit approval, compatibility tests, and migration review |

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
- [ ] Verify lower-bound schema parity before replay, including explicit handling
  of unavailable values.
- [x] Export pin count, signal layer count, and net count from `BoardStatistics`,
  not DSN regex. Fix area from the board outline bounding box.
- [ ] Export router-final actuals; optimizer-initial and optimizer-final snapshots
  on **current and v1.9** when the optimizer runs.
- [ ] Flatten the same raw fields into `benchmarks.json`; preserve nulls (partial
  current-board-statistics flattening is implemented).
- [x] Persist `system.cpu_score` on each **new** benchmark run (same value as
  `RuntimeEnvironment.cpuScore`).
- [ ] Complete the explicit benchmark schema parity check for field names and
  missingness; the validator exists, but historical rows such as
  `PCBench/1-Wire-Wing-pcb_1-Wire_Wing/unrouted.dsn` still lack
  `system.cpu_score`.
- [ ] Update `docs/settings.md` and `docs/architecture.md` when settings/APIs land
  (settings documentation is updated; architecture documentation remains).

### Phase 1: Settings split and versions

- [x] `RouterScoringVersion` / `OptimizerScoringVersion`.
- [x] Independent nullable router and optimizer scoring settings; maze costs stay
  search-only.
- [ ] `DEFAULT_*` in `DefaultSettings`; target default versions are V2, but the
  implementation remains V1 until formula and calibration gates pass.
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
- [ ] V1 path must reproduce current fixture scores within **±10** score points.

### Phase 3: Lower bounds (current tree only)

- [x] Wire, via, and bend lower bounds (§3.1) for current-tree statistics and
  write them to JSON. Cache-on-load and v1.9 harness support remain.
- [ ] Zero-length / empty-net / mixed-layer definitions.
- [ ] No conduction-area conditional (pours count as layer terminals).
- [ ] 45° corners included in \(B_{\text{actual}}\).

### Phase 4: V2 formulas on current tree

- [x] Implement §2 / §3 V2 using settings weights (router and optimizer paths
  are implemented).
- [ ] Calibrate V2 weights and validate current/v1.9 replay before enabling V2
  as the default.
- [ ] Default V2 on; V1 via setting/CLI.
- [ ] Offline replay of current V2 onto stored current and v1.9 JSON (harness /
  later tool, not the v1.9 binary).

### Phase 5: Calibration design (no script yet)

- [ ] Report shape, held-out set, noise floor, saturation checks.
- [ ] Optionally check whether via-excess\(/D\) is flat across density; only then
  consider a residual density factor.
- [ ] Refit \(\widehat{t}_{\text{cal}}(C,A)\) with persisted \(S\).

### Phase 6: Calibrated defaults

- [ ] Set V2 `DEFAULT_*` from current-vs-v1.9 analysis.
- [ ] Recalibrate `optimizer.optimizationImprovementThreshold`.
- [ ] Verify settings precedence and V1 CLI fallback.

### Phase 7: Verification

- [ ] Router and optimizer scores are independent; maze costs unchanged when V2
  weights change. ETA/\(W\) does not change maze or scores.
- [ ] Version flags select V1 vs V2.
- [ ] `SyntheticPerfectTwoPin` scores 1000 on both V2 scores; pour boards are not
  required to.
- [ ] Zero-connection boards are defined.
- [x] Gate: more incompletes or higher DRC count reject; ranking is by
  optimizer score (more-complete uglier boards do not auto-win); equal scores
  keep the incumbent. Timeout rip-up that adds incompletes is rejected.
- [x] Optimizer stops on improvement threshold, not proximity to 1000. The
  improvement threshold remains subject to later recalibration.
- [ ] V1 legacy reproduces historical current-tree scores within ±10 points.
- [ ] Raw-metric schema parity current vs v1.9 including pre/post optimizer snapshots;
  v1.9 score algorithm unchanged.
- [ ] Later V2 replay from JSON does not require a reroute (all §4.2 fields present).
- [ ] Full DRC uses `getAllClearanceViolations()`.
- [ ] No completion / DRC-count regression vs the previous current default.

### Phase 8: Optional settings-hierarchy refactor (confirmation required)

This is a **final, optional step** after Phases 0–7 are complete. Do not begin
this phase without explicit confirmation. Its purpose is to make stage ownership
clearer without changing routing behavior or scoring semantics.

- [ ] Confirm that the refactor should proceed after the scoring implementation
  and verification gates pass.
- [ ] Introduce an `AutorouterSettings` object by composition, not inheritance.
- [ ] Move the autorouter-stage execution fields from `RouterSettings` into
  `AutorouterSettings`: `enabled`, `algorithm`, `maxPasses`, `maxItems`,
  `maxThreads`, `saveIntermediateStages`, and `ignoreNetClasses`.
- [ ] Keep `fanout` and `optimizer` as separate stage settings. Keep shared
  route-engine policy (`viasAllowed`, `automaticNeckdown`, `strictDrc`,
  `neckWidthUm`, `tracePullTightAccuracy`, and related layer/cost settings)
  outside `AutorouterSettings`.
- [ ] Decide and document the final top-level name (`routing` versus the current
  `router`) before changing serialized structure.
- [ ] Preserve compatible field aliases with Gson
  `@SerializedName(value = "...", alternate = {"..."})` wherever the old and new
  names refer to the same serialized field.
- [ ] Do not assume `alternate` alone handles the old flat-to-new nested shape:
  `router.max_passes` and `routing.autorouter.max_passes` require an explicit
  migration/normalization path or a compatibility bridge.
- [ ] Add round-trip, legacy-read, precedence, CLI, GUI, API, and settings-merge
  tests before removing the legacy serialization bridge.
- [ ] Update `docs/settings.md`, `docs/architecture.md`, and all affected
  settings-path consumers only after compatibility tests pass.

---

## 6. Remaining decisions

Closed: D2 no DRC cap; completeness is not an automatic win over a worse
optimizer score (veto only if completeness or DRC count regresses); D3 keep
incumbent on score ties; D4 later V2 replay; D5 Manhattan MST; D6 layer-switch
\(V_{\min}\) + Manhattan one-bend \(B_{\min}\) with 45° counted in actuals; D7 v1.9
pre/post optimizer snapshots; D8 placeholder weights (set manually after test runs);
D9 deprecated alias + `optimizer_score`; CLI allows both settings-path keys and
short flags; router and optimizer versions are independent; timeout/rip-up that
increases incompletes is rejected; improvement threshold stays 0.01 until
post-V2 recalibration; \(D = \max(1,\ \widehat{t}_{\text{cal}})\) shares the ETA
polynomial; live \(S\) is not in \(D\); ETA is internal-only; V1 reproduction
tolerance is ±10 points; \(C = P \times L\) (not \(N_{\text{conn}}\));
\(S_{\text{cal}} = 416794\). D10: do not use inheritance for score settings;
do not add `CommonScoreSettings` yet; keep router and optimizer score settings
independent. D11: keep DRC settings separate from router settings because DRC
report configuration and board design rules have different ownership and
lifecycle.

### Still to decide

No structural scoring decision remains open for the current scoring phase. The
optional Phase 8 settings-hierarchy refactor still requires explicit confirmation
after the scoring work is complete. If common score values emerge later, add them
by composition after a concrete use case is identified.

Placeholder \(W_*\) / \(U_{\text{scale}}\) /
\(L_{\text{floor}}\) stay uncalibrated until after test runs (D8). Phase 0
continues with the remaining raw-metric and schema-parity work.

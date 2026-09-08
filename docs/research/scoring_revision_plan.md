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

**Complexity \(C\), difficulty \(D\), and work \(W\) share one board kernel**
(§3.0 / §3.5). \(C\) is computed from the board only (first cut: \(P \times L\)).
\(D\) is that kernel used as a score denominator. Single-thread ETA is an affine
function of the same kernel, converted with measured `cpu_score` \(S\). Using
the same *integer* for via-penalty and for CPU-seconds is the naive part (units
and host speed). Putting \(S\) into \(D\) would make quality scores depend on the
machine.

The **current** tree defaults to V2 for both router and optimizer scores, with a
settings / CLI override to run V1 legacy. **`src_v19` keeps its existing scoring
algorithm unchanged.** v1.9 only gains raw-metric telemetry so `benchmarks.json` can
store the same physical fields.

Live GUI/API ETA from this formula is **not** part of scoring Phases 1–4.
Phase 0 only persists the inputs (`cpu_score`, pin count, layers, area,
\(N_{\text{conn}}\)). Do not ship the n=36 fit as a user-facing progress bar until
\(S\) is on every run and the fit is refit after length/via fields exist.

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

Default in `DefaultSettings`: `V2_CONTINUOUS`. Independently overridable via
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

Default in `DefaultSettings`: `V2_LOWER_BOUND`. Independently overridable via
settings path (`optimizer.scoring.version`) and a short CLI flag
(`--optimizer-scoring-version v1`). A convenience `--scoring-version v1` may set
**both** to V1 without coupling the two enums in code.

### 1.3 Independent configurable settings

Keep maze-search cost fields on the existing scoring/router settings object so maze
behavior does not change when V2 board-score weights are calibrated.

Add two independent nullable settings sections (names may wrap today’s
`ScoringSettings` for serialization compatibility, but merge must copy nested
fields explicitly — `ReflectionUtil.copyFields` is same-class only):

- `RouterScoringSettings`
  - `version` (`RouterScoringVersion`)
  - unrouted-connection weight
  - clearance-violation-count weight
  - clearance-violation-depth weight and depth scale
- `OptimizerScoringSettings`
  - `version` (`OptimizerScoringVersion`)
  - excess wire-length weight
  - excess via weight
  - excess bend weight
  - length floor and difficulty-scale floor
  - density coefficient \(\alpha\) for \(D\) (placeholder, tunable)

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

**One kernel, two uses (not two independent fits):**

| Symbol | Role | Units | May include `cpu_score`? |
|---|---|---|---|
| \(C\) | Board complexity from geometry/netlist | pin-layers (first cut) | No |
| \(D\) | Score denominator = \(C\) times optional density | same as \(C\) | **No** |
| \(\widehat{t}\) | Single-thread ETA | seconds | Yes: \(\times S_{\text{cal}}/S\) |
| \(W\) | Machine-independent work \(= \widehat{t}_{\text{cal}} \cdot S_{\text{cal}}\) | score-iterations | Yes |

This is not naive if \(D\) and ETA share \(C\). It *is* naive to plug measured
CPU seconds (or \(W\)) into the via/bend/DRC denominator: scores would move when
\(S\) jitters (typically 28 000–34 000, sometimes higher).

#### First-cut kernel (until \(N_{\text{conn}}\) and length/vias exist)

$$
C = \max(1,\ P \times L)
$$

\(P\) = pin count from `BoardStatistics.items.pin_count` (not DSN regex).
\(L\) = signal layer count. Fixture `net_count` from `\ (net ` is unreliable
(often 0) and is **not** \(C\).

When `connections.maximumCount` is persisted, decide whether to switch \(C\) to
\(N_{\text{conn}}\) (still open). Until then \(C = P L\).

$$
D = \max(1,\ C) \times \bigl(1 + \alpha \cdot \delta\bigr), \qquad
\delta = \frac{L_{\min}}{\sqrt{\max(A_{\text{mm2}}, 1)}}
$$

\(\alpha = 0\) until length/via fields exist \(\Rightarrow D = C\).
\(D_{\text{floor}} = 1\). Fit \(\alpha\) later so \((V - V_{\min}) / D\) is
roughly flat across density quartiles on clean boards — **not** by regressing
\(D\) on CPU.

Unrouted remains \(N_{\text{unrouted}} / N_{\text{conn}}\). \(\Delta L\) uses
\(L_{\min}\), not \(D\). Only vias, bends, and DRC divide by \(D\).

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

### 3.5 Single-thread ETA from the same kernel

**Goal:** single-thread ETA and score normalization both start from \(C\).
Measured \(S =\) `RuntimeEnvironment.cpuScore` (iterations/ms). Observed range on
the calibration workstation: about **28 000–34 000**, occasionally higher. Always
use the **measured** \(S\) for that process; do not hard-code 31 000.

$$
\widehat{t}_{\text{seconds}} = \frac{W}{S}, \qquad
W = \widehat{t}_{\text{cal}} \cdot S_{\text{cal}}
$$

\(W\) and \(D\) must **not** enter maze costs. Scoring uses \(D\) only as in
§2.1 / §3.2. ETA code, if added later, reads \(C\), \(A\), and \(S\) only.

#### Experiment (36 boards, `cpu_seconds >= 30`)

Duration excluded. Nets and additive layer/component terms dropped.

$$
\widehat{t}_{\text{cal}} = \max\bigl(0,\ 19.64 + 0.0810 \cdot C - 0.131 \cdot A\bigr)
$$

with \(C = P L\) and \(A\) = `board_area_cm2`. \(R^2 = 0.78\), MAE 17.5 s.
The additive \(a + bP + cL + dC_{\text{comp}} + eA\) is worse (MAE 21.3 s).
Two boards have \(A = 0\) (metadata bug); they get no spread discount. The
\(\max(0,\cdot)\) floor stops a huge sparse board from going negative.

This predicts **completed-job CPU seconds** (autorouter + optimizer) on the
calibration host, not wall clock, not multi-thread optimizer, not timeouts.

\(S_{\text{cal}}\) was not stored. Working stand-in until the next benchmark
writes it: \(S_{\text{cal}} \approx 31000\). Then
\(\widehat{t}(S) = \widehat{t}_{\text{cal}} \cdot S_{\text{cal}} / S\).

Refit \(a,b,c\) (and whether \(C\) becomes \(N_{\text{conn}}\)) after Phase 0
schema exists. Do not mix that refit with the \(\alpha\) fit.

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
8. Refit ETA coefficients on clean boards with persisted \(S\); fit \(\alpha\)
   separately on via-excess vs density.

Do **not** create the calibration script in the schema/settings phases.

---

## 5. Architectural Implementation Roadmap

Scoring (Phases 1–4, 6–7) must not import ETA/\(W\) into `BatchAutorouter` or
`BatchOptimizer`. Telemetry for \(C\)/\(S\) is Phase 0 only.

### Phase 0: Schema and v1.9 raw telemetry

- [ ] Persist `totalViolationUm` in current and v1.9 `BoardStatistics`.
- [ ] Persist lower bounds and difficulty inputs (\(C\), \(P\), \(L\),
  \(N_{\text{conn}}\), \(L_{\min}\), \(A\), \(D\), \(\alpha\)) in current
  `RoutingResultManifest`; attach fixture-derived bounds in the harness for v1.9
  rows (needed for later V2 replay).
- [ ] Export pin count, signal layer count, and net count from `BoardStatistics`,
  not DSN regex. Fix area from the board outline bounding box.
- [ ] Export router-final actuals; optimizer-initial and optimizer-final snapshots
  on **current and v1.9** when the optimizer runs.
- [ ] Flatten the same raw fields into `benchmarks.json`; preserve nulls.
- [ ] Persist `system.cpu_score` on each benchmark run (same value as
  `RuntimeEnvironment.cpuScore`).
- [ ] Manifest schema parity test (field names, types, units, missingness) for
  current vs v1.9. Values need not match.
- [ ] Update `docs/settings.md` and `docs/architecture.md` when settings/APIs land.

### Phase 1: Settings split and versions

- [ ] `RouterScoringVersion` / `OptimizerScoringVersion`.
- [ ] Independent nullable router and optimizer scoring settings; maze costs stay
  search-only.
- [ ] `DEFAULT_*` in `DefaultSettings`; default versions V2 with uncalibrated
  placeholder weights.
- [ ] Independent CLI/settings: `router.scoring.version`,
  `optimizer.scoring.version`, short `--router-scoring-version` /
  `--optimizer-scoring-version`, and convenience `--scoring-version` for both.
- [ ] Nested merge that does not break `SettingsMerger`.

### Phase 2: V1-preserving API split

- [ ] `getRouterScore` / `getOptimizerScore`.
- [ ] Point `BatchAutorouter` / history at router score; `BatchOptimizer` at optimizer
  score + the decided score-ranking gate (connectivity/DRC vetoes only).
- [ ] Remove optimizer “close to 1000” stop; keep improvement-threshold stop
  (recalibrate default later).
- [ ] Explicit score fields: keep API `normalized_score` = router score; add
  `optimizer_score`. Deprecate `getNormalizedScore()` as a router-score alias.
- [ ] V1 path must reproduce current fixture scores within a documented tolerance.

### Phase 3: Lower bounds (current tree only)

- [ ] Wire, via, and bend lower bounds (§3.1); cache on load; write to JSON.
- [ ] Zero-length / empty-net / mixed-layer definitions.
- [ ] No conduction-area conditional (pours count as layer terminals).
- [ ] 45° corners included in \(B_{\text{actual}}\).

### Phase 4: V2 formulas on current tree

- [ ] Implement §2 / §3 V2 using settings weights (placeholders OK).
- [ ] Default V2 on; V1 via setting/CLI.
- [ ] Offline replay of current V2 onto stored current and v1.9 JSON (harness /
  later tool, not the v1.9 binary).

### Phase 5: Calibration design (no script yet)

- [ ] Report shape, held-out set, noise floor, saturation checks.
- [ ] Fit \(\alpha\) on clean fully-routed boards so via-excess\(/D\) is comparable
  across density; then optionally lock `DEFAULT_*` after manual test-run edits.
- [ ] Refit \(\widehat{t}_{\text{cal}}(C,A)\) with persisted \(S\); keep that
  separate from \(\alpha\).

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
- [ ] Gate: more incompletes or higher DRC count reject; ranking is by
  optimizer score (more-complete uglier boards do not auto-win); equal scores
  keep the incumbent. Timeout rip-up that adds incompletes is rejected.
- [ ] Optimizer stops on improvement threshold, not proximity to 1000.
- [ ] V1 legacy reproduces historical current-tree scores within tolerance.
- [ ] Raw-metric schema parity current vs v1.9 including pre/post optimizer snapshots;
  v1.9 score algorithm unchanged.
- [ ] Later V2 replay from JSON does not require a reroute (all §4.2 fields present).
- [ ] Full DRC uses `getAllClearanceViolations()`.
- [ ] No completion / DRC-count regression vs the previous current default.

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
post-V2 recalibration; \(C\), \(D\), and ETA share kernel \(P L\) (first cut);
\(S\) stays out of \(D\).

### Still to decide

1. **\(\alpha\).** Keep 0 until length/via/`N_{\text{conn}}\) exist; then fit so
   via-excess\(/D\) is flat across density. Not a CPU fit.
2. **Whether \(C\) switches from \(P L\) to \(N_{\text{conn}}\)** once
   `maximumCount` is in JSON.
3. **\(S_{\text{cal}}\).** Persist `cpu_score` on the next calibration run. Working
   stand-in ~31 000 (typical 28 000–34 000). Always use measured \(S\) at runtime.
4. **V1 reproduction tolerance** (numeric) for Phase 2.
5. **When (if ever) to show ETA in GUI/API** — after \(S\) is persisted and the
   coefficients are refit; not in scoring phases.

Placeholder \(W_*\) / \(U_{\text{scale}}\) / \(L_{\text{floor}}\) are not blocking.
Phase 0 can start.

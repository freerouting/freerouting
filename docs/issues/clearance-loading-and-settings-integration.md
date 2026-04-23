# Clearance Loading, Settings Integration, and RouterSettings Structural Analysis

**Status:** Investigation / Pre-implementation  
**Priority:** High (correctness; potential DRC bypass)  
**Affected packages:** `rules`, `settings`, `io.specctra.parser`, `autoroute`

---

## 1. Summary of Findings

### 1.1 How clearance values flow today

The current clearance pipeline is **entirely separate from `RouterSettings` / `SettingsMerger`**:

```
DSN file (Specctra)
  └─► io.specctra.parser.Network   (full board parse)
        └─► ClearanceMatrix.set_value(...)   ← direct mutation
              └─► BoardRules.clearance_matrix   ← lives inside RoutingBoard
                    └─► AutorouteControl.init_net(...)   ← reads at route-time
                          └─► ShapeSearchTree / ShoveTraceAlgo / Item   ← enforcement
```

The `SettingsMerger` pipeline (priority 0 → 70) is only used for *behavioural* router
parameters (`maxPasses`, `vias_allowed`, trace costs, etc.). It has **no awareness of
`ClearanceMatrix` whatsoever.**

### 1.2 Where clearances are loaded

| Location | What it does |
|---|---|
| `io.specctra.parser.Structure` | Reads `(structure rule (clearance …))` — board-level defaults; calls `ClearanceMatrix.set_default_value()` |
| `io.specctra.parser.Network` | Reads `(network class … (rule (clearance …)))` and `(class_class …)` — per-net-class and cross-class overrides; calls `ClearanceMatrix.set_value()` |
| `io.specctra.parser.Rule` | Exports clearance values back to `.ses` / `.rules` files |
| `gui.WindowClearanceMatrix` | Allows GUI editing of the live matrix; calls `set_value()` directly |
| `RulesFileSettings` | **Stub only** — `loadSettings()` returns an empty `RouterSettings` with a TODO comment. Clearances in `.rules` files are **silently ignored** at the settings-merge level. |

### 1.3 How clearances are enforced at routing time

1. **`AutorouteControl.init_net()`** reads `trace_clearance_class_no` and
   `via_clearance_class` from `NetClass` and sets
   `compensated_trace_half_width[i] = trace_half_width[i] + clearance_compensation_value(...)`.
   This is the primary expansion budget used by `MazeSearchAlgo`.

2. **`ShapeSearchTree.get_clearance()`** calls `cl_matrix.get_value(..., true)`, adding the
   global `clearance_safety_margin = 16` (board units) on top of the stored value.

3. **`Item.clearance_violations()`** calls `cl_matrix.get_value(..., false)` (no margin) for
   actual DRC reporting.

4. **`BasicBoard.get_clearance()`** delegates to `cl_matrix.get_value(..., true)` — used by
   shove/insert logic.

---

## 2. Identified Bugs and Logic Problems

### BUG-1 — `RulesFileSettings` is a non-functional stub
**File:** `settings/sources/RulesFileSettings.java`  
**Severity:** Medium  
**Detail:** `loadSettings()` always returns `new RouterSettings()` (all null fields) with a
`// TODO: Integrate with existing RulesFile.read logic` comment. Any clearance overrides
in a user-supplied `.rules` file are **not applied** through the settings pipeline. They are,
however, applied during a full board load via `DsnFileReader` → `RulesFile.read()` which
mutates `ClearanceMatrix` directly — but *only* during that specific code path. An API caller
that supplies a `.rules` file via `RulesFileSettings` would get no clearance effect.

### BUG-2 — `clearance_safety_margin` inconsistency between routing and DRC
**File:** `rules/ClearanceMatrix.java`, `board/Item.java`  
**Severity:** Low-Medium  
**Detail:** The router uses `get_value(..., true)` (+16 board-units margin) when making
routing decisions, but `Item.clearance_violations()` uses `get_value(..., false)` (no margin)
when reporting violations. This deliberate asymmetry means the router routes slightly more
conservatively than strictly required. However, the literal value `16` is a magic constant
with no documentation explaining its geometric meaning (it appears to be sub-mil — 16/10000 mm
= 0.0016 mm). The inconsistency can confuse developers and may hide real violations when the
safety margin is subtracted during analysis.

### BUG-3 — `ClearanceMatrix.set_value()` silently rounds odd values upward
**File:** `rules/ClearanceMatrix.java` lines 119–127  
**Severity:** Low  
**Detail:** The code rounds odd clearance values up to the next even integer. The reason is
stated as a `// NOTE: why does it need to be even?` comment — meaning the **original
rationale is lost**. If the DSN file specifies a clearance of, say, 101 µm (converted to
board units), it is silently inflated to 102 µm, potentially causing unnecessary routing
failures on tightly-constrained designs. This should be documented or the rounding removed.

### BUG-4 — `clearance_compensation_value()` integer division truncates half-values
**File:** `rules/ClearanceMatrix.java` line 238  
**Code:** `return (this.get_value(p_clearance_class_no, p_clearance_class_no, p_layer, false) + 1) / 2;`  
**Severity:** Low  
**Detail:** Because `set_value()` already forces even values, the `+1` inside the
compensation formula is always a no-op (even + 1 never rounds up an even number in integer
division). The formula is equivalent to `clearance / 2` for even values. While harmless, it
is misleading.

### BUG-5 — Out-of-bounds `get_value()` silently returns 0
**File:** `rules/ClearanceMatrix.java` lines 139–148  
**Severity:** Medium  
**Detail:** When clearance class indices or layer index are out of range, `get_value()` logs a
trace-level message and returns **0** — meaning *no clearance*. Callers in routing-critical
paths (`ShapeSearchTree`, `BasicBoard`) would then route with zero clearance enforcement
without any visible warning to the user. Should return the **default clearance** (class 1) or
at minimum log at `WARN` level, not just `TRACE`.

### BUG-6 — `max_value_on_layer` cache is never invalidated
**File:** `rules/ClearanceMatrix.java` fields `max_value_on_layer` / `Row.max_value`  
**Severity:** Low  
**Detail:** `max_value_on_layer[layer]` is updated only on `set_value()` calls with `Math.max`.
If a clearance value is *decreased* (possible via GUI `WindowClearanceMatrix` or future
programmatic API), the cached maximum is stale. `ShapeSearchTree` and `SearchTreeManager`
rely on `max_value()` for bounding-box inflation when building the search tree. A stale
high value causes unnecessarily large search regions (performance hit), not a correctness
violation.

---

## 3. Should Clearance Values Be in `RouterSettings` / `SettingsMerger`?

This is the core architectural question. Below is a structured analysis.

### 3.1 Arguments FOR integrating clearances into `RouterSettings`

| Argument | Reasoning |
|---|---|
| **Unified override chain** | Today, a user can override `maxPasses` via CLI or API but cannot override the default clearance without touching the board file. Integrating clearance into the settings pipeline would allow `--router.default_clearance=0.2mm` CLI overrides. |
| **Testability** | Unit tests for routing scenarios currently must supply a full DSN file to exercise clearance logic. A `RouterSettings` clearance field would allow injecting clearance in pure-Java test fixtures without file I/O. |
| **Headless/API use case** | REST API callers (priority 70 `ApiSettings`) currently cannot override clearances at all. Exposing a global minimum clearance multiplier would be useful for design-space exploration. |
| **Consistency** | All other routing parameters are in `RouterSettings`. Having clearance live elsewhere creates two mental models. |

### 3.2 Arguments AGAINST integrating clearances into `RouterSettings`

| Argument | Reasoning |
|---|---|
| **Fundamentally different nature** | `RouterSettings` holds *scalar behavioural parameters* (counts, booleans, costs). The `ClearanceMatrix` is a **typed, multi-dimensional data structure** (NxN × layers). It is not a setting; it is a piece of the board's design rules. Forcing it into `RouterSettings` would either bloat that class with a complex object or require a lossy scalar approximation (e.g. `defaultClearance`). |
| **Board-coupled identity** | Clearance classes are named and indexed by net class. They have no meaning outside the context of a specific board. `RouterSettings` is designed to be board-agnostic and serialisable independently (e.g. as JSON). A `ClearanceMatrix` inside `RouterSettings` would break this. |
| **Regression risk** | The `ClearanceMatrix` is mutated in-place during board load by multiple parser methods (`Structure`, `Network`, `RulesFile`). Moving it into `RouterSettings` and through `SettingsMerger.applyNewValuesFrom()` / `ReflectionUtil.copyFields()` would require deep-copy semantics and could easily introduce order-of-application bugs that silently change routing decisions. |
| **`ReflectionUtil.copyFields()` incompatibility** | The `copyFields` mechanism skips fields whose value equals the Java default. For an array/object field (`ClearanceMatrix`), it would need null-check semantics. But `ClearanceMatrix` is never null after board load, so every source would "win" the merge regardless of intent. This violates the core invariant of the settings system. |
| **Serialisation complexity** | `RouterSettings` is serialised to/from JSON (Gson) and to `.frb` binary. `ClearanceMatrix` is already `Serializable` for `.frb`. Making it part of `RouterSettings` JSON would require a custom Gson adapter for the NxN layer-indexed matrix, adding significant complexity. |

### 3.3 Verdict

**Do not move `ClearanceMatrix` into `RouterSettings`.**

The two systems serve different concerns:
- `RouterSettings` / `SettingsMerger` = *how to run the router* (strategy, limits, costs)
- `BoardRules.clearance_matrix` = *what the board design requires* (physical constraints from the netlist/DSN)

Conflating them would violate the separation of concerns and introduce high regression risk.

---

## 4. Full Taxonomy: All Values That Determine Routing

This section expands the analysis to cover every value that affects routing decisions,
categorised by their fundamental nature. The key distinction is:

- **Board-agnostic (BA):** Has meaning without a board. Belongs in `RouterSettings` /
  `SettingsMerger`. Can be set by CLI, API, JSON config, or GUI.
- **Board-specific (BS):** Has meaning only in the context of a specific PCB design. Belongs
  in `BoardRules` / the board object. Should be set exclusively by the input files (DSN, SES,
  RULES). CLI or API can only apply a *floor/multiplier* on top of them, never fully replace
  them.
- **Hybrid (HY):** Starts as board-agnostic (has a sensible default) but can be specialised
  per-board by the design file. Examples: layer preferred-direction flags, trace cost arrays.

### 4.1 Currently in `RouterSettings` — Classified

#### Group A — Pure Router Behaviour (all BA ✅ — correctly placed)

| Field | Type | BA/BS | Notes |
|---|---|---|---|
| `enabled` | `Boolean` | BA | Whether to run the router at all |
| `algorithm` | `String` | BA | Algorithm selector (`freerouting-router`, `freerouting-router-v19`) |
| `jobTimeoutString` | `String` | BA | Wall-clock time limit for the job |
| `maxPasses` | `Integer` | BA | Maximum routing passes |
| `maxItems` | `Integer` | BA (transient) | Testing/debug cap on items processed |
| `maxThreads` | `Integer` | BA | Parallelism degree |
| `vias_allowed` | `Boolean` | BA | Whether layer changes are permitted |
| `automatic_neckdown` | `Boolean` | BA | Auto-reduce width at narrow pins |
| `trace_pull_tight_accuracy` | `Integer` | BA | Pull-tight algorithm precision |
| `save_intermediate_stages` | `Boolean` | BA (transient) | Debug snapshot saving |

#### Group B — Layer Configuration Arrays (HY ⚠️ — partially misplaced)

| Field | Type | BA/BS | Issue |
|---|---|---|---|
| `isLayerActive[]` | `boolean[]` (transient) | HY | Default is BA (all layers active); per-net overrides come from `NetClass.active_routing_layer_arr` in `BoardRules`. Currently duplicated — the router uses both. |
| `isPreferredDirectionHorizontalOnLayer[]` | `boolean[]` (transient) | HY | Default is BA (alternating H/V); the DSN `snap_angle` sets the overall mode. Computed from board aspect ratio in `applyBoardSpecificOptimizations()`. |
| `ignoreNetClasses` | `String[]` (transient) | BA | CLI/API-driven filter. Applied by mutating `NetClass.is_ignored_by_autorouter` in `GuiManager` — a **side-effect outside the board rules system**. Should be applied consistently in `HeadlessBoardManager` as well. |

#### Group C — Scoring / Cost Weights (BA ✅ with HY initialisation)

| Field | Type | BA/BS | Notes |
|---|---|---|---|
| `scoring.via_costs` | `Integer` | BA | Dimensionless cost factor for vias |
| `scoring.plane_via_costs` | `Integer` | BA | Cheaper cost for plane-penetrating vias |
| `scoring.start_ripup_costs` | `Integer` | BA | Initial rip-up penalty |
| `scoring.unroutedNetPenalty` | `Float` | BA | Penalty for each unrouted net in scoring |
| `scoring.clearanceViolationPenalty` | `Float` | BA | Penalty per violation in scoring |
| `scoring.bendPenalty` | `Float` | BA | Penalty per trace bend in scoring |
| `scoring.defaultPreferredDirectionTraceCost` | `Double` | BA | Scalar base; arrays computed from this |
| `scoring.defaultUndesiredDirectionTraceCost` | `Double` | BA | Scalar base; arrays computed from this |
| `scoring.preferredDirectionTraceCost[]` | `double[]` (transient) | HY | Per-layer: BA default + BS board-aspect-ratio adjustments |
| `scoring.undesiredDirectionTraceCost[]` | `double[]` (transient) | HY | Same hybrid nature as above |

#### Group D — Optimizer Settings (BA ✅ — correctly placed)

| Field | Type | BA/BS | Notes |
|---|---|---|---|
| `optimizer.enabled` | `Boolean` | BA | |
| `optimizer.algorithm` | `String` | BA | |
| `optimizer.maxPasses` | `Integer` | BA | |
| `optimizer.maxThreads` | `Integer` | BA | |
| `optimizer.optimizationImprovementThreshold` | `Float` | BA | |
| `optimizer.boardUpdateStrategy` | enum (transient) | BA | |
| `optimizer.hybridRatio` | `String` (transient) | BA | |
| `optimizer.itemSelectionStrategy` | enum (transient) | BA | |

### 4.2 Currently in `BoardRules` — Classified

These values live outside `RouterSettings` today. They are all **correctly placed in
`BoardRules`**, but their relationship to the settings pipeline needs clarification.

| Value | Where stored | BA/BS | Override possibility |
|---|---|---|---|
| `ClearanceMatrix` (NxN per layer) | `BoardRules.clearance_matrix` | BS | API/CLI can apply a global floor multiplier (Option A in §5) |
| Per-net trace half-widths | `NetClass.trace_half_width_arr[]` | BS | No current override path |
| Via definitions & padstacks | `BoardRules.via_infos`, `via_rules` | BS | No current override path |
| Net class assignments | `BoardRules.net_classes` | BS | `ignoreNetClasses` is a partial BA override |
| `trace_angle_restriction` (snap angle) | `BoardRules` (transient) | BS → BA grey area | Read from DSN; could be a BA CLI override (e.g. force 90° mode) |
| `ignore_conduction` | `BoardRules` | BS → BA grey area | Sensible BA default (`true`); DSN can set it; could be a CLI override |
| `pin_edge_to_turn_dist` | `BoardRules` | BS | Computed from clearance rules; no CLI override needed |
| `use_slow_autoroute_algorithm` | `BoardRules` | BA misplaced | This is a *router strategy* flag, not a board design property. Should move to `RouterSettings`. |
| `min_trace_half_width` / `max_trace_half_width` | `BoardRules` (cached) | BS | Derived; not independently settable |

### 4.3 Values Not Surfaced to Settings At All

These routing-relevant values are computed or hard-coded and never exposed to the settings
pipeline. This is mostly intentional but worth documenting.

| Value | Location | BA/BS | Notes |
|---|---|---|---|
| `clearance_safety_margin = 16` | `ClearanceMatrix` constant | BA | Hard-coded routing guard margin. Could be a BA setting. |
| `max_shove_trace_recursion_depth = 20` | `AutorouteControl` | BA | Hard-coded; limits shove depth. |
| `max_shove_via_recursion_depth = 5` | `AutorouteControl` | BA | Hard-coded; limits via shove depth. |
| `max_spring_over_recursion_depth = 5` | `AutorouteControl` | BA | Hard-coded. |
| `tidy_region_width = Integer.MAX_VALUE` | `AutorouteControl` | BA | Set to unbounded; pull-tight scans whole board. |
| `BOARD_RANK_LIMIT = 50` | `BatchAutorouter` | BA | Heuristic for multi-board history selection. |
| `STAGNATION_PASS_LIMIT = 10` | `BatchAutorouter` | BA | Early-exit after N passes of no improvement. |
| `STAGNATION_SCORE_THRESHOLD = 0.5f` | `BatchAutorouter` | BA | Minimum improvement to count as progress. |
| `STOP_AT_PASS_MINIMUM = 8` | `BatchAutorouter` | BA | Always run at least this many passes. |

---

## 5. Critique of the Current `RouterSettings` Class

### 5.1 Structural Problems

#### Problem S1 — Mixed transient/non-transient without clear contract
Several fields are `transient` (skipped by Java serialisation and Gson) but carry
non-trivial state:

```java
public transient boolean[] isLayerActive;
public transient boolean[] isPreferredDirectionHorizontalOnLayer;
public transient Boolean save_intermediate_stages = false;   // ← has default value!
public transient String[] ignoreNetClasses;
```

The field `save_intermediate_stages` has a **non-null default initializer**, violating the
core invariant of the `SettingsMerger` system (all fields must be null by default so the
merger can detect "intent to set"). Although it is transient (not copied by `ReflectionUtil`),
the pattern is dangerous and inconsistent.

The layer arrays (`isLayerActive`, `isPreferredDirectionHorizontalOnLayer`) and per-layer
cost arrays in `RouterScoringSettings` are declared `transient` to avoid JSON serialisation,
but are populated by `applyBoardSpecificOptimizations()` which mixes **board geometry
calculations into a settings class**. This is an architectural boundary violation.

#### Problem S2 — Board geometry calculations inside a settings class
`applyBoardSpecificOptimizations(RoutingBoard p_board)` computes layer preferred-direction
flags and outer-layer cost penalties using the board's bounding box and layer structure.
This logic belongs in the routing engine, not in a serialisable settings object. The settings
class should only *hold* values; the calculation of those values should happen elsewhere
(e.g. in a factory/initialiser within `BatchAutorouter` or a new `BoardSpecificSettings`
class).

#### Problem S3 — `use_slow_autoroute_algorithm` is in the wrong class
`BoardRules.use_slow_autoroute_algorithm` is a pure algorithm-strategy toggle (BA), not a
design rule. It was stored in `BoardRules` for historical reasons. It should move to
`RouterSettings` so it can be toggled via CLI/API.

#### Problem S4 — `ignoreNetClasses` applied inconsistently
`RouterSettings.ignoreNetClasses` is a BA setting (set by CLI / API), but it is applied in
`GuiManager` by mutating `NetClass.is_ignored_by_autorouter` — a BS property inside
`BoardRules`. This side-effect:
- Only happens in the GUI code path, not in `HeadlessBoardManager`.
- Permanently mutates the board rules object (not reverted between jobs).
- Creates a mismatch: the setting is BA but its application is GUI-only.

#### Problem S5 — Hard-coded recursion/stagnation limits not configurable
`AutorouteControl` has several hard-coded depth limits (see §4.3) that advanced users or
CI/testing might want to tune. They are neither in `RouterSettings` nor documented. Moving
at least `max_shove_trace_recursion_depth` and the stagnation parameters to `RouterSettings`
would enable performance tuning without recompilation.

#### Problem S6 — `RouterSettings.validate()` uses direct field access instead of setters
`validate()` directly assigns `this.maxPasses = 9999;` etc., bypassing the property-change
event mechanism that keeps the GUI in sync. This is the same anti-pattern the `InteractiveSettings`
invariant specifically warns against.

### 5.2 Naming Inconsistencies

The class mixes Java camelCase with snake_case field names:
- `maxPasses`, `maxThreads`, `vias_allowed`, `automatic_neckdown`, `trace_pull_tight_accuracy`
- `optimizer.maxPasses` vs `scoring.via_costs` vs `scoring.unroutedNetPenalty`

This is legacy drift, but it hurts readability and makes field-lookup by reflection fragile.

---

## 6. The BA vs BS Override Question

> *Is it a correct assumption that board-agnostic values should be merged by `SettingsMerger`
> from different sources, and board-specific values should be set only by the input file(s)
> and not by CLI or API calls?*

**Mostly yes, but the boundary is softer than it seems for some values.**

### 6.1 Clear BA — full override chain appropriate

These should flow through `SettingsMerger` at all priority levels:

- `maxPasses`, `maxThreads`, `jobTimeoutString`, `enabled`, `algorithm`
- `vias_allowed`, `automatic_neckdown`, `trace_pull_tight_accuracy`
- All scoring weights: `via_costs`, `bendPenalty`, `unroutedNetPenalty`, etc.
- Optimizer settings

### 6.2 Clear BS — input file only, no override needed

These should never appear in `RouterSettings` at all:

- `ClearanceMatrix` — full NxN structure
- Per-net trace widths (`NetClass.trace_half_width_arr`)
- Via padstack geometry (`via_infos`, `via_rules`)
- `pin_edge_to_turn_dist` (derived from clearance rules)

### 6.3 Grey area — BA defaults with BS specialisation

These have a sensible BA default but are legitimately overridden by the design file. The
correct model is: **BA default applied first (low priority), then BS override from DSN/RULES
(higher priority)**. This maps naturally onto `SettingsMerger` priority slots 0 (default) →
20/40 (design file).

| Value | BA default | BS override source | Recommended treatment |
|---|---|---|---|
| `trace_angle_restriction` | `FORTYFIVE_DEGREE` | DSN `snap_angle` | Move to `RouterSettings`; DSN parser sets it via `DsnFileSettings` |
| `ignore_conduction` | `true` | DSN `(ignore conduction)` | Move to `RouterSettings`; also useful as CLI override |
| `use_slow_autoroute_algorithm` | `false` | Currently set nowhere; only in `BoardRules` | Move to `RouterSettings` entirely |
| `isLayerActive[]` | all `true` | DSN layer list | Keep as computed/transient; do not try to merge via `SettingsMerger` |
| `ignoreNetClasses` | `[]` | CLI / API only | Apply in `HeadlessBoardManager` (not just `GuiManager`) after board load |

### 6.4 Override floors on BS values — limited but valid

It is legitimate for CLI/API to express a *floor* or *multiplier* on BS values without
owning their full definition:

- `minClearanceOverrideUm` (Option A from §7): sets a minimum floor on all clearance matrix
  entries after DSN parsing — useful for DFM checks.
- A future `traceWidthMultiplier` (not yet proposed): scales all net-class trace widths
  uniformly for impedance-sensitive scenarios.

These are best expressed as post-parse board mutations triggered by BA settings, not as BA
settings that replace BS values.

---

## 7. Recommended Implementation Approaches

### Option A — Scalar clearance override in `RouterSettings` (minimal footprint)

Add a single nullable field to `RouterSettings`:

```java
@SerializedName("min_clearance_override_um")
public Double minClearanceOverrideUm;  // null = use board rules
```

During board load (`HeadlessBoardManager`), after `SettingsMerger.merge()`, if
`minClearanceOverrideUm != null`, walk the `ClearanceMatrix` and `Math.max()` every stored
value with the converted board-unit equivalent. This allows API/CLI override of a global
minimum without touching the multi-dimensional structure.

**Pros:** Minimal change; fits the existing `RouterSettings` nullable-field contract.  
**Cons:** Only a global floor, not per-class or per-layer; still mutates `ClearanceMatrix`
post-parse rather than expressing it declaratively.

### Option B — Dedicated `BoardConstraintsSource` interface (clean separation)

Introduce a new settings-adjacent concept:

```java
// New interface — NOT a SettingsSource, not part of RouterSettings
public interface BoardConstraintsSource {
    /** Called after board load; may mutate p_rules. */
    void applyConstraints(BoardRules p_rules, CoordinateTransform p_transform);
    int getPriority();
}
```

Implementations:
- `DsnConstraintsSource` (priority 20) — wraps what `Structure`/`Network` parsers already do
- `RulesFileConstraintsSource` (priority 40) — actually implements `RulesFile` clearance loading (fixing BUG-1)
- `ApiConstraintsSource` (priority 70) — accepts a `Map<String, Double>` of clearance overrides keyed by `"class1:class2"` strings

A new `BoardConstraintsMerger` (analogous to `SettingsMerger`) would iterate sources in
priority order. `HeadlessBoardManager` would drive it after the board is fully parsed.

**Pros:** Clean; explicit; handles both class-level and scalar overrides; fixes BUG-1 properly.  
**Cons:** New abstraction layer; moderate implementation effort.

### Option C — Fix known bugs without architectural change (safest for now)

Address the confirmed bugs incrementally without touching the overall architecture:

1. **Fix BUG-1** (`RulesFileSettings`): Implement `loadSettings()` by calling
   `RulesFile.readClearances()` (extract from existing `RulesFile.read()`) and storing a
   `ClearanceMatrixSnapshot` that is applied during board load.
2. **Fix BUG-5** (silent zero return): Upgrade the out-of-bounds log in `ClearanceMatrix.get_value()`
   from `TRACE` to `WARN` and return class-1 default clearance instead of 0.
3. **Document BUG-2** (safety margin): Add a class-level Javadoc explaining that
   `clearance_safety_margin = 16` is an internal routing guard, not a DRC-visible tolerance.
4. **Fix BUG-6** (stale max cache): Replace `max_value_on_layer` with a method that recomputes
   the maximum lazily (invalidate on any `set_value()` call by resetting to -1).

**Pros:** Low risk; immediately actionable; does not touch routing algorithms.  
**Cons:** Defers the architectural question.

### Option D — Introduce `BoardSpecificSettings` to isolate hybrid fields

Refactor the per-layer transient arrays and board-geometry computations out of `RouterSettings`
into a new class:

```java
/**
 * Board-specific routing parameters computed from a RoutingBoard at load time.
 * NOT part of SettingsMerger — constructed once per board load.
 * Immutable after construction.
 */
public final class BoardSpecificSettings {
    public final boolean[] isLayerActive;
    public final boolean[] isPreferredDirectionHorizontalOnLayer;
    public final double[] preferredDirectionTraceCost;     // board-adjusted
    public final double[] undesiredDirectionTraceCost;     // board-adjusted
    public final AngleRestriction traceAngleRestriction;   // from snap_angle
    public final boolean ignoreConduction;                 // from DSN
    public final double pinEdgeToTurnDist;                 // from clearance rules

    public static BoardSpecificSettings compute(RoutingBoard board, RouterSettings settings) { ... }
}
```

`RouterSettings` retains only the BA scalar defaults (`defaultPreferredDirectionTraceCost`,
`defaultUndesiredDirectionTraceCost`). The transient per-layer arrays move here.
`AutorouteControl` and `BatchAutorouter` receive both a `RouterSettings` and a
`BoardSpecificSettings`.

**Pros:** Clean separation; `RouterSettings` becomes a pure BA settings bag; eliminates
`applyBoardSpecificOptimizations()` from the settings class.  
**Cons:** API surface change; all callers of layer-array methods in `RouterSettings` must be
updated (~10 call sites).

---

## 8. Recommended Structural Refactoring of `RouterSettings`

Regardless of which option is chosen for clearances, the following structural improvements
to `RouterSettings` itself are recommended with low-to-medium risk:

### RS-1 — Move `use_slow_autoroute_algorithm` from `BoardRules` to `RouterSettings`
```java
@SerializedName("use_slow_algorithm")
public Boolean useSlowAutorouteAlgorithm;  // default: false
```
Update `BoardRules` to read from the merged `RouterSettings` at routing start.

### RS-2 — Move `trace_angle_restriction` and `ignore_conduction` to `RouterSettings`
Both are BA defaults that happen to be overridden by DSN. They should follow the BA/BS merge
flow: default in `DefaultSettings`, overridden by `DsnFileSettings`.

### RS-3 — Fix `ignoreNetClasses` application to work headlessly
Move the `ignoreNetClasses → NetClass.is_ignored_by_autorouter` mutation from `GuiManager`
to `HeadlessBoardManager.prepareJob()` so it works in API/headless mode.

### RS-4 — Remove the default initializer from `save_intermediate_stages`
Change `public transient Boolean save_intermediate_stages = false;` to
`public transient Boolean save_intermediate_stages;` for invariant consistency, and add it
to `DefaultSettings.getSettings()`.

### RS-5 — Fix `validate()` to use setters
Replace direct field assignment in `validate()` with setter calls (`setMaxPasses(...)` etc.)
to fire `PropertyChangeEvent`s correctly.

### RS-6 — Standardise naming to camelCase throughout
Rename `vias_allowed`, `trace_pull_tight_accuracy`, `via_costs`, `plane_via_costs`,
`start_ripup_costs` etc. using `@SerializedName` to preserve JSON compatibility while
achieving code-level consistency.

---

## 9. Acceptance Criteria for any Implementation

Before merging any changes touching `ClearanceMatrix` or clearance loading:

- [ ] `./gradlew test` passes with zero regressions.
- [ ] `Dac2020Bm01RoutingTest` (quickest smoke test) completes with ≤ baseline clearance violations.
- [ ] `scripts/tests/compare-versions.ps1` shows no degradation in routing completion rate vs v1.9 baseline.
- [ ] `ClearanceMatrix.get_value()` with out-of-bounds indices logs at WARN (not TRACE) and returns a safe value.
- [ ] No new `ClearanceMatrix` fields in `RouterSettings` unless they are nullable reference types with no default initializer (per the merger invariant).
- [ ] `RulesFileSettings.loadSettings()` either properly loads clearances or is annotated with a tracked issue reference.
- [ ] `ignoreNetClasses` is applied in `HeadlessBoardManager`, not only in `GuiManager`.
- [ ] All fields in `RouterSettings` and sub-classes that have `= <literal>` default initializers are moved to `DefaultSettings.getSettings()`.

---

## 10. Recommended Action Plan

### Immediate (no routing risk)
1. Fix BUG-5 — upgrade silent-zero return in `ClearanceMatrix.get_value()` to WARN + return class-1 default.
2. Add Javadoc to `ClearanceMatrix` explaining the safety margin (BUG-2) and even-rounding rationale (BUG-3).
3. Fix `save_intermediate_stages` default initializer (RS-4).
4. Fix `validate()` to use setters (RS-5).

### Short term (low routing risk)
5. Fix `ignoreNetClasses` application in `HeadlessBoardManager` (RS-3).
6. Move `use_slow_autoroute_algorithm` to `RouterSettings` (RS-1).
7. Move `trace_angle_restriction` and `ignore_conduction` into `RouterSettings` with DSN override path (RS-2).

### Medium term (moderate complexity)
8. Implement `BoardSpecificSettings` to isolate board-geometry-computed transient arrays (Option D).
9. Implement `RulesFileConstraintsSource` to fix BUG-1 properly (Option B, scoped to rules file only).

### Deferred
10. Option A (`minClearanceOverrideUm`) — only if a concrete API use case requires per-job clearance floor overrides.
11. Full `BoardConstraintsMerger` (Option B) — full implementation after Option D is stable.
12. Naming standardisation (RS-6) — cosmetic; do last to minimise diff noise.

### Do NOT
- Move `ClearanceMatrix` itself into `RouterSettings`.
- Add any `RouterSettings` field with a non-null default initializer outside of `DefaultSettings.getSettings()`.

# Long-Term Naming and Package Refactoring Plan

Status: plan for a separate branch. The current branch should remain
behaviorally unchanged apart from resolving the benchmark gate below.

The `.frb` format is rarely used and is primarily an internal testing format.
Backward compatibility for `.frb` files is therefore not a requirement for
these renames. Direct class and package moves are acceptable; in-repository
tests and fixtures must simply be updated as part of each migration.

## Benchmark gate

The benchmark commit `88e5a5e8` (`Update nightly benchmark results`) reports:

- 20 fixtures for both the current branch and v2.3.0.
- Two reported failures for both versions.
- An aggregate average score of `915.1` for both versions.
- Matching routing results on most fixtures.

This is not sufficient to declare the branch regression-free. On
`DAC2020_bm01.dsn`, the latest current-branch run reports 4 unrouted nets and
20 DRC violations, while v2.3.0 reports 4 unrouted nets and 2 DRC violations.
The same current run's internal quality record reports zero violations, so the
result is also a measurement inconsistency. The comprehensive DRC result is
the authoritative signal for this project. The benchmark should therefore be
investigated before merging or beginning behavior-sensitive renames.

The `CM5_MINIMA_3.dsn` result is a timeout for both versions and is not a
comparable improvement: the current result has incomplete `N/A` quality
fields. This is a baseline limitation rather than evidence of a current-branch
improvement.

## Recommendation scale

- **Critical** — affects a central domain concept or a public/API contract;
  schedule as a dedicated migration.
- **High** — materially improves architectural clarity; suitable for a
  dedicated refactoring series.
- **Medium** — useful clarity improvement, but not a prerequisite for
  maintainability.
- **Low** — cleanup or compatibility-shell removal after a migration window.
- **Keep** — the current name is already a good long-term fit.

## Class and type proposals

| Current type | Recommended long-term name | Reason | Importance | Main risk |
|---|---|---|---|---|
| `app.freerouting.board.BasicBoard` | `BoardModel` | This is the mutable board container for items, rules, libraries, spatial indexes, serialization, and board mutations. “Basic” understates its role. | Critical | High: central inheritance and in-repo serialization |
| `app.freerouting.settings.RouterSettings` | `RoutingPipelineSettings` | The type configures fanout, autorouting, optimization, scoring, limits, DRC behavior, layers, and resources; “Router” is narrower than its actual scope. | Critical | Critical: reflection, JSON, and API contracts |
| `app.freerouting.drc.DesignRulesChecker` | Split into `ClearanceViolationChecker`, `ConnectivityAnalyzer`, and `KicadDrcReportBuilder` | The current class checks clearances, connectivity, length constraints, dangling items, and external report generation. A single replacement name would hide the mixed responsibilities. | Critical | High: broad call-site and test migration |
| `app.freerouting.autoroute.BatchAutorouter` | `AutoroutePipelineCoordinator` | The class coordinates fanout, autoroute passes, stagnation detection, board restoration, scoring, telemetry, and cleanup. “Batch” is an implementation-era term. | Critical | High: central routing orchestration |
| `app.freerouting.gui.session.AutorouterAndRouteOptimizerThread` | `GuiRoutingPipelineTask` | It runs the GUI-facing routing pipeline, publishes progress, collects metrics, optimizes routes, and writes output. The current name is long and exposes an implementation detail. | High | High: thread lifecycle and session ports |
| `app.freerouting.management.RoutingJobSchedulerActionThread` | `RoutingJobExecutionTask` | “ActionThread” is a legacy concurrency detail; the meaningful responsibility is executing and monitoring one routing job. | High | Medium-high: scheduler integrations |
| `app.freerouting.management.HeadlessBoardManager` | `HeadlessBoardLifecycleService` | The class loads and creates boards, applies settings, validates state, computes checksums, and writes output. “Manager” does not describe the lifecycle boundary. | High | High: API and GUI inheritance |
| `app.freerouting.gui.session.GuiBoardManager` | `GuiBoardSession` | The class owns the active GUI board session, live settings, editor-state binding, board replacement, and GUI-specific lifecycle. | High | High: session and serialization references |
| `app.freerouting.gui.GuiManager` | `GuiApplicationBootstrap` | Its primary responsibility is desktop startup and application-level GUI wiring, not generic resource management. | High | Medium: startup and integration references |
| `app.freerouting.gui.session.InteractiveSettings` | `GuiSessionSettings` | It is the live GUI-session settings source, including active layer, selection, grids, route mode, and interactive options. “Interactive” alone is easy to confuse with routing mode. | High | High: singleton and settings-merger integration |
| `app.freerouting.settings.GuiSettings` | `GuiApplicationSettings` | This type is application GUI configuration, not router settings. The new name distinguishes it from the source type below. | High | Medium: settings and API references |
| `app.freerouting.settings.sources.GuiSettings` | `GuiRouterSettingsSource` | This type is a `SettingsMerger` source for router settings, not general GUI application configuration. | High | Medium: source registration and imports |
| `app.freerouting.gui.interactive.Settings` | Remove, or temporarily use `LegacyInteractiveSettings` | It duplicates the newer live settings concept and has no meaningful production role. Removal is preferable after confirming compatibility consumers. | Low | Low: verify external consumers |
| `app.freerouting.gui.interactive.InteractiveState` | `EditorInteractionState` | This is the base class for editor state-machine states handling pointer/key events, transitions, and commands. | Medium | High: many concrete states inherit it |
| `app.freerouting.gui.interactive.InteractiveStateController` | `EditorStateMachine` | It stores the current state, dispatches events, performs transitions, and bootstraps editor commands. | Medium | Medium: state registration and tests |
| `app.freerouting.autoroute.BatchOptimizer` | `RouteOptimizationStage` | It optimizes one routing job's board; it is not a general batch processor. | High | Medium-high: pipeline wiring |
| `app.freerouting.autoroute.BatchFanout` | `SmdFanoutStage` | It performs SMD-pin escape/fanout work and reports fanout statistics. | Medium | Medium: stage and diagnostic references |
| `app.freerouting.autoroute.NamedAlgorithm` | `RoutingStage` or `RoutingStageDefinition` | The type carries more than a name: it participates in stage context, board/settings access, and event handling. Confirm the exact abstraction before choosing between the two names. | Medium | Medium: inheritance contract |
| `app.freerouting.board.Item` | `BoardItem` | The class is the common board-model base for pins, vias, traces, areas, obstacles, and outlines. “Item” is too generic outside a narrow local context. | High | High: central model inheritance |
| `app.freerouting.board.ShapeSearchTree` | `BoardSpatialIndex` | It is a clearance-aware spatial index over board geometry, not a reusable generic tree abstraction. | High | High: routing performance and many call sites |
| `app.freerouting.board.SearchTreeManager` | `BoardSpatialIndexManager` | It manages default and compensated spatial indexes, reinsertion, and invalidation. | Medium-high | Medium-high: board infrastructure |
| `app.freerouting.rules.BoardRules` | `BoardRuleSet` | The class aggregates clearance, net, net-class, via, width, angle, and conduction rules. “RuleSet” better communicates an aggregate value. | Medium-high | Medium-high: serialized board state |
| `app.freerouting.rules.Nets` | `NetRegistry` | It is an indexed lookup/factory registry by number, name, and subnet, rather than merely a plural collection. | Medium | Medium: public domain API |
| `app.freerouting.drc.NetIncompletes` | `NetConnectivityAnalysis` | “Incompletes” is awkward; the type computes and stores ratsnest connections and related net-quality data. | High | Medium: DRC and GUI consumers |
| `app.freerouting.drc.AirLine` | `UnroutedConnection` | “AirLine” is historical terminology for a straight-line connection between two board items. | Medium-high | Medium: localized resources and GUI |
| `app.freerouting.core.scoring.BoardStatistics` | `BoardRoutingMetrics` | The type combines routing completion, DRC, scoring, fanout, and quality measurements. “Statistics” is too generic. | Medium-high | High: serialized and benchmark-facing |
| `app.freerouting.drc.DrcReport` | `KicadDrcReport` | This is a KiCad JSON report DTO, not the general internal DRC result. | High | Medium-high: JSON/report consumers |
| `app.freerouting.drc.DrcViolation` | `KicadDrcViolation` | Prefixing the external schema type separates it from internal violation concepts. | High | Medium-high: JSON/report contract |
| `app.freerouting.drc.DrcViolationItem` | `KicadDrcViolationItem` | Same schema clarification as `KicadDrcViolation`. | High | Medium-high: JSON/report contract |
| `app.freerouting.drc.DrcPosition` | `KicadDrcPosition` | Makes the external report schema role explicit. | Medium | Medium: JSON/report contract |
| `app.freerouting.io.specctra.parser.DsnFile` | `SpecctraDsnParserSupport` | Public DSN entry points are `DsnReader` and `DsnWriter`; this type is parser support and legacy helpers rather than the main file abstraction. | High | Medium: parser compatibility |
| `app.freerouting.io.specctra.parser.RulesFile` | Remove | It is an empty compatibility shell; `RulesReader` and `RulesWriter` are the real entry points. | Low | Low: in-repository references |
| `app.freerouting.io.specctra.parser.SpecctraSesFileWriter` | Remove | It is a deprecated wrapper around `SesWriter`. | Low | Low: in-repository references |
| `app.freerouting.io.specctra.parser.SesFileReader` | Remove | It is a deprecated wrapper around `SesReader`. | Low | Low: in-repository references |

### Duplicate type names to resolve early

The two `GuiSettings` classes are the most urgent naming collision because
they represent different concepts in adjacent configuration paths. Rename
them directly and update imports and settings-merger registration:

1. `settings.GuiSettings` → `GuiApplicationSettings`
2. `settings.sources.GuiSettings` → `GuiRouterSettingsSource`
3. `gui.session.InteractiveSettings` → `GuiSessionSettings`

Other duplicate concepts deserve a later, scoped pass:

- `rules.NetClass` versus `io.specctra.parser.NetClass`
  (`RoutingNetClass` versus `SpecctraNetClassScope`)
- `board.Unit` versus `io.specctra.parser.Unit`
  (`BoardUnit` versus `SpecctraUnitScope`)
- singular/plural collection pairs such as `Net`/`Nets`,
  `NetClass`/`NetClasses`, and `ViaInfo`/`ViaInfos`

These should not be renamed by broad text replacement. Their names are often
part of format-parser vocabulary or active board-model code.

## Variable and local-name proposals

`currTrace` is a valid first target, but a blanket replacement is not always
the best result. Use the most specific name that describes the role:

| Current pattern | Preferred replacement | When to use it |
|---|---|---|
| `currTrace` | `currentTrace` | A loop or state machine genuinely tracks the current trace |
| `currTrace` in pull-tight replacement logic | `traceToAdjust` | The variable identifies the trace being modified, not merely the current iteration |
| `currTrace` in a pattern match | `trace` | The surrounding scope already makes “current” obvious |
| `currItem` | `currentItem` | The item is the current loop/state value |
| `currLayer` | `currentLayer` | The layer is the current iteration/state value |
| `currNetNo` | `currentNetNumber` | The value is a net number rather than a net object |
| `currTraceInfo` | `traceInfo` or `currentTraceInfo` | Use `traceInfo` for a collection element and `currentTraceInfo` for state |
| `currTraceCount` | `traceCount` | The count is not itself an iterated trace |
| `currDoor` | `currentDoor` | The current expansion-door iteration is important to the algorithm |
| `currShape` | `currentShape` | The shape is stateful or compared across iterations |

Also replace legacy underscore names at normal API boundaries, for example
`get_locale()` → `getLocale()` and
`last_repainted_time` → `lastRepaintedTime`. Preserve deprecated forwarding
methods where external integrations may depend on the existing spelling.

Do not expand standard domain abbreviations such as API, MCP, DSN, SES, DRC,
EDT, or SMD merely for stylistic reasons.

## Package proposals

| Current package | Recommended direction | Reason | Importance | Main risk |
|---|---|---|---|---|
| `app.freerouting.api.v1.McpControllerV1` | Move the class to `app.freerouting.api.mcp.McpControllerV1` | MCP has its own server, authentication, rate limiting, SSE/WebSocket, and tool registry. Keeping its controller under REST `v1` is misleading. HTTP routes do not need to change. | High | Medium: registrations, imports, ArchUnit/docs |
| `app.freerouting.management` | Introduce `app.freerouting.orchestration` with `board`, `jobs`, and `sessions` subpackages | “Management” combines board lifecycle, session lifecycle, scheduling, and service coordination. Role-based packages expose the actual boundaries. | High | Medium-high: public Java API and ArchUnit rules |
| `app.freerouting.management.analytics` | `app.freerouting.analytics` | Analytics is an independent integration/service concern, not board management. | Medium | Medium: public analytics classes |
| `app.freerouting.core` | Split into `jobs`, `sessions`, `scoring`, and `board.library` by responsibility | `core` currently mixes jobs, sessions, scoring, board-library data, counters, and lifecycle support. | High | High: central model and public APIs |
| `app.freerouting.autoroute` | Retain the root; add `autoroute.batch`, `autoroute.search`, and `autoroute.optimization` | The package currently combines orchestration, search state, routing algorithms, optimizer code, and diagnostics. | Medium-high | Medium-high: algorithm imports and ArchUnit |
| `app.freerouting.board` | Introduce subpackages incrementally, beginning with isolated spatial and algorithm code | Board entities, spatial indexes, mutation algorithms, and model state are tightly coupled. | Medium-high | Very high: model coupling and performance |
| `app.freerouting.gui` | Retain existing `session`, `interactive`, `rendering`, and `a11y`; later consider `windows`, `menus`, `panels`, and `components` | The existing boundaries are meaningful and enforced, but the root package is crowded. | Medium-high | High: class-based i18n and GUI serialization |
| `app.freerouting.util.TextManager` | `app.freerouting.i18n.TextManager` | Localization is a domain service, not a generic utility. | Medium-high | Very high: resource-bundle lookup uses class names |
| `app.freerouting.util.gson` | `app.freerouting.serialization.gson` | Gson providers/adapters are serialization infrastructure, not generic utilities. | Medium | Medium: adapter registration and imports |
| `app.freerouting.drc` | Keep for now; consider `drc.clearance`, `drc.connectivity`, and `drc.reporting` after responsibility extraction | The package is broad, but the current architecture intentionally keeps DRC concerns together while accepted debt is tracked. | Low-medium | Medium-high: API and localized resources |
| `app.freerouting.settings` and `app.freerouting.settings.sources` | Keep | These packages already describe a coherent layered configuration subsystem. | Keep | Low |
| `app.freerouting.io`, `io.specctra`, `io.specctra.parser`, and `io.kicad` | Keep | Format boundaries are clear, and parser encapsulation is explicitly protected. | Keep | High if changed: public I/O entry points |
| `app.freerouting.geometry.planar` | Keep | “Planar” accurately describes the 2D geometry foundation and leaves room for future geometry domains. | Keep | Very high: serialized geometry and API consumers |
| `app.freerouting.rules` | Keep | Nets, net classes, clearance matrices, and via rules form a cohesive domain. | Keep | High: serialized board state |
| `app.freerouting.datastructures`, `logger`, and `debug` | Keep | These names are already recognizable and are used by current architectural boundaries. | Keep | Low-medium |

## Recommended actions before merging the current branch

These are the only items I recommend completing on the current branch. The
actual naming and package changes should wait for the separate refactoring
branch.

1. **Resolve the `DAC2020_bm01.dsn` benchmark discrepancy.**
   Reproduce the latest current-branch and v2.3.0 runs with identical settings.
   Compare the complete result from
   `DesignRulesChecker.getAllClearanceViolations()` with the benchmark's
   internal quality field. Determine whether the 20 violations are real or
   caused by the benchmark/DRC measurement path. Do not merge while a real
   increase from 2 to 20 remains unexplained.
2. **Record the CM5 timeout as a baseline limitation.**
   Both versions time out on `CM5_MINIMA_3.dsn`; the current result has
   incomplete fields. Ensure the benchmark report does not present this as a
   current-branch improvement, and either document it as accepted baseline
   debt or rerun it with a comparable bounded configuration.
3. **Run the final repository gates from the merge candidate.**
   On Windows, run:
   `gradlew.bat spotlessCheck checkstyleMain checkstyleTest checkstyleRewriteRecipes`,
   followed by the required test suite (`check` or the project's agreed
   merge-equivalent). Run `testGui` if GUI sources or accessibility tests are
   included in the merge candidate.
4. **Verify the intended diff and generated benchmark assets.**
   Confirm that the benchmark JAR, benchmark data, and website changes in
   `88e5a5e8` are intentional, that no temporary logs or unrelated generated
   files are present, and that `git diff --check` is clean.
5. **Keep the merge candidate behavior-neutral.**
   Do not begin the class/package renames or broad local-variable cleanup
   before merging. The new planning document is documentation only.

The current branch is merge-ready only after item 1 is resolved and item 3
passes. Item 2 may remain accepted baseline debt if it is explicitly recorded
and the result is not interpreted as a regression-free success.

## Separate-branch implementation plan

### Phase A — low-risk identifier cleanup

- Rename `currTrace` to `currentTrace` where it represents the current
  iteration value.
- Use role-specific names such as `traceToAdjust`, `traceInfo`, or `trace`
  where those are more accurate.
- Rename `currItem`, `currLayer`, `currDoor`, and similar locals.
- Convert legacy underscore identifiers such as `get_locale()` and
  `last_repainted_time` at internal call sites.
- Keep standard abbreviations such as API, MCP, DSN, SES, DRC, EDT, and SMD.

### Phase B — duplicate settings and low-risk type cleanup

- Rename `settings.GuiSettings` to `GuiApplicationSettings`.
- Rename `settings.sources.GuiSettings` to `GuiRouterSettingsSource`.
- Rename `gui.session.InteractiveSettings` to `GuiSessionSettings`.
- Remove unused `gui.interactive.Settings` after confirming there are no
  required in-repository consumers.
- Remove `RulesFile`, `SpecctraSesFileWriter`, and `SesFileReader`.
- Rename KiCad report DTOs with an explicit `Kicad` prefix.

Because `.frb` compatibility is not required, these can be direct moves and
renames. Update internal tests and fixtures in the same commit.

### Phase C — DRC responsibility extraction

- Extract clearance checking into `ClearanceViolationChecker`.
- Extract connectivity/incomplete-net analysis into `ConnectivityAnalyzer`.
- Extract KiCad JSON construction into `KicadDrcReportBuilder`.
- Replace `NetIncompletes` with `NetConnectivityAnalysis`.
- Replace `AirLine` with `UnroutedConnection`.
- Preserve the existing DRC behavior and validate with full clearance
  violation checks, not only `BoardStatistics`.

### Phase D — orchestration and GUI role names

- Rename `BatchAutorouter` to `AutoroutePipelineCoordinator`.
- Rename `BatchOptimizer` and `BatchFanout` to explicit routing-stage names.
- Rename the GUI routing thread and scheduler action thread to task/pipeline
  names that do not expose thread implementation details.
- Rename `HeadlessBoardManager`, `GuiBoardManager`, and `GuiManager` to
  lifecycle/session/bootstrap names.
- Update `docs/architecture.md` as orchestration boundaries change.

### Phase E — central model and settings names

- Rename `BasicBoard` to `BoardModel`.
- Rename `Item` to `BoardItem`.
- Rename `ShapeSearchTree` and `SearchTreeManager` to spatial-index names.
- Rename `BoardRules` to `BoardRuleSet` and `Nets` to `NetRegistry`.
- Rename `RouterSettings` to `RoutingPipelineSettings`.
- Treat these as separate, reviewable migrations because they affect a large
  portion of the routing pipeline and public Java-facing code.

### Phase F — package restructuring

1. Move `McpControllerV1` from `api.v1` to `api.mcp`; retain REST controllers
   in `api.v1`.
2. Split `management` into role-based orchestration packages.
3. Move analytics out of `management.analytics`.
4. Split `core` into jobs, sessions, scoring, and board-library packages.
5. Add focused `autoroute` subpackages.
6. Add isolated `board` subpackages for spatial and algorithm code.
7. Move `TextManager` to `i18n` and Gson infrastructure to
   `serialization.gson`.
8. Defer broad GUI, geometry, and format-package moves unless a concrete
   responsibility boundary justifies them.

### Validation gates for each phase

- Compile and run the focused unit tests.
- Run relevant architecture tests and update ArchUnit rules.
- Run `spotlessCheck`, Checkstyle, and `git diff --check`.
- Run `python scripts/i18n/extract-context.py --check` when Java or
  translation sources change.
- Run relevant routing fixtures and compare unrouted nets, full DRC
  violations, and completion against the stable v2.3.0 baseline.
- Do not modify `src_v19/`; it is a frozen compatibility reference.
- Preserve class-based localization resources when moving GUI, DRC, or utility
  classes.
- Update architecture documentation, API registrations, and public
  Java-facing documentation with each move.

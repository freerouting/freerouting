# Naming and Package Refactoring Plan

Status: **complete and ready to implement.** No further product decisions are
required.

Epic branch: `refactor/naming-and-packages`. Each phase is its own branch,
PR’d **into the epic**, not into `master`. Merge the epic to `master` only
when every phase is done.

This is a naming and packaging campaign. Do not change routing, DRC, or
scoring *behavior* except the locked Q1 seed/precedence fix. Do not modify
`src_v19/`. Do not run `spotlessApply`. Do not auto-stage files. Preserve LF.

IntelliJ (or equivalent) performs Java type/method/package renames. An LLM
updates docs, ArchUnit strings, resource-bundle paths, leftover greps, and
quality-gate interpretation. Do not chat-rewrite `Item` or other central
types.

---

## Locked decisions

| ID | Decision |
|---|---|
| D1 | Breaking `.frb` Java-serialization compatibility is acceptable. Keep save/load *code*; no `resolveClass` shims. |
| D2 | `gui.workspace.WorkspaceSettings extends GuiSettingsSource`. `settings.GuiSettings` → `GuiApplicationSettings`. Accessor `WorkspaceContract.getWorkspaceSettings()`. |
| D3 | **Session** = API/job session. **Primary session** = the one session the desktop may drive. **Workspace** = the desktop editor surface bound to that primary session (`gui.workspace`). |
| D4 | Keep the `BoardManager` family and the `Batch*` family. `GuiBoardManager` stays (it *hosts* the workspace). |
| D5 | Moderate packages: MCP controller, analytics, `gui.workspace`, `io.kicad` DRC DTOs, `core.library`, `i18n` + `Common_*.properties`, `management.jobs`, `management.sessions`. |
| D6 | Underscore / `p_` cleanup for all of `src/main/java` and `src/test/java` (not `src_v19/`), sliced; autoroute/shove last. |
| D7 | KiCad DRC DTOs → `io.kicad` as `KiCadDrcReport` / `KiCadDrcViolation` / `KiCadDrcViolationItem` / `KiCadDrcPosition`. Different type from `KiCadBoardJson`; same `KiCad` prefix. |
| D8 | User-facing wire names: publish the new name now, drop the old name in the **next minor**. Internal Java names have no window. Python client (same owner) updates in that window. |
| D9 | Do not wait for the `DAC2020_bm01.dsn` DRC discrepancy. |
| Q1 | Merge JSON/DSN/env/CLI (and other sources below 65) **seed** workspace settings at load. After the user changes a control, live `WorkspaceSettings` **wins**. GUI source priority **65** (above CLI 60, below API 70). Fix stale “priority 50” docs. |
| Q4 | `SessionManager` → `management.sessions`. |
| Q5 | Keep `core.Package` when moving to `core.library`. |
| Q6 | Move `Common_*.properties` with `TextManager` into `i18n`. Per-class bundles still move with their classes. |
| Q7 | Epic `refactor/naming-and-packages`; stacked phase branches; epic → `master` at the end. |
| Q8 | `WorkspaceContract`. Keep `GuiBoardManager`. |

---

## Glossary

| Term | Meaning | Code |
|---|---|---|
| **Session** | API/job container (caller, host, queued jobs). HTTP `/v1/sessions`. | `core.Session`, `management.sessions.SessionManager`, `api.v1.SessionControllerV1` |
| **Primary session** | The one session the desktop may show and drive. At most one. | `Session.isPrimary` (today `isPrimary`, `transient`) |
| **Workspace** | Desktop editor surface bound to the primary session. | Package `gui.workspace` (today `gui.session`) |

Do not put `Session` in GUI type names. Do not name the GUI package
`gui.editor` (that fights `gui.interactive`).

---

## End-state map (source of truth)

### Types

| Current FQCN | New FQCN / action |
|---|---|
| `settings.GuiSettings` | `settings.GuiApplicationSettings` |
| `settings.sources.GuiSettings` | `settings.sources.GuiSettingsSource` |
| `gui.session.InteractiveSettings` | `gui.workspace.WorkspaceSettings` **extends** `GuiSettingsSource` |
| `gui.session.GuiSessionContract` | `gui.workspace.WorkspaceContract` |
| `gui.session.GuiSessionPort` | `gui.workspace.WorkspacePort` |
| `gui.session.GuiSessionPortAdapter` | `gui.workspace.WorkspacePortAdapter` |
| `gui.session.SessionGeneration` | `gui.workspace.WorkspaceGeneration` |
| `gui.interactive.Settings` | **Delete** (no remaining imports) |
| `io.specctra.parser.RulesFile` | **Delete** |
| `io.specctra.parser.SpecctraSesFileWriter` | **Delete** |
| `io.specctra.parser.SesFileReader` | **Delete** |
| `drc.DrcReport` | `io.kicad.KiCadDrcReport` |
| `drc.DrcViolation` | `io.kicad.KiCadDrcViolation` |
| `drc.DrcViolationItem` | `io.kicad.KiCadDrcViolationItem` |
| `drc.DrcPosition` | `io.kicad.KiCadDrcPosition` |
| `api.v1.McpControllerV1` | `api.mcp.McpControllerV1` (HTTP paths unchanged) |
| `core.Session.isGuiSession` | `isPrimary` |
| `WorkspaceContract.getInteractiveSettings()` | `getWorkspaceSettings()` |
| `board.CalcFromSide` | `board.ShapeEntrySide` |
| `board.CalcShapeAndFromSide` | `board.ShapeAndEntrySide` |
| `board.ForcedPadAlgo` | `board.ForcedPadRouter` |
| `board.ForcedViaAlgo` | `board.ForcedViaInserter` |
| `board.MoveDrillItemAlgo` | `board.DrillItemMover` |
| `board.OptViaAlgo` | `board.ViaOptimizer` |
| `gui.GUIDefaultsFile` | `gui.GuiDefaultsFile` |

Keep: `GuiBoardManager`, `HeadlessBoardManager`, `BoardManager`,
`BatchAutorouter` / `BatchOptimizer` / `BatchFanout` / `BatchAutorouterV19`,
`RouterSettings`, `BasicBoard`, `Item`, `ShapeSearchTree`,
`DesignRulesChecker`, `AirLine`, `NamedAlgorithm`, `InteractiveState`,
`core.Package`.

`GlobalSettings.guiSettings`: keep the **Java field name** and
`@SerializedName("gui")`. Only the field’s *type* becomes
`GuiApplicationSettings`.

### Packages

| From | To | Contents |
|---|---|---|
| `gui.session` | `gui.workspace` | Entire package (~31 production types) + tests + `ScreenMessages_*.properties` + `GuiBoardManager_*.properties` |
| `management.analytics` | `app.freerouting.analytics` | Client, BigQuery, DTOs |
| `util.TextManager` | `util.TextManager` | Retained in `util` (text formatting & helpers); `Common_*.properties` at `app.freerouting` |
| *(new)* `core.library` | `BoardLibrary`, `Package`, `Packages`, `Padstack`, `Padstacks`, `LogicalPart`, `LogicalParts` |
| *(new)* `management.jobs` | `RoutingJobScheduler`, `RoutingJobSchedulerActionThread`, `ThreadActionListener` |
| *(new)* `management.sessions` | `SessionManager` |
| `management` root | stays | `BoardManager`, `HeadlessBoardManager`, `BoardLoader` |

Leave in `core`: `Session`, `RoutingJob*`, `StoppableThread`, `RouterCounters`,
`BoardFileDetails`, `ProgressThrottler`, `core.scoring`, `core.events`.

### Settings priority ladder (after Q1)

| Priority | Source | Class |
|---|---|---|
| 0 | Defaults | `DefaultSettings` |
| 10 | JSON file | `JsonFileSettings` |
| 20 | DSN | `DsnFileSettings` |
| 30 | SES | `SesFileSettings` |
| 40 | RULES | `RulesFileSettings` |
| 55 | Environment | `EnvironmentVariablesSource` |
| 60 | CLI | `CliSettings` |
| **65** | Live workspace / GUI placeholder | `GuiSettingsSource` / `WorkspaceSettings` |
| 70 | REST API | `ApiSettings` |

**Seed-then-live:** at board load, `merge()` sources 0–60, copy the result
into `WorkspaceSettings` (`setSettings` **and** live overlay fields
`tracePullTightAccuracy`, `automaticNeckdown` when the merged values are
non-null). After a GUI edit, priority 65 beats CLI/env. API jobs do not
register this source.

---

## What must not change (wire / behavior)

- HTTP paths, including `/v1/jobs/{jobId}/drc` and `/v1/mcp`.
- JSON keys (`@SerializedName`), `freerouting.json` `gui` block, `--router.*`,
  `FREEROUTING__ROUTER__*`.
- KiCad DRC JSON field names (KiCad’s `drc.v1.json` schema).
- Routing/DRC/scoring results, except Q1 seeding of live overlay fields.
- `src_v19/`.

OpenAPI schema *title* for the DRC report: publish **`KiCadDrcReport`** in
this minor; changelog that old schema name `DrcReport` goes away in the
**next minor**. Do not freeze the old title.

---

## Tooling and git

### IntelliJ

Rename class, move class, rename method/parameter. Turn **off** “search for
text occurrences” on package moves unless docs/properties are in scope for
that commit.

### LLM / human after the IDE step

Move `*.properties` to match `Class.getName()`. Update ArchUnit FQCNs and
package prefixes. Update AGENTS.md, `docs/architecture.md`, `docs/settings.md`,
API/MCP docs. Leftover-name grep. Quality gates.

### Branches

| Branch | Merges into |
|---|---|
| `refactor/naming-and-packages` | `master` at the end |
| `refactor/naming-phase-1-gui-settings` | epic |
| `refactor/naming-phase-2-workspace` | epic |
| `refactor/naming-phase-3-kicad-mcp` | epic |
| `refactor/naming-phase-4-packages` | epic |
| `refactor/naming-phase-5-curr` | epic |
| `refactor/naming-phase-6-identifiers` | epic (may be several PRs) |

Create each phase branch from the **epic** (after the previous phase has
merged). Do not branch phases from stale `master`.

### Quality gates (every phase, Windows)

```
gradlew.bat spotlessCheck checkstyleMain checkstyleTest checkstyleRewriteRecipes
gradlew.bat test --tests app.freerouting.architecture.ModuleBoundariesArchTest --tests app.freerouting.io.SpecctraPackageArchTest
```

Plus the phase-specific tests listed below. When Java or translation sources
change: `python scripts/i18n/extract-context.py --check`. When GUI packages
move: `gradlew.bat testGui` if that task is part of the merge candidate.
Routing-adjacent slices: `Issue508Test_BM01_first_2_nets`. Inspect
`git diff --stat` and `git diff --check`. Never `spotlessApply`.

Leftover grep (exclude `src_v19/` and historical `docs/issues/` write-ups
unless the issue file states a live invariant):

```
rg -n "InteractiveSettings|GuiSessionContract|isGuiSession|gui\.session|settings\.sources\.GuiSettings[^.S]|drc\.DrcReport|api\.v1\.McpControllerV1|util\.TextManager|app\.freerouting\.Common" --glob "!src_v19/**"
```

Narrow the pattern to the names that phase removed.

---

## Docs that must stay in sync

Update in the **same phase** that changes the name:

| File | Why |
|---|---|
| `AGENTS.md` | `WorkspaceSettings` singleton, merger priority 65, `GuiSettingsSource` subtype, `gui.session` |
| `docs/architecture.md` | Package glossary, mermaid (`api.v1.McpControllerV1`, `management`, `core`) |
| `docs/settings.md` | Priority table row 50 → 65; class names |
| `docs/API/API_v1.md` / `docs/API/MCP.md` | MCP controller package; DRC schema title |
| `docs/issues/soc-gui-separation-and-accessibility-plan.md` | Live invariants that name `WorkspaceSettings` / `gui.session` / priority 65 |
| This plan | Already the spec; do not leave it contradicting the code |

Issue archaeology under `docs/issues/` may keep old names as history.
Contributor *invariants* in AGENTS.md and the SoC plan § that are still
normative must be rewritten.

---

## ArchUnit (must edit when packages move)

`ModuleBoundariesArchTest`:

- `app.freerouting.gui.session.GuiBoardManager` → `app.freerouting.gui.workspace.GuiBoardManager` (two tests).
- `resideInAnyPackage("app.freerouting.gui.session..")` → `gui.workspace..` (`guiSessionMustNotDependOnConcreteInteractiveStates`).
- Worker FQCNs: `gui.session.InteractiveActionThread` and
  `gui.session.AutorouterAndRouteOptimizerThread` → `gui.workspace.*`.
- `PIPELINE_SUPPORT_PACKAGES`: add `"app.freerouting.i18n.."` when `TextManager`
  moves (board/rules already call it). Do **not** add `analytics` to the
  pipeline list.
- After analytics moves out of `management`, extend the
  “api/management must not depend on gui” tests to also name
  `"app.freerouting.analytics.."`.
- `management.jobs` / `management.sessions` / `core.library` stay inside
  existing `management..` / `core..` prefixes — no rule change required
  beyond imports.

`SpecctraPackageArchTest`: no FQCN change expected; still forbids GUI/management
imports from `io.specctra`.

---

## Phase runbooks

### Phase 1 — Q1 seed + GuiSettings split + dead shells

Branch: `refactor/naming-phase-1-gui-settings`

**Progress checklist**

- [x] Implement Q1 seed-then-live settings precedence at GUI priority 65.
- [x] Rename `GuiSettings` types and remove the four dead compatibility shells.
- [x] Update references, Javadocs, architecture strings, and normative documentation.
- [x] Add/extend settings integration tests and run the Phase 1 quality gates.
- [ ] Create the phase branch from the epic and merge the reviewed Phase 1 PR into the epic.

**1a. Q1 (first commit on the epic / this branch)**

- Keep `GuiSettingsSource` / current class priority **65**.
- Override `setSettings(RouterSettings)` on the live GUI source (today
  `WorkspaceSettings`, later `WorkspaceSettings`) so non-null
  `tracePullTightAccuracy` and `automaticNeckdown` copy onto the live
  fields. Today `setSettings` only stores the snapshot on the superclass
  and `getSettings()` overlays constructor defaults (500 / true), which
  can ignore CLI.
- At board load, keep copying `merger.merge()` into the live source (already
  `BoardFrame.attachParsedBoard` → `workspaceSettings.setSettings(mergedSettings)`).
- Extend `WorkspaceSettingsMergerTest`: CLI sets
  `tracePullTightAccuracy` to a non-default; after bind+merge, GUI shows
  that value; after `setTracePullTightAccuracy`, merge uses the GUI value
  and not CLI.
- Rewrite docs/comments/AGENTS.md/settings.md from priority **50 → 65**.
  Do not change 55/60/70.

**1b. Renames (IDE)**

- `settings.GuiSettings` → `GuiApplicationSettings` (keep field
  `GlobalSettings.guiSettings` and JSON `"gui"`).
- `settings.sources.GuiSettings` → `GuiSettingsSource`.
- `WorkspaceSettings` still extends the source type; do **not** rename it
  yet (Phase 2).

**1c. Deletes**

- `gui.interactive.Settings`
- `io.specctra.parser.RulesFile`
- `io.specctra.parser.SpecctraSesFileWriter`
- `io.specctra.parser.SesFileReader`

Grep for remaining references (including `@deprecated` javadoc links).
Update `SettingsMerger` javadoc (`GuiSettings` → `GuiSettingsSource`,
priority 65).

**Tests:** `WorkspaceSettingsMergerTest`, `JsonFileSettingsTest` if it
touches `GuiApplicationSettings`, `GuiStartupHeadlessTest`, architecture tests.

**Not in this phase:** `gui.session` package move, `WorkspaceSettings`.

---

### Phase 2 — workspace package + WorkspaceSettings + primary session

Branch: `refactor/naming-phase-2-workspace`

**Progress checklist**

- [x] Move `gui.session` to `gui.workspace` and apply the workspace type/accessor renames.
- [x] Rename `InteractiveSettings` to `WorkspaceSettings` while preserving the GUI settings invariants.
- [x] Move matching resources and tests; update mocks and ArchUnit references.
- [x] Update AGENTS.md, architecture, and SoC documentation.
- [x] Run workspace/session tests, architecture tests, `testGui`, and the i18n context check.
- [ ] Review and merge the Phase 2 changes into the epic.

IDE move entire `app.freerouting.gui.session` → `app.freerouting.gui.workspace`.
Then rename:

- `InteractiveSettings` → `WorkspaceSettings`
- `GuiSessionContract` → `WorkspaceContract`
- `getInteractiveSettings` → `getWorkspaceSettings` (contract, `GuiBoardManager`, all call sites)
- `GuiSessionPort` / `GuiSessionPortAdapter` → `WorkspacePort` / `WorkspacePortAdapter`
- `SessionGeneration` → `WorkspaceGeneration` (`LoadGeneration` / `RunGeneration` keep names)
- `Session.isGuiSession` → `isPrimary`
- `SessionManager` user-visible messages: “GUI session” → “primary session”
  (`getPrimarySession` / `setPrimarySession` already exist)

Keep `GuiBoardManager`, `InteractiveActionThread`,
`AutorouterAndRouteOptimizerThread`, `InteractiveCommand`, `EditorState*`.

**Resources:** move

- `src/main/resources/app/freerouting/gui/session/GuiBoardManager_*.properties`
- `src/main/resources/app/freerouting/gui/session/ScreenMessages_*.properties`

to `.../gui/workspace/` (class names unchanged, package path must match
`Class.getName()`).

**Tests:** move `src/test/java/app/freerouting/gui/session/` →
`gui/workspace/` and rename test classes that contain `InteractiveSettings`
or `GuiSession` (`InteractiveSettingsSingletonTest` →
`WorkspaceSettingsSingletonTest`, `InteractiveSettingsPropertyChangeTest` → `WorkspaceSettingsPropertyChangeTest`,
`GuiSessionPortTest` → `WorkspacePortTest`, `SettingsMergerGuiIntegrationTest`
stays or becomes `WorkspaceSettingsMergerTest`).

**ArchUnit:** all `gui.session` strings listed above.

**Docs:** AGENTS.md InteractiveSettings section → WorkspaceSettings;
`gui.session` → `gui.workspace`; architecture mermaid/glossary; SoC plan
live invariants.

**Mocks:** `SessionControllerMocked` JSON `isGuiSession` is internal; update
to `isPrimary` or drop the field (it is `transient` and not API JSON).

**Gates:** architecture tests, session/workspace tests, `testGui`,
`extract-context.py --check`.

---

### Phase 3 — KiCad DRC DTOs + MCP controller

Branch: `refactor/naming-phase-3-kicad-mcp`

**Progress checklist**

- [x] Create the phase branch from the updated epic.
- [x] Move and rename the KiCad DRC DTOs while preserving all wire field names.
- [x] Update `DesignRulesChecker`, OpenAPI schema naming, and the MCP controller package.
- [x] Preserve `/v1/mcp` and `/v1/jobs/{jobId}/drc` paths and document the schema-title transition.
- [x] Update DRC, MCP, CLI, and reflection-based tests.
- [x] Schedule the Python client follow-up for the next minor release.
- [x] Run Phase 3 quality gates and merge the PR into the epic.

Move/rename in `io.kicad` (beside `KiCadBoardJson`, no `io.kicad.drc`
subpackage):

- `DrcReport` → `KiCadDrcReport`
- `DrcViolation` → `KiCadDrcViolation`
- `DrcViolationItem` → `KiCadDrcViolationItem`
- `DrcPosition` → `KiCadDrcPosition`

Keep every `@SerializedName` value. `DesignRulesChecker.generateReport`
return type becomes `KiCadDrcReport`. `drc` keeps `DesignRulesChecker`,
`ClearanceViolation`, `NetIncompletes`, `AirLine`.

OpenAPI: schema title **`KiCadDrcReport`** (explicit `@Schema(name = "KiCadDrcReport")`
on the type if Swagger infers the simple name). Changelog: old schema name
`KiCadDrcReport` removed next minor. HTTP path unchanged.

Move `api.v1.McpControllerV1` → `api.mcp.McpControllerV1`. Update
`McpApplication`, `OpenApiResource`, `McpEndpointsTest` reflection FQCN.
HTTP `/v1/mcp` unchanged.

**Docs:** `docs/architecture.md` MCP node; `docs/API/MCP.md`; `docs/API/API_v1.md`.

**Tests:** `DesignRulesCheckerTest`, `UnconnectedItemsReproductionTest`,
`McpEndpointsTest`, `Freerouting` DRC CLI path.

**Python client:** schedule the schema-title follow-up in this minor so the
next minor can drop `KiCadDrcReport`.

---

### Phase 4 — remaining package splits

Branch: `refactor/naming-phase-4-packages`

**Progress checklist**

- [x] Create the phase branch from the updated epic.
- [x] Move analytics to `app.freerouting.analytics` and update GUI-isolation rules.
- [x] Move library types/resources to `core.library`, keeping `Package` unchanged.
- [x] Retain `TextManager` and common bundles in `util` / root.
- [x] Move job and session management types/tests to their new packages (`management.jobs`, `management.sessions`).
- [x] Update ArchUnit rules, architecture documentation, and package-local tests.
- [x] Run Phase 4 quality gates and merge the PR into the epic.

Do as **one PR if the diff stays reviewable**, otherwise split in this order:

1. `management.analytics` → `app.freerouting.analytics` (update ArchUnit GUI
   isolation to include `analytics..`).
2. `core.library` move of the seven library types; keep `Package` name;
   move any `Package_*.properties` / `Padstack_*.properties` /
   `LogicalPart_*.properties` with the classes (`TextManager(this.getClass())`).
3. `TextManager` → `i18n.TextManager`; move `Common_*.properties` to
   `src/main/resources/app/freerouting/i18n/`; change both
   `loadBundle("app.freerouting.Common", …)` strings and the error message
   that names the bundle. i18n tests / `EnglishPropertiesParityTest` if they
   hard-code `app.freerouting.Common`.
4. `management.jobs` and `management.sessions`; move
   `RoutingJobSchedulerTest` → `management.jobs` (or keep test package
   mirroring production), `SessionManagerTest` → `management.sessions`.
   `PowerPlaneValidationTest` stays unless it only exists for board manager.

**ArchUnit:** add `i18n..` to `PIPELINE_SUPPORT_PACKAGES`.

**Docs:** architecture glossary (`core` / `management` / new `analytics` /
`i18n`).

---

### Phase 5 — `curr*` locals

Branch: `refactor/naming-phase-5-curr`

**Progress checklist**

- [x] Create the phase branch from the updated epic.
- [x] Rename `curr*` locals in the `board` package and run the BM01 smoke test.
- [x] Rename `curr*` locals in `autoroute` and run the BM01 smoke test.
- [x] Rename `curr*` locals in `gui`.
- [x] Rename remaining `curr*` locals without changing types or packages.
- [x] Review routing-sensitive diffs, run quality gates, and merge the PR into the epic.

IDE structural search, **one top-level package per commit** if the diff is
large (`board`, then `autoroute`, then `gui`, then the rest).

| Pattern | Replacement | When |
|---|---|---|
| `currTrace` | `currentTrace` | Loop/state current trace |
| `currTrace` | `traceToAdjust` | Pull-tight target |
| `currTrace` | `trace` | Scope already says current |
| `currItem` / `currLayer` / `currDoor` / `currShape` | `currentItem` / `currentLayer` / `currentDoor` / `currentShape` | |
| `currNetNo` | `currentNetNumber` | Net number, not `Net` |
| `currTraceInfo` | `traceInfo` or `currentTraceInfo` | |
| `currTraceCount` | `traceCount` | |

No type/package renames in this phase. Smoke
`Issue508Test_BM01_first_2_nets` after `board`/`autoroute` slices.

---

### Phase 6 — underscore and `p_` identifiers

Branch: `refactor/naming-phase-6-identifiers` (or several PRs on that branch)

**Progress checklist**

- [x] Create the phase branch from the updated epic.
- [x] Convert identifiers in GUI, settings, API, analytics, management, and i18n.
- [x] Convert identifiers in `drc`, `rules`, `io`, and `core`.
- [x] Convert non-algorithm `board` identifiers.
- [x] Last, convert `autoroute` and shove/pull-tight/via algorithm identifiers.
- [x] Verify no snake_case Java identifiers or `p_` parameters remain outside `src_v19/`.
- [x] Preserve acronyms, wire names, CLI/JSON keys, and behavior; run BM01 and all gates.
- [x] Review and merge the Phase 6 PR(s) into the epic.

End state: no `snake_case` Java identifiers and no `p_` parameter prefixes in
`src/main/java` and `src/test/java`.

Slices:

1. GUI, settings, API, management/analytics/i18n (example: `get_locale()` →
   `getLocale()`, `last_repainted_time` → `lastRepaintedTime`).
2. `drc`, `rules`, `io`, `core`.
3. `board` non-algorithm types.
4. **Last:** `autoroute` and shove/pull-tight/via algorithms. Review as
   routing-sensitive even when behavior-neutral. Smoke BM01.

Do not expand API, MCP, DSN, SES, DRC, EDT, SMD. Do not rename JSON/CLI
keys. Do not mix with type/package PRs.

---

### Phase 7 — Functional class renames

Branch: `refactor/naming-phase-7-classes`

**Progress checklist**

- [x] Rename `CalcFromSide` → `ShapeEntrySide`.
- [x] Rename `CalcShapeAndFromSide` → `ShapeAndEntrySide`.
- [x] Rename `ForcedPadAlgo` → `ForcedPadRouter`.
- [x] Rename `ForcedViaAlgo` → `ForcedViaInserter`.
- [x] Rename `MoveDrillItemAlgo` → `DrillItemMover`.
- [x] Rename `OptViaAlgo` → `ViaOptimizer`.
- [x] Rename `GUIDefaultsFile` → `GuiDefaultsFile` and `GUIDefaultsScanner` → `GuiDefaultsScanner`.
- [x] Run quality gates and verify BM01 test suite.

---

### Phase 8 — Net, layer, and clearance abbreviation expansion

Branch: `refactor/naming-phase-8-abbreviations`

**Progress checklist**

- [x] Expand `netNo` / `currentNetNo` → `netNumber`, `netNoArr` → `netNumbers`.
- [x] Expand `layerNo` / `currentLayerNo` → `layerIndex` (0-based) and `layerNumber` (1-based / display).
- [x] Expand `clearanceClassNo` → `clearanceClassIndex`.
- [x] Expand `currentOb` → `currentObject` / `currentObstacle`.
- [x] Update getters/methods: `getNetNo()` → `getNetNumber()`, `maxNetNo()` → `maxNetNumber()`, `containsNet(int netNumber)`.
- [x] Run quality gates and verify full unit test suite.

---

### Phase 9 — Parameter naming polish

Branch: `refactor/naming-phase-9-parameters`

**Progress checklist**

- [x] Specctra scope parameter: `WriteScopeParameter par` → `WriteScopeParameter scopeParameter` and `ReadScopeParameter par` → `ReadScopeParameter scopeParameter`.
- [x] Object equality: `public boolean equals(Object obj)` → `public boolean equals(Object other)`.
- [x] Geometry parameters: `Point p` → `Point point`, `Line l` → `Line line`, `TileShape s` → `TileShape shape`.
- [x] Single letter & index parameters: `int no` → `int index` / `int pinNumber` / `int cornerIndex`.
- [x] Run full test suite and quality gates.

---

### Phase 10 — Array naming modernization (*Arr -> plural nouns)

Branch: `refactor/naming-phase-10-arrays`

**Progress checklist**

- [x] Core library & rules fields (`LayerStructure.layers`, `Nets.nets`, `Package.pins`, `Padstacks.padstacks`).
- [x] Geometry fields & methods (`Polyline.lines`, `Simplex.lines`, `Polygon.corners`).
- [x] Autoroute & settings array fields (`AutorouteControl.viaRadii`, `traceCosts`).
- [x] Specctra parser array fields (`pins`, `keepouts`, `classNames`).
- [x] Local array variables across `/src/` (`lines`, `points`, `corners`, `shapes`, `items`).
- [x] Run full test suite (`./gradlew check`) and quality gates.

---

### Phase 11 — Algorithmic engine class naming modernization (`*Algo` -> Domain role classes)

Branch: `refactor/naming-phase-11-algo-classes`

**Progress checklist**

- [x] Rename `PullTightAlgo` / `PullTightAlgo45` / `PullTightAlgo90` / `PullTightAlgoAnyAngle` → `TraceTightener` / `TraceTightener45` / `TraceTightener90` / `TraceTightenerAnyAngle`.
- [x] Rename `ShoveTraceAlgo` → `TraceShover`.
- [x] Rename `MazeSearchAlgo` → `MazeSearchEngine`.
- [x] Rename `MazeShoveTraceAlgo` → `MazeTraceShover`.
- [x] Rename `InsertFoundConnectionAlgo` → `FoundConnectionInserter`.
- [x] Rename `LocateFoundConnectionAlgo` / `LocateFoundConnectionAlgo45Degree` / `LocateFoundConnectionAlgoAnyAngle` → `FoundConnectionLocator` / `FoundConnectionLocator45Degree` / `FoundConnectionLocatorAnyAngle`.
- [x] Update all call sites, imports, and references.
- [x] Run full test suite and quality gates.

---

### Phase 12 — Clearance class index standardization (`clType` / `clearanceType` / `clClass` -> `clearanceClassIndex`)

Branch: `refactor/naming-phase-12-clearance-index`

**Progress checklist**

- [ ] Standardize `int clType`, `int clearanceType`, `int clClass` parameters in `app.freerouting.board` to `clearanceClassIndex`.
- [ ] Standardize parameters and fields in `app.freerouting.autoroute` to `clearanceClassIndex`.
- [ ] Standardize parameters in `app.freerouting.drc` and `app.freerouting.rules` to `clearanceClassIndex`.
- [ ] Standardize local variables (`currentClType`, `ignoreClType`) to `currentClearanceClassIndex`, `ignoreClearanceClassIndex`.
- [ ] Run full test suite and quality gates.

---

### Phase 13 — Domain number & index abbreviation expansion (`*No` / `*Ind` -> `*Number` / `*Index` / `*Id`)

Branch: `refactor/naming-phase-13-id-and-indices`

**Progress checklist**

- [ ] Item, component, and group IDs: `Item.getIdNo()` → `Item.getIdNumber()` (or `Item.getId()`), `Pin.getComponentNo()` → `getComponentNumber()`, `groupNo` → `groupNumber`.
- [ ] Pin numbers: `Pin.pinNo` → `Pin.pinNumber`, `Package.Pin.pinNo` → `pinNumber`.
- [ ] Geometric indices: `cornerNo` → `cornerIndex`, `shapeNo` → `shapeIndex`, `lineNo` → `lineIndex`.
- [ ] Graph and search indices: `doorNo` → `doorIndex`, `sectionNo` → `sectionIndex`, `treeIdNo` → `treeId`.
- [ ] Run full test suite and quality gates.

---

### Phase 14 — Interface & boundary clarity

Branch: `refactor/naming-phase-14-interfaces`

**Progress checklist**

- [ ] Rename `app.freerouting.board.ObjectInfoPanel` interface → `app.freerouting.board.ItemInfoPrinter` (decoupling from UI JPanel confusion).
- [ ] Rename `ReadScopeParameter.itemIdNoGenerator` → `idGenerator` / `itemIdentificationNumberGenerator`.
- [ ] Update all implementing classes (`PrintInfoWindow`, `BoardPrintInfo`, etc.) and callers.
- [ ] Run full test suite and quality gates.

---

### Phase 15 — Java modernization & switch expressions

Branch: `refactor/naming-phase-15-java-modernization`

**Progress checklist**

- [ ] Convert verbose `switch` statements to modern arrow switch expressions (`case A -> ...`) across `geometry.planar`, `io.specctra`, `drc`.
- [ ] Convert simple immutable data carriers to Java records (`ExpansionCostFactor`, `BoardHistoryEntry`, etc.).
- [ ] Modernize collection instantiation with `List.copyOf()`, `Set.copyOf()`, `List.of()` where immutable collections are created.
- [ ] Run full test suite and quality gates.

---

### Phase 16 — Javadoc & legacy comment cleanup

Branch: `refactor/naming-phase-16-javadoc-cleanup`

**Progress checklist**

- [ ] Update stale snake_case method references in javadocs/comments (`normalize_traces()`, `is_tail()`, `bounding_octagon()`, `get_direction()`, `start_point()`, `end_point()`, `edge_line_count()`, `border_line_count()`).
- [ ] Verify `python scripts/i18n/extract-context.py --check` and build docs.
- [ ] Run full verification suite (`./gradlew check`).

---

## Explicitly out of this campaign

`RouterSettings`, `BasicBoard`, `Item`, `ShapeSearchTree`,
`SearchTreeManager`, `DesignRulesChecker` split, `AirLine`, `NetIncompletes`,
`BoardStatistics`, `BoardRules`/`Nets`, `NamedAlgorithm`, `Batch*` family,
`GuiManager`, `InteractiveState`, parser `NetClass`/`Unit` homonyms,
`management` → `orchestration`, `board`/`autoroute` subpackages, GUI
windows/menus packages, DRC *behavior* extraction.

---

## Compatibility and collisions (implementer notes)

- `.frb` FQCNs will break (D1). No shims.
- `SettingsMerger.addOrReplaceSources` replaces by exact class **or**
  existing class `isAssignableFrom` new class. `WorkspaceSettings` must
  remain a subclass of `GuiSettingsSource` so it still replaces the
  placeholder.
- `NamedAlgorithm` must never be renamed to `RoutingStage` (`core.RoutingStage`
  enum already exists).
- `KiCadBoardJson` = board interchange JSON. `KiCadDrcReport` = KiCad DRC
  report schema. Same prefix, different classes.
- Class-based i18n: `new TextManager(this.getClass(), locale)` loads
  `class.getName()` bundles. Moving a class without its `*.properties` looks
  like a successful compile and a missing UI string.

---

## Campaign done when

- Every row in the end-state map is applied.
- Grep for old names is clean outside `src_v19/` and historical issue notes.
- ArchUnit green with `gui.workspace`, `i18n`, `analytics`.
- AGENTS.md, architecture, settings docs match the glossary and priority 65.
- Q1 tests prove CLI/env seed then GUI override.
- OpenAPI shows `KiCadDrcReport`; `/v1/mcp` and `/v1/jobs/{id}/drc` unchanged.
- `spotlessCheck`, Checkstyle, `git diff --check`, `extract-context.py --check`
  green on the epic.
- Epic PR to `master` lists D8: drop old schema name `DrcReport` in the **next
  minor**; Python client follow-up owned by the same maintainer.

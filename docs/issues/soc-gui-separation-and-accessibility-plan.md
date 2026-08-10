# GUI Separation and Accessibility Migration Plan

> Status: **Ready to implement** (D1–D30 locked; M1=A / M4=B incorporated; implementation not started)
>
> Scope: Separate GUI interaction and rendering from the headless routing pipeline, establish automated GUI accessibility coverage, and reorganize only the packages required for that separation.
>
> Related documentation:
> - [`docs/architecture.md`](../architecture.md)
> - [`src/test/java/app/freerouting/architecture/ModuleBoundariesArchTest.java`](../../src/test/java/app/freerouting/architecture/ModuleBoundariesArchTest.java)
> - [`docs/research/code_structure_recommendations.md`](../research/code_structure_recommendations.md)
> - [`docs/issues/soc-gui-separation-and-accessibility-review.md`](soc-gui-separation-and-accessibility-review.md) (peer review)
>
> **Boundary debt:** Live freeze ledger and accepted debt live in **§12 of this document**. The former `soc-architecture-boundary-debt-tracker.md` was stale (claimed zero freezes; inventory FIXED as of 2026-06) and has been retired. `AGENTS.md` should point here.

## 0. Phase overview (goals, impact, risk)

| Phase | Main goal(s) | Impact | Risk | Depends on |
| --- | --- | --- | --- | --- |
| **0** Baseline | Capture packages, violations, DSN coverage map, **v2.3.0 golden metrics**, green baseline | Low | Low | — |
| **1** Inventory + ArchUnit freeze | Leakage non-expandable; name-collision + EDT + GUI-test inventories | **High** | Low–Medium | 0 |
| **2** Accessibility foundation | Pure-JDK a11y harness + ≥3 component-only workflows; forced headless `testGui` | **High** | Medium | 0 (∥ with 1) |
| **3** Headless contracts | Remove GUI nullables from `BoardManager` | **High** | Medium | 1 |
| **4** Neutralize `core` / service leaks | Strip Swing from `RoutingJob`; `getPrimarySession` / `setPrimarySession` | **High** | Medium | 1, 3 |
| **5** Split compute vs presentation | Thin ratsnest/violations façades; keep `ObjectInfoPanel` in `board` | **High** | Medium | 1, 3 |
| **6** Invert board rendering | Domain stops painting; GUI renderer owns paint (revertible commits) | **Critical** | **High** | 1, 5 |
| **7** Invert autorouter diagnostics | Engine emits data; GUI/debug draws | High | Medium–High | 1, 6* |
| **8** Move `interactive` → `gui.interactive` | Flat package rename/move | Medium | Medium | 3–5 |
| **9** Extract `gui.session` | Session cluster + session-owned facade/`InteractiveCommand`; views bootstrap | High | Medium | 3, 8 |
| **10** Move `boardgraphics` → `gui.rendering` | Rendering under GUI + `ScreenTransform` | Medium | Low–Medium | 6, 7 |
| **11** Accessibility expansion + CI | Broader coverage; path-filtered `testGui` | High | Medium | 2 |
| **12** Final cleanup + docs | Strict ArchUnit; architecture docs; empty freeze ledger | High | Low | all prior |

\*Phase 7 may start after Phase 6 has removed shared draw patterns / frozen new AWT UI parameters in `autoroute`, even if item-family renderer commits are still landing.

**Sequencing rules:**

- Phase 1 ∥ Phase 2 is encouraged.
- Do **not** start Phase 8–10 until Phase 3–5 are done and Phase 6 has at least removed `board → boardgraphics` paint APIs (or frozen them with a removal plan already in progress).
- No `gui.windows` / `menus` / `panels` split.
- One long-lived branch; rebase/merge `master` regularly.
- Phase 6 work must land as **independently revertible commits** (neutral accessors → remove `Drawable`/paint → offscreen renderer).

## 1. Objective

Make the routing pipeline independently usable and testable without GUI classes, while making the Swing GUI maintainable and automatically testable through accessibility APIs.

### 1.1 Allowed dependency directions (authoritative)

This is the **strict** package graph. §1.2 is only a narrative summary.

```text
gui (views / windows / menus / panels)
  → may use gui.interactive, gui.session, gui.rendering, management, board, …

gui.interactive (editor states; implements session-owned command/facade interfaces)
  → may use gui.session, gui.rendering, board, …

gui.session (GuiBoardManager, InteractiveSettings, GUI action threads,
             InteractiveCommand + interactive facade/API types)
  → may use management, board, …
  → may use gui.rendering  (D26 — accepted; manager owns GraphicsContext state today)
  → must NOT depend on gui.interactive (D27 / D30)
     — no concrete *State types, and no InteractiveCommand interface from interactive
     — facade + InteractiveCommand (+ handles) live in gui.session (or gui.session.api)
     — concrete states in gui.interactive implement those session-owned interfaces
     — gui (views) owns initial-state bootstrap/registration (may see both packages)

gui.rendering (paint only)
  → may read board / diagnostic snapshots
  → must NOT be imported by board / autoroute / rules / drc / geometry / core / management / api

management / core / api
  → must NOT use gui.**

board / rules / autoroute / drc / geometry / datastructures / settings / logger / debug / util / io / core
  → must NOT use gui.** | javax.swing.** | java.awt UI types
  → may use java.awt.geom.** only (explicit whitelist)
```

**Phase-9 note:** Until the session-owned facade exists, a temporary §12 freeze on `gui.session → gui.interactive` is allowed only while the facade is being introduced in the same phase. By Phase 9 exit there must be **no** `gui.session → gui.interactive` edge (ArchUnit slice-cycle check must pass).
### 1.2 Narrative (non-authoritative)

Views and editor states sit above a GUI session façade. Rendering is a **sibling** of session: views/interactive use it for paint, and **session may also use it** because `GuiBoardManager` owns `GraphicsContext` state (D26). After Phase 9, session-owned interfaces (facade + `InteractiveCommand`) live in `gui.session`; concrete states in `gui.interactive` implement them; **views bootstrap** the initial state (D27/D30). Headless services and the routing pipeline sit below and must not see GUI types.

**Naming collision warning:** `management.Session` / `getPrimarySession` (UUID-backed job/session registry) is **unrelated** to package `gui.session` (Swing GUI board-session façade). Do not conflate them in reviews.

## 2. Locked decisions

| # | Topic | Decision |
| --- | --- | --- |
| D1 | GUI windows/menus/panels split | **Out of scope** |
| D2 | Branch strategy | **One long-lived branch** |
| D3 | Binary `.frb` fixtures | **None in repo** (verified). No fixture deletion work. Keep save/load **code**; no class-name compatibility shims if serialization FQCNs change |
| D4 | Done bar | **Phases 0–12** |
| D5 | GUI test runner | **`@Tag("gui")` + `testGui`** |
| D6 | CI trigger | **Path-filtered** GUI paths; ArchUnit always in normal `test` |
| D7 | Display strategy | **Component-only**; `testGui` forces `-Djava.awt.headless=true` |
| D8 | A11y tooling | **Pure JDK `AccessibleContext` harness** |
| D9 | Canvas a11y depth | **Critical keyboard/menu alternatives + inspect/item lists** |
| D10 | Rendering package | **`app.freerouting.gui.rendering`** |
| D11 | Interactive move | **Flat `gui.interactive` first** — flatten `interactive.commands` types into `gui.interactive` sources initially; Phase 9 moves the **`InteractiveCommand` interface** into `gui.session` (D30) |
| D12 | `GuiBoardManager` home | **Phase 8 park in `gui.interactive`; Phase 9 extract to `gui.session`** |
| D13 | Ratsnest compute | **Keep in `drc`** (`NetIncompletes` / `AirLine`); GUI presentation only outside |
| D14 | `ObjectInfoPanel` | **Keep AWT-free interface in `board`**; GUI implements |
| D15 | AWT policy | **Whitelist `java.awt.geom.*`; ban Swing + AWT UI types** |
| D16 | `SessionManager` primary session | **`getPrimarySession` / `setPrimarySession`** (management UUID session — not `gui.session`) |
| D17 | `.frb` save/load code | **Keep code**; there are no `.frb` fixtures to delete |
| D18 | Graphics transform rename | **Rename only `boardgraphics.CoordinateTransform` → `ScreenTransform`.** `board.CoordinateTransform` and `io.CoordinateTransform` stay unchanged. |
| D19 | A11y locale coverage | **English + Hungarian (`hu`)** (absorbs former R5 “EN+1” + R15 “pick `hu`”) |
| D20 | `gui.session` membership | **§4.4 default**, adjusted only by Phase 1 inventory |
| D21 | `InteractiveSettings` rename | **Keep name** |
| D22 | Accessible locator mechanism | **Stable locator constants + one shared registry/helper**; harness finds by locator, not translated label |
| D23 | Boundary debt home | **§12 of this plan** (old standalone tracker retired) |
| D24 | Routing parity baseline | **Phase out v1.9 as primary baseline.** Stable **v2.3.0** is the new baseline. Compare WIP to **v2.3.0**. Capture v2.3.0 golden metrics in Phase 0/1; update AGENTS.md in this initiative. |
| D25 | `testGui` in Gradle task graph | **`testGui` is part of `testAll`**. It is **not** part of `test` or `testSlow`. Path-filtered CI may still run `testGui` alone on GUI paths. |
| D26 | `gui.session` → `gui.rendering` | **Allowed** for this initiative (`GuiBoardManager` owns `GraphicsContext` state). Document honestly in §1.1. Optional later decoupling is out of scope. |
| D27 | `gui.session` ↔ interactive decoupling | **Decouple in Phase 9** so session does not import **any** `gui.interactive` type (not only concrete `*State`). States may depend on session. See **D30** for package ownership + bootstrap. |
| D28 | Parity gate frequency | **Full WIP-vs-v2.3.0 compare at Phase 5 and Phase 6 exits only.** Phases 7–8 get a **cheap** golden-fixture smoke: full DRC **and** completion/unrouted-net parity vs Phase 0 goldens (no full version compare). |
| D29 | Clearance gate metric | Use **`DesignRulesChecker.getAllClearanceViolations()`**, not `BoardStatistics.clearanceViolations.totalCount`. |
| D30 | Facade / command package + bootstrap | **Facade + `InteractiveCommand` (+ handles) live in `gui.session`** (or `gui.session.api`). Concrete states in `gui.interactive` implement them. **`gui` views own initial-state bootstrap/registration.** No `gui.session → gui.interactive` edge after Phase 9. |

- No `.frb` backward-compat `ObjectInputStream.resolveClass` mapping (and no `.frb` fixtures exist to migrate).
- No headful / Xvfb GUI CI requirement.
- No forced decoupling of `GuiBoardManager` from `GraphicsContext` in this initiative (D26).

## 3. Review findings incorporated

1. Debt tracker → §12; stale standalone tracker retired.
2. §1.1 authoritative graph; rendering is a sibling of session (**and session may use rendering — D26**).
3. Decision IDs contiguous **D1–D30**.
4. Package map includes headless-safe support packages; `core` listed in ban set.
5. `testGui` forces headless; belongs in `testAll` only.
6. Phase 0/1 inventories: DSN coverage, collisions (incl. cross-package `CoordinateTransform`), Swing retags, worker→Swing EDT, `commands` flatten.
7. Phase 6 revertible commits + early offscreen smoke.
8. A11y harness EDT asserts; locator registry; EN+`hu` + Hungarian resource check.
9. Routing parity baseline **v2.3.0** (D24) with **early golden-metric capture** in Phase 0/1; AGENTS.md updated in this initiative.
10. Full DRC gate via `DesignRulesChecker.getAllClearanceViolations()` (D29).
11. Phase 9: session-owned facade + `InteractiveCommand` in `gui.session`; views bootstrap (D27/D30); no session→interactive edge.
12. `interactive.commands` types flatten then `InteractiveCommand` moves to session (D11/D30).
13. Parity compares only at Phase 5/6; Phases 7–8 cheap smoke = full DRC **+ completion** (D28).
14. Scale ~116 GUI-related classes.

### 3.1 Former open-point remap

| Former | Now |
| --- | --- |
| R1 | D16 (`getPrimarySession`) |
| R2 | D22 (locator registry) |
| R3 | D17 (keep `.frb` code) |
| R4 | D18 (`ScreenTransform`; graphics only) |
| R5 | D19 (EN+1 policy; refined by R15) |
| R6 | D20 (§4.4 session cluster) |
| R7 | D21 (keep `InteractiveSettings` name) |
| R15 | D19 (pick `hu` — same decision as R5) |
| R16 | D24 (v2.3.0 baseline) |
| R17 | D25 (`testGui` in `testAll`) |
| N1 / N6 / N9 / N11 | D27 / D26 / D11 / D28 |
| M1 / M4 | D30 / D28 (completion added) |

R8–R14, R18, R19 remain open — see §10.

## 4. Target structure

### 4.1 Final package map

```text
app.freerouting
├── board, rules, autoroute, drc, geometry
├── datastructures, settings, logger, debug, util, io
├── core                 # jobs/sessions/stats — no Swing / no AWT UI
├── management           # HeadlessBoardManager, scheduler, analytics — no gui.**
├── api
└── gui
    ├── …                # existing Swing windows/menus/panels (flat; no further split)
    ├── interactive      # concrete editor states (implements session-owned command/facade APIs)
    ├── session          # GuiBoardManager, InteractiveSettings, ScreenMessages, GUI action threads,
    │                    # InteractiveCommand + interactive facade/API (D30)
    │                    # may use gui.rendering (D26); must NOT import gui.interactive (D27/D30)
    └── rendering        # GraphicsContext, ScreenTransform, color tables, board/debug renderers
```

Unlisted pipeline/support packages above are intentionally **headless-safe** and subject to the same Swing/AWT-UI bans as `board` / `autoroute`.

### 4.2 Moves in scope

| Move | When |
| --- | --- |
| Thin ratsnest/violations façades; compute remains `drc` | Phase 5 |
| Remove `Drawable` / paint from board; renderer in GUI | Phase 6 |
| Autorouter diagnostics → GUI/debug adapters | Phase 7 |
| `interactive` (+ flatten `commands`) → flat `gui.interactive` | Phase 8 |
| Session cluster → `gui.session` + facade/`InteractiveCommand` in session (D27/D30); views bootstrap | Phase 9 |
| `boardgraphics` → `gui.rendering` (+ `ScreenTransform`); session→rendering allowed | Phase 10 |
| `getGuiSession` → `getPrimarySession` in `SessionManager` | Phase 4 |
| Swing file chooser out of `RoutingJob` | Phase 4 |

### 4.3 Moves out of scope

- `gui.windows` / `menus` / `panels` / `components` reorganization
- Deep `board.model` / `autoroute.engine` splits
- `ObjectInfoPanel` → DTO/visitor rewrite
- New `ratsnest` / `connectivity` package
- `.frb` compatibility layer / fixture migration (no `.frb` fixtures exist)
- Headful GUI CI / Xvfb
- Forcing `GuiBoardManager` off `GraphicsContext` (D26 accepts the edge)

### 4.4 Phase 9 session cluster (explicit)

Move from temporary `gui.interactive` into `gui.session` at minimum:

- `GuiBoardManager`
- `InteractiveSettings` (remains `GuiSettings` subtype for merger priority)
- `ScreenMessages`
- `InteractiveActionThread`
- `AutorouterAndRouteOptimizerThread`

Leave concrete editor states (`*State`) in `gui.interactive`.

**Phase 9 must also** (D27 / D30):

1. Move / introduce the narrow interactive facade/API (name TBD — R19) **and** the `InteractiveCommand` interface (+ any state-handle/token types the session passes around) into **`gui.session`** (or `gui.session.api`).
2. Have concrete states in `gui.interactive` **implement** those session-owned interfaces (`gui.interactive → gui.session` only).
3. Assign **initial-state bootstrap/registration** to the **`gui` views** layer (views may see both packages; session must not need a concrete `*State` to start).
4. Ensure **no** `gui.session → gui.interactive` edge remains; ArchUnit `gui.**` slice-cycle check must pass.

Phase 1 inventory may add non-state session types to this list; do not invent extra subpackages for them. Phase 8 may temporarily flatten command *implementations* into `gui.interactive`, but the **`InteractiveCommand` interface** moves to session in Phase 9 (D11/D30). Do **not** keep a `commands` subpackage.

## 5. Completion criteria (measurable)

- Pipeline/support packages (`board`, `rules`, `autoroute`, `drc`, `geometry`, `datastructures`, `settings`, `logger`, `debug`, `util`, `io`, `core`, `management`, `api`) have no `gui.**`, no Swing, no AWT UI dependencies (geom whitelist only).
- No production sources under `app.freerouting.interactive` or `app.freerouting.boardgraphics`.
- `gui.interactive` = concrete editor states; `gui.session` = GUI session façade + owned command/facade APIs; `gui.rendering` = rendering.
- `gui.session` does not import any `gui.interactive` type (facade + `InteractiveCommand` owned by session — D27/D30); views bootstrap initial state.
- ArchUnit: `gui.**` slices are cycle-free.
- `gui.session → gui.rendering` is an allowed edge (D26).
- `BoardManager` has no nullable GUI settings API.
- Board/autorouter do not paint into `java.awt.Graphics`.
- `core.RoutingJob` has no Swing/AWT UI types.
- `SessionManager` uses `getPrimarySession` / `setPrimarySession`.
- Ratsnest/violation **compute** usable headlessly via `drc`.
- MVP GUI workflows green in `testGui` under forced headless, **EN + `hu`**, locator registry + `hu` resource check.
- Default `test` excludes `@Tag("gui")` and does not require a display.
- `testAll` includes `testGui`; `test` and `testSlow` do not.
- §12 freeze ledger empty (or only documented accepted permanent debt with owners).
- Golden-fixture clearance gates use **`DesignRulesChecker.getAllClearanceViolations()`** (D29).
- Phase 5/6: WIP vs **v2.3.0** parity green; Phase 0/1 captured v2.3.0 baseline metrics; AGENTS.md reflects D24.
- `docs/architecture.md` matches this plan.

## 6. Phase checklists

### Phase 0 — Baseline

- [ ] Record branch / working tree / HEAD.
- [ ] Run ArchUnit + `test` + quick routing fixture; capture results.
- [ ] Inventory class counts: `gui`, `interactive`, `boardgraphics`.
- [ ] **DSN fixture coverage map:** filename → owning test(s) → sole-coverage-for-a-path? (There are **no `.frb` fixtures** in the repo.)
- [ ] **Capture stable v2.3.0 golden metrics** on the agreed fixture matrix (completion / unrouted-net count, full-DRC violation count via `DesignRulesChecker.getAllClearanceViolations()`, SES sanity). Record numbers in branch notes and **§12.6**.
- [ ] Snapshot known leaks (board/autoroute paint, `RoutingJob` Swing, `BoardManager` GUI API, `SessionManager` GUI naming, `GuiBoardManager`→`GraphicsContext` / `InteractiveState`).
- [ ] Start AGENTS.md baseline-policy draft update toward D24 (land fully by Phase 12 at latest; prefer with Phase 0/1).

### Phase 1 — Inventory + ArchUnit freeze

- [ ] Full dependency inventory (pipeline ↔ GUI / AWT UI / Swing).
- [ ] Classify interactive/boardgraphics types (state vs session vs façade vs renderer).
- [ ] **Simple-name collision check** across `gui`, `interactive`, `boardgraphics`, **and** cross-package same-name types touched by moves (at minimum the three `CoordinateTransform` classes in `board` / `boardgraphics` / `io`; flattening `InteractiveCommand`).
- [ ] Confirm ratsnest compute call chain through `drc.NetIncompletes` / `AirLine`.
- [ ] List `ObjectInfoPanel.Printable` implementors (awareness only).
- [ ] Confirm MVP-workflow property bundles have complete `_hu` variants.
- [ ] Sketch the Phase 9 interactive facade surface (R19) and confirm home package = `gui.session` (D30): methods `GuiBoardManager` needs without importing any `gui.interactive` type.
- [ ] Plan views-layer bootstrap/registration of the initial interactive state (D30).
- [ ] **Inventory existing tests that construct Swing / need EDT**; plan deliberate `@Tag("gui")` retags.
- [ ] **Inventory worker-thread → Swing mutations**; assign removal to Phase 9 (or earlier if trivial).
- [ ] ArchUnit rules + §12 freezes (including planned temporary freeze only if facade lands mid-Phase-9).
- [ ] Add ArchUnit **`gui.**` slice-cycle** check (must stay green after Phase 9; may be frozen temporarily mid-phase only).
- [ ] Forbid pipeline/support → gui/interactive/boardgraphics/future gui.interactive|session|rendering; ban Swing + AWT UI; allow `java.awt.geom..`.
- [ ] Record freeze budget with owners/removal phases in §12.
- [ ] Adapt or stub WIP-vs-v2.3.0 compare tooling (R18); do not depend on v1.9 `compare-versions.ps1` as the primary gate.

### Phase 2 — Accessibility foundation (component-only, pure JDK)

- [ ] Document a11y contract (name/role/description/state/value; label-for; menu names).
- [ ] Implement **locator constants + shared registry** (D22); harness finds by locator, not translated label.
- [ ] Build harness: EDT execution, AccessibleContext walk, find by locator/role, invoke actions, assert states.
- [ ] Harness asserts `EventQueue.isDispatchThread()` for workflow mutations/actions.
- [ ] Failures include accessible path + role + locator.
- [ ] No private-field locators; no screen coordinates; no `setVisible` on top-level frames.
- [ ] Add `@Tag("gui")` and `testGui` task:
  - default `test` excludes `gui` (like `slow`)
  - `testSlow` remains slow-only (does **not** include `gui`)
  - `testAll` runs `test` + `testSlow` + `testGui` (D25)
  - `testGui` sets `systemProperty 'java.awt.headless', 'true'`
  - retag inventoried Swing tests from Phase 1 so coverage is intentional
  - path-filtered CI may invoke `testGui` alone on GUI-related paths
- [ ] Document component-only / forced-headless requirements and CI path filters (include legacy `interactive` / `boardgraphics` until moved).
- [ ] Product work: accessible names/roles + locator registration on MVP controls.
- [ ] ≥3 workflows: menu action, open/close parameter content, change setting, select layer, read status, cancel/stop route, open inspect/list.
- [ ] Sibling duplicate/empty accessible-name and locator checks.
- [ ] Run MVP workflows in English and Hungarian (`hu`, D19); locators stable across both.
- [ ] Add/extend a **Hungarian resource-parity check** for bundles touched by MVP workflows (document in §7).

### Phase 3 — Headless board contracts

- [ ] Headless board manager API without GUI methods.
- [ ] GUI session contract separate from headless manager.
- [ ] Remove null-based `getInteractiveSettings()` / `isInteractiveModeSupported()` from shared headless API.
- [ ] Prefer moving `initializeManualTraceHalfWidths` to GUI-session-only (R10).
- [ ] Preserve InteractiveSettings invariants (reset, live snapshot, merger priority).
- [ ] Update contract tests accordingly.

### Phase 4 — Core / management neutralization

- [ ] Remove Swing file chooser / AWT UI types from `RoutingJob`; GUI owns picking.
- [ ] Rename `SessionManager.getGuiSession` / `setGuiSession` → `getPrimarySession` / `setPrimarySession` (management UUID session only; **not** `gui.session`).
- [ ] Ensure analytics/API do not depend on GUI session types.
- [ ] Reduce circular loader↔manager delegation where practical.

### Phase 5 — Compute vs presentation

- [ ] Thin `RatsNest` to GUI façade over `drc` incompletes (optional rename R9).
- [ ] Thin `ClearanceViolations` similarly over `drc`.
- [ ] Keep `board.ObjectInfoPanel` as AWT-free interface; GUI continues to implement (accepted debt in §12).
- [ ] Headless tests: incompletes/violations without GUI classes.
- [ ] A11y tests for incompletes/violations lists/counts.
- [ ] **Exit gate:** golden fixtures; clearance delta **0** vs Phase 0/1 v2.3.0 baseline using **`DesignRulesChecker.getAllClearanceViolations()`** (D29); WIP-vs-v2.3.0 compare (D24/D28).

### Phase 6 — Invert board rendering (highest risk)

Land as **independently revertible commits** on the long-lived branch:

1. [ ] Add neutral accessors for geometry/layer/net/type/visibility/selection metadata (no paint removal yet).
2. [ ] Stand up GUI renderer + **early** offscreen `BufferedImage` smoke for major item types (must exist before mass paint deletion).
3. [ ] Remove `Drawable` / `Graphics` / `GraphicsContext` / AWT `Color` paint APIs from board (per family if needed).
4. [ ] Move traversal + draw priority fully into GUI renderer.
5. [ ] Headless load→route→DRC→SES without renderer init.
6. [ ] No routing mutation behavior changes.

**Exit gate:** full-DRC clearance delta **0** (D29); no completion regression vs **v2.3.0** (D24/D28); offscreen renderer smokes green.

### Phase 7 — Autorouter diagnostics

- [ ] Replace `draw(Graphics, …)` with neutral snapshots/events or GUI adapters.
- [ ] Diagnostics opt-in; logging remains headless path.
- [ ] ArchUnit: no new AWT UI parameters in `autoroute`.
- [ ] **Cheap exit gate:** golden-fixture smoke = full DRC **and** completion/unrouted-net parity vs Phase 0 goldens (D28) — no full v2.3.0 version compare.

### Phase 8 — Move to `gui.interactive` (flat)

- [ ] Resolve any simple-name collisions found in Phase 1 before/during move.
- [ ] Move remaining interactive production + tests flat into `gui.interactive`.
- [ ] **Flatten `interactive.commands` types into `gui.interactive` sources** (no `gui.interactive.commands` subpackage) (D11) — interface moves to `gui.session` in Phase 9 (D30).
- [ ] Temporarily includes `GuiBoardManager` and session cluster.
- [ ] Update i18n FQCNs / resources / ArchUnit / docs.
- [ ] No `.frb` compat shims.
- [ ] Run interactive tests, ArchUnit, i18n parity (+ `hu` check), MVP a11y, spotlessCheck, checkstyle.
- [ ] **Cheap exit gate:** golden-fixture smoke = full DRC **and** completion/unrouted-net parity vs Phase 0 goldens (D28).

### Phase 9 — Extract `gui.session`

- [ ] Extract §4.4 session cluster to `gui.session` (D20); keep `InteractiveSettings` name (D21).
- [ ] Move/introduce facade + `InteractiveCommand` (+ handles) into `gui.session` / `gui.session.api` (D30); pick facade type name (R19).
- [ ] Concrete states in `gui.interactive` implement session-owned interfaces; **views bootstrap** initial state.
- [ ] **Remove all `gui.session → gui.interactive` imports** (not only concrete `*State`) (D27/D30).
- [ ] Confirm `gui.session → gui.rendering` remains the allowed D26 edge (`GraphicsContext` ownership) — no forced decoupling.
- [ ] Temporary §12 freeze on `gui.session → gui.interactive` only while facade lands; **must be gone by phase exit**; `gui.**` slice-cycle check green.
- [ ] Ports for load / route start-stop / progress / board replace / settings.
- [ ] Eliminate inventoried worker→Swing call sites; EDT-only Swing mutation.
- [ ] Confirm `getPrimarySession` / `setPrimarySession` callers remain correct (still management API).
- [ ] A11y workflows still pass component-only under forced headless.

### Phase 10 — Move to `gui.rendering`

- [ ] Move `boardgraphics` → `gui.rendering`.
- [ ] Rename graphics `CoordinateTransform` → `ScreenTransform` (D18).
- [ ] Confirm **pipeline** has zero imports of rendering package (session→rendering is allowed — D26).
- [ ] Offscreen renderer tests green; headless routing green.

### Phase 11 — Accessibility expansion + path-filtered CI

- [ ] Expand coverage across major windows/menus (still component-only).
- [ ] State-change tests: layer, mode, enablement, progress, visibility, violation state.
- [ ] Keyboard/menu alternatives + inspect lists for critical canvas actions.
- [ ] Switch CI path filters to final `gui/interactive|session|rendering` (+ remaining `gui/**` as needed).
- [ ] Default `test` never requires display; `testGui` always headless; `testAll` includes `testGui`.

### Phase 12 — Final cleanup

- [ ] Remove transitional APIs / freezes / stale tests.
- [ ] Promote ArchUnit rules to strict; §12 freeze table empty except accepted permanent debt.
- [ ] **Finalize AGENTS.md** baseline policy: primary routing baseline is stable **v2.3.0**; v1.9/`src_v19` remains historical reference / optional deep dive, not the required parity gate (D24).
- [ ] Update `docs/architecture.md`, developer GUI-test docs.
- [ ] Record accepted debt: `ObjectInfoPanel` shape; DRC includes incompletes; session→rendering (D26).

## 7. Validation matrix

| Command | Purpose |
| --- | --- |
| `.\gradlew.bat test` | Fast tests (excludes `slow`, `gui`) |
| `.\gradlew.bat testGui` | `@Tag("gui")` component-only a11y tests (**forced headless**) |
| `.\gradlew.bat test --tests "app.freerouting.architecture.ModuleBoundariesArchTest"` | Boundaries |
| `.\gradlew.bat test --tests "app.freerouting.io.SpecctraPackageArchTest"` | Parser encapsulation |
| `.\gradlew.bat test --tests "app.freerouting.i18n.EnglishPropertiesParityTest"` | English i18n ownership |
| Hungarian resource-parity check (new / extended) | `hu` bundles for MVP + moved packages (D19) |
| `.\gradlew.bat test --tests "app.freerouting.fixtures.Dac2020Bm01RoutingTest"` | Quick routing smoke |
| `.\gradlew.bat spotlessCheck` | Formatting gate (do **not** auto-run `spotlessApply`) |
| `.\gradlew.bat checkstyleMain checkstyleTest` | Style gates |
| `python scripts/i18n/extract-context.py --check` | i18n context sync after package moves |
| `.\gradlew.bat check` | Full verification (does **not** imply `testGui` unless wired later) |
| `.\gradlew.bat testAll` | Fast + slow + **gui** (`test` → `testSlow` → `testGui`) |
| WIP vs **v2.3.0** compare script/workflow | Full routing parity at Phase 5/6 gates only (D24/D28) |
| Golden-fixture full DRC + completion/unrouted-net | Clearance delta **0** + completion parity at Phase 5/6; **cheap smoke** at Phase 7/8 (D28/D29) |

**Routing checkpoints:** golden fixture matrix; **full-DRC** clearance-violation delta **0**; completion not regressed vs stable **v2.3.0** at Phase 5/6; SES validity. Phases 7–8: cheap smoke = full DRC **+** completion/unrouted-net vs Phase 0 goldens. v1.9 is not the primary parity gate.

**GUI checkpoints:** locator discovery/actions; EDT assertion; no leaked windows/threads; forced headless; **EN + hu** (+ `hu` resource parity).

**Note:** Existing `scripts/tests/compare-versions.ps1` still targets v1.9. Phase 0/1 capture v2.3.0 golden metrics; Phase 5 setup adapts tooling to WIP-vs-v2.3.0 (R18). Do not treat v1.9 compare as required for this initiative’s exit gates.

## 8. Long-lived branch checkpoints

1. Phase 0–1 inventory + ArchUnit freezes + **v2.3.0 golden metrics** + §12 ledger  
2. `testGui` (forced headless) + harness + path-filter stub + Swing-test retags  
3. MVP locators + ≥3 workflows (EN + `hu`) + `hu` resource check  
4. Headless `BoardManager` split  
5. `RoutingJob` Swing removal + `getPrimarySession` / `setPrimarySession`  
6. Ratsnest/violations façade thinning + Phase 5 full parity vs **v2.3.0**  
7. Board paint inversion as revertible commits + Phase 6 full parity vs **v2.3.0**  
8. Autorouter diagnostic inversion + cheap full-DRC **+ completion** smoke  
9. Flat move to `gui.interactive` (flatten command impls) + cheap full-DRC **+ completion** smoke  
10. Extract `gui.session` + session-owned facade/`InteractiveCommand`; views bootstrap (D27/D30)  
11. Move to `gui.rendering` + `ScreenTransform` (session→rendering allowed)  
12. A11y expansion (EN + `hu`), final CI filters, docs, AGENTS.md D24, strict ArchUnit  

## 9. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Paint inversion changes routing behavior | Revertible commits; early offscreen smoke; full-DRC + **v2.3.0** parity at Phase 5/6 |
| Incomplete `BoardStatistics` clearance count | Gates use `DesignRulesChecker.getAllClearanceViolations()` (D29) |
| Tagging `@Tag("gui")` drops default CI coverage | Phase 1 Swing-test inventory + deliberate retags |
| Headless assumed but not forced | `testGui` sets `java.awt.headless=true` |
| Worker→Swing races | Phase 1 inventory; Phase 9 elimination; harness EDT asserts |
| i18n / `hu` gaps | English parity + Hungarian resource check; extract-context |
| Stale `.frb` fixture story | No `.frb` fixtures exist; keep save/load code only |
| Simple-name collisions on flat moves | Phase 1 collision inventory including `InteractiveCommand` + three `CoordinateTransform`s |
| Session still imports any `gui.interactive` type after Phase 9 | Session owns facade + `InteractiveCommand` (D30); views bootstrap; no permanent freeze of that edge |
| `gui.session ⇄ gui.interactive` package cycle | D30 + ArchUnit `gui.**` slice-cycle check |
| Session→rendering coupling | Explicitly allowed (D26) |
| ArchUnit freeze pile-up | §12 owner + removal phase |
| `gui.interactive` temporary god-package | Phase 9 mandatory |
| Confusing `getPrimarySession` with `gui.session` | Explicit docs in §1.2 / Phase 4 |
| v2.3.0 tooling not ready at Phase 5 | Capture metrics in Phase 0/1; adapt compare script early (R18) |
| Long branch drift | Regular master merge/rebase; keep ArchUnit green |
| geom whitelist creep | UI deny-list + review new awt imports |

## 10. Remaining decision points

D1–D30 are locked. Operational leftovers only:

| ID | Topic | Default / action | When |
| --- | --- | --- | --- |
| **R8** | Temporary ArchUnit freezes allowed? | **Yes**, exact freezes with §12 owners | Phase 1 |
| **R9** | Rename GUI `RatsNest` façade after thinning? | Optional; keep name if unclear | Phase 5 |
| **R10** | `initializeManualTraceHalfWidths` on headless manager? | Prefer **GUI-session-only** | Phase 3 |
| **R11** | Exact GitHub Actions path-filter globs | Documented GUI/legacy paths; switch after Phase 8/10 | Phase 2 / 11 |
| **R12** | `ObjectInfoPanel` DTO follow-up | **Out of scope**; §12 accepted debt | Future |
| **R13** | Eliminate `java.awt.geom` from pipeline | **Out of scope** | Future |
| **R14** | Legacy `interactive.Settings` vs `InteractiveSettings` | Inventory Phase 1; merge/delete Phase 8/9 | Phase 1 / 8 / 9 |
| **R18** | How to obtain/run stable v2.3.0 for parity | Prefer released v2.3.0 executable/artifact vs `v2.3.0` git tag checkout | Phase 0/1 tooling |
| **R19** | Interactive facade type name | e.g. `InteractiveController` / `EditorStateHost` — pick in Phase 1 sketch | Phase 1 / 9 |

## 11. Final sign-off

- [ ] Phases 0–12 complete with recorded checkpoint results.
- [ ] §5 completion criteria satisfied.
- [ ] No `.frb` compatibility shims (and no `.frb` fixture migration work needed).
- [ ] Component-only pure-JDK a11y MVP workflows green in forced-headless `testGui` (EN + `hu`).
- [ ] ArchUnit strict; §12 freezes cleared except accepted permanent debt (incl. D26).
- [ ] `gui.session` has no `gui.interactive` imports (D27/D30); `gui.**` slices cycle-free.
- [ ] Views own initial-state bootstrap/registration (D30).
- [ ] `spotlessCheck`, checkstyle, i18n extract-context + `hu` checks green after moves.
- [ ] `.\gradlew.bat check` passes.
- [ ] `.\gradlew.bat testAll` passes (`test` + `testSlow` + `testGui`).
- [ ] AGENTS.md reflects v2.3.0 primary baseline (D24).
- [ ] Deferred R8–R14 / R18–R19 resolved or explicitly accepted with defaults.

## 12. Boundary debt ledger (live)

> Replaces `docs/issues/soc-architecture-boundary-debt-tracker.md` and the missing `Architecture-boundary-debt-tracker.md` referenced by `AGENTS.md`.
>
> Update this section whenever ArchUnit freezes are added, reduced, or promoted to strict.

### 12.1 Operating rules

1. New boundary rules that currently fail may be added as **frozen** only with an exact violation baseline, an owner, and a target removal phase.
2. When a frozen rule reaches zero violations, promote it to **strict** in the same checkpoint as the last fix.
3. Do not relax strict rules to hide regressions.
4. Accepted permanent debt (not planned to remove in this initiative) must be listed in §12.3 with rationale.

### 12.2 Historical context (from retired tracker, 2026-06)

These older items were marked FIXED before this initiative and are retained only as history — **re-verify in Phase 1** rather than trusting the 2026-06 status:

| ID | Topic | Notes |
| --- | --- | --- |
| H1 | `FileFormat` moved out of GUI for api/management/core | Re-verify imports |
| H2 | `RoutingJob` no longer takes `GuiBoardManager` for rules read | Re-verify; Swing file chooser still present (Phase 4) |
| H3 | Interactive leakage reduced; `HeadlessBoardManager` in management | Re-verify; `RatsNest` façade still in interactive |
| H4 | Specctra parser encapsulation | Covered by `SpecctraPackageArchTest` |

### 12.3 Accepted permanent debt (this initiative)

| ID | Debt | Rationale | Owner |
| --- | --- | --- | --- |
| A1 | `board.ObjectInfoPanel` remains a presentation-shaped writer API | Explicit D14; DTO rewrite out of scope | GUI SoC initiative |
| A2 | Incomplete-connection compute lives under `drc` (name broader than clearances) | Explicit D13; document in architecture glossary | GUI SoC initiative |
| A3 | `gui.session` may depend on `gui.rendering` (`GraphicsContext` on `GuiBoardManager`) | Explicit D26; full view-owned graphics out of scope | GUI SoC initiative |

### 12.4 Active ArchUnit freezes (fill in Phase 1)

| Freeze ID | Rule | Violation count (baseline) | Removal phase | Owner | Status |
| --- | --- | --- | --- | --- | --- |
| *(none yet — populate during Phase 1)* | | | | | |

### 12.5 Validation

```powershell
Set-Location "C:\Work\freerouting"
.\gradlew.bat test --tests "app.freerouting.architecture.ModuleBoundariesArchTest" --tests "app.freerouting.io.SpecctraPackageArchTest"
```

### 12.6 v2.3.0 golden metrics (fill in Phase 0/1)

Record baseline numbers here (and/or in branch notes). Cheap Phase 7/8 smokes and Phase 5/6 full compares assert against these.

| Fixture | Metric | v2.3.0 value | Notes |
| --- | --- | --- | --- |
| *(populate during Phase 0/1)* | Completion / unrouted nets | | |
| | Full-DRC violations (`DesignRulesChecker.getAllClearanceViolations()`) | | |
| | SES sanity | | |
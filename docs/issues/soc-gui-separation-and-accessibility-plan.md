# GUI Separation and Accessibility Migration Plan

> Status: **In implementation** — Phases 0–5 complete (baseline, inventory + ArchUnit freeze, accessibility foundation, headless contracts, core/management neutralization, compute/presentation split); Phase 6 (board rendering inversion) is next. D1–D30 locked; M1=A / M4=B incorporated; execution staffing plan in §13 (added 2026-08-11).
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

- [x] Record branch / working tree / HEAD.
- [x] Run ArchUnit + `test` + quick routing fixture; capture results.
- [x] Inventory class counts: `gui`, `interactive`, `boardgraphics`.
- [x] **DSN fixture coverage map:** filename → owning test(s) → sole-coverage-for-a-path? (There are **no `.frb` fixtures** in the repo.)
- [x] **Capture stable v2.3.0 golden metrics** on the agreed fixture matrix (completion / unrouted-net count, full-DRC violation count via `DesignRulesChecker.getAllClearanceViolations()`, SES sanity). Record numbers in branch notes and **§12.6**.
- [x] Snapshot known leaks (board/autoroute paint, `RoutingJob` Swing, `BoardManager` GUI API, `SessionManager` GUI naming, `GuiBoardManager`→`GraphicsContext` / `InteractiveState`).
- [x] Start AGENTS.md baseline-policy draft update toward D24 (land fully by Phase 12 at latest; prefer with Phase 0/1).

### Phase 1 — Inventory + ArchUnit freeze

> **Phase 1 COMPLETE (2026-08-12).** Inventory + collision check + type classification + ArchUnit freeze layer
> (F1/F2/F3 frozen, R4/R5 strict) + §12.4 ledger + facade sketch (R19) + views-bootstrap plan all landed.
> Exit gate green: `ModuleBoundariesArchTest` + `SpecctraPackageArchTest` (`BUILD SUCCESSFUL`). Reviewed and
> approved (Flash review 2026-08-12). Details in `logs/phase1/branch-notes.md` + `logs/phase1/facade-sketch.md`.

- [x] Full dependency inventory (pipeline ↔ GUI / AWT UI / Swing).
- [x] Classify interactive/boardgraphics types (state vs session vs façade vs renderer).
- [x] **Simple-name collision check** across `gui`, `interactive`, `boardgraphics`, **and** cross-package same-name types touched by moves (at minimum the three `CoordinateTransform` classes in `board` / `boardgraphics` / `io`; flattening `InteractiveCommand`).
- [x] Confirm ratsnest compute call chain through `drc.NetIncompletes` / `AirLine`.
- [x] List `ObjectInfoPanel.Printable` implementers (awareness only).
- [x] Confirm MVP-workflow property bundles have complete `_hu` variants.
- [x] Sketch the Phase 9 interactive facade surface (R19) and confirm home package = `gui.session` (D30): methods `GuiBoardManager` needs without importing any `gui.interactive` type.
- [x] Plan views-layer bootstrap/registration of the initial interactive state (D30).
- [x] **Inventory existing tests that construct Swing / need EDT**; plan deliberate `@Tag("gui")` retags.
- [x] **Inventory worker-thread → Swing mutations**; assign removal to Phase 9 (or earlier if trivial).
- [x] ArchUnit rules + §12 freezes (including planned temporary freeze only if facade lands mid-Phase-9).
- [x] Add ArchUnit **`gui.**` slice-cycle** check (must stay green after Phase 9; may be frozen temporarily mid-phase only).
- [x] Forbid pipeline/support → gui/interactive/boardgraphics/future gui.interactive|session|rendering; ban Swing + AWT UI; allow `java.awt.geom..`.
- [x] Record freeze budget with owners/removal phases in §12.
- [x] Adapt or stub WIP-vs-v2.3.0 compare tooling (R18); do not depend on v1.9 `compare-versions.ps1` as the primary gate.

### Phase 2 — Accessibility foundation (component-only, pure JDK)

- [x] Document a11y contract (name/role/description/state/value; label-for; menu names).
- [x] Implement **locator constants + shared registry** (D22); harness finds by locator, not translated label.
- [x] Build harness: EDT execution, AccessibleContext walk, find by locator/role, invoke actions, assert states.
- [x] Harness asserts `EventQueue.isDispatchThread()` for workflow mutations/actions.
- [x] Failures include accessible path + role + locator.
- [x] No private-field locators; no screen coordinates; no `setVisible` on top-level frames.
- [x] Add `@Tag("gui")` and `testGui` task:
  - default `test` excludes `gui` (like `slow`)
  - `testSlow` remains slow-only (does **not** include `gui`)
  - `testAll` runs `test` + `testSlow` + `testGui` (D25)
  - `testGui` sets `systemProperty 'java.awt.headless', 'true'`
  - retag inventoried Swing tests from Phase 1 so coverage is intentional
  - path-filtered CI may invoke `testGui` alone on GUI-related paths
- [x] Document component-only / forced-headless requirements and CI path filters (include legacy `interactive` / `boardgraphics` until moved).
- [x] Product work: accessible names/roles + locator registration on MVP controls.
- [x] ≥3 workflows: menu action, open/close parameter content, change setting, select layer, read status, cancel/stop route, open inspect/list.
- [x] Sibling duplicate/empty accessible-name and locator checks.
- [x] Run MVP workflows in English and Hungarian (`hu`, D19); locators stable across both.
- [x] Add/extend a **Hungarian resource-parity check** for bundles touched by MVP workflows (document in §7).

### Phase 3 — Headless board contracts

- [x] Headless board manager API without GUI methods.
- [x] GUI session contract separate from headless manager.
- [x] Remove null-based `getInteractiveSettings()` / `isInteractiveModeSupported()` from shared headless API.
- [x] Prefer moving `initializeManualTraceHalfWidths` to GUI-session-only (R10).
- [x] Preserve InteractiveSettings invariants (reset, live snapshot, merger priority).
- [x] Update contract tests accordingly.

### Phase 4 — Core / management neutralization

- [x] Remove Swing file chooser / AWT UI types from `RoutingJob`; GUI owns picking. (`showOpenDialog` moved → `gui.BoardMenuFile`; F1 −11, F2 −2 frozen violations removed.)
- [x] Rename `SessionManager.getGuiSession` / `setGuiSession` → `getPrimarySession` / `setPrimarySession` (management UUID session only; **not** `gui.session`).
- [x] Ensure analytics/API do not depend on GUI session types. (Verified clean — no `gui`/`interactive`/`boardgraphics` imports in `management`/`api`.)
- [x] Reduce circular loader↔manager delegation where practical. (Verified **no circular delegation** exists — `BoardLoader` is a standalone static helper (BoardLoader → HeadlessBoardManager, unidirectional); nothing trivially safe to reduce.)

### Phase 5 — Compute vs presentation

- [x] Thin `RatsNest` to GUI façade over `drc` incompletes (optional rename R9; name retained).
- [x] Thin `ClearanceViolations` similarly over `drc`; severity ordering and smallest-clearance aggregation now live in headless-safe `drc.ClearanceViolation`.
- [x] Keep `board.ObjectInfoPanel` as AWT-free interface; GUI continues to implement (accepted debt in §12).
- [x] Headless tests: incompletes/violations without GUI classes (`RatsnestClearanceHeadlessTest`).
- [x] A11y tests for incompletes/violations lists/counts (`ViolationsIncompletesListA11yTest`), using component-only `JList` coverage under forced headless.
- [x] **Exit gate (2026-08-12):** golden-fixture completion/unrouted and full-DRC metrics remain at the §12.6 baseline; the recorded WIP-vs-v2.3.0 comparison is green on all 19 comparable fixtures (CM5_MINIMA_3 timed out in both builds); targeted headless/a11y tests, `check`, `testGui`, formatting, checkstyle, rewrite, and i18n gates are green. Clearance comparison uses **`DesignRulesChecker.getAllClearanceViolations()`** (D29).

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
| `.\\gradlew.bat test --tests "app.freerouting.i18n.HungarianResourceParityCheckTest"` | Hungarian resource parity for MVP bundles (D19) — `_hu` covers `_en` keys |
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

**Note:** Existing `scripts/tests/compare-versions.ps1` still targets v1.9. Phase 0/1 captured the
v2.3.0 golden metrics, and the Phase 5 exit re-verified the recorded current-branch vs v2.3.0
comparison on the 19 comparable fixtures. Do not treat the v1.9 compare as required for this
initiative’s exit gates.

## 8. Long-lived branch checkpoints

1. Phase 0–1 inventory + ArchUnit freezes + **v2.3.0 golden metrics** + §12 ledger
2. `testGui` (forced headless) + harness + path-filter stub + Swing-test retags
3. MVP locators + ≥3 workflows (EN + `hu`) + `hu` resource check
4. Headless `BoardManager` split
5. `RoutingJob` Swing removal + `getPrimarySession` / `setPrimarySession`
6. Ratsnest/violations façade thinning + Phase 5 full parity vs **v2.3.0** ✅
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
| **F1** | pipeline/support → `javax.swing..` (ModuleBoundariesArchTest.pipelineMustNotDependOnSwing) | 16 (was 27; −11 Phase 4) | Phase 4 (datastructures.FileFilter; RoutingJob file chooser ✅) + Phase 12 (util.TextManager, io.specctra.parser.SessionToEagle) | GUI SoC initiative | frozen, green |
| **F2** | pipeline/support → `java.awt..` excluding `java.awt.geom..` (ModuleBoundariesArchTest.pipelineMustNotDependOnAwtUiTypes) | 93 (was 95; −2 Phase 4) | Phase 4 (RoutingJob file chooser ✅) + Phase 6 (board paint) + Phase 7 (autoroute diagnostics) + Phase 12 (util.TextManager fonts) | GUI SoC initiative | frozen, green |
| **F3** | board/autoroute → `app.freerouting.boardgraphics..` (ModuleBoundariesArchTest.boardAndAutorouteMustNotDependOnBoardgraphics) | 145 | Phase 6 + Phase 10 (rendering inversion → gui.rendering) | GUI SoC initiative | frozen, green |

> Added Phase 1 (2026-08-12). Frozen store: `src/test/resources/archunit_store/` (3 rule files + `stored.rules`);
> `archunit.properties` keeps `allowStoreCreation=false` / `allowStoreUpdate=true`. Total frozen debt: **254**
> violations (was 267; −13 after Phase 4 RoutingJob file-chooser removal). Strict (non-frozen) rules added in the same change: **R4** `guiSlicesMustBeFreeOfCycles`
> (`allowEmptyShould(true)` — green until gui subpackages exist, must stay green after Phase 9) and **R5**
> `pipelineMustNotDependOnGui` (green; closes the io/util gap). Baselines verified against the 2026-08-12 run
> (`BUILD SUCCESSFUL in 38s`, gate = ModuleBoundariesArchTest + SpecctraPackageArchTest).

### 12.5 Validation

```powershell
Set-Location "C:\Work\freerouting"
.\gradlew.bat test --tests "app.freerouting.architecture.ModuleBoundariesArchTest" --tests "app.freerouting.io.SpecctraPackageArchTest"
```

### 12.6 Golden metrics & baseline (v2.3.0 reference + current build = baseline)

Record baseline numbers here (and/or in branch notes). Cheap Phase 7/8 smokes and Phase 5/6 full compares assert against these.

> **Phase 0 (2026-08-12):** Golden metrics captured via `scripts/benchmark/run-benchmarks.ps1` on the
> 20-fixture golden matrix. **current (branch `soc-gui-separation-and-accessibility`) ≡ v2.3.0** on routing
> quality (avg score 915.1 both; identical unrouted + full-DRC violations on all 19 comparable fixtures),
> with an improvement on `DAC2020_bm01` (current DRC 0 vs 2.3.0 2). `CM5_MINIMA_3` times out (~59 min) for
> **both** builds → N/A. Full-DRC numbers below are the post-route re-check runs of the current binary on
> each `.ses` output (aligns with D29 `DesignRulesChecker.getAllClearanceViolations()`).

| Fixture | v2.3.0 unrouted | current unrouted | v2.3.0 DRC viol | current DRC viol | Notes |
| --- | --- | --- | --- | --- | --- |
| DAC2020_bm01 | 4 | 4 | 2 | 0 | current fewer DRC violations |
| DAC2020_bm02 | 0 | 0 | 4 | 4 | identical |
| DAC2020_bm04 | 3 | 3 | 0 | 0 | identical |
| DAC2020_bm05 | 23 | 23 | 0 | 0 | identical |
| DAC2020_bm06 | 2 | 2 | 8 | 8 | identical |
| DAC2020_bm07 | 3 | 3 | 0 | 0 | identical |
| DAC2020_bm08 | 0 | 0 | 1 | 1 | identical |
| DAC2020_bm09 | 1 | 1 | 0 | 0 | identical |
| DAC2020_bm10 | 0 | 0 | 8 | 8 | identical |
| DAC2020_bm11 | 2 | 2 | 0 | 0 | identical |
| complex_hierarchy | 10 | 10 | 0 | 0 | identical |
| ecc83-pp | 0 | 0 | 0 | 0 | identical |
| ecc83-pp_v2 | 0 | 0 | 24 | 24 | identical |
| interf_u | 0 | 0 | 62 | 62 | identical |
| multichannel_mixer | 160 | 160 | 0 | 0 | identical |
| multichannel_mixer-unrouted | 128 | 128 | 612 | 612 | identical |
| pic_programmer | 0 | 0 | 1 | 1 | identical |
| sonde xilinx | 0 | 0 | 0 | 0 | identical |
| StickHub | 2 | 2 | 5 | 5 | identical |
| CM5_MINIMA_3 | — | — | — | — | **timeout both (~59 min)**; N/A |

SES sanity: all 19 comparable runs produced a `.ses` output that the current binary DRC-checked without
load errors (2 warn/err per run is the normal baseline). Completion-unrouted parity + full-DRC delta = **0**
vs v2.3.0 on all comparable fixtures (D28/D29 gates satisfied at the Phase 0/1 baseline).

> **Phase 5 exit (2026-08-12):** The compute/presentation split preserved the §12.6 completion and
> full-DRC baseline. `RatsnestClearanceHeadlessTest` verified incompletes and clearance computation
> without GUI façades; `ViolationsIncompletesListA11yTest` verified localized, locator-based list/count
> coverage in forced-headless mode. The current branch remains parity-green against v2.3.0 on all
> 19 comparable fixtures; `CM5_MINIMA_3` remains N/A because both builds timed out.

> **Baseline decision (2026-08-12):** the **current build** (branch `soc-gui-separation-and-accessibility`)
> is now the **authoritative routing baseline** for this initiative, superseding v2.3.0 as the parity
> reference (D24). Rationale: current ≡ v2.3.0 on all comparable fixtures and is strictly better on
> `DAC2020_bm01` (full-DRC 0 vs 2). Phase 5/6 exit gates and Phase 7/8 cheap smokes therefore assert
> **no regression below the current column above** (completion/unrouted + full-DRC). v2.3.0 remains the
> secondary reference. AGENTS.md baseline policy (D24 draft) to be updated to match on sign-off.

## 13. Execution staffing plan (models, cost, kickoff prompts)

> Added 2026-08-11, before implementation start. Benchmark/pricing snapshot as of that date — re-verify model standings before each major phase; the model market moves faster than this plan.
>
> This section is **operational guidance, not a locked decision set**. D1–D30 remain the only locked decisions. If a named model version is unavailable when a phase starts, substitute per §13.4.

### 13.1 Model evaluation findings (2026-08-11 snapshot)

Sources: arena.ai Code Arena / WebDev leaderboard (567K votes, 2026-08-11), cursor.com/evals (CursorBench 3.2 — agentic, ambiguous multi-file tasks from real sessions; v3.2 added instruction-following + advanced tool-use problems), vendor docs (Moonshot, Z.AI, DeepSeek). llm-stats.com was inaccessible (bot check) — no data from it.

| Model | Arena WebDev | CursorBench 3.2 (agentic) | Price $/M (in/out) | Context |
| --- | --- | --- | --- | --- |
| **Kimi K3** | **#2 overall — 1674** (above claude-opus-5-high) | Max 60.8 % / High 59.7 % | $3 / $15 | 1M |
| **GLM-5.2** | #7 — 1588 | Max 55.0 % / High 51.5 % | $1.40 / $4.40 | 1M |
| **DeepSeek V4 Flash** | #8 — 1585 (preliminary; statistically tied with GLM-5.2) | **not evaluated** (no public agentic evidence) | $0.14 / $0.28 | 1M |

Findings:

1. K3 leads GLM-5.2 by ~5 pts on the benchmark closest to this plan's demands (multi-file agentic work with instruction-following + tool use). K3 is the right primary.
2. Reasoning effort scales scores materially within each family (K3: 50.5 % Low → 60.8 % Max; GLM-5.2: 51.5 % High → 55.0 % Max). Use xhigh/extra-high for all design and gate-sensitive phases.
3. Flash ties GLM-5.2 on the arena at ~1/15th the cost, but has no CursorBench/agentic track record → mechanical, fully gated phases only; never Phase 6 or 9.
4. Neither benchmark measures Java/EDA refactoring or 13-session constraint retention → Phase 1 doubles as a controlled trial (§13.4 rule 6).

### 13.2 Cost model and totals

Unit: **1 task-equivalent (TE)** ≈ 800K input + 40K output tokens ≈ one substantial agentic task with build/test loops (anchored to CursorBench: K3 Max ≈ 38K completion tokens ≈ $2.70/task).

Cost per TE at list prices (no caching): **K3 $3.00 · GLM-5.2 $1.30 · Flash $0.12**.

| Phase | TE (range) | Dominant work |
| --- | --- | --- |
| 0 Baseline | 4 (3–6) | test runs, golden-metric capture, inventories |
| 1 Inventory + ArchUnit freeze | 8 (6–12) | whole-repo analysis, freeze baselines, facade sketch |
| 2 A11y foundation | 12 (8–16) | new harness code, Gradle wiring, 3+ workflows, EN+hu |
| 3 Headless contracts | 5 (3–7) | BoardManager API split |
| 4 Core/mgmt neutralization | 3 (2–4) | RoutingJob Swing removal, SessionManager rename |
| 5 Compute vs presentation | 6 (4–9) | façades + first full v2.3.0 parity gate |
| 6 Rendering inversion | 15 (10–22) | highest risk; revertible commits; full parity gate |
| 7 Autorouter diagnostics | 5 (3–7) | snapshot/event inversion; cheap smoke |
| 8 Move to gui.interactive | 8 (6–12) | mechanical, voluminous; FQCN/i18n updates |
| 9 Extract gui.session | 8 (6–12) | facade extraction; D27/D30 verification |
| 10 Move to gui.rendering | 3 (2–5) | mechanical; ScreenTransform rename |
| 11 A11y expansion + CI | 5 (3–7) | workflow replication; path filters |
| 12 Final cleanup + docs | 3 (2–4) | strict ArchUnit, AGENTS.md D24, docs |
| **Total** | **85 (60–120)** | |

Scenario totals (expected / range):

| Scenario | Expected | Range | Verdict |
| --- | --- | --- | --- |
| All Kimi K3 | $255 | $180–360 | Simplest; premium |
| All GLM-5.2 | $110 | $78–156 | Risky on Phases 6/9 (weakest agentic evidence at the hardest work) |
| All DeepSeek V4 Flash | $11 | $7–15 | Reference only — do not use; no agentic track record |
| **Hybrid (§13.3)** | **$190** | **$135–270** | **Recommended: K3 on critical path, GLM medium phases, Flash mechanical** |

Notes:

- Prompt caching (long sessions on one repo) typically cuts effective input cost 30–50 %; totals above are conservative list-price figures.
- Verification-heavy phases can exceed estimates; a failed parity-gate investigation adds 2–6 TE.
- Even the premium scenario is cheap relative to the cost of one clearance-violation regression reaching a release. Optimize for gate discipline, not token spend.

### 13.3 Per-phase assignments and kickoff prompts

| Phase | Model | Est. cost |
| --- | --- | --- |
| 0 | GLM-5.2 (xhigh) | $5 |
| 1 | **Kimi K3 (extra high)**; GLM-5.2 reviews freeze table + facade sketch | $24 |
| 2 | K3: harness + Gradle wiring + workflow #1 (8 TE); GLM-5.2: workflows #2–3 + EN/hu + parity check (4 TE) | $29 |
| 3 | **Kimi K3 (extra high)** | $15 |
| 4 | GLM-5.2 (xhigh) | $4 |
| 5 | **Kimi K3 (extra high)** (first full parity gate) | $18 |
| 6 | **Kimi K3 (extra high) only**; GLM-5.2 pre-reviews commit slicing (~2 TE) | $48 |
| 7 | GLM-5.2 (xhigh); escalate to K3 if cheap smoke fails twice | $7 |
| 8 | **DeepSeek V4 Flash (extra high)** + full gate suite; K3 handles collision stops | $1 |
| 9 | **Kimi K3 (extra high) only**; GLM-5.2 import-audit review (~2 TE) | $27 |
| 10 | **DeepSeek V4 Flash (extra high)** + gates | $1 |
| 11 | GLM-5.2 (xhigh) | $7 |
| 12 | GLM-5.2 (xhigh); K3 final sign-off review (~1 TE) | $7 |
| | **Total** | **≈ $190** |

Kickoff prompts — pin the listed context at session start; the model must paste gate output verbatim and stop at the stated boundary:

**Phase 0 (GLM-5.2 xhigh):**

```text
Read docs/issues/soc-gui-separation-and-accessibility-plan.md (§6 Phase 0, §12.6) and AGENTS.md. Execute Phase 0 only; no production edits.
1) Record branch/HEAD/working-tree state in branch notes.
2) Run: gradlew.bat test; the two ArchUnit classes; Dac2020Bm01RoutingTest. Paste results verbatim.
3) Count classes in gui / interactive / boardgraphics.
4) Build the DSN fixture coverage map (filename → owning test(s) → sole-coverage flag).
5) Capture v2.3.0 golden metrics on the agreed fixture matrix: completion/unrouted nets, full-DRC violations via DesignRulesChecker.getAllClearanceViolations(), SES sanity. Record in §12.6.
6) Snapshot known leaks (board/autoroute paint, RoutingJob Swing, BoardManager GUI API, SessionManager naming, GuiBoardManager→GraphicsContext/InteractiveState).
7) Draft the AGENTS.md D24 baseline-policy update as a separate, reviewable diff.
Stop at the Phase 0 boundary; do not begin Phase 1 inventories.
```

**Phase 1 (Kimi K3 extra high):**

```text
Pin: plan §1.1, §2 (D1–D30), §6 Phase 1, §12; AGENTS.md. Produce the Phase 1 inventory set, then the ArchUnit freeze layer:
- Full pipeline↔GUI/AWT/Swing dependency inventory.
- Classify interactive/boardgraphics types: state vs session vs facade vs renderer.
- Simple-name collisions across gui/interactive/boardgraphics, incl. the three CoordinateTransform classes (board/boardgraphics/io) and the InteractiveCommand flattening (D11).
- Confirm ratsnest compute call chain through drc.NetIncompletes/AirLine; list ObjectInfoPanel.Printable implementers.
- MVP-workflow _hu bundle completeness check.
- Phase 9 facade surface sketch (R19; home = gui.session per D30) + views-bootstrap plan.
- Swing-test inventory with @Tag("gui") retag plan; worker→Swing mutation inventory (removal assigned to Phase 9).
Then add ArchUnit rules (pipeline bans gui.**/Swing/AWT-UI with java.awt.geom whitelist; gui.** slice-cycle check) with frozen baselines: exact violation counts, owners, removal phases recorded in §12.4.
Gates: run ModuleBoundariesArchTest + SpecctraPackageArchTest; paste output. No production refactors beyond ArchUnit test files and §12 updates.
GLM-5.2 then reviews the freeze table and facade sketch before checkpoint 1 is declared.
```

**Phase 2 part A (Kimi K3 extra high):**

```text
Pin: plan §2 (D5/D7/D8/D9/D19/D22/D25), §6 Phase 2; AGENTS.md. Build the accessibility foundation core:
- A11y contract doc (name/role/description/state/value; label-for; menu names).
- Locator constants + shared registry (D22) — no private-field locators, no screen coordinates, no setVisible on top-level frames.
- Pure-JDK AccessibleContext harness: EDT execution, tree walk, find-by-locator/role, action invoke, state asserts; failures report accessible path + role + locator; mutations assert EventQueue.isDispatchThread().
- Gradle: @Tag("gui") + testGui (forced -Djava.awt.headless=true); test and testSlow exclude gui; testAll = test + testSlow + testGui (D25). Retag the Phase 1 inventoried Swing tests.
- Product wiring: accessible names/roles + locator registration on MVP controls; implement MVP workflow #1 end-to-end.
Gates: testGui green; test unchanged; spotlessCheck + checkstyle. Workflows #2–3 and EN/hu runs are a separate task (GLM-5.2).
```

**Phase 2 part B (GLM-5.2 xhigh):**

```text
Pin: plan §6 Phase 2, the a11y contract, and the harness/locator registry from part A. Replicate MVP workflows #2 and #3 (menu action; open/close parameter content; change setting; select layer; read status; cancel/stop route; open inspect/list — per plan), add sibling duplicate/empty accessible-name checks, run all MVP workflows in EN and hu (D19), and add the Hungarian resource-parity check for MVP bundles (document it in §7). Gates: testGui green in both locales; paste output. Do not modify the harness core without listing the change first.
```

**Phase 3 (Kimi K3 extra high):**

```text
Pin: plan §6 Phase 3, §2 (D12/D20); AGENTS.md BoardManager/InteractiveSettings invariants. Split headless vs GUI board contracts:
- Headless BoardManager API with no GUI methods; GUI session contract separate.
- Remove null-based getInteractiveSettings()/isInteractiveModeSupported() from the shared headless API (GUI-only access via GuiBoardManager).
- Move initializeManualTraceHalfWidths to GUI-session-only (R10 default).
- Preserve InteractiveSettings invariants exactly: singleton per GUI session via getOrCreate(board); reset(board) on every board load; getSettings() returns a live snapshot; merger priority 50; all fields private with PropertyChangeEvent-firing getters/setters.
- Update contract tests.
Gates: gradlew.bat test + ArchUnit classes; paste output. No package moves in this phase.
```

**Phase 4 (GLM-5.2 xhigh):**

```text
Pin: plan §6 Phase 4, §1.2 naming-collision warning; AGENTS.md. Neutralize core/management:
- Remove the Swing file chooser and any AWT UI types from core.RoutingJob; the GUI layer owns file picking.
- Rename SessionManager.getGuiSession/setGuiSession → getPrimarySession/setPrimarySession (D16). This is the management UUID session — do NOT touch anything related to the future gui.session package.
- Verify analytics/api have no GUI-session type dependencies.
- Reduce circular loader↔manager delegation only where trivially safe.
Gates: gradlew.bat test + ArchUnit; paste output.
```

**Phase 5 (Kimi K3 extra high):**

```text
Pin: plan §6 Phase 5, §2 (D13/D14/D24/D28/D29); AGENTS.md parity methodology. Thin compute vs presentation:
- Thin RatsNest to a GUI facade over drc incompletes (compute stays in drc.NetIncompletes/AirLine; D13). Optional rename R9 — default keep.
- Thin ClearanceViolations similarly over drc.
- Keep board.ObjectInfoPanel as the AWT-free interface (D14; accepted debt A1).
- Headless tests: incompletes/violations computable without GUI classes; a11y tests for lists/counts.
Exit gate (must pass before Phase 6): golden fixtures; clearance delta 0 vs §12.6 baseline using DesignRulesChecker.getAllClearanceViolations() (D29); full WIP-vs-v2.3.0 compare (D24/D28). If the compare diverges: sync instrumentation payloads first, then classify numeric drift vs behavioral ordering divergence, then apply the smallest tie-break fix. Record results in branch notes + §12.6.
```

**Phase 6 commit-sequence review (GLM-5.2 xhigh, read-only):**

```text
Pin: plan §6 Phase 6 and the current board paint call graph (read-only). Propose an independently revertible commit sequence: (1) neutral accessors for geometry/layer/net/type/visibility/selection; (2) GUI renderer + early offscreen BufferedImage smoke for major item families; (3) per-family removal of Drawable/Graphics/GraphicsContext/AWT Color paint APIs; (4) traversal + draw priority moved into the GUI renderer. Output a numbered commit list with rollback notes and per-commit gate commands. Do not edit code.
```

**Phase 6 execution (Kimi K3 extra high only):**

```text
Pin: plan §6 Phase 6, §2 (D15/D24/D28/D29), the approved commit sequence; AGENTS.md. Execute Phase 6 ONE commit at a time from the approved sequence; after each commit run test + ArchUnit + offscreen renderer smoke, paste output, and wait for go-ahead before the next commit. Constraints: no routing mutation behavior changes; headless load→route→DRC→SES must work without renderer init; java.awt.geom whitelist only in pipeline. Exit gate: full-DRC clearance delta 0 (D29) + no completion regression vs v2.3.0 (§12.6) + offscreen smokes green. Any gate failure → revert the current commit (commits are independent by construction) and diagnose before proceeding.
```

**Phase 7 (GLM-5.2 xhigh):**

```text
Pin: plan §6 Phase 7; AGENTS.md. Invert autorouter diagnostics: replace draw(Graphics, …) with neutral snapshots/events or GUI adapters; diagnostics opt-in; logging stays the headless path; no new AWT UI parameters in autoroute (ArchUnit). Cheap exit gate (D28): golden-fixture smoke = full DRC via DesignRulesChecker.getAllClearanceViolations() + completion/unrouted-net parity vs §12.6 goldens — no full version compare. Gates: test + ArchUnit + smoke; paste output. Two consecutive gate failures → escalate to Kimi K3 before continuing.
```

**Phase 8 (DeepSeek V4 Flash extra high):**

```text
Pin: plan §6 Phase 8, §2 (D11/D12), the Phase 1 collision inventory; AGENTS.md. Execute the approved mechanical move only:
- Move interactive production + tests flat into gui.interactive (temporarily including GuiBoardManager + session cluster, D12); flatten interactive.commands implementations into gui.interactive sources — NO commands subpackage; the InteractiveCommand interface itself stays put until Phase 9 (D11/D30).
- Resolve only the simple-name collisions listed in the Phase 1 inventory, using the pre-approved resolutions.
- Update FQCN imports, i18n context (run extract-context), tests. No .frb shims. No behavior changes.
Then run, pasting ALL output: testGui, ArchUnit classes, EnglishPropertiesParityTest + hu check, spotlessCheck, checkstyleMain/checkstyleTest, cheap smoke (full DRC + completion/unrouted-net parity vs §12.6, D28).
If you encounter ANY collision or ambiguity not in the inventory: STOP, report, do not improvise renames (handoff to Kimi K3).
```

**Phase 9 (Kimi K3 extra high only):**

```text
Pin: plan §4.4, §6 Phase 9, §2 (D20/D21/D26/D27/D30), the Phase 1 facade sketch; AGENTS.md. Extract gui.session:
- Move the §4.4 cluster (GuiBoardManager, InteractiveSettings — name unchanged, ScreenMessages, InteractiveActionThread, AutorouterAndRouteOptimizerThread) into gui.session.
- Move/introduce the facade (pick the name from the R19 shortlist) + InteractiveCommand interface + state-handle types into gui.session / gui.session.api (D30); concrete states in gui.interactive implement them; gui views own initial-state bootstrap/registration.
- Remove ALL gui.session → gui.interactive imports (not only concrete *State types); the gui.** slice-cycle check must pass; any temporary §12 freeze on that edge is removed by phase exit.
- Keep the gui.session → gui.rendering edge (D26); eliminate the inventoried worker→Swing call sites (EDT-only Swing mutation).
- Verify getPrimarySession/setPrimarySession callers remain correct (management UUID session — unrelated to gui.session).
Gates: test + testGui + ArchUnit (incl. slice-cycle) + a11y MVP workflows; paste output.
GLM-5.2 then runs an independent import audit of gui.session before checkpoint 10 is declared.
```

**Phase 10 (DeepSeek V4 Flash extra high):**

```text
Pin: plan §6 Phase 10, §2 (D10/D18/D26); AGENTS.md. Mechanical move only:
- Move boardgraphics → gui.rendering; rename boardgraphics.CoordinateTransform → ScreenTransform (D18 — board.CoordinateTransform and io.CoordinateTransform stay UNCHANGED).
- Update imports/i18n/tests; verify pipeline packages have ZERO imports of gui.rendering (gui.session → gui.rendering is allowed, D26).
Gates: test, testGui, ArchUnit, offscreen renderer tests, spotlessCheck/checkstyle; paste output. Any unlisted ambiguity → STOP and report.
```

**Phase 11 (GLM-5.2 xhigh):**

```text
Pin: plan §6 Phase 11, §2 (D9/D19/D22/D25), the a11y contract; AGENTS.md. Expand accessibility coverage across major windows/menus (component-only, forced headless): state-change tests (layer, mode, enablement, progress, visibility, violation state), keyboard/menu alternatives + inspect lists for critical canvas actions (D9). Switch CI path filters to the final gui/interactive|session|rendering paths (R11). Verify invariants: default test never requires a display; testGui always headless; testAll includes testGui. Run MVP + new workflows in EN and hu; hu resource-parity check green. Gates: testGui + testAll; paste output.
```

**Phase 12 (GLM-5.2 xhigh; Kimi K3 sign-off review):**

```text
Pin: plan §6 Phase 12, §5, §11, §12; AGENTS.md. Final cleanup:
- Remove transitional APIs/freezes/stale tests; promote frozen ArchUnit rules that reached zero to strict (same-checkpoint rule); §12.4 empty except accepted permanent debt A1–A3.
- Finalize the AGENTS.md baseline policy (D24: v2.3.0 primary; v1.9/src_v19 historical reference only).
- Update docs/architecture.md to match the final package map; update developer GUI-test docs.
- Record accepted debt (ObjectInfoPanel shape A1; drc incompletes A2; session→rendering A3/D26).
Full verification, pasting ALL output: spotlessCheck, checkstyleMain/checkstyleTest, extract-context --check, hu parity, gradlew.bat check, gradlew.bat testAll. Then verify §5 completion criteria item by item and the §11 sign-off list; produce the final report for the Kimi K3 sign-off review.
```

### 13.4 Operating rules for model use

1. **Re-anchor every session.** Pin this plan (current phase checklist + §1.1 + D-table), AGENTS.md, and the §12 ledger into context; have the model restate the active constraints (especially D26/D27/D30 in Phases 8–10) before editing.
2. **Gates are model-independent.** Every session ends with the phase's gate commands run and output pasted verbatim (§7 matrix). A model claiming success without gate output is a failure.
3. **Tier escalation.** If a gate fails twice under the assigned model, escalate one tier (Flash → GLM-5.2 → K3) for diagnosis before continuing.
4. **Flash guardrails.** Flash phases run only against a pre-approved mechanical plan; on any unlisted name collision or ambiguity the model must STOP and hand off — no improvised renames.
5. **No cross-session memory assumptions.** Branch notes + §12.6 metrics + phase checkboxes are the shared memory; update them at every §8 checkpoint.
6. **Phase 1 doubles as the model trial.** The collision-inventory/freeze-baseline work is the controlled comparison task: if K3 misses the three `CoordinateTransform` classes, skips test runs, or invents violation counts, swap the primary to GLM-5.2 before Phase 2.
7. **Parity methodology is fixed** regardless of model: sync instrumentation payloads WIP↔v2.3.0 first, classify numeric-only drift vs behavioral ordering divergence, apply the smallest ordering/tie-break fix, rerun at two `max_items` checkpoints (AGENTS.md remediation sequence).

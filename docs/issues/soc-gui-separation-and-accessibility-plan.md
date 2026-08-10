# GUI Separation and Accessibility Migration Plan

> Status: **Ready to implement** (architectural decisions locked; DeepSeek review incorporated; implementation not started)
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
| **0** Baseline | Capture packages, violations, `.frb` coverage map, green baseline | Low | Low | — |
| **1** Inventory + ArchUnit freeze | Leakage non-expandable; name-collision + EDT + GUI-test inventories | **High** | Low–Medium | 0 |
| **2** Accessibility foundation | Pure-JDK a11y harness + ≥3 component-only workflows; forced headless `testGui` | **High** | Medium | 0 (∥ with 1) |
| **3** Headless contracts | Remove GUI nullables from `BoardManager` | **High** | Medium | 1 |
| **4** Neutralize `core` / service leaks | Strip Swing from `RoutingJob`; `getPrimarySession` / `setPrimarySession` | **High** | Medium | 1, 3 |
| **5** Split compute vs presentation | Thin ratsnest/violations façades; keep `ObjectInfoPanel` in `board` | **High** | Medium | 1, 3 |
| **6** Invert board rendering | Domain stops painting; GUI renderer owns paint (revertible commits) | **Critical** | **High** | 1, 5 |
| **7** Invert autorouter diagnostics | Engine emits data; GUI/debug draws | High | Medium–High | 1, 6* |
| **8** Move `interactive` → `gui.interactive` | Flat package rename/move | Medium | Medium | 3–5 |
| **9** Extract `gui.session` | Move session cluster out of interactive; ports + EDT | High | Medium | 3, 8 |
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

gui.interactive (editor states)
  → may use gui.session, gui.rendering, board, …

gui.session (GuiBoardManager, InteractiveSettings, GUI action threads)
  → may use management, board, …
  → must NOT depend on gui.interactive states (goal after Phase 9)
  → must NOT be required to depend on gui.rendering

gui.rendering (paint only)
  → may read board / diagnostic snapshots
  → must NOT be imported by board / autoroute / rules / drc / geometry / core / management / api

management / core / api
  → must NOT use gui.**

board / rules / autoroute / drc / geometry / datastructures / settings / logger / debug / util / io
  → must NOT use gui.** | javax.swing.** | java.awt UI types
  → may use java.awt.geom.** only (explicit whitelist)
```

### 1.2 Narrative (non-authoritative)

Views and editor states sit above a GUI session façade; rendering is a **sibling** of session used by views/interactive for paint; headless services and the routing pipeline sit below and must not see GUI types.

**Naming collision warning:** `management.Session` / `getPrimarySession` (UUID-backed job/session registry) is **unrelated** to package `gui.session` (Swing GUI board-session façade). Do not conflate them in reviews.

## 2. Locked decisions

| # | Topic | Decision |
| --- | --- | --- |
| D1 | GUI windows/menus/panels split | **Out of scope** |
| D2 | Branch strategy | **One long-lived branch** |
| D3 | `.frb` fixtures | **Delete older `.frb` files**; no class-name compatibility shims |
| D4 | Done bar | **Phases 0–12** |
| D5 | GUI test runner | **`@Tag("gui")` + `testGui`** |
| D6 | CI trigger | **Path-filtered** GUI paths; ArchUnit always in normal `test` |
| D7 | Display strategy | **Component-only**; `testGui` forces `-Djava.awt.headless=true` |
| D8 | A11y tooling | **Pure JDK `AccessibleContext` harness** |
| D9 | Canvas a11y depth | **Critical keyboard/menu alternatives + inspect/item lists** |
| D10 | Rendering package | **`app.freerouting.gui.rendering`** |
| D11 | Interactive move | **Flat `gui.interactive` first** |
| D12 | `GuiBoardManager` home | **Phase 8 park in `gui.interactive`; Phase 9 extract to `gui.session`** |
| D13 | Ratsnest compute | **Keep in `drc`** (`NetIncompletes` / `AirLine`); GUI presentation only outside |
| D14 | `ObjectInfoPanel` | **Keep AWT-free interface in `board`**; GUI implements |
| D15 | AWT policy | **Whitelist `java.awt.geom.*`; ban Swing + AWT UI types** |
| D16 | `SessionManager` primary session | **`getPrimarySession` / `setPrimarySession`** (management UUID session — not `gui.session`) |
| D17 | `.frb` save/load code | **Keep code**; delete older fixtures only |
| D18 | Graphics transform rename | **`CoordinateTransform` → `ScreenTransform`** |
| D19 | A11y locale coverage | **English + Hungarian (`hu`)** |
| D20 | `gui.session` membership | **§4.5 default**, adjusted only by Phase 1 inventory |
| D21 | `InteractiveSettings` rename | **Keep name** |
| D22 | Accessible locator mechanism | **Stable locator constants + one shared registry/helper**; harness finds by locator, not translated label |
| D23 | Boundary debt home | **§12 of this plan** (old standalone tracker retired) |
| D24 | Routing parity baseline | **Phase out v1.9 compares.** Stable **v2.3.0** is the new baseline (already on par / slightly better than v1.9). Compare WIP / this branch to **v2.3.0**, not to v1.9. |
| D25 | `testGui` in Gradle task graph | **`testGui` is part of `testAll`**. It is **not** part of `test` or `testSlow`. Path-filtered CI may still run `testGui` alone on GUI paths. |

Standing non-goals:

- No routing algorithm / heuristic / scoring / clearance / optimizer policy changes.
- No visual snapshot testing as primary GUI strategy.
- No per-pixel accessible board object model.
- No `.frb` backward-compat `ObjectInputStream.resolveClass` mapping.
- No headful / Xvfb GUI CI requirement.

## 3. Review findings incorporated (DeepSeek + prior)

1. **Debt tracker reconciled:** live freeze ledger is §12; stale FIXED-only tracker deleted.
2. **§1 vs §4.2 fixed:** §1.1 is the authoritative graph; rendering is a sibling of session, not a layer under it.
3. **Decision IDs renumbered** to a contiguous D1–D23 set.
4. **Package map** includes `datastructures`, `util`, `settings`, `io`, etc. in the headless-safe set.
5. **`testGui` forces headless** via system property (not environment-dependent).
6. **Phase 0/1 inventories expanded:** `.frb` coverage map, simple-name collisions, existing Swing tests to retag, worker→Swing EDT call sites.
7. **Phase 6 is revertible-by-commit** with early offscreen renderer smoke requirement.
8. **A11y harness asserts EDT** (`EventQueue.isDispatchThread()`) on workflow actions.
9. **Routing parity baseline is v2.3.0**, not v1.9 (D24). Phase 5/6 exit gates compare WIP to stable v2.3.0.
10. **Quality gates** include spotlessCheck, checkstyle, i18n extract-context check.
11. **`SessionManager` rename scope** clarified as management UUID session only.
12. **Locator mechanism locked** to constants + shared registry (D22).
13. **`testGui` belongs in `testAll`**, never in default `test` or `testSlow` (D25).
14. **Scale note:** ~69 `gui` + ~37 `interactive` + ~10 `boardgraphics` classes (~116) in scope for moves/retagging.

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
    ├── interactive      # editor states / commands only (after Phase 9)
    ├── session          # GuiBoardManager, InteractiveSettings, ScreenMessages, GUI action threads
    └── rendering        # GraphicsContext, ScreenTransform, color tables, board/debug renderers
```

Unlisted pipeline/support packages above are intentionally **headless-safe** and subject to the same Swing/AWT-UI bans as `board` / `autoroute`.

### 4.2 Moves in scope

| Move | When |
| --- | --- |
| Thin ratsnest/violations façades; compute remains `drc` | Phase 5 |
| Remove `Drawable` / paint from board; renderer in GUI | Phase 6 |
| Autorouter diagnostics → GUI/debug adapters | Phase 7 |
| `interactive` → `gui.interactive` (flat, includes manager temporarily) | Phase 8 |
| Session cluster → `gui.session` | Phase 9 |
| `boardgraphics` → `gui.rendering` (+ `ScreenTransform`) | Phase 10 |
| `getGuiSession` → `getPrimarySession` in `SessionManager` | Phase 4 |
| Swing file chooser out of `RoutingJob` | Phase 4 |

### 4.3 Moves out of scope

- `gui.windows` / `menus` / `panels` / `components` reorganization
- Deep `board.model` / `autoroute.engine` splits
- `ObjectInfoPanel` → DTO/visitor rewrite
- New `ratsnest` / `connectivity` package
- `.frb` compatibility layer
- Headful GUI CI / Xvfb

### 4.4 Phase 9 session cluster (explicit)

Move from temporary `gui.interactive` into `gui.session` at minimum:

- `GuiBoardManager`
- `InteractiveSettings` (remains `GuiSettings` subtype for merger priority)
- `ScreenMessages`
- `InteractiveActionThread`
- `AutorouterAndRouteOptimizerThread`

Leave editor states (`*State`, commands, construction/route states) in `gui.interactive`.

Phase 1 inventory may add non-state session types to this list; do not invent extra subpackages for them.

## 5. Completion criteria (measurable)

- Pipeline/support packages (`board`, `rules`, `autoroute`, `drc`, `geometry`, `datastructures`, `settings`, `logger`, `debug`, `util`, `io`, `core`, `management`, `api`) have no `gui.**`, no Swing, no AWT UI dependencies (geom whitelist only).
- No production sources under `app.freerouting.interactive` or `app.freerouting.boardgraphics`.
- `gui.interactive` = editor states; `gui.session` = GUI session façade; `gui.rendering` = rendering.
- `BoardManager` has no nullable GUI settings API.
- Board/autorouter do not paint into `java.awt.Graphics`.
- `core.RoutingJob` has no Swing/AWT UI types.
- `SessionManager` uses `getPrimarySession` / `setPrimarySession`.
- Ratsnest/violation **compute** usable headlessly via `drc`.
- MVP GUI workflows green in `testGui` under forced headless, **EN + `hu`**, locator registry.
- Default `test` excludes `@Tag("gui")` and does not require a display (asserted by `testGui` system property + tagging inventory).
- `testAll` includes `testGui`; `test` and `testSlow` do not.
- §12 freeze ledger empty (or only documented accepted permanent debt with owners).
- `spotlessCheck`, checkstyle, ArchUnit, i18n extract-context check, routing golden fixtures, and **WIP vs v2.3.0** parity gates green.
- `docs/architecture.md` and `AGENTS.md` match this plan.

## 6. Phase checklists

### Phase 0 — Baseline

- [ ] Record branch / working tree / HEAD.
- [ ] Run ArchUnit + `test` + quick routing fixture; capture results.
- [ ] Inventory class counts: `gui`, `interactive`, `boardgraphics`.
- [ ] **`.frb` coverage map:** for each `.frb` fixture, record owning test(s) and whether it is sole coverage for a path; then delete older fixtures without leaving sole-coverage holes (or replace coverage with DSN/SES tests first).
- [ ] Snapshot known leaks (board/autoroute paint, `RoutingJob` Swing, `BoardManager` GUI API, `SessionManager` GUI naming).

### Phase 1 — Inventory + ArchUnit freeze

- [ ] Full dependency inventory (pipeline ↔ GUI / AWT UI / Swing).
- [ ] Classify interactive/boardgraphics types (state vs session vs façade vs renderer).
- [ ] **Simple-name collision check** across `gui`, `interactive`, `boardgraphics` before flat moves.
- [ ] Confirm ratsnest compute call chain through `drc.NetIncompletes` / `AirLine`.
- [ ] List `ObjectInfoPanel.Printable` implementors (awareness only).
- [ ] **Inventory existing tests that construct Swing / need EDT**; plan deliberate `@Tag("gui")` retags so default `test` does not silently lose coverage when exclusions start.
- [ ] **Inventory worker-thread → Swing mutations** (progress/status/board-panel updates from router/action threads); assign removal to Phase 9 (or earlier if trivial).
- [ ] ArchUnit:
  - forbid pipeline/support → `gui` / `interactive` / `boardgraphics` / future `gui.interactive|session|rendering`
  - forbid pipeline/support → `javax.swing..`
  - forbid pipeline/support → AWT UI types (explicit deny-list: `Graphics`, `Color`, `Font`, `Component`, `Image`, `Stroke`, …)
  - allow `java.awt.geom..`
  - forbid api/management → gui / Swing / AWT UI
  - forbid board/autoroute → rendering packages
  - freeze exact current violations with removal-phase owners in §12
- [ ] Record freeze budget: every freeze has owner + target phase; no freeze without a removal row in §12.

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
- [ ] **Exit gate:** golden routing fixture matrix + clearance delta **0**; compare WIP to stable **v2.3.0** (D24), not v1.9.

### Phase 6 — Invert board rendering (highest risk)

Land as **independently revertible commits** on the long-lived branch:

1. [ ] Add neutral accessors for geometry/layer/net/type/visibility/selection metadata (no paint removal yet).
2. [ ] Stand up GUI renderer + **early** offscreen `BufferedImage` smoke for major item types (must exist before mass paint deletion).
3. [ ] Remove `Drawable` / `Graphics` / `GraphicsContext` / AWT `Color` paint APIs from board (per family if needed).
4. [ ] Move traversal + draw priority fully into GUI renderer.
5. [ ] Headless load→route→DRC→SES without renderer init.
6. [ ] No routing mutation behavior changes.

**Exit gate:** clearance delta **0** on golden fixtures; no completion regression vs stable **v2.3.0** (D24); offscreen renderer smokes green.

### Phase 7 — Autorouter diagnostics

- [ ] Replace `draw(Graphics, …)` with neutral snapshots/events or GUI adapters.
- [ ] Diagnostics opt-in; logging remains headless path.
- [ ] ArchUnit: no new AWT UI parameters in `autoroute`.

### Phase 8 — Move to `gui.interactive` (flat)

- [ ] Resolve any simple-name collisions found in Phase 1 before/during move.
- [ ] Move remaining interactive production + tests flat into `gui.interactive`.
- [ ] Temporarily includes `GuiBoardManager` and session cluster.
- [ ] Update i18n FQCNs / resources / ArchUnit / docs.
- [ ] No `.frb` compat shims; fixtures already deleted/mapped in Phase 0.
- [ ] Run interactive tests, ArchUnit, i18n parity, MVP a11y, spotlessCheck, checkstyle.

### Phase 9 — Extract `gui.session`

- [ ] Extract §4.5 session cluster to `gui.session` (D20); keep `InteractiveSettings` name (D21).
- [ ] Break session→interactive state dependencies (or freeze with §12 removal plan).
- [ ] Ports for load / route start-stop / progress / board replace / settings.
- [ ] Eliminate inventoried worker→Swing call sites; EDT-only Swing mutation.
- [ ] Confirm `getPrimarySession` / `setPrimarySession` callers remain correct (still management API).
- [ ] A11y workflows still pass component-only under forced headless.

### Phase 10 — Move to `gui.rendering`

- [ ] Move `boardgraphics` → `gui.rendering`.
- [ ] Rename graphics `CoordinateTransform` → `ScreenTransform` (D18).
- [ ] Confirm pipeline has zero imports of rendering package.
- [ ] Offscreen renderer tests green; headless routing green.

### Phase 11 — Accessibility expansion + path-filtered CI

- [ ] Expand coverage across major windows/menus (still component-only).
- [ ] State-change tests: layer, mode, enablement, progress, visibility, violation state.
- [ ] Keyboard/menu alternatives + inspect lists for critical canvas actions.
- [ ] Switch CI path filters to final `gui/interactive|session|rendering` (+ remaining `gui/**` as needed).
- [ ] Default `test` never requires display; `testGui` always headless.

### Phase 12 — Final cleanup

- [ ] Remove transitional APIs / freezes / stale tests.
- [ ] Promote ArchUnit rules to strict; §12 freeze table empty except accepted permanent debt.
- [ ] Update `docs/architecture.md`, `AGENTS.md`, developer GUI-test docs.
- [ ] Fix stale `CODE_STRUCTURE_RECOMMENDATIONS` link → `docs/research/code_structure_recommendations.md`.
- [ ] Record accepted debt: `ObjectInfoPanel` shape; DRC includes incompletes.

## 7. Validation matrix

| Command | Purpose |
| --- | --- |
| `.\gradlew.bat test` | Fast tests (excludes `slow`, `gui`) |
| `.\gradlew.bat testGui` | `@Tag("gui")` component-only a11y tests (**forced headless**) |
| `.\gradlew.bat test --tests "app.freerouting.architecture.ModuleBoundariesArchTest"` | Boundaries |
| `.\gradlew.bat test --tests "app.freerouting.io.SpecctraPackageArchTest"` | Parser encapsulation |
| `.\gradlew.bat test --tests "app.freerouting.i18n.EnglishPropertiesParityTest"` | i18n ownership |
| `.\gradlew.bat test --tests "app.freerouting.fixtures.Dac2020Bm01RoutingTest"` | Quick routing smoke |
| `.\gradlew.bat spotlessCheck` | Formatting gate (do **not** auto-run `spotlessApply`) |
| `.\gradlew.bat checkstyleMain checkstyleTest` | Style gates |
| `python scripts/i18n/extract-context.py --check` | i18n context sync after package moves |
| `.\gradlew.bat check` | Full verification (does **not** imply `testGui` unless wired later) |
| `.\gradlew.bat testAll` | Fast + slow + **gui** (`test` → `testSlow` → `testGui`) |
| WIP vs **v2.3.0** compare script/workflow | Routing parity at Phase 5/6 gates (D24); do **not** require v1.9 |

**Routing checkpoints:** golden fixture matrix; clearance-violation delta **0**; completion not regressed vs stable **v2.3.0**; SES validity. v1.9 parity is retired for this initiative (and going forward as the primary baseline).

**GUI checkpoints:** locator discovery/actions; EDT assertion; no leaked windows/threads; forced headless; **EN + hu**.

**Note:** Existing `scripts/tests/compare-versions.ps1` still targets v1.9 (`buildBothVersions` / `freerouting-v190.log`). Phase 5/6 need either an adapted script or a sibling workflow that builds/runs stable **v2.3.0** vs current WIP. Treat that tooling update as part of the Phase 5 gate setup, not as restoring v1.9.

## 8. Long-lived branch checkpoints

1. Phase 0–1 inventory + ArchUnit freezes + §12 ledger rows  
2. `testGui` (forced headless) + harness + path-filter stub + Swing-test retags  
3. MVP locators + ≥3 workflows (EN + `hu`)  
4. Headless `BoardManager` split  
5. `RoutingJob` Swing removal + `getPrimarySession` / `setPrimarySession`  
6. Ratsnest/violations façade thinning + Phase 5 routing gate (vs **v2.3.0**)  
7. Board paint inversion as revertible commits + Phase 6 routing/parity gate (vs **v2.3.0**)  
8. Autorouter diagnostic inversion  
9. Flat move to `gui.interactive`  
10. Extract `gui.session`  
11. Move to `gui.rendering` + `ScreenTransform`  
12. A11y expansion (EN + `hu`), final CI filters, docs, strict ArchUnit, empty freeze ledger  

## 9. Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Paint inversion changes routing behavior | Revertible commits; early offscreen smoke; fixture + DRC + **v2.3.0** parity gates |
| Tagging `@Tag("gui")` drops default CI coverage | Phase 1 Swing-test inventory + deliberate retags |
| Headless assumed but not forced | `testGui` sets `java.awt.headless=true` |
| Worker→Swing races | Phase 1 inventory; Phase 9 elimination; harness EDT asserts |
| i18n breaks on package moves | Update bundle owners; parity + extract-context check |
| Older `.frb` fixtures fail / sole coverage lost | Phase 0 coverage map before delete |
| Simple-name collisions on flat moves | Phase 1 collision inventory |
| ArchUnit freeze pile-up | §12 owner + removal phase; budget reviewed each checkpoint |
| `gui.interactive` temporary god-package | Phase 9 mandatory |
| Confusing `getPrimarySession` with `gui.session` | Explicit docs in §1.2 / Phase 4 |
| Long branch drift | Regular master merge/rebase; keep ArchUnit green |
| geom whitelist creep | UI deny-list + review new awt imports |

## 10. Remaining decision points

D1–D25 are locked (including former R15–R17). Operational leftovers only:

| ID | Topic | Default / action | When |
| --- | --- | --- | --- |
| **R8** | Temporary ArchUnit freezes allowed? | **Yes**, exact freezes with §12 owners | Phase 1 |
| **R9** | Rename GUI `RatsNest` façade after thinning? | Optional; keep name if unclear | Phase 5 |
| **R10** | `initializeManualTraceHalfWidths` on headless manager? | Prefer **GUI-session-only** | Phase 3 |
| **R11** | Exact GitHub Actions path-filter globs | Documented GUI/legacy paths; switch after Phase 8/10 | Phase 2 / 11 |
| **R12** | `ObjectInfoPanel` DTO follow-up | **Out of scope**; §12 accepted debt | Future |
| **R13** | Eliminate `java.awt.geom` from pipeline | **Out of scope** | Future |
| **R14** | Legacy `interactive.Settings` vs `InteractiveSettings` | Inventory Phase 1; merge/delete Phase 8/9 | Phase 1 / 8 / 9 |
| **R18** | How to obtain/run stable v2.3.0 for parity | Prefer released v2.3.0 executable/artifact vs building a `v2.3.0` git tag checkout; pick when adapting compare tooling | Phase 5 setup |

Retired / locked formerly open items:

- **R15** → D19 (`hu`)
- **R16** → D24 (v2.3.0 baseline; phase out v1.9)
- **R17** → D25 (`testGui` in `testAll` only)

## 11. Final sign-off

- [ ] Phases 0–12 complete with recorded checkpoint results.
- [ ] §5 completion criteria satisfied.
- [ ] No `.frb` compatibility shims; older fixtures deleted per coverage map.
- [ ] Component-only pure-JDK a11y MVP workflows green in forced-headless `testGui` (EN + `hu`).
- [ ] ArchUnit strict; §12 freezes cleared except accepted permanent debt.
- [ ] `spotlessCheck`, checkstyle, i18n extract-context check green after moves.
- [ ] `.\gradlew.bat check` passes.
- [ ] `.\gradlew.bat testAll` passes (`test` + `testSlow` + `testGui`).
- [ ] `docs/architecture.md` and `AGENTS.md` updated; old debt tracker gone.
- [ ] Deferred R8–R14 / R18 resolved or explicitly accepted with defaults.

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

### 12.4 Active ArchUnit freezes (fill in Phase 1)

| Freeze ID | Rule | Violation count (baseline) | Removal phase | Owner | Status |
| --- | --- | --- | --- | --- | --- |
| *(none yet — populate during Phase 1)* | | | | | |

### 12.5 Validation

```powershell
Set-Location "C:\Work\freerouting"
.\gradlew.bat test --tests "app.freerouting.architecture.ModuleBoundariesArchTest" --tests "app.freerouting.io.SpecctraPackageArchTest"
```

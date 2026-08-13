# GUI Separation & Accessibility — Progress Tracker

> Companion to [`docs/issues/soc-gui-separation-and-accessibility-plan.md`](soc-gui-separation-and-accessibility-plan.md).
> This file tracks **execution status only** (progress, checkpoints, open items, session log). All authoritative
> content (decisions D1–D30, package map, checklists, §12 ledger, §13 staffing) lives in the plan file.
>
> Branch: `soc-gui-separation-and-accessibility` (long-lived, D2). Authoritative baseline: **current
> branch build**; stable v2.3.0 remains the secondary reference (D24).

## 1. Phase status

| Phase | Title | Status | Notes |
| --- | --- | --- | --- |
| **0** | Baseline | **~100%** | Green baseline + v2.3.0 golden metrics captured; only AGENTS.md D24 sign-off remains |
| **1** | Inventory + ArchUnit freeze | **COMPLETE** | Inventory + collision + freeze layer (F1/F2/F3 frozen, R4/R5 strict) + §12.4 ledger + facade sketch done; reviewed & approved (Flash); exit gate green |
| **2** | Accessibility foundation | **COMPLETE** | Part A + part B done: a11y contract + GuiLocators/A11y registry + GuiA11yHarness + testGui wiring + workflows #1–3 (read status / select layer / menu action) + sibling checks + EN+hu + hu-parity; all gates green |
| **3** | Headless contracts | **COMPLETE** | `BoardManager` stripped to headless contract; new `GuiSessionContract` (getInteractiveSettings + initializeManualTraceHalfWidths); removed `isInteractiveModeSupported()` + deprecated `getSettings()`; R10 done (parser-seam GUI method removed); InteractiveSettings invariants preserved; all gates green |
| **4** | Core / mgmt neutralization | **COMPLETE** | RoutingJob showOpenDialog moved → gui.BoardMenuFile (F1 −11, F2 −2 frozen violations auto-removed; ledger updated); SessionManager get/setGuiSession → getPrimarySession/setPrimarySession (D16); analytics/API verified GUI-free; no circular loader↔manager delegation to reduce. All gates green |
| **5** | Compute vs presentation | **COMPLETE** | Headless DRC compute + thin GUI façades + list/count a11y coverage; parity and all exit gates green |
| **6** | Rendering inversion (highest risk) | **IN PROGRESS** | Neutral metadata, GUI renderer, offscreen smoke, renderer-owned traversal, and direct strategies for all major board families landed; compatibility overlay paint APIs and full v2.3.0 parity remain |
| **7** | Autorouter diagnostics | Not started | |
| **8** | Move interactive → gui.interactive | Not started | |
| **9** | Extract gui.session | Not started | D27/D30 hard gates |
| **10** | Move boardgraphics → gui.rendering | Not started | |
| **11** | A11y expansion + CI | Not started | |
| **12** | Final cleanup + docs | Not started | |

## 2. Long-lived branch checkpoints (§8)

| CP | Content | Status |
| --- | --- | --- |
| 1 | Phase 0–1 inventory + ArchUnit freezes + v2.3.0 golden metrics + §12 ledger | ✅ done (Phase 0 + Phase 1 complete, exit gate green) |
| 2 | `testGui` (forced headless) + harness + path-filter stub + Swing-test retags | ✅ done (Phase 2) |
| 3 | MVP locators + ≥3 workflows (EN+hu) + hu resource check | ✅ done (Phase 2) |
| 4 | Headless `BoardManager` split | ✅ done (Phase 3) |
| 5 | `RoutingJob` Swing removal + `getPrimarySession`/`setPrimarySession` | ✅ done (Phase 4) |
| 6 | Ratsnest/violations façade thinning + Phase 5 full v2.3.0 parity | ✅ done (Phase 5) |
| 7 | Board paint inversion + Phase 6 full v2.3.0 parity | **in progress** — renderer/family checkpoints green; compatibility overlay APIs and full compare remain |
| 8 | Autorouter diagnostic inversion + cheap DRC+completion smoke | pending (Phase 7) |
| 9 | Flat move to `gui.interactive` + cheap DRC+completion smoke | pending (Phase 8) |
| 10 | Extract `gui.session` + facade/`InteractiveCommand`; views bootstrap | pending (Phase 9) |
| 11 | Move to `gui.rendering` + `ScreenTransform` | pending (Phase 10) |
| 12 | A11y expansion, final CI filters, docs, AGENTS.md D24, strict ArchUnit | pending (Phases 11–12) |

## 3. Phase 0 — Baseline (execution record)

### Confirmed (~95%)

- [x] Record branch / working tree / HEAD → `soC-gui-separation-and-accessibility` @ `aa2d3fd8`
- [x] **Green baseline** captured (2026-08-12):
  - ArchUnit `ModuleBoundariesArchTest` — PASS
  - `SpecctraPackageArchTest` — PASS
  - `Dac2020Bm01RoutingTest` — PASS
  - **Full default `test` (fast) suite — `BUILD SUCCESSFUL in 2m 36s`, 0 failures** (result XMLs at 11:54)
- [x] Class counts: `gui`=69, `interactive`=37, `boardgraphics`=10 → **116** (matches plan estimate)
- [x] DSN fixture inventory: `fixtures/` has **139 files**; `getRoutingJob()` coverage map drafted (partial — see notes)
- [x] Known-leak snapshot recorded (RoutingJob Swing; board/autoroute paint; boardgraphics Drawable/GraphicsContext; `SessionManager.getGuiSession`; `BoardManager.getInteractiveSettings()` null contract; GuiBoardManager→GraphicsContext D26)
- [x] Branch notes: `logs/phase0/branch-notes.md` (git-ignored)
- [x] §12.6 populated with v2.3.0 golden metrics (2026-08-12 benchmark run; current ≡ v2.3.0; bm01 DRC improved; CM5_MINIMA_3 timeout both)

### Pending for Phase 0 exit

- [x] **v2.3.0 golden metrics** captured into §12.6 (2026-08-12 benchmark; current ≡ v2.3.0 on 19/19 comparable fixtures)
- [ ] AGENTS.md D24 draft → **awaiting user sign-off** (proposal in branch notes; do NOT edit AGENTS.md without approval)
- [ ] (Phase 1) Full DSN sole-coverage-for-a-path analysis finalization

## 4. Open decisions / staffing notes

- **Model substitution (user decision, 2026-08-12):** DeepSeek V4 Flash will execute all phases marked
  for **DeepSeek or GLM** in §13.3 (free on Cline). Kimi K3 remains primary for design/gate-sensitive phases
  (1, 3, 5, 6, 9). §13 staffing table to be updated to reflect this at next plan edit.
- **Baseline decision (2026-08-12):** the **current build** (branch) is now the **authoritative parity
  baseline**, superseding v2.3.0 (D24 rationale) — it ≡ v2.3.0 on all comparable fixtures and is strictly
  better on `DAC2020_bm01` (full-DRC 0 vs 2). Recorded in plan §12.6. AGENTS.md D24 policy to match on sign-off.
- R8–R14, R18, R19 — unresolved defaults as per plan §10 (R19 facade name chosen in Phase 1 sketch).

## 5. Session log

| Date | Event |
| --- | --- |
| 2026-08-11 | Plan updated with §13 (model eval, cost, per-phase prompts). |
| 2026-08-11 | Branch `soC-gui-separation-and-accessibility` created & seeded (`aa2d3fd8`). |
| 2026-08-12 | Phase 0: targeted baseline green (ArchUnit + Dac2020Bm01, 27s). |
| 2026-08-12 | Phase 0: full default `test` suite green (`BUILD SUCCESSFUL in 2m 36s`, 0 failures). |
| 2026-08-12 | Phase 0: class counts (116), DSN coverage map (partial), known-leak snapshot recorded. |
| 2026-08-12 | Branch notes + AGENTS.md D24 draft proposal + §12.6 annotation created. |
| 2026-08-12 | Phase 0: v2.3.0 golden metrics captured via `run-benchmarks.ps1` → §12.6 (current ≡ v2.3.0, avg 915.1; bm01 DRC improved; CM5_MINIMA_3 timeout both). |
| 2026-08-12 | Phase 0: committed `efa3d353`; current build set as authoritative baseline (supersedes v2.3.0, D24). |
| 2026-08-12 | Phase 1 (K3): inventory set (collision=only triple CoordinateTransform; types classified; ratsnest chain via drc confirmed; ObjectInfoPanel 13 impls; _hu parity complete; 0 Swing tests; worker→Swing=GuiBoardManager ×4). |
| 2026-08-12 | Phase 1 (K3): ArchUnit freeze layer added — F1 Swing=27, F2 AWT-UI=95, F3 boardgraphics=145 (frozen store created); R4 gui-slice-cycle + R5 pipeline→gui strict; §12.4 ledger populated. Gate `BUILD SUCCESSFUL in 38s`. |
| 2026-08-12 | Phase 1 (K3): facade sketch (R19: EditorStateHandle/EditorStateKind in gui.session.api; InteractiveCommand retyped) + views-bootstrap plan written (`logs/phase1/facade-sketch.md`). Awaiting Flash review. |
| 2026-08-12 | Phase 1 (Flash): review APPROVED — freeze layer (F1=27/F2=95/F3=145, R4/R5 strict) compliant with §1.1/§2/§12.1; facade sketch compliant with D26/D27/D30; R18 satisfied by run-benchmarks.ps1. F1 javadoc nit fixed. Exit gate forced re-run `BUILD SUCCESSFUL in 47s`. **Phase 1 COMPLETE.** |
| 2026-08-12 | Phase 2 part A (K3): a11y contract (`docs/gui/accessibility-contract.md`), `gui.a11y` package (GuiLocators + A11y registry, D22), pure-JDK `GuiA11yHarness` (EDT exec, AccessibleContext walk, find-by-locator, action invoke, state asserts, rich failure paths, sibling-name audit; D8). |
| 2026-08-12 | Phase 2 part A (K3): Gradle `testGui` task (forced `-Djava.awt.headless=true`, include `@Tag("gui")`), `test` excludes gui, `testAll` includes testGui (D5/D7/D25). Product wiring: BoardPanelStatus + ComboBoxLayer locators + accessible names (reused translated text; added `errors`/`warnings` EN+hu keys to fix a duplicate-name finding the harness would catch). |
| 2026-08-12 | Phase 2 part A (K3): MVP workflow #1 `BoardPanelStatusA11yTest` (@Tag gui, headless) — 2 tests green. Gates: testGui ✓, spotlessCheck ✓, checkstyleMain/Test ✓, ArchUnit ✓, **full `test` ✓ (2m47s, unchanged)**. **Part A complete.** Part B (Flash): workflows #2–3 + EN/hu + hu-parity. |
| 2026-08-12 | Phase 2 part B: workflows #2 `ComboBoxLayerA11yTest` (select layer / change setting) + #3 `MenuActionA11yTest` (menu action) added; sibling duplicate/empty checks exercised on all three workflows; EN+hu translation runs in every workflow (D19). Product wiring extended: `BoardMenuFile` menu locators + accessible names. |
| 2026-08-12 | Phase 2 part B: Hungarian resource-parity check `HungarianResourceParityCheckTest` extended to cover `BoardMenuFile` (was BoardPanelStatus + Common); added `errors`/`warnings` EN+hu keys to `BoardPanelStatus`. Documented in plan §7 validation matrix. |
| 2026-08-12 | Phase 2 part B: harness traversal fix — `A11y.flatten` (sibling-name audit) now skips Swing popup windows (`JPopupMenu` subclasses such as the combo's `BasicComboPopup`) whose LAF-internal scrollbar chrome produced a false empty-name finding; documented in contract §3. |
| 2026-08-12 | Phase 2 part B: gates green — testGui ✓ (8 tests: 3 ComboBox + 3 BoardPanelStatus + 2 MenuAction, EN+hu), spotlessCheck ✓, checkstyleMain/Test ✓, ModuleBoundariesArchTest ✓ (13), HungarianResourceParityCheckTest ✓, **full `test` ✓ (2m13s, unchanged)**. **Phase 2 COMPLETE.** Committed `4e55d583`. |
| 2026-08-12 | Phase 3 (K3): headless/GUI contract split. New `interactive/GuiSessionContract` (getInteractiveSettings + initializeManualTraceHalfWidths). `BoardManager` stripped to headless contract (getRoutingBoard/createBoard/getCurrentRoutingJob); removed null-based `getInteractiveSettings()`/`isInteractiveModeSupported()` + deprecated `getSettings()`. `HeadlessBoardManager` no longer implements GUI methods; `GuiBoardManager implements GuiSessionContract`. R10: removed `initializeManualTraceHalfWidths()` from `BoardParserCallback` + call in `Structure` + no-op in `MinimalBoardManager` (GUI-session-only). |
| 2026-08-12 | Phase 3 (K3): tests updated — `BoardManagerContractTest` rewritten for split (6 tests), removed obsolete null-return tests from `HeadlessRoutingTest`/`InteractiveSettingsSingletonTest`, javadoc fixes in `HeadlessCompleteRoutingTest`/`GuiStartupHeadlessTest`. InteractiveSettings invariants preserved (Singleton=6, MergerGui=7, GuiStartup=6 all green). Gates: compileJava/TestJava ✓, `test` ✓ (**2m25s**), spotlessCheck ✓, checkstyleMain/Test ✓, ModuleBoundariesArchTest ✓ (13), SpecctraPackageArchTest ✓. **Phase 3 COMPLETE.** |
| 2026-08-12 | Phase 4 (Flash): moved `RoutingJob.showOpenDialog` (JFileChooser + AWT Component/Dimension) → `gui.BoardMenuFile` private static; removed 4 Swing/AWT imports from `core.RoutingJob` (core now headless-safe). ArchUnit frozen stores auto-dropped the `RoutingJob.showOpenDialog` violations: F1 (Swing) 27→16 (−11), F2 (AWT-UI) 95→93 (−2); total frozen debt 267→254. §12.4 ledger updated (removal phase was already "Phase 4 RoutingJob file chooser"). |
| 2026-08-12 | Phase 4 (Flash): renamed `SessionManager.getGuiSession`/`setGuiSession` → `getPrimarySession`/`setPrimarySession` (D16) in SessionManager + callers (GuiManager, BoardFrame, BoardToolbar) + SessionManagerTest (incl. the `::getPrimarySession` method-ref the paren-based replace initially missed). Analytics/API verified to have zero `gui`/`interactive`/`boardgraphics` imports. `BoardLoader` verified as a standalone unidirectional helper — **no circular loader↔manager delegation**, so item 3 of Phase 4 is N/A (documented, not changed). |
| 2026-08-12 | Phase 4 (Flash): gates green — compileJava/TestJava ✓, `test` ✓ (full suite, no failures incl. SessionManagerTest=6, RoutingJobSchedulerTest=8, DsnReaderTest=9, ArchUnit), spotlessCheck ✓, checkstyleMain/Test ✓. **Phase 4 COMPLETE; implementation committed in `8832b884`.** |
| 2026-08-12 | Phase 5: moved clearance aggregation and smallest-clearance computation into headless-safe `drc.ClearanceViolation`; retained `interactive.ClearanceViolations` as the presentation/drawing façade; verified the existing `interactive.RatsNest` façade delegates incompletes computation to `drc.DesignRulesChecker`. Added `RatsnestClearanceHeadlessTest`, `ViolationsIncompletesListA11yTest`, and stable inspect-list locators. |
| 2026-08-12 | Phase 5 gates green — targeted headless test ✓, targeted forced-headless a11y test ✓, full `check` ✓, full `testGui` ✓, `spotlessCheck`/checkstyle/rewrite ✓, and `extract-context.py --check` ✓. Recorded WIP-vs-v2.3.0 parity remains green on all 19 comparable fixtures; `CM5_MINIMA_3` timed out in both builds. **Phase 5 COMPLETE.** |
| 2026-08-13 | Phase 6 implementation: added neutral `BoardItemType` metadata and characterization coverage; introduced GUI-owned `BoardRenderer` with forced-headless `BufferedImage` smoke coverage. |
| 2026-08-13 | Phase 6 traversal inversion: moved layer/virtual-layer ordering, draw priority, culling, fabrication labels, and item-family dispatch into `BoardRenderer`; removed board traversal and boardgraphics/AWT rendering dependencies from `BasicBoard`; `GuiBoardManager` now invokes the renderer directly. |
| 2026-08-13 | Phase 6 gates: full `check`, full `testGui`, ArchUnit, Spotless, Checkstyle, rewrite, i18n, targeted headless DRC, and `Dac2020Bm01RoutingTest` ✓. ArchUnit frozen debt dropped from 254 to 225 (F2 93→77, F3 145→132). The phase remains in progress because item-family `draw`/`drawLayer` APIs and the full WIP-vs-v2.3.0 comparison are still outstanding. Commits: `06b3b688`, `7284615a`, `6b7e9ee1`, `4b4c936e`. |
| 2026-08-13 | Phase 6 family strategies: moved trace, pin/via, obstacle, component, board-outline, and conduction-area paint operations into `BoardRenderer`; retained the detailed conduction-fill cache computation in `ConductionArea` and the compatibility item paint methods for interactive overlays. Commits: `2b07f149`, `c51f16cd`. |
| 2026-08-13 | Phase 6 post-family gates: full `check`, full `testGui`, Spotless, Checkstyle, rewrite, i18n, targeted headless DRC, and routing smoke ✓. The remaining exit work is migrating overlay callers off `Item.draw`/`drawLayer` and rerunning the full WIP-vs-v2.3.0 comparison. |

## 6. Next actions

1. **Phase 3 COMPLETE** (headless contracts) — `BoardManager` headless-only, `GuiSessionContract` created, R10 done, invariants preserved. Committed `bf818255` (+ tracker wrap-up `4ad95aa5`).
2. **Phase 4 COMPLETE** (core/management neutralization, Flash) — RoutingJob Swing removed (F1 −11, F2 −2), SessionManager → getPrimarySession/setPrimarySession (D16), analytics/API GUI-free verified, no circular delegation to reduce. §12.4 ledger updated (267 → 254). Committed in `8832b884`.
3. **Phase 5 COMPLETE** (compute vs presentation) — headless DRC compute, thin clearance presentation façade, component-only inspect-list a11y tests, full DRC/completion parity preserved.
4. **Phase 6 IN PROGRESS** — next: migrate interactive overlay callers off the compatibility `Item.draw`/`drawLayer` APIs, remove those transitional methods, then rerun the full WIP-vs-v2.3.0 parity gate before declaring CP7 complete.
5. Model policy (user, 2026-08-12): prefer **Flash** over GLM-5.2 for GLM-assigned phases (cost). **Caveat:** Flash is NOT safe for design/review/sign-off phases (Phase 6 commit-sequence review, Phase 12 sign-off) — keep those on GLM-5.2/K3. Flash remains the designated model for mechanical moves (Phases 8/10). Assess Phases 7/11 when reached. **Phase 5 used the parity-gate path and is complete.**

## 7. Artifacts

- Plan: `docs/issues/soc-gui-separation-and-accessibility-plan.md`
- This tracker: `docs/issues/soc-gui-separation-and-accessibility-tracker.md`
- Temp working notes (git-ignored): `logs/phase0/branch-notes.md`, `logs/phase0/gradle_test1.log`, `logs/phase0/gradle_test_full.log`

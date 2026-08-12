# GUI Separation & Accessibility — Progress Tracker

> Companion to [`docs/issues/soc-gui-separation-and-accessibility-plan.md`](soc-gui-separation-and-accessibility-plan.md).
> This file tracks **execution status only** (progress, checkpoints, open items, session log). All authoritative
> content (decisions D1–D30, package map, checklists, §12 ledger, §13 staffing) lives in the plan file.
>
> Branch: `soC-gui-separation-and-accessibility` (long-lived, D2). Baseline: **stable v2.3.0** (D24).

## 1. Phase status

| Phase | Title | Status | Notes |
| --- | --- | --- | --- |
| **0** | Baseline | **~100%** | Green baseline + v2.3.0 golden metrics captured; only AGENTS.md D24 sign-off remains |
| **1** | Inventory + ArchUnit freeze | **COMPLETE** | Inventory + collision + freeze layer (F1/F2/F3 frozen, R4/R5 strict) + §12.4 ledger + facade sketch done; reviewed & approved (Flash); exit gate green |
| **2** | Accessibility foundation | **COMPLETE** | Part A + part B done: a11y contract + GuiLocators/A11y registry + GuiA11yHarness + testGui wiring + workflows #1–3 (read status / select layer / menu action) + sibling checks + EN+hu + hu-parity; all gates green |
| **3** | Headless contracts | Not started | |
| **4** | Core / mgmt neutralization | Not started | |
| **5** | Compute vs presentation | Not started | exits on full v2.3.0 parity |
| **6** | Rendering inversion (highest risk) | Not started | exits on full v2.3.0 parity; revertible commits |
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
| 4 | Headless `BoardManager` split | pending (Phase 3) |
| 5 | `RoutingJob` Swing removal + `getPrimarySession`/`setPrimarySession` | pending (Phase 4) |
| 6 | Ratsnest/violations façade thinning + Phase 5 full v2.3.0 parity | pending |
| 7 | Board paint inversion + Phase 6 full v2.3.0 parity | pending |
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
| 2026-08-12 | Phase 2 part B: gates green — testGui ✓ (8 tests: 3 ComboBox + 3 BoardPanelStatus + 2 MenuAction, EN+hu), spotlessCheck ✓, checkstyleMain/Test ✓, ModuleBoundariesArchTest ✓ (13), HungarianResourceParityCheckTest ✓, **full `test` ✓ (2m13s, unchanged)**. **Phase 2 COMPLETE.** |

## 6. Next actions

1. **Phase 2 COMPLETE** (accessibility foundation) — workflows #1–3, sibling checks, EN+hu, hu-parity, all gates green.
2. **Phase 3 — Headless contracts (next):** headless `BoardManager` API without GUI methods; separate GUI-session contract; remove `getInteractiveSettings()` / `isInteractiveModeSupported()` null-based API from shared headless path; prefer moving `initializeManualTraceHalfWidths` to GUI-session-only (R10); update contract tests. Depends on Phase 1 (done).
3. Update §13.3 model-cols for the Flash-substitution decision.

## 7. Artifacts

- Plan: `docs/issues/soc-gui-separation-and-accessibility-plan.md`
- This tracker: `docs/issues/soc-gui-separation-and-accessibility-tracker.md`
- Temp working notes (git-ignored): `logs/phase0/branch-notes.md`, `logs/phase0/gradle_test1.log`, `logs/phase0/gradle_test_full.log`

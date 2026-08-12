# GUI Separation & Accessibility — Progress Tracker

> Companion to [`docs/issues/soc-gui-separation-and-accessibility-plan.md`](soc-gui-separation-and-accessibility-plan.md).
> This file tracks **execution status only** (progress, checkpoints, open items, session log). All authoritative
> content (decisions D1–D30, package map, checklists, §12 ledger, §13 staffing) lives in the plan file.
>
> Branch: `soC-gui-separation-and-accessibility` (long-lived, D2). Baseline: **stable v2.3.0** (D24).

## 1. Phase status

| Phase | Title | Status | Notes |
| --- | --- | --- | --- |
| **0** | Baseline | **IN PROGRESS — ~95%** | Green baseline captured; v2.3.0 metrics pending tooling (R18); AGENTS.md D24 draft awaiting sign-off |
| **1** | Inventory + ArchUnit freeze | Not started | |
| **2** | Accessibility foundation | Not started | |
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
| 1 | Phase 0–1 inventory + ArchUnit freezes + v2.3.0 golden metrics + §12 ledger | 🔄 in progress (Phase 0 nearly done) |
| 2 | `testGui` (forced headless) + harness + path-filter stub + Swing-test retags | pending |
| 3 | MVP locators + ≥3 workflows (EN+hu) + hu resource check | pending |
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
- [x] §12.6 annotated in plan (WIP baseline note; v2.3.0 numbers intentionally not faked)

### Pending for Phase 0 exit

- [ ] **v2.3.0 golden metrics** capture into §12.6 — requires v2.3.0 artifact (R18 tooling)
- [ ] AGENTS.md D24 draft → **awaiting user sign-off** (proposal in branch notes; do NOT edit AGENTS.md without approval)
- [ ] (optional) Full DSN sole-coverage-for-a-path analysis finalization (primary work lives in Phase 1)

## 4. Open decisions / staffing notes

- **Model substitution (user decision, 2026-08-12):** DeepSeek V4 Flash will execute all phases marked
  for **DeepSeek or GLM** in §13.3 (free on Cline). Kimi K3 remains primary for design/gate-sensitive phases
  (1, 3, 5, 6, 9). §13 staffing table to be updated to reflect this at next plan edit.
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

## 6. Next actions

1. Confirm v2.3.0 golden metrics path (R18) — capture into §12.6.
2. User sign-off on AGENTS.md D24 draft (Phase 0/1).
3. Begin **Phase 1** (inventory + ArchUnit freezes): simple-name collisions (three `CoordinateTransform`s, `InteractiveCommand` flatten), DSN sole-coverage finalization, facade sketch (R19), Swing-test/EDT inventories, ArchUnit rules + §12.4 freezes.
4. Update §13.3 model-cols for the Flash-substitution decision.

## 7. Artifacts

- Plan: `docs/issues/soc-gui-separation-and-accessibility-plan.md`
- This tracker: `docs/issues/soc-gui-separation-and-accessibility-tracker.md`
- Temp working notes (git-ignored): `logs/phase0/branch-notes.md`, `logs/phase0/gradle_test1.log`, `logs/phase0/gradle_test_full.log`

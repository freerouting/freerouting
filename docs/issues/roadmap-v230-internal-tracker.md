# Freerouting v2.3 Release Plan

> **Public tracker:** [GitHub Issue #649](https://github.com/freerouting/freerouting/issues/649)  
> **Revised:** 2026-08-04 — post manual QA, issue closures, Python client publish, and #649 checklist update  
> **Version in tree:** `2.2.5-SNAPSHOT` (GA target: `2.3.0`)

---

## Release Status

**Phase:** Pre-RC — feature work is essentially complete; remaining items are integration sign-off, i18n polish, manual GUI QA, and the release cut.

| Gate | Status |
|---|---|
| Core routing features (fanout, bend cost, DRC stats, edge clearance CLI) | ✅ Done |
| MCP + A2A | ✅ Done |
| KiCad IPC / JSON pipeline (in-repo) | ✅ Implemented — final sign-off pending |
| Python API client | ✅ Published |
| Unit test audit | ✅ Done (#649 checked) |
| Optimizer verification + docs | ✅ Done (#649 checked) |
| SoC / InteractiveCommand | ✅ Done (#649 checked) |
| Manual GUI testing | 🔲 Remaining |
| i18n missing strings + template consistency | 🔲 Remaining |
| TODO resolution | ✅ Done (3 intentional TODO locations remain, within ≤10 target) |
| RC tag + GA cut | 🔲 Not started |

---

## Decisions (updated 2026-08-04)

| Question | Decision | Notes |
|---|---|---|
| Copper Plane 152-A | **Not a v2.3 gate** | Ship with known-bug caveat → v2.4 backlog |
| Star Ground (#383) | **Deferred to v2.4** | Not on #649 checklist; no implementation started; issue stays open |
| KiCad IPC API | **In scope** | Primary path for KiCad 9/10; supersedes DSN-only fix for #558 |
| Multi-threading | **Deferred** | v2.5+ |
| Code modernization + package rename | **Deferred** | Struck through on #649 — too disruptive pre-release |
| Community beta period | **Deferred** | Struck through on #649 — proceed to GA after internal QA + `./gradlew check` |
| #558 copper-to-edge (DSN path) | **Mitigated, not fully closed** | CLI `copperToEdgeClearanceUm` + IPC JSON carry full rules; KiCad DSN export gap remains upstream |

---

## Completed Work (mapped to #649)

### Routing Engine & Performance ✅

| Item | Evidence |
|---|---|
| Better SMD support (`BatchFanout`, `withFanout`, escape-length enforcement) | `BatchFanout.java`, `SmdPinFanoutRoutingTest`; bm05 nightly: 22 unrouted vs v1.9 37 |
| Single-sided fabrication / bend cost (#156) | `BendCostRoutingTest`, `MazeSearchAlgo`, UI in `WindowAutorouteParameter` |
| Copper-to-edge clearance support (#558 mitigation) | `copperToEdgeClearanceUm` in `RouterSettings`, `DevBoardClearanceRoutingTest` |
| `BoardStatistics` full DRC | Uses `DesignRulesChecker.getAllClearanceViolations()` |
| Layer visibility / virtual display layers (#713) | `WindowSelectParameter` KiCad-style layer panel |

### API & Integrations ✅ (mostly)

| Item | Evidence |
|---|---|
| MCP server + A2A Agent Card (#566, #588, #589) | `McpControllerV1`, `docs/API/MCP.md` |
| KiCad IPC JSON — Phases 1–3 (in-repo) | PR #765; `KiCadJsonReader`/`Writer`, plugin `router_ipc.py`, `integrations/KiCad/TESTING.md` |
| Python API client update | Published externally (`freerouting-python-client`); #649 checked |

### Architecture, QA & Docs ✅

| Item | Evidence |
|---|---|
| SoC — `InteractiveCommand` pattern | `interactive/commands/InteractiveCommand.java` |
| Unit test audit | #649 checked; 2 `@Disabled` parity tests documented in `Issue733DsnJsonParityTest` |
| Optimizer verification | #649 checked |
| Contextual i18n translations | PRs #748, #769 |
| TODO audit | Stale TODOs removed; 3 locations kept (`BaseController` auth, `GuiBoardManager` thread coupling, `GlobalSettings` migration hook) |

### Recently Closed Issues (manual verification, 2026-08-03/04)

| Issue | Title |
|---|---|
| [#742](https://github.com/freerouting/freerouting/issues/742) | Orientation decimal point omitted in SES export → fixed (#746) |
| [#754](https://github.com/freerouting/freerouting/issues/754) | StackOverflowError opening 6-layer PCB |
| [#753](https://github.com/freerouting/freerouting/issues/753) | FreeRouter stalling / version sensitivity |
| [#756](https://github.com/freerouting/freerouting/issues/756) | Hang when opening DSN files |
| [#757](https://github.com/freerouting/freerouting/issues/757) | StackOverflowError opening a DSN file |

---

## Remaining Before GA

These are the **only open items** on [#649](https://github.com/freerouting/freerouting/issues/649) still unchecked:

### 1. KiCad Integration Upgrade — final sign-off (~0.5 day)

Code is merged (PR #765). Remaining work is confirmation, not implementation:

- [ ] Run plugin unit tests: `integrations/KiCad/kicad-freerouting/tests/`
- [ ] Manual smoke test: KiCad 10 IPC mode (serialize → route → apply)
- [ ] Manual smoke test: DSN fallback on KiCad without IPC
- [ ] Check off #649 item once both paths verified

### 2. i18n — Missing Strings (~1 day)

- [ ] Scan `src/main/resources/**/*.properties`; diff non-English locales against `*_en.properties`
- [ ] Fill gaps (LLM-assisted translations with codebase context)
- [ ] Optional stretch: Gradle CI task to fail on missing keys (roadmap item 18 — not required for GA if manual audit is clean)

### 3. i18n — Template Consistency (~0.5 day)

- [ ] Verify `{0}`, `{1}` placeholder usage is consistent across all locale files
- [ ] Fix any mismatched placeholder counts

### 4. Manual GUI Testing (~1–2 days)

Test list from original Sprint 3 plan; document results in `logs/v23-gui-test-results.md`:

- [ ] Router settings panel — all fields round-trip correctly after DSN load
- [ ] Single-step routing mode — step forward/backward works
- [ ] Bend cost per-layer input in autoroute parameters
- [ ] Layer panel — color swatch, eye toggle, active layer, virtual layers (Silk/CY/Fab)
- [ ] KiCad plugin — IPC mode indicator and progress dialog
- [ ] DSN load on previously failing boards (6-layer, large hierarchies)

### 5. Release Cut (~1 day)

- [ ] `./gradlew check` green (fast + slow tests)
- [ ] `compare-versions.ps1` on bm01, bm05, bm07, bm08 — confirm no new clearance regressions
- [ ] Update `docs/architecture.md`: KiCad JSON/IPC layer, `BatchFanout`, MCP; update `docs/settings.md` for new router fields
- [ ] Bump `ext.publishInfo.versionId` in `gradle/project-info.gradle` → `2.3.0`
- [ ] `./gradlew executableJar` + platform installers
- [ ] Tag `v2.3.0`, publish GitHub release + SNAPSHOT assets
- [ ] Close #649

**Estimated remaining effort: ~4–5 developer-days → GA within one focused week.**

---

## Known Limitations (document in release notes, not blockers)

| Item | Status | User-facing guidance |
|---|---|---|
| [#558](https://github.com/freerouting/freerouting/issues/558) DSN copper-to-edge | Open (upstream KiCad DSN gap) | Use KiCad IPC mode (KiCad 9/10) or `--router.copperToEdgeClearanceUm=500` for DSN-only |
| [#383](https://github.com/freerouting/freerouting/issues/383) Star ground | Deferred v2.4 | Manual star-point + lock workflow unchanged |
| [#152](https://github.com/freerouting/freerouting/issues/152) Copper plane routing | Deferred v2.4 | Plane nets may introduce clearance violations (`interf_u.dsn`: 62 violations) |
| SMD bm05 full completion | Improved, not perfect | 22 unrouted (nightly) vs v1.9 37; tracked in `docs/issues/smd-pin-fanout-routing.md` |
| DSN/JSON parity | 2 tests `@Disabled` | IPC path is primary; parity gaps documented in `Issue733DsnJsonParityTest` |
| bm01 / bm07 routing | Minor regressions vs v1.9 on some nightly runs | Monitor; not a GA gate if `./gradlew check` passes |

---

## Deferred to Post-v2.3

| Item | Target | Reason |
|---|---|---|
| Star Ground (#383) | v2.4 | Not started; opt-in feature |
| Code modernization | v2.4+ | Struck through on #649 |
| Package reorganization | v2.4+ | Struck through on #649 |
| Community beta period | Skipped | Owner decision on #649 |
| Copper Plane 152-A | v2.4 | High risk, root cause unknown |
| Multi-threaded routing | v2.5+ | Architecture not thread-safe |
| Bidirectional maze search | v2.4+ | Major `MazeSearchAlgo` rewrite |
| i18n CI key-check Gradle task | v2.4 | Nice-to-have after manual audit |

---

## Sprint History (archived — for reference)

<details>
<summary>Sprint 1–4 original schedule (2026-05-14 plan)</summary>

### Sprint 1 — Routing Correctness ✅

- BoardStatistics DRC fix, SMD BatchFanout, copperToEdgeClearanceUm, layer visibility panel (#713)

### Sprint 2 — API, Integrations & Features 🟡

- MCP/A2A ✅, KiCad IPC ✅, Bend Cost ✅, Star Ground ❌ (deferred)

### Sprint 3 — Quality & Polish 🟡

- Unit test audit ✅, optimizer docs ✅, SoC ✅, contextual i18n ✅
- TODO audit ✅, missing i18n 🔲, GUI testing 🔲

### Sprint 4 — Release 🔲

- Code modernization ❌ (deferred), beta ❌ (skipped), architecture docs 🔲, RC/GA cut 🔲

</details>

---

## Benchmark Snapshot (2026-08-03 nightly)

See [`scripts/benchmark/results/benchmarks.md`](../../scripts/benchmark/results/benchmarks.md) for full tables.

**Current (`s2026.08.03`) wins vs historical best:**

| Fixture | Unrouted | Violations | Score | Notes |
|---|---:|---:|---:|---|
| bm05 | 22 | 0 | 785 | Best unrouted count across all versions |
| bm06 | 2 | 8 | 963 | Better than v1.9 (8 unrouted) |
| bm08 | 0 | 1 | 1000 | Best score; 1 violation (DRC now surfaces real issues) |
| bm11 | 2 | 0 | 987 | Best violations (v1.9 had 17) |

**Watch items:** bm01 (5 unrouted vs 2.2.4's 0), bm07 (3 unrouted vs v1.9's 0). Re-run compare-versions before tagging GA.

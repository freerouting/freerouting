# Freerouting v2.3 Release Plan

> **Public tracker:** [GitHub Issue #649](https://github.com/freerouting/freerouting/issues/649)
> **Revised:** 2026-08-06 (PM) — IPC improvement sprint; clearance gap documented; i18n + TODO complete
> **Version in tree:** `2.2.5-SNAPSHOT` (GA target: `2.3.0`)

---

## Release Status

**Phase:** IPC improvement sprint → then final QA → RC cut.

| Gate | Status |
|---|---|
| Core routing (fanout, bend cost, DRC stats, edge clearance CLI) | ✅ Done |
| MCP + A2A | ✅ Done |
| KiCad JSON/API pipeline | 🟡 **Experimental** — [`kicad-json-api-mode-improvement-tracker.md`](kicad-json-api-mode-improvement-tracker.md) |
| Python API client | ✅ Published |
| i18n (contextual, missing strings, templates) | ✅ Done |
| TODO resolution | ✅ Done — 3 intentional TODOs remain |
| Unit test audit + optimizer docs | ✅ Done |
| Manual GUI testing | 🔲 Remaining |
| RC tag + GA cut | 🔲 Not started |

---

## Decisions (updated 2026-08-06)

| Question | Decision | Notes |
|---|---|---|
| KiCad JSON/API mode | **Ship as experimental in v2.3** | In-repo JSON/API path exists; **DSN is the default**; JSON/API is opt-in. True KiCad protobuf IPC → v2.4. |
| Star Ground (#383) | **Deferred to v2.4** | See analysis below; issue stays open |
| bm01 / bm07 benchmark regressions | **Accepted for v2.3** | Minor unrouted-count drift vs v1.9 on nightly runs; not a GA gate |
| Copper Plane 152-A | **Not a v2.3 gate** | Known-bug caveat → v2.4 |
| #558 copper-to-edge | **Partial** | CLI override ✅; DSN export gap upstream; **IPC JSON does not read KiCad edge clearance yet** |
| Code modernization + package rename | **Deferred** | Struck through on #649 |
| Community beta | **Skipped** | Proceed to GA after internal GUI QA |

---

## Completed (#649 checklist)

### Routing, API, Integrations

- Better SMD support, single-sided / bend cost (#156), copper-to-edge CLI (#558 mitigation)
- MCP + A2A (#566, #588, #589)
- Python API client update
- KiCad IPC/JSON **code landed** (PR #765) — release as experimental, not GA-complete integration

### Quality, i18n, maintenance

- TODO resolution ✅ (3 remaining, intentional):
  - `GlobalSettings.java:321` — per-version migration hook placeholder
  - `GuiBoardManager.java:2730` — thread should receive board+settings only
  - `BaseController.java:66,73` — email lookup + auth endpoint (API security gap, tracked)
- i18n: contextual translations, missing strings, template consistency ✅
- Unit test audit ✅
- Optimizer verification + docs ✅
- SoC / `InteractiveCommand` ✅

### Recently closed (manual verification)

| Issue | Title |
|---|---|
| [#742](https://github.com/freerouting/freerouting/issues/742) | Orientation decimal in SES export |
| [#754](https://github.com/freerouting/freerouting/issues/754) | StackOverflowError on 6-layer PCB |
| [#753](https://github.com/freerouting/freerouting/issues/753) | Stalling / version sensitivity |
| [#756](https://github.com/freerouting/freerouting/issues/756) | Hang opening DSN |
| [#757](https://github.com/freerouting/freerouting/issues/757) | StackOverflowError opening DSN |

---

## Today's focus: JSON/API mode (2026-08-06)

Full backlog: [`kicad-json-api-mode-improvement-tracker.md`](kicad-json-api-mode-improvement-tracker.md)

**#558 blocker in JSON/API path:** KiCad exposes edge clearance via `board.GetDesignSettings().m_CopperEdgeClearance`, but the plugin hardcodes `outline.clearance: 0.5` and `KiCadJsonReader` ignores it (`outlineClearanceNo = 1`). **P0 fix:** read → JSON → apply `board_edge` class.

| Priority | Task | Est. |
|---|---|---|
| P0 | Wire `m_CopperEdgeClearance` through plugin + Java reader | 2–3 h |
| P0 | Verify netclass / custom DRU clearance import | 1–2 h |
| P1 | Issue733 DSN/JSON parity — fix top diffs | 3–4 h |
| P2 | Plugin: experimental label; DSN default | ✅ Done |

**Milestone #10 open:** [#649](https://github.com/freerouting/freerouting/issues/649) (tracker), [#558](https://github.com/freerouting/freerouting/issues/558) (edge clearance — JSON/API fix in progress), [#729](https://github.com/freerouting/freerouting/issues/729) (config save — unrelated).

---

## KiCad Integration — Experimental in v2.3

**What shipped (PR #765):**

- In-repo: `KiCadJsonReader`/`Writer`, REST endpoints for JSON job I/O, DRC on JSON boards
- Plugin: dual-mode (`router_json_api.py` + `router_dsn.py`); DSN default, JSON/API opt-in
- Plugin unit tests under `integrations/KiCad/kicad-freerouting/tests/`

**Why experimental, not GA-ready:**

- **Clearance settings not imported from KiCad** — edge clearance hardcoded; #558 unsolved on JSON/API path (P0)
- DSN/JSON board parity gaps (`Issue733DsnJsonParityTest` — 2 tests `@Disabled`)
- JSON/API path not yet validated end-to-end on enough real KiCad 9/10 boards
- True KiCad protobuf IPC is GUI-only until KiCad 11 (see [Issue-real-kicad-ipc-migration.md](Issue-real-kicad-ipc-migration.md))
- ✅ Plugin defaults to DSN; JSON/API is opt-in via `ROUTING_MODE_JSON`

**v2.3 release messaging:**

- **Default / recommended:** DSN mode (unchanged workflow, battle-tested)
- **Experimental:** JSON/API mode — opt-in on KiCad 9/10; report issues
- **#649 item:** Check off as "delivered experimental" once release notes and plugin UI label are updated

**v2.4 backlog:**

- Fix DSN/JSON parity gaps; re-enable parity tests
- Manual QA matrix (KiCad 9 + 10, JSON/API + DSN, large/complex boards)
- Migrate to real KiCad protobuf IPC (Issue-real-kicad-ipc-migration)
- Document known JSON/API limitations in `integrations/KiCad/TESTING.md` and KiCad plugin README

---

## Star Ground (#383) — Feature Primer

> Full analysis: [`docs/issues/Issue383-star-ground-routing.md`](Issue383-star-ground-routing.md)

### What the reporter wants (issue body, @Dapid, 2024-11)

Star ground is a PCB design practice where every ground return connects to **one common point** via independent traces — no shared ground paths between sub-circuits. Today the workflow is:

1. Manually route each GND pin to a chosen star center
2. Lock those traces
3. Run the autorouter for everything else

The pain: the star center location is a guess. If a different point would work better globally, the designer must redo step 1. The request is for Freerouting to **choose the star center and enforce radial topology automatically**.

Comments on the issue are sparse — mostly stale-bot cycles with @Dapid confirming **"Still valid"** (2025-03, 2025-07, 2025-11, 2026-03). No design discussion beyond the original report. Community interest is steady but low-volume.

### Why Freerouting can't do this today

The autorouter builds a **minimum spanning tree** per net (`MazeSearchAlgo`). For GND with N pads it naturally daisy-chains or buses — the opposite of a star. There is no topology constraint.

### Recommended implementation (v2.4, Option B from analysis)

Opt-in via `RouterSettings.starGroundNetNames` (empty by default → zero regression risk):

```
1. StarGroundPlanner.computeStarCenter(net) → Point (centroid of pins, snapped to valid via site)
2. Insert a hub via at the star center before routing
3. Split net into N two-terminal sub-nets: (pin_i ↔ hub) using existing fromto/subnet machinery
4. Route normally — each arm is independent
5. Optimizer guard — skip trace merges that would collapse radial arms back into a daisy chain
```

**Reuse points:** `rules.Net.subnet_number`, DSN `(fromto ...)` parsing in `Network.read_net_scope()`, existing subnet routing in `BatchAutorouter`.

**Hard parts:**

- Virtual hub item must survive `BoardHistory` restores
- Star center placement affects routability of other nets
- Optimizer must not undo the topology post-route
- Interaction with copper pours (`contains_plane`) — star ground is mostly an analog/low-frequency concern; may need to skip plane nets
- KiCad plugin UI to designate star-ground nets (no DSN-level constraint today)

**Effort estimate:** ~10 developer-days (was Sprint 2 in original roadmap).

---

## Remaining Before GA

### 1. Manual GUI testing (~1–2 days)

Only unchecked item on #649. Document in `logs/v23-gui-test-results.md`:

- [ ] Router settings panel round-trip after DSN load
- [ ] Single-step routing forward/backward
- [ ] Bend cost per-layer input
- [ ] Layer panel (color swatch, eye toggle, virtual layers)
- [ ] KiCad plugin — DSN mode (primary) + experimental IPC label/warning
- [ ] DSN load on previously failing boards (6-layer, large hierarchies)

### 2. Release cut (~1 day)

- [ ] `./gradlew check` green
- [ ] Update release notes: IPC experimental, DSN recommended, known limitations (#558, #152, bm05 partial)
- [ ] Mark KiCad plugin JSON/API mode as experimental in UI/docs
- [ ] Bump `gradle/project-info.gradle` → `2.3.0`
- [ ] `./gradlew executableJar` + platform installers
- [ ] Tag `v2.3.0`, publish GitHub release
- [ ] Close #649

**Estimated remaining: ~2–3 developer-days.**

---

## Known Limitations (release notes)

| Item | Guidance |
|---|---|
| KiCad JSON/API mode | Experimental — DSN is default for production work |
| [#558](https://github.com/freerouting/freerouting/issues/558) Edge clearance | DSN: use `--router.copperToEdgeClearanceUm=500`. JSON/API: fix in progress (read `m_CopperEdgeClearance`) |
| [#383](https://github.com/freerouting/freerouting/issues/383) Star ground | Manual lock-and-route workflow unchanged → v2.4 |
| [#152](https://github.com/freerouting/freerouting/issues/152) Copper plane routing | May introduce clearance violations on plane nets |
| SMD bm05 | 22 unrouted (nightly) vs v1.9 37 — improved, not perfect |
| bm01 / bm07 | Minor unrouted regressions vs v1.9 — **accepted** |

---

## Benchmark Snapshot (accepted baselines)

See [`scripts/benchmark/results/benchmarks.md`](../../scripts/benchmark/results/benchmarks.md).

| Fixture | Current (`s2026.08.03`) | Notes |
|---|---|---|
| bm05 | 22 unrouted, 0 violations | Best unrouted across versions |
| bm06 | 2 unrouted | Better than v1.9 (8) |
| bm08 | 0 unrouted, score 1000 | Best score |
| bm11 | 2 unrouted, 0 violations | Best violations |
| bm01 | 5 unrouted | Accepted regression vs 2.2.4 (0 unrouted) |
| bm07 | 3 unrouted | Accepted regression vs v1.9 (0 unrouted) |

---

## Post-v2.3 Backlog (v2.4 focus)

1. **KiCad IPC** — parity fixes, QA, promote from experimental to default
2. **Star Ground (#383)** — Option B sub-net expansion (see primer above)
3. **Copper plane 152-A** — clearance violation investigation
4. **SMD bm05** — remaining 22 unrouted connections
5. Code modernization + package rename (if ever)

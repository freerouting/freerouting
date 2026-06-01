# Freerouting v2.3 Roadmap — Finalized Prioritization & Sprint Schedule

> **Source:** [GitHub Issue #649](https://github.com/freerouting/freerouting/issues/649)  
> **Revised:** 2026-05-14 — incorporating owner decisions on all open questions.

---

## Decisions Incorporated

| Question | Decision | Impact |
|---|---|---|
| Copper Plane 152-A | Not a v2.3 gate — ship with known-bug caveat | 152-A moves to v2.4 investigation backlog |
| KiCad IPC API | In scope for v2.3 | Partially supersedes DSN-path fix for #558; IPC bypass eliminates the format limitation |
| Multi-threading | Deferred | Removed from v2.3 scope entirely |
| Star Ground (#383) | Keep in v2.3 — it's opt-in so regression risk is low | Added to Sprint 2 |
| i18n + cleanup | Handle ourselves | Scheduled in Sprint 3 |

---

## Final Priority Order

| # | Item | Importance | Difficulty | Risk | Est. Days | Sprint |
|---|---|---|---|---|---|---|
| 1 | Fix `BoardStatistics` DRC (use `getAllClearanceViolations`) | ★★★★★ | ★ | ★ | 1 | 1 |
| 2 | SMD: Fix `Pin.is_obstacle()` same-net via bug | ★★★★★ | ★ | ★ | 1 | 1 |
| 3 | SMD: Add `RoutingBoard.get_smd_pins()` | ★★★★★ | ★ | ★ | 0.5 | 1 |
| 4 | SMD: Implement `BatchFanout` class (port from v1.9) | ★★★★★ | ★★★★ | ★★★ | 4 | 1 |
| 5 | SMD: `withFanout` setting + integrate into `BatchAutorouter` | ★★★★★ | ★★ | ★★ | 2 | 1 |
| 6 | Edge Clearance: `copperToEdgeClearanceUm` in `RouterSettings` | ★★★★ | ★★ | ★ | 1 | 1 |
| 7 | Edge Clearance: Apply in `HeadlessBoardManager` + update test | ★★★★ | ★★★ | ★★ | 2 | 1 |
| 8 | MCP Server endpoints | ★★★★ | ★★★ | ★ | 3 | 2 |
| 9 | A2A Agent Card (`agent.json`) | ★★★★ | ★ | ★ | 1 | 2 |
| 10 | KiCad IPC API — Phase 1 (board read via IPC) | ★★★★ | ★★★★ | ★★ | 6 | 2 |
| 11 | KiCad IPC API — Phase 2 (route result write back via IPC) | ★★★★ | ★★★ | ★★ | 4 | 2 |
| 12 | Python client update | ★★★ | ★★ | ★ | 2 | 2 |
| 13 | Star Ground Routing (#383) — Phases 1–5 | ★★★ | ★★★★ | ★★ (opt-in) | 10 | 2 |
| 14 | Single-Sided / Bend Cost (#156) | ★★★ | ★★★ | ★★ | 5 | 2 |
| 15 | Unit test audit | ★★★ | ★★ | ★ | 2 | 3 |
| 16 | TODO resolution | ★★ | ★★ | ★★ | 3 | 3 |
| 17 | Optimizer benchmarking + docs | ★★★ | ★★ | ★ | 2 | 3 |
| 18 | i18n: missing strings + template consistency | ★★ | ★★ | ★ | 3 | 3 |
| 19 | i18n: contextual translations (LLM workflow) | ★★ | ★★ | ★ | 2 | 3 |
| 20 | Manual GUI testing | ★★★★ | ★★★ | ★ | 4 | 3 |
| 21 | SoC: Command Pattern for `InteractiveState` + ArchUnit boundary enforcement | ★★★ | ★★★★ | ★★★ | 10 | 3 |
| 22 | Code modernization + package rename | ★★ | ★★★ | ★★★ | 5 | 4 (last) |
| 23 | Community beta testing | ★★★★ | ★ | ★ | 2 effort (+2wk cal) | 4 |
| 24 | Architecture docs update (`docs/architecture.md`) | ★★★ | ★ | ★ | 1 | 4 |
| — | **Copper Plane 152-A** (via clearance violations) | ★★★★★ | ★★★★★ | ★★★★★ | 15–25 | **v2.4** |
| — | **Multi-threaded routing** | ★★★ | ★★★★★ | ★★★★★ | 30–60 | **v2.5+** |

**Total estimated v2.3 effort: ~76–82 developer-days (~16 calendar weeks solo)**

---

## Sprint 1 — "Routing Correctness" (Weeks 1–3, ~12 days)

**Goal:** Fix the two most damaging correctness bugs — SMD routing failure and edge clearance misreporting. Both have clear, low-risk implementations and dedicated test suites.

### Day 1 — Foundation fixes (zero regression risk)
- **Fix `BoardStatistics`** to call `DesignRulesChecker.getAllClearanceViolations()` instead of `board.get_outline().clearance_violation_count()`.  
  → Affects: `core/scoring/BoardStatistics.java`  
  → Impact: Surfaces real clearance violations in tests (incl. Issue #152, #558). May cause some existing tests to fail — that's intentional; they were passing for the wrong reason.

### Days 2–8 — SMD Pin Fanout
- Fix `Pin.is_obstacle()` (one line, remove `!via.attach_allowed` guard in the same-net branch).
  → Validate alone: `./gradlew test -Dtest=Dac2020Bm05RoutingTest,SmdPinFanoutRoutingTest`
- Add `RoutingBoard.get_smd_pins()` (port from v1.9 `BasicBoard`).
- Implement `BatchFanout.java` in `app.freerouting.autoroute/`:
  - Up to 20 passes, escalating ripup costs.
  - Uses `StoppableThread` (headless-safe, no `InteractiveActionThread`).
  - `FRLogger.info` per pass; `FRLogger.trace` per pin attempt.
- Add `public Boolean withFanout` to `RouterSettings`; default `true` in `DefaultSettings`.
- Integrate into `BatchAutorouter.autoroute_passes()` (pre-pass guard: `withFanout && !board.get_smd_pins().isEmpty()`).

**Exit gate:** All `SmdPinFanoutRoutingTest` and `Dac2020Bm05RoutingTest` tests pass. `compare-versions.ps1` on bm05: current ≥ v1.9 routing completion, 0 new clearance violations.

### Days 9–11 — Edge Clearance (DSN-path fix)

> [!NOTE]
> The KiCad IPC API (Sprint 2) will ultimately bypass the DSN format limitation that causes Issue #558. However, the `copperToEdgeClearanceUm` CLI parameter is still valuable for non-KiCad DSN users and for CI test validation. Implement it now; it will remain useful even after IPC.

- Add `public Double copperToEdgeClearanceUm` to `RouterSettings` (nullable, no default initializer) and set the base default to **500 µm (0.5 mm)** in `DefaultSettings`.
- After board load in `HeadlessBoardManager`, if non-null: create `"board_edge"` clearance class, populate matrix, update `BoardOutline` clearance class, re-insert into search tree.
- Update `DevBoardClearanceRoutingTest` to inject a **non-default** `copperToEdgeClearanceUm` value and assert the correct threshold is applied.

### Day 12 — Sprint 1 validation
- `./gradlew check` — full suite must be green.
- Run `compare-versions.ps1` on bm01, bm05, bm07, bm08.
- Document any newly surfaced violations from the `BoardStatistics` DRC fix as known issues.

---

## Sprint 2 — "API, Integrations & New Features" (Weeks 4–9, ~26 days)

**Goal:** Deliver the strategic integrations (MCP, IPC) and the two new routing features (Star Ground, Bend Cost).

### Days 13–15 — MCP Server & A2A Protocol
- Expose existing REST API endpoints as MCP tools. Use OpenAPI spec to generate JSON Schema for each tool's input/output.
- Run MCP as a **dedicated server** with its own `mcp_server` settings block (`enabled`, `endpoints`, auth, CORS, target API base URL).
- Support MCP realtime channels in v2.3: SSE (`/v1/mcp/events`) and WebSocket (`/v1/mcp/ws`) in addition to JSON-RPC (`/v1/mcp`).
- Serve `/.well-known/agent.json` (A2A Agent Card) as a static endpoint from the Jetty server.
- Add MCP endpoint documentation to Swagger UI.
- Update Python client: sync with any new API endpoint signatures.

**Deliverable:** `curl https://api.freerouting.app/.well-known/agent.json` returns a valid A2A card.

### Days 16–25 — KiCad IPC API Integration
KiCad's IPC API is gRPC-based and replaces the Specctra DSN file-exchange model. This eliminates the root cause of Issue #558 (copper-to-edge clearance not exported in DSN) for all KiCad users.

We will use a **Hybrid Local Loopback Bridge** approach to avoid native Unix Domain Sockets (UDS) or Named Pipes in Java. The KiCad Python plugin acts as the bridge, connecting to KiCad IPC natively and communicating with Freerouting's REST API over localhost HTTP.

**Phase 1 — Board read via IPC & JSON Loader (Days 16–21):**
- Define the **KiCad JSON schema** for board data (layers, nets, pads, tracks, vias, zones, rules).
- Implement `KiCadJsonReader` in Freerouting to deserialize the JSON stream into a `RoutingBoard`.
- Measure and log the performance penalty of JSON serialization/deserialization to evaluate overhead and aid in debugging.
- Implement a new API endpoint `PUT /v1/sessions/{sessionId}/monitor` to set an API session as the **"currently monitored" session**.
- If the Freerouting GUI is enabled, bind the monitored session's board and real-time routing progress to the active GUI visualizer.
- Unit tests using a mock JSON payload.

**Phase 2 — Route result write back via IPC & Streaming API (Days 22–25):**
- Expose routed traces and vias in a JSON format via the REST API.
- Use streaming API endpoints (SSE/WebSockets) to send real-time progress and incremental updates.
- Python bridge receives updates and writes them back to KiCad via KiCad IPC.

**Exit gate:** A KiCad 9 board with a non-default copper-to-edge clearance routes correctly via IPC without any CLI `copperToEdgeClearanceUm` override needed, and progress is displayed on the GUI.

### Days 26–35 — Star Ground Routing (#383)

The feature is **opt-in** (`RouterSettings.starGroundNetNames` defaults to empty list). Existing boards route identically without it. This is the key reason regression risk is acceptable for v2.3.

**Phase 1 — Settings (Day 26):**
- Add `public List<String> starGroundNetNames` to `RouterSettings`.
- Initialize to empty list in `DefaultSettings`.
- Expose in `docs/settings.md`.

**Phase 2 — StarGroundPlanner utility (Days 27–28):**
- New class `app.freerouting.autoroute.StarGroundPlanner`.
- `computeStarCenter(net, board)`: returns `Point` (geometric centroid of net pins, snapped to nearest valid via location).
- User override: if a pad exists at the computed location, use it; otherwise create a zero-clearance-overhead via.

**Phase 3 — Virtual Hub Item (Days 29–30):**
- Insert a shove-fixed via at the star center as the hub before routing begins.
- The hub via must survive `BoardHistory` restores; tag it with a `STAR_GROUND_HUB` flag.
- Remove (or keep as a real via) after routing completes.

**Phase 4 — Sub-net Expansion (Days 31–32):**
- In `BatchAutorouter.runBatchLoop()`, if any net is a star-ground net:
  - Create N 2-terminal sub-nets: `(real_pin_i, hub_via)` for each pin.
  - Remove the original monolithic net from the queue.
  - Route normally (the existing `fromto` / subnet path handles the rest).

**Phase 5 — Optimizer Guard (Day 33):**
- In `BatchOptRoute`/`OptViaAlgo`: if a trace belongs to a sub-net whose parent net is in `starGroundNetNames`, skip trace-consolidation moves that would merge two radial arms.

**Days 34–35 — Tests:**
- Add a synthetic 4-pin star-ground fixture DSN.
- `StarGroundRoutingTest`: assert each pin connects to the hub via (not to each other), 0 clearance violations, 0 incomplete connections.
- Verify that `withFanout = false` on a non-star board is unaffected.

### Days 36–38 — Single-Sided Fabrication / Bend Cost (#156)

- Add `public Double bendCostFactor` to `RouterSettings` (nullable, default `0.0` in `DefaultSettings`).
- In `MazeSearchAlgo`, when `bendCostFactor > 0`: add `bendCostFactor * trace_width` to the expansion cost whenever a trace segment changes direction by more than a configurable threshold (default: 45°).
- Document in `docs/settings.md`; expose via CLI and API.
- Test: a simple L-shaped board where `bendCostFactor = 1.0` produces fewer turns on the restricted layer vs. `bendCostFactor = 0`.

---

## Sprint 3 — "Quality & Polish" (Weeks 10–13, ~26 days)

**Goal:** Test coverage, cleanup, and user-facing polish before the release candidate.

### Unit Test Audit (Days 39–40)
- Enumerate all `@Disabled` tests; restore or delete with documented rationale.
- Identify tests that pass for wrong reasons (like old `DevBoardClearanceRoutingTest`); fix or annotate.
- Verify `./gradlew check` is green with no skipped/suppressed test.

### TODO Resolution (Days 41–43)
- `./gradlew grep` / IDE search for `// TODO` across all `src/main/`.
- Categorize: fix immediately, create a GitHub issue, or delete if obsolete.
- Target: ≤ 10 TODOs remaining in `src/main/` (only those tracking future enhancements).

### Optimizer Verification & Documentation (Days 44–45)
- Run `BatchOptRoute` before and after on 3 benchmark boards; measure: trace length delta, via count delta, runtime overhead.
- Update `docs/architecture.md` and `README.md` with findings.
- If improvement is negligible on small boards, document a minimum board size recommendation.

### i18n Audit (Days 46–50)
- Scan all `src/main/resources/**/*.properties` files; identify keys present in `en_US` but missing in other locale files.
- Add missing keys with LLM-assisted translations (provide codebase context for accurate terminology).
- Audit template strings (`{0}`, `{1}` substitution) — verify consistent use across all locales.
- Add a CI check (custom Gradle task) that fails if any non-English locale is missing a key present in `en_US`.

### SoC — Command Pattern for InteractiveState (Days 51–60)
- Extract each `InteractiveState` subclass (`RouteState`, `DragState`, `StitchRouteState`, etc.) into a Command-pattern structure:
  - `Command` interface: `execute()`, `undo()`, `canExecute()`.
  - Each state becomes a `Command` with its execution logic decoupled from `GuiBoardManager`.
- This enables `undo/redo` support and makes GUI testing with Mockito feasible.
- Guard: all new Command classes must not reference `interactiveSettings` directly; use `getInteractiveSettings()` with null-guard.
- Add/maintain ArchUnit module-boundary tests and freeze baselines for legacy SoC debt:
  - `src/test/java/app/freerouting/architecture/ModuleBoundariesArchTest.java`
  - `src/test/resources/archunit.properties`
  - `src/test/resources/archunit_store/`
- Keep `docs/issues/Architecture-boundary-debt-tracker.md` updated with rule status (strict vs frozen), representative violations, and remediation steps.

### Manual GUI Testing (Days 61–64)
- Test list (all new GUI components):
  - [ ] Router settings panel — all fields round-trip correctly after DSN load.
  - [ ] Single-step routing mode — step forward/backward works; undo restores state.
  - [ ] MCP server status indicator in the GUI (if any).
  - [ ] KiCad IPC mode indicator in the plugin.
  - [ ] Star Ground net selector (if exposed in GUI).
  - [ ] Bend Cost slider / input field.
- Document results in `logs/v23-gui-test-results.md`.

---

## Sprint 4 — "Beta & Release" (Weeks 14–16)

### Code Modernization + Package Rename (Days 65–69)

> [!WARNING]
> Do this **after** all feature branches are merged. A rename commit on a live branch causes merge conflicts everywhere.

- Standardize variable/method names to Java camelCase throughout.
- Rename packages per the planned reorganization (confirm scope with a search for any public API usages that would be breaking changes).
- Update all `import` statements, Javadoc, and `docs/architecture.md`.
- Run `./gradlew rewriteRun` to apply project-wide formatting recipes.

### Release Candidate & Beta Testing (Days 70–75 + 2 calendar weeks)
- Cut the RC build: `./gradlew executableJar` → tag `v2.3-rc1`.
- Reach out to power users (frequent GitHub issue reporters, tscircuit maintainers, KiCad forum).
- Collect feedback; triage into "must fix for GA" vs. "v2.4 backlog".
- Final regression: `./gradlew check`, `compare-versions.ps1` on bm01, bm05, bm07, bm08.

### Architecture Docs Update (Day 76)
- Update `docs/architecture.md` Mermaid diagram: new packages, IPC layer, MCP server, `BatchFanout`, `StarGroundPlanner`.
- Update `docs/settings.md`: `withFanout`, `copperToEdgeClearanceUm`, `bendCostFactor`, `starGroundNetNames`.
- Update `docs/developer.md`: Python client release workflow.
- Update architecture-boundary references and debt tracker links:
  - `docs/architecture.md` (module boundaries + ArchUnit test entry points)
  - `docs/issues/Architecture-boundary-debt-tracker.md`

---

## What Is Explicitly Out of Scope for v2.3

| Item | Reason | Target |
|---|---|---|
| Copper Plane 152-A (via clearance violations) | Root cause unidentified; fix risk too high | v2.4 investigation backlog |
| Multi-threaded routing | Not thread-safe architecture; 30–60 day effort | v2.5+ |
| Bidirectional maze search | Major MazeSearchAlgo rewrite | v2.4+ |
| Copper void/island detection | Long-term future work | TBD |

---

## Calendar Summary (Solo Developer Estimate)

| Sprint | Calendar Weeks | Key Deliverable |
|---|---|---|
| Sprint 1 | 1–3 | SMD routing fixed; edge clearance improved; DRC stats correct |
| Sprint 2 | 4–9 | MCP/A2A live; KiCad IPC integration; Star Ground + Bend Cost |
| Sprint 3 | 10–13 | Full test suite green; i18n complete; GUI polish |
| Sprint 4 | 14–16 (+2 beta) | RC build; beta feedback; docs updated; GA release |

**Total: ~16–18 calendar weeks from Sprint 1 start.**
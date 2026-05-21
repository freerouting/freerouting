# Project Persona & Goal

You are a Senior Java Engineer specialized in Computational Geometry and EDA (Electronic Design Automation). Your core mission on this project is advanced PCB auto-routing. You possess deep knowledge of algorithms, data structures, spatial optimization, and performance tuning necessary for managing complex routing spaces.

# Tech Stack & Environment

- **Language:** Java 25
- **Build System:** Gradle 9
- **Key Dependencies:** 
  - Jetty (embedded server)
  - Jersey (JAX-RS for API)
  - Log4j (logging)
  - Gson, Jakarta EE APIs, Google Cloud & Sheets APIs, Swagger/OpenAPI.
- **Python Client Library:** The project ecosystem includes a Python client library for headless interactions and REST API integrations; it is maintained outside this repository (release workflow reference: `docs/developer.md`).
- **EDA Integrations:** In-repo integration assets exist under `integrations/` for KiCad, Autodesk EAGLE, Target3001!, and EasyEDA; `README.md` and `docs/integrations.md` also document tscircuit and pcb-rnd workflows.

# Architecture & Standards

- **Architecture Reference:** The authoritative architecture overview lives in [`docs/architecture.md`](docs/architecture.md). It includes a Mermaid diagram of the full system, a package glossary, and navigation guidance. **Keep this file up-to-date whenever structural changes are made** — for example, when packages are added or reorganised, when a new interface (GUI mode, API version, CLI flag) is introduced, or when the boundary between the routing pipeline and the orchestration layer shifts. The in-repo diagram is the first document contributors read; a stale diagram misleads more than no diagram at all.
- **Domain Orientation:** This is an **algorithmic-heavy** project. The complexity lies in spatial data structures, pathfinding (e.g., modified A*, Lee algorithm, maze routing), and geometric calculations.
- **Separation of Concerns:** The **UI/Visualizer** and the **Routing Engine** are distinct domains. Always maintain a strict boundary between visual representation and core algorithmic logic. UI concerns should not bleed into the geometric models.
- **Module Boundary Enforcement:** Architectural package boundaries are enforced by ArchUnit tests in `src/test/java/app/freerouting/architecture/ModuleBoundariesArchTest.java` and `src/test/java/app/freerouting/io/SpecctraPackageArchTest.java`. Keep strict rules green. For known legacy boundary debt, use frozen rules (`FreezingArchRule`) and never relax strict rules to hide regressions.
- **Repository Package Boundaries:** Keep routing/data logic in `src/main/java/app/freerouting/{autoroute,board,geometry,drc,core,rules}`; keep UI/editor flow in `src/main/java/app/freerouting/{gui,interactive,boardgraphics}`; keep REST/API server concerns in `src/main/java/app/freerouting/{api,management}`; keep file-format I/O in `src/main/java/app/freerouting/io/{specctra,specctra/parser}` — the public entry points live in `io.specctra` and grammar internals in `io.specctra.parser`.
- **Coding Standards:** Adhere strictly to Clean Code principles and standard Java naming conventions (e.g., CamelCase for classes/methods). Prioritize readability and maintainability without sacrificing the algorithmic performance. 
- **Legacy Reference Implementation:** The source code of the original v1.9 implementation is available in the `src_v19/` directory. It serves as a reference and benchmark baseline that we can comare against when refactoring or optimizing routing logic. Use it to understand the original algorithmic decisions and to ensure that any new implementation maintains or improves upon the original performance and correctness. The source code should be modified only when more gradual trace logging is needed to understand the original algorithm's behavior in specific scenarios. Do not refactor or optimize the v1.9 code directly; instead, use it as a reference for the current development branch.
- **Logging & Debugging:** Use the `FRLogger` class for logging. The method `trace(String method, String operation, String message, String impactedItems, Point[] impactedPoints)` should be used for detailed algorithmic steps, especially in routing logic, to facilitate debugging and performance analysis. Logs should be structured and informative, including impacted nets and impacted points in the routing process. These logs should be maintained in both the current implementation and the v1.9 reference to allow for side-by-side comparisons when analyzing routing behavior and performance. You can leave the trace log method calls in place in the v1.9 code and in the current code as well to help future developers understand the routing process and identify any regressions or improvements in the new implementation.
  - For parity investigations, keep diagnostic payloads synchronized between current and v1.9. If current emits a debug marker (for example `[assign_raw]` with section/door identity), add the same marker fields to v1.9 before drawing conclusions from log diffs.
- **`InteractiveSettings` / GUI Session State:** `InteractiveSettings` extends `GuiSettings` (which implements `SettingsSource` at priority 50) and is the **sole** live source of GUI state in the `SettingsMerger` pipeline. Key invariants:
  - It is a **singleton within a GUI session**. Always obtain it via `InteractiveSettings.getOrCreate(board)` and reset it on every board load with `InteractiveSettings.reset(board)`.
  - It must **never** be referenced from `HeadlessBoardManager` or any `api`/`management` code path. `HeadlessBoardManager.getInteractiveSettings()` always returns `null` by design.
  - `InteractiveSettings.getSettings()` returns a **live snapshot** of the current GUI state and is what `SettingsMerger` reads at priority 50. Do not cache the result; always call `merger.merge()` to get an up-to-date `RouterSettings`.
  - All fields in `InteractiveSettings` (own and inherited) are `private`; external access must go through getters/setters so that `PropertyChangeEvent`s fire correctly.
- **`BoardManager` Class Hierarchy:** `HeadlessBoardManager` is the headless/API base; `GuiBoardManager` extends it and adds all GUI concerns (Swing panels, `InteractiveSettings`, serialisation of `.frb` binary files). Code that must work in both modes lives in `HeadlessBoardManager`; code that requires a display or user interaction lives in `GuiBoardManager`.
- **`SettingsMerger.addOrReplaceSources(SettingsSource)`:** When registering a `GuiSettings`-subtype source (e.g., the `InteractiveSettings` singleton), pass the concrete instance — the merger uses subtype matching to find and replace any existing `GuiSettings` entry at priority 50. Never register a plain `GuiSettings` instance after the singleton has been registered or it will silently shadow the live GUI state.

# Specific Constraints & Logic

- **Safety First:** Be *extremely careful* when modifying the routing algorithms. Even minor changes can lead to severe regressions in trace optimization, clearance violations, or routing completion rates.
- **Regressions Prevention:** Before refactoring any core routing logic, you **must** verify your changes against the existing test suite to prevent trace regressions. Always run reproduction tests on actual PCB design files (`.dsn`) if an issue is reported (see `src/test/java/app/freerouting/fixtures/RoutingFixtureTest.java` and fixtures in `fixtures/`).
- **Baseline Performance:** The original v1.9 implementation serves as a performance and correctness baseline. Any new implementation should aim to match or exceed the routing quality and efficiency of the v1.9 version. 
  - To compare performance, use the script `scripts/tests/compare-versions.ps1` which runs both versions on a PCB design and outputs key metrics.
  - TRACE level logs are written to `logs/` folder with `freerouting-v190.log` and `freerouting-current.log` filenames for easy comparison.
  - Log files can become huge, so use the script's filtering capabilities to focus on specific nets (`-DebugFilterByNet` command line argument), limit routing passes (`max_passes`) or items (`max_items`) when analyzing performance differences between the two versions.
  - Use PowerShell commands to extract and compare relevant log entries for specific nets or routing steps to identify where the new implementation may be diverging from the v1.9 baseline in terms of routing decisions, trace optimizations, or clearance handling.
  - For deterministic parity work, locate the first meaningful mismatch in normalized routing streams (for example `RAW_SECTION` selection records) and treat that position as the investigation anchor for both versions.
  - Classify divergence before fixing: distinguish numeric-only drift from behavioral ordering/tie-break divergence by suppressing volatile values (for example `expansion_value` and `sorting_value`) and comparing decision continuity.
  - Preferred remediation sequence: (1) synchronize instrumentation payloads in current and v1.9, (2) diff around the first normalized mismatch with stable identifiers (section, door, from_door, net), (3) apply the smallest possible ordering/tie-break fix in current (for example in `expand_to_door_section(...)` path), (4) rerun comparisons to confirm the mismatch moves later or disappears without introducing violations.
  - Exit criteria for parity investigations: no new clearance violations, no regression in routing completion, and stable or improved compare-versions metrics across repeated runs and at least two `max_items` checkpoints.
- **Algorithm Performance Metrics:** When optimizing routing algorithms, focus on key performance metrics such as:
  - **Clearance Violations:** (Critical priority) Ensure that no routing changes introduce new clearance violations.
  - **Routing Completion Rate:** (High priority) The percentage of successfully routed nets.
  - **Execution Time:** (Medium priority) Aim to reduce the time taken for routing without compromising the above metrics.
  - **Memory Usage:** (Medium priority) Ensure that any optimizations do not lead to excessive memory consumption, especially for large PCB designs.
    - **Interpreting router memory log lines:** The phrase `"the job allocated X GB of memory so far"` is the **cumulative total** of all JVM heap allocations since the job started (a GC throughput metric). It grows monotonically regardless of GC activity and is **not** the current heap size. The authoritative live-memory figure is `"peak heap usage: Y MB"` printed at the end of the session. When investigating memory reports from users, always ask for or measure peak heap, not cumulative allocated.
    - **Expected memory profile for a multi-pass routing job:** Working-set typically grows in a staircase pattern (each pass plateau, then a GC trim) up to a board-size-dependent peak, then drops sharply when the routing thread pool winds down and releases per-pass board state. The optimizer phase that follows normally runs at a significantly lower and flat memory footprint. Sustained growth *during* the optimizer (not routing) is the signal that indicates a genuine GC-root retention leak.
  - **Trace Length Optimization:** (Low priority) The total length of traces should be minimized while respecting design rules.
- **Testing & Validation:** Always write comprehensive unit tests for any new routing logic or optimizations. Use the existing test suite as a reference and ensure that all tests pass before merging changes. For any new features or optimizations, add specific test cases that validate the expected behavior and performance improvements.
  - **Running Tests:** If you implement a small change you can run only one unit test to do a quick check, preferably the `Issue508Test_BM01_first_2_nets` which is one of the quickest routing test. Use `./gradlew test` for the default (fast) unit-test set and `./gradlew check` for the full integration testing suite.
  - **Slow-test tagging policy:** Tag long-running fixture/benchmark tests with `@Tag("slow")`. The default `test` task excludes these tests unless explicitly enabled (`-PincludeSlowTests=true`). Use `./gradlew testSlow` to run only slow tests, and `./gradlew testAll` to run both fast + slow sets before releases or merge-critical validation.
  - **Timeout budget:** The Gradle unit-test task timeout is **30 minutes** to accommodate fanout-enabled routing fixtures on slower hardware.
  - **Large-board CI tests:** Boards with >500 nets can take several minutes per routing pass. Use `TestingSettings.setMaxItems(n)` (e.g. 100–200) to slice off a bounded chunk of work that runs in under 30 seconds while still exercising the target code path. Do **not** rely on a short `jobTimeoutString` alone — the timeout fires after the pass completes, so a single slow pass can still blow the budget.
  - **Full-scale OOM / stress tests** that cannot be bounded to CI time belong in `scripts/tests/` as standalone PowerShell scripts (see `run_test_Issue420_oom.ps1` as the reference pattern). These scripts build the executable JAR, run it headlessly with `-XX:+HeapDumpOnOutOfMemoryError`, sample the JVM working-set every 30 s via a `Start-Job` background sampler, and print a pass/fail summary with the memory trend at the end.
- **GUI vs Headless Guard:** Before calling any method that accesses `interactiveSettings`, always check `getInteractiveSettings() != null` or restrict the call to `GuiBoardManager` only. Shared `interactive`-package code (e.g., routing states like `RouteState`, `DragState`) may access `hdlg.interactiveSettings` directly — ensure those code paths are only reachable when `hdlg` is a `GuiBoardManager` instance.
- **Test Placement Conventions:**
  - Issue-regression and full-pipeline tests → `src/test/java/app/freerouting/fixtures/` (extend `RoutingFixtureTest`).
  - Unit/integration tests scoped to a specific package → place in the matching test package (e.g., tests for `app.freerouting.interactive` go in `src/test/java/app/freerouting/interactive/`).
  - DSN fixture files live in `fixtures/`; reference them by filename (e.g., `"Issue508-DAC2020_bm01.dsn"`). The quickest fixture for smoke-checks is `Dac2020Bm01RoutingTest`.
  - Bound long-running routing tests with `TestingSettings.setMaxPasses(n)`, `setMaxItems(m)`, and `setJobTimeoutString("HH:MM:SS")` to keep CI fast.
- **Issue Tracking:** Detailed per-issue specifications live in `docs/issues/`. Each file documents the problem, sub-issues (with ✅ when done), proposed/actual implementation, and acceptance criteria. Keep these files up-to-date as sub-issues are resolved so future agents have accurate context without re-reading the full conversation history.
  - Architecture boundary debt is tracked in `docs/issues/Architecture-boundary-debt-tracker.md`. Update it whenever frozen ArchUnit baselines change or when a frozen rule is promoted to strict.
  - Temporary analysis artifacts (draft GitHub replies, one-off log extracts, heap-dump notes) should be written to `logs/<IssueNNN>/` — this directory is git-ignored and will not clutter the repository.
- **Licensing:** This project is open-source under the **GPLv3** license. Ensure all dependencies and contributions respect this license.

# Settings & Configuration System

Router configuration is resolved at runtime by `SettingsMerger`, which layers nine `SettingsSource` implementations in ascending priority order (0 = defaults → 70 = API). The merger calls `RouterSettings.applyNewValuesFrom(source)`, which uses `ReflectionUtil.copyFields()` to copy a field only when the source value is **non-null and not the Java language default** for that type.

This design has one critical invariant: **all fields in `RouterSettings` (and its nested `RouterOptimizerSettings` / `RouterScoringSettings`) must be nullable reference types with no default initializers.** If a field were initialised to a non-null value (e.g. `public Integer maxPasses = 9999;`), every source object would carry that value and the merger could no longer distinguish "this source sets this field" from "this source has no opinion". A low-priority source would then silently override a higher-priority one. All hardcoded defaults belong exclusively in `DefaultSettings.getSettings()`, which is always applied first as the base layer.

The full priority ladder is documented in `docs/settings.md`. Key sources: `DefaultSettings` (0), `JsonFileSettings` (10), `DsnFileSettings` (20), `SesFileSettings` (30), `RulesFileSettings` (40), `GuiSettings` (50), `EnvironmentVariablesSource` (55), `CliSettings` (60), `ApiSettings` (70).

- **Copper-to-edge default:** `RouterSettings.copperToEdgeClearanceUm` defaults to **500.0 µm (0.5 mm)** in `DefaultSettings`. Keep the field nullable (no initializer in `RouterSettings`) so higher-priority sources can still override it cleanly.
- **Override-test guidance:** When writing tests for the edge-clearance override path, set `copperToEdgeClearanceUm` to a **non-default** value so the test verifies source precedence/override behavior, not just default propagation.

# Workflow Commands

Execute the following commands from the root directory using the Gradle Wrapper:

- **Run Tests (fast/default):** `./gradlew test`
- **Run Slow Tests Only:** `./gradlew testSlow`
- **Run Fast + Slow Tests:** `./gradlew testAll`
- **Run Full Verification Suite:** `./gradlew check`
- **Build the Executable JAR:** `./gradlew executableJar` (Find the result in `build/libs/freerouting-current-executable.jar`)
- **Build Both Current + v1.9 Executables:** `./gradlew buildBothVersions`
- **Run Current Development Environment:** `./gradlew run`
- **Run v1.9 Compatibility Build:** `./gradlew runV19`
- **Apply Project-Wide Cleanup/Formatting Recipes:** `./gradlew rewriteRun`

# Communication Style

Your communication should be direct, professional, and technically precise. Acknowledge and respect the inherent complexity of PCB routing logic. Do not oversimplify geometric problems; instead, provide thorough, algorithmically-sound justifications for any proposed code changes. Output complete and correct code when finalizing solutions.

- **Spelling:** The product name is always written **"Freerouting"** (capital F). Never write "freerouting" in prose, documentation, or user-facing messages.

# DRC & Clearance Architecture

Key facts about how design-rule checking and clearances work — important context for any issue investigation:

- **Internal DRC entry point:** `DesignRulesChecker.getAllClearanceViolations()` is the comprehensive DRC method that iterates all board item pairs. Always use this when you need a complete violation count. The shortcut `board.get_outline().clearance_violation_count()` (currently used in `BoardStatistics`) only checks violations from the `BoardOutline`'s perspective and is incomplete.
- **`BoardStatistics.clearanceViolations.totalCount` is currently incomplete:** It calls `board.get_outline().clearance_violation_count()` instead of `DesignRulesChecker.getAllClearanceViolations()`. Do not treat this count as a definitive "no violations" signal without understanding this limitation (see Issue 558).
- **A passing test is not always correct:** A routing fixture test that asserts `clearanceViolations.totalCount == 0` may pass because the internal DRC is checking the wrong threshold, not because the routing is actually correct. Always verify that the DRC is checking the same clearance values that the final EDA tool (e.g. KiCad) will check.
- **KiCad DSN export does not include copper-to-edge clearance:** KiCad's "copper to board edge clearance" setting is **not written into the Specctra `.dsn` file**. Freerouting therefore assigns the `BoardOutline` the default conductor-to-conductor clearance class and routes traces at that (smaller) distance from the board edge. KiCad's own DRC will flag violations after import. The Specctra format supports `(clearance_class ...)` inside `(boundary ...)`, and freerouting already parses it — the fix requires KiCad to start emitting it. See `docs/issues/Issue558-copper-to-edge-clearance.md` for the full analysis and workaround plan.
- **Board outline clearance class:** The `BoardOutline` item's clearance class is set during `HeadlessBoardManager.create_board()` from `p_outline_clearance_class_name`. When this is `null` (the KiCad case), it falls back to `ItemClass.AREA` = class 1 = "default". The `ShapeSearchTree` then uses this class's compensation value to determine how close routing can approach the board edge.

# Copper Pour / Power Plane Architecture

Key facts about how copper pours (power/ground planes) are modelled and routed — established during the Issue 152 investigation:

- **Model:** Copper pours are represented as `board.ConductionArea` (extends `ObstacleArea`, implements `Connectable`). A `ConductionArea` belongs to exactly one net (e.g. GND) and occupies one PCB layer.
- **Obstacle flag:** `ConductionArea.is_obstacle` controls whether the area blocks foreign-net traces. When `false` (the default for fills) foreign traces may pass through the pour geometrically, which is the normal behaviour for a poured plane. Use `RoutingBoard.change_conduction_is_obstacle(boolean)` to toggle this in bulk; note that the method is only reachable from `GuiBoardManager`, not headless code.
- **Plane-net flag:** `rules.Net.contains_plane` is the critical flag that switches the autorouter into plane-routing mode for a given net. When `true`, `BatchAutorouter.autoroute_item()` routes *from* the already-connected group *toward* the `ConductionArea` (short stub + via pattern), rather than routing pad-to-pad. An early-exit `CONNECTED_TO_PLANE` state is returned as soon as the connected set already touches a `ConductionArea`.
- **Two independent paths set `contains_plane`:**
  - **Path A — `Network.java` (reliable, fires for standard KiCad exports):** At net-creation time, `LayerStructure.contains_plane(netName)` is called. If the DSN `structure` section includes explicit plane layer-type information (KiCad's `(plane <netname> ...)` declaration triggers this), the flag is set immediately. Live tests confirm this works correctly for outer-layer pours (B.Cu, F.Cu) and inner-layer pours (In1.Cu).
  - **Path B — `DsnFile.adjustPlaneAutorouteSettings()` (heuristic fallback):** Only invoked when the DSN has no `(autoroute ...)` scope. Uses a ≥ 50% board-area threshold and skips outer layers (index 0 and last). This latent outer-layer guard is a bug for non-KiCad DSN files, but Path A fires first for well-formed KiCad exports so it is not normally encountered.
- **Via cost discount for plane nets:** When `contains_plane` is `true`, `BatchAutorouter.autoroute_item()` passes `settings.get_plane_via_costs()` (cheaper than the regular via cost) to `AutorouteControl`. This incentivises dropping vias onto the plane rather than routing traces across the board.
- **Via optimisation for plane-connected vias:** `OptViaAlgo.opt_plane_or_fanout_via()` handles the post-routing via repositioning for the stub-to-plane case. It verifies the new location is inside the `ConductionArea` before moving.
- **`getAutorouteItems()` quadratic false-work (partially fixed):** `BasicBoard.connectable_item_count()` counts `ConductionArea` as a connectable item. For a GND net with N pads + 1 pour, every pad whose `connected_set` has fewer than N+1 members is enqueued — even those already touching the plane. **Fix applied:** `BatchAutorouter.getAutorouteItems()` now skips items that are already connected to a `ConductionArea` for plane nets (i.e., `net.contains_plane() && connected_set.anyMatch(ConductionArea)`). Items not yet connected to the plane are still enqueued so they can drop a via onto the pour. This eliminates the repeated `normalize_traces` failure (which arose because `InsertFoundConnectionAlgo` was called for those false-work items) and ensures `autoroute_pass()` returns `false` once all plane-net items are connected — letting the routing loop exit cleanly.
- **Confirmed bug — plane routing introduces clearance violations (Issue 093):** Routing `Issue093-interf_u.dsn` (bottom-copper GND pour) with the current code introduces **62 clearance violations** and logs an internal error in `BatchAutorouter.autoroute_pass`. The plane-routing code path is active when this occurs. This is a safety-critical open bug tracked in `docs/issues/Issue152-copper-pour-plane-awareness.md`.
- **No pour connectivity / void detection:** If a foreign-net trace cuts through a pour layer, creating an isolated copper island, Freerouting does **not** detect the disconnected region. The `RatsNest` and the exported `.ses` file will show the net as fully routed even though part of the pour is electrically floating. This is long-term future work.
- **Loading boards to check plane flags without routing:** Use `DsnReader.readBoard(InputStream, BoardObservers, IdentificationNumberGenerator, String)` directly when you only need to inspect the loaded board state (e.g. verify `Net.contains_plane`) without running the routing scheduler. This is faster and avoids timeout issues in tests.

# API Analytics Architecture

Key facts about how API usage analytics are tracked — established during the analytics tracking investigation.

## Pipeline overview (three hops)

1. **API controller** (`JobControllerV1`, `SessionControllerV1`, `SystemControllerV1`) calls `FRAnalytics.apiEndpointCalled(method, request, response, userId)` on every **successful (2xx) response**.
2. `FRAnalytics.trackAnonymousAction` builds a `Properties` map and calls `FreeroutingAnalyticsClient.track(...)`, which **POSTs** a JSON `Payload` asynchronously (background thread) to `https://api.freerouting.app/v1/analytics/track`.
3. `AnalyticsControllerV1.trackAction` on the server receives that POST and writes the row to **BigQuery** via the singleton `BigQueryClient`, into the table `freerouting-analytics.freerouting_application.api_endpoint_called`. The table name is derived from the event name by lowercasing and replacing spaces/hyphens with underscores.

## Key invariants

- **`FRAnalytics.permanent_user_id` is always `null` in headless/API mode.** `FRAnalytics.setUserId()` is only called from the GUI startup path. Always pass the per-request `userId` UUID from `AuthenticateUser()` to the 4-arg overload `FRAnalytics.apiEndpointCalled(method, request, response, userId)` so that callers can be identified in BigQuery. Never call `FRAnalytics.setUserId()` from a request-handling thread — it writes static state shared across all concurrent requests (race condition).
- **`FRAnalytics.apiEndpointCalled` has two overloads.** The 3-arg overload (no userId) is kept for unauthenticated endpoints (e.g. `GET /v1/system/status`) where no caller UUID is available. All authenticated controller methods must use the 4-arg overload, passing the return value of `AuthenticateUser()`.
- **Error responses (4xx/5xx) are tracked by `ApiAnalyticsFilter`**, not by the controller methods. This JAX-RS `ContainerResponseFilter` (registered in `FreeroutingApplication`) fires for every response with status ≥ 400 — including 401 Unauthorized responses aborted by `ApiKeyValidationFilter` before the controller runs. Successful (2xx) responses are intentionally skipped by the filter to avoid double-tracking with the richer controller-level calls.
- **`BigQueryClient` is a singleton.** Use `BigQueryClient.getInstance(libraryVersion, serviceAccountKey)` — never `new BigQueryClient(...)` — in `AnalyticsControllerV1`. Constructing a new instance on every request re-authenticates against GCP's token endpoint (network I/O). The singleton is recreated automatically if the service-account key value changes (key rotation).
- **`FreeroutingAnalyticsClient` delivery failures are aggregated, not logged per-failure.** Calling `FRLogger.debug/warn` inside the async send thread floods logs when the analytics endpoint is unreachable. Instead, failures are routed to `AnalyticsErrorAggregator.recordFailure(endpoint, e)`. The first failure in each window is logged immediately at WARN; subsequent failures are counted silently. A daemon thread (`"analytics-error-reporter"`) flushes a per-error-type summary every 60 minutes: at WARN if total ≤ 50, at ERROR if > 50 (sustained outage).

## BigQuery table and column conventions

- All analytics events land in `freerouting-analytics.freerouting_application.<event_name>` where the table name is the event string lowercased with spaces/hyphens replaced by underscores (e.g. `"API Endpoint Called"` → `api_endpoint_called`).
- Standard columns present on all rows: `id`, `received_at`, `sent_at`, `timestamp`, `loaded_at`, `uuid_ts`, `user_id`, `anonymous_id`, `event`, `event_text`, `context_library_name`, `context_library_version`.
- API-call-specific columns: `api_method` (e.g. `"GET v1/jobs/…"`), `api_request`, `api_response`.
- `user_id` and `anonymous_id` are both set to the caller's `Freerouting-Profile-ID` UUID for authenticated API calls. Both will be `null` for unauthenticated endpoints (system status, environment) and for any legacy events fired before fix #1 was applied.

## Verifying analytics in BigQuery

```sql
-- Check recent API events are arriving
SELECT received_at, anonymous_id, api_method, context_library_version
FROM `freerouting-analytics.freerouting_application.api_endpoint_called`
WHERE received_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 HOUR)
ORDER BY received_at DESC LIMIT 50;

-- Call count per endpoint (last 24 h)
SELECT api_method, COUNT(*) AS calls
FROM `freerouting-analytics.freerouting_application.api_endpoint_called`
WHERE received_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 24 HOUR)
GROUP BY api_method ORDER BY calls DESC;

-- Confirm null-user-id volume (should be ~0 after fix #1)
SELECT
  COUNTIF(user_id IS NULL OR user_id = '') AS null_user_ids,
  COUNT(*) AS total
FROM `freerouting-analytics.freerouting_application.api_endpoint_called`
WHERE received_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY);

-- Error-response breakdown by endpoint (tracked by ApiAnalyticsFilter)
SELECT api_method, COUNT(*) AS error_count
FROM `freerouting-analytics.freerouting_application.api_endpoint_called`
WHERE received_at >= TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 7 DAY)
  AND (api_response LIKE '%error%' OR api_response LIKE '%not found%')
GROUP BY api_method ORDER BY error_count DESC;
```

## Relevant source files

| File | Role |
|---|---|
| `management/analytics/FRAnalytics.java` | Static facade; `apiEndpointCalled(…, UUID userId)` is the primary call site |
| `management/analytics/FreeroutingAnalyticsClient.java` | HTTP client posting to `api.freerouting.app/v1/analytics/track` |
| `management/analytics/AnalyticsErrorAggregator.java` | Aggregates delivery failures; emits first-failure WARN + hourly summary |
| `management/analytics/BigQueryClient.java` | Singleton GCP BigQuery writer; `getInstance()` avoids per-request re-auth |
| `api/ApiAnalyticsFilter.java` | JAX-RS dual filter; tracks all ≥ 400 responses centrally |
| `api/FreeroutingApplication.java` | Registers `ApiAnalyticsFilter` alongside existing filters |

# MCP Server Architecture

Key facts and invariants for the dedicated MCP server implementation.

## Server separation and settings

- Freerouting runs MCP as a **separate server** with its own settings block: `mcp_server`.
- `mcp_server` has independent lifecycle flags and network config: `enabled`, `running`, `http_allowed`, `endpoints`, `cors_origins`.
- MCP authentication config is independent from REST API config: `mcp_server.authentication`.
- MCP tools execute against the REST API using `mcp_server.target_api_base_url`.

## Protocol surface (v2.3)

- Public discovery endpoint: `GET /.well-known/agent.json` (A2A Agent Card).
- JSON-RPC endpoint: `POST /v1/mcp` (`initialize`, `tools/list`, `tools/call`).
- Realtime channels: `GET /v1/mcp/events` (SSE) and `GET /v1/mcp/ws` (WebSocket).
- Tool inventory is generated from OpenAPI (`/openapi/openapi.json`) and exposes nearly all `/v1/*` routes except MCP routes themselves.

## Target API guard

- `mcp_server.target_api_base_url` must point to the REST API base URL.
- It must never point to MCP routes (for example `/v1/mcp` or `/.well-known/*`), otherwise tool calls are rejected with a configuration error.

## Authentication and headers

- MCP request authentication uses the same header conventions as API requests:
  - `Authorization: Bearer <API_KEY>` when auth is enabled.
  - `Freerouting-Profile-ID` (or `Freerouting-Profile-Email`).
  - `Freerouting-Environment-Host` in `<ToolName>/<Version>` format.
- Keep API and MCP authentication paths independent; changing one must not silently change the other.

## Operational hardening invariants

- API and MCP both support configurable fixed-window rate limits:
  - `api_server.rate_limit`
  - `mcp_server.rate_limit`
- MCP and REST responses include `X-Correlation-ID` for request tracing.
- MCP tool bridge must forward `X-Correlation-ID` to underlying REST calls so logs can be cross-linked.

## Required docs updates when MCP changes

- Update `docs/API/MCP_setup.md` with concrete startup, verification, and troubleshooting steps.
- Update `docs/API/API_v1.md` MCP endpoint tables/examples.
- Update `docs/settings.md` whenever `mcp_server` fields change.
- Update `docs/architecture.md` if package boundaries or data flow changes.

# Docker Multi-Platform Build

The Docker images are built and published by two GitHub Actions workflows:

- `.github/workflows/docker-release.yml` — triggered on every GitHub release; tags the image with the semver version and `latest`.
- `.github/workflows/docker-nightly.yml` — triggered on every push to `master`; tags the image as `nightly`.

Both workflows build a multi-platform manifest image using Docker Buildx + QEMU. The supported platforms are:

| Platform | Hardware |
|---|---|
| `linux/amd64` | x86-64 servers, PCs, most cloud VMs |
| `linux/arm64` | Apple Silicon, AWS Graviton, Raspberry Pi 4/5 (64-bit OS) |

> **Note:** `linux/arm/v7` (32-bit ARM) support was dropped when the project moved to Java 25, because `eclipse-temurin:25` no longer publishes an `arm/v7` image. Raspberry Pi users running a 32-bit OS should switch to a 64-bit OS image to use the official Docker image.

`provenance: false` is set on both workflows so that the manifest does not contain build-attestation entries (which show up as `"architecture": "unknown"` in `docker manifest inspect`).

The Gradle tests are run on the native `ubuntu-latest` runner **before** the multi-platform build step to avoid QEMU network/loopback unreliability inside the cross-compiled build container.

For end-user self-hosting instructions see `docs/self-hosting.md`.

# Installer / jlink Module Requirements

The Windows, Linux, and macOS installers use `jlink` to build a minimal bundled JRE. The `--add-modules` list must include every JDK module that the application (or its bundled libraries) accesses at runtime — `jlink` does **not** auto-detect usages from inside a fat jar.

## Correct `--add-modules` list per platform

| Platform | Required modules |
|---|---|
| **Windows** | `java.desktop,java.logging,java.management,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.crypto.mscapi,jdk.management` |
| **Linux** | `java.desktop,java.logging,java.management,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.management` |
| **macOS** | `java.desktop,java.logging,java.management,java.net.http,java.sql,java.xml,jdk.crypto.ec,jdk.management` |

> `jdk.crypto.mscapi` is **Windows-only** (provides `SunMSCAPI` for the Windows OS certificate store). Do not add it to Linux/macOS scripts.

## Why each module is needed

- **`java.management`** — `ManagementFactory` (used in `SystemControllerV1`, `BatchAutorouterThread`, `RoutingJobSchedulerActionThread`, Log4j2 startup, Jersey-server). Missing this module causes `NoClassDefFoundError: java/lang/management/ManagementFactory` on startup and crashes the `/v1/system/status` endpoint with HTTP 500.
- **`jdk.management`** — `com.sun.management.ThreadMXBean.getThreadAllocatedBytes()` and `com.sun.management.OperatingSystemMXBean.getCpuLoad()`. Transitively brings in `java.management`.
- **`jdk.crypto.ec`** — EC crypto provider (`SunEC`) required for TLS 1.3 / ECDHE key exchange. Without it, all HTTPS connections to Google APIs (OAuth2, BigQuery, Sheets) fail at the SSL handshake.
- **`jdk.crypto.mscapi`** — Windows native certificate store access. Without it, Java uses only its own bundled `cacerts`, which is usually sufficient but may miss system-level certificates.

## Modules explicitly NOT needed

- `java.instrument` — javassist references it but HK2/Jersey proxy generation uses `ClassLoader.defineClass()`, not bytecode redefining. Do not add.
- `jdk.attach` / `jdk.jdi` — javassist debugging tools. Not needed for runtime proxy generation. Do not add.
- `jdk.httpserver` — referenced in jersey-server module-info but Freerouting uses Jetty, not the JDK HTTP server. Do not add.
- `java.naming` — log4j-core requires it `static` (optional) for JNDI lookups; Freerouting does not use JNDI lookups. Do not add. Instead, `System.setProperty("log4j2.disableJndi", "true")` is set at the very start of the Log4j2 property-setup block in `Freerouting.main()` (before `LogManager.getContext()` is first called) so Log4j2 skips its JNDI plugin registration entirely and emits no WARN.
- `java.rmi` — log4j-core requires it for remote JMX; not needed for local embedded logging. Do not add.

## Defensive coding pattern for `ManagementFactory`

All `ManagementFactory` call sites are wrapped in `try/catch (Throwable)` so the application degrades gracefully (returns -1 / skips stats) rather than crashing when running on a custom or stripped JRE:

```java
try {
    ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    // ...use threadMXBean...
} catch (Throwable t) {
    // java.management or jdk.management module not available in this JRE build
}
```

Affected files: `SystemControllerV1.getCpuLoad()`, `BatchAutorouterThread.captureStats()`, `RoutingJobSchedulerActionThread.monitorCpuAndMemoryUsage()`.

## Analysing module requirements

To check what JDK modules a jar requires, use:
```bash
jdeps --ignore-missing-deps -q --print-module-deps <jar-file>
```
For the project's own thin jar (without bundled deps):
```bash
jdeps --ignore-missing-deps -q --print-module-deps build/libs/freerouting.jar
```
To inspect a named module's `requires` declarations:
```bash
jar --describe-module --file <jar-file> | grep requires
```

# API Authentication Filter Architecture

## `ApiKeyValidationFilter` bypass bug (Issue 650) ✅ Fixed

**What was wrong:** `ApiKeyValidationFilter` checked for the `Authorization: Bearer` header and immediately rejected with 401 if the header was absent — **before** calling `ApiKeyValidationService.validateApiKey()`. This meant even when `authentication.enabled=false`, requests without an `Authorization` header were rejected.

**Fix applied:** After `ApiKeyValidationService.getInstance()`, skip all validation when authentication is disabled:

```java
ApiKeyValidationService validationService = ApiKeyValidationService.getInstance();
if (!validationService.isAuthenticationEnabled()) {
    return; // skip all validation
}
// ...then check for the header...
```

**`ApiKeyValidationService.validateApiKey(apiKey)` already returns `true` when `isEnabled == false`** — the bug was entirely in the filter's early-exit before that call was reached.

## Secure-by-default configuration (since Issue 650 fix)

Two defaults were hardened:

| Setting | Old default | New default | Reason |
|---|---|---|---|
| `ApiAuthenticationSettings.isEnabled` | `false` | **`true`** | Auth off + world-wide bind was a security risk |
| `ApiServerSettings.endpoints` | `https://0.0.0.0:37864` | **`http://127.0.0.1:37864`** | Localhost-only by default; `https://` was misleading (HTTPS not implemented, fell back to HTTP silently) |

**For local EDA plugin use (KiCad, EasyEDA):** The server already binds to `127.0.0.1` only, so network-level exposure is eliminated. Authentication should be explicitly disabled for seamless plugin operation:

```
java -jar freerouting-executable.jar --gui.enabled=false --api_server.enabled=true --api_server.authentication.enabled=false
```

**For remote/cloud deployments:** Keep `authentication.enabled=true` (the default) and configure a provider (e.g. GoogleSheets). To expose to a network interface, explicitly set `--api_server.endpoints=http://0.0.0.0:37864`.

# GitHub Actions CI Architecture

Key facts and invariants for all GitHub Actions workflows under `.github/workflows/`.

## Workflow inventory

| File | Trigger | Purpose |
|---|---|---|
| `gradle-build-on-pr.yml` | `pull_request` | Build + test on Ubuntu, macOS, Windows |
| `create-snapshot.yml` | push to `master` | Build all platform installers and publish to the `SNAPSHOT` release |
| `create-release.yml` | push of `v*` tag | Build all platform installers and publish to the versioned release |
| `docker-nightly.yml` | push to `master` | Build + push multi-arch Docker image tagged `nightly` |
| `docker-release.yml` | GitHub release published | Build + push multi-arch Docker image tagged with semver + `latest` |
| `deploy-pages.yml` | push to `master` (paths: `website/**`) | Deploy static website to GitHub Pages |
| `stale.yml` | daily schedule | Mark inactive issues/PRs stale after 120 days |
| `gemini-dispatch.yml` | PR/issue/comment events | Route `@gemini-cli` commands to the correct sub-workflow |
| `gemini-triage.yml` | `workflow_call` from dispatch | Label a single newly opened issue via Gemini CLI |
| `gemini-invoke.yml` | `workflow_call` from dispatch | Execute a freeform `@gemini-cli` request via Gemini CLI |
| `gemini-review.yml` | `workflow_call` from dispatch | Review a pull request via Gemini CLI |
| `gemini-scheduled-triage.yml` | hourly schedule | Bulk-triage all unlabelled/`needs-triage` open issues |

## Invariants that must be preserved in all workflows

### 1. Explicit `permissions` on every job
All jobs must declare an explicit `permissions` block with least-privilege. The default is `contents: write` for classic workflows — that is too broad. Use `contents: read` for read-only jobs and `contents: write` only on jobs that upload release assets or push packages.

### 2. `timeout-minutes` on every job
All jobs must have a `timeout-minutes` value to prevent runaway billable-minutes consumption:
- Gradle build/test jobs: **30 min**
- Docker multi-platform build jobs: **60 min**
- Gemini dispatch/comment/fallthrough jobs: **10 min**
- Stale/pages jobs: **10 min**

### 3. `chmod +x gradlew` before any `./gradlew` call on Linux/macOS
Fresh checkouts on GitHub-hosted runners do not preserve file permissions. Always add a `chmod +x gradlew` step before calling `./gradlew` on Linux and macOS runners.

### 4. `create-snapshot.yml` dependency graph
Platform build jobs (`build-jar`, `build-ubuntu-x64`, `build-windows-x64`, `build-macos-arm64`) must declare:
```yaml
needs: [ build-and-test, delete-old-snapshot-assets ]
```
**Never** depend on `delete-old-snapshot-assets` alone. If `build-and-test` fails, the old assets must not be deleted and no broken artifacts should be published. Depending only on `delete-old-snapshot-assets` (as was the original bug) allows the deletion to race ahead of a failing build and leave the `SNAPSHOT` release empty or stale.

## Gemini CLI workflow invariants

### `GEMINI_CLI_TRUST_WORKSPACE: 'true'` is required on every `run-gemini-cli` step
Every step that uses `google-github-actions/run-gemini-cli@v0` must set this environment variable, otherwise the CLI exits with:
> *"Gemini CLI is not running in a trusted directory… set the `GEMINI_CLI_TRUST_WORKSPACE=true` environment variable"* (exit code 1, intermittent)

The failure is intermittent because runner environment state varies between jobs. Set it unconditionally:
```yaml
env:
  GEMINI_CLI_TRUST_WORKSPACE: 'true'
```

For security-sensitive triage/analysis steps (where untrusted issue body is in scope), keep `GITHUB_TOKEN: ''` — the trust flag only grants filesystem trust to the *CLI itself*, not GitHub API credentials.

### `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'` at workflow level for workflows using `actions/github-script`
`actions/github-script@v7` runs on Node.js 20 which is deprecated on GitHub Actions runners (forced to Node.js 24 from June 2026, removed September 2026). Workflows that use this action must set:
```yaml
env:
  FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: 'true'
```
at the top-level `env:` block (workflow scope) so the flag applies to all jobs. This currently affects: `gemini-dispatch.yml`, `gemini-triage.yml`, `gemini-scheduled-triage.yml`.  
`gemini-invoke.yml` and `gemini-review.yml` do **not** use `actions/github-script` directly and do not need this flag.

### `GITHUB_TOKEN: ''` on all triage/analysis Gemini steps
Steps that process untrusted input (issue title/body) must explicitly blank the GitHub token to prevent any accidental credential exposure through Gemini CLI tool calls. The analysis steps in `gemini-triage.yml` and `gemini-scheduled-triage.yml` follow this pattern.

## `gradle/actions/setup-gradle` version
Use `gradle/actions/setup-gradle@v4` (not v3). v4 is the current stable version with improved caching and Gradle 9 compatibility.

## `actions/stale` version
Use `actions/stale@v9`. v5 (previously used) ran on Node.js 16 which is EOL. Do not downgrade. Do not pin to a commit SHA — the SHA has historically broken when the upstream repository rebased its release tags.

# Trace Normalisation & Routing-Loop Architecture

Key facts about how `PolylineTrace.normalize()`, `BasicBoard.normalize_traces()`, and `BatchAutorouter.runBatchLoop()` interact — established during the Issue 676 regression investigation.

## `PolylineTrace.normalize()` depth limit

`PolylineTrace.MAX_NORMALIZATION_DEPTH` (= 34) caps the recursion depth in the private `normalize(IntOctagon, int)` method. **Prior to the fix, exceeding the cap threw `Exception("Max normalization depth reached…")`.** This was silently swallowed by a try-catch in `InsertFoundConnectionAlgo` and logged as:
> `WARNING The normalization of net 'GND' failed.`

**Fix applied:** The `throw` was replaced with `FRLogger.debug(…) + return false`. `return false` is safe because the outer while-loop in `normalize_traces` treats it as "no change for this trace" and continues to the next trace, terminating normally. Both `public normalize(IntOctagon)` and `private normalize(IntOctagon, int)` no longer declare `throws Exception`, and all callers (in `InsertFoundConnectionAlgo`, `PullTightAlgo`, and `Wiring.java`) have had their try-catch wrappers removed.

## `PolylineTrace.combine_at_end()` null search-tree entries

`combine_at_end` contains two code paths:

1. **Full remove + re-insert** — used when `joined_polyline.arr.length != new_line_count` (parallel lines were skipped at the join).
2. **Optimised merge via `merge_entries_at_end`** — reuses existing search-tree leaf nodes for performance.

Path 2 calls `p_to_trace.get_search_tree_entries(tree)` and `p_from_trace.get_search_tree_entries(tree)`. Either can return `null` if the trace has no entries in the given search tree (e.g. it was freshly inserted or its entries were cleared). This caused a `NullPointerException` inside `ShapeSearchTree.merge_entries_at_end` at line 237, visible in the stack as:
```
NullPointerException: Cannot load from object array because "to_trace_entries" is null
    at ShapeSearchTree.merge_entries_at_end(ShapeSearchTree.java:237)
    at PolylineTrace.combine_at_end(PolylineTrace.java:395)
    at PolylineTrace.normalize(PolylineTrace.java:776)
```
Previously this NPE was hidden because the old try-catch in `InsertFoundConnectionAlgo` also caught `RuntimeException` (via `Exception`).

**Fix applied (in `PolylineTrace.combine_at_end`):** Before choosing path 2, check whether both `this` and `other_trace` have entries in the default search tree (`board.search_tree_manager.get_default_tree()`). If either is null, fall back to path 1 (the safe full-remove + re-insert). This guard lives entirely in `combine_at_end` so the tree-management code in `ShapeSearchTree` does not need to be changed.

```java
boolean hasTreeEntries =
    (this.get_search_tree_entries(board.search_tree_manager.get_default_tree()) != null)
    && (other_trace.get_search_tree_entries(board.search_tree_manager.get_default_tree()) != null);
if (joined_polyline.arr.length != new_line_count || !hasTreeEntries) {
    // fall back to full remove + re-insert
    ...
}
```

> **Why the default tree is sufficient for the guard:** All traces are always inserted into the default tree. Compensated trees are built on top of it. If a trace is missing from the default tree it is definitionally not in any compensated tree either.

## `BatchAutorouter` same-board-hash stop detection

v1.9 maintained an `already_checked_board_hashes` set. Between passes it computed a board hash; if the same hash appeared twice, it stopped routing. The new `BatchAutorouter` lacked this, so when GND plane items kept being queued and attempted without changing board state the router looped endlessly with score 0.

**Fix applied:** `runBatchLoop()` now maintains an `alreadyRoutedBoardHashes` `HashSet<String>`. Before each pass, the current board hash is checked; if already seen, the router logs an explanatory message and calls `thread.request_stop_auto_router()`. The set is cleared whenever a previous board state is restored (ripup + retry cycle) so that the restored state can be routed again with a higher ripup cost.

## `BoardStatistics.getNormalizedScore()` division by zero / NaN

`getMaximumScore()` returns `maximumCount * unroutedNetPenalty`. When a board is already fully routed (`maximumCount == 0`), this is 0, and dividing by it produces `NaN`. NaN failed all comparison-based stagnation checks, causing the score to always appear as 0.

**Fix applied:** A `if (maximumScore <= 0f) return 0f;` guard was added in `getNormalizedScore()` before the division.

## Symptom summary

| Symptom | Root cause | Fix location |
|---|---|---|
| `WARNING The normalization of net 'GND' failed.` (every pass) | `PolylineTrace.normalize()` threw `Exception` at max depth | `PolylineTrace.normalize()` — throw → `return false` |
| `NullPointerException` in `merge_entries_at_end` | Trace missing search-tree entries, `combine_at_end` chose optimised path | `PolylineTrace.combine_at_end()` — null guard before path 2 |
| Score always 0 | `getNormalizedScore()` divided by 0 when board fully routed | `BoardStatistics.getNormalizedScore()` — `<= 0` guard |
| Router loops endlessly on same board | No board-hash stagnation detection | `BatchAutorouter.runBatchLoop()` — `alreadyRoutedBoardHashes` set |
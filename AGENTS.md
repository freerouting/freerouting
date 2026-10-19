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
- **Python Client Library:** The project ecosystem includes a Python client library for headless interactions and REST API integrations located at `c:\Work\freerouting-python-client\` (`freerouting/freerouting-python-client`). Maintained by Andras; agents are permitted to edit files in this folder if needed (release workflow reference: `docs/developer.md`).
- **C# Port (`freerouting.net`):** Located at `c:\Work\freerouting.net\`, cloned from `experimentaltvcenter/freerouting.net`. This folder contains a C# port of Freerouting maintained by someone else. **Do not edit any files in `c:\Work\freerouting.net\`**.
- **EDA Integrations:** In-repo integration assets exist under `integrations/` for KiCad, Autodesk Fusion, Target3001!, and EasyEDA; `README.md` and `docs/integrations.md` also document tscircuit and pcb-rnd workflows.

# Architecture & Standards

- **Architecture Reference:** The authoritative architecture overview lives in [`docs/architecture.md`](docs/architecture.md). It includes a Mermaid diagram of the full system, a package glossary, and navigation guidance. **Keep this file up-to-date whenever structural changes are made** — for example, when packages are added or reorganised, when a new interface (GUI mode, API version, CLI flag) is introduced, or when the boundary between the routing pipeline and the orchestration layer shifts. The in-repo diagram is the first document contributors read; a stale diagram misleads more than no diagram at all.
- **Domain Orientation:** This is an **algorithmic-heavy** project. The complexity lies in spatial data structures, pathfinding (e.g., modified A*, Lee algorithm, maze routing), and geometric calculations.
- **Separation of Concerns:** The **UI/Visualizer** and the **Routing Engine** are distinct domains. Always maintain a strict boundary between visual representation and core algorithmic logic. UI concerns should not bleed into the geometric models.
- **Module Boundary Enforcement:** Architectural package boundaries are enforced by strict ArchUnit tests in `src/test/java/app/freerouting/architecture/ModuleBoundariesArchTest.java` and `src/test/java/app/freerouting/io/SpecctraPackageArchTest.java`. Keep every rule green; do not relax strict rules to hide regressions. Accepted boundary debt is documented in `docs/architecture.md` ("Accepted architectural debt"), mirroring the D-code comments in `ModuleBoundariesArchTest.java`.
- **Repository Package Boundaries:** Keep routing/data logic in `src/main/java/app/freerouting/{autoroute,board,geometry,drc,core,rules}`; keep UI/editor flow in `src/main/java/app/freerouting/{gui,gui/interactive,gui/workspace,gui/rendering,gui/a11y}`; keep REST/API server concerns in `src/main/java/app/freerouting/{api,management}`; keep file-format I/O in `src/main/java/app/freerouting/io/{specctra,specctra/parser}` — the public entry points live in `io.specctra` and grammar internals in `io.specctra.parser`.
- **Workspace Repositories & Editing Permissions:**
  - `c:\Work\freerouting`: The primary Java codebase.
  - `c:\Work\freerouting-python-client`: Python client library for headless interactions and REST API integrations (`freerouting/freerouting-python-client`). Maintained by Andras; agents are permitted to edit files here if needed.
  - `c:\Work\freerouting.net`: A clone of `experimentaltvcenter/freerouting.net` containing a C# port of Freerouting. Maintained externally by someone else; **do not edit any files in this directory**.
- **Coding Standards:** Adhere strictly to Clean Code principles and standard Java naming conventions (e.g., CamelCase for classes/methods). Prioritize readability and maintainability without sacrificing the algorithmic performance.
- **Formatting and quality gates:** Use the repository's pinned Spotless/Google Java Format
  configuration and LF line-ending policy. Do not run `spotlessApply` as an automatic
  cleanup step: it formats every configured Java source and can create hundreds of
  unrelated changes. Prefer `spotlessCheck` and the Checkstyle tasks, and report any
  failure before considering work ready. Never stage files automatically from an agent.
- **AI/contributor verification:** Before handoff, run
  `./gradlew spotlessCheck checkstyleMain checkstyleTest`,
  `pre-commit run --all-files`,
  and `python scripts/i18n/extract-context.py --check` when applicable. On Windows use
  `gradlew.bat`. If formatting is intentionally changed, isolate it in a dedicated
  change and inspect `git diff --stat` plus `git diff --check`.
  **PR Review & Merge Operations:** Follow the dedicated `github-automation` skill for the complete 7-step PR review workflow, feedback loop, body-integrity rules (`--body-file`), and merge policies. Never merge PRs automatically without explicit user confirmation.
- **Legacy Reference Implementation:** The historical v1.9 reference implementation is preserved in Git tag `v1.9.0` for optional deep-dive archaeology. The in-tree v1.9 source directory and build tasks have been removed; use git worktrees or `scripts/benchmark/Build-Baseline.ps1` when historical comparisons are needed.
- **Logging & Debugging:** Use the `FRLogger` class for logging. The method `trace(String method, String operation, String message, String impactedItems, Point[] impactedPoints)` should be used for detailed algorithmic steps, especially in routing logic, to facilitate debugging and performance analysis. Logs should be structured and informative, including impacted nets and impacted points in the routing process. When comparing against a baseline build, keep diagnostic payloads synchronized between the WIP tree and that baseline before drawing conclusions from log diffs.
  - For parity investigations, keep diagnostic payloads synchronized between WIP and the baseline under comparison. If WIP emits a debug marker (for example `[assign_raw]` with section/door identity), add the same marker fields to the baseline instrumentation before drawing conclusions from log diffs.
- **`WorkspaceSettings` / GUI Session State:** `WorkspaceSettings` extends `GuiSettingsSource` (which implements `SettingsSource` at priority 65) and is the **sole** live source of GUI state in the `SettingsMerger` pipeline. Key invariants:
  - It is a **singleton within a GUI session**. Always obtain it via `WorkspaceSettings.getOrCreate(board)` and reset it on every board load with `WorkspaceSettings.reset(board)`.
  - It must **never** be referenced from `HeadlessBoardManager` or any `api`/`management` code path. There is deliberately no `getWorkspaceSettings()` on the headless API: GUI-only manager operations live on the `WorkspaceContract` interface, which only `GuiBoardManager` implements. From a `BoardManager` reference, narrow with `if (manager instanceof WorkspaceContract gui)` — never with a null check.
  - `WorkspaceSettings.getSettings()` returns a **live snapshot** of the current GUI state and is what `SettingsMerger` reads at priority 65. Do not cache the result; always call `merger.merge()` to get an up-to-date `RouterSettings`.
  - All fields in `WorkspaceSettings` (own and inherited) are `private`; external access must go through getters/setters so that `PropertyChangeEvent`s fire correctly.
- **`BoardManager` Class Hierarchy:** `HeadlessBoardManager` is the headless/API base; `GuiBoardManager` extends it and adds all GUI concerns (Swing panels, `WorkspaceSettings`, serialisation of `.frb` binary files). Code that must work in both modes lives in `HeadlessBoardManager`; code that requires a display or user interaction lives in `GuiBoardManager`.
- **`SettingsMerger.addOrReplaceSources(SettingsSource)`:** When registering a `GuiSettingsSource`-subtype source (e.g., the `WorkspaceSettings` singleton), pass the concrete instance — the merger uses subtype matching to find and replace any existing `GuiSettingsSource` entry at priority 65. Never register a plain `GuiSettingsSource` instance after the singleton has been registered or it will silently shadow the live GUI state.

# Specific Constraints & Logic

- **Determinism:** The routing algorithm must stay deterministic. The same input and settings must always produce the same routing result, independent of run, machine load or thread scheduling.
- **Time-Budget Agnostic:** The algorithm must behave the same way regardless of the time budget or timeout settings of the job, the fanout, the autorouter or the optimizer. Do not add logic whose decisions depend on elapsed time or remaining budget (for example budget-based pass limits, "give up when little time is left" heuristics, or time-scaled ripup/search limits). A timeout may only stop work; it must not change how the work is done.
- **Measure Every Routing Change:** Whenever you apply a routing improvement, test and measure before and after the change on the same boards, with the same settings and worker count. Record the exact improvements and regressions (unrouted connections, clearance violations, boards that became clean or stopped being clean, CPU/wall time), and only keep the change if the measured result justifies it.
- **Safety First:** Be *extremely careful* when modifying the routing algorithms. Even minor changes can lead to severe regressions in trace optimization, clearance violations, or routing completion rates.
- **Regressions Prevention:** Before refactoring any core routing logic, you **must** verify your changes against the existing test suite to prevent trace regressions. Always run reproduction tests on actual PCB design files (`.dsn`) if an issue is reported (see `src/test/java/app/freerouting/fixtures/RoutingFixtureTest.java` and fixtures in `fixtures/`).
- **Baseline Performance:** The primary performance and correctness baseline for current development is stable **Freerouting v2.5.0** (declared in `scripts/benchmark/baselines/baseline-manifest.json`). Any WIP / feature-branch implementation should match or exceed v2.5.0 routing quality on the agreed golden fixtures.
  - Run compare workflows via `scripts/tests/compare-versions.ps1` (defaults to comparing current WIP against the v2.5.0 baseline build or binary).
  - When investigating regressions, compare clearance violations using `DesignRulesChecker.getAllClearanceViolations()` (do not trust `BoardStatistics.clearanceViolations.totalCount` alone — it is incomplete).
  - TRACE logs for baseline comparisons should use clear WIP vs baseline filenames; keep files manageable with net filters (`-DebugFilterByNet`), `max_passes`, and `max_items` as needed.
  - For deterministic parity work, locate the first meaningful mismatch in normalized routing streams (for example `RAW_SECTION` selection records) and treat that position as the investigation anchor.
  - Classify divergence before fixing: distinguish numeric-only drift from behavioral ordering/tie-break divergence by suppressing volatile values (for example `expansion_value` and `sorting_value`) and comparing decision continuity.
  - Preferred remediation sequence: (1) synchronize instrumentation payloads in WIP and baseline, (2) diff around the first normalized mismatch with stable identifiers (section, door, from_door, net), (3) apply the smallest possible ordering/tie-break fix in WIP, (4) rerun comparisons to confirm the mismatch moves later or disappears without introducing violations.
  - Exit criteria for parity investigations: no new clearance violations (full DRC), no regression in routing completion vs v2.5.0, and stable or improved compare metrics across repeated runs and at least two `max_items` checkpoints.
- **Algorithm Performance Metrics:** When optimizing routing algorithms, focus on key performance metrics such as:
  - **Clearance Violations:** (Critical priority) Ensure that no routing changes introduce new clearance violations.
  - **Routing Completion Rate:** (High priority) The percentage of successfully routed nets.
  - **Execution Time:** (Medium priority) Aim to reduce the time taken for routing without compromising the above metrics.
  - **Memory Usage:** (Medium priority) Ensure that any optimizations do not lead to excessive memory consumption, especially for large PCB designs.
    - **Interpreting router memory log lines:** The phrase `"the job allocated X GB of memory so far"` is the **cumulative total** of all JVM heap allocations since the job started (a GC throughput metric). It grows monotonically regardless of GC activity and is **not** the current heap size. The authoritative live-memory figure is `"peak heap usage: Y MB"` printed at the end of the session. When investigating memory reports from users, always ask for or measure peak heap, not cumulative allocated.
    - **Expected memory profile for a multi-pass routing job:** Working-set typically grows in a staircase pattern (each pass plateau, then a GC trim) up to a board-size-dependent peak, then drops sharply when the routing thread pool winds down and releases per-pass board state. The optimizer phase that follows normally runs at a significantly lower and flat memory footprint. Sustained growth *during* the optimizer (not routing) is the signal that indicates a genuine GC-root retention leak.
  - **Trace Length Optimization:** (Low priority) The total length of traces should be minimized while respecting design rules.
- **Testing & Validation:** Always write comprehensive unit tests for any new routing logic or optimizations. Use the existing test suite as a reference and ensure that all tests pass before merging changes. For any new features or optimizations, add specific test cases that validate the expected behavior and performance improvements.
  - **Parallel test runs:** You may run up to **8 tests (or benchmark/experiment jobs) in parallel** to speed up validation (for example `--workers 8` in `scripts/pcbench/run_corpus_benchmark.py`, or 8 concurrent single-board routing runs). This is the total across all concurrently running jobs, not per job. Do not exceed 8 (the reference machine has 12 logical cores; the optimizer needs headroom). Keep results comparable by using the same worker count for baseline and candidate runs, and prefer results from boards that finish inside the time budget when comparing, since timed-out runs depend on machine load.
  - **Running Tests:** If you implement a small change you can run only one unit test to do a quick check, preferably `Dac2020Bm01RoutingTest` which is one of the quickest routing tests. Use `./gradlew test` for the default (fast) unit-test set and `./gradlew check` for the full integration testing suite.
  - **Slow-test tagging policy:** Tag long-running fixture/benchmark tests with `@Tag("slow")`. The default `test` task excludes these tests unless explicitly enabled (`-PincludeSlowTests=true`). Use `./gradlew testSlow` to run only slow tests, and `./gradlew testAll` to run both fast + slow sets before releases or merge-critical validation.
  - **Timeout budget:** The Gradle unit-test task timeout is **30 minutes** to accommodate fanout-enabled routing fixtures on slower hardware.
  - **Large-board CI tests:** Boards with >500 nets can take several minutes per routing pass. Create a `new TestingSettings()` and use its instance setters `setMaxItems(n)` (e.g. 100–200) to slice off a bounded chunk of work that runs in under 30 seconds while still exercising the target code path. Do **not** rely on a short `jobTimeoutString` alone — the timeout fires after the pass completes, so a single slow pass can still blow the budget.
  - **Full-scale OOM / stress tests** that cannot be bounded to CI time belong in `scripts/tests/` as standalone PowerShell scripts (see `run_test_Issue420_oom.ps1` as the reference pattern). These scripts build the executable JAR, run it headlessly with `-XX:+HeapDumpOnOutOfMemoryError`, sample the JVM working-set every 30 s via a `Start-Job` background sampler, and print a pass/fail summary with the memory trend at the end.
- **GUI vs Headless Guard:** GUI-session state is reachable only through `WorkspaceContract` (implemented solely by `GuiBoardManager`). From a `BoardManager` reference, narrow with `if (manager instanceof WorkspaceContract gui)` before touching `gui.getWorkspaceSettings()`; never call workspace code from `HeadlessBoardManager` or any `api`/`management` path. Concrete editor-state code lives in `gui.interactive` and may access the session manager only through its GUI-session contract; ensure those paths are reachable only from a `GuiBoardManager` instance.
- **Test Placement Conventions:**
  - Issue-regression and full-pipeline tests → `src/test/java/app/freerouting/fixtures/` (extend `RoutingFixtureTest`).
  - Unit/integration tests scoped to a specific package → place in the matching test package (e.g., tests for `app.freerouting.gui.interactive` go in `src/test/java/app/freerouting/gui/interactive/`, and workspace tests go in `src/test/java/app/freerouting/gui/workspace/`).
  - DSN fixture files live in `fixtures/`; reference them by filename (e.g., `"Issue508-DAC2020_bm01.dsn"`). The quickest fixture for smoke-checks is `Dac2020Bm01RoutingTest`.
  - Bound long-running routing tests with the `TestingSettings` instance setters `setMaxPasses(n)`, `setMaxItems(m)`, and `setJobTimeoutString("HH:MM:SS")` to keep CI fast.
- **Issue Tracking:** Per-issue specification files under `docs/issues/` were retired (directory removed during the docs cleanup); track new work in GitHub issues and keep `docs/architecture.md` ("Accepted architectural debt") up to date as strict rules or accepted debt change, so future agents have accurate context without re-reading the full conversation history.
  - Temporary analysis artifacts (draft GitHub replies, one-off log extracts, heap-dump notes) should be written to `logs/<IssueNNN>/` — this directory is git-ignored and will not clutter the repository.
- **Licensing:** This project is open-source under the **GPLv3** license. Ensure all dependencies and contributions respect this license.

# Settings & Configuration System

Router configuration is resolved at runtime by `SettingsMerger`, which layers nine `SettingsSource` implementations in ascending priority order (0 = defaults → 70 = API). The merger calls `RouterSettings.applyNewValuesFrom(source)`, which uses `ReflectionUtil.copyFields()` to copy a field only when the source value is **non-null and not the Java language default** for that type.

This design has one critical invariant: **all fields in `RouterSettings` (and its nested `RouterOptimizerSettings` / `RouterScoringSettings`) must be nullable reference types with no default initializers.** If a field were initialised to a non-null value (e.g. `public Integer maxPasses = 9999;`), every source object would carry that value and the merger could no longer distinguish "this source sets this field" from "this source has no opinion". A low-priority source would then silently override a higher-priority one. All hardcoded defaults belong exclusively in `DefaultSettings.getSettings()`, which is always applied first as the base layer.

The full priority ladder is documented in `docs/settings.md`. Key sources: `DefaultSettings` (0), `JsonFileSettings` (10), `DsnFileSettings` (20), `SesFileSettings` (30), `RulesFileSettings` (40), `EnvironmentVariablesSource` (55), `CliSettings` (60), `GuiSettingsSource` / `WorkspaceSettings` (65), `ApiSettings` (70).

- **Copper-to-edge default:** `RouterSettings.copperToEdgeClearanceUm` defaults to **250.0 µm (0.25 mm)** in `DefaultSettings`. Keep the field nullable (no initializer in `RouterSettings`) so higher-priority sources can still override it cleanly.
- **Override-test guidance:** When writing tests for the edge-clearance override path, set `copperToEdgeClearanceUm` to a **non-default** value so the test verifies source precedence/override behavior, not just default propagation.

# Workflow Commands

Execute the following commands from the root directory using the Gradle Wrapper (on Windows use `gradlew.bat`):

- **Targeted Test (Fast & Token-Efficient):** `./gradlew test --tests "<ClassName>" -q` (e.g. `./gradlew test --tests "Dac2020Bm01RoutingTest" -q`)
- **Run Tests (fast/default):** `./gradlew test`
- **Run Slow Tests Only:** `./gradlew testSlow`
- **Run Fast + Slow Tests:** `./gradlew testAll`
- **Run Full Verification Suite:** `./gradlew check` (pipe verbose output to temporary logs like `> logs/check.log 2>&1` during agent sessions to avoid context bloat)
- **Build the Executable JAR:** `./gradlew executableJar` (Find the result in `build/libs/freerouting-current-executable.jar`)
- **Run Current Development Environment:** `./gradlew run`
- **Gradle Build Scan:** Gradle Build Scan is activated and available after builds complete on GitHub Actions (inspect the Develocity link printed in CI summaries and build results).

# Communication Style

Your communication should be direct, professional, and technically precise. Acknowledge and respect the inherent complexity of PCB routing logic. Do not oversimplify geometric problems; instead, provide thorough, algorithmically-sound justifications for any proposed code changes. Output complete and correct code when finalizing solutions.

- **Spelling:** The product name is always written **"Freerouting"** (capital F). Never write "freerouting" in prose, documentation, or user-facing messages.
- **Maintainer / Team Voice (First-Person Singular):** In general, when writing anything about the development team of Freerouting, use first-person singular pronouns (**I / me / my / mine**) instead of the default plural pronouns (**we / us / our / ours**), since there is currently one maintainer and main developer (Andras).
- **No Emoticons / Emojis:** Do not use emoticons or emojis in release notes, documentation, commit messages, or user-facing text.
- **Command Line Arguments / Parameter Names:** Always use lowercase `snake_case`, with nested fields separated by the dot (`.`) character (e.g. `--router.plane_as_obstacle=true`, `--router.plane_via_costs=50`, `--router.copper_to_edge_clearance_um=500`). Do not encourage users to use deprecated parameters (such as `conduction_is_obstacle` or `conductionIsObstacle`) or `camelCase` parameters (such as `planeAsObstacle`). Sample parameter references in documentation, CLI help, and discussions must strictly follow the lowercase `snake_case` with dot `.` format.
- **Release Notes:** Release notes must follow the structure and sections of the most recent releases (e.g. v2.4.1, v2.3.0). The style should be professional, direct, friendly, and grateful. Keep release notes as brief as possible without leaving anything important out. Include relevant sponsor callouts, highlights, detailed categorized sections, known limitations/guidance, and heartfelt contributor acknowledgments.

# Agent Efficiency & Context Optimization

To minimize unnecessary token consumption and avoid context window pollution:
- **Diff-First Inspection:** When reviewing PRs, investigating regressions, or checking bugfixes, always inspect the patch (`git diff`, `gh pr diff`) or narrow line ranges (`view_file` with explicit `StartLine`/`EndLine`) instead of loading entire multi-thousand-line source files.
- **Silent & Targeted Verification:** Never run broad Gradle tasks (like full `./gradlew test` or `./gradlew check`) directly into conversation output. Run targeted test classes (`./gradlew test --tests "<ClassName>" -q`) and redirect heavy multi-task output to files (e.g. `> logs/check.log 2>&1`), inspecting only the trailing summary if an error occurs.
- **Research Delegation:** For broad codebase explorations, log auditing, or multi-file surveys, delegate to the `research` subagent (`invoke_subagent`). Subagents execute in isolated contexts and return concise findings, protecting the main conversation history from token bloat.
- **Daily Triage, Parallel PR Worktrees & Nightly PCBench:** Follow the [`daily-ops`](.agents/skills/daily-ops/SKILL.md) skill for daily issue/PR briefings grouped by topic, parallel `Workspace: "share"` subagent PR reviews (with GitHub Copilot review verification), and post-merge `-RCx.jar` PCBench Tiers A & B regression runs.
- **Compaction Recommendations:** Proactively recommend the `/compact` command to the user between major phase shifts (e.g., when transitioning from root-cause diagnosis to implementation, or after completing a multi-turn bugfix before PR preparation).

# DRC & Clearance Architecture

Key facts about how design-rule checking and clearances work — important context for any issue investigation:

- **Internal DRC entry point:** `DesignRulesChecker.getAllClearanceViolations()` is the comprehensive DRC method that iterates all board item pairs. Always use this when you need a complete violation count.
- **`BoardStatistics.clearanceViolations.totalCount` is authoritative:** it is computed from `DesignRulesChecker.getAllClearanceViolations()` (see the `BoardStatistics` constructor). Treat it as the definitive in-app violation signal.
- **A passing test still needs threshold scrutiny:** verify that the DRC is checking the same clearance values that the final EDA tool (e.g. KiCad) will check (see the copper-to-edge gap below).
- **KiCad DSN export does not include copper-to-edge clearance:** KiCad's "copper to board edge clearance" setting is **not written into the Specctra `.dsn` file**. Freerouting therefore assigns the `BoardOutline` the default conductor-to-conductor clearance class and routes traces at that (smaller) distance from the board edge. KiCad's own DRC will flag violations after import. The Specctra format supports `(clearance_class ...)` inside `(boundary ...)`, and freerouting already parses it — the fix requires KiCad to start emitting it.
- **Board outline clearance class:** The `BoardOutline` item's clearance class is set during `HeadlessBoardManager.createBoard()` from `p_outline_clearance_class_name`. When this is `null` (the KiCad case), it falls back to `ItemClass.AREA` = class 1 = "default". The `ShapeSearchTree` then uses this class's compensation value to determine how close routing can approach the board edge.

# Copper Pour / Power Plane Architecture

Key facts about how copper pours (power/ground planes) are modelled and routed — established during the Issue 152 investigation:

- **Model:** Copper pours are represented as `board.ConductionArea` (extends `ObstacleArea`, implements `Connectable`). A `ConductionArea` belongs to exactly one net (e.g. GND) and occupies one PCB layer.
- **Obstacle flag:** `ConductionArea.is_obstacle` controls whether the area blocks foreign-net traces. When `false` (the default for fills) foreign traces may pass through the pour geometrically, which is the normal behaviour for a poured plane. Use `RoutingBoard.changePlaneAsObstacle(boolean)` to toggle this in bulk (`changeConductionIsObstacle` is a deprecated alias). The GUI toggles it via `GuiBoardRoutingSettings`; headless applies the `router.plane_as_obstacle` setting in `HeadlessBoardManager.applyPlaneAsObstacleOverride`.
- **Plane-net flag:** `rules.Net.contains_plane` is the critical flag that switches the autorouter into plane-routing mode for a given net. When `true`, `AutorouteConnectionRouter` routes *from* the already-connected group *toward* the `ConductionArea` (short stub + via pattern), rather than routing pad-to-pad. An early-exit `CONNECTED_TO_PLANE` state is returned as soon as the connected set already touches a `ConductionArea`.
- **Two independent paths set `contains_plane`:**
  - **Path A — DSN `(plane ...)` scopes (reliable, fires for standard KiCad exports):** At structure-load time, `io.specctra.parser.Structure` inserts each `(plane <netname> ...)` scope as a `ConductionArea` and creates the net via `nets.add(name, subnetNumber, /* containsPlane */ true)`, so the flag is set immediately. An explicit per-net override is also available via the `router.plane_nets` setting (`HeadlessBoardManager.applyPlaneNetsOverride`). Live tests confirm this works correctly for outer-layer pours (B.Cu, F.Cu) and inner-layer pours (In1.Cu).
  - **Path B — `DsnFile.adjustPlaneAutorouteSettings()` (heuristic fallback):** Only invoked from `DsnReader.readBoard` when the DSN has no `(autoroute ...)` scope. Uses a ≥ 30% board-area threshold (relaxed from 50% to support outer-layer pours on 2-layer boards) with no outer-layer skip.
- **Via cost discount for plane nets:** When `contains_plane` is `true`, `AutorouteConnectionRouter` passes `settings.getPlaneViaCosts()` (cheaper than the regular via cost) to the attempt. This incentivises dropping vias onto the plane rather than routing traces across the board.
- **Via optimisation for plane-connected vias:** `ViaOptimizer.optPlaneOrFanoutVia()` handles the post-routing via repositioning for the stub-to-plane case. It verifies the new location is inside the `ConductionArea` before moving.
- **`getAutorouteItems()` quadratic false-work (partially fixed):** `BasicBoard.connectable_item_count()` counts `ConductionArea` as a connectable item. For a GND net with N pads + 1 pour, every pad whose `connected_set` has fewer than N+1 members is enqueued — even those already touching the plane. **Fix applied:** `BatchAutorouter.getAutorouteItems()` now skips items that are already connected to a `ConductionArea` for plane nets (i.e., `net.containsPlane() && connected_set.anyMatch(ConductionArea)`). Items not yet connected to the plane are still enqueued so they can drop a via onto the pour. This eliminates the repeated `normalizeTraces` failure (which arose because `InsertFoundConnectionAlgo` was called for those false-work items) and ensures `autoroutePass()` returns `false` once all plane-net items are connected — letting the routing loop exit cleanly.
- **Confirmed bug — plane routing introduces clearance violations (Issue 093):** Routing `Issue093-interf_u.dsn` (bottom-copper GND pour) with the current code introduces **62 clearance violations** and logs an internal error in the autorouter pass. The plane-routing code path is active when this occurs. This is a safety-critical open bug.
- **No pour connectivity / void detection:** If a foreign-net trace cuts through a pour layer, creating an isolated copper island, Freerouting does **not** detect the disconnected region. The `RatsNest` and the exported `.ses` file will show the net as fully routed even though part of the pour is electrically floating. This is long-term future work.
- **Loading boards to check plane flags without routing:** Use `DsnReader.readBoard(InputStream, BoardObservers, IdGenerator, String)` (returns a `BoardReadResult`) directly when you only need to inspect the loaded board state (e.g. verify `Net.contains_plane`) without running the routing scheduler. This is faster and avoids timeout issues in tests.

# API Analytics Architecture

Key facts about API usage tracking (three-hop pipeline: Controller -> `FRAnalytics` / `FreeroutingAnalyticsClient` -> BigQuery `api_endpoint_called`):
- Authenticated requests must pass caller UUID via the 4-arg `FRAnalytics.apiEndpointCalled(..., userId)`.
- Global error tracking (>=400) is handled by `ApiAnalyticsFilter`.
- Failures in analytics reporting are aggregated by `AnalyticsErrorAggregator` rather than logged per failure.
- Complete pipeline details, invariants, table schema, and BigQuery verification queries are documented in [`docs/internals/api-analytics.md`](docs/internals/api-analytics.md).

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

- Update `docs/API/MCP.md` with concrete startup, verification, and troubleshooting steps.
- Update `docs/API/API_v1.md` MCP endpoint tables/examples.
- Update `docs/settings.md` whenever `mcp_server` fields change.
- Update `docs/architecture.md` if package boundaries or data flow changes.

# Packaging & JRE Module Requirements (jlink & Docker)

- **Docker Multi-Platform:** Automated via `docker-release.yml` and `docker-nightly.yml` for `linux/amd64` and `linux/arm64` (ARMv7 dropped on Java 25). `provenance: false` is required.
- **jlink Bundled JRE:** Requires platform-specific `--add-modules` (including `jdk.crypto.ec`, `jdk.management`, and on Windows `jdk.crypto.mscapi`). `ManagementFactory` calls must be guarded with `try/catch (Throwable)`.
- Full module breakdown, rationale, and container build invariants are documented in [`docs/internals/packaging-and-jlink.md`](docs/internals/packaging-and-jlink.md).

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
| `gui-a11y.yml` | `pull_request` (path-filtered to GUI sources/tests/resources) | GUI accessibility tests on Ubuntu, macOS, Windows |
| `pre-commit.yml` | `pull_request` + push to `master` | Run pre-commit hooks |
| `create-snapshot.yml` | push to `master` | Build all platform installers and publish to the `SNAPSHOT` release |
| `create-release.yml` | push of `v*` tag | Build all platform installers and publish to the versioned release |
| `docker-nightly.yml` | push to `master` | Build + push multi-arch Docker image tagged `nightly` |
| `docker-release.yml` | GitHub release published | Build + push multi-arch Docker image tagged with semver + `latest` |
| `deploy-pages.yml` | push to `master` (paths: `website/**`) | Deploy static website to GitHub Pages |
| `stale.yml` | daily schedule | Mark inactive issues/PRs stale after 120 days |

## Invariants that must be preserved in all workflows

### 1. Explicit `permissions` on every job
All jobs must declare an explicit `permissions` block with least-privilege. The default is `contents: write` for classic workflows — that is too broad. Use `contents: read` for read-only jobs and `contents: write` only on jobs that upload release assets or push packages.

### 2. `timeout-minutes` on every job
All jobs must have a `timeout-minutes` value to prevent runaway billable-minutes consumption:
- Gradle build/test jobs: **30 min**
- Docker multi-platform build jobs: **60 min**
- Stale/pages jobs: **10 min**

### 3. `chmod +x gradlew` before any `./gradlew` call on Linux/macOS
Fresh checkouts on GitHub-hosted runners do not preserve file permissions. Always add a `chmod +x gradlew` step before calling `./gradlew` on Linux and macOS runners.

### 4. `create-snapshot.yml` dependency graph
Platform build jobs (`build-jar`, `build-ubuntu-x64`, `build-windows-x64`, `build-macos-arm64`) must declare:
```yaml
needs: [ build-and-test, delete-old-snapshot-assets ]
```
**Never** depend on `delete-old-snapshot-assets` alone. If `build-and-test` fails, the old assets must not be deleted and no broken artifacts should be published. Depending only on `delete-old-snapshot-assets` (as was the original bug) allows the deletion to race ahead of a failing build and leave the `SNAPSHOT` release empty or stale.

## `gradle/actions/setup-gradle` version
Use `gradle/actions/setup-gradle@v5` (not v3/v4). v5 is the current stable version with improved caching and Gradle 9 compatibility.

## `actions/stale` version
Use `actions/stale@v9`. v5 (previously used) ran on Node.js 16 which is EOL. Do not downgrade. Do not pin to a commit SHA — the SHA has historically broken when the upstream repository rebased its release tags.

# Trace Normalisation & Routing-Loop Architecture

Key invariants established during the Issue 676 investigation:
- `PolylineTraceNormalization.normalize()` caps recursion at `MAX_NORMALIZATION_DEPTH` (= 16) and returns `false` (no throw) on depth limit.
- `PolylineTrace.combine_at_end()` guards optimised merge via `PolylineTraceSearchTreeAdapter.hasDefaultEntries`, falling back to full remove/re-insert if null.
- Score division-by-zero guard in `BoardStatistics.getLegacyNormalizedScore()` prevents `NaN` comparisons.
- Full architectural details and symptom summary are documented in [`docs/internals/trace-normalization.md`](docs/internals/trace-normalization.md).

# Routing Engine Issues and PR Analysis for Freerouting v2.5

**Date:** September 17, 2026
**Target Release:** Freerouting v2.5.0
**Branch:** `master`
**Status:** All P0/P1 Engine PRs Merged (#892, #901, #902, #904); 2.5.0-RC5 Benchmark Validated (Score: 953.5, 0 Failures); RE-04 (#858) & Housekeeping (#558, #632, #582, PR #818) Closed; Ready for v2.5.0 Release

---

## Executive Summary

A comprehensive audit was performed across all GitHub issues and Pull Requests labeled with `routing-engine`. In total, **16 issues** and **9 pull requests** carry or carried this label.

- **Issues (16 Total):**
  - **10 Closed:**
    - Handled and verified in previous milestones: [#152](https://github.com/freerouting/freerouting/issues/152), [#837](https://github.com/freerouting/freerouting/issues/837), [#848](https://github.com/freerouting/freerouting/issues/848).
    - Resolved and closed in v2.5 cut: [#872](https://github.com/freerouting/freerouting/issues/872), [#798](https://github.com/freerouting/freerouting/issues/798), [#811](https://github.com/freerouting/freerouting/issues/811), [#858](https://github.com/freerouting/freerouting/issues/858), [#558](https://github.com/freerouting/freerouting/issues/558), [#632](https://github.com/freerouting/freerouting/issues/632), [#582](https://github.com/freerouting/freerouting/issues/582).
  - **1 CAD / DRC Integration & Input Quirk:** [#523](https://github.com/freerouting/freerouting/issues/523) (tagged `missing-info`; unreproduced stubs).
  - **1 Non-Engine / GUI:** [#750](https://github.com/freerouting/freerouting/issues/750) (`routing-engine` label removed; pure GUI component dragging lag).
  - **4 Long-Term Architectural Enhancements (v2.6+ / v3.0):** [#879](https://github.com/freerouting/freerouting/issues/879), [#716](https://github.com/freerouting/freerouting/issues/716), [#718](https://github.com/freerouting/freerouting/issues/718), [#383](https://github.com/freerouting/freerouting/issues/383).

- **Pull Requests (9 Total):**
  - **5 Merged:**
    - PR [#887](https://github.com/freerouting/freerouting/pull/887) (Memory allocation & benchmark throughput).
    - PR [#892](https://github.com/freerouting/freerouting/pull/892) (Fixes [#872](https://github.com/freerouting/freerouting/issues/872) - single-sided optimizer handoff).
    - PR [#901](https://github.com/freerouting/freerouting/pull/901) (Fixes [#798](https://github.com/freerouting/freerouting/issues/798) - search-tree race condition; supersedes [#817](https://github.com/freerouting/freerouting/pull/817)).
    - PR [#902](https://github.com/freerouting/freerouting/pull/902) (Fixes [#811](https://github.com/freerouting/freerouting/issues/811) - weighted trace metric baseline; supersedes [#818](https://github.com/freerouting/freerouting/pull/818)).
    - PR [#904](https://github.com/freerouting/freerouting/pull/904) (Fixes '+' path parsing, per-worker isolation, runner graceful termination, and records 2.5.0-RC5 benchmark).
  - **1 Closed / Superseded PR:** PR [#818](https://github.com/freerouting/freerouting/pull/818) (superseded by merged PR [#902](https://github.com/freerouting/freerouting/pull/902); closed).
  - **1 Open Input-Validation PR:** PR [#820](https://github.com/freerouting/freerouting/pull/820) (addresses [#632](https://github.com/freerouting/freerouting/issues/632); soft-warning already in master).
  - **2 Open Experimental / Stale PRs:** PR [#793](https://github.com/freerouting/freerouting/pull/793) (author advises against merging heuristics), PR [#743](https://github.com/freerouting/freerouting/pull/743) (ETA calculator, obsolete thread architecture).

---

## Part 1: Issue Index & Validity Matrix

Each issue is assigned a persistent local identifier (`RE-01` through `RE-16`).

| Local ID | GitHub Issue | Title / Scope | Validity Assessment | v2.5 Recommendation |
| :--- | :--- | :--- | :--- | :--- |
| **RE-01** | [#872](https://github.com/freerouting/freerouting/issues/872) | Single-sided regression: optimizer starts from worse board than autorouter | **RESOLVED & CLOSED**. Fixed in PR [#892](https://github.com/freerouting/freerouting/pull/892). `BatchOptimizer.board` instance handoff synchronized; regression tests and fixture added. | **CLOSED (P0)** |
| **RE-02** | [#798](https://github.com/freerouting/freerouting/issues/798) | NPE in `PullTightAlgo` during interactive routing | **RESOLVED & CLOSED**. Fixed in PR [#901](https://github.com/freerouting/freerouting/pull/901) (supersedes [#817](https://github.com/freerouting/freerouting/pull/817)). Per-tree read/write locks, thread-local traversal stack, query-local IDs. | **CLOSED (P0)** |
| **RE-03** | [#811](https://github.com/freerouting/freerouting/issues/811) | Optimizer discarding accepted trace improvements | **RESOLVED & CLOSED**. Fixed in PR [#902](https://github.com/freerouting/freerouting/pull/902) (supersedes [#818](https://github.com/freerouting/freerouting/pull/818)). Weighted trace metric baseline ported to unified `BatchOptimizer`. | **CLOSED (P1)** |
| **RE-04** | [#858](https://github.com/freerouting/freerouting/issues/858) | 2.3.0 regression: clearance violations on clean boards | **RESOLVED & CLOSED**. Commit `b32877f93` separated pre-existing DRC violations and added pin containment exemption. Verified via `DevBoardClearanceRoutingTest` (0 router-introduced violations). | **CLOSED (P1)** |
| **RE-05** | [#558](https://github.com/freerouting/freerouting/issues/558) | Board-edge clearance class override | **RESOLVED & CLOSED**. PR #567 merged; `--router.copper_to_edge_clearance_um` added; upstream KiCad tracking [GitLab #24077](https://gitlab.com/kicad/code/kicad/-/work_items/24077). | **CLOSED (P2)** |
| **RE-06** | [#632](https://github.com/freerouting/freerouting/issues/632) | Crash on multi-board panels with pins outside PCB boundary | **RESOLVED & CLOSED**. `validateBoardDesignErrors()` in `HeadlessBoardManager` logs structured warnings without crashing valid designs. | **CLOSED (P2)** |
| **RE-07** | [#523](https://github.com/freerouting/freerouting/issues/523) | Unrouted/redundant track stubs left after passes | **NOT REPRODUCIBLE**. Tested by BanjoR; Specctra import/export already normalizes stubs. Tagged `missing-info`. | **TAGGED (P3)** |
| **RE-08** | [#750](https://github.com/freerouting/freerouting/issues/750) | GUI component dragging lag and ratsnest | **NOT AN ENGINE BUG**. Swing interaction event lag from PR #749. Removed `routing-engine` label; tagged `GUI`. | **UPDATED (P3)** |
| **RE-09** | [#582](https://github.com/freerouting/freerouting/issues/582) | High-density small-area PCB routing guidance | **ANSWERED & CLOSED**. Comprehensive routing recommendations and best practices provided in thread. | **CLOSED (P3)** |
| **RE-10** | [#879](https://github.com/freerouting/freerouting/issues/879) | Custom pad semantics (arbitrary non-convex padstacks) | **VALID FEATURE REQUEST**. Requires rewriting geometric primitives (`ConvexShape`, `SearchTree`). | **DEFER TO v3.0** |
| **RE-11** | [#716](https://github.com/freerouting/freerouting/issues/716) | Automatic trace length tuning (serpentine / meander) | **EXPLORATORY PROPOSAL**. High-speed length tuning architectural proposal. | **DEFER TO v2.6+** |
| **RE-12** | [#718](https://github.com/freerouting/freerouting/issues/718) | DRC support for net-ties and overlapping pads | **VALID FEATURE REQUEST**. Fixture committed in `9029645e7`. Multi-net pad clearance exemption. | **DEFER TO v2.6** |
| **RE-13** | [#383](https://github.com/freerouting/freerouting/issues/383) | Star-ground routing topology | **VALID FEATURE REQUEST**. Alleviated by plane support (#152). | **DEFER TO v2.6+** |
| **RE-14** | [#152](https://github.com/freerouting/freerouting/issues/152) | Copper pours and power/ground plane awareness | **CLOSED / MERGED** in `a1765d706`. | **N/A (CLOSED)** |
| **RE-15** | [#837](https://github.com/freerouting/freerouting/issues/837) | Configurable autorouter pass timeout in GUI & CLI | **CLOSED / MERGED**. | **N/A (CLOSED)** |
| **RE-16** | [#848](https://github.com/freerouting/freerouting/issues/848) | Parse `autoroute_settings` and `layer_rule` in `.rules` | **CLOSED / MERGED**. | **N/A (CLOSED)** |

---

## Part 2: Detailed Issue Analysis & Reproduction Commands

### RE-01 (Issue #872) — Single-Sided Routing Optimizer Board Handoff Regression
- **Impact:** Single-sided (1-layer) through-hole boards route 100% on v2.2.4, but v2.3.0/v2.4.1 consistently leave nets unrouted because the optimization stage discards the autorouter's best board.
- **Root Cause:**
  1. `RoutingPipeline` creates `BatchOptimizer` in its constructor before routing runs. `BatchOptimizer` records `this.board = job.board` (the unrouted board).
  2. `BatchAutorouter.runBatchLoop()` completes, assigning `job.board = bestBoard` (a `deepCopy()` clone).
  3. `BatchOptimizer.runBatchLoop()` executes without re-syncing `this.board = job.board`. It optimizes its stale, initial/intermediate board instance (e.g. 3 unrouted nets instead of 1), fails to improve it, and then writes back `this.job.board = this.board`, overwriting the autorouter's completed work.
- **Test Command Line:**
  ```powershell
  ./gradlew run --args="-de tests/Issue872-single-layer-repro.dsn -do build/Issue872-out.ses -mp 10 --router.optimizer.enabled=true"
  ```
- **Expected vs Actual:**
  - *Current Master:* Logs show `Optimization stage started on board '...'` with a worse score/unrouted count than `Auto-routing stage completed`, and final SES has unrouted nets.
  - *Fixed Master:* Optimizer starts with the exact score and unrouted count achieved by autorouting (`this.board = this.job.board;`).

---

### RE-02 (Issue #798) — NullPointerException in PullTightAlgo
- **Impact:** Crashes interactive routing in GUI with `Cannot invoke "app.freerouting.board.SearchTreeObject.shape_layer(int)" because "curr_object" is null`.
- **Root Cause:** In `ShapeSearchTree`, a reader query thread accesses a `Leaf` node while a writer thread detaches it and nulls its payload.
- **Resolution:** PR [#817](https://github.com/freerouting/freerouting/pull/817) resolves this with per-tree read/write locking and persistent detached leaf payload references.
- **Test Command Line:**
  ```powershell
  ./gradlew test --tests app.freerouting.board.ShapeSearchTreeLeafLifecycleTest
  ./gradlew test --tests app.freerouting.datastructures.MinAreaTreeLeafLifecycleTest
  ```

---

### RE-03 (Issue #811) — Optimizer Trace Metric Baseline & Discarded Improvements
- **Impact:** Length-only trace improvements rejected during optimization passes.
- **Status in Master:**
  - Board cloning overhead resolved via worker board reuse (`50ee20b4d`).
  - Leftover maze state cleared (`8dcd4cd68`).
  - Pre-flight guards and thresholding implemented (`b3f4ccc0f`, `e7f9bdf1a`).
  - Worker trace baseline fix ported to unified `pipeline.BatchOptimizer` with weighted trace metric and unit tests in PR [#902](https://github.com/freerouting/freerouting/pull/902) (superseding [#818](https://github.com/freerouting/freerouting/pull/818)).
- **Test Command Line:**
  ```powershell
  ./gradlew test --tests app.freerouting.autoroute.pipeline.BatchOptimizerTraceMetricTest
  ```

---

### RE-04 (Issue #858) — 2.3.0 Regression: Spurious Clearance Violations on Clean Boards
- **Impact:** Boards routed clean on v2.2.4 had 16–60 clearance violations in v2.3.0 due to intra-footprint pad gaps and board boundary false-positives dominating DRC scoring.
- **Resolution in Master:** Commit `b32877f93` cleanly separated `preExistingCount` vs `routerIntroducedCount` in `BoardStatistics`, exempted internal component pins from outline clearance, and calibrated edge clearance to 250 µm.
- **Verification Command Line:**
  ```powershell
  ./gradlew test --tests app.freerouting.fixtures.DevBoardClearanceRoutingTest
  # To test against user's original export, download ReyNeill's reproduction gist:
  # https://gist.github.com/ReyNeill/5f28499ea8a3962b9a74c760ec252ec0
  # ./gradlew run --args="-de Issue858-repro-kicad-export.dsn -do build/Issue858-out.ses -mp 50 -da -dct 0"
  ```

---

### RE-05 (Issue #558) — Board-Edge Clearance Class Override
- **Status:** PR [#567](https://github.com/freerouting/freerouting/pull/567) fixed non-zero layer edge clearance. Parameter `--router.copper_to_edge_clearance_um` was added. Upstream KiCad issue [GitLab #24077](https://gitlab.com/kicad/code/kicad/-/work_items/24077).
- **Test Command Line:**
  ```powershell
  ./gradlew test --tests app.freerouting.fixtures.DevBoardClearanceRoutingTest
  ./gradlew run --args="-de fixtures/Issue558-dev-board.dsn -do build/Issue558-out.ses --router.copper_to_edge_clearance_um=500"
  ```

---

### RE-06 (Issue #632) — Panel Design with Pins Outside PCB Boundary
- **Status:** Commit `b32877f93` implemented `validateBoardDesignErrors()` in `HeadlessBoardManager.java`, emitting clear warnings for components outside the boundary.
- **Test Command Line:**
  ```powershell
  ./gradlew run --args="-de \"fixtures/Issue632-MiniAutoPilot/Mini Auto Pilot.dsn\" -do build/Issue632-out.ses -mp 5"
  ```

---

### RE-07 (Issue #523) — Redundant / Duplicate Track Stubs
- **Status:** Extensively investigated by BanjoR against current master. SES import and export normalize paths; no production reproducer found.
- **Test Command Line:**
  ```powershell
  ./gradlew test --tests app.freerouting.fixtures.Dac2020Bm01RoutingTest
  ```

---

## Part 3: Pull Request Evaluation (`routing-engine` label)

| PR # | Title | State | Head Branch | Base Branch | Fixes Issue | Compatibility with Master | Recommendation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **#887** | Optimize routing memory allocation and update CI actions | **MERGED** | `research/peak-heap-allocation-optimization` | `master` | General perf | Merged Sep 14, 2026 | Already in master |
| **#892** | Fix single-sided board regression by updating optimizer board reference | **MERGED** | `fix/v2.5-routing-engine-improvements` | `master` | **#872 (RE-01)** | Merged Sep 15, 2026 | Synchronizes board instance in `BatchOptimizer`. Fixture & regression test added. |
| **#901** | Fix search-tree query/removal race (closes #798) | **MERGED** | `fix-issue-798-search-tree-npe` | `master` | **#798 (RE-02)** | Merged Sep 15, 2026 | Per-tree read/write lock, thread-local traversal stack, query-local IDs; supersedes #817. |
| **#902** | Fix optimizer worker trace metric baseline (closes #811) | **MERGED** | `fix/issue-811-optimizer-trace-metric` | `master` | **#811 (RE-03)** | Merged Sep 15, 2026 | Weighted trace metric baseline in unified `BatchOptimizer`; supersedes #818. |
| **#904** | Fix '+' path parsing, enhance benchmark runner, and record 2.5.0-RC5 results | **MERGED** | `fix/benchmark-isolation-and-plus-path-parsing` | `master` | Runner / RC5 | Merged Sep 16, 2026 | Fixes path parsing for dots/pluses, per-worker user data isolation, graceful worker drain, 2.5.0-RC5 benchmark (Score: 953.5). |
| **#817** | Fix search-tree query/removal race | **SUPERSEDED** | `fix-issue-798-search-tree-npe` | `master` | **#798 (RE-02)** | Superseded by PR #901 | Superseded and resolved by PR #901 |
| **#818** | Fix optimizer worker trace metric baseline | **CLOSED** | `investigate-issue-811-optimizer` | `master` | **#811 (RE-03)** | Superseded by PR #902 | Closed with comment pointing to merged PR #902 |
| **#820** | Fail fast on DSNs with pins outside PCB boundary | **OPEN** | `fix-issue-632-multi-board-routing` | `master` | **#632 (RE-06)** | **Medium**. Master already implemented soft-warning `validateBoardDesignErrors()`. Making it fatal might break valid edge connectors. | **SUPERSEDED by master's soft warnings; CLOSE or add opt-in flag** |
| **#793** | Draft: headless fixes, Specctra (type protect) handling, and routing heuristic experiments | **OPEN** | `2bee-router-optimizations` | `master` | Heuristics / leaks | **Low (261 commits behind master)**. Author explicitly advised in PR description NOT to merge routing heuristics after 101-board corpus test showed regression. | **DO NOT MERGE HEURISTICS. Cherry-pick only `IntPoint.hashCode()` and `(type protect)` if needed.** |
| **#743** | Add RoutingEtaCalculator and improve ETA functionality | **OPEN** | `Feat--Routing-ETA-Caluclator` | `master` | UI feature | **Low**. Touches obsolete `interactive.AutorouterAndRouteOptimizerThread`. Author noted accuracy is only ±10-12% and needs multi-phase rework. | **DEFER to post-v2.5** |

---

## Part 4: Action Plan for v2.5.0 Release

### Phase 1: High-Priority Fixes (The v2.5 Cut)

- [x] **Resolve RE-01 (Issue #872) — Single-Sided Board Optimizer Handoff Regression**
  - [x] Synchronize `this.board = this.job.board;` at entry of `BatchOptimizer.runBatchLoop()`.
  - [x] Add reproduction test fixture `fixtures/Issue872-single-layer-repro.dsn`.
  - [x] Add automated regression test `src/test/java/app/freerouting/fixtures/Issue872SingleLayerRoutingTest.java`.
  - [x] Merge PR: [#892](https://github.com/freerouting/freerouting/pull/892) and close [#872](https://github.com/freerouting/freerouting/issues/872).

- [x] **Resolve RE-02 (Issue #798) & Merge PR #901 — Search-Tree Query/Removal Race**
  - [x] Merge PR branch `fix-issue-798-search-tree-npe` with modern `master` architecture (`board.searchtree` package refactoring, Java 25 sealed tile hierarchy).
  - [x] Resolve merge conflicts in `MinAreaTree.java` and `ShapeSearchTree.java`.
  - [x] Add `onNodeVisited()` hook to `MinAreaTree` and adapt `MinAreaTreeLeafLifecycleTest` for `List<Leaf>`.
  - [x] Convert `completeShapeStack` to `ThreadLocal<ArrayStack<TreeNode>>` in `ShapeSearchTree`, `ShapeSearchTree45Degree`, and `ShapeSearchTree90Degree` for concurrent query safety.
  - [x] Replace static `lastGeneratedEntryId` with query-local `nextEntryId++` in `EntrySortedByClearance`, eliminating cross-instance race conditions.
  - [x] Merge PR: [#901](https://github.com/freerouting/freerouting/pull/901) and close [#798](https://github.com/freerouting/freerouting/issues/798).

- [x] **Port PR #818 Fix into Unified BatchOptimizer (Issue #811 / RE-03)**
  - [x] In `src/main/java/app/freerouting/autoroute/pipeline/BatchOptimizer.java`, apply weighted trace length baseline calculation for worker candidate evaluations (`baseline = baselineTraceLength > 0 ? baselineTraceLength : boardStatisticsBefore.traces.totalWeightedLength;`).
  - [x] Use `totalWeightedLength` consistently for both before baseline and after evaluation in `ItemRouteResult`.
  - [x] Add `BatchOptimizerTraceMetricTest.java` in package `app.freerouting.autoroute.pipeline`.
  - [x] Verify quality gates (`spotlessCheck`, `checkstyleTest`, `./gradlew test`).
  - [x] Merge PR: [#902](https://github.com/freerouting/freerouting/pull/902) and close [#811](https://github.com/freerouting/freerouting/issues/811).

- [x] **Benchmark Runner Resilience & 2.5.0-RC5 Benchmark Milestone**
  - [x] Fix path parsing for filenames with dots and pluses (`board.v1+final.dsn`).
  - [x] Isolate per-worker user data directories (`scripts/benchmark/.user_data/worker-{wid}`) to eliminate lock contention on `recent_files.json`.
  - [x] Implement graceful stop (ESC / Q waits for active subprocesses to complete without starting new ones).
  - [x] Complete full 1,157 PCBench benchmark run:
    - **Total Duration:** 2,137.9s (fastest release to date; vs RC4 2,233.5s and v2.4.1 3,251.0s).
    - **Average Score:** **953.5** (project record).
    - **Failures / Timeouts:** 0 / 1,157 (0.0%).
  - [x] Merge PR: [#904](https://github.com/freerouting/freerouting/pull/904).

- [x] **Verify & Close RE-04 (Issue #858)**
  - [x] Run verification on vanilla KiCad 10 DSN exports (`DevBoardClearanceRoutingTest`).
  - [x] Post results demonstrating zero router-introduced violations in v2.5 and close issue [#858](https://github.com/freerouting/freerouting/issues/858).

---

### Phase 2: Issue Housekeeping & Label Management

- [x] **Close Superseded PR #818:** Added comment pointing to merged PR [#902](https://github.com/freerouting/freerouting/pull/902) and closed PR.
- [x] **Close Issue #558 (RE-05):** Added summary comment pointing to `--router.copper_to_edge_clearance_um` and the upstream KiCad issue [GitLab #24077](https://gitlab.com/kicad/code/kicad/-/work_items/24077); closed issue.
- [x] **Close Issue #632 (RE-06):** Documented structured `WARNING` logs emitted in v2.5 by `validateBoardDesignErrors()` without aborting valid boards; closed issue.
- [x] **Close Issue #582 (RE-09):** Provided summary of high-density routing recommendations and closed question as answered.
- [x] **Update Issue #750 (RE-08):** Removed `routing-engine` label; kept tagged as `GUI`.
- [x] **Update Issue #523 (RE-07):** Added `missing-info` label for unreproduced track stubs.

---

### Phase 3: Long-Term Roadmap (v2.6+ / v3.0)

- [ ] **v2.6 Milestone:**
  - [ ] [#718](https://github.com/freerouting/freerouting/issues/718) (Net-ties / overlapping pad DRC exemption using `Issue718-Allow_Net-Ties` fixture).
  - [ ] [#716](https://github.com/freerouting/freerouting/issues/716) (Automatic trace length tuning / serpentine patterns).
  - [ ] [#383](https://github.com/freerouting/freerouting/issues/383) (Star-ground routing topology).
- [ ] **v3.0 Milestone:**
  - [ ] [#879](https://github.com/freerouting/freerouting/issues/879) (Full arbitrary non-convex padstack data structures).


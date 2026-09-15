# Routing Engine Issues and PR Analysis for Freerouting v2.5

**Date:** September 15, 2026
**Target Release:** Freerouting v2.5.0
**Branch:** `fix/v2.5-routing-engine-improvements` / `fix-issue-798-search-tree-npe`
**Status:** In Progress — RE-01 (PR #892) & RE-02 (PR #901) resolved and under PR review

---

## Executive Summary

A comprehensive audit was performed across all GitHub issues and Pull Requests labeled with `routing-engine`. In total, **16 issues** and **8 pull requests** carry this label.

- **Issues (16 Total):**
  - **3 Closed:** Handled and verified in previous milestones ([#152](https://github.com/freerouting/freerouting/issues/152), [#837](https://github.com/freerouting/freerouting/issues/837), [#848](https://github.com/freerouting/freerouting/issues/848)).
  - **4 Critical Engine & Pipeline Fixes (v2.5 Cut):** [#872](https://github.com/freerouting/freerouting/issues/872) (resolved in PR #892), [#798](https://github.com/freerouting/freerouting/issues/798) (resolved in PR #901), [#811](https://github.com/freerouting/freerouting/issues/811), [#858](https://github.com/freerouting/freerouting/issues/858).
  - **3 CAD / DRC Integration & Input Quirks:** [#558](https://github.com/freerouting/freerouting/issues/558), [#632](https://github.com/freerouting/freerouting/issues/632), [#523](https://github.com/freerouting/freerouting/issues/523).
  - **2 Non-Engine / Stale / GUI:** [#750](https://github.com/freerouting/freerouting/issues/750), [#582](https://github.com/freerouting/freerouting/issues/582).
  - **4 Long-Term Architectural Enhancements (v2.6+ / v3.0):** [#879](https://github.com/freerouting/freerouting/issues/879), [#716](https://github.com/freerouting/freerouting/issues/716), [#718](https://github.com/freerouting/freerouting/issues/718), [#383](https://github.com/freerouting/freerouting/issues/383).

- **Pull Requests (8 Total):**
  - **1 Merged:** PR [#887](https://github.com/freerouting/freerouting/pull/887) (Memory allocation & benchmark throughput).
  - **2 New Resolved PRs for v2.5:** PR [#892](https://github.com/freerouting/freerouting/pull/892) (fixes [#872](https://github.com/freerouting/freerouting/issues/872)), PR [#901](https://github.com/freerouting/freerouting/pull/901) (fixes [#798](https://github.com/freerouting/freerouting/issues/798), supersedes PR [#817](https://github.com/freerouting/freerouting/pull/817)).
  - **1 High-Value Open PR for v2.5:** PR [#818](https://github.com/freerouting/freerouting/pull/818) (addresses [#811](https://github.com/freerouting/freerouting/issues/811) baseline metric, needs rebase onto unified `pipeline.BatchOptimizer`).
  - **1 Open Input-Validation PR:** PR [#820](https://github.com/freerouting/freerouting/pull/820) (addresses [#632](https://github.com/freerouting/freerouting/issues/632), soft-warning already on master).
  - **2 Open Experimental / Stale PRs:** PR [#793](https://github.com/freerouting/freerouting/pull/793) (author advises against merging heuristics), PR [#743](https://github.com/freerouting/freerouting/pull/743) (ETA calculator, package conflicts with modern GUI/scheduler).
  - **1 Superseded PR:** PR [#817](https://github.com/freerouting/freerouting/pull/817) (superseded by PR [#901](https://github.com/freerouting/freerouting/pull/901)).

---

## Part 1: Issue Index & Validity Matrix

Each issue is assigned a persistent local identifier (`RE-01` through `RE-16`).

| Local ID | GitHub Issue | Title / Scope | Validity Assessment | v2.5 Recommendation |
| :--- | :--- | :--- | :--- | :--- |
| **RE-01** | [#872](https://github.com/freerouting/freerouting/issues/872) | Single-sided regression: optimizer starts from worse board than autorouter | **RESOLVED IN PR #892**. `BatchOptimizer.board` instance handoff re-synchronized; regression tests and fixture added. | **MERGE PR #892 (P0)** |
| **RE-02** | [#798](https://github.com/freerouting/freerouting/issues/798) | NPE in `PullTightAlgo` during interactive routing | **RESOLVED IN PR #901** (supersedes [#817](https://github.com/freerouting/freerouting/pull/817)). Read/write locks, thread-local traversal stack, query-local IDs. | **MERGE PR #901 (P0)** |
| **RE-03** | [#811](https://github.com/freerouting/freerouting/issues/811) | Optimizer discarding accepted trace improvements | **PARTIALLY VALID**. Board reuse, hygiene & guards merged. Worker trace metric fix pending in PR #818. | **PORT PR #818 (P1)** |
| **RE-04** | [#858](https://github.com/freerouting/freerouting/issues/858) | 2.3.0 regression: clearance violations on clean boards | **RESOLVED IN MASTER**. Commit `b32877f93` separated pre-existing DRC violations and added pin containment exemption. | **VERIFY & CLOSE (P1)** |
| **RE-05** | [#558](https://github.com/freerouting/freerouting/issues/558) | Board-edge clearance class override | **HANDLED IN FREEROUTING**. PR #567 merged; `--router.copper_to_edge_clearance_um` added; upstream KiCad block. | **CLOSE / DOCUMENT (P2)** |
| **RE-06** | [#632](https://github.com/freerouting/freerouting/issues/632) | Crash on multi-board panels with pins outside PCB boundary | **HANDLED IN MASTER**. `validateBoardDesignErrors()` logs structured warnings. PR #820 provides strict fail-fast. | **CLOSE #632 (P2)** |
| **RE-07** | [#523](https://github.com/freerouting/freerouting/issues/523) | Unrouted/redundant track stubs left after passes | **NOT REPRODUCIBLE**. Tested by BanjoR; Specctra import/export already normalizes stubs. | **NEEDS REPRODUCER (P3)** |
| **RE-08** | [#750](https://github.com/freerouting/freerouting/issues/750) | GUI component dragging lag and ratsnest | **NOT AN ENGINE BUG**. Swing interaction event lag from PR #749. | **REMOVE LABEL / GUI (P3)** |
| **RE-09** | [#582](https://github.com/freerouting/freerouting/issues/582) | High-density small-area PCB routing guidance | **QUESTION / ANSWERED**. Comprehensive recommendations provided in thread. | **CLOSE AS ANSWERED (P3)** |
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
  - Remaining: worker trace baseline was starting at `0.0`. PR [#818](https://github.com/freerouting/freerouting/pull/818) fixes this, but needs rebasing into `app.freerouting.autoroute.pipeline.BatchOptimizer`.
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
| **#892** | Fix single-sided board regression by updating optimizer board reference | **OPEN** | `fix/v2.5-routing-engine-improvements` | `master` | **#872 (RE-01)** | **High**. Fixes stale board instance in `BatchOptimizer`. Fixture & regression test added. | **MERGE for v2.5** |
| **#901** | Fix search-tree query/removal race (closes #798) | **OPEN** | `fix-issue-798-search-tree-npe` | `master` | **#798 (RE-02)** | **High**. Supersedes #817; resolves merge conflicts with master, adds thread-local traversal stack & query-local tie-breakers. | **MERGE for v2.5** |
| **#817** | Fix search-tree query/removal race | **SUPERSEDED** | `fix-issue-798-search-tree-npe` | `master` | **#798 (RE-02)** | **High**. Merged and superseded by PR #901 after resolving conflicts with master. | **SUPERSEDED by #901** |
| **#818** | Fix optimizer worker trace metric baseline | **SUPERSEDED** | `investigate-issue-811-optimizer` | `master` | **#811 (RE-03)** | **Ported**. Ported to unified `pipeline.BatchOptimizer` with weighted trace length baseline and unit tests on branch `fix/issue-811-optimizer-trace-metric`. | **SUPERSEDED by modern port for v2.5** |
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
  - [x] Create PR: [#892](https://github.com/freerouting/freerouting/pull/892).

- [x] **Resolve RE-02 (Issue #798) & Merge PR #817 / PR #901 — Search-Tree Query/Removal Race**
  - [x] Merge PR branch `fix-issue-798-search-tree-npe` with modern `master` architecture (`board.searchtree` package refactoring, Java 25 sealed tile hierarchy).
  - [x] Resolve merge conflicts in `MinAreaTree.java` and `ShapeSearchTree.java`.
  - [x] Add `onNodeVisited()` hook to `MinAreaTree` and adapt `MinAreaTreeLeafLifecycleTest` for `List<Leaf>`.
  - [x] Push branch to `origin/fix-issue-798-search-tree-npe` and open PR: [#901](https://github.com/freerouting/freerouting/pull/901) (superseding [#817](https://github.com/freerouting/freerouting/pull/817)).
  - [x] Address Copilot review feedback:
    - [x] Convert `completeShapeStack` to `ThreadLocal<ArrayStack<TreeNode>>` in `ShapeSearchTree`, `ShapeSearchTree45Degree`, and `ShapeSearchTree90Degree` for concurrent query safety.
    - [x] Replace static `lastGeneratedEntryId` with query-local `nextEntryId++` in `EntrySortedByClearance`, eliminating cross-instance race conditions.
    - [x] Remove unused parameter in `onNodeVisited()` and format Javadoc/method calls.
  - [x] Verify quality gates (`spotlessCheck`, `checkstyleMain`, `checkstyleTest`, `./gradlew test`).
  - [x] Commit fixes to `fix-issue-798-search-tree-npe` (`54f5500cb`).

- [x] **Port PR #818 Fix into Unified BatchOptimizer (Issue #811 / RE-03)**
  - [x] In `src/main/java/app/freerouting/autoroute/pipeline/BatchOptimizer.java`, apply weighted trace length baseline calculation for worker candidate evaluations (`baseline = baselineTraceLength > 0 ? baselineTraceLength : boardStatisticsBefore.traces.totalWeightedLength;`).
  - [x] Use `totalWeightedLength` consistently for both before baseline and after evaluation in `ItemRouteResult`.
  - [x] Add `BatchOptimizerTraceMetricTest.java` in package `app.freerouting.autoroute.pipeline`.
  - [x] Verify quality gates (`spotlessCheck`, `checkstyleTest`, `./gradlew test`).
  - [x] Push branch `fix/issue-811-optimizer-trace-metric`.

- [ ] **Verify & Close RE-04 (Issue #858)**
  - [ ] Run verification on vanilla KiCad 10 DSN exports.
  - [ ] Post results demonstrating zero router-introduced violations in v2.5 and close issue #858.

---

### Phase 2: Issue Housekeeping & Label Management

- [ ] **Close Issue #558 (RE-05):** Add summary comment pointing to `--router.copper_to_edge_clearance_um` and the upstream KiCad issue [GitLab #24077](https://gitlab.com/kicad/code/kicad/-/work_items/24077).
- [ ] **Close Issue #632 (RE-06):** Note that structured `WARNING` logs are emitted in v2.5 by `validateBoardDesignErrors()` without aborting valid boards.
- [ ] **Close Issue #582 (RE-09):** Mark as answered and link to `docs/architecture.md` / PCB routing tips.
- [ ] **Update Issue #750 (RE-08):** Remove `routing-engine` label; leave tagged as `GUI`.
- [ ] **Update Issue #523 (RE-07):** Tag with `needs-reproducer`; close if no modern repro is provided within 14 days.

---

### Phase 3: Long-Term Roadmap (v2.6+ / v3.0)

- [ ] **v2.6 Milestone:**
  - [ ] [#718](https://github.com/freerouting/freerouting/issues/718) (Net-ties / overlapping pad DRC exemption using `Issue718-Allow_Net-Ties` fixture).
  - [ ] [#716](https://github.com/freerouting/freerouting/issues/716) (Automatic trace length tuning / serpentine patterns).
  - [ ] [#383](https://github.com/freerouting/freerouting/issues/383) (Star-ground routing topology).
- [ ] **v3.0 Milestone:**
  - [ ] [#879](https://github.com/freerouting/freerouting/issues/879) (Full arbitrary non-convex padstack data structures).


# Freerouting Optimizer Unification Plan

**Document status:** Implementation Plan for Unified Optimizer  
**Date:** September 2026  
**Related roadmap:** [`code_structure_recommendations.md`](code_structure_recommendations.md)  
**Related settings:** [`../settings.md`](../settings.md)  

---

## Purpose

Unify the route optimizer across CLI, API, and GUI into **a single canonical `BatchOptimizer` class** without accepting correctness, determinism, logging, analytics, or performance regressions.

This plan removes `BatchOptimizerMultiThreaded` and all legacy multi-threading experimental code, unifies optimizer execution onto a **single thread-pool-based pipeline for all thread counts ($T \ge 1$)**, guarantees the preservation of the best board state (including the initial unoptimized state), and enforces **100% cross-thread and same-thread determinism** across all supported operational modes.

---

## Current Baseline & Identified Drift

| Dimension | Single-Threaded (`BatchOptimizer`) | Legacy Multi-Threaded (`BatchOptimizerMultiThreaded`) |
| :--- | :--- | :--- |
| **Active Usage** | CLI, API, and GUI (when `maxThreads == 1` or multi-threading feature flag is off). Primary maintained codebase. | GUI only, gated behind `featureFlags.multiThreading && maxThreads > 1`. |
| **Execution Architecture** | In-place synchronous loop on calling thread. | ThreadPoolExecutor with per-task board clones. |
| **Determinism** | Fully deterministic (sequential spatial sweep via `ReadSortedRouteItems`). | **Non-deterministic**: worker thread completion order determines greedy board swaps and tie-breaks; `RANDOM` uses unseeded shuffle. |
| **Board State Mutation** | In-place mutation with snapshot undo/redo (`generateSnapshot`, `popSnapshot`, `undo`). Very low memory overhead. | Each task performs an eager `job.board.deepCopy()`. Queuing all items upfront creates massive heap allocation and GC churn. |
| **Update Strategies** | Immediate sequential update on each improved item. | Supports `GREEDY` (race condition between threads), `GLOBAL_OPTIMAL` (best candidate per pass), and `HYBRID`. |
| **Item Selection** | Spatial sweep: sorted by coordinate (X ascending, Y ascending) and layer (vias first, traces second). | Supports `SEQUENTIAL`, `PRIORITIZED` (`PriorityQueue` based on past pass metrics), and `RANDOM` (`Collections.shuffle`). |
| **Pass Early Exit** | Stops early if `maxConsecutiveFailures` (default 50) is reached. | Queues all items upfront; lacks consecutive failure early exit. |
| **Initial / Best State** | Mutates board only on item-level score improvement; lacks an explicit baseline snapshot to restore if later passes regress or fail. | Does not preserve initial board as an explicit fallback if all passes fail or degrade. |
| **Logging & Metrics** | Standardized `INFO` logs with `ThreadMXBean` CPU time, memory allocation, peak heap usage, and structured score formatting. | Emits mostly `DEBUG` logs; does not aggregate worker pool CPU/memory metrics; different message templates. |

---

## Goals

1. **Single Unified Engine & Execution Path:**
   - Merge all capabilities into a single `BatchOptimizer` class.
   - **Uniform Thread Pool Pipeline:** Use a consistent `ThreadPoolExecutor` worker pool for all thread counts, including `maxThreads = 1`. Eliminating divergent single-threaded vs multi-threaded execution loops vastly simplifies maintenance, testing, and debugging.
2. **Absolute Determinism:**
   - **Zero randomness:** Completely eliminate `ItemSelectionStrategy.RANDOM` and all unseeded or randomized logic.
   - **Cross-Thread Determinism:** In `GLOBAL_OPTIMAL` mode with `SEQUENTIAL` selection, the optimization result is mathematically identical across all thread counts ($T=1, 2, 4, 8, \dots$). Whether running on 1 thread or 32 threads, every candidate in a pass is evaluated and reduced via a stable deterministic comparator, selecting the exact same sequence of improvements.
3. **Best-Board State Guarantee (Initial State Baseline):**
   - The optimizer captures the initial board state and its score prior to pass 1.
   - A `bestBoard` clone is always retained. When a pass achieves a new best score, the previous checkpoint is released and updated with the new best board.
   - If no optimization pass improves the board beyond the initial score, or if subsequent passes degrade the score, the `bestBoard` (or initial board) is restored before finishing.
4. **Preserve Single-Threaded Contracts:**
   - Retain the single-threaded logging format, event firing (`TaskStateChangedEvent`, `BoardUpdatedEvent`), throttled progress, and resource usage metrics (`ThreadMXBean` CPU seconds, total allocated memory, peak heap) aggregated across pool workers.
5. **Memory Safety:**
   - Eliminate eager `deepCopy()` of the board for all board items simultaneously.
   - Employ bounded task submission (lazy task creation or worker-local board reuse) to prevent OOM on large boards.
6. **Performance Parity:**
   - At `maxThreads = 1`, thread pool overhead must be negligible, meeting or exceeding single-threaded baseline performance.
   - At `maxThreads > 1`, demonstrate scalable throughput without correctness or DRC degradation.

---

## Non-Goals

- Do not change router heuristics, maze search, pull-tight algorithms, or DRC clearance calculations.
- Do not support non-deterministic execution modes under any configuration flag.
- Do not maintain branching "single-threaded vs multi-threaded" code paths in `BatchOptimizer`.
- Do not apply `ItemSelectionStrategy` or `BoardUpdateStrategy` to `BatchAutorouter` or `BatchFanout`:
  - `BatchFanout` requires component-centric geometric ordering (`pinSortingOrder`, e.g., `outer_first`), which is domain-specific to component footprint escape.
  - `BatchAutorouter` operates on full passes with ripup and unrouted net queues; altering its selection/update semantics would disrupt routing completion parity and baseline DRC metrics. These strategies are strictly scoped to post-route optimization.

---

## Strategic Decisions

### 1. Unified Thread Pool Execution Architecture
- Rather than maintaining two execution algorithms (an in-place synchronous snapshot loop for 1 thread and a thread-pool worker loop for $>1$ threads), the unified `BatchOptimizer` executes **via the thread pool across all configurations**:
  - `maxThreads = 1`: `Executors.newFixedThreadPool(1)` processes tasks through the exact same deterministic pipeline.
  - `maxThreads > 1`: `Executors.newFixedThreadPool(maxThreads)` executes candidate evaluations concurrently.
- **Benefits:**
  - One single codebase and loop to maintain and test.
  - Bug fixes, metric sampling, progress throttling, and cancellation logic apply identically everywhere.
  - No behavioral branching between headless (often tested on single core in CI) and desktop GUI (multi-core).

### 2. Retention of Clean Deterministic Strategies Only
- **`ItemSelectionStrategy`**:
  - Keep **`SEQUENTIAL`**: Deterministic spatial sweep (`ReadSortedRouteItems`).
  - Keep **`PRIORITIZED`**: Deterministic ordering based on metric gains (`ItemRouteResult`) from previous passes, with item ID as a stable tie-breaker.
  - Remove **`RANDOM`**: Completely deleted from codebase, CLI, and GUI.
- **`BoardUpdateStrategy`**:
  - Keep **`GLOBAL_OPTIMAL`**: Evaluates candidate items concurrently against the current pass baseline and deterministically applies the single best improvement.
  - Remove **`GREEDY`** and **`HYBRID`**.

### 3. Absolute Cross-Thread Determinism
- In `GLOBAL_OPTIMAL` with `SEQUENTIAL` selection:
  - The evaluation set for pass $N$ is identical.
  - Workers evaluate candidate items against the current baseline board.
  - Results are gathered into a deterministic list and reduced via a stable comparator:
    1. Incomplete connection count reduction (descending).
    2. Via count reduction (descending).
    3. Trace length reduction (descending).
    4. Item ID (ascending) as the absolute tie-breaker.
  - Result: **The identical winning candidate is selected regardless of thread count ($T=1$ vs $T=16$).**

### 4. Always Maintain `bestBoard` Clone
- Capture a deep copy of the board before pass 1 as `bestBoard` with `bestScore = initialScore`.
- After each pass:
  - If `passScore > bestScore`: Release the previous `bestBoard` and clone the new winning board as `bestBoard`.
  - If the optimizer finishes or aborts and `currentBoard.score < bestScore`: Restore `job.board = bestBoard`.
- This ensures the optimizer *never* leaves a board in a state worse than when it started.

### 5. Complete Removal of `BatchOptimizerMultiThreaded`
- Remove `BatchOptimizerMultiThreaded.java`.
- Fold task execution into private helper logic inside `BatchOptimizer`.
- Remove legacy options and unused fields (`HYBRID`, `hybridRatio`, `RANDOM`, `GREEDY`).
- Clean up documentation, settings schemas, and tests referencing the removed class.

---

## Actionable Work Packages

### Phase 1: Settings & Strategy Cleanup
- [ ] Remove `RANDOM` from `ItemSelectionStrategy`. Deprecate/remove any UI or CLI references.
- [ ] Remove `GREEDY` and `HYBRID` from `BoardUpdateStrategy` and `OptimizerSettings`.
- [ ] Ensure settings merger and CLI/GUI bindings only allow `SEQUENTIAL`, `PRIORITIZED`, and `GLOBAL_OPTIMAL`.

### Phase 2: Memory-Safe Parallel Worker Architecture
- [ ] Refactor pool execution so that tasks do not eagerly clone `job.board` for all items upfront:
  - Use a bounded work queue (e.g. queue capacity = `maxThreads * 2`) where workers acquire the current board snapshot lazily.
  - Or assign reusable worker boards per thread to avoid repeated allocations.
- [ ] Ensure thread pools shut down cleanly and handle interruption/timeouts cooperatively.

### Phase 3: Deterministic Candidate Selection & Tie-Breaking
- [ ] Enforce strict deterministic comparator in `ItemRouteResult`:
  1. Incomplete connection count reduction (descending).
  2. Via count reduction (descending).
  3. Trace length reduction (descending).
  4. Item ID (ascending) as the absolute tie-breaker.
- [ ] In `GLOBAL_OPTIMAL`, collect results across all workers and reduce them deterministically using the stable comparator. Worker finish order must never dictate candidate selection.

### Phase 4: Unified `BatchOptimizer` Implementation
- [ ] Implement the thread-pool execution loop directly inside `BatchOptimizer` for all thread counts ($T \ge 1$).
- [ ] Implement `bestBoard` retention:
  - Record initial board state and score before pass 1.
  - Retain `bestBoard` clone, updating it only when a pass score strictly exceeds `bestScore`.
  - Restore `bestBoard` if the final score is lower than `bestScore`.
- [ ] Support `maxConsecutiveFailures` and deadline checks in the pass loop.
- [ ] Delete `BatchOptimizerMultiThreaded.java` and `OptimizeRouteTask.java`.
- [ ] Replace `BatchOptimizer.createForHeadless` and `createForGui` with a single canonical factory method `BatchOptimizer.create(RoutingJob)`.

### Phase 5: Logging, Metrics, and Event Contract
- [ ] Aggregate CPU time across worker threads using `ThreadMXBean` so pool workers report accurate total CPU consumption.
- [ ] Emit identical `INFO` logs for stage start, per-pass completion, and stage summary.
- [ ] Ensure `BoardUpdatedEvent` and `TaskStateChangedEvent` fire deterministically at pass boundaries with correct statistics.

### Phase 6: Verification & Benchmarking
- [ ] **Cross-Thread Determinism Test:** Verify that `GLOBAL_OPTIMAL` produces 100% identical board states, metric scores, and DRC results whether running with 1, 2, 4, or 8 threads.
- [ ] **Initial Score Retention Test:** Verify that if an optimization pass degrades or fails to improve the board, the initial/best board state is preserved.
- [ ] **Memory & Performance Benchmark:** Run golden fixtures (`Issue508-DAC2020_bm01.dsn`, large multi-net boards) and record wall time, CPU time, peak heap, and cumulative allocation.
- [ ] Ensure all ArchUnit module boundary rules pass.

---

## Acceptance Gate

This plan is complete only when:

1. CLI, API, and GUI use the single unified `BatchOptimizer` class.
2. `BatchOptimizerMultiThreaded` is completely removed from the repository.
3. Execution runs through the same thread-pool pipeline regardless of thread count ($T \ge 1$).
4. In `GLOBAL_OPTIMAL` mode, results are identical across different thread counts ($T=1, 2, 4, 8$).
5. No random selection or non-deterministic greedy thread-race logic exists.
6. If optimization yields no score improvement, the initial board state is guaranteed to be preserved.
7. Single-threaded performance (`maxThreads = 1`) exhibits no regression against the current baseline.
8. Multi-threaded execution achieves throughput scaling without excessive heap allocation or GC stalls.




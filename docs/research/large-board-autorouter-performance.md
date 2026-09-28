# Large-board autorouter performance

Status: 2.5.0-RC10 contains phases 1 and 2. Phase 3 lookahead stays off. Replaying a stale plan, and adopting the deserialized board, each scored 58.95 / 1500 on the 128-item Aleste slice against the serial 59.35 / 1499, with 48 violations. Discarding every worker result restores 59.35 / 1499 and makes the pass slower (20.50 s versus 8.08 s) because the copy still runs. The production pass stays single-threaded unless `-Dfreerouting.autoroute.snapshot_commit=true`. Phases 4 and 5 are not started.

Determinism rule for every phase: the same board produces the same routes on one thread and on many threads. Workers may search ahead, but the main thread commits in today's item order. A search is discarded and rerun serially when an earlier commit changes the board, including when the recorded corridor misses that commit. The maze reads the whole occupancy, so a path found without the earlier copper is a different route. Finish-order commit is not used. The maze still mutates the `ShapeSearchTree` on the board it searches, so a prepared route replaces the live board only when that board is unchanged.

The batch loop calls `AutoroutePassRunner.runSingleThread`. `router.autorouter.max_threads` is kept for phase 3. The optimizer pool (`router.optimizer.max_threads`) is unchanged.

Measurements below are from the 2.5.0-RC9 corpus and from one profiled pass (`-Dfreerouting.benchmark.profile=true`, `max_items=8`) on a 2026-09-25 jar. Speedups in the table are planning ranges for the autorouter pass, not measured results of these designs.

## What the profiles showed

Loading both boards took under a second. Pin count does not separate the slow boards from the fast ones.

| Board | What RC9 did | Profile, first 8 autorouter items |
|---|---|---|
| `perfplusplus_Ard-perf++` | 1383 pins. Fanout left 35 incompletes. Router finished in 32 s. | Maze 297 ms. Intermediate statistics 341 ms. Board statistics 536 ms. Incomplete scan 150 ms. |
| `Aleste-520EX_aleste` | 2210 pins, 645 cm², 2 layers. One pass took 1748 s and went from 1620 incompletes to 882. Optimizer never started. | Maze 614 ms of 621 ms item time. Statistics and the incomplete scan were under 120 ms combined. Fanout stopped after 19 s. |
| `newer-motor-controllers_si31-3` | Fully routed. Router 640 s, optimizer 807 s. | Not re-profiled. The optimizer calls a full board scan before and after every ripped trace. |
| `oskirby_logicbone` | 8 layers. Fanout 899 s, then 3 s of autorouter with the incomplete count unchanged. | Not re-profiled. Pass 1 walks every SMD pin. |

The first Aleste items cost about 77 ms of maze each. The RC9 pass averaged about 2.4 s per completed connection. Net-id order tries the cheap items first.

## Removed: clone-and-shuffle pass

`AutoroutePassRunner.runMultiThread` deep-copied the whole board once per thread, shuffled the full item list on each copy, and kept the highest-scoring board. `join` waited 1000 ms. `AutorouteBatchLoop` never called it. `BatchAutorouterThread` existed only for that path and has been deleted.

- [x] Delete `runMultiThread` and `BatchAutorouter.autoroutePassMultiThread`.
- [x] Delete `BatchAutorouterThread`.
- [x] Leave `router.autorouter.max_threads` in settings. The live pass reads it only when snapshot commit is enabled.

## Phase 1 — Incremental board score

Full `BoardStatistics` and `DesignRulesChecker.calculateAllIncompletes()` stay at phase boundaries and in the result manifest. The score loop (pass start, pass end, stagnation, board history, optimizer candidates) stops rebuilding them.

- One disjoint-set per net over pins and conduction areas. Inserting a trace that joins two components decrements that net's incomplete count.
- Removing a trace recomputes that net only. Union-find cannot split, and a rip-up usually touches one net.
- Trace length and via count are running totals, updated on the same insert and remove paths as the search tree.
- An optimizer candidate counts as improved when its via count and trace length drop and the touched nets do not gain an incomplete. It does not call `calculateAllIncompletes`.
- Clearance violations stay on the existing full DRC at pass boundaries.

The maze order does not change. The run stays deterministic when the incremental incomplete count matches a full scan. A mismatch would move stagnation and board-history restores, which changes later passes.

- [x] Add the per-net component index on `RoutingBoard`, updated when a trace or via is inserted.
- [x] On remove, recompute only the touched net.
- [ ] Keep running totals for trace length and via count. Weighted length stays a full scan: adding and subtracting floats does not match the scan's accumulation order, and that would change optimizer decisions.
- [x] Point stagnation, board history, and the optimizer incomplete count at the ledger. `BoardStatistics` still scans geometry for length, bends, and vias.
- [ ] Build a full `BoardStatistics` only at phase boundaries and for the result manifest.
- [x] Stop the optimizer from calling `calculateAllIncompletes` once per candidate.
- [x] Test that the incremental incomplete count equals `calculateAllIncompletes` at the end of a pass.
- [x] Profile the same eight-item pass on 2.5.0-RC10. Aleste `incomplete_drc_ms` went from 66.3 to 9.9. perfplusplus went from 149.6 to 3.4. `board_statistics_ms` stayed 473.8 on perfplusplus because the geometry scan is still there. Maze time was unchanged (Aleste 656 ms, perfplusplus 307 ms). Both passes kept the previous score and unrouted count (Aleste 13.64 / 1612, perfplusplus 957.86 / 28).
- [x] `Issue508-DAC2020_bm01` still scored 181.20 with 142 unrouted and 0 violations after the two-item autorouter limit. A full Tier D completion compare is the nightly run.

## Phase 2 — Fanout pass 1

Pass 1 walks every SMD pin on one thread. The per-pin budget is 10 s times the pass number, and later passes walk the same pins again at higher rip-up cost. Do not add `router.fanout.timeout`. The job clock still covers the stage.

- Attempt each pin once. A `FAILED` or `INSERT_ERROR` pin is not retried unless a later fanout in its component inserts or rips geometry inside that pin's halo.
- Pins that are already connected stay skipped.
- Walk component by component. Pins inside one component stay serial. A component that fails to escape does not occupy the stage beyond its own pins.
- After that single-thread behavior keeps the same escape count, run components whose halos do not overlap on separate threads.

The single-thread skip rule is deterministic. Parallel components are deterministic when their halos do not interact, so thread timing cannot change the geometry either component writes.

- [x] Record pins that return `FAILED` or `INSERT_ERROR`.
- [x] Skip a recorded pin on later passes unless its component changes geometry inside the pin halo. A successful escape in that component bumps the generation and allows a retry. The decision does not depend on thread timing.
- [x] Keep already-connected pins skipped.
- [x] Walk one component at a time, pins inside it serial. This was already the fanout order.
- [x] On `Issue508-DAC2020_bm01` the escaped-pin count stayed 184/187 and the two-item autorouter score stayed 181.20. Later passes no longer count the already-failed pins as fresh failures.
- [ ] Run non-overlapping component halos in parallel. Not done: fanout writes one `ShapeSearchTree`, and committing two components at once would make the routes depend on which thread finished first.
- [x] Do not add a fanout stage timeout.

## Parallel autorouter passes

Threads help only when a pass has work units that do not write the same `ShapeSearchTree`. Two mazes on different nets still conflict through clearance, and rip-up mutates foreign nets. These three phases are sequential: each one assumes the previous phase's acceptance checks passed. None of them is a return of the full-board clone.

| Idea | Potential impact | Risk | Deterministic? |
|---|---|---|---|
| Snapshot search, serial commit | Overlaps the maze, which is almost all of Aleste's item time. Does not make one search smaller. About 2–4× on a sparse pass at 4–8 threads if most commits succeed; about 1–1.5× on a dense board, where retries serialize the pass. | Medium. Rip-up moves from search time to commit time. A stale search must be thrown away or it will route through copper that an earlier item just took. | Yes, if the main thread commits in today's item order and reruns any search whose corridor an earlier commit touched. Committing in finish order is faster and is not deterministic. |
| Spatial bins with a halo | Overlaps short connections whose boxes do not meet. About 1.5–3× on the item-routing portion of boards like Keyboard and perfplusplus. Long Aleste airlines stay on the serial queue, so that pass stays close to today's time. | High. A halo smaller than clearance plus maximum trace width inserts violating traces across a bin boundary. A larger halo leaves little work that can actually run together. | Yes, if bin membership is a pure function of the board, bins that run together do not have touching halos, and items inside a bin keep the current comparator. |
| Coarse assignment, then detailed corridors | The only idea that shrinks the search. A corridor that is a fraction of a 645 cm² outline can cut Aleste's per-connection maze several times, and disjoint corridors then use the bin rules. Planning range about 4–10× on that class of pass when the corridor still contains a path. | Highest. A bad corridor makes the detailed maze fail on a connection the full-board maze would have routed. Completion can drop even when each run is repeatable. | Yes, if the assignment is deterministic, the detailed maze is the current maze clipped to the corridor, and disjoint corridors follow the bin rules. The routes will not match today's full-board maze. |

### Phase 3 — Snapshot search, serial commit

The result object from a maze has no geometry. `PlannedConnection` still records corners, and the pass can still replace the live board with the worker copy, but `SnapshotLookahead.poll` does not return that copy. Both commits lost a connection on the 128-item Aleste slice. The worker search still runs when the property is set, and the main thread routes every item itself.

- [ ] Maze against a read-only occupancy snapshot. Not done: each lookahead still owns a deep-copied board, and the maze still expands in that copy's search tree.
- [x] Move rip-up of a replayed connection to commit time. The inserter exists. The pass does not call it, because that insert is not the serial route.
- [x] Commit on the main thread in the current item order.
- [x] Discard every worker result and route the item on the live board. Adopting the snapshot, including when the live board has not changed, lost a connection on Aleste.
- [x] Require `router.autorouter.max_threads` greater than one. The pool is one worker, because each search still needs its own board copy.
- [x] Log `snapshot_retries`, `snapshot_adopted`, `snapshot_replays`, `snapshot_copy_ms`, and `snapshot_prepared_ms` on the `BENCHMARK_PROFILE` line.
- [x] Aleste with the property on, one pass, same fanout. Replay-and-adopt at 128 items: 11.56 s, score 58.95 / 1500. Adopt-only: 16.28 s, 58 adopts, same 58.95 / 1500. No commit: 20.50 s, 127 retries, 0 adopts, score 59.35 / 1499, matching the serial 8.08 s pass. Violations stayed 48. `Issue508-DAC2020_bm01` with the property unset still scored 181.20 with 142 unrouted and 0 violations.
- [x] Keep the pass serial unless `-Dfreerouting.autoroute.snapshot_commit=true`. With the property set, the worker result is still discarded. The lookahead stays off for the nightly run.

### Phase 4 — Spatial bins with a halo

- [ ] Partition items by the airline bounding box, expanded by clearance and the maximum trace width.
- [ ] Keep touching bins, and any airline that crosses a bin boundary, on one serial queue.
- [ ] Lock or copy only the tiles a bin can touch.
- [ ] Fix the bin order, and keep the current item comparator inside a bin.
- [ ] Test that traces inserted from two adjacent bins cannot land closer than the clearance.
- [ ] Measure Keyboard and perfplusplus against phase 3. Expect little gain on Aleste.

### Phase 5 — Coarse assignment, then detailed corridors

- [ ] Add a serial coarse pass that assigns a corridor to each incomplete.
- [ ] Restrict the detailed maze to that corridor.
- [ ] Run disjoint corridors in parallel under the phase 4 rules.
- [ ] If the corridor maze fails, fall back to one full-board search for that item.
- [ ] Compare completion and full DRC on a bounded Aleste run, on si31-3, and on a small fully routed fixture against the phase 1 router.
- [ ] Drop the corridor restriction if completion falls.

## Acceptance for the whole plan

- No new clearance violations from `DesignRulesChecker.getAllClearanceViolations()`.
- Completion on the fast fixture and on si31-3 does not fall.
- Aleste's profiled pass gets cheaper in maze time, or in wall time once phase 3 is on, without a drop in connections completed per item attempted.
- `router.fanout.timeout` and `router.optimizer.timeout` stay unset. The job clock remains the only stage limit.

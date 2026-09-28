# Large-board autorouter performance

Status: 2.5.0-RC10 contains phases 1 and 2. Phases 3, 4, and 5 were measured and then removed. None of them beat the single-thread pass by enough to keep the extra code. Fanout and the autorouter pass each run on one thread. The optimizer pool (`router.optimizer.max_threads`) is unchanged. `router.autorouter.max_threads` remains in settings and is not read by the pass.

The maze reads the whole occupancy and mutates the `ShapeSearchTree` on the board it searches. A path found without earlier copper is a different route. Finish-order commit is not used.

The batch loop calls `AutoroutePassRunner.runSingleThread`.

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
- [x] Leave `router.autorouter.max_threads` in settings. The live pass does not read it.

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
- [x] Do not run non-overlapping component halos in parallel. Fanout writes one `ShapeSearchTree`, and committing two components at once would make the routes depend on which thread finished first.
- [x] Do not add a fanout stage timeout.

## Rejected parallel autorouter passes

Threads help only when a pass has work units that do not write the same `ShapeSearchTree`. Two mazes on different nets still conflict through clearance, and rip-up mutates foreign nets. The three ideas below were measured. The code for all three has been removed. The pass stays the phase 1 and 2 router.

| Idea | Result | Decision |
|---|---|---|
| Snapshot search, serial commit | Aleste, 128 items, fanout on. Same score as serial: 59.35, 1499 unrouted, 48 violations. Pass 16.62 s versus 8.08 s serial. A rebuilt search tree scored 58.95 / 1500. Cloning the live tree restored the serial score and was still slower. | Rejected. A prepared search is valid only when the previous item did not change the board, and that item then skipped its maze. The copy and the wait cost more than the search. |
| Spatial bins with a halo | perfplusplus, 32 items, fanout on. Bin commit 3.67 s versus 5.18 s serial, score 974.42 versus 993.98, 17 unrouted versus 4, 0 violations. | Rejected. Reinserting copied traces is not the maze result. Rip-up and trace split did not survive. |
| Coarse corridor, then the detailed maze | Same SES file as the full-board maze on every measured slice. perfplusplus 32 items: maze 392 ms versus 589 ms, pass 4.80 s versus 5.18 s, still 4 unrouted and 0 violations. Aleste 32 items: maze 1469 ms versus 1785 ms, pass 2.79 s versus 3.24 s, still 1588 unrouted and 48 violations. si31-3 16 items, fanout off: maze 800 ms versus 761 ms, same 875 unrouted and 1 pre-existing violation. bm01 2 items: both 188.03, 140 unrouted, 0 violations. | Rejected. The routes did not change. The maze saving is a few tenths of a second on the cheap prefix. The full Aleste pass is 1748 s. The clip is not worth the extra maze code. |

## Acceptance for what remains

- No new clearance violations from `DesignRulesChecker.getAllClearanceViolations()`.
- Phase 1 keeps the incremental incomplete count. On the eight-item profile, Aleste `incomplete_drc_ms` went from 66.3 to 9.9 and perfplusplus from 149.6 to 3.4, with the previous score and unrouted count.
- Phase 2 keeps the serial fanout skip. bm01 escape stayed 184/187 and the two-item autorouter score stayed 181.20. Parallel fanout is not added.
- `router.fanout.timeout` and `router.optimizer.timeout` stay unset. The job clock remains the only stage limit.

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

Pass 1 walks every SMD pin on one thread. The per-pin budget is 10 s times the pass number, and later passes walk the same pins again at higher rip-up cost. `router.fanout.timeout` is a real setting and stays unset in the defaults. Set `--router.fanout.timeout` only when a fanout stage budget is wanted. When it is unset, `router.job_timeout` still covers fanout, autorouting, and optimization together.

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
- [x] Leave `router.fanout.timeout` unset by default. The setting stays available when a fanout stage budget is wanted.

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
- `router.fanout.timeout` and `router.optimizer.timeout` stay unset in the defaults. Both remain configurable. When they are unset, the job clock is the only stage limit.

## Tier D clock stops, 2.5.0-RC10

The Tier D nightly on 2026-09-28 used `router.job_timeout=00:45:00` (22 boards). Fully routed went from 8 on RC9 to 11 on RC10. The three new completions are `CoreOne-xCORE200-Original_CoreOne` (RC9 stopped at the old 30-minute clock with 5 unrouted), `Horticulture_Light_EQ_3-Band_light` (1 unrouted), and `m2-electronics_m2fc` (2 unrouted). The summary table counted 4 timeouts, up from 2. That count is the manifest `final_state`, and it is not the set of boards whose autorouter ran out of clock.

`RoutingJobSchedulerActionThread` logs `Auto-routing stage completed with timeout` when `timeoutAt` has passed and a stop was requested. It writes `finished with state: COMPLETED` when the pipeline returns while `job.state` is still `RUNNING`. The monitor thread can then assign `TIMED_OUT` after that log line, because it samples `RUNNING`, waits out the grace period, and writes `TIMED_OUT` without checking the state again. The CLI manifest copies whichever value it sees. RC9 therefore recorded 2 manifest timeouts while 11 autorouter logs said `completed with timeout` at about 1800 s. RC10 recorded 4 manifest timeouts while 8 autorouter logs said the same phrase at about 2700 s. Log-level clock hits fell. The 2→4 delta is the race, not a new failure mode. Fanout finished on every one of these boards (50–183 s). The optimizer never started. Peak heap stayed between 488 MB and 995 MB. The allocator column is cumulative allocation, not live heap.

| Board | RC9 at the 30-minute clock | RC10 at the 45-minute clock | What the extra time did |
|---|---|---|---|
| `Aleste-520EX_aleste` | Pass 1 cut at 1748 s, 882 unrouted, 48 violations. Log timeout, manifest `COMPLETED`. | Pass 1 cut at 2646 s, 880 unrouted, 48 violations. Manifest `TIMED_OUT`. | Two more connections. Pass 1 was still open. |
| `DSP-ADAU1452_DSP-ADAU1452` | 3 passes. Best is pass 1: 40 unrouted. Pass 2 rips to 306, then the best board is restored. | 5 passes. Best is pass 1: 42 unrouted. Passes 2 and 4 rip to about 300. Pass 5 is 0 s and restores 42. | Did not beat pass 1. |
| `LeeChee_1800` | 3 passes. Best is pass 1: 86 unrouted. | 4 passes. Pass 1 still 86. Pass 2 rips to 313, pass 3 recovers to 158, pass 4 restores 86. | Same 86 unrouted. |
| `TCKB_Kicad_TCKB` | 3 passes, 82 unrouted. | 13 passes, 66 unrouted. The last two passes stay at 66. | Improved, then stuck. Manifest `TIMED_OUT`. |
| `KiCad-Library_Teensy_test_layout` | 4 passes, 69 unrouted, 79 violations. | 5 passes, 45 unrouted, 79 violations. Still moving on the last pass. | Improved. Manifest stayed `COMPLETED`. |
| `mechkeys_LFK78` | 4 passes, 113 unrouted. | 6 passes, 109 unrouted. Still moving. | Small gain. Manifest `COMPLETED`. |
| `mini_mass_prog_bench_prog_rig` | 2 passes, 39 unrouted. | 6 passes: 85, 49, 24, 11, 7, then 6. Pass times fell from 1056 s to 10 s. | Closest miss. Manifest `COMPLETED`. |
| `MonApollo_analog-board` | 5 passes, 48 unrouted, 58 violations. Manifest `TIMED_OUT`. | 11 passes. Best is 25 unrouted, then later passes rip back to 214 and restore 25. | Improved, then oscillated. Manifest `COMPLETED`. |

Two RC9 clock hits did not hit the RC10 clock. `dorkyboard_keyboard` reached the same 6 unrouted (best since pass 10) and stopped itself at 2536 s: the best score had not improved by more than 0.5 for `STAGNATION_PASS_LIMIT` (10) passes. `opendous_Upconverter` stopped the same way at 2037 s, pass 18, with the best board still pass 8 (224 unrouted, 8 violations) and about 12 minutes of job clock left. Later passes were worse and were restored.

### Why they stop

1. **Pass 1 is longer than the job.** `AutoroutePassRunner` checks the stop flag between items, not inside one maze. On Aleste the first pass is still walking the item list when the 45-minute clock fires. The RC9 profile already showed the maze as almost the whole item time, and the cheap nets go first, so the remaining items are the slow ones. Raising the budget from 30 to 45 minutes extended that same pass; it did not reach pass 2 or the optimizer.

2. **A pass that loses to the stored best still runs to the end.** DSP, LeeChee, MonApollo, and opendous each have an early pass that is the best score. The next pass rips hundreds of connections, board history restores the best, and the loop tries again until the clock or the 10-pass global tracker fires. A 0-second pass in the log is that restore, not a maze. The extra 15 minutes on DSP and LeeChee did not beat pass 1.

3. **Some boards are still improving when the clock fires.** mini_mass, Teensy, LFK78, and TCKB were still changing the unrouted count on the last completed pass. mini_mass was down to 6, with pass times already down to about 10 s. The job clock ended the autorouter before the optimizer. That is a budget fact, not a reason to branch the router on remaining time.

4. **dorkyboard stopped on the global tracker with 6 connections left.** The best score was pass 10. Ten later passes did not beat it by 0.5, so the loop stopped at pass 20 with time still on the clock. Last-mile rip-up resets only the pass-local counter. `passOfBestScore` keeps counting.

5. **The timeout counter and the log do not name the same event.** Comparing manifest `TIMED_OUT` counts across RC9 and RC10 mixes a state race with the budget change. The autorouter log line is the reliable clock signal.

### Decisions on the follow-ups

Keep the 45-minute Tier D budget. CoreOne, Horticulture, and m2 finished because that budget was long enough. Do not put the snapshot, bin, or corridor passes back.

**Implemented.** Record one timeout state. `assignTerminalState()` sets `TIMED_OUT` when the pipeline finishes because `timeoutAt` has passed and a stop was requested. `markTimedOutIfStillActive()` is the monitor's write, and it leaves `COMPLETED`, `CANCELLED`, and any other terminal state alone. The nightly table already counts manifest `final_state=TIMED_OUT`. The other follow-ups below are discarded.

**Discarded.** Do not reserve the end of the job. Switching to last-mile or the optimizer because the remaining time is short changes the route when a timeout is set, and it does nothing when `router.job_timeout` is unset. The same board should follow the same pass sequence either way.

**Discarded.** Do not restore as soon as a pass scores worse than the stored best by `STAGNATION_SCORE_THRESHOLD` (0.5). That rule drops solution tracks. A worse pass is the ripped board. The next pass routes into the space it opened, and on this run that next pass is where several new bests appeared:

| Board | Worse pass | Next best, on the ripped board |
|---|---|---|
| `KiCad-Library_Teensy_test_layout` | Pass 2, 116 unrouted, after a best of 88 | Pass 3, 69, then 52, then 45 |
| `TCKB_Kicad_TCKB` | Passes 3–4, 84 then 96, after a best of 71 | Pass 5, 66 |
| `mechkeys_LFK78` | Passes 4–5, 124 then 120, after a best of 117 | Pass 6, 109 |
| `dorkyboard_keyboard` | Pass 4, 19, after a best of 11 | Pass 5, 9. A later dip then reached 6 on pass 10 |
| `CoreOne-xCORE200-Original_CoreOne` | Pass 14, 3, after a best of 1 | Pass 16, 0. This is one of the three new full routes |

DSP, LeeChee, and opendous never beat their early best after a rip, so the same rule would have saved time there. It is not safe in general. The current restore already waits until pass 8 and then only on every 4th pass (`STOP_AT_PASS_MINIMUM`, `STOP_AT_PASS_MODULO`). Leave that schedule alone until a change can be shown to keep these recoveries.

**Discarded.** Do not suppress the global 10-pass stop while the incomplete count is at or under `LAST_MILE_INCOMPLETE_LIMIT`. That constant already exists. It is 8, next to `LAST_MILE_STAGNATION_PASSES` (3) and `LAST_MILE_MAX_ATTEMPTS` (2), and it only gates the last-mile rip-up. A fixed incomplete count is a poor general gate: dorkyboard's last 6 and opendous's last 224 are the same kind of stuck search at different scales. Do not add another use of the constant. Removing the existing gate is a separate change and is not part of this timeout fix.

**Discarded.** Do not end a pass early so that pass 2 can start. The runner already stops between items when a stop has been requested, and copper inserted earlier in that pass stays. On Aleste, pass 1 starts at 1620 unrouted and the 45-minute clock cuts it at 880. The traces for those 740 connections are already on the board. The items not yet visited were never attempted. Forcing that cut sooner, with no clock, would change which nets pass 1 tries. Pass 2 is not a continuation of the same walk. On DSP, pass 2 ripped the pass-1 board from 42 unrouted to 303. Starting that pass instead of finishing the first walk can destroy the partial route rather than complete it.

## Aleste pass 1, JFR

Recorded on the 2.5.0-RC10 jar with `-XX:StartFlightRecording=settings=profile` and `--router.job_timeout=00:06:00`, fanout on, one autorouter pass. Fanout finished in 19 s (68/88 pins). Pass 1 was cut at 341 s: 1054 items, 1620 unrouted down to 886, still 48 pre-existing violations. The nightly 45-minute pass only reached 880, so almost all of that extra half hour is the last few connections.

Of the 264 s inside `autorouteItem`, 263 s is the maze. Intermediate statistics were 65 ms, board statistics 1.2 s, incomplete scan 0.7 s. Peak heap was 598 MB. The job had allocated 437 GB, which is cumulative allocation, not retained memory.

32 437 execution samples. `LinkedList.linkLast` is 45% of them and 72% of allocation pressure. The stacks are one call:

`BoardOutline.isTraceObstacle` → `getEdgePinNets` → `BasicBoard.getPins` → `BoardItemRepository.getItems` → `new LinkedList` and one `add` per board item.

`getEdgePinNets` already caches the set of nets that touch the outline, and `insertItem` / `removeItem` / `MoveComponent` already call `invalidateEdgePinNets` when a pin or the outline changes. The cache probe still calls `getPins().size()` on every `isTraceObstacle`. `getPins` copies every item into a linked list, then copies the pins into a second list, and throws both away. `isTraceObstacle` runs from `ShapeSearchTree45Degree.completeShapeUnlocked`, which is every maze room. The item list grows as the pass inserts traces, so each later room pays more for the same check. That is why the tail of the pass is so much slower than the first 734 connections.

The next cost in the same samples is the room geometry itself: `completeShapeUnlocked` 6%, `Item.containsNet` 6%, `MinAreaTree.overlapsUnlocked` 4%.

`getEdgePinNets()` now returns the cached set when it is non-null. `invalidateEdgePinNets()` still clears it. Pin insert, pin remove, pin net edits, component moves, and outline transforms already call that. The pin-count probe is gone. It was not a safety check: a net change on an existing pin does not change the pin count, and those edits already invalidate the cache.

### Same 6-minute Aleste pass after the cache fix

Same command, new jar. Fanout escape stayed 68/88. Violations stayed 48, all pre-existing. The job clock is still 6 minutes, so a faster fanout gives the autorouter a longer pass. That is why the pass wall and the maze total went up.

| | RC10 jar | Cached edge-pin nets |
|---|---:|---:|
| Fanout | 18.98 s, 20.44 GB allocated | 5.78 s, 2.20 GB allocated |
| Pass 1 wall | 341.02 s | 354.20 s |
| Items attempted | 1054 | 1083 |
| Unrouted, from 1620 | 886 | 881 |
| Connections routed | 734 | 739 |
| Maze time | 262.97 s (249 ms/item) | 294.57 s (272 ms/item) |
| Score | 307.33 | 309.35 |
| Job allocation | 437.46 GB | 278.53 GB |
| Peak heap | 598 MB | 590 MB |
| `LinkedList.linkLast` execution samples | 45.1% | off the top 25; 2.1% of allocation |

Fanout is the clean win: same escapes, about 3.3× less time, about 9× less allocation. The autorouter routed 5 more connections because those 13 seconds moved into the slow tail, where each extra item costs more than the prefix average. ms/item rose for that reason. The list copy is no longer the pass.

32 140 samples on the new recording. `completeShapeUnlocked` is 14.8%, almost all of it `expandToRoomDoors` → `completeNeighbourRooms` during `findConnection`. `DecimalDigits.uncheckedGetCharsLatin1` is 11.3%. Those stacks are string concatenation inside `MazeSearchEngine.describeRoom`, `describeExpandable`, and `describeExpandableBounds`, called from `FRLogger.trace(...)`. `trace(String)` drops the message when TRACE is off, but the call sites build the string first. Eight `FRLogger.trace` sites in `MazeSearchEngine` do this on the room-expansion path. Guarding them with `isTraceEnabled()` keeps the same text when TRACE is on and skips the concat otherwise.

`MinAreaTree.overlapsUnlocked` is 8.3%, on clearance queries from via/pad checks and from neighbour-room calculation. `IntOctagon.normalize`, `union`, `intersection`, and `Line` construction are now the leading real allocators. That is the room geometry itself. `getPin` at 3.9% is `DsnWriter` while saving the session, after the pass.

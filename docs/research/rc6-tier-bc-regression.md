# RC5 to RC6 Tier B/C Routing Regression

Record of the October 2026 investigation into the routing-quality drop between Freerouting 2.6.0-RC5 and 2.6.0-RC6. No code change from this investigation was kept. The uncommitted experiments were discarded.

## Versions

| Build | Git SHA | Role |
|---|---|---|
| 2.6.0-RC5 | `0d658431e` | Baseline. One documentation commit ahead of feature parent `bc6b09fdc`. |
| Merge | `525131b11` | Merge of the part-3 branch with `48e4e09b5`. Routing code in this commit is in neither parent. |
| Parent of the rational-junction commit | `19e60aad5` | Last commit before `2eba6f7fb`. Includes wire-keepout support. |
| 2.6.0-RC6 | `2eba6f7fb` | "Fix rational junction connectivity during routing and SES round trips" (issue 1011 / PR 1010). |

Corpus invocation, matching PCBench headless runs: `java -Xmx4g -XX:+ExitOnOutOfMemoryError`, `--gui.enabled=false`, `--api_server.enabled=false`, `--mcp_server.enabled=false`, `--router.max_threads=1`, `--router.optimizer.max_threads=1`, fanout enabled, `-dct 0`, `--router.result_json`. Metrics are `connections.incomplete_count`, `clearance_violations.total_count`, and `normalized_score`. Runner: `logs/bisect-rc6/run_boards.py`. It does not write `benchmarks.json`. Raw logs, jars, and result JSON are under `logs/bisect-rc6/` (gitignored).

Commits on RC6 that are not in RC5, oldest first: `525131b11` (merge), `9b25743b3` (native-image metadata), `8180ca432` (MCP `structuredContent`), `929258d21` (logger level), `1aac9a34d` (seeded placement order; the corpus sets no item-order seed), `19e60aad5` (wire keepout), `2eba6f7fb` (rational junctions).

## What regressed

Paired Tier B and C boards, RC5 against RC6: 668 boards, 42 finished with more unrouted connections, 22 finished with fewer. Clearance barely moved. The loss is opens, not violations, except for one new violation on Spikeling.

| Tier | RC5 | RC6 |
|---|---|---|
| A | 452/452 clean | 452/452 clean |
| B | 454/527 clean (86.1%), score 984.3, 1790 s | 453/527 clean (86.0%), score 983.9, 1313 s. Fully routed stayed 454/527. |
| C | 100/141 clean (70.9%), 101 fully routed, 1 timeout, score 968.9 | 97/141 clean (68.8%), 98 fully routed, 2 timeouts, score 969.7 |

Large misses: wifi-iot-core-hw +6 unrouted, freeDSPx-AMPx4 +7, iso50 +3, kitspace 120-channel +3. Spikeling_V2.2 gained one clearance violation and stayed fully routed. mechkeys_LFK78 is the extra timeout.

## Commit attribution

All 42 regressed boards were routed on the merge jar `525131b11`, which contains everything in RC6 except `2eba6f7fb`. Wall clock about 3.24 h at 8 workers.

| Outcome | Boards |
|---|---|
| Matched RC5 exactly | 36. Their net RC6 loss comes from `2eba6f7fb`. |
| Matched RC6 | zx-sizif-xxs. The merge alone accounts for it. |
| Split | gsm-iot-core (+1 at the merge, then +1 in `2eba6f7fb`). freeDSPx-AMPx4 (+1 at the merge, then +6 in `2eba6f7fb`). |
| Other | multisensor_cr123 went from 6 to 8 unrouted at the merge, then to 7 on RC6. medusa went from 1 to 0 at the merge, then to 2 on RC6. |
| No score | mechkeys_LFK78, exit 3. |

`2eba6f7fb` must stay. It fixes rational-junction connectivity and SES round trips for issue 1011. The five merge-sensitive boards and mechkeys are a separate problem from the defects below.

## Two defects inside `2eba6f7fb`

### 1. The inserter rejects a route after the previous path has been ripped

`FoundConnectionInserter.insertSegments` walks contacts starting from a pin. `Pin.getNormalContacts()` lists overlapping pins, not traces. `DrillItem.getNormalContacts()` includes a trace whose endpoint or centreline contains the drill center. A trace that ends inside the pad but not on the drill center is invisible to a pin-seeded walk. Returning null marks the attempt failed, skips pull-tight (`optChangedArea` runs only after `AutorouteAttemptState.ROUTED`), and leaves the rip in place.

On full RC6 code, dropping only that `return null` restores RUMBAFLOCK (3 unrouted / 692.31 to 2 / 794.87) and leaves bullion and Spikeling on their RC6 scores. Calling `connectsTerminals` and ignoring the result does not, by itself, restore bullion or Spikeling. Attachment failures (`insertVia`, `insertTrace`, `connectToTrace`, a null terminal capture, a discontinuous plan) still have to return null.

An earlier three-board result that restored all of RUMBAFLOCK, bullion, and Spikeling came from a mixed jar (parent geometry, traces, board, and SES classes, plus a no-reject inserter), saved as `logs/bisect-rc6/jars/experiment-no-reject.jar`. That result is not a full-RC6 measurement. Class overlays that swap new traces or board classes onto old geometry crash with `NoSuchMethodError` (`splitLine`, `exactCoordinates`, `toIntegerExact`, `hasInteriorContacts`). Those exits are link failures, not routing scores. A valid comparison needs geometry and traces together before board classes are added.

### 2. Every interior contact blocks tail removal

`hasInteriorContacts()` makes `BasicBoard.getTraceTail`, `RoutingBoard.removeTraceTails`, `Item.getConnectionItems`, and `ExitStubs.release` refuse to delete any trace with an interior contact. That preserves a real unsplit T-junction, and it also preserves a short nub whose interior contact is within one clearance of a dangling end.

On Spikeling the nub is a 1500-unit stub on the same x as a longer trace. Clearance on that board is 2000 units (0.05 mm). Keeping the nub changes the route. Pass 2 then finishes with one clearance violation of about 21.74 µm (expected 2000 board units, actual about 1782) between two ordinary traces that themselves have `interior=false` and `tail=false`:

- `(1710987,-719889)` to `(1724500,-753600)`, layer 1
- `(1753933,-756000)` to `(1772000,-633000)`, layer 1

Class overlays on top of a no-reject inserter: geometry alone, geometry plus traces, and geometry plus SES stay clean on Spikeling. Geometry plus traces plus `BasicBoard`, and geometry plus traces plus `Item`, reproduce the violation. `RoutingBoard` alone and `DrillItem` alone do not.

The experimental predicate `Trace.interiorJunctionBlocksRemoval()` kept the block for a non-tail, and for a tail whose dangling end is farther than `max(clearance, half-width)` from the nearest interior contact. A tail inside that limit was removed as an ordinary nub. `hasInteriorContacts()` itself was left unchanged, so the rational-junction unit tests still see an interior contact. With the non-tail case returning true, `cycleCleanupReportsNoChangeWhenInteriorJunctionCannotBeRemovedWhole` passes. A first version that returned false for a connected non-tail let `removeIfCycle` delete the trunk.

## Probe scores

RC5 targets: RUMBAFLOCK 2 / 0 violations / 794.87, bullion 20 / 0 / 420.29, Spikeling 0 / 0 / 1000. RC6: RUMBAFLOCK 3 / 692.31, bullion 22 / 362.32, Spikeling 1 violation / 999.93.

| Change, on full RC6 code | RUMBAFLOCK | Spikeling | bullion |
|---|---|---|---|
| No postcondition rejection | 2 / 794.87 | 1 violation / 999.93 | 22 / 362.32 |
| Nub predicate only, postcondition still rejects | 3 / 692.31 | 0 / 1000 | not rerun on this jar |
| Both | 2 / 794.87 | 0 / 1000 | 20 / 420.29 |

The nub-only row was measured on the parent of `2eba6f7fb` plus the nub-rule sources for the first two boards. bullion was not part of that jar's run. The combined row is the full-tree measurement.

Trace-seed, pad-seat, pending-pad-accept, and pull-tight-before-recheck experiments left RUMBAFLOCK at 3 / 692.31 and Spikeling at 1 violation on builds that still returned null for the decisive attempts. `postcondition_pending_accept` did fire for pin-only terminals. Later rejects that include a `PolylineTrace` in the terminal set still returned null, and the score stayed at RC6.

## 36-board run of the combined experiment

The 36 boards whose whole RC6 loss disappeared on the merge jar were routed with both experiments (no postcondition rejection, plus the nub predicate). About 71 minutes, 8 workers. Excluded: zx-sizif-xxs, gsm-iot-core, freeDSPx-AMPx4, multisensor_cr123, medusa, mechkeys_LFK78.

| Outcome | Count | Boards |
|---|---|---|
| Back to RC5 | 10 | BLDC, both Roomba boards, HY-AI7688H (6 back to 4), KiwiSDR, bullion, iso50 (5 back to 2), Spikeling, RUMBAFLOCK, f-91w |
| Better than RC5 | 4 | cjmcu2 14 (RC5 16), USBI2C01 1 (2), Unifying 5 (6), tinyFISH 11 (12) |
| Between | 1 | wifi-iot-core-hw 17 (RC5 14, RC6 20) |
| Still exactly RC6 | 16 | one extra open each, including the 120-channel board (0 to 3), TS100C (7 to 9), Teensy, USB-C, esp32-iot-uno, livolo, minimal_node, PoEPi, wiflier, esp_hat, fst-01, keytee, ardfpga, MPPT, uSKY, wavegen |
| Worse than RC6 | 4 | EtherCAT 6 (RC6 was 5), cr2032 3 (2), samd51 4 (2), serial_gw 2 (1) |
| No score | 1 | dorkyboard_keyboard, exit 3 after 30 minutes. The log stops mid-route with no exception. |

Against RC6 the combined experiment helps 15 boards, leaves 16 unchanged, and adds opens on 4. It is not a general restoration.

## Which half does what

Same four boards, each half built as its own jar. Inserter-only keeps the postcondition change and restores `hasInteriorContacts()` at the tail gates. Nub-only keeps the predicate and restores the postcondition rejection.

| Board | RC5 | RC6 | Inserter only | Nub rule only |
|---|---|---|---|---|
| RUMBAFLOCK | 2 / 794.87 | 3 / 692.31 | 2 / 794.87 | 3 / 692.31 |
| Spikeling | 0 viol / 1000 | 1 viol / 999.93 | 1 viol / 999.93 | 0 viol / 1000 |
| USB-C | 0 / 1000 | 1 / 960.78 | 1 / 960.78 | 1 / 960.78 |
| serial_gw | 0 / 1000 | 1 | 2 / 979.95 | 1 / 989.97 |

The postcondition change fixes RUMBAFLOCK and is what pushes serial_gw from one open to two. The nub rule fixes Spikeling and leaves serial_gw on the RC6 score. Neither fixes USB-C.

## USB-C is the delayed stub cleanup

USB-C on `19e60aad5` is fully routed (0 / 1000). Adding the new geometry, traces, and board classes from `2eba6f7fb`, with the parent inserter, stays fully routed. Adding only the new `FoundConnectionInserter` reproduces RC6: 1 unrouted, score 960.78. Autoroute then skips the optimizer because the board is not fully routed. The parent build finishes at 0 unrouted and does run the optimizer.

Hunks of that inserter, still on the parent commit:

| Variant | USB-C |
|---|---|
| New inserter, unchanged | 1 / 960.78 |
| `connectToTrace` failure ignored | 1 / 960.78 |
| Postcondition rejection removed | 1 / 960.78 |
| Capture abort removed as well | 1 / 960.78 |
| Old `connectToTrace` half-width layer indices | 1 / 960.78 |
| Stub removal skipped entirely | 2 / 921.57 |
| Same stub removal moved back to the end of `insertTrace`, before `connectToTrace` | 0 / 1000 |

The commit moved stub removal out of `insertTrace` to after every segment and after `connectToTrace`, with the comment that a new bridge can look like a tail until its endpoint attachments are complete. That delay is the USB-C open. The same early cleanup, on the full RC6 tree plus both experiments above, restored all four probes to RC5: RUMBAFLOCK 2 / 794.87, Spikeling 0 / 1000, USB-C 0 / 1000, serial_gw 0 / 1000.

It also fails the insertion tests that protect issue 1011. `keepsBridgeUntilRationalEndpointAttachment` inserts a bridge that meets the target off the polyline (`(15000, 5001)` against the line `(0, 0)`–`(30000, 10000)`). Early stub removal deletes that bridge before `connectToTrace` attaches it. Six tests in `FoundConnectionInsertionTest` failed, including `keepsBridgeUntilRationalEndpointAttachment` and `acceptsReplacementOfMutableTargetTrace`. `RationalInteriorTraceContactTest` still passed.

Removing every tail except the trace whose endpoints both lie on the segment just inserted passes those tests, and the board win disappears: USB-C returns to 1 / 960.78 and serial_gw returns to 2 / 979.95. The trace that has to be removed early to fix the boards is the trace the junction test has to keep.

## Conclusions

1. The Tier B/C loss from RC5 to RC6 is mostly `2eba6f7fb`, not the merge. zx-sizif, and parts of gsm-iot, freeDSPx, cr123, and medusa, are merge-sensitive and were not explained here. mechkeys has no score on the merge jar.
2. Three mechanisms inside that commit showed up on real boards:
   - The pin-seeded contact walk misses a trace that ends in a pad, and returning null discards the route after the rip. This is RUMBAFLOCK. Accepting the route anyway adds an open on serial_gw.
   - `hasInteriorContacts()` blocks deletion of a short nub as well as a real T-junction. This is Spikeling's 21.74 µm violation, and bullion's two extra opens when combined with the inserter change.
   - Stub removal after `connectToTrace` leaves a tail that the old per-segment cleanup would have removed. This is USB-C, and it is also what takes serial_gw back to 0 when the cleanup is moved earlier. That earlier cleanup deletes the rational bridge issue 1011 has to keep.
3. The combined experiment (accept the missed insertion, and treat a near-junction tail as a nub) is not safe to ship. On the 36 boards it restores 10, improves 4 past RC5, partially helps wifi-iot-core-hw, leaves 16 at RC6, and makes EtherCAT and three Hardware_Playground boards worse than RC6.
4. A predicate that only asks whether both endpoints of a tail lie on the new segment cannot separate the harmful tail from the rational bridge.
5. No change from this investigation is in the tree. `2eba6f7fb` is unchanged.

## Unit tests touched by the discarded experiments

`FoundConnectionInsertionTest` was updated so two cases expect a non-null insertion while the orphan stays disconnected: `leavesOrphanUnconnectedWhenTerminalComponentFractures` (was `rejectsFragmentedTerminalComponentIncludingFixedTerminal`) and `leavesMissedTraceUnconnectedWhenAttachmentReportsSuccess` (was `rejectsDisconnectedInsertionEvenWhenAttachmentReportsSuccess`). `rejectsFailedTargetAttachment`, `rejectsFailedStartAttachment`, and `rejectsEmptyAndDisjointPlansBeforeMutatingBoard` still expect null. `RationalInteriorTraceContactTest` still expects `hasInteriorContacts` true, `getConnectionItems` empty, `getTraceTail` null, and the unsplit trunk to survive cleanup when the junction is farther than clearance from a dangling end. Those edits were discarded with the production changes.

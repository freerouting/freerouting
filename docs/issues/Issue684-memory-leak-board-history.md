# Issue #684 – Memory Leak: BoardHistory Grows Unboundedly

## Problem

Freerouting crashes with an `OutOfMemoryError` after 4–6 days of continuous routing.  
Two distinct root causes were identified from the log and code review:

### Root Cause 1 – `BoardHistory` has no size cap (primary OOM cause)

`BatchAutorouter.runBatchLoop()` calls `bh.add(this.board)` on **every routing pass**.  
`BoardHistory.add()` serialises the entire routing board to a `byte[]`
(Java object serialisation, no compression) and stores it indefinitely.

For a complex board the serialised snapshot can be several megabytes.
Over thousands of passes across 4–6 days, the `boards` list grows to gigabytes,
exhausting the heap (`-Xmx2G` in the reporter's configuration).

`bh.clear()` is called at the very end of `runBatchLoop()`, but only after the session
finishes — it provides no relief during a multi-day run.

### Root Cause 2 – `ItemIdentificationNumberGenerator` logs on every creation after overflow

Once `last_generated_id_no` reaches `Integer.MAX_VALUE / 2` (≈ 1 billion), the original
code emitted a WARN on **every single item creation**.  At the routing rate observed in the
log (~20 items/s), this floods the log at ≈ 50 ms intervals and makes diagnostics
impossible.  After further increments, the Java `int` would overflow to `Integer.MIN_VALUE`,
assigning **negative IDs** to new items.

## Fix

### Fix 1 – `BoardHistory.MAX_HISTORY_SIZE = 30`

A static cap of 30 entries was added to `BoardHistory`.  When the cap is reached,
`add()` evicts the **lowest-scoring** entry before inserting the new one, so the
history always retains the 30 best-seen board states.  30 snapshots are more than
sufficient for the routing algorithm's board-restore logic, while bounding peak heap
usage to at most `30 × [serialised board size]`.

`BOARD_RANK_LIMIT` in `BatchAutorouter` was updated from the hard-coded `50` to
`BoardHistory.MAX_HISTORY_SIZE` so the "stop when restored board rank > limit" check
remains reachable.

### Fix 2 – `ItemIdentificationNumberGenerator` wraps safely and warns once per wrap

Instead of warning on every item creation:
- The counter wraps back to 1 when it reaches `c_max_id_no`.
- A single WARN is emitted per wrap, including a wrap-count for diagnostics.
- Negative IDs are avoided.

## Acceptance Criteria

- ✅ `BoardHistory.size()` never exceeds `MAX_HISTORY_SIZE = 30` during routing.
- ✅ The routing loop still terminates and produces correct results on all existing fixture boards.
- ✅ `ItemIdentificationNumberGenerator` no longer emits repeated WARN lines; a single wrap
  message appears at most once per billion item insertions.
- ✅ All existing unit and fixture tests pass.

## Related Files

| File | Change |
|---|---|
| `src/main/java/app/freerouting/autoroute/BoardHistory.java` | Added `MAX_HISTORY_SIZE = 30`; evict worst entry on cap |
| `src/main/java/app/freerouting/autoroute/BatchAutorouter.java` | `BOARD_RANK_LIMIT` now references `BoardHistory.MAX_HISTORY_SIZE` |
| `src/main/java/app/freerouting/board/ItemIdentificationNumberGenerator.java` | Wrap-around with single warning per wrap |
| `src/test/java/app/freerouting/autoroute/BoardHistoryTest.java` | New `sizeCapEvictsWorstEntry` test |
| `src/test/java/app/freerouting/fixtures/Issue684MemoryLeakRoutingTest.java` | Routing smoke-test for the reported board |
| `fixtures/Issue684-Autorouter_PCB1_2026-5-8.dsn` | Fixture (added by @andrasfuchs) |

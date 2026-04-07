# Routing Parity Investigation: Current vs v1.9

## Problem Statement

The current freerouting implementation rips up more items than v1.9 when routing the same PCB design (`tests/Issue508-DAC2020_bm01.dsn`). In a 41-item run, the current version rips **8 items** while v1.9 rips **7 items** (the first 7 are identical; the 8th exists only in current).

### Symptom
- **Routing net=67, item=280**: current rips item **2653** (a net=68 trace); v1.9 does not rip anything extra.
- Both versions encounter the same geometric obstacle (net=68's trace) with **identical ripup costs** (`result=90510`, `detour=1.10483...`).
- The difference is purely in the **item ID** assigned to that obstacle trace: current assigns ID **2653**, v1.9 assigns ID **2550**.

---

## Root Cause

### Item ID as a Tie-Breaker

`MazeListElement.compareTo()` sorts expansion candidates by:
1. `sorting_value` (primary)
2. `expansion_value` (secondary)
3. `door.get_id_no()` (tertiary — **the tie-breaker**)
4. `section_no_of_door` (quaternary)

When sorting values are equal, the door ID decides which path is expanded first.

### How Door IDs Derive from Item IDs

```
net=68 trace → item.get_id_no() = 2653 (current) or 2550 (v19)
  → ObstacleExpansionRoom.get_id_no() = (item.get_id_no() << 10) | index_in_item
  → ExpansionDoor.get_id_no() = Math.min(id1,id2)*31 + Math.max(id1,id2)
  → MazeListElement.compareTo() tie-break
```

Different item IDs → different door IDs → different expansion order → current picks the path that rips the net=68 trace; v1.9 picks a different path that avoids it.

### Why Item IDs Differ

Item IDs are assigned sequentially by `ItemIdentificationNumberGenerator.new_no()` (increments `++last_generated_id_no`). By the time net=68's trace is created, the current version has generated approximately **103 more items** than v1.9, causing the counter to be ~103 higher.

**Root hypothesis:** The current version's `opt_changed_area()` (post-routing optimization) or trace insertion logic creates additional items (e.g., extra trace splits) that v1.9 does not, inflating the counter.

---

## Investigation Status

### Confirmed
- Ripup costs are **identical** between versions — not the cause.
- The 7 shared ripped items are **identical** (same IDs, same nets, same geometry).
- The 8th rip is caused by a **tie-break difference** due to item ID divergence.
- The tie-break mechanism is in `MazeListElement.compareTo()` using `door.get_id_no()`.

### Still Unknown
- **Exactly which routing step** causes the item ID counter to first diverge between current and v1.9.
- **What operation** creates the extra items in current (likely `opt_changed_area()` splitting traces differently, or extra items from different trace insertion paths).

### Next Investigative Step
Run 41-item comparison with `maxItemId` logging and find the first routing step where the counters diverge:

```powershell
# After running: .\scripts\tests\compare-versions.ps1 -InputFile tests\Issue508-DAC2020_bm01.dsn -MaxItems 41
$cur = Select-String "compare_trace_route_item" logs\freerouting-current*.log | ForEach-Object { if ($_ -match "net=(\d+).*maxItemId=(\d+)") { "net=$($Matches[1]) maxId=$($Matches[2])" } }
$v19 = Select-String "compare_trace_route_item" logs\freerouting-v190*.log  | ForEach-Object { if ($_ -match "net=(\d+).*maxItemId=(\d+)") { "net=$($Matches[1]) maxId=$($Matches[2])" } }
for ($i = 0; $i -lt $cur.Count; $i++) {
    if ($cur[$i] -ne $v19[$i]) { Write-Host "DIVERGE at index $i: CUR=$($cur[$i])  V19=$($v19[$i])"; break }
}
```

Once the first-divergence step is identified, compare `opt_changed_area()` behavior at that step between current and v1.9 by examining TRACE logs around that routing item.

---

## Code Changes Made (Diagnostic Instrumentation)

All changes below are **diagnostic/logging only** — no routing behavior was altered.

### 1. `MazeListElement.java` (both current and v19)
Added `int ripup_cost = 0;` field to carry the direct ripup cost through the maze list element so it can be logged at backtrack time.

```java
final boolean already_checked;
/** The ripup cost paid to enter the next_room through this door. */
int ripup_cost = 0;
```

### 2. `MazeSearchElement.java` (both current and v19)
Added `public int ripup_cost = 0;` field and reset it in `reset()`.

### 3. `MazeSearchAlgo.java` (both current and v19)
Three changes:
- In `expand_to_door_section()`: sets `new_element.ripup_cost = (int) p_add_costs` when a ripup cost is paid.
- For delayed-ripup element: sets `new_element.ripup_cost = (int) ripup_costs`.
- In main loop: propagates `curr_door_section.ripup_cost = list_element.ripup_cost`.

### 4. `LocateFoundConnectionAlgo.java` (both current and v19)
Added `, ripup_cost=NNN` to `BACKTRACK_STEP` trace log so the actual ripup cost per step is visible.

### 5. `BatchAutorouter.java` — Current version
- Added `int maxItemId = board.communication.id_no_generator.max_generated_no();` to track the item ID counter after each routing step.
- Added `maxItemId=NNN` to `compare_trace_route_item` log line.

### 6. `BatchAutorouter.java` — v19 version
Three changes:
- Added the entire `compare_trace_ripped_item` logging block (was **completely missing** from v19).
- Updated `compare_trace_route_item` format to include `details=n/a, ripped=N, netItems=0->0` fields (to match current format).
- Added `maxItemId=NNN` tracking using `routing_board.communication.id_no_generator.max_generated_no()`.

---

## Log Format Reference

### `compare_trace_route_item` (both versions, after changes)
```
compare_trace_route_item | Routing <ItemType> -> result=<state>, details=<details>, incompletes=N, netIncomplete=N, ripped=N, netItems=N->N, maxItemId=N
```

### `compare_trace_ripped_item` (both versions, after changes)
```
compare_trace_ripped_item | source_item=<id>, source_net=<net>, ripped_id=<id>, ripped_type=<type>, ripped_net_count=N, ripped_nets=<net1|net2|...>
```

### `BACKTRACK_STEP` (both versions, after changes)
```
BACKTRACK_STEP net=N, step=N, door_type=<type>, section=N, room_ripped=<bool>, ripup_cost=N, next_room_type=<type>, obstacle_id=N
```

---

## Key Files

| File | Role |
|---|---|
| `src/main/java/app/freerouting/autoroute/MazeListElement.java` | Maze expansion element; `compareTo()` tie-break uses `door.get_id_no()` |
| `src/main/java/app/freerouting/autoroute/MazeSearchElement.java` | Per-door search state; new `ripup_cost` field |
| `src/main/java/app/freerouting/autoroute/MazeSearchAlgo.java` | Core maze expansion; `expand_to_door_section()` |
| `src/main/java/app/freerouting/autoroute/LocateFoundConnectionAlgo.java` | Backtrack/solution path; `BACKTRACK_STEP` logging |
| `src/main/java/app/freerouting/autoroute/BatchAutorouter.java` | Outer routing loop; `compare_trace_route_item` / `compare_trace_ripped_item` |
| `src/main/java/app/freerouting/autoroute/ExpansionDoor.java` | `get_id_no()` = `Math.min(id1,id2)*31 + Math.max(id1,id2)` |
| `src/main/java/app/freerouting/autoroute/ObstacleExpansionRoom.java` | `get_id_no()` = `(item.get_id_no() << 10) \| index_in_item` |
| `src/main/java/app/freerouting/board/ItemIdentificationNumberGenerator.java` | Sequential counter `++last_generated_id_no`; source of ID divergence |
| Mirror files under `src_v19/` | v1.9 reference; same changes applied for log parity |

---

## Pending Fix

Once the exact step causing ID divergence is identified, the fix should target the operation in current that creates extra items compared to v1.9. Candidate areas:
- `opt_changed_area()` in `MazeSearchAlgo` or `BatchAutorouter`
- Trace splitting in `BasicBoard` or `RoutingBoard`
- Any post-routing cleanup that merges/splits traces differently than v1.9

The fix should be the **smallest possible change** that eliminates the extra item creation, verified by:
1. No new clearance violations
2. No regression in routing completion rate
3. `maxItemId` values matching between current and v1.9 at every routing step
4. Zero extra ripped items in the 41-item run


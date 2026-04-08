# Routing Parity Investigation: Current vs v1.9

## Status: **RESOLVED** (item ID parity achieved for 41-item run)

---

## Problem Statement

The current freerouting implementation was ripping more items than v1.9 when routing the same PCB design (`tests/Issue508-DAC2020_bm01.dsn`). In a 41-item run, the current version was ripping **8 items** while v1.9 ripped **7 items** (the first 7 were identical; the 8th existed only in current).

### Original Symptom
- **Routing net=67, item=280**: current ripped item **2653** (a net=68 trace); v1.9 did not.
- Both versions encountered the same geometric obstacle (net=68's trace) with **identical ripup costs** (`result=90510`, `detour=1.10483...`).
- The difference was in the **item ID** assigned to that obstacle trace: current assigned ID **2653**, v1.9 assigned ID **2550**.

---

## Root Cause (Identified & Fixed)

### 1. Item ID as a Tie-Breaker

`MazeListElement.compareTo()` sorts expansion candidates by:
1. `sorting_value` (primary)
2. `expansion_value` (secondary)
3. `door.get_id_no()` (tertiary — **the tie-breaker**)
4. `section_no_of_door` (quaternary)

### 2. How Door IDs Derive from Item IDs

```
net=68 trace → item.get_id_no() = 2653 (old current) or 2550 (v19)
  → ObstacleExpansionRoom.get_id_no() = (item.get_id_no() << 10) | index_in_item
  → ExpansionDoor.get_id_no() = Math.min(id1,id2)*31 + Math.max(id1,id2)
  → MazeListElement.compareTo() tie-break
```

Different item IDs → different door IDs → different expansion order → current picked the path that ripped the net=68 trace; v1.9 picked a different path that avoided it.

### 3. Why Item IDs Were Diverging

Item IDs are assigned sequentially by `ItemIdentificationNumberGenerator.new_no()` (`++last_generated_id_no`). By the time net=68's trace was created, the old current version had generated approximately **103 more items** than v1.9.

The extra items were created by `opt_changed_area()` post-routing optimization splitting traces differently than v1.9 — specifically, the current version's trace pull-tight algorithm was creating extra intermediate trace segments.

### 4. Fix Applied

The fix was NOT in the ID generator or routing logic directly. Instead, the optimization process was aligned with v1.9 behavior so that both versions generate the same number of trace items after each routing step. The result:

- **`maxItemId` values now match perfectly** at every single routing step across all 41 items.
- Both versions rip exactly **7 items** with identical IDs, types, nets, and costs.

---

## Resolution Verification

Running `scripts/tests/compare-versions.ps1 -InputFile tests/Issue508-DAC2020_bm01.dsn -MaxItems 41`:

| Metric | Current | v1.9 |
|---|---|---|
| Unrouted items | 163 | 163 ✅ |
| Ripped items | 7 | 7 ✅ |
| `maxItemId` at net=68 | 2550 | 2550 ✅ |
| `maxItemId` at net=67 | 2819 | 2819 ✅ |

The `maxItemId` values match **at all 41 steps** (verified programmatically: "ALL maxItemId values MATCH perfectly (41 steps checked)").

---

## Key Question: Do Ripping Costs Cause Divergence?

**Answer: No.** Ripup costs are identical between the two versions for all matching ripped items. The divergence was purely structural (item ID → door ID → expansion ordering), not cost-driven. This was confirmed after adding `ripup_cost=N` to the `compare_trace_ripped_item` log — all 7 ripped items show the same costs in both versions.

---

## Diagnostic Instrumentation Added

All changes below are **diagnostic/logging only** — no routing behavior was altered.

### 1. `MazeListElement.java` (both current and v19)
Added `int ripup_cost = 0;` field to carry the direct ripup cost through the maze list element.

```java
final boolean already_checked;
/** The ripup cost paid to enter the next_room through this door. */
int ripup_cost = 0;
```

### 2. `MazeSearchElement.java` (both current and v19)
Added `public int ripup_cost = 0;` field and reset it in `reset()`.

### 3. `MazeSearchAlgo.java` (both current and v19)
- In `expand_to_door_section()`: sets `new_element.ripup_cost = (int) p_add_costs` when a ripup cost is paid.
- For delayed-ripup element: sets `new_element.ripup_cost = (int) ripup_costs`.
- In main loop: propagates `curr_door_section.ripup_cost = list_element.ripup_cost`.

### 4. `LocateFoundConnectionAlgo.java` (both current and v19)
- Added `BACKTRACK_STEP` trace log with `ripup_cost=NNN` field (debug-gated to specific nets).
- Added `Map<Item, Integer> p_ripup_costs` parameter to `backtrack()`, constructor, and `get_instance()`.
- When adding an item to `p_ripped_item_list`, also records the cost: `p_ripup_costs.put(room.get_item(), curr_maze_search_element.ripup_cost)`.

### 5. `LocateFoundConnectionAlgo45Degree.java` + `LocateFoundConnectionAlgoAnyAngle.java` (both current and v19)
Updated constructors to forward the `p_ripup_costs` map to `super()`.

### 6. `AutorouteEngine.java` (both current and v19)
Added `Map<Item, Integer> p_ripup_costs` parameter to `autoroute_connection()` and forwarded it to `LocateFoundConnectionAlgo.get_instance()`. Callers that don't need cost tracking pass `null`.

### 7. `BatchAutorouter.java` (current version)
- Creates `Map<Item, Integer> ripped_item_costs = new LinkedHashMap<>()` per routing item.
- Passes it through `autoroute_item()` → `autoroute_connection()`.
- Logs `ripup_cost=N` per ripped item in `compare_trace_ripped_item`.
- Tracks `maxItemId = board.communication.id_no_generator.max_generated_no()` and logs it in `compare_trace_route_item`.

### 8. `BatchAutorouter.java` (v19 version)
Same changes as current: cost map, `ripup_cost=` logging, `maxItemId=` tracking.

### 9. `BatchAutorouterThread.java` and other callers (current)
Pass `null` for the cost map (costs not needed outside debug logging path).

---

## Log Format Reference

### `compare_trace_route_item` (both versions)
```
compare_trace_route_item | Routing <ItemType> -> result=<state>, details=<details>, incompletes=N, netIncomplete=N, ripped=N, netItems=N->N, maxItemId=N
```

### `compare_trace_ripped_item` (both versions)
```
compare_trace_ripped_item | source_item=<id>, source_net=<net>, ripped_id=<id>, ripped_type=<type>, ripped_net_count=N, ripped_nets=<net1|net2|...>, ripup_cost=N
```

### `BACKTRACK_STEP` (both versions, debug-gated)
```
BACKTRACK_STEP net=N, step=N, door_type=<type>, section=N, room_ripped=<bool>, ripup_cost=N, next_room_type=<type>, obstacle_id=N
```

---

## Key Files

| File | Role |
|---|---|
| `src/main/java/app/freerouting/autoroute/MazeListElement.java` | `compareTo()` tie-break uses `door.get_id_no()`; `ripup_cost` field added |
| `src/main/java/app/freerouting/autoroute/MazeSearchElement.java` | Per-door search state; `ripup_cost` field |
| `src/main/java/app/freerouting/autoroute/MazeSearchAlgo.java` | Core maze expansion; `expand_to_door_section()` |
| `src/main/java/app/freerouting/autoroute/LocateFoundConnectionAlgo.java` | Backtrack/solution path; `BACKTRACK_STEP` logging; ripup cost map |
| `src/main/java/app/freerouting/autoroute/AutorouteEngine.java` | `autoroute_connection()` forwards cost map |
| `src/main/java/app/freerouting/autoroute/BatchAutorouter.java` | Outer routing loop; `compare_trace_route_item` / `compare_trace_ripped_item` with `ripup_cost=` |
| `src/main/java/app/freerouting/autoroute/ExpansionDoor.java` | `get_id_no()` = `Math.min(id1,id2)*31 + Math.max(id1,id2)` |
| `src/main/java/app/freerouting/autoroute/ObstacleExpansionRoom.java` | `get_id_no()` = `(item.get_id_no() << 10) \| index_in_item` |
| `src/main/java/app/freerouting/board/ItemIdentificationNumberGenerator.java` | Sequential counter `++last_generated_id_no`; was the source of ID divergence |
| Mirror files under `src_v19/` | v1.9 reference; same instrumentation applied for log parity |

---

## Remaining Performance Delta (200-item run)

While parity is achieved for the first 41 items, the 200-item run shows:
- Current: **127 unrouted** items
- v1.9: **97 unrouted** items

This secondary divergence begins after ~100 items and is a **separate issue**. Possible causes:
1. After 41 items, `opt_changed_area()` may diverge again (creating different item IDs for later items)
2. The current version may have other algorithmic differences in later passes
3. Memory/GC differences (current uses ~2x more heap than v1.9)

This requires a separate parity investigation starting at the first divergence point after item 41 in the 200-item run.

---

## Key Lessons Learned

1. **Item IDs are NOT stable identifiers** — they depend on the total order of item creation across the board. Even a single extra trace split in optimization can cascade into different routing decisions many steps later.

2. **Tie-breakers matter** — `door.get_id_no()` is used as a tertiary sort key in `MazeListElement.compareTo()`. When sorting_value and expansion_value are equal, the door ID alone determines which path gets expanded first, which can lead to completely different routing choices.

3. **Ripup costs are NOT the cause** — Once we verified the costs were identical (by adding `ripup_cost=` to logs), the investigation pivoted to structural causes (item ID → door ID).

4. **`opt_changed_area()` is the culprit** — Post-routing trace optimization in the current version creates a different number of trace segments than v1.9, inflating the item ID counter. The fix was to align this behavior.

5. **`maxItemId` is the right diagnostic** — Tracking `board.communication.id_no_generator.max_generated_no()` after each routing step is the most direct way to detect when ID divergence begins.

---

## Comparison Commands

```powershell
# Run 41-item comparison
.\scripts\tests\compare-versions.ps1 -InputFile tests\Issue508-DAC2020_bm01.dsn -MaxItems 41

# Check ripped items with costs
Select-String "compare_trace_ripped_item" logs\freerouting-current.log | ForEach-Object { $_.Line }
Select-String "compare_trace_ripped_item" logs\freerouting-v190.log | ForEach-Object { $_.Line }

# Verify maxItemId matches at every step
$cur = @(Select-String "compare_trace_route_item" logs\freerouting-current.log | Where-Object { $_.Line -match "maxItemId=(\d+)" } | ForEach-Object { $matches[1] })
$v19 = @(Select-String "compare_trace_route_item" logs\freerouting-v190.log | Where-Object { $_.Line -match "maxItemId=(\d+)" } | ForEach-Object { $matches[1] })
for ($i = 0; $i -lt [Math]::Min($cur.Count, $v19.Count); $i++) {
    if ($cur[$i] -ne $v19[$i]) { Write-Host "DIVERGE at step $i`: CUR=$($cur[$i]) V19=$($v19[$i])"; break }
}
```

# Routing Parity Investigation: Current vs v1.9

## Status: **RESOLVED** — 111-item (MaxItems=61) run achieves full parity ✅

---

## Problem Statement

The current freerouting implementation was ripping more items than v1.9 when routing the same PCB design (`tests/Issue508-DAC2020_bm01.dsn`). In a 41-item run, the current version was ripping **8 items** while v1.9 ripped **7 items** (the first 7 were identical; the 8th existed only in current).

### Original Symptom
- **Routing net=67, item=280**: current ripped item **2653** (a net=68 trace); v1.9 did not.
- Both versions encountered the same geometric obstacle (net=68's trace) with **identical ripup costs** (`result=90510`, `detour=1.10483...`).
- The difference was in the **item ID** assigned to that obstacle trace: current assigned ID **2653**, v1.9 assigned ID **2550**.

---

## Root Cause (Identified & Fixed) — 41-item run

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

## Resolution Verification — 41-item run

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

## Net #49 (USBVCC) — Step 51 — Originally Diverging, Now Resolved

### Original Symptom (Before Fix)

After achieving parity on the 41-item run, the 61-item run showed divergence at step 51:

| Metric | Current (old) | v1.9 |
|---|---|---|
| `maxItemId` after Net #49 | **3477** | **3479** (diff: −2) |
| `netIncomplete` for Net #49 | **2** | **1** |
| `incompletes` total | **155** | **154** |

This 2-item offset **persists for all subsequent steps** (every step has Current's `maxItemId` = v1.9's `maxItemId` − 2), affecting routing decisions that use item IDs as tie-breakers.

### Segment Insertion Log (Identical in Both Versions)

Both current and v1.9 produce **exactly the same** segment insertion log for Net #49:

| i | Decision | from→to | delta |
|---|---|---|---|
| 1 | ADVANCE | (1140391,-1011936)→(1139816,-1011936) | 1 (ID 3469) |
| 2 | ADVANCE | (1139816,-1011936)→(1139814,-1011936) | 1 (ID 3470) |
| 3 | ADVANCE | (1139814,-1011936)→(1123350,-1028400) | 1 (ID 3471) |
| 4 | VIOLATION_CORRECTED | from_corner_no=2 | 0 |
| 5 | VIOLATION_CORRECTED | from_corner_no=2 | 0 |
| 6 | ADVANCE | (1139814,-1011936)→(1079119,-1038574) | 5 (IDs 3472–3476) |
| 7 | ADVANCE | (1079119,-1038574)→(1079119,-1042416) | 1 (ID 3477) |

### Stub Removal Log (Different)

| Version | Stub Found | Result |
|---|---|---|
| **Current** | `stub_id=3477, first=(1079119,-1038574), last=(1079119,-1042416), start_contacts=0, end_contacts=1` | `removed_stubs=1` |
| **v1.9** | _(no stub found)_ | `removed_stubs=0` |

### Root Cause

During `insert_forced_trace_polyline` for **segment i=7** (`(1079119,-1038574)` → `(1079119,-1042416)`), `pull_tight` in **current** modifies an adjacent trace (one of IDs 3472–3476 from segment i=6), shifting its endpoint away from `(1079119,-1038574)`. This leaves trace 3477 (the final-leg trace) with **`start_contacts=0`** at `(1079119,-1038574)`.

The stub removal loop in `InsertFoundConnectionAlgo.insert_trace()` then **incorrectly identifies trace 3477 as a stub and removes it**, disconnecting the destination pin at `(1079119,-1042416)`. This causes `netIncomplete=2` for net #49 (instead of the correct `netIncomplete=1`), and the 2-item offset (`maxItemId` being 2 less than v1.9) propagates to all subsequent steps.

In **v1.9**, `pull_tight` preserves the adjacent trace's endpoint at `(1079119,-1038574)`, so trace 3477 has `start_contacts≥1` and `get_trace_tail()` returns null → **no stub removed** (correct behavior).

### Diagnostic Markers in Logs

**Current log** (61-item run):
```
compare_trace_stub_found net=49, corner_idx=6, corner=(1079119,-1038574), stub_id=3477,
    stub_first=(1079119,-1038574), stub_last=(1079119,-1042416),
    start_contacts=0, end_contacts=1
[InsertFoundConnectionAlgo.insert_trace] [compare_trace_stub_cleanup] net=49, layer=0, removed_stubs=1, trace_enabled=true
```

**v1.9 log** (61-item run):
```
[InsertFoundConnectionAlgo.insert_trace] [compare_trace_stub_cleanup] net=49, layer=0, removed_stubs=0, test_level=RELEASE_VERSION
```

### Why v1.9 Also Runs the Stub Check Loop

The v1.9 code has a guard:
```java
if (board.get_test_level().ordinal() < TestLevel.ALL_DEBUGGING_OUTPUT.ordinal()) {
```
Since `test_level=RELEASE_VERSION` (ordinal < `ALL_DEBUGGING_OUTPUT`), the loop DOES run in v1.9. The loop finds **no stub** because `get_trace_tail((1079119,-1038574))` returns null — there IS no trace with `first_corner=(1079119,-1038574)` and `start_contacts=0` in v1.9's board state (contacts are ≥1 due to different pull_tight behavior).

### Fix Applied

In `InsertFoundConnectionAlgo.insert_trace()`, the stub removal loop now **protects the final-leg trace** from removal. A trace is not removed as a stub if its far end (the non-queried endpoint) equals the route's destination corner (`p_trace.corners[p_trace.corners.length - 1]`):

```java
Point destinationCorner = p_trace.corners[p_trace.corners.length - 1];
for (int i = 0; i < p_trace.corners.length - 1; i++) {
    Trace trace_stub = board.get_trace_tail(p_trace.corners[i], p_trace.layer, net_no_arr);
    if (trace_stub != null) {
        // Determine the "far end" of the stub (the non-queried end)
        Point far_end = trace_stub.first_corner().equals(p_trace.corners[i])
            ? trace_stub.last_corner()
            : trace_stub.first_corner();
        // Don't remove if the far end IS the destination: this trace is the final segment.
        // Removing it would disconnect the destination from the route.
        if (far_end.equals(destinationCorner)) {
            FRLogger.trace("compare_trace_stub_destination_protected ...");
            continue;
        }
        board.remove_item(trace_stub);
        removedTraceStubs++;
    }
}
```

**Why this is correct:**
- Trace 3477: `first_corner=(1079119,-1038574)` (queried, `start_contacts=0`), `last_corner=(1079119,-1042416)` = `destinationCorner` → **protected** ✓
- Stubs #476, #497, #527 (nets 78, 77, 76): far end ≠ destination → **still removed** ✓
- Stubs #1098, #1149 (nets 31, 33): far end ≠ destination → **still removed** ✓

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

### 10. `PolylineTrace.java` (both current and v19)
Added detailed debug logging to `combine_at_start()`, `combine_at_end()`, `normalize()`, and `split()` for tracing routing decisions for specific nets.

### 11. `RoutingBoard.java` (current)
Added sub-step logging to `insert_forced_trace_polyline()`: `insert_and_combine`, `normalize`, `split_at_keep`, `before_pull_tight`, `after_pull_tight`.

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

### `compare_trace_stub_cleanup` (both versions)
```
[InsertFoundConnectionAlgo.insert_trace] [compare_trace_stub_cleanup] net=N, layer=N, removed_stubs=N, trace_enabled=<bool>
```
(v1.9 appends `test_level=<level>` instead of `trace_enabled=<bool>`)

### `compare_trace_stub_found` (both versions)
```
compare_trace_stub_found net=N, corner_idx=N, corner=(...), stub_id=N, stub_first=(...), stub_last=(...), start_contacts=N, end_contacts=N
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
| `src/main/java/app/freerouting/autoroute/InsertFoundConnectionAlgo.java` | **Net 49 & 65 fix**: destination-corner guard was added then reverted; stub removal loop now matches v1.9 exactly |
| `src/main/java/app/freerouting/autoroute/ExpansionDoor.java` | `get_id_no()` = `Math.min(id1,id2)*31 + Math.max(id1,id2)` |
| `src/main/java/app/freerouting/autoroute/ObstacleExpansionRoom.java` | `get_id_no()` = `(item.get_id_no() << 10) \| index_in_item` |
| `src/main/java/app/freerouting/board/ItemIdentificationNumberGenerator.java` | Sequential counter `++last_generated_id_no`; was the source of ID divergence |
| `src/main/java/app/freerouting/board/PolylineTrace.java` | Debug logging in combine/normalize/split |
| `src/main/java/app/freerouting/board/RoutingBoard.java` | Sub-step logging in `insert_forced_trace_polyline()` |
| Mirror files under `src_v19/` | v1.9 reference; same instrumentation applied for log parity |

---

## Performance Summary

| Run | Current | v1.9 | Status |
|---|---|---|---|
| 41-item | 163 unrouted | 163 unrouted | ✅ Parity |
| 111-item (MaxItems=61) | **134 unrouted** | **134 unrouted** | ✅ **Parity** |
| 200-item | TBD | ~97 unrouted | 🔄 Next target |

---

## Key Lessons Learned

1. **Item IDs are NOT stable identifiers** — they depend on the total order of item creation across the board. Even a single extra trace split in optimization can cascade into different routing decisions many steps later.

2. **Tie-breakers matter** — `door.get_id_no()` is used as a tertiary sort key in `MazeListElement.compareTo()`. When sorting_value and expansion_value are equal, the door ID alone determines which path gets expanded first, which can lead to completely different routing choices.

3. **Ripup costs are NOT the cause** — Once we verified the costs were identical (by adding `ripup_cost=` to logs), the investigation pivoted to structural causes (item ID → door ID).

4. **`opt_changed_area()` is the culprit (41-item run)** — Post-routing trace optimization in the current version creates a different number of trace segments than v1.9, inflating the item ID counter. The fix was to align this behavior.

5. **`maxItemId` is the right diagnostic** — Tracking `board.communication.id_no_generator.max_generated_no()` after each routing step is the most direct way to detect when ID divergence begins.

6. **Stub removal guards can backfire** — A guard added to protect destination-connecting traces from removal (net=49 fix) later caused regression for net=65 by preventing a legitimate stub removal. When the upstream divergence causing the net=49 stub problem was resolved by other means, the guard became unnecessary and harmful. Always re-evaluate protective guards when upstream behavior changes.

7. **Use `start_contacts=0, end_contacts=0` to distinguish genuine stubs** — A trace with both endpoints having 0 contacts is a truly floating segment and should be removed regardless of what its endpoints are. A trace with `end_contacts=0` but whose endpoint IS the destination terminal is still a valid final-leg trace and should NOT be removed based on the endpoint alone.

---

## Comparison Commands

```powershell
# Run 111-item comparison (default for compare-versions.ps1)
.\scripts\tests\compare-versions.ps1

# Run with specific max items (use -max_items, not -MaxItems)
.\scripts\tests\compare-versions.ps1 -max_items 200

# Find first divergence between segment ID logs
python -u scripts\find_divergence.py logs\freerouting-current.log logs\freerouting-v190.log

# Check stub cleanup behavior
python -u -c "
log_file = 'logs/freerouting-current.log'
with open(log_file, encoding='utf-8', errors='replace') as f:
    for line in f:
        if 'stub_cleanup' in line or 'stub_found' in line:
            print(line.rstrip())
"
```

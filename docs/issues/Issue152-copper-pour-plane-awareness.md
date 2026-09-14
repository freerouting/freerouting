# Issue 152 — Copper Pour / Power Plane Awareness

**GitHub:** https://github.com/freerouting/freerouting/issues/152
**Status:** Partially fixed; core clearance-violation bug open
**Priority:** High

---

## Executive Summary

For multi-layer PCBs it is common to dedicate an entire layer (or a large polygonal fill on a layer) to a single net, typically GND or a power rail. These are called **copper pours**, **power planes**, or **conduction areas** in Freerouting.

The issue reports that Freerouting's autorouter introduces **clearance violations** while trying to route pads to those planes. The main reproducer is `fixtures/Issue093-interf_u.dsn`, a 2-layer KiCad board with a full GND copper pour on the bottom copper layer. Routing this design produces ~62 clearance violations and logs an internal error in `BatchAutorouter.autoroute_pass`.

---

## Sub-issues

| ID | Description | Status |
|----|-------------|--------|
| 152-A | Clearance violations introduced during plane-net routing | 🔍 Clarified (0 new violations; pre-existing 0.1 µm KiCad roundoff) |
| 152-B | False-work items: pads already connected to plane re-queued every pass | ✅ Fixed |
| 152-C | Router infinite loop when all plane-net items false-work | ✅ Fixed |
| 152-D | `BoardStatistics.clearanceViolations.totalCount` uses incomplete DRC | ✅ Fixed (uses `getAllClearanceViolations()`) |
| 152-E | `adjustPlaneAutorouteSettings` outer-layer guard skips outer-layer copper fills & $\le 2$-layer boards | ❌ Open |
| 152-F | User-configurable tuning parameters (CLI/JSON exposure, stub length, via-in-pad) | ⚠️ Partially Implemented (GUI/Cost setting exists; CLI/tuning missing) |
| 152-G | Plane connectivity (void/island) validation is absent | ❌ Open (Future) |
| 152-H | Route power plane-nets first in each routing pass | 💡 Proposed (To benchmark) |

---

## Architecture Background

### Copper Pour Model

Copper pours are represented as `board.ConductionArea` — a subclass of `ObstacleArea` that implements `Connectable`. Each `ConductionArea` belongs to exactly one net (e.g. `GND`) and occupies one PCB layer. The `is_obstacle` flag controls whether foreign-net traces must respect clearances to the pour; it defaults to `false` (traces may pass through the pour geometrically).

### How `Net.contains_plane` Gets Set (Two Paths)

**Path A — `Structure.java` (reliable, fires for standard KiCad exports):**
When the DSN parser processes a `(plane <netname> ...)` scope inside `(structure ...)`, it calls `board.rules.nets.add(..., true)`. This sets `contains_plane = true` immediately. KiCad's `(plane ...)` declaration triggers this.

**Path B — `DsnFile.adjustPlaneAutorouteSettings()` (heuristic fallback):**
Called from `DsnReader.readBoard()` only when the DSN file contains no `(autoroute ...)` scope. It scans `ConductionArea` items: if an area covers ≥ 50% of the board and lies on a non-outer signal layer with no wires, it marks the net's `contains_plane = true`.

*Note:* `LayerStructure.contains_plane(netName)` (used in `Network.java`) only looks at layers whose `is_signal == false`. For Issue093's 2-layer board where both layers are declared `(type signal)`, this always returns `false`. The correct value comes from Path A.

### Plane Routing in `BatchAutorouter`

When `Net.contains_plane() == true`, `autoroute_item()` uses a **plane-routing mode**:
1. If the item's `connected_set` already contains a `ConductionArea` → return `CONNECTED_TO_PLANE` (already done).
2. Otherwise: `route_start_set = connected_set`, `route_dest_set = unconnected_set` (which includes the `ConductionArea`). This is the reverse of standard pad-to-pad routing.
3. The engine searches for a short path to the `ConductionArea`, drops a via if needed, and calls `InsertFoundConnectionAlgo`.
4. `OptViaAlgo.opt_plane_or_fanout_via()` handles post-routing via repositioning.

---

## Detailed Analysis & Sub-Issues

### 152-A: Clearance Violations During Plane-Net Routing (Clarified)

**Symptom:** Historical reports suspected ~62 clearance violations introduced on `Issue093-interf_u.dsn`.

**Investigation findings (September 2026):**
- Full DRC examination of `Issue093-interf_u.dsn` before routing reveals 130 pre-existing violations, all of which are 0.1 µm (`0.00010 mm`) float rounding differences in hole-clearance rules emitted by KiCad (254.1 µm rule vs 254.0 µm pad-to-trace spacing).
- After running 18 passes of autorouting, the total violation count remains exactly 130: **0 new clearance violations were introduced by plane routing**, and all GND pads connected successfully.

### 152-B: False-Work Items (Fixed)

**Was:** `getAutorouteItems()` included pads already connected to a `ConductionArea` for plane nets. This caused `autoroute_item()` to be called pointlessly, wasting time and triggering spurious normalization failures.

**Fix:** `getAutorouteItems()` now skips items whose `connected_set` already contains a `ConductionArea` for plane nets (`connectedSet.stream().anyMatch(ConductionArea.class::isInstance)`).

### 152-C: Router Infinite Loop (Fixed)

**Was:** When all items were false-work, `autoroute_pass()` always returned `true`, and with no board-hash stagnation check, the router looped endlessly.

**Fix:** `runBatchLoop()` now maintains `alreadyRoutedBoardHashes`. If the board hash at pass start was already seen, routing stops. The set is cleared on board restores.

### 152-D: Missing Violation Detection in Statistics (Fixed)

**Was:** `BoardStatistics.clearanceViolations.totalCount` called `board.get_outline().clearance_violation_count()`, which only checked violations from the outline's perspective and missed inter-trace/via violations.

**Fix:** `BoardStatistics.java` was updated to call `clearanceDrc.getAllClearanceViolations()` whenever clearance violations are included.

### 152-E: Heuristic Plane Detection Fallback (Fixed)

**Problem:** `DsnFile.adjustPlaneAutorouteSettings()` originally skipped boards with $\le 2$ layers, skipped outer layers (index 0 and $N-1$), skipped layers with existing wires, and required $\ge 50\%$ board area coverage. Non-KiCad DSN files or boards with outer-layer ground pours failed heuristic detection unless Path A (`(plane ...)` in structure) explicitly fired.

**Fix:** Relaxed `adjustPlaneAutorouteSettings()`:
- Permitted 2-layer and single-layer boards.
- Removed outer-layer exclusion (index 0 and $N-1$).
- Removed existing wire disqualification.
- Lowered coverage threshold from $\ge 50\%$ to $\ge 30\%$ of board outline area.

### 152-F: User-Configurable Tuning Parameters (Fixed)

**Problem:** While `planeViaCosts` was exposed in `RoutingCostSettings` and the GUI, other settings were missing:
- No CLI or JSON config setting to explicitly designate nets as plane nets (overriding CAD exports).
- No option to toggle `planeAsObstacle` on `ConductionArea` via settings.

**Fix:**
- Added `planeNets` (`String[]`) to `RouterSettings`, serialized as `plane_nets`.
- Added `planeAsObstacle` (`Boolean`) to `RouterSettings`, serialized as `plane_as_obstacle` (with backwards-compatible aliases `conduction_is_obstacle`, `planeAsObstacle`, `conductionIsObstacle`).
- Initialized in `DefaultSettings` with nullable/empty defaults to preserve merger precedence invariants.
- Wired through `HeadlessBoardManager.applyPlaneNetsOverride()` and `applyPlaneAsObstacleOverride()` on board load.
- Renamed `RoutingBoard.changeConductionIsObstacle` to `changePlaneAsObstacle` (retaining deprecated alias).

### 152-G: Plane Connectivity & Void/Island Validation (Open)

**Problem:** Foreign signal traces routed through a copper pour (`is_obstacle = false`) physically slice the pour into disjoint pieces. In KiCad, zone fills flow around traces, which can isolate pins into dead copper islands. Freerouting treats `ConductionArea` as monolithic and does not verify topological connectivity of the pour after signal routing.

### 152-H: Route Power Plane-Nets First in Each Pass (Fixed)

**Concept:** In `BatchAutorouter.getAutorouteItems()`, prioritize items belonging to nets where `Net.containsPlane() == true` so they are routed at the very beginning of each pass.

**Algorithmic Rationale:**
1. **Escape Channel Preservation:** Plane connections require only short stubs and vias to reach the copper pour. If signal nets route first, they weave tight traces around IC pins, often blocking the small escape channels needed to drop a via to the plane.
2. **Reduced Ripup Cascades:** Establishing fixed power vias early provides immovable anchors. Signal traces naturally pathfind around them. If power vias are placed last, they often force ripup of complex, completed signal nets.
3. **Throughput:** Plane connections resolve in very few A* search steps. Routing them first quickly reduces the number of incomplete items.

**Candidate Fixtures for Benchmarking:**
- `fixtures/Issue163-pic_programmer.dsn`: 2-layer with bottom GND pour; fast smoke test (~1s/pass).
- `fixtures/Issue093-interf_u.dsn`: 2-layer with bottom GND pour; canonical reproducer (~5s/pass).
- `fixtures/Issue027-zMRETestFixture.dsn`: 2-layer dense board with F.Cu/B.Cu GND pours (~60s/pass).
- `fixtures/Issue219-LogicBoard_smt.dsn`: 4-layer board with dedicated inner VCC and GND planes.

---

## Acceptance Criteria

- [x] Benchmarking `152-H` confirms improved or equal routing completion and via efficiency on candidate plane fixtures without regressions on standard benchmarks.
- [x] Improved heuristic detection in `152-E` for 2-layer and outer-layer pour designs.
- [x] Full configuration support in `RouterSettings`, CLI, and JSON for plane settings (via costs, plane net declarations, planeAsObstacle).
- [ ] No clearance violations or routing regressions introduced across `./gradlew check`.

---

## Related Issues

- **Issue 093** — The canonical reproducer board.
- **Issue 558** — Copper-to-edge clearance not exported in DSN; similar DRC gap.

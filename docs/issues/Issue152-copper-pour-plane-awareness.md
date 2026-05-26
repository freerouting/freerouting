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
| 152-A | Clearance violations introduced during plane-net routing | ❌ Open |
| 152-B | False-work items: pads already connected to plane re-queued every pass | ✅ Fixed |
| 152-C | Router infinite loop when all plane-net items false-work | ✅ Fixed |
| 152-D | `BoardStatistics.clearanceViolations.totalCount` uses incomplete DRC | ❌ Open (see also Issue 558) |
| 152-E | `adjustPlaneAutorouteSettings` outer-layer guard skips outer-layer copper fills | ⚠️ Latent (Path A fires first) |
| 152-F | No user-configurable tuning parameters (via costs, stub length, density) | ❌ Open |
| 152-G | Plane connectivity (void/island) validation is absent | ❌ Open (Future) |

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

## Detailed Bug Analysis

### 152-A: Clearance Violations During Plane-Net Routing (Open)

**Symptom:** Routing `Issue093-interf_u.dsn` produces ~62 clearance violations.

**Likely causes:**
1. **Via placement violates clearances:** The autorouter places a via to connect a top-layer pad to the bottom-layer GND plane. The via's copper ring may be too close to adjacent pads or traces whose clearances were not properly checked during insertion.
2. **Stub trace placement:** The route from the pad's connected group to the `ConductionArea` may underestimated the clearance required relative to other same-layer items.
3. **Over-aggressive optimization:** `opt_changed_area()` or `opt_plane_or_fanout_via()` may pull geometry into violation.

### 152-B: False-Work Items (Fixed)

**Was:** `getAutorouteItems()` included pads already connected to a `ConductionArea` for plane nets. This caused `autoroute_item()` to be called pointlessly, wasting time and triggering spurious normalization failures.

**Fix:** `getAutorouteItems()` now skips items whose `connected_set` already contains a `ConductionArea` for plane nets.

### 152-C: Router Infinite Loop (Fixed)

**Was:** When all items were false-work, `autoroute_pass()` always returned `true`, and with no board-hash stagnation check, the router looped endlessly.

**Fix:** `runBatchLoop()` now maintains `alreadyRoutedBoardHashes`. If the board hash at pass start was already seen, routing stops. The set is cleared on board restores.

### 152-D: Missing Violation Detection in Statistics

**Problem:** `BoardStatistics.clearanceViolations.totalCount` calls `board.get_outline().clearance_violation_count()`, which only checks violations from the outline's perspective. It does NOT call `DesignRulesChecker.getAllClearanceViolations()`.

**Impact:** Inter-trace/inter-via violations from plane routing (like the 62 in Issue093) will **not** appear in the statistics, leading to incorrect "pass" signals in tests.

---

## Acceptance Criteria

Once 152-A is resolved:

- [ ] Routing `Issue093-interf_u.dsn` completes with **0 clearance violations** (measured by `DesignRulesChecker`).
- [ ] All GND pads are successfully connected to the GND copper pour.
- [ ] `BoardStatistics.clearanceViolations.totalCount` matches the global DRC count.
- [ ] No routing regression on other fixture boards (`./gradlew check`).
- [ ] User-controllable plane via costs and stub preferences added to `RouterSettings`.

---

## Related Issues

- **Issue 093** — The canonical reproducer board.
- **Issue 558** — Copper-to-edge clearance not exported in DSN; similar DRC gap.
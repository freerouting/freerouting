# Issue 152 – Copper Pour / Power Plane Awareness in the Autorouter

**GitHub:** https://github.com/freerouting/freerouting/issues/152  
**Status:** Open  
**Priority:** High  

---

## Executive Summary

For multi-layer PCBs it is common to dedicate an entire layer (or a large polygonal fill on a layer) to a single net, typically GND or a power rail. These are called **copper pours**, **power planes**, or **conduction areas** (Freerouting's internal term).

The issue reports that Freerouting's autorouter is substantially unaware of these pours:

1. **Plane detection is heuristic and fragile** – whether a fill counts as a "plane" is inferred at DSN-load time and silently misses many real-world boards.
2. **Plane-to-pad routing is incomplete** – the expected fanout pattern (short stub trace → via → plane) is not reliably produced.
3. **No user-configurable tuning parameters** – via costs, stub length preferences, and per-pad via density have no API or settings surface.
4. **Plane connectivity (void/island) validation is absent** – traces from foreign nets routed through a plane layer can create electrical discontinuities in the pour that are never detected.

---

## Background – How Copper Pours Are Modelled

| Concept | Class / field |
|---|---|
| Copper pour region | `board.ConductionArea` (extends `ObstacleArea`, implements `Connectable`) |
| Obstacle flag | `ConductionArea.is_obstacle` – when `false`, foreign traces may pass through the fill |
| Plane net flag | `rules.Net.contains_plane` – signals to the autorouter that this net uses a pour |
| Plane heuristic | `io.specctra.parser.DsnFile.adjustPlaneAutorouteSettings()` |
| Via cost discount | `AutorouteControl` uses `settings.get_plane_via_costs()` when `contains_plane == true` |
| Connectivity toggle | `RoutingBoard.change_conduction_is_obstacle(boolean)` via `GuiBoardManager` only |

The `contains_plane` flag drives the routing strategy switch in `BatchAutorouter.autoroute_item()`:

```java
if (contains_plane) {
    // Check if already touching the plane
    for (Item curr_item : connected_set) {
        if (curr_item instanceof ConductionArea)
            return CONNECTED_TO_PLANE; // done
    }
    // Route from connected group → toward the plane
    route_start_set = connected_set;
    route_dest_set   = unconnected_set;
} else {
    route_start_set = unconnected_set;
    route_dest_set   = connected_set;
}
```

When the flag is set correctly, the router at least attempts to target the `ConductionArea`. When it is not set, the router tries to connect all GND pads to each other as if they were a normal signal net—an essentially unsolvable problem on a board with a poured GND layer where pads already have galvanic contact with the pour.

---

## Detailed Findings

### Finding 1 – `contains_plane` Detection Works via Two Paths (Both Active)

**Location:** `src/main/java/app/freerouting/io/specctra/parser/DsnFile.java` and `LayerStructure.java`

There are **two independent code paths** that set `Net.contains_plane`:

**Path A – `Network.java` (reliable):** When the DSN `structure` section contains an explicit `(layer ... (type plane))` or equivalent structure, `LayerStructure.contains_plane(net_name)` returns `true` at net-creation time. KiCad's `(plane GND ...)` declaration in the `structure` section triggers this path.

**Path B – `DsnFile.adjustPlaneAutorouteSettings()` (heuristic fallback):** This is only called when the DSN has no `(autoroute ...)` scope, and uses area-threshold heuristics. It has an **outer-layer guard** (`layer_no == 0 || layer_no == last`) that would skip outer-layer fills — but in practice Path A fires first for well-formed KiCad exports.

**Live test results (April 2026) confirm:**
- `Issue015-StackOverflow.dsn` (outer B.Cu GND pour) → `contains_plane=true` ✅
- `Issue027-zMRETestFixture.dsn` (outer F.Cu GND pour) → `contains_plane=true` ✅
- `Issue219-LogicBoard_smt.dsn` (inner In1.Cu VCC pour) → `contains_plane=true` ✅

**Conclusion:** Plane detection is working correctly for standard KiCad exports. The outer-layer guard in `adjustPlaneAutorouteSettings()` is a latent bug that only triggers for DSN files without explicit layer-type information, but it does not affect typical KiCad boards.

---

### Finding 2 – `getAutorouteItems()` Counting Inconsistency for Plane Items

**Location:** `src/main/java/app/freerouting/autoroute/BatchAutorouter.java`, method `getAutorouteItems()`

```java
int net_item_count = board.connectable_item_count(curr_net_no);
if ((connected_set.size() < net_item_count) && (!curr_item.has_ignored_nets())) {
    autoroute_item_list.add(curr_item);
}
```

`connectable_item_count()` counts **all** `Connectable` items including `ConductionArea`. For a GND net with 20 pads and 1 pour:

- `net_item_count` = 21
- A pad whose `connected_set` = {pad, ConductionArea} has size = 2
- `2 < 21` → pad is added to the routing queue
- In `autoroute_item()`: `connected_set` contains a `ConductionArea` → returns `CONNECTED_TO_PLANE` (effectively a no-op)

This is correct in isolation, but creates false work: items that are already connected to the plane still cycle through `autoroute_item()` every pass, consuming time. For heavily-poured boards this is a measurable performance loss. More importantly, **a pad that is NOT yet connected to the plane** (e.g., the first pass before any routing) will attempt the plane routing path — but whether the maze search actually terminates at the `ConductionArea` shape depends on the expansion logic in `MazeSearchAlgo`, which has not been verified for this scenario.

---

### Finding 3 – No User-Configurable Tuning Parameters

Issue 152 explicitly requests:

1. **A weight to control how hard the autorouter tries to shorten the pad-to-via trace.** Currently there is only a generic `plane_via_costs` setting stored in `AutorouteSettings` (exposed in v1.9 as `get_plane_via_costs()`). There is no control over the *trace* cost from pad to the via.
2. **A weight to control how hard the autorouter tries for one via per pad.** No such setting exists. The router will happily use a shared via for multiple pads if it reduces routing cost.

In the current codebase, `RouterSettings` / `RouterOptimizerSettings` have no fields for these. The `BatchAutorouter` reads `settings.get_plane_via_costs()` through `AutorouteControl` but the cost is fixed.

---

### Finding 4 – Plane Connectivity / Void Validation Is Absent

When a trace from a foreign net (e.g., a signal net) is routed through a layer that has a GND pour, it creates a copper-free channel through the plane. If this channel divides the pour into electrically isolated regions, some GND pads may lose their plane connection—even though a via appears to "touch" the pour somewhere.

Freerouting does not model pour connectivity at all. The `ConductionArea` is treated as a single geometrically connected region regardless of traces passing through it. As a result:

- DRC will not flag a GND island.
- The `RatsNest` will show the connection as complete (because the via is inside the pour boundary).
- The exported `.ses` file will suggest the board is fully routed when it actually isn't.

This is the hardest sub-problem in the issue and requires spatial connectivity analysis of the pour polygon minus the obstacle footprints of foreign-net traces/vias.

---

## Affected Code Locations

| File | Relevance |
|---|---|
| `board/ConductionArea.java` | Core model for copper pour regions |
| `board/RoutingBoard.java` (`change_conduction_is_obstacle`) | Bulk toggle; only GUI-accessible |
| `autoroute/BatchAutorouter.java` (`getAutorouteItems`, `autoroute_item`) | Routing queue building + plane routing strategy |
| `autoroute/BatchAutorouterV19.java` (`autoroute_item`) | Mirrored logic for v1.9 compatibility build |
| `io/specctra/parser/DsnFile.java` (`adjustPlaneAutorouteSettings`) | Fragile plane-detection heuristic |
| `io/specctra/parser/LayerStructure.java` (`contains_plane`) | Reliable but rarely triggered plane detection |
| `io/specctra/parser/Network.java` (line 1124) | Sets `contains_plane` at net-creation time |
| `rules/Net.java` (`contains_plane`) | Flag driving routing strategy |
| `board/OptViaAlgo.java` (`opt_plane_or_fanout_via`) | Via optimization for plane-connected vias |
| `settings/RouterSettings.java` | Missing planeViaCosts, planeStubPreference fields |

---

## Sub-Issues (Actionable Breakdown)

### Sub-issue 1 ✅ Understand the Current State (Research)
**Done.** Full analysis + live test run results in this document.

---

### Sub-issue 2 ✅ Plane Detection Baseline (Verified Working)
**Plane detection via `Net.contains_plane` is already correct for KiCad DSN exports.**

Live tests confirm all three tested boards (outer B.Cu, outer F.Cu, inner In1.Cu) correctly get `contains_plane=true` via the `Network.java` → `LayerStructure.contains_plane()` code path.

**Remaining latent risk:** The `adjustPlaneAutorouteSettings()` fallback heuristic has an outer-layer guard that would misidentify non-KiCad DSN files with outer pours and no explicit `(type plane)` declaration. This is low-priority cleanup. Add to future backlog.

---

### Sub-issue 3 ✅ Pending – Fix Clearance Violations During Plane Routing

**Priority:** Critical  
**Scope:** `autoroute/BatchAutorouter.java`, `autoroute/AutorouteEngine.java`, `board/RoutingBoard.java`

**Confirmed Bug (Issue093):** Routing `Issue093-interf_u.dsn` (which has a bottom-copper GND pour) introduces **62 clearance violations** plus logs an internal error in `BatchAutorouter.autoroute_pass`. This board has `contains_plane=true` for GND, meaning the plane-routing code path IS active when these violations occur.

**Investigation steps:**
1. Run `Issue093` with TRACE logging enabled, filtered to the GND net.
2. Identify which traces/vias are causing the violations (likely a via placed too close to another pad while trying to connect to the plane).
3. Check whether `MoveDrillItemAlgo.check()` is correctly enforcing clearances when inserting plane-connection vias.
4. Verify `opt_plane_or_fanout_via()` in `OptViaAlgo` doesn't move vias into violation after initial placement.

**Risk:** High — this is a safety-critical bug (clearance violations mean the manufactured board may have shorts or DRC failures).

---

### Sub-issue 4 – Eliminate False Work in `getAutorouteItems()` for Plane Nets

**Priority:** Medium  
**Scope:** `BatchAutorouter.getAutorouteItems()`

**Problem:** Items already connected to a `ConductionArea` are incorrectly enqueued each pass, causing unnecessary cycles through `autoroute_item()` which returns `CONNECTED_TO_PLANE`.

**Proposed Fix:**
```java
if (contains_plane) {
    boolean alreadyConnectedToPlane = connected_set.stream()
        .anyMatch(c -> c instanceof ConductionArea);
    if (alreadyConnectedToPlane) continue;
}
```

**Risk:** Low, but must not exclude items whose connected group does NOT yet reach the plane.

---

### Sub-issue 5 – Add `planeViaCosts` and `planeStubPreference` to `RouterSettings`

**Priority:** Medium  
**Scope:** `settings/RouterSettings.java`, `settings/sources/DefaultSettings.java`

**Problem:** No user-configurable weights for plane routing behavior exist (via costs, per-pad via density preference).

**Proposed Fields:**
```java
// Cost of a via connecting to a power plane (lower = prefer plane vias)
public Integer planeViaCosts;       // default: 5 (current hard-coded value)

// 0.0-1.0: prefer one via per pad rather than shared vias
public Double planeStubPreference; // default: 0.0 (no preference)
```

**Note:** Must remain `null`-initialized per the `RouterSettings` invariant.

---

### Sub-issue 6 – Post-Routing Plane Void Detection (Research)

**Priority:** Low (complex, future work)  
**Scope:** New class or extension of `drc.DesignRulesChecker`

**Problem:** Traces from foreign nets passing through the plane layer may create electrically isolated "islands" in the pour, which Freerouting currently does not detect.

**Proposed Approach:**
1. For each `ConductionArea`, compute the polygon minus the union of obstacle footprints on the same layer.
2. Perform connected-component flood-fill analysis on the remaining geometry.
3. Check that all pads/vias of the plane net are in the same connected component.
4. Report isolated regions as DRC violations.

**Risk:** High complexity. Keep as long-term/research work item.

---

## Test Results Summary (Live Run, April 2026)

| Test | Fixture | Result | Notes |
|---|---|---|---|
| `issue015_outerLayerGndPour_containsPlaneIsTrue` | Issue015 B.Cu GND pour | ✅ PASS | `contains_plane=true` |
| `issue027_outerLayerGndPour_containsPlaneIsTrue` | Issue027 F.Cu GND pour | ✅ PASS | `contains_plane=true` |
| `issue219_innerLayerVccPour_containsPlaneIsTrue` | Issue219 In1.Cu VCC pour | ✅ PASS | `contains_plane=true` |
| `issue027_routingProducesPlaneConnection` | Issue027 F.Cu GND routing | ✅ PASS | No violations; 938 incomplete (limited to 30 items) |
| `issue219_innerVccPlane_routingDoesNotIntroduceClearanceViolations` | Issue219 routing | ✅ PASS | No violations; 952 incomplete (limited to 50 items) |
| `issue054_outerLayerGndPour_doesNotCrash` | Issue054 F.Cu GND routing | ✅ PASS | `contains_plane=true`; 243 incomplete |
| `issue093_bottomCopperGndPour_routingDoesNotIntroduceClearanceViolations` | Issue093 bottom GND routing | ❌ FAIL | **62 clearance violations** introduced + internal error |

---

## Proposed Test Fixtures

### Test 1 (Plane Detection — Already Passing)

Use `Issue015-StackOverflow.dsn`, `Issue027-zMRETestFixture.dsn`, `Issue219-LogicBoard_smt.dsn` to verify `contains_plane=true` via direct `DsnReader` board load (no routing).

### Test 2 (Clearance Violation Regression — Sub-issue 3)

Use `Issue093-interf_u.dsn`. After routing, assert `clearanceViolations.totalCount == 0`. Currently fails with 62 violations. This test must **pass** before Sub-issue 3 is closed.

### Test 3 (Plane Routing Completeness)

Use `Issue027` or `Issue219` with `maxPasses(10), maxItems(200)` to verify that plane-net incomplete counts decrease substantially across passes.

### Test 4 (Via Optimization for Plane Vias)

Unit test for `OptViaAlgo.opt_plane_or_fanout_via()` — use a synthetic board with a single-pad GND net touching a `ConductionArea`, verify the method returns `true` and moves the via closer to the pour.

---

## Risks Summary

| Risk | Severity | Mitigation |
|---|---|---|
| Issue093 clearance violations (confirmed bug) | Critical | Investigate `BatchAutorouter.autoroute_pass` error; fix via placement logic |
| Maze search may not stop at `ConductionArea` boundary | High | Add TRACE logging in `MazeSearchAlgo`; reproduce on Issue093 |
| `adjustPlaneAutorouteSettings()` latent outer-layer guard | Low | Cleanup only; `Network.java` Path A fires first for standard KiCad |
| Plane void detection is too expensive for CI | High | Keep as scripts/tests only; gate with explicit flag |
| `RouterSettings` new fields break merger priority | Low | Follow null-initialized invariant strictly |
| Changes to `getAutorouteItems` cause regression on non-plane boards | Medium | Run full `./gradlew check` after each sub-issue |

---

## Acceptance Criteria (Overall)

1. ✅ Boards with `(plane ...)` declarations correctly get `contains_plane=true` (already working).
2. ❌ Routing `Issue093-interf_u.dsn` must produce 0 clearance violations (currently 62).
3. After autorouting a board with a pour, all pads belonging to the plane net are connected to the `ConductionArea` (verified via `get_connected_set` check).
4. `getAutorouteItems()` does not enqueue items already connected to the plane.
5. No regression in any existing fixture test (`./gradlew check`).
6. No new clearance violations introduced on any fixture board.

---

## Related Issues

- **#70** – Initial request to ignore plane nets during routing (partial workaround, not a fix).
- **#558** – Copper-to-edge clearance not included in DSN (similar DRC gap).
- **#420** – OOM in optimizer (may be aggravated by false routing work on plane nets).

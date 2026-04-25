# Issue 558: Copper-to-Edge Clearance Not Respected

**GitHub Issue:** https://github.com/freerouting/freerouting/issues/558  
**Fixture file:** `fixtures/Issue558-dev-board.dsn`  
**Regression test:** `src/test/java/app/freerouting/fixtures/DevBoardClearanceRoutingTest.java`  
**Status:** Investigation complete. Fix not yet implemented.  
**Priority:** High (correctness; KiCad DRC fails on routes produced by freerouting)

---

## 1. Executive Summary

When a KiCad user has "copper to board edge clearance" set to 0.5 mm and routes through
freerouting, the resulting traces may be placed only 0.2 mm from the board edge (the
conductor-to-conductor clearance). KiCad's own DRC then reports clearance violations.

Freerouting's built-in DRC does **not** detect this discrepancy because:

1. KiCad does **not export** the copper-to-edge clearance value into the Specctra DSN file.
2. Freerouting therefore routes traces at the default conductor clearance (0.2 mm in this fixture).
3. Freerouting's DRC only checks against its own clearance rules; since the traces comply with
   those rules, it reports 0 violations.

The regression test `DevBoardClearanceRoutingTest` currently **passes but for the wrong reason**:
it asserts `clearanceViolations.totalCount == 0`, which is true because the internal DRC is
checking the wrong threshold (0.2 mm instead of 0.5 mm).

---

## 2. Problem Chain (step by step)

### 2.1 KiCad does not export copper-to-edge clearance

The fixture `Issue558-dev-board.dsn` was produced by KiCad 9. Its `(structure ...)` section is:

```dsn
(structure
  (layer F.Cu (type signal) (property (index 0)))
  (layer B.Cu (type signal) (property (index 1)))
  (boundary (path pcb 0  141500 -93300  112500 -93300  112500 -41400  141500 -41400  141500 -93300))
  (via "Via[0-1]_600:300_um")
  (rule
    (width 200)
    (clearance 200)
    (clearance 50 (type smd_smd))
  )
)
```

The unit is `um` at resolution 10 (1 DSN unit = 0.1 µm → `dsn_to_board(200) = 200 × 10 = 2000`
board units = 0.2 mm). **There is no copper-to-edge clearance field anywhere in the file.**

The Specctra DSN grammar technically allows a `(clearance_class ...)` token inside a `(boundary
...)` scope (freerouting already parses it in `Structure.read_boundary_scope()`), but KiCad's
DSN exporter does not emit it.

### 2.2 Freerouting assigns the outline the default clearance class

In `HeadlessBoardManager.create_board()`:

```java
// p_outline_clearance_class_name is null → falls through to AREA default
outline_cl_class_no = p_rules.get_default_net_class()
    .default_item_clearance_classes
    .get(DefaultItemClearanceClasses.ItemClass.AREA);  // returns 1 = "default"
```

`DefaultItemClearanceClasses` initialises all entries to 1 via `set_all(1)`. Therefore the
`BoardOutline`'s clearance class is "default" class 1, meaning traces are kept 0.2 mm from
the outline—not 0.5 mm.

### 2.3 The router routes at 0.2 mm from the board edge

`ShapeSearchTree.calculate_tree_shapes(BoardOutline)` applies the outline's clearance class
compensation:

```java
int cmp_value = this.clearance_compensation_value(p_board_outline.clearance_class_no(), layer_no);
result[curr_no] = tmp_polyline.offset_shape(half_width + cmp_value, 0);
```

With clearance class 1 (= 0.2 mm), traces are forced to stay 0.2 mm away—exactly satisfying
freerouting's rules but violating KiCad's 0.5 mm requirement.

### 2.4 Freerouting's DRC is checked against its own clearance matrix

In `BoardStatistics`:

```java
// Clearance violations
this.clearanceViolations.totalCount = board
    .get_outline()
    .clearance_violation_count();
```

`Item.clearance_violations()` looks up:

```java
double minimum_clearance = board.rules.clearance_matrix.get_value(
    curr_item.clearance_class,   // trace class
    this.clearance_class,        // outline class = 1 ("default") = 0.2 mm
    shape_layer(i), false);
```

Since the router placed traces at exactly 0.2 mm, the DRC finds no overlap when checking at
0.2 mm. Result: `clearanceViolations.totalCount == 0` — correct by freerouting's own rules.

### 2.5 KiCad's post-import DRC catches the violation

When the user imports the generated `.ses` file back into KiCad, KiCad applies its own
"copper to board edge clearance" (0.5 mm) and finds violations wherever a trace is within
0.5 mm of the board boundary. The autorouter result is deemed invalid by KiCad's DRC.

---

## 3. Test Status

### Current state (incorrect pass)

```java
// DevBoardClearanceRoutingTest.java
@Test
void test_Issue_558_Clearance_violation_at_board_edge() {
    var job = GetRoutingJob("Issue558-dev-board.dsn");
    job = RunRoutingJob(job);
    var statsAfter = GetBoardStatistics(job);
    assertEquals(0, statsAfter.connections.incompleteCount, ...);
    assertEquals(0, statsAfter.clearanceViolations.totalCount, ...);
}
```

Both assertions currently pass because:
- `incompleteCount == 0`: routing completes fully ✓ (correct behaviour)
- `clearanceViolations.totalCount == 0`: DRC finds no violations at 0.2 mm ✓ (but
  verifying the wrong threshold — should be 0.5 mm)

### Expected corrected behaviour

Once the fix is implemented, the test should:
- Still assert `incompleteCount == 0`
- Assert `clearanceViolations.totalCount == 0` — but this time because the router
  correctly routed at 0.5 mm from the edge (not 0.2 mm)

If the fix is applied only to the DRC (not the router), the test would start **failing** with
`clearanceViolations.totalCount > 0`, confirming that the router is placing traces too close
to the edge. The goal is then to additionally fix the routing so the test passes again, now
for the correct reason.

---

## 4. Code Locations

| File | Relevant location | Role |
|---|---|---|
| `io/specctra/parser/Structure.java:261` | `read_boundary_scope()` | Parses `(clearance_class ...)` from boundary; sets `outline_clearance_class_name`. KiCad never emits this, so it stays `null`. |
| `interactive/HeadlessBoardManager.java:259` | `create_board()` | Falls back to `ItemClass.AREA` (class 1) when `outline_clearance_class_name == null`. |
| `board/BasicBoard.java:448` | `insert_outline()` | Inserts `BoardOutline` with the resolved `clearance_class_no`. |
| `board/ShapeSearchTree.java:902` | `calculate_tree_shapes(BoardOutline)` | Expands outline line segments by clearance compensation → determines how close routing can go. |
| `board/Item.java:353` | `clearance_violations()` | Checks pairs of items for clearance violations using `ClearanceMatrix.get_value(class1, class2, layer, false)`. |
| `core/scoring/BoardStatistics.java` | (clearance violations block) | Only counts `board.get_outline().clearance_violation_count()` — incomplete; misses non-outline-item violations too. |
| `drc/DesignRulesChecker.java:43` | `getAllClearanceViolations()` | Comprehensive DRC: iterates all board items. Currently unused by `BoardStatistics`. |
| `rules/ClearanceMatrix.java:137` | `get_value()` | Returns 0 (no clearance) if a class index is out of bounds — silent failure (BUG-5, see clearance analysis doc). |

---

## 5. Root Causes

### RC-1 — DSN format does not carry copper-to-edge clearance (upstream)

The Specctra DSN format supports `(clearance_class ...)` inside `(boundary ...)` but KiCad
does not export it. This is an upstream limitation in KiCad's DSN exporter. Without this
information, freerouting has no way to know the 0.5 mm requirement exists.

**Impact:** Every KiCad user with a copper-to-edge clearance larger than the default
conductor-to-conductor clearance will experience this issue.

### RC-2 — No mechanism to specify or detect the edge clearance in freerouting

Even if freerouting had the 0.5 mm value (e.g., via a CLI argument or JSON config file),
there is no code path that:
1. Applies a different clearance for conductor-to-outline checks in the router, and
2. Exposes that clearance in the DRC.

### RC-3 — `BoardStatistics` uses an incomplete DRC method

`BoardStatistics.clearanceViolations.totalCount` is computed by checking only the
`BoardOutline` item's violations via `board.get_outline().clearance_violation_count()`.
The broader `DesignRulesChecker.getAllClearanceViolations()`, which iterates ALL items on
both sides of each pair, is not used. Even if `clearance_violation_count()` were computed
at the right threshold, it is architecturally incomplete as the single source of truth.

---

## 6. Did a Previous Fix Get Reverted?

Based on the git log:

- Commit `c4607ddb` ("Re-enable DevBoard clearance routing test", 2026-04-23): removed
  the `@Disabled` annotation. Before this commit, the test was disabled with the reason
  "Temporary disabled: Freerouting leaves 2 items unconnected."
- The referenced PR #567 ("might be part of the solution") is not yet merged.
- There is no evidence of a specific copper-to-edge clearance fix that was merged and then
  reverted.

The most likely scenario: PR #567 introduced some mechanism (which may have caused the 2
incomplete connections regression), and was rolled back. The test was re-enabled in a "passing
for the wrong reason" state to at least exercise the code path in CI.

---

## 7. Proposed Solutions

### Solution A — Add `copperToEdgeClearanceUm` to `RouterSettings` (minimal, recommended)

**Description:** Add a single nullable field to `RouterSettings`:

```java
// RouterSettings.java
@SerializedName("copper_to_edge_clearance_um")
public Double copperToEdgeClearanceUm;  // null = use outline's default clearance class
```

After full board load (in `HeadlessBoardManager.loadFromSpecctraDsn()` or
`HeadlessBoardManager.prepareJob()`), if `copperToEdgeClearanceUm != null`:

1. Convert the value to board units:
   ```java
   int edgeClearanceBoardUnits = (int) Math.round(
       board.communication.coordinate_transform.dsn_to_board(
           copperToEdgeClearanceUm / 1000.0  // µm → mm → board units via DSN transform
       ));
   ```
   Actually, since `copperToEdgeClearanceUm` is in µm and the board units depend on the
   coordinate transform, the conversion should use `Unit.scale()` appropriately. For the
   fixture (resolution = um × 10), 500 µm × 10 = 5000 board units.

2. Append (or update) a clearance class named `"board_edge"` in the `ClearanceMatrix`:
   ```java
   board.rules.clearance_matrix.append_class("board_edge");
   int edgeClassNo = board.rules.clearance_matrix.get_no("board_edge");
   // Set clearance from board_edge to all other classes
   for (int i = 1; i < board.rules.clearance_matrix.get_class_count(); i++) {
       for (int layer = 0; layer < board.layer_structure.arr.length; layer++) {
           board.rules.clearance_matrix.set_value(edgeClassNo, i, layer, edgeClearanceBoardUnits);
           board.rules.clearance_matrix.set_value(i, edgeClassNo, layer, edgeClearanceBoardUnits);
       }
   }
   ```

3. Re-insert the board outline with the new clearance class:
   ```java
   BoardOutline outline = board.get_outline();
   // Update outline's clearance class to edgeClassNo (requires reflection or new method)
   outline.setClearanceClassNo(edgeClassNo);
   board.search_tree_manager.remove(outline);
   board.search_tree_manager.insert(outline);
   ```
   Note: `BoardOutline` doesn't currently expose `setClearanceClassNo()`. Adding it or
   using `Item.change_clearance_class(int)` if it exists is needed.

**Pros:**
- Minimal change; fits existing `RouterSettings` nullable-field contract.
- Users can configure via CLI: `--router.copper_to_edge_clearance_um=500`
- Router would keep traces at the correct distance from the edge.
- DRC would detect violations if any trace is placed too close.

**Cons:**
- KiCad users still need a separate mechanism to pass the 0.5 mm value (CLI arg, JSON config,
  or KiCad plugin passing it via API).
- Does not fix the KiCad-side export limitation.

### Solution B — Fix the DRC method used by `BoardStatistics` (simpler, partial)

**Description:** Replace the incomplete DRC in `BoardStatistics` with the comprehensive one:

```java
// BoardStatistics.java — replace:
this.clearanceViolations.totalCount = board.get_outline().clearance_violation_count();

// With:
var drcChecker = new app.freerouting.drc.DesignRulesChecker(board, null);
this.clearanceViolations.totalCount = drcChecker.getAllClearanceViolations().size();
```

**Effect:** The DRC becomes comprehensive but still uses freerouting's internal clearance
rules (0.2 mm). The test would still pass, but now correctly because all item-pair violations
are checked. This alone does **not** fix the root problem (traces routed at 0.2 mm from edge
when 0.5 mm is required), but it is a necessary correctness improvement regardless.

**Pros:** Low risk; makes the DRC complete.  
**Cons:** Does not fix the routing behavior.

### Solution C — Request KiCad to export copper-to-edge clearance in DSN (upstream)

**Description:** File a KiCad enhancement request to add copper-to-edge clearance to
the DSN `(boundary ...)` scope using the existing `(clearance_class ...)` syntax. Example:

```dsn
(boundary
  (path pcb 0  141500 -93300 ... 141500 -93300)
  (clearance_class "board_edge")
)
(structure
  ...
  (rule
    (clearance 5000 (type wire_board_edge))
    (clearance 5000 (type via_board_edge))
    (clearance 5000 (type smd_board_edge))
  )
)
```

Freerouting already parses `(clearance_class ...)` inside `(boundary ...)`. If KiCad exports
it, freerouting would automatically use the correct clearance class. No changes to freerouting's
parser would be needed; only `HeadlessBoardManager.create_board()` needs to handle the
`outline_clearance_class_name` being non-null (which it already does).

**Pros:** Clean, standards-compliant, zero freerouting change needed on the routing side.  
**Cons:** Requires KiCad to implement and release; blocks the fix on an external project.

### Solution D — Support the clearance class via network class rule notation

**Description:** KiCad could alternatively encode the copper-to-edge clearance as a special
net-class rule (e.g., a class with all nets and a `"wire_board_edge"` clearance type) in
the `(network ...)` scope. Freerouting would need to recognise the `board_edge` clearance
class pair in `Structure.set_clearance_rule()` and apply it to the outline's clearance class.

This is more invasive and less clean than Solution C.

---

## 8. Recommended Fix Plan (stepwise, safest to riskiest)

### Step 1 — Fix `BoardStatistics` DRC method (no routing impact)

Replace `board.get_outline().clearance_violation_count()` with
`DesignRulesChecker.getAllClearanceViolations().size()` in `BoardStatistics`.

This is the correct thing to do regardless of Issue 558: the current DRC is incomplete. It
may cause the test to keep passing (correct) or start failing (revealing existing violations).

**Affected file:** `src/main/java/app/freerouting/core/scoring/BoardStatistics.java`  
**Risk:** Very low. Does not change routing behavior.

### Step 2 — Add `copperToEdgeClearanceUm` to RouterSettings

Add the nullable field. No routing impact until it is non-null; existing tests unaffected.

**Affected file:** `src/main/java/app/freerouting/settings/RouterSettings.java`  
**Risk:** None (field is nullable, merger ignores null fields).

### Step 3 — Apply the edge clearance after board load

In `HeadlessBoardManager` (after board construction), apply the `copperToEdgeClearanceUm`
floor to the `BoardOutline`'s clearance class. This requires:

a. `Item.change_clearance_class(int)` or a new `BoardOutline.setClearanceClassNo(int)` method.  
b. Re-inserting the outline into the search tree after the change.  
c. Logic to append or update the "board_edge" clearance class in `ClearanceMatrix`.

**Affected files:**
- `src/main/java/app/freerouting/interactive/HeadlessBoardManager.java`
- `src/main/java/app/freerouting/board/BoardOutline.java` (new setter or method)
- `src/main/java/app/freerouting/board/Item.java` (possibly new `change_clearance_class`)

**Risk:** Low-to-medium. The change only activates when `copperToEdgeClearanceUm` is non-null.
Existing routing behavior for all current test fixtures is unchanged.

### Step 4 — Update `DevBoardClearanceRoutingTest`

Modify the test to inject the expected copper-to-edge clearance via `TestingSettings` or
a direct RouterSettings field. The expected value (0.5 mm = 500 µm) should be set explicitly.

```java
@Test
void test_Issue_558_Clearance_violation_at_board_edge() {
    var testingSettings = new TestingSettings();
    testingSettings.setCopperToEdgeClearanceUm(500.0);  // 0.5mm as per KiCad board setup
    var job = GetRoutingJob("Issue558-dev-board.dsn", testingSettings);
    job = RunRoutingJob(job);
    var statsAfter = GetBoardStatistics(job);
    assertEquals(0, statsAfter.connections.incompleteCount, "All nets should be routed");
    assertEquals(0, statsAfter.clearanceViolations.totalCount,
        "No clearance violations – including the 0.5mm edge clearance");
}
```

**Risk:** Once Step 3 is done, this test validates the full fix.

### Step 5 (deferred) — Upstream KiCad DSN export fix

File a KiCad enhancement request so that the copper-to-edge clearance is exported to the DSN
`(boundary ...)` scope. This would benefit all users without requiring any CLI/config arguments.

---

## 9. Additional Bugs Found During Investigation (related, documented in clearance-loading-and-settings-integration.md)

These do not need to be fixed for Issue 558 but are flagged here to maintain context:

- **BUG-2:** `clearance_safety_margin = 16` is applied during routing but not during DRC; documented discrepancy.
- **BUG-5:** `ClearanceMatrix.get_value()` returns 0 (no clearance!) on out-of-bounds class/layer, only logging at TRACE level.
- **BUG-6:** `max_value_on_layer` cache in `ClearanceMatrix` is never invalidated on decrease.

---

## 10. Acceptance Criteria

- [ ] `DevBoardClearanceRoutingTest` fails before the fix (Step 1+4 reveals that DRC was wrong) and passes after (Step 3+4 makes the router route correctly at 0.5mm from edge).  
  *OR*  
  Test is updated per Step 4 and passes end-to-end with Step 3 applied.
- [ ] No regressions in any existing routing fixture tests (`./gradlew test`).
- [ ] `Dac2020Bm01RoutingTest` (quickest smoke test) passes with ≤ baseline clearance violations.
- [ ] `BoardStatistics.clearanceViolations.totalCount` uses `DesignRulesChecker.getAllClearanceViolations()`.
- [ ] All fields in `RouterSettings` (including new `copperToEdgeClearanceUm`) are nullable with no default initializer outside `DefaultSettings.getSettings()`.
- [ ] The routing result for `Issue558-dev-board.dsn` with `copperToEdgeClearanceUm=500` produces zero edge-clearance violations AND zero incomplete connections.


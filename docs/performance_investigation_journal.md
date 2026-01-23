# Performance Investigation Journal

## Theories and Potential Fixes

### Theory 1: Early Termination / Poor Routing Quality
- **Observation**: Current version ends with 92 unrouted items, while v1.9 achieves 26 unrouted items.
- **Observation**: 
  - Current: Pass 1 (21s, 100 unrouted), Pass 2 (6s, 105 unrouted), Pass 3 (5s, 92 unrouted).
  - v1.9: Pass 1 (74s, 55 unrouted), Pass 2 (107s, 26 unrouted).
- **Inference**: Current version is much faster per pass but effectively broken. Pass 1 accomplishes very little compared to v1.9. Pass 2 regression (105 unrouted) suggests instability.
- **Potential Fix**: Investigate if search depth/expansion limits are too low or if an exception is aborting the search.
- **Update**: Searched for "limit reached" and "Exception" in logs. Found none (except unrelated analytics 501). The router is not crashing, it's just failing to find paths.
- **Critical Finding**: Found many "No shapes returned" debug messages in `freerouting-current.log`.
  - `AutorouterEngine.complete_expansion_room: No shapes returned for net #98 on layer 0...`
  - This suggests the router calculates available space as empty, thus finding no path. This explains the "fast failure".
- **Log Confirmation**: Checked `AutorouteEngine.java` in `src_v19`. It *does* contain the same "No shapes returned" debug log. Since this log is absent from `freerouting-v190.log` (verified by grep), v1.9 definitively *does not* encounter this error state.
- **Deduction**: The regression causes `complete_expansion_room` to fail (return empty) in Current, whereas v1.9 succeeds. The "shove-aware" logic *skips* `restrain_shape` for shoveable items (preserving the room). Logic dictates that skipping `restrain` preserves the room, so emptiness must come from the `!is_shoveable` path (where it *does* call `restrain_shape`).
- **Hypothesis**: `restrain_shape` implementation in Current version is broken/regressed compared to v1.9.

### Theory 2: Log Volume Impact?
- **Observation**: Current v1.9 doesn't have detailed debug logging. The user noted "v1.9 often performs better now, but it doesn't have such detailed DEBUG logging."
- **Status**: Logs are 67MB vs 47MB. Difference doesn't seem large enough to explain a massive degradation in quality, but maybe valid for speed if quality was equal. Here quality is the main issue.

### Theory 3: "No shapes returned" / Expansion Room Regression
- **Observation**: "No shapes returned" errors in current log.
- **Hypothesis**: The logic for calculating expansion rooms (likely "shove-aware" or similar) is returning empty shapes where it shouldn't, causing the router to think the board is blocked.
- **Root Cause Identified**:
  - `ShapeSearchTree.complete_shape` in Current version contains a new block labeled `// FEATURE: Shove-aware room completion`.
  - It defaults to `boolean shoveAwareEnabled = true;`.
  - This logic *skips* `restrain_shape` for "shoveable" items (like traces).
  - While intended to rely on later shoving, skipping the cut appears to create room shapes that subsequent steps (or interactions with other obstacles) cannot handle, resulting in an empty intersection/cut later on ("No shapes returned").
  - v1.9 does *not* have this logic; it always calls `restrain_shape` and succeeds.
- **Conclusion**: The "Shove-aware" feature is experimental/unfinished and causing the router to fail in complex scenarios where v1.9 (standard algorithm) succeeds.

### Theory 4: Incorrect Clearance Values (User Suggested)
- **Observation**: High unrouted items count persists even after fixing "No shapes returned".
- **Hypothesis**: If clearance values are loaded incorrectly (too high), the router might see valid paths as blocked, causing "fast failure" as it quickly exhausts search options.
- **Action**: Tested with hardcoded `clearance = 1`.
- **Result**: **DISPROVEN**. Routing quality did NOT improve (99 unrouted items). The router worked longer (1m 40s) but failed to find paths, indicating the issue is fundamental to the search/geometry logic, not the clearance size.

### Theory 5: Logic Drift in `complete_shape` or `divide_large_room`
- **Observation**: Disabling "shove-aware" block in base class didn't fully fix it. v1.9 works.
- **Investigation**: Found that `ShapeSearchTree90Degree` and `ShapeSearchTree45Degree` OVERRIDE `complete_shape` and contain the SAME "Shove-aware" logic block.
- **Conclusion**: The router uses these subclasses (depending on board rules), bypassing the base class fix.
- **Action**: Remove "Shove-aware" logic from `ShapeSearchTree90Degree.java` and `ShapeSearchTree45Degree.java`.
- **Result**: Improved unrouted count from 92 to 73. Still not matching v1.9 (26 unrouted).

### Theory 6: Missing/Misconfigured Routing Settings (User Suggested)
- **Observation**: GUI settings dialog contains many settings (snap angle, push/shove, etc.) that might not be correctly propagating to `RouterSettings` or the headless autorouter.
- **Hypothesis**: Critical routing parameters (like "restrict pin exit directions" or "ignore conducting areas") are defaulting to incorrect values in the current version because they aren't being loaded from the same source or applied correctly.
- **Action**: Investigate where these settings are stored and ensure they are correctly mapped to `RouterSettings` and used by the autoroute engine.

### Theory 7: High Undesired Direction Cost in `RouterScoringSettings`
- **Observation**: Comparisons of `RouterScoringSettings.java` (Current) and `AutorouteSettings.java` (v1.9) revealed a significant discrepancy in the cost penalty for routing against the preferred layer direction.
- **Details**:
    - v1.9: Base cost is `1.0` + small aspect ratio factor (~0.1). Total ~1.1.
    - Current: Base cost is `2.5` + small aspect ratio factor. Total ~2.6.
- **Hypothesis**: The significantly higher penalty (2.5x) in the current version prevents the router from utilizing available paths that go against the preferred direction, leading to more unrouted items.
- **Action**: Check `RouterScoringSettings.java`.
- **Result**: `defaultUndesiredDirectionTraceCost` is initialized to `1.0` in the source code, contradicting the observation. However, dynamic calculation in `RouterSettings` might be different. Need to verify where `2.5` comes from.
- **Action**: Change `defaultUndesiredDirectionTraceCost` in `RouterScoringSettings.java` from `2.5` to `1.0`. (Pending verification of actual runtime value).

  5.  **Random Shuffle**: Baseline to check if a specific fixed order is preventing convergence (escaping local optima).

### Theory 9: DSN Settings Loading
- **Theory**: `AutorouteSettings` from DSN files might not be correctly read or applied, leading to the router using incorrect settings (e.g., failing to pick up board-specific costs or strategies).
- **Investigation**:
  - Traced `Structure.read_scope` -> `AutorouteSettings.read_scope`. Confirmed that settings are parsed and a `RouterSettings` object is created.
  - Confirmed `HeadlessBoardManager` calls `applyNewValuesFrom` to merge these settings into the active `RoutingJob`.
  - Confirmed `applyBoardSpecificOptimizations` is called *after* DSN loading, ensuring dynamic cost calculations (like the 2.5 undesired direction cost) are applied correctly.
  - **Issue Identified**: `ReflectionUtil` merges DSN settings into the job settings. Since `RouterSettings` created from DSN contain class-defaults (e.g. `start_ripup_costs=100`), these defaults will overwrite any CLI-provided or previously set values, potentially resetting tuned parameters to defaults.
  - **Relevance to Regression**: The benchmark DSN file (`Issue508-DAC2020_bm01.dsn`) **does not contain an `autoroute_settings` section**. Therefore, this entire loading path is skipped, and no overwriting occurs. The router uses default settings + `applyBoardSpecificOptimizations`.
  - **Conclusion**: This is **NOT** the cause of the performance regression for the test case. The logic is working as intended (though the overwriting behavior is a potential bug for other scenarios).

- **Observation**: Suspected that `AutorouteSettings` defined in the input DSN file might not be correctly read or applied in the current implementation (both GUI and CLI).
- **Hypothesis**: If settings from DSN are ignored, the router might be running with defaults that are suboptimal for the specific board, leading to performance regression.
- **Verification Plan**: Trace code from DSN parsing to router configuration for both execution modes.


## Proposed Fixes

### Fix 1: Disable Shove-Aware Logic (Recommended)
- **Description**: Set `shoveAwareEnabled = false` in `ShapeSearchTree.java`.
- **Reasoning**: This immediately reverts the behavior to v1.9 standard routing logic, which is proven to work for this test case. It eliminates the "No shapes returned" error and restores robustness.
- **Pros**: Low risk, high confidence fix.
- **Cons**: Disables the new feature (which seems broken anyway).

### Fix 2: Debug Shove-Aware Logic
- **Description**: Deep dive into why skipping `restrain_shape` leads to empty results later.
- **Reasoning**: Keeps the new feature but requires significant debugging time to understand the geometric failure mode.
- **Pros**: Potentially better routing if fixed.
- **Cons**: Time-consuming, high risk of not finding a quick solution.


## Notes

### "No shapes returned" Log Spam
- **Symptom**: The log file is flooded with `Restrain returned empty for obstacle` messages from `ShapeSearchTree45Degree.java`.
- **Frequency**: Measured ~118,655 occurrences in a single test run.
- **Context**: This occurs during `MazeSearchAlgo.find_connection` -> `ShapeSearchTree45Degree.complete_shape` -> `restrain_shape`.
- **Finding**: When `restrain_shape` returns an empty collection, it effectively means a "room" was completely invalidated by an obstacle, or the logic failed to produce a valid sub-room that still contains the required "contained shape".
- **Comparison with v1.9**: 
    - `IntOctagon` fields were renamed (e.g., `lx` -> `leftX`, `ly` -> `bottomY`).
    - The logic in `calc_outside_restrained_shape` and `calc_inside_restrained_shape` depends on these fields correctly mapping to the 8 boundary lines of the octagon.
    - Any discrepancy here would cause incorrect room clipping, potentially leading to empty shapes and the "No shapes returned" warning in `MazeSearchAlgo`.

### IntOctagon Refactoring
- **Significant Changes**: Use of `switch` expressions and renamed fields.
- **Potential Issue**: The mapping between `obstacle_line_no` (0-7) and the specific octagon fields (left, bottom, right, top, diagonals) must be perfectly consistent with the geometric definition. 
- **Status**: Under investigation. Comparing line-by-line mapping of octagon boundaries.

### Net item count discrepancy (+5V)
- **Finding**: Net `+5V` has **31 items** in the Current version vs **30 items** in v1.9.
- **Context**: This count comes from `board.connectable_item_count(netNo)`.
- **Incomplete Count**: Both versions report **25 incompletes** for `+5V`.
- **Implication**: There is 1 extra `Connectable` item belonging to net `+5V` in the current version.

### Item #15 (PL7) failing in Current
- **Finding**: Net `PL7` is the 15th item to be routed in both versions.
- **v1.9 Outcome**: Successfully routed (0 incompletes in summary).
- **Current Outcome**: Remains unrouted (1 incomplete in summary) despite `BatchAutorouter` logging `Routed: 15`.
- **Total Incompletes**: Current version has **184** (vs **183** in v1.9) after 15 items. Correcting for the +1 difference confirms `PL7` is the culprit.
- **Theory**: `autoroute_item` may be returning `ROUTED` without successfully committing the connection.

### Theory 10: DRC Incomplete Item Counter Bug (User Suggested)
- **Hypothesis**: The incomplete item counter in `DesignRulesChecker` might have a bug related to tolerance usage.
- **Scenario**: The router might successfully route a connection within a certain tolerance (e.g., wire slightly off-center from via), but the DRC check might be stricter or ignore this tolerance, failing to count it as connected.
- **Action**: Verify `DesignRulesChecker` and `NetIncompletes` logic. Check if tolerance is applied correctly when determining connectivity.


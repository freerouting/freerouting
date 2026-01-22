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
- **Action**: Change `defaultUndesiredDirectionTraceCost` in `RouterScoringSettings.java` from `2.5` to `1.0`.


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
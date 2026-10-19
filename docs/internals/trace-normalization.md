# Trace Normalisation & Routing-Loop Architecture

Key facts about how `PolylineTraceNormalization`, `BasicBoard.normalize_traces()`, and `AutorouteBatchLoop.run()` interact — established during the Issue 676 regression investigation.

## `PolylineTraceNormalization.normalize()` Depth Limit

`PolylineTraceNormalization.MAX_NORMALIZATION_DEPTH` (= 16) caps the recursion depth of trace normalization. **Exceeding the cap returns `false` (no further change at that depth level) instead of throwing** — an exception here used to be swallowed by a caller try-catch and surfaced only as:
> `WARNING The normalization of net 'GND' failed.`

`return false` is safe because the outer loop in `normalize_traces` treats it as "no change for this trace" and continues to the next trace, terminating normally. `normalize()` declares no checked exception.

## `PolylineTrace.combine_at_end()` Null Search-Tree Entries

`combine_at_end` contains two code paths:

1. **Full remove + re-insert** — used when `joined_polyline.arr.length != new_line_count` (parallel lines were skipped at the join).
2. **Optimised merge via `merge_entries_at_end`** — reuses existing search-tree leaf nodes for performance.

Path 2 calls `p_to_trace.get_search_tree_entries(tree)` and `p_from_trace.get_search_tree_entries(tree)`. Either can return `null` if the trace has no entries in the given search tree (e.g. it was freshly inserted or its entries were cleared). This caused a `NullPointerException` inside `ShapeSearchTree.merge_entries_at_end`, visible in the stack as:
```
NullPointerException: Cannot load from object array because "to_trace_entries" is null
 at ShapeSearchTree.merge_entries_at_end(ShapeSearchTree.java:237)
 at PolylineTrace.combine_at_end(PolylineTrace.java:395)
 at PolylineTrace.normalize(PolylineTrace.java:776)
```

**Fix applied (via `PolylineTraceSearchTreeAdapter.hasDefaultEntries`):** Before choosing path 2, `combine_at_end` checks whether both `this` and `other_trace` have entries in the default search tree (`board.search_tree_manager.get_default_tree()`). If either is null, it falls back to path 1 (the safe full-remove + re-insert).

> **Why the default tree is sufficient for the guard:** All traces are always inserted into the default tree. Compensated trees are built on top of it. If a trace is missing from the default tree it is definitionally not in any compensated tree either.

## `AutorouteBatchLoop` Same-Board-Hash Tracking

v1.9 maintained an `already_checked_board_hashes` set. Between passes it computed a board hash; if the same hash appeared twice, it stopped routing.

**Current state:** `AutorouteBatchLoop.run()` declares an `alreadyRoutedBoardHashes` `HashSet<String>`, but the same-hash early stop and hash insertions are **disabled (commented out)** — per-pass ripup budgets and random seeds change each pass, so an identical hash can still make progress later. Stagnation is instead handled actively by the `BoardHistory` restore check, which uses a strict `>` comparison so equally-scored boards do not trigger unbounded restores.

## `BoardStatistics` Legacy Normalized Score Division by Zero / NaN

`getMaximumScore()` returns `maximumCount * unroutedNetPenalty`. When a board is already fully routed (`maximumCount == 0`), this is 0, and dividing by it produces `NaN`. NaN failed all comparison-based stagnation checks, causing the score to always appear as 0.

**Fix applied:** A `if (maximumScore <= 0f) return 0f;` guard was added in `BoardStatistics.getLegacyNormalizedScore()` (while `getNormalizedScore()` now delegates to `getRouterScore()`).

## Symptom Summary

| Symptom | Root Cause | Fix Location |
|---|---|---|
| `WARNING The normalization of net 'GND' failed.` (every pass) | `PolylineTraceNormalization` hit the max depth and threw | `PolylineTraceNormalization` — throw -> debug + `return false` |
| `NullPointerException` in `merge_entries_at_end` | Trace missing search-tree entries, `combine_at_end` chose optimised path | `PolylineTrace.combine_at_end()` — `PolylineTraceSearchTreeAdapter.hasDefaultEntries` guard before path 2 |
| Score always 0 | `getLegacyNormalizedScore()` divided by 0 when board fully routed | `BoardStatistics.getLegacyNormalizedScore()` — `<= 0` guard (`getNormalizedScore()` delegates to `getRouterScore()`) |
| Router loops endlessly on same board | No board-hash stagnation detection | `AutorouteBatchLoop.run()` — same-hash early stop currently disabled; stagnation handled by the `BoardHistory` strict-`>` restore check |

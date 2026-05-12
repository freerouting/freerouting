# Issue #676 — `AutorouteSettings.get_layer_active: p_layer out of range [0..-1]` when starting autorouter from GUI

## Problem

When a LibrePCB DSN file (e.g. `Issue676-ch32v-tx118s.dsn`) is loaded into the GUI and the
**Start autorouter** button is clicked, the router immediately fails with a cascade of warnings
and errors:

```
WARNING AutorouteSettings.get_layer_active: p_layer=0 out of range [0..-1]
WARNING AutorouteSettings.get_layer_active: p_layer=1 out of range [0..-1]
ERROR   AutorouteEngine.autoroute_connection: Exception in MazeSearchAlgo.get_instance
```

The range `[0..-1]` reveals that `RouterSettings.getLayerCount()` returns **0** at the point when
`AutorouteControl` reads the per-layer settings, which means `isLayerActive` is `null`.

## Root Cause

### The `settingsMerger` in the GUI path never includes `DsnFileSettings`

The *prototype* settings merger created at application startup
(`Freerouting.java` line 753) contains four sources:
`DefaultSettings`, `JsonFileSettings`, `CliSettings`, `EnvironmentVariablesSource`.
**`DsnFileSettings` is intentionally omitted** from the prototype because it is
board-specific and is added only when the routing pipeline actually starts (in
`RoutingJobScheduler` / the headless path), or at GUI startup when a file is supplied
on the CLI (`GuiManager.java` lines 107–111).

When the user *loads a file from the GUI* (via File → Open), `DsnFileSettings` is
**never registered** in `board_handling.settingsMerger` (the clone held by
`GuiBoardManager` / `BoardFrame`).  Because of this, calling
`settingsMerger.merge()` returns a `RouterSettings` whose `isLayerActive` is `null`.

### `BoardToolbar.java` replaces `routerSettings` entirely

When the autorouter button is clicked, `BoardToolbar.java` (line 180 before the fix):

```java
guiRoutingJob.routerSettings = board_frame.board_panel.board_handling.settingsMerger.merge();
```

This **directly assigns** the returned `RouterSettings` object to `guiRoutingJob.routerSettings`,
**discarding** any previously populated `isLayerActive` / `isPreferredDirectionHorizontalOnLayer`
/ `scoring.preferredDirectionTraceCost` arrays that were correctly initialised during
`BoardFrame.loadDesignFile()`.

After this replacement `routerSettings.getLayerCount()` returns 0.  The subsequent
`AutorouteControl` constructor iterates `p_board.get_layer_count()` times (= 2), calls
`p_settings.get_layer_active(0)` and `get_layer_active(1)`, both of which hit the
range-check guard and log the `[0..-1]` warning.  Because `layer_active[]` remains all-false,
`MazeSearchAlgo.get_instance()` sees no usable layer and throws, producing the ERROR log.

### Why the board-load step does populate the arrays correctly

In `BoardFrame.loadDesignFile()` (executed when the DSN is opened):

```java
if (this.routingJob.routerSettings.isLayerActive == null ||
    this.routingJob.routerSettings.isLayerActive.length != boardLayerCount) {
  this.routingJob.routerSettings.setLayerCount(boardLayerCount);
  this.routingJob.routerSettings.applyBoardSpecificOptimizations(board);
}
// …
this.routingJob.setSettings(this.settingsMerger.merge());   // safe: uses applyNewValuesFrom
```

`setSettings()` calls `applyNewValuesFrom()` → `ReflectionUtil.copyFields()`.
Because the merged `isLayerActive` is `null` (no `DsnFileSettings` in the merger),
`copyFields` leaves the array untouched and `routerSettings.isLayerActive` stays
`[true, true]`.  The bug manifests only later when `BoardToolbar` replaces the
entire object.

## Fix

**`BoardToolbar.java`** — after obtaining the merged settings, call
`applyBoardSpecificOptimizations()` with the already-loaded board so that the layer
arrays are (re-)initialised regardless of which sources are registered in the merger:

```java
guiRoutingJob.routerSettings = board_frame.board_panel.board_handling.settingsMerger.merge();
// Re-apply board-specific optimisations to ensure layer arrays are populated (Issue #676).
app.freerouting.board.RoutingBoard routingBoard =
    board_frame.board_panel.board_handling.get_routing_board();
if (routingBoard != null) {
  guiRoutingJob.routerSettings.applyBoardSpecificOptimizations(routingBoard);
}
```

`applyBoardSpecificOptimizations()` already guards:
```java
if (isLayerActive == null || isLayerActive.length != layer_count) {
  isLayerActive = new boolean[layer_count];
}
```
so calling it unconditionally is safe and idempotent.

## Status

- ✅ Root cause identified
- ✅ Fix applied in `BoardToolbar.java`
- ✅ Fixture file `Issue676-ch32v-tx118s.dsn` added to `fixtures/`

## Acceptance Criteria

1. Loading `Issue676-ch32v-tx118s.dsn` in the GUI and clicking the Start autorouter button
   produces no `AutorouteSettings.get_layer_active: p_layer out of range [0..-1]` warnings.
2. No `AutorouteEngine.autoroute_connection: Exception in MazeSearchAlgo.get_instance` errors.
3. The router runs to completion (or stops cleanly when the user requests it) for this 2-layer board.
4. All existing routing fixture tests continue to pass.
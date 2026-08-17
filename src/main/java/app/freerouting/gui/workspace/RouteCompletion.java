package app.freerouting.gui.workspace;

/** Immutable terminal event for a background routing operation. */
public record RouteCompletion(
    RunGeneration generation,
    boolean cancelled,
    String statusMessage,
    int incompleteCount,
    boolean restoreRatsNest,
    boolean refreshWindows) {}

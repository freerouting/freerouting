package app.freerouting.gui.workspace;

/** Immutable copy of the counters needed by the batch progress presentation. */
public record BatchProgress(
    String phase,
    int queuedToBeRoutedCount,
    int routedCount,
    int failedToBeRoutedCount,
    int rippedCount,
    Integer fanoutExtraViasCount) {}

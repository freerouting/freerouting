package app.freerouting.gui.workspace.progress;

import app.freerouting.board.model.structure.Unit;

/**
 * Snapshot of board metrics and autorouting results captured upon job completion.
 *
 * @param totalNets total number of nets on the board
 * @param incompleteCount number of unrouted connections remaining
 * @param violationsCount number of clearance violations
 * @param viaCount total number of vias on the board
 * @param totalTraceLength total length of all traces
 * @param displayUnit active measurement unit
 * @param durationSeconds elapsed routing and optimization time in seconds
 * @param wasInterrupted true if the user cancelled or stopped routing early
 */
public record RoutingSummaryData(
    int totalNets,
    int incompleteCount,
    int violationsCount,
    int viaCount,
    double totalTraceLength,
    Unit displayUnit,
    double durationSeconds,
    boolean wasInterrupted) {}

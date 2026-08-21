package app.freerouting.gui.workspace.progress;

import app.freerouting.board.model.structure.Unit;

/**
 * Snapshot of board metrics and autorouting results captured upon job completion.
 *
 * @param totalNets total number of nets on the board
 * @param incompleteCount number of unrouted connections remaining
 * @param violationsCount number of clearance violations
 * @param maxViolation maximum clearance violation distance in display units
 * @param viaCount total number of vias on the board
 * @param totalTraceLength total length of all traces on the board in display units
 * @param displayUnit active measurement unit
 * @param durationSeconds elapsed routing and optimization time in seconds
 * @param score normalized board score from 0 to 1000
 * @param wasInterrupted true if the user cancelled or stopped routing early
 */
public record RoutingSummaryData(
    int totalNets,
    int incompleteCount,
    int violationsCount,
    double maxViolation,
    int viaCount,
    double totalTraceLength,
    Unit displayUnit,
    double durationSeconds,
    float score,
    boolean wasInterrupted) {}

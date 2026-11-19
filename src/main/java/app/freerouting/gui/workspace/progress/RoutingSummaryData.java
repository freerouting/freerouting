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
 * @param decoupling decoupling capacitor pre-routing summary, or {@code null} if not run
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
    boolean wasInterrupted,
    DecouplingSummaryItem decoupling) {

  /** Telemetry record for decoupling capacitor pre-routing metrics. */
  public record DecouplingSummaryItem(
      int candidatesTotal,
      int strapsRouted,
      int alreadyConnected,
      int viasPlaced,
      int coupledViaPairs,
      int fallbackViaPairs,
      int failed,
      double totalStrapLengthMm) {}

  /** Backwards-compatible constructor for jobs without decoupling metrics. */
  public RoutingSummaryData(
      int totalNets,
      int incompleteCount,
      int violationsCount,
      double maxViolation,
      int viaCount,
      double totalTraceLength,
      Unit displayUnit,
      double durationSeconds,
      float score,
      boolean wasInterrupted) {
    this(
        totalNets,
        incompleteCount,
        violationsCount,
        maxViolation,
        viaCount,
        totalTraceLength,
        displayUnit,
        durationSeconds,
        score,
        wasInterrupted,
        null);
  }
}

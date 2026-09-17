package app.freerouting.autoroute;

/**
 * Captures the transient reason why MazeSearchEngine failed to find a route. This is attached to
 * {@link AutorouteAttemptResult} when a connection attempt ends in {@link
 * AutorouteAttemptState#FAILED} so that the final {@link AutorouteUnroutedReport} can show per-net,
 * deduplicated failure diagnostics instead of a generic "no connection found".
 *
 * <p>This record is immutable and short-lived: it is allocated only at give-up time (not per
 * expansion step) so it does not add GC pressure inside the maze search tight loop.
 */
public record FailureReason(FailureType type, String description) {
  /**
   * The coarse category of failure. Future contributors can add new constants here without changing
   * the plumbing that threads {@link FailureReason} up to the unrouted report.
   */
  public enum FailureType {
    /**
     * The expansion list was exhausted and no DRC-legal path to the destination exists. This is the
     * most common cause: clearance walls, missing via paths, or fully blocked routing layers.
     */
    CLEARANCE_WALKED_EXHAUSTED,

    /**
     * The search stopped because the routing thread requested a stop (time limit, user cancel, or
     * global timeout) while the expansion was still in progress.
     */
    TIME_LIMIT_EXCEEDED,

    /**
     * {@link MazeSearchEngine#getInstance} could not create the search engine (e.g. because the
     * start/destination sets had no valid tree shapes, or an exception was thrown during init).
     */
    INITIALIZATION_FAILED,

    /**
     * Destination items were supplied but none of them had a usable tree shape on an active layer,
     * so the maze had nothing to aim at.
     */
    NO_VALID_DESTINATION_ITEMS
  }
}

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
    NO_VALID_DESTINATION_ITEMS,

    /**
     * The maze search could not take even a first step: the start items produced no accessible
     * expansion doors, i.e. the start pin or pad is fully encapsulated by keepouts, adjacent pads,
     * or traces, and no escape route into free space exists.
     */
    START_PIN_ESCAPE_FAILED,

    /**
     * Via placement was attempted during the search, but every candidate via location failed the
     * DRC or via-mask checks, so not a single via expansion element was ever accepted.
     */
    VIA_PLACEMENT_BLOCKED,

    /**
     * The router hit its maximum allowed rip-up/shove retry depth. It did not run out of wall-clock
     * time, but it detected a cyclic conflict where traces were endlessly shoving each other back
     * and forth.
     */
    MAX_RIPUP_DEPTH_REACHED,

    /**
     * The net's routing layer restrictions conflict with the physical board. For example, the net
     * class forces routing on the "Top" layer, but the destination is a surface-mount pad on the
     * "Bottom" layer with no via allowed.
     */
    LAYER_RESTRICTION_CONFLICT,

    /**
     * The maze search found a connection, but it could not be backtracked into concrete board items
     * and inserted (internal locator or inserter failure).
     */
    CONNECTION_INSERTION_FAILED,

    /**
     * The connection was inserted, but strict DRC enforcement found clearance violations among the
     * newly inserted items, so the whole connection was ripped and the attempt counts as failed.
     */
    STRICT_DRC_REJECTED,

    /**
     * An unexpected exception aborted the routing attempt before a normal give-up condition was
     * reached. The exception itself is logged with an error-level stack trace.
     */
    UNEXPECTED_EXCEPTION
  }
}

package app.freerouting.core.facade;

import app.freerouting.core.RoutingStage;
import app.freerouting.core.scoring.BoardStatistics;

/**
 * Snapshot of routing progress emitted to {@link HeadlessRoutingProgressListener}.
 *
 * @param stage the current routing stage (e.g. ROUTING, OPTIMIZATION, IDLE)
 * @param passNumber current pass number within the stage (1-based)
 * @param unroutedCount count of unrouted connections remaining
 * @param statistics latest board statistics snapshot, or {@code null} if not yet computed
 * @param message informative status or description of the latest event
 */
public record HeadlessRoutingProgress(
    RoutingStage stage,
    int passNumber,
    int unroutedCount,
    BoardStatistics statistics,
    String message) {}

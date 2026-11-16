package app.freerouting.core.facade;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.drc.ClearanceViolation;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome of a headless routing execution performed by {@link FreeroutingHeadlessEngine}.
 *
 * @param completed {@code true} if routing succeeded without cancellation, fatal errors, unrouted
 *     nets, or DRC clearance violations
 * @param cancelled {@code true} if routing was stopped early by a {@link CancellationToken}
 * @param unroutedConnections number of unrouted connections remaining
 * @param clearanceViolations authoritative clearance violations from design rules check
 * @param statistics final board statistics, or {@code null} if parsing failed
 * @param sesBytes generated Specctra SES session file bytes (empty if disabled or aborted)
 * @param executionTimeMillis elapsed wall-clock time in milliseconds
 * @param errorMessage error description if the operation failed, or {@code null}
 * @param board in-memory {@link RoutingBoard} instance, or {@code null} if parsing failed
 */
public record HeadlessRoutingResult(
    boolean completed,
    boolean cancelled,
    int unroutedConnections,
    Collection<ClearanceViolation> clearanceViolations,
    BoardStatistics statistics,
    byte[] sesBytes,
    long executionTimeMillis,
    String errorMessage,
    RoutingBoard board) {

  public HeadlessRoutingResult {
    clearanceViolations =
        clearanceViolations != null ? List.copyOf(clearanceViolations) : List.of();
    sesBytes = sesBytes != null ? sesBytes : new byte[0];
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof HeadlessRoutingResult that)) {
      return false;
    }
    return completed == that.completed
        && cancelled == that.cancelled
        && unroutedConnections == that.unroutedConnections
        && executionTimeMillis == that.executionTimeMillis
        && Objects.equals(clearanceViolations, that.clearanceViolations)
        && Objects.equals(statistics, that.statistics)
        && Arrays.equals(sesBytes, that.sesBytes)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(board, that.board);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            completed,
            cancelled,
            unroutedConnections,
            clearanceViolations,
            statistics,
            executionTimeMillis,
            errorMessage,
            board);
    result = 31 * result + Arrays.hashCode(sesBytes);
    return result;
  }

  /** Returns {@code true} if the request completed without a fatal error or cancellation. */
  public boolean isSuccess() {
    return errorMessage == null && !cancelled;
  }

  /** Returns {@code true} if all nets were routed and 0 DRC violations exist. */
  public boolean isCompleted() {
    return completed;
  }

  /** Returns {@code true} if the execution was stopped by cancellation. */
  public boolean isCancelled() {
    return cancelled;
  }

  /** Returns {@code true} if there are zero DRC clearance violations. */
  public boolean isDrcClean() {
    return clearanceViolations.isEmpty();
  }

  /** Returns the generated Specctra SES file content as a UTF-8 string. */
  public String sesContent() {
    return new String(sesBytes, StandardCharsets.UTF_8);
  }

  /** Creates a failed result with an error message and timing information. */
  public static HeadlessRoutingResult failure(String errorMessage, long executionTimeMillis) {
    return new HeadlessRoutingResult(
        false,
        false,
        -1,
        Collections.emptyList(),
        null,
        new byte[0],
        executionTimeMillis,
        errorMessage,
        null);
  }

  /** Creates a cancelled result with the partial board state and timing information. */
  public static HeadlessRoutingResult cancelled(
      RoutingBoard board, BoardStatistics stats, long executionTimeMillis) {
    int unrouted =
        stats != null && stats.connections != null ? stats.connections.incompleteCount : -1;
    return new HeadlessRoutingResult(
        false,
        true,
        unrouted,
        Collections.emptyList(),
        stats,
        new byte[0],
        executionTimeMillis,
        "Routing was cancelled",
        board);
  }
}

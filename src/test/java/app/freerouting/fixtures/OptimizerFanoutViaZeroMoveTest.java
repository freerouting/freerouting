package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.optimize.ViaOptimizer;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.Point;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.sources.TestingSettings;
import java.util.Collection;
import org.junit.jupiter.api.Test;

/**
 * Regression test for a fanout via that is reported as moved while it stays in place.
 *
 * <p>The via ends a trace of net N1 and has no other contact. {@code
 * ViaOptimizer.optPlaneOrFanoutVia} moves it along the trace towards the next corner, as far as the
 * pad of net N2 beside the trace allows. On the next call the remaining free length is below half a
 * board unit: the new location rounds back to the current one, the via is "moved" by a zero vector,
 * and the call returns {@code true}. {@code TraceTightener.optChangedArea} takes that as a change
 * and runs its loop again, up to {@code MAX_ROUNDS} (1000), and each zero move logs "Polyline: must
 * contain at least 2 different points" once. A call that returns {@code true} must have moved the
 * via.
 */
class OptimizerFanoutViaZeroMoveTest extends RoutingFixtureTest {

  private static final String DSN = "Optimizer-fanout-via-zero-move.dsn";

  /** Far more calls than the via needs to settle; before the fix every call returned true. */
  private static final int MAX_CALLS = 10;

  @Test
  void fanoutViaIsNotReportedAsMovedWhenItStaysInPlace() {
    RoutingJob job = getRoutingJob(DSN, new TestingSettings());
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    RoutingBoard board = boardManager.getRoutingBoard();
    assertEquals(
        0, board.getStatistics().clearanceViolations.totalCount, "clearance violations before");

    int calls = 0;
    boolean changed = true;
    while (changed && calls < MAX_CALLS) {
      Via via = onlyVia(board);
      Point before = via.getCenter();
      changed = ViaOptimizer.optViaLocation(board, via, null, 1, 10);
      ++calls;
      if (changed) {
        assertNotEquals(
            before, onlyVia(board).getCenter(), "call " + calls + " reported a move of zero");
      }
    }
    assertFalse(changed, "the via did not settle within " + MAX_CALLS + " calls");
    assertEquals(
        0, board.getStatistics().clearanceViolations.totalCount, "clearance violations after");
  }

  private static Via onlyVia(RoutingBoard board) {
    Collection<Via> vias = board.getVias();
    assertEquals(1, vias.size(), "via count");
    return vias.iterator().next();
  }
}

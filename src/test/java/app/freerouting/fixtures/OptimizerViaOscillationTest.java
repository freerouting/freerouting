package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.sources.TestingSettings;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Regression test for a via that never settles in the optimizer.
 *
 * <p>The garland holds one net that changes layer through a via placed the way KiCad exports it,
 * with a short stub on each layer. The Top stub is not exactly at 45 degrees (135 x 134.8 um), and
 * a trace of another net runs on Bottom next to the via. {@code ViaOptimizer.optViaLocation} moves
 * the via from A to B expecting the Top trace to be pulled tight from its next corner straight to
 * B, but that segment is not at a multiple of 45 degrees: the trace keeps its corner at A, and the
 * next call moves the via back to A. Each move is reported as a change, so {@code
 * TraceTightener.optChangedArea} ran its rounds up to {@code MAX_ROUNDS} (1000): about 30 s for
 * this three-trace board, and minutes per item on a dense board. Once the via has come back twice
 * to a location it already held in the loop, it is not moved there again, so the pass takes well
 * under a second.
 */
class OptimizerViaOscillationTest extends RoutingFixtureTest {

  private static final String DSN = "Optimizer-via-oscillation-garland.dsn";

  @Test
  void viaThatOscillatesDoesNotStallTheOptimizerPass() {
    RoutingJob job = getRoutingJob(DSN, new TestingSettings());
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    job.board = boardManager.getRoutingBoard();
    job.thread = new NoOpStoppableThread();
    job.routerSettings.optimizer.maxThreads = 1;
    job.routerSettings.optimizer.maxPasses = 1;
    // The board is already near its minimum length: the guards would skip the pass.
    job.routerSettings.optimizer.enablePreflightGuards = false;

    BoardStatistics before = job.board.getStatistics();

    // Before the fix this pass takes about 30 s.
    try {
      assertTimeoutPreemptively(
          Duration.ofSeconds(10), () -> BatchOptimizer.create(job).runBatchLoop());
    } finally {
      job.thread.requestStop();
    }

    BoardStatistics after = job.board.getStatistics();
    assertEquals(0, after.connections.incompleteCount, "incomplete connections");
    assertEquals(
        before.clearanceViolations.totalCount,
        after.clearanceViolations.totalCount,
        "clearance violations");
    assertEquals(before.items.viaCount, after.items.viaCount, "via count");
  }

  private static final class NoOpStoppableThread extends StoppableThread {
    @Override
    protected void threadAction() {}

    @Override
    public synchronized boolean isStopRequested() {
      return super.isStopRequested() || Thread.currentThread().isInterrupted();
    }
  }
}

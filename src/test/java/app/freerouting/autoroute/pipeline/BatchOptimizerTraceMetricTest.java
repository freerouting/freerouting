package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemRouteResult;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

/** Regression tests for the optimizer's worker trace-length comparison (Issue #811). */
class BatchOptimizerTraceMetricTest {

  private static final String FIXTURE = "Issue026-J2_reference.dsn";

  @Test
  void workerUsesTheWeightedTraceMetricFromItsBoardSnapshot() throws Exception {
    RoutingJob job = routedFixtureJob();
    BatchOptimizer optimizer = new BatchOptimizer(job);
    Trace trace =
        job.board.getTraces().stream()
            .filter(item -> !item.isUserFixed())
            .min(Comparator.comparingInt(Trace::getId))
            .orElseThrow(() -> new AssertionError("Fixture must contain an unfixed trace"));

    double snapshotWeightedTraceLength = weightedTraceLength(job.board);
    assertTrue(snapshotWeightedTraceLength > 0, "Worker snapshot must contain existing traces");

    ItemRouteResult result = optimizer.optRouteItem(trace, false);
    assertNotNull(result, "Worker must produce a route result");

    double reconstructedBefore = result.lengthReduced() + result.traceLength();
    double workerAfter = weightedTraceLength(job.board);
    assertEquals(
        snapshotWeightedTraceLength,
        reconstructedBefore,
        0.001,
        "Worker before metric must come from its own board snapshot");
    assertEquals(
        workerAfter,
        result.traceLength(),
        0.001,
        "Worker before/after values must use the same weighted metric");
  }

  @Test
  void traceOnlyImprovementIsAcceptedWhenCanonicalMetricDecreases() {
    ItemRouteResult result = new ItemRouteResult(1, 2, 2, 100.0, 90.0, 0, 0);

    assertTrue(result.improved(), "A trace-only reduction must be treated as an improvement");
    assertEquals(10.0, result.lengthReduced(), 0.001);
  }

  private static double weightedTraceLength(RoutingBoard board) {
    return new BoardStatistics(board, null, false).traces.totalWeightedLength;
  }

  private static RoutingJob routedFixtureJob() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(FIXTURE);
    RoutingJob job = new RoutingJob();
    job.board = board;
    job.thread = new TestStoppableThread();

    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.autorouter.maxPasses = 1;
    settings.autorouter.maxItems = Integer.MAX_VALUE;
    settings.fanout.enabled = false;
    settings.optimizer.enabled = true;
    settings.optimizer.maxThreads = 1;
    settings.optimizer.boardUpdateStrategy = BoardUpdateStrategy.GLOBAL_OPTIMAL;
    settings.optimizer.maxAutoroutePasses = 1;
    job.routerSettings = settings;

    new BatchAutorouter(job).runBatchLoop();
    return job;
  }

  private static final class TestStoppableThread extends StoppableThread {

    @Override
    protected void threadAction() {
      // The test invokes the algorithm directly; no thread body is required.
    }
  }
}

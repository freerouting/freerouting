package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/** Verifies cross-thread determinism and baseline board preservation for BatchOptimizer. */
class BatchOptimizerDeterminismTest extends RoutingFixtureTest {

  private static TestingSettings createBaseTestingSettings() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(1);
    settings.setMaxItems(2);
    settings.setJobTimeoutString("00:01:00");
    return settings;
  }

  private static void loadBoard(RoutingJob job) {
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    job.board = boardManager.getRoutingBoard();
    job.thread = new NoOpStoppableThread();
  }

  @Test
  void crossThreadOptimizationProducesIdenticalResults() {
    RoutingJob setupJob = getRoutingJob("Issue508-DAC2020_bm01.dsn", createBaseTestingSettings());
    loadBoard(setupJob);

    // Run fanout + autoroute so that the board has routed connections to optimize
    setupJob.routerSettings.maxThreads = 1;
    new BatchAutorouter(setupJob).runBatchLoop();
    setupJob.board.finishAutoroute();

    RoutingBoard preOptimizedBoard = setupJob.board.deepCopy();

    // Run optimizer with 1 thread
    RoutingJob job1 = getRoutingJob("Issue508-DAC2020_bm01.dsn", createBaseTestingSettings());
    loadBoard(job1);
    job1.board = preOptimizedBoard.deepCopy();
    job1.routerSettings.optimizer.maxThreads = 1;
    job1.routerSettings.optimizer.maxPasses = 1;
    job1.routerSettings.optimizer.maxItems = 6;
    BatchOptimizer optimizer1 = BatchOptimizer.create(job1);
    optimizer1.runBatchLoop();

    // Run optimizer with 4 threads on an identical board clone
    RoutingJob job2 = getRoutingJob("Issue508-DAC2020_bm01.dsn", createBaseTestingSettings());
    loadBoard(job2);
    job2.board = preOptimizedBoard.deepCopy();
    job2.routerSettings.optimizer.maxThreads = 4;
    job2.routerSettings.optimizer.maxPasses = 1;
    job2.routerSettings.optimizer.maxItems = 6;
    BatchOptimizer optimizer2 = BatchOptimizer.create(job2);
    optimizer2.runBatchLoop();

    BoardStatistics stats1 = job1.board.getStatistics();
    BoardStatistics stats2 = job2.board.getStatistics();

    assertEquals(job1.board.getHash(), job2.board.getHash());
    assertEquals(stats1.connections.incompleteCount, stats2.connections.incompleteCount);
    assertEquals(stats1.items.viaCount, stats2.items.viaCount);
    assertEquals(stats1.traces.totalLength, stats2.traces.totalLength, 0.001);
    assertEquals(stats1.clearanceViolations.totalCount, stats2.clearanceViolations.totalCount);
  }

  @Test
  void initialBoardStatePreservedWhenOptimizationDoesNotImprove() {
    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn", createBaseTestingSettings());
    loadBoard(job);

    // Route initially so the board has routed connections
    job.routerSettings.maxThreads = 1;
    new BatchAutorouter(job).runBatchLoop();
    job.board.finishAutoroute();

    BoardStatistics statsBefore = job.board.getStatistics();
    String initialHash = job.board.getHash();
    float initialScore = statsBefore.getNormalizedScore(job.routerSettings.scoring);

    // Configure optimizer with 0 passes so no items can improve
    job.routerSettings.optimizer.maxPasses = 0;
    BatchOptimizer optimizer = BatchOptimizer.create(job);
    optimizer.runBatchLoop();

    BoardStatistics statsAfter = job.board.getStatistics();
    float finalScore = statsAfter.getNormalizedScore(job.routerSettings.scoring);

    assertEquals(initialScore, finalScore, 0.001);
    assertEquals(initialHash, job.board.getHash());
    assertEquals(statsBefore.connections.incompleteCount, statsAfter.connections.incompleteCount);
    assertEquals(statsBefore.items.viaCount, statsAfter.items.viaCount);
  }

  private static final class NoOpStoppableThread extends StoppableThread {
    @Override
    protected void threadAction() {}
  }
}

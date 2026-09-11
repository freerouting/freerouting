package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.results.RoutingResultManifest;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.core.scoring.BoardStatisticsBounds;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.sources.TestingSettings;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link BatchOptimizer} pre-flight guards correctly identify un-improvable boards
 * and bypass the expensive optimization passes.
 */
class BatchOptimizerPreflightGuardsTest extends RoutingFixtureTest {

  private static final String DSN_BM08 = "Issue508-DAC2020_bm08.dsn";
  private static final String SES_BM08 = "Issue508-DAC2020_bm08-routed.ses";

  private RoutingBoard fullyRoutedBm08;

  @BeforeEach
  @Override
  protected void setUp() {
    super.setUp();
    fullyRoutedBm08 = loadRoutedBm08();
  }

  private RoutingBoard loadRoutedBm08() {
    TestingSettings settings = new TestingSettings();
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
      RoutingBoard board = boardManager.getRoutingBoard();
      InputStream sesStream =
          app.freerouting.TestFixtures.resolvePath(SES_BM08).toUri().toURL().openStream();
      SesReader.read(sesStream, board);
      board.finishAutoroute();
      return board;
    } catch (Exception e) {
      throw new RuntimeException("Failed to load test board", e);
    }
  }

  @Test
  void guard1BypassesOptimizerWhenBoardHasIncompletes() {
    TestingSettings settings = new TestingSettings();
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    job.board = fullyRoutedBm08.deepCopy();
    job.thread = new NoOpStoppableThread();

    BatchOptimizer optimizer = BatchOptimizer.create(job);
    BoardStatistics stats = new BoardStatistics();
    stats.connections.incompleteCount = 5;
    stats.vias.totalCount = 10;

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNotNull(bypassReason);
    assertTrue(bypassReason.contains("incomplete connection(s)"));
  }

  @Test
  void guard2BypassesOptimizerWhenBoardHasZeroVias() {
    TestingSettings settings = new TestingSettings();
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    job.board = fullyRoutedBm08.deepCopy();
    job.thread = new NoOpStoppableThread();

    BatchOptimizer optimizer = BatchOptimizer.create(job);
    BoardStatistics stats = new BoardStatistics();
    stats.connections.incompleteCount = 0;
    stats.vias.totalCount = 0;

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNotNull(bypassReason);
    assertEquals("board has 0 vias", bypassReason);
  }

  @Test
  void guard3BypassesOptimizerWhenScoreAtOrAboveCeiling() {
    TestingSettings settings = new TestingSettings();
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    job.board = fullyRoutedBm08.deepCopy();
    job.thread = new NoOpStoppableThread();

    BatchOptimizer optimizer = BatchOptimizer.create(job);
    BoardStatistics stats =
        new BoardStatistics() {
          @Override
          public float getOptimizerScore(app.freerouting.settings.RouterSettings routerSettings) {
            return 996.5f;
          }
        };
    stats.connections.incompleteCount = 0;
    stats.vias.totalCount = 10;

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNotNull(bypassReason);
    assertTrue(bypassReason.contains("995.00"));
  }

  @Test
  void guard3BypassesOptimizerWhenTraceLengthWithin2PercentOfTheoreticalMin() {
    TestingSettings settings = new TestingSettings();
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    job.board = fullyRoutedBm08.deepCopy();
    job.thread = new NoOpStoppableThread();

    BatchOptimizer optimizer = BatchOptimizer.create(job);
    BoardStatistics stats =
        new BoardStatistics() {
          @Override
          public float getOptimizerScore(app.freerouting.settings.RouterSettings routerSettings) {
            return 900.0f;
          }
        };
    stats.connections.incompleteCount = 0;
    stats.vias.totalCount = 10;
    stats.bounds = new BoardStatisticsBounds();
    stats.bounds.minTraceLengthMm = 100.0f;
    stats.traces.totalLengthMm = 101.5f; // within 1.02 * 100.0 = 102.0

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNotNull(bypassReason);
    assertTrue(bypassReason.contains("theoretical minimum"));
  }

  @Test
  void guardsCanBeDisabledViaSettings() {
    TestingSettings settings = new TestingSettings();
    settings.setOptimizerEnablePreflightGuards(false);
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    job.board = fullyRoutedBm08.deepCopy();
    job.thread = new NoOpStoppableThread();

    BatchOptimizer optimizer = BatchOptimizer.create(job);
    BoardStatistics stats = new BoardStatistics();
    stats.connections.incompleteCount = 5;
    stats.vias.totalCount = 0;

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNull(bypassReason);
  }

  @Test
  void runBatchLoopSetsManifestSnapshotsAndZeroPassesWhenBypassed() {
    TestingSettings settings = new TestingSettings();
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    job.board = fullyRoutedBm08.deepCopy(); // has 0 vias
    job.thread = new NoOpStoppableThread();

    BatchOptimizer optimizer = BatchOptimizer.create(job);
    optimizer.runBatchLoop();

    RoutingResultManifest.PhaseDetail phase = job.resultPhaseMetrics.optimizer;
    assertNotNull(phase.before);
    assertNotNull(phase.after);
    assertEquals(0, phase.passesCompleted);
    assertEquals(0.0f, phase.durationSeconds, 0.001f);
  }

  private static final class NoOpStoppableThread extends StoppableThread {
    @Override
    protected void threadAction() {}
  }
}

package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.BoardUpdateStrategy;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.sources.TestingSettings;
import java.io.InputStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parameterized tests for {@link BatchOptimizer} strategy and thread-count combinations.
 *
 * <p>Strategy: use the fully-routed DAC2020 bm08 board (DSN + SES) so that the optimizer starts
 * from a board with 0 unrouted connections and 0 clearance violations. Only the optimizer stage
 * runs; fanout and auto-router are intentionally bypassed. This focuses test time on the optimizer
 * and avoids redundant routing work in CI.
 *
 * <p>Combinations are chosen to probe edge cases, not to exhaustively try every permutation:
 *
 * <ul>
 *   <li>SEQUENTIAL x 1 thread — baseline single-threaded path
 *   <li>SEQUENTIAL x 4 threads — parallel evaluation with sequential ordering
 *   <li>SEQUENTIAL x 2 threads — asymmetric thread count (exposes off-by-one chunk boundaries)
 *   <li>PRIORITIZED x 1 thread — priority-queue ordering, single-threaded
 *   <li>PRIORITIZED x 4 threads — priority-queue + parallel — most likely to diverge
 * </ul>
 *
 * <p>Each test asserts:
 *
 * <ol>
 *   <li>The optimizer introduces no new clearance violations.
 *   <li>The optimizer introduces no new unrouted connections.
 *   <li>The final board score is at least as good as the pre-optimization score.
 * </ol>
 */
class BatchOptimizerStrategyTest extends RoutingFixtureTest {

  private static final String DSN_FIXTURE = "Issue508-DAC2020_bm08.dsn";
  private static final String SES_FIXTURE = "Issue508-DAC2020_bm08-routed.ses";

  private RoutingBoard preOptimizedBoard;
  private BoardStatistics preOptimizationStats;

  @BeforeEach
  @Override
  protected void setUp() {
    super.setUp();
    preOptimizedBoard = loadBm08Board();
    preOptimizationStats = new BoardStatistics(preOptimizedBoard);
  }

  // ---------------------------------------------------------------------------
  // Parameterized test cases
  // ---------------------------------------------------------------------------

  /**
   * Returns (ItemSelectionStrategy, maxThreads) combinations under test. Chosen to cover ordering
   * and concurrency boundaries without exhaustively testing all permutations.
   */
  static Stream<Arguments> optimizerVariants() {
    return Stream.of(
        // SEQUENTIAL x 1: baseline deterministic single-threaded path
        Arguments.of(Named.of("SEQUENTIAL x 1 thread", ItemSelectionStrategy.SEQUENTIAL), 1),
        // SEQUENTIAL x 4: same ordering but parallel candidates
        Arguments.of(Named.of("SEQUENTIAL x 4 threads", ItemSelectionStrategy.SEQUENTIAL), 4),
        // SEQUENTIAL x 2: asymmetric thread count, exposes off-by-one chunk boundaries
        Arguments.of(Named.of("SEQUENTIAL x 2 threads", ItemSelectionStrategy.SEQUENTIAL), 2),
        // PRIORITIZED x 1: priority-queue ordering, single-threaded
        Arguments.of(Named.of("PRIORITIZED x 1 thread", ItemSelectionStrategy.PRIORITIZED), 1),
        // PRIORITIZED x 4: priority-queue + parallel, most likely to diverge from SEQUENTIAL
        Arguments.of(Named.of("PRIORITIZED x 4 threads", ItemSelectionStrategy.PRIORITIZED), 4));
  }

  /**
   * Verifies that the optimizer does not introduce clearance violations or unrouted connections,
   * and that the resulting board score is at least as good as the pre-optimization score.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("optimizerVariants")
  void optimizerDoesNotDegradeFullyRoutedBoard(
      ItemSelectionStrategy selectionStrategy, int maxThreads) {
    // Set up an isolated job on a clone of the shared pre-routed board
    RoutingJob job = getRoutingJob(DSN_FIXTURE, createOptimizerTestingSettings());
    job.board = preOptimizedBoard.deepCopy();
    job.thread = new NoOpStoppableThread();

    // Configure optimizer settings
    job.routerSettings.optimizer.maxThreads = maxThreads;
    job.routerSettings.optimizer.maxPasses = 2;
    job.routerSettings.optimizer.itemSelectionStrategy = selectionStrategy;
    job.routerSettings.optimizer.boardUpdateStrategy = BoardUpdateStrategy.GLOBAL_OPTIMAL;
    // Bound item count to keep each variant fast enough for CI
    job.routerSettings.optimizer.maxItems = 50;
    job.routerSettings.optimizer.enabled = true;

    // Capture pre-optimization score (final so Checkstyle is satisfied)
    final float scoreBeforeOptimization =
        preOptimizationStats.getNormalizedScore(job.routerSettings.scoring);

    // Run optimizer only (no fanout, no autorouter)
    BatchOptimizer optimizer = BatchOptimizer.create(job);
    optimizer.runBatchLoop();

    // --- Assertions ---
    BoardStatistics statsAfter = new BoardStatistics(job.board);
    float scoreAfterOptimization = statsAfter.getNormalizedScore(job.routerSettings.scoring);

    // (1) No new unrouted connections
    assertEquals(
        0,
        statsAfter.connections.incompleteCount,
        String.format(
            "[%s x %d threads] Optimizer must not introduce unrouted connections. After: %d",
            selectionStrategy, maxThreads, statsAfter.connections.incompleteCount));

    // (2) No new clearance violations
    assertEquals(
        0,
        statsAfter.clearanceViolations.totalCount,
        String.format(
            "[%s x %d threads] Optimizer must not introduce clearance violations. After: %d",
            selectionStrategy, maxThreads, statsAfter.clearanceViolations.totalCount));

    // (3) Score must not regress (bestBoard restore guarantees this)
    assertTrue(
        scoreAfterOptimization >= scoreBeforeOptimization - 0.001f,
        String.format(
            "[%s x %d threads] Optimizer must not degrade score. Before: %.4f, After: %.4f",
            selectionStrategy, maxThreads, scoreBeforeOptimization, scoreAfterOptimization));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Loads the bm08 design from DSN and imports the companion SES to arrive at a fully-routed board.
   * The board has 0 unrouted connections and 0 clearance violations after this setup.
   */
  private RoutingBoard loadBm08Board() {
    RoutingJob job = getRoutingJob(DSN_FIXTURE, createOptimizerTestingSettings());
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board: " + DSN_FIXTURE, e);
    }

    RoutingBoard board = boardManager.getRoutingBoard();
    job.board = board;

    // Import the routed session to reach the fully-routed state
    try {
      InputStream sesStream =
          app.freerouting.TestFixtures.resolvePath(SES_FIXTURE).toUri().toURL().openStream();
      SesReader.read(sesStream, board);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load SES file: " + SES_FIXTURE, e);
    }

    board.finishAutoroute();
    return board;
  }

  private static TestingSettings createOptimizerTestingSettings() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:02:00");
    settings.setMaxPasses(1);
    return settings;
  }

  private static final class NoOpStoppableThread extends StoppableThread {
    @Override
    protected void threadAction() {}
  }
}

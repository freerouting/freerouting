package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.results.RoutingResultManifest;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.core.scoring.BoardStatisticsBounds;
import app.freerouting.io.specctra.SesReader;
import app.freerouting.management.HeadlessBoardManager;
import app.freerouting.settings.sources.TestingSettings;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
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
    assertTrue(bypassReason.contains("unrouted connection(s)"));
  }

  @Test
  void guard2BypassesOptimizerWhenBoardHasZeroViasAndHighOptimizerScore() {
    TestingSettings settings = new TestingSettings();
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    job.board = fullyRoutedBm08.deepCopy();
    job.thread = new NoOpStoppableThread();

    BatchOptimizer optimizer = BatchOptimizer.create(job);
    BoardStatistics stats =
        new BoardStatistics() {
          @Override
          public float getOptimizerScore(app.freerouting.settings.RouterSettings routerSettings) {
            return 960.0f;
          }
        };
    stats.connections.incompleteCount = 0;
    stats.vias.totalCount = 0;

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNotNull(bypassReason);
    assertTrue(bypassReason.contains("no vias to eliminate"));
    assertTrue(bypassReason.contains("950.00"));
  }

  @Test
  void guard2BypassesOptimizerWhenBoardHasZeroViasAndTraceLengthNearBound() {
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
    stats.vias.totalCount = 0;
    stats.bounds = new BoardStatisticsBounds();
    stats.bounds.minTraceLengthMm = 100.0f;
    stats.traces.totalLengthMm = 104.0f; // within 1.05 * 100 = 105.0

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNotNull(bypassReason);
    assertTrue(bypassReason.contains("within 5% of theoretical minimum"));
  }

  @Test
  void guard2DoesNotBypassWhenZeroViasButTraceLengthSuboptimal() {
    TestingSettings settings = new TestingSettings();
    RoutingJob job = getRoutingJob(DSN_BM08, settings);
    job.board = fullyRoutedBm08.deepCopy();
    job.thread = new NoOpStoppableThread();

    BatchOptimizer optimizer = BatchOptimizer.create(job);
    BoardStatistics stats =
        new BoardStatistics() {
          @Override
          public float getOptimizerScore(app.freerouting.settings.RouterSettings routerSettings) {
            return 800.0f;
          }
        };
    stats.connections.incompleteCount = 0;
    stats.vias.totalCount = 0;
    stats.bounds = new BoardStatisticsBounds();
    stats.bounds.minTraceLengthMm = 100.0f;
    stats.traces.totalLengthMm = 130.0f; // well above 1.05 * 100 = 105.0

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNull(bypassReason);
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
    stats.bounds.minViaCount = 10;
    stats.traces.totalLengthMm = 101.5f; // within 1.02 * 100.0 = 102.0

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNotNull(bypassReason);
    assertTrue(bypassReason.contains("theoretical minimum"));
  }

  @Test
  void guard3DoesNotBypassWhenTraceLengthNearBoundButExcessViasRemain() {
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
    stats.bounds.minViaCount = 2; // vias (10) > minViaCount (2)
    stats.traces.totalLengthMm = 101.5f;

    String bypassReason = optimizer.evaluatePreFlightGuards(stats);
    assertNull(bypassReason);
  }

  @Test
  void areAllViasMandatoryLayerTransitionsReturnsFalseWhenNoVias() {
    RoutingBoard board = mock(RoutingBoard.class);
    when(board.getVias()).thenReturn(List.of());

    assertFalse(BatchOptimizer.areAllViasMandatoryLayerTransitions(board));
  }

  @Test
  void areAllViasMandatoryLayerTransitionsReturnsTrueForSingleViaAcrossSmdLayers() {
    RoutingBoard board = mock(RoutingBoard.class);
    Via via = mock(Via.class);
    when(via.isUserFixed()).thenReturn(false);
    when(via.netCount()).thenReturn(1);
    when(via.getNetNumber(0)).thenReturn(1);

    Pin pinTop = mock(Pin.class);
    when(pinTop.firstLayer()).thenReturn(0);
    when(pinTop.lastLayer()).thenReturn(0);

    Pin pinBottom = mock(Pin.class);
    when(pinBottom.firstLayer()).thenReturn(1);
    when(pinBottom.lastLayer()).thenReturn(1);

    when(via.getConnectedSet(1, true)).thenReturn(Set.of(via, pinTop, pinBottom));
    when(board.getVias()).thenReturn(List.of(via));

    assertTrue(BatchOptimizer.areAllViasMandatoryLayerTransitions(board));
  }

  @Test
  void areAllViasMandatoryLayerTransitionsReturnsFalseWhenSmdPinsOnSameLayer() {
    RoutingBoard board = mock(RoutingBoard.class);
    Via via = mock(Via.class);
    when(via.isUserFixed()).thenReturn(false);
    when(via.netCount()).thenReturn(1);
    when(via.getNetNumber(0)).thenReturn(1);

    Pin pin1 = mock(Pin.class);
    when(pin1.firstLayer()).thenReturn(0);
    when(pin1.lastLayer()).thenReturn(0);

    Pin pin2 = mock(Pin.class);
    when(pin2.firstLayer()).thenReturn(0);
    when(pin2.lastLayer()).thenReturn(0);

    when(via.getConnectedSet(1, true)).thenReturn(Set.of(via, pin1, pin2));
    when(board.getVias()).thenReturn(List.of(via));

    assertFalse(BatchOptimizer.areAllViasMandatoryLayerTransitions(board));
  }

  @Test
  void areAllViasMandatoryLayerTransitionsReturnsFalseWhenMultipleViasInConnectedComponent() {
    RoutingBoard board = mock(RoutingBoard.class);
    Via via1 = mock(Via.class);
    Via via2 = mock(Via.class);
    when(via1.isUserFixed()).thenReturn(false);
    when(via1.netCount()).thenReturn(1);
    when(via1.getNetNumber(0)).thenReturn(1);

    Pin pinTop = mock(Pin.class);
    when(pinTop.firstLayer()).thenReturn(0);
    when(pinTop.lastLayer()).thenReturn(0);

    Pin pinBottom = mock(Pin.class);
    when(pinBottom.firstLayer()).thenReturn(1);
    when(pinBottom.lastLayer()).thenReturn(1);

    when(via1.getConnectedSet(1, true)).thenReturn(Set.of(via1, via2, pinTop, pinBottom));
    when(board.getVias()).thenReturn(List.of(via1));

    assertFalse(BatchOptimizer.areAllViasMandatoryLayerTransitions(board));
  }

  @Test
  void areAllViasMandatoryLayerTransitionsReturnsFalseWhenConnectedToThroughHolePin() {
    RoutingBoard board = mock(RoutingBoard.class);
    Via via = mock(Via.class);
    when(via.isUserFixed()).thenReturn(false);
    when(via.netCount()).thenReturn(1);
    when(via.getNetNumber(0)).thenReturn(1);

    Pin thPin = mock(Pin.class);
    when(thPin.firstLayer()).thenReturn(0);
    when(thPin.lastLayer()).thenReturn(3); // Through-hole pin across layers

    Pin pinBottom = mock(Pin.class);
    when(pinBottom.firstLayer()).thenReturn(1);
    when(pinBottom.lastLayer()).thenReturn(1);

    when(via.getConnectedSet(1, true)).thenReturn(Set.of(via, thPin, pinBottom));
    when(board.getVias()).thenReturn(List.of(via));

    assertFalse(BatchOptimizer.areAllViasMandatoryLayerTransitions(board));
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
    job.board = fullyRoutedBm08.deepCopy();
    job.routerSettings.optimizerScoring.excessWireLengthWeight = 0.0f;
    job.routerSettings.optimizerScoring.excessViaWeight = 0.0f;
    job.routerSettings.optimizerScoring.excessBendWeight = 0.0f;
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

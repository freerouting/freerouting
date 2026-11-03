package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.settings.RouterSettings;
import app.freerouting.settings.sources.DefaultSettings;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** The optimizer must improve the routed connections of a board that is not completely routed. */
class BatchOptimizerPartialBoardTest {

  /**
   * Net DETOUR is routed with a 52 mm detour that a straight 34 mm trace can replace. Net WALLED
   * connects W0 and W1 with a 10 mm trace, but cannot reach W2, which sits inside a closed keepout
   * frame on both layers.
   */
  private static final String DSN =
      """
      (pcb partial (resolution um 10) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 40000 25000))
          (keepout (rect signal 26000 15000 34000 15500))
          (keepout (rect signal 26000 20500 34000 21000))
          (keepout (rect signal 26000 15000 26500 21000))
          (keepout (rect signal 33500 15000 34000 21000))
          (via V) (rule (width 200) (clearance 200)))
        (placement (component C (place D1 3000 3000 front 0) (place D2 37000 3000 front 0)
          (place W0 14000 18000 front 0) (place W1 24000 18000 front 0)
          (place W2 30000 18000 front 0)))
        (library (image C (pin P 1 0 0))
          (padstack P (shape (circle top 800)) (shape (circle bottom 800)) (attach off))
          (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
        (network (net DETOUR (pins D1-1 D2-1)) (net WALLED (pins W0-1 W1-1 W2-1)))
        (wiring
          (wire (path top 200 3000 3000 3000 12000 37000 12000 37000 3000) (net DETOUR))
          (wire (path top 200 14000 18000 24000 18000) (net WALLED))))
      """;

  @Test
  void optimizesRoutedConnectionsWhileOthersStayUnrouted() {
    RoutingJob job = jobFor(loadBoard());
    BoardStatistics before = job.board.getStatistics();
    assertEquals(1, before.connections.incompleteCount, "W2 must start unrouted");
    assertEquals(62.0f, before.traces.totalLengthMm, 0.01f);

    new BatchOptimizer(job).runBatchLoop();

    BoardStatistics after = job.board.getStatistics();
    assertEquals(1, after.connections.incompleteCount, "W0 and W1 must stay connected");
    assertEquals(0, after.clearanceViolations.totalCount);
    assertEquals(
        34.0f + 10.0f,
        after.traces.totalLengthMm,
        0.5f,
        "the detour must become a straight trace, the trace of WALLED must stay");
  }

  private static RoutingBoard loadBoard() {
    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8)), null, null, "partial");
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static RoutingJob jobFor(RoutingBoard board) {
    RouterSettings settings = new DefaultSettings().getSettings();
    settings.setLayerCount(board.getLayerCount());
    settings.applyBoardSpecificOptimizations(board);
    settings.optimizer.enabled = true;
    settings.optimizer.maxThreads = 1;

    RoutingJob job = new RoutingJob();
    job.board = board;
    job.routerSettings = settings;
    job.thread =
        new StoppableThread() {
          @Override
          protected void threadAction() {}
        };
    return job;
  }
}

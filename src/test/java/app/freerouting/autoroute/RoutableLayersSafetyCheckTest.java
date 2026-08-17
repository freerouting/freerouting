package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertThrows;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.board.ItemIdGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.management.HeadlessBoardManager;
import org.junit.jupiter.api.Test;

class RoutableLayersSafetyCheckTest extends RoutingFixtureTest {

  @Test
  void testRoutingFailsWhenAllLayersDisabledCurrent() {
    RoutingJob job = getRoutingJob("Issue508-DAC2020_bm01.dsn");
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    job.board = boardManager.getRoutingBoard();

    // Disable all layers
    for (int i = 0; i < job.routerSettings.getLayerCount(); i++) {
      job.routerSettings.setLayerActive(i, false);
    }

    BatchAutorouter router = new BatchAutorouter(job);
    assertThrows(IllegalArgumentException.class, router::runBatchLoop);
  }
}

package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertThrows;

import app.freerouting.board.ItemIdentificationNumberGenerator;
import app.freerouting.core.RoutingJob;
import app.freerouting.fixtures.RoutingFixtureTest;
import app.freerouting.management.HeadlessBoardManager;
import org.junit.jupiter.api.Test;

class RoutableLayersSafetyCheckTest extends RoutingFixtureTest {

  @Test
  void testRoutingFailsWhenAllLayersDisabledCurrent() {
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn");
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdentificationNumberGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    job.board = boardManager.get_routing_board();

    // Disable all layers
    for (int i = 0; i < job.routerSettings.getLayerCount(); i++) {
      job.routerSettings.set_layer_active(i, false);
    }

    BatchAutorouter router = new BatchAutorouter(job);
    assertThrows(IllegalArgumentException.class, router::runBatchLoop);
  }

  @Test
  void testRoutingFailsWhenAllLayersDisabledV19() {
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn");
    HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
    try {
      boardManager.loadFromSpecctraDsn(job.input.getData(), null, new ItemIdentificationNumberGenerator());
    } catch (Exception e) {
      throw new RuntimeException("Failed to load DSN board", e);
    }
    job.board = boardManager.get_routing_board();

    // Disable all layers
    for (int i = 0; i < job.routerSettings.getLayerCount(); i++) {
      job.routerSettings.set_layer_active(i, false);
    }

    BatchAutorouterV19 routerV19 = new BatchAutorouterV19(job);
    assertThrows(IllegalArgumentException.class, routerV19::runBatchLoop);
  }
}

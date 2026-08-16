package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.Item;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * KiCad exports NPTH holes as circular per-copper-layer package keepouts. With the hole-clearance
 * override active they must be assigned to the dedicated "hole_edge" clearance class so copper
 * keeps hole clearance from the hole boundary.
 */
class HoleKeepoutClearanceTest extends RoutingFixtureTest {

  @Test
  void npthKeepoutsGetHoleEdgeClearanceClass() {
    var testingSettings = new TestingSettings();
    testingSettings.setHoleClearanceUm(250.0);
    // Load-only: all stages off — this test is about board preparation, not routing.
    testingSettings.setFanoutEnabled(false);
    testingSettings.setRouterEnabled(false);
    testingSettings.setOptimizerEnabled(false);
    testingSettings.setJobTimeoutString("00:02:00");
    RoutingJob job = getRoutingJob("Issue230-CNH_Functional_Tester_1.dsn", testingSettings);

    job = runRoutingJob(job);

    var matrix = job.board.rules.clearanceMatrix;
    int holeEdgeClassNo = matrix.getNo("hole_edge");
    assertTrue(holeEdgeClassNo > 0, "hole_edge clearance class must exist");

    int reclassified = 0;
    for (Item item : job.board.getItems()) {
      if (item.getClass() == ObstacleArea.class
          && item.getComponentId() > 0
          && ((ObstacleArea) item).getArea() instanceof Circle) {
        assertEquals(
            holeEdgeClassNo,
            item.clearanceClassIndex(),
            "circular package keepout (NPTH hole) must use the hole_edge class");
        reclassified++;
      }
    }
    assertTrue(reclassified > 0, "fixture must contain NPTH keepout circles");

    int expectedBoardUnits =
        (int)
            Math.round(
                Unit.scale(
                    250.0 * Math.max(1, job.board.communication.resolution),
                    Unit.UM,
                    job.board.communication.unit));
    for (int layer = 0; layer < matrix.getLayerCount(); layer++) {
      assertTrue(
          matrix.getValue(holeEdgeClassNo, 1, layer, false) >= expectedBoardUnits,
          "hole_edge clearance must be at least the configured hole clearance");
    }
  }
}

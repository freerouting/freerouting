package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.Item;
import app.freerouting.board.ObstacleArea;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * KiCad exports NPTH holes as circular per-copper-layer package keepouts. With the
 * hole-clearance override active they must be assigned to the dedicated "hole_edge"
 * clearance class so copper keeps hole clearance from the hole boundary.
 */
public class HoleKeepoutClearanceTest extends RoutingFixtureTest {

  @Test
  void npthKeepoutsGetHoleEdgeClearanceClass() {
    var testingSettings = new TestingSettings();
    testingSettings.setHoleClearanceUm(250.0);
    // Load-only: all stages off — this test is about board preparation, not routing.
    testingSettings.setFanoutEnabled(false);
    testingSettings.setRouterEnabled(false);
    testingSettings.setOptimizerEnabled(false);
    testingSettings.setJobTimeoutString("00:02:00");
    RoutingJob job = GetRoutingJob("Issue230-CNH_Functional_Tester_1.dsn", testingSettings);

    job = RunRoutingJob(job);

    var matrix = job.board.rules.clearance_matrix;
    int holeEdgeClassNo = matrix.get_no("hole_edge");
    assertTrue(holeEdgeClassNo > 0, "hole_edge clearance class must exist");

    int reclassified = 0;
    for (Item item : job.board.get_items()) {
      if (item.getClass() == ObstacleArea.class && item.get_component_no() > 0
          && ((ObstacleArea) item).get_area() instanceof Circle) {
        assertEquals(holeEdgeClassNo, item.clearance_class_no(),
            "circular package keepout (NPTH hole) must use the hole_edge class");
        reclassified++;
      }
    }
    assertTrue(reclassified > 0, "fixture must contain NPTH keepout circles");

    int expectedBoardUnits = 250 * Math.max(1, job.board.communication.resolution);
    for (int layer = 0; layer < matrix.get_layer_count(); layer++) {
      assertTrue(matrix.get_value(holeEdgeClassNo, 1, layer, false) >= expectedBoardUnits,
          "hole_edge clearance must be at least the configured hole clearance");
    }
  }
}

package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.geometry.planar.Point;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Automatic neckdown narrows a trace only where it enters a pin.
 *
 * <p>The micro-neckdown in trace insertion retried any segment that did not fit at 75 %, 60 % and
 * 50 % of the net-class width, wherever it was. On this board, whose signal nets are 250 um wide,
 * it left four segments at 125 um, 1.7 to 3.4 mm away from any pad of their net. A KiCad DRC flags
 * such traces against the minimum track width, and widening them back costs the clearance they were
 * narrowed for.
 */
class MicroNeckdownNearPinsTest extends RoutingFixtureTest {

  @Test
  void tracesAreNarrowedOnlyNearAPinOfTheirNet() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(30);
    RoutingJob job = getRoutingJob("Issue153-wavefolder.dsn", ts);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertEquals(0, new BoardStatistics(job.board).connections.incompleteCount);

    List<String> narrowedAwayFromPins = new ArrayList<>();
    for (Trace trace : job.board.getTraces()) {
      int net = trace.getNetNumber(0);
      int layer = trace.getLayer();
      if (trace.getHalfWidth() >= job.board.rules.getTraceHalfWidth(net, layer)) {
        continue;
      }
      boolean nearPin = false;
      for (Pin pin : job.board.getPins()) {
        if (!pin.containsNet(net) || !pin.isOnLayer(layer)) {
          continue;
        }
        double clearance =
            job.board.rules.clearanceMatrix.getValue(
                trace.clearanceClassIndex(), pin.clearanceClassIndex(), layer, true);
        // The distance within which FoundConnectionInserter necks a trace down to a pin.
        double zone = 2 * (0.5 * pin.getMaxWidth(layer) + clearance);
        if (within(pin, trace.firstCorner(), zone) || within(pin, trace.lastCorner(), zone)) {
          nearPin = true;
          break;
        }
      }
      if (!nearPin) {
        narrowedAwayFromPins.add(
            "net " + net + ": half width " + trace.getHalfWidth() + " at " + trace.firstCorner());
      }
    }
    assertTrue(
        narrowedAwayFromPins.isEmpty(),
        () ->
            narrowedAwayFromPins.size()
                + " traces narrower than their net class away from its pins: "
                + narrowedAwayFromPins);
  }

  private static boolean within(Pin pin, Point corner, double distance) {
    return pin.getCenter().toFloat().distance(corner.toFloat()) < distance;
  }
}

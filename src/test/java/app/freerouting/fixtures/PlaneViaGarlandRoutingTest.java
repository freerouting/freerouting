package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A 4-layer board whose inner layers are planes: In1.Cu is a GND plane, In2.Cu a +3V3 plane, both
 * declared {@code (type power)} with a {@code (plane …)} and its windows, as KiCad writes them for
 * power layers. On top: an SOIC-8 MCU, a 0805 decoupling capacitor, a 2-pin header and a garland of
 * four resistor + LED pairs whose cathodes go to GND.
 *
 * <p>Every SMD pin of a plane net must drop into its plane through a via of its own. Before the
 * plane-aware via costs, the fanout sorted its targets by bounding-box centre; a plane's centre is
 * the board centre, so the neighbouring pad always won, and the LED cathodes were chained to each
 * other on the outer layers instead of each reaching the GND plane.
 *
 * <p>A plane via is priced with {@code plane_via_costs}, an F.Cu ↔ B.Cu via with {@code via_costs}:
 * with a cheap plane via and an expensive signal via, the signals stay on few vias while each plane
 * pad still gets its own.
 */
class PlaneViaGarlandRoutingTest extends RoutingFixtureTest {

  private static final String BOARD = "Plane-via-garland-4layer.dsn";

  @Test
  void everyPlanePadDropsIntoItsPlaneThroughItsOwnVia() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:02:00");
    settings.setMaxPasses(20);
    settings.setOptimizerEnabled(false);
    settings.setViaCosts(200);
    settings.setPlaneViaCosts(1);

    RoutingJob job = getRoutingJob(BOARD, settings);
    runRoutingJob(job);

    assertRoutingResult(job, BOARD).maxIncompleteConnections(0).exactClearanceViolations(0).check();

    var board = job.board;
    List<String> chained = new ArrayList<>();
    int planePads = 0;
    for (Pin pin : board.getPins()) {
      if (pin.firstLayer() != pin.lastLayer() || pin.netCount() != 1) {
        continue;
      }
      int netNo = pin.getNetNumber(0);
      Net net = board.rules.nets.get(netNo);
      if (!net.containsPlane()) {
        continue;
      }
      planePads++;
      // Everything this pad reaches on its own before entering the plane.
      var escape = pin.getConnectedSet(netNo, true);
      long pins = escape.stream().filter(Pin.class::isInstance).count();
      long vias = escape.stream().filter(Via.class::isInstance).count();
      if (pins != 1 || vias == 0) {
        chained.add(
            pin.componentName()
                + "-"
                + pin.name()
                + " ("
                + net.name
                + "): "
                + pins
                + " pins, "
                + vias
                + " vias before the plane");
      }
    }
    assertEquals(8, planePads, "6 GND and 2 +3V3 SMD pads on the board");
    assertTrue(chained.isEmpty(), "Plane pads not escaping alone into their plane: " + chained);

    long tracesOnPlanes =
        board.getTraces().stream()
            .map(Trace::getLayer)
            .filter(layer -> !board.layerStructure.layers[layer].isSignal)
            .count();
    assertEquals(0L, tracesOnPlanes, "No trace may be routed inside a plane layer");
  }
}

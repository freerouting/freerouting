package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.RoutingJob;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * 4-layer boards whose inner layers are planes: In1.Cu is a GND plane, In2.Cu a +3V3 plane, both
 * declared {@code (type power)} with a {@code (plane …)} and its windows, as KiCad writes them for
 * power layers.
 *
 * <ul>
 *   <li>{@code Plane-via-garland-4layer.dsn}: an SOIC-8 MCU, a 0805 decoupling capacitor, a 2-pin
 *       header and a garland of four resistor + LED pairs whose cathodes go to GND.
 *   <li>{@code Plane-via-garland-dense-4layer.dsn}: the same idea packed with the parts that make a
 *       real board hard: a QFN-24 at 0.5 mm pitch with a GND exposed pad, a SOT-23-5, a TSSOP-16,
 *       0603 decoupling next to the supply pins and eight 0603 LED + R pairs at 2 mm pitch; 0.2 mm
 *       traces and clearance, 0.6 mm vias.
 *   <li>{@code Plane-via-adc-decoupling-4layer.dsn}: two TSSOP-16 ADCs, each with three 0603
 *       decoupling capacitors in a row at 2 mm pitch beside its supply pins, as on a real board.
 *       Without {@code plane_as_priority} the capacitors end up on a bus: each one's search to the
 *       plane runs across its neighbour's pad to the neighbour's via, which costs less than a via.
 * </ul>
 *
 * <p>Every SMD pad of a plane net must drop into its plane through a via of its own, rather than
 * being joined to another pad of the net on the outer layers. Pins of one package (the GND pins of
 * the QFN and its exposed pad) may share a via. Before the maze could reach a plane on a power
 * layer, only the fanout dropped pads into planes, and every pad it missed was chained to its
 * neighbours.
 */
@Tag("slow")
class PlaneViaGarlandRoutingTest extends RoutingFixtureTest {

  private static final String GARLAND = "Plane-via-garland-4layer.dsn";
  private static final String DENSE = "Plane-via-garland-dense-4layer.dsn";
  private static final String ADC = "Plane-via-adc-decoupling-4layer.dsn";

  private static TestingSettings settings() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:02:00");
    settings.setMaxPasses(20);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  @Test
  void cheapPlaneViasGiveEveryPlanePadItsOwnVia() {
    TestingSettings settings = settings();
    settings.setViaCosts(200);
    settings.setPlaneViaCosts(1);
    RoutingJob job = getRoutingJob(GARLAND, settings);
    runRoutingJob(job);

    assertRoutingResult(job, GARLAND)
        .maxIncompleteConnections(0)
        .exactClearanceViolations(0)
        .check();
    List<String> chained = chainedPlanePads(job.board);
    assertEquals(8, planePadCount(job.board), "6 GND and 2 +3V3 SMD pads on the board");
    assertTrue(chained.isEmpty(), "Plane pads not escaping alone into their plane: " + chained);
    assertNoTraceInPlaneLayers(job.board);
  }

  @Test
  void planeAsPriorityGivesEveryPlanePadItsOwnVia() {
    TestingSettings settings = settings();
    settings.setPlaneAsPriority(true);
    RoutingJob job = getRoutingJob(GARLAND, settings);
    runRoutingJob(job);

    assertRoutingResult(job, GARLAND)
        .maxIncompleteConnections(0)
        .exactClearanceViolations(0)
        .check();
    List<String> chained = chainedPlanePads(job.board);
    assertTrue(chained.isEmpty(), "Plane pads not escaping alone into their plane: " + chained);
    assertNoTraceInPlaneLayers(job.board);
  }

  @Test
  void planeAsPriorityRoutesTheDenseGarland() {
    TestingSettings settings = settings();
    settings.setPlaneAsPriority(true);
    RoutingJob job = getRoutingJob(DENSE, settings);
    runRoutingJob(job);

    assertRoutingResult(job, DENSE).maxIncompleteConnections(0).exactClearanceViolations(0).check();
    assertEquals(33, planePadCount(job.board), "GND and +3V3 SMD pads on the board");
    List<String> chained = chainedPlanePads(job.board);
    assertTrue(chained.isEmpty(), "Plane pads not escaping alone into their plane: " + chained);
    assertNoTraceInPlaneLayers(job.board);
  }

  @Test
  void planeAsPriorityGivesEachDecouplingCapacitorItsOwnVias() {
    TestingSettings settings = settings();
    settings.setPlaneAsPriority(true);
    RoutingJob job = getRoutingJob(ADC, settings);
    runRoutingJob(job);

    assertRoutingResult(job, ADC).maxIncompleteConnections(0).exactClearanceViolations(0).check();
    assertEquals(26, planePadCount(job.board), "GND and +3V3 SMD pads on the board");
    List<String> chained = chainedPlanePads(job.board);
    // 24 of 26 measured (12 before plane pads stopped crossing each other's copper, 8 without the
    // option); the last two are neighbouring GND pads sharing the via set in the gap between them.
    assertTrue(chained.size() <= 2, "Plane pads not escaping alone into their plane: " + chained);
    assertNoTraceInPlaneLayers(job.board);
  }

  @Test
  void planeAsPriorityKeepsItsObstaclesWhenNecking() {
    TestingSettings settings = settings();
    settings.setPlaneAsPriority(true);
    settings.setNeckWidthUm(100);
    RoutingJob job = getRoutingJob(ADC, settings);
    runRoutingJob(job);

    assertRoutingResult(job, ADC).maxIncompleteConnections(0).exactClearanceViolations(0).check();
    List<String> chained = chainedPlanePads(job.board);
    // A plane escape that failed at the class width used to be retried necked without its
    // obstacles, through the neighbouring capacitor's pad: 4 chained pads instead of 2.
    assertTrue(chained.size() <= 2, "Plane pads not escaping alone into their plane: " + chained);
    assertNoTraceInPlaneLayers(job.board);
  }

  private static boolean isPlanePad(RoutingBoard board, Pin pin) {
    if (pin.firstLayer() != pin.lastLayer() || pin.netCount() != 1) {
      return false;
    }
    Net net = board.rules.nets.get(pin.getNetNumber(0));
    return net != null && net.containsPlane();
  }

  private static int planePadCount(RoutingBoard board) {
    int count = 0;
    for (Pin pin : board.getPins()) {
      if (isPlanePad(board, pin)) {
        count++;
      }
    }
    return count;
  }

  /** Plane pads that reach their plane only through a pad of another package, or not at all. */
  private static List<String> chainedPlanePads(RoutingBoard board) {
    List<String> chained = new ArrayList<>();
    for (Pin pin : board.getPins()) {
      if (!isPlanePad(board, pin)) {
        continue;
      }
      int netNo = pin.getNetNumber(0);
      // Everything this pad reaches before entering the plane.
      var escape = pin.getConnectedSet(netNo, true);
      long otherPackages =
          escape.stream()
              .filter(
                  item ->
                      item instanceof Pin other && other.getComponentId() != pin.getComponentId())
              .count();
      long vias = escape.stream().filter(Via.class::isInstance).count();
      if (otherPackages > 0 || vias == 0) {
        chained.add(
            pin.componentName()
                + "-"
                + pin.name()
                + " ("
                + board.rules.nets.get(netNo).name
                + "): "
                + otherPackages
                + " pads of other packages, "
                + vias
                + " vias before the plane");
      }
    }
    return chained;
  }

  private static void assertNoTraceInPlaneLayers(RoutingBoard board) {
    long tracesOnPlanes =
        board.getTraces().stream()
            .map(Trace::getLayer)
            .filter(layer -> !board.layerStructure.layers[layer].isSignal)
            .count();
    assertEquals(0L, tracesOnPlanes, "No trace may be routed inside a plane layer");
  }
}

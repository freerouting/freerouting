package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * A copper area given as an outline minus windows (KiCad exports filled zones as {@code (wire
 * (polygon ...) (window ...))}) can be split into several islands by its own windows, with no item
 * of another net anywhere on the layer. The fracture analysis only looked for islands when it found
 * a foreign obstacle, so such an area was taken as one piece and the pads on either side of the
 * window were reported as connected.
 */
class ZoneSplitByOwnWindowTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Zone-split-by-own-window-2layer.dsn";

  private static TestingSettings routerOnly() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(20);
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(true);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  private static Pin pin(RoutingBoard board, String component) {
    return board.getPins().stream()
        .filter(
            p ->
                board.components.get(p.getComponentId()) != null
                    && component.equals(board.components.get(p.getComponentId()).name))
        .findFirst()
        .orElseThrow();
  }

  private static ConductionArea area(RoutingBoard board, int net) {
    return board.getConnectableItems(net).stream()
        .filter(ConductionArea.class::isInstance)
        .map(ConductionArea.class::cast)
        .findFirst()
        .orElseThrow();
  }

  private static int traceCount(RoutingBoard board, int net) {
    int count = 0;
    for (Trace trace : board.getTraces()) {
      if (trace.containsNet(net)) {
        count++;
      }
    }
    return count;
  }

  /**
   * {@code Zone-split-by-own-window-2layer.dsn} (invented board): two copper areas on F.Cu, x
   * 15..25 mm (GND, pins J1 at the top and J2 at the bottom) and x 55..65 mm (VCC, J3 and J4). Each
   * has one window at mid-height. The GND window runs from edge to edge, so the GND copper is two
   * disjoint pieces. The VCC window stops 1 mm short of each edge, so VCC stays one piece. Nothing
   * else is on the board.
   */
  @Test
  void areaSplitByItsOwnWindowIsFracturedAndRouted() {
    RoutingJob job = getRoutingJob(FIXTURE, routerOnly());
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    Pin j1 = pin(job.board, "J1");
    Pin j3 = pin(job.board, "J3");
    int gnd = j1.getNetNumber(0);
    int vcc = j3.getNetNumber(0);
    assertTrue(area(job.board, gnd).isFractured(), "the GND window does not split the GND area");
    assertFalse(area(job.board, vcc).isFractured(), "the VCC area is one piece around its window");
    assertTrue(
        j1.getConnectedSet(gnd).contains(pin(job.board, "J2")),
        "J1 and J2 (GND, on the two pieces of the area) are not connected");
    assertTrue(traceCount(job.board, gnd) > 0, "no trace joins the two pieces of the GND area");
    assertEquals(0, traceCount(job.board, vcc), "VCC is already connected through its area");
    assertRoutingResult(job, FIXTURE).exactIncompleteConnections(0).check();
  }
}

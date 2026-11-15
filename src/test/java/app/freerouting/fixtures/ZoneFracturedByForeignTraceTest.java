package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * A copper area that is not a plane can be split in two by the trace of another net. The pads on
 * either side are then no longer connected through it, and the autorouter must connect them by
 * another way. When the fractured area itself was part of the route sets, the search ended on the
 * island it already touched and the connection was never made (issue #1143).
 */
class ZoneFracturedByForeignTraceTest extends RoutingFixtureTest {

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

  /**
   * {@code Zone-fractured-by-foreign-trace-2layer.dsn} (invented board): a VCC copper area on F.Cu,
   * x 15..25 mm, given as a wiring polygon, with VCC pins J3 at the top and J4 at the bottom. A
   * fixed GND trace crosses it from J1 to J2 at mid-height, so the area falls into two islands.
   * B.Cu is free.
   */
  @Test
  void pinsOnTwoIslandsOfAFracturedAreaAreConnected() {
    RoutingJob job = getRoutingJob("Zone-fractured-by-foreign-trace-2layer.dsn", routerOnly());
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    Pin j3 = pin(job.board, "J3");
    Pin j4 = pin(job.board, "J4");
    int vcc = j3.getNetNumber(0);
    ConductionArea area =
        job.board.getConnectableItems(vcc).stream()
            .filter(ConductionArea.class::isInstance)
            .map(ConductionArea.class::cast)
            .findFirst()
            .orElseThrow();
    assertTrue(area.isFractured(), "the fixed GND trace does not split the VCC area");
    assertTrue(
        j3.getConnectedSet(vcc).contains(j4),
        "J3 and J4 (VCC, on two islands of the fractured area) are not connected");
    for (Trace trace : job.board.getTraces()) {
      if (trace.containsNet(vcc)) {
        assertEquals(1, trace.getLayer(), "the VCC connection crosses the GND trace on F.Cu");
      }
    }
    assertRoutingResult(job, "Zone-fractured-by-foreign-trace-2layer.dsn")
        .exactIncompleteConnections(0)
        .check();
  }
}

package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.RoutingJob;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * The islands of a fractured copper area are cached by the revision of the board. The shove and the
 * pull-tight change a trace in place ({@link PolylineTrace#change}): the board changes as well, and
 * the islands must follow, or a connection that is gone still counts as made.
 */
class PlaneIslandsFollowTraceChangeTest extends RoutingFixtureTest {

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
   * {@code Zone-fractured-by-foreign-trace-2layer.dsn}: a VCC area on F.Cu, x 15..25 mm, with VCC
   * pins J3 at the top and J4 at the bottom, split in two by a GND trace from J1 (x 8 mm) to J2 (x
   * 32 mm). Shortened in place so that it stops at x 12 mm, left of the area, the GND trace no
   * longer splits it.
   */
  @Test
  void aTraceChangedInPlaceIsSeenByTheIslandsOfAnArea() {
    TestingSettings loadOnly = new TestingSettings();
    loadOnly.setFanoutEnabled(false);
    loadOnly.setRouterEnabled(false);
    loadOnly.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob("Zone-fractured-by-foreign-trace-2layer.dsn", loadOnly);
    runRoutingJob(job);

    Pin j1 = pin(job.board, "J1");
    Pin j3 = pin(job.board, "J3");
    Pin j4 = pin(job.board, "J4");
    int gnd = j1.getNetNumber(0);
    int vcc = j3.getNetNumber(0);
    ConductionArea area =
        job.board.getConnectableItems(vcc).stream()
            .filter(ConductionArea.class::isInstance)
            .map(ConductionArea.class::cast)
            .findFirst()
            .orElseThrow();
    PolylineTrace gndTrace =
        job.board.getTraces().stream()
            .filter(t -> t.containsNet(gnd))
            .map(PolylineTrace.class::cast)
            .findFirst()
            .orElseThrow();
    assertTrue(area.isFractured(), "the GND trace does not split the VCC area");
    assertFalse(j3.getConnectedSet(vcc).contains(j4), "J3 and J4 start on two islands");

    gndTrace.change(new Polyline(gndTrace.firstCorner(), new IntPoint(120000, -125000)));

    assertFalse(area.isFractured(), "the area keeps the islands of the old GND trace");
    assertTrue(
        j3.getConnectedSet(vcc).contains(j4),
        "J3 and J4 are still taken for two islands after the GND trace left the area");
  }
}

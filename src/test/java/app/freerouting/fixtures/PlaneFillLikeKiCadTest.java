package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.kicad.KiCadBoardZones;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Where the fill of a plane is not where KiCad puts it, a bridge that the router lays on it is not
 * one. Boards with a GND plane on F.Cu and the GND pins J1 (left) and J2 (right), loaded without
 * routing.
 */
class PlaneFillLikeKiCadTest extends RoutingFixtureTest {

  private RoutingJob load(String board, KiCadBoardZones zones) {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(board, settings);
    job.kicadBoardZones = zones;
    runRoutingJob(job);
    assertEquals(RoutingJobState.COMPLETED, job.state);
    return job;
  }

  private static ConductionArea plane(RoutingJob job) {
    return job.board.getConductionAreas().iterator().next();
  }

  /**
   * {@code Plane-outside-outline-2layer.dsn}: a U-shaped board under a rectangular plane. J1 and J2
   * sit on the two arms; a trace of another net cuts the bar that joins them. The plane drawn
   * across the notch is outside the board: KiCad fills nothing there, and J1 and J2 are apart.
   */
  @Test
  void aPlaneDoesNotJoinItsPiecesOutsideTheBoard() {
    RoutingJob job = load("Plane-outside-outline-2layer.dsn", null);

    assertTrue(plane(job).isFractured(), "the plane joins J1 and J2 across the notch");
    assertEquals(1, new BoardStatistics(job.board).connections.incompleteCount);
  }

  /**
   * {@code Plane-zone-clearance-neck-stub-2layer.dsn}: the neck board of {@link
   * KiCadZoneClearancePlaneNeckTest} with a GND trace from J1 that stops in the clearance band of
   * the traces of the other net (fixed, so that loading keeps it). With the zone of the board, its
   * end touches no fill: it dangles.
   */
  @Test
  void aTraceEndInTheClearanceBandDangles() throws IOException {
    RoutingJob job =
        load(
            "Plane-zone-clearance-neck-stub-2layer.dsn",
            KiCadBoardZones.read(
                TestFixtures.resolvePath("Plane-zone-clearance-neck-2layer.kicad_pcb")));

    Trace stub = null;
    for (Item item : job.board.getItems()) {
      if (item instanceof Trace trace && trace.containsNet(plane(job).getNetNumber(0))) {
        stub = trace;
      }
    }
    assertTrue(stub != null, "the GND trace is on the board");
    assertTrue(stub.isTail(), "an end in the clearance band touches nothing");
  }

  /**
   * The islands of a plane are cached by the revision of the board. A trace changed in place (by
   * the shove or the pull-tight) changes the board as well: the stub of {@link
   * #aTraceEndInTheClearanceBandDangles}, moved so that it ends in the fill, no longer dangles.
   */
  @Test
  void aTraceChangedInPlaceIsSeenByThePlane() throws IOException {
    RoutingJob job =
        load(
            "Plane-zone-clearance-neck-stub-2layer.dsn",
            KiCadBoardZones.read(
                TestFixtures.resolvePath("Plane-zone-clearance-neck-2layer.kicad_pcb")));
    PolylineTrace stub = null;
    for (Item item : job.board.getItems()) {
      if (item instanceof PolylineTrace trace && trace.containsNet(plane(job).getNetNumber(0))) {
        stub = trace;
      }
    }
    assertTrue(stub != null && stub.isTail(), "the stub dangles in the clearance band");

    stub.change(new Polyline(stub.firstCorner(), new IntPoint(150000, -125000)));

    assertFalse(stub.isTail(), "the end moved into the fill is still taken for an end in the band");
  }
}

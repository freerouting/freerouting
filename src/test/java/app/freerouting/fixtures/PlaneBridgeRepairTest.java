package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.kicad.KiCadBoardZones;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * A plane that the copper of other nets cuts in pieces, or a pad cut off its plane, is an
 * incomplete connection that the router has to repair with a trace. Each board has a GND plane on
 * F.Cu and the GND pins J1 (left) and J2 (right), with nothing else to route.
 */
class PlaneBridgeRepairTest extends RoutingFixtureTest {

  private RoutingJob route(String board, KiCadBoardZones zones) {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(5);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(board, settings);
    job.kicadBoardZones = zones;
    runRoutingJob(job);
    assertEquals(RoutingJobState.COMPLETED, job.state);
    return job;
  }

  private static int incompletes(RoutingJob job) {
    return new BoardStatistics(job.board).connections.incompleteCount;
  }

  /**
   * {@code Plane-cut-through-2layer.dsn}: two fixed traces of another net cross the plane from top
   * to bottom and leave a 0.25 mm gap, too narrow for a trace: the plane is in two islands, and the
   * router has to join them on B.Cu.
   */
  @Test
  void aPlaneCutThroughIsBridged() {
    RoutingJob job = route("Plane-cut-through-2layer.dsn", null);

    assertEquals(0, incompletes(job));
  }

  /**
   * {@code Plane-zone-clearance-neck-2layer.dsn} with its zone: the 0.95 mm gap between the two
   * traces of another net leaves no copper with the 0.5 mm clearance of the zone, but a trace fits
   * in it with the 0.2 mm clearance of the net class. A trace through the gap joins the islands.
   */
  @Test
  void aNeckThatTheZoneCutsIsBridged() throws IOException {
    RoutingJob job =
        route(
            "Plane-zone-clearance-neck-2layer.dsn",
            KiCadBoardZones.read(
                TestFixtures.resolvePath("Plane-zone-clearance-neck-2layer.kicad_pcb")));

    assertTrue(job.board.getConductionAreas().iterator().next().isFractured());
    assertEquals(0, incompletes(job));
  }

  /**
   * {@code Plane-thermal-relief-starved-2layer.dsn} with its zone: the four vias of another net in
   * front of J1 leave no spoke to its thermal relief. A short trace from J1 out to the plane,
   * between two vias, connects it.
   */
  @Test
  void aPadCutOffItsPlaneIsConnected() throws IOException {
    RoutingJob job =
        route(
            "Plane-thermal-relief-starved-2layer.dsn",
            KiCadBoardZones.read(
                TestFixtures.resolvePath("Plane-thermal-relief-starved-2layer.kicad_pcb")));

    assertEquals(0, incompletes(job));
  }
}

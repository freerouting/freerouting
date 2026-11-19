package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.PadPlaneConnection;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.kicad.KiCadBoardZones;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * KiCad does not write into the DSN how a zone is connected to the pads of its net. A pad with a
 * thermal relief hangs on the plane by four spokes, and the refill of the zone drops every spoke
 * that the copper of another net, kept at the clearance of the zone, cuts or leaves without fill at
 * its end: the pad can be cut off a plane that is in one piece around it.
 *
 * <p>{@code Plane-thermal-relief-starved-2layer.dsn}: a GND plane on F.Cu with the GND pins J1
 * (left) and J2 (right), 1.7 mm square pads. Four fixed vias of another net stand in front of the
 * four sides of J1: three at 0.925 mm from the pad, the one above it at 1.125 mm. The plane passes
 * between the vias, so that it is in one piece whatever the clearance, and the DSN alone says that
 * J1 and J2 are connected. The expected results are the ones of the zone filler of KiCad 10.0.8 on
 * the same geometry.
 */
class KiCadThermalReliefPlaneTest extends RoutingFixtureTest {

  private static final String BOARD = "Plane-thermal-relief-starved-2layer.dsn";

  /** Loads the board and counts, without routing anything. */
  private RoutingJob load(KiCadBoardZones zones) {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(BOARD, settings);
    job.kicadBoardZones = zones;
    runRoutingJob(job);
    assertEquals(RoutingJobState.COMPLETED, job.state);
    return job;
  }

  /** The KiCad board of the fixture, with {@code from} replaced by {@code to} when given. */
  private static KiCadBoardZones board(String... replacements) throws IOException {
    String text =
        Files.readString(
            TestFixtures.resolvePath("Plane-thermal-relief-starved-2layer.kicad_pcb"),
            StandardCharsets.UTF_8);
    for (int i = 0; i + 1 < replacements.length; i += 2) {
      assertTrue(text.contains(replacements[i]), "the fixture has no '" + replacements[i] + "'");
      text = text.replace(replacements[i], replacements[i + 1]);
    }
    return KiCadBoardZones.parse(text, "test.kicad_pcb");
  }

  private static ConductionArea plane(RoutingJob job) {
    return job.board.getConductionAreas().iterator().next();
  }

  private static int incompletes(RoutingJob job) {
    return new BoardStatistics(job.board).connections.incompleteCount;
  }

  /** Without the KiCad board, the DSN alone says that the plane connects J1 and J2. */
  @Test
  void withoutTheKiCadBoardThePlaneConnectsThePads() {
    RoutingJob job = load(null);

    assertFalse(plane(job).isFractured());
    assertEquals(0, incompletes(job));
  }

  /**
   * Thermal reliefs with a gap of 0.5 mm and a zone clearance of 0.5 mm: the fill stops 0.5 mm
   * before each via, and each spoke of J1 ends less than half the minimum width away from that
   * limit. KiCad drops the four spokes.
   */
  @Test
  void aThermalReliefWithoutASpokeLeavesThePadAlone() throws IOException {
    RoutingJob job = load(board());

    ConductionArea plane = plane(job);
    assertEquals(PadPlaneConnection.Mode.THERMAL, plane.getPadConnection());
    assertEquals(5000, plane.getThermalGap(), "thermal gap of 0.5 mm in board units");
    assertEquals(5000, plane.getThermalSpokeWidth(), "spoke width of 0.5 mm in board units");
    assertTrue(plane.isFractured(), "J1 has no spoke left: it is alone on its island");
    assertEquals(1, incompletes(job), "J1 is not connected to the plane");
  }

  /**
   * One spoke is enough: with a zone clearance of 0.3 mm, the spoke towards the via that stands
   * further away is kept. Fewer spokes than the design rules ask for is a rule violation, not an
   * open.
   */
  @Test
  void oneSpokeConnectsThePad() throws IOException {
    RoutingJob job = load(board("(clearance 0.5)", "(clearance 0.3)"));

    assertFalse(plane(job).isFractured(), "one spoke of J1 reaches the plane");
    assertEquals(0, incompletes(job));
  }

  /** With a thermal gap of 0.2 mm the spokes end well before the vias. */
  @Test
  void shortSpokesAreKept() throws IOException {
    RoutingJob job = load(board("(thermal_gap 0.5)", "(thermal_gap 0.2)"));

    assertFalse(plane(job).isFractured());
    assertEquals(0, incompletes(job));
  }

  /** A solid connection: the fill reaches the pad between the vias. */
  @Test
  void aSolidConnectionReachesThePad() throws IOException {
    RoutingJob job = load(board("(connect_pads", "(connect_pads yes"));

    assertEquals(PadPlaneConnection.Mode.SOLID, plane(job).getPadConnection());
    assertFalse(plane(job).isFractured());
    assertEquals(0, incompletes(job));
  }

  /** A zone that connects no pad: J1 and J2 are both cut off the plane. */
  @Test
  void aZoneThatConnectsNoPadLeavesThemToTheRouter() throws IOException {
    RoutingJob job = load(board("(connect_pads", "(connect_pads no"));

    assertTrue(plane(job).isFractured());
    assertEquals(1, incompletes(job), "J1 and J2 must be connected by a trace");
  }

  /** The setting of a pad goes before the one of the zone. */
  @Test
  void theConnectionOfThePadOverridesTheZone() throws IOException {
    // on both GND pads, J1 and J2
    String net = "(net \"GND\")\n\t\t)";
    KiCadBoardZones solidZoneThermalPad =
        board("(connect_pads", "(connect_pads yes", net, "(zone_connect 1) " + net);
    assertEquals(2, countOf(solidZoneThermalPad, KiCadBoardZones.PadConnection.THERMAL));
    assertEquals(1, incompletes(load(solidZoneThermalPad)));

    KiCadBoardZones thermalZoneSolidPads = board(net, "(zone_connect 2) " + net);
    assertEquals(0, incompletes(load(thermalZoneSolidPads)));
  }

  /** A thru_hole_only zone gives a through-hole pad a thermal relief. */
  @Test
  void aThroughHolePadOfAThruHoleOnlyZoneHasAThermalRelief() throws IOException {
    RoutingJob job = load(board("(connect_pads", "(connect_pads thru_hole_only"));

    assertEquals(1, incompletes(job));
  }

  private static int countOf(KiCadBoardZones board, KiCadBoardZones.PadConnection connection) {
    int result = 0;
    for (KiCadBoardZones.Pad pad : board.pads()) {
      if (pad.connection() == connection) {
        result++;
      }
    }
    return result;
  }
}

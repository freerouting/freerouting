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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * The fill of a KiCad zone stops at the pads of its own net too: at the thermal gap of a pad with a
 * thermal relief, at the clearance of a pad the zone does not connect. And a trace or a via of the
 * net is connected to the plane only where the fill reaches it.
 *
 * <p>{@code Plane-thermal-relief-pocket-2layer.dsn}: a GND plane on F.Cu, 38 mm by 23 mm, with
 *
 * <ul>
 *   <li>J2, a through-hole pad in the middle of the fill;
 *   <li>SH1, a GND pad 0.6 mm wide that crosses the plane from top to bottom at x = 5 mm, set to
 *       "no connection" to zones: the fill keeps its clearance to it, which cuts a strip off the
 *       left of the plane;
 *   <li>TP1, a GND pad with a thermal relief in that strip: its four spokes are kept, and they end
 *       in the strip;
 *   <li>J1, a through-hole pad between four vias of another net that leave it no spoke (as in
 *       {@link KiCadThermalReliefPlaneTest}), with a GND trace from its centre that ends 0.25 mm
 *       past its edge, inside its thermal gap, where there is no fill.
 * </ul>
 *
 * <p>The expected results are the ones of the zone filler of KiCad 10.0.8 on the same geometry.
 */
class KiCadPlanePocketTest extends RoutingFixtureTest {

  private static final String BOARD = "Plane-thermal-relief-pocket-2layer.dsn";

  /** Loads the board and counts, without routing anything. */
  private int incompletes(KiCadBoardZones zones) {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(BOARD, settings);
    job.kicadBoardZones = zones;
    runRoutingJob(job);
    assertEquals(RoutingJobState.COMPLETED, job.state);
    return new BoardStatistics(job.board).connections.incompleteCount;
  }

  /** The KiCad board of the fixture, with {@code from} replaced by {@code to} when given. */
  private static KiCadBoardZones board(String... replacements) throws IOException {
    String text =
        Files.readString(
            TestFixtures.resolvePath("Plane-thermal-relief-pocket-2layer.kicad_pcb"),
            StandardCharsets.UTF_8);
    for (int i = 0; i + 1 < replacements.length; i += 2) {
      assertTrue(text.contains(replacements[i]), "the fixture has no '" + replacements[i] + "'");
      text = text.replace(replacements[i], replacements[i + 1]);
    }
    return KiCadBoardZones.parse(text, "test.kicad_pcb");
  }

  /** Without the KiCad board, the DSN alone says that the plane connects the four pads. */
  @Test
  void withoutTheKiCadBoardThePlaneConnectsThePads() {
    assertEquals(0, incompletes(null));
  }

  /**
   * SH1 is not connected to the fill, TP1 is connected to a strip that SH1 cuts off the plane, and
   * the trace of J1 ends before the fill: three connections are missing.
   */
  @Test
  void theClearanceOfAPadOfTheNetCutsThePlane() throws IOException {
    assertEquals(3, incompletes(board()));
  }

  /**
   * With a solid connection, SH1 joins the strip and the rest of the plane. J1 stays alone: the end
   * of its trace is not on the fill, however close the fill is.
   */
  @Test
  void aTraceThatEndsInAThermalGapIsNotOnThePlane() throws IOException {
    assertEquals(1, incompletes(board("(zone_connect 0)", "(zone_connect 2)")));
  }

  /** With a thermal relief, the spokes of SH1 reach the fill on both sides of it. */
  @Test
  void theSpokesOfAPadJoinTheTwoSidesOfItsThermalGap() throws IOException {
    assertEquals(1, incompletes(board("(zone_connect 0)", "(zone_connect 1)")));
  }
}

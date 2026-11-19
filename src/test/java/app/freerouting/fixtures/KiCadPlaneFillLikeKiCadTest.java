package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.NetIncompletes;
import app.freerouting.io.kicad.KiCadBoardZones;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * Where the fill of a zone reaches, as the zone filler of KiCad decides it: one net per case, each
 * with its own zone on F.Cu, a reference pad REF in the open fill and a probe pad P.
 *
 * <p>{@code Plane-fill-like-kicad-2layer.{kicad_pcb,dsn}} were built and filled in KiCad 10.0.8,
 * and the DSN exported by KiCad; the expected counts are the ones of its zone filler.
 *
 * <ul>
 *   <li>MUT: two round through-hole pads at a pitch of 1.27 mm, spokes at 0 and 90 degrees. Vias of
 *       another net cut three spokes of PM1; its fourth spoke ends in the relief of PM2, whose
 *       spoke ends in the relief of PM1: KiCad keeps both, and PM1 is connected through PM2;
 *   <li>FINE: a solid connection to a pad 0.25 mm wide between two foreign pads at a pitch of 0.5
 *       mm: the fill does not pass between them, it touches the pad at its ends;
 *   <li>EDGE: a pocket that a U-shaped foreign trace closes, open towards the edge of the board.
 *       Between the ends of the trace and the edge the fill would need to come within 0.5 mm of the
 *       edge, which KiCad does not allow;
 *   <li>RIM: two squares of zone joined by a bridge 1.4 mm high, with a foreign via in its middle
 *       that leaves 0.2 mm of fill to the outline on each side, less than the minimum width;
 *   <li>NECK: a zone of 170 mm, so that the grid has cells of about 0.33 mm, cut by a wall of
 *       foreign traces whose round ends leave a passage of 0.2 mm;
 *   <li>TRK: a pad outside the zone, with a trace of the net that ends 0.1 mm short of the outline;
 *   <li>CRN: a zone with a solid connection whose corners KiCad rounds with a radius of 2 mm, and a
 *       pad of 0.3 mm in a corner, inside the outline but outside the arc.
 * </ul>
 */
class KiCadPlaneFillLikeKiCadTest extends RoutingFixtureTest {

  private static final String BOARD = "Plane-fill-like-kicad-2layer";

  /** Loads the board with the KiCad board, without routing anything. */
  private RoutingBoard load(KiCadBoardZones zones) {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(BOARD + ".dsn", settings);
    job.kicadBoardZones = zones;
    runRoutingJob(job);
    assertEquals(RoutingJobState.COMPLETED, job.state);
    return job.board;
  }

  /** The KiCad board of the fixture, with {@code from} replaced by {@code to} when given. */
  private static KiCadBoardZones board(String... replacements) throws IOException {
    String text =
        Files.readString(TestFixtures.resolvePath(BOARD + ".kicad_pcb"), StandardCharsets.UTF_8);
    for (int i = 0; i + 1 < replacements.length; i += 2) {
      assertTrue(text.contains(replacements[i]), "the fixture has no '" + replacements[i] + "'");
      text = text.replace(replacements[i], replacements[i + 1]);
    }
    return KiCadBoardZones.parse(text, "test.kicad_pcb");
  }

  private static int incompletes(RoutingBoard board, String netName) {
    int net = board.rules.nets.get(netName, 1).netNumber;
    return new NetIncompletes(net, board.getConnectableItems(net), board).count();
  }

  /**
   * Without the KiCad board, the DSN alone says that these planes connect their pads (the passages
   * of RIM and NECK are closed by the clearance of the DSN already, and the trace of TRK ends off
   * the plane).
   */
  @Test
  void withoutTheKiCadBoardThePlanesConnectTheirPads() {
    RoutingBoard board = load(null);
    for (String net : new String[] {"MUT", "FINE", "EDGE"}) {
      assertEquals(0, incompletes(board, net), net);
    }
  }

  @Test
  void twoSpokesThatEndInEachOthersReliefJoinTheirPads() throws IOException {
    assertEquals(0, incompletes(load(board()), "MUT"));
  }

  /** Once PM2 is not connected to the zone, the only spoke left to PM1 ends in no fill. */
  @Test
  void aSpokeThatEndsInTheClearanceOfAPadWithoutConnectionIsLost() throws IOException {
    String text =
        Files.readString(TestFixtures.resolvePath(BOARD + ".kicad_pcb"), StandardCharsets.UTF_8);
    // the pad of the footprint PM2
    int footprint = text.indexOf("(property \"Reference\" \"PM2\"");
    int angle = text.indexOf("(thermal_bridge_angle 90)", footprint);
    assertTrue(footprint > 0 && angle > footprint, "the fixture has no pad PM2");
    text = text.substring(0, angle) + "(zone_connect 0)\n\t\t\t" + text.substring(angle);
    assertEquals(2, incompletes(load(KiCadBoardZones.parse(text, "test.kicad_pcb")), "MUT"));
  }

  @Test
  void aSolidPadBetweenCloseForeignPadsIsTouchedAtItsEnds() throws IOException {
    assertEquals(0, incompletes(load(board()), "FINE"));
  }

  @Test
  void theFillKeepsItsDistanceToTheEdgeOfTheBoard() throws IOException {
    assertEquals(1, incompletes(load(board()), "EDGE"));
  }

  @Test
  void theFillKeepsHalfItsMinimumWidthInsideTheOutlineOfTheZone() throws IOException {
    assertEquals(1, incompletes(load(board()), "RIM"));
  }

  @Test
  void aPassageNarrowerThanTheMinimumWidthIsClosedOnCoarseCells() throws IOException {
    assertEquals(1, incompletes(load(board()), "NECK"));
  }

  @Test
  void aTraceThatEndsShortOfTheOutlineIsNotOnThePlane() throws IOException {
    assertEquals(1, incompletes(load(board()), "TRK"));
  }

  @Test
  void theFillFollowsTheRoundedCornersOfTheOutline() throws IOException {
    assertEquals(1, incompletes(load(board()), "CRN"));
  }

  /** With sharp corners, the fill reaches the pad in the corner. */
  @Test
  void theFillReachesASharpCorner() throws IOException {
    assertEquals(0, incompletes(load(board("(smoothing fillet)", "(smoothing none)")), "CRN"));
  }
}

package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.io.kicad.KiCadBoardDrawings;
import app.freerouting.io.kicad.KiCadBoardZones;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The Specctra export of KiCad leaves out the copper without net: drawings of the footprints and of
 * the board, and texts. The KiCad board read with the design puts it back as obstacles.
 *
 * <p>{@code Netless-copper-garland-4layer.dsn}, made by KiCad 10.0.8 from the {@code .kicad_pcb}
 * next to it: 4 signal layers, six nets N1, N2, N7, N10, N13, N14, each between two pads at x = 5
 * and x = 65 mm on its row. On the straight line between them, at x = 35 mm, a barrier of copper
 * without net 6 mm high: N1 an {@code fp_line}, N2 an {@code fp_arc}, N7 a filled {@code gr_poly}
 * (pads on the back), N10 a {@code gr_text} turned by 90 degrees, each on the four layers; N13 a
 * {@code gr_line} on In1.Cu and In2.Cu, with F.Cu and B.Cu closed by a rule area (a keepout in the
 * DSN). N14 is the control: a filled {@code fp_rect} without net that extends its first pad.
 */
class KiCadNetlessCopperTest extends RoutingFixtureTest {

  private static final String BOARD = "Netless-copper-garland-4layer.dsn";

  private static final String KICAD_BOARD = "Netless-copper-garland-4layer.kicad_pcb";

  /** The rows of the barriers: net name to the y of KiCad, in mm. */
  private static final Map<String, Double> BARRIERS =
      Map.of("N1", 7.0, "N2", 17.0, "N7", 27.0, "N10", 37.0, "N13", 47.0);

  private RoutingJob route(String kicadBoard) throws IOException {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(20);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(BOARD, settings);
    if (kicadBoard != null) {
      useKiCadBoard(job, kicadBoard);
    }
    runRoutingJob(job);
    assertEquals(RoutingJobState.COMPLETED, job.state);
    return job;
  }

  /** Gives the job the KiCad board, as {@code -de} does: its zones and its copper drawings. */
  private static void useKiCadBoard(RoutingJob job, String kicadBoard) throws IOException {
    Path path = TestFixtures.resolvePath(kicadBoard);
    job.kicadBoardZones = KiCadBoardZones.read(path);
    job.kicadBoardDrawings = KiCadBoardDrawings.read(path);
  }

  /**
   * The places where a net crosses its barrier: a trace that crosses the line x = 35 mm, or a via
   * near it, less than 2.8 mm from the row (the arc of N2 reaches 2.83 mm on that line).
   */
  private static List<String> crossings(RoutingJob job) {
    List<String> result = new ArrayList<>();
    var transform = job.board.communication.coordinateTransform;
    for (Trace trace : job.board.getTraces()) {
      String net = job.board.rules.nets.get(trace.getNetNumber(0)).name;
      Double row = BARRIERS.get(net);
      if (row == null || !(trace instanceof PolylineTrace polylineTrace)) {
        continue;
      }
      FloatPoint[] corners = polylineTrace.polyline().cornerApproxArr();
      for (int i = 0; i + 1 < corners.length; i++) {
        double[] a = transform.boardToDsn(corners[i]);
        double[] b = transform.boardToDsn(corners[i + 1]);
        if ((a[0] - 35000) * (b[0] - 35000) > 0 || a[0] == b[0]) {
          continue;
        }
        double y = a[1] + (b[1] - a[1]) * (35000 - a[0]) / (b[0] - a[0]);
        // the y axis of the DSN points up
        if (Math.abs(-y / 1000 - row) < 2.8) {
          result.add(net + " trace on layer " + trace.getLayer() + " at y " + (-y / 1000));
        }
      }
    }
    for (Via via : job.board.getVias()) {
      String net = job.board.rules.nets.get(via.getNetNumber(0)).name;
      Double row = BARRIERS.get(net);
      double[] centre = transform.boardToDsn(via.getCenter().toFloat());
      if (row != null
          && Math.abs(centre[0] - 35000) < 2500
          && Math.abs(-centre[1] / 1000 - row) < 2.8) {
        result.add(net + " via at " + centre[0] / 1000 + ", " + (-centre[1] / 1000));
      }
    }
    return result;
  }

  /** Without the KiCad board, the nets are routed straight through their barriers. */
  @Test
  void withoutTheKiCadBoardTheNetsCrossTheirBarriers() throws IOException {
    RoutingJob job = route(null);

    assertTrue(crossings(job).size() >= 4, "crossings: " + crossings(job));
  }

  /** With it, every net goes around its barrier, and the control is still routed. */
  @Test
  void withTheKiCadBoardTheNetsGoAroundTheCopperWithoutNet() throws IOException {
    RoutingJob job = route(KICAD_BOARD);

    assertEquals(List.of(), crossings(job));
    assertEquals(0, new BoardStatistics(job.board).connections.incompleteCount);
  }

  /** The barrier of N13 is on the inner layers, and the control is an obstacle of its net. */
  @Test
  void theObstaclesAreOnTheLayersOfTheDrawingsAndTheControlIsOfItsNet() throws IOException {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(BOARD, settings);
    useKiCadBoard(job, KICAD_BOARD);
    runRoutingJob(job);

    var transform = job.board.communication.coordinateTransform;
    int innerAtN13 = 0;
    int ofN14 = 0;
    int n14 = job.board.rules.nets.get("N14", 1).netNumber;
    for (var item : job.board.getItems()) {
      if (!(item instanceof ObstacleArea area) || area.getComponentId() != 0) {
        continue;
      }
      double[] box = transform.boardToDsn(area.boundingBox());
      double x = (box[0] + box[2]) / 2000;
      double y = -(box[1] + box[3]) / 2000;
      if ((area.getLayer() == 1 || area.getLayer() == 2)
          && Math.abs(x - 35) < 1
          && Math.abs(y - 47) < 3.5) {
        innerAtN13++;
      }
      if (area.containsNet(n14)) {
        ofN14++;
      }
    }
    assertTrue(innerAtN13 >= 2, "no obstacle on In1.Cu and In2.Cu at the barrier of N13");
    assertTrue(ofN14 > 0, "the control rectangle is not of the net of the pad it touches");
  }

  /**
   * {@code Netless-copper-on-pad-2layer.dsn}, made by KiCad 10.0.8 from the {@code .kicad_pcb} next
   * to it: 2 layers, one net N5 between two pads at x = 6 and x = 34 mm. The first pad, 1.5 mm
   * square, is covered by a filled {@code fp_rect} without net of its footprint, 2.4 mm square,
   * with a stroke of 0.1 mm. Only the inside of the rectangle touches the pad; its outline, 0.45 mm
   * away from the pad, is of the same piece of copper and must not enclose the pad.
   */
  @Test
  void aFilledDrawingThatCoversAPadDoesNotEncloseIt() throws IOException {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(20);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob("Netless-copper-on-pad-2layer.dsn", settings);
    useKiCadBoard(job, "Netless-copper-on-pad-2layer.kicad_pcb");
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertEquals(0, new BoardStatistics(job.board).connections.incompleteCount);
    int n5 = job.board.rules.nets.get("N5", 1).netNumber;
    int areas = 0;
    for (var item : job.board.getItems()) {
      if (item instanceof ObstacleArea area && area.getComponentId() == 0) {
        areas++;
        assertTrue(area.containsNet(n5), "a piece of the rectangle is an obstacle for N5");
      }
    }
    // the inside and the four sides of the outline
    assertEquals(5, areas);
  }
}

package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@code Edge-pin-net-outline-garland.dsn}: a U-shaped board, 30 x 20 mm, with a 4 mm slot coming
 * down from the top edge. Nets EDGE and CTRL each join a pad left of the slot to a pad right of it.
 * EDGE also has a finger pad E that overhangs the top edge; CTRL, the control, has none.
 *
 * <p>KiCad lets the edge pad itself touch or cross Edge.Cuts, but applies the copper to edge
 * clearance to every track and via, including those of the pad's net. Freerouting exempted the
 * whole net of an edge pin from the outline, anywhere on the board: EDGE ran straight from A to B
 * through the slot, outside the board, while CTRL went around it.
 *
 * <p>Only the stretch of the outline next to the edge pad is exempt, for the pad's net, so that a
 * trace can reach the pad. {@code Edge-pin-net-centre-on-outline-garland.dsn} is the same board
 * with E a 1 x 1 mm pad centred on the top edge: Freerouting connects at the pad centre, so without
 * that exemption E could not be reached at all.
 */
class EdgePinNetOutlineTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Edge-pin-net-outline-garland.dsn";

  private static TestingSettings settings(boolean route) {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(5);
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(route);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  private static int netNumber(RoutingBoard board, String netName) {
    for (Net net : board.rules.nets.get(netName)) {
      return net.netNumber;
    }
    throw new IllegalStateException("net " + netName + " not found");
  }

  @Test
  void anEdgePinDoesNotExemptItsNetFromTheOutlineByDefault() {
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, settings(false)));
    BoardOutline outline = job.board.getOutline();
    int edge = netNumber(job.board, "EDGE");
    int ctrl = netNumber(job.board, "CTRL");

    assertTrue(outline.isTraceObstacle(edge), "EDGE traces must keep clear of the outline");
    assertTrue(outline.isTraceObstacle(ctrl), "CTRL traces must keep clear of the outline");

    outline.edgePinNetsCrossOutline = true;
    assertFalse(outline.isTraceObstacle(edge), "the opt-in restores the net-wide exemption");
    assertTrue(outline.isTraceObstacle(ctrl), "the opt-in only concerns edge-pin nets");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"Edge-pin-net-outline-garland.dsn", "Edge-pin-net-centre-on-outline-garland.dsn"})
  void noTraceOfTheEdgePinNetLeavesTheBoard(String fixture) {
    RoutingJob job = runRoutingJob(getRoutingJob(fixture, settings(true)));
    RoutingBoard board = job.board;
    BoardOutline outline = board.getOutline();
    int edge = netNumber(board, "EDGE");
    IntBox edgePad = null;
    for (Pin pin : board.getPins()) {
      if ("E".equals(pin.componentName())) {
        edgePad = pin.getShape(0).boundingBox();
      }
    }
    assertTrue(edgePad != null, "the fixture has the edge pad E");

    int edgeTraces = 0;
    for (Trace trace : board.getTraces()) {
      if (!trace.containsNet(edge) || !(trace instanceof PolylineTrace polylineTrace)) {
        continue;
      }
      edgeTraces++;
      Polyline polyline = polylineTrace.polyline();
      for (int i = 0; i + 1 < polyline.cornerCount(); i++) {
        FloatPoint from = polyline.cornerApprox(i);
        FloatPoint to = polyline.cornerApprox(i + 1);
        for (int step = 0; step <= 50; step++) {
          double t = step / 50.0;
          FloatPoint point =
              new FloatPoint(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t);
          boolean onThePad =
              point.x >= edgePad.ll.x
                  && point.x <= edgePad.ur.x
                  && point.y >= edgePad.ll.y
                  && point.y <= edgePad.ur.y;
          assertTrue(
              outline.contains(point) || onThePad,
              "EDGE trace leaves the board at " + point + " (segment " + from + " -> " + to + ")");
        }
      }
    }
    assertTrue(edgeTraces > 0, "EDGE must be routed");
    List<ClearanceViolation> traceOutlineViolations =
        new DesignRulesChecker(board, null)
            .getAllClearanceViolations().stream()
                .filter(
                    v ->
                        (v.firstItem instanceof Trace && v.secondItem instanceof BoardOutline)
                            || (v.secondItem instanceof Trace
                                && v.firstItem instanceof BoardOutline))
                .toList();
    assertEquals(
        0,
        traceOutlineViolations.size(),
        "routed traces must keep copper-to-edge clearance from the board outline");
    assertEquals(
        0,
        getBoardStatistics(job).connections.incompleteCount,
        "both nets, the finger pad included, must be routed inside the board");
  }

  @Test
  void checkTraceShapeBlocksMultiNetTieTraceAtEdgeWindow() {
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, settings(false)));
    RoutingBoard board = job.board;
    int edge = netNumber(board, "EDGE");
    int ctrl = netNumber(board, "CTRL");
    Pin edgePin = findEdgePin(board);
    IntBox padBox = edgePin.getShape(0).boundingBox();
    int layer = edgePin.firstLayer();
    int clClass = board.rules.nets.get(edge).getNetClass().getTraceClearanceClass();
    int halfWidth = board.rules.getTraceHalfWidth(edge, layer);
    int centerX = (padBox.ll.x + padBox.ur.x) / 2;
    IntPoint from = new IntPoint(centerX, padBox.ll.y - halfWidth * 2);
    IntPoint to = new IntPoint(centerX, padBox.ur.y);
    TileShape segment = new Polyline(from, to).offsetShape(halfWidth, 0);

    assertTrue(
        board.checkTraceShape(segment, layer, new int[] {edge}, clClass, Set.of(edgePin)),
        "single-net EDGE trace must be allowed inside the edge-pin window");
    assertFalse(
        board.checkTraceShape(segment, layer, new int[] {edge, ctrl}, clClass, Set.of(edgePin)),
        "tie trace carrying an ordinary net must be blocked at the edge-pin window");
  }

  @Test
  void checkTraceSegmentExemptsEdgePinWindow() {
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, settings(false)));
    RoutingBoard board = job.board;
    int edge = netNumber(board, "EDGE");
    int ctrl = netNumber(board, "CTRL");
    Pin edgePin = findEdgePin(board);
    IntBox padBox = edgePin.getShape(0).boundingBox();
    int layer = edgePin.firstLayer();
    int clClass = board.rules.nets.get(edge).getNetClass().getTraceClearanceClass();
    int halfWidth = board.rules.getTraceHalfWidth(edge, layer);
    int centerX = (padBox.ll.x + padBox.ur.x) / 2;
    IntPoint from = new IntPoint(centerX, padBox.ll.y - 5000);
    IntPoint to = new IntPoint(centerX, padBox.ur.y);

    double edgeOkLength =
        board.checkTraceSegment(from, to, layer, new int[] {edge}, halfWidth, clClass, false);
    double ctrlOkLength =
        board.checkTraceSegment(from, to, layer, new int[] {ctrl}, halfWidth, clClass, false);

    assertTrue(
        edgeOkLength > to.toFloat().distance(from.toFloat()),
        "EDGE trace segment inside the edge-pin window must not be blocked by the outline");
    assertTrue(
        ctrlOkLength < to.toFloat().distance(from.toFloat()),
        "CTRL trace segment crossing the outline at the edge-pin window must be blocked");
  }

  @Test
  void movingEdgePinUpdatesOutlineExemptionWindows() {
    RoutingJob job = runRoutingJob(getRoutingJob(FIXTURE, settings(false)));
    RoutingBoard board = job.board;
    int edge = netNumber(board, "EDGE");
    Pin edgePin = findEdgePin(board);
    IntBox oldBox = edgePin.getShape(0).boundingBox();
    int layer = edgePin.firstLayer();
    int clClass = board.rules.nets.get(edge).getNetClass().getTraceClearanceClass();
    int halfWidth = board.rules.getTraceHalfWidth(edge, layer);

    IntVector shift = new IntVector(20000, 0);
    edgePin.moveBy(shift);
    IntBox newBox = edgePin.getShape(0).boundingBox();

    int oldCenterX = (oldBox.ll.x + oldBox.ur.x) / 2;
    int newCenterX = (newBox.ll.x + newBox.ur.x) / 2;
    TileShape oldSegment =
        new Polyline(
                new IntPoint(oldCenterX, oldBox.ll.y - halfWidth * 2),
                new IntPoint(oldCenterX, oldBox.ur.y))
            .offsetShape(halfWidth, 0);
    TileShape newSegment =
        new Polyline(
                new IntPoint(newCenterX, newBox.ll.y - halfWidth * 2),
                new IntPoint(newCenterX, newBox.ur.y))
            .offsetShape(halfWidth, 0);

    assertFalse(
        board.checkTraceShape(oldSegment, layer, new int[] {edge}, clClass, Set.of()),
        "old edge-pin location must no longer be exempt after moving the pin");
    assertTrue(
        board.checkTraceShape(newSegment, layer, new int[] {edge}, clClass, Set.of(edgePin)),
        "new edge-pin location must be exempt after moving the pin");
  }

  private static Pin findEdgePin(RoutingBoard board) {
    for (Pin pin : board.getPins()) {
      if ("E".equals(pin.componentName())) {
        return pin;
      }
    }
    throw new IllegalStateException("edge pin E not found");
  }
}

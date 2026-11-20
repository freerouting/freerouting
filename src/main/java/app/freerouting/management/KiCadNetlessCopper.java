package app.freerouting.management;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Unit;
import app.freerouting.board.state.Communication;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.io.kicad.KiCadBoardDrawings;
import app.freerouting.io.kicad.KiCadCopperGraphic;
import app.freerouting.rules.DefaultItemClearanceClasses;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Inserts the copper drawings without net of a KiCad board as obstacles.
 *
 * <p>The Specctra export of KiCad leaves out the drawings and texts on copper layers that have no
 * net, of the board and of its footprints: Freerouting routes through them, and the KiCad DRC then
 * reports shorts and clearance violations. Each drawing read from the {@code .kicad_pcb} becomes
 * obstacle areas on its layers, with the clearance class of the keepouts: its filled inside if it
 * has one, and one area per segment of its stroke.
 *
 * <p>A drawing that touches a pin, a trace or a via is connected to it in copper: its areas get the
 * nets of what they touch, so that they stay an obstacle for the other nets only. This covers net
 * ties and drawings that extend a pad.
 */
final class KiCadNetlessCopper {

  /** The number of segments of a half circle around the end of a stroke. */
  private static final int CAP_SEGMENTS = 4;

  private KiCadNetlessCopper() {}

  /** The counts of an insertion, for the log. */
  record Result(int drawings, int areas, int connected, int skipped) {}

  static Result insert(RoutingBoard board, KiCadBoardDrawings kicadBoard) {
    Communication communication = board.communication;
    int clearanceClass =
        board
            .rules
            .getDefaultNetClass()
            .defaultItemClearanceClasses
            .get(DefaultItemClearanceClasses.ItemClass.AREA);
    List<String> kicadLayers = kicadBoard.copperLayers();
    int drawings = 0;
    int areas = 0;
    int connected = 0;
    int skipped = 0;
    for (KiCadCopperGraphic graphic : kicadBoard.netlessCopper()) {
      FloatPoint[] corners = new FloatPoint[graphic.cornerCount()];
      for (int i = 0; i < corners.length; i++) {
        // the y axis of KiCad points down, the one of the DSN up
        corners[i] =
            communication.coordinateTransform.dsnToBoard(
                new double[] {
                  Unit.scale(graphic.xUm()[i], Unit.UM, communication.unit),
                  Unit.scale(-graphic.yUm()[i], Unit.UM, communication.unit)
                });
      }
      double halfWidth = communication.physicalToBoard(graphic.widthUm() / 2.0, Unit.UM);
      List<PolygonShape> pieces = pieces(corners, halfWidth, graphic.filled(), graphic.closed());
      boolean inserted = false;
      for (String layerName : graphic.layers()) {
        int layer = board.layerStructure.getNo(layerName);
        if (layer < 0 && kicadLayers.size() == board.getLayerCount()) {
          // a layer renamed in the board: the DSN names it by its user name
          layer = kicadLayers.indexOf(layerName);
        }
        if (layer < 0) {
          continue;
        }
        for (PolygonShape piece : pieces) {
          int[] nets = netsTouching(board, piece, layer);
          if (nets.length > 0) {
            connected++;
            board.insertObstacle(
                piece, layer, nets, clearanceClass, 0, null, FixedState.SYSTEM_FIXED);
          } else {
            board.insertObstacle(piece, layer, clearanceClass, FixedState.SYSTEM_FIXED);
          }
          areas++;
          inserted = true;
        }
      }
      if (inserted) {
        drawings++;
      } else {
        skipped++;
      }
    }
    return new Result(drawings, areas, connected, skipped);
  }

  /**
   * The areas of a drawing in board coordinates: its inside if it is filled, then a stroke of
   * halfWidth around each of its segments.
   */
  static List<PolygonShape> pieces(
      FloatPoint[] corners, double halfWidth, boolean filled, boolean closed) {
    List<PolygonShape> result = new ArrayList<>();
    if (filled && corners.length >= 3) {
      PolygonShape inside = polygon(corners);
      if (inside != null) {
        result.add(inside);
      }
    }
    if (halfWidth > 0 || result.isEmpty()) {
      // a filled drawing whose inside is degenerate keeps at least its outline
      double half = Math.max(halfWidth, 1.0);
      int segments = closed ? corners.length : corners.length - 1;
      for (int i = 0; i < segments; i++) {
        result.add(stroke(corners[i], corners[(i + 1) % corners.length], half));
      }
      if (corners.length == 1) {
        result.add(stroke(corners[0], corners[0], half));
      }
    }
    return result;
  }

  /**
   * The polygon of these corners; its convex hull if it cannot be split into convex pieces (a
   * polygon that crosses itself), null if it is degenerate.
   */
  private static PolygonShape polygon(FloatPoint[] corners) {
    Point[] points = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      points[i] = corners[i].round();
    }
    PolygonShape shape = new PolygonShape(points);
    if (shape.borderLineCount() < 3 || shape.isEmpty()) {
      return null;
    }
    if (shape.splitToConvex() == null) {
      PolygonShape hull = shape.convexHull();
      return hull.borderLineCount() >= 3 && hull.splitToConvex() != null ? hull : null;
    }
    return shape;
  }

  /**
   * The convex area that encloses the stroke of halfWidth from a to b with round ends: half circles
   * of {@link #CAP_SEGMENTS} segments around the corners, outside the true round end.
   */
  private static PolygonShape stroke(FloatPoint a, FloatPoint b, double halfWidth) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double length = Math.hypot(dx, dy);
    double direction = length > 0 ? Math.atan2(dy, dx) : 0;
    // circumscribed, plus one unit for the rounding of the corners
    double radius = halfWidth / Math.cos(Math.PI / (2 * CAP_SEGMENTS)) + 1;
    List<Point> points = new ArrayList<>();
    for (int end = 0; end < 2; end++) {
      FloatPoint centre = end == 0 ? b : a;
      double start = direction - Math.PI / 2 + end * Math.PI;
      for (int i = 0; i <= CAP_SEGMENTS; i++) {
        double angle = start + i * Math.PI / CAP_SEGMENTS;
        points.add(
            new IntPoint(
                (int) Math.round(centre.x + radius * Math.cos(angle)),
                (int) Math.round(centre.y + radius * Math.sin(angle))));
      }
    }
    return new PolygonShape(points.toArray(new Point[0]));
  }

  /** The nets of the pins, traces and vias that this area touches on this layer, in order. */
  private static int[] netsTouching(RoutingBoard board, PolygonShape area, int layer) {
    TreeSet<Integer> nets = new TreeSet<>();
    for (Item item : board.overlappingItems(area, layer)) {
      if (item instanceof Pin || item instanceof Trace || item instanceof Via) {
        for (int i = 0; i < item.netCount(); i++) {
          nets.add(item.getNetNumber(i));
        }
      }
    }
    return nets.stream().mapToInt(Integer::intValue).toArray();
  }
}

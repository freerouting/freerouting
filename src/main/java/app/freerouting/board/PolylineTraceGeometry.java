package app.freerouting.board;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;

/**
 * Geometry-only operations used by {@link PolylineTrace}.
 *
 * <p>This collaborator deliberately has no board or trace state. Keeping the geometry value on
 * {@code PolylineTrace} preserves its serialized shape while making the small, frequently reused
 * geometry calculations explicit.
 */
final class PolylineTraceGeometry {

  private PolylineTraceGeometry() {}

  static Point firstCorner(Polyline lines) {
    return lines.corner(0);
  }

  static Point lastCorner(Polyline lines) {
    return lines.corner(lines.lines.length - 2);
  }

  static int cornerCount(Polyline lines) {
    return lines.lines.length - 1;
  }

  static double length(Polyline lines) {
    return lines.lengthApprox();
  }

  static IntBox boundingBox(Polyline lines, int halfWidth) {
    return lines.boundingBox().offset(halfWidth);
  }

  static int tileShapeCount(Polyline lines) {
    return Math.max(lines.lines.length - 2, 0);
  }

  static Polyline translate(Polyline lines, Vector vector) {
    return lines.translateBy(vector);
  }

  static Polyline turn90Degree(Polyline lines, int factor, IntPoint pole) {
    return lines.turn90Degree(factor, pole);
  }

  static Polyline rotateApprox(Polyline lines, double angleInDegree, FloatPoint pole) {
    return lines.rotateApprox(Math.toRadians(angleInDegree), pole);
  }

  static Polyline mirrorVertical(Polyline lines, IntPoint pole) {
    return lines.mirrorVertical(pole);
  }

  static TileShape connectionShape(Polyline lines, int index) {
    LineSegment currentLineSegment = new LineSegment(lines, index + 1);
    return currentLineSegment.toSimplex().simplify();
  }
}

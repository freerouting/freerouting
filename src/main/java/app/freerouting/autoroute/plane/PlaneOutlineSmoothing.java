package app.freerouting.autoroute.plane;

import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
import java.util.ArrayList;
import java.util.List;

/**
 * The outline of a zone with its corners smoothed the way KiCad smooths it before the fill ({@code
 * SHAPE_POLY_SET::Fillet} and {@code Chamfer}): every corner of the border is replaced by an arc of
 * the given radius tangent to its two sides, or cut at the given distance along both sides, and the
 * distance along a side is at most half its length. The holes are kept as they are.
 */
public final class PlaneOutlineSmoothing {

  /** The largest distance of the chords of a fillet to its arc, in board units (5 um). */
  private static final double MAX_ARC_ERROR = 50.0;

  private PlaneOutlineSmoothing() {}

  /**
   * The area with the corners of its border smoothed, or the area itself if its border is not a
   * polygon.
   *
   * @param area the outline of the zone
   * @param fillets true for fillets, false for chamfers
   * @param distance the radius of the fillets or the distance of the chamfers, in board units
   */
  public static Area smooth(Area area, boolean fillets, double distance) {
    if (distance <= 0 || !(area.getBorder() instanceof PolylineShape border)) {
      return area;
    }
    FloatPoint[] corners = border.cornerApproxArr();
    if (corners == null || corners.length < 3) {
      return area;
    }
    List<IntPoint> result = new ArrayList<>();
    int n = corners.length;
    for (int i = 0; i < n; i++) {
      FloatPoint previous = corners[(i + n - 1) % n];
      FloatPoint corner = corners[i];
      FloatPoint next = corners[(i + 1) % n];
      double lengthA = corner.distance(previous);
      double lengthB = corner.distance(next);
      if (lengthA <= 0 || lengthB <= 0) {
        continue;
      }
      double ax = (previous.x - corner.x) / lengthA;
      double ay = (previous.y - corner.y) / lengthA;
      double bx = (next.x - corner.x) / lengthB;
      double by = (next.y - corner.y) / lengthB;
      double cosine = Math.max(-1.0, Math.min(1.0, ax * bx + ay * by));
      double angle = Math.acos(cosine);
      if (angle < 1e-6 || Math.PI - angle < 1e-6) {
        // the two sides are parallel: nothing to smooth
        result.add(corner.round());
        continue;
      }
      double along;
      double radius = 0;
      if (fillets) {
        along = distance / Math.tan(angle / 2.0);
      } else {
        along = distance;
      }
      along = Math.min(along, 0.5 * Math.min(lengthA, lengthB));
      if (fillets) {
        radius = along * Math.tan(angle / 2.0);
      }
      FloatPoint start = new FloatPoint(corner.x + ax * along, corner.y + ay * along);
      FloatPoint end = new FloatPoint(corner.x + bx * along, corner.y + by * along);
      result.add(start.round());
      if (fillets && radius > 0) {
        double bisectorX = ax + bx;
        double bisectorY = ay + by;
        double bisector = Math.hypot(bisectorX, bisectorY);
        double centreDistance = radius / Math.sin(angle / 2.0);
        FloatPoint centre =
            new FloatPoint(
                corner.x + bisectorX / bisector * centreDistance,
                corner.y + bisectorY / bisector * centreDistance);
        double from = Math.atan2(start.y - centre.y, start.x - centre.x);
        double to = Math.atan2(end.y - centre.y, end.x - centre.x);
        double sweep = to - from;
        while (sweep > Math.PI) {
          sweep -= 2 * Math.PI;
        }
        while (sweep < -Math.PI) {
          sweep += 2 * Math.PI;
        }
        double step = 2.0 * Math.acos(Math.max(0.0, 1.0 - MAX_ARC_ERROR / radius));
        int segments = step > 0 ? Math.max(1, (int) Math.ceil(Math.abs(sweep) / step)) : 1;
        for (int k = 1; k < segments; k++) {
          double phi = from + sweep * k / segments;
          result.add(
              new FloatPoint(centre.x + radius * Math.cos(phi), centre.y + radius * Math.sin(phi))
                  .round());
        }
      }
      result.add(end.round());
    }
    // consecutive equal corners would make a degenerate side
    List<Point> points = new ArrayList<>();
    for (IntPoint point : result) {
      if (points.isEmpty() || !points.get(points.size() - 1).equals(point)) {
        points.add(point);
      }
    }
    if (points.size() > 1 && points.get(0).equals(points.get(points.size() - 1))) {
      points.remove(points.size() - 1);
    }
    if (points.size() < 3) {
      return area;
    }
    PolygonShape smoothed = new PolygonShape(points.toArray(new Point[0]));
    Shape[] holes = area.getHoles();
    List<PolylineShape> polylineHoles = new ArrayList<>();
    for (Shape hole : holes) {
      if (hole instanceof PolylineShape polylineHole) {
        polylineHoles.add(polylineHole);
      }
    }
    return new PolylineArea(smoothed, polylineHoles.toArray(new PolylineShape[0]));
  }
}

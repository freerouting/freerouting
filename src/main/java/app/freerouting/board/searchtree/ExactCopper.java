package app.freerouting.board.searchtree;

import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;

/**
 * Copper as a convex core grown by a radius. A circle is its centre grown by its radius; a pad
 * polygon or a trace tile is its polygon grown by nothing (or by a tree's clearance compensation
 * and a drill's hole envelope). The search trees keep copper as a bounding tile, an octagon on a
 * 45-degree board, which reaches past a circle at 22.5 degrees by 8% of its radius and past a
 * turned pad's slanted edges by up to tan(22.5 degrees) of half the edge. The clearance query keeps
 * the tiles as its fast test and measures this shape where they overlap, so a legal position is not
 * refused because of the tile.
 */
public final class ExactCopper {

  private final FloatPoint[] corners;
  private final TileShape polygon;
  private final FloatPoint centre;
  public final double radius;

  private ExactCopper(TileShape polygon, FloatPoint centre, double radius) {
    this.polygon = polygon;
    this.corners = polygon != null ? polygon.cornerApproxArr() : null;
    this.centre = centre;
    this.radius = radius;
  }

  /** The copper of {@code shape} grown by {@code grow}; null for a shape without a convex core. */
  public static ExactCopper of(Shape shape) {
    return of(shape, 0);
  }

  /** The copper of {@code shape} grown by {@code grow}; null for a shape without a convex core. */
  public static ExactCopper of(Shape shape, double grow) {
    if (shape instanceof Circle circle) {
      return new ExactCopper(null, circle.center.toFloat(), circle.radius + grow);
    }
    if (shape instanceof TileShape tile
        && !tile.isEmpty()
        && tile.isBounded()
        && tile.borderLineCount() > 0) {
      return new ExactCopper(tile, FloatPoint.ZERO, grow);
    }
    return null;
  }

  /** The distance between the copper of {@code a} and {@code b}, 0 where they overlap. */
  public static double distance(ExactCopper a, ExactCopper b) {
    double core;
    if (a.corners == null && b.corners == null) {
      core = a.centre.distance(b.centre);
    } else if (a.corners == null) {
      core = pointDistance(b.corners, a.centre);
    } else if (b.corners == null) {
      core = pointDistance(a.corners, b.centre);
    } else {
      core = polygonDistance(a, b);
    }
    return Math.max(0, core - a.radius - b.radius);
  }

  private static double polygonDistance(ExactCopper a, ExactCopper b) {
    if (a.polygon.intersects(b.polygon)) {
      return 0;
    }
    double result = Double.MAX_VALUE;
    for (FloatPoint corner : a.corners) {
      result = Math.min(result, pointDistance(b.corners, corner));
    }
    for (FloatPoint corner : b.corners) {
      result = Math.min(result, pointDistance(a.corners, corner));
    }
    return result;
  }

  /** The distance of {@code point} to the convex polygon with these corners, 0 inside it. */
  public static double pointDistance(FloatPoint[] corners, FloatPoint point) {
    int count = corners.length;
    if (count == 1) {
      return corners[0].distance(point);
    }
    boolean left = false;
    boolean right = false;
    double best = Double.MAX_VALUE;
    FloatPoint previous = corners[count - 1];
    for (int i = 0; i < count; i++) {
      FloatPoint current = corners[i];
      double ex = current.x - previous.x;
      double ey = current.y - previous.y;
      double px = point.x - previous.x;
      double py = point.y - previous.y;
      double cross = ex * py - ey * px;
      if (cross > 0) {
        left = true;
      } else if (cross < 0) {
        right = true;
      }
      double lengthSquare = ex * ex + ey * ey;
      double t = lengthSquare > 0 ? Math.clamp((px * ex + py * ey) / lengthSquare, 0, 1) : 0;
      double dx = px - t * ex;
      double dy = py - t * ey;
      best = Math.min(best, Math.sqrt(dx * dx + dy * dy));
      previous = current;
    }
    return count <= 2 || (left && right) ? best : 0;
  }
}

package app.freerouting.board.searchtree;

import app.freerouting.core.library.PadOval;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;

/**
 * Copper as a convex core grown by a radius. A circle is its centre grown by its radius, an oval
 * pad its centre line grown by its half width; a pad polygon or a trace tile is its polygon grown
 * by nothing (or by a tree's clearance compensation and a drill's hole envelope). The search trees
 * keep copper as a bounding tile, an octagon on a 45-degree board, which reaches past a circle at
 * 22.5 degrees by 8% of its radius and past a turned pad's slanted edges by up to tan(22.5 degrees)
 * of half the edge. The clearance query keeps the tiles as its fast test and measures this shape
 * where they overlap, so a legal position is not refused because of the tile.
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

  /** A segment grown by a radius: the copper of an oval pad. */
  private ExactCopper(FloatPoint[] segment, double radius) {
    this.polygon = null;
    this.corners = segment;
    this.centre = segment[0];
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

  /**
   * The copper of a round or oval pad, every point within its radius of its centre line, grown by
   * {@code grow}. The pad's board shape is a polygon around it, which reaches past the round ends.
   */
  public static ExactCopper ofOval(PadOval oval, double grow) {
    if (oval.start().x == oval.end().x && oval.start().y == oval.end().y) {
      return new ExactCopper(null, oval.start(), oval.radius() + grow);
    }
    return new ExactCopper(new FloatPoint[] {oval.start(), oval.end()}, oval.radius() + grow);
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
    } else if (a.polygon != null && b.polygon != null) {
      core = polygonDistance(a, b);
    } else {
      core = segmentDistance(a, b);
    }
    return Math.max(0, core - a.radius - b.radius);
  }

  /**
   * The distance between two cores, one of them at least a segment: 0 where they cross, else the
   * smallest distance from a corner of one to the other, where the distance between two convex sets
   * that do not meet is reached.
   */
  private static double segmentDistance(ExactCopper a, ExactCopper b) {
    ExactCopper segment = a.polygon == null ? a : b;
    ExactCopper other = segment == a ? b : a;
    FloatPoint start = segment.corners[0];
    FloatPoint end = segment.corners[1];
    int count = other.corners.length;
    FloatPoint previous = other.corners[count - 1];
    for (int i = 0; i < count; i++) {
      FloatPoint current = other.corners[i];
      if (segmentsCross(start, end, previous, current)) {
        return 0;
      }
      previous = current;
    }
    double result = Double.MAX_VALUE;
    for (FloatPoint corner : segment.corners) {
      result = Math.min(result, pointDistance(other.corners, corner));
    }
    for (FloatPoint corner : other.corners) {
      result = Math.min(result, pointDistance(segment.corners, corner));
    }
    return result;
  }

  /** Whether the segments p1-p2 and q1-q2 cross at a point inside both. */
  private static boolean segmentsCross(FloatPoint p1, FloatPoint p2, FloatPoint q1, FloatPoint q2) {
    double d1 = cross(q1, q2, p1);
    double d2 = cross(q1, q2, p2);
    double d3 = cross(p1, p2, q1);
    double d4 = cross(p1, p2, q2);
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
  }

  private static double cross(FloatPoint from, FloatPoint to, FloatPoint point) {
    return (to.x - from.x) * (point.y - from.y) - (to.y - from.y) * (point.x - from.x);
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

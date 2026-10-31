package app.freerouting.autoroute.path;

import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Constructs bounded, deterministic staircases inside a convex room. */
final class RestrictedAngleRoomPath {
  // Bound geometric construction work, independently of job timeout or machine load.
  private static final int MAX_SUBDIVISIONS = 4096;
  private int subdivisions;

  private RestrictedAngleRoomPath() {}

  /**
   * Returns corners after from, including to, or null when construction fails. Room boundaries are
   * inset except where an existing entry/exit point needs access to a door or terminal. The result
   * stays inside the room apart from the existing shared-door grid rounding allowance. Insertion
   * still checks actual copper clearances and contacts.
   */
  static List<IntPoint> connect(
      TileShape room, int inset, IntPoint from, IntPoint to, AngleRestriction angle) {
    if (!room.contains(from) || !room.contains(to)) {
      // Door points are rounded by the existing locator. A rounded point can fall across an
      // oblique partition by at most half a grid diagonal. Preserve that shared-door rounding
      // allowance, without moving endpoints or changing any copper-clearance check.
      double roundingRadius = Math.sqrt(0.5);
      if (room.nearestPointApprox(from.toFloat()).distance(from.toFloat()) > roundingRadius
          || room.nearestPointApprox(to.toFloat()).distance(to.toFloat()) > roundingRadius) {
        return null;
      }
      room = (TileShape) room.offset(1);
      if (!room.contains(from) || !room.contains(to)) {
        return null;
      }
    }
    Line[] borders = new Line[room.borderLineCount()];
    for (int i = 0; i < borders.length; i++) {
      Line border = room.borderLine(i);
      double wallInset = inset;
      if (angle == AngleRestriction.NINETY_DEGREE) {
        // An orthogonal trace has square corner caps, not a circular footprint. Its support
        // along a slanted wall is r * (|nx| + |ny|), so a Euclidean inset can admit a bad elbow.
        double dx = border.b.toFloat().x - border.a.toFloat().x;
        double dy = border.b.toFloat().y - border.a.toFloat().y;
        wallInset *= (Math.abs(dx) + Math.abs(dy)) / Math.hypot(dx, dy);
      }
      double distance =
          Math.max(
              0,
              Math.min(
                  wallInset,
                  Math.min(
                      -border.signedDistance(from.toFloat()),
                      -border.signedDistance(to.toFloat()))));
      Line shifted = border.translate(distance);
      // Interior is the right half-plane (TileShape.isOutside), so signedDistance is negative
      // there and a positive translate moves the border inward. Integer supports can still step
      // past an endpoint; ON_THE_LEFT means that endpoint would leave the room, so keep the border.
      borders[i] =
          shifted.sideOf(from) == Side.ON_THE_LEFT || shifted.sideOf(to) == Side.ON_THE_LEFT
              ? border
              : shifted;
    }
    TileShape allowed = Simplex.getInstance(borders);
    var result = new ArrayList<IntPoint>();
    return new RestrictedAngleRoomPath().append(allowed, from, to, angle, result) ? result : null;
  }

  private boolean append(
      TileShape room, IntPoint from, IntPoint to, AngleRestriction angle, List<IntPoint> result) {
    if (++subdivisions > MAX_SUBDIVISIONS) {
      return false;
    }
    if (from.equals(to)) {
      return true;
    }
    IntPoint bestElbow = null;
    double bestMargin = Double.NEGATIVE_INFINITY;
    for (boolean horizontalFirst : new boolean[] {true, false}) {
      IntPoint elbow =
          FoundConnectionLocator.calculateAdditionalCorner(
                  from.toFloat(), to.toFloat(), horizontalFirst, angle)
              .round();
      if (room.contains(elbow)) {
        double margin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < room.borderLineCount(); i++) {
          margin = Math.min(margin, -room.borderLine(i).signedDistance(elbow.toFloat()));
        }
        if (bestElbow == null || margin > bestMargin) {
          bestElbow = elbow;
          bestMargin = margin;
        }
      }
    }
    if (bestElbow != null) {
      if (!bestElbow.equals(from)) {
        result.add(bestElbow);
      }
      if (!bestElbow.equals(to)) {
        result.add(to);
      }
      return true;
    }

    double midX = ((double) from.x + to.x) / 2;
    double midY = ((double) from.y + to.y) / 2;
    var midpoints = new ArrayList<IntPoint>();
    for (int x : new int[] {(int) Math.floor(midX), (int) Math.ceil(midX)}) {
      for (int y : new int[] {(int) Math.floor(midY), (int) Math.ceil(midY)}) {
        IntPoint point = new IntPoint(x, y);
        if (!point.equals(from)
            && !point.equals(to)
            && room.contains(point)
            && !midpoints.contains(point)) {
          midpoints.add(point);
        }
      }
    }
    midpoints.sort(
        Comparator.comparingDouble(
                (IntPoint p) -> (p.x - midX) * (p.x - midX) + (p.y - midY) * (p.y - midY))
            .thenComparingInt(p -> p.x)
            .thenComparingInt(p -> p.y));
    for (IntPoint midpoint : midpoints) {
      int previousSize = result.size();
      if (append(room, from, midpoint, angle, result)
          && append(room, midpoint, to, angle, result)) {
        return true;
      }
      result.subList(previousSize, result.size()).clear();
    }
    return false;
  }
}

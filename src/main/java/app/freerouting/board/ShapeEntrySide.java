package app.freerouting.board;

import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;

/** CalcFromSide. */
public class ShapeEntrySide {

  public static final ShapeEntrySide NOT_CALCULATED = new ShapeEntrySide(-1, null);
  final int no;
  FloatPoint borderIntersection;

  /**
   * Calculates the number of the edge line of shape where polyline enters.
   *
   * <p>Used in the push trace algorithm to determine the shove direction. no is expected between 1
   * and polyline.lineCount - 2 inclusive.
   */
  ShapeEntrySide(Polyline polyline, int no, TileShape shape) {
    int fromsideNo = -1;
    FloatPoint intersection = null;
    boolean borderIntersectionFound = false;
    // calculate the edgeIndex of shape, where polyline enters
    for (int currentNo = no; currentNo > 0; currentNo--) {
      LineSegment currentSeg = new LineSegment(polyline, currentNo);
      int[] intersections = currentSeg.borderIntersections(shape);
      if (intersections.length > 0) {
        fromsideNo = intersections[0];
        intersection = currentSeg.getLine().intersectionApprox(shape.borderLine(fromsideNo));
        borderIntersectionFound = true;
        break;
      }
    }
    if (!borderIntersectionFound) {
      // The first corner of polyline is inside shape.
      // Calculate the nearest intersection point of polyline.arr[1]
      // with the border of shape to the first corner of polyline
      FloatPoint fromPoint = polyline.cornerApprox(0);
      Line checkLine = polyline.arr[1];
      double minDist = Double.MAX_VALUE;
      int edgeCount = shape.borderLineCount();
      for (int i = 0; i < edgeCount; i++) {
        Line currentLine = shape.borderLine(i);
        FloatPoint currentIntersection = checkLine.intersectionApprox(currentLine);
        double currentDistance = Math.abs(currentIntersection.distance(fromPoint));
        if (currentDistance < minDist) {
          fromsideNo = i;
          intersection = currentIntersection;
          minDist = currentDistance;
        }
      }
    }
    this.no = fromsideNo;
    this.borderIntersection = intersection;
  }

  /**
   * Calculates the nearest border side of shape to fromPoint. Used in the shove_drill_item
   * algorithm to determine the shove direction.
   */
  ShapeEntrySide(Point fromPoint, TileShape shape) {
    Point borderProjection = shape.nearestBorderPoint(fromPoint);
    this.no = shape.containsOnBorderLineNo(borderProjection);
    if (this.no < 0) {
      FRLogger.warn("CalcFromSide: this.no >= 0 expected");
    }
    this.borderIntersection = borderProjection.toFloat();
  }

  /**
   * Calculates the Side of shape at the start of lineSegment. If shoveToTheLeft, the fromSideIndex
   * is decremented by 2, else it is increased by 2.
   */
  ShapeEntrySide(LineSegment lineSegment, TileShape shape, boolean shoveToTheLeft) {
    FloatPoint startCorner = lineSegment.startPointApprox();
    FloatPoint endCorner = lineSegment.endPointApprox();
    int borderLineCount = shape.borderLineCount();
    Line checkLine = lineSegment.getLine();
    FloatPoint firstCorner = shape.cornerApprox(0);
    Side prevSide = checkLine.sideOf(firstCorner);
    int frontSideNo = -1;

    for (int i = 1; i <= borderLineCount; i++) {
      FloatPoint nextCorner;
      if (i == borderLineCount) {
        nextCorner = firstCorner;
      } else {
        nextCorner = shape.cornerApprox(i);
      }
      Side nextSide = checkLine.sideOf(nextCorner);
      if (prevSide != nextSide) {
        FloatPoint currentIntersection = shape.borderLine(i - 1).intersectionApprox(checkLine);
        if (currentIntersection.distanceSquare(startCorner)
            < currentIntersection.distanceSquare(endCorner)) {
          frontSideNo = i - 1;
          break;
        }
      }
      prevSide = nextSide;
    }
    if (frontSideNo < 0) {
      // Fallback: find the nearest side of the shape to the start point of the line segment
      startCorner = lineSegment.startPointApprox();
      double minDistance = Double.MAX_VALUE;
      int nearestSide = 0;

      // Check each side of the shape
      for (int i = 0; i < borderLineCount; i++) {
        FloatLine borderLine =
            new FloatLine(shape.borderLine(i).a.toFloat(), shape.borderLine(i).b.toFloat());
        FloatPoint projection = borderLine.perpendicularProjection(startCorner);

        // Only consider if projection is on the line segment
        FloatPoint sideStart = shape.cornerApprox(i);
        FloatPoint sideEnd = shape.cornerApprox((i + 1) % borderLineCount);
        if (projection.isContainedInBox(sideStart, sideEnd, 0.01)) {
          double distance = startCorner.distance(projection);
          if (distance < minDistance) {
            minDistance = distance;
            nearestSide = i;
            this.borderIntersection = projection;
          }
        }
      }

      // Apply the same shove direction logic as the original code
      if (shoveToTheLeft) {
        this.no = (nearestSide + 2) % borderLineCount;
      } else {
        this.no = (nearestSide + borderLineCount - 2) % borderLineCount;
      }

      // Update border intersection to be the middle of the chosen side
      FloatPoint prevCorner = shape.cornerApprox(this.no);
      FloatPoint nextCorner = shape.cornerApprox((this.no + 1) % borderLineCount);
      this.borderIntersection = prevCorner.middlePoint(nextCorner);
      return;
    }
    if (shoveToTheLeft) {
      this.no = (frontSideNo + 2) % borderLineCount;
    } else {
      this.no = (frontSideNo + borderLineCount - 2) % borderLineCount;
    }
    FloatPoint prevCorner = shape.cornerApprox(this.no);
    FloatPoint nextCorner = shape.cornerApprox((this.no + 1) % borderLineCount);
    this.borderIntersection = prevCorner.middlePoint(nextCorner);
  }

  /** Values already calculated. Just create an instance from them. */
  ShapeEntrySide(int no, FloatPoint borderIntersection) {
    this.no = no;
    this.borderIntersection = borderIntersection;
  }
}

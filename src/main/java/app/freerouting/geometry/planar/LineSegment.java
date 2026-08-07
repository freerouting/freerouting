package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * Implements functionality for line segments. The difference between a LineSegment and a Line is,
 * that a Line is infinite and a LineSegment has a start and an endpoint.
 */
public class LineSegment implements Serializable {

  private final Line start;
  private final Line middle;
  private final Line end;
  private transient Point precalculatedStartPoint;
  private transient Point precalculatedEndPoint;

  /**
   * Creates a line segment from the 3 input lines. It starts at the intersection of p_start_line
   * and p_middle_line and ends at the intersection of p_middle_line and p_end_line. p_start_line
   * and p_end_line must not be parallel to p_middle_line.
   */
  public LineSegment(Line p_start_line, Line p_middle_line, Line p_end_line) {
    start = p_start_line;
    middle = p_middle_line;
    end = p_end_line;
  }

  /**
   * creates the p_no-th line segment of p_polyline for p_no between 1 and p_polyline.lineCount - 2.
   */
  public LineSegment(Polyline p_polyline, int p_no) {
    if (p_no <= 0 || p_no >= p_polyline.arr.length - 1) {
      FRLogger.warn("LineSegment from Polyline: p_no out of range");
      start = null;
      middle = null;
      end = null;
      return;
    }
    start = p_polyline.arr[p_no - 1];
    middle = p_polyline.arr[p_no];
    end = p_polyline.arr[p_no + 1];
  }

  /** Creates the p_no-th line segment of p_shape for p_no between 0 and p_shape.lineCount - 1. */
  public LineSegment(PolylineShape p_shape, int p_no) {
    int lineCount = p_shape.border_line_count();
    if (p_no < 0 || p_no >= lineCount) {
      FRLogger.warn("LineSegment from TileShape: p_no out of range");
      start = null;
      middle = null;
      end = null;
      return;
    }
    if (p_no == 0) {
      start = p_shape.border_line(lineCount - 1);
    } else {
      start = p_shape.border_line(p_no - 1);
    }
    middle = p_shape.border_line(p_no);
    if (p_no == lineCount - 1) {
      end = p_shape.border_line(0);
    } else {
      end = p_shape.border_line(p_no + 1);
    }
  }

  /** Returns the intersection of the first 2 lines of this segment */
  public Point start_point() {
    if (precalculatedStartPoint == null) {
      precalculatedStartPoint = middle.intersection(start);
    }
    return precalculatedStartPoint;
  }

  /** Returns the intersection of the last 2 lines of this segment */
  public Point end_point() {
    if (precalculatedEndPoint == null) {
      precalculatedEndPoint = middle.intersection(end);
    }
    return precalculatedEndPoint;
  }

  /** Returns an approximation of the intersection of the first 2 lines of this segment */
  public FloatPoint start_point_approx() {
    FloatPoint result;
    if (precalculatedStartPoint != null) {
      result = precalculatedStartPoint.to_float();
    } else {
      result = this.start.intersection_approx(this.middle);
    }
    return result;
  }

  /** Returns an approximation of the intersection of the last 2 lines of this segment */
  public FloatPoint end_point_approx() {
    FloatPoint result;
    if (precalculatedEndPoint != null) {
      result = precalculatedEndPoint.to_float();
    } else {
      result = this.end.intersection_approx(this.middle);
    }
    return result;
  }

  /** Returns the (infinite) line of this segment. */
  public Line get_line() {
    return middle;
  }

  /** Returns the start closing line of this segment. */
  public Line get_start_closing_line() {
    return start;
  }

  /** Returns the end closing line of this segment. */
  public Line get_end_closing_line() {
    return end;
  }

  /** Returns the line segment with the opposite direction. */
  public LineSegment opposite() {
    return new LineSegment(end.opposite(), middle.opposite(), start.opposite());
  }

  /** Transforms this LineSegment into a polyline of length 3. */
  public Polyline to_polyline() {
    Line[] lines = new Line[3];
    lines[0] = start;
    lines[1] = middle;
    lines[2] = end;
    return new Polyline(lines);
  }

  /**
   * Creates a 1 dimensional simplex rom this line segment, which has the same shape as the line
   * segment.
   */
  public Simplex to_simplex() {
    Line[] lineArr = new Line[4];
    if (this.end_point().side_of(this.start) == Side.ON_THE_RIGHT) {
      lineArr[0] = this.start.opposite();
    } else {
      lineArr[0] = this.start;
    }
    lineArr[1] = this.middle;
    lineArr[2] = this.middle.opposite();
    if (this.start_point().side_of(this.end) == Side.ON_THE_RIGHT) {
      lineArr[3] = this.end.opposite();
    } else {
      lineArr[3] = this.end;
    }
    return Simplex.get_instance(lineArr);
  }

  /** Checks if p_point is contained in this line segment */
  public boolean contains(Point p_point) {
    if (!(p_point instanceof IntPoint)) {
      FRLogger.warn("LineSegments.contains currently only implemented for IntPoints");
      return false;
    }
    if (middle.side_of(p_point) != Side.COLLINEAR) {
      return false;
    }
    // create a perpendicular line at p_point and check, that the two
    // endpoints of this segment are on different sides of that line.
    Direction perpendicularDirection = middle.direction().turn_45_degree(2);
    Line perpendicularLine = new Line(p_point, perpendicularDirection);
    Side startPointSide = perpendicularLine.side_of(this.start_point());
    Side endPointSide = perpendicularLine.side_of(this.end_point());
    return startPointSide != endPointSide || startPointSide == Side.COLLINEAR;
  }

  /** calculates the smallest surrounding box of this line segment */
  public IntBox bounding_box() {
    FloatPoint startCorner = middle.intersection_approx(start);
    FloatPoint endCorner = middle.intersection_approx(end);
    double llx = Math.min(startCorner.x, endCorner.x);
    double lly = Math.min(startCorner.y, endCorner.y);
    double urx = Math.max(startCorner.x, endCorner.x);
    double ury = Math.max(startCorner.y, endCorner.y);
    IntPoint lowerLeft = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
    IntPoint upperRight = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
    return new IntBox(lowerLeft, upperRight);
  }

  /** calculates the smallest surrounding octagon of this line segment */
  public IntOctagon bounding_octagon() {
    FloatPoint startCorner = middle.intersection_approx(start);
    FloatPoint endCorner = middle.intersection_approx(end);
    double lx = Math.floor(Math.min(startCorner.x, endCorner.x));
    double ly = Math.floor(Math.min(startCorner.y, endCorner.y));
    double rx = Math.ceil(Math.max(startCorner.x, endCorner.x));
    double uy = Math.ceil(Math.max(startCorner.y, endCorner.y));
    double startXMinusY = startCorner.x - startCorner.y;
    double endXMinusY = endCorner.x - endCorner.y;
    double ulx = Math.floor(Math.min(startXMinusY, endXMinusY));
    double lrx = Math.ceil(Math.max(startXMinusY, endXMinusY));
    double startXPlusY = startCorner.x + startCorner.y;
    double endXPlusY = endCorner.x + endCorner.y;
    double llx = Math.floor(Math.min(startXPlusY, endXPlusY));
    double urx = Math.ceil(Math.max(startXPlusY, endXPlusY));
    IntOctagon result =
        new IntOctagon(
            (int) lx, (int) ly, (int) rx, (int) uy, (int) ulx, (int) lrx, (int) llx, (int) urx);
    return result.normalize();
  }

  /**
   * Creates a new line segment with the same start and middle line and an end line, so that the
   * length of the new line segment is about p_new_length.
   */
  public LineSegment change_length_approx(double p_new_length) {
    FloatPoint newEndPoint = start_point_approx().change_length(end_point_approx(), p_new_length);
    Direction perpendicularDirection = this.middle.direction().turn_45_degree(2);
    Line newEndLine = new Line(newEndPoint.round(), perpendicularDirection);
    return new LineSegment(this.start, this.middle, newEndLine);
  }

  /**
   * Looks up the intersections of this line segment with p_other. The result array may have length
   * 0, 1 or 2. If the segments do not intersect the result array will have length 0. The result
   * lines are so that the intersections of the result lines with this line segment will deliver the
   * intersection points. If the segments overlap, the result array has length 2 and the
   * intersection points are the first and the last overlap point. Otherwise, the result array has
   * length 1 and the intersection point is the unique intersection or touching point. The result is
   * not symmetric in this and p_other, because intersecting lines and not the intersection points
   * are returned.
   */
  public Line[] intersection(LineSegment p_other) {
    if (!this.bounding_box().intersects(p_other.bounding_box())) {
      return new Line[0];
    }
    Side startPointSide = start_point().side_of(p_other.middle);
    Side endPointSide = end_point().side_of(p_other.middle);
    if (startPointSide == Side.COLLINEAR && endPointSide == Side.COLLINEAR) {
      // there may be an overlap
      LineSegment thisSorted = this.sort_endpoints_in_x_y();
      LineSegment otherSorted = p_other.sort_endpoints_in_x_y();
      LineSegment leftLine;
      LineSegment rightLine;
      if (thisSorted.start_point().compare_x_y(otherSorted.start_point()) <= 0) {
        leftLine = thisSorted;
        rightLine = otherSorted;
      } else {
        leftLine = otherSorted;
        rightLine = thisSorted;
      }
      int cmp = leftLine.end_point().compare_x_y(rightLine.start_point());
      if (cmp < 0) {
        // end point of the left line is to the left of the start point of the right line
        return new Line[0];
      }
      if (cmp == 0) {
        // end point of the left line is equal to the start point of the right line
        Line[] result = new Line[1];
        result[0] = leftLine.end;
        return result;
      }
      // now there is a real overlap
      Line[] result = new Line[2];
      result[0] = rightLine.start;
      if (rightLine.end_point().compare_x_y(leftLine.end_point()) >= 0) {
        result[1] = leftLine.end;
      } else {
        result[1] = rightLine.end;
      }
      return result;
    }
    if (startPointSide == endPointSide
        || p_other.start_point().side_of(this.middle) == p_other.end_point().side_of(this.middle)) {
      return new Line[0]; // no intersection possible
    }
    // now both start points and both end points are on different sides of the middle
    // line of the other segment.
    Line[] result = new Line[1];
    result[0] = p_other.middle;
    return result;
  }

  /** Checks if this LineSegment and p_other contain a common point */
  public boolean intersects(LineSegment p_other) {
    Line[] intersections = this.intersection(p_other);
    return intersections.length > 0;
  }

  /**
   * Checks if this LineSegment and p_other contain a common LineSegment, which is not reduced to a
   * point.
   */
  public boolean overlaps(LineSegment p_other) {
    Line[] intersections = this.intersection(p_other);
    return intersections.length > 1;
  }

  /**
   * Constructs an approximation of this line segment by orthogonal stairs with integer coordinates.
   * The length of the stairs will be at most p_stair_width. If p_to_the_right, the stairs will be
   * to the right of this line segment, else to the left.
   */
  public IntPoint[] stair_approximation(double p_width, boolean p_to_the_right) {
    IntPoint startPoint = this.start_point().to_float().round();
    IntPoint endPoint = this.end_point().to_float().round();
    if (startPoint.equals(endPoint)) {
      return new IntPoint[0];
    }

    if (startPoint.x == endPoint.x || startPoint.y == endPoint.y) {
      IntPoint[] result = new IntPoint[2];
      result[0] = startPoint;
      result[1] = endPoint;
      return result;
    }

    int dx = endPoint.x - startPoint.x;
    int dy = endPoint.y - startPoint.y;
    int absDx = Math.abs(dx);
    int absDy = Math.abs(dy);
    boolean functionOfX = absDx >= absDy;
    // use otherwise function of y for better numerical  stability

    int stairWidth;
    int stairCount;

    if (functionOfX) {
      stairWidth = (int) Math.round((p_width * (double) absDx) / (double) absDy);
      stairCount = (absDx - 1) / stairWidth + 1;
      if (endPoint.x < startPoint.x) {
        stairWidth = -stairWidth;
      }
    } else {
      stairWidth = (int) Math.round((p_width * (double) absDy) / (double) absDx);
      stairCount = (absDy - 1) / stairWidth + 1;
      if (endPoint.y < startPoint.y) {
        stairWidth = -stairWidth;
      }
    }
    IntPoint[] result = new IntPoint[2 * stairCount + 1];

    result[0] = startPoint;
    double det = (double) dx * (double) dy;
    boolean changeXFirst = p_to_the_right && det > 0 || !p_to_the_right && det < 0;
    int currIndex = 0;

    int prevLinePointX = startPoint.x;
    int prevLinePointY = startPoint.y;
    for (int i = 1; i < stairCount; i++) {
      int currLinePointX;
      int currLinePointY;
      if (functionOfX) {
        currLinePointX = startPoint.x + i * stairWidth;
        currLinePointY = (int) Math.round(this.get_line().function_value_approx(currLinePointX));
      } else {
        currLinePointY = startPoint.y + i * stairWidth;
        currLinePointX =
            (int) Math.round(this.get_line().function_in_y_value_approx(currLinePointY));
      }
      ++currIndex;
      if (changeXFirst) {
        result[currIndex] = new IntPoint(currLinePointX, prevLinePointY);
      } else {
        result[currIndex] = new IntPoint(prevLinePointX, currLinePointY);
      }
      ++currIndex;
      result[currIndex] = new IntPoint(currLinePointX, currLinePointY);
      prevLinePointX = currLinePointX;
      prevLinePointY = currLinePointY;
    }
    ++currIndex;
    if (changeXFirst) {
      result[currIndex] = new IntPoint(endPoint.x, prevLinePointY);
    } else {
      result[currIndex] = new IntPoint(prevLinePointX, endPoint.y);
    }
    ++currIndex;
    result[currIndex] = endPoint;
    return result;
  }

  /**
   * Constructs an approximation of this line segment by 45 degree stairs with integer coordinates.
   * The length of the stairs will be at most p_stair_width. If p_to_the_right, the stairs will be
   * to the right of this line segment, else to the left.
   */
  public IntPoint[] stair_approximation_45(double p_width, boolean p_to_the_right) {
    IntPoint startPoint = this.start_point().to_float().round();
    IntPoint endPoint = this.end_point().to_float().round();
    if (startPoint.equals(endPoint)) {
      return new IntPoint[0];
    }
    IntVector delta = endPoint.difference_by(startPoint);
    if (delta.is_multiple_of_45_degree()) {
      IntPoint[] result = new IntPoint[2];
      result[0] = startPoint;
      result[1] = endPoint;
      return result;
    }
    IntVector absDelta = new IntVector(Math.abs(delta.x), Math.abs(delta.y));
    boolean functionOfX = absDelta.x >= absDelta.y;
    // use otherwise function of y for better numerical  stability
    double det = (double) delta.x * (double) delta.y;
    int stairWidth;
    int stairCount;
    if (functionOfX) {
      stairWidth = (int) Math.round((p_width * (double) absDelta.x) / (double) absDelta.y);
      stairCount = (absDelta.x - 1) / stairWidth + 1;
      if (endPoint.x < startPoint.x) {
        stairWidth = -stairWidth;
      }
    } else {
      stairWidth = (int) Math.round((p_width * (double) absDelta.y) / (double) absDelta.x);
      stairCount = (absDelta.y - 1) / stairWidth + 1;
      if (endPoint.y < startPoint.y) {
        stairWidth = -stairWidth;
      }
    }
    IntPoint[] result = new IntPoint[2 * stairCount + 1];
    result[0] = startPoint;
    IntPoint prevLinePoint = startPoint;
    int currIndex = 0;
    for (int i = 1; i <= stairCount; i++) {
      IntPoint currLinePoint;
      int currX;
      int currY;
      if (i == stairCount) {
        currLinePoint = endPoint;
      } else {
        if (functionOfX) {
          currX = startPoint.x + i * stairWidth;
          currY = (int) Math.round(this.get_line().function_value_approx(currX));
        } else {
          currY = startPoint.y + i * stairWidth;
          currX = (int) Math.round(this.get_line().function_value_approx(currY));
        }
        currLinePoint = new IntPoint(currX, currY);
      }
      if (functionOfX) {
        boolean diagonalFirst = p_to_the_right && det < 0 || !p_to_the_right && det > 0;

        if (diagonalFirst) {
          currX =
              prevLinePoint.x
                  + Signum.as_int(stairWidth) * Math.abs(currLinePoint.y - prevLinePoint.y);
          currY = currLinePoint.y;
        } else
        // horizontal first
        {
          currX =
              currLinePoint.x
                  - Signum.as_int(stairWidth) * Math.abs(currLinePoint.y - prevLinePoint.y);
          currY = prevLinePoint.y;
        }
      } else
      // function of y
      {
        boolean diagonalFirst = p_to_the_right && det > 0 || !p_to_the_right && det < 0;

        if (diagonalFirst) {
          currX = currLinePoint.x;
          currY =
              prevLinePoint.y
                  + Signum.as_int(stairWidth) * Math.abs(currLinePoint.x - prevLinePoint.x);
        } else {
          currX = prevLinePoint.x;
          currY =
              currLinePoint.y
                  - Signum.as_int(stairWidth) * Math.abs(currLinePoint.x - prevLinePoint.x);
        }
      }
      ++currIndex;
      result[currIndex] = new IntPoint(currX, currY);
      ++currIndex;
      result[currIndex] = currLinePoint;
      prevLinePoint = currLinePoint;
    }
    return result;
  }

  /**
   * Returns an array with the borderline numbers of p_shape, which are intersected by this line
   * segment. Intersections at an endpoint of this line segment are only counted, if the line
   * segment intersects with the interior of p_shape. The result array may have length 0, 1 or 2.
   * With 2 intersections the intersection which is nearest to the start point of the line segment
   * comes first.
   */
  public int[] border_intersections(TileShape p_shape) {
    int[] emptyResult = new int[0];
    if (!this.bounding_box().intersects(p_shape.bounding_box())) {
      return emptyResult;
    }

    int edgeCount = p_shape.border_line_count();
    Line prevLine = p_shape.border_line(edgeCount - 1);
    Line currLine = p_shape.border_line(0);
    int[] result = new int[2];
    Point[] intersection = new Point[2];
    int intersectionCount = 0;
    Point lineStart = this.start_point();
    Point lineEnd = this.end_point();

    for (int edgeLineNo = 0; edgeLineNo < edgeCount; edgeLineNo++) {
      Line nextLine;
      if (edgeLineNo == edgeCount - 1) {
        nextLine = p_shape.border_line(0);
      } else {
        nextLine = p_shape.border_line(edgeLineNo + 1);
      }

      Side startPointSide = currLine.side_of(lineStart);
      Side endPointSide = currLine.side_of(lineEnd);
      if (startPointSide == Side.ON_THE_LEFT && endPointSide == Side.ON_THE_LEFT) {
        // both endpoints are outside the borderLine,
        // no intersection possible
        return emptyResult;
      }

      if (startPointSide == Side.COLLINEAR) {
        // the start is on currLine, check that the end point is inside
        // the halfplane, because touches count only, if the interior
        // is entered
        if (endPointSide != Side.ON_THE_RIGHT) {
          return emptyResult;
        }
      }

      if (endPointSide == Side.COLLINEAR) {
        // the end is on currLine, check that the start point is inside
        // the halfplane, because touches count only, if the interior
        // is entered
        if (startPointSide != Side.ON_THE_RIGHT) {
          return emptyResult;
        }
      }

      if (startPointSide != Side.ON_THE_RIGHT || endPointSide != Side.ON_THE_RIGHT) {
        // not both points are inside the halplane defined by currLine
        Point is = this.middle.intersection(currLine);
        Side prevLineSideOfIs = prevLine.side_of(is);
        Side nextLineSideOfIs = nextLine.side_of(is);
        if (prevLineSideOfIs != Side.ON_THE_LEFT && nextLineSideOfIs != Side.ON_THE_LEFT) {
          // this line segment intersects currLine between the
          // previous and the next corner of p_simplex

          if (prevLineSideOfIs == Side.COLLINEAR) {
            // this line segment goes through the previous
            // corner of p_simplex. Check, that the intersection
            // isn't merely a touch.
            Point prevPrevCorner;
            if (edgeLineNo == 0) {
              prevPrevCorner = p_shape.corner(edgeCount - 1);
            } else {
              prevPrevCorner = p_shape.corner(edgeLineNo - 1);
            }

            Point nextCorner;
            if (edgeLineNo == edgeCount - 1) {
              nextCorner = p_shape.corner(0);
            } else {
              nextCorner = p_shape.corner(edgeLineNo + 1);
            }
            // check, that prevPrevCorner and nextCorner
            // are on different sides of this line segment.
            Side prevPrevCornerSide = this.middle.side_of(prevPrevCorner);
            Side nextCornerSide = this.middle.side_of(nextCorner);
            if (prevPrevCornerSide == Side.COLLINEAR
                || nextCornerSide == Side.COLLINEAR
                || prevPrevCornerSide == nextCornerSide) {
              return emptyResult;
            }
          }
          if (nextLineSideOfIs == Side.COLLINEAR) {
            // this line segment goes through the next
            // corner of p_simplex. Check, that the intersection
            // isn't merely a touch.
            Point prevCorner = p_shape.corner(edgeLineNo);
            Point nextNextCorner;

            if (edgeLineNo == edgeCount - 2) {
              nextNextCorner = p_shape.corner(0);
            } else if (edgeLineNo == edgeCount - 1) {
              nextNextCorner = p_shape.corner(1);
            } else {
              nextNextCorner = p_shape.corner(edgeLineNo + 2);
            }
            // check, that prevCorner and nextNextCorner
            // are on different sides of this line segment.
            Side prevCornerSide = this.middle.side_of(prevCorner);
            Side nextNextCornerSide = this.middle.side_of(nextNextCorner);
            if (prevCornerSide == Side.COLLINEAR
                || nextNextCornerSide == Side.COLLINEAR
                || prevCornerSide == nextNextCornerSide) {
              return emptyResult;
            }
          }
          boolean intersectionAlreadyHandled = false;
          for (int i = 0; i < intersectionCount; i++) {
            if (is.equals(intersection[i])) {
              intersectionAlreadyHandled = true;
              break;
            }
          }
          if (!intersectionAlreadyHandled) {
            if (intersectionCount < result.length) {
              // a new intersection is found
              result[intersectionCount] = edgeLineNo;
              intersection[intersectionCount] = is;
              ++intersectionCount;
            } else {
              FRLogger.warn(
                  "border_intersections: intersection_count ("
                      + intersectionCount
                      + ") is too big!");
            }
          }
        }
      }

      prevLine = currLine;
      currLine = nextLine;
    }

    if (intersectionCount == 0) {
      return emptyResult;
    }

    if (intersectionCount == 2) {
      // assure the correct order
      FloatPoint is0 = intersection[0].to_float();
      FloatPoint is1 = intersection[1].to_float();
      FloatPoint currStart = lineStart.to_float();
      if (currStart.distance_square(is1) < currStart.distance_square(is0))
      // swap the result points
      {
        int tmp = result[0];
        result[0] = result[1];
        result[1] = tmp;
      }

      return result;
    }

    if (intersectionCount != 1) {
      FRLogger.warn("LineSegment.border_intersections: intersectionCount 1 expected");
    }

    int[] normalisedResult = new int[1];
    normalisedResult[0] = result[0];
    return normalisedResult;
  }

  /**
   * Inverts the direction of this.middle, if start_point() has a bigger x coordinate than
   * end_point(), or an equal x coordinate and a bigger y coordinate.
   */
  public LineSegment sort_endpoints_in_x_y() {
    boolean swapEndlines = start_point().compare_x_y(end_point()) > 0;
    LineSegment result;

    if (swapEndlines) {
      result = new LineSegment(this.end, this.middle, this.start);
      result.precalculatedStartPoint = this.precalculatedEndPoint;
      result.precalculatedEndPoint = this.precalculatedStartPoint;
    } else {
      result = this;
    }

    return result;
  }
}

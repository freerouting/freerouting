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
  public LineSegment(Line pStartLine, Line pMiddleLine, Line pEndLine) {
    start = pStartLine;
    middle = pMiddleLine;
    end = pEndLine;
  }

  /**
   * creates the p_no-th line segment of p_polyline for p_no between 1 and p_polyline.lineCount - 2.
   */
  public LineSegment(Polyline pPolyline, int pNo) {
    if (pNo <= 0 || pNo >= pPolyline.arr.length - 1) {
      FRLogger.warn("LineSegment from Polyline: p_no out of range");
      start = null;
      middle = null;
      end = null;
      return;
    }
    start = pPolyline.arr[pNo - 1];
    middle = pPolyline.arr[pNo];
    end = pPolyline.arr[pNo + 1];
  }

  /** Creates the p_no-th line segment of p_shape for p_no between 0 and p_shape.lineCount - 1. */
  public LineSegment(PolylineShape pShape, int pNo) {
    int lineCount = pShape.borderLineCount();
    if (pNo < 0 || pNo >= lineCount) {
      FRLogger.warn("LineSegment from TileShape: p_no out of range");
      start = null;
      middle = null;
      end = null;
      return;
    }
    if (pNo == 0) {
      start = pShape.borderLine(lineCount - 1);
    } else {
      start = pShape.borderLine(pNo - 1);
    }
    middle = pShape.borderLine(pNo);
    if (pNo == lineCount - 1) {
      end = pShape.borderLine(0);
    } else {
      end = pShape.borderLine(pNo + 1);
    }
  }

  /** Returns the intersection of the first 2 lines of this segment */
  public Point startPoint() {
    if (precalculatedStartPoint == null) {
      precalculatedStartPoint = middle.intersection(start);
    }
    return precalculatedStartPoint;
  }

  /** Returns the intersection of the last 2 lines of this segment */
  public Point endPoint() {
    if (precalculatedEndPoint == null) {
      precalculatedEndPoint = middle.intersection(end);
    }
    return precalculatedEndPoint;
  }

  /** Returns an approximation of the intersection of the first 2 lines of this segment */
  public FloatPoint startPointApprox() {
    FloatPoint result;
    if (precalculatedStartPoint != null) {
      result = precalculatedStartPoint.toFloat();
    } else {
      result = this.start.intersectionApprox(this.middle);
    }
    return result;
  }

  /** Returns an approximation of the intersection of the last 2 lines of this segment */
  public FloatPoint endPointApprox() {
    FloatPoint result;
    if (precalculatedEndPoint != null) {
      result = precalculatedEndPoint.toFloat();
    } else {
      result = this.end.intersectionApprox(this.middle);
    }
    return result;
  }

  /** Returns the (infinite) line of this segment. */
  public Line getLine() {
    return middle;
  }

  /** Returns the start closing line of this segment. */
  public Line getStartClosingLine() {
    return start;
  }

  /** Returns the end closing line of this segment. */
  public Line getEndClosingLine() {
    return end;
  }

  /** Returns the line segment with the opposite direction. */
  public LineSegment opposite() {
    return new LineSegment(end.opposite(), middle.opposite(), start.opposite());
  }

  /** Transforms this LineSegment into a polyline of length 3. */
  public Polyline toPolyline() {
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
  public Simplex toSimplex() {
    Line[] lineArr = new Line[4];
    if (this.endPoint().sideOf(this.start) == Side.ON_THE_RIGHT) {
      lineArr[0] = this.start.opposite();
    } else {
      lineArr[0] = this.start;
    }
    lineArr[1] = this.middle;
    lineArr[2] = this.middle.opposite();
    if (this.startPoint().sideOf(this.end) == Side.ON_THE_RIGHT) {
      lineArr[3] = this.end.opposite();
    } else {
      lineArr[3] = this.end;
    }
    return Simplex.getInstance(lineArr);
  }

  /** Checks if p_point is contained in this line segment */
  public boolean contains(Point pPoint) {
    if (!(pPoint instanceof IntPoint)) {
      FRLogger.warn("LineSegments.contains currently only implemented for IntPoints");
      return false;
    }
    if (middle.sideOf(pPoint) != Side.COLLINEAR) {
      return false;
    }
    // create a perpendicular line at p_point and check, that the two
    // endpoints of this segment are on different sides of that line.
    Direction perpendicularDirection = middle.direction().turn45Degree(2);
    Line perpendicularLine = new Line(pPoint, perpendicularDirection);
    Side startPointSide = perpendicularLine.sideOf(this.startPoint());
    Side endPointSide = perpendicularLine.sideOf(this.endPoint());
    return startPointSide != endPointSide || startPointSide == Side.COLLINEAR;
  }

  /** calculates the smallest surrounding box of this line segment */
  public IntBox boundingBox() {
    FloatPoint startCorner = middle.intersectionApprox(start);
    FloatPoint endCorner = middle.intersectionApprox(end);
    double llx = Math.min(startCorner.x, endCorner.x);
    double lly = Math.min(startCorner.y, endCorner.y);
    double urx = Math.max(startCorner.x, endCorner.x);
    double ury = Math.max(startCorner.y, endCorner.y);
    IntPoint lowerLeft = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
    IntPoint upperRight = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
    return new IntBox(lowerLeft, upperRight);
  }

  /** calculates the smallest surrounding octagon of this line segment */
  public IntOctagon boundingOctagon() {
    FloatPoint startCorner = middle.intersectionApprox(start);
    FloatPoint endCorner = middle.intersectionApprox(end);
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
  public LineSegment changeLengthApprox(double pNewLength) {
    FloatPoint newEndPoint = startPointApprox().changeLength(endPointApprox(), pNewLength);
    Direction perpendicularDirection = this.middle.direction().turn45Degree(2);
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
  public Line[] intersection(LineSegment pOther) {
    if (!this.boundingBox().intersects(pOther.boundingBox())) {
      return new Line[0];
    }
    Side startPointSide = startPoint().sideOf(pOther.middle);
    Side endPointSide = endPoint().sideOf(pOther.middle);
    if (startPointSide == Side.COLLINEAR && endPointSide == Side.COLLINEAR) {
      // there may be an overlap
      LineSegment thisSorted = this.sortEndpointsInXY();
      LineSegment otherSorted = pOther.sortEndpointsInXY();
      LineSegment leftLine;
      LineSegment rightLine;
      if (thisSorted.startPoint().compareXY(otherSorted.startPoint()) <= 0) {
        leftLine = thisSorted;
        rightLine = otherSorted;
      } else {
        leftLine = otherSorted;
        rightLine = thisSorted;
      }
      int cmp = leftLine.endPoint().compareXY(rightLine.startPoint());
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
      if (rightLine.endPoint().compareXY(leftLine.endPoint()) >= 0) {
        result[1] = leftLine.end;
      } else {
        result[1] = rightLine.end;
      }
      return result;
    }
    if (startPointSide == endPointSide
        || pOther.startPoint().sideOf(this.middle) == pOther.endPoint().sideOf(this.middle)) {
      return new Line[0]; // no intersection possible
    }
    // now both start points and both end points are on different sides of the middle
    // line of the other segment.
    Line[] result = new Line[1];
    result[0] = pOther.middle;
    return result;
  }

  /** Checks if this LineSegment and p_other contain a common point */
  public boolean intersects(LineSegment pOther) {
    Line[] intersections = this.intersection(pOther);
    return intersections.length > 0;
  }

  /**
   * Checks if this LineSegment and p_other contain a common LineSegment, which is not reduced to a
   * point.
   */
  public boolean overlaps(LineSegment pOther) {
    Line[] intersections = this.intersection(pOther);
    return intersections.length > 1;
  }

  /**
   * Constructs an approximation of this line segment by orthogonal stairs with integer coordinates.
   * The length of the stairs will be at most p_stair_width. If p_to_the_right, the stairs will be
   * to the right of this line segment, else to the left.
   */
  public IntPoint[] stairApproximation(double pWidth, boolean pToTheRight) {
    IntPoint startPoint = this.startPoint().toFloat().round();
    IntPoint endPoint = this.endPoint().toFloat().round();
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
      stairWidth = (int) Math.round((pWidth * (double) absDx) / (double) absDy);
      stairCount = (absDx - 1) / stairWidth + 1;
      if (endPoint.x < startPoint.x) {
        stairWidth = -stairWidth;
      }
    } else {
      stairWidth = (int) Math.round((pWidth * (double) absDy) / (double) absDx);
      stairCount = (absDy - 1) / stairWidth + 1;
      if (endPoint.y < startPoint.y) {
        stairWidth = -stairWidth;
      }
    }
    IntPoint[] result = new IntPoint[2 * stairCount + 1];

    result[0] = startPoint;
    double det = (double) dx * (double) dy;
    boolean changeXFirst = pToTheRight && det > 0 || !pToTheRight && det < 0;
    int currIndex = 0;

    int prevLinePointX = startPoint.x;
    int prevLinePointY = startPoint.y;
    for (int i = 1; i < stairCount; i++) {
      int currLinePointX;
      int currLinePointY;
      if (functionOfX) {
        currLinePointX = startPoint.x + i * stairWidth;
        currLinePointY = (int) Math.round(this.getLine().functionValueApprox(currLinePointX));
      } else {
        currLinePointY = startPoint.y + i * stairWidth;
        currLinePointX = (int) Math.round(this.getLine().functionInYValueApprox(currLinePointY));
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
  public IntPoint[] stairApproximation45(double pWidth, boolean pToTheRight) {
    IntPoint startPoint = this.startPoint().toFloat().round();
    IntPoint endPoint = this.endPoint().toFloat().round();
    if (startPoint.equals(endPoint)) {
      return new IntPoint[0];
    }
    IntVector delta = endPoint.differenceBy(startPoint);
    if (delta.isMultipleOf45Degree()) {
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
      stairWidth = (int) Math.round((pWidth * (double) absDelta.x) / (double) absDelta.y);
      stairCount = (absDelta.x - 1) / stairWidth + 1;
      if (endPoint.x < startPoint.x) {
        stairWidth = -stairWidth;
      }
    } else {
      stairWidth = (int) Math.round((pWidth * (double) absDelta.y) / (double) absDelta.x);
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
          currY = (int) Math.round(this.getLine().functionValueApprox(currX));
        } else {
          currY = startPoint.y + i * stairWidth;
          currX = (int) Math.round(this.getLine().functionValueApprox(currY));
        }
        currLinePoint = new IntPoint(currX, currY);
      }
      if (functionOfX) {
        boolean diagonalFirst = pToTheRight && det < 0 || !pToTheRight && det > 0;

        if (diagonalFirst) {
          currX =
              prevLinePoint.x
                  + Signum.asInt(stairWidth) * Math.abs(currLinePoint.y - prevLinePoint.y);
          currY = currLinePoint.y;
        } else
        // horizontal first
        {
          currX =
              currLinePoint.x
                  - Signum.asInt(stairWidth) * Math.abs(currLinePoint.y - prevLinePoint.y);
          currY = prevLinePoint.y;
        }
      } else
      // function of y
      {
        boolean diagonalFirst = pToTheRight && det > 0 || !pToTheRight && det < 0;

        if (diagonalFirst) {
          currX = currLinePoint.x;
          currY =
              prevLinePoint.y
                  + Signum.asInt(stairWidth) * Math.abs(currLinePoint.x - prevLinePoint.x);
        } else {
          currX = prevLinePoint.x;
          currY =
              currLinePoint.y
                  - Signum.asInt(stairWidth) * Math.abs(currLinePoint.x - prevLinePoint.x);
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
  public int[] borderIntersections(TileShape pShape) {
    int[] emptyResult = new int[0];
    if (!this.boundingBox().intersects(pShape.boundingBox())) {
      return emptyResult;
    }

    int edgeCount = pShape.borderLineCount();
    Line prevLine = pShape.borderLine(edgeCount - 1);
    Line currLine = pShape.borderLine(0);
    int[] result = new int[2];
    Point[] intersection = new Point[2];
    int intersectionCount = 0;
    Point lineStart = this.startPoint();
    Point lineEnd = this.endPoint();

    for (int edgeLineNo = 0; edgeLineNo < edgeCount; edgeLineNo++) {
      Line nextLine;
      if (edgeLineNo == edgeCount - 1) {
        nextLine = pShape.borderLine(0);
      } else {
        nextLine = pShape.borderLine(edgeLineNo + 1);
      }

      Side startPointSide = currLine.sideOf(lineStart);
      Side endPointSide = currLine.sideOf(lineEnd);
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
        Side prevLineSideOfIs = prevLine.sideOf(is);
        Side nextLineSideOfIs = nextLine.sideOf(is);
        if (prevLineSideOfIs != Side.ON_THE_LEFT && nextLineSideOfIs != Side.ON_THE_LEFT) {
          // this line segment intersects currLine between the
          // previous and the next corner of p_simplex

          if (prevLineSideOfIs == Side.COLLINEAR) {
            // this line segment goes through the previous
            // corner of p_simplex. Check, that the intersection
            // isn't merely a touch.
            Point prevPrevCorner;
            if (edgeLineNo == 0) {
              prevPrevCorner = pShape.corner(edgeCount - 1);
            } else {
              prevPrevCorner = pShape.corner(edgeLineNo - 1);
            }

            Point nextCorner;
            if (edgeLineNo == edgeCount - 1) {
              nextCorner = pShape.corner(0);
            } else {
              nextCorner = pShape.corner(edgeLineNo + 1);
            }
            // check, that prevPrevCorner and nextCorner
            // are on different sides of this line segment.
            Side prevPrevCornerSide = this.middle.sideOf(prevPrevCorner);
            Side nextCornerSide = this.middle.sideOf(nextCorner);
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
            Point prevCorner = pShape.corner(edgeLineNo);
            Point nextNextCorner;

            if (edgeLineNo == edgeCount - 2) {
              nextNextCorner = pShape.corner(0);
            } else if (edgeLineNo == edgeCount - 1) {
              nextNextCorner = pShape.corner(1);
            } else {
              nextNextCorner = pShape.corner(edgeLineNo + 2);
            }
            // check, that prevCorner and nextNextCorner
            // are on different sides of this line segment.
            Side prevCornerSide = this.middle.sideOf(prevCorner);
            Side nextNextCornerSide = this.middle.sideOf(nextNextCorner);
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
      FloatPoint is0 = intersection[0].toFloat();
      FloatPoint is1 = intersection[1].toFloat();
      FloatPoint currStart = lineStart.toFloat();
      if (currStart.distanceSquare(is1) < currStart.distanceSquare(is0))
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
  public LineSegment sortEndpointsInXY() {
    boolean swapEndlines = startPoint().compareXY(endPoint()) > 0;
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

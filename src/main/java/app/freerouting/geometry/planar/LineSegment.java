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
   * Creates a line segment from the 3 input lines. It starts at the intersection of startLine and
   * middleLine and ends at the intersection of middleLine and endLine. startLine and endLine must
   * not be parallel to middleLine.
   */
  public LineSegment(Line startLine, Line middleLine, Line endLine) {
    start = startLine;
    middle = middleLine;
    end = endLine;
  }

  /** Creates the no-th line segment of polyline for no between 1 and polyline.lineCount - 2. */
  public LineSegment(Polyline polyline, int no) {
    if (no <= 0 || no >= polyline.lines.length - 1) {
      FRLogger.warn("LineSegment from Polyline: no out of range");
      start = null;
      middle = null;
      end = null;
      return;
    }
    start = polyline.lines[no - 1];
    middle = polyline.lines[no];
    end = polyline.lines[no + 1];
  }

  /** Creates the no-th line segment of shape for no between 0 and shape.lineCount - 1. */
  public LineSegment(PolylineShape shape, int no) {
    int lineCount = shape.borderLineCount();
    if (no < 0 || no >= lineCount) {
      FRLogger.warn("LineSegment from TileShape: no out of range");
      start = null;
      middle = null;
      end = null;
      return;
    }
    if (no == 0) {
      start = shape.borderLine(lineCount - 1);
    } else {
      start = shape.borderLine(no - 1);
    }
    middle = shape.borderLine(no);
    if (no == lineCount - 1) {
      end = shape.borderLine(0);
    } else {
      end = shape.borderLine(no + 1);
    }
  }

  /** Returns the intersection of the first 2 lines of this segment. */
  public Point startPoint() {
    if (precalculatedStartPoint == null) {
      precalculatedStartPoint = middle.intersection(start);
    }
    return precalculatedStartPoint;
  }

  /** Returns the intersection of the last 2 lines of this segment. */
  public Point endPoint() {
    if (precalculatedEndPoint == null) {
      precalculatedEndPoint = middle.intersection(end);
    }
    return precalculatedEndPoint;
  }

  /** Returns an approximation of the intersection of the first 2 lines of this segment. */
  public FloatPoint startPointApprox() {
    FloatPoint result;
    if (precalculatedStartPoint != null) {
      result = precalculatedStartPoint.toFloat();
    } else {
      result = this.start.intersectionApprox(this.middle);
    }
    return result;
  }

  /** Returns an approximation of the intersection of the last 2 lines of this segment. */
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
    Line[] lines = new Line[4];
    if (this.endPoint().sideOf(this.start) == Side.ON_THE_RIGHT) {
      lines[0] = this.start.opposite();
    } else {
      lines[0] = this.start;
    }
    lines[1] = this.middle;
    lines[2] = this.middle.opposite();
    if (this.startPoint().sideOf(this.end) == Side.ON_THE_RIGHT) {
      lines[3] = this.end.opposite();
    } else {
      lines[3] = this.end;
    }
    return Simplex.getInstance(lines);
  }

  /** Checks if point is contained in this line segment. */
  public boolean contains(Point point) {
    if (!(point instanceof IntPoint)) {
      FRLogger.warn("LineSegments.contains currently only implemented for IntPoints");
      return false;
    }
    if (middle.sideOf(point) != Side.COLLINEAR) {
      return false;
    }
    // create a perpendicular line at point and check, that the two
    // endpoints of this segment are on different sides of that line.
    Direction perpendicularDirection = middle.direction().turn45Degree(2);
    Line perpendicularLine = new Line(point, perpendicularDirection);
    Side startPointSide = perpendicularLine.sideOf(this.startPoint());
    Side endPointSide = perpendicularLine.sideOf(this.endPoint());
    return startPointSide != endPointSide || startPointSide == Side.COLLINEAR;
  }

  /** Calculates the smallest surrounding box of this line segment. */
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

  /** Calculates the smallest surrounding octagon of this line segment. */
  public IntOctagon boundingOctagon() {
    FloatPoint startCorner = middle.intersectionApprox(start);
    FloatPoint endCorner = middle.intersectionApprox(end);
    double lx = Math.floor(Math.min(startCorner.x, endCorner.x));
    double ly = Math.floor(Math.min(startCorner.y, endCorner.y));
    double rx = Math.ceil(Math.max(startCorner.x, endCorner.x));
    double uy = Math.ceil(Math.max(startCorner.y, endCorner.y));
    double startXminusY = startCorner.x - startCorner.y;
    double endXminusY = endCorner.x - endCorner.y;
    double ulx = Math.floor(Math.min(startXminusY, endXminusY));
    double lrx = Math.ceil(Math.max(startXminusY, endXminusY));
    double startXplusY = startCorner.x + startCorner.y;
    double endXplusY = endCorner.x + endCorner.y;
    double llx = Math.floor(Math.min(startXplusY, endXplusY));
    double urx = Math.ceil(Math.max(startXplusY, endXplusY));
    IntOctagon result =
        new IntOctagon(
            (int) lx, (int) ly, (int) rx, (int) uy, (int) ulx, (int) lrx, (int) llx, (int) urx);
    return result.normalize();
  }

  /**
   * Creates a new line segment with the same start and middle line and an end line, so that the
   * length of the new line segment is about newLength.
   */
  public LineSegment changeLengthApprox(double newLength) {
    FloatPoint newEndPoint = startPointApprox().changeLength(endPointApprox(), newLength);
    Direction perpendicularDirection = this.middle.direction().turn45Degree(2);
    Line newEndLine = new Line(newEndPoint.round(), perpendicularDirection);
    return new LineSegment(this.start, this.middle, newEndLine);
  }

  /**
   * Looks up the intersections of this line segment with other. The result array may have length 0,
   * 1 or 2. If the segments do not intersect the result array will have length 0. The result lines
   * are so that the intersections of the result lines with this line segment will deliver the
   * intersection points. If the segments overlap, the result array has length 2 and the
   * intersection points are the first and the last overlap point. Otherwise, the result array has
   * length 1 and the intersection point is the unique intersection or touching point. The result is
   * not symmetric in this and other, because intersecting lines and not the intersection points are
   * returned.
   */
  public Line[] intersection(LineSegment other) {
    if (!this.boundingBox().intersects(other.boundingBox())) {
      return new Line[0];
    }
    Side startPointSide = startPoint().sideOf(other.middle);
    Side endPointSide = endPoint().sideOf(other.middle);
    if (startPointSide == Side.COLLINEAR && endPointSide == Side.COLLINEAR) {
      // there may be an overlap
      LineSegment thisSorted = this.sortEndpointsInXY();
      LineSegment otherSorted = other.sortEndpointsInXY();
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
        || other.startPoint().sideOf(this.middle) == other.endPoint().sideOf(this.middle)) {
      return new Line[0]; // no intersection possible
    }
    // now both start points and both end points are on different sides of the middle
    // line of the other segment.
    Line[] result = new Line[1];
    result[0] = other.middle;
    return result;
  }

  /** Checks if this LineSegment and other contain a common point. */
  public boolean intersects(LineSegment other) {
    Line[] intersections = this.intersection(other);
    return intersections.length > 0;
  }

  /**
   * Checks if this LineSegment and other contain a common LineSegment, which is not reduced to a
   * point.
   */
  public boolean overlaps(LineSegment other) {
    Line[] intersections = this.intersection(other);
    return intersections.length > 1;
  }

  /**
   * Constructs an approximation of this line segment by orthogonal stairs with integer coordinates.
   * The length of the stairs will be at most stairWidth. If toTheRight, the stairs will be to the
   * right of this line segment, else to the left.
   */
  public IntPoint[] stairApproximation(double width, boolean toTheRight) {
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
      stairWidth = (int) Math.round((width * (double) absDx) / (double) absDy);
      stairCount = (absDx - 1) / stairWidth + 1;
      if (endPoint.x < startPoint.x) {
        stairWidth = -stairWidth;
      }
    } else {
      stairWidth = (int) Math.round((width * (double) absDy) / (double) absDx);
      stairCount = (absDy - 1) / stairWidth + 1;
      if (endPoint.y < startPoint.y) {
        stairWidth = -stairWidth;
      }
    }
    IntPoint[] result = new IntPoint[2 * stairCount + 1];

    result[0] = startPoint;
    double det = (double) dx * (double) dy;
    boolean changeXfirst = toTheRight && det > 0 || !toTheRight && det < 0;
    int currentIndex = 0;

    int prevLinePointX = startPoint.x;
    int prevLinePointY = startPoint.y;
    for (int i = 1; i < stairCount; i++) {
      int currentLinePointX;
      int currentLinePointY;
      if (functionOfX) {
        currentLinePointX = startPoint.x + i * stairWidth;
        currentLinePointY = (int) Math.round(this.getLine().functionValueApprox(currentLinePointX));
      } else {
        currentLinePointY = startPoint.y + i * stairWidth;
        currentLinePointX =
            (int) Math.round(this.getLine().functionInYValueApprox(currentLinePointY));
      }
      ++currentIndex;
      if (changeXfirst) {
        result[currentIndex] = new IntPoint(currentLinePointX, prevLinePointY);
      } else {
        result[currentIndex] = new IntPoint(prevLinePointX, currentLinePointY);
      }
      ++currentIndex;
      result[currentIndex] = new IntPoint(currentLinePointX, currentLinePointY);
      prevLinePointX = currentLinePointX;
      prevLinePointY = currentLinePointY;
    }
    ++currentIndex;
    if (changeXfirst) {
      result[currentIndex] = new IntPoint(endPoint.x, prevLinePointY);
    } else {
      result[currentIndex] = new IntPoint(prevLinePointX, endPoint.y);
    }
    ++currentIndex;
    result[currentIndex] = endPoint;
    return result;
  }

  /**
   * Constructs an approximation of this line segment by 45 degree stairs with integer coordinates.
   * The length of the stairs will be at most stairWidth. If toTheRight, the stairs will be to the
   * right of this line segment, else to the left.
   */
  public IntPoint[] stairApproximation45(double width, boolean toTheRight) {
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
      stairWidth = (int) Math.round((width * (double) absDelta.x) / (double) absDelta.y);
      stairCount = (absDelta.x - 1) / stairWidth + 1;
      if (endPoint.x < startPoint.x) {
        stairWidth = -stairWidth;
      }
    } else {
      stairWidth = (int) Math.round((width * (double) absDelta.y) / (double) absDelta.x);
      stairCount = (absDelta.y - 1) / stairWidth + 1;
      if (endPoint.y < startPoint.y) {
        stairWidth = -stairWidth;
      }
    }
    IntPoint[] result = new IntPoint[2 * stairCount + 1];
    result[0] = startPoint;
    IntPoint prevLinePoint = startPoint;
    int currentIndex = 0;
    for (int i = 1; i <= stairCount; i++) {
      IntPoint currentLinePoint;
      int currentX;
      int currentY;
      if (i == stairCount) {
        currentLinePoint = endPoint;
      } else {
        if (functionOfX) {
          currentX = startPoint.x + i * stairWidth;
          currentY = (int) Math.round(this.getLine().functionValueApprox(currentX));
        } else {
          currentY = startPoint.y + i * stairWidth;
          currentX = (int) Math.round(this.getLine().functionValueApprox(currentY));
        }
        currentLinePoint = new IntPoint(currentX, currentY);
      }
      if (functionOfX) {
        boolean diagonalFirst = toTheRight && det < 0 || !toTheRight && det > 0;

        if (diagonalFirst) {
          currentX =
              prevLinePoint.x
                  + Signum.asInt(stairWidth) * Math.abs(currentLinePoint.y - prevLinePoint.y);
          currentY = currentLinePoint.y;
        } else {
          // horizontal first
          currentX =
              currentLinePoint.x
                  - Signum.asInt(stairWidth) * Math.abs(currentLinePoint.y - prevLinePoint.y);
          currentY = prevLinePoint.y;
        }
      } else {
        // function of y
        boolean diagonalFirst = toTheRight && det > 0 || !toTheRight && det < 0;

        if (diagonalFirst) {
          currentX = currentLinePoint.x;
          currentY =
              prevLinePoint.y
                  + Signum.asInt(stairWidth) * Math.abs(currentLinePoint.x - prevLinePoint.x);
        } else {
          currentX = prevLinePoint.x;
          currentY =
              currentLinePoint.y
                  - Signum.asInt(stairWidth) * Math.abs(currentLinePoint.x - prevLinePoint.x);
        }
      }
      ++currentIndex;
      result[currentIndex] = new IntPoint(currentX, currentY);
      ++currentIndex;
      result[currentIndex] = currentLinePoint;
      prevLinePoint = currentLinePoint;
    }
    return result;
  }

  /**
   * Returns an array with the borderline numbers of shape, which are intersected by this line
   * segment. Intersections at an endpoint of this line segment are only counted, if the line
   * segment intersects with the interior of shape. The result array may have length 0, 1 or 2. With
   * 2 intersections the intersection which is nearest to the start point of the line segment comes
   * first.
   */
  public int[] borderIntersections(TileShape shape) {
    int[] emptyResult = new int[0];
    if (!this.boundingBox().intersects(shape.boundingBox())) {
      return emptyResult;
    }

    int edgeCount = shape.borderLineCount();
    Line prevLine = shape.borderLine(edgeCount - 1);
    Line currentLine = shape.borderLine(0);
    int[] result = new int[2];
    Point[] intersection = new Point[2];
    int intersectionCount = 0;
    Point lineStart = this.startPoint();
    Point lineEnd = this.endPoint();

    for (int edgeLineNo = 0; edgeLineNo < edgeCount; edgeLineNo++) {
      Line nextLine;
      if (edgeLineNo == edgeCount - 1) {
        nextLine = shape.borderLine(0);
      } else {
        nextLine = shape.borderLine(edgeLineNo + 1);
      }

      Side startPointSide = currentLine.sideOf(lineStart);
      Side endPointSide = currentLine.sideOf(lineEnd);
      if (startPointSide == Side.ON_THE_LEFT && endPointSide == Side.ON_THE_LEFT) {
        // both endpoints are outside the borderLine,
        // no intersection possible
        return emptyResult;
      }

      if (startPointSide == Side.COLLINEAR) {
        // the start is on currentLine, check that the end point is inside
        // the halfplane, because touches count only, if the interior
        // is entered
        if (endPointSide != Side.ON_THE_RIGHT) {
          return emptyResult;
        }
      }

      if (endPointSide == Side.COLLINEAR) {
        // the end is on currentLine, check that the start point is inside
        // the halfplane, because touches count only, if the interior
        // is entered
        if (startPointSide != Side.ON_THE_RIGHT) {
          return emptyResult;
        }
      }

      if (startPointSide != Side.ON_THE_RIGHT || endPointSide != Side.ON_THE_RIGHT) {
        // not both points are inside the halplane defined by currentLine
        Point is = this.middle.intersection(currentLine);
        Side prevLineSideOfIs = prevLine.sideOf(is);
        Side nextLineSideOfIs = nextLine.sideOf(is);
        if (prevLineSideOfIs != Side.ON_THE_LEFT && nextLineSideOfIs != Side.ON_THE_LEFT) {
          // this line segment intersects currentLine between the
          // previous and the next corner of simplex

          if (prevLineSideOfIs == Side.COLLINEAR) {
            // this line segment goes through the previous
            // corner of simplex. Check, that the intersection
            // isn't merely a touch.
            Point prevPrevCorner;
            if (edgeLineNo == 0) {
              prevPrevCorner = shape.corner(edgeCount - 1);
            } else {
              prevPrevCorner = shape.corner(edgeLineNo - 1);
            }

            Point nextCorner;
            if (edgeLineNo == edgeCount - 1) {
              nextCorner = shape.corner(0);
            } else {
              nextCorner = shape.corner(edgeLineNo + 1);
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
            // corner of simplex. Check, that the intersection
            // isn't merely a touch.
            Point prevCorner = shape.corner(edgeLineNo);
            Point nextNextCorner;

            if (edgeLineNo == edgeCount - 2) {
              nextNextCorner = shape.corner(0);
            } else if (edgeLineNo == edgeCount - 1) {
              nextNextCorner = shape.corner(1);
            } else {
              nextNextCorner = shape.corner(edgeLineNo + 2);
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

      prevLine = currentLine;
      currentLine = nextLine;
    }

    if (intersectionCount == 0) {
      return emptyResult;
    }

    if (intersectionCount == 2) {
      // assure the correct order
      FloatPoint is0 = intersection[0].toFloat();
      FloatPoint is1 = intersection[1].toFloat();
      FloatPoint currentStart = lineStart.toFloat();
      if (currentStart.distanceSquare(is1) < currentStart.distanceSquare(is0)) {
        // swap the result points
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
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
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

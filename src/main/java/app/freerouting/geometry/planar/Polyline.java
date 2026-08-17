package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A Polyline is a sequence of lines, where no 2 consecutive lines may be parallel. A Polyline of n
 * lines defines a Polygon of n-1 intersection points of consecutive lines. The lines of the objects
 * of class Polyline are normally defined by points with integer coordinates, whereas the
 * intersections of Lines can be represented in general only by infinite precision rational points.
 * We use polylines with integer coordinates instead of polygons with infinite precision rational
 * coordinates because of its better performance in geometric calculations.
 */
public class Polyline implements Serializable {

  private static final boolean USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES = true;

  /** Stores the array of lines of this polyline. */
  public final Line[] lines;

  private transient FloatPoint[] precalculatedFloatCorners;
  private transient Point[] precalculatedCorners;
  private transient IntBox precalculatedBoundingBox;

  /**
   * Creates a polyline of length polygon.cornerCount + 1 from polygon, so that the i-th corner of
   * polygon will be the intersection of the i-th and the i+1-th lines of the new created polyline
   * for 0 {@literal <}= i {@literal <} points.length. polygon must have at least 2 corners
   */
  public Polyline(Polygon polygon) {
    Point[] points = polygon.cornerArray();
    if (points.length < 2) {
      FRLogger.warn("Polyline: must contain at least 2 different points");
      lines = new Line[0];
      return;
    }
    lines = new Line[points.length + 1];
    for (int i = 1; i < points.length; i++) {
      lines[i] = new Line(points[i - 1], points[i]);
    }
    // construct perpendicular lines at the start and at the end to represent
    // the first and the last point of points as intersection of lines.

    Direction dir = Direction.getInstance(points[0], points[1]);
    lines[0] = Line.getInstance(points[0], dir.turn45Degree(2));

    dir = Direction.getInstance(points[points.length - 1], points[points.length - 2]);
    lines[points.length] = Line.getInstance(points[points.length - 1], dir.turn45Degree(2));
  }

  /** Creates a polyline from an array of points. */
  public Polyline(Point[] points) {
    this(new Polygon(points));
  }

  /** Creates a polyline consisting of three lines. */
  public Polyline(Point fromCorner, Point toCorner) {
    if (fromCorner.equals(toCorner)) {
      lines = new Line[0];
      return;
    }
    lines = new Line[3];
    Direction dir = Direction.getInstance(fromCorner, toCorner);
    lines[0] = Line.getInstance(fromCorner, dir.turn45Degree(2));
    lines[1] = new Line(fromCorner, toCorner);
    dir = Direction.getInstance(fromCorner, toCorner);
    lines[2] = Line.getInstance(toCorner, dir.turn45Degree(2));
  }

  /**
   * Creates a polyline from an array of lines. Lines, which are parallel to the previous line are
   * skipped. The directed lines are normalized, so that they intersect the previous line before the
   * next line
   */
  public Polyline(Line[] inputLines) {
    Line[] filteredLines = removeConsecutiveParallelLines(inputLines);
    filteredLines = removeOverlaps(filteredLines);
    if (filteredLines.length < 3) {
      this.lines = new Line[0];
      return;
    }
    precalculatedFloatCorners = new FloatPoint[filteredLines.length - 1];

    // turn evtl the direction of the lines that they point always
    // from the previous corner to the next corner
    for (int i = 1; i < filteredLines.length - 1; i++) {
      precalculatedFloatCorners[i] = filteredLines[i].intersectionApprox(filteredLines[i + 1]);
      Side sideOfLine = filteredLines[i - 1].sideOf(precalculatedFloatCorners[i]);
      if (sideOfLine != Side.COLLINEAR) {
        Direction d0 = filteredLines[i - 1].direction();
        Direction d1 = filteredLines[i].direction();
        Side side1 = d0.sideOf(d1);
        if (side1 != sideOfLine) {
          filteredLines[i] = filteredLines[i].opposite();
        }
      }
    }
    this.lines = filteredLines;
  }

  private static Line[] removeConsecutiveParallelLines(Line[] lines) {
    if (lines.length < 3) {
      // polyline must have at least 3 lines
      return lines;
    }
    Line[] tmpArr = new Line[lines.length];
    int newLength = 0;
    tmpArr[0] = lines[0];
    for (int i = 1; i < lines.length; i++) {
      // skip multiple lines
      if (!tmpArr[newLength].isParallel(lines[i])) {
        ++newLength;
        tmpArr[newLength] = lines[i];
      }
    }
    ++newLength;
    if (newLength == lines.length) {
      // nothing skipped
      return lines;
    }
    // at least 1 line is skipped, adjust the array
    if (newLength < 3) {
      return new Line[0];
    }
    Line[] result = new Line[newLength];
    System.arraycopy(tmpArr, 0, result, 0, newLength);
    return result;
  }

  /** Checks if previous and next lines are equal or opposite and removes the resulting overlap. */
  private static Line[] removeOverlaps(Line[] lines) {
    if (lines.length < 4) {
      return lines;
    }
    int newLength = 0;
    Line[] tmpArr = new Line[lines.length];
    tmpArr[0] = lines[0];
    if (!lines[0].isEqualOrOpposite(lines[2])) {
      ++newLength;
    }
    // else skip the first line
    tmpArr[newLength] = lines[1];
    ++newLength;
    for (int i = 2; i < lines.length - 2; i++) {
      if (tmpArr[newLength - 1].isEqualOrOpposite(lines[i + 1])) {
        // skip 2 lines
        --newLength;
      } else {
        tmpArr[newLength] = lines[i];
        ++newLength;
      }
    }
    tmpArr[newLength] = lines[lines.length - 2];
    ++newLength;
    // Guard: newLength must be >= 2 before accessing tmpArr[newLength - 2].
    // If the loop decremented newLength all the way to 0 the index would be -1.
    if (newLength >= 2 && !lines[lines.length - 1].isEqualOrOpposite(tmpArr[newLength - 2])) {
      tmpArr[newLength] = lines[lines.length - 1];
      ++newLength;
    }
    // else skip the last line
    if (newLength == lines.length) {
      // nothing skipped
      return lines;
    }
    // at least 1 line is skipped, adjust the array
    if (newLength < 3) {
      return new Line[0];
    }
    Line[] result = new Line[newLength];
    System.arraycopy(tmpArr, 0, result, 0, newLength);
    return result;
  }

  /** Returns the number of lines minus 1. */
  public int cornerCount() {
    return lines.length - 1;
  }

  public boolean isEmpty() {
    return lines.length < 3;
  }

  /** Checks, if this polyline is empty or if all corner points are equal. */
  public boolean isPoint() {
    if (lines.length < 3) {
      return true;
    }
    Point firstCorner = this.corner(0);
    for (int i = 1; i < lines.length - 1; i++) {
      if (!this.corner(i).equals(firstCorner)) {
        return false;
      }
    }
    return true;
  }

  /** Checks if all lines of this polyline are orthogonal. */
  public boolean isOrthogonal() {
    for (int i = 0; i < lines.length; i++) {
      if (!lines[i].isOrthogonal()) {
        return false;
      }
    }
    return true;
  }

  /** Checks if all lines of this polyline are multiples of 45 degrees. */
  public boolean isMultipleOf45Degree() {
    for (int i = 0; i < lines.length; i++) {
      if (!lines[i].isMultipleOf45Degree()) {
        return false;
      }
    }
    return true;
  }

  /** Returns the intersection of the first line with the second line. */
  public Point firstCorner() {
    return corner(0);
  }

  /** Returns the intersection of the last line with the line before the last line. */
  public Point lastCorner() {
    return corner(lines.length - 2);
  }

  /**
   * Returns the array of the intersection of two consecutive lines approximated by FloatPoint's.
   */
  public Point[] corners() {
    if (lines.length < 2) {
      return new Point[0];
    }
    if (precalculatedCorners == null) {
      // corner array is not yet allocated
      precalculatedCorners = new Point[lines.length - 1];
    }
    for (int i = 0; i < precalculatedCorners.length; i++) {
      if (precalculatedCorners[i] == null) {
        precalculatedCorners[i] = lines[i].intersection(lines[i + 1]);
      }
    }
    return precalculatedCorners;
  }

  /** Returns the array of intersections of consecutive lines, approximated by FloatPoint values. */
  public FloatPoint[] cornerApproxArr() {
    if (lines.length < 2) {
      return new FloatPoint[0];
    }
    if (precalculatedFloatCorners == null) {
      // corner array is not yet allocated
      precalculatedFloatCorners = new FloatPoint[lines.length - 1];
    }
    for (int i = 0; i < precalculatedFloatCorners.length; i++) {
      if (precalculatedFloatCorners[i] == null) {
        precalculatedFloatCorners[i] = lines[i].intersectionApprox(lines[i + 1]);
      }
    }
    return precalculatedFloatCorners;
  }

  /**
   * Returns an approximation of the intersection of the no-th with the (no - 1)-th line by a
   * FloatPoint.
   */
  public FloatPoint cornerApprox(int cornerIndex) {
    int no;
    if (cornerIndex < 0) {
      FRLogger.warn("Polyline.corner_approx: no is < 0");
      no = 0;
    } else if (cornerIndex >= lines.length - 1) {
      FRLogger.warn("Polyline.corner_approx: no must be less than lines.length - 1");
      no = lines.length - 2;
    } else {
      no = cornerIndex;
    }
    if (precalculatedFloatCorners == null) {
      // corner array is not yet allocated
      precalculatedFloatCorners = new FloatPoint[lines.length - 1];
    }
    if (precalculatedFloatCorners[no] == null) {
      // corner is not yet calculated
      precalculatedFloatCorners[no] = lines[no].intersectionApprox(lines[no + 1]);
    }
    return precalculatedFloatCorners[no];
  }

  /** Returns the intersection of the no-th with the (no - 1)-th edge line. */
  public Point corner(int cornerIndex) {
    if (lines.length < 2) {
      FRLogger.trace("Polyline.corner: lines.length is < 2");
      return null;
    }
    int no;
    if (cornerIndex < 0) {
      FRLogger.warn("Polyline.corner: no is < 0");
      no = 0;
    } else if (cornerIndex >= lines.length - 1) {
      FRLogger.warn("Polyline.corner: no must be less than lines.length - 1");
      no = lines.length - 2;
    } else {
      no = cornerIndex;
    }
    if (precalculatedCorners == null) {
      // corner array is not yet allocated
      precalculatedCorners = new Point[lines.length - 1];
    }
    if (precalculatedCorners[no] == null) {
      // corner is not yet calculated
      precalculatedCorners[no] = lines[no].intersection(lines[no + 1]);
    }
    return precalculatedCorners[no];
  }

  /** Returns the polyline with the reversed order of lines. */
  public Polyline reverse() {
    Line[] reversedLines = new Line[lines.length];
    for (int i = 0; i < lines.length; i++) {
      reversedLines[i] = lines[lines.length - i - 1].opposite();
    }
    return new Polyline(reversedLines);
  }

  /** Calculates the length of this polyline from fromCorner to toCorner. */
  public double lengthApprox(int requestedFromCorner, int requestedToCorner) {
    int fromCorner = Math.max(requestedFromCorner, 0);
    int toCorner = Math.min(requestedToCorner, lines.length - 2);
    double result = 0;
    for (int i = fromCorner; i < toCorner; i++) {
      result += this.cornerApprox(i + 1).distance(this.cornerApprox(i));
    }
    return result;
  }

  /** Calculates the cumulative distance between consecutive corners of this polyline. */
  public double lengthApprox() {
    return lengthApprox(0, lines.length - 2);
  }

  /**
   * Calculates for each line a shape around this line where the right and left edge lines have the
   * distance halfWidth from the center line. Returns an array of convex shapes of length lineCount
   * - 2.
   */
  public TileShape[] offsetShapes(int halfWidth) {
    return offsetShapes(halfWidth, 0, lines.length - 1);
  }

  /**
   * Calculates for each line between fromNo and toNo a shape around this line, where the right and
   * left edge lines have the distance halfWidth from the center line.
   */
  public TileShape[] offsetShapes(int halfWidth, int requestedFromNo, int requestedToNo) {
    int fromNo = Math.max(requestedFromNo, 0);
    int toNo = Math.min(requestedToNo, this.lines.length - 1);
    int shapeCount = Math.max(toNo - fromNo - 1, 0);
    TileShape[] shapes = new TileShape[shapeCount];
    if (shapeCount == 0) {
      return shapes;
    }
    Vector prevDir = this.lines[fromNo].direction().getVector();
    Vector currentDirection = this.lines[fromNo + 1].direction().getVector();
    for (int i = fromNo + 1; i < toNo; i++) {
      Vector nextDir = this.lines[i + 1].direction().getVector();

      Line[] offsetLines = new Line[4];

      offsetLines[0] = this.lines[i].translate(-halfWidth);
      // current center line translated to the right

      // create the front line of the offset shape
      Side nextDirFromCurrDir = nextDir.sideOf(currentDirection);
      // left turn from currentLine to nextLine
      if (nextDirFromCurrDir == Side.ON_THE_LEFT) {
        offsetLines[1] = this.lines[i + 1].translate(-halfWidth);
        // next right line
      } else {
        offsetLines[1] = this.lines[i + 1].opposite().translate(-halfWidth);
        // next left line in opposite direction
      }

      offsetLines[2] = this.lines[i].opposite().translate(-halfWidth);
      // current left line in opposite direction

      // create the back line of the offset shape
      Side currentDirFromPrevDir = currentDirection.sideOf(prevDir);
      // left turn from prevLine to currentLine
      if (currentDirFromPrevDir == Side.ON_THE_LEFT) {
        offsetLines[3] = this.lines[i - 1].translate(-halfWidth);
        // previous line translated to the right
      } else {
        offsetLines[3] = this.lines[i - 1].opposite().translate(-halfWidth);
        // previous left line in opposite direction
      }
      // cut off outstanding corners with following shapes
      FloatPoint cornerToCheck = null;
      Line currentLine = offsetLines[1];
      Line checkLine;
      if (nextDirFromCurrDir == Side.ON_THE_LEFT) {
        checkLine = offsetLines[2];
      } else {
        checkLine = offsetLines[0];
      }
      FloatPoint checkDistanceCorner = cornerApprox(i);
      final double checkDistSquare = 2.0 * halfWidth * halfWidth;
      Collection<Line> cutDogEarLines = new LinkedList<>();
      Vector tmpCurrDir = nextDir;
      boolean directionChanged = false;
      for (int j = i + 2; j < this.lines.length - 1; j++) {
        if (cornerApprox(j - 1).distanceSquare(checkDistanceCorner) > checkDistSquare) {
          break;
        }
        if (!directionChanged) {
          cornerToCheck = currentLine.intersectionApprox(checkLine);
        }
        Vector tmpNextDir = this.lines[j].direction().getVector();
        Line nextBorderLine;
        Side tmpNextDirFromTmpCurrDir = tmpNextDir.sideOf(tmpCurrDir);
        directionChanged = tmpNextDirFromTmpCurrDir != nextDirFromCurrDir;
        if (!directionChanged) {
          if (tmpNextDirFromTmpCurrDir == Side.ON_THE_LEFT) {
            nextBorderLine = this.lines[j].translate(-halfWidth);
          } else {
            nextBorderLine = this.lines[j].opposite().translate(-halfWidth);
          }

          if (nextBorderLine.sideOf(cornerToCheck) == Side.ON_THE_LEFT
              && nextBorderLine.sideOf(this.corner(i)) == Side.ON_THE_RIGHT
              && nextBorderLine.sideOf(this.corner(i - 1)) == Side.ON_THE_RIGHT) {
            // an outstanding corner
            cutDogEarLines.add(nextBorderLine);
          }
          tmpCurrDir = tmpNextDir;
          currentLine = nextBorderLine;
        }
      }
      // cut off outstanding corners with previous shapes
      checkDistanceCorner = cornerApprox(i - 1);
      if (currentDirFromPrevDir == Side.ON_THE_LEFT) {
        checkLine = offsetLines[2];
      } else {
        checkLine = offsetLines[0];
      }
      currentLine = offsetLines[3];
      tmpCurrDir = prevDir;
      directionChanged = false;
      for (int j = i - 2; j >= 1; j--) {
        if (cornerApprox(j).distanceSquare(checkDistanceCorner) > checkDistSquare) {
          break;
        }
        if (!directionChanged) {
          cornerToCheck = currentLine.intersectionApprox(checkLine);
        }
        Vector tmpPrevDir = this.lines[j].direction().getVector();
        Line prevBorderLine;
        Side tmpCurrDirFromTmpPrevDir = tmpCurrDir.sideOf(tmpPrevDir);
        directionChanged = tmpCurrDirFromTmpPrevDir != currentDirFromPrevDir;
        if (!directionChanged) {
          if (tmpCurrDir.sideOf(tmpPrevDir) == Side.ON_THE_LEFT) {
            prevBorderLine = this.lines[j].translate(-halfWidth);
          } else {
            prevBorderLine = this.lines[j].opposite().translate(-halfWidth);
          }
          if (prevBorderLine.sideOf(cornerToCheck) == Side.ON_THE_LEFT
              && prevBorderLine.sideOf(this.corner(i)) == Side.ON_THE_RIGHT
              && prevBorderLine.sideOf(this.corner(i - 1)) == Side.ON_THE_RIGHT) {
            // an outstanding corner
            cutDogEarLines.add(prevBorderLine);
          }
          tmpCurrDir = tmpPrevDir;
          currentLine = prevBorderLine;
        }
      }
      TileShape s1 = TileShape.getInstance(offsetLines);
      int cutLineCount = cutDogEarLines.size();
      if (cutLineCount > 0) {
        Line[] cutLines = new Line[cutLineCount];
        Iterator<Line> it = cutDogEarLines.iterator();
        for (int j = 0; j < cutLineCount; j++) {
          cutLines[j] = it.next();
        }
        s1 = s1.intersection(TileShape.getInstance(cutLines));
      }
      int currentShapeNo = i - fromNo - 1;
      TileShape boundingShape;
      if (USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES) {
        // intersect with the bounding octagon
        IntOctagon surrOct = boundingOctagon(i - 1, i);
        boundingShape = surrOct.offset(halfWidth);

      } else {
        // intersect with the bounding box
        IntBox surrBox = boundingBox(i - 1, i);
        IntBox offsetBox = surrBox.offset(halfWidth);
        boundingShape = offsetBox.toSimplex();
      }
      shapes[currentShapeNo] = boundingShape.intersectionWithSimplify(s1);
      if (shapes[currentShapeNo].isEmpty()) {
        FRLogger.warn("offset_shapes: shape is empty");
      }

      prevDir = currentDirection;
      currentDirection = nextDir;
    }
    return shapes;
  }

  /**
   * Calculates for the no-th line segment a shape around this line where the right and left edge
   * lines have the distance halfWidth from the center line. 0 {@literal <}= no {@literal <}=
   * lines.length - 3
   */
  public TileShape offsetShape(int halfWidth, int no) {
    if (no < 0 || no > lines.length - 3) {
      FRLogger.warn("Polyline.offsetShape: no out of range");
      return null;
    }
    TileShape[] result = offsetShapes(halfWidth, no, no + 2);
    return result[0];
  }

  /**
   * Calculates for the no-th line segment a box shape around this line where the border lines have
   * the distance halfWidth from the center line. 0 {@literal <}= no {@literal <}= lines.length - 3
   */
  public IntBox offsetBox(int halfWidth, int no) {
    LineSegment currentLineSegment = new LineSegment(this, no + 1);
    return currentLineSegment.boundingBox().offset(halfWidth);
  }

  /** Returns the polyline translated by vector. */
  public Polyline translateBy(Vector vector) {
    if (vector.equals(Vector.ZERO)) {
      return this;
    }
    Line[] newArray = new Line[lines.length];
    for (int i = 0; i < newArray.length; i++) {
      newArray[i] = lines[i].translateBy(vector);
    }
    return new Polyline(newArray);
  }

  /** Returns the polyline turned by factor times 90 degrees around pole. */
  public Polyline turn90Degree(int factor, IntPoint pole) {
    Line[] newArray = new Line[lines.length];
    for (int i = 0; i < newArray.length; i++) {
      newArray[i] = lines[i].turn90Degree(factor, pole);
    }
    return new Polyline(newArray);
  }

  /** Returns an approximation of this polyline rotated around pole. */
  public Polyline rotateApprox(double angle, FloatPoint pole) {
    if (angle == 0) {
      return this;
    }
    IntPoint[] newCorners = new IntPoint[this.cornerCount()];
    for (int i = 0; i < newCorners.length; i++) {

      newCorners[i] = this.cornerApprox(i).rotate(angle, pole).round();
    }
    return new Polyline(newCorners);
  }

  /** Mirrors this polyline at the vertical line through pole. */
  public Polyline mirrorVertical(IntPoint pole) {
    Line[] newArray = new Line[lines.length];
    for (int i = 0; i < newArray.length; i++) {
      newArray[i] = lines[i].mirrorVertical(pole);
    }
    return new Polyline(newArray);
  }

  /** Mirrors this polyline at the horizontal line through pole. */
  public Polyline mirrorHorizontal(IntPoint pole) {
    Line[] newArray = new Line[lines.length];
    for (int i = 0; i < newArray.length; i++) {
      newArray[i] = lines[i].mirrorHorizontal(pole);
    }
    return new Polyline(newArray);
  }

  /**
   * Returns the smallest box containing the intersection points from index fromCornerNo to index
   * toCornerNo of the lines of this polyline.
   */
  public IntBox boundingBox(int requestedFromCornerNo, int requestedToCornerNo) {
    int fromCornerNo = Math.max(requestedFromCornerNo, 0);
    int toCornerNo = Math.min(requestedToCornerNo, lines.length - 2);
    double llx = Integer.MAX_VALUE;
    double lly = llx;
    double urx = Integer.MIN_VALUE;
    double ury = urx;
    for (int i = fromCornerNo; i <= toCornerNo; i++) {
      FloatPoint currentCorner = cornerApprox(i);
      llx = Math.min(llx, currentCorner.x);
      lly = Math.min(lly, currentCorner.y);
      urx = Math.max(urx, currentCorner.x);
      ury = Math.max(ury, currentCorner.y);
    }
    IntPoint lowerLeft = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
    IntPoint upperRight = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
    return new IntBox(lowerLeft, upperRight);
  }

  /** Returns the smallest box containing the intersection points of the lines of this polyline. */
  public IntBox boundingBox() {
    if (precalculatedBoundingBox == null) {
      precalculatedBoundingBox = boundingBox(0, cornerCount() - 1);
    }
    return precalculatedBoundingBox;
  }

  /**
   * Returns the smallest octagon containing the intersection points from index fromCornerNo to
   * index toCornerNo of the lines of this polyline.
   */
  public IntOctagon boundingOctagon(int requestedFromCornerNo, int requestedToCornerNo) {
    int fromCornerNo = Math.max(requestedFromCornerNo, 0);
    int toCornerNo = Math.min(requestedToCornerNo, lines.length - 2);
    double lx = Integer.MAX_VALUE;
    double ly = Integer.MAX_VALUE;
    double rx = Integer.MIN_VALUE;
    double uy = Integer.MIN_VALUE;
    double ulx = Integer.MAX_VALUE;
    double lrx = Integer.MIN_VALUE;
    double llx = Integer.MAX_VALUE;
    double urx = Integer.MIN_VALUE;
    for (int i = fromCornerNo; i <= toCornerNo; i++) {
      FloatPoint current = cornerApprox(i);
      lx = Math.min(lx, current.x);
      ly = Math.min(ly, current.y);
      rx = Math.max(rx, current.x);
      uy = Math.max(uy, current.y);
      double tmp = current.x - current.y;
      ulx = Math.min(ulx, tmp);
      lrx = Math.max(lrx, tmp);
      tmp = current.x + current.y;
      llx = Math.min(llx, tmp);
      urx = Math.max(urx, tmp);
    }
    return new IntOctagon(
        (int) Math.floor(lx),
        (int) Math.floor(ly),
        (int) Math.ceil(rx),
        (int) Math.ceil(uy),
        (int) Math.floor(ulx),
        (int) Math.ceil(lrx),
        (int) Math.floor(llx),
        (int) Math.ceil(urx));
  }

  /** Calculates an approximation of the nearest point on this polyline to fromPoint. */
  public FloatPoint nearestPointApprox(FloatPoint fromPoint) {
    double minDistance = Double.MAX_VALUE;
    FloatPoint nearestPoint = null;
    // calculate the nearest corner point
    FloatPoint[] corners = cornerApproxArr();
    for (int i = 0; i < corners.length; i++) {
      double currentDistance = corners[i].distance(fromPoint);
      if (currentDistance < minDistance) {
        minDistance = currentDistance;
        nearestPoint = corners[i];
      }
    }
    final double ctolerance = 1;
    for (int i = 1; i < lines.length - 1; i++) {
      FloatPoint projection = fromPoint.projectionApprox(lines[i]);
      double currentDistance = projection.distance(fromPoint);
      if (currentDistance < minDistance) {
        // look, if the projection is inside the segment
        double segmentLength = corners[i].distance(corners[i - 1]);
        if (projection.distance(corners[i]) + projection.distance(corners[i - 1])
            < segmentLength + ctolerance) {
          minDistance = currentDistance;
          nearestPoint = projection;
        }
      }
    }
    return nearestPoint;
  }

  /** Calculates the distance of fromPoint to the nearest point on this polyline. */
  public double distance(FloatPoint fromPoint) {
    return fromPoint.distance(nearestPointApprox(fromPoint));
  }

  /**
   * Combines the two polylines, if they have a common end corner. The order of lines in this
   * polyline will be preserved. Returns the combined polyline or this polyline, if this polyline
   * and other have no common end corner. If there is something to combine at the start of this
   * polyline, other is inserted in front of this polyline. If there is something to combine at the
   * end of this polyline, this polyline is inserted in front of other.
   */
  public Polyline combine(Polyline other) {
    if (other == null || lines.length < 3 || other.lines.length < 3) {
      return this;
    }
    boolean combineAtStart;
    boolean combineOtherAtStart;
    if (firstCorner().equals(other.firstCorner())) {
      combineAtStart = true;
      combineOtherAtStart = true;
    } else if (firstCorner().equals(other.lastCorner())) {
      combineAtStart = true;
      combineOtherAtStart = false;
    } else if (lastCorner().equals(other.firstCorner())) {
      combineAtStart = false;
      combineOtherAtStart = true;
    } else if (lastCorner().equals(other.lastCorner())) {
      combineAtStart = false;
      combineOtherAtStart = false;
    } else {
      return this; // no common endpoint
    }
    Line[] newLines = new Line[this.lines.length + other.lines.length - 2];
    if (combineAtStart) {
      // insert the lines of other in front
      if (combineOtherAtStart) {
        // insert in reverse order, skip the first line of other
        for (int i = 0; i < other.lines.length - 1; i++) {
          newLines[i] = other.lines[other.lines.length - i - 1].opposite();
        }
      } else {
        // skip the last line of other
        System.arraycopy(other.lines, 0, newLines, 0, other.lines.length - 1);
      }
      // append the lines of this polyline, skip the first line
      System.arraycopy(this.lines, 1, newLines, other.lines.length - 1, this.lines.length - 1);
    } else {
      // insert the lines of this polyline in front, skip the last line
      System.arraycopy(this.lines, 0, newLines, 0, this.lines.length - 1);
      if (combineOtherAtStart) {
        // skip the first line of other
        System.arraycopy(other.lines, 1, newLines, this.lines.length - 1, other.lines.length - 1);
      } else {
        // insert in reverse order, skip the last line of other
        for (int i = 1; i < other.lines.length; i++) {
          newLines[this.lines.length + i - 2] = other.lines[other.lines.length - i - 1].opposite();
        }
      }
    }
    return new Polyline(newLines);
  }

  /**
   * Splits this polyline at the line with index lineIndex into two by inserting endline as
   * concluding line of the first split piece and as the start line of the second split piece.
   * endline and the line with index lineIndex must not be parallel. The order of the lines in the
   * two result pieces is preserved. lineIndex must be bigger than 0 and less than lines.length - 1.
   * Returns null, if nothing was split.
   */
  public Polyline[] split(int lineIndex, Line endLine) {
    if (lineIndex < 1 || lineIndex > lines.length - 2) {
      FRLogger.warn("Polyline.split: lineIndex out of range");
      return null;
    }
    if (this.lines[lineIndex].isParallel(endLine)) {
      return null;
    }
    Point newEndCorner = this.lines[lineIndex].intersection(endLine);
    FRLogger.trace(
        "Polyline.split",
        "compare_trace_split_called",
        "lineIndex="
            + lineIndex
            + ", lines.length="
            + lines.length
            + ", lines.length-2="
            + (lines.length - 2)
            + ", newEndCorner="
            + debugPoint(newEndCorner)
            + " (type="
            + newEndCorner.getClass().getSimpleName()
            + ")"
            + ", lastCorner="
            + debugPoint(this.lastCorner())
            + " (type="
            + this.lastCorner().getClass().getSimpleName()
            + ")"
            + ", equals="
            + newEndCorner.equals(this.lastCorner()),
        "Polyline split lineIndex=" + lineIndex,
        new Point[] {this.firstCorner(), newEndCorner, this.lastCorner()});
    StringBuilder sb = new StringBuilder("    CORNERS:");
    for (int i = 0; i < this.cornerCount(); i++) {
      sb.append(" ").append(this.cornerApprox(i));
    }
    FRLogger.trace(
        "Polyline.split",
        "compare_trace_split_corners",
        sb.toString(),
        "Polyline split lineIndex=" + lineIndex,
        new Point[] {this.firstCorner(), newEndCorner, this.lastCorner()});
    if (lineIndex == 1 && newEndCorner.equals(this.firstCorner())
        || lineIndex >= lines.length - 2 && newEndCorner.equals(this.lastCorner())) {
      // No split, if endLine does not intersect, but touches
      // only this Polyline at an end point.
      return null;
    }
    Line[] firstPiece;
    if (this.corner(lineIndex - 1).equals(newEndCorner)) {
      // skip line segment of length 0 at the end of the first piece
      firstPiece = new Line[lineIndex + 1];
      System.arraycopy(this.lines, 0, firstPiece, 0, firstPiece.length);

    } else {
      firstPiece = new Line[lineIndex + 2];
      System.arraycopy(this.lines, 0, firstPiece, 0, lineIndex + 1);
      firstPiece[lineIndex + 1] = endLine;
    }
    Line[] secondPiece;
    if (this.corner(lineIndex).equals(newEndCorner)) {
      // skip line segment of length 0 at the beginning of the second piece
      secondPiece = new Line[lines.length - lineIndex];
      System.arraycopy(this.lines, lineIndex, secondPiece, 0, secondPiece.length);

    } else {
      secondPiece = new Line[lines.length - lineIndex + 1];
      secondPiece[0] = endLine;
      System.arraycopy(this.lines, lineIndex, secondPiece, 1, secondPiece.length - 1);
    }
    Polyline[] result = new Polyline[2];
    result[0] = new Polyline(firstPiece);
    result[1] = new Polyline(secondPiece);
    if (result[0].isPoint() || result[1].isPoint()) {
      return null;
    }
    return result;
  }

  /** Creates a new polyline by skipping lines from fromNo to toNo. */
  public Polyline skipLines(int fromNo, int toNo) {
    if (fromNo < 0 || toNo > lines.length - 1 || fromNo > toNo) {
      return this;
    }
    Line[] newLines = new Line[lines.length - (toNo - fromNo + 1)];
    System.arraycopy(this.lines, 0, newLines, 0, fromNo);
    System.arraycopy(this.lines, toNo + 1, newLines, fromNo, newLines.length - fromNo);
    return new Polyline(newLines);
  }

  /** Returns whether this polyline contains the given point. */
  public boolean contains(Point point) {
    for (int i = 1; i < lines.length - 1; i++) {
      LineSegment currentSegment = new LineSegment(this, i);
      if (currentSegment.contains(point)) {
        return true;
      }
    }
    return false;
  }

  private static String debugPoint(Point point) {
    if (point instanceof IntPoint intPoint) {
      return "(" + intPoint.x + "," + intPoint.y + ")";
    }
    return String.valueOf(point);
  }

  /**
   * Creates a perpendicular line segment from fromPoint onto the nearest line segment of this
   * polyline to fromSide. Returns null, if the perpendicular line does not intersect the nearest
   * line segment inside its segment bounds or if fromPoint is contained in this polyline.
   */
  public LineSegment projectionLine(Point point) {
    if (point == null) {
      FRLogger.warn(
          "Polyline.projectionLine: fromPoint is null; returning null. "
              + "This indicates a degenerate routing connection was attempted with an "
              + "uninitialized endpoint.");
      return null;
    }
    FloatPoint fromPoint = point.toFloat();
    double minDistance = Double.MAX_VALUE;
    Line resultLine = null;
    Line nearestLine = null;
    for (int i = 1; i < lines.length - 1; i++) {
      FloatPoint projection = fromPoint.projectionApprox(lines[i]);
      double currentDistance = projection.distance(fromPoint);
      if (currentDistance < minDistance) {
        Direction directionTowardsLine = this.lines[i].perpendicularDirection(point);
        if (directionTowardsLine == null) {
          continue;
        }
        Line currentResultLine = new Line(point, directionTowardsLine);
        Point prevCorner = this.corner(i - 1);
        Point nextCorner = this.corner(i);
        Side prevCornerSide = currentResultLine.sideOf(prevCorner);
        Side nextCornerSide = currentResultLine.sideOf(nextCorner);
        if (prevCornerSide == nextCornerSide && prevCornerSide != Side.COLLINEAR) {
          // the projection point is outside the line segment
          continue;
        }
        nearestLine = this.lines[i];
        minDistance = currentDistance;
        resultLine = currentResultLine;
      }
    }
    if (nearestLine == null) {
      return null;
    }
    Line startLine = new Line(point, nearestLine.direction());
    return new LineSegment(startLine, resultLine, nearestLine);
  }

  /**
   * Shortens this polyline to newLineCount lines. Additionally, the last line segment will be
   * approximately shortened to newLength. The last corner of the new polyline will be an IntPoint.
   */
  public Polyline shorten(int newLineCount, double lastSegmentLength) {
    FloatPoint lastCorner = this.cornerApprox(newLineCount - 2);
    FloatPoint prevLastCorner = this.cornerApprox(newLineCount - 3);
    IntPoint newLastCorner = prevLastCorner.changeLength(lastCorner, lastSegmentLength).round();
    if (newLastCorner.equals(this.corner(this.cornerCount() - 2))) {
      // skip the last line
      return skipLines(newLineCount - 1, newLineCount - 1);
    }
    Line[] newLines = new Line[newLineCount];
    System.arraycopy(this.lines, 0, newLines, 0, newLineCount - 2);
    // create the last 2 lines of the new polyline
    Point firstLinePoint = lines[newLineCount - 2].a;
    if (firstLinePoint.equals(newLastCorner)) {
      firstLinePoint = lines[newLineCount - 2].b;
    }
    Line newPrevLastLine = new Line(firstLinePoint, newLastCorner);
    newLines[newLineCount - 2] = newPrevLastLine;
    newLines[newLineCount - 1] =
        Line.getInstance(newLastCorner, newPrevLastLine.direction().turn45Degree(6));
    return new Polyline(newLines);
  }
}

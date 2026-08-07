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

  /** the array of lines of this Polyline. */
  public final Line[] arr;

  private transient FloatPoint[] precalculatedFloatCorners;
  private transient Point[] precalculatedCorners;
  private transient IntBox precalculatedBoundingBox;

  /**
   * creates a polyline of length p_polygon.cornerCount + 1 from p_polygon, so that the i-th corner
   * of p_polygon will be the intersection of the i-th and the i+1-th lines of the new created
   * p_polyline for 0 {@literal <}= i {@literal <} p_point_arr.length. p_polygon must have at least
   * 2 corners
   */
  public Polyline(Polygon p_polygon) {
    Point[] pointArr = p_polygon.corner_array();
    if (pointArr.length < 2) {
      FRLogger.warn("Polyline: must contain at least 2 different points");
      arr = new Line[0];
      return;
    }
    arr = new Line[pointArr.length + 1];
    for (int i = 1; i < pointArr.length; i++) {
      arr[i] = new Line(pointArr[i - 1], pointArr[i]);
    }
    // construct perpendicular lines at the start and at the end to represent
    // the first and the last point of pointArr as intersection of lines.

    Direction dir = Direction.get_instance(pointArr[0], pointArr[1]);
    arr[0] = Line.get_instance(pointArr[0], dir.turn_45_degree(2));

    dir = Direction.get_instance(pointArr[pointArr.length - 1], pointArr[pointArr.length - 2]);
    arr[pointArr.length] = Line.get_instance(pointArr[pointArr.length - 1], dir.turn_45_degree(2));
  }

  public Polyline(Point[] p_points) {
    this(new Polygon(p_points));
  }

  /** creates a polyline consisting of 3 lines */
  public Polyline(Point p_from_corner, Point p_to_corner) {
    if (p_from_corner.equals(p_to_corner)) {
      arr = new Line[0];
      return;
    }
    arr = new Line[3];
    Direction dir = Direction.get_instance(p_from_corner, p_to_corner);
    arr[0] = Line.get_instance(p_from_corner, dir.turn_45_degree(2));
    arr[1] = new Line(p_from_corner, p_to_corner);
    dir = Direction.get_instance(p_from_corner, p_to_corner);
    arr[2] = Line.get_instance(p_to_corner, dir.turn_45_degree(2));
  }

  /**
   * Creates a polyline from an array of lines. Lines, which are parallel to the previous line are
   * skipped. The directed lines are normalized, so that they intersect the previous line before the
   * next line
   */
  public Polyline(Line[] p_line_arr) {
    Line[] lines = remove_consecutive_parallel_lines(p_line_arr);
    lines = remove_overlaps(lines);
    if (lines.length < 3) {
      arr = new Line[0];
      return;
    }
    precalculatedFloatCorners = new FloatPoint[lines.length - 1];

    // turn evtl the direction of the lines that they point always
    // from the previous corner to the next corner
    for (int i = 1; i < lines.length - 1; i++) {
      precalculatedFloatCorners[i] = lines[i].intersection_approx(lines[i + 1]);
      Side sideOfLine = lines[i - 1].side_of(precalculatedFloatCorners[i]);
      if (sideOfLine != Side.COLLINEAR) {
        Direction d0 = lines[i - 1].direction();
        Direction d1 = lines[i].direction();
        Side side1 = d0.side_of(d1);
        if (side1 != sideOfLine) {
          lines[i] = lines[i].opposite();
        }
      }
    }
    arr = lines;
  }

  private static Line[] remove_consecutive_parallel_lines(Line[] p_line_arr) {
    if (p_line_arr.length < 3) {
      // polyline must have at least 3 lines
      return p_line_arr;
    }
    Line[] tmpArr = new Line[p_line_arr.length];
    int newLength = 0;
    tmpArr[0] = p_line_arr[0];
    for (int i = 1; i < p_line_arr.length; i++) {
      // skip multiple lines
      if (!tmpArr[newLength].is_parallel(p_line_arr[i])) {
        ++newLength;
        tmpArr[newLength] = p_line_arr[i];
      }
    }
    ++newLength;
    if (newLength == p_line_arr.length) {
      // nothing skipped
      return p_line_arr;
    }
    // at least 1 line is skipped, adjust the array
    if (newLength < 3) {
      return new Line[0];
    }
    Line[] result = new Line[newLength];
    System.arraycopy(tmpArr, 0, result, 0, newLength);
    return result;
  }

  /** checks if previous and next line are equal or opposite and removes the resulting overlap */
  private static Line[] remove_overlaps(Line[] p_line_arr) {
    if (p_line_arr.length < 4) {
      return p_line_arr;
    }
    int newLength = 0;
    Line[] tmpArr = new Line[p_line_arr.length];
    tmpArr[0] = p_line_arr[0];
    if (!p_line_arr[0].is_equal_or_opposite(p_line_arr[2])) {
      ++newLength;
    }
    // else skip the first line
    tmpArr[newLength] = p_line_arr[1];
    ++newLength;
    for (int i = 2; i < p_line_arr.length - 2; i++) {
      if (tmpArr[newLength - 1].is_equal_or_opposite(p_line_arr[i + 1])) {
        // skip 2 lines
        --newLength;
      } else {
        tmpArr[newLength] = p_line_arr[i];
        ++newLength;
      }
    }
    tmpArr[newLength] = p_line_arr[p_line_arr.length - 2];
    ++newLength;
    // Guard: newLength must be >= 2 before accessing tmpArr[newLength - 2].
    // If the loop decremented newLength all the way to 0 the index would be -1.
    if (newLength >= 2
        && !p_line_arr[p_line_arr.length - 1].is_equal_or_opposite(tmpArr[newLength - 2])) {
      tmpArr[newLength] = p_line_arr[p_line_arr.length - 1];
      ++newLength;
    }
    // else skip the last line
    if (newLength == p_line_arr.length) {
      // nothing skipped
      return p_line_arr;
    }
    // at least 1 line is skipped, adjust the array
    if (newLength < 3) {
      return new Line[0];
    }
    Line[] result = new Line[newLength];
    System.arraycopy(tmpArr, 0, result, 0, newLength);
    return result;
  }

  /** Returns the number of lines minus 1 */
  public int corner_count() {
    return arr.length - 1;
  }

  public boolean is_empty() {
    return arr.length < 3;
  }

  /** Checks, if this polyline is empty or if all corner points are equal. */
  public boolean is_point() {
    if (arr.length < 3) {
      return true;
    }
    Point firstCorner = this.corner(0);
    for (int i = 1; i < arr.length - 1; i++) {
      if (!this.corner(i).equals(firstCorner)) {
        return false;
      }
    }
    return true;
  }

  /** checks, if all lines of this polyline are orthogonal */
  public boolean is_orthogonal() {
    for (int i = 0; i < arr.length; i++) {
      if (!arr[i].is_orthogonal()) {
        return false;
      }
    }
    return true;
  }

  /** checks, if all lines of this polyline are multiples of 45 degree */
  public boolean is_multiple_of_45_degree() {
    for (int i = 0; i < arr.length; i++) {
      if (!arr[i].is_multiple_of_45_degree()) {
        return false;
      }
    }
    return true;
  }

  /** returns the intersection of the first line with the second line */
  public Point first_corner() {
    return corner(0);
  }

  /** returns the intersection of the last line with the line before the last line */
  public Point last_corner() {
    return corner(arr.length - 2);
  }

  /**
   * returns the array of the intersection of two consecutive lines approximated by FloatPoint's.
   */
  public Point[] corner_arr() {
    if (arr.length < 2) {
      return new Point[0];
    }
    if (precalculatedCorners == null)
    // corner array is not yet allocated
    {
      precalculatedCorners = new Point[arr.length - 1];
    }
    for (int i = 0; i < precalculatedCorners.length; i++) {
      if (precalculatedCorners[i] == null) {
        precalculatedCorners[i] = arr[i].intersection(arr[i + 1]);
      }
    }
    return precalculatedCorners;
  }

  /**
   * returns the array of the intersection of two consecutive lines approximated by FloatPoint's.
   */
  public FloatPoint[] corner_approx_arr() {
    if (arr.length < 2) {
      return new FloatPoint[0];
    }
    if (precalculatedFloatCorners == null)
    // corner array is not yet allocated
    {
      precalculatedFloatCorners = new FloatPoint[arr.length - 1];
    }
    for (int i = 0; i < precalculatedFloatCorners.length; i++) {
      if (precalculatedFloatCorners[i] == null) {
        precalculatedFloatCorners[i] = arr[i].intersection_approx(arr[i + 1]);
      }
    }
    return precalculatedFloatCorners;
  }

  /**
   * Returns an approximation of the intersection of the p_no-th with the (p_no - 1)-th line by a
   * FloatPoint.
   */
  public FloatPoint corner_approx(int p_no) {
    int no;
    if (p_no < 0) {
      FRLogger.warn("Polyline.corner_approx: p_no is < 0");
      no = 0;
    } else if (p_no >= arr.length - 1) {
      FRLogger.warn("Polyline.corner_approx: p_no must be less than arr.length - 1");
      no = arr.length - 2;
    } else {
      no = p_no;
    }
    if (precalculatedFloatCorners == null)
    // corner array is not yet allocated
    {
      precalculatedFloatCorners = new FloatPoint[arr.length - 1];
    }
    if (precalculatedFloatCorners[no] == null)
    // corner is not yet calculated
    {
      precalculatedFloatCorners[no] = arr[no].intersection_approx(arr[no + 1]);
    }
    return precalculatedFloatCorners[no];
  }

  /** Returns the intersection of the p_no-th with the (p_no - 1)-th edge line. */
  public Point corner(int p_no) {
    if (arr.length < 2) {
      FRLogger.trace("Polyline.corner: arr.length is < 2");
      return null;
    }
    int no;
    if (p_no < 0) {
      FRLogger.warn("Polyline.corner: p_no is < 0");
      no = 0;
    } else if (p_no >= arr.length - 1) {
      FRLogger.warn("Polyline.corner: p_no must be less than arr.length - 1");
      no = arr.length - 2;
    } else {
      no = p_no;
    }
    if (precalculatedCorners == null)
    // corner array is not yet allocated
    {
      precalculatedCorners = new Point[arr.length - 1];
    }
    if (precalculatedCorners[no] == null)
    // corner is not yet calculated
    {
      precalculatedCorners[no] = arr[no].intersection(arr[no + 1]);
    }
    return precalculatedCorners[no];
  }

  /** return the polyline with the reversed order of lines */
  public Polyline reverse() {
    Line[] reversedLines = new Line[arr.length];
    for (int i = 0; i < arr.length; i++) {
      reversedLines[i] = arr[arr.length - i - 1].opposite();
    }
    return new Polyline(reversedLines);
  }

  /** Calculates the length of this polyline from p_from_corner to p_to_corner. */
  public double length_approx(int p_from_corner, int p_to_corner) {
    int fromCorner = Math.max(p_from_corner, 0);
    int toCorner = Math.min(p_to_corner, arr.length - 2);
    double result = 0;
    for (int i = fromCorner; i < toCorner; i++) {
      result += this.corner_approx(i + 1).distance(this.corner_approx(i));
    }
    return result;
  }

  /** Calculates the cumulative distance between consecutive corners of this polyline. */
  public double length_approx() {
    return length_approx(0, arr.length - 2);
  }

  /**
   * calculates for each line a shape around this line where the right and left edge lines have the
   * distance p_half_width from the center line Returns an array of convex shapes of length
   * lineCount - 2
   */
  public TileShape[] offset_shapes(int p_half_width) {
    return offset_shapes(p_half_width, 0, arr.length - 1);
  }

  /**
   * calculates for each line between p_from_no and p_to_no a shape around this line, where the
   * right and left edge lines have the distance p_half_width from the center line
   */
  public TileShape[] offset_shapes(int p_half_width, int p_from_no, int p_to_no) {
    int fromNo = Math.max(p_from_no, 0);
    int toNo = Math.min(p_to_no, arr.length - 1);
    int shapeCount = Math.max(toNo - fromNo - 1, 0);
    TileShape[] shapeArr = new TileShape[shapeCount];
    if (shapeCount == 0) {
      return shapeArr;
    }
    Vector prevDir = arr[fromNo].direction().get_vector();
    Vector currDir = arr[fromNo + 1].direction().get_vector();
    for (int i = fromNo + 1; i < toNo; i++) {
      Vector nextDir = arr[i + 1].direction().get_vector();

      Line[] lines = new Line[4];

      lines[0] = arr[i].translate(-p_half_width);
      // current center line translated to the right

      // create the front line of the offset shape
      Side nextDirFromCurrDir = nextDir.side_of(currDir);
      // left turn from currLine to nextLine
      if (nextDirFromCurrDir == Side.ON_THE_LEFT) {
        lines[1] = arr[i + 1].translate(-p_half_width);
        // next right line
      } else {
        lines[1] = arr[i + 1].opposite().translate(-p_half_width);
        // next left line in opposite direction
      }

      lines[2] = arr[i].opposite().translate(-p_half_width);
      // current left line in opposite direction

      // create the back line of the offset shape
      Side currDirFromPrevDir = currDir.side_of(prevDir);
      // left turn from prevLine to currLine
      if (currDirFromPrevDir == Side.ON_THE_LEFT) {
        lines[3] = arr[i - 1].translate(-p_half_width);
        // previous line translated to the right
      } else {
        lines[3] = arr[i - 1].opposite().translate(-p_half_width);
        // previous left line in opposite direction
      }
      // cut off outstanding corners with following shapes
      FloatPoint cornerToCheck = null;
      Line currLine = lines[1];
      Line checkLine;
      if (nextDirFromCurrDir == Side.ON_THE_LEFT) {
        checkLine = lines[2];
      } else {
        checkLine = lines[0];
      }
      FloatPoint checkDistanceCorner = corner_approx(i);
      final double checkDistSquare = 2.0 * p_half_width * p_half_width;
      Collection<Line> cutDogEarLines = new LinkedList<>();
      Vector tmpCurrDir = nextDir;
      boolean directionChanged = false;
      for (int j = i + 2; j < arr.length - 1; j++) {
        if (corner_approx(j - 1).distance_square(checkDistanceCorner) > checkDistSquare) {
          break;
        }
        if (!directionChanged) {
          cornerToCheck = currLine.intersection_approx(checkLine);
        }
        Vector tmpNextDir = arr[j].direction().get_vector();
        Line nextBorderLine;
        Side tmpNextDirFromTmpCurrDir = tmpNextDir.side_of(tmpCurrDir);
        directionChanged = tmpNextDirFromTmpCurrDir != nextDirFromCurrDir;
        if (!directionChanged) {
          if (tmpNextDirFromTmpCurrDir == Side.ON_THE_LEFT) {
            nextBorderLine = arr[j].translate(-p_half_width);
          } else {
            nextBorderLine = arr[j].opposite().translate(-p_half_width);
          }

          if (nextBorderLine.side_of(cornerToCheck) == Side.ON_THE_LEFT
              && nextBorderLine.side_of(this.corner(i)) == Side.ON_THE_RIGHT
              && nextBorderLine.side_of(this.corner(i - 1)) == Side.ON_THE_RIGHT)
          // an outstanding corner
          {
            cutDogEarLines.add(nextBorderLine);
          }
          tmpCurrDir = tmpNextDir;
          currLine = nextBorderLine;
        }
      }
      // cut off outstanding corners with previous shapes
      checkDistanceCorner = corner_approx(i - 1);
      if (currDirFromPrevDir == Side.ON_THE_LEFT) {
        checkLine = lines[2];
      } else {
        checkLine = lines[0];
      }
      currLine = lines[3];
      tmpCurrDir = prevDir;
      directionChanged = false;
      for (int j = i - 2; j >= 1; j--) {
        if (corner_approx(j).distance_square(checkDistanceCorner) > checkDistSquare) {
          break;
        }
        if (!directionChanged) {
          cornerToCheck = currLine.intersection_approx(checkLine);
        }
        Vector tmpPrevDir = arr[j].direction().get_vector();
        Line prevBorderLine;
        Side tmpCurrDirFromTmpPrevDir = tmpCurrDir.side_of(tmpPrevDir);
        directionChanged = tmpCurrDirFromTmpPrevDir != currDirFromPrevDir;
        if (!directionChanged) {
          if (tmpCurrDir.side_of(tmpPrevDir) == Side.ON_THE_LEFT) {
            prevBorderLine = arr[j].translate(-p_half_width);
          } else {
            prevBorderLine = arr[j].opposite().translate(-p_half_width);
          }
          if (prevBorderLine.side_of(cornerToCheck) == Side.ON_THE_LEFT
              && prevBorderLine.side_of(this.corner(i)) == Side.ON_THE_RIGHT
              && prevBorderLine.side_of(this.corner(i - 1)) == Side.ON_THE_RIGHT)
          // an outstanding corner
          {
            cutDogEarLines.add(prevBorderLine);
          }
          tmpCurrDir = tmpPrevDir;
          currLine = prevBorderLine;
        }
      }
      TileShape s1 = TileShape.get_instance(lines);
      int cutLineCount = cutDogEarLines.size();
      if (cutLineCount > 0) {
        Line[] cutLines = new Line[cutLineCount];
        Iterator<Line> it = cutDogEarLines.iterator();
        for (int j = 0; j < cutLineCount; j++) {
          cutLines[j] = it.next();
        }
        s1 = s1.intersection(TileShape.get_instance(cutLines));
      }
      int currShapeNo = i - fromNo - 1;
      TileShape boundingShape;
      if (USE_BOUNDING_OCTAGON_FOR_OFFSET_SHAPES)
      // intersect with the bounding octagon
      {
        IntOctagon surrOct = bounding_octagon(i - 1, i);
        boundingShape = surrOct.offset(p_half_width);

      } else
      // intersect with the bounding box
      {
        IntBox surrBox = bounding_box(i - 1, i);
        IntBox offsetBox = surrBox.offset(p_half_width);
        boundingShape = offsetBox.to_Simplex();
      }
      shapeArr[currShapeNo] = boundingShape.intersection_with_simplify(s1);
      if (shapeArr[currShapeNo].is_empty()) {
        FRLogger.warn("offset_shapes: shape is empty");
      }

      prevDir = currDir;
      currDir = nextDir;
    }
    return shapeArr;
  }

  /**
   * Calculates for the p_no-th line segment a shape around this line where the right and left edge
   * lines have the distance p_half_width from the center line. 0 {@literal <}= p_no {@literal <}=
   * arr.length - 3
   */
  public TileShape offset_shape(int p_half_width, int p_no) {
    if (p_no < 0 || p_no > arr.length - 3) {
      FRLogger.warn("Polyline.offsetShape: p_no out of range");
      return null;
    }
    TileShape[] result = offset_shapes(p_half_width, p_no, p_no + 2);
    return result[0];
  }

  /**
   * Calculates for the p_no-th line segment a box shape around this line where the border lines
   * have the distance p_half_width from the center line. 0 {@literal <}= p_no {@literal <}=
   * arr.length - 3
   */
  public IntBox offset_box(int p_half_width, int p_no) {
    LineSegment currLineSegment = new LineSegment(this, p_no + 1);
    return currLineSegment.bounding_box().offset(p_half_width);
  }

  /** Returns the by p_vector translated polyline */
  public Polyline translate_by(Vector p_vector) {
    if (p_vector.equals(Vector.ZERO)) {
      return this;
    }
    Line[] newArr = new Line[arr.length];
    for (int i = 0; i < newArr.length; i++) {
      newArr[i] = arr[i].translate_by(p_vector);
    }
    return new Polyline(newArr);
  }

  /** Returns the polyline turned by p_factor times 90 degree around p_pole. */
  public Polyline turn_90_degree(int p_factor, IntPoint p_pole) {
    Line[] newArr = new Line[arr.length];
    for (int i = 0; i < newArr.length; i++) {
      newArr[i] = arr[i].turn_90_degree(p_factor, p_pole);
    }
    return new Polyline(newArr);
  }

  public Polyline rotate_approx(double p_angle, FloatPoint p_pole) {
    if (p_angle == 0) {
      return this;
    }
    IntPoint[] newCorners = new IntPoint[this.corner_count()];
    for (int i = 0; i < newCorners.length; i++) {

      newCorners[i] = this.corner_approx(i).rotate(p_angle, p_pole).round();
    }
    return new Polyline(newCorners);
  }

  /** Mirrors this polyline at the vertical line through p_pole */
  public Polyline mirror_vertical(IntPoint p_pole) {
    Line[] newArr = new Line[arr.length];
    for (int i = 0; i < newArr.length; i++) {
      newArr[i] = arr[i].mirror_vertical(p_pole);
    }
    return new Polyline(newArr);
  }

  /** Mirrors this polyline at the horizontal line through p_pole */
  public Polyline mirror_horizontal(IntPoint p_pole) {
    Line[] newArr = new Line[arr.length];
    for (int i = 0; i < newArr.length; i++) {
      newArr[i] = arr[i].mirror_horizontal(p_pole);
    }
    return new Polyline(newArr);
  }

  /**
   * Returns the smallest box containing the intersection points from index p_from_corner_no to
   * index p_to_corner_no of the lines of this polyline
   */
  public IntBox bounding_box(int p_from_corner_no, int p_to_corner_no) {
    int fromCornerNo = Math.max(p_from_corner_no, 0);
    int toCornerNo = Math.min(p_to_corner_no, arr.length - 2);
    double llx = Integer.MAX_VALUE;
    double lly = llx;
    double urx = Integer.MIN_VALUE;
    double ury = urx;
    for (int i = fromCornerNo; i <= toCornerNo; i++) {
      FloatPoint currCorner = corner_approx(i);
      llx = Math.min(llx, currCorner.x);
      lly = Math.min(lly, currCorner.y);
      urx = Math.max(urx, currCorner.x);
      ury = Math.max(ury, currCorner.y);
    }
    IntPoint lowerLeft = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
    IntPoint upperRight = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
    return new IntBox(lowerLeft, upperRight);
  }

  /** Returns the smallest box containing the intersection points of the lines of this polyline */
  public IntBox bounding_box() {
    if (precalculatedBoundingBox == null) {
      precalculatedBoundingBox = bounding_box(0, corner_count() - 1);
    }
    return precalculatedBoundingBox;
  }

  /**
   * Returns the smallest octagon containing the intersection points from index p_from_corner_no to
   * index p_to_corner_no of the lines of this polyline
   */
  public IntOctagon bounding_octagon(int p_from_corner_no, int p_to_corner_no) {
    int fromCornerNo = Math.max(p_from_corner_no, 0);
    int toCornerNo = Math.min(p_to_corner_no, arr.length - 2);
    double lx = Integer.MAX_VALUE;
    double ly = Integer.MAX_VALUE;
    double rx = Integer.MIN_VALUE;
    double uy = Integer.MIN_VALUE;
    double ulx = Integer.MAX_VALUE;
    double lrx = Integer.MIN_VALUE;
    double llx = Integer.MAX_VALUE;
    double urx = Integer.MIN_VALUE;
    for (int i = fromCornerNo; i <= toCornerNo; i++) {
      FloatPoint curr = corner_approx(i);
      lx = Math.min(lx, curr.x);
      ly = Math.min(ly, curr.y);
      rx = Math.max(rx, curr.x);
      uy = Math.max(uy, curr.y);
      double tmp = curr.x - curr.y;
      ulx = Math.min(ulx, tmp);
      lrx = Math.max(lrx, tmp);
      tmp = curr.x + curr.y;
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

  /** Calculates an approximation of the nearest point on this polyline to p_from_point. */
  public FloatPoint nearest_point_approx(FloatPoint p_from_point) {
    double minDistance = Double.MAX_VALUE;
    FloatPoint nearestPoint = null;
    // calculate the nearest corner point
    FloatPoint[] corners = corner_approx_arr();
    for (int i = 0; i < corners.length; i++) {
      double currDistance = corners[i].distance(p_from_point);
      if (currDistance < minDistance) {
        minDistance = currDistance;
        nearestPoint = corners[i];
      }
    }
    final double cTolerance = 1;
    for (int i = 1; i < arr.length - 1; i++) {
      FloatPoint projection = p_from_point.projection_approx(arr[i]);
      double currDistance = projection.distance(p_from_point);
      if (currDistance < minDistance) {
        // look, if the projection is inside the segment
        double segmentLength = corners[i].distance(corners[i - 1]);
        if (projection.distance(corners[i]) + projection.distance(corners[i - 1])
            < segmentLength + cTolerance) {
          minDistance = currDistance;
          nearestPoint = projection;
        }
      }
    }
    return nearestPoint;
  }

  /** Calculates the distance of p_from_point to the nearest point on this polyline */
  public double distance(FloatPoint p_from_point) {
    return p_from_point.distance(nearest_point_approx(p_from_point));
  }

  /**
   * Combines the two polylines, if they have a common end corner. The order of lines in this
   * polyline will be preserved. Returns the combined polyline or this polyline, if this polyline
   * and p_other have no common end corner. If there is something to combine at the start of this
   * polyline, p_other is inserted in front of this polyline. If there is something to combine at
   * the end of this polyline, this polyline is inserted in front of p_other.
   */
  public Polyline combine(Polyline p_other) {
    if (p_other == null || arr.length < 3 || p_other.arr.length < 3) {
      return this;
    }
    boolean combineAtStart;
    boolean combineOtherAtStart;
    if (first_corner().equals(p_other.first_corner())) {
      combineAtStart = true;
      combineOtherAtStart = true;
    } else if (first_corner().equals(p_other.last_corner())) {
      combineAtStart = true;
      combineOtherAtStart = false;
    } else if (last_corner().equals(p_other.first_corner())) {
      combineAtStart = false;
      combineOtherAtStart = true;
    } else if (last_corner().equals(p_other.last_corner())) {
      combineAtStart = false;
      combineOtherAtStart = false;
    } else {
      return this; // no common endpoint
    }
    Line[] lineArr = new Line[arr.length + p_other.arr.length - 2];
    if (combineAtStart) {
      // insert the lines of p_other in front
      if (combineOtherAtStart) {
        // insert in reverse order, skip the first line of p_other
        for (int i = 0; i < p_other.arr.length - 1; i++) {
          lineArr[i] = p_other.arr[p_other.arr.length - i - 1].opposite();
        }
      } else {
        // skip the last line of p_other
        System.arraycopy(p_other.arr, 0, lineArr, 0, p_other.arr.length - 1);
      }
      // append the lines of this polyline, skip the first line
      System.arraycopy(arr, 1, lineArr, p_other.arr.length - 1, arr.length - 1);
    } else {
      // insert the lines of this polyline in front, skip the last line
      System.arraycopy(arr, 0, lineArr, 0, arr.length - 1);
      if (combineOtherAtStart) {
        // skip the first line of p_other
        System.arraycopy(p_other.arr, 1, lineArr, arr.length - 1, p_other.arr.length - 1);
      } else {
        // insert in reverse order, skip the last line of p_other
        for (int i = 1; i < p_other.arr.length; i++) {
          lineArr[arr.length + i - 2] = p_other.arr[p_other.arr.length - i - 1].opposite();
        }
      }
    }
    return new Polyline(lineArr);
  }

  /**
   * Splits this polyline at the line with number p_line_no into two by inserting p_endline as
   * concluding line of the first split piece and as the start line of the second split piece.
   * p_endline and the line with number p_line_no must not be parallel. The order of the lines ins
   * the two result pieces is preserved. p_line_no must be bigger than 0 and less than arr.length -
   * 1. Returns null, if nothing was split.
   */
  public Polyline[] split(int p_line_no, Line p_end_line) {
    if (p_line_no < 1 || p_line_no > arr.length - 2) {
      FRLogger.warn("Polyline.split: p_line_no out of range");
      return null;
    }
    if (this.arr[p_line_no].is_parallel(p_end_line)) {
      return null;
    }
    Point newEndCorner = this.arr[p_line_no].intersection(p_end_line);
    FRLogger.trace(
        "Polyline.split",
        "compare_trace_split_called",
        "p_line_no="
            + p_line_no
            + ", arr.length="
            + arr.length
            + ", arr.length-2="
            + (arr.length - 2)
            + ", newEndCorner="
            + debug_point(newEndCorner)
            + " (type="
            + newEndCorner.getClass().getSimpleName()
            + ")"
            + ", lastCorner="
            + debug_point(this.last_corner())
            + " (type="
            + this.last_corner().getClass().getSimpleName()
            + ")"
            + ", equals="
            + newEndCorner.equals(this.last_corner()),
        "Polyline split p_line_no=" + p_line_no,
        new Point[] {this.first_corner(), newEndCorner, this.last_corner()});
    StringBuilder sb = new StringBuilder("    CORNERS:");
    for (int i = 0; i < this.corner_count(); i++) {
      sb.append(" ").append(this.corner_approx(i));
    }
    FRLogger.trace(
        "Polyline.split",
        "compare_trace_split_corners",
        sb.toString(),
        "Polyline split p_line_no=" + p_line_no,
        new Point[] {this.first_corner(), newEndCorner, this.last_corner()});
    if (p_line_no == 1 && newEndCorner.equals(this.first_corner())
        || p_line_no >= arr.length - 2 && newEndCorner.equals(this.last_corner())) {
      // No split, if p_end_line does not intersect, but touches
      // only this Polyline at an end point.
      return null;
    }
    Line[] firstPiece;
    if (this.corner(p_line_no - 1).equals(newEndCorner)) {
      // skip line segment of length 0 at the end of the first piece
      firstPiece = new Line[p_line_no + 1];
      System.arraycopy(arr, 0, firstPiece, 0, firstPiece.length);

    } else {
      firstPiece = new Line[p_line_no + 2];
      System.arraycopy(arr, 0, firstPiece, 0, p_line_no + 1);
      firstPiece[p_line_no + 1] = p_end_line;
    }
    Line[] secondPiece;
    if (this.corner(p_line_no).equals(newEndCorner)) {
      // skip line segment of length 0 at the beginning of the second piece
      secondPiece = new Line[arr.length - p_line_no];
      System.arraycopy(this.arr, p_line_no, secondPiece, 0, secondPiece.length);

    } else {
      secondPiece = new Line[arr.length - p_line_no + 1];
      secondPiece[0] = p_end_line;
      System.arraycopy(this.arr, p_line_no, secondPiece, 1, secondPiece.length - 1);
    }
    Polyline[] result = new Polyline[2];
    result[0] = new Polyline(firstPiece);
    result[1] = new Polyline(secondPiece);
    if (result[0].is_point() || result[1].is_point()) {
      return null;
    }
    return result;
  }

  /** create a new Polyline by skipping the lines of this Polyline from p_from_no to p_to_no */
  public Polyline skip_lines(int p_from_no, int p_to_no) {
    if (p_from_no < 0 || p_to_no > arr.length - 1 || p_from_no > p_to_no) {
      return this;
    }
    Line[] newLines = new Line[arr.length - (p_to_no - p_from_no + 1)];
    System.arraycopy(arr, 0, newLines, 0, p_from_no);
    System.arraycopy(arr, p_to_no + 1, newLines, p_from_no, newLines.length - p_from_no);
    return new Polyline(newLines);
  }

  public boolean contains(Point p_point) {
    for (int i = 1; i < arr.length - 1; i++) {
      LineSegment currSegment = new LineSegment(this, i);
      if (currSegment.contains(p_point)) {
        return true;
      }
    }
    return false;
  }

  private static String debug_point(Point p_point) {
    if (p_point instanceof IntPoint intPoint) {
      return "(" + intPoint.x + "," + intPoint.y + ")";
    }
    return String.valueOf(p_point);
  }

  /**
   * Creates a perpendicular line segment from p_from_point onto the nearest line segment of this
   * polyline to p_from_side. Returns null, if the perpendicular line does not intersect the nearest
   * line segment inside its segment bounds or if p_from_point is contained in this polyline.
   */
  public LineSegment projection_line(Point p_from_point) {
    if (p_from_point == null) {
      FRLogger.warn(
          "Polyline.projectionLine: p_from_point is null; returning null. This indicates a degenerate routing connection was attempted with an uninitialized endpoint.");
      return null;
    }
    FloatPoint fromPoint = p_from_point.to_float();
    double minDistance = Double.MAX_VALUE;
    Line resultLine = null;
    Line nearestLine = null;
    for (int i = 1; i < arr.length - 1; i++) {
      FloatPoint projection = fromPoint.projection_approx(arr[i]);
      double currDistance = projection.distance(fromPoint);
      if (currDistance < minDistance) {
        Direction directionTowardsLine = this.arr[i].perpendicular_direction(p_from_point);
        if (directionTowardsLine == null) {
          continue;
        }
        Line currResultLine = new Line(p_from_point, directionTowardsLine);
        Point prevCorner = this.corner(i - 1);
        Point nextCorner = this.corner(i);
        Side prevCornerSide = currResultLine.side_of(prevCorner);
        Side nextCornerSide = currResultLine.side_of(nextCorner);
        if (prevCornerSide == nextCornerSide && prevCornerSide != Side.COLLINEAR) {
          // the projection point is outside the line segment
          continue;
        }
        nearestLine = this.arr[i];
        minDistance = currDistance;
        resultLine = currResultLine;
      }
    }
    if (nearestLine == null) {
      return null;
    }
    Line startLine = new Line(p_from_point, nearestLine.direction());
    return new LineSegment(startLine, resultLine, nearestLine);
  }

  /**
   * Shortens this polyline to p_new_line_count lines. Additionally, the last line segment will be
   * approximately shortened to p_new_length. The last corner of the new polyline will be an
   * IntPoint.
   */
  public Polyline shorten(int p_new_line_count, double p_last_segment_length) {
    FloatPoint lastCorner = this.corner_approx(p_new_line_count - 2);
    FloatPoint prevLastCorner = this.corner_approx(p_new_line_count - 3);
    IntPoint newLastCorner =
        prevLastCorner.change_length(lastCorner, p_last_segment_length).round();
    if (newLastCorner.equals(this.corner(this.corner_count() - 2))) {
      // skip the last line
      return skip_lines(p_new_line_count - 1, p_new_line_count - 1);
    }
    Line[] newLines = new Line[p_new_line_count];
    System.arraycopy(arr, 0, newLines, 0, p_new_line_count - 2);
    // create the last 2 lines of the new polyline
    Point firstLinePoint = arr[p_new_line_count - 2].a;
    if (firstLinePoint.equals(newLastCorner)) {
      firstLinePoint = arr[p_new_line_count - 2].b;
    }
    Line newPrevLastLine = new Line(firstLinePoint, newLastCorner);
    newLines[p_new_line_count - 2] = newPrevLastLine;
    newLines[p_new_line_count - 1] =
        Line.get_instance(newLastCorner, newPrevLastLine.direction().turn_45_degree(6));
    return new Polyline(newLines);
  }
}

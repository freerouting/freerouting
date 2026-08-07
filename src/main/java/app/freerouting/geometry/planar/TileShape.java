package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Abstract class defining functionality for convex shapes, whose borders consists of straight
 * lines.
 */
public abstract class TileShape extends PolylineShape implements ConvexShape, Serializable {

  /** creates a Simplex as intersection of the halfplanes defined by an array of directed lines */
  public static TileShape get_instance(Line[] p_line_arr) {
    Simplex result = Simplex.get_instance(p_line_arr);
    return result.simplify();
  }

  /**
   * Creates a TileShape from a Point array, who forms the corners of the shape of a convex polygon.
   * May work only for IntPoints.
   */
  public static TileShape get_instance(Point[] p_convex_polygon) {
    Line[] lineArr = new Line[p_convex_polygon.length];
    for (int j = 0; j < lineArr.length - 1; j++) {
      lineArr[j] = new Line(p_convex_polygon[j], p_convex_polygon[j + 1]);
    }
    lineArr[lineArr.length - 1] =
        new Line(p_convex_polygon[lineArr.length - 1], p_convex_polygon[0]);
    return get_instance(lineArr);
  }

  /** creates a half_plane from a directed line */
  public static TileShape get_instance(Line p_line) {
    Line[] lines = new Line[1];
    lines[0] = p_line;
    return Simplex.get_instance(lines);
  }

  /**
   * Creates a normalized IntOctagon from the input values. For the meaning of the parameter
   * shortcuts see class IntOctagon.
   */
  public static IntOctagon get_instance(
      int p_lx, int p_ly, int p_rx, int p_uy, int p_ulx, int p_lrx, int p_llx, int p_urx) {
    IntOctagon oct = new IntOctagon(p_lx, p_ly, p_rx, p_uy, p_ulx, p_lrx, p_llx, p_urx);
    return oct.normalize();
  }

  /** creates a boxlike convex shape */
  public static IntOctagon get_instance(
      int p_lower_left_x, int p_lower_left_y, int p_upper_right_x, int p_upper_right_y) {
    IntBox box = new IntBox(p_lower_left_x, p_lower_left_y, p_upper_right_x, p_upper_right_y);
    return box.to_IntOctagon();
  }

  /** creates the smallest IntOctagon containing p_point */
  public static IntBox get_instance(Point p_point) {
    return p_point.surrounding_box();
  }

  /**
   * Tries to simplify the result shape to a simpler shape. Simplifying always in the intersection
   * function may cause performance problems.
   */
  public TileShape intersection_with_simplify(TileShape p_other) {
    TileShape result = this.intersection(p_other);
    return result.simplify();
  }

  /** Converts the physical instance of this shape to a simpler physical instance, if possible. */
  public abstract TileShape simplify();

  /** Returns a unique ID for this shape for deterministic tie-breaking. */
  public abstract int get_id_no();

  /** checks if this TileShape is an IntBox or can be converted into an IntBox */
  public abstract boolean is_IntBox();

  /** checks if this TileShape is an IntOctagon or can be converted into an IntOctagon */
  public abstract boolean is_IntOctagon();

  /** Returns the intersection of this shape with p_other */
  public abstract TileShape intersection(TileShape p_other);

  /**
   * Returns the p_no-th edge line of this shape for p_no between 0 and edge_line_count() - 1. The
   * edge lines are sorted in counterclock sense around the shape starting with the edge with the
   * smallest direction.
   */
  @Override
  public abstract Line border_line(int p_no);

  /** if p_line is a borderline of this shape the number of that edge is returned, otherwise -1 */
  public abstract int border_line_index(Line p_line);

  /** Converts the internal representation of this TieShape to a Simplex */
  public abstract Simplex to_Simplex();

  /**
   * Returns the content of the area of the shape. If the shape is unbounded, Double.MAX_VALUE is
   * returned.
   */
  @Override
  public double area() {
    if (!is_bounded()) {
      return Double.MAX_VALUE;
    }

    if (dimension() < 2) {
      return 0;
    }
    // calculate half of the absolute value of
    // x0 (y1 - yn-1) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ xn-1( y0 - yn-2)
    // where xi, yi are the coordinates of the i-th corner of this TileShape.

    double result = 0;
    int cornerCount = border_line_count();
    FloatPoint prevCorner = corner_approx(cornerCount - 2);
    FloatPoint currCorner = corner_approx(cornerCount - 1);
    for (int i = 0; i < cornerCount; i++) {
      FloatPoint nextCorner = corner_approx(i);
      result += currCorner.x * (nextCorner.y - prevCorner.y);
      prevCorner = currCorner;
      currCorner = nextCorner;
    }
    return 0.5 * Math.abs(result);
  }

  /** Returns true, if p_point is not contained in the inside or the edge of the shape */
  @Override
  public boolean is_outside(Point p_point) {
    int lineCount = border_line_count();
    if (lineCount == 0) {
      return true;
    }
    for (int i = 0; i < lineCount; i++) {
      if (border_line(i).side_of(p_point) == Side.ON_THE_LEFT) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contains(Point p_point) {
    return !is_outside(p_point);
  }

  /** Returns true, if p_point is contained in this shape, but not on an edge line */
  @Override
  public boolean contains_inside(Point p_point) {
    int lineCount = border_line_count();
    if (lineCount == 0) {
      return false;
    }
    for (int i = 0; i < lineCount; i++) {
      if (border_line(i).side_of(p_point) != Side.ON_THE_RIGHT) {
        return false;
      }
    }
    return true;
  }

  /** Returns true, if p_point is contained in this shape. */
  @Override
  public boolean contains(FloatPoint p_point) {
    return contains(p_point, 0);
  }

  /**
   * Returns true, if p_point is contained in this shape with tolerance p_tolerance. p_tolerance is
   * used when determining if a point is on the left side of a border line. It is used there in
   * calculating a determinant and is not the distance of p_point to the border.
   */
  public boolean contains(FloatPoint p_point, double p_tolerance) {
    int lineCount = border_line_count();
    if (lineCount == 0) {
      return false;
    }
    for (int i = 0; i < lineCount; i++) {
      if (border_line(i).side_of(p_point, p_tolerance) != Side.ON_THE_RIGHT) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns Side.COLLINEAR if p_point is on the border of this shape with tolerance p_tolerance.
   * p_tolerance is used when determining if a point is on the right side of a border line. It is
   * used there in calculating a determinant and is not the distance of p_point to the border.
   * Otherwise, the function returns Side.ON_THE_LEFT if p_point is outside of this shape, and
   * Side.ON_THE_RIGHT if p_point is inside this shape.
   */
  public Side side_of_border(FloatPoint p_point, double p_tolerance) {
    int lineCount = border_line_count();
    if (lineCount == 0) {
      return Side.COLLINEAR;
    }
    Side result = Side.ON_THE_RIGHT; // point is inside
    for (int i = 0; i < lineCount; i++) {
      Side currSide = border_line(i).side_of(p_point, p_tolerance);
      if (currSide == Side.ON_THE_LEFT) {
        return Side.ON_THE_LEFT; // point is outside
      } else if (currSide == Side.COLLINEAR) {
        result = currSide;
      }
    }
    return result;
  }

  /**
   * If p_point lies on the border of this shape, the number of the edge line segment containing
   * p_point is returned, otherwise -1 is returned.
   */
  public int contains_on_border_line_no(Point p_point) {
    int lineCount = border_line_count();
    if (lineCount == 0) {
      return -1;
    }
    int containingLineNo = -1;
    for (int i = 0; i < lineCount; i++) {
      Side sideOf = border_line(i).side_of(p_point);
      if (sideOf == Side.ON_THE_LEFT) {
        // p_point outside the convex shape
        return -1;
      }
      if (sideOf == Side.COLLINEAR) {
        containingLineNo = i;
      }
    }
    return containingLineNo;
  }

  /** Returns true, if p_point lies exact on the boundary of the shape */
  @Override
  public boolean contains_on_border(Point p_point) {
    return contains_on_border_line_no(p_point) >= 0;
  }

  /**
   * Returns true, if this shape contains p_other completely. THere may be some numerical
   * inaccuracy.
   */
  public boolean contains_approx(TileShape p_other) {
    FloatPoint[] corners = p_other.corner_approx_arr();
    for (FloatPoint currCorner : corners) {
      if (!this.contains(currCorner)) {
        return false;
      }
    }
    return true;
  }

  /** Returns true, if this shape contains p_other completely. */
  public boolean contains(TileShape p_other) {
    for (int i = 0; i < p_other.border_line_count(); i++) {
      if (!this.contains(p_other.corner(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the distance between p_point and its nearest point on the shape. 0, if p_point is
   * contained in this shape
   */
  @Override
  public double distance(FloatPoint p_point) {
    FloatPoint nearestPoint = nearest_point_approx(p_point);
    return nearestPoint.distance(p_point);
  }

  /** Returns the distance between p_point and its nearest point on the edge of the shape. */
  @Override
  public double border_distance(FloatPoint p_point) {
    FloatPoint nearestPoint = nearest_border_point_approx(p_point);
    return nearestPoint.distance(p_point);
  }

  @Override
  public double smallest_radius() {
    return border_distance(centre_of_gravity());
  }

  /**
   * Returns the point in this shape, which has the smallest distance to p_from_point. p_from_point,
   * if that point is contained in this shape
   */
  public Point nearest_point(Point p_from_point) {
    if (!is_outside(p_from_point)) {
      return p_from_point;
    }
    return nearest_border_point(p_from_point);
  }

  @Override
  public FloatPoint nearest_point_approx(FloatPoint p_from_point) {
    if (this.contains(p_from_point)) {
      return p_from_point;
    }
    return nearest_border_point_approx(p_from_point);
  }

  /** Returns the nearest point to p_from_point on the edge of the shape */
  public Point nearest_border_point(Point p_from_point) {
    int lineCount = border_line_count();
    if (lineCount == 0) {
      return null;
    }
    FloatPoint fromPointF = p_from_point.to_float();
    if (lineCount == 1) {
      return border_line(0).perpendicular_projection(p_from_point);
    }
    double minDist = Double.MAX_VALUE;
    int minDistInd = 0;

    // calculate the distance to the nearest corner first
    for (int i = 0; i < lineCount; i++) {
      FloatPoint currCornerF = corner_approx(i);
      double currDist = currCornerF.distance_square(fromPointF);
      if (currDist < minDist) {
        minDist = currDist;
        minDistInd = i;
      }
    }

    Point nearestPoint = corner(minDistInd);

    int prevInd = lineCount - 2;
    int currInd = lineCount - 1;

    for (int nextInd = 0; nextInd < lineCount; nextInd++) {
      Point projection = border_line(currInd).perpendicular_projection(p_from_point);
      if ((!corner_is_bounded(currInd)
              || border_line(prevInd).side_of(projection) == Side.ON_THE_RIGHT)
          && (!corner_is_bounded(nextInd)
              || border_line(nextInd).side_of(projection) == Side.ON_THE_RIGHT)) {
        FloatPoint projectionF = projection.to_float();
        double currDist = projectionF.distance_square(fromPointF);
        if (currDist < minDist) {
          minDist = currDist;
          nearestPoint = projection;
        }
      }
      prevInd = currInd;
      currInd = nextInd;
    }
    return nearestPoint;
  }

  /** Returns an approximation of the nearest point to p_from_point on the border of this shape */
  public FloatPoint nearest_border_point_approx(FloatPoint p_from_point) {
    FloatPoint[] nearestPoints = nearest_border_points_approx(p_from_point, 1);
    if (nearestPoints.length == 0) {
      return null;
    }
    return nearestPoints[0];
  }

  /**
   * Returns an approximation of the p_count nearest points to p_from_point on the border of this
   * shape. The result points must be located on different border lines and are sorted in ascending
   * order (the nearest point comes first).
   */
  public FloatPoint[] nearest_border_points_approx(FloatPoint p_from_point, int p_count) {
    if (p_count <= 0) {
      return new FloatPoint[0];
    }
    int lineCount = border_line_count();
    int resultCount = Math.min(p_count, lineCount);
    if (lineCount == 0) {
      return new FloatPoint[0];
    }
    if (lineCount == 1) {
      FloatPoint[] result = new FloatPoint[1];
      result[0] = p_from_point.projection_approx(border_line(0));
      return result;
    }
    if (this.dimension() == 0) {
      FloatPoint[] result = new FloatPoint[1];
      result[0] = corner_approx(0);
      return result;
    }
    FloatPoint[] nearestPoints = new FloatPoint[resultCount];
    double[] minDists = new double[resultCount];
    Arrays.fill(minDists, Double.MAX_VALUE);

    // calculate the distances to the nearest corners first
    for (int i = 0; i < lineCount; i++) {
      if (corner_is_bounded(i)) {
        FloatPoint currCorner = corner_approx(i);
        double currDist = currCorner.distance_square(p_from_point);
        for (int j = 0; j < resultCount; j++) {
          if (currDist < minDists[j]) {
            for (int k = j + 1; k < resultCount; k++) {
              minDists[k] = minDists[k - 1];
              nearestPoints[k] = nearestPoints[k - 1];
            }
            minDists[j] = currDist;
            nearestPoints[j] = currCorner;
            break;
          }
        }
      }
    }

    int prevInd = lineCount - 2;
    int currInd = lineCount - 1;

    for (int nextInd = 0; nextInd < lineCount; nextInd++) {
      FloatPoint projection = p_from_point.projection_approx(border_line(currInd));
      if ((!corner_is_bounded(currInd)
              || border_line(prevInd).side_of(projection) == Side.ON_THE_RIGHT)
          && (!corner_is_bounded(nextInd)
              || border_line(nextInd).side_of(projection) == Side.ON_THE_RIGHT)) {
        double currDist = projection.distance_square(p_from_point);
        for (int j = 0; j < resultCount; j++) {
          if (currDist < minDists[j]) {
            for (int k = j + 1; k < resultCount; k++) {
              minDists[k] = minDists[k - 1];
              nearestPoints[k] = nearestPoints[k - 1];
            }
            minDists[j] = currDist;
            nearestPoints[j] = projection;
            break;
          }
        }
      }
      prevInd = currInd;
      currInd = nextInd;
    }
    return nearestPoints;
  }

  /** Returns the number of the nearest corner of the shape to p_from_point */
  public int index_of_nearest_corner(Point p_from_point) {
    FloatPoint fromPointF = p_from_point.to_float();
    int result = 0;
    int cornerCount = border_line_count();
    double minDist = Double.MIN_VALUE;
    for (int i = 0; i < cornerCount; i++) {
      double currDist = corner_approx(i).distance(fromPointF);
      if (currDist < minDist) {
        minDist = currDist;
        result = i;
      }
    }
    return result;
  }

  /**
   * Returns a line segment consisting of an approximations of the corners with index 0 and
   * cornerCount / 2.
   */
  public FloatLine diagonal_corner_segment() {
    if (this.is_empty()) {
      return null;
    }
    FloatPoint firstCorner = this.corner_approx(0);
    FloatPoint lastCorner = this.corner_approx(this.border_line_count() / 2);
    return new FloatLine(firstCorner, lastCorner);
  }

  /**
   * Returns an approximation of the p_count nearest relative outside locations of p_shape in the
   * direction of different border lines of this shape. These relative locations are sorted in
   * ascending order (the shortest comes first).
   */
  public FloatPoint[] nearest_relative_outside_locations(TileShape p_shape, int p_count) {
    int lineCount = border_line_count();
    if (p_count <= 0 || lineCount < 3 || !this.intersects(p_shape)) {
      return new FloatPoint[0];
    }

    int resultCount = Math.min(p_count, lineCount);

    FloatPoint[] translateCoors = new FloatPoint[resultCount];
    double[] minDists = new double[resultCount];
    Arrays.fill(minDists, Double.MAX_VALUE);

    int currInd = lineCount - 1;

    int otherLineCount = p_shape.border_line_count();

    for (int nextInd = 0; nextInd < lineCount; nextInd++) {
      double currMaxDist = 0;
      FloatPoint currTranslateCoor = FloatPoint.ZERO;
      for (int cornerNo = 0; cornerNo < otherLineCount; cornerNo++) {
        FloatPoint currCorner = p_shape.corner_approx(cornerNo);
        if (border_line(currInd).side_of(currCorner) == Side.ON_THE_RIGHT) {
          FloatPoint projection = currCorner.projection_approx(border_line(currInd));
          double currDist = projection.distance_square(currCorner);
          if (currDist > currMaxDist) {
            currMaxDist = currDist;
            currTranslateCoor = projection.subtract(currCorner);
          }
        }
      }

      for (int j = 0; j < resultCount; j++) {
        if (currMaxDist < minDists[j]) {
          for (int k = j + 1; k < resultCount; k++) {
            minDists[k] = minDists[k - 1];
            translateCoors[k] = translateCoors[k - 1];
          }
          minDists[j] = currMaxDist;
          translateCoors[j] = currTranslateCoor;
          break;
        }
      }
      currInd = nextInd;
    }
    return translateCoors;
  }

  @Override
  public ConvexShape shrink(double p_offset) {
    ConvexShape result = this.offset(-p_offset);
    if (result.is_empty()) {
      IntBox centreBox = this.centre_of_gravity().bounding_box();
      result = this.intersection(centreBox);
    }
    return result;
  }

  /**
   * Returns the maximum of the edge widths of the shape. Only defined when the shape is bounded.
   */
  public double length() {
    if (!this.is_bounded()) {
      return Integer.MAX_VALUE;
    }
    int dimension = this.dimension();
    if (dimension <= 0) {
      return 0;
    }
    if (dimension == 1) {
      return this.circumference() / 2;
    }
    // now the shape is 2-dimensional
    double maxDistance = -1;
    double maxDistance2 = -1;
    FloatPoint gravityPoint = this.centre_of_gravity();
    for (int i = 0; i < border_line_count(); i++) {
      double currDistance = Math.abs(border_line(i).signed_distance(gravityPoint));
      if (currDistance > maxDistance) {
        maxDistance2 = maxDistance;
        maxDistance = currDistance;
      } else if (currDistance > maxDistance2) {
        maxDistance2 = currDistance;
      }
    }
    return maxDistance + maxDistance2;
  }

  /**
   * Calculates, if this Shape and p_other have a common border piece and returns an 2 dimensional
   * array with the indices in this shape and p_other of the touching edge lines in this case.
   * Otherwise, an array of dimension 0 is returned. Used if the intersection shape is
   * 1-dimensional.
   */
  public int[] touching_sides(TileShape p_other) {
    // search the first edge line of p_other with reverse direction >= right

    int sideNo2 = -1;
    Direction dir2 = null;
    for (int i = 0; i < p_other.border_line_count(); i++) {
      Direction currDir = p_other.border_line(i).direction();
      if (currDir.compareTo(Direction.LEFT) >= 0) {
        sideNo2 = i;
        dir2 = currDir.opposite();
        break;
      }
    }
    if (dir2 == null) {
      FRLogger.warn("touching_side : dir2 not found");
      return new int[0];
    }
    int sideNo1 = 0;
    Direction dir1 = this.border_line(0).direction();
    final int maxInd = this.border_line_count() + p_other.border_line_count();

    for (int i = 0; i < maxInd; i++) {
      int compare = dir2.compareTo(dir1);
      if (compare == 0) {
        if (this.border_line(sideNo1).is_equal_or_opposite(p_other.border_line(sideNo2))) {
          int[] result = new int[2];
          result[0] = sideNo1;
          result[1] = sideNo2;
          return result;
        }
      }
      if (compare >= 0) // dir2 is bigger than dir1
      {
        sideNo1 = (sideNo1 + 1) % this.border_line_count();
        dir1 = this.border_line(sideNo1).direction();
      } else // dir1 is bigger than dir2
      {
        sideNo2 = (sideNo2 + 1) % p_other.border_line_count();
        dir2 = p_other.border_line(sideNo2).direction().opposite();
      }
    }
    return new int[0];
  }

  /**
   * Calculates the minimal distance of p_line to this shape, assuming, that p_line is on the left
   * of this shape. Returns -1, if p_line is on the right of this shape or intersects with the
   * interior of this shape.
   */
  public double distance_to_the_left(Line p_line) {
    double result = Integer.MAX_VALUE;
    for (int i = 0; i < this.border_line_count(); i++) {
      FloatPoint currCorner = this.corner_approx(i);
      Side lineSide = p_line.side_of(currCorner, 1);
      if (lineSide == Side.COLLINEAR) {
        lineSide = p_line.side_of(this.corner(i));
      }
      if (lineSide == Side.ON_THE_RIGHT) {
        // currPoint would be outside the result shape
        result = -1;
        break;
      }
      result = Math.min(result, p_line.signed_distance(currCorner));
    }
    return result;
  }

  /**
   * Returns Side.COLLINEAR, if p_line intersects with the interior of this shape, Side.ON_THE_LEFT,
   * if this shape is completely on the left of p_line or Side.ON_THE_RIGHT, if this shape is
   * completely on the right of p_line.
   */
  public Side side_of(Line p_line) {
    boolean onTheLeft = false;
    boolean onTheRight = false;
    for (int i = 0; i < this.border_line_count(); i++) {
      Side currSide = p_line.side_of(this.corner(i));
      if (currSide == Side.ON_THE_LEFT) {
        onTheRight = true;
      } else if (currSide == Side.ON_THE_RIGHT) {
        onTheLeft = true;
      }
      if (onTheLeft && onTheRight) {
        return Side.COLLINEAR;
      }
    }
    Side result;
    if (onTheLeft) {
      result = Side.ON_THE_LEFT;
    } else {
      result = Side.ON_THE_RIGHT;
    }
    return result;
  }

  @Override
  public TileShape turn_90_degree(int p_factor, IntPoint p_pole) {
    Line[] newLines = new Line[border_line_count()];
    for (int i = 0; i < newLines.length; i++) {
      newLines[i] = this.border_line(i).turn_90_degree(p_factor, p_pole);
    }
    return get_instance(newLines);
  }

  @Override
  public TileShape rotate_approx(double p_angle, FloatPoint p_pole) {
    if (p_angle == 0) {
      return this;
    }
    IntPoint[] newCorners = new IntPoint[border_line_count()];
    for (int i = 0; i < newCorners.length; i++) {

      newCorners[i] = this.corner_approx(i).rotate(p_angle, p_pole).round();
    }
    Polygon cornerPolygon = new Polygon(newCorners);
    Point[] polygonCorners = cornerPolygon.corner_array();
    TileShape result;
    if (polygonCorners.length >= 3) {
      result = get_instance(polygonCorners);
    } else if (polygonCorners.length == 2) {
      Polyline currPolyline = new Polyline(polygonCorners);
      LineSegment currSegment = new LineSegment(currPolyline, 0);
      result = currSegment.to_simplex();
    } else if (polygonCorners.length == 1) {
      result = get_instance(polygonCorners[0]);
    } else {
      result = Simplex.EMPTY;
    }
    return result;
  }

  @Override
  public TileShape mirror_vertical(IntPoint p_pole) {
    Line[] newLines = new Line[border_line_count()];
    for (int i = 0; i < newLines.length; i++) {
      newLines[i] = this.border_line(i).mirror_vertical(p_pole);
    }
    return get_instance(newLines);
  }

  @Override
  public TileShape mirror_horizontal(IntPoint p_pole) {
    Line[] newLines = new Line[border_line_count()];
    for (int i = 0; i < newLines.length; i++) {
      newLines[i] = this.border_line(i).mirror_horizontal(p_pole);
    }
    return get_instance(newLines);
  }

  /**
   * Calculates the border line of this shape intersecting the ray from p_from_point into the
   * direction p_direction. p_from_point is assumed to be inside this shape, otherwise -1 is
   * returned.
   */
  public int intersecting_border_line_no(Point p_from_point, Direction p_direction) {
    if (!this.contains(p_from_point)) {
      return -1;
    }
    FloatPoint fromPoint = p_from_point.to_float();
    Line intersectionLine = new Line(p_from_point, p_direction);
    FloatPoint secondLinePoint = intersectionLine.b.to_float();
    int result = -1;
    double minDistance = Float.MAX_VALUE;
    for (int i = 0; i < this.border_line_count(); i++) {
      Line currBorderLine = this.border_line(i);
      FloatPoint currIntersection = currBorderLine.intersection_approx(intersectionLine);
      if (currIntersection.x >= Integer.MAX_VALUE) {
        continue; // lines are parallel
      }
      double currDistance = currIntersection.distance_square(fromPoint);
      if (currDistance < minDistance) {
        boolean directionOk =
            currBorderLine.side_of(secondLinePoint) == Side.ON_THE_LEFT
                || secondLinePoint.distance_square(currIntersection) < currDistance;
        if (directionOk) {
          result = i;
          minDistance = currDistance;
        }
      }
    }
    return result;
  }

  /** Cuts p_shape out of this shape and divides the result into convex pieces */
  public abstract TileShape[] cutout(TileShape p_shape);

  /**
   * Returns an array of tuples of integers. The length of the array is the number of points, where
   * p_polyline enters or leaves the interior of this shape. The first coordinate of the tuple is
   * the number of the line segment of p_polyline, which enters the simplex and the second
   * coordinate of the tuple is the number of the edge_line of the simplex, which is crossed there.
   * That means that the entrance point is the intersection of this 2 lines.
   */
  public int[][] entrance_points(Polyline p_polyline) {
    int[][] result = new int[2 * p_polyline.arr.length][2];
    int intersectionCount = 0;
    int prevIntersectionLineNo = -1;
    int prevIntersectionEdgeNo = -1;
    for (int lineNo = 1; lineNo < p_polyline.arr.length - 1; lineNo++) {
      LineSegment currLineSeg = new LineSegment(p_polyline, lineNo);
      int[] currIntersections = currLineSeg.border_intersections(this);
      for (int i = 0; i < currIntersections.length; i++) {
        int edgeNo = currIntersections[i];
        if (lineNo != prevIntersectionLineNo || edgeNo != prevIntersectionEdgeNo) {
          result[intersectionCount][0] = lineNo;
          result[intersectionCount][1] = edgeNo;
          ++intersectionCount;
          prevIntersectionLineNo = lineNo;
          prevIntersectionEdgeNo = edgeNo;
        }
      }
    }
    return Arrays.copyOf(result, intersectionCount);
  }

  /**
   * Cuts out the parts of p_polyline in the interior of this shape and returns a list of the
   * remaining pieces of p_polyline. Pieces completely contained in the border of this shape are not
   * returned.
   */
  @Override
  public Polyline[] cutout(Polyline p_polyline) {
    int[][] intersectionNo = this.entrance_points(p_polyline);
    Point firstCorner = p_polyline.first_corner();
    boolean firstCornerIsInside = this.contains_inside(firstCorner);
    if (intersectionNo.length == 0)
    // no intersections
    {
      if (firstCornerIsInside)
      // p_polyline is contained completely in this shape
      {
        return new Polyline[0];
      }
      // p_polyline is completely outside
      Polyline[] result = new Polyline[1];
      result[0] = p_polyline;
      return result;
    }
    Collection<Polyline> pieces = new LinkedList<>();
    int currIntersectionNo = 0;
    int[] currIntersectionTuple = intersectionNo[currIntersectionNo];
    Point firstIntersection =
        p_polyline.arr[currIntersectionTuple[0]].intersection(
            this.border_line(currIntersectionTuple[1]));
    if (!firstCornerIsInside)
    // calculate outside piece at start
    {
      if (!firstCorner.equals(firstIntersection))
      // otherwise skip 1 point outside polyline at the start
      {
        int currPolylineIntersectionNo = currIntersectionTuple[0];
        Line[] currLines = new Line[currPolylineIntersectionNo + 2];
        System.arraycopy(p_polyline.arr, 0, currLines, 0, currPolylineIntersectionNo + 1);
        // close the polyline piece with the intersected edge line.
        currLines[currPolylineIntersectionNo + 1] = this.border_line(currIntersectionTuple[1]);
        Polyline currPiece = new Polyline(currLines);
        if (!currPiece.is_empty()) {
          pieces.add(currPiece);
        }
      }
      ++currIntersectionNo;
    }
    while (currIntersectionNo < intersectionNo.length - 1)
    // calculate the next outside polyline piece
    {
      currIntersectionTuple = intersectionNo[currIntersectionNo];
      int[] nextIntersectionTuple = intersectionNo[currIntersectionNo + 1];
      int currIntersectionNoOfPolyline = currIntersectionTuple[0];
      int nextIntersectionNoOfPolyline = nextIntersectionTuple[0];
      // check that at least 1 corner of p_polyline with number
      // between currIntersectionNoOfPolyline and
      // nextIntersectionNoOfPolyline
      // is not contained in this shape. Otherwise, the part of p_polyline
      // between this intersections is completely contained in the border
      // and can be ignored
      boolean insertPiece = false;
      for (int i = currIntersectionNoOfPolyline + 1; i < nextIntersectionNoOfPolyline; i++) {
        if (this.is_outside(p_polyline.corner(i))) {
          insertPiece = true;
          break;
        }
      }

      if (insertPiece) {
        Line[] currLines =
            new Line[nextIntersectionNoOfPolyline - currIntersectionNoOfPolyline + 3];
        currLines[0] = this.border_line(currIntersectionTuple[1]);
        System.arraycopy(
            p_polyline.arr, currIntersectionNoOfPolyline, currLines, 1, currLines.length - 2);
        currLines[currLines.length - 1] = this.border_line(nextIntersectionTuple[1]);
        Polyline currPiece = new Polyline(currLines);
        if (!currPiece.is_empty()) {
          pieces.add(currPiece);
        }
      }
      currIntersectionNo += 2;
    }
    if (currIntersectionNo <= intersectionNo.length - 1)
    // calculate outside piece at end
    {
      currIntersectionTuple = intersectionNo[currIntersectionNo];
      int currPolylineIntersectionNo = currIntersectionTuple[0];
      Line[] currLines = new Line[p_polyline.arr.length - currPolylineIntersectionNo + 1];
      currLines[0] = this.border_line(currIntersectionTuple[1]);
      System.arraycopy(
          p_polyline.arr, currPolylineIntersectionNo, currLines, 1, currLines.length - 1);
      Polyline currPiece = new Polyline(currLines);
      if (!currPiece.is_empty()) {
        pieces.add(currPiece);
      }
    }
    Polyline[] result = new Polyline[pieces.size()];
    Iterator<Polyline> it = pieces.iterator();
    for (int i = 0; i < result.length; i++) {
      result[i] = it.next();
    }
    return result;
  }

  @Override
  public TileShape[] split_to_convex() {
    TileShape[] result = new TileShape[1];
    result[0] = this;
    return result;
  }

  /**
   * Divides this shape into sections with width and height at most p_max_section_width of about
   * equal size.
   */
  public TileShape[] divide_into_sections(double p_max_section_width) {
    if (this.is_empty()) {
      TileShape[] result = new TileShape[1];
      result[0] = this;
      return result;
    }
    TileShape[] sectionBoxes = this.bounding_box().divide_into_sections(p_max_section_width);
    Collection<TileShape> sectionList = new LinkedList<>();
    for (int i = 0; i < sectionBoxes.length; i++) {
      TileShape currSection = this.intersection_with_simplify(sectionBoxes[i]);
      if (currSection.dimension() == 2) {
        sectionList.add(currSection);
      }
    }
    TileShape[] result = new TileShape[sectionList.size()];
    Iterator<TileShape> it = sectionList.iterator();
    for (int i = 0; i < result.length; i++) {
      result[i] = it.next();
    }
    return result;
  }

  /** Checks, if p_line_segment has a common point with the interior of this shape. */
  public boolean is_intersected_interior_by(LineSegment p_line_segment) {
    return is_intersected_interior_by(
        p_line_segment.start_point(), p_line_segment.end_point(), p_line_segment.get_line());
  }

  /**
   * Checks if the line segment defined by p_start_point, p_end_point and p_line has a common point
   * with the interior of this shape.
   */
  public boolean is_intersected_interior_by(Point p_start_point, Point p_end_point, Line p_line) {
    FloatPoint floatStartPoint = p_start_point.to_float();
    FloatPoint floatEndPoint = p_end_point.to_float();

    Side[] borderLineSideOfStartPointArr = new Side[this.border_line_count()];
    Side[] borderLineSideOfEndPointArr = new Side[borderLineSideOfStartPointArr.length];
    for (int i = 0; i < borderLineSideOfStartPointArr.length; i++) {
      Line currBorderLine = this.border_line(i);
      Side borderLineSideOfStartPoint = currBorderLine.side_of(floatStartPoint, 1);
      if (borderLineSideOfStartPoint == Side.COLLINEAR) {
        borderLineSideOfStartPoint = currBorderLine.side_of(p_start_point);
      }
      Side borderLineSideOfEndPoint = currBorderLine.side_of(floatEndPoint, 1);
      if (borderLineSideOfEndPoint == Side.COLLINEAR) {
        borderLineSideOfEndPoint = currBorderLine.side_of(p_end_point);
      }
      if (borderLineSideOfStartPoint != Side.ON_THE_RIGHT
          && borderLineSideOfEndPoint != Side.ON_THE_RIGHT) {
        // both endpoints are outside the borderLine,
        // no intersection possible
        return false;
      }
      borderLineSideOfStartPointArr[i] = borderLineSideOfStartPoint;
      borderLineSideOfEndPointArr[i] = borderLineSideOfEndPoint;
    }
    boolean startPointIsInside = true;
    for (int i = 0; i < borderLineSideOfStartPointArr.length; i++) {
      if (borderLineSideOfStartPointArr[i] != Side.ON_THE_RIGHT) {
        startPointIsInside = false;
        break;
      }
    }
    if (startPointIsInside) {
      return true;
    }
    boolean endPointIsInside = true;
    for (int i = 0; i < borderLineSideOfEndPointArr.length; i++) {
      if (borderLineSideOfEndPointArr[i] != Side.ON_THE_RIGHT) {
        endPointIsInside = false;
        break;
      }
    }
    if (endPointIsInside) {
      return true;
    }
    Line segmentLine = p_line;
    // Check, if this line segments intersect a border line of p_shape.
    for (int i = 0; i < borderLineSideOfStartPointArr.length; i++) {
      Side borderLineSideOfStartPoint = borderLineSideOfStartPointArr[i];
      Side borderLineSideOfEndPoint = borderLineSideOfEndPointArr[i];
      if (borderLineSideOfStartPoint != borderLineSideOfEndPoint) {
        if (borderLineSideOfStartPoint == Side.COLLINEAR
                && borderLineSideOfEndPoint == Side.ON_THE_LEFT
            || borderLineSideOfEndPoint == Side.COLLINEAR
                && borderLineSideOfStartPoint == Side.ON_THE_LEFT) {
          // the interior of p_shape is not intersected.
          continue;
        }
        Side prevCornerSide = segmentLine.side_of(this.corner_approx(i), 1);
        if (prevCornerSide == Side.COLLINEAR) {
          prevCornerSide = segmentLine.side_of(this.corner(i));
        }
        int nextCornerIndex;
        if (i == borderLineSideOfStartPointArr.length - 1) {
          nextCornerIndex = 0;
        } else {
          nextCornerIndex = i + 1;
        }
        Side nextCornerSide = segmentLine.side_of(this.corner_approx(nextCornerIndex), 1);
        if (nextCornerSide == Side.COLLINEAR) {
          nextCornerSide = segmentLine.side_of(this.corner(nextCornerIndex));
        }
        if (prevCornerSide == Side.ON_THE_LEFT && nextCornerSide == Side.ON_THE_RIGHT
            || prevCornerSide == Side.ON_THE_RIGHT && nextCornerSide == Side.ON_THE_LEFT) {
          // this line segment crosses a border line of p_shape
          return true;
        }
      }
    }
    return false;
  }

  // auxiliary functions needed because the virtual function mechanism does
  // not work in parameter position
  abstract TileShape intersection(Simplex p_other);

  abstract TileShape intersection(IntOctagon p_other);

  abstract TileShape intersection(IntBox p_other);

  /** Auxiliary function to implement the public function cutout(TileShape p_shape) */
  abstract TileShape[] cutout_from(IntBox p_shape);

  /** Auxiliary function to implement the public function cutout(TileShape p_shape) */
  abstract TileShape[] cutout_from(IntOctagon p_shape);

  /** Auxiliary function to implement the public function cutout(TileShape p_shape) */
  abstract TileShape[] cutout_from(Simplex p_shape);
}

package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

/**
 * Shape described bei a closed polygon of corner points. The corners are ordered in counterclock
 * sense around the border of the shape. The corners are normalised, so that the corner with the
 * lowest y-value comes first. In case of equal y-value the corner with the lowest x-value comes
 * first.
 */
public class PolygonShape extends PolylineShape {

  private static final int seed = 99;
  private static final Random randomGenerator = new Random(seed);
  public final Point[] corners;

  /** the following fields are for storing precalculated data */
  private transient IntBox precalculatedBoundingBox;

  private transient IntOctagon precalculatedBoundingOctagon;
  private transient TileShape[] precalculatedConvexPieces;

  /** Creates a new instance of PolygonShape */
  public PolygonShape(Polygon p_polygon) {
    Polygon currPolygon = p_polygon;
    if (p_polygon.winding_number_after_closing() < 0) {
      // the corners of the polygon are in clockwise sense
      currPolygon = p_polygon.revert_corners();
    }
    Point[] currCorners = currPolygon.corner_array();
    int lastCornerNo = currCorners.length - 1;

    if (lastCornerNo > 0) {
      if (currCorners[0].equals(currCorners[lastCornerNo])) {
        // skip last point
        --lastCornerNo;
      }
    }

    boolean lastPointCollinear = false;

    if (lastCornerNo >= 2) {
      lastPointCollinear =
          currCorners[lastCornerNo].side_of(currCorners[lastCornerNo - 1], currCorners[0])
              == Side.COLLINEAR;
    }
    if (lastPointCollinear) {
      // skip last point
      --lastCornerNo;
    }

    int firstCornerNo = 0;
    boolean firstPointCollinear = false;

    if (lastCornerNo - firstCornerNo >= 2) {
      firstPointCollinear =
          currCorners[0].side_of(currCorners[1], currCorners[lastCornerNo]) == Side.COLLINEAR;
    }

    if (firstPointCollinear) {
      // skip first point
      ++firstCornerNo;
    }
    // search the point with the lowest y and then with the lowest x
    int startCornerNo = firstCornerNo;
    FloatPoint startCorner = currCorners[startCornerNo].to_float();
    for (int i = startCornerNo + 1; i <= lastCornerNo; i++) {
      FloatPoint currCorner = currCorners[i].to_float();
      if (currCorner.y < startCorner.y
          || currCorner.y == startCorner.y && currCorner.x < startCorner.x) {
        startCornerNo = i;
        startCorner = currCorner;
      }
    }
    int newCornerCount = lastCornerNo - firstCornerNo + 1;
    Point[] result = new Point[newCornerCount];
    int currCornerNo = 0;
    for (int i = startCornerNo; i <= lastCornerNo; i++) {
      result[currCornerNo] = currCorners[i];
      ++currCornerNo;
    }
    for (int i = firstCornerNo; i < startCornerNo; i++) {
      result[currCornerNo] = currCorners[i];
      ++currCornerNo;
    }
    corners = result;
  }

  public PolygonShape(Point[] p_corner_arr) {
    this(new Polygon(p_corner_arr));
  }

  @Override
  public Point corner(int p_no) {
    if (p_no < 0 || p_no >= corners.length) {
      FRLogger.warn("PolygonShape.corner: p_no out of range");
      return null;
    }
    return corners[p_no];
  }

  @Override
  public int border_line_count() {
    return corners.length;
  }

  @Override
  public boolean corner_is_bounded(int p_no) {
    return true;
  }

  @Override
  public boolean intersects(Shape p_shape) {
    return p_shape.intersects(this);
  }

  @Override
  public boolean intersects(Circle p_circle) {
    TileShape[] convexPieces = split_to_convex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].intersects(p_circle)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(Simplex p_simplex) {
    TileShape[] convexPieces = split_to_convex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].intersects(p_simplex)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(IntOctagon p_oct) {
    TileShape[] convexPieces = split_to_convex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].intersects(p_oct)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(IntBox p_box) {
    TileShape[] convexPieces = split_to_convex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].intersects(p_box)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Polyline[] cutout(Polyline p_polyline) {
    FRLogger.warn("PolygonShape.cutout not yet implemented");
    return null;
  }

  @Override
  public PolygonShape enlarge(double p_offset) {
    if (p_offset == 0) {
      return this;
    }
    FRLogger.warn("PolygonShape.enlarge not yet implemented");
    return null;
  }

  @Override
  public double border_distance(FloatPoint p_point) {
    FRLogger.warn("PolygonShape.border_distance not yet implemented");
    return 0;
  }

  @Override
  public double smallest_radius() {
    return border_distance(centre_of_gravity());
  }

  @Override
  public boolean contains(FloatPoint p_point) {
    TileShape[] convexPieces = split_to_convex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].contains(p_point)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contains_inside(Point p_point) {
    if (contains_on_border(p_point)) {
      return false;
    }
    return !is_outside(p_point);
  }

  @Override
  public boolean is_outside(Point p_point) {
    TileShape[] convexPieces = split_to_convex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (!convexPieces[i].is_outside(p_point)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean contains(Point p_point) {
    return !is_outside(p_point);
  }

  @Override
  public boolean contains_on_border(Point p_point) {
    // FRLogger.warn("PolygonShape.contains_on_edge not yet implemented");
    return false;
  }

  @Override
  public double distance(FloatPoint p_point) {
    FRLogger.warn("PolygonShape.distance not yet implemented");
    return 0;
  }

  @Override
  public PolygonShape translate_by(Vector p_vector) {
    if (p_vector.equals(Vector.ZERO)) {
      return this;
    }
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].translate_by(p_vector);
    }
    return new PolygonShape(newCorners);
  }

  @Override
  public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs) {
    return p_dirs.bounds(this);
  }

  @Override
  public IntBox bounding_box() {
    if (precalculatedBoundingBox == null) {
      double llx = Integer.MAX_VALUE;
      double lly = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;
      double ury = Integer.MIN_VALUE;
      for (int i = 0; i < corners.length; i++) {
        FloatPoint curr = corners[i].to_float();
        llx = Math.min(llx, curr.x);
        lly = Math.min(lly, curr.y);
        urx = Math.max(urx, curr.x);
        ury = Math.max(ury, curr.y);
      }
      IntPoint lowerLeft = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
      IntPoint upperRight = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
      precalculatedBoundingBox = new IntBox(lowerLeft, upperRight);
    }
    return precalculatedBoundingBox;
  }

  @Override
  public IntOctagon bounding_octagon() {
    if (precalculatedBoundingOctagon == null) {
      double lx = Integer.MAX_VALUE;
      double ly = Integer.MAX_VALUE;
      double rx = Integer.MIN_VALUE;
      double uy = Integer.MIN_VALUE;
      double ulx = Integer.MAX_VALUE;
      double lrx = Integer.MIN_VALUE;
      double llx = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;
      for (int i = 0; i < corners.length; i++) {
        FloatPoint curr = corners[i].to_float();
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
      precalculatedBoundingOctagon =
          new IntOctagon(
              (int) Math.floor(lx),
              (int) Math.floor(ly),
              (int) Math.ceil(rx),
              (int) Math.ceil(uy),
              (int) Math.floor(ulx),
              (int) Math.ceil(lrx),
              (int) Math.floor(llx),
              (int) Math.ceil(urx));
    }
    return precalculatedBoundingOctagon;
  }

  /**
   * Checks, if every line segment between 2 points of the shape is contained completely in the
   * shape.
   */
  public boolean is_convex() {
    if (corners.length <= 2) {
      return true;
    }
    Point prevPoint = corners[corners.length - 1];
    Point currPoint = corners[0];
    Point nextPoint = corners[1];

    for (int ind = 0; ind < corners.length; ind++) {
      if (nextPoint.side_of(prevPoint, currPoint) == Side.ON_THE_RIGHT) {
        return false;
      }
      prevPoint = currPoint;
      currPoint = nextPoint;
      if (ind == corners.length - 2) {
        nextPoint = corners[0];
      } else {
        nextPoint = corners[ind + 2];
      }
    }
    // check, if the sum of the interior angles is at most 2 * pi

    Line firstLine = new Line(corners[corners.length - 1], corners[0]);
    Line currLine = new Line(corners[0], corners[1]);
    IntDirection firstDirection = (IntDirection) firstLine.direction();
    IntDirection currDirection = (IntDirection) currLine.direction();
    double lastDet = firstDirection.determinant(currDirection);

    for (int ind2 = 2; ind2 < corners.length; ind2++) {
      currLine = new Line(currLine.b, corners[ind2]);
      currDirection = (IntDirection) currLine.direction();
      double currDet = firstDirection.determinant(currDirection);
      if (lastDet <= 0 && currDet > 0) {
        return false;
      }
      lastDet = currDet;
    }

    return true;
  }

  public PolygonShape convex_hull() {
    if (corners.length <= 2) {
      return this;
    }
    Point prevPoint = corners[corners.length - 1];
    Point currPoint = corners[0];
    Point nextPoint;
    for (int ind = 0; ind < corners.length; ind++) {
      if (ind == corners.length - 1) {
        nextPoint = corners[0];
      } else {
        nextPoint = corners[ind + 1];
      }
      if (nextPoint.side_of(prevPoint, currPoint) != Side.ON_THE_LEFT) {
        // skip currPoint;
        Point[] newCorners = new Point[corners.length - 1];
        System.arraycopy(corners, 0, newCorners, 0, ind);
        if (ind < newCorners.length)
        // copy remaining elements if present
        {
          System.arraycopy(corners, ind + 1, newCorners, ind, newCorners.length - ind);
        }
        PolygonShape result = new PolygonShape(newCorners);
        return result.convex_hull();
      }
      prevPoint = currPoint;
      currPoint = nextPoint;
    }
    return this;
  }

  @Override
  public TileShape bounding_tile() {
    PolygonShape hull = convex_hull();
    Line[] boundingLines = new Line[hull.corners.length];
    for (int i = 0; i < boundingLines.length - 1; i++) {
      boundingLines[i] = new Line(hull.corners[i], hull.corners[i + 1]);
    }
    boundingLines[boundingLines.length - 1] =
        new Line(hull.corners[hull.corners.length - 1], hull.corners[0]);
    return TileShape.get_instance(boundingLines);
  }

  @Override
  public double area() {

    if (dimension() <= 2) {
      return 0;
    }
    // calculate half of the absolute value of
    // x0 (y1 - yn-1) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ xn-1( y0 - yn-2)
    // where xi, yi are the coordinates of the i-th corner of this polygon.

    double result = 0;
    FloatPoint prevCorner = corners[corners.length - 2].to_float();
    FloatPoint currCorner = corners[corners.length - 1].to_float();
    for (int i = 0; i < corners.length; i++) {
      FloatPoint nextCorner = corners[i].to_float();
      result += currCorner.x * (nextCorner.y - prevCorner.y);
      prevCorner = currCorner;
      currCorner = nextCorner;
    }
    return 0.5 * Math.abs(result);
  }

  @Override
  public int dimension() {
    if (corners.length == 0) {
      return -1;
    }
    if (corners.length == 1) {
      return 0;
    }
    if (corners.length == 2) {
      return 1;
    }
    return 2;
  }

  @Override
  public boolean is_bounded() {
    return true;
  }

  @Override
  public boolean is_empty() {
    return corners.length == 0;
  }

  @Override
  public Line border_line(int p_no) {
    if (p_no < 0 || p_no >= corners.length) {
      FRLogger.warn("PolygonShape.edge_line: p_no out of range");
      return null;
    }
    Point nextCorner;
    if (p_no == corners.length - 1) {
      nextCorner = corners[0];
    } else {
      nextCorner = corners[p_no + 1];
    }
    return new Line(corners[p_no], nextCorner);
  }

  @Override
  public FloatPoint nearest_point_approx(FloatPoint p_from_point) {
    double minDist = Double.MAX_VALUE;
    FloatPoint result = null;
    TileShape[] convexShapes = split_to_convex();
    for (int i = 0; i < convexShapes.length; i++) {
      FloatPoint currNearestPoint = convexShapes[i].nearest_point_approx(p_from_point);
      double currDist = currNearestPoint.distance_square(p_from_point);
      if (currDist < minDist) {
        minDist = currDist;
        result = currNearestPoint;
      }
    }
    return result;
  }

  @Override
  public PolygonShape turn_90_degree(int p_factor, IntPoint p_pole) {
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].turn_90_degree(p_factor, p_pole);
    }
    return new PolygonShape(newCorners);
  }

  @Override
  public PolygonShape rotate_approx(double p_angle, FloatPoint p_pole) {
    if (p_angle == 0) {
      return this;
    }
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].to_float().rotate(p_angle, p_pole).round();
    }
    return new PolygonShape(newCorners);
  }

  @Override
  public PolygonShape mirror_vertical(IntPoint p_pole) {
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].mirror_vertical(p_pole);
    }
    return new PolygonShape(newCorners);
  }

  @Override
  public PolygonShape mirror_horizontal(IntPoint p_pole) {
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].mirror_horizontal(p_pole);
    }
    return new PolygonShape(newCorners);
  }

  /**
   * Splits this polygon shape into convex pieces. The result is not exact, because rounded
   * intersections of lines are used in the result pieces. It can be made exact, if Polylines are
   * returned instead of Polygons, so that no intersection points are needed in the result.
   */
  @Override
  public TileShape[] split_to_convex() {
    if (this.precalculatedConvexPieces == null)
    // not yet precalculated
    {
      // use a fixed seed to get reproducible result
      randomGenerator.setSeed(seed);
      Collection<PolygonShape> convexPieces = split_to_convex_recu();
      if (convexPieces == null) {
        // split failed, maybe the polygon has selfontersections
        return null;
      }
      precalculatedConvexPieces = new TileShape[convexPieces.size()];
      Iterator<PolygonShape> it = convexPieces.iterator();
      for (int i = 0; i < precalculatedConvexPieces.length; i++) {
        PolygonShape currPiece = it.next();
        precalculatedConvexPieces[i] = TileShape.get_instance(currPiece.corners);
      }
    }
    return this.precalculatedConvexPieces;
  }

  /** Private recursive part of split_to_convex. Returns a collection of polygon shape pieces. */
  private Collection<PolygonShape> split_to_convex_recu() {
    // start with a hashed corner and search the first concave corner
    int startCornerNo = randomGenerator.nextInt(corners.length);
    Point currCorner = corners[startCornerNo];
    Point prevCorner;
    if (startCornerNo != 0) {
      prevCorner = corners[startCornerNo - 1];
    } else {
      prevCorner = corners[corners.length - 1];
    }

    Point nextCorner;

    // search for the next concave corner from here
    int concaveCornerNo = -1;
    for (int i = 0; i < corners.length; i++) {
      if (startCornerNo < corners.length - 1) {
        nextCorner = corners[startCornerNo + 1];
      } else {
        nextCorner = corners[0];
      }
      if (nextCorner.side_of(prevCorner, currCorner) == Side.ON_THE_RIGHT) {
        // concave corner found
        concaveCornerNo = startCornerNo;
        break;
      }
      prevCorner = currCorner;
      currCorner = nextCorner;
      startCornerNo = (startCornerNo + 1) % corners.length;
    }
    Collection<PolygonShape> result = new LinkedList<>();
    if (concaveCornerNo < 0) {
      // no concave corner found, this shape is already convex
      result.add(this);
      return result;
    }
    DivisionPoint d = new DivisionPoint(concaveCornerNo);
    if (d.projection == null) {
      // projection not found, maybe polygon has selfintersections
      return null;
    }

    // construct the result pieces from p_polygon and the division point
    int cornerCount = d.cornerNoAfterProjection - concaveCornerNo;

    if (cornerCount < 0) {
      cornerCount += corners.length;
    }
    ++cornerCount;
    Point[] firstArr = new Point[cornerCount];
    int cornerInd = concaveCornerNo;

    for (int i = 0; i < cornerCount - 1; i++) {
      firstArr[i] = corners[cornerInd];
      cornerInd = (cornerInd + 1) % corners.length;
    }
    firstArr[cornerCount - 1] = d.projection.round();
    PolygonShape firstPiece = new PolygonShape(firstArr);

    cornerCount = concaveCornerNo - d.cornerNoAfterProjection;
    if (cornerCount < 0) {
      cornerCount += corners.length;
    }
    cornerCount += 2;
    Point[] lastArr = new Point[cornerCount];
    lastArr[0] = d.projection.round();
    cornerInd = d.cornerNoAfterProjection;
    for (int i = 1; i < cornerCount; i++) {
      lastArr[i] = corners[cornerInd];
      cornerInd = (cornerInd + 1) % corners.length;
    }
    PolygonShape lastPiece = new PolygonShape(lastArr);
    Collection<PolygonShape> c1 = firstPiece.split_to_convex_recu();
    if (c1 == null) {
      return null;
    }
    Collection<PolygonShape> c2 = lastPiece.split_to_convex_recu();
    if (c2 == null) {
      return null;
    }
    result.addAll(c1);
    result.addAll(c2);
    return result;
  }

  private class DivisionPoint {

    final int cornerNoAfterProjection;
    final FloatPoint projection;

    /**
     * At a concave corner of the closed polygon, a minimal axis parallel division line is
     * constructed, to divide the closed polygon into two.
     */
    DivisionPoint(int p_concave_corner_no) {
      FloatPoint concaveCorner = corners[p_concave_corner_no].to_float();
      FloatPoint beforeConcaveCorner;

      if (p_concave_corner_no != 0) {
        beforeConcaveCorner = corners[p_concave_corner_no - 1].to_float();
      } else {
        beforeConcaveCorner = corners[corners.length - 1].to_float();
      }

      FloatPoint afterConcaveCorner;

      if (p_concave_corner_no == corners.length - 1) {
        afterConcaveCorner = corners[0].to_float();
      } else {
        afterConcaveCorner = corners[p_concave_corner_no + 1].to_float();
      }

      boolean searchRight =
          beforeConcaveCorner.y > concaveCorner.y || concaveCorner.y > afterConcaveCorner.y;

      boolean searchLeft =
          beforeConcaveCorner.y < concaveCorner.y || concaveCorner.y < afterConcaveCorner.y;

      boolean searchUp =
          beforeConcaveCorner.x < concaveCorner.x || concaveCorner.x < afterConcaveCorner.x;

      boolean searchDown =
          beforeConcaveCorner.x > concaveCorner.x || concaveCorner.x > afterConcaveCorner.x;

      double minProjectionDist = Integer.MAX_VALUE;
      FloatPoint minProjection = null;
      int cornerNoAfterMinProjection = 0;

      int cornerNoAfterCurrProjection = (p_concave_corner_no + 2) % corners.length;

      Point cornerBeforeCurrProjection;
      if (cornerNoAfterCurrProjection != 0) {
        cornerBeforeCurrProjection = corners[cornerNoAfterCurrProjection - 1];
      } else {
        cornerBeforeCurrProjection = corners[corners.length - 1];
      }
      FloatPoint cornerBeforeProjectionApprox = cornerBeforeCurrProjection.to_float();

      double currDist;
      int loopEnd = corners.length - 2;

      for (int i = 0; i < loopEnd; i++) {
        Point cornerAfterCurrProjection = corners[cornerNoAfterCurrProjection];
        FloatPoint cornerAfterProjectionApprox = cornerAfterCurrProjection.to_float();
        if (cornerBeforeProjectionApprox.y != cornerAfterProjectionApprox.y)
        // try a horizontal division
        {
          double minY;
          double maxY;

          if (cornerAfterProjectionApprox.y > cornerBeforeProjectionApprox.y) {
            minY = cornerBeforeProjectionApprox.y;
            maxY = cornerAfterProjectionApprox.y;
          } else {
            minY = cornerAfterProjectionApprox.y;
            maxY = cornerBeforeProjectionApprox.y;
          }

          if (concaveCorner.y >= minY && concaveCorner.y <= maxY) {
            Line currLine = new Line(cornerBeforeCurrProjection, cornerAfterCurrProjection);
            double xIntersect = currLine.function_in_y_value_approx(concaveCorner.y);
            currDist = Math.abs(xIntersect - concaveCorner.x);
            // Make sure, that the new shape will not be concave at the projection point.
            // That might happen, if the boundary curve runs back in itself.
            boolean projectionOk =
                currDist < minProjectionDist
                    && (searchRight
                            && xIntersect > concaveCorner.x
                            && concaveCorner.y <= cornerAfterProjectionApprox.y
                        || searchLeft
                            && xIntersect < concaveCorner.x
                            && concaveCorner.y >= cornerAfterProjectionApprox.y);
            if (projectionOk) {
              minProjectionDist = currDist;
              cornerNoAfterMinProjection = cornerNoAfterCurrProjection;
              minProjection = new FloatPoint(xIntersect, concaveCorner.y);
            }
          }
        }

        if (cornerBeforeProjectionApprox.x != cornerAfterProjectionApprox.x)
        // try a vertical division
        {
          double minX;
          double maxX;
          if (cornerAfterProjectionApprox.x > cornerBeforeProjectionApprox.x) {
            minX = cornerBeforeProjectionApprox.x;
            maxX = cornerAfterProjectionApprox.x;
          } else {
            minX = cornerAfterProjectionApprox.x;
            maxX = cornerBeforeProjectionApprox.x;
          }
          if (concaveCorner.x >= minX && concaveCorner.x <= maxX) {
            Line currLine = new Line(cornerBeforeCurrProjection, cornerAfterCurrProjection);
            double yIntersect = currLine.function_value_approx(concaveCorner.x);
            currDist = Math.abs(yIntersect - concaveCorner.y);
            // make sure, that the new shape will be convex at the projection point
            boolean projectionOk =
                currDist < minProjectionDist
                    && (searchUp
                            && yIntersect > concaveCorner.y
                            && concaveCorner.x >= cornerAfterProjectionApprox.x
                        || searchDown
                            && yIntersect < concaveCorner.y
                            && concaveCorner.x <= cornerAfterProjectionApprox.x);

            if (projectionOk) {
              minProjectionDist = currDist;
              cornerNoAfterMinProjection = cornerNoAfterCurrProjection;
              minProjection = new FloatPoint(concaveCorner.x, yIntersect);
            }
          }
        }
        cornerBeforeCurrProjection = cornerAfterCurrProjection;
        cornerBeforeProjectionApprox = cornerAfterProjectionApprox;
        if (cornerNoAfterCurrProjection == corners.length - 1) {
          cornerNoAfterCurrProjection = 0;
        } else {
          ++cornerNoAfterCurrProjection;
        }
      }
      if (minProjectionDist == Integer.MAX_VALUE) {
        FRLogger.warn("PolygonShape.DivisionPoint: projection not found");
      }

      projection = minProjection;
      cornerNoAfterProjection = cornerNoAfterMinProjection;
    }
  }
}

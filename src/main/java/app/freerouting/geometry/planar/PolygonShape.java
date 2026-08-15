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

  /** Stores precalculated data for this polygon shape. */
  private transient IntBox precalculatedBoundingBox;

  private transient IntOctagon precalculatedBoundingOctagon;
  private transient TileShape[] precalculatedConvexPieces;

  /** Creates a new instance of PolygonShape. */
  public PolygonShape(Polygon polygon) {
    Polygon currentPolygon = polygon;
    if (polygon.windingNumberAfterClosing() < 0) {
      // the corners of the polygon are in clockwise sense
      currentPolygon = polygon.revertCorners();
    }
    Point[] currentCorners = currentPolygon.cornerArray();
    int lastCornerNo = currentCorners.length - 1;

    if (lastCornerNo > 0) {
      if (currentCorners[0].equals(currentCorners[lastCornerNo])) {
        // skip last point
        --lastCornerNo;
      }
    }

    boolean lastPointCollinear = false;

    if (lastCornerNo >= 2) {
      lastPointCollinear =
          currentCorners[lastCornerNo].sideOf(currentCorners[lastCornerNo - 1], currentCorners[0])
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
          currentCorners[0].sideOf(currentCorners[1], currentCorners[lastCornerNo])
              == Side.COLLINEAR;
    }

    if (firstPointCollinear) {
      // skip first point
      ++firstCornerNo;
    }
    // search the point with the lowest y and then with the lowest x
    int startCornerNo = firstCornerNo;
    FloatPoint startCorner = currentCorners[startCornerNo].toFloat();
    for (int i = startCornerNo + 1; i <= lastCornerNo; i++) {
      FloatPoint currentCorner = currentCorners[i].toFloat();
      if (currentCorner.y < startCorner.y
          || currentCorner.y == startCorner.y && currentCorner.x < startCorner.x) {
        startCornerNo = i;
        startCorner = currentCorner;
      }
    }
    int newCornerCount = lastCornerNo - firstCornerNo + 1;
    Point[] result = new Point[newCornerCount];
    int currentCornerNo = 0;
    for (int i = startCornerNo; i <= lastCornerNo; i++) {
      result[currentCornerNo] = currentCorners[i];
      ++currentCornerNo;
    }
    for (int i = firstCornerNo; i < startCornerNo; i++) {
      result[currentCornerNo] = currentCorners[i];
      ++currentCornerNo;
    }
    corners = result;
  }

  /** Creates a polygon shape from an array of corner points. */
  public PolygonShape(Point[] cornerArr) {
    this(new Polygon(cornerArr));
  }

  @Override
  public Point corner(int no) {
    if (no < 0 || no >= corners.length) {
      FRLogger.warn("PolygonShape.corner: p_no out of range");
      return null;
    }
    return corners[no];
  }

  @Override
  public int borderLineCount() {
    return corners.length;
  }

  @Override
  public boolean cornerIsBounded(int no) {
    return true;
  }

  @Override
  public boolean intersects(Shape shape) {
    return shape.intersects(this);
  }

  @Override
  public boolean intersects(Circle circle) {
    TileShape[] convexPieces = splitToConvex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].intersects(circle)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(Simplex simplex) {
    TileShape[] convexPieces = splitToConvex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].intersects(simplex)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(IntOctagon oct) {
    TileShape[] convexPieces = splitToConvex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].intersects(oct)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean intersects(IntBox box) {
    TileShape[] convexPieces = splitToConvex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].intersects(box)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Polyline[] cutout(Polyline polyline) {
    FRLogger.warn("PolygonShape.cutout not yet implemented");
    return null;
  }

  @Override
  public PolygonShape enlarge(double offset) {
    if (offset == 0) {
      return this;
    }
    FRLogger.warn("PolygonShape.enlarge not yet implemented");
    return null;
  }

  @Override
  public double borderDistance(FloatPoint point) {
    FRLogger.warn("PolygonShape.border_distance not yet implemented");
    return 0;
  }

  @Override
  public double smallestRadius() {
    return borderDistance(centreOfGravity());
  }

  @Override
  public boolean contains(FloatPoint point) {
    TileShape[] convexPieces = splitToConvex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (convexPieces[i].contains(point)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contains(Point point) {
    return !isOutside(point);
  }

  @Override
  public boolean containsInside(Point point) {
    if (containsOnBorder(point)) {
      return false;
    }
    return !isOutside(point);
  }

  @Override
  public boolean isOutside(Point point) {
    TileShape[] convexPieces = splitToConvex();
    for (int i = 0; i < convexPieces.length; i++) {
      if (!convexPieces[i].isOutside(point)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean containsOnBorder(Point point) {
    // FRLogger.warn("PolygonShape.contains_on_edge not yet implemented");
    return false;
  }

  @Override
  public double distance(FloatPoint point) {
    FRLogger.warn("PolygonShape.distance not yet implemented");
    return 0;
  }

  @Override
  public PolygonShape translateBy(Vector vector) {
    if (vector.equals(Vector.ZERO)) {
      return this;
    }
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].translateBy(vector);
    }
    return new PolygonShape(newCorners);
  }

  @Override
  public RegularTileShape boundingShape(ShapeBoundingDirections dirs) {
    return dirs.bounds(this);
  }

  @Override
  public IntBox boundingBox() {
    if (precalculatedBoundingBox == null) {
      double llx = Integer.MAX_VALUE;
      double lly = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;
      double ury = Integer.MIN_VALUE;
      for (int i = 0; i < corners.length; i++) {
        FloatPoint current = corners[i].toFloat();
        llx = Math.min(llx, current.x);
        lly = Math.min(lly, current.y);
        urx = Math.max(urx, current.x);
        ury = Math.max(ury, current.y);
      }
      IntPoint lowerLeft = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
      IntPoint upperRight = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
      precalculatedBoundingBox = new IntBox(lowerLeft, upperRight);
    }
    return precalculatedBoundingBox;
  }

  @Override
  public IntOctagon boundingOctagon() {
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
        FloatPoint current = corners[i].toFloat();
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
  public boolean isConvex() {
    if (corners.length <= 2) {
      return true;
    }
    Point prevPoint = corners[corners.length - 1];
    Point currentPoint = corners[0];
    Point nextPoint = corners[1];

    for (int ind = 0; ind < corners.length; ind++) {
      if (nextPoint.sideOf(prevPoint, currentPoint) == Side.ON_THE_RIGHT) {
        return false;
      }
      prevPoint = currentPoint;
      currentPoint = nextPoint;
      if (ind == corners.length - 2) {
        nextPoint = corners[0];
      } else if (ind == corners.length - 1) {
        nextPoint = corners[1];
      } else {
        nextPoint = corners[ind + 2];
      }
    }
    // check, if the sum of the interior angles is at most 2 * pi

    Line firstLine = new Line(corners[corners.length - 1], corners[0]);
    Line currentLine = new Line(corners[0], corners[1]);
    IntDirection firstDirection = (IntDirection) firstLine.direction();
    IntDirection currentDirection = (IntDirection) currentLine.direction();
    double lastDet = firstDirection.determinant(currentDirection);

    for (int ind2 = 2; ind2 < corners.length; ind2++) {
      currentLine = new Line(currentLine.b, corners[ind2]);
      currentDirection = (IntDirection) currentLine.direction();
      double currentDet = firstDirection.determinant(currentDirection);
      if (lastDet <= 0 && currentDet > 0) {
        return false;
      }
      lastDet = currentDet;
    }

    return true;
  }

  /** Returns the convex hull of this polygon shape. */
  public PolygonShape convexHull() {
    if (corners.length <= 2) {
      return this;
    }
    Point prevPoint = corners[corners.length - 1];
    Point currentPoint = corners[0];
    Point nextPoint;
    for (int ind = 0; ind < corners.length; ind++) {
      if (ind == corners.length - 1) {
        nextPoint = corners[0];
      } else {
        nextPoint = corners[ind + 1];
      }
      if (nextPoint.sideOf(prevPoint, currentPoint) != Side.ON_THE_LEFT) {
        // skip currentPoint;
        Point[] newCorners = new Point[corners.length - 1];
        System.arraycopy(corners, 0, newCorners, 0, ind);
        if (ind < newCorners.length) {
          // copy remaining elements if present
          System.arraycopy(corners, ind + 1, newCorners, ind, newCorners.length - ind);
        }
        PolygonShape result = new PolygonShape(newCorners);
        return result.convexHull();
      }
      prevPoint = currentPoint;
      currentPoint = nextPoint;
    }
    return this;
  }

  @Override
  public TileShape boundingTile() {
    PolygonShape hull = convexHull();
    Line[] boundingLines = new Line[hull.corners.length];
    for (int i = 0; i < boundingLines.length - 1; i++) {
      boundingLines[i] = new Line(hull.corners[i], hull.corners[i + 1]);
    }
    boundingLines[boundingLines.length - 1] =
        new Line(hull.corners[hull.corners.length - 1], hull.corners[0]);
    return TileShape.getInstance(boundingLines);
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
    FloatPoint prevCorner = corners[corners.length - 2].toFloat();
    FloatPoint currentCorner = corners[corners.length - 1].toFloat();
    for (int i = 0; i < corners.length; i++) {
      FloatPoint nextCorner = corners[i].toFloat();
      result += currentCorner.x * (nextCorner.y - prevCorner.y);
      prevCorner = currentCorner;
      currentCorner = nextCorner;
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
  public boolean isBounded() {
    return true;
  }

  @Override
  public boolean isEmpty() {
    return corners.length == 0;
  }

  @Override
  public Line borderLine(int no) {
    if (no < 0 || no >= corners.length) {
      FRLogger.warn("PolygonShape.edge_line: p_no out of range");
      return null;
    }
    Point nextCorner;
    if (no == corners.length - 1) {
      nextCorner = corners[0];
    } else {
      nextCorner = corners[no + 1];
    }
    return new Line(corners[no], nextCorner);
  }

  @Override
  public FloatPoint nearestPointApprox(FloatPoint fromPoint) {
    double minDist = Double.MAX_VALUE;
    FloatPoint result = null;
    TileShape[] convexShapes = splitToConvex();
    for (int i = 0; i < convexShapes.length; i++) {
      FloatPoint currentNearestPoint = convexShapes[i].nearestPointApprox(fromPoint);
      double currentDistance = currentNearestPoint.distanceSquare(fromPoint);
      if (currentDistance < minDist) {
        minDist = currentDistance;
        result = currentNearestPoint;
      }
    }
    return result;
  }

  @Override
  public PolygonShape turn90Degree(int factor, IntPoint pole) {
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].turn90Degree(factor, pole);
    }
    return new PolygonShape(newCorners);
  }

  @Override
  public PolygonShape rotateApprox(double angle, FloatPoint pole) {
    if (angle == 0) {
      return this;
    }
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].toFloat().rotate(angle, pole).round();
    }
    return new PolygonShape(newCorners);
  }

  @Override
  public PolygonShape mirrorVertical(IntPoint pole) {
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].mirrorVertical(pole);
    }
    return new PolygonShape(newCorners);
  }

  @Override
  public PolygonShape mirrorHorizontal(IntPoint pole) {
    Point[] newCorners = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      newCorners[i] = corners[i].mirrorHorizontal(pole);
    }
    return new PolygonShape(newCorners);
  }

  /**
   * Splits this polygon shape into convex pieces. The result is not exact, because rounded
   * intersections of lines are used in the result pieces. It can be made exact, if Polylines are
   * returned instead of Polygons, so that no intersection points are needed in the result.
   */
  @Override
  public TileShape[] splitToConvex() {
    if (this.precalculatedConvexPieces == null) {
      // not yet precalculated
      // use a fixed seed to get reproducible result
      randomGenerator.setSeed(seed);
      Collection<PolygonShape> convexPieces = splitToConvexRecu();
      if (convexPieces == null) {
        // split failed, maybe the polygon has selfontersections
        return null;
      }
      precalculatedConvexPieces = new TileShape[convexPieces.size()];
      Iterator<PolygonShape> it = convexPieces.iterator();
      for (int i = 0; i < precalculatedConvexPieces.length; i++) {
        PolygonShape currentPiece = it.next();
        precalculatedConvexPieces[i] = TileShape.getInstance(currentPiece.corners);
      }
    }
    return this.precalculatedConvexPieces;
  }

  /** Private recursive part of split_to_convex. Returns a collection of polygon shape pieces. */
  private Collection<PolygonShape> splitToConvexRecu() {
    // start with a hashed corner and search the first concave corner
    int startCornerNo = randomGenerator.nextInt(corners.length);
    Point currentCorner = corners[startCornerNo];
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
      if (nextCorner.sideOf(prevCorner, currentCorner) == Side.ON_THE_RIGHT) {
        // concave corner found
        concaveCornerNo = startCornerNo;
        break;
      }
      prevCorner = currentCorner;
      currentCorner = nextCorner;
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
    PolygonShape firstPiece = new PolygonShape(firstArr);
    Collection<PolygonShape> c1 = firstPiece.splitToConvexRecu();
    if (c1 == null) {
      return null;
    }
    Collection<PolygonShape> c2 = lastPiece.splitToConvexRecu();
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
    DivisionPoint(int concaveCornerNo) {
      FloatPoint concaveCorner = corners[concaveCornerNo].toFloat();
      FloatPoint beforeConcaveCorner;

      if (concaveCornerNo != 0) {
        beforeConcaveCorner = corners[concaveCornerNo - 1].toFloat();
      } else {
        beforeConcaveCorner = corners[corners.length - 1].toFloat();
      }

      FloatPoint afterConcaveCorner;

      if (concaveCornerNo == corners.length - 1) {
        afterConcaveCorner = corners[0].toFloat();
      } else {
        afterConcaveCorner = corners[concaveCornerNo + 1].toFloat();
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

      int cornerNoAfterCurrProjection = (concaveCornerNo + 2) % corners.length;

      Point cornerBeforeCurrProjection;
      if (cornerNoAfterCurrProjection != 0) {
        cornerBeforeCurrProjection = corners[cornerNoAfterCurrProjection - 1];
      } else {
        cornerBeforeCurrProjection = corners[corners.length - 1];
      }
      FloatPoint cornerBeforeProjectionApprox = cornerBeforeCurrProjection.toFloat();

      double currentDistance;
      int loopEnd = corners.length - 2;

      for (int i = 0; i < loopEnd; i++) {
        Point cornerAfterCurrProjection = corners[cornerNoAfterCurrProjection];
        FloatPoint cornerAfterProjectionApprox = cornerAfterCurrProjection.toFloat();
        if (cornerBeforeProjectionApprox.y != cornerAfterProjectionApprox.y) {
          // try a horizontal division
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
            Line currentLine = new Line(cornerBeforeCurrProjection, cornerAfterCurrProjection);
            double xintersection = currentLine.functionInYValueApprox(concaveCorner.y);
            currentDistance = Math.abs(xintersection - concaveCorner.x);
            // Make sure, that the new shape will not be concave at the projection point.
            // That might happen, if the boundary curve runs back in itself.
            boolean projectionOk =
                currentDistance < minProjectionDist
                    && (searchRight
                            && xintersection > concaveCorner.x
                            && concaveCorner.y <= cornerAfterProjectionApprox.y
                        || searchLeft
                            && xintersection < concaveCorner.x
                            && concaveCorner.y >= cornerAfterProjectionApprox.y);
            if (projectionOk) {
              minProjectionDist = currentDistance;
              cornerNoAfterMinProjection = cornerNoAfterCurrProjection;
              minProjection = new FloatPoint(xintersection, concaveCorner.y);
            }
          }
        }

        if (cornerBeforeProjectionApprox.x != cornerAfterProjectionApprox.x) {
          // try a vertical division
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
            Line currentLine = new Line(cornerBeforeCurrProjection, cornerAfterCurrProjection);
            double yintersection = currentLine.functionValueApprox(concaveCorner.x);
            currentDistance = Math.abs(yintersection - concaveCorner.y);
            // make sure, that the new shape will be convex at the projection point
            boolean projectionOk =
                currentDistance < minProjectionDist
                    && (searchUp
                            && yintersection > concaveCorner.y
                            && concaveCorner.x >= cornerAfterProjectionApprox.x
                        || searchDown
                            && yintersection < concaveCorner.y
                            && concaveCorner.x <= cornerAfterProjectionApprox.x);

            if (projectionOk) {
              minProjectionDist = currentDistance;
              cornerNoAfterMinProjection = cornerNoAfterCurrProjection;
              minProjection = new FloatPoint(concaveCorner.x, yintersection);
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

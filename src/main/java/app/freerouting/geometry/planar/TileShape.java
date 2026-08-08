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
  public static TileShape getInstance(Line[] pLineArr) {
    Simplex result = Simplex.getInstance(pLineArr);
    return result.simplify();
  }

  /**
   * Creates a TileShape from a Point array, who forms the corners of the shape of a convex polygon.
   * May work only for IntPoints.
   */
  public static TileShape getInstance(Point[] pConvexPolygon) {
    Line[] lineArr = new Line[pConvexPolygon.length];
    for (int j = 0; j < lineArr.length - 1; j++) {
      lineArr[j] = new Line(pConvexPolygon[j], pConvexPolygon[j + 1]);
    }
    lineArr[lineArr.length - 1] = new Line(pConvexPolygon[lineArr.length - 1], pConvexPolygon[0]);
    return getInstance(lineArr);
  }

  /** creates a half_plane from a directed line */
  public static TileShape getInstance(Line pLine) {
    Line[] lines = new Line[1];
    lines[0] = pLine;
    return Simplex.getInstance(lines);
  }

  /**
   * Creates a normalized IntOctagon from the input values. For the meaning of the parameter
   * shortcuts see class IntOctagon.
   */
  public static IntOctagon getInstance(
      int pLx, int pLy, int pRx, int pUy, int pUlx, int pLrx, int pLlx, int pUrx) {
    IntOctagon oct = new IntOctagon(pLx, pLy, pRx, pUy, pUlx, pLrx, pLlx, pUrx);
    return oct.normalize();
  }

  /** creates a boxlike convex shape */
  public static IntOctagon getInstance(
      int pLowerLeftX, int pLowerLeftY, int pUpperRightX, int pUpperRightY) {
    IntBox box = new IntBox(pLowerLeftX, pLowerLeftY, pUpperRightX, pUpperRightY);
    return box.toIntOctagon();
  }

  /** creates the smallest IntOctagon containing p_point */
  public static IntBox getInstance(Point pPoint) {
    return pPoint.surroundingBox();
  }

  /**
   * Tries to simplify the result shape to a simpler shape. Simplifying always in the intersection
   * function may cause performance problems.
   */
  public TileShape intersectionWithSimplify(TileShape pOther) {
    TileShape result = this.intersection(pOther);
    return result.simplify();
  }

  /** Converts the physical instance of this shape to a simpler physical instance, if possible. */
  public abstract TileShape simplify();

  /** Returns a unique ID for this shape for deterministic tie-breaking. */
  public abstract int getIdNo();

  /** checks if this TileShape is an IntBox or can be converted into an IntBox */
  public abstract boolean isIntBox();

  /** checks if this TileShape is an IntOctagon or can be converted into an IntOctagon */
  public abstract boolean isIntOctagon();

  /** Returns the intersection of this shape with p_other */
  public abstract TileShape intersection(TileShape pOther);

  /**
   * Returns the p_no-th edge line of this shape for p_no between 0 and edge_line_count() - 1. The
   * edge lines are sorted in counterclock sense around the shape starting with the edge with the
   * smallest direction.
   */
  @Override
  public abstract Line borderLine(int pNo);

  /** if p_line is a borderline of this shape the number of that edge is returned, otherwise -1 */
  public abstract int borderLineIndex(Line pLine);

  /** Converts the internal representation of this TieShape to a Simplex */
  public abstract Simplex toSimplex();

  /**
   * Returns the content of the area of the shape. If the shape is unbounded, Double.MAX_VALUE is
   * returned.
   */
  @Override
  public double area() {
    if (!isBounded()) {
      return Double.MAX_VALUE;
    }

    if (dimension() < 2) {
      return 0;
    }
    // calculate half of the absolute value of
    // x0 (y1 - yn-1) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ xn-1( y0 - yn-2)
    // where xi, yi are the coordinates of the i-th corner of this TileShape.

    double result = 0;
    int cornerCount = borderLineCount();
    FloatPoint prevCorner = cornerApprox(cornerCount - 2);
    FloatPoint currCorner = cornerApprox(cornerCount - 1);
    for (int i = 0; i < cornerCount; i++) {
      FloatPoint nextCorner = cornerApprox(i);
      result += currCorner.x * (nextCorner.y - prevCorner.y);
      prevCorner = currCorner;
      currCorner = nextCorner;
    }
    return 0.5 * Math.abs(result);
  }

  /** Returns true, if p_point is not contained in the inside or the edge of the shape */
  @Override
  public boolean isOutside(Point pPoint) {
    int lineCount = borderLineCount();
    if (lineCount == 0) {
      return true;
    }
    for (int i = 0; i < lineCount; i++) {
      if (borderLine(i).sideOf(pPoint) == Side.ON_THE_LEFT) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean contains(Point pPoint) {
    return !isOutside(pPoint);
  }

  /** Returns true, if p_point is contained in this shape, but not on an edge line */
  @Override
  public boolean containsInside(Point pPoint) {
    int lineCount = borderLineCount();
    if (lineCount == 0) {
      return false;
    }
    for (int i = 0; i < lineCount; i++) {
      if (borderLine(i).sideOf(pPoint) != Side.ON_THE_RIGHT) {
        return false;
      }
    }
    return true;
  }

  /** Returns true, if p_point is contained in this shape. */
  @Override
  public boolean contains(FloatPoint pPoint) {
    return contains(pPoint, 0);
  }

  /**
   * Returns true, if p_point is contained in this shape with tolerance p_tolerance. p_tolerance is
   * used when determining if a point is on the left side of a border line. It is used there in
   * calculating a determinant and is not the distance of p_point to the border.
   */
  public boolean contains(FloatPoint pPoint, double pTolerance) {
    int lineCount = borderLineCount();
    if (lineCount == 0) {
      return false;
    }
    for (int i = 0; i < lineCount; i++) {
      if (borderLine(i).sideOf(pPoint, pTolerance) != Side.ON_THE_RIGHT) {
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
  public Side sideOfBorder(FloatPoint pPoint, double pTolerance) {
    int lineCount = borderLineCount();
    if (lineCount == 0) {
      return Side.COLLINEAR;
    }
    Side result = Side.ON_THE_RIGHT; // point is inside
    for (int i = 0; i < lineCount; i++) {
      Side currSide = borderLine(i).sideOf(pPoint, pTolerance);
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
  public int containsOnBorderLineNo(Point pPoint) {
    int lineCount = borderLineCount();
    if (lineCount == 0) {
      return -1;
    }
    int containingLineNo = -1;
    for (int i = 0; i < lineCount; i++) {
      Side sideOf = borderLine(i).sideOf(pPoint);
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
  public boolean containsOnBorder(Point pPoint) {
    return containsOnBorderLineNo(pPoint) >= 0;
  }

  /**
   * Returns true, if this shape contains p_other completely. THere may be some numerical
   * inaccuracy.
   */
  public boolean containsApprox(TileShape pOther) {
    FloatPoint[] corners = pOther.cornerApproxArr();
    for (FloatPoint currCorner : corners) {
      if (!this.contains(currCorner)) {
        return false;
      }
    }
    return true;
  }

  /** Returns true, if this shape contains p_other completely. */
  public boolean contains(TileShape pOther) {
    for (int i = 0; i < pOther.borderLineCount(); i++) {
      if (!this.contains(pOther.corner(i))) {
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
  public double distance(FloatPoint pPoint) {
    FloatPoint nearestPoint = nearestPointApprox(pPoint);
    return nearestPoint.distance(pPoint);
  }

  /** Returns the distance between p_point and its nearest point on the edge of the shape. */
  @Override
  public double borderDistance(FloatPoint pPoint) {
    FloatPoint nearestPoint = nearestBorderPointApprox(pPoint);
    return nearestPoint.distance(pPoint);
  }

  @Override
  public double smallestRadius() {
    return borderDistance(centreOfGravity());
  }

  /**
   * Returns the point in this shape, which has the smallest distance to p_from_point. p_from_point,
   * if that point is contained in this shape
   */
  public Point nearestPoint(Point pFromPoint) {
    if (!isOutside(pFromPoint)) {
      return pFromPoint;
    }
    return nearestBorderPoint(pFromPoint);
  }

  @Override
  public FloatPoint nearestPointApprox(FloatPoint pFromPoint) {
    if (this.contains(pFromPoint)) {
      return pFromPoint;
    }
    return nearestBorderPointApprox(pFromPoint);
  }

  /** Returns the nearest point to p_from_point on the edge of the shape */
  public Point nearestBorderPoint(Point pFromPoint) {
    int lineCount = borderLineCount();
    if (lineCount == 0) {
      return null;
    }
    FloatPoint fromPointF = pFromPoint.toFloat();
    if (lineCount == 1) {
      return borderLine(0).perpendicularProjection(pFromPoint);
    }
    double minDist = Double.MAX_VALUE;
    int minDistInd = 0;

    // calculate the distance to the nearest corner first
    for (int i = 0; i < lineCount; i++) {
      FloatPoint currCornerF = cornerApprox(i);
      double currDist = currCornerF.distanceSquare(fromPointF);
      if (currDist < minDist) {
        minDist = currDist;
        minDistInd = i;
      }
    }

    Point nearestPoint = corner(minDistInd);

    int prevInd = lineCount - 2;
    int currInd = lineCount - 1;

    for (int nextInd = 0; nextInd < lineCount; nextInd++) {
      Point projection = borderLine(currInd).perpendicularProjection(pFromPoint);
      if ((!cornerIsBounded(currInd) || borderLine(prevInd).sideOf(projection) == Side.ON_THE_RIGHT)
          && (!cornerIsBounded(nextInd)
              || borderLine(nextInd).sideOf(projection) == Side.ON_THE_RIGHT)) {
        FloatPoint projectionF = projection.toFloat();
        double currDist = projectionF.distanceSquare(fromPointF);
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
  public FloatPoint nearestBorderPointApprox(FloatPoint pFromPoint) {
    FloatPoint[] nearestPoints = nearestBorderPointsApprox(pFromPoint, 1);
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
  public FloatPoint[] nearestBorderPointsApprox(FloatPoint pFromPoint, int pCount) {
    if (pCount <= 0) {
      return new FloatPoint[0];
    }
    int lineCount = borderLineCount();
    int resultCount = Math.min(pCount, lineCount);
    if (lineCount == 0) {
      return new FloatPoint[0];
    }
    if (lineCount == 1) {
      FloatPoint[] result = new FloatPoint[1];
      result[0] = pFromPoint.projectionApprox(borderLine(0));
      return result;
    }
    if (this.dimension() == 0) {
      FloatPoint[] result = new FloatPoint[1];
      result[0] = cornerApprox(0);
      return result;
    }
    FloatPoint[] nearestPoints = new FloatPoint[resultCount];
    double[] minDists = new double[resultCount];
    Arrays.fill(minDists, Double.MAX_VALUE);

    // calculate the distances to the nearest corners first
    for (int i = 0; i < lineCount; i++) {
      if (cornerIsBounded(i)) {
        FloatPoint currCorner = cornerApprox(i);
        double currDist = currCorner.distanceSquare(pFromPoint);
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
      FloatPoint projection = pFromPoint.projectionApprox(borderLine(currInd));
      if ((!cornerIsBounded(currInd) || borderLine(prevInd).sideOf(projection) == Side.ON_THE_RIGHT)
          && (!cornerIsBounded(nextInd)
              || borderLine(nextInd).sideOf(projection) == Side.ON_THE_RIGHT)) {
        double currDist = projection.distanceSquare(pFromPoint);
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
  public int indexOfNearestCorner(Point pFromPoint) {
    FloatPoint fromPointF = pFromPoint.toFloat();
    int result = 0;
    int cornerCount = borderLineCount();
    double minDist = Double.MIN_VALUE;
    for (int i = 0; i < cornerCount; i++) {
      double currDist = cornerApprox(i).distance(fromPointF);
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
  public FloatLine diagonalCornerSegment() {
    if (this.isEmpty()) {
      return null;
    }
    FloatPoint firstCorner = this.cornerApprox(0);
    FloatPoint lastCorner = this.cornerApprox(this.borderLineCount() / 2);
    return new FloatLine(firstCorner, lastCorner);
  }

  /**
   * Returns an approximation of the p_count nearest relative outside locations of p_shape in the
   * direction of different border lines of this shape. These relative locations are sorted in
   * ascending order (the shortest comes first).
   */
  public FloatPoint[] nearestRelativeOutsideLocations(TileShape pShape, int pCount) {
    int lineCount = borderLineCount();
    if (pCount <= 0 || lineCount < 3 || !this.intersects(pShape)) {
      return new FloatPoint[0];
    }

    int resultCount = Math.min(pCount, lineCount);

    FloatPoint[] translateCoors = new FloatPoint[resultCount];
    double[] minDists = new double[resultCount];
    Arrays.fill(minDists, Double.MAX_VALUE);

    int currInd = lineCount - 1;

    int otherLineCount = pShape.borderLineCount();

    for (int nextInd = 0; nextInd < lineCount; nextInd++) {
      double currMaxDist = 0;
      FloatPoint currTranslateCoor = FloatPoint.ZERO;
      for (int cornerNo = 0; cornerNo < otherLineCount; cornerNo++) {
        FloatPoint currCorner = pShape.cornerApprox(cornerNo);
        if (borderLine(currInd).sideOf(currCorner) == Side.ON_THE_RIGHT) {
          FloatPoint projection = currCorner.projectionApprox(borderLine(currInd));
          double currDist = projection.distanceSquare(currCorner);
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
  public ConvexShape shrink(double pOffset) {
    ConvexShape result = this.offset(-pOffset);
    if (result.isEmpty()) {
      IntBox centreBox = this.centreOfGravity().boundingBox();
      result = this.intersection(centreBox);
    }
    return result;
  }

  /**
   * Returns the maximum of the edge widths of the shape. Only defined when the shape is bounded.
   */
  public double length() {
    if (!this.isBounded()) {
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
    FloatPoint gravityPoint = this.centreOfGravity();
    for (int i = 0; i < borderLineCount(); i++) {
      double currDistance = Math.abs(borderLine(i).signedDistance(gravityPoint));
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
  public int[] touchingSides(TileShape pOther) {
    // search the first edge line of p_other with reverse direction >= right

    int sideNo2 = -1;
    Direction dir2 = null;
    for (int i = 0; i < pOther.borderLineCount(); i++) {
      Direction currDir = pOther.borderLine(i).direction();
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
    Direction dir1 = this.borderLine(0).direction();
    final int maxInd = this.borderLineCount() + pOther.borderLineCount();

    for (int i = 0; i < maxInd; i++) {
      int compare = dir2.compareTo(dir1);
      if (compare == 0) {
        if (this.borderLine(sideNo1).isEqualOrOpposite(pOther.borderLine(sideNo2))) {
          int[] result = new int[2];
          result[0] = sideNo1;
          result[1] = sideNo2;
          return result;
        }
      }
      if (compare >= 0) // dir2 is bigger than dir1
      {
        sideNo1 = (sideNo1 + 1) % this.borderLineCount();
        dir1 = this.borderLine(sideNo1).direction();
      } else // dir1 is bigger than dir2
      {
        sideNo2 = (sideNo2 + 1) % pOther.borderLineCount();
        dir2 = pOther.borderLine(sideNo2).direction().opposite();
      }
    }
    return new int[0];
  }

  /**
   * Calculates the minimal distance of p_line to this shape, assuming, that p_line is on the left
   * of this shape. Returns -1, if p_line is on the right of this shape or intersects with the
   * interior of this shape.
   */
  public double distanceToTheLeft(Line pLine) {
    double result = Integer.MAX_VALUE;
    for (int i = 0; i < this.borderLineCount(); i++) {
      FloatPoint currCorner = this.cornerApprox(i);
      Side lineSide = pLine.sideOf(currCorner, 1);
      if (lineSide == Side.COLLINEAR) {
        lineSide = pLine.sideOf(this.corner(i));
      }
      if (lineSide == Side.ON_THE_RIGHT) {
        // currPoint would be outside the result shape
        result = -1;
        break;
      }
      result = Math.min(result, pLine.signedDistance(currCorner));
    }
    return result;
  }

  /**
   * Returns Side.COLLINEAR, if p_line intersects with the interior of this shape, Side.ON_THE_LEFT,
   * if this shape is completely on the left of p_line or Side.ON_THE_RIGHT, if this shape is
   * completely on the right of p_line.
   */
  public Side sideOf(Line pLine) {
    boolean onTheLeft = false;
    boolean onTheRight = false;
    for (int i = 0; i < this.borderLineCount(); i++) {
      Side currSide = pLine.sideOf(this.corner(i));
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
  public TileShape turn90Degree(int pFactor, IntPoint pPole) {
    Line[] newLines = new Line[borderLineCount()];
    for (int i = 0; i < newLines.length; i++) {
      newLines[i] = this.borderLine(i).turn90Degree(pFactor, pPole);
    }
    return getInstance(newLines);
  }

  @Override
  public TileShape rotateApprox(double pAngle, FloatPoint pPole) {
    if (pAngle == 0) {
      return this;
    }
    IntPoint[] newCorners = new IntPoint[borderLineCount()];
    for (int i = 0; i < newCorners.length; i++) {

      newCorners[i] = this.cornerApprox(i).rotate(pAngle, pPole).round();
    }
    Polygon cornerPolygon = new Polygon(newCorners);
    Point[] polygonCorners = cornerPolygon.cornerArray();
    TileShape result;
    if (polygonCorners.length >= 3) {
      result = getInstance(polygonCorners);
    } else if (polygonCorners.length == 2) {
      Polyline currPolyline = new Polyline(polygonCorners);
      LineSegment currSegment = new LineSegment(currPolyline, 0);
      result = currSegment.toSimplex();
    } else if (polygonCorners.length == 1) {
      result = getInstance(polygonCorners[0]);
    } else {
      result = Simplex.EMPTY;
    }
    return result;
  }

  @Override
  public TileShape mirrorVertical(IntPoint pPole) {
    Line[] newLines = new Line[borderLineCount()];
    for (int i = 0; i < newLines.length; i++) {
      newLines[i] = this.borderLine(i).mirrorVertical(pPole);
    }
    return getInstance(newLines);
  }

  @Override
  public TileShape mirrorHorizontal(IntPoint pPole) {
    Line[] newLines = new Line[borderLineCount()];
    for (int i = 0; i < newLines.length; i++) {
      newLines[i] = this.borderLine(i).mirrorHorizontal(pPole);
    }
    return getInstance(newLines);
  }

  /**
   * Calculates the border line of this shape intersecting the ray from p_from_point into the
   * direction p_direction. p_from_point is assumed to be inside this shape, otherwise -1 is
   * returned.
   */
  public int intersectingBorderLineNo(Point pFromPoint, Direction pDirection) {
    if (!this.contains(pFromPoint)) {
      return -1;
    }
    FloatPoint fromPoint = pFromPoint.toFloat();
    Line intersectionLine = new Line(pFromPoint, pDirection);
    FloatPoint secondLinePoint = intersectionLine.b.toFloat();
    int result = -1;
    double minDistance = Float.MAX_VALUE;
    for (int i = 0; i < this.borderLineCount(); i++) {
      Line currBorderLine = this.borderLine(i);
      FloatPoint currIntersection = currBorderLine.intersectionApprox(intersectionLine);
      if (currIntersection.x >= Integer.MAX_VALUE) {
        continue; // lines are parallel
      }
      double currDistance = currIntersection.distanceSquare(fromPoint);
      if (currDistance < minDistance) {
        boolean directionOk =
            currBorderLine.sideOf(secondLinePoint) == Side.ON_THE_LEFT
                || secondLinePoint.distanceSquare(currIntersection) < currDistance;
        if (directionOk) {
          result = i;
          minDistance = currDistance;
        }
      }
    }
    return result;
  }

  /** Cuts p_shape out of this shape and divides the result into convex pieces */
  public abstract TileShape[] cutout(TileShape pShape);

  /**
   * Returns an array of tuples of integers. The length of the array is the number of points, where
   * p_polyline enters or leaves the interior of this shape. The first coordinate of the tuple is
   * the number of the line segment of p_polyline, which enters the simplex and the second
   * coordinate of the tuple is the number of the edge_line of the simplex, which is crossed there.
   * That means that the entrance point is the intersection of this 2 lines.
   */
  public int[][] entrancePoints(Polyline pPolyline) {
    int[][] result = new int[2 * pPolyline.arr.length][2];
    int intersectionCount = 0;
    int prevIntersectionLineNo = -1;
    int prevIntersectionEdgeNo = -1;
    for (int lineNo = 1; lineNo < pPolyline.arr.length - 1; lineNo++) {
      LineSegment currLineSeg = new LineSegment(pPolyline, lineNo);
      int[] currIntersections = currLineSeg.borderIntersections(this);
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
  public Polyline[] cutout(Polyline pPolyline) {
    int[][] intersectionNo = this.entrancePoints(pPolyline);
    Point firstCorner = pPolyline.firstCorner();
    boolean firstCornerIsInside = this.containsInside(firstCorner);
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
      result[0] = pPolyline;
      return result;
    }
    Collection<Polyline> pieces = new LinkedList<>();
    int currIntersectionNo = 0;
    int[] currIntersectionTuple = intersectionNo[currIntersectionNo];
    Point firstIntersection =
        pPolyline.arr[currIntersectionTuple[0]].intersection(
            this.borderLine(currIntersectionTuple[1]));
    if (!firstCornerIsInside)
    // calculate outside piece at start
    {
      if (!firstCorner.equals(firstIntersection))
      // otherwise skip 1 point outside polyline at the start
      {
        int currPolylineIntersectionNo = currIntersectionTuple[0];
        Line[] currLines = new Line[currPolylineIntersectionNo + 2];
        System.arraycopy(pPolyline.arr, 0, currLines, 0, currPolylineIntersectionNo + 1);
        // close the polyline piece with the intersected edge line.
        currLines[currPolylineIntersectionNo + 1] = this.borderLine(currIntersectionTuple[1]);
        Polyline currPiece = new Polyline(currLines);
        if (!currPiece.isEmpty()) {
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
        if (this.isOutside(pPolyline.corner(i))) {
          insertPiece = true;
          break;
        }
      }

      if (insertPiece) {
        Line[] currLines =
            new Line[nextIntersectionNoOfPolyline - currIntersectionNoOfPolyline + 3];
        currLines[0] = this.borderLine(currIntersectionTuple[1]);
        System.arraycopy(
            pPolyline.arr, currIntersectionNoOfPolyline, currLines, 1, currLines.length - 2);
        currLines[currLines.length - 1] = this.borderLine(nextIntersectionTuple[1]);
        Polyline currPiece = new Polyline(currLines);
        if (!currPiece.isEmpty()) {
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
      Line[] currLines = new Line[pPolyline.arr.length - currPolylineIntersectionNo + 1];
      currLines[0] = this.borderLine(currIntersectionTuple[1]);
      System.arraycopy(
          pPolyline.arr, currPolylineIntersectionNo, currLines, 1, currLines.length - 1);
      Polyline currPiece = new Polyline(currLines);
      if (!currPiece.isEmpty()) {
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
  public TileShape[] splitToConvex() {
    TileShape[] result = new TileShape[1];
    result[0] = this;
    return result;
  }

  /**
   * Divides this shape into sections with width and height at most p_max_section_width of about
   * equal size.
   */
  public TileShape[] divideIntoSections(double pMaxSectionWidth) {
    if (this.isEmpty()) {
      TileShape[] result = new TileShape[1];
      result[0] = this;
      return result;
    }
    TileShape[] sectionBoxes = this.boundingBox().divideIntoSections(pMaxSectionWidth);
    Collection<TileShape> sectionList = new LinkedList<>();
    for (int i = 0; i < sectionBoxes.length; i++) {
      TileShape currSection = this.intersectionWithSimplify(sectionBoxes[i]);
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
  public boolean isIntersectedInteriorBy(LineSegment pLineSegment) {
    return isIntersectedInteriorBy(
        pLineSegment.startPoint(), pLineSegment.endPoint(), pLineSegment.getLine());
  }

  /**
   * Checks if the line segment defined by p_start_point, p_end_point and p_line has a common point
   * with the interior of this shape.
   */
  public boolean isIntersectedInteriorBy(Point pStartPoint, Point pEndPoint, Line pLine) {
    FloatPoint floatStartPoint = pStartPoint.toFloat();
    FloatPoint floatEndPoint = pEndPoint.toFloat();

    Side[] borderLineSideOfStartPointArr = new Side[this.borderLineCount()];
    Side[] borderLineSideOfEndPointArr = new Side[borderLineSideOfStartPointArr.length];
    for (int i = 0; i < borderLineSideOfStartPointArr.length; i++) {
      Line currBorderLine = this.borderLine(i);
      Side borderLineSideOfStartPoint = currBorderLine.sideOf(floatStartPoint, 1);
      if (borderLineSideOfStartPoint == Side.COLLINEAR) {
        borderLineSideOfStartPoint = currBorderLine.sideOf(pStartPoint);
      }
      Side borderLineSideOfEndPoint = currBorderLine.sideOf(floatEndPoint, 1);
      if (borderLineSideOfEndPoint == Side.COLLINEAR) {
        borderLineSideOfEndPoint = currBorderLine.sideOf(pEndPoint);
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
    Line segmentLine = pLine;
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
        Side prevCornerSide = segmentLine.sideOf(this.cornerApprox(i), 1);
        if (prevCornerSide == Side.COLLINEAR) {
          prevCornerSide = segmentLine.sideOf(this.corner(i));
        }
        int nextCornerIndex;
        if (i == borderLineSideOfStartPointArr.length - 1) {
          nextCornerIndex = 0;
        } else {
          nextCornerIndex = i + 1;
        }
        Side nextCornerSide = segmentLine.sideOf(this.cornerApprox(nextCornerIndex), 1);
        if (nextCornerSide == Side.COLLINEAR) {
          nextCornerSide = segmentLine.sideOf(this.corner(nextCornerIndex));
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
  abstract TileShape intersection(Simplex pOther);

  abstract TileShape intersection(IntOctagon pOther);

  abstract TileShape intersection(IntBox pOther);

  /** Auxiliary function to implement the public function cutout(TileShape p_shape) */
  abstract TileShape[] cutoutFrom(IntBox pShape);

  /** Auxiliary function to implement the public function cutout(TileShape p_shape) */
  abstract TileShape[] cutoutFrom(IntOctagon pShape);

  /** Auxiliary function to implement the public function cutout(TileShape p_shape) */
  abstract TileShape[] cutoutFrom(Simplex pShape);
}

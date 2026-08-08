package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Abstract class with functions for shapes, whose borders consist ob straight lines. */
public abstract class PolylineShape implements Shape, Serializable {

  /** returns true, if the shape has no infinite part at this corner */
  public abstract boolean cornerIsBounded(int pNo);

  /** Returns the number of borderlines of the shape */
  public abstract int borderLineCount();

  /**
   * Returns the p_no-th corner of this shape for p_no between 0 and border_line_count() - 1. The
   * corners are sorted starting with the smallest y-coordinate in counterclock sense around the
   * shape. If there are several corners with the smallest y-coordinate, the corner with the
   * smallest x-coordinate comes first. Consecutive corners may be equal.
   */
  public abstract Point corner(int pNo);

  /** Turns this shape by p_factor times 90 degree around p_pole. */
  @Override
  public abstract PolylineShape turn90Degree(int pFactor, IntPoint pPole);

  /** Rotates this shape around p_pole by p_angle. The result may be not exact. */
  @Override
  public abstract PolylineShape rotateApprox(double pAngle, FloatPoint pPole);

  /** Mirrors this shape at the horizontal line through p_pole. */
  @Override
  public abstract PolylineShape mirrorHorizontal(IntPoint pPole);

  /** Mirrors this shape at the vertical line through p_pole. */
  @Override
  public abstract PolylineShape mirrorVertical(IntPoint pPole);

  /** Returns the affine translation of the area by p_vector */
  @Override
  public abstract PolylineShape translateBy(Vector pVector);

  /** Return all unbounded corners of this shape. */
  public Point[] boundedCorners() {
    int cornerCount = this.borderLineCount();
    Collection<Point> resultList = new LinkedList<>();
    for (int i = 0; i < cornerCount; i++) {
      if (this.cornerIsBounded(i)) {
        resultList.add(this.corner(i));
      }
    }
    Point[] result = new Point[resultList.size()];
    Iterator<Point> it = resultList.iterator();
    for (int i = 0; i < result.length; i++) {
      result[i] = it.next();
    }
    return result;
  }

  /**
   * Returns an approximation of the p_no-th corner of this shape for p_no between 0 and
   * border_line_count() - 1. If the shape is not bounded at this corner, the coordinates of the
   * result will be set to Integer.MAX_VALUE.
   */
  public FloatPoint cornerApprox(int pNo) {
    return corner(pNo).toFloat();
  }

  /**
   * Returns an approximation of the all corners of this shape. If the shape is not bounded at a
   * corner, the coordinates will be set to Integer.MAX_VALUE.
   */
  @Override
  public FloatPoint[] cornerApproxArr() {
    int cornerCount = this.borderLineCount();
    FloatPoint[] result = new FloatPoint[cornerCount];
    for (int i = 0; i < cornerCount; i++) {
      result[i] = this.cornerApprox(i);
    }
    return result;
  }

  /**
   * If p_point is equal to a corner of this shape, the number of that corner is returned; -1
   * otherwise.
   */
  public int equalsCorner(Point pPoint) {
    for (int i = 0; i < borderLineCount(); i++) {
      if (pPoint.equals(corner(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the cumulative border line length of the shape. If the shape is unbounded,
   * Integer.MAX_VALUE is returned.
   */
  @Override
  public double circumference() {
    if (!isBounded()) {
      return Integer.MAX_VALUE;
    }
    int cornerCount = borderLineCount();
    double result = 0;
    FloatPoint prevCorner = cornerApprox(cornerCount - 1);
    for (int i = 0; i < cornerCount; i++) {
      FloatPoint currCorner = cornerApprox(i);
      result += currCorner.distance(prevCorner);
      prevCorner = currCorner;
    }
    return result;
  }

  /** Returns the arithmetic middle of the corners of this shape */
  @Override
  public FloatPoint centreOfGravity() {
    int cornerCount = borderLineCount();
    double x = 0;
    double y = 0;
    for (int i = 0; i < cornerCount; i++) {
      FloatPoint currPoint = cornerApprox(i);
      x += currPoint.x;
      y += currPoint.y;
    }
    x /= cornerCount;
    y /= cornerCount;
    return new FloatPoint(x, y);
  }

  /** checks, if this shape is completely contained in p_box. */
  @Override
  public boolean isContainedIn(IntBox pBox) {
    return pBox.contains(boundingBox());
  }

  /**
   * Returns the index of the corner of the shape, so that all other points of the shape are to the
   * right of the line from p_from_point to this corner
   */
  public int indexOfLeftMostCorner(FloatPoint pFromPoint) {
    FloatPoint leftMostCorner = cornerApprox(0);
    int cornerCount = borderLineCount();
    int result = 0;
    for (int i = 1; i < cornerCount; i++) {
      FloatPoint currCorner = cornerApprox(i);
      if (currCorner.sideOf(pFromPoint, leftMostCorner) == Side.ON_THE_LEFT) {
        leftMostCorner = currCorner;
        result = i;
      }
    }
    return result;
  }

  /**
   * Returns the index of the corner of the shape, so that all other points of the shape are to the
   * left of the line from p_from_point to this corner
   */
  public int indexOfRightMostCorner(FloatPoint pFromPoint) {
    FloatPoint rightMostCorner = cornerApprox(0);
    int cornerCount = borderLineCount();
    int result = 0;
    for (int i = 1; i < cornerCount; i++) {
      FloatPoint currCorner = cornerApprox(i);
      if (currCorner.sideOf(pFromPoint, rightMostCorner) == Side.ON_THE_RIGHT) {
        rightMostCorner = currCorner;
        result = i;
      }
    }
    return result;
  }

  /**
   * Returns a FloatLine result, so that result.a is an approximation of the left most corner of
   * this shape when viewed from p_from_point, and result.b is an approximation of the right most
   * corner.
   */
  public FloatLine polarLineSegment(FloatPoint pFromPoint) {
    if (this.isEmpty()) {
      FRLogger.warn("PolylineShape.polarLineSegment: shape is empty");
      return null;
    }
    FloatPoint leftMostCorner = cornerApprox(0);
    FloatPoint rightMostCorner = cornerApprox(0);
    int cornerCount = borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      FloatPoint currCorner = cornerApprox(i);
      if (currCorner.sideOf(pFromPoint, rightMostCorner) == Side.ON_THE_RIGHT) {
        rightMostCorner = currCorner;
      }
      if (currCorner.sideOf(pFromPoint, leftMostCorner) == Side.ON_THE_LEFT) {
        leftMostCorner = currCorner;
      }
    }
    return new FloatLine(leftMostCorner, rightMostCorner);
  }

  /** Returns the p_no-th border line of this shape. */
  public abstract Line borderLine(int pNo);

  /** Returns the previous border line or corner number of this shape. */
  public int prevNo(int pNo) {
    int result;
    if (pNo == 0) {
      result = borderLineCount() - 1;
    } else {
      result = pNo - 1;
    }
    return result;
  }

  /** Returns the next border line or corner number of this shape. */
  public int nextNo(int pNo) {
    return (pNo + 1) % borderLineCount();
  }

  @Override
  public PolylineShape getBorder() {
    return this;
  }

  @Override
  public Shape[] getHoles() {
    return new Shape[0];
  }

  /** Checks, if this shape and p_line have a common point. */
  public boolean intersects(Line pLine) {
    Side sideOfFirstCorner = pLine.sideOf(corner(0));
    if (sideOfFirstCorner == Side.COLLINEAR) {
      return true;
    }
    for (int i = 1; i < this.borderLineCount(); i++) {
      if (pLine.sideOf(corner(i)) != sideOfFirstCorner) {
        return true;
      }
    }
    return false;
  }

  /** Calculates the left most corner of this shape, when looked at from p_from_point. */
  public Point leftMostCorner(Point pFromPoint) {
    if (this.isEmpty()) {
      return pFromPoint;
    }
    Point result = this.corner(0);
    int cornerCount = this.borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      Point currCorner = this.corner(i);
      if (currCorner.sideOf(pFromPoint, result) == Side.ON_THE_LEFT) {
        result = currCorner;
      }
    }
    return result;
  }

  /** Calculates the left most corner of this shape, when looked at from p_from_point. */
  public Point rightMostCorner(Point pFromPoint) {
    if (this.isEmpty()) {
      return pFromPoint;
    }
    Point result = this.corner(0);
    int cornerCount = this.borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      Point currCorner = this.corner(i);
      if (currCorner.sideOf(pFromPoint, result) == Side.ON_THE_RIGHT) {
        result = currCorner;
      }
    }
    return result;
  }
}

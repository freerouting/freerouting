package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/** Abstract class with functions for shapes whose borders consist of straight lines. */
public abstract class PolylineShape implements Shape, Serializable {

  /** Returns true if the shape has no infinite part at this corner. */
  public abstract boolean cornerIsBounded(int no);

  /** Returns the number of borderlines of the shape. */
  public abstract int borderLineCount();

  /**
   * Returns the no-th corner of this shape for no between 0 and border_line_count() - 1. The
   * corners are sorted starting with the smallest y-coordinate in counterclock sense around the
   * shape. If there are several corners with the smallest y-coordinate, the corner with the
   * smallest x-coordinate comes first. Consecutive corners may be equal.
   */
  public abstract Point corner(int no);

  /** Turns this shape by factor times 90 degree around pole. */
  @Override
  public abstract PolylineShape turn90Degree(int factor, IntPoint pole);

  /** Rotates this shape around pole by angle. The result may be not exact. */
  @Override
  public abstract PolylineShape rotateApprox(double angle, FloatPoint pole);

  /** Mirrors this shape at the horizontal line through pole. */
  @Override
  public abstract PolylineShape mirrorHorizontal(IntPoint pole);

  /** Mirrors this shape at the vertical line through pole. */
  @Override
  public abstract PolylineShape mirrorVertical(IntPoint pole);

  /** Returns the affine translation of the area by vector. */
  @Override
  public abstract PolylineShape translateBy(Vector vector);

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
   * Returns an approximation of the no-th corner of this shape for no between 0 and
   * border_line_count() - 1. If the shape is not bounded at this corner, the coordinates of the
   * result will be set to Integer.MAX_VALUE.
   */
  public FloatPoint cornerApprox(int no) {
    return corner(no).toFloat();
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
   * If point is equal to a corner of this shape, the number of that corner is returned; -1
   * otherwise.
   */
  public int equalsCorner(Point point) {
    for (int i = 0; i < borderLineCount(); i++) {
      if (point.equals(corner(i))) {
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
      FloatPoint currentCorner = cornerApprox(i);
      result += currentCorner.distance(prevCorner);
      prevCorner = currentCorner;
    }
    return result;
  }

  /** Returns the arithmetic middle of the corners of this shape. */
  @Override
  public FloatPoint centreOfGravity() {
    int cornerCount = borderLineCount();
    double x = 0;
    double y = 0;
    for (int i = 0; i < cornerCount; i++) {
      FloatPoint currentPoint = cornerApprox(i);
      x += currentPoint.x;
      y += currentPoint.y;
    }
    x /= cornerCount;
    y /= cornerCount;
    return new FloatPoint(x, y);
  }

  /** Checks if this shape is completely contained in box. */
  @Override
  public boolean isContainedIn(IntBox box) {
    return box.contains(boundingBox());
  }

  /**
   * Returns the index of the corner of the shape, so that all other points of the shape are to the
   * right of the line from fromPoint to this corner.
   */
  public int indexOfLeftMostCorner(FloatPoint fromPoint) {
    FloatPoint leftMostCorner = cornerApprox(0);
    int cornerCount = borderLineCount();
    int result = 0;
    for (int i = 1; i < cornerCount; i++) {
      FloatPoint currentCorner = cornerApprox(i);
      if (currentCorner.sideOf(fromPoint, leftMostCorner) == Side.ON_THE_LEFT) {
        leftMostCorner = currentCorner;
        result = i;
      }
    }
    return result;
  }

  /**
   * Returns the index of the corner of the shape, so that all other points of the shape are to the
   * left of the line from fromPoint to this corner.
   */
  public int indexOfRightMostCorner(FloatPoint fromPoint) {
    FloatPoint rightMostCorner = cornerApprox(0);
    int cornerCount = borderLineCount();
    int result = 0;
    for (int i = 1; i < cornerCount; i++) {
      FloatPoint currentCorner = cornerApprox(i);
      if (currentCorner.sideOf(fromPoint, rightMostCorner) == Side.ON_THE_RIGHT) {
        rightMostCorner = currentCorner;
        result = i;
      }
    }
    return result;
  }

  /**
   * Returns a FloatLine result, so that result.a is an approximation of the left most corner of
   * this shape when viewed from fromPoint, and result.b is an approximation of the right most
   * corner.
   */
  public FloatLine polarLineSegment(FloatPoint fromPoint) {
    if (this.isEmpty()) {
      FRLogger.warn("PolylineShape.polarLineSegment: shape is empty");
      return null;
    }
    FloatPoint leftMostCorner = cornerApprox(0);
    FloatPoint rightMostCorner = cornerApprox(0);
    int cornerCount = borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      FloatPoint currentCorner = cornerApprox(i);
      if (currentCorner.sideOf(fromPoint, rightMostCorner) == Side.ON_THE_RIGHT) {
        rightMostCorner = currentCorner;
      }
      if (currentCorner.sideOf(fromPoint, leftMostCorner) == Side.ON_THE_LEFT) {
        leftMostCorner = currentCorner;
      }
    }
    return new FloatLine(leftMostCorner, rightMostCorner);
  }

  /** Returns the no-th border line of this shape. */
  public abstract Line borderLine(int no);

  /** Returns the previous border line or corner number of this shape. */
  public int prevNo(int no) {
    int result;
    if (no == 0) {
      result = borderLineCount() - 1;
    } else {
      result = no - 1;
    }
    return result;
  }

  /** Returns the next border line or corner number of this shape. */
  public int nextNo(int no) {
    return (no + 1) % borderLineCount();
  }

  @Override
  public PolylineShape getBorder() {
    return this;
  }

  @Override
  public Shape[] getHoles() {
    return new Shape[0];
  }

  /** Checks, if this shape and line have a common point. */
  public boolean intersects(Line line) {
    Side sideOfFirstCorner = line.sideOf(corner(0));
    if (sideOfFirstCorner == Side.COLLINEAR) {
      return true;
    }
    for (int i = 1; i < this.borderLineCount(); i++) {
      if (line.sideOf(corner(i)) != sideOfFirstCorner) {
        return true;
      }
    }
    return false;
  }

  /** Calculates the left most corner of this shape, when looked at from fromPoint. */
  public Point leftMostCorner(Point fromPoint) {
    if (this.isEmpty()) {
      return fromPoint;
    }
    Point result = this.corner(0);
    int cornerCount = this.borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      Point currentCorner = this.corner(i);
      if (currentCorner.sideOf(fromPoint, result) == Side.ON_THE_LEFT) {
        result = currentCorner;
      }
    }
    return result;
  }

  /** Calculates the left most corner of this shape, when looked at from fromPoint. */
  public Point rightMostCorner(Point fromPoint) {
    if (this.isEmpty()) {
      return fromPoint;
    }
    Point result = this.corner(0);
    int cornerCount = this.borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      Point currentCorner = this.corner(i);
      if (currentCorner.sideOf(fromPoint, result) == Side.ON_THE_RIGHT) {
        result = currentCorner;
      }
    }
    return result;
  }
}

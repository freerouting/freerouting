package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.math.BigInteger;

/** Implements functionality for lines in the plane. */
public class Line implements Comparable<Line>, Serializable {

  public final Point a;
  public final Point b;
  private transient Direction dir; // should only be accessed from get_direction().

  /** creates a directed Line from two Points */
  public Line(Point p_a, Point p_b) {
    a = p_a;
    b = p_b;
    dir = null;
    if (!(a instanceof IntPoint && b instanceof IntPoint)) {
      FRLogger.warn("Line(p_a, p_b) only implemented for IntPoints till now");
    }
  }

  /** creates a directed Line from four integer Coordinates */
  public Line(int p_a_x, int p_a_y, int p_b_x, int p_b_y) {
    a = new IntPoint(p_a_x, p_a_y);
    b = new IntPoint(p_b_x, p_b_y);
    dir = null;
  }

  /** creates a directed Line from a Point and a Direction */
  public Line(Point p_a, Direction p_dir) {
    a = p_a;
    b = p_a.translateBy(p_dir.getVector());
    dir = p_dir;
    if (!(a instanceof IntPoint && b instanceof IntPoint)) {
      FRLogger.warn("Line(p_a, p_dir) only implemented for IntPoints till now");
    }
  }

  /** create a directed line from an IntPoint and an IntDirection */
  public static Line getInstance(Point p_a, Direction p_dir) {
    Point b = p_a.translateBy(p_dir.getVector());
    return new Line(p_a, b);
  }

  /** returns true, if this and p_ob define the same line */
  public int getIdNo() {
    return 31 * a.getIdNo() + b.getIdNo();
  }

  @Override
  public final boolean equals(Object p_ob) {
    if (this == p_ob) {
      return true;
    }
    if (p_ob == null) {
      return false;
    }
    if (!(p_ob instanceof Line other)) {
      return false;
    }
    if (sideOf(other.a) != Side.COLLINEAR) {
      return false;
    }
    return direction().equals(other.direction());
  }

  /**
   * Returns true, if this and p_other define the same line. Is designed for good performance, but
   * works only for lines consisting of IntPoints.
   */
  public final boolean fastEquals(Line p_other) {
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    IntPoint otherA = (IntPoint) p_other.a;
    double dx1 = otherA.x - thisA.x;
    double dy1 = otherA.y - thisA.y;
    double dx2 = thisB.x - thisA.x;
    double dy2 = thisB.y - thisA.y;
    double det = dx1 * dy2 - dx2 * dy1;
    if (det != 0) {
      return false;
    }
    return direction().equals(p_other.direction());
  }

  /** get the direction of this directed line */
  public Direction direction() {
    if (dir == null) {
      Vector d = b.differenceBy(a);
      dir = Direction.getInstance(d);
    }
    return dir;
  }

  /**
   * The function returns Side.ON_THE_LEFT, if this Line is on the left of p_point,
   * Side.ON_THE_RIGHT, if this Line is on the right of p_point and Side.COLLINEAR, if this Line
   * contains p_point.
   */
  public Side sideOf(Point p_point) {
    Side result = p_point.sideOf(this);
    return result.negate();
  }

  /**
   * Returns Side.COLLINEAR, if p_point is on the line with tolerance p_tolerance. Otherwise,
   * Side.ON_THE_LEFT, if this line is on the left of p_point, or Side.ON_THE_RIGHT, if this line is
   * on the right of p_point,
   */
  public Side sideOf(FloatPoint p_point, double p_tolerance) {
    // only implemented for IntPoint lines for performance reasons
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    double det =
        (thisB.y - thisA.y) * (p_point.x - thisA.x) - (thisB.x - thisA.x) * (p_point.y - thisA.y);
    Side result;
    if (det - p_tolerance > 0) {
      result = Side.ON_THE_LEFT;
    } else if (det + p_tolerance < 0) {
      result = Side.ON_THE_RIGHT;
    } else {
      result = Side.COLLINEAR;
    }

    return result;
  }

  /**
   * returns Side.ON_THE_LEFT, if this line is on the left of p_point, Side.ON_THE_RIGHT, if this
   * line is on the right of p_point, Side.COLLINEAR otherwise.
   */
  public Side sideOf(FloatPoint p_point) {
    return sideOf(p_point, 0);
  }

  /**
   * Returns Side.ON_THE_LEFT, if this line is on the left of the intersection of p_1 and p_2,
   * Side.ON_THE_RIGHT, if this line is on the right of the intersection, and Side.COLLINEAR, if all
   * 3 lines intersect in exactly 1 point.
   */
  public Side sideOfIntersection(Line p_1, Line p_2) {

    FloatPoint intersectionApprox = p_1.intersectionApprox(p_2);
    Side result = this.sideOf(intersectionApprox, 1.0);
    if (result == Side.COLLINEAR) {
      // Previous calculation was with FloatPoints and a tolerance
      // for performance reasons. Make an exact check for
      // collinearity now with class Point instead of FloatPoint.
      Point intersection = p_1.intersection(p_2);
      result = this.sideOf(intersection);
    }
    return result;
  }

  /** Looks, if all interior points of p_tile are on the right side of this line. */
  public boolean isOnTheLeft(TileShape p_tile) {
    for (int i = 0; i < p_tile.borderLineCount(); i++) {
      if (this.sideOf(p_tile.corner(i)) == Side.ON_THE_RIGHT) {
        return false;
      }
    }
    return true;
  }

  /** Looks, if all interior points of p_tile are on the left side of this line. */
  public boolean isOnTheRight(TileShape p_tile) {
    for (int i = 0; i < p_tile.borderLineCount(); i++) {
      if (this.sideOf(p_tile.corner(i)) == Side.ON_THE_LEFT) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the signed distance of this line from p_point. The result will be positive, if the line
   * is on the left of p_point, else negative.
   */
  public double signedDistance(FloatPoint p_point) {
    // only implemented for IntPoint lines for performance reasons
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    double dx = thisB.x - thisA.x;
    double dy = thisB.y - thisA.y;
    double det = dy * (p_point.x - thisA.x) - dx * (p_point.y - thisA.y);
    // area of the parallelogramm spanned by the 3 points
    double length = Math.sqrt(dx * dx + dy * dy);
    return det / length;
  }

  /**
   * returns true, if the 2 lines define the same set of points, but may have opposite directions
   */
  public boolean overlaps(Line p_other) {
    return sideOf(p_other.a) == Side.COLLINEAR && sideOf(p_other.b) == Side.COLLINEAR;
  }

  /** Returns the line defining the same set of points, but with opposite direction */
  public Line opposite() {
    return new Line(b, a);
  }

  /**
   * Returns the intersection point of the 2 lines. If the lines are parallel result.is_infinite()
   * will be true.
   */
  public Point intersection(Line p_other) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntVector delta1 = (IntVector) b.differenceBy(a);
    IntVector delta2 = (IntVector) p_other.b.differenceBy(p_other.a);
    // Separate handling for orthogonal and 45 degree lines for better performance
    if (delta1.x == 0) // this line is vertical
    {
      if (delta2.y == 0) // other line is horizontal
      {
        return new IntPoint(((IntPoint) this.a).x, ((IntPoint) p_other.a).y);
      }
      if (delta2.x == delta2.y) // other line is right diagonal
      {
        int thisX = ((IntPoint) this.a).x;
        IntPoint otherA = (IntPoint) p_other.a;
        return new IntPoint(thisX, otherA.y + thisX - otherA.x);
      }
      if (delta2.x == -delta2.y) // other line is left diagonal
      {
        int thisX = ((IntPoint) this.a).x;
        IntPoint otherA = (IntPoint) p_other.a;
        return new IntPoint(thisX, otherA.y + otherA.x - thisX);
      }
    } else if (delta1.y == 0) // this line is horizontal
    {
      if (delta2.x == 0) // other line is vertical
      {
        return new IntPoint(((IntPoint) p_other.a).x, ((IntPoint) this.a).y);
      }
      if (delta2.x == delta2.y) // other line is right diagonal
      {
        int thisY = ((IntPoint) this.a).y;
        IntPoint otherA = (IntPoint) p_other.a;
        return new IntPoint(otherA.x + thisY - otherA.y, thisY);
      }
      if (delta2.x == -delta2.y) // other line is left diagonal
      {
        int thisY = ((IntPoint) this.a).y;
        IntPoint otherA = (IntPoint) p_other.a;
        return new IntPoint(otherA.x + otherA.y - thisY, thisY);
      }
    } else if (delta1.x == delta1.y) // this line is right diagonal
    {
      if (delta2.x == 0) // other line is vertical
      {
        int otherX = ((IntPoint) p_other.a).x;
        IntPoint thisA = (IntPoint) this.a;
        return new IntPoint(otherX, thisA.y + otherX - thisA.x);
      }
      if (delta2.y == 0) // other line is horizontal
      {
        int otherY = ((IntPoint) p_other.a).y;
        IntPoint thisA = (IntPoint) this.a;
        return new IntPoint(thisA.x + otherY - thisA.y, otherY);
      }
    } else if (delta1.x == -delta1.y) // this line is left diagonal
    {
      if (delta2.x == 0) // other line is vertical
      {
        int otherX = ((IntPoint) p_other.a).x;
        IntPoint thisA = (IntPoint) this.a;
        return new IntPoint(otherX, thisA.y + thisA.x - otherX);
      }
      if (delta2.y == 0) // other line is horizontal
      {
        int otherY = ((IntPoint) p_other.a).y;
        IntPoint thisA = (IntPoint) this.a;
        return new IntPoint(thisA.x + thisA.y - otherY, otherY);
      }
    }

    BigInteger det1 = BigInteger.valueOf(((IntPoint) a).determinant((IntPoint) b));
    BigInteger det2 = BigInteger.valueOf(((IntPoint) p_other.a).determinant((IntPoint) p_other.b));
    BigInteger det = BigInteger.valueOf(delta2.determinant(delta1));
    BigInteger tmp1 = det1.multiply(BigInteger.valueOf(delta2.x));
    BigInteger tmp2 = det2.multiply(BigInteger.valueOf(delta1.x));
    BigInteger isX = tmp1.subtract(tmp2);
    tmp1 = det1.multiply(BigInteger.valueOf(delta2.y));
    tmp2 = det2.multiply(BigInteger.valueOf(delta1.y));
    BigInteger isY = tmp1.subtract(tmp2);
    int signum = det.signum();
    if (signum != 0) {
      if (signum < 0) {
        det = det.negate();
        isX = isX.negate();
        isY = isY.negate();
      }
      if (isX.mod(det).signum() == 0 && isY.mod(det).signum() == 0) {
        isX = isX.divide(det);
        isY = isY.divide(det);
        if (Math.abs(isX.doubleValue()) <= Limits.CRIT_INT
            && Math.abs(isY.doubleValue()) <= Limits.CRIT_INT) {
          return new IntPoint(isX.intValue(), isY.intValue());
        }
        det = BigInteger.ONE;
      }
    }
    return new RationalPoint(isX, isY, det);
  }

  /**
   * Returns an approximation of the intersection of the 2 lines by a FloatPoint. If the lines are
   * parallel the result coordinates will be Integer.MAX_VALUE. Useful in situations where
   * performance is more important than accuracy.
   */
  public FloatPoint intersectionApprox(Line p_other) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    IntPoint otherA = (IntPoint) p_other.a;
    IntPoint otherB = (IntPoint) p_other.b;
    double d1x = thisB.x - thisA.x;
    double d1y = thisB.y - thisA.y;
    double d2x = otherB.x - otherA.x;
    double d2y = otherB.y - otherA.y;
    double det1 = (double) thisA.x * thisB.y - (double) thisA.y * thisB.x;
    double det2 = (double) otherA.x * otherB.y - (double) otherA.y * otherB.x;
    double det = d2x * d1y - d2y * d1x;
    double isX;
    double isY;
    if (det == 0) {
      isX = Integer.MAX_VALUE;
      isY = Integer.MAX_VALUE;
    } else {
      isX = (d2x * det1 - d1x * det2) / det;
      isY = (d2y * det1 - d1y * det2) / det;
    }
    return new FloatPoint(isX, isY);
  }

  /** returns the perpendicular projection of p_point onto this line */
  public Point perpendicularProjection(Point p_point) {
    return p_point.perpendicularProjection(this);
  }

  /**
   * translates the line perpendicular at about p_dist. If p_dist {@literal >} 0, the line will be
   * translated to the left, else to the right
   */
  public Line translate(double p_dist) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntPoint ai = (IntPoint) a;
    IntVector v = (IntVector) direction().getVector();
    double vxvx = (double) v.x * v.x;
    double vyvy = (double) v.y * v.y;
    double length = Math.sqrt(vxvx + vyvy);
    IntPoint newA;
    if (vxvx <= vyvy) {
      // translate along the x axis
      int relX = (int) Math.round((p_dist * length) / v.y);
      newA = new IntPoint(ai.x - relX, ai.y);
    } else {
      // translate along the  y axis
      int relY = (int) Math.round((p_dist * length) / v.x);
      newA = new IntPoint(ai.x, ai.y + relY);
    }
    return Line.getInstance(newA, direction());
  }

  /** translates the line by p_vector */
  public Line translateBy(Vector p_vector) {
    if (p_vector.equals(Vector.ZERO)) {
      return this;
    }
    Point newA = a.translateBy(p_vector);
    Point newB = b.translateBy(p_vector);
    return new Line(newA, newB);
  }

  /** returns true, if the line is axis_parallel */
  public boolean isOrthogonal() {
    return direction().isOrthogonal();
  }

  /** returns true, if this line is diagonal */
  public boolean isDiagonal() {
    return direction().isDiagonal();
  }

  /** returns true, if the direction of this line is a multiple of 45 degree */
  public boolean isMultipleOf45Degree() {
    return direction().isMultipleOf45Degree();
  }

  /** checks, if this Line and p_other are parallel */
  public boolean isParallel(Line p_other) {
    return this.direction().sideOf(p_other.direction()) == Side.COLLINEAR;
  }

  /** checks, if this Line and p_other are perpendicular */
  public boolean isPerpendicular(Line p_other) {
    Vector v1 = direction().getVector();
    Vector v2 = p_other.direction().getVector();
    return v1.projection(v2) == Signum.ZERO;
  }

  /** returns true, if this and p_ob define the same line */
  public boolean isEqualOrOpposite(Line p_other) {

    return sideOf(p_other.a) == Side.COLLINEAR && sideOf(p_other.b) == Side.COLLINEAR;
  }

  /** calculates the cosinus of the angle between this line and p_other */
  public double cosAngle(Line p_other) {
    Vector v1 = b.differenceBy(a);
    Vector v2 = p_other.b.differenceBy(p_other.a);
    return v1.cosAngle(v2);
  }

  /**
   * A line l_1 is defined bigger than a line l_2, if the direction of l_1 is bigger than the
   * direction of l_2. Implements the comparable interface. Throws a cast exception, if p_other is
   * not a Line. Fast implementation only for lines consisting of IntPoints because of critical
   * performance
   */
  @Override
  public int compareTo(Line p_other) {
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    IntPoint otherA = (IntPoint) p_other.a;
    IntPoint otherB = (IntPoint) p_other.b;
    int dx1 = thisB.x - thisA.x;
    int dy1 = thisB.y - thisA.y;
    int dx2 = otherB.x - otherA.x;
    int dy2 = otherB.y - otherA.y;
    if (dy1 > 0) {
      if (dy2 < 0) {
        return -1;
      }
      if (dy2 == 0) {
        if (dx2 > 0) {
          return 1;
        }
        return -1;
      }
    } else if (dy1 < 0) {
      if (dy2 >= 0) {
        return 1;
      }
    } else // dy1 == 0
    {
      if (dx1 > 0) {
        if (dy2 != 0 || dx2 < 0) {
          return -1;
        }
        return 0;
      }
      // dx1 < 0
      if (dy2 > 0 || dy2 == 0 && dx2 > 0) {
        return 1;
      }
      if (dy2 < 0) {
        return -1;
      }
      return 0;
    }

    // now this direction and p_other are located in the same
    // open horizontal half plane

    double determinant = (double) dx2 * dy1 - (double) dy2 * dx1;
    return Signum.asInt(determinant);
  }

  /**
   * Calculates an approximation of the function value of this line at p_x, if the line is not
   * vertical.
   */
  public double functionValueApprox(double p_x) {
    FloatPoint p1 = a.toFloat();
    FloatPoint p2 = b.toFloat();
    double dx = p2.x - p1.x;
    if (dx == 0) {
      FRLogger.warn("function_value_approx: line is vertical");
      return 0;
    }
    double dy = p2.y - p1.y;
    double det = p1.x * p2.y - p2.x * p1.y;
    return (dy * p_x - det) / dx;
  }

  /**
   * Calculates an approximation of the function value in y of this line at p_y, if the line is not
   * horizontal.
   */
  public double functionInYValueApprox(double p_y) {
    FloatPoint p1 = a.toFloat();
    FloatPoint p2 = b.toFloat();
    double dy = p2.y - p1.y;
    if (dy == 0) {
      FRLogger.warn("function_in_y_value_approx: line is horizontal");
      return 0;
    }
    double dx = p2.x - p1.x;
    double det = p1.x * p2.y - p2.x * p1.y;
    return (dx * p_y + det) / dy;
  }

  /**
   * Calculates the direction from p_from_point to the nearest point on this line to p_fro_point.
   * Returns null, if p_from_point is contained in this line.
   */
  public Direction perpendicularDirection(Point p_from_point) {
    Side lineSide = this.sideOf(p_from_point);
    if (lineSide == Side.COLLINEAR) {
      return null;
    }
    Direction dir1 = this.direction().turn45Degree(2);
    Direction dir2 = this.direction().turn45Degree(6);

    Point checkPoint1 = p_from_point.translateBy(dir1.getVector());
    if (this.sideOf(checkPoint1) != lineSide) {
      return dir1;
    }
    Point checkPoint2 = p_from_point.translateBy(dir2.getVector());
    if (this.sideOf(checkPoint2) != lineSide) {
      return dir2;
    }
    FloatPoint nearestLinePoint = p_from_point.toFloat().projectionApprox(this);
    Direction result;
    if (nearestLinePoint.distanceSquare(checkPoint1.toFloat())
        <= nearestLinePoint.distanceSquare(checkPoint2.toFloat())) {
      result = dir1;
    } else {
      result = dir2;
    }
    return result;
  }

  /** Turns this line by p_factor times 90 degree around p_pole. */
  public Line turn90Degree(int p_factor, IntPoint p_pole) {
    Point newA = a.turn90Degree(p_factor, p_pole);
    Point newB = b.turn90Degree(p_factor, p_pole);
    return new Line(newA, newB);
  }

  /** Mirrors this line at the vertical line through p_pole */
  public Line mirrorVertical(IntPoint p_pole) {
    Point newA = b.mirrorVertical(p_pole);
    Point newB = a.mirrorVertical(p_pole);
    return new Line(newA, newB);
  }

  /** Mirrors this line at the horizontal line through p_pole */
  public Line mirrorHorizontal(IntPoint p_pole) {
    Point newA = b.mirrorHorizontal(p_pole);
    Point newB = a.mirrorHorizontal(p_pole);
    return new Line(newA, newB);
  }

  public float length() {
    IntPoint ipa = (IntPoint) a;
    IntPoint ipb = (IntPoint) b;

    return (float) Math.sqrt((ipb.x - ipa.x) * (ipb.x - ipa.x) + (ipb.y - ipa.y) * (ipb.y - ipa.y));
  }
}

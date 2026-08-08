package app.freerouting.geometry.planar;

import java.io.Serializable;
import java.math.BigInteger;

/** Abstract class describing functionality for Points in the plane. */
public abstract class Point implements Serializable {

  /** Standard implementation of the zero point . */
  public static final IntPoint ZERO = new IntPoint(0, 0);

  /**
   * creates an IntPoint from p_x and p_y. If p_x or p_y is too big for an IntPoint, a RationalPoint
   * is created.
   */
  public static Point getInstance(int p_x, int p_y) {
    IntPoint result = new IntPoint(p_x, p_y);
    if (Math.abs(p_x) > Limits.CRIT_INT || Math.abs(p_y) > Limits.CRIT_INT) {
      return new RationalPoint(result);
    }
    return result;
  }

  /** factory method for creating a Point from 3 BigIntegers */
  public static Point getInstance(BigInteger p_x, BigInteger p_y, BigInteger p_z) {
    if (p_z.signum() < 0) {
      // the dominator z of a RationalPoint is expected to be positive
      p_x = p_x.negate();
      p_y = p_y.negate();
      p_z = p_z.negate();
    }
    if (p_x.mod(p_z).signum() == 0) {
      // p_x and p_y can be divided by p_z
      p_x = p_x.divide(p_z);
      p_y = p_y.divide(p_z);
      p_z = BigInteger.ONE;
    }
    if (p_z.equals(BigInteger.ONE)) {
      if (p_x.abs().compareTo(Limits.CRIT_INT_BIG) <= 0
          && p_y.abs().compareTo(Limits.CRIT_INT_BIG) <= 0) {
        // the Point fits into an IntPoint
        return new IntPoint(p_x.intValue(), p_y.intValue());
      }
    }
    return new RationalPoint(p_x, p_y, p_z);
  }

  /** returns the translation of this point by p_vector */
  public abstract Point translateBy(Vector p_vector);

  /** returns the difference vector of this point and p_other */
  public abstract Vector differenceBy(Point p_other);

  /** approximates the coordinates of this point by float coordinates */
  public abstract FloatPoint toFloat();

  /** Returns a unique ID for this point for deterministic tie-breaking. */
  public abstract int getIdNo();

  /** returns true, if this Point is a RationalPoint with denominator z = 0. */
  public abstract boolean isInfinite();

  /** creates the smallest Box with integer coordinates containing this point. */
  public abstract IntBox surroundingBox();

  /** creates the smallest Octagon with integer coordinates containing this point. */
  public abstract IntOctagon surroundingOctagon();

  /** Returns true, if this point lies in the interior or on the border of p_box. */
  public abstract boolean isContainedIn(IntBox p_box);

  public abstract Side sideOf(Line p_line);

  /** returns the nearest point to this point on p_line */
  public abstract Point perpendicularProjection(Line p_line);

  /**
   * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to
   * p_2; Side.ON_THE_RIGHT, if this Point is on the right of the line from p_1 to p_2; and
   * Side.COLLINEAR, if this Point is collinear with p_1 and p_2.
   */
  public Side sideOf(Point p_1, Point p_2) {
    Vector v1 = differenceBy(p_1);
    Vector v2 = p_2.differenceBy(p_1);
    return v1.sideOf(v2);
  }

  /**
   * Calculates the perpendicular direction from this point to p_line. Returns Direction. NULL, if
   * this point lies on p_line.
   */
  public Direction perpendicularDirection(Line p_line) {
    Side side = this.sideOf(p_line);
    if (side == Side.COLLINEAR) {
      return Direction.NULL;
    }
    Direction result;
    if (side == Side.ON_THE_RIGHT) {
      result = p_line.direction().turn45Degree(2);
    } else {
      result = p_line.direction().turn45Degree(6);
    }
    return result;
  }

  /**
   * Returns 1, if this Point has a strict bigger x coordinate than p_other, 0, if the x coordinates
   * are equal, and -1 otherwise.
   */
  public abstract int compareX(Point p_other);

  /**
   * Returns 1, if this Point has a strict bigger y coordinate than p_other, 0, if the y coordinates
   * are equal, and -1 otherwise.
   */
  public abstract int compareY(Point p_other);

  /**
   * The function returns compare_x (p_other), if the result is not 0. Otherwise, it returns
   * compare_y (p_other).
   */
  public int compareXY(Point p_other) {
    int result = compareX(p_other);
    if (result == 0) {
      result = compareY(p_other);
    }
    return result;
  }

  /** Turns this point by p_factor times 90 degree around p_pole. */
  public Point turn90Degree(int p_factor, Point p_pole) {
    Vector v = this.differenceBy(p_pole);
    v = v.turn90Degree(p_factor);
    return p_pole.translateBy(v);
  }

  /** Mirrors this point at the vertical line through p_pole. */
  public Point mirrorVertical(Point p_pole) {
    Vector v = this.differenceBy(p_pole);
    v = v.mirrorAtYAxis();
    return p_pole.translateBy(v);
  }

  /** Mirrors this point at the horizontal line through p_pole. */
  public Point mirrorHorizontal(Point p_pole) {
    Vector v = this.differenceBy(p_pole);
    v = v.mirrorAtXAxis();
    return p_pole.translateBy(v);
  }

  // auxiliary functions needed because the virtual function mechanism
  // does not work in parameter position

  abstract Point translateBy(IntVector p_vector);

  abstract Point translateBy(RationalVector p_vector);

  abstract Vector differenceBy(IntPoint p_other);

  abstract Vector differenceBy(RationalPoint p_other);

  abstract int compareX(IntPoint p_other);

  abstract int compareX(RationalPoint p_other);

  abstract int compareY(IntPoint p_other);

  abstract int compareY(RationalPoint p_other);
}

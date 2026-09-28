package app.freerouting.geometry.planar;

import java.io.Serializable;
import java.math.BigInteger;

/** Abstract class describing functionality for Points in the plane. */
public abstract sealed class Point implements Serializable permits IntPoint, RationalPoint {

  /** Standard implementation of the zero point. */
  public static final IntPoint ZERO = new IntPoint(0, 0);

  /**
   * Creates an IntPoint from x and y. If x or y is too big for an IntPoint, a RationalPoint is
   * created.
   */
  public static Point getInstance(int x, int y) {
    IntPoint result = new IntPoint(x, y);
    if (Math.abs(x) > Limits.CRIT_INT || Math.abs(y) > Limits.CRIT_INT) {
      return new RationalPoint(result);
    }
    return result;
  }

  /** Factory method for creating a Point from 3 BigIntegers. */
  public static Point getInstance(BigInteger x, BigInteger y, BigInteger z) {
    if (z.signum() < 0) {
      // the dominator z of a RationalPoint is expected to be positive
      x = x.negate();
      y = y.negate();
      z = z.negate();
    }
    if (x.mod(z).signum() == 0) {
      // x and y can be divided by z
      x = x.divide(z);
      y = y.divide(z);
      z = BigInteger.ONE;
    }
    if (z.equals(BigInteger.ONE)) {
      if (x.abs().compareTo(Limits.CRIT_INT_BIG) <= 0
          && y.abs().compareTo(Limits.CRIT_INT_BIG) <= 0) {
        // the Point fits into an IntPoint
        return new IntPoint(x.intValue(), y.intValue());
      }
    }
    return new RationalPoint(x, y, z);
  }

  /** Returns the translation of this point by vector. */
  public abstract Point translateBy(Vector vector);

  abstract Point translateBy(IntVector vector);

  abstract Point translateBy(RationalVector vector);

  /** Returns the difference vector of this point and other. */
  public abstract Vector differenceBy(Point other);

  abstract Vector differenceBy(IntPoint other);

  abstract Vector differenceBy(RationalPoint other);

  /** Approximates the coordinates of this point by float coordinates. */
  public abstract FloatPoint toFloat();

  /** Returns a unique ID for this point for deterministic tie-breaking. */
  public abstract int getId();

  /** Returns true, if this Point is a RationalPoint with denominator z = 0. */
  public abstract boolean isInfinite();

  /** Creates the smallest Box with integer coordinates containing this point. */
  public abstract IntBox surroundingBox();

  /** Creates the smallest Octagon with integer coordinates containing this point. */
  public abstract IntOctagon surroundingOctagon();

  /** Returns true, if this point lies in the interior or on the border of box. */
  public abstract boolean isContainedIn(IntBox box);

  /** Returns the side of a line on which this point lies. */
  public abstract Side sideOf(Line line);

  /**
   * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from 1 to 2;
   * Side.ON_THE_RIGHT, if this Point is on the right of the line from 1 to 2; and Side.COLLINEAR,
   * if this Point is collinear with 1 and 2.
   */
  public Side sideOf(Point p1, Point p2) {
    Vector v1 = differenceBy(p1);
    Vector v2 = p2.differenceBy(p1);
    return v1.sideOf(v2);
  }

  /** Returns the nearest point to this point on line. */
  public abstract Point perpendicularProjection(Line line);

  /**
   * Calculates the perpendicular direction from this point to line. Returns Direction. NULL, if
   * this point lies on line.
   */
  public Direction perpendicularDirection(Line line) {
    Side side = this.sideOf(line);
    if (side == Side.COLLINEAR) {
      return Direction.NULL;
    }
    Direction result;
    if (side == Side.ON_THE_RIGHT) {
      result = line.direction().turn45Degree(2);
    } else {
      result = line.direction().turn45Degree(6);
    }
    return result;
  }

  /**
   * Returns 1, if this Point has a strict bigger x coordinate than other, 0, if the x coordinates
   * are equal, and -1 otherwise.
   */
  public abstract int compareX(Point other);

  abstract int compareX(IntPoint other);

  abstract int compareX(RationalPoint other);

  /**
   * Returns 1, if this Point has a strict bigger y coordinate than other, 0, if the y coordinates
   * are equal, and -1 otherwise.
   */
  public abstract int compareY(Point other);

  abstract int compareY(IntPoint other);

  abstract int compareY(RationalPoint other);

  /**
   * The function returns compare_x (other), if the result is not 0. Otherwise, it returns compare_y
   * (other).
   */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public int compareXY(Point other) {
    int result = compareX(other);
    if (result == 0) {
      result = compareY(other);
    }
    return result;
  }

  /** Turns this point by factor times 90 degree around pole. */
  public Point turn90Degree(int factor, Point pole) {
    Vector v = this.differenceBy(pole);
    v = v.turn90Degree(factor);
    return pole.translateBy(v);
  }

  /** Mirrors this point at the vertical line through pole. */
  public Point mirrorVertical(Point pole) {
    Vector v = this.differenceBy(pole);
    v = v.mirrorAtYAxis();
    return pole.translateBy(v);
  }

  /** Mirrors this point at the horizontal line through pole. */
  public Point mirrorHorizontal(Point pole) {
    Vector v = this.differenceBy(pole);
    v = v.mirrorAtXAxis();
    return pole.translateBy(v);
  }
}

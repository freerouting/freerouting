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
  public static Point getInstance(int pX, int pY) {
    IntPoint result = new IntPoint(pX, pY);
    if (Math.abs(pX) > Limits.CRIT_INT || Math.abs(pY) > Limits.CRIT_INT) {
      return new RationalPoint(result);
    }
    return result;
  }

  /** factory method for creating a Point from 3 BigIntegers */
  public static Point getInstance(BigInteger pX, BigInteger pY, BigInteger pZ) {
    if (pZ.signum() < 0) {
      // the dominator z of a RationalPoint is expected to be positive
      pX = pX.negate();
      pY = pY.negate();
      pZ = pZ.negate();
    }
    if (pX.mod(pZ).signum() == 0) {
      // p_x and p_y can be divided by p_z
      pX = pX.divide(pZ);
      pY = pY.divide(pZ);
      pZ = BigInteger.ONE;
    }
    if (pZ.equals(BigInteger.ONE)) {
      if (pX.abs().compareTo(Limits.CRIT_INT_BIG) <= 0
          && pY.abs().compareTo(Limits.CRIT_INT_BIG) <= 0) {
        // the Point fits into an IntPoint
        return new IntPoint(pX.intValue(), pY.intValue());
      }
    }
    return new RationalPoint(pX, pY, pZ);
  }

  /** returns the translation of this point by p_vector */
  public abstract Point translateBy(Vector pVector);

  /** returns the difference vector of this point and p_other */
  public abstract Vector differenceBy(Point pOther);

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
  public abstract boolean isContainedIn(IntBox pBox);

  public abstract Side sideOf(Line pLine);

  /** returns the nearest point to this point on p_line */
  public abstract Point perpendicularProjection(Line pLine);

  /**
   * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to
   * p_2; Side.ON_THE_RIGHT, if this Point is on the right of the line from p_1 to p_2; and
   * Side.COLLINEAR, if this Point is collinear with p_1 and p_2.
   */
  public Side sideOf(Point p1, Point p2) {
    Vector v1 = differenceBy(p1);
    Vector v2 = p2.differenceBy(p1);
    return v1.sideOf(v2);
  }

  /**
   * Calculates the perpendicular direction from this point to p_line. Returns Direction. NULL, if
   * this point lies on p_line.
   */
  public Direction perpendicularDirection(Line pLine) {
    Side side = this.sideOf(pLine);
    if (side == Side.COLLINEAR) {
      return Direction.NULL;
    }
    Direction result;
    if (side == Side.ON_THE_RIGHT) {
      result = pLine.direction().turn45Degree(2);
    } else {
      result = pLine.direction().turn45Degree(6);
    }
    return result;
  }

  /**
   * Returns 1, if this Point has a strict bigger x coordinate than p_other, 0, if the x coordinates
   * are equal, and -1 otherwise.
   */
  public abstract int compareX(Point pOther);

  /**
   * Returns 1, if this Point has a strict bigger y coordinate than p_other, 0, if the y coordinates
   * are equal, and -1 otherwise.
   */
  public abstract int compareY(Point pOther);

  /**
   * The function returns compare_x (p_other), if the result is not 0. Otherwise, it returns
   * compare_y (p_other).
   */
  public int compareXY(Point pOther) {
    int result = compareX(pOther);
    if (result == 0) {
      result = compareY(pOther);
    }
    return result;
  }

  /** Turns this point by p_factor times 90 degree around p_pole. */
  public Point turn90Degree(int pFactor, Point pPole) {
    Vector v = this.differenceBy(pPole);
    v = v.turn90Degree(pFactor);
    return pPole.translateBy(v);
  }

  /** Mirrors this point at the vertical line through p_pole. */
  public Point mirrorVertical(Point pPole) {
    Vector v = this.differenceBy(pPole);
    v = v.mirrorAtYAxis();
    return pPole.translateBy(v);
  }

  /** Mirrors this point at the horizontal line through p_pole. */
  public Point mirrorHorizontal(Point pPole) {
    Vector v = this.differenceBy(pPole);
    v = v.mirrorAtXAxis();
    return pPole.translateBy(v);
  }

  // auxiliary functions needed because the virtual function mechanism
  // does not work in parameter position

  abstract Point translateBy(IntVector pVector);

  abstract Point translateBy(RationalVector pVector);

  abstract Vector differenceBy(IntPoint pOther);

  abstract Vector differenceBy(RationalPoint pOther);

  abstract int compareX(IntPoint pOther);

  abstract int compareX(RationalPoint pOther);

  abstract int compareY(IntPoint pOther);

  abstract int compareY(RationalPoint pOther);
}

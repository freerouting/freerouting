package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * Abstract class describing functionality of Vectors. Vectors are used for translating Points in
 * the plane.
 */
public abstract class Vector implements Serializable {

  /** Standard implementation of the zero vector . */
  public static final IntVector ZERO = new IntVector(0, 0);

  /** Creates a Vector (p_x, p_y) in the plane. */
  public static Vector getInstance(int pX, int pY) {
    IntVector result = new IntVector(pX, pY);
    if (Math.abs(pX) > Limits.CRIT_INT || Math.abs(pY) > Limits.CRIT_INT) {
      return new RationalVector(result);
    }
    return result;
  }

  /**
   * Creates a 2-dimensional Vector from the 3 input values. If p_z != 0 it correspondents to the
   * Vector in the plane with rational number coordinates (p_x / p_z, p_y / p_z).
   */
  public static Vector getInstance(BigInteger pX, BigInteger pY, BigInteger pZ) {
    if (pZ.signum() < 0) {
      // the dominator z of a RationalVector is expected to be positive
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
        return new IntVector(pX.intValue(), pY.intValue());
      }
    }
    return new RationalVector(pX, pY, pZ);
  }

  /** returns true, if this vector is equal to the zero vector. */
  public abstract boolean isZero();

  /** returns the Vector such that this plus this.negate() is zero */
  public abstract Vector negate();

  /** adds p_other to this vector */
  public abstract Vector add(Vector pOther);

  /**
   * Let L be the line from the Zero Vector to p_other. The function returns Side.ON_THE_LEFT, if
   * this Vector is on the left of L Side.ON_THE_RIGHT, if this Vector is on the right of L and
   * Side.COLLINEAR, if this Vector is collinear with L.
   */
  public abstract Side sideOf(Vector pOther);

  /** returns true, if the vector is horizontal or vertical */
  public abstract boolean isOrthogonal();

  /** returns true, if the vector is diagonal */
  public abstract boolean isDiagonal();

  /** Returns true, if the vector is orthogonal or diagonal */
  public boolean isMultipleOf45Degree() {
    return isOrthogonal() || isDiagonal();
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other
   * {@literal >} 0, Signum.NEGATIVE, if the scalar product Vector is {@literal <} 0, and
   * Signum.ZERO, if the scalar product is equal 0.
   */
  public abstract Signum projection(Vector pOther);

  /** Returns an approximation of the scalar product of this vector with p_other by a double. */
  public abstract double scalarProduct(Vector pOther);

  /** approximates the coordinates of this vector by float coordinates */
  public abstract FloatPoint toFloat();

  /** Turns this vector by p_factor times 90 degree. */
  public abstract Vector turn90Degree(int pFactor);

  /** Mirrors this vector at the x axis. */
  public abstract Vector mirrorAtXAxis();

  /** Mirrors this vector at the y axis. */
  public abstract Vector mirrorAtYAxis();

  /** returns an approximation of the Euclidean length of this vector */
  public double lengthApprox() {
    return this.toFloat().size();
  }

  /**
   * Returns an approximation of the cosinus of the angle between this vector and p_other by a
   * double.
   */
  public double cosAngle(Vector pOther) {
    double result = this.scalarProduct(pOther);
    result /= this.toFloat().size() * pOther.toFloat().size();
    return result;
  }

  /** Returns an approximation of the signed angle between this vector and p_other. */
  public double angleApprox(Vector pOther) {
    double result = Math.acos(cosAngle(pOther));
    if (this.sideOf(pOther) == Side.ON_THE_LEFT) {
      result = -result;
    }
    return result;
  }

  /** Returns an approximation of the signed angle between this vector and the x axis. */
  public double angleApprox() {
    Vector other = new IntVector(1, 0);
    return other.angleApprox(this);
  }

  /** Returns an approximation vector of this vector with the same direction and length p_length. */
  public abstract Vector changeLengthApprox(double pLength);

  abstract Direction toNormalizedDirection();

  // auxiliary functions needed because the virtual function mechanism
  // does not work in parameter position

  abstract Vector add(IntVector pOther);

  abstract Vector add(RationalVector pOther);

  abstract Point addTo(IntPoint pPoint);

  abstract Point addTo(RationalPoint pPoint);

  abstract Side sideOf(IntVector pOther);

  abstract Side sideOf(RationalVector pOther);

  abstract Signum projection(IntVector pOther);

  abstract Signum projection(RationalVector pOther);

  abstract double scalarProduct(IntVector pOther);

  abstract double scalarProduct(RationalVector pOther);
}

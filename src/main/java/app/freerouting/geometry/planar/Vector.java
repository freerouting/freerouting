package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * Abstract class describing functionality of Vectors. Vectors are used for translating Points in
 * the plane.
 */
public abstract class Vector implements Serializable {

  /** Standard implementation of the zero vector. */
  public static final IntVector ZERO = new IntVector(0, 0);

  /** Creates a Vector (x, y) in the plane. */
  public static Vector getInstance(int x, int y) {
    IntVector result = new IntVector(x, y);
    if (Math.abs(x) > Limits.CRIT_INT || Math.abs(y) > Limits.CRIT_INT) {
      return new RationalVector(result);
    }
    return result;
  }

  /**
   * Creates a 2-dimensional Vector from the 3 input values. If z != 0 it correspondents to the
   * Vector in the plane with rational number coordinates (x / z, y / z).
   */
  public static Vector getInstance(BigInteger x, BigInteger y, BigInteger z) {
    if (z.signum() < 0) {
      // the dominator z of a RationalVector is expected to be positive
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
        return new IntVector(x.intValue(), y.intValue());
      }
    }
    return new RationalVector(x, y, z);
  }

  /** Returns true, if this vector is equal to the zero vector. */
  public abstract boolean isZero();

  /** Returns the Vector such that this plus this.negate() is zero. */
  public abstract Vector negate();

  /** Adds other to this vector. */
  public abstract Vector add(Vector other);

  abstract Vector add(IntVector other);

  abstract Vector add(RationalVector other);

  /**
   * Let L be the line from the Zero Vector to other. The function returns Side.ON_THE_LEFT, if this
   * Vector is on the left of L Side.ON_THE_RIGHT, if this Vector is on the right of L and
   * Side.COLLINEAR, if this Vector is collinear with L.
   */
  public abstract Side sideOf(Vector other);

  abstract Side sideOf(IntVector other);

  abstract Side sideOf(RationalVector other);

  /** Returns true, if the vector is horizontal or vertical. */
  public abstract boolean isOrthogonal();

  /** Returns true, if the vector is diagonal. */
  public abstract boolean isDiagonal();

  /** Returns true, if the vector is orthogonal or diagonal. */
  public boolean isMultipleOf45Degree() {
    return isOrthogonal() || isDiagonal();
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of this vector and other {@literal
   * >} 0, Signum.NEGATIVE, if the scalar product Vector is {@literal <} 0, and Signum.ZERO, if the
   * scalar product is equal 0.
   */
  public abstract Signum projection(Vector other);

  abstract Signum projection(IntVector other);

  abstract Signum projection(RationalVector other);

  /** Returns an approximation of the scalar product of this vector with other by a double. */
  public abstract double scalarProduct(Vector other);

  abstract double scalarProduct(IntVector other);

  abstract double scalarProduct(RationalVector other);

  /** Approximates the coordinates of this vector by float coordinates. */
  public abstract FloatPoint toFloat();

  /** Turns this vector by factor times 90 degree. */
  public abstract Vector turn90Degree(int factor);

  /** Mirrors this vector at the x axis. */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public abstract Vector mirrorAtXAxis();

  /** Mirrors this vector at the y axis. */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public abstract Vector mirrorAtYAxis();

  /** Returns an approximation of the Euclidean length of this vector. */
  public double lengthApprox() {
    return this.toFloat().size();
  }

  /**
   * Returns an approximation of the cosinus of the angle between this vector and other by a double.
   */
  public double cosAngle(Vector other) {
    double result = this.scalarProduct(other);
    result /= this.toFloat().size() * other.toFloat().size();
    return result;
  }

  /** Returns an approximation of the signed angle between this vector and other. */
  public double angleApprox(Vector other) {
    double result = Math.acos(cosAngle(other));
    if (this.sideOf(other) == Side.ON_THE_LEFT) {
      result = -result;
    }
    return result;
  }

  /** Returns an approximation of the signed angle between this vector and the x axis. */
  public double angleApprox() {
    Vector other = new IntVector(1, 0);
    return other.angleApprox(this);
  }

  /** Returns an approximation vector of this vector with the same direction and length length. */
  public abstract Vector changeLengthApprox(double length);

  abstract Direction toNormalizedDirection();

  abstract Point addTo(IntPoint point);

  abstract Point addTo(RationalPoint point);
}

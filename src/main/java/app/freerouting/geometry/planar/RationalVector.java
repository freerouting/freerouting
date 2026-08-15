package app.freerouting.geometry.planar;

import app.freerouting.datastructures.BigIntAux;
import app.freerouting.datastructures.Signum;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * Analog RationalPoint, but implementing the functionality of a Vector instead of the functionality
 * of a Point.
 */
public class RationalVector extends Vector implements Serializable {

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final BigInteger x;

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final BigInteger y;

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final BigInteger z;

  /**
   * Creates a RationalVector from 3 BigIntegers x, y and z. They represent the 2-dimensional Vector
   * with the rational number Tuple ( x / z , y / z).
   */
  public RationalVector(BigInteger x, BigInteger y, BigInteger z) {
    if (z.signum() >= 0) {
      this.x = x;
      this.y = y;
      this.z = z;

    } else {
      this.x = x.negate();
      this.y = y.negate();
      this.z = z.negate();
    }
  }

  /** Creates a RationalVector from an IntVector. */
  RationalVector(IntVector vector) {
    x = BigInteger.valueOf(vector.x);
    y = BigInteger.valueOf(vector.y);
    z = BigInteger.ONE;
  }

  /** Returns true, if the x and y coordinates of this vector are 0. */
  @Override
  public final boolean isZero() {
    return x.signum() == 0 && y.signum() == 0;
  }

  /** Returns true, if this RationalVector is equal to ob. */
  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null) {
      return false;
    }
    if (getClass() != other.getClass()) {
      return false;
    }
    RationalVector otherVector = (RationalVector) other;
    BigInteger det = BigIntAux.determinant(x, otherVector.x, z, otherVector.z);
    if (det.signum() != 0) {
      return false;
    }
    det = BigIntAux.determinant(y, otherVector.y, z, otherVector.z);
    return det.signum() == 0;
  }

  /** Returns the Vector such that this plus this.minus() is zero. */
  @Override
  public Vector negate() {
    return new RationalVector(x.negate(), y.negate(), z);
  }

  /** Adds other to this vector. */
  @Override
  public final Vector add(Vector other) {
    return other.add(this);
  }

  @Override
  final Vector add(IntVector other) {
    RationalVector vector = new RationalVector(other);
    return add(vector);
  }

  @Override
  final Vector add(RationalVector other) {
    BigInteger[] v1 = new BigInteger[3];
    v1[0] = x;
    v1[1] = y;
    v1[2] = z;

    BigInteger[] v2 = new BigInteger[3];
    v2[0] = other.x;
    v2[1] = other.y;
    v2[2] = other.z;
    BigInteger[] result = BigIntAux.addRationalCoordinates(v1, v2);
    return new RationalVector(result[0], result[1], result[2]);
  }

  /**
   * Let L be the line from the Zero Vector to other. The function returns Side.ON_THE_LEFT, if this
   * Vector is on the left of L Side.ON_THE_RIGHT, if this Vector is on the right of L and
   * Side.COLLINEAR, if this Vector is collinear with L.
   */
  @Override
  public Side sideOf(Vector other) {
    Side tmp = other.sideOf(this);
    return tmp.negate();
  }

  @Override
  Side sideOf(IntVector other) {
    RationalVector vector = new RationalVector(other);
    return sideOf(vector);
  }

  @Override
  Side sideOf(RationalVector other) {
    BigInteger tmp1 = y.multiply(other.x);
    BigInteger tmp2 = x.multiply(other.y);
    BigInteger determinant = tmp1.subtract(tmp2);
    int signum = determinant.signum();
    return Side.of(signum);
  }

  @Override
  public boolean isOrthogonal() {
    return x.signum() == 0 || y.signum() == 0;
  }

  @Override
  public boolean isDiagonal() {
    return x.abs().equals(y.abs());
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of this vector and other {@literal
   * >} 0, Signum.NEGATIVE, if the scalar product is {@literal <} 0, and Signum.ZERO, if the scalar
   * product is equal 0.
   */
  @Override
  public Signum projection(Vector other) {
    return other.projection(this);
  }

  @Override
  Signum projection(IntVector other) {
    Vector vector = new RationalVector(other);
    return vector.projection(this);
  }

  @Override
  Signum projection(RationalVector other) {
    BigInteger tmp1 = x.multiply(other.x);
    BigInteger tmp2 = y.multiply(other.y);
    BigInteger tmp3 = tmp1.add(tmp2);
    int result = tmp3.signum();
    return Signum.of(result);
  }

  /** Calculates the scalar product of this vector and other. */
  @Override
  public double scalarProduct(Vector other) {
    return other.scalarProduct(this);
  }

  @Override
  double scalarProduct(IntVector other) {
    Vector vector = new RationalVector(other);
    return vector.scalarProduct(this);
  }

  @Override
  double scalarProduct(RationalVector other) {
    FloatPoint v1 = toFloat();
    FloatPoint v2 = other.toFloat();
    return v1.x * v2.x + v1.y * v2.y;
  }

  /** Approximates the coordinates of this vector by float coordinates. */
  @Override
  public FloatPoint toFloat() {
    double xd = x.doubleValue();
    double yd = y.doubleValue();
    double zd = z.doubleValue();
    return new FloatPoint(xd / zd, yd / zd);
  }

  @Override
  public Vector changeLengthApprox(double length) {
    FRLogger.warn("RationalVector: change_length_approx not yet implemented");
    return this;
  }

  @Override
  public Vector turn90Degree(int factor) {
    int n = factor;
    while (n < 0) {
      n += 4;
    }
    while (n >= 4) {
      n -= 4;
    }
    BigInteger newX;
    BigInteger newY;
    switch (n) {
      case 0 -> { // 0 degree
        newX = x;
        newY = y;
      }
      case 1 -> { // 90 degree
        newX = y.negate();
        newY = x;
      }
      case 2 -> { // 180 degree
        newX = x.negate();
        newY = y.negate();
      }
      case 3 -> { // 270 degree
        newX = y;
        newY = x.negate();
      }
      default -> {
        return this;
      }
    }
    return new RationalVector(newX, newY, this.z);
  }

  @Override
  public Vector mirrorAtYAxis() {
    return new RationalVector(this.x.negate(), this.y, this.z);
  }

  @Override
  public Vector mirrorAtXAxis() {
    return new RationalVector(this.x, this.y.negate(), this.z);
  }

  @Override
  Direction toNormalizedDirection() {
    BigInteger dx = x;
    BigInteger dy = y;
    BigInteger gcd = dx.gcd(y);
    dx = dx.divide(gcd);
    dy = dy.divide(gcd);
    if (dx.abs().compareTo(Limits.CRIT_INT_BIG) <= 0
        && dy.abs().compareTo(Limits.CRIT_INT_BIG) <= 0) {
      return new IntDirection(dx.intValue(), dy.intValue());
    }
    return new BigIntDirection(dx, dy);
  }

  @Override
  Point addTo(IntPoint point) {
    BigInteger newX = z.multiply(BigInteger.valueOf(point.x));
    newX = newX.add(x);
    BigInteger newY = z.multiply(BigInteger.valueOf(point.y));
    newY = newY.add(y);
    return new RationalPoint(newX, newY, z);
  }

  @Override
  Point addTo(RationalPoint point) {
    BigInteger[] v1 = new BigInteger[3];
    v1[0] = x;
    v1[1] = y;
    v1[2] = z;

    BigInteger[] v2 = new BigInteger[3];
    v2[0] = point.x;
    v2[1] = point.y;
    v2[2] = point.z;

    BigInteger[] result = BigIntAux.addRationalCoordinates(v1, v2);
    return new RationalPoint(result[0], result[1], result[2]);
  }
}

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

  public final BigInteger x;
  public final BigInteger y;
  public final BigInteger z;

  /**
   * creates a RationalVector from 3 BigIntegers p_x, p_y and p_z. They represent the 2-dimensional
   * Vector with the rational number Tuple ( p_x / p_z , p_y / p_z).
   */
  public RationalVector(BigInteger pX, BigInteger pY, BigInteger pZ) {
    if (pZ.signum() >= 0) {
      x = pX;
      y = pY;
      z = pZ;

    } else {
      x = pX.negate();
      y = pY.negate();
      z = pZ.negate();
    }
  }

  /** creates a RationalVector from an IntVector */
  RationalVector(IntVector pVector) {
    x = BigInteger.valueOf(pVector.x);
    y = BigInteger.valueOf(pVector.y);
    z = BigInteger.ONE;
  }

  /** returns true, if the x and y coordinates of this vector are 0 */
  @Override
  public final boolean isZero() {
    return x.signum() == 0 && y.signum() == 0;
  }

  /** returns true, if this RationalVector is equal to p_ob */
  @Override
  public final boolean equals(Object pOb) {
    if (this == pOb) {
      return true;
    }
    if (pOb == null) {
      return false;
    }
    if (getClass() != pOb.getClass()) {
      return false;
    }
    RationalPoint other = (RationalPoint) pOb;
    BigInteger det = BigIntAux.determinant(x, other.x, z, other.z);
    if (det.signum() != 0) {
      return false;
    }
    det = BigIntAux.determinant(y, other.y, z, other.z);

    return det.signum() == 0;
  }

  /** returns the Vector such that this plus this.minus() is zero */
  @Override
  public Vector negate() {
    return new RationalVector(x.negate(), y.negate(), z);
  }

  /** adds p_other to this vector */
  @Override
  public final Vector add(Vector pOther) {
    return pOther.add(this);
  }

  /**
   * Let L be the line from the Zero Vector to p_other. The function returns Side.ON_THE_LEFT, if
   * this Vector is on the left of L Side.ON_THE_RIGHT, if this Vector is on the right of L and
   * Side.COLLINEAR, if this Vector is collinear with L.
   */
  @Override
  public Side sideOf(Vector pOther) {
    Side tmp = pOther.sideOf(this);
    return tmp.negate();
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
   * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other
   * {@literal >} 0, Signum.NEGATIVE, if the scalar product is {@literal <} 0, and Signum.ZERO, if
   * the scalar product is equal 0.
   */
  @Override
  public Signum projection(Vector pOther) {
    return pOther.projection(this);
  }

  /** calculates the scalar product of this vector and p_other */
  @Override
  public double scalarProduct(Vector pOther) {
    return pOther.scalarProduct(this);
  }

  /** approximates the coordinates of this vector by float coordinates */
  @Override
  public FloatPoint toFloat() {
    double xd = x.doubleValue();
    double yd = y.doubleValue();
    double zd = z.doubleValue();
    return new FloatPoint(xd / zd, yd / zd);
  }

  @Override
  public Vector changeLengthApprox(double pLength) {
    FRLogger.warn("RationalVector: change_length_approx not yet implemented");
    return this;
  }

  @Override
  public Vector turn90Degree(int pFactor) {
    int n = pFactor;
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
  double scalarProduct(IntVector pOther) {
    Vector other = new RationalVector(pOther);
    return other.scalarProduct(this);
  }

  @Override
  double scalarProduct(RationalVector pOther) {
    FloatPoint v1 = toFloat();
    FloatPoint v2 = pOther.toFloat();
    return v1.x * v2.x + v1.y * v2.y;
  }

  @Override
  Signum projection(IntVector pOther) {
    Vector other = new RationalVector(pOther);
    return other.projection(this);
  }

  @Override
  Signum projection(RationalVector pOther) {
    BigInteger tmp1 = x.multiply(pOther.x);
    BigInteger tmp2 = y.multiply(pOther.y);
    BigInteger tmp3 = tmp1.add(tmp2);
    int result = tmp3.signum();
    return Signum.of(result);
  }

  @Override
  final Vector add(IntVector pOther) {
    RationalVector other = new RationalVector(pOther);
    return add(other);
  }

  @Override
  final Vector add(RationalVector pOther) {
    BigInteger[] v1 = new BigInteger[3];
    v1[0] = x;
    v1[1] = y;
    v1[2] = z;

    BigInteger[] v2 = new BigInteger[3];
    v2[0] = pOther.x;
    v2[1] = pOther.y;
    v2[2] = pOther.z;
    BigInteger[] result = BigIntAux.addRationalCoordinates(v1, v2);
    return new RationalVector(result[0], result[1], result[2]);
  }

  @Override
  Point addTo(IntPoint pPoint) {
    BigInteger newX = z.multiply(BigInteger.valueOf(pPoint.x));
    newX = newX.add(x);
    BigInteger newY = z.multiply(BigInteger.valueOf(pPoint.y));
    newY = newY.add(y);
    return new RationalPoint(newX, newY, z);
  }

  @Override
  Point addTo(RationalPoint pPoint) {
    BigInteger[] v1 = new BigInteger[3];
    v1[0] = x;
    v1[1] = y;
    v1[2] = z;

    BigInteger[] v2 = new BigInteger[3];
    v2[0] = pPoint.x;
    v2[1] = pPoint.y;
    v2[2] = pPoint.z;

    BigInteger[] result = BigIntAux.addRationalCoordinates(v1, v2);
    return new RationalPoint(result[0], result[1], result[2]);
  }

  @Override
  Side sideOf(IntVector pOther) {
    RationalVector other = new RationalVector(pOther);
    return sideOf(other);
  }

  @Override
  Side sideOf(RationalVector pOther) {
    BigInteger tmp1 = y.multiply(pOther.x);
    BigInteger tmp2 = x.multiply(pOther.y);
    BigInteger determinant = tmp1.subtract(tmp2);
    int signum = determinant.signum();
    return Side.of(signum);
  }
}

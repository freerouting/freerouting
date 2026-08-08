package app.freerouting.geometry.planar;

import app.freerouting.datastructures.BigIntAux;
import app.freerouting.datastructures.Signum;
import java.io.Serializable;

/** Implementation of the interface Vector via a tuple of integers */
public class IntVector extends Vector implements Serializable {

  /** the x coordinate of this vector */
  public final int x;

  /** the y coordinate of this vector */
  public final int y;

  /** creates an IntVector from two integer coordinates */
  public IntVector(int pX, int pY) {
    // range check omitted for performance reasons
    x = pX;
    y = pY;
  }

  /** returns true, if this IntVector is equal to p_ob */
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
    IntVector other = (IntVector) pOb;
    return x == other.x && y == other.y;
  }

  /** returns true, if both coordinates of this vector are 0 */
  @Override
  public final boolean isZero() {
    return x == 0 && y == 0;
  }

  /** returns the Vector such that this plus this.minus() is zero */
  @Override
  public Vector negate() {
    return new IntVector(-x, -y);
  }

  @Override
  public boolean isOrthogonal() {
    return x == 0 || y == 0;
  }

  @Override
  public boolean isDiagonal() {
    return Math.abs(x) == Math.abs(y);
  }

  /** Calculates the determinant of the matrix consisting of this Vector and p_other. */
  public final long determinant(IntVector pOther) {
    return (long) x * pOther.y - (long) y * pOther.x;
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
    int newX;
    int newY;
    switch (n) {
      case 0 -> { // 0 degree
        newX = x;
        newY = y;
      }
      case 1 -> { // 90 degree
        newX = -y;
        newY = x;
      }
      case 2 -> { // 180 degree
        newX = -x;
        newY = -y;
      }
      case 3 -> { // 270 degree
        newX = y;
        newY = -x;
      }
      default -> {
        newX = 0;
        newY = 0;
      }
    }
    return new IntVector(newX, newY);
  }

  @Override
  public Vector mirrorAtYAxis() {
    return new IntVector(-this.x, this.y);
  }

  @Override
  public Vector mirrorAtXAxis() {
    return new IntVector(this.x, -this.y);
  }

  /** adds p_other to this vector */
  @Override
  public final Vector add(Vector pOther) {
    return pOther.add(this);
  }

  @Override
  final Vector add(IntVector pOther) {
    return new IntVector(x + pOther.x, y + pOther.y);
  }

  @Override
  final Vector add(RationalVector pOther) {
    return pOther.add(this);
  }

  /** returns the Point, which results from adding this vector to p_point */
  @Override
  final Point addTo(IntPoint pPoint) {
    return new IntPoint(pPoint.x + x, pPoint.y + y);
  }

  @Override
  final Point addTo(RationalPoint pPoint) {
    return pPoint.translateBy(this);
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
  Side sideOf(IntVector pOther) {
    double determinant = (double) pOther.x * y - (double) pOther.y * x;
    return Side.of(determinant);
  }

  @Override
  Side sideOf(RationalVector pOther) {
    Side tmp = pOther.sideOf(this);
    return tmp.negate();
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other
   * {@literal >} 0, Signum.NEGATIVE, if the scalar product Vector is {@literal <} 0, and
   * Signum.ZERO, if the scalar product is equal 0.
   */
  @Override
  public Signum projection(Vector pOther) {
    return pOther.projection(this);
  }

  @Override
  public double scalarProduct(Vector pOther) {
    return pOther.scalarProduct(this);
  }

  /** converts this vector to a PointFloat. */
  @Override
  public FloatPoint toFloat() {
    return new FloatPoint(x, y);
  }

  @Override
  public Vector changeLengthApprox(double pLength) {
    FloatPoint newPoint = this.toFloat().changeSize(pLength);
    return newPoint.round().differenceBy(Point.ZERO);
  }

  @Override
  Direction toNormalizedDirection() {
    int dx = x;
    int dy = y;

    int gcd = BigIntAux.binaryGcd(Math.abs(dx), Math.abs(dy));
    if (gcd > 1) {
      dx /= gcd;
      dy /= gcd;
    }
    return new IntDirection(dx, dy);
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other > 0,
   * Signum.NEGATIVE, if the scalar product Vector is < 0, and Signum.ZERO, if the scalar product is
   * equal 0.
   */
  @Override
  Signum projection(IntVector pOther) {
    double tmp = (double) x * pOther.x + (double) y * pOther.y;
    return Signum.of(tmp);
  }

  @Override
  double scalarProduct(IntVector pOther) {
    return (double) x * pOther.x + (double) y * pOther.y;
  }

  @Override
  double scalarProduct(RationalVector pOther) {
    return pOther.scalarProduct(this);
  }

  @Override
  Signum projection(RationalVector pOther) {
    return pOther.projection(this);
  }
}

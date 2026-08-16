package app.freerouting.geometry.planar;

import app.freerouting.datastructures.BigIntAux;
import app.freerouting.datastructures.Signum;
import java.io.Serializable;

/** Implementation of the interface Vector via a tuple of integers. */
public class IntVector extends Vector implements Serializable {

  /** The x coordinate of this vector. */
  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final int x;

  /** The y coordinate of this vector. */
  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final int y;

  /** Creates an IntVector from two integer coordinates. */
  public IntVector(int x, int y) {
    // range check omitted for performance reasons
    this.x = x;
    this.y = y;
  }

  /** Returns true, if this IntVector is equal to ob. */
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
    IntVector otherVector = (IntVector) other;
    return x == otherVector.x && y == otherVector.y;
  }

  /** Returns true, if both coordinates of this vector are 0. */
  @Override
  public final boolean isZero() {
    return x == 0 && y == 0;
  }

  /** Returns the Vector such that this plus this.minus() is zero. */
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

  /** Calculates the determinant of the matrix consisting of this Vector and other. */
  public final long determinant(IntVector other) {
    return (long) x * other.y - (long) y * other.x;
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
    return switch (n) {
      case 0 -> new IntVector(x, y);
      case 1 -> new IntVector(-y, x);
      case 2 -> new IntVector(-x, -y);
      case 3 -> new IntVector(y, -x);
      default -> IntVector.ZERO;
    };
  }

  @Override
  public Vector mirrorAtYAxis() {
    return new IntVector(-this.x, this.y);
  }

  @Override
  public Vector mirrorAtXAxis() {
    return new IntVector(this.x, -this.y);
  }

  /** Adds other to this vector. */
  @Override
  public final Vector add(Vector other) {
    return other.add(this);
  }

  @Override
  final Vector add(IntVector other) {
    return new IntVector(x + other.x, y + other.y);
  }

  @Override
  final Vector add(RationalVector other) {
    return other.add(this);
  }

  /** Returns the Point, which results from adding this vector to point. */
  @Override
  final Point addTo(IntPoint point) {
    return new IntPoint(point.x + x, point.y + y);
  }

  @Override
  final Point addTo(RationalPoint point) {
    return point.translateBy(this);
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
    double determinant = (double) other.x * y - (double) other.y * x;
    return Side.of(determinant);
  }

  @Override
  Side sideOf(RationalVector other) {
    Side tmp = other.sideOf(this);
    return tmp.negate();
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of this vector and other {@literal
   * >} 0, Signum.NEGATIVE, if the scalar product Vector is {@literal <} 0, and Signum.ZERO, if the
   * scalar product is equal 0.
   */
  @Override
  public Signum projection(Vector other) {
    return other.projection(this);
  }

  @Override
  Signum projection(IntVector other) {
    double tmp = (double) x * other.x + (double) y * other.y;
    return Signum.of(tmp);
  }

  @Override
  Signum projection(RationalVector other) {
    return other.projection(this);
  }

  @Override
  public double scalarProduct(Vector other) {
    return other.scalarProduct(this);
  }

  @Override
  double scalarProduct(IntVector other) {
    return (double) x * other.x + (double) y * other.y;
  }

  @Override
  double scalarProduct(RationalVector other) {
    return other.scalarProduct(this);
  }

  /** Converts this vector to a PointFloat. */
  @Override
  public FloatPoint toFloat() {
    return new FloatPoint(x, y);
  }

  @Override
  public Vector changeLengthApprox(double length) {
    FloatPoint newPoint = this.toFloat().changeSize(length);
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
}

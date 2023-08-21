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
  public IntVector(int p_x, int p_y) {
    // range check ommitet for performance reasons
    x = p_x;
    y = p_y;
  }

  /** returns true, if this IntVector is equal to p_ob */
  @Override
  public final boolean equals(Object p_ob) {
    if (this == p_ob) {
      return true;
    }
    if (p_ob == null) {
      return false;
    }
    if (getClass() != p_ob.getClass()) {
      return false;
    }
    IntVector other = (IntVector) p_ob;
    return (x == other.x && y == other.y);
  }

  /** returns true, if both coordinates of this vector are 0 */
  @Override
  public final boolean is_zero() {
    return x == 0 && y == 0;
  }

  /** returns the Vector such that this plus this.minus() is zero */
  @Override
  public Vector negate() {
    return new IntVector(-x, -y);
  }

  @Override
  public boolean is_orthogonal() {
    return (x == 0 || y == 0);
  }

  @Override
  public boolean is_diagonal() {
    return (Math.abs(x) == Math.abs(y));
  }

  /** Calculates the determinant of the matrix consisting of this Vector and p_other. */
  public final long determinant(IntVector p_other) {
    return (long) x * p_other.y - (long) y * p_other.x;
  }

  @Override
  public Vector turn_90_degree(int p_factor) {
    int n = p_factor;
    while (n < 0) {
      n += 4;
    }
    while (n >= 4) {
      n -= 4;
    }
    int new_x;
    int new_y;
    switch (n) {
      case 0 -> { // 0 degree
        new_x = x;
        new_y = y;
      }
      case 1 -> { // 90 degree
        new_x = -y;
        new_y = x;
      }
      case 2 -> { // 180 degree
        new_x = -x;
        new_y = -y;
      }
      case 3 -> { // 270 degree
        new_x = y;
        new_y = -x;
      }
      default -> {
        new_x = 0;
        new_y = 0;
      }
    }
    return new IntVector(new_x, new_y);
  }

  @Override
  public Vector mirror_at_y_axis() {
    return new IntVector(-this.x, this.y);
  }

  @Override
  public Vector mirror_at_x_axis() {
    return new IntVector(this.x, -this.y);
  }

  /** adds p_other to this vector */
  @Override
  public final Vector add(Vector p_other) {
    return p_other.add(this);
  }

  @Override
  final Vector add(IntVector p_other) {
    return new IntVector(x + p_other.x, y + p_other.y);
  }

  @Override
  final Vector add(RationalVector p_other) {
    return p_other.add(this);
  }

  /** returns the Point, which results from adding this vector to p_point */
  @Override
  final Point add_to(IntPoint p_point) {
    return new IntPoint(p_point.x + x, p_point.y + y);
  }

  @Override
  final Point add_to(RationalPoint p_point) {
    return p_point.translate_by(this);
  }

  /**
   * Let L be the line from the Zero Vector to p_other. The function returns Side.ON_THE_LEFT, if
   * this Vector is on the left of L Side.ON_THE_RIGHT, if this Vector is on the right of L and
   * Side.COLLINEAR, if this Vector is collinear with L.
   */
  @Override
  public Side side_of(Vector p_other) {
    Side tmp = p_other.side_of(this);
    return tmp.negate();
  }

  @Override
  Side side_of(IntVector p_other) {
    double determinant = (double) p_other.x * y - (double) p_other.y * x;
    return Side.of(determinant);
  }

  @Override
  Side side_of(RationalVector p_other) {
    Side tmp = p_other.side_of(this);
    return tmp.negate();
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of this vector and p_other
   * {@literal >} 0, Signum.NEGATIVE, if the scalar product Vector is {@literal <} 0, and
   * Signum.ZERO, if the scalar product is equal 0.
   */
  @Override
  public Signum projection(Vector p_other) {
    return p_other.projection(this);
  }

  @Override
  public double scalar_product(Vector p_other) {
    return p_other.scalar_product(this);
  }

  /** converts this vector to a PointFloat. */
  @Override
  public FloatPoint to_float() {
    return new FloatPoint(x, y);
  }

  @Override
  public Vector change_length_approx(double p_length) {
    FloatPoint new_point = this.to_float().change_size(p_length);
    return new_point.round().difference_by(Point.ZERO);
  }

  @Override
  Direction to_normalized_direction() {
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
  Signum projection(IntVector p_other) {
    double tmp = (double) x * p_other.x + (double) y * p_other.y;
    return Signum.of(tmp);
  }

  @Override
  double scalar_product(IntVector p_other) {
    return (double) x * p_other.x + (double) y * p_other.y;
  }

  @Override
  double scalar_product(RationalVector p_other) {
    return p_other.scalar_product(this);
  }

  @Override
  Signum projection(RationalVector p_other) {
    return p_other.projection(this);
  }
}

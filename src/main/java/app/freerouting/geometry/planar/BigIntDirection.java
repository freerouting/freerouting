package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.math.BigInteger;

/** Implements the abstract class Direction as a tuple of infinite precision integers. */
public class BigIntDirection extends Direction implements java.io.Serializable {
  final BigInteger x;
  final BigInteger y;

  BigIntDirection(BigInteger p_x, BigInteger p_y) {
    x = p_x;
    y = p_y;
  }

  /** creates a BigIntDirection from an IntDirection */
  BigIntDirection(IntDirection p_dir) {
    x = BigInteger.valueOf(p_dir.x);
    y = BigInteger.valueOf(p_dir.y);
  }

  public boolean is_orthogonal() {
    return (x.signum() == 0 || y.signum() == 0);
  }

  public boolean is_diagonal() {
    return x.abs().equals(y.abs());
  }

  public Vector get_vector() {
    return new RationalVector(x, y, BigInteger.ONE);
  }

  public Direction turn_45_degree(int p_factor) {
    FRLogger.warn("BigIntDirection: turn_45_degree not yet implemented");
    return this;
  }

  public Direction opposite() {
    return new BigIntDirection(x.negate(), y.negate());
  }

  /**
   * Implements the Comparable interface. Returns 1, if this direction has a strict bigger angle
   * with the positive x-axis than p_other_direction, 0, if this direction is equal to
   * p_other_direction, and -1 otherwise. Throws an exception, if p_other_direction is not a
   * Direction.
   */
  public int compareTo(Direction p_other_direction) {
    return -p_other_direction.compareTo(this);
  }

  int compareTo(IntDirection p_other) {
    BigIntDirection other = new BigIntDirection(p_other);
    return compareTo(other);
  }

  int compareTo(BigIntDirection p_other) {
    int x1 = x.signum();
    int y1 = y.signum();
    int x2 = p_other.x.signum();
    int y2 = p_other.y.signum();
    if (y1 > 0) {
      if (y2 < 0) {
        return -1;
      }
      if (y2 == 0) {
        if (x2 > 0) {
          return 1;
        }
        return -1;
      }
    } else if (y1 < 0) {
      if (y2 >= 0) {
        return 1;
      }
    } else // y1 == 0
    {
      if (x1 > 0) {
        if (y2 != 0 || x2 < 0) {
          return -1;
        }
        return 0;
      }
      // x1 < 0
      if (y2 > 0 || y2 == 0 && x2 > 0) {
        return 1;
      }
      if (y2 < 0) {
        return -1;
      }
      return 0;
    }

    // now this direction and p_other are located in the same
    // open horizontal half plane

    BigInteger tmp_1 = y.multiply(p_other.x);
    BigInteger tmp_2 = x.multiply(p_other.y);
    BigInteger determinant = tmp_1.subtract(tmp_2);
    return determinant.signum();
  }
}

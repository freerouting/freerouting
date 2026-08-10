package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.math.BigInteger;

/** Implements the abstract class Direction as a tuple of infinite precision integers. */
public class BigIntDirection extends Direction implements Serializable {

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  final BigInteger x;

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  final BigInteger y;

  BigIntDirection(BigInteger x, BigInteger y) {
    this.x = x;
    this.y = y;
  }

  /** Creates a BigIntDirection from an IntDirection. */
  BigIntDirection(IntDirection dir) {
    x = BigInteger.valueOf(dir.x);
    y = BigInteger.valueOf(dir.y);
  }

  @Override
  public boolean isOrthogonal() {
    return x.signum() == 0 || y.signum() == 0;
  }

  @Override
  public boolean isDiagonal() {
    return x.abs().equals(y.abs());
  }

  @Override
  public Vector getVector() {
    return new RationalVector(x, y, BigInteger.ONE);
  }

  @Override
  public Direction turn45Degree(int factor) {
    FRLogger.warn("BigIntDirection: turn_45_degree not yet implemented");
    return this;
  }

  @Override
  public Direction opposite() {
    return new BigIntDirection(x.negate(), y.negate());
  }

  /**
   * Implements the Comparable interface. Returns 1, if this direction has a strict bigger angle
   * with the positive x-axis than p_other_direction, 0, if this direction is equal to
   * p_other_direction, and -1 otherwise. Throws an exception, if p_other_direction is not a
   * Direction.
   */
  @Override
  public int compareTo(Direction otherDirection) {
    return -otherDirection.compareTo(this);
  }

  @Override
  int compareTo(IntDirection otherDirection) {
    BigIntDirection other = new BigIntDirection(otherDirection);
    return compareTo(other);
  }

  @Override
  int compareTo(BigIntDirection other) {
    int x1 = x.signum();
    int y1 = y.signum();
    int x2 = other.x.signum();
    int y2 = other.y.signum();
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
    } else { // y1 == 0
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

    BigInteger tmp1 = y.multiply(other.x);
    BigInteger tmp2 = x.multiply(other.y);
    BigInteger determinant = tmp1.subtract(tmp2);
    return determinant.signum();
  }
}

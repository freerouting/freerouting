package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;
import java.io.Serializable;

/** Implements an abstract class Direction as an equivalence class of IntVector's. */
public class IntDirection extends Direction implements Serializable {

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final int x;

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final int y;

  IntDirection(int x, int y) {
    this.x = x;
    this.y = y;
  }

  IntDirection(IntVector vector) {
    x = vector.x;
    y = vector.y;
  }

  @Override
  public boolean isOrthogonal() {
    return x == 0 || y == 0;
  }

  @Override
  public boolean isDiagonal() {
    return Math.abs(x) == Math.abs(y);
  }

  @Override
  public Vector getVector() {
    return new IntVector(x, y);
  }

  @Override
  int compareTo(IntDirection other) {
    if (y > 0) {
      if (other.y < 0) {
        return -1;
      }
      if (other.y == 0) {
        if (other.x > 0) {
          return 1;
        }
        return -1;
      }
    } else if (y < 0) {
      if (other.y >= 0) {
        return 1;
      }
    } else { // y == 0
      if (x > 0) {
        if (other.y != 0 || other.x < 0) {
          return -1;
        }
        return 0;
      }
      // x < 0
      if (other.y > 0 || other.y == 0 && other.x > 0) {
        return 1;
      }
      if (other.y < 0) {
        return -1;
      }
      return 0;
    }

    // now this direction and other are located in the same
    // open horizontal half plane

    double determinant = (double) other.x * y - (double) other.y * x;
    return Signum.asInt(determinant);
  }

  /**
   * Implements the Comparable interface. Returns 1, if this direction has a strict bigger angle
   * with the positive x-axis than otherDirection, 0, if this direction is equal to otherDirection,
   * and -1 otherwise. Throws an exception, if otherDirection is not a Direction.
   */
  @Override
  public int compareTo(Direction otherDirection) {
    return -otherDirection.compareTo(this);
  }

  @Override
  int compareTo(BigIntDirection other) {
    return -other.compareTo(this);
  }

  @Override
  public Direction opposite() {
    return new IntDirection(-x, -y);
  }

  @Override
  public Direction turn45Degree(int factor) {
    int n = factor % 8;
    return switch (n) {
      case 0 -> new IntDirection(x, y); // 0 degrees
      case 1 -> new IntDirection(x - y, x + y); // 45 degrees
      case 2 -> new IntDirection(-y, x); // 90 degrees
      case 3 -> new IntDirection(-x - y, x - y); // 135 degrees
      case 4 -> new IntDirection(-x, -y); // 180 degrees
      case 5 -> new IntDirection(y - x, -x - y); // 225 degrees
      case 6 -> new IntDirection(y, -x); // 270 degrees
      case 7 -> new IntDirection(x + y, y - x); // 315 degrees
      default -> new IntDirection(0, 0);
    };
  }

  final double determinant(IntDirection other) {
    return (double) x * other.y - (double) y * other.x;
  }
}

package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;

import java.io.Serializable;

/** Implements an abstract class Direction as an equivalence class of IntVector's. */
public class IntDirection extends Direction implements Serializable {
  public final int x;
  public final int y;

  IntDirection(int p_x, int p_y) {
    x = p_x;
    y = p_y;
  }

  IntDirection(IntVector p_vector) {
    x = p_vector.x;
    y = p_vector.y;
  }

  @Override
  public boolean is_orthogonal() {
    return (x == 0 || y == 0);
  }

  @Override
  public boolean is_diagonal() {
    return (Math.abs(x) == Math.abs(y));
  }

  @Override
  public Vector get_vector() {
    return new IntVector(x, y);
  }

  @Override
  int compareTo(IntDirection p_other) {
    if (y > 0) {
      if (p_other.y < 0) {
        return -1;
      }
      if (p_other.y == 0) {
        if (p_other.x > 0) {
          return 1;
        }
        return -1;
      }
    } else if (y < 0) {
      if (p_other.y >= 0) {
        return 1;
      }
    } else // y == 0
    {
      if (x > 0) {
        if (p_other.y != 0 || p_other.x < 0) {
          return -1;
        }
        return 0;
      }
      // x < 0
      if (p_other.y > 0 || p_other.y == 0 && p_other.x > 0) {
        return 1;
      }
      if (p_other.y < 0) {
        return -1;
      }
      return 0;
    }

    // now this direction and p_other are located in the same
    // open horizontal half plane

    double determinant = (double) p_other.x * y - (double) p_other.y * x;
    return Signum.as_int(determinant);
  }

  @Override
  public Direction opposite() {
    return new IntDirection(-x, -y);
  }

  @Override
  public Direction turn_45_degree(int p_factor) {
    int n = p_factor % 8;
    int new_x;
    int new_y;
    switch (n) {
      case 0 -> { // 0 degree
        new_x = x;
        new_y = y;
      }
      case 1 -> { // 45 degree
        new_x = x - y;
        new_y = x + y;
      }
      case 2 -> { // 90 degree
        new_x = -y;
        new_y = x;
      }
      case 3 -> { // 135 degree
        new_x = -x - y;
        new_y = x - y;
      }
      case 4 -> { // 180 degree
        new_x = -x;
        new_y = -y;
      }
      case 5 -> { // 225 degree
        new_x = y - x;
        new_y = -x - y;
      }
      case 6 -> { // 270 degree
        new_x = y;
        new_y = -x;
      }
      case 7 -> { // 315 degree
        new_x = x + y;
        new_y = y - x;
      }
      default -> {
        new_x = 0;
        new_y = 0;
      }
    }
    return new IntDirection(new_x, new_y);
  }

  /**
   * Implements the Comparable interface. Returns 1, if this direction has a strict bigger angle
   * with the positive x-axis than p_other_direction, 0, if this direction is equal to
   * p_other_direction, and -1 otherwise. Throws an exception, if p_other_direction is not a
   * Direction.
   */
  @Override
  public int compareTo(Direction p_other_direction) {
    return -p_other_direction.compareTo(this);
  }

  @Override
  int compareTo(BigIntDirection p_other) {
    return -(p_other.compareTo(this));
  }

  final double determinant(IntDirection p_other) {
    return (double) x * p_other.y - (double) y * p_other.x;
  }
}

package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;
import java.io.Serializable;

/** Implements an abstract class Direction as an equivalence class of IntVector's. */
public class IntDirection extends Direction implements Serializable {

  public final int x;
  public final int y;

  IntDirection(int pX, int pY) {
    x = pX;
    y = pY;
  }

  IntDirection(IntVector pVector) {
    x = pVector.x;
    y = pVector.y;
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
  int compareTo(IntDirection pOther) {
    if (y > 0) {
      if (pOther.y < 0) {
        return -1;
      }
      if (pOther.y == 0) {
        if (pOther.x > 0) {
          return 1;
        }
        return -1;
      }
    } else if (y < 0) {
      if (pOther.y >= 0) {
        return 1;
      }
    } else // y == 0
    {
      if (x > 0) {
        if (pOther.y != 0 || pOther.x < 0) {
          return -1;
        }
        return 0;
      }
      // x < 0
      if (pOther.y > 0 || pOther.y == 0 && pOther.x > 0) {
        return 1;
      }
      if (pOther.y < 0) {
        return -1;
      }
      return 0;
    }

    // now this direction and p_other are located in the same
    // open horizontal half plane

    double determinant = (double) pOther.x * y - (double) pOther.y * x;
    return Signum.asInt(determinant);
  }

  @Override
  public Direction opposite() {
    return new IntDirection(-x, -y);
  }

  @Override
  public Direction turn45Degree(int pFactor) {
    int n = pFactor % 8;
    int newX;
    int newY;
    switch (n) {
      case 0 -> { // 0 degree
        newX = x;
        newY = y;
      }
      case 1 -> { // 45 degree
        newX = x - y;
        newY = x + y;
      }
      case 2 -> { // 90 degree
        newX = -y;
        newY = x;
      }
      case 3 -> { // 135 degree
        newX = -x - y;
        newY = x - y;
      }
      case 4 -> { // 180 degree
        newX = -x;
        newY = -y;
      }
      case 5 -> { // 225 degree
        newX = y - x;
        newY = -x - y;
      }
      case 6 -> { // 270 degree
        newX = y;
        newY = -x;
      }
      case 7 -> { // 315 degree
        newX = x + y;
        newY = y - x;
      }
      default -> {
        newX = 0;
        newY = 0;
      }
    }
    return new IntDirection(newX, newY);
  }

  /**
   * Implements the Comparable interface. Returns 1, if this direction has a strict bigger angle
   * with the positive x-axis than p_other_direction, 0, if this direction is equal to
   * p_other_direction, and -1 otherwise. Throws an exception, if p_other_direction is not a
   * Direction.
   */
  @Override
  public int compareTo(Direction pOtherDirection) {
    return -pOtherDirection.compareTo(this);
  }

  @Override
  int compareTo(BigIntDirection pOther) {
    return -pOther.compareTo(this);
  }

  final double determinant(IntDirection pOther) {
    return (double) x * pOther.y - (double) y * pOther.x;
  }
}

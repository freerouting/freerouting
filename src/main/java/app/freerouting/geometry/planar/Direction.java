package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;
import java.io.Serializable;

/**
 * Abstract class defining functionality of directions in the plane. A Direction is an equivalence
 * class of vectors. Two vectors define the same object of class Direction, if they point into the
 * same direction. We prefer using directions instead of angles, because with angles the arithmetic
 * calculations are in general not exact.
 */
public abstract class Direction implements Comparable<Direction>, Serializable {

  public static final IntDirection NULL = new IntDirection(0, 0);

  /** The direction to the east. */
  public static final IntDirection RIGHT = new IntDirection(1, 0);

  /** The direction to the northeast. */
  public static final IntDirection RIGHT45 = new IntDirection(1, 1);

  /** The direction to the north. */
  public static final IntDirection UP = new IntDirection(0, 1);

  /** The direction to the northwest. */
  public static final IntDirection UP45 = new IntDirection(-1, 1);

  /** The direction to the west. */
  public static final IntDirection LEFT = new IntDirection(-1, 0);

  /** The direction to the southwest. */
  public static final IntDirection LEFT45 = new IntDirection(-1, -1);

  /** The direction to the south. */
  public static final IntDirection DOWN = new IntDirection(0, -1);

  /** The direction to the southeast. */
  public static final IntDirection DOWN45 = new IntDirection(1, -1);

  /** Creates a Direction from the input Vector. */
  public static Direction getInstance(Vector vector) {
    return vector.toNormalizedDirection();
  }

  /** Calculates the direction from from to to. If from and to are equal, null is returned. */
  public static Direction getInstance(Point from, Point to) {
    if (from.equals(to)) {
      return null;
    }
    return getInstance(to.differenceBy(from));
  }

  /** Creates a Direction whose angle with the x-axis is nearly equal to angle. */
  public static Direction getInstanceApprox(double angle) {
    final double scaleFactor = 10000;
    int x = (int) Math.round(Math.cos(angle) * scaleFactor);
    int y = (int) Math.round(Math.sin(angle) * scaleFactor);
    return getInstance(new IntVector(x, y));
  }

  /** Returns any Vector pointing into this direction. */
  public abstract Vector getVector();

  /** Returns true, if the direction is horizontal or vertical. */
  public abstract boolean isOrthogonal();

  /** Returns true, if the direction is diagonal. */
  public abstract boolean isDiagonal();

  /** Returns true, if the direction is orthogonal or diagonal. */
  public boolean isMultipleOf45Degree() {
    return isOrthogonal() || isDiagonal();
  }

  /** Turns the direction by factor times 45 degree. */
  public abstract Direction turn45Degree(int factor);

  /** Returns the opposite direction of this direction. */
  public abstract Direction opposite();

  /** Returns true, if ob is a Direction and this Direction and ob point into the same direction. */
  @Override
  public final boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    Direction otherDirection = (Direction) other;
    if (this == otherDirection) {
      return true;
    }
    if (other == null) {
      return false;
    }

    if (this.sideOf(otherDirection) != Side.COLLINEAR) {
      return false;
    }
    // check, that dir and other_dir do not point into opposite directions
    Vector thisVector = getVector();
    Vector otherVector = otherDirection.getVector();
    return thisVector.projection(otherVector) == Signum.POSITIVE;
  }

  /**
   * Let L be the line from the Zero Vector to other.get_vector(). The function returns
   * Side.ON_THE_LEFT, if this.get_vector() is on the left of L Side.ON_THE_RIGHT, if
   * this.get_vector() is on the right of L and Side.COLLINEAR, if this.get_vector() is collinear
   * with L.
   */
  public Side sideOf(Direction other) {
    return this.getVector().sideOf(other.getVector());
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of a vector representing this
   * direction and a vector representing other is {@literal >} 0, Signum.NEGATIVE, if the scalar
   * product is {@literal <} 0, and Signum.ZERO, if the scalar product is equal 0.
   */
  public Signum projection(Direction other) {
    return this.getVector().projection(other.getVector());
  }

  /** Calculates an approximation of the direction in the middle of this direction and other. */
  public Direction middleApprox(Direction other) {
    FloatPoint v1 = getVector().toFloat();
    FloatPoint v2 = other.getVector().toFloat();
    double length1 = v1.size();
    double length2 = v2.size();
    double x = v1.x / length1 + v2.x / length2;
    double y = v1.y / length1 + v2.y / length2;
    final double scaleFactor = 1000;
    Vector vm = new IntVector((int) Math.round(x * scaleFactor), (int) Math.round(y * scaleFactor));
    return Direction.getInstance(vm);
  }

  /**
   * Returns 1, if the angle between 1 and this direction is bigger the angle between 2 and this
   * direction, 0, if 1 is equal to 2, * and -1 otherwise.
   */
  public int compareFrom(Direction p1, Direction p2) {
    int result;
    if (p1.compareTo(this) >= 0) {
      if (p2.compareTo(this) >= 0) {
        result = p1.compareTo(p2);
      } else {
        result = -1;
      }
    } else {
      if (p2.compareTo(this) >= 0) {
        result = 1;
      } else {
        result = p1.compareTo(p2);
      }
    }
    return result;
  }

  /** Returns an approximation of the signed angle corresponding to this direction. */
  public double angleApprox() {
    return this.getVector().angleApprox();
  }

  // auxiliary functions needed because the virtual function mechanism
  // does not work in parameter position

  abstract int compareTo(IntDirection other);

  abstract int compareTo(BigIntDirection other);

  @Override
  public String toString() {
    if (this.compareTo(RIGHT) == 0) {
      return "RIGHT";
    } else if (this.compareTo(RIGHT45) == 0) {
      return "UP-RIGHT";
    } else if (this.compareTo(UP) == 0) {
      return "UP";
    } else if (this.compareTo(UP45) == 0) {
      return "UP-LEFT";
    } else if (this.compareTo(LEFT) == 0) {
      return "LEFT";
    } else if (this.compareTo(LEFT45) == 0) {
      return "DOWN-LEFT";
    } else if (this.compareTo(DOWN) == 0) {
      return "DOWN";
    } else if (this.compareTo(DOWN45) == 0) {
      return "DOWN-RIGHT";
    } else if (this.compareTo(NULL) == 0) {
      return "NULL";
    } else {
      return "UNKNOWN";
    }
  }
}

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

  /** the direction to the east */
  public static final IntDirection RIGHT = new IntDirection(1, 0);

  /** the direction to the northeast */
  public static final IntDirection RIGHT45 = new IntDirection(1, 1);

  /** the direction to the north */
  public static final IntDirection UP = new IntDirection(0, 1);

  /** the direction to the northwest */
  public static final IntDirection UP45 = new IntDirection(-1, 1);

  /** the direction to the west */
  public static final IntDirection LEFT = new IntDirection(-1, 0);

  /** the direction to the southwest */
  public static final IntDirection LEFT45 = new IntDirection(-1, -1);

  /** the direction to the south */
  public static final IntDirection DOWN = new IntDirection(0, -1);

  /** the direction to the southeast */
  public static final IntDirection DOWN45 = new IntDirection(1, -1);

  /** creates a Direction from the input Vector */
  public static Direction getInstance(Vector pVector) {
    return pVector.toNormalizedDirection();
  }

  /**
   * Calculates the direction from p_from to p_to. If p_from and p_to are equal, null is returned.
   */
  public static Direction getInstance(Point pFrom, Point pTo) {
    if (pFrom.equals(pTo)) {
      return null;
    }
    return getInstance(pTo.differenceBy(pFrom));
  }

  /** Creates a Direction whose angle with the x-axis is nearly equal to p_angle */
  public static Direction getInstanceApprox(double pAngle) {
    final double scaleFactor = 10000;
    int x = (int) Math.round(Math.cos(pAngle) * scaleFactor);
    int y = (int) Math.round(Math.sin(pAngle) * scaleFactor);
    return getInstance(new IntVector(x, y));
  }

  /** return any Vector pointing into this direction */
  public abstract Vector getVector();

  /** returns true, if the direction is horizontal or vertical */
  public abstract boolean isOrthogonal();

  /** returns true, if the direction is diagonal */
  public abstract boolean isDiagonal();

  /** returns true, if the direction is orthogonal or diagonal */
  public boolean isMultipleOf45Degree() {
    return isOrthogonal() || isDiagonal();
  }

  /** turns the direction by p_factor times 45 degree */
  public abstract Direction turn45Degree(int pFactor);

  /** returns the opposite direction of this direction */
  public abstract Direction opposite();

  /**
   * Returns true, if p_ob is a Direction and this Direction and p_ob point into the same direction
   */
  @Override
  public final boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Direction pOther = (Direction) obj;
    if (this == pOther) {
      return true;
    }
    if (pOther == null) {
      return false;
    }

    if (this.sideOf(pOther) != Side.COLLINEAR) {
      return false;
    }
    // check, that dir and other_dir do not point into opposite directions
    Vector thisVector = getVector();
    Vector otherVector = pOther.getVector();
    return thisVector.projection(otherVector) == Signum.POSITIVE;
  }

  /**
   * Let L be the line from the Zero Vector to p_other.get_vector(). The function returns
   * Side.ON_THE_LEFT, if this.get_vector() is on the left of L Side.ON_THE_RIGHT, if
   * this.get_vector() is on the right of L and Side.COLLINEAR, if this.get_vector() is collinear
   * with L.
   */
  public Side sideOf(Direction pOther) {
    return this.getVector().sideOf(pOther.getVector());
  }

  /**
   * The function returns Signum.POSITIVE, if the scalar product of a vector representing this
   * direction and a vector representing p_other is {@literal >} 0, Signum.NEGATIVE, if the scalar
   * product is {@literal <} 0, and Signum.ZERO, if the scalar product is equal 0.
   */
  public Signum projection(Direction pOther) {
    return this.getVector().projection(pOther.getVector());
  }

  /** calculates an approximation of the direction in the middle of this direction and p_other */
  public Direction middleApprox(Direction pOther) {
    FloatPoint v1 = getVector().toFloat();
    FloatPoint v2 = pOther.getVector().toFloat();
    double length1 = v1.size();
    double length2 = v2.size();
    double x = v1.x / length1 + v2.x / length2;
    double y = v1.y / length1 + v2.y / length2;
    final double scaleFactor = 1000;
    Vector vm = new IntVector((int) Math.round(x * scaleFactor), (int) Math.round(y * scaleFactor));
    return Direction.getInstance(vm);
  }

  /**
   * Returns 1, if the angle between p_1 and this direction is bigger the angle between p_2 and this
   * direction, 0, if p_1 is equal to p_2, * and -1 otherwise.
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

  abstract int compareTo(IntDirection pOther);

  abstract int compareTo(BigIntDirection pOther);

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

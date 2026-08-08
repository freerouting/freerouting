package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.math.BigInteger;

/** Implementation of the abstract class Point as a tuple of integers. */
public class IntPoint extends Point implements Serializable {

  /** the x coordinate of this point */
  public final int x;

  /** the y coordinate of this point */
  public final int y;

  /** create an IntPoint from two integer coordinates */
  public IntPoint(int p_x, int p_y) {
    if (Math.abs(p_x) > Limits.CRIT_INT) {
      FRLogger.debug("IntPoint: p_x is out of range");
    }
    if (Math.abs(p_y) > Limits.CRIT_INT) {
      FRLogger.debug("IntPoint: p_y is out of range");
    }

    x = p_x;
    y = p_y;
  }

  /** Returns true, if this IntPoint is equal to p_ob */
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
    IntPoint other = (IntPoint) p_ob;
    return x == other.x && y == other.y;
  }

  @Override
  public boolean isInfinite() {
    return false;
  }

  @Override
  public IntBox surroundingBox() {
    return new IntBox(this, this);
  }

  @Override
  public IntOctagon surroundingOctagon() {
    int tmp1 = x - y;
    int tmp2 = x + y;

    return new IntOctagon(x, y, x, y, tmp1, tmp1, tmp2, tmp2);
  }

  @Override
  public boolean isContainedIn(IntBox p_box) {
    return x >= p_box.ll.x && y >= p_box.ll.y && x <= p_box.ur.x && y <= p_box.ur.y;
  }

  /** returns the translation of this point by p_vector */
  @Override
  public final Point translateBy(Vector p_vector) {
    if (p_vector.equals(Vector.ZERO)) {
      return this;
    }
    return p_vector.addTo(this);
  }

  @Override
  Point translateBy(IntVector p_vector) {
    return new IntPoint(x + p_vector.x, y + p_vector.y);
  }

  @Override
  Point translateBy(RationalVector p_vector) {
    return p_vector.addTo(this);
  }

  /** returns the difference vector of this point and p_other */
  @Override
  public Vector differenceBy(Point p_other) {
    Vector tmp = p_other.differenceBy(this);
    return tmp.negate();
  }

  @Override
  Vector differenceBy(RationalPoint p_other) {
    Vector tmp = p_other.differenceBy(this);
    return tmp.negate();
  }

  @Override
  IntVector differenceBy(IntPoint p_other) {
    return new IntVector(x - p_other.x, y - p_other.y);
  }

  @Override
  public Side sideOf(Line p_line) {
    Vector v1 = differenceBy(p_line.a);
    Vector v2 = p_line.b.differenceBy(p_line.a);
    return v1.sideOf(v2);
  }

  /** converts this point to a FloatPoint. */
  @Override
  public FloatPoint toFloat() {
    return new FloatPoint(x, y);
  }

  public int getIdNo() {
    return 31 * x + y;
  }

  /** returns the determinant of the vectors (x, y) and (p_other.x, p_other.y) */
  public final long determinant(IntPoint p_other) {
    return (long) x * p_other.y - (long) y * p_other.x;
  }

  @Override
  public Point perpendicularProjection(Line p_line) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntVector v = (IntVector) p_line.b.differenceBy(p_line.a);
    BigInteger vxvx = BigInteger.valueOf((long) v.x * v.x);
    BigInteger vyvy = BigInteger.valueOf((long) v.y * v.y);
    BigInteger vxvy = BigInteger.valueOf((long) v.x * v.y);
    BigInteger denominator = vxvx.add(vyvy);
    BigInteger det = BigInteger.valueOf(((IntPoint) p_line.a).determinant((IntPoint) p_line.b));
    BigInteger pointX = BigInteger.valueOf(x);
    BigInteger pointY = BigInteger.valueOf(y);

    BigInteger tmp1 = vxvx.multiply(pointX);
    BigInteger tmp2 = vxvy.multiply(pointY);
    tmp1 = tmp1.add(tmp2);
    tmp2 = det.multiply(BigInteger.valueOf(v.y));
    BigInteger projX = tmp1.add(tmp2);

    tmp1 = vxvy.multiply(pointX);
    tmp2 = vyvy.multiply(pointY);
    tmp1 = tmp1.add(tmp2);
    tmp2 = det.multiply(BigInteger.valueOf(v.x));
    BigInteger projY = tmp1.subtract(tmp2);

    int signum = denominator.signum();
    if (signum != 0) {
      if (signum < 0) {
        denominator = denominator.negate();
        projX = projX.negate();
        projY = projY.negate();
      }
      if (projX.mod(denominator).signum() == 0 && projY.mod(denominator).signum() == 0) {
        projX = projX.divide(denominator);
        projY = projY.divide(denominator);
        return new IntPoint(projX.intValue(), projY.intValue());
      }
    }
    return new RationalPoint(projX, projY, denominator);
  }

  /**
   * Returns the signed area of the parallelogramm spanned by the vectors p_2 - p_1 and this - p_1
   */
  public double signedArea(IntPoint p_1, IntPoint p_2) {
    IntVector d21 = p_2.differenceBy(p_1);
    IntVector d01 = this.differenceBy(p_1);
    return d21.determinant(d01);
  }

  /** calculates the square of the distance between this point and p_to_point */
  public double distanceSquare(IntPoint p_to_point) {
    double dx = p_to_point.x - this.x;
    double dy = p_to_point.y - this.y;
    return dx * dx + dy * dy;
  }

  /** calculates the distance between this point and p_to_point */
  public double distance(IntPoint p_to_point) {
    return Math.sqrt(distanceSquare(p_to_point));
  }

  /**
   * Calculates the nearest point to this point on the horizontal or vertical line through p_other
   * (Snaps this point to on orthogonal line through p_other).
   */
  public IntPoint orthogonalProjection(IntPoint p_other) {
    IntPoint result;
    int horizontalDistance = Math.abs(this.x - p_other.x);
    int verticalDistance = Math.abs(this.y - p_other.y);
    if (horizontalDistance <= verticalDistance) {
      // projection onto the vertical line through p_other
      result = new IntPoint(p_other.x, this.y);
    } else {
      // projection onto the horizontal line through p_other
      result = new IntPoint(this.x, p_other.y);
    }
    return result;
  }

  /**
   * Calculates the nearest point to this point on an orthogonal or diagonal line through p_other
   * (Snaps this point to on 45 degree line through p_other).
   */
  public IntPoint fortyfiveDegreeProjection(IntPoint p_other) {
    int dx = this.x - p_other.x;
    int dy = this.y - p_other.y;
    double[] distArr = new double[4];
    distArr[0] = Math.abs(dx);
    distArr[1] = Math.abs(dy);
    double diagonal1 = ((double) dy - (double) dx) / 2;
    double diagonal2 = ((double) dy + (double) dx) / 2;
    distArr[2] = Math.abs(diagonal1);
    distArr[3] = Math.abs(diagonal2);
    double minDist = distArr[0];
    for (int i = 1; i < 4; i++) {
      if (distArr[i] < minDist) {
        minDist = distArr[i];
      }
    }
    IntPoint result;
    if (minDist == distArr[0]) {
      // projection onto the vertical line through p_other
      result = new IntPoint(p_other.x, this.y);
    } else if (minDist == distArr[1]) {
      // projection onto the horizontal line through p_other
      result = new IntPoint(this.x, p_other.y);
    } else if (minDist == distArr[2]) {
      // projection onto the right diagonal line through p_other
      int diagonalValue = (int) diagonal2;
      result = new IntPoint(p_other.x + diagonalValue, p_other.y + diagonalValue);
    } else {
      // projection onto the left diagonal line through p_other
      int diagonalValue = (int) diagonal1;
      result = new IntPoint(p_other.x - diagonalValue, p_other.y + diagonalValue);
    }
    return result;
  }

  /**
   * Calculates a corner point p so that the lines through this point and p and from p to p_to_point
   * are multiples of 45 degree, and that the angle at p will be 45 degree. If p_left_turn,
   * p_to_point will be on the left of the line from this point to p, else on the right. Returns
   * null, if the line from this point to p_to_point is already a multiple of 45 degree.
   */
  public IntPoint fortyfiveDegreeCorner(IntPoint p_to_point, boolean p_left_turn) {
    int dx = p_to_point.x - this.x;
    int dy = p_to_point.y - this.y;
    IntPoint result;

    // handle the 8 sections between the 45 degree lines

    if (dy > 0 && dy < dx) {
      if (p_left_turn) {
        result = new IntPoint(p_to_point.x - dy, this.y);
      } else {
        result = new IntPoint(this.x + dy, p_to_point.y);
      }
    } else if (dx > 0 && dy > dx) {
      if (p_left_turn) {
        result = new IntPoint(p_to_point.x, this.y + dx);
      } else {
        result = new IntPoint(this.x, p_to_point.y - dx);
      }
    } else if (dx < 0 && dy > -dx) {
      if (p_left_turn) {
        result = new IntPoint(this.x, p_to_point.y + dx);
      } else {
        result = new IntPoint(p_to_point.x, this.y - dx);
      }
    } else if (dy > 0 && dy < -dx) {
      if (p_left_turn) {
        result = new IntPoint(this.x - dy, p_to_point.y);
      } else {
        result = new IntPoint(p_to_point.x + dy, this.y);
      }
    } else if (dy < 0 && dy > dx) {
      if (p_left_turn) {
        result = new IntPoint(p_to_point.x - dy, this.y);
      } else {
        result = new IntPoint(this.x + dy, p_to_point.y);
      }
    } else if (dx < 0 && dy < dx) {
      if (p_left_turn) {
        result = new IntPoint(p_to_point.x, this.y + dx);
      } else {
        result = new IntPoint(this.x, p_to_point.y - dx);
      }
    } else if (dx > 0 && dy < -dx) {
      if (p_left_turn) {
        result = new IntPoint(this.x, p_to_point.y + dx);
      } else {
        result = new IntPoint(p_to_point.x, this.y - dx);
      }
    } else if (dy < 0 && dy > -dx) {
      if (p_left_turn) {
        result = new IntPoint(this.x - dy, p_to_point.y);
      } else {
        result = new IntPoint(p_to_point.x + dy, this.y);
      }
    } else {
      // the line from this point to p_to_point is already a multiple of 45 degree
      result = null;
    }
    return result;
  }

  /**
   * Calculates a corner point p so that the lines through this point and p and from p to p_to_point
   * are horizontal or vertical, and that the angle at p will be 90 degree. If p_left_turn,
   * p_to_point will be on the left of the line from this point to p, else on the right. Returns
   * null, if the line from this point to p_to_point is already orthogonal.
   */
  public IntPoint ninetyDegreeCorner(IntPoint p_to_point, boolean p_left_turn) {
    int dx = p_to_point.x - this.x;
    int dy = p_to_point.y - this.y;
    IntPoint result;

    // handle the 4 quadrants

    if (dx > 0 && dy > 0 || dx < 0 && dy < 0) {
      if (p_left_turn) {
        result = new IntPoint(p_to_point.x, this.y);
      } else {
        result = new IntPoint(this.x, p_to_point.y);
      }
    } else if (dx < 0 && dy > 0 || dx > 0 && dy < 0) {
      if (p_left_turn) {
        result = new IntPoint(this.x, p_to_point.y);
      } else {
        result = new IntPoint(p_to_point.x, this.y);
      }
    } else {
      // the line from this point to p_to_point is already orthogonal
      result = null;
    }
    return result;
  }

  @Override
  public int compareX(Point p_other) {
    return -p_other.compareX(this);
  }

  @Override
  public int compareY(Point p_other) {
    return -p_other.compareY(this);
  }

  @Override
  int compareX(IntPoint p_other) {
    int result;
    if (this.x > p_other.x) {
      result = 1;
    } else if (this.x == p_other.x) {
      result = 0;
    } else {
      result = -1;
    }
    return result;
  }

  @Override
  int compareY(IntPoint p_other) {
    int result;
    if (this.y > p_other.y) {
      result = 1;
    } else if (this.y == p_other.y) {
      result = 0;
    } else {
      result = -1;
    }
    return result;
  }

  @Override
  int compareX(RationalPoint p_other) {
    return -p_other.compareX(this);
  }

  @Override
  int compareY(RationalPoint p_other) {
    return -p_other.compareY(this);
  }

  @Override
  public String toString() {
    return "(" + this.x + "," + this.y + ")";
  }
}

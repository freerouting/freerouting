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
  public IntPoint(int pX, int pY) {
    if (Math.abs(pX) > Limits.CRIT_INT) {
      FRLogger.debug("IntPoint: p_x is out of range");
    }
    if (Math.abs(pY) > Limits.CRIT_INT) {
      FRLogger.debug("IntPoint: p_y is out of range");
    }

    x = pX;
    y = pY;
  }

  /** Returns true, if this IntPoint is equal to p_ob */
  @Override
  public final boolean equals(Object pOb) {
    if (this == pOb) {
      return true;
    }
    if (pOb == null) {
      return false;
    }
    if (getClass() != pOb.getClass()) {
      return false;
    }
    IntPoint other = (IntPoint) pOb;
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
  public boolean isContainedIn(IntBox pBox) {
    return x >= pBox.ll.x && y >= pBox.ll.y && x <= pBox.ur.x && y <= pBox.ur.y;
  }

  /** returns the translation of this point by p_vector */
  @Override
  public final Point translateBy(Vector pVector) {
    if (pVector.equals(Vector.ZERO)) {
      return this;
    }
    return pVector.addTo(this);
  }

  @Override
  Point translateBy(IntVector pVector) {
    return new IntPoint(x + pVector.x, y + pVector.y);
  }

  @Override
  Point translateBy(RationalVector pVector) {
    return pVector.addTo(this);
  }

  /** returns the difference vector of this point and p_other */
  @Override
  public Vector differenceBy(Point pOther) {
    Vector tmp = pOther.differenceBy(this);
    return tmp.negate();
  }

  @Override
  Vector differenceBy(RationalPoint pOther) {
    Vector tmp = pOther.differenceBy(this);
    return tmp.negate();
  }

  @Override
  IntVector differenceBy(IntPoint pOther) {
    return new IntVector(x - pOther.x, y - pOther.y);
  }

  @Override
  public Side sideOf(Line pLine) {
    Vector v1 = differenceBy(pLine.a);
    Vector v2 = pLine.b.differenceBy(pLine.a);
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
  public final long determinant(IntPoint pOther) {
    return (long) x * pOther.y - (long) y * pOther.x;
  }

  @Override
  public Point perpendicularProjection(Line pLine) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntVector v = (IntVector) pLine.b.differenceBy(pLine.a);
    BigInteger vxvx = BigInteger.valueOf((long) v.x * v.x);
    BigInteger vyvy = BigInteger.valueOf((long) v.y * v.y);
    BigInteger vxvy = BigInteger.valueOf((long) v.x * v.y);
    BigInteger denominator = vxvx.add(vyvy);
    BigInteger det = BigInteger.valueOf(((IntPoint) pLine.a).determinant((IntPoint) pLine.b));
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
  public double signedArea(IntPoint p1, IntPoint p2) {
    IntVector d21 = p2.differenceBy(p1);
    IntVector d01 = this.differenceBy(p1);
    return d21.determinant(d01);
  }

  /** calculates the square of the distance between this point and p_to_point */
  public double distanceSquare(IntPoint pToPoint) {
    double dx = pToPoint.x - this.x;
    double dy = pToPoint.y - this.y;
    return dx * dx + dy * dy;
  }

  /** calculates the distance between this point and p_to_point */
  public double distance(IntPoint pToPoint) {
    return Math.sqrt(distanceSquare(pToPoint));
  }

  /**
   * Calculates the nearest point to this point on the horizontal or vertical line through p_other
   * (Snaps this point to on orthogonal line through p_other).
   */
  public IntPoint orthogonalProjection(IntPoint pOther) {
    IntPoint result;
    int horizontalDistance = Math.abs(this.x - pOther.x);
    int verticalDistance = Math.abs(this.y - pOther.y);
    if (horizontalDistance <= verticalDistance) {
      // projection onto the vertical line through p_other
      result = new IntPoint(pOther.x, this.y);
    } else {
      // projection onto the horizontal line through p_other
      result = new IntPoint(this.x, pOther.y);
    }
    return result;
  }

  /**
   * Calculates the nearest point to this point on an orthogonal or diagonal line through p_other
   * (Snaps this point to on 45 degree line through p_other).
   */
  public IntPoint fortyfiveDegreeProjection(IntPoint pOther) {
    int dx = this.x - pOther.x;
    int dy = this.y - pOther.y;
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
      result = new IntPoint(pOther.x, this.y);
    } else if (minDist == distArr[1]) {
      // projection onto the horizontal line through p_other
      result = new IntPoint(this.x, pOther.y);
    } else if (minDist == distArr[2]) {
      // projection onto the right diagonal line through p_other
      int diagonalValue = (int) diagonal2;
      result = new IntPoint(pOther.x + diagonalValue, pOther.y + diagonalValue);
    } else {
      // projection onto the left diagonal line through p_other
      int diagonalValue = (int) diagonal1;
      result = new IntPoint(pOther.x - diagonalValue, pOther.y + diagonalValue);
    }
    return result;
  }

  /**
   * Calculates a corner point p so that the lines through this point and p and from p to p_to_point
   * are multiples of 45 degree, and that the angle at p will be 45 degree. If p_left_turn,
   * p_to_point will be on the left of the line from this point to p, else on the right. Returns
   * null, if the line from this point to p_to_point is already a multiple of 45 degree.
   */
  public IntPoint fortyfiveDegreeCorner(IntPoint pToPoint, boolean pLeftTurn) {
    int dx = pToPoint.x - this.x;
    int dy = pToPoint.y - this.y;
    IntPoint result;

    // handle the 8 sections between the 45 degree lines

    if (dy > 0 && dy < dx) {
      if (pLeftTurn) {
        result = new IntPoint(pToPoint.x - dy, this.y);
      } else {
        result = new IntPoint(this.x + dy, pToPoint.y);
      }
    } else if (dx > 0 && dy > dx) {
      if (pLeftTurn) {
        result = new IntPoint(pToPoint.x, this.y + dx);
      } else {
        result = new IntPoint(this.x, pToPoint.y - dx);
      }
    } else if (dx < 0 && dy > -dx) {
      if (pLeftTurn) {
        result = new IntPoint(this.x, pToPoint.y + dx);
      } else {
        result = new IntPoint(pToPoint.x, this.y - dx);
      }
    } else if (dy > 0 && dy < -dx) {
      if (pLeftTurn) {
        result = new IntPoint(this.x - dy, pToPoint.y);
      } else {
        result = new IntPoint(pToPoint.x + dy, this.y);
      }
    } else if (dy < 0 && dy > dx) {
      if (pLeftTurn) {
        result = new IntPoint(pToPoint.x - dy, this.y);
      } else {
        result = new IntPoint(this.x + dy, pToPoint.y);
      }
    } else if (dx < 0 && dy < dx) {
      if (pLeftTurn) {
        result = new IntPoint(pToPoint.x, this.y + dx);
      } else {
        result = new IntPoint(this.x, pToPoint.y - dx);
      }
    } else if (dx > 0 && dy < -dx) {
      if (pLeftTurn) {
        result = new IntPoint(this.x, pToPoint.y + dx);
      } else {
        result = new IntPoint(pToPoint.x, this.y - dx);
      }
    } else if (dy < 0 && dy > -dx) {
      if (pLeftTurn) {
        result = new IntPoint(this.x - dy, pToPoint.y);
      } else {
        result = new IntPoint(pToPoint.x + dy, this.y);
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
  public IntPoint ninetyDegreeCorner(IntPoint pToPoint, boolean pLeftTurn) {
    int dx = pToPoint.x - this.x;
    int dy = pToPoint.y - this.y;
    IntPoint result;

    // handle the 4 quadrants

    if (dx > 0 && dy > 0 || dx < 0 && dy < 0) {
      if (pLeftTurn) {
        result = new IntPoint(pToPoint.x, this.y);
      } else {
        result = new IntPoint(this.x, pToPoint.y);
      }
    } else if (dx < 0 && dy > 0 || dx > 0 && dy < 0) {
      if (pLeftTurn) {
        result = new IntPoint(this.x, pToPoint.y);
      } else {
        result = new IntPoint(pToPoint.x, this.y);
      }
    } else {
      // the line from this point to p_to_point is already orthogonal
      result = null;
    }
    return result;
  }

  @Override
  public int compareX(Point pOther) {
    return -pOther.compareX(this);
  }

  @Override
  public int compareY(Point pOther) {
    return -pOther.compareY(this);
  }

  @Override
  int compareX(IntPoint pOther) {
    int result;
    if (this.x > pOther.x) {
      result = 1;
    } else if (this.x == pOther.x) {
      result = 0;
    } else {
      result = -1;
    }
    return result;
  }

  @Override
  int compareY(IntPoint pOther) {
    int result;
    if (this.y > pOther.y) {
      result = 1;
    } else if (this.y == pOther.y) {
      result = 0;
    } else {
      result = -1;
    }
    return result;
  }

  @Override
  int compareX(RationalPoint pOther) {
    return -pOther.compareX(this);
  }

  @Override
  int compareY(RationalPoint pOther) {
    return -pOther.compareY(this);
  }

  @Override
  public String toString() {
    return "(" + this.x + "," + this.y + ")";
  }
}

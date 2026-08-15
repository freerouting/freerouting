package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.math.BigInteger;

/** Implementation of the abstract class Point as a tuple of integers. */
public class IntPoint extends Point implements Serializable {

  /** The x coordinate of this point. */
  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final int x;

  /** The y coordinate of this point. */
  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final int y;

  /** Creates an IntPoint from two integer coordinates. */
  public IntPoint(int x, int y) {
    if (Math.abs(x) > Limits.CRIT_INT) {
      FRLogger.debug("IntPoint: x is out of range");
    }
    if (Math.abs(y) > Limits.CRIT_INT) {
      FRLogger.debug("IntPoint: y is out of range");
    }

    this.x = x;
    this.y = y;
  }

  /** Returns true, if this IntPoint is equal to ob. */
  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null) {
      return false;
    }
    if (getClass() != other.getClass()) {
      return false;
    }
    IntPoint otherPoint = (IntPoint) other;
    return x == otherPoint.x && y == otherPoint.y;
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
  public boolean isContainedIn(IntBox box) {
    return x >= box.ll.x && y >= box.ll.y && x <= box.ur.x && y <= box.ur.y;
  }

  /** Returns the translation of this point by vector. */
  @Override
  public final Point translateBy(Vector vector) {
    if (vector.equals(Vector.ZERO)) {
      return this;
    }
    return vector.addTo(this);
  }

  @Override
  Point translateBy(IntVector vector) {
    return new IntPoint(x + vector.x, y + vector.y);
  }

  @Override
  Point translateBy(RationalVector vector) {
    return vector.addTo(this);
  }

  /** Returns the difference vector of this point and other. */
  @Override
  public Vector differenceBy(Point other) {
    Vector tmp = other.differenceBy(this);
    return tmp.negate();
  }

  @Override
  Vector differenceBy(RationalPoint other) {
    Vector tmp = other.differenceBy(this);
    return tmp.negate();
  }

  @Override
  IntVector differenceBy(IntPoint other) {
    return new IntVector(x - other.x, y - other.y);
  }

  @Override
  public Side sideOf(Line line) {
    Vector v1 = differenceBy(line.a);
    Vector v2 = line.b.differenceBy(line.a);
    return v1.sideOf(v2);
  }

  /** Converts this point to a FloatPoint. */
  @Override
  public FloatPoint toFloat() {
    return new FloatPoint(x, y);
  }

  public int getIdNo() {
    return 31 * x + y;
  }

  /** Returns the determinant of the vectors (x, y) and (other.x, other.y). */
  public final long determinant(IntPoint other) {
    return (long) x * other.y - (long) y * other.x;
  }

  @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
  @Override
  public Point perpendicularProjection(Line line) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntVector v = (IntVector) line.b.differenceBy(line.a);
    BigInteger vxvx = BigInteger.valueOf((long) v.x * v.x);
    BigInteger vyvy = BigInteger.valueOf((long) v.y * v.y);
    BigInteger vxvy = BigInteger.valueOf((long) v.x * v.y);
    BigInteger denominator = vxvx.add(vyvy);
    BigInteger det = BigInteger.valueOf(((IntPoint) line.a).determinant((IntPoint) line.b));
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

  /** Returns the signed area of the parallelogramm spanned by the vectors 2 - 1 and this - 1. */
  public double signedArea(IntPoint p1, IntPoint p2) {
    IntVector d21 = p2.differenceBy(p1);
    IntVector d01 = this.differenceBy(p1);
    return d21.determinant(d01);
  }

  /** Calculates the square of the distance between this point and toPoint. */
  public double distanceSquare(IntPoint toPoint) {
    double dx = toPoint.x - this.x;
    double dy = toPoint.y - this.y;
    return dx * dx + dy * dy;
  }

  /** Calculates the distance between this point and toPoint. */
  public double distance(IntPoint toPoint) {
    return Math.sqrt(distanceSquare(toPoint));
  }

  /**
   * Calculates the nearest point to this point on the horizontal or vertical line through other
   * (Snaps this point to on orthogonal line through other).
   */
  public IntPoint orthogonalProjection(IntPoint other) {
    IntPoint result;
    int horizontalDistance = Math.abs(this.x - other.x);
    int verticalDistance = Math.abs(this.y - other.y);
    if (horizontalDistance <= verticalDistance) {
      // projection onto the vertical line through other
      result = new IntPoint(other.x, this.y);
    } else {
      // projection onto the horizontal line through other
      result = new IntPoint(this.x, other.y);
    }
    return result;
  }

  /**
   * Calculates the nearest point to this point on an orthogonal or diagonal line through other
   * (Snaps this point to on 45 degree line through other).
   */
  public IntPoint fortyfiveDegreeProjection(IntPoint other) {
    int dx = this.x - other.x;
    int dy = this.y - other.y;
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
      // projection onto the vertical line through other
      result = new IntPoint(other.x, this.y);
    } else if (minDist == distArr[1]) {
      // projection onto the horizontal line through other
      result = new IntPoint(this.x, other.y);
    } else if (minDist == distArr[2]) {
      // projection onto the right diagonal line through other
      int diagonalValue = (int) diagonal2;
      result = new IntPoint(other.x + diagonalValue, other.y + diagonalValue);
    } else {
      // projection onto the left diagonal line through other
      int diagonalValue = (int) diagonal1;
      result = new IntPoint(other.x - diagonalValue, other.y + diagonalValue);
    }
    return result;
  }

  /**
   * Calculates a corner point p so that the lines through this point and p and from p to toPoint
   * are multiples of 45 degree, and that the angle at p will be 45 degree. If leftTurn, toPoint
   * will be on the left of the line from this point to p, else on the right. Returns null, if the
   * line from this point to toPoint is already a multiple of 45 degree.
   */
  public IntPoint fortyfiveDegreeCorner(IntPoint toPoint, boolean leftTurn) {
    int dx = toPoint.x - this.x;
    int dy = toPoint.y - this.y;
    IntPoint result;

    // handle the 8 sections between the 45 degree lines

    if (dy > 0 && dy < dx) {
      if (leftTurn) {
        result = new IntPoint(toPoint.x - dy, this.y);
      } else {
        result = new IntPoint(this.x + dy, toPoint.y);
      }
    } else if (dx > 0 && dy > dx) {
      if (leftTurn) {
        result = new IntPoint(toPoint.x, this.y + dx);
      } else {
        result = new IntPoint(this.x, toPoint.y - dx);
      }
    } else if (dx < 0 && dy > -dx) {
      if (leftTurn) {
        result = new IntPoint(this.x, toPoint.y + dx);
      } else {
        result = new IntPoint(toPoint.x, this.y - dx);
      }
    } else if (dy > 0 && dy < -dx) {
      if (leftTurn) {
        result = new IntPoint(this.x - dy, toPoint.y);
      } else {
        result = new IntPoint(toPoint.x + dy, this.y);
      }
    } else if (dy < 0 && dy > dx) {
      if (leftTurn) {
        result = new IntPoint(toPoint.x - dy, this.y);
      } else {
        result = new IntPoint(this.x + dy, toPoint.y);
      }
    } else if (dx < 0 && dy < dx) {
      if (leftTurn) {
        result = new IntPoint(toPoint.x, this.y + dx);
      } else {
        result = new IntPoint(this.x, toPoint.y - dx);
      }
    } else if (dx > 0 && dy < -dx) {
      if (leftTurn) {
        result = new IntPoint(this.x, toPoint.y + dx);
      } else {
        result = new IntPoint(toPoint.x, this.y - dx);
      }
    } else if (dy < 0 && dy > -dx) {
      if (leftTurn) {
        result = new IntPoint(this.x - dy, toPoint.y);
      } else {
        result = new IntPoint(toPoint.x + dy, this.y);
      }
    } else {
      // the line from this point to toPoint is already a multiple of 45 degree
      result = null;
    }
    return result;
  }

  /**
   * Calculates a corner point p so that the lines through this point and p and from p to toPoint
   * are horizontal or vertical, and that the angle at p will be 90 degree. If leftTurn, toPoint
   * will be on the left of the line from this point to p, else on the right. Returns null, if the
   * line from this point to toPoint is already orthogonal.
   */
  public IntPoint ninetyDegreeCorner(IntPoint toPoint, boolean leftTurn) {
    int dx = toPoint.x - this.x;
    int dy = toPoint.y - this.y;
    IntPoint result;

    // handle the 4 quadrants

    if (dx > 0 && dy > 0 || dx < 0 && dy < 0) {
      if (leftTurn) {
        result = new IntPoint(toPoint.x, this.y);
      } else {
        result = new IntPoint(this.x, toPoint.y);
      }
    } else if (dx < 0 && dy > 0 || dx > 0 && dy < 0) {
      if (leftTurn) {
        result = new IntPoint(this.x, toPoint.y);
      } else {
        result = new IntPoint(toPoint.x, this.y);
      }
    } else {
      // the line from this point to toPoint is already orthogonal
      result = null;
    }
    return result;
  }

  @Override
  public int compareX(Point other) {
    return -other.compareX(this);
  }

  @Override
  int compareX(IntPoint other) {
    int result;
    if (this.x > other.x) {
      result = 1;
    } else if (this.x == other.x) {
      result = 0;
    } else {
      result = -1;
    }
    return result;
  }

  @Override
  int compareX(RationalPoint other) {
    return -other.compareX(this);
  }

  @Override
  public int compareY(Point other) {
    return -other.compareY(this);
  }

  @Override
  int compareY(IntPoint other) {
    int result;
    if (this.y > other.y) {
      result = 1;
    } else if (this.y == other.y) {
      result = 0;
    } else {
      result = -1;
    }
    return result;
  }

  @Override
  int compareY(RationalPoint other) {
    return -other.compareY(this);
  }

  @Override
  public String toString() {
    return "(" + this.x + "," + this.y + ")";
  }
}

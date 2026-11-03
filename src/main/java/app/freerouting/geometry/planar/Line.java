package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Signum;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.math.BigInteger;

/** Implements functionality for lines in the plane. */
public class Line implements Comparable<Line>, Serializable {

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final Point a;

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final Point b;

  private transient Direction dir; // should only be accessed from getDirection().

  /** Creates a directed Line from two points. */
  public Line(Point a, Point b) {
    if (a instanceof IntPoint && b instanceof IntPoint) {
      this.a = a;
      this.b = b;
      this.dir = null;
      return;
    }
    IntPoint[] support = integerSupport(a, b);
    this.a = support[0];
    this.b = support[1];
    this.dir = null;
  }

  /** Creates a directed Line from four integer coordinates. */
  public Line(int ax, int ay, int bx, int by) {
    this.a = new IntPoint(ax, ay);
    this.b = new IntPoint(bx, by);
    this.dir = null;
  }

  /** Creates a directed Line from a point and a direction. */
  public Line(Point a, Direction dir) {
    Point b = a.translateBy(dir.getVector());
    if (a instanceof IntPoint && b instanceof IntPoint) {
      this.a = a;
      this.b = b;
      this.dir = dir;
      return;
    }
    IntPoint[] support = integerSupport(a, b);
    this.a = support[0];
    this.b = support[1];
    this.dir = null;
  }

  /** Finds a bounded, nonparallel cap at an exact point without rounding it. */
  public Line capAt(Point point) {
    try {
      return new Line(point, direction().turn45Degree(2));
    } catch (IllegalArgumentException unsupportedPerpendicular) {
      if (point == null || point.isInfinite()) {
        return null;
      }
      BigInteger[] coordinates = rationalCoordinates(point);
      BigInteger x = floorDivide(coordinates[0], coordinates[2]);
      BigInteger y = floorDivide(coordinates[1], coordinates[2]);
      for (int[] offset : new int[][] {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
        BigInteger ax = x.add(BigInteger.valueOf(offset[0]));
        BigInteger ay = y.add(BigInteger.valueOf(offset[1]));
        if (ax.abs().compareTo(Limits.CRIT_INT_BIG) > 0
            || ay.abs().compareTo(Limits.CRIT_INT_BIG) > 0) {
          continue;
        }
        try {
          Line cap = new Line(point, new IntPoint(ax.intValueExact(), ay.intValueExact()));
          if (!isParallel(cap)) {
            return cap;
          }
        } catch (IllegalArgumentException unsupportedCap) {
          // Try the next exact lattice direction; never move the junction.
        }
      }
      return null;
    }
  }

  /** Resolves exact points to the bounded integer supports required by line operations. */
  private static IntPoint[] integerSupport(Point first, Point second) {
    if (first instanceof IntPoint a && second instanceof IntPoint b) {
      return new IntPoint[] {a, b};
    }
    if (first == null || second == null || first.isInfinite() || second.isInfinite()) {
      throw new IllegalArgumentException("A line requires finite points");
    }
    BigInteger[] a = rationalCoordinates(first);
    BigInteger[] b = rationalCoordinates(second);
    BigInteger dx = b[0].multiply(a[2]).subtract(a[0].multiply(b[2]));
    BigInteger dy = b[1].multiply(a[2]).subtract(a[1].multiply(b[2]));
    BigInteger gcd = dx.gcd(dy);
    if (gcd.signum() == 0) {
      throw new IllegalArgumentException("A line requires distinct points");
    }
    dx = dx.divide(gcd);
    dy = dy.divide(gcd);
    BigInteger[] constant = a[0].multiply(dy).subtract(a[1].multiply(dx)).divideAndRemainder(a[2]);
    if (constant[1].signum() != 0) {
      throw new IllegalArgumentException("The line has no integer defining points");
    }
    BigInteger x;
    BigInteger y;
    if (dx.signum() == 0) {
      x = constant[0].divide(dy);
      y = BigInteger.ZERO;
    } else {
      BigInteger modulus = dx.abs();
      x = constant[0].multiply(dy.mod(modulus).modInverse(modulus)).mod(modulus);
      y = dy.multiply(x).subtract(constant[0]).divide(dx);
    }
    BigInteger[] interval = {null, null};
    restrictSupportParameter(interval, x, dx);
    restrictSupportParameter(interval, y, dy);
    if (interval[0].compareTo(interval[1]) > 0) {
      throw new IllegalArgumentException("The line has no bounded integer defining points");
    }
    BigInteger step =
        dx.signum() == 0
            ? floorDivide(a[1].subtract(y.multiply(a[2])), dy.multiply(a[2]))
            : floorDivide(a[0].subtract(x.multiply(a[2])), dx.multiply(a[2]));
    step = step.max(interval[0]).min(interval[1]);
    x = x.add(step.multiply(dx));
    y = y.add(step.multiply(dy));
    return new IntPoint[] {
      new IntPoint(x.intValueExact(), y.intValueExact()),
      new IntPoint(x.add(dx).intValueExact(), y.add(dy).intValueExact())
    };
  }

  private static BigInteger[] rationalCoordinates(Point point) {
    if (point instanceof IntPoint integer) {
      return new BigInteger[] {
        BigInteger.valueOf(integer.x), BigInteger.valueOf(integer.y), BigInteger.ONE
      };
    }
    RationalPoint rational = (RationalPoint) point;
    return new BigInteger[] {rational.x, rational.y, rational.z};
  }

  private static void restrictSupportParameter(
      BigInteger[] interval, BigInteger origin, BigInteger direction) {
    BigInteger limit = Limits.CRIT_INT_BIG;
    BigInteger low = limit.negate().max(limit.negate().subtract(direction));
    BigInteger high = limit.min(limit.subtract(direction));
    if (direction.signum() == 0) {
      if (origin.compareTo(low) < 0 || origin.compareTo(high) > 0) {
        throw new IllegalArgumentException("The line is outside the integer coordinate range");
      }
      return;
    }
    BigInteger start = direction.signum() > 0 ? low : high;
    BigInteger end = direction.signum() > 0 ? high : low;
    BigInteger lower = floorDivide(start.subtract(origin).negate(), direction).negate();
    BigInteger upper = floorDivide(end.subtract(origin), direction);
    interval[0] = interval[0] == null ? lower : interval[0].max(lower);
    interval[1] = interval[1] == null ? upper : interval[1].min(upper);
  }

  private static BigInteger floorDivide(BigInteger numerator, BigInteger denominator) {
    if (denominator.signum() < 0) {
      numerator = numerator.negate();
      denominator = denominator.negate();
    }
    BigInteger[] quotient = numerator.divideAndRemainder(denominator);
    return quotient[1].signum() < 0 ? quotient[0].subtract(BigInteger.ONE) : quotient[0];
  }

  /** Creates a directed line from an IntPoint and an IntDirection. */
  public static Line getInstance(Point a, Direction dir) {
    Point b = a.translateBy(dir.getVector());
    return new Line(a, b);
  }

  /** Returns true if this and ob define the same line. */
  public int getId() {
    return 31 * a.getId() + b.getId();
  }

  @Override
  public final boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null) {
      return false;
    }
    if (!(other instanceof Line otherLine)) {
      return false;
    }
    if (sideOf(otherLine.a) != Side.COLLINEAR) {
      return false;
    }
    if (sideOf(otherLine.b) != Side.COLLINEAR) {
      return false;
    }
    Vector dir1 = b.differenceBy(a);
    Vector dir2 = otherLine.b.differenceBy(otherLine.a);
    return dir1.projection(dir2) == Signum.POSITIVE;
  }

  /**
   * Returns true, if this and other define the same line. Is designed for good performance, but
   * works only for lines consisting of IntPoints.
   */
  public final boolean fastEquals(Line other) {
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    IntPoint otherA = (IntPoint) other.a;
    double dx1 = otherA.x - thisA.x;
    double dy1 = otherA.y - thisA.y;
    double dx2 = thisB.x - thisA.x;
    double dy2 = thisB.y - thisA.y;
    double det = dx1 * dy2 - dx2 * dy1;
    if (det != 0) {
      return false;
    }
    return direction().equals(other.direction());
  }

  /** Gets the direction of this directed line. */
  public Direction direction() {
    if (dir == null) {
      Vector d = b.differenceBy(a);
      dir = Direction.getInstance(d);
    }
    return dir;
  }

  /**
   * The function returns Side.ON_THE_LEFT, if this Line is on the left of point, Side.ON_THE_RIGHT,
   * if this Line is on the right of point and Side.COLLINEAR, if this Line contains point.
   */
  public Side sideOf(Point point) {
    Side result = point.sideOf(this);
    return result.negate();
  }

  /**
   * Returns Side.COLLINEAR, if point is on the line with tolerance tolerance. Otherwise,
   * Side.ON_THE_LEFT, if this line is on the left of point, or Side.ON_THE_RIGHT, if this line is
   * on the right of point,
   */
  public Side sideOf(FloatPoint point, double tolerance) {
    // only implemented for IntPoint lines for performance reasons
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    double det =
        (thisB.y - thisA.y) * (point.x - thisA.x) - (thisB.x - thisA.x) * (point.y - thisA.y);
    Side result;
    if (det - tolerance > 0) {
      result = Side.ON_THE_LEFT;
    } else if (det + tolerance < 0) {
      result = Side.ON_THE_RIGHT;
    } else {
      result = Side.COLLINEAR;
    }

    return result;
  }

  /**
   * Returns Side.ON_THE_LEFT, if this line is on the left of point, Side.ON_THE_RIGHT, if this line
   * is on the right of point, Side.COLLINEAR otherwise.
   */
  public Side sideOf(FloatPoint point) {
    return sideOf(point, 0);
  }

  /**
   * Returns Side.ON_THE_LEFT, if this line is on the left of the intersection of 1 and 2,
   * Side.ON_THE_RIGHT, if this line is on the right of the intersection, and Side.COLLINEAR, if all
   * 3 lines intersect in exactly 1 point.
   */
  public Side sideOfIntersection(Line p1, Line p2) {

    FloatPoint intersectionApprox = p1.intersectionApprox(p2);
    Side result = this.sideOf(intersectionApprox, 1.0);
    if (result == Side.COLLINEAR) {
      // Previous calculation was with FloatPoints and a tolerance
      // for performance reasons. Make an exact check for
      // collinearity now with class Point instead of FloatPoint.
      Point intersection = p1.intersection(p2);
      result = this.sideOf(intersection);
    }
    return result;
  }

  /** Looks, if all interior points of tile are on the right side of this line. */
  public boolean isOnTheLeft(TileShape tile) {
    for (int i = 0; i < tile.borderLineCount(); i++) {
      if (this.sideOf(tile.corner(i)) == Side.ON_THE_RIGHT) {
        return false;
      }
    }
    return true;
  }

  /** Looks, if all interior points of tile are on the left side of this line. */
  public boolean isOnTheRight(TileShape tile) {
    for (int i = 0; i < tile.borderLineCount(); i++) {
      if (this.sideOf(tile.corner(i)) == Side.ON_THE_LEFT) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the signed distance of this line from point. The result will be positive, if the line
   * is on the left of point, else negative.
   */
  public double signedDistance(FloatPoint point) {
    // only implemented for IntPoint lines for performance reasons
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    double dx = thisB.x - thisA.x;
    double dy = thisB.y - thisA.y;
    double det = dy * (point.x - thisA.x) - dx * (point.y - thisA.y);
    // area of the parallelogramm spanned by the 3 points
    double length = Math.sqrt(dx * dx + dy * dy);
    return det / length;
  }

  /**
   * Returns true if the two lines define the same set of points, but may have opposite directions.
   */
  public boolean overlaps(Line other) {
    return sideOf(other.a) == Side.COLLINEAR && sideOf(other.b) == Side.COLLINEAR;
  }

  /** Returns the line defining the same set of points, but with opposite direction. */
  public Line opposite() {
    return new Line(b, a);
  }

  /**
   * Returns the intersection point of the 2 lines. If the lines are parallel result.isInfinite()
   * will be true.
   */
  public Point intersection(Line other) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntVector delta1 = (IntVector) b.differenceBy(a);
    IntVector delta2 = (IntVector) other.b.differenceBy(other.a);
    // Separate handling for orthogonal and 45 degree lines for better performance
    if (delta1.x == 0) { // this line is vertical
      if (delta2.y == 0) { // other line is horizontal
        return new IntPoint(((IntPoint) this.a).x, ((IntPoint) other.a).y);
      }
      if (delta2.x == delta2.y) { // other line is right diagonal
        int thisX = ((IntPoint) this.a).x;
        IntPoint otherA = (IntPoint) other.a;
        return new IntPoint(thisX, otherA.y + thisX - otherA.x);
      }
      if (delta2.x == -delta2.y) { // other line is left diagonal
        int thisX = ((IntPoint) this.a).x;
        IntPoint otherA = (IntPoint) other.a;
        return new IntPoint(thisX, otherA.y + otherA.x - thisX);
      }
    } else if (delta1.y == 0) { // this line is horizontal
      if (delta2.x == 0) { // other line is vertical
        return new IntPoint(((IntPoint) other.a).x, ((IntPoint) this.a).y);
      }
      if (delta2.x == delta2.y) { // other line is right diagonal
        int thisYcoordinate = ((IntPoint) this.a).y;
        IntPoint otherA = (IntPoint) other.a;
        return new IntPoint(otherA.x + thisYcoordinate - otherA.y, thisYcoordinate);
      }
      if (delta2.x == -delta2.y) { // other line is left diagonal
        int thisYcoordinate = ((IntPoint) this.a).y;
        IntPoint otherA = (IntPoint) other.a;
        return new IntPoint(otherA.x + otherA.y - thisYcoordinate, thisYcoordinate);
      }
    } else if (delta1.x == delta1.y) { // this line is right diagonal
      if (delta2.x == 0) { // other line is vertical
        int otherX = ((IntPoint) other.a).x;
        IntPoint thisA = (IntPoint) this.a;
        return new IntPoint(otherX, thisA.y + otherX - thisA.x);
      }
      if (delta2.y == 0) { // other line is horizontal
        int otherY = ((IntPoint) other.a).y;
        IntPoint thisA = (IntPoint) this.a;
        return new IntPoint(thisA.x + otherY - thisA.y, otherY);
      }
    } else if (delta1.x == -delta1.y) { // this line is left diagonal
      if (delta2.x == 0) { // other line is vertical
        int otherX = ((IntPoint) other.a).x;
        IntPoint thisA = (IntPoint) this.a;
        return new IntPoint(otherX, thisA.y + thisA.x - otherX);
      }
      if (delta2.y == 0) { // other line is horizontal
        int otherY = ((IntPoint) other.a).y;
        IntPoint thisA = (IntPoint) this.a;
        return new IntPoint(thisA.x + thisA.y - otherY, otherY);
      }
    }

    BigInteger det1 = BigInteger.valueOf(((IntPoint) a).determinant((IntPoint) b));
    BigInteger det2 = BigInteger.valueOf(((IntPoint) other.a).determinant((IntPoint) other.b));
    BigInteger det = BigInteger.valueOf(delta2.determinant(delta1));
    BigInteger tmp1 = det1.multiply(BigInteger.valueOf(delta2.x));
    BigInteger tmp2 = det2.multiply(BigInteger.valueOf(delta1.x));
    BigInteger isX = tmp1.subtract(tmp2);
    tmp1 = det1.multiply(BigInteger.valueOf(delta2.y));
    tmp2 = det2.multiply(BigInteger.valueOf(delta1.y));
    BigInteger isY = tmp1.subtract(tmp2);
    int signum = det.signum();
    if (signum != 0) {
      if (signum < 0) {
        det = det.negate();
        isX = isX.negate();
        isY = isY.negate();
      }
      if (isX.mod(det).signum() == 0 && isY.mod(det).signum() == 0) {
        isX = isX.divide(det);
        isY = isY.divide(det);
        if (Math.abs(isX.doubleValue()) <= Limits.CRIT_INT
            && Math.abs(isY.doubleValue()) <= Limits.CRIT_INT) {
          return new IntPoint(isX.intValue(), isY.intValue());
        }
        det = BigInteger.ONE;
      }
    }
    return new RationalPoint(isX, isY, det);
  }

  /**
   * Returns an approximation of the intersection of the 2 lines by a FloatPoint. If the lines are
   * parallel the result coordinates will be Integer.MAX_VALUE. Useful in situations where
   * performance is more important than accuracy.
   */
  public FloatPoint intersectionApprox(Line other) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    IntPoint otherA = (IntPoint) other.a;
    IntPoint otherB = (IntPoint) other.b;
    double d1x = thisB.x - thisA.x;
    double d1y = thisB.y - thisA.y;
    double d2x = otherB.x - otherA.x;
    double d2y = otherB.y - otherA.y;
    double det1 = (double) thisA.x * thisB.y - (double) thisA.y * thisB.x;
    double det2 = (double) otherA.x * otherB.y - (double) otherA.y * otherB.x;
    double det = d2x * d1y - d2y * d1x;
    double isX;
    double isY;
    if (det == 0) {
      isX = Integer.MAX_VALUE;
      isY = Integer.MAX_VALUE;
    } else {
      isX = (d2x * det1 - d1x * det2) / det;
      isY = (d2y * det1 - d1y * det2) / det;
    }
    return new FloatPoint(isX, isY);
  }

  /** Returns the perpendicular projection of point onto this line. */
  public Point perpendicularProjection(Point point) {
    return point.perpendicularProjection(this);
  }

  /**
   * Translates the line perpendicular by dist. If dist {@literal >} 0, the line is translated to
   * the left; otherwise, it is translated to the right.
   */
  public Line translate(double dist) {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    IntPoint ai = (IntPoint) a;
    IntVector v = (IntVector) direction().getVector();
    double vxvx = (double) v.x * v.x;
    double vyvy = (double) v.y * v.y;
    double length = Math.sqrt(vxvx + vyvy);
    IntPoint newA;
    if (vxvx <= vyvy) {
      // translate along the x axis
      int relX = (int) Math.round((dist * length) / v.y);
      newA = new IntPoint(ai.x - relX, ai.y);
    } else {
      // translate along the  y axis
      int relY = (int) Math.round((dist * length) / v.x);
      newA = new IntPoint(ai.x, ai.y + relY);
    }
    return Line.getInstance(newA, direction());
  }

  /** Translates the line by vector. */
  public Line translateBy(Vector vector) {
    if (vector.equals(Vector.ZERO)) {
      return this;
    }
    Point newA = a.translateBy(vector);
    Point newB = b.translateBy(vector);
    return new Line(newA, newB);
  }

  /** Returns true if the line is axis-parallel. */
  public boolean isOrthogonal() {
    return direction().isOrthogonal();
  }

  /** Returns true if this line is diagonal. */
  public boolean isDiagonal() {
    return direction().isDiagonal();
  }

  /** Returns true if the direction of this line is a multiple of 45 degrees. */
  public boolean isMultipleOf45Degree() {
    return direction().isMultipleOf45Degree();
  }

  /** Checks if this line and other are parallel. */
  public boolean isParallel(Line other) {
    return this.direction().sideOf(other.direction()) == Side.COLLINEAR;
  }

  /** Checks if this line and other are perpendicular. */
  public boolean isPerpendicular(Line other) {
    Vector v1 = direction().getVector();
    Vector v2 = other.direction().getVector();
    return v1.projection(v2) == Signum.ZERO;
  }

  /** Returns true if this and ob define the same line. */
  public boolean isEqualOrOpposite(Line other) {

    return sideOf(other.a) == Side.COLLINEAR && sideOf(other.b) == Side.COLLINEAR;
  }

  /** Calculates the cosine of the angle between this line and other. */
  public double cosAngle(Line other) {
    Vector v1 = b.differenceBy(a);
    Vector v2 = other.b.differenceBy(other.a);
    return v1.cosAngle(v2);
  }

  /**
   * A line l_1 is defined bigger than a line l_2, if the direction of l_1 is bigger than the
   * direction of l_2. Implements the comparable interface. Throws a cast exception, if other is not
   * a Line. Fast implementation only for lines consisting of IntPoints because of critical
   * performance
   */
  @Override
  public int compareTo(Line other) {
    IntPoint thisA = (IntPoint) a;
    IntPoint thisB = (IntPoint) b;
    IntPoint otherA = (IntPoint) other.a;
    IntPoint otherB = (IntPoint) other.b;
    int dx1 = thisB.x - thisA.x;
    int dy1 = thisB.y - thisA.y;
    int dx2 = otherB.x - otherA.x;
    int dy2 = otherB.y - otherA.y;
    if (dy1 > 0) {
      if (dy2 < 0) {
        return -1;
      }
      if (dy2 == 0) {
        if (dx2 > 0) {
          return 1;
        }
        return -1;
      }
    } else if (dy1 < 0) {
      if (dy2 >= 0) {
        return 1;
      }
    } else { // dy1 == 0
      if (dx1 > 0) {
        if (dy2 != 0 || dx2 < 0) {
          return -1;
        }
        return 0;
      }
      // dx1 < 0
      if (dy2 > 0 || dy2 == 0 && dx2 > 0) {
        return 1;
      }
      if (dy2 < 0) {
        return -1;
      }
      return 0;
    }

    // now this direction and other are located in the same
    // open horizontal half plane

    double determinant = (double) dx2 * dy1 - (double) dy2 * dx1;
    return Signum.asInt(determinant);
  }

  /**
   * Calculates an approximation of the function value of this line at x, if the line is not
   * vertical.
   */
  public double functionValueApprox(double x) {
    FloatPoint p1 = a.toFloat();
    FloatPoint p2 = b.toFloat();
    double dx = p2.x - p1.x;
    if (dx == 0) {
      FRLogger.warn("function_value_approx: line is vertical");
      return 0;
    }
    double dy = p2.y - p1.y;
    double det = p1.x * p2.y - p2.x * p1.y;
    return (dy * x - det) / dx;
  }

  /**
   * Calculates an approximation of the function value in y of this line at y, if the line is not
   * horizontal.
   */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public double functionInYValueApprox(double y) {
    FloatPoint p1 = a.toFloat();
    FloatPoint p2 = b.toFloat();
    double dy = p2.y - p1.y;
    if (dy == 0) {
      FRLogger.warn("function_in_y_value_approx: line is horizontal");
      return 0;
    }
    double dx = p2.x - p1.x;
    double det = p1.x * p2.y - p2.x * p1.y;
    return (dx * y + det) / dy;
  }

  /**
   * Calculates the direction from fromPoint to the nearest point on this line to froPoint. Returns
   * null, if fromPoint is contained in this line.
   */
  public Direction perpendicularDirection(Point fromPoint) {
    Side lineSide = this.sideOf(fromPoint);
    if (lineSide == Side.COLLINEAR) {
      return null;
    }
    Direction dir1 = this.direction().turn45Degree(2);
    Direction dir2 = this.direction().turn45Degree(6);

    Point checkPoint1 = fromPoint.translateBy(dir1.getVector());
    if (this.sideOf(checkPoint1) != lineSide) {
      return dir1;
    }
    Point checkPoint2 = fromPoint.translateBy(dir2.getVector());
    if (this.sideOf(checkPoint2) != lineSide) {
      return dir2;
    }
    FloatPoint nearestLinePoint = fromPoint.toFloat().projectionApprox(this);
    Direction result;
    if (nearestLinePoint.distanceSquare(checkPoint1.toFloat())
        <= nearestLinePoint.distanceSquare(checkPoint2.toFloat())) {
      result = dir1;
    } else {
      result = dir2;
    }
    return result;
  }

  /** Turns this line by factor times 90 degree around pole. */
  public Line turn90Degree(int factor, IntPoint pole) {
    Point newA = a.turn90Degree(factor, pole);
    Point newB = b.turn90Degree(factor, pole);
    return new Line(newA, newB);
  }

  /** Mirrors this line at the vertical line through pole. */
  public Line mirrorVertical(IntPoint pole) {
    Point newA = b.mirrorVertical(pole);
    Point newB = a.mirrorVertical(pole);
    return new Line(newA, newB);
  }

  /** Mirrors this line at the horizontal line through pole. */
  public Line mirrorHorizontal(IntPoint pole) {
    Point newA = b.mirrorHorizontal(pole);
    Point newB = a.mirrorHorizontal(pole);
    return new Line(newA, newB);
  }

  /** Returns the Euclidean length of this line. */
  public float length() {
    IntPoint ipa = (IntPoint) a;
    IntPoint ipb = (IntPoint) b;

    double dx = (double) ipb.x - ipa.x;
    double dy = (double) ipb.y - ipa.y;
    return (float) Math.sqrt(dx * dx + dy * dy);
  }
}

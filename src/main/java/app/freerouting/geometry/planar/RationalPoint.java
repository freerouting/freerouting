package app.freerouting.geometry.planar;

import app.freerouting.datastructures.BigIntAux;
import java.io.Serializable;
import java.math.BigInteger;

/**
 * Implementation of points in the projective plane represented by 3 coordinates x, y, z, which are
 * infinite precision integers. Two projective points (x1, y1, z1) and (x2, y2 z2) are equal, if
 * they are located on the same line through the zero point, that means, there exist a number r with
 * x2 = r*x1, y2 = r*y1 and z2 = r*z1. The affine Point with rational coordinates represented by the
 * projective Point (x, y, z) is (x/z, y/z). The projective plane with integer coordinates contains
 * in addition to the affine plane with rational coordinates the so-called line at infinity, which
 * consist of all projective points (x, y, z) with z = 0.
 */
public final class RationalPoint extends Point implements Serializable {

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  final BigInteger x;

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  final BigInteger y;

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  final BigInteger z;

  /**
   * Creates a RationalPoint from 3 BigIntegers x, y and z. They represent the 2-dimensional point
   * with the rational number Tuple ( x / z , y / z). Throws IllegalArgumentException if denominator
   * z is <= 0
   */
  RationalPoint(BigInteger x, BigInteger y, BigInteger z) {
    this.x = x;
    this.y = y;
    this.z = z;
    if (z.signum() < 0) {
      throw new IllegalArgumentException("RationalPoint: z is expected to be >= 0");
    }
  }

  /** Creates a RationalPoint from an IntPoint. */
  RationalPoint(IntPoint point) {
    x = BigInteger.valueOf(point.x);
    y = BigInteger.valueOf(point.y);
    z = BigInteger.ONE;
  }

  /** Approximates the coordinates of this point by float coordinates. */
  @Override
  public FloatPoint toFloat() {
    double xd = x.doubleValue();
    double yd = y.doubleValue();
    double zd = z.doubleValue();
    if (zd == 0) {
      xd = Float.MAX_VALUE;
      yd = Float.MAX_VALUE;
    } else {
      xd /= zd;
      yd /= zd;
    }

    return new FloatPoint(xd, yd);
  }

  @Override
  public int getId() {
    int result = x.hashCode();
    result = 31 * result + y.hashCode();
    return 31 * result + z.hashCode();
  }

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
    RationalPoint otherPoint = (RationalPoint) other;
    BigInteger det = BigIntAux.determinant(x, otherPoint.x, z, otherPoint.z);
    if (det.signum() != 0) {
      return false;
    }
    det = BigIntAux.determinant(y, otherPoint.y, z, otherPoint.z);
    return det.signum() == 0;
  }

  @Override
  public int hashCode() {
    if (z.signum() == 0) {
      return 0;
    }
    BigInteger gcd = x.abs().gcd(y.abs()).gcd(z);
    BigInteger rx = x;
    BigInteger ry = y;
    BigInteger rz = z;
    if (gcd.compareTo(BigInteger.ONE) > 0) {
      rx = x.divide(gcd);
      ry = y.divide(gcd);
      rz = z.divide(gcd);
    }
    int result = rx.hashCode();
    result = 31 * result + ry.hashCode();
    return 31 * result + rz.hashCode();
  }

  @Override
  public boolean isInfinite() {
    return z.signum() == 0;
  }

  @Override
  public IntBox surroundingBox() {
    FloatPoint fp = toFloat();
    int llx = (int) Math.floor(fp.x);
    int lly = (int) Math.floor(fp.y);
    int urx = (int) Math.ceil(fp.x);
    int ury = (int) Math.ceil(fp.y);
    return new IntBox(llx, lly, urx, ury);
  }

  @Override
  public IntOctagon surroundingOctagon() {
    FloatPoint fp = toFloat();
    int lx = (int) Math.floor(fp.x);
    int ly = (int) Math.floor(fp.y);
    int rx = (int) Math.ceil(fp.x);
    int uy = (int) Math.ceil(fp.y);

    double tmp = fp.x - fp.y;
    int ulx = (int) Math.floor(tmp);
    int lrx = (int) Math.ceil(tmp);

    tmp = fp.x + fp.y;
    int llx = (int) Math.floor(tmp);
    int urx = (int) Math.ceil(tmp);
    return new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
  }

  @Override
  public boolean isContainedIn(IntBox box) {
    BigInteger tmp = BigInteger.valueOf(box.ll.x).multiply(z);
    if (x.compareTo(tmp) < 0) {
      return false;
    }
    tmp = BigInteger.valueOf(box.ll.y).multiply(z);
    if (y.compareTo(tmp) < 0) {
      return false;
    }
    tmp = BigInteger.valueOf(box.ur.x).multiply(z);
    if (x.compareTo(tmp) > 0) {
      return false;
    }
    tmp = BigInteger.valueOf(box.ur.y).multiply(z);
    return y.compareTo(tmp) <= 0;
  }

  /** Returns the translation of this point by vector. */
  @Override
  public Point translateBy(Vector vector) {
    if (vector.equals(Vector.ZERO)) {
      return this;
    }
    return vector.addTo(this);
  }

  @Override
  Point translateBy(IntVector vector) {
    BigIntAux.RationalCoordinates result =
        BigIntAux.addRationalCoordinates(
            x, y, z, BigInteger.valueOf(vector.x), BigInteger.valueOf(vector.y), BigInteger.ONE);
    return new RationalPoint(result.x(), result.y(), result.z());
  }

  @Override
  Point translateBy(RationalVector vector) {
    BigIntAux.RationalCoordinates result =
        BigIntAux.addRationalCoordinates(x, y, z, vector.x, vector.y, vector.z);
    return new RationalPoint(result.x(), result.y(), result.z());
  }

  /** Returns the difference vector of this point and other. */
  @Override
  public Vector differenceBy(Point other) {
    if (other instanceof IntPoint integer) {
      return differenceBy(integer);
    }
    return differenceBy((RationalPoint) other);
  }

  @Override
  Vector differenceBy(IntPoint other) {
    BigIntAux.RationalCoordinates result =
        BigIntAux.addRationalCoordinates(
            x, y, z, BigInteger.valueOf(-other.x), BigInteger.valueOf(-other.y), BigInteger.ONE);
    return new RationalVector(result.x(), result.y(), result.z());
  }

  @Override
  Vector differenceBy(RationalPoint other) {
    BigIntAux.RationalCoordinates result =
        BigIntAux.addRationalCoordinates(x, y, z, other.x.negate(), other.y.negate(), other.z);
    return new RationalVector(result.x(), result.y(), result.z());
  }

  /**
   * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from 1 to 2;
   * Side.ON_THE_RIGHT, if this Point is on the right f the line from 1 to 2; and Side.COLLINEAR, if
   * this Point is collinear with 1 and 2.
   */
  @Override
  public Side sideOf(Point p1, Point p2) {
    BigIntAux.RationalCoordinates a = coordinates(p1);
    BigIntAux.RationalCoordinates b = coordinates(p2);
    BigIntAux.RationalCoordinates v1 =
        BigIntAux.addRationalCoordinates(x, y, z, a.x().negate(), a.y().negate(), a.z());
    if (p1 instanceof IntPoint i1 && p2 instanceof IntPoint i2) {
      BigInteger crossProduct =
          v1.y()
              .multiply(BigInteger.valueOf((long) i2.x - i1.x))
              .subtract(v1.x().multiply(BigInteger.valueOf((long) i2.y - i1.y)));
      return Side.of(crossProduct.signum());
    }
    BigIntAux.RationalCoordinates v2 =
        BigIntAux.addRationalCoordinates(
            b.x(), b.y(), b.z(), a.x().negate(), a.y().negate(), a.z());
    BigInteger crossProduct = v1.y().multiply(v2.x()).subtract(v1.x().multiply(v2.y()));
    return Side.of(crossProduct.signum());
  }

  private static BigIntAux.RationalCoordinates coordinates(Point point) {
    if (point instanceof IntPoint p) {
      return new BigIntAux.RationalCoordinates(
          BigInteger.valueOf(p.x), BigInteger.valueOf(p.y), BigInteger.ONE);
    }
    if (point instanceof RationalPoint p) {
      return new BigIntAux.RationalCoordinates(p.x, p.y, p.z);
    }
    throw new IllegalArgumentException("Unsupported point type: " + point.getClass().getName());
  }

  @Override
  public Side sideOf(Line line) {
    return sideOf(line.a, line.b);
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

    BigInteger tmp1 = vxvx.multiply(x);
    BigInteger tmp2 = vxvy.multiply(y);
    tmp1 = tmp1.add(tmp2);
    tmp2 = det.multiply(BigInteger.valueOf(v.y));
    tmp2 = tmp2.multiply(z);
    BigInteger projX = tmp1.add(tmp2);

    tmp1 = vxvy.multiply(x);
    tmp2 = vyvy.multiply(y);
    tmp1 = tmp1.add(tmp2);
    tmp2 = det.multiply(BigInteger.valueOf(v.x));
    tmp2 = tmp2.multiply(z);
    BigInteger projY = tmp1.add(tmp2);

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
        if (projX.abs().compareTo(Limits.CRIT_INT_BIG) <= 0
            && projY.abs().compareTo(Limits.CRIT_INT_BIG) <= 0) {
          return new IntPoint(projX.intValue(), projY.intValue());
        }
        denominator = BigInteger.ONE;
      }
    }
    return new RationalPoint(projX, projY, denominator);
  }

  @Override
  public int compareX(Point other) {
    return -other.compareX(this);
  }

  @Override
  int compareX(RationalPoint other) {
    BigInteger tmp1 = this.x.multiply(other.z);
    BigInteger tmp2 = other.x.multiply(this.z);
    return tmp1.compareTo(tmp2);
  }

  @Override
  int compareX(IntPoint other) {
    BigInteger tmp1 = this.z.multiply(BigInteger.valueOf(other.x));
    return this.x.compareTo(tmp1);
  }

  @Override
  public int compareY(Point other) {
    return -other.compareY(this);
  }

  @Override
  int compareY(RationalPoint other) {
    BigInteger tmp1 = this.y.multiply(other.z);
    BigInteger tmp2 = other.y.multiply(this.z);
    return tmp1.compareTo(tmp2);
  }

  @Override
  int compareY(IntPoint other) {
    BigInteger tmp1 = this.z.multiply(BigInteger.valueOf(other.y));
    return this.y.compareTo(tmp1);
  }
}

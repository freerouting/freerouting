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
public class RationalPoint extends Point implements Serializable {

  final BigInteger x;
  final BigInteger y;
  final BigInteger z;

  /**
   * creates a RationalPoint from 3 BigIntegers p_x, p_y and p_z. They represent the 2-dimensional
   * point with the rational number Tuple ( p_x / p_z , p_y / p_z). Throws IllegalArgumentException
   * if denominator p_z is <= 0
   */
  RationalPoint(BigInteger p_x, BigInteger p_y, BigInteger p_z) {
    x = p_x;
    y = p_y;
    z = p_z;
    if (p_z.signum() < 0) {
      throw new IllegalArgumentException("RationalPoint: p_z is expected to be >= 0");
    }
  }

  /** creates a RationalPoint from an IntPoint */
  RationalPoint(IntPoint p_point) {
    x = BigInteger.valueOf(p_point.x);
    y = BigInteger.valueOf(p_point.y);
    z = BigInteger.ONE;
  }

  /** approximates the coordinates of this point by float coordinates */
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

  /** returns true, if this RationalPoint is equal to p_ob */
  @Override
  public int getIdNo() {
    int result = x.hashCode();
    result = 31 * result + y.hashCode();
    return 31 * result + z.hashCode();
  }

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
    RationalPoint other = (RationalPoint) p_ob;
    BigInteger det = BigIntAux.determinant(x, other.x, z, other.z);
    if (det.signum() != 0) {
      return false;
    }
    det = BigIntAux.determinant(y, other.y, z, other.z);

    return det.signum() == 0;
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
  public boolean isContainedIn(IntBox p_box) {
    BigInteger tmp = BigInteger.valueOf(p_box.ll.x).multiply(z);
    if (x.compareTo(tmp) < 0) {
      return false;
    }
    tmp = BigInteger.valueOf(p_box.ll.y).multiply(z);
    if (y.compareTo(tmp) < 0) {
      return false;
    }
    tmp = BigInteger.valueOf(p_box.ur.x).multiply(z);
    if (x.compareTo(tmp) > 0) {
      return false;
    }
    tmp = BigInteger.valueOf(p_box.ur.y).multiply(z);
    return y.compareTo(tmp) <= 0;
  }

  /** returns the translation of this point by p_vector */
  @Override
  public Point translateBy(Vector p_vector) {
    if (p_vector.equals(Vector.ZERO)) {
      return this;
    }
    return p_vector.addTo(this);
  }

  @Override
  Point translateBy(IntVector p_vector) {
    RationalVector vector = new RationalVector(p_vector);
    return translateBy(vector);
  }

  @Override
  Point translateBy(RationalVector p_vector) {
    BigInteger[] v1 = new BigInteger[3];
    v1[0] = x;
    v1[1] = y;
    v1[2] = z;

    BigInteger[] v2 = new BigInteger[3];
    v2[0] = p_vector.x;
    v2[1] = p_vector.y;
    v2[2] = p_vector.z;
    BigInteger[] result = BigIntAux.addRationalCoordinates(v1, v2);
    return new RationalPoint(result[0], result[1], result[2]);
  }

  /** returns the difference vector of this point and p_other */
  @Override
  public Vector differenceBy(Point p_other) {
    Vector tmp = p_other.differenceBy(this);
    return tmp.negate();
  }

  @Override
  Vector differenceBy(IntPoint p_other) {
    RationalPoint other = new RationalPoint(p_other);
    return differenceBy(other);
  }

  @Override
  Vector differenceBy(RationalPoint p_other) {
    BigInteger[] v1 = new BigInteger[3];
    v1[0] = x;
    v1[1] = y;
    v1[2] = z;

    BigInteger[] v2 = new BigInteger[3];
    v2[0] = p_other.x.negate();
    v2[1] = p_other.y.negate();
    v2[2] = p_other.z;
    BigInteger[] result = BigIntAux.addRationalCoordinates(v1, v2);
    return new RationalVector(result[0], result[1], result[2]);
  }

  /**
   * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to
   * p_2; Side.ON_THE_RIGHT, if this Point is on the right f the line from p_1 to p_2; and
   * Side.COLLINEAR, if this Point is collinear with p_1 and p_2.
   */
  @Override
  public Side sideOf(Point p_1, Point p_2) {
    Vector v1 = differenceBy(p_1);
    Vector v2 = p_2.differenceBy(p_1);
    return v1.sideOf(v2);
  }

  @Override
  public Side sideOf(Line p_line) {
    return sideOf(p_line.a, p_line.b);
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
  public int compareX(Point p_other) {
    return -p_other.compareX(this);
  }

  @Override
  public int compareY(Point p_other) {
    return -p_other.compareY(this);
  }

  @Override
  int compareX(RationalPoint p_other) {
    BigInteger tmp1 = this.x.multiply(p_other.z);
    BigInteger tmp2 = p_other.x.multiply(this.z);
    return tmp1.compareTo(tmp2);
  }

  @Override
  int compareY(RationalPoint p_other) {
    BigInteger tmp1 = this.y.multiply(p_other.z);
    BigInteger tmp2 = p_other.y.multiply(this.z);
    return tmp1.compareTo(tmp2);
  }

  @Override
  int compareX(IntPoint p_other) {
    BigInteger tmp1 = this.z.multiply(BigInteger.valueOf(p_other.x));
    return this.x.compareTo(tmp1);
  }

  @Override
  int compareY(IntPoint p_other) {
    BigInteger tmp1 = this.z.multiply(BigInteger.valueOf(p_other.y));
    return this.y.compareTo(tmp1);
  }
}

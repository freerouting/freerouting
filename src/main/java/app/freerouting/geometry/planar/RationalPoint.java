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
  RationalPoint(BigInteger pX, BigInteger pY, BigInteger pZ) {
    x = pX;
    y = pY;
    z = pZ;
    if (pZ.signum() < 0) {
      throw new IllegalArgumentException("RationalPoint: p_z is expected to be >= 0");
    }
  }

  /** creates a RationalPoint from an IntPoint */
  RationalPoint(IntPoint pPoint) {
    x = BigInteger.valueOf(pPoint.x);
    y = BigInteger.valueOf(pPoint.y);
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
    RationalPoint other = (RationalPoint) pOb;
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
  public boolean isContainedIn(IntBox pBox) {
    BigInteger tmp = BigInteger.valueOf(pBox.ll.x).multiply(z);
    if (x.compareTo(tmp) < 0) {
      return false;
    }
    tmp = BigInteger.valueOf(pBox.ll.y).multiply(z);
    if (y.compareTo(tmp) < 0) {
      return false;
    }
    tmp = BigInteger.valueOf(pBox.ur.x).multiply(z);
    if (x.compareTo(tmp) > 0) {
      return false;
    }
    tmp = BigInteger.valueOf(pBox.ur.y).multiply(z);
    return y.compareTo(tmp) <= 0;
  }

  /** returns the translation of this point by p_vector */
  @Override
  public Point translateBy(Vector pVector) {
    if (pVector.equals(Vector.ZERO)) {
      return this;
    }
    return pVector.addTo(this);
  }

  @Override
  Point translateBy(IntVector pVector) {
    RationalVector vector = new RationalVector(pVector);
    return translateBy(vector);
  }

  @Override
  Point translateBy(RationalVector pVector) {
    BigInteger[] v1 = new BigInteger[3];
    v1[0] = x;
    v1[1] = y;
    v1[2] = z;

    BigInteger[] v2 = new BigInteger[3];
    v2[0] = pVector.x;
    v2[1] = pVector.y;
    v2[2] = pVector.z;
    BigInteger[] result = BigIntAux.addRationalCoordinates(v1, v2);
    return new RationalPoint(result[0], result[1], result[2]);
  }

  /** returns the difference vector of this point and p_other */
  @Override
  public Vector differenceBy(Point pOther) {
    Vector tmp = pOther.differenceBy(this);
    return tmp.negate();
  }

  @Override
  Vector differenceBy(IntPoint pOther) {
    RationalPoint other = new RationalPoint(pOther);
    return differenceBy(other);
  }

  @Override
  Vector differenceBy(RationalPoint pOther) {
    BigInteger[] v1 = new BigInteger[3];
    v1[0] = x;
    v1[1] = y;
    v1[2] = z;

    BigInteger[] v2 = new BigInteger[3];
    v2[0] = pOther.x.negate();
    v2[1] = pOther.y.negate();
    v2[2] = pOther.z;
    BigInteger[] result = BigIntAux.addRationalCoordinates(v1, v2);
    return new RationalVector(result[0], result[1], result[2]);
  }

  /**
   * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to
   * p_2; Side.ON_THE_RIGHT, if this Point is on the right f the line from p_1 to p_2; and
   * Side.COLLINEAR, if this Point is collinear with p_1 and p_2.
   */
  @Override
  public Side sideOf(Point p1, Point p2) {
    Vector v1 = differenceBy(p1);
    Vector v2 = p2.differenceBy(p1);
    return v1.sideOf(v2);
  }

  @Override
  public Side sideOf(Line pLine) {
    return sideOf(pLine.a, pLine.b);
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
  public int compareX(Point pOther) {
    return -pOther.compareX(this);
  }

  @Override
  public int compareY(Point pOther) {
    return -pOther.compareY(this);
  }

  @Override
  int compareX(RationalPoint pOther) {
    BigInteger tmp1 = this.x.multiply(pOther.z);
    BigInteger tmp2 = pOther.x.multiply(this.z);
    return tmp1.compareTo(tmp2);
  }

  @Override
  int compareY(RationalPoint pOther) {
    BigInteger tmp1 = this.y.multiply(pOther.z);
    BigInteger tmp2 = pOther.y.multiply(this.z);
    return tmp1.compareTo(tmp2);
  }

  @Override
  int compareX(IntPoint pOther) {
    BigInteger tmp1 = this.z.multiply(BigInteger.valueOf(pOther.x));
    return this.x.compareTo(tmp1);
  }

  @Override
  int compareY(IntPoint pOther) {
    BigInteger tmp1 = this.z.multiply(BigInteger.valueOf(pOther.y));
    return this.y.compareTo(tmp1);
  }
}

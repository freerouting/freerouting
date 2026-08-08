package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;

/**
 * Defines a line in the plane by to FloatPoints. Calculations with FloatLines are generally not
 * exact. For that reason collinear for example is not defined for FloatLines. If exactness is
 * needed, use the class Line instead.
 */
public class FloatLine {

  public final FloatPoint a;
  public final FloatPoint b;

  /** Creates a line from two FloatPoints. */
  public FloatLine(FloatPoint pA, FloatPoint pB) {
    if (pA == null || pB == null) {
      FRLogger.debug("FloatLine: one or both endpoints are null (degenerate line segment)");
    }
    a = pA;
    b = pB;
  }

  /** Returns the FloatLine with swapped end points. */
  public FloatLine opposite() {
    return new FloatLine(this.b, this.a);
  }

  public FloatLine adjustDirection(FloatLine pOther) {
    if (this.b.sideOf(this.a, pOther.a) == pOther.b.sideOf(this.a, pOther.a)) {
      return this;
    }
    return this.opposite();
  }

  /**
   * Calculates the intersection of this line with p_other. Returns null, if the lines are parallel.
   */
  public FloatPoint intersection(FloatLine pOther) {
    double d1x = this.b.x - this.a.x;
    double d1y = this.b.y - this.a.y;
    double d2x = pOther.b.x - pOther.a.x;
    double d2y = pOther.b.y - pOther.a.y;
    double det1 = this.a.x * this.b.y - this.a.y * this.b.x;
    double det2 = pOther.a.x * pOther.b.y - pOther.a.y * pOther.b.x;
    double det = d2x * d1y - d2y * d1x;
    double isX;
    double isY;
    if (det == 0) {
      return null;
    }
    isX = (d2x * det1 - d1x * det2) / det;
    isY = (d2y * det1 - d1y * det2) / det;
    return new FloatPoint(isX, isY);
  }

  /**
   * translates the line perpendicular at about p_dist. If p_dist {@literal >} 0, the line will be
   * translated to the left, else to the right
   */
  public FloatLine translate(double pDist) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double dxdx = dx * dx;
    double dydy = dy * dy;
    double length = Math.sqrt(dxdx + dydy);
    FloatPoint newA;
    if (dxdx <= dydy) {
      // translate along the x axis
      double relX = (pDist * length) / dy;
      newA = new FloatPoint(this.a.x - relX, this.a.y);
    } else {
      // translate along the y axis
      double relY = (pDist * length) / dx;
      newA = new FloatPoint(this.a.x, this.a.y + relY);
    }
    FloatPoint newB = new FloatPoint(newA.x + dx, newA.y + dy);
    return new FloatLine(newA, newB);
  }

  /**
   * Returns the signed distance of this line from p_point. The result will be positive, if the line
   * is on the left of p_point, else negative.
   */
  public double signedDistance(FloatPoint pPoint) {
    double dx = this.b.x - this.a.x;
    double dy = this.b.y - this.a.y;
    double det = dy * (pPoint.x - this.a.x) - dx * (pPoint.y - this.a.y);
    // area of the parallelogramm spanned by the 3 points
    double length = Math.sqrt(dx * dx + dy * dy);
    return det / length;
  }

  /** Returns an approximation of the perpensicular projection of p_point onto this line. */
  public FloatPoint perpendicularProjection(FloatPoint pPoint) {

    double dx = b.x - a.x;
    double dy = b.y - a.y;
    if (dx == 0 && dy == 0) {
      return this.a;
    }

    double dxdx = dx * dx;
    double dydy = dy * dy;
    double dxdy = dx * dy;
    double denominator = dxdx + dydy;
    double det = a.x * b.y - b.x * a.y;

    double x = (pPoint.x * dxdx + pPoint.y * dxdy + det * dy) / denominator;
    double y = (pPoint.x * dxdy + pPoint.y * dydy - det * dx) / denominator;

    return new FloatPoint(x, y);
  }

  /**
   * Returns the distance of p_point to the nearest point of this line between this.a and this.b.
   */
  public double segmentDistance(FloatPoint pPoint) {
    FloatPoint projection = perpendicularProjection(pPoint);
    double result;
    if (projection.isContainedInBox(this.a, this.b, 0.01)) {
      result = pPoint.distance(projection);
    } else {
      result = Math.min(pPoint.distance(a), pPoint.distance(b));
    }
    return result;
  }

  /**
   * Returns the perpendicular projection of p_line_segment onto this oriented line segment, Returns
   * null, if the projection is empty.
   */
  public FloatLine segmentProjection(FloatLine pLineSegment) {
    if (this.b.scalarProduct(this.a, pLineSegment.a) < 0) {
      return null;
    }
    if (this.a.scalarProduct(this.b, pLineSegment.b) < 0) {
      return null;
    }
    FloatPoint projectedA;
    if (this.a.scalarProduct(this.b, pLineSegment.a) < 0) {
      projectedA = this.a;
    } else {
      projectedA = this.perpendicularProjection(pLineSegment.a);
      if (Math.abs(projectedA.x) >= Limits.CRIT_INT || Math.abs(projectedA.y) >= Limits.CRIT_INT) {
        return null;
      }
    }
    FloatPoint projectedB;
    if (this.b.scalarProduct(this.a, pLineSegment.b) < 0) {
      projectedB = this.b;
    } else {
      projectedB = this.perpendicularProjection(pLineSegment.b);
    }
    if (Math.abs(projectedB.x) >= Limits.CRIT_INT || Math.abs(projectedB.y) >= Limits.CRIT_INT) {
      return null;
    }
    return new FloatLine(projectedA, projectedB);
  }

  /**
   * Returns the projection of p_line_segment onto this oriented line segment by moving
   * p_line_segment perpendicular into the direction of this line segment Returns null, if the
   * projection is empty or p_line_segment.a == p_line_segment.b
   */
  public FloatLine segmentProjection2(FloatLine pLineSegment) {
    if (pLineSegment.a.scalarProduct(pLineSegment.b, this.b) <= 0) {
      return null;
    }
    if (pLineSegment.b.scalarProduct(pLineSegment.a, this.a) <= 0) {
      return null;
    }
    FloatPoint projectedA;
    if (pLineSegment.a.scalarProduct(pLineSegment.b, this.a) < 0) {
      FloatLine currPerpendicularLine =
          new FloatLine(pLineSegment.a, pLineSegment.b.turn90Degree(1, pLineSegment.a));
      projectedA = currPerpendicularLine.intersection(this);
      if (projectedA == null
          || Math.abs(projectedA.x) >= Limits.CRIT_INT
          || Math.abs(projectedA.y) >= Limits.CRIT_INT) {
        return null;
      }
    } else {
      projectedA = this.a;
    }

    FloatPoint projectedB;

    if (pLineSegment.b.scalarProduct(pLineSegment.a, this.b) < 0) {
      FloatLine currPerpendicularLine =
          new FloatLine(pLineSegment.b, pLineSegment.a.turn90Degree(1, pLineSegment.b));
      projectedB = currPerpendicularLine.intersection(this);
      if (projectedB == null
          || Math.abs(projectedB.x) >= Limits.CRIT_INT
          || Math.abs(projectedB.y) >= Limits.CRIT_INT) {
        return null;
      }
    } else {
      projectedB = this.b;
    }
    return new FloatLine(projectedA, projectedB);
  }

  /**
   * Shrinks this line on both sides by p_value. The result will contain at least the gravity point
   * of the line.
   */
  public FloatLine shrinkSegment(double pOffset) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    if (dx == 0 && dy == 0) {
      return this;
    }
    double length = Math.sqrt(dx * dx + dy * dy);
    double offset = Math.min(pOffset, length / 2);
    FloatPoint newA = new FloatPoint(a.x + (dx * offset) / length, a.y + (dy * offset) / length);
    double newLength = length - offset;
    FloatPoint newB =
        new FloatPoint(a.x + (dx * newLength) / length, a.y + (dy * newLength) / length);
    return new FloatLine(newA, newB);
  }

  /** Calculates the nearest point on this line to p_from_point between this.a and this.b. */
  public FloatPoint nearestSegmentPoint(FloatPoint pFromPoint) {
    FloatPoint projection = this.perpendicularProjection(pFromPoint);
    if (projection.isContainedInBox(this.a, this.b, 0.01)) {
      return projection;
    }
    // Now the projection is outside the line segment.
    FloatPoint result;
    if (pFromPoint.distanceSquare(this.a) <= pFromPoint.distanceSquare(this.b)) {
      result = this.a;
    } else {
      result = this.b;
    }
    return result;
  }

  /**
   * Divides this line segment into p_count line segments of nearly equal length. and at most
   * p_max_section_length.
   */
  public FloatLine[] divideSegmentIntoSections(int pCount) {
    if (pCount == 0) {
      return new FloatLine[0];
    }
    if (pCount == 1) {
      FloatLine[] result = new FloatLine[1];
      result[0] = this;
      return result;
    }
    double lineLength = this.b.distance(this.a);
    FloatLine[] result = new FloatLine[pCount];
    double sectionLength = lineLength / pCount;
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    FloatPoint currA = this.a;
    for (int i = 0; i < pCount; i++) {
      FloatPoint currB;
      if (i == pCount - 1) {
        currB = this.b;
      } else {
        double currBDist = (i + 1) * sectionLength;
        double currBX = a.x + (dx * currBDist) / lineLength;
        double currBY = a.y + (dy * currBDist) / lineLength;
        currB = new FloatPoint(currBX, currBY);
      }
      result[i] = new FloatLine(currA, currB);
      currA = currB;
    }
    return result;
  }
}

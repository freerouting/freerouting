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
  public FloatLine(FloatPoint p_a, FloatPoint p_b) {
    if (p_a == null || p_b == null) {
      FRLogger.debug("FloatLine: one or both endpoints are null (degenerate line segment)");
    }
    a = p_a;
    b = p_b;
  }

  /** Returns the FloatLine with swapped end points. */
  public FloatLine opposite() {
    return new FloatLine(this.b, this.a);
  }

  public FloatLine adjustDirection(FloatLine p_other) {
    if (this.b.sideOf(this.a, p_other.a) == p_other.b.sideOf(this.a, p_other.a)) {
      return this;
    }
    return this.opposite();
  }

  /**
   * Calculates the intersection of this line with p_other. Returns null, if the lines are parallel.
   */
  public FloatPoint intersection(FloatLine p_other) {
    double d1x = this.b.x - this.a.x;
    double d1y = this.b.y - this.a.y;
    double d2x = p_other.b.x - p_other.a.x;
    double d2y = p_other.b.y - p_other.a.y;
    double det1 = this.a.x * this.b.y - this.a.y * this.b.x;
    double det2 = p_other.a.x * p_other.b.y - p_other.a.y * p_other.b.x;
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
  public FloatLine translate(double p_dist) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double dxdx = dx * dx;
    double dydy = dy * dy;
    double length = Math.sqrt(dxdx + dydy);
    FloatPoint newA;
    if (dxdx <= dydy) {
      // translate along the x axis
      double relX = (p_dist * length) / dy;
      newA = new FloatPoint(this.a.x - relX, this.a.y);
    } else {
      // translate along the y axis
      double relY = (p_dist * length) / dx;
      newA = new FloatPoint(this.a.x, this.a.y + relY);
    }
    FloatPoint newB = new FloatPoint(newA.x + dx, newA.y + dy);
    return new FloatLine(newA, newB);
  }

  /**
   * Returns the signed distance of this line from p_point. The result will be positive, if the line
   * is on the left of p_point, else negative.
   */
  public double signedDistance(FloatPoint p_point) {
    double dx = this.b.x - this.a.x;
    double dy = this.b.y - this.a.y;
    double det = dy * (p_point.x - this.a.x) - dx * (p_point.y - this.a.y);
    // area of the parallelogramm spanned by the 3 points
    double length = Math.sqrt(dx * dx + dy * dy);
    return det / length;
  }

  /** Returns an approximation of the perpensicular projection of p_point onto this line. */
  public FloatPoint perpendicularProjection(FloatPoint p_point) {

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

    double x = (p_point.x * dxdx + p_point.y * dxdy + det * dy) / denominator;
    double y = (p_point.x * dxdy + p_point.y * dydy - det * dx) / denominator;

    return new FloatPoint(x, y);
  }

  /**
   * Returns the distance of p_point to the nearest point of this line between this.a and this.b.
   */
  public double segmentDistance(FloatPoint p_point) {
    FloatPoint projection = perpendicularProjection(p_point);
    double result;
    if (projection.isContainedInBox(this.a, this.b, 0.01)) {
      result = p_point.distance(projection);
    } else {
      result = Math.min(p_point.distance(a), p_point.distance(b));
    }
    return result;
  }

  /**
   * Returns the perpendicular projection of p_line_segment onto this oriented line segment, Returns
   * null, if the projection is empty.
   */
  public FloatLine segmentProjection(FloatLine p_line_segment) {
    if (this.b.scalarProduct(this.a, p_line_segment.a) < 0) {
      return null;
    }
    if (this.a.scalarProduct(this.b, p_line_segment.b) < 0) {
      return null;
    }
    FloatPoint projectedA;
    if (this.a.scalarProduct(this.b, p_line_segment.a) < 0) {
      projectedA = this.a;
    } else {
      projectedA = this.perpendicularProjection(p_line_segment.a);
      if (Math.abs(projectedA.x) >= Limits.CRIT_INT || Math.abs(projectedA.y) >= Limits.CRIT_INT) {
        return null;
      }
    }
    FloatPoint projectedB;
    if (this.b.scalarProduct(this.a, p_line_segment.b) < 0) {
      projectedB = this.b;
    } else {
      projectedB = this.perpendicularProjection(p_line_segment.b);
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
  public FloatLine segmentProjection2(FloatLine p_line_segment) {
    if (p_line_segment.a.scalarProduct(p_line_segment.b, this.b) <= 0) {
      return null;
    }
    if (p_line_segment.b.scalarProduct(p_line_segment.a, this.a) <= 0) {
      return null;
    }
    FloatPoint projectedA;
    if (p_line_segment.a.scalarProduct(p_line_segment.b, this.a) < 0) {
      FloatLine currPerpendicularLine =
          new FloatLine(p_line_segment.a, p_line_segment.b.turn90Degree(1, p_line_segment.a));
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

    if (p_line_segment.b.scalarProduct(p_line_segment.a, this.b) < 0) {
      FloatLine currPerpendicularLine =
          new FloatLine(p_line_segment.b, p_line_segment.a.turn90Degree(1, p_line_segment.b));
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
  public FloatLine shrinkSegment(double p_offset) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    if (dx == 0 && dy == 0) {
      return this;
    }
    double length = Math.sqrt(dx * dx + dy * dy);
    double offset = Math.min(p_offset, length / 2);
    FloatPoint newA = new FloatPoint(a.x + (dx * offset) / length, a.y + (dy * offset) / length);
    double newLength = length - offset;
    FloatPoint newB =
        new FloatPoint(a.x + (dx * newLength) / length, a.y + (dy * newLength) / length);
    return new FloatLine(newA, newB);
  }

  /** Calculates the nearest point on this line to p_from_point between this.a and this.b. */
  public FloatPoint nearestSegmentPoint(FloatPoint p_from_point) {
    FloatPoint projection = this.perpendicularProjection(p_from_point);
    if (projection.isContainedInBox(this.a, this.b, 0.01)) {
      return projection;
    }
    // Now the projection is outside the line segment.
    FloatPoint result;
    if (p_from_point.distanceSquare(this.a) <= p_from_point.distanceSquare(this.b)) {
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
  public FloatLine[] divideSegmentIntoSections(int p_count) {
    if (p_count == 0) {
      return new FloatLine[0];
    }
    if (p_count == 1) {
      FloatLine[] result = new FloatLine[1];
      result[0] = this;
      return result;
    }
    double lineLength = this.b.distance(this.a);
    FloatLine[] result = new FloatLine[p_count];
    double sectionLength = lineLength / p_count;
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    FloatPoint currA = this.a;
    for (int i = 0; i < p_count; i++) {
      FloatPoint currB;
      if (i == p_count - 1) {
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

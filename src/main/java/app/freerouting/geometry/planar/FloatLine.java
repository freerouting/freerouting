package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;

/**
 * Defines a line in the plane by to FloatPoints. Calculations with FloatLines are generally not
 * exact. For that reason collinear for example is not defined for FloatLines. If exactness is
 * needed, use the class Line instead.
 */
public class FloatLine {

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final FloatPoint a;

  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final FloatPoint b;

  /** Creates a line from two FloatPoints. */
  public FloatLine(FloatPoint a, FloatPoint b) {
    if (a == null || b == null) {
      FRLogger.debug("FloatLine: one or both endpoints are null (degenerate line segment)");
    }
    this.a = a;
    this.b = b;
  }

  /** Returns the FloatLine with swapped end points. */
  public FloatLine opposite() {
    return new FloatLine(this.b, this.a);
  }

  /** Adjusts this line's direction to match the orientation of another line. */
  public FloatLine adjustDirection(FloatLine other) {
    if (this.b.sideOf(this.a, other.a) == other.b.sideOf(this.a, other.a)) {
      return this;
    }
    return this.opposite();
  }

  /**
   * Calculates the intersection of this line with p_other. Returns null, if the lines are parallel.
   */
  public FloatPoint intersection(FloatLine other) {
    double d1x = this.b.x - this.a.x;
    double d1y = this.b.y - this.a.y;
    double d2x = other.b.x - other.a.x;
    double d2y = other.b.y - other.a.y;
    double det1 = this.a.x * this.b.y - this.a.y * this.b.x;
    double det2 = other.a.x * other.b.y - other.a.y * other.b.x;
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
   * Translates the line perpendicular at about p_dist. If p_dist {@literal >} 0, the line will be
   * translated to the left, else to the right.
   */
  public FloatLine translate(double dist) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    double dxdx = dx * dx;
    double dydy = dy * dy;
    double length = Math.sqrt(dxdx + dydy);
    FloatPoint newA;
    if (dxdx <= dydy) {
      // translate along the x axis
      double relX = (dist * length) / dy;
      newA = new FloatPoint(this.a.x - relX, this.a.y);
    } else {
      // translate along the y axis
      double relY = (dist * length) / dx;
      newA = new FloatPoint(this.a.x, this.a.y + relY);
    }
    FloatPoint newB = new FloatPoint(newA.x + dx, newA.y + dy);
    return new FloatLine(newA, newB);
  }

  /**
   * Returns the signed distance of this line from p_point. The result will be positive, if the line
   * is on the left of p_point, else negative.
   */
  public double signedDistance(FloatPoint point) {
    double dx = this.b.x - this.a.x;
    double dy = this.b.y - this.a.y;
    double det = dy * (point.x - this.a.x) - dx * (point.y - this.a.y);
    // area of the parallelogramm spanned by the 3 points
    double length = Math.sqrt(dx * dx + dy * dy);
    return det / length;
  }

  /** Returns an approximation of the perpendicular projection of p_point onto this line. */
  public FloatPoint perpendicularProjection(FloatPoint point) {

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

    double x = (point.x * dxdx + point.y * dxdy + det * dy) / denominator;
    double y = (point.x * dxdy + point.y * dydy - det * dx) / denominator;

    return new FloatPoint(x, y);
  }

  /**
   * Returns the distance of p_point to the nearest point of this line between this.a and this.b.
   */
  public double segmentDistance(FloatPoint point) {
    FloatPoint projection = perpendicularProjection(point);
    double result;
    if (projection.isContainedInBox(this.a, this.b, 0.01)) {
      result = point.distance(projection);
    } else {
      result = Math.min(point.distance(a), point.distance(b));
    }
    return result;
  }

  /**
   * Returns the perpendicular projection of p_line_segment onto this oriented line segment, Returns
   * null, if the projection is empty.
   */
  public FloatLine segmentProjection(FloatLine lineSegment) {
    if (this.b.scalarProduct(this.a, lineSegment.a) < 0) {
      return null;
    }
    if (this.a.scalarProduct(this.b, lineSegment.b) < 0) {
      return null;
    }
    FloatPoint projectedA;
    if (this.a.scalarProduct(this.b, lineSegment.a) < 0) {
      projectedA = this.a;
    } else {
      projectedA = this.perpendicularProjection(lineSegment.a);
      if (Math.abs(projectedA.x) >= Limits.CRIT_INT || Math.abs(projectedA.y) >= Limits.CRIT_INT) {
        return null;
      }
    }
    FloatPoint projectedB;
    if (this.b.scalarProduct(this.a, lineSegment.b) < 0) {
      projectedB = this.b;
    } else {
      projectedB = this.perpendicularProjection(lineSegment.b);
    }
    if (Math.abs(projectedB.x) >= Limits.CRIT_INT || Math.abs(projectedB.y) >= Limits.CRIT_INT) {
      return null;
    }
    return new FloatLine(projectedA, projectedB);
  }

  /**
   * Returns the projection of p_line_segment onto this oriented line segment by moving
   * p_line_segment perpendicular into the direction of this line segment Returns null, if the
   * projection is empty or p_line_segment.a == p_line_segment.b.
   */
  public FloatLine segmentProjection2(FloatLine lineSegment) {
    if (lineSegment.a.scalarProduct(lineSegment.b, this.b) <= 0) {
      return null;
    }
    if (lineSegment.b.scalarProduct(lineSegment.a, this.a) <= 0) {
      return null;
    }
    FloatPoint projectedA;
    if (lineSegment.a.scalarProduct(lineSegment.b, this.a) < 0) {
      FloatLine currentPerpendicularLine =
          new FloatLine(lineSegment.a, lineSegment.b.turn90Degree(1, lineSegment.a));
      projectedA = currentPerpendicularLine.intersection(this);
      if (projectedA == null
          || Math.abs(projectedA.x) >= Limits.CRIT_INT
          || Math.abs(projectedA.y) >= Limits.CRIT_INT) {
        return null;
      }
    } else {
      projectedA = this.a;
    }

    FloatPoint projectedB;

    if (lineSegment.b.scalarProduct(lineSegment.a, this.b) < 0) {
      FloatLine currentPerpendicularLine =
          new FloatLine(lineSegment.b, lineSegment.a.turn90Degree(1, lineSegment.b));
      projectedB = currentPerpendicularLine.intersection(this);
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
   * Shrinks this line on both sides by p_value. The result will contain at least the midpoint of
   * the line.
   */
  public FloatLine shrinkSegment(double offset) {
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    if (dx == 0 && dy == 0) {
      return this;
    }
    double length = Math.sqrt(dx * dx + dy * dy);
    double effectiveOffset = Math.min(offset, length / 2);
    FloatPoint newA =
        new FloatPoint(
            a.x + (dx * effectiveOffset) / length, a.y + (dy * effectiveOffset) / length);
    double newLength = length - effectiveOffset;
    FloatPoint newB =
        new FloatPoint(a.x + (dx * newLength) / length, a.y + (dy * newLength) / length);
    return new FloatLine(newA, newB);
  }

  /** Calculates the nearest point on this line to p_from_point between this.a and this.b. */
  public FloatPoint nearestSegmentPoint(FloatPoint fromPoint) {
    FloatPoint projection = this.perpendicularProjection(fromPoint);
    if (projection.isContainedInBox(this.a, this.b, 0.01)) {
      return projection;
    }
    // Now the projection is outside the line segment.
    FloatPoint result;
    if (fromPoint.distanceSquare(this.a) <= fromPoint.distanceSquare(this.b)) {
      result = this.a;
    } else {
      result = this.b;
    }
    return result;
  }

  /**
   * Divides this line segment into p_count line segments of nearly equal length and at most
   * p_max_section_length.
   */
  public FloatLine[] divideSegmentIntoSections(int count) {
    if (count == 0) {
      return new FloatLine[0];
    }
    if (count == 1) {
      FloatLine[] result = new FloatLine[1];
      result[0] = this;
      return result;
    }
    double lineLength = this.b.distance(this.a);
    FloatLine[] result = new FloatLine[count];
    double sectionLength = lineLength / count;
    double dx = b.x - a.x;
    double dy = b.y - a.y;
    FloatPoint currentA = this.a;
    for (int i = 0; i < count; i++) {
      FloatPoint currentB;
      if (i == count - 1) {
        currentB = this.b;
      } else {
        double currentDistance = (i + 1) * sectionLength;
        double currentX = a.x + (dx * currentDistance) / lineLength;
        double currentY = a.y + (dy * currentDistance) / lineLength;
        currentB = new FloatPoint(currentX, currentY);
      }
      result[i] = new FloatLine(currentA, currentB);
      currentA = currentB;
    }
    return result;
  }
}

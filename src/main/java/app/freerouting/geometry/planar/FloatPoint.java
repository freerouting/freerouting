package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Implements a point in the plane as a tuple of double's. Because arithmetic calculations with
 * doubles are in general not exact, FloatPoint is not derived from the abstract class Point.
 */
public class FloatPoint implements Serializable {

  public static final FloatPoint ZERO = new FloatPoint(0, 0);

  /** The x coordinate of this point. */
  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final double x;

  /** The y coordinate of this point. */
  @SuppressWarnings("checkstyle:GoogleNonConstantFieldName")
  public final double y;

  /** Creates an instance of class FloatPoint from two doubles. */
  public FloatPoint(double x, double y) {
    this.x = x;
    this.y = y;
  }

  /** Creates a FloatPoint from an IntPoint. */
  public FloatPoint(IntPoint pt) {
    x = pt.x;
    y = pt.y;
  }

  /** Calculates the smallest IntOctagon containing all the input points. */
  public static IntOctagon boundingOctagon(FloatPoint[] pointArr) {
    double lx = Integer.MAX_VALUE;
    double ly = Integer.MAX_VALUE;
    double rx = Integer.MIN_VALUE;
    double uy = Integer.MIN_VALUE;
    double ulx = Integer.MAX_VALUE;
    double lrx = Integer.MIN_VALUE;
    double llx = Integer.MAX_VALUE;
    double urx = Integer.MIN_VALUE;
    for (int i = 0; i < pointArr.length; i++) {
      FloatPoint current = pointArr[i];
      lx = Math.min(lx, current.x);
      ly = Math.min(ly, current.y);
      rx = Math.max(rx, current.x);
      uy = Math.max(uy, current.y);
      double tmp = current.x - current.y;
      ulx = Math.min(ulx, tmp);
      lrx = Math.max(lrx, tmp);
      tmp = current.x + current.y;
      llx = Math.min(llx, tmp);
      urx = Math.max(urx, tmp);
    }
    return new IntOctagon(
        (int) Math.floor(lx),
        (int) Math.floor(ly),
        (int) Math.ceil(rx),
        (int) Math.ceil(uy),
        (int) Math.floor(ulx),
        (int) Math.ceil(lrx),
        (int) Math.floor(llx),
        (int) Math.ceil(urx));
  }

  /** Returns the square of the distance from this point to the zero point. */
  public final double sizeSquare() {
    return x * x + y * y;
  }

  /** Returns the distance from this point to the zero point. */
  public final double size() {
    return Math.sqrt(sizeSquare());
  }

  /** Returns the square of the distance from this Point to the Point p_other. */
  public final double distanceSquare(FloatPoint other) {
    double dx = other.x - x;
    double dy = other.y - y;
    return dx * dx + dy * dy;
  }

  /** Returns the distance from this point to the point p_other. */
  public final double distance(FloatPoint other) {
    return Math.sqrt(distanceSquare(other));
  }

  /** Computes the weighted distance to p_other. */
  public double weightedDistance(FloatPoint other, double horizontalWeight, double verticalWeight) {
    double deltaX = this.x - other.x;
    double deltaY = this.y - other.y;
    deltaX *= horizontalWeight;
    deltaY *= verticalWeight;
    return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
  }

  /** Rounds the coordinates from an object of class Point_double to an object of class IntPoint. */
  public IntPoint round() {
    return new IntPoint((int) Math.round(x), (int) Math.round(y));
  }

  /**
   * Rounds this point, so that if this point is on the right side of any directed line with
   * direction p_dir, the result point will also be on the right side.
   */
  public IntPoint roundToTheRight(Direction dir) {
    FloatPoint directionVector = dir.getVector().toFloat();
    int roundedX;

    if (directionVector.y > 0) {
      roundedX = (int) Math.ceil(x);
    } else if (directionVector.y < 0) {
      roundedX = (int) Math.floor(x);
    } else {
      roundedX = (int) Math.round(x);
    }

    int roundedY;

    if (directionVector.x > 0) {
      roundedY = (int) Math.floor(y);
    } else if (directionVector.x < 0) {
      roundedY = (int) Math.ceil(y);
    } else {
      roundedY = (int) Math.round(y);
    }
    return new IntPoint(roundedX, roundedY);
  }

  /**
   * Round this Point so the x coordinate of the result will be a multiple of p_horizontal_grid and
   * the y coordinate a multiple of p_vertical_grid.
   */
  public IntPoint roundToGrid(int horizontalGrid, int verticalGrid) {
    double roundedX;
    if (horizontalGrid > 0) {
      roundedX = Math.rint(this.x / horizontalGrid) * horizontalGrid;
    } else {
      roundedX = this.x;
    }
    double roundedY;
    if (verticalGrid > 0) {
      roundedY = Math.rint(this.y / verticalGrid) * verticalGrid;
    } else {
      roundedY = this.y;
    }
    return new IntPoint((int) roundedX, (int) roundedY);
  }

  /**
   * Rounds this point, so that if this point is on the left side of any directed line with
   * direction p_dir, the result point will also be on the left side.
   */
  public IntPoint roundToTheLeft(Direction dir) {
    FloatPoint directionVector = dir.getVector().toFloat();
    int roundedX;

    if (directionVector.y > 0) {
      roundedX = (int) Math.floor(x);
    } else if (directionVector.y < 0) {
      roundedX = (int) Math.ceil(x);
    } else {
      roundedX = (int) Math.round(x);
    }

    int roundedY;

    if (directionVector.x > 0) {
      roundedY = (int) Math.ceil(y);
    } else if (directionVector.x < 0) {
      roundedY = (int) Math.floor(y);
    } else {
      roundedY = (int) Math.round(y);
    }
    return new IntPoint(roundedX, roundedY);
  }

  /** Adds the coordinates of this FloatPoint and p_other. */
  public FloatPoint add(FloatPoint other) {
    return new FloatPoint(this.x + other.x, this.y + other.y);
  }

  /** Subtracts the coordinates of p_other from this FloatPoint. */
  public FloatPoint subtract(FloatPoint other) {
    return new FloatPoint(this.x - other.x, this.y - other.y);
  }

  /** Returns an approximation of the perpendicular projection of this point onto p_line. */
  public FloatPoint projectionApprox(Line line) {
    FloatLine floatLine = new FloatLine(line.a.toFloat(), line.b.toFloat());
    return floatLine.perpendicularProjection(this);
  }

  /** Calculates the scalar product of (p_1 - this). with (p_2 - this). */
  public double scalarProduct(FloatPoint p1, FloatPoint p2) {
    if (p1 == null || p2 == null) {
      FRLogger.warn("FloatPoint.scalarProduct: parameter point is null");
      return 0;
    }
    double dx1 = p1.x - this.x;
    double dx2 = p2.x - this.x;
    double dy1 = p1.y - this.y;
    double dy2 = p2.y - this.y;
    return dx1 * dx2 + dy1 * dy2;
  }

  /**
   * Approximates a FloatPoint on the line from zero to this point with distance p_new_length from
   * zero.
   */
  public FloatPoint changeSize(double newSize) {
    if (x == 0 && y == 0) {
      // the size of the zero point cannot be changed
      return this;
    }
    double length = Math.sqrt(x * x + y * y);
    double newX = (x * newSize) / length;
    double newY = (y * newSize) / length;
    return new FloatPoint(newX, newY);
  }

  /**
   * Approximates a FloatPoint on the line from this point to p_to_point with distance p_new_length
   * from this point.
   */
  public FloatPoint changeLength(FloatPoint toPoint, double newLength) {
    double dx = toPoint.x - this.x;
    double dy = toPoint.y - this.y;
    if (dx == 0 && dy == 0) {
      FRLogger.warn("IntPoint.change_length: Points are equal");
      return toPoint;
    }
    double length = Math.sqrt(dx * dx + dy * dy);
    double newX = this.x + (dx * newLength) / length;
    double newY = this.y + (dy * newLength) / length;
    return new FloatPoint(newX, newY);
  }

  /** Returns the middle point between this point and p_to_point. */
  public FloatPoint middlePoint(FloatPoint toPoint) {
    if (toPoint == this) {
      return this;
    }
    double middleX = 0.5 * (this.x + toPoint.x);
    double middleY = 0.5 * (this.y + toPoint.y);
    return new FloatPoint(middleX, middleY);
  }

  /**
   * The function returns Side.ON_THE_LEFT, if this Point is on the left of the line from p_1 to
   * p_2; and Side.ON_THE_RIGHT, if this Point is on the right of the line from p_1 to p_2.
   * Collinearity is not defined, because numerical calculations ar not exact for FloatPoints.
   */
  public Side sideOf(FloatPoint p1, FloatPoint p2) {
    double d21X = p2.x - p1.x;
    double d21Y = p2.y - p1.y;
    double d01X = this.x - p1.x;
    double d01Y = this.y - p1.y;
    double determinant = d21X * d01Y - d21Y * d01X;
    return Side.of(determinant);
  }

  /** Rotates this FloatPoint by p_angle (in radians) around the p_pole. */
  public FloatPoint rotate(double angle, FloatPoint pole) {
    if (angle == 0) {
      return this;
    }

    double dx = x - pole.x;
    double dy = y - pole.y;
    double sinAngle = Math.sin(angle);
    double cosAngle = Math.cos(angle);
    double newDx = dx * cosAngle - dy * sinAngle;
    double newDy = dx * sinAngle + dy * cosAngle;
    return new FloatPoint(pole.x + newDx, pole.y + newDy);
  }

  /** Turns this FloatPoint by p_factor times 90 degrees around ZERO. */
  public FloatPoint turn90Degree(int factor) {
    int n = factor;
    while (n < 0) {
      n += 4;
    }
    while (n >= 4) {
      n -= 4;
    }
    double newX;
    double newY;
    switch (n) {
      case 0 -> { // 0 degree
        newX = x;
        newY = y;
      }
      case 1 -> { // 90 degree
        newX = -y;
        newY = x;
      }
      case 2 -> { // 180 degree
        newX = -x;
        newY = -y;
      }
      case 3 -> { // 270 degree
        newX = y;
        newY = -x;
      }
      default -> {
        newX = 0;
        newY = 0;
      }
    }
    return new FloatPoint(newX, newY);
  }

  /** Turns this FloatPoint by p_factor times 90 degrees around p_pole. */
  public FloatPoint turn90Degree(int factor, FloatPoint pole) {
    FloatPoint v = this.subtract(pole);
    v = v.turn90Degree(factor);
    return pole.add(v);
  }

  /**
   * Checks, if this point is contained in the box spanned by p_1 and p_2 with the input tolerance.
   */
  public boolean isContainedInBox(FloatPoint p1, FloatPoint p2, double tolerance) {
    double minX;
    double maxX;
    if (p1.x < p2.x) {
      minX = p1.x;
      maxX = p2.x;
    } else {
      minX = p2.x;
      maxX = p1.x;
    }
    if (this.x < minX - tolerance || this.x > maxX + tolerance) {
      return false;
    }
    double minY;
    double maxY;
    if (p1.y < p2.y) {
      minY = p1.y;
      maxY = p2.y;
    } else {
      minY = p2.y;
      maxY = p1.y;
    }
    return this.y >= minY - tolerance && this.y <= maxY + tolerance;
  }

  /** Creates the smallest IntBox containing this point. */
  public IntBox boundingBox() {
    IntPoint lowerLeft = new IntPoint((int) Math.floor(this.x), (int) Math.floor(this.y));
    IntPoint upperRight = new IntPoint((int) Math.ceil(this.x), (int) Math.ceil(this.y));
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Calculates the touching points of the tangents from this point to a circle around p_to_point
   * with radius p_distance. Solves the quadratic equation, which results by substituting x by the
   * term in y from the equation of the polar line of a circle with center p_to_point and radius
   * p_distance and putting it into the circle equation. The polar line is the line through the 2
   * tangential points of the circle looked at from this point and has the equation (this.x -
   * p_to_point.x) * (x - p_to_point.x) + (this.y - p_to_point.y) * (y - p_to_point.y) = p_distance
   * **2
   */
  public FloatPoint[] tangentialPoints(FloatPoint toPoint, double distance) {
    // turn the situation 90 degree if the x difference is smaller
    // than the y difference for better numerical stability

    double dx = Math.abs(this.x - toPoint.x);
    double dy = Math.abs(this.y - toPoint.y);
    boolean situationTurned = dy > dx;
    FloatPoint pole;
    FloatPoint circleCenter;

    if (situationTurned) {
      // turn the situation by 90 degree
      pole = new FloatPoint(-this.y, this.x);
      circleCenter = new FloatPoint(-toPoint.y, toPoint.x);
    } else {
      pole = this;
      circleCenter = toPoint;
    }

    dx = pole.x - circleCenter.x;
    dy = pole.y - circleCenter.y;
    double dxSquare = dx * dx;
    double dySquare = dy * dy;
    double distSquare = dxSquare + dySquare;
    double radiusSquare = distance * distance;
    double discriminant = radiusSquare * dySquare - (radiusSquare - dxSquare) * distSquare;

    if (discriminant <= 0) {
      // pole is inside the circle.
      return new FloatPoint[0];
    }
    double squareRoot = Math.sqrt(discriminant);

    FloatPoint[] result = new FloatPoint[2];

    double a1 = radiusSquare * dy;
    double dy1 = (a1 + distance * squareRoot) / distSquare;
    double dy2 = (a1 - distance * squareRoot) / distSquare;

    double firstPointY = dy1 + circleCenter.y;
    double firstPointX = (radiusSquare - dy * dy1) / dx + circleCenter.x;
    double secondPointY = dy2 + circleCenter.y;
    double secondPointX = (radiusSquare - dy * dy2) / dx + circleCenter.x;

    if (situationTurned) {
      // turn the result by 270 degree
      result[0] = new FloatPoint(firstPointY, -firstPointX);
      result[1] = new FloatPoint(secondPointY, -secondPointX);
    } else {
      result[0] = new FloatPoint(firstPointX, firstPointY);
      result[1] = new FloatPoint(secondPointX, secondPointY);
    }
    return result;
  }

  /**
   * Calculates the left tangential point of the line from this point to a circle around p_to_point
   * with radius p_distance. Returns null, if this point is inside this circle.
   */
  public FloatPoint leftTangentialPoint(FloatPoint toPoint, double distance) {
    if (toPoint == null) {
      return null;
    }
    FloatPoint[] tangentPoints = tangentialPoints(toPoint, distance);
    if (tangentPoints.length < 2) {
      return null;
    }
    FloatPoint result;
    if (toPoint.sideOf(this, tangentPoints[0]) == Side.ON_THE_RIGHT) {
      result = tangentPoints[0];
    } else {
      result = tangentPoints[1];
    }
    return result;
  }

  /**
   * Calculates the right tangential point of the line from this point to a circle around p_to_point
   * with radius p_distance. Returns null, if this point is inside this circle.
   */
  public FloatPoint rightTangentialPoint(FloatPoint toPoint, double distance) {
    if (toPoint == null) {
      return null;
    }
    FloatPoint[] tangentPoints = tangentialPoints(toPoint, distance);
    if (tangentPoints.length < 2) {
      return null;
    }
    FloatPoint result;
    if (toPoint.sideOf(this, tangentPoints[0]) == Side.ON_THE_LEFT) {
      result = tangentPoints[0];
    } else {
      result = tangentPoints[1];
    }
    return result;
  }

  /**
   * Calculates the center of the circle through this point, p_1 and p_2 by calculating the
   * intersection of the two lines perpendicular to and passing through the midpoints of the lines
   * (this, p_1) and (p_1, p_2).
   */
  public FloatPoint circleCenter(FloatPoint p1, FloatPoint p2) {
    double slope1 = (p1.y - this.y) / (p1.x - this.x);
    double slope2 = (p2.y - p1.y) / (p2.x - p1.x);
    double centerX =
        (slope1 * slope2 * (this.y - p2.y) + slope2 * (this.x + p1.x) - slope1 * (p1.x + p2.x))
            / (2 * (slope2 - slope1));
    double centerY = (0.5 * (this.x + p1.x) - centerX) / slope1 + 0.5 * (this.y + p1.y);
    return new FloatPoint(centerX, centerY);
  }

  /** Returns true, if this point is contained in the circle through p_1, p_2 and p_3. */
  public boolean insideCircle(FloatPoint p1, FloatPoint p2, FloatPoint p3) {
    FloatPoint center = p1.circleCenter(p2, p3);
    double radiusSquare = center.distanceSquare(p1);
    return this.distanceSquare(center)
        < radiusSquare - 1; // - 1 is a tolerance for numerical stability.
  }

  /** Returns a localized string representation of this point. */
  public String toString(Locale locale) {
    NumberFormat nf = NumberFormat.getInstance(locale);
    nf.setMaximumFractionDigits(4);
    return "(" + nf.format(x) + " , " + nf.format(y) + ")";
  }

  /** Returns a padded, localized string representation of this point. */
  public String toString(Locale locale, int fractionDigits, int padding) {
    NumberFormat nf = NumberFormat.getInstance(locale);
    nf.setMinimumFractionDigits(fractionDigits);
    nf.setMaximumFractionDigits(fractionDigits);
    return "X "
        + String.format("%" + padding + "s", nf.format(x))
        + "   Y "
        + String.format("%" + padding + "s", nf.format(-y));
  }

  @Override
  public String toString() {
    return toString(Locale.ENGLISH);
  }
}

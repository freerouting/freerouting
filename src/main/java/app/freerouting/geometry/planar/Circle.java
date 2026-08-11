package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

/** Describes functionality of a circle shape in the plane. */
public class Circle implements ConvexShape, Serializable {

  public final IntPoint center;
  public final int radius;

  /** Creates a new instance of Circle. */
  public Circle(IntPoint center, int radius) {
    this.center = center;
    if (radius < 0) {
      FRLogger.warn("Circle: unexpected negative radius");
      this.radius = -radius;
    } else {
      this.radius = radius;
    }
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean isBounded() {
    return true;
  }

  @Override
  public int dimension() {
    if (radius == 0) {
      // circle is reduced to a point
      return 0;
    }
    return 2;
  }

  @Override
  public double circumference() {
    return 2.0 * Math.PI * radius;
  }

  @Override
  public double area() {
    return (Math.PI * radius) * radius;
  }

  @Override
  public FloatPoint centreOfGravity() {
    return center.toFloat();
  }

  @Override
  public boolean isOutside(Point point) {
    FloatPoint fp = point.toFloat();
    return fp.distanceSquare(center.toFloat()) > (double) radius * radius;
  }

  @Override
  public boolean contains(Point point) {
    return !isOutside(point);
  }

  @Override
  public boolean contains(FloatPoint point) {
    return point.distanceSquare(center.toFloat()) <= (double) radius * radius;
  }

  @Override
  public boolean containsInside(Point point) {
    FloatPoint fp = point.toFloat();
    return fp.distanceSquare(center.toFloat()) < (double) radius * radius;
  }

  @Override
  public boolean containsOnBorder(Point point) {
    FloatPoint fp = point.toFloat();
    return fp.distanceSquare(center.toFloat()) == (double) radius * radius;
  }

  @Override
  public double distance(FloatPoint point) {
    double d = point.distance(center.toFloat()) - radius;
    return Math.max(d, 0.0);
  }

  @Override
  public double smallestRadius() {
    return radius;
  }

  @Override
  public IntBox boundingBox() {
    int llx = center.x - radius;
    int urx = center.x + radius;
    int lly = center.y - radius;
    int ury = center.y + radius;
    return new IntBox(llx, lly, urx, ury);
  }

  @Override
  public IntOctagon boundingOctagon() {
    int lx = center.x - radius;
    int rx = center.x + radius;
    int ly = center.y - radius;
    int uy = center.y + radius;

    final double sqrt2Minus1 = Math.sqrt(2) - 1;
    final int ceilCornerValue = (int) Math.ceil(sqrt2Minus1 * radius);
    final int floorCornerValue = (int) Math.floor(sqrt2Minus1 * radius);

    int ulx = lx - (center.y + floorCornerValue);
    int lrx = rx - (center.y - ceilCornerValue);
    int llx = lx + (center.y - floorCornerValue);
    int urx = rx + (center.y + ceilCornerValue);
    return new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
  }

  @Override
  public TileShape boundingTile() {
    return boundingOctagon();
    // the following caused problems with the spring_over algorithm in routing.
    /* if (this.precalculated_bounding_tile == null)
    {
        this.precalculated_bounding_tile = bounding_tile(c_max_approximation_segment_length);
    }
    return this.precalculated_bounding_tile; */
  }

  /**
   * Creates a bounding tile shape around this circle, so that the length of the line segments of
   * the tile is at most p_max_segment_length.
   */
  public TileShape boundingTile(int maxSegmentLength) {
    int quadrantDivisionCount = this.radius / maxSegmentLength + 1;
    if (quadrantDivisionCount <= 2) {
      return this.boundingOctagon();
    }
    Line[] tangentLineArr = new Line[quadrantDivisionCount * 4];
    for (int i = 0; i < quadrantDivisionCount; i++) {
      // calculate the tangential points in the first quadrant
      Vector borderDelta;
      if (i == 0) {
        borderDelta = new IntVector(this.radius, 0);
      } else {
        double currAngle = i * Math.PI / (2.0 * quadrantDivisionCount);
        int currX = (int) Math.ceil(Math.sin(currAngle) * this.radius);
        int currY = (int) Math.ceil(Math.cos(currAngle) * this.radius);
        borderDelta = new IntVector(currX, currY);
      }
      Point currA = this.center.translateBy(borderDelta);
      Point currB = currA.turn90Degree(1, this.center);
      Direction currDir = Direction.getInstance(currB.differenceBy(this.center));
      Line currTangent = new Line(currA, currDir);
      tangentLineArr[quadrantDivisionCount + i] = currTangent;
      tangentLineArr[2 * quadrantDivisionCount + i] = currTangent.turn90Degree(1, this.center);
      tangentLineArr[3 * quadrantDivisionCount + i] = currTangent.turn90Degree(2, this.center);
      tangentLineArr[i] = currTangent.turn90Degree(3, this.center);
    }
    return TileShape.getInstance(tangentLineArr);
  }

  @Override
  public boolean isContainedIn(IntBox box) {
    if (box.ll.x > center.x - radius) {
      return false;
    }
    if (box.ll.y > center.y - radius) {
      return false;
    }
    if (box.ur.x < center.x + radius) {
      return false;
    }
    return box.ur.y >= center.y + radius;
  }

  @Override
  public Circle turn90Degree(int factor, IntPoint pole) {
    IntPoint newCenter = (IntPoint) center.turn90Degree(factor, pole);
    return new Circle(newCenter, radius);
  }

  @Override
  public Circle rotateApprox(double angle, FloatPoint pole) {
    IntPoint newCenter = center.toFloat().rotate(angle, pole).round();
    return new Circle(newCenter, radius);
  }

  @Override
  public Circle mirrorVertical(IntPoint pole) {
    IntPoint newCenter = (IntPoint) center.mirrorVertical(pole);
    return new Circle(newCenter, radius);
  }

  @Override
  public Circle mirrorHorizontal(IntPoint pole) {
    IntPoint newCenter = (IntPoint) center.mirrorHorizontal(pole);
    return new Circle(newCenter, radius);
  }

  @Override
  public double maxWidth() {
    return 2 * this.radius;
  }

  @Override
  public double minWidth() {
    return 2 * this.radius;
  }

  @Override
  public RegularTileShape boundingShape(ShapeBoundingDirections dirs) {
    return dirs.bounds(this);
  }

  @Override
  public Circle offset(double offset) {
    double newRadius = this.radius + offset;
    int r = (int) Math.round(newRadius);
    return new Circle(this.center, r);
  }

  @Override
  public Circle shrink(double offset) {
    double newRadius = this.radius - offset;
    int r = Math.max((int) Math.round(newRadius), 1);
    return new Circle(this.center, r);
  }

  @Override
  public Circle translateBy(Vector vector) {
    if (vector.equals(Vector.ZERO)) {
      return this;
    }
    if (!(vector instanceof IntVector)) {
      FRLogger.warn("Circle.translate_by only implemented for IntVectors till now");
      return this;
    }
    IntPoint newCenter = (IntPoint) center.translateBy(vector);
    return new Circle(newCenter, radius);
  }

  @Override
  public FloatPoint nearestPointApprox(FloatPoint point) {
    FRLogger.warn("Circle.nearest_point_approx not yet implemented");
    return null;
  }

  @Override
  public double borderDistance(FloatPoint point) {
    double d = point.distance(center.toFloat()) - radius;
    return Math.abs(d);
  }

  @Override
  public Circle enlarge(double offset) {
    if (offset == 0) {
      return this;
    }
    int newRadius = radius + (int) Math.round(offset);
    return new Circle(center, newRadius);
  }

  @Override
  public boolean intersects(Shape other) {
    return other.intersects(this);
  }

  @Override
  public boolean intersects(Circle other) {
    double radiusSumSquare = radius + other.radius;
    radiusSumSquare *= radiusSumSquare;
    return center.distanceSquare(other.center) <= radiusSumSquare;
  }

  @Override
  public boolean intersects(IntBox box) {
    return box.distance(center.toFloat()) <= radius;
  }

  @Override
  public boolean intersects(IntOctagon oct) {
    return oct.distance(center.toFloat()) <= radius;
  }

  @Override
  public boolean intersects(Simplex simplex) {
    return simplex.distance(center.toFloat()) <= radius;
  }

  @Override
  public Polyline[] cutout(Polyline polyline) {
    FRLogger.warn("Circle.cutout not yet implemented");
    return null;
  }

  @Override
  public TileShape[] splitToConvex() {
    TileShape[] result = new TileShape[1];
    result[0] = this.boundingTile();
    return result;
  }

  @Override
  public Circle getBorder() {
    return this;
  }

  @Override
  public Shape[] getHoles() {
    return new Shape[0];
  }

  @Override
  public FloatPoint[] cornerApproxArr() {
    return new FloatPoint[0];
  }

  @Override
  public String toString() {
    return toString(Locale.ENGLISH);
  }

  /** Returns a localized string representation of this circle. */
  public String toString(Locale locale) {
    String result = "Circle: ";
    if (!center.equals(Point.ZERO)) {
      String centerString = "center " + center;
      result += centerString;
    }
    NumberFormat nf = NumberFormat.getInstance(locale);
    String radiusString = "radius " + nf.format(radius);
    result += radiusString;
    return result;
  }

  // private TileShape precalculated_bounding_tile = null;

  // private static final int c_max_approximation_segment_length = 10000;
}

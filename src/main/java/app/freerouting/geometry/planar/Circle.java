package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

/** Describes functionality of a circle shape in the plane. */
public class Circle implements ConvexShape, Serializable {

  public final IntPoint center;
  public final int radius;

  /** Creates a new instance of Circle */
  public Circle(IntPoint p_center, int p_radius) {
    center = p_center;
    if (p_radius < 0) {
      FRLogger.warn("Circle: unexpected negative radius");
      radius = -p_radius;
    } else {
      radius = p_radius;
    }
  }

  @Override
  public boolean is_empty() {
    return false;
  }

  @Override
  public boolean is_bounded() {
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
  public FloatPoint centre_of_gravity() {
    return center.to_float();
  }

  @Override
  public boolean is_outside(Point p_point) {
    FloatPoint fp = p_point.to_float();
    return fp.distance_square(center.to_float()) > (double) radius * radius;
  }

  @Override
  public boolean contains(Point p_point) {
    return !is_outside(p_point);
  }

  @Override
  public boolean contains_inside(Point p_point) {
    FloatPoint fp = p_point.to_float();
    return fp.distance_square(center.to_float()) < (double) radius * radius;
  }

  @Override
  public boolean contains_on_border(Point p_point) {
    FloatPoint fp = p_point.to_float();
    return fp.distance_square(center.to_float()) == (double) radius * radius;
  }

  @Override
  public boolean contains(FloatPoint p_point) {
    return p_point.distance_square(center.to_float()) <= (double) radius * radius;
  }

  @Override
  public double distance(FloatPoint p_point) {
    double d = p_point.distance(center.to_float()) - radius;
    return Math.max(d, 0.0);
  }

  @Override
  public double smallest_radius() {
    return radius;
  }

  @Override
  public IntBox bounding_box() {
    int llx = center.x - radius;
    int urx = center.x + radius;
    int lly = center.y - radius;
    int ury = center.y + radius;
    return new IntBox(llx, lly, urx, ury);
  }

  @Override
  public IntOctagon bounding_octagon() {
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
  public TileShape bounding_tile() {
    return bounding_octagon();
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
  public TileShape bounding_tile(int p_max_segment_length) {
    int quadrantDivisionCount = this.radius / p_max_segment_length + 1;
    if (quadrantDivisionCount <= 2) {
      return this.bounding_octagon();
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
      Point currA = this.center.translate_by(borderDelta);
      Point currB = currA.turn_90_degree(1, this.center);
      Direction currDir = Direction.get_instance(currB.difference_by(this.center));
      Line currTangent = new Line(currA, currDir);
      tangentLineArr[quadrantDivisionCount + i] = currTangent;
      tangentLineArr[2 * quadrantDivisionCount + i] = currTangent.turn_90_degree(1, this.center);
      tangentLineArr[3 * quadrantDivisionCount + i] = currTangent.turn_90_degree(2, this.center);
      tangentLineArr[i] = currTangent.turn_90_degree(3, this.center);
    }
    return TileShape.get_instance(tangentLineArr);
  }

  @Override
  public boolean is_contained_in(IntBox p_box) {
    if (p_box.ll.x > center.x - radius) {
      return false;
    }
    if (p_box.ll.y > center.y - radius) {
      return false;
    }
    if (p_box.ur.x < center.x + radius) {
      return false;
    }
    return p_box.ur.y >= center.y + radius;
  }

  @Override
  public Circle turn_90_degree(int p_factor, IntPoint p_pole) {
    IntPoint newCenter = (IntPoint) center.turn_90_degree(p_factor, p_pole);
    return new Circle(newCenter, radius);
  }

  @Override
  public Circle rotate_approx(double p_angle, FloatPoint p_pole) {
    IntPoint newCenter = center.to_float().rotate(p_angle, p_pole).round();
    return new Circle(newCenter, radius);
  }

  @Override
  public Circle mirror_vertical(IntPoint p_pole) {
    IntPoint newCenter = (IntPoint) center.mirror_vertical(p_pole);
    return new Circle(newCenter, radius);
  }

  @Override
  public Circle mirror_horizontal(IntPoint p_pole) {
    IntPoint newCenter = (IntPoint) center.mirror_horizontal(p_pole);
    return new Circle(newCenter, radius);
  }

  @Override
  public double max_width() {
    return 2 * this.radius;
  }

  @Override
  public double min_width() {
    return 2 * this.radius;
  }

  @Override
  public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs) {
    return p_dirs.bounds(this);
  }

  @Override
  public Circle offset(double p_offset) {
    double newRadius = this.radius + p_offset;
    int r = (int) Math.round(newRadius);
    return new Circle(this.center, r);
  }

  @Override
  public Circle shrink(double p_offset) {
    double newRadius = this.radius - p_offset;
    int r = Math.max((int) Math.round(newRadius), 1);
    return new Circle(this.center, r);
  }

  @Override
  public Circle translate_by(Vector p_vector) {
    if (p_vector.equals(Vector.ZERO)) {
      return this;
    }
    if (!(p_vector instanceof IntVector)) {
      FRLogger.warn("Circle.translate_by only implemented for IntVectors till now");
      return this;
    }
    IntPoint newCenter = (IntPoint) center.translate_by(p_vector);
    return new Circle(newCenter, radius);
  }

  @Override
  public FloatPoint nearest_point_approx(FloatPoint p_point) {
    FRLogger.warn("Circle.nearest_point_approx not yet implemented");
    return null;
  }

  @Override
  public double border_distance(FloatPoint p_point) {
    double d = p_point.distance(center.to_float()) - radius;
    return Math.abs(d);
  }

  @Override
  public Circle enlarge(double p_offset) {
    if (p_offset == 0) {
      return this;
    }
    int newRadius = radius + (int) Math.round(p_offset);
    return new Circle(center, newRadius);
  }

  @Override
  public boolean intersects(Shape p_other) {
    return p_other.intersects(this);
  }

  @Override
  public Polyline[] cutout(Polyline p_polyline) {
    FRLogger.warn("Circle.cutout not yet implemented");
    return null;
  }

  @Override
  public boolean intersects(Circle p_other) {
    double dSquare = radius + p_other.radius;
    dSquare *= dSquare;
    return center.distance_square(p_other.center) <= dSquare;
  }

  @Override
  public boolean intersects(IntBox p_box) {
    return p_box.distance(center.to_float()) <= radius;
  }

  @Override
  public boolean intersects(IntOctagon p_oct) {
    return p_oct.distance(center.to_float()) <= radius;
  }

  @Override
  public boolean intersects(Simplex p_simplex) {
    return p_simplex.distance(center.to_float()) <= radius;
  }

  @Override
  public TileShape[] split_to_convex() {
    TileShape[] result = new TileShape[1];
    result[0] = this.bounding_tile();
    return result;
  }

  @Override
  public Circle get_border() {
    return this;
  }

  @Override
  public Shape[] get_holes() {
    return new Shape[0];
  }

  @Override
  public FloatPoint[] corner_approx_arr() {
    return new FloatPoint[0];
  }

  @Override
  public String toString() {
    return to_string(Locale.ENGLISH);
  }

  public String to_string(Locale p_locale) {
    String result = "Circle: ";
    if (!center.equals(Point.ZERO)) {
      String centerString = "center " + center;
      result += centerString;
    }
    NumberFormat nf = NumberFormat.getInstance(p_locale);
    String radiusString = "radius " + nf.format(radius);
    result += radiusString;
    return result;
  }

  // private TileShape precalculated_bounding_tile = null;

  // private static final int c_max_approximation_segment_length = 10000;
}

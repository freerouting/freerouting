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

    final double sqrt2_minus_1 = Math.sqrt(2) - 1;
    final int ceil_corner_value = (int) Math.ceil(sqrt2_minus_1 * radius);
    final int floor_corner_value = (int) Math.floor(sqrt2_minus_1 * radius);

    int ulx = lx - (center.y + floor_corner_value);
    int lrx = rx - (center.y - ceil_corner_value);
    int llx = lx + (center.y - floor_corner_value);
    int urx = rx + (center.y + ceil_corner_value);
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
    int quadrant_division_count = this.radius / p_max_segment_length + 1;
    if (quadrant_division_count <= 2) {
      return this.bounding_octagon();
    }
    Line[] tangent_line_arr = new Line[quadrant_division_count * 4];
    for (int i = 0; i < quadrant_division_count; ++i) {
      // calculate the tangential points in the first quadrant
      Vector border_delta;
      if (i == 0) {
        border_delta = new IntVector(this.radius, 0);
      } else {
        double curr_angle = i * Math.PI / (2.0 * quadrant_division_count);
        int curr_x = (int) Math.ceil(Math.sin(curr_angle) * this.radius);
        int curr_y = (int) Math.ceil(Math.cos(curr_angle) * this.radius);
        border_delta = new IntVector(curr_x, curr_y);
      }
      Point curr_a = this.center.translate_by(border_delta);
      Point curr_b = curr_a.turn_90_degree(1, this.center);
      Direction curr_dir = Direction.get_instance(curr_b.difference_by(this.center));
      Line curr_tangent = new Line(curr_a, curr_dir);
      tangent_line_arr[quadrant_division_count + i] = curr_tangent;
      tangent_line_arr[2 * quadrant_division_count + i] =
          curr_tangent.turn_90_degree(1, this.center);
      tangent_line_arr[3 * quadrant_division_count + i] =
          curr_tangent.turn_90_degree(2, this.center);
      tangent_line_arr[i] = curr_tangent.turn_90_degree(3, this.center);
    }
    return TileShape.get_instance(tangent_line_arr);
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
    IntPoint new_center = (IntPoint) center.turn_90_degree(p_factor, p_pole);
    return new Circle(new_center, radius);
  }

  @Override
  public Circle rotate_approx(double p_angle, FloatPoint p_pole) {
    IntPoint new_center = center.to_float().rotate(p_angle, p_pole).round();
    return new Circle(new_center, radius);
  }

  @Override
  public Circle mirror_vertical(IntPoint p_pole) {
    IntPoint new_center = (IntPoint) center.mirror_vertical(p_pole);
    return new Circle(new_center, radius);
  }

  @Override
  public Circle mirror_horizontal(IntPoint p_pole) {
    IntPoint new_center = (IntPoint) center.mirror_horizontal(p_pole);
    return new Circle(new_center, radius);
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
    double new_radius = this.radius + p_offset;
    int r = (int) Math.round(new_radius);
    return new Circle(this.center, r);
  }

  @Override
  public Circle shrink(double p_offset) {
    double new_radius = this.radius - p_offset;
    int r = Math.max((int) Math.round(new_radius), 1);
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
    IntPoint new_center = (IntPoint) center.translate_by(p_vector);
    return new Circle(new_center, radius);
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
    int new_radius = radius + (int) Math.round(p_offset);
    return new Circle(center, new_radius);
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
    double d_square = radius + p_other.radius;
    d_square *= d_square;
    return center.distance_square(p_other.center) <= d_square;
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
    if (!(center.equals(Point.ZERO))) {
      String center_string = "center " + center;
      result += center_string;
    }
    NumberFormat nf = NumberFormat.getInstance(p_locale);
    String radius_string = "radius " + nf.format(radius);
    result += radius_string;
    return result;
  }

  // private TileShape precalculated_bounding_tile = null;

  // private static final int c_max_approximation_segment_length = 10000;
}

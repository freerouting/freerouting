package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/** Implements functionality of orthogonal rectangles in the plane with integer coordinates. */
public class IntBox extends RegularTileShape implements Serializable {

  /** Standard implementation of an empty box. */
  public static final IntBox EMPTY =
      new IntBox(Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT, -Limits.CRIT_INT);

  /** coordinates of the lower left corner */
  public final IntPoint ll;

  /** coordinates of the upper right corner */
  public final IntPoint ur;

  /** Creates an IntBox from its lower left and upper right corners. */
  public IntBox(IntPoint p_ll, IntPoint p_ur) {
    ll = p_ll;
    ur = p_ur;
  }

  /** creates an IntBox from the coordinates of its lower left and upper right corners. */
  public IntBox(int p_ll_x, int p_ll_y, int p_ur_x, int p_ur_y) {
    ll = new IntPoint(p_ll_x, p_ll_y);
    ur = new IntPoint(p_ur_x, p_ur_y);
  }

  @Override
  public boolean is_IntOctagon() {
    return true;
  }

  /** Returns true, if the box is empty */
  @Override
  public boolean is_empty() {
    return ll.x > ur.x || ll.y > ur.y;
  }

  @Override
  public int border_line_count() {
    return 4;
  }

  /** returns the horizontal extension of the box. */
  public int width() {
    return ur.x - ll.x;
  }

  /** Returns the vertical extension of the box. */
  public int height() {
    return ur.y - ll.y;
  }

  @Override
  public double max_width() {
    return Math.max(ur.x - ll.x, ur.y - ll.y);
  }

  @Override
  public double min_width() {
    return Math.min(ur.x - ll.x, ur.y - ll.y);
  }

  @Override
  public double area() {
    return ((double) (ur.x - ll.x)) * ((double) (ur.y - ll.y));
  }

  @Override
  public double circumference() {
    return 2 * ((ur.x - ll.x) + (ur.y - ll.y));
  }

  @Override
  public IntPoint corner(int p_no) {
    if (p_no == 0) {
      return ll;
    }
    if (p_no == 1) {
      return new IntPoint(ur.x, ll.y);
    }
    if (p_no == 2) {
      return ur;
    }
    if (p_no == 3) {
      return new IntPoint(ll.x, ur.y);
    }
    throw new IllegalArgumentException("IntBox.corner: p_no out of range");
  }

  @Override
  public int dimension() {
    if (is_empty()) {
      return -1;
    }
    if (ll.equals(ur)) {
      return 0;
    }
    if (ur.x == ll.x || ll.y == ur.y) {
      return 1;
    }
    return 2;
  }

  /** Checks, if p_point is located in the interior of this box. */
  public boolean contains_inside(IntPoint p_point) {
    return p_point.x > this.ll.x
        && p_point.x < this.ur.x
        && p_point.y > this.ll.y
        && p_point.y < this.ur.y;
  }

  @Override
  public boolean is_IntBox() {
    return true;
  }

  @Override
  public TileShape simplify() {
    return this;
  }

  /** Calculates the nearest point of this box to p_from_point. */
  public FloatPoint nearest_point(FloatPoint p_from_point) {
    double x;
    if (p_from_point.x <= ll.x) {
      x = ll.x;
    } else if (p_from_point.x >= ur.x) {
      x = ur.x;
    } else {
      x = p_from_point.x;
    }

    double y;
    if (p_from_point.y <= ll.y) {
      y = ll.y;
    } else if (p_from_point.y >= ur.y) {
      y = ur.y;
    } else {
      y = p_from_point.y;
    }

    return new FloatPoint(x, y);
  }

  /**
   * Calculates the sorted p_max_result_points nearest points on the border of this box. p_point is
   * assumed to be located in the interior of this nox. The function is only implemented for
   * p_max_result_points {@literal <}= 2;
   */
  public IntPoint[] nearest_border_projections(IntPoint p_point, int p_max_result_points) {
    if (p_max_result_points <= 0) {
      return new IntPoint[0];
    }
    p_max_result_points = Math.min(p_max_result_points, 2);
    IntPoint[] result = new IntPoint[p_max_result_points];

    int lowerXDiff = p_point.x - ll.x;
    int upperXDiff = ur.x - p_point.x;
    int lowerYDiff = p_point.y - ll.y;
    int upperYDiff = ur.y - p_point.y;

    int minDiff;
    int secondMinDiff;

    int nearestProjectionX = p_point.x;
    int nearestProjectionY = p_point.y;
    int secondNearestProjectionX = p_point.x;
    int secondNearestProjectionY = p_point.y;
    if (lowerXDiff <= upperXDiff) {
      minDiff = lowerXDiff;
      secondMinDiff = upperXDiff;
      nearestProjectionX = ll.x;
      secondNearestProjectionX = ur.x;
    } else {
      minDiff = upperXDiff;
      secondMinDiff = lowerXDiff;
      nearestProjectionX = ur.x;
      secondNearestProjectionX = ll.x;
    }
    if (lowerYDiff < minDiff) {
      secondMinDiff = minDiff;
      minDiff = lowerYDiff;
      secondNearestProjectionX = nearestProjectionX;
      secondNearestProjectionY = nearestProjectionY;
      nearestProjectionX = p_point.x;
      nearestProjectionY = ll.y;
    } else if (lowerYDiff < secondMinDiff) {
      secondMinDiff = lowerYDiff;
      secondNearestProjectionX = p_point.x;
      secondNearestProjectionY = ll.y;
    }
    if (upperYDiff < minDiff) {
      secondMinDiff = minDiff;
      minDiff = upperYDiff;
      secondNearestProjectionX = nearestProjectionX;
      secondNearestProjectionY = nearestProjectionY;
      nearestProjectionX = p_point.x;
      nearestProjectionY = ur.y;
    } else if (upperYDiff < secondMinDiff) {
      secondMinDiff = upperYDiff;
      secondNearestProjectionX = p_point.x;
      secondNearestProjectionY = ur.y;
    }
    result[0] = new IntPoint(nearestProjectionX, nearestProjectionY);
    if (result.length > 1) {
      result[1] = new IntPoint(secondNearestProjectionX, secondNearestProjectionY);
    }

    return result;
  }

  /** Calculates distance of this box to p_from_point. */
  @Override
  public double distance(FloatPoint p_from_point) {
    return p_from_point.distance(nearest_point(p_from_point));
  }

  /** Computes the weighted distance to the box p_other. */
  public double weighted_distance(
      IntBox p_other, double p_horizontal_weight, double p_vertical_weight) {
    double result;

    double maxLlX = Math.max(this.ll.x, p_other.ll.x);
    double maxLlY = Math.max(this.ll.y, p_other.ll.y);
    double minUrX = Math.min(this.ur.x, p_other.ur.x);
    double minUrY = Math.min(this.ur.y, p_other.ur.y);

    if (minUrX >= maxLlX) {
      result = Math.max(p_vertical_weight * (maxLlY - minUrY), 0);
    } else if (minUrY >= maxLlY) {
      result = Math.max(p_horizontal_weight * (maxLlX - minUrX), 0);
    } else {
      double deltaX = maxLlX - minUrX;
      double deltaY = maxLlY - minUrY;
      deltaX *= p_horizontal_weight;
      deltaY *= p_vertical_weight;
      result = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }
    return result;
  }

  @Override
  public IntBox bounding_box() {
    return this;
  }

  public int get_id_no() {
    return 31 * ll.get_id_no() + ur.get_id_no();
  }

  @Override
  public IntOctagon bounding_octagon() {
    return to_IntOctagon();
  }

  @Override
  public boolean is_bounded() {
    return true;
  }

  @Override
  public IntBox bounding_tile() {
    return this;
  }

  @Override
  public boolean corner_is_bounded(int p_no) {
    return true;
  }

  @Override
  public RegularTileShape union(RegularTileShape p_other) {
    return p_other.union(this);
  }

  @Override
  public IntBox union(IntBox p_other) {
    int llx = Math.min(ll.x, p_other.ll.x);
    int lly = Math.min(ll.y, p_other.ll.y);
    int urx = Math.max(ur.x, p_other.ur.x);
    int ury = Math.max(ur.y, p_other.ur.y);
    return new IntBox(llx, lly, urx, ury);
  }

  /** Returns the intersection of this box with an IntBox. */
  @Override
  public IntBox intersection(IntBox p_other) {
    if (p_other.ll.x > ur.x) {
      return EMPTY;
    }
    if (p_other.ll.y > ur.y) {
      return EMPTY;
    }
    if (ll.x > p_other.ur.x) {
      return EMPTY;
    }
    if (ll.y > p_other.ur.y) {
      return EMPTY;
    }
    int llx = Math.max(ll.x, p_other.ll.x);
    int urx = Math.min(ur.x, p_other.ur.x);
    int lly = Math.max(ll.y, p_other.ll.y);
    int ury = Math.min(ur.y, p_other.ur.y);
    return new IntBox(llx, lly, urx, ury);
  }

  /** returns the intersection of this box with a ConvexShape */
  @Override
  public TileShape intersection(TileShape p_other) {
    return p_other.intersection(this);
  }

  @Override
  IntOctagon intersection(IntOctagon p_other) {
    return p_other.intersection(this.to_IntOctagon());
  }

  @Override
  Simplex intersection(Simplex p_other) {
    return p_other.intersection(this.to_Simplex());
  }

  @Override
  public boolean intersects(Shape p_other) {
    return p_other.intersects(this);
  }

  @Override
  public boolean intersects(IntBox p_other) {
    if (p_other.ll.x > this.ur.x) {
      return false;
    }
    if (p_other.ll.y > this.ur.y) {
      return false;
    }
    if (this.ll.x > p_other.ur.x) {
      return false;
    }
    return this.ll.y <= p_other.ur.y;
  }

  /** Returns true, if this box intersects with p_other and the intersection is 2-dimensional. */
  public boolean overlaps(IntBox p_other) {
    if (p_other.ll.x >= this.ur.x) {
      return false;
    }
    if (p_other.ll.y >= this.ur.y) {
      return false;
    }
    if (this.ll.x >= p_other.ur.x) {
      return false;
    }
    return this.ll.y < p_other.ur.y;
  }

  @Override
  public boolean contains(RegularTileShape p_other) {
    return p_other.is_contained_in(this);
  }

  @Override
  public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs) {
    return p_dirs.bounds(this);
  }

  /**
   * Enlarges the box by p_offset. Contrary to the offset() method the result is an IntOctagon, not
   * an IntBox.
   */
  @Override
  public IntOctagon enlarge(double p_offset) {
    return bounding_octagon().offset(p_offset);
  }

  @Override
  public IntBox translate_by(Vector p_rel_coor) {
    // This function is at the moment only implemented for Vectors
    // with integer coordinates.
    // The general implementation is still missing.

    if (p_rel_coor.equals(Vector.ZERO)) {
      return this;
    }
    IntPoint newLl = (IntPoint) ll.translate_by(p_rel_coor);
    IntPoint newUr = (IntPoint) ur.translate_by(p_rel_coor);
    return new IntBox(newLl, newUr);
  }

  @Override
  public IntBox turn_90_degree(int p_factor, IntPoint p_pole) {
    IntPoint p1 = (IntPoint) ll.turn_90_degree(p_factor, p_pole);
    IntPoint p2 = (IntPoint) ur.turn_90_degree(p_factor, p_pole);

    int llx = Math.min(p1.x, p2.x);
    int lly = Math.min(p1.y, p2.y);
    int urx = Math.max(p1.x, p2.x);
    int ury = Math.max(p1.y, p2.y);
    return new IntBox(llx, lly, urx, ury);
  }

  @Override
  public Line border_line(int p_no) {
    int aX;
    int aY;
    int bX;
    int bY;
    switch (p_no) {
      case 0 -> {
        // lower boundary line
        aX = 0;
        aY = ll.y;
        bX = 1;
        bY = ll.y;
      }
      case 1 -> {
        // right boundary line
        aX = ur.x;
        aY = 0;
        bX = ur.x;
        bY = 1;
      }
      case 2 -> {
        // upper boundary line
        aX = 0;
        aY = ur.y;
        bX = -1;
        bY = ur.y;
      }
      case 3 -> {
        // left boundary line
        aX = ll.x;
        aY = 0;
        bX = ll.x;
        bY = -1;
      }
      default -> throw new IllegalArgumentException("IntBox.edge_line: p_no out of range");
    }
    return new Line(aX, aY, bX, bY);
  }

  @Override
  public int border_line_index(Line p_line) {
    FRLogger.warn("edge_index_of_line not yet implemented for IntBoxes");
    return -1;
  }

  /**
   * Returns the box offsetted by p_dist. If p_dist {@literal >} 0, the offset is to the outside,
   * else to the inside.
   */
  @Override
  public IntBox offset(double p_dist) {
    if (p_dist == 0 || is_empty()) {
      return this;
    }
    int dist = (int) Math.round(p_dist);
    IntPoint lowerLeft = new IntPoint(ll.x - dist, ll.y - dist);
    IntPoint upperRight = new IntPoint(ur.x + dist, ur.y + dist);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Returns the box, where the horizontal boundary is offsetted by p_dist. If p_dist {@literal >}
   * 0, the offset is to the outside, else to the inside.
   */
  public IntBox horizontal_offset(double p_dist) {
    if (p_dist == 0 || is_empty()) {
      return this;
    }
    int dist = (int) Math.round(p_dist);
    IntPoint lowerLeft = new IntPoint(ll.x - dist, ll.y);
    IntPoint upperRight = new IntPoint(ur.x + dist, ur.y);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Returns the box, where the vertical boundary is offsetted by p_dist. If p_dist {@literal >} 0,
   * the offset is to the outside, else to the inside.
   */
  public IntBox vertical_offset(double p_dist) {
    if (p_dist == 0 || is_empty()) {
      return this;
    }
    int dist = (int) Math.round(p_dist);
    IntPoint lowerLeft = new IntPoint(ll.x, ll.y - dist);
    IntPoint upperRight = new IntPoint(ur.x, ur.y + dist);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Shrinks the width and height of the box by the input width. The box will not vanish completely.
   */
  public IntBox shrink(int p_width) {
    int llX;
    int urX;
    if (2 * p_width <= this.ur.x - this.ll.x) {
      llX = this.ll.x + p_width;
      urX = this.ur.x - p_width;
    } else {
      llX = (this.ll.x + this.ur.x) / 2;
      urX = llX;
    }
    int llY;
    int urY;
    if (2 * p_width <= this.ur.y - this.ll.y) {
      llY = this.ll.y + p_width;
      urY = this.ur.y - p_width;
    } else {
      llY = (this.ll.y + this.ur.y) / 2;
      urY = llY;
    }
    return new IntBox(llX, llY, urX, urY);
  }

  @Override
  public Side compare(RegularTileShape p_other, int p_edge_no) {
    Side result = p_other.compare(this, p_edge_no);
    return result.negate();
  }

  @Override
  public Side compare(IntBox p_other, int p_edge_no) {
    Side result;
    switch (p_edge_no) {
      case 0 -> {
        // compare the lower edge line
        if (ll.y > p_other.ll.y) {
          result = Side.ON_THE_LEFT;
        } else if (ll.y < p_other.ll.y) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 1 -> {
        // compare the right edge line
        if (ur.x < p_other.ur.x) {
          result = Side.ON_THE_LEFT;
        } else if (ur.x > p_other.ur.x) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 2 -> {
        // compare the upper edge line
        if (ur.y < p_other.ur.y) {
          result = Side.ON_THE_LEFT;
        } else if (ur.y > p_other.ur.y) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 3 -> {
        // compare the left edge line
        if (ll.x > p_other.ll.x) {
          result = Side.ON_THE_LEFT;
        } else if (ll.x < p_other.ll.x) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      default -> throw new IllegalArgumentException("IntBox.compare: p_edge_no out of range");
    }
    return result;
  }

  /** Returns an object of class IntOctagon defining the same shape */
  public IntOctagon to_IntOctagon() {
    return new IntOctagon(
        ll.x, ll.y, ur.x, ur.y, ll.x - ur.y, ur.x - ll.y, ll.x + ll.y, ur.x + ur.y);
  }

  /** Returns an object of class Simplex defining the same shape */
  @Override
  public Simplex to_Simplex() {
    Line[] lineArr;
    if (is_empty()) {
      lineArr = new Line[0];
    } else {
      lineArr = new Line[4];
      lineArr[0] = Line.get_instance(ll, IntDirection.RIGHT);
      lineArr[1] = Line.get_instance(ur, IntDirection.UP);
      lineArr[2] = Line.get_instance(ur, IntDirection.LEFT);
      lineArr[3] = Line.get_instance(ll, IntDirection.DOWN);
    }
    return new Simplex(lineArr);
  }

  @Override
  public boolean is_contained_in(IntBox p_other) {
    if (is_empty() || this == p_other) {
      return true;
    }
    return ll.x >= p_other.ll.x
        && ll.y >= p_other.ll.y
        && ur.x <= p_other.ur.x
        && ur.y <= p_other.ur.y;
  }

  /** Return true, if p_other is contained in the interior of this box. */
  public boolean contains_in_interior(IntBox p_other) {
    if (p_other.is_empty()) {
      return true;
    }
    return p_other.ll.x > ll.x && p_other.ll.y > ll.y && p_other.ur.x < ur.x && p_other.ur.y < ur.y;
  }

  /** Calculates the part of p_from_box, which has minimal distance to this box. */
  public IntBox nearest_part(IntBox p_from_box) {
    int llX;

    if (p_from_box.ll.x >= this.ll.x) {
      llX = p_from_box.ll.x;
    } else {
      llX = Math.min(p_from_box.ur.x, this.ll.x);
    }

    int urX;

    if (p_from_box.ur.x <= this.ur.x) {
      urX = p_from_box.ur.x;
    } else {
      urX = Math.max(p_from_box.ll.x, this.ur.x);
    }

    int llY;

    if (p_from_box.ll.y >= this.ll.y) {
      llY = p_from_box.ll.y;
    } else {
      llY = Math.min(p_from_box.ur.y, this.ll.y);
    }

    int urY;

    if (p_from_box.ur.y <= this.ur.y) {
      urY = p_from_box.ur.y;
    } else {
      urY = Math.max(p_from_box.ll.y, this.ur.y);
    }
    return new IntBox(llX, llY, urX, urY);
  }

  @Override
  public boolean is_contained_in(IntOctagon p_other) {
    return p_other.contains(to_IntOctagon());
  }

  @Override
  public boolean intersects(IntOctagon p_other) {
    return p_other.intersects(to_IntOctagon());
  }

  @Override
  public boolean intersects(Simplex p_other) {
    return p_other.intersects(to_Simplex());
  }

  @Override
  public boolean intersects(Circle p_other) {
    return p_other.intersects(this);
  }

  @Override
  public IntOctagon union(IntOctagon p_other) {
    return p_other.union(to_IntOctagon());
  }

  @Override
  public Side compare(IntOctagon p_other, int p_edge_no) {
    return to_IntOctagon().compare(p_other, p_edge_no);
  }

  /**
   * Divides this box into sections with width and height at most p_max_section_width of about equal
   * size.
   */
  @Override
  public IntBox[] divide_into_sections(double p_max_section_width) {
    if (p_max_section_width <= 0) {
      return new IntBox[0];
    }
    double length = this.ur.x - this.ll.x;
    double height = this.ur.y - this.ll.y;
    int xCount = (int) Math.ceil(length / p_max_section_width);
    int yCount = (int) Math.ceil(height / p_max_section_width);
    int sectionLengthX = (int) Math.ceil(length / xCount);
    int sectionLengthY = (int) Math.ceil(height / yCount);
    IntBox[] result = new IntBox[xCount * yCount];
    int currIndex = 0;
    for (int j = 0; j < yCount; j++) {
      int currLly = this.ll.y + j * sectionLengthY;
      int currUry;
      if (j == (yCount - 1)) {
        currUry = this.ur.y;
      } else {
        currUry = currLly + sectionLengthY;
      }
      for (int i = 0; i < xCount; i++) {
        int currLlx = this.ll.x + i * sectionLengthX;
        int currUrx;
        if (i == (xCount - 1)) {
          currUrx = this.ur.x;
        } else {
          currUrx = currLlx + sectionLengthX;
        }
        result[currIndex] = new IntBox(currLlx, currLly, currUrx, currUry);
        ++currIndex;
      }
    }
    return result;
  }

  @Override
  public TileShape[] cutout(TileShape p_shape) {
    TileShape[] tmpResult = p_shape.cutout_from(this);
    TileShape[] result = new TileShape[tmpResult.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = tmpResult[i].simplify();
    }
    return result;
  }

  @Override
  IntBox[] cutout_from(IntBox p_d) {
    IntBox c = this.intersection(p_d);
    if (this.is_empty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntBox[] result = new IntBox[1];
      result[0] = p_d;
      return result;
    }

    IntBox[] result = new IntBox[4];

    result[0] = new IntBox(p_d.ll.x, p_d.ll.y, c.ur.x, c.ll.y);

    result[1] = new IntBox(p_d.ll.x, c.ll.y, c.ll.x, p_d.ur.y);

    result[2] = new IntBox(c.ur.x, p_d.ll.y, p_d.ur.x, c.ur.y);

    result[3] = new IntBox(c.ll.x, c.ur.y, p_d.ur.x, p_d.ur.y);

    // now the division will be optimised, so that the cumulative
    // circumference will be minimal.

    IntBox b;

    if (c.ll.x - p_d.ll.x > c.ll.y - p_d.ll.y) {
      // switch left dividing line to lower
      b = result[0];
      result[0] = new IntBox(c.ll.x, b.ll.y, b.ur.x, b.ur.y);
      b = result[1];
      result[1] = new IntBox(b.ll.x, p_d.ll.y, b.ur.x, b.ur.y);
    }
    if (p_d.ur.y - c.ur.y > c.ll.x - p_d.ll.x) {
      // switch upper dividing line to the left
      b = result[1];
      result[1] = new IntBox(b.ll.x, b.ll.y, b.ur.x, c.ur.y);
      b = result[3];
      result[3] = new IntBox(p_d.ll.x, b.ll.y, b.ur.x, b.ur.y);
    }
    if (p_d.ur.x - c.ur.x > p_d.ur.y - c.ur.y) {
      // switch right dividing line to upper
      b = result[2];
      result[2] = new IntBox(b.ll.x, b.ll.y, b.ur.x, p_d.ur.y);
      b = result[3];
      result[3] = new IntBox(b.ll.x, b.ll.y, c.ur.x, b.ur.y);
    }
    if (c.ll.y - p_d.ll.y > p_d.ur.x - c.ur.x) {
      // switch lower dividing line to the left
      b = result[0];
      result[0] = new IntBox(b.ll.x, b.ll.y, p_d.ur.x, b.ur.y);
      b = result[2];
      result[2] = new IntBox(b.ll.x, c.ll.y, b.ur.x, b.ur.y);
    }
    return result;
  }

  @Override
  Simplex[] cutout_from(Simplex p_simplex) {
    return this.to_Simplex().cutout_from(p_simplex);
  }

  @Override
  IntOctagon[] cutout_from(IntOctagon p_oct) {
    return this.to_IntOctagon().cutout_from(p_oct);
  }
}

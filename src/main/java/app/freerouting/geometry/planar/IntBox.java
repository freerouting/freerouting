package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/** Implements functionality of orthogonal rectangles in the plane with integer coordinates. */
public class IntBox extends RegularTileShape implements Serializable {

  /** Standard implementation of an empty box. */
  public static final IntBox EMPTY =
      new IntBox(Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT, -Limits.CRIT_INT);

  /** Stores the coordinates of the lower-left corner. */
  public final IntPoint ll;

  /** Stores the coordinates of the upper-right corner. */
  public final IntPoint ur;

  /** Creates an IntBox from its lower left and upper right corners. */
  public IntBox(IntPoint ll, IntPoint ur) {
    this.ll = ll;
    this.ur = ur;
  }

  /** Creates an IntBox from the coordinates of its lower-left and upper-right corners. */
  public IntBox(int llX, int llY, int urX, int urY) {
    ll = new IntPoint(llX, llY);
    ur = new IntPoint(urX, urY);
  }

  @Override
  public boolean isIntOctagon() {
    return true;
  }

  /** Returns true, if the box is empty. */
  @Override
  public boolean isEmpty() {
    return ll.x > ur.x || ll.y > ur.y;
  }

  @Override
  public int borderLineCount() {
    return 4;
  }

  /** Returns the horizontal extension of the box. */
  public int width() {
    return ur.x - ll.x;
  }

  /** Returns the vertical extension of the box. */
  public int height() {
    return ur.y - ll.y;
  }

  @Override
  public double maxWidth() {
    return Math.max(ur.x - ll.x, ur.y - ll.y);
  }

  @Override
  public double minWidth() {
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
  public IntPoint corner(int no) {
    if (no == 0) {
      return ll;
    }
    if (no == 1) {
      return new IntPoint(ur.x, ll.y);
    }
    if (no == 2) {
      return ur;
    }
    if (no == 3) {
      return new IntPoint(ll.x, ur.y);
    }
    throw new IllegalArgumentException("IntBox.corner: p_no out of range");
  }

  @Override
  public int dimension() {
    if (isEmpty()) {
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
  public boolean containsInside(IntPoint point) {
    return point.x > this.ll.x && point.x < this.ur.x && point.y > this.ll.y && point.y < this.ur.y;
  }

  @Override
  public boolean isIntBox() {
    return true;
  }

  @Override
  public TileShape simplify() {
    return this;
  }

  /** Calculates the nearest point of this box to p_from_point. */
  public FloatPoint nearestPoint(FloatPoint fromPoint) {
    double x;
    if (fromPoint.x <= ll.x) {
      x = ll.x;
    } else if (fromPoint.x >= ur.x) {
      x = ur.x;
    } else {
      x = fromPoint.x;
    }

    double y;
    if (fromPoint.y <= ll.y) {
      y = ll.y;
    } else if (fromPoint.y >= ur.y) {
      y = ur.y;
    } else {
      y = fromPoint.y;
    }

    return new FloatPoint(x, y);
  }

  /**
   * Calculates the sorted p_max_result_points nearest points on the border of this box. p_point is
   * assumed to be located in the interior of this nox. The function is only implemented for
   * p_max_result_points {@literal <}= 2;
   */
  public IntPoint[] nearestBorderProjections(IntPoint point, int maxResultPoints) {
    if (maxResultPoints <= 0) {
      return new IntPoint[0];
    }
    maxResultPoints = Math.min(maxResultPoints, 2);
    int lowerHorizontalDifference = point.x - ll.x;
    int upperHorizontalDifference = ur.x - point.x;
    int lowerVerticalDifference = point.y - ll.y;
    int upperVerticalDifference = ur.y - point.y;

    int minDiff;
    int secondMinDiff;

    int nearestProjectionX = point.x;
    int nearestProjectionY = point.y;
    int secondNearestProjectionX = point.x;
    int secondNearestProjectionY = point.y;
    if (lowerHorizontalDifference <= upperHorizontalDifference) {
      minDiff = lowerHorizontalDifference;
      secondMinDiff = upperHorizontalDifference;
      nearestProjectionX = ll.x;
      secondNearestProjectionX = ur.x;
    } else {
      minDiff = upperHorizontalDifference;
      secondMinDiff = lowerHorizontalDifference;
      nearestProjectionX = ur.x;
      secondNearestProjectionX = ll.x;
    }
    if (lowerVerticalDifference < minDiff) {
      secondMinDiff = minDiff;
      minDiff = lowerVerticalDifference;
      secondNearestProjectionX = nearestProjectionX;
      secondNearestProjectionY = nearestProjectionY;
      nearestProjectionX = point.x;
      nearestProjectionY = ll.y;
    } else if (lowerVerticalDifference < secondMinDiff) {
      secondMinDiff = lowerVerticalDifference;
      secondNearestProjectionX = point.x;
      secondNearestProjectionY = ll.y;
    }
    if (upperVerticalDifference < minDiff) {
      secondMinDiff = minDiff;
      minDiff = upperVerticalDifference;
      secondNearestProjectionX = nearestProjectionX;
      secondNearestProjectionY = nearestProjectionY;
      nearestProjectionX = point.x;
      nearestProjectionY = ur.y;
    } else if (upperVerticalDifference < secondMinDiff) {
      secondMinDiff = upperVerticalDifference;
      secondNearestProjectionX = point.x;
      secondNearestProjectionY = ur.y;
    }
    IntPoint[] result = new IntPoint[maxResultPoints];
    result[0] = new IntPoint(nearestProjectionX, nearestProjectionY);
    if (result.length > 1) {
      result[1] = new IntPoint(secondNearestProjectionX, secondNearestProjectionY);
    }

    return result;
  }

  /** Calculates distance of this box to p_from_point. */
  @Override
  public double distance(FloatPoint fromPoint) {
    return fromPoint.distance(nearestPoint(fromPoint));
  }

  /** Computes the weighted distance to the box p_other. */
  public double weightedDistance(IntBox other, double horizontalWeight, double verticalWeight) {
    double result;

    double maxLlX = Math.max(this.ll.x, other.ll.x);
    double maxLlY = Math.max(this.ll.y, other.ll.y);
    double minUrX = Math.min(this.ur.x, other.ur.x);
    double minUrY = Math.min(this.ur.y, other.ur.y);

    if (minUrX >= maxLlX) {
      result = Math.max(verticalWeight * (maxLlY - minUrY), 0);
    } else if (minUrY >= maxLlY) {
      result = Math.max(horizontalWeight * (maxLlX - minUrX), 0);
    } else {
      double deltaX = maxLlX - minUrX;
      double deltaY = maxLlY - minUrY;
      deltaX *= horizontalWeight;
      deltaY *= verticalWeight;
      result = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    }
    return result;
  }

  @Override
  public IntBox boundingBox() {
    return this;
  }

  public int getIdNo() {
    return 31 * ll.getIdNo() + ur.getIdNo();
  }

  @Override
  public IntOctagon boundingOctagon() {
    return toIntOctagon();
  }

  @Override
  public boolean isBounded() {
    return true;
  }

  @Override
  public IntBox boundingTile() {
    return this;
  }

  @Override
  public boolean cornerIsBounded(int no) {
    return true;
  }

  @Override
  public RegularTileShape union(RegularTileShape other) {
    return other.union(this);
  }

  @Override
  public IntBox union(IntBox other) {
    int llx = Math.min(ll.x, other.ll.x);
    int lly = Math.min(ll.y, other.ll.y);
    int urx = Math.max(ur.x, other.ur.x);
    int ury = Math.max(ur.y, other.ur.y);
    return new IntBox(llx, lly, urx, ury);
  }

  @Override
  public IntOctagon union(IntOctagon other) {
    return other.union(toIntOctagon());
  }

  /** Returns the intersection of this box with an IntBox. */
  @Override
  public IntBox intersection(IntBox other) {
    if (other.ll.x > ur.x) {
      return EMPTY;
    }
    if (other.ll.y > ur.y) {
      return EMPTY;
    }
    if (ll.x > other.ur.x) {
      return EMPTY;
    }
    if (ll.y > other.ur.y) {
      return EMPTY;
    }
    int llx = Math.max(ll.x, other.ll.x);
    int urx = Math.min(ur.x, other.ur.x);
    int lly = Math.max(ll.y, other.ll.y);
    int ury = Math.min(ur.y, other.ur.y);
    return new IntBox(llx, lly, urx, ury);
  }

  /** Returns the intersection of this box with a ConvexShape. */
  @Override
  public TileShape intersection(TileShape other) {
    return other.intersection(this);
  }

  @Override
  IntOctagon intersection(IntOctagon other) {
    return other.intersection(this.toIntOctagon());
  }

  @Override
  Simplex intersection(Simplex other) {
    return other.intersection(this.toSimplex());
  }

  @Override
  public boolean intersects(Shape other) {
    return other.intersects(this);
  }

  @Override
  public boolean intersects(IntBox other) {
    if (other.ll.x > this.ur.x) {
      return false;
    }
    if (other.ll.y > this.ur.y) {
      return false;
    }
    if (this.ll.x > other.ur.x) {
      return false;
    }
    return this.ll.y <= other.ur.y;
  }

  @Override
  public boolean intersects(IntOctagon other) {
    return other.intersects(toIntOctagon());
  }

  @Override
  public boolean intersects(Simplex other) {
    return other.intersects(toSimplex());
  }

  @Override
  public boolean intersects(Circle other) {
    return other.intersects(this);
  }

  /** Returns true, if this box intersects with p_other and the intersection is 2-dimensional. */
  public boolean overlaps(IntBox other) {
    if (other.ll.x >= this.ur.x) {
      return false;
    }
    if (other.ll.y >= this.ur.y) {
      return false;
    }
    if (this.ll.x >= other.ur.x) {
      return false;
    }
    return this.ll.y < other.ur.y;
  }

  @Override
  public boolean contains(RegularTileShape other) {
    return other.isContainedIn(this);
  }

  @Override
  public RegularTileShape boundingShape(ShapeBoundingDirections dirs) {
    return dirs.bounds(this);
  }

  /**
   * Enlarges the box by p_offset. Contrary to the offset() method the result is an IntOctagon, not
   * an IntBox.
   */
  @Override
  public IntOctagon enlarge(double offset) {
    return boundingOctagon().offset(offset);
  }

  @Override
  public IntBox translateBy(Vector relCoor) {
    // This function is at the moment only implemented for Vectors
    // with integer coordinates.
    // The general implementation is still missing.

    if (relCoor.equals(Vector.ZERO)) {
      return this;
    }
    IntPoint newLl = (IntPoint) ll.translateBy(relCoor);
    IntPoint newUr = (IntPoint) ur.translateBy(relCoor);
    return new IntBox(newLl, newUr);
  }

  @Override
  public IntBox turn90Degree(int factor, IntPoint pole) {
    IntPoint p1 = (IntPoint) ll.turn90Degree(factor, pole);
    IntPoint p2 = (IntPoint) ur.turn90Degree(factor, pole);

    int llx = Math.min(p1.x, p2.x);
    int lly = Math.min(p1.y, p2.y);
    int urx = Math.max(p1.x, p2.x);
    int ury = Math.max(p1.y, p2.y);
    return new IntBox(llx, lly, urx, ury);
  }

  @Override
  public Line borderLine(int no) {
    int ax;
    int ay;
    int bx;
    int by;
    switch (no) {
      case 0 -> {
        // lower boundary line
        ax = 0;
        ay = ll.y;
        bx = 1;
        by = ll.y;
      }
      case 1 -> {
        // right boundary line
        ax = ur.x;
        ay = 0;
        bx = ur.x;
        by = 1;
      }
      case 2 -> {
        // upper boundary line
        ax = 0;
        ay = ur.y;
        bx = -1;
        by = ur.y;
      }
      case 3 -> {
        // left boundary line
        ax = ll.x;
        ay = 0;
        bx = ll.x;
        by = -1;
      }
      default -> throw new IllegalArgumentException("IntBox.edge_line: p_no out of range");
    }
    return new Line(ax, ay, bx, by);
  }

  @Override
  public int borderLineIndex(Line line) {
    FRLogger.warn("edge_index_of_line not yet implemented for IntBoxes");
    return -1;
  }

  /**
   * Returns the box offsetted by p_dist. If p_dist {@literal >} 0, the offset is to the outside,
   * else to the inside.
   */
  @Override
  public IntBox offset(double dist) {
    if (dist == 0 || isEmpty()) {
      return this;
    }
    int roundedDistance = (int) Math.round(dist);
    IntPoint lowerLeft = new IntPoint(ll.x - roundedDistance, ll.y - roundedDistance);
    IntPoint upperRight = new IntPoint(ur.x + roundedDistance, ur.y + roundedDistance);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Returns the box, where the horizontal boundary is offsetted by p_dist. If p_dist {@literal >}
   * 0, the offset is to the outside, else to the inside.
   */
  public IntBox horizontalOffset(double dist) {
    if (dist == 0 || isEmpty()) {
      return this;
    }
    int roundedDistance = (int) Math.round(dist);
    IntPoint lowerLeft = new IntPoint(ll.x - roundedDistance, ll.y);
    IntPoint upperRight = new IntPoint(ur.x + roundedDistance, ur.y);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Returns the box, where the vertical boundary is offsetted by p_dist. If p_dist {@literal >} 0,
   * the offset is to the outside, else to the inside.
   */
  public IntBox verticalOffset(double dist) {
    if (dist == 0 || isEmpty()) {
      return this;
    }
    int roundedDistance = (int) Math.round(dist);
    IntPoint lowerLeft = new IntPoint(ll.x, ll.y - roundedDistance);
    IntPoint upperRight = new IntPoint(ur.x, ur.y + roundedDistance);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Shrinks the width and height of the box by the input width. The box will not vanish completely.
   */
  public IntBox shrink(int width) {
    int llX;
    int urX;
    if (2 * width <= this.ur.x - this.ll.x) {
      llX = this.ll.x + width;
      urX = this.ur.x - width;
    } else {
      llX = (this.ll.x + this.ur.x) / 2;
      urX = llX;
    }
    int llY;
    int urY;
    if (2 * width <= this.ur.y - this.ll.y) {
      llY = this.ll.y + width;
      urY = this.ur.y - width;
    } else {
      llY = (this.ll.y + this.ur.y) / 2;
      urY = llY;
    }
    return new IntBox(llX, llY, urX, urY);
  }

  @Override
  public Side compare(RegularTileShape other, int edgeNo) {
    Side result = other.compare(this, edgeNo);
    return result.negate();
  }

  @Override
  public Side compare(IntBox other, int edgeNo) {
    Side result;
    switch (edgeNo) {
      case 0 -> {
        // compare the lower edge line
        if (ll.y > other.ll.y) {
          result = Side.ON_THE_LEFT;
        } else if (ll.y < other.ll.y) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 1 -> {
        // compare the right edge line
        if (ur.x < other.ur.x) {
          result = Side.ON_THE_LEFT;
        } else if (ur.x > other.ur.x) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 2 -> {
        // compare the upper edge line
        if (ur.y < other.ur.y) {
          result = Side.ON_THE_LEFT;
        } else if (ur.y > other.ur.y) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 3 -> {
        // compare the left edge line
        if (ll.x > other.ll.x) {
          result = Side.ON_THE_LEFT;
        } else if (ll.x < other.ll.x) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      default -> throw new IllegalArgumentException("IntBox.compare: p_edge_no out of range");
    }
    return result;
  }

  @Override
  public Side compare(IntOctagon other, int edgeNo) {
    return toIntOctagon().compare(other, edgeNo);
  }

  /** Returns an object of class IntOctagon defining the same shape. */
  public IntOctagon toIntOctagon() {
    return new IntOctagon(
        ll.x, ll.y, ur.x, ur.y, ll.x - ur.y, ur.x - ll.y, ll.x + ll.y, ur.x + ur.y);
  }

  /** Returns an object of class Simplex defining the same shape. */
  @Override
  public Simplex toSimplex() {
    Line[] lineArr;
    if (isEmpty()) {
      lineArr = new Line[0];
    } else {
      lineArr = new Line[4];
      lineArr[0] = Line.getInstance(ll, IntDirection.RIGHT);
      lineArr[1] = Line.getInstance(ur, IntDirection.UP);
      lineArr[2] = Line.getInstance(ur, IntDirection.LEFT);
      lineArr[3] = Line.getInstance(ll, IntDirection.DOWN);
    }
    return new Simplex(lineArr);
  }

  @Override
  public boolean isContainedIn(IntBox other) {
    if (isEmpty() || this == other) {
      return true;
    }
    return ll.x >= other.ll.x && ll.y >= other.ll.y && ur.x <= other.ur.x && ur.y <= other.ur.y;
  }

  @Override
  public boolean isContainedIn(IntOctagon other) {
    return other.contains(toIntOctagon());
  }

  /** Return true, if p_other is contained in the interior of this box. */
  public boolean containsInInterior(IntBox other) {
    if (other.isEmpty()) {
      return true;
    }
    return other.ll.x > ll.x && other.ll.y > ll.y && other.ur.x < ur.x && other.ur.y < ur.y;
  }

  /** Calculates the part of p_from_box, which has minimal distance to this box. */
  public IntBox nearestPart(IntBox fromBox) {
    int llX;

    if (fromBox.ll.x >= this.ll.x) {
      llX = fromBox.ll.x;
    } else {
      llX = Math.min(fromBox.ur.x, this.ll.x);
    }

    int urX;

    if (fromBox.ur.x <= this.ur.x) {
      urX = fromBox.ur.x;
    } else {
      urX = Math.max(fromBox.ll.x, this.ur.x);
    }

    int llY;

    if (fromBox.ll.y >= this.ll.y) {
      llY = fromBox.ll.y;
    } else {
      llY = Math.min(fromBox.ur.y, this.ll.y);
    }

    int urY;

    if (fromBox.ur.y <= this.ur.y) {
      urY = fromBox.ur.y;
    } else {
      urY = Math.max(fromBox.ll.y, this.ur.y);
    }
    return new IntBox(llX, llY, urX, urY);
  }

  /**
   * Divides this box into sections with width and height at most p_max_section_width of about equal
   * size.
   */
  @Override
  public IntBox[] divideIntoSections(double maxSectionWidth) {
    if (maxSectionWidth <= 0) {
      return new IntBox[0];
    }
    double length = this.ur.x - this.ll.x;
    double height = this.ur.y - this.ll.y;
    int xcount = (int) Math.ceil(length / maxSectionWidth);
    int ycount = (int) Math.ceil(height / maxSectionWidth);
    int sectionLengthX = (int) Math.ceil(length / xcount);
    int sectionLengthY = (int) Math.ceil(height / ycount);
    IntBox[] result = new IntBox[xcount * ycount];
    int currentIndex = 0;
    for (int j = 0; j < ycount; j++) {
      int currentLly = this.ll.y + j * sectionLengthY;
      int currentUry;
      if (j == (ycount - 1)) {
        currentUry = this.ur.y;
      } else {
        currentUry = currentLly + sectionLengthY;
      }
      for (int i = 0; i < xcount; i++) {
        int currentLlx = this.ll.x + i * sectionLengthX;
        int currentUrx;
        if (i == (xcount - 1)) {
          currentUrx = this.ur.x;
        } else {
          currentUrx = currentLlx + sectionLengthX;
        }
        result[currentIndex] = new IntBox(currentLlx, currentLly, currentUrx, currentUry);
        ++currentIndex;
      }
    }
    return result;
  }

  @Override
  public TileShape[] cutout(TileShape shape) {
    TileShape[] tmpResult = shape.cutoutFrom(this);
    TileShape[] result = new TileShape[tmpResult.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = tmpResult[i].simplify();
    }
    return result;
  }

  @Override
  IntBox[] cutoutFrom(IntBox d) {
    IntBox c = this.intersection(d);
    if (this.isEmpty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntBox[] result = new IntBox[1];
      result[0] = d;
      return result;
    }

    IntBox[] result = new IntBox[4];

    result[0] = new IntBox(d.ll.x, d.ll.y, c.ur.x, c.ll.y);

    result[1] = new IntBox(d.ll.x, c.ll.y, c.ll.x, d.ur.y);

    result[2] = new IntBox(c.ur.x, d.ll.y, d.ur.x, c.ur.y);

    result[3] = new IntBox(c.ll.x, c.ur.y, d.ur.x, d.ur.y);

    // now the division will be optimised, so that the cumulative
    // circumference will be minimal.

    IntBox b;

    if (c.ll.x - d.ll.x > c.ll.y - d.ll.y) {
      // switch left dividing line to lower
      b = result[0];
      result[0] = new IntBox(c.ll.x, b.ll.y, b.ur.x, b.ur.y);
      b = result[1];
      result[1] = new IntBox(b.ll.x, d.ll.y, b.ur.x, b.ur.y);
    }
    if (d.ur.y - c.ur.y > c.ll.x - d.ll.x) {
      // switch upper dividing line to the left
      b = result[1];
      result[1] = new IntBox(b.ll.x, b.ll.y, b.ur.x, c.ur.y);
      b = result[3];
      result[3] = new IntBox(d.ll.x, b.ll.y, b.ur.x, b.ur.y);
    }
    if (d.ur.x - c.ur.x > d.ur.y - c.ur.y) {
      // switch right dividing line to upper
      b = result[2];
      result[2] = new IntBox(b.ll.x, b.ll.y, b.ur.x, d.ur.y);
      b = result[3];
      result[3] = new IntBox(b.ll.x, b.ll.y, c.ur.x, b.ur.y);
    }
    if (c.ll.y - d.ll.y > d.ur.x - c.ur.x) {
      // switch lower dividing line to the left
      b = result[0];
      result[0] = new IntBox(b.ll.x, b.ll.y, d.ur.x, b.ur.y);
      b = result[2];
      result[2] = new IntBox(b.ll.x, c.ll.y, b.ur.x, b.ur.y);
    }
    return result;
  }

  @Override
  Simplex[] cutoutFrom(Simplex simplex) {
    return this.toSimplex().cutoutFrom(simplex);
  }

  @Override
  IntOctagon[] cutoutFrom(IntOctagon oct) {
    return this.toIntOctagon().cutoutFrom(oct);
  }
}

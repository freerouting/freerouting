package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;

/**
 * IntOctagon is a specialized geometric shape implementation representing an octagon with integer
 * coordinates and 45-degree angle constraints. The class extends RegularTileShape and provides
 * efficient representations for PCB (Printed Circuit Board) routing spaces.
 */
public class IntOctagon extends RegularTileShape implements Serializable {

  /** Reusable instance of an empty octagon. */
  public static final IntOctagon EMPTY =
      new IntOctagon(
          Limits.CRIT_INT,
          Limits.CRIT_INT,
          -Limits.CRIT_INT,
          -Limits.CRIT_INT,
          Limits.CRIT_INT,
          -Limits.CRIT_INT,
          Limits.CRIT_INT,
          -Limits.CRIT_INT);

  /* Vertical boundaries (east/west) */

  // X-coordinate of the left vertical border
  public final int leftX;
  // X-coordinate of the right vertical border
  public final int rightX;

  /* Horizontal boundaries (north/south) */

  // Y-coordinate of the bottom horizontal border
  public final int bottomY;
  // Y-coordinate of the top horizontal border
  public final int topY;

  /* Diagonal boundaries at +45° angle */

  // X-axis intersection of lower-left diagonal border
  public final int lowerLeftDiagonalX;
  // X-axis intersection of upper-right diagonal border
  public final int upperRightDiagonalX;

  /* Diagonal boundaries at -45° angle */

  // X-axis intersection of upper-left diagonal border
  public final int upperLeftDiagonalX;
  // X-axis intersection of lower-right diagonal border
  public final int lowerRightDiagonalX;

  /** Result of to_simplex() memorized for performance reasons. */
  private Simplex precalculatedToSimplex;

  /**
   * Creates an IntOctagon from 8 integer values. p_lx is the smallest x value of the shape. p_ly is
   * the smallest y value of the shape. p_rx is the biggest x value af the shape. p_uy is the
   * biggest y value of the shape. p_ulx is the intersection of the upper left diagonal boundary
   * line with the x axis. p_lrx is the intersection of the lower right diagonal boundary line with
   * the x axis. p_llx is the intersection of the lower left diagonal boundary line with the x axis.
   * p_urx is the intersection of the upper right diagonal boundary line with the x axis.
   */
  public IntOctagon(
      int p_lx, int p_ly, int p_rx, int p_uy, int p_ulx, int p_lrx, int p_llx, int p_urx) {
    leftX = p_lx;
    bottomY = p_ly;
    rightX = p_rx;
    topY = p_uy;
    upperLeftDiagonalX = p_ulx;
    lowerRightDiagonalX = p_lrx;
    lowerLeftDiagonalX = p_llx;
    upperRightDiagonalX = p_urx;
  }

  @Override
  public boolean is_empty() {
    return this == EMPTY;
  }

  @Override
  public boolean is_IntOctagon() {
    return true;
  }

  @Override
  public boolean is_bounded() {
    return true;
  }

  @Override
  public boolean corner_is_bounded(int p_no) {
    return true;
  }

  @Override
  public IntBox bounding_box() {
    return new IntBox(leftX, bottomY, rightX, topY);
  }

  @Override
  public IntOctagon bounding_octagon() {
    return this;
  }

  @Override
  public IntOctagon bounding_tile() {
    return this;
  }

  @Override
  public int dimension() {
    if (this == EMPTY) {
      return -1;
    }
    int result;

    if (rightX > leftX
        && topY > bottomY
        && lowerRightDiagonalX > upperLeftDiagonalX
        && upperRightDiagonalX > lowerLeftDiagonalX) {
      result = 2;
    } else if (rightX == leftX && topY == bottomY) {
      result = 0;
    } else {
      result = 1;
    }
    return result;
  }

  @Override
  public IntPoint corner(int p_no) {

    int x;
    int y;
    switch (p_no) {
      case 0 -> {
        x = lowerLeftDiagonalX - bottomY;
        y = bottomY;
      }
      case 1 -> {
        x = lowerRightDiagonalX + bottomY;
        y = bottomY;
      }
      case 2 -> {
        x = rightX;
        y = rightX - lowerRightDiagonalX;
      }
      case 3 -> {
        x = rightX;
        y = upperRightDiagonalX - rightX;
      }
      case 4 -> {
        x = upperRightDiagonalX - topY;
        y = topY;
      }
      case 5 -> {
        x = upperLeftDiagonalX + topY;
        y = topY;
      }
      case 6 -> {
        x = leftX;
        y = leftX - upperLeftDiagonalX;
      }
      case 7 -> {
        x = leftX;
        y = lowerLeftDiagonalX - leftX;
      }
      default -> throw new IllegalArgumentException("IntOctagon.corner: p_no out of range");
    }
    return new IntPoint(x, y);
  }

  public int get_id_no() {
    int result = leftX;
    result = 31 * result + rightX;
    result = 31 * result + bottomY;
    result = 31 * result + topY;
    result = 31 * result + lowerLeftDiagonalX;
    result = 31 * result + upperRightDiagonalX;
    result = 31 * result + upperLeftDiagonalX;
    return 31 * result + lowerRightDiagonalX;
  }

  /**
   * Additional to the function corner() for performance reasons to avoid allocation of an IntPoint.
   */
  public int corner_y(int p_no) {
    return switch (p_no) {
      case 0, 1 -> bottomY;
      case 2 -> rightX - lowerRightDiagonalX;
      case 3 -> upperRightDiagonalX - rightX;
      case 4, 5 -> topY;
      case 6 -> leftX - upperLeftDiagonalX;
      case 7 -> lowerLeftDiagonalX - leftX;
      default -> throw new IllegalArgumentException("IntOctagon.corner: p_no out of range");
    };
  }

  /**
   * Additional to the function corner() for performance reasons to avoid allocation of an IntPoint.
   */
  public int corner_x(int p_no) {
    return switch (p_no) {
      case 0 -> lowerLeftDiagonalX - bottomY;
      case 1 -> lowerRightDiagonalX + bottomY;
      case 2, 3 -> rightX;
      case 4 -> upperRightDiagonalX - topY;
      case 5 -> upperLeftDiagonalX + topY;
      case 6, 7 -> leftX;
      default -> throw new IllegalArgumentException("IntOctagon.corner: p_no out of range");
    };
  }

  @Override
  public double area() {

    // calculate half of the absolute value of
    // x0 (y1 - y7) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ x7( y0 - y6)
    // where xi, yi are the coordinates of the i-th corner of this Octagon.

    // Overwrites the same implementation in TileShape for performance
    // reasons to avoid Point allocation.

    double result =
        (double) (lowerLeftDiagonalX - bottomY) * (double) (bottomY - lowerLeftDiagonalX + leftX);
    result +=
        (double) (lowerRightDiagonalX + bottomY)
            * (double) (rightX - lowerRightDiagonalX - bottomY);
    result +=
        (double) rightX
            * (double) (upperRightDiagonalX - 2 * rightX - bottomY + topY + lowerRightDiagonalX);
    result +=
        (double) (upperRightDiagonalX - topY) * (double) (topY - upperRightDiagonalX + rightX);
    result += (double) (upperLeftDiagonalX + topY) * (double) (leftX - upperLeftDiagonalX - topY);
    result +=
        (double) leftX
            * (double) (lowerLeftDiagonalX - 2 * leftX - topY + bottomY + upperLeftDiagonalX);

    return 0.5 * Math.abs(result);
  }

  @Override
  public int border_line_count() {
    return 8;
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
        aY = bottomY;
        bX = 1;
        bY = bottomY;
      }
      case 1 -> {
        // lower right boundary line
        aX = lowerRightDiagonalX;
        aY = 0;
        bX = lowerRightDiagonalX + 1;
        bY = 1;
      }
      case 2 -> {
        // right boundary line
        aX = rightX;
        aY = 0;
        bX = rightX;
        bY = 1;
      }
      case 3 -> {
        // upper right boundary line
        aX = upperRightDiagonalX;
        aY = 0;
        bX = upperRightDiagonalX - 1;
        bY = 1;
      }
      case 4 -> {
        // upper boundary line
        aX = 0;
        aY = topY;
        bX = -1;
        bY = topY;
      }
      case 5 -> {
        // upper left boundary line
        aX = upperLeftDiagonalX;
        aY = 0;
        bX = upperLeftDiagonalX - 1;
        bY = -1;
      }
      case 6 -> {
        // left boundary line
        aX = leftX;
        aY = 0;
        bX = leftX;
        bY = -1;
      }
      case 7 -> {
        // lower left boundary line
        aX = lowerLeftDiagonalX;
        aY = 0;
        bX = lowerLeftDiagonalX + 1;
        bY = -1;
      }
      default -> throw new IllegalArgumentException("IntOctagon.edge_line: p_no out of range");
    }
    return new Line(aX, aY, bX, bY);
  }

  @Override
  public IntOctagon translate_by(Vector p_rel_coor) {
    // This function is at the moment only implemented for Vectors
    // with integer coordinates.
    // The general implementation is still missing.

    if (p_rel_coor.equals(Vector.ZERO)) {
      return this;
    }
    IntVector relCoor = (IntVector) p_rel_coor;
    return new IntOctagon(
        leftX + relCoor.x,
        bottomY + relCoor.y,
        rightX + relCoor.x,
        topY + relCoor.y,
        upperLeftDiagonalX + relCoor.x - relCoor.y,
        lowerRightDiagonalX + relCoor.x - relCoor.y,
        lowerLeftDiagonalX + relCoor.x + relCoor.y,
        upperRightDiagonalX + relCoor.x + relCoor.y);
  }

  @Override
  public double max_width() {
    double width1 = Math.max(rightX - leftX, topY - bottomY);
    double width2 =
        Math.max(
            upperRightDiagonalX - lowerLeftDiagonalX, lowerRightDiagonalX - upperLeftDiagonalX);
    return Math.max(width1, width2 / Limits.sqrt2);
  }

  @Override
  public double min_width() {
    double width1 = Math.min(rightX - leftX, topY - bottomY);
    double width2 =
        Math.min(
            upperRightDiagonalX - lowerLeftDiagonalX, lowerRightDiagonalX - upperLeftDiagonalX);
    return Math.min(width1, width2 / Limits.sqrt2);
  }

  @Override
  public IntOctagon offset(double p_distance) {
    int width = (int) Math.round(p_distance);
    if (width == 0) {
      return this;
    }
    int diaWidth = (int) Math.round(Limits.sqrt2 * p_distance);
    IntOctagon result =
        new IntOctagon(
            leftX - width,
            bottomY - width,
            rightX + width,
            topY + width,
            upperLeftDiagonalX - diaWidth,
            lowerRightDiagonalX + diaWidth,
            lowerLeftDiagonalX - diaWidth,
            upperRightDiagonalX + diaWidth);
    return result.normalize();
  }

  @Override
  public IntOctagon enlarge(double p_offset) {
    return offset(p_offset);
  }

  @Override
  public boolean contains(RegularTileShape p_other) {
    return p_other.is_contained_in(this);
  }

  @Override
  public RegularTileShape union(RegularTileShape p_other) {
    return p_other.union(this);
  }

  @Override
  public TileShape intersection(TileShape p_other) {
    return p_other.intersection(this);
  }

  public IntOctagon normalize() {
    if (leftX > rightX
        || bottomY > topY
        || lowerLeftDiagonalX > upperRightDiagonalX
        || upperLeftDiagonalX > lowerRightDiagonalX) {
      return EMPTY;
    }
    int newLx = leftX;
    int newRx = rightX;
    int newLy = bottomY;
    int newUy = topY;
    int newLlx = lowerLeftDiagonalX;
    int newUlx = upperLeftDiagonalX;
    int newLrx = lowerRightDiagonalX;
    int newUrx = upperRightDiagonalX;

    if (newLx < newLlx - newUy)
    // the point newLx, newUy is the lower left border line of
    // this octagon
    // change newLx , that the lower left border line runs through
    // this point
    {
      newLx = newLlx - newUy;
    }

    if (newLx < newUlx + newLy)
    // the point newLx, newLy is above the upper left border line of
    // this octagon
    // change newLx , that the upper left border line runs through
    // this point
    {
      newLx = newUlx + newLy;
    }

    if (newRx > newUrx - newLy)
    // the point newRx, newLy is above the upper right border line of
    // this octagon
    // change newRx , that the upper right border line runs through
    // this point
    {
      newRx = newUrx - newLy;
    }

    if (newRx > newLrx + newUy)
    // the point newRx, newUy is below the lower right border line of
    // this octagon
    // change rx , that the lower right border line runs through
    // this point

    {
      newRx = newLrx + newUy;
    }

    if (newLy < newLx - newLrx)
    // the point lx, ly is below the lower right border line of this
    // octagon
    // change ly, so that the lower right border line runs through
    // this point
    {
      newLy = newLx - newLrx;
    }

    if (newLy < newLlx - newRx)
    // the point rx, ly is below the lower left border line of
    // this octagon.
    // change ly, so that the lower left border line runs through
    // this point
    {
      newLy = newLlx - newRx;
    }

    if (newUy > newUrx - newLx)
    // the point lx, uy is above the upper right border line of
    // this octagon.
    // Change the uy, so that the upper right border line runs through
    // this point.
    {
      newUy = newUrx - newLx;
    }

    if (newUy > newRx - newUlx)
    // the point rx, uy is above the upper left border line of
    // this octagon.
    // Change the uy, so that the upper left border line runs through
    // this point.
    {
      newUy = newRx - newUlx;
    }

    if (newLlx - newLx < newLy)
    // The point lx, ly is above the lower left border line of
    // this octagon.
    // Change the lower left line, so that it runs through this point.
    {
      newLlx = newLx + newLy;
    }

    if (newRx - newLrx < newLy)
    // the point rx, ly is above the lower right border line of
    // this octagon.
    // Change the lower right line, so that it runs through this point.
    {
      newLrx = newRx - newLy;
    }

    if (newUrx - newRx > newUy)
    // the point rx, uy is below the upper right border line of p_oct.
    // Change the upper right line, so that it runs through this point.
    {
      newUrx = newUy + newRx;
    }

    if (newLx - newUlx > newUy)
    // the point lx, uy is below the upper left border line of
    // this octagon.
    // Change the upper left line, so that it runs through this point.
    {
      newUlx = newLx - newUy;
    }

    int diagUpperY = (int) Math.ceil((newUrx - newUlx) / 2.0);

    if (newUy > diagUpperY)
    // the intersection of the upper right and the upper left border
    // line is below newUy.  Adjust newUy to diagUpperY.
    {
      newUy = diagUpperY;
    }

    int diagLowerY = (int) Math.floor((newLlx - newLrx) / 2.0);

    if (newLy < diagLowerY)
    // the intersection of the lower right and the lower left border
    // line is above newLy.  Adjust newLy to diagLowerY.
    {
      newLy = diagLowerY;
    }

    int diagRightX = (int) Math.ceil((newUrx + newLrx) / 2.0);

    if (newRx > diagRightX)
    // the intersection of the upper right and the lower right border
    // line is to the left of  right x.  Adjust newRx to diagRightX.
    {
      newRx = diagRightX;
    }

    int diagLeftX = (int) Math.floor((newLlx + newUlx) / 2.0);

    if (newLx < diagLeftX)
    // the intersection of the lower left and the upper left border
    // line is to the right of left x.  Adjust newLx to diagLeftX.
    {
      newLx = diagLeftX;
    }
    if (newLx > newRx || newLy > newUy || newLlx > newUrx || newUlx > newLrx) {
      return EMPTY;
    }
    return new IntOctagon(newLx, newLy, newRx, newUy, newUlx, newLrx, newLlx, newUrx);
  }

  /** Checks, if this IntOctagon is normalized. */
  public boolean is_normalized() {
    IntOctagon on = this.normalize();
    return leftX == on.leftX
        && bottomY == on.bottomY
        && rightX == on.rightX
        && topY == on.topY
        && lowerLeftDiagonalX == on.lowerLeftDiagonalX
        && lowerRightDiagonalX == on.lowerRightDiagonalX
        && upperLeftDiagonalX == on.upperLeftDiagonalX
        && upperRightDiagonalX == on.upperRightDiagonalX;
  }

  @Override
  public Simplex to_Simplex() {
    if (is_empty()) {
      return Simplex.EMPTY;
    }
    if (precalculatedToSimplex == null) {
      Line[] lineArr = new Line[8];
      for (int i = 0; i < 8; i++) {
        lineArr[i] = border_line(i);
      }
      Simplex currSimplex = new Simplex(lineArr);
      precalculatedToSimplex = currSimplex.remove_redundant_lines();
    }
    return precalculatedToSimplex;
  }

  @Override
  public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs) {
    return p_dirs.bounds(this);
  }

  @Override
  public boolean intersects(Shape p_other) {
    return p_other.intersects(this);
  }

  /**
   * Returns true, if p_point is contained in this octagon. Because of the parameter type
   * FloatPoint, the function may not be exact close to the border.
   */
  @Override
  public boolean contains(FloatPoint p_point) {
    if (leftX > p_point.x || bottomY > p_point.y || rightX < p_point.x || topY < p_point.y) {
      return false;
    }
    double tmp1 = p_point.x - p_point.y;
    double tmp2 = p_point.x + p_point.y;
    return upperLeftDiagonalX <= tmp1
        && lowerRightDiagonalX >= tmp1
        && lowerLeftDiagonalX <= tmp2
        && upperRightDiagonalX >= tmp2;
  }

  /**
   * Calculates the side of the point (p_x, p_y) of the border line with index p_border_line_no. The
   * border lines are located in counterclock sense around this octagon.
   */
  public Side side_of_border_line(int p_x, int p_y, int p_border_line_no) {

    int tmp =
        switch (p_border_line_no) {
          case 0 -> this.bottomY - p_y;
          case 2 -> p_x - this.rightX;
          case 4 -> p_y - this.topY;
          case 6 -> this.leftX - p_x;
          case 1 -> p_x - p_y - this.lowerRightDiagonalX;
          case 3 -> p_x + p_y - this.upperRightDiagonalX;
          case 5 -> this.upperLeftDiagonalX + p_y - p_x;
          case 7 -> this.lowerLeftDiagonalX - p_x - p_y;
          default -> {
            FRLogger.warn("IntOctagon.side_of_border_line: p_border_line_no out of range");
            yield 0;
          }
        };
    Side result;
    if (tmp < 0) {
      result = Side.ON_THE_LEFT;
    } else if (tmp > 0) {
      result = Side.ON_THE_RIGHT;
    } else {
      result = Side.COLLINEAR;
    }
    return result;
  }

  @Override
  Simplex intersection(Simplex p_other) {
    return p_other.intersection(this);
  }

  @Override
  public IntOctagon intersection(IntOctagon p_other) {
    IntOctagon result =
        new IntOctagon(
            Math.max(leftX, p_other.leftX),
            Math.max(bottomY, p_other.bottomY),
            Math.min(rightX, p_other.rightX),
            Math.min(topY, p_other.topY),
            Math.max(upperLeftDiagonalX, p_other.upperLeftDiagonalX),
            Math.min(lowerRightDiagonalX, p_other.lowerRightDiagonalX),
            Math.max(lowerLeftDiagonalX, p_other.lowerLeftDiagonalX),
            Math.min(upperRightDiagonalX, p_other.upperRightDiagonalX));
    return result.normalize();
  }

  @Override
  IntOctagon intersection(IntBox p_other) {
    return intersection(p_other.to_IntOctagon());
  }

  /** checks if this (normalized) octagon is contained in p_box */
  @Override
  public boolean is_contained_in(IntBox p_box) {
    return leftX >= p_box.ll.x
        && bottomY >= p_box.ll.y
        && rightX <= p_box.ur.x
        && topY <= p_box.ur.y;
  }

  @Override
  public boolean is_contained_in(IntOctagon p_other) {
    return leftX >= p_other.leftX
        && bottomY >= p_other.bottomY
        && rightX <= p_other.rightX
        && topY <= p_other.topY
        && lowerLeftDiagonalX >= p_other.lowerLeftDiagonalX
        && upperLeftDiagonalX >= p_other.upperLeftDiagonalX
        && lowerRightDiagonalX <= p_other.lowerRightDiagonalX
        && upperRightDiagonalX <= p_other.upperRightDiagonalX;
  }

  @Override
  public IntOctagon union(IntOctagon p_other) {
    return new IntOctagon(
        Math.min(leftX, p_other.leftX),
        Math.min(bottomY, p_other.bottomY),
        Math.max(rightX, p_other.rightX),
        Math.max(topY, p_other.topY),
        Math.min(upperLeftDiagonalX, p_other.upperLeftDiagonalX),
        Math.max(lowerRightDiagonalX, p_other.lowerRightDiagonalX),
        Math.min(lowerLeftDiagonalX, p_other.lowerLeftDiagonalX),
        Math.max(upperRightDiagonalX, p_other.upperRightDiagonalX));
  }

  @Override
  public boolean intersects(IntBox p_other) {
    return intersects(p_other.to_IntOctagon());
  }

  /** checks, if two normalized Octagons intersect. */
  @Override
  public boolean intersects(IntOctagon p_other) {
    int isLx;
    int isRx;
    isLx = Math.max(p_other.leftX, this.leftX);
    isRx = Math.min(p_other.rightX, this.rightX);
    if (isLx > isRx) {
      return false;
    }

    int isLy;
    int isUy;
    isLy = Math.max(p_other.bottomY, this.bottomY);
    isUy = Math.min(p_other.topY, this.topY);
    if (isLy > isUy) {
      return false;
    }

    int isLlx;
    int isUrx;
    isLlx = Math.max(p_other.lowerLeftDiagonalX, this.lowerLeftDiagonalX);
    isUrx = Math.min(p_other.upperRightDiagonalX, this.upperRightDiagonalX);
    if (isLlx > isUrx) {
      return false;
    }

    int isUlx;
    int isLrx;
    isUlx = Math.max(p_other.upperLeftDiagonalX, this.upperLeftDiagonalX);
    isLrx = Math.min(p_other.lowerRightDiagonalX, this.lowerRightDiagonalX);
    return isUlx <= isLrx;
  }

  /**
   * Returns true, if this octagon intersects with p_other and the intersection is 2-dimensional.
   */
  public boolean overlaps(IntOctagon p_other) {
    int isLx;
    int isRx;
    isLx = Math.max(p_other.leftX, this.leftX);
    isRx = Math.min(p_other.rightX, this.rightX);
    if (isLx >= isRx) {
      return false;
    }

    int isLy;
    int isUy;
    isLy = Math.max(p_other.bottomY, this.bottomY);
    isUy = Math.min(p_other.topY, this.topY);
    if (isLy >= isUy) {
      return false;
    }

    int isLlx;
    int isUrx;
    isLlx = Math.max(p_other.lowerLeftDiagonalX, this.lowerLeftDiagonalX);
    isUrx = Math.min(p_other.upperRightDiagonalX, this.upperRightDiagonalX);
    if (isLlx >= isUrx) {
      return false;
    }

    int isUlx;
    int isLrx;
    isUlx = Math.max(p_other.upperLeftDiagonalX, this.upperLeftDiagonalX);
    isLrx = Math.min(p_other.lowerRightDiagonalX, this.lowerRightDiagonalX);
    return isUlx < isLrx;
  }

  @Override
  public boolean intersects(Simplex p_other) {
    return p_other.intersects(this);
  }

  @Override
  public boolean intersects(Circle p_other) {
    return p_other.intersects(this);
  }

  @Override
  public IntOctagon union(IntBox p_other) {
    return union(p_other.to_IntOctagon());
  }

  /** computes the x value of the left boundary of this Octagon at p_y */
  public int left_x_value(int p_y) {
    int result = Math.max(leftX, upperLeftDiagonalX + p_y);
    return Math.max(result, lowerLeftDiagonalX - p_y);
  }

  /** computes the x value of the right boundary of this Octagon at p_y */
  public int right_x_value(int p_y) {
    int result = Math.min(rightX, upperRightDiagonalX - p_y);
    return Math.min(result, lowerRightDiagonalX + p_y);
  }

  /** computes the y value of the lower boundary of this Octagon at p_x */
  public int lower_y_value(int p_x) {
    int result = Math.max(bottomY, lowerLeftDiagonalX - p_x);
    return Math.max(result, p_x - lowerRightDiagonalX);
  }

  /** computes the y value of the upper boundary of this Octagon at p_x */
  public int upper_y_value(int p_x) {
    int result = Math.min(topY, p_x - upperLeftDiagonalX);
    return Math.min(result, upperRightDiagonalX - p_x);
  }

  @Override
  public Side compare(RegularTileShape p_other, int p_edge_no) {
    Side result = p_other.compare(this, p_edge_no);
    return result.negate();
  }

  @Override
  public Side compare(IntOctagon p_other, int p_edge_no) {
    Side result;
    switch (p_edge_no) {
      case 0 -> {
        // compare the lower edge line
        if (bottomY > p_other.bottomY) {
          result = Side.ON_THE_LEFT;
        } else if (bottomY < p_other.bottomY) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 1 -> {
        // compare the lower right edge line
        if (lowerRightDiagonalX < p_other.lowerRightDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (lowerRightDiagonalX > p_other.lowerRightDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 2 -> {
        // compare the right edge line
        if (rightX < p_other.rightX) {
          result = Side.ON_THE_LEFT;
        } else if (rightX > p_other.rightX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 3 -> {
        // compare the upper right edge line
        if (upperRightDiagonalX < p_other.upperRightDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (upperRightDiagonalX > p_other.upperRightDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 4 -> {
        // compare the upper edge line
        if (topY < p_other.topY) {
          result = Side.ON_THE_LEFT;
        } else if (topY > p_other.topY) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 5 -> {
        // compare the upper left edge line
        if (upperLeftDiagonalX > p_other.upperLeftDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (upperLeftDiagonalX < p_other.upperLeftDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 6 -> {
        // compare the left edge line
        if (leftX > p_other.leftX) {
          result = Side.ON_THE_LEFT;
        } else if (leftX < p_other.leftX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 7 -> {
        // compare the lower left edge line
        if (lowerLeftDiagonalX > p_other.lowerLeftDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (lowerLeftDiagonalX < p_other.lowerLeftDiagonalX) {
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
  public Side compare(IntBox p_other, int p_edge_no) {
    return compare(p_other.to_IntOctagon(), p_edge_no);
  }

  @Override
  public int border_line_index(Line p_line) {
    FRLogger.warn("edge_index_of_line not yet implemented for octagons");
    return -1;
  }

  /**
   * Calculates the border point of this octagon from p_point into the 45 degree direction p_dir. If
   * this border point is not an IntPoint, the nearest outside IntPoint of the octagon is returned.
   */
  public IntPoint border_point(IntPoint p_point, FortyfiveDegreeDirection p_dir) {
    int resultX;
    int resultY;
    switch (p_dir) {
      case RIGHT -> {
        resultX = Math.min(rightX, upperRightDiagonalX - p_point.y);
        resultX = Math.min(resultX, lowerRightDiagonalX + p_point.y);
        resultY = p_point.y;
      }
      case LEFT -> {
        resultX = Math.max(leftX, upperLeftDiagonalX + p_point.y);
        resultX = Math.max(resultX, lowerLeftDiagonalX - p_point.y);
        resultY = p_point.y;
      }
      case UP -> {
        resultX = p_point.x;
        resultY = Math.min(topY, p_point.x - upperLeftDiagonalX);
        resultY = Math.min(resultY, upperRightDiagonalX - p_point.x);
      }
      case DOWN -> {
        resultX = p_point.x;
        resultY = Math.max(bottomY, lowerLeftDiagonalX - p_point.x);
        resultY = Math.max(resultY, p_point.x - lowerRightDiagonalX);
      }
      case RIGHT45 -> {
        resultX = (int) (Math.ceil(0.5 * (p_point.x - p_point.y + upperRightDiagonalX)));
        resultX = Math.min(resultX, rightX);
        resultX = Math.min(resultX, p_point.x - p_point.y + topY);
        resultY = p_point.y - p_point.x + resultX;
      }
      case UP45 -> {
        resultX = (int) (Math.floor(0.5 * (p_point.x + p_point.y + upperLeftDiagonalX)));
        resultX = Math.max(resultX, leftX);
        resultX = Math.max(resultX, p_point.x + p_point.y - topY);
        resultY = p_point.y + p_point.x - resultX;
      }
      case LEFT45 -> {
        resultX = (int) (Math.floor(0.5 * (p_point.x - p_point.y + lowerLeftDiagonalX)));
        resultX = Math.max(resultX, leftX);
        resultX = Math.max(resultX, p_point.x - p_point.y + bottomY);
        resultY = p_point.y - p_point.x + resultX;
      }
      case DOWN45 -> {
        resultX = (int) (Math.ceil(0.5 * (p_point.x + p_point.y + lowerRightDiagonalX)));
        resultX = Math.min(resultX, rightX);
        resultX = Math.min(resultX, p_point.x + p_point.y - bottomY);
        resultY = p_point.y + p_point.x - resultX;
      }
      default -> {
        FRLogger.warn("IntOctagon.border_point: unexpected 45 degree direction");
        resultX = 0;
        resultY = 0;
      }
    }
    return new IntPoint(resultX, resultY);
  }

  /**
   * Calculates the sorted p_max_result_points nearest points on the border of this octagon in the
   * 45-degree directions. p_point is assumed to be located in the interior of this octagon.
   */
  public IntPoint[] nearest_border_projections(IntPoint p_point, int p_max_result_points) {
    if (!this.contains(p_point) || p_max_result_points <= 0) {
      return new IntPoint[0];
    }
    p_max_result_points = Math.min(p_max_result_points, 8);
    IntPoint[] result = new IntPoint[p_max_result_points];
    double[] minDist = new double[p_max_result_points];
    for (int i = 0; i < p_max_result_points; i++) {
      minDist[i] = Double.MAX_VALUE;
    }
    FloatPoint insidePoint = p_point.to_float();
    for (FortyfiveDegreeDirection currDir : FortyfiveDegreeDirection.values()) {
      IntPoint currBorderPoint = border_point(p_point, currDir);
      double currDist = insidePoint.distance_square(currBorderPoint.to_float());
      for (int i = 0; i < p_max_result_points; i++) {
        if (currDist < minDist[i]) {
          for (int k = p_max_result_points - 1; k > i; k--) {
            minDist[k] = minDist[k - 1];
            result[k] = result[k - 1];
          }
          minDist[i] = currDist;
          result[i] = currBorderPoint;
          break;
        }
      }
    }
    return result;
  }

  Side border_line_side_of(FloatPoint p_point, int p_line_no, double p_tolerance) {
    return switch (p_line_no) {
      case 0 -> {
        if (p_point.y > this.bottomY + p_tolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (p_point.y < this.bottomY - p_tolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 2 -> {
        if (p_point.x < this.rightX - p_tolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (p_point.x > this.rightX + p_tolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 4 -> {
        if (p_point.y < this.topY - p_tolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (p_point.y > this.topY + p_tolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 6 -> {
        if (p_point.x > this.leftX + p_tolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (p_point.x < this.leftX - p_tolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 1 -> {
        double tmp = p_point.y - p_point.x + lowerRightDiagonalX;
        if (tmp > p_tolerance)
        // the p_point is above the lower right border line of this octagon
        {
          yield Side.ON_THE_RIGHT;
        } else if (tmp < -p_tolerance)
        // the p_point is below the lower right border line of this octagon
        {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 3 -> {
        double tmp = p_point.x + p_point.y - upperRightDiagonalX;
        if (tmp < -p_tolerance) {
          // the p_point is below the upper right border line of this octagon
          yield Side.ON_THE_RIGHT;
        } else if (tmp > p_tolerance) {
          // the p_point is above the upper right border line of this octagon
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 5 -> {
        double tmp = p_point.y - p_point.x + upperLeftDiagonalX;
        if (tmp < -p_tolerance)
        // the p_point is below the upper left border line of this octagon
        {
          yield Side.ON_THE_RIGHT;
        } else if (tmp > p_tolerance)
        // the p_point is above the upper left border line of this octagon
        {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 7 -> {
        double tmp = p_point.x + p_point.y - lowerLeftDiagonalX;
        if (tmp > p_tolerance) {
          // the p_point is above the lower left border line of this octagon
          yield Side.ON_THE_RIGHT;
        } else if (tmp < -p_tolerance) {
          // the p_point is below the lower left border line of this octagon
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      default -> {
        FRLogger.warn("IntOctagon.border_line_side_of: p_line_no out of range");
        yield Side.COLLINEAR;
      }
    };
  }

  /** Checks, if this octagon can be converted to an IntBox. */
  @Override
  public boolean is_IntBox() {
    if (lowerLeftDiagonalX != leftX + bottomY) {
      return false;
    }
    if (lowerRightDiagonalX != rightX - bottomY) {
      return false;
    }
    if (upperRightDiagonalX != rightX + topY) {
      return false;
    }
    return upperLeftDiagonalX == leftX - topY;
  }

  @Override
  public TileShape simplify() {
    if (this.is_IntBox()) {
      return this.bounding_box();
    }
    return this;
  }

  @Override
  public TileShape[] cutout(TileShape p_shape) {
    return p_shape.cutout_from(this);
  }

  /** Divide p_d minus this octagon into 8 convex pieces, from which 4 have cut off a corner. */
  @Override
  IntOctagon[] cutout_from(IntBox p_d) {
    IntOctagon c = this.intersection(p_d);

    if (this.is_empty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntOctagon[] result = new IntOctagon[1];
      result[0] = p_d.to_IntOctagon();
      return result;
    }

    IntBox[] boxes = new IntBox[4];

    // construct left box

    boxes[0] =
        new IntBox(
            p_d.ll.x, c.lowerLeftDiagonalX - c.leftX, c.leftX, c.leftX - c.upperLeftDiagonalX);

    // construct right box

    boxes[1] =
        new IntBox(
            c.rightX, c.rightX - c.lowerRightDiagonalX, p_d.ur.x, c.upperRightDiagonalX - c.rightX);

    // construct lower box

    boxes[2] =
        new IntBox(
            c.lowerLeftDiagonalX - c.bottomY,
            p_d.ll.y,
            c.lowerRightDiagonalX + c.bottomY,
            c.bottomY);

    // construct upper box

    boxes[3] =
        new IntBox(c.upperLeftDiagonalX + c.topY, c.topY, c.upperRightDiagonalX - c.topY, p_d.ur.y);

    IntOctagon[] octagons = new IntOctagon[4];

    // construct upper left octagon

    IntOctagon currOct =
        new IntOctagon(
            p_d.ll.x,
            boxes[0].ur.y,
            boxes[3].ll.x,
            p_d.ur.y,
            -Limits.CRIT_INT,
            c.upperLeftDiagonalX,
            -Limits.CRIT_INT,
            Limits.CRIT_INT);
    octagons[0] = currOct.normalize();

    // construct lower left octagon

    currOct =
        new IntOctagon(
            p_d.ll.x,
            p_d.ll.y,
            boxes[2].ll.x,
            boxes[0].ll.y,
            -Limits.CRIT_INT,
            Limits.CRIT_INT,
            -Limits.CRIT_INT,
            c.lowerLeftDiagonalX);
    octagons[1] = currOct.normalize();

    // construct lower right octagon

    currOct =
        new IntOctagon(
            boxes[2].ur.x,
            p_d.ll.y,
            p_d.ur.x,
            boxes[1].ll.y,
            c.lowerRightDiagonalX,
            Limits.CRIT_INT,
            -Limits.CRIT_INT,
            Limits.CRIT_INT);
    octagons[2] = currOct.normalize();

    // construct upper right octagon

    currOct =
        new IntOctagon(
            boxes[3].ur.x,
            boxes[1].ur.y,
            p_d.ur.x,
            p_d.ur.y,
            -Limits.CRIT_INT,
            Limits.CRIT_INT,
            c.upperRightDiagonalX,
            Limits.CRIT_INT);
    octagons[3] = currOct.normalize();

    // optimise the result to minimum cumulative circumference

    IntBox b = boxes[0];
    IntOctagon o = octagons[0];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
      // switch the horizontal upper left divide line to vertical

      boxes[0] = new IntBox(b.ll.x, b.ll.y, b.ur.x, o.topY);
      currOct =
          new IntOctagon(
              b.ur.x,
              o.bottomY,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[0] = currOct.normalize();
    }

    b = boxes[3];
    o = octagons[0];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
      // switch the vertical upper left divide line to horizontal

      boxes[3] = new IntBox(o.leftX, b.ll.y, b.ur.x, b.ur.y);
      currOct =
          new IntOctagon(
              o.leftX,
              o.bottomY,
              o.rightX,
              b.ll.y,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[0] = currOct.normalize();
    }
    b = boxes[3];
    o = octagons[3];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
      // switch the vertical upper right divide line to horizontal

      boxes[3] = new IntBox(b.ll.x, b.ll.y, o.rightX, b.ur.y);
      currOct =
          new IntOctagon(
              o.leftX,
              o.bottomY,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[3] = currOct.normalize();
    }
    b = boxes[1];
    o = octagons[3];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
      // switch the horizontal upper right divide line to vertical

      boxes[1] = new IntBox(b.ll.x, b.ll.y, b.ur.x, o.topY);
      currOct =
          new IntOctagon(
              o.leftX,
              o.bottomY,
              b.ll.x,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[3] = currOct.normalize();
    }
    b = boxes[1];
    o = octagons[2];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
      // switch the horizontal lower right divide line to vertical

      boxes[1] = new IntBox(b.ll.x, o.bottomY, b.ur.x, b.ur.y);
      currOct =
          new IntOctagon(
              o.leftX,
              o.bottomY,
              b.ll.x,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[2] = currOct.normalize();
    }
    b = boxes[2];
    o = octagons[2];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
      // switch the vertical lower right divide line to horizontal

      boxes[2] = new IntBox(b.ll.x, b.ll.y, o.rightX, b.ur.y);
      currOct =
          new IntOctagon(
              o.leftX,
              b.ur.y,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[2] = currOct.normalize();
    }
    b = boxes[2];
    o = octagons[1];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
      // switch the vertical lower  left divide line to horizontal

      boxes[2] = new IntBox(o.leftX, b.ll.y, b.ur.x, b.ur.y);
      currOct =
          new IntOctagon(
              o.leftX,
              b.ur.y,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[1] = currOct.normalize();
    }
    b = boxes[0];
    o = octagons[1];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
      // switch the horizontal lower left divide line to vertical
      boxes[0] = new IntBox(b.ll.x, o.bottomY, b.ur.x, b.ur.y);
      currOct =
          new IntOctagon(
              b.ur.x,
              o.bottomY,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[1] = currOct.normalize();
    }

    IntOctagon[] result = new IntOctagon[8];

    // add the 4 boxes to the result
    for (int i = 0; i < 4; i++) {
      result[i] = boxes[i].to_IntOctagon();
    }

    // add the 4 octagons to the result
    System.arraycopy(octagons, 0, result, 4, 4);
    return result;
  }

  /** Divide p_divide_octagon minus cut_octagon into 8 convex pieces without sharp angles. */
  @Override
  IntOctagon[] cutout_from(IntOctagon p_d) {
    IntOctagon c = this.intersection(p_d);

    if (this.is_empty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntOctagon[] result = new IntOctagon[1];
      result[0] = p_d;
      return result;
    }

    IntOctagon[] result = new IntOctagon[8];

    int tmp = c.lowerLeftDiagonalX - c.leftX;

    result[0] =
        new IntOctagon(
            p_d.leftX,
            tmp,
            c.leftX,
            c.leftX - c.upperLeftDiagonalX,
            p_d.upperLeftDiagonalX,
            p_d.lowerRightDiagonalX,
            p_d.lowerLeftDiagonalX,
            p_d.upperRightDiagonalX);

    int tmp2 = c.lowerLeftDiagonalX - c.bottomY;

    result[1] =
        new IntOctagon(
            p_d.leftX,
            p_d.bottomY,
            tmp2,
            tmp,
            p_d.upperLeftDiagonalX,
            p_d.lowerRightDiagonalX,
            p_d.lowerLeftDiagonalX,
            c.lowerLeftDiagonalX);

    tmp = c.lowerRightDiagonalX + c.bottomY;

    result[2] =
        new IntOctagon(
            tmp2,
            p_d.bottomY,
            tmp,
            c.bottomY,
            p_d.upperLeftDiagonalX,
            p_d.lowerRightDiagonalX,
            p_d.lowerLeftDiagonalX,
            p_d.upperRightDiagonalX);

    tmp2 = c.rightX - c.lowerRightDiagonalX;

    result[3] =
        new IntOctagon(
            tmp,
            p_d.bottomY,
            p_d.rightX,
            tmp2,
            c.lowerRightDiagonalX,
            p_d.lowerRightDiagonalX,
            p_d.lowerLeftDiagonalX,
            p_d.upperRightDiagonalX);

    tmp = c.upperRightDiagonalX - c.rightX;

    result[4] =
        new IntOctagon(
            c.rightX,
            tmp2,
            p_d.rightX,
            tmp,
            p_d.upperLeftDiagonalX,
            p_d.lowerRightDiagonalX,
            p_d.lowerLeftDiagonalX,
            p_d.upperRightDiagonalX);

    tmp2 = c.upperRightDiagonalX - c.topY;

    result[5] =
        new IntOctagon(
            tmp2,
            tmp,
            p_d.rightX,
            p_d.topY,
            p_d.upperLeftDiagonalX,
            p_d.lowerRightDiagonalX,
            c.upperRightDiagonalX,
            p_d.upperRightDiagonalX);

    tmp = c.upperLeftDiagonalX + c.topY;

    result[6] =
        new IntOctagon(
            tmp,
            c.topY,
            tmp2,
            p_d.topY,
            p_d.upperLeftDiagonalX,
            p_d.lowerRightDiagonalX,
            p_d.lowerLeftDiagonalX,
            p_d.upperRightDiagonalX);

    tmp2 = c.leftX - c.upperLeftDiagonalX;

    result[7] =
        new IntOctagon(
            p_d.leftX,
            tmp2,
            tmp,
            p_d.topY,
            p_d.upperLeftDiagonalX,
            c.upperLeftDiagonalX,
            p_d.lowerLeftDiagonalX,
            p_d.upperRightDiagonalX);

    for (int i = 0; i < 8; i++) {
      result[i] = result[i].normalize();
    }

    IntOctagon curr1 = result[0];
    IntOctagon curr2 = result[7];

    if (!(curr1.is_empty() || curr2.is_empty())
        && curr1.rightX - curr1.left_x_value(curr1.topY)
            > curr2.upper_y_value(curr1.rightX) - curr2.bottomY) {
      // switch the horizontal upper left divide line to vertical
      curr1 =
          new IntOctagon(
              Math.min(curr1.leftX, curr2.leftX),
              curr1.bottomY,
              curr1.rightX,
              curr2.topY,
              curr2.upperLeftDiagonalX,
              curr1.lowerRightDiagonalX,
              curr1.lowerLeftDiagonalX,
              curr2.upperRightDiagonalX);

      curr2 =
          new IntOctagon(
              curr1.rightX,
              curr2.bottomY,
              curr2.rightX,
              curr2.topY,
              curr2.upperLeftDiagonalX,
              curr2.lowerRightDiagonalX,
              curr2.lowerLeftDiagonalX,
              curr2.upperRightDiagonalX);

      result[0] = curr1.normalize();
      result[7] = curr2.normalize();
    }
    curr1 = result[7];
    curr2 = result[6];
    if (!(curr1.is_empty() || curr2.is_empty())
        && curr2.upper_y_value(curr1.rightX) - curr2.bottomY
            > curr1.rightX - curr1.left_x_value(curr2.bottomY))
    // switch the vertical upper left divide line to horizontal
    {
      curr2 =
          new IntOctagon(
              curr1.leftX,
              curr2.bottomY,
              curr2.rightX,
              Math.max(curr2.topY, curr1.topY),
              curr1.upperLeftDiagonalX,
              curr2.lowerRightDiagonalX,
              curr1.lowerLeftDiagonalX,
              curr2.upperRightDiagonalX);

      curr1 =
          new IntOctagon(
              curr1.leftX,
              curr1.bottomY,
              curr1.rightX,
              curr2.bottomY,
              curr1.upperLeftDiagonalX,
              curr1.lowerRightDiagonalX,
              curr1.lowerLeftDiagonalX,
              curr1.upperRightDiagonalX);

      result[7] = curr1.normalize();
      result[6] = curr2.normalize();
    }
    curr1 = result[6];
    curr2 = result[5];
    if (!(curr1.is_empty() || curr2.is_empty())
        && curr2.upper_y_value(curr1.rightX) - curr1.bottomY
            > curr2.right_x_value(curr1.bottomY) - curr2.leftX)
    // switch the vertical upper right divide line to horizontal
    {
      curr1 =
          new IntOctagon(
              curr1.leftX,
              curr1.bottomY,
              curr2.rightX,
              Math.max(curr2.topY, curr1.topY),
              curr1.upperLeftDiagonalX,
              curr2.lowerRightDiagonalX,
              curr1.lowerLeftDiagonalX,
              curr2.upperRightDiagonalX);

      curr2 =
          new IntOctagon(
              curr2.leftX,
              curr2.bottomY,
              curr2.rightX,
              curr1.bottomY,
              curr2.upperLeftDiagonalX,
              curr2.lowerRightDiagonalX,
              curr2.lowerLeftDiagonalX,
              curr2.upperRightDiagonalX);

      result[6] = curr1.normalize();
      result[5] = curr2.normalize();
    }
    curr1 = result[5];
    curr2 = result[4];
    if (!(curr1.is_empty() || curr2.is_empty())
        && curr2.right_x_value(curr2.topY) - curr2.leftX
            > curr1.upper_y_value(curr2.leftX) - curr2.topY)
    // switch the horizontal upper right divide line to vertical
    {
      curr2 =
          new IntOctagon(
              curr2.leftX,
              curr2.bottomY,
              Math.max(curr2.rightX, curr1.rightX),
              curr1.topY,
              curr1.upperLeftDiagonalX,
              curr2.lowerRightDiagonalX,
              curr2.lowerLeftDiagonalX,
              curr1.upperRightDiagonalX);

      curr1 =
          new IntOctagon(
              curr1.leftX,
              curr1.bottomY,
              curr2.leftX,
              curr1.topY,
              curr1.upperLeftDiagonalX,
              curr1.lowerRightDiagonalX,
              curr1.lowerLeftDiagonalX,
              curr1.upperRightDiagonalX);

      result[5] = curr1.normalize();
      result[4] = curr2.normalize();
    }
    curr1 = result[4];
    curr2 = result[3];
    if (!(curr1.is_empty() || curr2.is_empty())
        && curr1.right_x_value(curr1.bottomY) - curr1.leftX
            > curr1.bottomY - curr2.lower_y_value(curr1.leftX))
    // switch the horizontal lower right divide line to vertical
    {
      curr1 =
          new IntOctagon(
              curr1.leftX,
              curr2.bottomY,
              Math.max(curr2.rightX, curr1.rightX),
              curr1.topY,
              curr1.upperLeftDiagonalX,
              curr2.lowerRightDiagonalX,
              curr2.lowerLeftDiagonalX,
              curr1.upperRightDiagonalX);

      curr2 =
          new IntOctagon(
              curr2.leftX,
              curr2.bottomY,
              curr1.leftX,
              curr2.topY,
              curr2.upperLeftDiagonalX,
              curr2.lowerRightDiagonalX,
              curr2.lowerLeftDiagonalX,
              curr2.upperRightDiagonalX);

      result[4] = curr1.normalize();
      result[3] = curr2.normalize();
    }

    curr1 = result[3];
    curr2 = result[2];

    if (!(curr1.is_empty() || curr2.is_empty())
        && curr2.topY - curr2.lower_y_value(curr2.rightX)
            > curr1.right_x_value(curr2.topY) - curr2.rightX)
    // switch the vertical lower right divide line to horizontal
    {
      curr2 =
          new IntOctagon(
              curr2.leftX,
              Math.min(curr1.bottomY, curr2.bottomY),
              curr1.rightX,
              curr2.topY,
              curr2.upperLeftDiagonalX,
              curr1.lowerRightDiagonalX,
              curr2.lowerLeftDiagonalX,
              curr1.upperRightDiagonalX);

      curr1 =
          new IntOctagon(
              curr1.leftX,
              curr2.topY,
              curr1.rightX,
              curr1.topY,
              curr1.upperLeftDiagonalX,
              curr1.lowerRightDiagonalX,
              curr1.lowerLeftDiagonalX,
              curr1.upperRightDiagonalX);

      result[3] = curr1.normalize();
      result[2] = curr2.normalize();
    }

    curr1 = result[2];
    curr2 = result[1];

    if (!(curr1.is_empty() || curr2.is_empty())
        && curr1.topY - curr1.lower_y_value(curr1.leftX)
            > curr1.leftX - curr2.left_x_value(curr1.topY))
    // switch the vertical lower left divide line to horizontal
    {
      curr1 =
          new IntOctagon(
              curr2.leftX,
              Math.min(curr1.bottomY, curr2.bottomY),
              curr1.rightX,
              curr1.topY,
              curr2.upperLeftDiagonalX,
              curr1.lowerRightDiagonalX,
              curr2.lowerLeftDiagonalX,
              curr1.upperRightDiagonalX);

      curr2 =
          new IntOctagon(
              curr2.leftX,
              curr1.topY,
              curr2.rightX,
              curr2.topY,
              curr2.upperLeftDiagonalX,
              curr2.lowerRightDiagonalX,
              curr2.lowerLeftDiagonalX,
              curr2.upperRightDiagonalX);

      result[2] = curr1.normalize();
      result[1] = curr2.normalize();
    }

    curr1 = result[1];
    curr2 = result[0];

    if (!(curr1.is_empty() || curr2.is_empty())
        && curr2.rightX - curr2.left_x_value(curr2.bottomY)
            > curr2.bottomY - curr1.lower_y_value(curr2.rightX))
    // switch the horizontal lower left divide line to vertical
    {
      curr2 =
          new IntOctagon(
              Math.min(curr2.leftX, curr1.leftX),
              curr1.bottomY,
              curr2.rightX,
              curr2.topY,
              curr2.upperLeftDiagonalX,
              curr1.lowerRightDiagonalX,
              curr1.lowerLeftDiagonalX,
              curr2.upperRightDiagonalX);

      curr1 =
          new IntOctagon(
              curr2.rightX,
              curr1.bottomY,
              curr1.rightX,
              curr1.topY,
              curr1.upperLeftDiagonalX,
              curr1.lowerRightDiagonalX,
              curr1.lowerLeftDiagonalX,
              curr1.upperRightDiagonalX);

      result[1] = curr1.normalize();
      result[0] = curr2.normalize();
    }

    return result;
  }

  @Override
  Simplex[] cutout_from(Simplex p_simplex) {
    return this.to_Simplex().cutout_from(p_simplex);
  }

  @Override
  public String toString() {
    return "IntOctagon(lx="
        + leftX
        + ", ly="
        + bottomY
        + ", rx="
        + rightX
        + ", uy="
        + topY
        + ", ulx="
        + upperLeftDiagonalX
        + ", lrx="
        + lowerRightDiagonalX
        + ", llx="
        + lowerLeftDiagonalX
        + ", urx="
        + upperRightDiagonalX
        + ")";
  }
}

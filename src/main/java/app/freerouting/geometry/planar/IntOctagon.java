package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;

import java.io.Serializable;

/**
 * IntOctagon is a specialized geometric shape implementation representing an octagon with integer coordinates and 45-degree angle constraints.
 * The class extends RegularTileShape and provides efficient representations for PCB (Printed Circuit Board) routing spaces.
 */
public class IntOctagon extends RegularTileShape implements Serializable
{
  /**
   * Reusable instance of an empty octagon.
   */
  public static final IntOctagon EMPTY = new IntOctagon(Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT, -Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT);

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

  /**
   * Result of to_simplex() memorized for performance reasons.
   */
  private Simplex precalculated_to_simplex;

  /**
   * Creates an IntOctagon from 8 integer values. p_lx is the smallest x value of the shape. p_ly is
   * the smallest y value of the shape. p_rx is the biggest x value af the shape. p_uy is the
   * biggest y value of the shape. p_ulx is the intersection of the upper left diagonal boundary
   * line with the x axis. p_lrx is the intersection of the lower right diagonal boundary line with
   * the x axis. p_llx is the intersection of the lower left diagonal boundary line with the x axis.
   * p_urx is the intersection of the upper right diagonal boundary line with the x axis.
   */
  public IntOctagon(int p_lx, int p_ly, int p_rx, int p_uy, int p_ulx, int p_lrx, int p_llx, int p_urx)
  {
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
  public boolean is_empty()
  {
    return this == EMPTY;
  }

  @Override
  public boolean is_IntOctagon()
  {
    return true;
  }

  @Override
  public boolean is_bounded()
  {
    return true;
  }

  @Override
  public boolean corner_is_bounded(int p_no)
  {
    return true;
  }

  @Override
  public IntBox bounding_box()
  {
    return new IntBox(leftX, bottomY, rightX, topY);
  }

  @Override
  public IntOctagon bounding_octagon()
  {
    return this;
  }

  @Override
  public IntOctagon bounding_tile()
  {
    return this;
  }

  @Override
  public int dimension()
  {
    if (this == EMPTY)
    {
      return -1;
    }
    int result;

    if (rightX > leftX && topY > bottomY && lowerRightDiagonalX > upperLeftDiagonalX && upperRightDiagonalX > lowerLeftDiagonalX)
    {
      result = 2;
    }
    else if (rightX == leftX && topY == bottomY)
    {
      result = 0;
    }
    else
    {
      result = 1;
    }
    return result;
  }

  @Override
  public IntPoint corner(int p_no)
  {

    int x;
    int y;
    switch (p_no)
    {
      case 0 ->
      {
        x = lowerLeftDiagonalX - bottomY;
        y = bottomY;
      }
      case 1 ->
      {
        x = lowerRightDiagonalX + bottomY;
        y = bottomY;
      }
      case 2 ->
      {
        x = rightX;
        y = rightX - lowerRightDiagonalX;
      }
      case 3 ->
      {
        x = rightX;
        y = upperRightDiagonalX - rightX;
      }
      case 4 ->
      {
        x = upperRightDiagonalX - topY;
        y = topY;
      }
      case 5 ->
      {
        x = upperLeftDiagonalX + topY;
        y = topY;
      }
      case 6 ->
      {
        x = leftX;
        y = leftX - upperLeftDiagonalX;
      }
      case 7 ->
      {
        x = leftX;
        y = lowerLeftDiagonalX - leftX;
      }
      default -> throw new IllegalArgumentException("IntOctagon.corner: p_no out of range");
    }
    return new IntPoint(x, y);
  }

  /**
   * Additional to the function corner() for performance reasons to avoid allocation of an IntPoint.
   */
  public int corner_y(int p_no)
  {
    return switch (p_no)
    {
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
  public int corner_x(int p_no)
  {
    return switch (p_no)
    {
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
  public double area()
  {

    // calculate half of the absolute value of
    // x0 (y1 - y7) + x1 (y2 - y0) + x2 (y3 - y1) + ...+ x7( y0 - y6)
    // where xi, yi are the coordinates of the i-th corner of this Octagon.

    // Overwrites the same implementation in TileShape for performance
    // reasons to avoid Point allocation.

    double result = (double) (lowerLeftDiagonalX - bottomY) * (double) (bottomY - lowerLeftDiagonalX + leftX);
    result += (double) (lowerRightDiagonalX + bottomY) * (double) (rightX - lowerRightDiagonalX - bottomY);
    result += (double) rightX * (double) (upperRightDiagonalX - 2 * rightX - bottomY + topY + lowerRightDiagonalX);
    result += (double) (upperRightDiagonalX - topY) * (double) (topY - upperRightDiagonalX + rightX);
    result += (double) (upperLeftDiagonalX + topY) * (double) (leftX - upperLeftDiagonalX - topY);
    result += (double) leftX * (double) (lowerLeftDiagonalX - 2 * leftX - topY + bottomY + upperLeftDiagonalX);

    return 0.5 * Math.abs(result);
  }

  @Override
  public int border_line_count()
  {
    return 8;
  }

  @Override
  public Line border_line(int p_no)
  {
    int a_x;
    int a_y;
    int b_x;
    int b_y;
    switch (p_no)
    {
      case 0 ->
      {
        // lower boundary line
        a_x = 0;
        a_y = bottomY;
        b_x = 1;
        b_y = bottomY;
      }
      case 1 ->
      {
        // lower right boundary line
        a_x = lowerRightDiagonalX;
        a_y = 0;
        b_x = lowerRightDiagonalX + 1;
        b_y = 1;
      }
      case 2 ->
      {
        // right boundary line
        a_x = rightX;
        a_y = 0;
        b_x = rightX;
        b_y = 1;
      }
      case 3 ->
      {
        // upper right boundary line
        a_x = upperRightDiagonalX;
        a_y = 0;
        b_x = upperRightDiagonalX - 1;
        b_y = 1;
      }
      case 4 ->
      {
        // upper boundary line
        a_x = 0;
        a_y = topY;
        b_x = -1;
        b_y = topY;
      }
      case 5 ->
      {
        // upper left boundary line
        a_x = upperLeftDiagonalX;
        a_y = 0;
        b_x = upperLeftDiagonalX - 1;
        b_y = -1;
      }
      case 6 ->
      {
        // left boundary line
        a_x = leftX;
        a_y = 0;
        b_x = leftX;
        b_y = -1;
      }
      case 7 ->
      {
        // lower left boundary line
        a_x = lowerLeftDiagonalX;
        a_y = 0;
        b_x = lowerLeftDiagonalX + 1;
        b_y = -1;
      }
      default -> throw new IllegalArgumentException("IntOctagon.edge_line: p_no out of range");
    }
    return new Line(a_x, a_y, b_x, b_y);
  }

  @Override
  public IntOctagon translate_by(Vector p_rel_coor)
  {
    // This function is at the moment only implemented for Vectors
    // with integer coordinates.
    // The general implementation is still missing.

    if (p_rel_coor.equals(Vector.ZERO))
    {
      return this;
    }
    IntVector rel_coor = (IntVector) p_rel_coor;
    return new IntOctagon(leftX + rel_coor.x, bottomY + rel_coor.y, rightX + rel_coor.x, topY + rel_coor.y, upperLeftDiagonalX + rel_coor.x - rel_coor.y, lowerRightDiagonalX + rel_coor.x - rel_coor.y, lowerLeftDiagonalX + rel_coor.x + rel_coor.y, upperRightDiagonalX + rel_coor.x + rel_coor.y);
  }

  @Override
  public double max_width()
  {
    double width_1 = Math.max(rightX - leftX, topY - bottomY);
    double width2 = Math.max(upperRightDiagonalX - lowerLeftDiagonalX, lowerRightDiagonalX - upperLeftDiagonalX);
    return Math.max(width_1, width2 / Limits.sqrt2);
  }

  @Override
  public double min_width()
  {
    double width_1 = Math.min(rightX - leftX, topY - bottomY);
    double width2 = Math.min(upperRightDiagonalX - lowerLeftDiagonalX, lowerRightDiagonalX - upperLeftDiagonalX);
    return Math.min(width_1, width2 / Limits.sqrt2);
  }

  @Override
  public IntOctagon offset(double p_distance)
  {
    int width = (int) Math.round(p_distance);
    if (width == 0)
    {
      return this;
    }
    int dia_width = (int) Math.round(Limits.sqrt2 * p_distance);
    IntOctagon result = new IntOctagon(leftX - width, bottomY - width, rightX + width, topY + width, upperLeftDiagonalX - dia_width, lowerRightDiagonalX + dia_width, lowerLeftDiagonalX - dia_width, upperRightDiagonalX + dia_width);
    return result.normalize();
  }

  @Override
  public IntOctagon enlarge(double p_offset)
  {
    return offset(p_offset);
  }

  @Override
  public boolean contains(RegularTileShape p_other)
  {
    return p_other.is_contained_in(this);
  }

  @Override
  public RegularTileShape union(RegularTileShape p_other)
  {
    return p_other.union(this);
  }

  @Override
  public TileShape intersection(TileShape p_other)
  {
    return p_other.intersection(this);
  }

  public IntOctagon normalize()
  {
    if (leftX > rightX || bottomY > topY || lowerLeftDiagonalX > upperRightDiagonalX || upperLeftDiagonalX > lowerRightDiagonalX)
    {
      return EMPTY;
    }
    int new_lx = leftX;
    int new_rx = rightX;
    int new_ly = bottomY;
    int new_uy = topY;
    int new_llx = lowerLeftDiagonalX;
    int new_ulx = upperLeftDiagonalX;
    int new_lrx = lowerRightDiagonalX;
    int new_urx = upperRightDiagonalX;

    if (new_lx < new_llx - new_uy)
    // the point new_lx, new_uy is the lower left border line of
    // this octagon
    // change new_lx , that the lower left border line runs through
    // this point
    {
      new_lx = new_llx - new_uy;
    }

    if (new_lx < new_ulx + new_ly)
    // the point new_lx, new_ly is above the upper left border line of
    // this octagon
    // change new_lx , that the upper left border line runs through
    // this point
    {
      new_lx = new_ulx + new_ly;
    }

    if (new_rx > new_urx - new_ly)
    // the point new_rx, new_ly is above the upper right border line of
    // this octagon
    // change new_rx , that the upper right border line runs through
    // this point
    {
      new_rx = new_urx - new_ly;
    }

    if (new_rx > new_lrx + new_uy)
    // the point new_rx, new_uy is below the lower right border line of
    // this octagon
    // change rx , that the lower right border line runs through
    // this point

    {
      new_rx = new_lrx + new_uy;
    }

    if (new_ly < new_lx - new_lrx)
    // the point lx, ly is below the lower right border line of this
    // octagon
    // change ly, so that the lower right border line runs through
    // this point
    {
      new_ly = new_lx - new_lrx;
    }

    if (new_ly < new_llx - new_rx)
    // the point rx, ly is below the lower left border line of
    // this octagon.
    // change ly, so that the lower left border line runs through
    // this point
    {
      new_ly = new_llx - new_rx;
    }

    if (new_uy > new_urx - new_lx)
    // the point lx, uy is above the upper right border line of
    // this octagon.
    // Change the uy, so that the upper right border line runs through
    // this point.
    {
      new_uy = new_urx - new_lx;
    }

    if (new_uy > new_rx - new_ulx)
    // the point rx, uy is above the upper left border line of
    // this octagon.
    // Change the uy, so that the upper left border line runs through
    // this point.
    {
      new_uy = new_rx - new_ulx;
    }

    if (new_llx - new_lx < new_ly)
    // The point lx, ly is above the lower left border line of
    // this octagon.
    // Change the lower left line, so that it runs through this point.
    {
      new_llx = new_lx + new_ly;
    }

    if (new_rx - new_lrx < new_ly)
    // the point rx, ly is above the lower right border line of
    // this octagon.
    // Change the lower right line, so that it runs through this point.
    {
      new_lrx = new_rx - new_ly;
    }

    if (new_urx - new_rx > new_uy)
    // the point rx, uy is below the upper right border line of p_oct.
    // Change the upper right line, so that it runs through this point.
    {
      new_urx = new_uy + new_rx;
    }

    if (new_lx - new_ulx > new_uy)
    // the point lx, uy is below the upper left border line of
    // this octagon.
    // Change the upper left line, so that it runs through this point.
    {
      new_ulx = new_lx - new_uy;
    }

    int diag_upper_y = (int) Math.ceil((new_urx - new_ulx) / 2.0);

    if (new_uy > diag_upper_y)
    // the intersection of the upper right and the upper left border
    // line is below new_uy.  Adjust new_uy to diag_upper_y.
    {
      new_uy = diag_upper_y;
    }

    int diag_lower_y = (int) Math.floor((new_llx - new_lrx) / 2.0);

    if (new_ly < diag_lower_y)
    // the intersection of the lower right and the lower left border
    // line is above new_ly.  Adjust new_ly to diag_lower_y.
    {
      new_ly = diag_lower_y;
    }

    int diag_right_x = (int) Math.ceil((new_urx + new_lrx) / 2.0);

    if (new_rx > diag_right_x)
    // the intersection of the upper right and the lower right border
    // line is to the left of  right x.  Adjust new_rx to diag_right_x.
    {
      new_rx = diag_right_x;
    }

    int diag_left_x = (int) Math.floor((new_llx + new_ulx) / 2.0);

    if (new_lx < diag_left_x)
    // the intersection of the lower left and the upper left border
    // line is to the right of left x.  Ajust new_lx to diag_left_x.
    {
      new_lx = diag_left_x;
    }
    if (new_lx > new_rx || new_ly > new_uy || new_llx > new_urx || new_ulx > new_lrx)
    {
      return EMPTY;
    }
    return new IntOctagon(new_lx, new_ly, new_rx, new_uy, new_ulx, new_lrx, new_llx, new_urx);
  }

  /**
   * Checks, if this IntOctagon is normalized.
   */
  public boolean is_normalized()
  {
    IntOctagon on = this.normalize();
    return leftX == on.leftX && bottomY == on.bottomY && rightX == on.rightX && topY == on.topY && lowerLeftDiagonalX == on.lowerLeftDiagonalX && lowerRightDiagonalX == on.lowerRightDiagonalX && upperLeftDiagonalX == on.upperLeftDiagonalX && upperRightDiagonalX == on.upperRightDiagonalX;
  }

  @Override
  public Simplex to_Simplex()
  {
    if (is_empty())
    {
      return Simplex.EMPTY;
    }
    if (precalculated_to_simplex == null)
    {
      Line[] line_arr = new Line[8];
      for (int i = 0; i < 8; i++)
      {
        line_arr[i] = border_line(i);
      }
      Simplex curr_simplex = new Simplex(line_arr);
      precalculated_to_simplex = curr_simplex.remove_redundant_lines();
    }
    return precalculated_to_simplex;
  }

  @Override
  public RegularTileShape bounding_shape(ShapeBoundingDirections p_dirs)
  {
    return p_dirs.bounds(this);
  }

  @Override
  public boolean intersects(Shape p_other)
  {
    return p_other.intersects(this);
  }

  /**
   * Returns true, if p_point is contained in this octagon. Because of the parameter type
   * FloatPoint, the function may not be exact close to the border.
   */
  @Override
  public boolean contains(FloatPoint p_point)
  {
    if (leftX > p_point.x || bottomY > p_point.y || rightX < p_point.x || topY < p_point.y)
    {
      return false;
    }
    double tmp_1 = p_point.x - p_point.y;
    double tmp_2 = p_point.x + p_point.y;
    return !(upperLeftDiagonalX > tmp_1) && !(lowerRightDiagonalX < tmp_1) && !(lowerLeftDiagonalX > tmp_2) && !(upperRightDiagonalX < tmp_2);
  }

  /**
   * Calculates the side of the point (p_x, p_y) of the border line with index p_border_line_no. The
   * border lines are located in counterclock sense around this octagon.
   */
  public Side side_of_border_line(int p_x, int p_y, int p_border_line_no)
  {

    int tmp = switch (p_border_line_no)
    {
      case 0 -> this.bottomY - p_y;
      case 2 -> p_x - this.rightX;
      case 4 -> p_y - this.topY;
      case 6 -> this.leftX - p_x;
      case 1 -> p_x - p_y - this.lowerRightDiagonalX;
      case 3 -> p_x + p_y - this.upperRightDiagonalX;
      case 5 -> this.upperLeftDiagonalX + p_y - p_x;
      case 7 -> this.lowerLeftDiagonalX - p_x - p_y;
      default ->
      {
        FRLogger.warn("IntOctagon.side_of_border_line: p_border_line_no out of range");
        yield 0;
      }
    };
    Side result;
    if (tmp < 0)
    {
      result = Side.ON_THE_LEFT;
    }
    else if (tmp > 0)
    {
      result = Side.ON_THE_RIGHT;
    }
    else
    {
      result = Side.COLLINEAR;
    }
    return result;
  }

  @Override
  Simplex intersection(Simplex p_other)
  {
    return p_other.intersection(this);
  }

  @Override
  public IntOctagon intersection(IntOctagon p_other)
  {
    IntOctagon result = new IntOctagon(Math.max(leftX, p_other.leftX), Math.max(bottomY, p_other.bottomY), Math.min(rightX, p_other.rightX), Math.min(topY, p_other.topY), Math.max(upperLeftDiagonalX, p_other.upperLeftDiagonalX), Math.min(lowerRightDiagonalX, p_other.lowerRightDiagonalX), Math.max(lowerLeftDiagonalX, p_other.lowerLeftDiagonalX), Math.min(upperRightDiagonalX, p_other.upperRightDiagonalX));
    return result.normalize();
  }

  @Override
  IntOctagon intersection(IntBox p_other)
  {
    return intersection(p_other.to_IntOctagon());
  }

  /**
   * checks if this (normalized) octagon is contained in p_box
   */
  @Override
  public boolean is_contained_in(IntBox p_box)
  {
    return leftX >= p_box.ll.x && bottomY >= p_box.ll.y && rightX <= p_box.ur.x && topY <= p_box.ur.y;
  }

  @Override
  public boolean is_contained_in(IntOctagon p_other)
  {
    return leftX >= p_other.leftX && bottomY >= p_other.bottomY && rightX <= p_other.rightX && topY <= p_other.topY && lowerLeftDiagonalX >= p_other.lowerLeftDiagonalX && upperLeftDiagonalX >= p_other.upperLeftDiagonalX && lowerRightDiagonalX <= p_other.lowerRightDiagonalX && upperRightDiagonalX <= p_other.upperRightDiagonalX;
  }

  @Override
  public IntOctagon union(IntOctagon p_other)
  {
    return new IntOctagon(Math.min(leftX, p_other.leftX), Math.min(bottomY, p_other.bottomY), Math.max(rightX, p_other.rightX), Math.max(topY, p_other.topY), Math.min(upperLeftDiagonalX, p_other.upperLeftDiagonalX), Math.max(lowerRightDiagonalX, p_other.lowerRightDiagonalX), Math.min(lowerLeftDiagonalX, p_other.lowerLeftDiagonalX), Math.max(upperRightDiagonalX, p_other.upperRightDiagonalX));
  }

  @Override
  public boolean intersects(IntBox p_other)
  {
    return intersects(p_other.to_IntOctagon());
  }

  /**
   * checks, if two normalized Octagons intersect.
   */
  @Override
  public boolean intersects(IntOctagon p_other)
  {
    int is_lx;
    int is_rx;
    is_lx = Math.max(p_other.leftX, this.leftX);
    is_rx = Math.min(p_other.rightX, this.rightX);
    if (is_lx > is_rx)
    {
      return false;
    }

    int is_ly;
    int is_uy;
    is_ly = Math.max(p_other.bottomY, this.bottomY);
    is_uy = Math.min(p_other.topY, this.topY);
    if (is_ly > is_uy)
    {
      return false;
    }

    int is_llx;
    int is_urx;
    is_llx = Math.max(p_other.lowerLeftDiagonalX, this.lowerLeftDiagonalX);
    is_urx = Math.min(p_other.upperRightDiagonalX, this.upperRightDiagonalX);
    if (is_llx > is_urx)
    {
      return false;
    }

    int is_ulx;
    int is_lrx;
    is_ulx = Math.max(p_other.upperLeftDiagonalX, this.upperLeftDiagonalX);
    is_lrx = Math.min(p_other.lowerRightDiagonalX, this.lowerRightDiagonalX);
    return is_ulx <= is_lrx;
  }

  /**
   * Returns true, if this octagon intersects with p_other and the intersection is 2-dimensional.
   */
  public boolean overlaps(IntOctagon p_other)
  {
    int is_lx;
    int is_rx;
    is_lx = Math.max(p_other.leftX, this.leftX);
    is_rx = Math.min(p_other.rightX, this.rightX);
    if (is_lx >= is_rx)
    {
      return false;
    }

    int is_ly;
    int is_uy;
    is_ly = Math.max(p_other.bottomY, this.bottomY);
    is_uy = Math.min(p_other.topY, this.topY);
    if (is_ly >= is_uy)
    {
      return false;
    }

    int is_llx;
    int is_urx;
    is_llx = Math.max(p_other.lowerLeftDiagonalX, this.lowerLeftDiagonalX);
    is_urx = Math.min(p_other.upperRightDiagonalX, this.upperRightDiagonalX);
    if (is_llx >= is_urx)
    {
      return false;
    }

    int is_ulx;
    int is_lrx;
    is_ulx = Math.max(p_other.upperLeftDiagonalX, this.upperLeftDiagonalX);
    is_lrx = Math.min(p_other.lowerRightDiagonalX, this.lowerRightDiagonalX);
    return is_ulx < is_lrx;
  }

  @Override
  public boolean intersects(Simplex p_other)
  {
    return p_other.intersects(this);
  }

  @Override
  public boolean intersects(Circle p_other)
  {
    return p_other.intersects(this);
  }

  @Override
  public IntOctagon union(IntBox p_other)
  {
    return union(p_other.to_IntOctagon());
  }

  /**
   * computes the x value of the left boundary of this Octagon at p_y
   */
  public int left_x_value(int p_y)
  {
    int result = Math.max(leftX, upperLeftDiagonalX + p_y);
    return Math.max(result, lowerLeftDiagonalX - p_y);
  }

  /**
   * computes the x value of the right boundary of this Octagon at p_y
   */
  public int right_x_value(int p_y)
  {
    int result = Math.min(rightX, upperRightDiagonalX - p_y);
    return Math.min(result, lowerRightDiagonalX + p_y);
  }

  /**
   * computes the y value of the lower boundary of this Octagon at p_x
   */
  public int lower_y_value(int p_x)
  {
    int result = Math.max(bottomY, lowerLeftDiagonalX - p_x);
    return Math.max(result, p_x - lowerRightDiagonalX);
  }

  /**
   * computes the y value of the upper boundary of this Octagon at p_x
   */
  public int upper_y_value(int p_x)
  {
    int result = Math.min(topY, p_x - upperLeftDiagonalX);
    return Math.min(result, upperRightDiagonalX - p_x);
  }

  @Override
  public Side compare(RegularTileShape p_other, int p_edge_no)
  {
    Side result = p_other.compare(this, p_edge_no);
    return result.negate();
  }

  @Override
  public Side compare(IntOctagon p_other, int p_edge_no)
  {
    Side result;
    switch (p_edge_no)
    {
      case 0 ->
      {
        // compare the lower edge line
        if (bottomY > p_other.bottomY)
        {
          result = Side.ON_THE_LEFT;
        }
        else if (bottomY < p_other.bottomY)
        {
          result = Side.ON_THE_RIGHT;
        }
        else
        {
          result = Side.COLLINEAR;
        }
      }
      case 1 ->
      {
        // compare the lower right edge line
        if (lowerRightDiagonalX < p_other.lowerRightDiagonalX)
        {
          result = Side.ON_THE_LEFT;
        }
        else if (lowerRightDiagonalX > p_other.lowerRightDiagonalX)
        {
          result = Side.ON_THE_RIGHT;
        }
        else
        {
          result = Side.COLLINEAR;
        }
      }
      case 2 ->
      {
        // compare the right edge line
        if (rightX < p_other.rightX)
        {
          result = Side.ON_THE_LEFT;
        }
        else if (rightX > p_other.rightX)
        {
          result = Side.ON_THE_RIGHT;
        }
        else
        {
          result = Side.COLLINEAR;
        }
      }
      case 3 ->
      {
        // compare the upper right edge line
        if (upperRightDiagonalX < p_other.upperRightDiagonalX)
        {
          result = Side.ON_THE_LEFT;
        }
        else if (upperRightDiagonalX > p_other.upperRightDiagonalX)
        {
          result = Side.ON_THE_RIGHT;
        }
        else
        {
          result = Side.COLLINEAR;
        }
      }
      case 4 ->
      {
        // compare the upper edge line
        if (topY < p_other.topY)
        {
          result = Side.ON_THE_LEFT;
        }
        else if (topY > p_other.topY)
        {
          result = Side.ON_THE_RIGHT;
        }
        else
        {
          result = Side.COLLINEAR;
        }
      }
      case 5 ->
      {
        // compare the upper left edge line
        if (upperLeftDiagonalX > p_other.upperLeftDiagonalX)
        {
          result = Side.ON_THE_LEFT;
        }
        else if (upperLeftDiagonalX < p_other.upperLeftDiagonalX)
        {
          result = Side.ON_THE_RIGHT;
        }
        else
        {
          result = Side.COLLINEAR;
        }
      }
      case 6 ->
      {
        // compare the left edge line
        if (leftX > p_other.leftX)
        {
          result = Side.ON_THE_LEFT;
        }
        else if (leftX < p_other.leftX)
        {
          result = Side.ON_THE_RIGHT;
        }
        else
        {
          result = Side.COLLINEAR;
        }
      }
      case 7 ->
      {
        // compare the lower left edge line
        if (lowerLeftDiagonalX > p_other.lowerLeftDiagonalX)
        {
          result = Side.ON_THE_LEFT;
        }
        else if (lowerLeftDiagonalX < p_other.lowerLeftDiagonalX)
        {
          result = Side.ON_THE_RIGHT;
        }
        else
        {
          result = Side.COLLINEAR;
        }
      }
      default -> throw new IllegalArgumentException("IntBox.compare: p_edge_no out of range");
    }
    return result;
  }

  @Override
  public Side compare(IntBox p_other, int p_edge_no)
  {
    return compare(p_other.to_IntOctagon(), p_edge_no);
  }

  @Override
  public int border_line_index(Line p_line)
  {
    FRLogger.warn("edge_index_of_line not yet implemented for octagons");
    return -1;
  }

  /**
   * Calculates the border point of this octagon from p_point into the 45 degree direction p_dir. If
   * this border point is not an IntPoint, the nearest outside IntPoint of the octagon is returned.
   */
  public IntPoint border_point(IntPoint p_point, FortyfiveDegreeDirection p_dir)
  {
    int result_x;
    int result_y;
    switch (p_dir)
    {
      case RIGHT ->
      {
        result_x = Math.min(rightX, upperRightDiagonalX - p_point.y);
        result_x = Math.min(result_x, lowerRightDiagonalX + p_point.y);
        result_y = p_point.y;
      }
      case LEFT ->
      {
        result_x = Math.max(leftX, upperLeftDiagonalX + p_point.y);
        result_x = Math.max(result_x, lowerLeftDiagonalX - p_point.y);
        result_y = p_point.y;
      }
      case UP ->
      {
        result_x = p_point.x;
        result_y = Math.min(topY, p_point.x - upperLeftDiagonalX);
        result_y = Math.min(result_y, upperRightDiagonalX - p_point.x);
      }
      case DOWN ->
      {
        result_x = p_point.x;
        result_y = Math.max(bottomY, lowerLeftDiagonalX - p_point.x);
        result_y = Math.max(result_y, p_point.x - lowerRightDiagonalX);
      }
      case RIGHT45 ->
      {
        result_x = (int) (Math.ceil(0.5 * (p_point.x - p_point.y + upperRightDiagonalX)));
        result_x = Math.min(result_x, rightX);
        result_x = Math.min(result_x, p_point.x - p_point.y + topY);
        result_y = p_point.y - p_point.x + result_x;
      }
      case UP45 ->
      {
        result_x = (int) (Math.floor(0.5 * (p_point.x + p_point.y + upperLeftDiagonalX)));
        result_x = Math.max(result_x, leftX);
        result_x = Math.max(result_x, p_point.x + p_point.y - topY);
        result_y = p_point.y + p_point.x - result_x;
      }
      case LEFT45 ->
      {
        result_x = (int) (Math.floor(0.5 * (p_point.x - p_point.y + lowerLeftDiagonalX)));
        result_x = Math.max(result_x, leftX);
        result_x = Math.max(result_x, p_point.x - p_point.y + bottomY);
        result_y = p_point.y - p_point.x + result_x;
      }
      case DOWN45 ->
      {
        result_x = (int) (Math.ceil(0.5 * (p_point.x + p_point.y + lowerRightDiagonalX)));
        result_x = Math.min(result_x, rightX);
        result_x = Math.min(result_x, p_point.x + p_point.y - bottomY);
        result_y = p_point.y + p_point.x - result_x;
      }
      default ->
      {
        FRLogger.warn("IntOctagon.border_point: unexpected 45 degree direction");
        result_x = 0;
        result_y = 0;
      }
    }
    return new IntPoint(result_x, result_y);
  }

  /**
   * Calculates the sorted p_max_result_points nearest points on the border of this octagon in the
   * 45-degree directions. p_point is assumed to be located in the interior of this octagon.
   */
  public IntPoint[] nearest_border_projections(IntPoint p_point, int p_max_result_points)
  {
    if (!this.contains(p_point) || p_max_result_points <= 0)
    {
      return new IntPoint[0];
    }
    p_max_result_points = Math.min(p_max_result_points, 8);
    IntPoint[] result = new IntPoint[p_max_result_points];
    double[] min_dist = new double[p_max_result_points];
    for (int i = 0; i < p_max_result_points; i++)
    {
      min_dist[i] = Double.MAX_VALUE;
    }
    FloatPoint inside_point = p_point.to_float();
    for (FortyfiveDegreeDirection curr_dir : FortyfiveDegreeDirection.values())
    {
      IntPoint curr_border_point = border_point(p_point, curr_dir);
      double curr_dist = inside_point.distance_square(curr_border_point.to_float());
      for (int i = 0; i < p_max_result_points; i++)
      {
        if (curr_dist < min_dist[i])
        {
          for (int k = p_max_result_points - 1; k > i; k--)
          {
            min_dist[k] = min_dist[k - 1];
            result[k] = result[k - 1];
          }
          min_dist[i] = curr_dist;
          result[i] = curr_border_point;
          break;
        }
      }
    }
    return result;
  }

  Side border_line_side_of(FloatPoint p_point, int p_line_no, double p_tolerance)
  {
    return switch (p_line_no)
    {
      case 0 ->
      {
        if (p_point.y > this.bottomY + p_tolerance)
        {
          yield Side.ON_THE_RIGHT;
        }
        else if (p_point.y < this.bottomY - p_tolerance)
        {
          yield Side.ON_THE_LEFT;
        }
        else
        {
          yield Side.COLLINEAR;
        }
      }
      case 2 ->
      {
        if (p_point.x < this.rightX - p_tolerance)
        {
          yield Side.ON_THE_RIGHT;
        }
        else if (p_point.x > this.rightX + p_tolerance)
        {
          yield Side.ON_THE_LEFT;
        }
        else
        {
          yield Side.COLLINEAR;
        }
      }
      case 4 ->
      {
        if (p_point.y < this.topY - p_tolerance)
        {
          yield Side.ON_THE_RIGHT;
        }
        else if (p_point.y > this.topY + p_tolerance)
        {
          yield Side.ON_THE_LEFT;
        }
        else
        {
          yield Side.COLLINEAR;
        }
      }
      case 6 ->
      {
        if (p_point.x > this.leftX + p_tolerance)
        {
          yield Side.ON_THE_RIGHT;
        }
        else if (p_point.x < this.leftX - p_tolerance)
        {
          yield Side.ON_THE_LEFT;
        }
        else
        {
          yield Side.COLLINEAR;
        }
      }
      case 1 ->
      {
        double tmp = p_point.y - p_point.x + lowerRightDiagonalX;
        if (tmp > p_tolerance)
        // the p_point is above the lower right border line of this octagon
        {
          yield Side.ON_THE_RIGHT;
        }
        else if (tmp < -p_tolerance)
        // the p_point is below the lower right border line of this octagon
        {
          yield Side.ON_THE_LEFT;
        }
        else
        {
          yield Side.COLLINEAR;
        }
      }
      case 3 ->
      {
        double tmp = p_point.x + p_point.y - upperRightDiagonalX;
        if (tmp < -p_tolerance)
        {
          // the p_point is below the upper right border line of this octagon
          yield Side.ON_THE_RIGHT;
        }
        else if (tmp > p_tolerance)
        {
          // the p_point is above the upper right border line of this octagon
          yield Side.ON_THE_LEFT;
        }
        else
        {
          yield Side.COLLINEAR;
        }
      }
      case 5 ->
      {
        double tmp = p_point.y - p_point.x + upperLeftDiagonalX;
        if (tmp < -p_tolerance)
        // the p_point is below the upper left border line of this octagon
        {
          yield Side.ON_THE_RIGHT;
        }
        else if (tmp > p_tolerance)
        // the p_point is above the upper left border line of this octagon
        {
          yield Side.ON_THE_LEFT;
        }
        else
        {
          yield Side.COLLINEAR;
        }
      }
      case 7 ->
      {
        double tmp = p_point.x + p_point.y - lowerLeftDiagonalX;
        if (tmp > p_tolerance)
        {
          // the p_point is above the lower left border line of this octagon
          yield Side.ON_THE_RIGHT;
        }
        else if (tmp < -p_tolerance)
        {
          // the p_point is below the lower left border line of this octagon
          yield Side.ON_THE_LEFT;
        }
        else
        {
          yield Side.COLLINEAR;
        }
      }
      default ->
      {
        FRLogger.warn("IntOctagon.border_line_side_of: p_line_no out of range");
        yield Side.COLLINEAR;
      }
    };
  }

  /**
   * Checks, if this octagon can be converted to an IntBox.
   */
  @Override
  public boolean is_IntBox()
  {
    if (lowerLeftDiagonalX != leftX + bottomY)
    {
      return false;
    }
    if (lowerRightDiagonalX != rightX - bottomY)
    {
      return false;
    }
    if (upperRightDiagonalX != rightX + topY)
    {
      return false;
    }
    return upperLeftDiagonalX == leftX - topY;
  }

  @Override
  public TileShape simplify()
  {
    if (this.is_IntBox())
    {
      return this.bounding_box();
    }
    return this;
  }

  @Override
  public TileShape[] cutout(TileShape p_shape)
  {
    return p_shape.cutout_from(this);
  }

  /**
   * Divide p_d minus this octagon into 8 convex pieces, from which 4 have cut off a corner.
   */
  @Override
  IntOctagon[] cutout_from(IntBox p_d)
  {
    IntOctagon c = this.intersection(p_d);

    if (this.is_empty() || c.dimension() < this.dimension())
    {
      // there is only an overlap at the border
      IntOctagon[] result = new IntOctagon[1];
      result[0] = p_d.to_IntOctagon();
      return result;
    }

    IntBox[] boxes = new IntBox[4];

    // construct left box

    boxes[0] = new IntBox(p_d.ll.x, c.lowerLeftDiagonalX - c.leftX, c.leftX, c.leftX - c.upperLeftDiagonalX);

    // construct right box

    boxes[1] = new IntBox(c.rightX, c.rightX - c.lowerRightDiagonalX, p_d.ur.x, c.upperRightDiagonalX - c.rightX);

    // construct lower box

    boxes[2] = new IntBox(c.lowerLeftDiagonalX - c.bottomY, p_d.ll.y, c.lowerRightDiagonalX + c.bottomY, c.bottomY);

    // construct upper box

    boxes[3] = new IntBox(c.upperLeftDiagonalX + c.topY, c.topY, c.upperRightDiagonalX - c.topY, p_d.ur.y);

    IntOctagon[] octagons = new IntOctagon[4];

    // construct upper left octagon

    IntOctagon curr_oct = new IntOctagon(p_d.ll.x, boxes[0].ur.y, boxes[3].ll.x, p_d.ur.y, -Limits.CRIT_INT, c.upperLeftDiagonalX, -Limits.CRIT_INT, Limits.CRIT_INT);
    octagons[0] = curr_oct.normalize();

    // construct lower left octagon

    curr_oct = new IntOctagon(p_d.ll.x, p_d.ll.y, boxes[2].ll.x, boxes[0].ll.y, -Limits.CRIT_INT, Limits.CRIT_INT, -Limits.CRIT_INT, c.lowerLeftDiagonalX);
    octagons[1] = curr_oct.normalize();

    // construct lower right octagon

    curr_oct = new IntOctagon(boxes[2].ur.x, p_d.ll.y, p_d.ur.x, boxes[1].ll.y, c.lowerRightDiagonalX, Limits.CRIT_INT, -Limits.CRIT_INT, Limits.CRIT_INT);
    octagons[2] = curr_oct.normalize();

    // construct upper right octagon

    curr_oct = new IntOctagon(boxes[3].ur.x, boxes[1].ur.y, p_d.ur.x, p_d.ur.y, -Limits.CRIT_INT, Limits.CRIT_INT, c.upperRightDiagonalX, Limits.CRIT_INT);
    octagons[3] = curr_oct.normalize();

    // optimise the result to minimum cumulative circumference

    IntBox b = boxes[0];
    IntOctagon o = octagons[0];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY)
    {
      // switch the horizontal upper left divide line to vertical

      boxes[0] = new IntBox(b.ll.x, b.ll.y, b.ur.x, o.topY);
      curr_oct = new IntOctagon(b.ur.x, o.bottomY, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX);
      octagons[0] = curr_oct.normalize();
    }

    b = boxes[3];
    o = octagons[0];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX)
    {
      // switch the vertical upper left divide line to horizontal

      boxes[3] = new IntBox(o.leftX, b.ll.y, b.ur.x, b.ur.y);
      curr_oct = new IntOctagon(o.leftX, o.bottomY, o.rightX, b.ll.y, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX);
      octagons[0] = curr_oct.normalize();
    }
    b = boxes[3];
    o = octagons[3];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX)
    {
      // switch the vertical upper right divide line to horizontal

      boxes[3] = new IntBox(b.ll.x, b.ll.y, o.rightX, b.ur.y);
      curr_oct = new IntOctagon(o.leftX, o.bottomY, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX);
      octagons[3] = curr_oct.normalize();
    }
    b = boxes[1];
    o = octagons[3];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY)
    {
      // switch the horizontal upper right divide line to vertical

      boxes[1] = new IntBox(b.ll.x, b.ll.y, b.ur.x, o.topY);
      curr_oct = new IntOctagon(o.leftX, o.bottomY, b.ll.x, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX);
      octagons[3] = curr_oct.normalize();
    }
    b = boxes[1];
    o = octagons[2];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY)
    {
      // switch the horizontal lower right divide line to vertical

      boxes[1] = new IntBox(b.ll.x, o.bottomY, b.ur.x, b.ur.y);
      curr_oct = new IntOctagon(o.leftX, o.bottomY, b.ll.x, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX);
      octagons[2] = curr_oct.normalize();
    }
    b = boxes[2];
    o = octagons[2];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX)
    {
      // switch the vertical lower right divide line to horizontal

      boxes[2] = new IntBox(b.ll.x, b.ll.y, o.rightX, b.ur.y);
      curr_oct = new IntOctagon(o.leftX, b.ur.y, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX);
      octagons[2] = curr_oct.normalize();
    }
    b = boxes[2];
    o = octagons[1];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX)
    {
      // switch the vertical lower  left divide line to horizontal

      boxes[2] = new IntBox(o.leftX, b.ll.y, b.ur.x, b.ur.y);
      curr_oct = new IntOctagon(o.leftX, b.ur.y, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX);
      octagons[1] = curr_oct.normalize();
    }
    b = boxes[0];
    o = octagons[1];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY)
    {
      // switch the horizontal lower left divide line to vertical
      boxes[0] = new IntBox(b.ll.x, o.bottomY, b.ur.x, b.ur.y);
      curr_oct = new IntOctagon(b.ur.x, o.bottomY, o.rightX, o.topY, o.upperLeftDiagonalX, o.lowerRightDiagonalX, o.lowerLeftDiagonalX, o.upperRightDiagonalX);
      octagons[1] = curr_oct.normalize();
    }

    IntOctagon[] result = new IntOctagon[8];

    // add the 4 boxes to the result
    for (int i = 0; i < 4; i++)
    {
      result[i] = boxes[i].to_IntOctagon();
    }

    // add the 4 octagons to the result
    System.arraycopy(octagons, 0, result, 4, 4);
    return result;
  }

  /**
   * Divide p_divide_octagon minus cut_octagon into 8 convex pieces without sharp angles.
   */
  @Override
  IntOctagon[] cutout_from(IntOctagon p_d)
  {
    IntOctagon c = this.intersection(p_d);

    if (this.is_empty() || c.dimension() < this.dimension())
    {
      // there is only an overlap at the border
      IntOctagon[] result = new IntOctagon[1];
      result[0] = p_d;
      return result;
    }

    IntOctagon[] result = new IntOctagon[8];

    int tmp = c.lowerLeftDiagonalX - c.leftX;

    result[0] = new IntOctagon(p_d.leftX, tmp, c.leftX, c.leftX - c.upperLeftDiagonalX, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX);

    int tmp2 = c.lowerLeftDiagonalX - c.bottomY;

    result[1] = new IntOctagon(p_d.leftX, p_d.bottomY, tmp2, tmp, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, c.lowerLeftDiagonalX);

    tmp = c.lowerRightDiagonalX + c.bottomY;

    result[2] = new IntOctagon(tmp2, p_d.bottomY, tmp, c.bottomY, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX);

    tmp2 = c.rightX - c.lowerRightDiagonalX;

    result[3] = new IntOctagon(tmp, p_d.bottomY, p_d.rightX, tmp2, c.lowerRightDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX);

    tmp = c.upperRightDiagonalX - c.rightX;

    result[4] = new IntOctagon(c.rightX, tmp2, p_d.rightX, tmp, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX);

    tmp2 = c.upperRightDiagonalX - c.topY;

    result[5] = new IntOctagon(tmp2, tmp, p_d.rightX, p_d.topY, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, c.upperRightDiagonalX, p_d.upperRightDiagonalX);

    tmp = c.upperLeftDiagonalX + c.topY;

    result[6] = new IntOctagon(tmp, c.topY, tmp2, p_d.topY, p_d.upperLeftDiagonalX, p_d.lowerRightDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX);

    tmp2 = c.leftX - c.upperLeftDiagonalX;

    result[7] = new IntOctagon(p_d.leftX, tmp2, tmp, p_d.topY, p_d.upperLeftDiagonalX, c.upperLeftDiagonalX, p_d.lowerLeftDiagonalX, p_d.upperRightDiagonalX);

    for (int i = 0; i < 8; i++)
    {
      result[i] = result[i].normalize();
    }

    IntOctagon curr_1 = result[0];
    IntOctagon curr_2 = result[7];

    if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_1.rightX - curr_1.left_x_value(curr_1.topY) > curr_2.upper_y_value(curr_1.rightX) - curr_2.bottomY)
    {
      // switch the horizontal upper left divide line to vertical
      curr_1 = new IntOctagon(Math.min(curr_1.leftX, curr_2.leftX), curr_1.bottomY, curr_1.rightX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_2.upperRightDiagonalX);

      curr_2 = new IntOctagon(curr_1.rightX, curr_2.bottomY, curr_2.rightX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_2.upperRightDiagonalX);

      result[0] = curr_1.normalize();
      result[7] = curr_2.normalize();
    }
    curr_1 = result[7];
    curr_2 = result[6];
    if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.upper_y_value(curr_1.rightX) - curr_2.bottomY > curr_1.rightX - curr_1.left_x_value(curr_2.bottomY))
    // switch the vertical upper left divide line to horizontal
    {
      curr_2 = new IntOctagon(curr_1.leftX, curr_2.bottomY, curr_2.rightX, Math.max(curr_2.topY, curr_1.topY), curr_1.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_2.upperRightDiagonalX);

      curr_1 = new IntOctagon(curr_1.leftX, curr_1.bottomY, curr_1.rightX, curr_2.bottomY, curr_1.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_1.upperRightDiagonalX);

      result[7] = curr_1.normalize();
      result[6] = curr_2.normalize();
    }
    curr_1 = result[6];
    curr_2 = result[5];
    if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.upper_y_value(curr_1.rightX) - curr_1.bottomY > curr_2.right_x_value(curr_1.bottomY) - curr_2.leftX)
    // switch the vertical upper right divide line to horizontal
    {
      curr_1 = new IntOctagon(curr_1.leftX, curr_1.bottomY, curr_2.rightX, Math.max(curr_2.topY, curr_1.topY), curr_1.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_2.upperRightDiagonalX);

      curr_2 = new IntOctagon(curr_2.leftX, curr_2.bottomY, curr_2.rightX, curr_1.bottomY, curr_2.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_2.upperRightDiagonalX);

      result[6] = curr_1.normalize();
      result[5] = curr_2.normalize();
    }
    curr_1 = result[5];
    curr_2 = result[4];
    if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.right_x_value(curr_2.topY) - curr_2.leftX > curr_1.upper_y_value(curr_2.leftX) - curr_2.topY)
    // switch the horizontal upper right divide line to vertical
    {
      curr_2 = new IntOctagon(curr_2.leftX, curr_2.bottomY, Math.max(curr_2.rightX, curr_1.rightX), curr_1.topY, curr_1.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_1.upperRightDiagonalX);

      curr_1 = new IntOctagon(curr_1.leftX, curr_1.bottomY, curr_2.leftX, curr_1.topY, curr_1.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_1.upperRightDiagonalX);

      result[5] = curr_1.normalize();
      result[4] = curr_2.normalize();
    }
    curr_1 = result[4];
    curr_2 = result[3];
    if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_1.right_x_value(curr_1.bottomY) - curr_1.leftX > curr_1.bottomY - curr_2.lower_y_value(curr_1.leftX))
    // switch the horizontal lower right divide line to vertical
    {
      curr_1 = new IntOctagon(curr_1.leftX, curr_2.bottomY, Math.max(curr_2.rightX, curr_1.rightX), curr_1.topY, curr_1.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_1.upperRightDiagonalX);

      curr_2 = new IntOctagon(curr_2.leftX, curr_2.bottomY, curr_1.leftX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_2.upperRightDiagonalX);

      result[4] = curr_1.normalize();
      result[3] = curr_2.normalize();
    }

    curr_1 = result[3];
    curr_2 = result[2];

    if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.topY - curr_2.lower_y_value(curr_2.rightX) > curr_1.right_x_value(curr_2.topY) - curr_2.rightX)
    // switch the vertical lower right divide line to horizontal
    {
      curr_2 = new IntOctagon(curr_2.leftX, Math.min(curr_1.bottomY, curr_2.bottomY), curr_1.rightX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_1.upperRightDiagonalX);

      curr_1 = new IntOctagon(curr_1.leftX, curr_2.topY, curr_1.rightX, curr_1.topY, curr_1.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_1.upperRightDiagonalX);

      result[3] = curr_1.normalize();
      result[2] = curr_2.normalize();
    }

    curr_1 = result[2];
    curr_2 = result[1];

    if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_1.topY - curr_1.lower_y_value(curr_1.leftX) > curr_1.leftX - curr_2.left_x_value(curr_1.topY))
    // switch the vertical lower left divide line to horizontal
    {
      curr_1 = new IntOctagon(curr_2.leftX, Math.min(curr_1.bottomY, curr_2.bottomY), curr_1.rightX, curr_1.topY, curr_2.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_1.upperRightDiagonalX);

      curr_2 = new IntOctagon(curr_2.leftX, curr_1.topY, curr_2.rightX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_2.lowerRightDiagonalX, curr_2.lowerLeftDiagonalX, curr_2.upperRightDiagonalX);

      result[2] = curr_1.normalize();
      result[1] = curr_2.normalize();
    }

    curr_1 = result[1];
    curr_2 = result[0];

    if (!(curr_1.is_empty() || curr_2.is_empty()) && curr_2.rightX - curr_2.left_x_value(curr_2.bottomY) > curr_2.bottomY - curr_1.lower_y_value(curr_2.rightX))
    // switch the horizontal lower left divide line to vertical
    {
      curr_2 = new IntOctagon(Math.min(curr_2.leftX, curr_1.leftX), curr_1.bottomY, curr_2.rightX, curr_2.topY, curr_2.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_2.upperRightDiagonalX);

      curr_1 = new IntOctagon(curr_2.rightX, curr_1.bottomY, curr_1.rightX, curr_1.topY, curr_1.upperLeftDiagonalX, curr_1.lowerRightDiagonalX, curr_1.lowerLeftDiagonalX, curr_1.upperRightDiagonalX);

      result[1] = curr_1.normalize();
      result[0] = curr_2.normalize();
    }

    return result;
  }

  @Override
  Simplex[] cutout_from(Simplex p_simplex)
  {
    return this
        .to_Simplex()
        .cutout_from(p_simplex);
  }
}

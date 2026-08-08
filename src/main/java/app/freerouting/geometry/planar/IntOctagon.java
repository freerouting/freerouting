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
  public IntOctagon(int pLx, int pLy, int pRx, int pUy, int pUlx, int pLrx, int pLlx, int pUrx) {
    leftX = pLx;
    bottomY = pLy;
    rightX = pRx;
    topY = pUy;
    upperLeftDiagonalX = pUlx;
    lowerRightDiagonalX = pLrx;
    lowerLeftDiagonalX = pLlx;
    upperRightDiagonalX = pUrx;
  }

  @Override
  public boolean isEmpty() {
    return this == EMPTY;
  }

  @Override
  public boolean isIntOctagon() {
    return true;
  }

  @Override
  public boolean isBounded() {
    return true;
  }

  @Override
  public boolean cornerIsBounded(int pNo) {
    return true;
  }

  @Override
  public IntBox boundingBox() {
    return new IntBox(leftX, bottomY, rightX, topY);
  }

  @Override
  public IntOctagon boundingOctagon() {
    return this;
  }

  @Override
  public IntOctagon boundingTile() {
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
  public IntPoint corner(int pNo) {

    int x;
    int y;
    switch (pNo) {
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

  public int getIdNo() {
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
  public int cornerY(int pNo) {
    return switch (pNo) {
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
  public int cornerX(int pNo) {
    return switch (pNo) {
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
  public int borderLineCount() {
    return 8;
  }

  @Override
  public Line borderLine(int pNo) {
    int aX;
    int aY;
    int bX;
    int bY;
    switch (pNo) {
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
  public IntOctagon translateBy(Vector pRelCoor) {
    // This function is at the moment only implemented for Vectors
    // with integer coordinates.
    // The general implementation is still missing.

    if (pRelCoor.equals(Vector.ZERO)) {
      return this;
    }
    IntVector relCoor = (IntVector) pRelCoor;
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
  public double maxWidth() {
    double width1 = Math.max(rightX - leftX, topY - bottomY);
    double width2 =
        Math.max(
            upperRightDiagonalX - lowerLeftDiagonalX, lowerRightDiagonalX - upperLeftDiagonalX);
    return Math.max(width1, width2 / Limits.sqrt2);
  }

  @Override
  public double minWidth() {
    double width1 = Math.min(rightX - leftX, topY - bottomY);
    double width2 =
        Math.min(
            upperRightDiagonalX - lowerLeftDiagonalX, lowerRightDiagonalX - upperLeftDiagonalX);
    return Math.min(width1, width2 / Limits.sqrt2);
  }

  @Override
  public IntOctagon offset(double pDistance) {
    int width = (int) Math.round(pDistance);
    if (width == 0) {
      return this;
    }
    int diaWidth = (int) Math.round(Limits.sqrt2 * pDistance);
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
  public IntOctagon enlarge(double pOffset) {
    return offset(pOffset);
  }

  @Override
  public boolean contains(RegularTileShape pOther) {
    return pOther.isContainedIn(this);
  }

  @Override
  public RegularTileShape union(RegularTileShape pOther) {
    return pOther.union(this);
  }

  @Override
  public TileShape intersection(TileShape pOther) {
    return pOther.intersection(this);
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
  public boolean isNormalized() {
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
  public Simplex toSimplex() {
    if (isEmpty()) {
      return Simplex.EMPTY;
    }
    if (precalculatedToSimplex == null) {
      Line[] lineArr = new Line[8];
      for (int i = 0; i < 8; i++) {
        lineArr[i] = borderLine(i);
      }
      Simplex currSimplex = new Simplex(lineArr);
      precalculatedToSimplex = currSimplex.removeRedundantLines();
    }
    return precalculatedToSimplex;
  }

  @Override
  public RegularTileShape boundingShape(ShapeBoundingDirections pDirs) {
    return pDirs.bounds(this);
  }

  @Override
  public boolean intersects(Shape pOther) {
    return pOther.intersects(this);
  }

  /**
   * Returns true, if p_point is contained in this octagon. Because of the parameter type
   * FloatPoint, the function may not be exact close to the border.
   */
  @Override
  public boolean contains(FloatPoint pPoint) {
    if (leftX > pPoint.x || bottomY > pPoint.y || rightX < pPoint.x || topY < pPoint.y) {
      return false;
    }
    double tmp1 = pPoint.x - pPoint.y;
    double tmp2 = pPoint.x + pPoint.y;
    return upperLeftDiagonalX <= tmp1
        && lowerRightDiagonalX >= tmp1
        && lowerLeftDiagonalX <= tmp2
        && upperRightDiagonalX >= tmp2;
  }

  /**
   * Calculates the side of the point (p_x, p_y) of the border line with index p_border_line_no. The
   * border lines are located in counterclock sense around this octagon.
   */
  public Side sideOfBorderLine(int pX, int pY, int pBorderLineNo) {

    int tmp =
        switch (pBorderLineNo) {
          case 0 -> this.bottomY - pY;
          case 2 -> pX - this.rightX;
          case 4 -> pY - this.topY;
          case 6 -> this.leftX - pX;
          case 1 -> pX - pY - this.lowerRightDiagonalX;
          case 3 -> pX + pY - this.upperRightDiagonalX;
          case 5 -> this.upperLeftDiagonalX + pY - pX;
          case 7 -> this.lowerLeftDiagonalX - pX - pY;
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
  Simplex intersection(Simplex pOther) {
    return pOther.intersection(this);
  }

  @Override
  public IntOctagon intersection(IntOctagon pOther) {
    IntOctagon result =
        new IntOctagon(
            Math.max(leftX, pOther.leftX),
            Math.max(bottomY, pOther.bottomY),
            Math.min(rightX, pOther.rightX),
            Math.min(topY, pOther.topY),
            Math.max(upperLeftDiagonalX, pOther.upperLeftDiagonalX),
            Math.min(lowerRightDiagonalX, pOther.lowerRightDiagonalX),
            Math.max(lowerLeftDiagonalX, pOther.lowerLeftDiagonalX),
            Math.min(upperRightDiagonalX, pOther.upperRightDiagonalX));
    return result.normalize();
  }

  @Override
  IntOctagon intersection(IntBox pOther) {
    return intersection(pOther.toIntOctagon());
  }

  /** checks if this (normalized) octagon is contained in p_box */
  @Override
  public boolean isContainedIn(IntBox pBox) {
    return leftX >= pBox.ll.x && bottomY >= pBox.ll.y && rightX <= pBox.ur.x && topY <= pBox.ur.y;
  }

  @Override
  public boolean isContainedIn(IntOctagon pOther) {
    return leftX >= pOther.leftX
        && bottomY >= pOther.bottomY
        && rightX <= pOther.rightX
        && topY <= pOther.topY
        && lowerLeftDiagonalX >= pOther.lowerLeftDiagonalX
        && upperLeftDiagonalX >= pOther.upperLeftDiagonalX
        && lowerRightDiagonalX <= pOther.lowerRightDiagonalX
        && upperRightDiagonalX <= pOther.upperRightDiagonalX;
  }

  @Override
  public IntOctagon union(IntOctagon pOther) {
    return new IntOctagon(
        Math.min(leftX, pOther.leftX),
        Math.min(bottomY, pOther.bottomY),
        Math.max(rightX, pOther.rightX),
        Math.max(topY, pOther.topY),
        Math.min(upperLeftDiagonalX, pOther.upperLeftDiagonalX),
        Math.max(lowerRightDiagonalX, pOther.lowerRightDiagonalX),
        Math.min(lowerLeftDiagonalX, pOther.lowerLeftDiagonalX),
        Math.max(upperRightDiagonalX, pOther.upperRightDiagonalX));
  }

  @Override
  public boolean intersects(IntBox pOther) {
    return intersects(pOther.toIntOctagon());
  }

  /** checks, if two normalized Octagons intersect. */
  @Override
  public boolean intersects(IntOctagon pOther) {
    int isLx;
    int isRx;
    isLx = Math.max(pOther.leftX, this.leftX);
    isRx = Math.min(pOther.rightX, this.rightX);
    if (isLx > isRx) {
      return false;
    }

    int isLy;
    int isUy;
    isLy = Math.max(pOther.bottomY, this.bottomY);
    isUy = Math.min(pOther.topY, this.topY);
    if (isLy > isUy) {
      return false;
    }

    int isLlx;
    int isUrx;
    isLlx = Math.max(pOther.lowerLeftDiagonalX, this.lowerLeftDiagonalX);
    isUrx = Math.min(pOther.upperRightDiagonalX, this.upperRightDiagonalX);
    if (isLlx > isUrx) {
      return false;
    }

    int isUlx;
    int isLrx;
    isUlx = Math.max(pOther.upperLeftDiagonalX, this.upperLeftDiagonalX);
    isLrx = Math.min(pOther.lowerRightDiagonalX, this.lowerRightDiagonalX);
    return isUlx <= isLrx;
  }

  /**
   * Returns true, if this octagon intersects with p_other and the intersection is 2-dimensional.
   */
  public boolean overlaps(IntOctagon pOther) {
    int isLx;
    int isRx;
    isLx = Math.max(pOther.leftX, this.leftX);
    isRx = Math.min(pOther.rightX, this.rightX);
    if (isLx >= isRx) {
      return false;
    }

    int isLy;
    int isUy;
    isLy = Math.max(pOther.bottomY, this.bottomY);
    isUy = Math.min(pOther.topY, this.topY);
    if (isLy >= isUy) {
      return false;
    }

    int isLlx;
    int isUrx;
    isLlx = Math.max(pOther.lowerLeftDiagonalX, this.lowerLeftDiagonalX);
    isUrx = Math.min(pOther.upperRightDiagonalX, this.upperRightDiagonalX);
    if (isLlx >= isUrx) {
      return false;
    }

    int isUlx;
    int isLrx;
    isUlx = Math.max(pOther.upperLeftDiagonalX, this.upperLeftDiagonalX);
    isLrx = Math.min(pOther.lowerRightDiagonalX, this.lowerRightDiagonalX);
    return isUlx < isLrx;
  }

  @Override
  public boolean intersects(Simplex pOther) {
    return pOther.intersects(this);
  }

  @Override
  public boolean intersects(Circle pOther) {
    return pOther.intersects(this);
  }

  @Override
  public IntOctagon union(IntBox pOther) {
    return union(pOther.toIntOctagon());
  }

  /** computes the x value of the left boundary of this Octagon at p_y */
  public int leftXValue(int pY) {
    int result = Math.max(leftX, upperLeftDiagonalX + pY);
    return Math.max(result, lowerLeftDiagonalX - pY);
  }

  /** computes the x value of the right boundary of this Octagon at p_y */
  public int rightXValue(int pY) {
    int result = Math.min(rightX, upperRightDiagonalX - pY);
    return Math.min(result, lowerRightDiagonalX + pY);
  }

  /** computes the y value of the lower boundary of this Octagon at p_x */
  public int lowerYValue(int pX) {
    int result = Math.max(bottomY, lowerLeftDiagonalX - pX);
    return Math.max(result, pX - lowerRightDiagonalX);
  }

  /** computes the y value of the upper boundary of this Octagon at p_x */
  public int upperYValue(int pX) {
    int result = Math.min(topY, pX - upperLeftDiagonalX);
    return Math.min(result, upperRightDiagonalX - pX);
  }

  @Override
  public Side compare(RegularTileShape pOther, int pEdgeNo) {
    Side result = pOther.compare(this, pEdgeNo);
    return result.negate();
  }

  @Override
  public Side compare(IntOctagon pOther, int pEdgeNo) {
    Side result;
    switch (pEdgeNo) {
      case 0 -> {
        // compare the lower edge line
        if (bottomY > pOther.bottomY) {
          result = Side.ON_THE_LEFT;
        } else if (bottomY < pOther.bottomY) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 1 -> {
        // compare the lower right edge line
        if (lowerRightDiagonalX < pOther.lowerRightDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (lowerRightDiagonalX > pOther.lowerRightDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 2 -> {
        // compare the right edge line
        if (rightX < pOther.rightX) {
          result = Side.ON_THE_LEFT;
        } else if (rightX > pOther.rightX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 3 -> {
        // compare the upper right edge line
        if (upperRightDiagonalX < pOther.upperRightDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (upperRightDiagonalX > pOther.upperRightDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 4 -> {
        // compare the upper edge line
        if (topY < pOther.topY) {
          result = Side.ON_THE_LEFT;
        } else if (topY > pOther.topY) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 5 -> {
        // compare the upper left edge line
        if (upperLeftDiagonalX > pOther.upperLeftDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (upperLeftDiagonalX < pOther.upperLeftDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 6 -> {
        // compare the left edge line
        if (leftX > pOther.leftX) {
          result = Side.ON_THE_LEFT;
        } else if (leftX < pOther.leftX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 7 -> {
        // compare the lower left edge line
        if (lowerLeftDiagonalX > pOther.lowerLeftDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (lowerLeftDiagonalX < pOther.lowerLeftDiagonalX) {
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
  public Side compare(IntBox pOther, int pEdgeNo) {
    return compare(pOther.toIntOctagon(), pEdgeNo);
  }

  @Override
  public int borderLineIndex(Line pLine) {
    FRLogger.warn("edge_index_of_line not yet implemented for octagons");
    return -1;
  }

  /**
   * Calculates the border point of this octagon from p_point into the 45 degree direction p_dir. If
   * this border point is not an IntPoint, the nearest outside IntPoint of the octagon is returned.
   */
  public IntPoint borderPoint(IntPoint pPoint, FortyfiveDegreeDirection pDir) {
    int resultX;
    int resultY;
    switch (pDir) {
      case RIGHT -> {
        resultX = Math.min(rightX, upperRightDiagonalX - pPoint.y);
        resultX = Math.min(resultX, lowerRightDiagonalX + pPoint.y);
        resultY = pPoint.y;
      }
      case LEFT -> {
        resultX = Math.max(leftX, upperLeftDiagonalX + pPoint.y);
        resultX = Math.max(resultX, lowerLeftDiagonalX - pPoint.y);
        resultY = pPoint.y;
      }
      case UP -> {
        resultX = pPoint.x;
        resultY = Math.min(topY, pPoint.x - upperLeftDiagonalX);
        resultY = Math.min(resultY, upperRightDiagonalX - pPoint.x);
      }
      case DOWN -> {
        resultX = pPoint.x;
        resultY = Math.max(bottomY, lowerLeftDiagonalX - pPoint.x);
        resultY = Math.max(resultY, pPoint.x - lowerRightDiagonalX);
      }
      case RIGHT45 -> {
        resultX = (int) (Math.ceil(0.5 * (pPoint.x - pPoint.y + upperRightDiagonalX)));
        resultX = Math.min(resultX, rightX);
        resultX = Math.min(resultX, pPoint.x - pPoint.y + topY);
        resultY = pPoint.y - pPoint.x + resultX;
      }
      case UP45 -> {
        resultX = (int) (Math.floor(0.5 * (pPoint.x + pPoint.y + upperLeftDiagonalX)));
        resultX = Math.max(resultX, leftX);
        resultX = Math.max(resultX, pPoint.x + pPoint.y - topY);
        resultY = pPoint.y + pPoint.x - resultX;
      }
      case LEFT45 -> {
        resultX = (int) (Math.floor(0.5 * (pPoint.x - pPoint.y + lowerLeftDiagonalX)));
        resultX = Math.max(resultX, leftX);
        resultX = Math.max(resultX, pPoint.x - pPoint.y + bottomY);
        resultY = pPoint.y - pPoint.x + resultX;
      }
      case DOWN45 -> {
        resultX = (int) (Math.ceil(0.5 * (pPoint.x + pPoint.y + lowerRightDiagonalX)));
        resultX = Math.min(resultX, rightX);
        resultX = Math.min(resultX, pPoint.x + pPoint.y - bottomY);
        resultY = pPoint.y + pPoint.x - resultX;
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
  public IntPoint[] nearestBorderProjections(IntPoint pPoint, int pMaxResultPoints) {
    if (!this.contains(pPoint) || pMaxResultPoints <= 0) {
      return new IntPoint[0];
    }
    pMaxResultPoints = Math.min(pMaxResultPoints, 8);
    IntPoint[] result = new IntPoint[pMaxResultPoints];
    double[] minDist = new double[pMaxResultPoints];
    for (int i = 0; i < pMaxResultPoints; i++) {
      minDist[i] = Double.MAX_VALUE;
    }
    FloatPoint insidePoint = pPoint.toFloat();
    for (FortyfiveDegreeDirection currDir : FortyfiveDegreeDirection.values()) {
      IntPoint currBorderPoint = borderPoint(pPoint, currDir);
      double currDist = insidePoint.distanceSquare(currBorderPoint.toFloat());
      for (int i = 0; i < pMaxResultPoints; i++) {
        if (currDist < minDist[i]) {
          for (int k = pMaxResultPoints - 1; k > i; k--) {
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

  Side borderLineSideOf(FloatPoint pPoint, int pLineNo, double pTolerance) {
    return switch (pLineNo) {
      case 0 -> {
        if (pPoint.y > this.bottomY + pTolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (pPoint.y < this.bottomY - pTolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 2 -> {
        if (pPoint.x < this.rightX - pTolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (pPoint.x > this.rightX + pTolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 4 -> {
        if (pPoint.y < this.topY - pTolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (pPoint.y > this.topY + pTolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 6 -> {
        if (pPoint.x > this.leftX + pTolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (pPoint.x < this.leftX - pTolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 1 -> {
        double tmp = pPoint.y - pPoint.x + lowerRightDiagonalX;
        if (tmp > pTolerance)
        // the p_point is above the lower right border line of this octagon
        {
          yield Side.ON_THE_RIGHT;
        } else if (tmp < -pTolerance)
        // the p_point is below the lower right border line of this octagon
        {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 3 -> {
        double tmp = pPoint.x + pPoint.y - upperRightDiagonalX;
        if (tmp < -pTolerance) {
          // the p_point is below the upper right border line of this octagon
          yield Side.ON_THE_RIGHT;
        } else if (tmp > pTolerance) {
          // the p_point is above the upper right border line of this octagon
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 5 -> {
        double tmp = pPoint.y - pPoint.x + upperLeftDiagonalX;
        if (tmp < -pTolerance)
        // the p_point is below the upper left border line of this octagon
        {
          yield Side.ON_THE_RIGHT;
        } else if (tmp > pTolerance)
        // the p_point is above the upper left border line of this octagon
        {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 7 -> {
        double tmp = pPoint.x + pPoint.y - lowerLeftDiagonalX;
        if (tmp > pTolerance) {
          // the p_point is above the lower left border line of this octagon
          yield Side.ON_THE_RIGHT;
        } else if (tmp < -pTolerance) {
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
  public boolean isIntBox() {
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
    if (this.isIntBox()) {
      return this.boundingBox();
    }
    return this;
  }

  @Override
  public TileShape[] cutout(TileShape pShape) {
    return pShape.cutoutFrom(this);
  }

  /** Divide p_d minus this octagon into 8 convex pieces, from which 4 have cut off a corner. */
  @Override
  IntOctagon[] cutoutFrom(IntBox pD) {
    IntOctagon c = this.intersection(pD);

    if (this.isEmpty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntOctagon[] result = new IntOctagon[1];
      result[0] = pD.toIntOctagon();
      return result;
    }

    IntBox[] boxes = new IntBox[4];

    // construct left box

    boxes[0] =
        new IntBox(
            pD.ll.x, c.lowerLeftDiagonalX - c.leftX, c.leftX, c.leftX - c.upperLeftDiagonalX);

    // construct right box

    boxes[1] =
        new IntBox(
            c.rightX, c.rightX - c.lowerRightDiagonalX, pD.ur.x, c.upperRightDiagonalX - c.rightX);

    // construct lower box

    boxes[2] =
        new IntBox(
            c.lowerLeftDiagonalX - c.bottomY,
            pD.ll.y,
            c.lowerRightDiagonalX + c.bottomY,
            c.bottomY);

    // construct upper box

    boxes[3] =
        new IntBox(c.upperLeftDiagonalX + c.topY, c.topY, c.upperRightDiagonalX - c.topY, pD.ur.y);

    IntOctagon[] octagons = new IntOctagon[4];

    // construct upper left octagon

    IntOctagon currOct =
        new IntOctagon(
            pD.ll.x,
            boxes[0].ur.y,
            boxes[3].ll.x,
            pD.ur.y,
            -Limits.CRIT_INT,
            c.upperLeftDiagonalX,
            -Limits.CRIT_INT,
            Limits.CRIT_INT);
    octagons[0] = currOct.normalize();

    // construct lower left octagon

    currOct =
        new IntOctagon(
            pD.ll.x,
            pD.ll.y,
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
            pD.ll.y,
            pD.ur.x,
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
            pD.ur.x,
            pD.ur.y,
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
      result[i] = boxes[i].toIntOctagon();
    }

    // add the 4 octagons to the result
    System.arraycopy(octagons, 0, result, 4, 4);
    return result;
  }

  /** Divide p_divide_octagon minus cut_octagon into 8 convex pieces without sharp angles. */
  @Override
  IntOctagon[] cutoutFrom(IntOctagon pD) {
    IntOctagon c = this.intersection(pD);

    if (this.isEmpty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntOctagon[] result = new IntOctagon[1];
      result[0] = pD;
      return result;
    }

    IntOctagon[] result = new IntOctagon[8];

    int tmp = c.lowerLeftDiagonalX - c.leftX;

    result[0] =
        new IntOctagon(
            pD.leftX,
            tmp,
            c.leftX,
            c.leftX - c.upperLeftDiagonalX,
            pD.upperLeftDiagonalX,
            pD.lowerRightDiagonalX,
            pD.lowerLeftDiagonalX,
            pD.upperRightDiagonalX);

    int tmp2 = c.lowerLeftDiagonalX - c.bottomY;

    result[1] =
        new IntOctagon(
            pD.leftX,
            pD.bottomY,
            tmp2,
            tmp,
            pD.upperLeftDiagonalX,
            pD.lowerRightDiagonalX,
            pD.lowerLeftDiagonalX,
            c.lowerLeftDiagonalX);

    tmp = c.lowerRightDiagonalX + c.bottomY;

    result[2] =
        new IntOctagon(
            tmp2,
            pD.bottomY,
            tmp,
            c.bottomY,
            pD.upperLeftDiagonalX,
            pD.lowerRightDiagonalX,
            pD.lowerLeftDiagonalX,
            pD.upperRightDiagonalX);

    tmp2 = c.rightX - c.lowerRightDiagonalX;

    result[3] =
        new IntOctagon(
            tmp,
            pD.bottomY,
            pD.rightX,
            tmp2,
            c.lowerRightDiagonalX,
            pD.lowerRightDiagonalX,
            pD.lowerLeftDiagonalX,
            pD.upperRightDiagonalX);

    tmp = c.upperRightDiagonalX - c.rightX;

    result[4] =
        new IntOctagon(
            c.rightX,
            tmp2,
            pD.rightX,
            tmp,
            pD.upperLeftDiagonalX,
            pD.lowerRightDiagonalX,
            pD.lowerLeftDiagonalX,
            pD.upperRightDiagonalX);

    tmp2 = c.upperRightDiagonalX - c.topY;

    result[5] =
        new IntOctagon(
            tmp2,
            tmp,
            pD.rightX,
            pD.topY,
            pD.upperLeftDiagonalX,
            pD.lowerRightDiagonalX,
            c.upperRightDiagonalX,
            pD.upperRightDiagonalX);

    tmp = c.upperLeftDiagonalX + c.topY;

    result[6] =
        new IntOctagon(
            tmp,
            c.topY,
            tmp2,
            pD.topY,
            pD.upperLeftDiagonalX,
            pD.lowerRightDiagonalX,
            pD.lowerLeftDiagonalX,
            pD.upperRightDiagonalX);

    tmp2 = c.leftX - c.upperLeftDiagonalX;

    result[7] =
        new IntOctagon(
            pD.leftX,
            tmp2,
            tmp,
            pD.topY,
            pD.upperLeftDiagonalX,
            c.upperLeftDiagonalX,
            pD.lowerLeftDiagonalX,
            pD.upperRightDiagonalX);

    for (int i = 0; i < 8; i++) {
      result[i] = result[i].normalize();
    }

    IntOctagon curr1 = result[0];
    IntOctagon curr2 = result[7];

    if (!(curr1.isEmpty() || curr2.isEmpty())
        && curr1.rightX - curr1.leftXValue(curr1.topY)
            > curr2.upperYValue(curr1.rightX) - curr2.bottomY) {
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
    if (!(curr1.isEmpty() || curr2.isEmpty())
        && curr2.upperYValue(curr1.rightX) - curr2.bottomY
            > curr1.rightX - curr1.leftXValue(curr2.bottomY))
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
    if (!(curr1.isEmpty() || curr2.isEmpty())
        && curr2.upperYValue(curr1.rightX) - curr1.bottomY
            > curr2.rightXValue(curr1.bottomY) - curr2.leftX)
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
    if (!(curr1.isEmpty() || curr2.isEmpty())
        && curr2.rightXValue(curr2.topY) - curr2.leftX
            > curr1.upperYValue(curr2.leftX) - curr2.topY)
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
    if (!(curr1.isEmpty() || curr2.isEmpty())
        && curr1.rightXValue(curr1.bottomY) - curr1.leftX
            > curr1.bottomY - curr2.lowerYValue(curr1.leftX))
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

    if (!(curr1.isEmpty() || curr2.isEmpty())
        && curr2.topY - curr2.lowerYValue(curr2.rightX)
            > curr1.rightXValue(curr2.topY) - curr2.rightX)
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

    if (!(curr1.isEmpty() || curr2.isEmpty())
        && curr1.topY - curr1.lowerYValue(curr1.leftX)
            > curr1.leftX - curr2.leftXValue(curr1.topY))
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

    if (!(curr1.isEmpty() || curr2.isEmpty())
        && curr2.rightX - curr2.leftXValue(curr2.bottomY)
            > curr2.bottomY - curr1.lowerYValue(curr2.rightX))
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
  Simplex[] cutoutFrom(Simplex pSimplex) {
    return this.toSimplex().cutoutFrom(pSimplex);
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

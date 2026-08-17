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

  /** Result of toSimplex() memorized for performance reasons. */
  private Simplex precalculatedToSimplex;

  /**
   * Creates an IntOctagon from 8 integer boundary values.
   *
   * @param leftX the smallest x value of the shape
   * @param bottomY the smallest y value of the shape
   * @param rightX the biggest x value of the shape
   * @param topY the biggest y value of the shape
   * @param upperLeftDiagonalX the intersection of the upper left diagonal boundary line with the x
   *     axis
   * @param lowerRightDiagonalX the intersection of the lower right diagonal boundary line with the
   *     x axis
   * @param lowerLeftDiagonalX the intersection of the lower left diagonal boundary line with the x
   *     axis
   * @param upperRightDiagonalX the intersection of the upper right diagonal boundary line with the
   *     x axis
   */
  public IntOctagon(
      int leftX,
      int bottomY,
      int rightX,
      int topY,
      int upperLeftDiagonalX,
      int lowerRightDiagonalX,
      int lowerLeftDiagonalX,
      int upperRightDiagonalX) {
    this.leftX = leftX;
    this.bottomY = bottomY;
    this.rightX = rightX;
    this.topY = topY;
    this.upperLeftDiagonalX = upperLeftDiagonalX;
    this.lowerRightDiagonalX = lowerRightDiagonalX;
    this.lowerLeftDiagonalX = lowerLeftDiagonalX;
    this.upperRightDiagonalX = upperRightDiagonalX;
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
  public boolean cornerIsBounded(int no) {
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
  public IntPoint corner(int no) {
    return switch (no) {
      case 0 ->
          new IntPoint(lowerLeftDiagonalX - bottomY, bottomY); // lower-left (bottom horizontal)
      case 1 ->
          new IntPoint(lowerRightDiagonalX + bottomY, bottomY); // lower-right (bottom horizontal)
      case 2 -> new IntPoint(rightX, rightX - lowerRightDiagonalX); // bottom-right vertical
      case 3 -> new IntPoint(rightX, upperRightDiagonalX - rightX); // top-right vertical
      case 4 -> new IntPoint(upperRightDiagonalX - topY, topY); // upper-right (top horizontal)
      case 5 -> new IntPoint(upperLeftDiagonalX + topY, topY); // upper-left (top horizontal)
      case 6 -> new IntPoint(leftX, leftX - upperLeftDiagonalX); // top-left vertical
      case 7 -> new IntPoint(leftX, lowerLeftDiagonalX - leftX); // bottom-left vertical
      default -> throw new IllegalArgumentException("IntOctagon.corner: no out of range");
    };
  }

  /** Returns a stable identifier for this octagon. */
  @Override
  public int getId() {
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
  public int cornerY(int no) {
    return switch (no) {
      case 0, 1 -> bottomY;
      case 2 -> rightX - lowerRightDiagonalX;
      case 3 -> upperRightDiagonalX - rightX;
      case 4, 5 -> topY;
      case 6 -> leftX - upperLeftDiagonalX;
      case 7 -> lowerLeftDiagonalX - leftX;
      default -> throw new IllegalArgumentException("IntOctagon.corner: no out of range");
    };
  }

  /**
   * Additional to the function corner() for performance reasons to avoid allocation of an IntPoint.
   */
  public int cornerX(int no) {
    return switch (no) {
      case 0 -> lowerLeftDiagonalX - bottomY;
      case 1 -> lowerRightDiagonalX + bottomY;
      case 2, 3 -> rightX;
      case 4 -> upperRightDiagonalX - topY;
      case 5 -> upperLeftDiagonalX + topY;
      case 6, 7 -> leftX;
      default -> throw new IllegalArgumentException("IntOctagon.corner: no out of range");
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
  public Line borderLine(int no) {
    return switch (no) {
      case 0 -> new Line(0, bottomY, 1, bottomY); // lower boundary line
      case 1 ->
          new Line(lowerRightDiagonalX, 0, lowerRightDiagonalX + 1, 1); // lower right boundary line
      case 2 -> new Line(rightX, 0, rightX, 1); // right boundary line
      case 3 ->
          new Line(upperRightDiagonalX, 0, upperRightDiagonalX - 1, 1); // upper right boundary line
      case 4 -> new Line(0, topY, -1, topY); // upper boundary line
      case 5 ->
          new Line(upperLeftDiagonalX, 0, upperLeftDiagonalX - 1, -1); // upper left boundary line
      case 6 -> new Line(leftX, 0, leftX, -1); // left boundary line
      case 7 ->
          new Line(lowerLeftDiagonalX, 0, lowerLeftDiagonalX + 1, -1); // lower left boundary line
      default -> throw new IllegalArgumentException("IntOctagon.borderLine: no out of range");
    };
  }

  @Override
  public IntOctagon translateBy(Vector relCoor) {
    // This function is at the moment only implemented for Vectors
    // with integer coordinates.
    // The general implementation is still missing.

    if (relCoor.equals(Vector.ZERO)) {
      return this;
    }
    IntVector relativeCoordinate = (IntVector) relCoor;
    return new IntOctagon(
        leftX + relativeCoordinate.x,
        bottomY + relativeCoordinate.y,
        rightX + relativeCoordinate.x,
        topY + relativeCoordinate.y,
        upperLeftDiagonalX + relativeCoordinate.x - relativeCoordinate.y,
        lowerRightDiagonalX + relativeCoordinate.x - relativeCoordinate.y,
        lowerLeftDiagonalX + relativeCoordinate.x + relativeCoordinate.y,
        upperRightDiagonalX + relativeCoordinate.x + relativeCoordinate.y);
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
  public IntOctagon offset(double distance) {
    int width = (int) Math.round(distance);
    if (width == 0) {
      return this;
    }
    int diaWidth = (int) Math.round(Limits.sqrt2 * distance);
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
  public IntOctagon enlarge(double offset) {
    return offset(offset);
  }

  @Override
  public boolean contains(RegularTileShape other) {
    return other.isContainedIn(this);
  }

  /**
   * Returns true if point is contained in this octagon. Because of the parameter type FloatPoint,
   * the function may not be exact close to the border.
   */
  @Override
  public boolean contains(FloatPoint point) {
    if (leftX > point.x || bottomY > point.y || rightX < point.x || topY < point.y) {
      return false;
    }
    double tmp1 = point.x - point.y;
    double tmp2 = point.x + point.y;
    return upperLeftDiagonalX <= tmp1
        && lowerRightDiagonalX >= tmp1
        && lowerLeftDiagonalX <= tmp2
        && upperRightDiagonalX >= tmp2;
  }

  @Override
  public RegularTileShape union(RegularTileShape other) {
    return other.union(this);
  }

  @Override
  public IntOctagon union(IntOctagon other) {
    return new IntOctagon(
        Math.min(leftX, other.leftX),
        Math.min(bottomY, other.bottomY),
        Math.max(rightX, other.rightX),
        Math.max(topY, other.topY),
        Math.min(upperLeftDiagonalX, other.upperLeftDiagonalX),
        Math.max(lowerRightDiagonalX, other.lowerRightDiagonalX),
        Math.min(lowerLeftDiagonalX, other.lowerLeftDiagonalX),
        Math.max(upperRightDiagonalX, other.upperRightDiagonalX));
  }

  @Override
  public IntOctagon union(IntBox other) {
    return union(other.toIntOctagon());
  }

  @Override
  public TileShape intersection(TileShape other) {
    return other.intersection(this);
  }

  @Override
  Simplex intersection(Simplex other) {
    return other.intersection(this);
  }

  @Override
  public IntOctagon intersection(IntOctagon other) {
    IntOctagon result =
        new IntOctagon(
            Math.max(leftX, other.leftX),
            Math.max(bottomY, other.bottomY),
            Math.min(rightX, other.rightX),
            Math.min(topY, other.topY),
            Math.max(upperLeftDiagonalX, other.upperLeftDiagonalX),
            Math.min(lowerRightDiagonalX, other.lowerRightDiagonalX),
            Math.max(lowerLeftDiagonalX, other.lowerLeftDiagonalX),
            Math.min(upperRightDiagonalX, other.upperRightDiagonalX));
    return result.normalize();
  }

  @Override
  IntOctagon intersection(IntBox other) {
    return intersection(other.toIntOctagon());
  }

  /** Returns an equivalent octagon with all redundant bounds tightened. */
  @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
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

    if (newLx < newLlx - newUy) {
      // the point newLx, newUy is the lower left border line of
      // this octagon
      // change newLx , that the lower left border line runs through
      // this point
      newLx = newLlx - newUy;
    }

    if (newLx < newUlx + newLy) {
      // the point newLx, newLy is above the upper left border line of
      // this octagon
      // change newLx , that the upper left border line runs through
      // this point
      newLx = newUlx + newLy;
    }

    if (newRx > newUrx - newLy) {
      // the point newRx, newLy is above the upper right border line of
      // this octagon
      // change newRx , that the upper right border line runs through
      // this point
      newRx = newUrx - newLy;
    }

    if (newRx > newLrx + newUy) {
      // the point newRx, newUy is below the lower right border line of
      // this octagon
      // change rx , that the lower right border line runs through
      // this point
      newRx = newLrx + newUy;
    }

    if (newLy < newLx - newLrx) {
      // the point lx, ly is below the lower right border line of this
      // octagon
      // change ly, so that the lower right border line runs through
      // this point
      newLy = newLx - newLrx;
    }

    if (newLy < newLlx - newRx) {
      // the point rx, ly is below the lower left border line of
      // this octagon.
      // change ly, so that the lower left border line runs through
      // this point
      newLy = newLlx - newRx;
    }

    if (newUy > newUrx - newLx) {
      // the point lx, uy is above the upper right border line of
      // this octagon.
      // Change the uy, so that the upper right border line runs through
      // this point.
      newUy = newUrx - newLx;
    }

    if (newUy > newRx - newUlx) {
      // the point rx, uy is above the upper left border line of
      // this octagon.
      // Change the uy, so that the upper left border line runs through
      // this point.
      newUy = newRx - newUlx;
    }

    if (newLlx - newLx < newLy) {
      // The point lx, ly is above the lower left border line of
      // this octagon.
      // Change the lower left line, so that it runs through this point.
      newLlx = newLx + newLy;
    }

    if (newRx - newLrx < newLy) {
      // the point rx, ly is above the lower right border line of
      // this octagon.
      // Change the lower right line, so that it runs through this point.
      newLrx = newRx - newLy;
    }

    if (newUrx - newRx > newUy) {
      // the point rx, uy is below the upper right border line of oct.
      // Change the upper right line, so that it runs through this point.
      newUrx = newUy + newRx;
    }

    if (newLx - newUlx > newUy) {
      // the point lx, uy is below the upper left border line of
      // this octagon.
      // Change the upper left line, so that it runs through this point.
      newUlx = newLx - newUy;
    }

    int diagUpperY = (int) Math.ceil((newUrx - newUlx) / 2.0);

    if (newUy > diagUpperY) {
      // the intersection of the upper right and the upper left border
      // line is below newUy.  Adjust newUy to diagUpperY.
      newUy = diagUpperY;
    }

    int diagLowerY = (int) Math.floor((newLlx - newLrx) / 2.0);

    if (newLy < diagLowerY) {
      // the intersection of the lower right and the lower left border
      // line is above newLy.  Adjust newLy to diagLowerY.
      newLy = diagLowerY;
    }

    int diagRightX = (int) Math.ceil((newUrx + newLrx) / 2.0);

    if (newRx > diagRightX) {
      // the intersection of the upper right and the lower right border
      // line is to the left of  right x.  Adjust newRx to diagRightX.
      newRx = diagRightX;
    }

    int diagLeftX = (int) Math.floor((newLlx + newUlx) / 2.0);

    if (newLx < diagLeftX) {
      // the intersection of the lower left and the upper left border
      // line is to the right of left x.  Adjust newLx to diagLeftX.
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
      Line[] lines = new Line[8];
      for (int i = 0; i < 8; i++) {
        lines[i] = borderLine(i);
      }
      Simplex currentSimplex = new Simplex(lines);
      precalculatedToSimplex = currentSimplex.removeRedundantLines();
    }
    return precalculatedToSimplex;
  }

  @Override
  public RegularTileShape boundingShape(ShapeBoundingDirections dirs) {
    return dirs.bounds(this);
  }

  /**
   * Calculates the side of the point (x, y) of the border line with index borderLineNo. The border
   * lines are located in counterclock sense around this octagon.
   */
  public Side sideOfBorderLine(int x, int y, int borderLineNo) {

    int tmp =
        switch (borderLineNo) {
          case 0 -> this.bottomY - y; // lower boundary line
          case 1 -> x - y - this.lowerRightDiagonalX; // lower-right diagonal line
          case 2 -> x - this.rightX; // right boundary line
          case 3 -> x + y - this.upperRightDiagonalX; // upper-right diagonal line
          case 4 -> y - this.topY; // upper boundary line
          case 5 -> this.upperLeftDiagonalX + y - x; // upper-left diagonal line
          case 6 -> this.leftX - x; // left boundary line
          case 7 -> this.lowerLeftDiagonalX - x - y; // lower-left diagonal line
          default -> {
            FRLogger.warn("IntOctagon.sideOfBorderLine: borderLineNo out of range");
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

  /** Checks if this normalized octagon is contained in box. */
  @Override
  public boolean isContainedIn(IntBox box) {
    return leftX >= box.ll.x && bottomY >= box.ll.y && rightX <= box.ur.x && topY <= box.ur.y;
  }

  @Override
  public boolean isContainedIn(IntOctagon other) {
    return leftX >= other.leftX
        && bottomY >= other.bottomY
        && rightX <= other.rightX
        && topY <= other.topY
        && lowerLeftDiagonalX >= other.lowerLeftDiagonalX
        && upperLeftDiagonalX >= other.upperLeftDiagonalX
        && lowerRightDiagonalX <= other.lowerRightDiagonalX
        && upperRightDiagonalX <= other.upperRightDiagonalX;
  }

  @Override
  public boolean intersects(Shape other) {
    return other.intersects(this);
  }

  @Override
  public boolean intersects(IntBox other) {
    return intersects(other.toIntOctagon());
  }

  /** Checks if two normalized octagons intersect. */
  @Override
  public boolean intersects(IntOctagon other) {
    int isLx;
    int isRx;
    isLx = Math.max(other.leftX, this.leftX);
    isRx = Math.min(other.rightX, this.rightX);
    if (isLx > isRx) {
      return false;
    }

    int isLy;
    int isUy;
    isLy = Math.max(other.bottomY, this.bottomY);
    isUy = Math.min(other.topY, this.topY);
    if (isLy > isUy) {
      return false;
    }

    int isLlx;
    int isUrx;
    isLlx = Math.max(other.lowerLeftDiagonalX, this.lowerLeftDiagonalX);
    isUrx = Math.min(other.upperRightDiagonalX, this.upperRightDiagonalX);
    if (isLlx > isUrx) {
      return false;
    }

    int isUlx;
    int isLrx;
    isUlx = Math.max(other.upperLeftDiagonalX, this.upperLeftDiagonalX);
    isLrx = Math.min(other.lowerRightDiagonalX, this.lowerRightDiagonalX);
    return isUlx <= isLrx;
  }

  @Override
  public boolean intersects(Simplex other) {
    return other.intersects(this);
  }

  @Override
  public boolean intersects(Circle other) {
    return other.intersects(this);
  }

  /** Returns true, if this octagon intersects with other and the intersection is 2-dimensional. */
  public boolean overlaps(IntOctagon other) {
    int isLx;
    int isRx;
    isLx = Math.max(other.leftX, this.leftX);
    isRx = Math.min(other.rightX, this.rightX);
    if (isLx >= isRx) {
      return false;
    }

    int isLy;
    int isUy;
    isLy = Math.max(other.bottomY, this.bottomY);
    isUy = Math.min(other.topY, this.topY);
    if (isLy >= isUy) {
      return false;
    }

    int isLlx;
    int isUrx;
    isLlx = Math.max(other.lowerLeftDiagonalX, this.lowerLeftDiagonalX);
    isUrx = Math.min(other.upperRightDiagonalX, this.upperRightDiagonalX);
    if (isLlx >= isUrx) {
      return false;
    }

    int isUlx;
    int isLrx;
    isUlx = Math.max(other.upperLeftDiagonalX, this.upperLeftDiagonalX);
    isLrx = Math.min(other.lowerRightDiagonalX, this.lowerRightDiagonalX);
    return isUlx < isLrx;
  }

  /** Computes the x value of the left boundary of this octagon at y. */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public int leftXValue(int y) {
    int result = Math.max(leftX, upperLeftDiagonalX + y);
    return Math.max(result, lowerLeftDiagonalX - y);
  }

  /** Computes the x value of the right boundary of this octagon at y. */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public int rightXValue(int y) {
    int result = Math.min(rightX, upperRightDiagonalX - y);
    return Math.min(result, lowerRightDiagonalX + y);
  }

  /** Computes the y value of the lower boundary of this octagon at x. */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public int lowerYValue(int x) {
    int result = Math.max(bottomY, lowerLeftDiagonalX - x);
    return Math.max(result, x - lowerRightDiagonalX);
  }

  /** Computes the y value of the upper boundary of this octagon at x. */
  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  public int upperYValue(int x) {
    int result = Math.min(topY, x - upperLeftDiagonalX);
    return Math.min(result, upperRightDiagonalX - x);
  }

  @Override
  public Side compare(RegularTileShape other, int edgeIndex) {
    Side result = other.compare(this, edgeIndex);
    return result.negate();
  }

  @Override
  public Side compare(IntOctagon other, int edgeIndex) {
    Side result;
    switch (edgeIndex) {
      case 0 -> {
        // compare the lower edge line
        if (bottomY > other.bottomY) {
          result = Side.ON_THE_LEFT;
        } else if (bottomY < other.bottomY) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 1 -> {
        // compare the lower right edge line
        if (lowerRightDiagonalX < other.lowerRightDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (lowerRightDiagonalX > other.lowerRightDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 2 -> {
        // compare the right edge line
        if (rightX < other.rightX) {
          result = Side.ON_THE_LEFT;
        } else if (rightX > other.rightX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 3 -> {
        // compare the upper right edge line
        if (upperRightDiagonalX < other.upperRightDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (upperRightDiagonalX > other.upperRightDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 4 -> {
        // compare the upper edge line
        if (topY < other.topY) {
          result = Side.ON_THE_LEFT;
        } else if (topY > other.topY) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 5 -> {
        // compare the upper left edge line
        if (upperLeftDiagonalX > other.upperLeftDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (upperLeftDiagonalX < other.upperLeftDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 6 -> {
        // compare the left edge line
        if (leftX > other.leftX) {
          result = Side.ON_THE_LEFT;
        } else if (leftX < other.leftX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 7 -> {
        // compare the lower left edge line
        if (lowerLeftDiagonalX > other.lowerLeftDiagonalX) {
          result = Side.ON_THE_LEFT;
        } else if (lowerLeftDiagonalX < other.lowerLeftDiagonalX) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      default -> throw new IllegalArgumentException("IntBox.compare: edgeIndex out of range");
    }
    return result;
  }

  @Override
  public Side compare(IntBox other, int edgeIndex) {
    return compare(other.toIntOctagon(), edgeIndex);
  }

  @Override
  public int borderLineIndex(Line line) {
    FRLogger.warn("edge_index_of_line not yet implemented for octagons");
    return -1;
  }

  /**
   * Calculates the border point of this octagon from point into the 45 degree direction dir. If
   * this border point is not an IntPoint, the nearest outside IntPoint of the octagon is returned.
   */
  public IntPoint borderPoint(IntPoint point, FortyfiveDegreeDirection dir) {
    int resultX;
    int resultY;
    switch (dir) {
      case RIGHT -> {
        resultX = Math.min(rightX, upperRightDiagonalX - point.y);
        resultX = Math.min(resultX, lowerRightDiagonalX + point.y);
        resultY = point.y;
      }
      case LEFT -> {
        resultX = Math.max(leftX, upperLeftDiagonalX + point.y);
        resultX = Math.max(resultX, lowerLeftDiagonalX - point.y);
        resultY = point.y;
      }
      case UP -> {
        resultX = point.x;
        resultY = Math.min(topY, point.x - upperLeftDiagonalX);
        resultY = Math.min(resultY, upperRightDiagonalX - point.x);
      }
      case DOWN -> {
        resultX = point.x;
        resultY = Math.max(bottomY, lowerLeftDiagonalX - point.x);
        resultY = Math.max(resultY, point.x - lowerRightDiagonalX);
      }
      case RIGHT45 -> {
        resultX = (int) (Math.ceil(0.5 * (point.x - point.y + upperRightDiagonalX)));
        resultX = Math.min(resultX, rightX);
        resultX = Math.min(resultX, point.x - point.y + topY);
        resultY = point.y - point.x + resultX;
      }
      case UP45 -> {
        resultX = (int) (Math.floor(0.5 * (point.x + point.y + upperLeftDiagonalX)));
        resultX = Math.max(resultX, leftX);
        resultX = Math.max(resultX, point.x + point.y - topY);
        resultY = point.y + point.x - resultX;
      }
      case LEFT45 -> {
        resultX = (int) (Math.floor(0.5 * (point.x - point.y + lowerLeftDiagonalX)));
        resultX = Math.max(resultX, leftX);
        resultX = Math.max(resultX, point.x - point.y + bottomY);
        resultY = point.y - point.x + resultX;
      }
      case DOWN45 -> {
        resultX = (int) (Math.ceil(0.5 * (point.x + point.y + lowerRightDiagonalX)));
        resultX = Math.min(resultX, rightX);
        resultX = Math.min(resultX, point.x + point.y - bottomY);
        resultY = point.y + point.x - resultX;
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
   * Calculates the sorted maxResultPoints nearest points on the border of this octagon in the
   * 45-degree directions. point is assumed to be located in the interior of this octagon.
   */
  public IntPoint[] nearestBorderProjections(IntPoint point, int maxResultPoints) {
    if (!this.contains(point) || maxResultPoints <= 0) {
      return new IntPoint[0];
    }
    maxResultPoints = Math.min(maxResultPoints, 8);
    IntPoint[] result = new IntPoint[maxResultPoints];
    double[] minDist = new double[maxResultPoints];
    for (int i = 0; i < maxResultPoints; i++) {
      minDist[i] = Double.MAX_VALUE;
    }
    FloatPoint insidePoint = point.toFloat();
    for (FortyfiveDegreeDirection currentDirection : FortyfiveDegreeDirection.values()) {
      IntPoint currentBorderPoint = borderPoint(point, currentDirection);
      double currentDistance = insidePoint.distanceSquare(currentBorderPoint.toFloat());
      for (int i = 0; i < maxResultPoints; i++) {
        if (currentDistance < minDist[i]) {
          for (int k = maxResultPoints - 1; k > i; k--) {
            minDist[k] = minDist[k - 1];
            result[k] = result[k - 1];
          }
          minDist[i] = currentDistance;
          result[i] = currentBorderPoint;
          break;
        }
      }
    }
    return result;
  }

  Side borderLineSideOf(FloatPoint point, int lineIndex, double tolerance) {
    return switch (lineIndex) {
      case 0 -> {
        if (point.y > this.bottomY + tolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (point.y < this.bottomY - tolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 2 -> {
        if (point.x < this.rightX - tolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (point.x > this.rightX + tolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 4 -> {
        if (point.y < this.topY - tolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (point.y > this.topY + tolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 6 -> {
        if (point.x > this.leftX + tolerance) {
          yield Side.ON_THE_RIGHT;
        } else if (point.x < this.leftX - tolerance) {
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 1 -> {
        double tmp = point.y - point.x + lowerRightDiagonalX;
        if (tmp > tolerance) {
          // the point is above the lower right border line of this octagon
          yield Side.ON_THE_RIGHT;
        } else if (tmp < -tolerance) {
          // the point is below the lower right border line of this octagon
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 3 -> {
        double tmp = point.x + point.y - upperRightDiagonalX;
        if (tmp < -tolerance) {
          // the point is below the upper right border line of this octagon
          yield Side.ON_THE_RIGHT;
        } else if (tmp > tolerance) {
          // the point is above the upper right border line of this octagon
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 5 -> {
        double tmp = point.y - point.x + upperLeftDiagonalX;
        if (tmp < -tolerance) {
          // the point is below the upper left border line of this octagon
          yield Side.ON_THE_RIGHT;
        } else if (tmp > tolerance) {
          // the point is above the upper left border line of this octagon
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      case 7 -> {
        double tmp = point.x + point.y - lowerLeftDiagonalX;
        if (tmp > tolerance) {
          // the point is above the lower left border line of this octagon
          yield Side.ON_THE_RIGHT;
        } else if (tmp < -tolerance) {
          // the point is below the lower left border line of this octagon
          yield Side.ON_THE_LEFT;
        } else {
          yield Side.COLLINEAR;
        }
      }
      default -> {
        FRLogger.warn("IntOctagon.borderLineSideOf: lineIndex out of range");
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
  public TileShape[] cutout(TileShape shape) {
    return shape.cutoutFrom(this);
  }

  /** Divide d minus this octagon into 8 convex pieces, from which 4 have cut off a corner. */
  @Override
  IntOctagon[] cutoutFrom(IntBox d) {
    IntOctagon c = this.intersection(d);

    if (this.isEmpty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntOctagon[] result = new IntOctagon[1];
      result[0] = d.toIntOctagon();
      return result;
    }

    IntBox[] boxes = new IntBox[4];

    // construct left box

    boxes[0] =
        new IntBox(d.ll.x, c.lowerLeftDiagonalX - c.leftX, c.leftX, c.leftX - c.upperLeftDiagonalX);

    // construct right box

    boxes[1] =
        new IntBox(
            c.rightX, c.rightX - c.lowerRightDiagonalX, d.ur.x, c.upperRightDiagonalX - c.rightX);

    // construct lower box

    boxes[2] =
        new IntBox(
            c.lowerLeftDiagonalX - c.bottomY, d.ll.y, c.lowerRightDiagonalX + c.bottomY, c.bottomY);

    // construct upper box

    boxes[3] =
        new IntBox(c.upperLeftDiagonalX + c.topY, c.topY, c.upperRightDiagonalX - c.topY, d.ur.y);

    IntOctagon[] octagons = new IntOctagon[4];

    // construct upper left octagon

    IntOctagon currentOct =
        new IntOctagon(
            d.ll.x,
            boxes[0].ur.y,
            boxes[3].ll.x,
            d.ur.y,
            -Limits.CRIT_INT,
            c.upperLeftDiagonalX,
            -Limits.CRIT_INT,
            Limits.CRIT_INT);
    octagons[0] = currentOct.normalize();

    // construct lower left octagon

    currentOct =
        new IntOctagon(
            d.ll.x,
            d.ll.y,
            boxes[2].ll.x,
            boxes[0].ll.y,
            -Limits.CRIT_INT,
            Limits.CRIT_INT,
            -Limits.CRIT_INT,
            c.lowerLeftDiagonalX);
    octagons[1] = currentOct.normalize();

    // construct lower right octagon

    currentOct =
        new IntOctagon(
            boxes[2].ur.x,
            d.ll.y,
            d.ur.x,
            boxes[1].ll.y,
            c.lowerRightDiagonalX,
            Limits.CRIT_INT,
            -Limits.CRIT_INT,
            Limits.CRIT_INT);
    octagons[2] = currentOct.normalize();

    // construct upper right octagon

    currentOct =
        new IntOctagon(
            boxes[3].ur.x,
            boxes[1].ur.y,
            d.ur.x,
            d.ur.y,
            -Limits.CRIT_INT,
            Limits.CRIT_INT,
            c.upperRightDiagonalX,
            Limits.CRIT_INT);
    octagons[3] = currentOct.normalize();

    // optimise the result to minimum cumulative circumference

    IntBox b = boxes[0];
    IntOctagon o = octagons[0];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
      // switch the horizontal upper left divide line to vertical

      boxes[0] = new IntBox(b.ll.x, b.ll.y, b.ur.x, o.topY);
      currentOct =
          new IntOctagon(
              b.ur.x,
              o.bottomY,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[0] = currentOct.normalize();
    }

    b = boxes[3];
    o = octagons[0];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
      // switch the vertical upper left divide line to horizontal

      boxes[3] = new IntBox(o.leftX, b.ll.y, b.ur.x, b.ur.y);
      currentOct =
          new IntOctagon(
              o.leftX,
              o.bottomY,
              o.rightX,
              b.ll.y,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[0] = currentOct.normalize();
    }
    b = boxes[3];
    o = octagons[3];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
      // switch the vertical upper right divide line to horizontal

      boxes[3] = new IntBox(b.ll.x, b.ll.y, o.rightX, b.ur.y);
      currentOct =
          new IntOctagon(
              o.leftX,
              o.bottomY,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[3] = currentOct.normalize();
    }
    b = boxes[1];
    o = octagons[3];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
      // switch the horizontal upper right divide line to vertical

      boxes[1] = new IntBox(b.ll.x, b.ll.y, b.ur.x, o.topY);
      currentOct =
          new IntOctagon(
              o.leftX,
              o.bottomY,
              b.ll.x,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[3] = currentOct.normalize();
    }
    b = boxes[1];
    o = octagons[2];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
      // switch the horizontal lower right divide line to vertical

      boxes[1] = new IntBox(b.ll.x, o.bottomY, b.ur.x, b.ur.y);
      currentOct =
          new IntOctagon(
              o.leftX,
              o.bottomY,
              b.ll.x,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[2] = currentOct.normalize();
    }
    b = boxes[2];
    o = octagons[2];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
      // switch the vertical lower right divide line to horizontal

      boxes[2] = new IntBox(b.ll.x, b.ll.y, o.rightX, b.ur.y);
      currentOct =
          new IntOctagon(
              o.leftX,
              b.ur.y,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[2] = currentOct.normalize();
    }
    b = boxes[2];
    o = octagons[1];
    if (b.ur.y - b.ll.y > o.rightX - o.leftX) {
      // switch the vertical lower  left divide line to horizontal

      boxes[2] = new IntBox(o.leftX, b.ll.y, b.ur.x, b.ur.y);
      currentOct =
          new IntOctagon(
              o.leftX,
              b.ur.y,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[1] = currentOct.normalize();
    }
    b = boxes[0];
    o = octagons[1];
    if (b.ur.x - b.ll.x > o.topY - o.bottomY) {
      // switch the horizontal lower left divide line to vertical
      boxes[0] = new IntBox(b.ll.x, o.bottomY, b.ur.x, b.ur.y);
      currentOct =
          new IntOctagon(
              b.ur.x,
              o.bottomY,
              o.rightX,
              o.topY,
              o.upperLeftDiagonalX,
              o.lowerRightDiagonalX,
              o.lowerLeftDiagonalX,
              o.upperRightDiagonalX);
      octagons[1] = currentOct.normalize();
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

  /** Divide divideOctagon minus cut_octagon into 8 convex pieces without sharp angles. */
  @Override
  IntOctagon[] cutoutFrom(IntOctagon d) {
    IntOctagon c = this.intersection(d);

    if (this.isEmpty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntOctagon[] result = new IntOctagon[1];
      result[0] = d;
      return result;
    }

    IntOctagon[] result = new IntOctagon[8];

    int tmp = c.lowerLeftDiagonalX - c.leftX;

    result[0] =
        new IntOctagon(
            d.leftX,
            tmp,
            c.leftX,
            c.leftX - c.upperLeftDiagonalX,
            d.upperLeftDiagonalX,
            d.lowerRightDiagonalX,
            d.lowerLeftDiagonalX,
            d.upperRightDiagonalX);

    int tmp2 = c.lowerLeftDiagonalX - c.bottomY;

    result[1] =
        new IntOctagon(
            d.leftX,
            d.bottomY,
            tmp2,
            tmp,
            d.upperLeftDiagonalX,
            d.lowerRightDiagonalX,
            d.lowerLeftDiagonalX,
            c.lowerLeftDiagonalX);

    tmp = c.lowerRightDiagonalX + c.bottomY;

    result[2] =
        new IntOctagon(
            tmp2,
            d.bottomY,
            tmp,
            c.bottomY,
            d.upperLeftDiagonalX,
            d.lowerRightDiagonalX,
            d.lowerLeftDiagonalX,
            d.upperRightDiagonalX);

    tmp2 = c.rightX - c.lowerRightDiagonalX;

    result[3] =
        new IntOctagon(
            tmp,
            d.bottomY,
            d.rightX,
            tmp2,
            c.lowerRightDiagonalX,
            d.lowerRightDiagonalX,
            d.lowerLeftDiagonalX,
            d.upperRightDiagonalX);

    tmp = c.upperRightDiagonalX - c.rightX;

    result[4] =
        new IntOctagon(
            c.rightX,
            tmp2,
            d.rightX,
            tmp,
            d.upperLeftDiagonalX,
            d.lowerRightDiagonalX,
            d.lowerLeftDiagonalX,
            d.upperRightDiagonalX);

    tmp2 = c.upperRightDiagonalX - c.topY;

    result[5] =
        new IntOctagon(
            tmp2,
            tmp,
            d.rightX,
            d.topY,
            d.upperLeftDiagonalX,
            d.lowerRightDiagonalX,
            c.upperRightDiagonalX,
            d.upperRightDiagonalX);

    tmp = c.upperLeftDiagonalX + c.topY;

    result[6] =
        new IntOctagon(
            tmp,
            c.topY,
            tmp2,
            d.topY,
            d.upperLeftDiagonalX,
            d.lowerRightDiagonalX,
            d.lowerLeftDiagonalX,
            d.upperRightDiagonalX);

    tmp2 = c.leftX - c.upperLeftDiagonalX;

    result[7] =
        new IntOctagon(
            d.leftX,
            tmp2,
            tmp,
            d.topY,
            d.upperLeftDiagonalX,
            c.upperLeftDiagonalX,
            d.lowerLeftDiagonalX,
            d.upperRightDiagonalX);

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
            > curr1.rightX - curr1.leftXValue(curr2.bottomY)) {
      // switch the vertical upper left divide line to horizontal
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
            > curr2.rightXValue(curr1.bottomY) - curr2.leftX) {
      // switch the vertical upper right divide line to horizontal
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
            > curr1.upperYValue(curr2.leftX) - curr2.topY) {
      // switch the horizontal upper right divide line to vertical
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
            > curr1.bottomY - curr2.lowerYValue(curr1.leftX)) {
      // switch the horizontal lower right divide line to vertical
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
            > curr1.rightXValue(curr2.topY) - curr2.rightX) {
      // switch the vertical lower right divide line to horizontal
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
            > curr1.leftX - curr2.leftXValue(curr1.topY)) {
      // switch the vertical lower left divide line to horizontal
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
            > curr2.bottomY - curr1.lowerYValue(curr2.rightX)) {
      // switch the horizontal lower left divide line to vertical
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
  Simplex[] cutoutFrom(Simplex simplex) {
    return this.toSimplex().cutoutFrom(simplex);
  }

  @Override
  public String toString() {
    return "IntOctagon(leftX="
        + leftX
        + ", bottomY="
        + bottomY
        + ", rightX="
        + rightX
        + ", topY="
        + topY
        + ", upperLeftDiagonalX="
        + upperLeftDiagonalX
        + ", lowerRightDiagonalX="
        + lowerRightDiagonalX
        + ", lowerLeftDiagonalX="
        + lowerLeftDiagonalX
        + ", upperRightDiagonalX="
        + upperRightDiagonalX
        + ")";
  }
}

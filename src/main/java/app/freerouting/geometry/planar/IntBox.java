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
  public IntBox(IntPoint pLl, IntPoint pUr) {
    ll = pLl;
    ur = pUr;
  }

  /** creates an IntBox from the coordinates of its lower left and upper right corners. */
  public IntBox(int pLlX, int pLlY, int pUrX, int pUrY) {
    ll = new IntPoint(pLlX, pLlY);
    ur = new IntPoint(pUrX, pUrY);
  }

  @Override
  public boolean isIntOctagon() {
    return true;
  }

  /** Returns true, if the box is empty */
  @Override
  public boolean isEmpty() {
    return ll.x > ur.x || ll.y > ur.y;
  }

  @Override
  public int borderLineCount() {
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
  public IntPoint corner(int pNo) {
    if (pNo == 0) {
      return ll;
    }
    if (pNo == 1) {
      return new IntPoint(ur.x, ll.y);
    }
    if (pNo == 2) {
      return ur;
    }
    if (pNo == 3) {
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
  public boolean containsInside(IntPoint pPoint) {
    return pPoint.x > this.ll.x
        && pPoint.x < this.ur.x
        && pPoint.y > this.ll.y
        && pPoint.y < this.ur.y;
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
  public FloatPoint nearestPoint(FloatPoint pFromPoint) {
    double x;
    if (pFromPoint.x <= ll.x) {
      x = ll.x;
    } else if (pFromPoint.x >= ur.x) {
      x = ur.x;
    } else {
      x = pFromPoint.x;
    }

    double y;
    if (pFromPoint.y <= ll.y) {
      y = ll.y;
    } else if (pFromPoint.y >= ur.y) {
      y = ur.y;
    } else {
      y = pFromPoint.y;
    }

    return new FloatPoint(x, y);
  }

  /**
   * Calculates the sorted p_max_result_points nearest points on the border of this box. p_point is
   * assumed to be located in the interior of this nox. The function is only implemented for
   * p_max_result_points {@literal <}= 2;
   */
  public IntPoint[] nearestBorderProjections(IntPoint pPoint, int pMaxResultPoints) {
    if (pMaxResultPoints <= 0) {
      return new IntPoint[0];
    }
    pMaxResultPoints = Math.min(pMaxResultPoints, 2);
    IntPoint[] result = new IntPoint[pMaxResultPoints];

    int lowerXDiff = pPoint.x - ll.x;
    int upperXDiff = ur.x - pPoint.x;
    int lowerYDiff = pPoint.y - ll.y;
    int upperYDiff = ur.y - pPoint.y;

    int minDiff;
    int secondMinDiff;

    int nearestProjectionX = pPoint.x;
    int nearestProjectionY = pPoint.y;
    int secondNearestProjectionX = pPoint.x;
    int secondNearestProjectionY = pPoint.y;
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
      nearestProjectionX = pPoint.x;
      nearestProjectionY = ll.y;
    } else if (lowerYDiff < secondMinDiff) {
      secondMinDiff = lowerYDiff;
      secondNearestProjectionX = pPoint.x;
      secondNearestProjectionY = ll.y;
    }
    if (upperYDiff < minDiff) {
      secondMinDiff = minDiff;
      minDiff = upperYDiff;
      secondNearestProjectionX = nearestProjectionX;
      secondNearestProjectionY = nearestProjectionY;
      nearestProjectionX = pPoint.x;
      nearestProjectionY = ur.y;
    } else if (upperYDiff < secondMinDiff) {
      secondMinDiff = upperYDiff;
      secondNearestProjectionX = pPoint.x;
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
  public double distance(FloatPoint pFromPoint) {
    return pFromPoint.distance(nearestPoint(pFromPoint));
  }

  /** Computes the weighted distance to the box p_other. */
  public double weightedDistance(IntBox pOther, double pHorizontalWeight, double pVerticalWeight) {
    double result;

    double maxLlX = Math.max(this.ll.x, pOther.ll.x);
    double maxLlY = Math.max(this.ll.y, pOther.ll.y);
    double minUrX = Math.min(this.ur.x, pOther.ur.x);
    double minUrY = Math.min(this.ur.y, pOther.ur.y);

    if (minUrX >= maxLlX) {
      result = Math.max(pVerticalWeight * (maxLlY - minUrY), 0);
    } else if (minUrY >= maxLlY) {
      result = Math.max(pHorizontalWeight * (maxLlX - minUrX), 0);
    } else {
      double deltaX = maxLlX - minUrX;
      double deltaY = maxLlY - minUrY;
      deltaX *= pHorizontalWeight;
      deltaY *= pVerticalWeight;
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
  public boolean cornerIsBounded(int pNo) {
    return true;
  }

  @Override
  public RegularTileShape union(RegularTileShape pOther) {
    return pOther.union(this);
  }

  @Override
  public IntBox union(IntBox pOther) {
    int llx = Math.min(ll.x, pOther.ll.x);
    int lly = Math.min(ll.y, pOther.ll.y);
    int urx = Math.max(ur.x, pOther.ur.x);
    int ury = Math.max(ur.y, pOther.ur.y);
    return new IntBox(llx, lly, urx, ury);
  }

  /** Returns the intersection of this box with an IntBox. */
  @Override
  public IntBox intersection(IntBox pOther) {
    if (pOther.ll.x > ur.x) {
      return EMPTY;
    }
    if (pOther.ll.y > ur.y) {
      return EMPTY;
    }
    if (ll.x > pOther.ur.x) {
      return EMPTY;
    }
    if (ll.y > pOther.ur.y) {
      return EMPTY;
    }
    int llx = Math.max(ll.x, pOther.ll.x);
    int urx = Math.min(ur.x, pOther.ur.x);
    int lly = Math.max(ll.y, pOther.ll.y);
    int ury = Math.min(ur.y, pOther.ur.y);
    return new IntBox(llx, lly, urx, ury);
  }

  /** returns the intersection of this box with a ConvexShape */
  @Override
  public TileShape intersection(TileShape pOther) {
    return pOther.intersection(this);
  }

  @Override
  IntOctagon intersection(IntOctagon pOther) {
    return pOther.intersection(this.toIntOctagon());
  }

  @Override
  Simplex intersection(Simplex pOther) {
    return pOther.intersection(this.toSimplex());
  }

  @Override
  public boolean intersects(Shape pOther) {
    return pOther.intersects(this);
  }

  @Override
  public boolean intersects(IntBox pOther) {
    if (pOther.ll.x > this.ur.x) {
      return false;
    }
    if (pOther.ll.y > this.ur.y) {
      return false;
    }
    if (this.ll.x > pOther.ur.x) {
      return false;
    }
    return this.ll.y <= pOther.ur.y;
  }

  /** Returns true, if this box intersects with p_other and the intersection is 2-dimensional. */
  public boolean overlaps(IntBox pOther) {
    if (pOther.ll.x >= this.ur.x) {
      return false;
    }
    if (pOther.ll.y >= this.ur.y) {
      return false;
    }
    if (this.ll.x >= pOther.ur.x) {
      return false;
    }
    return this.ll.y < pOther.ur.y;
  }

  @Override
  public boolean contains(RegularTileShape pOther) {
    return pOther.isContainedIn(this);
  }

  @Override
  public RegularTileShape boundingShape(ShapeBoundingDirections pDirs) {
    return pDirs.bounds(this);
  }

  /**
   * Enlarges the box by p_offset. Contrary to the offset() method the result is an IntOctagon, not
   * an IntBox.
   */
  @Override
  public IntOctagon enlarge(double pOffset) {
    return boundingOctagon().offset(pOffset);
  }

  @Override
  public IntBox translateBy(Vector pRelCoor) {
    // This function is at the moment only implemented for Vectors
    // with integer coordinates.
    // The general implementation is still missing.

    if (pRelCoor.equals(Vector.ZERO)) {
      return this;
    }
    IntPoint newLl = (IntPoint) ll.translateBy(pRelCoor);
    IntPoint newUr = (IntPoint) ur.translateBy(pRelCoor);
    return new IntBox(newLl, newUr);
  }

  @Override
  public IntBox turn90Degree(int pFactor, IntPoint pPole) {
    IntPoint p1 = (IntPoint) ll.turn90Degree(pFactor, pPole);
    IntPoint p2 = (IntPoint) ur.turn90Degree(pFactor, pPole);

    int llx = Math.min(p1.x, p2.x);
    int lly = Math.min(p1.y, p2.y);
    int urx = Math.max(p1.x, p2.x);
    int ury = Math.max(p1.y, p2.y);
    return new IntBox(llx, lly, urx, ury);
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
  public int borderLineIndex(Line pLine) {
    FRLogger.warn("edge_index_of_line not yet implemented for IntBoxes");
    return -1;
  }

  /**
   * Returns the box offsetted by p_dist. If p_dist {@literal >} 0, the offset is to the outside,
   * else to the inside.
   */
  @Override
  public IntBox offset(double pDist) {
    if (pDist == 0 || isEmpty()) {
      return this;
    }
    int dist = (int) Math.round(pDist);
    IntPoint lowerLeft = new IntPoint(ll.x - dist, ll.y - dist);
    IntPoint upperRight = new IntPoint(ur.x + dist, ur.y + dist);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Returns the box, where the horizontal boundary is offsetted by p_dist. If p_dist {@literal >}
   * 0, the offset is to the outside, else to the inside.
   */
  public IntBox horizontalOffset(double pDist) {
    if (pDist == 0 || isEmpty()) {
      return this;
    }
    int dist = (int) Math.round(pDist);
    IntPoint lowerLeft = new IntPoint(ll.x - dist, ll.y);
    IntPoint upperRight = new IntPoint(ur.x + dist, ur.y);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Returns the box, where the vertical boundary is offsetted by p_dist. If p_dist {@literal >} 0,
   * the offset is to the outside, else to the inside.
   */
  public IntBox verticalOffset(double pDist) {
    if (pDist == 0 || isEmpty()) {
      return this;
    }
    int dist = (int) Math.round(pDist);
    IntPoint lowerLeft = new IntPoint(ll.x, ll.y - dist);
    IntPoint upperRight = new IntPoint(ur.x, ur.y + dist);
    return new IntBox(lowerLeft, upperRight);
  }

  /**
   * Shrinks the width and height of the box by the input width. The box will not vanish completely.
   */
  public IntBox shrink(int pWidth) {
    int llX;
    int urX;
    if (2 * pWidth <= this.ur.x - this.ll.x) {
      llX = this.ll.x + pWidth;
      urX = this.ur.x - pWidth;
    } else {
      llX = (this.ll.x + this.ur.x) / 2;
      urX = llX;
    }
    int llY;
    int urY;
    if (2 * pWidth <= this.ur.y - this.ll.y) {
      llY = this.ll.y + pWidth;
      urY = this.ur.y - pWidth;
    } else {
      llY = (this.ll.y + this.ur.y) / 2;
      urY = llY;
    }
    return new IntBox(llX, llY, urX, urY);
  }

  @Override
  public Side compare(RegularTileShape pOther, int pEdgeNo) {
    Side result = pOther.compare(this, pEdgeNo);
    return result.negate();
  }

  @Override
  public Side compare(IntBox pOther, int pEdgeNo) {
    Side result;
    switch (pEdgeNo) {
      case 0 -> {
        // compare the lower edge line
        if (ll.y > pOther.ll.y) {
          result = Side.ON_THE_LEFT;
        } else if (ll.y < pOther.ll.y) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 1 -> {
        // compare the right edge line
        if (ur.x < pOther.ur.x) {
          result = Side.ON_THE_LEFT;
        } else if (ur.x > pOther.ur.x) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 2 -> {
        // compare the upper edge line
        if (ur.y < pOther.ur.y) {
          result = Side.ON_THE_LEFT;
        } else if (ur.y > pOther.ur.y) {
          result = Side.ON_THE_RIGHT;
        } else {
          result = Side.COLLINEAR;
        }
      }
      case 3 -> {
        // compare the left edge line
        if (ll.x > pOther.ll.x) {
          result = Side.ON_THE_LEFT;
        } else if (ll.x < pOther.ll.x) {
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
  public IntOctagon toIntOctagon() {
    return new IntOctagon(
        ll.x, ll.y, ur.x, ur.y, ll.x - ur.y, ur.x - ll.y, ll.x + ll.y, ur.x + ur.y);
  }

  /** Returns an object of class Simplex defining the same shape */
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
  public boolean isContainedIn(IntBox pOther) {
    if (isEmpty() || this == pOther) {
      return true;
    }
    return ll.x >= pOther.ll.x && ll.y >= pOther.ll.y && ur.x <= pOther.ur.x && ur.y <= pOther.ur.y;
  }

  /** Return true, if p_other is contained in the interior of this box. */
  public boolean containsInInterior(IntBox pOther) {
    if (pOther.isEmpty()) {
      return true;
    }
    return pOther.ll.x > ll.x && pOther.ll.y > ll.y && pOther.ur.x < ur.x && pOther.ur.y < ur.y;
  }

  /** Calculates the part of p_from_box, which has minimal distance to this box. */
  public IntBox nearestPart(IntBox pFromBox) {
    int llX;

    if (pFromBox.ll.x >= this.ll.x) {
      llX = pFromBox.ll.x;
    } else {
      llX = Math.min(pFromBox.ur.x, this.ll.x);
    }

    int urX;

    if (pFromBox.ur.x <= this.ur.x) {
      urX = pFromBox.ur.x;
    } else {
      urX = Math.max(pFromBox.ll.x, this.ur.x);
    }

    int llY;

    if (pFromBox.ll.y >= this.ll.y) {
      llY = pFromBox.ll.y;
    } else {
      llY = Math.min(pFromBox.ur.y, this.ll.y);
    }

    int urY;

    if (pFromBox.ur.y <= this.ur.y) {
      urY = pFromBox.ur.y;
    } else {
      urY = Math.max(pFromBox.ll.y, this.ur.y);
    }
    return new IntBox(llX, llY, urX, urY);
  }

  @Override
  public boolean isContainedIn(IntOctagon pOther) {
    return pOther.contains(toIntOctagon());
  }

  @Override
  public boolean intersects(IntOctagon pOther) {
    return pOther.intersects(toIntOctagon());
  }

  @Override
  public boolean intersects(Simplex pOther) {
    return pOther.intersects(toSimplex());
  }

  @Override
  public boolean intersects(Circle pOther) {
    return pOther.intersects(this);
  }

  @Override
  public IntOctagon union(IntOctagon pOther) {
    return pOther.union(toIntOctagon());
  }

  @Override
  public Side compare(IntOctagon pOther, int pEdgeNo) {
    return toIntOctagon().compare(pOther, pEdgeNo);
  }

  /**
   * Divides this box into sections with width and height at most p_max_section_width of about equal
   * size.
   */
  @Override
  public IntBox[] divideIntoSections(double pMaxSectionWidth) {
    if (pMaxSectionWidth <= 0) {
      return new IntBox[0];
    }
    double length = this.ur.x - this.ll.x;
    double height = this.ur.y - this.ll.y;
    int xCount = (int) Math.ceil(length / pMaxSectionWidth);
    int yCount = (int) Math.ceil(height / pMaxSectionWidth);
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
  public TileShape[] cutout(TileShape pShape) {
    TileShape[] tmpResult = pShape.cutoutFrom(this);
    TileShape[] result = new TileShape[tmpResult.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = tmpResult[i].simplify();
    }
    return result;
  }

  @Override
  IntBox[] cutoutFrom(IntBox pD) {
    IntBox c = this.intersection(pD);
    if (this.isEmpty() || c.dimension() < this.dimension()) {
      // there is only an overlap at the border
      IntBox[] result = new IntBox[1];
      result[0] = pD;
      return result;
    }

    IntBox[] result = new IntBox[4];

    result[0] = new IntBox(pD.ll.x, pD.ll.y, c.ur.x, c.ll.y);

    result[1] = new IntBox(pD.ll.x, c.ll.y, c.ll.x, pD.ur.y);

    result[2] = new IntBox(c.ur.x, pD.ll.y, pD.ur.x, c.ur.y);

    result[3] = new IntBox(c.ll.x, c.ur.y, pD.ur.x, pD.ur.y);

    // now the division will be optimised, so that the cumulative
    // circumference will be minimal.

    IntBox b;

    if (c.ll.x - pD.ll.x > c.ll.y - pD.ll.y) {
      // switch left dividing line to lower
      b = result[0];
      result[0] = new IntBox(c.ll.x, b.ll.y, b.ur.x, b.ur.y);
      b = result[1];
      result[1] = new IntBox(b.ll.x, pD.ll.y, b.ur.x, b.ur.y);
    }
    if (pD.ur.y - c.ur.y > c.ll.x - pD.ll.x) {
      // switch upper dividing line to the left
      b = result[1];
      result[1] = new IntBox(b.ll.x, b.ll.y, b.ur.x, c.ur.y);
      b = result[3];
      result[3] = new IntBox(pD.ll.x, b.ll.y, b.ur.x, b.ur.y);
    }
    if (pD.ur.x - c.ur.x > pD.ur.y - c.ur.y) {
      // switch right dividing line to upper
      b = result[2];
      result[2] = new IntBox(b.ll.x, b.ll.y, b.ur.x, pD.ur.y);
      b = result[3];
      result[3] = new IntBox(b.ll.x, b.ll.y, c.ur.x, b.ur.y);
    }
    if (c.ll.y - pD.ll.y > pD.ur.x - c.ur.x) {
      // switch lower dividing line to the left
      b = result[0];
      result[0] = new IntBox(b.ll.x, b.ll.y, pD.ur.x, b.ur.y);
      b = result[2];
      result[2] = new IntBox(b.ll.x, c.ll.y, b.ur.x, b.ur.y);
    }
    return result;
  }

  @Override
  Simplex[] cutoutFrom(Simplex pSimplex) {
    return this.toSimplex().cutoutFrom(pSimplex);
  }

  @Override
  IntOctagon[] cutoutFrom(IntOctagon pOct) {
    return this.toIntOctagon().cutoutFrom(pOct);
  }
}

package app.freerouting.geometry.planar;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Convex shape defined as intersection of half-planes. A half-plane is defined as the positive side
 * of a directed line.
 */
public class Simplex extends TileShape implements Serializable {

  /** Standard implementation for an empty Simplex. */
  public static final Simplex EMPTY = new Simplex(new Line[0]);

  private final Line[] arr;

  /** the following fields are for storing precalculated data */
  private transient Point[] precalculatedCorners;

  private transient FloatPoint[] precalculatedFloatCorners;
  private transient IntBox precalculatedBoundingBox;
  private transient IntOctagon precalculatedBoundingOctagon;

  /**
   * Constructs a Simplex from the directed lines in p_line_arr. The simplex will not be normalized.
   * To get a normalized simplex use TileShape.get_instance
   */
  public Simplex(Line[] pLineArr) {
    arr = pLineArr;
  }

  /** creates a Simplex as intersection of the halfplanes defined by an array of directed lines */
  public static Simplex getInstance(Line[] pLineArr) {
    if (pLineArr.length == 0) {
      return Simplex.EMPTY;
    }
    Line[] currArr = new Line[pLineArr.length];
    System.arraycopy(pLineArr, 0, currArr, 0, pLineArr.length);
    // sort the lines in ascending direction
    Arrays.sort(currArr);
    Simplex currSimplex = new Simplex(currArr);
    return currSimplex.removeRedundantLines();
  }

  /** Return true, if this simplex is empty */
  @Override
  public boolean isEmpty() {
    return arr.length == 0;
  }

  /**
   * Converts the physical instance of this shape to a simpler physical instance, if possible. (For
   * example a Simplex to an IntOctagon).
   */
  @Override
  public TileShape simplify() {
    TileShape result = this;
    if (this.isEmpty()) {
      result = Simplex.EMPTY;
    } else if (this.isIntBox()) {
      result = this.boundingBox();
    } else if (this.isIntOctagon()) {
      result = this.toIntOctagon();
    }
    return result;
  }

  @Override
  public int getIdNo() {
    int result = 0;
    for (Line curr : arr) {
      result = 31 * result + curr.getIdNo();
    }
    return result;
  }

  /**
   * Returns true, if the determinant of the direction of index p_no -1 and the direction of index
   * p_no is {@literal >} 0
   */
  @Override
  public boolean cornerIsBounded(int pNo) {
    int no;
    if (pNo < 0) {
      FRLogger.warn("corner: p_no is < 0");
      no = 0;
    } else if (pNo >= arr.length) {
      FRLogger.warn("corner: p_index must be less than arr.length - 1");
      no = arr.length - 1;
    } else {
      no = pNo;
    }
    if (arr.length == 1) {
      return false;
    }
    int prevNo;
    if (no == 0) {
      prevNo = arr.length - 1;
    } else {
      prevNo = no - 1;
    }
    IntVector prevDir = (IntVector) arr[prevNo].direction().getVector();
    IntVector currDir = (IntVector) arr[no].direction().getVector();
    return prevDir.determinant(currDir) > 0;
  }

  /** Returns true, if the shape of this simplex is contained in a sufficiently large box */
  @Override
  public boolean isBounded() {
    if (arr.length == 0) {
      return true;
    }
    if (arr.length < 3) {
      return false;
    }
    for (int i = 0; i < arr.length; i++) {
      if (!cornerIsBounded(i)) {
        return false;
      }
    }
    return true;
  }

  /** Returns the number of edge lines defining this simplex */
  @Override
  public int borderLineCount() {
    return arr.length;
  }

  /**
   * Returns the intersection of the p_no -1-th with the p_no-th line of this simplex. If the
   * simplex is not bounded at this corner, the coordinates of the result will be set to
   * Integer.MAX_VALUE.
   */
  @Override
  public Point corner(int pNo) {
    int no;
    if (pNo < 0) {
      FRLogger.warn("Simplex.corner: p_no is < 0");
      no = 0;
    } else if (pNo >= arr.length) {
      FRLogger.warn("Simplex.corner: p_no must be less than arr.length - 1");
      no = arr.length - 1;
    } else {
      no = pNo;
    }
    if (precalculatedCorners == null)
    // corner array is not yet allocated
    {
      precalculatedCorners = new Point[arr.length];
    }
    if (precalculatedCorners[no] == null)
    // corner is not yet calculated
    {
      Line prev;
      if (no == 0) {
        prev = arr[arr.length - 1];
      } else {
        prev = arr[no - 1];
      }
      precalculatedCorners[no] = arr[no].intersection(prev);
    }
    return precalculatedCorners[no];
  }

  /**
   * Returns an approximation of the intersection of the p_no -1-th with the p_no-th line of this
   * simplex by a FloatPoint. If the simplex is not bounded at this corner, the coordinates of the
   * result will be set to Integer.MAX_VALUE.
   */
  @Override
  public FloatPoint cornerApprox(int pNo) {
    if (arr.length == 0) {
      return null;
    }
    int no;
    if (pNo < 0) {
      FRLogger.warn("Simplex.corner_approx: p_no is < 0");
      no = 0;
    } else if (pNo >= arr.length) {
      FRLogger.warn("Simplex.corner_approx: p_no must be less than arr.length - 1");
      no = arr.length - 1;
    } else {
      no = pNo;
    }
    if (precalculatedFloatCorners == null)
    // corner array is not yet allocated
    {
      precalculatedFloatCorners = new FloatPoint[arr.length];
    }
    if (precalculatedFloatCorners[no] == null)
    // corner is not yet calculated
    {
      Line prev;
      if (no == 0) {
        prev = arr[arr.length - 1];
      } else {
        prev = arr[no - 1];
      }
      precalculatedFloatCorners[no] = arr[no].intersectionApprox(prev);
    }
    return precalculatedFloatCorners[no];
  }

  @Override
  public FloatPoint[] cornerApproxArr() {
    if (precalculatedFloatCorners == null)
    // corner array is not yet allocated
    {
      precalculatedFloatCorners = new FloatPoint[arr.length];
    }
    for (int i = 0; i < precalculatedFloatCorners.length; i++) {
      if (precalculatedFloatCorners[i] == null)
      // corner is not yet calculated
      {
        Line prev;
        if (i == 0) {
          prev = arr[arr.length - 1];
        } else {
          prev = arr[i - 1];
        }
        precalculatedFloatCorners[i] = arr[i].intersectionApprox(prev);
      }
    }
    return precalculatedFloatCorners;
  }

  /**
   * returns the p_no-th edge line of this simplex. The edge lines are sorted in ascending
   * direction.
   */
  @Override
  public Line borderLine(int pNo) {
    if (arr.length == 0) {
      FRLogger.warn("Simplex.edge_line : simplex is empty");
      return null;
    }
    int no;
    if (pNo < 0) {
      FRLogger.warn("Simplex.edge_line : p_no is < 0");
      no = 0;
    } else if (pNo >= arr.length) {
      FRLogger.warn("Simplex.edge_line: p_no must be less than arr.length - 1");
      no = arr.length - 1;
    } else {
      no = pNo;
    }
    return arr[no];
  }

  /**
   * Returns the dimension of this simplex. The result may be 2, 1, 0, or -1 (if the simplex is
   * empty).
   */
  @Override
  public int dimension() {
    if (arr.length == 0) {
      return -1;
    }
    if (arr.length > 4) {
      return 2;
    }
    if (arr.length == 1) {
      // we have a half plane
      return 2;
    }
    if (arr.length == 2) {
      if (arr[0].overlaps(arr[1])) {
        return 1;
      }
      return 2;
    }
    if (arr.length == 3) {
      if (arr[0].overlaps(arr[1]) || arr[0].overlaps(arr[2]) || arr[1].overlaps(arr[2])) {
        // simplex is 1 dimensional and unbounded at one side
        return 1;
      }
      Point intersection = arr[1].intersection(arr[2]);
      Side sideOfLine0 = arr[0].sideOf(intersection);
      if (sideOfLine0 == Side.ON_THE_RIGHT) {
        return 2;
      }
      if (sideOfLine0 == Side.ON_THE_LEFT) {
        FRLogger.debug("empty Simplex not normalized");
        return -1;
      }
      // now the 3 lines intersect in the same point
      return 0;
    }
    // now the simplex has 4 edge lines
    // check if opposing lines are collinear
    boolean collinear02 = arr[0].overlaps(arr[2]);
    boolean collinear13 = arr[1].overlaps(arr[3]);
    if (collinear02 && collinear13) {
      return 0;
    }
    if (collinear02 || collinear13) {
      return 1;
    }
    return 2;
  }

  @Override
  public double maxWidth() {
    if (!this.isBounded()) {
      return Integer.MAX_VALUE;
    }
    double maxDistance = Integer.MIN_VALUE;
    double maxDistance2 = Integer.MIN_VALUE;
    FloatPoint gravityPoint = this.centreOfGravity();

    for (int i = 0; i < borderLineCount(); i++) {
      double currDistance = Math.abs(arr[i].signedDistance(gravityPoint));

      if (currDistance > maxDistance) {
        maxDistance2 = maxDistance;
        maxDistance = currDistance;
      } else if (currDistance > maxDistance2) {
        maxDistance2 = currDistance;
      }
    }
    return maxDistance + maxDistance2;
  }

  @Override
  public double minWidth() {
    if (!this.isBounded()) {
      return Integer.MAX_VALUE;
    }
    double minDistance = Integer.MAX_VALUE;
    double minDistance2 = Integer.MAX_VALUE;
    FloatPoint gravityPoint = this.centreOfGravity();

    for (int i = 0; i < borderLineCount(); i++) {
      double currDistance = Math.abs(arr[i].signedDistance(gravityPoint));

      if (currDistance < minDistance) {
        minDistance2 = minDistance;
        minDistance = currDistance;
      } else if (currDistance < minDistance2) {
        minDistance2 = currDistance;
      }
    }
    return minDistance + minDistance2;
  }

  /** checks if this simplex can be converted into an IntBox */
  @Override
  public boolean isIntBox() {
    for (int i = 0; i < arr.length; i++) {
      Line currLine = arr[i];
      if (!(currLine.a instanceof IntPoint && currLine.b instanceof IntPoint)) {
        return false;
      }
      if (!currLine.isOrthogonal()) {
        return false;
      }
      if (!cornerIsBounded(i)) {
        return false;
      }
    }
    return true;
  }

  /** checks if this simplex can be converted into an IntOctagon */
  @Override
  public boolean isIntOctagon() {
    for (int i = 0; i < arr.length; i++) {
      Line currLine = arr[i];
      if (!(currLine.a instanceof IntPoint && currLine.b instanceof IntPoint)) {
        return false;
      }
      if (!currLine.isMultipleOf45Degree()) {
        return false;
      }
      if (!cornerIsBounded(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Converts this IntSimplex to an IntOctagon. Returns null, if that is not possible, because not
   * all lines of this IntSimplex are 45 degree
   */
  public IntOctagon toIntOctagon() {
    // this function is at the moment only implemented for lines
    // consisting of IntPoints.
    // The general implementation is still missing.
    if (!isIntOctagon()) {
      return null;
    }
    if (isEmpty()) {
      return IntOctagon.EMPTY;
    }

    // initialise to the biggest octagon values

    int rx = Limits.CRIT_INT;
    int uy = Limits.CRIT_INT;
    int lrx = Limits.CRIT_INT;
    int urx = Limits.CRIT_INT;
    int lx = -Limits.CRIT_INT;
    int ly = -Limits.CRIT_INT;
    int llx = -Limits.CRIT_INT;
    int ulx = -Limits.CRIT_INT;
    for (int i = 0; i < arr.length; i++) {
      Line currLine = arr[i];
      IntPoint a = (IntPoint) currLine.a;
      IntPoint b = (IntPoint) currLine.b;
      if (a.y == b.y) {
        if (b.x >= a.x) {
          // lower boundary line
          ly = a.y;
        }
        if (b.x <= a.x) {
          // upper boundary line
          uy = a.y;
        }
      }
      if (a.x == b.x) {
        if (b.y >= a.y) {
          // right boundary line
          rx = a.x;
        }
        if (b.y <= a.y) {
          // left boundary line
          lx = a.x;
        }
      }
      if (a.y < b.y) {
        if (a.x < b.x) {
          // lower right boundary line
          lrx = a.x - a.y;
        } else if (a.x > b.x) {
          // upper right boundary line
          urx = a.x + a.y;
        }
      } else if (a.y > b.y) {
        if (a.x < b.x) {
          // lower left boundary line
          llx = a.x + a.y;
        } else if (a.x > b.x) {
          // upper left boundary line
          ulx = a.x - a.y;
        }
      }
    }
    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
  }

  /** Returns the simplex, which results from translating the lines of this simplex by p_vector */
  @Override
  public Simplex translateBy(Vector pVector) {
    if (pVector.equals(Vector.ZERO)) {
      return this;
    }
    Line[] newArr = new Line[arr.length];
    for (int i = 0; i < arr.length; i++) {
      newArr[i] = arr[i].translateBy(pVector);
    }
    return new Simplex(newArr);
  }

  /**
   * Returns the smallest box with int coordinates containing all corners of this simplex. The
   * coordinates of the result will be Integer.MAX_VALUE, if the simplex is not bounded
   */
  @Override
  public IntBox boundingBox() {
    if (arr.length == 0) {
      return IntBox.EMPTY;
    }
    if (precalculatedBoundingBox == null) {
      double llx = Integer.MAX_VALUE;
      double lly = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;
      double ury = Integer.MIN_VALUE;
      for (int i = 0; i < arr.length; i++) {
        FloatPoint curr = cornerApprox(i);
        llx = Math.min(llx, curr.x);
        lly = Math.min(lly, curr.y);
        urx = Math.max(urx, curr.x);
        ury = Math.max(ury, curr.y);
      }
      IntPoint lowerLeft = new IntPoint((int) Math.floor(llx), (int) Math.floor(lly));
      IntPoint upperRight = new IntPoint((int) Math.ceil(urx), (int) Math.ceil(ury));
      precalculatedBoundingBox = new IntBox(lowerLeft, upperRight);
    }
    return precalculatedBoundingBox;
  }

  /** Calculates a bounding octagon of the Simplex. Returns null, if the Simplex is not bounded. */
  @Override
  public IntOctagon boundingOctagon() {
    if (precalculatedBoundingOctagon == null) {
      double lx = Integer.MAX_VALUE;
      double ly = Integer.MAX_VALUE;
      double rx = Integer.MIN_VALUE;
      double uy = Integer.MIN_VALUE;
      double ulx = Integer.MAX_VALUE;
      double lrx = Integer.MIN_VALUE;
      double llx = Integer.MAX_VALUE;
      double urx = Integer.MIN_VALUE;
      for (int i = 0; i < arr.length; i++) {
        FloatPoint curr = cornerApprox(i);
        lx = Math.min(lx, curr.x);
        ly = Math.min(ly, curr.y);
        rx = Math.max(rx, curr.x);
        uy = Math.max(uy, curr.y);

        double tmp = curr.x - curr.y;
        ulx = Math.min(ulx, tmp);
        lrx = Math.max(lrx, tmp);

        tmp = curr.x + curr.y;
        llx = Math.min(llx, tmp);
        urx = Math.max(urx, tmp);
      }
      if (Math.min(lx, ly) < -Limits.CRIT_INT
          || Math.max(rx, uy) > Limits.CRIT_INT
          || Math.min(ulx, llx) < -Limits.CRIT_INT
          || Math.max(lrx, urx) > Limits.CRIT_INT)
      // result is not bounded
      {
        return null;
      }
      precalculatedBoundingOctagon =
          new IntOctagon(
              (int) Math.floor(lx),
              (int) Math.floor(ly),
              (int) Math.ceil(rx),
              (int) Math.ceil(uy),
              (int) Math.floor(ulx),
              (int) Math.ceil(lrx),
              (int) Math.floor(llx),
              (int) Math.ceil(urx));
    }
    return precalculatedBoundingOctagon;
  }

  @Override
  public Simplex boundingTile() {
    return this;
  }

  @Override
  public RegularTileShape boundingShape(ShapeBoundingDirections pDirs) {
    return pDirs.bounds(this);
  }

  /**
   * Returns the simplex offsetted by p_with. If p_width {@literal >} 0, the offset is to the outer,
   * else to the inner.
   */
  @Override
  public Simplex offset(double pWidth) {
    if (pWidth == 0) {
      return this;
    }
    Line[] newArr = new Line[arr.length];
    for (int i = 0; i < arr.length; i++) {
      newArr[i] = arr[i].translate(-pWidth);
    }
    Simplex offsetSimplex = new Simplex(newArr);
    if (pWidth < 0) {
      offsetSimplex = offsetSimplex.removeRedundantLines();
    }
    return offsetSimplex;
  }

  /**
   * Returns this simplex enlarged by p_offset. The result simplex is intersected with the by
   * p_offset enlarged bounding octagon of this simplex
   */
  @Override
  public Simplex enlarge(double pOffset) {
    if (pOffset == 0) {
      return this;
    }
    Simplex offsetSimplex = offset(pOffset);
    IntOctagon boundingOct = this.boundingOctagon();
    if (boundingOct == null) {
      return Simplex.EMPTY;
    }
    IntOctagon offsetOct = boundingOct.offset(pOffset);
    return offsetSimplex.intersection(offsetOct.toSimplex());
  }

  /**
   * Returns the number of the rightmost corner seen from p_from_point No other point of this
   * simplex may be to the right of the line from p_from_point to the result corner.
   */
  public int indexOfRightMostCorner(Point pFromPoint) {
    Point pole = pFromPoint;
    Point rightMostCorner = corner(0);
    int result = 0;
    for (int i = 1; i < arr.length; i++) {
      Point currCorner = corner(i);
      if (currCorner.sideOf(pole, rightMostCorner) == Side.ON_THE_RIGHT) {
        rightMostCorner = currCorner;
        result = i;
      }
    }
    return result;
  }

  /** Returns the intersection of p_box with this simplex */
  @Override
  public Simplex intersection(IntBox pBox) {
    return intersection(pBox.toSimplex());
  }

  /** Returns the intersection of this simplex and p_other */
  @Override
  public Simplex intersection(Simplex pOther) {
    if (this.isEmpty() || pOther.isEmpty()) {
      return EMPTY;
    }
    Line[] newArr = new Line[arr.length + pOther.arr.length];
    System.arraycopy(arr, 0, newArr, 0, arr.length);
    System.arraycopy(pOther.arr, 0, newArr, arr.length, pOther.arr.length);
    Arrays.sort(newArr);
    Simplex result = new Simplex(newArr);
    return result.removeRedundantLines();
  }

  /** Returns the intersection of this simplex and the shape p_other */
  @Override
  public TileShape intersection(TileShape pOther) {
    return pOther.intersection(this);
  }

  @Override
  public boolean intersects(Shape pOther) {
    return pOther.intersects(this);
  }

  @Override
  public boolean intersects(Simplex pOther) {
    ConvexShape is = intersection(pOther);
    return !is.isEmpty();
  }

  /** if p_line is a borderline of this simplex the number of that edge is returned, otherwise -1 */
  @Override
  public int borderLineIndex(Line pLine) {
    for (int i = 0; i < arr.length; i++) {
      if (pLine.equals(arr[i])) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Enlarges the simplex by removing the edge line with index p_no. The result simplex may get
   * unbounded.
   */
  public Simplex removeBorderLine(int pNo) {
    if (pNo < 0 || pNo >= arr.length) {
      return this;
    }
    Line[] newArr = new Line[this.arr.length - 1];
    System.arraycopy(this.arr, 0, newArr, 0, pNo);
    System.arraycopy(this.arr, pNo + 1, newArr, pNo, newArr.length - pNo);
    return new Simplex(newArr);
  }

  @Override
  public Simplex toSimplex() {
    return this;
  }

  @Override
  Simplex intersection(IntOctagon pOther) {
    return intersection(pOther.toSimplex());
  }

  @Override
  public TileShape[] cutout(TileShape pShape) {
    return pShape.cutoutFrom(this);
  }

  /**
   * cuts this simplex out of p_outer_simplex. Divides the resulting shape into simplices along the
   * minimal distance lines from the vertices of the inner simplex to the outer simplex; Returns the
   * convex pieces constructed by this division.
   */
  @Override
  public Simplex[] cutoutFrom(Simplex pOuterSimplex) {
    if (this.dimension() < 2) {
      FRLogger.warn("Simplex.cutout_from only implemented for 2-dim simplex");
      return null;
    }
    Simplex innerSimplex = this.intersection(pOuterSimplex);
    if (innerSimplex.dimension() < 2) {
      // nothing to cutout from p_outer_simplex
      Simplex[] result = new Simplex[1];
      result[0] = pOuterSimplex;
      return result;
    }
    int innerCornerCount = innerSimplex.arr.length;
    Line[][] divisionLineArr = new Line[innerCornerCount][];
    for (int inner_corner_no = 0; inner_corner_no < innerCornerCount; inner_corner_no++) {
      divisionLineArr[inner_corner_no] =
          innerSimplex.calcDivisionLines(inner_corner_no, pOuterSimplex);
      if (divisionLineArr[inner_corner_no] == null) {
        FRLogger.warn("Simplex.cutout_from: division line is null");
        Simplex[] result = new Simplex[1];
        result[0] = pOuterSimplex;
        return result;
      }
    }
    boolean checkCrossFirstLine = false;
    Line prevDivisionLine = null;
    Line firstDivisionLine = divisionLineArr[0][0];
    IntDirection firstDirection = (IntDirection) firstDivisionLine.direction();
    Collection<Simplex> resultList = new LinkedList<>();

    for (int inner_corner_no = 0; inner_corner_no < innerCornerCount; inner_corner_no++) {
      Line nextDivisionLine;
      if (inner_corner_no == innerSimplex.arr.length - 1) {
        nextDivisionLine = divisionLineArr[0][0];
      } else {
        nextDivisionLine = divisionLineArr[inner_corner_no + 1][0];
      }
      Line[] currDivisionLines = divisionLineArr[inner_corner_no];
      if (currDivisionLines.length == 2) {
        // 2 division lines are necessary (sharp corner).
        // Construct an unbounded simplex from
        // currDivisionLines[1] and currDivisionLines[0]
        // and intersect it with the outer simplex
        IntDirection currDir = (IntDirection) currDivisionLines[0].direction();
        boolean mergePrevDivisionLine = false;
        boolean mergeFirstDivisionLine = false;
        if (prevDivisionLine != null) {
          IntDirection prevDir = (IntDirection) prevDivisionLine.direction();
          if (currDir.determinant(prevDir) > 0) {

            // the previous division line may intersect
            //  currDivisionLines[0] inside p_divide_simplex
            mergePrevDivisionLine = true;
          }
        }
        if (!checkCrossFirstLine) {
          checkCrossFirstLine = inner_corner_no > 0 && currDir.determinant(firstDirection) > 0;
        }
        if (checkCrossFirstLine) {
          IntDirection currDir2 = (IntDirection) currDivisionLines[1].direction();
          if (currDir2.determinant(firstDirection) < 0) {
            // The current piece has an intersection area with the first
            // piece.
            // Add a line to tmpPolyline to prevent this
            mergeFirstDivisionLine = true;
          }
        }
        int pieceLineCount = 2;
        if (mergePrevDivisionLine) {
          ++pieceLineCount;
        }
        if (mergeFirstDivisionLine) {
          ++pieceLineCount;
        }
        Line[] pieceLines = new Line[pieceLineCount];
        pieceLines[0] = new Line(currDivisionLines[1].b, currDivisionLines[1].a);
        pieceLines[1] = currDivisionLines[0];
        int currLineNo = 1;
        if (mergePrevDivisionLine) {
          ++currLineNo;
          pieceLines[currLineNo] = prevDivisionLine;
        }
        if (mergeFirstDivisionLine) {
          ++currLineNo;
          pieceLines[currLineNo] = new Line(firstDivisionLine.b, firstDivisionLine.a);
        }
        Simplex currPiece = new Simplex(pieceLines);
        resultList.add(currPiece.intersection(pOuterSimplex));
      }
      // construct an unbounded simplex from nextDivisionLine,
      // innerSimplex.line [inner_corner_no] and the last current division line
      // and intersect it with the outer simplex
      boolean mergeNextDivisionLine = !nextDivisionLine.b.equals(nextDivisionLine.a);
      Line lastCurrDivisionLine = currDivisionLines[currDivisionLines.length - 1];
      IntDirection lastCurrDir = (IntDirection) lastCurrDivisionLine.direction();
      boolean mergeLastCurrDivisionLine = !lastCurrDivisionLine.b.equals(lastCurrDivisionLine.a);
      boolean mergePrevDivisionLine = false;
      boolean mergeFirstDivisionLine = false;
      if (prevDivisionLine != null) {
        IntDirection prevDir = (IntDirection) prevDivisionLine.direction();
        if (lastCurrDir.determinant(prevDir) > 0) {

          // the previous division line may intersect
          //  the last current division line inside p_divide_simplex
          mergePrevDivisionLine = true;
        }
      }
      if (!checkCrossFirstLine) {
        checkCrossFirstLine =
            inner_corner_no > 0
                && lastCurrDir.determinant(firstDirection) > 0
                && lastCurrDir.getVector().scalarProduct(firstDirection.getVector()) < 0;
        // scalarProduct checked to ignore backcrossing at
        // small inner_corner_no
      }
      if (checkCrossFirstLine) {
        IntDirection nextDir = (IntDirection) nextDivisionLine.direction();
        if (nextDir.determinant(firstDirection) < 0) {
          // The current piece has an intersection area with the first piece.
          // Add a line to tmpPolyline to prevent this
          mergeFirstDivisionLine = true;
        }
      }
      int pieceLineCount = 1;
      if (mergeNextDivisionLine) {
        ++pieceLineCount;
      }
      if (mergeLastCurrDivisionLine) {
        ++pieceLineCount;
      }
      if (mergePrevDivisionLine) {
        ++pieceLineCount;
      }
      if (mergeFirstDivisionLine) {
        ++pieceLineCount;
      }
      Line[] pieceLines = new Line[pieceLineCount];
      Line currLine = innerSimplex.arr[inner_corner_no];
      pieceLines[0] = new Line(currLine.b, currLine.a);
      int currLineNo = 0;
      if (mergeNextDivisionLine) {
        ++currLineNo;
        pieceLines[currLineNo] = new Line(nextDivisionLine.b, nextDivisionLine.a);
      }
      if (mergeLastCurrDivisionLine) {
        ++currLineNo;
        pieceLines[currLineNo] = lastCurrDivisionLine;
      }
      if (mergePrevDivisionLine) {
        ++currLineNo;
        pieceLines[currLineNo] = prevDivisionLine;
      }
      if (mergeFirstDivisionLine) {
        ++currLineNo;
        pieceLines[currLineNo] = new Line(firstDivisionLine.b, firstDivisionLine.a);
      }
      Simplex currPiece = new Simplex(pieceLines);
      resultList.add(currPiece.intersection(pOuterSimplex));
      nextDivisionLine = prevDivisionLine;
    }
    Simplex[] result = new Simplex[resultList.size()];
    Iterator<Simplex> it = resultList.iterator();
    for (int i = 0; i < result.length; i++) {
      result[i] = it.next();
    }
    return result;
  }

  @Override
  Simplex[] cutoutFrom(IntOctagon pOct) {
    return cutoutFrom(pOct.toSimplex());
  }

  @Override
  Simplex[] cutoutFrom(IntBox pBox) {
    return cutoutFrom(pBox.toSimplex());
  }

  /**
   * Removes lines, which are redundant in the definition of the shape of this simplex. Assumes that
   * the lines of this simplex are sorted.
   */
  Simplex removeRedundantLines() {
    Line[] lineArr = new Line[arr.length];
    // copy the sorted lines of arr into lineArr while skipping
    // multiple lines
    int newLength = 1;
    lineArr[0] = arr[0];
    Line prev = lineArr[0];
    for (int i = 1; i < arr.length; i++) {
      if (!arr[i].fastEquals(prev)) {
        lineArr[newLength] = arr[i];
        prev = lineArr[newLength];
        ++newLength;
      }
    }

    Side[] intersectionSides = new Side[newLength];
    // precalculated array , on which side of this line the previous and the
    // next line do intersect

    boolean tryAgain = newLength > 2;
    int indexOfLastRemovedLine = newLength;
    while (tryAgain) {
      tryAgain = false;
      int prevInd = newLength - 1;
      int nextInd;
      Line prevLine = lineArr[prevInd];
      Line currLine = lineArr[0];
      Line nextLine;
      for (int ind = 0; ind < newLength; ind++) {
        if (ind == newLength - 1) {
          nextInd = 0;
        } else {
          nextInd = ind + 1;
        }
        nextLine = lineArr[nextInd];

        boolean removeLine = false;
        IntDirection prevDir = (IntDirection) prevLine.direction();
        IntDirection nextDir = (IntDirection) nextLine.direction();
        double det = prevDir.determinant(nextDir);
        if (det != 0) // prevLine and nextLine are not parallel
        {
          if (intersectionSides[ind] == null) {
            // intersectionSides [ind] not precalculated
            intersectionSides[ind] = currLine.sideOfIntersection(prevLine, nextLine);
          }
          if (det > 0)
          // direction of nextLine is bigger than direction of prevLine
          {
            // if the intersection of prevLine and nextLine
            // is on the left of currLine, currLine does not
            // contribute to the shape of the simplex
            removeLine = intersectionSides[ind] != Side.ON_THE_LEFT;
          } else
          // direction of nextLine is smaller than direction of prevLine
          {

            if (intersectionSides[ind] == Side.ON_THE_LEFT) {
              IntDirection currDir = (IntDirection) currLine.direction();
              if (prevDir.determinant(currDir) > 0)
              // direction of currLine is bigger than direction of prevLine
              {
                // the halfplane defined by currLine does not intersect
                // with the simplex defined by prevLine and nex_line,
                // hence this simplex must be empty
                newLength = 0;
                tryAgain = false;
                break;
              }
            }
          }
        } else // prevLine and nextLine are parallel
        {
          if (prevLine.sideOf(nextLine.a) == Side.ON_THE_LEFT)
          // prevLine is to the left of nextLine,
          // the halfplanes defined by prevLine and nextLine
          // do not intersect
          {
            newLength = 0;
            tryAgain = false;
            break;
          }
        }
        if (removeLine) {
          tryAgain = true;
          --newLength;
          for (int i = ind; i < newLength; i++) {
            lineArr[i] = lineArr[i + 1];
            intersectionSides[i] = intersectionSides[i + 1];
          }

          if (newLength < 3) {
            tryAgain = false;
            break;
          }
          // reset 3 precalculated intersectionSides
          if (ind == 0) {
            prevInd = newLength - 1;
          }
          intersectionSides[prevInd] = null;
          if (ind >= newLength) {
            nextInd = 0;
          } else {
            nextInd = ind;
          }
          intersectionSides[nextInd] = null;
          --ind;
          indexOfLastRemovedLine = ind;
        } else {
          prevLine = currLine;
          prevInd = ind;
        }
        currLine = nextLine;
        if (!tryAgain && ind >= indexOfLastRemovedLine)
        // tried all lines without removing one
        {
          break;
        }
      }
    }

    if (newLength == 2) {
      if (lineArr[0].isParallel(lineArr[1])) {
        if (lineArr[0].direction().equals(lineArr[1].direction()))
        // one of the two remaining lines is redundant
        {
          if (lineArr[1].sideOf(lineArr[0].a) == Side.ON_THE_LEFT) {
            lineArr[0] = lineArr[1];
          }
          --newLength;
        } else
        // the two remaining lines have opposite direction
        // the simplex may be empty
        {
          if (lineArr[1].sideOf(lineArr[0].a) == Side.ON_THE_LEFT) {
            newLength = 0;
          }
        }
      }
    }
    if (newLength == arr.length) {
      return this; // nothing removed
    }
    if (newLength == 0) {
      return Simplex.EMPTY;
    }
    Line[] result = new Line[newLength];
    System.arraycopy(lineArr, 0, result, 0, newLength);
    return new Simplex(result);
  }

  @Override
  public boolean intersects(IntBox pBox) {
    return intersects(pBox.toSimplex());
  }

  @Override
  public boolean intersects(IntOctagon pOctagon) {
    return intersects(pOctagon.toSimplex());
  }

  @Override
  public boolean intersects(Circle pCircle) {
    return pCircle.intersects(this);
  }

  /**
   * For each corner of this inner simplex 1 or 2 perpendicular projections onto lines of the outer
   * simplex are constructed, so that the resulting pieces after cutting out the inner simplex are
   * convex. 2 projections may be necessary at sharp angle corners. Used in the method cutout_from
   * with parametertype Simplex.
   */
  private Line[] calcDivisionLines(int pInnerCornerNo, Simplex pOuterSimplex) {
    Line currInnerLine = this.arr[pInnerCornerNo];
    Line prevInnerLine;
    if (pInnerCornerNo != 0) {
      prevInnerLine = this.arr[pInnerCornerNo - 1];
    } else {
      prevInnerLine = this.arr[arr.length - 1];
    }
    FloatPoint intersection = currInnerLine.intersectionApprox(prevInnerLine);
    if (intersection.x >= Integer.MAX_VALUE) {
      FRLogger.warn("Simplex.calc_division_lines: intersection expected");
      return null;
    }
    IntPoint innerCorner = intersection.round();
    double cTolerance = 0.0001;
    boolean isExact =
        Math.abs(innerCorner.x - intersection.x) < cTolerance
            && Math.abs(innerCorner.y - intersection.y) < cTolerance;

    if (!isExact) {
      // it is assumed, that the corners of the original inner simplex are
      // exact and the not exact corners come from the intersection of
      // the inner simplex with the outer simplex.
      // Because these corners lie on the border of the outer simplex,
      // no division is necessary
      Line[] result = new Line[1];
      result[0] = prevInnerLine;
      return result;
    }
    IntDirection firstProjectionDir = Direction.NULL;
    IntDirection secondProjectionDir = Direction.NULL;
    IntDirection prevInnerDir = (IntDirection) prevInnerLine.direction().opposite();
    IntDirection nextInnerDir = (IntDirection) currInnerLine.direction();
    int outerLineNo = 0;

    // search the first outer line, so that
    // the perpendicular projection of the inner corner onto this
    // line is visible from innerCorner to the left of prevInnerLine.

    double minDistance = Integer.MAX_VALUE;

    for (int ind = 0; ind < pOuterSimplex.arr.length; ind++) {
      Line outerLine = pOuterSimplex.arr[outerLineNo];
      IntDirection currProjectionDir = (IntDirection) innerCorner.perpendicularDirection(outerLine);
      if (currProjectionDir == Direction.NULL) {
        Line[] result = new Line[1];
        result[0] = new Line(innerCorner, innerCorner);
        return result;
      }
      boolean projectionVisible = prevInnerDir.determinant(currProjectionDir) >= 0;
      if (projectionVisible) {
        double currDistance = Math.abs(outerLine.signedDistance(innerCorner.toFloat()));
        boolean secondDivisionNecessary = currProjectionDir.determinant(nextInnerDir) < 0;
        // may occur at a sharp angle
        IntDirection currSecondProjectionDir = currProjectionDir;

        if (secondDivisionNecessary) {
          // search the first projection_dir between currProjectionDir
          // and nextInnerDir, that is visible from next_inner_line
          boolean secondProjectionVisible = false;
          int tmpOuterLineNo = outerLineNo;
          while (!secondProjectionVisible) {
            if (tmpOuterLineNo == pOuterSimplex.arr.length - 1) {
              tmpOuterLineNo = 0;
            } else {
              ++tmpOuterLineNo;
            }
            currSecondProjectionDir =
                (IntDirection)
                    innerCorner.perpendicularDirection(pOuterSimplex.arr[tmpOuterLineNo]);

            if (currSecondProjectionDir == Direction.NULL)
            // inner corner is on outerLine
            {
              Line[] result = new Line[1];
              result[0] = new Line(innerCorner, innerCorner);
              return result;
            }
            if (currProjectionDir.determinant(currSecondProjectionDir) < 0) {
              // currSecondProjectionDir not found;
              // the angle between currProjectionDir and
              // currSecondProjectionDir would be already bigger
              // than 180 degree
              currDistance = Integer.MAX_VALUE;
              break;
            }

            secondProjectionVisible = currSecondProjectionDir.determinant(nextInnerDir) >= 0;
          }
          currDistance +=
              Math.abs(pOuterSimplex.arr[tmpOuterLineNo].signedDistance(innerCorner.toFloat()));
        }
        if (currDistance < minDistance) {
          minDistance = currDistance;
          firstProjectionDir = currProjectionDir;
          secondProjectionDir = currSecondProjectionDir;
        }
      }
      if (outerLineNo == pOuterSimplex.arr.length - 1) {
        outerLineNo = 0;
      } else {
        ++outerLineNo;
      }
    }
    if (minDistance == Integer.MAX_VALUE) {
      FRLogger.warn("Simplex.calc_division_lines: division not found");
      return null;
    }
    Line[] result;
    if (firstProjectionDir.equals(secondProjectionDir)) {
      result = new Line[1];
      result[0] = new Line(innerCorner, firstProjectionDir);
    } else {
      result = new Line[2];
      result[0] = new Line(innerCorner, firstProjectionDir);
      result[1] = new Line(innerCorner, secondProjectionDir);
    }
    return result;
  }
}

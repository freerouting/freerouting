package app.freerouting.geometry.planar;

import app.freerouting.datastructures.Stoppable;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A PolylineArea is an Area, where the outside border curve and the hole borders consist of
 * straight lines.
 */
public class PolylineArea implements Area, Serializable {

  final PolylineShape borderShape;
  final PolylineShape[] holeArr;
  private transient TileShape[] precalculatedConvexPieces;

  /** Creates a new instance of PolylineShapeWithHoles. */
  public PolylineArea(PolylineShape borderShape, PolylineShape[] holeArr) {
    this.borderShape = borderShape;
    this.holeArr = holeArr;
  }

  private static void cutoutHolePiece(
      TileShape dividePiece, TileShape holePiece, Collection<TileShape> pieces) {
    TileShape[] resultPieces = dividePiece.cutout(holePiece);
    for (int i = 0; i < resultPieces.length; i++) {
      TileShape currPiece = resultPieces[i];
      if (currPiece.dimension() == 2) {
        pieces.add(currPiece);
      }
    }
  }

  @Override
  public int dimension() {
    return borderShape.dimension();
  }

  @Override
  public boolean isBounded() {
    return borderShape.isBounded();
  }

  @Override
  public boolean isEmpty() {
    return borderShape.isEmpty();
  }

  @Override
  public boolean isContainedIn(IntBox box) {
    return borderShape.isContainedIn(box);
  }

  @Override
  public PolylineShape getBorder() {
    return borderShape;
  }

  @Override
  public PolylineShape[] getHoles() {
    return holeArr;
  }

  @Override
  public IntBox boundingBox() {
    return borderShape.boundingBox();
  }

  @Override
  public IntOctagon boundingOctagon() {
    return borderShape.boundingOctagon();
  }

  @Override
  public boolean contains(FloatPoint point) {
    if (!borderShape.contains(point)) {
      return false;
    }
    for (int i = 0; i < holeArr.length; i++) {
      if (holeArr[i].contains(point)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean contains(Point point) {
    if (!borderShape.contains(point)) {
      return false;
    }
    for (int i = 0; i < holeArr.length; i++) {
      if (holeArr[i].containsInside(point)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public FloatPoint nearestPointApprox(FloatPoint fromPoint) {
    double minDist = Double.MAX_VALUE;
    FloatPoint result = null;
    TileShape[] convexShapes = splitToConvex();
    for (int i = 0; i < convexShapes.length; i++) {
      FloatPoint currNearestPoint = convexShapes[i].nearestPointApprox(fromPoint);
      double currDist = currNearestPoint.distanceSquare(fromPoint);
      if (currDist < minDist) {
        minDist = currDist;
        result = currNearestPoint;
      }
    }
    return result;
  }

  @Override
  public PolylineArea translateBy(Vector vector) {
    if (vector.equals(Vector.ZERO)) {
      return this;
    }
    PolylineShape translatedBorder = borderShape.translateBy(vector);
    PolylineShape[] translatedHoles = new PolylineShape[holeArr.length];
    for (int i = 0; i < holeArr.length; i++) {
      translatedHoles[i] = holeArr[i].translateBy(vector);
    }
    return new PolylineArea(translatedBorder, translatedHoles);
  }

  @Override
  public FloatPoint[] cornerApproxArr() {
    int cornerCount = borderShape.borderLineCount();
    for (int i = 0; i < holeArr.length; i++) {
      cornerCount += holeArr[i].borderLineCount();
    }
    FloatPoint[] result = new FloatPoint[cornerCount];
    FloatPoint[] currCornerArr = borderShape.cornerApproxArr();
    System.arraycopy(currCornerArr, 0, result, 0, currCornerArr.length);
    int destPos = currCornerArr.length;
    for (int i = 0; i < holeArr.length; i++) {
      currCornerArr = holeArr[i].cornerApproxArr();
      System.arraycopy(currCornerArr, 0, result, destPos, currCornerArr.length);
      destPos += currCornerArr.length;
    }
    return result;
  }

  /**
   * Splits this polygon shape with holes into convex pieces. The result is not exact, because
   * rounded intersections of lines are used in the result pieces. It can be made exact, if
   * Polylines are returned instead of Polygons, so that no intersection points are needed in the
   * result.
   */
  @Override
  public TileShape[] splitToConvex() {
    return splitToConvex(null);
  }

  /**
   * Splits this polygon shape with holes into convex pieces. The result is not exact, because
   * rounded intersections of lines are used in the result pieces. It can be made exact, if
   * Polylines are returned instead of Polygons, so that no intersection points are needed in the
   * result. If p_stoppable_thread != null, this function can be interrupted.
   */
  public TileShape[] splitToConvex(Stoppable stoppableThread) {
    if (precalculatedConvexPieces == null) {
      TileShape[] convexBorderPieces = borderShape.splitToConvex();
      if (convexBorderPieces == null) {
        // split failed
        return null;
      }
      Collection<TileShape> currPieceList = new LinkedList<>(Arrays.asList(convexBorderPieces));
      for (int i = 0; i < holeArr.length; i++) {
        if (holeArr[i].dimension() < 2) {
          FRLogger.warn("PolylineArea. split_to_convex: dimension 2 for hole expected");
          continue;
        }
        TileShape[] convexHolePieces = holeArr[i].splitToConvex();
        if (convexHolePieces == null) {
          return null;
        }
        for (int j = 0; j < convexHolePieces.length; j++) {
          TileShape currHolePiece = convexHolePieces[j];
          Collection<TileShape> newPieceList = new LinkedList<>();
          for (TileShape currDividePiece : currPieceList) {
            if (stoppableThread != null && stoppableThread.isStopRequested()) {
              return null;
            }
            cutoutHolePiece(currDividePiece, currHolePiece, newPieceList);
          }
          currPieceList = newPieceList;
        }
      }
      precalculatedConvexPieces = new TileShape[currPieceList.size()];
      Iterator<TileShape> it = currPieceList.iterator();
      for (int i = 0; i < precalculatedConvexPieces.length; i++) {
        precalculatedConvexPieces[i] = it.next();
      }
    }
    return precalculatedConvexPieces;
  }

  @Override
  public PolylineArea turn90Degree(int factor, IntPoint pole) {
    PolylineShape newBorder = borderShape.turn90Degree(factor, pole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].turn90Degree(factor, pole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea rotateApprox(double angle, FloatPoint pole) {
    PolylineShape newBorder = borderShape.rotateApprox(angle, pole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].rotateApprox(angle, pole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea mirrorVertical(IntPoint pole) {
    PolylineShape newBorder = borderShape.mirrorVertical(pole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].mirrorVertical(pole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea mirrorHorizontal(IntPoint pole) {
    PolylineShape newBorder = borderShape.mirrorHorizontal(pole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].mirrorHorizontal(pole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }
}

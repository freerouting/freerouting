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

  /** Creates a new instance of PolylineShapeWithHoles */
  public PolylineArea(PolylineShape pBorderShape, PolylineShape[] pHoleArr) {
    borderShape = pBorderShape;
    holeArr = pHoleArr;
  }

  private static void cutoutHolePiece(
      TileShape pDividePiece, TileShape pHolePiece, Collection<TileShape> pResultPieces) {
    TileShape[] resultPieces = pDividePiece.cutout(pHolePiece);
    for (int i = 0; i < resultPieces.length; i++) {
      TileShape currPiece = resultPieces[i];
      if (currPiece.dimension() == 2) {
        pResultPieces.add(currPiece);
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
  public boolean isContainedIn(IntBox pBox) {
    return borderShape.isContainedIn(pBox);
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
  public boolean contains(FloatPoint pPoint) {
    if (!borderShape.contains(pPoint)) {
      return false;
    }
    for (int i = 0; i < holeArr.length; i++) {
      if (holeArr[i].contains(pPoint)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean contains(Point pPoint) {
    if (!borderShape.contains(pPoint)) {
      return false;
    }
    for (int i = 0; i < holeArr.length; i++) {
      if (holeArr[i].containsInside(pPoint)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public FloatPoint nearestPointApprox(FloatPoint pFromPoint) {
    double minDist = Double.MAX_VALUE;
    FloatPoint result = null;
    TileShape[] convexShapes = splitToConvex();
    for (int i = 0; i < convexShapes.length; i++) {
      FloatPoint currNearestPoint = convexShapes[i].nearestPointApprox(pFromPoint);
      double currDist = currNearestPoint.distanceSquare(pFromPoint);
      if (currDist < minDist) {
        minDist = currDist;
        result = currNearestPoint;
      }
    }
    return result;
  }

  @Override
  public PolylineArea translateBy(Vector pVector) {
    if (pVector.equals(Vector.ZERO)) {
      return this;
    }
    PolylineShape translatedBorder = borderShape.translateBy(pVector);
    PolylineShape[] translatedHoles = new PolylineShape[holeArr.length];
    for (int i = 0; i < holeArr.length; i++) {
      translatedHoles[i] = holeArr[i].translateBy(pVector);
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
  public TileShape[] splitToConvex(Stoppable pStoppableThread) {
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
            if (pStoppableThread != null && pStoppableThread.isStopRequested()) {
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
  public PolylineArea turn90Degree(int pFactor, IntPoint pPole) {
    PolylineShape newBorder = borderShape.turn90Degree(pFactor, pPole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].turn90Degree(pFactor, pPole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea rotateApprox(double pAngle, FloatPoint pPole) {
    PolylineShape newBorder = borderShape.rotateApprox(pAngle, pPole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].rotateApprox(pAngle, pPole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea mirrorVertical(IntPoint pPole) {
    PolylineShape newBorder = borderShape.mirrorVertical(pPole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].mirrorVertical(pPole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }

  @Override
  public PolylineArea mirrorHorizontal(IntPoint pPole) {
    PolylineShape newBorder = borderShape.mirrorHorizontal(pPole);
    PolylineShape[] newHoleArr = new PolylineShape[holeArr.length];
    for (int i = 0; i < newHoleArr.length; i++) {
      newHoleArr[i] = holeArr[i].mirrorHorizontal(pPole);
    }
    return new PolylineArea(newBorder, newHoleArr);
  }
}

package app.freerouting.board;

import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.ViaInfo;

/** Class with static functions for checking and inserting forced vias. */
public final class ForcedViaAlgo {

  private ForcedViaAlgo() {}

  /**
   * Checks, if a Via is possible at the input layer after evtl. shoving aside obstacle traces.
   * p_room_shape is used for calculating the fromSide.
   */
  public static ForcedPadAlgo.CheckDrillResult checkLayer(
      double pViaRadius,
      int pClClass,
      boolean pAttachSmdAllowed,
      TileShape pRoomShape,
      Point pLocation,
      int pLayer,
      int[] pNetNoArr,
      int pMaxRecursionDepth,
      int pMaxViaRecursionDepth,
      RoutingBoard pBoard,
      int pTraceHalfWidth,
      int pTraceClearanceClass) {
    if (pViaRadius <= 0) {
      return ForcedPadAlgo.CheckDrillResult.DRILLABLE;
    }
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(pBoard);
    if (!(pLocation instanceof IntPoint)) {
      return ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE;
    }
    IntPoint intLocation = (IntPoint) pLocation;
    ConvexShape viaShape = new Circle(intLocation, (int) Math.ceil(pViaRadius));

    double checkRadius =
        pViaRadius
            + 0.5 * pBoard.clearanceValue(pClClass, pClClass, pLayer)
            + pBoard.getMinTraceHalfWidth();

    TileShape tileShape;
    boolean is90Degree;
    if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      tileShape = viaShape.boundingBox();
      is90Degree = true;
    } else {
      tileShape = viaShape.boundingOctagon();
      is90Degree = false;
    }

    CalcFromSide fromSide =
        calculateFromSide(
            pLocation.toFloat(), tileShape, pRoomShape.toSimplex(), checkRadius, is90Degree);
    if (fromSide == null) {
      return ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE;
    }

    ForcedPadAlgo.CheckDrillResult viaResult =
        forcedPadAlgo.checkForcedPad(
            tileShape,
            fromSide,
            pLayer,
            pNetNoArr,
            pClClass,
            pAttachSmdAllowed,
            null,
            pMaxRecursionDepth,
            pMaxViaRecursionDepth,
            false,
            null);
    if (viaResult == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
      return viaResult;
    }

    if (pTraceHalfWidth <= 0) {
      return viaResult;
    }

    Circle startTraceCircle = new Circle(intLocation, pTraceHalfWidth);
    TileShape startTraceShape;
    if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      startTraceShape = startTraceCircle.boundingBox();
    } else {
      startTraceShape = startTraceCircle.boundingOctagon();
    }

    ForcedPadAlgo.CheckDrillResult traceResult =
        forcedPadAlgo.checkForcedPad(
            startTraceShape,
            fromSide,
            pLayer,
            pNetNoArr,
            pTraceClearanceClass,
            true,
            null,
            pMaxRecursionDepth,
            pMaxViaRecursionDepth,
            false,
            null);
    if (traceResult == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
      return traceResult;
    }
    if (viaResult == ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD
        || traceResult == ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
      return ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD;
    }
    return ForcedPadAlgo.CheckDrillResult.DRILLABLE;
  }

  /**
   * Checks, if a Via is possible with the input parameter after evtl. shoving aside obstacle
   * traces.
   */
  public static boolean check(
      ViaInfo pViaInfo,
      Point pLocation,
      int[] pNetNoArr,
      int pMaxRecursionDepth,
      int pMaxViaRecursionDepth,
      RoutingBoard pBoard,
      int[] pTracePenHalfwidthArr,
      int pTraceClearanceClassNo) {
    Vector translateVector = pLocation.differenceBy(Point.ZERO);
    int calcFromSideOffset = pBoard.getMinTraceHalfWidth();
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(pBoard);
    Padstack viaPadstack = pViaInfo.getPadstack();
    Shape holeShape = holeCheckShape(viaPadstack, pLocation, pBoard);
    for (int i = viaPadstack.fromLayer(); i <= viaPadstack.toLayer(); i++) {
      Shape currPadShape = viaPadstack.getShape(i);
      int currClearanceClass = pViaInfo.getClearanceClass();
      if (currPadShape == null) {
        if (holeShape == null) {
          continue;
        }
        // The drill hole itself must keep hole clearance from copper on this layer.
        currPadShape = holeShape;
        currClearanceClass = 0;
      } else {
        currPadShape = (Shape) currPadShape.translateBy(translateVector);
      }
      TileShape tileShape;
      if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        tileShape = currPadShape.boundingBox();
      } else {
        tileShape = currPadShape.boundingOctagon();
      }
      CalcFromSide fromSide =
          forcedPadAlgo.calcFromSide(
              tileShape, pLocation, i, calcFromSideOffset, currClearanceClass);
      if (forcedPadAlgo.checkForcedPad(
              tileShape,
              fromSide,
              i,
              pNetNoArr,
              currClearanceClass,
              pViaInfo.attachSmdAllowed(),
              null,
              pMaxRecursionDepth,
              pMaxViaRecursionDepth,
              false,
              null)
          == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
        pBoard.setShoveFailingLayer(i);
        return false;
      }
      if (currClearanceClass != 0 && holeShape != null) {
        // The drill hole must ALSO keep hole clearance from other-net copper on layers where
        // the pad exists — the pad check above only enforces the (smaller) copper clearance.
        TileShape holeTile;
        if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          holeTile = holeShape.boundingBox();
        } else {
          holeTile = holeShape.boundingOctagon();
        }
        if (forcedPadAlgo.checkForcedPad(
                holeTile,
                fromSide,
                i,
                pNetNoArr,
                0,
                pViaInfo.attachSmdAllowed(),
                null,
                pMaxRecursionDepth,
                pMaxViaRecursionDepth,
                false,
                null)
            == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          pBoard.setShoveFailingLayer(i);
          return false;
        }
      }

      if (pTracePenHalfwidthArr != null
          && i < pTracePenHalfwidthArr.length
          && pTracePenHalfwidthArr[i] > 0
          && pLocation instanceof IntPoint tracePoint) {
        Circle startTraceCircle = new Circle(tracePoint, pTracePenHalfwidthArr[i]);
        TileShape startTraceShape;
        if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          startTraceShape = startTraceCircle.boundingBox();
        } else {
          startTraceShape = startTraceCircle.boundingOctagon();
        }
        if (forcedPadAlgo.checkForcedPad(
                startTraceShape,
                fromSide,
                i,
                pNetNoArr,
                pTraceClearanceClassNo,
                true,
                null,
                pMaxRecursionDepth,
                pMaxViaRecursionDepth,
                false,
                null)
            == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          pBoard.setShoveFailingLayer(i);
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Shoves aside traces, so that a via with the input parameters can be inserted without clearance
   * violations. If the shove failed, the database may be damaged, so that an undo becomes
   * necessary. p_trace_clearance_class_no and p_trace_pen_halfwidth_arr is provided to make space
   * for starting a trace in case the trace width is bigger than the via shape. Returns false, if
   * the forced via failed.
   */
  public static boolean insert(
      ViaInfo pViaInfo,
      Point pLocation,
      int[] pNetNoArr,
      int pTraceClearanceClassNo,
      int[] pTracePenHalfwidthArr,
      int pMaxRecursionDepth,
      int pMaxViaRecursionDepth,
      RoutingBoard pBoard) {
    Vector translateVector = pLocation.differenceBy(Point.ZERO);
    int calcFromSideOffset = pBoard.getMinTraceHalfWidth();
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(pBoard);
    Padstack viaPadstack = pViaInfo.getPadstack();
    Shape holeShape = holeCheckShape(viaPadstack, pLocation, pBoard);
    for (int i = viaPadstack.fromLayer(); i <= viaPadstack.toLayer(); i++) {
      Shape currPadShape = viaPadstack.getShape(i);
      int currClearanceClass = pViaInfo.getClearanceClass();
      if (currPadShape == null) {
        if (holeShape == null) {
          continue;
        }
        currPadShape = holeShape;
        currClearanceClass = 0;
      } else {
        currPadShape = (Shape) currPadShape.translateBy(translateVector);
      }
      TileShape tileShape;
      Circle startTraceCircle;
      if (pTracePenHalfwidthArr[i] > 0 && pLocation instanceof IntPoint point) {
        startTraceCircle = new Circle(point, pTracePenHalfwidthArr[i]);
      } else {
        startTraceCircle = null;
      }
      TileShape startTraceShape = null;
      if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        tileShape = currPadShape.boundingBox();
        if (startTraceCircle != null) {
          startTraceShape = startTraceCircle.boundingBox();
        }
      } else {
        tileShape = currPadShape.boundingOctagon();
        if (startTraceCircle != null) {
          startTraceShape = startTraceCircle.boundingOctagon();
        }
      }
      CalcFromSide fromSide =
          forcedPadAlgo.calcFromSide(
              tileShape, pLocation, i, calcFromSideOffset, currClearanceClass);
      if (!forcedPadAlgo.forcedPad(
          tileShape,
          fromSide,
          i,
          pNetNoArr,
          currClearanceClass,
          pViaInfo.attachSmdAllowed(),
          null,
          pMaxRecursionDepth,
          pMaxViaRecursionDepth)) {
        pBoard.setShoveFailingLayer(i);
        return false;
      }
      if (currClearanceClass != 0 && holeShape != null) {
        TileShape holeTile;
        if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          holeTile = holeShape.boundingBox();
        } else {
          holeTile = holeShape.boundingOctagon();
        }
        if (!forcedPadAlgo.forcedPad(
            holeTile,
            fromSide,
            i,
            pNetNoArr,
            0,
            pViaInfo.attachSmdAllowed(),
            null,
            pMaxRecursionDepth,
            pMaxViaRecursionDepth)) {
          pBoard.setShoveFailingLayer(i);
          return false;
        }
      }
      if (startTraceShape != null) {
        // necessary in case startTraceShape is bigger than tileShape
        if (!forcedPadAlgo.forcedPad(
            startTraceShape,
            fromSide,
            i,
            pNetNoArr,
            pTraceClearanceClassNo,
            true,
            null,
            pMaxRecursionDepth,
            pMaxViaRecursionDepth)) {
          pBoard.setShoveFailingLayer(i);
          return false;
        }
      }
    }
    pBoard.insertVia(
        viaPadstack,
        pLocation,
        pNetNoArr,
        pViaInfo.getClearanceClass(),
        FixedState.UNFIXED,
        pViaInfo.attachSmdAllowed());
    return true;
  }

  /**
   * Hole-clearance substitute shape for a copper-less layer of a via padstack: the drill still
   * passes through, so other copper must stay holeClearance away from it. Returns null when the
   * rule is off or no drill radius is known.
   */
  private static Shape holeCheckShape(Padstack pPadstack, Point pLocation, RoutingBoard pBoard) {
    int holeClearance = pBoard.rules.getHoleClearance();
    if (holeClearance <= 0 || !(pLocation instanceof IntPoint center)) {
      return null;
    }
    double drillRadius = pPadstack.getDrillRadius();
    if (drillRadius <= 0) {
      return null;
    }
    // Inflate by the hole clearance itself and check with the null clearance class (0), so
    // the requirement is exact hole-to-copper spacing regardless of the neighbor's class.
    return new Circle(center, (int) Math.ceil(drillRadius + holeClearance + 10));
  }

  private static CalcFromSide calculateFromSide(
      FloatPoint pViaLocation,
      TileShape pViaShape,
      Simplex pRoomShape,
      double pDist,
      boolean is90Degree) {
    IntBox viaBox = pViaShape.boundingBox();
    for (int i = 0; i < 4; i++) {
      FloatPoint checkPoint;
      double borderX;
      double borderY;
      switch (i) {
        case 0 -> {
          checkPoint = new FloatPoint(pViaLocation.x, pViaLocation.y - pDist);
          borderX = pViaLocation.x;
          borderY = viaBox.ll.y;
        }
        case 1 -> {
          checkPoint = new FloatPoint(pViaLocation.x + pDist, pViaLocation.y);
          borderX = viaBox.ur.x;
          borderY = pViaLocation.y;
        }
        case 2 -> {
          checkPoint = new FloatPoint(pViaLocation.x, pViaLocation.y + pDist);
          borderX = pViaLocation.x;
          borderY = viaBox.ur.y;
        }
        default -> { // i == 3
          checkPoint = new FloatPoint(pViaLocation.x - pDist, pViaLocation.y);
          borderX = viaBox.ll.x;
          borderY = pViaLocation.y;
        }
      }
      if (pRoomShape.contains(checkPoint)) {
        int fromSideNo;
        if (is90Degree) {
          fromSideNo = i;
        } else {
          fromSideNo = 2 * i;
        }
        FloatPoint currBorderPoint = new FloatPoint(borderX, borderY);
        return new CalcFromSide(fromSideNo, currBorderPoint);
      }
    }
    if (is90Degree) {
      return null;
    }
    // try the diagonal directions
    double dist = pDist / Limits.sqrt2;
    double borderDist = viaBox.maxWidth() / (2 * Limits.sqrt2);
    for (int i = 0; i < 4; i++) {
      FloatPoint checkPoint;
      double borderX;
      double borderY;
      switch (i) {
        case 0 -> {
          checkPoint = new FloatPoint(pViaLocation.x + dist, pViaLocation.y - dist);
          borderX = pViaLocation.x + borderDist;
          borderY = pViaLocation.y - borderDist;
        }
        case 1 -> {
          checkPoint = new FloatPoint(pViaLocation.x + dist, pViaLocation.y + dist);
          borderX = pViaLocation.x + borderDist;
          borderY = pViaLocation.y + borderDist;
        }
        case 2 -> {
          checkPoint = new FloatPoint(pViaLocation.x - dist, pViaLocation.y + dist);
          borderX = pViaLocation.x - borderDist;
          borderY = pViaLocation.y + borderDist;
        }
        default -> { // i == 3
          checkPoint = new FloatPoint(pViaLocation.x - dist, pViaLocation.y - dist);
          borderX = pViaLocation.x - borderDist;
          borderY = pViaLocation.y - borderDist;
        }
      }
      if (pRoomShape.contains(checkPoint)) {

        int fromSideNo = 2 * i + 1;
        FloatPoint currBorderPoint = new FloatPoint(borderX, borderY);
        return new CalcFromSide(fromSideNo, currBorderPoint);
      }
    }
    return null;
  }
}

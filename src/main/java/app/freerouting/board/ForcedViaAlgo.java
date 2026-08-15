package app.freerouting.board;

import app.freerouting.core.library.Padstack;
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
      double viaRadius,
      int clClass,
      boolean attachSmdAllowed,
      TileShape roomShape,
      Point location,
      int layer,
      int[] netNoArr,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      RoutingBoard board,
      int traceHalfWidth,
      int traceClearanceClass) {
    if (viaRadius <= 0) {
      return ForcedPadAlgo.CheckDrillResult.DRILLABLE;
    }
    if (!(location instanceof IntPoint)) {
      return ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE;
    }
    IntPoint intLocation = (IntPoint) location;
    ConvexShape viaShape = new Circle(intLocation, (int) Math.ceil(viaRadius));

    double checkRadius =
        viaRadius
            + 0.5 * board.clearanceValue(clClass, clClass, layer)
            + board.getMinTraceHalfWidth();

    TileShape tileShape;
    boolean is90Degree;
    if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      tileShape = viaShape.boundingBox();
      is90Degree = true;
    } else {
      tileShape = viaShape.boundingOctagon();
      is90Degree = false;
    }

    CalcFromSide fromSide =
        calculateFromSide(
            location.toFloat(), tileShape, roomShape.toSimplex(), checkRadius, is90Degree);
    if (fromSide == null) {
      return ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE;
    }

    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(board);
    ForcedPadAlgo.CheckDrillResult viaResult =
        forcedPadAlgo.checkForcedPad(
            tileShape,
            fromSide,
            layer,
            netNoArr,
            clClass,
            attachSmdAllowed,
            null,
            maxRecursionDepth,
            maxViaRecursionDepth,
            false,
            null);
    if (viaResult == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
      return viaResult;
    }

    if (traceHalfWidth <= 0) {
      return viaResult;
    }

    Circle startTraceCircle = new Circle(intLocation, traceHalfWidth);
    TileShape startTraceShape;
    if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      startTraceShape = startTraceCircle.boundingBox();
    } else {
      startTraceShape = startTraceCircle.boundingOctagon();
    }

    ForcedPadAlgo.CheckDrillResult traceResult =
        forcedPadAlgo.checkForcedPad(
            startTraceShape,
            fromSide,
            layer,
            netNoArr,
            traceClearanceClass,
            true,
            null,
            maxRecursionDepth,
            maxViaRecursionDepth,
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
      ViaInfo viaInfo,
      Point location,
      int[] netNoArr,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      RoutingBoard board,
      int[] tracePenHalfwidthArr,
      int traceClearanceClassNo) {
    Vector translateVector = location.differenceBy(Point.ZERO);
    int calcFromSideOffset = board.getMinTraceHalfWidth();
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(board);
    Padstack viaPadstack = viaInfo.getPadstack();
    Shape holeShape = holeCheckShape(viaPadstack, location, board);
    for (int i = viaPadstack.fromLayer(); i <= viaPadstack.toLayer(); i++) {
      Shape currPadShape = viaPadstack.getShape(i);
      int currClearanceClass = viaInfo.getClearanceClass();
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
      if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        tileShape = currPadShape.boundingBox();
      } else {
        tileShape = currPadShape.boundingOctagon();
      }
      CalcFromSide fromSide =
          forcedPadAlgo.calcFromSide(
              tileShape, location, i, calcFromSideOffset, currClearanceClass);
      if (forcedPadAlgo.checkForcedPad(
              tileShape,
              fromSide,
              i,
              netNoArr,
              currClearanceClass,
              viaInfo.attachSmdAllowed(),
              null,
              maxRecursionDepth,
              maxViaRecursionDepth,
              false,
              null)
          == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
        board.setShoveFailingLayer(i);
        return false;
      }
      if (currClearanceClass != 0 && holeShape != null) {
        // The drill hole must ALSO keep hole clearance from other-net copper on layers where
        // the pad exists — the pad check above only enforces the (smaller) copper clearance.
        TileShape holeTile;
        if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          holeTile = holeShape.boundingBox();
        } else {
          holeTile = holeShape.boundingOctagon();
        }
        if (forcedPadAlgo.checkForcedPad(
                holeTile,
                fromSide,
                i,
                netNoArr,
                0,
                viaInfo.attachSmdAllowed(),
                null,
                maxRecursionDepth,
                maxViaRecursionDepth,
                false,
                null)
            == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          board.setShoveFailingLayer(i);
          return false;
        }
      }

      if (tracePenHalfwidthArr != null
          && i < tracePenHalfwidthArr.length
          && tracePenHalfwidthArr[i] > 0
          && location instanceof IntPoint tracePoint) {
        Circle startTraceCircle = new Circle(tracePoint, tracePenHalfwidthArr[i]);
        TileShape startTraceShape;
        if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          startTraceShape = startTraceCircle.boundingBox();
        } else {
          startTraceShape = startTraceCircle.boundingOctagon();
        }
        if (forcedPadAlgo.checkForcedPad(
                startTraceShape,
                fromSide,
                i,
                netNoArr,
                traceClearanceClassNo,
                true,
                null,
                maxRecursionDepth,
                maxViaRecursionDepth,
                false,
                null)
            == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          board.setShoveFailingLayer(i);
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
      ViaInfo viaInfo,
      Point location,
      int[] netNoArr,
      int traceClearanceClassNo,
      int[] tracePenHalfwidthArr,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      RoutingBoard board) {
    Vector translateVector = location.differenceBy(Point.ZERO);
    int calcFromSideOffset = board.getMinTraceHalfWidth();
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(board);
    Padstack viaPadstack = viaInfo.getPadstack();
    Shape holeShape = holeCheckShape(viaPadstack, location, board);
    for (int i = viaPadstack.fromLayer(); i <= viaPadstack.toLayer(); i++) {
      Shape currPadShape = viaPadstack.getShape(i);
      int currClearanceClass = viaInfo.getClearanceClass();
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
      if (tracePenHalfwidthArr[i] > 0 && location instanceof IntPoint point) {
        startTraceCircle = new Circle(point, tracePenHalfwidthArr[i]);
      } else {
        startTraceCircle = null;
      }
      TileShape startTraceShape = null;
      if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
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
              tileShape, location, i, calcFromSideOffset, currClearanceClass);
      if (!forcedPadAlgo.forcedPad(
          tileShape,
          fromSide,
          i,
          netNoArr,
          currClearanceClass,
          viaInfo.attachSmdAllowed(),
          null,
          maxRecursionDepth,
          maxViaRecursionDepth)) {
        board.setShoveFailingLayer(i);
        return false;
      }
      if (currClearanceClass != 0 && holeShape != null) {
        TileShape holeTile;
        if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          holeTile = holeShape.boundingBox();
        } else {
          holeTile = holeShape.boundingOctagon();
        }
        if (!forcedPadAlgo.forcedPad(
            holeTile,
            fromSide,
            i,
            netNoArr,
            0,
            viaInfo.attachSmdAllowed(),
            null,
            maxRecursionDepth,
            maxViaRecursionDepth)) {
          board.setShoveFailingLayer(i);
          return false;
        }
      }
      if (startTraceShape != null) {
        // necessary in case startTraceShape is bigger than tileShape
        if (!forcedPadAlgo.forcedPad(
            startTraceShape,
            fromSide,
            i,
            netNoArr,
            traceClearanceClassNo,
            true,
            null,
            maxRecursionDepth,
            maxViaRecursionDepth)) {
          board.setShoveFailingLayer(i);
          return false;
        }
      }
    }
    board.insertVia(
        viaPadstack,
        location,
        netNoArr,
        viaInfo.getClearanceClass(),
        FixedState.UNFIXED,
        viaInfo.attachSmdAllowed());
    return true;
  }

  /**
   * Hole-clearance substitute shape for a copper-less layer of a via padstack: the drill still
   * passes through, so other copper must stay holeClearance away from it. Returns null when the
   * rule is off or no drill radius is known.
   */
  private static Shape holeCheckShape(Padstack padstack, Point location, RoutingBoard board) {
    int holeClearance = board.rules.getHoleClearance();
    if (holeClearance <= 0 || !(location instanceof IntPoint center)) {
      return null;
    }
    double drillRadius = padstack.getDrillRadius();
    if (drillRadius <= 0) {
      return null;
    }
    // Inflate by the hole clearance itself and check with the null clearance class (0), so
    // the requirement is exact hole-to-copper spacing regardless of the neighbor's class.
    return new Circle(center, (int) Math.ceil(drillRadius + holeClearance + 10));
  }

  private static CalcFromSide calculateFromSide(
      FloatPoint viaLocation,
      TileShape viaShape,
      Simplex roomShape,
      double dist,
      boolean is90Degree) {
    IntBox viaBox = viaShape.boundingBox();
    for (int i = 0; i < 4; i++) {
      FloatPoint checkPoint;
      double borderX;
      double borderY;
      switch (i) {
        case 0 -> {
          checkPoint = new FloatPoint(viaLocation.x, viaLocation.y - dist);
          borderX = viaLocation.x;
          borderY = viaBox.ll.y;
        }
        case 1 -> {
          checkPoint = new FloatPoint(viaLocation.x + dist, viaLocation.y);
          borderX = viaBox.ur.x;
          borderY = viaLocation.y;
        }
        case 2 -> {
          checkPoint = new FloatPoint(viaLocation.x, viaLocation.y + dist);
          borderX = viaLocation.x;
          borderY = viaBox.ur.y;
        }
        default -> { // i == 3
          checkPoint = new FloatPoint(viaLocation.x - dist, viaLocation.y);
          borderX = viaBox.ll.x;
          borderY = viaLocation.y;
        }
      }
      if (roomShape.contains(checkPoint)) {
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
    dist = dist / Limits.sqrt2;
    double borderDist = viaBox.maxWidth() / (2 * Limits.sqrt2);
    for (int i = 0; i < 4; i++) {
      FloatPoint checkPoint;
      double borderX;
      double borderY;
      switch (i) {
        case 0 -> {
          checkPoint = new FloatPoint(viaLocation.x + dist, viaLocation.y - dist);
          borderX = viaLocation.x + borderDist;
          borderY = viaLocation.y - borderDist;
        }
        case 1 -> {
          checkPoint = new FloatPoint(viaLocation.x + dist, viaLocation.y + dist);
          borderX = viaLocation.x + borderDist;
          borderY = viaLocation.y + borderDist;
        }
        case 2 -> {
          checkPoint = new FloatPoint(viaLocation.x - dist, viaLocation.y + dist);
          borderX = viaLocation.x - borderDist;
          borderY = viaLocation.y + borderDist;
        }
        default -> { // i == 3
          checkPoint = new FloatPoint(viaLocation.x - dist, viaLocation.y - dist);
          borderX = viaLocation.x - borderDist;
          borderY = viaLocation.y - borderDist;
        }
      }
      if (roomShape.contains(checkPoint)) {

        int fromSideNo = 2 * i + 1;
        FloatPoint currBorderPoint = new FloatPoint(borderX, borderY);
        return new CalcFromSide(fromSideNo, currBorderPoint);
      }
    }
    return null;
  }
}

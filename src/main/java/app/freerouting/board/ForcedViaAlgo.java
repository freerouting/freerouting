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
      double p_via_radius,
      int p_cl_class,
      boolean p_attach_smd_allowed,
      TileShape p_room_shape,
      Point p_location,
      int p_layer,
      int[] p_net_no_arr,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      RoutingBoard p_board,
      int p_trace_half_width,
      int p_trace_clearance_class) {
    if (p_via_radius <= 0) {
      return ForcedPadAlgo.CheckDrillResult.DRILLABLE;
    }
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(p_board);
    if (!(p_location instanceof IntPoint)) {
      return ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE;
    }
    IntPoint intLocation = (IntPoint) p_location;
    ConvexShape viaShape = new Circle(intLocation, (int) Math.ceil(p_via_radius));

    double checkRadius =
        p_via_radius
            + 0.5 * p_board.clearanceValue(p_cl_class, p_cl_class, p_layer)
            + p_board.getMinTraceHalfWidth();

    TileShape tileShape;
    boolean is90Degree;
    if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      tileShape = viaShape.boundingBox();
      is90Degree = true;
    } else {
      tileShape = viaShape.boundingOctagon();
      is90Degree = false;
    }

    CalcFromSide fromSide =
        calculateFromSide(
            p_location.toFloat(), tileShape, p_room_shape.toSimplex(), checkRadius, is90Degree);
    if (fromSide == null) {
      return ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE;
    }

    ForcedPadAlgo.CheckDrillResult viaResult =
        forcedPadAlgo.checkForcedPad(
            tileShape,
            fromSide,
            p_layer,
            p_net_no_arr,
            p_cl_class,
            p_attach_smd_allowed,
            null,
            p_max_recursion_depth,
            p_max_via_recursion_depth,
            false,
            null);
    if (viaResult == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
      return viaResult;
    }

    if (p_trace_half_width <= 0) {
      return viaResult;
    }

    Circle startTraceCircle = new Circle(intLocation, p_trace_half_width);
    TileShape startTraceShape;
    if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      startTraceShape = startTraceCircle.boundingBox();
    } else {
      startTraceShape = startTraceCircle.boundingOctagon();
    }

    ForcedPadAlgo.CheckDrillResult traceResult =
        forcedPadAlgo.checkForcedPad(
            startTraceShape,
            fromSide,
            p_layer,
            p_net_no_arr,
            p_trace_clearance_class,
            true,
            null,
            p_max_recursion_depth,
            p_max_via_recursion_depth,
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
      ViaInfo p_via_info,
      Point p_location,
      int[] p_net_no_arr,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      RoutingBoard p_board,
      int[] p_trace_pen_halfwidth_arr,
      int p_trace_clearance_class_no) {
    Vector translateVector = p_location.differenceBy(Point.ZERO);
    int calcFromSideOffset = p_board.getMinTraceHalfWidth();
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(p_board);
    Padstack viaPadstack = p_via_info.getPadstack();
    Shape holeShape = holeCheckShape(viaPadstack, p_location, p_board);
    for (int i = viaPadstack.fromLayer(); i <= viaPadstack.toLayer(); i++) {
      Shape currPadShape = viaPadstack.getShape(i);
      int currClearanceClass = p_via_info.getClearanceClass();
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
      if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        tileShape = currPadShape.boundingBox();
      } else {
        tileShape = currPadShape.boundingOctagon();
      }
      CalcFromSide fromSide =
          forcedPadAlgo.calcFromSide(
              tileShape, p_location, i, calcFromSideOffset, currClearanceClass);
      if (forcedPadAlgo.checkForcedPad(
              tileShape,
              fromSide,
              i,
              p_net_no_arr,
              currClearanceClass,
              p_via_info.attachSmdAllowed(),
              null,
              p_max_recursion_depth,
              p_max_via_recursion_depth,
              false,
              null)
          == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
        p_board.setShoveFailingLayer(i);
        return false;
      }
      if (currClearanceClass != 0 && holeShape != null) {
        // The drill hole must ALSO keep hole clearance from other-net copper on layers where
        // the pad exists — the pad check above only enforces the (smaller) copper clearance.
        TileShape holeTile;
        if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          holeTile = holeShape.boundingBox();
        } else {
          holeTile = holeShape.boundingOctagon();
        }
        if (forcedPadAlgo.checkForcedPad(
                holeTile,
                fromSide,
                i,
                p_net_no_arr,
                0,
                p_via_info.attachSmdAllowed(),
                null,
                p_max_recursion_depth,
                p_max_via_recursion_depth,
                false,
                null)
            == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          p_board.setShoveFailingLayer(i);
          return false;
        }
      }

      if (p_trace_pen_halfwidth_arr != null
          && i < p_trace_pen_halfwidth_arr.length
          && p_trace_pen_halfwidth_arr[i] > 0
          && p_location instanceof IntPoint tracePoint) {
        Circle startTraceCircle = new Circle(tracePoint, p_trace_pen_halfwidth_arr[i]);
        TileShape startTraceShape;
        if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          startTraceShape = startTraceCircle.boundingBox();
        } else {
          startTraceShape = startTraceCircle.boundingOctagon();
        }
        if (forcedPadAlgo.checkForcedPad(
                startTraceShape,
                fromSide,
                i,
                p_net_no_arr,
                p_trace_clearance_class_no,
                true,
                null,
                p_max_recursion_depth,
                p_max_via_recursion_depth,
                false,
                null)
            == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          p_board.setShoveFailingLayer(i);
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
      ViaInfo p_via_info,
      Point p_location,
      int[] p_net_no_arr,
      int p_trace_clearance_class_no,
      int[] p_trace_pen_halfwidth_arr,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      RoutingBoard p_board) {
    Vector translateVector = p_location.differenceBy(Point.ZERO);
    int calcFromSideOffset = p_board.getMinTraceHalfWidth();
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(p_board);
    Padstack viaPadstack = p_via_info.getPadstack();
    Shape holeShape = holeCheckShape(viaPadstack, p_location, p_board);
    for (int i = viaPadstack.fromLayer(); i <= viaPadstack.toLayer(); i++) {
      Shape currPadShape = viaPadstack.getShape(i);
      int currClearanceClass = p_via_info.getClearanceClass();
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
      if (p_trace_pen_halfwidth_arr[i] > 0 && p_location instanceof IntPoint point) {
        startTraceCircle = new Circle(point, p_trace_pen_halfwidth_arr[i]);
      } else {
        startTraceCircle = null;
      }
      TileShape startTraceShape = null;
      if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
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
              tileShape, p_location, i, calcFromSideOffset, currClearanceClass);
      if (!forcedPadAlgo.forcedPad(
          tileShape,
          fromSide,
          i,
          p_net_no_arr,
          currClearanceClass,
          p_via_info.attachSmdAllowed(),
          null,
          p_max_recursion_depth,
          p_max_via_recursion_depth)) {
        p_board.setShoveFailingLayer(i);
        return false;
      }
      if (currClearanceClass != 0 && holeShape != null) {
        TileShape holeTile;
        if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          holeTile = holeShape.boundingBox();
        } else {
          holeTile = holeShape.boundingOctagon();
        }
        if (!forcedPadAlgo.forcedPad(
            holeTile,
            fromSide,
            i,
            p_net_no_arr,
            0,
            p_via_info.attachSmdAllowed(),
            null,
            p_max_recursion_depth,
            p_max_via_recursion_depth)) {
          p_board.setShoveFailingLayer(i);
          return false;
        }
      }
      if (startTraceShape != null) {
        // necessary in case startTraceShape is bigger than tileShape
        if (!forcedPadAlgo.forcedPad(
            startTraceShape,
            fromSide,
            i,
            p_net_no_arr,
            p_trace_clearance_class_no,
            true,
            null,
            p_max_recursion_depth,
            p_max_via_recursion_depth)) {
          p_board.setShoveFailingLayer(i);
          return false;
        }
      }
    }
    p_board.insertVia(
        viaPadstack,
        p_location,
        p_net_no_arr,
        p_via_info.getClearanceClass(),
        FixedState.UNFIXED,
        p_via_info.attachSmdAllowed());
    return true;
  }

  /**
   * Hole-clearance substitute shape for a copper-less layer of a via padstack: the drill still
   * passes through, so other copper must stay holeClearance away from it. Returns null when the
   * rule is off or no drill radius is known.
   */
  private static Shape holeCheckShape(
      Padstack p_padstack, Point p_location, RoutingBoard p_board) {
    int holeClearance = p_board.rules.getHoleClearance();
    if (holeClearance <= 0 || !(p_location instanceof IntPoint center)) {
      return null;
    }
    double drillRadius = p_padstack.getDrillRadius();
    if (drillRadius <= 0) {
      return null;
    }
    // Inflate by the hole clearance itself and check with the null clearance class (0), so
    // the requirement is exact hole-to-copper spacing regardless of the neighbor's class.
    return new Circle(center, (int) Math.ceil(drillRadius + holeClearance + 10));
  }

  private static CalcFromSide calculateFromSide(
      FloatPoint p_via_location,
      TileShape p_via_shape,
      Simplex p_room_shape,
      double p_dist,
      boolean is90Degree) {
    IntBox viaBox = p_via_shape.boundingBox();
    for (int i = 0; i < 4; i++) {
      FloatPoint checkPoint;
      double borderX;
      double borderY;
      switch (i) {
        case 0 -> {
          checkPoint = new FloatPoint(p_via_location.x, p_via_location.y - p_dist);
          borderX = p_via_location.x;
          borderY = viaBox.ll.y;
        }
        case 1 -> {
          checkPoint = new FloatPoint(p_via_location.x + p_dist, p_via_location.y);
          borderX = viaBox.ur.x;
          borderY = p_via_location.y;
        }
        case 2 -> {
          checkPoint = new FloatPoint(p_via_location.x, p_via_location.y + p_dist);
          borderX = p_via_location.x;
          borderY = viaBox.ur.y;
        }
        default -> { // i == 3
          checkPoint = new FloatPoint(p_via_location.x - p_dist, p_via_location.y);
          borderX = viaBox.ll.x;
          borderY = p_via_location.y;
        }
      }
      if (p_room_shape.contains(checkPoint)) {
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
    double dist = p_dist / Limits.sqrt2;
    double borderDist = viaBox.maxWidth() / (2 * Limits.sqrt2);
    for (int i = 0; i < 4; i++) {
      FloatPoint checkPoint;
      double borderX;
      double borderY;
      switch (i) {
        case 0 -> {
          checkPoint = new FloatPoint(p_via_location.x + dist, p_via_location.y - dist);
          borderX = p_via_location.x + borderDist;
          borderY = p_via_location.y - borderDist;
        }
        case 1 -> {
          checkPoint = new FloatPoint(p_via_location.x + dist, p_via_location.y + dist);
          borderX = p_via_location.x + borderDist;
          borderY = p_via_location.y + borderDist;
        }
        case 2 -> {
          checkPoint = new FloatPoint(p_via_location.x - dist, p_via_location.y + dist);
          borderX = p_via_location.x - borderDist;
          borderY = p_via_location.y + borderDist;
        }
        default -> { // i == 3
          checkPoint = new FloatPoint(p_via_location.x - dist, p_via_location.y - dist);
          borderX = p_via_location.x - borderDist;
          borderY = p_via_location.y - borderDist;
        }
      }
      if (p_room_shape.contains(checkPoint)) {

        int fromSideNo = 2 * i + 1;
        FloatPoint currBorderPoint = new FloatPoint(borderX, borderY);
        return new CalcFromSide(fromSideNo, currBorderPoint);
      }
    }
    return null;
  }
}

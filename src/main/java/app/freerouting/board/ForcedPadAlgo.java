package app.freerouting.board;

import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Class with functions for checking and inserting pads with eventually shoving aside obstacle
 * traces.
 */
public class ForcedPadAlgo {

  private final RoutingBoard board;

  /** Creates a new instance of ForcedPadAlgo */
  public ForcedPadAlgo(RoutingBoard p_board) {
    board = p_board;
  }

  private static TileShape calc_check_shape_for_from_side(
      TileShape p_shape, Point p_shape_center, Line p_border_line) {
    FloatPoint shapeCenter = p_shape_center.to_float();
    FloatPoint offsetProjection = shapeCenter.projection_approx(p_border_line);
    // Make sure, that direction restrictions are retained.
    Line[] lineArr = new Line[3];
    Direction currDir = p_border_line.direction();
    lineArr[0] = new Line(p_shape_center, currDir);
    lineArr[1] = new Line(p_shape_center, currDir.turn_45_degree(2));
    lineArr[2] = new Line(offsetProjection.round(), currDir);
    Polyline checkLine = new Polyline(lineArr);
    return checkLine.offset_shape(1, 0);
  }

  /** Checks, if p_line is in front of p_pad_shape when shoving from p_from_side */
  private static boolean in_front_of_pad(
      Line p_line, TileShape p_pad_shape, int p_from_side, int p_width, boolean p_with_sides) {
    if (!p_pad_shape.is_IntOctagon()) {
      // only implemented for octagons
      return true;
    }
    IntOctagon padOctagon = p_pad_shape.bounding_octagon();
    if (!(p_line.a instanceof IntPoint lineA && p_line.b instanceof IntPoint line_b)) {
      // not implemented
      return true;
    }

    double diagWidth = p_width * Math.sqrt(2);

    boolean result;
    switch (p_from_side) {
      case 0 -> {
        result =
            Math.min(lineA.y, line_b.y) >= padOctagon.topY + p_width
                || Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.min(lineA.x + lineA.y, line_b.x + line_b.x)
                    >= padOctagon.upperRightDiagonalX + diagWidth;
        if (p_with_sides && !result) {
          result =
              Math.max(lineA.x, line_b.x) <= padOctagon.leftX - p_width
                      && Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth
                  || Math.min(lineA.x, line_b.x) >= padOctagon.rightX + p_width
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 1 -> {
        result =
            Math.min(lineA.y, line_b.y) >= padOctagon.topY + p_width
                || Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.max(lineA.x, line_b.x) <= padOctagon.leftX - p_width;
        if (p_with_sides && !result) {
          result =
              Math.min(lineA.x, line_b.x) <= padOctagon.leftX - p_width
                      && Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth
                  || Math.max(lineA.y, line_b.y) >= padOctagon.topY + p_width
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 2 -> {
        result =
            Math.max(lineA.x, line_b.x) <= padOctagon.leftX - p_width
                || Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth;
        if (p_with_sides && !result) {
          result =
              Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - p_width
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth
                  || Math.min(lineA.y, line_b.y) >= padOctagon.topY + p_width
                      && Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth;
        }
      }
      case 3 -> {
        result =
            Math.max(lineA.x, line_b.x) <= padOctagon.leftX - p_width
                || Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - p_width
                || Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth;
        if (p_with_sides && !result) {
          result =
              Math.min(lineA.y, line_b.y) <= padOctagon.bottomY - p_width
                      && Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.min(lineA.x, line_b.x) <= padOctagon.leftX - p_width
                      && Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth;
        }
      }
      case 4 -> {
        result =
            Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - p_width
                || Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth
                || Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (p_with_sides && !result) {
          result =
              Math.min(lineA.x, line_b.x) >= padOctagon.rightX + p_width
                      && Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.max(lineA.x, line_b.x) <= padOctagon.leftX - p_width
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth;
        }
      }
      case 5 -> {
        result =
            Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - p_width
                || Math.min(lineA.x, line_b.x) >= padOctagon.rightX + p_width
                || Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (p_with_sides && !result) {
          result =
              Math.max(lineA.x, line_b.x) >= padOctagon.rightX + p_width
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth
                  || Math.min(lineA.y, line_b.y) <= padOctagon.bottomY - p_width
                      && Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth;
        }
      }
      case 6 -> {
        result =
            Math.min(lineA.x, line_b.x) >= padOctagon.rightX + p_width
                || Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                    >= padOctagon.upperRightDiagonalX + diagWidth
                || Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (p_with_sides && !result) {
          result =
              Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - p_width
                      && Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.min(lineA.y, line_b.y) >= padOctagon.topY + p_width
                      && Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 7 -> {
        result =
            Math.min(lineA.y, line_b.y) >= padOctagon.topY + p_width
                || Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                    >= padOctagon.upperRightDiagonalX + diagWidth
                || Math.min(lineA.x, line_b.x) >= padOctagon.rightX + p_width;
        if (p_with_sides && !result) {
          result =
              Math.max(lineA.y, line_b.y) >= padOctagon.topY + p_width
                      && Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth
                  || Math.max(lineA.x, line_b.x) >= padOctagon.rightX + p_width
                      && Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth;
        }
      }
      default -> {
        FRLogger.warn("ForcedPadAlgo.in_front_of_pad: p_from_side out of range");
        result = true;
      }
    }

    return result;
  }

  /**
   * Checks, if possible obstacle traces can be shoved aside, so that a pad with the input
   * parameters can be inserted without clearance violations. Returns false, if the check failed. If
   * p_ignore_items != null, items in this list are not checked, If p_check_only_front only trace
   * obstacles in the direction from p_from_side are checked for performance reasons. This is the
   * cave when moving drill_items
   */
  public CheckDrillResult check_forced_pad(
      TileShape p_pad_shape,
      CalcFromSide p_from_side,
      int p_layer,
      int[] p_net_no_arr,
      int p_cl_type,
      boolean p_copper_sharing_allowed,
      Collection<Item> p_ignore_items,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      boolean p_check_only_front,
      TimeLimit p_time_limit) {
    if (!p_pad_shape.is_contained_in(board.get_bounding_box())) {
      this.board.set_shove_failing_obstacle(board.get_outline());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    ShapeSearchTree searchTree = this.board.searchTreeManager.get_default_tree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(p_pad_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, board);
    Collection<Item> obstacles =
        searchTree.overlapping_items_with_clearance(p_pad_shape, p_layer, new int[0], p_cl_type);

    if (p_ignore_items != null) {
      obstacles.removeAll(p_ignore_items);
    }
    boolean obstaclesShovable = shapeEntries.store_items(obstacles, true, p_copper_sharing_allowed);
    if (!obstaclesShovable) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }

    // check, if the obstacle vias can be shoved

    for (Via curr_shove_via : shapeEntries.shoveViaList) {
      if (p_max_via_recursion_depth <= 0) {
        this.board.set_shove_failing_obstacle(curr_shove_via);
        return CheckDrillResult.NOT_DRILLABLE;
      }
      IntPoint[] newViaCenter =
          MoveDrillItemAlgo.try_shove_via_points(
              p_pad_shape, p_layer, curr_shove_via, p_cl_type, false, board);

      if (newViaCenter.length == 0) {
        this.board.set_shove_failing_obstacle(curr_shove_via);
        return CheckDrillResult.NOT_DRILLABLE;
      }
      Vector delta = newViaCenter[0].difference_by(curr_shove_via.get_center());
      Collection<Item> ignoreItems = new LinkedList<>();
      if (!MoveDrillItemAlgo.check(
          curr_shove_via,
          delta,
          p_max_recursion_depth,
          p_max_via_recursion_depth - 1,
          ignoreItems,
          this.board,
          p_time_limit)) {
        return CheckDrillResult.NOT_DRILLABLE;
      }
    }
    CheckDrillResult result = CheckDrillResult.DRILLABLE;
    if (p_copper_sharing_allowed) {
      for (Item curr_obstacle : obstacles) {
        if (curr_obstacle instanceof Pin) {
          result = CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD;
          break;
        }
      }
    }
    int tracePieceCount = shapeEntries.substitute_trace_count();
    if (tracePieceCount == 0) {
      return result;
    }
    if (p_max_recursion_depth <= 0) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    if (shapeEntries.stack_depth() > 1) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(board);
    boolean isOrthogonalMode = p_pad_shape instanceof IntBox;
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.next_substitute_trace_piece();
      if (currSubstituteTrace == null) {
        break;
      }
      for (int i = 0; i < currSubstituteTrace.tile_shape_count(); i++) {
        Line currLine = currSubstituteTrace.polyline().arr[i + 1];
        Direction currDir = currLine.direction();
        boolean isInFront;
        if (p_check_only_front) {
          isInFront =
              in_front_of_pad(
                  currLine,
                  p_pad_shape,
                  p_from_side.no,
                  currSubstituteTrace.get_half_width(),
                  true);
        } else {
          isInFront = true;
        }
        if (isInFront) {
          CalcShapeAndFromSide curr =
              new CalcShapeAndFromSide(currSubstituteTrace, i, isOrthogonalMode, true);
          if (!shoveTraceAlgo.check(
              curr.shape,
              curr.fromSide,
              currDir,
              p_layer,
              currSubstituteTrace.netNoArr,
              currSubstituteTrace.clearance_class_no(),
              p_max_recursion_depth - 1,
              p_max_via_recursion_depth,
              0,
              p_time_limit)) {
            return CheckDrillResult.NOT_DRILLABLE;
          }
        }
      }
    }
    return result;
  }

  /**
   * Shoves aside traces, so that a pad with the input parameters can be inserted without clearance
   * violations. Returns false, if the shove failed. In this case the database may be damaged, so
   * that an undo becomes necessary.
   */
  boolean forced_pad(
      TileShape p_pad_shape,
      CalcFromSide p_from_side,
      int p_layer,
      int[] p_net_no_arr,
      int p_cl_type,
      boolean p_copper_sharing_allowed,
      Collection<Item> p_ignore_items,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth) {
    if (p_pad_shape.is_empty()) {
      FRLogger.warn("ShoveTraceAux.forced_pad: p_pad_shape is empty");
      return true;
    }
    if (!p_pad_shape.is_contained_in(board.get_bounding_box())) {
      this.board.set_shove_failing_obstacle(board.get_outline());
      return false;
    }
    if (!MoveDrillItemAlgo.shove_vias(
        p_pad_shape,
        p_from_side,
        p_layer,
        p_net_no_arr,
        p_cl_type,
        p_ignore_items,
        p_max_recursion_depth,
        p_max_via_recursion_depth,
        false,
        this.board)) {
      return false;
    }
    ShapeSearchTree searchTree = this.board.searchTreeManager.get_default_tree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(p_pad_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, board);
    Collection<Item> obstacles =
        searchTree.overlapping_items_with_clearance(p_pad_shape, p_layer, new int[0], p_cl_type);
    if (p_ignore_items != null) {
      obstacles.removeAll(p_ignore_items);
    }
    boolean obstaclesShovable =
        shapeEntries.store_items(obstacles, true, p_copper_sharing_allowed)
            && shapeEntries.shoveViaList.isEmpty();
    if (!obstaclesShovable) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return false;
    }
    int tracePieceCount = shapeEntries.substitute_trace_count();
    if (tracePieceCount == 0) {
      return true;
    }
    if (p_max_recursion_depth <= 0) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return false;
    }
    boolean tailsExistBefore = board.contains_trace_tails(obstacles, p_net_no_arr);
    shapeEntries.cutout_traces(obstacles);
    boolean isOrthogonalMode = p_pad_shape instanceof IntBox;
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(this.board);
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.next_substitute_trace_piece();
      if (currSubstituteTrace == null) {
        break;
      }
      if (currSubstituteTrace.first_corner().equals(currSubstituteTrace.last_corner())) {
        continue;
      }
      int[] currNetNoArr = currSubstituteTrace.netNoArr;
      for (int i = 0; i < currSubstituteTrace.tile_shape_count(); i++) {
        CalcShapeAndFromSide curr =
            new CalcShapeAndFromSide(currSubstituteTrace, i, isOrthogonalMode, false);
        if (!shoveTraceAlgo.insert(
            curr.shape,
            curr.fromSide,
            p_layer,
            currNetNoArr,
            currSubstituteTrace.clearance_class_no(),
            p_ignore_items,
            p_max_recursion_depth - 1,
            p_max_via_recursion_depth,
            0)) {
          return false;
        }
      }
      for (int i = 0; i < currSubstituteTrace.corner_count(); i++) {
        board.join_changed_area(currSubstituteTrace.polyline().corner_approx(i), p_layer);
      }
      Point[] endCorners = null;
      if (!tailsExistBefore) {
        endCorners = new Point[2];
        endCorners[0] = currSubstituteTrace.first_corner();
        endCorners[1] = currSubstituteTrace.last_corner();
      }
      board.insert_item(currSubstituteTrace);
      IntOctagon optArea;
      if (board.changedArea != null) {
        optArea = board.changedArea.get_area(p_layer);
      } else {
        optArea = null;
      }

      try {
        currSubstituteTrace.normalize(optArea);
      } catch (Exception e) {
        FRLogger.error("Couldn't normalize trace.", e);
      }

      if (!tailsExistBefore) {
        for (int i = 0; i < 2; i++) {
          Trace tail = board.get_trace_tail(endCorners[i], p_layer, currNetNoArr);
          if (tail != null) {
            board.remove_items(tail.get_connection_items(Item.StopConnectionOption.VIA));
            for (int currNetNo : currNetNoArr) {
              board.combine_traces(currNetNo);
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * Looks for a side of p_shape, so that a trace line from the shape center to the nearest point on
   * this side does not conflict with any obstacles.
   */
  CalcFromSide calc_from_side(
      TileShape p_shape, Point p_shape_center, int p_layer, int p_offset, int p_cl_class) {
    int[] emptyArr = new int[0];
    TileShape offsetShape = (TileShape) p_shape.offset(p_offset);
    for (int i = 0; i < offsetShape.border_line_count(); i++) {
      TileShape checkShape =
          calc_check_shape_for_from_side(p_shape, p_shape_center, offsetShape.border_line(i));

      if (board.check_trace_shape(checkShape, p_layer, emptyArr, p_cl_class, null)) {
        return new CalcFromSide(i, null);
      }
    }
    // try second check without clearance
    for (int i = 0; i < offsetShape.border_line_count(); i++) {
      TileShape checkShape =
          calc_check_shape_for_from_side(p_shape, p_shape_center, offsetShape.border_line(i));
      if (board.check_trace_shape(checkShape, p_layer, emptyArr, 0, null)) {
        return new CalcFromSide(i, null);
      }
    }
    return CalcFromSide.NOT_CALCULATED;
  }

  public enum CheckDrillResult {
    DRILLABLE,
    DRILLABLE_WITH_ATTACH_SMD,
    NOT_DRILLABLE
  }
}

package app.freerouting.board;

import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ClearanceMatrix;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

/** Contains internal auxiliary functions of class RoutingBoard for shoving traces */
public class ShoveTraceAlgo {

  private final RoutingBoard board;

  public ShoveTraceAlgo(RoutingBoard p_board) {
    board = p_board;
  }

  /**
   * Checks if a shove with the input parameters is possible without clearance violations The result
   * is the maximum length of a trace from the start of the line segment to the end of the line
   * segment, for which the algorithm succeeds. If the algorithm succeeds completely, the result
   * will be equal to Integer.MAX_VALUE.
   */
  public static double check(
      RoutingBoard p_board,
      LineSegment p_line_segment,
      boolean p_shove_to_the_left,
      int p_layer,
      int[] p_net_no_arr,
      int p_trace_half_width,
      int p_cl_type,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth) {
    ShapeSearchTree searchTree = p_board.searchTreeManager.get_default_tree();
    if (searchTree.is_clearance_compensation_used()) {
      p_trace_half_width += searchTree.clearance_compensation_value(p_cl_type, p_layer);
    }
    TileShape[] traceShapes = p_line_segment.to_polyline().offset_shapes(p_trace_half_width);
    if (traceShapes.length != 1) {
      FRLogger.warn("ShoveTraceAlgo.check: traceShape count 1 expected");
      return 0;
    }

    TileShape traceShape = traceShapes[0];
    if (traceShape.is_empty()) {
      FRLogger.warn("ShoveTraceAlgo.check: traceShape is empty");
      return 0;
    }
    if (!traceShape.is_contained_in(p_board.get_bounding_box())) {
      return 0;
    }
    CalcFromSide fromSide = new CalcFromSide(p_line_segment, traceShape, p_shove_to_the_left);
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(traceShape, p_layer, p_net_no_arr, p_cl_type, fromSide, p_board);
    Collection<Item> obstacles =
        searchTree.overlapping_items_with_clearance(traceShape, p_layer, new int[0], p_cl_type);
    boolean obstaclesShovable = shapeEntries.store_items(obstacles, false, true);
    if (!obstaclesShovable || shapeEntries.trace_tails_in_shape()) {
      return 0;
    }
    int tracePieceCount = shapeEntries.substitute_trace_count();

    if (shapeEntries.stack_depth() > 1) {
      return 0;
    }

    FloatPoint startCornerAppprox = p_line_segment.start_point_approx();
    FloatPoint endCornerAppprox = p_line_segment.end_point_approx();
    double segmentLength = endCornerAppprox.distance(startCornerAppprox);

    ClearanceMatrix clMatrix = p_board.rules.clearanceMatrix;

    double result = Integer.MAX_VALUE;

    // check, if the obstacle vias can be shoved

    for (Via curr_shove_via : shapeEntries.shoveViaList) {
      if (curr_shove_via.shares_net_no(p_net_no_arr)) {
        continue;
      }
      boolean shoveViaOk = false;
      if (p_max_via_recursion_depth > 0) {

        IntPoint[] newViaCenter =
            MoveDrillItemAlgo.try_shove_via_points(
                traceShape, p_layer, curr_shove_via, p_cl_type, false, p_board);

        if (newViaCenter.length == 0) {
          return 0;
        }
        Vector delta = newViaCenter[0].difference_by(curr_shove_via.get_center());
        Collection<Item> ignoreItems = new LinkedList<>();
        shoveViaOk =
            MoveDrillItemAlgo.check(
                curr_shove_via,
                delta,
                p_max_recursion_depth,
                p_max_via_recursion_depth - 1,
                ignoreItems,
                p_board,
                null);
      }

      if (!shoveViaOk) {
        FloatPoint viaCenterAppprox = curr_shove_via.get_center().to_float();
        double projection = startCornerAppprox.scalar_product(endCornerAppprox, viaCenterAppprox);
        projection /= segmentLength;
        IntBox viaBox = curr_shove_via.get_tree_shape_on_layer(searchTree, p_layer).bounding_box();
        double viaRadius = 0.5 * viaBox.max_width();
        double currOkLength = projection - viaRadius - p_trace_half_width;
        if (!searchTree.is_clearance_compensation_used()) {
          currOkLength -=
              clMatrix.get_value(p_cl_type, curr_shove_via.clearance_class_no(), p_layer, true);
        }
        if (currOkLength <= 0) {
          return 0;
        }
        result = Math.min(result, currOkLength);
      }
    }
    if (tracePieceCount == 0) {
      return result;
    }
    if (p_max_recursion_depth <= 0) {
      return 0;
    }

    Direction lineDirection = p_line_segment.get_line().direction();
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.next_substitute_trace_piece();
      if (currSubstituteTrace == null) {
        break;
      }
      for (int i = 0; i < currSubstituteTrace.tile_shape_count(); i++) {
        LineSegment currLineSegment = new LineSegment(currSubstituteTrace.polyline(), i + 1);
        if (p_shove_to_the_left) {
          // swap the line segment to get the correct shove length
          // in case it is smaller than the length of the whole line segment.
          currLineSegment = currLineSegment.opposite();
        }

        boolean isInFront = currLineSegment.get_line().direction().equals(lineDirection);
        if (isInFront) {
          double shoveOkLength =
              check(
                  p_board,
                  currLineSegment,
                  p_shove_to_the_left,
                  p_layer,
                  currSubstituteTrace.netNoArr,
                  currSubstituteTrace.get_half_width(),
                  currSubstituteTrace.clearance_class_no(),
                  p_max_recursion_depth - 1,
                  p_max_via_recursion_depth);
          if (shoveOkLength < Integer.MAX_VALUE) {
            if (shoveOkLength <= 0) {
              return 0;
            }
            double projection =
                Math.min(
                    startCornerAppprox.scalar_product(
                        endCornerAppprox, currLineSegment.start_point_approx()),
                    startCornerAppprox.scalar_product(
                        endCornerAppprox, currLineSegment.end_point_approx()));
            projection /= segmentLength;
            double currOkLength =
                shoveOkLength
                    + projection
                    - p_trace_half_width
                    - currSubstituteTrace.get_half_width();
            if (searchTree.is_clearance_compensation_used()) {
              currOkLength -=
                  searchTree.clearance_compensation_value(
                      currSubstituteTrace.clearance_class_no(), p_layer);
            } else {
              currOkLength -=
                  clMatrix.get_value(
                      p_cl_type, currSubstituteTrace.clearance_class_no(), p_layer, true);
            }
            if (currOkLength <= 0) {
              return 0;
            }
            result = Math.min(currOkLength, result);
          }
          break;
        }
      }
    }
    return result;
  }

  /**
   * Checks if a shove with the input parameters is possible without clearance violations p_dir is
   * used internally to prevent the check from bouncing back. Returns false, if the shove failed.
   */
  public boolean check(
      TileShape p_trace_shape,
      CalcFromSide p_from_side,
      Direction p_dir,
      int p_layer,
      int[] p_net_no_arr,
      int p_cl_type,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      int p_max_spring_over_recursion_depth,
      TimeLimit p_time_limit) {
    if (p_time_limit != null && p_time_limit.limit_exceeded()) {
      return false;
    }

    if (p_trace_shape.is_empty()) {
      FRLogger.warn("ShoveTraceAux.check: p_trace_shape is empty");
      return true;
    }
    if (!p_trace_shape.is_contained_in(board.get_bounding_box())) {
      this.board.set_shove_failing_obstacle(board.get_outline());
      return false;
    }
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(p_trace_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, board);
    ShapeSearchTree searchTree = this.board.searchTreeManager.get_default_tree();
    Collection<Item> obstacles =
        searchTree.overlapping_items_with_clearance(p_trace_shape, p_layer, new int[0], p_cl_type);
    obstacles.removeAll(get_ignore_items_at_tie_pins(p_trace_shape, p_layer, p_net_no_arr));
    boolean obstaclesShovable = shapeEntries.store_items(obstacles, false, true);
    if (!obstaclesShovable) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return false;
    }
    int tracePieceCount = shapeEntries.substitute_trace_count();

    if (p_net_no_arr != null && p_net_no_arr.length > 0 && !obstacles.isEmpty()) {
      StringBuilder obstacleLog = new StringBuilder();
      obstacleLog
          .append(tracePieceCount > 0 ? "[shove_check_obstacles]" : "[shove_check_obstacles_zero]")
          .append(" net=")
          .append(p_net_no_arr[0])
          .append(", shape_bb=")
          .append(p_trace_shape.bounding_box())
          .append(", tracePieceCount=")
          .append(tracePieceCount)
          .append(", obstacles=[");
      boolean first = true;
      for (Item obs : obstacles) {
        if (!first) {
          obstacleLog.append(", ");
        }
        first = false;
        obstacleLog
            .append("{id=")
            .append(obs.get_id_no())
            .append(",type=")
            .append(obs.getClass().getSimpleName());
        if (obs instanceof PolylineTrace pt) {
          obstacleLog
              .append(",nets=")
              .append(java.util.Arrays.toString(pt.netNoArr))
              .append(",fc=")
              .append(pt.first_corner())
              .append(",lc=")
              .append(pt.last_corner());
        }
        obstacleLog.append("}");
      }
      obstacleLog.append("]");
      FRLogger.trace(obstacleLog.toString());
    }

    if (shapeEntries.stack_depth() > 1) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return false;
    }
    double shapeRadius = 0.5 * p_trace_shape.bounding_box().min_width();

    // check, if the obstacle vias can be shoved

    for (Via curr_shove_via : shapeEntries.shoveViaList) {
      if (curr_shove_via.shares_net_no(p_net_no_arr)) {
        continue;
      }
      if (p_max_via_recursion_depth <= 0) {
        this.board.set_shove_failing_obstacle(curr_shove_via);
        return false;
      }
      FloatPoint currShoveViaCenter = curr_shove_via.get_center().to_float();
      IntPoint[] tryViaCenters =
          MoveDrillItemAlgo.try_shove_via_points(
              p_trace_shape, p_layer, curr_shove_via, p_cl_type, true, board);

      double maxDist =
          0.5 * curr_shove_via.get_shape_on_layer(p_layer).bounding_box().max_width() + shapeRadius;
      double maxDistSquare = maxDist * maxDist;
      boolean shoveViaOk = false;
      for (int i = 0; i < tryViaCenters.length; i++) {
        if (i == 0
            || currShoveViaCenter.distance_square(tryViaCenters[i].to_float()) <= maxDistSquare) {
          Vector delta = tryViaCenters[i].difference_by(curr_shove_via.get_center());
          Collection<Item> ignoreItems = new LinkedList<>();
          if (MoveDrillItemAlgo.check(
              curr_shove_via,
              delta,
              p_max_recursion_depth,
              p_max_via_recursion_depth - 1,
              ignoreItems,
              this.board,
              p_time_limit)) {
            shoveViaOk = true;
            break;
          }
        }
      }
      if (!shoveViaOk) {
        return false;
      }
    }

    if (tracePieceCount == 0) {
      return true;
    }
    if (p_max_recursion_depth <= 0) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return false;
    }

    boolean isOrthogonalMode = p_trace_shape instanceof IntBox;
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.next_substitute_trace_piece();
      if (currSubstituteTrace == null) {
        break;
      }
      if (p_max_spring_over_recursion_depth > 0) {
        Polyline newPolyline =
            spring_over(
                currSubstituteTrace.polyline(),
                currSubstituteTrace.get_compensated_half_width(searchTree),
                p_layer,
                currSubstituteTrace.netNoArr,
                currSubstituteTrace.clearance_class_no(),
                false,
                p_max_spring_over_recursion_depth,
                null);
        if (newPolyline == null) {
          // spring_over did not work
          return false;
        }
        if (newPolyline != currSubstituteTrace.polyline()) {
          // spring_over changed something
          --p_max_spring_over_recursion_depth;
          currSubstituteTrace.change(newPolyline);
        }
      }
      for (int i = 0; i < currSubstituteTrace.tile_shape_count(); i++) {
        Direction currDir = currSubstituteTrace.polyline().arr[i + 1].direction();
        boolean isInFront = p_dir == null || p_dir.equals(currDir);
        if (isInFront) {
          CalcShapeAndFromSide curr =
              new CalcShapeAndFromSide(currSubstituteTrace, i, isOrthogonalMode, true);
          if (!this.check(
              curr.shape,
              curr.fromSide,
              currDir,
              p_layer,
              currSubstituteTrace.netNoArr,
              currSubstituteTrace.clearance_class_no(),
              p_max_recursion_depth - 1,
              p_max_via_recursion_depth,
              p_max_spring_over_recursion_depth,
              p_time_limit)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Puts in a trace segment with the input parameters and shoves obstacles out of the way. If the
   * shove does not work, the database may be damaged. To prevent this, call check first.
   */
  public boolean insert(
      TileShape p_trace_shape,
      CalcFromSide p_from_side,
      int p_layer,
      int[] p_net_no_arr,
      int p_cl_type,
      Collection<Item> p_ignore_items,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      int p_max_spring_over_recursion_depth) {
    if (p_trace_shape.is_empty()) {
      FRLogger.warn("ShoveTraceAux.insert: p_trace_shape is empty");
      return true;
    }
    if (!p_trace_shape.is_contained_in(board.get_bounding_box())) {
      this.board.set_shove_failing_obstacle(board.get_outline());
      return false;
    }
    if (!MoveDrillItemAlgo.shove_vias(
        p_trace_shape,
        p_from_side,
        p_layer,
        p_net_no_arr,
        p_cl_type,
        p_ignore_items,
        p_max_recursion_depth,
        p_max_via_recursion_depth,
        true,
        this.board)) {
      return false;
    }
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(p_trace_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, board);
    ShapeSearchTree searchTree = this.board.searchTreeManager.get_default_tree();
    Collection<Item> obstacles =
        searchTree.overlapping_items_with_clearance(p_trace_shape, p_layer, new int[0], p_cl_type);
    obstacles.removeAll(get_ignore_items_at_tie_pins(p_trace_shape, p_layer, p_net_no_arr));
    boolean obstaclesShovable = shapeEntries.store_items(obstacles, false, true);
    if (!shapeEntries.shoveViaList.isEmpty()) {
      obstaclesShovable = false;
      this.board.set_shove_failing_obstacle(shapeEntries.shoveViaList.iterator().next());
      return false;
    }
    if (!obstaclesShovable) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return false;
    }
    int tracePieceCount = shapeEntries.substitute_trace_count();
    if (p_net_no_arr != null && p_net_no_arr.length > 0 && !obstacles.isEmpty()) {
      StringBuilder obstacleLog = new StringBuilder();
      obstacleLog
          .append(
              tracePieceCount > 0 ? "[shove_insert_obstacles]" : "[shove_insert_obstacles_zero]")
          .append(" net=")
          .append(p_net_no_arr[0])
          .append(", shape_bb=")
          .append(p_trace_shape.bounding_box())
          .append(", tracePieceCount=")
          .append(tracePieceCount)
          .append(", obstacles=[");
      boolean first = true;
      for (Item obs : obstacles) {
        if (!first) {
          obstacleLog.append(", ");
        }
        first = false;
        obstacleLog
            .append("{id=")
            .append(obs.get_id_no())
            .append(",type=")
            .append(obs.getClass().getSimpleName());
        if (obs instanceof PolylineTrace pt) {
          obstacleLog
              .append(",nets=")
              .append(java.util.Arrays.toString(pt.netNoArr))
              .append(",fc=")
              .append(pt.first_corner())
              .append(",lc=")
              .append(pt.last_corner());
        }
        obstacleLog.append("}");
      }
      obstacleLog.append("]");
      FRLogger.trace(obstacleLog.toString());
    }
    if (tracePieceCount == 0) {
      return true;
    }
    if (p_max_recursion_depth <= 0) {
      this.board.set_shove_failing_obstacle(shapeEntries.get_found_obstacle());
      return false;
    }
    boolean tailsExistBefore = board.contains_trace_tails(obstacles, p_net_no_arr);
    shapeEntries.cutout_traces(obstacles);
    boolean isOrthogonalMode = p_trace_shape instanceof IntBox;
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.next_substitute_trace_piece();
      if (currSubstituteTrace == null) {
        break;
      }
      if (currSubstituteTrace.first_corner().equals(currSubstituteTrace.last_corner())) {
        continue;
      }
      if (p_max_spring_over_recursion_depth > 0) {
        Polyline newPolyline =
            spring_over(
                currSubstituteTrace.polyline(),
                currSubstituteTrace.get_compensated_half_width(searchTree),
                p_layer,
                currSubstituteTrace.netNoArr,
                currSubstituteTrace.clearance_class_no(),
                false,
                p_max_spring_over_recursion_depth,
                null);

        if (newPolyline == null) {
          // spring_over did not work
          return false;
        }
        if (newPolyline != currSubstituteTrace.polyline()) {
          // spring_over changed something
          --p_max_spring_over_recursion_depth;
          currSubstituteTrace.change(newPolyline);
        }
      }
      int[] currNetNoArr = currSubstituteTrace.netNoArr;
      for (int i = 0; i < currSubstituteTrace.tile_shape_count(); i++) {
        CalcShapeAndFromSide curr =
            new CalcShapeAndFromSide(currSubstituteTrace, i, isOrthogonalMode, false);
        if (!this.insert(
            curr.shape,
            curr.fromSide,
            p_layer,
            currNetNoArr,
            currSubstituteTrace.clearance_class_no(),
            p_ignore_items,
            p_max_recursion_depth - 1,
            p_max_via_recursion_depth,
            p_max_spring_over_recursion_depth)) {
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

      try {
        currSubstituteTrace.normalize(board.changedArea.get_area(p_layer));
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

  Collection<Item> get_ignore_items_at_tie_pins(
      TileShape p_trace_shape, int p_layer, int[] p_net_no_arr) {
    Collection<SearchTreeObject> overlaps = this.board.overlapping_objects(p_trace_shape, p_layer);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currObject : overlaps) {
      if (currObject instanceof Pin currPin) {
        if (currPin.shares_net_no(p_net_no_arr)) {
          result.addAll(currPin.get_all_contacts(p_layer));
        }
      }
    }
    return result;
  }

  /**
   * Checks, if there are obstacle in the way of p_polyline and tries to wrap the polyline trace
   * around these obstacles in counterclock sense. Returns null, if that is not possible. Returns
   * p_polyline, if there were no obstacles If p_contact_pins != null, all pins not contained in
   * p_contact_pins are regarded as obstacles, even if they are of the own net.
   */
  private Polyline spring_over(
      Polyline p_polyline,
      int p_half_width,
      int p_layer,
      int[] p_net_no_arr,
      int p_cl_type,
      boolean p_over_connected_pins,
      int p_recursion_depth,
      Set<Pin> p_contact_pins) {
    Item foundObstacle = null;
    IntBox foundObstacleBoundingBox = null;
    ShapeSearchTree searchTree = this.board.searchTreeManager.get_default_tree();
    int[] checkNetNoArr;
    if (p_contact_pins == null) {
      checkNetNoArr = p_net_no_arr;
    } else {
      checkNetNoArr = new int[0];
    }
    for (int i = 0; i < p_polyline.arr.length - 2; i++) {
      TileShape currShape = p_polyline.offset_shape(p_half_width, i);
      Collection<Item> obstacles =
          searchTree.overlapping_items_with_clearance(currShape, p_layer, checkNetNoArr, p_cl_type);
      for (Item currItem : obstacles) {
        boolean isObstacle;
        if (currItem.shares_net_no(p_net_no_arr)) {
          // to avoid acid traps
          isObstacle =
              currItem instanceof Pin
                  && p_contact_pins != null
                  && !p_contact_pins.contains(currItem);
        } else if (currItem instanceof ConductionArea area) {
          isObstacle = area.get_is_obstacle();
        } else if (currItem instanceof ViaObstacleArea
            || currItem instanceof ComponentObstacleArea) {
          isObstacle = false;
        } else if (currItem instanceof PolylineTrace) {
          if (currItem.is_shove_fixed()) {
            isObstacle = true;
            // check for a shove fixed trace exit stub, which has to be ignored at a tie pin.
            Collection<Item> currContacts = currItem.get_normal_contacts();
            for (Item currContact : currContacts) {
              if (currContact.shares_net_no(p_net_no_arr)) {
                isObstacle = false;
              }
            }
          } else {
            // an unfixed trace can be pushed aside eventually
            isObstacle = false;
          }
        } else {
          // an unfixed via can be pushed aside eventually
          isObstacle = !currItem.is_routable();
        }

        if (isObstacle) {
          if (foundObstacle == null) {
            foundObstacle = currItem;
            foundObstacleBoundingBox = currItem.bounding_box();
          } else if (foundObstacle != currItem) {
            // check, if 1 obstacle is contained in the other obstacle and take
            // the bigger obstacle in this case.
            // That may happen in case of fixed vias inside of pins.
            IntBox currItemBoundingBox = currItem.bounding_box();
            if (foundObstacleBoundingBox.intersects(currItemBoundingBox)) {
              if (currItemBoundingBox.contains(foundObstacleBoundingBox)) {
                foundObstacle = currItem;
                foundObstacleBoundingBox = currItemBoundingBox;
              } else if (!foundObstacleBoundingBox.contains(currItemBoundingBox)) {
                return null;
              }
            }
          }
        }
      }
      if (foundObstacle != null) {
        break;
      }
    }
    if (foundObstacle == null) {
      // no obstacle in the way, nothing to do
      return p_polyline;
    }

    if (p_recursion_depth <= 0
        || foundObstacle instanceof BoardOutline
        || (foundObstacle instanceof Trace && !foundObstacle.is_shove_fixed())) {
      this.board.set_shove_failing_obstacle(foundObstacle);
      return null;
    }
    boolean trySpringOver = true;
    if (!p_over_connected_pins) {
      // Check if the obstacle has a trace contact on p_layer
      Collection<Item> contactsOnLayer = foundObstacle.get_all_contacts(p_layer);
      for (Item currContact : contactsOnLayer) {
        if (currContact instanceof Trace) {
          trySpringOver = false;
          break;
        }
      }
    }
    ConvexShape obstacleShape = null;
    if (trySpringOver) {
      if (foundObstacle instanceof ObstacleArea || foundObstacle instanceof Trace) {
        if (foundObstacle.tree_shape_count(searchTree) == 1) {
          obstacleShape = foundObstacle.get_tree_shape(searchTree, 0);
        } else {
          trySpringOver = false;
        }
      } else if (foundObstacle instanceof DrillItem found_drill_item) {
        obstacleShape = found_drill_item.get_tree_shape_on_layer(searchTree, p_layer);
      }
    }
    if (!trySpringOver) {
      this.board.set_shove_failing_obstacle(foundObstacle);
      return null;
    }
    TileShape offsetShape;
    if (searchTree.is_clearance_compensation_used()) {
      int offset = p_half_width + 1;
      offsetShape = (TileShape) obstacleShape.enlarge(offset);
    } else {
      // enlarge the shape in 2 steps  for symmetry reasons
      int offset = p_half_width + 1;
      double halfClOffset =
          0.5 * board.clearance_value(foundObstacle.clearance_class_no(), p_cl_type, p_layer);
      offsetShape = (TileShape) obstacleShape.enlarge(offset + halfClOffset);
      offsetShape = (TileShape) offsetShape.enlarge(halfClOffset);
    }
    if (this.board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE) {
      offsetShape = offsetShape.bounding_box();
    } else if (this.board.rules.get_trace_angle_restriction()
        == AngleRestriction.FORTYFIVE_DEGREE) {
      offsetShape = offsetShape.bounding_octagon();
    }

    if (offsetShape.contains_inside(p_polyline.first_corner())
        || offsetShape.contains_inside(p_polyline.last_corner())) {
      // can happen with clearance compensation off because of asymmetry in calculations with the
      // offset shapes
      this.board.set_shove_failing_obstacle(foundObstacle);
      return null;
    }
    int[][] entries = offsetShape.entrance_points(p_polyline);
    if (entries.length == 0) {
      return p_polyline; // no obstacle
    }

    if (entries.length < 2) {
      this.board.set_shove_failing_obstacle(foundObstacle);
      return null;
    }
    Polyline[] pieces =
        offsetShape.cutout(
            p_polyline); // build a circuit around the offsetShape in counter clock sense
    // from the first intersection point to the second intersection point
    int firstIntersectionSideNo = entries[0][1];
    int lastIntersectionSideNo = entries[entries.length - 1][1];
    int firstIntersectionLineNo = entries[0][0];
    int lastIntersectionLineNo = entries[entries.length - 1][0];
    int sideDiff = lastIntersectionSideNo - firstIntersectionSideNo;
    if (sideDiff < 0) {
      sideDiff += offsetShape.border_line_count();
    } else if (sideDiff == 0) {
      FloatPoint compareCorner = offsetShape.corner_approx(firstIntersectionSideNo);
      FloatPoint firstIntersection =
          p_polyline.arr[firstIntersectionLineNo].intersection_approx(
              offsetShape.border_line(firstIntersectionSideNo));
      FloatPoint secondIntersection =
          p_polyline.arr[lastIntersectionLineNo].intersection_approx(
              offsetShape.border_line(lastIntersectionSideNo));
      if (compareCorner.distance(secondIntersection) < compareCorner.distance(firstIntersection)) {
        sideDiff += offsetShape.border_line_count();
      }
    }
    Line[] substituteLines = new Line[sideDiff + 3];
    substituteLines[0] = p_polyline.arr[firstIntersectionLineNo];
    int currEdgeLineNo = firstIntersectionSideNo;

    for (int i = 1; i <= sideDiff + 1; i++) {
      substituteLines[i] = offsetShape.border_line(currEdgeLineNo);
      if (currEdgeLineNo == offsetShape.border_line_count() - 1) {
        currEdgeLineNo = 0;
      } else {
        ++currEdgeLineNo;
      }
    }
    substituteLines[sideDiff + 2] = p_polyline.arr[lastIntersectionLineNo];
    Polyline substitutePolyline = new Polyline(substituteLines);
    Polyline result = substitutePolyline;

    if (pieces.length > 0) {
      result = pieces[0].combine(substitutePolyline);
    }
    if (pieces.length > 1) {
      result = result.combine(pieces[1]);
    }
    return spring_over(
        result,
        p_half_width,
        p_layer,
        p_net_no_arr,
        p_cl_type,
        p_over_connected_pins,
        p_recursion_depth - 1,
        p_contact_pins);
  }

  /**
   * Checks, if there are obstacle in the way of p_polyline and tries to wrap the polyline trace
   * around these obstacles. Returns null, if that is not possible. Returns p_polyline, if there
   * were no obstacles This function looks contrary to the previous function for the shortest way
   * around the obstacles. If p_contact_pins != null, all pins not contained in p_contact_pins are
   * regarded as obstacles, even if they are of the own net.
   */
  Polyline spring_over_obstacles(
      Polyline p_polyline,
      int p_half_width,
      int p_layer,
      int[] p_net_no_arr,
      int p_cl_type,
      Set<Pin> p_contact_pins) {
    final int cMaxSpringOverRecursionDepth = 20;
    Polyline counterClockWiseResult =
        spring_over(
            p_polyline,
            p_half_width,
            p_layer,
            p_net_no_arr,
            p_cl_type,
            true,
            cMaxSpringOverRecursionDepth,
            p_contact_pins);
    if (counterClockWiseResult == p_polyline) {
      return p_polyline; // no obstacle
    }

    Polyline clockWiseResult =
        spring_over(
            p_polyline.reverse(),
            p_half_width,
            p_layer,
            p_net_no_arr,
            p_cl_type,
            true,
            cMaxSpringOverRecursionDepth,
            p_contact_pins);
    Polyline result = null;
    if (clockWiseResult != null && counterClockWiseResult != null) {
      if (clockWiseResult.length_approx() <= counterClockWiseResult.length_approx()) {
        result = clockWiseResult.reverse();
      } else {
        result = counterClockWiseResult;
      }

    } else if (clockWiseResult != null) {
      result = clockWiseResult.reverse();
    } else if (counterClockWiseResult != null) {
      result = counterClockWiseResult;
    }

    return result;
  }
}

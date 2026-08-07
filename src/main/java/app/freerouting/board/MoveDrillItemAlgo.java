package app.freerouting.board;

import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import java.util.Collection;
import java.util.LinkedList;

/** Contains internal auxiliary functions of class RoutingBoard for shoving vias and pins */
public final class MoveDrillItemAlgo {

  private MoveDrillItemAlgo() {}

  /**
   * Checks, if p_drill_item can be translated by p_vector by shoving obstacle traces and vias
   * aside, so that no clearance violations occur.
   */
  public static boolean check(
      DrillItem p_drill_item,
      Vector p_vector,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      Collection<Item> p_ignore_items,
      RoutingBoard p_board,
      TimeLimit p_time_limit) {

    if (p_time_limit != null && p_time_limit.limit_exceeded()) {
      return false;
    }
    if (p_drill_item.is_shove_fixed()) {
      return false;
    }

    // Check, that p_drillitem is only connected to traces.
    Collection<Item> contactList = p_drill_item.get_normal_contacts();
    for (Item currContact : contactList) {
      if (!(currContact instanceof Trace || currContact instanceof ConductionArea)) {
        return false;
      }
    }
    Collection<Item> ignoreItems;
    if (p_ignore_items == null) {
      ignoreItems = new LinkedList<>();
    } else {
      ignoreItems = p_ignore_items;
    }
    ignoreItems.add(p_drill_item);
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(p_board);
    boolean attachAllowed = false;
    if (p_drill_item instanceof Via via) {
      attachAllowed = via.attachAllowed;
    }
    ShapeSearchTree searchTree = p_board.searchTreeManager.get_default_tree();
    for (int currLayer = p_drill_item.first_layer();
        currLayer <= p_drill_item.last_layer();
        currLayer++) {
      int currInd = currLayer - p_drill_item.first_layer();
      TileShape currShape = p_drill_item.get_tree_shape(searchTree, currInd);
      if (currShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currShape.translate_by(p_vector);
      TileShape currTileShape;
      if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE) {
        currTileShape = newShape.bounding_box();
      } else {
        currTileShape = newShape.bounding_octagon();
      }
      CalcFromSide fromSide = new CalcFromSide(p_drill_item.get_center(), currTileShape);
      if (forcedPadAlgo.check_forced_pad(
              currTileShape,
              fromSide,
              currLayer,
              p_drill_item.netNoArr,
              p_drill_item.clearance_class_no(),
              attachAllowed,
              ignoreItems,
              p_max_recursion_depth,
              p_max_via_recursion_depth,
              true,
              p_time_limit)
          == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
        return false;
      }
    }
    return true;
  }

  /**
   * Translates p_drill_item by p_vector by shoving obstacle traces and vias aside, so that no
   * clearance violations occur. If p_tidy_region != null, it will be joined by the bounding
   * octagons of the translated shapes.
   */
  static boolean insert(
      DrillItem p_drill_item,
      Vector p_vector,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      IntOctagon p_tidy_region,
      RoutingBoard p_board) {
    if (p_drill_item.is_shove_fixed()) {
      return false;
    }

    boolean attachAllowed = false;
    if (p_drill_item instanceof Via via) {
      attachAllowed = via.attachAllowed;
    }
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(p_board);
    Collection<Item> ignoreItems = new LinkedList<>();
    ignoreItems.add(p_drill_item);
    ShapeSearchTree searchTree = p_board.searchTreeManager.get_default_tree();
    for (int currLayer = p_drill_item.first_layer();
        currLayer <= p_drill_item.last_layer();
        currLayer++) {
      int currInd = currLayer - p_drill_item.first_layer();
      TileShape currShape = p_drill_item.get_tree_shape(searchTree, currInd);
      if (currShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currShape.translate_by(p_vector);
      TileShape currTileShape;
      if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE) {
        currTileShape = newShape.bounding_box();
      } else {
        currTileShape = newShape.bounding_octagon();
      }
      if (p_tidy_region != null) {
        p_tidy_region = p_tidy_region.union(currTileShape.bounding_octagon());
      }
      CalcFromSide fromSide = new CalcFromSide(p_drill_item.get_center(), currTileShape);
      if (!forcedPadAlgo.forced_pad(
          currTileShape,
          fromSide,
          currLayer,
          p_drill_item.netNoArr,
          p_drill_item.clearance_class_no(),
          attachAllowed,
          ignoreItems,
          p_max_recursion_depth,
          p_max_via_recursion_depth)) {
        return false;
      }
      IntBox currBoundingBox = currShape.bounding_box();
      for (int j = 0; j < 4; j++) {
        p_board.join_changed_area(currBoundingBox.corner_approx(j), currLayer);
      }
    }
    p_drill_item.move_by(p_vector);
    return true;
  }

  /**
   * Shoves vias out of p_obstacle_shape. Returns false, if the database is damaged, so that an undo
   * is necessary afterwards.
   */
  static boolean shove_vias(
      TileShape p_obstacle_shape,
      CalcFromSide p_from_side,
      int p_layer,
      int[] p_net_no_arr,
      int p_cl_type,
      Collection<Item> p_ignore_items,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      boolean p_copper_sharing_allowed,
      RoutingBoard p_board) {
    ShapeSearchTree searchTree = p_board.searchTreeManager.get_default_tree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(
            p_obstacle_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, p_board);
    Collection<Item> obstacles =
        searchTree.overlapping_items_with_clearance(
            p_obstacle_shape, p_layer, new int[0], p_cl_type);

    if (!shapeEntries.store_items(obstacles, false, p_copper_sharing_allowed)) {
      return true;
    }
    if (p_ignore_items != null) {
      shapeEntries.shoveViaList.removeAll(p_ignore_items);
    }
    if (shapeEntries.shoveViaList.isEmpty()) {
      return true;
    }
    double shapeRadius = 0.5 * p_obstacle_shape.bounding_box().min_width();
    for (Via currVia : shapeEntries.shoveViaList) {
      if (currVia.shares_net_no(p_net_no_arr)) {
        continue;
      }
      if (p_max_via_recursion_depth <= 0) {
        return true;
      }
      IntPoint[] tryViaCenters =
          try_shove_via_points(p_obstacle_shape, p_layer, currVia, p_cl_type, true, p_board);
      IntPoint newViaCenter = null;
      double maxDist =
          0.5 * currVia.get_shape_on_layer(p_layer).bounding_box().max_width() + shapeRadius;
      double maxDistSquare = maxDist * maxDist;
      IntPoint currViaCenter = (IntPoint) currVia.get_center();
      FloatPoint checkViaCenter = currViaCenter.to_float();
      Vector relCoor = null;
      for (int i = 0; i < tryViaCenters.length; i++) {
        if (i == 0
            || checkViaCenter.distance_square(tryViaCenters[i].to_float()) <= maxDistSquare) {
          Collection<Item> ignoreItems = new LinkedList<>();
          if (p_ignore_items != null) {
            ignoreItems.addAll(p_ignore_items);
          }
          relCoor = tryViaCenters[i].difference_by(currViaCenter);
          // No time limit here because the item database is already changed.
          boolean shoveOk =
              check(
                  currVia,
                  relCoor,
                  p_max_recursion_depth,
                  p_max_via_recursion_depth - 1,
                  ignoreItems,
                  p_board,
                  null);
          if (shoveOk) {
            newViaCenter = tryViaCenters[i];
            break;
          }
        }
      }
      if (newViaCenter == null) {
        continue;
      }
      if (!insert(
          currVia, relCoor, p_max_recursion_depth, p_max_via_recursion_depth - 1, null, p_board)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Calculates possible new location for a via to shove outside p_obstacle_shape. if
   * p_extended_check is true, more than 1 possible new locations are calculated. The function is
   * used here and in ShoveTraceAlgo.check.
   */
  static IntPoint[] try_shove_via_points(
      TileShape p_obstacle_shape,
      int p_layer,
      Via p_via,
      int p_cl_class_no,
      boolean p_extended_check,
      RoutingBoard p_board) {
    ShapeSearchTree searchTree = p_board.searchTreeManager.get_default_tree();
    TileShape currViaShape = p_via.get_tree_shape_on_layer(searchTree, p_layer);
    if (currViaShape == null) {
      return new IntPoint[0];
    }
    boolean isIntOctagon = p_obstacle_shape.is_IntOctagon();
    double clearanceValue =
        p_board.clearance_value(p_cl_class_no, p_via.clearance_class_no(), p_layer);
    double shoveDistance;
    if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE
        || isIntOctagon) {
      shoveDistance = 0.5 * currViaShape.bounding_box().max_width();
      if (!searchTree.is_clearance_compensation_used()) {
        shoveDistance += clearanceValue;
      }
    } else {
      // a different algorithm is used for calculating the new via centers
      shoveDistance = 0;
      if (!searchTree.is_clearance_compensation_used()) {
        // enlarge p_obstacle_shape and currViaShape by half of the clearance value to synchronize
        // with the check algorithm in ShapeSearchTree.overlapping_tree_entries_with_clearance
        shoveDistance += 0.5 * clearanceValue;
      }
    }

    // The additional constant 2 is an empirical value for the tolerance in case of diagonal
    // shoving.
    shoveDistance += 2;

    IntPoint currViaCenter = (IntPoint) p_via.get_center();
    IntPoint[] tryViaCenters;
    int tryCount = 1;
    if (p_board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE) {
      IntBox currOffsetBox = p_obstacle_shape.bounding_box().offset(shoveDistance);
      if (p_extended_check) {
        tryCount = 2;
      }
      tryViaCenters = currOffsetBox.nearest_border_projections(currViaCenter, tryCount);
    } else if (isIntOctagon) {
      IntOctagon currOffsetOctagon = p_obstacle_shape.bounding_octagon().enlarge(shoveDistance);
      if (p_extended_check) {
        tryCount = 4;
      }

      tryViaCenters = currOffsetOctagon.nearest_border_projections(currViaCenter, tryCount);
    } else {
      TileShape currOffsetShape = (TileShape) p_obstacle_shape.enlarge(shoveDistance);
      if (!searchTree.is_clearance_compensation_used()) {
        currViaShape = (TileShape) currViaShape.enlarge(0.5 * clearanceValue);
      }
      if (p_extended_check) {
        tryCount = 4;
      }
      FloatPoint[] shoveDeltas =
          currOffsetShape.nearest_relative_outside_locations(currViaShape, tryCount);
      tryViaCenters = new IntPoint[shoveDeltas.length];
      for (int i = 0; i < tryViaCenters.length; i++) {
        Vector currDelta = shoveDeltas[i].round().difference_by(Point.ZERO);
        tryViaCenters[i] = (IntPoint) currViaCenter.translate_by(currDelta);
      }
    }
    return tryViaCenters;
  }
}

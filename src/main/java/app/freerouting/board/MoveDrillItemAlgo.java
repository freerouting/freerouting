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

    if (p_time_limit != null && p_time_limit.limitExceeded()) {
      return false;
    }
    if (p_drill_item.isShoveFixed()) {
      return false;
    }

    // Check, that p_drillitem is only connected to traces.
    Collection<Item> contactList = p_drill_item.getNormalContacts();
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
    ShapeSearchTree searchTree = p_board.searchTreeManager.getDefaultTree();
    for (int currLayer = p_drill_item.firstLayer();
        currLayer <= p_drill_item.lastLayer();
        currLayer++) {
      int currInd = currLayer - p_drill_item.firstLayer();
      TileShape currShape = p_drill_item.getTreeShape(searchTree, currInd);
      if (currShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currShape.translateBy(p_vector);
      TileShape currTileShape;
      if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        currTileShape = newShape.boundingBox();
      } else {
        currTileShape = newShape.boundingOctagon();
      }
      CalcFromSide fromSide = new CalcFromSide(p_drill_item.getCenter(), currTileShape);
      if (forcedPadAlgo.checkForcedPad(
              currTileShape,
              fromSide,
              currLayer,
              p_drill_item.netNoArr,
              p_drill_item.clearanceClassNo(),
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
    if (p_drill_item.isShoveFixed()) {
      return false;
    }

    boolean attachAllowed = false;
    if (p_drill_item instanceof Via via) {
      attachAllowed = via.attachAllowed;
    }
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(p_board);
    Collection<Item> ignoreItems = new LinkedList<>();
    ignoreItems.add(p_drill_item);
    ShapeSearchTree searchTree = p_board.searchTreeManager.getDefaultTree();
    for (int currLayer = p_drill_item.firstLayer();
        currLayer <= p_drill_item.lastLayer();
        currLayer++) {
      int currInd = currLayer - p_drill_item.firstLayer();
      TileShape currShape = p_drill_item.getTreeShape(searchTree, currInd);
      if (currShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currShape.translateBy(p_vector);
      TileShape currTileShape;
      if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        currTileShape = newShape.boundingBox();
      } else {
        currTileShape = newShape.boundingOctagon();
      }
      if (p_tidy_region != null) {
        p_tidy_region = p_tidy_region.union(currTileShape.boundingOctagon());
      }
      CalcFromSide fromSide = new CalcFromSide(p_drill_item.getCenter(), currTileShape);
      if (!forcedPadAlgo.forcedPad(
          currTileShape,
          fromSide,
          currLayer,
          p_drill_item.netNoArr,
          p_drill_item.clearanceClassNo(),
          attachAllowed,
          ignoreItems,
          p_max_recursion_depth,
          p_max_via_recursion_depth)) {
        return false;
      }
      IntBox currBoundingBox = currShape.boundingBox();
      for (int j = 0; j < 4; j++) {
        p_board.joinChangedArea(currBoundingBox.cornerApprox(j), currLayer);
      }
    }
    p_drill_item.moveBy(p_vector);
    return true;
  }

  /**
   * Shoves vias out of p_obstacle_shape. Returns false, if the database is damaged, so that an undo
   * is necessary afterwards.
   */
  static boolean shoveVias(
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
    ShapeSearchTree searchTree = p_board.searchTreeManager.getDefaultTree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(
            p_obstacle_shape, p_layer, p_net_no_arr, p_cl_type, p_from_side, p_board);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(
            p_obstacle_shape, p_layer, new int[0], p_cl_type);

    if (!shapeEntries.storeItems(obstacles, false, p_copper_sharing_allowed)) {
      return true;
    }
    if (p_ignore_items != null) {
      shapeEntries.shoveViaList.removeAll(p_ignore_items);
    }
    if (shapeEntries.shoveViaList.isEmpty()) {
      return true;
    }
    double shapeRadius = 0.5 * p_obstacle_shape.boundingBox().minWidth();
    for (Via currVia : shapeEntries.shoveViaList) {
      if (currVia.sharesNetNo(p_net_no_arr)) {
        continue;
      }
      if (p_max_via_recursion_depth <= 0) {
        return true;
      }
      IntPoint[] tryViaCenters =
          tryShoveViaPoints(p_obstacle_shape, p_layer, currVia, p_cl_type, true, p_board);
      IntPoint newViaCenter = null;
      double maxDist =
          0.5 * currVia.getShapeOnLayer(p_layer).boundingBox().maxWidth() + shapeRadius;
      double maxDistSquare = maxDist * maxDist;
      IntPoint currViaCenter = (IntPoint) currVia.getCenter();
      FloatPoint checkViaCenter = currViaCenter.toFloat();
      Vector relCoor = null;
      for (int i = 0; i < tryViaCenters.length; i++) {
        if (i == 0
            || checkViaCenter.distanceSquare(tryViaCenters[i].toFloat()) <= maxDistSquare) {
          Collection<Item> ignoreItems = new LinkedList<>();
          if (p_ignore_items != null) {
            ignoreItems.addAll(p_ignore_items);
          }
          relCoor = tryViaCenters[i].differenceBy(currViaCenter);
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
  static IntPoint[] tryShoveViaPoints(
      TileShape p_obstacle_shape,
      int p_layer,
      Via p_via,
      int p_cl_class_no,
      boolean p_extended_check,
      RoutingBoard p_board) {
    ShapeSearchTree searchTree = p_board.searchTreeManager.getDefaultTree();
    TileShape currViaShape = p_via.getTreeShapeOnLayer(searchTree, p_layer);
    if (currViaShape == null) {
      return new IntPoint[0];
    }
    boolean isIntOctagon = p_obstacle_shape.isIntOctagon();
    double clearanceValue =
        p_board.clearanceValue(p_cl_class_no, p_via.clearanceClassNo(), p_layer);
    double shoveDistance;
    if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE
        || isIntOctagon) {
      shoveDistance = 0.5 * currViaShape.boundingBox().maxWidth();
      if (!searchTree.isClearanceCompensationUsed()) {
        shoveDistance += clearanceValue;
      }
    } else {
      // a different algorithm is used for calculating the new via centers
      shoveDistance = 0;
      if (!searchTree.isClearanceCompensationUsed()) {
        // enlarge p_obstacle_shape and currViaShape by half of the clearance value to synchronize
        // with the check algorithm in ShapeSearchTree.overlapping_tree_entries_with_clearance
        shoveDistance += 0.5 * clearanceValue;
      }
    }

    // The additional constant 2 is an empirical value for the tolerance in case of diagonal
    // shoving.
    shoveDistance += 2;

    IntPoint currViaCenter = (IntPoint) p_via.getCenter();
    IntPoint[] tryViaCenters;
    int tryCount = 1;
    if (p_board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      IntBox currOffsetBox = p_obstacle_shape.boundingBox().offset(shoveDistance);
      if (p_extended_check) {
        tryCount = 2;
      }
      tryViaCenters = currOffsetBox.nearestBorderProjections(currViaCenter, tryCount);
    } else if (isIntOctagon) {
      IntOctagon currOffsetOctagon = p_obstacle_shape.boundingOctagon().enlarge(shoveDistance);
      if (p_extended_check) {
        tryCount = 4;
      }

      tryViaCenters = currOffsetOctagon.nearestBorderProjections(currViaCenter, tryCount);
    } else {
      TileShape currOffsetShape = (TileShape) p_obstacle_shape.enlarge(shoveDistance);
      if (!searchTree.isClearanceCompensationUsed()) {
        currViaShape = (TileShape) currViaShape.enlarge(0.5 * clearanceValue);
      }
      if (p_extended_check) {
        tryCount = 4;
      }
      FloatPoint[] shoveDeltas =
          currOffsetShape.nearestRelativeOutsideLocations(currViaShape, tryCount);
      tryViaCenters = new IntPoint[shoveDeltas.length];
      for (int i = 0; i < tryViaCenters.length; i++) {
        Vector currDelta = shoveDeltas[i].round().differenceBy(Point.ZERO);
        tryViaCenters[i] = (IntPoint) currViaCenter.translateBy(currDelta);
      }
    }
    return tryViaCenters;
  }
}

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

/** Contains internal auxiliary functions of class RoutingBoard for shoving vias and pins. */
public final class MoveDrillItemAlgo {

  private MoveDrillItemAlgo() {}

  /**
   * Checks, if p_drill_item can be translated by p_vector by shoving obstacle traces and vias
   * aside, so that no clearance violations occur.
   */
  public static boolean check(
      DrillItem drillItem,
      Vector vector,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      Collection<Item> ignoreItems,
      RoutingBoard board,
      TimeLimit timeLimit) {

    if (timeLimit != null && timeLimit.limitExceeded()) {
      return false;
    }
    if (drillItem.isShoveFixed()) {
      return false;
    }

    // Check, that p_drillitem is only connected to traces.
    Collection<Item> contactList = drillItem.getNormalContacts();
    for (Item currContact : contactList) {
      if (!(currContact instanceof Trace || currContact instanceof ConductionArea)) {
        return false;
      }
    }
    Collection<Item> effectiveIgnoreItems;
    if (ignoreItems == null) {
      effectiveIgnoreItems = new LinkedList<>();
    } else {
      effectiveIgnoreItems = ignoreItems;
    }
    effectiveIgnoreItems.add(drillItem);
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(board);
    boolean attachAllowed = false;
    if (drillItem instanceof Via via) {
      attachAllowed = via.attachAllowed;
    }
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    for (int currLayer = drillItem.firstLayer(); currLayer <= drillItem.lastLayer(); currLayer++) {
      int currInd = currLayer - drillItem.firstLayer();
      TileShape currShape = drillItem.getTreeShape(searchTree, currInd);
      if (currShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currShape.translateBy(vector);
      TileShape currTileShape;
      if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        currTileShape = newShape.boundingBox();
      } else {
        currTileShape = newShape.boundingOctagon();
      }
      CalcFromSide fromSide = new CalcFromSide(drillItem.getCenter(), currTileShape);
      if (forcedPadAlgo.checkForcedPad(
              currTileShape,
              fromSide,
              currLayer,
              drillItem.netNoArr,
              drillItem.clearanceClassNo(),
              attachAllowed,
              effectiveIgnoreItems,
              maxRecursionDepth,
              maxViaRecursionDepth,
              true,
              timeLimit)
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
      DrillItem drillItem,
      Vector vector,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      IntOctagon tidyRegion,
      RoutingBoard board) {
    if (drillItem.isShoveFixed()) {
      return false;
    }

    boolean attachAllowed = false;
    if (drillItem instanceof Via via) {
      attachAllowed = via.attachAllowed;
    }
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(board);
    Collection<Item> ignoreItems = new LinkedList<>();
    ignoreItems.add(drillItem);
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    for (int currLayer = drillItem.firstLayer(); currLayer <= drillItem.lastLayer(); currLayer++) {
      int currInd = currLayer - drillItem.firstLayer();
      TileShape currShape = drillItem.getTreeShape(searchTree, currInd);
      if (currShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currShape.translateBy(vector);
      TileShape currTileShape;
      if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        currTileShape = newShape.boundingBox();
      } else {
        currTileShape = newShape.boundingOctagon();
      }
      if (tidyRegion != null) {
        tidyRegion = tidyRegion.union(currTileShape.boundingOctagon());
      }
      CalcFromSide fromSide = new CalcFromSide(drillItem.getCenter(), currTileShape);
      if (!forcedPadAlgo.forcedPad(
          currTileShape,
          fromSide,
          currLayer,
          drillItem.netNoArr,
          drillItem.clearanceClassNo(),
          attachAllowed,
          ignoreItems,
          maxRecursionDepth,
          maxViaRecursionDepth)) {
        return false;
      }
      IntBox currBoundingBox = currShape.boundingBox();
      for (int j = 0; j < 4; j++) {
        board.joinChangedArea(currBoundingBox.cornerApprox(j), currLayer);
      }
    }
    drillItem.moveBy(vector);
    return true;
  }

  /**
   * Shoves vias out of p_obstacle_shape. Returns false, if the database is damaged, so that an undo
   * is necessary afterwards.
   */
  static boolean shoveVias(
      TileShape obstacleShape,
      CalcFromSide fromSide,
      int layer,
      int[] netNoArr,
      int clType,
      Collection<Item> ignoreItems,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      boolean copperSharingAllowed,
      RoutingBoard board) {
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(obstacleShape, layer, netNoArr, clType, fromSide, board);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(obstacleShape, layer, new int[0], clType);

    if (!shapeEntries.storeItems(obstacles, false, copperSharingAllowed)) {
      return true;
    }
    if (ignoreItems != null) {
      shapeEntries.shoveViaList.removeAll(ignoreItems);
    }
    if (shapeEntries.shoveViaList.isEmpty()) {
      return true;
    }
    double shapeRadius = 0.5 * obstacleShape.boundingBox().minWidth();
    for (Via currVia : shapeEntries.shoveViaList) {
      if (currVia.sharesNetNo(netNoArr)) {
        continue;
      }
      if (maxViaRecursionDepth <= 0) {
        return true;
      }
      IntPoint[] tryViaCenters =
          tryShoveViaPoints(obstacleShape, layer, currVia, clType, true, board);
      IntPoint newViaCenter = null;
      double maxDist = 0.5 * currVia.getShapeOnLayer(layer).boundingBox().maxWidth() + shapeRadius;
      double maxDistSquare = maxDist * maxDist;
      IntPoint currViaCenter = (IntPoint) currVia.getCenter();
      FloatPoint checkViaCenter = currViaCenter.toFloat();
      Vector relCoor = null;
      for (int i = 0; i < tryViaCenters.length; i++) {
        if (i == 0 || checkViaCenter.distanceSquare(tryViaCenters[i].toFloat()) <= maxDistSquare) {
          Collection<Item> localIgnoreItems = new LinkedList<>();
          if (ignoreItems != null) {
            localIgnoreItems.addAll(ignoreItems);
          }
          relCoor = tryViaCenters[i].differenceBy(currViaCenter);
          // No time limit here because the item database is already changed.
          boolean shoveOk =
              check(
                  currVia,
                  relCoor,
                  maxRecursionDepth,
                  maxViaRecursionDepth - 1,
                  localIgnoreItems,
                  board,
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
      if (!insert(currVia, relCoor, maxRecursionDepth, maxViaRecursionDepth - 1, null, board)) {
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
      TileShape obstacleShape,
      int layer,
      Via via,
      int clClassNo,
      boolean extendedCheck,
      RoutingBoard board) {
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    TileShape currViaShape = via.getTreeShapeOnLayer(searchTree, layer);
    if (currViaShape == null) {
      return new IntPoint[0];
    }
    boolean isIntOctagon = obstacleShape.isIntOctagon();
    double clearanceValue = board.clearanceValue(clClassNo, via.clearanceClassNo(), layer);
    double shoveDistance;
    if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE || isIntOctagon) {
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

    IntPoint currViaCenter = (IntPoint) via.getCenter();
    IntPoint[] tryViaCenters;
    int tryCount = 1;
    if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      IntBox currOffsetBox = obstacleShape.boundingBox().offset(shoveDistance);
      if (extendedCheck) {
        tryCount = 2;
      }
      tryViaCenters = currOffsetBox.nearestBorderProjections(currViaCenter, tryCount);
    } else if (isIntOctagon) {
      IntOctagon currOffsetOctagon = obstacleShape.boundingOctagon().enlarge(shoveDistance);
      if (extendedCheck) {
        tryCount = 4;
      }

      tryViaCenters = currOffsetOctagon.nearestBorderProjections(currViaCenter, tryCount);
    } else {
      TileShape currOffsetShape = (TileShape) obstacleShape.enlarge(shoveDistance);
      if (!searchTree.isClearanceCompensationUsed()) {
        currViaShape = (TileShape) currViaShape.enlarge(0.5 * clearanceValue);
      }
      if (extendedCheck) {
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

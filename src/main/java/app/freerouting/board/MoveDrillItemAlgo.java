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
      DrillItem pDrillItem,
      Vector pVector,
      int pMaxRecursionDepth,
      int pMaxViaRecursionDepth,
      Collection<Item> pIgnoreItems,
      RoutingBoard pBoard,
      TimeLimit pTimeLimit) {

    if (pTimeLimit != null && pTimeLimit.limitExceeded()) {
      return false;
    }
    if (pDrillItem.isShoveFixed()) {
      return false;
    }

    // Check, that p_drillitem is only connected to traces.
    Collection<Item> contactList = pDrillItem.getNormalContacts();
    for (Item currContact : contactList) {
      if (!(currContact instanceof Trace || currContact instanceof ConductionArea)) {
        return false;
      }
    }
    Collection<Item> ignoreItems;
    if (pIgnoreItems == null) {
      ignoreItems = new LinkedList<>();
    } else {
      ignoreItems = pIgnoreItems;
    }
    ignoreItems.add(pDrillItem);
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(pBoard);
    boolean attachAllowed = false;
    if (pDrillItem instanceof Via via) {
      attachAllowed = via.attachAllowed;
    }
    ShapeSearchTree searchTree = pBoard.searchTreeManager.getDefaultTree();
    for (int currLayer = pDrillItem.firstLayer();
        currLayer <= pDrillItem.lastLayer();
        currLayer++) {
      int currInd = currLayer - pDrillItem.firstLayer();
      TileShape currShape = pDrillItem.getTreeShape(searchTree, currInd);
      if (currShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currShape.translateBy(pVector);
      TileShape currTileShape;
      if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        currTileShape = newShape.boundingBox();
      } else {
        currTileShape = newShape.boundingOctagon();
      }
      CalcFromSide fromSide = new CalcFromSide(pDrillItem.getCenter(), currTileShape);
      if (forcedPadAlgo.checkForcedPad(
              currTileShape,
              fromSide,
              currLayer,
              pDrillItem.netNoArr,
              pDrillItem.clearanceClassNo(),
              attachAllowed,
              ignoreItems,
              pMaxRecursionDepth,
              pMaxViaRecursionDepth,
              true,
              pTimeLimit)
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
      DrillItem pDrillItem,
      Vector pVector,
      int pMaxRecursionDepth,
      int pMaxViaRecursionDepth,
      IntOctagon pTidyRegion,
      RoutingBoard pBoard) {
    if (pDrillItem.isShoveFixed()) {
      return false;
    }

    boolean attachAllowed = false;
    if (pDrillItem instanceof Via via) {
      attachAllowed = via.attachAllowed;
    }
    ForcedPadAlgo forcedPadAlgo = new ForcedPadAlgo(pBoard);
    Collection<Item> ignoreItems = new LinkedList<>();
    ignoreItems.add(pDrillItem);
    ShapeSearchTree searchTree = pBoard.searchTreeManager.getDefaultTree();
    for (int currLayer = pDrillItem.firstLayer();
        currLayer <= pDrillItem.lastLayer();
        currLayer++) {
      int currInd = currLayer - pDrillItem.firstLayer();
      TileShape currShape = pDrillItem.getTreeShape(searchTree, currInd);
      if (currShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currShape.translateBy(pVector);
      TileShape currTileShape;
      if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        currTileShape = newShape.boundingBox();
      } else {
        currTileShape = newShape.boundingOctagon();
      }
      if (pTidyRegion != null) {
        pTidyRegion = pTidyRegion.union(currTileShape.boundingOctagon());
      }
      CalcFromSide fromSide = new CalcFromSide(pDrillItem.getCenter(), currTileShape);
      if (!forcedPadAlgo.forcedPad(
          currTileShape,
          fromSide,
          currLayer,
          pDrillItem.netNoArr,
          pDrillItem.clearanceClassNo(),
          attachAllowed,
          ignoreItems,
          pMaxRecursionDepth,
          pMaxViaRecursionDepth)) {
        return false;
      }
      IntBox currBoundingBox = currShape.boundingBox();
      for (int j = 0; j < 4; j++) {
        pBoard.joinChangedArea(currBoundingBox.cornerApprox(j), currLayer);
      }
    }
    pDrillItem.moveBy(pVector);
    return true;
  }

  /**
   * Shoves vias out of p_obstacle_shape. Returns false, if the database is damaged, so that an undo
   * is necessary afterwards.
   */
  static boolean shoveVias(
      TileShape pObstacleShape,
      CalcFromSide pFromSide,
      int pLayer,
      int[] pNetNoArr,
      int pClType,
      Collection<Item> pIgnoreItems,
      int pMaxRecursionDepth,
      int pMaxViaRecursionDepth,
      boolean pCopperSharingAllowed,
      RoutingBoard pBoard) {
    ShapeSearchTree searchTree = pBoard.searchTreeManager.getDefaultTree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(pObstacleShape, pLayer, pNetNoArr, pClType, pFromSide, pBoard);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(pObstacleShape, pLayer, new int[0], pClType);

    if (!shapeEntries.storeItems(obstacles, false, pCopperSharingAllowed)) {
      return true;
    }
    if (pIgnoreItems != null) {
      shapeEntries.shoveViaList.removeAll(pIgnoreItems);
    }
    if (shapeEntries.shoveViaList.isEmpty()) {
      return true;
    }
    double shapeRadius = 0.5 * pObstacleShape.boundingBox().minWidth();
    for (Via currVia : shapeEntries.shoveViaList) {
      if (currVia.sharesNetNo(pNetNoArr)) {
        continue;
      }
      if (pMaxViaRecursionDepth <= 0) {
        return true;
      }
      IntPoint[] tryViaCenters =
          tryShoveViaPoints(pObstacleShape, pLayer, currVia, pClType, true, pBoard);
      IntPoint newViaCenter = null;
      double maxDist = 0.5 * currVia.getShapeOnLayer(pLayer).boundingBox().maxWidth() + shapeRadius;
      double maxDistSquare = maxDist * maxDist;
      IntPoint currViaCenter = (IntPoint) currVia.getCenter();
      FloatPoint checkViaCenter = currViaCenter.toFloat();
      Vector relCoor = null;
      for (int i = 0; i < tryViaCenters.length; i++) {
        if (i == 0 || checkViaCenter.distanceSquare(tryViaCenters[i].toFloat()) <= maxDistSquare) {
          Collection<Item> ignoreItems = new LinkedList<>();
          if (pIgnoreItems != null) {
            ignoreItems.addAll(pIgnoreItems);
          }
          relCoor = tryViaCenters[i].differenceBy(currViaCenter);
          // No time limit here because the item database is already changed.
          boolean shoveOk =
              check(
                  currVia,
                  relCoor,
                  pMaxRecursionDepth,
                  pMaxViaRecursionDepth - 1,
                  ignoreItems,
                  pBoard,
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
      if (!insert(currVia, relCoor, pMaxRecursionDepth, pMaxViaRecursionDepth - 1, null, pBoard)) {
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
      TileShape pObstacleShape,
      int pLayer,
      Via pVia,
      int pClClassNo,
      boolean pExtendedCheck,
      RoutingBoard pBoard) {
    ShapeSearchTree searchTree = pBoard.searchTreeManager.getDefaultTree();
    TileShape currViaShape = pVia.getTreeShapeOnLayer(searchTree, pLayer);
    if (currViaShape == null) {
      return new IntPoint[0];
    }
    boolean isIntOctagon = pObstacleShape.isIntOctagon();
    double clearanceValue = pBoard.clearanceValue(pClClassNo, pVia.clearanceClassNo(), pLayer);
    double shoveDistance;
    if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE || isIntOctagon) {
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

    IntPoint currViaCenter = (IntPoint) pVia.getCenter();
    IntPoint[] tryViaCenters;
    int tryCount = 1;
    if (pBoard.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      IntBox currOffsetBox = pObstacleShape.boundingBox().offset(shoveDistance);
      if (pExtendedCheck) {
        tryCount = 2;
      }
      tryViaCenters = currOffsetBox.nearestBorderProjections(currViaCenter, tryCount);
    } else if (isIntOctagon) {
      IntOctagon currOffsetOctagon = pObstacleShape.boundingOctagon().enlarge(shoveDistance);
      if (pExtendedCheck) {
        tryCount = 4;
      }

      tryViaCenters = currOffsetOctagon.nearestBorderProjections(currViaCenter, tryCount);
    } else {
      TileShape currOffsetShape = (TileShape) pObstacleShape.enlarge(shoveDistance);
      if (!searchTree.isClearanceCompensationUsed()) {
        currViaShape = (TileShape) currViaShape.enlarge(0.5 * clearanceValue);
      }
      if (pExtendedCheck) {
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

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
public final class DrillItemMover {

  private DrillItemMover() {}

  /**
   * Checks, if drillItem can be translated by vector by shoving obstacle traces and vias aside, so
   * that no clearance violations occur.
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

    // Check, that drillitem is only connected to traces.
    Collection<Item> contactList = drillItem.getNormalContacts();
    for (Item currentContact : contactList) {
      if (!(currentContact instanceof Trace || currentContact instanceof ConductionArea)) {
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
    ForcedPadRouter forcedPadRouter = new ForcedPadRouter(board);
    boolean attachAllowed = false;
    if (drillItem instanceof Via via) {
      attachAllowed = via.attachAllowed;
    }
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    for (int currentLayer = drillItem.firstLayer();
        currentLayer <= drillItem.lastLayer();
        currentLayer++) {
      int currentInd = currentLayer - drillItem.firstLayer();
      TileShape currentShape = drillItem.getTreeShape(searchTree, currentInd);
      if (currentShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currentShape.translateBy(vector);
      TileShape currentTileShape;
      if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        currentTileShape = newShape.boundingBox();
      } else {
        currentTileShape = newShape.boundingOctagon();
      }
      ShapeEntrySide fromSide = new ShapeEntrySide(drillItem.getCenter(), currentTileShape);
      if (forcedPadRouter.checkForcedPad(
              currentTileShape,
              fromSide,
              currentLayer,
              drillItem.netNumbers,
              drillItem.clearanceClassIndex(),
              attachAllowed,
              effectiveIgnoreItems,
              maxRecursionDepth,
              maxViaRecursionDepth,
              true,
              timeLimit)
          == ForcedPadRouter.CheckDrillResult.NOT_DRILLABLE) {
        return false;
      }
    }
    return true;
  }

  /**
   * Translates drillItem by vector by shoving obstacle traces and vias aside, so that no clearance
   * violations occur. If tidyRegion != null, it will be joined by the bounding octagons of the
   * translated shapes.
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
    ForcedPadRouter forcedPadRouter = new ForcedPadRouter(board);
    Collection<Item> ignoreItems = new LinkedList<>();
    ignoreItems.add(drillItem);
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    for (int currentLayer = drillItem.firstLayer();
        currentLayer <= drillItem.lastLayer();
        currentLayer++) {
      int currentInd = currentLayer - drillItem.firstLayer();
      TileShape currentShape = drillItem.getTreeShape(searchTree, currentInd);
      if (currentShape == null) {
        continue;
      }
      ConvexShape newShape = (ConvexShape) currentShape.translateBy(vector);
      TileShape currentTileShape;
      if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
        currentTileShape = newShape.boundingBox();
      } else {
        currentTileShape = newShape.boundingOctagon();
      }
      if (tidyRegion != null) {
        tidyRegion = tidyRegion.union(currentTileShape.boundingOctagon());
      }
      ShapeEntrySide fromSide = new ShapeEntrySide(drillItem.getCenter(), currentTileShape);
      if (!forcedPadRouter.forcedPad(
          currentTileShape,
          fromSide,
          currentLayer,
          drillItem.netNumbers,
          drillItem.clearanceClassIndex(),
          attachAllowed,
          ignoreItems,
          maxRecursionDepth,
          maxViaRecursionDepth)) {
        return false;
      }
      IntBox currentBoundingBox = currentShape.boundingBox();
      for (int j = 0; j < 4; j++) {
        board.joinChangedArea(currentBoundingBox.cornerApprox(j), currentLayer);
      }
    }
    drillItem.moveBy(vector);
    return true;
  }

  /**
   * Shoves vias out of obstacleShape. Returns false, if the database is damaged, so that an undo is
   * necessary afterwards.
   */
  static boolean shoveVias(
      TileShape obstacleShape,
      ShapeEntrySide fromSide,
      int layer,
      int[] netNumbers,
      int clearanceClassIndex,
      Collection<Item> ignoreItems,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      boolean copperSharingAllowed,
      RoutingBoard board) {
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(
            obstacleShape, layer, netNumbers, clearanceClassIndex, fromSide, board);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(
            obstacleShape, layer, new int[0], clearanceClassIndex);

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
    for (Via currentVia : shapeEntries.shoveViaList) {
      if (currentVia.sharesNetNo(netNumbers)) {
        continue;
      }
      if (maxViaRecursionDepth <= 0) {
        return true;
      }
      IntPoint[] tryViaCenters =
          tryShoveViaPoints(obstacleShape, layer, currentVia, clearanceClassIndex, true, board);
      IntPoint newViaCenter = null;
      double maxDist =
          0.5 * currentVia.getShapeOnLayer(layer).boundingBox().maxWidth() + shapeRadius;
      double maxDistSquare = maxDist * maxDist;
      IntPoint currentViaCenter = (IntPoint) currentVia.getCenter();
      FloatPoint checkViaCenter = currentViaCenter.toFloat();
      Vector relCoor = null;
      for (int i = 0; i < tryViaCenters.length; i++) {
        if (i == 0 || checkViaCenter.distanceSquare(tryViaCenters[i].toFloat()) <= maxDistSquare) {
          Collection<Item> localIgnoreItems = new LinkedList<>();
          if (ignoreItems != null) {
            localIgnoreItems.addAll(ignoreItems);
          }
          relCoor = tryViaCenters[i].differenceBy(currentViaCenter);
          // No time limit here because the item database is already changed.
          boolean shoveOk =
              check(
                  currentVia,
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
      if (!insert(currentVia, relCoor, maxRecursionDepth, maxViaRecursionDepth - 1, null, board)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Calculates possible new location for a via to shove outside obstacleShape. if extendedCheck is
   * true, more than 1 possible new locations are calculated. The function is used here and in
   * TraceShover.check.
   */
  static IntPoint[] tryShoveViaPoints(
      TileShape obstacleShape,
      int layer,
      Via via,
      int clearanceClassIndex,
      boolean extendedCheck,
      RoutingBoard board) {
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    TileShape currentViaShape = via.getTreeShapeOnLayer(searchTree, layer);
    if (currentViaShape == null) {
      return new IntPoint[0];
    }
    boolean isIntOctagon = obstacleShape.isIntOctagon();
    double clearanceValue =
        board.clearanceValue(clearanceClassIndex, via.clearanceClassIndex(), layer);
    double shoveDistance;
    if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE || isIntOctagon) {
      shoveDistance = 0.5 * currentViaShape.boundingBox().maxWidth();
      if (!searchTree.isClearanceCompensationUsed()) {
        shoveDistance += clearanceValue;
      }
    } else {
      // a different algorithm is used for calculating the new via centers
      shoveDistance = 0;
      if (!searchTree.isClearanceCompensationUsed()) {
        // enlarge obstacleShape and currentViaShape by half of the clearance value to
        // synchronize
        // with the check algorithm in ShapeSearchTree.overlapping_tree_entries_with_clearance
        shoveDistance += 0.5 * clearanceValue;
      }
    }

    // The additional constant 2 is an empirical value for the tolerance in case of diagonal
    // shoving.
    shoveDistance += 2;

    IntPoint currentViaCenter = (IntPoint) via.getCenter();
    IntPoint[] tryViaCenters;
    int tryCount = 1;
    if (board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      IntBox currentOffsetBox = obstacleShape.boundingBox().offset(shoveDistance);
      if (extendedCheck) {
        tryCount = 2;
      }
      tryViaCenters = currentOffsetBox.nearestBorderProjections(currentViaCenter, tryCount);
    } else if (isIntOctagon) {
      IntOctagon currentOffsetOctagon = obstacleShape.boundingOctagon().enlarge(shoveDistance);
      if (extendedCheck) {
        tryCount = 4;
      }

      tryViaCenters = currentOffsetOctagon.nearestBorderProjections(currentViaCenter, tryCount);
    } else {
      TileShape currentOffsetShape = (TileShape) obstacleShape.enlarge(shoveDistance);
      if (!searchTree.isClearanceCompensationUsed()) {
        currentViaShape = (TileShape) currentViaShape.enlarge(0.5 * clearanceValue);
      }
      if (extendedCheck) {
        tryCount = 4;
      }
      FloatPoint[] shoveDeltas =
          currentOffsetShape.nearestRelativeOutsideLocations(currentViaShape, tryCount);
      tryViaCenters = new IntPoint[shoveDeltas.length];
      for (int i = 0; i < tryViaCenters.length; i++) {
        Vector currentDelta = shoveDeltas[i].round().differenceBy(Point.ZERO);
        tryViaCenters[i] = (IntPoint) currentViaCenter.translateBy(currentDelta);
      }
    }
    return tryViaCenters;
  }
}

package app.freerouting.board;

import app.freerouting.autoroute.maze.AutorouteControl.ExpansionCostFactor;
import app.freerouting.board.optimize.TraceTightener;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.TileShape;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Mutating routing-board operations that maintain the changed-area contract. */
public final class RoutingBoardOperations {

  private static final int PULL_TIGHT_TIME_LIMIT = 2000;

  private final RoutingBoard board;

  RoutingBoardOperations(RoutingBoard board) {
    this.board = board;
  }

  void startMarkingChangedArea() {
    if (board.changedArea == null) {
      board.changedArea = new ChangedArea(board.getLayerCount());
    }
  }

  void joinChangedArea(FloatPoint point, int layer) {
    if (board.changedArea != null) {
      board.changedArea.join(point, layer);
    }
  }

  void markAllChangedArea() {
    startMarkingChangedArea();
    FloatPoint[] boardCorners = new FloatPoint[4];
    boardCorners[0] = board.boundingBox.ll.toFloat();
    boardCorners[1] = new FloatPoint(board.boundingBox.ur.x, board.boundingBox.ll.y);
    boardCorners[2] = board.boundingBox.ur.toFloat();
    boardCorners[3] = new FloatPoint(board.boundingBox.ll.x, board.boundingBox.ur.y);
    for (int layer = 0; layer < board.getLayerCount(); layer++) {
      for (FloatPoint boardCorner : boardCorners) {
        joinChangedArea(boardCorner, layer);
      }
    }
  }

  void optChangedArea(
      int[] onlyNetNoArr,
      IntOctagon clipShape,
      int accuracy,
      ExpansionCostFactor[] traceCosts,
      Stoppable stoppableThread,
      int timeLimit,
      app.freerouting.geometry.planar.Point keepPoint,
      int keepPointLayer) {
    if (board.changedArea == null) {
      return;
    }
    if (clipShape != IntOctagon.EMPTY) {
      TraceTightener pullTightAlgo =
          TraceTightener.getInstance(
              board,
              onlyNetNoArr,
              clipShape,
              accuracy,
              stoppableThread,
              timeLimit,
              keepPoint,
              keepPointLayer);
      pullTightAlgo.optChangedArea(traceCosts);
    }
    board.joinGraphicsUpdateBox(board.changedArea.surroundingBox());
    board.changedArea = null;
  }

  boolean removeItemsAndPullTight(
      Collection<Item> itemList, int tidyWidth, int pullTightAccuracy) {
    boolean result = true;
    IntOctagon tidyRegion;
    boolean calculateTidyRegion;
    if (tidyWidth < Integer.MAX_VALUE) {
      tidyRegion = IntOctagon.EMPTY;
      calculateTidyRegion = tidyWidth > 0;
    } else {
      tidyRegion = null;
      calculateTidyRegion = false;
    }
    startMarkingChangedArea();
    Set<Integer> changedNets = new TreeSet<>();
    for (Item currentItem : itemList) {
      if (currentItem.isDeletionForbidden() || currentItem.isUserFixed()) {
        result = false;
      } else {
        for (int i = 0; i < currentItem.tileShapeCount(); i++) {
          TileShape currentShape = currentItem.getTileShape(i);
          board.changedArea.join(currentShape, currentItem.shapeLayer(i));
          if (calculateTidyRegion) {
            tidyRegion = tidyRegion.union(currentShape.boundingOctagon());
          }
        }
        board.removeItem(currentItem);
        for (int i = 0; i < currentItem.netCount(); i++) {
          changedNets.add(currentItem.getNetNumber(i));
        }
      }
    }
    for (Integer currentNetNumber : changedNets) {
      board.combineTraces(currentNetNumber);
    }
    if (calculateTidyRegion) {
      tidyRegion = tidyRegion.enlarge(tidyWidth);
    }
    board.optChangedArea(
        new int[0], tidyRegion, pullTightAccuracy, null, null, PULL_TIGHT_TIME_LIMIT);
    return result;
  }
}

package app.freerouting.board.facade;

import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import java.util.Collection;
import java.util.Set;

/** Search and clearance queries specific to routing-board operations. */
public final class RoutingBoardSearchFacade {

  private final RoutingBoard board;

  RoutingBoardSearchFacade(RoutingBoard board) {
    this.board = board;
  }

  double checkTraceSegment(
      Point fromPoint,
      Point toPoint,
      int layer,
      int[] netNumbers,
      int traceHalfWidth,
      int clClassNo,
      boolean onlyNotShovableObstacles) {
    if (fromPoint.equals(toPoint)) {
      return 0;
    }
    Polyline currentPolyline = new Polyline(fromPoint, toPoint);
    LineSegment currentLineSegment = new LineSegment(currentPolyline, 1);
    return checkTraceSegment(
        currentLineSegment, layer, netNumbers, traceHalfWidth, clClassNo, onlyNotShovableObstacles);
  }

  double checkTraceSegment(
      LineSegment lineSegment,
      int layer,
      int[] netNumbers,
      int traceHalfWidth,
      int clClassNo,
      boolean onlyNotShovableObstacles) {
    Polyline checkPolyline = lineSegment.toPolyline();
    if (checkPolyline.lines.length != 3) {
      return 0;
    }
    TileShape shapeToCheck = checkPolyline.offsetShape(traceHalfWidth, 0);
    FloatPoint fromPoint = lineSegment.startPointApprox();
    FloatPoint toPoint = lineSegment.endPointApprox();
    double lineLength = toPoint.distance(fromPoint);
    double okLength = Integer.MAX_VALUE;
    ShapeSearchTree defaultTree = board.searchTreeManager.getDefaultTree();

    Collection<TreeEntry> obstacleEntries =
        defaultTree.overlappingTreeEntriesWithClearance(shapeToCheck, layer, netNumbers, clClassNo);

    for (TreeEntry currentObstacleEntry : obstacleEntries) {
      if (!(currentObstacleEntry.object instanceof Item currentObstacle)) {
        continue;
      }
      if (onlyNotShovableObstacles
          && currentObstacle.isRoutable()
          && !currentObstacle.isShoveFixed()) {
        continue;
      }
      TileShape currentObstacleShape =
          currentObstacleEntry.object.getTreeShape(
              defaultTree, currentObstacleEntry.shapeIndexInObject);
      TileShape currentOffsetShape;
      FloatPoint nearestObstaclePoint;
      double shortenValue;
      if (defaultTree.isClearanceCompensationUsed()) {
        currentOffsetShape = shapeToCheck;
        shortenValue =
            traceHalfWidth
                + board.rules.clearanceMatrix.clearanceCompensationValue(
                    currentObstacle.clearanceClassIndex(), layer);
      } else {
        int clearanceValue =
            board.clearanceValue(currentObstacle.clearanceClassIndex(), clClassNo, layer);
        currentOffsetShape = (TileShape) shapeToCheck.offset(clearanceValue);
        shortenValue = traceHalfWidth + clearanceValue;
      }
      TileShape intersection = currentObstacleShape.intersection(currentOffsetShape);
      if (intersection.isEmpty()) {
        continue;
      }
      nearestObstaclePoint = intersection.nearestPointApprox(fromPoint);
      double projection = fromPoint.scalarProduct(toPoint, nearestObstaclePoint) / lineLength;
      projection = Math.max(0.0, projection - shortenValue - 1);
      if (projection < okLength) {
        okLength = projection;
        if (okLength <= 0) {
          return 0;
        }
      }
    }
    return okLength;
  }

  boolean checkMoveItem(Item item, Vector vector, Collection<Item> ignoreItems) {
    int netCount = item.netNumbers.length;
    if (netCount > 1) {
      return false;
    }
    int contactCount = 0;
    if (item instanceof Connectable) {
      contactCount = item.getAllContacts().size();
    }
    if (item instanceof Trace && contactCount > 0) {
      return false;
    }
    if (ignoreItems != null) {
      ignoreItems.add(item);
    }
    for (int i = 0; i < item.tileShapeCount(); i++) {
      TileShape movedShape = (TileShape) item.getTileShape(i).translateBy(vector);
      if (!movedShape.isContainedIn(board.boundingBox)) {
        return false;
      }
      Set<Item> obstacles =
          board.overlappingItemsWithClearance(
              movedShape, item.shapeLayer(i), item.netNumbers, item.clearanceClassIndex());
      for (Item currentItem : obstacles) {
        if (ignoreItems != null) {
          if (!ignoreItems.contains(currentItem) && currentItem.isObstacle(item)) {
            return false;
          }
        } else if (currentItem != item && currentItem.isObstacle(item)) {
          return false;
        }
      }
    }
    return true;
  }

  boolean checkChangeNet(Item item, int newNetNo) {
    int[] netNumbers = new int[] {newNetNo};
    for (int i = 0; i < item.tileShapeCount(); i++) {
      TileShape currentShape = item.getTileShape(i);
      Set<Item> obstacles =
          board.overlappingItemsWithClearance(
              currentShape, item.shapeLayer(i), netNumbers, item.clearanceClassIndex());
      for (Item currentObject : obstacles) {
        if (currentObject != item
            && currentObject instanceof Connectable connectable
            && !connectable.containsNet(newNetNo)) {
          return false;
        }
      }
    }
    return true;
  }

  Item pickNearestRoutingItem(Point location, int layer, Item fromItem) {
    TileShape pointShape = TileShape.getInstance(location);
    Collection<Item> foundItems = board.overlappingItems(pointShape, layer);
    FloatPoint pickLocation = location.toFloat();
    double minDist = Integer.MAX_VALUE;
    Item nearestItem = null;
    Set<Item> ignoreSet = null;
    for (Item currentItem : foundItems) {
      if (!currentItem.isConnectable()) {
        continue;
      }
      boolean candidateFound = false;
      double currentDistance = 0;
      if (currentItem instanceof PolylineTrace currentTrace) {
        if (layer < 0 || currentTrace.getLayer() == layer) {
          if (nearestItem instanceof DrillItem) {
            continue;
          }
          int traceRadius = currentTrace.getHalfWidth();
          currentDistance = currentTrace.polyline().distance(pickLocation);
          if (currentDistance < minDist && currentDistance <= traceRadius) {
            candidateFound = true;
          }
        }
      } else if (currentItem instanceof DrillItem currentDrillItem) {
        if (layer < 0 || currentDrillItem.isOnLayer(layer)) {
          FloatPoint drillItemCenter = currentDrillItem.getCenter().toFloat();
          currentDistance = drillItemCenter.distance(pickLocation);
          if (currentDistance < minDist || nearestItem instanceof Trace) {
            candidateFound = true;
          }
        }
      } else if (currentItem instanceof ConductionArea currentArea) {
        if ((layer < 0 || currentArea.getLayer() == layer) && nearestItem == null) {
          candidateFound = true;
          currentDistance = Integer.MAX_VALUE;
        }
      }
      if (candidateFound) {
        if (fromItem != null) {
          if (ignoreSet == null) {
            ignoreSet = fromItem.getConnectedSet(-1);
          }
          if (ignoreSet.contains(currentItem)) {
            continue;
          }
        }
        minDist = currentDistance;
        nearestItem = currentItem;
      }
    }
    return nearestItem;
  }
}

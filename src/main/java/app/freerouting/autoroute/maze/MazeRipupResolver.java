package app.freerouting.autoroute.maze;

import app.freerouting.autoroute.expansion.CompleteExpansionRoom;
import app.freerouting.autoroute.expansion.ExpansionDoor;
import app.freerouting.autoroute.expansion.ObstacleExpansionRoom;
import app.freerouting.autoroute.path.Connection;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Resolves whether maze expansion may rip up an obstacle and calculates its cost. */
final class MazeRipupResolver {

  private static final double FANOUT_COST_CONSTANT = 20000;

  private final MazeSearchEngine search;

  MazeRipupResolver(MazeSearchEngine search) {
    this.search = search;
  }

  static double calcFanoutViaRipupCostFactor(Trace trace) {
    Collection<Item> currentEndContacts;
    for (int i = 0; i < 2; i++) {
      if (i == 0) {
        currentEndContacts = trace.getStartContacts();
      } else {
        currentEndContacts = trace.getEndContacts();
      }
      if (currentEndContacts.size() != 1) {
        continue;
      }
      Item currentTraceContact = currentEndContacts.iterator().next();
      boolean protectFanoutVia = false;
      if (currentTraceContact instanceof Pin
          && currentTraceContact.firstLayer() == currentTraceContact.lastLayer()) {
        protectFanoutVia = true;
      } else if (currentTraceContact instanceof PolylineTrace contactTrace
          && currentTraceContact.getFixedState() == FixedState.SHOVE_FIXED) {
        if (contactTrace.cornerCount() == 2) {
          protectFanoutVia = true;
        }
      }

      if (protectFanoutVia) {
        double fanoutViaCostFactor = trace.getHalfWidth() / trace.getLength();
        fanoutViaCostFactor *= fanoutViaCostFactor;
        fanoutViaCostFactor *= FANOUT_COST_CONSTANT;
        return Math.max(fanoutViaCostFactor, 1);
      }
    }
    return 1;
  }

  /**
   * Checks whether the next room can be ripped and returns its cost, or -1 when it cannot be
   * ripped.
   */
  int checkRipup(MazeListElement listElement, Item obstacleItem, boolean doorIsSmall) {
    AutorouteControl ctrl = search.ctrl;
    if (!obstacleItem.isRoutable()) {
      return -1;
    }
    if (doorIsSmall && !enterThroughSmallDoor(listElement, obstacleItem)) {
      return -1;
    }
    CompleteExpansionRoom previousRoom = listElement.door.otherRoom(listElement.nextRoom);
    boolean roomWasShoved = listElement.adjustment != MazeSearchElement.Adjustment.NONE;
    Item previousItem = null;
    if (previousRoom instanceof ObstacleExpansionRoom room) {
      previousItem = room.getItem();
    }
    if (roomWasShoved) {
      if (previousItem != null
          && previousItem != obstacleItem
          && previousItem.sharesNet(obstacleItem)) {
        return -1;
      }
    } else if (previousItem == obstacleItem) {
      return MazeSearchEngine.ALREADY_RIPPED_COSTS;
    }

    double fanoutViaCostFactor = 1.0;
    double costFactor = 1;
    boolean preserveFanoutProtection =
        !ctrl.removeUnconnectedVias
            && ctrl.ripupCosts <= (ctrl.settings.getStartRipupCosts() * 2);
    if (obstacleItem instanceof Trace obstacleTrace) {
      costFactor = obstacleTrace.getHalfWidth();
      if (preserveFanoutProtection) {
        fanoutViaCostFactor = calcFanoutViaRipupCostFactor(obstacleTrace);
      }
    } else if (obstacleItem instanceof Via) {
      boolean lookIfFanoutVia = preserveFanoutProtection;
      Collection<Item> contactList = obstacleItem.getNormalContacts();
      int contactCount = 0;
      for (Item currentContact : contactList) {
        if (!(currentContact instanceof Trace obstacleTrace) || currentContact.isUserFixed()) {
          return -1;
        }
        ++contactCount;
        costFactor = Math.max(costFactor, obstacleTrace.getHalfWidth());
        if (lookIfFanoutVia && !ctrl.isFanout) {
          double currentFanoutViaCostFactor = calcFanoutViaRipupCostFactor(obstacleTrace);
          if (currentFanoutViaCostFactor > 1) {
            fanoutViaCostFactor = currentFanoutViaCostFactor;
            lookIfFanoutVia = false;
          }
        }
      }
      if (fanoutViaCostFactor <= 1) {
        costFactor *= 0.5 * Math.max(contactCount - 1, 0);
      }
    }

    double ripupCost = ctrl.ripupCosts * costFactor;
    double detour = 1;
    double traceLength = 0;
    double minTraceLength = 0;
    int itemCount = 0;
    String connectionItemIds = "[]";
    if (fanoutViaCostFactor <= 1 && !ctrl.isFanout) {
      Connection obstacleConnection = Connection.get(obstacleItem);
      if (obstacleConnection != null) {
        detour = obstacleConnection.getDetour();
        traceLength = obstacleConnection.traceLength();
        itemCount = obstacleConnection.itemList.size();
        if (obstacleConnection.startPoint != null && obstacleConnection.endPoint != null) {
          minTraceLength =
              obstacleConnection
                  .startPoint
                  .toFloat()
                  .distance(obstacleConnection.endPoint.toFloat());
        }
        StringBuilder sb = new StringBuilder("[");
        for (Item connectionItem : obstacleConnection.itemList) {
          if (sb.length() > 1) {
            sb.append(",");
          }
          sb.append(connectionItem.getId());
        }
        sb.append("]");
        connectionItemIds = sb.toString();
      }
    }
    boolean randomize = ctrl.ripupPassNo >= 4 && ctrl.ripupPassNo % 3 != 0;
    if (randomize) {
      double randomNumber = search.randomGenerator.nextDouble();
      double randomFactor = 0.5 + randomNumber * randomNumber;
      detour *= randomFactor;
    }
    ripupCost /= detour;
    ripupCost *= fanoutViaCostFactor;
    int result = Math.max((int) ripupCost, 1);
    final int maxRipupCosts = Integer.MAX_VALUE / 100;
    result = Math.min(result, maxRipupCosts);
    int[] nets = new int[obstacleItem.netCount()];
    for (int i = 0; i < nets.length; i++) {
      nets[i] = obstacleItem.getNetNumber(i);
    }
    FRLogger.trace(
        "CHECK_RIPUP net="
            + ctrl.netNumber
            + ", obstacle_id="
            + obstacleItem.getId()
            + ", obstacle_nets="
            + java.util.Arrays.toString(nets)
            + ", connectionItems="
            + connectionItemIds
            + ", halfWidth="
            + costFactor
            + ", ripupCosts="
            + ctrl.ripupCosts
            + ", traceLength="
            + traceLength
            + ", minTraceLength="
            + minTraceLength
            + ", itemCount="
            + itemCount
            + ", detour="
            + detour
            + ", result="
            + result);
    return result;
  }

  /** Checks entering a thick room from a via or trace through a small door after ripup. */
  boolean checkLeavingRippedItem(MazeListElement listElement) {
    if (!(listElement.door instanceof ExpansionDoor currentDoor)) {
      return false;
    }
    CompleteExpansionRoom fromRoom = currentDoor.otherRoom(listElement.nextRoom);
    if (!(fromRoom instanceof ObstacleExpansionRoom obstacleRoom)) {
      return false;
    }
    Item currentItem = obstacleRoom.getItem();
    if (!currentItem.isRoutable()) {
      return false;
    }
    return enterThroughSmallDoor(listElement, currentItem);
  }

  /**
   * Checks whether a door can be entered while ignoring the obstacle item and its directly
   * connected items.
   */
  private boolean enterThroughSmallDoor(MazeListElement listElement, Item ignoreItem) {
    if (listElement.door.getDimension() != 1) {
      return false;
    }
    TileShape doorShape = listElement.door.getShape();
    Line doorLine = null;
    FloatPoint prevCorner = doorShape.cornerApprox(0);
    int cornerCount = doorShape.borderLineCount();
    for (int i = 1; i < cornerCount; i++) {
      FloatPoint nextCorner = doorShape.cornerApprox(i);
      if (nextCorner.distanceSquare(prevCorner) > 1) {
        doorLine = doorShape.borderLine(i - 1);
        break;
      }
      prevCorner = nextCorner;
    }
    if (doorLine == null) {
      return false;
    }

    IntPoint doorCenter = doorShape.centreOfGravity().round();
    int currentLayer = listElement.nextRoom.getLayer();
    int checkRadius =
        search.ctrl.compensatedTraceHalfWidth[currentLayer] + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
    Line[] lines = new Line[3];
    lines[0] = doorLine.translate(checkRadius);
    lines[1] = new Line(doorCenter, doorLine.direction().turn45Degree(2));
    lines[2] = doorLine.translate(-checkRadius);

    Polyline checkPolyline = new Polyline(lines);
    TileShape checkShape = checkPolyline.offsetShape(checkRadius, 0);
    int[] ignoreNetNos = new int[1];
    ignoreNetNos[0] = search.ctrl.netNumber;
    Set<SearchTreeObject> overlappingObjects = new TreeSet<>();
    search.autorouteEngine.autorouteSearchTree.overlappingObjects(
        checkShape, currentLayer, ignoreNetNos, overlappingObjects);

    for (SearchTreeObject currentObject : overlappingObjects) {
      if (!(currentObject instanceof Item currentItem) || currentItem == ignoreItem) {
        continue;
      }
      if (!currentItem.sharesNet(ignoreItem)) {
        return false;
      }
      if (!currentItem.getNormalContacts().contains(ignoreItem)) {
        return false;
      }
    }
    return true;
  }
}

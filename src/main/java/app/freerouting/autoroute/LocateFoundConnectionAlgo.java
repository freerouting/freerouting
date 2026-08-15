package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;

/** Locates and constructs trace connection geometries from maze search backtrack paths. */
public abstract class LocateFoundConnectionAlgo {

  /** The new items implementing the found connection. */
  public final Collection<ResultItem> connectionItems;

  /** The start item of the new routed connection. */
  public final Item startItem;

  /** The layer of the connection to the start item. */
  public final int startLayer;

  /** The destination item of the new routed connection. */
  public final Item targetItem;

  /** The layer of the connection to the target item. */
  public final int targetLayer;

  /**
   * The array of backtrack doors from the destination to the start of a found connection of the.
   * maze search algorithm.
   */
  protected final BacktrackElement[] backtrackArray;

  protected final AutorouteControl ctrl;
  protected final AngleRestriction angleRestriction;
  protected final TargetItemExpansionDoor startDoor;
  protected FloatPoint currentFromPoint;
  protected FloatPoint previousFromPoint;
  protected int currentTraceLayer;
  protected int currentFromDoorIndex;
  protected int currentToDoorIndex;
  protected int currentTargetDoorIndex;
  protected TileShape currentTargetShape;

  /** Creates a new instance of LocateFoundConnectionAlgo. */
  protected LocateFoundConnectionAlgo(
      MazeSearchAlgo.Result mazeSearchResult,
      AutorouteControl ctrl,
      ShapeSearchTree searchTree,
      AngleRestriction angleRestriction,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts) {
    this.ctrl = ctrl;
    this.angleRestriction = angleRestriction;
    Collection<BacktrackElement> backtrackList =
        backtrack(mazeSearchResult, rippedItemList, ripupCosts, ctrl.netNo);
    this.backtrackArray = new BacktrackElement[backtrackList.size()];
    Iterator<BacktrackElement> it = backtrackList.iterator();
    for (int i = 0; i < backtrackArray.length; i++) {
      this.backtrackArray[i] = it.next();
    }
    if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
      FRLogger.trace(
          "compare_trace_backtrack_raw net="
              + this.ctrl.netNo
              + ", size="
              + this.backtrackArray.length);
      for (int i = 0; i < this.backtrackArray.length; i++) {
        BacktrackElement element = this.backtrackArray[i];
        String nextRoomType =
            element.nextRoom != null ? element.nextRoom.getClass().getSimpleName() : "null";
        FRLogger.trace(
            "compare_trace_backtrack_raw net="
                + this.ctrl.netNo
                + ", idx="
                + i
                + ", door_type="
                + element.door.getClass().getSimpleName()
                + ", section="
                + element.sectionNoOfDoor
                + ", next_room_type="
                + nextRoomType);
      }
    }
    this.connectionItems = new LinkedList<>();
    BacktrackElement startInfo = this.backtrackArray[backtrackArray.length - 1];
    if (!(startInfo.door instanceof TargetItemExpansionDoor)) {
      FRLogger.warn("LocateFoundConnectionAlgo: ItemExpansionDoor expected for startInfo.door");
      this.startItem = null;
      this.startLayer = 0;
      this.targetItem = null;
      this.targetLayer = 0;
      this.startDoor = null;
      return;
    }
    this.startDoor = (TargetItemExpansionDoor) startInfo.door;
    this.startItem = startDoor.item;
    this.startLayer = startDoor.room.getLayer();

    this.currentFromDoorIndex = 0;
    boolean atFanoutEnd = false;
    if (mazeSearchResult.destinationDoor
        instanceof TargetItemExpansionDoor currentDestinationDoor) {
      this.targetItem = currentDestinationDoor.item;
      this.targetLayer = currentDestinationDoor.room.getLayer();

      this.currentFromPoint = calculateStartingPoint(currentDestinationDoor, searchTree);
    } else if (mazeSearchResult.destinationDoor instanceof ExpansionDrill currentDrill) {
      // may happen only in case of fanout
      this.targetItem = null;
      this.currentFromPoint = currentDrill.location.toFloat();
      this.targetLayer = currentDrill.firstLayer + mazeSearchResult.sectionNoOfDoor;
      atFanoutEnd = true;
    } else {
      FRLogger.warn("LocateFoundConnectionAlgo: unexpected type of destinationDoor");
      this.targetItem = null;
      this.targetLayer = 0;
      return;
    }
    this.currentTraceLayer = this.targetLayer;
    this.previousFromPoint = this.currentFromPoint;

    boolean connectionDone = false;
    while (!connectionDone) {
      boolean layerChanged = false;
      if (atFanoutEnd) {
        // do not increase this.currentTargetDoorIndex
        layerChanged = true;
      } else {
        this.currentTargetDoorIndex = this.currentFromDoorIndex + 1;
        while (currentTargetDoorIndex < this.backtrackArray.length && !layerChanged) {
          if (this.backtrackArray[this.currentTargetDoorIndex].door instanceof ExpansionDrill) {
            layerChanged = true;
          } else {
            ++this.currentTargetDoorIndex;
          }
        }
      }
      if (layerChanged) {
        // the next trace leads to a via
        ExpansionDrill currentTargetDrill =
            (ExpansionDrill) this.backtrackArray[this.currentTargetDoorIndex].door;
        this.currentTargetShape = TileShape.getInstance(currentTargetDrill.location);
      } else {
        // the next trace leads to the final target
        connectionDone = true;
        this.currentTargetDoorIndex = this.backtrackArray.length - 1;
        TileShape targetShape =
            ((Connectable) startItem).getTraceConnectionShape(searchTree, startDoor.treeEntryNo);
        this.currentTargetShape = targetShape.intersection(startDoor.room.getShape());
        if (this.currentTargetShape.dimension() >= 2) {
          // the target is a conduction area, make a save connection
          // by shrinking the shape by the trace halfwidth.
          double traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[startDoor.room.getLayer()];
          TileShape shrinkedShape = (TileShape) this.currentTargetShape.offset(-traceHalfWidth);
          if (!shrinkedShape.isEmpty()) {
            this.currentTargetShape = shrinkedShape;
          }
        }
      }
      this.currentToDoorIndex = this.currentFromDoorIndex + 1;
      ResultItem nextTrace = this.calculateNextTrace(layerChanged, atFanoutEnd);
      atFanoutEnd = false;
      this.connectionItems.add(nextTrace);
    }
  }

  /** Returns a new Instance of LocateFoundConnectionAlgo or null, if p_destination_door is null. */
  public static LocateFoundConnectionAlgo getInstance(
      MazeSearchAlgo.Result mazeSearchResult,
      AutorouteControl ctrl,
      ShapeSearchTree searchTree,
      AngleRestriction angleRestriction,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts) {
    if (mazeSearchResult == null) {
      return null;
    }
    LocateFoundConnectionAlgo result;
    if (angleRestriction == AngleRestriction.NINETY_DEGREE
        || angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result =
          new LocateFoundConnectionAlgo45Degree(
              mazeSearchResult, ctrl, searchTree, angleRestriction, rippedItemList, ripupCosts);
    } else {
      result =
          new LocateFoundConnectionAlgoAnyAngle(
              mazeSearchResult, ctrl, searchTree, angleRestriction, rippedItemList, ripupCosts);
    }
    return result;
  }

  /**
   * Calculates the starting point of the next trace on p_from_door.item. The implementation is not
   * yet optimal for starting points on traces or areas.
   */
  private static FloatPoint calculateStartingPoint(
      TargetItemExpansionDoor fromDoor, ShapeSearchTree searchTree) {
    TileShape connectionShape =
        ((Connectable) fromDoor.item).getTraceConnectionShape(searchTree, fromDoor.treeEntryNo);
    connectionShape = connectionShape.intersection(fromDoor.room.getShape());
    return connectionShape.centreOfGravity().round().toFloat();
  }

  /**
   * Creates a list of doors by backtracking from p_destination_door to the start door. Returns
   * null, if p_destination_door is null.
   */
  private static Collection<BacktrackElement> backtrack(
      MazeSearchAlgo.Result mazeSearchResult,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int netNo) {
    if (mazeSearchResult == null) {
      return null;
    }
    Collection<BacktrackElement> result = new LinkedList<>();
    CompleteExpansionRoom currentNextRoom = null;
    ExpandableObject currentBacktrackDoor = mazeSearchResult.destinationDoor;
    MazeSearchElement currentMazeSearchElement =
        currentBacktrackDoor.getMazeSearchElement(mazeSearchResult.sectionNoOfDoor);
    boolean debugBacktrack = netNo == 98;
    if (debugBacktrack) {
      String destType = currentBacktrackDoor.getClass().getSimpleName();
      FRLogger.trace(
          "BACKTRACK_START net="
              + netNo
              + ", dest_type="
              + destType
              + ", dest_section="
              + mazeSearchResult.sectionNoOfDoor
              + ", dest_room_ripped="
              + currentMazeSearchElement.roomRipped);
    }
    if (currentBacktrackDoor instanceof TargetItemExpansionDoor door) {
      currentNextRoom = door.room;
    } else if (currentBacktrackDoor instanceof ExpansionDrill currentDrill) {
      currentNextRoom =
          currentDrill.roomArr[currentDrill.firstLayer + mazeSearchResult.sectionNoOfDoor];
      if (currentMazeSearchElement.roomRipped) {
        for (CompleteExpansionRoom tmpRoom : currentDrill.roomArr) {
          if (tmpRoom instanceof ObstacleExpansionRoom room) {
            rippedItemList.add(room.getItem());
            if (ripupCosts != null) {
              ripupCosts.put(room.getItem(), currentMazeSearchElement.ripupCost);
            }
          }
        }
      }
    }
    BacktrackElement currentBacktrackElement =
        new BacktrackElement(
            currentBacktrackDoor, mazeSearchResult.sectionNoOfDoor, currentNextRoom);
    int step = 0;
    for (; ; ) {
      result.add(currentBacktrackElement);
      currentBacktrackDoor = currentMazeSearchElement.backtrackDoor;
      if (currentBacktrackDoor == null) {
        break;
      }
      int currentSectionNo = currentMazeSearchElement.sectionNoOfBacktrackDoor;
      if (currentSectionNo >= currentBacktrackDoor.mazeSearchElementCount()) {
        FRLogger.warn("LocateFoundConnectionAlgo: currentSectionNo to big");
        currentSectionNo = currentBacktrackDoor.mazeSearchElementCount() - 1;
      }
      if (currentBacktrackDoor instanceof ExpansionDrill currentDrill) {
        currentNextRoom = currentDrill.roomArr[currentSectionNo];
      } else {
        currentNextRoom = currentBacktrackDoor.otherRoom(currentNextRoom);
      }
      currentMazeSearchElement = currentBacktrackDoor.getMazeSearchElement(currentSectionNo);
      currentBacktrackElement =
          new BacktrackElement(currentBacktrackDoor, currentSectionNo, currentNextRoom);
      if (debugBacktrack) {
        String doorType = currentBacktrackDoor.getClass().getSimpleName();
        String nextRoomType =
            currentNextRoom != null ? currentNextRoom.getClass().getSimpleName() : "null";
        int obstacleId = -1;
        if (currentNextRoom instanceof ObstacleExpansionRoom obst) {
          obstacleId = obst.getItem().getIdNo();
        }
        FRLogger.trace(
            "BACKTRACK_STEP net="
                + netNo
                + ", step="
                + step
                + ", door_type="
                + doorType
                + ", section="
                + currentSectionNo
                + ", roomRipped="
                + currentMazeSearchElement.roomRipped
                + ", ripupCost="
                + currentMazeSearchElement.ripupCost
                + ", next_room_type="
                + nextRoomType
                + ", obstacle_id="
                + obstacleId);
      }
      if (currentMazeSearchElement.roomRipped) {
        if (currentNextRoom instanceof ObstacleExpansionRoom room) {
          rippedItemList.add(room.getItem());
          if (ripupCosts != null) {
            ripupCosts.put(room.getItem(), currentMazeSearchElement.ripupCost);
          }
        }
      }
      step++;
    }
    return result;
  }

  private static FloatPoint ninetyDegreeCorner(
      FloatPoint fromPoint, FloatPoint toPoint, boolean horizontalFirst) {
    double x;
    double y;
    if (horizontalFirst) {
      x = toPoint.x;
      y = fromPoint.y;
    } else {
      x = fromPoint.x;
      y = toPoint.y;
    }
    return new FloatPoint(x, y);
  }

  private static FloatPoint fortyfiveDegreeCorner(
      FloatPoint fromPoint, FloatPoint toPoint, boolean horizontalFirst) {
    double absDx = Math.abs(toPoint.x - fromPoint.x);
    double absDy = Math.abs(toPoint.y - fromPoint.y);
    double x;
    double y;

    if (absDx <= absDy) {
      if (horizontalFirst) {
        x = toPoint.x;
        if (toPoint.y >= fromPoint.y) {
          y = fromPoint.y + absDx;
        } else {
          y = fromPoint.y - absDx;
        }
      } else {
        x = fromPoint.x;
        if (toPoint.y > fromPoint.y) {
          y = toPoint.y - absDx;
        } else {
          y = toPoint.y + absDx;
        }
      }
    } else {
      if (horizontalFirst) {
        y = fromPoint.y;
        if (toPoint.x > fromPoint.x) {
          x = toPoint.x - absDy;
        } else {
          x = toPoint.x + absDy;
        }
      } else {
        y = toPoint.y;
        if (toPoint.x > fromPoint.x) {
          x = fromPoint.x + absDy;
        } else {
          x = fromPoint.x - absDy;
        }
      }
    }
    return new FloatPoint(x, y);
  }

  /**
   * Calculates an additional corner, so that for the lines from p_from_point to the result corner
   * and from the result corner to p_to_point p_angle_restriction is fulfilled.
   */
  static FloatPoint calculateAdditionalCorner(
      FloatPoint fromPoint,
      FloatPoint toPoint,
      boolean horizontalFirst,
      AngleRestriction angleRestriction) {
    FloatPoint result;
    if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
      result = ninetyDegreeCorner(fromPoint, toPoint, horizontalFirst);
    } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result = fortyfiveDegreeCorner(fromPoint, toPoint, horizontalFirst);
    } else {
      result = toPoint;
    }
    return result;
  }

  /**
   * Calculates the next trace of the connection under construction. Returns null, if all traces are
   * returned.
   */
  private ResultItem calculateNextTrace(boolean layerChanged, boolean atFanoutEnd) {
    Collection<FloatPoint> cornerList = new LinkedList<>();
    cornerList.add(this.currentFromPoint);
    if (!atFanoutEnd) {
      FloatPoint adjustedStartCorner = this.adjustStartCorner();
      if (adjustedStartCorner != this.currentFromPoint) {
        FloatPoint addCorner =
            calculateAdditionalCorner(
                this.currentFromPoint, adjustedStartCorner, true, this.angleRestriction);
        cornerList.add(addCorner);
        cornerList.add(adjustedStartCorner);
        this.previousFromPoint = this.currentFromPoint;
        this.currentFromPoint = adjustedStartCorner;
      }
    }
    FloatPoint prevCorner = this.currentFromPoint;
    for (; ; ) {
      Collection<FloatPoint> nextCorners = calculateNextTraceCorners();
      if (nextCorners.isEmpty()) {
        break;
      }
      for (FloatPoint currentNextCorner : nextCorners) {
        if (currentNextCorner != prevCorner) {
          cornerList.add(currentNextCorner);
          this.previousFromPoint = this.currentFromPoint;
          this.currentFromPoint = currentNextCorner;
          prevCorner = currentNextCorner;
        }
      }
    }

    int nextLayer = this.currentTraceLayer;
    if (layerChanged) {
      this.currentFromDoorIndex = this.currentTargetDoorIndex + 1;
      CompleteExpansionRoom nextRoom = this.backtrackArray[this.currentFromDoorIndex].nextRoom;
      if (nextRoom != null) {
        nextLayer = nextRoom.getLayer();
      }
    }

    // Round the new trace corners to Integer.
    Collection<IntPoint> roundedCornerList = new LinkedList<>();
    IntPoint prevPoint = null;
    for (FloatPoint corner : cornerList) {
      IntPoint currentPoint = corner.round();
      if (!currentPoint.equals(prevPoint)) {
        roundedCornerList.add(currentPoint);
        prevPoint = currentPoint;
      }
    }

    // Construct the result item
    IntPoint[] cornerArr = new IntPoint[roundedCornerList.size()];
    Iterator<IntPoint> it2 = roundedCornerList.iterator();
    for (int i = 0; i < cornerArr.length; i++) {
      cornerArr[i] = it2.next();
    }
    ResultItem result = new ResultItem(cornerArr, this.currentTraceLayer);
    if (this.ctrl.netNo == 33 || this.ctrl.netNo == 66 || this.ctrl.netNo == 67) {
      IntPoint first = cornerArr.length > 0 ? cornerArr[0] : null;
      IntPoint last = cornerArr.length > 0 ? cornerArr[cornerArr.length - 1] : null;
      FRLogger.trace(
          "compare_trace_next_trace_raw net="
              + this.ctrl.netNo
              + ", traceLayer="
              + this.currentTraceLayer
              + ", nextLayer="
              + nextLayer
              + ", cornerCount="
              + cornerArr.length
              + ", first="
              + first
              + ", last="
              + last
              + ", from_door="
              + this.currentFromDoorIndex
              + ", to_door="
              + this.currentToDoorIndex
              + ", target_door="
              + this.currentTargetDoorIndex);
    }
    this.currentTraceLayer = nextLayer;
    return result;
  }

  /**
   * Returns the next list of corners for the construction of the trace in calculate_next_trace. If
   * the result is empty, the trace is already completed.
   */
  protected abstract Collection<FloatPoint> calculateNextTraceCorners();

  /** Emits optional diagnostics for the backtrack rooms. */
  public void emitDiagnostics(AutorouteDiagnostic.Sink sink, double intensity) {
    if (sink == null || intensity <= 0) {
      return;
    }
    for (int i = 0; i < backtrackArray.length; i++) {
      CompleteExpansionRoom nextRoom = backtrackArray[i].nextRoom;
      if (nextRoom != null) {
        nextRoom.emitDiagnostic(sink, intensity);
      }
      ExpandableObject nextDoor = backtrackArray[i].door;
      if (nextDoor instanceof ExpansionDrill drill) {
        drill.emitDiagnostic(sink, intensity);
      }
    }
  }

  /**
   * Adjusts the start corner, so that a trace starting at this corner is completely contained in
   * the start room.
   */
  private FloatPoint adjustStartCorner() {
    if (this.currentFromDoorIndex < 0) {
      return this.currentFromPoint;
    }
    BacktrackElement currentFromInfo = this.backtrackArray[this.currentFromDoorIndex];
    if (currentFromInfo.nextRoom == null) {
      return this.currentFromPoint;
    }
    double traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[this.currentTraceLayer];
    TileShape shrinkedRoomShape =
        (TileShape) currentFromInfo.nextRoom.getShape().offset(-traceHalfWidth);
    if (shrinkedRoomShape.isEmpty() || shrinkedRoomShape.contains(this.currentFromPoint)) {
      return this.currentFromPoint;
    }
    return shrinkedRoomShape.nearestPointApprox(this.currentFromPoint).round().toFloat();
  }

  /**
   * Type of a single item in the result list connectionItems. Used to create a new PolylineTrace.
   */
  protected static class ResultItem {

    public final IntPoint[] corners;
    public final int layer;

    public ResultItem(IntPoint[] corners, int layer) {
      this.corners = corners;
      this.layer = layer;
    }
  }

  /**
   * Type of the elements of the list returned by this.backtrack(). Next_room is the common room of
   * the current door and the next door in the backtrack list.
   */
  protected static final class BacktrackElement {

    public final ExpandableObject door;
    public final int sectionNoOfDoor;
    public final CompleteExpansionRoom nextRoom;

    private BacktrackElement(
        ExpandableObject door, int sectionNoOfDoor, CompleteExpansionRoom room) {
      this.door = door;
      this.sectionNoOfDoor = sectionNoOfDoor;
      this.nextRoom = room;
    }
  }
}

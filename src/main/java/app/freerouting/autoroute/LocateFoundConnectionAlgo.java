package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.SortedSet;

public abstract class LocateFoundConnectionAlgo {

  /** The new items implementing the found connection */
  public final Collection<ResultItem> connectionItems;

  /** The start item of the new routed connection */
  public final Item startItem;

  /** The layer of the connection to the start item */
  public final int startLayer;

  /** The destination item of the new routed connection */
  public final Item targetItem;

  /** The layer of the connection to the target item */
  public final int targetLayer;

  /**
   * The array of backtrack doors from the destination to the start of a found connection of the
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

  /** Creates a new instance of LocateFoundConnectionAlgo */
  protected LocateFoundConnectionAlgo(
      MazeSearchAlgo.Result pMazeSearchResult,
      AutorouteControl pCtrl,
      ShapeSearchTree pSearchTree,
      AngleRestriction pAngleRestriction,
      SortedSet<Item> pRippedItemList,
      Map<Item, Integer> pRipupCosts) {
    this.ctrl = pCtrl;
    this.angleRestriction = pAngleRestriction;
    Collection<BacktrackElement> backtrackList =
        backtrack(pMazeSearchResult, pRippedItemList, pRipupCosts, pCtrl.netNo);
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
    if (pMazeSearchResult.destinationDoor
        instanceof TargetItemExpansionDoor curr_destination_door) {
      this.targetItem = curr_destination_door.item;
      this.targetLayer = curr_destination_door.room.getLayer();

      this.currentFromPoint = calculateStartingPoint(curr_destination_door, pSearchTree);
    } else if (pMazeSearchResult.destinationDoor instanceof ExpansionDrill currDrill) {
      // may happen only in case of fanout
      this.targetItem = null;
      this.currentFromPoint = currDrill.location.toFloat();
      this.targetLayer = currDrill.firstLayer + pMazeSearchResult.sectionNoOfDoor;
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
            ((Connectable) startItem).getTraceConnectionShape(pSearchTree, startDoor.treeEntryNo);
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
      MazeSearchAlgo.Result pMazeSearchResult,
      AutorouteControl pCtrl,
      ShapeSearchTree pSearchTree,
      AngleRestriction pAngleRestriction,
      SortedSet<Item> pRippedItemList,
      Map<Item, Integer> pRipupCosts) {
    if (pMazeSearchResult == null) {
      return null;
    }
    LocateFoundConnectionAlgo result;
    if (pAngleRestriction == AngleRestriction.NINETY_DEGREE
        || pAngleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result =
          new LocateFoundConnectionAlgo45Degree(
              pMazeSearchResult,
              pCtrl,
              pSearchTree,
              pAngleRestriction,
              pRippedItemList,
              pRipupCosts);
    } else {
      result =
          new LocateFoundConnectionAlgoAnyAngle(
              pMazeSearchResult,
              pCtrl,
              pSearchTree,
              pAngleRestriction,
              pRippedItemList,
              pRipupCosts);
    }
    return result;
  }

  /**
   * Calculates the starting point of the next trace on p_from_door.item. The implementation is not
   * yet optimal for starting points on traces or areas.
   */
  private static FloatPoint calculateStartingPoint(
      TargetItemExpansionDoor pFromDoor, ShapeSearchTree pSearchTree) {
    TileShape connectionShape =
        ((Connectable) pFromDoor.item).getTraceConnectionShape(pSearchTree, pFromDoor.treeEntryNo);
    connectionShape = connectionShape.intersection(pFromDoor.room.getShape());
    return connectionShape.centreOfGravity().round().toFloat();
  }

  /**
   * Creates a list of doors by backtracking from p_destination_door to the start door. Returns
   * null, if p_destination_door is null.
   */
  private static Collection<BacktrackElement> backtrack(
      MazeSearchAlgo.Result pMazeSearchResult,
      SortedSet<Item> pRippedItemList,
      Map<Item, Integer> pRipupCosts,
      int pNetNo) {
    if (pMazeSearchResult == null) {
      return null;
    }
    Collection<BacktrackElement> result = new LinkedList<>();
    CompleteExpansionRoom currNextRoom = null;
    ExpandableObject currBacktrackDoor = pMazeSearchResult.destinationDoor;
    MazeSearchElement currMazeSearchElement =
        currBacktrackDoor.getMazeSearchElement(pMazeSearchResult.sectionNoOfDoor);
    boolean debugBacktrack = pNetNo == 98;
    if (debugBacktrack) {
      String destType = currBacktrackDoor.getClass().getSimpleName();
      FRLogger.trace(
          "BACKTRACK_START net="
              + pNetNo
              + ", dest_type="
              + destType
              + ", dest_section="
              + pMazeSearchResult.sectionNoOfDoor
              + ", dest_room_ripped="
              + currMazeSearchElement.roomRipped);
    }
    if (currBacktrackDoor instanceof TargetItemExpansionDoor door) {
      currNextRoom = door.room;
    } else if (currBacktrackDoor instanceof ExpansionDrill currDrill) {
      currNextRoom = currDrill.roomArr[currDrill.firstLayer + pMazeSearchResult.sectionNoOfDoor];
      if (currMazeSearchElement.roomRipped) {
        for (CompleteExpansionRoom tmpRoom : currDrill.roomArr) {
          if (tmpRoom instanceof ObstacleExpansionRoom room) {
            pRippedItemList.add(room.getItem());
            if (pRipupCosts != null) {
              pRipupCosts.put(room.getItem(), currMazeSearchElement.ripupCost);
            }
          }
        }
      }
    }
    BacktrackElement currBacktrackElement =
        new BacktrackElement(currBacktrackDoor, pMazeSearchResult.sectionNoOfDoor, currNextRoom);
    int step = 0;
    for (; ; ) {
      result.add(currBacktrackElement);
      currBacktrackDoor = currMazeSearchElement.backtrackDoor;
      if (currBacktrackDoor == null) {
        break;
      }
      int currSectionNo = currMazeSearchElement.sectionNoOfBacktrackDoor;
      if (currSectionNo >= currBacktrackDoor.mazeSearchElementCount()) {
        FRLogger.warn("LocateFoundConnectionAlgo: currSectionNo to big");
        currSectionNo = currBacktrackDoor.mazeSearchElementCount() - 1;
      }
      if (currBacktrackDoor instanceof ExpansionDrill currDrill) {
        currNextRoom = currDrill.roomArr[currSectionNo];
      } else {
        currNextRoom = currBacktrackDoor.otherRoom(currNextRoom);
      }
      currMazeSearchElement = currBacktrackDoor.getMazeSearchElement(currSectionNo);
      currBacktrackElement = new BacktrackElement(currBacktrackDoor, currSectionNo, currNextRoom);
      if (debugBacktrack) {
        String doorType = currBacktrackDoor.getClass().getSimpleName();
        String nextRoomType =
            currNextRoom != null ? currNextRoom.getClass().getSimpleName() : "null";
        int obstacleId = -1;
        if (currNextRoom instanceof ObstacleExpansionRoom obst) {
          obstacleId = obst.getItem().getIdNo();
        }
        FRLogger.trace(
            "BACKTRACK_STEP net="
                + pNetNo
                + ", step="
                + step
                + ", door_type="
                + doorType
                + ", section="
                + currSectionNo
                + ", roomRipped="
                + currMazeSearchElement.roomRipped
                + ", ripupCost="
                + currMazeSearchElement.ripupCost
                + ", next_room_type="
                + nextRoomType
                + ", obstacle_id="
                + obstacleId);
      }
      if (currMazeSearchElement.roomRipped) {
        if (currNextRoom instanceof ObstacleExpansionRoom room) {
          pRippedItemList.add(room.getItem());
          if (pRipupCosts != null) {
            pRipupCosts.put(room.getItem(), currMazeSearchElement.ripupCost);
          }
        }
      }
      step++;
    }
    return result;
  }

  private static FloatPoint ninetyDegreeCorner(
      FloatPoint pFromPoint, FloatPoint pToPoint, boolean pHorizontalFirst) {
    double x;
    double y;
    if (pHorizontalFirst) {
      x = pToPoint.x;
      y = pFromPoint.y;
    } else {
      x = pFromPoint.x;
      y = pToPoint.y;
    }
    return new FloatPoint(x, y);
  }

  private static FloatPoint fortyfiveDegreeCorner(
      FloatPoint pFromPoint, FloatPoint pToPoint, boolean pHorizontalFirst) {
    double absDx = Math.abs(pToPoint.x - pFromPoint.x);
    double absDy = Math.abs(pToPoint.y - pFromPoint.y);
    double x;
    double y;

    if (absDx <= absDy) {
      if (pHorizontalFirst) {
        x = pToPoint.x;
        if (pToPoint.y >= pFromPoint.y) {
          y = pFromPoint.y + absDx;
        } else {
          y = pFromPoint.y - absDx;
        }
      } else {
        x = pFromPoint.x;
        if (pToPoint.y > pFromPoint.y) {
          y = pToPoint.y - absDx;
        } else {
          y = pToPoint.y + absDx;
        }
      }
    } else {
      if (pHorizontalFirst) {
        y = pFromPoint.y;
        if (pToPoint.x > pFromPoint.x) {
          x = pToPoint.x - absDy;
        } else {
          x = pToPoint.x + absDy;
        }
      } else {
        y = pToPoint.y;
        if (pToPoint.x > pFromPoint.x) {
          x = pFromPoint.x + absDy;
        } else {
          x = pFromPoint.x - absDy;
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
      FloatPoint pFromPoint,
      FloatPoint pToPoint,
      boolean pHorizontalFirst,
      AngleRestriction pAngleRestriction) {
    FloatPoint result;
    if (pAngleRestriction == AngleRestriction.NINETY_DEGREE) {
      result = ninetyDegreeCorner(pFromPoint, pToPoint, pHorizontalFirst);
    } else if (pAngleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result = fortyfiveDegreeCorner(pFromPoint, pToPoint, pHorizontalFirst);
    } else {
      result = pToPoint;
    }
    return result;
  }

  /**
   * Calculates the next trace of the connection under construction. Returns null, if all traces are
   * returned.
   */
  private ResultItem calculateNextTrace(boolean pLayerChanged, boolean pAtFanoutEnd) {
    Collection<FloatPoint> cornerList = new LinkedList<>();
    cornerList.add(this.currentFromPoint);
    if (!pAtFanoutEnd) {
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
      for (FloatPoint currNextCorner : nextCorners) {
        if (currNextCorner != prevCorner) {
          cornerList.add(currNextCorner);
          this.previousFromPoint = this.currentFromPoint;
          this.currentFromPoint = currNextCorner;
          prevCorner = currNextCorner;
        }
      }
    }

    int nextLayer = this.currentTraceLayer;
    if (pLayerChanged) {
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
      IntPoint currPoint = corner.round();
      if (!currPoint.equals(prevPoint)) {
        roundedCornerList.add(currPoint);
        prevPoint = currPoint;
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

  /** Test display of the baktrack rooms. */
  public void draw(Graphics pGraphics, GraphicsContext pGraphicsContext) {
    for (int i = 0; i < backtrackArray.length; i++) {
      CompleteExpansionRoom nextRoom = backtrackArray[i].nextRoom;
      if (nextRoom != null) {
        nextRoom.draw(pGraphics, pGraphicsContext, 0.2);
      }
      ExpandableObject nextDoor = backtrackArray[i].door;
      if (nextDoor instanceof ExpansionDrill drill) {
        drill.draw(pGraphics, pGraphicsContext, 0.2);
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
    BacktrackElement currFromInfo = this.backtrackArray[this.currentFromDoorIndex];
    if (currFromInfo.nextRoom == null) {
      return this.currentFromPoint;
    }
    double traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[this.currentTraceLayer];
    TileShape shrinkedRoomShape =
        (TileShape) currFromInfo.nextRoom.getShape().offset(-traceHalfWidth);
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

    public ResultItem(IntPoint[] pCorners, int pLayer) {
      corners = pCorners;
      layer = pLayer;
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
        ExpandableObject pDoor, int pSectionNoOfDoor, CompleteExpansionRoom pRoom) {
      door = pDoor;
      sectionNoOfDoor = pSectionNoOfDoor;
      nextRoom = pRoom;
    }
  }
}

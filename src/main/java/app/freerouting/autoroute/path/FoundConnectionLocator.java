package app.freerouting.autoroute.path;

import app.freerouting.autoroute.AutorouteDiagnostic;
import app.freerouting.autoroute.drill.ExpansionDrill;
import app.freerouting.autoroute.expansion.CompleteExpansionRoom;
import app.freerouting.autoroute.expansion.ExpandableObject;
import app.freerouting.autoroute.expansion.ObstacleExpansionRoom;
import app.freerouting.autoroute.expansion.TargetItemExpansionDoor;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.autoroute.maze.MazeSearchElement;
import app.freerouting.autoroute.maze.MazeSearchEngine;
import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.DrillItem;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.Predicate;

/** Locates and constructs trace connection geometries from maze search backtrack paths. */
public abstract class FoundConnectionLocator {

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
  protected final ShapeSearchTree searchTree;
  protected final AngleRestriction angleRestriction;
  protected final TargetItemExpansionDoor startDoor;
  protected FloatPoint currentFromPoint;
  protected FloatPoint previousFromPoint;
  protected int currentTraceLayer;
  protected int currentFromDoorIndex;
  protected int currentToDoorIndex;
  protected int currentTargetDoorIndex;
  protected TileShape currentTargetShape;

  /** Creates a new instance of FoundConnectionLocator. */
  protected FoundConnectionLocator(
      MazeSearchEngine.Result mazeSearchResult,
      AutorouteControl ctrl,
      ShapeSearchTree searchTree,
      AngleRestriction angleRestriction,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts) {
    this.ctrl = ctrl;
    this.searchTree = searchTree;
    this.angleRestriction = angleRestriction;
    Collection<BacktrackElement> backtrackList =
        backtrack(mazeSearchResult, rippedItemList, ripupCosts, ctrl.netNumber);
    this.backtrackArray = new BacktrackElement[backtrackList.size()];
    Iterator<BacktrackElement> it = backtrackList.iterator();
    for (int i = 0; i < backtrackArray.length; i++) {
      this.backtrackArray[i] = it.next();
    }
    if (this.ctrl.netNumber == 33 || this.ctrl.netNumber == 66 || this.ctrl.netNumber == 67) {
      FRLogger.trace(
          "compare_trace_backtrack_raw net="
              + this.ctrl.netNumber
              + ", size="
              + this.backtrackArray.length);
      for (int i = 0; i < this.backtrackArray.length; i++) {
        BacktrackElement element = this.backtrackArray[i];
        String nextRoomType =
            element.nextRoom != null ? element.nextRoom.getClass().getSimpleName() : "null";
        FRLogger.trace(
            "compare_trace_backtrack_raw net="
                + this.ctrl.netNumber
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
      FRLogger.warn("FoundConnectionLocator: ItemExpansionDoor expected for startInfo.door");
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

      this.currentFromPoint = calculateStartingPoint(currentDestinationDoor, searchTree, this.ctrl);
    } else if (mazeSearchResult.destinationDoor instanceof ExpansionDrill currentDrill) {
      // may happen only in case of fanout
      this.targetItem = null;
      this.currentFromPoint = currentDrill.location.toFloat();
      this.targetLayer = currentDrill.firstLayer + mazeSearchResult.sectionNoOfDoor;
      atFanoutEnd = true;
    } else {
      FRLogger.warn("FoundConnectionLocator: unexpected type of destinationDoor");
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
        TileShape boundedShape = calculateWidthSafeTargetShape(startDoor, targetShape, this.ctrl);
        if (boundedShape.dimension() >= 2 && !(startItem instanceof DrillItem)) {
          // the target is a conduction area, make a safe connection
          // by shrinking the shape by the trace halfwidth.
          double traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[startDoor.room.getLayer()];
          TileShape shrinkedShape = (TileShape) boundedShape.offset(-traceHalfWidth);
          if (!shrinkedShape.isEmpty()) {
            boundedShape = shrinkedShape;
          }
        }
        this.currentTargetShape = boundedShape;
        if (startItem instanceof Pin targetPin) {
          TileShape copper = (TileShape) targetPin.getTileShape(startDoor.treeEntryNo);
          double traceHalfWidth = this.ctrl.compensatedTraceHalfWidth[startDoor.room.getLayer()];
          TileShape targetRoomShape = startDoor.room.getShape();
          IntPoint usualEnd =
              this.currentTargetShape.nearestPointApprox(this.currentFromPoint).round();
          TileShape shrunkTargetRoom = (TileShape) targetRoomShape.offset(-traceHalfWidth);
          IntPoint clearEnd =
              padEndWithClearStub(
                  targetPin,
                  copper != null ? copper : targetShape,
                  shrunkTargetRoom,
                  usualEnd,
                  startDoor.room.getLayer(),
                  false);
          if (clearEnd != null) {
            this.currentTargetShape = TileShape.getInstance(clearEnd);
          }
        }
      }
      this.currentToDoorIndex = this.currentFromDoorIndex + 1;
      ResultItem nextTrace = this.calculateNextTrace(layerChanged, atFanoutEnd);
      atFanoutEnd = false;
      this.connectionItems.add(nextTrace);
    }
  }

  /** Returns a new Instance of FoundConnectionLocator or null, if destinationDoor is null. */
  public static FoundConnectionLocator getInstance(
      MazeSearchEngine.Result mazeSearchResult,
      AutorouteControl ctrl,
      ShapeSearchTree searchTree,
      AngleRestriction angleRestriction,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts) {
    if (mazeSearchResult == null) {
      return null;
    }
    FoundConnectionLocator result;
    if (angleRestriction == AngleRestriction.NINETY_DEGREE
        || angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result =
          new FoundConnectionLocator45Degree(
              mazeSearchResult, ctrl, searchTree, angleRestriction, rippedItemList, ripupCosts);
    } else {
      result =
          new FoundConnectionLocatorAnyAngle(
              mazeSearchResult, ctrl, searchTree, angleRestriction, rippedItemList, ripupCosts);
    }
    return result;
  }

  /**
   * Calculates the width-safe target shape for a target door within its room. For DrillItems
   * (pins/vias), returns the connection shape directly. For distributed targets
   * (Trace/ConductionArea), enforces the clearance offset shrink of the room.
   */
  private static TileShape calculateWidthSafeTargetShape(
      TargetItemExpansionDoor door, TileShape targetShape, AutorouteControl ctrl) {
    if (door.item instanceof DrillItem) {
      return targetShape.intersection(door.room.getShape());
    }
    int layer = door.room.getLayer();
    int shrinkOffset =
        (door.room instanceof ObstacleExpansionRoom)
            ? ctrl.compensatedTraceHalfWidth[layer]
            : ctrl.getTraceClearanceOffset(layer);
    TileShape safeRoomShape = (TileShape) door.room.getShape().offset(-shrinkOffset);
    if (!safeRoomShape.isEmpty()) {
      TileShape safeTargetShape = targetShape.intersection(safeRoomShape);
      if (!safeTargetShape.isEmpty()) {
        return safeTargetShape;
      }
    }
    return targetShape.intersection(door.room.getShape());
  }

  /**
   * Calculates the starting point of the next trace on fromDoor.item, ensuring it is positioned
   * inside the width-safe clearance region of fromDoor.room.
   */
  private static FloatPoint calculateStartingPoint(
      TargetItemExpansionDoor fromDoor, ShapeSearchTree searchTree, AutorouteControl ctrl) {
    TileShape connectionShape =
        ((Connectable) fromDoor.item).getTraceConnectionShape(searchTree, fromDoor.treeEntryNo);
    if (connectionShape == null || connectionShape.isEmpty()) {
      return fromDoor.room.getShape().centreOfGravity().round().toFloat();
    }
    connectionShape = calculateWidthSafeTargetShape(fromDoor, connectionShape, ctrl);
    return connectionShape.centreOfGravity().round().toFloat();
  }

  /**
   * Creates a list of doors by backtracking from destinationDoor to the start door. Returns null,
   * if destinationDoor is null.
   */
  private static Collection<BacktrackElement> backtrack(
      MazeSearchEngine.Result mazeSearchResult,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int netNumber) {
    if (mazeSearchResult == null) {
      return null;
    }
    Collection<BacktrackElement> result = new LinkedList<>();
    CompleteExpansionRoom currentNextRoom = null;
    ExpandableObject currentBacktrackDoor = mazeSearchResult.destinationDoor;
    MazeSearchElement currentMazeSearchElement =
        currentBacktrackDoor.getMazeSearchElement(mazeSearchResult.sectionNoOfDoor);
    boolean debugBacktrack = netNumber == 98;
    if (debugBacktrack) {
      String destType = currentBacktrackDoor.getClass().getSimpleName();
      FRLogger.trace(
          "BACKTRACK_START net="
              + netNumber
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
        FRLogger.warn("FoundConnectionLocator: currentSectionNo to big");
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
          obstacleId = obst.getItem().getId();
        }
        FRLogger.trace(
            "BACKTRACK_STEP net="
                + netNumber
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
   * Calculates an additional corner, so that for the lines from fromPoint to the result corner and
   * from the result corner to toPoint angleRestriction is fulfilled.
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
    List<FloatPoint> cornerList = new ArrayList<>();
    cornerList.add(this.currentFromPoint);
    if (!atFanoutEnd) {
      FloatPoint adjustedStartCorner = this.adjustStartCorner();
      // The adjustment may have moved the pad end itself (PadEndWithClearStub).
      cornerList.set(0, this.currentFromPoint);
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
    IntPoint[] corners = new IntPoint[roundedCornerList.size()];
    Iterator<IntPoint> it2 = roundedCornerList.iterator();
    for (int i = 0; i < corners.length; i++) {
      corners[i] = it2.next();
    }
    ResultItem result = new ResultItem(corners, this.currentTraceLayer);
    if (this.ctrl.netNumber == 33 || this.ctrl.netNumber == 66 || this.ctrl.netNumber == 67) {
      IntPoint first = corners.length > 0 ? corners[0] : null;
      IntPoint last = corners.length > 0 ? corners[corners.length - 1] : null;
      FRLogger.trace(
          "compare_trace_next_trace_raw net="
              + this.ctrl.netNumber
              + ", traceLayer="
              + this.currentTraceLayer
              + ", nextLayer="
              + nextLayer
              + ", cornerCount="
              + corners.length
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
    double shrinkOffset =
        (currentFromInfo.nextRoom instanceof ObstacleExpansionRoom)
            ? this.ctrl.compensatedTraceHalfWidth[this.currentTraceLayer]
            : this.ctrl.getTraceClearanceOffset(this.currentTraceLayer);
    TileShape shrinkedRoomShape =
        (TileShape) currentFromInfo.nextRoom.getShape().offset(-shrinkOffset);
    if (shrinkedRoomShape.isEmpty() || shrinkedRoomShape.contains(this.currentFromPoint)) {
      return this.currentFromPoint;
    }
    // The first trace's stub out of a pad: from the pad end to the room's corner margin, clear of
    // copper
    // overlapping the pad (PadEndWithClearStub), or from a pad end that is.
    if (this.currentFromDoorIndex == 0
        && currentFromInfo.door instanceof TargetItemExpansionDoor padDoor
        && padDoor.item instanceof Pin pin) {
      TileShape copper = (TileShape) pin.getTileShape(padDoor.treeEntryNo);
      if (copper != null) {
        IntPoint clearEnd =
            padEndWithClearStub(
                pin,
                copper,
                shrinkedRoomShape,
                this.currentFromPoint.round(),
                this.currentTraceLayer,
                true);
        if (clearEnd != null) {
          this.currentFromPoint = clearEnd.toFloat();
          this.previousFromPoint = this.currentFromPoint;
        }
      }
    }
    return shrinkedRoomShape.nearestPointApprox(this.currentFromPoint).round().toFloat();
  }

  /**
   * freerouting.net (2026-10-20): a pad end whose stub is clear of copper overlapping the pad. A
   * trace's first and last corners lie inside the pad's room shrunk by the compensated half-width
   * (adjustStartCorner, the corner calculation); the stub between that corner and the pad end
   * crosses the pad's own copper, which the rooms do not cover, so nothing checks it against an
   * obstacle that overlaps the pad. nan-15_top-plate's D1-1 (KiCad benchmark): a keyswitch cut-out
   * grown by the edge clearance, a zero-clearance keepout, crosses the pad's right side 0.1 mm from
   * its centre; the stub from the centre ran north along the cut-out's edge, 2 um into it, and the
   * strict check ripped the connection every pass and round until one cost setting found another
   * way. Where the stub from usualEnd to the shrunk room's nearest point meets copper the insertion
   * cannot shove (a foreign pin, fixed copper, a keepout, the outline), the end moves to the point
   * of the pad's copper nearest the usual one from which the stub is clear (a grid of 32 steps a
   * side over the copper). Null keeps the usual end.
   */
  private IntPoint padEndWithClearStub(
      Pin pin,
      TileShape copper,
      TileShape shrunkRoom,
      IntPoint usualEnd,
      int layer,
      boolean fromPad) {
    if (copper.dimension() < 2
        || pin.board == null
        || shrunkRoom.isEmpty()
        || !unshovableCopperOverlapsPad(pin.board, pin, copper, layer, this.ctrl.netNumber)) {
      return null;
    }
    int halfWidth = this.ctrl.traceHalfWidth[layer];
    Predicate<IntPoint> clear =
        end -> {
          FloatPoint endPoint = end.toFloat();
          FloatPoint corner = shrunkRoom.nearestPointApprox(endPoint).round().toFloat();
          FloatPoint[] path;
          if (fromPad) {
            path =
                new FloatPoint[] {
                  endPoint,
                  calculateAdditionalCorner(endPoint, corner, true, this.angleRestriction),
                  corner
                };
          } else {
            FloatPoint add =
                calculateAdditionalCorner(corner, endPoint, true, this.angleRestriction);
            if (!shrunkRoom.contains(add)) {
              add = calculateAdditionalCorner(corner, endPoint, false, this.angleRestriction);
            }
            path = new FloatPoint[] {corner, add, endPoint};
          }
          List<Point> corners = new ArrayList<>();
          for (FloatPoint p : path) {
            IntPoint rounded = p.round();
            if (corners.isEmpty() || !rounded.equals(corners.get(corners.size() - 1))) {
              corners.add(rounded);
            }
          }
          if (corners.size() < 2) {
            IntBox box =
                new IntBox(
                    end.x - halfWidth, end.y - halfWidth, end.x + halfWidth, end.y + halfWidth);
            return !endBlockedByUnshovableCopper(
                pin.board, box, layer, this.ctrl.netNumber, this.ctrl.traceClearanceClassIndex);
          }
          Polyline stub = new Polyline(corners.toArray(new Point[0]));
          for (int i = 0; i < stub.cornerCount() - 1; i++) {
            if (endBlockedByUnshovableCopper(
                pin.board,
                stub.offsetShape(halfWidth, i),
                layer,
                this.ctrl.netNumber,
                this.ctrl.traceClearanceClassIndex)) {
              return false;
            }
          }
          return true;
        };

    if (clear.test(usualEnd)) {
      return null;
    }
    IntBox box = copper.boundingBox();
    final int steps = 32;
    double stepX = (box.ur.x - box.ll.x) / (double) steps;
    double stepY = (box.ur.y - box.ll.y) / (double) steps;
    IntPoint best = null;
    double bestDistance = Double.MAX_VALUE;
    for (int i = 1; i < steps; i++) {
      for (int j = 1; j < steps; j++) {
        IntPoint at =
            new IntPoint(
                (int) Math.round(box.ll.x + i * stepX), (int) Math.round(box.ll.y + j * stepY));
        double distance = at.toFloat().distance(usualEnd.toFloat());
        if (distance >= bestDistance || !copper.contains(at) || !clear.test(at)) {
          continue;
        }
        best = at;
        bestDistance = distance;
      }
    }
    if (FRLogger.isTraceEnabled()) {
      FRLogger.trace(
          "pad_end_with_clear_stub pin="
              + pin.getId()
              + ", layer="
              + layer
              + ", usual="
              + usualEnd
              + ", result="
              + (best != null ? best.toString() : "none"));
    }
    return best;
  }

  /**
   * freerouting.net (2026-10-21): whether copper the insertion cannot shove (a foreign pin, fixed
   * copper, a keepout) overlaps the pad's own copper on layer, the case padEndWithClearStub is for.
   */
  public static boolean unshovableCopperOverlapsPad(
      BasicBoard board, Pin pin, TileShape copper, int layer, int netNumber) {
    ShapeSearchTree defaultTree = board.searchTreeManager.getDefaultTree();
    Collection<ShapeTree.TreeEntry> treeEntries = new ArrayList<>();
    defaultTree.overlappingTreeEntries(copper, layer, new int[0], treeEntries);
    for (ShapeTree.TreeEntry entry : treeEntries) {
      if (!(entry.object instanceof Item item) || item == pin || !item.isTraceObstacle(netNumber)) {
        continue;
      }
      if (!(item instanceof Pin
          || item.isUserFixed()
          || !(item instanceof Trace || item instanceof Via))) {
        continue;
      }
      TileShape shape = item.getTileShape(entry.shapeIndexInObject);
      if (shape != null && shape.intersection(copper).dimension() >= 2) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether a trace end with shape on layer meets copper of another net that the forced insertion
   * cannot shove: a pin, fixed copper, a keepout or conduction area, the outline.
   */
  private static boolean endBlockedByUnshovableCopper(
      BasicBoard board, TileShape shape, int layer, int netNumber, int clearanceClassIndex) {
    if (!shape.isContainedIn(board.getBoundingBox())) {
      return true;
    }
    ShapeSearchTree defaultTree = board.searchTreeManager.getDefaultTree();
    Collection<ShapeTree.TreeEntry> treeEntries = new ArrayList<>();
    int[] ignoreNetNos = new int[0];
    if (defaultTree.isClearanceCompensationUsed()) {
      defaultTree.overlappingTreeEntries(shape, layer, ignoreNetNos, treeEntries);
    } else {
      defaultTree.overlappingTreeEntriesWithClearance(
          shape, layer, ignoreNetNos, clearanceClassIndex, treeEntries);
    }
    for (ShapeTree.TreeEntry entry : treeEntries) {
      if (!(entry.object instanceof Item item) || !item.isTraceObstacle(netNumber)) {
        continue;
      }
      if (item instanceof Pin
          || item.isUserFixed()
          || !(item instanceof Trace || item instanceof Via)) {
        return true;
      }
    }
    return false;
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

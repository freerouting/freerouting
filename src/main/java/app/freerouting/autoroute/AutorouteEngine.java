package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.board.ShapeSearchTree45Degree;
import app.freerouting.board.ShapeSearchTree90Degree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Temporary autoroute data stored on the RoutingBoard. */
public class AutorouteEngine {

  static final int TRACE_WIDTH_TOLERANCE = 2;

  /**
   * The current search tree used in autorouting. It depends on the trac clearance class used in the
   * autoroute algorithm.
   */
  public final ShapeSearchTree autorouteSearchTree;

  /**
   * If maintainDatabase, the autorouter database is maintained after a connection is completed for
   * performance reasons.
   */
  public final boolean maintainDatabase;

  /** The 2-dimensional array of rectangular pages of ExpansionDrills. */
  final DrillPageArray drillPageArray;

  /** The PCB-board of this autoroute algorithm. */
  final RoutingBoard board;

  /** To be able to stop the expansion algorithm. */
  Stoppable stoppableThread;

  /** The net number used for routing in this autoroute algorithm. */
  private int netNo;

  /** To stop the expansion algorithm after a time limit is exceeded. */
  private TimeLimit timeLimit;

  /** The list of incomplete expansion rooms on the routing board. */
  private List<IncompleteFreeSpaceExpansionRoom> incompleteExpansionRooms;

  /** The list of complete expansion rooms on the routing board. */
  private List<CompleteFreeSpaceExpansionRoom> completeExpansionRooms;

  /** The count of expansion rooms created so far. */
  private int expansionRoomInstanceCount;

  /**
   * Creates a new instance of BoardAutorouteEngine. If maintainDatabase, the autorouter database.
   * is maintained after a connection is completed for performance reasons.
   */
  public AutorouteEngine(RoutingBoard board, int traceClearanceClassNo, boolean maintainDatabase) {
    this.board = board;
    this.maintainDatabase = maintainDatabase;
    this.netNo = -1;
    this.autorouteSearchTree = board.searchTreeManager.getAutorouteTree(traceClearanceClassNo);
    int maxDrillPageWidth = (int) (5 * board.rules.getDefaultViaDiameter());
    maxDrillPageWidth = Math.max(maxDrillPageWidth, 10000);
    this.drillPageArray = new DrillPageArray(this.board, maxDrillPageWidth);
    this.stoppableThread = null;
  }

  /** Initializes a connection search for the specified net number. */
  public void initConnection(int netNo, Stoppable stoppableThread, TimeLimit timeLimit) {
    if (this.maintainDatabase) {
      if (netNo != this.netNo) {
        if (this.completeExpansionRooms != null) {
          // invalidate the net dependent complete free space expansion rooms.
          Collection<CompleteFreeSpaceExpansionRoom> roomsToRemove = new ArrayList<>();
          for (CompleteFreeSpaceExpansionRoom currRoom : completeExpansionRooms) {
            if (currRoom.isNetDependent()) {
              roomsToRemove.add(currRoom);
            }
          }
          for (CompleteFreeSpaceExpansionRoom currRoom : roomsToRemove) {
            this.removeCompleteExpansionRoom(currRoom);
          }
        }
        // invalidate the neighbour rooms of the items of netNo
        Collection<Item> itemList = this.board.getItems();
        for (Item currItem : itemList) {
          if (currItem.containsNet(netNo)) {
            this.board.additionalUpdateAfterChange(currItem);
          }
        }
      }
    }
    this.netNo = netNo;
    this.stoppableThread = stoppableThread;
    this.timeLimit = timeLimit;
  }

  /**
   * Auto-routes a connection between startSet and destSet. Returns ALREADY_CONNECTED, ROUTED,
   * NOT_ROUTED, or INSERT_ERROR. ripupCosts is an optional map to receive per-ripped-item ripup
   * costs (may be null).
   */
  public AutorouteAttemptResult autorouteConnection(
      Set<Item> startSet,
      Set<Item> destSet,
      AutorouteControl ctrl,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts) {
    String sourceItems = String.join(", ", startSet.stream().map(Item::toString).toList());
    String targetItems = String.join(", ", destSet.stream().map(Item::toString).toList());

    MazeSearchAlgo mazeSearchAlgo;
    try {
      mazeSearchAlgo = MazeSearchAlgo.getInstance(startSet, destSet, this, ctrl);
    } catch (Exception e) {
      FRLogger.error(
          "AutorouteEngine.autoroute_connection: Exception in MazeSearchAlgo.get_instance", e);
      mazeSearchAlgo = null;
    }

    if (mazeSearchAlgo == null) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between "
              + sourceItems
              + " and "
              + targetItems
              + ", because the maze search algorithm could not be created.");
    }

    MazeSearchAlgo.Result searchResult = null;
    if (mazeSearchAlgo != null) {
      try {
        searchResult = mazeSearchAlgo.findConnection();
      } catch (Exception e) {
        FRLogger.error(
            "AutorouteEngine.autoroute_connection: Exception in mazeSearchAlgo.find_connection", e);
      }
    }

    if (searchResult != null) {
      if (ctrl.netNo == 33 || ctrl.netNo == 66 || ctrl.netNo == 67) {
        String destinationType =
            searchResult.destinationDoor != null
                ? searchResult.destinationDoor.getClass().getSimpleName()
                : "null";
        FRLogger.trace(
            "compare_trace_maze_result_raw net="
                + ctrl.netNo
                + ", section="
                + searchResult.sectionNoOfDoor
                + ", destination_type="
                + destinationType);
      }
    }

    LocateFoundConnectionAlgo autorouteResult = null;
    if (searchResult != null) {
      try {
        autorouteResult =
            LocateFoundConnectionAlgo.getInstance(
                searchResult,
                ctrl,
                this.autorouteSearchTree,
                board.rules.getTraceAngleRestriction(),
                rippedItemList,
                ripupCosts);
      } catch (Exception e) {
        FRLogger.error(
            "AutorouteEngine.autoroute_connection: Exception in "
                + "LocateFoundConnectionAlgo.get_instance",
            e);
      }
    }

    // Always clean up expansion rooms from the search tree, regardless of search outcome.
    // This mirrors v1.9's behavior: clear() is called before any early returns so that
    // stale CompleteFreeSpaceExpansionRoom objects don't pollute subsequent routing attempts.
    if (!this.maintainDatabase) {
      this.clear();
    } else {
      this.resetAllDoors();
    }

    if (searchResult == null) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between "
              + sourceItems
              + " and "
              + targetItems
              + ", because no connection was found between their nets.");
    }

    if (autorouteResult == null) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between " + sourceItems + " and " + targetItems + ".");
    }

    if (!ctrl.layerActive[autorouteResult.startLayer]
        || !ctrl.layerActive[autorouteResult.targetLayer]) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between "
              + sourceItems
              + " and "
              + targetItems
              + ", because some of their layers are disabled.");
    }

    if (autorouteResult.connectionItems == null) {
      FRLogger.debug("AutorouteEngine.autoroute_connection: result_items != null expected");
      return new AutorouteAttemptResult(
          AutorouteAttemptState.SKIPPED,
          "No new connections were made between " + sourceItems + " and " + targetItems + ".");
    }

    // Delete the ripped connections.
    SortedSet<Item> rippedConnections = new TreeSet<>();
    Set<Integer> changedNets = new TreeSet<>();
    Item.StopConnectionOption stopConnectionOption;
    if (ctrl.removeUnconnectedVias) {
      stopConnectionOption = Item.StopConnectionOption.NONE;
    } else {
      stopConnectionOption = Item.StopConnectionOption.FANOUT_VIA;
    }

    for (Item currRippedItem : rippedItemList) {
      rippedConnections.addAll(currRippedItem.getConnectionItems(stopConnectionOption));
      for (int i = 0; i < currRippedItem.netCount(); i++) {
        changedNets.add(currRippedItem.getNetNo(i));
      }
    }

    // let the observers know the changes in the board database.
    boolean observersActivated = !this.board.observersActive();
    if (observersActivated) {
      this.board.startNotifyObservers();
    }

    board.removeItems(rippedConnections);

    for (int currNetNo : changedNets) {
      this.board.removeTraceTails(currNetNo, stopConnectionOption);
    }
    InsertFoundConnectionAlgo insertFoundConnectionAlgo =
        InsertFoundConnectionAlgo.getInstance(autorouteResult, board, ctrl);

    if (observersActivated) {
      this.board.endNotifyObservers();
    }
    if (insertFoundConnectionAlgo == null) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between "
              + sourceItems
              + " and "
              + targetItems
              + ", because the new connection could not be inserted.");
    }

    return new AutorouteAttemptResult(AutorouteAttemptState.ROUTED);
  }

  /** Returns the net number of the current connection to route. */
  public int getNetNo() {
    return this.netNo;
  }

  /** Returns if the user has stopped the autorouter. */
  public boolean isStopRequested() {
    if (this.timeLimit != null) {
      if (this.timeLimit.limitExceeded()) {
        return true;
      }
    }
    if (this.stoppableThread == null) {
      return false;
    }
    return this.stoppableThread.isStopRequested();
  }

  /** Clears all temporary data. */
  public void clear() {
    if (completeExpansionRooms != null) {
      for (CompleteFreeSpaceExpansionRoom currRoom : completeExpansionRooms) {
        currRoom.removeFromTree(this.autorouteSearchTree);
      }
    }
    completeExpansionRooms = null;
    incompleteExpansionRooms = null;
    expansionRoomInstanceCount = 0;
    board.clearAllItemTemporaryAutorouteData();
  }

  /** Draws the shapes of the expansion rooms created so far. */
  public void draw(Graphics graphics, GraphicsContext graphicsContext, double intensity) {
    if (completeExpansionRooms == null) {
      return;
    }
    for (CompleteFreeSpaceExpansionRoom currRoom : completeExpansionRooms) {
      currRoom.draw(graphics, graphicsContext, intensity);
    }
    Collection<Item> itemList = this.board.getItems();
    for (Item currItem : itemList) {
      ItemAutorouteInfo autorouteInfo = currItem.getAutorouteInfo();
      if (autorouteInfo != null) {
        autorouteInfo.draw(graphics, graphicsContext, intensity);
      }
    }
  }

  /**
   * Creates a new FreeSpaceExpansionRoom and adds it to the room list. Its shape is normally
   * unbounded at construction time of the room. The final (completed) shape will be a subshape of
   * the start shape, which does not overlap with any obstacle, and it is as big as possible.
   */
  public IncompleteFreeSpaceExpansionRoom addIncompleteExpansionRoom(
      TileShape shape, int layer, TileShape containedShape) {
    IncompleteFreeSpaceExpansionRoom newRoom =
        new IncompleteFreeSpaceExpansionRoom(shape, layer, containedShape);
    if (this.incompleteExpansionRooms == null) {
      this.incompleteExpansionRooms = new ArrayList<>();
    }
    this.incompleteExpansionRooms.add(newRoom);
    return newRoom;
  }

  /**
   * Returns the first element in the list of incomplete expansion rooms or null if the list is
   * empty.
   */
  public IncompleteFreeSpaceExpansionRoom getFirstIncompleteExpansionRoom() {
    if (incompleteExpansionRooms == null) {
      return null;
    }
    if (incompleteExpansionRooms.isEmpty()) {
      return null;
    }
    Iterator<IncompleteFreeSpaceExpansionRoom> it = incompleteExpansionRooms.iterator();
    return it.next();
  }

  /** Removes an incomplete room from the database. */
  public void removeIncompleteExpansionRoom(IncompleteFreeSpaceExpansionRoom room) {
    this.removeAllDoors(room);
    incompleteExpansionRooms.remove(room);
  }

  /**
   * Removes a complete expansion room from the database and creates new incomplete expansion rooms
   * for the neighbours.
   */
  public void removeCompleteExpansionRoom(CompleteFreeSpaceExpansionRoom room) {
    // create new incomplete expansion rooms for all neighbours
    TileShape roomShape = room.getShape();
    int roomLayer = room.getLayer();
    Collection<ExpansionDoor> roomDoors = room.getDoors();
    for (ExpansionDoor currDoor : roomDoors) {
      ExpansionRoom currNeighbour = currDoor.otherRoom(room);
      if (currNeighbour == null) {
        continue;
      }
      currNeighbour.removeDoor(currDoor);
      TileShape neighbourShape = currNeighbour.getShape();
      TileShape intersection = roomShape.intersection(neighbourShape);
      if (intersection.dimension() == 1) {
        // add a new incomplete room to currNeighbour.
        int[] touchingSides = roomShape.touchingSides(neighbourShape);
        Line[] lineArr = new Line[1];
        lineArr[0] = neighbourShape.borderLine(touchingSides[1]).opposite();
        Simplex newIncompleteRoomShape = Simplex.getInstance(lineArr);
        IncompleteFreeSpaceExpansionRoom newIncompleteRoom =
            addIncompleteExpansionRoom(newIncompleteRoomShape, roomLayer, intersection);
        ExpansionDoor newDoor = new ExpansionDoor(currNeighbour, newIncompleteRoom, 1);
        currNeighbour.addDoor(newDoor);
        newIncompleteRoom.addDoor(newDoor);
      }
    }
    this.removeAllDoors(room);
    room.removeFromTree(this.autorouteSearchTree);
    if (completeExpansionRooms != null) {
      completeExpansionRooms.remove(room);
    } else {
      FRLogger.warn(
          "AutorouteEngine.remove_complete_expansion_room: this.completeExpansionRooms is null");
    }
    this.drillPageArray.invalidate(roomShape);
  }

  /**
   * Completes the shape of room. Returns the resulting rooms after completing the shape. room will
   * no longer exist after this function.
   */
  public Collection<CompleteFreeSpaceExpansionRoom> completeExpansionRoom(
      IncompleteFreeSpaceExpansionRoom room) {

    try {
      final Collection<CompleteFreeSpaceExpansionRoom> result = new ArrayList<>();
      TileShape fromDoorShape = null;
      SearchTreeObject ignoreObject = null;
      Collection<ExpansionDoor> roomDoors = room.getDoors();
      for (ExpansionDoor currDoor : roomDoors) {
        ExpansionRoom otherRoom = currDoor.otherRoom(room);
        if (otherRoom instanceof CompleteFreeSpaceExpansionRoom freeRoom
            && currDoor.dimension == 2) {
          fromDoorShape = currDoor.getShape();
          ignoreObject = freeRoom;
          break;
        }
      }
      FRLogger.trace(
          "COMPLETE_ROOM input"
              + ", net="
              + this.netNo
              + ", layer="
              + room.getLayer()
              + ", room_bounds="
              + describeShapeBounds(room.getShape())
              + ", contained_bounds="
              + describeShapeBounds(room.getContainedShape())
              + ", from_door_bounds="
              + describeShapeBounds(fromDoorShape)
              + ", ignoreObject="
              + (ignoreObject == null ? "null" : ignoreObject.getClass().getSimpleName()));
      Collection<IncompleteFreeSpaceExpansionRoom> completedShapes =
          this.autorouteSearchTree.completeShape(room, this.netNo, ignoreObject, fromDoorShape);
      int initialCandidateIndex = 0;
      for (IncompleteFreeSpaceExpansionRoom initialCandidate : completedShapes) {
        FRLogger.trace(
            "COMPLETE_ROOM initial_candidate"
                + ", net="
                + this.netNo
                + ", layer="
                + initialCandidate.getLayer()
                + ", index="
                + initialCandidateIndex
                + ", dimension="
                + initialCandidate.getShape().dimension()
                + ", incomplete_bounds="
                + describeShapeBounds(initialCandidate.getShape())
                + ", from_door_bounds="
                + describeShapeBounds(fromDoorShape));
        ++initialCandidateIndex;
      }
      this.removeIncompleteExpansionRoom(room);
      boolean isFirstCompletedRoom = true;
      for (IncompleteFreeSpaceExpansionRoom currIncompleteRoom : completedShapes) {
        if (currIncompleteRoom.getShape().dimension() != 2) {
          continue;
        }
        if (isFirstCompletedRoom) {
          isFirstCompletedRoom = false;
          FRLogger.trace(
              "COMPLETE_ROOM first_candidate"
                  + ", net="
                  + this.netNo
                  + ", layer="
                  + currIncompleteRoom.getLayer()
                  + ", incomplete_bounds="
                  + describeShapeBounds(currIncompleteRoom.getShape())
                  + ", from_door_bounds="
                  + describeShapeBounds(fromDoorShape));
          CompleteFreeSpaceExpansionRoom completedRoom = this.addCompleteRoom(currIncompleteRoom);
          if (completedRoom != null) {
            result.add(completedRoom);
          }
        } else {
          // the shape of the first completed room may have changed and may
          // intersect now with the other shapes. Therefore, the completed shapes
          // have to be recalculated.
          Collection<IncompleteFreeSpaceExpansionRoom> currCompletedShapes =
              this.autorouteSearchTree.completeShape(
                  currIncompleteRoom, this.netNo, ignoreObject, fromDoorShape);
          for (IncompleteFreeSpaceExpansionRoom tmpRoom : currCompletedShapes) {
            FRLogger.trace(
                "COMPLETE_ROOM recalc_candidate"
                    + ", net="
                    + this.netNo
                    + ", layer="
                    + tmpRoom.getLayer()
                    + ", incomplete_bounds="
                    + describeShapeBounds(tmpRoom.getShape())
                    + ", from_door_bounds="
                    + describeShapeBounds(fromDoorShape));
            CompleteFreeSpaceExpansionRoom completedRoom = this.addCompleteRoom(tmpRoom);
            if (completedRoom != null) {
              result.add(completedRoom);
            }
          }
        }
      }
      return result;
    } catch (Exception e) {
      FRLogger.error("AutorouteEngine.complete_expansion_room: ", e);
      return new ArrayList<>();
    }
  }

  /** Calculates the doors and adds the completed room to the room database. */
  private CompleteFreeSpaceExpansionRoom addCompleteRoom(IncompleteFreeSpaceExpansionRoom room) {
    CompleteFreeSpaceExpansionRoom completedRoom =
        (CompleteFreeSpaceExpansionRoom) calculateDoors(room);
    if (completedRoom == null || completedRoom.getShape().dimension() != 2) {
      return null;
    }
    if (completeExpansionRooms == null) {
      completeExpansionRooms = new ArrayList<>();
    }
    completeExpansionRooms.add(completedRoom);
    this.autorouteSearchTree.insert(completedRoom);
    FRLogger.trace(
        "COMPLETE_ROOM added"
            + ", net="
            + this.netNo
            + ", layer="
            + completedRoom.getLayer()
            + ", bounds="
            + describeShapeBounds(completedRoom.getShape()));
    return completedRoom;
  }

  private static String describeShapeBounds(TileShape shape) {
    if (shape == null) {
      return "null";
    }
    IntBox bounds = shape.boundingBox();
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  /**
   * Calculates the neighbours of room and inserts doors to the new created neighbour rooms. The
   * shape of the result room may be different to the shape of room.
   */
  private CompleteExpansionRoom calculateDoors(ExpansionRoom room) {
    CompleteExpansionRoom result;
    if (this.autorouteSearchTree instanceof ShapeSearchTree90Degree) {
      result = SortedOrthogonalRoomNeighbours.calculate(room, this);
    } else if (this.autorouteSearchTree instanceof ShapeSearchTree45Degree) {
      result = Sorted45DegreeRoomNeighbours.calculate(room, this);
    } else {
      result = SortedRoomNeighbours.calculate(room, this);
    }
    return result;
  }

  /**
   * Completes the shapes of the neighbour rooms of room, so that the doors of room will not change
   * later on.
   */
  public void completeNeighbourRooms(CompleteExpansionRoom room) {
    if (room.getDoors() == null) {
      return;
    }
    // Keep v1.9 semantics: completing a neighbour can mutate door topology, so
    // restart iteration on the updated door set.
    Iterator<ExpansionDoor> it = room.getDoors().iterator();
    while (it.hasNext()) {
      ExpansionDoor currDoor = it.next();
      // cast to ExpansionRoom because ExpansionDoor.otherRoom works differently with
      // parameter type CompleteExpansionRoom.
      ExpansionRoom neighbourRoom = currDoor.otherRoom((ExpansionRoom) room);
      if (neighbourRoom == null) {
        continue;
      }
      if (neighbourRoom instanceof IncompleteFreeSpaceExpansionRoom freeRoom) {
        this.completeExpansionRoom(freeRoom);
        it = room.getDoors().iterator();
      } else if (neighbourRoom instanceof ObstacleExpansionRoom obstacleNeighbourRoom) {
        if (!obstacleNeighbourRoom.allDoorsCalculated()) {
          this.calculateDoors(obstacleNeighbourRoom);
          obstacleNeighbourRoom.setDoorsCalculated(true);
        }
      }
    }
  }

  /**
   * Invalidates all drill pages intersecting with shape so they must be recalculated at the next
   * call of getDrills().
   */
  public void invalidateDrillPages(TileShape shape) {
    this.drillPageArray.invalidate(shape);
  }

  /** Removes all doors from room. */
  void removeAllDoors(ExpansionRoom room) {
    for (ExpansionDoor currDoor : room.getDoors()) {
      ExpansionRoom otherRoom = currDoor.otherRoom(room);
      if (otherRoom == null) {
        continue;
      }
      otherRoom.removeDoor(currDoor);
      if (otherRoom instanceof IncompleteFreeSpaceExpansionRoom freeRoom) {
        this.removeIncompleteExpansionRoom(freeRoom);
      }
    }
    room.clearDoors();
  }

  /**
   * Returns all complete free space expansion rooms with a target door to an item in the set items.
   */
  Set<CompleteFreeSpaceExpansionRoom> getRoomsWithTargetItems(Set<Item> items) {
    Set<CompleteFreeSpaceExpansionRoom> result = new TreeSet<>();
    if (this.completeExpansionRooms != null) {
      for (CompleteFreeSpaceExpansionRoom currRoom : this.completeExpansionRooms) {
        Collection<TargetItemExpansionDoor> targetDoorList = currRoom.getTargetDoors();
        for (TargetItemExpansionDoor currTargetDoor : targetDoorList) {
          Item currTargetItem = currTargetDoor.item;
          if (items.contains(currTargetItem)) {
            result.add(currRoom);
          }
        }
      }
    }
    return result;
  }

  /** Checks if the internal datastructure is valid. */
  public boolean validate() {
    if (completeExpansionRooms == null) {
      return true;
    }
    boolean result = true;
    for (CompleteFreeSpaceExpansionRoom currRoom : completeExpansionRooms) {
      if (!currRoom.validate(this)) {
        result = false;
      }
    }
    return result;
  }

  /**
   * Resets all doors for autorouting the next connection in case the autorouting database is
   * retained.
   */
  private void resetAllDoors() {
    if (this.completeExpansionRooms != null) {
      for (ExpansionRoom currRoom : this.completeExpansionRooms) {
        currRoom.resetDoors();
      }
    }
    Collection<Item> itemList = this.board.getItems();
    for (Item currItem : itemList) {
      ItemAutorouteInfo currAutorouteInfo = currItem.getAutorouteInfoPur();
      if (currAutorouteInfo != null) {
        currAutorouteInfo.resetDoors();
        currAutorouteInfo.setPrecalculatedConnection(null);
      }
    }
    this.drillPageArray.reset();
  }

  /** Returns the next expansion room identifier. */
  protected int generateRoomIdNo() {
    return ++expansionRoomInstanceCount;
  }
}

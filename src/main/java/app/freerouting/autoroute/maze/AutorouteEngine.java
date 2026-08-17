package app.freerouting.autoroute.maze;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.AutorouteDiagnostic;
import app.freerouting.autoroute.ItemAutorouteInfo;
import app.freerouting.autoroute.drill.DrillPageArray;
import app.freerouting.autoroute.expansion.CompleteExpansionRoom;
import app.freerouting.autoroute.expansion.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.expansion.ExpansionDoor;
import app.freerouting.autoroute.expansion.ExpansionRoom;
import app.freerouting.autoroute.expansion.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.expansion.ObstacleExpansionRoom;
import app.freerouting.autoroute.expansion.SortedRoomNeighbours;
import app.freerouting.autoroute.expansion.TargetItemExpansionDoor;
import app.freerouting.autoroute.path.FoundConnectionInserter;
import app.freerouting.autoroute.path.FoundConnectionLocator;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
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

  public static final int TRACE_WIDTH_TOLERANCE = 2;

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
  public final RoutingBoard board;

  /** To be able to stop the expansion algorithm. */
  public Stoppable stoppableThread;

  /** The net number used for routing in this autoroute algorithm. */
  private int netNumber;

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
  public AutorouteEngine(
      RoutingBoard board, int traceClearanceClassIndex, boolean maintainDatabase) {
    this.board = board;
    this.maintainDatabase = maintainDatabase;
    this.netNumber = -1;
    this.autorouteSearchTree = board.searchTreeManager.getAutorouteTree(traceClearanceClassIndex);
    int maxDrillPageWidth = (int) (5 * board.rules.getDefaultViaDiameter());
    maxDrillPageWidth = Math.max(maxDrillPageWidth, 10000);
    this.drillPageArray = new DrillPageArray(this.board, maxDrillPageWidth);
    this.stoppableThread = null;
  }

  /** Initializes a connection search for the specified net number. */
  public void initConnection(int netNumber, Stoppable stoppableThread, TimeLimit timeLimit) {
    if (this.maintainDatabase) {
      if (netNumber != this.netNumber) {
        if (this.completeExpansionRooms != null) {
          // invalidate the net dependent complete free space expansion rooms.
          Collection<CompleteFreeSpaceExpansionRoom> roomsToRemove = new ArrayList<>();
          for (CompleteFreeSpaceExpansionRoom currentRoom : completeExpansionRooms) {
            if (currentRoom.isNetDependent()) {
              roomsToRemove.add(currentRoom);
            }
          }
          for (CompleteFreeSpaceExpansionRoom currentRoom : roomsToRemove) {
            this.removeCompleteExpansionRoom(currentRoom);
          }
        }
        // invalidate the neighbour rooms of the items of netNumber
        Collection<Item> itemList = this.board.getItems();
        for (Item currentItem : itemList) {
          if (currentItem.containsNet(netNumber)) {
            this.board.additionalUpdateAfterChange(currentItem);
          }
        }
      }
    }
    this.netNumber = netNumber;
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
    MazeSearchEngine mazeSearchAlgo;
    try {
      mazeSearchAlgo = MazeSearchEngine.getInstance(startSet, destSet, this, ctrl);
    } catch (Exception e) {
      FRLogger.error(
          "AutorouteEngine.autoroute_connection: Exception in MazeSearchEngine.get_instance", e);
      mazeSearchAlgo = null;
    }

    if (mazeSearchAlgo == null) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between "
              + describeConnection(startSet, destSet)
              + ", because the maze search algorithm could not be created.");
    }

    MazeSearchEngine.Result searchResult = null;
    if (mazeSearchAlgo != null) {
      try {
        searchResult = mazeSearchAlgo.findConnection();
      } catch (Exception e) {
        FRLogger.error(
            "AutorouteEngine.autoroute_connection: Exception in mazeSearchAlgo.find_connection", e);
      }
    }

    if (searchResult != null) {
      if (ctrl.netNumber == 33 || ctrl.netNumber == 66 || ctrl.netNumber == 67) {
        String destinationType =
            searchResult.destinationDoor != null
                ? searchResult.destinationDoor.getClass().getSimpleName()
                : "null";
        FRLogger.trace(
            "compare_trace_maze_result_raw net="
                + ctrl.netNumber
                + ", section="
                + searchResult.sectionNoOfDoor
                + ", destination_type="
                + destinationType);
      }
    }

    FoundConnectionLocator autorouteResult = null;
    if (searchResult != null) {
      try {
        autorouteResult =
            FoundConnectionLocator.getInstance(
                searchResult,
                ctrl,
                this.autorouteSearchTree,
                board.rules.getTraceAngleRestriction(),
                rippedItemList,
                ripupCosts);
      } catch (Exception e) {
        FRLogger.error(
            "AutorouteEngine.autoroute_connection: Exception in "
                + "FoundConnectionLocator.get_instance",
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
              + describeConnection(startSet, destSet)
              + ", because no connection was found between their nets.");
    }

    if (autorouteResult == null) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between " + describeConnection(startSet, destSet) + ".");
    }

    if (!ctrl.layerActive[autorouteResult.startLayer]
        || !ctrl.layerActive[autorouteResult.targetLayer]) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between "
              + describeConnection(startSet, destSet)
              + ", because some of their layers are disabled.");
    }

    if (autorouteResult.connectionItems == null) {
      FRLogger.debug("AutorouteEngine.autoroute_connection: result_items != null expected");
      return new AutorouteAttemptResult(
          AutorouteAttemptState.SKIPPED,
          "No new connections were made between " + describeConnection(startSet, destSet) + ".");
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

    for (Item currentRippedItem : rippedItemList) {
      rippedConnections.addAll(currentRippedItem.getConnectionItems(stopConnectionOption));
      for (int i = 0; i < currentRippedItem.netCount(); i++) {
        changedNets.add(currentRippedItem.getNetNumber(i));
      }
    }

    // let the observers know the changes in the board database.
    boolean observersActivated = !this.board.observersActive();
    if (observersActivated) {
      this.board.startNotifyObservers();
    }

    board.removeItems(rippedConnections);

    for (int currentNetNumber : changedNets) {
      this.board.removeTraceTails(currentNetNumber, stopConnectionOption);
    }
    FoundConnectionInserter insertFoundConnectionAlgo =
        FoundConnectionInserter.getInstance(autorouteResult, board, ctrl);

    if (observersActivated) {
      this.board.endNotifyObservers();
    }
    if (insertFoundConnectionAlgo == null) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.FAILED,
          "Failed to route connection between "
              + describeConnection(startSet, destSet)
              + ", because the new connection could not be inserted.");
    }

    return new AutorouteAttemptResult(AutorouteAttemptState.ROUTED);
  }

  private static String describeConnection(Set<Item> startSet, Set<Item> destSet) {
    return String.join(", ", startSet.stream().map(Item::toString).toList())
        + " and "
        + String.join(", ", destSet.stream().map(Item::toString).toList());
  }

  /** Returns the net number of the current connection to route. */
  public int getNetNumber() {
    return this.netNumber;
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
      for (CompleteFreeSpaceExpansionRoom currentRoom : completeExpansionRooms) {
        currentRoom.removeFromTree(this.autorouteSearchTree);
      }
    }
    completeExpansionRooms = null;
    incompleteExpansionRooms = null;
    expansionRoomInstanceCount = 0;
    board.clearAllItemTemporaryAutorouteData();
  }

  /** Emits optional diagnostics for the expansion rooms created so far. */
  public void emitDiagnostics(AutorouteDiagnostic.Sink sink, double intensity) {
    if (sink == null || intensity <= 0 || completeExpansionRooms == null) {
      return;
    }
    for (CompleteFreeSpaceExpansionRoom currentRoom : completeExpansionRooms) {
      currentRoom.emitDiagnostic(sink, intensity);
    }
    Collection<Item> itemList = this.board.getItems();
    for (Item currentItem : itemList) {
      ItemAutorouteInfo autorouteInfo = currentItem.getAutorouteInfo();
      if (autorouteInfo != null) {
        autorouteInfo.emitDiagnostics(sink, intensity);
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
    for (ExpansionDoor currentDoor : roomDoors) {
      ExpansionRoom currentNeighbour = currentDoor.otherRoom(room);
      if (currentNeighbour == null) {
        continue;
      }
      currentNeighbour.removeDoor(currentDoor);
      TileShape neighbourShape = currentNeighbour.getShape();
      TileShape intersection = roomShape.intersection(neighbourShape);
      if (intersection.dimension() == 1) {
        // add a new incomplete room to currentNeighbour.
        int[] touchingSides = roomShape.touchingSides(neighbourShape);
        Line[] lines = new Line[1];
        lines[0] = neighbourShape.borderLine(touchingSides[1]).opposite();
        Simplex newIncompleteRoomShape = Simplex.getInstance(lines);
        IncompleteFreeSpaceExpansionRoom newIncompleteRoom =
            addIncompleteExpansionRoom(newIncompleteRoomShape, roomLayer, intersection);
        ExpansionDoor newDoor = new ExpansionDoor(currentNeighbour, newIncompleteRoom, 1);
        currentNeighbour.addDoor(newDoor);
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
      for (ExpansionDoor currentDoor : roomDoors) {
        ExpansionRoom otherRoom = currentDoor.otherRoom(room);
        if (otherRoom instanceof CompleteFreeSpaceExpansionRoom freeRoom
            && currentDoor.dimension == 2) {
          fromDoorShape = currentDoor.getShape();
          ignoreObject = freeRoom;
          break;
        }
      }
      FRLogger.trace(
          "COMPLETE_ROOM input"
              + ", net="
              + this.netNumber
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
          this.autorouteSearchTree.completeShape(room, this.netNumber, ignoreObject, fromDoorShape);
      int initialCandidateIndex = 0;
      for (IncompleteFreeSpaceExpansionRoom initialCandidate : completedShapes) {
        FRLogger.trace(
            "COMPLETE_ROOM initial_candidate"
                + ", net="
                + this.netNumber
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
      for (IncompleteFreeSpaceExpansionRoom currentIncompleteRoom : completedShapes) {
        if (currentIncompleteRoom.getShape().dimension() != 2) {
          continue;
        }
        if (isFirstCompletedRoom) {
          isFirstCompletedRoom = false;
          FRLogger.trace(
              "COMPLETE_ROOM first_candidate"
                  + ", net="
                  + this.netNumber
                  + ", layer="
                  + currentIncompleteRoom.getLayer()
                  + ", incomplete_bounds="
                  + describeShapeBounds(currentIncompleteRoom.getShape())
                  + ", from_door_bounds="
                  + describeShapeBounds(fromDoorShape));
          CompleteFreeSpaceExpansionRoom completedRoom =
              this.addCompleteRoom(currentIncompleteRoom);
          if (completedRoom != null) {
            result.add(completedRoom);
          }
        } else {
          // the shape of the first completed room may have changed and may
          // intersect now with the other shapes. Therefore, the completed shapes
          // have to be recalculated.
          Collection<IncompleteFreeSpaceExpansionRoom> currentCompletedShapes =
              this.autorouteSearchTree.completeShape(
                  currentIncompleteRoom, this.netNumber, ignoreObject, fromDoorShape);
          for (IncompleteFreeSpaceExpansionRoom tmpRoom : currentCompletedShapes) {
            FRLogger.trace(
                "COMPLETE_ROOM recalc_candidate"
                    + ", net="
                    + this.netNumber
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
            + this.netNumber
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
    return SortedRoomNeighbours.complete(room, this);
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
      ExpansionDoor currentDoor = it.next();
      // cast to ExpansionRoom because ExpansionDoor.otherRoom works differently with
      // parameter type CompleteExpansionRoom.
      ExpansionRoom neighbourRoom = currentDoor.otherRoom((ExpansionRoom) room);
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
  public void removeAllDoors(ExpansionRoom room) {
    for (ExpansionDoor currentDoor : room.getDoors()) {
      ExpansionRoom otherRoom = currentDoor.otherRoom(room);
      if (otherRoom == null) {
        continue;
      }
      otherRoom.removeDoor(currentDoor);
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
      for (CompleteFreeSpaceExpansionRoom currentRoom : this.completeExpansionRooms) {
        Collection<TargetItemExpansionDoor> targetDoorList = currentRoom.getTargetDoors();
        for (TargetItemExpansionDoor currentTargetDoor : targetDoorList) {
          Item currentTargetItem = currentTargetDoor.item;
          if (items.contains(currentTargetItem)) {
            result.add(currentRoom);
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
    for (CompleteFreeSpaceExpansionRoom currentRoom : completeExpansionRooms) {
      if (!currentRoom.validate(this)) {
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
      for (ExpansionRoom currentRoom : this.completeExpansionRooms) {
        currentRoom.resetDoors();
      }
    }
    Collection<Item> itemList = this.board.getItems();
    for (Item currentItem : itemList) {
      ItemAutorouteInfo currentAutorouteInfo = currentItem.getAutorouteInfoPur();
      if (currentAutorouteInfo != null) {
        currentAutorouteInfo.resetDoors();
        currentAutorouteInfo.setPrecalculatedConnection(null);
      }
    }
    this.drillPageArray.reset();
  }

  /** Returns the next expansion room identifier. */
  public int generateRoomIdNo() {
    return ++expansionRoomInstanceCount;
  }
}

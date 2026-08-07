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

  /** The 2-dimensional array of rectangular pages of ExpansionDrills */
  final DrillPageArray drillPageArray;

  /** The PCB-board of this autoroute algorithm. */
  final RoutingBoard board;

  /** To be able to stop the expansion algorithm. */
  Stoppable stoppableThread;

  /** The net number used for routing in this autoroute algorithm. */
  private int netNo;

  /** To stop the expansion algorithm after a time limit is exceeded. */
  private TimeLimit timeLimit;

  /** The list of incomplete expansion rooms on the routing board */
  private List<IncompleteFreeSpaceExpansionRoom> incompleteExpansionRooms;

  /** The list of complete expansion rooms on the routing board */
  private List<CompleteFreeSpaceExpansionRoom> completeExpansionRooms;

  /** The count of expansion rooms created so far */
  private int expansionRoomInstanceCount;

  /**
   * Creates a new instance of BoardAutorouteEngine If p_maintain_database, the autorouter database
   * is maintained after a connection is completed for performance reasons.
   */
  public AutorouteEngine(
      RoutingBoard p_board, int p_trace_clearance_class_no, boolean p_maintain_database) {
    this.board = p_board;
    this.maintainDatabase = p_maintain_database;
    this.netNo = -1;
    this.autorouteSearchTree =
        p_board.searchTreeManager.get_autoroute_tree(p_trace_clearance_class_no);
    int maxDrillPageWidth = (int) (5 * p_board.rules.get_default_via_diameter());
    maxDrillPageWidth = Math.max(maxDrillPageWidth, 10000);
    this.drillPageArray = new DrillPageArray(this.board, maxDrillPageWidth);
    this.stoppableThread = null;
  }

  public void init_connection(int p_net_no, Stoppable p_stoppable_thread, TimeLimit p_time_limit) {
    if (this.maintainDatabase) {
      if (p_net_no != this.netNo) {
        if (this.completeExpansionRooms != null) {
          // invalidate the net dependent complete free space expansion rooms.
          Collection<CompleteFreeSpaceExpansionRoom> roomsToRemove = new ArrayList<>();
          for (CompleteFreeSpaceExpansionRoom currRoom : completeExpansionRooms) {
            if (currRoom.is_net_dependent()) {
              roomsToRemove.add(currRoom);
            }
          }
          for (CompleteFreeSpaceExpansionRoom currRoom : roomsToRemove) {
            this.remove_complete_expansion_room(currRoom);
          }
        }
        // invalidate the neighbour rooms of the items of p_net_no
        Collection<Item> itemList = this.board.get_items();
        for (Item currItem : itemList) {
          if (currItem.contains_net(p_net_no)) {
            this.board.additional_update_after_change(currItem);
          }
        }
      }
    }
    this.netNo = p_net_no;
    this.stoppableThread = p_stoppable_thread;
    this.timeLimit = p_time_limit;
  }

  /**
   * Auto-routes a connection between p_start_set and p_dest_set. Returns ALREADY_CONNECTED, ROUTED,
   * NOT_ROUTED, or INSERT_ERROR. p_ripup_costs is an optional map to receive per-ripped-item ripup
   * costs (may be null).
   */
  public AutorouteAttemptResult autoroute_connection(
      Set<Item> p_start_set,
      Set<Item> p_dest_set,
      AutorouteControl p_ctrl,
      SortedSet<Item> p_ripped_item_list,
      Map<Item, Integer> p_ripup_costs) {
    String sourceItems = String.join(", ", p_start_set.stream().map(Item::toString).toList());
    String targetItems = String.join(", ", p_dest_set.stream().map(Item::toString).toList());

    MazeSearchAlgo mazeSearchAlgo;
    try {
      mazeSearchAlgo = MazeSearchAlgo.get_instance(p_start_set, p_dest_set, this, p_ctrl);
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
        searchResult = mazeSearchAlgo.find_connection();
      } catch (Exception e) {
        FRLogger.error(
            "AutorouteEngine.autoroute_connection: Exception in mazeSearchAlgo.find_connection", e);
      }
    }

    if (searchResult != null) {
      if (p_ctrl.netNo == 33 || p_ctrl.netNo == 66 || p_ctrl.netNo == 67) {
        String destinationType =
            searchResult.destinationDoor != null
                ? searchResult.destinationDoor.getClass().getSimpleName()
                : "null";
        FRLogger.trace(
            "compare_trace_maze_result_raw net="
                + p_ctrl.netNo
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
            LocateFoundConnectionAlgo.get_instance(
                searchResult,
                p_ctrl,
                this.autorouteSearchTree,
                board.rules.get_trace_angle_restriction(),
                p_ripped_item_list,
                p_ripup_costs);
      } catch (Exception e) {
        FRLogger.error(
            "AutorouteEngine.autoroute_connection: Exception in LocateFoundConnectionAlgo.get_instance",
            e);
      }
    }

    // Always clean up expansion rooms from the search tree, regardless of search outcome.
    // This mirrors v1.9's behavior: clear() is called before any early returns so that
    // stale CompleteFreeSpaceExpansionRoom objects don't pollute subsequent routing attempts.
    if (!this.maintainDatabase) {
      this.clear();
    } else {
      this.reset_all_doors();
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

    if (!p_ctrl.layerActive[autorouteResult.startLayer]
        || !p_ctrl.layerActive[autorouteResult.targetLayer]) {
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
    if (p_ctrl.removeUnconnectedVias) {
      stopConnectionOption = Item.StopConnectionOption.NONE;
    } else {
      stopConnectionOption = Item.StopConnectionOption.FANOUT_VIA;
    }

    for (Item curr_ripped_item : p_ripped_item_list) {
      rippedConnections.addAll(curr_ripped_item.get_connection_items(stopConnectionOption));
      for (int i = 0; i < curr_ripped_item.net_count(); i++) {
        changedNets.add(curr_ripped_item.get_net_no(i));
      }
    }

    // let the observers know the changes in the board database.
    boolean observersActivated = !this.board.observers_active();
    if (observersActivated) {
      this.board.start_notify_observers();
    }

    board.remove_items(rippedConnections);

    for (int currNetNo : changedNets) {
      this.board.remove_trace_tails(currNetNo, stopConnectionOption);
    }
    InsertFoundConnectionAlgo insertFoundConnectionAlgo =
        InsertFoundConnectionAlgo.get_instance(autorouteResult, board, p_ctrl);

    if (observersActivated) {
      this.board.end_notify_observers();
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
  public int get_net_no() {
    return this.netNo;
  }

  /** Returns if the user has stopped the autorouter. */
  public boolean is_stop_requested() {
    if (this.timeLimit != null) {
      if (this.timeLimit.limit_exceeded()) {
        return true;
      }
    }
    if (this.stoppableThread == null) {
      return false;
    }
    return this.stoppableThread.isStopRequested();
  }

  /** Clears all temporary data */
  public void clear() {
    if (completeExpansionRooms != null) {
      for (CompleteFreeSpaceExpansionRoom currRoom : completeExpansionRooms) {
        currRoom.remove_from_tree(this.autorouteSearchTree);
      }
    }
    completeExpansionRooms = null;
    incompleteExpansionRooms = null;
    expansionRoomInstanceCount = 0;
    board.clear_all_item_temporary_autoroute_data();
  }

  /** Draws the shapes of the expansion rooms created so far. */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context, double p_intensity) {
    if (completeExpansionRooms == null) {
      return;
    }
    for (CompleteFreeSpaceExpansionRoom currRoom : completeExpansionRooms) {
      currRoom.draw(p_graphics, p_graphics_context, p_intensity);
    }
    Collection<Item> itemList = this.board.get_items();
    for (Item currItem : itemList) {
      ItemAutorouteInfo autorouteInfo = currItem.get_autoroute_info();
      if (autorouteInfo != null) {
        autorouteInfo.draw(p_graphics, p_graphics_context, p_intensity);
      }
    }
    // this.drillPageArray.draw(p_graphics, p_graphics_context, p_intensity);
  }

  /**
   * Creates a new FreeSpaceExpansionRoom and adds it to the room list. Its shape is normally
   * unbounded at construction time of the room. The final (completed) shape will be a subshape of
   * the start shape, which does not overlap with any obstacle, and it is as big as possible.
   * p_contained_points will remain contained in the shape, after it is completed.
   */
  public IncompleteFreeSpaceExpansionRoom add_incomplete_expansion_room(
      TileShape p_shape, int p_layer, TileShape p_contained_shape) {
    IncompleteFreeSpaceExpansionRoom newRoom =
        new IncompleteFreeSpaceExpansionRoom(p_shape, p_layer, p_contained_shape);
    if (this.incompleteExpansionRooms == null) {
      this.incompleteExpansionRooms = new ArrayList<>();
    }
    this.incompleteExpansionRooms.add(newRoom);
    return newRoom;
  }

  /**
   * Returns the first element in the list of incomplete expansion rooms or null, if the list is
   * empty.
   */
  public IncompleteFreeSpaceExpansionRoom get_first_incomplete_expansion_room() {
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
  public void remove_incomplete_expansion_room(IncompleteFreeSpaceExpansionRoom p_room) {
    this.remove_all_doors(p_room);
    incompleteExpansionRooms.remove(p_room);
  }

  /**
   * Removes a complete expansion room from the database and creates new incomplete expansion rooms
   * for the neighbours.
   */
  public void remove_complete_expansion_room(CompleteFreeSpaceExpansionRoom p_room) {
    // create new incomplete expansion rooms for all neighbours
    TileShape roomShape = p_room.get_shape();
    int roomLayer = p_room.get_layer();
    Collection<ExpansionDoor> roomDoors = p_room.get_doors();
    for (ExpansionDoor currDoor : roomDoors) {
      ExpansionRoom currNeighbour = currDoor.other_room(p_room);
      if (currNeighbour == null) {
        continue;
      }
      currNeighbour.remove_door(currDoor);
      TileShape neighbourShape = currNeighbour.get_shape();
      TileShape intersection = roomShape.intersection(neighbourShape);
      if (intersection.dimension() == 1) {
        // add a new incomplete room to currNeighbour.
        int[] touchingSides = roomShape.touching_sides(neighbourShape);
        Line[] lineArr = new Line[1];
        lineArr[0] = neighbourShape.border_line(touchingSides[1]).opposite();
        Simplex newIncompleteRoomShape = Simplex.get_instance(lineArr);
        IncompleteFreeSpaceExpansionRoom newIncompleteRoom =
            add_incomplete_expansion_room(newIncompleteRoomShape, roomLayer, intersection);
        ExpansionDoor newDoor = new ExpansionDoor(currNeighbour, newIncompleteRoom, 1);
        currNeighbour.add_door(newDoor);
        newIncompleteRoom.add_door(newDoor);
      }
    }
    this.remove_all_doors(p_room);
    p_room.remove_from_tree(this.autorouteSearchTree);
    if (completeExpansionRooms != null) {
      completeExpansionRooms.remove(p_room);
    } else {
      FRLogger.warn(
          "AutorouteEngine.remove_complete_expansion_room: this.completeExpansionRooms is null");
    }
    this.drillPageArray.invalidate(roomShape);
  }

  /**
   * Completes the shape of p_room. Returns the resulting rooms after completing the shape. p_room
   * will no more exist after this function.
   */
  public Collection<CompleteFreeSpaceExpansionRoom> complete_expansion_room(
      IncompleteFreeSpaceExpansionRoom p_room) {

    try {
      Collection<CompleteFreeSpaceExpansionRoom> result = new ArrayList<>();
      TileShape fromDoorShape = null;
      SearchTreeObject ignoreObject = null;
      Collection<ExpansionDoor> roomDoors = p_room.get_doors();
      for (ExpansionDoor currDoor : roomDoors) {
        ExpansionRoom otherRoom = currDoor.other_room(p_room);
        if (otherRoom instanceof CompleteFreeSpaceExpansionRoom room && currDoor.dimension == 2) {
          fromDoorShape = currDoor.get_shape();
          ignoreObject = room;
          break;
        }
      }
      FRLogger.trace(
          "COMPLETE_ROOM input"
              + ", net="
              + this.netNo
              + ", layer="
              + p_room.get_layer()
              + ", room_bounds="
              + describe_shape_bounds(p_room.get_shape())
              + ", contained_bounds="
              + describe_shape_bounds(p_room.get_contained_shape())
              + ", from_door_bounds="
              + describe_shape_bounds(fromDoorShape)
              + ", ignoreObject="
              + (ignoreObject == null ? "null" : ignoreObject.getClass().getSimpleName()));
      Collection<IncompleteFreeSpaceExpansionRoom> completedShapes =
          this.autorouteSearchTree.complete_shape(p_room, this.netNo, ignoreObject, fromDoorShape);
      int initialCandidateIndex = 0;
      for (IncompleteFreeSpaceExpansionRoom initialCandidate : completedShapes) {
        FRLogger.trace(
            "COMPLETE_ROOM initial_candidate"
                + ", net="
                + this.netNo
                + ", layer="
                + initialCandidate.get_layer()
                + ", index="
                + initialCandidateIndex
                + ", dimension="
                + initialCandidate.get_shape().dimension()
                + ", incomplete_bounds="
                + describe_shape_bounds(initialCandidate.get_shape())
                + ", from_door_bounds="
                + describe_shape_bounds(fromDoorShape));
        ++initialCandidateIndex;
      }
      this.remove_incomplete_expansion_room(p_room);
      boolean isFirstCompletedRoom = true;
      for (IncompleteFreeSpaceExpansionRoom curr_incomplete_room : completedShapes) {
        if (curr_incomplete_room.get_shape().dimension() != 2) {
          continue;
        }
        if (isFirstCompletedRoom) {
          isFirstCompletedRoom = false;
          FRLogger.trace(
              "COMPLETE_ROOM first_candidate"
                  + ", net="
                  + this.netNo
                  + ", layer="
                  + curr_incomplete_room.get_layer()
                  + ", incomplete_bounds="
                  + describe_shape_bounds(curr_incomplete_room.get_shape())
                  + ", from_door_bounds="
                  + describe_shape_bounds(fromDoorShape));
          CompleteFreeSpaceExpansionRoom completedRoom =
              this.add_complete_room(curr_incomplete_room);
          if (completedRoom != null) {
            result.add(completedRoom);
          }
        } else {
          // the shape of the first completed room may have changed and may
          // intersect now with the other shapes. Therefore, the completed shapes
          // have to be recalculated.
          Collection<IncompleteFreeSpaceExpansionRoom> currCompletedShapes =
              this.autorouteSearchTree.complete_shape(
                  curr_incomplete_room, this.netNo, ignoreObject, fromDoorShape);
          for (IncompleteFreeSpaceExpansionRoom tmp_room : currCompletedShapes) {
            FRLogger.trace(
                "COMPLETE_ROOM recalc_candidate"
                    + ", net="
                    + this.netNo
                    + ", layer="
                    + tmp_room.get_layer()
                    + ", incomplete_bounds="
                    + describe_shape_bounds(tmp_room.get_shape())
                    + ", from_door_bounds="
                    + describe_shape_bounds(fromDoorShape));
            CompleteFreeSpaceExpansionRoom completedRoom = this.add_complete_room(tmp_room);
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
  private CompleteFreeSpaceExpansionRoom add_complete_room(
      IncompleteFreeSpaceExpansionRoom p_room) {
    CompleteFreeSpaceExpansionRoom completedRoom =
        (CompleteFreeSpaceExpansionRoom) calculate_doors(p_room);
    if (completedRoom == null || completedRoom.get_shape().dimension() != 2) {
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
            + completedRoom.get_layer()
            + ", bounds="
            + describe_shape_bounds(completedRoom.get_shape()));
    return completedRoom;
  }

  private static String describe_shape_bounds(TileShape p_shape) {
    if (p_shape == null) {
      return "null";
    }
    IntBox bounds = p_shape.bounding_box();
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  /**
   * Calculates the neighbours of p_room and inserts doors to the new created neighbour rooms. The
   * shape of the result room may be different to the shape of p_room
   */
  private CompleteExpansionRoom calculate_doors(ExpansionRoom p_room) {
    CompleteExpansionRoom result;
    if (this.autorouteSearchTree instanceof ShapeSearchTree90Degree) {
      result = SortedOrthogonalRoomNeighbours.calculate(p_room, this);
    } else if (this.autorouteSearchTree instanceof ShapeSearchTree45Degree) {
      result = Sorted45DegreeRoomNeighbours.calculate(p_room, this);
    } else {
      result = SortedRoomNeighbours.calculate(p_room, this);
    }
    return result;
  }

  /**
   * Completes the shapes of the neighbour rooms of p_room, so that the doors of p_room will not
   * change later on.
   */
  public void complete_neighbour_rooms(CompleteExpansionRoom p_room) {
    if (p_room.get_doors() == null) {
      return;
    }
    // Keep v1.9 semantics: completing a neighbour can mutate door topology, so
    // restart iteration on the updated door set.
    Iterator<ExpansionDoor> it = p_room.get_doors().iterator();
    while (it.hasNext()) {
      ExpansionDoor currDoor = it.next();
      // cast to ExpansionRoom because ExpansionDoor.otherRoom works differently with
      // parameter type CompleteExpansionRoom.
      ExpansionRoom neighbourRoom = currDoor.other_room((ExpansionRoom) p_room);
      if (neighbourRoom == null) {
        continue;
      }
      if (neighbourRoom instanceof IncompleteFreeSpaceExpansionRoom room) {
        this.complete_expansion_room(room);
        it = p_room.get_doors().iterator();
      } else if (neighbourRoom instanceof ObstacleExpansionRoom obstacle_neighbour_room) {
        if (!obstacle_neighbour_room.all_doors_calculated()) {
          this.calculate_doors(obstacle_neighbour_room);
          obstacle_neighbour_room.set_doors_calculated(true);
        }
      }
    }
  }

  /**
   * Invalidates all drill pages intersecting with p_shape, so they must be recalculated at the next
   * call of get_ddrills()
   */
  public void invalidate_drill_pages(TileShape p_shape) {
    this.drillPageArray.invalidate(p_shape);
  }

  /** Removes all doors from p_room */
  void remove_all_doors(ExpansionRoom p_room) {
    for (ExpansionDoor currDoor : p_room.get_doors()) {
      ExpansionRoom otherRoom = currDoor.other_room(p_room);
      if (otherRoom == null) {
        continue;
      }
      otherRoom.remove_door(currDoor);
      if (otherRoom instanceof IncompleteFreeSpaceExpansionRoom room) {
        this.remove_incomplete_expansion_room(room);
      }
    }
    p_room.clear_doors();
  }

  /**
   * Returns all complete free space expansion rooms with a target door to an item in the set
   * p_items.
   */
  Set<CompleteFreeSpaceExpansionRoom> get_rooms_with_target_items(Set<Item> p_items) {
    Set<CompleteFreeSpaceExpansionRoom> result = new TreeSet<>();
    if (this.completeExpansionRooms != null) {
      for (CompleteFreeSpaceExpansionRoom currRoom : this.completeExpansionRooms) {
        Collection<TargetItemExpansionDoor> targetDoorList = currRoom.get_target_doors();
        for (TargetItemExpansionDoor curr_target_door : targetDoorList) {
          Item currTargetItem = curr_target_door.item;
          if (p_items.contains(currTargetItem)) {
            result.add(currRoom);
          }
        }
      }
    }
    return result;
  }

  /** Checks, if the internal datastructure is valid. */
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
   * Reset all doors for autorouting the next connection, in case the autorouting database is
   * retained.
   */
  private void reset_all_doors() {
    if (this.completeExpansionRooms != null) {
      for (ExpansionRoom currRoom : this.completeExpansionRooms) {
        currRoom.reset_doors();
      }
    }
    Collection<Item> itemList = this.board.get_items();
    for (Item currItem : itemList) {
      ItemAutorouteInfo currAutorouteInfo = currItem.get_autoroute_info_pur();
      if (currAutorouteInfo != null) {
        currAutorouteInfo.reset_doors();
        currAutorouteInfo.set_precalculated_connection(null);
      }
    }
    this.drillPageArray.reset();
  }

  protected int generate_room_id_no() {
    return ++expansionRoomInstanceCount;
  }
}

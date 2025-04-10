package app.freerouting.autoroute;

import app.freerouting.board.*;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Temporary autoroute data stored on the RoutingBoard.
 */
public class AutorouteEngine
{

  static final int TRACE_WIDTH_TOLERANCE = 2;
  /**
   * The current search tree used in autorouting. It depends on the trac clearance class used in the
   * autoroute algorithm.
   */
  public final ShapeSearchTree autoroute_search_tree;
  /**
   * If maintain_database, the autorouter database is maintained after a connection is completed for
   * performance reasons.
   */
  public final boolean maintain_database;
  /**
   * The 2-dimensional array of rectangular pages of ExpansionDrills
   */
  final DrillPageArray drill_page_array;
  /**
   * The PCB-board of this autoroute algorithm.
   */
  final RoutingBoard board;
  /**
   * To be able to stop the expansion algorithm.
   */
  Stoppable stoppable_thread;
  /**
   * The net number used for routing in this autoroute algorithm.
   */
  private int net_no;
  /**
   * To stop the expansion algorithm after a time limit is exceeded.
   */
  private TimeLimit time_limit;
  /**
   * The list of incomplete expansion rooms on the routing board
   */
  private List<IncompleteFreeSpaceExpansionRoom> incomplete_expansion_rooms;
  /**
   * The list of complete expansion rooms on the routing board
   */
  private List<CompleteFreeSpaceExpansionRoom> complete_expansion_rooms;
  /**
   * The count of expansion rooms created so far
   */
  private int expansion_room_instance_count = 0;

  /**
   * Creates a new instance of BoardAutorouteEngine If p_maintain_database, the autorouter database
   * is maintained after a connection is completed for performance reasons.
   */
  public AutorouteEngine(RoutingBoard p_board, int p_trace_clearance_class_no, boolean p_maintain_database, boolean p_use_slow_algorithm)
  {
    this.board = p_board;
    this.maintain_database = p_maintain_database;
    this.net_no = -1;
    this.autoroute_search_tree = p_board.search_tree_manager.get_autoroute_tree(p_trace_clearance_class_no, p_use_slow_algorithm);
    int max_drill_page_width = (int) (5 * p_board.rules.get_default_via_diameter());
    max_drill_page_width = Math.max(max_drill_page_width, 10000);
    this.drill_page_array = new DrillPageArray(this.board, max_drill_page_width);
    this.stoppable_thread = null;
  }

  public void init_connection(int p_net_no, Stoppable p_stoppable_thread, TimeLimit p_time_limit)
  {
    if (this.maintain_database)
    {
      if (p_net_no != this.net_no)
      {
        if (this.complete_expansion_rooms != null)
        {
          // invalidate the net dependent complete free space expansion rooms.
          Collection<CompleteFreeSpaceExpansionRoom> rooms_to_remove = new LinkedList<>();
          for (CompleteFreeSpaceExpansionRoom curr_room : complete_expansion_rooms)
          {
            if (curr_room.is_net_dependent())
            {
              rooms_to_remove.add(curr_room);
            }
          }
          for (CompleteFreeSpaceExpansionRoom curr_room : rooms_to_remove)
          {
            this.remove_complete_expansion_room(curr_room);
          }
        }
        // invalidate the neighbour rooms of the items of p_net_no
        Collection<Item> item_list = this.board.get_items();
        for (Item curr_item : item_list)
        {
          if (curr_item.contains_net(p_net_no))
          {
            this.board.additional_update_after_change(curr_item);
          }
        }
      }
    }
    this.net_no = p_net_no;
    this.stoppable_thread = p_stoppable_thread;
    this.time_limit = p_time_limit;
  }

  /**
   * Auto-routes a connection between p_start_set and p_dest_set.
   * Returns ALREADY_CONNECTED, ROUTED, NOT_ROUTED, or INSERT_ERROR.
   */
  public AutorouteAttemptResult autoroute_connection(Set<Item> p_start_set, Set<Item> p_dest_set, AutorouteControl p_ctrl, SortedSet<Item> p_ripped_item_list)
  {
    String sourceItems = String.join(", ", p_start_set
        .stream()
        .map(Item::toString)
        .toList());
    String targetItems = String.join(", ", p_dest_set
        .stream()
        .map(Item::toString)
        .toList());
    
    MazeSearchAlgo maze_search_algo;
    try
    {
      maze_search_algo = MazeSearchAlgo.get_instance(p_start_set, p_dest_set, this, p_ctrl);
    } catch (Exception e)
    {
      FRLogger.error("AutorouteEngine.autoroute_connection: Exception in MazeSearchAlgo.get_instance", e);
      maze_search_algo = null;
    }

    if (maze_search_algo == null)
    {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED, "Failed to route connection between " + sourceItems + " and " + targetItems + ", because the maze search algorithm could not be created.");
    }

    MazeSearchAlgo.Result search_result = null;
    if (maze_search_algo != null)
    {
      try
      {
        search_result = maze_search_algo.find_connection();
      } catch (Exception e)
      {
        FRLogger.error("AutorouteEngine.autoroute_connection: Exception in maze_search_algo.find_connection", e);
      }
    }

    if (search_result == null)
    {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED, "Failed to route connection between " + sourceItems + " and " + targetItems + ", because no connection was found between their nets.");
    }

    LocateFoundConnectionAlgo autoroute_result = null;
    if (search_result != null)
    {
      try
      {
        autoroute_result = LocateFoundConnectionAlgo.get_instance(search_result, p_ctrl, this.autoroute_search_tree, board.rules.get_trace_angle_restriction(), p_ripped_item_list);
      } catch (Exception e)
      {
        FRLogger.error("AutorouteEngine.autoroute_connection: Exception in LocateFoundConnectionAlgo.get_instance", e);
      }
    }

    if (!this.maintain_database)
    {
      this.clear();
    }
    else
    {
      this.reset_all_doors();
    }

    if (autoroute_result == null)
    {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED, "Failed to route connection between " + sourceItems + " and " + targetItems + ".");
    }

    if (!p_ctrl.layer_active[autoroute_result.start_layer] || !p_ctrl.layer_active[autoroute_result.target_layer])
    {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED, "Failed to route connection between " + sourceItems + " and " + targetItems + ", because some of their layers are disabled.");
    }

    if (autoroute_result.connection_items == null)
    {
      FRLogger.debug("AutorouteEngine.autoroute_connection: result_items != null expected");
      return new AutorouteAttemptResult(AutorouteAttemptState.SKIPPED, "No new connections were made between " + sourceItems + " and " + targetItems + ".");
    }

    // Delete the ripped connections.
    SortedSet<Item> ripped_connections = new TreeSet<>();
    Set<Integer> changed_nets = new TreeSet<>();
    Item.StopConnectionOption stop_connection_option;
    if (p_ctrl.remove_unconnected_vias)
    {
      stop_connection_option = Item.StopConnectionOption.NONE;
    }
    else
    {
      stop_connection_option = Item.StopConnectionOption.FANOUT_VIA;
    }

    for (Item curr_ripped_item : p_ripped_item_list)
    {
      ripped_connections.addAll(curr_ripped_item.get_connection_items(stop_connection_option));
      for (int i = 0; i < curr_ripped_item.net_count(); ++i)
      {
        changed_nets.add(curr_ripped_item.get_net_no(i));
      }
    }

    // let the observers know the changes in the board database.
    boolean observers_activated = !this.board.observers_active();
    if (observers_activated)
    {
      this.board.start_notify_observers();
    }

    board.remove_items(ripped_connections);

    for (int curr_net_no : changed_nets)
    {
      this.board.remove_trace_tails(curr_net_no, stop_connection_option);
    }
    InsertFoundConnectionAlgo insert_found_connection_algo = InsertFoundConnectionAlgo.get_instance(autoroute_result, board, p_ctrl);

    if (observers_activated)
    {
      this.board.end_notify_observers();
    }
    if (insert_found_connection_algo == null)
    {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED, "Failed to route connection between " + sourceItems + " and " + targetItems + ", because the new connection could not be inserted.");
    }

    return new AutorouteAttemptResult(AutorouteAttemptState.ROUTED);
  }

  /**
   * Returns the net number of the current connection to route.
   */
  public int get_net_no()
  {
    return this.net_no;
  }

  /**
   * Returns if the user has stopped the autorouter.
   */
  public boolean is_stop_requested()
  {
    if (this.time_limit != null)
    {
      if (this.time_limit.limit_exceeded())
      {
        return true;
      }
    }
    if (this.stoppable_thread == null)
    {
      return false;
    }
    return this.stoppable_thread.isStopRequested();
  }

  /**
   * Clears all temporary data
   */
  public void clear()
  {
    if (complete_expansion_rooms != null)
    {
      for (CompleteFreeSpaceExpansionRoom curr_room : complete_expansion_rooms)
      {
        curr_room.remove_from_tree(this.autoroute_search_tree);
      }
    }
    complete_expansion_rooms = null;
    incomplete_expansion_rooms = null;
    expansion_room_instance_count = 0;
    board.clear_all_item_temporary_autoroute_data();
  }

  /**
   * Draws the shapes of the expansion rooms created so far.
   */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context, double p_intensity)
  {
    if (complete_expansion_rooms == null)
    {
      return;
    }
    for (CompleteFreeSpaceExpansionRoom curr_room : complete_expansion_rooms)
    {
      curr_room.draw(p_graphics, p_graphics_context, p_intensity);
    }
    Collection<Item> item_list = this.board.get_items();
    for (Item curr_item : item_list)
    {
      ItemAutorouteInfo autoroute_info = curr_item.get_autoroute_info();
      if (autoroute_info != null)
      {
        autoroute_info.draw(p_graphics, p_graphics_context, p_intensity);
      }
    }
    // this.drill_page_array.draw(p_graphics, p_graphics_context, p_intensity);
  }

  /**
   * Creates a new FreeSpaceExpansionRoom and adds it to the room list. Its shape is normally
   * unbounded at construction time of the room. The final (completed) shape will be a subshape of
   * the start shape, which does not overlap with any obstacle, and it is as big as possible.
   * p_contained_points will remain contained in the shape, after it is completed.
   */
  public IncompleteFreeSpaceExpansionRoom add_incomplete_expansion_room(TileShape p_shape, int p_layer, TileShape p_contained_shape)
  {
    IncompleteFreeSpaceExpansionRoom new_room = new IncompleteFreeSpaceExpansionRoom(p_shape, p_layer, p_contained_shape);
    if (this.incomplete_expansion_rooms == null)
    {
      this.incomplete_expansion_rooms = new LinkedList<>();
    }
    this.incomplete_expansion_rooms.add(new_room);
    return new_room;
  }

  /**
   * Returns the first element in the list of incomplete expansion rooms or null, if the list is
   * empty.
   */
  public IncompleteFreeSpaceExpansionRoom get_first_incomplete_expansion_room()
  {
    if (incomplete_expansion_rooms == null)
    {
      return null;
    }
    if (incomplete_expansion_rooms.isEmpty())
    {
      return null;
    }
    Iterator<IncompleteFreeSpaceExpansionRoom> it = incomplete_expansion_rooms.iterator();
    return it.next();
  }

  /**
   * Removes an incomplete room from the database.
   */
  public void remove_incomplete_expansion_room(IncompleteFreeSpaceExpansionRoom p_room)
  {
    this.remove_all_doors(p_room);
    incomplete_expansion_rooms.remove(p_room);
  }

  /**
   * Removes a complete expansion room from the database and creates new incomplete expansion rooms
   * for the neighbours.
   */
  public void remove_complete_expansion_room(CompleteFreeSpaceExpansionRoom p_room)
  {
    // create new incomplete expansion rooms for all  neighbours
    TileShape room_shape = p_room.get_shape();
    int room_layer = p_room.get_layer();
    Collection<ExpansionDoor> room_doors = p_room.get_doors();
    for (ExpansionDoor curr_door : room_doors)
    {
      ExpansionRoom curr_neighbour = curr_door.other_room(p_room);
      if (curr_neighbour == null)
      {
        continue;
      }
      curr_neighbour.remove_door(curr_door);
      TileShape neighbour_shape = curr_neighbour.get_shape();
      TileShape intersection = room_shape.intersection(neighbour_shape);
      if (intersection.dimension() == 1)
      {
        // add a new incomplete room to curr_neighbour.
        int[] touching_sides = room_shape.touching_sides(neighbour_shape);
        Line[] line_arr = new Line[1];
        line_arr[0] = neighbour_shape
            .border_line(touching_sides[1])
            .opposite();
        Simplex new_incomplete_room_shape = Simplex.get_instance(line_arr);
        IncompleteFreeSpaceExpansionRoom new_incomplete_room = add_incomplete_expansion_room(new_incomplete_room_shape, room_layer, intersection);
        ExpansionDoor new_door = new ExpansionDoor(curr_neighbour, new_incomplete_room, 1);
        curr_neighbour.add_door(new_door);
        new_incomplete_room.add_door(new_door);
      }
    }
    this.remove_all_doors(p_room);
    p_room.remove_from_tree(this.autoroute_search_tree);
    if (complete_expansion_rooms != null)
    {
      complete_expansion_rooms.remove(p_room);
    }
    else
    {
      FRLogger.warn("AutorouteEngine.remove_complete_expansion_room: this.complete_expansion_rooms is null");
    }
    this.drill_page_array.invalidate(room_shape);
  }

  /**
   * Completes the shape of p_room. Returns the resulting rooms after completing the shape. p_room
   * will no more exist after this function.
   */
  public Collection<CompleteFreeSpaceExpansionRoom> complete_expansion_room(IncompleteFreeSpaceExpansionRoom p_room)
  {

    try
    {
      Collection<CompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
      TileShape from_door_shape = null;
      SearchTreeObject ignore_object = null;
      Collection<ExpansionDoor> room_doors = p_room.get_doors();
      for (ExpansionDoor curr_door : room_doors)
      {
        ExpansionRoom other_room = curr_door.other_room(p_room);
        if (other_room instanceof CompleteFreeSpaceExpansionRoom && curr_door.dimension == 2)
        {
          from_door_shape = curr_door.get_shape();
          ignore_object = (CompleteFreeSpaceExpansionRoom) other_room;
          break;
        }
      }
      Collection<IncompleteFreeSpaceExpansionRoom> completed_shapes = this.autoroute_search_tree.complete_shape(p_room, this.net_no, ignore_object, from_door_shape);
      this.remove_incomplete_expansion_room(p_room);
      boolean is_first_completed_room = true;
      for (IncompleteFreeSpaceExpansionRoom curr_incomplete_room : completed_shapes)
      {
        if (curr_incomplete_room
            .get_shape()
            .dimension() != 2)
        {
          continue;
        }
        if (is_first_completed_room)
        {
          is_first_completed_room = false;
          CompleteFreeSpaceExpansionRoom completed_room = this.add_complete_room(curr_incomplete_room);
          if (completed_room != null)
          {
            result.add(completed_room);
          }
        }
        else
        {
          // the shape of the first completed room may have changed and may
          // intersect now with the other shapes. Therefore, the completed shapes
          // have to be recalculated.
          Collection<IncompleteFreeSpaceExpansionRoom> curr_completed_shapes = this.autoroute_search_tree.complete_shape(curr_incomplete_room, this.net_no, ignore_object, from_door_shape);
          for (IncompleteFreeSpaceExpansionRoom tmp_room : curr_completed_shapes)
          {
            CompleteFreeSpaceExpansionRoom completed_room = this.add_complete_room(tmp_room);
            if (completed_room != null)
            {
              result.add(completed_room);
            }
          }
        }
      }
      return result;
    } catch (Exception e)
    {
      FRLogger.error("AutorouteEngine.complete_expansion_room: ", e);
      return new LinkedList<>();
    }
  }

  /**
   * Calculates the doors and adds the completed room to the room database.
   */
  private CompleteFreeSpaceExpansionRoom add_complete_room(IncompleteFreeSpaceExpansionRoom p_room)
  {
    CompleteFreeSpaceExpansionRoom completed_room = (CompleteFreeSpaceExpansionRoom) calculate_doors(p_room);
    if (completed_room == null || completed_room
        .get_shape()
        .dimension() != 2)
    {
      return null;
    }
    if (complete_expansion_rooms == null)
    {
      complete_expansion_rooms = new LinkedList<>();
    }
    complete_expansion_rooms.add(completed_room);
    this.autoroute_search_tree.insert(completed_room);
    return completed_room;
  }

  /**
   * Calculates the neighbours of p_room and inserts doors to the new created neighbour rooms. The
   * shape of the result room may be different to the shape of p_room
   */
  private CompleteExpansionRoom calculate_doors(ExpansionRoom p_room)
  {
    CompleteExpansionRoom result;
    if (this.autoroute_search_tree instanceof ShapeSearchTree90Degree)
    {
      result = SortedOrthogonalRoomNeighbours.calculate(p_room, this);
    }
    else if (this.autoroute_search_tree instanceof ShapeSearchTree45Degree)
    {
      result = Sorted45DegreeRoomNeighbours.calculate(p_room, this);
    }
    else
    {
      result = SortedRoomNeighbours.calculate(p_room, this);
    }
    return result;
  }

  /**
   * Completes the shapes of the neighbour rooms of p_room, so that the doors of p_room will not
   * change later on.
   */
  public void complete_neighbour_rooms(CompleteExpansionRoom p_room)
  {
    if (p_room.get_doors() == null)
    {
      return;
    }
    Iterator<ExpansionDoor> it = p_room
        .get_doors()
        .iterator();
    while (it.hasNext())
    {
      ExpansionDoor curr_door = it.next();
      // cast to ExpansionRoom because ExpansionDoor.other_room works differently with
      // parameter type CompleteExpansionRoom.
      ExpansionRoom neighbour_room = curr_door.other_room((ExpansionRoom) p_room);
      if (neighbour_room == null)
      {
        continue;
      }
      if (neighbour_room instanceof IncompleteFreeSpaceExpansionRoom)
      {
        this.complete_expansion_room((IncompleteFreeSpaceExpansionRoom) neighbour_room);
        // restart reading because the doors have changed
        it = p_room
            .get_doors()
            .iterator();
      }
      else if (neighbour_room instanceof ObstacleExpansionRoom obstacle_neighbour_room)
      {
        if (!obstacle_neighbour_room.all_doors_calculated())
        {
          this.calculate_doors(obstacle_neighbour_room);
          obstacle_neighbour_room.set_doors_calculated(true);
        }
      }
    }
  }

  /**
   * Invalidates all drill pages intersecting with p_shape, so they must be recalculated at the
   * next call of get_ddrills()
   */
  public void invalidate_drill_pages(TileShape p_shape)
  {
    this.drill_page_array.invalidate(p_shape);
  }

  /**
   * Removes all doors from p_room
   */
  void remove_all_doors(ExpansionRoom p_room)
  {
    for (ExpansionDoor curr_door : p_room.get_doors())
    {
      ExpansionRoom other_room = curr_door.other_room(p_room);
      if (other_room == null)
      {
        continue;
      }
      other_room.remove_door(curr_door);
      if (other_room instanceof IncompleteFreeSpaceExpansionRoom)
      {
        this.remove_incomplete_expansion_room((IncompleteFreeSpaceExpansionRoom) other_room);
      }
    }
    p_room.clear_doors();
  }

  /**
   * Returns all complete free space expansion rooms with a target door to an item in the set
   * p_items.
   */
  Set<CompleteFreeSpaceExpansionRoom> get_rooms_with_target_items(Set<Item> p_items)
  {
    Set<CompleteFreeSpaceExpansionRoom> result = new TreeSet<>();
    if (this.complete_expansion_rooms != null)
    {
      for (CompleteFreeSpaceExpansionRoom curr_room : this.complete_expansion_rooms)
      {
        Collection<TargetItemExpansionDoor> target_door_list = curr_room.get_target_doors();
        for (TargetItemExpansionDoor curr_target_door : target_door_list)
        {
          Item curr_target_item = curr_target_door.item;
          if (p_items.contains(curr_target_item))
          {
            result.add(curr_room);
          }
        }
      }
    }
    return result;
  }

  /**
   * Checks, if the internal datastructure is valid.
   */
  public boolean validate()
  {
    if (complete_expansion_rooms == null)
    {
      return true;
    }
    boolean result = true;
    for (CompleteFreeSpaceExpansionRoom curr_room : complete_expansion_rooms)
    {
      if (!curr_room.validate(this))
      {
        result = false;
      }
    }
    return result;
  }

  /**
   * Reset all doors for autorouting the next connection, in case the autorouting database is
   * retained.
   */
  private void reset_all_doors()
  {
    if (this.complete_expansion_rooms != null)
    {
      for (ExpansionRoom curr_room : this.complete_expansion_rooms)
      {
        curr_room.reset_doors();
      }
    }
    Collection<Item> item_list = this.board.get_items();
    for (Item curr_item : item_list)
    {
      ItemAutorouteInfo curr_autoroute_info = curr_item.get_autoroute_info_pur();
      if (curr_autoroute_info != null)
      {
        curr_autoroute_info.reset_doors();
        curr_autoroute_info.set_precalculated_connection(null);
      }
    }
    this.drill_page_array.reset();
  }

  protected int generate_room_id_no()
  {
    return ++expansion_room_instance_count;
  }
}
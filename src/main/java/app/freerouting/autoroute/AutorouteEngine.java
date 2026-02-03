package app.freerouting.autoroute;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.board.ShapeSearchTree45Degree;
import app.freerouting.board.ShapeSearchTree90Degree;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Core engine that orchestrates the execution of autorouter passes and manages routing state.
 *
 * <p>This class serves as the main coordinator for the autorouting algorithm, responsible for:
 * <ul>
 *   <li><strong>Pass Management:</strong> Sequencing multiple routing passes with different strategies</li>
 *   <li><strong>Connection Selection:</strong> Determining which incomplete connections to route</li>
 *   <li><strong>Routing Execution:</strong> Delegating actual routing to {@link AutorouteControl}</li>
 *   <li><strong>State Tracking:</strong> Monitoring incomplete items and routing progress</li>
 *   <li><strong>Optimization:</strong> Managing via minimization and post-routing cleanup</li>
 * </ul>
 *
 * <p><strong>Multi-Pass Strategy:</strong>
 * The autorouter executes multiple passes with progressively relaxed constraints:
 * <ol>
 *   <li><strong>Pass 1:</strong> Strict routing with minimal vias and optimal paths</li>
 *   <li><strong>Pass 2:</strong> Relaxed via limits, wider search space</li>
 *   <li><strong>Pass 3+:</strong> Further relaxed constraints for difficult connections</li>
 * </ol>
 *
 * <p><strong>Connection Priority:</strong>
 * Routes connections in order of:
 * <ul>
 *   <li>Shortest airline distance first (easier connections)</li>
 *   <li>Then progressively harder connections</li>
 *   <li>Special handling for SMD (Surface Mount Device) connections</li>
 * </ul>
 *
 * <p><strong>Via Optimization:</strong>
 * After routing, attempts to:
 * <ul>
 *   <li>Remove unnecessary vias where traces can stay on one layer</li>
 *   <li>Minimize layer transitions</li>
 *   <li>Improve routing quality and manufacturability</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong>
 * This class is designed for single-threaded execution per instance. For multi-threaded
 * routing, create separate AutorouteEngine instances.
 *
 * <p><strong>Interruption:</strong>
 * Routing can be interrupted via the {@link Stoppable} interface. The engine
 * checks for stop requests between routing operations and at strategic points.
 *
 * <p><strong>Usage Pattern:</strong>
 * <pre>{@code
 * AutorouteEngine engine = new AutorouteEngine(board, thread, settings);
 * engine.autoroute_passes();  // Execute routing
 * if (settings.viaOptimizationEnabled) {
 *     engine.remove_tails(Item.StopConnectionOption.VIA);  // Clean up
 * }
 * }</pre>
 *
 * @see AutorouteControl
 * @see CompleteExpansionRoom
 * @see MazeSearchAlgo
 * @see Stoppable
 */
public class AutorouteEngine {

  /**
   * Tolerance value for trace width matching (2 board units).
   *
   * <p>Used when comparing trace widths to determine if they're considered
   * equivalent. Allows for small variations in trace width without triggering
   * routing conflicts.
   */
  static final int TRACE_WIDTH_TOLERANCE = 2;

  /**
   * The search tree optimized for autorouting operations.
   *
   * <p>This specialized search tree is configured for the specific trace clearance
   * class used in the current autoroute algorithm. It provides efficient spatial
   * queries for:
   * <ul>
   *   <li>Finding obstacles during routing</li>
   *   <li>Checking clearance violations</li>
   *   <li>Locating nearby items for expansion</li>
   * </ul>
   *
   * <p>The tree structure is optimized for the autorouter's frequent queries
   * and may differ from the board's main search tree.
   */
  public final ShapeSearchTree autoroute_search_tree;

  /**
   * Flag controlling database maintenance during routing.
   *
   * <p>When {@code true}, the autorouter maintains its internal database after
   * each connection completion. This improves performance for subsequent routing
   * operations by:
   * <ul>
   *   <li>Keeping expansion rooms up-to-date</li>
   *   <li>Avoiding full database rebuilds</li>
   *   <li>Enabling incremental updates</li>
   * </ul>
   *
   * <p>When {@code false}, the database is rebuilt as needed, which may be slower
   * but uses less memory.
   */
  public final boolean maintain_database;

  /**
   * Two-dimensional array managing rectangular pages of expansion drills.
   *
   * <p>The drill page array partitions the board into rectangular regions,
   * each containing expansion drill information for that area. This spatial
   * partitioning enables:
   * <ul>
   *   <li>Efficient local searches during expansion</li>
   *   <li>Reduced memory footprint (load only needed pages)</li>
   *   <li>Better cache locality</li>
   * </ul>
   *
   * @see DrillPageArray
   * @see ExpansionDrill
   */
  final DrillPageArray drill_page_array;

  /**
   * The PCB routing board for this autoroute algorithm instance.
   *
   * <p>Contains all design data including components, nets, rules, and existing routing.
   *
   * @see RoutingBoard
   */
  final RoutingBoard board;

  /**
   * Reference to stoppable thread for interruption checks.
   *
   * <p>The autorouter periodically checks {@link Stoppable#isStopRequested()}
   * to allow user interruption. When true, routing terminates gracefully.
   */
  Stoppable stoppable_thread;

  /**
   * The net number currently being routed by this algorithm instance.
   *
   * <p>Set before routing each connection and used to:
   * <ul>
   *   <li>Determine which items belong to the same net</li>
   *   <li>Apply net-specific routing rules</li>
   *   <li>Avoid conflicts with other nets</li>
   * </ul>
   */
  private int net_no;

  /**
   * Time limit controller to prevent excessive routing time per connection.
   *
   * <p>Stops the expansion algorithm when:
   * <ul>
   *   <li>Maximum routing time for a single connection is exceeded</li>
   *   <li>Overall autorouter time budget is exhausted</li>
   * </ul>
   *
   * <p>Helps ensure the autorouter doesn't get stuck on difficult connections.
   *
   * @see TimeLimit
   */
  private TimeLimit time_limit;

  /**
   * List of incomplete expansion rooms found on the routing board.
   *
   * <p>Expansion rooms represent free space areas where routing can occur.
   * Incomplete rooms are those that haven't been fully explored yet and may
   * contain additional routing opportunities.
   *
   * <p>Updated during routing as new areas are explored.
   *
   * @see IncompleteFreeSpaceExpansionRoom
   */
  private List<IncompleteFreeSpaceExpansionRoom> incomplete_expansion_rooms;

  /**
   * List of complete expansion rooms on the routing board.
   *
   * <p>Complete expansion rooms are fully explored free space areas where all
   * routing possibilities have been evaluated. These rooms serve as:
   * <ul>
   *   <li>Cached routing data for performance</li>
   *   <li>Connection points between different routing regions</li>
   *   <li>Targets for expansion algorithms</li>
   * </ul>
   *
   * <p>Maintained across routing operations when {@link #maintain_database} is true.
   *
   * @see CompleteFreeSpaceExpansionRoom
   */
  private List<CompleteFreeSpaceExpansionRoom> complete_expansion_rooms;

  /**
   * Counter tracking the total number of expansion rooms created.
   *
   * <p>Each expansion room gets a unique instance number for:
   * <ul>
   *   <li>Debugging and tracing</li>
   *   <li>Performance analysis</li>
   *   <li>Memory usage monitoring</li>
   * </ul>
   *
   * <p>Incremented each time a new expansion room is instantiated.
   */
  private int expansion_room_instance_count;

  /**
   * Creates a new autoroute engine instance for the specified board.
   *
   * <p>Initializes the routing infrastructure including:
   * <ul>
   *   <li>Search tree optimized for the specified clearance class</li>
   *   <li>Drill page array for spatial partitioning</li>
   *   <li>Database maintenance mode setting</li>
   * </ul>
   *
   * <p><strong>Clearance Class:</strong>
   * The trace clearance class determines:
   * <ul>
   *   <li>Minimum spacing between routed traces</li>
   *   <li>Which obstacles are relevant for routing</li>
   *   <li>Search tree optimization strategy</li>
   * </ul>
   *
   * <p><strong>Database Maintenance:</strong>
   * When {@code p_maintain_database} is true:
   * <ul>
   *   <li><strong>Pros:</strong> Faster subsequent routing, incremental updates</li>
   *   <li><strong>Cons:</strong> Higher memory usage</li>
   * </ul>
   *
   * <p>When false:
   * <ul>
   *   <li><strong>Pros:</strong> Lower memory footprint</li>
   *   <li><strong>Cons:</strong> Database rebuilt as needed (slower)</li>
   * </ul>
   *
   * <p><strong>Drill Page Width:</strong>
   * Calculated as 5× the default via diameter (minimum 10,000 board units).
   * Larger values reduce page count but increase per-page memory.
   *
   * @param p_board the routing board to operate on
   * @param p_trace_clearance_class_no the clearance class index for trace routing
   * @param p_maintain_database true to maintain database between connections, false to rebuild
   *
   * @see DrillPageArray
   * @see ShapeSearchTree
   */
  public AutorouteEngine(RoutingBoard p_board, int p_trace_clearance_class_no, boolean p_maintain_database) {
    this.board = p_board;
    this.maintain_database = p_maintain_database;
    this.net_no = -1;
    this.autoroute_search_tree = p_board.search_tree_manager.get_autoroute_tree(p_trace_clearance_class_no);
    int max_drill_page_width = (int) (5 * p_board.rules.get_default_via_diameter());
    max_drill_page_width = Math.max(max_drill_page_width, 10000);
    this.drill_page_array = new DrillPageArray(this.board, max_drill_page_width);
    this.stoppable_thread = null;
  }

  /**
   * Initializes the engine for routing a new connection on the specified net.
   *
   * <p>This method prepares the autoroute engine for routing operations by:
   * <ul>
   *   <li>Setting the current net number</li>
   *   <li>Configuring interruption checking</li>
   *   <li>Setting time limits for routing</li>
   *   <li>Invalidating cached data for the new net (if database is maintained)</li>
   * </ul>
   *
   * <p><strong>Database Maintenance Mode:</strong>
   * When {@link #maintain_database} is true and the net changes:
   * <ol>
   *   <li><strong>Invalidate Net-Dependent Rooms:</strong> Removes expansion rooms
   *       that depend on specific net configurations, as they may not be valid
   *       for the new net</li>
   *   <li><strong>Invalidate Neighbor Rooms:</strong> Marks rooms adjacent to items
   *       on the target net as invalid, since routing this net may affect them</li>
   * </ol>
   *
   * <p><strong>Performance Consideration:</strong>
   * The invalidation process iterates through all items to find those on the
   * target net. For boards with many items, this can be time-consuming.
   *
   * <p>Must be called before routing each connection with {@link #autoroute_connection}.
   *
   * @param p_net_no the net number to route
   * @param p_stoppable_thread thread to check for stop requests, or null if not interruptible
   * @param p_time_limit time limit controller for routing timeout, or null for unlimited
   *
   * @see #autoroute_connection
   * @see TimeLimit
   * @see Stoppable
   */
  public void init_connection(int p_net_no, Stoppable p_stoppable_thread, TimeLimit p_time_limit) {
    if (this.maintain_database) {
      if (p_net_no != this.net_no) {
        if (this.complete_expansion_rooms != null) {
          // invalidate the net dependent complete free space expansion rooms.
          Collection<CompleteFreeSpaceExpansionRoom> rooms_to_remove = new ArrayList<>();
          for (CompleteFreeSpaceExpansionRoom curr_room : complete_expansion_rooms) {
            if (curr_room.is_net_dependent()) {
              rooms_to_remove.add(curr_room);
            }
          }
          for (CompleteFreeSpaceExpansionRoom curr_room : rooms_to_remove) {
            this.remove_complete_expansion_room(curr_room);
          }
        }
        // invalidate the neighbour rooms of the items of p_net_no
        Collection<Item> item_list = this.board.get_items();
        for (Item curr_item : item_list) {
          if (curr_item.contains_net(p_net_no)) {
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
   * Attempts to route a connection between two sets of items using the autorouting algorithm.
   *
   * <p>This is the core routing method that tries to establish a physical connection
   * (traces and vias) between items in the start set and items in the destination set.
   *
   * <p><strong>Algorithm Overview:</strong>
   * <ol>
   *   <li><strong>Validation:</strong> Check if items are already connected</li>
   *   <li><strong>Expansion:</strong> Create expansion rooms from start items</li>
   *   <li><strong>Search:</strong> Expand rooms using maze search until destination reached</li>
   *   <li><strong>Backtracing:</strong> Construct optimal path from found connection</li>
   *   <li><strong>Insertion:</strong> Insert traces and vias into the board</li>
   *   <li><strong>Cleanup:</strong> Remove ripped items, update database</li>
   * </ol>
   *
   * <p><strong>Return Values:</strong>
   * <ul>
   *   <li><strong>AlreadyConnected:</strong> Items are already electrically connected</li>
   *   <li><strong>Routed:</strong> Successfully routed a new connection</li>
   *   <li><strong>NotRouted:</strong> Could not find a valid route</li>
   *   <li><strong>InsertError:</strong> Route found but insertion failed</li>
   * </ul>
   *
   * <p><strong>Rip-up and Retry:</strong>
   * If {@code p_ripped_item_list} is provided, the algorithm may remove existing
   * traces that block the desired route. Ripped items are added to the list for
   * potential restoration if routing ultimately fails.
   *
   * <p><strong>Time Limits:</strong>
   * Respects the time limit set via {@link #init_connection}. If time expires:
   * <ul>
   *   <li>Expansion stops</li>
   *   <li>Best partial result is used if available</li>
   *   <li>Returns NOT_ROUTED if no path found</li>
   * </ul>
   *
   * <p><strong>Interruption:</strong>
   * Checks {@link Stoppable#isStopRequested()} periodically. If true:
   * <ul>
   *   <li>Routing terminates immediately</li>
   *   <li>Partial results are discarded</li>
   *   <li>Returns NOT_ROUTED</li>
   * </ul>
   *
   * <p><strong>Prerequisites:</strong>
   * Must call {@link #init_connection} before this method to set up net number
   * and time limits.
   *
   * @param p_start_set the set of items to route from (typically pads or existing traces)
   * @param p_dest_set the set of items to route to (typically pads or existing traces)
   * @param p_ctrl the autoroute control providing routing strategy and parameters
   * @param p_ripped_item_list optional list to collect items removed during routing, or null
   * @return the result of the routing attempt
   *
   * @see AutorouteAttemptResult
   * @see AutorouteControl
   * @see #init_connection
   */
  public AutorouteAttemptResult autoroute_connection(Set<Item> p_start_set, Set<Item> p_dest_set,
      AutorouteControl p_ctrl, SortedSet<Item> p_ripped_item_list) {
    String sourceItems = String.join(", ", p_start_set
        .stream()
        .map(Item::toString)
        .toList());
    String targetItems = String.join(", ", p_dest_set
        .stream()
        .map(Item::toString)
        .toList());

    MazeSearchAlgo maze_search_algo;
    try {
      maze_search_algo = MazeSearchAlgo.get_instance(p_start_set, p_dest_set, this, p_ctrl);
    } catch (Exception e) {
      FRLogger.error("AutorouteEngine.autoroute_connection: Exception in MazeSearchAlgo.get_instance", e);
      maze_search_algo = null;
    }

    if (maze_search_algo == null) {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED,
          "Failed to route connection between " + sourceItems + " and " + targetItems
              + ", because the maze search algorithm could not be created.");
    }

    MazeSearchAlgo.Result search_result = null;
    if (maze_search_algo != null) {
      try {
        search_result = maze_search_algo.find_connection();
      } catch (Exception e) {
        FRLogger.error("AutorouteEngine.autoroute_connection: Exception in maze_search_algo.find_connection", e);
      }
    }

    if (search_result == null) {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED,
          "Failed to route connection between " + sourceItems + " and " + targetItems
              + ", because no connection was found between their nets.");
    }

    LocateFoundConnectionAlgo autoroute_result = null;
    if (search_result != null) {
      try {
        autoroute_result = LocateFoundConnectionAlgo.get_instance(search_result, p_ctrl, this.autoroute_search_tree,
            board.rules.get_trace_angle_restriction(), p_ripped_item_list);
      } catch (Exception e) {
        FRLogger.error("AutorouteEngine.autoroute_connection: Exception in LocateFoundConnectionAlgo.get_instance", e);
      }
    }

    if (!this.maintain_database) {
      this.clear();
    } else {
      this.reset_all_doors();
    }

    if (autoroute_result == null) {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED,
          "Failed to route connection between " + sourceItems + " and " + targetItems + ".");
    }

    if (!p_ctrl.layer_active[autoroute_result.start_layer] || !p_ctrl.layer_active[autoroute_result.target_layer]) {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED, "Failed to route connection between "
          + sourceItems + " and " + targetItems + ", because some of their layers are disabled.");
    }

    if (autoroute_result.connection_items == null) {
      FRLogger.debug("AutorouteEngine.autoroute_connection: result_items != null expected");
      return new AutorouteAttemptResult(AutorouteAttemptState.SKIPPED,
          "No new connections were made between " + sourceItems + " and " + targetItems + ".");
    }

    // Delete the ripped connections.
    SortedSet<Item> ripped_connections = new TreeSet<>();
    Set<Integer> changed_nets = new TreeSet<>();
    Item.StopConnectionOption stop_connection_option;
    if (p_ctrl.remove_unconnected_vias) {
      stop_connection_option = Item.StopConnectionOption.NONE;
    } else {
      stop_connection_option = Item.StopConnectionOption.FANOUT_VIA;
    }

    for (Item curr_ripped_item : p_ripped_item_list) {
      ripped_connections.addAll(curr_ripped_item.get_connection_items(stop_connection_option));
      for (int i = 0; i < curr_ripped_item.net_count(); i++) {
        changed_nets.add(curr_ripped_item.get_net_no(i));
      }
    }

    // let the observers know the changes in the board database.
    boolean observers_activated = !this.board.observers_active();
    if (observers_activated) {
      this.board.start_notify_observers();
    }

    board.remove_items(ripped_connections);

    for (int curr_net_no : changed_nets) {
      this.board.remove_trace_tails(curr_net_no, stop_connection_option);
    }
    InsertFoundConnectionAlgo insert_found_connection_algo = InsertFoundConnectionAlgo.get_instance(autoroute_result,
        board, p_ctrl);

    if (observers_activated) {
      this.board.end_notify_observers();
    }
    if (insert_found_connection_algo == null) {
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED,
          "Failed to route connection between " + sourceItems + " and " + targetItems
              + ", because the new connection could not be inserted.");
    }

    return new AutorouteAttemptResult(AutorouteAttemptState.ROUTED);
  }

  /**
   * Returns the net number of the current connection to route.
   */
  public int get_net_no() {
    return this.net_no;
  }

  /**
   * Returns if the user has stopped the autorouter.
   */
  public boolean is_stop_requested() {
    if (this.time_limit != null) {
      if (this.time_limit.limit_exceeded()) {
        return true;
      }
    }
    if (this.stoppable_thread == null) {
      return false;
    }
    return this.stoppable_thread.isStopRequested();
  }

  /**
   * Clears all temporary data
   */
  public void clear() {
    if (complete_expansion_rooms != null) {
      for (CompleteFreeSpaceExpansionRoom curr_room : complete_expansion_rooms) {
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
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context, double p_intensity) {
    if (complete_expansion_rooms == null) {
      return;
    }
    for (CompleteFreeSpaceExpansionRoom curr_room : complete_expansion_rooms) {
      curr_room.draw(p_graphics, p_graphics_context, p_intensity);
    }
    Collection<Item> item_list = this.board.get_items();
    for (Item curr_item : item_list) {
      ItemAutorouteInfo autoroute_info = curr_item.get_autoroute_info();
      if (autoroute_info != null) {
        autoroute_info.draw(p_graphics, p_graphics_context, p_intensity);
      }
    }
    // this.drill_page_array.draw(p_graphics, p_graphics_context, p_intensity);
  }

  /**
   * Creates a new FreeSpaceExpansionRoom and adds it to the room list. Its shape
   * is normally unbounded at construction time of the room. The final (completed)
   * shape will be a subshape of the start
   * shape, which does not overlap with any obstacle, and it is as big as
   * possible. p_contained_points will remain contained in the shape, after it is
   * completed.
   */
  public IncompleteFreeSpaceExpansionRoom add_incomplete_expansion_room(TileShape p_shape, int p_layer,
      TileShape p_contained_shape) {
    IncompleteFreeSpaceExpansionRoom new_room = new IncompleteFreeSpaceExpansionRoom(p_shape, p_layer,
        p_contained_shape);
    if (this.incomplete_expansion_rooms == null) {
      this.incomplete_expansion_rooms = new ArrayList<>();
    }
    this.incomplete_expansion_rooms.add(new_room);
    return new_room;
  }

  /**
   * Returns the first incomplete expansion room from the database.
   *
   * <p>Incomplete expansion rooms represent areas of free space that haven't been
   * fully explored yet. This method provides access to the next room to expand
   * during the routing algorithm.
   *
   * @return the first incomplete expansion room, or null if none exist
   *
   * @see IncompleteFreeSpaceExpansionRoom
   */
  public IncompleteFreeSpaceExpansionRoom get_first_incomplete_expansion_room() {
    if (incomplete_expansion_rooms == null) {
      return null;
    }
    if (incomplete_expansion_rooms.isEmpty()) {
      return null;
    }
    Iterator<IncompleteFreeSpaceExpansionRoom> it = incomplete_expansion_rooms.iterator();
    return it.next();
  }

  /**
   * Removes an incomplete expansion room from the routing database.
   *
   * <p>This operation:
   * <ol>
   *   <li>Removes all doors connecting this room to neighbors</li>
   *   <li>Removes the room from the incomplete rooms list</li>
   * </ol>
   *
   * <p>Typically called when:
   * <ul>
   *   <li>The room has been fully explored (converted to complete)</li>
   *   <li>The room is no longer valid due to board changes</li>
   *   <li>Database cleanup is needed</li>
   * </ul>
   *
   * @param p_room the incomplete expansion room to remove
   *
   * @see #remove_all_doors(ExpansionRoom)
   */
  public void remove_incomplete_expansion_room(IncompleteFreeSpaceExpansionRoom p_room) {
    this.remove_all_doors(p_room);
    incomplete_expansion_rooms.remove(p_room);
  }

  /**
   * Removes a complete expansion room from the database and creates new incomplete rooms.
   *
   * <p>When a complete room is removed (typically because routing has changed the board):
   * <ol>
   *   <li><strong>Remove Doors:</strong> Disconnect from all neighbor rooms</li>
   *   <li><strong>Create Incomplete Rooms:</strong> For each neighbor, create a new
   *       incomplete room at the boundary where the removed room connected</li>
   *   <li><strong>Update Neighbors:</strong> Connect new incomplete rooms to the neighbors</li>
   * </ol>
   *
   * <p><strong>Rationale:</strong>
   * The space previously occupied by the complete room is now unexplored relative
   * to its neighbors. Creating incomplete rooms at the boundaries allows the
   * expansion algorithm to re-explore these areas.
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>After inserting new traces that invalidate cached rooms</li>
   *   <li>When ripping up existing routing</li>
   *   <li>During database maintenance after board modifications</li>
   * </ul>
   *
   * @param p_room the complete expansion room to remove
   *
   * @see #add_incomplete_expansion_room
   * @see ExpansionDoor
   */
  public void remove_complete_expansion_room(CompleteFreeSpaceExpansionRoom p_room) {
    // create new incomplete expansion rooms for all neighbours
    TileShape room_shape = p_room.get_shape();
    int room_layer = p_room.get_layer();
    Collection<ExpansionDoor> room_doors = p_room.get_doors();
    for (ExpansionDoor curr_door : room_doors) {
      ExpansionRoom curr_neighbour = curr_door.other_room(p_room);
      if (curr_neighbour == null) {
        continue;
      }
      curr_neighbour.remove_door(curr_door);
      TileShape neighbour_shape = curr_neighbour.get_shape();
      TileShape intersection = room_shape.intersection(neighbour_shape);
      if (intersection.dimension() == 1) {
        // add a new incomplete room to curr_neighbour.
        int[] touching_sides = room_shape.touching_sides(neighbour_shape);
        Line[] line_arr = new Line[1];
        line_arr[0] = neighbour_shape
            .border_line(touching_sides[1])
            .opposite();
        Simplex new_incomplete_room_shape = Simplex.get_instance(line_arr);
        IncompleteFreeSpaceExpansionRoom new_incomplete_room = add_incomplete_expansion_room(new_incomplete_room_shape,
            room_layer, intersection);
        ExpansionDoor new_door = new ExpansionDoor(curr_neighbour, new_incomplete_room, 1);
        curr_neighbour.add_door(new_door);
        new_incomplete_room.add_door(new_door);
      }
    }
    this.remove_all_doors(p_room);
    p_room.remove_from_tree(this.autoroute_search_tree);
    if (complete_expansion_rooms != null) {
      complete_expansion_rooms.remove(p_room);
    } else {
      FRLogger.warn("AutorouteEngine.remove_complete_expansion_room: this.complete_expansion_rooms is null");
    }
    this.drill_page_array.invalidate(room_shape);
  }

  /**
   * Completes the shape of p_room. Returns the resulting rooms after completing
   * the shape. p_room will no more exist after this function.
   */
  public Collection<CompleteFreeSpaceExpansionRoom> complete_expansion_room(IncompleteFreeSpaceExpansionRoom p_room) {

    try {
      Collection<CompleteFreeSpaceExpansionRoom> result = new ArrayList<>();
      TileShape from_door_shape = null;
      SearchTreeObject ignore_object = null;
      Collection<ExpansionDoor> room_doors = p_room.get_doors();
      for (ExpansionDoor curr_door : room_doors) {
        ExpansionRoom other_room = curr_door.other_room(p_room);
        if (other_room instanceof CompleteFreeSpaceExpansionRoom room && curr_door.dimension == 2) {
          from_door_shape = curr_door.get_shape();
          ignore_object = room;
          break;
        }
      }
      Collection<IncompleteFreeSpaceExpansionRoom> completed_shapes = this.autoroute_search_tree.complete_shape(p_room,
          this.net_no, ignore_object, from_door_shape);

      // DEBUG: Log when room completion fails
      if (completed_shapes.isEmpty()) {
        if ((globalSettings != null) && (globalSettings.debugSettings != null)
            && (globalSettings.debugSettings.enableDetailedLogging)) {
          String netInfo;
          if (this.board != null && this.board.rules != null && this.board.rules.nets != null
              && this.net_no >= 0 && this.net_no <= this.board.rules.nets.max_net_no()) {
            netInfo = this.board.rules.nets.get(this.net_no).toString();
          } else {
            netInfo = "Net #" + this.net_no + " (Unknown)";
          }
          List<Point> points = new ArrayList<>();
          TileShape roomShape = p_room.get_shape();
          TileShape containedShape = p_room.get_contained_shape();
          if (roomShape != null && roomShape.centre_of_gravity() != null) {
            points.add(roomShape.centre_of_gravity().round());
          }
          if (containedShape != null && containedShape.centre_of_gravity() != null) {
            points.add(containedShape.centre_of_gravity().round());
          }
          if (from_door_shape != null && from_door_shape.centre_of_gravity() != null) {
            points.add(from_door_shape.centre_of_gravity().round());
          }

          FRLogger.trace("AutorouteEngine.complete_expansion_room", "no_shapes_returned",
              "No shapes returned on layer " + p_room.get_layer()
                  + ", initial shape: "
                  + (roomShape != null ? roomShape.getClass().getSimpleName() : "unbounded")
                  + ", contained shape: " + (containedShape != null ? "present" : "null")
                  + ", doors=" + (room_doors != null ? room_doors.size() : 0)
                  + ", ignore_object=" + (ignore_object != null ? ignore_object.toString() : "null")
                  + ", from_door_shape=" + (from_door_shape != null ? from_door_shape.toString() : "null"),
              netInfo,
              points.toArray(new Point[0]));
        }
      }

      this.remove_incomplete_expansion_room(p_room);
      boolean is_first_completed_room = true;
      int rooms_before_2d_filter = completed_shapes.size();
      for (IncompleteFreeSpaceExpansionRoom curr_incomplete_room : completed_shapes) {
        if (curr_incomplete_room
            .get_shape()
            .dimension() != 2) {
          continue;
        }
        if (is_first_completed_room) {
          is_first_completed_room = false;
          CompleteFreeSpaceExpansionRoom completed_room = this.add_complete_room(curr_incomplete_room);
          if (completed_room != null) {
            result.add(completed_room);
          }
        } else {
          // the shape of the first completed room may have changed and may
          // intersect now with the other shapes. Therefore, the completed shapes
          // have to be recalculated.
          Collection<IncompleteFreeSpaceExpansionRoom> curr_completed_shapes = this.autoroute_search_tree
              .complete_shape(curr_incomplete_room, this.net_no, ignore_object, from_door_shape);
          for (IncompleteFreeSpaceExpansionRoom tmp_room : curr_completed_shapes) {
            CompleteFreeSpaceExpansionRoom completed_room = this.add_complete_room(tmp_room);
            if (completed_room != null) {
              result.add(completed_room);
            }
          }
        }
      }

      // DEBUG: Log if 2D filtering removed all rooms
      if (result.isEmpty() && rooms_before_2d_filter > 0) {
        if ((globalSettings != null) && (globalSettings.debugSettings != null)
            && (globalSettings.debugSettings.enableDetailedLogging)) {
          String netInfo;
          if (this.board != null && this.board.rules != null && this.board.rules.nets != null
              && this.net_no >= 0 && this.net_no <= this.board.rules.nets.max_net_no()) {
            netInfo = this.board.rules.nets.get(this.net_no).toString();
          } else {
            netInfo = "Net #" + this.net_no + " (Unknown)";
          }
          List<Point> points = new ArrayList<>();
          TileShape roomShape = p_room.get_shape();
          if (roomShape != null && roomShape.centre_of_gravity() != null) {
            points.add(roomShape.centre_of_gravity().round());
          }
          if (from_door_shape != null && from_door_shape.centre_of_gravity() != null) {
            points.add(from_door_shape.centre_of_gravity().round());
          }

          FRLogger.trace("AutorouteEngine.complete_expansion_room", "all_shapes_filtered",
              "All " + rooms_before_2d_filter + " completed shapes were < 2D on layer " + p_room.get_layer()
                  + ", ignore_object=" + (ignore_object != null ? ignore_object.toString() : "null")
                  + ", from_door_shape=" + (from_door_shape != null ? from_door_shape.toString() : "null"),
              netInfo,
              points.toArray(new Point[0]));
        }
      }

      return result;
    } catch (Exception e) {
      FRLogger.error("AutorouteEngine.complete_expansion_room: ", e);
      return new ArrayList<>();
    }
  }

  /**
   * Calculates doors for an incomplete room and adds it to the complete room database.
   *
   * <p>This method performs the transition from an incomplete expansion room to a
   * complete one by:
   * <ol>
   *   <li><strong>Door Calculation:</strong> Identifies all connection points (doors)
   *       to neighboring expansion rooms</li>
   *   <li><strong>Validation:</strong> Ensures the room is 2-dimensional (has area)</li>
   *   <li><strong>Database Addition:</strong> Adds to the complete rooms collection</li>
   * </ol>
   *
   * <p><strong>Door Calculation:</strong>
   * Doors represent boundaries where this room connects to adjacent rooms. They're
   * crucial for the expansion algorithm to navigate between free space regions.
   *
   * <p><strong>Dimension Validation:</strong>
   * Only 2D rooms (areas, not lines or points) are valid for routing. Degenerate
   * rooms are rejected by returning null.
   *
   * <p><strong>Lazy Initialization:</strong>
   * Creates the {@link #complete_expansion_rooms} list on first use to save memory
   * when database maintenance is disabled.
   *
   * @param p_room the incomplete room to convert to complete
   * @return the completed room if successful, or null if invalid (not 2D or door calculation failed)
   *
   * @see CompleteFreeSpaceExpansionRoom
   */
  private CompleteFreeSpaceExpansionRoom add_complete_room(IncompleteFreeSpaceExpansionRoom p_room) {
    CompleteFreeSpaceExpansionRoom completed_room = (CompleteFreeSpaceExpansionRoom) calculate_doors(p_room);
    if (completed_room == null || completed_room
        .get_shape()
        .dimension() != 2) {
      return null;
    }
    if (complete_expansion_rooms == null) {
      complete_expansion_rooms = new ArrayList<>();
    }
    complete_expansion_rooms.add(completed_room);
    this.autoroute_search_tree.insert(completed_room);
    return completed_room;
  }

  /**
   * Calculates the neighbours of p_room and inserts doors to the new created
   * neighbour rooms. The shape of the result room may be different to the shape
   * of p_room
   */
  private CompleteExpansionRoom calculate_doors(ExpansionRoom p_room) {
    CompleteExpansionRoom result;
    if (this.autoroute_search_tree instanceof ShapeSearchTree90Degree) {
      result = SortedOrthogonalRoomNeighbours.calculate(p_room, this);
    } else if (this.autoroute_search_tree instanceof ShapeSearchTree45Degree) {
      result = Sorted45DegreeRoomNeighbours.calculate(p_room, this);
    } else {
      result = SortedRoomNeighbours.calculate(p_room, this);
    }
    return result;
  }

  /**
   * Completes the shapes of the neighbour rooms of p_room, so that the doors of
   * p_room will not change later on.
   */
  public void complete_neighbour_rooms(CompleteExpansionRoom p_room) {
    if (p_room.get_doors() == null) {
      return;
    }
    // Snapshot the doors to avoid ConcurrentModificationException and restarting
    // the iterator
    // This changes the complexity from O(N^2) (due to restarts) to O(N)
    List<ExpansionDoor> doors_snapshot = new ArrayList<>(p_room.get_doors());

    for (ExpansionDoor curr_door : doors_snapshot) {
      // cast to ExpansionRoom because ExpansionDoor.other_room works differently with
      // parameter type CompleteExpansionRoom.
      ExpansionRoom neighbour_room = curr_door.other_room((ExpansionRoom) p_room);
      if (neighbour_room == null) {
        continue;
      }
      if (neighbour_room instanceof IncompleteFreeSpaceExpansionRoom room) {
        this.complete_expansion_room(room);
      } else if (neighbour_room instanceof ObstacleExpansionRoom obstacle_neighbour_room) {
        if (!obstacle_neighbour_room.all_doors_calculated()) {
          this.calculate_doors(obstacle_neighbour_room);
          obstacle_neighbour_room.set_doors_calculated(true);
        }
      }
    }
  }

  /**
   * Invalidates all drill pages intersecting with p_shape, so they must be
   * recalculated at the next call of get_ddrills()
   */
  public void invalidate_drill_pages(TileShape p_shape) {
    this.drill_page_array.invalidate(p_shape);
  }

  /**
   * Removes all doors from p_room
   */
  void remove_all_doors(ExpansionRoom p_room) {
    for (ExpansionDoor curr_door : p_room.get_doors()) {
      ExpansionRoom other_room = curr_door.other_room(p_room);
      if (other_room == null) {
        continue;
      }
      other_room.remove_door(curr_door);
      if (other_room instanceof IncompleteFreeSpaceExpansionRoom room) {
        this.remove_incomplete_expansion_room(room);
      }
    }
    p_room.clear_doors();
  }

  /**
   * Finds all complete expansion rooms that have target doors connecting to specified items.
   *
   * <p>Target doors represent connections from expansion rooms directly to board items
   * (such as pads, vias, or existing traces). This method identifies which rooms can
   * reach the target items, which is essential for:
   * <ul>
   *   <li>Determining routing completion (path found to destination)</li>
   *   <li>Backtracing from destination to source</li>
   *   <li>Validating connectivity</li>
   * </ul>
   *
   * <p><strong>Algorithm:</strong>
   * Iterates through all complete expansion rooms and checks if any of their target
   * doors connect to items in the provided set.
   *
   * @param p_items the set of target items to find rooms for
   * @return a set of complete rooms with target doors to the specified items (may be empty)
   *
   * @see TargetItemExpansionDoor
   * @see CompleteFreeSpaceExpansionRoom#get_target_doors()
   */
  Set<CompleteFreeSpaceExpansionRoom> get_rooms_with_target_items(Set<Item> p_items) {
    Set<CompleteFreeSpaceExpansionRoom> result = new TreeSet<>();
    if (this.complete_expansion_rooms != null) {
      for (CompleteFreeSpaceExpansionRoom curr_room : this.complete_expansion_rooms) {
        Collection<TargetItemExpansionDoor> target_door_list = curr_room.get_target_doors();
        for (TargetItemExpansionDoor curr_target_door : target_door_list) {
          Item curr_target_item = curr_target_door.item;
          if (p_items.contains(curr_target_item)) {
            result.add(curr_room);
          }
        }
      }
    }
    return result;
  }

  /**
   * Validates the internal data structures for consistency and correctness.
   *
   * <p>Performs integrity checks on the autorouter's internal state, including:
   * <ul>
   *   <li>Complete expansion room validity</li>
   *   <li>Door connections between rooms</li>
   *   <li>Shape consistency</li>
   *   <li>Database coherence</li>
   * </ul>
   *
   * <p><strong>When to Use:</strong>
   * <ul>
   *   <li><strong>Debugging:</strong> Diagnose routing algorithm issues</li>
   *   <li><strong>Testing:</strong> Verify database integrity after operations</li>
   *   <li><strong>Development:</strong> Ensure changes don't corrupt data structures</li>
   * </ul>
   *
   * <p><strong>Performance:</strong>
   * This is an expensive operation that iterates through all complete rooms and
   * validates each one. Should not be called in production code or performance-critical paths.
   *
   * <p>Returns true even if {@link #complete_expansion_rooms} is null (no database to validate).
   *
   * @return true if all data structures are valid, false if any inconsistencies detected
   *
   * @see CompleteFreeSpaceExpansionRoom#validate(AutorouteEngine)
   */
  public boolean validate() {
    if (complete_expansion_rooms == null) {
      return true;
    }
    boolean result = true;
    for (CompleteFreeSpaceExpansionRoom curr_room : complete_expansion_rooms) {
      if (!curr_room.validate(this)) {
        result = false;
      }
    }
    return result;
  }

  /**
   * Reset all doors for autorouting the next connection, in case the autorouting
   * database is retained.
   */
  private void reset_all_doors() {
    if (this.complete_expansion_rooms != null) {
      for (ExpansionRoom curr_room : this.complete_expansion_rooms) {
        curr_room.reset_doors();
      }
    }
    Collection<Item> item_list = this.board.get_items();
    for (Item curr_item : item_list) {
      ItemAutorouteInfo curr_autoroute_info = curr_item.get_autoroute_info_pur();
      if (curr_autoroute_info != null) {
        curr_autoroute_info.reset_doors();
        curr_autoroute_info.set_precalculated_connection(null);
      }
    }
    this.drill_page_array.reset();
  }

  protected int generate_room_id_no() {
    return ++expansion_room_instance_count;
  }
}
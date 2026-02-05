package app.freerouting.board;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.autoroute.AutorouteControl.ExpansionCostFactor;
import app.freerouting.autoroute.AutorouteEngine;
import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.Net;
import app.freerouting.rules.ViaInfo;
import app.freerouting.settings.RouterSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Represents the main routing board for PCB design and manufacturing.
 *
 * <p>
 * This class extends {@link BasicBoard} and provides comprehensive
 * functionality for:
 * <ul>
 * <li>Automatic and manual routing of traces between components</li>
 * <li>Managing routing database and optimization algorithms</li>
 * <li>Handling trace insertion, modification, and removal with collision
 * detection</li>
 * <li>Push-and-shove routing capabilities to automatically resolve
 * conflicts</li>
 * <li>Via placement and fanout routing from pins</li>
 * <li>Route optimization and pull-tight operations</li>
 * <li>Tracking routing failures and maintaining board statistics</li>
 * </ul>
 *
 * <p>
 * The board maintains its own autoroute engine and changed area tracking for
 * efficient
 * incremental updates. It supports both fixed and user-modified routing with
 * sophisticated
 * clearance checking and net management.
 *
 * @see BasicBoard
 * @see AutorouteEngine
 * @see Item
 */
public class RoutingBoard extends BasicBoard implements Serializable {

  /**
   * The maximum time limit in milliseconds for the pull tight algorithm.
   *
   * <p>
   * Pull tight operations optimize trace routes by removing unnecessary segments
   * and tightening connections. This timeout prevents the algorithm from running
   * indefinitely on complex routing scenarios.
   */
  private static final int PULL_TIGHT_TIME_LIMIT = 2000;
  /**
   * Tracks routing failures for items on this board.
   *
   * <p>
   * This log is kept persistent to maintain a history of failures across multiple
   * routing passes and threads. It helps identify problematic connections and
   * areas
   * that consistently fail during auto-routing attempts. The failure information
   * can
   * be used to adjust routing strategies or identify design issues.
   */
  public final app.freerouting.autoroute.RoutingFailureLog failureLog;
  /**
   * The area marked for optimizing the route.
   *
   * <p>
   * This transient field tracks regions of the board that have been modified and
   * need route optimization. When traces are added, moved, or removed, the
   * affected
   * areas are recorded here. The pull-tight algorithm uses this information to
   * efficiently optimize only the changed portions of the board rather than
   * reprocessing
   * the entire board.
   *
   * @see #start_marking_changed_area()
   * @see #join_changed_area(FloatPoint, int)
   */
  transient ChangedArea changed_area;
  /**
   * Contains the database for the auto-route algorithm.
   *
   * <p>
   * The autoroute engine maintains a specialized search tree and expansion rooms
   * for efficient pathfinding during automatic routing. It can be retained
   * between
   * routing operations for performance reasons, or cleared to save memory. When
   * retained,
   * the engine database is incrementally updated as board items change.
   *
   * @see AutorouteEngine
   * @see #init_autoroute(int, int, Stoppable, TimeLimit, boolean)
   * @see #finish_autoroute()
   */
  private transient AutorouteEngine autoroute_engine;

  /**
   * The item that caused the most recent push-and-shove operation to fail.
   *
   * <p>
   * This transient field stores a reference to the obstacle that could not be
   * pushed
   * aside during the last shove attempt. It provides diagnostic information for
   * routing
   * failures and user feedback.
   *
   * @see #get_shove_failing_obstacle()
   * @see #set_shove_failing_obstacle(Item)
   */
  private transient Item shove_failing_obstacle;

  /**
   * The layer index where the most recent push-and-shove operation failed.
   *
   * <p>
   * This transient field stores the layer where the obstacle was encountered. A
   * value
   * of -1 indicates no recent failure or that the failure information has been
   * cleared.
   *
   * @see #get_shove_failing_layer()
   * @see #set_shove_failing_layer(int)
   */
  private transient int shove_failing_layer = -1;

  /**
   * Creates a new instance of a routing board with the specified parameters.
   *
   * <p>
   * This constructor initializes the board with geometric constraints, layer
   * information,
   * and routing rules. The board outline shapes define the physical boundaries
   * where routing
   * can occur.
   *
   * @param p_bounding_box        the rectangular boundary that contains all board
   *                              elements
   * @param p_layer_structure     the layer stack-up defining signal, power, and
   *                              ground layers
   * @param p_outline_shapes      the physical board outlines as polyline shapes
   * @param p_outline_cl_class_no the clearance class number for the board outline
   * @param p_rules               the board rules containing clearance matrices,
   *                              net definitions, and design rules
   * @param p_board_communication the communication interface for user interaction
   *                              and logging
   */
  public RoutingBoard(IntBox p_bounding_box, LayerStructure p_layer_structure, PolylineShape[] p_outline_shapes,
      int p_outline_cl_class_no, BoardRules p_rules, Communication p_board_communication) {
    super(p_bounding_box, p_layer_structure, p_outline_shapes, p_outline_cl_class_no, p_rules, p_board_communication);
    this.failureLog = new app.freerouting.autoroute.RoutingFailureLog();
  }

  /**
   * Maintains the auto-router database after an item is inserted, changed, or
   * deleted.
   *
   * <p>
   * This method is called automatically after any item modification to keep the
   * autoroute
   * search tree synchronized with the current board state. It invalidates
   * expansion rooms
   * that overlap with the modified item's shapes, ensuring that subsequent
   * routing operations
   * use accurate free space information.
   *
   * <p>
   * The method performs the following updates:
   * <ul>
   * <li>Invalidates drill pages affected by the item's shapes</li>
   * <li>Removes expansion rooms that overlap with the item</li>
   * <li>Clears autoroute information cached in the item</li>
   * </ul>
   *
   * @param p_item the item that was inserted, modified, or deleted; if null, no
   *               action is taken
   */
  @Override
  public void additional_update_after_change(Item p_item) {
    if (p_item == null) {
      return;
    }
    if (this.autoroute_engine == null || !this.autoroute_engine.maintain_database) {
      return;
    }
    // Invalidate the free space expansion rooms touching a shape of p_item.
    int shape_count = p_item.tree_shape_count(this.autoroute_engine.autoroute_search_tree);
    for (int i = 0; i < shape_count; i++) {
      TileShape curr_shape = p_item.get_tree_shape(this.autoroute_engine.autoroute_search_tree, i);
      this.autoroute_engine.invalidate_drill_pages(curr_shape);
      int curr_layer = p_item.shape_layer(i);
      Collection<SearchTreeObject> overlaps = this.autoroute_engine.autoroute_search_tree
          .overlapping_objects(curr_shape, curr_layer);
      for (SearchTreeObject curr_object : overlaps) {
        if (curr_object instanceof CompleteFreeSpaceExpansionRoom room) {
          this.autoroute_engine.remove_complete_expansion_room(room);
        }
      }
    }
    p_item.clear_autoroute_info();
  }

  /**
   * Removes the specified items from the board and optimizes nearby rubber
   * traces.
   *
   * <p>
   * This method performs a coordinated removal and optimization operation:
   * <ol>
   * <li>Attempts to remove each item in the collection</li>
   * <li>Marks the affected areas for optimization</li>
   * <li>Combines traces that may have been split by the removed items</li>
   * <li>Pulls nearby traces tight to optimize routing in the affected region</li>
   * </ol>
   *
   * <p>
   * Items that are fixed or have deletion forbidden will not be removed, and the
   * method will return false to indicate partial failure.
   *
   * @param p_item_list           the collection of items to remove from the board
   * @param p_tidy_width          the radius around removed items where
   *                              optimization occurs; use
   *                              {@link Integer#MAX_VALUE} to disable region
   *                              limiting
   * @param p_pull_tight_accuracy the accuracy level for the pull-tight
   *                              optimization algorithm
   * @return true if all items were successfully removed, false if any items could
   *         not be
   *         removed because they were fixed or deletion was forbidden
   */
  public boolean remove_items_and_pull_tight(Collection<Item> p_item_list, int p_tidy_width,
      int p_pull_tight_accuracy) {
    boolean result = true;
    IntOctagon tidy_region;
    boolean calculate_tidy_region;
    if (p_tidy_width < Integer.MAX_VALUE) {
      tidy_region = IntOctagon.EMPTY;
      calculate_tidy_region = p_tidy_width > 0;
    } else {
      tidy_region = null;
      calculate_tidy_region = false;
    }
    start_marking_changed_area();
    Set<Integer> changed_nets = new TreeSet<>();
    for (Item curr_item : p_item_list) {
      if (curr_item.isDeletionForbidden() || curr_item.is_user_fixed()) {
        // We are not allowed to delete this item.
        result = false;
      } else {
        for (int i = 0; i < curr_item.tile_shape_count(); i++) {
          TileShape curr_shape = curr_item.get_tile_shape(i);
          changed_area.join(curr_shape, curr_item.shape_layer(i));
          if (calculate_tidy_region) {
            tidy_region = tidy_region.union(curr_shape.bounding_octagon());
          }
        }
        remove_item(curr_item);
        for (int i = 0; i < curr_item.net_count(); i++) {
          changed_nets.add(curr_item.get_net_no(i));
        }
      }
    }
    for (Integer curr_net_no : changed_nets) {
      this.combine_traces(curr_net_no);
    }
    if (calculate_tidy_region) {
      tidy_region = tidy_region.enlarge(p_tidy_width);
    }
    opt_changed_area(new int[0], tidy_region, p_pull_tight_accuracy, null, null, PULL_TIGHT_TIME_LIMIT);
    return result;
  }

  /**
   * Starts marking changed areas for optimizing traces.
   *
   * <p>
   * Initializes the changed area tracking system if it hasn't been created yet.
   * After calling this method, subsequent modifications to the board will
   * automatically
   * record affected areas. The accumulated changed area is later used by
   * optimization
   * algorithms to focus processing on modified regions only.
   *
   * @see #join_changed_area(FloatPoint, int)
   * @see #mark_all_changed_area()
   * @see #opt_changed_area(int[], IntOctagon, int, ExpansionCostFactor[],
   *      Stoppable, int)
   */
  public void start_marking_changed_area() {
    if (changed_area == null) {
      changed_area = new ChangedArea(get_layer_count());
    }
  }

  /**
   * Enlarges the changed area on the specified layer to include the given point.
   *
   * <p>
   * This method expands the tracked changed region to encompass the specified
   * point.
   * It should be called whenever a point on the board is modified (e.g., when
   * traces are
   * added, moved, or removed). The accumulated changed area determines which
   * portions of
   * the board need optimization during pull-tight operations.
   *
   * @param p_point the point to include in the changed area
   * @param p_layer the layer index where the change occurred
   */
  public void join_changed_area(FloatPoint p_point, int p_layer) {
    if (changed_area != null) {
      changed_area.join(p_point, p_layer);
    }
  }

  /**
   * Marks the entire board as changed on all layers.
   *
   * <p>
   * This method initializes the changed area tracker and marks all four corners
   * of
   * the board's bounding box on every layer. Use this when you want subsequent
   * optimization
   * operations to process the entire board rather than just specific modified
   * regions.
   * This is useful after major board changes or when performing global
   * optimization.
   *
   * @see #start_marking_changed_area()
   * @see #join_changed_area(FloatPoint, int)
   */
  public void mark_all_changed_area() {
    start_marking_changed_area();
    FloatPoint[] board_corners = new FloatPoint[4];
    board_corners[0] = bounding_box.ll.to_float();
    board_corners[1] = new FloatPoint(bounding_box.ur.x, bounding_box.ll.y);
    board_corners[2] = bounding_box.ur.to_float();
    board_corners[3] = new FloatPoint(bounding_box.ll.x, bounding_box.ur.y);
    for (int i = 0; i < get_layer_count(); i++) {
      for (int j = 0; j < 4; j++) {
        join_changed_area(board_corners[j], i);
      }
    }
  }

  /**
   * Optimizes routes in the internally marked changed area.
   *
   * <p>
   * This is a convenience method that calls the full version of opt_changed_area
   * without keep-point parameters. It performs pull-tight optimization on traces
   * within
   * the previously marked changed area.
   *
   * @param p_only_net_no_arr  array of net numbers to optimize; if empty, all
   *                           nets are optimized
   * @param p_clip_shape       optional shape to restrict optimization region;
   *                           pass {@link IntOctagon#EMPTY}
   *                           to use only the marked changed area, or null for no
   *                           clipping
   * @param p_accuracy         the accuracy level for pull-tight optimization
   *                           (higher values = more precise)
   * @param p_trace_cost_arr   cost factors for via optimization; may be null
   * @param p_stoppable_thread thread that can request algorithm termination; may
   *                           be null
   * @param p_time_limit       maximum time in milliseconds for optimization; 0 or
   *                           negative means no limit
   *
   * @see #opt_changed_area(int[], IntOctagon, int, ExpansionCostFactor[],
   *      Stoppable, int, Point, int)
   */
  public void opt_changed_area(int[] p_only_net_no_arr, IntOctagon p_clip_shape, int p_accuracy,
      ExpansionCostFactor[] p_trace_cost_arr, Stoppable p_stoppable_thread, int p_time_limit) {
    opt_changed_area(p_only_net_no_arr, p_clip_shape, p_accuracy, p_trace_cost_arr, p_stoppable_thread, p_time_limit,
        null, 0);
  }

  /**
   * Optimizes routes in the internally marked changed area with optional point
   * preservation.
   *
   * <p>
   * This method runs the pull-tight algorithm on the previously marked changed
   * area,
   * optimizing trace routes to remove unnecessary segments and improve routing
   * efficiency.
   * The optimization can be constrained by net, clipping shape, and time limits.
   *
   * <p>
   * If a keep-point is specified, traces on the keep-point layer that originally
   * contained
   * that point will still contain it after optimization, preventing unwanted
   * topology changes.
   *
   * @param p_only_net_no_arr  array of net numbers to optimize; if empty, all
   *                           nets are optimized
   * @param p_clip_shape       optional shape to restrict optimization region;
   *                           pass {@link IntOctagon#EMPTY}
   *                           to use only the marked changed area, or null for no
   *                           clipping
   * @param p_accuracy         the accuracy level for pull-tight optimization
   *                           (higher values = more precise)
   * @param p_trace_cost_arr   cost factors for via optimization; may be null to
   *                           use defaults
   * @param p_stoppable_thread thread that can request algorithm termination; may
   *                           be null
   * @param p_time_limit       maximum time in milliseconds for optimization; 0 or
   *                           negative means no limit
   * @param p_keep_point       specific point to preserve during optimization; may
   *                           be null
   * @param p_keep_point_layer layer of the keep-point; only used if p_keep_point
   *                           is not null
   */
  public void opt_changed_area(int[] p_only_net_no_arr, IntOctagon p_clip_shape, int p_accuracy,
      ExpansionCostFactor[] p_trace_cost_arr, Stoppable p_stoppable_thread, int p_time_limit,
      Point p_keep_point, int p_keep_point_layer) {
    if (changed_area == null) {
      return;
    }
    if (p_clip_shape != IntOctagon.EMPTY) {
      PullTightAlgo pull_tight_algo = PullTightAlgo.get_instance(this, p_only_net_no_arr, p_clip_shape, p_accuracy,
          p_stoppable_thread, p_time_limit, p_keep_point, p_keep_point_layer);
      pull_tight_algo.opt_changed_area(p_trace_cost_arr);
    }
    join_graphics_update_box(changed_area.surrounding_box());
    changed_area = null;
  }

  /**
   * Checks if a rectangular trace segment can be inserted without conflicts.
   *
   * <p>
   * This method determines the maximum distance from the start point toward the
   * end point
   * where a trace with the specified width can be placed without violating
   * clearance rules.
   * It converts the two points into a line segment and delegates to the main
   * checking method.
   *
   * @param p_from_point                  the starting point of the trace segment
   * @param p_to_point                    the ending point of the trace segment
   * @param p_layer                       the layer index where the trace would be
   *                                      placed
   * @param p_net_no_arr                  array of net numbers for the trace
   * @param p_trace_half_width            half-width of the trace (radius of the
   *                                      trace)
   * @param p_cl_class_no                 clearance class number for checking
   *                                      violations
   * @param p_only_not_shovable_obstacles if true, only check against fixed
   *                                      obstacles;
   *                                      if false, check against all items
   *                                      including unfixed traces
   * @return the maximum distance that can be routed from start toward end without
   *         conflict;
   *         returns {@link Integer#MAX_VALUE} if no conflict exists, or 0 if
   *         routing cannot start
   *
   * @see #check_trace_segment(LineSegment, int, int[], int, int, boolean)
   */
  public double check_trace_segment(Point p_from_point, Point p_to_point, int p_layer, int[] p_net_no_arr,
      int p_trace_half_width, int p_cl_class_no, boolean p_only_not_shovable_obstacles) {
    if (p_from_point.equals(p_to_point)) {
      return 0;
    }
    Polyline curr_polyline = new Polyline(p_from_point, p_to_point);
    LineSegment curr_line_segment = new LineSegment(curr_polyline, 1);
    return check_trace_segment(curr_line_segment, p_layer, p_net_no_arr, p_trace_half_width, p_cl_class_no,
        p_only_not_shovable_obstacles);
  }

  /**
   * Checks if a trace shape around the given line segment can be inserted without
   * conflicts.
   *
   * <p>
   * This method creates a rectangular shape around the line segment using the
   * specified
   * half-width and checks for clearance violations with existing board items. It
   * returns the
   * maximum distance from the line's start point that can be routed without
   * conflicts.
   *
   * <p>
   * The algorithm:
   * <ol>
   * <li>Creates an offset shape around the line segment</li>
   * <li>Queries for overlapping items with required clearance</li>
   * <li>For each obstacle, calculates the projection distance where conflict
   * occurs</li>
   * <li>Returns the minimum conflict-free distance</li>
   * </ol>
   *
   * @param p_line_segment                the line segment defining the trace path
   * @param p_layer                       the layer index where the trace would be
   *                                      placed
   * @param p_net_no_arr                  array of net numbers for the trace
   * @param p_trace_half_width            half-width of the trace (radius of the
   *                                      trace)
   * @param p_cl_class_no                 clearance class number for checking
   *                                      violations
   * @param p_only_not_shovable_obstacles if true, only check against fixed
   *                                      obstacles (shove-fixed items);
   *                                      if false, check against all items
   *                                      including routable/unfixed traces
   * @return the maximum distance from line start that can be routed without
   *         conflict;
   *         returns {@link Integer#MAX_VALUE} if the entire segment is clear, or
   *         0 if routing cannot start
   */
  public double check_trace_segment(LineSegment p_line_segment, int p_layer, int[] p_net_no_arr, int p_trace_half_width,
      int p_cl_class_no, boolean p_only_not_shovable_obstacles) {
    Polyline check_polyline = p_line_segment.to_polyline();
    if (check_polyline.arr.length != 3) {
      return 0;
    }
    TileShape shape_to_check = check_polyline.offset_shape(p_trace_half_width, 0);
    FloatPoint from_point = p_line_segment.start_point_approx();
    FloatPoint to_point = p_line_segment.end_point_approx();
    double line_length = to_point.distance(from_point);
    double ok_length = Integer.MAX_VALUE;
    ShapeSearchTree default_tree = this.search_tree_manager.get_default_tree();

    Collection<TreeEntry> obstacle_entries = default_tree.overlapping_tree_entries_with_clearance(shape_to_check,
        p_layer, p_net_no_arr, p_cl_class_no);

    for (TreeEntry curr_obstacle_entry : obstacle_entries) {

      if (!(curr_obstacle_entry.object instanceof Item curr_obstacle)) {
        continue;
      }
      if (p_only_not_shovable_obstacles && curr_obstacle.is_routable() && !curr_obstacle.is_shove_fixed()) {
        continue;
      }
      TileShape curr_obstacle_shape = curr_obstacle_entry.object.get_tree_shape(default_tree,
          curr_obstacle_entry.shape_index_in_object);
      TileShape curr_offset_shape;
      FloatPoint nearest_obstacle_point;
      double shorten_value;
      if (default_tree.is_clearance_compensation_used()) {
        curr_offset_shape = shape_to_check;
        shorten_value = p_trace_half_width
            + rules.clearance_matrix.clearance_compensation_value(curr_obstacle.clearance_class_no(), p_layer);
      } else {
        int clearance_value = this.clearance_value(curr_obstacle.clearance_class_no(), p_cl_class_no, p_layer);
        curr_offset_shape = (TileShape) shape_to_check.offset(clearance_value);
        shorten_value = p_trace_half_width + clearance_value;
      }
      TileShape intersection = curr_obstacle_shape.intersection(curr_offset_shape);
      if (intersection.is_empty()) {
        continue;
      }
      nearest_obstacle_point = intersection.nearest_point_approx(from_point);

      double projection = from_point.scalar_product(to_point, nearest_obstacle_point) / line_length;

      projection = Math.max(0.0, projection - shorten_value - 1);

      if (projection < ok_length) {
        ok_length = projection;
        if (ok_length <= 0) {
          return 0;
        }
      }
    }

    return ok_length;
  }

  /**
   * Checks if an item can be translated by a vector without producing overlaps or
   * clearance violations.
   *
   * <p>
   * This method validates whether moving an item by the specified vector would
   * result in a legal
   * board state. It performs comprehensive checking including:
   * <ul>
   * <li>Verifying the moved item stays within board boundaries</li>
   * <li>Checking for clearance violations with other items</li>
   * <li>Ensuring traces maintain their connectivity after movement</li>
   * </ul>
   *
   * <p>
   * Limitations: This method does not yet support items with multiple nets.
   *
   * @param p_item         the item to be moved
   * @param p_vector       the translation vector defining the movement
   * @param p_ignore_items collection of items to ignore during checking
   *                       (typically includes the item
   *                       being moved and its connected components); may be null
   * @return true if the item can be safely moved by the vector, false if
   *         conflicts would occur
   *         or if the operation is not supported for this item type
   */
  public boolean check_move_item(Item p_item, Vector p_vector, Collection<Item> p_ignore_items) {
    int net_count = p_item.net_no_arr.length;
    if (net_count > 1) {
      return false; // not yet implemented
    }
    int contact_count = 0;
    // the connected items must remain connected after moving
    if (p_item instanceof Connectable) {
      contact_count = p_item
          .get_all_contacts()
          .size();
    }
    if (p_item instanceof Trace && contact_count > 0) {
      return false;
    }
    if (p_ignore_items != null) {
      p_ignore_items.add(p_item);
    }
    for (int i = 0; i < p_item.tile_shape_count(); i++) {
      TileShape moved_shape = (TileShape) p_item
          .get_tile_shape(i)
          .translate_by(p_vector);
      if (!moved_shape.is_contained_in(bounding_box)) {
        return false;
      }
      Set<Item> obstacles = this.overlapping_items_with_clearance(moved_shape, p_item.shape_layer(i), p_item.net_no_arr,
          p_item.clearance_class_no());
      for (Item curr_item : obstacles) {
        if (p_ignore_items != null) {
          if (!p_ignore_items.contains(curr_item)) {
            if (curr_item.is_obstacle(p_item)) {
              return false;
            }
          }
        } else if (curr_item != p_item) {
          if (curr_item.is_obstacle(p_item)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Checks if the net number of an item can be changed without producing
   * clearance violations.
   *
   * <p>
   * This method verifies whether reassigning an item to a different net would
   * violate
   * clearance rules with existing board items. It examines all shapes of the item
   * and ensures
   * that changing to the new net would not create conflicts with items on other
   * nets.
   *
   * @param p_item       the item whose net assignment is being checked
   * @param p_new_net_no the proposed new net number for the item
   * @return true if the net can be changed without clearance violations,
   *         false if conflicts would occur
   */
  public boolean check_change_net(Item p_item, int p_new_net_no) {
    int[] net_no_arr = new int[1];
    net_no_arr[0] = p_new_net_no;
    for (int i = 0; i < p_item.tile_shape_count(); i++) {
      TileShape curr_shape = p_item.get_tile_shape(i);
      Set<Item> obstacles = this.overlapping_items_with_clearance(curr_shape, p_item.shape_layer(i), net_no_arr,
          p_item.clearance_class_no());
      for (SearchTreeObject curr_ob : obstacles) {
        if (curr_ob != p_item && curr_ob instanceof Connectable connectable
            && !connectable.contains_net(p_new_net_no)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Translates a drill item (pin or via) by a vector and shoves obstacle traces
   * aside.
   *
   * <p>
   * This method attempts to move a drill item using push-and-shove routing. It
   * will
   * try to push aside any traces that obstruct the new position. The operation
   * includes:
   * <ul>
   * <li>Unfixing shove-fixed traces connected to the drill item</li>
   * <li>Performing the move with recursive shoving of obstacles</li>
   * <li>Optimizing the affected area with pull-tight operations</li>
   * </ul>
   *
   * <p>
   * <strong>Warning:</strong> If this method returns false, the database may be
   * in an
   * inconsistent state and an undo operation should be performed to restore
   * integrity.
   *
   * @param p_drill_item              the drill item (pin or via) to move
   * @param p_vector                  the translation vector defining the movement
   * @param p_max_recursion_depth     maximum depth for recursive shoving of
   *                                  traces
   * @param p_max_via_recursion_depth maximum depth for recursive shoving of vias
   * @param p_tidy_width              radius around the moved item where
   *                                  optimization occurs
   * @param p_pull_tight_accuracy     accuracy level for pull-tight optimization
   * @param p_pull_tight_time_limit   maximum time in milliseconds for pull-tight
   *                                  operations
   * @return true if the move succeeded, false if clearance violations could not
   *         be resolved
   *         (database may be damaged if false is returned)
   */
  public boolean move_drill_item(DrillItem p_drill_item, Vector p_vector, int p_max_recursion_depth,
      int p_max_via_recursion_depth, int p_tidy_width, int p_pull_tight_accuracy,
      int p_pull_tight_time_limit) {
    clear_shove_failing_obstacle();
    // unfix the connected shove fixed traces.
    Collection<Item> contact_list = p_drill_item.get_normal_contacts();
    for (Item curr_contact : contact_list) {
      if (curr_contact.get_fixed_state() == FixedState.SHOVE_FIXED) {
        curr_contact.set_fixed_state(FixedState.NOT_FIXED);
      }
    }

    IntOctagon tidy_region;
    boolean calculate_tidy_region;
    if (p_tidy_width < Integer.MAX_VALUE) {
      tidy_region = IntOctagon.EMPTY;
      calculate_tidy_region = p_tidy_width > 0;
    } else {
      tidy_region = null;
      calculate_tidy_region = false;
    }
    int[] net_no_arr = p_drill_item.net_no_arr;
    start_marking_changed_area();
    if (!MoveDrillItemAlgo.insert(p_drill_item, p_vector, p_max_recursion_depth, p_max_via_recursion_depth, tidy_region,
        this)) {
      return false;
    }
    if (calculate_tidy_region) {
      tidy_region = tidy_region.enlarge(p_tidy_width);
    }
    int[] opt_net_no_arr;
    if (p_max_recursion_depth <= 0) {
      opt_net_no_arr = net_no_arr;
    } else {
      opt_net_no_arr = new int[0];
    }
    opt_changed_area(opt_net_no_arr, tidy_region, p_pull_tight_accuracy, null, null, p_pull_tight_time_limit);
    return true;
  }

  /**
   * Finds the nearest routable item at a given location that can start or end a
   * route.
   *
   * <p>
   * This method searches for connectable items (traces, drill items, or
   * conduction areas)
   * near the specified point. It's used to identify where routing can begin or
   * where it can
   * connect to existing routes. The method prioritizes drill items over traces,
   * and traces
   * over conduction areas.
   *
   * <p>
   * Items that are already connected to p_from_item (if provided) are excluded
   * from the
   * search to avoid selecting the same connected set.
   *
   * @param p_location  the point to search near
   * @param p_layer     the layer to search on; use negative value to search all
   *                    layers
   * @param p_from_item if not null, items connected to this item are ignored in
   *                    the search
   * @return the nearest routable item found, or null if no suitable item exists
   */
  public Item pick_nearest_routing_item(Point p_location, int p_layer, Item p_from_item) {
    TileShape point_shape = TileShape.get_instance(p_location);
    Collection<Item> found_items = overlapping_items(point_shape, p_layer);
    FloatPoint pick_location = p_location.to_float();
    double min_dist = Integer.MAX_VALUE;
    Item nearest_item = null;
    Set<Item> ignore_set = null;
    for (Item curr_item : found_items) {
      if (!curr_item.is_connectable()) {
        continue;
      }
      boolean candidate_found = false;
      double curr_dist = 0;
      if (curr_item instanceof PolylineTrace curr_trace) {
        if (p_layer < 0 || curr_trace.get_layer() == p_layer) {
          if (nearest_item instanceof DrillItem) {
            continue; // prefer drill items
          }
          int trace_radius = curr_trace.get_half_width();
          curr_dist = curr_trace
              .polyline()
              .distance(pick_location);
          if (curr_dist < min_dist && curr_dist <= trace_radius) {
            candidate_found = true;
          }
        }
      } else if (curr_item instanceof DrillItem curr_drill_item) {
        if (p_layer < 0 || curr_drill_item.is_on_layer(p_layer)) {
          FloatPoint drill_item_center = curr_drill_item
              .get_center()
              .to_float();
          curr_dist = drill_item_center.distance(pick_location);
          if (curr_dist < min_dist || nearest_item instanceof Trace) {
            candidate_found = true;
          }
        }
      } else if (curr_item instanceof ConductionArea curr_area) {
        if ((p_layer < 0 || curr_area.get_layer() == p_layer) && nearest_item == null) {
          candidate_found = true;
          curr_dist = Integer.MAX_VALUE;
        }
      }
      if (candidate_found) {
        if (p_from_item != null) {
          if (ignore_set == null) {
            // calculated here to avoid unnecessary calculations for performance reasoss.
            ignore_set = p_from_item.get_connected_set(-1);
          }
          if (ignore_set.contains(curr_item)) {
            continue;
          }
        }
        min_dist = curr_dist;
        nearest_item = curr_item;
      }
    }
    return nearest_item;
  }

  /**
   * Inserts a via at the specified location, shoving aside traces to avoid
   * clearance violations.
   *
   * <p>
   * This method uses push-and-shove routing to insert a via even when obstacles
   * are present.
   * It will attempt to push aside existing traces and vias to make room for the
   * new via.
   * After insertion, the affected area is optimized with pull-tight operations.
   *
   * <p>
   * <strong>Warning:</strong> If this method returns false, the database may be
   * in an
   * inconsistent state and an undo operation should be performed to restore
   * integrity.
   *
   * @param p_via_info                 the via definition including padstack and
   *                                   geometry information
   * @param p_location                 the center point where the via should be
   *                                   placed
   * @param p_net_no_arr               array of net numbers for the via
   * @param p_trace_clearance_class_no clearance class for connected traces
   * @param p_trace_pen_halfwidth_arr  half-widths of traces on each layer
   * @param p_max_recursion_depth      maximum depth for recursive shoving of
   *                                   traces
   * @param p_max_via_recursion_depth  maximum depth for recursive shoving of vias
   * @param p_tidy_width               radius around the via where optimization
   *                                   occurs
   * @param p_pull_tight_accuracy      accuracy level for pull-tight optimization
   * @param p_pull_tight_time_limit    maximum time in milliseconds for pull-tight
   *                                   operations
   * @return true if the via was successfully inserted, false if shoving failed
   *         (database may be damaged if false is returned)
   */
  public boolean forced_via(ViaInfo p_via_info, Point p_location, int[] p_net_no_arr, int p_trace_clearance_class_no,
      int[] p_trace_pen_halfwidth_arr, int p_max_recursion_depth,
      int p_max_via_recursion_depth, int p_tidy_width, int p_pull_tight_accuracy, int p_pull_tight_time_limit) {
    clear_shove_failing_obstacle();
    this.start_marking_changed_area();
    boolean result = ForcedViaAlgo.insert(p_via_info, p_location, p_net_no_arr, p_trace_clearance_class_no,
        p_trace_pen_halfwidth_arr, p_max_recursion_depth, p_max_via_recursion_depth, this);
    if (result) {
      IntOctagon tidy_clip_shape;
      if (p_tidy_width < Integer.MAX_VALUE) {
        tidy_clip_shape = p_location
            .surrounding_octagon()
            .enlarge(p_tidy_width);
      } else {
        tidy_clip_shape = null;
      }
      int[] opt_net_no_arr;
      if (p_max_recursion_depth <= 0) {
        opt_net_no_arr = p_net_no_arr;
      } else {
        opt_net_no_arr = new int[0];
      }
      this.opt_changed_area(opt_net_no_arr, tidy_clip_shape, p_pull_tight_accuracy, null, null,
          p_pull_tight_time_limit);
    }
    return result;
  }

  /**
   * Inserts a trace line segment using push-and-shove routing from start to end
   * point.
   *
   * <p>
   * This method attempts to insert a straight trace segment while shoving aside
   * obstacle
   * traces and vias. It returns the last point between start and end where the
   * insertion
   * succeeded. The method converts the segment to a polyline and delegates to the
   * polyline
   * insertion method.
   *
   * <p>
   * <strong>Warning:</strong> If the check was inaccurate and an error occurs
   * during insertion,
   * this method may return null, indicating the database is in an inconsistent
   * state and requires
   * an undo operation.
   *
   * @param p_from_corner                     the starting point of the trace
   *                                          segment
   * @param p_to_corner                       the ending point of the trace
   *                                          segment
   * @param p_half_width                      half-width of the trace (radius)
   * @param p_layer                           the layer index where the trace is
   *                                          placed
   * @param p_net_no_arr                      array of net numbers for the trace
   * @param p_clearance_class_no              clearance class number for the trace
   * @param p_max_recursion_depth             maximum depth for recursive shoving
   *                                          of traces
   * @param p_max_via_recursion_depth         maximum depth for recursive shoving
   *                                          of vias
   * @param p_max_spring_over_recursion_depth maximum depth for spring-over
   *                                          obstacle avoidance
   * @param p_tidy_width                      radius around the trace where
   *                                          optimization occurs
   * @param p_pull_tight_accuracy             accuracy level for pull-tight
   *                                          optimization
   * @param p_with_check                      if true, performs checking before
   *                                          insertion to avoid database damage
   * @param p_time_limit                      time limit for the operation
   * @return the last point where insertion succeeded (may be anywhere between
   *         start and end),
   *         or null if database was damaged during insertion
   *
   * @see #insert_forced_trace_polyline(Polyline, int, int, int[], int, int, int,
   *      int, int, int, boolean, TimeLimit)
   */
  public Point insert_forced_trace_segment(Point p_from_corner, Point p_to_corner, int p_half_width, int p_layer,
      int[] p_net_no_arr, int p_clearance_class_no, int p_max_recursion_depth,
      int p_max_via_recursion_depth, int p_max_spring_over_recursion_depth, int p_tidy_width, int p_pull_tight_accuracy,
      boolean p_with_check, TimeLimit p_time_limit) {
    if (p_from_corner.equals(p_to_corner)) {
      return p_to_corner;
    }
    Polyline insert_polyline = new Polyline(p_from_corner, p_to_corner);
    Point ok_point = insert_forced_trace_polyline(insert_polyline, p_half_width, p_layer, p_net_no_arr,
        p_clearance_class_no, p_max_recursion_depth, p_max_via_recursion_depth,
        p_max_spring_over_recursion_depth, p_tidy_width, p_pull_tight_accuracy, p_with_check, p_time_limit);
    Point result;
    if (ok_point == insert_polyline.first_corner()) {
      result = p_from_corner;
    } else if (ok_point == insert_polyline.last_corner()) {
      result = p_to_corner;
    } else {
      result = ok_point;
    }
    return result;
  }

  /**
   * Checks if a trace polyline can be inserted using push-and-shove routing.
   *
   * <p>
   * This method validates whether a multi-segment trace defined by a polyline can
   * be
   * inserted while shoving aside obstacles. It does not actually insert the
   * trace, only
   * checks feasibility. Each segment of the polyline is checked sequentially.
   *
   * @param p_polyline                        the polyline defining the trace path
   * @param p_half_width                      half-width of the trace (radius)
   * @param p_layer                           the layer index where the trace
   *                                          would be placed
   * @param p_net_no_arr                      array of net numbers for the trace
   * @param p_clearance_class_no              clearance class number for the trace
   * @param p_max_recursion_depth             maximum depth for recursive shoving
   *                                          of traces
   * @param p_max_via_recursion_depth         maximum depth for recursive shoving
   *                                          of vias
   * @param p_max_spring_over_recursion_depth maximum depth for spring-over
   *                                          obstacle avoidance
   * @return true if the entire polyline can be inserted with shoving, false if
   *         any segment fails
   */
  public boolean check_forced_trace_polyline(Polyline p_polyline, int p_half_width, int p_layer, int[] p_net_no_arr,
      int p_clearance_class_no, int p_max_recursion_depth, int p_max_via_recursion_depth,
      int p_max_spring_over_recursion_depth) {
    ShapeSearchTree search_tree = search_tree_manager.get_default_tree();
    int compensated_half_width = p_half_width + search_tree.clearance_compensation_value(p_clearance_class_no, p_layer);
    TileShape[] trace_shapes = p_polyline.offset_shapes(compensated_half_width, 0, p_polyline.arr.length - 1);
    boolean orthogonal_mode = rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE;
    ShoveTraceAlgo shove_trace_algo = new ShoveTraceAlgo(this);
    for (int i = 0; i < trace_shapes.length; i++) {
      TileShape curr_trace_shape = trace_shapes[i];
      if (orthogonal_mode) {
        curr_trace_shape = curr_trace_shape.bounding_box();
      }
      CalcFromSide from_side = new CalcFromSide(p_polyline, i + 1, curr_trace_shape);

      boolean check_shove_ok = shove_trace_algo.check(curr_trace_shape, from_side, null, p_layer, p_net_no_arr,
          p_clearance_class_no, p_max_recursion_depth, p_max_via_recursion_depth,
          p_max_spring_over_recursion_depth, null);
      if (!check_shove_ok) {
        return false;
      }
    }
    return true;
  }

  /**
   * Inserts a trace polyline using push-and-shove routing while shoving obstacles
   * aside.
   *
   * <p>
   * This is the main workhorse method for interactive trace routing. It attempts
   * to insert
   * a multi-segment trace defined by a polyline, pushing aside obstacles as
   * needed. The method
   * performs several sophisticated operations:
   * <ul>
   * <li>Springs over obstacles to find better routing paths</li>
   * <li>Combines with existing traces at connection points to avoid dog-ears</li>
   * <li>Checks and inserts each segment with push-and-shove logic</li>
   * <li>Handles partial insertion by shortening the polyline if conflicts
   * occur</li>
   * <li>Removes generated routing cycles</li>
   * <li>Optimizes the inserted trace with pull-tight operations</li>
   * </ul>
   *
   * <p>
   * <strong>Warning:</strong> If the check was inaccurate and an error occurs
   * during insertion,
   * this method may return null, indicating the database is in an inconsistent
   * state and requires
   * an undo operation.
   *
   * @param p_polyline                        the polyline defining the trace path
   * @param p_half_width                      half-width of the trace (radius)
   * @param p_layer                           the layer index where the trace is
   *                                          placed
   * @param p_net_no_arr                      array of net numbers for the trace
   * @param p_clearance_class_no              clearance class number for the trace
   * @param p_max_recursion_depth             maximum depth for recursive shoving
   *                                          of traces
   * @param p_max_via_recursion_depth         maximum depth for recursive shoving
   *                                          of vias
   * @param p_max_spring_over_recursion_depth maximum depth for spring-over
   *                                          obstacle avoidance
   * @param p_tidy_width                      radius around the trace where
   *                                          optimization occurs
   * @param p_pull_tight_accuracy             accuracy level for pull-tight
   *                                          optimization
   * @param p_with_check                      if true, performs checking before
   *                                          insertion to avoid database damage
   * @param p_time_limit                      time limit for the operation
   * @return the last corner of the polyline where insertion succeeded,
   *         or null if database was damaged during insertion
   */
  public Point insert_forced_trace_polyline(Polyline p_polyline, int p_half_width, int p_layer, int[] p_net_no_arr,
      int p_clearance_class_no, int p_max_recursion_depth, int p_max_via_recursion_depth,
      int p_max_spring_over_recursion_depth, int p_tidy_width, int p_pull_tight_accuracy, boolean p_with_check,
      TimeLimit p_time_limit) {
    clear_shove_failing_obstacle();
    Point from_corner = p_polyline.first_corner();
    Point to_corner = p_polyline.last_corner();
    if (from_corner.equals(to_corner)) {
      return to_corner;
    }
    if (!(from_corner instanceof IntPoint && to_corner instanceof IntPoint)) {
      FRLogger.warn("RoutingBoard.insert_forced_trace_segment: only implemented for IntPoints");
      return from_corner;
    }
    start_marking_changed_area();
    // Check, if there ends an item of the same net at p_from_corner.
    // If so, its geometry will be used to cut off dog ears of the check shape.
    Trace picked_trace = null;
    ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Set<Item> picked_items = this.pick_items(from_corner, p_layer, filter);
    if (picked_items.size() == 1) {
      Trace curr_picked_trace = (Trace) picked_items
          .iterator()
          .next();
      if (curr_picked_trace.nets_equal(p_net_no_arr) && curr_picked_trace.get_half_width() == p_half_width
          && curr_picked_trace.clearance_class_no() == p_clearance_class_no
          && (curr_picked_trace instanceof PolylineTrace)) {
        // can combine with the picked trace
        picked_trace = curr_picked_trace;
      }
    }
    ShapeSearchTree search_tree = search_tree_manager.get_default_tree();
    int compensated_half_width = p_half_width + search_tree.clearance_compensation_value(p_clearance_class_no, p_layer);
    ShoveTraceAlgo shove_trace_algo = new ShoveTraceAlgo(this);
    Polyline new_polyline = shove_trace_algo.spring_over_obstacles(p_polyline, compensated_half_width, p_layer,
        p_net_no_arr, p_clearance_class_no, null);
    if (new_polyline == null) {
      FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "spring_over_failed",
          "spring_over_obstacles returned null, cannot insert segment from "
              + from_corner + " to " + to_corner
              + ", layer=" + p_layer
              + ", half_width=" + p_half_width
              + ", compensated_half_width=" + compensated_half_width,
          FRLogger.formatNetLabel(this, p_net_no_arr.length > 0 ? p_net_no_arr[0] : -1),
          new Point[] { from_corner, to_corner });
      return from_corner;
    }
    Polyline combined_polyline;
    if (picked_trace == null) {
      combined_polyline = new_polyline;
    } else {
      PolylineTrace combine_trace = (PolylineTrace) picked_trace;
      combined_polyline = new_polyline.combine(combine_trace.polyline());
    }
    if (combined_polyline.arr.length < 3) {
      FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "polyline_too_short",
          "combined_polyline has insufficient lines (arr.length=" + combined_polyline.arr.length + " < 3)"
              + ", new_polyline.arr.length=" + new_polyline.arr.length
              + ", from " + from_corner + " to " + to_corner
              + ", layer=" + p_layer,
          FRLogger.formatNetLabel(this, p_net_no_arr.length > 0 ? p_net_no_arr[0] : -1),
          new Point[] { from_corner, to_corner });
      return from_corner;
    }
    int start_shape_no = combined_polyline.arr.length - new_polyline.arr.length;
    // calculate the last shapes of combined_polyline for checking
    TileShape[] trace_shapes = combined_polyline.offset_shapes(compensated_half_width, start_shape_no,
        combined_polyline.arr.length - 1);
    int last_shape_no = trace_shapes.length;
    boolean orthogonal_mode = rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE;
    for (int i = 0; i < trace_shapes.length; i++) {
      TileShape curr_trace_shape = trace_shapes[i];
      if (orthogonal_mode) {
        curr_trace_shape = curr_trace_shape.bounding_box();
      }
      CalcFromSide from_side = new CalcFromSide(combined_polyline,
          combined_polyline.corner_count() - trace_shapes.length - 1 + i, curr_trace_shape);

      if (p_with_check) {
        boolean check_shove_ok = shove_trace_algo.check(curr_trace_shape, from_side, null, p_layer, p_net_no_arr,
            p_clearance_class_no, p_max_recursion_depth, p_max_via_recursion_depth,
            p_max_spring_over_recursion_depth, p_time_limit);
        if (!check_shove_ok) {
          FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "shove_check_failed",
              "shove check failed at shape " + i + "/" + trace_shapes.length
                  + ", from " + from_corner + " to " + to_corner
                  + ", layer=" + p_layer
                  + ", half_width=" + p_half_width,
              "Net #" + (p_net_no_arr.length > 0 ? p_net_no_arr[0] : -1),
              new Point[] { from_corner, to_corner });
          last_shape_no = i;
          break;
        }
      }
      boolean insert_ok = shove_trace_algo.insert(curr_trace_shape, from_side, p_layer, p_net_no_arr,
          p_clearance_class_no, null, p_max_recursion_depth, p_max_via_recursion_depth,
          p_max_spring_over_recursion_depth);
      if (!insert_ok) {
        FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "shove_insert_failed",
            "shove insert failed at shape " + i + "/" + trace_shapes.length
                + ", from " + from_corner + " to " + to_corner
                + ", layer=" + p_layer
                + ", half_width=" + p_half_width,
            "Net #" + (p_net_no_arr.length > 0 ? p_net_no_arr[0] : -1),
            new Point[] { from_corner, to_corner });
        return null;
      }
    }
    Point new_corner = to_corner;
    if (last_shape_no < trace_shapes.length) {
      // the shove with index last_shape_no failed.
      // Sample the shove line to a shorter shove distance and try again.
      TileShape last_trace_shape = trace_shapes[last_shape_no];
      if (orthogonal_mode) {
        last_trace_shape = last_trace_shape.bounding_box();
      }
      int sample_width = 2 * this.get_min_trace_half_width();
      FloatPoint last_corner = new_polyline.corner_approx(last_shape_no + 1);
      FloatPoint prev_last_corner = new_polyline.corner_approx(last_shape_no);
      double last_segment_length = last_corner.distance(prev_last_corner);
      if (last_segment_length > 100 * sample_width) {
        // to many cycles to sample
        FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "segment_too_long",
            "last segment too long to sample: length=" + last_segment_length
                + ", sample_width=" + sample_width
                + ", max_allowed=" + (100 * sample_width)
                + ", from " + from_corner + " to " + to_corner
                + ", layer=" + p_layer,
            "Net #" + (p_net_no_arr.length > 0 ? p_net_no_arr[0] : -1),
            new Point[] { from_corner, to_corner });
        return from_corner;
      }
      int shape_index = combined_polyline.corner_count() - trace_shapes.length - 1 + last_shape_no;
      if (last_segment_length > sample_width) {
        new_polyline = new_polyline.shorten(new_polyline.arr.length - (trace_shapes.length - last_shape_no - 1),
            sample_width);
        Point curr_last_corner = new_polyline.last_corner();
        if (!(curr_last_corner instanceof IntPoint)) {
          FRLogger.warn("RoutingBoard.insert_forced_trace_polyline: IntPoint expected");
          return from_corner;
        }
        new_corner = curr_last_corner;
        if (picked_trace == null) {
          combined_polyline = new_polyline;
        } else {
          PolylineTrace combine_trace = (PolylineTrace) picked_trace;
          combined_polyline = new_polyline.combine(combine_trace.polyline());
        }
        if (combined_polyline.arr.length < 3) {
          FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "shortened_polyline_too_short",
              "combined_polyline after shortening has insufficient lines (arr.length=" + combined_polyline.arr.length
                  + " < 3)"
                  + ", new_corner=" + new_corner
                  + ", sample_width=" + sample_width
                  + ", from " + from_corner + " to " + to_corner
                  + ", layer=" + p_layer,
              "Net #" + (p_net_no_arr.length > 0 ? p_net_no_arr[0] : -1),
              new Point[] { from_corner, to_corner });
          return new_corner;
        }
        shape_index = combined_polyline.arr.length - 3;
        last_trace_shape = combined_polyline.offset_shape(compensated_half_width, shape_index);
        if (orthogonal_mode) {
          last_trace_shape = last_trace_shape.bounding_box();
        }
      }
      CalcFromSide from_side = new CalcFromSide(combined_polyline, shape_index, last_trace_shape);
      boolean check_shove_ok = shove_trace_algo.check(last_trace_shape, from_side, null, p_layer, p_net_no_arr,
          p_clearance_class_no, p_max_recursion_depth, p_max_via_recursion_depth,
          p_max_spring_over_recursion_depth, p_time_limit);
      if (!check_shove_ok) {
        FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "final_shove_check_failed",
            "final shove check failed after shortening"
                + ", shape_index=" + shape_index
                + ", new_corner=" + new_corner
                + ", from " + from_corner + " to " + to_corner
                + ", layer=" + p_layer
                + ", half_width=" + p_half_width,
            "Net #" + (p_net_no_arr.length > 0 ? p_net_no_arr[0] : -1),
            new Point[] { from_corner, to_corner });
        return from_corner;
      }
      boolean insert_ok = shove_trace_algo.insert(last_trace_shape, from_side, p_layer, p_net_no_arr,
          p_clearance_class_no, null, p_max_recursion_depth, p_max_via_recursion_depth,
          p_max_spring_over_recursion_depth);
      if (!insert_ok) {
        FRLogger.warn("RoutingBoard.insert_forced_trace_polyline: shove trace failed");
        return null;
      }
    }
    // insert the new trace segment
    for (int i = 0; i < new_polyline.corner_count(); i++) {
      join_changed_area(new_polyline.corner_approx(i), p_layer);
    }
    PolylineTrace new_trace = insert_trace_without_cleaning(new_polyline, p_layer, p_half_width, p_net_no_arr,
        p_clearance_class_no, FixedState.NOT_FIXED);

    new_trace.combine();

    // Log trace state after combine for debug nets
    if (p_net_no_arr != null && p_net_no_arr.length > 0) {
      int netNo = p_net_no_arr[0];
      if (netNo == 99 || netNo == 98 || netNo == 94) {
        String netLabel = "Net #" + netNo;
        if (rules != null && rules.nets != null && netNo <= rules.nets.max_net_no()) {
          netLabel += " (" + rules.nets.get(netNo).name + ")";
        }

        // Check if the trace still exists (might have been merged)
        boolean traceStillExists = false;
        try {
          traceStillExists = (new_trace.board != null && new_trace.get_id_no() > 0);
        } catch (Exception e) {
          // Trace was removed
        }

        if (traceStillExists) {
          FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "trace_after_combine",
              "Trace after combine: trace_id=" + new_trace.get_id_no()
                  + ", from=" + new_trace.first_corner()
                  + ", to=" + new_trace.last_corner()
                  + ", layer=" + p_layer
                  + ", corners=" + new_trace.corner_count()
                  + ", still_exists=true",
              netLabel,
              new Point[] { new_trace.first_corner(), new_trace.last_corner() });
        } else {
          FRLogger.trace("RoutingBoard.insert_forced_trace_polyline", "trace_merged_away",
              "Trace was merged during combine (no longer exists as separate trace)",
              netLabel,
              null);
        }
      }
    }

    IntOctagon tidy_region = null;
    if (p_tidy_width < Integer.MAX_VALUE) {
      tidy_region = new_corner
          .surrounding_octagon()
          .enlarge(p_tidy_width);
    }
    int[] opt_net_no_arr;
    if (p_max_recursion_depth <= 0) {
      opt_net_no_arr = p_net_no_arr;
    } else {
      opt_net_no_arr = new int[0];
    }
    PullTightAlgo pull_tight_algo = PullTightAlgo.get_instance(this, opt_net_no_arr, tidy_region, p_pull_tight_accuracy,
        null, -1, new_corner, p_layer);

    try {
      // Remove evtl. generated cycles because otherwise pull_tight may not work
      // correctly.
      // NOTE: Use normalize_without_tail_removal during incremental routing to
      // prevent
      // premature removal of traces that are still being built and don't have all
      // connections yet.
      if (new_trace.normalize_without_tail_removal(changed_area.get_area(p_layer))) {
        pull_tight_algo.split_traces_at_keep_point();
        // otherwise the new corner may no more be contained in the new trace after
        // optimizing
        ItemSelectionFilter item_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
        Set<Item> curr_picked_items = this.pick_items(new_corner, p_layer, item_filter);
        new_trace = null;
        if (!curr_picked_items.isEmpty()) {
          Item found_trace = curr_picked_items
              .iterator()
              .next();
          if (found_trace instanceof PolylineTrace trace) {
            new_trace = trace;
          }
        }
      }
    } catch (Exception e) {
      FRLogger.error("RoutingBoard.insert_forced_trace_polyline: Couldn't remove generated circles from the board.", e);
    }

    // To avoid that a separate handling for moving backwards in the own trace line
    // becomes necessary, pull tight is called here.
    if (p_tidy_width > 0 && new_trace != null) {
      new_trace.pull_tight(pull_tight_algo);
    }
    return new_corner;
  }

  /**
   * Initializes the auto-route database for routing a connection.
   *
   * <p>
   * This method sets up or reuses the autoroute engine for a specific net and
   * clearance class.
   * The engine maintains a specialized search tree and expansion rooms for
   * efficient pathfinding.
   *
   * <p>
   * If p_retain_autoroute_database is true, the engine is kept active after
   * routing completes,
   * allowing it to be maintained incrementally as the board changes. This
   * improves performance for
   * subsequent routing operations but uses more memory. If false or if clearance
   * class changes,
   * a new engine is created.
   *
   * @param p_net_no                    the net number to route
   * @param p_trace_clearance_class_no  the clearance class for the traces being
   *                                    routed
   * @param p_stoppable_thread          thread that can request algorithm
   *                                    termination; may be null
   * @param p_time_limit                time limit for routing operations; may be
   *                                    null for no limit
   * @param p_retain_autoroute_database if true, keeps the engine active for
   *                                    incremental updates;
   *                                    if false, engine will be cleared after
   *                                    routing
   * @return the initialized autoroute engine ready for routing operations
   *
   * @see #finish_autoroute()
   * @see AutorouteEngine
   */
  public AutorouteEngine init_autoroute(int p_net_no, int p_trace_clearance_class_no, Stoppable p_stoppable_thread,
      TimeLimit p_time_limit, boolean p_retain_autoroute_database) {
    if (this.autoroute_engine == null || !p_retain_autoroute_database
        || this.autoroute_engine.autoroute_search_tree.compensated_clearance_class_no != p_trace_clearance_class_no) {
      this.autoroute_engine = new AutorouteEngine(this, p_trace_clearance_class_no, p_retain_autoroute_database);
    }
    this.autoroute_engine.init_connection(p_net_no, p_stoppable_thread, p_time_limit);
    return this.autoroute_engine;
  }

  /**
   * Clears the auto-route database if it was retained after previous routing
   * operations.
   *
   * <p>
   * Call this method to free memory used by the autoroute engine when routing is
   * complete
   * and no further routing operations are immediately planned. The engine can be
   * recreated
   * later if needed by calling init_autoroute again.
   *
   * @see #init_autoroute(int, int, Stoppable, TimeLimit, boolean)
   */
  public void finish_autoroute() {
    if (this.autoroute_engine != null) {
      this.autoroute_engine.clear();
    }
    this.autoroute_engine = null;
  }

  /**
   * Automatically routes an item to another item of the same net not yet
   * electrically connected.
   *
   * <p>
   * This method performs automatic routing from the given item to any unconnected
   * item on
   * the same net. It uses the autoroute engine to find an optimal path while
   * respecting design
   * rules and clearances. The method handles various termination conditions and
   * optimizes the
   * result if routing succeeds.
   *
   * <p>
   * The method will not route items that:
   * <ul>
   * <li>Are not connectable (non-routing items)</li>
   * <li>Have no nets assigned</li>
   * <li>Are already fully connected</li>
   * <li>Are connected to a plane (conduction area)</li>
   * </ul>
   *
   * @param p_item             the starting item for routing (must be connectable
   *                           and have at least one net)
   * @param routerSettings     the router configuration including trace costs and
   *                           pull-tight settings
   * @param p_via_costs        the cost factor for via insertion during routing
   * @param p_stoppable_thread thread that can request algorithm termination; may
   *                           be null
   * @param p_time_limit       time limit for the routing operation; may be null
   *                           for no limit
   * @return an AutorouteAttemptResult indicating the outcome (ROUTED,
   *         ALREADY_CONNECTED,
   *         NO_CONNECTIONS, CONNECTED_TO_PLANE, etc.) with explanatory message
   *
   * @see AutorouteAttemptResult
   * @see AutorouteAttemptState
   */
  public AutorouteAttemptResult autoroute(Item p_item, RouterSettings routerSettings, int p_via_costs,
      Stoppable p_stoppable_thread, TimeLimit p_time_limit) {
    if (!(p_item instanceof Connectable) || p_item.net_count() == 0) {
      return new AutorouteAttemptResult(AutorouteAttemptState.NO_CONNECTIONS,
          "The item '" + p_item + "' is not connectable.");
    }
    if (p_item.net_count() > 1) {
      FRLogger.warn("RoutingBoard.autoroute: net_count > 1 not yet implemented");
    }
    int route_net_no = p_item.get_net_no(0);
    AutorouteControl ctrl_settings = new AutorouteControl(this, route_net_no, routerSettings, p_via_costs,
        routerSettings.get_trace_cost_arr());
    ctrl_settings.remove_unconnected_vias = false;
    Set<Item> route_start_set = p_item.get_connected_set(route_net_no);
    Net route_net = rules.nets.get(route_net_no);
    if (route_net != null && route_net.contains_plane()) {
      for (Item curr_item : route_start_set) {
        if (curr_item instanceof ConductionArea) {
          return new AutorouteAttemptResult(AutorouteAttemptState.CONNECTED_TO_PLANE,
              "The item '" + curr_item + "' is connected to a plane.");
        }
      }
    }
    Set<Item> route_dest_set = p_item.get_unconnected_set(route_net_no);
    if (route_dest_set.isEmpty()) {
      return new AutorouteAttemptResult(AutorouteAttemptState.ALREADY_CONNECTED,
          "The item '" + p_item + "' is already connected.");
    }
    SortedSet<Item> ripped_item_list = new TreeSet<>();
    AutorouteEngine curr_autoroute_engine = init_autoroute(p_item.get_net_no(0), ctrl_settings.trace_clearance_class_no,
        p_stoppable_thread, p_time_limit, false);
    AutorouteAttemptResult result = curr_autoroute_engine.autoroute_connection(route_start_set, route_dest_set,
        ctrl_settings, ripped_item_list);
    if (result.state == AutorouteAttemptState.ROUTED) {
      final int time_limit_to_prevent_endless_loop = 1000;
      opt_changed_area(new int[0], null, routerSettings.trace_pull_tight_accuracy, ctrl_settings.trace_costs,
          p_stoppable_thread, time_limit_to_prevent_endless_loop);
    }
    return result;
  }

  /**
   * Autoroutes from the input pin until the first via, in case the pin and its
   * connected set has only 1 layer. Ripup is allowed if p_ripup_costs is
   * {@literal >}= 0. Returns an enum of type
   * AutorouteEngine.AutorouteResult
   */
  public AutorouteAttemptResult fanout(Pin p_pin, RouterSettings routerSettings, int p_ripup_costs,
      Stoppable p_stoppable_thread, TimeLimit p_time_limit) {
    if (p_pin.first_layer() != p_pin.last_layer() || p_pin.net_count() != 1) {
      return new AutorouteAttemptResult(AutorouteAttemptState.ALREADY_CONNECTED,
          "The pin '" + p_pin + "' is already connected.");
    }
    int pin_net_no = p_pin.get_net_no(0);
    int pin_layer = p_pin.first_layer();
    Set<Item> pin_connected_set = p_pin.get_connected_set(pin_net_no);
    for (Item curr_item : pin_connected_set) {
      if (curr_item.first_layer() != pin_layer || curr_item.last_layer() != pin_layer) {
        return new AutorouteAttemptResult(AutorouteAttemptState.ALREADY_CONNECTED,
            "The pin '" + p_pin + "' is already connected.");
      }
    }
    Set<Item> unconnected_set = p_pin.get_unconnected_set(pin_net_no);
    if (unconnected_set.isEmpty()) {
      return new AutorouteAttemptResult(AutorouteAttemptState.NO_UNCONNECTED_NETS,
          "The pin '" + p_pin + "' is already connected.");
    }
    AutorouteControl ctrl_settings = new AutorouteControl(this, pin_net_no, routerSettings);
    ctrl_settings.is_fanout = true;
    ctrl_settings.remove_unconnected_vias = false;
    if (p_ripup_costs >= 0) {
      ctrl_settings.ripup_allowed = true;
      ctrl_settings.ripup_costs = p_ripup_costs;
    }
    SortedSet<Item> ripped_item_list = new TreeSet<>();
    AutorouteEngine curr_autoroute_engine = init_autoroute(pin_net_no, ctrl_settings.trace_clearance_class_no,
        p_stoppable_thread, p_time_limit, false);
    AutorouteAttemptResult result = curr_autoroute_engine.autoroute_connection(pin_connected_set, unconnected_set,
        ctrl_settings, ripped_item_list);
    if (result.state == AutorouteAttemptState.ROUTED) {
      final int time_limit_to_prevent_endless_loop = 1000;
      opt_changed_area(new int[0], null, routerSettings.trace_pull_tight_accuracy, ctrl_settings.trace_costs,
          p_stoppable_thread, time_limit_to_prevent_endless_loop);
    }
    return result;
  }

  /**
   * Inserts a trace from p_from_point to the nearest point on p_to_trace. Returns
   * false, if that is not possible without clearance violation.
   */
  public boolean connect_to_trace(IntPoint p_from_point, Trace p_to_trace, int p_pen_half_width, int p_cl_type) {

    Point first_corner = p_to_trace.first_corner();

    Point last_corner = p_to_trace.last_corner();

    int[] net_no_arr = p_to_trace.net_no_arr;

    if (!(p_to_trace instanceof PolylineTrace to_trace)) {
      return false; // not yet implemented
    }
    if (to_trace
        .polyline()
        .contains(p_from_point)) {
      // no connection line necessary
      return true;
    }
    LineSegment projection_line = to_trace
        .polyline()
        .projection_line(p_from_point);
    if (projection_line == null) {
      return false;
    }
    Polyline connection_line = projection_line.to_polyline();
    if (connection_line == null || connection_line.arr.length != 3) {
      return false;
    }
    int trace_layer = p_to_trace.get_layer();
    if (!this.check_polyline_trace(connection_line, trace_layer, p_pen_half_width, p_to_trace.net_no_arr, p_cl_type)) {
      return false;
    }
    if (this.changed_area != null) {
      for (int i = 0; i < connection_line.corner_count(); i++) {
        this.changed_area.join(connection_line.corner_approx(i), trace_layer);
      }
    }

    this.insert_trace(connection_line, trace_layer, p_pen_half_width, net_no_arr, p_cl_type, FixedState.NOT_FIXED);

    if (!p_from_point.equals(first_corner)) {
      Trace tail = this.get_trace_tail(first_corner, trace_layer, net_no_arr);
      if (tail != null && !tail.is_user_fixed()) {
        this.remove_item(tail);
      }
    }
    if (!p_from_point.equals(last_corner)) {
      Trace tail = this.get_trace_tail(last_corner, trace_layer, net_no_arr);
      if (tail != null && !tail.is_user_fixed()) {
        this.remove_item(tail);
      }
    }
    return true;
  }

  /**
   * Checks, if the list p_items contains traces, which have no contact at their
   * start or end point. Trace with net number p_except_net_no are ignored.
   */
  public boolean contains_trace_tails(Collection<Item> p_items, int[] p_except_net_no_arr) {
    for (Item curr_ob : p_items) {
      if (curr_ob instanceof Trace curr_trace) {
        if (!curr_trace.nets_equal(p_except_net_no_arr)) {
          if (curr_trace.is_tail()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Removes all trace tails of the input net. If p_net_no {@literal <}= 0, the
   * tails of all nets are removed. Returns true, if something was removed.
   */
  public boolean remove_trace_tails(int p_net_no, Item.StopConnectionOption p_stop_connection_option) {
    FRLogger.info("RoutingBoard.remove_trace_tails called: net=" + p_net_no + ", stop_option=" + p_stop_connection_option + ", total_items=" + this.get_items().size());
    SortedSet<Item> stub_set = new TreeSet<>();
    Collection<Item> board_items = this.get_items();
    for (Item curr_item : board_items) {
      if (!curr_item.is_routable()) {
        continue;
      }
      if (curr_item.net_count() != 1) {
        continue;
      }
      if (p_net_no > 0 && curr_item.get_net_no(0) != p_net_no) {
        continue;
      }

      if (curr_item instanceof Trace trace) {
        Collection<Item> startContacts = trace.get_start_contacts();
        Collection<Item> endContacts = trace.get_end_contacts();
        boolean startEmpty = startContacts.isEmpty();
        boolean endEmpty = endContacts.isEmpty();
        String reasonCode;
        String reasonDetail;
        if (startEmpty && endEmpty) {
          reasonCode = "no_contacts_both";
          reasonDetail = "both endpoints have no contacts";
        } else if (startEmpty) {
          reasonCode = "no_contacts_start";
          reasonDetail = "start endpoint has no contacts";
        } else if (endEmpty) {
          reasonCode = "no_contacts_end";
          reasonDetail = "end endpoint has no contacts";
        } else {
          reasonCode = "contacts_both";
          reasonDetail = "both endpoints have contacts";
        }

        // Build contact details strings
        StringBuilder startContactStr = new StringBuilder();
        for (Item contact : startContacts) {
          if (startContactStr.length() > 0) startContactStr.append(",");
          startContactStr.append(contact.getClass().getSimpleName()).append("#").append(contact.get_id_no());
        }
        StringBuilder endContactStr = new StringBuilder();
        for (Item contact : endContacts) {
          if (endContactStr.length() > 0) endContactStr.append(",");
          endContactStr.append(contact.getClass().getSimpleName()).append("#").append(contact.get_id_no());
        }

        FRLogger.trace("RoutingBoard.remove_trace_tails", "tail_check",
            FRLogger.buildTracePayload("tail_detection", "scan", "check",
                "item_type=Trace"
                    + " item_id=" + curr_item.get_id_no()
                    + " reason_code=" + reasonCode
                    + " reason_detail=" + reasonDetail
                    + " start_contacts=" + startContacts.size()
                    + " start_contact_items=[" + startContactStr + "]"
                    + " end_contacts=" + endContacts.size()
                    + " end_contact_items=[" + endContactStr + "]"
                    + " start=" + trace.first_corner()
                    + " end=" + trace.last_corner()
                    + " stop_option=" + p_stop_connection_option),
            FRLogger.formatNetLabel(this, curr_item.get_net_no(0)),
            new Point[] { trace.first_corner(), trace.last_corner() });
      }

      if (curr_item.is_tail()) {
        if (curr_item instanceof Via) {
          if (p_stop_connection_option == Item.StopConnectionOption.VIA) {
            continue;
          }
          if (p_stop_connection_option == Item.StopConnectionOption.FANOUT_VIA) {
            if (curr_item.is_fanout_via(null)) {
              continue;
            }
          }
        }
        if (curr_item instanceof Trace trace) {
          Collection<Item> startContacts = trace.get_start_contacts();
          Collection<Item> endContacts = trace.get_end_contacts();
          boolean startEmpty = startContacts.isEmpty();
          boolean endEmpty = endContacts.isEmpty();
          String reasonCode;
          String reasonDetail;
          if (startEmpty && endEmpty) {
            reasonCode = "no_contacts_both";
            reasonDetail = "both endpoints have no contacts";
          } else if (startEmpty) {
            reasonCode = "no_contacts_start";
            reasonDetail = "start endpoint has no contacts";
          } else {
            reasonCode = "no_contacts_end";
            reasonDetail = "end endpoint has no contacts";
          }

          // Build contact details strings
          StringBuilder startContactStr = new StringBuilder();
          for (Item contact : startContacts) {
            if (startContactStr.length() > 0) startContactStr.append(",");
            startContactStr.append(contact.getClass().getSimpleName()).append("#").append(contact.get_id_no());
          }
          StringBuilder endContactStr = new StringBuilder();
          for (Item contact : endContacts) {
            if (endContactStr.length() > 0) endContactStr.append(",");
            endContactStr.append(contact.getClass().getSimpleName()).append("#").append(contact.get_id_no());
          }

          FRLogger.trace("RoutingBoard.remove_trace_tails", "tail_candidate",
              FRLogger.buildTracePayload("tail_detection", "scan", "candidate",
                  "item_type=Trace"
                      + " item_id=" + curr_item.get_id_no()
                      + " reason_code=" + reasonCode
                      + " reason_detail=" + reasonDetail
                      + " start_contacts=" + startContacts.size()
                      + " start_contact_items=[" + startContactStr + "]"
                      + " end_contacts=" + endContacts.size()
                      + " end_contact_items=[" + endContactStr + "]"
                      + " start=" + trace.first_corner()
                      + " end=" + trace.last_corner()
                      + " stop_option=" + p_stop_connection_option),
              FRLogger.formatNetLabel(this, curr_item.get_net_no(0)),
              new Point[] { trace.first_corner(), trace.last_corner() });
        } else {
          FRLogger.trace("RoutingBoard.remove_trace_tails", "tail_candidate",
              FRLogger.buildTracePayload("tail_detection", "scan", "candidate",
                  "item_type=" + curr_item.getClass().getSimpleName()
                      + " item_id=" + curr_item.get_id_no()
                      + " stop_option=" + p_stop_connection_option),
              FRLogger.formatNetLabel(this, curr_item.get_net_no(0)),
              null);
        }
        stub_set.add(curr_item);
      }
    }
    SortedSet<Item> stub_connections = new TreeSet<>();
    for (Item curr_item : stub_set) {
      int item_contact_count = curr_item
          .get_normal_contacts()
          .size();

      if (item_contact_count == 1) {
        Set<Item> connections = curr_item.get_connection_items(p_stop_connection_option);
        FRLogger.trace("RoutingBoard.remove_trace_tails", "gathering_connections",
            FRLogger.buildTracePayload("tail_detection", "collect", "connections",
                "item_id=" + curr_item.get_id_no()
                    + " item_type=" + curr_item.getClass().getSimpleName()
                    + " contact_count=" + item_contact_count
                    + " connection_items_count=" + connections.size()
                    + " stop_option=" + p_stop_connection_option),
            FRLogger.formatNetLabel(this, curr_item.get_net_no(0)),
            null);
        stub_connections.addAll(connections);
      } else {
        // the connected items are no stubs for example if a via is only connected on 1
        // layer,
        // but to several traces.
        FRLogger.trace("RoutingBoard.remove_trace_tails", "adding_single_item",
            FRLogger.buildTracePayload("tail_detection", "collect", "single_item",
                "item_id=" + curr_item.get_id_no()
                    + " item_type=" + curr_item.getClass().getSimpleName()
                    + " contact_count=" + item_contact_count),
            FRLogger.formatNetLabel(this, curr_item.get_net_no(0)),
            null);
        stub_connections.add(curr_item);
      }
    }
    if (stub_connections.isEmpty()) {
      return false;
    }

    for (Item curr_item : stub_connections) {
      FRLogger.trace("RoutingBoard.remove_trace_tails", "tail_removal",
          FRLogger.buildTracePayload("tail_detection", "remove", "item",
              "stop_option=" + p_stop_connection_option
                  + " item_type=" + curr_item.getClass().getSimpleName()
                  + " item=" + curr_item),
          FRLogger.formatNetLabel(this, curr_item.get_net_no(0)),
          null);
    }

    this.remove_items(stub_connections);
    return true;
  }

  /**
   * Clears temporary autoroute information from all items on the board.
   *
   * <p>
   * During autorouting, items may cache temporary data such as expansion room
   * references
   * and routing state. This method iterates through all items and clears this
   * cached information.
   * Call this method when autorouting is complete or when preparing for a fresh
   * routing attempt.
   *
   * @see Item#clear_autoroute_info()
   */
  public void clear_all_item_temporary_autoroute_data() {
    Iterator<UndoableObjects.UndoableObjectNode> it = this.item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      curr_item.clear_autoroute_info();
    }
  }

  /**
   * Sets, if all conduction areas on the board are obstacles for route of foreign
   * nets.
   */
  public void change_conduction_is_obstacle(boolean p_value) {
    if (this.rules.get_ignore_conduction() != p_value) {
      return; // no multiply
    }
    boolean something_changed = false;
    // Change the is_obstacle property of all conduction areas of the board.
    Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
    for (;;) {
      Item curr_item = (Item) item_list.read_object(it);
      if (curr_item == null) {
        break;
      }
      if (curr_item instanceof ConductionArea curr_conduction_area) {
        Layer curr_layer = layer_structure.arr[curr_conduction_area.get_layer()];
        if (curr_layer.is_signal && curr_conduction_area.get_is_obstacle() != p_value) {
          curr_conduction_area.set_is_obstacle(p_value);
          something_changed = true;
        }
      }
    }
    this.rules.set_ignore_conduction(!p_value);
    if (something_changed) {
      this.search_tree_manager.reinsert_tree_items();
    }
  }

  /**
   * Reduces the net assignments of traces and vias to be a subset of their
   * contact items' nets.
   *
   * <p>
   * This method is particularly important for routing at tie pins, where multiple
   * nets may
   * converge. It ensures that traces and vias with multiple nets only retain nets
   * that are
   * actually present in their connecting items (pins, other traces, etc.).
   *
   * <p>
   * The algorithm iteratively processes all routable items with multiple nets:
   * <ul>
   * <li>For vias: removes any net not present in all connected items</li>
   * <li>For traces: removes nets not present in connected pins or other
   * contacts</li>
   * <li>Repeats until no further reductions are possible</li>
   * </ul>
   *
   * <p>
   * System-fixed items are not modified as their nets are considered
   * authoritative.
   *
   * @return true if any item's nets were reduced, false if no changes were needed
   */
  public boolean reduce_nets_of_route_items() {
    boolean result = false;
    boolean something_changed = true;
    while (something_changed) {
      something_changed = false;
      Iterator<UndoableObjects.UndoableObjectNode> it = item_list.start_read_object();
      for (;;) {
        UndoableObjects.Storable curr_ob = item_list.read_object(it);
        if (curr_ob == null) {
          break;
        }
        Item curr_item = (Item) curr_ob;
        if (curr_item.net_no_arr.length <= 1 || curr_item.get_fixed_state() == FixedState.SYSTEM_FIXED) {
          continue;
        }
        if (curr_ob instanceof Via) {
          Collection<Item> contacts = curr_item.get_normal_contacts();
          for (int curr_net_no : curr_item.net_no_arr) {
            for (Item curr_contact : contacts) {
              if (!curr_contact.contains_net(curr_net_no)) {
                if (curr_net_no == 99) {
                  FRLogger.trace("RoutingBoard.reduce_nets_of_route_items", "remove_net",
                      "Removing net #99 from via: via=" + curr_item,
                      "Net #99",
                      null);
                }
                curr_item.remove_from_net(curr_net_no);
                something_changed = true;
                break;
              }
            }
            if (something_changed) {
              break;
            }
          }

        } else if (curr_ob instanceof Trace curr_trace) {
          Collection<Item> contacts = curr_trace.get_start_contacts();
          for (int i = 0; i < 2; i++) {
            for (int curr_net_no : curr_item.net_no_arr) {
              boolean pin_found = false;
              for (Item curr_contact : contacts) {
                if (curr_contact instanceof Pin) {
                  pin_found = true;
                  if (!curr_contact.contains_net(curr_net_no)) {
                    if (curr_net_no == 99) {
                      FRLogger.trace("RoutingBoard.reduce_nets_of_route_items", "remove_net",
                          "Removing net #99 from trace (pin contact): trace=" + curr_item,
                          "Net #99",
                          null);
                    }
                    curr_item.remove_from_net(curr_net_no);
                    something_changed = true;
                    break;
                  }
                }
              }
              if (!pin_found) // at tie pins traces may have different nets
              {
                for (Item curr_contact : contacts) {
                  if (!(curr_contact instanceof Pin) && !curr_contact.contains_net(curr_net_no)) {
                    if (curr_net_no == 99) {
                      FRLogger.trace("RoutingBoard.reduce_nets_of_route_items", "remove_net",
                          "Removing net #99 from trace (non-pin contact): trace=" + curr_item,
                          "Net #99",
                          null);
                    }
                    curr_item.remove_from_net(curr_net_no);
                    something_changed = true;
                    break;
                  }
                }
              }
            }
            if (something_changed) {
              break;
            }
            contacts = curr_trace.get_end_contacts();
          }
          if (something_changed) {
            break;
          }
        }
        if (something_changed) {
          break;
        }
      }
    }
    return result;
  }

  /**
   * Returns the obstacle item responsible for the most recent shove failure.
   *
   * <p>
   * When a push-and-shove operation fails, this method provides access to the
   * item that
   * caused the failure. This information is useful for debugging routing issues
   * and providing
   * user feedback about why a routing operation could not complete.
   *
   * @return the item that blocked the last shove attempt, or null if no recent
   *         failure or
   *         if the failure information has been cleared
   *
   * @see #get_shove_failing_layer()
   * @see #set_shove_failing_obstacle(Item)
   */
  public Item get_shove_failing_obstacle() {
    return shove_failing_obstacle;
  }

  /**
   * Records the obstacle item responsible for a shove failure.
   *
   * <p>
   * This method is called internally by shove algorithms when they encounter an
   * item that
   * cannot be pushed aside. The recorded information can be retrieved later for
   * debugging or
   * user feedback.
   *
   * @param p_item the item that caused the shove to fail
   *
   * @see #get_shove_failing_obstacle()
   * @see #set_shove_failing_layer(int)
   */
  void set_shove_failing_obstacle(Item p_item) {
    shove_failing_obstacle = p_item;
  }

  /**
   * Returns the layer index where the most recent shove failure occurred.
   *
   * <p>
   * When a push-and-shove operation fails, this provides the layer where the
   * obstacle
   * was encountered. Used in conjunction with get_shove_failing_obstacle() to
   * provide complete
   * failure information.
   *
   * @return the layer index where shove failed, or -1 if no recent failure or if
   *         the failure
   *         information has been cleared
   *
   * @see #get_shove_failing_obstacle()
   * @see #set_shove_failing_obstacle(Item)
   */
  public int get_shove_failing_layer() {
    return shove_failing_layer;
  }

  /**
   * Records the layer index where a shove failure occurred.
   *
   * <p>
   * This method is called internally by shove algorithms when they fail at a
   * specific layer.
   * The recorded layer index can be retrieved later for debugging or user
   * feedback.
   *
   * @param p_layer the layer index where the shove failed
   *
   * @see #get_shove_failing_layer()
   * @see #set_shove_failing_obstacle(Item)
   */
  void set_shove_failing_layer(int p_layer) {
    shove_failing_layer = p_layer;
  }

  /**
   * Clears the recorded shove failure information.
   *
   * <p>
   * This method resets both the failing obstacle item and layer to their default
   * values
   * (null and -1 respectively). It should be called before starting a new shove
   * operation to
   * ensure old failure information doesn't persist.
   *
   * @see #get_shove_failing_obstacle()
   * @see #get_shove_failing_layer()
   */
  private void clear_shove_failing_obstacle() {
    shove_failing_obstacle = null;
    shove_failing_layer = -1;
  }

  /**
   * Checks if the auto-route database is being maintained outside the auto-route
   * algorithm.
   *
   * <p>
   * When the autoroute database is maintained, it is kept synchronized with board
   * changes
   * even when routing is not actively occurring. This allows faster routing
   * operations at the
   * cost of memory and update overhead.
   *
   * @return true if the autoroute engine exists and is maintaining its database,
   *         false if no engine is active
   *
   * @see #set_maintaining_autoroute_database(boolean)
   */
  boolean is_maintaining_autoroute_database() {
    return this.autoroute_engine != null;
  }

  /**
   * Sets whether the auto-route database should be maintained outside the
   * auto-route algorithm.
   *
   * <p>
   * When set to true, this method enables database maintenance, though the actual
   * engine
   * creation happens during init_autoroute. When set to false, the autoroute
   * engine is
   * immediately cleared to free memory.
   *
   * <p>
   * Maintaining the database improves routing performance but increases memory
   * usage and
   * adds overhead to board modification operations.
   *
   * @param p_value true to enable database maintenance, false to disable and
   *                clear the engine
   *
   * @see #is_maintaining_autoroute_database()
   * @see #init_autoroute(int, int, Stoppable, TimeLimit, boolean)
   */
  void set_maintaining_autoroute_database(boolean p_value) {
    if (p_value) {

    } else {
      this.autoroute_engine = null;
    }
  }

  /**
   * Generates comprehensive statistics about the current board state.
   *
   * <p>
   * This method creates a BoardStatistics object that contains detailed
   * information about
   * the board including counts of components, traces, vias, unrouted connections,
   * violations,
   * and other metrics useful for evaluating board quality and routing progress.
   *
   * @return a new BoardStatistics object containing current board metrics
   *
   * @see BoardStatistics
   */
  public BoardStatistics get_statistics() {
    return new BoardStatistics(this);
  }

  /**
   * Creates a deep copy of the routing board including all items and routing
   * state.
   *
   * <p>
   * This method uses Java serialization to create a complete independent copy of
   * the board.
   * The copy includes all items, nets, rules, and board geometry. The autoroute
   * engine and
   * temporary autoroute data are explicitly cleared in the copy to ensure a clean
   * state.
   *
   * <p>
   * This is particularly useful for:
   * <ul>
   * <li>Implementing undo/redo functionality</li>
   * <li>Comparing different routing solutions</li>
   * <li>Preserving board state before experimental operations</li>
   * <li>Testing routing algorithms without affecting the original board</li>
   * </ul>
   *
   * <p>
   * Note: This operation can be memory and CPU intensive for large boards due to
   * the
   * serialization overhead.
   *
   * @return a complete independent copy of this routing board, or null if the
   *         copy operation fails
   */
  public synchronized RoutingBoard deepCopy() {
    ObjectOutputStream oos = null;
    ObjectInputStream ois = null;

    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(bos);

      oos.writeObject(this); // serialize this.board
      oos.flush();

      ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
      ois = new ObjectInputStream(bin);

      RoutingBoard board_copy = (RoutingBoard) ois.readObject();

      // board_copy.clear_autoroute_database();
      board_copy.clear_all_item_temporary_autoroute_data();
      board_copy.finish_autoroute();

      return board_copy;
    } catch (Exception e) {
      FRLogger.error("Exception in deep_copy_routing_board" + e, e);
      return null;
    } finally {
      try {
        if (oos != null) {
          oos.close();
        }
        if (ois != null) {
          ois.close();
        }
      } catch (Exception _) {
      }
    }
  }
}
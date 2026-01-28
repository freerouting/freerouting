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
import java.util.SortedSet;

/**
 * Abstract algorithm for locating and constructing the physical routing path from a found connection.
 *
 * <p>After the maze search algorithm finds a connection between two items, this class converts
 * that abstract connection (represented as a chain of expansion rooms and doors) into concrete
 * routing geometry (traces and vias) that can be inserted into the board.
 *
 * <p><strong>Key Responsibilities:</strong>
 * <ul>
 *   <li><strong>Backtracking:</strong> Trace the path from destination back to source through expansion rooms</li>
 *   <li><strong>Corner Calculation:</strong> Determine trace corner points respecting angle restrictions</li>
 *   <li><strong>Via Placement:</strong> Identify layer changes and create via locations</li>
 *   <li><strong>Geometry Construction:</strong> Build polyline traces with proper corners and connections</li>
 *   <li><strong>Connection Safety:</strong> Ensure traces properly connect to pads and maintain clearances</li>
 * </ul>
 *
 * <p><strong>Algorithm Flow:</strong>
 * <ol>
 *   <li><strong>Backtracking:</strong> Walk backwards from destination to source collecting rooms/doors</li>
 *   <li><strong>Segmentation:</strong> Divide path into trace segments (between vias/endpoints)</li>
 *   <li><strong>Corner Generation:</strong> Calculate trace corners for each segment</li>
 *   <li><strong>Angle Compliance:</strong> Adjust corners to meet angle restrictions</li>
 *   <li><strong>Rounding:</strong> Convert floating-point corners to integer board coordinates</li>
 *   <li><strong>Item Creation:</strong> Package segments into {@link ResultItem}s for insertion</li>
 * </ol>
 *
 * <p><strong>Angle Restriction Support:</strong>
 * <ul>
 *   <li><strong>90° (Orthogonal):</strong> {@link LocateFoundConnectionAlgo45Degree} with orthogonal-only routing</li>
 *   <li><strong>45° (Octilinear):</strong> {@link LocateFoundConnectionAlgo45Degree} with 45° diagonals</li>
 *   <li><strong>Any Angle:</strong> {@link LocateFoundConnectionAlgoAnyAngle} for unrestricted routing</li>
 * </ul>
 *
 * <p><strong>Connection Points:</strong>
 * The algorithm handles various connection types:
 * <ul>
 *   <li><strong>Pads:</strong> Connect to pad center or optimal point</li>
 *   <li><strong>Traces:</strong> Connect anywhere along existing trace</li>
 *   <li><strong>Vias:</strong> Layer transitions using drill locations</li>
 *   <li><strong>Areas:</strong> Connect to conduction area boundaries (with shrinking for safety)</li>
 * </ul>
 *
 * <p><strong>Corner Adjustment:</strong>
 * Corners are adjusted to:
 * <ul>
 *   <li>Stay within expansion room boundaries (considering trace width)</li>
 *   <li>Respect angle restrictions (90°, 45°, or any angle)</li>
 *   <li>Avoid creating zero-length segments</li>
 *   <li>Maintain proper clearances from obstacles</li>
 * </ul>
 *
 * <p><strong>Rip-up Tracking:</strong>
 * During backtracking, any items that were "ripped up" (temporarily removed) to make
 * the connection possible are collected in the ripped item list for potential restoration
 * if routing ultimately fails.
 *
 * <p><strong>Factory Pattern:</strong>
 * Use {@link #get_instance} to create the appropriate subclass based on angle restrictions.
 *
 * @see MazeSearchAlgo
 * @see AutorouteEngine
 * @see LocateFoundConnectionAlgo45Degree
 * @see LocateFoundConnectionAlgoAnyAngle
 * @see InsertFoundConnectionAlgo
 */
public abstract class LocateFoundConnectionAlgo {

  /**
   * Collection of result items representing the physical traces and vias to be inserted.
   *
   * <p>Each {@link ResultItem} contains:
   * <ul>
   *   <li>Array of corner points (in integer board coordinates)</li>
   *   <li>Layer number for the trace</li>
   * </ul>
   *
   * <p>Items are ordered from destination to source, matching the backtracking direction.
   *
   * @see ResultItem
   */
  public final Collection<ResultItem> connection_items;

  /**
   * The start item of the new routed connection (source point).
   *
   * <p>This is typically a pad, via, or existing trace that forms one endpoint
   * of the connection. The routing path begins here after all backtracking is complete.
   */
  public final Item start_item;

  /**
   * The layer of the connection to the start item.
   *
   * <p>Indicates which layer the first trace segment connects to the start item on.
   * Important for via placement and trace continuity.
   */
  public final int start_layer;

  /**
   * The destination item of the new routed connection (target point).
   *
   * <p>This is the item found by the maze search algorithm - typically a pad, via,
   * or existing trace on the same net. The routing path ends here.
   *
   * <p>May be null in fanout scenarios where the destination is an expansion drill
   * rather than a board item.
   */
  public final Item target_item;

  /**
   * The layer of the connection to the target item.
   *
   * <p>Indicates which layer the final trace segment connects to the target item on.
   */
  public final int target_layer;

  /**
   * Array of backtrack elements tracing the path from destination to start.
   *
   * <p>Each {@link BacktrackElement} contains:
   * <ul>
   *   <li>The door (expansion door or drill) at this step</li>
   *   <li>Section number of the door</li>
   *   <li>The next room in the backtrack sequence</li>
   * </ul>
   *
   * <p>Array is ordered from destination (index 0) to source (index length-1).
   * Built by traversing the maze search result's backtrack links.
   *
   * @see BacktrackElement
   */
  protected final BacktrackElement[] backtrack_array;

  /**
   * Autoroute control providing routing parameters and settings.
   *
   * <p>Used to access:
   * <ul>
   *   <li>Compensated trace half-widths per layer</li>
   *   <li>Routing strategy settings</li>
   *   <li>Clearance requirements</li>
   * </ul>
   *
   * @see AutorouteControl
   */
  protected final AutorouteControl ctrl;

  /**
   * Angle restriction mode determining valid trace angles.
   *
   * <p>Possible values:
   * <ul>
   *   <li>{@link AngleRestriction#NINETY_DEGREE}: Only horizontal/vertical traces</li>
   *   <li>{@link AngleRestriction#FORTYFIVE_DEGREE}: Horizontal, vertical, and 45° diagonals</li>
   *   <li>{@link AngleRestriction#NONE}: Any angle allowed</li>
   * </ul>
   */
  protected final AngleRestriction angle_restriction;

  /**
   * The expansion door connecting to the start item.
   *
   * <p>This is the final door in the backtrack array, representing the connection
   * point to the source item. Contains information about:
   * <ul>
   *   <li>The start item reference</li>
   *   <li>The expansion room at the start</li>
   *   <li>Tree entry number for connection shape lookup</li>
   * </ul>
   */
  protected final TargetItemExpansionDoor start_door;

  /**
   * Current position during trace construction (floating-point for precision).
   *
   * <p>Updated as corners are calculated and added to the current trace segment.
   * Maintains sub-grid precision during calculations, rounded to integer at the end.
   */
  protected FloatPoint current_from_point;

  /**
   * Previous position during trace construction.
   *
   * <p>Used to maintain direction continuity and calculate appropriate corner angles
   * when angle restrictions apply.
   */
  protected FloatPoint previous_from_point;

  /**
   * Current layer being traced on.
   *
   * <p>Updated when processing layer changes (vias). Starts at the target layer
   * and progresses toward the start layer.
   */
  protected int current_trace_layer;

  /**
   * Index in backtrack array of the current "from" door.
   *
   * <p>Marks where we are in the backtrack sequence as we construct trace segments.
   */
  protected int current_from_door_index;

  /**
   * Index in backtrack array of the current "to" door.
   *
   * <p>Used during corner calculation to determine the bounds of the current
   * expansion room being traced through.
   */
  protected int current_to_door_index;

  /**
   * Index in backtrack array of the target door for the current trace segment.
   *
   * <p>Points to the drill (for vias) or final target that this trace segment
   * is heading toward.
   */
  protected int current_target_door_index;

  /**
   * Shape to connect to at the current target (drill location or item connection shape).
   *
   * <p>For vias: a point shape at the drill location.
   * For items: the connection shape (possibly shrunk for safety with conduction areas).
   */
  protected TileShape current_target_shape;

  /**
   * Creates a new connection locator and constructs all trace segments for the found path.
   *
   * <p><strong>Construction Process:</strong>
   * <ol>
   *   <li><strong>Backtracking:</strong> Trace path from destination to source through expansion rooms</li>
   *   <li><strong>Initialize Endpoints:</strong> Identify start and target items, layers, and connection points</li>
   *   <li><strong>Segment Construction:</strong> Build trace segments iteratively:
   *     <ul>
   *       <li>Determine target (via drill or final connection)</li>
   *       <li>Calculate corner points with angle restrictions</li>
   *       <li>Round to integer coordinates</li>
   *       <li>Create ResultItem for insertion</li>
   *     </ul>
   *   </li>
   *   <li><strong>Layer Transitions:</strong> Handle vias at expansion drills</li>
   * </ol>
   *
   * <p><strong>Backtrack Array Structure:</strong>
   * Index 0 = destination, Index [length-1] = source
   * <pre>
   * [Dest Item] -> [Room] -> [Drill/Via] -> [Room] -> ... -> [Room] -> [Start Item]
   * </pre>
   *
   * <p><strong>Connection Types:</strong>
   * <ul>
   *   <li><strong>Normal:</strong> Target is a TargetItemExpansionDoor (pad/trace/via)</li>
   *   <li><strong>Fanout:</strong> Target is an ExpansionDrill (via fanout endpoint)</li>
   * </ul>
   *
   * <p><strong>Area Shrinking:</strong>
   * When connecting to conduction areas (dimension ≥ 2), the target shape is shrunk
   * by the trace half-width to ensure a safe, well-connected contact avoiding DRC violations.
   *
   * <p><strong>Ripped Items:</strong>
   * Any items that were temporarily removed (ripped up) during maze search to enable
   * the connection are added to {@code p_ripped_item_list} for potential restoration.
   *
   * <p><strong>Error Handling:</strong>
   * If start or destination doors are not of expected types, logs warnings and
   * initializes fields to null/zero values. The connection_items collection will be empty.
   *
   * @param p_maze_search_result the maze search result containing the found path
   * @param p_ctrl autoroute control with routing parameters
   * @param p_search_tree shape search tree for connection shape lookups
   * @param p_angle_restriction angle restriction mode for trace routing
   * @param p_ripped_item_list output collection for items that were ripped up
   *
   * @see #backtrack
   * @see #calculate_next_trace
   * @see ResultItem
   */
  protected LocateFoundConnectionAlgo(MazeSearchAlgo.Result p_maze_search_result, AutorouteControl p_ctrl, ShapeSearchTree p_search_tree, AngleRestriction p_angle_restriction,
      SortedSet<Item> p_ripped_item_list) {
    this.ctrl = p_ctrl;
    this.angle_restriction = p_angle_restriction;
    Collection<BacktrackElement> backtrack_list = backtrack(p_maze_search_result, p_ripped_item_list);
    this.backtrack_array = new BacktrackElement[backtrack_list.size()];
    Iterator<BacktrackElement> it = backtrack_list.iterator();
    for (int i = 0; i < backtrack_array.length; i++) {
      this.backtrack_array[i] = it.next();
    }
    this.connection_items = new LinkedList<>();
    BacktrackElement start_info = this.backtrack_array[backtrack_array.length - 1];
    if (!(start_info.door instanceof TargetItemExpansionDoor)) {
      FRLogger.warn("LocateFoundConnectionAlgo: ItemExpansionDoor expected for start_info.door");
      this.start_item = null;
      this.start_layer = 0;
      this.target_item = null;
      this.target_layer = 0;
      this.start_door = null;
      return;
    }
    this.start_door = (TargetItemExpansionDoor) start_info.door;
    this.start_item = start_door.item;
    this.start_layer = start_door.room.get_layer();

    this.current_from_door_index = 0;
    boolean at_fanout_end = false;
    if (p_maze_search_result.destination_door instanceof TargetItemExpansionDoor curr_destination_door) {
      this.target_item = curr_destination_door.item;
      this.target_layer = curr_destination_door.room.get_layer();

      this.current_from_point = calculate_starting_point(curr_destination_door, p_search_tree);
    } else if (p_maze_search_result.destination_door instanceof ExpansionDrill curr_drill) {
      // may happen only in case of fanout
      this.target_item = null;
      this.current_from_point = curr_drill.location.to_float();
      this.target_layer = curr_drill.first_layer + p_maze_search_result.section_no_of_door;
      at_fanout_end = true;
    } else {
      FRLogger.warn("LocateFoundConnectionAlgo: unexpected type of destination_door");
      this.target_item = null;
      this.target_layer = 0;
      return;
    }
    this.current_trace_layer = this.target_layer;
    this.previous_from_point = this.current_from_point;

    boolean connection_done = false;
    while (!connection_done) {
      boolean layer_changed = false;
      if (at_fanout_end) {
        // do not increase this.current_target_door_index
        layer_changed = true;
      } else {
        this.current_target_door_index = this.current_from_door_index + 1;
        while (current_target_door_index < this.backtrack_array.length && !layer_changed) {
          if (this.backtrack_array[this.current_target_door_index].door instanceof ExpansionDrill) {
            layer_changed = true;
          } else {
            ++this.current_target_door_index;
          }
        }
      }
      if (layer_changed) {
        // the next trace leads to a via
        ExpansionDrill current_target_drill = (ExpansionDrill) this.backtrack_array[this.current_target_door_index].door;
        this.current_target_shape = TileShape.get_instance(current_target_drill.location);
      } else {
        // the next trace leads to the final target
        connection_done = true;
        this.current_target_door_index = this.backtrack_array.length - 1;
        TileShape target_shape = ((Connectable) start_item).get_trace_connection_shape(p_search_tree, start_door.tree_entry_no);
        this.current_target_shape = target_shape.intersection(start_door.room.get_shape());
        if (this.current_target_shape.dimension() >= 2) {
          // the target is a conduction area, make a save connection
          // by shrinking the shape by the trace halfwidth.
          double trace_half_width = this.ctrl.compensated_trace_half_width[start_door.room.get_layer()];
          TileShape shrinked_shape = (TileShape) this.current_target_shape.offset(-trace_half_width);
          if (!shrinked_shape.is_empty()) {
            this.current_target_shape = shrinked_shape;
          }
        }
      }
      this.current_to_door_index = this.current_from_door_index + 1;
      ResultItem next_trace = this.calculate_next_trace(layer_changed, at_fanout_end);
      at_fanout_end = false;
      this.connection_items.add(next_trace);
    }
  }

  /**
   * Factory method that creates the appropriate LocateFoundConnectionAlgo subclass instance.
   *
   * <p>Selects the implementation based on angle restrictions:
   * <ul>
   *   <li><strong>90° or 45°:</strong> Returns {@link LocateFoundConnectionAlgo45Degree}
   *     <ul>
   *       <li>Optimized for Manhattan (90°) and octilinear (45°) routing</li>
   *       <li>Uses grid-aligned corner calculation</li>
   *       <li>More predictable trace geometry</li>
   *     </ul>
   *   </li>
   *   <li><strong>Any Angle:</strong> Returns {@link LocateFoundConnectionAlgoAnyAngle}
   *     <ul>
   *       <li>Supports arbitrary trace angles</li>
   *       <li>More direct paths, potentially shorter routes</li>
   *       <li>May be harder to manufacture</li>
   *     </ul>
   *   </li>
   * </ul>
   *
   * <p><strong>Null Handling:</strong>
   * Returns null if {@code p_maze_search_result} is null (no connection found).
   *
   * @param p_maze_search_result the maze search result, or null if search failed
   * @param p_ctrl autoroute control with routing parameters
   * @param p_search_tree shape search tree for connection shape lookups
   * @param p_angle_restriction angle restriction mode for trace routing
   * @param p_ripped_item_list output collection for items that were ripped up
   * @return appropriate subclass instance, or null if no connection found
   *
   * @see LocateFoundConnectionAlgo45Degree
   * @see LocateFoundConnectionAlgoAnyAngle
   * @see AngleRestriction
   */
  public static LocateFoundConnectionAlgo get_instance(MazeSearchAlgo.Result p_maze_search_result, AutorouteControl p_ctrl, ShapeSearchTree p_search_tree, AngleRestriction p_angle_restriction,
      SortedSet<Item> p_ripped_item_list) {
    if (p_maze_search_result == null) {
      return null;
    }
    LocateFoundConnectionAlgo result;
    if (p_angle_restriction == AngleRestriction.NINETY_DEGREE || p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result = new LocateFoundConnectionAlgo45Degree(p_maze_search_result, p_ctrl, p_search_tree, p_angle_restriction, p_ripped_item_list);
    } else {
      result = new LocateFoundConnectionAlgoAnyAngle(p_maze_search_result, p_ctrl, p_search_tree, p_angle_restriction, p_ripped_item_list);
    }
    return result;
  }

  /**
   * Calculates the optimal starting point for the next trace on the target item.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Get the trace connection shape from the item (depends on item type)</li>
   *   <li>Intersect with the expansion room shape (constrain to reachable area)</li>
   *   <li>Calculate center of gravity of the intersection</li>
   *   <li>Round to nearest integer point for grid alignment</li>
   * </ol>
   *
   * <p><strong>Connection Shape Types:</strong>
   * <ul>
   *   <li><strong>Pads:</strong> Usually the pad center or copper shape</li>
   *   <li><strong>Traces:</strong> The trace polyline or a segment</li>
   *   <li><strong>Vias:</strong> The via drill location</li>
   *   <li><strong>Areas:</strong> The conduction area boundary</li>
   * </ul>
   *
   * <p><strong>Note:</strong> The implementation may not be optimal for traces or areas.
   * Currently uses center of gravity which may not always be the best connection point
   * (e.g., nearest point on trace would be better).
   *
   * <p><strong>TODO:</strong> Optimize for starting points on traces and conduction areas.
   *
   * @param p_from_door the target item expansion door to start from
   * @param p_search_tree shape search tree for connection shape lookups
   * @return the calculated starting point in floating-point coordinates
   *
   * @see Connectable#get_trace_connection_shape
   */
  private static FloatPoint calculate_starting_point(TargetItemExpansionDoor p_from_door, ShapeSearchTree p_search_tree) {
    TileShape connection_shape = ((Connectable) p_from_door.item).get_trace_connection_shape(p_search_tree, p_from_door.tree_entry_no);
    connection_shape = connection_shape.intersection(p_from_door.room.get_shape());
    return connection_shape.centre_of_gravity().round().to_float();
  }

  /**
   * Traces the routing path backwards from destination to source, collecting all doors and rooms.
   *
   * <p><strong>Backtracking Process:</strong>
   * <ol>
   *   <li>Start at the destination door (where maze search ended)</li>
   *   <li>Follow backtrack_door links through maze search elements</li>
   *   <li>For each door, create a BacktrackElement with:
   *     <ul>
   *       <li>The door itself (expansion door or drill)</li>
   *       <li>Section number of the door</li>
   *       <li>The next room in the sequence</li>
   *     </ul>
   *   </li>
   *   <li>Continue until reaching the start (backtrack_door is null)</li>
   * </ol>
   *
   * <p><strong>Door Types:</strong>
   * <ul>
   *   <li><strong>TargetItemExpansionDoor:</strong> Connection to a board item (pad/trace/via)</li>
   *   <li><strong>ExpansionDrill:</strong> Via drill location for layer changes</li>
   * </ul>
   *
   * <p><strong>Ripped Item Collection:</strong>
   * During backtracking, identifies rooms that were "ripped" (obstacles temporarily removed):
   * <ul>
   *   <li>Checks maze search element's room_ripped flag</li>
   *   <li>If room is an ObstacleExpansionRoom, adds its item to ripped list</li>
   *   <li>Special handling for drills: checks all rooms in drill array</li>
   * </ul>
   *
   * <p><strong>Section Numbers:</strong>
   * For ExpansionDrills spanning multiple layers, section_no indicates which layer
   * the connection uses (0 = first_layer, 1 = first_layer+1, etc.).
   *
   * <p><strong>Result Ordering:</strong>
   * The returned list is ordered from destination (first) to source (last),
   * opposite to the routing direction but convenient for trace construction.
   *
   * @param p_maze_search_result the maze search result containing the destination door
   * @param p_ripped_item_list output collection for items that were ripped up
   * @return list of backtrack elements from destination to source, or null if result is null
   *
   * @see BacktrackElement
   * @see MazeSearchElement#backtrack_door
   * @see MazeSearchElement#room_ripped
   */
  private static Collection<BacktrackElement> backtrack(MazeSearchAlgo.Result p_maze_search_result, SortedSet<Item> p_ripped_item_list) {
    if (p_maze_search_result == null) {
      return null;
    }
    Collection<BacktrackElement> result = new LinkedList<>();
    CompleteExpansionRoom curr_next_room = null;
    ExpandableObject curr_backtrack_door = p_maze_search_result.destination_door;
    MazeSearchElement curr_maze_search_element = curr_backtrack_door.get_maze_search_element(p_maze_search_result.section_no_of_door);
    if (curr_backtrack_door instanceof TargetItemExpansionDoor door) {
      curr_next_room = door.room;
    } else if (curr_backtrack_door instanceof ExpansionDrill curr_drill) {
      curr_next_room = curr_drill.room_arr[curr_drill.first_layer + p_maze_search_result.section_no_of_door];
      if (curr_maze_search_element.room_ripped) {
        for (CompleteExpansionRoom tmp_room : curr_drill.room_arr) {
          if (tmp_room instanceof ObstacleExpansionRoom room) {
            p_ripped_item_list.add(room.get_item());
          }
        }
      }
    }
    BacktrackElement curr_backtrack_element = new BacktrackElement(curr_backtrack_door, p_maze_search_result.section_no_of_door, curr_next_room);
    for (; ; ) {
      result.add(curr_backtrack_element);
      curr_backtrack_door = curr_maze_search_element.backtrack_door;
      if (curr_backtrack_door == null) {
        break;
      }
      int curr_section_no = curr_maze_search_element.section_no_of_backtrack_door;
      if (curr_section_no >= curr_backtrack_door.maze_search_element_count()) {
        FRLogger.warn("LocateFoundConnectionAlgo: curr_section_no to big");
        curr_section_no = curr_backtrack_door.maze_search_element_count() - 1;
      }
      if (curr_backtrack_door instanceof ExpansionDrill curr_drill) {
        curr_next_room = curr_drill.room_arr[curr_section_no];
      } else {
        curr_next_room = curr_backtrack_door.other_room(curr_next_room);
      }
      curr_maze_search_element = curr_backtrack_door.get_maze_search_element(curr_section_no);
      curr_backtrack_element = new BacktrackElement(curr_backtrack_door, curr_section_no, curr_next_room);
      if (curr_maze_search_element.room_ripped) {
        if (curr_next_room instanceof ObstacleExpansionRoom room) {
          p_ripped_item_list.add(room.get_item());
        }
      }
    }
    return result;
  }

  /**
   * Calculates a 90-degree corner point between two positions.
   *
   * <p>Creates an L-shaped path with one corner at either horizontal-then-vertical
   * or vertical-then-horizontal orientation.
   *
   * <p><strong>Orientation:</strong>
   * <ul>
   *   <li><strong>Horizontal first:</strong> Corner at (to.x, from.y) - go right/left, then up/down</li>
   *   <li><strong>Vertical first:</strong> Corner at (from.x, to.y) - go up/down, then right/left</li>
   * </ul>
   *
   * @param p_from_point starting position
   * @param p_to_point ending position
   * @param p_horizontal_first true for horizontal-first, false for vertical-first
   * @return the corner point creating a 90-degree turn
   */
  private static FloatPoint ninety_degree_corner(FloatPoint p_from_point, FloatPoint p_to_point, boolean p_horizontal_first) {
    double x;
    double y;
    if (p_horizontal_first) {
      x = p_to_point.x;
      y = p_from_point.y;
    } else {
      x = p_from_point.x;
      y = p_to_point.y;
    }
    return new FloatPoint(x, y);
  }

  /**
   * Calculates a 45-degree corner point between two positions for octilinear routing.
   *
   * <p>Creates a path with two segments: one axis-aligned (horizontal or vertical) and
   * one at 45 degrees. The corner connects these segments based on the distance ratios
   * and the specified orientation preference.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Calculate absolute distances: abs_dx and abs_dy</li>
   *   <li>Determine dominant direction (dx vs dy)</li>
   *   <li>Calculate corner to create one axis-aligned segment and one 45° segment</li>
   *   <li>Respect orientation preference (horizontal-first or vertical-first)</li>
   * </ol>
   *
   * <p><strong>Examples:</strong>
   * <ul>
   *   <li>If abs_dy > abs_dx and horizontal_first: horizontal segment then 45° diagonal</li>
   *   <li>If abs_dx > abs_dy and vertical_first: vertical segment then 45° diagonal</li>
   * </ul>
   *
   * @param p_from_point starting position
   * @param p_to_point ending position
   * @param p_horizontal_first true for horizontal preference, false for vertical
   * @return the corner point creating a 45-degree turn
   */
  private static FloatPoint fortyfive_degree_corner(FloatPoint p_from_point, FloatPoint p_to_point, boolean p_horizontal_first) {
    double abs_dx = Math.abs(p_to_point.x - p_from_point.x);
    double abs_dy = Math.abs(p_to_point.y - p_from_point.y);
    double x;
    double y;

    if (abs_dx <= abs_dy) {
      if (p_horizontal_first) {
        x = p_to_point.x;
        if (p_to_point.y >= p_from_point.y) {
          y = p_from_point.y + abs_dx;
        } else {
          y = p_from_point.y - abs_dx;
        }
      } else {
        x = p_from_point.x;
        if (p_to_point.y > p_from_point.y) {
          y = p_to_point.y - abs_dx;
        } else {
          y = p_to_point.y + abs_dx;
        }
      }
    } else {
      if (p_horizontal_first) {
        y = p_from_point.y;
        if (p_to_point.x > p_from_point.x) {
          x = p_to_point.x - abs_dy;
        } else {
          x = p_to_point.x + abs_dy;
        }
      } else {
        y = p_to_point.y;
        if (p_to_point.x > p_from_point.x) {
          x = p_from_point.x + abs_dy;
        } else {
          x = p_from_point.x - abs_dy;
        }
      }
    }
    return new FloatPoint(x, y);
  }

  /**
   * Calculates an additional corner point to satisfy angle restrictions between two positions.
   *
   * <p>This method inserts intermediate corners to ensure trace segments comply with
   * the specified angle restriction (90°, 45°, or none).
   *
   * <p><strong>Angle Restriction Handling:</strong>
   * <ul>
   *   <li><strong>NINETY_DEGREE:</strong> Creates L-shaped paths (one horizontal, one vertical segment)</li>
   *   <li><strong>FORTYFIVE_DEGREE:</strong> Creates octilinear paths (axis-aligned + 45° diagonal)</li>
   *   <li><strong>NONE:</strong> Returns target point directly (no corner needed)</li>
   * </ul>
   *
   * <p><strong>Orientation Preference:</strong>
   * The {@code p_horizontal_first} parameter controls which direction is preferred for
   * the first segment when a corner is needed.
   *
   * @param p_from_point starting position
   * @param p_to_point ending position
   * @param p_horizontal_first true to prefer horizontal segment first, false for vertical
   * @param p_angle_restriction the angle restriction mode to apply
   * @return the calculated corner point, or p_to_point if no corner needed
   *
   * @see #ninety_degree_corner
   * @see #fortyfive_degree_corner
   */
  static FloatPoint calculate_additional_corner(FloatPoint p_from_point, FloatPoint p_to_point, boolean p_horizontal_first, AngleRestriction p_angle_restriction) {
    FloatPoint result;
    if (p_angle_restriction == AngleRestriction.NINETY_DEGREE) {
      result = ninety_degree_corner(p_from_point, p_to_point, p_horizontal_first);
    } else if (p_angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result = fortyfive_degree_corner(p_from_point, p_to_point, p_horizontal_first);
    } else {
      result = p_to_point;
    }
    return result;
  }

  /**
   * Calculates and constructs the next trace segment of the connection.
   *
   * <p><strong>Trace Construction Process:</strong>
   * <ol>
   *   <li><strong>Initialize:</strong> Start with current_from_point as first corner</li>
   *   <li><strong>Adjust Start:</strong> Ensure start corner is within room bounds (considering trace width)</li>
   *   <li><strong>Add Corners:</strong> Iteratively calculate corners through expansion rooms</li>
   *   <li><strong>Layer Transition:</strong> Update layer if this segment ends at a via</li>
   *   <li><strong>Round Corners:</strong> Convert floating-point to integer coordinates</li>
   *   <li><strong>Create Result:</strong> Package into ResultItem for insertion</li>
   * </ol>
   *
   * <p><strong>Corner Adjustment:</strong>
   * Corners may need adjustment to:
   * <ul>
   *   <li>Stay within expansion room boundaries</li>
   *   <li>Comply with angle restrictions</li>
   *   <li>Avoid zero-length segments</li>
   * </ul>
   *
   * <p><strong>Layer Changes:</strong>
   * If {@code p_layer_changed} is true, this trace segment ends at a via:
   * <ul>
   *   <li>Updates current_from_door_index to skip past the drill</li>
   *   <li>Updates current_trace_layer to the next layer</li>
   * </ul>
   *
   * <p><strong>Fanout Handling:</strong>
   * The {@code p_at_fanout_end} parameter indicates this is the first segment from
   * a fanout drill (no adjustment needed).
   *
   * @param p_layer_changed true if this segment ends at a via (layer transition)
   * @param p_at_fanout_end true if starting from a fanout drill endpoint
   * @return the constructed trace segment as a ResultItem
   *
   * @see ResultItem
   * @see #calculate_next_trace_corners
   * @see #adjust_start_corner
   */
  private ResultItem calculate_next_trace(boolean p_layer_changed, boolean p_at_fanout_end) {
    Collection<FloatPoint> corner_list = new LinkedList<>();
    corner_list.add(this.current_from_point);
    if (!p_at_fanout_end) {
      FloatPoint adjusted_start_corner = this.adjust_start_corner();
      if (adjusted_start_corner != this.current_from_point) {
        FloatPoint add_corner = calculate_additional_corner(this.current_from_point, adjusted_start_corner, true, this.angle_restriction);
        corner_list.add(add_corner);
        corner_list.add(adjusted_start_corner);
        this.previous_from_point = this.current_from_point;
        this.current_from_point = adjusted_start_corner;
      }
    }
    FloatPoint prev_corner = this.current_from_point;
    for (; ; ) {
      Collection<FloatPoint> next_corners = calculate_next_trace_corners();
      if (next_corners.isEmpty()) {
        break;
      }
      for (FloatPoint curr_next_corner : next_corners) {
        if (curr_next_corner != prev_corner) {
          corner_list.add(curr_next_corner);
          this.previous_from_point = this.current_from_point;
          this.current_from_point = curr_next_corner;
          prev_corner = curr_next_corner;
        }
      }
    }

    int next_layer = this.current_trace_layer;
    if (p_layer_changed) {
      this.current_from_door_index = this.current_target_door_index + 1;
      CompleteExpansionRoom next_room = this.backtrack_array[this.current_from_door_index].next_room;
      if (next_room != null) {
        next_layer = next_room.get_layer();
      }
    }

    // Round the new trace corners to Integer.
    Collection<IntPoint> rounded_corner_list = new LinkedList<>();
    IntPoint prev_point = null;
    for (FloatPoint corner : corner_list) {
      IntPoint curr_point = corner.round();
      if (!curr_point.equals(prev_point)) {
        rounded_corner_list.add(curr_point);
        prev_point = curr_point;
      }
    }

    // Construct the result item
    IntPoint[] corner_arr = new IntPoint[rounded_corner_list.size()];
    Iterator<IntPoint> it2 = rounded_corner_list.iterator();
    for (int i = 0; i < corner_arr.length; i++) {
      corner_arr[i] = it2.next();
    }
    ResultItem result = new ResultItem(corner_arr, this.current_trace_layer);
    this.current_trace_layer = next_layer;
    return result;
  }

  /**
   * Calculates the next list of corners for the current trace segment under construction.
   *
   * <p>This abstract method is implemented differently by subclasses based on angle restrictions:
   * <ul>
   *   <li><strong>{@link LocateFoundConnectionAlgo45Degree}:</strong> Generates corners for
   *       90° or 45° routing with grid alignment</li>
   *   <li><strong>{@link LocateFoundConnectionAlgoAnyAngle}:</strong> Generates corners for
   *       unrestricted angle routing</li>
   * </ul>
   *
   * <p><strong>Behavior:</strong>
   * <ul>
   *   <li>Returns collection of corner points to add to current trace</li>
   *   <li>Empty collection indicates trace segment is complete</li>
   *   <li>Updates {@link #current_from_point} and {@link #current_to_door_index} internally</li>
   * </ul>
   *
   * <p>Called repeatedly by {@link #calculate_next_trace} until all corners are generated
   * for the current segment (returns empty collection).
   *
   * @return collection of next corner points, or empty if trace is complete
   *
   * @see #calculate_next_trace
   */
  protected abstract Collection<FloatPoint> calculate_next_trace_corners();

  /**
   * Renders the backtrack rooms and doors for debugging and visualization.
   *
   * <p>Displays:
   * <ul>
   *   <li><strong>Expansion Rooms:</strong> Drawn with 20% intensity showing the free space areas</li>
   *   <li><strong>Expansion Drills:</strong> Drawn as points showing via locations</li>
   * </ul>
   *
   * <p>Useful for:
   * <ul>
   *   <li>Debugging routing path issues</li>
   *   <li>Understanding maze search results</li>
   *   <li>Visualizing expansion room geometry</li>
   *   <li>Verifying backtrack sequence correctness</li>
   * </ul>
   *
   * @param p_graphics the graphics context for rendering
   * @param p_graphics_context the board graphics context with rendering settings
   *
   * @see CompleteExpansionRoom#draw
   * @see ExpansionDrill#draw
   */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context) {
    for (int i = 0; i < backtrack_array.length; i++) {
      CompleteExpansionRoom next_room = backtrack_array[i].next_room;
      if (next_room != null) {
        next_room.draw(p_graphics, p_graphics_context, 0.2);
      }
      ExpandableObject next_door = backtrack_array[i].door;
      if (next_door instanceof ExpansionDrill drill) {
        drill.draw(p_graphics, p_graphics_context, 0.2);
      }
    }
  }

  /**
   * Adjusts the start corner to ensure the trace stays within the start room boundaries.
   *
   * <p><strong>Adjustment Process:</strong>
   * <ol>
   *   <li>Get the current expansion room shape</li>
   *   <li>Shrink room by trace half-width to account for trace thickness</li>
   *   <li>Check if current_from_point is inside the shrunk shape</li>
   *   <li>If outside, find nearest point on shrunk shape boundary</li>
   *   <li>Round to integer and convert back to float</li>
   * </ol>
   *
   * <p><strong>Rationale:</strong>
   * A trace starting at {@code current_from_point} with width {@code trace_half_width}
   * must have its centerline inside the shrunk room to ensure the entire trace width
   * fits within the free space area.
   *
   * <p><strong>Edge Cases:</strong>
   * <ul>
   *   <li>If shrunk shape is empty (room too small): returns original point unchanged</li>
   *   <li>If point already inside: returns original point unchanged</li>
   *   <li>If no current room: returns original point unchanged</li>
   * </ul>
   *
   * @return the adjusted start corner, or original point if no adjustment needed
   *
   * @see TileShape#offset
   * @see TileShape#nearest_point_approx
   */
  private FloatPoint adjust_start_corner() {
    if (this.current_from_door_index < 0) {
      return this.current_from_point;
    }
    BacktrackElement curr_from_info = this.backtrack_array[this.current_from_door_index];
    if (curr_from_info.next_room == null) {
      return this.current_from_point;
    }
    double trace_half_width = this.ctrl.compensated_trace_half_width[this.current_trace_layer];
    TileShape shrinked_room_shape = (TileShape) curr_from_info.next_room.get_shape().offset(-trace_half_width);
    if (shrinked_room_shape.is_empty() || shrinked_room_shape.contains(this.current_from_point)) {
      return this.current_from_point;
    }
    return shrinked_room_shape.nearest_point_approx(this.current_from_point).round().to_float();
  }

  /**
   * Container for a single trace segment in the routing result.
   *
   * <p>Represents one physical trace (polyline) to be inserted into the board.
   * Used to create a {@link app.freerouting.board.PolylineTrace} during insertion.
   *
   * <p><strong>Contents:</strong>
   * <ul>
   *   <li><strong>corners:</strong> Array of corner points defining the trace polyline</li>
   *   <li><strong>layer:</strong> The board layer this trace is routed on</li>
   * </ul>
   *
   * <p><strong>Usage:</strong>
   * After all ResultItems are calculated, {@link InsertFoundConnectionAlgo} uses them
   * to create actual board items.
   *
   * @see InsertFoundConnectionAlgo
   */
  protected static class ResultItem {

    /**
     * Array of corner points defining the trace polyline geometry.
     *
     * <p>Points are in integer board coordinates (rounded from floating-point calculations).
     * Must have at least 2 points to form a valid trace.
     */
    public final IntPoint[] corners;

    /**
     * The board layer number this trace is routed on.
     *
     * <p>Layer numbering starts at 0 for the first layer.
     */
    public final int layer;

    /**
     * Creates a new result item representing a trace segment.
     *
     * @param p_corners array of corner points (at least 2)
     * @param p_layer the layer number for this trace
     */
    public ResultItem(IntPoint[] p_corners, int p_layer) {
      corners = p_corners;
      layer = p_layer;
    }
  }

  /**
   * Element in the backtrack sequence from destination to source.
   *
   * <p>Each element represents one step in the path found by the maze search algorithm,
   * containing information about the door (connection point) and the adjacent room
   * (free space area).
   *
   * <p><strong>Structure:</strong>
   * <ul>
   *   <li><strong>door:</strong> The expansion door or drill at this step</li>
   *   <li><strong>section_no_of_door:</strong> Which section (layer) of the door is used</li>
   *   <li><strong>next_room:</strong> The expansion room following this door</li>
   * </ul>
   *
   * <p><strong>Door Types:</strong>
   * <ul>
   *   <li>{@link TargetItemExpansionDoor}: Connection to a board item</li>
   *   <li>{@link ExpansionDrill}: Via drill for layer transition</li>
   * </ul>
   *
   * @see #backtrack
   */
  protected static class BacktrackElement {

    /**
     * The door at this step in the backtrack sequence.
     *
     * <p>Can be either a TargetItemExpansionDoor (connecting to an item)
     * or an ExpansionDrill (via location).
     */
    public final ExpandableObject door;

    /**
     * Section number of the door (relevant for multi-layer drills).
     *
     * <p>For ExpansionDrills spanning multiple layers, indicates which layer
     * is used: 0 = first_layer, 1 = first_layer+1, etc.
     */
    public final int section_no_of_door;

    /**
     * The expansion room following this door in the backtrack sequence.
     *
     * <p>Represents the free space area between this door and the next door.
     * Used for corner calculations and trace width checks.
     */
    public final CompleteExpansionRoom next_room;

    /**
     * Creates a new backtrack element.
     *
     * @param p_door the expansion door or drill
     * @param p_section_no_of_door section number for multi-layer doors
     * @param p_room the next room in the sequence
     */
    private BacktrackElement(ExpandableObject p_door, int p_section_no_of_door, CompleteExpansionRoom p_room) {
      door = p_door;
      section_no_of_door = p_section_no_of_door;
      next_room = p_room;
    }
  }
}
package app.freerouting.autoroute;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Connectable;
import app.freerouting.board.FixedState;
import app.freerouting.board.ForcedPadAlgo;
import app.freerouting.board.ForcedViaAlgo;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.board.ShapeSearchTree;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class for auto-routing an incomplete connection via a maze search algorithm.
 */
public class MazeSearchAlgo {

  private static final int ALREADY_RIPPED_COSTS = 1;
  /**
   * The autoroute engine of this expansion algorithm.
   */
  public final AutorouteEngine autoroute_engine;
  final AutorouteControl ctrl;
  /**
   * The queue of expanded elements used in this search algorithm.
   */
  final PriorityQueue<MazeListElement> maze_expansion_list;

  /**
   * Used for calculating of a good lower bound for the distance between a new
   * MazeExpansionElement and the destination set of the expansion.
   */
  final DestinationDistance destination_distance;
  /**
   * The search tree for expanding. It is the tree compensated for the current
   * net.
   */
  private final ShapeSearchTree search_tree;
  private final Random random_generator = new Random();
  /**
   * The destination door found by the expanding algorithm.
   */
  private ExpandableObject destination_door;
  private int section_no_of_destination_door;

  /**
   * Creates a new instance of MazeSearchAlgo
   */
  MazeSearchAlgo(AutorouteEngine p_autoroute_engine, AutorouteControl p_ctrl) {
    autoroute_engine = p_autoroute_engine;
    ctrl = p_ctrl;
    this.search_tree = p_autoroute_engine.autoroute_search_tree;
    maze_expansion_list = new PriorityQueue<>(Comparator.comparingDouble(o -> o.sorting_value));
    destination_distance = new DestinationDistance(ctrl.trace_costs, ctrl.layer_active, ctrl.min_normal_via_cost,
        ctrl.min_cheap_via_cost);
  }

  /**
   * Initializes a new instance of MazeSearchAlgo for searching a connection
   * between p_start_items and p_destination_items. Returns null, if the
   * initialisation failed.
   */
  public static MazeSearchAlgo get_instance(Set<Item> p_start_items, Set<Item> p_destination_items,
      AutorouteEngine p_autoroute_database, AutorouteControl p_ctrl) {
    MazeSearchAlgo new_instance = new MazeSearchAlgo(p_autoroute_database, p_ctrl);
    MazeSearchAlgo result;
    if (new_instance.init(p_start_items, p_destination_items)) {
      result = new_instance;
    } else {
      result = null;
    }
    return result;
  }

  /**
   * Looks for pins with more than 1 nets and reduces shapes of traces of foreign
   * nets, which are already connected to such a pin, so that the pin center is
   * not blocked for connection.
   */
  private static void reduce_trace_shapes_at_tie_pins(Collection<Item> p_item_list, int p_own_net_no,
      ShapeSearchTree p_autoroute_tree) {
    for (Item curr_item : p_item_list) {
      if ((curr_item instanceof Pin curr_tie_pin) && curr_item.net_count() > 1) {
        Collection<Item> pin_contacts = curr_item.get_normal_contacts();
        for (Item curr_contact : pin_contacts) {
          if (!(curr_contact instanceof PolylineTrace) || curr_contact.contains_net(p_own_net_no)) {
            continue;
          }
          p_autoroute_tree.reduce_trace_shape_at_tie_pin(curr_tie_pin, (PolylineTrace) curr_contact);
        }
      }
    }
  }

  /**
   * Return the additional cost factor for ripping the trace, if it is connected
   * to a fanout via or 1, if no fanout via was found.
   */
  private static double calc_fanout_via_ripup_cost_factor(Trace p_trace) {
    final double FANOUT_COST_CONST = 20000;
    Collection<Item> curr_end_contacts;
    for (int i = 0; i < 2; i++) {
      if (i == 0) {
        curr_end_contacts = p_trace.get_start_contacts();
      } else {
        curr_end_contacts = p_trace.get_end_contacts();
      }
      if (curr_end_contacts.size() != 1) {
        continue;
      }
      Item curr_trace_contact = curr_end_contacts
          .iterator()
          .next();
      boolean protect_fanout_via = false;
      if (curr_trace_contact instanceof Pin && curr_trace_contact.first_layer() == curr_trace_contact.last_layer()) {
        protect_fanout_via = true;
      } else if (curr_trace_contact instanceof PolylineTrace contact_trace
          && curr_trace_contact.get_fixed_state() == FixedState.SHOVE_FIXED) {
        // look for shove fixed exit traces of SMD-pins
        if (contact_trace.corner_count() == 2) {
          protect_fanout_via = true;
        }
      }

      if (protect_fanout_via) {
        double fanout_via_cost_factor = p_trace.get_half_width() / p_trace.get_length();
        fanout_via_cost_factor *= fanout_via_cost_factor;
        fanout_via_cost_factor *= FANOUT_COST_CONST;
        fanout_via_cost_factor = Math.max(fanout_via_cost_factor, 1);
        return fanout_via_cost_factor;
      }
    }
    return 1;
  }

  /**
   * Returns the perpendicular projection of p_from_segment onto p_to_segment.
   * Returns null, if the projection is empty.
   */
  private static FloatLine segment_projection(FloatLine p_from_segment, FloatLine p_to_segment) {
    FloatLine check_segment = p_from_segment.adjust_direction(p_to_segment);
    FloatLine first_projection = p_to_segment.segment_projection(check_segment);
    FloatLine second_projection = p_to_segment.segment_projection_2(check_segment);
    FloatLine result;
    if (first_projection != null && second_projection != null) {
      FloatPoint result_a;
      if (first_projection.a == p_to_segment.a || second_projection.a == p_to_segment.a) {
        result_a = p_to_segment.a;
      } else if (first_projection.a.distance_square(p_to_segment.a) <= second_projection.a
          .distance_square(p_to_segment.a)) {
        result_a = first_projection.a;
      } else {
        result_a = second_projection.a;
      }
      FloatPoint result_b;
      if (first_projection.b == p_to_segment.b || second_projection.b == p_to_segment.b) {
        result_b = p_to_segment.b;
      } else if (first_projection.b.distance_square(p_to_segment.b) <= second_projection.b
          .distance_square(p_to_segment.b)) {
        result_b = first_projection.b;
      } else {
        result_b = second_projection.b;
      }
      result = new FloatLine(result_a, result_b);
    } else if (first_projection != null) {
      result = first_projection;
    } else {
      result = second_projection;
    }
    return result;
  }

  /**
   * Does a maze search to find a connection route between the start and the
   * destination items. If the algorithm succeeds, the ExpansionDoor and its
   * section number of the found destination is
   * returned, from where the whole found connection can be backtracked.
   * Otherwise, the return value will be null.
   */
  public Result find_connection() {
    while (occupy_next_element()) {
      continue;
    }
    if (this.destination_door == null) {
      return null;
    }
    return new Result(this.destination_door, this.section_no_of_destination_door);
  }

  /**
   * Expands the next element in the maze expansion list. Returns false, if the
   * expansion list is exhausted or the destination is reached.
   */
  public boolean occupy_next_element() {
    if (this.destination_door != null) {
      return false; // destination already reached
    }
    MazeListElement list_element = null;
    MazeSearchElement curr_door_section = null;
    // Search the next element, which is not yet expanded.
    // Use poll() to efficiently get and remove the best element (O(log n) instead
    // of O(n))
    boolean next_element_found = false;
    while (!maze_expansion_list.isEmpty()) {
      if (this.autoroute_engine.is_stop_requested()) {
        return false;
      }

      list_element = maze_expansion_list.poll(); // O(log n) - gets highest priority element
      if (list_element == null) {
        break; // Queue unexpectedly empty
      }

      int curr_section_no = list_element.section_no_of_door;
      curr_door_section = list_element.door.get_maze_search_element(curr_section_no);

      if (!curr_door_section.is_occupied) {
        next_element_found = true;
        break;
      }
      // Element already occupied, recycle and continue
      MazeListElement.recycle(list_element);
    }
    if (!next_element_found) {
      return false;
    }
    curr_door_section.backtrack_door = list_element.backtrack_door;
    curr_door_section.section_no_of_backtrack_door = list_element.section_no_of_backtrack_door;
    curr_door_section.room_ripped = list_element.room_ripped;
    curr_door_section.adjustment = list_element.adjustment;

    if (list_element.door instanceof DrillPage) {
      expand_to_drills_of_page(list_element);
      return true;
    }

    if (list_element.door instanceof TargetItemExpansionDoor curr_door) {
      if (curr_door.is_destination_door()) {
        // The destination is reached.
        this.destination_door = curr_door;
        this.section_no_of_destination_door = list_element.section_no_of_door;
        return false;
      }
    }
    if (ctrl.is_fanout && list_element.door instanceof ExpansionDrill
        && list_element.backtrack_door instanceof ExpansionDrill) {
      // algorithm completed after the first drill;
      this.destination_door = list_element.door;
      this.section_no_of_destination_door = list_element.section_no_of_door;
      return false;
    }
    if (ctrl.vias_allowed && list_element.door instanceof ExpansionDrill
        && !(list_element.backtrack_door instanceof ExpansionDrill)) {
      expand_to_other_layers(list_element);
    }

    if (list_element.next_room != null) {
      if (list_element.next_room != null) {
        if (!expand_to_room_doors(list_element)) {
          return true; // occupation by ripup is delayed or nothing was expanded
          // In case nothing was expanded allow the section to be occupied from
          // somewhere else, if the next room is thin.
        }
      }
    }
    curr_door_section.is_occupied = true;
    return true;
  }

  /**
   * Expands the other door section of the room. Returns true, if the from door
   * section has to be occupied, and false, if the occupation for is delayed.
   */
  private boolean expand_to_room_doors(MazeListElement p_list_element) {

    // Complete the neighbour rooms to make sure, that the
    // doors of this room will not change later on.
    int layer_no = p_list_element.next_room.get_layer();

    boolean layer_active = ctrl.layer_active[layer_no];
    if (!layer_active) {
      if (autoroute_engine.board.layer_structure.arr[layer_no].is_signal) {
        return true;
      }
    }

    double half_width = ctrl.compensated_trace_half_width[layer_no];
    boolean curr_door_is_small = false;
    if (p_list_element.door instanceof ExpansionDoor curr_door) {
      double half_width_add = half_width + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
      if (this.ctrl.with_neckdown) {
        // try evtl. neckdown at a destination pin
        double neck_down_half_width = check_neck_down_at_dest_pin(p_list_element.next_room);
        if (neck_down_half_width > 0) {
          half_width_add = Math.min(half_width_add, neck_down_half_width);
          half_width = half_width_add;
        }
      }
      curr_door_is_small = door_is_small(curr_door, 2 * half_width_add);
    }

    this.autoroute_engine.complete_neighbour_rooms(p_list_element.next_room);

    FloatPoint shape_entry_middle = p_list_element.shape_entry.a.middle_point(p_list_element.shape_entry.b);

    if (this.ctrl.with_neckdown && p_list_element.door instanceof TargetItemExpansionDoor door) {
      // try evtl. neckdown at a start pin
      Item start_item = door.item;
      if (start_item instanceof Pin pin) {
        double neckdown_half_width = pin.get_trace_neckdown_halfwidth(layer_no);
        if (neckdown_half_width > 0) {
          half_width = Math.min(half_width, neckdown_half_width);
        }
      }
    }

    boolean next_room_is_thick = true;
    if (p_list_element.next_room instanceof ObstacleExpansionRoom room) {
      next_room_is_thick = room_shape_is_thick(room);
    } else {
      TileShape next_room_shape = p_list_element.next_room.get_shape();
      if (next_room_shape.min_width() < 2 * half_width) {
        next_room_is_thick = false; // to prevent problems with the opposite side
      } else if (!p_list_element.already_checked && p_list_element.door.get_dimension() == 1 && !curr_door_is_small) {
        // The algorithm below works only, if p_location is on the border of
        // p_room_shape.
        // That is only the case for 1 dimensional doors.
        // For small doors the check is done in check_leaving_via below.

        FloatPoint[] nearest_points = next_room_shape.nearest_border_points_approx(shape_entry_middle, 2);
        if (nearest_points.length < 2) {
          FRLogger.warn("MazeSearchAlgo.expand_to_room_doors: nearest_points.length == 2 expected");
          next_room_is_thick = false;
        } else {
          double curr_dist = nearest_points[1].distance(shape_entry_middle);
          next_room_is_thick = curr_dist > half_width + 1;
        }
      }
    }
    if (!layer_active && p_list_element.door instanceof ExpansionDrill drill) {
      // check for drill to a foreign conduction area on split plane.
      Point drill_location = drill.location;
      ItemSelectionFilter filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.CONDUCTION);
      Set<Item> picked_items = autoroute_engine.board.pick_items(drill_location, layer_no, filter);
      for (Item curr_item : picked_items) {
        if (!curr_item.contains_net(ctrl.net_no)) {
          return true;
        }
      }
    }
    boolean something_expanded = expand_to_target_doors(p_list_element, next_room_is_thick, curr_door_is_small,
        shape_entry_middle);

    if (!layer_active) {
      return true;
    }

    int ripup_costs = 0;

    if (p_list_element.next_room instanceof FreeSpaceExpansionRoom) {
      if (!p_list_element.already_checked) {
        if (curr_door_is_small) {
          boolean enter_through_small_door = false;
          if (next_room_is_thick) {
            // check to enter the thick room from a ripped item through a small door (after
            // ripup)
            enter_through_small_door = check_leaving_ripped_item(p_list_element);
          }
          if (!enter_through_small_door) {
            return something_expanded;
          }
        }
      }
    } else if (p_list_element.next_room instanceof ObstacleExpansionRoom obstacle_room) {

      if (!p_list_element.already_checked) {
        boolean room_rippable = false;
        if (this.ctrl.ripup_allowed) {
          ripup_costs = check_ripup(p_list_element, obstacle_room.get_item(), curr_door_is_small);
          room_rippable = ripup_costs >= 0;
        }

        if (ripup_costs != ALREADY_RIPPED_COSTS && next_room_is_thick) {
          Item obstacle_item = obstacle_room.get_item();
          if (!curr_door_is_small && this.ctrl.max_shove_trace_recursion_depth > 0
              && obstacle_item instanceof PolylineTrace) {
            boolean shoved = shove_trace_room(p_list_element, obstacle_room);
            if (!shoved) {
              if (ripup_costs > 0) {
                // delay the occupation by ripup to allow shoving the room by another door
                // sections.
                MazeListElement new_element = MazeListElement.obtain(p_list_element.door,
                    p_list_element.section_no_of_door, p_list_element.backtrack_door,
                    p_list_element.section_no_of_backtrack_door,
                    p_list_element.expansion_value + ripup_costs, p_list_element.sorting_value + ripup_costs,
                    p_list_element.next_room, p_list_element.shape_entry, true, p_list_element.adjustment,
                    true);
                this.maze_expansion_list.add(new_element);
              }
              return something_expanded;
            }
          }
        }
        if (!room_rippable) {
          return true;
        }
      }
    }

    for (ExpansionDoor to_door : p_list_element.next_room.get_doors()) {
      if (to_door == p_list_element.door) {
        continue;
      }
      if (expand_to_door(to_door, p_list_element, ripup_costs, next_room_is_thick, MazeSearchElement.Adjustment.NONE)) {
        something_expanded = true;
      }
    }

    // Expand also the drill pages intersecting the room.
    if (ctrl.vias_allowed && !(p_list_element.door instanceof ExpansionDrill)) {
      if ((something_expanded || next_room_is_thick)
          && p_list_element.next_room instanceof CompleteFreeSpaceExpansionRoom) {
        // avoid setting something_expanded to true when next_room is thin to allow
        // occupying by
        // different sections of the door
        Collection<DrillPage> overlapping_drill_pages = this.autoroute_engine.drill_page_array
            .overlapping_pages(p_list_element.next_room.get_shape());
        {
          for (DrillPage to_drill_page : overlapping_drill_pages) {
            expand_to_drill_page(to_drill_page, p_list_element);
            something_expanded = true;
          }
        }
      } else if (p_list_element.next_room instanceof ObstacleExpansionRoom room) {
        Item curr_obstacle_item = room.get_item();
        if (curr_obstacle_item instanceof Via curr_via) {
          ExpansionDrill via_drill_info = curr_via
              .get_autoroute_drill_info(this.autoroute_engine.autoroute_search_tree);
          expand_to_drill(via_drill_info, p_list_element, ripup_costs);
        }
      }
    }

    return something_expanded;
  }

  /**
   * Expand the target doors of the room. Returns true, if at leat 1 target door
   * was expanded
   */
  private boolean expand_to_target_doors(MazeListElement p_list_element, boolean p_next_room_is_thick,
      boolean p_curr_door_is_small, FloatPoint p_shape_entry_middle) {
    if (p_curr_door_is_small) {
      boolean enter_through_small_door = false;
      if (p_list_element.door instanceof ExpansionDoor) {
        CompleteExpansionRoom from_room = p_list_element.door.other_room(p_list_element.next_room);
        if (from_room instanceof ObstacleExpansionRoom) {
          // otherwise entering through the small door may fail, because it was not
          // checked.
          enter_through_small_door = true;
        }
      }
      if (!enter_through_small_door) {
        return false;
      }
    }
    boolean result = false;
    for (TargetItemExpansionDoor to_door : p_list_element.next_room.get_target_doors()) {
      if (to_door == p_list_element.door) {
        continue;
      }
      // Validate index before calling - prevents warning when indices become stale
      // during routing
      int tree_shape_count = to_door.item.tree_shape_count(this.autoroute_engine.autoroute_search_tree);
      if (to_door.tree_entry_no < 0 || to_door.tree_entry_no >= tree_shape_count) {
        // Index out of range (trace was modified during routing)
        continue;
      }
      TileShape target_shape = ((Connectable) to_door.item)
          .get_trace_connection_shape(this.autoroute_engine.autoroute_search_tree, to_door.tree_entry_no);
      if (target_shape == null) {
        // Item's tree shape index out of range (can happen when traces are modified
        // during routing)
        continue;
      }
      FloatPoint connection_point = target_shape.nearest_point_approx(p_shape_entry_middle);
      if (!p_next_room_is_thick) {
        // check the line from p_shape_entry_middle to the nearest point.
        int[] curr_net_no_arr = new int[1];
        curr_net_no_arr[0] = this.ctrl.net_no;
        int curr_layer = p_list_element.next_room.get_layer();
        IntPoint[] check_points = new IntPoint[2];
        check_points[0] = p_shape_entry_middle.round();
        check_points[1] = connection_point.round();
        if (!check_points[0].equals(check_points[1])) {
          Polyline check_polyline = new Polyline(check_points);
          boolean check_ok = autoroute_engine.board.check_forced_trace_polyline(check_polyline,
              ctrl.trace_half_width[curr_layer], curr_layer, curr_net_no_arr, ctrl.trace_clearance_class_no,
              ctrl.max_shove_trace_recursion_depth, ctrl.max_shove_via_recursion_depth,
              ctrl.max_spring_over_recursion_depth);
          if (!check_ok) {
            continue;
          }
        }
      }

      FloatLine new_shape_entry = new FloatLine(connection_point, connection_point);

      if (expand_to_door_section(to_door, 0, new_shape_entry, p_list_element, 0, MazeSearchElement.Adjustment.NONE)) {
        result = true;
      }
    }
    return result;
  }

  /**
   * Return true, if at least 1 door ection was expanded.
   */
  private boolean expand_to_door(ExpansionDoor p_to_door, MazeListElement p_list_element, int p_add_costs,
      boolean p_next_room_is_thick, MazeSearchElement.Adjustment p_adjustment) {
    double half_width = ctrl.compensated_trace_half_width[p_list_element.next_room.get_layer()];
    boolean something_expanded = false;
    FloatLine[] line_sections = p_to_door.get_section_segments(half_width);

    for (int i = 0; i < line_sections.length; i++) {
      if (p_to_door.section_arr[i].is_occupied) {
        continue;
      }
      FloatLine new_shape_entry;
      if (p_next_room_is_thick) {
        new_shape_entry = line_sections[i];
        if (p_to_door.dimension == 1 && line_sections.length == 1
            && p_to_door.first_room instanceof CompleteFreeSpaceExpansionRoom
            && p_to_door.second_room instanceof CompleteFreeSpaceExpansionRoom) {
          // check entering the p_to_door at an acute corner of the shape of
          // p_list_element.next_room
          FloatPoint shape_entry_middle = new_shape_entry.a.middle_point(new_shape_entry.b);
          TileShape room_shape = p_list_element.next_room.get_shape();
          if (room_shape.min_width() < 2 * half_width) {
            return false;
          }
          FloatPoint[] nearest_points = room_shape.nearest_border_points_approx(shape_entry_middle, 2);
          if (nearest_points.length < 2 || nearest_points[1].distance(shape_entry_middle) <= half_width + 1) {
            return false;
          }
        }
      } else {
        // expand only doors on the opposite side of the room from the shape_entry.
        if (p_to_door.dimension == 1 && i == 0 && line_sections[0].b.distance_square(line_sections[0].a) < 1) {
          // p_to_door is small belonging to a via or thin room
          continue;
        }
        new_shape_entry = segment_projection(p_list_element.shape_entry, line_sections[i]);
        if (new_shape_entry == null) {
          continue;
        }
      }

      if (expand_to_door_section(p_to_door, i, new_shape_entry, p_list_element, p_add_costs, p_adjustment)) {
        something_expanded = true;
      }
    }
    return something_expanded;
  }

  /**
   * Checks, if the width p_door is big enough for a trace with width
   * p_trace_width.
   */
  private boolean door_is_small(ExpansionDoor p_door, double p_trace_width) {
    if (p_door.dimension == 1 || p_door.first_room instanceof CompleteFreeSpaceExpansionRoom
        && p_door.second_room instanceof CompleteFreeSpaceExpansionRoom) {
      TileShape door_shape = p_door.get_shape();
      if (door_shape.is_empty()) {
        FRLogger.trace("MazeSearchAlgo:check_door_width door_shape is empty");
        return true;
      }

      double door_length;
      AngleRestriction angle_restriction = autoroute_engine.board.rules.get_trace_angle_restriction();
      if (angle_restriction == AngleRestriction.NINETY_DEGREE) {
        IntBox door_box = door_shape.bounding_box();
        door_length = door_box.max_width();
      } else if (angle_restriction == AngleRestriction.FORTYFIVE_DEGREE) {
        IntOctagon door_oct = door_shape.bounding_octagon();
        door_length = door_oct.max_width();
      } else {
        FloatLine door_line_segment = door_shape.diagonal_corner_segment();
        door_length = door_line_segment.b.distance(door_line_segment.a);
      }
      return door_length < p_trace_width;
    }
    return false;
  }

  /**
   * Return true, if the door section was successfully expanded.
   */
  private boolean expand_to_door_section(ExpandableObject p_door, int p_section_no, FloatLine p_shape_entry,
      MazeListElement p_from_element, int p_add_costs,
      MazeSearchElement.Adjustment p_adjustment) {
    if (p_door.get_maze_search_element(p_section_no).is_occupied || p_shape_entry == null) {
      return false;
    }
    CompleteExpansionRoom next_room = p_door.other_room(p_from_element.next_room);
    int layer = p_from_element.next_room.get_layer();
    FloatPoint shape_entry_middle = p_shape_entry.a.middle_point(p_shape_entry.b);
    double expansion_value = p_from_element.expansion_value + p_add_costs
        + shape_entry_middle.weighted_distance(p_from_element.shape_entry.a.middle_point(p_from_element.shape_entry.b),
            ctrl.trace_costs[layer].horizontal,
            ctrl.trace_costs[layer].vertical);
    double sorting_value = expansion_value + this.destination_distance.calculate(shape_entry_middle, layer);
    boolean room_ripped = p_add_costs > 0 && p_adjustment == MazeSearchElement.Adjustment.NONE
        || p_from_element.already_checked && p_from_element.room_ripped;

    MazeListElement new_element = MazeListElement.obtain(p_door, p_section_no, p_from_element.door,
        p_from_element.section_no_of_door, expansion_value, sorting_value, next_room, p_shape_entry,
        room_ripped, p_adjustment, false);
    this.maze_expansion_list.add(new_element);
    return true;
  }

  private void expand_to_drill(ExpansionDrill p_drill, MazeListElement p_from_element, int p_add_costs) {
    int layer = p_from_element.next_room.get_layer();
    int trace_half_width = this.ctrl.compensated_trace_half_width[layer];
    boolean room_shape_is_thin = p_from_element.next_room
        .get_shape()
        .min_width() < 2 * trace_half_width;

    if (room_shape_is_thin) {
      // expand only drills intersecting the backtrack door
      if (p_from_element.backtrack_door == null || !p_drill
          .get_shape()
          .intersects(p_from_element.backtrack_door.get_shape())) {
        return;
      }
    }

    double via_radius = ctrl.via_radius_arr[layer];
    ConvexShape shrinked_drill_shape = p_drill
        .get_shape()
        .shrink(via_radius);
    FloatPoint compare_corner = p_from_element.shape_entry.a.middle_point(p_from_element.shape_entry.b);
    if (p_from_element.door instanceof DrillPage
        && p_from_element.backtrack_door instanceof TargetItemExpansionDoor door) {
      // If expansion comes from a pin with trace exit directions the expansion_value
      // is calculated
      // from the nearest trace exit point instead from the center olf the pin.
      Item from_item = door.item;
      if (from_item instanceof Pin pin) {
        FloatPoint nearest_exit_corner = pin.nearest_trace_exit_corner(p_drill.location.to_float(), trace_half_width,
            layer);
        if (nearest_exit_corner != null) {
          compare_corner = nearest_exit_corner;
        }
      }
    }
    FloatPoint nearest_point = shrinked_drill_shape.nearest_point_approx(compare_corner);
    FloatLine shape_entry = new FloatLine(nearest_point, nearest_point);
    int section_no = layer - p_drill.first_layer;
    double expansion_value = p_from_element.expansion_value + p_add_costs + nearest_point
        .weighted_distance(compare_corner, ctrl.trace_costs[layer].horizontal, ctrl.trace_costs[layer].vertical);
    ExpandableObject new_backtrack_door;
    int new_section_no_of_backtrack_door;
    if (p_from_element.door instanceof DrillPage) {
      new_backtrack_door = p_from_element.backtrack_door;
      new_section_no_of_backtrack_door = p_from_element.section_no_of_backtrack_door;
    } else {
      // Expanded directly through already existing via
      // The step expand_to_drill_page is skipped
      new_backtrack_door = p_from_element.door;
      new_section_no_of_backtrack_door = p_from_element.section_no_of_door;
      expansion_value += ctrl.min_normal_via_cost;
    }
    double sorting_value = expansion_value + this.destination_distance.calculate(nearest_point, layer);
    MazeListElement new_element = MazeListElement.obtain(p_drill, section_no, new_backtrack_door,
        new_section_no_of_backtrack_door, expansion_value, sorting_value, null, shape_entry,
        p_from_element.room_ripped, MazeSearchElement.Adjustment.NONE, false);
    this.maze_expansion_list.add(new_element);
  }

  /**
   * A drill page is inserted between an expansion room and the drill to expand in
   * order to prevent performance problems with rooms with big shapes containing
   * many drills.
   */
  private void expand_to_drill_page(DrillPage p_drill_page, MazeListElement p_from_element) {

    int layer = p_from_element.next_room.get_layer();
    FloatPoint from_element_shape_entry_middle = p_from_element.shape_entry.a
        .middle_point(p_from_element.shape_entry.b);
    FloatPoint nearest_point = p_drill_page.shape.nearest_point(from_element_shape_entry_middle);
    double expansion_value = p_from_element.expansion_value + ctrl.min_normal_via_cost;
    double sorting_value = expansion_value
        + nearest_point.weighted_distance(from_element_shape_entry_middle, ctrl.trace_costs[layer].horizontal,
            ctrl.trace_costs[layer].vertical)
        + this.destination_distance.calculate(
            nearest_point, layer);
    MazeListElement new_element = MazeListElement.obtain(p_drill_page, layer, p_from_element.door,
        p_from_element.section_no_of_door, expansion_value, sorting_value, p_from_element.next_room,
        p_from_element.shape_entry, p_from_element.room_ripped, MazeSearchElement.Adjustment.NONE, false);
    this.maze_expansion_list.add(new_element);
  }

  private void expand_to_drills_of_page(MazeListElement p_from_element) {
    int from_room_layer = p_from_element.section_no_of_door;
    DrillPage drill_page = (DrillPage) p_from_element.door;
    Collection<ExpansionDrill> drill_list = drill_page.get_drills(this.autoroute_engine, this.ctrl.attach_smd_allowed);
    for (ExpansionDrill curr_drill : drill_list) {
      int section_no = from_room_layer - curr_drill.first_layer;
      if (section_no < 0 || section_no >= curr_drill.room_arr.length) {
        continue;
      }
      if (curr_drill.room_arr[section_no] == p_from_element.next_room
          && !curr_drill.get_maze_search_element(section_no).is_occupied) {
        expand_to_drill(curr_drill, p_from_element, 0);
      }
    }
  }

  /**
   * Tries to expand other layers by inserting a via.
   */
  private void expand_to_other_layers(MazeListElement p_list_element) {
    int via_lower_bound = 0;
    int via_upper_bound = -1;
    ExpansionDrill curr_drill = (ExpansionDrill) p_list_element.door;
    int from_layer = curr_drill.first_layer + p_list_element.section_no_of_door;
    boolean smd_attached_on_component_side = false;
    boolean smd_attached_on_solder_side = false;
    boolean room_ripped;
    if (curr_drill.room_arr[p_list_element.section_no_of_door] instanceof ObstacleExpansionRoom room) {
      // check ripup of an existing via
      if (!this.ctrl.ripup_allowed) {
        return;
      }
      Item curr_obstacle_item = room.get_item();
      if (!(curr_obstacle_item instanceof Via)) {
        return;
      }
      Padstack curr_obstacle_padstack = ((Via) curr_obstacle_item).get_padstack();
      if (!this.ctrl.via_rule.contains_padstack(curr_obstacle_padstack)
          || curr_obstacle_item.clearance_class_no() != this.ctrl.via_clearance_class) {
        return;
      }
      via_lower_bound = curr_obstacle_padstack.from_layer();
      via_upper_bound = curr_obstacle_padstack.to_layer();
      room_ripped = true;
    } else {
      int[] net_no_arr = new int[1];
      net_no_arr[0] = ctrl.net_no;

      room_ripped = false;
      int via_lower_limit = Math.max(curr_drill.first_layer, ctrl.via_lower_bound);
      int via_upper_limit = Math.min(curr_drill.last_layer, ctrl.via_upper_bound);
      // Calculate the lower bound of possible vias.
      int curr_layer = from_layer;
      for (;;) {
        TileShape curr_room_shape = curr_drill.room_arr[curr_layer - curr_drill.first_layer].get_shape();
        ForcedPadAlgo.CheckDrillResult drill_result = ForcedViaAlgo.check_layer(ctrl.via_radius_arr[curr_layer],
            ctrl.via_clearance_class, ctrl.attach_smd_allowed, curr_room_shape,
            curr_drill.location, curr_layer, net_no_arr, ctrl.max_shove_trace_recursion_depth, 0,
            autoroute_engine.board);
        if (drill_result == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          via_lower_bound = curr_layer + 1;
          break;
        } else if (drill_result == ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
          if (curr_layer == 0) {
            smd_attached_on_component_side = true;
          } else if (curr_layer == ctrl.layer_count - 1) {
            smd_attached_on_solder_side = true;
          }
        }
        if (curr_layer <= via_lower_limit) {
          via_lower_bound = via_lower_limit;
          break;
        }
        --curr_layer;
      }
      if (via_lower_bound > curr_drill.first_layer) {
        return;
      }
      curr_layer = from_layer + 1;
      for (;;) {
        if (curr_layer > via_upper_limit) {
          via_upper_bound = via_upper_limit;
          break;
        }
        TileShape curr_room_shape = curr_drill.room_arr[curr_layer - curr_drill.first_layer].get_shape();
        ForcedPadAlgo.CheckDrillResult drill_result = ForcedViaAlgo.check_layer(ctrl.via_radius_arr[curr_layer],
            ctrl.via_clearance_class, ctrl.attach_smd_allowed, curr_room_shape,
            curr_drill.location, curr_layer, net_no_arr, ctrl.max_shove_trace_recursion_depth, 0,
            autoroute_engine.board);
        if (drill_result == ForcedPadAlgo.CheckDrillResult.NOT_DRILLABLE) {
          via_upper_bound = curr_layer - 1;
          break;
        } else if (drill_result == ForcedPadAlgo.CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD) {
          if (curr_layer == ctrl.layer_count - 1) {
            smd_attached_on_solder_side = true;
          }
        }
        ++curr_layer;
      }
      if (via_upper_bound < curr_drill.last_layer) {
        return;
      }
    }

    for (int to_layer = via_lower_bound; to_layer <= via_upper_bound; to_layer++) {
      if (to_layer == from_layer) {
        continue;
      }
      // check, there is a fitting via mask.
      int curr_first_layer;
      int curr_last_layer;
      if (to_layer < from_layer) {
        curr_first_layer = to_layer;
        curr_last_layer = from_layer;
      } else {
        curr_first_layer = from_layer;
        curr_last_layer = to_layer;
      }
      boolean mask_found = false;
      for (int i = 0; i < ctrl.via_info_arr.length; i++) {
        AutorouteControl.ViaMask curr_via_info = ctrl.via_info_arr[i];
        if (curr_first_layer >= curr_via_info.from_layer && curr_last_layer <= curr_via_info.to_layer
            && curr_via_info.from_layer >= via_lower_bound && curr_via_info.to_layer <= via_upper_bound) {
          boolean mask_ok = true;
          if (curr_via_info.from_layer == 0 && smd_attached_on_component_side
              || curr_via_info.to_layer == ctrl.layer_count - 1 && smd_attached_on_solder_side) {
            mask_ok = curr_via_info.attach_smd_allowed;
          }
          if (mask_ok) {
            mask_found = true;
            break;
          }
        }
      }
      if (!mask_found) {
        continue;
      }
      MazeSearchElement curr_drill_layer_info = curr_drill.get_maze_search_element(to_layer - curr_drill.first_layer);
      if (curr_drill_layer_info.is_occupied) {
        continue;
      }
      double expansion_value = p_list_element.expansion_value + ctrl.add_via_costs[from_layer].to_layer[to_layer];
      FloatPoint shape_entry_middle = p_list_element.shape_entry.a.middle_point(p_list_element.shape_entry.b);
      double sorting_value = expansion_value + this.destination_distance.calculate(shape_entry_middle, to_layer);
      int curr_room_index = to_layer - curr_drill.first_layer;
      MazeListElement new_element = MazeListElement.obtain(curr_drill, curr_room_index, curr_drill,
          p_list_element.section_no_of_door, expansion_value, sorting_value,
          curr_drill.room_arr[curr_room_index], p_list_element.shape_entry, room_ripped,
          MazeSearchElement.Adjustment.NONE, false);
      this.maze_expansion_list.add(new_element);
    }
  }

  /**
   * Initializes the maze search algorithm. Returns false if the initialisation
   * failed.
   */
  private boolean init(Set<Item> p_start_items, Set<Item> p_destination_items) {
    reduce_trace_shapes_at_tie_pins(p_start_items, this.ctrl.net_no, this.search_tree);
    reduce_trace_shapes_at_tie_pins(p_destination_items, this.ctrl.net_no, this.search_tree);
    // process the destination items
    boolean destination_ok = false;
    for (Item curr_item : p_destination_items) {
      if (this.autoroute_engine.is_stop_requested()) {
        return false;
      }
      ItemAutorouteInfo curr_info = curr_item.get_autoroute_info();
      curr_info.set_start_info(false);
      for (int i = 0; i < curr_item.tree_shape_count(this.search_tree); i++) {
        TileShape curr_tree_shape = curr_item.get_tree_shape(this.search_tree, i);
        if (curr_tree_shape != null) {
          destination_distance.join(curr_tree_shape.bounding_box(), curr_item.shape_layer(i));
        }
      }
      destination_ok = true;
    }
    if (!destination_ok && this.ctrl.is_fanout) {
      // destination set is not needed for fanout
      IntBox board_bounding_box = this.autoroute_engine.board.bounding_box;
      destination_distance.join(board_bounding_box, 0);
      destination_distance.join(board_bounding_box, this.ctrl.layer_count - 1);
      destination_ok = true;
    }

    if (!destination_ok) {
      return false;
    }
    // process the start items
    Collection<IncompleteFreeSpaceExpansionRoom> start_rooms = new LinkedList<>();
    for (Item curr_item : p_start_items) {
      if (this.autoroute_engine.is_stop_requested()) {
        return false;
      }
      ItemAutorouteInfo curr_info = curr_item.get_autoroute_info();
      curr_info.set_start_info(true);
      if (curr_item instanceof Connectable connectable) {
        for (int i = 0; i < curr_item.tree_shape_count(search_tree); i++) {
          TileShape contained_shape = connectable.get_trace_connection_shape(search_tree, i);
          IncompleteFreeSpaceExpansionRoom new_start_room = autoroute_engine.add_incomplete_expansion_room(null,
              curr_item.shape_layer(i), contained_shape);
          start_rooms.add(new_start_room);
        }
      }
    }

    // complete the start rooms
    Collection<CompleteFreeSpaceExpansionRoom> completed_start_rooms = new LinkedList<>();

    if (this.autoroute_engine.maintain_database) {
      // add the completed start rooms carried over from the last autoroute to the
      // start rooms.
      completed_start_rooms.addAll(this.autoroute_engine.get_rooms_with_target_items(p_start_items));
    }

    for (IncompleteFreeSpaceExpansionRoom curr_room : start_rooms) {
      if (this.autoroute_engine.is_stop_requested()) {
        return false;
      }
      Collection<CompleteFreeSpaceExpansionRoom> curr_completed_rooms = autoroute_engine
          .complete_expansion_room(curr_room);
      completed_start_rooms.addAll(curr_completed_rooms);
    }

    // Put the ItemExpansionDoors of the completed start rooms into
    // the maze_expansion_list.
    boolean start_ok = false;
    for (CompleteFreeSpaceExpansionRoom curr_room : completed_start_rooms) {
      for (TargetItemExpansionDoor curr_door : curr_room.get_target_doors()) {
        if (this.autoroute_engine.is_stop_requested()) {
          return false;
        }
        if (curr_door.is_destination_door()) {
          continue;
        }
        TileShape connection_shape = ((Connectable) curr_door.item).get_trace_connection_shape(search_tree,
            curr_door.tree_entry_no);
        connection_shape = connection_shape.intersection(curr_door.room.get_shape());
        FloatPoint curr_center = connection_shape.centre_of_gravity();
        FloatLine shape_entry = new FloatLine(curr_center, curr_center);
        double sorting_value = this.destination_distance.calculate(curr_center, curr_room.get_layer());
        MazeListElement new_list_element = MazeListElement.obtain(curr_door, 0, null, 0, 0, sorting_value, curr_room,
            shape_entry, false, MazeSearchElement.Adjustment.NONE, false);
        maze_expansion_list.add(new_list_element);
        start_ok = true;
      }
    }
    return start_ok;
  }

  private boolean room_shape_is_thick(ObstacleExpansionRoom p_obstacle_room) {
    Item obstacle_item = p_obstacle_room.get_item();
    int layer = p_obstacle_room.get_layer();
    double obstacle_half_width;
    if (obstacle_item instanceof Trace trace) {
      obstacle_half_width = trace.get_half_width()
          + this.search_tree.clearance_compensation_value(obstacle_item.clearance_class_no(), layer);

    } else if (obstacle_item instanceof Via via) {
      TileShape via_shape = via.get_tree_shape_on_layer(this.search_tree, layer);
      obstacle_half_width = 0.5 * via_shape.max_width();
    } else {
      FRLogger.warn("MazeSearchAlgo. room_shape_is_thick: unexpected obstacle item");
      obstacle_half_width = 0;
    }
    return obstacle_half_width >= this.ctrl.compensated_trace_half_width[layer];
  }

  /**
   * Checks, if the room can be ripped and returns the rip up costs, which are >
   * 0, if the room is ripped and -1, if no ripup was possible. If the previous
   * room was also ripped and contained the same
   * item or an item of the same connection, the result will be equal to
   * ALREADY_RIPPED_COSTS
   */
  private int check_ripup(MazeListElement p_list_element, Item p_obstacle_item, boolean p_door_is_small) {
    if (!p_obstacle_item.is_routable()) {
      return -1;
    }
    if (p_door_is_small) {
      // allow entering a via or trace, if its corresponding border segment is smaller
      // than the
      // current trace width

      if (!enter_through_small_door(p_list_element, p_obstacle_item)) {
        return -1;
      }
    }
    CompleteExpansionRoom previous_room = p_list_element.door.other_room(p_list_element.next_room);
    boolean room_was_shoved = p_list_element.adjustment != MazeSearchElement.Adjustment.NONE;
    Item previous_item = null;
    if (previous_room instanceof ObstacleExpansionRoom room) {
      previous_item = room.get_item();
    }
    if (room_was_shoved) {
      if (previous_item != null && previous_item != p_obstacle_item && previous_item.shares_net(p_obstacle_item)) {
        // The ripped trace may start at a fork.
        return -1;
      }
    } else if (previous_item == p_obstacle_item) {
      return ALREADY_RIPPED_COSTS;
    }

    double fanout_via_cost_factor = 1.0;
    double cost_factor = 1;
    if (p_obstacle_item instanceof Trace obstacle_trace) {
      cost_factor = obstacle_trace.get_half_width();
      if (!this.ctrl.remove_unconnected_vias) {
        // protect traces between SMD-pins and fanout vias
        fanout_via_cost_factor = calc_fanout_via_ripup_cost_factor(obstacle_trace);
      }
    } else if (p_obstacle_item instanceof Via) {
      boolean look_if_fanout_via = !this.ctrl.remove_unconnected_vias;
      Collection<Item> contact_list = p_obstacle_item.get_normal_contacts();
      int contact_count = 0;
      for (Item curr_contact : contact_list) {
        if (!(curr_contact instanceof Trace obstacle_trace) || curr_contact.is_user_fixed()) {
          return -1;
        }
        ++contact_count;
        cost_factor = Math.max(cost_factor, obstacle_trace.get_half_width());
        if (look_if_fanout_via) {
          double curr_fanout_via_cost_factor = calc_fanout_via_ripup_cost_factor(obstacle_trace);
          if (curr_fanout_via_cost_factor > 1) {
            fanout_via_cost_factor = curr_fanout_via_cost_factor;
            look_if_fanout_via = false;
          }
        }
      }
      if (fanout_via_cost_factor <= 1) {
        // not a fanout via
        cost_factor *= 0.5 * Math.max(contact_count - 1, 0);
      }
    }

    double ripup_cost = this.ctrl.ripup_costs * cost_factor;
    double detour = 1;
    if (fanout_via_cost_factor <= 1) // p_obstacle_item does not belong to a fanout
    {
      Connection obstacle_connection = Connection.get(p_obstacle_item);
      if (obstacle_connection != null) {
        detour = obstacle_connection.get_detour();
      }
    }
    boolean randomize = this.ctrl.ripup_pass_no >= 4 && this.ctrl.ripup_pass_no % 3 != 0;
    if (randomize) {
      // shuffle the result to avoid repetitive loops
      double random_number = this.random_generator.nextDouble();
      double random_factor = 0.5 + random_number * random_number;
      detour *= random_factor;
    }
    ripup_cost /= detour;

    ripup_cost *= fanout_via_cost_factor;
    int result = Math.max((int) ripup_cost, 1);
    final int MAX_RIPUP_COSTS = Integer.MAX_VALUE / 100;
    return Math.min(result, MAX_RIPUP_COSTS);
  }

  /**
   * Shoves a trace room and expands the corresponding doors. Return false, if no
   * door was expanded. In this case occupation of the door_section by ripup can
   * be delayed to allow shoving the room fromm
   * a different door section
   */
  private boolean shove_trace_room(MazeListElement p_list_element, ObstacleExpansionRoom p_obstacle_room) {
    if (p_list_element.section_no_of_door != 0
        && p_list_element.section_no_of_door != p_list_element.door.maze_search_element_count() - 1) {
      // No delay of occupation necessary because inner sections of a door are
      // currently not
      // shoved.
      return true;
    }
    boolean result = false;
    if (p_list_element.adjustment != MazeSearchElement.Adjustment.RIGHT) {
      Collection<MazeShoveTraceAlgo.DoorSection> left_to_door_section_list = new LinkedList<>();

      if (MazeShoveTraceAlgo.check_shove_trace_line(p_list_element, p_obstacle_room, this.autoroute_engine.board,
          this.ctrl, false, left_to_door_section_list)) {
        result = true;
      }

      for (MazeShoveTraceAlgo.DoorSection curr_left_door_section : left_to_door_section_list) {
        MazeSearchElement.Adjustment curr_adjustment;
        if (curr_left_door_section.door.dimension == 2) {
          // the door is the link door to the next room
          curr_adjustment = MazeSearchElement.Adjustment.LEFT;
        } else {
          curr_adjustment = MazeSearchElement.Adjustment.NONE;
        }

        expand_to_door_section(curr_left_door_section.door, curr_left_door_section.section_no,
            curr_left_door_section.section_line, p_list_element, 0, curr_adjustment);
      }
    }

    if (p_list_element.adjustment != MazeSearchElement.Adjustment.LEFT) {
      Collection<MazeShoveTraceAlgo.DoorSection> right_to_door_section_list = new LinkedList<>();

      if (MazeShoveTraceAlgo.check_shove_trace_line(p_list_element, p_obstacle_room, this.autoroute_engine.board,
          this.ctrl, true, right_to_door_section_list)) {
        result = true;
      }
      for (MazeShoveTraceAlgo.DoorSection curr_right_door_section : right_to_door_section_list) {
        MazeSearchElement.Adjustment curr_adjustment;
        if (curr_right_door_section.door.dimension == 2) {
          // the door is the link door to the next room
          curr_adjustment = MazeSearchElement.Adjustment.RIGHT;
        } else {
          curr_adjustment = MazeSearchElement.Adjustment.NONE;
        }
        expand_to_door_section(curr_right_door_section.door, curr_right_door_section.section_no,
            curr_right_door_section.section_line, p_list_element, 0, curr_adjustment);
      }
    }
    return result;
  }

  /**
   * Checks, if the next room contains a destination pin, where evtl. neckdown is
   * necessary. Return the neck down width in this case, or 0, if no such pin was
   * found,
   */
  private double check_neck_down_at_dest_pin(CompleteExpansionRoom p_room) {
    Collection<TargetItemExpansionDoor> target_doors = p_room.get_target_doors();
    for (TargetItemExpansionDoor curr_target_door : target_doors) {
      if (curr_target_door.item instanceof Pin pin) {
        return pin.get_trace_neckdown_halfwidth(p_room.get_layer());
      }
    }
    return 0;
  }

  /**
   * Checks, if the next room can be entered if the door of p_list_element is
   * small. If p_ignore_item != null, p_ignore_item and all other items directly
   * connected to p_ignore_item are ignored in the
   * check.
   */
  private boolean enter_through_small_door(MazeListElement p_list_element, Item p_ignore_item) {
    if (p_list_element.door.get_dimension() != 1) {
      return false;
    }
    TileShape door_shape = p_list_element.door.get_shape();

    // Get the line of the 1 dimensional door.
    Line door_line = null;
    FloatPoint prev_corner = door_shape.corner_approx(0);
    int corner_count = door_shape.border_line_count();
    for (int i = 1; i < corner_count; i++) {
      // skip lines of length 0
      FloatPoint next_corner = door_shape.corner_approx(i);
      if (next_corner.distance_square(prev_corner) > 1) {
        door_line = door_shape.border_line(i - 1);
        break;
      }
      prev_corner = next_corner;
    }
    if (door_line == null) {
      return false;
    }

    IntPoint door_center = door_shape
        .centre_of_gravity()
        .round();
    int curr_layer = p_list_element.next_room.get_layer();
    int check_radius = this.ctrl.compensated_trace_half_width[curr_layer] + AutorouteEngine.TRACE_WIDTH_TOLERANCE;
    // create a perpendicular line segment of length 2 * check_radius through the
    // door center
    Line[] line_arr = new Line[3];
    line_arr[0] = door_line.translate(check_radius);
    line_arr[1] = new Line(door_center, door_line
        .direction()
        .turn_45_degree(2));
    line_arr[2] = door_line.translate(-check_radius);

    Polyline check_polyline = new Polyline(line_arr);
    TileShape check_shape = check_polyline.offset_shape(check_radius, 0);
    int[] ignore_net_nos = new int[1];
    ignore_net_nos[0] = this.ctrl.net_no;
    Set<SearchTreeObject> overlapping_objects = new TreeSet<>();
    this.autoroute_engine.autoroute_search_tree.overlapping_objects(check_shape, curr_layer, ignore_net_nos,
        overlapping_objects);

    for (SearchTreeObject curr_object : overlapping_objects) {
      if (!(curr_object instanceof Item curr_item) || curr_object == p_ignore_item) {
        continue;
      }
      if (!curr_item.shares_net(p_ignore_item)) {
        return false;
      }
      Set<Item> curr_contacts = curr_item.get_normal_contacts();
      if (!curr_contacts.contains(p_ignore_item)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks entering a thick room from a via or trace through a small door (after
   * ripup)
   */
  private boolean check_leaving_ripped_item(MazeListElement p_list_element) {
    if (!(p_list_element.door instanceof ExpansionDoor curr_door)) {
      return false;
    }
    CompleteExpansionRoom from_room = curr_door.other_room(p_list_element.next_room);
    if (!(from_room instanceof ObstacleExpansionRoom)) {
      return false;
    }
    Item curr_item = ((ObstacleExpansionRoom) from_room).get_item();
    if (!curr_item.is_routable()) {
      return false;
    }
    return enter_through_small_door(p_list_element, curr_item);
  }

  /**
   * The result type of MazeSearchAlgo.find_connection
   */
  public static class Result {

    public final ExpandableObject destination_door;
    public final int section_no_of_door;

    Result(ExpandableObject p_destination_door, int p_section_no_of_door) {
      destination_door = p_destination_door;
      section_no_of_door = p_section_no_of_door;
    }
  }

  /**
   * Used for the result of MazeShoveViaAlgo.check_shove_via and
   * MazeShoveThinRoomAlgo.check_shove_thin_room.
   */
  static class ShoveResult {

    /**
     * The opposite door to be expanded
     */
    final ExpansionDoor opposite_door;
    /**
     * The doors at the adjusted edge of the room shape to be expanded.
     */
    final Collection<ExpansionDoor> side_doors;
    /**
     * The passing point of a trace through the from_door after adjustment.
     */
    final FloatPoint from_door_passing_point;
    /**
     * The passing point of a trace through the opposite door after adjustment.
     */
    final FloatPoint opposite_door_passing_point;

    ShoveResult(ExpansionDoor p_opposite_door, Collection<ExpansionDoor> p_side_doors,
        FloatPoint p_from_door_passing_point, FloatPoint p_opposite_door_passing_point) {
      opposite_door = p_opposite_door;
      side_doors = p_side_doors;
      from_door_passing_point = p_from_door_passing_point;
      opposite_door_passing_point = p_opposite_door_passing_point;
    }
  }
}
package app.freerouting.tests;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.SearchTreeObject;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import java.util.Collection;
import java.util.Iterator;

/** Some consistency checking on a routing board. */
public class Validate {
  private static int[] last_violation_count = null;
  private static boolean first_time = true;
  private static int prev_stub_count = 0;

  /**
   * Does some consistency checking on the routing board and may be some other actions. Returns
   * false, if problems were detected.
   */
  public static boolean check(String p_s, BasicBoard p_board) {
    if (p_board.get_test_level() == app.freerouting.board.TestLevel.RELEASE_VERSION) {
      return true;
    }
    boolean result = true;

    IntOctagon surr_oct = p_board.bounding_box.to_IntOctagon();
    int layer_count = p_board.get_layer_count();
    if (last_violation_count == null) {
      last_violation_count = new int[layer_count];
    }
    for (int layer = 0; layer < layer_count; ++layer) {
      if (first_time) {
        System.out.println(" validate board is on ");
        first_time = false;
      }
      Collection<SearchTreeObject> l = p_board.overlapping_objects(surr_oct, layer);
      Iterator<SearchTreeObject> i = l.iterator();
      int clearance_violation_count = 0;
      int conflict_ob_count = 0;
      int trace_count = 0;
      while (i.hasNext()) {
        Item curr_ob = (Item) i.next();
        if (!curr_ob.validate()) {
          System.out.println(p_s);
        }
        int cl_count = curr_ob.clearance_violation_count();
        if (cl_count > 0) {
          ++conflict_ob_count;
          clearance_violation_count += cl_count;
        }
        if (curr_ob instanceof PolylineTrace) {
          ++trace_count;
        }
      }
      if (conflict_ob_count == 1) {
        System.out.println("conflicts not symmetric");
      }
      if (clearance_violation_count != last_violation_count[layer]) {
        result = false;
        System.out.print(clearance_violation_count);
        System.out.print(" clearance violations on layer ");
        System.out.print(layer);
        System.out.print(" ");
        System.out.println(p_s);
        if (clearance_violation_count > 0) {
          System.out.print("with items of nets: ");
        }
        i = l.iterator();
        while (i.hasNext()) {
          Item curr_ob = (Item) i.next();
          int cl_count = curr_ob.clearance_violation_count();
          if (cl_count == 0) {
            continue;
          }

          int curr_net_no = 0;
          if (curr_ob instanceof PolylineTrace) {
            PolylineTrace curr_trace = (PolylineTrace) curr_ob;
            if (curr_trace.net_count() > 0) {
              curr_net_no = curr_trace.get_net_no(0);
            }
          }
          System.out.print(curr_net_no);
          System.out.print(", ");
        }
        System.out.println();
      }
      if (clearance_violation_count != last_violation_count[layer]) {
        last_violation_count[layer] = clearance_violation_count;
      }
    }
    return result;
  }

  public static boolean check(
      String p_s,
      BasicBoard p_board,
      Polyline p_polyline,
      int p_layer,
      int p_half_width,
      int[] p_net_no_arr,
      int p_cl_type) {
    TileShape[] offset_shapes =
        p_polyline.offset_shapes(p_half_width, 0, p_polyline.arr.length - 1);
    for (int i = 0; i < offset_shapes.length; ++i) {
      Collection<Item> obstacles =
          p_board
              .search_tree_manager
              .get_default_tree()
              .overlapping_items_with_clearance(offset_shapes[i], p_layer, p_net_no_arr, p_cl_type);
      Iterator<Item> it = obstacles.iterator();
      while (it.hasNext()) {
        Item curr_obs = it.next();
        if (!curr_obs.shares_net_no(p_net_no_arr)) {
          System.out.print(p_s);
          System.out.println(": cannot insert trace without violations");
          return false;
        }
      }
    }
    return true;
  }

  /** check, that all traces on p_board are orthogonal */
  public static void orthogonal(String p_s, BasicBoard p_board) {
    Iterator<Item> it = p_board.get_items().iterator();
    while (it.hasNext()) {
      Item curr_ob = it.next();
      if (curr_ob instanceof PolylineTrace) {
        PolylineTrace curr_trace = (PolylineTrace) curr_ob;
        if (!curr_trace.polyline().is_orthogonal()) {
          System.out.print(p_s);
          System.out.println(": trace not orthogonal");
          break;
        }
      }
    }
  }

  /** check, that all traces on p_board are multiples of 45 degree */
  public static void multiple_of_45_degree(String p_s, BasicBoard p_board) {
    int count = 0;
    Iterator<Item> it = p_board.get_items().iterator();
    while (it.hasNext()) {
      Item curr_ob = it.next();
      if (curr_ob instanceof PolylineTrace) {
        PolylineTrace curr_trace = (PolylineTrace) curr_ob;
        if (!curr_trace.polyline().is_multiple_of_45_degree()) {
          ++count;
        }
      }
    }
    if (count > 1) {
      System.out.print(p_s);
      System.out.print(count);
      System.out.println(" traces not 45 degree");
    }
  }

  public static boolean corners_on_grid(String p_s, Polyline p_polyline) {
    for (int i = 0; i < p_polyline.corner_count(); ++i) {
      if (!(p_polyline.corner(i) instanceof IntPoint)) {
        System.out.print(p_s);
        System.out.println(": corner not on grid");
        return false;
      }
    }
    return true;
  }

  public static int stub_count(String p_s, BasicBoard p_board, int p_net_no) {
    if (first_time) {
      System.out.println(" stub_count is on ");
      first_time = false;
    }
    int result = 0;
    Iterator<Item> it = p_board.get_items().iterator();
    while (it.hasNext()) {
      Item curr_ob = it.next();
      if (curr_ob instanceof PolylineTrace) {
        PolylineTrace curr_trace = (PolylineTrace) curr_ob;
        if (curr_trace.contains_net(p_net_no)) {
          if (curr_trace.get_start_contacts().size() == 0) {
            ++result;
          }
          if (curr_trace.get_end_contacts().size() == 0) {
            ++result;
          }
        }
      }
    }
    if (result != prev_stub_count) {
      System.out.print(result + " stubs ");
      System.out.println(p_s);
      prev_stub_count = result;
    }
    return result;
  }

  public static boolean has_cycles(String p_s, BasicBoard p_board) {
    boolean result = false;
    Iterator<Item> it = p_board.get_items().iterator();
    while (it.hasNext()) {
      Item curr_item = it.next();
      if (!(curr_item instanceof app.freerouting.board.Trace)) {
        continue;
      }
      if (((app.freerouting.board.Trace) curr_item).is_cycle()) {
        System.out.print(p_s);
        System.out.println(": cycle found");
        result = true;
        break;
      }
    }
    return result;
  }

  /** checks, if there are more than p_max_count traces with net number p_net_no */
  public static boolean trace_count_exceeded(
      String p_s, BasicBoard p_board, int p_net_no, int p_max_count) {
    int found_traces = 0;
    Iterator<Item> it = p_board.get_items().iterator();
    while (it.hasNext()) {
      Item curr_ob = it.next();
      if (curr_ob instanceof app.freerouting.board.Trace) {
        if (curr_ob.contains_net(p_net_no)) {
          ++found_traces;
        }
      }
    }
    if (found_traces > p_max_count) {
      System.out.print(p_s);
      System.out.print(": ");
      System.out.print(p_max_count);
      System.out.println(" traces exceeded");
      return true;
    }
    return false;
  }

  /** checks, if there are unconnected traces ore vias on the board */
  public static boolean unconnnected_routing_items(String p_s, BasicBoard p_board) {
    Iterator<Item> it = p_board.get_items().iterator();
    while (it.hasNext()) {
      Item curr_item = it.next();
      if (curr_item.is_routable()) {
        Collection<Item> contact_list = curr_item.get_normal_contacts();
        if (contact_list.size() == 0) {
          System.out.print(p_s);
          System.out.print(": uncontacted routing item found ");
          return true;
        }
      }
    }
    return false;
  }
}

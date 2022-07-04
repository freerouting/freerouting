package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.InteractiveActionThread;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.SortedSet;

/** Handles the sequencing of the fanout inside the batch autorouter. */
public class BatchFanout {

  private final InteractiveActionThread thread;
  private final RoutingBoard routing_board;
  private final SortedSet<Component> sorted_components;

  private BatchFanout(InteractiveActionThread p_thread) {
    this.thread = p_thread;
    this.routing_board = p_thread.hdlg.get_routing_board();
    Collection<app.freerouting.board.Pin> board_smd_pin_list = routing_board.get_smd_pins();
    this.sorted_components = new java.util.TreeSet<Component>();
    for (int i = 1; i <= routing_board.components.count(); ++i) {
      app.freerouting.board.Component curr_board_component = routing_board.components.get(i);
      Component curr_component = new Component(curr_board_component, board_smd_pin_list);
      if (curr_component.smd_pin_count > 0) {
        sorted_components.add(curr_component);
      }
    }
  }

  public static void fanout_board(InteractiveActionThread p_thread) {
    BatchFanout fanout_instance = new BatchFanout(p_thread);
    final int MAX_PASS_COUNT = 20;
    for (int i = 0; i < MAX_PASS_COUNT; ++i) {
      int routed_count = fanout_instance.fanout_pass(i);
      if (routed_count == 0) {
        break;
      }
    }
  }

  /** Routes a fanout pass and returns the number of new fanouted SMD-pins in this pass. */
  private int fanout_pass(int p_pass_no) {
    int components_to_go = this.sorted_components.size();
    int routed_count = 0;
    int not_routed_count = 0;
    int insert_error_count = 0;
    int ripup_costs =
        this.thread.hdlg.get_settings().autoroute_settings.get_start_ripup_costs()
            * (p_pass_no + 1);
    for (Component curr_component : this.sorted_components) {
      this.thread.hdlg.screen_messages.set_batch_fanout_info(p_pass_no + 1, components_to_go);
      for (Component.Pin curr_pin : curr_component.smd_pins) {
        double max_milliseconds = 10000 * (p_pass_no + 1);
        TimeLimit time_limit = new TimeLimit((int) max_milliseconds);
        this.routing_board.start_marking_changed_area();
        AutorouteEngine.AutorouteResult curr_result =
            this.routing_board.fanout(
                curr_pin.board_pin,
                this.thread.hdlg.get_settings(),
                ripup_costs,
                this.thread,
                time_limit);
        if (curr_result == AutorouteEngine.AutorouteResult.ROUTED) {
          ++routed_count;
        } else if (curr_result == AutorouteEngine.AutorouteResult.NOT_ROUTED) {
          ++not_routed_count;
        } else if (curr_result == AutorouteEngine.AutorouteResult.INSERT_ERROR) {
          ++insert_error_count;
        }
        if (curr_result != AutorouteEngine.AutorouteResult.NOT_ROUTED) {
          this.thread.hdlg.repaint();
        }
        if (this.thread.is_stop_requested()) {
          return routed_count;
        }
      }
      --components_to_go;
    }
    if (this.routing_board.get_test_level() != app.freerouting.board.TestLevel.RELEASE_VERSION) {
      FRLogger.warn(
          "fanout pass: "
              + (p_pass_no + 1)
              + ", routed: "
              + routed_count
              + ", not routed: "
              + not_routed_count
              + ", errors: "
              + insert_error_count);
    }
    return routed_count;
  }

  private static class Component implements Comparable<Component> {

    final app.freerouting.board.Component board_component;
    final int smd_pin_count;
    final SortedSet<Pin> smd_pins;
    /** The center of gravity of all SMD pins of this component. */
    final FloatPoint gravity_center_of_smd_pins;
    Component(
        app.freerouting.board.Component p_board_component,
        Collection<app.freerouting.board.Pin> p_board_smd_pin_list) {
      this.board_component = p_board_component;

      // calcoulate the center of gravity of all SMD pins of this component.
      Collection<app.freerouting.board.Pin> curr_pin_list =
          new java.util.LinkedList<app.freerouting.board.Pin>();
      int cmp_no = p_board_component.no;
      for (app.freerouting.board.Pin curr_board_pin : p_board_smd_pin_list) {
        if (curr_board_pin.get_component_no() == cmp_no) {
          curr_pin_list.add(curr_board_pin);
        }
      }
      double x = 0;
      double y = 0;
      for (app.freerouting.board.Pin curr_pin : curr_pin_list) {
        FloatPoint curr_point = curr_pin.get_center().to_float();
        x += curr_point.x;
        y += curr_point.y;
      }
      this.smd_pin_count = curr_pin_list.size();
      x /= this.smd_pin_count;
      y /= this.smd_pin_count;
      this.gravity_center_of_smd_pins = new FloatPoint(x, y);

      // calculate the sorted SMD pins of this component
      this.smd_pins = new java.util.TreeSet<Pin>();

      for (app.freerouting.board.Pin curr_board_pin : curr_pin_list) {
        this.smd_pins.add(new Pin(curr_board_pin));
      }
    }

    /** Sort the components, so that components with maor pins come first */
    public int compareTo(Component p_other) {
      int compare_value = this.smd_pin_count - p_other.smd_pin_count;
      int result;
      if (compare_value > 0) {
        result = -1;
      } else if (compare_value < 0) {
        result = 1;
      } else {
        result = this.board_component.no - p_other.board_component.no;
      }
      return result;
    }

    class Pin implements Comparable<Pin> {

      final app.freerouting.board.Pin board_pin;
      final double distance_to_component_center;

      Pin(app.freerouting.board.Pin p_board_pin) {
        this.board_pin = p_board_pin;
        FloatPoint pin_location = p_board_pin.get_center().to_float();
        this.distance_to_component_center = pin_location.distance(gravity_center_of_smd_pins);
      }

      public int compareTo(Pin p_other) {
        int result;
        double delta_dist =
            this.distance_to_component_center - p_other.distance_to_component_center;
        if (delta_dist > 0) {
          result = 1;
        } else if (delta_dist < 0) {
          result = -1;
        } else {
          result = this.board_pin.pin_no - p_other.board_pin.pin_no;
        }
        return result;
      }
    }
  }
}

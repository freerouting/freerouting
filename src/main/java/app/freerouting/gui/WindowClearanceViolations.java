package app.freerouting.gui;

import app.freerouting.board.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.ClearanceViolations;
import app.freerouting.logger.FRLogger;
import java.util.List;

public class WindowClearanceViolations extends WindowObjectListWithFilter {

  private final java.util.ResourceBundle resources;

  /** Creates a new instance of clearance violations window */
  public WindowClearanceViolations(BoardFrame p_board_frame) {
    super(p_board_frame);
    this.resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.WindowClearanceViolations", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));
    this.list_empty_message.setText(resources.getString("list_empty_message"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_ClearanceViolations");
  }

  @Override
  protected void fill_list() {
    app.freerouting.interactive.BoardHandling board_handling =
        this.board_frame.board_panel.board_handling;

    ClearanceViolations clearance_violations =
        new ClearanceViolations(board_handling.get_routing_board().get_items());
    java.util.SortedSet<ViolationInfo> sorted_set = new java.util.TreeSet<ViolationInfo>();
    for (ClearanceViolation curr_violation : clearance_violations.list) {
      sorted_set.add(new ViolationInfo(curr_violation));
    }
    for (ViolationInfo curr_violation : sorted_set) {
      this.add_to_list(curr_violation);
    }
    this.list.setVisibleRowCount(Math.min(sorted_set.size(), DEFAULT_TABLE_SIZE));

    if (clearance_violations.global_smallest_clearance != Double.MAX_VALUE) {
      FRLogger.info(
          String.format(
              "The smallest clearance on the board is %.4f mm.",
              clearance_violations.global_smallest_clearance / 10000.0));
    }
  }

  @Override
  protected void select_instances() {
    List<Object> selected_violations = list.getSelectedValuesList();
    if (selected_violations.size() <= 0) {
      return;
    }
    java.util.Set<app.freerouting.board.Item> selected_items =
        new java.util.TreeSet<app.freerouting.board.Item>();
    for (int i = 0; i < selected_violations.size(); ++i) {
      ClearanceViolation curr_violation = ((ViolationInfo) selected_violations.get(i)).violation;
      selected_items.add(curr_violation.first_item);
      selected_items.add(curr_violation.second_item);
    }
    app.freerouting.interactive.BoardHandling board_handling =
        board_frame.board_panel.board_handling;
    board_handling.select_items(selected_items);
    board_handling.toggle_selected_item_violations();
    board_handling.zoom_selection();
  }

  private String item_info(app.freerouting.board.Item p_item) {
    String result;
    if (p_item instanceof app.freerouting.board.Pin) {
      result = resources.getString("pin");
    } else if (p_item instanceof app.freerouting.board.Via via) {
      app.freerouting.rules.Net curr_net = p_item.board.rules.nets.get(via.get_net_no(0));
      result = resources.getString("via") + " [" + curr_net.name + "]";
    } else if (p_item instanceof app.freerouting.board.Trace trace) {
      app.freerouting.rules.Net curr_net = p_item.board.rules.nets.get(trace.get_net_no(0));
      result = resources.getString("trace") + " [" + curr_net.name + "]";
    } else if (p_item instanceof app.freerouting.board.ConductionArea) {
      result = resources.getString("conduction_area");
    } else if (p_item instanceof app.freerouting.board.ObstacleArea) {
      result = resources.getString("keepout");
    } else if (p_item instanceof app.freerouting.board.ViaObstacleArea) {
      result = resources.getString("via_keepout");
    } else if (p_item instanceof app.freerouting.board.ComponentObstacleArea) {
      result = resources.getString("component_keepout");
    } else if (p_item instanceof app.freerouting.board.BoardOutline) {
      result = resources.getString("board_outline");
    } else {
      result = resources.getString("unknown");
    }
    return result;
  }

  private class ViolationInfo implements Comparable<ViolationInfo>, WindowObjectInfo.Printable {
    public final ClearanceViolation violation;
    public final FloatPoint location;
    public final double delta;

    public ViolationInfo(ClearanceViolation p_violation) {
      this.violation = p_violation;
      FloatPoint board_location = p_violation.shape.centre_of_gravity();
      this.location =
          board_frame.board_panel.board_handling.coordinate_transform.board_to_user(board_location);
      this.delta = (p_violation.expected_clearance - p_violation.actual_clearance) / 10000.0;
    }

    @Override
    public String toString() {
      app.freerouting.board.LayerStructure layer_structure =
          board_frame.board_panel.board_handling.get_routing_board().layer_structure;

      String clearance_violation_message_template = resources.getString("clearance_violation_message_template");
      return String.format(
          clearance_violation_message_template,
          delta,
          item_info(violation.first_item),
          item_info(violation.second_item),
          location.to_string(board_frame.get_locale()),
          layer_structure.arr[violation.layer].name
      );
    }

    @Override
    public void print_info(
        app.freerouting.board.ObjectInfoPanel p_window, java.util.Locale p_locale) {
      this.violation.print_info(p_window, p_locale);
    }

    @Override
    public int compareTo(ViolationInfo p_other) {
      if (this.delta > p_other.delta) {
        return -1;
      } else if (this.delta < p_other.delta) {
        return +1;
      }

      if (this.violation.layer < p_other.violation.layer) {
        return -1;
      } else if (this.violation.layer > p_other.violation.layer) {
        return +1;
      }

      return 0;
    }
  }
}

package app.freerouting.gui;

import app.freerouting.board.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.ClearanceViolations;
import java.util.List;

public class WindowClearanceViolations extends WindowObjectListWithFilter {

  private final java.util.ResourceBundle resources;

  /** Creates a new instance of IncompletesWindow */
  public WindowClearanceViolations(BoardFrame p_board_frame) {
    super(p_board_frame);
    this.resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.WindowClearanceViolations", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));
    this.list_empty_message.setText(resources.getString("list_empty_message"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_ClearanceViolations");
  }

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
  }

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
    } else if (p_item instanceof app.freerouting.board.Via) {
      result = resources.getString("via");
    } else if (p_item instanceof app.freerouting.board.Trace) {
      result = resources.getString("trace");
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

    public ViolationInfo(ClearanceViolation p_violation) {
      this.violation = p_violation;
      FloatPoint board_location = p_violation.shape.centre_of_gravity();
      this.location =
          board_frame.board_panel.board_handling.coordinate_transform.board_to_user(board_location);
    }

    public String toString() {
      app.freerouting.board.LayerStructure layer_structure =
          board_frame.board_panel.board_handling.get_routing_board().layer_structure;
      String result =
          item_info(violation.first_item)
              + " - "
              + item_info(violation.second_item)
              + " "
              + resources.getString("at")
              + " "
              + location.to_string(board_frame.get_locale())
              + " "
              + resources.getString("on_layer")
              + " "
              + layer_structure.arr[violation.layer].name;
      return result;
    }

    public void print_info(
        app.freerouting.board.ObjectInfoPanel p_window, java.util.Locale p_locale) {
      this.violation.print_info(p_window, p_locale);
    }

    public int compareTo(ViolationInfo p_other) {
      if (this.location.x > p_other.location.x) {
        return 1;
      }
      if (this.location.x < p_other.location.x) {
        return -1;
      }
      if (this.location.y > p_other.location.y) {
        return 1;
      }
      if (this.location.y < p_other.location.y) {
        return -1;
      }
      return this.violation.layer - p_other.violation.layer;
    }
  }
}

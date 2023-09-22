package app.freerouting.gui;

import app.freerouting.board.BoardOutline;
import app.freerouting.board.ClearanceViolation;
import app.freerouting.board.ComponentObstacleArea;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Item;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.Pin;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.board.ViaObstacleArea;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.interactive.ClearanceViolations;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class WindowClearanceViolations extends WindowObjectListWithFilter {

  private final ResourceBundle resources;

  /** Creates a new instance of clearance violations window */
  public WindowClearanceViolations(BoardFrame p_board_frame) {
    super(p_board_frame);
    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowClearanceViolations", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));
    this.list_empty_message.setText(resources.getString("list_empty_message"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_ClearanceViolations");
  }

  @Override
  protected void fill_list() {
    BoardHandling board_handling =
        this.board_frame.board_panel.board_handling;

    ClearanceViolations clearance_violations =
        new ClearanceViolations(board_handling.get_routing_board().get_items());
    SortedSet<ViolationInfo> sorted_set = new TreeSet<>();
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
    if (selected_violations.isEmpty()) {
      return;
    }
    Set<Item> selected_items =
        new TreeSet<>();
    for (int i = 0; i < selected_violations.size(); ++i) {
      ClearanceViolation curr_violation = ((ViolationInfo) selected_violations.get(i)).violation;
      selected_items.add(curr_violation.first_item);
      selected_items.add(curr_violation.second_item);
    }
    BoardHandling board_handling =
        board_frame.board_panel.board_handling;
    board_handling.select_items(selected_items);
    board_handling.toggle_selected_item_violations();
    board_handling.zoom_selection();
  }

  private String item_info(Item p_item) {
    String result;
    if (p_item instanceof Pin) {
      result = resources.getString("pin");
    } else if (p_item instanceof Via via) {
      Net curr_net = p_item.board.rules.nets.get(via.get_net_no(0));
      result = resources.getString("via") + " [" + curr_net.name + "]";
    } else if (p_item instanceof Trace trace) {
      Net curr_net = p_item.board.rules.nets.get(trace.get_net_no(0));
      result = resources.getString("trace") + " [" + curr_net.name + "]";
    } else if (p_item instanceof ConductionArea) {
      result = resources.getString("conduction_area");
    } else if (p_item instanceof ObstacleArea) {
      result = resources.getString("keepout");
    } else if (p_item instanceof ViaObstacleArea) {
      result = resources.getString("via_keepout");
    } else if (p_item instanceof ComponentObstacleArea) {
      result = resources.getString("component_keepout");
    } else if (p_item instanceof BoardOutline) {
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
      LayerStructure layer_structure =
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
        ObjectInfoPanel p_window, Locale p_locale) {
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

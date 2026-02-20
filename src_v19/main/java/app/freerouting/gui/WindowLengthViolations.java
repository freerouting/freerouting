package app.freerouting.gui;

import app.freerouting.board.CoordinateTransform;
import app.freerouting.board.Item;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.interactive.RatsNest;
import app.freerouting.rules.Net;
import app.freerouting.rules.NetClass;
import app.freerouting.rules.Nets;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class WindowLengthViolations extends WindowObjectListWithFilter {

  private final ResourceBundle resources;

  /** Creates a new instance of WindowLengthViolations */
  public WindowLengthViolations(BoardFrame p_board_frame) {
    super(p_board_frame);
    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.WindowLengthViolations", p_board_frame.get_locale());
    this.setTitle(resources.getString("title"));
    this.list_empty_message.setText(resources.getString("list_empty"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_LengthViolations");
  }

  @Override
  protected void fill_list() {
    RatsNest ratsnest = this.board_frame.board_panel.board_handling.get_ratsnest();
    Nets net_list = this.board_frame.board_panel.board_handling.get_routing_board().rules.nets;
    SortedSet<LengthViolation> length_violations =
        new TreeSet<>();
    for (int net_index = 1; net_index <= net_list.max_net_no(); ++net_index) {
      double curr_violation_length = ratsnest.get_length_violation(net_index);
      if (curr_violation_length != 0) {
        LengthViolation curr_length_violation =
            new LengthViolation(net_list.get(net_index), curr_violation_length);
        length_violations.add(curr_length_violation);
      }
    }

    for (LengthViolation curr_violation : length_violations) {
      this.add_to_list(curr_violation);
    }
    this.list.setVisibleRowCount(Math.min(length_violations.size(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances() {
    List<Object> selected_violations = list.getSelectedValuesList();
    if (selected_violations.isEmpty()) {
      return;
    }
    Set<Item> selected_items = new TreeSet<>();
    for (int i = 0; i < selected_violations.size(); ++i) {
      LengthViolation curr_violation = ((LengthViolation) selected_violations.get(i));
      selected_items.addAll(curr_violation.net.get_items());
    }
    BoardHandling board_handling =
        board_frame.board_panel.board_handling;
    board_handling.select_items(selected_items);
    board_handling.zoom_selection();
  }

  private class LengthViolation implements Comparable<LengthViolation> {
    final Net net;
    final double violation_length;

    LengthViolation(Net p_net, double p_violation_length) {
      net = p_net;
      violation_length = p_violation_length;
    }

    @Override
    public int compareTo(LengthViolation p_other) {
      return this.net.name.compareToIgnoreCase(p_other.net.name);
    }

    @Override
    public String toString() {
      CoordinateTransform coordinate_transform =
          board_frame.board_panel.board_handling.coordinate_transform;
      NetClass net_class = this.net.get_class();
      float allowed_length;
      String allowed_string;
      if (violation_length > 0) {
        allowed_length =
            (float) coordinate_transform.board_to_user(net_class.get_maximum_trace_length());
        allowed_string = " " + resources.getString("maximum_allowed") + " ";
      } else {
        allowed_length =
            (float) coordinate_transform.board_to_user(net_class.get_minimum_trace_length());
        allowed_string = " " + resources.getString("minimum_allowed") + " ";
      }
      float length = (float) coordinate_transform.board_to_user(this.net.get_trace_length());
      return resources.getString("net")
              + " "
              + this.net.name
              + resources.getString("trace_length")
              + " "
              + length
              + allowed_string
              + allowed_length;
    }
  }
}

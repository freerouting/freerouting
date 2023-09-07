package app.freerouting.gui;

import app.freerouting.board.Item;
import app.freerouting.interactive.RatsNest;

import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

public class WindowIncompletes extends WindowObjectListWithFilter {

  /** Creates a new instance of IncompletesWindow */
  public WindowIncompletes(BoardFrame p_board_frame) {
    super(p_board_frame);
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    this.setTitle(resources.getString("incompletes"));
    this.list_empty_message.setText(resources.getString("route_completed"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_Incompletes");
  }

  /** Fills the list with the board incompletes. */
  @Override
  protected void fill_list() {
    RatsNest ratsnest = board_frame.board_panel.board_handling.get_ratsnest();
    RatsNest.AirLine[] sorted_arr = ratsnest.get_airlines();

    Arrays.sort(sorted_arr);
    for (int i = 0; i < sorted_arr.length; ++i) {
      this.add_to_list(sorted_arr[i]);
    }
    this.list.setVisibleRowCount(Math.min(sorted_arr.length, DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances() {
    List<Object> selected_incompletes = list.getSelectedValuesList();
    if (selected_incompletes.isEmpty()) {
      return;
    }
    Set<Item> selected_items =
        new TreeSet<>();
    for (int i = 0; i < selected_incompletes.size(); ++i) {
      RatsNest.AirLine curr_airline = (RatsNest.AirLine) selected_incompletes.get(i);
      selected_items.add(curr_airline.from_item);
      selected_items.add(curr_airline.to_item);
    }
    board_frame.board_panel.board_handling.select_items(selected_items);
    board_frame.board_panel.board_handling.zoom_selection();
  }
}

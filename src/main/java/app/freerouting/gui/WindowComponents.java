package app.freerouting.gui;

import app.freerouting.board.Component;
import app.freerouting.board.Components;
import java.util.List;

/** Window displaying the components on the board. */
public class WindowComponents extends WindowObjectListWithFilter {

  /** Creates a new instance of ComponentsWindow */
  public WindowComponents(BoardFrame p_board_frame) {
    super(p_board_frame);
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    this.setTitle(resources.getString("components"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_BoardComponents");
  }

  /** Fills the list with the board components. */
  protected void fill_list() {
    Components components =
        this.board_frame.board_panel.board_handling.get_routing_board().components;
    Component[] sorted_arr = new Component[components.count()];
    for (int i = 0; i < sorted_arr.length; ++i) {
      sorted_arr[i] = components.get(i + 1);
    }
    java.util.Arrays.sort(sorted_arr);
    for (int i = 0; i < sorted_arr.length; ++i) {
      this.add_to_list(sorted_arr[i]);
    }
    this.list.setVisibleRowCount(Math.min(components.count(), DEFAULT_TABLE_SIZE));
  }

  protected void select_instances() {
    List<Object> selected_components = list.getSelectedValuesList();
    if (selected_components.size() <= 0) {
      return;
    }
    app.freerouting.board.RoutingBoard routing_board =
        board_frame.board_panel.board_handling.get_routing_board();
    java.util.Set<app.freerouting.board.Item> selected_items =
        new java.util.TreeSet<app.freerouting.board.Item>();
    java.util.Collection<app.freerouting.board.Item> board_items = routing_board.get_items();
    for (app.freerouting.board.Item curr_item : board_items) {
      if (curr_item.get_component_no() > 0) {
        app.freerouting.board.Component curr_component =
            routing_board.components.get(curr_item.get_component_no());
        boolean component_matches = false;
        for (int i = 0; i < selected_components.size(); ++i) {
          if (curr_component == selected_components.get(i)) {
            component_matches = true;
            break;
          }
        }
        if (component_matches) {
          selected_items.add(curr_item);
        }
      }
    }
    board_frame.board_panel.board_handling.select_items(selected_items);
    board_frame.board_panel.board_handling.zoom_selection();
  }
}

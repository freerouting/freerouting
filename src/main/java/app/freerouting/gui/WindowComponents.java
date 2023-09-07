package app.freerouting.gui;

import app.freerouting.board.Component;
import app.freerouting.board.Components;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

/** Window displaying the components on the board. */
public class WindowComponents extends WindowObjectListWithFilter {

  /** Creates a new instance of ComponentsWindow */
  public WindowComponents(BoardFrame p_board_frame) {
    super(p_board_frame);
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    this.setTitle(resources.getString("components"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_BoardComponents");
  }

  /** Fills the list with the board components. */
  @Override
  protected void fill_list() {
    Components components =
        this.board_frame.board_panel.board_handling.get_routing_board().components;
    Component[] sorted_arr = new Component[components.count()];
    for (int i = 0; i < sorted_arr.length; ++i) {
      sorted_arr[i] = components.get(i + 1);
    }
    Arrays.sort(sorted_arr);
    for (int i = 0; i < sorted_arr.length; ++i) {
      this.add_to_list(sorted_arr[i]);
    }
    this.list.setVisibleRowCount(Math.min(components.count(), DEFAULT_TABLE_SIZE));
  }

  @Override
  protected void select_instances() {
    List<Object> selected_components = list.getSelectedValuesList();
    if (selected_components.isEmpty()) {
      return;
    }
    RoutingBoard routing_board =
        board_frame.board_panel.board_handling.get_routing_board();
    Set<Item> selected_items =
        new TreeSet<>();
    Collection<Item> board_items = routing_board.get_items();
    for (Item curr_item : board_items) {
      if (curr_item.get_component_no() > 0) {
        Component curr_component =
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

package app.freerouting.gui;

import app.freerouting.library.Package;
import app.freerouting.library.Packages;
import java.util.List;

/** Window displaying the library packages. */
public class WindowPackages extends WindowObjectListWithFilter {

  /** Creates a new instance of PackagesWindow */
  public WindowPackages(BoardFrame p_board_frame) {
    super(p_board_frame);
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    this.setTitle(resources.getString("packages"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_LibraryPackages");
  }

  /** Fills the list with the library packages. */
  protected void fill_list() {
    Packages packages =
        this.board_frame.board_panel.board_handling.get_routing_board().library.packages;
    Package[] sorted_arr = new Package[packages.count()];
    for (int i = 0; i < sorted_arr.length; ++i) {
      sorted_arr[i] = packages.get(i + 1);
    }
    java.util.Arrays.sort(sorted_arr);
    for (int i = 0; i < sorted_arr.length; ++i) {
      this.add_to_list(sorted_arr[i]);
    }
    this.list.setVisibleRowCount(Math.min(packages.count(), DEFAULT_TABLE_SIZE));
  }

  protected void select_instances() {
    List<Object> selected_packages = list.getSelectedValuesList();
    if (selected_packages.size() <= 0) {
      return;
    }
    app.freerouting.board.RoutingBoard routing_board =
        board_frame.board_panel.board_handling.get_routing_board();
    java.util.Set<app.freerouting.board.Item> board_instances =
        new java.util.TreeSet<app.freerouting.board.Item>();
    java.util.Collection<app.freerouting.board.Item> board_items = routing_board.get_items();
    for (app.freerouting.board.Item curr_item : board_items) {
      if (curr_item.get_component_no() > 0) {
        app.freerouting.board.Component curr_component =
            routing_board.components.get(curr_item.get_component_no());
        Package curr_package = curr_component.get_package();
        boolean package_matches = false;
        for (int i = 0; i < selected_packages.size(); ++i) {
          if (curr_package == selected_packages.get(i)) {
            package_matches = true;
            break;
          }
        }
        if (package_matches) {
          board_instances.add(curr_item);
        }
      }
    }
    board_frame.board_panel.board_handling.select_items(board_instances);
    board_frame.board_panel.board_handling.zoom_selection();
  }
}

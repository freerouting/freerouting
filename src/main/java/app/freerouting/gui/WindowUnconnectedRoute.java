package app.freerouting.gui;

import app.freerouting.board.Item;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

public class WindowUnconnectedRoute extends WindowObjectListWithFilter {

  private final java.util.ResourceBundle resources;
  private int max_unconnected_route_info_id_no = 0;

  /** Creates a new instance of WindowUnconnectedRoute */
  public WindowUnconnectedRoute(BoardFrame p_board_frame) {
    super(p_board_frame);
    this.resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.CleanupWindows", p_board_frame.get_locale());
    this.setTitle(resources.getString("unconnected_route"));
    this.list_empty_message.setText(resources.getString("no_unconnected_route_found"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_UnconnectedRoute");
  }

  protected void fill_list() {
    app.freerouting.board.BasicBoard routing_board =
        this.board_frame.board_panel.board_handling.get_routing_board();

    Set<Item> handled_items = new java.util.TreeSet<Item>();

    SortedSet<UnconnectedRouteInfo> unconnected_route_info_set =
        new java.util.TreeSet<UnconnectedRouteInfo>();

    Collection<Item> board_items = routing_board.get_items();
    for (Item curr_item : board_items) {
      if (!(curr_item instanceof app.freerouting.board.Trace
          || curr_item instanceof app.freerouting.board.Via)) {
        continue;
      }
      if (handled_items.contains(curr_item)) {
        continue;
      }
      Collection<Item> curr_connected_set = curr_item.get_connected_set(-1);
      boolean terminal_item_found = false;
      for (Item curr_connnected_item : curr_connected_set) {
        handled_items.add(curr_connnected_item);
        if (!(curr_connnected_item instanceof app.freerouting.board.Trace
            || curr_connnected_item instanceof app.freerouting.board.Via)) {
          terminal_item_found = true;
        }
      }
      if (!terminal_item_found) {
        // We have found unconnnected route
        if (curr_item.net_count() == 1) {
          app.freerouting.rules.Net curr_net =
              routing_board.rules.nets.get(curr_item.get_net_no(0));
          if (curr_net != null) {
            UnconnectedRouteInfo curr_unconnected_route_info =
                new UnconnectedRouteInfo(curr_net, curr_connected_set);
            unconnected_route_info_set.add(curr_unconnected_route_info);
          }
        } else {
          FRLogger.warn("WindowUnconnectedRoute.fill_list: net_count 1 expected");
        }
      }
    }

    for (UnconnectedRouteInfo curr_info : unconnected_route_info_set) {
      this.add_to_list(curr_info);
    }
    this.list.setVisibleRowCount(Math.min(unconnected_route_info_set.size(), DEFAULT_TABLE_SIZE));
  }

  protected void select_instances() {
    List<Object> selected_list_values = list.getSelectedValuesList();
    if (selected_list_values.size() <= 0) {
      return;
    }
    Set<app.freerouting.board.Item> selected_items =
        new java.util.TreeSet<app.freerouting.board.Item>();
    for (int i = 0; i < selected_list_values.size(); ++i) {
      selected_items.addAll(((UnconnectedRouteInfo) selected_list_values.get(i)).item_list);
    }
    app.freerouting.interactive.BoardHandling board_handling =
        board_frame.board_panel.board_handling;
    board_handling.select_items(selected_items);
    board_handling.zoom_selection();
  }

  /** Describes information of a connected set of unconnected traces and vias. */
  private class UnconnectedRouteInfo implements Comparable<UnconnectedRouteInfo> {
    private final app.freerouting.rules.Net net;
    private final Collection<Item> item_list;
    private final int id_no;
    private final Integer trace_count;
    private final Integer via_count;
    public UnconnectedRouteInfo(app.freerouting.rules.Net p_net, Collection<Item> p_item_list) {
      this.net = p_net;
      this.item_list = p_item_list;
      ++max_unconnected_route_info_id_no;
      this.id_no = max_unconnected_route_info_id_no;
      int curr_trace_count = 0;
      int curr_via_count = 0;
      for (Item curr_item : p_item_list) {
        if (curr_item instanceof app.freerouting.board.Trace) {
          ++curr_trace_count;
        } else if (curr_item instanceof app.freerouting.board.Via) {
          ++curr_via_count;
        }
      }
      this.trace_count = curr_trace_count;
      this.via_count = curr_via_count;
    }

    public String toString() {

      String result =
          resources.getString("net")
              + " "
              + this.net.name
              + ": "
              + resources.getString("trace_count")
              + " "
              + this.trace_count
              + ", "
              + resources.getString("via_count")
              + " "
              + this.via_count;

      return result;
    }

    public int compareTo(UnconnectedRouteInfo p_other) {
      int result = this.net.name.compareTo(p_other.net.name);
      if (result == 0) {
        result = this.id_no - p_other.id_no;
      }
      return result;
    }
  }
}

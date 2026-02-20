package app.freerouting.gui;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.interactive.BoardHandling;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;

import java.util.Collection;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class WindowUnconnectedRoute extends WindowObjectListWithFilter {

  private final ResourceBundle resources;
  private int max_unconnected_route_info_id_no = 0;

  /** Creates a new instance of WindowUnconnectedRoute */
  public WindowUnconnectedRoute(BoardFrame p_board_frame) {
    super(p_board_frame);
    this.resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.CleanupWindows", p_board_frame.get_locale());
    this.setTitle(resources.getString("unconnected_route"));
    this.list_empty_message.setText(resources.getString("no_unconnected_route_found"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_UnconnectedRoute");
  }

  @Override
  protected void fill_list() {
    BasicBoard routing_board =
        this.board_frame.board_panel.board_handling.get_routing_board();

    Set<Item> handled_items = new TreeSet<>();

    SortedSet<UnconnectedRouteInfo> unconnected_route_info_set =
        new TreeSet<>();

    Collection<Item> board_items = routing_board.get_items();
    for (Item curr_item : board_items) {
      if (!(curr_item instanceof Trace
          || curr_item instanceof Via)) {
        continue;
      }
      if (handled_items.contains(curr_item)) {
        continue;
      }
      Collection<Item> curr_connected_set = curr_item.get_connected_set(-1);
      boolean terminal_item_found = false;
      for (Item curr_connnected_item : curr_connected_set) {
        handled_items.add(curr_connnected_item);
        if (!(curr_connnected_item instanceof Trace
            || curr_connnected_item instanceof Via)) {
          terminal_item_found = true;
        }
      }
      if (!terminal_item_found) {
        // We have found unconnected route
        if (curr_item.net_count() == 1) {
          Net curr_net =
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

  @Override
  protected void select_instances() {
    List<Object> selected_list_values = list.getSelectedValuesList();
    if (selected_list_values.isEmpty()) {
      return;
    }
    Set<Item> selected_items =
        new TreeSet<>();
    for (int i = 0; i < selected_list_values.size(); ++i) {
      selected_items.addAll(((UnconnectedRouteInfo) selected_list_values.get(i)).item_list);
    }
    BoardHandling board_handling =
        board_frame.board_panel.board_handling;
    board_handling.select_items(selected_items);
    board_handling.zoom_selection();
  }

  /** Describes information of a connected set of unconnected traces and vias. */
  private class UnconnectedRouteInfo implements Comparable<UnconnectedRouteInfo> {
    private final Net net;
    private final Collection<Item> item_list;
    private final int id_no;
    private final Integer trace_count;
    private final Integer via_count;
    public UnconnectedRouteInfo(Net p_net, Collection<Item> p_item_list) {
      this.net = p_net;
      this.item_list = p_item_list;
      ++max_unconnected_route_info_id_no;
      this.id_no = max_unconnected_route_info_id_no;
      int curr_trace_count = 0;
      int curr_via_count = 0;
      for (Item curr_item : p_item_list) {
        if (curr_item instanceof Trace) {
          ++curr_trace_count;
        } else if (curr_item instanceof Via) {
          ++curr_via_count;
        }
      }
      this.trace_count = curr_trace_count;
      this.via_count = curr_via_count;
    }

    @Override
    public String toString() {
      return resources.getString("net")
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
    }

    @Override
    public int compareTo(UnconnectedRouteInfo p_other) {
      int result = this.net.name.compareTo(p_other.net.name);
      if (result == 0) {
        result = this.id_no - p_other.id_no;
      }
      return result;
    }
  }
}

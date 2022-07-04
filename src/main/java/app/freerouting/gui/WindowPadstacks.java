package app.freerouting.gui;

import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.library.Padstack;
import app.freerouting.library.Padstacks;
import java.util.List;

/** Window displaying the library padstacks. */
public class WindowPadstacks extends WindowObjectListWithFilter {

  /** Creates a new instance of PadstacksWindow */
  public WindowPadstacks(BoardFrame p_board_frame) {
    super(p_board_frame);
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    this.setTitle(resources.getString("padstacks"));
    p_board_frame.set_context_sensitive_help(this, "WindowObjectList_LibraryPadstacks");
  }

  /** Fills the list with the library padstacks. */
  protected void fill_list() {
    Padstacks padstacks =
        this.board_frame.board_panel.board_handling.get_routing_board().library.padstacks;
    Padstack[] sorted_arr = new Padstack[padstacks.count()];
    for (int i = 0; i < sorted_arr.length; ++i) {
      sorted_arr[i] = padstacks.get(i + 1);
    }
    java.util.Arrays.sort(sorted_arr);
    for (int i = 0; i < sorted_arr.length; ++i) {
      this.add_to_list(sorted_arr[i]);
    }
    this.list.setVisibleRowCount(Math.min(padstacks.count(), DEFAULT_TABLE_SIZE));
  }

  protected void select_instances() {
    List<Object> selected_padstacks = list.getSelectedValuesList();
    if (selected_padstacks.size() <= 0) {
      return;
    }
    java.util.Collection<Padstack> padstack_list = new java.util.LinkedList<Padstack>();
    for (int i = 0; i < selected_padstacks.size(); ++i) {
      padstack_list.add((Padstack) selected_padstacks.get(i));
    }
    app.freerouting.board.RoutingBoard routing_board =
        board_frame.board_panel.board_handling.get_routing_board();
    java.util.Set<app.freerouting.board.Item> board_instances =
        new java.util.TreeSet<app.freerouting.board.Item>();
    java.util.Iterator<UndoableObjects.UndoableObjectNode> it =
        routing_board.item_list.start_read_object();
    for (; ; ) {
      app.freerouting.datastructures.UndoableObjects.Storable curr_object =
          routing_board.item_list.read_object(it);
      if (curr_object == null) {
        break;
      }
      if (curr_object instanceof app.freerouting.board.DrillItem) {
        app.freerouting.library.Padstack curr_padstack =
            ((app.freerouting.board.DrillItem) curr_object).get_padstack();
        for (Padstack curr_selected_padstack : padstack_list) {
          if (curr_padstack == curr_selected_padstack) {
            board_instances.add((app.freerouting.board.Item) curr_object);
            break;
          }
        }
      }
    }
    board_frame.board_panel.board_handling.select_items(board_instances);
    board_frame.board_panel.board_handling.zoom_selection();
  }
}

package app.freerouting.gui;

import app.freerouting.board.LayerStructure;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.ResourceBundle;

/** Used as submenu in a popup menu for change layer actions. */
class PopupMenuChangeLayer extends JMenu {

  private final BoardFrame board_frame;
  private final LayermenuItem[] item_arr;

  /** Creates a new instance of ChangeLayerMenu */
  PopupMenuChangeLayer(BoardFrame p_board_frame) {
    this.board_frame = p_board_frame;

    LayerStructure layer_structure =
        board_frame.board_panel.board_handling.get_routing_board().layer_structure;
    this.item_arr = new LayermenuItem[layer_structure.signal_layer_count()];
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());

    this.setText(resources.getString("change_layer"));
    this.setToolTipText(resources.getString("change_layer_tooltip"));
    int curr_signal_layer_no = 0;
    for (int i = 0; i < layer_structure.arr.length; ++i) {
      if (layer_structure.arr[i].is_signal) {
        this.item_arr[curr_signal_layer_no] = new LayermenuItem(i);
        this.item_arr[curr_signal_layer_no].setText(layer_structure.arr[i].name);
        this.add(this.item_arr[curr_signal_layer_no]);
        ++curr_signal_layer_no;
      }
    }
  }

  /** Disables the item with index p_no and enables all other items. */
  void disable_item(int p_no) {
    for (int i = 0; i < item_arr.length; ++i) {
      this.item_arr[i].setEnabled(i != p_no);
    }
  }

  private class LayermenuItem extends JMenuItem {
    private final int layer_no;
    private final String message1;
    LayermenuItem(int p_layer_no) {
      ResourceBundle resources =
          ResourceBundle.getBundle(
              "app.freerouting.gui.Default", board_frame.get_locale());
      message1 = resources.getString("layer_changed_to") + " ";
      layer_no = p_layer_no;
      addActionListener(
          evt -> {
            final BoardPanel board_panel = board_frame.board_panel;
            if (board_panel.board_handling.change_layer_action(layer_no)) {
              String layer_name =
                  board_panel.board_handling.get_routing_board()
                      .layer_structure
                      .arr[layer_no]
                      .name;
              board_panel.screen_messages.set_status_message(message1 + layer_name);
            }
            // If change_layer failed the status message is set inside change_layer_action
            // because the information of the cause of the failing is missing here.
            board_panel.move_mouse(board_panel.right_button_click_location);
          });
    }
  }
}

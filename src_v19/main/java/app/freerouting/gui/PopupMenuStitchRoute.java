package app.freerouting.gui;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;

import app.freerouting.management.FRAnalytics;
import javax.swing.JMenuItem;
import java.util.ResourceBundle;

public class PopupMenuStitchRoute extends PopupMenuDisplay {

  private final PopupMenuChangeLayer change_layer_menu;

  /** Creates a new instance of PopupMenuStitchRoute */
  public PopupMenuStitchRoute(BoardFrame p_board_frame) {
    super(p_board_frame);
    LayerStructure layer_structure =
        board_panel.board_handling.get_routing_board().layer_structure;

    if (layer_structure.arr.length > 0) {
      change_layer_menu = new PopupMenuChangeLayer(p_board_frame);
      this.add(change_layer_menu, 0);
    } else {
      change_layer_menu = null;
    }
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    JMenuItem popup_insert_menuitem = new JMenuItem();
    popup_insert_menuitem.setText(resources.getString("insert"));
    popup_insert_menuitem.addActionListener(evt -> board_panel.board_handling.left_button_clicked(board_panel.right_button_click_location));
    popup_insert_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_insert_menuitem", popup_insert_menuitem.getText()));

    this.add(popup_insert_menuitem, 0);

    JMenuItem popup_done_menuitem = new JMenuItem();
    popup_done_menuitem.setText(resources.getString("done"));
    popup_done_menuitem.addActionListener(evt -> board_panel.board_handling.return_from_state());
    popup_done_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_done_menuitem", popup_done_menuitem.getText()));

    this.add(popup_done_menuitem, 1);

    JMenuItem popup_cancel_menuitem = new JMenuItem();
    popup_cancel_menuitem.setText(resources.getString("cancel"));
    popup_cancel_menuitem.addActionListener(evt -> board_panel.board_handling.cancel_state());
    popup_cancel_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_cancel_menuitem", popup_cancel_menuitem.getText()));

    this.add(popup_cancel_menuitem, 2);

    Layer curr_layer =
        layer_structure.arr[board_panel.board_handling.settings.get_layer()];
    disable_layer_item(layer_structure.get_signal_layer_no(curr_layer));
  }

  /** Disables the p_no-th item in the change_layer_menu. */
  void disable_layer_item(int p_no) {
    if (this.change_layer_menu != null) {
      this.change_layer_menu.disable_item(p_no);
    }
  }
}

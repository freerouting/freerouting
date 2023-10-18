package app.freerouting.gui;

import app.freerouting.board.TestLevel;

import app.freerouting.management.FRAnalytics;
import javax.swing.JMenuItem;
import java.util.ResourceBundle;

/** Popup menu used in the interactive selected item state. */
class PopupMenuSelectedItems extends PopupMenuDisplay {

  /** Creates a new instance of SelectedItemPopupMenu */
  PopupMenuSelectedItems(BoardFrame p_board_frame) {
    super(p_board_frame);
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    JMenuItem popup_copy_menuitem = new JMenuItem();
    popup_copy_menuitem.setText(resources.getString("copy"));
    popup_copy_menuitem.addActionListener(evt -> board_panel.board_handling.copy_selected_items(board_panel.right_button_click_location));
    popup_copy_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_copy_menuitem", popup_copy_menuitem.getText()));

    if (board_panel.board_handling.get_routing_board().get_test_level()
        != TestLevel.RELEASE_VERSION) {
      this.add(popup_copy_menuitem);
    }

    JMenuItem popup_move_menuitem = new JMenuItem();
    popup_move_menuitem.setText(resources.getString("move"));
    popup_move_menuitem.addActionListener(evt -> board_panel.board_handling.move_selected_items(board_panel.right_button_click_location));
    popup_move_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_move_menuitem", popup_move_menuitem.getText()));

    this.add(popup_move_menuitem, 0);
  }
}

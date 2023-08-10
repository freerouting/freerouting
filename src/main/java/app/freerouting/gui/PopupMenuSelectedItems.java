package app.freerouting.gui;

import app.freerouting.board.TestLevel;

import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Popup menu used in the interactive selected item state.. */
class PopupMenuSelectedItems extends PopupMenuDisplay {

  /** Creates a new instance of SelectedItemPopupMenu */
  PopupMenuSelectedItems(BoardFrame p_board_frame) {
    super(p_board_frame);
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    JMenuItem copy_item = new JMenuItem();
    copy_item.setText(resources.getString("copy"));
    copy_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            board_panel.board_handling.copy_selected_items(board_panel.right_button_click_location);
          }
        });

    if (board_panel.board_handling.get_routing_board().get_test_level()
        != TestLevel.RELEASE_VERSION) {
      this.add(copy_item);
    }

    JMenuItem move_item = new JMenuItem();
    move_item.setText(resources.getString("move"));
    move_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            board_panel.board_handling.move_selected_items(board_panel.right_button_click_location);
          }
        });

    this.add(move_item, 0);
  }
}

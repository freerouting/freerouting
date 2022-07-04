package app.freerouting.gui;

/** Popup menu used in the interactive selected item state.. */
class PopupMenuSelectedItems extends PopupMenuDisplay {

  /** Creates a new instance of SelectedItemPopupMenu */
  PopupMenuSelectedItems(BoardFrame p_board_frame) {
    super(p_board_frame);
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    javax.swing.JMenuItem copy_item = new javax.swing.JMenuItem();
    copy_item.setText(resources.getString("copy"));
    copy_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.board_handling.copy_selected_items(board_panel.right_button_click_location);
          }
        });

    if (board_panel.board_handling.get_routing_board().get_test_level()
        != app.freerouting.board.TestLevel.RELEASE_VERSION) {
      this.add(copy_item);
    }

    javax.swing.JMenuItem move_item = new javax.swing.JMenuItem();
    move_item.setText(resources.getString("move"));
    move_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.board_handling.move_selected_items(board_panel.right_button_click_location);
          }
        });

    this.add(move_item, 0);
  }
}

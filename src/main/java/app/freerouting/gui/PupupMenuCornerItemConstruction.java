package app.freerouting.gui;

/** Popup menu used while constructing a cornered shape. */
class PupupMenuCornerItemConstruction extends javax.swing.JPopupMenu {

  private final BoardPanel board_panel;

  /** Creates a new instance of CornerItemConstructionPopupMenu */
  PupupMenuCornerItemConstruction(BoardFrame p_board_frame) {
    this.board_panel = p_board_frame.board_panel;
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    javax.swing.JMenuItem add_corner_item = new javax.swing.JMenuItem();
    add_corner_item.setText(resources.getString("add_corner"));
    add_corner_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            // Same action as if the left button is clicked with
            // the current mouse coordinates in this situation
            // because the left button is a short cut for this action.
            board_panel.board_handling.left_button_clicked(board_panel.right_button_click_location);
          }
        });

    this.add(add_corner_item);

    javax.swing.JMenuItem close_item = new javax.swing.JMenuItem();
    close_item.setText(resources.getString("close"));
    close_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.board_handling.return_from_state();
          }
        });

    this.add(close_item);

    javax.swing.JMenuItem cancel_item = new javax.swing.JMenuItem();
    cancel_item.setText(resources.getString("cancel"));
    cancel_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.board_handling.cancel_state();
          }
        });

    this.add(cancel_item);
  }
}

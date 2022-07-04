package app.freerouting.gui;

/** Popup menu containing the 2 items complete and cancel. */
class PopupMenuInsertCancel extends javax.swing.JPopupMenu {

  private final BoardPanel board_panel;

  /** Creates a new instance of CompleteCancelPopupMenu */
  PopupMenuInsertCancel(BoardFrame p_board_frame) {
    this.board_panel = p_board_frame.board_panel;
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    javax.swing.JMenuItem insert_item = new javax.swing.JMenuItem();
    insert_item.setText(resources.getString("insert"));
    insert_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.board_handling.return_from_state();
          }
        });

    this.add(insert_item);

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

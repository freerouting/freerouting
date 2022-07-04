package app.freerouting.gui;

public class BoardMenuOther extends javax.swing.JMenu {
  private final BoardFrame board_frame;
  private final java.util.ResourceBundle resources;

  /** Creates a new instance of BoardMenuOther */
  private BoardMenuOther(BoardFrame p_board_frame) {
    board_frame = p_board_frame;
    resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuOther", p_board_frame.get_locale());
  }

  /** Returns a new other menu for the board frame. */
  public static BoardMenuOther get_instance(BoardFrame p_board_frame) {
    final BoardMenuOther other_menu = new BoardMenuOther(p_board_frame);

    other_menu.setText(other_menu.resources.getString("other"));

    javax.swing.JMenuItem snapshots = new javax.swing.JMenuItem();
    snapshots.setText(other_menu.resources.getString("snapshots"));
    snapshots.setToolTipText(other_menu.resources.getString("snapshots_tooltip"));
    snapshots.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            other_menu.board_frame.snapshot_window.setVisible(true);
          }
        });

    other_menu.add(snapshots);

    return other_menu;
  }
}

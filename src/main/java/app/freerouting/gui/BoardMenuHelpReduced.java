package app.freerouting.gui;

public class BoardMenuHelpReduced extends javax.swing.JMenu {
  protected final BoardFrame board_frame;
  protected final java.util.ResourceBundle resources;
  /**
   * Creates a new instance of BoardMenuHelpReduced Separated from BoardMenuHelp to avoid
   * ClassNotFound exception when the library jh.jar is not found, which is only used in the
   * extended help menu.
   */
  public BoardMenuHelpReduced(BoardFrame p_board_frame) {
    this.board_frame = p_board_frame;
    this.resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuHelp", p_board_frame.get_locale());
    this.setText(this.resources.getString("help"));

    javax.swing.JMenuItem about_window = new javax.swing.JMenuItem();
    about_window.setText(this.resources.getString("about"));
    about_window.addActionListener(
        (java.awt.event.ActionEvent evt) -> {
          board_frame.about_window.setVisible(true);
        });
    this.add(about_window);
  }
}

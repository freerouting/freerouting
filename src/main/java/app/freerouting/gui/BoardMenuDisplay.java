package app.freerouting.gui;

/** Creates the display menu of a board frame. */
public class BoardMenuDisplay extends javax.swing.JMenu {
  private final BoardFrame board_frame;
  private final java.util.ResourceBundle resources;

  /** Creates a new instance of BoardDisplayMenu */
  private BoardMenuDisplay(BoardFrame p_board_frame) {
    board_frame = p_board_frame;
    resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuDisplay", p_board_frame.get_locale());
  }

  /** Returns a new display menu for the board frame. */
  public static BoardMenuDisplay get_instance(BoardFrame p_board_frame) {
    final BoardMenuDisplay display_menu = new BoardMenuDisplay(p_board_frame);
    display_menu.setText(display_menu.resources.getString("display"));

    javax.swing.JMenuItem itemvisibility = new javax.swing.JMenuItem();
    itemvisibility.setText(display_menu.resources.getString("object_visibility"));
    itemvisibility.setToolTipText(display_menu.resources.getString("object_visibility_tooltip"));
    itemvisibility.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            display_menu.board_frame.object_visibility_window.setVisible(true);
          }
        });

    display_menu.add(itemvisibility);

    javax.swing.JMenuItem layervisibility = new javax.swing.JMenuItem();
    layervisibility.setText(display_menu.resources.getString("layer_visibility"));
    layervisibility.setToolTipText(display_menu.resources.getString("layer_visibility_tooltip"));
    layervisibility.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            display_menu.board_frame.layer_visibility_window.setVisible(true);
          }
        });

    display_menu.add(layervisibility);

    javax.swing.JMenuItem colors = new javax.swing.JMenuItem();
    colors.setText(display_menu.resources.getString("colors"));
    colors.setToolTipText(display_menu.resources.getString("colors_tooltip"));
    colors.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            display_menu.board_frame.color_manager.setVisible(true);
          }
        });

    display_menu.add(colors);

    javax.swing.JMenuItem miscellaneous = new javax.swing.JMenuItem();
    miscellaneous.setText(display_menu.resources.getString("miscellaneous"));
    miscellaneous.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            display_menu.board_frame.display_misc_window.setVisible(true);
          }
        });

    display_menu.add(miscellaneous);

    return display_menu;
  }
}

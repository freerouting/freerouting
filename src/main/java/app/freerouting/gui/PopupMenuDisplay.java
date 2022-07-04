package app.freerouting.gui;

public class PopupMenuDisplay extends javax.swing.JPopupMenu {

  protected final BoardPanel board_panel;

  /** Creates a new instance of PopupMenuDisplay */
  public PopupMenuDisplay(BoardFrame p_board_frame) {
    this.board_panel = p_board_frame.board_panel;
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    javax.swing.JMenuItem center_display_item = new javax.swing.JMenuItem();
    center_display_item.setText(resources.getString("center_display"));
    center_display_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.center_display(board_panel.right_button_click_location);
          }
        });

    this.add(center_display_item);

    javax.swing.JMenu zoom_menu = new javax.swing.JMenu();
    zoom_menu.setText(resources.getString("zoom"));

    javax.swing.JMenuItem zoom_in_item = new javax.swing.JMenuItem();
    zoom_in_item.setText(resources.getString("zoom_in"));
    zoom_in_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.zoom_in(board_panel.right_button_click_location);
          }
        });

    zoom_menu.add(zoom_in_item);

    javax.swing.JMenuItem zoom_out_item = new javax.swing.JMenuItem();
    zoom_out_item.setText(resources.getString("zoom_out"));
    zoom_out_item.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            board_panel.zoom_out(board_panel.right_button_click_location);
          }
        });

    zoom_menu.add(zoom_out_item);

    this.add(zoom_menu);
  }
}

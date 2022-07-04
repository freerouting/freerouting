package app.freerouting.gui;

/** Creates the parameter menu of a board frame. */
public class BoardMenuParameter extends javax.swing.JMenu {
  private final BoardFrame board_frame;
  private final java.util.ResourceBundle resources;

  /** Creates a new instance of BoardSelectMenu */
  private BoardMenuParameter(BoardFrame p_board_frame) {
    board_frame = p_board_frame;
    resources =
        java.util.ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuParameter", p_board_frame.get_locale());
  }

  /** Returns a new windows menu for the board frame. */
  public static BoardMenuParameter get_instance(BoardFrame p_board_frame) {
    final BoardMenuParameter parameter_menu = new BoardMenuParameter(p_board_frame);

    parameter_menu.setText(parameter_menu.resources.getString("parameter"));

    javax.swing.JMenuItem selectwindow = new javax.swing.JMenuItem();
    selectwindow.setText(parameter_menu.resources.getString("select"));
    selectwindow.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            parameter_menu.board_frame.select_parameter_window.setVisible(true);
          }
        });

    parameter_menu.add(selectwindow);

    javax.swing.JMenuItem routewindow = new javax.swing.JMenuItem();
    routewindow.setText(parameter_menu.resources.getString("route"));
    routewindow.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            parameter_menu.board_frame.route_parameter_window.setVisible(true);
          }
        });

    parameter_menu.add(routewindow);

    javax.swing.JMenuItem autoroutewindow = new javax.swing.JMenuItem();
    autoroutewindow.setText(parameter_menu.resources.getString("autoroute"));
    autoroutewindow.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            parameter_menu.board_frame.autoroute_parameter_window.setVisible(true);
          }
        });

    parameter_menu.add(autoroutewindow);

    javax.swing.JMenuItem movewindow = new javax.swing.JMenuItem();
    movewindow.setText(parameter_menu.resources.getString("move"));
    movewindow.addActionListener(
        new java.awt.event.ActionListener() {
          public void actionPerformed(java.awt.event.ActionEvent evt) {
            parameter_menu.board_frame.move_parameter_window.setVisible(true);
          }
        });

    parameter_menu.add(movewindow);

    return parameter_menu;
  }
}

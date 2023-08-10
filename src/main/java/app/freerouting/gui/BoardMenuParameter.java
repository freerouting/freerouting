package app.freerouting.gui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Creates the parameter menu of a board frame. */
public class BoardMenuParameter extends JMenu {
  private final BoardFrame board_frame;
  private final ResourceBundle resources;

  /** Creates a new instance of BoardSelectMenu */
  private BoardMenuParameter(BoardFrame p_board_frame) {
    board_frame = p_board_frame;
    resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuParameter", p_board_frame.get_locale());
  }

  /** Returns a new windows menu for the board frame. */
  public static BoardMenuParameter get_instance(BoardFrame p_board_frame) {
    final BoardMenuParameter parameter_menu = new BoardMenuParameter(p_board_frame);

    parameter_menu.setText(parameter_menu.resources.getString("parameter"));

    JMenuItem selectwindow = new JMenuItem();
    selectwindow.setText(parameter_menu.resources.getString("select"));
    selectwindow.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            parameter_menu.board_frame.select_parameter_window.setVisible(true);
          }
        });

    parameter_menu.add(selectwindow);

    JMenuItem routewindow = new JMenuItem();
    routewindow.setText(parameter_menu.resources.getString("route"));
    routewindow.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            parameter_menu.board_frame.route_parameter_window.setVisible(true);
          }
        });

    parameter_menu.add(routewindow);

    JMenuItem autoroutewindow = new JMenuItem();
    autoroutewindow.setText(parameter_menu.resources.getString("autoroute"));
    autoroutewindow.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            parameter_menu.board_frame.autoroute_parameter_window.setVisible(true);
          }
        });

    parameter_menu.add(autoroutewindow);

    JMenuItem movewindow = new JMenuItem();
    movewindow.setText(parameter_menu.resources.getString("move"));
    movewindow.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            parameter_menu.board_frame.move_parameter_window.setVisible(true);
          }
        });

    parameter_menu.add(movewindow);

    return parameter_menu;
  }
}

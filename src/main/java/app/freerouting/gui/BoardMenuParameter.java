package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import app.freerouting.management.TextManager;

import javax.swing.*;

/**
 * Creates the parameter menu of a board frame.
 */
public class BoardMenuParameter extends JMenu
{
  private final BoardFrame board_frame;
  private final TextManager tm;

  /**
   * Creates a new instance of BoardSelectMenu
   */
  private BoardMenuParameter(BoardFrame p_board_frame)
  {
    board_frame = p_board_frame;
    tm = new TextManager(this.getClass(), p_board_frame.get_locale());
  }

  /**
   * Returns a new windows menu for the board frame.
   */
  public static BoardMenuParameter get_instance(BoardFrame p_board_frame)
  {
    final BoardMenuParameter parameter_menu = new BoardMenuParameter(p_board_frame);

    parameter_menu.setText(parameter_menu.tm.getText("parameter"));

    JMenuItem settings_selection_menuitem = new JMenuItem();
    settings_selection_menuitem.setText(parameter_menu.tm.getText("select"));
    settings_selection_menuitem.addActionListener(evt -> parameter_menu.board_frame.select_parameter_window.setVisible(true));
    settings_selection_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("settings_selection_menuitem", settings_selection_menuitem.getText()));

    parameter_menu.add(settings_selection_menuitem);

    JMenuItem settings_routing_menuitem = new JMenuItem();
    settings_routing_menuitem.setText(parameter_menu.tm.getText("route"));
    settings_routing_menuitem.addActionListener(evt -> parameter_menu.board_frame.route_parameter_window.setVisible(true));
    settings_routing_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("settings_routing_menuitem", settings_routing_menuitem.getText()));

    parameter_menu.add(settings_routing_menuitem);

    JMenuItem settings_autorouter_menuitem = new JMenuItem();
    settings_autorouter_menuitem.setText(parameter_menu.tm.getText("autoroute"));
    settings_autorouter_menuitem.addActionListener(evt -> parameter_menu.board_frame.autoroute_parameter_window.setVisible(true));
    settings_autorouter_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("settings_autorouter_menuitem", settings_autorouter_menuitem.getText()));

    parameter_menu.add(settings_autorouter_menuitem);

    JMenuItem settings_controls_menuitem = new JMenuItem();
    settings_controls_menuitem.setText(parameter_menu.tm.getText("move"));
    settings_controls_menuitem.addActionListener(evt -> parameter_menu.board_frame.move_parameter_window.setVisible(true));
    settings_controls_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("settings_controls_menuitem", settings_controls_menuitem.getText()));

    parameter_menu.add(settings_controls_menuitem);

    return parameter_menu;
  }
}
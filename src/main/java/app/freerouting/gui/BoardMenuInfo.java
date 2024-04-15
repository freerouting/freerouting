package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import app.freerouting.management.TextManager;

import javax.swing.*;

public class BoardMenuInfo extends JMenu
{
  private final BoardFrame board_frame;
  private final TextManager tm;

  /**
   * Creates a new instance of BoardLibraryMenu
   */
  private BoardMenuInfo(BoardFrame p_board_frame)
  {
    board_frame = p_board_frame;
    tm = new TextManager(this.getClass(), p_board_frame.get_locale());
  }

  /**
   * Returns a new info menu for the board frame.
   */
  public static BoardMenuInfo get_instance(BoardFrame p_board_frame)
  {
    final BoardMenuInfo info_menu = new BoardMenuInfo(p_board_frame);

    info_menu.setText(info_menu.tm.getText("info"));

    JMenuItem info_packages_menuitem = new JMenuItem();
    info_packages_menuitem.setText(info_menu.tm.getText("library_packages"));
    info_packages_menuitem.addActionListener(evt -> info_menu.board_frame.packages_window.setVisible(true));
    info_packages_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("info_packages_menuitem", info_packages_menuitem.getText()));
    info_menu.add(info_packages_menuitem);

    JMenuItem info_padstacks_menuitem = new JMenuItem();
    info_padstacks_menuitem.setText(info_menu.tm.getText("library_padstacks"));
    info_padstacks_menuitem.addActionListener(evt -> info_menu.board_frame.padstacks_window.setVisible(true));
    info_padstacks_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("info_padstacks_menuitem", info_padstacks_menuitem.getText()));
    info_menu.add(info_padstacks_menuitem);

    JMenuItem info_components_menuitem = new JMenuItem();
    info_components_menuitem.setText(info_menu.tm.getText("board_components"));
    info_components_menuitem.addActionListener(evt -> info_menu.board_frame.components_window.setVisible(true));
    info_components_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("info_components_menuitem", info_components_menuitem.getText()));
    info_menu.add(info_components_menuitem);

    JMenuItem info_incompletes_menuitem = new JMenuItem();
    info_incompletes_menuitem.setText(info_menu.tm.getText("incompletes"));
    info_incompletes_menuitem.addActionListener(evt -> info_menu.board_frame.incompletes_window.setVisible(true));
    info_incompletes_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("info_incompletes_menuitem", info_incompletes_menuitem.getText()));
    info_menu.add(info_incompletes_menuitem);

    JMenuItem info_length_violations_menuitem = new JMenuItem();
    info_length_violations_menuitem.setText(info_menu.tm.getText("length_violations"));
    info_length_violations_menuitem.addActionListener(evt -> info_menu.board_frame.length_violations_window.setVisible(true));
    info_length_violations_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("info_length_violations_menuitem", info_length_violations_menuitem.getText()));
    info_menu.add(info_length_violations_menuitem);

    JMenuItem info_clearance_violations_menuitem = new JMenuItem();
    info_clearance_violations_menuitem.setText(info_menu.tm.getText("clearance_violations"));
    info_clearance_violations_menuitem.addActionListener(evt -> info_menu.board_frame.clearance_violations_window.setVisible(true));
    info_clearance_violations_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("info_clearance_violations_menuitem", info_clearance_violations_menuitem.getText()));
    info_menu.add(info_clearance_violations_menuitem);

    JMenuItem info_unconnected_routes_menuitem = new JMenuItem();
    info_unconnected_routes_menuitem.setText(info_menu.tm.getText("unconnected_route"));
    info_unconnected_routes_menuitem.addActionListener(evt -> info_menu.board_frame.unconnected_route_window.setVisible(true));
    info_unconnected_routes_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("info_unconnected_routes_menuitem", info_unconnected_routes_menuitem.getText()));
    info_menu.add(info_unconnected_routes_menuitem);

    JMenuItem info_route_stubs_menuitem = new JMenuItem();
    info_route_stubs_menuitem.setText(info_menu.tm.getText("route_stubs"));
    info_route_stubs_menuitem.addActionListener(evt -> info_menu.board_frame.route_stubs_window.setVisible(true));
    info_route_stubs_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("info_route_stubs_menuitem", info_route_stubs_menuitem.getText()));
    info_menu.add(info_route_stubs_menuitem);

    return info_menu;
  }
}
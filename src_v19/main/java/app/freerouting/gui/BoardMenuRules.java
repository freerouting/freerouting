package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.ResourceBundle;

/** Creates the rules menu of a board frame. */
public class BoardMenuRules extends JMenu {

  private final BoardFrame board_frame;
  private final ResourceBundle resources;

  /** Creates a new instance of BoardRulesMenu */
  private BoardMenuRules(BoardFrame p_board_frame) {
    board_frame = p_board_frame;
    resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.BoardMenuRules", p_board_frame.get_locale());
  }

  /** Returns a new windows menu for the board frame. */
  public static BoardMenuRules get_instance(BoardFrame p_board_frame) {
    final BoardMenuRules rules_menu = new BoardMenuRules(p_board_frame);

    rules_menu.setText(rules_menu.resources.getString("rules"));

    JMenuItem rules_clearance_menuitem = new JMenuItem();
    rules_clearance_menuitem.setText(rules_menu.resources.getString("clearance_matrix"));
    rules_clearance_menuitem.addActionListener(evt -> rules_menu.board_frame.clearance_matrix_window.setVisible(true));
    rules_clearance_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("rules_clearance_menuitem", rules_clearance_menuitem.getText()));
    rules_menu.add(rules_clearance_menuitem);

    JMenuItem rules_vias_menuitem = new JMenuItem();
    rules_vias_menuitem.setText(rules_menu.resources.getString("vias"));
    rules_vias_menuitem.addActionListener(evt -> rules_menu.board_frame.via_window.setVisible(true));
    rules_vias_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("rules_vias_menuitem", rules_vias_menuitem.getText()));
    rules_menu.add(rules_vias_menuitem);

    JMenuItem rules_nets_menuitem = new JMenuItem();
    rules_nets_menuitem.setText(rules_menu.resources.getString("nets"));
    rules_nets_menuitem.addActionListener(evt -> rules_menu.board_frame.net_info_window.setVisible(true));
    rules_nets_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("rules_nets_menuitem", rules_nets_menuitem.getText()));
    rules_menu.add(rules_nets_menuitem);

    JMenuItem rules_net_class_menuitem = new JMenuItem();
    rules_net_class_menuitem.setText(rules_menu.resources.getString("net_classes"));
    rules_net_class_menuitem.addActionListener(evt -> rules_menu.board_frame.edit_net_rules_window.setVisible(true));
    rules_net_class_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("rules_net_class_menuitem", rules_net_class_menuitem.getText()));
    rules_menu.add(rules_net_class_menuitem);

    return rules_menu;
  }
}

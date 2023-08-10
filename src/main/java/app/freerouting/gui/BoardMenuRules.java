package app.freerouting.gui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    JMenuItem clearance_window = new JMenuItem();
    clearance_window.setText(rules_menu.resources.getString("clearance_matrix"));
    clearance_window.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            rules_menu.board_frame.clearance_matrix_window.setVisible(true);
          }
        });
    rules_menu.add(clearance_window);

    JMenuItem via_window = new JMenuItem();
    via_window.setText(rules_menu.resources.getString("vias"));
    via_window.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            rules_menu.board_frame.via_window.setVisible(true);
          }
        });
    rules_menu.add(via_window);

    JMenuItem nets_window = new JMenuItem();
    nets_window.setText(rules_menu.resources.getString("nets"));
    nets_window.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            rules_menu.board_frame.net_info_window.setVisible(true);
          }
        });

    rules_menu.add(nets_window);

    JMenuItem net_class_window = new JMenuItem();
    net_class_window.setText(rules_menu.resources.getString("net_classes"));
    net_class_window.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            rules_menu.board_frame.edit_net_rules_window.setVisible(true);
          }
        });
    rules_menu.add(net_class_window);

    return rules_menu;
  }
}

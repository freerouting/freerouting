package app.freerouting.gui;

import app.freerouting.interactive.InteractiveState;
import app.freerouting.interactive.MoveItemState;

import app.freerouting.management.FRAnalytics;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.util.ResourceBundle;

public class PopupMenuMove extends PopupMenuDisplay {

  /** Creates a new instance of PopupMenuMove */
  public PopupMenuMove(BoardFrame p_board_frame) {
    super(p_board_frame);
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.PopupMenuMove", p_board_frame.get_locale());

    // Add menu for turning the items by a multiple of 90 degree

    JMenuItem rotate_menu = new JMenu();
    rotate_menu.setText(resources.getString("turn"));
    this.add(rotate_menu, 0);

    JMenuItem popup_turn_90_menuitem = new JMenuItem();
    popup_turn_90_menuitem.setText(resources.getString("90_degree"));
    popup_turn_90_menuitem.addActionListener(evt -> turn_45_degree(2));
    popup_turn_90_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_turn_90_menuitem", popup_turn_90_menuitem.getText()));
    rotate_menu.add(popup_turn_90_menuitem);

    JMenuItem popup_turn_180_menuitem = new JMenuItem();
    popup_turn_180_menuitem.setText(resources.getString("180_degree"));
    popup_turn_180_menuitem.addActionListener(evt -> turn_45_degree(4));
    popup_turn_180_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_turn_180_menuitem", popup_turn_180_menuitem.getText()));
    rotate_menu.add(popup_turn_180_menuitem);

    JMenuItem popup_turn_270_menuitem = new JMenuItem();
    popup_turn_270_menuitem.setText(resources.getString("-90_degree"));
    popup_turn_270_menuitem.addActionListener(evt -> turn_45_degree(6));
    popup_turn_270_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_turn_270_menuitem", popup_turn_270_menuitem.getText()));
    rotate_menu.add(popup_turn_270_menuitem);

    JMenuItem popup_turn_45_menuitem = new JMenuItem();
    popup_turn_45_menuitem.setText(resources.getString("45_degree"));
    popup_turn_45_menuitem.addActionListener(evt -> turn_45_degree(1));
    popup_turn_45_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_turn_45_menuitem", popup_turn_45_menuitem.getText()));
    rotate_menu.add(popup_turn_45_menuitem);

    JMenuItem popup_turn_135_menuitem = new JMenuItem();
    popup_turn_135_menuitem.setText(resources.getString("135_degree"));
    popup_turn_135_menuitem.addActionListener(evt -> turn_45_degree(3));
    popup_turn_135_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_turn_135_menuitem", popup_turn_135_menuitem.getText()));
    rotate_menu.add(popup_turn_135_menuitem);

    JMenuItem popup_turn_225_menuitem = new JMenuItem();
    popup_turn_225_menuitem.setText(resources.getString("-135_degree"));
    popup_turn_225_menuitem.addActionListener(evt -> turn_45_degree(5));
    popup_turn_225_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_turn_225_menuitem", popup_turn_225_menuitem.getText()));
    rotate_menu.add(popup_turn_225_menuitem);

    JMenuItem popup_turn_315_menuitem = new JMenuItem();
    popup_turn_315_menuitem.setText(resources.getString("-45_degree"));
    popup_turn_315_menuitem.addActionListener(evt -> turn_45_degree(7));
    popup_turn_315_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_turn_315_menuitem", popup_turn_315_menuitem.getText()));
    rotate_menu.add(popup_turn_315_menuitem);

    JMenuItem popup_change_side_menuitem = new JMenuItem();
    popup_change_side_menuitem.setText(resources.getString("change_side"));
    popup_change_side_menuitem.addActionListener(evt -> board_panel.board_handling.change_placement_side());
    popup_change_side_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_change_side_menuitem", popup_change_side_menuitem.getText()));

    this.add(popup_change_side_menuitem, 1);

    JMenuItem popup_reset_rotation_menuitem = new JMenuItem();
    popup_reset_rotation_menuitem.setText(resources.getString("reset_rotation"));
    popup_reset_rotation_menuitem.addActionListener(
        evt -> {
          InteractiveState interactive_state =
              board_panel.board_handling.get_interactive_state();
          if (interactive_state instanceof MoveItemState) {
            ((MoveItemState) interactive_state).reset_rotation();
          }
        });
    popup_reset_rotation_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_reset_rotation_menuitem", popup_reset_rotation_menuitem.getText()));

    this.add(popup_reset_rotation_menuitem, 2);

    JMenuItem popup_insert_menuitem = new JMenuItem();
    popup_insert_menuitem.setText(resources.getString("insert"));
    popup_insert_menuitem.addActionListener(evt -> board_panel.board_handling.return_from_state());
    popup_insert_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_insert_menuitem", popup_insert_menuitem.getText()));

    this.add(popup_insert_menuitem, 3);

    JMenuItem popup_cancel_menuitem = new JMenuItem();
    popup_cancel_menuitem.setText(resources.getString("cancel"));
    popup_cancel_menuitem.addActionListener(evt -> board_panel.board_handling.cancel_state());
    popup_cancel_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_cancel_menuitem", popup_cancel_menuitem.getText()));

    this.add(popup_cancel_menuitem, 4);
  }

  private void turn_45_degree(int p_factor) {
    board_panel.board_handling.turn_45_degree(p_factor);
    board_panel.move_mouse(board_panel.right_button_click_location);
  }
}

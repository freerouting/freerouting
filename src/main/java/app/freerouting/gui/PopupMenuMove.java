package app.freerouting.gui;

import app.freerouting.interactive.InteractiveState;
import app.freerouting.interactive.MoveItemState;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    JMenuItem turn_90_item = new JMenuItem();
    turn_90_item.setText(resources.getString("90_degree"));
    turn_90_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            turn_45_degree(2);
          }
        });
    rotate_menu.add(turn_90_item);

    JMenuItem turn_180_item = new JMenuItem();
    turn_180_item.setText(resources.getString("180_degree"));
    turn_180_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            turn_45_degree(4);
          }
        });
    rotate_menu.add(turn_180_item);

    JMenuItem turn_270_item = new JMenuItem();
    turn_270_item.setText(resources.getString("-90_degree"));
    turn_270_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            turn_45_degree(6);
          }
        });
    rotate_menu.add(turn_270_item);

    JMenuItem turn_45_item = new JMenuItem();
    turn_45_item.setText(resources.getString("45_degree"));
    turn_45_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            turn_45_degree(1);
          }
        });
    rotate_menu.add(turn_45_item);

    JMenuItem turn_135_item = new JMenuItem();
    turn_135_item.setText(resources.getString("135_degree"));
    turn_135_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            turn_45_degree(3);
          }
        });
    rotate_menu.add(turn_135_item);

    JMenuItem turn_225_item = new JMenuItem();
    turn_225_item.setText(resources.getString("-135_degree"));
    turn_225_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            turn_45_degree(5);
          }
        });
    rotate_menu.add(turn_225_item);

    JMenuItem turn_315_item = new JMenuItem();
    turn_315_item.setText(resources.getString("-45_degree"));
    turn_315_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            turn_45_degree(7);
          }
        });
    rotate_menu.add(turn_315_item);

    JMenuItem change_side_item = new JMenuItem();
    change_side_item.setText(resources.getString("change_side"));
    change_side_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            board_panel.board_handling.change_placement_side();
          }
        });

    this.add(change_side_item, 1);

    JMenuItem reset_rotation_item = new JMenuItem();
    reset_rotation_item.setText(resources.getString("reset_rotation"));
    reset_rotation_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            InteractiveState interactive_state =
                board_panel.board_handling.get_interactive_state();
            if (interactive_state instanceof MoveItemState) {
              ((MoveItemState) interactive_state).reset_rotation();
            }
          }
        });

    this.add(reset_rotation_item, 2);

    JMenuItem insert_item = new JMenuItem();
    insert_item.setText(resources.getString("insert"));
    insert_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            board_panel.board_handling.return_from_state();
          }
        });

    this.add(insert_item, 3);

    JMenuItem cancel_item = new JMenuItem();
    cancel_item.setText(resources.getString("cancel"));
    cancel_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            board_panel.board_handling.cancel_state();
          }
        });

    this.add(cancel_item, 4);
  }

  private void turn_45_degree(int p_factor) {
    board_panel.board_handling.turn_45_degree(p_factor);
    board_panel.move_mouse(board_panel.right_button_click_location);
  }
}

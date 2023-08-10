package app.freerouting.gui;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Popup menu used while constructing a cornered shape. */
class PupupMenuCornerItemConstruction extends JPopupMenu {

  private final BoardPanel board_panel;

  /** Creates a new instance of CornerItemConstructionPopupMenu */
  PupupMenuCornerItemConstruction(BoardFrame p_board_frame) {
    this.board_panel = p_board_frame.board_panel;
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    JMenuItem add_corner_item = new JMenuItem();
    add_corner_item.setText(resources.getString("add_corner"));
    add_corner_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            // Same action as if the left button is clicked with
            // the current mouse coordinates in this situation
            // because the left button is a short cut for this action.
            board_panel.board_handling.left_button_clicked(board_panel.right_button_click_location);
          }
        });

    this.add(add_corner_item);

    JMenuItem close_item = new JMenuItem();
    close_item.setText(resources.getString("close"));
    close_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            board_panel.board_handling.return_from_state();
          }
        });

    this.add(close_item);

    JMenuItem cancel_item = new JMenuItem();
    cancel_item.setText(resources.getString("cancel"));
    cancel_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            board_panel.board_handling.cancel_state();
          }
        });

    this.add(cancel_item);
  }
}

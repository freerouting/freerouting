package app.freerouting.gui;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

/** Popup menu containing the 2 items complete and cancel. */
class PopupMenuInsertCancel extends JPopupMenu {

  private final BoardPanel board_panel;

  /** Creates a new instance of CompleteCancelPopupMenu */
  PopupMenuInsertCancel(BoardFrame p_board_frame) {
    this.board_panel = p_board_frame.board_panel;
    ResourceBundle resources =
        ResourceBundle.getBundle(
            "app.freerouting.gui.Default", p_board_frame.get_locale());
    JMenuItem insert_item = new JMenuItem();
    insert_item.setText(resources.getString("insert"));
    insert_item.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent evt) {
            board_panel.board_handling.return_from_state();
          }
        });

    this.add(insert_item);

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

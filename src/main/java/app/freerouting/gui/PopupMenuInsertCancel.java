package app.freerouting.gui;

import app.freerouting.management.FRAnalytics;
import app.freerouting.management.TextManager;

import javax.swing.*;

/**
 * Popup menu containing the 2 items complete and cancel.
 */
class PopupMenuInsertCancel extends JPopupMenu
{

  private final BoardPanel board_panel;

  /**
   * Creates a new instance of CompleteCancelPopupMenu
   */
  PopupMenuInsertCancel(BoardFrame p_board_frame)
  {
    this.board_panel = p_board_frame.board_panel;

    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    JMenuItem popup_insert_menuitem = new JMenuItem();
    popup_insert_menuitem.setText(tm.getText("insert"));
    popup_insert_menuitem.addActionListener(evt -> board_panel.board_handling.return_from_state());
    popup_insert_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_insert_menuitem", popup_insert_menuitem.getText()));

    this.add(popup_insert_menuitem);

    JMenuItem popup_cancel_menuitem = new JMenuItem();
    popup_cancel_menuitem.setText(tm.getText("cancel"));
    popup_cancel_menuitem.addActionListener(evt -> board_panel.board_handling.cancel_state());
    popup_cancel_menuitem.addActionListener(evt -> FRAnalytics.buttonClicked("popup_cancel_menuitem", popup_cancel_menuitem.getText()));

    this.add(popup_cancel_menuitem);
  }
}
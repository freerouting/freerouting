package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Popup menu used while constructing a cornered shape. */
class PopupMenuCornerItemConstruction extends JPopupMenu {

  private final BoardPanel boardPanel;

  /** Creates a new instance of CornerItemConstructionPopupMenu */
  PopupMenuCornerItemConstruction(BoardFrame p_board_frame) {
    this.boardPanel = p_board_frame.boardPanel;

    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    JMenuItem popupAddCornerMenuitem = new JMenuItem();
    popupAddCornerMenuitem.setText(tm.getText("addCorner"));
    popupAddCornerMenuitem.addActionListener(
        // Same action as if the left button is clicked with
        // the current mouse coordinates in this situation
        // because the left button is a shortcut for this action.
        _ -> boardPanel.boardHandling.left_button_clicked(boardPanel.rightButtonClickLocation));
    popupAddCornerMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupAddCornerMenuitem", popupAddCornerMenuitem.getText()));

    this.add(popupAddCornerMenuitem);

    JMenuItem popupCloseMenuitem = new JMenuItem();
    popupCloseMenuitem.setText(tm.getText("close"));
    popupCloseMenuitem.addActionListener(_ -> boardPanel.boardHandling.return_from_state());
    popupCloseMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCloseMenuitem", popupCloseMenuitem.getText()));

    this.add(popupCloseMenuitem);

    JMenuItem popupCancelMenuitem = new JMenuItem();
    popupCancelMenuitem.setText(tm.getText("cancel"));
    popupCancelMenuitem.addActionListener(_ -> boardPanel.boardHandling.cancel_state());
    popupCancelMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCancelMenuitem", popupCancelMenuitem.getText()));

    this.add(popupCancelMenuitem);
  }
}

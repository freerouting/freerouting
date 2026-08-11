package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Popup menu containing the 2 items complete and cancel. */
class PopupMenuInsertCancel extends JPopupMenu {

  private final BoardPanel boardPanel;

  /** Creates a new instance of CompleteCancelPopupMenu. */
  PopupMenuInsertCancel(BoardFrame boardFrame) {
    this.boardPanel = boardFrame.boardPanel;

    TextManager tm = new TextManager(this.getClass(), boardFrame.get_locale());

    JMenuItem popupInsertMenuitem = new JMenuItem();
    popupInsertMenuitem.setText(tm.getText("insert"));
    popupInsertMenuitem.addActionListener(_ -> boardPanel.boardHandling.returnFromState());
    popupInsertMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupInsertMenuitem", popupInsertMenuitem.getText()));

    this.add(popupInsertMenuitem);

    JMenuItem popupCancelMenuitem = new JMenuItem();
    popupCancelMenuitem.setText(tm.getText("cancel"));
    popupCancelMenuitem.addActionListener(_ -> boardPanel.boardHandling.cancelState());
    popupCancelMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCancelMenuitem", popupCancelMenuitem.getText()));

    this.add(popupCancelMenuitem);
  }
}

package app.freerouting.gui.menus;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.board.BoardPanel;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Popup menu containing the 2 items complete and cancel. */
public class PopupMenuInsertCancel extends JPopupMenu {

  private final BoardPanel boardPanel;

  /** Creates a new instance of CompleteCancelPopupMenu. */
  public PopupMenuInsertCancel(BoardFrame boardFrame) {
    this.boardPanel = boardFrame.boardPanel;

    TextManager tm = new TextManager(this.getClass(), boardFrame.getLocale());

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

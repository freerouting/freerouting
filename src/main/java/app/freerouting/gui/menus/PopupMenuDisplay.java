package app.freerouting.gui.menus;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.gui.board.BoardFrame;
import app.freerouting.gui.board.BoardPanel;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/** Provides display-related actions for the board context menu. */
public class PopupMenuDisplay extends JPopupMenu {

  protected final BoardPanel boardPanel;

  /** Creates a new instance of PopupMenuDisplay. */
  public PopupMenuDisplay(BoardFrame boardFrame) {
    this.boardPanel = boardFrame.boardPanel;

    TextManager tm = new TextManager(this.getClass(), boardFrame.getLocale());

    // Flat items rather than a "Zoom" submenu: a JMenu's arrow icon makes Swing's
    // BasicMenuItemUI reserve a matching arrow-icon column across every sibling item in
    // this JPopupMenu (see MenuItemLayoutHelper), which was padding out the whole menu -
    // including unrelated items added by subclasses - just to make room for one arrow.
    addPopupItem(
        tm,
        "center_display",
        "popupCenterDisplayMenuitem",
        () -> boardPanel.centerDisplay(boardPanel.rightButtonClickLocation));
    addPopupItem(
        tm,
        "zoom_in",
        "popupZoomInMenuitem",
        () -> boardPanel.zoomIn(boardPanel.rightButtonClickLocation));
    addPopupItem(
        tm,
        "zoom_out",
        "popupZoomOutMenuitem",
        () -> boardPanel.zoomOut(boardPanel.rightButtonClickLocation));
  }

  /** Builds one menu item with translated text, action, and analytics tracking. */
  private void addPopupItem(TextManager tm, String textKey, String analyticsId, Runnable action) {
    JMenuItem menuItem = new JMenuItem();
    menuItem.setText(tm.getText(textKey));
    menuItem.addActionListener(_ -> action.run());
    menuItem.addActionListener(_ -> FRAnalytics.buttonClicked(analyticsId, menuItem.getText()));
    this.add(menuItem);
  }
}

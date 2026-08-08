package app.freerouting.gui;

import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class PopupMenuDisplay extends JPopupMenu {

  protected final BoardPanel boardPanel;

  /** Creates a new instance of PopupMenuDisplay */
  public PopupMenuDisplay(BoardFrame p_board_frame) {
    this.boardPanel = p_board_frame.boardPanel;

    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    JMenuItem popupCenterDisplayMenuitem = new JMenuItem();
    popupCenterDisplayMenuitem.setText(tm.getText("center_display"));
    popupCenterDisplayMenuitem.addActionListener(
        _ -> boardPanel.centerDisplay(boardPanel.rightButtonClickLocation));
    popupCenterDisplayMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupCenterDisplayMenuitem", popupCenterDisplayMenuitem.getText()));

    this.add(popupCenterDisplayMenuitem);

    JMenu zoomMenu = new JMenu();
    zoomMenu.setText(tm.getText("zoom"));

    JMenuItem popupZoomInMenuitem = new JMenuItem();
    popupZoomInMenuitem.setText(tm.getText("zoom_in"));
    popupZoomInMenuitem.addActionListener(
        _ -> boardPanel.zoomIn(boardPanel.rightButtonClickLocation));
    popupZoomInMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupZoomInMenuitem", popupZoomInMenuitem.getText()));

    zoomMenu.add(popupZoomInMenuitem);

    JMenuItem popupZoomOutMenuitem = new JMenuItem();
    popupZoomOutMenuitem.setText(tm.getText("zoom_out"));
    popupZoomOutMenuitem.addActionListener(
        _ -> boardPanel.zoomOut(boardPanel.rightButtonClickLocation));
    popupZoomOutMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupZoomOutMenuitem", popupZoomOutMenuitem.getText()));

    zoomMenu.add(popupZoomOutMenuitem);

    this.add(zoomMenu);
  }
}

package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;

/** Provides actions for stitching a route through the board. */
public class PopupMenuStitchRoute extends PopupMenuDisplay {

  private final PopupMenuChangeLayer changeLayerMenu;

  /** Creates a new instance of PopupMenuStitchRoute. */
  public PopupMenuStitchRoute(BoardFrame boardFrame) {
    super(boardFrame);
    LayerStructure layerStructure = boardPanel.boardHandling.getRoutingBoard().layerStructure;

    if (layerStructure.arr.length > 0) {
      changeLayerMenu = new PopupMenuChangeLayer(boardFrame);
      this.add(changeLayerMenu, 0);
    } else {
      changeLayerMenu = null;
    }

    TextManager tm = new TextManager(this.getClass(), boardFrame.getLocale());

    JMenuItem popupInsertMenuitem = new JMenuItem();
    popupInsertMenuitem.setText(tm.getText("insert"));
    popupInsertMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.leftButtonClicked(boardPanel.rightButtonClickLocation));
    popupInsertMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupInsertMenuitem", popupInsertMenuitem.getText()));

    this.add(popupInsertMenuitem, 0);

    JMenuItem popupDoneMenuitem = new JMenuItem();
    popupDoneMenuitem.setText(tm.getText("done"));
    popupDoneMenuitem.addActionListener(_ -> boardPanel.boardHandling.returnFromState());
    popupDoneMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupDoneMenuitem", popupDoneMenuitem.getText()));

    this.add(popupDoneMenuitem, 1);

    JMenuItem popupCancelMenuitem = new JMenuItem();
    popupCancelMenuitem.setText(tm.getText("cancel"));
    popupCancelMenuitem.addActionListener(_ -> boardPanel.boardHandling.cancelState());
    popupCancelMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCancelMenuitem", popupCancelMenuitem.getText()));

    this.add(popupCancelMenuitem, 2);

    Layer currentLayer =
        layerStructure.arr[boardPanel.boardHandling.getWorkspaceSettings().getLayer()];
    disableLayerItem(layerStructure.getSignalLayerNo(currentLayer));
  }

  /** Disables the no-th item in the changeLayerMenu. */
  void disableLayerItem(int no) {
    if (this.changeLayerMenu != null) {
      this.changeLayerMenu.disableItem(no);
    }
  }
}

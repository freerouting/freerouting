package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;

/** Popup menu used in the interactive route state. */
public class PopupMenuDynamicRoute extends PopupMenuDisplay {

  private final PopupMenuChangeLayer changeLayerMenu;

  /** Creates a new instance of RoutePopupMenu. */
  PopupMenuDynamicRoute(BoardFrame boardFrame) {
    super(boardFrame);

    TextManager tm = new TextManager(this.getClass(), boardFrame.getLocale());

    final LayerStructure layerStructure = boardPanel.boardHandling.getRoutingBoard().layerStructure;

    JMenuItem popupEndRouteMenuitem = new JMenuItem();
    popupEndRouteMenuitem.setText(tm.getText("end_route"));
    popupEndRouteMenuitem.addActionListener(_ -> boardPanel.boardHandling.returnFromState());
    popupEndRouteMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupEndRouteMenuitem", popupEndRouteMenuitem.getText()));

    this.add(popupEndRouteMenuitem, 0);

    JMenuItem popupCancelMenuitem = new JMenuItem();
    popupCancelMenuitem.setText(tm.getText("cancel_route"));
    popupCancelMenuitem.addActionListener(_ -> boardPanel.boardHandling.cancelState());
    popupCancelMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCancelMenuitem", popupCancelMenuitem.getText()));

    this.add(popupCancelMenuitem, 1);

    if (layerStructure.layers.length > 0) {
      this.changeLayerMenu = new PopupMenuChangeLayer(boardFrame);
      this.add(changeLayerMenu, 0);
    } else {
      this.changeLayerMenu = null;
    }

    Layer currentLayer =
        layerStructure.layers[boardPanel.boardHandling.getWorkspaceSettings().getLayer()];
    disableLayerItem(layerStructure.getSignalLayerNo(currentLayer));
  }

  /** Disables the no-th item in the changeLayerMenu. */
  void disableLayerItem(int no) {
    if (this.changeLayerMenu != null) {
      this.changeLayerMenu.disableItem(no);
    }
  }
}

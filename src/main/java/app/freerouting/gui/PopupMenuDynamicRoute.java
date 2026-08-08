package app.freerouting.gui;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;

/** Popup menu used in the interactive route state. */
public class PopupMenuDynamicRoute extends PopupMenuDisplay {

  private final PopupMenuChangeLayer changeLayerMenu;

  /** Creates a new instance of RoutePopupMenu */
  PopupMenuDynamicRoute(BoardFrame p_board_frame) {
    super(p_board_frame);

    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    LayerStructure layerStructure = boardPanel.boardHandling.getRoutingBoard().layerStructure;

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

    if (layerStructure.arr.length > 0) {
      this.changeLayerMenu = new PopupMenuChangeLayer(p_board_frame);
      this.add(changeLayerMenu, 0);
    } else {
      this.changeLayerMenu = null;
    }

    Layer currLayer =
        layerStructure.arr[boardPanel.boardHandling.getInteractiveSettings().getLayer()];
    disableLayerItem(layerStructure.getSignalLayerNo(currLayer));
  }

  /** Disables the p_no-th item in the changeLayerMenu. */
  void disableLayerItem(int p_no) {
    if (this.changeLayerMenu != null) {
      this.changeLayerMenu.disableItem(p_no);
    }
  }
}

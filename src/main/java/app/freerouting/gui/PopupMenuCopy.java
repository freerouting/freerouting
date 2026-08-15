package app.freerouting.gui;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;

/** Popup menu used in the interactive copy item state. */
public class PopupMenuCopy extends PopupMenuDisplay {

  private final PopupMenuChangeLayer changeLayerMenu;

  /** Creates a new instance of CopyPopupMenu. */
  PopupMenuCopy(BoardFrame boardFrame) {
    super(boardFrame);
    LayerStructure layerStructure = boardPanel.boardHandling.getRoutingBoard().layerStructure;

    if (layerStructure.arr.length > 0) {
      changeLayerMenu = new PopupMenuChangeLayer(boardFrame);
      this.add(changeLayerMenu, 0);
    } else {
      changeLayerMenu = null;
    }

    TextManager tm = new TextManager(this.getClass(), boardFrame.getLocale());

    JMenuItem popupCopyInsertMenuitem = new JMenuItem();
    popupCopyInsertMenuitem.setText(tm.getText("insert"));
    popupCopyInsertMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.leftButtonClicked(boardPanel.rightButtonClickLocation));
    popupCopyInsertMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupCopyInsertMenuitem", popupCopyInsertMenuitem.getText()));

    this.add(popupCopyInsertMenuitem, 0);

    JMenuItem popupCopyDoneMenuitem = new JMenuItem();
    popupCopyDoneMenuitem.setText(tm.getText("done"));
    popupCopyDoneMenuitem.addActionListener(_ -> boardPanel.boardHandling.returnFromState());
    popupCopyDoneMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCopyDoneMenuitem", popupCopyDoneMenuitem.getText()));

    this.add(popupCopyDoneMenuitem, 1);

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

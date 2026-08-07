package app.freerouting.gui;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;

/** Popup menu used in the interactive copy item state. */
public class PopupMenuCopy extends PopupMenuDisplay {

  private final PopupMenuChangeLayer changeLayerMenu;

  /** Creates a new instance of CopyPopupMenu */
  PopupMenuCopy(BoardFrame p_board_frame) {
    super(p_board_frame);
    LayerStructure layerStructure = boardPanel.boardHandling.get_routing_board().layerStructure;

    if (layerStructure.arr.length > 0) {
      changeLayerMenu = new PopupMenuChangeLayer(p_board_frame);
      this.add(changeLayerMenu, 0);
    } else {
      changeLayerMenu = null;
    }

    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    JMenuItem popupCopyInsertMenuitem = new JMenuItem();
    popupCopyInsertMenuitem.setText(tm.getText("insert"));
    popupCopyInsertMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.left_button_clicked(boardPanel.rightButtonClickLocation));
    popupCopyInsertMenuitem.addActionListener(
        _ ->
            FRAnalytics.buttonClicked(
                "popupCopyInsertMenuitem", popupCopyInsertMenuitem.getText()));

    this.add(popupCopyInsertMenuitem, 0);

    JMenuItem popupCopyDoneMenuitem = new JMenuItem();
    popupCopyDoneMenuitem.setText(tm.getText("done"));
    popupCopyDoneMenuitem.addActionListener(_ -> boardPanel.boardHandling.return_from_state());
    popupCopyDoneMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCopyDoneMenuitem", popupCopyDoneMenuitem.getText()));

    this.add(popupCopyDoneMenuitem, 1);

    Layer currLayer =
        layerStructure.arr[boardPanel.boardHandling.getInteractiveSettings().get_layer()];
    disable_layer_item(layerStructure.get_signal_layer_no(currLayer));
  }

  /** Disables the p_no-th item in the changeLayerMenu. */
  void disable_layer_item(int p_no) {
    if (this.changeLayerMenu != null) {
      this.changeLayerMenu.disable_item(p_no);
    }
  }
}

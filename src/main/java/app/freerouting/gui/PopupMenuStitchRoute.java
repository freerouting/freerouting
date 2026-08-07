package app.freerouting.gui;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import javax.swing.JMenuItem;

public class PopupMenuStitchRoute extends PopupMenuDisplay {

  private final PopupMenuChangeLayer changeLayerMenu;

  /** Creates a new instance of PopupMenuStitchRoute */
  public PopupMenuStitchRoute(BoardFrame p_board_frame) {
    super(p_board_frame);
    LayerStructure layerStructure = boardPanel.boardHandling.get_routing_board().layerStructure;

    if (layerStructure.arr.length > 0) {
      changeLayerMenu = new PopupMenuChangeLayer(p_board_frame);
      this.add(changeLayerMenu, 0);
    } else {
      changeLayerMenu = null;
    }

    TextManager tm = new TextManager(this.getClass(), p_board_frame.get_locale());

    JMenuItem popupInsertMenuitem = new JMenuItem();
    popupInsertMenuitem.setText(tm.getText("insert"));
    popupInsertMenuitem.addActionListener(
        _ -> boardPanel.boardHandling.left_button_clicked(boardPanel.rightButtonClickLocation));
    popupInsertMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupInsertMenuitem", popupInsertMenuitem.getText()));

    this.add(popupInsertMenuitem, 0);

    JMenuItem popupDoneMenuitem = new JMenuItem();
    popupDoneMenuitem.setText(tm.getText("done"));
    popupDoneMenuitem.addActionListener(_ -> boardPanel.boardHandling.return_from_state());
    popupDoneMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupDoneMenuitem", popupDoneMenuitem.getText()));

    this.add(popupDoneMenuitem, 1);

    JMenuItem popupCancelMenuitem = new JMenuItem();
    popupCancelMenuitem.setText(tm.getText("cancel"));
    popupCancelMenuitem.addActionListener(_ -> boardPanel.boardHandling.cancel_state());
    popupCancelMenuitem.addActionListener(
        _ -> FRAnalytics.buttonClicked("popupCancelMenuitem", popupCancelMenuitem.getText()));

    this.add(popupCancelMenuitem, 2);

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

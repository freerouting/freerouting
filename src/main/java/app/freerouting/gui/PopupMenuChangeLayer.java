package app.freerouting.gui;

import app.freerouting.board.LayerStructure;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Used as submenu in a popup menu for change layer actions. */
class PopupMenuChangeLayer extends JMenu {

  private final BoardFrame boardFrame;
  private final LayermenuItem[] itemArr;

  /** Creates a new instance of ChangeLayerMenu */
  PopupMenuChangeLayer(BoardFrame p_board_frame) {
    this.boardFrame = p_board_frame;

    LayerStructure layerStructure =
        boardFrame.boardPanel.boardHandling.getRoutingBoard().layerStructure;
    this.itemArr = new LayermenuItem[layerStructure.signalLayerCount()];
    TextManager tm = new TextManager(this.getClass(), boardFrame.get_locale());

    this.setText(tm.getText("change_layer"));
    this.setToolTipText(tm.getText("change_layer_tooltip"));
    int currSignalLayerNo = 0;
    for (int i = 0; i < layerStructure.arr.length; i++) {
      if (layerStructure.arr[i].isSignal) {
        this.itemArr[currSignalLayerNo] = new LayermenuItem(i);
        this.itemArr[currSignalLayerNo].setText(layerStructure.arr[i].name);
        this.add(this.itemArr[currSignalLayerNo]);
        ++currSignalLayerNo;
      }
    }
  }

  /** Disables the item with index p_no and enables all other items. */
  void disableItem(int p_no) {
    for (int i = 0; i < itemArr.length; i++) {
      this.itemArr[i].setEnabled(i != p_no);
    }
  }

  private class LayermenuItem extends JMenuItem {

    private final int layerNo;

    LayermenuItem(int p_layer_no) {
      layerNo = p_layer_no;
      addActionListener(
          _ -> {
            final BoardPanel boardPanel = boardFrame.boardPanel;
            if (boardPanel.boardHandling.changeLayerAction(layerNo)) {
              TextManager tm = new TextManager(PopupMenuChangeLayer.class, boardFrame.get_locale());
              String layerName =
                  boardPanel.boardHandling.getRoutingBoard().layerStructure.arr[layerNo].name;
              boardPanel.screenMessages.setStatusMessage(
                  tm.getText("layer_changed_to_message", layerName));
            }
            // If change_layer failed the status message is set inside change_layer_action
            // because the information of the cause of the failing is missing here.
            boardPanel.moveMouse(boardPanel.rightButtonClickLocation);
          });
    }
  }
}

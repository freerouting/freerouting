package app.freerouting.gui;

import app.freerouting.board.LayerStructure;
import app.freerouting.util.TextManager;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

/** Used as submenu in a popup menu for change layer actions. */
class PopupMenuChangeLayer extends JMenu {

  private final BoardFrame boardFrame;
  private final LayermenuItem[] items;

  /** Creates a new instance of ChangeLayerMenu. */
  PopupMenuChangeLayer(BoardFrame boardFrame) {
    this.boardFrame = boardFrame;

    LayerStructure layerStructure =
        boardFrame.boardPanel.boardHandling.getRoutingBoard().layerStructure;
    this.items = new LayermenuItem[layerStructure.signalLayerCount()];
    TextManager tm = new TextManager(this.getClass(), boardFrame.getLocale());

    this.setText(tm.getText("change_layer"));
    this.setToolTipText(tm.getText("change_layer_tooltip"));
    int currentSignalLayerNo = 0;
    for (int i = 0; i < layerStructure.layers.length; i++) {
      if (layerStructure.layers[i].isSignal) {
        this.items[currentSignalLayerNo] = new LayermenuItem(i);
        this.items[currentSignalLayerNo].setText(layerStructure.layers[i].name);
        this.add(this.items[currentSignalLayerNo]);
        ++currentSignalLayerNo;
      }
    }
  }

  /** Disables the item with index no and enables all other items. */
  void disableItem(int no) {
    for (int i = 0; i < items.length; i++) {
      this.items[i].setEnabled(i != no);
    }
  }

  private class LayermenuItem extends JMenuItem {

    private final int layerIndex;

    LayermenuItem(int layerIndex) {
      this.layerIndex = layerIndex;
      addActionListener(
          _ -> {
            final BoardPanel boardPanel = boardFrame.boardPanel;
            if (boardPanel.boardHandling.changeLayerAction(layerIndex)) {
              TextManager tm = new TextManager(PopupMenuChangeLayer.class, boardFrame.getLocale());
              String layerName =
                  boardPanel.boardHandling.getRoutingBoard().layerStructure.layers[layerIndex].name;
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

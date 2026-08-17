package app.freerouting.gui.interactive;

import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.Pin;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Set;
import javax.swing.JPopupMenu;

/** Common base class for the main menus, which can be selected in the toolbar. */
public class MenuState extends InteractiveState {

  /** Creates a new instance of MenuState. */
  MenuState(GuiBoardManager boardHandle) {
    super(null, boardHandle);
    this.returnState = this;
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuMain;
  }

  /**
   * Selects items at the specified location. Returns a new instance of InspectedItemState with the
   * selected items if something was selected.
   */
  public InteractiveState selectItems(FloatPoint location) {
    this.hdlg.displayLayerMessage();
    Set<Item> pickedItems = hdlg.pickItems(location);
    boolean somethingFound = !pickedItems.isEmpty();
    InteractiveState result;
    if (somethingFound) {
      result = InspectedItemState.getInstance(pickedItems, this, hdlg);
      hdlg.screenMessages.setStatusMessage(tm.getText("in_inspect_mode"));
    } else {
      result = this;
    }
    hdlg.repaint();
    return result;
  }

  /** Starts pin swapping for the pin selected at the given location. */
  public InteractiveState swapPins(FloatPoint location) {
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = hdlg.pickItems(location, selectionFilter);
    InteractiveState result = this;
    if (!pickedItems.isEmpty()) {
      Item firstItem = pickedItems.iterator().next();
      if (!(firstItem instanceof Pin selectedPin)) {
        FRLogger.warn("MenuState.swap_pin: Pin expected");
        return this;
      }
      result = PinSwapState.getInstance(selectedPin, this, hdlg);
    } else {
      hdlg.screenMessages.setStatusMessage(tm.getText("no_pin_selected"));
    }
    hdlg.repaint();
    return result;
  }

  /** Action to be taken when a key shortcut is pressed. */
  @Override
  public InteractiveState keyTyped(char keyChar) {
    InteractiveState currentReturnState = this;
    switch (keyChar) {
      case 'b' -> hdlg.redo();
      case 'd' -> currentReturnState = DragMenuState.getInstance(hdlg);
      case 'e' ->
          currentReturnState =
              ExpandTestState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
      case 'g' -> hdlg.toggleRatsnest();
      case 'i' -> currentReturnState = this.selectItems(hdlg.getCurrentMousePosition());
      case 'p' -> {
        hdlg.getWorkspaceSettings().setPushEnabled(!hdlg.getWorkspaceSettings().getPushEnabled());
        hdlg.getPanel().boardFrame.refreshWindows();
      }
      case 'r' -> currentReturnState = RouteMenuState.getInstance(hdlg);
      case 's' -> currentReturnState = InspectMenuState.getInstance(hdlg);
      case 't' ->
          currentReturnState = RouteState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
      case 'u' -> hdlg.undo();
      case 'v' -> hdlg.toggleClearanceViolations();
      case 'w' -> currentReturnState = swapPins(hdlg.getCurrentMousePosition());
      case '+' -> {
        // increase the current layer to the next signal layer
        LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
        int currentLayerIndex = hdlg.getWorkspaceSettings().getLayer();
        do {
          ++currentLayerIndex;
        } while (currentLayerIndex < layerStructure.layers.length
            && !layerStructure.layers[currentLayerIndex].isSignal);

        if (currentLayerIndex < layerStructure.layers.length) {
          hdlg.setCurrentLayer(currentLayerIndex);
        }
      }
      case '-' -> {
        // decrease the current layer to the previous signal layer
        LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
        int currentLayerIndex = hdlg.getWorkspaceSettings().getLayer();
        do {
          --currentLayerIndex;
        } while (currentLayerIndex >= 0 && !layerStructure.layers[currentLayerIndex].isSignal);

        if (currentLayerIndex >= 0) {
          hdlg.setCurrentLayer(currentLayerIndex);
        }
      }
      default -> currentReturnState = super.keyTyped(keyChar);
    }
    return currentReturnState;
  }

  /** Do nothing on complete. */
  @Override
  public InteractiveState complete() {
    return this;
  }

  /** Do nothing on cancel. */
  @Override
  public InteractiveState cancel() {
    return this;
  }

  @Override
  public void setToolbar() {
    hdlg.getPanel().boardFrame.setMenuToolbar();
  }
}

package app.freerouting.gui.interactive;

import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.Pin;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.session.GuiBoardManager;
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
    InteractiveState currReturnState = this;
    switch (keyChar) {
      case 'b' -> hdlg.redo();
      case 'd' -> currReturnState = DragMenuState.getInstance(hdlg);
      case 'e' ->
          currReturnState = ExpandTestState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
      case 'g' -> hdlg.toggleRatsnest();
      case 'i' -> currReturnState = this.selectItems(hdlg.getCurrentMousePosition());
      case 'p' -> {
        hdlg.getInteractiveSettings()
            .setPushEnabled(!hdlg.getInteractiveSettings().getPushEnabled());
        hdlg.getPanel().boardFrame.refreshWindows();
      }
      case 'r' -> currReturnState = RouteMenuState.getInstance(hdlg);
      case 's' -> currReturnState = InspectMenuState.getInstance(hdlg);
      case 't' ->
          currReturnState = RouteState.getInstance(hdlg.getCurrentMousePosition(), this, hdlg);
      case 'u' -> hdlg.undo();
      case 'v' -> hdlg.toggleClearanceViolations();
      case 'w' -> currReturnState = swapPins(hdlg.getCurrentMousePosition());
      case '+' -> {
        // increase the current layer to the next signal layer
        LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
        int currentLayerNo = hdlg.getInteractiveSettings().getLayer();
        do {
          ++currentLayerNo;
        } while (currentLayerNo < layerStructure.arr.length
            && !layerStructure.arr[currentLayerNo].isSignal);

        if (currentLayerNo < layerStructure.arr.length) {
          hdlg.setCurrentLayer(currentLayerNo);
        }
      }
      case '-' -> {
        // decrease the current layer to the previous signal layer
        LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
        int currentLayerNo = hdlg.getInteractiveSettings().getLayer();
        do {
          --currentLayerNo;
        } while (currentLayerNo >= 0 && !layerStructure.arr[currentLayerNo].isSignal);

        if (currentLayerNo >= 0) {
          hdlg.setCurrentLayer(currentLayerNo);
        }
      }
      default -> currReturnState = super.keyTyped(keyChar);
    }
    return currReturnState;
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

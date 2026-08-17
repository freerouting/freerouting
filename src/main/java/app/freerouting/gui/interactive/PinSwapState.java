package app.freerouting.gui.interactive;

import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Set;

/** Interactive state for swapping the nets of two compatible pins. */
public final class PinSwapState extends InteractiveState {

  private final Pin fromPin;
  private final Set<Pin> swappablePins;
  private Pin toPin;

  /** Creates a new instance of PinSwapState. */
  private PinSwapState(Pin pinToSwap, InteractiveState returnState, GuiBoardManager boardHandling) {
    super(returnState, boardHandling);
    this.fromPin = pinToSwap;
    this.swappablePins = pinToSwap.getSwappablePins();
  }

  /** Returns a pin-swap state, or the parent state if no swap is available. */
  public static InteractiveState getInstance(
      Pin pinToSwap, InteractiveState returnState, GuiBoardManager boardHandling) {
    PinSwapState newState = new PinSwapState(pinToSwap, returnState, boardHandling);
    if (newState.swappablePins.isEmpty()) {
      newState.hdlg.screenMessages.setStatusMessage(newState.tm.getText("no_swappable_pin_found"));
      return returnState;
    }
    newState.hdlg.screenMessages.setStatusMessage(
        newState.tm.getText("please_click_second_pin_with_the_left_mouse_button"));
    return newState;
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = hdlg.pickItems(location, selectionFilter);
    if (pickedItems.isEmpty()) {
      this.hdlg.screenMessages.setStatusMessage(tm.getText("no_pin_selected"));
      return this.cancel();
    }
    Item toItem = pickedItems.iterator().next();
    if (!(toItem instanceof Pin)) {
      hdlg.screenMessages.setStatusMessage(tm.getText("picked_pin_expected"));
      return this.cancel();
    }

    this.toPin = (Pin) toItem;
    if (!swappablePins.contains(this.toPin)) {
      return cancel();
    }
    return complete();
  }

  @Override
  public InteractiveState complete() {
    if (this.fromPin == null || this.toPin == null) {
      hdlg.screenMessages.setStatusMessage(tm.getText("pin_to_swap_missing"));
      return this.cancel();
    }
    if (this.fromPin.netCount() > 1 || this.toPin.netCount() > 1) {
      FRLogger.warn(
          "PinSwapState.complete: pin swap is not implemented for pins "
              + "belonging to more than one net.");
      return this.cancel();
    }
    int fromNetNumber;
    if (this.fromPin.netCount() > 0) {
      fromNetNumber = this.fromPin.getNetNumber(0);
    } else {
      fromNetNumber = -1;
    }
    int toNetNumber;
    if (this.toPin.netCount() > 0) {
      toNetNumber = this.toPin.getNetNumber(0);
    } else {
      toNetNumber = -1;
    }
    if (!hdlg.getRoutingBoard().checkChangeNet(this.fromPin, toNetNumber)) {
      hdlg.screenMessages.setStatusMessage(
          tm.getText("pin_not_swapped_because_it_is_already_connected"));
      return this.cancel();
    }
    if (!hdlg.getRoutingBoard().checkChangeNet(this.toPin, fromNetNumber)) {
      hdlg.screenMessages.setStatusMessage(
          tm.getText("pin_not_swapped_because_second_pin_is_already_connected"));
      return this.cancel();
    }
    hdlg.getRoutingBoard().generateSnapshot();
    this.fromPin.swap(this.toPin);
    for (int i = 0; i < this.fromPin.netCount(); i++) {
      hdlg.updateRatsnest(this.fromPin.getNetNumber(i));
    }
    for (int i = 0; i < this.toPin.netCount(); i++) {
      hdlg.updateRatsnest(this.toPin.getNetNumber(i));
    }
    hdlg.screenMessages.setStatusMessage(tm.getText("pin_swap_completed"));
    return this.returnState;
  }

  @Override
  public void draw(Graphics graphics) {
    Color highlightColor = hdlg.graphicsContext.getHighlightColor();
    double highligtColorIntensity = hdlg.graphicsContext.getHighlightColorIntensity();
    BoardRenderer.drawOverlayItem(
        fromPin, graphics, hdlg.graphicsContext, highlightColor, 0.5 * highligtColorIntensity);
    for (Pin currentPin : swappablePins) {
      BoardRenderer.drawOverlayItem(
          currentPin, graphics, hdlg.graphicsContext, highlightColor, highligtColorIntensity);
    }
  }
}

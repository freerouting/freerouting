package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Set;

public final class PinSwapState extends InteractiveState {

  private final Pin fromPin;
  private final Set<Pin> swappablePins;
  private Pin toPin;

  /** Creates a new instance of PinSwapState */
  private PinSwapState(
      Pin p_pin_to_swap, InteractiveState p_return_state, GuiBoardManager p_board_handling) {
    super(p_return_state, p_board_handling);
    this.fromPin = p_pin_to_swap;
    this.swappablePins = p_pin_to_swap.getSwappablePins();
  }

  public static InteractiveState getInstance(
      Pin p_pin_to_swap, InteractiveState p_return_state, GuiBoardManager p_board_handling) {
    PinSwapState newState = new PinSwapState(p_pin_to_swap, p_return_state, p_board_handling);
    if (newState.swappablePins.isEmpty()) {
      newState.hdlg.screenMessages.setStatusMessage(
          newState.tm.getText("no_swappable_pin_found"));
      return p_return_state;
    }
    newState.hdlg.screenMessages.setStatusMessage(
        newState.tm.getText("please_click_second_pin_with_the_left_mouse_button"));
    return newState;
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint p_location) {
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = hdlg.pickItems(p_location, selectionFilter);
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
          "PinSwapState.complete: pin swap not yet implemented for pins belonging to more than 1 net ");
      return this.cancel();
    }
    int fromNetNo;
    if (this.fromPin.netCount() > 0) {
      fromNetNo = this.fromPin.getNetNo(0);
    } else {
      fromNetNo = -1;
    }
    int toNetNo;
    if (this.toPin.netCount() > 0) {
      toNetNo = this.toPin.getNetNo(0);
    } else {
      toNetNo = -1;
    }
    if (!hdlg.getRoutingBoard().checkChangeNet(this.fromPin, toNetNo)) {
      hdlg.screenMessages.setStatusMessage(
          tm.getText("pin_not_swapped_because_it_is_already_connected"));
      return this.cancel();
    }
    if (!hdlg.getRoutingBoard().checkChangeNet(this.toPin, fromNetNo)) {
      hdlg.screenMessages.setStatusMessage(
          tm.getText("pin_not_swapped_because_second_pin_is_already_connected"));
      return this.cancel();
    }
    hdlg.getRoutingBoard().generateSnapshot();
    this.fromPin.swap(this.toPin);
    for (int i = 0; i < this.fromPin.netCount(); i++) {
      hdlg.updateRatsnest(this.fromPin.getNetNo(i));
    }
    for (int i = 0; i < this.toPin.netCount(); i++) {
      hdlg.updateRatsnest(this.toPin.getNetNo(i));
    }
    hdlg.screenMessages.setStatusMessage(tm.getText("pin_swap_completed"));
    return this.returnState;
  }

  @Override
  public void draw(Graphics p_graphics) {
    Color highlightColor = hdlg.graphicsContext.getHilightColor();
    double highligtColorIntensity = hdlg.graphicsContext.getHilightColorIntensity();
    fromPin.draw(p_graphics, hdlg.graphicsContext, highlightColor, 0.5 * highligtColorIntensity);
    for (Pin currPin : swappablePins) {
      currPin.draw(p_graphics, hdlg.graphicsContext, highlightColor, highligtColorIntensity);
    }
  }
}

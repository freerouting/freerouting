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
    this.swappablePins = p_pin_to_swap.get_swappable_pins();
  }

  public static InteractiveState get_instance(
      Pin p_pin_to_swap, InteractiveState p_return_state, GuiBoardManager p_board_handling) {
    PinSwapState newState = new PinSwapState(p_pin_to_swap, p_return_state, p_board_handling);
    if (newState.swappablePins.isEmpty()) {
      newState.hdlg.screenMessages.set_status_message(
          newState.tm.getText("no_swappable_pin_found"));
      return p_return_state;
    }
    newState.hdlg.screenMessages.set_status_message(
        newState.tm.getText("please_click_second_pin_with_the_left_mouse_button"));
    return newState;
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = hdlg.pick_items(p_location, selectionFilter);
    if (pickedItems.isEmpty()) {
      this.hdlg.screenMessages.set_status_message(tm.getText("no_pin_selected"));
      return this.cancel();
    }
    Item toItem = pickedItems.iterator().next();
    if (!(toItem instanceof Pin)) {
      hdlg.screenMessages.set_status_message(tm.getText("picked_pin_expected"));
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
      hdlg.screenMessages.set_status_message(tm.getText("pin_to_swap_missing"));
      return this.cancel();
    }
    if (this.fromPin.net_count() > 1 || this.toPin.net_count() > 1) {
      FRLogger.warn(
          "PinSwapState.complete: pin swap not yet implemented for pins belonging to more than 1 net ");
      return this.cancel();
    }
    int fromNetNo;
    if (this.fromPin.net_count() > 0) {
      fromNetNo = this.fromPin.get_net_no(0);
    } else {
      fromNetNo = -1;
    }
    int toNetNo;
    if (this.toPin.net_count() > 0) {
      toNetNo = this.toPin.get_net_no(0);
    } else {
      toNetNo = -1;
    }
    if (!hdlg.get_routing_board().check_change_net(this.fromPin, toNetNo)) {
      hdlg.screenMessages.set_status_message(
          tm.getText("pin_not_swapped_because_it_is_already_connected"));
      return this.cancel();
    }
    if (!hdlg.get_routing_board().check_change_net(this.toPin, fromNetNo)) {
      hdlg.screenMessages.set_status_message(
          tm.getText("pin_not_swapped_because_second_pin_is_already_connected"));
      return this.cancel();
    }
    hdlg.get_routing_board().generate_snapshot();
    this.fromPin.swap(this.toPin);
    for (int i = 0; i < this.fromPin.net_count(); i++) {
      hdlg.update_ratsnest(this.fromPin.get_net_no(i));
    }
    for (int i = 0; i < this.toPin.net_count(); i++) {
      hdlg.update_ratsnest(this.toPin.get_net_no(i));
    }
    hdlg.screenMessages.set_status_message(tm.getText("pin_swap_completed"));
    return this.returnState;
  }

  @Override
  public void draw(Graphics p_graphics) {
    Color highlightColor = hdlg.graphicsContext.get_hilight_color();
    double highligtColorIntensity = hdlg.graphicsContext.get_hilight_color_intensity();
    fromPin.draw(p_graphics, hdlg.graphicsContext, highlightColor, 0.5 * highligtColorIntensity);
    for (Pin currPin : swappablePins) {
      currPin.draw(p_graphics, hdlg.graphicsContext, highlightColor, highligtColorIntensity);
    }
  }
}

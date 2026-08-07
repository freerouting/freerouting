package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.Pin;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Set;
import javax.swing.JPopupMenu;

/** Common base class for the main menus, which can be selected in the toolbar. */
public class MenuState extends InteractiveState {

  /** Creates a new instance of MenuState */
  MenuState(GuiBoardManager p_board_handle) {
    super(null, p_board_handle);
    this.returnState = this;
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popupMenuMain;
  }

  /**
   * Selects items at p_location. Returns a new instance of SelectedItemState with the selected
   * items, if something was selected.
   */
  public InteractiveState select_items(FloatPoint p_location) {
    this.hdlg.display_layer_message();
    Set<Item> pickedItems = hdlg.pick_items(p_location);
    boolean somethingFound = !pickedItems.isEmpty();
    InteractiveState result;
    if (somethingFound) {
      result = InspectedItemState.get_instance(pickedItems, this, hdlg);
      hdlg.screenMessages.set_status_message(tm.getText("in_inspect_mode"));
    } else {
      result = this;
    }
    hdlg.repaint();
    return result;
  }

  public InteractiveState swap_pin(FloatPoint p_location) {
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = hdlg.pick_items(p_location, selectionFilter);
    InteractiveState result = this;
    if (!pickedItems.isEmpty()) {
      Item firstItem = pickedItems.iterator().next();
      if (!(firstItem instanceof Pin selected_pin)) {
        FRLogger.warn("MenuState.swap_pin: Pin expected");
        return this;
      }
      result = PinSwapState.get_instance(selected_pin, this, hdlg);
    } else {
      hdlg.screenMessages.set_status_message(tm.getText("no_pin_selected"));
    }
    hdlg.repaint();
    return result;
  }

  /** Action to be taken when a key shortcut is pressed. */
  @Override
  public InteractiveState key_typed(char p_key_char) {
    InteractiveState currReturnState = this;
    switch (p_key_char) {
      case 'b' -> hdlg.redo();
      case 'd' -> currReturnState = DragMenuState.get_instance(hdlg);
      case 'e' ->
          currReturnState =
              ExpandTestState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
      case 'g' -> hdlg.toggle_ratsnest();
      case 'i' -> currReturnState = this.select_items(hdlg.get_current_mouse_position());
      case 'p' -> {
        hdlg.getInteractiveSettings()
            .set_push_enabled(!hdlg.getInteractiveSettings().get_push_enabled());
        hdlg.get_panel().boardFrame.refresh_windows();
      }
      case 'r' -> currReturnState = RouteMenuState.get_instance(hdlg);
      case 's' -> currReturnState = InspectMenuState.get_instance(hdlg);
      case 't' ->
          currReturnState = RouteState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
      case 'u' -> hdlg.undo();
      case 'v' -> hdlg.toggle_clearance_violations();
      case 'w' -> currReturnState = swap_pin(hdlg.get_current_mouse_position());
      case '+' -> {
        // increase the current layer to the next signal layer
        LayerStructure layerStructure = hdlg.get_routing_board().layerStructure;
        int currentLayerNo = hdlg.getInteractiveSettings().get_layer();
        do {
          ++currentLayerNo;
        } while (currentLayerNo < layerStructure.arr.length
            && !layerStructure.arr[currentLayerNo].isSignal);

        if (currentLayerNo < layerStructure.arr.length) {
          hdlg.set_current_layer(currentLayerNo);
        }
      }
      case '-' -> {
        // decrease the current layer to the previous signal layer
        LayerStructure layerStructure = hdlg.get_routing_board().layerStructure;
        int currentLayerNo = hdlg.getInteractiveSettings().get_layer();
        do {
          --currentLayerNo;
        } while (currentLayerNo >= 0 && !layerStructure.arr[currentLayerNo].isSignal);

        if (currentLayerNo >= 0) {
          hdlg.set_current_layer(currentLayerNo);
        }
      }
      default -> currReturnState = super.key_typed(p_key_char);
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
  public void set_toolbar() {
    hdlg.get_panel().boardFrame.set_menu_toolbar();
  }
}

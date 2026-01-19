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

/**
 * Common base class for the main menus, which can be selected in the toolbar.
 */
public class MenuState extends InteractiveState {

  /**
   * Creates a new instance of MenuState
   */
  MenuState(GuiBoardManager p_board_handle) {
    super(null, p_board_handle);
    this.return_state = this;
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popup_menu_main;
  }

  /**
   * Selects items at p_location. Returns a new instance of SelectedItemState with
   * the selected items, if something was selected.
   */
  public InteractiveState select_items(FloatPoint p_location) {
    this.hdlg.display_layer_message();
    Set<Item> picked_items = hdlg.pick_items(p_location);
    boolean something_found = !picked_items.isEmpty();
    InteractiveState result;
    if (something_found) {
      result = InspectedItemState.get_instance(picked_items, this, hdlg);
      hdlg.screen_messages.set_status_message(tm.getText("in_inspect_mode"));
    } else {
      result = this;
    }
    hdlg.repaint();
    return result;
  }

  public InteractiveState swap_pin(FloatPoint p_location) {
    ItemSelectionFilter selection_filter = new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> picked_items = hdlg.pick_items(p_location, selection_filter);
    InteractiveState result = this;
    if (!picked_items.isEmpty()) {
      Item first_item = picked_items.iterator().next();
      if (!(first_item instanceof Pin selected_pin)) {
        FRLogger.warn("MenuState.swap_pin: Pin expected");
        return this;
      }
      result = PinSwapState.get_instance(selected_pin, this, hdlg);
    } else {
      hdlg.screen_messages.set_status_message(tm.getText("no_pin_selected"));
    }
    hdlg.repaint();
    return result;
  }

  /**
   * Action to be taken when a key shortcut is pressed.
   */
  @Override
  public InteractiveState key_typed(char p_key_char) {
    InteractiveState curr_return_state = this;
    switch (p_key_char) {
      case 'b' -> hdlg.redo();
      case 'd' -> curr_return_state = DragMenuState.get_instance(hdlg);
      case 'e' -> curr_return_state = ExpandTestState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
      case 'g' -> hdlg.toggle_ratsnest();
      case 'i' -> curr_return_state = this.select_items(hdlg.get_current_mouse_position());
      case 'p' -> {
        hdlg.settings.set_push_enabled(!hdlg.settings.push_enabled);
        hdlg.get_panel().board_frame.refresh_windows();
      }
      case 'r' -> curr_return_state = RouteMenuState.get_instance(hdlg);
      case 's' -> curr_return_state = InspectMenuState.get_instance(hdlg);
      case 't' -> curr_return_state = RouteState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
      case 'u' -> hdlg.undo();
      case 'v' -> hdlg.toggle_clearance_violations();
      case 'w' -> curr_return_state = swap_pin(hdlg.get_current_mouse_position());
      case '+' -> {
        // increase the current layer to the next signal layer
        LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
        int current_layer_no = hdlg.settings.layer;
        do {
          ++current_layer_no;
        } while (current_layer_no < layer_structure.arr.length && !layer_structure.arr[current_layer_no].is_signal);

        if (current_layer_no < layer_structure.arr.length) {
          hdlg.set_current_layer(current_layer_no);
        }
      }
      case '-' -> {
        // decrease the current layer to the previous signal layer
        LayerStructure layer_structure = hdlg.get_routing_board().layer_structure;
        int current_layer_no = hdlg.settings.layer;
        do {
          --current_layer_no;
        } while (current_layer_no >= 0 && !layer_structure.arr[current_layer_no].is_signal);

        if (current_layer_no >= 0) {
          hdlg.set_current_layer(current_layer_no);
        }

      }
      default -> curr_return_state = super.key_typed(p_key_char);
    }
    return curr_return_state;
  }

  /**
   * Do nothing on complete.
   */
  @Override
  public InteractiveState complete() {
    return this;
  }

  /**
   * Do nothing on cancel.
   */
  @Override
  public InteractiveState cancel() {
    return this;
  }

  @Override
  public void set_toolbar() {
    hdlg.get_panel().board_frame.set_menu_toolbar();
  }
}
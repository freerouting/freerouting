package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import java.util.Set;
import java.util.TreeSet;

/**
 * Interactive state for selecting all items in a rectangle.
 */
public class InspectItemsInRegionState extends SelectRegionState {

  /**
   * Creates a new instance of InspectItemsInRegionState
   */
  private InspectItemsInRegionState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  /**
   * Returns a new instance of this class.
   */
  public static InspectItemsInRegionState get_instance(InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    return get_instance(null, p_parent_state, p_board_handling);
  }

  /**
   * Returns a new instance of this class with first point p_location.
   */
  public static InspectItemsInRegionState get_instance(FloatPoint p_location, InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    p_board_handling.display_layer_message();
    InspectItemsInRegionState new_instance = new InspectItemsInRegionState(p_parent_state, p_board_handling);
    new_instance.corner1 = p_location;
    new_instance.hdlg.screen_messages
        .set_status_message(new_instance.tm.getText("drag_left_mouse_button_to_selects_items_in_region"));
    return new_instance;
  }

  @Override
  public InteractiveState complete() {
    if (!hdlg.is_board_read_only()) {
      hdlg.screen_messages.set_status_message("");
      corner2 = hdlg.get_current_mouse_position();
      this.select_all_in_region();
    }
    return this.return_state;
  }

  /**
   * Selects all items in the rectangle defined by corner1 and corner2.
   */
  private void select_all_in_region() {
    IntPoint p1 = this.corner1.round();
    IntPoint p2 = this.corner2.round();

    IntBox b = new IntBox(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
    int select_layer;
    if (hdlg.interactiveSettings.select_on_all_visible_layers) {
      select_layer = -1;
    } else {
      select_layer = hdlg.interactiveSettings.layer;
    }
    Set<Item> found_items = hdlg.interactiveSettings.item_selection_filter
        .filter(hdlg.get_routing_board().overlapping_items(b, select_layer));
    if (hdlg.interactiveSettings.select_on_all_visible_layers) {
      // remove items, which are not visible
      Set<Item> visible_items = new TreeSet<>();
      for (Item curr_item : found_items) {
        for (int i = curr_item.first_layer(); i <= curr_item.last_layer(); i++) {
          if (hdlg.graphics_context.get_layer_visibility(i) > 0) {
            visible_items.add(curr_item);
            break;
          }
        }
      }
      found_items = visible_items;
    }
    boolean something_found = !found_items.isEmpty();
    if (something_found) {
      if (this.return_state instanceof InspectedItemState state) {
        state.get_item_list().addAll(found_items);
      } else {
        this.return_state = InspectedItemState.get_instance(found_items, this.return_state, hdlg);
      }
    } else {
      hdlg.screen_messages.set_status_message(tm.getText("nothing_selected"));
    }
  }
}
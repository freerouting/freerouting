package app.freerouting.interactive;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.geometry.planar.FloatPoint;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class implementing functionality when the mouse is dragged on a routing board
 */
public abstract class DragState extends InteractiveState {

  protected FloatPoint previous_location;
  protected boolean something_dragged;
  protected boolean observers_activated;

  /**
   * Creates a new instance of DragState
   */
  protected DragState(FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    previous_location = p_location;
  }

  /**
   * Returns a new instance of this state, if an item to drag was found at the
   * input location; null otherwise.
   */
  public static DragState get_instance(FloatPoint p_location, InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    p_board_handling.display_layer_message();
    Item item_to_move = null;
    int try_count = 1;
    if (p_board_handling.interactiveSettings.select_on_all_visible_layers) {
      try_count += p_board_handling.get_layer_count();
    }
    int curr_layer = p_board_handling.interactiveSettings.layer;
    int pick_layer = curr_layer;
    boolean item_found = false;

    for (int i = 0; i < try_count; i++) {
      if (i == 0
          || pick_layer != curr_layer && (p_board_handling.graphics_context.get_layer_visibility(pick_layer)) > 0) {
        Collection<Item> found_items = p_board_handling.get_routing_board().pick_items(p_location.round(), pick_layer,
            p_board_handling.interactiveSettings.item_selection_filter);
        for (Item curr_item : found_items) {
          item_found = true;
          if (curr_item instanceof Trace) {
            continue; // traces are not moved
          }
          if (!p_board_handling.interactiveSettings.drag_components_enabled && curr_item.get_component_no() != 0) {
            continue;
          }
          item_to_move = curr_item;
          if (curr_item instanceof DrillItem) {
            break; // drill items are preferred
          }
        }
        if (item_to_move != null) {
          break;
        }
      }
      // nothing found on settings.layer, try all visible layers
      pick_layer = i;
    }
    DragState result;
    if (item_to_move != null) {
      result = new DragItemState(item_to_move, p_location, p_parent_state, p_board_handling);
    } else if (!item_found) {
      result = new MakeSpaceState(p_location, p_parent_state, p_board_handling);
    } else {
      result = null;
    }
    if (result != null) {
      p_board_handling.hide_ratsnest();
    }
    return result;
  }

  public abstract InteractiveState move_to(FloatPoint p_to_location);

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    InteractiveState result = this.move_to(p_point);
    if (result != this) {
      // an error occurred
      Set<Integer> changed_nets = new TreeSet<>();
      hdlg.get_routing_board().undo(changed_nets);
      for (Integer changed_net : changed_nets) {
        hdlg.update_ratsnest(changed_net);
      }
    }
    if (this.something_dragged) {
    }
    return result;
  }

  @Override
  public InteractiveState complete() {
    return this.button_released();
  }

}
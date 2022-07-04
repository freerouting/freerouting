package app.freerouting.interactive;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.geometry.planar.FloatPoint;
import java.util.Iterator;

/** Class implementing functionality when the mouse is dragged on a routing board */
public abstract class DragState extends InteractiveState {
  protected FloatPoint previous_location;
  protected boolean something_dragged = false;
  protected boolean observers_activated = false;

  /** Creates a new instance of DragState */
  protected DragState(
      FloatPoint p_location,
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_parent_state, p_board_handling, p_activityReplayFile);
    previous_location = p_location;
  }

  /**
   * Returns a new instance of this state, if a item to drag was found at the input location; null
   * otherwise.
   */
  public static DragState get_instance(
      FloatPoint p_location,
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    p_board_handling.display_layer_messsage();
    Item item_to_move = null;
    int try_count = 1;
    if (p_board_handling.settings.select_on_all_visible_layers) {
      try_count += p_board_handling.get_layer_count();
    }
    int curr_layer = p_board_handling.settings.layer;
    int pick_layer = curr_layer;
    boolean item_found = false;

    for (int i = 0; i < try_count; ++i) {
      if (i == 0
          || pick_layer != curr_layer
              && (p_board_handling.graphics_context.get_layer_visibility(pick_layer)) > 0) {
        java.util.Collection<Item> found_items =
            p_board_handling
                .get_routing_board()
                .pick_items(
                    p_location.round(),
                    pick_layer,
                    p_board_handling.settings.item_selection_filter);
        Iterator<Item> it = found_items.iterator();
        while (it.hasNext()) {
          item_found = true;
          Item curr_item = it.next();
          if (curr_item instanceof Trace) {
            continue; // traces are not moved
          }
          if (!p_board_handling.settings.drag_components_enabled
              && curr_item.get_component_no() != 0) {
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
      result =
          new DragItemState(
              item_to_move, p_location, p_parent_state, p_board_handling, p_activityReplayFile);
    } else if (!item_found) {
      result =
          new MakeSpaceState(p_location, p_parent_state, p_board_handling, p_activityReplayFile);
    } else {
      result = null;
    }
    if (result != null) {
      p_board_handling.hide_ratsnest();
    }
    return result;
  }

  public abstract InteractiveState move_to(FloatPoint p_to_location);

  public InteractiveState mouse_dragged(FloatPoint p_point) {
    InteractiveState result = this.move_to(p_point);
    if (result != this) {
      // an error occured
      java.util.Set<Integer> changed_nets = new java.util.TreeSet<Integer>();
      hdlg.get_routing_board().undo(changed_nets);
      for (Integer changed_net : changed_nets) {
        hdlg.update_ratsnest(changed_net);
      }
    }
    if (this.something_dragged) {
      if (activityReplayFile != null) {
        activityReplayFile.add_corner(p_point);
      }
    }
    return result;
  }

  public InteractiveState complete() {
    return this.button_released();
  }

  public InteractiveState process_logfile_point(FloatPoint p_point) {
    return move_to(p_point);
  }
}

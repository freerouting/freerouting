package app.freerouting.interactive;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.geometry.planar.FloatPoint;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Class implementing functionality when the mouse is dragged on a routing board */
public abstract class DragState extends InteractiveState {

  protected FloatPoint previousLocation;
  protected boolean somethingDragged;
  protected boolean observersActivated;

  /** Creates a new instance of DragState */
  protected DragState(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    previousLocation = p_location;
  }

  /**
   * Returns a new instance of this state, if an item to drag was found at the input location; null
   * otherwise.
   */
  public static DragState get_instance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    p_board_handling.display_layer_message();
    Item itemToMove = null;
    int tryCount = 1;
    if (p_board_handling.getInteractiveSettings().get_select_on_all_visible_layers()) {
      tryCount += p_board_handling.get_layer_count();
    }
    int currLayer = p_board_handling.getInteractiveSettings().get_layer();
    int pickLayer = currLayer;
    boolean itemFound = false;

    for (int i = 0; i < tryCount; i++) {
      if (i == 0
          || pickLayer != currLayer
              && (p_board_handling.graphicsContext.get_layer_visibility(pickLayer)) > 0) {
        Collection<Item> foundItems =
            p_board_handling
                .get_routing_board()
                .pick_items(
                    p_location.round(),
                    pickLayer,
                    p_board_handling.getInteractiveSettings().get_item_selection_filter());
        for (Item currItem : foundItems) {
          itemFound = true;
          if (currItem instanceof Trace) {
            continue; // traces are not moved
          }
          if (!p_board_handling.getInteractiveSettings().get_drag_components_enabled()
              && currItem.get_component_no() != 0) {
            continue;
          }
          itemToMove = currItem;
          if (currItem instanceof DrillItem) {
            break; // drill items are preferred
          }
        }
        if (itemToMove != null) {
          break;
        }
      }
      // nothing found on settings.layer, try all visible layers
      pickLayer = i;
    }
    DragState result;
    if (itemToMove != null) {
      result = new DragItemState(itemToMove, p_location, p_parent_state, p_board_handling);
    } else if (!itemFound) {
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
      Set<Integer> changedNets = new TreeSet<>();
      hdlg.get_routing_board().undo(changedNets);
      for (Integer changed_net : changedNets) {
        hdlg.update_ratsnest(changed_net);
      }
    }
    if (this.somethingDragged) {}
    return result;
  }

  @Override
  public InteractiveState complete() {
    return this.button_released();
  }
}

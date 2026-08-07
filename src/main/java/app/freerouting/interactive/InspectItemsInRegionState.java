package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import java.util.Set;
import java.util.TreeSet;

/** Interactive state for selecting all items in a rectangle. */
public final class InspectItemsInRegionState extends SelectRegionState {

  /** Creates a new instance of InspectItemsInRegionState */
  private InspectItemsInRegionState(
      InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  /** Returns a new instance of this class. */
  public static InspectItemsInRegionState get_instance(
      InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    return get_instance(null, p_parent_state, p_board_handling);
  }

  /** Returns a new instance of this class with first point p_location. */
  public static InspectItemsInRegionState get_instance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    p_board_handling.display_layer_message();
    InspectItemsInRegionState newInstance =
        new InspectItemsInRegionState(p_parent_state, p_board_handling);
    newInstance.corner1 = p_location;
    newInstance.hdlg.screenMessages.set_status_message(
        newInstance.tm.getText("drag_left_mouse_button_to_select_items_in_region"));
    return newInstance;
  }

  @Override
  public InteractiveState complete() {
    if (!hdlg.is_board_read_only()) {
      hdlg.screenMessages.set_status_message("");
      corner2 = hdlg.get_current_mouse_position();
      this.select_all_in_region();
    }
    return this.returnState;
  }

  /** Selects all items in the rectangle defined by corner1 and corner2. */
  private void select_all_in_region() {
    IntPoint p1 = this.corner1.round();
    IntPoint p2 = this.corner2.round();

    IntBox b =
        new IntBox(
            Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
    int selectLayer;
    if (hdlg.getInteractiveSettings().get_select_on_all_visible_layers()) {
      selectLayer = -1;
    } else {
      selectLayer = hdlg.getInteractiveSettings().get_layer();
    }
    Set<Item> foundItems =
        hdlg.getInteractiveSettings()
            .get_item_selection_filter()
            .filter(hdlg.get_routing_board().overlapping_items(b, selectLayer));
    if (hdlg.getInteractiveSettings().get_select_on_all_visible_layers()) {
      // remove items, which are not visible
      Set<Item> visibleItems = new TreeSet<>();
      for (Item currItem : foundItems) {
        for (int i = currItem.first_layer(); i <= currItem.last_layer(); i++) {
          if (hdlg.graphicsContext.get_layer_visibility(i) > 0) {
            visibleItems.add(currItem);
            break;
          }
        }
      }
      foundItems = visibleItems;
    }
    boolean somethingFound = !foundItems.isEmpty();
    if (somethingFound) {
      if (this.returnState instanceof InspectedItemState state) {
        state.get_item_list().addAll(foundItems);
      } else {
        this.returnState = InspectedItemState.get_instance(foundItems, this.returnState, hdlg);
      }
    } else {
      hdlg.screenMessages.set_status_message(tm.getText("nothing_selected"));
    }
  }
}

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
  public static InspectItemsInRegionState getInstance(
      InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    return getInstance(null, p_parent_state, p_board_handling);
  }

  /** Returns a new instance of this class with first point p_location. */
  public static InspectItemsInRegionState getInstance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    p_board_handling.displayLayerMessage();
    InspectItemsInRegionState newInstance =
        new InspectItemsInRegionState(p_parent_state, p_board_handling);
    newInstance.corner1 = p_location;
    newInstance.hdlg.screenMessages.setStatusMessage(
        newInstance.tm.getText("drag_left_mouse_button_to_select_items_in_region"));
    return newInstance;
  }

  @Override
  public InteractiveState complete() {
    if (!hdlg.isBoardReadOnly()) {
      hdlg.screenMessages.setStatusMessage("");
      corner2 = hdlg.getCurrentMousePosition();
      this.selectAllInRegion();
    }
    return this.returnState;
  }

  /** Selects all items in the rectangle defined by corner1 and corner2. */
  private void selectAllInRegion() {
    IntPoint p1 = this.corner1.round();
    IntPoint p2 = this.corner2.round();

    IntBox b =
        new IntBox(
            Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
    int selectLayer;
    if (hdlg.getInteractiveSettings().getSelectOnAllVisibleLayers()) {
      selectLayer = -1;
    } else {
      selectLayer = hdlg.getInteractiveSettings().getLayer();
    }
    Set<Item> foundItems =
        hdlg.getInteractiveSettings()
            .getItemSelectionFilter()
            .filter(hdlg.getRoutingBoard().overlappingItems(b, selectLayer));
    if (hdlg.getInteractiveSettings().getSelectOnAllVisibleLayers()) {
      // remove items, which are not visible
      Set<Item> visibleItems = new TreeSet<>();
      for (Item currItem : foundItems) {
        for (int i = currItem.firstLayer(); i <= currItem.lastLayer(); i++) {
          if (hdlg.graphicsContext.getLayerVisibility(i) > 0) {
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
        state.getItemList().addAll(foundItems);
      } else {
        this.returnState = InspectedItemState.getInstance(foundItems, this.returnState, hdlg);
      }
    } else {
      hdlg.screenMessages.setStatusMessage(tm.getText("nothing_selected"));
    }
  }
}

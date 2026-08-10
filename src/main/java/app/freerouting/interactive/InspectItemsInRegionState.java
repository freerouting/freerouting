package app.freerouting.interactive;

import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import java.util.Set;
import java.util.TreeSet;

/** Interactive state for selecting all items in a rectangle. */
public final class InspectItemsInRegionState extends SelectRegionState {

  /** Creates a new instance of InspectItemsInRegionState. */
  private InspectItemsInRegionState(InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
  }

  /** Returns a new instance of this class. */
  public static InspectItemsInRegionState getInstance(
      InteractiveState parentState, GuiBoardManager boardHandling) {
    return getInstance(null, parentState, boardHandling);
  }

  /** Returns a new instance of this class with the first point at the given location. */
  public static InspectItemsInRegionState getInstance(
      FloatPoint location, InteractiveState parentState, GuiBoardManager boardHandling) {
    boardHandling.displayLayerMessage();
    InspectItemsInRegionState newInstance =
        new InspectItemsInRegionState(parentState, boardHandling);
    newInstance.corner1 = location;
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
    int selectLayer;
    if (hdlg.getInteractiveSettings().getSelectOnAllVisibleLayers()) {
      selectLayer = -1;
    } else {
      selectLayer = hdlg.getInteractiveSettings().getLayer();
    }
    Set<Item> foundItems = findItems(selectLayer);
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

  private static IntBox createSelectionBox(FloatPoint firstCorner, FloatPoint secondCorner) {
    IntPoint firstPoint = firstCorner.round();
    IntPoint secondPoint = secondCorner.round();
    return new IntBox(
        Math.min(firstPoint.x, secondPoint.x),
        Math.min(firstPoint.y, secondPoint.y),
        Math.max(firstPoint.x, secondPoint.x),
        Math.max(firstPoint.y, secondPoint.y));
  }

  private Set<Item> findItems(int selectLayer) {
    return hdlg
        .getInteractiveSettings()
        .getItemSelectionFilter()
        .filter(
            hdlg
                .getRoutingBoard()
                .overlappingItems(createSelectionBox(this.corner1, this.corner2), selectLayer));
  }
}

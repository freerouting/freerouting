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
  public static DragState getInstance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    p_board_handling.displayLayerMessage();
    Item itemToMove = null;
    int tryCount = 1;
    if (p_board_handling.getInteractiveSettings().getSelectOnAllVisibleLayers()) {
      tryCount += p_board_handling.getLayerCount();
    }
    int currLayer = p_board_handling.getInteractiveSettings().getLayer();
    int pickLayer = currLayer;
    boolean itemFound = false;

    for (int i = 0; i < tryCount; i++) {
      if (i == 0
          || pickLayer != currLayer
              && (p_board_handling.graphicsContext.getLayerVisibility(pickLayer)) > 0) {
        Collection<Item> foundItems =
            p_board_handling
                .getRoutingBoard()
                .pickItems(
                    p_location.round(),
                    pickLayer,
                    p_board_handling.getInteractiveSettings().getItemSelectionFilter());
        for (Item currItem : foundItems) {
          itemFound = true;
          if (currItem instanceof Trace) {
            continue; // traces are not moved
          }
          if (!p_board_handling.getInteractiveSettings().getDragComponentsEnabled()
              && currItem.getComponentNo() != 0) {
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
      p_board_handling.hideRatsnest();
    }
    return result;
  }

  public abstract InteractiveState moveTo(FloatPoint p_to_location);

  @Override
  public InteractiveState mouseDragged(FloatPoint p_point) {
    InteractiveState result = this.moveTo(p_point);
    if (result != this) {
      // an error occurred
      Set<Integer> changedNets = new TreeSet<>();
      hdlg.getRoutingBoard().undo(changedNets);
      for (Integer changed_net : changedNets) {
        hdlg.updateRatsnest(changed_net);
      }
    }
    if (this.somethingDragged) {}
    return result;
  }

  @Override
  public InteractiveState complete() {
    return this.buttonReleased();
  }
}

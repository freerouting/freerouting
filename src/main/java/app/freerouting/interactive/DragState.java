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
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
    previousLocation = pLocation;
  }

  /**
   * Returns a new instance of this state, if an item to drag was found at the input location; null
   * otherwise.
   */
  public static DragState getInstance(
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    pBoardHandling.displayLayerMessage();
    Item itemToMove = null;
    int tryCount = 1;
    if (pBoardHandling.getInteractiveSettings().getSelectOnAllVisibleLayers()) {
      tryCount += pBoardHandling.getLayerCount();
    }
    int currLayer = pBoardHandling.getInteractiveSettings().getLayer();
    int pickLayer = currLayer;
    boolean itemFound = false;

    for (int i = 0; i < tryCount; i++) {
      if (i == 0
          || pickLayer != currLayer
              && (pBoardHandling.graphicsContext.getLayerVisibility(pickLayer)) > 0) {
        Collection<Item> foundItems =
            pBoardHandling
                .getRoutingBoard()
                .pickItems(
                    pLocation.round(),
                    pickLayer,
                    pBoardHandling.getInteractiveSettings().getItemSelectionFilter());
        for (Item currItem : foundItems) {
          itemFound = true;
          if (currItem instanceof Trace) {
            continue; // traces are not moved
          }
          if (!pBoardHandling.getInteractiveSettings().getDragComponentsEnabled()
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
      result = new DragItemState(itemToMove, pLocation, pParentState, pBoardHandling);
    } else if (!itemFound) {
      result = new MakeSpaceState(pLocation, pParentState, pBoardHandling);
    } else {
      result = null;
    }
    if (result != null) {
      pBoardHandling.hideRatsnest();
    }
    return result;
  }

  public abstract InteractiveState moveTo(FloatPoint pToLocation);

  @Override
  public InteractiveState mouseDragged(FloatPoint pPoint) {
    InteractiveState result = this.moveTo(pPoint);
    if (result != this) {
      // an error occurred
      Set<Integer> changedNets = new TreeSet<>();
      hdlg.getRoutingBoard().undo(changedNets);
      for (Integer changedNet : changedNets) {
        hdlg.updateRatsnest(changedNet);
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

package app.freerouting.gui.interactive;

import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Trace;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.workspace.GuiBoardManager;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Class implementing functionality when the mouse is dragged on a routing board. */
public abstract class DragState extends InteractiveState {

  protected FloatPoint previousLocation;
  protected boolean somethingDragged;
  protected boolean observersActivated;

  /** Creates a new instance of DragState. */
  protected DragState(
      FloatPoint location, InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
    previousLocation = location;
  }

  /**
   * Returns a new instance of this state, if an item to drag was found at the input location; null
   * otherwise.
   */
  public static DragState getInstance(
      FloatPoint location, InteractiveState parentState, GuiBoardManager boardHandling) {
    boardHandling.displayLayerMessage();
    DragCandidate candidate = findItemToMove(location, boardHandling);
    Item itemToMove = candidate.item();
    boolean itemFound = candidate.itemFound();
    DragState result;
    if (itemToMove != null) {
      result = new DragItemState(itemToMove, location, parentState, boardHandling);
    } else if (!itemFound) {
      result = new MakeSpaceState(location, parentState, boardHandling);
    } else {
      result = null;
    }
    if (result != null) {
      boardHandling.hideRatsnest();
    }
    return result;
  }

  private static DragCandidate findItemToMove(FloatPoint location, GuiBoardManager boardHandling) {
    int tryCount = 1;
    if (boardHandling.getWorkspaceSettings().getSelectOnAllVisibleLayers()) {
      tryCount += boardHandling.getLayerCount();
    }
    int currentLayer = boardHandling.getWorkspaceSettings().getLayer();
    int pickLayer = currentLayer;
    boolean itemFound = false;

    for (int i = 0; i < tryCount; i++) {
      if (i == 0
          || pickLayer != currentLayer
              && (boardHandling.graphicsContext.getLayerVisibility(pickLayer)) > 0) {
        Collection<Item> foundItems =
            boardHandling
                .getRoutingBoard()
                .pickItems(
                    location.round(),
                    pickLayer,
                    boardHandling.getWorkspaceSettings().getItemSelectionFilter());
        DragCandidate candidate = selectItemToMove(foundItems, boardHandling);
        itemFound |= candidate.itemFound();
        if (candidate.item() != null) {
          return new DragCandidate(candidate.item(), itemFound);
        }
      }
      // Nothing found on the settings layer; try all visible layers.
      pickLayer = i;
    }
    return new DragCandidate(null, itemFound);
  }

  private static DragCandidate selectItemToMove(
      Collection<Item> foundItems, GuiBoardManager boardHandling) {
    Item itemToMove = null;
    boolean itemFound = false;
    for (Item currentItem : foundItems) {
      itemFound = true;
      if (currentItem instanceof Trace) {
        continue; // traces are not moved
      }
      if (!boardHandling.getWorkspaceSettings().getDragComponentsEnabled()
          && currentItem.getComponentNo() != 0) {
        continue;
      }
      itemToMove = currentItem;
      if (currentItem instanceof DrillItem) {
        break; // drill items are preferred
      }
    }
    return new DragCandidate(itemToMove, itemFound);
  }

  /**
   * Moves the state-managed item or route to the given location.
   *
   * @param toLocation the destination location
   * @return the resulting interaction state
   */
  public abstract InteractiveState moveTo(FloatPoint toLocation);

  @Override
  public InteractiveState mouseDragged(FloatPoint point) {
    InteractiveState result = this.moveTo(point);
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

  private record DragCandidate(Item item, boolean itemFound) {}
}

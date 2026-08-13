package app.freerouting.gui.interactive;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.Component;
import app.freerouting.board.Components;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Item;
import app.freerouting.board.LayerStructure;
import app.freerouting.board.Via;
import app.freerouting.core.BoardLibrary;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.session.GuiBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JPopupMenu;

/** Interactive state for moving selected items and components. */
public final class MoveItemState extends InteractiveState {

  private final Set<Item> itemList;
  private final Set<Component> componentList;

  /** In case of a component grid the first component is aligned to this grid. */
  private final Component gridSnapComponent;

  private final Collection<NetItems> netItemsList;
  private final boolean observersActivated;
  private IntPoint currentPosition;
  private IntPoint previousPosition;
  private Collection<ClearanceViolation> clearanceViolations;

  /** Creates a new instance of MoveItemState. */
  private MoveItemState(
      FloatPoint location,
      Set<Item> itemList,
      Set<Component> componentList,
      Component firstComponent,
      InteractiveState parentState,
      GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
    this.componentList = componentList;
    this.gridSnapComponent = firstComponent;
    this.currentPosition = location.round();
    this.previousPosition = currentPosition;
    BasicBoard routingBoard = hdlg.getRoutingBoard();
    this.observersActivated = !hdlg.getRoutingBoard().observersActive();
    if (this.observersActivated) {
      hdlg.getRoutingBoard().startNotifyObservers();
    }
    // make the situation restorable by undo
    routingBoard.generateSnapshot();

    for (Item currItem : itemList) {
      routingBoard.removeItem(currItem);
    }
    this.netItemsList = new LinkedList<>();
    this.itemList = new TreeSet<>();

    for (Item currItem : itemList) {
      // Copy the items in p_item_list, because otherwise the undo algorithm will not
      // work.
      Item copiedItem = currItem.copy(0);
      for (int i = 0; i < currItem.netCount(); i++) {
        addToNetItemsList(copiedItem, currItem.getNetNo(i));
      }
      this.itemList.add(copiedItem);
    }
  }

  /**
   * Returns a new instance of MoveItemState, or {@code null} if the selected items do not belong to
   * a single component.
   */
  public static MoveItemState getInstance(
      FloatPoint location,
      Collection<Item> itemList,
      InteractiveState parentState,
      GuiBoardManager boardHandling) {

    TextManager tm = new TextManager(InteractiveState.class, boardHandling.getLocale());

    if (itemList.isEmpty()) {
      boardHandling.screenMessages.setStatusMessage(
          tm.getText("move_component_failed_because_no_item_selected"));
      return null;
    }
    // extend p_item_list to full components
    Set<Item> allItems = new TreeSet<>();
    Set<Component> componentList = new TreeSet<>();
    BasicBoard routingBoard = boardHandling.getRoutingBoard();
    Component gridSnapComponent = null;
    for (Item currItem : itemList) {
      if (currItem.getComponentNo() > 0) {
        Component currComponent = routingBoard.components.get(currItem.getComponentNo());
        if (currComponent == null) {
          FRLogger.warn("MoveComponentState.get_instance inconsistent component number");
          return null;
        }
        if (gridSnapComponent == null
            && (boardHandling.getInteractiveSettings().getHorizontalComponentGrid() > 0
                || boardHandling.getInteractiveSettings().getVerticalComponentGrid() > 0)) {
          gridSnapComponent = currComponent;
        }
        if (!componentList.contains(currComponent)) {
          Collection<Item> componentItems = routingBoard.getComponentItems(currComponent.no);
          for (Item currComponentItem : componentItems) {
            componentList.add(currComponent);
            allItems.add(currComponentItem);
          }
        }
      } else {
        allItems.add(currItem);
      }
    }
    boolean moveOk = true;
    Set<Item> fixedItems = new TreeSet<>();
    Set<Item> obstacleItems = new TreeSet<>();
    Set<Item> addItems = new TreeSet<>();
    for (Item currItem : allItems) {
      if (currItem.isUserFixed()) {
        boardHandling.screenMessages.setStatusMessage(
            tm.getText("some_items_cannot_be_moved_because_they_are_fixed"));
        moveOk = false;
        obstacleItems.add(currItem);
        fixedItems.add(currItem);
      } else if (currItem.isConnected()) {
        // Check if the whole connected set is inside the selected items,
        // and add the items of the connected set to the move list in this case.
        // Conduction areas are ignored, because otherwise components with
        // pins contacted to a plane could never be moved.
        boolean itemMovable = true;
        Collection<Item> contacts = currItem.getConnectedSet(-1, true);
        {
          for (Item currContact : contacts) {
            if (currContact instanceof ConductionArea) {

              continue;
            }
            if (currContact.isUserFixed()) {
              itemMovable = false;
              fixedItems.add(currContact);
            } else if (currContact.getComponentNo() != 0) {
              Component currComponent = routingBoard.components.get(currContact.getComponentNo());
              if (!componentList.contains(currComponent)) {
                itemMovable = false;
              }
            }
            if (itemMovable) {
              addItems.add(currContact);
            } else {
              obstacleItems.add(currContact);
            }
          }
        }
        if (!itemMovable) {
          moveOk = false;
        }
      }
    }
    if (!moveOk) {
      if (parentState instanceof InspectedItemState state) {
        if (!fixedItems.isEmpty()) {
          state.getItemList().addAll(fixedItems);
          boardHandling.screenMessages.setStatusMessage(
              tm.getText("please_unfix_selected_items_before_moving"));
        } else {
          state.getItemList().addAll(obstacleItems);
          boardHandling.screenMessages.setStatusMessage(
              tm.getText("please_unroute_or_extend_selection_before_moving"));
        }
      }
      return null;
    }
    allItems.addAll(addItems);
    return new MoveItemState(
        location,
        allItems,
        componentList,
        gridSnapComponent,
        parentState.returnState,
        boardHandling);
  }

  private void addToNetItemsList(Item item, int netNo) {
    for (NetItems currItems : this.netItemsList) {
      if (currItems.netNo == netNo) {
        // list for p_net_no exists already
        currItems.items.add(item);
        return;
      }
    }
    Collection<Item> newItemList = hdlg.getRoutingBoard().getConnectableItems(netNo);
    newItemList.add(item);
    NetItems newNetItems = new NetItems(netNo, newItemList);
    this.netItemsList.add(newNetItems);
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    move(hdlg.getCurrentMousePosition());
    return this;
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    return this.complete();
  }

  @Override
  public InteractiveState complete() {
    for (Item currItem : this.itemList) {
      if (currItem.clearanceViolationCount() > 0) {
        hdlg.screenMessages.setStatusMessage(tm.getText("insertion_failed_because_of_obstacles"));
        return this;
      }
    }
    BasicBoard routingBoard = hdlg.getRoutingBoard();
    for (Item currItem : this.itemList) {
      routingBoard.insertItem(currItem);
    }

    // let the observers synchronize the moving
    for (Component currComponent : this.componentList) {
      routingBoard.communication.observers.notifyMoved(currComponent);
    }

    for (NetItems currNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currNetItems.netNo);
    }
    hdlg.screenMessages.setStatusMessage(tm.getText("move_completed"));
    hdlg.repaint();
    return this.returnState;
  }

  @Override
  public InteractiveState cancel() {
    hdlg.getRoutingBoard().undo(null);
    for (NetItems currNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currNetItems.netNo);
    }
    return this.returnState;
  }

  @Override
  public InteractiveState mouseWheelMoved(int rotation) {
    if (hdlg.getInteractiveSettings().getZoomWithWheel()) {
      super.mouseWheelMoved(rotation);
    } else {
      this.rotate(-rotation);
    }
    return this;
  }

  /** Changes the position of the items in the list to the given location. */
  private void move(FloatPoint newPosition) {
    currentPosition = newPosition.round();
    if (!currentPosition.equals(previousPosition)) {
      Vector translateVector = currentPosition.differenceBy(previousPosition);
      if (this.gridSnapComponent != null) {
        translateVector = adjustToPlacementGrid(translateVector);
      }
      Components components = hdlg.getRoutingBoard().components;
      for (Component currComponent : this.componentList) {
        components.move(currComponent.no, translateVector);
      }
      this.clearanceViolations = new LinkedList<>();
      for (Item currItem : this.itemList) {
        currItem.translateBy(translateVector);
        this.clearanceViolations.addAll(currItem.clearanceViolations());
      }
      previousPosition = currentPosition;
      for (NetItems currNetItems : this.netItemsList) {
        this.hdlg.updateRatsnest(currNetItems.netNo, currNetItems.items);
      }
      hdlg.repaint();
    }
  }

  private Vector adjustToPlacementGrid(Vector vector) {
    Point newComponentLocation = this.gridSnapComponent.getLocation().translateBy(vector);
    IntPoint roundedComponentLocation =
        newComponentLocation
            .toFloat()
            .roundToGrid(
                hdlg.getInteractiveSettings().getHorizontalComponentGrid(),
                hdlg.getInteractiveSettings().getVerticalComponentGrid());
    Vector adjustment = roundedComponentLocation.differenceBy(newComponentLocation);
    Vector result = vector.add(adjustment);
    this.currentPosition = this.previousPosition.translateBy(result).toFloat().round();
    return vector.add(adjustment);
  }

  /** Turns the items in the list by the given number of 90-degree increments. */
  public void turn90Degree(int factor) {
    if (factor == 0) {
      return;
    }
    Components components = hdlg.getRoutingBoard().components;
    for (Component currComponent : this.componentList) {
      components.turn90Degree(currComponent.no, factor, currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    for (Item currItem : this.itemList) {
      currItem.turn90Degree(factor, currentPosition);
      this.clearanceViolations.addAll(currItem.clearanceViolations());
    }
    for (NetItems currNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currNetItems.netNo, currNetItems.items);
    }
    hdlg.repaint();
  }

  /** Rotates the items in the list by the given angle around the current position. */
  public void rotate(double angleInDegree) {
    if (angleInDegree == 0) {
      return;
    }
    Components components = hdlg.getRoutingBoard().components;
    for (Component currComponent : this.componentList) {
      components.rotate(currComponent.no, angleInDegree, this.currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    FloatPoint floatPosition = this.currentPosition.toFloat();
    for (Item currItem : this.itemList) {
      currItem.rotateApprox(angleInDegree, floatPosition);
      this.clearanceViolations.addAll(currItem.clearanceViolations());
    }
    for (NetItems currNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currNetItems.netNo, currNetItems.items);
    }
    hdlg.repaint();
  }

  /** Turns the items in the list by the given number of 45-degree increments. */
  public void turn45Degree(int factor) {
    if (factor % 2 == 0) {
      turn90Degree(factor / 2);
    } else {
      rotate(factor * 45);
    }
  }

  /** Changes the placement side of the items in the list. */
  public void changePlacementSide() {
    // Check, that all items can be mirrored
    LayerStructure layerStructure = hdlg.getRoutingBoard().layerStructure;
    BoardLibrary boardLibrary = hdlg.getRoutingBoard().library;
    boolean placementSideChangable = true;
    for (Item currItem : itemList) {
      if (currItem instanceof Via via) {
        if (boardLibrary.getMirroredViaPadstack(via.getPadstack()) == null) {
          placementSideChangable = false;
          break;
        }
      } else if (currItem.firstLayer() == currItem.lastLayer()) {
        int newLayerNo = hdlg.getLayerCount() - currItem.firstLayer() - 1;
        if (!layerStructure.arr[newLayerNo].isSignal) {
          placementSideChangable = false;
          break;
        }
      }
    }
    if (!placementSideChangable) {
      hdlg.screenMessages.setStatusMessage(tm.getText("cannot_change_placement_side"));
      return;
    }

    Components components = hdlg.getRoutingBoard().components;
    for (Component currComponent : this.componentList) {
      components.changeSide(currComponent.no, currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    for (Item currItem : this.itemList) {
      currItem.changePlacementSide(currentPosition);
      this.clearanceViolations.addAll(currItem.clearanceViolations());
    }
    for (NetItems currNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currNetItems.netNo, currNetItems.items);
    }
    hdlg.repaint();
  }

  /** Resets the rotation of the moved components. */
  public void resetRotation() {
    Component componentToReset = null;
    for (Component currComponent : this.componentList) {
      if (componentToReset == null) {
        componentToReset = currComponent;
      } else if (componentToReset.getRotationInDegree() != currComponent.getRotationInDegree()) {
        hdlg.screenMessages.setStatusMessage(
            tm.getText("unable_to_reset_components_with_different_rotations"));
        return;
      }
    }
    if (componentToReset == null) {
      return;
    }
    double rotation = componentToReset.getRotationInDegree();
    if (!hdlg.getRoutingBoard().components.getFlipStyleRotateFirst()
        || componentToReset.placedOnFront()) {
      rotation = 360 - rotation;
    }
    rotate(rotation);
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState keyTyped(char keyChar) {
    InteractiveState currReturnState = this;
    switch (keyChar) {
      case '+' -> turn90Degree(1);
      case '*' -> turn90Degree(2);
      case '-' -> turn90Degree(3);
      case '/' -> changePlacementSide();
      case 'r' -> hdlg.getInteractiveSettings().setZoomWithWheel(false);
      case 'z' -> hdlg.getInteractiveSettings().setZoomWithWheel(true);
      default -> currReturnState = super.keyTyped(keyChar);
    }
    return currReturnState;
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuMove;
  }

  @Override
  public String getHelpId() {
    return "MoveItemState";
  }

  @Override
  public void draw(Graphics graphics) {
    if (this.itemList == null) {
      return;
    }
    for (Item currItem : this.itemList) {
      BoardRenderer.drawOverlayItem(currItem, graphics, hdlg.graphicsContext);
    }
    if (this.clearanceViolations != null) {
      Color drawColor = hdlg.graphicsContext.getViolationsColor();
      for (ClearanceViolation currViolation : this.clearanceViolations) {
        hdlg.graphicsContext.fillArea(currViolation.shape, graphics, drawColor, 1);
      }
    }
  }

  private static class NetItems {

    final int netNo;
    final Collection<Item> items;

    NetItems(int netNo, Collection<Item> items) {
      this.netNo = netNo;
      this.items = items;
    }
  }
}

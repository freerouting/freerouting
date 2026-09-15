package app.freerouting.gui.interactive;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.Components;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.core.library.BoardLibrary;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.workspace.GuiBoardManager;
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

    for (Item currentItem : itemList) {
      routingBoard.removeItem(currentItem);
    }
    this.netItemsList = new LinkedList<>();
    this.itemList = new TreeSet<>();

    for (Item currentItem : itemList) {
      // Copy the items in itemList, because otherwise the undo algorithm will not
      // work.
      Item copiedItem = currentItem.copy(0);
      for (int i = 0; i < currentItem.netCount(); i++) {
        addToNetItemsList(copiedItem, currentItem.getNetNumber(i));
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
    // extend itemList to full components
    Set<Item> allItems = new TreeSet<>();
    Set<Component> componentList = new TreeSet<>();
    BasicBoard routingBoard = boardHandling.getRoutingBoard();
    Component gridSnapComponent = null;
    for (Item currentItem : itemList) {
      if (currentItem.getComponentId() > 0) {
        Component currentComponent = routingBoard.components.get(currentItem.getComponentId());
        if (currentComponent == null) {
          FRLogger.warn("MoveComponentState.get_instance inconsistent component number");
          return null;
        }
        if (gridSnapComponent == null
            && (boardHandling.getWorkspaceSettings().getHorizontalComponentGrid() > 0
                || boardHandling.getWorkspaceSettings().getVerticalComponentGrid() > 0)) {
          gridSnapComponent = currentComponent;
        }
        if (!componentList.contains(currentComponent)) {
          Collection<Item> componentItems = routingBoard.getComponentItems(currentComponent.id);
          for (Item currentComponentItem : componentItems) {
            componentList.add(currentComponent);
            allItems.add(currentComponentItem);
          }
        }
      } else {
        allItems.add(currentItem);
      }
    }
    boolean moveOk = true;
    Set<Item> fixedItems = new TreeSet<>();
    Set<Item> obstacleItems = new TreeSet<>();
    Set<Item> addItems = new TreeSet<>();
    for (Item currentItem : allItems) {
      if (currentItem.isUserFixed()) {
        boardHandling.screenMessages.setStatusMessage(
            tm.getText("some_items_cannot_be_moved_because_they_are_fixed"));
        moveOk = false;
        obstacleItems.add(currentItem);
        fixedItems.add(currentItem);
      } else if (currentItem.isConnected()) {
        // Check if the whole connected set is inside the selected items,
        // and add the items of the connected set to the move list in this case.
        // Conduction areas are ignored, because otherwise components with
        // pins contacted to a plane could never be moved.
        boolean itemMovable = true;
        Collection<Item> contacts = currentItem.getConnectedSet(-1, true);
        {
          for (Item currentContact : contacts) {
            if (currentContact instanceof ConductionArea) {

              continue;
            }
            if (currentContact.isUserFixed()) {
              itemMovable = false;
              fixedItems.add(currentContact);
            } else if (currentContact.getComponentId() != 0) {
              Component currentComponent =
                  routingBoard.components.get(currentContact.getComponentId());
              if (!componentList.contains(currentComponent)) {
                itemMovable = false;
              }
            }
            if (itemMovable) {
              addItems.add(currentContact);
            } else {
              obstacleItems.add(currentContact);
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

  private void addToNetItemsList(Item item, int netNumber) {
    for (NetItems currentItems : this.netItemsList) {
      if (currentItems.netNumber == netNumber) {
        // list for netNumber exists already
        currentItems.items.add(item);
        return;
      }
    }
    Collection<Item> newItemList = hdlg.getRoutingBoard().getConnectableItems(netNumber);
    newItemList.add(item);
    NetItems newNetItems = new NetItems(netNumber, newItemList);
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
    for (Item currentItem : this.itemList) {
      if (currentItem.clearanceViolationCount() > 0) {
        hdlg.screenMessages.setStatusMessage(tm.getText("insertion_failed_because_of_obstacles"));
        return this;
      }
    }
    BasicBoard routingBoard = hdlg.getRoutingBoard();
    for (Item currentItem : this.itemList) {
      routingBoard.insertItem(currentItem);
    }

    // let the observers synchronize the moving
    for (Component currentComponent : this.componentList) {
      routingBoard.communication.observers.notifyMoved(currentComponent);
    }

    for (NetItems currentNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currentNetItems.netNumber);
    }
    hdlg.screenMessages.setStatusMessage(tm.getText("move_completed"));
    hdlg.repaint();
    return this.returnState;
  }

  @Override
  public InteractiveState cancel() {
    hdlg.getRoutingBoard().undo(null);
    for (NetItems currentNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currentNetItems.netNumber);
    }
    return this.returnState;
  }

  @Override
  public InteractiveState mouseWheelMoved(int rotation) {
    if (hdlg.getWorkspaceSettings().getZoomWithWheel()) {
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
      for (Component currentComponent : this.componentList) {
        components.move(currentComponent.id, translateVector);
      }
      this.clearanceViolations = new LinkedList<>();
      for (Item currentItem : this.itemList) {
        currentItem.translateBy(translateVector);
        this.clearanceViolations.addAll(currentItem.clearanceViolations());
      }
      previousPosition = currentPosition;
      for (NetItems currentNetItems : this.netItemsList) {
        this.hdlg.updateRatsnest(currentNetItems.netNumber, currentNetItems.items);
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
                hdlg.getWorkspaceSettings().getHorizontalComponentGrid(),
                hdlg.getWorkspaceSettings().getVerticalComponentGrid());
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
    for (Component currentComponent : this.componentList) {
      components.turn90Degree(currentComponent.id, factor, currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    for (Item currentItem : this.itemList) {
      currentItem.turn90Degree(factor, currentPosition);
      this.clearanceViolations.addAll(currentItem.clearanceViolations());
    }
    for (NetItems currentNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currentNetItems.netNumber, currentNetItems.items);
    }
    hdlg.repaint();
  }

  /** Rotates the items in the list by the given angle around the current position. */
  public void rotate(double angleInDegree) {
    if (angleInDegree == 0) {
      return;
    }
    Components components = hdlg.getRoutingBoard().components;
    for (Component currentComponent : this.componentList) {
      components.rotate(currentComponent.id, angleInDegree, this.currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    FloatPoint floatPosition = this.currentPosition.toFloat();
    for (Item currentItem : this.itemList) {
      currentItem.rotateApprox(angleInDegree, floatPosition);
      this.clearanceViolations.addAll(currentItem.clearanceViolations());
    }
    for (NetItems currentNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currentNetItems.netNumber, currentNetItems.items);
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
    for (Item currentItem : itemList) {
      if (currentItem instanceof Via via) {
        if (boardLibrary.getMirroredViaPadstack(via.getPadstack()) == null) {
          placementSideChangable = false;
          break;
        }
      } else if (currentItem.firstLayer() == currentItem.lastLayer()) {
        int newLayerNo = hdlg.getLayerCount() - currentItem.firstLayer() - 1;
        if (!layerStructure.layers[newLayerNo].isSignal) {
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
    for (Component currentComponent : this.componentList) {
      components.changeSide(currentComponent.id, currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    for (Item currentItem : this.itemList) {
      currentItem.changePlacementSide(currentPosition);
      this.clearanceViolations.addAll(currentItem.clearanceViolations());
    }
    for (NetItems currentNetItems : this.netItemsList) {
      this.hdlg.updateRatsnest(currentNetItems.netNumber, currentNetItems.items);
    }
    hdlg.repaint();
  }

  /** Resets the rotation of the moved components. */
  public void resetRotation() {
    Component componentToReset = null;
    for (Component currentComponent : this.componentList) {
      if (componentToReset == null) {
        componentToReset = currentComponent;
      } else if (componentToReset.getRotationInDegree() != currentComponent.getRotationInDegree()) {
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
    InteractiveState currentReturnState = this;
    switch (keyChar) {
      case '+' -> turn90Degree(1);
      case '*' -> turn90Degree(2);
      case '-' -> turn90Degree(3);
      case '/' -> changePlacementSide();
      case 'r' -> hdlg.getWorkspaceSettings().setZoomWithWheel(false);
      case 'z' -> hdlg.getWorkspaceSettings().setZoomWithWheel(true);
      default -> currentReturnState = super.keyTyped(keyChar);
    }
    return currentReturnState;
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
    for (Item currentItem : this.itemList) {
      BoardRenderer.drawOverlayItem(currentItem, graphics, hdlg.graphicsContext);
    }
    if (this.clearanceViolations != null) {
      Color drawColor = hdlg.graphicsContext.getViolationsColor();
      for (ClearanceViolation currentViolation : this.clearanceViolations) {
        hdlg.graphicsContext.fillArea(currentViolation.shape, graphics, drawColor, 1);
      }
    }
  }

  private static class NetItems {

    final int netNumber;
    final Collection<Item> items;

    NetItems(int netNumber, Collection<Item> items) {
      this.netNumber = netNumber;
      this.items = items;
    }
  }
}

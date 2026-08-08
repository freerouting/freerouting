package app.freerouting.interactive;

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
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JPopupMenu;

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

  /** Creates a new instance of MoveComponentState */
  private MoveItemState(
      FloatPoint p_location,
      Set<Item> p_item_list,
      Set<Component> p_component_list,
      Component p_first_component,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    this.componentList = p_component_list;
    this.gridSnapComponent = p_first_component;
    this.currentPosition = p_location.round();
    this.previousPosition = currentPosition;
    BasicBoard routingBoard = hdlg.getRoutingBoard();
    this.observersActivated = !hdlg.getRoutingBoard().observersActive();
    if (this.observersActivated) {
      hdlg.getRoutingBoard().startNotifyObservers();
    }
    // make the situation restorable by undo
    routingBoard.generateSnapshot();

    for (Item currItem : p_item_list) {
      routingBoard.removeItem(currItem);
    }
    this.netItemsList = new LinkedList<>();
    this.itemList = new TreeSet<>();

    for (Item currItem : p_item_list) {
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
   * Returns a new instance of MoveComponentState, or null, if the items of p_itemlist do not belong
   * to a single component.
   */
  public static MoveItemState getInstance(
      FloatPoint p_location,
      Collection<Item> p_item_list,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {

    TextManager tm = new TextManager(InteractiveState.class, p_board_handling.getLocale());

    if (p_item_list.isEmpty()) {
      p_board_handling.screenMessages.setStatusMessage(
          tm.getText("move_component_failed_because_no_item_selected"));
      return null;
    }
    // extend p_item_list to full components
    Set<Item> itemList = new TreeSet<>();
    Set<Component> componentList = new TreeSet<>();
    BasicBoard routingBoard = p_board_handling.getRoutingBoard();
    Component gridSnapComponent = null;
    for (Item currItem : p_item_list) {
      if (currItem.getComponentNo() > 0) {
        Component currComponent = routingBoard.components.get(currItem.getComponentNo());
        if (currComponent == null) {
          FRLogger.warn("MoveComponentState.get_instance inconsistent component number");
          return null;
        }
        if (gridSnapComponent == null
            && (p_board_handling.getInteractiveSettings().getHorizontalComponentGrid() > 0
                || p_board_handling.getInteractiveSettings().getVerticalComponentGrid() > 0)) {
          gridSnapComponent = currComponent;
        }
        if (!componentList.contains(currComponent)) {
          Collection<Item> componentItems = routingBoard.getComponentItems(currComponent.no);
          for (Item curr_component_item : componentItems) {
            componentList.add(currComponent);
            itemList.add(curr_component_item);
          }
        }
      } else {
        itemList.add(currItem);
      }
    }
    Set<Item> fixedItems = new TreeSet<>();
    Set<Item> obstacleItems = new TreeSet<>();
    Set<Item> addItems = new TreeSet<>();
    boolean moveOk = true;
    for (Item currItem : itemList) {
      if (currItem.isUserFixed()) {
        p_board_handling.screenMessages.setStatusMessage(
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
      if (p_parent_state instanceof InspectedItemState state) {
        if (!fixedItems.isEmpty()) {
          state.getItemList().addAll(fixedItems);
          p_board_handling.screenMessages.setStatusMessage(
              tm.getText("please_unfix_selected_items_before_moving"));
        } else {
          state.getItemList().addAll(obstacleItems);
          p_board_handling.screenMessages.setStatusMessage(
              tm.getText("please_unroute_or_extend_selection_before_moving"));
        }
      }
      return null;
    }
    itemList.addAll(addItems);
    return new MoveItemState(
        p_location,
        itemList,
        componentList,
        gridSnapComponent,
        p_parent_state.returnState,
        p_board_handling);
  }

  private void addToNetItemsList(Item p_item, int p_net_no) {
    for (NetItems curr_items : this.netItemsList) {
      if (curr_items.netNo == p_net_no) {
        // list for p_net_no exists already
        curr_items.items.add(p_item);
        return;
      }
    }
    Collection<Item> newItemList = hdlg.getRoutingBoard().getConnectableItems(p_net_no);
    newItemList.add(p_item);
    NetItems newNetItems = new NetItems(p_net_no, newItemList);
    this.netItemsList.add(newNetItems);
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    move(hdlg.getCurrentMousePosition());
    return this;
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint p_location) {
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

    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.updateRatsnest(curr_net_items.netNo);
    }
    hdlg.screenMessages.setStatusMessage(tm.getText("move_completed"));
    hdlg.repaint();
    return this.returnState;
  }

  @Override
  public InteractiveState cancel() {
    hdlg.getRoutingBoard().undo(null);
    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.updateRatsnest(curr_net_items.netNo);
    }
    return this.returnState;
  }

  @Override
  public InteractiveState mouseWheelMoved(int p_rotation) {
    if (hdlg.getInteractiveSettings().getZoomWithWheel()) {
      super.mouseWheelMoved(p_rotation);
    } else {
      this.rotate(-p_rotation);
    }
    return this;
  }

  /** Changes the position of the items in the list to p_new_location. */
  private void move(FloatPoint p_new_position) {
    currentPosition = p_new_position.round();
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
      for (NetItems curr_net_items : this.netItemsList) {
        this.hdlg.updateRatsnest(curr_net_items.netNo, curr_net_items.items);
      }
      hdlg.repaint();
    }
  }

  private Vector adjustToPlacementGrid(Vector p_vector) {
    Point newComponentLocation = this.gridSnapComponent.getLocation().translateBy(p_vector);
    IntPoint roundedComponentLocation =
        newComponentLocation
            .toFloat()
            .roundToGrid(
                hdlg.getInteractiveSettings().getHorizontalComponentGrid(),
                hdlg.getInteractiveSettings().getVerticalComponentGrid());
    Vector adjustment = roundedComponentLocation.differenceBy(newComponentLocation);
    Vector result = p_vector.add(adjustment);
    this.currentPosition = this.previousPosition.translateBy(result).toFloat().round();
    return p_vector.add(adjustment);
  }

  /** Turns the items in the list by p_factor times 90 degree around the current position. */
  public void turn90Degree(int p_factor) {
    if (p_factor == 0) {
      return;
    }
    Components components = hdlg.getRoutingBoard().components;
    for (Component currComponent : this.componentList) {
      components.turn90Degree(currComponent.no, p_factor, currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    for (Item currItem : this.itemList) {
      currItem.turn90Degree(p_factor, currentPosition);
      this.clearanceViolations.addAll(currItem.clearanceViolations());
    }
    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.updateRatsnest(curr_net_items.netNo, curr_net_items.items);
    }
    hdlg.repaint();
  }

  public void rotate(double p_angle_in_degree) {
    if (p_angle_in_degree == 0) {
      return;
    }
    Components components = hdlg.getRoutingBoard().components;
    for (Component currComponent : this.componentList) {
      components.rotate(currComponent.no, p_angle_in_degree, this.currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    FloatPoint floatPosition = this.currentPosition.toFloat();
    for (Item currItem : this.itemList) {
      currItem.rotateApprox(p_angle_in_degree, floatPosition);
      this.clearanceViolations.addAll(currItem.clearanceViolations());
    }
    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.updateRatsnest(curr_net_items.netNo, curr_net_items.items);
    }
    hdlg.repaint();
  }

  /** Turns the items in the list by p_factor times 90 degree around the current position. */
  public void turn45Degree(int p_factor) {
    if (p_factor % 2 == 0) {
      turn90Degree(p_factor / 2);
    } else {
      rotate(p_factor * 45);
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
    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.updateRatsnest(curr_net_items.netNo, curr_net_items.items);
    }
    hdlg.repaint();
  }

  public void resetRotation() {
    Component componentToReset = null;
    for (Component currComponent : this.componentList) {
      if (componentToReset == null) {
        componentToReset = currComponent;
      } else if (componentToReset.getRotationInDegree()
          != currComponent.getRotationInDegree()) {
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
  public InteractiveState keyTyped(char p_key_char) {
    InteractiveState currReturnState = this;
    switch (p_key_char) {
      case '+' -> turn90Degree(1);
      case '*' -> turn90Degree(2);
      case '-' -> turn90Degree(3);
      case '/' -> changePlacementSide();
      case 'r' -> hdlg.getInteractiveSettings().setZoomWithWheel(false);
      case 'z' -> hdlg.getInteractiveSettings().setZoomWithWheel(true);
      default -> currReturnState = super.keyTyped(p_key_char);
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
  public void draw(Graphics p_graphics) {
    if (this.itemList == null) {
      return;
    }
    for (Item currItem : this.itemList) {
      currItem.draw(p_graphics, hdlg.graphicsContext);
    }
    if (this.clearanceViolations != null) {
      Color drawColor = hdlg.graphicsContext.getViolationsColor();
      for (ClearanceViolation currViolation : this.clearanceViolations) {
        hdlg.graphicsContext.fillArea(currViolation.shape, p_graphics, drawColor, 1);
      }
    }
  }

  private static class NetItems {

    final int netNo;
    final Collection<Item> items;

    NetItems(int p_net_no, Collection<Item> p_items) {
      netNo = p_net_no;
      items = p_items;
    }
  }
}

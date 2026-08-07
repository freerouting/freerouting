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
    BasicBoard routingBoard = hdlg.get_routing_board();
    this.observersActivated = !hdlg.get_routing_board().observers_active();
    if (this.observersActivated) {
      hdlg.get_routing_board().start_notify_observers();
    }
    // make the situation restorable by undo
    routingBoard.generate_snapshot();

    for (Item currItem : p_item_list) {
      routingBoard.remove_item(currItem);
    }
    this.netItemsList = new LinkedList<>();
    this.itemList = new TreeSet<>();

    for (Item currItem : p_item_list) {
      // Copy the items in p_item_list, because otherwise the undo algorithm will not
      // work.
      Item copiedItem = currItem.copy(0);
      for (int i = 0; i < currItem.net_count(); i++) {
        add_to_net_items_list(copiedItem, currItem.get_net_no(i));
      }
      this.itemList.add(copiedItem);
    }
  }

  /**
   * Returns a new instance of MoveComponentState, or null, if the items of p_itemlist do not belong
   * to a single component.
   */
  public static MoveItemState get_instance(
      FloatPoint p_location,
      Collection<Item> p_item_list,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {

    TextManager tm = new TextManager(InteractiveState.class, p_board_handling.get_locale());

    if (p_item_list.isEmpty()) {
      p_board_handling.screenMessages.set_status_message(
          tm.getText("move_component_failed_because_no_item_selected"));
      return null;
    }
    // extend p_item_list to full components
    Set<Item> itemList = new TreeSet<>();
    Set<Component> componentList = new TreeSet<>();
    BasicBoard routingBoard = p_board_handling.get_routing_board();
    Component gridSnapComponent = null;
    for (Item currItem : p_item_list) {
      if (currItem.get_component_no() > 0) {
        Component currComponent = routingBoard.components.get(currItem.get_component_no());
        if (currComponent == null) {
          FRLogger.warn("MoveComponentState.get_instance inconsistent component number");
          return null;
        }
        if (gridSnapComponent == null
            && (p_board_handling.getInteractiveSettings().get_horizontal_component_grid() > 0
                || p_board_handling.getInteractiveSettings().get_vertical_component_grid() > 0)) {
          gridSnapComponent = currComponent;
        }
        if (!componentList.contains(currComponent)) {
          Collection<Item> componentItems = routingBoard.get_component_items(currComponent.no);
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
      if (currItem.is_user_fixed()) {
        p_board_handling.screenMessages.set_status_message(
            tm.getText("some_items_cannot_be_moved_because_they_are_fixed"));
        moveOk = false;
        obstacleItems.add(currItem);
        fixedItems.add(currItem);
      } else if (currItem.is_connected()) {
        // Check if the whole connected set is inside the selected items,
        // and add the items of the connected set to the move list in this case.
        // Conduction areas are ignored, because otherwise components with
        // pins contacted to a plane could never be moved.
        boolean itemMovable = true;
        Collection<Item> contacts = currItem.get_connected_set(-1, true);
        {
          for (Item currContact : contacts) {
            if (currContact instanceof ConductionArea) {

              continue;
            }
            if (currContact.is_user_fixed()) {
              itemMovable = false;
              fixedItems.add(currContact);
            } else if (currContact.get_component_no() != 0) {
              Component currComponent = routingBoard.components.get(currContact.get_component_no());
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
          state.get_item_list().addAll(fixedItems);
          p_board_handling.screenMessages.set_status_message(
              tm.getText("please_unfix_selected_items_before_moving"));
        } else {
          state.get_item_list().addAll(obstacleItems);
          p_board_handling.screenMessages.set_status_message(
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

  private void add_to_net_items_list(Item p_item, int p_net_no) {
    for (NetItems curr_items : this.netItemsList) {
      if (curr_items.netNo == p_net_no) {
        // list for p_net_no exists already
        curr_items.items.add(p_item);
        return;
      }
    }
    Collection<Item> newItemList = hdlg.get_routing_board().get_connectable_items(p_net_no);
    newItemList.add(p_item);
    NetItems newNetItems = new NetItems(p_net_no, newItemList);
    this.netItemsList.add(newNetItems);
  }

  @Override
  public InteractiveState mouse_moved() {
    super.mouse_moved();
    move(hdlg.get_current_mouse_position());
    return this;
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return this.complete();
  }

  @Override
  public InteractiveState complete() {
    for (Item currItem : this.itemList) {
      if (currItem.clearance_violation_count() > 0) {
        hdlg.screenMessages.set_status_message(tm.getText("insertion_failed_because_of_obstacles"));
        return this;
      }
    }
    BasicBoard routingBoard = hdlg.get_routing_board();
    for (Item currItem : this.itemList) {
      routingBoard.insert_item(currItem);
    }

    // let the observers synchronize the moving
    for (Component currComponent : this.componentList) {
      routingBoard.communication.observers.notify_moved(currComponent);
    }

    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.update_ratsnest(curr_net_items.netNo);
    }
    hdlg.screenMessages.set_status_message(tm.getText("move_completed"));
    hdlg.repaint();
    return this.returnState;
  }

  @Override
  public InteractiveState cancel() {
    hdlg.get_routing_board().undo(null);
    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.update_ratsnest(curr_net_items.netNo);
    }
    return this.returnState;
  }

  @Override
  public InteractiveState mouse_wheel_moved(int p_rotation) {
    if (hdlg.getInteractiveSettings().get_zoom_with_wheel()) {
      super.mouse_wheel_moved(p_rotation);
    } else {
      this.rotate(-p_rotation);
    }
    return this;
  }

  /** Changes the position of the items in the list to p_new_location. */
  private void move(FloatPoint p_new_position) {
    currentPosition = p_new_position.round();
    if (!currentPosition.equals(previousPosition)) {
      Vector translateVector = currentPosition.difference_by(previousPosition);
      if (this.gridSnapComponent != null) {
        translateVector = adjust_to_placement_grid(translateVector);
      }
      Components components = hdlg.get_routing_board().components;
      for (Component currComponent : this.componentList) {
        components.move(currComponent.no, translateVector);
      }
      this.clearanceViolations = new LinkedList<>();
      for (Item currItem : this.itemList) {
        currItem.translate_by(translateVector);
        this.clearanceViolations.addAll(currItem.clearance_violations());
      }
      previousPosition = currentPosition;
      for (NetItems curr_net_items : this.netItemsList) {
        this.hdlg.update_ratsnest(curr_net_items.netNo, curr_net_items.items);
      }
      hdlg.repaint();
    }
  }

  private Vector adjust_to_placement_grid(Vector p_vector) {
    Point newComponentLocation = this.gridSnapComponent.get_location().translate_by(p_vector);
    IntPoint roundedComponentLocation =
        newComponentLocation
            .to_float()
            .round_to_grid(
                hdlg.getInteractiveSettings().get_horizontal_component_grid(),
                hdlg.getInteractiveSettings().get_vertical_component_grid());
    Vector adjustment = roundedComponentLocation.difference_by(newComponentLocation);
    Vector result = p_vector.add(adjustment);
    this.currentPosition = this.previousPosition.translate_by(result).to_float().round();
    return p_vector.add(adjustment);
  }

  /** Turns the items in the list by p_factor times 90 degree around the current position. */
  public void turn_90_degree(int p_factor) {
    if (p_factor == 0) {
      return;
    }
    Components components = hdlg.get_routing_board().components;
    for (Component currComponent : this.componentList) {
      components.turn_90_degree(currComponent.no, p_factor, currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    for (Item currItem : this.itemList) {
      currItem.turn_90_degree(p_factor, currentPosition);
      this.clearanceViolations.addAll(currItem.clearance_violations());
    }
    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.update_ratsnest(curr_net_items.netNo, curr_net_items.items);
    }
    hdlg.repaint();
  }

  public void rotate(double p_angle_in_degree) {
    if (p_angle_in_degree == 0) {
      return;
    }
    Components components = hdlg.get_routing_board().components;
    for (Component currComponent : this.componentList) {
      components.rotate(currComponent.no, p_angle_in_degree, this.currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    FloatPoint floatPosition = this.currentPosition.to_float();
    for (Item currItem : this.itemList) {
      currItem.rotate_approx(p_angle_in_degree, floatPosition);
      this.clearanceViolations.addAll(currItem.clearance_violations());
    }
    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.update_ratsnest(curr_net_items.netNo, curr_net_items.items);
    }
    hdlg.repaint();
  }

  /** Turns the items in the list by p_factor times 90 degree around the current position. */
  public void turn_45_degree(int p_factor) {
    if (p_factor % 2 == 0) {
      turn_90_degree(p_factor / 2);
    } else {
      rotate(p_factor * 45);
    }
  }

  /** Changes the placement side of the items in the list. */
  public void change_placement_side() {
    // Check, that all items can be mirrored
    LayerStructure layerStructure = hdlg.get_routing_board().layerStructure;
    BoardLibrary boardLibrary = hdlg.get_routing_board().library;
    boolean placementSideChangable = true;
    for (Item currItem : itemList) {
      if (currItem instanceof Via via) {
        if (boardLibrary.get_mirrored_via_padstack(via.get_padstack()) == null) {
          placementSideChangable = false;
          break;
        }
      } else if (currItem.first_layer() == currItem.last_layer()) {
        int newLayerNo = hdlg.get_layer_count() - currItem.first_layer() - 1;
        if (!layerStructure.arr[newLayerNo].isSignal) {
          placementSideChangable = false;
          break;
        }
      }
    }
    if (!placementSideChangable) {
      hdlg.screenMessages.set_status_message(tm.getText("cannot_change_placement_side"));
      return;
    }

    Components components = hdlg.get_routing_board().components;
    for (Component currComponent : this.componentList) {
      components.change_side(currComponent.no, currentPosition);
    }
    this.clearanceViolations = new LinkedList<>();
    for (Item currItem : this.itemList) {
      currItem.change_placement_side(currentPosition);
      this.clearanceViolations.addAll(currItem.clearance_violations());
    }
    for (NetItems curr_net_items : this.netItemsList) {
      this.hdlg.update_ratsnest(curr_net_items.netNo, curr_net_items.items);
    }
    hdlg.repaint();
  }

  public void reset_rotation() {
    Component componentToReset = null;
    for (Component currComponent : this.componentList) {
      if (componentToReset == null) {
        componentToReset = currComponent;
      } else if (componentToReset.get_rotation_in_degree()
          != currComponent.get_rotation_in_degree()) {
        hdlg.screenMessages.set_status_message(
            tm.getText("unable_to_reset_components_with_different_rotations"));
        return;
      }
    }
    if (componentToReset == null) {
      return;
    }
    double rotation = componentToReset.get_rotation_in_degree();
    if (!hdlg.get_routing_board().components.get_flip_style_rotate_first()
        || componentToReset.placed_on_front()) {
      rotation = 360 - rotation;
    }
    rotate(rotation);
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState key_typed(char p_key_char) {
    InteractiveState currReturnState = this;
    switch (p_key_char) {
      case '+' -> turn_90_degree(1);
      case '*' -> turn_90_degree(2);
      case '-' -> turn_90_degree(3);
      case '/' -> change_placement_side();
      case 'r' -> hdlg.getInteractiveSettings().set_zoom_with_wheel(false);
      case 'z' -> hdlg.getInteractiveSettings().set_zoom_with_wheel(true);
      default -> currReturnState = super.key_typed(p_key_char);
    }
    return currReturnState;
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popupMenuMove;
  }

  @Override
  public String get_help_id() {
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
      Color drawColor = hdlg.graphicsContext.get_violations_color();
      for (ClearanceViolation currViolation : this.clearanceViolations) {
        hdlg.graphicsContext.fill_area(currViolation.shape, p_graphics, drawColor, 1);
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

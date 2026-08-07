package app.freerouting.interactive;

import app.freerouting.board.Component;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Via;
import app.freerouting.core.Package;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.JPopupMenu;

/** Interactive copying of items. */
public final class CopyItemState extends InteractiveState {

  private final Collection<Item> itemList;
  private Point startPosition;
  private Point currentPosition;
  private int currentLayer;
  private boolean layerChanged;
  private Point previousPosition;

  /** Creates a new instance of CopyItemState */
  private CopyItemState(
      FloatPoint p_location,
      Collection<Item> p_item_list,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    itemList = new LinkedList<>();

    startPosition = p_location.round();
    currentLayer = p_board_handling.getInteractiveSettings().get_layer();
    layerChanged = false;
    currentPosition = startPosition;
    previousPosition = currentPosition;
    for (Item currItem : p_item_list) {
      if (currItem instanceof DrillItem || currItem instanceof ObstacleArea) {
        Item newItem = currItem.copy(0);
        itemList.add(newItem);
      }
    }
  }

  /** Returns a new instance of CopyItemState or null, if p_item_list is empty. */
  public static CopyItemState get_instance(
      FloatPoint p_location,
      Collection<Item> p_item_list,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    if (p_item_list.isEmpty()) {
      return null;
    }
    p_board_handling.remove_ratsnest(); // copying an item may change the connectivity.
    return new CopyItemState(p_location, p_item_list, p_parent_state, p_board_handling);
  }

  /** Creates a new padstack from p_old_padstack with a layer range starting at p_new_layer. */
  private static Padstack change_padstack_layers(
      Padstack p_old_padstack,
      int p_new_layer,
      RoutingBoard p_board,
      Map<Padstack, Padstack> p_padstack_pairs) {
    Padstack newPadstack;
    int oldLayer = p_old_padstack.from_layer();
    if (oldLayer == p_new_layer) {
      newPadstack = p_old_padstack;
    } else if (p_padstack_pairs.containsKey(p_old_padstack)) {
      // New padstack already created, assign it to the via.
      newPadstack = p_padstack_pairs.get(p_old_padstack);
    } else {
      // Create a new padstack.
      ConvexShape[] newShapes = new ConvexShape[p_board.get_layer_count()];
      int layerDiff = oldLayer - p_new_layer;
      for (int i = 0; i < newShapes.length; i++) {
        int newLayerNo = i + layerDiff;
        if (newLayerNo >= 0 && newLayerNo < newShapes.length) {
          newShapes[i] = p_old_padstack.get_shape(i + layerDiff);
        }
      }
      newPadstack = p_board.library.padstacks.add(newShapes);
      p_padstack_pairs.put(p_old_padstack, newPadstack);
    }
    return newPadstack;
  }

  @Override
  public InteractiveState mouse_moved() {
    super.mouse_moved();
    change_position(hdlg.get_current_mouse_position());
    return this;
  }

  /** Changes the position for inserting the copied items to p_new_location. */
  private void change_position(FloatPoint p_new_position) {
    currentPosition = p_new_position.round();
    if (!currentPosition.equals(previousPosition)) {
      Vector translateVector = currentPosition.difference_by(previousPosition);
      for (Item currItem : itemList) {
        currItem.translate_by(translateVector);
      }
      previousPosition = currentPosition;
      hdlg.repaint();
    }
  }

  /** Changes the first layer of the items in the copy list to p_new_layer. */
  @Override
  public boolean change_layer_action(int p_new_layer) {
    currentLayer = p_new_layer;
    layerChanged = true;
    hdlg.set_layer(p_new_layer);
    return true;
  }

  /**
   * Inserts the items in the copy list into the board. Items, which would produce a clearance
   * violation, are not inserted.
   */
  public void insert() {
    if (itemList == null) {
      return;
    }
    Map<Padstack, Padstack> padstack_pairs =
        new TreeMap<>(); // Contains old and new padstacks after layer change.

    RoutingBoard board = hdlg.get_routing_board();
    if (layerChanged) {
      // create new via padstacks
      for (Item currOb : itemList) {
        if (currOb instanceof Via currVia) {
          Padstack newPadstack =
              change_padstack_layers(currVia.get_padstack(), currentLayer, board, padstack_pairs);
          currVia.set_padstack(newPadstack);
        }
      }
    }
    // Copy the components of the old items and assign the new items to the copied
    // components.

    // Contains the old and new id no of a copied component.
    Map<Integer, Integer> cmp_no_pairs = new TreeMap<>();

    // Contains the new created components after copying.
    Collection<Component> copiedComponents = new LinkedList<>();

    Vector translateVector = currentPosition.difference_by(startPosition);
    for (Item currItem : itemList) {
      int currCmpNo = currItem.get_component_no();
      if (currCmpNo > 0) {
        // This item belongs to a component
        int newCmpNo;
        Integer currKey = currCmpNo;
        if (cmp_no_pairs.containsKey(currKey)) {
          // the new component for this pin is already created
          Integer currValue = cmp_no_pairs.get(currKey);
          newCmpNo = currValue;
        } else {
          Component oldComponent = board.components.get(currCmpNo);
          if (oldComponent == null) {
            FRLogger.warn("CopyItemState: component not found");
            continue;
          }
          Point newLocation = oldComponent.get_location().translate_by(translateVector);
          Package newPackage;
          if (layerChanged) {
            // create a new package with changed layers of the padstacks.
            Package.Pin[] newPinArr = new Package.Pin[oldComponent.get_package().pin_count()];
            for (int i = 0; i < newPinArr.length; i++) {
              Package.Pin oldPin = oldComponent.get_package().get_pin(i);
              Padstack oldPadstack = board.library.padstacks.get(oldPin.padstackNo);
              if (oldPadstack == null) {
                FRLogger.warn("CopyItemState.insert: package padstack not found");
                return;
              }
              Padstack newPadstack =
                  change_padstack_layers(oldPadstack, currentLayer, board, padstack_pairs);
              newPinArr[i] =
                  new Package.Pin(
                      oldPin.name,
                      newPadstack.no,
                      oldPin.relativeLocation,
                      oldPin.rotationInDegree);
            }
            newPackage = board.library.packages.add(newPinArr);
          } else {
            newPackage = oldComponent.get_package();
          }
          Component newComponent =
              board.components.add(
                  newLocation,
                  oldComponent.get_rotation_in_degree(),
                  oldComponent.placed_on_front(),
                  newPackage);
          copiedComponents.add(newComponent);
          newCmpNo = newComponent.no;
          cmp_no_pairs.put(currCmpNo, newCmpNo);
        }
        currItem.assign_component_no(newCmpNo);
      }
    }
    boolean allItemsInserted = true;
    boolean firstTime = true;
    for (Item currItem : itemList) {
      if (currItem.board != null && currItem.clearance_violation_count() == 0) {
        if (firstTime) {
          // make the current situation restorable by undo
          board.generate_snapshot();
          firstTime = false;
        }
        board.insert_item(currItem.copy(0));
      } else {
        allItemsInserted = false;
      }
    }
    if (allItemsInserted) {
      hdlg.screenMessages.set_status_message(tm.getText("allItemsInserted"));
    } else {
      hdlg.screenMessages.set_status_message(
          tm.getText("some_items_not_inserted_because_of_obstacles"));
    }
    startPosition = currentPosition;
    layerChanged = false;
    hdlg.repaint();
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    insert();
    return this;
  }

  @Override
  public void draw(Graphics p_graphics) {
    if (itemList == null) {
      return;
    }
    for (Item currItem : itemList) {
      currItem.draw(
          p_graphics,
          hdlg.graphicsContext,
          hdlg.graphicsContext.get_hilight_color(),
          hdlg.graphicsContext.get_hilight_color_intensity());
    }
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popupMenuCopy;
  }
}

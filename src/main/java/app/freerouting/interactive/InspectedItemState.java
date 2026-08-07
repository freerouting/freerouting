package app.freerouting.interactive;

import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.WindowObjectInfo;
import java.awt.Graphics;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JPopupMenu;

/** Class implementing actions on the currently selected items. */
public final class InspectedItemState extends InteractiveState {

  private Set<Item> itemList;
  private ClearanceViolations clearanceViolations;

  /** Creates a new instance of InspectedItemState */
  private InspectedItemState(
      Set<Item> p_item_list, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    itemList = p_item_list;
  }

  /**
   * Creates a new InspectedItemState with the items in p_item_list selected. Returns null, if
   * p_item_list is empty.
   */
  public static InspectedItemState get_instance(
      Set<Item> p_item_list, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    if (p_item_list.isEmpty()) {
      return null;
    }
    return new InspectedItemState(p_item_list, p_parent_state, p_board_handling);
  }

  /** Gets the list of the currently selected items. */
  public Collection<Item> get_item_list() {
    return itemList;
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return toggle_select(p_location);
  }

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    return InspectItemsInRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState key_typed(char p_key_char) {
    InteractiveState result = this;

    switch (p_key_char) {
      case 'e' -> result = this.extent_to_whole_connections();
      case 'i' -> result = this.info();
      case 'n' -> this.extent_to_whole_nets();
      case 'r' ->
          result = ZoomRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
      case 's' -> result = this.extent_to_whole_connected_sets();
      case 'v' -> this.toggle_clearance_violations();
      case 'w' -> this.hdlg.zoom_selection();
      default -> result = super.key_typed(p_key_char);
    }
    return result;
  }

  /** Select also all items belonging to any net of the current selected items. */
  public InteractiveState extent_to_whole_nets() {

    // collect all net numbers of the selected items
    Set<Integer> currNetNoSet = new TreeSet<>();
    for (Item currItem : itemList) {
      if (currItem instanceof Connectable) {
        for (int i = 0; i < currItem.net_count(); i++) {
          currNetNoSet.add(currItem.get_net_no(i));
        }
      }
    }
    Set<Item> newSelectedItems = new TreeSet<>();
    for (int currNetNo : currNetNoSet) {
      newSelectedItems.addAll(hdlg.get_routing_board().get_connectable_items(currNetNo));
    }
    this.itemList = newSelectedItems;
    if (newSelectedItems.isEmpty()) {
      return this.returnState;
    }
    filter();
    hdlg.repaint();
    return this;
  }

  /** Select also all items belonging to any group of the current selected items. */
  public InteractiveState extent_to_whole_components() {

    // collect all group numbers of the selected items
    Set<Integer> currGroupNoSet = new TreeSet<>();
    for (Item currItem : itemList) {
      if (currItem.get_component_no() > 0) {
        currGroupNoSet.add(currItem.get_component_no());
      }
    }
    Set<Item> newSelectedItems = new TreeSet<>(itemList);
    for (int curr_group_no : currGroupNoSet) {
      newSelectedItems.addAll(hdlg.get_routing_board().get_component_items(curr_group_no));
    }
    if (newSelectedItems.isEmpty()) {
      return this.returnState;
    }
    this.itemList = newSelectedItems;
    hdlg.repaint();
    return this;
  }

  /** Select also all items belonging to any connected set of the current selected items. */
  public InteractiveState extent_to_whole_connected_sets() {
    Set<Item> newSelectedItems = new TreeSet<>();
    for (Item currItem : this.itemList) {
      if (currItem instanceof Connectable) {
        newSelectedItems.addAll(currItem.get_connected_set(-1));
      }
    }
    if (newSelectedItems.isEmpty()) {
      return this.returnState;
    }
    this.itemList = newSelectedItems;
    filter();
    hdlg.repaint();
    return this;
  }

  /** Select also all items belonging to any connection of the current selected items. */
  public InteractiveState extent_to_whole_connections() {
    Set<Item> newSelectedItems = new TreeSet<>();
    for (Item currItem : this.itemList) {
      if (currItem instanceof Connectable) {
        newSelectedItems.addAll(currItem.get_connection_items());
      }
    }
    if (newSelectedItems.isEmpty()) {
      return this.returnState;
    }
    this.itemList = newSelectedItems;
    filter();
    hdlg.repaint();
    return this;
  }

  /**
   * Picks item at p_point. Removes it from the selectedItems list, if it is already in there,
   * otherwise adds it to the list. Returns true (to change to the returnState) if nothing was
   * picked.
   */
  public InteractiveState toggle_select(FloatPoint p_point) {
    Collection<Item> pickedItems = hdlg.pick_items(p_point);
    boolean stateEnded = pickedItems.isEmpty();
    if (pickedItems.size() == 1) {
      Item pickedItem = pickedItems.iterator().next();
      if (this.itemList.contains(pickedItem)) {
        this.itemList.remove(pickedItem);
        if (this.itemList.isEmpty()) {
          stateEnded = true;
        }
      } else {
        this.itemList.add(pickedItem);
      }
    }
    hdlg.repaint();
    InteractiveState result;
    if (stateEnded) {
      result = this.returnState;
    } else {
      result = this;
    }
    return result;
  }

  /** Shows or hides the clearance violations of the selected items. */
  public void toggle_clearance_violations() {
    if (clearanceViolations == null) {
      clearanceViolations = new ClearanceViolations(this.itemList);
      Integer violationCount = clearanceViolations.list.size();
      String currMessage = violationCount + " " + tm.getText("clearance_violations_found");
      hdlg.screenMessages.set_status_message(currMessage);
    } else {
      clearanceViolations = null;
      hdlg.screenMessages.set_status_message("");
    }
    hdlg.repaint();
  }

  /** Removes items not selected by the current interactive filter from the selected item list. */
  public InteractiveState filter() {
    itemList = hdlg.getInteractiveSettings().get_item_selection_filter().filter(itemList);
    InteractiveState result = this;
    if (itemList.isEmpty()) {
      result = this.returnState;
    }
    hdlg.repaint();
    return result;
  }

  /** Prints information about the selected item into a graphical text window. */
  public InspectedItemState info() {
    WindowObjectInfo.display(
        this.itemList,
        hdlg.get_panel().boardFrame,
        hdlg.coordinateTransform,
        new java.awt.Point(100, 100));
    return this;
  }

  @Override
  public String get_help_id() {
    return "InspectedItemState";
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
    if (clearanceViolations != null) {
      clearanceViolations.draw(p_graphics, hdlg.graphicsContext);
    }
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popupMenuSelect;
  }

  @Override
  public void set_toolbar() {
    hdlg.get_panel().boardFrame.set_inspect_toolbar();
  }

  @Override
  public void display_default_message() {
    hdlg.screenMessages.set_status_message(tm.getText("in_inspect_item_mode"));
  }
}

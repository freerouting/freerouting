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

/**
 * Class implementing actions on the currently selected items.
 */
public class InspectedItemState extends InteractiveState {

  private Set<Item> item_list;
  private ClearanceViolations clearance_violations;

  /**
   * Creates a new instance of InspectedItemState
   */
  private InspectedItemState(Set<Item> p_item_list, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    item_list = p_item_list;
  }

  /**
   * Creates a new InspectedItemState with the items in p_item_list selected.
   * Returns null, if p_item_list is empty.
   */
  public static InspectedItemState get_instance(Set<Item> p_item_list, InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    if (p_item_list.isEmpty()) {
      return null;
    }
    InspectedItemState new_state = new InspectedItemState(p_item_list, p_parent_state, p_board_handling);
    return new_state;
  }

  /**
   * Gets the list of the currently selected items.
   */
  public Collection<Item> get_item_list() {
    return item_list;
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return toggle_select(p_location);
  }

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    return InspectItemsInRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
  }

  /**
   * Action to be taken when a key is pressed (Shortcut).
   */
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

  /**
   * Select also all items belonging to any net of the current selected items.
   */
  public InteractiveState extent_to_whole_nets() {

    // collect all net numbers of the selected items
    Set<Integer> curr_net_no_set = new TreeSet<>();
    for (Item curr_item : item_list) {
      if (curr_item instanceof Connectable) {
        for (int i = 0; i < curr_item.net_count(); i++) {
          curr_net_no_set.add(curr_item.get_net_no(i));
        }
      }
    }
    Set<Item> new_selected_items = new TreeSet<>();
    for (int curr_net_no : curr_net_no_set) {
      new_selected_items.addAll(hdlg
          .get_routing_board()
          .get_connectable_items(curr_net_no));
    }
    this.item_list = new_selected_items;
    if (new_selected_items.isEmpty()) {
      return this.return_state;
    }
    filter();
    hdlg.repaint();
    return this;
  }

  /**
   * Select also all items belonging to any group of the current selected items.
   */
  public InteractiveState extent_to_whole_components() {

    // collect all group numbers of the selected items
    Set<Integer> curr_group_no_set = new TreeSet<>();
    for (Item curr_item : item_list) {
      if (curr_item.get_component_no() > 0) {
        curr_group_no_set.add(curr_item.get_component_no());
      }
    }
    Set<Item> new_selected_items = new TreeSet<>(item_list);
    for (int curr_group_no : curr_group_no_set) {
      new_selected_items.addAll(hdlg
          .get_routing_board()
          .get_component_items(curr_group_no));
    }
    if (new_selected_items.isEmpty()) {
      return this.return_state;
    }
    this.item_list = new_selected_items;
    hdlg.repaint();
    return this;
  }

  /**
   * Select also all items belonging to any connected set of the current selected
   * items.
   */
  public InteractiveState extent_to_whole_connected_sets() {
    Set<Item> new_selected_items = new TreeSet<>();
    for (Item curr_item : this.item_list) {
      if (curr_item instanceof Connectable) {
        new_selected_items.addAll(curr_item.get_connected_set(-1));
      }
    }
    if (new_selected_items.isEmpty()) {
      return this.return_state;
    }
    this.item_list = new_selected_items;
    filter();
    hdlg.repaint();
    return this;
  }

  /**
   * Select also all items belonging to any connection of the current selected
   * items.
   */
  public InteractiveState extent_to_whole_connections() {
    Set<Item> new_selected_items = new TreeSet<>();
    for (Item curr_item : this.item_list) {
      if (curr_item instanceof Connectable) {
        new_selected_items.addAll(curr_item.get_connection_items());
      }
    }
    if (new_selected_items.isEmpty()) {
      return this.return_state;
    }
    this.item_list = new_selected_items;
    filter();
    hdlg.repaint();
    return this;
  }

  /**
   * Picks item at p_point. Removes it from the selected_items list, if it is
   * already in there, otherwise adds it to the list. Returns true (to change to
   * the return_state) if nothing was picked.
   */
  public InteractiveState toggle_select(FloatPoint p_point) {
    Collection<Item> picked_items = hdlg.pick_items(p_point);
    boolean state_ended = picked_items.isEmpty();
    if (picked_items.size() == 1) {
      Item picked_item = picked_items
          .iterator()
          .next();
      if (this.item_list.contains(picked_item)) {
        this.item_list.remove(picked_item);
        if (this.item_list.isEmpty()) {
          state_ended = true;
        }
      } else {
        this.item_list.add(picked_item);
      }
    }
    hdlg.repaint();
    InteractiveState result;
    if (state_ended) {
      result = this.return_state;
    } else {
      result = this;
    }
    return result;
  }

  /**
   * Shows or hides the clearance violations of the selected items.
   */
  public void toggle_clearance_violations() {
    if (clearance_violations == null) {
      clearance_violations = new ClearanceViolations(this.item_list);
      Integer violation_count = clearance_violations.list.size();
      String curr_message = violation_count + " " + tm.getText("clearance_violations_found");
      hdlg.screen_messages.set_status_message(curr_message);
    } else {
      clearance_violations = null;
      hdlg.screen_messages.set_status_message("");
    }
    hdlg.repaint();
  }

  /**
   * Removes items not selected by the current interactive filter from the
   * selected item list.
   */
  public InteractiveState filter() {
    item_list = hdlg.settings.item_selection_filter.filter(item_list);
    InteractiveState result = this;
    if (item_list.isEmpty()) {
      result = this.return_state;
    }
    hdlg.repaint();
    return result;
  }

  /**
   * Prints information about the selected item into a graphical text window.
   */
  public InspectedItemState info() {
    WindowObjectInfo.display(this.item_list, hdlg.get_panel().board_frame, hdlg.coordinate_transform,
        new java.awt.Point(100, 100));
    return this;
  }

  @Override
  public String get_help_id() {
    return "InspectedItemState";
  }

  @Override
  public void draw(Graphics p_graphics) {
    if (item_list == null) {
      return;
    }

    for (Item curr_item : item_list) {
      curr_item.draw(p_graphics, hdlg.graphics_context, hdlg.graphics_context.get_hilight_color(),
          hdlg.graphics_context.get_hilight_color_intensity());
    }
    if (clearance_violations != null) {
      clearance_violations.draw(p_graphics, hdlg.graphics_context);
    }
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popup_menu_select;
  }

  @Override
  public void set_toolbar() {
    hdlg.get_panel().board_frame.set_inspect_toolbar();
  }

  @Override
  public void display_default_message() {
    hdlg.screen_messages.set_status_message(tm.getText("in_inspect_item_mode"));
  }
}
package app.freerouting.board;

import java.util.Set;
import java.util.TreeSet;

/** Filter for selecting items on the board. */
public class ItemSelectionFilter implements java.io.Serializable {
  /** the filter array of the item types */
  private final boolean[] values;

  /** Creates a new filter with all item types selected. */
  public ItemSelectionFilter() {
    this.values = new boolean[SelectableChoices.values().length];
    java.util.Arrays.fill(this.values, true);
    this.values[SelectableChoices.KEEPOUT.ordinal()] = false;
    this.values[SelectableChoices.VIA_KEEPOUT.ordinal()] = false;
    this.values[SelectableChoices.COMPONENT_KEEPOUT.ordinal()] = false;
    this.values[SelectableChoices.CONDUCTION.ordinal()] = false;
    this.values[SelectableChoices.BOARD_OUTLINE.ordinal()] = false;
  }

  /** Creates a new filter with only p_item_type selected. */
  public ItemSelectionFilter(SelectableChoices p_item_type) {
    this.values = new boolean[SelectableChoices.values().length];
    java.util.Arrays.fill(this.values, false);
    values[p_item_type.ordinal()] = true;
    values[SelectableChoices.FIXED.ordinal()] = true;
    values[SelectableChoices.UNFIXED.ordinal()] = true;
  }

  /** Creates a new filter with only p_item_types selected. */
  public ItemSelectionFilter(SelectableChoices[] p_item_types) {
    this.values = new boolean[SelectableChoices.values().length];
    java.util.Arrays.fill(this.values, false);
    for (int i = 0; i < p_item_types.length; ++i) {
      values[p_item_types[i].ordinal()] = true;
    }
    values[SelectableChoices.FIXED.ordinal()] = true;
    values[SelectableChoices.UNFIXED.ordinal()] = true;
  }

  /** Copy constructor */
  public ItemSelectionFilter(ItemSelectionFilter p_item_selection_filter) {
    this.values = new boolean[SelectableChoices.values().length];
    for (int i = 0; i < this.values.length; ++i) {
      this.values[i] = p_item_selection_filter.values[i];
    }
  }

  /** Selects or deselects an item type */
  public void set_selected(SelectableChoices p_choice, boolean p_value) {
    values[p_choice.ordinal()] = p_value;
  }

  /** Selects all item types. */
  public void select_all() {
    java.util.Arrays.fill(values, true);
  }

  /** Deselects all item types. */
  public void deselect_all() {
    java.util.Arrays.fill(values, false);
  }

  /** Filters a collection of items with this filter. */
  public Set<Item> filter(java.util.Set<app.freerouting.board.Item> p_items) {
    Set<Item> result = new TreeSet<Item>();
    for (app.freerouting.board.Item curr_item : p_items) {
      if (curr_item.is_selected_by_filter(this)) {
        result.add(curr_item);
      }
    }
    return result;
  }

  /** Looks, if the input item type is selected. */
  public boolean is_selected(SelectableChoices p_choice) {
    return values[p_choice.ordinal()];
  }

  /** The possible choices in the filter. */
  public enum SelectableChoices {
    TRACES,
    VIAS,
    PINS,
    CONDUCTION,
    KEEPOUT,
    VIA_KEEPOUT,
    COMPONENT_KEEPOUT,
    BOARD_OUTLINE,
    FIXED,
    UNFIXED
  }
}

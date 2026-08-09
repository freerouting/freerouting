package app.freerouting.board;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/** Filter for selecting items on the board. */
public class ItemSelectionFilter implements Serializable {

  /** The filter array of the item types. */
  private final boolean[] values;

  /** Creates a new filter with all item types selected. */
  public ItemSelectionFilter() {
    this.values = new boolean[SelectableChoices.values().length];
    Arrays.fill(this.values, true);
    this.values[SelectableChoices.KEEPOUT.ordinal()] = false;
    this.values[SelectableChoices.VIA_KEEPOUT.ordinal()] = false;
    this.values[SelectableChoices.COMPONENT_KEEPOUT.ordinal()] = false;
    this.values[SelectableChoices.CONDUCTION.ordinal()] = false;
    this.values[SelectableChoices.BOARD_OUTLINE.ordinal()] = false;
  }

  /** Creates a new filter with only p_item_type selected. */
  public ItemSelectionFilter(SelectableChoices itemType) {
    this.values = new boolean[SelectableChoices.values().length];
    values[itemType.ordinal()] = true;
    values[SelectableChoices.FIXED.ordinal()] = true;
    values[SelectableChoices.UNFIXED.ordinal()] = true;
  }

  /** Creates a new filter with only p_item_types selected. */
  public ItemSelectionFilter(SelectableChoices[] itemTypes) {
    this.values = new boolean[SelectableChoices.values().length];
    for (int i = 0; i < itemTypes.length; i++) {
      values[itemTypes[i].ordinal()] = true;
    }
    values[SelectableChoices.FIXED.ordinal()] = true;
    values[SelectableChoices.UNFIXED.ordinal()] = true;
  }

  /** Copy constructor. */
  public ItemSelectionFilter(ItemSelectionFilter itemSelectionFilter) {
    this.values = itemSelectionFilter.values.clone();
  }

  /** Selects or deselects an item type. */
  public void setSelected(SelectableChoices choice, boolean value) {
    values[choice.ordinal()] = value;
  }

  /** Selects all item types. */
  public void selectAll() {
    Arrays.fill(values, true);
  }

  /** Deselects all item types. */
  public void deselectAll() {
    Arrays.fill(values, false);
  }

  /** Filters a collection of items with this filter. */
  public Set<Item> filter(Set<Item> items) {
    Set<Item> result = new TreeSet<>();
    for (Item currItem : items) {
      if (currItem.isSelectedByFilter(this)) {
        result.add(currItem);
      }
    }
    return result;
  }

  /** Looks, if the input item type is selected. */
  public boolean isSelected(SelectableChoices choice) {
    return values[choice.ordinal()];
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

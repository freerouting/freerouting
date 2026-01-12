package app.freerouting.drc;

import app.freerouting.board.Item;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Information about an unconnected NET (not individual items).
 * 
 * IMPORTANT: Despite the name "unconnected_items", this class represents an
 * unconnected NET.
 * Each instance represents ONE net that has multiple disconnected groups of
 * items.
 * The items list contains ALL items from the net to show which components/pins
 * are affected.
 */
public class UnconnectedItems {

  /**
   * The first item that is unconnected (representative from first connected
   * group)
   */
  public final Item first_item;
  /**
   * The second item that is unconnected (representative from second connected
   * group)
   */
  public final Item second_item;
  /**
   * All items from the unconnected net (for better visibility of affected
   * components)
   */
  public final List<Item> all_items;
  /**
   * The type of the unconnected item
   */
  public final String type;

  /**
   * Creates a new instance of UnconnectedItems with two representative items
   */
  public UnconnectedItems(Item p_first_item, Item p_second_item) {
    this(p_first_item, p_second_item, Arrays.asList(p_first_item, p_second_item), "unconnected_items");
  }

  /**
   * Creates a new instance of UnconnectedItems with all items from the net
   */
  public UnconnectedItems(Item p_first_item, Item p_second_item, List<Item> p_all_items) {
    this(p_first_item, p_second_item, p_all_items, "unconnected_items");
  }

  /**
   * Creates a new instance of UnconnectedItems with a specific type
   */
  public UnconnectedItems(Item p_first_item, Item p_second_item, String p_type) {
    this(p_first_item, p_second_item, Arrays.asList(p_first_item, p_second_item), p_type);
  }

  /**
   * Creates a new instance of UnconnectedItems with all items and a specific type
   */
  public UnconnectedItems(Item p_first_item, Item p_second_item, List<Item> p_all_items, String p_type) {
    first_item = p_first_item;
    second_item = p_second_item;
    all_items = p_all_items != null ? new ArrayList<>(p_all_items) : Arrays.asList(p_first_item, p_second_item);
    type = p_type;
  }
}

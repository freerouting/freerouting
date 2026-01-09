package app.freerouting.drc;

import app.freerouting.board.Item;

/**
 * Information of an unconnected items.
 */
public class UnconnectedItems {

  /**
   * The first item that is unconnected
   */
  public final Item first_item;
  /**
   * The second item that is unconnected
   */
  public final Item second_item;
  /**
   * The type of the unconnected item
   */
  public final String type;

  /**
   * Creates a new instance of UnconnectedItems
   */
  public UnconnectedItems(Item p_first_item, Item p_second_item) {
    this(p_first_item, p_second_item, "unconnected_items");
  }

  /**
   * Creates a new instance of UnconnectedItems with a specific type
   */
  public UnconnectedItems(Item p_first_item, Item p_second_item, String p_type) {
    first_item = p_first_item;
    second_item = p_second_item;
    type = p_type;
  }
}
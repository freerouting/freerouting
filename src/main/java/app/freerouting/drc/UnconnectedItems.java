package app.freerouting.drc;

import app.freerouting.board.Item;

/**
 * Information of an unconnected items.
 */
public class UnconnectedItems
{

  /**
   * The first item that is unconnected
   */
  public final Item first_item;
  /**
   * The second item that is unconnected
   */
  public final Item second_item;

  /**
   * Creates a new instance of UnconnectedItems
   */
  public UnconnectedItems(Item p_first_item, Item p_second_item)
  {
    first_item = p_first_item;
    second_item = p_second_item;
  }
}
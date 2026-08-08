package app.freerouting.drc;

import app.freerouting.board.Item;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Information about an unconnected NET (not individual items).
 *
 * <p>IMPORTANT: Despite the name "unconnectedItems", this class represents an unconnected NET. Each
 * instance represents ONE net that has multiple disconnected groups of items. The items list
 * contains ALL items from the net to show which components/pins are affected.
 */
public class UnconnectedItems {

  /** The first item that is unconnected (representative from first connected group) */
  public final Item firstItem;

  /** The second item that is unconnected (representative from second connected group) */
  public final Item secondItem;

  /** All items from the unconnected net (for better visibility of affected components) */
  public final List<Item> allItems;

  /** The type of the unconnected item */
  public final String type;

  /** Creates a new instance of UnconnectedItems with two representative items */
  public UnconnectedItems(Item pFirstItem, Item pSecondItem) {
    this(pFirstItem, pSecondItem, Arrays.asList(pFirstItem, pSecondItem), "unconnectedItems");
  }

  /** Creates a new instance of UnconnectedItems with all items from the net */
  public UnconnectedItems(Item pFirstItem, Item pSecondItem, List<Item> pAllItems) {
    this(pFirstItem, pSecondItem, pAllItems, "unconnectedItems");
  }

  /** Creates a new instance of UnconnectedItems with a specific type */
  public UnconnectedItems(Item pFirstItem, Item pSecondItem, String pType) {
    this(pFirstItem, pSecondItem, Arrays.asList(pFirstItem, pSecondItem), pType);
  }

  /** Creates a new instance of UnconnectedItems with all items and a specific type */
  public UnconnectedItems(Item pFirstItem, Item pSecondItem, List<Item> pAllItems, String pType) {
    firstItem = pFirstItem;
    secondItem = pSecondItem;
    allItems =
        pAllItems != null ? new ArrayList<>(pAllItems) : Arrays.asList(pFirstItem, pSecondItem);
    type = pType;
  }
}

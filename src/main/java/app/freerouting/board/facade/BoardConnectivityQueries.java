package app.freerouting.board.facade;

import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.datastructures.UndoableObjects;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/** Read-only connectivity and component queries extracted from {@link BasicBoard}. */
public final class BoardConnectivityQueries {

  private final BasicBoard board;
  private Map<Integer, List<Item>> connectableItemsByNet;
  private long connectableItemsStamp = -1;

  BoardConnectivityQueries(BasicBoard board) {
    this.board = board;
  }

  private Map<Integer, List<Item>> getConnectableIndex() {
    long currentStamp = board.itemList.getVersion() + board.getNetAssignmentChangeCount();
    if (connectableItemsByNet == null || connectableItemsStamp != currentStamp) {
      Map<Integer, List<Item>> map = new HashMap<>();
      Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
      for (; ; ) {
        UndoableObjects.Storable currentItem = board.itemList.readObject(iterator);
        if (currentItem == null) {
          break;
        }
        if (currentItem instanceof Connectable && currentItem instanceof Item item) {
          for (int i = 0; i < item.netCount(); i++) {
            int netNo = item.getNetNumber(i);
            map.computeIfAbsent(netNo, k -> new ArrayList<>()).add(item);
          }
        }
      }
      connectableItemsByNet = map;
      connectableItemsStamp = currentStamp;
    }
    return connectableItemsByNet;
  }

  /** Returns all connectable items containing the requested net. */
  Collection<Item> getConnectableItems(int netNumber) {
    List<Item> items = getConnectableIndex().get(netNumber);
    return items != null ? new ArrayList<>(items) : new LinkedList<>();
  }

  /** Returns the number of connectable items containing the requested net. */
  int connectableItemCount(int netNumber) {
    List<Item> items = getConnectableIndex().get(netNumber);
    return items != null ? items.size() : 0;
  }

  /** Returns all items belonging to the requested component. */
  Collection<Item> getComponentItems(int componentId) {
    Collection<Item> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(iterator);
      if (currentItem == null) {
        return result;
      }
      if (currentItem.getComponentId() == componentId) {
        result.add(currentItem);
      }
    }
  }

  /** Returns all pins belonging to the requested component. */
  Collection<Pin> getComponentPins(int componentId) {
    Collection<Pin> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(iterator);
      if (currentItem == null) {
        return result;
      }
      if (currentItem.getComponentId() == componentId && currentItem instanceof Pin pin) {
        result.add(pin);
      }
    }
  }

  /** Returns a component pin by component ID and package pin index. */
  Pin getPin(int componentId, int pinIndex) {
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(iterator);
      if (currentItem == null) {
        return null;
      }
      if (currentItem.getComponentId() == componentId && currentItem instanceof Pin pin) {
        if (pin.pinIndex == pinIndex) {
          return pin;
        }
      }
    }
  }

  /** Returns the connected sets for the requested net. */
  Collection<Collection<Item>> getConnectedSets(int netNumber) {
    Collection<Collection<Item>> result = new LinkedList<>();
    if (netNumber <= 0) {
      return result;
    }
    SortedSet<Item> itemsToHandle = new TreeSet<>(getConnectableItems(netNumber));
    Iterator<Item> connectedItems = itemsToHandle.iterator();
    while (connectedItems.hasNext()) {
      Item currentItem = connectedItems.next();
      Collection<Item> nextConnectedSet = currentItem.getConnectedSet(netNumber);
      result.add(nextConnectedSet);
      itemsToHandle.removeAll(nextConnectedSet);
      connectedItems = itemsToHandle.iterator();
    }
    return result;
  }
}

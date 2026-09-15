package app.freerouting.board.facade;

import app.freerouting.board.model.items.Connectable;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.datastructures.UndoableObjects;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

/** Read-only connectivity and component queries extracted from {@link BasicBoard}. */
public final class BoardConnectivityQueries {

  private final BasicBoard board;

  BoardConnectivityQueries(BasicBoard board) {
    this.board = board;
  }

  /** Returns all connectable items containing the requested net. */
  Collection<Item> getConnectableItems(int netNumber) {
    Collection<Item> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(iterator);
      if (currentItem == null) {
        return result;
      }
      if (currentItem instanceof Connectable && currentItem.containsNet(netNumber)) {
        result.add(currentItem);
      }
    }
  }

  /** Returns the number of connectable items containing the requested net. */
  int connectableItemCount(int netNumber) {
    int result = 0;
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(iterator);
      if (currentItem == null) {
        return result;
      }
      if (currentItem instanceof Connectable && currentItem.containsNet(netNumber)) {
        result++;
      }
    }
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
    SortedSet<Item> itemsToHandle = new TreeSet<>();
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(iterator);
      if (currentItem == null) {
        break;
      }
      if (currentItem instanceof Connectable && currentItem.containsNet(netNumber)) {
        itemsToHandle.add(currentItem);
      }
    }
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

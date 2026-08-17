package app.freerouting.board;

import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Owns the item-list access and search-tree/observer bookkeeping for a {@link BasicBoard}.
 *
 * <p>The board remains the public façade. This collaborator is deliberately transient and is
 * recreated on demand so adding it does not change the serialized board representation.
 */
public final class BoardItemRepository {

  private final BasicBoard board;

  BoardItemRepository(BasicBoard board) {
    this.board = board;
  }

  /** Returns the outline item, if one is present. */
  BoardOutline getOutline() {
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      UndoableObjects.Storable currentItem = board.itemList.readObject(iterator);
      if (currentItem == null) {
        return null;
      }
      if (currentItem instanceof BoardOutline outline) {
        return outline;
      }
    }
  }

  /** Returns the item with the requested ID, if one is present. */
  Item getItem(int id) {
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(iterator);
      if (currentItem == null) {
        return null;
      }
      if (currentItem.getId() == id) {
        return currentItem;
      }
    }
  }

  /** Returns a snapshot collection of all items currently in the item list. */
  Collection<Item> getItems() {
    Collection<Item> result = new LinkedList<>();
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) board.itemList.readObject(iterator);
      if (currentItem == null) {
        return result;
      }
      result.add(currentItem);
    }
  }

  Collection<ConductionArea> getConductionAreas() {
    Collection<ConductionArea> result = new LinkedList<>();
    for (Item currentItem : getItems()) {
      if (currentItem instanceof ConductionArea area) {
        result.add(area);
      }
    }
    return result;
  }

  Collection<Pin> getPins() {
    Collection<Pin> result = new LinkedList<>();
    for (Item currentItem : getItems()) {
      if (currentItem instanceof Pin pin) {
        result.add(pin);
      }
    }
    return result;
  }

  Collection<Pin> getSmdPins() {
    Collection<Pin> result = new LinkedList<>();
    for (Item currentItem : getItems()) {
      if (currentItem instanceof Pin pin && pin.firstLayer() == pin.lastLayer()) {
        result.add(pin);
      }
    }
    return result;
  }

  Collection<Via> getVias() {
    Collection<Via> result = new LinkedList<>();
    for (Item currentItem : getItems()) {
      if (currentItem instanceof Via via) {
        result.add(via);
      }
    }
    return result;
  }

  Collection<Trace> getTraces() {
    Collection<Trace> result = new LinkedList<>();
    for (Item currentItem : getItems()) {
      if (currentItem instanceof Trace trace) {
        result.add(trace);
      }
    }
    return result;
  }

  double cumulativeTraceLength() {
    double result = 0;
    for (Item currentItem : getItems()) {
      if (currentItem instanceof Trace trace) {
        result += trace.getLength();
      }
    }
    return result;
  }

  /** Inserts an item and performs the existing tree, observer, and revision updates. */
  void insertItem(Item item) {
    if (item == null) {
      return;
    }
    if (isItemActivityDebugCandidate(item)) {
      FRLogger.trace(
          "ITEM_ACTIVITY action=INSERT"
              + ", id="
              + item.getId()
              + ", type="
              + item.getClass().getSimpleName()
              + ", bounds="
              + describeBounds(item.boundingBox())
              + ", net0="
              + firstNetOrNone(item));
    }

    if (board.rules == null
        || board.rules.clearanceMatrix == null
        || item.clearanceClassIndex() < 0
        || item.clearanceClassIndex() >= board.rules.clearanceMatrix.getClassCount()) {
      FRLogger.warn("LayeredBoard.insert_item: clearanceClass no out of range");
      item.setClearanceClassIndex(0);
    }
    item.board = board;
    board.itemList.insert(item);
    board.searchTreeManager.insert(item);
    if (board.communication != null && board.communication.observers != null) {
      board.communication.observers.notifyNew(item);
    }
    board.additionalUpdateAfterChange(item);
    board.incrementRevision();
  }

  /** Removes an item and performs the existing tree, observer, and revision updates. */
  void removeItem(Item item) {
    if (item == null) {
      return;
    }
    if (isItemActivityDebugCandidate(item)) {
      FRLogger.trace(
          "ITEM_ACTIVITY action=REMOVE"
              + ", id="
              + item.getId()
              + ", type="
              + item.getClass().getSimpleName()
              + ", bounds="
              + describeBounds(item.boundingBox())
              + ", net0="
              + firstNetOrNone(item));
    }
    if (item instanceof Trace trace
        && trace.netNumbers.length > 0
        && trace.netNumbers[0] == 94) {
      logTraceRemoval(trace);
    }
    if (item.isDeletionForbidden()) {
      return;
    }
    board.additionalUpdateAfterChange(item);
    board.searchTreeManager.remove(item);
    board.itemList.delete(item);
    if (board.communication != null && board.communication.observers != null) {
      board.communication.observers.notifyDeleted(item);
    }
    board.incrementRevision();
  }

  /** Removes all removable items and reports whether every requested item was removed. */
  boolean removeItems(Collection<Item> items) {
    boolean result = true;
    for (Item currentItem : items) {
      if (currentItem.isDeletionForbidden() || currentItem.isUserFixed()) {
        result = false;
      } else {
        removeItem(currentItem);
      }
    }
    return result;
  }

  /** Deletes all traces and vias from the undoable item list. */
  void deleteAllTracksAndVias() {
    Iterator<UndoableObjects.UndoableObjectNode> iterator = board.itemList.startReadObject();
    for (; ; ) {
      UndoableObjects.Storable currentItem = board.itemList.readObject(iterator);
      if (currentItem == null) {
        return;
      }
      if (currentItem instanceof Trace || currentItem instanceof Via) {
        board.itemList.delete(currentItem);
      }
    }
  }

  private static boolean isItemActivityDebugCandidate(Item item) {
    IntBox bounds = item.boundingBox();
    IntBox debugWindow = new IntBox(1620000, -1105000, 1930000, -1003000);
    return bounds != null && bounds.intersects(debugWindow);
  }

  private static String firstNetOrNone(Item item) {
    return item.netCount() > 0 ? Integer.toString(item.getNetNumber(0)) : "none";
  }

  private static String describeBounds(IntBox bounds) {
    if (bounds == null) {
      return "null";
    }
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  private static void logTraceRemoval(Trace trace) {
    if (trace instanceof PolylineTrace polylineTrace
        && polylineTrace.cornerCount() == 2
        && trace.firstCorner().equals(new IntPoint(1885928, -1097274))
        && trace.lastCorner().equals(new IntPoint(1885928, -1098024))) {
      FRLogger.trace(
          "BasicBoard.remove_item",
          "compare_trace_remove_item",
          "REMOVE_ITEM called on trace [7,8]",
          "Net #"
              + trace.netNumbers[0]
              + ",Trace #"
              + trace.getId()
              + ",Layer #"
              + trace.getLayer(),
          new Point[] {trace.firstCorner(), trace.lastCorner()});
      for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
        FRLogger.trace(
            "BasicBoard.remove_item",
            "compare_trace_remove_item_stack",
            stackTraceElement.toString(),
            "Net #"
                + trace.netNumbers[0]
                + ",Trace #"
                + trace.getId()
                + ",Layer #"
                + trace.getLayer(),
            new Point[] {trace.firstCorner(), trace.lastCorner()});
      }
    } else {
      FRLogger.trace(
          "BasicBoard.remove_item",
          "compare_trace_remove_item",
          "REMOVE_ITEM called by " + Thread.currentThread().getStackTrace()[3],
          "Net #"
              + trace.netNumbers[0]
              + ",Trace #"
              + trace.getId()
              + ",Layer #"
              + trace.getLayer(),
          new Point[] {trace.firstCorner(), trace.lastCorner()});
    }
  }
}

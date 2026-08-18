package app.freerouting.board.facade;

import app.freerouting.board.model.items.Item;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.logger.FRLogger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

/** Owns routing-board undo/redo side effects and deep-copy restoration. */
public final class RoutingBoardUndoFacade {

  private final RoutingBoard board;

  RoutingBoardUndoFacade(RoutingBoard board) {
    this.board = board;
  }

  boolean undo(Set<Integer> changedNets) {
    board.components.undo(board.communication.observers);
    Collection<UndoableObjects.Storable> cancelledObjects = new LinkedList<>();
    Collection<UndoableObjects.Storable> restoredObjects = new LinkedList<>();
    boolean result = board.itemList.undo(cancelledObjects, restoredObjects);
    applyUndoRedoSideEffects(cancelledObjects, restoredObjects, changedNets);
    return result;
  }

  boolean redo(Set<Integer> changedNets) {
    board.components.redo(board.communication.observers);
    Collection<UndoableObjects.Storable> cancelledObjects = new LinkedList<>();
    Collection<UndoableObjects.Storable> restoredObjects = new LinkedList<>();
    boolean result = board.itemList.redo(cancelledObjects, restoredObjects);
    applyUndoRedoSideEffects(cancelledObjects, restoredObjects, changedNets);
    return result;
  }

  synchronized RoutingBoard deepCopy() {
    ObjectOutputStream outputStream = null;
    ObjectInputStream inputStream = null;
    try {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      outputStream = new ObjectOutputStream(byteArrayOutputStream);
      outputStream.writeObject(board);
      outputStream.flush();

      ByteArrayInputStream byteArrayInputStream =
          new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
      inputStream = new ObjectInputStream(byteArrayInputStream);
      RoutingBoard boardCopy = (RoutingBoard) inputStream.readObject();
      boardCopy.clearAllItemTemporaryAutorouteData();
      boardCopy.finishAutoroute();
      return boardCopy;
    } catch (Exception exception) {
      FRLogger.error("Exception in deep_copy_routing_board" + exception, exception);
      return null;
    } finally {
      try {
        if (outputStream != null) {
          outputStream.close();
        }
        if (inputStream != null) {
          inputStream.close();
        }
      } catch (Exception exception) {
        FRLogger.error("Exception closing deep-copy streams" + exception, exception);
      }
    }
  }

  private void applyUndoRedoSideEffects(
      Collection<UndoableObjects.Storable> cancelledObjects,
      Collection<UndoableObjects.Storable> restoredObjects,
      Set<Integer> changedNets) {
    for (UndoableObjects.Storable storable : cancelledObjects) {
      Item currentItem = (Item) storable;
      board.searchTreeManager.remove(currentItem);
      if (board.communication != null && board.communication.observers != null) {
        board.communication.observers.notifyDeleted(currentItem);
      }
      if (changedNets != null) {
        for (int i = 0; i < currentItem.netCount(); i++) {
          changedNets.add(currentItem.getNetNumber(i));
        }
      }
    }
    for (UndoableObjects.Storable storable : restoredObjects) {
      Item currentItem = (Item) storable;
      currentItem.board = board;
      board.searchTreeManager.insert(currentItem);
      currentItem.clearAutorouteInfo();
      if (board.communication != null && board.communication.observers != null) {
        board.communication.observers.notifyNew(currentItem);
      }
      if (changedNets != null) {
        for (int i = 0; i < currentItem.netCount(); i++) {
          changedNets.add(currentItem.getNetNumber(i));
        }
      }
    }
  }
}

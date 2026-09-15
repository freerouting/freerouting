package app.freerouting.datastructures;

import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Database of objects, for which Undo and Redo operations are made possible. The algorithm works
 * only for objects containing no references.
 */
public class UndoableObjects implements Serializable {

  /**
   * The entries of this map are of type UndoableObject, the keys of type UndoableObjects.Storable.
   */
  private final ConcurrentMap<Storable, UndoableObjectNode> objects;

  /**
   * Lists of deleted objects on each undo level, which were already existing before the previous
   * snapshot.
   */
  private final Vector<Collection<UndoableObjectNode>> deletedObjectsStack;

  /** The current undo level. */
  private int stackLevel;

  private boolean redoPossible;

  /** Creates a new instance of UndoableObjectsList. */
  public UndoableObjects() {
    stackLevel = 0;
    objects = new ConcurrentSkipListMap<>();
    deletedObjectsStack = new Vector<>();
  }

  /**
   * Returns an iterator for sequential reading of the object list.
   *
   * @return an iterator for sequential reading of the object list
   */
  public Iterator<UndoableObjectNode> startReadObject() {
    return objects.values().iterator();
  }

  /**
   * Reads the next object in this list. Returns null, if the list is exhausted. it must be created
   * by start_read_object.
   */
  public UndoableObjects.Storable readObject(Iterator<UndoableObjectNode> it) {
    while (it.hasNext()) {
      UndoableObjectNode currentNode = it.next();
      // skip objects getting alive only by redo
      if (currentNode != null && currentNode.level <= this.stackLevel) {
        return currentNode.object;
      }
    }
    return null;
  }

  /** Adds object to the UndoableObjectsList. */
  public void insert(UndoableObjects.Storable object) {
    disableRedo();
    UndoableObjectNode currentUndoableObject = new UndoableObjectNode(object, stackLevel);
    objects.put(object, currentUndoableObject);
  }

  /**
   * Removes object from the top level of the UndoableObjectsList. Returns false, if object was not
   * found in the list.
   */
  public boolean delete(UndoableObjects.Storable object) {
    disableRedo();
    Collection<UndoableObjectNode> currentDeleteList;
    if (deletedObjectsStack.isEmpty()) {
      // stackLevel 0
      currentDeleteList = null;
    } else {
      currentDeleteList = deletedObjectsStack.lastElement();
    }
    // search object in the list
    UndoableObjectNode objectNode = objects.get(object);
    if (objectNode == null) {
      return false;
    }

    if (object instanceof app.freerouting.board.model.items.Item item) {
      String itemNetNames = item.getAllNetNames();
      FRLogger.trace(
          "UndoableObjects.delete",
          "delete",
          "Deleting item with "
              + itemNetNames
              + ": "
              + "itemType="
              + item.getClass().getSimpleName()
              + ", item="
              + item,
          itemNetNames,
          null);
    }
    // if (objectNode.object != object)
    { // object can be cloned from the object pointed by objectNode.object
      // Since objectNode.object has been retrieved via objects.get(object)
      // objects.remove(object) would certainly remove the object.
      // Thus ignore the warning and proceed with the deletion
      // Intentionally left blank.
      // FRLogger.warn("UndoableObjectList.delete: Object inconsistent");
      // return false;
    }

    if (currentDeleteList != null) {
      if (objectNode.level < this.stackLevel) {
        // add currentObject to the current delete list to make Undo possible.
        currentDeleteList.add(objectNode);
      } else if (objectNode.undoObject != null) {
        // add currentObject.undoObject to the current delete list to make Undo possible.

        currentDeleteList.add(objectNode.undoObject);
      }
    }
    objects.remove(object);
    return true;
  }

  /** Makes the current state of the list restorable by Undo. */
  public void generateSnapshot() {
    disableRedo();
    Collection<UndoableObjectNode> currentDeletedObjectsList = new LinkedList<>();
    deletedObjectsStack.add(currentDeletedObjectsList);
    ++stackLevel;
  }

  /**
   * Restores the situation before the last snapshot. Outputs the cancelled and the restored objects
   * (if != null) to enable the calling function to take additional actions needed for these
   * objects. Returns false, if no more undo is possible
   */
  public boolean undo(
      Collection<UndoableObjects.Storable> cancelledObjects,
      Collection<UndoableObjects.Storable> restoredObjects) {
    if (stackLevel == 0) {
      return false; // no more undo possible
    }
    for (UndoableObjectNode currentNode : objects.values()) {
      if (currentNode.level == stackLevel) {
        if (currentNode.undoObject != null) {
          // replace the current object by its previous state.
          currentNode.undoObject.redoObject = currentNode;
          objects.put(currentNode.object, currentNode.undoObject);
          if (restoredObjects != null) {
            restoredObjects.add(currentNode.undoObject.object);
          }
        }
        if (cancelledObjects != null) {
          cancelledObjects.add(currentNode.object);
        }
      }
    }
    // restore the deleted objects
    Collection<UndoableObjectNode> currentDeleteList =
        deletedObjectsStack.elementAt(stackLevel - 1);
    for (UndoableObjectNode currentDeletedNode : currentDeleteList) {
      this.objects.put(currentDeletedNode.object, currentDeletedNode);
      if (restoredObjects != null) {
        restoredObjects.add(currentDeletedNode.object);
      }
    }
    --this.stackLevel;
    redoPossible = true;
    return true;
  }

  /**
   * Restores the situation before the last undo. Outputs the cancelled and the restored objects (if
   * != null) to enable the calling function to take additional actions needed for these objects.
   * Returns false, if no more redo is possible.
   */
  public boolean redo(
      Collection<UndoableObjects.Storable> cancelledObjects,
      Collection<UndoableObjects.Storable> restoredObjects) {
    if (this.stackLevel >= deletedObjectsStack.size()) {
      return false; // already at the top level
    }
    ++this.stackLevel;
    for (UndoableObjectNode currentNode : objects.values()) {
      if (currentNode.redoObject != null && currentNode.redoObject.level == this.stackLevel) {
        // Object was created on a lower level and changed on the current level,
        // replace the lower level object by the object on the current layer.
        objects.put(currentNode.object, currentNode.redoObject);
        if (cancelledObjects != null) {
          cancelledObjects.add(currentNode.object);
        }
        if (restoredObjects != null) {
          restoredObjects.add(currentNode.redoObject.object);
          // else the redoObject was deleted on the redo level
        }
      } else if (currentNode.level == this.stackLevel) {
        // Object was created on the current level, allow it to be restored.
        restoredObjects.add(currentNode.object);
      }
    }
    // Delete the objects, which were deleted on the current level, again.
    Collection<UndoableObjectNode> currentDeleteList =
        deletedObjectsStack.elementAt(stackLevel - 1);
    for (UndoableObjectNode currentDeletedNode : currentDeleteList) {
      while (currentDeletedNode.redoObject != null
          && currentDeletedNode.redoObject.level <= this.stackLevel) {
        currentDeletedNode = currentDeletedNode.redoObject;
      }
      if (this.objects.remove(currentDeletedNode.object) == null) {
        FRLogger.warn("previous deleted object not found");
      }
      if (restoredObjects == null || !restoredObjects.remove(currentDeletedNode.object)) {
        // the object needs only be cancelled if it is already in the board
        if (cancelledObjects != null) {
          cancelledObjects.add(currentDeletedNode.object);
        }
      }
    }
    return true;
  }

  /**
   * Removes the top snapshot from the undo stack, so that its situation cannot be restored anymore.
   * Returns false, if no more snapshot could be popped.
   */
  public boolean popSnapshot() {
    disableRedo();
    if (stackLevel == 0) {
      return false;
    }
    for (UndoableObjectNode currentNode : objects.values()) {
      if (currentNode.level == stackLevel - 1) {
        if (currentNode.redoObject != null && currentNode.redoObject.level == stackLevel) {
          currentNode.redoObject.undoObject = currentNode.undoObject;
          if (currentNode.undoObject != null) {
            currentNode.undoObject.redoObject = currentNode.redoObject;
          }
        }
      } else if (currentNode.level >= stackLevel) {
        --currentNode.level;
      }
    }
    int deletedObjectsStackSize = deletedObjectsStack.size();
    if (deletedObjectsStackSize >= 2) {
      // join the top delete list with the delete list of the second top level
      Collection<UndoableObjectNode> fromDeleteList =
          deletedObjectsStack.elementAt(deletedObjectsStackSize - 1);
      Collection<UndoableObjectNode> toDeleteList =
          deletedObjectsStack.elementAt(deletedObjectsStackSize - 2);
      for (UndoableObjectNode currentDeletedNode : fromDeleteList) {
        if (currentDeletedNode.level < this.stackLevel - 1) {
          toDeleteList.add(currentDeletedNode);
        } else if (currentDeletedNode.undoObject != null) {
          toDeleteList.add(currentDeletedNode.undoObject);
        }
      }
    }
    deletedObjectsStack.remove(deletedObjectsStackSize - 1);
    --stackLevel;
    return true;
  }

  /**
   * Must be called before object will be modified after a snapshot for the first time, if it may
   * have existed before that snapshot.
   */
  public void saveForUndo(UndoableObjects.Storable object) {
    disableRedo();
    // search object in the map
    UndoableObjectNode currentNode = objects.get(object);
    if (currentNode == null) {
      FRLogger.warn("UndoableObjects.save_for_undo: object node not found");
      return;
    }
    if (currentNode.level < this.stackLevel) {

      UndoableObjectNode oldNode =
          new UndoableObjectNode((UndoableObjects.Storable) object.clone(), currentNode.level);
      oldNode.undoObject = currentNode.undoObject;
      oldNode.redoObject = currentNode;
      currentNode.undoObject = oldNode;
      currentNode.level = this.stackLevel;
    }
  }

  /** Must be called, if objects are changed for the first time after undo. */
  private void disableRedo() {
    if (!redoPossible) {
      return;
    }
    redoPossible = false;
    // shorten the size of the deletedObjectsStack to this.stackLevel
    deletedObjectsStack.subList(this.stackLevel, deletedObjectsStack.size()).clear();
    Iterator<UndoableObjectNode> it = objects.values().iterator();
    while (it.hasNext()) {
      UndoableObjectNode currentNode = it.next();
      if (currentNode.level > this.stackLevel) {
        it.remove();
      } else if (currentNode.level == this.stackLevel) {
        currentNode.redoObject = null;
      }
    }
  }

  /**
   * Condition for an Object to be stored in an UndoableObjects database. An object of class
   * UndoableObjects.Storable must not contain any references.
   */
  public interface Storable extends Comparable<Object> {

    /**
     * Creates an exact copy of this object. Public overwriting of the protected clone method in
     * java.lang.Object.
     */
    Object clone();
  }

  /**
   * Stores information for correct restoring or cancelling an object in an undo or redo operation.
   * level is the level in the Undo stack, where this object was inserted.
   */
  public static class UndoableObjectNode implements Serializable {

    final Storable object; // the object in the node
    int level; // the level in the Undo stack, where this node was inserted
    UndoableObjectNode undoObject; // the object to restore in an undo or null.
    UndoableObjectNode redoObject; // the object to restore in a redo or null.

    /** Creates a new instance of UndoableObjectNode. */
    UndoableObjectNode(Storable object, int level) {
      this.object = object;
      this.level = level;
      undoObject = null;
      redoObject = null;
    }
  }
}

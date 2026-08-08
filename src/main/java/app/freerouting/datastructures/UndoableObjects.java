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
   * the lists of deleted objects on each undo level, which where already existing before the
   * previous snapshot.
   */
  private final Vector<Collection<UndoableObjectNode>> deletedObjectsStack;

  /** the current undo level */
  private int stackLevel;

  private boolean redoPossible;

  /** Creates a new instance of UndoableObjectsList */
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
   * Reads the next object in this list. Returns null, if the list is exhausted. p_it must be
   * created by start_read_object.
   */
  public UndoableObjects.Storable readObject(Iterator<UndoableObjectNode> p_it) {
    while (p_it.hasNext()) {
      UndoableObjectNode currNode = p_it.next();
      // skip objects getting alive only by redo
      if (currNode != null && currNode.level <= this.stackLevel) {
        return currNode.object;
      }
    }
    return null;
  }

  /** Adds p_object to the UndoableObjectsList. */
  public void insert(UndoableObjects.Storable p_object) {
    disableRedo();
    UndoableObjectNode currUndoableObject = new UndoableObjectNode(p_object, stackLevel);
    objects.put(p_object, currUndoableObject);
  }

  /**
   * Removes p_object from the top level of the UndoableObjectsList. Returns false, if p_object was
   * not found in the list.
   */
  public boolean delete(UndoableObjects.Storable p_object) {
    disableRedo();
    Collection<UndoableObjectNode> currDeleteList;
    if (deletedObjectsStack.isEmpty()) {
      // stackLevel 0
      currDeleteList = null;
    } else {
      currDeleteList = deletedObjectsStack.lastElement();
    }
    // search p_object in the list
    UndoableObjectNode objectNode = objects.get(p_object);
    if (objectNode == null) {
      return false;
    }

    if (p_object instanceof app.freerouting.board.Item item) {
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
    // if (objectNode.object != p_object)
    { // p_object can be cloned from the object pointed by objectNode.object
      // Since objectNode.object has been retrieved via objects.get(p_object)
      // objects.remove(p_object) would certainly remove the object.
      // Thus ignore the warning and proceed with the deletion
      //
      // FRLogger.warn("UndoableObjectList.delete: Object inconsistent");
      // return false;
    }

    if (currDeleteList != null) {
      if (objectNode.level < this.stackLevel) {
        // add currOb to the current delete list to make Undo possible.
        currDeleteList.add(objectNode);
      } else if (objectNode.undoObject != null) {
        // add currOb.undoObject to the current delete list to make Undo possible.

        currDeleteList.add(objectNode.undoObject);
      }
    }
    objects.remove(p_object);
    return true;
  }

  /** Makes the current state of the list restorable by Undo. */
  public void generateSnapshot() {
    disableRedo();
    Collection<UndoableObjectNode> currDeletedObjectsList = new LinkedList<>();
    deletedObjectsStack.add(currDeletedObjectsList);
    ++stackLevel;
  }

  /**
   * Restores the situation before the last snapshot. Outputs the cancelled and the restored objects
   * (if != null) to enable the calling function to take additional actions needed for these
   * objects. Returns false, if no more undo is possible
   */
  public boolean undo(
      Collection<UndoableObjects.Storable> p_cancelled_objects,
      Collection<UndoableObjects.Storable> p_restored_objects) {
    if (stackLevel == 0) {
      return false; // no more undo possible
    }
    for (UndoableObjectNode currNode : objects.values()) {
      if (currNode.level == stackLevel) {
        if (currNode.undoObject != null) {
          // replace the current object by its previous state.
          currNode.undoObject.redoObject = currNode;
          objects.put(currNode.object, currNode.undoObject);
          if (p_restored_objects != null) {
            p_restored_objects.add(currNode.undoObject.object);
          }
        }
        if (p_cancelled_objects != null) {
          p_cancelled_objects.add(currNode.object);
        }
      }
    }
    // restore the deleted objects
    Collection<UndoableObjectNode> currDeleteList = deletedObjectsStack.elementAt(stackLevel - 1);
    for (UndoableObjectNode curr_deleted_node : currDeleteList) {
      this.objects.put(curr_deleted_node.object, curr_deleted_node);
      if (p_restored_objects != null) {
        p_restored_objects.add(curr_deleted_node.object);
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
      Collection<UndoableObjects.Storable> p_cancelled_objects,
      Collection<UndoableObjects.Storable> p_restored_objects) {
    if (this.stackLevel >= deletedObjectsStack.size()) {
      return false; // already at the top level
    }
    ++this.stackLevel;
    for (UndoableObjectNode currNode : objects.values()) {
      if (currNode.redoObject != null && currNode.redoObject.level == this.stackLevel) {
        // Object was created on a lower level and changed on the current level,
        // replace the lower level object by the object on the current layer.
        objects.put(currNode.object, currNode.redoObject);
        if (p_cancelled_objects != null) {
          p_cancelled_objects.add(currNode.object);
        }
        if (p_restored_objects != null) {
          p_restored_objects.add(currNode.redoObject.object);
          // else the redoObject was deleted on the redo level
        }
      } else if (currNode.level == this.stackLevel) {
        // Object was created on the current level, allow it to be restored.
        p_restored_objects.add(currNode.object);
      }
    }
    // Delete the objects, which were deleted on the current level, again.
    Collection<UndoableObjectNode> currDeleteList = deletedObjectsStack.elementAt(stackLevel - 1);
    for (UndoableObjectNode curr_deleted_node : currDeleteList) {
      while (curr_deleted_node.redoObject != null
          && curr_deleted_node.redoObject.level <= this.stackLevel) {
        curr_deleted_node = curr_deleted_node.redoObject;
      }
      if (this.objects.remove(curr_deleted_node.object) == null) {
        FRLogger.warn("previous deleted object not found");
      }
      if (p_restored_objects == null || !p_restored_objects.remove(curr_deleted_node.object)) {
        // the object needs only be cancelled if it is already in the board
        if (p_cancelled_objects != null) {
          p_cancelled_objects.add(curr_deleted_node.object);
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
    for (UndoableObjectNode currNode : objects.values()) {
      if (currNode.level == stackLevel - 1) {
        if (currNode.redoObject != null && currNode.redoObject.level == stackLevel) {
          currNode.redoObject.undoObject = currNode.undoObject;
          if (currNode.undoObject != null) {
            currNode.undoObject.redoObject = currNode.redoObject;
          }
        }
      } else if (currNode.level >= stackLevel) {
        --currNode.level;
      }
    }
    int deletedObjectsStackSize = deletedObjectsStack.size();
    if (deletedObjectsStackSize >= 2) {
      // join the top delete list with the delete list of the second top level
      Collection<UndoableObjectNode> fromDeleteList =
          deletedObjectsStack.elementAt(deletedObjectsStackSize - 1);
      Collection<UndoableObjectNode> toDeleteList =
          deletedObjectsStack.elementAt(deletedObjectsStackSize - 2);
      for (UndoableObjectNode curr_deleted_node : fromDeleteList) {
        if (curr_deleted_node.level < this.stackLevel - 1) {
          toDeleteList.add(curr_deleted_node);
        } else if (curr_deleted_node.undoObject != null) {
          toDeleteList.add(curr_deleted_node.undoObject);
        }
      }
    }
    deletedObjectsStack.remove(deletedObjectsStackSize - 1);
    --stackLevel;
    return true;
  }

  /**
   * Must be called before p_object will be modified after a snapshot for the first time, if it may
   * have existed before that snapshot.
   */
  public void saveForUndo(UndoableObjects.Storable p_object) {
    disableRedo();
    // search p_object in the map
    UndoableObjectNode currNode = objects.get(p_object);
    if (currNode == null) {
      FRLogger.warn("UndoableObjects.save_for_undo: object node not found");
      return;
    }
    if (currNode.level < this.stackLevel) {

      UndoableObjectNode oldNode =
          new UndoableObjectNode((UndoableObjects.Storable) p_object.clone(), currNode.level);
      oldNode.undoObject = currNode.undoObject;
      oldNode.redoObject = currNode;
      currNode.undoObject = oldNode;
      currNode.level = this.stackLevel;
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
      UndoableObjectNode currNode = it.next();
      if (currNode.level > this.stackLevel) {
        it.remove();
      } else if (currNode.level == this.stackLevel) {
        currNode.redoObject = null;
      }
    }
  }

  /**
   * Condition for an Object to be stored in an UndoableObjects database. An object of class
   * UndoableObjects.Storable must not contain any references.
   */
  public interface Storable extends Comparable<Object> {

    /**
     * Creates an exact copy of this object Public overwriting of the protected clone method in
     * java.lang.Object,
     */
    Object clone();
  }

  /**
   * Stores information for correct restoring or cancelling an object in an undo or redo operation.
   * p_level is the level in the Undo stack, where this object was inserted.
   */
  public static class UndoableObjectNode implements Serializable {

    final Storable object; // the object in the node
    int level; // the level in the Undo stack, where this node was inserted
    UndoableObjectNode undoObject; // the object to restore in an undo or null.
    UndoableObjectNode redoObject; // the object to restore in a redo or null.

    /** Creates a new instance of UndoableObjectNode */
    UndoableObjectNode(Storable p_object, int p_level) {
      object = p_object;
      level = p_level;
      undoObject = null;
      redoObject = null;
    }
  }
}

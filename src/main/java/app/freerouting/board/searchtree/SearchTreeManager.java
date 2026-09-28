package app.freerouting.board.searchtree;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The SearchTreeManager manages the search trees used in the auto-router. It is responsible for the
 * creation of the search trees and the insertion and removal of items from the trees. The
 * SearchTreeManager also provides functions to merge and change tree entries for performance
 * reasons.
 */
public class SearchTreeManager {

  private final Collection<ShapeSearchTree> compensatedSearchTrees;
  private final BasicBoard board;
  private ShapeSearchTree defaultTree;
  private boolean clearanceCompensationUsed;

  /** Creates a new instance of SearchTreeManager. */
  public SearchTreeManager(BasicBoard board) {
    this.board = board;
    compensatedSearchTrees = new LinkedList<>();
    defaultTree = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, board, 0);
    compensatedSearchTrees.add(defaultTree);
    this.clearanceCompensationUsed = false;
  }

  /** Inserts the tree shapes of item into all active search trees. */
  public void insert(Item item) {
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {
      currentTree.insert(item);
    }
    item.setOnTheBoard(true);
  }

  /** Removes all entries of an item from the search trees. */
  public void remove(Item item) {
    if (!item.isOnTheBoard()) {
      return;
    }
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {

      ShapeTree.Leaf[] currentTreeEntries = item.getSearchTreeEntries(currentTree);
      {
        if (currentTreeEntries != null) {
          currentTree.remove(currentTreeEntries);
        }
      }
    }
    item.clearSearchTreeEntries();
    item.setOnTheBoard(false);
  }

  /** Returns the default tree used in interactive routing. */
  public ShapeSearchTree getDefaultTree() {
    return defaultTree;
  }

  public boolean validateEntries(Item item) {
    boolean result = true;
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {

      if (!currentTree.validateEntries(item)) {
        result = false;
      }
    }
    return result;
  }

  /**
   * Returns, if clearance compensation is used for the default tree. This is normally the case, if
   * there exist only the clearance classes null and default in the clearance matrix.
   */
  public boolean isClearanceCompensationUsed() {
    return this.clearanceCompensationUsed;
  }

  /** Sets the usage of clearance compensation to true or false. */
  public void setClearanceCompensationUsed(boolean value) {
    if (this.clearanceCompensationUsed == value) {
      return;
    }

    this.clearanceCompensationUsed = value;
    removeAllBoardItems();
    this.compensatedSearchTrees.clear();
    int compensatedClearanceClassNo;
    if (value) {
      compensatedClearanceClassNo = 1;
    } else {
      compensatedClearanceClassNo = 0;
    }
    defaultTree =
        new ShapeSearchTree(
            FortyfiveDegreeBoundingDirections.INSTANCE, this.board, compensatedClearanceClassNo);
    this.compensatedSearchTrees.add(defaultTree);
    insertAllBoardItems();
  }

  /** Actions to be done, when a value in the clearance matrix is changed interactively. */
  public void clearanceValueChanged() {
    // delete all trees except the default tree
    this.compensatedSearchTrees.removeIf(
        t -> t.compensatedClearanceClassNo != defaultTree.compensatedClearanceClassNo);
    if (this.clearanceCompensationUsed) {
      removeAllBoardItems();
      insertAllBoardItems();
    }
  }

  /** Actions to be done, when a new clearance class is removed interactively. */
  public void clearanceClassRemoved(int no) {
    Iterator<ShapeSearchTree> it = this.compensatedSearchTrees.iterator();
    if (no == defaultTree.compensatedClearanceClassNo) {
      FRLogger.warn("SearchtreeManager.clearance_class_removed: unable to remove default tree");
      return;
    }
    while (it.hasNext()) {
      ShapeSearchTree currentTree = it.next();
      if (currentTree.compensatedClearanceClassNo == no) {
        it.remove();
      }
    }
  }

  /**
   * Returns the tree compensated for the clearance class with number clearanceClassIndex.
   * Initialized the tree, if it is not yet allocated.
   */
  public ShapeSearchTree getAutorouteTree(int clearanceClassIndex) {
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {
      if (currentTree.compensatedClearanceClassNo == clearanceClassIndex) {
        return currentTree;
      }
    }

    // Create a new ShapeSearchTree object based on the board's settings
    ShapeSearchTree currentAutorouteTree;
    if (this.board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      // fast algorithm with 90 degree restriction
      currentAutorouteTree = new ShapeSearchTree90Degree(this.board, clearanceClassIndex);
    } else if (this.board.rules.getTraceAngleRestriction() == AngleRestriction.FORTYFIVE_DEGREE) {
      // fast algorithm with 45 degree restriction
      currentAutorouteTree = new ShapeSearchTree45Degree(this.board, clearanceClassIndex);
    } else {
      // slow algorithm or no angle restriction
      currentAutorouteTree =
          new ShapeSearchTree(
              FortyfiveDegreeBoundingDirections.INSTANCE, this.board, clearanceClassIndex);
    }
    this.compensatedSearchTrees.add(currentAutorouteTree);

    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) this.board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      currentAutorouteTree.insert(currentItem);
    }
    return currentAutorouteTree;
  }

  // The following functions are used internally for performance improvement.

  /** Clears all compensated trees used in the autoroute algorithm apart from the default tree. */
  public void resetCompensatedTrees() {
    this.compensatedSearchTrees.removeIf(t -> t != defaultTree);
  }

  /**
   * Reinsert all items into the search trees. Public because rule changes that affect precalculated
   * tree shapes (e.g. the drill-hole clearance override, applied after the board is loaded) must
   * refresh the shapes of already-inserted items.
   */
  public void reinsertTreeItems() {
    removeAllBoardItems();
    // Removing clears the tree entries but NOT the precalculated tree shapes cached on each
    // item; without dropping those, re-insertion silently reuses the stale shapes and rule
    // changes (e.g. the drill-hole clearance override) never reach the trees.
    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) this.board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      currentItem.clearDerivedData();
    }
    insertAllBoardItems();
  }

  private void removeAllBoardItems() {
    if (this.board == null) {
      FRLogger.warn("SearchtreeManager.remove_all_board_items: app.freerouting.board is null");
      return;
    }
    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) this.board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      this.remove(currentItem);
    }
  }

  private void insertAllBoardItems() {
    if (this.board == null) {
      FRLogger.warn("SearchtreeManager.insert_all_board_items: app.freerouting.board is null");
      return;
    }
    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) this.board.itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      currentItem.clearDerivedData();
      this.insert(currentItem);
    }
  }

  /**
   * Merges the tree entries from fromTrace in front of toTrace. Special implementation for combine
   * trace for performance reasons.
   */
  public void mergeEntriesInFront(
      PolylineTrace fromTrace,
      PolylineTrace toTrace,
      Polyline joinedPolyline,
      int fromEntryNo,
      int toEntryNo) {
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {
      currentTree.mergeEntriesInFront(fromTrace, toTrace, joinedPolyline, fromEntryNo, toEntryNo);
    }
  }

  /**
   * Merges the tree entries from fromTrace to the end of toTrace. Special implementation for
   * combine trace for performance reasons.
   */
  public void mergeEntriesAtEnd(
      PolylineTrace fromTrace,
      PolylineTrace toTrace,
      Polyline joinedPolyline,
      int fromEntryNo,
      int toEntryNo) {
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {
      currentTree.mergeEntriesAtEnd(fromTrace, toTrace, joinedPolyline, fromEntryNo, toEntryNo);
    }
  }

  /**
   * Changes the tree entries from keepAtStartCount + 1 to newShapeCount - 1 - keepAtEndCount to
   * changedEntries. Special implementation for change_trace for performance reasons
   */
  public void changeEntries(
      PolylineTrace obj, Polyline newPolyline, int keepAtStartCount, int keepAtEndCount) {
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {
      currentTree.changeEntries(obj, newPolyline, keepAtStartCount, keepAtEndCount);
    }
  }

  /**
   * Transfers tree entries from fromTrace to start and endPiece after a middle piece was cut out.
   * Special implementation for ShapeTraceEntries.fast_cutout_trace for performance reasons.
   */
  void reuseEntriesAfterCutout(
      PolylineTrace fromTrace, PolylineTrace startPiece, PolylineTrace endPiece) {
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {

      currentTree.reuseEntriesAfterCutout(fromTrace, startPiece, endPiece);
    }
  }

  /**
   * Replaces this board's search trees with the child order and cached shapes of {@code source}. A
   * deep copy otherwise inserts every item into an empty tree, and that fresh tree does not route
   * the same way as the incremental tree. Returns false when a leaf cannot be mapped, leaving the
   * rebuilt trees in place.
   */
  public boolean adoptStructureFrom(SearchTreeManager source) {
    if (source == null || source.board == null || this.board == null) {
      return false;
    }
    Map<Integer, Item> destById = new HashMap<>();
    for (Item item : this.board.getItems()) {
      destById.put(item.getId(), item);
    }
    List<ShapeSearchTree> adoptedTrees = new ArrayList<>();
    List<Map<Integer, TileShape[]>> adoptedShapes = new ArrayList<>();
    ShapeSearchTree adoptedDefault = null;
    try {
      for (ShapeSearchTree sourceTree : source.compensatedSearchTrees) {
        ShapeSearchTree destTree = createEmptyLike(sourceTree);
        Map<Integer, TileShape[]> shapesById = cachedShapes(source, sourceTree);
        destTree.copyStructureFrom(
            sourceTree,
            object -> {
              if (!(object instanceof Item item)) {
                return null;
              }
              return destById.get(item.getId());
            });
        adoptedTrees.add(destTree);
        adoptedShapes.add(shapesById);
        if (sourceTree == source.defaultTree) {
          adoptedDefault = destTree;
        }
      }
    } catch (RuntimeException exception) {
      FRLogger.warn("SearchTreeManager.adopt_structure_from failed: " + exception.getMessage());
      return false;
    }
    if (adoptedDefault == null) {
      return false;
    }
    for (Item item : destById.values()) {
      item.clearSearchTreeEntries();
    }
    this.compensatedSearchTrees.clear();
    this.compensatedSearchTrees.addAll(adoptedTrees);
    this.defaultTree = adoptedDefault;
    this.clearanceCompensationUsed = source.clearanceCompensationUsed;
    for (int index = 0; index < adoptedTrees.size(); index++) {
      bindClonedLeaves(adoptedTrees.get(index), adoptedShapes.get(index));
    }
    return true;
  }

  private ShapeSearchTree createEmptyLike(ShapeSearchTree sourceTree) {
    int clearance = sourceTree.compensatedClearanceClassNo;
    if (sourceTree instanceof ShapeSearchTree90Degree) {
      return new ShapeSearchTree90Degree(this.board, clearance);
    }
    if (sourceTree instanceof ShapeSearchTree45Degree) {
      return new ShapeSearchTree45Degree(this.board, clearance);
    }
    return new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, this.board, clearance);
  }

  private static Map<Integer, TileShape[]> cachedShapes(
      SearchTreeManager source, ShapeSearchTree sourceTree) {
    Map<Integer, TileShape[]> shapesById = new HashMap<>();
    for (Item item : source.board.getItems()) {
      ShapeTree.Leaf[] entries = item.getSearchTreeEntries(sourceTree);
      if (entries == null || entries.length == 0) {
        continue;
      }
      TileShape[] shapes = new TileShape[entries.length];
      for (int index = 0; index < entries.length; index++) {
        shapes[index] = item.getTreeShape(sourceTree, index);
      }
      shapesById.put(item.getId(), shapes);
    }
    return shapesById;
  }

  private static void bindClonedLeaves(
      ShapeSearchTree destTree, Map<Integer, TileShape[]> shapesById) {
    Map<Integer, ShapeTree.Leaf[]> leavesById = new HashMap<>();
    for (ShapeTree.Leaf leaf : destTree.toArray()) {
      if (!(leaf.object instanceof Item item)) {
        continue;
      }
      int index = leaf.shapeIndexInObject;
      ShapeTree.Leaf[] existing = leavesById.get(item.getId());
      int length = existing == null ? 0 : existing.length;
      if (index >= length) {
        ShapeTree.Leaf[] grown = new ShapeTree.Leaf[index + 1];
        if (existing != null) {
          System.arraycopy(existing, 0, grown, 0, existing.length);
        }
        existing = grown;
        leavesById.put(item.getId(), existing);
      }
      existing[index] = leaf;
    }
    for (Map.Entry<Integer, ShapeTree.Leaf[]> entry : leavesById.entrySet()) {
      ShapeTree.Leaf[] leaves = entry.getValue();
      Item item = null;
      for (ShapeTree.Leaf leaf : leaves) {
        if (leaf != null && leaf.object instanceof Item found) {
          item = found;
          break;
        }
      }
      if (item == null) {
        continue;
      }
      TileShape[] shapes = shapesById.get(entry.getKey());
      if (shapes != null && shapes.length > leaves.length) {
        ShapeTree.Leaf[] grown = new ShapeTree.Leaf[shapes.length];
        System.arraycopy(leaves, 0, grown, 0, leaves.length);
        leaves = grown;
      }
      item.setSearchTreeEntries(leaves, destTree);
      if (shapes != null) {
        item.setPrecalculatedTreeShapes(shapes, destTree);
      }
    }
  }
}

package app.freerouting.board.searchtree;

import app.freerouting.board.facade.BasicBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.datastructures.ShapeTree;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.OrthogonalBoundingDirections;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

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

  /** Angle whose derived shapes are currently stored in the default tree. */
  private AngleRestriction indexedShapeAngle;

  /** Creates a new instance of SearchTreeManager. */
  public SearchTreeManager(BasicBoard board) {
    this.board = board;
    compensatedSearchTrees = new LinkedList<>();
    defaultTree = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, board, 0);
    compensatedSearchTrees.add(defaultTree);
    this.clearanceCompensationUsed = false;
    this.indexedShapeAngle = board.rules.getTraceAngleRestriction();
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
    refreshIndexedShapesAfterAngleChange();
    return defaultTree;
  }

  /**
   * Returns a managed general tree without clearance compensation. This avoids the specialized
   * autoroute trees' area approximations, but retains the general tree's pad and hole envelopes.
   */
  public synchronized ShapeSearchTree getUncompensatedTree() {
    for (ShapeSearchTree tree : compensatedSearchTrees) {
      if (tree.compensatedClearanceClassNo == 0
          && tree.getClass() == ShapeSearchTree.class
          && tree.obstacleShapeAngle() == null) {
        return tree;
      }
    }
    ShapeSearchTree tree =
        new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, board, 0);
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (Item item = (Item) board.itemList.readObject(it);
        item != null;
        item = (Item) board.itemList.readObject(it)) {
      tree.insert(item);
    }
    compensatedSearchTrees.add(tree);
    return tree;
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
    this.indexedShapeAngle = board.rules.getTraceAngleRestriction();
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
   * Returns a tree for the clearance class and the current outline geometry. General rooms retain
   * slanted outline boundaries without changing the permitted trace directions. Index bounds stay
   * conservative for the selected room-envelope policy.
   */
  public ShapeSearchTree getAutorouteTree(int clearanceClassIndex) {
    refreshIndexedShapesAfterAngleChange();
    // The general default tree can serve any room geometry, as before.
    if (defaultTree.compensatedClearanceClassNo == clearanceClassIndex) {
      return defaultTree;
    }
    Class<? extends ShapeSearchTree> treeType = autorouteTreeType();
    for (ShapeSearchTree currentTree : compensatedSearchTrees) {
      if (currentTree.compensatedClearanceClassNo == clearanceClassIndex
          && currentTree.getClass() == treeType
          && (treeType != ShapeSearchTree.class
              || currentTree.obstacleShapeAngle() == board.rules.getTraceAngleRestriction())) {
        return currentTree;
      }
    }

    // Create a new ShapeSearchTree object based on the board's settings
    ShapeSearchTree currentAutorouteTree;
    if (treeType == ShapeSearchTree90Degree.class) {
      // fast algorithm with 90 degree restriction
      currentAutorouteTree = new ShapeSearchTree90Degree(this.board, clearanceClassIndex);
    } else if (treeType == ShapeSearchTree45Degree.class) {
      // fast algorithm with 45 degree restriction
      currentAutorouteTree = new ShapeSearchTree45Degree(this.board, clearanceClassIndex);
    } else {
      // General room geometry; path construction still obeys the board's trace-angle restriction.
      AngleRestriction angle = board.rules.getTraceAngleRestriction();
      currentAutorouteTree =
          new ShapeSearchTree(
              angle == AngleRestriction.NINETY_DEGREE
                  ? OrthogonalBoundingDirections.INSTANCE
                  : FortyfiveDegreeBoundingDirections.INSTANCE,
              this.board,
              clearanceClassIndex,
              angle);
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

  /**
   * The default tree's drill and trace shapes read the live angle and are not keyed by it. After a
   * snap-angle change, rebuild those shapes and drop retained rooms before the same tree is reused.
   */
  private void refreshIndexedShapesAfterAngleChange() {
    AngleRestriction angle = board.rules.getTraceAngleRestriction();
    if (indexedShapeAngle == angle) {
      return;
    }
    indexedShapeAngle = angle;
    board.clearRetainedAutorouteState();
    reinsertTreeItems();
  }

  private Class<? extends ShapeSearchTree> autorouteTreeType() {
    AngleRestriction angle = board.rules.getTraceAngleRestriction();
    if (angle == AngleRestriction.NONE) {
      return ShapeSearchTree.class;
    }
    BoardOutline outline = board.getOutline();
    if (outline != null) {
      if (angle == AngleRestriction.NINETY_DEGREE
          && !outline.keepoutOutsideOutlineGenerated()
          && isSingleRectangle(outline)) {
        // At an outer rectangular corner, square strip caps only cover exterior copper or
        // copper already blocked by the adjacent edge. This does not hold at concave corners.
        return ShapeSearchTree90Degree.class;
      }
      // Inspect the actual general-tree pieces, including outside-area decomposition and every
      // layer. Testing only polygon edges misses slanted decomposition boundaries. Never replace
      // an outline piece by its enclosing box/octagon when that would discard a boundary.
      int shapeCount = outline.tileShapeCount();
      for (int i = 0; i < shapeCount; i++) {
        TileShape shape = outline.getTreeShape(defaultTree, i);
        if (shape != null
            && !(angle == AngleRestriction.NINETY_DEGREE
                ? shape.isIntBox()
                : shape.isIntOctagon())) {
          return ShapeSearchTree.class;
        }
      }
    }
    return angle == AngleRestriction.NINETY_DEGREE
        ? ShapeSearchTree90Degree.class
        : ShapeSearchTree45Degree.class;
  }

  private static boolean isSingleRectangle(BoardOutline outline) {
    if (outline.shapeCount() != 1) {
      return false;
    }
    var shape = outline.getShape(0);
    if (shape.borderLineCount() != 4 || shape.area() != shape.boundingBox().area()) {
      return false;
    }
    for (int i = 0; i < 4; i++) {
      if (!shape.borderLine(i).isOrthogonal()) {
        return false;
      }
    }
    return true;
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
}

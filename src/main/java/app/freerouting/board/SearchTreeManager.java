package app.freerouting.board;

import app.freerouting.datastructures.ShapeTree;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.Polyline;
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

  /** Creates a new instance of SearchTreeManager */
  public SearchTreeManager(BasicBoard pBoard) {
    board = pBoard;
    compensatedSearchTrees = new LinkedList<>();
    defaultTree = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, pBoard, 0);
    compensatedSearchTrees.add(defaultTree);
    this.clearanceCompensationUsed = false;
  }

  /** Inserts the tree shapes of p_item into all active search trees. */
  public void insert(Item pItem) {
    for (ShapeSearchTree currTree : compensatedSearchTrees) {
      currTree.insert(pItem);
    }
    pItem.setOnTheBoard(true);
  }

  /** Removes all entries of an item from the search trees. */
  public void remove(Item pItem) {
    if (!pItem.isOnTheBoard()) {
      return;
    }
    for (ShapeSearchTree currTree : compensatedSearchTrees) {

      ShapeTree.Leaf[] currTreeEntries = pItem.getSearchTreeEntries(currTree);
      {
        if (currTreeEntries != null) {
          currTree.remove(currTreeEntries);
        }
      }
    }
    pItem.clearSearchTreeEntries();
    pItem.setOnTheBoard(false);
  }

  /** Returns the default tree used in interactive routing. */
  public ShapeSearchTree getDefaultTree() {
    return defaultTree;
  }

  boolean validateEntries(Item pItem) {
    boolean result = true;
    for (ShapeSearchTree currTree : compensatedSearchTrees) {

      if (!currTree.validateEntries(pItem)) {
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
  public void setClearanceCompensationUsed(boolean pValue) {
    if (this.clearanceCompensationUsed == pValue) {
      return;
    }

    this.clearanceCompensationUsed = pValue;
    removeAllBoardItems();
    this.compensatedSearchTrees.clear();
    int compensatedClearanceClassNo;
    if (pValue) {
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
  public void clearanceClassRemoved(int pNo) {
    Iterator<ShapeSearchTree> it = this.compensatedSearchTrees.iterator();
    if (pNo == defaultTree.compensatedClearanceClassNo) {
      FRLogger.warn("SearchtreeManager.clearance_class_removed: unable to remove default tree");
      return;
    }
    while (it.hasNext()) {
      ShapeSearchTree currTree = it.next();
      if (currTree.compensatedClearanceClassNo == pNo) {
        it.remove();
      }
    }
  }

  /**
   * Returns the tree compensated for the clearance class with number p_clearance_class_no.
   * Initialized the tree, if it is not yet allocated.
   */
  public ShapeSearchTree getAutorouteTree(int pClearanceClassNo) {
    for (ShapeSearchTree currTree : compensatedSearchTrees) {
      if (currTree.compensatedClearanceClassNo == pClearanceClassNo) {
        return currTree;
      }
    }

    // Create a new ShapeSearchTree object based on the board's settings
    ShapeSearchTree currAutorouteTree;
    if (this.board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      // fast algorithm with 90 degree restriction
      currAutorouteTree = new ShapeSearchTree90Degree(this.board, pClearanceClassNo);
    } else if (this.board.rules.getTraceAngleRestriction() == AngleRestriction.FORTYFIVE_DEGREE) {
      // fast algorithm with 45 degree restriction
      currAutorouteTree = new ShapeSearchTree45Degree(this.board, pClearanceClassNo);
    } else {
      // slow algorithm or no angle restriction
      currAutorouteTree =
          new ShapeSearchTree(
              FortyfiveDegreeBoundingDirections.INSTANCE, this.board, pClearanceClassNo);
    }
    this.compensatedSearchTrees.add(currAutorouteTree);

    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.itemList.startReadObject();
    for (; ; ) {
      Item currItem = (Item) this.board.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      currAutorouteTree.insert(currItem);
    }
    return currAutorouteTree;
  }

  // ********************************************************************************

  // The following functions are used internally for performance improvement.

  // ********************************************************************************

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
      Item currItem = (Item) this.board.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      currItem.clearDerivedData();
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
      Item currItem = (Item) this.board.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      this.remove(currItem);
    }
  }

  private void insertAllBoardItems() {
    if (this.board == null) {
      FRLogger.warn("SearchtreeManager.insert_all_board_items: app.freerouting.board is null");
      return;
    }
    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.itemList.startReadObject();
    for (; ; ) {
      Item currItem = (Item) this.board.itemList.readObject(it);
      if (currItem == null) {
        break;
      }
      currItem.clearDerivedData();
      this.insert(currItem);
    }
  }

  /**
   * Merges the tree entries from p_from_trace in front of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void mergeEntriesInFront(
      PolylineTrace pFromTrace,
      PolylineTrace pToTrace,
      Polyline pJoinedPolyline,
      int pFromEntryNo,
      int pToEntryNo) {
    for (ShapeSearchTree currTree : compensatedSearchTrees) {
      currTree.mergeEntriesInFront(pFromTrace, pToTrace, pJoinedPolyline, pFromEntryNo, pToEntryNo);
    }
  }

  /**
   * Merges the tree entries from p_from_trace to the end of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void mergeEntriesAtEnd(
      PolylineTrace pFromTrace,
      PolylineTrace pToTrace,
      Polyline pJoinedPolyline,
      int pFromEntryNo,
      int pToEntryNo) {
    for (ShapeSearchTree currTree : compensatedSearchTrees) {
      currTree.mergeEntriesAtEnd(pFromTrace, pToTrace, pJoinedPolyline, pFromEntryNo, pToEntryNo);
    }
  }

  /**
   * Changes the tree entries from p_keep_at_start_count + 1 to newShapeCount - 1 - keepAtEndCount
   * to p_changed_entries. Special implementation for change_trace for performance reasons
   */
  void changeEntries(
      PolylineTrace pObj, Polyline pNewPolyline, int pKeepAtStartCount, int pKeepAtEndCount) {
    for (ShapeSearchTree currTree : compensatedSearchTrees) {
      currTree.changeEntries(pObj, pNewPolyline, pKeepAtStartCount, pKeepAtEndCount);
    }
  }

  /**
   * Transfers tree entries from p_from_trace to p_start and p_end_piece after a middle piece was
   * cut out. Special implementation for ShapeTraceEntries.fast_cutout_trace for performance
   * reasons.
   */
  void reuseEntriesAfterCutout(
      PolylineTrace pFromTrace, PolylineTrace pStartPiece, PolylineTrace pEndPiece) {
    for (ShapeSearchTree currTree : compensatedSearchTrees) {

      currTree.reuseEntriesAfterCutout(pFromTrace, pStartPiece, pEndPiece);
    }
  }
}

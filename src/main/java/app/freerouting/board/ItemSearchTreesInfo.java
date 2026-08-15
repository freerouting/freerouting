package app.freerouting.board;

import app.freerouting.datastructures.ShapeTree;
import app.freerouting.geometry.planar.TileShape;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Stores information about the search trees of the board items, which is precalculated for
 * performance reasons.
 */
class ItemSearchTreesInfo {

  private final Collection<SearchTreeInfo> treeList;

  /** Creates a new instance of ItemSearchTreeEntries. */
  public ItemSearchTreesInfo() {
    this.treeList = new LinkedList<>();
  }

  /**
   * Returns the tree entries for the tree with identification number p_tree_no, or null, if for
   * this tree no entries of this item are inserted.
   */
  public ShapeTree.Leaf[] getTreeEntries(ShapeTree tree) {
    for (SearchTreeInfo currentTreeInfo : this.treeList) {
      if (currentTreeInfo.tree == tree) {
        return currentTreeInfo.entryArr;
      }
    }
    return null;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  public void setTreeEntries(ShapeTree.Leaf[] treeEntries, ShapeTree tree) {
    for (SearchTreeInfo currentTreeInfo : this.treeList) {
      if (currentTreeInfo.tree == tree) {
        currentTreeInfo.entryArr = treeEntries;
        return;
      }
    }
    SearchTreeInfo newTreeInfo = new SearchTreeInfo(tree);
    newTreeInfo.entryArr = treeEntries;
    this.treeList.add(newTreeInfo);
  }

  /**
   * Returns the precalculated tiles shapes for the tree with identification number p_tree_no, or
   * null, if the tile shapes of this tree are not yet precalculated.
   */
  public TileShape[] getPrecalculatedTreeShapes(ShapeTree tree) {
    for (SearchTreeInfo currentTreeInfo : this.treeList) {
      if (currentTreeInfo.tree == tree) {
        return currentTreeInfo.precalculatedTreeShapes;
      }
    }
    return null;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  public void setPrecalculatedTreeShapes(TileShape[] tileShapes, ShapeTree tree) {
    for (SearchTreeInfo currentTreeInfo : this.treeList) {
      if (currentTreeInfo.tree == tree) {
        currentTreeInfo.precalculatedTreeShapes = tileShapes;
        return;
      }
    }
    SearchTreeInfo newTreeInfo = new SearchTreeInfo(tree);
    newTreeInfo.precalculatedTreeShapes = tileShapes;
    this.treeList.add(newTreeInfo);
  }

  /** Clears the stored information about the precalculated tree shapes for all search trees. */
  public void clearPrecalculatedTreeShapes() {
    for (SearchTreeInfo currentTreeInfo : this.treeList) {

      currentTreeInfo.precalculatedTreeShapes = null;
    }
  }

  private static class SearchTreeInfo {

    final ShapeTree tree;
    ShapeTree.Leaf[] entryArr;
    TileShape[] precalculatedTreeShapes;

    SearchTreeInfo(ShapeTree tree) {
      this.tree = tree;
      entryArr = null;
      precalculatedTreeShapes = null;
    }
  }
}

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

  /** Creates a new instance of ItemSearchTreeEntries */
  public ItemSearchTreesInfo() {
    this.treeList = new LinkedList<>();
  }

  /**
   * Returns the tree entries for the tree with identification number p_tree_no, or null, if for
   * this tree no entries of this item are inserted.
   */
  public ShapeTree.Leaf[] getTreeEntries(ShapeTree pTree) {
    for (SearchTreeInfo currTreeInfo : this.treeList) {
      if (currTreeInfo.tree == pTree) {
        return currTreeInfo.entryArr;
      }
    }
    return null;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  public void setTreeEntries(ShapeTree.Leaf[] pTreeEntries, ShapeTree pTree) {
    for (SearchTreeInfo currTreeInfo : this.treeList) {
      if (currTreeInfo.tree == pTree) {
        currTreeInfo.entryArr = pTreeEntries;
        return;
      }
    }
    SearchTreeInfo newTreeInfo = new SearchTreeInfo(pTree);
    newTreeInfo.entryArr = pTreeEntries;
    this.treeList.add(newTreeInfo);
  }

  /**
   * Returns the precalculated tiles shapes for the tree with identification number p_tree_no, or
   * null, if the tile shapes of this tree are not yet precalculated.
   */
  public TileShape[] getPrecalculatedTreeShapes(ShapeTree pTree) {
    for (SearchTreeInfo currTreeInfo : this.treeList) {
      if (currTreeInfo.tree == pTree) {
        return currTreeInfo.precalculatedTreeShapes;
      }
    }
    return null;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  public void setPrecalculatedTreeShapes(TileShape[] pTileShapes, ShapeTree pTree) {
    for (SearchTreeInfo currTreeInfo : this.treeList) {
      if (currTreeInfo.tree == pTree) {
        currTreeInfo.precalculatedTreeShapes = pTileShapes;
        return;
      }
    }
    SearchTreeInfo newTreeInfo = new SearchTreeInfo(pTree);
    newTreeInfo.precalculatedTreeShapes = pTileShapes;
    this.treeList.add(newTreeInfo);
  }

  /** clears the stored information about the precalculated tree shapes for all search trees. */
  public void clearPrecalculatedTreeShapes() {
    for (SearchTreeInfo currTreeInfo : this.treeList) {

      currTreeInfo.precalculatedTreeShapes = null;
    }
  }

  private static class SearchTreeInfo {

    final ShapeTree tree;
    ShapeTree.Leaf[] entryArr;
    TileShape[] precalculatedTreeShapes;

    SearchTreeInfo(ShapeTree pTree) {
      tree = pTree;
      entryArr = null;
      precalculatedTreeShapes = null;
    }
  }
}

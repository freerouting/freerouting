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
  public ShapeTree.Leaf[] getTreeEntries(ShapeTree p_tree) {
    for (SearchTreeInfo curr_tree_info : this.treeList) {
      if (curr_tree_info.tree == p_tree) {
        return curr_tree_info.entryArr;
      }
    }
    return null;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  public void setTreeEntries(ShapeTree.Leaf[] p_tree_entries, ShapeTree p_tree) {
    for (SearchTreeInfo curr_tree_info : this.treeList) {
      if (curr_tree_info.tree == p_tree) {
        curr_tree_info.entryArr = p_tree_entries;
        return;
      }
    }
    SearchTreeInfo newTreeInfo = new SearchTreeInfo(p_tree);
    newTreeInfo.entryArr = p_tree_entries;
    this.treeList.add(newTreeInfo);
  }

  /**
   * Returns the precalculated tiles shapes for the tree with identification number p_tree_no, or
   * null, if the tile shapes of this tree are not yet precalculated.
   */
  public TileShape[] getPrecalculatedTreeShapes(ShapeTree p_tree) {
    for (SearchTreeInfo curr_tree_info : this.treeList) {
      if (curr_tree_info.tree == p_tree) {
        return curr_tree_info.precalculatedTreeShapes;
      }
    }
    return null;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  public void setPrecalculatedTreeShapes(TileShape[] p_tile_shapes, ShapeTree p_tree) {
    for (SearchTreeInfo curr_tree_info : this.treeList) {
      if (curr_tree_info.tree == p_tree) {
        curr_tree_info.precalculatedTreeShapes = p_tile_shapes;
        return;
      }
    }
    SearchTreeInfo newTreeInfo = new SearchTreeInfo(p_tree);
    newTreeInfo.precalculatedTreeShapes = p_tile_shapes;
    this.treeList.add(newTreeInfo);
  }

  /** clears the stored information about the precalculated tree shapes for all search trees. */
  public void clearPrecalculatedTreeShapes() {
    for (SearchTreeInfo curr_tree_info : this.treeList) {

      curr_tree_info.precalculatedTreeShapes = null;
    }
  }

  private static class SearchTreeInfo {

    final ShapeTree tree;
    ShapeTree.Leaf[] entryArr;
    TileShape[] precalculatedTreeShapes;

    SearchTreeInfo(ShapeTree p_tree) {
      tree = p_tree;
      entryArr = null;
      precalculatedTreeShapes = null;
    }
  }
}

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

  private final Collection<SearchTreeInfo> tree_list;

  /** Creates a new instance of ItemSearchTreeEntries */
  public ItemSearchTreesInfo() {
    this.tree_list = new LinkedList<SearchTreeInfo>();
  }

  /**
   * Returns the tree entries for the tree with identification number p_tree_no, or null, if for
   * this tree no entries of this item are inserted.
   */
  public ShapeTree.Leaf[] get_tree_entries(ShapeTree p_tree) {
    for (SearchTreeInfo curr_tree_info : this.tree_list) {
      if (curr_tree_info.tree == p_tree) {
        return curr_tree_info.entry_arr;
      }
    }
    return null;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  public void set_tree_entries(ShapeTree.Leaf[] p_tree_entries, ShapeTree p_tree) {
    for (SearchTreeInfo curr_tree_info : this.tree_list) {
      if (curr_tree_info.tree == p_tree) {
        curr_tree_info.entry_arr = p_tree_entries;
        return;
      }
    }
    SearchTreeInfo new_tree_info = new SearchTreeInfo(p_tree);
    new_tree_info.entry_arr = p_tree_entries;
    this.tree_list.add(new_tree_info);
  }

  /**
   * Returns the precalculated tiles hapes for the tree with identification number p_tree_no, or
   * null, if the tile shapes of this tree are nnot yet precalculated.
   */
  public TileShape[] get_precalculated_tree_shapes(ShapeTree p_tree) {
    for (SearchTreeInfo curr_tree_info : this.tree_list) {
      if (curr_tree_info.tree == p_tree) {
        return curr_tree_info.precalculated_tree_shapes;
      }
    }
    return null;
  }

  /** Sets the item tree entries for the tree with identification number p_tree_no. */
  public void set_precalculated_tree_shapes(TileShape[] p_tile_shapes, ShapeTree p_tree) {
    for (SearchTreeInfo curr_tree_info : this.tree_list) {
      if (curr_tree_info.tree == p_tree) {
        curr_tree_info.precalculated_tree_shapes = p_tile_shapes;
        return;
      }
    }
    SearchTreeInfo new_tree_info = new SearchTreeInfo(p_tree);
    new_tree_info.precalculated_tree_shapes = p_tile_shapes;
    this.tree_list.add(new_tree_info);
  }

  /** clears the stored information about the precalculated tree shapes for all search trees. */
  public void clear_precalculated_tree_shapes() {
    for (SearchTreeInfo curr_tree_info : this.tree_list) {

      curr_tree_info.precalculated_tree_shapes = null;
    }
  }

  private static class SearchTreeInfo {
    final ShapeTree tree;
    ShapeTree.Leaf[] entry_arr;
    TileShape[] precalculated_tree_shapes;
    SearchTreeInfo(ShapeTree p_tree) {
      tree = p_tree;
      entry_arr = null;
      precalculated_tree_shapes = null;
    }
  }
}

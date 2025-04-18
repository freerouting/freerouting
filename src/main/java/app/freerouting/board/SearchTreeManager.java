package app.freerouting.board;

import app.freerouting.datastructures.ShapeTree;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.logger.FRLogger;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Objects;

/**
 * The SearchTreeManager manages the search trees used in the auto-router.
 * It is responsible for the creation of the search trees and the insertion and removal of items from the trees.
 * The SearchTreeManager also provides functions to merge and change tree entries for performance reasons.
 */
public class SearchTreeManager
{
  private final Collection<ShapeSearchTree> compensated_search_trees;
  private final BasicBoard board;
  private ShapeSearchTree default_tree;
  private boolean clearance_compensation_used;

  /**
   * Creates a new instance of SearchTreeManager
   */
  public SearchTreeManager(BasicBoard p_board)
  {
    board = p_board;
    compensated_search_trees = new LinkedList<>();
    default_tree = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, p_board, 0);
    compensated_search_trees.add(default_tree);
    this.clearance_compensation_used = false;
  }

  /**
   * Inserts the tree shapes of p_item into all active search trees.
   */
  public void insert(Item p_item)
  {
    for (ShapeSearchTree curr_tree : compensated_search_trees)
    {
      curr_tree.insert(p_item);
    }
    p_item.set_on_the_board(true);
  }

  /**
   * Removes all entries of an item from the search trees.
   */
  public void remove(Item p_item)
  {
    if (!p_item.is_on_the_board())
    {
      return;
    }
    for (ShapeSearchTree curr_tree : compensated_search_trees)
    {

      ShapeTree.Leaf[] curr_tree_entries = p_item.get_search_tree_entries(curr_tree);
      {
        if (curr_tree_entries != null)
        {
          curr_tree.remove(curr_tree_entries);
        }
      }
    }
    p_item.clear_search_tree_entries();
    p_item.set_on_the_board(false);
  }

  /**
   * Returns the default tree used in interactive routing.
   */
  public ShapeSearchTree get_default_tree()
  {
    return default_tree;
  }

  boolean validate_entries(Item p_item)
  {
    boolean result = true;
    for (ShapeSearchTree curr_tree : compensated_search_trees)
    {

      if (!curr_tree.validate_entries(p_item))
      {
        result = false;
      }
    }
    return result;
  }

  /**
   * Returns, if clearance compensation is used for the default tree. This is normally the case, if
   * there exist only the clearance classes null and default in the clearance matrix.
   */
  public boolean is_clearance_compensation_used()
  {
    return this.clearance_compensation_used;
  }

  /**
   * Sets the usage of clearance compensation to true or false.
   */
  public void set_clearance_compensation_used(boolean p_value)
  {
    if (this.clearance_compensation_used == p_value)
    {
      return;
    }

    this.clearance_compensation_used = p_value;
    remove_all_board_items();
    this.compensated_search_trees.clear();
    int compensated_clearance_class_no;
    if (p_value)
    {
      compensated_clearance_class_no = 1;
    }
    else
    {
      compensated_clearance_class_no = 0;
    }
    default_tree = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, this.board, compensated_clearance_class_no);
    this.compensated_search_trees.add(default_tree);
    insert_all_board_items();
  }

  /**
   * Actions to be done, when a value in the clearance matrix is changed interactively.
   */
  public void clearance_value_changed()
  {
    // delete all trees except the default tree
    this.compensated_search_trees.removeIf(t -> t.compensated_clearance_class_no != default_tree.compensated_clearance_class_no);
    if (this.clearance_compensation_used)
    {
      remove_all_board_items();
      insert_all_board_items();
    }
  }

  /**
   * Actions to be done, when a new clearance class is removed interactively.
   */
  public void clearance_class_removed(int p_no)
  {
    Iterator<ShapeSearchTree> it = this.compensated_search_trees.iterator();
    if (p_no == default_tree.compensated_clearance_class_no)
    {
      FRLogger.warn("SearchtreeManager.clearance_class_removed: unable to remove default tree");
      return;
    }
    while (it.hasNext())
    {
      ShapeSearchTree curr_tree = it.next();
      if (curr_tree.compensated_clearance_class_no == p_no)
      {
        it.remove();
      }
    }
  }

  /**
   * Returns the tree compensated for the clearance class with number p_clearance_class_no.
   * Initialized the tree, if it is not yet allocated.
   */
  public ShapeSearchTree get_autoroute_tree(int p_clearance_class_no, boolean p_use_slow_algorithm)
  {
    boolean use_fast_algorithm = !p_use_slow_algorithm;
    String preferred_autoroute_tree_key = "ShapeSearchTree_FortyfiveDegree_cc" + p_clearance_class_no;
    if (use_fast_algorithm && this.board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
    {
      preferred_autoroute_tree_key = "ShapeSearchTree90Degree_NinetyDegree_cc" + p_clearance_class_no;
    }
    else if (use_fast_algorithm && this.board.rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
    {
      preferred_autoroute_tree_key = "ShapeSearchTree45Degree_FortyfiveDegree_cc" + p_clearance_class_no;
    }

    for (ShapeSearchTree curr_tree : compensated_search_trees)
    {
      if (Objects.equals(curr_tree.key, preferred_autoroute_tree_key))
      {
        return curr_tree;
      }
    }

    // Create a new ShapeSearchTree object based on the board's settings
    ShapeSearchTree curr_autoroute_tree;
    if (use_fast_algorithm && this.board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
    {
      // fast algorithm with 90 degree restriction
      curr_autoroute_tree = new ShapeSearchTree90Degree(this.board, p_clearance_class_no);
    }
    else if (use_fast_algorithm && this.board.rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
    {
      // fast algorithm with 45 degree restriction
      curr_autoroute_tree = new ShapeSearchTree45Degree(this.board, p_clearance_class_no);
    }
    else
    {
      // slow algorithm or no angle restriction
      curr_autoroute_tree = new ShapeSearchTree(FortyfiveDegreeBoundingDirections.INSTANCE, this.board, p_clearance_class_no);
    }
    this.compensated_search_trees.add(curr_autoroute_tree);

    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.item_list.start_read_object();
    for (; ; )
    {
      Item curr_item = (Item) this.board.item_list.read_object(it);
      if (curr_item == null)
      {
        break;
      }
      curr_autoroute_tree.insert(curr_item);
    }
    return curr_autoroute_tree;
  }

  // ********************************************************************************

  // The following functions are used internally for performance improvement.

  // ********************************************************************************

  /**
   * Clears all compensated trees used in the autoroute algorithm apart from the default tree.
   */
  public void reset_compensated_trees()
  {
    this.compensated_search_trees.removeIf(t -> t != default_tree);
  }

  /**
   * Reinsert all items into the search trees
   */
  void reinsert_tree_items()
  {
    remove_all_board_items();
    insert_all_board_items();
  }

  private void remove_all_board_items()
  {
    if (this.board == null)
    {
      FRLogger.warn("SearchtreeManager.remove_all_board_items: app.freerouting.board is null");
      return;
    }
    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.item_list.start_read_object();
    for (; ; )
    {
      Item curr_item = (Item) this.board.item_list.read_object(it);
      if (curr_item == null)
      {
        break;
      }
      this.remove(curr_item);
    }
  }

  private void insert_all_board_items()
  {
    if (this.board == null)
    {
      FRLogger.warn("SearchtreeManager.insert_all_board_items: app.freerouting.board is null");
      return;
    }
    Iterator<UndoableObjects.UndoableObjectNode> it = this.board.item_list.start_read_object();
    for (; ; )
    {
      Item curr_item = (Item) this.board.item_list.read_object(it);
      if (curr_item == null)
      {
        break;
      }
      curr_item.clear_derived_data();
      this.insert(curr_item);
    }
  }

  /**
   * Merges the tree entries from p_from_trace in front of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void merge_entries_in_front(PolylineTrace p_from_trace, PolylineTrace p_to_trace, Polyline p_joined_polyline, int p_from_entry_no, int p_to_entry_no)
  {
    for (ShapeSearchTree curr_tree : compensated_search_trees)
    {
      curr_tree.merge_entries_in_front(p_from_trace, p_to_trace, p_joined_polyline, p_from_entry_no, p_to_entry_no);
    }
  }

  /**
   * Merges the tree entries from p_from_trace to the end of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void merge_entries_at_end(PolylineTrace p_from_trace, PolylineTrace p_to_trace, Polyline p_joined_polyline, int p_from_entry_no, int p_to_entry_no)
  {
    for (ShapeSearchTree curr_tree : compensated_search_trees)
    {
      curr_tree.merge_entries_at_end(p_from_trace, p_to_trace, p_joined_polyline, p_from_entry_no, p_to_entry_no);
    }
  }

  /**
   * Changes the tree entries from p_keep_at_start_count + 1 to new_shape_count - 1 -
   * keep_at_end_count to p_changed_entries. Special implementation for change_trace for performance
   * reasons
   */
  void change_entries(PolylineTrace p_obj, Polyline p_new_polyline, int p_keep_at_start_count, int p_keep_at_end_count)
  {
    for (ShapeSearchTree curr_tree : compensated_search_trees)
    {
      curr_tree.change_entries(p_obj, p_new_polyline, p_keep_at_start_count, p_keep_at_end_count);
    }
  }

  /**
   * Transfers tree entries from p_from_trace to p_start and p_end_piece after a middle piece was
   * cut out. Special implementation for ShapeTraceEntries.fast_cutout_trace for performance
   * reasons.
   */
  void reuse_entries_after_cutout(PolylineTrace p_from_trace, PolylineTrace p_start_piece, PolylineTrace p_end_piece)
  {
    for (ShapeSearchTree curr_tree : compensated_search_trees)
    {

      curr_tree.reuse_entries_after_cutout(p_from_trace, p_start_piece, p_end_piece);
    }
  }
}
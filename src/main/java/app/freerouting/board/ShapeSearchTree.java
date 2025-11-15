package app.freerouting.board;

import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.datastructures.MinAreaTree;
import app.freerouting.datastructures.Signum;
import app.freerouting.geometry.planar.*;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ClearanceMatrix;

import java.util.*;

/**
 * Elementary geometric search functions making direct use of the MinAreaTree in the package
 * datastructures.
 */
public class ShapeSearchTree extends MinAreaTree
{
  /**
   * used in objects of class EntrySortedByClearance
   */
  private static int last_generated_id_no = 0;
  /**
   * The clearance class number for which the shapes of this tree is compensated. If
   * compensated_clearance_class_no = 0, the shapes are not compensated.
   */
  public final int compensated_clearance_class_no;
  public final String key;
  protected final BasicBoard board;

  /**
   * Creates a new ShapeSearchTree. p_compensated_clearance_class_no is the clearance class number
   * for which the shapes of this tree is compensated. If p_compensated_clearance_class_no = 0, the
   * shapes are not compensated.
   */
  ShapeSearchTree(ShapeBoundingDirections p_directions, BasicBoard p_board, int p_compensated_clearance_class_no)
  {
    super(p_directions);
    this.compensated_clearance_class_no = p_compensated_clearance_class_no;
    board = p_board;
    key = getKey(this, p_directions, compensated_clearance_class_no);
  }

  public static String getKey(ShapeSearchTree searchTree, ShapeBoundingDirections directions, int clearance_class)
  {
    return searchTree
        .getClass()
        .getSimpleName() + "_" + directions
        .getClass()
        .getSimpleName()
        .replaceAll("BoundingDirections", "") + "_cc" + clearance_class;
  }

  @Override
  public String toString()
  {
    return key;
  }


  /**
   * Returns, if for the shapes stored in this tree clearance compensation is used.
   */
  public boolean is_clearance_compensation_used()
  {
    return this.compensated_clearance_class_no > 0;
  }

  /**
   * Return the clearance compensation value of p_clearance_class_no to the clearance compensation
   * class of this search tree with on layer p_layer. Returns 0, if no clearance compensation is
   * used for this tree.
   */
  public int clearance_compensation_value(int p_clearance_class_no, int p_layer)
  {
    if (p_clearance_class_no <= 0)
    {
      return 0;
    }
    int result = board.rules.clearance_matrix.get_value(p_clearance_class_no, this.compensated_clearance_class_no, p_layer, false) - board.rules.clearance_matrix.clearance_compensation_value(this.compensated_clearance_class_no, p_layer);
    return Math.max(result, 0);
  }

  /**
   * Changes the tree entries from p_keep_at_start_count + 1 to new_shape_count - 1 -
   * keep_at_end_count to p_changed_entries. Special implementation for change_trace for performance
   * reasons
   */
  void change_entries(PolylineTrace p_obj, Polyline p_new_polyline, int p_keep_at_start_count, int p_keep_at_end_count)
  {
    // calculate the shapes of p_new_polyline from keep_at_start_count to
    // new_shape_count - keep_at_end_count - 1;
    int compensated_half_width = p_obj.get_half_width() + this.clearance_compensation_value(p_obj.clearance_class_no(), p_obj.get_layer());
    TileShape[] changed_shapes = this.offset_shapes(p_new_polyline, compensated_half_width, p_keep_at_start_count, p_new_polyline.arr.length - 1 - p_keep_at_end_count);
    int old_shape_count = p_obj.tree_shape_count(this);
    int new_shape_count = changed_shapes.length + p_keep_at_start_count + p_keep_at_end_count;
    Leaf[] new_leaf_arr = new Leaf[new_shape_count];
    TileShape[] new_precalculated_tree_shapes = new TileShape[new_shape_count];
    Leaf[] old_entries = p_obj.get_search_tree_entries(this);
    for (int i = 0; i < p_keep_at_start_count; ++i)
    {
      new_leaf_arr[i] = old_entries[i];
      new_precalculated_tree_shapes[i] = p_obj.get_tree_shape(this, i);
    }
    for (int i = p_keep_at_start_count; i < old_shape_count - p_keep_at_end_count; ++i)
    {
      remove_leaf(old_entries[i]);
    }
    for (int i = 0; i < p_keep_at_end_count; ++i)
    {
      int new_index = new_shape_count - p_keep_at_end_count + i;
      int old_index = old_shape_count - p_keep_at_end_count + i;

      new_leaf_arr[new_index] = old_entries[old_index];
      new_leaf_arr[new_index].shape_index_in_object = new_index;
      new_precalculated_tree_shapes[new_index] = p_obj.get_tree_shape(this, old_index);
    }

    // correct the precalculated tree shapes first, because it is used in this.insert
    System.arraycopy(changed_shapes, 0, new_precalculated_tree_shapes, p_keep_at_start_count, changed_shapes.length);
    p_obj.set_precalculated_tree_shapes(new_precalculated_tree_shapes, this);

    for (int i = p_keep_at_start_count; i < new_shape_count - p_keep_at_end_count; ++i)
    {
      new_leaf_arr[i] = insert(p_obj, i);
    }
    p_obj.set_search_tree_entries(new_leaf_arr, this);
  }

  /**
   * Merges the tree entries from p_from_trace in front of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void merge_entries_in_front(PolylineTrace p_from_trace, PolylineTrace p_to_trace, Polyline p_joined_polyline, int p_from_entry_no, int p_to_entry_no)
  {
    int compensated_half_width = p_to_trace.get_half_width() + this.clearance_compensation_value(p_to_trace.clearance_class_no(), p_to_trace.get_layer());
    TileShape[] link_shapes = this.offset_shapes(p_joined_polyline, compensated_half_width, p_from_entry_no, p_to_entry_no);
    boolean change_order = p_from_trace
        .first_corner()
        .equals(p_to_trace.first_corner());
    // remove the last or first tree entry from p_from_trace and the
    // first tree entry from p_to_trace, because they will be replaced by
    // the new link entries.
    int from_shape_count_minus_1 = p_from_trace.tile_shape_count() - 1;
    int remove_no;
    if (change_order)
    {
      remove_no = 0;
    }
    else
    {
      remove_no = from_shape_count_minus_1;
    }
    Leaf[] from_trace_entries = p_from_trace.get_search_tree_entries(this);
    Leaf[] to_trace_entries = p_to_trace.get_search_tree_entries(this);
    remove_leaf(from_trace_entries[remove_no]);
    remove_leaf(to_trace_entries[0]);
    int new_shape_count = from_trace_entries.length + link_shapes.length + to_trace_entries.length - 2;
    Leaf[] new_leaf_arr = new Leaf[new_shape_count];
    int old_to_shape_count = to_trace_entries.length;
    TileShape[] new_precalculated_tree_shapes = new TileShape[new_shape_count];
    // transfer the tree entries except the last or first from p_from_trace to p_to_trace
    for (int i = 0; i < from_shape_count_minus_1; ++i)
    {
      int from_no;
      if (change_order)
      {
        from_no = from_shape_count_minus_1 - i;
      }
      else
      {
        from_no = i;
      }
      new_precalculated_tree_shapes[i] = p_from_trace.get_tree_shape(this, from_no);
      new_leaf_arr[i] = from_trace_entries[from_no];
      new_leaf_arr[i].object = p_to_trace;
      new_leaf_arr[i].shape_index_in_object = i;
    }
    for (int i = 1; i < old_to_shape_count; ++i)
    {
      int curr_ind = from_shape_count_minus_1 + link_shapes.length + i - 1;
      new_precalculated_tree_shapes[curr_ind] = p_to_trace.get_tree_shape(this, i);
      new_leaf_arr[curr_ind] = to_trace_entries[i];
      new_leaf_arr[curr_ind].shape_index_in_object = curr_ind;
    }

    // correct the precalculated tree shapes first, because it is used in this.insert
    for (int i = 0; i < link_shapes.length; ++i)
    {
      int curr_ind = from_shape_count_minus_1 + i;
      new_precalculated_tree_shapes[curr_ind] = link_shapes[i];
    }
    p_to_trace.set_precalculated_tree_shapes(new_precalculated_tree_shapes, this);

    // create the new link entries
    for (int i = 0; i < link_shapes.length; ++i)
    {
      int curr_ind = from_shape_count_minus_1 + i;
      new_leaf_arr[curr_ind] = insert(p_to_trace, curr_ind);
    }

    p_to_trace.set_search_tree_entries(new_leaf_arr, this);
  }

  /**
   * Merges the tree entries from p_from_trace to the end of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void merge_entries_at_end(PolylineTrace p_from_trace, PolylineTrace p_to_trace, Polyline p_joined_polyline, int p_from_entry_no, int p_to_entry_no)
  {
    int compensated_half_width = p_to_trace.get_half_width() + this.clearance_compensation_value(p_to_trace.clearance_class_no(), p_to_trace.get_layer());
    TileShape[] link_shapes = this.offset_shapes(p_joined_polyline, compensated_half_width, p_from_entry_no, p_to_entry_no);
    boolean change_order = p_from_trace
        .last_corner()
        .equals(p_to_trace.last_corner());
    Leaf[] from_trace_entries = p_from_trace.get_search_tree_entries(this);
    Leaf[] to_trace_entries = p_to_trace.get_search_tree_entries(this);
    // remove the last or first tree entry from p_from_trace and the
    // last tree entry from p_to_trace, because they will be replaced by
    // the new link entries.
    int to_shape_count_minus_1 = p_to_trace.tile_shape_count() - 1;
    remove_leaf(to_trace_entries[to_shape_count_minus_1]);
    int remove_no;
    if (change_order)
    {
      remove_no = p_from_trace.tile_shape_count() - 1;
    }
    else
    {
      remove_no = 0;
    }
    remove_leaf(from_trace_entries[remove_no]);
    int new_shape_count = from_trace_entries.length + link_shapes.length + to_trace_entries.length - 2;
    Leaf[] new_leaf_arr = new Leaf[new_shape_count];
    TileShape[] new_precalculated_tree_shapes = new TileShape[new_shape_count];
    // transfer the tree entries except the last from the old shapes
    // of p_to_trace to the new shapes of p_to_trace
    for (int i = 0; i < to_shape_count_minus_1; ++i)
    {
      new_precalculated_tree_shapes[i] = p_to_trace.get_tree_shape(this, i);
      new_leaf_arr[i] = to_trace_entries[i];
    }

    for (int i = 1; i < from_trace_entries.length; ++i)
    {
      int curr_ind = to_shape_count_minus_1 + link_shapes.length + i - 1;
      int from_no;
      if (change_order)
      {
        from_no = from_trace_entries.length - i - 1;
      }
      else
      {
        from_no = i;
      }
      new_precalculated_tree_shapes[curr_ind] = p_from_trace.get_tree_shape(this, from_no);
      new_leaf_arr[curr_ind] = from_trace_entries[from_no];
      new_leaf_arr[curr_ind].object = p_to_trace;
      new_leaf_arr[curr_ind].shape_index_in_object = curr_ind;
    }

    // correct the precalculated tree shapes first, because it is used in this.insert
    for (int i = 0; i < link_shapes.length; ++i)
    {
      int curr_ind = to_shape_count_minus_1 + i;
      new_precalculated_tree_shapes[curr_ind] = link_shapes[i];
    }
    p_to_trace.set_precalculated_tree_shapes(new_precalculated_tree_shapes, this);

    // create the new link entries
    for (int i = 0; i < link_shapes.length; ++i)
    {
      int curr_ind = to_shape_count_minus_1 + i;
      new_leaf_arr[curr_ind] = insert(p_to_trace, curr_ind);
    }
    p_to_trace.set_search_tree_entries(new_leaf_arr, this);
  }

  /**
   * Transfers tree entries from p_from_trace to p_start and p_end_piece after a middle piece was
   * cut out. Special implementation for ShapeTraceEntries.fast_cutout_trace for performance
   * reasons.
   */
  void reuse_entries_after_cutout(PolylineTrace p_from_trace, PolylineTrace p_start_piece, PolylineTrace p_end_piece)
  {
    Leaf[] start_piece_leaf_arr = new Leaf[p_start_piece.polyline().arr.length - 2];
    Leaf[] from_trace_entries = p_from_trace.get_search_tree_entries(this);
    // transfer the entries at the start of p_from_trace to p_start_piece.
    for (int i = 0; i < start_piece_leaf_arr.length - 1; ++i)
    {
      start_piece_leaf_arr[i] = from_trace_entries[i];
      start_piece_leaf_arr[i].object = p_start_piece;
      start_piece_leaf_arr[i].shape_index_in_object = i;
      from_trace_entries[i] = null;
    }
    start_piece_leaf_arr[start_piece_leaf_arr.length - 1] = insert(p_start_piece, start_piece_leaf_arr.length - 1);

    // create the last tree entry of the start piece.

    Leaf[] end_piece_leaf_arr = new Leaf[p_end_piece.polyline().arr.length - 2];

    // create the first tree entry of the end piece.
    end_piece_leaf_arr[0] = insert(p_end_piece, 0);

    for (int i = 1; i < end_piece_leaf_arr.length; ++i)
    {
      int from_index = from_trace_entries.length - end_piece_leaf_arr.length + i;
      end_piece_leaf_arr[i] = from_trace_entries[from_index];
      end_piece_leaf_arr[i].object = p_end_piece;
      end_piece_leaf_arr[i].shape_index_in_object = i;
      from_trace_entries[from_index] = null;
    }

    p_start_piece.set_search_tree_entries(start_piece_leaf_arr, this);
    p_end_piece.set_search_tree_entries(end_piece_leaf_arr, this);
  }

  /**
   * Puts all items in the tree overlapping with p_shape on layer p_layer into p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlapping_objects(ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, Set<SearchTreeObject> p_obstacles)
  {
    Collection<TreeEntry> tree_entries = new LinkedList<>();
    overlapping_tree_entries(p_shape, p_layer, p_ignore_net_nos, tree_entries);
    if (p_obstacles != null)
    {
      for (TreeEntry curr_entry : tree_entries)
      {
        p_obstacles.add((SearchTreeObject) curr_entry.object);
      }
    }
  }

  /**
   * Returns all SearchTreeObjects on layer p_layer, which overlap with p_shape. If p_layer
   * {@literal <} 0, the layer is ignored
   */
  public Set<SearchTreeObject> overlapping_objects(ConvexShape p_shape, int p_layer)
  {
    Set<SearchTreeObject> result = new TreeSet<>();
    this.overlapping_objects(p_shape, p_layer, new int[0], result);
    return result;
  }

  /**
   * Puts all tree entries overlapping with p_shape on layer p_layer into the list p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlapping_tree_entries(ConvexShape p_shape, int p_layer, Collection<TreeEntry> p_tree_entries)
  {
    overlapping_tree_entries(p_shape, p_layer, new int[0], p_tree_entries);
  }

  /**
   * Puts all tree entries overlapping with p_shape on layer p_layer into the list p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored. tree_entries with object containing a net number
   * of p_ignore_net_nos are ignored.
   */
  public void overlapping_tree_entries(ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, Collection<TreeEntry> p_tree_entries)
  {
    if (p_shape == null)
    {
      return;
    }
    if (p_tree_entries == null)
    {
      FRLogger.warn("ShapeSearchTree.overlaps: p_obstacle_entries is null");
      return;
    }
    RegularTileShape bounds = p_shape.bounding_shape(bounding_directions);
    if (bounds == null)
    {
      FRLogger.warn("ShapeSearchTree.overlaps: p_shape not bounded");
      return;
    }
    Collection<Leaf> tmp_list = this.overlaps(bounds);
    boolean is_45_degree = p_shape instanceof IntOctagon;

    for (Leaf curr_leaf : tmp_list)
    {
      SearchTreeObject curr_object = (SearchTreeObject) curr_leaf.object;
      int shape_index = curr_leaf.shape_index_in_object;
      boolean ignore_object = p_layer >= 0 && curr_object.shape_layer(shape_index) != p_layer;
      if (!ignore_object)
      {
        for (int i = 0; i < p_ignore_net_nos.length; ++i)
        {
          if (!curr_object.is_obstacle(p_ignore_net_nos[i]))
          {
            ignore_object = true;
          }
        }
      }
      if (!ignore_object)
      {
        TileShape curr_shape = curr_object.get_tree_shape(this, curr_leaf.shape_index_in_object);
        boolean add_item;
        if (is_45_degree && curr_shape instanceof IntOctagon)
        // in this case the check for intersection is redundant and
        // therefore skipped for performance reasons
        {
          add_item = true;
        }
        else
        {
          add_item = curr_shape.intersects(p_shape);
        }
        if (add_item)
        {
          TreeEntry new_entry = new TreeEntry(curr_object, shape_index);
          p_tree_entries.add(new_entry);
        }
      }
    }
  }

  /**
   * Looks up all entries in the search tree, so that inserting an item with shape p_shape, net
   * number p_net_no, clearance type p_cl_type and layer p_layer would produce a clearance
   * violation, and puts them into the set p_obstacle_entries. The elements in p_obstacle_entries
   * are of type TreeEntry. if p_layer < 0, the layer is ignored. Used only internally, because the
   * clearance compensation is not taken into account.
   */
  void overlapping_tree_entries_with_clearance(ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, int p_cl_type, Collection<TreeEntry> p_obstacle_entries)
  {
    if (p_shape == null)
    {
      return;
    }
    if (p_obstacle_entries == null)
    {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: p_obstacle_entries is null");
      return;
    }
    ClearanceMatrix cl_matrix = board.rules.clearance_matrix;
    RegularTileShape bounds = p_shape.bounding_shape(bounding_directions);
    if (bounds == null)
    {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: p_shape is not bounded");
      bounds = board.get_bounding_box();
    }
    int max_clearance = (int) (1.2 * cl_matrix.max_value(p_cl_type, p_layer));
    // search with the bounds enlarged by the maximum clearance to
    // get all candidates for overlap
    // a factor less than sqr2 has evtl. be added because
    // enlarging is not symmetric.
    RegularTileShape offset_bounds = (RegularTileShape) bounds.offset(max_clearance);
    Collection<Leaf> tmp_list = overlaps(offset_bounds);
    // sort the found items by its clearances tp p_cl_type on layer p_layer
    Set<EntrySortedByClearance> sorted_items = new TreeSet<>();

    for (Leaf curr_leaf : tmp_list)
    {
      Item curr_item = (Item) curr_leaf.object;
      int shape_index = curr_leaf.shape_index_in_object;
      boolean ignore_item = p_layer >= 0 && curr_item.shape_layer(shape_index) != p_layer;
      if (!ignore_item)
      {
        for (int i = 0; i < p_ignore_net_nos.length; ++i)
        {
          if (!curr_item.is_obstacle(p_ignore_net_nos[i]))
          {
            ignore_item = true;
          }
        }
      }
      if (!ignore_item)
      {
        int curr_clearance = cl_matrix.get_value(p_cl_type, curr_item.clearance_class_no(), p_layer, true);
        EntrySortedByClearance sorted_ob = new EntrySortedByClearance(curr_leaf, curr_clearance);
        sorted_items.add(sorted_ob);
      }
    }
    int curr_half_clearance = 0;
    ConvexShape curr_offset_shape = p_shape;
    for (EntrySortedByClearance tmp_entry : sorted_items)
    {
      int tmp_half_clearance = tmp_entry.clearance / 2;
      if (tmp_half_clearance != curr_half_clearance)
      {
        curr_half_clearance = tmp_half_clearance;
        curr_offset_shape = (TileShape) p_shape.enlarge(curr_half_clearance);
      }
      TileShape tmp_shape = tmp_entry.leaf.object.get_tree_shape(this, tmp_entry.leaf.shape_index_in_object);
      // enlarge both item shapes by the half clearance to create
      // symmetry.
      ConvexShape tmp_offset_shape = (ConvexShape) tmp_shape.enlarge(curr_half_clearance);
      if (curr_offset_shape.intersects(tmp_offset_shape))
      {
        p_obstacle_entries.add(new TreeEntry(tmp_entry.leaf.object, tmp_entry.leaf.shape_index_in_object));
      }
    }
  }

  /**
   * Puts all items in the tree overlapping with p_shape on layer p_layer into p_obstacles, if
   * p_obstacles != null. If p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlapping_objects_with_clearance(ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, int p_cl_type, Set<SearchTreeObject> p_obstacles)
  {
    Collection<TreeEntry> tree_entries = new LinkedList<>();
    if (this.is_clearance_compensation_used())
    {
      overlapping_tree_entries(p_shape, p_layer, p_ignore_net_nos, tree_entries);
    }
    else
    {
      overlapping_tree_entries_with_clearance(p_shape, p_layer, p_ignore_net_nos, p_cl_type, tree_entries);
    }
    if (p_obstacles == null)
    {
      return;
    }
    for (TreeEntry curr_entry : tree_entries)
    {
      p_obstacles.add((SearchTreeObject) curr_entry.object);
    }
  }

  /**
   * Returns items, which overlap with p_shape on layer p_layer inclusive clearance.
   * p_clearance_class is the index in the clearance matrix, which describes the required clearance
   * restrictions to other items. The function may also return items, which are nearly overlapping,
   * but do not overlap with exact calculation. If p_layer {@literal <} 0, the layer is ignored.
   */
  public Set<Item> overlapping_items_with_clearance(ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, int p_clearance_class)
  {
    Set<SearchTreeObject> overlaps = new TreeSet<>();

    this.overlapping_objects_with_clearance(p_shape, p_layer, p_ignore_net_nos, p_clearance_class, overlaps);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject curr_object : overlaps)
    {
      if (curr_object instanceof Item)
      {
        result.add((Item) curr_object);
      }
    }
    return result;
  }

  /**
   * Returns all objects of class TreeEntry, which overlap with p_shape on layer p_layer inclusive
   * clearance. p_clearance_class is the index in the clearance matrix, which describes the required
   * clearance restrictions to other items. If p_layer {@literal <} 0, the layer is ignored.
   */
  public Collection<TreeEntry> overlapping_tree_entries_with_clearance(ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, int p_clearance_class)
  {
    Collection<TreeEntry> result = new LinkedList<>();
    if (this.is_clearance_compensation_used())
    {
      this.overlapping_tree_entries(p_shape, p_layer, p_ignore_net_nos, result);
    }
    else
    {
      this.overlapping_tree_entries_with_clearance(p_shape, p_layer, p_ignore_net_nos, p_clearance_class, result);
    }
    return result;
  }

  /**
   * Calculates a new incomplete room with a maximal TileShape contained in the shape of p_room,
   * which may overlap only with items of the input net on the input layer.
   * p_room.get_contained_shape() will be contained in the shape of the result room. If that is not
   * possible, several rooms are returned with shapes, which intersect with
   * p_room.get_contained_shape(). The result room is not yet complete, because its doors are not
   * yet calculated. If p_ignore_shape != null, objects of type CompleteFreeSpaceExpansionRoom,
   * whose intersection with the shape of p_room is contained in p_ignore_shape, are ignored.
   */
  public Collection<IncompleteFreeSpaceExpansionRoom> complete_shape(IncompleteFreeSpaceExpansionRoom p_room, int p_net_no, SearchTreeObject p_ignore_object, TileShape p_ignore_shape)
  {
    if (p_room.get_contained_shape() == null)
    {
      FRLogger.warn("ShapeSearchTree.complete_shape: p_shape_to_be_contained != null expected");
      return new LinkedList<>();
    }
    if (this.root == null)
    {
      return new LinkedList<>();
    }
    TileShape start_shape = board.get_bounding_box();
    if (p_room.get_shape() != null)
    {
      start_shape = start_shape.intersection(p_room.get_shape());
    }
    RegularTileShape bounding_shape = start_shape.bounding_shape(this.bounding_directions);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    if (start_shape.dimension() == 2)
    {
      IncompleteFreeSpaceExpansionRoom new_room = new IncompleteFreeSpaceExpansionRoom(start_shape, p_room.get_layer(), p_room.get_contained_shape());
      result.add(new_room);
    }
    this.node_stack.reset();
    this.node_stack.push(this.root);
    TreeNode curr_node;
    int room_layer = p_room.get_layer();

    for (; ; )
    {
      curr_node = this.node_stack.pop();
      if (curr_node == null)
      {
        break;
      }
      if (curr_node.bounding_shape.intersects(bounding_shape))
      {
        if (curr_node instanceof Leaf curr_leaf)
        {
          SearchTreeObject curr_object = (SearchTreeObject) curr_leaf.object;
          int shape_index = curr_leaf.shape_index_in_object;
          if (curr_object.is_trace_obstacle(p_net_no) && curr_object.shape_layer(shape_index) == room_layer && curr_object != p_ignore_object)
          {

            TileShape curr_object_shape = curr_object.get_tree_shape(this, shape_index);
            Collection<IncompleteFreeSpaceExpansionRoom> new_result = new LinkedList<>();
            RegularTileShape new_bounding_shape = IntOctagon.EMPTY;

            for (IncompleteFreeSpaceExpansionRoom curr_incomplete_room : result)
            {
              boolean something_changed = false;
              TileShape intersection = curr_incomplete_room
                  .get_shape()
                  .intersection(curr_object_shape);
              if (intersection.dimension() == 2)
              {
                boolean ignore_expansion_room = curr_object instanceof CompleteFreeSpaceExpansionRoom && p_ignore_shape != null && p_ignore_shape.contains(intersection);
                // cannot happen in free angle routing, because then expansion_rooms
                // may not overlap. Therefore, that can be removed as soon as special
                // function for 45-degree routing is used.
                if (!ignore_expansion_room)
                {
                  something_changed = true;
                  new_result.addAll(restrain_shape(curr_incomplete_room, curr_object_shape));
                  for (IncompleteFreeSpaceExpansionRoom tmp_room : new_result)
                  {
                    new_bounding_shape = new_bounding_shape.union(tmp_room
                        .get_shape()
                        .bounding_shape(this.bounding_directions));
                  }
                }
              }
              if (!something_changed)
              {
                new_result.add(curr_incomplete_room);
                new_bounding_shape = new_bounding_shape.union(curr_incomplete_room
                    .get_shape()
                    .bounding_shape(this.bounding_directions));
              }
            }
            result = new_result;
            bounding_shape = new_bounding_shape;
          }
        }
        else
        {
          this.node_stack.push(((InnerNode) curr_node).first_child);
          this.node_stack.push(((InnerNode) curr_node).second_child);
        }
      }
    }
    result = divide_large_room(result, board.get_bounding_box());
    return result;
  }

  /**
   * Restrains the shape of p_incomplete_room to a TileShape, which does not intersect with the
   * interior of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be contained in the
   * shape of the result room. If that is not possible, several rooms are returned with shapes,
   * which intersect with p_incomplete_room.get_contained_shape().
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrain_shape(IncompleteFreeSpaceExpansionRoom p_incomplete_room, TileShape p_obstacle_shape)
  {
    // Search the edge line of p_obstacle_shape, so that p_shape_to_be_contained
    // are on the right side of this line, and that the line segment
    // intersects with the interior of p_shape.
    // If there are more than 1 such lines take the line which is
    // furthest away from p_points_to_be_con.tained
    // Then intersect p_shape with the halfplane defined by the
    // opposite of this line.
    Simplex obstacle_simplex = p_obstacle_shape.to_Simplex(); // otherwise border_lines of length 0 for octagons may not be handled
    // correctly
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    TileShape room_shape = p_incomplete_room.get_shape();
    int layer = p_incomplete_room.get_layer();

    TileShape shape_to_be_contained = p_incomplete_room.get_contained_shape();
    if (shape_to_be_contained != null)
    {
      shape_to_be_contained = shape_to_be_contained.to_Simplex(); // There may be a performance problem, if a point shape is represented
      // as an octagon
    }
    if (shape_to_be_contained == null || shape_to_be_contained.is_empty())
    {
      FRLogger.trace("ShapeSearchTree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    Line cut_line = null;
    double cut_line_distance = -1;
    for (int i = 0; i < obstacle_simplex.border_line_count(); ++i)
    {
      LineSegment curr_line_segment = new LineSegment(obstacle_simplex, i);
      if (room_shape.is_intersected_interior_by(curr_line_segment))
      {
        // otherwise curr_object may not touch the intersection
        // of p_shape with the half_plane defined by the cut_line.
        // That may lead to problems when creating the ExpansionRooms.
        Line curr_line = obstacle_simplex.border_line(i);

        double curr_min_distance = shape_to_be_contained.distance_to_the_left(curr_line);

        if (curr_min_distance > cut_line_distance)
        {
          cut_line_distance = curr_min_distance;
          cut_line = curr_line.opposite();
        }
      }
    }
    if (cut_line != null)
    {
      TileShape result_piece = TileShape.get_instance(cut_line);
      if (room_shape != null)
      {
        result_piece = room_shape.intersection(result_piece);
      }
      if (result_piece.dimension() >= 2)
      {
        result.add(new IncompleteFreeSpaceExpansionRoom(result_piece, layer, shape_to_be_contained));
      }
    }
    else
    {
      // There is no cut line, so that all p_shape_to_be_contained is
      // completely on the right side of that line. Search a cut line, so that
      // at least part of p_shape_to_be_contained is on the right side.
      if (shape_to_be_contained.dimension() < 1)
      {
        // There is already a completed expansion room around p_shape_to_be_contained.
        return result;
      }

      for (int i = 0; i < obstacle_simplex.border_line_count(); ++i)
      {
        LineSegment curr_line_segment = new LineSegment(obstacle_simplex, i);
        if (room_shape.is_intersected_interior_by(curr_line_segment))
        {
          Line curr_line = obstacle_simplex.border_line(i);
          if (shape_to_be_contained.side_of(curr_line) == Side.COLLINEAR)
          {
            // curr_line intersects with the interior of p_shape_to_be_contained
            cut_line = curr_line.opposite();
            break;
          }
        }
      }

      if (cut_line == null)
      {
        // cut line not found, parts or the whole of p_shape may be already
        // occupied from somewhere else.
        return result;
      }
      // Calculate the new shape to be contained in the result shape.
      TileShape cut_half_plane = TileShape.get_instance(cut_line);
      TileShape new_shape_to_be_contained = shape_to_be_contained.intersection(cut_half_plane);

      TileShape result_piece;
      if (room_shape == null)
      {
        result_piece = cut_half_plane;
      }
      else
      {
        result_piece = room_shape.intersection(cut_half_plane);
      }
      if (result_piece.dimension() >= 2)
      {
        result.add(new IncompleteFreeSpaceExpansionRoom(result_piece, layer, new_shape_to_be_contained));
      }
      TileShape opposite_half_plane = TileShape.get_instance(cut_line.opposite());
      TileShape rest_piece;
      if (room_shape == null)
      {
        rest_piece = opposite_half_plane;
      }
      else
      {
        rest_piece = room_shape.intersection(opposite_half_plane);
      }
      if (rest_piece.dimension() >= 2)
      {
        TileShape rest_shape_to_be_contained = shape_to_be_contained.intersection(opposite_half_plane);
        IncompleteFreeSpaceExpansionRoom rest_incomplete_room = new IncompleteFreeSpaceExpansionRoom(rest_piece, layer, rest_shape_to_be_contained);
        result.addAll(restrain_shape(rest_incomplete_room, obstacle_simplex));
      }
    }
    return result;
  }

  /**
   * Reduces the first or last shape of p_trace at a tie pin, so that the autorouter algorithm can
   * find a connection for a different net.
   */
  public void reduce_trace_shape_at_tie_pin(Pin p_tie_pin, PolylineTrace p_trace)
  {
    TileShape pin_shape = p_tie_pin.get_tree_shape_on_layer(this, p_trace.get_layer());
    FloatPoint compare_corner;
    int trace_shape_no;
    if (p_trace
        .first_corner()
        .equals(p_tie_pin.get_center()))
    {
      trace_shape_no = 0;
      compare_corner = p_trace
          .polyline()
          .corner_approx(1);

    }
    else if (p_trace
        .last_corner()
        .equals(p_tie_pin.get_center()))
    {
      trace_shape_no = p_trace.corner_count() - 2;
      compare_corner = p_trace
          .polyline()
          .corner_approx(p_trace.corner_count() - 2);
    }
    else
    {
      return;
    }
    TileShape trace_shape = p_trace.get_tree_shape(this, trace_shape_no);
    TileShape intersection = trace_shape.intersection(pin_shape);
    if (intersection.dimension() < 2)
    {
      return;
    }
    TileShape[] shape_pieces = trace_shape.cutout(pin_shape);
    TileShape new_trace_shape = Simplex.EMPTY;
    for (int i = 0; i < shape_pieces.length; ++i)
    {
      if (shape_pieces[i].dimension() == 2)
      {
        if (new_trace_shape == Simplex.EMPTY || shape_pieces[i].contains(compare_corner))
        {
          new_trace_shape = shape_pieces[i];
        }
      }
    }
    change_item_shape(p_trace, trace_shape_no, new_trace_shape);
  }

  /**
   * Changes the shape with index p_shape_no of this item to p_new_shape and updates the entry in
   * the tree.
   */
  void change_item_shape(Item p_item, int p_shape_no, TileShape p_new_shape)
  {
    Leaf[] old_entries = p_item.get_search_tree_entries(this);
    Leaf[] new_leaf_arr = new Leaf[old_entries.length];
    TileShape[] new_precalculated_tree_shapes = new TileShape[old_entries.length];
    remove_leaf(old_entries[p_shape_no]);
    for (int i = 0; i < new_precalculated_tree_shapes.length; ++i)
    {
      if (i == p_shape_no)
      {
        new_precalculated_tree_shapes[i] = p_new_shape;

      }
      else
      {
        new_precalculated_tree_shapes[i] = p_item.get_tree_shape(this, i);
        new_leaf_arr[i] = old_entries[i];
      }
    }
    p_item.set_precalculated_tree_shapes(new_precalculated_tree_shapes, this);
    new_leaf_arr[p_shape_no] = insert(p_item, p_shape_no);
    p_item.set_search_tree_entries(new_leaf_arr, this);
  }

  TileShape[] calculate_tree_shapes(DrillItem p_drill_item)
  {
    if (this.board == null)
    {
      return new TileShape[0];
    }
    TileShape[] result = new TileShape[p_drill_item.tile_shape_count()];
    for (int i = 0; i < result.length; ++i)
    {
      Shape curr_shape = p_drill_item.get_shape(i);
      if (curr_shape == null)
      {
        result[i] = null;
      }
      else
      {
        TileShape curr_tile_shape;
        if (this.board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE)
        {
          curr_tile_shape = curr_shape.bounding_box();
        }
        else if (this.board.rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
        {
          curr_tile_shape = curr_shape.bounding_octagon();
        }
        else
        {
          curr_tile_shape = curr_shape.bounding_tile();
        }
        int offset_width = this.clearance_compensation_value(p_drill_item.clearance_class_no(), p_drill_item.shape_layer(i));
        if (curr_tile_shape == null)
        {
          FRLogger.warn("ShapeSearchTree.calculate_tree_shapes: shape is null");
        }
        else
        {
          curr_tile_shape = (TileShape) curr_tile_shape.enlarge(offset_width);
        }
        result[i] = curr_tile_shape;
      }
    }
    return result;
  }

  TileShape[] calculate_tree_shapes(ObstacleArea p_obstacle_area)
  {
    if (this.board == null)
    {
      return new TileShape[0];
    }
    TileShape[] convex_shapes = p_obstacle_area.split_to_convex();
    if (convex_shapes == null)
    {
      return new TileShape[0];
    }
    double max_tree_shape_width = 50000;
    if (this.board.communication.host_cad_exists())
    {
      max_tree_shape_width = Math.min(500 * this.board.communication.get_resolution(Unit.MIL), max_tree_shape_width);
      // Problem with low resolution on Kicad.
      // Called only for designs from host cad systems because otherwise the old sample.dsn gets to
      // many tree shapes.
    }

    Collection<TileShape> tree_shape_list = new LinkedList<>();
    for (int i = 0; i < convex_shapes.length; ++i)
    {
      TileShape curr_convex_shape = convex_shapes[i];

      int offset_width = this.clearance_compensation_value(p_obstacle_area.clearance_class_no(), p_obstacle_area.get_layer());
      curr_convex_shape = (TileShape) curr_convex_shape.enlarge(offset_width);
      TileShape[] curr_tree_shapes = curr_convex_shape.divide_into_sections(max_tree_shape_width);
      tree_shape_list.addAll(Arrays.asList(curr_tree_shapes));
    }
    TileShape[] result = new TileShape[tree_shape_list.size()];
    Iterator<TileShape> it = tree_shape_list.iterator();
    for (int i = 0; i < result.length; ++i)
    {
      result[i] = it.next();
    }
    return result;
  }

  TileShape[] calculate_tree_shapes(BoardOutline p_board_outline)
  {
    if (this.board == null)
    {
      return new TileShape[0];
    }
    TileShape[] result;
    if (p_board_outline.keepout_outside_outline_generated())
    {
      TileShape[] convex_shapes = p_board_outline
          .get_keepout_area()
          .split_to_convex();
      if (convex_shapes == null)
      {
        return new TileShape[0];
      }
      Collection<TileShape> tree_shape_list = new LinkedList<>();
      for (int layer_no = 0; layer_no < this.board.layer_structure.arr.length; ++layer_no)
      {
        for (int i = 0; i < convex_shapes.length; ++i)
        {
          TileShape curr_convex_shape = convex_shapes[i];
          int offset_width = this.clearance_compensation_value(p_board_outline.clearance_class_no(), layer_no);
          curr_convex_shape = (TileShape) curr_convex_shape.enlarge(offset_width);
          tree_shape_list.add(curr_convex_shape);
        }
      }
      result = new TileShape[tree_shape_list.size()];
      Iterator<TileShape> it = tree_shape_list.iterator();
      for (int i = 0; i < result.length; ++i)
      {
        result[i] = it.next();
      }
    }
    else
    {
      // Only the line shapes of the outline are inserted as obstacles into the tree.
      result = new TileShape[p_board_outline.line_count() * this.board.layer_structure.arr.length];
      int half_width = p_board_outline.get_half_width();
      Line[] curr_line_arr = new Line[3];
      int curr_no = 0;
      for (int layer_no = 0; layer_no < this.board.layer_structure.arr.length; ++layer_no)
      {
        for (int shape_no = 0; shape_no < p_board_outline.shape_count(); ++shape_no)
        {
          PolylineShape curr_outline_shape = p_board_outline.get_shape(shape_no);
          int border_line_count = curr_outline_shape.border_line_count();
          curr_line_arr[0] = curr_outline_shape.border_line(border_line_count - 1);
          for (int i = 0; i < border_line_count; ++i)
          {
            curr_line_arr[1] = curr_outline_shape.border_line(i);
            curr_line_arr[2] = curr_outline_shape.border_line((i + 1) % border_line_count);
            Polyline tmp_polyline = new Polyline(curr_line_arr);
            int cmp_value = this.clearance_compensation_value(p_board_outline.clearance_class_no(), layer_no);
            result[curr_no] = tmp_polyline.offset_shape(half_width + cmp_value, 0);
            ++curr_no;
            curr_line_arr[0] = curr_line_arr[1];
          }
        }
      }
    }
    return result;
  }

  /**
   * Used for creating the shapes of a polyline_trace for this tree. Overwritten in derived classes.
   */
  TileShape offset_shape(Polyline p_polyline, int p_half_width, int p_no)
  {
    return p_polyline.offset_shape(p_half_width, p_no);
  }

  /**
   * Used for creating the shapes of a polyline_trace for this tree. Overwritten in derived classes.
   */
  public TileShape[] offset_shapes(Polyline p_polyline, int p_half_width, int p_from_no, int p_to_no)
  {
    return p_polyline.offset_shapes(p_half_width, p_from_no, p_to_no);
  }

  TileShape[] calculate_tree_shapes(PolylineTrace p_trace)
  {
    if (this.board == null)
    {
      return new TileShape[0];
    }
    int offset_width = p_trace.get_half_width() + this.clearance_compensation_value(p_trace.clearance_class_no(), p_trace.get_layer());
    TileShape[] result = new TileShape[p_trace.tile_shape_count()];
    for (int i = 0; i < result.length; ++i)
    {
      result[i] = this.offset_shape(p_trace.polyline(), offset_width, i);
    }
    return result;
  }

  /**
   * Makes sure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm gets problems with
   * vias.
   */
  protected Collection<IncompleteFreeSpaceExpansionRoom> divide_large_room(Collection<IncompleteFreeSpaceExpansionRoom> p_room_list, IntBox p_board_bounding_box)
  {
    if (p_room_list.size() != 1)
    {
      return p_room_list;
    }
    IncompleteFreeSpaceExpansionRoom curr_room = p_room_list
        .iterator()
        .next();
    IntBox room_bounding_box = curr_room
        .get_shape()
        .bounding_box();
    if (2 * room_bounding_box.height() <= p_board_bounding_box.height() || 2 * room_bounding_box.width() <= p_board_bounding_box.width())
    {
      return p_room_list;
    }
    double max_section_width = 0.5 * Math.max(p_board_bounding_box.height(), p_board_bounding_box.width());
    TileShape[] section_arr = curr_room
        .get_shape()
        .divide_into_sections(max_section_width);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    for (TileShape curr_section : section_arr)
    {
      TileShape curr_shape_to_be_contained = curr_section.intersection(curr_room.get_contained_shape());
      IncompleteFreeSpaceExpansionRoom curr_section_room = new IncompleteFreeSpaceExpansionRoom(curr_section, curr_room.get_layer(), curr_shape_to_be_contained);
      result.add(curr_section_room);
    }
    return result;
  }

  boolean validate_entries(Item p_item)
  {
    Leaf[] curr_tree_entries = p_item.get_search_tree_entries(this);
    for (int i = 0; i < curr_tree_entries.length; ++i)
    {
      Leaf curr_leaf = curr_tree_entries[i];
      if (curr_leaf.shape_index_in_object != i)
      {
        FRLogger.warn("tree entry inconsistent for Item");
        return false;
      }
    }
    return true;
  }

  /**
   * created for sorting Items according to their clearance to p_cl_type on layer p_layer
   */
  private static class EntrySortedByClearance implements Comparable<EntrySortedByClearance>
  {

    private final int entry_id_no;
    Leaf leaf;
    int clearance;

    EntrySortedByClearance(Leaf p_leaf, int p_clearance)
    {
      leaf = p_leaf;
      clearance = p_clearance;
      if (last_generated_id_no == Integer.MAX_VALUE)
      {
        last_generated_id_no = 0;
      }
      else
      {
        ++last_generated_id_no;
      }
      entry_id_no = last_generated_id_no;
    }

    @Override
    public int compareTo(EntrySortedByClearance p_other)
    {
      if (clearance != p_other.clearance)
      {
        return Signum.as_int(clearance - p_other.clearance);
      }
      return entry_id_no - p_other.entry_id_no;
    }
  }
}
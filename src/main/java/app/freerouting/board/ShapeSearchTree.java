package app.freerouting.board;

import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.datastructures.ArrayStack;
import app.freerouting.datastructures.MinAreaTree;
import app.freerouting.datastructures.Signum;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.RegularTileShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.ShapeBoundingDirections;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Elementary geometric search functions making direct use of the MinAreaTree in the package
 * datastructures.
 */
public class ShapeSearchTree extends MinAreaTree {

  private static final int DRILL_HOLE_CLEARANCE_MARGIN = 10;

  /** used in objects of class EntrySortedByClearance */
  private static int lastGeneratedIdNo;

  /**
   * The clearance class number for which the shapes of this tree is compensated. If
   * compensatedClearanceClassNo = 0, the shapes are not compensated.
   */
  public final int compensatedClearanceClassNo;

  public final String key;
  protected final BasicBoard board;

  /**
   * Creates a new ShapeSearchTree. p_compensated_clearance_class_no is the clearance class number
   * for which the shapes of this tree is compensated. If p_compensated_clearance_class_no = 0, the
   * shapes are not compensated.
   */
  ShapeSearchTree(
      ShapeBoundingDirections p_directions,
      BasicBoard p_board,
      int p_compensated_clearance_class_no) {
    super(p_directions);
    this.compensatedClearanceClassNo = p_compensated_clearance_class_no;
    board = p_board;
    key = getKey(this, p_directions, compensatedClearanceClassNo);
  }

  public static String getKey(
      ShapeSearchTree searchTree, ShapeBoundingDirections directions, int clearanceClass) {
    return searchTree.getClass().getSimpleName()
        + "_"
        + directions.getClass().getSimpleName().replaceAll("BoundingDirections", "")
        + "_cc"
        + clearanceClass;
  }

  @Override
  public String toString() {
    return key;
  }

  /** Returns, if for the shapes stored in this tree clearance compensation is used. */
  public boolean is_clearance_compensation_used() {
    return this.compensatedClearanceClassNo > 0;
  }

  /**
   * Return the clearance compensation value of p_clearance_class_no to the clearance compensation
   * class of this search tree with on layer p_layer. Returns 0, if no clearance compensation is
   * used for this tree.
   */
  public int clearance_compensation_value(int p_clearance_class_no, int p_layer) {
    if (p_clearance_class_no <= 0) {
      return 0;
    }
    int result =
        board.rules.clearanceMatrix.get_value(
                p_clearance_class_no, this.compensatedClearanceClassNo, p_layer, false)
            - board.rules.clearanceMatrix.clearance_compensation_value(
                this.compensatedClearanceClassNo, p_layer);
    return Math.max(result, 0);
  }

  /**
   * Changes the tree entries from p_keep_at_start_count + 1 to newShapeCount - 1 - keepAtEndCount
   * to p_changed_entries. Special implementation for change_trace for performance reasons
   */
  void change_entries(
      PolylineTrace p_obj,
      Polyline p_new_polyline,
      int p_keep_at_start_count,
      int p_keep_at_end_count) {
    // calculate the shapes of p_new_polyline from keepAtStartCount to
    // newShapeCount - keepAtEndCount - 1;
    int compensatedHalfWidth =
        p_obj.get_half_width()
            + this.clearance_compensation_value(p_obj.clearance_class_no(), p_obj.get_layer());
    TileShape[] changedShapes =
        this.offset_shapes(
            p_new_polyline,
            compensatedHalfWidth,
            p_keep_at_start_count,
            p_new_polyline.arr.length - 1 - p_keep_at_end_count);
    int oldShapeCount = p_obj.tree_shape_count(this);
    int newShapeCount = changedShapes.length + p_keep_at_start_count + p_keep_at_end_count;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    Leaf[] oldEntries = p_obj.get_search_tree_entries(this);
    for (int i = 0; i < p_keep_at_start_count; i++) {
      newLeafArr[i] = oldEntries[i];
      newPrecalculatedTreeShapes[i] = p_obj.get_tree_shape(this, i);
    }
    for (int i = p_keep_at_start_count; i < oldShapeCount - p_keep_at_end_count; i++) {
      remove_leaf(oldEntries[i]);
    }
    for (int i = 0; i < p_keep_at_end_count; i++) {
      int newIndex = newShapeCount - p_keep_at_end_count + i;
      int oldIndex = oldShapeCount - p_keep_at_end_count + i;

      newLeafArr[newIndex] = oldEntries[oldIndex];
      newLeafArr[newIndex].shapeIndexInObject = newIndex;
      newPrecalculatedTreeShapes[newIndex] = p_obj.get_tree_shape(this, oldIndex);
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    System.arraycopy(
        changedShapes, 0, newPrecalculatedTreeShapes, p_keep_at_start_count, changedShapes.length);
    p_obj.set_precalculated_tree_shapes(newPrecalculatedTreeShapes, this);

    for (int i = p_keep_at_start_count; i < newShapeCount - p_keep_at_end_count; i++) {
      newLeafArr[i] = insert(p_obj, i);
    }
    p_obj.set_search_tree_entries(newLeafArr, this);
  }

  /**
   * Merges the tree entries from p_from_trace in front of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void merge_entries_in_front(
      PolylineTrace p_from_trace,
      PolylineTrace p_to_trace,
      Polyline p_joined_polyline,
      int p_from_entry_no,
      int p_to_entry_no) {
    int compensatedHalfWidth =
        p_to_trace.get_half_width()
            + this.clearance_compensation_value(
                p_to_trace.clearance_class_no(), p_to_trace.get_layer());
    TileShape[] linkShapes =
        this.offset_shapes(p_joined_polyline, compensatedHalfWidth, p_from_entry_no, p_to_entry_no);
    boolean changeOrder = p_from_trace.first_corner().equals(p_to_trace.first_corner());
    // remove the last or first tree entry from p_from_trace and the
    // first tree entry from p_to_trace, because they will be replaced by
    // the new link entries.
    int fromShapeCountMinus1 = p_from_trace.tile_shape_count() - 1;
    int removeNo;
    if (changeOrder) {
      removeNo = 0;
    } else {
      removeNo = fromShapeCountMinus1;
    }
    Leaf[] fromTraceEntries = p_from_trace.get_search_tree_entries(this);
    Leaf[] toTraceEntries = p_to_trace.get_search_tree_entries(this);
    remove_leaf(fromTraceEntries[removeNo]);
    remove_leaf(toTraceEntries[0]);
    int newShapeCount = fromTraceEntries.length + linkShapes.length + toTraceEntries.length - 2;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    int oldToShapeCount = toTraceEntries.length;
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    // transfer the tree entries except the last or first from p_from_trace to
    // p_to_trace
    for (int i = 0; i < fromShapeCountMinus1; i++) {
      int fromNo;
      if (changeOrder) {
        fromNo = fromShapeCountMinus1 - i;
      } else {
        fromNo = i;
      }
      newPrecalculatedTreeShapes[i] = p_from_trace.get_tree_shape(this, fromNo);
      newLeafArr[i] = fromTraceEntries[fromNo];
      newLeafArr[i].object = p_to_trace;
      newLeafArr[i].shapeIndexInObject = i;
    }
    for (int i = 1; i < oldToShapeCount; i++) {
      int currInd = fromShapeCountMinus1 + linkShapes.length + i - 1;
      newPrecalculatedTreeShapes[currInd] = p_to_trace.get_tree_shape(this, i);
      newLeafArr[currInd] = toTraceEntries[i];
      newLeafArr[currInd].shapeIndexInObject = currInd;
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = fromShapeCountMinus1 + i;
      newPrecalculatedTreeShapes[currInd] = linkShapes[i];
    }
    p_to_trace.set_precalculated_tree_shapes(newPrecalculatedTreeShapes, this);

    // create the new link entries
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = fromShapeCountMinus1 + i;
      newLeafArr[currInd] = insert(p_to_trace, currInd);
    }

    p_to_trace.set_search_tree_entries(newLeafArr, this);
  }

  /**
   * Merges the tree entries from p_from_trace to the end of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void merge_entries_at_end(
      PolylineTrace p_from_trace,
      PolylineTrace p_to_trace,
      Polyline p_joined_polyline,
      int p_from_entry_no,
      int p_to_entry_no) {
    int compensatedHalfWidth =
        p_to_trace.get_half_width()
            + this.clearance_compensation_value(
                p_to_trace.clearance_class_no(), p_to_trace.get_layer());
    TileShape[] linkShapes =
        this.offset_shapes(p_joined_polyline, compensatedHalfWidth, p_from_entry_no, p_to_entry_no);
    boolean changeOrder = p_from_trace.last_corner().equals(p_to_trace.last_corner());
    Leaf[] fromTraceEntries = p_from_trace.get_search_tree_entries(this);
    Leaf[] toTraceEntries = p_to_trace.get_search_tree_entries(this);
    // remove the last or first tree entry from p_from_trace and the
    // last tree entry from p_to_trace, because they will be replaced by
    // the new link entries.
    int toShapeCountMinus1 = p_to_trace.tile_shape_count() - 1;
    remove_leaf(toTraceEntries[toShapeCountMinus1]);
    int removeNo;
    if (changeOrder) {
      removeNo = p_from_trace.tile_shape_count() - 1;
    } else {
      removeNo = 0;
    }
    remove_leaf(fromTraceEntries[removeNo]);
    int newShapeCount = fromTraceEntries.length + linkShapes.length + toTraceEntries.length - 2;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    // transfer the tree entries except the last from the old shapes
    // of p_to_trace to the new shapes of p_to_trace
    for (int i = 0; i < toShapeCountMinus1; i++) {
      newPrecalculatedTreeShapes[i] = p_to_trace.get_tree_shape(this, i);
      newLeafArr[i] = toTraceEntries[i];
    }

    for (int i = 1; i < fromTraceEntries.length; i++) {
      int currInd = toShapeCountMinus1 + linkShapes.length + i - 1;
      int fromNo;
      if (changeOrder) {
        fromNo = fromTraceEntries.length - i - 1;
      } else {
        fromNo = i;
      }
      newPrecalculatedTreeShapes[currInd] = p_from_trace.get_tree_shape(this, fromNo);
      newLeafArr[currInd] = fromTraceEntries[fromNo];
      newLeafArr[currInd].object = p_to_trace;
      newLeafArr[currInd].shapeIndexInObject = currInd;
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = toShapeCountMinus1 + i;
      newPrecalculatedTreeShapes[currInd] = linkShapes[i];
    }
    p_to_trace.set_precalculated_tree_shapes(newPrecalculatedTreeShapes, this);

    // create the new link entries
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = toShapeCountMinus1 + i;
      newLeafArr[currInd] = insert(p_to_trace, currInd);
    }
    p_to_trace.set_search_tree_entries(newLeafArr, this);
  }

  /**
   * Transfers tree entries from p_from_trace to p_start and p_end_piece after a middle piece was
   * cut out. Special implementation for ShapeTraceEntries.fast_cutout_trace for performance
   * reasons.
   */
  void reuse_entries_after_cutout(
      PolylineTrace p_from_trace, PolylineTrace p_start_piece, PolylineTrace p_end_piece) {
    Leaf[] startPieceLeafArr = new Leaf[p_start_piece.polyline().arr.length - 2];
    Leaf[] fromTraceEntries = p_from_trace.get_search_tree_entries(this);
    // transfer the entries at the start of p_from_trace to p_start_piece.
    for (int i = 0; i < startPieceLeafArr.length - 1; i++) {
      startPieceLeafArr[i] = fromTraceEntries[i];
      startPieceLeafArr[i].object = p_start_piece;
      startPieceLeafArr[i].shapeIndexInObject = i;
      fromTraceEntries[i] = null;
    }
    startPieceLeafArr[startPieceLeafArr.length - 1] =
        insert(p_start_piece, startPieceLeafArr.length - 1);

    // create the last tree entry of the start piece.

    Leaf[] endPieceLeafArr = new Leaf[p_end_piece.polyline().arr.length - 2];

    // create the first tree entry of the end piece.
    endPieceLeafArr[0] = insert(p_end_piece, 0);

    for (int i = 1; i < endPieceLeafArr.length; i++) {
      int fromIndex = fromTraceEntries.length - endPieceLeafArr.length + i;
      endPieceLeafArr[i] = fromTraceEntries[fromIndex];
      endPieceLeafArr[i].object = p_end_piece;
      endPieceLeafArr[i].shapeIndexInObject = i;
      fromTraceEntries[fromIndex] = null;
    }

    p_start_piece.set_search_tree_entries(startPieceLeafArr, this);
    p_end_piece.set_search_tree_entries(endPieceLeafArr, this);
  }

  /**
   * Puts all items in the tree overlapping with p_shape on layer p_layer into p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlapping_objects(
      ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, Set<SearchTreeObject> p_obstacles) {
    Collection<TreeEntry> treeEntries = new LinkedList<>();
    overlapping_tree_entries(p_shape, p_layer, p_ignore_net_nos, treeEntries);
    if (p_obstacles != null) {
      for (TreeEntry currEntry : treeEntries) {
        p_obstacles.add((SearchTreeObject) currEntry.object);
      }
    }
  }

  /**
   * Returns all SearchTreeObjects on layer p_layer, which overlap with p_shape. If p_layer
   * {@literal <} 0, the layer is ignored
   */
  public Set<SearchTreeObject> overlapping_objects(ConvexShape p_shape, int p_layer) {
    Set<SearchTreeObject> result = new TreeSet<>();
    this.overlapping_objects(p_shape, p_layer, new int[0], result);
    return result;
  }

  /**
   * Puts all tree entries overlapping with p_shape on layer p_layer into the list p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlapping_tree_entries(
      ConvexShape p_shape, int p_layer, Collection<TreeEntry> p_tree_entries) {
    overlapping_tree_entries(p_shape, p_layer, new int[0], p_tree_entries);
  }

  /**
   * Puts all tree entries overlapping with p_shape on layer p_layer into the list p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored. treeEntries with object containing a net number
   * of p_ignore_net_nos are ignored.
   */
  public void overlapping_tree_entries(
      ConvexShape p_shape,
      int p_layer,
      int[] p_ignore_net_nos,
      Collection<TreeEntry> p_tree_entries) {
    if (p_shape == null) {
      return;
    }
    if (p_tree_entries == null) {
      FRLogger.warn("ShapeSearchTree.overlaps: p_obstacle_entries is null");
      return;
    }
    RegularTileShape bounds = p_shape.bounding_shape(boundingDirections);
    if (bounds == null) {
      FRLogger.warn("ShapeSearchTree.overlaps: p_shape not bounded");
      return;
    }
    Collection<Leaf> tmpList = this.overlaps(bounds);
    boolean is45Degree = p_shape instanceof IntOctagon;

    for (Leaf currLeaf : tmpList) {
      SearchTreeObject currObject = (SearchTreeObject) currLeaf.object;
      int shapeIndex = currLeaf.shapeIndexInObject;
      boolean ignoreObject = p_layer >= 0 && currObject.shape_layer(shapeIndex) != p_layer;
      if (!ignoreObject) {
        for (int i = 0; i < p_ignore_net_nos.length; i++) {
          if (!currObject.is_obstacle(p_ignore_net_nos[i])) {
            ignoreObject = true;
          }
        }
      }
      if (!ignoreObject) {
        TileShape currShape = currObject.get_tree_shape(this, currLeaf.shapeIndexInObject);
        boolean addItem;
        if (is45Degree && currShape instanceof IntOctagon)
        // in this case the check for intersection is redundant and
        // therefore skipped for performance reasons
        {
          addItem = true;
        } else {
          addItem = currShape.intersects(p_shape);
        }
        if (addItem) {
          TreeEntry newEntry = new TreeEntry(currObject, shapeIndex);
          p_tree_entries.add(newEntry);
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
  void overlapping_tree_entries_with_clearance(
      ConvexShape p_shape,
      int p_layer,
      int[] p_ignore_net_nos,
      int p_cl_type,
      Collection<TreeEntry> p_obstacle_entries) {
    if (p_shape == null) {
      return;
    }
    if (p_obstacle_entries == null) {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: p_obstacle_entries is null");
      return;
    }
    ClearanceMatrix clMatrix = board.rules.clearanceMatrix;
    RegularTileShape bounds = p_shape.bounding_shape(boundingDirections);
    if (bounds == null) {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: p_shape is not bounded");
      bounds = board.get_bounding_box();
    }
    int maxClearance = (int) (1.2 * clMatrix.max_value(p_cl_type, p_layer));
    // search with the bounds enlarged by the maximum clearance to
    // get all candidates for overlap
    // a factor less than sqr2 has evtl. be added because
    // enlarging is not symmetric.
    RegularTileShape offsetBounds = (RegularTileShape) bounds.offset(maxClearance);
    Collection<Leaf> tmpList = overlaps(offsetBounds);
    // sort the found items by its clearances tp p_cl_type on layer p_layer
    Set<EntrySortedByClearance> sortedItems = new TreeSet<>();

    for (Leaf currLeaf : tmpList) {
      Item currItem = (Item) currLeaf.object;
      int shapeIndex = currLeaf.shapeIndexInObject;
      boolean ignoreItem = p_layer >= 0 && currItem.shape_layer(shapeIndex) != p_layer;
      if (!ignoreItem) {
        for (int i = 0; i < p_ignore_net_nos.length; i++) {
          if (!currItem.is_obstacle(p_ignore_net_nos[i])) {
            ignoreItem = true;
          }
        }
      }
      if (!ignoreItem) {
        int currClearance =
            clMatrix.get_value(p_cl_type, currItem.clearance_class_no(), p_layer, true);
        EntrySortedByClearance sortedOb = new EntrySortedByClearance(currLeaf, currClearance);
        sortedItems.add(sortedOb);
      }
    }
    int currHalfClearance = 0;
    ConvexShape currOffsetShape = p_shape;
    for (EntrySortedByClearance tmp_entry : sortedItems) {
      int tmpHalfClearance = tmp_entry.clearance / 2;
      if (tmpHalfClearance != currHalfClearance) {
        currHalfClearance = tmpHalfClearance;
        currOffsetShape = (TileShape) p_shape.enlarge(currHalfClearance);
      }
      TileShape tmpShape =
          tmp_entry.leaf.object.get_tree_shape(this, tmp_entry.leaf.shapeIndexInObject);
      // enlarge both item shapes by the half clearance to create
      // symmetry.
      ConvexShape tmpOffsetShape = (ConvexShape) tmpShape.enlarge(currHalfClearance);
      if (currOffsetShape.intersects(tmpOffsetShape)) {
        p_obstacle_entries.add(
            new TreeEntry(tmp_entry.leaf.object, tmp_entry.leaf.shapeIndexInObject));
      }
    }
  }

  /**
   * Puts all items in the tree overlapping with p_shape on layer p_layer into p_obstacles, if
   * p_obstacles != null. If p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlapping_objects_with_clearance(
      ConvexShape p_shape,
      int p_layer,
      int[] p_ignore_net_nos,
      int p_cl_type,
      Set<SearchTreeObject> p_obstacles) {
    Collection<TreeEntry> treeEntries = new LinkedList<>();
    if (this.is_clearance_compensation_used()) {
      overlapping_tree_entries(p_shape, p_layer, p_ignore_net_nos, treeEntries);
    } else {
      overlapping_tree_entries_with_clearance(
          p_shape, p_layer, p_ignore_net_nos, p_cl_type, treeEntries);
    }
    if (p_obstacles == null) {
      return;
    }
    for (TreeEntry currEntry : treeEntries) {
      p_obstacles.add((SearchTreeObject) currEntry.object);
    }
  }

  /**
   * Returns items, which overlap with p_shape on layer p_layer inclusive clearance.
   * p_clearance_class is the index in the clearance matrix, which describes the required clearance
   * restrictions to other items. The function may also return items, which are nearly overlapping,
   * but do not overlap with exact calculation. If p_layer {@literal <} 0, the layer is ignored.
   */
  public Set<Item> overlapping_items_with_clearance(
      ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, int p_clearance_class) {
    Set<SearchTreeObject> overlaps = new TreeSet<>();

    this.overlapping_objects_with_clearance(
        p_shape, p_layer, p_ignore_net_nos, p_clearance_class, overlaps);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currObject : overlaps) {
      if (currObject instanceof Item item) {
        result.add(item);
      }
    }
    return result;
  }

  /**
   * Returns all objects of class TreeEntry, which overlap with p_shape on layer p_layer inclusive
   * clearance. p_clearance_class is the index in the clearance matrix, which describes the required
   * clearance restrictions to other items. If p_layer {@literal <} 0, the layer is ignored.
   */
  public Collection<TreeEntry> overlapping_tree_entries_with_clearance(
      ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, int p_clearance_class) {
    Collection<TreeEntry> result = new LinkedList<>();
    if (this.is_clearance_compensation_used()) {
      this.overlapping_tree_entries(p_shape, p_layer, p_ignore_net_nos, result);
    } else {
      this.overlapping_tree_entries_with_clearance(
          p_shape, p_layer, p_ignore_net_nos, p_clearance_class, result);
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
  public Collection<IncompleteFreeSpaceExpansionRoom> complete_shape(
      IncompleteFreeSpaceExpansionRoom p_room,
      int p_net_no,
      SearchTreeObject p_ignore_object,
      TileShape p_ignore_shape) {
    if (p_room.get_contained_shape() == null) {
      FRLogger.warn("ShapeSearchTree.complete_shape: p_shape_to_be_contained != null expected");
      return new LinkedList<>();
    }
    if (this.root == null) {
      return new LinkedList<>();
    }
    TileShape startShape = board.get_bounding_box();
    if (p_room.get_shape() != null) {
      startShape = startShape.intersection(p_room.get_shape());
    }
    RegularTileShape boundingShape = startShape.bounding_shape(this.boundingDirections);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    if (startShape.dimension() == 2) {
      IncompleteFreeSpaceExpansionRoom newRoom =
          new IncompleteFreeSpaceExpansionRoom(
              startShape, p_room.get_layer(), p_room.get_contained_shape());
      result.add(newRoom);
    }

    // To ensure exact algorithmic parity with v1.9, we need to visit obstacles
    // in a deterministic order. The non-deterministic order of tree traversal
    // causes different room partitioning.
    List<Leaf> overlappingLeaves = new LinkedList<>();
    ArrayStack<TreeNode> nodeStack = new ArrayStack<>(10000);
    nodeStack.push(this.root);
    TreeNode currNode;
    int roomLayer = p_room.get_layer();

    while ((currNode = nodeStack.pop()) != null) {
      if (currNode.boundingShape.intersects(boundingShape)) {
        if (currNode instanceof Leaf leaf) {
          overlappingLeaves.add(leaf);
        } else {
          nodeStack.push(((InnerNode) currNode).firstChild);
          nodeStack.push(((InnerNode) currNode).secondChild);
        }
      }
    }

    // Sort obstacles to ensure deterministic room partitioning.
    // v1.9's "natural" order was based on its tree structure.
    // Sorting with Leaf's natural comparison (which uses item idNo and shapeIndex)
    // provides a stable visit order.
    Collections.sort(overlappingLeaves);

    for (Leaf currLeaf : overlappingLeaves) {
      SearchTreeObject currObject = (SearchTreeObject) currLeaf.object;
      int shapeIndex = currLeaf.shapeIndexInObject;
      if (currObject.is_trace_obstacle(p_net_no)
          && currObject.shape_layer(shapeIndex) == roomLayer
          && currObject != p_ignore_object) {

        TileShape currObjectShape = currObject.get_tree_shape(this, shapeIndex);
        Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
        RegularTileShape newBoundingShape = IntOctagon.EMPTY;

        for (IncompleteFreeSpaceExpansionRoom curr_incomplete_room : result) {
          boolean somethingChanged = false;
          TileShape intersection = curr_incomplete_room.get_shape().intersection(currObjectShape);
          if (intersection.dimension() == 2) {
            boolean ignoreExpansionRoom =
                currObject instanceof CompleteFreeSpaceExpansionRoom
                    && p_ignore_shape != null
                    && p_ignore_shape.contains(intersection);
            FRLogger.trace(
                "COMPLETE_SHAPE_DECISION"
                    + ", net="
                    + p_net_no
                    + ", layer="
                    + roomLayer
                    + ", action="
                    + (ignoreExpansionRoom ? "IGNORE" : "RESTRAIN")
                    + ", obstacle_type="
                    + currObject.getClass().getSimpleName()
                    + ", obstacle_bounds="
                    + currObjectShape.bounding_box()
                    + ", overlap_bounds="
                    + intersection.bounding_box()
                    + ", ignore_bounds="
                    + (p_ignore_shape == null ? "null" : p_ignore_shape.bounding_box()));

            if (!ignoreExpansionRoom) {
              somethingChanged = true;
              Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
                  restrain_shape(curr_incomplete_room, currObjectShape);
              newResult.addAll(newRooms);
              // Keep v1.9 semantics: the bounding shape must include all accumulated rooms.
              for (IncompleteFreeSpaceExpansionRoom tmp_room : newResult) {
                newBoundingShape =
                    newBoundingShape.union(
                        tmp_room.get_shape().bounding_shape(this.boundingDirections));
              }
            }
          }
          if (!somethingChanged) {
            newResult.add(curr_incomplete_room);
            newBoundingShape =
                newBoundingShape.union(
                    curr_incomplete_room.get_shape().bounding_shape(this.boundingDirections));
          }
        }
        result = newResult;
        boundingShape = newBoundingShape;
      }
    }
    return divide_large_room(result, board.get_bounding_box());
  }

  /**
   * Restrains the shape of p_incomplete_room to a TileShape, which does not intersect with the
   * interior of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be contained in the
   * shape of the result room. If that is not possible, several rooms are returned with shapes,
   * which intersect with p_incomplete_room.get_contained_shape().
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrain_shape(
      IncompleteFreeSpaceExpansionRoom p_incomplete_room, TileShape p_obstacle_shape) {
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    // Search the edge line of p_obstacle_shape, so that p_shape_to_be_contained
    // are on the right side of this line, and that the line segment
    // intersects with the interior of p_shape.
    // If there are more than 1 such lines take the line which is
    // furthest away from p_points_to_be_contained.
    // Then intersect p_shape with the halfplane defined by the
    // opposite of this line.

    // Always convert to Simplex to match v1.9 semantics - the comment below explains why:
    // "otherwise border_lines of length 0 for octagons may not be handled correctly"
    Simplex obstacleSimplex = p_obstacle_shape.to_Simplex();

    TileShape roomShape = p_incomplete_room.get_shape();
    int layer = p_incomplete_room.get_layer();

    TileShape shapeToBeContained = p_incomplete_room.get_contained_shape();
    if (shapeToBeContained != null) {
      shapeToBeContained =
          shapeToBeContained.to_Simplex(); // There may be a performance problem, if a point
      // shape is represented
      // as an octagon
    }
    if (shapeToBeContained == null || shapeToBeContained.is_empty()) {
      FRLogger.trace("ShapeSearchTree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    Line cutLine = null;
    double cutLineDistance = -1;

    for (int i = 0; i < obstacleSimplex.border_line_count(); i++) {
      LineSegment currLineSegment = new LineSegment(obstacleSimplex, i);
      if (roomShape.is_intersected_interior_by(currLineSegment)) {
        // otherwise currObject may not touch the intersection
        // of p_shape with the half_plane defined by the cutLine.
        // That may lead to problems when creating the ExpansionRooms.
        Line currLine = obstacleSimplex.border_line(i);

        double currMinDistance = shapeToBeContained.distance_to_the_left(currLine);

        if (currMinDistance > cutLineDistance) {
          cutLineDistance = currMinDistance;
          cutLine = currLine.opposite();
        }
      }
    }

    if (cutLine != null) {
      TileShape resultPiece = TileShape.get_instance(cutLine);
      if (roomShape != null) {
        resultPiece = roomShape.intersection(resultPiece);
      }
      if (resultPiece.dimension() >= 2) {
        result.add(new IncompleteFreeSpaceExpansionRoom(resultPiece, layer, shapeToBeContained));
      }
    } else {
      // There is no cut line, so that all p_shape_to_be_contained is
      // completely on the right side of that line. Search a cut line, so that
      // at least part of p_shape_to_be_contained is on the right side.
      if (shapeToBeContained.dimension() < 1) {
        // There is already a completed expansion room around p_shape_to_be_contained.
        return result;
      }

      for (int i = 0; i < obstacleSimplex.border_line_count(); i++) {
        LineSegment currLineSegment = new LineSegment(obstacleSimplex, i);
        if (roomShape.is_intersected_interior_by(currLineSegment)) {
          Line currLine = obstacleSimplex.border_line(i);
          if (shapeToBeContained.side_of(currLine) == Side.COLLINEAR) {
            // currLine intersects with the interior of p_shape_to_be_contained
            cutLine = currLine.opposite();
            break;
          }
        }
      }

      if (cutLine == null) {
        // cut line not found, parts or the whole of p_shape may be already
        // occupied from somewhere else.
        return result;
      }
      // Calculate the new shape to be contained in the result shape.
      TileShape cutHalfPlane = TileShape.get_instance(cutLine);
      TileShape newShapeToBeContained = shapeToBeContained.intersection(cutHalfPlane);

      TileShape resultPiece;
      if (roomShape == null) {
        resultPiece = cutHalfPlane;
      } else {
        resultPiece = roomShape.intersection(cutHalfPlane);
      }
      if (resultPiece.dimension() >= 2) {
        result.add(new IncompleteFreeSpaceExpansionRoom(resultPiece, layer, newShapeToBeContained));
      }
      TileShape oppositeHalfPlane = TileShape.get_instance(cutLine.opposite());
      TileShape restPiece;
      if (roomShape == null) {
        restPiece = oppositeHalfPlane;
      } else {
        restPiece = roomShape.intersection(oppositeHalfPlane);
      }
      if (restPiece.dimension() >= 2) {
        TileShape restShapeToBeContained = shapeToBeContained.intersection(oppositeHalfPlane);
        IncompleteFreeSpaceExpansionRoom restIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(restPiece, layer, restShapeToBeContained);
        result.addAll(restrain_shape(restIncompleteRoom, p_obstacle_shape));
      }
    }
    return result;
  }

  /**
   * Reduces the first or last shape of p_trace at a tie pin, so that the autorouter algorithm can
   * find a connection for a different net.
   */
  public void reduce_trace_shape_at_tie_pin(Pin p_tie_pin, PolylineTrace p_trace) {
    TileShape pinShape = p_tie_pin.get_tree_shape_on_layer(this, p_trace.get_layer());
    FloatPoint compareCorner;
    int traceShapeNo;
    if (p_trace.first_corner().equals(p_tie_pin.get_center())) {
      traceShapeNo = 0;
      compareCorner = p_trace.polyline().corner_approx(1);

    } else if (p_trace.last_corner().equals(p_tie_pin.get_center())) {
      traceShapeNo = p_trace.corner_count() - 2;
      compareCorner = p_trace.polyline().corner_approx(p_trace.corner_count() - 2);
    } else {
      return;
    }
    TileShape traceShape = p_trace.get_tree_shape(this, traceShapeNo);
    TileShape intersection = traceShape.intersection(pinShape);
    if (intersection.dimension() < 2) {
      return;
    }
    TileShape[] shapePieces = traceShape.cutout(pinShape);
    TileShape newTraceShape = Simplex.EMPTY;
    for (int i = 0; i < shapePieces.length; i++) {
      if (shapePieces[i].dimension() == 2) {
        if (newTraceShape == Simplex.EMPTY || shapePieces[i].contains(compareCorner)) {
          newTraceShape = shapePieces[i];
        }
      }
    }
    change_item_shape(p_trace, traceShapeNo, newTraceShape);
  }

  /**
   * Changes the shape with index p_shape_no of this item to p_new_shape and updates the entry in
   * the tree.
   */
  void change_item_shape(Item p_item, int p_shape_no, TileShape p_new_shape) {
    Leaf[] oldEntries = p_item.get_search_tree_entries(this);
    Leaf[] newLeafArr = new Leaf[oldEntries.length];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[oldEntries.length];
    remove_leaf(oldEntries[p_shape_no]);
    for (int i = 0; i < newPrecalculatedTreeShapes.length; i++) {
      if (i == p_shape_no) {
        newPrecalculatedTreeShapes[i] = p_new_shape;

      } else {
        newPrecalculatedTreeShapes[i] = p_item.get_tree_shape(this, i);
        newLeafArr[i] = oldEntries[i];
      }
    }
    p_item.set_precalculated_tree_shapes(newPrecalculatedTreeShapes, this);
    newLeafArr[p_shape_no] = insert(p_item, p_shape_no);
    p_item.set_search_tree_entries(newLeafArr, this);
  }

  TileShape[] calculate_tree_shapes(DrillItem p_drill_item) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result = new TileShape[p_drill_item.tile_shape_count()];
    for (int i = 0; i < result.length; i++) {
      Shape currShape = p_drill_item.get_shape(i);
      if (currShape == null) {
        currShape = drill_hole_obstacle(p_drill_item);
      }
      if (currShape == null) {
        result[i] = null;
      } else {
        TileShape currTileShape;
        if (this.board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE) {
          currTileShape = currShape.bounding_box();
        } else if (this.board.rules.get_trace_angle_restriction()
            == AngleRestriction.FORTYFIVE_DEGREE) {
          currTileShape = currShape.bounding_octagon();
        } else {
          currTileShape = currShape.bounding_tile();
        }
        int offsetWidth =
            this.clearance_compensation_value(
                p_drill_item.clearance_class_no(), p_drill_item.shape_layer(i));
        offsetWidth +=
            drill_hole_clearance_delta(p_drill_item, currShape, p_drill_item.shape_layer(i));
        if (currTileShape == null) {
          FRLogger.warn("ShapeSearchTree.calculate_tree_shapes: shape is null");
        } else {
          currTileShape = (TileShape) currTileShape.enlarge(offsetWidth);
        }
        result[i] = currTileShape;
      }
    }
    return result;
  }

  /**
   * Synthesized obstacle for copper layers where a drilled item has NO pad shape: the drill hole
   * still passes through (e.g. a through-via with unused inner layers), so other-net copper on
   * those layers must keep hole clearance from it. Returns null when the hole-clearance rule is
   * disabled or no drill radius is known.
   */
  protected Shape drill_hole_obstacle(DrillItem p_drill_item) {
    if (this.board == null
        || this.board.rules == null
        || this.board.rules.get_hole_clearance() <= 0
        || p_drill_item.get_padstack() == null) {
      return null;
    }
    double drillRadius = p_drill_item.get_padstack().get_drill_radius();
    if (drillRadius <= 0) {
      return null;
    }
    Point center = p_drill_item.get_center();
    if (!(center instanceof IntPoint)) {
      center = center.to_float().round();
    }
    return new Circle((IntPoint) center, (int) Math.ceil(drillRadius));
  }

  /**
   * Extra obstacle inflation so that copper of other nets stays holeClearance away from this item's
   * drill hole (not just its copper pad). Applies to every drilled item — vias, PTH pins and
   * hole-only (NPTH) padstacks alike; returns 0 when the hole-clearance rule is disabled.
   */
  protected int drill_hole_clearance_delta(DrillItem p_drill_item, Shape p_shape, int p_layer) {
    if (this.board == null || this.board.rules == null) {
      return 0;
    }
    int holeClearance = this.board.rules.get_hole_clearance();
    if (holeClearance <= 0 || p_shape == null || p_drill_item.get_padstack() == null) {
      return 0;
    }

    double drillRadius = p_drill_item.get_padstack().get_drill_radius();
    if (drillRadius <= 0) {
      return 0;
    }
    double copperRadius;
    if (p_drill_item.get_padstack().holeOnly) {
      copperRadius = drillRadius;
    } else {
      copperRadius = p_shape.border_distance(p_drill_item.get_center().to_float());
      if (copperRadius <= 0) {
        Shape padShape = p_drill_item.get_padstack().get_shape(p_layer);
        copperRadius = padShape == null ? drillRadius : padShape.border_distance(FloatPoint.ZERO);
      }
    }
    int clearanceClass =
        this.compensatedClearanceClassNo > 0
            ? this.compensatedClearanceClassNo
            : BoardRules.default_clearance_class();
    int copperClearance =
        this.board.rules.clearanceMatrix.get_value(
            p_drill_item.clearance_class_no(), clearanceClass, p_layer, false);
    return Math.max(
        0,
        (int)
            Math.ceil(
                drillRadius
                    + holeClearance
                    + DRILL_HOLE_CLEARANCE_MARGIN
                    - copperRadius
                    - copperClearance));
  }

  TileShape[] calculate_tree_shapes(ObstacleArea p_obstacle_area) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] convexShapes = p_obstacle_area.split_to_convex();
    if (convexShapes == null) {
      return new TileShape[0];
    }
    double maxTreeShapeWidth = 50000;
    if (this.board.communication.host_cad_exists()) {
      maxTreeShapeWidth =
          Math.min(500 * this.board.communication.get_resolution(Unit.MIL), maxTreeShapeWidth);
      // Problem with low resolution on Kicad.
      // Called only for designs from host cad systems because otherwise the old
      // sample.dsn gets to
      // many tree shapes.
    }

    Collection<TileShape> treeShapeList = new LinkedList<>();
    for (int i = 0; i < convexShapes.length; i++) {
      TileShape currConvexShape = convexShapes[i];

      int offsetWidth =
          this.clearance_compensation_value(
              p_obstacle_area.clearance_class_no(), p_obstacle_area.get_layer());
      currConvexShape = (TileShape) currConvexShape.enlarge(offsetWidth);
      TileShape[] currTreeShapes = currConvexShape.divide_into_sections(maxTreeShapeWidth);
      treeShapeList.addAll(Arrays.asList(currTreeShapes));
    }
    TileShape[] result = new TileShape[treeShapeList.size()];
    Iterator<TileShape> it = treeShapeList.iterator();
    for (int i = 0; i < result.length; i++) {
      result[i] = it.next();
    }
    return result;
  }

  TileShape[] calculate_tree_shapes(BoardOutline p_board_outline) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result;
    if (p_board_outline.keepout_outside_outline_generated()) {
      TileShape[] convexShapes = p_board_outline.get_keepout_area().split_to_convex();
      if (convexShapes == null) {
        return new TileShape[0];
      }
      Collection<TileShape> treeShapeList = new LinkedList<>();
      for (int layerNo = 0; layerNo < this.board.layerStructure.arr.length; layerNo++) {
        for (int i = 0; i < convexShapes.length; i++) {
          TileShape currConvexShape = convexShapes[i];
          int offsetWidth =
              this.clearance_compensation_value(p_board_outline.clearance_class_no(), layerNo);
          currConvexShape = (TileShape) currConvexShape.enlarge(offsetWidth);
          treeShapeList.add(currConvexShape);
        }
      }
      result = new TileShape[treeShapeList.size()];
      Iterator<TileShape> it = treeShapeList.iterator();
      for (int i = 0; i < result.length; i++) {
        result[i] = it.next();
      }
    } else {
      // Only the line shapes of the outline are inserted as obstacles into the tree.
      result = new TileShape[p_board_outline.line_count() * this.board.layerStructure.arr.length];
      int halfWidth = p_board_outline.get_half_width();
      Line[] currLineArr = new Line[3];
      int currNo = 0;
      for (int layerNo = 0; layerNo < this.board.layerStructure.arr.length; layerNo++) {
        for (int shapeNo = 0; shapeNo < p_board_outline.shape_count(); shapeNo++) {
          PolylineShape currOutlineShape = p_board_outline.get_shape(shapeNo);
          int borderLineCount = currOutlineShape.border_line_count();
          currLineArr[0] = currOutlineShape.border_line(borderLineCount - 1);
          for (int i = 0; i < borderLineCount; i++) {
            currLineArr[1] = currOutlineShape.border_line(i);
            currLineArr[2] = currOutlineShape.border_line((i + 1) % borderLineCount);
            Polyline tmpPolyline = new Polyline(currLineArr);
            int cmpValue =
                this.clearance_compensation_value(p_board_outline.clearance_class_no(), layerNo);
            result[currNo] = tmpPolyline.offset_shape(halfWidth + cmpValue, 0);
            ++currNo;
            currLineArr[0] = currLineArr[1];
          }
        }
      }
    }
    return result;
  }

  /**
   * Used for creating the shapes of a polyline_trace for this tree. Overwritten in derived classes.
   */
  TileShape offset_shape(Polyline p_polyline, int p_half_width, int p_no) {
    return p_polyline.offset_shape(p_half_width, p_no);
  }

  /**
   * Used for creating the shapes of a polyline_trace for this tree. Overwritten in derived classes.
   */
  public TileShape[] offset_shapes(
      Polyline p_polyline, int p_half_width, int p_from_no, int p_to_no) {
    return p_polyline.offset_shapes(p_half_width, p_from_no, p_to_no);
  }

  TileShape[] calculate_tree_shapes(PolylineTrace p_trace) {
    if (this.board == null) {
      return new TileShape[0];
    }
    int offsetWidth =
        p_trace.get_half_width()
            + this.clearance_compensation_value(p_trace.clearance_class_no(), p_trace.get_layer());
    TileShape[] result = new TileShape[p_trace.tile_shape_count()];
    for (int i = 0; i < result.length; i++) {
      result[i] = this.offset_shape(p_trace.polyline(), offsetWidth, i);
    }
    return result;
  }

  /**
   * Makes sure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm gets problems with
   * vias.
   */
  protected Collection<IncompleteFreeSpaceExpansionRoom> divide_large_room(
      Collection<IncompleteFreeSpaceExpansionRoom> p_room_list, IntBox p_board_bounding_box) {
    if (p_room_list.size() != 1) {
      return p_room_list;
    }
    IncompleteFreeSpaceExpansionRoom currRoom = p_room_list.iterator().next();
    IntBox roomBoundingBox = currRoom.get_shape().bounding_box();
    if (2 * roomBoundingBox.height() <= p_board_bounding_box.height()
        || 2 * roomBoundingBox.width() <= p_board_bounding_box.width()) {
      return p_room_list;
    }
    double maxSectionWidth =
        0.5 * Math.max(p_board_bounding_box.height(), p_board_bounding_box.width());
    TileShape[] sectionArr = currRoom.get_shape().divide_into_sections(maxSectionWidth);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    for (TileShape currSection : sectionArr) {
      TileShape currShapeToBeContained = currSection.intersection(currRoom.get_contained_shape());
      IncompleteFreeSpaceExpansionRoom currSectionRoom =
          new IncompleteFreeSpaceExpansionRoom(
              currSection, currRoom.get_layer(), currShapeToBeContained);
      result.add(currSectionRoom);
    }
    return result;
  }

  boolean validate_entries(Item p_item) {
    Leaf[] currTreeEntries = p_item.get_search_tree_entries(this);
    for (int i = 0; i < currTreeEntries.length; i++) {
      Leaf currLeaf = currTreeEntries[i];
      if (currLeaf.shapeIndexInObject != i) {
        FRLogger.warn("tree entry inconsistent for Item");
        return false;
      }
    }
    return true;
  }

  /** created for sorting Items according to their clearance to p_cl_type on layer p_layer */
  private static class EntrySortedByClearance implements Comparable<EntrySortedByClearance> {

    private final int entryIdNo;
    Leaf leaf;
    int clearance;

    EntrySortedByClearance(Leaf p_leaf, int p_clearance) {
      leaf = p_leaf;
      clearance = p_clearance;
      if (lastGeneratedIdNo == Integer.MAX_VALUE) {
        lastGeneratedIdNo = 0;
      } else {
        ++lastGeneratedIdNo;
      }
      entryIdNo = lastGeneratedIdNo;
    }

    @Override
    public int compareTo(EntrySortedByClearance p_other) {
      if (clearance != p_other.clearance) {
        return Signum.as_int(clearance - p_other.clearance);
      }
      return entryIdNo - p_other.entryIdNo;
    }
  }
}

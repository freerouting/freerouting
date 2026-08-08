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
  public boolean isClearanceCompensationUsed() {
    return this.compensatedClearanceClassNo > 0;
  }

  /**
   * Return the clearance compensation value of p_clearance_class_no to the clearance compensation
   * class of this search tree with on layer p_layer. Returns 0, if no clearance compensation is
   * used for this tree.
   */
  public int clearanceCompensationValue(int p_clearance_class_no, int p_layer) {
    if (p_clearance_class_no <= 0) {
      return 0;
    }
    int result =
        board.rules.clearanceMatrix.getValue(
                p_clearance_class_no, this.compensatedClearanceClassNo, p_layer, false)
            - board.rules.clearanceMatrix.clearanceCompensationValue(
                this.compensatedClearanceClassNo, p_layer);
    return Math.max(result, 0);
  }

  /**
   * Changes the tree entries from p_keep_at_start_count + 1 to newShapeCount - 1 - keepAtEndCount
   * to p_changed_entries. Special implementation for change_trace for performance reasons
   */
  void changeEntries(
      PolylineTrace p_obj,
      Polyline p_new_polyline,
      int p_keep_at_start_count,
      int p_keep_at_end_count) {
    // calculate the shapes of p_new_polyline from keepAtStartCount to
    // newShapeCount - keepAtEndCount - 1;
    int compensatedHalfWidth =
        p_obj.getHalfWidth()
            + this.clearanceCompensationValue(p_obj.clearanceClassNo(), p_obj.getLayer());
    TileShape[] changedShapes =
        this.offsetShapes(
            p_new_polyline,
            compensatedHalfWidth,
            p_keep_at_start_count,
            p_new_polyline.arr.length - 1 - p_keep_at_end_count);
    int oldShapeCount = p_obj.treeShapeCount(this);
    int newShapeCount = changedShapes.length + p_keep_at_start_count + p_keep_at_end_count;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    Leaf[] oldEntries = p_obj.getSearchTreeEntries(this);
    for (int i = 0; i < p_keep_at_start_count; i++) {
      newLeafArr[i] = oldEntries[i];
      newPrecalculatedTreeShapes[i] = p_obj.getTreeShape(this, i);
    }
    for (int i = p_keep_at_start_count; i < oldShapeCount - p_keep_at_end_count; i++) {
      removeLeaf(oldEntries[i]);
    }
    for (int i = 0; i < p_keep_at_end_count; i++) {
      int newIndex = newShapeCount - p_keep_at_end_count + i;
      int oldIndex = oldShapeCount - p_keep_at_end_count + i;

      newLeafArr[newIndex] = oldEntries[oldIndex];
      newLeafArr[newIndex].shapeIndexInObject = newIndex;
      newPrecalculatedTreeShapes[newIndex] = p_obj.getTreeShape(this, oldIndex);
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    System.arraycopy(
        changedShapes, 0, newPrecalculatedTreeShapes, p_keep_at_start_count, changedShapes.length);
    p_obj.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    for (int i = p_keep_at_start_count; i < newShapeCount - p_keep_at_end_count; i++) {
      newLeafArr[i] = insert(p_obj, i);
    }
    p_obj.setSearchTreeEntries(newLeafArr, this);
  }

  /**
   * Merges the tree entries from p_from_trace in front of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void mergeEntriesInFront(
      PolylineTrace p_from_trace,
      PolylineTrace p_to_trace,
      Polyline p_joined_polyline,
      int p_from_entry_no,
      int p_to_entry_no) {
    int compensatedHalfWidth =
        p_to_trace.getHalfWidth()
            + this.clearanceCompensationValue(
                p_to_trace.clearanceClassNo(), p_to_trace.getLayer());
    TileShape[] linkShapes =
        this.offsetShapes(p_joined_polyline, compensatedHalfWidth, p_from_entry_no, p_to_entry_no);
    boolean changeOrder = p_from_trace.firstCorner().equals(p_to_trace.firstCorner());
    // remove the last or first tree entry from p_from_trace and the
    // first tree entry from p_to_trace, because they will be replaced by
    // the new link entries.
    int fromShapeCountMinus1 = p_from_trace.tileShapeCount() - 1;
    int removeNo;
    if (changeOrder) {
      removeNo = 0;
    } else {
      removeNo = fromShapeCountMinus1;
    }
    Leaf[] fromTraceEntries = p_from_trace.getSearchTreeEntries(this);
    Leaf[] toTraceEntries = p_to_trace.getSearchTreeEntries(this);
    removeLeaf(fromTraceEntries[removeNo]);
    removeLeaf(toTraceEntries[0]);
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
      newPrecalculatedTreeShapes[i] = p_from_trace.getTreeShape(this, fromNo);
      newLeafArr[i] = fromTraceEntries[fromNo];
      newLeafArr[i].object = p_to_trace;
      newLeafArr[i].shapeIndexInObject = i;
    }
    for (int i = 1; i < oldToShapeCount; i++) {
      int currInd = fromShapeCountMinus1 + linkShapes.length + i - 1;
      newPrecalculatedTreeShapes[currInd] = p_to_trace.getTreeShape(this, i);
      newLeafArr[currInd] = toTraceEntries[i];
      newLeafArr[currInd].shapeIndexInObject = currInd;
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = fromShapeCountMinus1 + i;
      newPrecalculatedTreeShapes[currInd] = linkShapes[i];
    }
    p_to_trace.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    // create the new link entries
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = fromShapeCountMinus1 + i;
      newLeafArr[currInd] = insert(p_to_trace, currInd);
    }

    p_to_trace.setSearchTreeEntries(newLeafArr, this);
  }

  /**
   * Merges the tree entries from p_from_trace to the end of p_to_trace. Special implementation for
   * combine trace for performance reasons.
   */
  void mergeEntriesAtEnd(
      PolylineTrace p_from_trace,
      PolylineTrace p_to_trace,
      Polyline p_joined_polyline,
      int p_from_entry_no,
      int p_to_entry_no) {
    int compensatedHalfWidth =
        p_to_trace.getHalfWidth()
            + this.clearanceCompensationValue(
                p_to_trace.clearanceClassNo(), p_to_trace.getLayer());
    TileShape[] linkShapes =
        this.offsetShapes(p_joined_polyline, compensatedHalfWidth, p_from_entry_no, p_to_entry_no);
    boolean changeOrder = p_from_trace.lastCorner().equals(p_to_trace.lastCorner());
    Leaf[] fromTraceEntries = p_from_trace.getSearchTreeEntries(this);
    Leaf[] toTraceEntries = p_to_trace.getSearchTreeEntries(this);
    // remove the last or first tree entry from p_from_trace and the
    // last tree entry from p_to_trace, because they will be replaced by
    // the new link entries.
    int toShapeCountMinus1 = p_to_trace.tileShapeCount() - 1;
    removeLeaf(toTraceEntries[toShapeCountMinus1]);
    int removeNo;
    if (changeOrder) {
      removeNo = p_from_trace.tileShapeCount() - 1;
    } else {
      removeNo = 0;
    }
    removeLeaf(fromTraceEntries[removeNo]);
    int newShapeCount = fromTraceEntries.length + linkShapes.length + toTraceEntries.length - 2;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    // transfer the tree entries except the last from the old shapes
    // of p_to_trace to the new shapes of p_to_trace
    for (int i = 0; i < toShapeCountMinus1; i++) {
      newPrecalculatedTreeShapes[i] = p_to_trace.getTreeShape(this, i);
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
      newPrecalculatedTreeShapes[currInd] = p_from_trace.getTreeShape(this, fromNo);
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
    p_to_trace.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    // create the new link entries
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = toShapeCountMinus1 + i;
      newLeafArr[currInd] = insert(p_to_trace, currInd);
    }
    p_to_trace.setSearchTreeEntries(newLeafArr, this);
  }

  /**
   * Transfers tree entries from p_from_trace to p_start and p_end_piece after a middle piece was
   * cut out. Special implementation for ShapeTraceEntries.fast_cutout_trace for performance
   * reasons.
   */
  void reuseEntriesAfterCutout(
      PolylineTrace p_from_trace, PolylineTrace p_start_piece, PolylineTrace p_end_piece) {
    Leaf[] startPieceLeafArr = new Leaf[p_start_piece.polyline().arr.length - 2];
    Leaf[] fromTraceEntries = p_from_trace.getSearchTreeEntries(this);
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

    p_start_piece.setSearchTreeEntries(startPieceLeafArr, this);
    p_end_piece.setSearchTreeEntries(endPieceLeafArr, this);
  }

  /**
   * Puts all items in the tree overlapping with p_shape on layer p_layer into p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlappingObjects(
      ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, Set<SearchTreeObject> p_obstacles) {
    Collection<TreeEntry> treeEntries = new LinkedList<>();
    overlappingTreeEntries(p_shape, p_layer, p_ignore_net_nos, treeEntries);
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
  public Set<SearchTreeObject> overlappingObjects(ConvexShape p_shape, int p_layer) {
    Set<SearchTreeObject> result = new TreeSet<>();
    this.overlappingObjects(p_shape, p_layer, new int[0], result);
    return result;
  }

  /**
   * Puts all tree entries overlapping with p_shape on layer p_layer into the list p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlappingTreeEntries(
      ConvexShape p_shape, int p_layer, Collection<TreeEntry> p_tree_entries) {
    overlappingTreeEntries(p_shape, p_layer, new int[0], p_tree_entries);
  }

  /**
   * Puts all tree entries overlapping with p_shape on layer p_layer into the list p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored. treeEntries with object containing a net number
   * of p_ignore_net_nos are ignored.
   */
  public void overlappingTreeEntries(
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
    RegularTileShape bounds = p_shape.boundingShape(boundingDirections);
    if (bounds == null) {
      FRLogger.warn("ShapeSearchTree.overlaps: p_shape not bounded");
      return;
    }
    Collection<Leaf> tmpList = this.overlaps(bounds);
    boolean is45Degree = p_shape instanceof IntOctagon;

    for (Leaf currLeaf : tmpList) {
      SearchTreeObject currObject = (SearchTreeObject) currLeaf.object;
      int shapeIndex = currLeaf.shapeIndexInObject;
      boolean ignoreObject = p_layer >= 0 && currObject.shapeLayer(shapeIndex) != p_layer;
      if (!ignoreObject) {
        for (int i = 0; i < p_ignore_net_nos.length; i++) {
          if (!currObject.isObstacle(p_ignore_net_nos[i])) {
            ignoreObject = true;
          }
        }
      }
      if (!ignoreObject) {
        TileShape currShape = currObject.getTreeShape(this, currLeaf.shapeIndexInObject);
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
  void overlappingTreeEntriesWithClearance(
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
    RegularTileShape bounds = p_shape.boundingShape(boundingDirections);
    if (bounds == null) {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: p_shape is not bounded");
      bounds = board.getBoundingBox();
    }
    int maxClearance = (int) (1.2 * clMatrix.maxValue(p_cl_type, p_layer));
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
      boolean ignoreItem = p_layer >= 0 && currItem.shapeLayer(shapeIndex) != p_layer;
      if (!ignoreItem) {
        for (int i = 0; i < p_ignore_net_nos.length; i++) {
          if (!currItem.isObstacle(p_ignore_net_nos[i])) {
            ignoreItem = true;
          }
        }
      }
      if (!ignoreItem) {
        int currClearance =
            clMatrix.getValue(p_cl_type, currItem.clearanceClassNo(), p_layer, true);
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
          tmp_entry.leaf.object.getTreeShape(this, tmp_entry.leaf.shapeIndexInObject);
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
  public void overlappingObjectsWithClearance(
      ConvexShape p_shape,
      int p_layer,
      int[] p_ignore_net_nos,
      int p_cl_type,
      Set<SearchTreeObject> p_obstacles) {
    Collection<TreeEntry> treeEntries = new LinkedList<>();
    if (this.isClearanceCompensationUsed()) {
      overlappingTreeEntries(p_shape, p_layer, p_ignore_net_nos, treeEntries);
    } else {
      overlappingTreeEntriesWithClearance(
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
  public Set<Item> overlappingItemsWithClearance(
      ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, int p_clearance_class) {
    Set<SearchTreeObject> overlaps = new TreeSet<>();

    this.overlappingObjectsWithClearance(
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
  public Collection<TreeEntry> overlappingTreeEntriesWithClearance(
      ConvexShape p_shape, int p_layer, int[] p_ignore_net_nos, int p_clearance_class) {
    Collection<TreeEntry> result = new LinkedList<>();
    if (this.isClearanceCompensationUsed()) {
      this.overlappingTreeEntries(p_shape, p_layer, p_ignore_net_nos, result);
    } else {
      this.overlappingTreeEntriesWithClearance(
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
  public Collection<IncompleteFreeSpaceExpansionRoom> completeShape(
      IncompleteFreeSpaceExpansionRoom p_room,
      int p_net_no,
      SearchTreeObject p_ignore_object,
      TileShape p_ignore_shape) {
    if (p_room.getContainedShape() == null) {
      FRLogger.warn("ShapeSearchTree.complete_shape: p_shape_to_be_contained != null expected");
      return new LinkedList<>();
    }
    if (this.root == null) {
      return new LinkedList<>();
    }
    TileShape startShape = board.getBoundingBox();
    if (p_room.getShape() != null) {
      startShape = startShape.intersection(p_room.getShape());
    }
    RegularTileShape boundingShape = startShape.boundingShape(this.boundingDirections);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    if (startShape.dimension() == 2) {
      IncompleteFreeSpaceExpansionRoom newRoom =
          new IncompleteFreeSpaceExpansionRoom(
              startShape, p_room.getLayer(), p_room.getContainedShape());
      result.add(newRoom);
    }

    // To ensure exact algorithmic parity with v1.9, we need to visit obstacles
    // in a deterministic order. The non-deterministic order of tree traversal
    // causes different room partitioning.
    List<Leaf> overlappingLeaves = new LinkedList<>();
    ArrayStack<TreeNode> nodeStack = new ArrayStack<>(10000);
    nodeStack.push(this.root);
    TreeNode currNode;
    int roomLayer = p_room.getLayer();

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
      if (currObject.isTraceObstacle(p_net_no)
          && currObject.shapeLayer(shapeIndex) == roomLayer
          && currObject != p_ignore_object) {

        TileShape currObjectShape = currObject.getTreeShape(this, shapeIndex);
        Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
        RegularTileShape newBoundingShape = IntOctagon.EMPTY;

        for (IncompleteFreeSpaceExpansionRoom curr_incomplete_room : result) {
          boolean somethingChanged = false;
          TileShape intersection = curr_incomplete_room.getShape().intersection(currObjectShape);
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
                    + currObjectShape.boundingBox()
                    + ", overlap_bounds="
                    + intersection.boundingBox()
                    + ", ignore_bounds="
                    + (p_ignore_shape == null ? "null" : p_ignore_shape.boundingBox()));

            if (!ignoreExpansionRoom) {
              somethingChanged = true;
              Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
                  restrainShape(curr_incomplete_room, currObjectShape);
              newResult.addAll(newRooms);
              // Keep v1.9 semantics: the bounding shape must include all accumulated rooms.
              for (IncompleteFreeSpaceExpansionRoom tmp_room : newResult) {
                newBoundingShape =
                    newBoundingShape.union(
                        tmp_room.getShape().boundingShape(this.boundingDirections));
              }
            }
          }
          if (!somethingChanged) {
            newResult.add(curr_incomplete_room);
            newBoundingShape =
                newBoundingShape.union(
                    curr_incomplete_room.getShape().boundingShape(this.boundingDirections));
          }
        }
        result = newResult;
        boundingShape = newBoundingShape;
      }
    }
    return divideLargeRoom(result, board.getBoundingBox());
  }

  /**
   * Restrains the shape of p_incomplete_room to a TileShape, which does not intersect with the
   * interior of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be contained in the
   * shape of the result room. If that is not possible, several rooms are returned with shapes,
   * which intersect with p_incomplete_room.get_contained_shape().
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrainShape(
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
    Simplex obstacleSimplex = p_obstacle_shape.toSimplex();

    TileShape roomShape = p_incomplete_room.getShape();
    int layer = p_incomplete_room.getLayer();

    TileShape shapeToBeContained = p_incomplete_room.getContainedShape();
    if (shapeToBeContained != null) {
      shapeToBeContained =
          shapeToBeContained.toSimplex(); // There may be a performance problem, if a point
      // shape is represented
      // as an octagon
    }
    if (shapeToBeContained == null || shapeToBeContained.isEmpty()) {
      FRLogger.trace("ShapeSearchTree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    Line cutLine = null;
    double cutLineDistance = -1;

    for (int i = 0; i < obstacleSimplex.borderLineCount(); i++) {
      LineSegment currLineSegment = new LineSegment(obstacleSimplex, i);
      if (roomShape.isIntersectedInteriorBy(currLineSegment)) {
        // otherwise currObject may not touch the intersection
        // of p_shape with the half_plane defined by the cutLine.
        // That may lead to problems when creating the ExpansionRooms.
        Line currLine = obstacleSimplex.borderLine(i);

        double currMinDistance = shapeToBeContained.distanceToTheLeft(currLine);

        if (currMinDistance > cutLineDistance) {
          cutLineDistance = currMinDistance;
          cutLine = currLine.opposite();
        }
      }
    }

    if (cutLine != null) {
      TileShape resultPiece = TileShape.getInstance(cutLine);
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

      for (int i = 0; i < obstacleSimplex.borderLineCount(); i++) {
        LineSegment currLineSegment = new LineSegment(obstacleSimplex, i);
        if (roomShape.isIntersectedInteriorBy(currLineSegment)) {
          Line currLine = obstacleSimplex.borderLine(i);
          if (shapeToBeContained.sideOf(currLine) == Side.COLLINEAR) {
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
      TileShape cutHalfPlane = TileShape.getInstance(cutLine);
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
      TileShape oppositeHalfPlane = TileShape.getInstance(cutLine.opposite());
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
        result.addAll(restrainShape(restIncompleteRoom, p_obstacle_shape));
      }
    }
    return result;
  }

  /**
   * Reduces the first or last shape of p_trace at a tie pin, so that the autorouter algorithm can
   * find a connection for a different net.
   */
  public void reduceTraceShapeAtTiePin(Pin p_tie_pin, PolylineTrace p_trace) {
    TileShape pinShape = p_tie_pin.getTreeShapeOnLayer(this, p_trace.getLayer());
    FloatPoint compareCorner;
    int traceShapeNo;
    if (p_trace.firstCorner().equals(p_tie_pin.getCenter())) {
      traceShapeNo = 0;
      compareCorner = p_trace.polyline().cornerApprox(1);

    } else if (p_trace.lastCorner().equals(p_tie_pin.getCenter())) {
      traceShapeNo = p_trace.cornerCount() - 2;
      compareCorner = p_trace.polyline().cornerApprox(p_trace.cornerCount() - 2);
    } else {
      return;
    }
    TileShape traceShape = p_trace.getTreeShape(this, traceShapeNo);
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
    changeItemShape(p_trace, traceShapeNo, newTraceShape);
  }

  /**
   * Changes the shape with index p_shape_no of this item to p_new_shape and updates the entry in
   * the tree.
   */
  void changeItemShape(Item p_item, int p_shape_no, TileShape p_new_shape) {
    Leaf[] oldEntries = p_item.getSearchTreeEntries(this);
    Leaf[] newLeafArr = new Leaf[oldEntries.length];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[oldEntries.length];
    removeLeaf(oldEntries[p_shape_no]);
    for (int i = 0; i < newPrecalculatedTreeShapes.length; i++) {
      if (i == p_shape_no) {
        newPrecalculatedTreeShapes[i] = p_new_shape;

      } else {
        newPrecalculatedTreeShapes[i] = p_item.getTreeShape(this, i);
        newLeafArr[i] = oldEntries[i];
      }
    }
    p_item.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);
    newLeafArr[p_shape_no] = insert(p_item, p_shape_no);
    p_item.setSearchTreeEntries(newLeafArr, this);
  }

  TileShape[] calculateTreeShapes(DrillItem p_drill_item) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result = new TileShape[p_drill_item.tileShapeCount()];
    for (int i = 0; i < result.length; i++) {
      Shape currShape = p_drill_item.getShape(i);
      if (currShape == null) {
        currShape = drillHoleObstacle(p_drill_item);
      }
      if (currShape == null) {
        result[i] = null;
      } else {
        TileShape currTileShape;
        if (this.board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          currTileShape = currShape.boundingBox();
        } else if (this.board.rules.getTraceAngleRestriction()
            == AngleRestriction.FORTYFIVE_DEGREE) {
          currTileShape = currShape.boundingOctagon();
        } else {
          currTileShape = currShape.boundingTile();
        }
        int offsetWidth =
            this.clearanceCompensationValue(
                p_drill_item.clearanceClassNo(), p_drill_item.shapeLayer(i));
        offsetWidth +=
            drillHoleClearanceDelta(p_drill_item, currShape, p_drill_item.shapeLayer(i));
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
  protected Shape drillHoleObstacle(DrillItem p_drill_item) {
    if (this.board == null
        || this.board.rules == null
        || this.board.rules.getHoleClearance() <= 0
        || p_drill_item.getPadstack() == null) {
      return null;
    }
    double drillRadius = p_drill_item.getPadstack().getDrillRadius();
    if (drillRadius <= 0) {
      return null;
    }
    Point center = p_drill_item.getCenter();
    if (!(center instanceof IntPoint)) {
      center = center.toFloat().round();
    }
    return new Circle((IntPoint) center, (int) Math.ceil(drillRadius));
  }

  /**
   * Extra obstacle inflation so that copper of other nets stays holeClearance away from this item's
   * drill hole (not just its copper pad). Applies to every drilled item — vias, PTH pins and
   * hole-only (NPTH) padstacks alike; returns 0 when the hole-clearance rule is disabled.
   */
  protected int drillHoleClearanceDelta(DrillItem p_drill_item, Shape p_shape, int p_layer) {
    if (this.board == null || this.board.rules == null) {
      return 0;
    }
    int holeClearance = this.board.rules.getHoleClearance();
    if (holeClearance <= 0 || p_shape == null || p_drill_item.getPadstack() == null) {
      return 0;
    }

    double drillRadius = p_drill_item.getPadstack().getDrillRadius();
    if (drillRadius <= 0) {
      return 0;
    }
    double copperRadius;
    if (p_drill_item.getPadstack().holeOnly) {
      copperRadius = drillRadius;
    } else {
      copperRadius = p_shape.borderDistance(p_drill_item.getCenter().toFloat());
      if (copperRadius <= 0) {
        Shape padShape = p_drill_item.getPadstack().getShape(p_layer);
        copperRadius = padShape == null ? drillRadius : padShape.borderDistance(FloatPoint.ZERO);
      }
    }
    int clearanceClass =
        this.compensatedClearanceClassNo > 0
            ? this.compensatedClearanceClassNo
            : BoardRules.defaultClearanceClass();
    int copperClearance =
        this.board.rules.clearanceMatrix.getValue(
            p_drill_item.clearanceClassNo(), clearanceClass, p_layer, false);
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

  TileShape[] calculateTreeShapes(ObstacleArea p_obstacle_area) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] convexShapes = p_obstacle_area.splitToConvex();
    if (convexShapes == null) {
      return new TileShape[0];
    }
    double maxTreeShapeWidth = 50000;
    if (this.board.communication.hostCadExists()) {
      maxTreeShapeWidth =
          Math.min(500 * this.board.communication.getResolution(Unit.MIL), maxTreeShapeWidth);
      // Problem with low resolution on Kicad.
      // Called only for designs from host cad systems because otherwise the old
      // sample.dsn gets to
      // many tree shapes.
    }

    Collection<TileShape> treeShapeList = new LinkedList<>();
    for (int i = 0; i < convexShapes.length; i++) {
      TileShape currConvexShape = convexShapes[i];

      int offsetWidth =
          this.clearanceCompensationValue(
              p_obstacle_area.clearanceClassNo(), p_obstacle_area.getLayer());
      currConvexShape = (TileShape) currConvexShape.enlarge(offsetWidth);
      TileShape[] currTreeShapes = currConvexShape.divideIntoSections(maxTreeShapeWidth);
      treeShapeList.addAll(Arrays.asList(currTreeShapes));
    }
    TileShape[] result = new TileShape[treeShapeList.size()];
    Iterator<TileShape> it = treeShapeList.iterator();
    for (int i = 0; i < result.length; i++) {
      result[i] = it.next();
    }
    return result;
  }

  TileShape[] calculateTreeShapes(BoardOutline p_board_outline) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result;
    if (p_board_outline.keepoutOutsideOutlineGenerated()) {
      TileShape[] convexShapes = p_board_outline.getKeepoutArea().splitToConvex();
      if (convexShapes == null) {
        return new TileShape[0];
      }
      Collection<TileShape> treeShapeList = new LinkedList<>();
      for (int layerNo = 0; layerNo < this.board.layerStructure.arr.length; layerNo++) {
        for (int i = 0; i < convexShapes.length; i++) {
          TileShape currConvexShape = convexShapes[i];
          int offsetWidth =
              this.clearanceCompensationValue(p_board_outline.clearanceClassNo(), layerNo);
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
      result = new TileShape[p_board_outline.lineCount() * this.board.layerStructure.arr.length];
      int halfWidth = p_board_outline.getHalfWidth();
      Line[] currLineArr = new Line[3];
      int currNo = 0;
      for (int layerNo = 0; layerNo < this.board.layerStructure.arr.length; layerNo++) {
        for (int shapeNo = 0; shapeNo < p_board_outline.shapeCount(); shapeNo++) {
          PolylineShape currOutlineShape = p_board_outline.getShape(shapeNo);
          int borderLineCount = currOutlineShape.borderLineCount();
          currLineArr[0] = currOutlineShape.borderLine(borderLineCount - 1);
          for (int i = 0; i < borderLineCount; i++) {
            currLineArr[1] = currOutlineShape.borderLine(i);
            currLineArr[2] = currOutlineShape.borderLine((i + 1) % borderLineCount);
            Polyline tmpPolyline = new Polyline(currLineArr);
            int cmpValue =
                this.clearanceCompensationValue(p_board_outline.clearanceClassNo(), layerNo);
            result[currNo] = tmpPolyline.offsetShape(halfWidth + cmpValue, 0);
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
  TileShape offsetShape(Polyline p_polyline, int p_half_width, int p_no) {
    return p_polyline.offsetShape(p_half_width, p_no);
  }

  /**
   * Used for creating the shapes of a polyline_trace for this tree. Overwritten in derived classes.
   */
  public TileShape[] offsetShapes(
      Polyline p_polyline, int p_half_width, int p_from_no, int p_to_no) {
    return p_polyline.offsetShapes(p_half_width, p_from_no, p_to_no);
  }

  TileShape[] calculateTreeShapes(PolylineTrace p_trace) {
    if (this.board == null) {
      return new TileShape[0];
    }
    int offsetWidth =
        p_trace.getHalfWidth()
            + this.clearanceCompensationValue(p_trace.clearanceClassNo(), p_trace.getLayer());
    TileShape[] result = new TileShape[p_trace.tileShapeCount()];
    for (int i = 0; i < result.length; i++) {
      result[i] = this.offsetShape(p_trace.polyline(), offsetWidth, i);
    }
    return result;
  }

  /**
   * Makes sure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm gets problems with
   * vias.
   */
  protected Collection<IncompleteFreeSpaceExpansionRoom> divideLargeRoom(
      Collection<IncompleteFreeSpaceExpansionRoom> p_room_list, IntBox p_board_bounding_box) {
    if (p_room_list.size() != 1) {
      return p_room_list;
    }
    IncompleteFreeSpaceExpansionRoom currRoom = p_room_list.iterator().next();
    IntBox roomBoundingBox = currRoom.getShape().boundingBox();
    if (2 * roomBoundingBox.height() <= p_board_bounding_box.height()
        || 2 * roomBoundingBox.width() <= p_board_bounding_box.width()) {
      return p_room_list;
    }
    double maxSectionWidth =
        0.5 * Math.max(p_board_bounding_box.height(), p_board_bounding_box.width());
    TileShape[] sectionArr = currRoom.getShape().divideIntoSections(maxSectionWidth);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    for (TileShape currSection : sectionArr) {
      TileShape currShapeToBeContained = currSection.intersection(currRoom.getContainedShape());
      IncompleteFreeSpaceExpansionRoom currSectionRoom =
          new IncompleteFreeSpaceExpansionRoom(
              currSection, currRoom.getLayer(), currShapeToBeContained);
      result.add(currSectionRoom);
    }
    return result;
  }

  boolean validateEntries(Item p_item) {
    Leaf[] currTreeEntries = p_item.getSearchTreeEntries(this);
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
        return Signum.asInt(clearance - p_other.clearance);
      }
      return entryIdNo - p_other.entryIdNo;
    }
  }
}

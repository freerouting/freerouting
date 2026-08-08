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
      ShapeBoundingDirections pDirections, BasicBoard pBoard, int pCompensatedClearanceClassNo) {
    super(pDirections);
    this.compensatedClearanceClassNo = pCompensatedClearanceClassNo;
    board = pBoard;
    key = getKey(this, pDirections, compensatedClearanceClassNo);
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
  public int clearanceCompensationValue(int pClearanceClassNo, int pLayer) {
    if (pClearanceClassNo <= 0) {
      return 0;
    }
    int result =
        board.rules.clearanceMatrix.getValue(
                pClearanceClassNo, this.compensatedClearanceClassNo, pLayer, false)
            - board.rules.clearanceMatrix.clearanceCompensationValue(
                this.compensatedClearanceClassNo, pLayer);
    return Math.max(result, 0);
  }

  /**
   * Changes the tree entries from p_keep_at_start_count + 1 to newShapeCount - 1 - keepAtEndCount
   * to p_changed_entries. Special implementation for change_trace for performance reasons
   */
  void changeEntries(
      PolylineTrace pObj, Polyline pNewPolyline, int pKeepAtStartCount, int pKeepAtEndCount) {
    // calculate the shapes of p_new_polyline from keepAtStartCount to
    // newShapeCount - keepAtEndCount - 1;
    int compensatedHalfWidth =
        pObj.getHalfWidth()
            + this.clearanceCompensationValue(pObj.clearanceClassNo(), pObj.getLayer());
    TileShape[] changedShapes =
        this.offsetShapes(
            pNewPolyline,
            compensatedHalfWidth,
            pKeepAtStartCount,
            pNewPolyline.arr.length - 1 - pKeepAtEndCount);
    int oldShapeCount = pObj.treeShapeCount(this);
    int newShapeCount = changedShapes.length + pKeepAtStartCount + pKeepAtEndCount;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    Leaf[] oldEntries = pObj.getSearchTreeEntries(this);
    for (int i = 0; i < pKeepAtStartCount; i++) {
      newLeafArr[i] = oldEntries[i];
      newPrecalculatedTreeShapes[i] = pObj.getTreeShape(this, i);
    }
    for (int i = pKeepAtStartCount; i < oldShapeCount - pKeepAtEndCount; i++) {
      removeLeaf(oldEntries[i]);
    }
    for (int i = 0; i < pKeepAtEndCount; i++) {
      int newIndex = newShapeCount - pKeepAtEndCount + i;
      int oldIndex = oldShapeCount - pKeepAtEndCount + i;

      newLeafArr[newIndex] = oldEntries[oldIndex];
      newLeafArr[newIndex].shapeIndexInObject = newIndex;
      newPrecalculatedTreeShapes[newIndex] = pObj.getTreeShape(this, oldIndex);
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    System.arraycopy(
        changedShapes, 0, newPrecalculatedTreeShapes, pKeepAtStartCount, changedShapes.length);
    pObj.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    for (int i = pKeepAtStartCount; i < newShapeCount - pKeepAtEndCount; i++) {
      newLeafArr[i] = insert(pObj, i);
    }
    pObj.setSearchTreeEntries(newLeafArr, this);
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
    int compensatedHalfWidth =
        pToTrace.getHalfWidth()
            + this.clearanceCompensationValue(pToTrace.clearanceClassNo(), pToTrace.getLayer());
    TileShape[] linkShapes =
        this.offsetShapes(pJoinedPolyline, compensatedHalfWidth, pFromEntryNo, pToEntryNo);
    boolean changeOrder = pFromTrace.firstCorner().equals(pToTrace.firstCorner());
    // remove the last or first tree entry from p_from_trace and the
    // first tree entry from p_to_trace, because they will be replaced by
    // the new link entries.
    int fromShapeCountMinus1 = pFromTrace.tileShapeCount() - 1;
    int removeNo;
    if (changeOrder) {
      removeNo = 0;
    } else {
      removeNo = fromShapeCountMinus1;
    }
    Leaf[] fromTraceEntries = pFromTrace.getSearchTreeEntries(this);
    Leaf[] toTraceEntries = pToTrace.getSearchTreeEntries(this);
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
      newPrecalculatedTreeShapes[i] = pFromTrace.getTreeShape(this, fromNo);
      newLeafArr[i] = fromTraceEntries[fromNo];
      newLeafArr[i].object = pToTrace;
      newLeafArr[i].shapeIndexInObject = i;
    }
    for (int i = 1; i < oldToShapeCount; i++) {
      int currInd = fromShapeCountMinus1 + linkShapes.length + i - 1;
      newPrecalculatedTreeShapes[currInd] = pToTrace.getTreeShape(this, i);
      newLeafArr[currInd] = toTraceEntries[i];
      newLeafArr[currInd].shapeIndexInObject = currInd;
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = fromShapeCountMinus1 + i;
      newPrecalculatedTreeShapes[currInd] = linkShapes[i];
    }
    pToTrace.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    // create the new link entries
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = fromShapeCountMinus1 + i;
      newLeafArr[currInd] = insert(pToTrace, currInd);
    }

    pToTrace.setSearchTreeEntries(newLeafArr, this);
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
    int compensatedHalfWidth =
        pToTrace.getHalfWidth()
            + this.clearanceCompensationValue(pToTrace.clearanceClassNo(), pToTrace.getLayer());
    TileShape[] linkShapes =
        this.offsetShapes(pJoinedPolyline, compensatedHalfWidth, pFromEntryNo, pToEntryNo);
    boolean changeOrder = pFromTrace.lastCorner().equals(pToTrace.lastCorner());
    Leaf[] fromTraceEntries = pFromTrace.getSearchTreeEntries(this);
    Leaf[] toTraceEntries = pToTrace.getSearchTreeEntries(this);
    // remove the last or first tree entry from p_from_trace and the
    // last tree entry from p_to_trace, because they will be replaced by
    // the new link entries.
    int toShapeCountMinus1 = pToTrace.tileShapeCount() - 1;
    removeLeaf(toTraceEntries[toShapeCountMinus1]);
    int removeNo;
    if (changeOrder) {
      removeNo = pFromTrace.tileShapeCount() - 1;
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
      newPrecalculatedTreeShapes[i] = pToTrace.getTreeShape(this, i);
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
      newPrecalculatedTreeShapes[currInd] = pFromTrace.getTreeShape(this, fromNo);
      newLeafArr[currInd] = fromTraceEntries[fromNo];
      newLeafArr[currInd].object = pToTrace;
      newLeafArr[currInd].shapeIndexInObject = currInd;
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = toShapeCountMinus1 + i;
      newPrecalculatedTreeShapes[currInd] = linkShapes[i];
    }
    pToTrace.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    // create the new link entries
    for (int i = 0; i < linkShapes.length; i++) {
      int currInd = toShapeCountMinus1 + i;
      newLeafArr[currInd] = insert(pToTrace, currInd);
    }
    pToTrace.setSearchTreeEntries(newLeafArr, this);
  }

  /**
   * Transfers tree entries from p_from_trace to p_start and p_end_piece after a middle piece was
   * cut out. Special implementation for ShapeTraceEntries.fast_cutout_trace for performance
   * reasons.
   */
  void reuseEntriesAfterCutout(
      PolylineTrace pFromTrace, PolylineTrace pStartPiece, PolylineTrace pEndPiece) {
    Leaf[] startPieceLeafArr = new Leaf[pStartPiece.polyline().arr.length - 2];
    Leaf[] fromTraceEntries = pFromTrace.getSearchTreeEntries(this);
    // transfer the entries at the start of p_from_trace to p_start_piece.
    for (int i = 0; i < startPieceLeafArr.length - 1; i++) {
      startPieceLeafArr[i] = fromTraceEntries[i];
      startPieceLeafArr[i].object = pStartPiece;
      startPieceLeafArr[i].shapeIndexInObject = i;
      fromTraceEntries[i] = null;
    }
    startPieceLeafArr[startPieceLeafArr.length - 1] =
        insert(pStartPiece, startPieceLeafArr.length - 1);

    // create the last tree entry of the start piece.

    Leaf[] endPieceLeafArr = new Leaf[pEndPiece.polyline().arr.length - 2];

    // create the first tree entry of the end piece.
    endPieceLeafArr[0] = insert(pEndPiece, 0);

    for (int i = 1; i < endPieceLeafArr.length; i++) {
      int fromIndex = fromTraceEntries.length - endPieceLeafArr.length + i;
      endPieceLeafArr[i] = fromTraceEntries[fromIndex];
      endPieceLeafArr[i].object = pEndPiece;
      endPieceLeafArr[i].shapeIndexInObject = i;
      fromTraceEntries[fromIndex] = null;
    }

    pStartPiece.setSearchTreeEntries(startPieceLeafArr, this);
    pEndPiece.setSearchTreeEntries(endPieceLeafArr, this);
  }

  /**
   * Puts all items in the tree overlapping with p_shape on layer p_layer into p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlappingObjects(
      ConvexShape pShape, int pLayer, int[] pIgnoreNetNos, Set<SearchTreeObject> pObstacles) {
    Collection<TreeEntry> treeEntries = new LinkedList<>();
    overlappingTreeEntries(pShape, pLayer, pIgnoreNetNos, treeEntries);
    if (pObstacles != null) {
      for (TreeEntry currentEntry : treeEntries) {
        pObstacles.add((SearchTreeObject) currentEntry.object);
      }
    }
  }

  /**
   * Returns all SearchTreeObjects on layer p_layer, which overlap with p_shape. If p_layer
   * {@literal <} 0, the layer is ignored
   */
  public Set<SearchTreeObject> overlappingObjects(ConvexShape pShape, int pLayer) {
    Set<SearchTreeObject> result = new TreeSet<>();
    this.overlappingObjects(pShape, pLayer, new int[0], result);
    return result;
  }

  /**
   * Puts all tree entries overlapping with p_shape on layer p_layer into the list p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlappingTreeEntries(
      ConvexShape pShape, int pLayer, Collection<TreeEntry> pTreeEntries) {
    overlappingTreeEntries(pShape, pLayer, new int[0], pTreeEntries);
  }

  /**
   * Puts all tree entries overlapping with p_shape on layer p_layer into the list p_obstacles. If
   * p_layer {@literal <} 0, the layer is ignored. treeEntries with object containing a net number
   * of p_ignore_net_nos are ignored.
   */
  public void overlappingTreeEntries(
      ConvexShape pShape, int pLayer, int[] pIgnoreNetNos, Collection<TreeEntry> pTreeEntries) {
    if (pShape == null) {
      return;
    }
    if (pTreeEntries == null) {
      FRLogger.warn("ShapeSearchTree.overlaps: p_obstacle_entries is null");
      return;
    }
    RegularTileShape bounds = pShape.boundingShape(boundingDirections);
    if (bounds == null) {
      FRLogger.warn("ShapeSearchTree.overlaps: p_shape not bounded");
      return;
    }
    Collection<Leaf> tmpList = this.overlaps(bounds);
    boolean is45Degree = pShape instanceof IntOctagon;

    for (Leaf currentLeaf : tmpList) {
      SearchTreeObject currentObject = (SearchTreeObject) currentLeaf.object;
      int shapeIndex = currentLeaf.shapeIndexInObject;
      boolean ignoreObject = pLayer >= 0 && currentObject.shapeLayer(shapeIndex) != pLayer;
      if (!ignoreObject) {
        for (int i = 0; i < pIgnoreNetNos.length; i++) {
          if (!currentObject.isObstacle(pIgnoreNetNos[i])) {
            ignoreObject = true;
          }
        }
      }
      if (!ignoreObject) {
        TileShape currentShape = currentObject.getTreeShape(this, currentLeaf.shapeIndexInObject);
        boolean addItem;
        if (is45Degree && currentShape instanceof IntOctagon)
        // in this case the check for intersection is redundant and
        // therefore skipped for performance reasons
        {
          addItem = true;
        } else {
          addItem = currentShape.intersects(pShape);
        }
        if (addItem) {
          TreeEntry newEntry = new TreeEntry(currentObject, shapeIndex);
          pTreeEntries.add(newEntry);
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
      ConvexShape pShape,
      int pLayer,
      int[] pIgnoreNetNos,
      int pClType,
      Collection<TreeEntry> pObstacleEntries) {
    if (pShape == null) {
      return;
    }
    if (pObstacleEntries == null) {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: p_obstacle_entries is null");
      return;
    }
    ClearanceMatrix clMatrix = board.rules.clearanceMatrix;
    RegularTileShape bounds = pShape.boundingShape(boundingDirections);
    if (bounds == null) {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: p_shape is not bounded");
      bounds = board.getBoundingBox();
    }
    int maxClearance = (int) (1.2 * clMatrix.maxValue(pClType, pLayer));
    // search with the bounds enlarged by the maximum clearance to
    // get all candidates for overlap
    // a factor less than sqr2 has evtl. be added because
    // enlarging is not symmetric.
    RegularTileShape offsetBounds = (RegularTileShape) bounds.offset(maxClearance);
    Collection<Leaf> tmpList = overlaps(offsetBounds);
    // sort the found items by its clearances tp p_cl_type on layer p_layer
    Set<EntrySortedByClearance> sortedItems = new TreeSet<>();

    for (Leaf currentLeaf : tmpList) {
      Item currentItem = (Item) currentLeaf.object;
      int shapeIndex = currentLeaf.shapeIndexInObject;
      boolean ignoreItem = pLayer >= 0 && currentItem.shapeLayer(shapeIndex) != pLayer;
      if (!ignoreItem) {
        for (int i = 0; i < pIgnoreNetNos.length; i++) {
          if (!currentItem.isObstacle(pIgnoreNetNos[i])) {
            ignoreItem = true;
          }
        }
      }
      if (!ignoreItem) {
        int currentClearance =
            clMatrix.getValue(pClType, currentItem.clearanceClassNo(), pLayer, true);
        EntrySortedByClearance sortedOb = new EntrySortedByClearance(currentLeaf, currentClearance);
        sortedItems.add(sortedOb);
      }
    }
    int currentHalfClearance = 0;
    ConvexShape currentOffsetShape = pShape;
    for (EntrySortedByClearance tmpEntry : sortedItems) {
      int tmpHalfClearance = tmpEntry.clearance / 2;
      if (tmpHalfClearance != currentHalfClearance) {
        currentHalfClearance = tmpHalfClearance;
        currentOffsetShape = (TileShape) pShape.enlarge(currentHalfClearance);
      }
      TileShape tmpShape =
          tmpEntry.leaf.object.getTreeShape(this, tmpEntry.leaf.shapeIndexInObject);
      // enlarge both item shapes by the half clearance to create
      // symmetry.
      ConvexShape tmpOffsetShape = (ConvexShape) tmpShape.enlarge(currentHalfClearance);
      if (currentOffsetShape.intersects(tmpOffsetShape)) {
        pObstacleEntries.add(new TreeEntry(tmpEntry.leaf.object, tmpEntry.leaf.shapeIndexInObject));
      }
    }
  }

  /**
   * Puts all items in the tree overlapping with p_shape on layer p_layer into p_obstacles, if
   * p_obstacles != null. If p_layer {@literal <} 0, the layer is ignored.
   */
  public void overlappingObjectsWithClearance(
      ConvexShape pShape,
      int pLayer,
      int[] pIgnoreNetNos,
      int pClType,
      Set<SearchTreeObject> pObstacles) {
    Collection<TreeEntry> treeEntries = new LinkedList<>();
    if (this.isClearanceCompensationUsed()) {
      overlappingTreeEntries(pShape, pLayer, pIgnoreNetNos, treeEntries);
    } else {
      overlappingTreeEntriesWithClearance(pShape, pLayer, pIgnoreNetNos, pClType, treeEntries);
    }
    if (pObstacles == null) {
      return;
    }
    for (TreeEntry currentEntry : treeEntries) {
      pObstacles.add((SearchTreeObject) currentEntry.object);
    }
  }

  /**
   * Returns items, which overlap with p_shape on layer p_layer inclusive clearance.
   * p_clearance_class is the index in the clearance matrix, which describes the required clearance
   * restrictions to other items. The function may also return items, which are nearly overlapping,
   * but do not overlap with exact calculation. If p_layer {@literal <} 0, the layer is ignored.
   */
  public Set<Item> overlappingItemsWithClearance(
      ConvexShape pShape, int pLayer, int[] pIgnoreNetNos, int pClearanceClass) {
    Set<SearchTreeObject> overlaps = new TreeSet<>();

    this.overlappingObjectsWithClearance(pShape, pLayer, pIgnoreNetNos, pClearanceClass, overlaps);
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
      ConvexShape pShape, int pLayer, int[] pIgnoreNetNos, int pClearanceClass) {
    Collection<TreeEntry> result = new LinkedList<>();
    if (this.isClearanceCompensationUsed()) {
      this.overlappingTreeEntries(pShape, pLayer, pIgnoreNetNos, result);
    } else {
      this.overlappingTreeEntriesWithClearance(
          pShape, pLayer, pIgnoreNetNos, pClearanceClass, result);
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
      IncompleteFreeSpaceExpansionRoom pRoom,
      int pNetNo,
      SearchTreeObject pIgnoreObject,
      TileShape pIgnoreShape) {
    if (pRoom.getContainedShape() == null) {
      FRLogger.warn("ShapeSearchTree.complete_shape: p_shape_to_be_contained != null expected");
      return new LinkedList<>();
    }
    if (this.root == null) {
      return new LinkedList<>();
    }
    TileShape startShape = board.getBoundingBox();
    if (pRoom.getShape() != null) {
      startShape = startShape.intersection(pRoom.getShape());
    }
    RegularTileShape boundingShape = startShape.boundingShape(this.boundingDirections);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    if (startShape.dimension() == 2) {
      IncompleteFreeSpaceExpansionRoom newRoom =
          new IncompleteFreeSpaceExpansionRoom(
              startShape, pRoom.getLayer(), pRoom.getContainedShape());
      result.add(newRoom);
    }

    // To ensure exact algorithmic parity with v1.9, we need to visit obstacles
    // in a deterministic order. The non-deterministic order of tree traversal
    // causes different room partitioning.
    List<Leaf> overlappingLeaves = new LinkedList<>();
    ArrayStack<TreeNode> nodeStack = new ArrayStack<>(10000);
    nodeStack.push(this.root);
    TreeNode currNode;
    int roomLayer = pRoom.getLayer();

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
      if (currObject.isTraceObstacle(pNetNo)
          && currObject.shapeLayer(shapeIndex) == roomLayer
          && currObject != pIgnoreObject) {

        TileShape currObjectShape = currObject.getTreeShape(this, shapeIndex);
        Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
        RegularTileShape newBoundingShape = IntOctagon.EMPTY;

        for (IncompleteFreeSpaceExpansionRoom currIncompleteRoom : result) {
          boolean somethingChanged = false;
          TileShape intersection = currIncompleteRoom.getShape().intersection(currObjectShape);
          if (intersection.dimension() == 2) {
            boolean ignoreExpansionRoom =
                currObject instanceof CompleteFreeSpaceExpansionRoom
                    && pIgnoreShape != null
                    && pIgnoreShape.contains(intersection);
            FRLogger.trace(
                "COMPLETE_SHAPE_DECISION"
                    + ", net="
                    + pNetNo
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
                    + (pIgnoreShape == null ? "null" : pIgnoreShape.boundingBox()));

            if (!ignoreExpansionRoom) {
              somethingChanged = true;
              Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
                  restrainShape(currIncompleteRoom, currObjectShape);
              newResult.addAll(newRooms);
              // Keep v1.9 semantics: the bounding shape must include all accumulated rooms.
              for (IncompleteFreeSpaceExpansionRoom tmpRoom : newResult) {
                newBoundingShape =
                    newBoundingShape.union(
                        tmpRoom.getShape().boundingShape(this.boundingDirections));
              }
            }
          }
          if (!somethingChanged) {
            newResult.add(currIncompleteRoom);
            newBoundingShape =
                newBoundingShape.union(
                    currIncompleteRoom.getShape().boundingShape(this.boundingDirections));
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
      IncompleteFreeSpaceExpansionRoom pIncompleteRoom, TileShape pObstacleShape) {
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
    Simplex obstacleSimplex = pObstacleShape.toSimplex();

    TileShape roomShape = pIncompleteRoom.getShape();
    int layer = pIncompleteRoom.getLayer();

    TileShape shapeToBeContained = pIncompleteRoom.getContainedShape();
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
        result.addAll(restrainShape(restIncompleteRoom, pObstacleShape));
      }
    }
    return result;
  }

  /**
   * Reduces the first or last shape of p_trace at a tie pin, so that the autorouter algorithm can
   * find a connection for a different net.
   */
  public void reduceTraceShapeAtTiePin(Pin pTiePin, PolylineTrace pTrace) {
    TileShape pinShape = pTiePin.getTreeShapeOnLayer(this, pTrace.getLayer());
    FloatPoint compareCorner;
    int traceShapeNo;
    if (pTrace.firstCorner().equals(pTiePin.getCenter())) {
      traceShapeNo = 0;
      compareCorner = pTrace.polyline().cornerApprox(1);

    } else if (pTrace.lastCorner().equals(pTiePin.getCenter())) {
      traceShapeNo = pTrace.cornerCount() - 2;
      compareCorner = pTrace.polyline().cornerApprox(pTrace.cornerCount() - 2);
    } else {
      return;
    }
    TileShape traceShape = pTrace.getTreeShape(this, traceShapeNo);
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
    changeItemShape(pTrace, traceShapeNo, newTraceShape);
  }

  /**
   * Changes the shape with index p_shape_no of this item to p_new_shape and updates the entry in
   * the tree.
   */
  void changeItemShape(Item pItem, int pShapeNo, TileShape pNewShape) {
    Leaf[] oldEntries = pItem.getSearchTreeEntries(this);
    Leaf[] newLeafArr = new Leaf[oldEntries.length];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[oldEntries.length];
    removeLeaf(oldEntries[pShapeNo]);
    for (int i = 0; i < newPrecalculatedTreeShapes.length; i++) {
      if (i == pShapeNo) {
        newPrecalculatedTreeShapes[i] = pNewShape;

      } else {
        newPrecalculatedTreeShapes[i] = pItem.getTreeShape(this, i);
        newLeafArr[i] = oldEntries[i];
      }
    }
    pItem.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);
    newLeafArr[pShapeNo] = insert(pItem, pShapeNo);
    pItem.setSearchTreeEntries(newLeafArr, this);
  }

  TileShape[] calculateTreeShapes(DrillItem pDrillItem) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result = new TileShape[pDrillItem.tileShapeCount()];
    for (int i = 0; i < result.length; i++) {
      Shape currShape = pDrillItem.getShape(i);
      if (currShape == null) {
        currShape = drillHoleObstacle(pDrillItem);
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
                pDrillItem.clearanceClassNo(), pDrillItem.shapeLayer(i));
        offsetWidth += drillHoleClearanceDelta(pDrillItem, currShape, pDrillItem.shapeLayer(i));
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
  protected Shape drillHoleObstacle(DrillItem pDrillItem) {
    if (this.board == null
        || this.board.rules == null
        || this.board.rules.getHoleClearance() <= 0
        || pDrillItem.getPadstack() == null) {
      return null;
    }
    double drillRadius = pDrillItem.getPadstack().getDrillRadius();
    if (drillRadius <= 0) {
      return null;
    }
    Point center = pDrillItem.getCenter();
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
  protected int drillHoleClearanceDelta(DrillItem pDrillItem, Shape pShape, int pLayer) {
    if (this.board == null || this.board.rules == null) {
      return 0;
    }
    int holeClearance = this.board.rules.getHoleClearance();
    if (holeClearance <= 0 || pShape == null || pDrillItem.getPadstack() == null) {
      return 0;
    }

    double drillRadius = pDrillItem.getPadstack().getDrillRadius();
    if (drillRadius <= 0) {
      return 0;
    }
    double copperRadius;
    if (pDrillItem.getPadstack().holeOnly) {
      copperRadius = drillRadius;
    } else {
      copperRadius = pShape.borderDistance(pDrillItem.getCenter().toFloat());
      if (copperRadius <= 0) {
        Shape padShape = pDrillItem.getPadstack().getShape(pLayer);
        copperRadius = padShape == null ? drillRadius : padShape.borderDistance(FloatPoint.ZERO);
      }
    }
    int clearanceClass =
        this.compensatedClearanceClassNo > 0
            ? this.compensatedClearanceClassNo
            : BoardRules.defaultClearanceClass();
    int copperClearance =
        this.board.rules.clearanceMatrix.getValue(
            pDrillItem.clearanceClassNo(), clearanceClass, pLayer, false);
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

  TileShape[] calculateTreeShapes(ObstacleArea pObstacleArea) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] convexShapes = pObstacleArea.splitToConvex();
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
              pObstacleArea.clearanceClassNo(), pObstacleArea.getLayer());
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

  TileShape[] calculateTreeShapes(BoardOutline pBoardOutline) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result;
    if (pBoardOutline.keepoutOutsideOutlineGenerated()) {
      TileShape[] convexShapes = pBoardOutline.getKeepoutArea().splitToConvex();
      if (convexShapes == null) {
        return new TileShape[0];
      }
      Collection<TileShape> treeShapeList = new LinkedList<>();
      for (int layerNo = 0; layerNo < this.board.layerStructure.arr.length; layerNo++) {
        for (int i = 0; i < convexShapes.length; i++) {
          TileShape currConvexShape = convexShapes[i];
          int offsetWidth =
              this.clearanceCompensationValue(pBoardOutline.clearanceClassNo(), layerNo);
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
      result = new TileShape[pBoardOutline.lineCount() * this.board.layerStructure.arr.length];
      int halfWidth = pBoardOutline.getHalfWidth();
      Line[] currLineArr = new Line[3];
      int currNo = 0;
      for (int layerNo = 0; layerNo < this.board.layerStructure.arr.length; layerNo++) {
        for (int shapeNo = 0; shapeNo < pBoardOutline.shapeCount(); shapeNo++) {
          PolylineShape currOutlineShape = pBoardOutline.getShape(shapeNo);
          int borderLineCount = currOutlineShape.borderLineCount();
          currLineArr[0] = currOutlineShape.borderLine(borderLineCount - 1);
          for (int i = 0; i < borderLineCount; i++) {
            currLineArr[1] = currOutlineShape.borderLine(i);
            currLineArr[2] = currOutlineShape.borderLine((i + 1) % borderLineCount);
            Polyline tmpPolyline = new Polyline(currLineArr);
            int cmpValue =
                this.clearanceCompensationValue(pBoardOutline.clearanceClassNo(), layerNo);
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
  TileShape offsetShape(Polyline pPolyline, int pHalfWidth, int pNo) {
    return pPolyline.offsetShape(pHalfWidth, pNo);
  }

  /**
   * Used for creating the shapes of a polyline_trace for this tree. Overwritten in derived classes.
   */
  public TileShape[] offsetShapes(Polyline pPolyline, int pHalfWidth, int pFromNo, int pToNo) {
    return pPolyline.offsetShapes(pHalfWidth, pFromNo, pToNo);
  }

  TileShape[] calculateTreeShapes(PolylineTrace pTrace) {
    if (this.board == null) {
      return new TileShape[0];
    }
    int offsetWidth =
        pTrace.getHalfWidth()
            + this.clearanceCompensationValue(pTrace.clearanceClassNo(), pTrace.getLayer());
    TileShape[] result = new TileShape[pTrace.tileShapeCount()];
    for (int i = 0; i < result.length; i++) {
      result[i] = this.offsetShape(pTrace.polyline(), offsetWidth, i);
    }
    return result;
  }

  /**
   * Makes sure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm gets problems with
   * vias.
   */
  protected Collection<IncompleteFreeSpaceExpansionRoom> divideLargeRoom(
      Collection<IncompleteFreeSpaceExpansionRoom> pRoomList, IntBox pBoardBoundingBox) {
    if (pRoomList.size() != 1) {
      return pRoomList;
    }
    IncompleteFreeSpaceExpansionRoom currRoom = pRoomList.iterator().next();
    IntBox roomBoundingBox = currRoom.getShape().boundingBox();
    if (2 * roomBoundingBox.height() <= pBoardBoundingBox.height()
        || 2 * roomBoundingBox.width() <= pBoardBoundingBox.width()) {
      return pRoomList;
    }
    double maxSectionWidth = 0.5 * Math.max(pBoardBoundingBox.height(), pBoardBoundingBox.width());
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

  boolean validateEntries(Item pItem) {
    Leaf[] currTreeEntries = pItem.getSearchTreeEntries(this);
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

    EntrySortedByClearance(Leaf pLeaf, int pClearance) {
      leaf = pLeaf;
      clearance = pClearance;
      if (lastGeneratedIdNo == Integer.MAX_VALUE) {
        lastGeneratedIdNo = 0;
      } else {
        ++lastGeneratedIdNo;
      }
      entryIdNo = lastGeneratedIdNo;
    }

    @Override
    public int compareTo(EntrySortedByClearance pOther) {
      if (clearance != pOther.clearance) {
        return Signum.asInt(clearance - pOther.clearance);
      }
      return entryIdNo - pOther.entryIdNo;
    }
  }
}

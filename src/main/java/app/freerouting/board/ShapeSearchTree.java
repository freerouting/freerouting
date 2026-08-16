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

  /** Used in objects of class EntrySortedByClearance. */
  private static int lastGeneratedIdNo;

  /**
   * The clearance class number for which the shapes of this tree is compensated. If
   * compensatedClearanceClassNo = 0, the shapes are not compensated.
   */
  public final int compensatedClearanceClassNo;

  public final String key;
  protected final BasicBoard board;

  /**
   * Creates a new ShapeSearchTree. compensatedClearanceClassNo is the clearance class number for
   * which the shapes of this tree is compensated. If compensatedClearanceClassNo = 0, the shapes
   * are not compensated.
   */
  ShapeSearchTree(
      ShapeBoundingDirections directions, BasicBoard board, int compensatedClearanceClassNo) {
    super(directions);
    this.compensatedClearanceClassNo = compensatedClearanceClassNo;
    this.board = board;
    key = getKey(this, directions, compensatedClearanceClassNo);
  }

  /** GetKey. */
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
   * Return the clearance compensation value of clearanceClassIndex to the clearance compensation
   * class of this search tree with on layer layer. Returns 0, if no clearance compensation is used
   * for this tree.
   */
  public int clearanceCompensationValue(int clearanceClassIndex, int layer) {
    if (clearanceClassIndex <= 0) {
      return 0;
    }
    int result =
        board.rules.clearanceMatrix.getValue(
                clearanceClassIndex, this.compensatedClearanceClassNo, layer, false)
            - board.rules.clearanceMatrix.clearanceCompensationValue(
                this.compensatedClearanceClassNo, layer);
    return Math.max(result, 0);
  }

  /**
   * Changes the tree entries from keepAtStartCount + 1 to newShapeCount - 1 - keepAtEndCount to
   * changedEntries. Special implementation for change_trace for performance reasons
   */
  void changeEntries(
      PolylineTrace obj, Polyline newPolyline, int keepAtStartCount, int keepAtEndCount) {
    // calculate the shapes of newPolyline from keepAtStartCount to
    // newShapeCount - keepAtEndCount - 1;
    int compensatedHalfWidth =
        obj.getHalfWidth()
            + this.clearanceCompensationValue(obj.clearanceClassIndex(), obj.getLayer());
    TileShape[] changedShapes =
        this.offsetShapes(
            newPolyline,
            compensatedHalfWidth,
            keepAtStartCount,
            newPolyline.lines.length - 1 - keepAtEndCount);
    int oldShapeCount = obj.treeShapeCount(this);
    int newShapeCount = changedShapes.length + keepAtStartCount + keepAtEndCount;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    Leaf[] oldEntries = obj.getSearchTreeEntries(this);
    for (int i = 0; i < keepAtStartCount; i++) {
      newLeafArr[i] = oldEntries[i];
      newPrecalculatedTreeShapes[i] = obj.getTreeShape(this, i);
    }
    for (int i = keepAtStartCount; i < oldShapeCount - keepAtEndCount; i++) {
      removeLeaf(oldEntries[i]);
    }
    for (int i = 0; i < keepAtEndCount; i++) {
      int newIndex = newShapeCount - keepAtEndCount + i;
      int oldIndex = oldShapeCount - keepAtEndCount + i;

      newLeafArr[newIndex] = oldEntries[oldIndex];
      newLeafArr[newIndex].shapeIndexInObject = newIndex;
      newPrecalculatedTreeShapes[newIndex] = obj.getTreeShape(this, oldIndex);
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    System.arraycopy(
        changedShapes, 0, newPrecalculatedTreeShapes, keepAtStartCount, changedShapes.length);
    obj.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    for (int i = keepAtStartCount; i < newShapeCount - keepAtEndCount; i++) {
      newLeafArr[i] = insert(obj, i);
    }
    obj.setSearchTreeEntries(newLeafArr, this);
  }

  /**
   * Merges the tree entries from fromTrace in front of toTrace. Special implementation for combine
   * trace for performance reasons.
   */
  void mergeEntriesInFront(
      PolylineTrace fromTrace,
      PolylineTrace toTrace,
      Polyline joinedPolyline,
      int fromEntryNo,
      int toEntryNo) {
    boolean changeOrder = fromTrace.firstCorner().equals(toTrace.firstCorner());
    // remove the last or first tree entry from fromTrace and the
    // first tree entry from toTrace, because they will be replaced by
    // the new link entries.
    int fromShapeCountMinus1 = fromTrace.tileShapeCount() - 1;
    int removeNo;
    if (changeOrder) {
      removeNo = 0;
    } else {
      removeNo = fromShapeCountMinus1;
    }
    Leaf[] fromTraceEntries = fromTrace.getSearchTreeEntries(this);
    Leaf[] toTraceEntries = toTrace.getSearchTreeEntries(this);
    removeLeaf(fromTraceEntries[removeNo]);
    removeLeaf(toTraceEntries[0]);
    TileShape[] linkShapes =
        this.offsetShapes(
            joinedPolyline,
            toTrace.getHalfWidth()
                + this.clearanceCompensationValue(
                    toTrace.clearanceClassIndex(), toTrace.getLayer()),
            fromEntryNo,
            toEntryNo);
    int newShapeCount = fromTraceEntries.length + linkShapes.length + toTraceEntries.length - 2;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    int oldToShapeCount = toTraceEntries.length;
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    // transfer the tree entries except the last or first from fromTrace to
    // toTrace
    for (int i = 0; i < fromShapeCountMinus1; i++) {
      int fromNo;
      if (changeOrder) {
        fromNo = fromShapeCountMinus1 - i;
      } else {
        fromNo = i;
      }
      newPrecalculatedTreeShapes[i] = fromTrace.getTreeShape(this, fromNo);
      newLeafArr[i] = fromTraceEntries[fromNo];
      newLeafArr[i].object = toTrace;
      newLeafArr[i].shapeIndexInObject = i;
    }
    for (int i = 1; i < oldToShapeCount; i++) {
      int currentInd = fromShapeCountMinus1 + linkShapes.length + i - 1;
      newPrecalculatedTreeShapes[currentInd] = toTrace.getTreeShape(this, i);
      newLeafArr[currentInd] = toTraceEntries[i];
      newLeafArr[currentInd].shapeIndexInObject = currentInd;
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    for (int i = 0; i < linkShapes.length; i++) {
      int currentInd = fromShapeCountMinus1 + i;
      newPrecalculatedTreeShapes[currentInd] = linkShapes[i];
    }
    toTrace.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    // create the new link entries
    for (int i = 0; i < linkShapes.length; i++) {
      int currentInd = fromShapeCountMinus1 + i;
      newLeafArr[currentInd] = insert(toTrace, currentInd);
    }

    toTrace.setSearchTreeEntries(newLeafArr, this);
  }

  /**
   * Merges the tree entries from fromTrace to the end of toTrace. Special implementation for
   * combine trace for performance reasons.
   */
  void mergeEntriesAtEnd(
      PolylineTrace fromTrace,
      PolylineTrace toTrace,
      Polyline joinedPolyline,
      int fromEntryNo,
      int toEntryNo) {
    boolean changeOrder = fromTrace.lastCorner().equals(toTrace.lastCorner());
    Leaf[] fromTraceEntries = fromTrace.getSearchTreeEntries(this);
    Leaf[] toTraceEntries = toTrace.getSearchTreeEntries(this);
    // remove the last or first tree entry from fromTrace and the
    // last tree entry from toTrace, because they will be replaced by
    // the new link entries.
    int toShapeCountMinus1 = toTrace.tileShapeCount() - 1;
    removeLeaf(toTraceEntries[toShapeCountMinus1]);
    int removeNo;
    if (changeOrder) {
      removeNo = fromTrace.tileShapeCount() - 1;
    } else {
      removeNo = 0;
    }
    removeLeaf(fromTraceEntries[removeNo]);
    TileShape[] linkShapes =
        this.offsetShapes(
            joinedPolyline,
            toTrace.getHalfWidth()
                + this.clearanceCompensationValue(
                    toTrace.clearanceClassIndex(), toTrace.getLayer()),
            fromEntryNo,
            toEntryNo);
    int newShapeCount = fromTraceEntries.length + linkShapes.length + toTraceEntries.length - 2;
    Leaf[] newLeafArr = new Leaf[newShapeCount];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[newShapeCount];
    // transfer the tree entries except the last from the old shapes
    // of toTrace to the new shapes of toTrace
    for (int i = 0; i < toShapeCountMinus1; i++) {
      newPrecalculatedTreeShapes[i] = toTrace.getTreeShape(this, i);
      newLeafArr[i] = toTraceEntries[i];
    }

    for (int i = 1; i < fromTraceEntries.length; i++) {
      int currentInd = toShapeCountMinus1 + linkShapes.length + i - 1;
      int fromNo;
      if (changeOrder) {
        fromNo = fromTraceEntries.length - i - 1;
      } else {
        fromNo = i;
      }
      newPrecalculatedTreeShapes[currentInd] = fromTrace.getTreeShape(this, fromNo);
      newLeafArr[currentInd] = fromTraceEntries[fromNo];
      newLeafArr[currentInd].object = toTrace;
      newLeafArr[currentInd].shapeIndexInObject = currentInd;
    }

    // correct the precalculated tree shapes first, because it is used in
    // this.insert
    for (int i = 0; i < linkShapes.length; i++) {
      int currentInd = toShapeCountMinus1 + i;
      newPrecalculatedTreeShapes[currentInd] = linkShapes[i];
    }
    toTrace.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);

    // create the new link entries
    for (int i = 0; i < linkShapes.length; i++) {
      int currentInd = toShapeCountMinus1 + i;
      newLeafArr[currentInd] = insert(toTrace, currentInd);
    }
    toTrace.setSearchTreeEntries(newLeafArr, this);
  }

  /**
   * Transfers tree entries from fromTrace to start and endPiece after a middle piece was cut out.
   * Special implementation for ShapeTraceEntries.fast_cutout_trace for performance reasons.
   */
  void reuseEntriesAfterCutout(
      PolylineTrace fromTrace, PolylineTrace startPiece, PolylineTrace endPiece) {
    Leaf[] startPieceLeafArr = new Leaf[startPiece.polyline().lines.length - 2];
    Leaf[] fromTraceEntries = fromTrace.getSearchTreeEntries(this);
    // transfer the entries at the start of fromTrace to startPiece.
    for (int i = 0; i < startPieceLeafArr.length - 1; i++) {
      startPieceLeafArr[i] = fromTraceEntries[i];
      startPieceLeafArr[i].object = startPiece;
      startPieceLeafArr[i].shapeIndexInObject = i;
      fromTraceEntries[i] = null;
    }
    startPieceLeafArr[startPieceLeafArr.length - 1] =
        insert(startPiece, startPieceLeafArr.length - 1);

    // create the last tree entry of the start piece.

    Leaf[] endPieceLeafArr = new Leaf[endPiece.polyline().lines.length - 2];

    // create the first tree entry of the end piece.
    endPieceLeafArr[0] = insert(endPiece, 0);

    for (int i = 1; i < endPieceLeafArr.length; i++) {
      int fromIndex = fromTraceEntries.length - endPieceLeafArr.length + i;
      endPieceLeafArr[i] = fromTraceEntries[fromIndex];
      endPieceLeafArr[i].object = endPiece;
      endPieceLeafArr[i].shapeIndexInObject = i;
      fromTraceEntries[fromIndex] = null;
    }

    startPiece.setSearchTreeEntries(startPieceLeafArr, this);
    endPiece.setSearchTreeEntries(endPieceLeafArr, this);
  }

  /**
   * Puts all items in the tree overlapping with shape on layer layer into obstacles. If layer
   * {@literal <} 0, the layer is ignored.
   */
  public void overlappingObjects(
      ConvexShape shape, int layer, int[] ignoreNetNos, Set<SearchTreeObject> obstacles) {
    Collection<TreeEntry> treeEntries = new LinkedList<>();
    overlappingTreeEntries(shape, layer, ignoreNetNos, treeEntries);
    if (obstacles != null) {
      for (TreeEntry currentEntry : treeEntries) {
        obstacles.add((SearchTreeObject) currentEntry.object);
      }
    }
  }

  /**
   * Returns all SearchTreeObjects on layer layer, which overlap with shape. If layer {@literal <}
   * 0, the layer is ignored
   */
  public Set<SearchTreeObject> overlappingObjects(ConvexShape shape, int layer) {
    Set<SearchTreeObject> result = new TreeSet<>();
    this.overlappingObjects(shape, layer, new int[0], result);
    return result;
  }

  /**
   * Puts all tree entries overlapping with shape on layer layer into the list obstacles. If layer
   * {@literal <} 0, the layer is ignored.
   */
  public void overlappingTreeEntries(
      ConvexShape shape, int layer, Collection<TreeEntry> treeEntries) {
    overlappingTreeEntries(shape, layer, new int[0], treeEntries);
  }

  /**
   * Puts all tree entries overlapping with shape on layer layer into the list obstacles. If layer
   * {@literal <} 0, the layer is ignored. treeEntries with object containing a net number of
   * ignoreNetNos are ignored.
   */
  public void overlappingTreeEntries(
      ConvexShape shape, int layer, int[] ignoreNetNos, Collection<TreeEntry> treeEntries) {
    if (shape == null) {
      return;
    }
    if (treeEntries == null) {
      FRLogger.warn("ShapeSearchTree.overlaps: obstacleEntries is null");
      return;
    }
    RegularTileShape bounds = shape.boundingShape(boundingDirections);
    if (bounds == null) {
      FRLogger.warn("ShapeSearchTree.overlaps: shape not bounded");
      return;
    }
    Collection<Leaf> tmpList = this.overlaps(bounds);
    boolean is45Degree = shape instanceof IntOctagon;

    for (Leaf currentLeaf : tmpList) {
      SearchTreeObject currentObject = (SearchTreeObject) currentLeaf.object;
      int shapeIndex = currentLeaf.shapeIndexInObject;
      boolean ignoreObject = layer >= 0 && currentObject.shapeLayer(shapeIndex) != layer;
      if (!ignoreObject) {
        for (int i = 0; i < ignoreNetNos.length; i++) {
          if (!currentObject.isObstacle(ignoreNetNos[i])) {
            ignoreObject = true;
          }
        }
      }
      if (!ignoreObject) {
        TileShape currentShape = currentObject.getTreeShape(this, currentLeaf.shapeIndexInObject);
        boolean addItem;
        if (is45Degree && currentShape instanceof IntOctagon) {
          // in this case the check for intersection is redundant and
          // therefore skipped for performance reasons
          addItem = true;
        } else {
          addItem = currentShape.intersects(shape);
        }
        if (addItem) {
          TreeEntry newEntry = new TreeEntry(currentObject, shapeIndex);
          treeEntries.add(newEntry);
        }
      }
    }
  }

  /**
   * Looks up all entries in the search tree, so that inserting an item with shape shape, net number
   * netNumber, clearance type clearanceClassIndex and layer layer would produce a clearance
   * violation, and puts them into the set obstacleEntries. The elements in obstacleEntries are of
   * type TreeEntry. if layer < 0, the layer is ignored. Used only internally, because the clearance
   * compensation is not taken into account.
   */
  void overlappingTreeEntriesWithClearance(
      ConvexShape shape,
      int layer,
      int[] ignoreNetNos,
      int clearanceClassIndex,
      Collection<TreeEntry> obstacleEntries) {
    if (shape == null) {
      return;
    }
    if (obstacleEntries == null) {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: obstacleEntries is null");
      return;
    }
    ClearanceMatrix clMatrix = board.rules.clearanceMatrix;
    RegularTileShape bounds = shape.boundingShape(boundingDirections);
    if (bounds == null) {
      FRLogger.warn("ShapeSearchTree.overlaps_with_clearance: shape is not bounded");
      bounds = board.getBoundingBox();
    }
    int maxClearance = (int) (1.2 * clMatrix.maxValue(clearanceClassIndex, layer));
    // search with the bounds enlarged by the maximum clearance to
    // get all candidates for overlap
    // a factor less than sqr2 has evtl. be added because
    // enlarging is not symmetric.
    RegularTileShape offsetBounds = (RegularTileShape) bounds.offset(maxClearance);
    Collection<Leaf> tmpList = overlaps(offsetBounds);
    // sort the found items by its clearances tp clearanceClassIndex on layer layer
    Set<EntrySortedByClearance> sortedItems = new TreeSet<>();

    for (Leaf currentLeaf : tmpList) {
      Item currentItem = (Item) currentLeaf.object;
      int shapeIndex = currentLeaf.shapeIndexInObject;
      boolean ignoreItem = layer >= 0 && currentItem.shapeLayer(shapeIndex) != layer;
      if (!ignoreItem) {
        for (int i = 0; i < ignoreNetNos.length; i++) {
          if (!currentItem.isObstacle(ignoreNetNos[i])) {
            ignoreItem = true;
          }
        }
      }
      if (!ignoreItem) {
        int currentClearance =
            clMatrix.getValue(clearanceClassIndex, currentItem.clearanceClassIndex(), layer, true);
        EntrySortedByClearance sortedOb = new EntrySortedByClearance(currentLeaf, currentClearance);
        sortedItems.add(sortedOb);
      }
    }
    int currentHalfClearance = 0;
    ConvexShape currentOffsetShape = shape;
    for (EntrySortedByClearance tmpEntry : sortedItems) {
      int tmpHalfClearance = tmpEntry.clearance / 2;
      if (tmpHalfClearance != currentHalfClearance) {
        currentHalfClearance = tmpHalfClearance;
        currentOffsetShape = (ConvexShape) shape.enlarge(currentHalfClearance);
      }
      TileShape tmpShape =
          tmpEntry.leaf.object.getTreeShape(this, tmpEntry.leaf.shapeIndexInObject);
      // enlarge both item shapes by the half clearance to create
      // symmetry.
      ConvexShape tmpOffsetShape = (ConvexShape) tmpShape.enlarge(currentHalfClearance);
      if (currentOffsetShape.intersects(tmpOffsetShape)) {
        obstacleEntries.add(new TreeEntry(tmpEntry.leaf.object, tmpEntry.leaf.shapeIndexInObject));
      }
    }
  }

  /**
   * Returns all objects of class TreeEntry, which overlap with shape on layer layer inclusive
   * clearance. clearanceClassIndex is the index in the clearance matrix, which describes the
   * required clearance restrictions to other items. If layer {@literal <} 0, the layer is ignored.
   */
  public Collection<TreeEntry> overlappingTreeEntriesWithClearance(
      ConvexShape shape, int layer, int[] ignoreNetNos, int clearanceClassIndex) {
    Collection<TreeEntry> result = new LinkedList<>();
    if (this.isClearanceCompensationUsed()) {
      this.overlappingTreeEntries(shape, layer, ignoreNetNos, result);
    } else {
      this.overlappingTreeEntriesWithClearance(
          shape, layer, ignoreNetNos, clearanceClassIndex, result);
    }
    return result;
  }

  /**
   * Puts all items in the tree overlapping with shape on layer layer into obstacles, if obstacles
   * != null. If layer {@literal <} 0, the layer is ignored.
   */
  public void overlappingObjectsWithClearance(
      ConvexShape shape,
      int layer,
      int[] ignoreNetNos,
      int clearanceClassIndex,
      Set<SearchTreeObject> obstacles) {
    Collection<TreeEntry> treeEntries = new LinkedList<>();
    if (this.isClearanceCompensationUsed()) {
      overlappingTreeEntries(shape, layer, ignoreNetNos, treeEntries);
    } else {
      overlappingTreeEntriesWithClearance(
          shape, layer, ignoreNetNos, clearanceClassIndex, treeEntries);
    }
    if (obstacles == null) {
      return;
    }
    for (TreeEntry currentEntry : treeEntries) {
      obstacles.add((SearchTreeObject) currentEntry.object);
    }
  }

  /**
   * Returns items, which overlap with shape on layer layer inclusive clearance. clearanceClassIndex
   * is the index in the clearance matrix, which describes the required clearance restrictions to
   * other items. The function may also return items, which are nearly overlapping, but do not
   * overlap with exact calculation. If layer {@literal <} 0, the layer is ignored.
   */
  public Set<Item> overlappingItemsWithClearance(
      ConvexShape shape, int layer, int[] ignoreNetNos, int clearanceClassIndex) {
    Set<SearchTreeObject> overlaps = new TreeSet<>();

    this.overlappingObjectsWithClearance(shape, layer, ignoreNetNos, clearanceClassIndex, overlaps);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currentObject : overlaps) {
      if (currentObject instanceof Item item) {
        result.add(item);
      }
    }
    return result;
  }

  /**
   * Calculates a new incomplete room with a maximal TileShape contained in the shape of room, which
   * may overlap only with items of the input net on the input layer. room.get_contained_shape()
   * will be contained in the shape of the result room. If that is not possible, several rooms are
   * returned with shapes, which intersect with room.get_contained_shape(). The result room is not
   * yet complete, because its doors are not yet calculated. If ignoreShape != null, objects of type
   * CompleteFreeSpaceExpansionRoom, whose intersection with the shape of room is contained in
   * ignoreShape, are ignored.
   */
  public Collection<IncompleteFreeSpaceExpansionRoom> completeShape(
      IncompleteFreeSpaceExpansionRoom room,
      int netNumber,
      SearchTreeObject ignoreObject,
      TileShape ignoreShape) {
    if (room.getContainedShape() == null) {
      FRLogger.warn("ShapeSearchTree.complete_shape: shapeToBeContained != null expected");
      return new LinkedList<>();
    }
    if (this.root == null) {
      return new LinkedList<>();
    }
    TileShape startShape = board.getBoundingBox();
    if (room.getShape() != null) {
      startShape = startShape.intersection(room.getShape());
    }
    RegularTileShape boundingShape = startShape.boundingShape(this.boundingDirections);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    if (startShape.dimension() == 2) {
      IncompleteFreeSpaceExpansionRoom newRoom =
          new IncompleteFreeSpaceExpansionRoom(
              startShape, room.getLayer(), room.getContainedShape());
      result.add(newRoom);
    }

    // To ensure exact algorithmic parity with v1.9, we need to visit obstacles
    // in a deterministic order. The non-deterministic order of tree traversal
    // causes different room partitioning.
    List<Leaf> overlappingLeaves = new LinkedList<>();
    ArrayStack<TreeNode> nodeStack = new ArrayStack<>(10000);
    nodeStack.push(this.root);
    TreeNode currentNode;
    int roomLayer = room.getLayer();

    while ((currentNode = nodeStack.pop()) != null) {
      if (currentNode.boundingShape.intersects(boundingShape)) {
        if (currentNode instanceof Leaf leaf) {
          overlappingLeaves.add(leaf);
        } else {
          nodeStack.push(((InnerNode) currentNode).firstChild);
          nodeStack.push(((InnerNode) currentNode).secondChild);
        }
      }
    }

    // Sort obstacles to ensure deterministic room partitioning.
    // v1.9's "natural" order was based on its tree structure.
    // Sorting with Leaf's natural comparison (which uses item idNo and shapeIndex)
    // provides a stable visit order.
    Collections.sort(overlappingLeaves);

    for (Leaf currentLeaf : overlappingLeaves) {
      SearchTreeObject currentObject = (SearchTreeObject) currentLeaf.object;
      int shapeIndex = currentLeaf.shapeIndexInObject;
      if (currentObject.isTraceObstacle(netNumber)
          && currentObject.shapeLayer(shapeIndex) == roomLayer
          && currentObject != ignoreObject) {

        TileShape currentObjectShape = currentObject.getTreeShape(this, shapeIndex);
        Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
        RegularTileShape newBoundingShape = IntOctagon.EMPTY;

        for (IncompleteFreeSpaceExpansionRoom currentIncompleteRoom : result) {
          boolean somethingChanged = false;
          TileShape intersection =
              currentIncompleteRoom.getShape().intersection(currentObjectShape);
          if (intersection.dimension() == 2) {
            boolean ignoreExpansionRoom =
                currentObject instanceof CompleteFreeSpaceExpansionRoom
                    && ignoreShape != null
                    && ignoreShape.contains(intersection);
            FRLogger.trace(
                "COMPLETE_SHAPE_DECISION"
                    + ", net="
                    + netNumber
                    + ", layer="
                    + roomLayer
                    + ", action="
                    + (ignoreExpansionRoom ? "IGNORE" : "RESTRAIN")
                    + ", obstacle_type="
                    + currentObject.getClass().getSimpleName()
                    + ", obstacle_bounds="
                    + currentObjectShape.boundingBox()
                    + ", overlap_bounds="
                    + intersection.boundingBox()
                    + ", ignore_bounds="
                    + (ignoreShape == null ? "null" : ignoreShape.boundingBox()));

            if (!ignoreExpansionRoom) {
              somethingChanged = true;
              Collection<IncompleteFreeSpaceExpansionRoom> newRooms =
                  restrainShape(currentIncompleteRoom, currentObjectShape);
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
            newResult.add(currentIncompleteRoom);
            newBoundingShape =
                newBoundingShape.union(
                    currentIncompleteRoom.getShape().boundingShape(this.boundingDirections));
          }
        }
        result = newResult;
        boundingShape = newBoundingShape;
      }
    }
    return divideLargeRoom(result, board.getBoundingBox());
  }

  /**
   * Restrains the shape of incompleteRoom to a TileShape, which does not intersect with the
   * interior of obstacleShape. incompleteRoom.get_contained_shape() must be contained in the shape
   * of the result room. If that is not possible, several rooms are returned with shapes, which
   * intersect with incompleteRoom.get_contained_shape().
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrainShape(
      IncompleteFreeSpaceExpansionRoom incompleteRoom, TileShape obstacleShape) {
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    // Search the edge line of obstacleShape, so that shapeToBeContained
    // are on the right side of this line, and that the line segment
    // intersects with the interior of shape.
    // If there are more than 1 such lines take the line which is
    // furthest away from pointsToBeContained.
    // Then intersect shape with the halfplane defined by the
    // opposite of this line.

    // Always convert to Simplex to match v1.9 semantics - the comment below explains why:
    // "otherwise border_lines of length 0 for octagons may not be handled correctly"
    Simplex obstacleSimplex = obstacleShape.toSimplex();

    TileShape shapeToBeContained = incompleteRoom.getContainedShape();
    if (shapeToBeContained != null) {
      shapeToBeContained =
          shapeToBeContained.toSimplex(); // There may be a performance problem, if a point
      // shape is represented
      // as an octagon
    }
    TileShape roomShape = incompleteRoom.getShape();
    if (shapeToBeContained == null || shapeToBeContained.isEmpty()) {
      FRLogger.trace("ShapeSearchTree.restrain_shape: shapeToBeContained is empty");
      return result;
    }
    int layer = incompleteRoom.getLayer();
    Line cutLine = null;
    double cutLineDistance = -1;

    for (int i = 0; i < obstacleSimplex.borderLineCount(); i++) {
      LineSegment currentLineSegment = new LineSegment(obstacleSimplex, i);
      if (roomShape.isIntersectedInteriorBy(currentLineSegment)) {
        // otherwise currentObject may not touch the intersection
        // of shape with the half_plane defined by the cutLine.
        // That may lead to problems when creating the ExpansionRooms.
        Line currentLine = obstacleSimplex.borderLine(i);

        double currentMinDistance = shapeToBeContained.distanceToTheLeft(currentLine);

        if (currentMinDistance > cutLineDistance) {
          cutLineDistance = currentMinDistance;
          cutLine = currentLine.opposite();
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
      // There is no cut line, so that all shapeToBeContained is
      // completely on the right side of that line. Search a cut line, so that
      // at least part of shapeToBeContained is on the right side.
      if (shapeToBeContained.dimension() < 1) {
        // There is already a completed expansion room around shapeToBeContained.
        return result;
      }

      for (int i = 0; i < obstacleSimplex.borderLineCount(); i++) {
        LineSegment currentLineSegment = new LineSegment(obstacleSimplex, i);
        if (roomShape.isIntersectedInteriorBy(currentLineSegment)) {
          Line currentLine = obstacleSimplex.borderLine(i);
          if (shapeToBeContained.sideOf(currentLine) == Side.COLLINEAR) {
            // currentLine intersects with the interior of shapeToBeContained
            cutLine = currentLine.opposite();
            break;
          }
        }
      }

      if (cutLine == null) {
        // cut line not found, parts or the whole of shape may be already
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
        result.addAll(restrainShape(restIncompleteRoom, obstacleShape));
      }
    }
    return result;
  }

  /**
   * Reduces the first or last shape of trace at a tie pin, so that the autorouter algorithm can
   * find a connection for a different net.
   */
  public void reduceTraceShapeAtTiePin(Pin tiePin, PolylineTrace trace) {
    TileShape pinShape = tiePin.getTreeShapeOnLayer(this, trace.getLayer());
    FloatPoint compareCorner;
    int traceShapeNo;
    if (trace.firstCorner().equals(tiePin.getCenter())) {
      traceShapeNo = 0;
      compareCorner = trace.polyline().cornerApprox(1);

    } else if (trace.lastCorner().equals(tiePin.getCenter())) {
      traceShapeNo = trace.cornerCount() - 2;
      compareCorner = trace.polyline().cornerApprox(trace.cornerCount() - 2);
    } else {
      return;
    }
    TileShape traceShape = trace.getTreeShape(this, traceShapeNo);
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
    changeItemShape(trace, traceShapeNo, newTraceShape);
  }

  /**
   * Changes the shape with index shapeNo of this item to newShape and updates the entry in the
   * tree.
   */
  void changeItemShape(Item item, int shapeNo, TileShape newShape) {
    Leaf[] oldEntries = item.getSearchTreeEntries(this);
    Leaf[] newLeafArr = new Leaf[oldEntries.length];
    TileShape[] newPrecalculatedTreeShapes = new TileShape[oldEntries.length];
    removeLeaf(oldEntries[shapeNo]);
    for (int i = 0; i < newPrecalculatedTreeShapes.length; i++) {
      if (i == shapeNo) {
        newPrecalculatedTreeShapes[i] = newShape;

      } else {
        newPrecalculatedTreeShapes[i] = item.getTreeShape(this, i);
        newLeafArr[i] = oldEntries[i];
      }
    }
    item.setPrecalculatedTreeShapes(newPrecalculatedTreeShapes, this);
    newLeafArr[shapeNo] = insert(item, shapeNo);
    item.setSearchTreeEntries(newLeafArr, this);
  }

  TileShape[] calculateTreeShapes(DrillItem drillItem) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result = new TileShape[drillItem.tileShapeCount()];
    for (int i = 0; i < result.length; i++) {
      Shape currentShape = drillItem.getShape(i);
      if (currentShape == null) {
        currentShape = drillHoleObstacle(drillItem);
      }
      if (currentShape == null) {
        result[i] = null;
      } else {
        TileShape currentTileShape;
        if (this.board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
          currentTileShape = currentShape.boundingBox();
        } else if (this.board.rules.getTraceAngleRestriction()
            == AngleRestriction.FORTYFIVE_DEGREE) {
          currentTileShape = currentShape.boundingOctagon();
        } else {
          currentTileShape = currentShape.boundingTile();
        }
        int offsetWidth =
            this.clearanceCompensationValue(
                drillItem.clearanceClassIndex(), drillItem.shapeLayer(i));
        offsetWidth += drillHoleClearanceDelta(drillItem, currentShape, drillItem.shapeLayer(i));
        if (currentTileShape == null) {
          FRLogger.warn("ShapeSearchTree.calculate_tree_shapes: shape is null");
        } else {
          currentTileShape = (TileShape) currentTileShape.enlarge(offsetWidth);
        }
        result[i] = currentTileShape;
      }
    }
    return result;
  }

  TileShape[] calculateTreeShapes(ObstacleArea obstacleArea) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] convexShapes = obstacleArea.splitToConvex();
    if (convexShapes == null) {
      return new TileShape[0];
    }
    double maxTreeShapeWidth = 50000;
    if (this.board.communication.hostCadExists()) {
      maxTreeShapeWidth =
          Math.min(500 * this.board.communication.getResolution(Unit.MIL), maxTreeShapeWidth);
    }

    Collection<TileShape> treeShapeList = new LinkedList<>();
    for (int i = 0; i < convexShapes.length; i++) {
      TileShape currentConvexShape = convexShapes[i];
      int offsetWidth =
          this.clearanceCompensationValue(
              obstacleArea.clearanceClassIndex(), obstacleArea.getLayer());
      currentConvexShape = (TileShape) currentConvexShape.enlarge(offsetWidth);
      TileShape[] currentTreeShapes = currentConvexShape.divideIntoSections(maxTreeShapeWidth);
      treeShapeList.addAll(Arrays.asList(currentTreeShapes));
    }
    TileShape[] obstacleResult = new TileShape[treeShapeList.size()];
    Iterator<TileShape> it = treeShapeList.iterator();
    for (int i = 0; i < obstacleResult.length; i++) {
      obstacleResult[i] = it.next();
    }
    return obstacleResult;
  }

  TileShape[] calculateTreeShapes(BoardOutline boardOutline) {
    if (this.board == null) {
      return new TileShape[0];
    }
    TileShape[] result;
    if (boardOutline.keepoutOutsideOutlineGenerated()) {
      TileShape[] convexShapes = boardOutline.getKeepoutArea().splitToConvex();
      if (convexShapes == null) {
        return new TileShape[0];
      }
      Collection<TileShape> treeShapeList = new LinkedList<>();
      for (int layerIndex = 0; layerIndex < this.board.layerStructure.layers.length; layerIndex++) {
        for (int i = 0; i < convexShapes.length; i++) {
          TileShape currentConvexShape = convexShapes[i];
          int offsetWidth =
              this.clearanceCompensationValue(boardOutline.clearanceClassIndex(), layerIndex);
          currentConvexShape = (TileShape) currentConvexShape.enlarge(offsetWidth);
          treeShapeList.add(currentConvexShape);
        }
      }
      result = new TileShape[treeShapeList.size()];
      Iterator<TileShape> it = treeShapeList.iterator();
      for (int i = 0; i < result.length; i++) {
        result[i] = it.next();
      }
    } else {
      // Only the line shapes of the outline are inserted as obstacles into the tree.
      result = new TileShape[boardOutline.lineCount() * this.board.layerStructure.layers.length];
      int halfWidth = boardOutline.getHalfWidth();
      Line[] currentLineArr = new Line[3];
      int currentNo = 0;
      for (int layerIndex = 0; layerIndex < this.board.layerStructure.layers.length; layerIndex++) {
        for (int shapeNo = 0; shapeNo < boardOutline.shapeCount(); shapeNo++) {
          PolylineShape currentOutlineShape = boardOutline.getShape(shapeNo);
          int borderLineCount = currentOutlineShape.borderLineCount();
          currentLineArr[0] = currentOutlineShape.borderLine(borderLineCount - 1);
          for (int i = 0; i < borderLineCount; i++) {
            currentLineArr[1] = currentOutlineShape.borderLine(i);
            currentLineArr[2] = currentOutlineShape.borderLine((i + 1) % borderLineCount);
            Polyline tmpPolyline = new Polyline(currentLineArr);
            int cmpValue =
                this.clearanceCompensationValue(boardOutline.clearanceClassIndex(), layerIndex);
            result[currentNo] = tmpPolyline.offsetShape(halfWidth + cmpValue, 0);
            ++currentNo;
            currentLineArr[0] = currentLineArr[1];
          }
        }
      }
    }
    return result;
  }

  TileShape[] calculateTreeShapes(PolylineTrace trace) {
    if (this.board == null) {
      return new TileShape[0];
    }
    int offsetWidth =
        trace.getHalfWidth()
            + this.clearanceCompensationValue(trace.clearanceClassIndex(), trace.getLayer());
    TileShape[] result = new TileShape[trace.tileShapeCount()];
    for (int i = 0; i < result.length; i++) {
      result[i] = this.offsetShape(trace.polyline(), offsetWidth, i);
    }
    return result;
  }

  /**
   * Synthesized obstacle for copper layers where a drilled item has NO pad shape: the drill hole
   * still passes through (e.g. a through-via with unused inner layers), so other-net copper on
   * those layers must keep hole clearance from it. Returns null when the hole-clearance rule is
   * disabled or no drill radius is known.
   */
  protected Shape drillHoleObstacle(DrillItem drillItem) {
    if (this.board == null
        || this.board.rules == null
        || this.board.rules.getHoleClearance() <= 0
        || drillItem.getPadstack() == null) {
      return null;
    }
    double drillRadius = drillItem.getPadstack().getDrillRadius();
    if (drillRadius <= 0) {
      return null;
    }
    Point center = drillItem.getCenter();
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
  protected int drillHoleClearanceDelta(DrillItem drillItem, Shape shape, int layer) {
    if (this.board == null || this.board.rules == null) {
      return 0;
    }
    int holeClearance = this.board.rules.getHoleClearance();
    if (holeClearance <= 0 || shape == null || drillItem.getPadstack() == null) {
      return 0;
    }

    double drillRadius = drillItem.getPadstack().getDrillRadius();
    if (drillRadius <= 0) {
      return 0;
    }
    double copperRadius;
    if (drillItem.getPadstack().holeOnly) {
      copperRadius = drillRadius;
    } else {
      copperRadius = shape.borderDistance(drillItem.getCenter().toFloat());
      if (copperRadius <= 0) {
        Shape padShape = drillItem.getPadstack().getShape(layer);
        copperRadius = padShape == null ? drillRadius : padShape.borderDistance(FloatPoint.ZERO);
      }
    }
    int clearanceClass =
        this.compensatedClearanceClassNo > 0
            ? this.compensatedClearanceClassNo
            : BoardRules.defaultClearanceClass();
    int copperClearance =
        this.board.rules.clearanceMatrix.getValue(
            drillItem.clearanceClassIndex(), clearanceClass, layer, false);
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

  /**
   * Used for creating the shapes of a polyline_trace for this tree. Overwritten in derived classes.
   */
  TileShape offsetShape(Polyline polyline, int halfWidth, int no) {
    return polyline.offsetShape(halfWidth, no);
  }

  /**
   * Used for creating the shapes of a polyline_trace for this tree. Overwritten in derived classes.
   */
  public TileShape[] offsetShapes(Polyline polyline, int halfWidth, int fromNo, int toNo) {
    return polyline.offsetShapes(halfWidth, fromNo, toNo);
  }

  /**
   * Makes sure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm gets problems with
   * vias.
   */
  protected Collection<IncompleteFreeSpaceExpansionRoom> divideLargeRoom(
      Collection<IncompleteFreeSpaceExpansionRoom> roomList, IntBox boardBoundingBox) {
    if (roomList.size() != 1) {
      return roomList;
    }
    IncompleteFreeSpaceExpansionRoom currentRoom = roomList.iterator().next();
    IntBox roomBoundingBox = currentRoom.getShape().boundingBox();
    if (2 * roomBoundingBox.height() <= boardBoundingBox.height()
        || 2 * roomBoundingBox.width() <= boardBoundingBox.width()) {
      return roomList;
    }
    double maxSectionWidth = 0.5 * Math.max(boardBoundingBox.height(), boardBoundingBox.width());
    TileShape[] sectionArr = currentRoom.getShape().divideIntoSections(maxSectionWidth);
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    for (TileShape currentSection : sectionArr) {
      TileShape currentShapeToBeContained =
          currentSection.intersection(currentRoom.getContainedShape());
      IncompleteFreeSpaceExpansionRoom currentSectionRoom =
          new IncompleteFreeSpaceExpansionRoom(
              currentSection, currentRoom.getLayer(), currentShapeToBeContained);
      result.add(currentSectionRoom);
    }
    return result;
  }

  boolean validateEntries(Item item) {
    Leaf[] currentTreeEntries = item.getSearchTreeEntries(this);
    for (int i = 0; i < currentTreeEntries.length; i++) {
      Leaf currentLeaf = currentTreeEntries[i];
      if (currentLeaf.shapeIndexInObject != i) {
        FRLogger.warn("tree entry inconsistent for Item");
        return false;
      }
    }
    return true;
  }

  /**
   * Created for sorting Items according to their clearance to clearanceClassIndex on layer layer.
   */
  private static class EntrySortedByClearance implements Comparable<EntrySortedByClearance> {

    private final int entryIdNo;
    Leaf leaf;
    int clearance;

    EntrySortedByClearance(Leaf leaf, int clearance) {
      this.leaf = leaf;
      this.clearance = clearance;
      if (lastGeneratedIdNo == Integer.MAX_VALUE) {
        lastGeneratedIdNo = 0;
      } else {
        ++lastGeneratedIdNo;
      }
      entryIdNo = lastGeneratedIdNo;
    }

    @Override
    public int compareTo(EntrySortedByClearance other) {
      if (clearance != other.clearance) {
        return Signum.asInt(clearance - other.clearance);
      }
      return entryIdNo - other.entryIdNo;
    }
  }
}

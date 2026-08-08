package app.freerouting.board;

import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.datastructures.ArrayStack;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.OrthogonalBoundingDirections;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A special simple ShapeSearchtree, where the shapes are of class IntBox. It is used in the
 * 90-degree autorouter algorithm.
 */
public class ShapeSearchTree90Degree extends ShapeSearchTree {

  /** Creates a new instance of ShapeSearchTree90Degree */
  public ShapeSearchTree90Degree(BasicBoard pBoard, int pCompensatedClearanceClassNo) {
    super(OrthogonalBoundingDirections.INSTANCE, pBoard, pCompensatedClearanceClassNo);
  }

  /**
   * Calculates a new incomplete room with a maximal TileShape contained in the shape of p_room,
   * which may overlap only with items of the input net on the input layer.
   * p_room.get_contained_shape() will be contained in the shape of the result room. If that is not
   * possible, several rooms are returned with shapes, which intersect with
   * p_room.get_contained_shape(). The result room is not yet complete, because its doors are not
   * yet calculated.
   */
  @Override
  public Collection<IncompleteFreeSpaceExpansionRoom> completeShape(
      IncompleteFreeSpaceExpansionRoom pRoom,
      int pNetNo,
      SearchTreeObject pIgnoreObject,
      TileShape pIgnoreShape) {
    if (!(pRoom.getContainedShape() instanceof IntBox shapeToBeContained)) {
      FRLogger.warn("BoxShapeSearchTree.complete_shape: unexpected p_shape_to_be_contained");
      return new LinkedList<>();
    }
    if (this.root == null) {
      return new LinkedList<>();
    }
    IntBox startShape = board.getBoundingBox();
    if (pRoom.getShape() != null) {
      if (!(pRoom.getShape() instanceof IntBox)) {
        FRLogger.warn("BoxShapeSearchTree.complete_shape: p_start_shape of type IntBox expected");
        return new LinkedList<>();
      }
      startShape = ((IntBox) pRoom.getShape()).intersection(startShape);
    }
    IntBox boundingShape = startShape;
    int roomLayer = pRoom.getLayer();
    boolean debugAnchor = isCompleteShapeDebugAnchor(pNetNo, roomLayer, startShape);
    int debugStep = 0;
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    result.add(new IncompleteFreeSpaceExpansionRoom(startShape, roomLayer, shapeToBeContained));

    // Process obstacles inline during tree traversal with dynamic boundingShape updates.
    // This matches v1.9's algorithm exactly: as obstacles are processed, boundingShape
    // shrinks, which prunes subsequent tree traversal (just like v1.9 does).
    ArrayStack<TreeNode> nodeStack = new ArrayStack<>(10000);
    nodeStack.push(this.root);
    TreeNode currNode;

    for (; ; ) {
      currNode = nodeStack.pop();
      if (currNode == null) {
        break;
      }
      if (currNode.boundingShape.intersects(boundingShape)) {
        if (currNode instanceof Leaf currLeaf) {
          SearchTreeObject currObject = (SearchTreeObject) currLeaf.object;
          int shapeIndex = currLeaf.shapeIndexInObject;
          boolean isObstacle = currObject.isTraceObstacle(pNetNo);
          int objectLayer = currObject.shapeLayer(shapeIndex);
          boolean sameLayer = objectLayer == roomLayer;
          boolean ignoredObject = currObject == pIgnoreObject;
          if (debugAnchor) {
            traceCompleteShapeFilter(
                debugStep,
                pNetNo,
                roomLayer,
                shapeIndex,
                objectLayer,
                isObstacle,
                sameLayer,
                ignoredObject,
                currObject);
          }
          if (isObstacle && sameLayer && !ignoredObject) {

            IntBox currObjectShape = currObject.getTreeShape(this, shapeIndex).boundingBox();
            if (debugAnchor) {
              traceCompleteShapeCandidate(
                  debugStep, pNetNo, roomLayer, currObject, currObjectShape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
            IntBox newBoundingShape = IntBox.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom currRoom : result) {
              IntBox currShape = (IntBox) currRoom.getShape();
              boolean overlaps = currShape.overlaps(currObjectShape);
              if (overlaps) {
                if (currObject instanceof CompleteFreeSpaceExpansionRoom && pIgnoreShape != null) {
                  IntBox intersection = currShape.intersection(currObjectShape);
                  if (pIgnoreShape.contains(intersection)) {
                    if (debugAnchor) {
                      traceCompleteShapeDecision(
                          debugStep,
                          pNetNo,
                          roomLayer,
                          "SKIP_BY_IGNORE_SHAPE",
                          overlaps,
                          currShape,
                          currObjectShape);
                    }
                    // ignore also all objects, whose intersection is contained in the
                    // 2-dim overlap-door with the fromRoom.
                    continue;
                  }
                }
                if (debugAnchor) {
                  traceCompleteShapeDecision(
                      debugStep,
                      pNetNo,
                      roomLayer,
                      "RESTRAIN",
                      overlaps,
                      currShape,
                      currObjectShape);
                }
                Collection<IncompleteFreeSpaceExpansionRoom> newRestrainedShapes =
                    restrainShape(currRoom, currObjectShape);
                newResult.addAll(newRestrainedShapes);

                for (IncompleteFreeSpaceExpansionRoom tmpShape : newResult) {
                  newBoundingShape = newBoundingShape.union(tmpShape.getShape().boundingBox());
                }
              } else {
                if (debugAnchor) {
                  traceCompleteShapeDecision(
                      debugStep,
                      pNetNo,
                      roomLayer,
                      "KEEP_NON_OVERLAP",
                      overlaps,
                      currShape,
                      currObjectShape);
                }
                newResult.add(currRoom);
                newBoundingShape = newBoundingShape.union(currShape.boundingBox());
              }
            }
            if (hadRoomsBeforeObstacle && newResult.isEmpty()) {
              FRLogger.trace(
                  "COMPLETE_SHAPE_BLOCKED net="
                      + pNetNo
                      + ", layer="
                      + roomLayer
                      + ", contained="
                      + describeBounds(shapeToBeContained)
                      + ", obstacle_type="
                      + currObject.getClass().getSimpleName()
                      + ", obstacle_id="
                      + obstacleId(currObject)
                      + ", obstacle_bounds="
                      + describeBounds(currObjectShape));
            }
            result = newResult;
            boundingShape = newBoundingShape;
          }
          if (debugAnchor) {
            debugStep++;
          }
        } else {
          nodeStack.push(((InnerNode) currNode).firstChild);
          nodeStack.push(((InnerNode) currNode).secondChild);
        }
      }
    }
    return result;
  }

  /**
   * Restrains the shape of p_incomplete_room to a box shape, which does not intersect with the
   * interior of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be contained in the
   * shape of the result room.
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrainShape(
      IncompleteFreeSpaceExpansionRoom pIncompleteRoom, IntBox pObstacleShape) {
    // Search the edge line of p_obstacle_shape, so that p_shape_to_be_contained
    // are on the right side of this line, and that the line segment
    // intersects with the interior of p_shape.
    // If there are more than 1 such lines take the line which is
    // furthest away from the shapeToBeContained
    // Then intersect p_shape with the halfplane defined by the
    // opposite of this line.

    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();

    TileShape containedShape = pIncompleteRoom.getContainedShape();
    if (containedShape == null || containedShape.isEmpty()) {
      FRLogger.trace("BoxShapeSearchTree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    IntBox roomShape = pIncompleteRoom.getShape().boundingBox();
    IntBox shapeToBeContained = pIncompleteRoom.getContainedShape().boundingBox();
    int cutLineDistance = 0;
    IntBox restrainedShape = null;

    if (roomShape.ll.x < pObstacleShape.ur.x
        && roomShape.ur.x > pObstacleShape.ur.x
        && roomShape.ur.y > pObstacleShape.ll.y
        && roomShape.ll.y < pObstacleShape.ur.y) {
      // The right line segment of the obstacleShape intersects the interior of
      // p_shape
      int currDistance = shapeToBeContained.ll.x - pObstacleShape.ur.x;
      if (currDistance > cutLineDistance) {
        cutLineDistance = currDistance;
        restrainedShape =
            new IntBox(pObstacleShape.ur.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
      }
    }
    if (roomShape.ll.x < pObstacleShape.ll.x
        && roomShape.ur.x > pObstacleShape.ll.x
        && roomShape.ur.y > pObstacleShape.ll.y
        && roomShape.ll.y < pObstacleShape.ur.y) {
      // The left line segment of the obstacleShape intersects the interior of
      // p_shape
      int currDistance = pObstacleShape.ll.x - shapeToBeContained.ur.x;
      if (currDistance > cutLineDistance) {
        cutLineDistance = currDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, roomShape.ll.y, pObstacleShape.ll.x, roomShape.ur.y);
      }
    }
    if (roomShape.ll.y < pObstacleShape.ll.y
        && roomShape.ur.y > pObstacleShape.ll.y
        && roomShape.ur.x > pObstacleShape.ll.x
        && roomShape.ll.x < pObstacleShape.ur.x) {
      // The lower line segment of the obstacleShape intersects the interior of
      // p_shape
      int currDistance = pObstacleShape.ll.y - shapeToBeContained.ur.y;
      if (currDistance > cutLineDistance) {
        cutLineDistance = currDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, pObstacleShape.ll.y);
      }
    }
    if (roomShape.ll.y < pObstacleShape.ur.y
        && roomShape.ur.y > pObstacleShape.ur.y
        && roomShape.ur.x > pObstacleShape.ll.x
        && roomShape.ll.x < pObstacleShape.ur.x) {
      // The upper line segment of the obstacleShape intersects the interior of
      // p_shape
      int currDistance = shapeToBeContained.ll.y - pObstacleShape.ur.y;
      if (currDistance > cutLineDistance) {
        cutLineDistance = currDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, pObstacleShape.ur.y, roomShape.ur.x, roomShape.ur.y);
      }
    }
    if (restrainedShape != null) {
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrainedShape, pIncompleteRoom.getLayer(), shapeToBeContained));
      return result;
    }

    // Now shapeToBeContained intersects with the obstacleShape.
    // shapeToBeContained and p_shape evtl. need to be divided in two.
    IntBox is = shapeToBeContained.intersection(pObstacleShape);
    if (is.isEmpty()) {
      FRLogger.warn(
          "BoxShapeSearchTree.restrain_shape: Intersection between obstacleShape and shapeToBeContained expected");
      return result;
    }
    IntBox newShape1 = null;
    IntBox newShape2 = null;
    if (is.ll.x > roomShape.ll.x && is.ll.x == pObstacleShape.ll.x && is.ll.x < roomShape.ur.x) {
      newShape1 = new IntBox(roomShape.ll.x, roomShape.ll.y, is.ll.x, roomShape.ur.y);
      newShape2 = new IntBox(is.ll.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ur.x > roomShape.ll.x
        && is.ur.x == pObstacleShape.ur.x
        && is.ur.x < roomShape.ur.x) {
      newShape2 = new IntBox(roomShape.ll.x, roomShape.ll.y, is.ur.x, roomShape.ur.y);
      newShape1 = new IntBox(is.ur.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ll.y > roomShape.ll.y
        && is.ll.y == pObstacleShape.ll.y
        && is.ll.y < roomShape.ur.y) {
      newShape1 = new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, is.ll.y);
      newShape2 = new IntBox(roomShape.ll.x, is.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ur.y > roomShape.ll.y
        && is.ur.y == pObstacleShape.ur.y
        && is.ur.y < roomShape.ur.y) {
      newShape2 = new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, is.ur.y);
      newShape1 = new IntBox(roomShape.ll.x, is.ur.y, roomShape.ur.x, roomShape.ur.y);
    }
    if (newShape1 != null) {
      IntBox newShapeToBeContained = shapeToBeContained.intersection(newShape1);
      if (newShapeToBeContained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                newShape1, pIncompleteRoom.getLayer(), newShapeToBeContained));
        IncompleteFreeSpaceExpansionRoom newIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(
                newShape2, pIncompleteRoom.getLayer(), shapeToBeContained.intersection(newShape2));
        result.addAll(restrainShape(newIncompleteRoom, pObstacleShape));
      }
    }
    return result;
  }

  private static String describeBounds(IntBox pBounds) {
    return "[("
        + pBounds.ll.x
        + ","
        + pBounds.ll.y
        + ")..("
        + pBounds.ur.x
        + ","
        + pBounds.ur.y
        + ")]";
  }

  /**
   * Returns true for the specific room being diagnosed in the current parity investigation. Update
   * these coordinates to anchor detailed per-leaf logging to a different room.
   */
  private static boolean isCompleteShapeDebugAnchor(
      int pNetNo, int pRoomLayer, IntBox pStartShape) {
    return pNetNo == 84
        && pRoomLayer == 0
        && pStartShape.ll.x == 1767436
        && pStartShape.ll.y == -1206395
        && pStartShape.ur.x == 1994010
        && pStartShape.ur.y == -782336;
  }

  private static void traceCompleteShapeFilter(
      int pStep,
      int pNetNo,
      int pRoomLayer,
      int pShapeIndex,
      int pObjectLayer,
      boolean pIsObstacle,
      boolean pSameLayer,
      boolean pIgnoredObject,
      SearchTreeObject pObject) {
    FRLogger.trace(
        "COMPLETE_SHAPE_FILTER"
            + ", step="
            + pStep
            + ", net="
            + pNetNo
            + ", layer="
            + pRoomLayer
            + ", shapeIndex="
            + pShapeIndex
            + ", object_layer="
            + pObjectLayer
            + ", is_trace_obstacle="
            + pIsObstacle
            + ", same_layer="
            + pSameLayer
            + ", ignored_object="
            + pIgnoredObject
            + ", accepted="
            + (pIsObstacle && pSameLayer && !pIgnoredObject)
            + ", obstacle_id="
            + obstacleId(pObject)
            + ", obstacle_nets="
            + obstacleNets(pObject)
            + ", obstacle="
            + pObject);
  }

  private static void traceCompleteShapeCandidate(
      int pStep, int pNetNo, int pRoomLayer, SearchTreeObject pObject, IntBox pObstacleShape) {
    FRLogger.trace(
        "COMPLETE_SHAPE_OBS candidate"
            + ", step="
            + pStep
            + ", net="
            + pNetNo
            + ", layer="
            + pRoomLayer
            + ", obstacle="
            + pObject
            + ", obstacle_id="
            + obstacleId(pObject)
            + ", obstacle_nets="
            + obstacleNets(pObject)
            + ", obstacle_bounds="
            + describeBounds(pObstacleShape));
  }

  private static void traceCompleteShapeDecision(
      int pStep,
      int pNetNo,
      int pRoomLayer,
      String pAction,
      boolean pOverlap,
      IntBox pRoomShape,
      IntBox pObstacleShape) {
    FRLogger.trace(
        "COMPLETE_SHAPE_DECISION"
            + ", step="
            + pStep
            + ", net="
            + pNetNo
            + ", layer="
            + pRoomLayer
            + ", action="
            + pAction
            + ", overlap="
            + pOverlap
            + ", room_bounds="
            + describeBounds(pRoomShape)
            + ", obstacle_bounds="
            + describeBounds(pObstacleShape));
  }

  private static int obstacleId(SearchTreeObject pObject) {
    return pObject instanceof Item item ? item.getIdNo() : -1;
  }

  private static String obstacleNets(SearchTreeObject pObject) {
    return pObject instanceof Item item ? java.util.Arrays.toString(item.netNoArr) : "[]";
  }

  @Override
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
        IntBox currTileShape = currShape.boundingBox();
        int offsetWidth =
            this.clearanceCompensationValue(
                pDrillItem.clearanceClassNo(), pDrillItem.shapeLayer(i));
        offsetWidth += drillHoleClearanceDelta(pDrillItem, currShape, pDrillItem.shapeLayer(i));
        if (currTileShape == null) {
          FRLogger.warn("BoxShapeSearchTree.calculate_tree_shapes: shape is null");
        } else {
          currTileShape = currTileShape.offset(offsetWidth);
        }
        result[i] = currTileShape;
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(ObstacleArea pObstacleArea) {
    TileShape[] result = super.calculateTreeShapes(pObstacleArea);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingBox();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(BoardOutline pOutline) {
    TileShape[] result = super.calculateTreeShapes(pOutline);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingBox();
      }
    }
    return result;
  }

  /** Used for creating the shapes of a polyline_trace for this tree. */
  @Override
  TileShape offsetShape(Polyline pPolyline, int pHalfWidth, int pNo) {
    return pPolyline.offsetBox(pHalfWidth, pNo);
  }

  /** Used for creating the shapes of a polyline_trace for this tree. */
  @Override
  public TileShape[] offsetShapes(Polyline pPolyline, int pHalfWidth, int pFromNo, int pToNo) {
    int fromNo = Math.max(pFromNo, 0);
    int toNo = Math.min(pToNo, pPolyline.arr.length - 1);
    int shapeCount = Math.max(toNo - fromNo - 1, 0);
    TileShape[] shapeArr = new TileShape[shapeCount];
    for (int j = fromNo; j < toNo - 1; j++) {
      shapeArr[j - fromNo] = pPolyline.offsetBox(pHalfWidth, j);
    }
    return shapeArr;
  }
}

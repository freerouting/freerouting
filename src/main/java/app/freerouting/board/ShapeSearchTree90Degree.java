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

  /** Creates a new instance of ShapeSearchTree90Degree. */
  public ShapeSearchTree90Degree(BasicBoard board, int compensatedClearanceClassNo) {
    super(OrthogonalBoundingDirections.INSTANCE, board, compensatedClearanceClassNo);
  }

  /**
   * Calculates a new incomplete room with a maximal TileShape contained in the shape of room, which
   * may overlap only with items of the input net on the input layer. room.get_contained_shape()
   * will be contained in the shape of the result room. If that is not possible, several rooms are
   * returned with shapes, which intersect with room.get_contained_shape(). The result room is not
   * yet complete, because its doors are not yet calculated.
   */
  @Override
  public Collection<IncompleteFreeSpaceExpansionRoom> completeShape(
      IncompleteFreeSpaceExpansionRoom room,
      int netNo,
      SearchTreeObject ignoreObject,
      TileShape ignoreShape) {
    if (!(room.getContainedShape() instanceof IntBox shapeToBeContained)) {
      FRLogger.warn("BoxShapeSearchTree.complete_shape: unexpected shapeToBeContained");
      return new LinkedList<>();
    }
    if (this.root == null) {
      return new LinkedList<>();
    }
    IntBox startShape = board.getBoundingBox();
    if (room.getShape() != null) {
      if (!(room.getShape() instanceof IntBox)) {
        FRLogger.warn("BoxShapeSearchTree.complete_shape: startShape of type IntBox expected");
        return new LinkedList<>();
      }
      startShape = ((IntBox) room.getShape()).intersection(startShape);
    }
    IntBox boundingShape = startShape;
    int roomLayer = room.getLayer();
    boolean debugAnchor = isCompleteShapeDebugAnchor(netNo, roomLayer, startShape);
    int debugStep = 0;
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    result.add(new IncompleteFreeSpaceExpansionRoom(startShape, roomLayer, shapeToBeContained));

    // Process obstacles inline during tree traversal with dynamic boundingShape updates.
    // This matches v1.9's algorithm exactly: as obstacles are processed, boundingShape
    // shrinks, which prunes subsequent tree traversal (just like v1.9 does).
    ArrayStack<TreeNode> nodeStack = new ArrayStack<>(10000);
    nodeStack.push(this.root);
    TreeNode currentNode;

    for (; ; ) {
      currentNode = nodeStack.pop();
      if (currentNode == null) {
        break;
      }
      if (currentNode.boundingShape.intersects(boundingShape)) {
        if (currentNode instanceof Leaf currentLeaf) {
          SearchTreeObject currentObject = (SearchTreeObject) currentLeaf.object;
          int shapeIndex = currentLeaf.shapeIndexInObject;
          boolean isObstacle = currentObject.isTraceObstacle(netNo);
          int objectLayer = currentObject.shapeLayer(shapeIndex);
          boolean sameLayer = objectLayer == roomLayer;
          boolean ignoredObject = currentObject == ignoreObject;
          if (debugAnchor) {
            traceCompleteShapeFilter(
                debugStep,
                netNo,
                roomLayer,
                shapeIndex,
                objectLayer,
                isObstacle,
                sameLayer,
                ignoredObject,
                currentObject);
          }
          if (isObstacle && sameLayer && !ignoredObject) {

            IntBox currentObjectShape = currentObject.getTreeShape(this, shapeIndex).boundingBox();
            if (debugAnchor) {
              traceCompleteShapeCandidate(
                  debugStep, netNo, roomLayer, currentObject, currentObjectShape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
            IntBox newBoundingShape = IntBox.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom currentRoom : result) {
              IntBox currentShape = (IntBox) currentRoom.getShape();
              boolean overlaps = currentShape.overlaps(currentObjectShape);
              if (overlaps) {
                if (currentObject instanceof CompleteFreeSpaceExpansionRoom
                    && ignoreShape != null) {
                  IntBox intersection = currentShape.intersection(currentObjectShape);
                  if (ignoreShape.contains(intersection)) {
                    if (debugAnchor) {
                      traceCompleteShapeDecision(
                          debugStep,
                          netNo,
                          roomLayer,
                          "SKIP_BY_IGNORE_SHAPE",
                          overlaps,
                          currentShape,
                          currentObjectShape);
                    }
                    // ignore also all objects, whose intersection is contained in the
                    // 2-dim overlap-door with the fromRoom.
                    continue;
                  }
                }
                if (debugAnchor) {
                  traceCompleteShapeDecision(
                      debugStep,
                      netNo,
                      roomLayer,
                      "RESTRAIN",
                      overlaps,
                      currentShape,
                      currentObjectShape);
                }
                Collection<IncompleteFreeSpaceExpansionRoom> newRestrainedShapes =
                    restrainShape(currentRoom, currentObjectShape);
                newResult.addAll(newRestrainedShapes);

                for (IncompleteFreeSpaceExpansionRoom tmpShape : newResult) {
                  newBoundingShape = newBoundingShape.union(tmpShape.getShape().boundingBox());
                }
              } else {
                if (debugAnchor) {
                  traceCompleteShapeDecision(
                      debugStep,
                      netNo,
                      roomLayer,
                      "KEEP_NON_OVERLAP",
                      overlaps,
                      currentShape,
                      currentObjectShape);
                }
                newResult.add(currentRoom);
                newBoundingShape = newBoundingShape.union(currentShape.boundingBox());
              }
            }
            if (hadRoomsBeforeObstacle && newResult.isEmpty()) {
              FRLogger.trace(
                  "COMPLETE_SHAPE_BLOCKED net="
                      + netNo
                      + ", layer="
                      + roomLayer
                      + ", contained="
                      + describeBounds(shapeToBeContained)
                      + ", obstacle_type="
                      + currentObject.getClass().getSimpleName()
                      + ", obstacle_id="
                      + obstacleId(currentObject)
                      + ", obstacle_bounds="
                      + describeBounds(currentObjectShape));
            }
            result = newResult;
            boundingShape = newBoundingShape;
          }
          if (debugAnchor) {
            debugStep++;
          }
        } else {
          nodeStack.push(((InnerNode) currentNode).firstChild);
          nodeStack.push(((InnerNode) currentNode).secondChild);
        }
      }
    }
    return result;
  }

  /**
   * Restrains the shape of incompleteRoom to a box shape, which does not intersect with the
   * interior of obstacleShape. incompleteRoom.get_contained_shape() must be contained in the shape
   * of the result room.
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrainShape(
      IncompleteFreeSpaceExpansionRoom incompleteRoom, IntBox obstacleShape) {
    // Search the edge line of obstacleShape, so that shapeToBeContained
    // are on the right side of this line, and that the line segment
    // intersects with the interior of shape.
    // If there are more than 1 such lines take the line which is
    // furthest away from the shapeToBeContained
    // Then intersect shape with the halfplane defined by the
    // opposite of this line.

    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();

    TileShape containedShape = incompleteRoom.getContainedShape();
    if (containedShape == null || containedShape.isEmpty()) {
      FRLogger.trace("BoxShapeSearchTree.restrain_shape: shapeToBeContained is empty");
      return result;
    }
    IntBox roomShape = incompleteRoom.getShape().boundingBox();
    IntBox shapeToBeContained = incompleteRoom.getContainedShape().boundingBox();
    int cutLineDistance = 0;
    IntBox restrainedShape = null;

    if (roomShape.ll.x < obstacleShape.ur.x
        && roomShape.ur.x > obstacleShape.ur.x
        && roomShape.ur.y > obstacleShape.ll.y
        && roomShape.ll.y < obstacleShape.ur.y) {
      // The right line segment of the obstacleShape intersects the interior of
      // shape
      int currentDistance = shapeToBeContained.ll.x - obstacleShape.ur.x;
      if (currentDistance > cutLineDistance) {
        cutLineDistance = currentDistance;
        restrainedShape =
            new IntBox(obstacleShape.ur.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
      }
    }
    if (roomShape.ll.x < obstacleShape.ll.x
        && roomShape.ur.x > obstacleShape.ll.x
        && roomShape.ur.y > obstacleShape.ll.y
        && roomShape.ll.y < obstacleShape.ur.y) {
      // The left line segment of the obstacleShape intersects the interior of
      // shape
      int currentDistance = obstacleShape.ll.x - shapeToBeContained.ur.x;
      if (currentDistance > cutLineDistance) {
        cutLineDistance = currentDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, roomShape.ll.y, obstacleShape.ll.x, roomShape.ur.y);
      }
    }
    if (roomShape.ll.y < obstacleShape.ll.y
        && roomShape.ur.y > obstacleShape.ll.y
        && roomShape.ur.x > obstacleShape.ll.x
        && roomShape.ll.x < obstacleShape.ur.x) {
      // The lower line segment of the obstacleShape intersects the interior of
      // shape
      int currentDistance = obstacleShape.ll.y - shapeToBeContained.ur.y;
      if (currentDistance > cutLineDistance) {
        cutLineDistance = currentDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, obstacleShape.ll.y);
      }
    }
    if (roomShape.ll.y < obstacleShape.ur.y
        && roomShape.ur.y > obstacleShape.ur.y
        && roomShape.ur.x > obstacleShape.ll.x
        && roomShape.ll.x < obstacleShape.ur.x) {
      // The upper line segment of the obstacleShape intersects the interior of
      // shape
      int currentDistance = shapeToBeContained.ll.y - obstacleShape.ur.y;
      if (currentDistance > cutLineDistance) {
        cutLineDistance = currentDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, obstacleShape.ur.y, roomShape.ur.x, roomShape.ur.y);
      }
    }
    if (restrainedShape != null) {
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrainedShape, incompleteRoom.getLayer(), shapeToBeContained));
      return result;
    }

    // Now shapeToBeContained intersects with the obstacleShape.
    // shapeToBeContained and shape evtl. need to be divided in two.
    IntBox is = shapeToBeContained.intersection(obstacleShape);
    if (is.isEmpty()) {
      FRLogger.warn(
          "BoxShapeSearchTree.restrain_shape: Intersection between obstacleShape"
              + " and shapeToBeContained expected");
      return result;
    }
    IntBox newShape1 = null;
    IntBox newShape2 = null;
    if (is.ll.x > roomShape.ll.x && is.ll.x == obstacleShape.ll.x && is.ll.x < roomShape.ur.x) {
      newShape1 = new IntBox(roomShape.ll.x, roomShape.ll.y, is.ll.x, roomShape.ur.y);
      newShape2 = new IntBox(is.ll.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ur.x > roomShape.ll.x
        && is.ur.x == obstacleShape.ur.x
        && is.ur.x < roomShape.ur.x) {
      newShape2 = new IntBox(roomShape.ll.x, roomShape.ll.y, is.ur.x, roomShape.ur.y);
      newShape1 = new IntBox(is.ur.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ll.y > roomShape.ll.y
        && is.ll.y == obstacleShape.ll.y
        && is.ll.y < roomShape.ur.y) {
      newShape1 = new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, is.ll.y);
      newShape2 = new IntBox(roomShape.ll.x, is.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ur.y > roomShape.ll.y
        && is.ur.y == obstacleShape.ur.y
        && is.ur.y < roomShape.ur.y) {
      newShape2 = new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, is.ur.y);
      newShape1 = new IntBox(roomShape.ll.x, is.ur.y, roomShape.ur.x, roomShape.ur.y);
    }
    if (newShape1 != null) {
      IntBox newShapeToBeContained = shapeToBeContained.intersection(newShape1);
      if (newShapeToBeContained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                newShape1, incompleteRoom.getLayer(), newShapeToBeContained));
        IncompleteFreeSpaceExpansionRoom newIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(
                newShape2, incompleteRoom.getLayer(), shapeToBeContained.intersection(newShape2));
        result.addAll(restrainShape(newIncompleteRoom, obstacleShape));
      }
    }
    return result;
  }

  private static String describeBounds(IntBox bounds) {
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  /**
   * Returns true for the specific room being diagnosed in the current parity investigation. Update
   * these coordinates to anchor detailed per-leaf logging to a different room.
   */
  private static boolean isCompleteShapeDebugAnchor(int netNo, int roomLayer, IntBox startShape) {
    return netNo == 84
        && roomLayer == 0
        && startShape.ll.x == 1767436
        && startShape.ll.y == -1206395
        && startShape.ur.x == 1994010
        && startShape.ur.y == -782336;
  }

  private static void traceCompleteShapeFilter(
      int step,
      int netNo,
      int roomLayer,
      int shapeIndex,
      int objectLayer,
      boolean isObstacle,
      boolean sameLayer,
      boolean ignoredObject,
      SearchTreeObject object) {
    FRLogger.trace(
        "COMPLETE_SHAPE_FILTER"
            + ", step="
            + step
            + ", net="
            + netNo
            + ", layer="
            + roomLayer
            + ", shapeIndex="
            + shapeIndex
            + ", object_layer="
            + objectLayer
            + ", is_trace_obstacle="
            + isObstacle
            + ", same_layer="
            + sameLayer
            + ", ignored_object="
            + ignoredObject
            + ", accepted="
            + (isObstacle && sameLayer && !ignoredObject)
            + ", obstacle_id="
            + obstacleId(object)
            + ", obstacle_nets="
            + obstacleNets(object)
            + ", obstacle="
            + object);
  }

  private static void traceCompleteShapeCandidate(
      int step, int netNo, int roomLayer, SearchTreeObject object, IntBox obstacleShape) {
    FRLogger.trace(
        "COMPLETE_SHAPE_OBS candidate"
            + ", step="
            + step
            + ", net="
            + netNo
            + ", layer="
            + roomLayer
            + ", obstacle="
            + object
            + ", obstacle_id="
            + obstacleId(object)
            + ", obstacle_nets="
            + obstacleNets(object)
            + ", obstacle_bounds="
            + describeBounds(obstacleShape));
  }

  private static void traceCompleteShapeDecision(
      int step,
      int netNo,
      int roomLayer,
      String action,
      boolean overlap,
      IntBox roomShape,
      IntBox obstacleShape) {
    FRLogger.trace(
        "COMPLETE_SHAPE_DECISION"
            + ", step="
            + step
            + ", net="
            + netNo
            + ", layer="
            + roomLayer
            + ", action="
            + action
            + ", overlap="
            + overlap
            + ", room_bounds="
            + describeBounds(roomShape)
            + ", obstacle_bounds="
            + describeBounds(obstacleShape));
  }

  private static int obstacleId(SearchTreeObject object) {
    return object instanceof Item item ? item.getIdNo() : -1;
  }

  private static String obstacleNets(SearchTreeObject object) {
    return object instanceof Item item ? java.util.Arrays.toString(item.netNoArr) : "[]";
  }

  @Override
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
        IntBox currentTileShape = currentShape.boundingBox();
        int offsetWidth =
            this.clearanceCompensationValue(drillItem.clearanceClassNo(), drillItem.shapeLayer(i));
        offsetWidth += drillHoleClearanceDelta(drillItem, currentShape, drillItem.shapeLayer(i));
        if (currentTileShape == null) {
          FRLogger.warn("BoxShapeSearchTree.calculate_tree_shapes: shape is null");
        } else {
          currentTileShape = currentTileShape.offset(offsetWidth);
        }
        result[i] = currentTileShape;
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(ObstacleArea obstacleArea) {
    TileShape[] result = super.calculateTreeShapes(obstacleArea);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingBox();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(BoardOutline outline) {
    TileShape[] result = super.calculateTreeShapes(outline);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingBox();
      }
    }
    return result;
  }

  /** Used for creating the shapes of a polyline_trace for this tree. */
  @Override
  TileShape offsetShape(Polyline polyline, int halfWidth, int no) {
    return polyline.offsetBox(halfWidth, no);
  }

  /** Used for creating the shapes of a polyline_trace for this tree. */
  @Override
  public TileShape[] offsetShapes(Polyline polyline, int halfWidth, int fromNo, int toNo) {
    fromNo = Math.max(fromNo, 0);
    toNo = Math.min(toNo, polyline.arr.length - 1);
    int shapeCount = Math.max(toNo - fromNo - 1, 0);
    TileShape[] shapeArr = new TileShape[shapeCount];
    for (int j = fromNo; j < toNo - 1; j++) {
      shapeArr[j - fromNo] = polyline.offsetBox(halfWidth, j);
    }
    return shapeArr;
  }
}

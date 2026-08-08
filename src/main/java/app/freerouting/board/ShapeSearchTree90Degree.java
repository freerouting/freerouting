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
  public ShapeSearchTree90Degree(BasicBoard p_board, int p_compensated_clearance_class_no) {
    super(OrthogonalBoundingDirections.INSTANCE, p_board, p_compensated_clearance_class_no);
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
      IncompleteFreeSpaceExpansionRoom p_room,
      int p_net_no,
      SearchTreeObject p_ignore_object,
      TileShape p_ignore_shape) {
    if (!(p_room.getContainedShape() instanceof IntBox shapeToBeContained)) {
      FRLogger.warn("BoxShapeSearchTree.complete_shape: unexpected p_shape_to_be_contained");
      return new LinkedList<>();
    }
    if (this.root == null) {
      return new LinkedList<>();
    }
    IntBox startShape = board.getBoundingBox();
    if (p_room.getShape() != null) {
      if (!(p_room.getShape() instanceof IntBox)) {
        FRLogger.warn("BoxShapeSearchTree.complete_shape: p_start_shape of type IntBox expected");
        return new LinkedList<>();
      }
      startShape = ((IntBox) p_room.getShape()).intersection(startShape);
    }
    IntBox boundingShape = startShape;
    int roomLayer = p_room.getLayer();
    boolean debugAnchor = isCompleteShapeDebugAnchor(p_net_no, roomLayer, startShape);
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
          boolean isObstacle = currObject.isTraceObstacle(p_net_no);
          int objectLayer = currObject.shapeLayer(shapeIndex);
          boolean sameLayer = objectLayer == roomLayer;
          boolean ignoredObject = currObject == p_ignore_object;
          if (debugAnchor) {
            traceCompleteShapeFilter(
                debugStep,
                p_net_no,
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
                  debugStep, p_net_no, roomLayer, currObject, currObjectShape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
            IntBox newBoundingShape = IntBox.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom currRoom : result) {
              IntBox currShape = (IntBox) currRoom.getShape();
              boolean overlaps = currShape.overlaps(currObjectShape);
              if (overlaps) {
                if (currObject instanceof CompleteFreeSpaceExpansionRoom
                    && p_ignore_shape != null) {
                  IntBox intersection = currShape.intersection(currObjectShape);
                  if (p_ignore_shape.contains(intersection)) {
                    if (debugAnchor) {
                      traceCompleteShapeDecision(
                          debugStep,
                          p_net_no,
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
                      p_net_no,
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
                      p_net_no,
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
                      + p_net_no
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
      IncompleteFreeSpaceExpansionRoom p_incomplete_room, IntBox p_obstacle_shape) {
    // Search the edge line of p_obstacle_shape, so that p_shape_to_be_contained
    // are on the right side of this line, and that the line segment
    // intersects with the interior of p_shape.
    // If there are more than 1 such lines take the line which is
    // furthest away from the shapeToBeContained
    // Then intersect p_shape with the halfplane defined by the
    // opposite of this line.

    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();

    TileShape containedShape = p_incomplete_room.getContainedShape();
    if (containedShape == null || containedShape.isEmpty()) {
      FRLogger.trace("BoxShapeSearchTree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    IntBox roomShape = p_incomplete_room.getShape().boundingBox();
    IntBox shapeToBeContained = p_incomplete_room.getContainedShape().boundingBox();
    int cutLineDistance = 0;
    IntBox restrainedShape = null;

    if (roomShape.ll.x < p_obstacle_shape.ur.x
        && roomShape.ur.x > p_obstacle_shape.ur.x
        && roomShape.ur.y > p_obstacle_shape.ll.y
        && roomShape.ll.y < p_obstacle_shape.ur.y) {
      // The right line segment of the obstacleShape intersects the interior of
      // p_shape
      int currDistance = shapeToBeContained.ll.x - p_obstacle_shape.ur.x;
      if (currDistance > cutLineDistance) {
        cutLineDistance = currDistance;
        restrainedShape =
            new IntBox(p_obstacle_shape.ur.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
      }
    }
    if (roomShape.ll.x < p_obstacle_shape.ll.x
        && roomShape.ur.x > p_obstacle_shape.ll.x
        && roomShape.ur.y > p_obstacle_shape.ll.y
        && roomShape.ll.y < p_obstacle_shape.ur.y) {
      // The left line segment of the obstacleShape intersects the interior of
      // p_shape
      int currDistance = p_obstacle_shape.ll.x - shapeToBeContained.ur.x;
      if (currDistance > cutLineDistance) {
        cutLineDistance = currDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, roomShape.ll.y, p_obstacle_shape.ll.x, roomShape.ur.y);
      }
    }
    if (roomShape.ll.y < p_obstacle_shape.ll.y
        && roomShape.ur.y > p_obstacle_shape.ll.y
        && roomShape.ur.x > p_obstacle_shape.ll.x
        && roomShape.ll.x < p_obstacle_shape.ur.x) {
      // The lower line segment of the obstacleShape intersects the interior of
      // p_shape
      int currDistance = p_obstacle_shape.ll.y - shapeToBeContained.ur.y;
      if (currDistance > cutLineDistance) {
        cutLineDistance = currDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, p_obstacle_shape.ll.y);
      }
    }
    if (roomShape.ll.y < p_obstacle_shape.ur.y
        && roomShape.ur.y > p_obstacle_shape.ur.y
        && roomShape.ur.x > p_obstacle_shape.ll.x
        && roomShape.ll.x < p_obstacle_shape.ur.x) {
      // The upper line segment of the obstacleShape intersects the interior of
      // p_shape
      int currDistance = shapeToBeContained.ll.y - p_obstacle_shape.ur.y;
      if (currDistance > cutLineDistance) {
        cutLineDistance = currDistance;
        restrainedShape =
            new IntBox(roomShape.ll.x, p_obstacle_shape.ur.y, roomShape.ur.x, roomShape.ur.y);
      }
    }
    if (restrainedShape != null) {
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrainedShape, p_incomplete_room.getLayer(), shapeToBeContained));
      return result;
    }

    // Now shapeToBeContained intersects with the obstacleShape.
    // shapeToBeContained and p_shape evtl. need to be divided in two.
    IntBox is = shapeToBeContained.intersection(p_obstacle_shape);
    if (is.isEmpty()) {
      FRLogger.warn(
          "BoxShapeSearchTree.restrain_shape: Intersection between obstacleShape and shapeToBeContained expected");
      return result;
    }
    IntBox newShape1 = null;
    IntBox newShape2 = null;
    if (is.ll.x > roomShape.ll.x && is.ll.x == p_obstacle_shape.ll.x && is.ll.x < roomShape.ur.x) {
      newShape1 = new IntBox(roomShape.ll.x, roomShape.ll.y, is.ll.x, roomShape.ur.y);
      newShape2 = new IntBox(is.ll.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ur.x > roomShape.ll.x
        && is.ur.x == p_obstacle_shape.ur.x
        && is.ur.x < roomShape.ur.x) {
      newShape2 = new IntBox(roomShape.ll.x, roomShape.ll.y, is.ur.x, roomShape.ur.y);
      newShape1 = new IntBox(is.ur.x, roomShape.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ll.y > roomShape.ll.y
        && is.ll.y == p_obstacle_shape.ll.y
        && is.ll.y < roomShape.ur.y) {
      newShape1 = new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, is.ll.y);
      newShape2 = new IntBox(roomShape.ll.x, is.ll.y, roomShape.ur.x, roomShape.ur.y);
    } else if (is.ur.y > roomShape.ll.y
        && is.ur.y == p_obstacle_shape.ur.y
        && is.ur.y < roomShape.ur.y) {
      newShape2 = new IntBox(roomShape.ll.x, roomShape.ll.y, roomShape.ur.x, is.ur.y);
      newShape1 = new IntBox(roomShape.ll.x, is.ur.y, roomShape.ur.x, roomShape.ur.y);
    }
    if (newShape1 != null) {
      IntBox newShapeToBeContained = shapeToBeContained.intersection(newShape1);
      if (newShapeToBeContained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                newShape1, p_incomplete_room.getLayer(), newShapeToBeContained));
        IncompleteFreeSpaceExpansionRoom newIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(
                newShape2,
                p_incomplete_room.getLayer(),
                shapeToBeContained.intersection(newShape2));
        result.addAll(restrainShape(newIncompleteRoom, p_obstacle_shape));
      }
    }
    return result;
  }

  private static String describeBounds(IntBox p_bounds) {
    return "[("
        + p_bounds.ll.x
        + ","
        + p_bounds.ll.y
        + ")..("
        + p_bounds.ur.x
        + ","
        + p_bounds.ur.y
        + ")]";
  }

  /**
   * Returns true for the specific room being diagnosed in the current parity investigation. Update
   * these coordinates to anchor detailed per-leaf logging to a different room.
   */
  private static boolean isCompleteShapeDebugAnchor(
      int p_net_no, int p_room_layer, IntBox p_start_shape) {
    return p_net_no == 84
        && p_room_layer == 0
        && p_start_shape.ll.x == 1767436
        && p_start_shape.ll.y == -1206395
        && p_start_shape.ur.x == 1994010
        && p_start_shape.ur.y == -782336;
  }

  private static void traceCompleteShapeFilter(
      int p_step,
      int p_net_no,
      int p_room_layer,
      int p_shape_index,
      int p_object_layer,
      boolean p_is_obstacle,
      boolean p_same_layer,
      boolean p_ignored_object,
      SearchTreeObject p_object) {
    FRLogger.trace(
        "COMPLETE_SHAPE_FILTER"
            + ", step="
            + p_step
            + ", net="
            + p_net_no
            + ", layer="
            + p_room_layer
            + ", shapeIndex="
            + p_shape_index
            + ", object_layer="
            + p_object_layer
            + ", is_trace_obstacle="
            + p_is_obstacle
            + ", same_layer="
            + p_same_layer
            + ", ignored_object="
            + p_ignored_object
            + ", accepted="
            + (p_is_obstacle && p_same_layer && !p_ignored_object)
            + ", obstacle_id="
            + obstacleId(p_object)
            + ", obstacle_nets="
            + obstacleNets(p_object)
            + ", obstacle="
            + p_object);
  }

  private static void traceCompleteShapeCandidate(
      int p_step,
      int p_net_no,
      int p_room_layer,
      SearchTreeObject p_object,
      IntBox p_obstacle_shape) {
    FRLogger.trace(
        "COMPLETE_SHAPE_OBS candidate"
            + ", step="
            + p_step
            + ", net="
            + p_net_no
            + ", layer="
            + p_room_layer
            + ", obstacle="
            + p_object
            + ", obstacle_id="
            + obstacleId(p_object)
            + ", obstacle_nets="
            + obstacleNets(p_object)
            + ", obstacle_bounds="
            + describeBounds(p_obstacle_shape));
  }

  private static void traceCompleteShapeDecision(
      int p_step,
      int p_net_no,
      int p_room_layer,
      String p_action,
      boolean p_overlap,
      IntBox p_room_shape,
      IntBox p_obstacle_shape) {
    FRLogger.trace(
        "COMPLETE_SHAPE_DECISION"
            + ", step="
            + p_step
            + ", net="
            + p_net_no
            + ", layer="
            + p_room_layer
            + ", action="
            + p_action
            + ", overlap="
            + p_overlap
            + ", room_bounds="
            + describeBounds(p_room_shape)
            + ", obstacle_bounds="
            + describeBounds(p_obstacle_shape));
  }

  private static int obstacleId(SearchTreeObject p_object) {
    return p_object instanceof Item item ? item.getIdNo() : -1;
  }

  private static String obstacleNets(SearchTreeObject p_object) {
    return p_object instanceof Item item ? java.util.Arrays.toString(item.netNoArr) : "[]";
  }

  @Override
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
        IntBox currTileShape = currShape.boundingBox();
        int offsetWidth =
            this.clearanceCompensationValue(
                p_drill_item.clearanceClassNo(), p_drill_item.shapeLayer(i));
        offsetWidth +=
            drillHoleClearanceDelta(p_drill_item, currShape, p_drill_item.shapeLayer(i));
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
  TileShape[] calculateTreeShapes(ObstacleArea p_obstacle_area) {
    TileShape[] result = super.calculateTreeShapes(p_obstacle_area);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingBox();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(BoardOutline p_outline) {
    TileShape[] result = super.calculateTreeShapes(p_outline);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingBox();
      }
    }
    return result;
  }

  /** Used for creating the shapes of a polyline_trace for this tree. */
  @Override
  TileShape offsetShape(Polyline p_polyline, int p_half_width, int p_no) {
    return p_polyline.offsetBox(p_half_width, p_no);
  }

  /** Used for creating the shapes of a polyline_trace for this tree. */
  @Override
  public TileShape[] offsetShapes(
      Polyline p_polyline, int p_half_width, int p_from_no, int p_to_no) {
    int fromNo = Math.max(p_from_no, 0);
    int toNo = Math.min(p_to_no, p_polyline.arr.length - 1);
    int shapeCount = Math.max(toNo - fromNo - 1, 0);
    TileShape[] shapeArr = new TileShape[shapeCount];
    for (int j = fromNo; j < toNo - 1; j++) {
      shapeArr[j - fromNo] = p_polyline.offsetBox(p_half_width, j);
    }
    return shapeArr;
  }
}

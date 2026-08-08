package app.freerouting.board;

import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.autoroute.IncompleteFreeSpaceExpansionRoom;
import app.freerouting.datastructures.ArrayStack;
import app.freerouting.geometry.planar.FortyfiveDegreeBoundingDirections;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;

/**
 * A special simple ShapeSearchtree, where the shapes are of class IntOctagon. It is used in the
 * 45-degree autorouter algorithm.
 */
public class ShapeSearchTree45Degree extends ShapeSearchTree {

  /** Creates a new instance of ShapeSearchTree45Degree */
  public ShapeSearchTree45Degree(BasicBoard p_board, int p_compensated_clearance_class_no) {
    super(FortyfiveDegreeBoundingDirections.INSTANCE, p_board, p_compensated_clearance_class_no);
  }

  /**
   * Checks, if the border line segment with index p_obstacle_border_line_no intersects with the
   * inside of p_room_shape.
   */
  private static boolean obstacleSegmentTouchesInside(
      IntOctagon p_obstacle_shape, int p_obstacle_border_line_no, IntOctagon p_room_shape) {
    int currBorderLineNo = p_obstacle_border_line_no;
    int currObstacleCornerX = p_obstacle_shape.cornerX(p_obstacle_border_line_no);
    int currObstacleCornerY = p_obstacle_shape.cornerY(p_obstacle_border_line_no);
    for (int j = 0; j < 5; j++) {

      if (p_room_shape.sideOfBorderLine(
              currObstacleCornerX, currObstacleCornerY, currBorderLineNo)
          != Side.ON_THE_LEFT) {
        return false;
      }
      currBorderLineNo = (currBorderLineNo + 1) % 8;
    }

    int nextObstacleBorderLineNo = (p_obstacle_border_line_no + 1) % 8;
    int nextObstacleCornerX = p_obstacle_shape.cornerX(nextObstacleBorderLineNo);
    int nextObstacleCornerY = p_obstacle_shape.cornerY(nextObstacleBorderLineNo);
    currBorderLineNo = (p_obstacle_border_line_no + 5) % 8;
    for (int j = 0; j < 3; j++) {
      if (p_room_shape.sideOfBorderLine(
              nextObstacleCornerX, nextObstacleCornerY, currBorderLineNo)
          != Side.ON_THE_LEFT) {
        return false;
      }
      currBorderLineNo = (currBorderLineNo + 1) % 8;
    }
    return true;
  }

  private static double signedLineDistance(
      IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_contained_shape) {
    return switch (p_obstacle_line_no) {
      case 0 -> p_obstacle_shape.bottomY - p_contained_shape.topY;
      case 2 -> p_contained_shape.leftX - p_obstacle_shape.rightX;
      case 4 -> p_contained_shape.bottomY - p_obstacle_shape.topY;
      case 6 -> p_obstacle_shape.leftX - p_contained_shape.rightX;

      // factor 0.5 used instead to 1 / sqrt(2) to prefer orthogonal lines slightly to diagonal
      // restraining lines.
      case 1 -> 0.5 * (p_contained_shape.upperLeftDiagonalX - p_obstacle_shape.lowerRightDiagonalX);
      case 3 -> 0.5 * (p_contained_shape.lowerLeftDiagonalX - p_obstacle_shape.upperRightDiagonalX);
      case 5 -> 0.5 * (p_obstacle_shape.upperLeftDiagonalX - p_contained_shape.lowerRightDiagonalX);
      case 7 -> 0.5 * (p_obstacle_shape.lowerLeftDiagonalX - p_contained_shape.upperRightDiagonalX);
      default -> {
        FRLogger.warn(
            "ShapeSearchTree45Degree.signed_line_distance: p_obstacle_line_no out of range");
        yield 0;
      }
    };
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
    TileShape containedRaw = p_room.getContainedShape();
    if (containedRaw == null) {
      FRLogger.warn(
          "ShapeSearchTree45Degree.complete_shape: contained shape is null, skipping expansion room");
      return new LinkedList<>();
    }
    if (!containedRaw.isIntOctagon()) {
      // The contained shape is not an IntOctagon (e.g. a Simplex from a non-45° trace segment).
      // Use the bounding octagon as a safe conservative approximation so the expansion room is
      // not silently discarded, which was previously causing incomplete routing connections.
      FRLogger.debug(
          "ShapeSearchTree45Degree.complete_shape: non-IntOctagon contained shape, using bounding octagon approximation");
    }
    IntOctagon shapeToBeContained = containedRaw.boundingOctagon();
    if (shapeToBeContained == null) {
      // bounding_octagon() returned null — this can happen for empty/degenerate shapes (e.g. a
      // zero-length trace segment). Discard the expansion room gracefully rather than throw NPE.
      FRLogger.debug(
          "ShapeSearchTree45Degree.complete_shape: bounding_octagon() returned null for contained shape of type "
              + containedRaw.getClass().getSimpleName()
              + ", skipping expansion room");
      return new LinkedList<>();
    }

    if (this.root == null) {
      return new LinkedList<>();
    }

    IntOctagon startShape = board.getBoundingBox().boundingOctagon();
    if (p_room.getShape() != null) {
      if (!(p_room.getShape() instanceof IntOctagon)) {
        FRLogger.warn(
            "ShapeSearchTree45Degree.complete_shape: p_start_shape of type IntOctagon expected");
        return new LinkedList<>();
      }
      startShape = p_room.getShape().boundingOctagon().intersection(startShape);
    }

    IntOctagon boundingShape = startShape;
    int roomLayer = p_room.getLayer();
    boolean debugAnchor = isCompleteShapeDebugAnchor(p_net_no, roomLayer, startShape);
    int debugStep = 0;
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    result.add(new IncompleteFreeSpaceExpansionRoom(startShape, roomLayer, shapeToBeContained));
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
          boolean isObstacle = currObject.isTraceObstacle(p_net_no);

          int shapeIndex = currLeaf.shapeIndexInObject;
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

            IntOctagon currObjectShape =
                currObject.getTreeShape(this, shapeIndex).boundingOctagon();
            if (debugAnchor) {
              traceCompleteShapeCandidate(
                  debugStep, p_net_no, roomLayer, currObject, currObjectShape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
            IntOctagon newBoundingShape = IntOctagon.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom currRoom : result) {
              IntOctagon currShape = (IntOctagon) currRoom.getShape();
              boolean overlaps = currShape.overlaps(currObjectShape);
              if (overlaps) {
                if (currObject instanceof CompleteFreeSpaceExpansionRoom
                    && p_ignore_shape != null) {
                  IntOctagon intersection = currShape.intersection(currObjectShape);
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
                    if (!p_ignore_shape.contains(currShape)) {
                      newResult.add(currRoom);
                      newBoundingShape = newBoundingShape.union(currShape.boundingBox());
                    }
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
                      + describeBounds(shapeToBeContained.boundingBox())
                      + ", obstacle_type="
                      + currObject.getClass().getSimpleName()
                      + ", obstacle_id="
                      + obstacleId(currObject)
                      + ", obstacle_bounds="
                      + describeBounds(currObjectShape.boundingBox()));
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

    result = divideLargeRoom(result, board.getBoundingBox());
    // remove rooms with shapes equal to the contained shape to prevent endless loop.
    result.removeIf(room -> room.getContainedShape().contains(room.getShape()));
    return result;
  }

  /**
   * Makes sure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm gets problems with
   * vias.
   */
  @Override
  protected Collection<IncompleteFreeSpaceExpansionRoom> divideLargeRoom(
      Collection<IncompleteFreeSpaceExpansionRoom> p_room_list, IntBox p_board_bounding_box) {
    Collection<IncompleteFreeSpaceExpansionRoom> result =
        super.divideLargeRoom(p_room_list, p_board_bounding_box);
    for (IncompleteFreeSpaceExpansionRoom currRoom : result) {
      currRoom.setShape(currRoom.getShape().boundingOctagon());
      currRoom.setContainedShape(currRoom.getContainedShape().boundingOctagon());
    }
    return result;
  }

  /**
   * Restrains the shape of p_incomplete_room to an octagon shape, which does not intersect with the
   * interior of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be contained in the
   * shape of the result room.
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrainShape(
      IncompleteFreeSpaceExpansionRoom p_incomplete_room, IntOctagon p_obstacle_shape) {
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
      FRLogger.debug("ShapeSearchTree45Degree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    IntOctagon shapeToBeContained;
    if (containedShape.isIntOctagon()) {
      shapeToBeContained = containedShape.boundingOctagon();
    } else if (containedShape instanceof Simplex) {
      shapeToBeContained = containedShape.boundingOctagon();
      if (shapeToBeContained == null) {
        FRLogger.warn("restrain_shape: cannot convert Simplex to IntOctagon");
        return new LinkedList<>();
      }
    } else {
      FRLogger.warn("restrain_shape: incompatible shape type");
      return new LinkedList<>();
    }

    IntOctagon roomShape;
    if (p_incomplete_room.getShape() instanceof IntOctagon) {
      roomShape = p_incomplete_room.getShape().boundingOctagon();
    } else if (p_incomplete_room.getShape() instanceof Simplex) {
      roomShape = p_incomplete_room.getShape().boundingOctagon();
      if (roomShape == null) {
        FRLogger.warn("restrain_shape: cannot convert room shape Simplex to IntOctagon");
        return new LinkedList<>();
      }
    } else {
      FRLogger.warn("restrain_shape: unsupported room shape type");
      return new LinkedList<>();
    }

    double cutLineDistance = -1;
    int restrainingLineNo = -1;

    for (int obstacle_line_no = 0; obstacle_line_no < 8; obstacle_line_no++) {
      double currDistance =
          signedLineDistance(p_obstacle_shape, obstacle_line_no, shapeToBeContained);
      if (currDistance > cutLineDistance) {
        if (obstacleSegmentTouchesInside(p_obstacle_shape, obstacle_line_no, roomShape)) {
          cutLineDistance = currDistance;
          restrainingLineNo = obstacle_line_no;
        }
      }
    }
    if (cutLineDistance >= 0) {
      IntOctagon restrainedShape =
          calcOutsideRestrainedShape(p_obstacle_shape, restrainingLineNo, roomShape);
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrainedShape, p_incomplete_room.getLayer(), shapeToBeContained));
      return result;
    }

    // There is no cut line, so that all p_shape_to_be_contained is
    // completely on the right side of that line. Search a cut line, so that
    // at least part of p_shape_to_be_contained is on the right side.
    if (shapeToBeContained.dimension() < 1) {
      // There is already a completed expansion room around p_shape_to_be_contained.
      return result;
    }

    restrainingLineNo = -1;
    for (int obstacle_line_no = 0; obstacle_line_no < 8; obstacle_line_no++) {
      if (obstacleSegmentTouchesInside(p_obstacle_shape, obstacle_line_no, roomShape)) {
        Line currLine = p_obstacle_shape.borderLine(obstacle_line_no);
        if (shapeToBeContained.sideOf(currLine) == Side.COLLINEAR) {
          // currLine intersects with the interior of p_shape_to_be_contained
          restrainingLineNo = obstacle_line_no;
          break;
        }
      }
    }
    if (restrainingLineNo < 0) {
      // cut line not found, parts or the whole of p_shape may be already
      // occupied from somewhere else.
      return result;
    }
    IntOctagon restrainedShape =
        calcOutsideRestrainedShape(p_obstacle_shape, restrainingLineNo, roomShape);
    if (restrainedShape.dimension() == 2) {
      IntOctagon newShapeToBeContained = shapeToBeContained.intersection(restrainedShape);
      if (newShapeToBeContained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                restrainedShape, p_incomplete_room.getLayer(), newShapeToBeContained));
      }
    }

    IntOctagon restPiece =
        calcInsideRestrainedShape(p_obstacle_shape, restrainingLineNo, roomShape);
    if (restPiece.dimension() >= 2) {
      TileShape restShapeToBeContained = shapeToBeContained.intersection(restPiece);
      if (restShapeToBeContained.dimension() >= 0) {
        IncompleteFreeSpaceExpansionRoom restIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(
                restPiece, p_incomplete_room.getLayer(), restShapeToBeContained);
        result.addAll(restrainShape(restIncompleteRoom, p_obstacle_shape));
      }
    }
    return result;
  }

  /**
   * Intersects p_room_shape with the half plane defined by the outside of the borderline with index
   * p_obstacle_line_no of p_obstacle_shape.
   */
  IntOctagon calcOutsideRestrainedShape(
      IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_room_shape) {
    int lx = p_room_shape.leftX;
    int ly = p_room_shape.bottomY;
    int rx = p_room_shape.rightX;
    int uy = p_room_shape.topY;
    int ulx = p_room_shape.upperLeftDiagonalX;
    int lrx = p_room_shape.lowerRightDiagonalX;
    int llx = p_room_shape.lowerLeftDiagonalX;
    int urx = p_room_shape.upperRightDiagonalX;

    switch (p_obstacle_line_no) {
      case 0 -> uy = p_obstacle_shape.bottomY;
      case 2 -> lx = p_obstacle_shape.rightX;
      case 4 -> ly = p_obstacle_shape.topY;
      case 6 -> rx = p_obstacle_shape.leftX;
      case 1 -> ulx = p_obstacle_shape.lowerRightDiagonalX;
      case 3 -> llx = p_obstacle_shape.upperRightDiagonalX;
      case 5 -> lrx = p_obstacle_shape.upperLeftDiagonalX;
      case 7 -> urx = p_obstacle_shape.lowerLeftDiagonalX;
      default ->
          FRLogger.warn(
              "ShapeSearchTree45Degree.calc_outside_restrained_shape: p_obstacle_line_no out of range");
    }

    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
  }

  /**
   * Intersects p_room_shape with the half plane defined by the inside of the borderline with index
   * p_obstacle_line_no of p_obstacle_shape.
   */
  IntOctagon calcInsideRestrainedShape(
      IntOctagon p_obstacle_shape, int p_obstacle_line_no, IntOctagon p_room_shape) {
    int lx = p_room_shape.leftX;
    int ly = p_room_shape.bottomY;
    int rx = p_room_shape.rightX;
    int uy = p_room_shape.topY;
    int ulx = p_room_shape.upperLeftDiagonalX;
    int lrx = p_room_shape.lowerRightDiagonalX;
    int llx = p_room_shape.lowerLeftDiagonalX;
    int urx = p_room_shape.upperRightDiagonalX;

    switch (p_obstacle_line_no) {
      case 0 -> ly = p_obstacle_shape.bottomY;
      case 2 -> rx = p_obstacle_shape.rightX;
      case 4 -> uy = p_obstacle_shape.topY;
      case 6 -> lx = p_obstacle_shape.leftX;
      case 1 -> lrx = p_obstacle_shape.lowerRightDiagonalX;
      case 3 -> urx = p_obstacle_shape.upperRightDiagonalX;
      case 5 -> ulx = p_obstacle_shape.upperLeftDiagonalX;
      case 7 -> llx = p_obstacle_shape.lowerLeftDiagonalX;
      default ->
          FRLogger.warn(
              "ShapeSearchTree45Degree.calc_inside_restrained_shape: p_obstacle_line_no out of range");
    }

    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
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
        TileShape currTileShape = currShape.boundingOctagon();
        if (currTileShape.isIntBox()) {
          currTileShape = currShape.boundingBox();

          // To avoid small corner cutoffs when taking the offset as an octagon.
          // That may complicate the room division in the maze expand algorithm unnecessary.
        }

        int offsetWidth =
            this.clearanceCompensationValue(
                p_drill_item.clearanceClassNo(), p_drill_item.shapeLayer(i));
        offsetWidth +=
            drillHoleClearanceDelta(p_drill_item, currShape, p_drill_item.shapeLayer(i));
        currTileShape = (TileShape) currTileShape.offset(offsetWidth);
        result[i] = currTileShape.boundingOctagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(ObstacleArea p_obstacle_area) {
    TileShape[] result = super.calculateTreeShapes(p_obstacle_area);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingOctagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(BoardOutline p_outline) {
    TileShape[] result = super.calculateTreeShapes(p_outline);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingOctagon();
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

  private static boolean isCompleteShapeDebugAnchor(
      int p_net_no, int p_room_layer, IntOctagon p_start_shape) {
    return p_net_no == 77
        && p_room_layer == 0
        && p_start_shape.leftX == 1762393
        && p_start_shape.bottomY == -1080137
        && p_start_shape.rightX == 1910447
        && p_start_shape.topY == -1006110;
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
      IntOctagon p_obstacle_shape) {
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
            + describeBounds(p_obstacle_shape.boundingBox()));
  }

  private static void traceCompleteShapeDecision(
      int p_step,
      int p_net_no,
      int p_room_layer,
      String p_action,
      boolean p_overlap,
      IntOctagon p_room_shape,
      IntOctagon p_obstacle_shape) {
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
            + describeBounds(p_room_shape.boundingBox())
            + ", obstacle_bounds="
            + describeBounds(p_obstacle_shape.boundingBox()));
  }

  private static int obstacleId(SearchTreeObject p_object) {
    return p_object instanceof Item item ? item.getIdNo() : -1;
  }

  private static String obstacleNets(SearchTreeObject p_object) {
    return p_object instanceof Item item ? java.util.Arrays.toString(item.netNoArr) : "[]";
  }
}

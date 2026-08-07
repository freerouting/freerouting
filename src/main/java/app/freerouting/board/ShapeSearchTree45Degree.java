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
  private static boolean obstacle_segment_touches_inside(
      IntOctagon p_obstacle_shape, int p_obstacle_border_line_no, IntOctagon p_room_shape) {
    int currBorderLineNo = p_obstacle_border_line_no;
    int currObstacleCornerX = p_obstacle_shape.corner_x(p_obstacle_border_line_no);
    int currObstacleCornerY = p_obstacle_shape.corner_y(p_obstacle_border_line_no);
    for (int j = 0; j < 5; j++) {

      if (p_room_shape.side_of_border_line(
              currObstacleCornerX, currObstacleCornerY, currBorderLineNo)
          != Side.ON_THE_LEFT) {
        return false;
      }
      currBorderLineNo = (currBorderLineNo + 1) % 8;
    }

    int nextObstacleBorderLineNo = (p_obstacle_border_line_no + 1) % 8;
    int nextObstacleCornerX = p_obstacle_shape.corner_x(nextObstacleBorderLineNo);
    int nextObstacleCornerY = p_obstacle_shape.corner_y(nextObstacleBorderLineNo);
    currBorderLineNo = (p_obstacle_border_line_no + 5) % 8;
    for (int j = 0; j < 3; j++) {
      if (p_room_shape.side_of_border_line(
              nextObstacleCornerX, nextObstacleCornerY, currBorderLineNo)
          != Side.ON_THE_LEFT) {
        return false;
      }
      currBorderLineNo = (currBorderLineNo + 1) % 8;
    }
    return true;
  }

  private static double signed_line_distance(
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
  public Collection<IncompleteFreeSpaceExpansionRoom> complete_shape(
      IncompleteFreeSpaceExpansionRoom p_room,
      int p_net_no,
      SearchTreeObject p_ignore_object,
      TileShape p_ignore_shape) {
    TileShape containedRaw = p_room.get_contained_shape();
    if (containedRaw == null) {
      FRLogger.warn(
          "ShapeSearchTree45Degree.complete_shape: contained shape is null, skipping expansion room");
      return new LinkedList<>();
    }
    if (!containedRaw.is_IntOctagon()) {
      // The contained shape is not an IntOctagon (e.g. a Simplex from a non-45° trace segment).
      // Use the bounding octagon as a safe conservative approximation so the expansion room is
      // not silently discarded, which was previously causing incomplete routing connections.
      FRLogger.debug(
          "ShapeSearchTree45Degree.complete_shape: non-IntOctagon contained shape, using bounding octagon approximation");
    }
    IntOctagon shapeToBeContained = containedRaw.bounding_octagon();
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

    IntOctagon startShape = board.get_bounding_box().bounding_octagon();
    if (p_room.get_shape() != null) {
      if (!(p_room.get_shape() instanceof IntOctagon)) {
        FRLogger.warn(
            "ShapeSearchTree45Degree.complete_shape: p_start_shape of type IntOctagon expected");
        return new LinkedList<>();
      }
      startShape = p_room.get_shape().bounding_octagon().intersection(startShape);
    }

    IntOctagon boundingShape = startShape;
    int roomLayer = p_room.get_layer();
    boolean debugAnchor = is_complete_shape_debug_anchor(p_net_no, roomLayer, startShape);
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
          boolean isObstacle = currObject.is_trace_obstacle(p_net_no);

          int shapeIndex = currLeaf.shapeIndexInObject;
          int objectLayer = currObject.shape_layer(shapeIndex);
          boolean sameLayer = objectLayer == roomLayer;
          boolean ignoredObject = currObject == p_ignore_object;
          if (debugAnchor) {
            trace_complete_shape_filter(
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
                currObject.get_tree_shape(this, shapeIndex).bounding_octagon();
            if (debugAnchor) {
              trace_complete_shape_candidate(
                  debugStep, p_net_no, roomLayer, currObject, currObjectShape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
            IntOctagon newBoundingShape = IntOctagon.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom currRoom : result) {
              IntOctagon currShape = (IntOctagon) currRoom.get_shape();
              boolean overlaps = currShape.overlaps(currObjectShape);
              if (overlaps) {
                if (currObject instanceof CompleteFreeSpaceExpansionRoom
                    && p_ignore_shape != null) {
                  IntOctagon intersection = currShape.intersection(currObjectShape);
                  if (p_ignore_shape.contains(intersection)) {
                    if (debugAnchor) {
                      trace_complete_shape_decision(
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
                      newBoundingShape = newBoundingShape.union(currShape.bounding_box());
                    }
                    continue;
                  }
                }
                if (debugAnchor) {
                  trace_complete_shape_decision(
                      debugStep,
                      p_net_no,
                      roomLayer,
                      "RESTRAIN",
                      overlaps,
                      currShape,
                      currObjectShape);
                }
                Collection<IncompleteFreeSpaceExpansionRoom> newRestrainedShapes =
                    restrain_shape(currRoom, currObjectShape);
                newResult.addAll(newRestrainedShapes);

                for (IncompleteFreeSpaceExpansionRoom tmpShape : newResult) {
                  newBoundingShape = newBoundingShape.union(tmpShape.get_shape().bounding_box());
                }
              } else {
                if (debugAnchor) {
                  trace_complete_shape_decision(
                      debugStep,
                      p_net_no,
                      roomLayer,
                      "KEEP_NON_OVERLAP",
                      overlaps,
                      currShape,
                      currObjectShape);
                }
                newResult.add(currRoom);
                newBoundingShape = newBoundingShape.union(currShape.bounding_box());
              }
            }
            if (hadRoomsBeforeObstacle && newResult.isEmpty()) {
              FRLogger.trace(
                  "COMPLETE_SHAPE_BLOCKED net="
                      + p_net_no
                      + ", layer="
                      + roomLayer
                      + ", contained="
                      + describe_bounds(shapeToBeContained.bounding_box())
                      + ", obstacle_type="
                      + currObject.getClass().getSimpleName()
                      + ", obstacle_id="
                      + obstacle_id(currObject)
                      + ", obstacle_bounds="
                      + describe_bounds(currObjectShape.bounding_box()));
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

    result = divide_large_room(result, board.get_bounding_box());
    // remove rooms with shapes equal to the contained shape to prevent endless loop.
    result.removeIf(room -> room.get_contained_shape().contains(room.get_shape()));
    return result;
  }

  /**
   * Makes sure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm gets problems with
   * vias.
   */
  @Override
  protected Collection<IncompleteFreeSpaceExpansionRoom> divide_large_room(
      Collection<IncompleteFreeSpaceExpansionRoom> p_room_list, IntBox p_board_bounding_box) {
    Collection<IncompleteFreeSpaceExpansionRoom> result =
        super.divide_large_room(p_room_list, p_board_bounding_box);
    for (IncompleteFreeSpaceExpansionRoom currRoom : result) {
      currRoom.set_shape(currRoom.get_shape().bounding_octagon());
      currRoom.set_contained_shape(currRoom.get_contained_shape().bounding_octagon());
    }
    return result;
  }

  /**
   * Restrains the shape of p_incomplete_room to an octagon shape, which does not intersect with the
   * interior of p_obstacle_shape. p_incomplete_room.get_contained_shape() must be contained in the
   * shape of the result room.
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrain_shape(
      IncompleteFreeSpaceExpansionRoom p_incomplete_room, IntOctagon p_obstacle_shape) {
    // Search the edge line of p_obstacle_shape, so that p_shape_to_be_contained
    // are on the right side of this line, and that the line segment
    // intersects with the interior of p_shape.
    // If there are more than 1 such lines take the line which is
    // furthest away from the shapeToBeContained
    // Then intersect p_shape with the halfplane defined by the
    // opposite of this line.

    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();

    TileShape containedShape = p_incomplete_room.get_contained_shape();
    if (containedShape == null || containedShape.is_empty()) {
      FRLogger.debug("ShapeSearchTree45Degree.restrain_shape: p_shape_to_be_contained is empty");
      return result;
    }
    IntOctagon shapeToBeContained;
    if (containedShape.is_IntOctagon()) {
      shapeToBeContained = containedShape.bounding_octagon();
    } else if (containedShape instanceof Simplex) {
      shapeToBeContained = containedShape.bounding_octagon();
      if (shapeToBeContained == null) {
        FRLogger.warn("restrain_shape: cannot convert Simplex to IntOctagon");
        return new LinkedList<>();
      }
    } else {
      FRLogger.warn("restrain_shape: incompatible shape type");
      return new LinkedList<>();
    }

    IntOctagon roomShape;
    if (p_incomplete_room.get_shape() instanceof IntOctagon) {
      roomShape = p_incomplete_room.get_shape().bounding_octagon();
    } else if (p_incomplete_room.get_shape() instanceof Simplex) {
      roomShape = p_incomplete_room.get_shape().bounding_octagon();
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
          signed_line_distance(p_obstacle_shape, obstacle_line_no, shapeToBeContained);
      if (currDistance > cutLineDistance) {
        if (obstacle_segment_touches_inside(p_obstacle_shape, obstacle_line_no, roomShape)) {
          cutLineDistance = currDistance;
          restrainingLineNo = obstacle_line_no;
        }
      }
    }
    if (cutLineDistance >= 0) {
      IntOctagon restrainedShape =
          calc_outside_restrained_shape(p_obstacle_shape, restrainingLineNo, roomShape);
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrainedShape, p_incomplete_room.get_layer(), shapeToBeContained));
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
      if (obstacle_segment_touches_inside(p_obstacle_shape, obstacle_line_no, roomShape)) {
        Line currLine = p_obstacle_shape.border_line(obstacle_line_no);
        if (shapeToBeContained.side_of(currLine) == Side.COLLINEAR) {
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
        calc_outside_restrained_shape(p_obstacle_shape, restrainingLineNo, roomShape);
    if (restrainedShape.dimension() == 2) {
      IntOctagon newShapeToBeContained = shapeToBeContained.intersection(restrainedShape);
      if (newShapeToBeContained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                restrainedShape, p_incomplete_room.get_layer(), newShapeToBeContained));
      }
    }

    IntOctagon restPiece =
        calc_inside_restrained_shape(p_obstacle_shape, restrainingLineNo, roomShape);
    if (restPiece.dimension() >= 2) {
      TileShape restShapeToBeContained = shapeToBeContained.intersection(restPiece);
      if (restShapeToBeContained.dimension() >= 0) {
        IncompleteFreeSpaceExpansionRoom restIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(
                restPiece, p_incomplete_room.get_layer(), restShapeToBeContained);
        result.addAll(restrain_shape(restIncompleteRoom, p_obstacle_shape));
      }
    }
    return result;
  }

  /**
   * Intersects p_room_shape with the half plane defined by the outside of the borderline with index
   * p_obstacle_line_no of p_obstacle_shape.
   */
  IntOctagon calc_outside_restrained_shape(
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
  IntOctagon calc_inside_restrained_shape(
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
        TileShape currTileShape = currShape.bounding_octagon();
        if (currTileShape.is_IntBox()) {
          currTileShape = currShape.bounding_box();

          // To avoid small corner cutoffs when taking the offset as an octagon.
          // That may complicate the room division in the maze expand algorithm unnecessary.
        }

        int offsetWidth =
            this.clearance_compensation_value(
                p_drill_item.clearance_class_no(), p_drill_item.shape_layer(i));
        offsetWidth +=
            drill_hole_clearance_delta(p_drill_item, currShape, p_drill_item.shape_layer(i));
        currTileShape = (TileShape) currTileShape.offset(offsetWidth);
        result[i] = currTileShape.bounding_octagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculate_tree_shapes(ObstacleArea p_obstacle_area) {
    TileShape[] result = super.calculate_tree_shapes(p_obstacle_area);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].bounding_octagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculate_tree_shapes(BoardOutline p_outline) {
    TileShape[] result = super.calculate_tree_shapes(p_outline);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].bounding_octagon();
      }
    }
    return result;
  }

  private static String describe_bounds(IntBox p_bounds) {
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

  private static boolean is_complete_shape_debug_anchor(
      int p_net_no, int p_room_layer, IntOctagon p_start_shape) {
    return p_net_no == 77
        && p_room_layer == 0
        && p_start_shape.leftX == 1762393
        && p_start_shape.bottomY == -1080137
        && p_start_shape.rightX == 1910447
        && p_start_shape.topY == -1006110;
  }

  private static void trace_complete_shape_filter(
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
            + obstacle_id(p_object)
            + ", obstacle_nets="
            + obstacle_nets(p_object)
            + ", obstacle="
            + p_object);
  }

  private static void trace_complete_shape_candidate(
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
            + obstacle_id(p_object)
            + ", obstacle_nets="
            + obstacle_nets(p_object)
            + ", obstacle_bounds="
            + describe_bounds(p_obstacle_shape.bounding_box()));
  }

  private static void trace_complete_shape_decision(
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
            + describe_bounds(p_room_shape.bounding_box())
            + ", obstacle_bounds="
            + describe_bounds(p_obstacle_shape.bounding_box()));
  }

  private static int obstacle_id(SearchTreeObject p_object) {
    return p_object instanceof Item item ? item.get_id_no() : -1;
  }

  private static String obstacle_nets(SearchTreeObject p_object) {
    return p_object instanceof Item item ? java.util.Arrays.toString(item.netNoArr) : "[]";
  }
}

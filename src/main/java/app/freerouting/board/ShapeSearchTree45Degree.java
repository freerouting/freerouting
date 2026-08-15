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

  /** Creates a new instance of ShapeSearchTree45Degree. */
  public ShapeSearchTree45Degree(BasicBoard board, int compensatedClearanceClassNo) {
    super(FortyfiveDegreeBoundingDirections.INSTANCE, board, compensatedClearanceClassNo);
  }

  /**
   * Checks, if the border line segment with index obstacleBorderLineNo intersects with the inside
   * of roomShape.
   */
  private static boolean obstacleSegmentTouchesInside(
      IntOctagon obstacleShape, int obstacleBorderLineNo, IntOctagon roomShape) {
    int currentBorderLineNo = obstacleBorderLineNo;
    int currentObstacleCornerX = obstacleShape.cornerX(obstacleBorderLineNo);
    int currentObstacleCornerY = obstacleShape.cornerY(obstacleBorderLineNo);
    for (int j = 0; j < 5; j++) {

      if (roomShape.sideOfBorderLine(
              currentObstacleCornerX, currentObstacleCornerY, currentBorderLineNo)
          != Side.ON_THE_LEFT) {
        return false;
      }
      currentBorderLineNo = (currentBorderLineNo + 1) % 8;
    }

    int nextObstacleBorderLineNo = (obstacleBorderLineNo + 1) % 8;
    int nextObstacleCornerX = obstacleShape.cornerX(nextObstacleBorderLineNo);
    int nextObstacleCornerY = obstacleShape.cornerY(nextObstacleBorderLineNo);
    currentBorderLineNo = (obstacleBorderLineNo + 5) % 8;
    for (int j = 0; j < 3; j++) {
      if (roomShape.sideOfBorderLine(nextObstacleCornerX, nextObstacleCornerY, currentBorderLineNo)
          != Side.ON_THE_LEFT) {
        return false;
      }
      currentBorderLineNo = (currentBorderLineNo + 1) % 8;
    }
    return true;
  }

  private static double signedLineDistance(
      IntOctagon obstacleShape, int obstacleLineNo, IntOctagon containedShape) {
    return switch (obstacleLineNo) {
      case 0 -> obstacleShape.bottomY - containedShape.topY;
      case 2 -> containedShape.leftX - obstacleShape.rightX;
      case 4 -> containedShape.bottomY - obstacleShape.topY;
      case 6 -> obstacleShape.leftX - containedShape.rightX;

      // factor 0.5 used instead to 1 / sqrt(2) to prefer orthogonal lines slightly to diagonal
      // restraining lines.
      case 1 -> 0.5 * (containedShape.upperLeftDiagonalX - obstacleShape.lowerRightDiagonalX);
      case 3 -> 0.5 * (containedShape.lowerLeftDiagonalX - obstacleShape.upperRightDiagonalX);
      case 5 -> 0.5 * (obstacleShape.upperLeftDiagonalX - containedShape.lowerRightDiagonalX);
      case 7 -> 0.5 * (obstacleShape.lowerLeftDiagonalX - containedShape.upperRightDiagonalX);
      default -> {
        FRLogger.warn("ShapeSearchTree45Degree.signed_line_distance: obstacleLineNo out of range");
        yield 0;
      }
    };
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
    TileShape containedRaw = room.getContainedShape();
    if (containedRaw == null) {
      FRLogger.warn(
          "ShapeSearchTree45Degree.complete_shape: contained shape is null,"
              + " skipping expansion room");
      return new LinkedList<>();
    }
    if (!containedRaw.isIntOctagon()) {
      // The contained shape is not an IntOctagon (e.g. a Simplex from a non-45° trace segment).
      // Use the bounding octagon as a safe conservative approximation so the expansion room is
      // not silently discarded, which was previously causing incomplete routing connections.
      FRLogger.debug(
          "ShapeSearchTree45Degree.complete_shape: non-IntOctagon contained shape,"
              + " using bounding octagon approximation");
    }
    IntOctagon shapeToBeContained = containedRaw.boundingOctagon();
    if (shapeToBeContained == null) {
      // bounding_octagon() returned null — this can happen for empty/degenerate shapes (e.g. a
      // zero-length trace segment). Discard the expansion room gracefully rather than throw NPE.
      FRLogger.debug(
          "ShapeSearchTree45Degree.complete_shape: bounding_octagon() returned null"
              + " for contained shape of type "
              + containedRaw.getClass().getSimpleName()
              + ", skipping expansion room");
      return new LinkedList<>();
    }

    if (this.root == null) {
      return new LinkedList<>();
    }

    IntOctagon startShape = board.getBoundingBox().boundingOctagon();
    if (room.getShape() != null) {
      if (!(room.getShape() instanceof IntOctagon)) {
        FRLogger.warn(
            "ShapeSearchTree45Degree.complete_shape: startShape of type IntOctagon expected");
        return new LinkedList<>();
      }
      startShape = room.getShape().boundingOctagon().intersection(startShape);
    }

    IntOctagon boundingShape = startShape;
    int roomLayer = room.getLayer();
    boolean debugAnchor = isCompleteShapeDebugAnchor(netNo, roomLayer, startShape);
    int debugStep = 0;
    Collection<IncompleteFreeSpaceExpansionRoom> result = new LinkedList<>();
    result.add(new IncompleteFreeSpaceExpansionRoom(startShape, roomLayer, shapeToBeContained));
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
          boolean isObstacle = currentObject.isTraceObstacle(netNo);

          int shapeIndex = currentLeaf.shapeIndexInObject;
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

            IntOctagon currentObjectShape =
                currentObject.getTreeShape(this, shapeIndex).boundingOctagon();
            if (debugAnchor) {
              traceCompleteShapeCandidate(
                  debugStep, netNo, roomLayer, currentObject, currentObjectShape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
            IntOctagon newBoundingShape = IntOctagon.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom currentRoom : result) {
              IntOctagon currentShape = (IntOctagon) currentRoom.getShape();
              boolean overlaps = currentShape.overlaps(currentObjectShape);
              if (overlaps) {
                if (currentObject instanceof CompleteFreeSpaceExpansionRoom
                    && ignoreShape != null) {
                  IntOctagon intersection = currentShape.intersection(currentObjectShape);
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
                    if (!ignoreShape.contains(currentShape)) {
                      newResult.add(currentRoom);
                      newBoundingShape = newBoundingShape.union(currentShape.boundingBox());
                    }
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
                      + describeBounds(shapeToBeContained.boundingBox())
                      + ", obstacle_type="
                      + currentObject.getClass().getSimpleName()
                      + ", obstacle_id="
                      + obstacleId(currentObject)
                      + ", obstacle_bounds="
                      + describeBounds(currentObjectShape.boundingBox()));
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

    result = divideLargeRoom(result, board.getBoundingBox());
    // remove rooms with shapes equal to the contained shape to prevent endless loop.
    result.removeIf(
        expansionRoom -> expansionRoom.getContainedShape().contains(expansionRoom.getShape()));
    return result;
  }

  /**
   * Makes sure that on each layer there will be more than 1 IncompleteFreeSpaceExpansionRoom, even
   * if there are no objects on the layer. Otherwise, the maze search algorithm gets problems with
   * vias.
   */
  @Override
  protected Collection<IncompleteFreeSpaceExpansionRoom> divideLargeRoom(
      Collection<IncompleteFreeSpaceExpansionRoom> roomList, IntBox boardBoundingBox) {
    Collection<IncompleteFreeSpaceExpansionRoom> result =
        super.divideLargeRoom(roomList, boardBoundingBox);
    for (IncompleteFreeSpaceExpansionRoom currentRoom : result) {
      currentRoom.setShape(currentRoom.getShape().boundingOctagon());
      currentRoom.setContainedShape(currentRoom.getContainedShape().boundingOctagon());
    }
    return result;
  }

  /**
   * Restrains the shape of incompleteRoom to an octagon shape, which does not intersect with the
   * interior of obstacleShape. incompleteRoom.get_contained_shape() must be contained in the shape
   * of the result room.
   */
  private Collection<IncompleteFreeSpaceExpansionRoom> restrainShape(
      IncompleteFreeSpaceExpansionRoom incompleteRoom, IntOctagon obstacleShape) {
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
      FRLogger.debug("ShapeSearchTree45Degree.restrain_shape: shapeToBeContained is empty");
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
    if (incompleteRoom.getShape() instanceof IntOctagon) {
      roomShape = incompleteRoom.getShape().boundingOctagon();
    } else if (incompleteRoom.getShape() instanceof Simplex) {
      roomShape = incompleteRoom.getShape().boundingOctagon();
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

    for (int obstacleLineNo = 0; obstacleLineNo < 8; obstacleLineNo++) {
      double currentDistance =
          signedLineDistance(obstacleShape, obstacleLineNo, shapeToBeContained);
      if (currentDistance > cutLineDistance) {
        if (obstacleSegmentTouchesInside(obstacleShape, obstacleLineNo, roomShape)) {
          cutLineDistance = currentDistance;
          restrainingLineNo = obstacleLineNo;
        }
      }
    }
    if (cutLineDistance >= 0) {
      IntOctagon restrainedShape =
          calcOutsideRestrainedShape(obstacleShape, restrainingLineNo, roomShape);
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrainedShape, incompleteRoom.getLayer(), shapeToBeContained));
      return result;
    }

    // There is no cut line, so that all shapeToBeContained is
    // completely on the right side of that line. Search a cut line, so that
    // at least part of shapeToBeContained is on the right side.
    if (shapeToBeContained.dimension() < 1) {
      // There is already a completed expansion room around shapeToBeContained.
      return result;
    }

    restrainingLineNo = -1;
    for (int obstacleLineNo = 0; obstacleLineNo < 8; obstacleLineNo++) {
      if (obstacleSegmentTouchesInside(obstacleShape, obstacleLineNo, roomShape)) {
        Line currentLine = obstacleShape.borderLine(obstacleLineNo);
        if (shapeToBeContained.sideOf(currentLine) == Side.COLLINEAR) {
          // currentLine intersects with the interior of shapeToBeContained
          restrainingLineNo = obstacleLineNo;
          break;
        }
      }
    }
    if (restrainingLineNo < 0) {
      // cut line not found, parts or the whole of shape may be already
      // occupied from somewhere else.
      return result;
    }
    IntOctagon restrainedShape =
        calcOutsideRestrainedShape(obstacleShape, restrainingLineNo, roomShape);
    if (restrainedShape.dimension() == 2) {
      IntOctagon newShapeToBeContained = shapeToBeContained.intersection(restrainedShape);
      if (newShapeToBeContained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                restrainedShape, incompleteRoom.getLayer(), newShapeToBeContained));
      }
    }

    IntOctagon restPiece = calcInsideRestrainedShape(obstacleShape, restrainingLineNo, roomShape);
    if (restPiece.dimension() >= 2) {
      TileShape restShapeToBeContained = shapeToBeContained.intersection(restPiece);
      if (restShapeToBeContained.dimension() >= 0) {
        IncompleteFreeSpaceExpansionRoom restIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(
                restPiece, incompleteRoom.getLayer(), restShapeToBeContained);
        result.addAll(restrainShape(restIncompleteRoom, obstacleShape));
      }
    }
    return result;
  }

  /**
   * Intersects roomShape with the half plane defined by the outside of the borderline with index
   * obstacleLineNo of obstacleShape.
   */
  IntOctagon calcOutsideRestrainedShape(
      IntOctagon obstacleShape, int obstacleLineNo, IntOctagon roomShape) {
    int lx = roomShape.leftX;
    int ly = roomShape.bottomY;
    int rx = roomShape.rightX;
    int uy = roomShape.topY;
    int ulx = roomShape.upperLeftDiagonalX;
    int lrx = roomShape.lowerRightDiagonalX;
    int llx = roomShape.lowerLeftDiagonalX;
    int urx = roomShape.upperRightDiagonalX;

    switch (obstacleLineNo) {
      case 0 -> uy = obstacleShape.bottomY;
      case 2 -> lx = obstacleShape.rightX;
      case 4 -> ly = obstacleShape.topY;
      case 6 -> rx = obstacleShape.leftX;
      case 1 -> ulx = obstacleShape.lowerRightDiagonalX;
      case 3 -> llx = obstacleShape.upperRightDiagonalX;
      case 5 -> lrx = obstacleShape.upperLeftDiagonalX;
      case 7 -> urx = obstacleShape.lowerLeftDiagonalX;
      default ->
          FRLogger.warn(
              "ShapeSearchTree45Degree.calc_outside_restrained_shape:"
                  + " obstacleLineNo out of range");
    }

    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
  }

  /**
   * Intersects roomShape with the half plane defined by the inside of the borderline with index
   * obstacleLineNo of obstacleShape.
   */
  IntOctagon calcInsideRestrainedShape(
      IntOctagon obstacleShape, int obstacleLineNo, IntOctagon roomShape) {
    int lx = roomShape.leftX;
    int ly = roomShape.bottomY;
    int rx = roomShape.rightX;
    int uy = roomShape.topY;
    int ulx = roomShape.upperLeftDiagonalX;
    int lrx = roomShape.lowerRightDiagonalX;
    int llx = roomShape.lowerLeftDiagonalX;
    int urx = roomShape.upperRightDiagonalX;

    switch (obstacleLineNo) {
      case 0 -> ly = obstacleShape.bottomY;
      case 2 -> rx = obstacleShape.rightX;
      case 4 -> uy = obstacleShape.topY;
      case 6 -> lx = obstacleShape.leftX;
      case 1 -> lrx = obstacleShape.lowerRightDiagonalX;
      case 3 -> urx = obstacleShape.upperRightDiagonalX;
      case 5 -> ulx = obstacleShape.upperLeftDiagonalX;
      case 7 -> llx = obstacleShape.lowerLeftDiagonalX;
      default ->
          FRLogger.warn(
              "ShapeSearchTree45Degree.calc_inside_restrained_shape:"
                  + " obstacleLineNo out of range");
    }

    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
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
        TileShape currentTileShape = currentShape.boundingOctagon();
        if (currentTileShape.isIntBox()) {
          currentTileShape = currentShape.boundingBox();

          // To avoid small corner cutoffs when taking the offset as an octagon.
          // That may complicate the room division in the maze expand algorithm unnecessary.
        }

        int offsetWidth =
            this.clearanceCompensationValue(drillItem.clearanceClassNo(), drillItem.shapeLayer(i));
        offsetWidth += drillHoleClearanceDelta(drillItem, currentShape, drillItem.shapeLayer(i));
        currentTileShape = (TileShape) currentTileShape.offset(offsetWidth);
        result[i] = currentTileShape.boundingOctagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(ObstacleArea obstacleArea) {
    TileShape[] result = super.calculateTreeShapes(obstacleArea);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingOctagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(BoardOutline outline) {
    TileShape[] result = super.calculateTreeShapes(outline);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingOctagon();
      }
    }
    return result;
  }

  private static String describeBounds(IntBox bounds) {
    return "[(" + bounds.ll.x + "," + bounds.ll.y + ")..(" + bounds.ur.x + "," + bounds.ur.y + ")]";
  }

  private static boolean isCompleteShapeDebugAnchor(
      int netNo, int roomLayer, IntOctagon startShape) {
    return netNo == 77
        && roomLayer == 0
        && startShape.leftX == 1762393
        && startShape.bottomY == -1080137
        && startShape.rightX == 1910447
        && startShape.topY == -1006110;
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
      int step, int netNo, int roomLayer, SearchTreeObject object, IntOctagon obstacleShape) {
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
            + describeBounds(obstacleShape.boundingBox()));
  }

  private static void traceCompleteShapeDecision(
      int step,
      int netNo,
      int roomLayer,
      String action,
      boolean overlap,
      IntOctagon roomShape,
      IntOctagon obstacleShape) {
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
            + describeBounds(roomShape.boundingBox())
            + ", obstacle_bounds="
            + describeBounds(obstacleShape.boundingBox()));
  }

  private static int obstacleId(SearchTreeObject object) {
    return object instanceof Item item ? item.getIdNo() : -1;
  }

  private static String obstacleNets(SearchTreeObject object) {
    return object instanceof Item item ? java.util.Arrays.toString(item.netNoArr) : "[]";
  }
}

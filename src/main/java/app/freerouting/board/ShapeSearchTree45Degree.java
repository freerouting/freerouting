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
  public ShapeSearchTree45Degree(BasicBoard pBoard, int pCompensatedClearanceClassNo) {
    super(FortyfiveDegreeBoundingDirections.INSTANCE, pBoard, pCompensatedClearanceClassNo);
  }

  /**
   * Checks, if the border line segment with index p_obstacle_border_line_no intersects with the
   * inside of p_room_shape.
   */
  private static boolean obstacleSegmentTouchesInside(
      IntOctagon pObstacleShape, int pObstacleBorderLineNo, IntOctagon pRoomShape) {
    int currBorderLineNo = pObstacleBorderLineNo;
    int currObstacleCornerX = pObstacleShape.cornerX(pObstacleBorderLineNo);
    int currObstacleCornerY = pObstacleShape.cornerY(pObstacleBorderLineNo);
    for (int j = 0; j < 5; j++) {

      if (pRoomShape.sideOfBorderLine(currObstacleCornerX, currObstacleCornerY, currBorderLineNo)
          != Side.ON_THE_LEFT) {
        return false;
      }
      currBorderLineNo = (currBorderLineNo + 1) % 8;
    }

    int nextObstacleBorderLineNo = (pObstacleBorderLineNo + 1) % 8;
    int nextObstacleCornerX = pObstacleShape.cornerX(nextObstacleBorderLineNo);
    int nextObstacleCornerY = pObstacleShape.cornerY(nextObstacleBorderLineNo);
    currBorderLineNo = (pObstacleBorderLineNo + 5) % 8;
    for (int j = 0; j < 3; j++) {
      if (pRoomShape.sideOfBorderLine(nextObstacleCornerX, nextObstacleCornerY, currBorderLineNo)
          != Side.ON_THE_LEFT) {
        return false;
      }
      currBorderLineNo = (currBorderLineNo + 1) % 8;
    }
    return true;
  }

  private static double signedLineDistance(
      IntOctagon pObstacleShape, int pObstacleLineNo, IntOctagon pContainedShape) {
    return switch (pObstacleLineNo) {
      case 0 -> pObstacleShape.bottomY - pContainedShape.topY;
      case 2 -> pContainedShape.leftX - pObstacleShape.rightX;
      case 4 -> pContainedShape.bottomY - pObstacleShape.topY;
      case 6 -> pObstacleShape.leftX - pContainedShape.rightX;

      // factor 0.5 used instead to 1 / sqrt(2) to prefer orthogonal lines slightly to diagonal
      // restraining lines.
      case 1 -> 0.5 * (pContainedShape.upperLeftDiagonalX - pObstacleShape.lowerRightDiagonalX);
      case 3 -> 0.5 * (pContainedShape.lowerLeftDiagonalX - pObstacleShape.upperRightDiagonalX);
      case 5 -> 0.5 * (pObstacleShape.upperLeftDiagonalX - pContainedShape.lowerRightDiagonalX);
      case 7 -> 0.5 * (pObstacleShape.lowerLeftDiagonalX - pContainedShape.upperRightDiagonalX);
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
      IncompleteFreeSpaceExpansionRoom pRoom,
      int pNetNo,
      SearchTreeObject pIgnoreObject,
      TileShape pIgnoreShape) {
    TileShape containedRaw = pRoom.getContainedShape();
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
    if (pRoom.getShape() != null) {
      if (!(pRoom.getShape() instanceof IntOctagon)) {
        FRLogger.warn(
            "ShapeSearchTree45Degree.complete_shape: p_start_shape of type IntOctagon expected");
        return new LinkedList<>();
      }
      startShape = pRoom.getShape().boundingOctagon().intersection(startShape);
    }

    IntOctagon boundingShape = startShape;
    int roomLayer = pRoom.getLayer();
    boolean debugAnchor = isCompleteShapeDebugAnchor(pNetNo, roomLayer, startShape);
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
          boolean isObstacle = currObject.isTraceObstacle(pNetNo);

          int shapeIndex = currLeaf.shapeIndexInObject;
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

            IntOctagon currObjectShape =
                currObject.getTreeShape(this, shapeIndex).boundingOctagon();
            if (debugAnchor) {
              traceCompleteShapeCandidate(
                  debugStep, pNetNo, roomLayer, currObject, currObjectShape);
            }
            Collection<IncompleteFreeSpaceExpansionRoom> newResult = new LinkedList<>();
            IntOctagon newBoundingShape = IntOctagon.EMPTY;
            boolean hadRoomsBeforeObstacle = !result.isEmpty();
            for (IncompleteFreeSpaceExpansionRoom currRoom : result) {
              IntOctagon currShape = (IntOctagon) currRoom.getShape();
              boolean overlaps = currShape.overlaps(currObjectShape);
              if (overlaps) {
                if (currObject instanceof CompleteFreeSpaceExpansionRoom && pIgnoreShape != null) {
                  IntOctagon intersection = currShape.intersection(currObjectShape);
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
                    if (!pIgnoreShape.contains(currShape)) {
                      newResult.add(currRoom);
                      newBoundingShape = newBoundingShape.union(currShape.boundingBox());
                    }
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
      Collection<IncompleteFreeSpaceExpansionRoom> pRoomList, IntBox pBoardBoundingBox) {
    Collection<IncompleteFreeSpaceExpansionRoom> result =
        super.divideLargeRoom(pRoomList, pBoardBoundingBox);
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
      IncompleteFreeSpaceExpansionRoom pIncompleteRoom, IntOctagon pObstacleShape) {
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
    if (pIncompleteRoom.getShape() instanceof IntOctagon) {
      roomShape = pIncompleteRoom.getShape().boundingOctagon();
    } else if (pIncompleteRoom.getShape() instanceof Simplex) {
      roomShape = pIncompleteRoom.getShape().boundingOctagon();
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
          signedLineDistance(pObstacleShape, obstacle_line_no, shapeToBeContained);
      if (currDistance > cutLineDistance) {
        if (obstacleSegmentTouchesInside(pObstacleShape, obstacle_line_no, roomShape)) {
          cutLineDistance = currDistance;
          restrainingLineNo = obstacle_line_no;
        }
      }
    }
    if (cutLineDistance >= 0) {
      IntOctagon restrainedShape =
          calcOutsideRestrainedShape(pObstacleShape, restrainingLineNo, roomShape);
      result.add(
          new IncompleteFreeSpaceExpansionRoom(
              restrainedShape, pIncompleteRoom.getLayer(), shapeToBeContained));
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
      if (obstacleSegmentTouchesInside(pObstacleShape, obstacle_line_no, roomShape)) {
        Line currLine = pObstacleShape.borderLine(obstacle_line_no);
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
        calcOutsideRestrainedShape(pObstacleShape, restrainingLineNo, roomShape);
    if (restrainedShape.dimension() == 2) {
      IntOctagon newShapeToBeContained = shapeToBeContained.intersection(restrainedShape);
      if (newShapeToBeContained.dimension() > 0) {
        result.add(
            new IncompleteFreeSpaceExpansionRoom(
                restrainedShape, pIncompleteRoom.getLayer(), newShapeToBeContained));
      }
    }

    IntOctagon restPiece = calcInsideRestrainedShape(pObstacleShape, restrainingLineNo, roomShape);
    if (restPiece.dimension() >= 2) {
      TileShape restShapeToBeContained = shapeToBeContained.intersection(restPiece);
      if (restShapeToBeContained.dimension() >= 0) {
        IncompleteFreeSpaceExpansionRoom restIncompleteRoom =
            new IncompleteFreeSpaceExpansionRoom(
                restPiece, pIncompleteRoom.getLayer(), restShapeToBeContained);
        result.addAll(restrainShape(restIncompleteRoom, pObstacleShape));
      }
    }
    return result;
  }

  /**
   * Intersects p_room_shape with the half plane defined by the outside of the borderline with index
   * p_obstacle_line_no of p_obstacle_shape.
   */
  IntOctagon calcOutsideRestrainedShape(
      IntOctagon pObstacleShape, int pObstacleLineNo, IntOctagon pRoomShape) {
    int lx = pRoomShape.leftX;
    int ly = pRoomShape.bottomY;
    int rx = pRoomShape.rightX;
    int uy = pRoomShape.topY;
    int ulx = pRoomShape.upperLeftDiagonalX;
    int lrx = pRoomShape.lowerRightDiagonalX;
    int llx = pRoomShape.lowerLeftDiagonalX;
    int urx = pRoomShape.upperRightDiagonalX;

    switch (pObstacleLineNo) {
      case 0 -> uy = pObstacleShape.bottomY;
      case 2 -> lx = pObstacleShape.rightX;
      case 4 -> ly = pObstacleShape.topY;
      case 6 -> rx = pObstacleShape.leftX;
      case 1 -> ulx = pObstacleShape.lowerRightDiagonalX;
      case 3 -> llx = pObstacleShape.upperRightDiagonalX;
      case 5 -> lrx = pObstacleShape.upperLeftDiagonalX;
      case 7 -> urx = pObstacleShape.lowerLeftDiagonalX;
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
      IntOctagon pObstacleShape, int pObstacleLineNo, IntOctagon pRoomShape) {
    int lx = pRoomShape.leftX;
    int ly = pRoomShape.bottomY;
    int rx = pRoomShape.rightX;
    int uy = pRoomShape.topY;
    int ulx = pRoomShape.upperLeftDiagonalX;
    int lrx = pRoomShape.lowerRightDiagonalX;
    int llx = pRoomShape.lowerLeftDiagonalX;
    int urx = pRoomShape.upperRightDiagonalX;

    switch (pObstacleLineNo) {
      case 0 -> ly = pObstacleShape.bottomY;
      case 2 -> rx = pObstacleShape.rightX;
      case 4 -> uy = pObstacleShape.topY;
      case 6 -> lx = pObstacleShape.leftX;
      case 1 -> lrx = pObstacleShape.lowerRightDiagonalX;
      case 3 -> urx = pObstacleShape.upperRightDiagonalX;
      case 5 -> ulx = pObstacleShape.upperLeftDiagonalX;
      case 7 -> llx = pObstacleShape.lowerLeftDiagonalX;
      default ->
          FRLogger.warn(
              "ShapeSearchTree45Degree.calc_inside_restrained_shape: p_obstacle_line_no out of range");
    }

    IntOctagon result = new IntOctagon(lx, ly, rx, uy, ulx, lrx, llx, urx);
    return result.normalize();
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
        TileShape currTileShape = currShape.boundingOctagon();
        if (currTileShape.isIntBox()) {
          currTileShape = currShape.boundingBox();

          // To avoid small corner cutoffs when taking the offset as an octagon.
          // That may complicate the room division in the maze expand algorithm unnecessary.
        }

        int offsetWidth =
            this.clearanceCompensationValue(
                pDrillItem.clearanceClassNo(), pDrillItem.shapeLayer(i));
        offsetWidth += drillHoleClearanceDelta(pDrillItem, currShape, pDrillItem.shapeLayer(i));
        currTileShape = (TileShape) currTileShape.offset(offsetWidth);
        result[i] = currTileShape.boundingOctagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(ObstacleArea pObstacleArea) {
    TileShape[] result = super.calculateTreeShapes(pObstacleArea);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingOctagon();
      }
    }
    return result;
  }

  @Override
  TileShape[] calculateTreeShapes(BoardOutline pOutline) {
    TileShape[] result = super.calculateTreeShapes(pOutline);
    for (int i = 0; i < result.length; i++) {
      if (result[i] != null) {
        result[i] = result[i].boundingOctagon();
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

  private static boolean isCompleteShapeDebugAnchor(
      int pNetNo, int pRoomLayer, IntOctagon pStartShape) {
    return pNetNo == 77
        && pRoomLayer == 0
        && pStartShape.leftX == 1762393
        && pStartShape.bottomY == -1080137
        && pStartShape.rightX == 1910447
        && pStartShape.topY == -1006110;
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
      int pStep, int pNetNo, int pRoomLayer, SearchTreeObject pObject, IntOctagon pObstacleShape) {
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
            + describeBounds(pObstacleShape.boundingBox()));
  }

  private static void traceCompleteShapeDecision(
      int pStep,
      int pNetNo,
      int pRoomLayer,
      String pAction,
      boolean pOverlap,
      IntOctagon pRoomShape,
      IntOctagon pObstacleShape) {
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
            + describeBounds(pRoomShape.boundingBox())
            + ", obstacle_bounds="
            + describeBounds(pObstacleShape.boundingBox()));
  }

  private static int obstacleId(SearchTreeObject pObject) {
    return pObject instanceof Item item ? item.getIdNo() : -1;
  }

  private static String obstacleNets(SearchTreeObject pObject) {
    return pObject instanceof Item item ? java.util.Arrays.toString(item.netNoArr) : "[]";
  }
}

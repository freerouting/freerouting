package app.freerouting.board;

import app.freerouting.board.optimize.TraceShover;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.board.searchtree.ShapeTraceEntries;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Class with functions for checking and inserting pads with eventually shoving aside obstacle
 * traces.
 */
public class ForcedPadRouter {

  private final RoutingBoard board;

  /** Creates a new instance of ForcedPadAlgo. */
  public ForcedPadRouter(RoutingBoard board) {
    this.board = board;
  }

  private static TileShape calcCheckShapeForFromSide(
      TileShape shape, Point shapeCenter, Line borderLine) {
    FloatPoint shapeCenterFloat = shapeCenter.toFloat();
    FloatPoint offsetProjection = shapeCenterFloat.projectionApprox(borderLine);
    // Make sure, that direction restrictions are retained.
    Line[] lines = new Line[3];
    Direction currentDirection = borderLine.direction();
    lines[0] = new Line(shapeCenter, currentDirection);
    lines[1] = new Line(shapeCenter, currentDirection.turn45Degree(2));
    lines[2] = new Line(offsetProjection.round(), currentDirection);
    Polyline checkLine = new Polyline(lines);
    return checkLine.offsetShape(1, 0);
  }

  /** Checks, if line is in front of padShape when shoving from fromSide. */
  private static boolean inFrontOfPad(
      Line line, TileShape padShape, int fromSide, int width, boolean withSides) {
    if (!padShape.isIntOctagon()) {
      // only implemented for octagons
      return true;
    }
    IntOctagon padOctagon = padShape.boundingOctagon();
    if (!(line.a instanceof IntPoint lineA && line.b instanceof IntPoint lineB)) {
      // not implemented
      return true;
    }

    double diagWidth = width * Math.sqrt(2);

    boolean result;
    switch (fromSide) {
      case 0 -> {
        result =
            Math.min(lineA.y, lineB.y) >= padOctagon.topY + width
                || Math.max(lineA.x - lineA.y, lineB.x - lineB.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.min(lineA.x + lineA.y, lineB.x + lineB.x)
                    >= padOctagon.upperRightDiagonalX + diagWidth;
        if (withSides && !result) {
          result =
              Math.max(lineA.x, lineB.x) <= padOctagon.leftX - width
                      && Math.min(lineA.x - lineA.y, lineB.x - lineB.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth
                  || Math.min(lineA.x, lineB.x) >= padOctagon.rightX + width
                      && Math.min(lineA.x + lineA.y, lineB.x + lineB.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 1 -> {
        result =
            Math.min(lineA.y, lineB.y) >= padOctagon.topY + width
                || Math.max(lineA.x - lineA.y, lineB.x - lineB.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.max(lineA.x, lineB.x) <= padOctagon.leftX - width;
        if (withSides && !result) {
          result =
              Math.min(lineA.x, lineB.x) <= padOctagon.leftX - width
                      && Math.max(lineA.x + lineA.y, lineB.x + lineB.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth
                  || Math.max(lineA.y, lineB.y) >= padOctagon.topY + width
                      && Math.min(lineA.x + lineA.y, lineB.x + lineB.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 2 -> {
        result =
            Math.max(lineA.x, lineB.x) <= padOctagon.leftX - width
                || Math.max(lineA.x - lineA.y, lineB.x - lineB.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.max(lineA.x + lineA.y, lineB.x + lineB.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth;
        if (withSides && !result) {
          result =
              Math.max(lineA.y, lineB.y) <= padOctagon.bottomY - width
                      && Math.min(lineA.x + lineA.y, lineB.x + lineB.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth
                  || Math.min(lineA.y, lineB.y) >= padOctagon.topY + width
                      && Math.min(lineA.x - lineA.y, lineB.x - lineB.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth;
        }
      }
      case 3 -> {
        result =
            Math.max(lineA.x, lineB.x) <= padOctagon.leftX - width
                || Math.max(lineA.y, lineB.y) <= padOctagon.bottomY - width
                || Math.max(lineA.x + lineA.y, lineB.x + lineB.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth;
        if (withSides && !result) {
          result =
              Math.min(lineA.y, lineB.y) <= padOctagon.bottomY - width
                      && Math.min(lineA.x - lineA.y, lineB.x - lineB.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.min(lineA.x, lineB.x) <= padOctagon.leftX - width
                      && Math.max(lineA.x - lineA.y, lineB.x - lineB.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth;
        }
      }
      case 4 -> {
        result =
            Math.max(lineA.y, lineB.y) <= padOctagon.bottomY - width
                || Math.max(lineA.x + lineA.y, lineB.x + lineB.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth
                || Math.min(lineA.x - lineA.y, lineB.x - lineB.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (withSides && !result) {
          result =
              Math.min(lineA.x, lineB.x) >= padOctagon.rightX + width
                      && Math.max(lineA.x - lineA.y, lineB.x - lineB.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.max(lineA.x, lineB.x) <= padOctagon.leftX - width
                      && Math.min(lineA.x + lineA.y, lineB.x + lineB.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth;
        }
      }
      case 5 -> {
        result =
            Math.max(lineA.y, lineB.y) <= padOctagon.bottomY - width
                || Math.min(lineA.x, lineB.x) >= padOctagon.rightX + width
                || Math.min(lineA.x - lineA.y, lineB.x - lineB.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (withSides && !result) {
          result =
              Math.max(lineA.x, lineB.x) >= padOctagon.rightX + width
                      && Math.min(lineA.x + lineA.y, lineB.x + lineB.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth
                  || Math.min(lineA.y, lineB.y) <= padOctagon.bottomY - width
                      && Math.max(lineA.x + lineA.y, lineB.x + lineB.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth;
        }
      }
      case 6 -> {
        result =
            Math.min(lineA.x, lineB.x) >= padOctagon.rightX + width
                || Math.min(lineA.x + lineA.y, lineB.x + lineB.y)
                    >= padOctagon.upperRightDiagonalX + diagWidth
                || Math.min(lineA.x - lineA.y, lineB.x - lineB.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (withSides && !result) {
          result =
              Math.max(lineA.y, lineB.y) <= padOctagon.bottomY - width
                      && Math.max(lineA.x - lineA.y, lineB.x - lineB.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.min(lineA.y, lineB.y) >= padOctagon.topY + width
                      && Math.max(lineA.x + lineA.y, lineB.x + lineB.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 7 -> {
        result =
            Math.min(lineA.y, lineB.y) >= padOctagon.topY + width
                || Math.min(lineA.x + lineA.y, lineB.x + lineB.y)
                    >= padOctagon.upperRightDiagonalX + diagWidth
                || Math.min(lineA.x, lineB.x) >= padOctagon.rightX + width;
        if (withSides && !result) {
          result =
              Math.max(lineA.y, lineB.y) >= padOctagon.topY + width
                      && Math.max(lineA.x - lineA.y, lineB.x - lineB.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth
                  || Math.max(lineA.x, lineB.x) >= padOctagon.rightX + width
                      && Math.min(lineA.x - lineA.y, lineB.x - lineB.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth;
        }
      }
      default -> {
        FRLogger.warn("ForcedPadAlgo.in_front_of_pad: fromSide out of range");
        result = true;
      }
    }

    return result;
  }

  /**
   * Checks, if possible obstacle traces can be shoved aside, so that a pad with the input
   * parameters can be inserted without clearance violations. Returns false, if the check failed. If
   * ignoreItems != null, items in this list are not checked, If checkOnlyFront only trace obstacles
   * in the direction from fromSide are checked for performance reasons. This is the cave when
   * moving drill_items
   */
  public CheckDrillResult checkForcedPad(
      TileShape padShape,
      ShapeEntrySide fromSide,
      int layer,
      int[] netNumbers,
      int clearanceClassIndex,
      boolean copperSharingAllowed,
      Collection<Item> ignoreItems,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      boolean checkOnlyFront,
      TimeLimit timeLimit) {
    if (!padShape.isContainedIn(board.getBoundingBox())) {
      this.board.setShoveFailingObstacle(board.getOutline());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(padShape, layer, netNumbers, clearanceClassIndex, fromSide, board);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(padShape, layer, new int[0], clearanceClassIndex);

    if (ignoreItems != null) {
      obstacles.removeAll(ignoreItems);
    }
    boolean obstaclesShovable = shapeEntries.storeItems(obstacles, true, copperSharingAllowed);
    if (!obstaclesShovable) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }

    // check, if the obstacle vias can be shoved

    for (Via currentShoveVia : shapeEntries.shoveViaList) {
      if (maxViaRecursionDepth <= 0) {
        this.board.setShoveFailingObstacle(currentShoveVia);
        return CheckDrillResult.NOT_DRILLABLE;
      }
      IntPoint[] newViaCenter =
          DrillItemMover.tryShoveViaPoints(
              padShape, layer, currentShoveVia, clearanceClassIndex, false, board);

      if (newViaCenter.length == 0) {
        this.board.setShoveFailingObstacle(currentShoveVia);
        return CheckDrillResult.NOT_DRILLABLE;
      }
      Vector delta = newViaCenter[0].differenceBy(currentShoveVia.getCenter());
      Collection<Item> checkIgnoreItems = new LinkedList<>();
      if (!DrillItemMover.check(
          currentShoveVia,
          delta,
          maxRecursionDepth,
          maxViaRecursionDepth - 1,
          checkIgnoreItems,
          this.board,
          timeLimit)) {
        return CheckDrillResult.NOT_DRILLABLE;
      }
    }
    CheckDrillResult result = CheckDrillResult.DRILLABLE;
    if (copperSharingAllowed) {
      for (Item currentObstacle : obstacles) {
        if (currentObstacle instanceof Pin) {
          result = CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD;
          break;
        }
      }
    }
    int tracePieceCount = shapeEntries.substituteTraceCount();
    if (tracePieceCount == 0) {
      return result;
    }
    if (maxRecursionDepth <= 0) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    if (shapeEntries.stackDepth() > 1) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    TraceShover shoveTraceAlgo = new TraceShover(board);
    boolean isOrthogonalMode = padShape instanceof IntBox;
    for (; ; ) {
      PolylineTrace currentSubstituteTrace = shapeEntries.nextSubstituteTracePiece();
      if (currentSubstituteTrace == null) {
        break;
      }
      for (int i = 0; i < currentSubstituteTrace.tileShapeCount(); i++) {
        Line currentLine = currentSubstituteTrace.polyline().lines[i + 1];
        Direction currentDirection = currentLine.direction();
        boolean isInFront;
        if (checkOnlyFront) {
          isInFront =
              inFrontOfPad(
                  currentLine, padShape, fromSide.no, currentSubstituteTrace.getHalfWidth(), true);
        } else {
          isInFront = true;
        }
        if (isInFront) {
          ShapeAndEntrySide current =
              new ShapeAndEntrySide(currentSubstituteTrace, i, isOrthogonalMode, true);
          if (!shoveTraceAlgo.check(
              current.shape,
              current.fromSide,
              currentDirection,
              layer,
              currentSubstituteTrace.netNumbers,
              currentSubstituteTrace.clearanceClassIndex(),
              maxRecursionDepth - 1,
              maxViaRecursionDepth,
              0,
              timeLimit)) {
            return CheckDrillResult.NOT_DRILLABLE;
          }
        }
      }
    }
    return result;
  }

  /**
   * Shoves aside traces, so that a pad with the input parameters can be inserted without clearance
   * violations. Returns false, if the shove failed. In this case the database may be damaged, so
   * that an undo becomes necessary.
   */
  boolean forcedPad(
      TileShape padShape,
      ShapeEntrySide fromSide,
      int layer,
      int[] netNumbers,
      int clearanceClassIndex,
      boolean copperSharingAllowed,
      Collection<Item> ignoreItems,
      int maxRecursionDepth,
      int maxViaRecursionDepth) {
    if (padShape.isEmpty()) {
      FRLogger.warn("ShoveTraceAux.forced_pad: padShape is empty");
      return true;
    }
    if (!padShape.isContainedIn(board.getBoundingBox())) {
      this.board.setShoveFailingObstacle(board.getOutline());
      return false;
    }
    if (!DrillItemMover.shoveVias(
        padShape,
        fromSide,
        layer,
        netNumbers,
        clearanceClassIndex,
        ignoreItems,
        maxRecursionDepth,
        maxViaRecursionDepth,
        false,
        this.board)) {
      return false;
    }
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(padShape, layer, netNumbers, clearanceClassIndex, fromSide, board);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(padShape, layer, new int[0], clearanceClassIndex);
    if (ignoreItems != null) {
      obstacles.removeAll(ignoreItems);
    }
    boolean obstaclesShovable =
        shapeEntries.storeItems(obstacles, true, copperSharingAllowed)
            && shapeEntries.shoveViaList.isEmpty();
    if (!obstaclesShovable) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }
    int tracePieceCount = shapeEntries.substituteTraceCount();
    if (tracePieceCount == 0) {
      return true;
    }
    if (maxRecursionDepth <= 0) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }
    boolean tailsExistBefore = board.containsTraceTails(obstacles, netNumbers);
    shapeEntries.cutoutTraces(obstacles);
    boolean isOrthogonalMode = padShape instanceof IntBox;
    TraceShover shoveTraceAlgo = new TraceShover(this.board);
    for (; ; ) {
      PolylineTrace currentSubstituteTrace = shapeEntries.nextSubstituteTracePiece();
      if (currentSubstituteTrace == null) {
        break;
      }
      if (currentSubstituteTrace.firstCorner().equals(currentSubstituteTrace.lastCorner())) {
        continue;
      }
      int[] currentNetNumbers = currentSubstituteTrace.netNumbers;
      for (int i = 0; i < currentSubstituteTrace.tileShapeCount(); i++) {
        ShapeAndEntrySide current =
            new ShapeAndEntrySide(currentSubstituteTrace, i, isOrthogonalMode, false);
        if (!shoveTraceAlgo.insert(
            current.shape,
            current.fromSide,
            layer,
            currentNetNumbers,
            currentSubstituteTrace.clearanceClassIndex(),
            ignoreItems,
            maxRecursionDepth - 1,
            maxViaRecursionDepth,
            0)) {
          return false;
        }
      }
      for (int i = 0; i < currentSubstituteTrace.cornerCount(); i++) {
        board.joinChangedArea(currentSubstituteTrace.polyline().cornerApprox(i), layer);
      }
      Point[] endCorners = null;
      if (!tailsExistBefore) {
        endCorners = new Point[2];
        endCorners[0] = currentSubstituteTrace.firstCorner();
        endCorners[1] = currentSubstituteTrace.lastCorner();
      }
      board.insertItem(currentSubstituteTrace);
      IntOctagon optArea;
      if (board.changedArea != null) {
        optArea = board.changedArea.getArea(layer);
      } else {
        optArea = null;
      }

      try {
        currentSubstituteTrace.normalize(optArea);
      } catch (Exception e) {
        FRLogger.error("Couldn't normalize trace.", e);
      }

      if (!tailsExistBefore) {
        for (int i = 0; i < 2; i++) {
          Trace tail = board.getTraceTail(endCorners[i], layer, currentNetNumbers);
          if (tail != null) {
            board.removeItems(tail.getConnectionItems(Item.StopConnectionOption.VIA));
            for (int currentNetNumber : currentNetNumbers) {
              board.combineTraces(currentNetNumber);
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * Looks for a side of shape, so that a trace line from the shape center to the nearest point on
   * this side does not conflict with any obstacles.
   */
  ShapeEntrySide calcFromSide(
      TileShape shape, Point shapeCenter, int layer, int offset, int clearanceClassIndex) {
    int[] emptyArr = new int[0];
    TileShape offsetShape = (TileShape) shape.offset(offset);
    for (int i = 0; i < offsetShape.borderLineCount(); i++) {
      TileShape checkShape =
          calcCheckShapeForFromSide(shape, shapeCenter, offsetShape.borderLine(i));

      if (board.checkTraceShape(checkShape, layer, emptyArr, clearanceClassIndex, null)) {
        return new ShapeEntrySide(i, null);
      }
    }
    // try second check without clearance
    for (int i = 0; i < offsetShape.borderLineCount(); i++) {
      TileShape checkShape =
          calcCheckShapeForFromSide(shape, shapeCenter, offsetShape.borderLine(i));
      if (board.checkTraceShape(checkShape, layer, emptyArr, 0, null)) {
        return new ShapeEntrySide(i, null);
      }
    }
    return ShapeEntrySide.NOT_CALCULATED;
  }

  /** CheckDrillResult. */
  public enum CheckDrillResult {
    DRILLABLE,
    DRILLABLE_WITH_ATTACH_SMD,
    NOT_DRILLABLE
  }
}

package app.freerouting.board;

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
public class ForcedPadAlgo {

  private final RoutingBoard board;

  /** Creates a new instance of ForcedPadAlgo */
  public ForcedPadAlgo(RoutingBoard pBoard) {
    board = pBoard;
  }

  private static TileShape calcCheckShapeForFromSide(
      TileShape pShape, Point pShapeCenter, Line pBorderLine) {
    FloatPoint shapeCenter = pShapeCenter.toFloat();
    FloatPoint offsetProjection = shapeCenter.projectionApprox(pBorderLine);
    // Make sure, that direction restrictions are retained.
    Line[] lineArr = new Line[3];
    Direction currDir = pBorderLine.direction();
    lineArr[0] = new Line(pShapeCenter, currDir);
    lineArr[1] = new Line(pShapeCenter, currDir.turn45Degree(2));
    lineArr[2] = new Line(offsetProjection.round(), currDir);
    Polyline checkLine = new Polyline(lineArr);
    return checkLine.offsetShape(1, 0);
  }

  /** Checks, if p_line is in front of p_pad_shape when shoving from p_from_side */
  private static boolean inFrontOfPad(
      Line pLine, TileShape pPadShape, int pFromSide, int pWidth, boolean pWithSides) {
    if (!pPadShape.isIntOctagon()) {
      // only implemented for octagons
      return true;
    }
    IntOctagon padOctagon = pPadShape.boundingOctagon();
    if (!(pLine.a instanceof IntPoint lineA && pLine.b instanceof IntPoint line_b)) {
      // not implemented
      return true;
    }

    double diagWidth = pWidth * Math.sqrt(2);

    boolean result;
    switch (pFromSide) {
      case 0 -> {
        result =
            Math.min(lineA.y, line_b.y) >= padOctagon.topY + pWidth
                || Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.min(lineA.x + lineA.y, line_b.x + line_b.x)
                    >= padOctagon.upperRightDiagonalX + diagWidth;
        if (pWithSides && !result) {
          result =
              Math.max(lineA.x, line_b.x) <= padOctagon.leftX - pWidth
                      && Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth
                  || Math.min(lineA.x, line_b.x) >= padOctagon.rightX + pWidth
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 1 -> {
        result =
            Math.min(lineA.y, line_b.y) >= padOctagon.topY + pWidth
                || Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.max(lineA.x, line_b.x) <= padOctagon.leftX - pWidth;
        if (pWithSides && !result) {
          result =
              Math.min(lineA.x, line_b.x) <= padOctagon.leftX - pWidth
                      && Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth
                  || Math.max(lineA.y, line_b.y) >= padOctagon.topY + pWidth
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 2 -> {
        result =
            Math.max(lineA.x, line_b.x) <= padOctagon.leftX - pWidth
                || Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                    <= padOctagon.upperLeftDiagonalX - diagWidth
                || Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth;
        if (pWithSides && !result) {
          result =
              Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - pWidth
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth
                  || Math.min(lineA.y, line_b.y) >= padOctagon.topY + pWidth
                      && Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth;
        }
      }
      case 3 -> {
        result =
            Math.max(lineA.x, line_b.x) <= padOctagon.leftX - pWidth
                || Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - pWidth
                || Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth;
        if (pWithSides && !result) {
          result =
              Math.min(lineA.y, line_b.y) <= padOctagon.bottomY - pWidth
                      && Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.min(lineA.x, line_b.x) <= padOctagon.leftX - pWidth
                      && Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth;
        }
      }
      case 4 -> {
        result =
            Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - pWidth
                || Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                    <= padOctagon.lowerLeftDiagonalX - diagWidth
                || Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (pWithSides && !result) {
          result =
              Math.min(lineA.x, line_b.x) >= padOctagon.rightX + pWidth
                      && Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.max(lineA.x, line_b.x) <= padOctagon.leftX - pWidth
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth;
        }
      }
      case 5 -> {
        result =
            Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - pWidth
                || Math.min(lineA.x, line_b.x) >= padOctagon.rightX + pWidth
                || Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (pWithSides && !result) {
          result =
              Math.max(lineA.x, line_b.x) >= padOctagon.rightX + pWidth
                      && Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth
                  || Math.min(lineA.y, line_b.y) <= padOctagon.bottomY - pWidth
                      && Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                          <= padOctagon.lowerLeftDiagonalX - diagWidth;
        }
      }
      case 6 -> {
        result =
            Math.min(lineA.x, line_b.x) >= padOctagon.rightX + pWidth
                || Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                    >= padOctagon.upperRightDiagonalX + diagWidth
                || Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                    >= padOctagon.lowerRightDiagonalX + diagWidth;
        if (pWithSides && !result) {
          result =
              Math.max(lineA.y, line_b.y) <= padOctagon.bottomY - pWidth
                      && Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth
                  || Math.min(lineA.y, line_b.y) >= padOctagon.topY + pWidth
                      && Math.max(lineA.x + lineA.y, line_b.x + line_b.y)
                          >= padOctagon.upperRightDiagonalX + diagWidth;
        }
      }
      case 7 -> {
        result =
            Math.min(lineA.y, line_b.y) >= padOctagon.topY + pWidth
                || Math.min(lineA.x + lineA.y, line_b.x + line_b.y)
                    >= padOctagon.upperRightDiagonalX + diagWidth
                || Math.min(lineA.x, line_b.x) >= padOctagon.rightX + pWidth;
        if (pWithSides && !result) {
          result =
              Math.max(lineA.y, line_b.y) >= padOctagon.topY + pWidth
                      && Math.max(lineA.x - lineA.y, line_b.x - line_b.y)
                          <= padOctagon.upperLeftDiagonalX - diagWidth
                  || Math.max(lineA.x, line_b.x) >= padOctagon.rightX + pWidth
                      && Math.min(lineA.x - lineA.y, line_b.x - line_b.y)
                          >= padOctagon.lowerRightDiagonalX + diagWidth;
        }
      }
      default -> {
        FRLogger.warn("ForcedPadAlgo.in_front_of_pad: p_from_side out of range");
        result = true;
      }
    }

    return result;
  }

  /**
   * Checks, if possible obstacle traces can be shoved aside, so that a pad with the input
   * parameters can be inserted without clearance violations. Returns false, if the check failed. If
   * p_ignore_items != null, items in this list are not checked, If p_check_only_front only trace
   * obstacles in the direction from p_from_side are checked for performance reasons. This is the
   * cave when moving drill_items
   */
  public CheckDrillResult checkForcedPad(
      TileShape pPadShape,
      CalcFromSide pFromSide,
      int pLayer,
      int[] pNetNoArr,
      int pClType,
      boolean pCopperSharingAllowed,
      Collection<Item> pIgnoreItems,
      int pMaxRecursionDepth,
      int pMaxViaRecursionDepth,
      boolean pCheckOnlyFront,
      TimeLimit pTimeLimit) {
    if (!pPadShape.isContainedIn(board.getBoundingBox())) {
      this.board.setShoveFailingObstacle(board.getOutline());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(pPadShape, pLayer, pNetNoArr, pClType, pFromSide, board);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(pPadShape, pLayer, new int[0], pClType);

    if (pIgnoreItems != null) {
      obstacles.removeAll(pIgnoreItems);
    }
    boolean obstaclesShovable = shapeEntries.storeItems(obstacles, true, pCopperSharingAllowed);
    if (!obstaclesShovable) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }

    // check, if the obstacle vias can be shoved

    for (Via currShoveVia : shapeEntries.shoveViaList) {
      if (pMaxViaRecursionDepth <= 0) {
        this.board.setShoveFailingObstacle(currShoveVia);
        return CheckDrillResult.NOT_DRILLABLE;
      }
      IntPoint[] newViaCenter =
          MoveDrillItemAlgo.tryShoveViaPoints(
              pPadShape, pLayer, currShoveVia, pClType, false, board);

      if (newViaCenter.length == 0) {
        this.board.setShoveFailingObstacle(currShoveVia);
        return CheckDrillResult.NOT_DRILLABLE;
      }
      Vector delta = newViaCenter[0].differenceBy(currShoveVia.getCenter());
      Collection<Item> ignoreItems = new LinkedList<>();
      if (!MoveDrillItemAlgo.check(
          currShoveVia,
          delta,
          pMaxRecursionDepth,
          pMaxViaRecursionDepth - 1,
          ignoreItems,
          this.board,
          pTimeLimit)) {
        return CheckDrillResult.NOT_DRILLABLE;
      }
    }
    CheckDrillResult result = CheckDrillResult.DRILLABLE;
    if (pCopperSharingAllowed) {
      for (Item currObstacle : obstacles) {
        if (currObstacle instanceof Pin) {
          result = CheckDrillResult.DRILLABLE_WITH_ATTACH_SMD;
          break;
        }
      }
    }
    int tracePieceCount = shapeEntries.substituteTraceCount();
    if (tracePieceCount == 0) {
      return result;
    }
    if (pMaxRecursionDepth <= 0) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    if (shapeEntries.stackDepth() > 1) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return CheckDrillResult.NOT_DRILLABLE;
    }
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(board);
    boolean isOrthogonalMode = pPadShape instanceof IntBox;
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.nextSubstituteTracePiece();
      if (currSubstituteTrace == null) {
        break;
      }
      for (int i = 0; i < currSubstituteTrace.tileShapeCount(); i++) {
        Line currLine = currSubstituteTrace.polyline().arr[i + 1];
        Direction currDir = currLine.direction();
        boolean isInFront;
        if (pCheckOnlyFront) {
          isInFront =
              inFrontOfPad(
                  currLine, pPadShape, pFromSide.no, currSubstituteTrace.getHalfWidth(), true);
        } else {
          isInFront = true;
        }
        if (isInFront) {
          CalcShapeAndFromSide curr =
              new CalcShapeAndFromSide(currSubstituteTrace, i, isOrthogonalMode, true);
          if (!shoveTraceAlgo.check(
              curr.shape,
              curr.fromSide,
              currDir,
              pLayer,
              currSubstituteTrace.netNoArr,
              currSubstituteTrace.clearanceClassNo(),
              pMaxRecursionDepth - 1,
              pMaxViaRecursionDepth,
              0,
              pTimeLimit)) {
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
      TileShape pPadShape,
      CalcFromSide pFromSide,
      int pLayer,
      int[] pNetNoArr,
      int pClType,
      boolean pCopperSharingAllowed,
      Collection<Item> pIgnoreItems,
      int pMaxRecursionDepth,
      int pMaxViaRecursionDepth) {
    if (pPadShape.isEmpty()) {
      FRLogger.warn("ShoveTraceAux.forced_pad: p_pad_shape is empty");
      return true;
    }
    if (!pPadShape.isContainedIn(board.getBoundingBox())) {
      this.board.setShoveFailingObstacle(board.getOutline());
      return false;
    }
    if (!MoveDrillItemAlgo.shoveVias(
        pPadShape,
        pFromSide,
        pLayer,
        pNetNoArr,
        pClType,
        pIgnoreItems,
        pMaxRecursionDepth,
        pMaxViaRecursionDepth,
        false,
        this.board)) {
      return false;
    }
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(pPadShape, pLayer, pNetNoArr, pClType, pFromSide, board);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(pPadShape, pLayer, new int[0], pClType);
    if (pIgnoreItems != null) {
      obstacles.removeAll(pIgnoreItems);
    }
    boolean obstaclesShovable =
        shapeEntries.storeItems(obstacles, true, pCopperSharingAllowed)
            && shapeEntries.shoveViaList.isEmpty();
    if (!obstaclesShovable) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }
    int tracePieceCount = shapeEntries.substituteTraceCount();
    if (tracePieceCount == 0) {
      return true;
    }
    if (pMaxRecursionDepth <= 0) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }
    boolean tailsExistBefore = board.containsTraceTails(obstacles, pNetNoArr);
    shapeEntries.cutoutTraces(obstacles);
    boolean isOrthogonalMode = pPadShape instanceof IntBox;
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(this.board);
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.nextSubstituteTracePiece();
      if (currSubstituteTrace == null) {
        break;
      }
      if (currSubstituteTrace.firstCorner().equals(currSubstituteTrace.lastCorner())) {
        continue;
      }
      int[] currNetNoArr = currSubstituteTrace.netNoArr;
      for (int i = 0; i < currSubstituteTrace.tileShapeCount(); i++) {
        CalcShapeAndFromSide curr =
            new CalcShapeAndFromSide(currSubstituteTrace, i, isOrthogonalMode, false);
        if (!shoveTraceAlgo.insert(
            curr.shape,
            curr.fromSide,
            pLayer,
            currNetNoArr,
            currSubstituteTrace.clearanceClassNo(),
            pIgnoreItems,
            pMaxRecursionDepth - 1,
            pMaxViaRecursionDepth,
            0)) {
          return false;
        }
      }
      for (int i = 0; i < currSubstituteTrace.cornerCount(); i++) {
        board.joinChangedArea(currSubstituteTrace.polyline().cornerApprox(i), pLayer);
      }
      Point[] endCorners = null;
      if (!tailsExistBefore) {
        endCorners = new Point[2];
        endCorners[0] = currSubstituteTrace.firstCorner();
        endCorners[1] = currSubstituteTrace.lastCorner();
      }
      board.insertItem(currSubstituteTrace);
      IntOctagon optArea;
      if (board.changedArea != null) {
        optArea = board.changedArea.getArea(pLayer);
      } else {
        optArea = null;
      }

      try {
        currSubstituteTrace.normalize(optArea);
      } catch (Exception e) {
        FRLogger.error("Couldn't normalize trace.", e);
      }

      if (!tailsExistBefore) {
        for (int i = 0; i < 2; i++) {
          Trace tail = board.getTraceTail(endCorners[i], pLayer, currNetNoArr);
          if (tail != null) {
            board.removeItems(tail.getConnectionItems(Item.StopConnectionOption.VIA));
            for (int currNetNo : currNetNoArr) {
              board.combineTraces(currNetNo);
            }
          }
        }
      }
    }
    return true;
  }

  /**
   * Looks for a side of p_shape, so that a trace line from the shape center to the nearest point on
   * this side does not conflict with any obstacles.
   */
  CalcFromSide calcFromSide(
      TileShape pShape, Point pShapeCenter, int pLayer, int pOffset, int pClClass) {
    int[] emptyArr = new int[0];
    TileShape offsetShape = (TileShape) pShape.offset(pOffset);
    for (int i = 0; i < offsetShape.borderLineCount(); i++) {
      TileShape checkShape =
          calcCheckShapeForFromSide(pShape, pShapeCenter, offsetShape.borderLine(i));

      if (board.checkTraceShape(checkShape, pLayer, emptyArr, pClClass, null)) {
        return new CalcFromSide(i, null);
      }
    }
    // try second check without clearance
    for (int i = 0; i < offsetShape.borderLineCount(); i++) {
      TileShape checkShape =
          calcCheckShapeForFromSide(pShape, pShapeCenter, offsetShape.borderLine(i));
      if (board.checkTraceShape(checkShape, pLayer, emptyArr, 0, null)) {
        return new CalcFromSide(i, null);
      }
    }
    return CalcFromSide.NOT_CALCULATED;
  }

  public enum CheckDrillResult {
    DRILLABLE,
    DRILLABLE_WITH_ATTACH_SMD,
    NOT_DRILLABLE
  }
}

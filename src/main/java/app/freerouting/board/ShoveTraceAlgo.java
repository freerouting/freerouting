package app.freerouting.board;

import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.Direction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.ClearanceMatrix;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

/** Contains internal auxiliary functions of class RoutingBoard for shoving traces. */
public class ShoveTraceAlgo {

  private final RoutingBoard board;

  /** ShoveTraceAlgo. */
  public ShoveTraceAlgo(RoutingBoard board) {
    this.board = board;
  }

  /**
   * Checks if a shove with the input parameters is possible without clearance violations The result
   * is the maximum length of a trace from the start of the line segment to the end of the line
   * segment, for which the algorithm succeeds. If the algorithm succeeds completely, the result
   * will be equal to Integer.MAX_VALUE.
   */
  public static double check(
      RoutingBoard board,
      LineSegment lineSegment,
      boolean shoveToTheLeft,
      int layer,
      int[] netNoArr,
      int traceHalfWidth,
      int clType,
      int maxRecursionDepth,
      int maxViaRecursionDepth) {
    ShapeSearchTree searchTree = board.searchTreeManager.getDefaultTree();
    if (searchTree.isClearanceCompensationUsed()) {
      traceHalfWidth += searchTree.clearanceCompensationValue(clType, layer);
    }
    TileShape[] traceShapes = lineSegment.toPolyline().offsetShapes(traceHalfWidth);
    if (traceShapes.length != 1) {
      FRLogger.warn("ShoveTraceAlgo.check: traceShape count 1 expected");
      return 0;
    }

    TileShape traceShape = traceShapes[0];
    if (traceShape.isEmpty()) {
      FRLogger.warn("ShoveTraceAlgo.check: traceShape is empty");
      return 0;
    }
    if (!traceShape.isContainedIn(board.getBoundingBox())) {
      return 0;
    }
    CalcFromSide fromSide = new CalcFromSide(lineSegment, traceShape, shoveToTheLeft);
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(traceShape, layer, netNoArr, clType, fromSide, board);
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(traceShape, layer, new int[0], clType);
    boolean obstaclesShovable = shapeEntries.storeItems(obstacles, false, true);
    if (!obstaclesShovable || shapeEntries.traceTailsInShape()) {
      return 0;
    }
    int tracePieceCount = shapeEntries.substituteTraceCount();

    if (shapeEntries.stackDepth() > 1) {
      return 0;
    }

    FloatPoint startCornerAppprox = lineSegment.startPointApprox();
    FloatPoint endCornerAppprox = lineSegment.endPointApprox();
    double segmentLength = endCornerAppprox.distance(startCornerAppprox);

    ClearanceMatrix clMatrix = board.rules.clearanceMatrix;

    double result = Integer.MAX_VALUE;

    // check, if the obstacle vias can be shoved

    for (Via currShoveVia : shapeEntries.shoveViaList) {
      if (currShoveVia.sharesNetNo(netNoArr)) {
        continue;
      }
      boolean shoveViaOk = false;
      if (maxViaRecursionDepth > 0) {

        IntPoint[] newViaCenter =
            MoveDrillItemAlgo.tryShoveViaPoints(
                traceShape, layer, currShoveVia, clType, false, board);

        if (newViaCenter.length == 0) {
          return 0;
        }
        Vector delta = newViaCenter[0].differenceBy(currShoveVia.getCenter());
        Collection<Item> ignoreItems = new LinkedList<>();
        shoveViaOk =
            MoveDrillItemAlgo.check(
                currShoveVia,
                delta,
                maxRecursionDepth,
                maxViaRecursionDepth - 1,
                ignoreItems,
                board,
                null);
      }

      if (!shoveViaOk) {
        FloatPoint viaCenterAppprox = currShoveVia.getCenter().toFloat();
        double projection = startCornerAppprox.scalarProduct(endCornerAppprox, viaCenterAppprox);
        projection /= segmentLength;
        IntBox viaBox = currShoveVia.getTreeShapeOnLayer(searchTree, layer).boundingBox();
        double viaRadius = 0.5 * viaBox.maxWidth();
        double currOkLength = projection - viaRadius - traceHalfWidth;
        if (!searchTree.isClearanceCompensationUsed()) {
          currOkLength -= clMatrix.getValue(clType, currShoveVia.clearanceClassNo(), layer, true);
        }
        if (currOkLength <= 0) {
          return 0;
        }
        result = Math.min(result, currOkLength);
      }
    }
    if (tracePieceCount == 0) {
      return result;
    }
    if (maxRecursionDepth <= 0) {
      return 0;
    }

    Direction lineDirection = lineSegment.getLine().direction();
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.nextSubstituteTracePiece();
      if (currSubstituteTrace == null) {
        break;
      }
      for (int i = 0; i < currSubstituteTrace.tileShapeCount(); i++) {
        LineSegment currLineSegment = new LineSegment(currSubstituteTrace.polyline(), i + 1);
        if (shoveToTheLeft) {
          // swap the line segment to get the correct shove length
          // in case it is smaller than the length of the whole line segment.
          currLineSegment = currLineSegment.opposite();
        }

        boolean isInFront = currLineSegment.getLine().direction().equals(lineDirection);
        if (isInFront) {
          double shoveOkLength =
              check(
                  board,
                  currLineSegment,
                  shoveToTheLeft,
                  layer,
                  currSubstituteTrace.netNoArr,
                  currSubstituteTrace.getHalfWidth(),
                  currSubstituteTrace.clearanceClassNo(),
                  maxRecursionDepth - 1,
                  maxViaRecursionDepth);
          if (shoveOkLength < Integer.MAX_VALUE) {
            if (shoveOkLength <= 0) {
              return 0;
            }
            double projection =
                Math.min(
                    startCornerAppprox.scalarProduct(
                        endCornerAppprox, currLineSegment.startPointApprox()),
                    startCornerAppprox.scalarProduct(
                        endCornerAppprox, currLineSegment.endPointApprox()));
            projection /= segmentLength;
            double currOkLength =
                shoveOkLength + projection - traceHalfWidth - currSubstituteTrace.getHalfWidth();
            if (searchTree.isClearanceCompensationUsed()) {
              currOkLength -=
                  searchTree.clearanceCompensationValue(
                      currSubstituteTrace.clearanceClassNo(), layer);
            } else {
              currOkLength -=
                  clMatrix.getValue(clType, currSubstituteTrace.clearanceClassNo(), layer, true);
            }
            if (currOkLength <= 0) {
              return 0;
            }
            result = Math.min(currOkLength, result);
          }
          break;
        }
      }
    }
    return result;
  }

  /**
   * Checks if a shove with the input parameters is possible without clearance violations p_dir is
   * used internally to prevent the check from bouncing back. Returns false, if the shove failed.
   */
  public boolean check(
      TileShape traceShape,
      CalcFromSide fromSide,
      Direction dir,
      int layer,
      int[] netNoArr,
      int clType,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      int maxSpringOverRecursionDepth,
      TimeLimit timeLimit) {
    if (timeLimit != null && timeLimit.limitExceeded()) {
      return false;
    }

    if (traceShape.isEmpty()) {
      FRLogger.warn("ShoveTraceAux.check: p_trace_shape is empty");
      return true;
    }
    if (!traceShape.isContainedIn(board.getBoundingBox())) {
      this.board.setShoveFailingObstacle(board.getOutline());
      return false;
    }
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(traceShape, layer, netNoArr, clType, fromSide, board);
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(traceShape, layer, new int[0], clType);
    obstacles.removeAll(getIgnoreItemsAtTiePins(traceShape, layer, netNoArr));
    boolean obstaclesShovable = shapeEntries.storeItems(obstacles, false, true);
    if (!obstaclesShovable) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }
    int tracePieceCount = shapeEntries.substituteTraceCount();

    if (netNoArr != null && netNoArr.length > 0 && !obstacles.isEmpty()) {
      StringBuilder obstacleLog = new StringBuilder();
      obstacleLog
          .append(tracePieceCount > 0 ? "[shove_check_obstacles]" : "[shove_check_obstacles_zero]")
          .append(" net=")
          .append(netNoArr[0])
          .append(", shape_bb=")
          .append(traceShape.boundingBox())
          .append(", tracePieceCount=")
          .append(tracePieceCount)
          .append(", obstacles=[");
      boolean first = true;
      for (Item obs : obstacles) {
        if (!first) {
          obstacleLog.append(", ");
        }
        first = false;
        obstacleLog
            .append("{id=")
            .append(obs.getIdNo())
            .append(",type=")
            .append(obs.getClass().getSimpleName());
        if (obs instanceof PolylineTrace pt) {
          obstacleLog
              .append(",nets=")
              .append(java.util.Arrays.toString(pt.netNoArr))
              .append(",fc=")
              .append(pt.firstCorner())
              .append(",lc=")
              .append(pt.lastCorner());
        }
        obstacleLog.append("}");
      }
      obstacleLog.append("]");
      FRLogger.trace(obstacleLog.toString());
    }

    if (shapeEntries.stackDepth() > 1) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }
    double shapeRadius = 0.5 * traceShape.boundingBox().minWidth();

    // check, if the obstacle vias can be shoved

    for (Via currShoveVia : shapeEntries.shoveViaList) {
      if (currShoveVia.sharesNetNo(netNoArr)) {
        continue;
      }
      if (maxViaRecursionDepth <= 0) {
        this.board.setShoveFailingObstacle(currShoveVia);
        return false;
      }
      FloatPoint currShoveViaCenter = currShoveVia.getCenter().toFloat();
      IntPoint[] tryViaCenters =
          MoveDrillItemAlgo.tryShoveViaPoints(traceShape, layer, currShoveVia, clType, true, board);

      double maxDist =
          0.5 * currShoveVia.getShapeOnLayer(layer).boundingBox().maxWidth() + shapeRadius;
      double maxDistSquare = maxDist * maxDist;
      boolean shoveViaOk = false;
      for (int i = 0; i < tryViaCenters.length; i++) {
        if (i == 0
            || currShoveViaCenter.distanceSquare(tryViaCenters[i].toFloat()) <= maxDistSquare) {
          Vector delta = tryViaCenters[i].differenceBy(currShoveVia.getCenter());
          Collection<Item> ignoreItems = new LinkedList<>();
          if (MoveDrillItemAlgo.check(
              currShoveVia,
              delta,
              maxRecursionDepth,
              maxViaRecursionDepth - 1,
              ignoreItems,
              this.board,
              timeLimit)) {
            shoveViaOk = true;
            break;
          }
        }
      }
      if (!shoveViaOk) {
        return false;
      }
    }

    if (tracePieceCount == 0) {
      return true;
    }
    if (maxRecursionDepth <= 0) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }

    boolean isOrthogonalMode = traceShape instanceof IntBox;
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.nextSubstituteTracePiece();
      if (currSubstituteTrace == null) {
        break;
      }
      if (maxSpringOverRecursionDepth > 0) {
        Polyline newPolyline =
            springOver(
                currSubstituteTrace.polyline(),
                currSubstituteTrace.getCompensatedHalfWidth(searchTree),
                layer,
                currSubstituteTrace.netNoArr,
                currSubstituteTrace.clearanceClassNo(),
                false,
                maxSpringOverRecursionDepth,
                null);
        if (newPolyline == null) {
          // spring_over did not work
          return false;
        }
        if (newPolyline != currSubstituteTrace.polyline()) {
          // spring_over changed something
          --maxSpringOverRecursionDepth;
          currSubstituteTrace.change(newPolyline);
        }
      }
      for (int i = 0; i < currSubstituteTrace.tileShapeCount(); i++) {
        Direction currDir = currSubstituteTrace.polyline().arr[i + 1].direction();
        boolean isInFront = dir == null || dir.equals(currDir);
        if (isInFront) {
          CalcShapeAndFromSide curr =
              new CalcShapeAndFromSide(currSubstituteTrace, i, isOrthogonalMode, true);
          if (!this.check(
              curr.shape,
              curr.fromSide,
              currDir,
              layer,
              currSubstituteTrace.netNoArr,
              currSubstituteTrace.clearanceClassNo(),
              maxRecursionDepth - 1,
              maxViaRecursionDepth,
              maxSpringOverRecursionDepth,
              timeLimit)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * Puts in a trace segment with the input parameters and shoves obstacles out of the way. If the
   * shove does not work, the database may be damaged. To prevent this, call check first.
   */
  public boolean insert(
      TileShape traceShape,
      CalcFromSide fromSide,
      int layer,
      int[] netNoArr,
      int clType,
      Collection<Item> ignoreItems,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      int maxSpringOverRecursionDepth) {
    if (traceShape.isEmpty()) {
      FRLogger.warn("ShoveTraceAux.insert: p_trace_shape is empty");
      return true;
    }
    if (!traceShape.isContainedIn(board.getBoundingBox())) {
      this.board.setShoveFailingObstacle(board.getOutline());
      return false;
    }
    if (!MoveDrillItemAlgo.shoveVias(
        traceShape,
        fromSide,
        layer,
        netNoArr,
        clType,
        ignoreItems,
        maxRecursionDepth,
        maxViaRecursionDepth,
        true,
        this.board)) {
      return false;
    }
    ShapeTraceEntries shapeEntries =
        new ShapeTraceEntries(traceShape, layer, netNoArr, clType, fromSide, board);
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    Collection<Item> obstacles =
        searchTree.overlappingItemsWithClearance(traceShape, layer, new int[0], clType);
    obstacles.removeAll(getIgnoreItemsAtTiePins(traceShape, layer, netNoArr));
    boolean obstaclesShovable = shapeEntries.storeItems(obstacles, false, true);
    if (!shapeEntries.shoveViaList.isEmpty()) {
      obstaclesShovable = false;
      this.board.setShoveFailingObstacle(shapeEntries.shoveViaList.iterator().next());
      return false;
    }
    if (!obstaclesShovable) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }
    int tracePieceCount = shapeEntries.substituteTraceCount();
    if (netNoArr != null && netNoArr.length > 0 && !obstacles.isEmpty()) {
      StringBuilder obstacleLog = new StringBuilder();
      obstacleLog
          .append(
              tracePieceCount > 0 ? "[shove_insert_obstacles]" : "[shove_insert_obstacles_zero]")
          .append(" net=")
          .append(netNoArr[0])
          .append(", shape_bb=")
          .append(traceShape.boundingBox())
          .append(", tracePieceCount=")
          .append(tracePieceCount)
          .append(", obstacles=[");
      boolean first = true;
      for (Item obs : obstacles) {
        if (!first) {
          obstacleLog.append(", ");
        }
        first = false;
        obstacleLog
            .append("{id=")
            .append(obs.getIdNo())
            .append(",type=")
            .append(obs.getClass().getSimpleName());
        if (obs instanceof PolylineTrace pt) {
          obstacleLog
              .append(",nets=")
              .append(java.util.Arrays.toString(pt.netNoArr))
              .append(",fc=")
              .append(pt.firstCorner())
              .append(",lc=")
              .append(pt.lastCorner());
        }
        obstacleLog.append("}");
      }
      obstacleLog.append("]");
      FRLogger.trace(obstacleLog.toString());
    }
    if (tracePieceCount == 0) {
      return true;
    }
    if (maxRecursionDepth <= 0) {
      this.board.setShoveFailingObstacle(shapeEntries.getFoundObstacle());
      return false;
    }
    boolean tailsExistBefore = board.containsTraceTails(obstacles, netNoArr);
    shapeEntries.cutoutTraces(obstacles);
    boolean isOrthogonalMode = traceShape instanceof IntBox;
    for (; ; ) {
      PolylineTrace currSubstituteTrace = shapeEntries.nextSubstituteTracePiece();
      if (currSubstituteTrace == null) {
        break;
      }
      if (currSubstituteTrace.firstCorner().equals(currSubstituteTrace.lastCorner())) {
        continue;
      }
      if (maxSpringOverRecursionDepth > 0) {
        Polyline newPolyline =
            springOver(
                currSubstituteTrace.polyline(),
                currSubstituteTrace.getCompensatedHalfWidth(searchTree),
                layer,
                currSubstituteTrace.netNoArr,
                currSubstituteTrace.clearanceClassNo(),
                false,
                maxSpringOverRecursionDepth,
                null);

        if (newPolyline == null) {
          // spring_over did not work
          return false;
        }
        if (newPolyline != currSubstituteTrace.polyline()) {
          // spring_over changed something
          --maxSpringOverRecursionDepth;
          currSubstituteTrace.change(newPolyline);
        }
      }
      int[] currNetNoArr = currSubstituteTrace.netNoArr;
      for (int i = 0; i < currSubstituteTrace.tileShapeCount(); i++) {
        CalcShapeAndFromSide curr =
            new CalcShapeAndFromSide(currSubstituteTrace, i, isOrthogonalMode, false);
        if (!this.insert(
            curr.shape,
            curr.fromSide,
            layer,
            currNetNoArr,
            currSubstituteTrace.clearanceClassNo(),
            ignoreItems,
            maxRecursionDepth - 1,
            maxViaRecursionDepth,
            maxSpringOverRecursionDepth)) {
          return false;
        }
      }
      for (int i = 0; i < currSubstituteTrace.cornerCount(); i++) {
        board.joinChangedArea(currSubstituteTrace.polyline().cornerApprox(i), layer);
      }
      Point[] endCorners = null;
      if (!tailsExistBefore) {
        endCorners = new Point[2];
        endCorners[0] = currSubstituteTrace.firstCorner();
        endCorners[1] = currSubstituteTrace.lastCorner();
      }
      board.insertItem(currSubstituteTrace);

      try {
        currSubstituteTrace.normalize(board.changedArea.getArea(layer));
      } catch (Exception e) {
        FRLogger.error("Couldn't normalize trace.", e);
      }

      if (!tailsExistBefore) {
        for (int i = 0; i < 2; i++) {
          Trace tail = board.getTraceTail(endCorners[i], layer, currNetNoArr);
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

  Collection<Item> getIgnoreItemsAtTiePins(TileShape traceShape, int layer, int[] netNoArr) {
    Collection<SearchTreeObject> overlaps = this.board.overlappingObjects(traceShape, layer);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currObject : overlaps) {
      if (currObject instanceof Pin currPin) {
        if (currPin.sharesNetNo(netNoArr)) {
          result.addAll(currPin.getAllContacts(layer));
        }
      }
    }
    return result;
  }

  /**
   * Checks, if there are obstacle in the way of p_polyline and tries to wrap the polyline trace
   * around these obstacles in counterclock sense. Returns null, if that is not possible. Returns
   * p_polyline, if there were no obstacles If p_contact_pins != null, all pins not contained in
   * p_contact_pins are regarded as obstacles, even if they are of the own net.
   */
  private Polyline springOver(
      Polyline polyline,
      int halfWidth,
      int layer,
      int[] netNoArr,
      int clType,
      boolean overConnectedPins,
      int recursionDepth,
      Set<Pin> contactPins) {
    Item foundObstacle = null;
    IntBox foundObstacleBoundingBox = null;
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    int[] checkNetNoArr;
    if (contactPins == null) {
      checkNetNoArr = netNoArr;
    } else {
      checkNetNoArr = new int[0];
    }
    for (int i = 0; i < polyline.arr.length - 2; i++) {
      TileShape currShape = polyline.offsetShape(halfWidth, i);
      Collection<Item> obstacles =
          searchTree.overlappingItemsWithClearance(currShape, layer, checkNetNoArr, clType);
      for (Item currItem : obstacles) {
        boolean isObstacle;
        if (currItem.sharesNetNo(netNoArr)) {
          // to avoid acid traps
          isObstacle =
              currItem instanceof Pin && contactPins != null && !contactPins.contains(currItem);
        } else if (currItem instanceof ConductionArea area) {
          isObstacle = area.getIsObstacle();
        } else if (currItem instanceof ViaObstacleArea
            || currItem instanceof ComponentObstacleArea) {
          isObstacle = false;
        } else if (currItem instanceof PolylineTrace) {
          if (currItem.isShoveFixed()) {
            isObstacle = true;
            // check for a shove fixed trace exit stub, which has to be ignored at a tie pin.
            Collection<Item> currContacts = currItem.getNormalContacts();
            for (Item currContact : currContacts) {
              if (currContact.sharesNetNo(netNoArr)) {
                isObstacle = false;
              }
            }
          } else {
            // an unfixed trace can be pushed aside eventually
            isObstacle = false;
          }
        } else {
          // an unfixed via can be pushed aside eventually
          isObstacle = !currItem.isRoutable();
        }

        if (isObstacle) {
          if (foundObstacle == null) {
            foundObstacle = currItem;
            foundObstacleBoundingBox = currItem.boundingBox();
          } else if (foundObstacle != currItem) {
            // check, if 1 obstacle is contained in the other obstacle and take
            // the bigger obstacle in this case.
            // That may happen in case of fixed vias inside of pins.
            IntBox currItemBoundingBox = currItem.boundingBox();
            if (foundObstacleBoundingBox.intersects(currItemBoundingBox)) {
              if (currItemBoundingBox.contains(foundObstacleBoundingBox)) {
                foundObstacle = currItem;
                foundObstacleBoundingBox = currItemBoundingBox;
              } else if (!foundObstacleBoundingBox.contains(currItemBoundingBox)) {
                return null;
              }
            }
          }
        }
      }
      if (foundObstacle != null) {
        break;
      }
    }
    if (foundObstacle == null) {
      // no obstacle in the way, nothing to do
      return polyline;
    }

    if (recursionDepth <= 0
        || foundObstacle instanceof BoardOutline
        || (foundObstacle instanceof Trace && !foundObstacle.isShoveFixed())) {
      this.board.setShoveFailingObstacle(foundObstacle);
      return null;
    }
    boolean trySpringOver = true;
    if (!overConnectedPins) {
      // Check if the obstacle has a trace contact on p_layer
      Collection<Item> contactsOnLayer = foundObstacle.getAllContacts(layer);
      for (Item currContact : contactsOnLayer) {
        if (currContact instanceof Trace) {
          trySpringOver = false;
          break;
        }
      }
    }
    ConvexShape obstacleShape = null;
    if (trySpringOver) {
      if (foundObstacle instanceof ObstacleArea || foundObstacle instanceof Trace) {
        if (foundObstacle.treeShapeCount(searchTree) == 1) {
          obstacleShape = foundObstacle.getTreeShape(searchTree, 0);
        } else {
          trySpringOver = false;
        }
      } else if (foundObstacle instanceof DrillItem foundDrillItem) {
        obstacleShape = foundDrillItem.getTreeShapeOnLayer(searchTree, layer);
      }
    }
    if (!trySpringOver) {
      this.board.setShoveFailingObstacle(foundObstacle);
      return null;
    }
    TileShape offsetShape;
    if (searchTree.isClearanceCompensationUsed()) {
      int offset = halfWidth + 1;
      offsetShape = (TileShape) obstacleShape.enlarge(offset);
    } else {
      // enlarge the shape in 2 steps  for symmetry reasons
      int offset = halfWidth + 1;
      double halfClOffset =
          0.5 * board.clearanceValue(foundObstacle.clearanceClassNo(), clType, layer);
      offsetShape = (TileShape) obstacleShape.enlarge(offset + halfClOffset);
      offsetShape = (TileShape) offsetShape.enlarge(halfClOffset);
    }
    if (this.board.rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      offsetShape = offsetShape.boundingBox();
    } else if (this.board.rules.getTraceAngleRestriction() == AngleRestriction.FORTYFIVE_DEGREE) {
      offsetShape = offsetShape.boundingOctagon();
    }

    if (offsetShape.containsInside(polyline.firstCorner())
        || offsetShape.containsInside(polyline.lastCorner())) {
      // can happen with clearance compensation off because of asymmetry in calculations with the
      // offset shapes
      this.board.setShoveFailingObstacle(foundObstacle);
      return null;
    }
    int[][] entries = offsetShape.entrancePoints(polyline);
    if (entries.length == 0) {
      return polyline; // no obstacle
    }

    if (entries.length < 2) {
      this.board.setShoveFailingObstacle(foundObstacle);
      return null;
    }
    int firstIntersectionSideNo = entries[0][1];
    int lastIntersectionSideNo = entries[entries.length - 1][1];
    int firstIntersectionLineNo = entries[0][0];
    int lastIntersectionLineNo = entries[entries.length - 1][0];
    int sideDiff = lastIntersectionSideNo - firstIntersectionSideNo;
    if (sideDiff < 0) {
      sideDiff += offsetShape.borderLineCount();
    } else if (sideDiff == 0) {
      FloatPoint compareCorner = offsetShape.cornerApprox(firstIntersectionSideNo);
      FloatPoint firstIntersection =
          polyline.arr[firstIntersectionLineNo].intersectionApprox(
              offsetShape.borderLine(firstIntersectionSideNo));
      FloatPoint secondIntersection =
          polyline.arr[lastIntersectionLineNo].intersectionApprox(
              offsetShape.borderLine(lastIntersectionSideNo));
      if (compareCorner.distance(secondIntersection) < compareCorner.distance(firstIntersection)) {
        sideDiff += offsetShape.borderLineCount();
      }
    }
    Line[] substituteLines = new Line[sideDiff + 3];
    substituteLines[0] = polyline.arr[firstIntersectionLineNo];
    int currEdgeLineNo = firstIntersectionSideNo;

    for (int i = 1; i <= sideDiff + 1; i++) {
      substituteLines[i] = offsetShape.borderLine(currEdgeLineNo);
      if (currEdgeLineNo == offsetShape.borderLineCount() - 1) {
        currEdgeLineNo = 0;
      } else {
        ++currEdgeLineNo;
      }
    }
    substituteLines[sideDiff + 2] = polyline.arr[lastIntersectionLineNo];
    Polyline substitutePolyline = new Polyline(substituteLines);
    Polyline[] pieces =
        offsetShape.cutout(
            polyline); // build a circuit around the offsetShape in counter clock sense
    // from the first intersection point to the second intersection point
    Polyline result = substitutePolyline;

    if (pieces.length > 0) {
      result = pieces[0].combine(substitutePolyline);
    }
    if (pieces.length > 1) {
      result = result.combine(pieces[1]);
    }
    return springOver(
        result,
        halfWidth,
        layer,
        netNoArr,
        clType,
        overConnectedPins,
        recursionDepth - 1,
        contactPins);
  }

  /**
   * Checks, if there are obstacle in the way of p_polyline and tries to wrap the polyline trace
   * around these obstacles. Returns null, if that is not possible. Returns p_polyline, if there
   * were no obstacles This function looks contrary to the previous function for the shortest way
   * around the obstacles. If p_contact_pins != null, all pins not contained in p_contact_pins are
   * regarded as obstacles, even if they are of the own net.
   */
  Polyline springOverObstacles(
      Polyline polyline,
      int halfWidth,
      int layer,
      int[] netNoArr,
      int clType,
      Set<Pin> contactPins) {
    final int maxSpringOverRecursionDepth = 20;
    Polyline counterClockWiseResult =
        springOver(
            polyline,
            halfWidth,
            layer,
            netNoArr,
            clType,
            true,
            maxSpringOverRecursionDepth,
            contactPins);
    if (counterClockWiseResult == polyline) {
      return polyline; // no obstacle
    }

    Polyline clockWiseResult =
        springOver(
            polyline.reverse(),
            halfWidth,
            layer,
            netNoArr,
            clType,
            true,
            maxSpringOverRecursionDepth,
            contactPins);
    Polyline result = null;
    if (clockWiseResult != null && counterClockWiseResult != null) {
      if (clockWiseResult.lengthApprox() <= counterClockWiseResult.lengthApprox()) {
        result = clockWiseResult.reverse();
      } else {
        result = counterClockWiseResult;
      }

    } else if (clockWiseResult != null) {
      result = clockWiseResult.reverse();
    } else if (counterClockWiseResult != null) {
      result = counterClockWiseResult;
    }

    return result;
  }
}

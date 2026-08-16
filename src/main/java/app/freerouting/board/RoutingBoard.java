package app.freerouting.board;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.AutorouteControl;
import app.freerouting.autoroute.AutorouteControl.ExpansionCostFactor;
import app.freerouting.autoroute.AutorouteEngine;
import app.freerouting.autoroute.CompleteFreeSpaceExpansionRoom;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.ShapeTree.TreeEntry;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.LineSegment;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.Net;
import app.freerouting.rules.ViaInfo;
import app.freerouting.settings.RouterSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The class that represents the board that is being routed. It contains all the data for the board
 * and the methods to manipulate it.
 */
public class RoutingBoard extends BasicBoard implements Serializable {

  /** The time limit in milliseconds for the pull tight algorithm. */
  private static final int PULL_TIGHT_TIME_LIMIT = 2000;

  public final app.freerouting.autoroute.RoutingFailureLog failureLog;

  /** The area marked for optimizing the route. */
  transient ChangedArea changedArea;

  /** Contains the database for the auto-route algorithm. */
  private transient AutorouteEngine autorouteEngine;

  private transient Item shoveFailingObstacle;
  private transient int shoveFailingLayer = -1;

  /**
   * Creates a new instance of a routing Board with surrounding box boundingBox Rules contains the
   * restrictions to obey when inserting items. Among other things it may contain a clearance
   * matrix.
   */
  public RoutingBoard(
      IntBox boundingBox,
      LayerStructure layerStructure,
      PolylineShape[] outlineShapes,
      int outlineClClassNo,
      BoardRules rules,
      Communication boardCommunication) {
    super(boundingBox, layerStructure, outlineShapes, outlineClClassNo, rules, boardCommunication);
    this.failureLog = new app.freerouting.autoroute.RoutingFailureLog();
  }

  /** Maintains the auto-router database after item is inserted, changed, or deleted. */
  @Override
  public void additionalUpdateAfterChange(Item item) {
    if (item == null) {
      return;
    }
    if (this.autorouteEngine == null || !this.autorouteEngine.maintainDatabase) {
      return;
    }
    // Invalidate the free space expansion rooms touching a shape of item.
    int shapeCount = item.treeShapeCount(this.autorouteEngine.autorouteSearchTree);
    for (int i = 0; i < shapeCount; i++) {
      TileShape currentShape = item.getTreeShape(this.autorouteEngine.autorouteSearchTree, i);
      this.autorouteEngine.invalidateDrillPages(currentShape);
      int currentLayer = item.shapeLayer(i);
      Collection<SearchTreeObject> overlaps =
          this.autorouteEngine.autorouteSearchTree.overlappingObjects(currentShape, currentLayer);
      for (SearchTreeObject currentObject : overlaps) {
        if (currentObject instanceof CompleteFreeSpaceExpansionRoom room) {
          this.autorouteEngine.removeCompleteExpansionRoom(room);
        }
      }
    }
    item.clearAutorouteInfo();
  }

  /**
   * Removes the items in itemList and pulls the nearby rubber traces tight. Returns false, if some
   * items could not be removed, because they were fixed.
   */
  public boolean removeItemsAndPullTight(
      Collection<Item> itemList, int tidyWidth, int pullTightAccuracy) {
    boolean result = true;
    IntOctagon tidyRegion;
    boolean calculateTidyRegion;
    if (tidyWidth < Integer.MAX_VALUE) {
      tidyRegion = IntOctagon.EMPTY;
      calculateTidyRegion = tidyWidth > 0;
    } else {
      tidyRegion = null;
      calculateTidyRegion = false;
    }
    startMarkingChangedArea();
    Set<Integer> changedNets = new TreeSet<>();
    for (Item currentItem : itemList) {
      if (currentItem.isDeletionForbidden() || currentItem.isUserFixed()) {
        // We are not allowed to delete this item.
        result = false;
      } else {
        for (int i = 0; i < currentItem.tileShapeCount(); i++) {
          TileShape currentShape = currentItem.getTileShape(i);
          changedArea.join(currentShape, currentItem.shapeLayer(i));
          if (calculateTidyRegion) {
            tidyRegion = tidyRegion.union(currentShape.boundingOctagon());
          }
        }
        removeItem(currentItem);
        for (int i = 0; i < currentItem.netCount(); i++) {
          changedNets.add(currentItem.getNetNumber(i));
        }
      }
    }
    for (Integer currentNetNumber : changedNets) {
      this.combineTraces(currentNetNumber);
    }
    if (calculateTidyRegion) {
      tidyRegion = tidyRegion.enlarge(tidyWidth);
    }
    optChangedArea(new int[0], tidyRegion, pullTightAccuracy, null, null, PULL_TIGHT_TIME_LIMIT);
    return result;
  }

  /** Starts marking the changed areas for optimizing traces. */
  public void startMarkingChangedArea() {
    if (changedArea == null) {
      changedArea = new ChangedArea(getLayerCount());
    }
  }

  /** Enlarges the changed area on layer, so that it contains point. */
  public void joinChangedArea(FloatPoint point, int layer) {
    if (changedArea != null) {
      changedArea.join(point, layer);
    }
  }

  /** Marks the whole board as changed. */
  public void markAllChangedArea() {
    startMarkingChangedArea();
    FloatPoint[] boardCorners = new FloatPoint[4];
    boardCorners[0] = boundingBox.ll.toFloat();
    boardCorners[1] = new FloatPoint(boundingBox.ur.x, boundingBox.ll.y);
    boardCorners[2] = boundingBox.ur.toFloat();
    boardCorners[3] = new FloatPoint(boundingBox.ll.x, boundingBox.ur.y);
    for (int i = 0; i < getLayerCount(); i++) {
      for (int j = 0; j < 4; j++) {
        joinChangedArea(boardCorners[j], i);
      }
    }
  }

  /**
   * Optimizes the route in the internally marked area. If netNumber {@literal >} 0, only traces
   * with net number netNumber are optimized. If clipShape != null the optimizing is restricted to
   * clipShape. traceCosts is used for optimizing vias and may be null. If stoppableThread != null,
   * the algorithm can be requested to be stopped. If timeLimit {@literal >} 0; the algorithm will
   * be stopped after timeLimit Milliseconds.
   */
  public void optChangedArea(
      int[] onlyNetNoArr,
      IntOctagon clipShape,
      int accuracy,
      ExpansionCostFactor[] traceCosts,
      Stoppable stoppableThread,
      int timeLimit) {
    optChangedArea(
        onlyNetNoArr, clipShape, accuracy, traceCosts, stoppableThread, timeLimit, null, 0);
  }

  /**
   * Optimizes the route in the internally marked area. If netNumber {@literal >} 0, only traces
   * with net number netNumber are optimized. If clipShape != null the optimizing is restricted to
   * clipShape. traceCosts is used for optimizing vias and may be null. If stoppableThread != null,
   * the algorithm can be requested to be stopped. If timeLimit {@literal >} 0; the algorithm will
   * be stopped after timeLimit Milliseconds. If keepPoint != null, traces on layer keepPointLayer
   * containing keepPoint will also contain this point after optimizing.
   */
  public void optChangedArea(
      int[] onlyNetNoArr,
      IntOctagon clipShape,
      int accuracy,
      ExpansionCostFactor[] traceCosts,
      Stoppable stoppableThread,
      int timeLimit,
      Point keepPoint,
      int keepPointLayer) {
    if (changedArea == null) {
      return;
    }
    if (clipShape != IntOctagon.EMPTY) {
      TraceTightener pullTightAlgo =
          TraceTightener.getInstance(
              this,
              onlyNetNoArr,
              clipShape,
              accuracy,
              stoppableThread,
              timeLimit,
              keepPoint,
              keepPointLayer);
      pullTightAlgo.optChangedArea(traceCosts);
    }
    joinGraphicsUpdateBox(changedArea.surroundingBox());
    changedArea = null;
  }

  /**
   * Checks if a rectangular boxed trace line segment with the input parameters can be inserted
   * without conflict. If a conflict exists, The result length is the maximal line length from
   * line.a to line.b, which can be inserted without conflict (Integer.MAX_VALUE, if no conflict
   * exists). If onlyNotShovableObstacles, unfixed traces and vias are ignored.
   */
  public double checkTraceSegment(
      Point fromPoint,
      Point toPoint,
      int layer,
      int[] netNumbers,
      int traceHalfWidth,
      int clClassNo,
      boolean onlyNotShovableObstacles) {
    if (fromPoint.equals(toPoint)) {
      return 0;
    }
    Polyline currentPolyline = new Polyline(fromPoint, toPoint);
    LineSegment currentLineSegment = new LineSegment(currentPolyline, 1);
    return checkTraceSegment(
        currentLineSegment, layer, netNumbers, traceHalfWidth, clClassNo, onlyNotShovableObstacles);
  }

  /**
   * Checks if a trace shape around the input parameters can be inserted without conflict. If a
   * conflict exists, The result length is the maximal line length from line.a to line.b, which can
   * be inserted without conflict (Integer.MAX_VALUE, if no conflict exists). If
   * onlyNotShovableObstacles, unfixed traces and vias are ignored.
   */
  public double checkTraceSegment(
      LineSegment lineSegment,
      int layer,
      int[] netNumbers,
      int traceHalfWidth,
      int clClassNo,
      boolean onlyNotShovableObstacles) {
    Polyline checkPolyline = lineSegment.toPolyline();
    if (checkPolyline.lines.length != 3) {
      return 0;
    }
    TileShape shapeToCheck = checkPolyline.offsetShape(traceHalfWidth, 0);
    FloatPoint fromPoint = lineSegment.startPointApprox();
    FloatPoint toPoint = lineSegment.endPointApprox();
    double lineLength = toPoint.distance(fromPoint);
    double okLength = Integer.MAX_VALUE;
    ShapeSearchTree defaultTree = this.searchTreeManager.getDefaultTree();

    Collection<TreeEntry> obstacleEntries =
        defaultTree.overlappingTreeEntriesWithClearance(shapeToCheck, layer, netNumbers, clClassNo);

    for (TreeEntry currentObstacleEntry : obstacleEntries) {

      if (!(currentObstacleEntry.object instanceof Item currentObstacle)) {
        continue;
      }
      if (onlyNotShovableObstacles
          && currentObstacle.isRoutable()
          && !currentObstacle.isShoveFixed()) {
        continue;
      }
      TileShape currentObstacleShape =
          currentObstacleEntry.object.getTreeShape(
              defaultTree, currentObstacleEntry.shapeIndexInObject);
      TileShape currentOffsetShape;
      FloatPoint nearestObstaclePoint;
      double shortenValue;
      if (defaultTree.isClearanceCompensationUsed()) {
        currentOffsetShape = shapeToCheck;
        shortenValue =
            traceHalfWidth
                + rules.clearanceMatrix.clearanceCompensationValue(
                    currentObstacle.clearanceClassIndex(), layer);
      } else {
        int clearanceValue =
            this.clearanceValue(currentObstacle.clearanceClassIndex(), clClassNo, layer);
        currentOffsetShape = (TileShape) shapeToCheck.offset(clearanceValue);
        shortenValue = traceHalfWidth + clearanceValue;
      }
      TileShape intersection = currentObstacleShape.intersection(currentOffsetShape);
      if (intersection.isEmpty()) {
        continue;
      }
      nearestObstaclePoint = intersection.nearestPointApprox(fromPoint);

      double projection = fromPoint.scalarProduct(toPoint, nearestObstaclePoint) / lineLength;

      projection = Math.max(0.0, projection - shortenValue - 1);

      if (projection < okLength) {
        okLength = projection;
        if (okLength <= 0) {
          return 0;
        }
      }
    }

    return okLength;
  }

  /**
   * Checks, if item can be translated by vector without producing overlaps or clearance violations.
   */
  public boolean checkMoveItem(Item item, Vector vector, Collection<Item> ignoreItems) {
    int netCount = item.netNumbers.length;
    if (netCount > 1) {
      return false; // not yet implemented
    }
    int contactCount = 0;
    // the connected items must remain connected after moving
    if (item instanceof Connectable) {
      contactCount = item.getAllContacts().size();
    }
    if (item instanceof Trace && contactCount > 0) {
      return false;
    }
    if (ignoreItems != null) {
      ignoreItems.add(item);
    }
    for (int i = 0; i < item.tileShapeCount(); i++) {
      TileShape movedShape = (TileShape) item.getTileShape(i).translateBy(vector);
      if (!movedShape.isContainedIn(boundingBox)) {
        return false;
      }
      Set<Item> obstacles =
          this.overlappingItemsWithClearance(
              movedShape, item.shapeLayer(i), item.netNumbers, item.clearanceClassIndex());
      for (Item currentItem : obstacles) {
        if (ignoreItems != null) {
          if (!ignoreItems.contains(currentItem)) {
            if (currentItem.isObstacle(item)) {
              return false;
            }
          }
        } else if (currentItem != item) {
          if (currentItem.isObstacle(item)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /** Checks, if the net number of item can be changed without producing clearance violations. */
  public boolean checkChangeNet(Item item, int newNetNo) {
    int[] netNumbers = new int[1];
    netNumbers[0] = newNetNo;
    for (int i = 0; i < item.tileShapeCount(); i++) {
      TileShape currentShape = item.getTileShape(i);
      Set<Item> obstacles =
          this.overlappingItemsWithClearance(
              currentShape, item.shapeLayer(i), netNumbers, item.clearanceClassIndex());
      for (SearchTreeObject currentObject : obstacles) {
        if (currentObject != item
            && currentObject instanceof Connectable connectable
            && !connectable.containsNet(newNetNo)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Translates drillItem by vector and shoves obstacle traces aside. Returns false, if that was not
   * possible without creating clearance violations. In this case the database may be damaged, so
   * that an undo becomes necessary.
   */
  public boolean moveDrillItem(
      DrillItem drillItem,
      Vector vector,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      int tidyWidth,
      int pullTightAccuracy,
      int pullTightTimeLimit) {
    clearShoveFailingObstacle();
    // unfix the connected shove fixed traces.
    Collection<Item> contactList = drillItem.getNormalContacts();
    for (Item currentContact : contactList) {
      if (currentContact.getFixedState() == FixedState.SHOVE_FIXED) {
        currentContact.setFixedState(FixedState.UNFIXED);
      }
    }

    IntOctagon tidyRegion;
    boolean calculateTidyRegion;
    if (tidyWidth < Integer.MAX_VALUE) {
      tidyRegion = IntOctagon.EMPTY;
      calculateTidyRegion = tidyWidth > 0;
    } else {
      tidyRegion = null;
      calculateTidyRegion = false;
    }
    startMarkingChangedArea();
    if (!DrillItemMover.insert(
        drillItem, vector, maxRecursionDepth, maxViaRecursionDepth, tidyRegion, this)) {
      return false;
    }
    if (calculateTidyRegion) {
      tidyRegion = tidyRegion.enlarge(tidyWidth);
    }
    int[] optNetNoArr;
    if (maxRecursionDepth <= 0) {
      int[] netNumbers = drillItem.netNumbers;
      optNetNoArr = netNumbers;
    } else {
      optNetNoArr = new int[0];
    }
    optChangedArea(optNetNoArr, tidyRegion, pullTightAccuracy, null, null, pullTightTimeLimit);
    return true;
  }

  /**
   * Checks, if there is an item nearby sharing a net with netNumbers, from where a routing can
   * start, or where the routing can connect to. If fromItem != null, items, which are connected to
   * fromItem, are ignored. Returns null, if no item is found, If layer {@literal <} 0, the layer is
   * ignored
   */
  public Item pickNearestRoutingItem(Point location, int layer, Item fromItem) {
    TileShape pointShape = TileShape.getInstance(location);
    Collection<Item> foundItems = overlappingItems(pointShape, layer);
    FloatPoint pickLocation = location.toFloat();
    double minDist = Integer.MAX_VALUE;
    Item nearestItem = null;
    Set<Item> ignoreSet = null;
    for (Item currentItem : foundItems) {
      if (!currentItem.isConnectable()) {
        continue;
      }
      boolean candidateFound = false;
      double currentDistance = 0;
      if (currentItem instanceof PolylineTrace currentTrace) {
        if (layer < 0 || currentTrace.getLayer() == layer) {
          if (nearestItem instanceof DrillItem) {
            continue; // prefer drill items
          }
          int traceRadius = currentTrace.getHalfWidth();
          currentDistance = currentTrace.polyline().distance(pickLocation);
          if (currentDistance < minDist && currentDistance <= traceRadius) {
            candidateFound = true;
          }
        }
      } else if (currentItem instanceof DrillItem currentDrillItem) {
        if (layer < 0 || currentDrillItem.isOnLayer(layer)) {
          FloatPoint drillItemCenter = currentDrillItem.getCenter().toFloat();
          currentDistance = drillItemCenter.distance(pickLocation);
          if (currentDistance < minDist || nearestItem instanceof Trace) {
            candidateFound = true;
          }
        }
      } else if (currentItem instanceof ConductionArea currentArea) {
        if ((layer < 0 || currentArea.getLayer() == layer) && nearestItem == null) {
          candidateFound = true;
          currentDistance = Integer.MAX_VALUE;
        }
      }
      if (candidateFound) {
        if (fromItem != null) {
          if (ignoreSet == null) {
            // calculated here to avoid unnecessary calculations for performance reasoss.
            ignoreSet = fromItem.getConnectedSet(-1);
          }
          if (ignoreSet.contains(currentItem)) {
            continue;
          }
        }
        minDist = currentDistance;
        nearestItem = currentItem;
      }
    }
    return nearestItem;
  }

  /**
   * Shoves aside traces, so that a via with the input parameters can be inserted without clearance
   * violations. If the shove failed, the database may be damaged, so that an undo becomes
   * necessary. Returns false, if the forced via failed.
   */
  public boolean forcedVia(
      ViaInfo viaInfo,
      Point location,
      int[] netNumbers,
      int traceClearanceClassIndex,
      int[] tracePenHalfwidthArr,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      int tidyWidth,
      int pullTightAccuracy,
      int pullTightTimeLimit) {
    clearShoveFailingObstacle();
    this.startMarkingChangedArea();
    boolean result =
        ForcedViaInserter.insert(
            viaInfo,
            location,
            netNumbers,
            traceClearanceClassIndex,
            tracePenHalfwidthArr,
            maxRecursionDepth,
            maxViaRecursionDepth,
            this);
    if (result) {
      IntOctagon tidyClipShape;
      if (tidyWidth < Integer.MAX_VALUE) {
        tidyClipShape = location.surroundingOctagon().enlarge(tidyWidth);
      } else {
        tidyClipShape = null;
      }
      int[] optNetNoArr;
      if (maxRecursionDepth <= 0) {
        optNetNoArr = netNumbers;
      } else {
        optNetNoArr = new int[0];
      }
      this.optChangedArea(
          optNetNoArr, tidyClipShape, pullTightAccuracy, null, null, pullTightTimeLimit);
    }
    return result;
  }

  /**
   * Tries to insert a trace line with the input parameters from fromCorner to toCorner while
   * shoving aside obstacle traces and vias. Returns the last point between fromCorner and toCorner,
   * to which the shove succeeded. Returns null, if the check was inaccurate and an error occurred
   * while inserting, so that the database may be damaged and an undo necessary. searchTree is the
   * shape search tree used in the algorithm.
   */
  public Point insertForcedTraceSegment(
      Point fromCorner,
      Point toCorner,
      int halfWidth,
      int layer,
      int[] netNumbers,
      int clearanceClassIndex,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      int maxSpringOverRecursionDepth,
      int tidyWidth,
      int pullTightAccuracy,
      boolean withCheck,
      TimeLimit timeLimit) {
    if (fromCorner.equals(toCorner)) {
      return toCorner;
    }
    Polyline insertPolyline = new Polyline(fromCorner, toCorner);
    Point okPoint =
        insertForcedTracePolyline(
            insertPolyline,
            halfWidth,
            layer,
            netNumbers,
            clearanceClassIndex,
            maxRecursionDepth,
            maxViaRecursionDepth,
            maxSpringOverRecursionDepth,
            tidyWidth,
            pullTightAccuracy,
            withCheck,
            timeLimit);
    Point result;
    if (okPoint == insertPolyline.firstCorner()) {
      result = fromCorner;
    } else if (okPoint == insertPolyline.lastCorner()) {
      result = toCorner;
    } else {
      result = okPoint;
    }
    return result;
  }

  /**
   * Checks, if a trace polyline with the input parameters can be inserted while shoving aside
   * obstacle traces and vias.
   */
  public boolean checkForcedTracePolyline(
      Polyline polyline,
      int halfWidth,
      int layer,
      int[] netNumbers,
      int clearanceClassIndex,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      int maxSpringOverRecursionDepth) {
    ShapeSearchTree searchTree = searchTreeManager.getDefaultTree();
    int compensatedHalfWidth =
        halfWidth + searchTree.clearanceCompensationValue(clearanceClassIndex, layer);
    TileShape[] traceShapes =
        polyline.offsetShapes(compensatedHalfWidth, 0, polyline.lines.length - 1);
    boolean orthogonalMode = rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE;
    TraceShover shoveTraceAlgo = new TraceShover(this);
    for (int i = 0; i < traceShapes.length; i++) {
      TileShape currentTraceShape = traceShapes[i];
      if (orthogonalMode) {
        currentTraceShape = currentTraceShape.boundingBox();
      }
      ShapeEntrySide fromSide = new ShapeEntrySide(polyline, i + 1, currentTraceShape);

      boolean checkShoveOk =
          shoveTraceAlgo.check(
              currentTraceShape,
              fromSide,
              null,
              layer,
              netNumbers,
              clearanceClassIndex,
              maxRecursionDepth,
              maxViaRecursionDepth,
              maxSpringOverRecursionDepth,
              null);
      if (!checkShoveOk) {
        return false;
      }
    }
    return true;
  }

  /**
   * Tries to insert a trace polyline with the input parameters from while shoving aside obstacle
   * traces and vias. Returns the last corner on the polyline, to which the shove succeeded. Returns
   * null, if the check was inaccurate and an error occurred while inserting, so that the database
   * may be damaged and an undo necessary.
   */
  public Point insertForcedTracePolyline(
      Polyline polyline,
      int halfWidth,
      int layer,
      int[] netNumbers,
      int clearanceClassIndex,
      int maxRecursionDepth,
      int maxViaRecursionDepth,
      int maxSpringOverRecursionDepth,
      int tidyWidth,
      int pullTightAccuracy,
      boolean withCheck,
      TimeLimit timeLimit) {
    clearShoveFailingObstacle();
    Point fromCorner = polyline.firstCorner();
    Point toCorner = polyline.lastCorner();
    if (fromCorner == null || toCorner == null) {
      // A degenerate polyline (parallel/near-parallel lines produced while shoving against
      // fixed multi-layer copper) has no well-defined first/last corner -- corner(i) returns
      // null. The trace cannot be inserted; return null so the caller treats this segment as
      // not inserted and reroutes, instead of dereferencing a null corner (NPE at .equals).
      FRLogger.trace(
          "RoutingBoard.insert_forced_trace_polyline: degenerate polyline (null corner), skipping");
      return null;
    }
    if (fromCorner.equals(toCorner)) {
      return toCorner;
    }
    if (!(fromCorner instanceof IntPoint && toCorner instanceof IntPoint)) {
      FRLogger.warn("RoutingBoard.insert_forced_trace_segment: only implemented for IntPoints");
      return fromCorner;
    }
    startMarkingChangedArea();
    // Check, if there ends an item of the same net at fromCorner.
    // If so, its geometry will be used to cut off dog ears of the check shape.
    Trace pickedTrace = null;
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Set<Item> pickedItems = this.pickItems(fromCorner, layer, filter);
    if (netNumbers != null && netNumbers.length > 0) {
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + netNumbers[0]
              + ", step=start, pickedSize="
              + pickedItems.size()
              + ", from="
              + fromCorner
              + ", to="
              + toCorner
              + ", idMax="
              + communication.idNoGenerator.maxGeneratedNo());
    }
    if (pickedItems.size() == 1) {
      Trace currentPickedTrace = (Trace) pickedItems.iterator().next();
      if (currentPickedTrace.netsEqual(netNumbers)
          && currentPickedTrace.getHalfWidth() == halfWidth
          && currentPickedTrace.clearanceClassIndex() == clearanceClassIndex
          && (currentPickedTrace instanceof PolylineTrace)) {
        // can combine with the picked trace
        pickedTrace = currentPickedTrace;
      }
    }
    ShapeSearchTree searchTree = searchTreeManager.getDefaultTree();
    int compensatedHalfWidth =
        halfWidth + searchTree.clearanceCompensationValue(clearanceClassIndex, layer);
    TraceShover shoveTraceAlgo = new TraceShover(this);
    Polyline newPolyline =
        shoveTraceAlgo.springOverObstacles(
            polyline, compensatedHalfWidth, layer, netNumbers, clearanceClassIndex, null);
    if (newPolyline == null) {
      if (netNumbers != null && netNumbers.length > 0 && netNumbers[0] == 94) {
        FRLogger.trace(
            "RoutingBoard.insert_forced_trace_polyline",
            "compare_trace_insert_forced_fail",
            "spring_over_obstacles returned null",
            "Net #" + netNumbers[0] + ",Layer #" + layer,
            new Point[] {fromCorner, toCorner});
      }
      return fromCorner;
    }
    Polyline combinedPolyline;
    if (pickedTrace == null) {
      combinedPolyline = newPolyline;
    } else {
      PolylineTrace combineTrace = (PolylineTrace) pickedTrace;
      combinedPolyline = newPolyline.combine(combineTrace.polyline());
    }
    if (combinedPolyline.lines.length < 3) {
      if (netNumbers != null && netNumbers.length > 0 && netNumbers[0] == 94) {
        FRLogger.trace(
            "RoutingBoard.insert_forced_trace_polyline",
            "compare_trace_insert_forced_fail",
            "combinedPolyline.lines.length < 3",
            "Net #" + netNumbers[0] + ",Layer #" + layer,
            new Point[] {fromCorner, toCorner});
      }
      return fromCorner;
    }
    int startShapeNo = combinedPolyline.lines.length - newPolyline.lines.length;
    // calculate the last shapes of combinedPolyline for checking
    TileShape[] traceShapes =
        combinedPolyline.offsetShapes(
            compensatedHalfWidth, startShapeNo, combinedPolyline.lines.length - 1);
    int lastShapeNo = traceShapes.length;
    boolean orthogonalMode = rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE;
    int idBeforeShoveLoop = communication.idNoGenerator.maxGeneratedNo();
    for (int i = 0; i < traceShapes.length; i++) {
      TileShape currentTraceShape = traceShapes[i];
      if (orthogonalMode) {
        currentTraceShape = currentTraceShape.boundingBox();
      }
      ShapeEntrySide fromSide =
          new ShapeEntrySide(
              combinedPolyline,
              combinedPolyline.cornerCount() - traceShapes.length - 1 + i,
              currentTraceShape);
      if (withCheck) {
        boolean checkShoveOk =
            shoveTraceAlgo.check(
                currentTraceShape,
                fromSide,
                null,
                layer,
                netNumbers,
                clearanceClassIndex,
                maxRecursionDepth,
                maxViaRecursionDepth,
                maxSpringOverRecursionDepth,
                timeLimit);
        if (!checkShoveOk) {
          lastShapeNo = i;
          break;
        }
      }
      int idBeforeShove = communication.idNoGenerator.maxGeneratedNo();
      boolean insertOk =
          shoveTraceAlgo.insert(
              currentTraceShape,
              fromSide,
              layer,
              netNumbers,
              clearanceClassIndex,
              null,
              maxRecursionDepth,
              maxViaRecursionDepth,
              maxSpringOverRecursionDepth);
      int idAfterShove = communication.idNoGenerator.maxGeneratedNo();
      if (netNumbers != null && netNumbers.length > 0) {
        FRLogger.trace(
            "compare_trace_shove_shape net="
                + netNumbers[0]
                + ", shapeIdx="
                + i
                + ", idBefore="
                + idBeforeShove
                + ", idAfter="
                + idAfterShove
                + ", delta="
                + (idAfterShove - idBeforeShove));
      }
      if (!insertOk) {
        return null;
      }
    }
    Point newCorner = toCorner;
    if (netNumbers != null && netNumbers.length > 0) {
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + netNumbers[0]
              + ", step=after_shove_loop, shoveLoopDelta="
              + (communication.idNoGenerator.maxGeneratedNo() - idBeforeShoveLoop)
              + ", lastShapeNo="
              + lastShapeNo
              + ", traceShapes.length="
              + traceShapes.length
              + ", idMax="
              + communication.idNoGenerator.maxGeneratedNo());
    }
    if (lastShapeNo < traceShapes.length) {
      // the shove with index lastShapeNo failed.
      // Sample the shove line to a shorter shove distance and try again.
      TileShape lastTraceShape = traceShapes[lastShapeNo];
      if (orthogonalMode) {
        lastTraceShape = lastTraceShape.boundingBox();
      }
      int sampleWidth = 2 * this.getMinTraceHalfWidth();
      FloatPoint lastCorner = newPolyline.cornerApprox(lastShapeNo + 1);
      FloatPoint prevLastCorner = newPolyline.cornerApprox(lastShapeNo);
      double lastSegmentLength = lastCorner.distance(prevLastCorner);
      if (lastSegmentLength > 100 * sampleWidth) {
        // to many cycles to sample
        if (netNumbers != null && netNumbers.length > 0 && netNumbers[0] == 94) {
          FRLogger.trace(
              "RoutingBoard.insert_forced_trace_polyline",
              "compare_trace_insert_forced_fail",
              "too many cycles to sample",
              "Net #" + netNumbers[0] + ",Layer #" + layer,
              new Point[] {fromCorner, toCorner});
        }
        return fromCorner;
      }
      int shapeIndex = combinedPolyline.cornerCount() - traceShapes.length - 1 + lastShapeNo;
      if (lastSegmentLength > sampleWidth) {
        newPolyline =
            newPolyline.shorten(
                newPolyline.lines.length - (traceShapes.length - lastShapeNo - 1), sampleWidth);
        Point currentLastCorner = newPolyline.lastCorner();
        if (!(currentLastCorner instanceof IntPoint)) {
          FRLogger.trace("RoutingBoard.insert_forced_trace_polyline: IntPoint expected");
          if (netNumbers != null && netNumbers.length > 0 && netNumbers[0] == 94) {
            FRLogger.trace(
                "RoutingBoard.insert_forced_trace_polyline",
                "compare_trace_insert_forced_fail",
                "currentLastCorner is not an IntPoint",
                "Net #" + netNumbers[0] + ",Layer #" + layer,
                new Point[] {fromCorner, toCorner});
          }
          return fromCorner;
        }
        newCorner = currentLastCorner;
        if (pickedTrace == null) {
          combinedPolyline = newPolyline;
        } else {
          PolylineTrace combineTrace = (PolylineTrace) pickedTrace;
          combinedPolyline = newPolyline.combine(combineTrace.polyline());
        }
        if (combinedPolyline.lines.length < 3) {
          return newCorner;
        }
        shapeIndex = combinedPolyline.lines.length - 3;
        lastTraceShape = combinedPolyline.offsetShape(compensatedHalfWidth, shapeIndex);
        if (orthogonalMode) {
          lastTraceShape = lastTraceShape.boundingBox();
        }
      }
      ShapeEntrySide fromSide = new ShapeEntrySide(combinedPolyline, shapeIndex, lastTraceShape);
      boolean checkShoveOk =
          shoveTraceAlgo.check(
              lastTraceShape,
              fromSide,
              null,
              layer,
              netNumbers,
              clearanceClassIndex,
              maxRecursionDepth,
              maxViaRecursionDepth,
              maxSpringOverRecursionDepth,
              timeLimit);
      if (!checkShoveOk) {
        if (netNumbers != null && netNumbers.length > 0 && netNumbers[0] == 94) {
          Item shoveFailingObstacle = this.getShoveFailingObstacle();
          FRLogger.trace(
              "RoutingBoard.insert_forced_trace_polyline",
              "compare_trace_insert_forced_fail",
              "checkShoveOk returned false",
              "Net #" + netNumbers[0] + ",Layer #" + layer,
              new Point[] {fromCorner, toCorner});
          FRLogger.trace(
              "RoutingBoard.insert_forced_trace_polyline",
              "compare_trace_insert_forced_obstacle",
              "failing obstacle=" + shoveFailingObstacle,
              "Net #"
                  + netNumbers[0]
                  + ",Layer #"
                  + layer
                  + ",Obstacle="
                  + (shoveFailingObstacle == null
                      ? "null"
                      : shoveFailingObstacle.getClass().getSimpleName()
                          + "#"
                          + shoveFailingObstacle.getIdNo()),
              new Point[] {fromCorner, toCorner});
        }
        return fromCorner;
      }
      boolean insertOk =
          shoveTraceAlgo.insert(
              lastTraceShape,
              fromSide,
              layer,
              netNumbers,
              clearanceClassIndex,
              null,
              maxRecursionDepth,
              maxViaRecursionDepth,
              maxSpringOverRecursionDepth);
      if (!insertOk) {
        FRLogger.trace("RoutingBoard.insert_forced_trace_polyline: shove trace failed");
        return null;
      }
    }
    // insert the new trace segment
    for (int i = 0; i < newPolyline.cornerCount(); i++) {
      joinChangedArea(newPolyline.cornerApprox(i), layer);
    }
    int idBeforeInsert = communication.idNoGenerator.maxGeneratedNo();
    PolylineTrace newTrace =
        insertTraceWithoutCleaning(
            newPolyline, layer, halfWidth, netNumbers, clearanceClassIndex, FixedState.UNFIXED);
    int idAfterInsert = communication.idNoGenerator.maxGeneratedNo();
    boolean combineResult = newTrace.combine();
    int idAfterCombine = communication.idNoGenerator.maxGeneratedNo();
    if (netNumbers != null && netNumbers.length > 0) {
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + netNumbers[0]
              + ", step=insert_and_combine"
              + ", insertDelta="
              + (idAfterInsert - idBeforeInsert)
              + ", combineDelta="
              + (idAfterCombine - idAfterInsert)
              + ", combined="
              + combineResult
              + ", idMax="
              + idAfterCombine);
    }

    IntOctagon tidyRegion = null;
    if (tidyWidth < Integer.MAX_VALUE) {
      tidyRegion = newCorner.surroundingOctagon().enlarge(tidyWidth);
    }
    int[] optNetNoArr;
    if (maxRecursionDepth <= 0) {
      optNetNoArr = netNumbers;
    } else {
      optNetNoArr = new int[0];
    }
    TraceTightener pullTightAlgo =
        TraceTightener.getInstance(
            this, optNetNoArr, tidyRegion, pullTightAccuracy, null, -1, newCorner, layer);

    try {
      // Remove evtl. generated cycles because otherwise pullTight may not work
      // correctly.
      int idBeforeNorm = communication.idNoGenerator.maxGeneratedNo();
      boolean normalizeResult = newTrace != null && newTrace.normalize(changedArea.getArea(layer));
      int idAfterNorm = communication.idNoGenerator.maxGeneratedNo();
      if (netNumbers != null && netNumbers.length > 0) {
        FRLogger.trace(
            "compare_trace_insert_forced_sub net="
                + netNumbers[0]
                + ", step=normalize, result="
                + normalizeResult
                + ", idBefore="
                + idBeforeNorm
                + ", idAfter="
                + idAfterNorm
                + ", delta="
                + (idAfterNorm - idBeforeNorm));
      }
      if (normalizeResult) {

        int idBeforeSplit = communication.idNoGenerator.maxGeneratedNo();
        pullTightAlgo.splitTracesAtKeepPoint();
        int idAfterSplit = communication.idNoGenerator.maxGeneratedNo();
        if (netNumbers != null && netNumbers.length > 0) {
          FRLogger.trace(
              "compare_trace_insert_forced_sub net="
                  + netNumbers[0]
                  + ", step=split_at_keep, idBefore="
                  + idBeforeSplit
                  + ", idAfter="
                  + idAfterSplit
                  + ", delta="
                  + (idAfterSplit - idBeforeSplit));
        }
        // otherwise the new corner may no more be contained in the new trace after
        // optimizing
        ItemSelectionFilter itemFilter =
            new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
        Set<Item> currentPickedItems = this.pickItems(newCorner, layer, itemFilter);
        newTrace = null;
        if (!currentPickedItems.isEmpty()) {
          Item foundTrace = currentPickedItems.iterator().next();
          if (foundTrace instanceof PolylineTrace trace) {
            newTrace = trace;
          }
        }
      }
    } catch (Exception e) {
      // Max normalization depth is hit for geometrically complex or degenerate trace segments.
      // The router skips the segment and continues; affected connections may remain unrouted.
      FRLogger.trace(
          "RoutingBoard.insert_forced_trace_polyline: A trace could not be normalized"
              + " and was skipped. Cause: "
              + e.getMessage());
    }

    // To avoid, that a separate handling for moving backwards in the own trace line
    // becomes necessary, pull tight is called here.
    if (netNumbers != null && netNumbers.length > 0) {
      ItemSelectionFilter dbgFilter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
      Set<Item> dbgBefore = this.pickItems(newCorner, layer, dbgFilter);
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + netNumbers[0]
              + ", step=before_pull_tight, pickedAtEndCorner="
              + dbgBefore.size()
              + ", new_trace_null="
              + (newTrace == null)
              + ", newCorner="
              + newCorner);
    }
    if (tidyWidth > 0 && newTrace != null) {
      newTrace.pullTight(pullTightAlgo);
    }
    if (netNumbers != null && netNumbers.length > 0) {
      ItemSelectionFilter dbgFilter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
      Set<Item> dbgAfter = this.pickItems(newCorner, layer, dbgFilter);
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + netNumbers[0]
              + ", step=after_pull_tight, pickedAtEndCorner="
              + dbgAfter.size()
              + ", newCorner="
              + newCorner);
    }
    return newCorner;
  }

  /**
   * Initialises the auto-route database for routing a connection. If retainAutorouteDatabase, the
   * auto-route database is retained and maintained after the algorithm for performance reasons.
   */
  public AutorouteEngine initAutoroute(
      int netNumber,
      int traceClearanceClassIndex,
      Stoppable stoppableThread,
      TimeLimit timeLimit,
      boolean retainAutorouteDatabase) {
    if (this.autorouteEngine == null
        || !retainAutorouteDatabase
        || this.autorouteEngine.autorouteSearchTree.compensatedClearanceClassNo
            != traceClearanceClassIndex) {
      this.autorouteEngine =
          new AutorouteEngine(this, traceClearanceClassIndex, retainAutorouteDatabase);
    }
    this.autorouteEngine.initConnection(netNumber, stoppableThread, timeLimit);
    return this.autorouteEngine;
  }

  /** Clears the auto-route database in case it was retained. */
  public void finishAutoroute() {
    if (this.autorouteEngine != null) {
      this.autorouteEngine.clear();
    }
    this.autorouteEngine = null;
  }

  /**
   * Routes automatically item to another item of the same net, to which it is not yet electrically
   * connected. Returns an enum of type AutorouteAttemptState
   */
  public AutorouteAttemptResult autoroute(
      Item item,
      RouterSettings routerSettings,
      int viaCosts,
      Stoppable stoppableThread,
      TimeLimit timeLimit) {
    if (!(item instanceof Connectable) || item.netCount() == 0) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.NO_CONNECTIONS, "The item '" + item + "' is not connectable.");
    }
    if (item.netCount() > 1) {
      FRLogger.warn("RoutingBoard.autoroute: netCount > 1 not yet implemented");
    }
    int routeNetNo = item.getNetNumber(0);
    AutorouteControl ctrlSettings =
        new AutorouteControl(
            this, routeNetNo, routerSettings, viaCosts, routerSettings.getTraceCosts());
    ctrlSettings.removeUnconnectedVias = false;
    Set<Item> routeStartSet = item.getConnectedSet(routeNetNo);
    Net routeNet = rules.nets.get(routeNetNo);
    if (routeNet != null && routeNet.containsPlane()) {
      for (Item currentItem : routeStartSet) {
        if (currentItem instanceof ConductionArea) {
          return new AutorouteAttemptResult(
              AutorouteAttemptState.CONNECTED_TO_PLANE,
              "The item '" + currentItem + "' is connected to a plane.");
        }
      }
    }
    Set<Item> routeDestSet = item.getUnconnectedSet(routeNetNo);
    if (routeDestSet.isEmpty()) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.ALREADY_CONNECTED, "The item '" + item + "' is already connected.");
    }
    SortedSet<Item> rippedItemList = new TreeSet<>();
    AutorouteEngine currentAutorouteEngine =
        initAutoroute(
            item.getNetNumber(0),
            ctrlSettings.traceClearanceClassIndex,
            stoppableThread,
            timeLimit,
            false);
    AutorouteAttemptResult result =
        currentAutorouteEngine.autorouteConnection(
            routeStartSet,
            routeDestSet,
            ctrlSettings,
            rippedItemList,
            null); // null: costs not needed here
    if (result.state == AutorouteAttemptState.ROUTED) {
      final int timeLimitToPreventEndlessLoop = 1000;
      optChangedArea(
          new int[] {routeNetNo},
          null,
          routerSettings.tracePullTightAccuracy,
          ctrlSettings.traceCosts,
          stoppableThread,
          timeLimitToPreventEndlessLoop);
    }
    return result;
  }

  /**
   * Autoroutes from the input pin until the first via, in case the pin and its connected set has
   * only 1 layer. Ripup is allowed if ripupCosts is {@literal >}= 0. Returns an enum of type
   * AutorouteEngine.AutorouteResult
   */
  public AutorouteAttemptResult fanout(
      Pin pin,
      RouterSettings routerSettings,
      int ripupCosts,
      Stoppable stoppableThread,
      TimeLimit timeLimit) {
    if (pin.firstLayer() != pin.lastLayer() || pin.netCount() != 1) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.ALREADY_CONNECTED, "The pin '" + pin + "' is already connected.");
    }
    int pinNetNo = pin.getNetNumber(0);
    int pinLayer = pin.firstLayer();
    Set<Item> pinConnectedSet = pin.getConnectedSet(pinNetNo);
    for (Item currentItem : pinConnectedSet) {
      if (currentItem.firstLayer() != pinLayer || currentItem.lastLayer() != pinLayer) {
        return new AutorouteAttemptResult(
            AutorouteAttemptState.ALREADY_CONNECTED, "The pin '" + pin + "' is already connected.");
      }
    }
    Set<Item> unconnectedSet = pin.getUnconnectedSet(pinNetNo);
    if (unconnectedSet.isEmpty()) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.NO_UNCONNECTED_NETS, "The pin '" + pin + "' is already connected.");
    }
    app.freerouting.geometry.planar.FloatPoint pinCenter = pin.getCenter().toFloat();
    java.util.List<Item> sortedUnconnectedList = new java.util.ArrayList<>(unconnectedSet);
    sortedUnconnectedList.sort(
        (item1, item2) -> {
          app.freerouting.geometry.planar.IntBox box1 = item1.boundingBox();
          double cx1 = (box1.ll.x + box1.ur.x) / 2.0;
          double cy1 = (box1.ll.y + box1.ur.y) / 2.0;
          double dx1 = cx1 - pinCenter.x;
          double dy1 = cy1 - pinCenter.y;
          double distSq1 = dx1 * dx1 + dy1 * dy1;

          app.freerouting.geometry.planar.IntBox box2 = item2.boundingBox();
          double cx2 = (box2.ll.x + box2.ur.x) / 2.0;
          double cy2 = (box2.ll.y + box2.ur.y) / 2.0;
          double dx2 = cx2 - pinCenter.x;
          double dy2 = cy2 - pinCenter.y;
          double distSq2 = dx2 * dx2 + dy2 * dy2;

          return Double.compare(distSq1, distSq2);
        });

    AutorouteControl ctrlSettings = new AutorouteControl(this, pinNetNo, routerSettings);
    ctrlSettings.isFanout = true;
    if (routerSettings.fanout != null
        && Boolean.TRUE.equals(routerSettings.fanout.fallbackToBoardVias)
        && ctrlSettings.viaRule != null) {
      app.freerouting.rules.ViaRule combinedViaRule =
          new app.freerouting.rules.ViaRule(ctrlSettings.viaRule.name + "_fallback");
      for (int i = 0; i < ctrlSettings.viaRule.viaCount(); i++) {
        combinedViaRule.appendVia(ctrlSettings.viaRule.getVia(i));
      }
      if (!this.rules.viaRules.isEmpty()) {
        app.freerouting.rules.ViaRule defaultViaRule = this.rules.viaRules.firstElement();
        for (int i = 0; i < defaultViaRule.viaCount(); i++) {
          app.freerouting.rules.ViaInfo defaultVia = defaultViaRule.getVia(i);
          if (!combinedViaRule.contains(defaultVia)) {
            combinedViaRule.appendVia(defaultVia);
          }
        }
      }
      ctrlSettings.viaRule = combinedViaRule;
      ctrlSettings.rebuildViaInfo(this, routerSettings.getViaCosts(), pinNetNo);
    }
    Component pinComponent = this.components.get(pin.getComponentNo());
    if (pinComponent != null && pin.name() != null) {
      ctrlSettings.fanoutStartPinName = pinComponent.name + "-" + pin.name();
    } else {
      ctrlSettings.fanoutStartPinName = pin.toString();
    }
    ctrlSettings.fanoutStartPinCenter = pin.getCenter();
    ctrlSettings.fanoutStartPinLayer = pin.firstLayer();
    ctrlSettings.removeUnconnectedVias = false;
    if (ripupCosts >= 0) {
      ctrlSettings.ripupAllowed = true;
      ctrlSettings.ripupCosts = ripupCosts;
    }
    SortedSet<Item> rippedItemList = new TreeSet<>();
    AutorouteEngine currentAutorouteEngine =
        initAutoroute(
            pinNetNo, ctrlSettings.traceClearanceClassIndex, stoppableThread, timeLimit, false);

    AutorouteAttemptResult result = null;
    if (sortedUnconnectedList.size() <= 4) {
      if (!sortedUnconnectedList.isEmpty()) {
        // 1. Try to route to the closest target first
        Item closestTarget = sortedUnconnectedList.get(0);
        result =
            currentAutorouteEngine.autorouteConnection(
                pinConnectedSet,
                Set.of(closestTarget),
                ctrlSettings,
                rippedItemList,
                null); // null: costs not needed here

        // 2. If that fails and we have other targets, fall back to searching the entire unconnected
        // set at once
        if (result.state != AutorouteAttemptState.ROUTED
            && result.state != AutorouteAttemptState.ALREADY_CONNECTED
            && sortedUnconnectedList.size() > 1) {
          result =
              currentAutorouteEngine.autorouteConnection(
                  pinConnectedSet, unconnectedSet, ctrlSettings, rippedItemList, null);
        }
      }
    } else {
      // For large nets (e.g. power/ground/buses), route to the entire unconnected set at once to
      // avoid CPU thrashing
      result =
          currentAutorouteEngine.autorouteConnection(
              pinConnectedSet, unconnectedSet, ctrlSettings, rippedItemList, null);
    }
    if (result == null) {
      result =
          new AutorouteAttemptResult(
              AutorouteAttemptState.FAILED, "No target items to route connection.");
    }

    if (result.state == AutorouteAttemptState.ROUTED) {
      final int timeLimitToPreventEndlessLoop = 1000;
      optChangedArea(
          new int[] {pinNetNo},
          null,
          routerSettings.tracePullTightAccuracy,
          ctrlSettings.traceCosts,
          stoppableThread,
          timeLimitToPreventEndlessLoop);
    }
    return result;
  }

  /**
   * Inserts a trace from fromPoint to the nearest point on toTrace. Returns false, if that is not
   * possible without clearance violation.
   */
  public boolean connectToTrace(IntPoint fromPoint, Trace toTrace, int penHalfWidth, int clType) {

    if (!(toTrace instanceof PolylineTrace polylineTrace)) {
      return false; // not yet implemented
    }

    if (polylineTrace.polyline().contains(fromPoint)) {
      // no connection line necessary
      return true;
    }
    LineSegment projectionLine = polylineTrace.polyline().projectionLine(fromPoint);
    if (projectionLine == null) {
      return false;
    }
    Polyline connectionLine = projectionLine.toPolyline();
    if (connectionLine == null || connectionLine.lines.length != 3) {
      return false;
    }
    int traceLayer = toTrace.getLayer();
    if (!this.checkPolylineTrace(
        connectionLine, traceLayer, penHalfWidth, toTrace.netNumbers, clType)) {
      return false;
    }
    if (this.changedArea != null) {
      for (int i = 0; i < connectionLine.cornerCount(); i++) {
        this.changedArea.join(connectionLine.cornerApprox(i), traceLayer);
      }
    }

    this.insertTrace(
        connectionLine, traceLayer, penHalfWidth, toTrace.netNumbers, clType, FixedState.UNFIXED);

    Point firstCorner = toTrace.firstCorner();
    Point lastCorner = toTrace.lastCorner();
    int[] netNumbers = toTrace.netNumbers;
    if (!fromPoint.equals(firstCorner)) {
      Trace tail = this.getTraceTail(firstCorner, traceLayer, netNumbers);
      if (tail != null && !tail.isUserFixed()) {
        this.removeItem(tail);
      }
    }
    if (!fromPoint.equals(lastCorner)) {
      Trace tail = this.getTraceTail(lastCorner, traceLayer, netNumbers);
      if (tail != null && !tail.isUserFixed()) {
        this.removeItem(tail);
      }
    }
    return true;
  }

  /**
   * Checks, if the list items contains traces, which have no contact at their start or end point.
   * Trace with net number exceptNetNo are ignored.
   */
  public boolean containsTraceTails(Collection<Item> items, int[] exceptNetNoArr) {
    for (Item currentObject : items) {
      if (currentObject instanceof Trace currentTrace) {
        if (!currentTrace.netsEqual(exceptNetNoArr)) {
          if (currentTrace.isTail()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Removes all trace tails of the input net. If netNumber {@literal <}= 0, the tails of all nets
   * are removed. Returns true, if something was removed.
   */
  public boolean removeTraceTails(int netNumber, Item.StopConnectionOption stopConnectionOption) {
    SortedSet<Item> stubSet = new TreeSet<>();
    Collection<Item> boardItems = this.getItems();
    for (Item currentItem : boardItems) {
      if (!currentItem.isRoutable()) {
        continue;
      }
      if (currentItem.netCount() != 1) {
        continue;
      }
      if (netNumber > 0 && currentItem.getNetNumber(0) != netNumber) {
        continue;
      }
      if (currentItem.isTail()) {
        if (currentItem instanceof Via) {
          if (stopConnectionOption == Item.StopConnectionOption.VIA) {
            continue;
          }
          if (stopConnectionOption == Item.StopConnectionOption.FANOUT_VIA) {
            if (currentItem.isFanoutVia(null)) {
              continue;
            }
          }
        }
        stubSet.add(currentItem);
      }
    }
    SortedSet<Item> stubConnections = new TreeSet<>();
    for (Item currentItem : stubSet) {
      int itemContactCount = currentItem.getNormalContacts().size();
      if (itemContactCount == 1) {
        stubConnections.addAll(currentItem.getConnectionItems(stopConnectionOption));
      } else {
        // the connected items are no stubs for example if a via is only connected on 1
        // layer,
        // but to several traces.
        stubConnections.add(currentItem);
      }
    }
    if (stubConnections.isEmpty()) {
      return false;
    }
    this.removeItems(stubConnections);
    this.combineTraces(netNumber);
    return true;
  }

  /** Clear all item temporary autoroute data. */
  public void clearAllItemTemporaryAutorouteData() {
    Iterator<UndoableObjects.UndoableObjectNode> it = this.itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      currentItem.clearAutorouteInfo();
    }
  }

  /** Sets, if all conduction areas on the board are obstacles for route of foreign nets. */
  public void changeConductionIsObstacle(boolean value) {
    if (this.rules.getIgnoreConduction() != value) {
      return; // no multiply
    }
    boolean somethingChanged = false;
    // Change the isObstacle property of all conduction areas of the board.
    Iterator<UndoableObjects.UndoableObjectNode> it = itemList.startReadObject();
    for (; ; ) {
      Item currentItem = (Item) itemList.readObject(it);
      if (currentItem == null) {
        break;
      }
      if (currentItem instanceof ConductionArea currentConductionArea) {
        Layer currentLayer = layerStructure.layers[currentConductionArea.getLayer()];
        if (currentLayer.isSignal && currentConductionArea.getIsObstacle() != value) {
          currentConductionArea.setIsObstacle(value);
          somethingChanged = true;
        }
      }
    }
    this.rules.setIgnoreConduction(!value);
    if (somethingChanged) {
      this.searchTreeManager.reinsertTreeItems();
    }
  }

  /**
   * Tries to educe the nets of traces and vias, so that the nets are a subset of the nets of the
   * contact items. This is applied to traces and vias with more than 1 net connected to tie pins.
   * Returns true, if the nets of some items were reduced.
   */
  public boolean reduceNetsOfRouteItems() {
    boolean result = false;
    boolean somethingChanged = true;
    while (somethingChanged) {
      somethingChanged = false;
      Iterator<UndoableObjects.UndoableObjectNode> it = itemList.startReadObject();
      for (; ; ) {
        UndoableObjects.Storable currentObject = itemList.readObject(it);
        if (currentObject == null) {
          break;
        }
        Item currentItem = (Item) currentObject;
        if (currentItem.netNumbers.length <= 1
            || currentItem.getFixedState() == FixedState.SYSTEM_FIXED) {
          continue;
        }
        if (currentObject instanceof Via) {
          Collection<Item> contacts = currentItem.getNormalContacts();
          for (int currentNetNumber : currentItem.netNumbers) {
            for (Item currentContact : contacts) {
              if (!currentContact.containsNet(currentNetNumber)) {
                currentItem.removeFromNet(currentNetNumber);
                somethingChanged = true;
                break;
              }
            }
            if (somethingChanged) {
              break;
            }
          }

        } else if (currentObject instanceof Trace currentTrace) {
          Collection<Item> contacts = currentTrace.getStartContacts();
          for (int i = 0; i < 2; i++) {
            for (int currentNetNumber : currentItem.netNumbers) {
              boolean pinFound = false;
              for (Item currentContact : contacts) {
                if (currentContact instanceof Pin) {
                  pinFound = true;
                  if (!currentContact.containsNet(currentNetNumber)) {
                    currentItem.removeFromNet(currentNetNumber);
                    somethingChanged = true;
                    break;
                  }
                }
              }
              if (!pinFound) { // at tie pins traces may have different nets
                for (Item currentContact : contacts) {
                  if (!(currentContact instanceof Pin)
                      && !currentContact.containsNet(currentNetNumber)) {
                    currentItem.removeFromNet(currentNetNumber);
                    somethingChanged = true;
                    break;
                  }
                }
              }
            }
            if (somethingChanged) {
              break;
            }
            contacts = currentTrace.getEndContacts();
          }
          if (somethingChanged) {
            break;
          }
        }
        if (somethingChanged) {
          break;
        }
      }
    }
    return result;
  }

  /** Returns the obstacle responsible for the last shove to fail. */
  public Item getShoveFailingObstacle() {
    return shoveFailingObstacle;
  }

  void setShoveFailingObstacle(Item item) {
    shoveFailingObstacle = item;
  }

  public int getShoveFailingLayer() {
    return shoveFailingLayer;
  }

  void setShoveFailingLayer(int layer) {
    shoveFailingLayer = layer;
  }

  private void clearShoveFailingObstacle() {
    shoveFailingObstacle = null;
    shoveFailingLayer = -1;
  }

  /**
   * Returns, if the auto-route database is maintained outside the auto-route algorithm while
   * changing items on rhe board.
   */
  boolean isMaintainingAutorouteDatabase() {
    return this.autorouteEngine != null;
  }

  /**
   * Sets, if the auto-route database has to be maintained outside the auto-route algorithm while
   * changing items on rhe board.
   */
  void setMaintainingAutorouteDatabase(boolean value) {
    if (value) {

    } else {
      this.autorouteEngine = null;
    }
  }

  public BoardStatistics getStatistics() {
    return new BoardStatistics(this);
  }

  /**
   * Create a deep copy of the routing board. This method is similar to the BasicBoard.clone method,
   * but it copies the routing related values as well.
   */
  public synchronized RoutingBoard deepCopy() {
    ObjectOutputStream oos = null;
    ObjectInputStream objectInputStream = null;

    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(bos);

      oos.writeObject(this); // serialize this.board
      oos.flush();

      ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
      objectInputStream = new ObjectInputStream(bin);

      RoutingBoard boardCopy = (RoutingBoard) objectInputStream.readObject();

      // boardCopy.clear_autoroute_database();
      boardCopy.clearAllItemTemporaryAutorouteData();
      boardCopy.finishAutoroute();

      return boardCopy;
    } catch (Exception e) {
      FRLogger.error("Exception in deep_copy_routing_board" + e, e);
      return null;
    } finally {
      try {
        if (oos != null) {
          oos.close();
        }
        if (objectInputStream != null) {
          objectInputStream.close();
        }
      } catch (Exception _) {
        // Best-effort stream cleanup during board deserialization.
      }
    }
  }
}

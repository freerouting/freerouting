package app.freerouting.gui.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Unit;
import app.freerouting.core.Padstack;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Ellipse;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Limits;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.rendering.GraphicsContext;
import app.freerouting.gui.rendering.NetIncompletesGraphics;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

/** Functionality for interactive routing. */
public class Route {

  /** The time limit in milliseconds for the forced-trace check. */
  private static final int CHECK_FORCED_TRACE_TIME_LIMIT = 3000;

  /** The time limit in milliseconds for the pull-tight algorithm. */
  private static final int PULL_TIGHT_TIME_LIMIT = 2000;

  /** The net numbers used for routing. */
  final int[] netNoArr;

  private final Item startItem;
  private final Set<Item> targetSet;

  /** Pins that can be reached by a pin swap from a target pin. */
  private final Set<SwapPinInfo> swapPinInfos;

  private final int[] penHalfWidthArr;
  private final boolean[] layerActive;
  private final int clearanceClass;
  private final ViaRule viaRule;
  private final int maxShoveTraceRecursionDepth;
  private final int maxShoveViaRecursionDepth;
  private final int maxSpringOverRecursionDepth;
  private final int traceTidyWidth;
  private final int pullTightAccuracy;
  private final RoutingBoard board;
  private final boolean isStitchMode;
  private final boolean withNeckdown;
  private final boolean viaSnapToSmdCenter;
  private final boolean highlightShoveFailingObstacle;
  private final int pullTightTimeLimit;
  private Point prevCorner;
  private int layer;
  private Collection<TargetPoint> targetPoints; // From drill items.
  private Collection<Item> targetTracesAndAreas; // from traces and conduction areas
  private FloatPoint nearestTargetPoint;
  private Item nearestTargetItem;
  private Item shoveFailingObstacle;

  /**
   * Starts routing a connection. The trace half-width array is provided because it may be different
   * from the half-width array in the board rules.
   */
  public Route(
      Point startCorner,
      int layerNo,
      int[] penHalfWidthArr,
      boolean[] layerActiveArr,
      int[] netNoArr,
      int clearanceClassNo,
      ViaRule viaRuleValue,
      boolean pushEnabled,
      int traceTidyWidthValue,
      int pullTightAccuracyValue,
      Item startItem,
      Set<Item> targetSetValue,
      RoutingBoard routingBoard,
      boolean stitchMode,
      boolean neckdown,
      boolean snapToSmdCenter,
      boolean highlightShoveFailingObstacleValue) {
    board = routingBoard;
    layer = layerNo;
    if (pushEnabled) {
      maxShoveTraceRecursionDepth = 20;
      maxShoveViaRecursionDepth = 8;
      maxSpringOverRecursionDepth = 5;
    } else {
      maxShoveTraceRecursionDepth = 0;
      maxShoveViaRecursionDepth = 0;
      maxSpringOverRecursionDepth = 0;
    }
    traceTidyWidth = traceTidyWidthValue;
    pullTightAccuracy = pullTightAccuracyValue;
    prevCorner = startCorner;
    this.netNoArr = netNoArr;
    this.penHalfWidthArr = penHalfWidthArr;
    this.layerActive = layerActiveArr;
    this.clearanceClass = clearanceClassNo;
    this.viaRule = viaRuleValue;
    this.startItem = startItem;
    this.targetSet = targetSetValue;
    this.isStitchMode = stitchMode;
    this.withNeckdown = neckdown;
    this.viaSnapToSmdCenter = snapToSmdCenter;
    this.highlightShoveFailingObstacle = highlightShoveFailingObstacleValue;
    pullTightTimeLimit = PULL_TIGHT_TIME_LIMIT;

    calculateTargetPointsAndAreas();
    swapPinInfos = calculateSwapPinInfos();
  }

  /**
   * Appends a line to the trace routed so far.
   *
   * @return true if the route is completed by connecting to a target
   */
  public boolean nextCorner(FloatPoint corner) {
    if (!this.layerActive[this.layer]) {
      return false;
    }
    IntPoint currCorner = corner.round();
    if (!(board.contains(prevCorner)
        && board.contains(currCorner)
        && board.layerStructure.arr[this.layer].isSignal)) {
      return false;
    }

    if (currCorner.equals(prevCorner)) {
      return false;
    }
    if (nearestTargetItem instanceof DrillItem target) {
      if (this.prevCorner.equals(target.getCenter())) {
        return true; // connection already completed at prevCorner.
      }
    }
    this.shoveFailingObstacle = null;
    AngleRestriction angleRestriction = this.board.rules.getTraceAngleRestriction();
    if (angleRestriction != AngleRestriction.NONE && !(prevCorner instanceof IntPoint)) {
      return false;
    }
    if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
      currCorner = currCorner.orthogonalProjection((IntPoint) prevCorner);
    } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      currCorner = currCorner.fortyfiveDegreeProjection((IntPoint) prevCorner);
    }
    Item endRoutingItem = board.pickNearestRoutingItem(prevCorner, this.layer, null);
    // look for a nearby item of this net, which is not connected to
    // endRoutingItem.
    nearestTargetItem = board.pickNearestRoutingItem(currCorner, this.layer, endRoutingItem);
    TimeLimit checkForcedTraceTimeLimit;
    if (isStitchMode) {
      // because no check before inserting in this case
      checkForcedTraceTimeLimit = null;
    } else {
      checkForcedTraceTimeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
    }

    // app.freerouting.tests.Validate.check("before insert", app.freerouting.board);
    Point okPoint =
        board.insertForcedTraceSegment(
            prevCorner,
            currCorner,
            penHalfWidthArr[layer],
            layer,
            netNoArr,
            clearanceClass,
            maxShoveTraceRecursionDepth,
            maxShoveViaRecursionDepth,
            maxSpringOverRecursionDepth,
            traceTidyWidth,
            pullTightAccuracy,
            !isStitchMode,
            checkForcedTraceTimeLimit);
    // app.freerouting.tests.Validate.check("after insert", app.freerouting.board);
    if (okPoint == prevCorner && this.withNeckdown) {
      okPoint = tryNeckdownAtStart(currCorner);
    }
    if (okPoint == prevCorner && this.withNeckdown) {
      okPoint = tryNeckdownAtEnd(this.prevCorner, currCorner);
    }
    if (okPoint == null) {
      // database may be damaged, restore previous situation
      board.undo(null);
      // end routing in case it is dynamic
      return !isStitchMode;
    }

    if (okPoint == prevCorner) {
      setShoveFailingObstacle(board.getShoveFailingObstacle());
      return false;
    }
    this.prevCorner = okPoint;
    // check, if a target is reached
    boolean routeCompleted = false;
    if (okPoint == currCorner) {
      routeCompleted = connectToTarget(currCorner);
    }

    IntOctagon tidyClipShape;
    if (traceTidyWidth == Integer.MAX_VALUE) {
      tidyClipShape = null;
    } else if (traceTidyWidth == 0) {
      tidyClipShape = IntOctagon.EMPTY;
    } else {
      tidyClipShape = okPoint.surroundingOctagon().enlarge(traceTidyWidth);
    }
    int[] optNetNoArr;
    if (maxShoveTraceRecursionDepth <= 0) {
      optNetNoArr = netNoArr;
    } else {
      optNetNoArr = new int[0];
    }
    if (routeCompleted) {
      this.board.reduceNetsOfRouteItems();
      for (int currNetNo : this.netNoArr) {
        this.board.combineTraces(currNetNo);
      }
    } else {
      calcNearestTargetPoint(this.prevCorner.toFloat());
    }
    board.optChangedArea(
        optNetNoArr,
        tidyClipShape,
        pullTightAccuracy,
        null,
        null,
        pullTightTimeLimit,
        okPoint,
        layer);
    return routeCompleted;
  }

  /**
   * Changes the interactive route layer and inserts a via.
   *
   * @return true if the layer change was possible
   */
  public boolean changeLayer(int toLayer) {
    if (this.layer == toLayer) {
      return true;
    }
    if (toLayer < 0 || toLayer >= this.layerActive.length) {
      FRLogger.warn("Route.change_layer: p_to_layer out of range");
      return false;
    }
    if (!this.layerActive[toLayer]) {
      return false;
    }
    if (this.viaRule == null) {
      return false;
    }
    this.shoveFailingObstacle = null;
    if (this.viaSnapToSmdCenter) {
      boolean snappedToSmdCenter = snapToSmdCenter(toLayer);
      if (!snappedToSmdCenter) {
        snapToSmdCenter(this.layer);
      }
    }
    boolean result = true;
    int minLayer = Math.min(this.layer, toLayer);
    int maxLayer = Math.max(this.layer, toLayer);
    boolean viaFound = false;
    for (int i = 0; i < this.viaRule.viaCount(); i++) {
      ViaInfo currViaInfo = this.viaRule.getVia(i);
      Padstack currViaPadstack = currViaInfo.getPadstack();
      if (minLayer < currViaPadstack.fromLayer() || maxLayer > currViaPadstack.toLayer()) {
        continue;
      }
      // make the current situation restorable by undo
      board.generateSnapshot();
      result =
          board.forcedVia(
              currViaInfo,
              this.prevCorner,
              this.netNoArr,
              clearanceClass,
              penHalfWidthArr,
              maxShoveTraceRecursionDepth,
              0,
              this.traceTidyWidth,
              this.pullTightAccuracy,
              pullTightTimeLimit);
      if (result) {
        viaFound = true;
        break;
      }
      setShoveFailingObstacle(board.getShoveFailingObstacle());
      board.undo(null);
    }
    if (viaFound) {
      this.layer = toLayer;
    }
    return result;
  }

  /** Snaps to the center of an SMD pin on the specified layer when it belongs to this net. */
  private boolean snapToSmdCenter(int layerNo) {
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = board.pickItems(this.prevCorner, layerNo, selectionFilter);
    Pin foundSmdPin = null;
    for (Item currItem : pickedItems) {
      if (currItem instanceof Pin currPin && currItem.sharesNetNo(this.netNoArr)) {
        if (currPin.firstLayer() == layerNo && currPin.lastLayer() == layerNo) {
          foundSmdPin = currPin;
          break;
        }
      }
    }
    if (foundSmdPin == null) {
      return false;
    }
    Point pinCenter = foundSmdPin.getCenter();
    if (!(pinCenter instanceof IntPoint toCorner)) {
      return false;
    }
    if (this.connect(this.prevCorner, toCorner)) {
      this.prevCorner = toCorner;
    }
    return true;
  }

  /**
   * If the starting point is already on a target item, makes a connection to that target.
   *
   * @return true if the connection was completed
   */
  private boolean connectToTarget(IntPoint fromPoint) {
    if (nearestTargetItem != null && targetSet != null && !targetSet.contains(nearestTargetItem)) {
      nearestTargetItem = null;
    }
    if (nearestTargetItem == null || !nearestTargetItem.sharesNetNo(this.netNoArr)) {
      return false;
    }
    boolean routeCompleted = false;
    Point connectionPoint = null;
    if (nearestTargetItem instanceof DrillItem target) {
      connectionPoint = target.getCenter();
    } else if (nearestTargetItem instanceof PolylineTrace trace) {
      return board.connectToTrace(
          fromPoint, trace, this.penHalfWidthArr[layer], this.clearanceClass);
    } else if (nearestTargetItem instanceof ConductionArea) {
      connectionPoint = fromPoint;
    }
    if (connectionPoint instanceof IntPoint point) {
      routeCompleted = connect(fromPoint, point);
    }
    return routeCompleted;
  }

  /**
   * Tries to make a trace connection between two points according to the angle restriction.
   *
   * @return true if the connection succeeded
   */
  private boolean connect(Point fromPoint, IntPoint toPoint) {
    Point[] corners = angledConnection(fromPoint, toPoint);
    boolean connectionSucceeded = true;
    for (int i = 1; i < corners.length; i++) {
      Point fromCorner = corners[i - 1];
      Point toCorner = corners[i];
      TimeLimit timeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
      while (!fromCorner.equals(toCorner)) {
        Point currOkPoint =
            board.insertForcedTraceSegment(
                fromCorner,
                toCorner,
                penHalfWidthArr[layer],
                this.layer,
                netNoArr,
                clearanceClass,
                maxShoveTraceRecursionDepth,
                maxShoveViaRecursionDepth,
                maxSpringOverRecursionDepth,
                traceTidyWidth,
                pullTightAccuracy,
                !isStitchMode,
                timeLimit);
        if (currOkPoint == null) {
          // database may be damaged, restore previous situation
          board.undo(null);
          return true;
        }
        if (currOkPoint.equals(fromCorner) && this.withNeckdown) {
          currOkPoint = tryNeckdownAtEnd(fromCorner, toCorner);
        }
        if (currOkPoint.equals(fromCorner)) {
          this.prevCorner = fromCorner;
          connectionSucceeded = false;
          break;
        }
        fromCorner = currOkPoint;
      }
    }
    return connectionSucceeded;
  }

  /** Calculates the nearest layer of the nearest target item to this.layer. */
  public int nearestTargetLayer() {
    if (nearestTargetItem == null) {
      return this.layer;
    }
    int result;
    int firstLayer = nearestTargetItem.firstLayer();
    int lastLayer = nearestTargetItem.lastLayer();
    if (this.layer < firstLayer) {
      result = firstLayer;
    } else {
      result = Math.min(this.layer, lastLayer);
    }
    return result;
  }

  /** Returns all pins, which can be reached by a pin swap from a start or target pin. */
  private Set<SwapPinInfo> calculateSwapPinInfos() {
    Set<SwapPinInfo> result = new TreeSet<>();
    if (this.targetSet == null) {
      return result;
    }
    for (Item currItem : this.targetSet) {
      if (currItem instanceof Pin pin) {
        Collection<Pin> currSwappablePins = pin.getSwappablePins();
        for (Pin currSwappablePin : currSwappablePins) {
          result.add(new SwapPinInfo(currSwappablePin));
        }
      }
    }
    // add the from item, if it is a pin
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = board.pickItems(this.prevCorner, this.layer, selectionFilter);
    for (Item currItem : pickedItems) {
      if (currItem instanceof Pin pin) {
        Collection<Pin> currSwappablePins = pin.getSwappablePins();
        for (Pin currSwappablePin : currSwappablePins) {
          result.add(new SwapPinInfo(currSwappablePin));
        }
      }
    }
    return result;
  }

  /** Highlights the targets and draws the incomplete. */
  public void draw(Graphics graphics, GraphicsContext graphicsContext) {
    if (this.highlightShoveFailingObstacle && this.shoveFailingObstacle != null) {
      BoardRenderer.drawOverlayItem(
          this.shoveFailingObstacle,
          graphics,
          graphicsContext,
          graphicsContext.getViolationsColor(),
          1);
    }
    if (targetSet == null || netNoArr.length < 1) {
      return;
    }
    Net currentNet = board.rules.nets.get(netNoArr[0]);
    if (currentNet == null) {
      return;
    }
    Color highlightColor = graphicsContext.getHighlightColor();
    double highlightColorIntensity = graphicsContext.getHighlightColorIntensity();

    // highlight the swappable pins and their incompletes
    for (SwapPinInfo currInfo : this.swapPinInfos) {
      BoardRenderer.drawOverlayItem(
          currInfo.pin, graphics, graphicsContext, highlightColor, 0.3 * highlightColorIntensity);
      if (currInfo.incomplete != null) {
        // draw the swap pin incomplete
        FloatPoint[] drawPoints = new FloatPoint[2];
        drawPoints[0] = currInfo.incomplete.a;
        drawPoints[1] = currInfo.incomplete.b;
        Color drawColor = graphicsContext.getIncompleteColor();
        graphicsContext.draw(drawPoints, 1, drawColor, graphics, highlightColorIntensity);
      }
    }

    // highlight the target set
    for (Item currItem : targetSet) {
      if (!(currItem instanceof ConductionArea)) {
        BoardRenderer.drawOverlayItem(
            currItem, graphics, graphicsContext, highlightColor, highlightColorIntensity);
      }
    }
    FloatPoint fromCorner = this.prevCorner.toFloat();
    if (nearestTargetPoint != null && prevCorner != null) {
      boolean currLengthMatchingOk = true; // used for drawing the incomplete as violation
      double maxTraceLength = currentNet.getNetClass().getMaximumTraceLength();
      double minTraceLength = currentNet.getNetClass().getMinimumTraceLength();
      double lengthMatchingColorIntensity = graphicsContext.getLengthMatchingAreaColorIntensity();
      if (maxTraceLength > 0 || minTraceLength > 0 && lengthMatchingColorIntensity > 0) {

        // draw the length matching area
        double traceLengthAdd = fromCorner.distance(this.prevCorner.toFloat());
        // traceLengthAdd is != 0 only in stitching mode.
        if (maxTraceLength <= 0) {
          // maxTraceLength not provided. Create an ellipse containing the whole board.
          maxTraceLength = 0.3 * Limits.CRIT_INT;
        }
        double currMaxTraceLength = maxTraceLength - (currentNet.getTraceLength() + traceLengthAdd);
        double currMinTraceLength = minTraceLength - (currentNet.getTraceLength() + traceLengthAdd);
        double incompleteLength = nearestTargetPoint.distance(fromCorner);
        if (incompleteLength < currMaxTraceLength && minTraceLength <= maxTraceLength) {
          Vector delta = nearestTargetPoint.round().differenceBy(prevCorner);
          double rotation = delta.angleApprox();
          FloatPoint center = fromCorner.middlePoint(nearestTargetPoint);
          double biggerRadius = 0.5 * currMaxTraceLength;
          // dist_focus_to_center^2 = biggerRadius^2 - smallerRadius^2
          double smallerRadius =
              0.5
                  * Math.sqrt(
                      currMaxTraceLength * currMaxTraceLength
                          - incompleteLength * incompleteLength);
          int ellipseCount;
          if (minTraceLength <= 0 || incompleteLength >= currMinTraceLength) {
            ellipseCount = 1;
          } else {
            // display an ellipse ring.
            ellipseCount = 2;
          }
          Ellipse[] ellipseArr = new Ellipse[ellipseCount];
          ellipseArr[0] = new Ellipse(center, rotation, biggerRadius, smallerRadius);
          IntBox boundingBox = new IntBox(prevCorner.toFloat().round(), nearestTargetPoint.round());
          boundingBox = boundingBox.offset(currMaxTraceLength - incompleteLength);
          board.joinGraphicsUpdateBox(boundingBox);
          if (ellipseCount == 2) {
            biggerRadius = 0.5 * currMinTraceLength;
            smallerRadius =
                0.5
                    * Math.sqrt(
                        currMinTraceLength * currMinTraceLength
                            - incompleteLength * incompleteLength);
            ellipseArr[1] = new Ellipse(center, rotation, biggerRadius, smallerRadius);
          }
          graphicsContext.fillEllipseArr(
              ellipseArr,
              graphics,
              graphicsContext.getLengthMatchingAreaColor(),
              lengthMatchingColorIntensity);
        } else {
          currLengthMatchingOk = false;
        }
      }

      // draw the incomplete
      FloatPoint[] drawPoints = new FloatPoint[2];
      drawPoints[0] = fromCorner;
      drawPoints[1] = nearestTargetPoint;
      Color drawColor = graphicsContext.getIncompleteColor();
      double drawWidth =
          Math.min(this.board.communication.getResolution(Unit.MIL), 100); // problem with low
      // resolution on Kicad
      if (!currLengthMatchingOk) {
        drawColor = graphicsContext.getViolationsColor();
        drawWidth *= 3;
      }
      graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, highlightColorIntensity);
      if (this.nearestTargetItem != null && !this.nearestTargetItem.isOnLayer(this.layer)) {
        // draw a marker to indicate the layer change.
        NetIncompletesGraphics.drawLayerChangeMarker(
            drawPoints[0], 4 * penHalfWidthArr[0], graphics, graphicsContext);
      }
    }
  }

  /** Makes a connection polygon whose lines fulfill the angle restriction. */
  private Point[] angledConnection(Point fromPoint, Point toPoint) {
    IntPoint addCorner = null;
    if (fromPoint instanceof IntPoint point && toPoint instanceof IntPoint point1) {
      AngleRestriction angleRestriction = this.board.rules.getTraceAngleRestriction();
      if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
        addCorner = point.ninetyDegreeCorner(point1, true);
      } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
        addCorner = point.fortyfiveDegreeCorner(point1, true);
      }
    }
    int newCornerCount = 2;
    if (addCorner != null) {
      ++newCornerCount;
    }
    Point[] result = new Point[newCornerCount];
    result[0] = fromPoint;
    if (addCorner != null) {
      result[1] = addCorner;
    }
    result[result.length - 1] = toPoint;
    return result;
  }

  /** Calculates target points for drill items and target traces or conduction areas. */
  private void calculateTargetPointsAndAreas() {
    targetPoints = new LinkedList<>();
    targetTracesAndAreas = new LinkedList<>();
    if (targetSet == null) {
      return;
    }
    for (Item currOb : targetSet) {
      if (currOb instanceof DrillItem item) {
        Point currPoint = item.getCenter();
        targetPoints.add(new TargetPoint(currPoint.toFloat(), currOb));
      } else if (currOb instanceof Trace || currOb instanceof ConductionArea) {
        targetTracesAndAreas.add(currOb);
      }
    }
  }

  /** Returns the last successfully inserted corner of the route. */
  public Point getLastCorner() {
    return prevCorner;
  }

  /** Returns whether routing is enabled on the specified layer. */
  public boolean isLayerActive(int layerNo) {
    if (layerNo < 0 || layerNo >= layerActive.length) {
      return false;
    }
    return layerActive[layerNo];
  }

  /** Calculates the nearest target point used to draw the incomplete connection. */
  void calcNearestTargetPoint(FloatPoint fromPoint) {
    double minDist = Double.MAX_VALUE;
    FloatPoint nearestPoint = null;
    Item nearestItem = null;
    for (TargetPoint currTargetPoint : targetPoints) {
      double currDist = fromPoint.distance(currTargetPoint.location);
      if (currDist < minDist) {
        minDist = currDist;
        nearestPoint = currTargetPoint.location;
        nearestItem = currTargetPoint.item;
      }
    }
    for (Item currItem : targetTracesAndAreas) {
      if (currItem instanceof PolylineTrace currTrace) {
        Polyline currPolyline = currTrace.polyline();
        if (currPolyline.boundingBox().distance(fromPoint) < minDist) {
          FloatPoint currNearestPoint = currPolyline.nearestPointApprox(fromPoint);
          double currDist = fromPoint.distance(currNearestPoint);
          if (currDist < minDist) {
            minDist = currDist;
            nearestPoint = currNearestPoint;
            nearestItem = currTrace;
          }
        }
      } else if (currItem instanceof ConductionArea currConductionArea
          && currItem.tileShapeCount() > 0) {
        Area currArea = currConductionArea.getArea();
        if (currArea.boundingBox().distance(fromPoint) < minDist) {
          FloatPoint currNearestPoint = currArea.nearestPointApprox(fromPoint);
          double currDist = fromPoint.distance(currNearestPoint);
          if (currDist < minDist) {
            minDist = currDist;
            nearestPoint = currNearestPoint;
            nearestItem = currConductionArea;
          }
        }
      }
    }
    if (nearestPoint == null) {
      return; // target set is empty
    }
    nearestTargetPoint = nearestPoint;
    nearestTargetItem = nearestItem;
    // join the graphics update box by the nearest item, so that the incomplete
    // is completely displayed.
    board.joinGraphicsUpdateBox(nearestItem.boundingBox());
  }

  private void setShoveFailingObstacle(Item item) {
    this.shoveFailingObstacle = item;
    if (item != null) {
      this.board.joinGraphicsUpdateBox(item.boundingBox());
    }
  }

  /**
   * Tries a smaller trace width when the route starts at a nearby pin.
   *
   * @return the successful point, or the previous corner if the attempt failed
   */
  private Point tryNeckdownAtStart(IntPoint toCorner) {
    if (!(this.startItem instanceof Pin startPin)) {
      return this.prevCorner;
    }
    if (!startPin.isOnLayer(this.layer)) {
      return this.prevCorner;
    }
    FloatPoint pinCenter = startPin.getCenter().toFloat();
    double currClearance =
        this.board.rules.clearanceMatrix.getValue(
            this.clearanceClass, startPin.clearanceClassNo(), this.layer, true);
    double pinNeckDownDistance = 2 * (0.5 * startPin.getMaxWidth(this.layer) + currClearance);
    if (pinCenter.distance(this.prevCorner.toFloat()) >= pinNeckDownDistance) {
      return this.prevCorner;
    }

    int neckDownHalfwidth = startPin.getTraceNeckdownHalfwidth(this.layer);
    if (neckDownHalfwidth >= this.penHalfWidthArr[this.layer]) {
      return this.prevCorner;
    }

    // check, that the neck_down started inside the pin shape
    if (!this.prevCorner.equals(startPin.getCenter())) {
      Item pickedItem = this.board.pickNearestRoutingItem(this.prevCorner, this.layer, null);
      if (pickedItem instanceof Trace trace) {
        if (trace.getHalfWidth() > neckDownHalfwidth) {
          return this.prevCorner;
        }
      }
    }
    TimeLimit timeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
    return board.insertForcedTraceSegment(
        prevCorner,
        toCorner,
        neckDownHalfwidth,
        layer,
        netNoArr,
        clearanceClass,
        maxShoveTraceRecursionDepth,
        maxShoveViaRecursionDepth,
        maxSpringOverRecursionDepth,
        traceTidyWidth,
        pullTightAccuracy,
        !isStitchMode,
        timeLimit);
  }

  /**
   * Tries a smaller trace width when the route ends at a nearby pin.
   *
   * @return the successful point, or {@code fromCorner} if the attempt failed
   */
  private Point tryNeckdownAtEnd(Point fromCorner, Point toCorner) {
    if (!(this.nearestTargetItem instanceof Pin targetPin)) {
      return fromCorner;
    }
    if (!targetPin.isOnLayer(this.layer)) {
      return fromCorner;
    }
    FloatPoint pinCenter = targetPin.getCenter().toFloat();
    double currClearance =
        this.board.rules.clearanceMatrix.getValue(
            this.clearanceClass, targetPin.clearanceClassNo(), this.layer, true);
    double pinNeckDownDistance = 2 * (0.5 * targetPin.getMaxWidth(this.layer) + currClearance);
    if (pinCenter.distance(fromCorner.toFloat()) >= pinNeckDownDistance) {
      return fromCorner;
    }
    int neckDownHalfwidth = targetPin.getTraceNeckdownHalfwidth(this.layer);
    if (neckDownHalfwidth >= this.penHalfWidthArr[this.layer]) {
      return fromCorner;
    }
    TimeLimit timeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
    return board.insertForcedTraceSegment(
        fromCorner,
        toCorner,
        neckDownHalfwidth,
        layer,
        netNoArr,
        clearanceClass,
        maxShoveTraceRecursionDepth,
        maxShoveViaRecursionDepth,
        maxSpringOverRecursionDepth,
        traceTidyWidth,
        pullTightAccuracy,
        !isStitchMode,
        timeLimit);
  }

  private static class TargetPoint {

    final FloatPoint location;
    final Item item;

    TargetPoint(FloatPoint locationValue, Item itemValue) {
      location = locationValue;
      item = itemValue;
    }
  }

  private class SwapPinInfo implements Comparable<SwapPinInfo> {

    final Pin pin;
    FloatLine incomplete;

    SwapPinInfo(Pin pinValue) {
      pin = pinValue;
      incomplete = null;
      if (pinValue.isConnected() || pinValue.netCount() != 1) {
        return;
      }
      // calculate the incomplete of p_pin
      FloatPoint pinCenter = pinValue.getCenter().toFloat();
      double minDist = Double.MAX_VALUE;
      FloatPoint nearestPoint = null;
      Collection<Item> netItems = board.getConnectableItems(pinValue.getNetNo(0));
      for (Item currItem : netItems) {
        if (currItem == this.pin || !(currItem instanceof DrillItem)) {
          continue;
        }
        FloatPoint currPoint = ((DrillItem) currItem).getCenter().toFloat();
        double currDist = pinCenter.distanceSquare(currPoint);
        if (currDist < minDist) {
          minDist = currDist;
          nearestPoint = currPoint;
        }
      }
      if (nearestPoint != null) {
        incomplete = new FloatLine(pinCenter, nearestPoint);
      }
    }

    @Override
    public int compareTo(SwapPinInfo other) {
      return this.pin.compareTo(other.pin);
    }
  }
}

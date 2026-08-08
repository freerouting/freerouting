package app.freerouting.interactive;

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
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.boardgraphics.NetIncompletesGraphics;
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

  /** The time limit in milliseconds for the pull tight algorithm */
  private static final int CHECK_FORCED_TRACE_TIME_LIMIT = 3000;

  /** The time limit in milliseconds for the pull tight algorithm */
  private static final int PULL_TIGHT_TIME_LIMIT = 2000;

  /** The net numbers used for routing */
  final int[] netNoArr;

  private final Item startItem;
  private final Set<Item> targetSet;

  /** Pins, which can be reached by a pin swap by a target pin. */
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
  private final boolean hilightShoveFailingObstacle;
  private final int pullTightTimeLimit;
  private Point prevCorner;
  private int layer;
  private Collection<TargetPoint> targetPoints; // from drill_items
  private Collection<Item> targetTracesAndAreas; // from traces and conduction areas
  private FloatPoint nearestTargetPoint;
  private Item nearestTargetItem;
  private Item shoveFailingObstacle;

  /**
   * Starts routing a connection. p_pen_half_width_arr is provided because it may be different from
   * the half width array in p_board.rules.
   */
  public Route(
      Point pStartCorner,
      int pLayer,
      int[] pPenHalfWidthArr,
      boolean[] pLayerActiveArr,
      int[] pNetNoArr,
      int pClearanceClass,
      ViaRule pViaRule,
      boolean pPushEnabled,
      int pTraceTidyWidth,
      int pPullTightAccuracy,
      Item pStartItem,
      Set<Item> pTargetSet,
      RoutingBoard pBoard,
      boolean pIsStitchMode,
      boolean pWithNeckdown,
      boolean pViaSnapToSmdCenter,
      boolean pHilightShoveFailingObstacle) {
    board = pBoard;
    layer = pLayer;
    if (pPushEnabled) {
      maxShoveTraceRecursionDepth = 20;
      maxShoveViaRecursionDepth = 8;
      maxSpringOverRecursionDepth = 5;
    } else {
      maxShoveTraceRecursionDepth = 0;
      maxShoveViaRecursionDepth = 0;
      maxSpringOverRecursionDepth = 0;
    }
    traceTidyWidth = pTraceTidyWidth;
    pullTightAccuracy = pPullTightAccuracy;
    prevCorner = pStartCorner;
    netNoArr = pNetNoArr;
    penHalfWidthArr = pPenHalfWidthArr;
    layerActive = pLayerActiveArr;
    clearanceClass = pClearanceClass;
    viaRule = pViaRule;
    startItem = pStartItem;
    targetSet = pTargetSet;
    isStitchMode = pIsStitchMode;
    withNeckdown = pWithNeckdown;
    viaSnapToSmdCenter = pViaSnapToSmdCenter;
    hilightShoveFailingObstacle = pHilightShoveFailingObstacle;
    pullTightTimeLimit = PULL_TIGHT_TIME_LIMIT;

    calculateTargetPointsAndAreas();
    swapPinInfos = calculateSwapPinInfos();
  }

  /**
   * Append a line to the trace routed so far. Return true, if the route is completed by connecting
   * to a target.
   */
  public boolean nextCorner(FloatPoint pCorner) {
    if (!this.layerActive[this.layer]) {
      return false;
    }
    IntPoint currCorner = pCorner.round();
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
   * Changing the layer in interactive route and inserting a via. Returns false, if changing the
   * layer was not possible.
   */
  public boolean changeLayer(int pToLayer) {
    if (this.layer == pToLayer) {
      return true;
    }
    if (pToLayer < 0 || pToLayer >= this.layerActive.length) {
      FRLogger.warn("Route.change_layer: p_to_layer out of range");
      return false;
    }
    if (!this.layerActive[pToLayer]) {
      return false;
    }
    if (this.viaRule == null) {
      return false;
    }
    this.shoveFailingObstacle = null;
    if (this.viaSnapToSmdCenter) {
      boolean snappedToSmdCenter = snapToSmdCenter(pToLayer);
      if (!snappedToSmdCenter) {
        snapToSmdCenter(this.layer);
      }
    }
    boolean result = true;
    int minLayer = Math.min(this.layer, pToLayer);
    int maxLayer = Math.max(this.layer, pToLayer);
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
      this.layer = pToLayer;
    }
    return result;
  }

  /**
   * Snaps to the center of a smd pin, if the location on p_layer is inside a smd pin of the own
   * net,
   */
  private boolean snapToSmdCenter(int pLayer) {
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = board.pickItems(this.prevCorner, pLayer, selectionFilter);
    Pin foundSmdPin = null;
    for (Item currItem : pickedItems) {
      if (currItem instanceof Pin currPin && currItem.sharesNetNo(this.netNoArr)) {
        if (currPin.firstLayer() == pLayer && currPin.lastLayer() == pLayer) {
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
   * If p_from_point is already on a target item, a connection to the target is made and true
   * returned.
   */
  private boolean connectToTarget(IntPoint pFromPoint) {
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
          pFromPoint, trace, this.penHalfWidthArr[layer], this.clearanceClass);
    } else if (nearestTargetItem instanceof ConductionArea) {
      connectionPoint = pFromPoint;
    }
    if (connectionPoint instanceof IntPoint point) {
      routeCompleted = connect(pFromPoint, point);
    }
    return routeCompleted;
  }

  /**
   * Tries to make a trace connection from p_from_point to p_to_point according to the angle
   * restriction. Returns true, if the connection succeeded.
   */
  private boolean connect(Point pFromPoint, IntPoint pToPoint) {
    Point[] corners = angledConnection(pFromPoint, pToPoint);
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
        for (Pin curr_swappable_pin : currSwappablePins) {
          result.add(new SwapPinInfo(curr_swappable_pin));
        }
      }
    }
    return result;
  }

  /** Highlights the targets and draws the incomplete. */
  public void draw(Graphics pGraphics, GraphicsContext pGraphicsContext) {
    if (this.hilightShoveFailingObstacle && this.shoveFailingObstacle != null) {
      this.shoveFailingObstacle.draw(
          pGraphics, pGraphicsContext, pGraphicsContext.getViolationsColor(), 1);
    }
    if (targetSet == null || netNoArr.length < 1) {
      return;
    }
    Net currNet = board.rules.nets.get(netNoArr[0]);
    if (currNet == null) {
      return;
    }
    Color highlightColor = pGraphicsContext.getHilightColor();
    double highligtColorIntensity = pGraphicsContext.getHilightColorIntensity();

    // hilight the swappable pins and their incompletes
    for (SwapPinInfo currInfo : this.swapPinInfos) {
      currInfo.pin.draw(pGraphics, pGraphicsContext, highlightColor, 0.3 * highligtColorIntensity);
      if (currInfo.incomplete != null) {
        // draw the swap pin incomplete
        FloatPoint[] drawPoints = new FloatPoint[2];
        drawPoints[0] = currInfo.incomplete.a;
        drawPoints[1] = currInfo.incomplete.b;
        Color drawColor = pGraphicsContext.getIncompleteColor();
        pGraphicsContext.draw(drawPoints, 1, drawColor, pGraphics, highligtColorIntensity);
      }
    }

    // hilight the target set
    for (Item currItem : targetSet) {
      if (!(currItem instanceof ConductionArea)) {
        currItem.draw(pGraphics, pGraphicsContext, highlightColor, highligtColorIntensity);
      }
    }
    FloatPoint fromCorner = this.prevCorner.toFloat();
    if (nearestTargetPoint != null && prevCorner != null) {
      boolean currLengthMatchingOk = true; // used for drawing the incomplete as violation
      double maxTraceLength = currNet.getNetClass().getMaximumTraceLength();
      double minTraceLength = currNet.getNetClass().getMinimumTraceLength();
      double lengthMatchingColorIntensity = pGraphicsContext.getLengthMatchingAreaColorIntensity();
      if (maxTraceLength > 0 || minTraceLength > 0 && lengthMatchingColorIntensity > 0) {

        // draw the length matching area
        double traceLengthAdd = fromCorner.distance(this.prevCorner.toFloat());
        // traceLengthAdd is != 0 only in stitching mode.
        if (maxTraceLength <= 0) {
          // maxTraceLength not provided. Create an ellipse containing the whole board.
          maxTraceLength = 0.3 * Limits.CRIT_INT;
        }
        double currMaxTraceLength = maxTraceLength - (currNet.getTraceLength() + traceLengthAdd);
        double currMinTraceLength = minTraceLength - (currNet.getTraceLength() + traceLengthAdd);
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
          pGraphicsContext.fillEllipseArr(
              ellipseArr,
              pGraphics,
              pGraphicsContext.getLengthMatchingAreaColor(),
              lengthMatchingColorIntensity);
        } else {
          currLengthMatchingOk = false;
        }
      }

      // draw the incomplete
      FloatPoint[] drawPoints = new FloatPoint[2];
      drawPoints[0] = fromCorner;
      drawPoints[1] = nearestTargetPoint;
      Color drawColor = pGraphicsContext.getIncompleteColor();
      double drawWidth =
          Math.min(this.board.communication.getResolution(Unit.MIL), 100); // problem with low
      // resolution on Kicad
      if (!currLengthMatchingOk) {
        drawColor = pGraphicsContext.getViolationsColor();
        drawWidth *= 3;
      }
      pGraphicsContext.draw(drawPoints, drawWidth, drawColor, pGraphics, highligtColorIntensity);
      if (this.nearestTargetItem != null && !this.nearestTargetItem.isOnLayer(this.layer)) {
        // draw a marker to indicate the layer change.
        NetIncompletesGraphics.drawLayerChangeMarker(
            drawPoints[0], 4 * penHalfWidthArr[0], pGraphics, pGraphicsContext);
      }
    }
  }

  /**
   * Makes a connection polygon from p_from_point to p_to_point whose lines fulfill the angle
   * restriction.
   */
  private Point[] angledConnection(Point pFromPoint, Point pToPoint) {
    IntPoint addCorner = null;
    if (pFromPoint instanceof IntPoint point && pToPoint instanceof IntPoint point1) {
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
    result[0] = pFromPoint;
    if (addCorner != null) {
      result[1] = addCorner;
    }
    result[result.length - 1] = pToPoint;
    return result;
  }

  /**
   * Calculates a list of the center points of DrillItems, end points of traces and areas of
   * ConductionAreas in the target set.
   */
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

  public Point getLastCorner() {
    return prevCorner;
  }

  public boolean isLayerActive(int pLayer) {
    if (pLayer < 0 || pLayer >= layerActive.length) {
      return false;
    }
    return layerActive[pLayer];
  }

  /** The nearest point is used for drawing the incomplete */
  void calcNearestTargetPoint(FloatPoint pFromPoint) {
    double minDist = Double.MAX_VALUE;
    FloatPoint nearestPoint = null;
    Item nearestItem = null;
    for (TargetPoint currTargetPoint : targetPoints) {
      double currDist = pFromPoint.distance(currTargetPoint.location);
      if (currDist < minDist) {
        minDist = currDist;
        nearestPoint = currTargetPoint.location;
        nearestItem = currTargetPoint.item;
      }
    }
    for (Item currItem : targetTracesAndAreas) {
      if (currItem instanceof PolylineTrace currTrace) {
        Polyline currPolyline = currTrace.polyline();
        if (currPolyline.boundingBox().distance(pFromPoint) < minDist) {
          FloatPoint currNearestPoint = currPolyline.nearestPointApprox(pFromPoint);
          double currDist = pFromPoint.distance(currNearestPoint);
          if (currDist < minDist) {
            minDist = currDist;
            nearestPoint = currNearestPoint;
            nearestItem = currTrace;
          }
        }
      } else if (currItem instanceof ConductionArea curr_conduction_area
          && currItem.tileShapeCount() > 0) {
        Area currArea = curr_conduction_area.getArea();
        if (currArea.boundingBox().distance(pFromPoint) < minDist) {
          FloatPoint currNearestPoint = currArea.nearestPointApprox(pFromPoint);
          double currDist = pFromPoint.distance(currNearestPoint);
          if (currDist < minDist) {
            minDist = currDist;
            nearestPoint = currNearestPoint;
            nearestItem = curr_conduction_area;
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

  private void setShoveFailingObstacle(Item pItem) {
    this.shoveFailingObstacle = pItem;
    if (pItem != null) {
      this.board.joinGraphicsUpdateBox(pItem.boundingBox());
    }
  }

  /**
   * If the routed starts at a pin and the route failed with the normal trace width, another try
   * with the smallest pin width is done. Returns the okPoint of the try, which is this.prevPoint,
   * if the try failed.
   */
  private Point tryNeckdownAtStart(IntPoint pToCorner) {
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
        pToCorner,
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
   * If the routed ends at a pin and the route failed with the normal trace width, another try with
   * the smallest pin width is done. Returns the okPoint of the try, which is p_from_corner, if the
   * try failed.
   */
  private Point tryNeckdownAtEnd(Point pFromCorner, Point pToCorner) {
    if (!(this.nearestTargetItem instanceof Pin target_pin)) {
      return pFromCorner;
    }
    if (!target_pin.isOnLayer(this.layer)) {
      return pFromCorner;
    }
    FloatPoint pinCenter = target_pin.getCenter().toFloat();
    double currClearance =
        this.board.rules.clearanceMatrix.getValue(
            this.clearanceClass, target_pin.clearanceClassNo(), this.layer, true);
    double pinNeckDownDistance = 2 * (0.5 * target_pin.getMaxWidth(this.layer) + currClearance);
    if (pinCenter.distance(pFromCorner.toFloat()) >= pinNeckDownDistance) {
      return pFromCorner;
    }
    int neckDownHalfwidth = target_pin.getTraceNeckdownHalfwidth(this.layer);
    if (neckDownHalfwidth >= this.penHalfWidthArr[this.layer]) {
      return pFromCorner;
    }
    TimeLimit timeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
    return board.insertForcedTraceSegment(
        pFromCorner,
        pToCorner,
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

    TargetPoint(FloatPoint pLocation, Item pItem) {
      location = pLocation;
      item = pItem;
    }
  }

  private class SwapPinInfo implements Comparable<SwapPinInfo> {

    final Pin pin;
    FloatLine incomplete;

    SwapPinInfo(Pin pPin) {
      pin = pPin;
      incomplete = null;
      if (pPin.isConnected() || pPin.netCount() != 1) {
        return;
      }
      // calculate the incomplete of p_pin
      FloatPoint pinCenter = pPin.getCenter().toFloat();
      double minDist = Double.MAX_VALUE;
      FloatPoint nearestPoint = null;
      Collection<Item> netItems = board.getConnectableItems(pPin.getNetNo(0));
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
    public int compareTo(SwapPinInfo pOther) {
      return this.pin.compareTo(pOther.pin);
    }
  }
}

package app.freerouting.board.optimize;

import app.freerouting.autoroute.maze.AutorouteControl.ExpansionCostFactor;
import app.freerouting.board.AngleRestriction;
import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.board.searchtree.SearchTreeObject;
import app.freerouting.board.searchtree.ShapeSearchTree;
import app.freerouting.datastructures.Signum;
import app.freerouting.datastructures.Stoppable;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Side;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Set;

/** Class with functionality for optimising traces and vias. */
public abstract class TraceTightener {

  protected static final double c_max_cos_angle = 0.999;
  // with angles to close to 180 degree the algorithm becomes numerically
  // unstable
  protected static final double c_min_corner_dist_square = 0.9;
  protected final RoutingBoard board;

  /** If only_net_no {@literal >} 0, only nets with this net numbers are optimized. */
  public final int[] onlyNetNoArr;

  /** If stoppableThread != null, the algorithm can be requested to be stopped. */
  private final Stoppable stoppableThread;

  private final TimeLimit timeLimit;

  /**
   * If keepPoint != null, traces containing the keepPoint must also contain the keepPoint after
   * optimizing.
   */
  private final Point keepPoint;

  private final int keepPointLayer;
  protected int currentLayer;
  protected int currentHalfWidth;
  protected int[] currentNetNumbers;
  protected int currentClearanceClassIndex;
  protected IntOctagon currentClipShape;
  protected Set<Pin> contactPins;
  protected int minTranslateDist;

  /** Creates a new instance of TraceTightener. */
  TraceTightener(
      RoutingBoard board,
      int[] onlyNetNoArr,
      Stoppable stoppableThread,
      int timeLimit,
      Point keepPoint,
      int keepPointLayer) {
    this.board = board;
    this.onlyNetNoArr = onlyNetNoArr;
    this.stoppableThread = stoppableThread;
    if (timeLimit > 0) {
      this.timeLimit = new TimeLimit(timeLimit);
    } else {
      this.timeLimit = null;
    }
    this.keepPoint = keepPoint;
    this.keepPointLayer = keepPointLayer;
  }

  /**
   * Returns a new instance of TraceTightener. If onlyNetNo > 0, only traces with net number notNo
   * are optimized. If stoppableThread != null, the algorithm can be requested to be stopped. If
   * timeLimit > 0; the algorithm will be stopped after timeLimit Milliseconds.
   */
  public static TraceTightener getInstance(
      RoutingBoard board,
      int[] onlyNetNoArr,
      IntOctagon clipShape,
      int minTranslateDist,
      Stoppable stoppableThread,
      int timeLimit,
      Point keepPoint,
      int keepPointLayer) {
    TraceTightener result;
    AngleRestriction angleRestriction = board.rules.getTraceAngleRestriction();
    if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
      result =
          new TraceTightener90(
              board, onlyNetNoArr, stoppableThread, timeLimit, keepPoint, keepPointLayer);
    } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result =
          new TraceTightener45(
              board, onlyNetNoArr, stoppableThread, timeLimit, keepPoint, keepPointLayer);
    } else {
      result =
          new TraceTightenerAnyAngle(
              board, onlyNetNoArr, stoppableThread, timeLimit, keepPoint, keepPointLayer);
    }
    result.currentClipShape = clipShape;
    result.minTranslateDist = Math.max(minTranslateDist, 100);
    return result;
  }

  /**
   * Function for optimizing the route in an internal marked area. If clipShape != null, the
   * optimizing area is restricted to clipShape. traceCosts is used for optimizing vias and may be
   * null.
   */
  public void optChangedArea(ExpansionCostFactor[] traceCosts) {
    if (board.changedArea == null) {
      return;
    }
    boolean somethingChanged = true;
    // starting with curr_min_translate_dist big is a try to
    // avoid fine approximation at the beginning to avoid
    // problems with dog ears
    while (somethingChanged) {
      somethingChanged = false;
      for (int i = 0; i < board.getLayerCount(); i++) {
        IntOctagon changedRegion = board.changedArea.getArea(i);
        if (changedRegion.isEmpty()) {
          continue;
        }
        board.changedArea.setEmpty(i);
        board.joinGraphicsUpdateBox(changedRegion.boundingBox());
        double changedAreaOffset =
            1.5
                * (board.rules.clearanceMatrix.maxValue(i)
                    + 2 * board.rules.getMaxTraceHalfWidth());
        changedRegion = changedRegion.enlarge(changedAreaOffset);
        // search in the ShapeSearchTree for all overlapping traces
        // with clipShape on layer i
        Collection<SearchTreeObject> items = board.overlappingObjects(changedRegion, i);
        for (SearchTreeObject currentObject : items) {
          if (this.isStopRequested()) {
            return;
          }
          if (currentObject instanceof PolylineTrace currentTrace) {
            if (currentTrace.pullTight(this)) {
              somethingChanged = true;
              if (this.splitTracesAtKeepPoint()) {
                break;
              }
            } else if (smoothenEndCornersAtTrace(currentTrace)) {
              somethingChanged = true;
              break; // because items may be removed
            }
          } else if (currentObject instanceof Via via && traceCosts != null) {
            if (ViaOptimizer.optViaLocation(
                this.board, via, traceCosts, this.minTranslateDist, 10)) {
              somethingChanged = true;
            }
          }
        }
      }
    }
  }

  /**
   * Function for optimizing a single trace polygon contactPins are the pins at the end corners of
   * polyline. Other pins are regarded as obstacles, even if they are of the own net.
   */
  public Polyline pullTight(
      Polyline polyline,
      int layer,
      int halfWidth,
      int[] netNumbers,
      int clearanceClassIndex,
      Set<Pin> contactPins) {
    currentLayer = layer;
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    currentHalfWidth =
        halfWidth + searchTree.clearanceCompensationValue(clearanceClassIndex, layer);
    currentNetNumbers = netNumbers;
    currentClearanceClassIndex = clearanceClassIndex;
    this.contactPins = contactPins;
    return pullTight(polyline);
  }

  public abstract Polyline pullTight(Polyline polyline);

  /** Terminates the pull tight algorithm, if the user has made a stop request. */
  protected boolean isStopRequested() {
    if (this.stoppableThread != null && this.stoppableThread.isStopRequested()) {
      return true;
    }
    if (this.timeLimit == null) {
      return false;
    }
    boolean timeLimitExceeded = this.timeLimit.limitExceeded();
    if (timeLimitExceeded) {

      if (this.board == null) {
        FRLogger.error("TraceTightener.is_stop_requested: board is null", null);
      }

      FRLogger.debug("TraceTightener.is_stop_requested: time limit exceeded");
    }
    return timeLimitExceeded;
  }

  /** Tries to shorten polyline by relocating its lines. */
  Polyline repositionLines(Polyline polyline) {
    if (polyline.lines.length < 5) {
      return polyline;
    }
    for (int i = 2; i < polyline.lines.length - 2; i++) {
      Line newLine = repositionLine(polyline.lines, i);
      if (newLine != null) {
        Line[] lines = new Line[polyline.lines.length];
        System.arraycopy(polyline.lines, 0, lines, 0, lines.length);
        lines[i] = newLine;
        Polyline result = new Polyline(lines);
        return skipSegmentsOfLength0(result);
      }
    }
    return polyline;
  }

  /**
   * Tries to reposition the line with index no to make the polyline consisting of lines shorter.
   */
  protected Line repositionLine(Line[] lines, int no) {
    if (lines.length - no < 3) {
      return null;
    }
    if (currentClipShape != null) {
      // check, that the corners of the line to translate are inside the clip shape
      for (int i = -1; i < 1; i++) {
        Point currentCorner = lines[no + i].intersection(lines[no + i + 1]);
        if (currentClipShape.isOutside(currentCorner)) {
          return null;
        }
      }
    }
    Line translateLine = lines[no];
    Point prevCorner = lines[no - 2].intersection(lines[no - 1]);
    Point nextCorner = lines[no + 1].intersection(lines[no + 2]);
    double prevDist = translateLine.signedDistance(prevCorner.toFloat());
    double nextDist = translateLine.signedDistance(nextCorner.toFloat());
    if (Signum.of(prevDist) != Signum.of(nextDist)) {
      // the 2 corners are at different sides of translateLine
      return null;
    }
    Point nearestPoint;
    double maxTranslateDist;
    if (Math.abs(prevDist) < Math.abs(nextDist)) {
      nearestPoint = prevCorner;
      maxTranslateDist = prevDist;
    } else {
      nearestPoint = nextCorner;
      maxTranslateDist = nextDist;
    }
    double translateDist = maxTranslateDist;
    double deltaDist = maxTranslateDist;
    Side sideOfNearestPoint = translateLine.sideOf(nearestPoint);
    int sign = Signum.asInt(maxTranslateDist);
    Line newLine = null;
    Line[] checkLines = new Line[3];
    checkLines[0] = lines[no - 1];
    checkLines[2] = lines[no + 1];
    boolean firstTime = true;
    while (firstTime || Math.abs(deltaDist) > minTranslateDist) {
      if (firstTime && nearestPoint instanceof IntPoint) {
        checkLines[1] = Line.getInstance(nearestPoint, translateLine.direction());
      } else {
        checkLines[1] = translateLine.translate(-translateDist);
      }
      if (checkLines[1].equals(translateLine)) {
        // may happen at first time if nearestPoint is not an IntPoint
        return null;
      }
      Side newLineSideOfNearestPoint = checkLines[1].sideOf(nearestPoint);
      if (newLineSideOfNearestPoint != sideOfNearestPoint
          && newLineSideOfNearestPoint != Side.COLLINEAR) {
        // moved a little bit to far at the first time
        // because of numerical inaccuracy;
        // may happen if nearestPoint is not an IntPoint
        double shortenValue = sign * 0.5;
        maxTranslateDist -= shortenValue;
        translateDist -= shortenValue;
        deltaDist -= shortenValue;
        continue;
      }
      Polyline tmp = new Polyline(checkLines);

      boolean checkOk = false;
      if (tmp.lines.length == 3) {
        TileShape shapeToCheck = tmp.offsetShape(currentHalfWidth, 0);
        checkOk =
            board.checkTraceShape(
                shapeToCheck,
                currentLayer,
                currentNetNumbers,
                currentClearanceClassIndex,
                this.contactPins);
      }
      deltaDist /= 2;
      if (checkOk) {
        newLine = checkLines[1];
        if (firstTime) {
          // biggest possible change
          break;
        }
        translateDist += deltaDist;
      } else {
        translateDist -= deltaDist;
      }
      firstTime = false;
    }
    if (newLine != null && board.changedArea != null) {
      // mark the changed area
      board.changedArea.join(checkLines[0].intersectionApprox(newLine), currentLayer);
      board.changedArea.join(checkLines[2].intersectionApprox(newLine), currentLayer);
      board.changedArea.join(lines[no - 1].intersectionApprox(lines[no]), currentLayer);
      board.changedArea.join(lines[no].intersectionApprox(lines[no + 1]), currentLayer);
    }
    return newLine;
  }

  /**
   * Tries to skip line segments of length 0.
   *
   * <p>A check is necessary before skipping because new dog ears may occur.
   */
  Polyline skipSegmentsOfLength0(Polyline polyline) {
    boolean polylineChanged = false;
    Polyline currentPolyline = polyline;
    for (int i = 1; i < currentPolyline.lines.length - 1; i++) {
      boolean trySkip;
      if (i == 1 || i == currentPolyline.lines.length - 2) {
        // the position of the first corner and the last corner
        //  must be retained exactly
        Point prevCorner = currentPolyline.corner(i - 1);
        Point currentCorner = currentPolyline.corner(i);
        trySkip = currentCorner.equals(prevCorner);
      } else {
        FloatPoint prevCorner = currentPolyline.cornerApprox(i - 1);
        FloatPoint currentCorner = currentPolyline.cornerApprox(i);
        trySkip = currentCorner.distanceSquare(prevCorner) < c_min_corner_dist_square;
      }

      if (trySkip) {
        // check, if skipping the line of length 0 does not
        // result in a clearance violation
        Line[] currentLines = new Line[currentPolyline.lines.length - 1];
        System.arraycopy(currentPolyline.lines, 0, currentLines, 0, i);
        System.arraycopy(currentPolyline.lines, i + 1, currentLines, i, currentLines.length - i);
        Polyline tmp = new Polyline(currentLines);
        boolean checkOk = tmp.lines.length == currentLines.length;
        if (checkOk && !currentPolyline.lines[i].isMultipleOf45Degree()) {
          // no check necessary for skipping 45 degree lines, because the check is
          // performance critical and the line shapes
          // are intersected with the bounding octagon anyway.
          if (i > 1) {
            TileShape shapeToCheck = tmp.offsetShape(currentHalfWidth, i - 2);
            checkOk =
                board.checkTraceShape(
                    shapeToCheck,
                    currentLayer,
                    currentNetNumbers,
                    currentClearanceClassIndex,
                    this.contactPins);
          }
          if (checkOk && (i < currentPolyline.lines.length - 2)) {
            TileShape shapeToCheck = tmp.offsetShape(currentHalfWidth, i - 1);
            checkOk =
                board.checkTraceShape(
                    shapeToCheck,
                    currentLayer,
                    currentNetNumbers,
                    currentClearanceClassIndex,
                    this.contactPins);
          }
        }
        if (checkOk) {
          polylineChanged = true;
          currentPolyline = tmp;
          --i;
        }
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    return currentPolyline;
  }

  /** Smoothens acute angles with contact traces. Returns true, if something was changed. */
  public boolean smoothenEndCornersAtTrace(PolylineTrace trace) {
    if (this.onlyNetNoArr.length > 0 && !trace.netsEqual(this.onlyNetNoArr)) {
      return false;
    }
    currentLayer = trace.getLayer();
    currentHalfWidth = trace.getHalfWidth();
    currentNetNumbers = trace.netNumbers;
    currentClearanceClassIndex = trace.clearanceClassIndex();
    return smoothenEndCornersAtTrace1(trace);
  }

  /** Smoothens acute angles with contact traces. Returns true, if something was changed. */
  private boolean smoothenEndCornersAtTrace1(PolylineTrace trace) {
    // try to improve the connection to other traces
    if (trace.isShoveFixed()) {
      return false;
    }
    Set<Pin> savedContactPins = this.contactPins;
    // to allow the trace to slide to the end point of a contact trace, if the contact trace ends at
    // a pin.
    this.contactPins = null;
    boolean result = false;
    boolean connectionToTraceImproved = true;
    PolylineTrace currentTrace = trace;
    while (connectionToTraceImproved) {
      connectionToTraceImproved = false;
      Polyline adjustedPolyline = smoothenEndCornersAtTrace2(currentTrace);
      if (adjustedPolyline != null) {
        int traceLayer = currentTrace.getLayer();
        int currentClClass = currentTrace.clearanceClassIndex();
        FixedState currentFixedState = currentTrace.getFixedState();
        board.removeItem(currentTrace);
        PolylineTrace adjInsTrace =
            board.insertTraceWithoutCleaning(
                adjustedPolyline,
                traceLayer,
                currentHalfWidth,
                currentTrace.netNumbers,
                currentClClass,
                currentFixedState);
        if (adjInsTrace != null) {
          result = true;
          connectionToTraceImproved = true;
          board.removeItem(currentTrace);
          currentTrace = adjInsTrace;
          for (int currentNetNumber : currentTrace.netNumbers) {
            board.splitTraces(adjustedPolyline.firstCorner(), traceLayer, currentNetNumber);
            board.splitTraces(adjustedPolyline.lastCorner(), traceLayer, currentNetNumber);

            try {
              board.normalizeTraces(currentNetNumber);
            } catch (Exception e) {
              FRLogger.error(
                  "The normalization of net '"
                      + board.rules.nets.get(currentNetNumber).name
                      + "' failed.",
                  e);
            }

            if (splitTracesAtKeepPoint()) {
              return true;
            }
          }
        }
      }
    }
    this.contactPins = savedContactPins;
    return result;
  }

  /**
   * Splits the traces containing this.keepPoint if this.keepPoint != null. Returns true, if
   * something was split.
   */
  public boolean splitTracesAtKeepPoint() {
    if (this.keepPoint == null) {
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems =
        this.board.pickItems(this.keepPoint, this.keepPointLayer, filter);
    for (Item currentItem : pickedItems) {
      Trace[] splitPieces = ((Trace) currentItem).split(this.keepPoint);
      if (splitPieces != null) {
        return true;
      }
    }
    return false;
  }

  /** Smoothens acute angles with contact traces. Returns null, if something was changed. */
  private Polyline smoothenEndCornersAtTrace2(PolylineTrace trace) {
    if (trace == null || !trace.isOnTheBoard()) {
      return null;
    }
    Polyline result = smoothenStartCornerAtTrace(trace);
    if (result == null) {
      result = smoothenEndCornerAtTrace(trace);
      if (result != null && board.changedArea != null) {
        // mark the changed area
        board.changedArea.join(result.cornerApprox(result.cornerCount() - 1), currentLayer);
      }
    } else if (board.changedArea != null) {
      // mark the changed area
      board.changedArea.join(result.cornerApprox(0), currentLayer);
    }
    if (result != null) {
      this.contactPins = trace.touchingPinsAtEndCorners();
      result = skipSegmentsOfLength0(result);
    }
    return result;
  }

  /** Wraps around pins of the own net to avoid acid traps. */
  protected Polyline avoidAcidTraps(Polyline polyline) {
    if (true) {
      return polyline;
    }
    Polyline result = polyline;
    TraceShover shoveTraceAlgo = new TraceShover(this.board);
    Polyline newPolyline =
        shoveTraceAlgo.springOverObstacles(
            polyline,
            currentHalfWidth,
            currentLayer,
            currentNetNumbers,
            currentClearanceClassIndex,
            contactPins);
    if (newPolyline != null && newPolyline != polyline) {
      if (this.board.checkPolylineTrace(
          newPolyline,
          currentLayer,
          currentHalfWidth,
          currentNetNumbers,
          currentClearanceClassIndex)) {
        result = newPolyline;
      }
    }
    return result;
  }

  abstract Polyline smoothenStartCornerAtTrace(PolylineTrace trace);

  abstract Polyline smoothenEndCornerAtTrace(PolylineTrace trace);
}

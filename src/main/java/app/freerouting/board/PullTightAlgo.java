package app.freerouting.board;

import app.freerouting.autoroute.AutorouteControl.ExpansionCostFactor;
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
public abstract class PullTightAlgo {

  protected static final double c_max_cos_angle = 0.999;
  // with angles to close to 180 degree the algorithm becomes numerically
  // unstable
  protected static final double c_min_corner_dist_square = 0.9;
  protected final RoutingBoard board;

  /** If only_net_no {@literal >} 0, only nets with this net numbers are optimized. */
  protected final int[] onlyNetNoArr;

  /** If stoppableThread != null, the algorithm can be requested to be stopped. */
  private final Stoppable stoppableThread;

  private final TimeLimit timeLimit;

  /**
   * If keepPoint != null, traces containing the keepPoint must also contain the keepPoint after
   * optimizing.
   */
  private final Point keepPoint;

  private final int keepPointLayer;
  protected int currLayer;
  protected int currHalfWidth;
  protected int[] currNetNoArr;
  protected int currClType;
  protected IntOctagon currClipShape;
  protected Set<Pin> contactPins;
  protected int minTranslateDist;

  /** Creates a new instance of PullTightAlgo. */
  PullTightAlgo(
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
   * Returns a new instance of PullTightAlgo. If p_only_net_no > 0, only traces with net number
   * p_not_no are optimized. If p_stoppable_thread != null, the algorithm can be requested to be
   * stopped. If p_time_limit > 0; the algorithm will be stopped after p_time_limit Milliseconds.
   */
  static PullTightAlgo getInstance(
      RoutingBoard board,
      int[] onlyNetNoArr,
      IntOctagon clipShape,
      int minTranslateDist,
      Stoppable stoppableThread,
      int timeLimit,
      Point keepPoint,
      int keepPointLayer) {
    PullTightAlgo result;
    AngleRestriction angleRestriction = board.rules.getTraceAngleRestriction();
    if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
      result =
          new PullTightAlgo90(
              board, onlyNetNoArr, stoppableThread, timeLimit, keepPoint, keepPointLayer);
    } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result =
          new PullTightAlgo45(
              board, onlyNetNoArr, stoppableThread, timeLimit, keepPoint, keepPointLayer);
    } else {
      result =
          new PullTightAlgoAnyAngle(
              board, onlyNetNoArr, stoppableThread, timeLimit, keepPoint, keepPointLayer);
    }
    result.currClipShape = clipShape;
    result.minTranslateDist = Math.max(minTranslateDist, 100);
    return result;
  }

  /**
   * Function for optimizing the route in an internal marked area. If p_clip_shape != null, the
   * optimizing area is restricted to p_clip_shape. p_trace_cost_arr is used for optimizing vias and
   * may be null.
   */
  void optChangedArea(ExpansionCostFactor[] traceCostArr) {
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
        for (SearchTreeObject currOb : items) {
          if (this.isStopRequested()) {
            return;
          }
          if (currOb instanceof PolylineTrace currTrace) {
            if (currTrace.pullTight(this)) {
              somethingChanged = true;
              if (this.splitTracesAtKeepPoint()) {
                break;
              }
            } else if (smoothenEndCornersAtTrace(currTrace)) {
              somethingChanged = true;
              break; // because items may be removed
            }
          } else if (currOb instanceof Via via && traceCostArr != null) {
            if (OptViaAlgo.optViaLocation(
                this.board, via, traceCostArr, this.minTranslateDist, 10)) {
              somethingChanged = true;
            }
          }
        }
      }
    }
  }

  /**
   * Function for optimizing a single trace polygon p_contact_pins are the pins at the end corners
   * of p_polyline. Other pins are regarded as obstacles, even if they are of the own net.
   */
  Polyline pullTight(
      Polyline polyline,
      int layer,
      int halfWidth,
      int[] netNoArr,
      int clType,
      Set<Pin> contactPins) {
    currLayer = layer;
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    currHalfWidth = halfWidth + searchTree.clearanceCompensationValue(clType, layer);
    currNetNoArr = netNoArr;
    currClType = clType;
    this.contactPins = contactPins;
    return pullTight(polyline);
  }

  abstract Polyline pullTight(Polyline polyline);

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
        FRLogger.error("PullTightAlgo.is_stop_requested: board is null", null);
      }

      FRLogger.debug("PullTightAlgo.is_stop_requested: time limit exceeded");
    }
    return timeLimitExceeded;
  }

  /** Tries to shorten p_polyline by relocating its lines. */
  Polyline repositionLines(Polyline polyline) {
    if (polyline.arr.length < 5) {
      return polyline;
    }
    for (int i = 2; i < polyline.arr.length - 2; i++) {
      Line newLine = repositionLine(polyline.arr, i);
      if (newLine != null) {
        Line[] lineArr = new Line[polyline.arr.length];
        System.arraycopy(polyline.arr, 0, lineArr, 0, lineArr.length);
        lineArr[i] = newLine;
        Polyline result = new Polyline(lineArr);
        return skipSegmentsOfLength0(result);
      }
    }
    return polyline;
  }

  /**
   * Tries to reposition the line with index p_no to make the polyline consisting of p_line_arr
   * shorter.
   */
  protected Line repositionLine(Line[] lineArr, int no) {
    if (lineArr.length - no < 3) {
      return null;
    }
    if (currClipShape != null) {
      // check, that the corners of the line to translate are inside the clip shape
      for (int i = -1; i < 1; i++) {
        Point currCorner = lineArr[no + i].intersection(lineArr[no + i + 1]);
        if (currClipShape.isOutside(currCorner)) {
          return null;
        }
      }
    }
    Line translateLine = lineArr[no];
    Point prevCorner = lineArr[no - 2].intersection(lineArr[no - 1]);
    Point nextCorner = lineArr[no + 1].intersection(lineArr[no + 2]);
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
    checkLines[0] = lineArr[no - 1];
    checkLines[2] = lineArr[no + 1];
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
      if (tmp.arr.length == 3) {
        TileShape shapeToCheck = tmp.offsetShape(currHalfWidth, 0);
        checkOk =
            board.checkTraceShape(
                shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
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
      board.changedArea.join(checkLines[0].intersectionApprox(newLine), currLayer);
      board.changedArea.join(checkLines[2].intersectionApprox(newLine), currLayer);
      board.changedArea.join(lineArr[no - 1].intersectionApprox(lineArr[no]), currLayer);
      board.changedArea.join(lineArr[no].intersectionApprox(lineArr[no + 1]), currLayer);
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
    Polyline currPolyline = polyline;
    for (int i = 1; i < currPolyline.arr.length - 1; i++) {
      boolean trySkip;
      if (i == 1 || i == currPolyline.arr.length - 2) {
        // the position of the first corner and the last corner
        //  must be retained exactly
        Point prevCorner = currPolyline.corner(i - 1);
        Point currCorner = currPolyline.corner(i);
        trySkip = currCorner.equals(prevCorner);
      } else {
        FloatPoint prevCorner = currPolyline.cornerApprox(i - 1);
        FloatPoint currCorner = currPolyline.cornerApprox(i);
        trySkip = currCorner.distanceSquare(prevCorner) < c_min_corner_dist_square;
      }

      if (trySkip) {
        // check, if skipping the line of length 0 does not
        // result in a clearance violation
        Line[] currLines = new Line[currPolyline.arr.length - 1];
        System.arraycopy(currPolyline.arr, 0, currLines, 0, i);
        System.arraycopy(currPolyline.arr, i + 1, currLines, i, currLines.length - i);
        Polyline tmp = new Polyline(currLines);
        boolean checkOk = tmp.arr.length == currLines.length;
        if (checkOk && !currPolyline.arr[i].isMultipleOf45Degree()) {
          // no check necessary for skipping 45 degree lines, because the check is
          // performance critical and the line shapes
          // are intersected with the bounding octagon anyway.
          if (i > 1) {
            TileShape shapeToCheck = tmp.offsetShape(currHalfWidth, i - 2);
            checkOk =
                board.checkTraceShape(
                    shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
          }
          if (checkOk && (i < currPolyline.arr.length - 2)) {
            TileShape shapeToCheck = tmp.offsetShape(currHalfWidth, i - 1);
            checkOk =
                board.checkTraceShape(
                    shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
          }
        }
        if (checkOk) {
          polylineChanged = true;
          currPolyline = tmp;
          --i;
        }
      }
    }
    if (!polylineChanged) {
      return polyline;
    }
    return currPolyline;
  }

  /** Smoothens acute angles with contact traces. Returns true, if something was changed. */
  boolean smoothenEndCornersAtTrace(PolylineTrace trace) {
    if (this.onlyNetNoArr.length > 0 && !trace.netsEqual(this.onlyNetNoArr)) {
      return false;
    }
    currLayer = trace.getLayer();
    currHalfWidth = trace.getHalfWidth();
    currNetNoArr = trace.netNoArr;
    currClType = trace.clearanceClassNo();
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
    PolylineTrace currTrace = trace;
    while (connectionToTraceImproved) {
      connectionToTraceImproved = false;
      Polyline adjustedPolyline = smoothenEndCornersAtTrace2(currTrace);
      if (adjustedPolyline != null) {
        int traceLayer = currTrace.getLayer();
        int currClClass = currTrace.clearanceClassNo();
        FixedState currFixedState = currTrace.getFixedState();
        board.removeItem(currTrace);
        PolylineTrace adjInsTrace =
            board.insertTraceWithoutCleaning(
                adjustedPolyline,
                traceLayer,
                currHalfWidth,
                currTrace.netNoArr,
                currClClass,
                currFixedState);
        if (adjInsTrace != null) {
          result = true;
          connectionToTraceImproved = true;
          board.removeItem(currTrace);
          currTrace = adjInsTrace;
          for (int currNetNo : currTrace.netNoArr) {
            board.splitTraces(adjustedPolyline.firstCorner(), traceLayer, currNetNo);
            board.splitTraces(adjustedPolyline.lastCorner(), traceLayer, currNetNo);

            try {
              board.normalizeTraces(currNetNo);
            } catch (Exception e) {
              FRLogger.error(
                  "The normalization of net '" + board.rules.nets.get(currNetNo).name + "' failed.",
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
  boolean splitTracesAtKeepPoint() {
    if (this.keepPoint == null) {
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems =
        this.board.pickItems(this.keepPoint, this.keepPointLayer, filter);
    for (Item currItem : pickedItems) {
      Trace[] splitPieces = ((Trace) currItem).split(this.keepPoint);
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
        board.changedArea.join(result.cornerApprox(result.cornerCount() - 1), currLayer);
      }
    } else if (board.changedArea != null) {
      // mark the changed area
      board.changedArea.join(result.cornerApprox(0), currLayer);
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
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(this.board);
    Polyline newPolyline =
        shoveTraceAlgo.springOverObstacles(
            polyline, currHalfWidth, currLayer, currNetNoArr, currClType, contactPins);
    if (newPolyline != null && newPolyline != polyline) {
      if (this.board.checkPolylineTrace(
          newPolyline, currLayer, currHalfWidth, currNetNoArr, currClType)) {
        result = newPolyline;
      }
    }
    return result;
  }

  abstract Polyline smoothenStartCornerAtTrace(PolylineTrace trace);

  abstract Polyline smoothenEndCornerAtTrace(PolylineTrace trace);
}

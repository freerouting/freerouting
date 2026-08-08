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

  /** Creates a new instance of PullTightAlgo */
  PullTightAlgo(
      RoutingBoard p_board,
      int[] p_only_net_no_arr,
      Stoppable p_stoppable_thread,
      int p_time_limit,
      Point p_keep_point,
      int p_keep_point_layer) {
    board = p_board;
    onlyNetNoArr = p_only_net_no_arr;
    stoppableThread = p_stoppable_thread;
    if (p_time_limit > 0) {
      this.timeLimit = new TimeLimit(p_time_limit);
    } else {
      this.timeLimit = null;
    }
    this.keepPoint = p_keep_point;
    this.keepPointLayer = p_keep_point_layer;
  }

  /**
   * Returns a new instance of PullTightAlgo. If p_only_net_no > 0, only traces with net number
   * p_not_no are optimized. If p_stoppable_thread != null, the algorithm can be requested to be
   * stopped. If p_time_limit > 0; the algorithm will be stopped after p_time_limit Milliseconds.
   */
  static PullTightAlgo getInstance(
      RoutingBoard p_board,
      int[] p_only_net_no_arr,
      IntOctagon p_clip_shape,
      int p_min_translate_dist,
      Stoppable p_stoppable_thread,
      int p_time_limit,
      Point p_keep_point,
      int p_keep_point_layer) {
    PullTightAlgo result;
    AngleRestriction angleRestriction = p_board.rules.getTraceAngleRestriction();
    if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
      result =
          new PullTightAlgo90(
              p_board,
              p_only_net_no_arr,
              p_stoppable_thread,
              p_time_limit,
              p_keep_point,
              p_keep_point_layer);
    } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      result =
          new PullTightAlgo45(
              p_board,
              p_only_net_no_arr,
              p_stoppable_thread,
              p_time_limit,
              p_keep_point,
              p_keep_point_layer);
    } else {
      result =
          new PullTightAlgoAnyAngle(
              p_board,
              p_only_net_no_arr,
              p_stoppable_thread,
              p_time_limit,
              p_keep_point,
              p_keep_point_layer);
    }
    result.currClipShape = p_clip_shape;
    result.minTranslateDist = Math.max(p_min_translate_dist, 100);
    return result;
  }

  /**
   * Function for optimizing the route in an internal marked area. If p_clip_shape != null, the
   * optimizing area is restricted to p_clip_shape. p_trace_cost_arr is used for optimizing vias and
   * may be null.
   */
  void optChangedArea(ExpansionCostFactor[] p_trace_cost_arr) {
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
          } else if (currOb instanceof Via via && p_trace_cost_arr != null) {
            if (OptViaAlgo.optViaLocation(
                this.board, via, p_trace_cost_arr, this.minTranslateDist, 10)) {
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
      Polyline p_polyline,
      int p_layer,
      int p_half_width,
      int[] p_net_no_arr,
      int p_cl_type,
      Set<Pin> p_contact_pins) {
    currLayer = p_layer;
    ShapeSearchTree searchTree = this.board.searchTreeManager.getDefaultTree();
    currHalfWidth = p_half_width + searchTree.clearanceCompensationValue(p_cl_type, p_layer);
    currNetNoArr = p_net_no_arr;
    currClType = p_cl_type;
    contactPins = p_contact_pins;
    return pullTight(p_polyline);
  }

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

  /** tries to shorten p_polyline by relocating its lines */
  Polyline repositionLines(Polyline p_polyline) {
    if (p_polyline.arr.length < 5) {
      return p_polyline;
    }
    for (int i = 2; i < p_polyline.arr.length - 2; i++) {
      Line newLine = repositionLine(p_polyline.arr, i);
      if (newLine != null) {
        Line[] lineArr = new Line[p_polyline.arr.length];
        System.arraycopy(p_polyline.arr, 0, lineArr, 0, lineArr.length);
        lineArr[i] = newLine;
        Polyline result = new Polyline(lineArr);
        return skipSegmentsOfLength0(result);
      }
    }
    return p_polyline;
  }

  /**
   * Tries to reposition the line with index p_no to make the polyline consisting of p_line_arr
   * shorter.
   */
  protected Line repositionLine(Line[] p_line_arr, int p_no) {
    if (p_line_arr.length - p_no < 3) {
      return null;
    }
    if (currClipShape != null)
    // check, that the corners of the line to translate are inside
    // the clip shape
    {
      for (int i = -1; i < 1; i++) {
        Point currCorner = p_line_arr[p_no + i].intersection(p_line_arr[p_no + i + 1]);
        if (currClipShape.isOutside(currCorner)) {
          return null;
        }
      }
    }
    Line translateLine = p_line_arr[p_no];
    Point prevCorner = p_line_arr[p_no - 2].intersection(p_line_arr[p_no - 1]);
    Point nextCorner = p_line_arr[p_no + 1].intersection(p_line_arr[p_no + 2]);
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
    checkLines[0] = p_line_arr[p_no - 1];
    checkLines[2] = p_line_arr[p_no + 1];
    boolean firstTime = true;
    while (firstTime || Math.abs(deltaDist) > minTranslateDist) {
      boolean checkOk = false;

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
      board.changedArea.join(p_line_arr[p_no - 1].intersectionApprox(p_line_arr[p_no]), currLayer);
      board.changedArea.join(p_line_arr[p_no].intersectionApprox(p_line_arr[p_no + 1]), currLayer);
    }
    return newLine;
  }

  /**
   * tries to skip linesegments of length 0. A check is necessary before skipping because new dog
   * ears may occur.
   */
  Polyline skipSegmentsOfLength0(Polyline p_polyline) {
    boolean polylineChanged = false;
    Polyline currPolyline = p_polyline;
    for (int i = 1; i < currPolyline.arr.length - 1; i++) {
      boolean trySkip;
      if (i == 1 || i == currPolyline.arr.length - 2)
      // the position of the first corner and the last corner
      //  must be retained exactly
      {
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
      return p_polyline;
    }
    return currPolyline;
  }

  /** Smoothens acute angles with contact traces. Returns true, if something was changed. */
  boolean smoothenEndCornersAtTrace(PolylineTrace p_trace) {
    if (this.onlyNetNoArr.length > 0 && !p_trace.netsEqual(this.onlyNetNoArr)) {
      return false;
    }
    currLayer = p_trace.getLayer();
    currHalfWidth = p_trace.getHalfWidth();
    currNetNoArr = p_trace.netNoArr;
    currClType = p_trace.clearanceClassNo();
    return smoothenEndCornersAtTrace1(p_trace);
  }

  /** Smoothens acute angles with contact traces. Returns true, if something was changed. */
  private boolean smoothenEndCornersAtTrace1(PolylineTrace p_trace) {
    // try to improve the connection to other traces
    if (p_trace.isShoveFixed()) {
      return false;
    }
    Set<Pin> savedContactPins = this.contactPins;
    // to allow the trace to slide to the end point of a contact trace, if the contact trace ends at
    // a pin.
    this.contactPins = null;
    boolean result = false;
    boolean connectionToTraceImproved = true;
    PolylineTrace currTrace = p_trace;
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
  private Polyline smoothenEndCornersAtTrace2(PolylineTrace p_trace) {
    if (p_trace == null || !p_trace.isOnTheBoard()) {
      return null;
    }
    Polyline result = smoothenStartCornerAtTrace(p_trace);
    if (result == null) {
      result = smoothenEndCornerAtTrace(p_trace);
      if (result != null && board.changedArea != null) {
        // mark the changed area
        board.changedArea.join(result.cornerApprox(result.cornerCount() - 1), currLayer);
      }
    } else if (board.changedArea != null) {
      // mark the changed area
      board.changedArea.join(result.cornerApprox(0), currLayer);
    }
    if (result != null) {
      this.contactPins = p_trace.touchingPinsAtEndCorners();
      result = skipSegmentsOfLength0(result);
    }
    return result;
  }

  /** Wraps around pins of the own net to avoid acid traps. */
  protected Polyline avoidAcidTraps(Polyline p_polyline) {
    if (true) {
      return p_polyline;
    }
    Polyline result = p_polyline;
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(this.board);
    Polyline newPolyline =
        shoveTraceAlgo.springOverObstacles(
            p_polyline, currHalfWidth, currLayer, currNetNoArr, currClType, contactPins);
    if (newPolyline != null && newPolyline != p_polyline) {
      if (this.board.checkPolylineTrace(
          newPolyline, currLayer, currHalfWidth, currNetNoArr, currClType)) {
        result = newPolyline;
      }
    }
    return result;
  }

  abstract Polyline pullTight(Polyline p_polyline);

  abstract Polyline smoothenStartCornerAtTrace(PolylineTrace p_trace);

  abstract Polyline smoothenEndCornerAtTrace(PolylineTrace p_trace);
}

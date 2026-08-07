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
  static PullTightAlgo get_instance(
      RoutingBoard p_board,
      int[] p_only_net_no_arr,
      IntOctagon p_clip_shape,
      int p_min_translate_dist,
      Stoppable p_stoppable_thread,
      int p_time_limit,
      Point p_keep_point,
      int p_keep_point_layer) {
    PullTightAlgo result;
    AngleRestriction angleRestriction = p_board.rules.get_trace_angle_restriction();
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
  void opt_changed_area(ExpansionCostFactor[] p_trace_cost_arr) {
    if (board.changedArea == null) {
      return;
    }
    boolean somethingChanged = true;
    // starting with curr_min_translate_dist big is a try to
    // avoid fine approximation at the beginning to avoid
    // problems with dog ears
    while (somethingChanged) {
      somethingChanged = false;
      for (int i = 0; i < board.get_layer_count(); i++) {
        IntOctagon changedRegion = board.changedArea.get_area(i);
        if (changedRegion.is_empty()) {
          continue;
        }
        board.changedArea.set_empty(i);
        board.join_graphics_update_box(changedRegion.bounding_box());
        double changedAreaOffset =
            1.5
                * (board.rules.clearanceMatrix.max_value(i)
                    + 2 * board.rules.get_max_trace_half_width());
        changedRegion = changedRegion.enlarge(changedAreaOffset);
        // search in the ShapeSearchTree for all overlapping traces
        // with clipShape on layer i
        Collection<SearchTreeObject> items = board.overlapping_objects(changedRegion, i);
        for (SearchTreeObject currOb : items) {
          if (this.is_stop_requested()) {
            return;
          }
          if (currOb instanceof PolylineTrace currTrace) {
            if (currTrace.pull_tight(this)) {
              somethingChanged = true;
              if (this.split_traces_at_keep_point()) {
                break;
              }
            } else if (smoothen_end_corners_at_trace(currTrace)) {
              somethingChanged = true;
              break; // because items may be removed
            }
          } else if (currOb instanceof Via via && p_trace_cost_arr != null) {
            if (OptViaAlgo.opt_via_location(
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
  Polyline pull_tight(
      Polyline p_polyline,
      int p_layer,
      int p_half_width,
      int[] p_net_no_arr,
      int p_cl_type,
      Set<Pin> p_contact_pins) {
    currLayer = p_layer;
    ShapeSearchTree searchTree = this.board.searchTreeManager.get_default_tree();
    currHalfWidth = p_half_width + searchTree.clearance_compensation_value(p_cl_type, p_layer);
    currNetNoArr = p_net_no_arr;
    currClType = p_cl_type;
    contactPins = p_contact_pins;
    return pull_tight(p_polyline);
  }

  /** Terminates the pull tight algorithm, if the user has made a stop request. */
  protected boolean is_stop_requested() {
    if (this.stoppableThread != null && this.stoppableThread.isStopRequested()) {
      return true;
    }
    if (this.timeLimit == null) {
      return false;
    }
    boolean timeLimitExceeded = this.timeLimit.limit_exceeded();
    if (timeLimitExceeded) {

      if (this.board == null) {
        FRLogger.error("PullTightAlgo.is_stop_requested: board is null", null);
      }

      FRLogger.debug("PullTightAlgo.is_stop_requested: time limit exceeded");
    }
    return timeLimitExceeded;
  }

  /** tries to shorten p_polyline by relocating its lines */
  Polyline reposition_lines(Polyline p_polyline) {
    if (p_polyline.arr.length < 5) {
      return p_polyline;
    }
    for (int i = 2; i < p_polyline.arr.length - 2; i++) {
      Line newLine = reposition_line(p_polyline.arr, i);
      if (newLine != null) {
        Line[] lineArr = new Line[p_polyline.arr.length];
        System.arraycopy(p_polyline.arr, 0, lineArr, 0, lineArr.length);
        lineArr[i] = newLine;
        Polyline result = new Polyline(lineArr);
        return skip_segments_of_length_0(result);
      }
    }
    return p_polyline;
  }

  /**
   * Tries to reposition the line with index p_no to make the polyline consisting of p_line_arr
   * shorter.
   */
  protected Line reposition_line(Line[] p_line_arr, int p_no) {
    if (p_line_arr.length - p_no < 3) {
      return null;
    }
    if (currClipShape != null)
    // check, that the corners of the line to translate are inside
    // the clip shape
    {
      for (int i = -1; i < 1; i++) {
        Point currCorner = p_line_arr[p_no + i].intersection(p_line_arr[p_no + i + 1]);
        if (currClipShape.is_outside(currCorner)) {
          return null;
        }
      }
    }
    Line translateLine = p_line_arr[p_no];
    Point prevCorner = p_line_arr[p_no - 2].intersection(p_line_arr[p_no - 1]);
    Point nextCorner = p_line_arr[p_no + 1].intersection(p_line_arr[p_no + 2]);
    double prevDist = translateLine.signed_distance(prevCorner.to_float());
    double nextDist = translateLine.signed_distance(nextCorner.to_float());
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
    Side sideOfNearestPoint = translateLine.side_of(nearestPoint);
    int sign = Signum.as_int(maxTranslateDist);
    Line newLine = null;
    Line[] checkLines = new Line[3];
    checkLines[0] = p_line_arr[p_no - 1];
    checkLines[2] = p_line_arr[p_no + 1];
    boolean firstTime = true;
    while (firstTime || Math.abs(deltaDist) > minTranslateDist) {
      boolean checkOk = false;

      if (firstTime && nearestPoint instanceof IntPoint) {
        checkLines[1] = Line.get_instance(nearestPoint, translateLine.direction());
      } else {
        checkLines[1] = translateLine.translate(-translateDist);
      }
      if (checkLines[1].equals(translateLine)) {
        // may happen at first time if nearestPoint is not an IntPoint
        return null;
      }
      Side newLineSideOfNearestPoint = checkLines[1].side_of(nearestPoint);
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
        TileShape shapeToCheck = tmp.offset_shape(currHalfWidth, 0);
        checkOk =
            board.check_trace_shape(
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
      board.changedArea.join(checkLines[0].intersection_approx(newLine), currLayer);
      board.changedArea.join(checkLines[2].intersection_approx(newLine), currLayer);
      board.changedArea.join(p_line_arr[p_no - 1].intersection_approx(p_line_arr[p_no]), currLayer);
      board.changedArea.join(p_line_arr[p_no].intersection_approx(p_line_arr[p_no + 1]), currLayer);
    }
    return newLine;
  }

  /**
   * tries to skip linesegments of length 0. A check is necessary before skipping because new dog
   * ears may occur.
   */
  Polyline skip_segments_of_length_0(Polyline p_polyline) {
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
        FloatPoint prevCorner = currPolyline.corner_approx(i - 1);
        FloatPoint currCorner = currPolyline.corner_approx(i);
        trySkip = currCorner.distance_square(prevCorner) < c_min_corner_dist_square;
      }

      if (trySkip) {
        // check, if skipping the line of length 0 does not
        // result in a clearance violation
        Line[] currLines = new Line[currPolyline.arr.length - 1];
        System.arraycopy(currPolyline.arr, 0, currLines, 0, i);
        System.arraycopy(currPolyline.arr, i + 1, currLines, i, currLines.length - i);
        Polyline tmp = new Polyline(currLines);
        boolean checkOk = tmp.arr.length == currLines.length;
        if (checkOk && !currPolyline.arr[i].is_multiple_of_45_degree()) {
          // no check necessary for skipping 45 degree lines, because the check is
          // performance critical and the line shapes
          // are intersected with the bounding octagon anyway.
          if (i > 1) {
            TileShape shapeToCheck = tmp.offset_shape(currHalfWidth, i - 2);
            checkOk =
                board.check_trace_shape(
                    shapeToCheck, currLayer, currNetNoArr, currClType, this.contactPins);
          }
          if (checkOk && (i < currPolyline.arr.length - 2)) {
            TileShape shapeToCheck = tmp.offset_shape(currHalfWidth, i - 1);
            checkOk =
                board.check_trace_shape(
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
  boolean smoothen_end_corners_at_trace(PolylineTrace p_trace) {
    if (this.onlyNetNoArr.length > 0 && !p_trace.nets_equal(this.onlyNetNoArr)) {
      return false;
    }
    currLayer = p_trace.get_layer();
    currHalfWidth = p_trace.get_half_width();
    currNetNoArr = p_trace.netNoArr;
    currClType = p_trace.clearance_class_no();
    return smoothen_end_corners_at_trace_1(p_trace);
  }

  /** Smoothens acute angles with contact traces. Returns true, if something was changed. */
  private boolean smoothen_end_corners_at_trace_1(PolylineTrace p_trace) {
    // try to improve the connection to other traces
    if (p_trace.is_shove_fixed()) {
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
      Polyline adjustedPolyline = smoothen_end_corners_at_trace_2(currTrace);
      if (adjustedPolyline != null) {
        int traceLayer = currTrace.get_layer();
        int currClClass = currTrace.clearance_class_no();
        FixedState currFixedState = currTrace.get_fixed_state();
        board.remove_item(currTrace);
        PolylineTrace adjInsTrace =
            board.insert_trace_without_cleaning(
                adjustedPolyline,
                traceLayer,
                currHalfWidth,
                currTrace.netNoArr,
                currClClass,
                currFixedState);
        if (adjInsTrace != null) {
          result = true;
          connectionToTraceImproved = true;
          board.remove_item(currTrace);
          currTrace = adjInsTrace;
          for (int currNetNo : currTrace.netNoArr) {
            board.split_traces(adjustedPolyline.first_corner(), traceLayer, currNetNo);
            board.split_traces(adjustedPolyline.last_corner(), traceLayer, currNetNo);

            try {
              board.normalize_traces(currNetNo);
            } catch (Exception e) {
              FRLogger.error(
                  "The normalization of net '" + board.rules.nets.get(currNetNo).name + "' failed.",
                  e);
            }

            if (split_traces_at_keep_point()) {
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
  boolean split_traces_at_keep_point() {
    if (this.keepPoint == null) {
      return false;
    }
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Collection<Item> pickedItems =
        this.board.pick_items(this.keepPoint, this.keepPointLayer, filter);
    for (Item currItem : pickedItems) {
      Trace[] splitPieces = ((Trace) currItem).split(this.keepPoint);
      if (splitPieces != null) {
        return true;
      }
    }
    return false;
  }

  /** Smoothens acute angles with contact traces. Returns null, if something was changed. */
  private Polyline smoothen_end_corners_at_trace_2(PolylineTrace p_trace) {
    if (p_trace == null || !p_trace.is_on_the_board()) {
      return null;
    }
    Polyline result = smoothen_start_corner_at_trace(p_trace);
    if (result == null) {
      result = smoothen_end_corner_at_trace(p_trace);
      if (result != null && board.changedArea != null) {
        // mark the changed area
        board.changedArea.join(result.corner_approx(result.corner_count() - 1), currLayer);
      }
    } else if (board.changedArea != null) {
      // mark the changed area
      board.changedArea.join(result.corner_approx(0), currLayer);
    }
    if (result != null) {
      this.contactPins = p_trace.touching_pins_at_end_corners();
      result = skip_segments_of_length_0(result);
    }
    return result;
  }

  /** Wraps around pins of the own net to avoid acid traps. */
  protected Polyline avoid_acid_traps(Polyline p_polyline) {
    if (true) {
      return p_polyline;
    }
    Polyline result = p_polyline;
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(this.board);
    Polyline newPolyline =
        shoveTraceAlgo.spring_over_obstacles(
            p_polyline, currHalfWidth, currLayer, currNetNoArr, currClType, contactPins);
    if (newPolyline != null && newPolyline != p_polyline) {
      if (this.board.check_polyline_trace(
          newPolyline, currLayer, currHalfWidth, currNetNoArr, currClType)) {
        result = newPolyline;
      }
    }
    return result;
  }

  abstract Polyline pull_tight(Polyline p_polyline);

  abstract Polyline smoothen_start_corner_at_trace(PolylineTrace p_trace);

  abstract Polyline smoothen_end_corner_at_trace(PolylineTrace p_trace);
}

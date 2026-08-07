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
      Point p_start_corner,
      int p_layer,
      int[] p_pen_half_width_arr,
      boolean[] p_layer_active_arr,
      int[] p_net_no_arr,
      int p_clearance_class,
      ViaRule p_via_rule,
      boolean p_push_enabled,
      int p_trace_tidy_width,
      int p_pull_tight_accuracy,
      Item p_start_item,
      Set<Item> p_target_set,
      RoutingBoard p_board,
      boolean p_is_stitch_mode,
      boolean p_with_neckdown,
      boolean p_via_snap_to_smd_center,
      boolean p_hilight_shove_failing_obstacle) {
    board = p_board;
    layer = p_layer;
    if (p_push_enabled) {
      maxShoveTraceRecursionDepth = 20;
      maxShoveViaRecursionDepth = 8;
      maxSpringOverRecursionDepth = 5;
    } else {
      maxShoveTraceRecursionDepth = 0;
      maxShoveViaRecursionDepth = 0;
      maxSpringOverRecursionDepth = 0;
    }
    traceTidyWidth = p_trace_tidy_width;
    pullTightAccuracy = p_pull_tight_accuracy;
    prevCorner = p_start_corner;
    netNoArr = p_net_no_arr;
    penHalfWidthArr = p_pen_half_width_arr;
    layerActive = p_layer_active_arr;
    clearanceClass = p_clearance_class;
    viaRule = p_via_rule;
    startItem = p_start_item;
    targetSet = p_target_set;
    isStitchMode = p_is_stitch_mode;
    withNeckdown = p_with_neckdown;
    viaSnapToSmdCenter = p_via_snap_to_smd_center;
    hilightShoveFailingObstacle = p_hilight_shove_failing_obstacle;
    pullTightTimeLimit = PULL_TIGHT_TIME_LIMIT;

    calculate_target_points_and_areas();
    swapPinInfos = calculate_swap_pin_infos();
  }

  /**
   * Append a line to the trace routed so far. Return true, if the route is completed by connecting
   * to a target.
   */
  public boolean next_corner(FloatPoint p_corner) {
    if (!this.layerActive[this.layer]) {
      return false;
    }
    IntPoint currCorner = p_corner.round();
    if (!(board.contains(prevCorner)
        && board.contains(currCorner)
        && board.layerStructure.arr[this.layer].isSignal)) {
      return false;
    }

    if (currCorner.equals(prevCorner)) {
      return false;
    }
    if (nearestTargetItem instanceof DrillItem target) {
      if (this.prevCorner.equals(target.get_center())) {
        return true; // connection already completed at prevCorner.
      }
    }
    this.shoveFailingObstacle = null;
    AngleRestriction angleRestriction = this.board.rules.get_trace_angle_restriction();
    if (angleRestriction != AngleRestriction.NONE && !(prevCorner instanceof IntPoint)) {
      return false;
    }
    if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
      currCorner = currCorner.orthogonal_projection((IntPoint) prevCorner);
    } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
      currCorner = currCorner.fortyfive_degree_projection((IntPoint) prevCorner);
    }
    Item endRoutingItem = board.pick_nearest_routing_item(prevCorner, this.layer, null);
    // look for a nearby item of this net, which is not connected to
    // endRoutingItem.
    nearestTargetItem = board.pick_nearest_routing_item(currCorner, this.layer, endRoutingItem);
    TimeLimit checkForcedTraceTimeLimit;
    if (isStitchMode) {
      // because no check before inserting in this case
      checkForcedTraceTimeLimit = null;
    } else {
      checkForcedTraceTimeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
    }

    // app.freerouting.tests.Validate.check("before insert", app.freerouting.board);
    Point okPoint =
        board.insert_forced_trace_segment(
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
      okPoint = try_neckdown_at_start(currCorner);
    }
    if (okPoint == prevCorner && this.withNeckdown) {
      okPoint = try_neckdown_at_end(this.prevCorner, currCorner);
    }
    if (okPoint == null) {
      // database may be damaged, restore previous situation
      board.undo(null);
      // end routing in case it is dynamic
      return !isStitchMode;
    }

    if (okPoint == prevCorner) {
      set_shove_failing_obstacle(board.get_shove_failing_obstacle());
      return false;
    }
    this.prevCorner = okPoint;
    // check, if a target is reached
    boolean routeCompleted = false;
    if (okPoint == currCorner) {
      routeCompleted = connect_to_target(currCorner);
    }

    IntOctagon tidyClipShape;
    if (traceTidyWidth == Integer.MAX_VALUE) {
      tidyClipShape = null;
    } else if (traceTidyWidth == 0) {
      tidyClipShape = IntOctagon.EMPTY;
    } else {
      tidyClipShape = okPoint.surrounding_octagon().enlarge(traceTidyWidth);
    }
    int[] optNetNoArr;
    if (maxShoveTraceRecursionDepth <= 0) {
      optNetNoArr = netNoArr;
    } else {
      optNetNoArr = new int[0];
    }
    if (routeCompleted) {
      this.board.reduce_nets_of_route_items();
      for (int currNetNo : this.netNoArr) {
        this.board.combine_traces(currNetNo);
      }
    } else {
      calc_nearest_target_point(this.prevCorner.to_float());
    }
    board.opt_changed_area(
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
  public boolean change_layer(int p_to_layer) {
    if (this.layer == p_to_layer) {
      return true;
    }
    if (p_to_layer < 0 || p_to_layer >= this.layerActive.length) {
      FRLogger.warn("Route.change_layer: p_to_layer out of range");
      return false;
    }
    if (!this.layerActive[p_to_layer]) {
      return false;
    }
    if (this.viaRule == null) {
      return false;
    }
    this.shoveFailingObstacle = null;
    if (this.viaSnapToSmdCenter) {
      boolean snappedToSmdCenter = snap_to_smd_center(p_to_layer);
      if (!snappedToSmdCenter) {
        snap_to_smd_center(this.layer);
      }
    }
    boolean result = true;
    int minLayer = Math.min(this.layer, p_to_layer);
    int maxLayer = Math.max(this.layer, p_to_layer);
    boolean viaFound = false;
    for (int i = 0; i < this.viaRule.via_count(); i++) {
      ViaInfo currViaInfo = this.viaRule.get_via(i);
      Padstack currViaPadstack = currViaInfo.get_padstack();
      if (minLayer < currViaPadstack.from_layer() || maxLayer > currViaPadstack.to_layer()) {
        continue;
      }
      // make the current situation restorable by undo
      board.generate_snapshot();
      result =
          board.forced_via(
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
      set_shove_failing_obstacle(board.get_shove_failing_obstacle());
      board.undo(null);
    }
    if (viaFound) {
      this.layer = p_to_layer;
    }
    return result;
  }

  /**
   * Snaps to the center of a smd pin, if the location on p_layer is inside a smd pin of the own
   * net,
   */
  private boolean snap_to_smd_center(int p_layer) {
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = board.pick_items(this.prevCorner, p_layer, selectionFilter);
    Pin foundSmdPin = null;
    for (Item currItem : pickedItems) {
      if (currItem instanceof Pin currPin && currItem.shares_net_no(this.netNoArr)) {
        if (currPin.first_layer() == p_layer && currPin.last_layer() == p_layer) {
          foundSmdPin = currPin;
          break;
        }
      }
    }
    if (foundSmdPin == null) {
      return false;
    }
    Point pinCenter = foundSmdPin.get_center();
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
  private boolean connect_to_target(IntPoint p_from_point) {
    if (nearestTargetItem != null && targetSet != null && !targetSet.contains(nearestTargetItem)) {
      nearestTargetItem = null;
    }
    if (nearestTargetItem == null || !nearestTargetItem.shares_net_no(this.netNoArr)) {
      return false;
    }
    boolean routeCompleted = false;
    Point connectionPoint = null;
    if (nearestTargetItem instanceof DrillItem target) {
      connectionPoint = target.get_center();
    } else if (nearestTargetItem instanceof PolylineTrace trace) {
      return board.connect_to_trace(
          p_from_point, trace, this.penHalfWidthArr[layer], this.clearanceClass);
    } else if (nearestTargetItem instanceof ConductionArea) {
      connectionPoint = p_from_point;
    }
    if (connectionPoint instanceof IntPoint point) {
      routeCompleted = connect(p_from_point, point);
    }
    return routeCompleted;
  }

  /**
   * Tries to make a trace connection from p_from_point to p_to_point according to the angle
   * restriction. Returns true, if the connection succeeded.
   */
  private boolean connect(Point p_from_point, IntPoint p_to_point) {
    Point[] corners = angled_connection(p_from_point, p_to_point);
    boolean connectionSucceeded = true;
    for (int i = 1; i < corners.length; i++) {
      Point fromCorner = corners[i - 1];
      Point toCorner = corners[i];
      TimeLimit timeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
      while (!fromCorner.equals(toCorner)) {
        Point currOkPoint =
            board.insert_forced_trace_segment(
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
          currOkPoint = try_neckdown_at_end(fromCorner, toCorner);
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
  public int nearest_target_layer() {
    if (nearestTargetItem == null) {
      return this.layer;
    }
    int result;
    int firstLayer = nearestTargetItem.first_layer();
    int lastLayer = nearestTargetItem.last_layer();
    if (this.layer < firstLayer) {
      result = firstLayer;
    } else {
      result = Math.min(this.layer, lastLayer);
    }
    return result;
  }

  /** Returns all pins, which can be reached by a pin swap from a start or target pin. */
  private Set<SwapPinInfo> calculate_swap_pin_infos() {
    Set<SwapPinInfo> result = new TreeSet<>();
    if (this.targetSet == null) {
      return result;
    }
    for (Item currItem : this.targetSet) {
      if (currItem instanceof Pin pin) {
        Collection<Pin> currSwappablePins = pin.get_swappable_pins();
        for (Pin curr_swappable_pin : currSwappablePins) {
          result.add(new SwapPinInfo(curr_swappable_pin));
        }
      }
    }
    // add the from item, if it is a pin
    ItemSelectionFilter selectionFilter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.PINS);
    Collection<Item> pickedItems = board.pick_items(this.prevCorner, this.layer, selectionFilter);
    for (Item currItem : pickedItems) {
      if (currItem instanceof Pin pin) {
        Collection<Pin> currSwappablePins = pin.get_swappable_pins();
        for (Pin curr_swappable_pin : currSwappablePins) {
          result.add(new SwapPinInfo(curr_swappable_pin));
        }
      }
    }
    return result;
  }

  /** Highlights the targets and draws the incomplete. */
  public void draw(Graphics p_graphics, GraphicsContext p_graphics_context) {
    if (this.hilightShoveFailingObstacle && this.shoveFailingObstacle != null) {
      this.shoveFailingObstacle.draw(
          p_graphics, p_graphics_context, p_graphics_context.get_violations_color(), 1);
    }
    if (targetSet == null || netNoArr.length < 1) {
      return;
    }
    Net currNet = board.rules.nets.get(netNoArr[0]);
    if (currNet == null) {
      return;
    }
    Color highlightColor = p_graphics_context.get_hilight_color();
    double highligtColorIntensity = p_graphics_context.get_hilight_color_intensity();

    // hilight the swappable pins and their incompletes
    for (SwapPinInfo currInfo : this.swapPinInfos) {
      currInfo.pin.draw(
          p_graphics, p_graphics_context, highlightColor, 0.3 * highligtColorIntensity);
      if (currInfo.incomplete != null) {
        // draw the swap pin incomplete
        FloatPoint[] drawPoints = new FloatPoint[2];
        drawPoints[0] = currInfo.incomplete.a;
        drawPoints[1] = currInfo.incomplete.b;
        Color drawColor = p_graphics_context.get_incomplete_color();
        p_graphics_context.draw(drawPoints, 1, drawColor, p_graphics, highligtColorIntensity);
      }
    }

    // hilight the target set
    for (Item currItem : targetSet) {
      if (!(currItem instanceof ConductionArea)) {
        currItem.draw(p_graphics, p_graphics_context, highlightColor, highligtColorIntensity);
      }
    }
    FloatPoint fromCorner = this.prevCorner.to_float();
    if (nearestTargetPoint != null && prevCorner != null) {
      boolean currLengthMatchingOk = true; // used for drawing the incomplete as violation
      double maxTraceLength = currNet.getNetClass().get_maximum_trace_length();
      double minTraceLength = currNet.getNetClass().get_minimum_trace_length();
      double lengthMatchingColorIntensity =
          p_graphics_context.get_length_matching_area_color_intensity();
      if (maxTraceLength > 0 || minTraceLength > 0 && lengthMatchingColorIntensity > 0) {

        // draw the length matching area
        double traceLengthAdd = fromCorner.distance(this.prevCorner.to_float());
        // traceLengthAdd is != 0 only in stitching mode.
        if (maxTraceLength <= 0) {
          // maxTraceLength not provided. Create an ellipse containing the whole board.
          maxTraceLength = 0.3 * Limits.CRIT_INT;
        }
        double currMaxTraceLength = maxTraceLength - (currNet.get_trace_length() + traceLengthAdd);
        double currMinTraceLength = minTraceLength - (currNet.get_trace_length() + traceLengthAdd);
        double incompleteLength = nearestTargetPoint.distance(fromCorner);
        if (incompleteLength < currMaxTraceLength && minTraceLength <= maxTraceLength) {
          Vector delta = nearestTargetPoint.round().difference_by(prevCorner);
          double rotation = delta.angle_approx();
          FloatPoint center = fromCorner.middle_point(nearestTargetPoint);
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
          IntBox boundingBox =
              new IntBox(prevCorner.to_float().round(), nearestTargetPoint.round());
          boundingBox = boundingBox.offset(currMaxTraceLength - incompleteLength);
          board.join_graphics_update_box(boundingBox);
          if (ellipseCount == 2) {
            biggerRadius = 0.5 * currMinTraceLength;
            smallerRadius =
                0.5
                    * Math.sqrt(
                        currMinTraceLength * currMinTraceLength
                            - incompleteLength * incompleteLength);
            ellipseArr[1] = new Ellipse(center, rotation, biggerRadius, smallerRadius);
          }
          p_graphics_context.fill_ellipse_arr(
              ellipseArr,
              p_graphics,
              p_graphics_context.get_length_matching_area_color(),
              lengthMatchingColorIntensity);
        } else {
          currLengthMatchingOk = false;
        }
      }

      // draw the incomplete
      FloatPoint[] drawPoints = new FloatPoint[2];
      drawPoints[0] = fromCorner;
      drawPoints[1] = nearestTargetPoint;
      Color drawColor = p_graphics_context.get_incomplete_color();
      double drawWidth =
          Math.min(this.board.communication.get_resolution(Unit.MIL), 100); // problem with low
      // resolution on Kicad
      if (!currLengthMatchingOk) {
        drawColor = p_graphics_context.get_violations_color();
        drawWidth *= 3;
      }
      p_graphics_context.draw(drawPoints, drawWidth, drawColor, p_graphics, highligtColorIntensity);
      if (this.nearestTargetItem != null && !this.nearestTargetItem.is_on_layer(this.layer)) {
        // draw a marker to indicate the layer change.
        NetIncompletesGraphics.draw_layer_change_marker(
            drawPoints[0], 4 * penHalfWidthArr[0], p_graphics, p_graphics_context);
      }
    }
  }

  /**
   * Makes a connection polygon from p_from_point to p_to_point whose lines fulfill the angle
   * restriction.
   */
  private Point[] angled_connection(Point p_from_point, Point p_to_point) {
    IntPoint addCorner = null;
    if (p_from_point instanceof IntPoint point && p_to_point instanceof IntPoint point1) {
      AngleRestriction angleRestriction = this.board.rules.get_trace_angle_restriction();
      if (angleRestriction == AngleRestriction.NINETY_DEGREE) {
        addCorner = point.ninety_degree_corner(point1, true);
      } else if (angleRestriction == AngleRestriction.FORTYFIVE_DEGREE) {
        addCorner = point.fortyfive_degree_corner(point1, true);
      }
    }
    int newCornerCount = 2;
    if (addCorner != null) {
      ++newCornerCount;
    }
    Point[] result = new Point[newCornerCount];
    result[0] = p_from_point;
    if (addCorner != null) {
      result[1] = addCorner;
    }
    result[result.length - 1] = p_to_point;
    return result;
  }

  /**
   * Calculates a list of the center points of DrillItems, end points of traces and areas of
   * ConductionAreas in the target set.
   */
  private void calculate_target_points_and_areas() {
    targetPoints = new LinkedList<>();
    targetTracesAndAreas = new LinkedList<>();
    if (targetSet == null) {
      return;
    }
    for (Item currOb : targetSet) {
      if (currOb instanceof DrillItem item) {
        Point currPoint = item.get_center();
        targetPoints.add(new TargetPoint(currPoint.to_float(), currOb));
      } else if (currOb instanceof Trace || currOb instanceof ConductionArea) {
        targetTracesAndAreas.add(currOb);
      }
    }
  }

  public Point get_last_corner() {
    return prevCorner;
  }

  public boolean is_layer_active(int p_layer) {
    if (p_layer < 0 || p_layer >= layerActive.length) {
      return false;
    }
    return layerActive[p_layer];
  }

  /** The nearest point is used for drawing the incomplete */
  void calc_nearest_target_point(FloatPoint p_from_point) {
    double minDist = Double.MAX_VALUE;
    FloatPoint nearestPoint = null;
    Item nearestItem = null;
    for (TargetPoint curr_target_point : targetPoints) {
      double currDist = p_from_point.distance(curr_target_point.location);
      if (currDist < minDist) {
        minDist = currDist;
        nearestPoint = curr_target_point.location;
        nearestItem = curr_target_point.item;
      }
    }
    for (Item currItem : targetTracesAndAreas) {
      if (currItem instanceof PolylineTrace currTrace) {
        Polyline currPolyline = currTrace.polyline();
        if (currPolyline.bounding_box().distance(p_from_point) < minDist) {
          FloatPoint currNearestPoint = currPolyline.nearest_point_approx(p_from_point);
          double currDist = p_from_point.distance(currNearestPoint);
          if (currDist < minDist) {
            minDist = currDist;
            nearestPoint = currNearestPoint;
            nearestItem = currTrace;
          }
        }
      } else if (currItem instanceof ConductionArea curr_conduction_area
          && currItem.tile_shape_count() > 0) {
        Area currArea = curr_conduction_area.get_area();
        if (currArea.bounding_box().distance(p_from_point) < minDist) {
          FloatPoint currNearestPoint = currArea.nearest_point_approx(p_from_point);
          double currDist = p_from_point.distance(currNearestPoint);
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
    board.join_graphics_update_box(nearestItem.bounding_box());
  }

  private void set_shove_failing_obstacle(Item p_item) {
    this.shoveFailingObstacle = p_item;
    if (p_item != null) {
      this.board.join_graphics_update_box(p_item.bounding_box());
    }
  }

  /**
   * If the routed starts at a pin and the route failed with the normal trace width, another try
   * with the smallest pin width is done. Returns the okPoint of the try, which is this.prevPoint,
   * if the try failed.
   */
  private Point try_neckdown_at_start(IntPoint p_to_corner) {
    if (!(this.startItem instanceof Pin startPin)) {
      return this.prevCorner;
    }
    if (!startPin.is_on_layer(this.layer)) {
      return this.prevCorner;
    }
    FloatPoint pinCenter = startPin.get_center().to_float();
    double currClearance =
        this.board.rules.clearanceMatrix.get_value(
            this.clearanceClass, startPin.clearance_class_no(), this.layer, true);
    double pinNeckDownDistance = 2 * (0.5 * startPin.get_max_width(this.layer) + currClearance);
    if (pinCenter.distance(this.prevCorner.to_float()) >= pinNeckDownDistance) {
      return this.prevCorner;
    }

    int neckDownHalfwidth = startPin.get_trace_neckdown_halfwidth(this.layer);
    if (neckDownHalfwidth >= this.penHalfWidthArr[this.layer]) {
      return this.prevCorner;
    }

    // check, that the neck_down started inside the pin shape
    if (!this.prevCorner.equals(startPin.get_center())) {
      Item pickedItem = this.board.pick_nearest_routing_item(this.prevCorner, this.layer, null);
      if (pickedItem instanceof Trace trace) {
        if (trace.get_half_width() > neckDownHalfwidth) {
          return this.prevCorner;
        }
      }
    }
    TimeLimit timeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
    return board.insert_forced_trace_segment(
        prevCorner,
        p_to_corner,
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
  private Point try_neckdown_at_end(Point p_from_corner, Point p_to_corner) {
    if (!(this.nearestTargetItem instanceof Pin target_pin)) {
      return p_from_corner;
    }
    if (!target_pin.is_on_layer(this.layer)) {
      return p_from_corner;
    }
    FloatPoint pinCenter = target_pin.get_center().to_float();
    double currClearance =
        this.board.rules.clearanceMatrix.get_value(
            this.clearanceClass, target_pin.clearance_class_no(), this.layer, true);
    double pinNeckDownDistance = 2 * (0.5 * target_pin.get_max_width(this.layer) + currClearance);
    if (pinCenter.distance(p_from_corner.to_float()) >= pinNeckDownDistance) {
      return p_from_corner;
    }
    int neckDownHalfwidth = target_pin.get_trace_neckdown_halfwidth(this.layer);
    if (neckDownHalfwidth >= this.penHalfWidthArr[this.layer]) {
      return p_from_corner;
    }
    TimeLimit timeLimit = new TimeLimit(CHECK_FORCED_TRACE_TIME_LIMIT);
    return board.insert_forced_trace_segment(
        p_from_corner,
        p_to_corner,
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

    TargetPoint(FloatPoint p_location, Item p_item) {
      location = p_location;
      item = p_item;
    }
  }

  private class SwapPinInfo implements Comparable<SwapPinInfo> {

    final Pin pin;
    FloatLine incomplete;

    SwapPinInfo(Pin p_pin) {
      pin = p_pin;
      incomplete = null;
      if (p_pin.is_connected() || p_pin.net_count() != 1) {
        return;
      }
      // calculate the incomplete of p_pin
      FloatPoint pinCenter = p_pin.get_center().to_float();
      double minDist = Double.MAX_VALUE;
      FloatPoint nearestPoint = null;
      Collection<Item> netItems = board.get_connectable_items(p_pin.get_net_no(0));
      for (Item currItem : netItems) {
        if (currItem == this.pin || !(currItem instanceof DrillItem)) {
          continue;
        }
        FloatPoint currPoint = ((DrillItem) currItem).get_center().to_float();
        double currDist = pinCenter.distance_square(currPoint);
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
    public int compareTo(SwapPinInfo p_other) {
      return this.pin.compareTo(p_other.pin);
    }
  }
}

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

  /** The time limit in milliseconds for the pull tight algorithm */
  private static final int PULL_TIGHT_TIME_LIMIT = 2000;

  public final app.freerouting.autoroute.RoutingFailureLog failureLog;

  /** the area marked for optimizing the route */
  transient ChangedArea changedArea;

  /** Contains the database for the auto-route algorithm. */
  private transient AutorouteEngine autorouteEngine;

  private transient Item shoveFailingObstacle;
  private transient int shoveFailingLayer = -1;

  /**
   * Creates a new instance of a routing Board with surrounding box p_bounding_box Rules contains
   * the restrictions to obey when inserting items. Among other things it may contain a clearance
   * matrix.
   */
  public RoutingBoard(
      IntBox p_bounding_box,
      LayerStructure p_layer_structure,
      PolylineShape[] p_outline_shapes,
      int p_outline_cl_class_no,
      BoardRules p_rules,
      Communication p_board_communication) {
    super(
        p_bounding_box,
        p_layer_structure,
        p_outline_shapes,
        p_outline_cl_class_no,
        p_rules,
        p_board_communication);
    this.failureLog = new app.freerouting.autoroute.RoutingFailureLog();
  }

  /** Maintains the auto-router database after p_item is inserted, changed, or deleted. */
  @Override
  public void additional_update_after_change(Item p_item) {
    if (p_item == null) {
      return;
    }
    if (this.autorouteEngine == null || !this.autorouteEngine.maintainDatabase) {
      return;
    }
    // Invalidate the free space expansion rooms touching a shape of p_item.
    int shapeCount = p_item.tree_shape_count(this.autorouteEngine.autorouteSearchTree);
    for (int i = 0; i < shapeCount; i++) {
      TileShape currShape = p_item.get_tree_shape(this.autorouteEngine.autorouteSearchTree, i);
      this.autorouteEngine.invalidate_drill_pages(currShape);
      int currLayer = p_item.shape_layer(i);
      Collection<SearchTreeObject> overlaps =
          this.autorouteEngine.autorouteSearchTree.overlapping_objects(currShape, currLayer);
      for (SearchTreeObject currObject : overlaps) {
        if (currObject instanceof CompleteFreeSpaceExpansionRoom room) {
          this.autorouteEngine.remove_complete_expansion_room(room);
        }
      }
    }
    p_item.clear_autoroute_info();
  }

  /**
   * Removes the items in p_item_list and pulls the nearby rubber traces tight. Returns false, if
   * some items could not be removed, because they were fixed.
   */
  public boolean remove_items_and_pull_tight(
      Collection<Item> p_item_list, int p_tidy_width, int p_pull_tight_accuracy) {
    boolean result = true;
    IntOctagon tidyRegion;
    boolean calculateTidyRegion;
    if (p_tidy_width < Integer.MAX_VALUE) {
      tidyRegion = IntOctagon.EMPTY;
      calculateTidyRegion = p_tidy_width > 0;
    } else {
      tidyRegion = null;
      calculateTidyRegion = false;
    }
    start_marking_changed_area();
    Set<Integer> changedNets = new TreeSet<>();
    for (Item currItem : p_item_list) {
      if (currItem.isDeletionForbidden() || currItem.is_user_fixed()) {
        // We are not allowed to delete this item.
        result = false;
      } else {
        for (int i = 0; i < currItem.tile_shape_count(); i++) {
          TileShape currShape = currItem.get_tile_shape(i);
          changedArea.join(currShape, currItem.shape_layer(i));
          if (calculateTidyRegion) {
            tidyRegion = tidyRegion.union(currShape.bounding_octagon());
          }
        }
        remove_item(currItem);
        for (int i = 0; i < currItem.net_count(); i++) {
          changedNets.add(currItem.get_net_no(i));
        }
      }
    }
    for (Integer currNetNo : changedNets) {
      this.combine_traces(currNetNo);
    }
    if (calculateTidyRegion) {
      tidyRegion = tidyRegion.enlarge(p_tidy_width);
    }
    opt_changed_area(
        new int[0], tidyRegion, p_pull_tight_accuracy, null, null, PULL_TIGHT_TIME_LIMIT);
    return result;
  }

  /** starts marking the changed areas for optimizing traces */
  public void start_marking_changed_area() {
    if (changedArea == null) {
      changedArea = new ChangedArea(get_layer_count());
    }
  }

  /** enlarges the changed area on p_layer, so that it contains p_point */
  public void join_changed_area(FloatPoint p_point, int p_layer) {
    if (changedArea != null) {
      changedArea.join(p_point, p_layer);
    }
  }

  /** marks the whole board as changed */
  public void mark_all_changed_area() {
    start_marking_changed_area();
    FloatPoint[] boardCorners = new FloatPoint[4];
    boardCorners[0] = boundingBox.ll.to_float();
    boardCorners[1] = new FloatPoint(boundingBox.ur.x, boundingBox.ll.y);
    boardCorners[2] = boundingBox.ur.to_float();
    boardCorners[3] = new FloatPoint(boundingBox.ll.x, boundingBox.ur.y);
    for (int i = 0; i < get_layer_count(); i++) {
      for (int j = 0; j < 4; j++) {
        join_changed_area(boardCorners[j], i);
      }
    }
  }

  /**
   * Optimizes the route in the internally marked area. If p_net_no {@literal >} 0, only traces with
   * net number p_net_no are optimized. If p_clip_shape != null the optimizing is restricted to
   * p_clip_shape. p_trace_cost_arr is used for optimizing vias and may be null. If
   * p_stoppable_thread != null, the algorithm can be requested to be stopped. If p_time_limit
   * {@literal >} 0; the algorithm will be stopped after p_time_limit Milliseconds.
   */
  public void opt_changed_area(
      int[] p_only_net_no_arr,
      IntOctagon p_clip_shape,
      int p_accuracy,
      ExpansionCostFactor[] p_trace_cost_arr,
      Stoppable p_stoppable_thread,
      int p_time_limit) {
    opt_changed_area(
        p_only_net_no_arr,
        p_clip_shape,
        p_accuracy,
        p_trace_cost_arr,
        p_stoppable_thread,
        p_time_limit,
        null,
        0);
  }

  /**
   * Optimizes the route in the internally marked area. If p_net_no {@literal >} 0, only traces with
   * net number p_net_no are optimized. If p_clip_shape != null the optimizing is restricted to
   * p_clip_shape. p_trace_cost_arr is used for optimizing vias and may be null. If
   * p_stoppable_thread != null, the algorithm can be requested to be stopped. If p_time_limit
   * {@literal >} 0; the algorithm will be stopped after p_time_limit Milliseconds. If p_keep_point
   * != null, traces on layer p_keep_point_layer containing p_keep_point will also contain this
   * point after optimizing.
   */
  public void opt_changed_area(
      int[] p_only_net_no_arr,
      IntOctagon p_clip_shape,
      int p_accuracy,
      ExpansionCostFactor[] p_trace_cost_arr,
      Stoppable p_stoppable_thread,
      int p_time_limit,
      Point p_keep_point,
      int p_keep_point_layer) {
    if (changedArea == null) {
      return;
    }
    if (p_clip_shape != IntOctagon.EMPTY) {
      PullTightAlgo pullTightAlgo =
          PullTightAlgo.get_instance(
              this,
              p_only_net_no_arr,
              p_clip_shape,
              p_accuracy,
              p_stoppable_thread,
              p_time_limit,
              p_keep_point,
              p_keep_point_layer);
      pullTightAlgo.opt_changed_area(p_trace_cost_arr);
    }
    join_graphics_update_box(changedArea.surrounding_box());
    changedArea = null;
  }

  /**
   * Checks if a rectangular boxed trace line segment with the input parameters can be inserted
   * without conflict. If a conflict exists, The result length is the maximal line length from
   * p_line.a to p_line.b, which can be inserted without conflict (Integer.MAX_VALUE, if no conflict
   * exists). If p_only_not_shovable_obstacles, unfixed traces and vias are ignored.
   */
  public double check_trace_segment(
      Point p_from_point,
      Point p_to_point,
      int p_layer,
      int[] p_net_no_arr,
      int p_trace_half_width,
      int p_cl_class_no,
      boolean p_only_not_shovable_obstacles) {
    if (p_from_point.equals(p_to_point)) {
      return 0;
    }
    Polyline currPolyline = new Polyline(p_from_point, p_to_point);
    LineSegment currLineSegment = new LineSegment(currPolyline, 1);
    return check_trace_segment(
        currLineSegment,
        p_layer,
        p_net_no_arr,
        p_trace_half_width,
        p_cl_class_no,
        p_only_not_shovable_obstacles);
  }

  /**
   * Checks if a trace shape around the input parameters can be inserted without conflict. If a
   * conflict exists, The result length is the maximal line length from p_line.a to p_line.b, which
   * can be inserted without conflict (Integer.MAX_VALUE, if no conflict exists). If
   * p_only_not_shovable_obstacles, unfixed traces and vias are ignored.
   */
  public double check_trace_segment(
      LineSegment p_line_segment,
      int p_layer,
      int[] p_net_no_arr,
      int p_trace_half_width,
      int p_cl_class_no,
      boolean p_only_not_shovable_obstacles) {
    Polyline checkPolyline = p_line_segment.to_polyline();
    if (checkPolyline.arr.length != 3) {
      return 0;
    }
    TileShape shapeToCheck = checkPolyline.offset_shape(p_trace_half_width, 0);
    FloatPoint fromPoint = p_line_segment.start_point_approx();
    FloatPoint toPoint = p_line_segment.end_point_approx();
    double lineLength = toPoint.distance(fromPoint);
    double okLength = Integer.MAX_VALUE;
    ShapeSearchTree defaultTree = this.searchTreeManager.get_default_tree();

    Collection<TreeEntry> obstacleEntries =
        defaultTree.overlapping_tree_entries_with_clearance(
            shapeToCheck, p_layer, p_net_no_arr, p_cl_class_no);

    for (TreeEntry curr_obstacle_entry : obstacleEntries) {

      if (!(curr_obstacle_entry.object instanceof Item curr_obstacle)) {
        continue;
      }
      if (p_only_not_shovable_obstacles
          && curr_obstacle.is_routable()
          && !curr_obstacle.is_shove_fixed()) {
        continue;
      }
      TileShape currObstacleShape =
          curr_obstacle_entry.object.get_tree_shape(
              defaultTree, curr_obstacle_entry.shapeIndexInObject);
      TileShape currOffsetShape;
      FloatPoint nearestObstaclePoint;
      double shortenValue;
      if (defaultTree.is_clearance_compensation_used()) {
        currOffsetShape = shapeToCheck;
        shortenValue =
            p_trace_half_width
                + rules.clearanceMatrix.clearance_compensation_value(
                    curr_obstacle.clearance_class_no(), p_layer);
      } else {
        int clearanceValue =
            this.clearance_value(curr_obstacle.clearance_class_no(), p_cl_class_no, p_layer);
        currOffsetShape = (TileShape) shapeToCheck.offset(clearanceValue);
        shortenValue = p_trace_half_width + clearanceValue;
      }
      TileShape intersection = currObstacleShape.intersection(currOffsetShape);
      if (intersection.is_empty()) {
        continue;
      }
      nearestObstaclePoint = intersection.nearest_point_approx(fromPoint);

      double projection = fromPoint.scalar_product(toPoint, nearestObstaclePoint) / lineLength;

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
   * Checks, if p_item can be translated by p_vector without producing overlaps or clearance
   * violations.
   */
  public boolean check_move_item(Item p_item, Vector p_vector, Collection<Item> p_ignore_items) {
    int netCount = p_item.netNoArr.length;
    if (netCount > 1) {
      return false; // not yet implemented
    }
    int contactCount = 0;
    // the connected items must remain connected after moving
    if (p_item instanceof Connectable) {
      contactCount = p_item.get_all_contacts().size();
    }
    if (p_item instanceof Trace && contactCount > 0) {
      return false;
    }
    if (p_ignore_items != null) {
      p_ignore_items.add(p_item);
    }
    for (int i = 0; i < p_item.tile_shape_count(); i++) {
      TileShape movedShape = (TileShape) p_item.get_tile_shape(i).translate_by(p_vector);
      if (!movedShape.is_contained_in(boundingBox)) {
        return false;
      }
      Set<Item> obstacles =
          this.overlapping_items_with_clearance(
              movedShape, p_item.shape_layer(i), p_item.netNoArr, p_item.clearance_class_no());
      for (Item currItem : obstacles) {
        if (p_ignore_items != null) {
          if (!p_ignore_items.contains(currItem)) {
            if (currItem.is_obstacle(p_item)) {
              return false;
            }
          }
        } else if (currItem != p_item) {
          if (currItem.is_obstacle(p_item)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /** Checks, if the net number of p_item can be changed without producing clearance violations. */
  public boolean check_change_net(Item p_item, int p_new_net_no) {
    int[] netNoArr = new int[1];
    netNoArr[0] = p_new_net_no;
    for (int i = 0; i < p_item.tile_shape_count(); i++) {
      TileShape currShape = p_item.get_tile_shape(i);
      Set<Item> obstacles =
          this.overlapping_items_with_clearance(
              currShape, p_item.shape_layer(i), netNoArr, p_item.clearance_class_no());
      for (SearchTreeObject currOb : obstacles) {
        if (currOb != p_item
            && currOb instanceof Connectable connectable
            && !connectable.contains_net(p_new_net_no)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Translates p_drill_item by p_vector and shoves obstacle traces aside. Returns false, if that
   * was not possible without creating clearance violations. In this case the database may be
   * damaged, so that an undo becomes necessary.
   */
  public boolean move_drill_item(
      DrillItem p_drill_item,
      Vector p_vector,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      int p_tidy_width,
      int p_pull_tight_accuracy,
      int p_pull_tight_time_limit) {
    clear_shove_failing_obstacle();
    // unfix the connected shove fixed traces.
    Collection<Item> contactList = p_drill_item.get_normal_contacts();
    for (Item currContact : contactList) {
      if (currContact.get_fixed_state() == FixedState.SHOVE_FIXED) {
        currContact.set_fixed_state(FixedState.UNFIXED);
      }
    }

    IntOctagon tidyRegion;
    boolean calculateTidyRegion;
    if (p_tidy_width < Integer.MAX_VALUE) {
      tidyRegion = IntOctagon.EMPTY;
      calculateTidyRegion = p_tidy_width > 0;
    } else {
      tidyRegion = null;
      calculateTidyRegion = false;
    }
    int[] netNoArr = p_drill_item.netNoArr;
    start_marking_changed_area();
    if (!MoveDrillItemAlgo.insert(
        p_drill_item,
        p_vector,
        p_max_recursion_depth,
        p_max_via_recursion_depth,
        tidyRegion,
        this)) {
      return false;
    }
    if (calculateTidyRegion) {
      tidyRegion = tidyRegion.enlarge(p_tidy_width);
    }
    int[] optNetNoArr;
    if (p_max_recursion_depth <= 0) {
      optNetNoArr = netNoArr;
    } else {
      optNetNoArr = new int[0];
    }
    opt_changed_area(
        optNetNoArr, tidyRegion, p_pull_tight_accuracy, null, null, p_pull_tight_time_limit);
    return true;
  }

  /**
   * Checks, if there is an item nearby sharing a net with p_net_no_arr, from where a routing can
   * start, or where the routing can connect to. If p_from_item != null, items, which are connected
   * to p_from_item, are ignored. Returns null, if no item is found, If p_layer {@literal <} 0, the
   * layer is ignored
   */
  public Item pick_nearest_routing_item(Point p_location, int p_layer, Item p_from_item) {
    TileShape pointShape = TileShape.get_instance(p_location);
    Collection<Item> foundItems = overlapping_items(pointShape, p_layer);
    FloatPoint pickLocation = p_location.to_float();
    double minDist = Integer.MAX_VALUE;
    Item nearestItem = null;
    Set<Item> ignoreSet = null;
    for (Item currItem : foundItems) {
      if (!currItem.is_connectable()) {
        continue;
      }
      boolean candidateFound = false;
      double currDist = 0;
      if (currItem instanceof PolylineTrace currTrace) {
        if (p_layer < 0 || currTrace.get_layer() == p_layer) {
          if (nearestItem instanceof DrillItem) {
            continue; // prefer drill items
          }
          int traceRadius = currTrace.get_half_width();
          currDist = currTrace.polyline().distance(pickLocation);
          if (currDist < minDist && currDist <= traceRadius) {
            candidateFound = true;
          }
        }
      } else if (currItem instanceof DrillItem curr_drill_item) {
        if (p_layer < 0 || curr_drill_item.is_on_layer(p_layer)) {
          FloatPoint drillItemCenter = curr_drill_item.get_center().to_float();
          currDist = drillItemCenter.distance(pickLocation);
          if (currDist < minDist || nearestItem instanceof Trace) {
            candidateFound = true;
          }
        }
      } else if (currItem instanceof ConductionArea currArea) {
        if ((p_layer < 0 || currArea.get_layer() == p_layer) && nearestItem == null) {
          candidateFound = true;
          currDist = Integer.MAX_VALUE;
        }
      }
      if (candidateFound) {
        if (p_from_item != null) {
          if (ignoreSet == null) {
            // calculated here to avoid unnecessary calculations for performance reasoss.
            ignoreSet = p_from_item.get_connected_set(-1);
          }
          if (ignoreSet.contains(currItem)) {
            continue;
          }
        }
        minDist = currDist;
        nearestItem = currItem;
      }
    }
    return nearestItem;
  }

  /**
   * Shoves aside traces, so that a via with the input parameters can be inserted without clearance
   * violations. If the shove failed, the database may be damaged, so that an undo becomes
   * necessary. Returns false, if the forced via failed.
   */
  public boolean forced_via(
      ViaInfo p_via_info,
      Point p_location,
      int[] p_net_no_arr,
      int p_trace_clearance_class_no,
      int[] p_trace_pen_halfwidth_arr,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      int p_tidy_width,
      int p_pull_tight_accuracy,
      int p_pull_tight_time_limit) {
    clear_shove_failing_obstacle();
    this.start_marking_changed_area();
    boolean result =
        ForcedViaAlgo.insert(
            p_via_info,
            p_location,
            p_net_no_arr,
            p_trace_clearance_class_no,
            p_trace_pen_halfwidth_arr,
            p_max_recursion_depth,
            p_max_via_recursion_depth,
            this);
    if (result) {
      IntOctagon tidyClipShape;
      if (p_tidy_width < Integer.MAX_VALUE) {
        tidyClipShape = p_location.surrounding_octagon().enlarge(p_tidy_width);
      } else {
        tidyClipShape = null;
      }
      int[] optNetNoArr;
      if (p_max_recursion_depth <= 0) {
        optNetNoArr = p_net_no_arr;
      } else {
        optNetNoArr = new int[0];
      }
      this.opt_changed_area(
          optNetNoArr, tidyClipShape, p_pull_tight_accuracy, null, null, p_pull_tight_time_limit);
    }
    return result;
  }

  /**
   * Tries to insert a trace line with the input parameters from p_from_corner to p_to_corner while
   * shoving aside obstacle traces and vias. Returns the last point between p_from_corner and
   * p_to_corner, to which the shove succeeded. Returns null, if the check was inaccurate and an
   * error occurred while inserting, so that the database may be damaged and an undo necessary.
   * p_search_tree is the shape search tree used in the algorithm.
   */
  public Point insert_forced_trace_segment(
      Point p_from_corner,
      Point p_to_corner,
      int p_half_width,
      int p_layer,
      int[] p_net_no_arr,
      int p_clearance_class_no,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      int p_max_spring_over_recursion_depth,
      int p_tidy_width,
      int p_pull_tight_accuracy,
      boolean p_with_check,
      TimeLimit p_time_limit) {
    if (p_from_corner.equals(p_to_corner)) {
      return p_to_corner;
    }
    Polyline insertPolyline = new Polyline(p_from_corner, p_to_corner);
    Point okPoint =
        insert_forced_trace_polyline(
            insertPolyline,
            p_half_width,
            p_layer,
            p_net_no_arr,
            p_clearance_class_no,
            p_max_recursion_depth,
            p_max_via_recursion_depth,
            p_max_spring_over_recursion_depth,
            p_tidy_width,
            p_pull_tight_accuracy,
            p_with_check,
            p_time_limit);
    Point result;
    if (okPoint == insertPolyline.first_corner()) {
      result = p_from_corner;
    } else if (okPoint == insertPolyline.last_corner()) {
      result = p_to_corner;
    } else {
      result = okPoint;
    }
    return result;
  }

  /**
   * Checks, if a trace polyline with the input parameters can be inserted while shoving aside
   * obstacle traces and vias.
   */
  public boolean check_forced_trace_polyline(
      Polyline p_polyline,
      int p_half_width,
      int p_layer,
      int[] p_net_no_arr,
      int p_clearance_class_no,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      int p_max_spring_over_recursion_depth) {
    ShapeSearchTree searchTree = searchTreeManager.get_default_tree();
    int compensatedHalfWidth =
        p_half_width + searchTree.clearance_compensation_value(p_clearance_class_no, p_layer);
    TileShape[] traceShapes =
        p_polyline.offset_shapes(compensatedHalfWidth, 0, p_polyline.arr.length - 1);
    boolean orthogonalMode = rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE;
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(this);
    for (int i = 0; i < traceShapes.length; i++) {
      TileShape currTraceShape = traceShapes[i];
      if (orthogonalMode) {
        currTraceShape = currTraceShape.bounding_box();
      }
      CalcFromSide fromSide = new CalcFromSide(p_polyline, i + 1, currTraceShape);

      boolean checkShoveOk =
          shoveTraceAlgo.check(
              currTraceShape,
              fromSide,
              null,
              p_layer,
              p_net_no_arr,
              p_clearance_class_no,
              p_max_recursion_depth,
              p_max_via_recursion_depth,
              p_max_spring_over_recursion_depth,
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
  public Point insert_forced_trace_polyline(
      Polyline p_polyline,
      int p_half_width,
      int p_layer,
      int[] p_net_no_arr,
      int p_clearance_class_no,
      int p_max_recursion_depth,
      int p_max_via_recursion_depth,
      int p_max_spring_over_recursion_depth,
      int p_tidy_width,
      int p_pull_tight_accuracy,
      boolean p_with_check,
      TimeLimit p_time_limit) {
    clear_shove_failing_obstacle();
    Point fromCorner = p_polyline.first_corner();
    Point toCorner = p_polyline.last_corner();
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
    start_marking_changed_area();
    // Check, if there ends an item of the same net at p_from_corner.
    // If so, its geometry will be used to cut off dog ears of the check shape.
    Trace pickedTrace = null;
    ItemSelectionFilter filter =
        new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
    Set<Item> pickedItems = this.pick_items(fromCorner, p_layer, filter);
    if (p_net_no_arr != null && p_net_no_arr.length > 0) {
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + p_net_no_arr[0]
              + ", step=start, pickedSize="
              + pickedItems.size()
              + ", from="
              + fromCorner
              + ", to="
              + toCorner
              + ", idMax="
              + communication.idNoGenerator.max_generated_no());
    }
    if (pickedItems.size() == 1) {
      Trace currPickedTrace = (Trace) pickedItems.iterator().next();
      if (currPickedTrace.nets_equal(p_net_no_arr)
          && currPickedTrace.get_half_width() == p_half_width
          && currPickedTrace.clearance_class_no() == p_clearance_class_no
          && (currPickedTrace instanceof PolylineTrace)) {
        // can combine with the picked trace
        pickedTrace = currPickedTrace;
      }
    }
    ShapeSearchTree searchTree = searchTreeManager.get_default_tree();
    int compensatedHalfWidth =
        p_half_width + searchTree.clearance_compensation_value(p_clearance_class_no, p_layer);
    ShoveTraceAlgo shoveTraceAlgo = new ShoveTraceAlgo(this);
    Polyline newPolyline =
        shoveTraceAlgo.spring_over_obstacles(
            p_polyline, compensatedHalfWidth, p_layer, p_net_no_arr, p_clearance_class_no, null);
    if (newPolyline == null) {
      if (p_net_no_arr != null && p_net_no_arr.length > 0 && p_net_no_arr[0] == 94) {
        FRLogger.trace(
            "RoutingBoard.insert_forced_trace_polyline",
            "compare_trace_insert_forced_fail",
            "spring_over_obstacles returned null",
            "Net #" + p_net_no_arr[0] + ",Layer #" + p_layer,
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
    if (combinedPolyline.arr.length < 3) {
      if (p_net_no_arr != null && p_net_no_arr.length > 0 && p_net_no_arr[0] == 94) {
        FRLogger.trace(
            "RoutingBoard.insert_forced_trace_polyline",
            "compare_trace_insert_forced_fail",
            "combinedPolyline.arr.length < 3",
            "Net #" + p_net_no_arr[0] + ",Layer #" + p_layer,
            new Point[] {fromCorner, toCorner});
      }
      return fromCorner;
    }
    int startShapeNo = combinedPolyline.arr.length - newPolyline.arr.length;
    // calculate the last shapes of combinedPolyline for checking
    TileShape[] traceShapes =
        combinedPolyline.offset_shapes(
            compensatedHalfWidth, startShapeNo, combinedPolyline.arr.length - 1);
    int lastShapeNo = traceShapes.length;
    boolean orthogonalMode = rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE;
    int idBeforeShoveLoop = communication.idNoGenerator.max_generated_no();
    for (int i = 0; i < traceShapes.length; i++) {
      TileShape currTraceShape = traceShapes[i];
      if (orthogonalMode) {
        currTraceShape = currTraceShape.bounding_box();
      }
      CalcFromSide fromSide =
          new CalcFromSide(
              combinedPolyline,
              combinedPolyline.corner_count() - traceShapes.length - 1 + i,
              currTraceShape);
      if (p_with_check) {
        boolean checkShoveOk =
            shoveTraceAlgo.check(
                currTraceShape,
                fromSide,
                null,
                p_layer,
                p_net_no_arr,
                p_clearance_class_no,
                p_max_recursion_depth,
                p_max_via_recursion_depth,
                p_max_spring_over_recursion_depth,
                p_time_limit);
        if (!checkShoveOk) {
          lastShapeNo = i;
          break;
        }
      }
      int idBeforeShove = communication.idNoGenerator.max_generated_no();
      boolean insertOk =
          shoveTraceAlgo.insert(
              currTraceShape,
              fromSide,
              p_layer,
              p_net_no_arr,
              p_clearance_class_no,
              null,
              p_max_recursion_depth,
              p_max_via_recursion_depth,
              p_max_spring_over_recursion_depth);
      int idAfterShove = communication.idNoGenerator.max_generated_no();
      if (p_net_no_arr != null && p_net_no_arr.length > 0) {
        FRLogger.trace(
            "compare_trace_shove_shape net="
                + p_net_no_arr[0]
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
    if (p_net_no_arr != null && p_net_no_arr.length > 0) {
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + p_net_no_arr[0]
              + ", step=after_shove_loop, shoveLoopDelta="
              + (communication.idNoGenerator.max_generated_no() - idBeforeShoveLoop)
              + ", lastShapeNo="
              + lastShapeNo
              + ", traceShapes.length="
              + traceShapes.length
              + ", idMax="
              + communication.idNoGenerator.max_generated_no());
    }
    if (lastShapeNo < traceShapes.length) {
      // the shove with index lastShapeNo failed.
      // Sample the shove line to a shorter shove distance and try again.
      TileShape lastTraceShape = traceShapes[lastShapeNo];
      if (orthogonalMode) {
        lastTraceShape = lastTraceShape.bounding_box();
      }
      int sampleWidth = 2 * this.get_min_trace_half_width();
      FloatPoint lastCorner = newPolyline.corner_approx(lastShapeNo + 1);
      FloatPoint prevLastCorner = newPolyline.corner_approx(lastShapeNo);
      double lastSegmentLength = lastCorner.distance(prevLastCorner);
      if (lastSegmentLength > 100 * sampleWidth) {
        // to many cycles to sample
        if (p_net_no_arr != null && p_net_no_arr.length > 0 && p_net_no_arr[0] == 94) {
          FRLogger.trace(
              "RoutingBoard.insert_forced_trace_polyline",
              "compare_trace_insert_forced_fail",
              "too many cycles to sample",
              "Net #" + p_net_no_arr[0] + ",Layer #" + p_layer,
              new Point[] {fromCorner, toCorner});
        }
        return fromCorner;
      }
      int shapeIndex = combinedPolyline.corner_count() - traceShapes.length - 1 + lastShapeNo;
      if (lastSegmentLength > sampleWidth) {
        newPolyline =
            newPolyline.shorten(
                newPolyline.arr.length - (traceShapes.length - lastShapeNo - 1), sampleWidth);
        Point currLastCorner = newPolyline.last_corner();
        if (!(currLastCorner instanceof IntPoint)) {
          FRLogger.trace("RoutingBoard.insert_forced_trace_polyline: IntPoint expected");
          if (p_net_no_arr != null && p_net_no_arr.length > 0 && p_net_no_arr[0] == 94) {
            FRLogger.trace(
                "RoutingBoard.insert_forced_trace_polyline",
                "compare_trace_insert_forced_fail",
                "currLastCorner is not an IntPoint",
                "Net #" + p_net_no_arr[0] + ",Layer #" + p_layer,
                new Point[] {fromCorner, toCorner});
          }
          return fromCorner;
        }
        newCorner = currLastCorner;
        if (pickedTrace == null) {
          combinedPolyline = newPolyline;
        } else {
          PolylineTrace combineTrace = (PolylineTrace) pickedTrace;
          combinedPolyline = newPolyline.combine(combineTrace.polyline());
        }
        if (combinedPolyline.arr.length < 3) {
          return newCorner;
        }
        shapeIndex = combinedPolyline.arr.length - 3;
        lastTraceShape = combinedPolyline.offset_shape(compensatedHalfWidth, shapeIndex);
        if (orthogonalMode) {
          lastTraceShape = lastTraceShape.bounding_box();
        }
      }
      CalcFromSide fromSide = new CalcFromSide(combinedPolyline, shapeIndex, lastTraceShape);
      boolean checkShoveOk =
          shoveTraceAlgo.check(
              lastTraceShape,
              fromSide,
              null,
              p_layer,
              p_net_no_arr,
              p_clearance_class_no,
              p_max_recursion_depth,
              p_max_via_recursion_depth,
              p_max_spring_over_recursion_depth,
              p_time_limit);
      if (!checkShoveOk) {
        if (p_net_no_arr != null && p_net_no_arr.length > 0 && p_net_no_arr[0] == 94) {
          Item shoveFailingObstacle = this.get_shove_failing_obstacle();
          FRLogger.trace(
              "RoutingBoard.insert_forced_trace_polyline",
              "compare_trace_insert_forced_fail",
              "checkShoveOk returned false",
              "Net #" + p_net_no_arr[0] + ",Layer #" + p_layer,
              new Point[] {fromCorner, toCorner});
          FRLogger.trace(
              "RoutingBoard.insert_forced_trace_polyline",
              "compare_trace_insert_forced_obstacle",
              "failing obstacle=" + shoveFailingObstacle,
              "Net #"
                  + p_net_no_arr[0]
                  + ",Layer #"
                  + p_layer
                  + ",Obstacle="
                  + (shoveFailingObstacle == null
                      ? "null"
                      : shoveFailingObstacle.getClass().getSimpleName()
                          + "#"
                          + shoveFailingObstacle.get_id_no()),
              new Point[] {fromCorner, toCorner});
        }
        return fromCorner;
      }
      boolean insertOk =
          shoveTraceAlgo.insert(
              lastTraceShape,
              fromSide,
              p_layer,
              p_net_no_arr,
              p_clearance_class_no,
              null,
              p_max_recursion_depth,
              p_max_via_recursion_depth,
              p_max_spring_over_recursion_depth);
      if (!insertOk) {
        FRLogger.trace("RoutingBoard.insert_forced_trace_polyline: shove trace failed");
        return null;
      }
    }
    // insert the new trace segment
    for (int i = 0; i < newPolyline.corner_count(); i++) {
      join_changed_area(newPolyline.corner_approx(i), p_layer);
    }
    int idBeforeInsert = communication.idNoGenerator.max_generated_no();
    PolylineTrace newTrace =
        insert_trace_without_cleaning(
            newPolyline,
            p_layer,
            p_half_width,
            p_net_no_arr,
            p_clearance_class_no,
            FixedState.UNFIXED);
    int idAfterInsert = communication.idNoGenerator.max_generated_no();
    boolean combineResult = newTrace.combine();
    int idAfterCombine = communication.idNoGenerator.max_generated_no();
    if (p_net_no_arr != null && p_net_no_arr.length > 0) {
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + p_net_no_arr[0]
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
    if (p_tidy_width < Integer.MAX_VALUE) {
      tidyRegion = newCorner.surrounding_octagon().enlarge(p_tidy_width);
    }
    int[] optNetNoArr;
    if (p_max_recursion_depth <= 0) {
      optNetNoArr = p_net_no_arr;
    } else {
      optNetNoArr = new int[0];
    }
    PullTightAlgo pullTightAlgo =
        PullTightAlgo.get_instance(
            this, optNetNoArr, tidyRegion, p_pull_tight_accuracy, null, -1, newCorner, p_layer);

    try {
      // Remove evtl. generated cycles because otherwise pullTight may not work
      // correctly.
      int idBeforeNorm = communication.idNoGenerator.max_generated_no();
      boolean normalizeResult =
          newTrace != null && newTrace.normalize(changedArea.get_area(p_layer));
      int idAfterNorm = communication.idNoGenerator.max_generated_no();
      if (p_net_no_arr != null && p_net_no_arr.length > 0) {
        FRLogger.trace(
            "compare_trace_insert_forced_sub net="
                + p_net_no_arr[0]
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

        int idBeforeSplit = communication.idNoGenerator.max_generated_no();
        pullTightAlgo.split_traces_at_keep_point();
        int idAfterSplit = communication.idNoGenerator.max_generated_no();
        if (p_net_no_arr != null && p_net_no_arr.length > 0) {
          FRLogger.trace(
              "compare_trace_insert_forced_sub net="
                  + p_net_no_arr[0]
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
        Set<Item> currPickedItems = this.pick_items(newCorner, p_layer, itemFilter);
        newTrace = null;
        if (!currPickedItems.isEmpty()) {
          Item foundTrace = currPickedItems.iterator().next();
          if (foundTrace instanceof PolylineTrace trace) {
            newTrace = trace;
          }
        }
      }
    } catch (Exception e) {
      // Max normalization depth is hit for geometrically complex or degenerate trace segments.
      // The router skips the segment and continues; affected connections may remain unrouted.
      FRLogger.trace(
          "RoutingBoard.insert_forced_trace_polyline: A trace could not be normalized and was skipped. Cause: "
              + e.getMessage());
    }

    // To avoid, that a separate handling for moving backwards in the own trace line
    // becomes necessary, pull tight is called here.
    if (p_net_no_arr != null && p_net_no_arr.length > 0) {
      ItemSelectionFilter _dbg_filter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
      Set<Item> _dbg_before = this.pick_items(newCorner, p_layer, _dbg_filter);
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + p_net_no_arr[0]
              + ", step=before_pull_tight, pickedAtEndCorner="
              + _dbg_before.size()
              + ", new_trace_null="
              + (newTrace == null)
              + ", newCorner="
              + newCorner);
    }
    if (p_tidy_width > 0 && newTrace != null) {
      newTrace.pull_tight(pullTightAlgo);
    }
    if (p_net_no_arr != null && p_net_no_arr.length > 0) {
      ItemSelectionFilter _dbg_filter =
          new ItemSelectionFilter(ItemSelectionFilter.SelectableChoices.TRACES);
      Set<Item> _dbg_after = this.pick_items(newCorner, p_layer, _dbg_filter);
      FRLogger.trace(
          "compare_trace_insert_forced_sub net="
              + p_net_no_arr[0]
              + ", step=after_pull_tight, pickedAtEndCorner="
              + _dbg_after.size()
              + ", newCorner="
              + newCorner);
    }
    return newCorner;
  }

  /**
   * Initialises the auto-route database for routing a connection. If p_retain_autoroute_database,
   * the auto-route database is retained and maintained after the algorithm for performance reasons.
   */
  public AutorouteEngine init_autoroute(
      int p_net_no,
      int p_trace_clearance_class_no,
      Stoppable p_stoppable_thread,
      TimeLimit p_time_limit,
      boolean p_retain_autoroute_database) {
    if (this.autorouteEngine == null
        || !p_retain_autoroute_database
        || this.autorouteEngine.autorouteSearchTree.compensatedClearanceClassNo
            != p_trace_clearance_class_no) {
      this.autorouteEngine =
          new AutorouteEngine(this, p_trace_clearance_class_no, p_retain_autoroute_database);
    }
    this.autorouteEngine.init_connection(p_net_no, p_stoppable_thread, p_time_limit);
    return this.autorouteEngine;
  }

  /** Clears the auto-route database in case it was retained. */
  public void finish_autoroute() {
    if (this.autorouteEngine != null) {
      this.autorouteEngine.clear();
    }
    this.autorouteEngine = null;
  }

  /**
   * Routes automatically p_item to another item of the same net, to which it is not yet
   * electrically connected. Returns an enum of type AutorouteAttemptState
   */
  public AutorouteAttemptResult autoroute(
      Item p_item,
      RouterSettings routerSettings,
      int p_via_costs,
      Stoppable p_stoppable_thread,
      TimeLimit p_time_limit) {
    if (!(p_item instanceof Connectable) || p_item.net_count() == 0) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.NO_CONNECTIONS, "The item '" + p_item + "' is not connectable.");
    }
    if (p_item.net_count() > 1) {
      FRLogger.warn("RoutingBoard.autoroute: netCount > 1 not yet implemented");
    }
    int routeNetNo = p_item.get_net_no(0);
    AutorouteControl ctrlSettings =
        new AutorouteControl(
            this, routeNetNo, routerSettings, p_via_costs, routerSettings.get_trace_cost_arr());
    ctrlSettings.removeUnconnectedVias = false;
    Set<Item> routeStartSet = p_item.get_connected_set(routeNetNo);
    Net routeNet = rules.nets.get(routeNetNo);
    if (routeNet != null && routeNet.contains_plane()) {
      for (Item currItem : routeStartSet) {
        if (currItem instanceof ConductionArea) {
          return new AutorouteAttemptResult(
              AutorouteAttemptState.CONNECTED_TO_PLANE,
              "The item '" + currItem + "' is connected to a plane.");
        }
      }
    }
    Set<Item> routeDestSet = p_item.get_unconnected_set(routeNetNo);
    if (routeDestSet.isEmpty()) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.ALREADY_CONNECTED,
          "The item '" + p_item + "' is already connected.");
    }
    SortedSet<Item> rippedItemList = new TreeSet<>();
    AutorouteEngine currAutorouteEngine =
        init_autoroute(
            p_item.get_net_no(0),
            ctrlSettings.traceClearanceClassNo,
            p_stoppable_thread,
            p_time_limit,
            false);
    AutorouteAttemptResult result =
        currAutorouteEngine.autoroute_connection(
            routeStartSet,
            routeDestSet,
            ctrlSettings,
            rippedItemList,
            null); // null: costs not needed here
    if (result.state == AutorouteAttemptState.ROUTED) {
      final int timeLimitToPreventEndlessLoop = 1000;
      opt_changed_area(
          new int[] {routeNetNo},
          null,
          routerSettings.tracePullTightAccuracy,
          ctrlSettings.traceCosts,
          p_stoppable_thread,
          timeLimitToPreventEndlessLoop);
    }
    return result;
  }

  /**
   * Autoroutes from the input pin until the first via, in case the pin and its connected set has
   * only 1 layer. Ripup is allowed if p_ripup_costs is {@literal >}= 0. Returns an enum of type
   * AutorouteEngine.AutorouteResult
   */
  public AutorouteAttemptResult fanout(
      Pin p_pin,
      RouterSettings routerSettings,
      int p_ripup_costs,
      Stoppable p_stoppable_thread,
      TimeLimit p_time_limit) {
    if (p_pin.first_layer() != p_pin.last_layer() || p_pin.net_count() != 1) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.ALREADY_CONNECTED, "The pin '" + p_pin + "' is already connected.");
    }
    int pinNetNo = p_pin.get_net_no(0);
    int pinLayer = p_pin.first_layer();
    Set<Item> pinConnectedSet = p_pin.get_connected_set(pinNetNo);
    for (Item currItem : pinConnectedSet) {
      if (currItem.first_layer() != pinLayer || currItem.last_layer() != pinLayer) {
        return new AutorouteAttemptResult(
            AutorouteAttemptState.ALREADY_CONNECTED,
            "The pin '" + p_pin + "' is already connected.");
      }
    }
    Set<Item> unconnectedSet = p_pin.get_unconnected_set(pinNetNo);
    if (unconnectedSet.isEmpty()) {
      return new AutorouteAttemptResult(
          AutorouteAttemptState.NO_UNCONNECTED_NETS,
          "The pin '" + p_pin + "' is already connected.");
    }
    app.freerouting.geometry.planar.FloatPoint pinCenter = p_pin.get_center().to_float();
    java.util.List<Item> sortedUnconnectedList = new java.util.ArrayList<>(unconnectedSet);
    sortedUnconnectedList.sort(
        (item1, item2) -> {
          app.freerouting.geometry.planar.IntBox box1 = item1.bounding_box();
          double cx1 = (box1.ll.x + box1.ur.x) / 2.0;
          double cy1 = (box1.ll.y + box1.ur.y) / 2.0;
          double dx1 = cx1 - pinCenter.x;
          double dy1 = cy1 - pinCenter.y;
          double distSq1 = dx1 * dx1 + dy1 * dy1;

          app.freerouting.geometry.planar.IntBox box2 = item2.bounding_box();
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
      for (int i = 0; i < ctrlSettings.viaRule.via_count(); i++) {
        combinedViaRule.append_via(ctrlSettings.viaRule.get_via(i));
      }
      if (!this.rules.viaRules.isEmpty()) {
        app.freerouting.rules.ViaRule defaultViaRule = this.rules.viaRules.firstElement();
        for (int i = 0; i < defaultViaRule.via_count(); i++) {
          app.freerouting.rules.ViaInfo defaultVia = defaultViaRule.get_via(i);
          if (!combinedViaRule.contains(defaultVia)) {
            combinedViaRule.append_via(defaultVia);
          }
        }
      }
      ctrlSettings.viaRule = combinedViaRule;
      ctrlSettings.rebuild_via_info(this, routerSettings.get_via_costs(), pinNetNo);
    }
    Component pinComponent = this.components.get(p_pin.get_component_no());
    if (pinComponent != null && p_pin.name() != null) {
      ctrlSettings.fanoutStartPinName = pinComponent.name + "-" + p_pin.name();
    } else {
      ctrlSettings.fanoutStartPinName = p_pin.toString();
    }
    ctrlSettings.fanoutStartPinCenter = p_pin.get_center();
    ctrlSettings.fanoutStartPinLayer = p_pin.first_layer();
    ctrlSettings.removeUnconnectedVias = false;
    if (p_ripup_costs >= 0) {
      ctrlSettings.ripupAllowed = true;
      ctrlSettings.ripupCosts = p_ripup_costs;
    }
    SortedSet<Item> rippedItemList = new TreeSet<>();
    AutorouteEngine currAutorouteEngine =
        init_autoroute(
            pinNetNo, ctrlSettings.traceClearanceClassNo, p_stoppable_thread, p_time_limit, false);

    AutorouteAttemptResult result = null;
    if (sortedUnconnectedList.size() <= 4) {
      if (!sortedUnconnectedList.isEmpty()) {
        // 1. Try to route to the closest target first
        Item closestTarget = sortedUnconnectedList.get(0);
        result =
            currAutorouteEngine.autoroute_connection(
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
              currAutorouteEngine.autoroute_connection(
                  pinConnectedSet, unconnectedSet, ctrlSettings, rippedItemList, null);
        }
      }
    } else {
      // For large nets (e.g. power/ground/buses), route to the entire unconnected set at once to
      // avoid CPU thrashing
      result =
          currAutorouteEngine.autoroute_connection(
              pinConnectedSet, unconnectedSet, ctrlSettings, rippedItemList, null);
    }
    if (result == null) {
      result =
          new AutorouteAttemptResult(
              AutorouteAttemptState.FAILED, "No target items to route connection.");
    }

    if (result.state == AutorouteAttemptState.ROUTED) {
      final int timeLimitToPreventEndlessLoop = 1000;
      opt_changed_area(
          new int[] {pinNetNo},
          null,
          routerSettings.tracePullTightAccuracy,
          ctrlSettings.traceCosts,
          p_stoppable_thread,
          timeLimitToPreventEndlessLoop);
    }
    return result;
  }

  /**
   * Inserts a trace from p_from_point to the nearest point on p_to_trace. Returns false, if that is
   * not possible without clearance violation.
   */
  public boolean connect_to_trace(
      IntPoint p_from_point, Trace p_to_trace, int p_pen_half_width, int p_cl_type) {

    Point firstCorner = p_to_trace.first_corner();

    Point lastCorner = p_to_trace.last_corner();

    int[] netNoArr = p_to_trace.netNoArr;

    if (!(p_to_trace instanceof PolylineTrace to_trace)) {
      return false; // not yet implemented
    }
    if (to_trace.polyline().contains(p_from_point)) {
      // no connection line necessary
      return true;
    }
    LineSegment projectionLine = to_trace.polyline().projection_line(p_from_point);
    if (projectionLine == null) {
      return false;
    }
    Polyline connectionLine = projectionLine.to_polyline();
    if (connectionLine == null || connectionLine.arr.length != 3) {
      return false;
    }
    int traceLayer = p_to_trace.get_layer();
    if (!this.check_polyline_trace(
        connectionLine, traceLayer, p_pen_half_width, p_to_trace.netNoArr, p_cl_type)) {
      return false;
    }
    if (this.changedArea != null) {
      for (int i = 0; i < connectionLine.corner_count(); i++) {
        this.changedArea.join(connectionLine.corner_approx(i), traceLayer);
      }
    }

    this.insert_trace(
        connectionLine, traceLayer, p_pen_half_width, netNoArr, p_cl_type, FixedState.UNFIXED);

    if (!p_from_point.equals(firstCorner)) {
      Trace tail = this.get_trace_tail(firstCorner, traceLayer, netNoArr);
      if (tail != null && !tail.is_user_fixed()) {
        this.remove_item(tail);
      }
    }
    if (!p_from_point.equals(lastCorner)) {
      Trace tail = this.get_trace_tail(lastCorner, traceLayer, netNoArr);
      if (tail != null && !tail.is_user_fixed()) {
        this.remove_item(tail);
      }
    }
    return true;
  }

  /**
   * Checks, if the list p_items contains traces, which have no contact at their start or end point.
   * Trace with net number p_except_net_no are ignored.
   */
  public boolean contains_trace_tails(Collection<Item> p_items, int[] p_except_net_no_arr) {
    for (Item currOb : p_items) {
      if (currOb instanceof Trace currTrace) {
        if (!currTrace.nets_equal(p_except_net_no_arr)) {
          if (currTrace.is_tail()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Removes all trace tails of the input net. If p_net_no {@literal <}= 0, the tails of all nets
   * are removed. Returns true, if something was removed.
   */
  public boolean remove_trace_tails(
      int p_net_no, Item.StopConnectionOption p_stop_connection_option) {
    SortedSet<Item> stubSet = new TreeSet<>();
    Collection<Item> boardItems = this.get_items();
    for (Item currItem : boardItems) {
      if (!currItem.is_routable()) {
        continue;
      }
      if (currItem.net_count() != 1) {
        continue;
      }
      if (p_net_no > 0 && currItem.get_net_no(0) != p_net_no) {
        continue;
      }
      if (currItem.is_tail()) {
        if (currItem instanceof Via) {
          if (p_stop_connection_option == Item.StopConnectionOption.VIA) {
            continue;
          }
          if (p_stop_connection_option == Item.StopConnectionOption.FANOUT_VIA) {
            if (currItem.is_fanout_via(null)) {
              continue;
            }
          }
        }
        stubSet.add(currItem);
      }
    }
    SortedSet<Item> stubConnections = new TreeSet<>();
    for (Item currItem : stubSet) {
      int itemContactCount = currItem.get_normal_contacts().size();
      if (itemContactCount == 1) {
        stubConnections.addAll(currItem.get_connection_items(p_stop_connection_option));
      } else {
        // the connected items are no stubs for example if a via is only connected on 1
        // layer,
        // but to several traces.
        stubConnections.add(currItem);
      }
    }
    if (stubConnections.isEmpty()) {
      return false;
    }
    this.remove_items(stubConnections);
    this.combine_traces(p_net_no);
    return true;
  }

  public void clear_all_item_temporary_autoroute_data() {
    Iterator<UndoableObjects.UndoableObjectNode> it = this.itemList.start_read_object();
    for (; ; ) {
      Item currItem = (Item) itemList.read_object(it);
      if (currItem == null) {
        break;
      }
      currItem.clear_autoroute_info();
    }
  }

  /** Sets, if all conduction areas on the board are obstacles for route of foreign nets. */
  public void change_conduction_is_obstacle(boolean p_value) {
    if (this.rules.get_ignore_conduction() != p_value) {
      return; // no multiply
    }
    boolean somethingChanged = false;
    // Change the isObstacle property of all conduction areas of the board.
    Iterator<UndoableObjects.UndoableObjectNode> it = itemList.start_read_object();
    for (; ; ) {
      Item currItem = (Item) itemList.read_object(it);
      if (currItem == null) {
        break;
      }
      if (currItem instanceof ConductionArea curr_conduction_area) {
        Layer currLayer = layerStructure.arr[curr_conduction_area.get_layer()];
        if (currLayer.isSignal && curr_conduction_area.get_is_obstacle() != p_value) {
          curr_conduction_area.set_is_obstacle(p_value);
          somethingChanged = true;
        }
      }
    }
    this.rules.set_ignore_conduction(!p_value);
    if (somethingChanged) {
      this.searchTreeManager.reinsert_tree_items();
    }
  }

  /**
   * Tries to educe the nets of traces and vias, so that the nets are a subset of the nets of the
   * contact items. This is applied to traces and vias with more than 1 net connected to tie pins.
   * Returns true, if the nets of some items were reduced.
   */
  public boolean reduce_nets_of_route_items() {
    boolean result = false;
    boolean somethingChanged = true;
    while (somethingChanged) {
      somethingChanged = false;
      Iterator<UndoableObjects.UndoableObjectNode> it = itemList.start_read_object();
      for (; ; ) {
        UndoableObjects.Storable currOb = itemList.read_object(it);
        if (currOb == null) {
          break;
        }
        Item currItem = (Item) currOb;
        if (currItem.netNoArr.length <= 1
            || currItem.get_fixed_state() == FixedState.SYSTEM_FIXED) {
          continue;
        }
        if (currOb instanceof Via) {
          Collection<Item> contacts = currItem.get_normal_contacts();
          for (int currNetNo : currItem.netNoArr) {
            for (Item currContact : contacts) {
              if (!currContact.contains_net(currNetNo)) {
                currItem.remove_from_net(currNetNo);
                somethingChanged = true;
                break;
              }
            }
            if (somethingChanged) {
              break;
            }
          }

        } else if (currOb instanceof Trace currTrace) {
          Collection<Item> contacts = currTrace.get_start_contacts();
          for (int i = 0; i < 2; i++) {
            for (int currNetNo : currItem.netNoArr) {
              boolean pinFound = false;
              for (Item currContact : contacts) {
                if (currContact instanceof Pin) {
                  pinFound = true;
                  if (!currContact.contains_net(currNetNo)) {
                    currItem.remove_from_net(currNetNo);
                    somethingChanged = true;
                    break;
                  }
                }
              }
              if (!pinFound) // at tie pins traces may have different nets
              {
                for (Item currContact : contacts) {
                  if (!(currContact instanceof Pin) && !currContact.contains_net(currNetNo)) {
                    currItem.remove_from_net(currNetNo);
                    somethingChanged = true;
                    break;
                  }
                }
              }
            }
            if (somethingChanged) {
              break;
            }
            contacts = currTrace.get_end_contacts();
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
  public Item get_shove_failing_obstacle() {
    return shoveFailingObstacle;
  }

  void set_shove_failing_obstacle(Item p_item) {
    shoveFailingObstacle = p_item;
  }

  public int get_shove_failing_layer() {
    return shoveFailingLayer;
  }

  void set_shove_failing_layer(int p_layer) {
    shoveFailingLayer = p_layer;
  }

  private void clear_shove_failing_obstacle() {
    shoveFailingObstacle = null;
    shoveFailingLayer = -1;
  }

  /**
   * Returns, if the auto-route database is maintained outside the auto-route algorithm while
   * changing items on rhe board.
   */
  boolean is_maintaining_autoroute_database() {
    return this.autorouteEngine != null;
  }

  /**
   * Sets, if the auto-route database has to be maintained outside the auto-route algorithm while
   * changing items on rhe board.
   */
  void set_maintaining_autoroute_database(boolean p_value) {
    if (p_value) {

    } else {
      this.autorouteEngine = null;
    }
  }

  public BoardStatistics get_statistics() {
    return new BoardStatistics(this);
  }

  /**
   * Create a deep copy of the routing board. This method is similar to the BasicBoard.clone method,
   * but it copies the routing related values as well.
   */
  public synchronized RoutingBoard deepCopy() {
    ObjectOutputStream oos = null;
    ObjectInputStream ois = null;

    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      oos = new ObjectOutputStream(bos);

      oos.writeObject(this); // serialize this.board
      oos.flush();

      ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
      ois = new ObjectInputStream(bin);

      RoutingBoard boardCopy = (RoutingBoard) ois.readObject();

      // boardCopy.clear_autoroute_database();
      boardCopy.clear_all_item_temporary_autoroute_data();
      boardCopy.finish_autoroute();

      return boardCopy;
    } catch (Exception e) {
      FRLogger.error("Exception in deep_copy_routing_board" + e, e);
      return null;
    } finally {
      try {
        if (oos != null) {
          oos.close();
        }
        if (ois != null) {
          ois.close();
        }
      } catch (Exception _) {
      }
    }
  }
}

package app.freerouting.autoroute;

import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.board.*;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.RatsNest;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;

import java.util.*;

/**
 * Handles the sequencing of the auto-router passes.
 */
public class BatchAutorouterThread extends StoppableThread
{
  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;

  protected final transient List<BoardUpdatedEventListener> boardUpdatedEventListeners = new ArrayList<>();

  private final RoutingBoard board;
  private final boolean remove_unconnected_vias;
  private final AutorouteControl.ExpansionCostFactor[] trace_cost_arr;
  private final boolean retain_autoroute_database;
  private final int start_ripup_costs;
  private final int trace_pull_tight_accuracy;
  private final RouterSettings settings;
  private final List<Item> autorouteItemList;
  private final int passNo;
  private final boolean useSlowAlgorithm;

  public FloatLine latest_air_line;

  public BatchAutorouterThread(RoutingBoard board, List<Item> autorouteItemList, int passNo, boolean useSlowAlgorithm, RouterSettings routerSettings, int startRipupCosts, int tracePullTightAccuracy, boolean p_remove_unconnected_vias, boolean p_with_preferred_directions)
  {
    this.board = board;
    this.settings = routerSettings;
    this.autorouteItemList = autorouteItemList;
    this.passNo = passNo;
    this.useSlowAlgorithm = useSlowAlgorithm;

    this.remove_unconnected_vias = p_remove_unconnected_vias;
    if (p_with_preferred_directions)
    {
      this.trace_cost_arr = this.settings.get_trace_cost_arr();
    }
    else
    {
      // remove preferred direction
      this.trace_cost_arr = new AutorouteControl.ExpansionCostFactor[this.board.get_layer_count()];
      for (int i = 0; i < this.trace_cost_arr.length; i++)
      {
        double curr_min_cost = this.settings.get_preferred_direction_trace_costs(i);
        this.trace_cost_arr[i] = new AutorouteControl.ExpansionCostFactor(curr_min_cost, curr_min_cost);
      }
    }

    this.start_ripup_costs = startRipupCosts;
    this.trace_pull_tight_accuracy = tracePullTightAccuracy;
    this.retain_autoroute_database = false;
  }

  // Calculates the shortest distance between two sets of items, specifically between Pin and Via items (pins and vias are connectable DrillItems)
  private static FloatLine calc_airline(Collection<Item> p_from_items, Collection<Item> p_to_items)
  {
    FloatPoint from_corner = null;
    FloatPoint to_corner = null;
    double min_distance = Double.MAX_VALUE;
    for (Item curr_from_item : p_from_items)
    {
      FloatPoint curr_from_corner;
      if (curr_from_item instanceof DrillItem item)
      {
        curr_from_corner = item
            .get_center()
            .to_float();
      }
      else if (curr_from_item instanceof PolylineTrace from_trace)
      {
        // Use trace endpoints as potential connection points
        continue; // We'll handle traces in the second loop for better efficiency
      }
      else
      {
        continue;
      }

      for (Item curr_to_item : p_to_items)
      {
        FloatPoint curr_to_corner;
        if (curr_to_item instanceof DrillItem drillItem)
        {
          curr_to_corner = drillItem
              .get_center()
              .to_float();
        }
        else if (curr_to_item instanceof PolylineTrace to_trace)
        {
          // Find nearest point on trace to the from item point
          curr_to_corner = nearest_point_on_trace(to_trace, curr_from_corner);
        }
        else
        {
          continue;
        }

        double curr_distance = curr_from_corner.distance(curr_to_corner);
        if (curr_distance < min_distance)
        {
          min_distance = curr_distance;
          from_corner = curr_from_corner;
          to_corner = curr_to_corner;
        }
      }
    }

    // Check trace-to-trace and trace-to-drill connections
    for (Item curr_from_item : p_from_items)
    {
      if (!(curr_from_item instanceof PolylineTrace from_trace))
      {
        continue;
      }

      for (Item curr_to_item : p_to_items)
      {
        FloatPoint curr_from_corner;
        FloatPoint curr_to_corner;

        if (curr_to_item instanceof DrillItem item)
        {
          // Trace to drill item
          curr_to_corner = item
              .get_center()
              .to_float();
          curr_from_corner = nearest_point_on_trace(from_trace, curr_to_corner);
        }
        else if (curr_to_item instanceof PolylineTrace to_trace)
        {
          // Trace to trace - find the closest points between the two traces
          FloatPoint[] closest_points = find_closest_points_between_traces(from_trace, to_trace);
          curr_from_corner = closest_points[0];
          curr_to_corner = closest_points[1];
        }
        else
        {
          continue;
        }

        double curr_distance = curr_from_corner.distance(curr_to_corner);
        if (curr_distance < min_distance)
        {
          min_distance = curr_distance;
          from_corner = curr_from_corner;
          to_corner = curr_to_corner;
        }
      }
    }

    if (from_corner != null && to_corner != null)
    {
      return new FloatLine(from_corner, to_corner);
    }
    else
    {
      return null;
    }
  }

  /**
   * Finds the nearest point on a trace to the given point
   */
  private static FloatPoint nearest_point_on_trace(PolylineTrace p_trace, FloatPoint p_point)
  {
    double min_distance = Double.MAX_VALUE;
    FloatPoint nearest_point = null;

    // Get endpoints
    FloatPoint first_corner = p_trace
        .first_corner()
        .to_float();
    FloatPoint last_corner = p_trace
        .last_corner()
        .to_float();

    // Check distance to endpoints first
    double distance_to_first = p_point.distance(first_corner);
    double distance_to_last = p_point.distance(last_corner);

    if (distance_to_first < min_distance)
    {
      min_distance = distance_to_first;
      nearest_point = first_corner;
    }

    if (distance_to_last < min_distance)
    {
      min_distance = distance_to_last;
      nearest_point = last_corner;
    }

    // Check distances to line segments
    for (int i = 0; i < p_trace.corner_count() - 1; i++)
    {
      FloatPoint segment_start = p_trace
          .polyline()
          .corner_approx(i);
      FloatPoint segment_end = p_trace
          .polyline()
          .corner_approx(i + 1);
      FloatLine segment = new FloatLine(segment_start, segment_end);

      FloatPoint projection = segment.perpendicular_projection(p_point);
      if (projection.is_contained_in_box(segment_start, segment_end, 0.01))
      {
        double distance = p_point.distance(projection);
        if (distance < min_distance)
        {
          min_distance = distance;
          nearest_point = projection;
        }
      }
    }

    return nearest_point;
  }

  /**
   * Finds the closest points between two traces
   *
   * @return an array with two FloatPoints: [point_on_first_trace, point_on_second_trace]
   */
  private static FloatPoint[] find_closest_points_between_traces(PolylineTrace p_first_trace, PolylineTrace p_second_trace)
  {
    double min_distance = Double.MAX_VALUE;
    FloatPoint[] result = new FloatPoint[2];

    // Check endpoints to endpoints
    FloatPoint first_trace_start = p_first_trace
        .first_corner()
        .to_float();
    FloatPoint first_trace_end = p_first_trace
        .last_corner()
        .to_float();
    FloatPoint second_trace_start = p_second_trace
        .first_corner()
        .to_float();
    FloatPoint second_trace_end = p_second_trace
        .last_corner()
        .to_float();

    // Check all endpoint combinations
    double distance = first_trace_start.distance(second_trace_start);
    if (distance < min_distance)
    {
      min_distance = distance;
      result[0] = first_trace_start;
      result[1] = second_trace_start;
    }

    distance = first_trace_start.distance(second_trace_end);
    if (distance < min_distance)
    {
      min_distance = distance;
      result[0] = first_trace_start;
      result[1] = second_trace_end;
    }

    distance = first_trace_end.distance(second_trace_start);
    if (distance < min_distance)
    {
      min_distance = distance;
      result[0] = first_trace_end;
      result[1] = second_trace_start;
    }

    distance = first_trace_end.distance(second_trace_end);
    if (distance < min_distance)
    {
      min_distance = distance;
      result[0] = first_trace_end;
      result[1] = second_trace_end;
    }

    // Check all segment combinations for closest points
    for (int i = 0; i < p_first_trace.corner_count() - 1; i++)
    {
      FloatPoint first_segment_start = p_first_trace
          .polyline()
          .corner_approx(i);
      FloatPoint first_segment_end = p_first_trace
          .polyline()
          .corner_approx(i + 1);
      FloatLine first_segment = new FloatLine(first_segment_start, first_segment_end);

      for (int j = 0; j < p_second_trace.corner_count() - 1; j++)
      {
        FloatPoint second_segment_start = p_second_trace
            .polyline()
            .corner_approx(j);
        FloatPoint second_segment_end = p_second_trace
            .polyline()
            .corner_approx(j + 1);
        FloatLine second_segment = new FloatLine(second_segment_start, second_segment_end);

        // Find closest points between these two line segments
        FloatPoint point_on_first = first_segment.nearest_segment_point(second_segment_start);
        FloatPoint point_on_second = second_segment.perpendicular_projection(point_on_first);

        // Check if projection is on the segment
        if (!point_on_second.is_contained_in_box(second_segment_start, second_segment_end, 0.01))
        {
          // If not, use the nearest endpoint
          double dist_to_start = point_on_first.distance(second_segment_start);
          double dist_to_end = point_on_first.distance(second_segment_end);
          point_on_second = dist_to_start < dist_to_end ? second_segment_start : second_segment_end;
        }

        // Recalculate the point on first segment based on the point on second segment
        point_on_first = first_segment.nearest_segment_point(point_on_second);

        distance = point_on_first.distance(point_on_second);
        if (distance < min_distance)
        {
          min_distance = distance;
          result[0] = point_on_first;
          result[1] = point_on_second;
        }
      }
    }

    return result;
  }

  private RoutingBoard autorouteItems()
  {
    int items_to_go_count = autorouteItemList.size();
    int ripped_item_count = 0;
    int not_routed = 0;
    int routed = 0;
    int skipped = 0;

    BoardStatistics stats = board.get_statistics();
    RouterCounters routerCounters = new RouterCounters();
    routerCounters.passCount = passNo;
    routerCounters.queuedToBeRoutedCount = items_to_go_count;
    routerCounters.skippedCount = skipped;
    routerCounters.rippedCount = ripped_item_count;
    routerCounters.failedToBeRoutedCount = not_routed;
    routerCounters.routedCount = routed;
    routerCounters.incompleteCount = new RatsNest(board).incomplete_count();

    this.fireBoardUpdatedEvent(stats, routerCounters, board);


    // Let's go through all items to route
    for (Item curr_item : autorouteItemList)
    {
      // If the user requested to stop the auto-router, we stop it
      if (this.is_stop_auto_router_requested())
      {
        break;
      }

      // Let's go through all nets of this item
      for (int i = 0; i < curr_item.net_count(); i++)
      {
        // If the user requested to stop the auto-router, we stop it
        if (this.is_stop_auto_router_requested())
        {
          break;
        }

        // We visually mark the area of the board, which is changed by the auto-router
        board.start_marking_changed_area();

        // Do the auto-routing step for this item (typically PolylineTrace or Pin)
        SortedSet<Item> ripped_item_list = new TreeSet<>();

        var autorouterResult = autoroute_item(board, curr_item, curr_item.get_net_no(i), ripped_item_list, passNo, useSlowAlgorithm);
        if (autorouterResult.state == AutorouteAttemptState.ROUTED)
        {
          // The item was successfully routed
          ++routed;
        } else if ((autorouterResult.state == AutorouteAttemptState.ALREADY_CONNECTED) || (autorouterResult.state == AutorouteAttemptState.NO_UNCONNECTED_NETS) || (autorouterResult.state == AutorouteAttemptState.CONNECTED_TO_PLANE))
        {
          // The item doesn't need to be routed
          ++skipped;
        } else
        {
          FRLogger.debug("Autorouter " + autorouterResult.details);
          ++not_routed;
        }
        --items_to_go_count;
        ripped_item_count += ripped_item_list.size();

        BoardStatistics boardStatistics = board.get_statistics();
        routerCounters.passCount = passNo;
        routerCounters.queuedToBeRoutedCount = items_to_go_count;
        routerCounters.skippedCount = skipped;
        routerCounters.rippedCount = ripped_item_count;
        routerCounters.failedToBeRoutedCount = not_routed;
        routerCounters.routedCount = routed;
        routerCounters.incompleteCount = new RatsNest(board).incomplete_count();
        this.fireBoardUpdatedEvent(boardStatistics, routerCounters, board);
      }
    }

    if (this.remove_unconnected_vias)
    {
      remove_tails(Item.StopConnectionOption.NONE);
    }
    else
    {
      remove_tails(Item.StopConnectionOption.FANOUT_VIA);
    }

    return this.board;
  }

  // Tries to route an item on a specific net. Returns true, if the item is routed.
  private AutorouteAttemptResult autoroute_item(RoutingBoard board, Item p_item, int p_route_net_no, SortedSet<Item> p_ripped_item_list, int p_ripup_pass_no, boolean useSlowAlgorithm)
  {
    try
    {
      boolean contains_plane = false;

      // Get the net
      Net route_net = board.rules.nets.get(p_route_net_no);
      if (route_net != null)
      {
        contains_plane = route_net.contains_plane();
      }

      // Get the current via costs based on auto-router settings
      int curr_via_costs;
      if (contains_plane)
      {
        curr_via_costs = settings.get_plane_via_costs();
      }
      else
      {
        curr_via_costs = settings.get_via_costs();
      }

      // Get and calculate the auto-router settings based on the board and net we are working on
      AutorouteControl autoroute_control = new AutorouteControl(board, p_route_net_no, settings, curr_via_costs, trace_cost_arr);
      autoroute_control.ripup_allowed = true;
      autoroute_control.ripup_costs = start_ripup_costs * p_ripup_pass_no;
      autoroute_control.remove_unconnected_vias = remove_unconnected_vias;

      // Check if the item is already routed
      Set<Item> unconnected_set = p_item.get_unconnected_set(p_route_net_no);
      if (unconnected_set.isEmpty())
      {
        return new AutorouteAttemptResult(AutorouteAttemptState.NO_UNCONNECTED_NETS);
      }

      Set<Item> connected_set = p_item.get_connected_set(p_route_net_no);
      Set<Item> route_start_set;
      Set<Item> route_dest_set;
      if (contains_plane)
      {
        for (Item curr_item : connected_set)
        {
          if (curr_item instanceof ConductionArea)
          {
            return new AutorouteAttemptResult(AutorouteAttemptState.CONNECTED_TO_PLANE);
          }
        }
      }
      if (contains_plane)
      {
        route_start_set = connected_set;
        route_dest_set = unconnected_set;
      }
      else
      {
        route_start_set = unconnected_set;
        route_dest_set = connected_set;
      }

      // Calculate the shortest distance between the two sets of items
      this.latest_air_line = calc_airline(route_start_set, route_dest_set);

      // Calculate the maximum time for this autoroute pass
      double max_milliseconds = 100000 * Math.pow(2, p_ripup_pass_no - 1);
      max_milliseconds = Math.min(max_milliseconds, Integer.MAX_VALUE);
      TimeLimit time_limit = new TimeLimit((int) max_milliseconds);

      // Initialize the auto-router engine
      AutorouteEngine autoroute_engine = board.init_autoroute(p_route_net_no, autoroute_control.trace_clearance_class_no, this, time_limit, this.retain_autoroute_database, useSlowAlgorithm);

      // Do the auto-routing between the two sets of items
      AutorouteAttemptResult autoroute_result = autoroute_engine.autoroute_connection(route_start_set, route_dest_set, autoroute_control, p_ripped_item_list);

      // Update the changed area of the board
      if (autoroute_result.state == AutorouteAttemptState.ROUTED)
      {
        board.opt_changed_area(new int[0], null, trace_pull_tight_accuracy, autoroute_control.trace_costs, this, TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
      }

      return autoroute_result;
    } catch (Exception e)
    {
      FRLogger.error("Error during autoroute_item", e);
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED);
    }
  }

  private void remove_tails(Item.StopConnectionOption p_stop_connection_option)
  {
    board.start_marking_changed_area();
    board.remove_trace_tails(-1, p_stop_connection_option);
    board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, this.trace_cost_arr, this, TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  @Override
  protected void thread_action()
  {
    autorouteItems();
  }

  public void addBoardUpdatedEventListener(BoardUpdatedEventListener listener)
  {
    boardUpdatedEventListeners.add(listener);
  }

  /**
   * Fires a board updated event. This happens when the board has been updated, e.g. after a route has been added.
   */
  public void fireBoardUpdatedEvent(BoardStatistics boardStatistics, RouterCounters routerCounters, RoutingBoard board)
  {
    BoardUpdatedEvent event = new BoardUpdatedEvent(this, boardStatistics, routerCounters, board);
    for (BoardUpdatedEventListener listener : boardUpdatedEventListeners)
    {
      listener.onBoardUpdatedEvent(event);
    }
  }

  public RoutingBoard getBoard()
  {
    return board;
  }
}

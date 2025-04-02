package app.freerouting.autoroute;

import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.*;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.RatsNest;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;

import java.util.*;

import static java.util.Collections.shuffle;

/**
 * Handles the sequencing of the auto-router passes.
 */
public class BatchAutorouter extends NamedAlgorithm
{
  // The lowest rank of the board to be selected to go back to
  private static final int BOARD_RANK_LIMIT = 50;
  // Maximum number of tries on the same board
  private static final int MAXIMUM_TRIES_ON_THE_SAME_BOARD = 3;
  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;
  // The minimum number of passes to complete the board, unless all items are routed
  private static final int STOP_AT_PASS_MINIMUM = 8;
  // The modulo of the pass number to check if the improvements were so small that process should stop despite not all items are routed
  private static final int STOP_AT_PASS_MODULO = 4;

  private final boolean remove_unconnected_vias;
  private final AutorouteControl.ExpansionCostFactor[] trace_cost_arr;
  private final boolean retain_autoroute_database;
  private final int start_ripup_costs;
  private final int trace_pull_tight_accuracy;
  protected RoutingJob job;
  private boolean is_interrupted = false;
  /**
   * Used to draw the airline of the current routed incomplete.
   */
  private FloatLine air_line;

  public BatchAutorouter(RoutingJob job)
  {
    this(job.thread, job.board, job.routerSettings, !job.routerSettings.getRunFanout(), true, job.routerSettings.get_start_ripup_costs(), job.routerSettings.trace_pull_tight_accuracy);
    this.job = job;
  }

  public BatchAutorouter(StoppableThread p_thread, RoutingBoard board, RouterSettings settings, boolean p_remove_unconnected_vias, boolean p_with_preferred_directions, int p_start_ripup_costs, int p_pull_tight_accuracy)
  {
    super(p_thread, board, settings);

    this.remove_unconnected_vias = p_remove_unconnected_vias;
    if (p_with_preferred_directions)
    {
      this.trace_cost_arr = this.settings.get_trace_cost_arr();
    }
    else
    {
      // remove preferred direction
      this.trace_cost_arr = new AutorouteControl.ExpansionCostFactor[this.board.get_layer_count()];
      for (int i = 0; i < this.trace_cost_arr.length; ++i)
      {
        double curr_min_cost = this.settings.get_preferred_direction_trace_costs(i);
        this.trace_cost_arr[i] = new AutorouteControl.ExpansionCostFactor(curr_min_cost, curr_min_cost);
      }
    }

    this.start_ripup_costs = p_start_ripup_costs;
    this.trace_pull_tight_accuracy = p_pull_tight_accuracy;
    this.retain_autoroute_database = false;
  }

  /**
   * Auto-routes ripup passes until the board is completed or the auto-router is stopped by the user,
   * or if p_max_pass_count is exceeded. Is currently used in the optimize via batch pass. Returns
   * the number of passes to complete the board or p_max_pass_count + 1, if the board is not
   * completed.
   */
  public static int autoroute_passes_for_optimizing_item(RoutingJob job, int p_max_pass_count, int p_ripup_costs, int trace_pull_tight_accuracy, boolean p_with_preferred_directions, RoutingBoard updated_routing_board, RouterSettings routerSettings)
  {
    BatchAutorouter router_instance = new BatchAutorouter(job.thread, updated_routing_board, routerSettings, true, p_with_preferred_directions, p_ripup_costs, trace_pull_tight_accuracy);
    router_instance.job = job;

    boolean still_unrouted_items = true;
    int curr_pass_no = 1;
    while (still_unrouted_items && !router_instance.is_interrupted && curr_pass_no <= p_max_pass_count)
    {
      if (job.thread.is_stop_auto_router_requested())
      {
        router_instance.is_interrupted = true;
      }
      still_unrouted_items = router_instance.autoroute_pass(curr_pass_no);
      if (still_unrouted_items && !router_instance.is_interrupted && updated_routing_board == null)
      {
        routerSettings.increment_pass_no();
      }
      ++curr_pass_no;
    }
    router_instance.remove_tails(Item.StopConnectionOption.NONE);
    if (!still_unrouted_items)
    {
      --curr_pass_no;
    }
    return curr_pass_no;
  }

  @Override
  public String getId()
  {
    return "freerouting-router";
  }

  @Override
  public String getName()
  {
    return "Freerouting Auto-router";
  }

  @Override
  public String getVersion()
  {
    return "1.0";
  }

  @Override
  public String getDescription()
  {
    return "Freerouting Auto-router v1.0";
  }

  @Override
  public NamedAlgorithmType getType()
  {
    return NamedAlgorithmType.ROUTER;
  }

  /**
   * Autoroutes ripup passes until the board is completed or the autorouter is stopped by the user.
   * Returns true if the board is completed.
   */
  public boolean runBatchLoop()
  {
    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

    boolean continueAutorouting = true;
    BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

    while (continueAutorouting && !this.is_interrupted)
    {
      if (thread.is_stop_auto_router_requested() || (job != null && job.state == RoutingJobState.TIMED_OUT))
      {
        this.is_interrupted = true;
      }

      String current_board_hash = this.board.get_hash();

      int curr_pass_no = this.settings.get_start_pass_no();
      if (curr_pass_no > this.settings.get_stop_pass_no())
      {
        thread.request_stop_auto_router();
        break;
      }

      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.RUNNING, curr_pass_no, current_board_hash));

      float boardScoreBefore = new BoardStatistics(this.board).getNormalizedScore(job.routerSettings.scoring);
      bh.add(this.board);

      FRLogger.traceEntry("BatchAutorouter.autoroute_pass #" + curr_pass_no + " on board '" + current_board_hash + "'");

      continueAutorouting = autoroute_pass(curr_pass_no);

      BoardStatistics boardStatisticsAfter = new BoardStatistics(this.board);
      float boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);

      if ((bh.size() >= STOP_AT_PASS_MINIMUM) || (thread.is_stop_auto_router_requested()))
      {
        if (((curr_pass_no % STOP_AT_PASS_MODULO == 0) && (curr_pass_no >= STOP_AT_PASS_MINIMUM)) || (thread.is_stop_auto_router_requested()))
        {
          // Check if the score improved compared to the previous passes, restore a previous board if not
          if (bh.getMaxScore() >= boardScoreAfter)
          {
            var boardToRestore = bh.restoreBoard(MAXIMUM_TRIES_ON_THE_SAME_BOARD);
            if (boardToRestore == null)
            {
              job.logInfo("The router was not able to improve the board, stopping the auto-router.");
              thread.request_stop_auto_router();
              break;
            }

            int boardToRestoreRank = bh.getRank(boardToRestore);

            if (boardToRestoreRank > BOARD_RANK_LIMIT)
            {
              thread.request_stop_auto_router();
              break;
            }

            this.board = boardToRestore;
            var boardStatistics = this.board.get_statistics();
            job.logInfo("Restoring an earlier board that has the score of " + FRLogger.formatScore(boardStatistics.getNormalizedScore(job.routerSettings.scoring), boardStatistics.connections.incompleteCount, boardStatistics.clearanceViolations.totalCount) + ".");
          }
        }
      }
      double autorouter_pass_duration = FRLogger.traceExit("BatchAutorouter.autoroute_pass #" + curr_pass_no + " on board '" + current_board_hash + "'");

      String passCompletedMessage = "Auto-router pass #" + curr_pass_no + " on board '" + current_board_hash + "' was completed in " + FRLogger.formatDuration(autorouter_pass_duration) + " with the score of " + FRLogger.formatScore(boardScoreAfter, boardStatisticsAfter.connections.incompleteCount, boardStatisticsAfter.clearanceViolations.totalCount);
      if (job.resourceUsage.cpuTimeUsed > 0)
      {
        passCompletedMessage += ", using " + FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed) + " CPU seconds and " + (int) job.resourceUsage.maxMemoryUsed + " MB memory.";
      }
      else
      {
        passCompletedMessage += ".";
      }
      job.logInfo(passCompletedMessage);

      if (this.settings.save_intermediate_stages)
      {
        fireBoardSnapshotEvent(this.board);
      }

      // check if there are still unrouted items
      if (continueAutorouting && !is_interrupted)
      {
        this.settings.increment_pass_no();
      }
    }

    job.board = this.board;

    if (!(this.remove_unconnected_vias || continueAutorouting || this.is_interrupted))
    {
      // clean up the route if the board is completed and if fanout is used.
      remove_tails(Item.StopConnectionOption.NONE);
    }

    bh.clear();

    if (!this.is_interrupted)
    {
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.FINISHED, this.settings.get_start_pass_no(), this.board.get_hash()));
    }
    else
    {
      // TODO: set it to TIMED_OUT if it was interrupted because of timeout
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.CANCELLED, this.settings.get_start_pass_no(), this.board.get_hash()));
    }

    return !this.is_interrupted;
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the board is already
   * completely routed.
   */
  private boolean autoroute_pass(int p_pass_no)
  {
    try
    {
      LinkedList<Item> autoroute_item_list = new LinkedList<>();
      Set<Item> handled_items = new TreeSet<>();
      Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
      for (; ; )
      {
        UndoableObjects.Storable curr_ob = board.item_list.read_object(it);
        if (curr_ob == null)
        {
          break;
        }
        if (curr_ob instanceof Connectable && curr_ob instanceof Item curr_item)
        {
          // This is a connectable item, like PolylineTrace or Pin
          if (!curr_item.is_routable())
          {
            if (!handled_items.contains(curr_item))
            {

              // Let's go through all nets of this item
              for (int i = 0; i < curr_item.net_count(); ++i)
              {
                int curr_net_no = curr_item.get_net_no(i);
                Set<Item> connected_set = curr_item.get_connected_set(curr_net_no);
                for (Item curr_connected_item : connected_set)
                {
                  if (curr_connected_item.net_count() <= 1)
                  {
                    handled_items.add(curr_connected_item);
                  }
                }
                int net_item_count = board.connectable_item_count(curr_net_no);

                // If the item is not connected to all other items of the net, we add it to the auto-router's to-do list
                if ((connected_set.size() < net_item_count) && (!curr_item.has_ignored_nets()))
                {
                  autoroute_item_list.add(curr_item);
                }
              }
            }
          }
        }
      }

      // If there are no items to route, we're done
      if (autoroute_item_list.isEmpty())
      {
        this.air_line = null;
        return false;
      }

      int items_to_go_count = autoroute_item_list.size();
      int ripped_item_count = 0;
      int not_routed = 0;
      int routed = 0;
      int skipped = 0;
      BoardStatistics stats = board.get_statistics();
      RouterCounters routerCounters = new RouterCounters();
      routerCounters.passCount = p_pass_no;
      routerCounters.queuedToBeRoutedCount = items_to_go_count;
      routerCounters.skippedCount = skipped;
      routerCounters.rippedCount = ripped_item_count;
      routerCounters.failedToBeRoutedCount = not_routed;
      routerCounters.routedCount = routed;
      routerCounters.incompleteCount = new RatsNest(board).incomplete_count();

      this.fireBoardUpdatedEvent(stats, routerCounters, this.board);

      // Shuffle the items to route
      shuffle(autoroute_item_list, new Random());

      // Let's go through all items to route
      for (Item curr_item : autoroute_item_list)
      {
        // If the user requested to stop the auto-router, we stop it
        if (this.is_interrupted)
        {
          break;
        }

        // Let's go through all nets of this item
        for (int i = 0; i < curr_item.net_count(); ++i)
        {
          // If the user requested to stop the auto-router, we stop it
          if (this.thread.is_stop_auto_router_requested())
          {
            this.is_interrupted = true;
            break;
          }

          // We visually mark the area of the board, which is changed by the auto-router
          board.start_marking_changed_area();

          // Do the auto-routing step for this item (typically PolylineTrace or Pin)
          SortedSet<Item> ripped_item_list = new TreeSet<>();
          //boolean useSlowAlgorithm = this.board.rules.get_use_slow_autoroute_algorithm();
          boolean useSlowAlgorithm = p_pass_no % 4 == 0;
          var autorouterResult = autoroute_item(curr_item, curr_item.get_net_no(i), ripped_item_list, p_pass_no, useSlowAlgorithm);
          if (autorouterResult.state == AutorouteAttemptState.ROUTED)
          {
            // The item was successfully routed
            ++routed;
          }
          else if ((autorouterResult.state == AutorouteAttemptState.ALREADY_CONNECTED) || (autorouterResult.state == AutorouteAttemptState.NO_UNCONNECTED_NETS) || (autorouterResult.state == AutorouteAttemptState.CONNECTED_TO_PLANE))
          {
            // The item doesn't need to be routed
            ++skipped;
          }
          else
          {
            job.logDebug("Autorouter " + autorouterResult.details);
            ++not_routed;
          }
          --items_to_go_count;
          ripped_item_count += ripped_item_list.size();

          BoardStatistics boardStatistics = board.get_statistics();
          routerCounters.passCount = p_pass_no;
          routerCounters.queuedToBeRoutedCount = items_to_go_count;
          routerCounters.skippedCount = skipped;
          routerCounters.rippedCount = ripped_item_count;
          routerCounters.failedToBeRoutedCount = not_routed;
          routerCounters.routedCount = routed;
          routerCounters.incompleteCount = new RatsNest(board).incomplete_count();
          this.fireBoardUpdatedEvent(boardStatistics, routerCounters, this.board);
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

      // We are done with this pass
      this.air_line = null;
      return true;
    } catch (Exception e)
    {
      job.logError("Something went wrong during the auto-routing", e);
      this.air_line = null;
      return false;
    }
  }

  private void remove_tails(Item.StopConnectionOption p_stop_connection_option)
  {
    board.start_marking_changed_area();
    board.remove_trace_tails(-1, p_stop_connection_option);
    board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, this.trace_cost_arr, this.thread, TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  // Tries to route an item on a specific net. Returns true, if the item is routed.
  private AutorouteAttemptResult autoroute_item(Item p_item, int p_route_net_no, SortedSet<Item> p_ripped_item_list, int p_ripup_pass_no, boolean useSlowAlgorithm)
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
        curr_via_costs = this.settings.get_plane_via_costs();
      }
      else
      {
        curr_via_costs = this.settings.get_via_costs();
      }

      // Get and calculate the auto-router settings based on the board and net we are working on
      AutorouteControl autoroute_control = new AutorouteControl(this.board, p_route_net_no, settings, curr_via_costs, this.trace_cost_arr);
      autoroute_control.ripup_allowed = true;
      autoroute_control.ripup_costs = this.start_ripup_costs * p_ripup_pass_no;
      autoroute_control.remove_unconnected_vias = this.remove_unconnected_vias;

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
      calc_airline(route_start_set, route_dest_set);

      // Calculate the maximum time for this autoroute pass
      double max_milliseconds = 100000 * Math.pow(2, p_ripup_pass_no - 1);
      max_milliseconds = Math.min(max_milliseconds, Integer.MAX_VALUE);
      TimeLimit time_limit = new TimeLimit((int) max_milliseconds);

      // Initialize the auto-router engine
      AutorouteEngine autoroute_engine = board.init_autoroute(p_route_net_no, autoroute_control.trace_clearance_class_no, this.thread, time_limit, this.retain_autoroute_database, useSlowAlgorithm);

      // Do the auto-routing between the two sets of items
      AutorouteAttemptResult autoroute_result = autoroute_engine.autoroute_connection(route_start_set, route_dest_set, autoroute_control, p_ripped_item_list);

      // Update the changed area of the board
      if (autoroute_result.state == AutorouteAttemptState.ROUTED)
      {
        board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, autoroute_control.trace_costs, this.thread, TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
      }

      return autoroute_result;
    } catch (Exception e)
    {
      job.logError("Error during autoroute_item", e);
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED);
    }
  }

  /**
   * Returns the airline of the current autorouted connection or null, if no such airline exists
   */
  public FloatLine get_air_line()
  {
    if (this.air_line == null)
    {
      return null;
    }
    if (this.air_line.a == null || this.air_line.b == null)
    {
      return null;
    }
    return this.air_line;
  }

  // Calculates the shortest distance between two sets of items, specifically between Pin and Via items (pins and vias are connectable DrillItems)
  private void calc_airline(Collection<Item> p_from_items, Collection<Item> p_to_items)
  {
    FloatPoint from_corner = null;
    FloatPoint to_corner = null;
    double min_distance = Double.MAX_VALUE;
    for (Item curr_from_item : p_from_items)
    {
      FloatPoint curr_from_corner;
      if (curr_from_item instanceof DrillItem)
      {
        curr_from_corner = ((DrillItem) curr_from_item)
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
        if (curr_to_item instanceof DrillItem)
        {
          curr_to_corner = ((DrillItem) curr_to_item)
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

        if (curr_to_item instanceof DrillItem)
        {
          // Trace to drill item
          curr_to_corner = ((DrillItem) curr_to_item)
              .get_center()
              .to_float();
          curr_from_corner = nearest_point_on_trace(from_trace, curr_to_corner);
        }
        else if (curr_to_item instanceof PolylineTrace to_trace)
        {
          // Trace to trace - find closest points between the two traces
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
      this.air_line = new FloatLine(from_corner, to_corner);
    }
    else
    {
      this.air_line = null;
    }
  }

  /**
   * Finds the nearest point on a trace to the given point
   */
  private FloatPoint nearest_point_on_trace(PolylineTrace p_trace, FloatPoint p_point)
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
  private FloatPoint[] find_closest_points_between_traces(PolylineTrace p_first_trace, PolylineTrace p_second_trace)
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
          point_on_second = (dist_to_start < dist_to_end) ? second_segment_start : second_segment_end;
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
}
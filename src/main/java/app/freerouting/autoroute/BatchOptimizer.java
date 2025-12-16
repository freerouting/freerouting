package app.freerouting.autoroute;

import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.interactive.RatsNest;
import app.freerouting.logger.FRLogger;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Optimizes routes using a single thread on a board that has completed auto-routing.
 */
public class BatchOptimizer extends NamedAlgorithm {

  protected static int MAX_AUTOROUTE_PASSES = 6;
  protected static int ADDITIONAL_RIPUP_COST_FACTOR_AT_START = 10;
  protected ReadSortedRouteItems sorted_route_items;
  // in the first passes the ripup costs are increased for better performance.
  protected boolean use_increased_ripup_costs;
  // the minimum cumulative trace length that was reached during the optimization
  protected double min_cumulative_trace_length = 0.0;
  protected RoutingJob job;

  /**
   * Creates a new instance of BatchOptRoute, which is used to optimize the board.
   *
   * @param job
   */
  public BatchOptimizer(RoutingJob job) {
    super(job.thread, job.board, job.routerSettings);
    this.job = job;
  }

  static boolean contains_only_unfixed_traces(Collection<Item> p_item_list) {
    for (Item curr_item : p_item_list) {
      if (curr_item.is_user_fixed() || !(curr_item instanceof Trace)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Optimize the route on the board.
   */
  public void runBatchLoop() {
    job.logDebug("Before optimization: Via count: " + board
        .get_vias()
        .size() + ", trace length: " + Math.round(board.cumulative_trace_length()));

    double route_improved = -1;
    int curr_pass_no = 0;
    use_increased_ripup_costs = true;

    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

    while (((route_improved >= this.settings.optimizer.optimizationImprovementThreshold) || (route_improved < 0)) && (!this.thread.isStopRequested())) {
      ++curr_pass_no;
      String current_board_hash = this.board.get_hash();
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.RUNNING, curr_pass_no, current_board_hash));

      boolean with_preferred_directions = curr_pass_no % 2 != 0; // to create more variations
      route_improved = opt_route_pass(curr_pass_no, with_preferred_directions);
    }

    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.FINISHED, curr_pass_no, this.board.get_hash()));
  }

  /**
   * Tries to reduce the number of vias and the trace length of a completely routed board. Returns the amount of improvements is made in percentage (expressed between 0.0 and 1.0). -1 if the routing
   * must go on no matter how much it improved.
   */
  protected float opt_route_pass(int p_pass_no, boolean p_with_preferred_directions) {
    float route_improved = 0.0f;

    BoardStatistics boardStatisticsBefore = board.get_statistics();
    RouterCounters routerCounters = new RouterCounters();
    routerCounters.passCount = p_pass_no;
    this.fireBoardUpdatedEvent(boardStatisticsBefore, routerCounters, this.board);

    this.sorted_route_items = new ReadSortedRouteItems();
    this.min_cumulative_trace_length = boardStatisticsBefore.traces.totalWeightedLength;
    String optimizationPassId =
        "BatchOptRoute.opt_route_pass #" + p_pass_no + " with " + boardStatisticsBefore.items.viaCount + " vias and " + "%(,.2f".formatted(boardStatisticsBefore.traces.totalLength) + " trace length.";

    FRLogger.traceEntry(optimizationPassId);

    while (true) {
      if (this.thread.isStopRequested()) {
        FRLogger.traceExit(optimizationPassId);
        return route_improved;
      }
      Item curr_item = sorted_route_items.next();
      if (curr_item == null) {
        break;
      }
      if (opt_route_item(curr_item, p_with_preferred_directions, false).improved()) {
        BoardStatistics boardStatisticsAfter = board.get_statistics();
        this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCounters, board);

        route_improved = (float) (boardStatisticsBefore.items.viaCount != 0 && boardStatisticsBefore.traces.totalLength != 0 ? 1.0 - (
            (((float) boardStatisticsAfter.items.viaCount / boardStatisticsBefore.items.viaCount) + (boardStatisticsAfter.traces.totalLength / boardStatisticsBefore.traces.totalLength)) / 2) : 0);
      }
    }

    this.sorted_route_items = null;
    if (this.use_increased_ripup_costs && (route_improved == 0)) {
      this.use_increased_ripup_costs = false;
      route_improved = -1; // to keep the optimizer going with lower ripup costs
    }

    double routeoptimizer_pass_duration = FRLogger.traceExit(optimizationPassId);
    BoardStatistics boardStatisticsAfter = new BoardStatistics(this.board);
    job.logInfo("Optimizer pass #" + p_pass_no + " was completed in " + FRLogger.formatDuration(routeoptimizer_pass_duration) + " with the score of " + FRLogger.formatScore(
        boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring), boardStatisticsAfter.connections.incompleteCount, boardStatisticsAfter.clearanceViolations.totalCount) + ".");
    return route_improved;
  }

  /**
   * Try to improve the route by re-routing the connections containing p_item.
   *
   * @param p_item                      the item to be re-routed
   * @param p_with_preferred_directions if true, the preferred directions are used for the traces
   * @param disableSnapshots            if true, the snapshots are not used which means that the routing cannot be undone, but it's much more efficient
   */
  protected ItemRouteResult opt_route_item(Item p_item, boolean p_with_preferred_directions, boolean disableSnapshots) {
    // check if item.board is a RoutingBoard
    if (!(p_item.board instanceof RoutingBoard routingBoard)) {
      job.logWarning("The item to be optimized is not on a RoutingBoard.");
      return new ItemRouteResult(p_item.get_id_no());
    }

    // calculate the statistics for the board before the routing
    BoardStatistics boardStatisticsBefore = routingBoard.get_statistics();
    RouterCounters routerCountersBefore = new RouterCounters();
    routerCountersBefore.incompleteCount = new RatsNest(routingBoard).incomplete_count();
    this.fireBoardUpdatedEvent(boardStatisticsBefore, routerCountersBefore, routingBoard);

    // collect the items to be re-routed
    Set<Item> ripped_items = new TreeSet<>();
    ripped_items.add(p_item);

    // add the contacts of the traces to the ripped items if it's a trace
    if (p_item instanceof Trace curr_trace) {
      // add also the fork items, especially because not all fork items may be
      // returned by ReadSortedRouteItems because of matching end points.
      Set<Item> curr_contact_list = curr_trace.get_start_contacts();
      for (int i = 0; i < 2; i++) {
        if (contains_only_unfixed_traces(curr_contact_list)) {
          ripped_items.addAll(curr_contact_list);
        }
        curr_contact_list = curr_trace.get_end_contacts();
      }
    }

    Set<Item> ripped_connections = new TreeSet<>();
    // add all the connections of the items to be re-routed
    for (Item curr_item : ripped_items) {
      ripped_connections.addAll(curr_item.get_connection_items(Item.StopConnectionOption.NONE));
    }

    // check if the connections contain user fixed items, which should not be re-routed
    for (Item curr_item : ripped_connections) {
      if (curr_item.is_user_fixed()) {
        return new ItemRouteResult(p_item.get_id_no());
      }
    }

    if (!disableSnapshots) {
      // make the current situation restorable by undo with the snapshot
      routingBoard.generate_snapshot();
    }

    // remove the items to be re-routed
    routingBoard.remove_items(ripped_connections);
    for (int i = 0; i < p_item.net_count(); i++) {
      routingBoard.combine_traces(p_item.get_net_no(i));
    }

    // calculate the ripup costs
    int ripup_costs = this.settings.get_start_ripup_costs();
    if (this.use_increased_ripup_costs) {
      // TODO: move this fixed parameter (ADDITIONAL_RIPUP_COST_FACTOR_AT_START=10) to the router optimizer settings
      ripup_costs *= ADDITIONAL_RIPUP_COST_FACTOR_AT_START;
    }

    // reduce the ripup costs for traces
    if (p_item instanceof Trace) {
      // taking less ripup costs seems to produce better results
      // TODO: move this fixed parameter (0.6) to the router optimizer settings
      ripup_costs = (int) Math.round(0.6 * (double) ripup_costs);
    }

    // route the connections
    BatchAutorouter.autoroute_passes_for_optimizing_item(job, MAX_AUTOROUTE_PASSES, ripup_costs, settings.trace_pull_tight_accuracy, p_with_preferred_directions, routingBoard, settings);

    // check the result by generating the statistics for the board again after the routing
    BoardStatistics boardStatisticsAfter = routingBoard.get_statistics();
    RouterCounters routerCountersAfter = new RouterCounters();
    routerCountersAfter.incompleteCount = new RatsNest(routingBoard).incomplete_count();
    this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCountersAfter, routingBoard);

    // check if the board was improved
    ItemRouteResult result = new ItemRouteResult(p_item.get_id_no(), boardStatisticsBefore.items.viaCount, boardStatisticsAfter.items.viaCount, this.min_cumulative_trace_length,
        boardStatisticsAfter.traces.totalLength, routerCountersBefore.incompleteCount, routerCountersAfter.incompleteCount);
    boolean route_improved = !this.thread.isStopRequested() && result.improved();
    result.update_improved(route_improved);

    if (route_improved) {
      this.min_cumulative_trace_length = Math.min(this.min_cumulative_trace_length, boardStatisticsAfter.traces.totalWeightedLength);

      if (!disableSnapshots) {
        // this was a successful routing, so the snapshot can be removed
        routingBoard.pop_snapshot();
      }
    } else {
      if (!disableSnapshots) {
        // this was not a successful routing, so we can undo the routing using the snapshot
        routingBoard.undo(null);
      }
    }

    return result;
  }

  /**
   * Returns the current position of the item, which will be rerouted or null, if the optimizer is not active.
   */
  public FloatPoint get_current_position() {
    if (sorted_route_items == null) {
      return null;
    }
    return sorted_route_items.get_current_position();
  }

  @Override
  public String getId() {
    return "freerouting-optimizer";
  }

  @Override
  protected String getName() {
    return "Freerouting Optimizer";
  }

  @Override
  protected String getVersion() {
    return "1.0";
  }

  @Override
  protected String getDescription() {
    return "Freerouting Optimizer v1.0";
  }

  @Override
  protected NamedAlgorithmType getType() {
    return NamedAlgorithmType.OPTIMIZER;
  }

  /**
   * Reads the vias and traces on the board in ascending x order. Because the vias and traces on the board change while optimizing the item list of the board is read from scratch each time the next
   * route item is returned.
   */
  protected class ReadSortedRouteItems {

    protected FloatPoint min_item_coor;
    protected int min_item_layer;

    ReadSortedRouteItems() {
      min_item_coor = new FloatPoint(Integer.MIN_VALUE, Integer.MIN_VALUE);
      min_item_layer = -1;
    }

    Item next() {
      Item result = null;
      FloatPoint curr_min_coor = new FloatPoint(Integer.MAX_VALUE, Integer.MAX_VALUE);
      int curr_min_layer = Integer.MAX_VALUE;
      Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
      for (; ; ) {
        UndoableObjects.Storable curr_item = board.item_list.read_object(it);
        if (curr_item == null) {
          break;
        }
        if (curr_item instanceof Via curr_via) {
          if (!curr_via.is_user_fixed()) {
            FloatPoint curr_via_center = curr_via
                .get_center()
                .to_float();
            int curr_via_min_layer = curr_via.first_layer();
            if (curr_via_center.x > min_item_coor.x || curr_via_center.x == min_item_coor.x && (curr_via_center.y > min_item_coor.y
                || curr_via_center.y == min_item_coor.y && curr_via_min_layer > min_item_layer)) {
              if (curr_via_center.x < curr_min_coor.x || curr_via_center.x == curr_min_coor.x && (curr_via_center.y < curr_min_coor.y
                  || curr_via_center.y == curr_min_coor.y && curr_via_min_layer < curr_min_layer)) {
                curr_min_coor = curr_via_center;
                curr_min_layer = curr_via_min_layer;
                result = curr_via;
              }
            }
          }
        }
      }
      // Read traces last to prefer vias to traces at the same location
      it = board.item_list.start_read_object();
      for (; ; ) {
        UndoableObjects.Storable curr_item = board.item_list.read_object(it);
        if (curr_item == null) {
          break;
        }
        if (curr_item instanceof Trace curr_trace) {
          if (!curr_trace.is_shove_fixed()) {
            FloatPoint first_corner = curr_trace
                .first_corner()
                .to_float();
            FloatPoint last_corner = curr_trace
                .last_corner()
                .to_float();
            FloatPoint compare_corner;
            if (first_corner.x < last_corner.x || first_corner.x == last_corner.x && first_corner.y < last_corner.y) {
              compare_corner = last_corner;
            } else {
              compare_corner = first_corner;
            }
            int curr_trace_layer = curr_trace.get_layer();
            if (compare_corner.x > min_item_coor.x || compare_corner.x == min_item_coor.x && (compare_corner.y > min_item_coor.y
                || compare_corner.y == min_item_coor.y && curr_trace_layer > min_item_layer)) {
              if (compare_corner.x < curr_min_coor.x || compare_corner.x == curr_min_coor.x && (compare_corner.y < curr_min_coor.y
                  || compare_corner.y == curr_min_coor.y && curr_trace_layer < curr_min_layer)) {
                boolean is_connected_to_via = false;
                Set<Item> trace_contacts = curr_trace.get_normal_contacts();
                for (Item curr_contact : trace_contacts) {
                  if (curr_contact instanceof Via && !curr_contact.is_user_fixed()) {
                    is_connected_to_via = true;
                    break;
                  }
                }
                if (!is_connected_to_via) {
                  curr_min_coor = compare_corner;
                  curr_min_layer = curr_trace_layer;
                  result = curr_trace;
                }
              }
            }
          }
        }
      }
      min_item_coor = curr_min_coor;
      min_item_layer = curr_min_layer;
      return result;
    }

    FloatPoint get_current_position() {
      return min_item_coor;
    }
  }
}
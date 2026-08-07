package app.freerouting.autoroute;

import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Connectable;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * V1.9 BatchAutorouter implementation - ported from v1.9 for performance comparison. This is a
 * simpler, single-threaded router that was used in v1.9.
 *
 * <p>Key differences from current router: - Single-threaded only (no multi-threading support) -
 * Simpler pass logic without board history/backtracking - Different item routing order (natural
 * order vs sorted) - No failure tracking or skip logic
 */
public class BatchAutorouterV19 extends NamedAlgorithm {

  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;

  private final boolean removeUnconnectedVias;
  private final AutorouteControl.ExpansionCostFactor[] traceCostArr;
  private final boolean retainAutorouteDatabase;
  private final int startRipupCosts;
  private final int tracePullTightAccuracy;

  protected RoutingJob job;
  private boolean isOptimizerAutorouter;

  /** Used to draw the airline of the current routed incomplete. */
  private FloatLine airLine;

  /** Initial number of unrouted nets at the start of the routing session. */
  private int initialUnroutedCount;

  /** Time when the routing session started. */
  private Instant sessionStartTime;

  /**
   * Creates a new V1.9 batch autorouter from a RoutingJob. This constructor adapts the current
   * RoutingJob structure to the v1.9 router's needs.
   */
  public BatchAutorouterV19(RoutingJob job) {
    this(
        job.thread,
        job.board,
        job.routerSettings,
        true,
        true,
        job.routerSettings.get_start_ripup_costs(),
        job.routerSettings.tracePullTightAccuracy);
    this.job = job;
  }

  /** Internal constructor matching v1.9 signature. */
  public BatchAutorouterV19(
      StoppableThread p_thread,
      RoutingBoard board,
      RouterSettings settings,
      boolean p_remove_unconnected_vias,
      boolean p_with_preferred_directions,
      int p_start_ripup_costs,
      int p_pull_tight_accuracy) {
    super(p_thread, board, settings);

    // Validate that this is single-threaded (v1.9 doesn't support multi-threading)
    if (settings.maxThreads > 1) {
      FRLogger.warn(
          "V1.9 router only supports single-threaded operation. Setting maxThreads to 1.");
      settings.maxThreads = 1;
    }

    this.removeUnconnectedVias = p_remove_unconnected_vias;
    if (p_with_preferred_directions) {
      this.traceCostArr = this.settings.get_trace_cost_arr();
    } else {
      // remove preferred direction
      this.traceCostArr = new AutorouteControl.ExpansionCostFactor[this.board.get_layer_count()];
      for (int i = 0; i < this.traceCostArr.length; i++) {
        double currMinCost = this.settings.get_preferred_direction_trace_costs(i);
        this.traceCostArr[i] = new AutorouteControl.ExpansionCostFactor(currMinCost, currMinCost);
      }
    }

    this.startRipupCosts = p_start_ripup_costs;
    this.tracePullTightAccuracy = p_pull_tight_accuracy;
    this.retainAutorouteDatabase = false;
  }

  @Override
  public String getId() {
    return RouterSettings.ALGORITHM_V19;
  }

  @Override
  public String getName() {
    return "Freerouting Auto-router v1.9";
  }

  @Override
  public String getVersion() {
    return "1.9";
  }

  @Override
  public String getDescription() {
    return "Freerouting Auto-router v1.9 (ported for performance comparison)";
  }

  @Override
  public NamedAlgorithmType getType() {
    return NamedAlgorithmType.ROUTER;
  }

  /** Returns the initial number of unrouted nets at the start of the routing session. */
  public int getInitialUnroutedCount() {
    return this.initialUnroutedCount;
  }

  /** Returns the time when the routing session started. */
  public Instant getSessionStartTime() {
    return this.sessionStartTime;
  }

  /**
   * Autoroutes ripup passes until the board is completed or the autorouter is stopped by the user.
   * Returns true if the board is completed.
   *
   * <p>This is the main entry point, matching the current router's interface.
   */
  public boolean runBatchLoop() {
    boolean anyRoutable = false;
    for (int i = 0; i < this.settings.getLayerCount(); i++) {
      if (this.settings.get_layer_active(i) && this.board.layerStructure.arr[i].isSignal) {
        anyRoutable = true;
        break;
      }
    }
    if (!anyRoutable) {
      FRLogger.warn("Cannot start autorouter: all layers are disabled.");
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.CANCELLED, 0, this.board.get_hash()));
      throw new IllegalArgumentException("Cannot start autorouter: all layers are disabled.");
    }

    this.fireTaskStateChangedEvent(
        new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

    // Capture initial state for session summary
    this.sessionStartTime = Instant.now();
    DesignRulesChecker tempDrc = new DesignRulesChecker(this.board, null);
    tempDrc.calculateAllIncompletes();
    this.initialUnroutedCount = tempDrc.getIncompleteCount();

    job.logInfo(
        "Starting V1.9 router with " + this.initialUnroutedCount + " incomplete connections.");

    boolean continueAutorouting = true;
    int currentPass = 1;

    while (continueAutorouting && !this.thread.is_stop_auto_router_requested()) {
      if (job != null && job.state == RoutingJobState.TIMED_OUT) {
        this.thread.request_stop_auto_router();
      }

      String currentBoardHash = this.board.get_hash();

      if (this.settings.maxPasses != null
          && this.settings.maxPasses > 0
          && currentPass > this.settings.maxPasses) {
        thread.request_stop_auto_router();
        break;
      }

      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.RUNNING, currentPass, currentBoardHash));

      FRLogger.traceEntry(
          "BatchAutorouterV19.autoroute_pass #"
              + currentPass
              + " on board '"
              + currentBoardHash
              + "'");

      // Run one pass using v1.9 logic
      continueAutorouting = autoroute_pass(currentPass, true);

      double autorouterPassDuration =
          FRLogger.traceExit(
              "BatchAutorouterV19.autoroute_pass #"
                  + currentPass
                  + " on board '"
                  + currentBoardHash
                  + "'");

      var boardStatistics = this.board.get_statistics();
      float boardScore = boardStatistics.getNormalizedScore(job.routerSettings.scoring);

      String passCompletedMessage =
          "V1.9 Auto-routing pass #"
              + currentPass
              + " on board '"
              + currentBoardHash
              + "' was completed in "
              + FRLogger.formatDuration(autorouterPassDuration)
              + " with the score of "
              + FRLogger.formatScore(
                  boardScore,
                  boardStatistics.connections.incompleteCount,
                  boardStatistics.clearanceViolations.totalCount);
      if (job.resourceUsage.cpuTimeUsed > 0) {
        passCompletedMessage +=
            ", using "
                + FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed)
                + " CPU seconds and the job allocated "
                + FRLogger.defaultFloatFormat.format(job.resourceUsage.maxMemoryUsed / 1024.0f)
                + " GB of memory so far.";
      } else {
        passCompletedMessage += ".";
      }
      if (!isOptimizerAutorouter) {
        job.logInfo(passCompletedMessage);
      }

      if (this.settings.saveIntermediateStages) {
        fireBoardSnapshotEvent(this.board);
      }

      // check if there are still unrouted items
      if (continueAutorouting && !this.thread.is_stop_auto_router_requested()) {
        currentPass++;
      }
    }

    job.board = this.board;

    if (!(this.removeUnconnectedVias
        || continueAutorouting
        || this.thread.is_stop_auto_router_requested())) {
      // clean up the route if the board is completed and if fanout is used.
      remove_tails(Item.StopConnectionOption.NONE);
    }

    if (!this.thread.is_stop_auto_router_requested()) {
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.FINISHED, currentPass, this.board.get_hash()));
    } else {
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.CANCELLED, currentPass, this.board.get_hash()));
    }

    return !this.thread.is_stop_auto_router_requested();
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the board is already
   * completely routed.
   *
   * <p>This is the v1.9 implementation - simpler than current version: - No board
   * history/backtracking - No failure tracking - Natural item order (no sorting by airline
   * distance) - Simpler progress tracking
   */
  private boolean autoroute_pass(int p_pass_no, boolean p_with_screen_message) {
    try {
      Collection<Item> autorouteItemList = new LinkedList<>();
      Set<Item> handledItems = new TreeSet<>();
      Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.start_read_object();
      for (; ; ) {
        UndoableObjects.Storable currOb = board.itemList.read_object(it);
        if (currOb == null) {
          break;
        }
        if (currOb instanceof Connectable && currOb instanceof Item) {
          // This is a connectable item, like PolylineTrace or Pin
          Item currItem = (Item) currOb;
          if (!currItem.is_routable()) {
            if (!handledItems.contains(currItem)) {

              // Let's go through all nets of this item
              for (int i = 0; i < currItem.net_count(); ++i) {
                int currNetNo = currItem.get_net_no(i);
                Set<Item> connectedSet = currItem.get_connected_set(currNetNo);
                for (Item curr_connected_item : connectedSet) {
                  if (curr_connected_item.net_count() <= 1) {
                    handledItems.add(curr_connected_item);
                  }
                }
                int netItemCount = board.connectable_item_count(currNetNo);

                // If the item is not connected to all other items of the net, we add it to the
                // auto-router's to-do list
                if ((connectedSet.size() < netItemCount) && (!currItem.has_ignored_nets())) {
                  autorouteItemList.add(currItem);
                }
              }
            }
          }
        }
      }

      // If there are no items to route, we're done
      if (autorouteItemList.isEmpty()) {
        this.airLine = null;
        return false;
      }

      int itemsToGoCount = autorouteItemList.size();
      int rippedItemCount = 0;
      int notFound = 0;
      int routed = 0;

      job.logDebug("V1.9 Pass #" + p_pass_no + ": " + itemsToGoCount + " items to route");

      // Let's go through all items to route (v1.9: natural order, no sorting)
      for (Item currItem : autorouteItemList) {
        // If the user requested to stop the auto-router, we stop it
        if (this.thread.is_stop_auto_router_requested()) {
          break;
        }

        // Let's go through all nets of this item
        for (int i = 0; i < currItem.net_count(); ++i) {
          // If the user requested to stop the auto-router, we stop it
          if (this.thread.is_stop_auto_router_requested()) {
            break;
          }

          // We visually mark the area of the board, which is changed by the auto-router
          board.start_marking_changed_area();

          // Do the auto-routing step for this item (typically PolylineTrace or Pin)
          SortedSet<Item> rippedItemList = new TreeSet<>();
          if (autoroute_item(currItem, currItem.get_net_no(i), rippedItemList, p_pass_no)) {
            ++routed;
          } else {
            ++notFound;
          }
          --itemsToGoCount;
          rippedItemCount += rippedItemList.size();
        }
      }

      // V1.9: Always remove tails after each pass
      if (this.removeUnconnectedVias) {
        remove_tails(Item.StopConnectionOption.NONE);
      } else {
        remove_tails(Item.StopConnectionOption.FANOUT_VIA);
      }

      // We are done with this pass
      this.airLine = null;

      job.logDebug(
          "V1.9 Pass #"
              + p_pass_no
              + " completed: routed="
              + routed
              + ", notFound="
              + notFound
              + ", ripped="
              + rippedItemCount);

      return true;
    } catch (Exception e) {
      job.logError("Something went wrong during the V1.9 auto-routing", e);
      this.airLine = null;
      return false;
    }
  }

  private void remove_tails(Item.StopConnectionOption p_stop_connection_option) {
    board.start_marking_changed_area();
    board.remove_trace_tails(-1, p_stop_connection_option);
    board.opt_changed_area(
        new int[0],
        null,
        this.tracePullTightAccuracy,
        this.traceCostArr,
        this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  /**
   * Tries to route an item on a specific net. Returns true, if the item is routed. This is the v1.9
   * implementation.
   */
  private boolean autoroute_item(
      Item p_item, int p_route_net_no, SortedSet<Item> p_ripped_item_list, int p_ripup_pass_no) {
    try {
      boolean containsPlane = false;

      // Get the net
      Net routeNet = board.rules.nets.get(p_route_net_no);
      if (routeNet != null) {
        containsPlane = routeNet.contains_plane();
      }

      // Get the current via costs based on auto-router settings
      int currViaCosts;
      if (containsPlane) {
        currViaCosts = this.settings.get_plane_via_costs();
      } else {
        currViaCosts = this.settings.get_via_costs();
      }

      // Get and calculate the auto-router settings based on the board and net we are
      // working on
      AutorouteControl autorouteControl =
          new AutorouteControl(
              this.board, p_route_net_no, settings, currViaCosts, this.traceCostArr);
      autorouteControl.ripupAllowed = true;
      autorouteControl.ripupCosts = this.startRipupCosts * p_ripup_pass_no;
      autorouteControl.removeUnconnectedVias = this.removeUnconnectedVias;

      // Check if the item is already routed
      Set<Item> unconnectedSet = p_item.get_unconnected_set(p_route_net_no);
      if (unconnectedSet.isEmpty()) {
        return true; // p_item is already routed.
      }

      Set<Item> connectedSet = p_item.get_connected_set(p_route_net_no);
      Set<Item> routeStartSet;
      Set<Item> routeDestSet;
      if (containsPlane) {
        for (Item currItem : connectedSet) {
          if (currItem instanceof ConductionArea) {
            return true; // already connected to plane
          }
        }
      }
      if (containsPlane) {
        routeStartSet = connectedSet;
        routeDestSet = unconnectedSet;
      } else {
        routeStartSet = unconnectedSet;
        routeDestSet = connectedSet;
      }

      // Calculate the shortest distance between the two sets of items
      calc_airline(routeStartSet, routeDestSet);

      // Calculate the maximum time for this autoroute pass
      double maxMilliseconds = 100000 * Math.pow(2, p_ripup_pass_no - 1);
      maxMilliseconds = Math.min(maxMilliseconds, Integer.MAX_VALUE);
      TimeLimit timeLimit = new TimeLimit((int) maxMilliseconds);

      // Initialize the auto-router engine
      AutorouteEngine autorouteEngine =
          board.init_autoroute(
              p_route_net_no,
              autorouteControl.traceClearanceClassNo,
              this.thread,
              timeLimit,
              this.retainAutorouteDatabase);

      // Do the auto-routing between the two sets of items
      // V1.9 used AutorouteEngine.AutorouteResult enum, current version uses
      // AutorouteAttemptResult
      // We need to adapt to the current interface
      AutorouteAttemptResult autorouteResult =
          autorouteEngine.autoroute_connection(
              routeStartSet,
              routeDestSet,
              autorouteControl,
              p_ripped_item_list,
              null); // null: costs not needed here

      // Update the changed area of the board
      if (autorouteResult.state == AutorouteAttemptState.ROUTED) {
        board.opt_changed_area(
            new int[0],
            null,
            this.tracePullTightAccuracy,
            autorouteControl.traceCosts,
            this.thread,
            TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
      }

      // Return true, if the item is routed
      return autorouteResult.state == AutorouteAttemptState.ROUTED
          || autorouteResult.state == AutorouteAttemptState.ALREADY_CONNECTED;
    } catch (Exception e) {
      return false;
    }
  }

  /** Returns the airline of the current autorouted connection or null, if no such airline exists */
  public FloatLine get_air_line() {
    if (this.airLine == null) {
      return null;
    }
    if (this.airLine.a == null || this.airLine.b == null) {
      return null;
    }
    return this.airLine;
  }

  /**
   * Calculates the shortest distance between two sets of items, specifically between Pin and Via
   * items (pins and vias are connectable DrillItems)
   */
  private void calc_airline(Collection<Item> p_from_items, Collection<Item> p_to_items) {
    FloatPoint fromCorner = null;
    FloatPoint toCorner = null;
    double minDistance = Double.MAX_VALUE;
    for (Item curr_from_item : p_from_items) {
      if (!(curr_from_item instanceof DrillItem)) {
        continue;
      }
      FloatPoint currFromCorner = ((DrillItem) curr_from_item).get_center().to_float();
      for (Item curr_to_item : p_to_items) {
        if (!(curr_to_item instanceof DrillItem)) {
          continue;
        }
        FloatPoint currToCorner = ((DrillItem) curr_to_item).get_center().to_float();
        double currDistance = currFromCorner.distance_square(currToCorner);
        if (currDistance < minDistance) {
          minDistance = currDistance;
          fromCorner = currFromCorner;
          toCorner = currToCorner;
        }
      }
    }
    this.airLine = new FloatLine(fromCorner, toCorner);
  }
}

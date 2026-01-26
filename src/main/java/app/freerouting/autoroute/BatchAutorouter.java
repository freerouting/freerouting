package app.freerouting.autoroute;

import static java.util.Collections.shuffle;

import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Connectable;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Handles the sequencing of the auto-router passes.
 */
public class BatchAutorouter extends NamedAlgorithm {

  // The lowest rank of the board to be selected to go back to
  private static final int BOARD_RANK_LIMIT = 50;
  // Maximum number of tries on the same board
  private static final int MAXIMUM_TRIES_ON_THE_SAME_BOARD = 3;
  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;
  // The minimum number of passes to complete the board, unless all items are
  // routed
  private static final int STOP_AT_PASS_MINIMUM = 8;
  // The modulo of the pass number to check if the improvements were so small that
  // process should stop despite not all items are routed
  private static final int STOP_AT_PASS_MODULO = 4;

  private final boolean remove_unconnected_vias;
  private final AutorouteControl.ExpansionCostFactor[] trace_cost_arr;
  private final boolean retain_autoroute_database;
  private final int start_ripup_costs;
  private final int trace_pull_tight_accuracy;
  // Reusable collections to reduce memory churn (thread-safe as each thread has
  // its own BatchAutorouter instance)
  private final SortedSet<Item> reusable_ripped_item_list = new TreeSet<>();
  private final List<Item> reusable_autoroute_item_list = new ArrayList<>();
  private final Set<Item> reusable_handled_items = new TreeSet<>();
  protected RoutingJob job;
  /**
   * Time when the routing session started.
   */
  private Random random;
  /**
   * Used to draw the airline of the current routed incomplete.
   */
  private FloatLine air_line;
  /**
   * Initial number of unrouted nets at the start of the routing session.
   */
  private int initialUnroutedCount;
  /**
   * Time when the routing session started.
   */
  private Instant sessionStartTime;
  private long lastBoardUpdateTimestamp = 0;
  private int totalItemsRouted = 0;

  public BatchAutorouter(RoutingJob job) {
    this(job.thread, job.board, job.routerSettings, true, true,
        job.routerSettings.get_start_ripup_costs(), job.routerSettings.trace_pull_tight_accuracy);
    this.job = job;
  }

  public BatchAutorouter(StoppableThread p_thread, RoutingBoard board, RouterSettings settings,
      boolean p_remove_unconnected_vias, boolean p_with_preferred_directions, int p_start_ripup_costs,
      int p_pull_tight_accuracy) {
    super(p_thread, board, settings);

    this.random = new Random();

    this.remove_unconnected_vias = p_remove_unconnected_vias;
    if (p_with_preferred_directions) {
      this.trace_cost_arr = this.settings.get_trace_cost_arr();
    } else {
      // remove preferred direction
      this.trace_cost_arr = new AutorouteControl.ExpansionCostFactor[this.board.get_layer_count()];
      for (int i = 0; i < this.trace_cost_arr.length; i++) {
        double curr_min_cost = this.settings.get_preferred_direction_trace_costs(i);
        this.trace_cost_arr[i] = new AutorouteControl.ExpansionCostFactor(curr_min_cost, curr_min_cost);
      }
    }

    this.start_ripup_costs = p_start_ripup_costs;
    this.trace_pull_tight_accuracy = p_pull_tight_accuracy;
    this.retain_autoroute_database = false;
  }

  /**
   * Auto-routes ripup passes until the board is completed or the auto-router is
   * stopped by the user, or if p_max_pass_count is exceeded. Is currently used in
   * the optimize via batch pass. Returns the
   * number of passes to complete the board or p_max_pass_count + 1, if the board
   * is not completed.
   */
  public static int autoroute_passes_for_optimizing_item(RoutingJob job, int p_max_pass_count, int p_ripup_costs,
      int trace_pull_tight_accuracy, boolean p_with_preferred_directions,
      RoutingBoard updated_routing_board, RouterSettings routerSettings) {
    BatchAutorouter router_instance = new BatchAutorouter(job.thread, updated_routing_board, routerSettings, true,
        p_with_preferred_directions, p_ripup_costs, trace_pull_tight_accuracy);
    router_instance.job = job;

    boolean still_unrouted_items = true;
    int curr_pass_no = 1;
    while (still_unrouted_items && !job.thread.is_stop_auto_router_requested() && curr_pass_no <= p_max_pass_count) {
      still_unrouted_items = router_instance.autoroute_pass(curr_pass_no);
      if (still_unrouted_items && !job.thread.is_stop_auto_router_requested() && updated_routing_board == null) {
      }
      ++curr_pass_no;
    }
    router_instance.remove_tails(Item.StopConnectionOption.NONE);
    if (!still_unrouted_items) {
      --curr_pass_no;
    }
    return curr_pass_no;
  }

  private boolean shouldFireBoardUpdate() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastBoardUpdateTimestamp > 250) { // Limit updates to 4 times per second (250ms)
      lastBoardUpdateTimestamp = currentTime;
      return true;
    }
    return false;
  }

  private List<Item> getAutorouteItems(RoutingBoard board) {
    // Reuse instance collections to reduce memory allocation
    reusable_autoroute_item_list.clear();
    reusable_handled_items.clear();
    List<Item> autoroute_item_list = reusable_autoroute_item_list;
    Set<Item> handled_items = reusable_handled_items;
    Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
    for (;;) {
      UndoableObjects.Storable curr_ob = board.item_list.read_object(it);
      if (curr_ob == null) {
        break;
      }
      if (curr_ob instanceof Connectable && curr_ob instanceof Item curr_item) {
        // This is a connectable item, like PolylineTrace or Pin
        if (!curr_item.is_routable()) {
          if (!handled_items.contains(curr_item)) {

            // Let's go through all nets of this item
            for (int i = 0; i < curr_item.net_count(); i++) {
              int curr_net_no = curr_item.get_net_no(i);
              Set<Item> connected_set = curr_item.get_connected_set(curr_net_no);
              for (Item curr_connected_item : connected_set) {
                if (curr_connected_item.net_count() <= 1) {
                  handled_items.add(curr_connected_item);
                }
              }
              int net_item_count = board.connectable_item_count(curr_net_no);

              // If the item is not connected to all other items of the net, we add it to the
              // auto-router's to-do list
              if ((connected_set.size() < net_item_count) && (!curr_item.has_ignored_nets())) {
                autoroute_item_list.add(curr_item);
                Net net = board.rules.nets.get(curr_net_no);
                String netName = (net != null) ? net.name : "net#" + curr_net_no;
                FRLogger.trace("BatchAutorouter.getAutorouteItem","queue_item",
                    "Queuing item for routing: " + curr_item.getClass().getSimpleName() + " on net '"
                    + netName + "' (#" + curr_net_no + ") (connected: " + connected_set.size() + "/" + net_item_count + ")",
                    curr_item.toString());
              }
            }
          }
        }
      }
    }
    return autoroute_item_list;
  }

  /**
   * Multi-threaded version of the router that routes one ripup pass of all items
   * of the board. WARNING: this version is not working as intended yet. It is a
   * work in progress.
   * <p>
   * Returns false if the board is already completely routed.
   */
  private boolean autoroute_pass_multi_thread(int p_pass_no) {
    try {
      List<Item> autoroute_item_list = getAutorouteItems(this.board);

      // If there are no items to route, we're done
      if (autoroute_item_list.isEmpty()) {
        this.air_line = null;
        return false;
      }

      boolean useSlowAlgorithm = false;

      BatchAutorouterThread[] autorouterThreads = new BatchAutorouterThread[job.routerSettings.maxThreads];
      BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

      // Start multiple instances of the following part in parallel, wait for the
      // results and keep only the best one

      // Prepare the threads
      for (int threadIndex = 0; threadIndex < job.routerSettings.maxThreads; threadIndex++) {
        // deep copy the board
        PerformanceProfiler.start("board.deepCopy");
        RoutingBoard clonedBoard = this.board.deepCopy();
        PerformanceProfiler.end("board.deepCopy");

        // clone the auto-route item list to avoid concurrent modification
        List<Item> clonedAutorouteItemList = new ArrayList<>(getAutorouteItems(clonedBoard));

        // shuffle the items to route
        shuffle(clonedAutorouteItemList, this.random);

        autorouterThreads[threadIndex] = new BatchAutorouterThread(clonedBoard, clonedAutorouteItemList, p_pass_no,
            job.routerSettings, this.start_ripup_costs,
            this.trace_pull_tight_accuracy, this.remove_unconnected_vias, true);
        autorouterThreads[threadIndex].setName("Router thread #" + p_pass_no + "." + ThreadIndexToLetter(threadIndex));
        autorouterThreads[threadIndex].setDaemon(true);
        autorouterThreads[threadIndex].setPriority(Thread.MIN_PRIORITY);
      }

      // Update the board on the GUI only based on the first thread
      autorouterThreads[0].addBoardUpdatedEventListener(new BoardUpdatedEventListener() {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
          air_line = autorouterThreads[0].latest_air_line;
          fireBoardUpdatedEvent(event.getBoardStatistics(), event.getRouterCounters(), event.getBoard());
        }
      });

      // Start the threads
      for (int threadIndex = 0; threadIndex < job.routerSettings.maxThreads; threadIndex++) {
        // start the thread
        autorouterThreads[threadIndex].start();
      }

      // Wait for the threads to finish
      for (int threadIndex = 0; threadIndex < job.routerSettings.maxThreads; threadIndex++) {
        BatchAutorouterThread autorouterThread = autorouterThreads[threadIndex];

        // wait for the thread to finish
        try {
          autorouterThread.join(TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
        } catch (InterruptedException e) {
          job.logError("Autorouter thread #" + p_pass_no + "." + ThreadIndexToLetter(threadIndex) + " was interrupted",
              e);
          this.thread.requestStop();
          break;
        }

        bh.add(autorouterThread.getBoard());

        // calculate the new board score
        BoardStatistics clonedBoardStatistics = autorouterThread
            .getBoard()
            .get_statistics();
        float clonedBoardScore = clonedBoardStatistics.getNormalizedScore(job.routerSettings.scoring);

        job.logDebug("Router thread #" + p_pass_no + "." + ThreadIndexToLetter(threadIndex) + " finished with score: "
            + FRLogger.formatScore(clonedBoardScore,
                clonedBoardStatistics.connections.incompleteCount,
                clonedBoardStatistics.clearanceViolations.totalCount));

        // Aggregate resource usage
        job.resourceUsage.cpuTimeUsed += autorouterThread.cpuTimeUsed;
        job.resourceUsage.maxMemoryUsed += autorouterThread.maxMemoryUsed;
      }

      BatchAutorouterThread bestThread = autorouterThreads[0];
      float bestScore = -Float.MAX_VALUE;

      // Find the best thread
      for (int i = 0; i < job.routerSettings.maxThreads; i++) {
        BoardStatistics stats = autorouterThreads[i].getBoard().get_statistics();
        float score = stats.getNormalizedScore(job.routerSettings.scoring);
        if (score > bestScore) {
          bestScore = score;
          bestThread = autorouterThreads[i];
        }
      }

      this.board = bh.restoreBestBoard();
      bh.clear();

      // Check if we made any progress
      boolean anyProgress = bestThread.getRoutedCount() > 0 || bestThread.getFailedCount() > 0;

      // We are done with this pass
      this.air_line = null;
      return anyProgress;
    } catch (Exception e) {
      job.logError("Something went wrong during the auto-routing", e);
      this.air_line = null;
      return false;
    }
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the
   * board is already completely routed.
   */
  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the
   * board is already completely routed.
   */
  private boolean autoroute_pass(int p_pass_no) {
    long passStartTime = System.currentTimeMillis();
    try {
      List<Item> autoroute_item_list = getAutorouteItems(this.board);

      // If there are no items to route, we're done
      if (autoroute_item_list.isEmpty()) {
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
      RatsNest ratsNest = new RatsNest(board);
      routerCounters.incompleteCount = ratsNest.incomplete_count();

      // Log incomplete details for debugging
      if (routerCounters.incompleteCount > 0) {
        job.logDebug("Pass #" + p_pass_no + ": " + routerCounters.incompleteCount + " incompletes across "
            + items_to_go_count + " items to route");
        for (int netNo = 1; netNo <= board.rules.nets.max_net_no(); netNo++) {
          int netIncompletes = ratsNest.incomplete_count(netNo);
          if (netIncompletes > 0) {
            Net net = board.rules.nets.get(netNo);
            String netName = (net != null) ? net.name : "net#" + netNo;
            job.logDebug("  Net '" + netName + "' has " + netIncompletes + " incomplete(s)");
          }
        }
      }

      this.fireBoardUpdatedEvent(stats, routerCounters, this.board);

      // TODO: Start mutliple instances of the following part in parallel, wait for
      // the results and keep the best one

      // Sort items by airline distance (shortest first) for deterministic routing
      // This prioritizes local connections which typically route faster
      // NOTE: Disabled in v2.3 because it negatively impacts convergence compared to
      // v1.9 (natural order)
      // autoroute_item_list.sort(Comparator.comparingDouble(this::calculateItemDistance));

      // Strategy-based sorting to optionally improve convergence
      sortItems(autoroute_item_list, p_pass_no);

      // Let's go through all items to route
      for (Item curr_item : autoroute_item_list) {
        // If the user requested to stop the auto-router, we stop it
        if (this.thread.is_stop_auto_router_requested()) {
          break;
        }

        // Check if this item should be skipped due to repeated failures
        if (board.failureLog.shouldSkip(curr_item)) {
          Net net = board.rules.nets.get(curr_item.get_net_no(0));
          String netName = (net != null) ? net.name : "net#" + curr_item.get_net_no(0);
          job.logDebug("Skipping " + curr_item.getClass().getSimpleName() + " on net '" + netName
              + "' - exceeded failure threshold (" + board.failureLog.getFailureCount(curr_item) + " failures)");
          --items_to_go_count;
          continue;
        }

        // Let's go through all nets of this item
        for (int i = 0; i < curr_item.net_count(); i++) {
          // If the user requested to stop the auto-router, we stop it
          if (this.thread.is_stop_auto_router_requested()) {
            break;
          }

          // OPTIMIZATION: Check if the item is already connected before doing expensive
          // setup
          // This avoids overhead if a previous item in this pass already routed this
          // connection
          Set<Item> unconnected_set = curr_item.get_unconnected_set(curr_item.get_net_no(i));

          if (unconnected_set.isEmpty()) {
            ++skipped;
            --items_to_go_count;
            // No need to fire event for simple skip
            continue;
          }

          // We visually mark the area of the board, which is changed by the auto-router
          board.start_marking_changed_area();

          // Do the auto-routing step for this item (typically PolylineTrace or Pin)
          // Reuse instance collection to reduce memory allocation
          reusable_ripped_item_list.clear();
          SortedSet<Item> ripped_item_list = reusable_ripped_item_list;

          // The item could not be routed, so we have to remove the ripped traces
          if (!ripped_item_list.isEmpty()) {
            for (Item curr_ripped_item : ripped_item_list) {
              board.remove_item(curr_ripped_item);
            }
          }

          if (this.totalItemsRouted >= job.routerSettings.maxItems) {
            job.logInfo("Max items limit reached (" + job.routerSettings.maxItems + "). Stopping auto-router.");
            this.thread.request_stop_auto_router();
            break;
          }
          this.totalItemsRouted++;

          int netNo = curr_item.get_net_no(i);

          // Debugging Check
          app.freerouting.debug.DebugControl.getInstance().check("autoroute_item", netNo, null);

          int incompletesBefore = ratsNest.incomplete_count(netNo);
          PerformanceProfiler.start("autoroute_item");
          var autorouterResult = autoroute_item(curr_item, netNo, ripped_item_list, p_pass_no);
          PerformanceProfiler.end("autoroute_item");
          int incompletesAfter = (new RatsNest(board)).incomplete_count(netNo);

          if (autorouterResult.state == AutorouteAttemptState.ROUTED) {
            // The item was successfully routed
            ++routed;
            job.logDebug("Item " + routed + " routed for net #" + netNo + ": incompletes " + incompletesBefore + " -> "
                + incompletesAfter);
          } else if (autorouterResult.state == AutorouteAttemptState.FAILED) {
          } else if ((autorouterResult.state == AutorouteAttemptState.ALREADY_CONNECTED)
              || (autorouterResult.state == AutorouteAttemptState.NO_UNCONNECTED_NETS)
              || (autorouterResult.state == AutorouteAttemptState.CONNECTED_TO_PLANE)) {
            // The item doesn't need to be routed
            ++skipped;
          } else {
            Net net = board.rules.nets.get(curr_item.get_net_no(i));
            String netName = (net != null) ? net.name : "net#" + curr_item.get_net_no(i);

            // Record the failure
            board.failureLog.recordFailure(curr_item, p_pass_no, autorouterResult.state, autorouterResult.details);

            job.logDebug("Autorouter " + autorouterResult.details);
            // Log details when we're down to last few items or item has many failures
            int failureCount = board.failureLog.getFailureCount(curr_item);
            if (items_to_go_count <= 5 || failureCount >= 3) {
              job.logDebug("Pass #" + p_pass_no + ": Failed to route " + curr_item.getClass().getSimpleName()
                  + " on net '" + netName + "' (" + items_to_go_count + " items remaining, "
                  + failureCount + " failures). State: " + autorouterResult.state);
            }
            ++not_routed;
          }
          --items_to_go_count;
          ripped_item_count += ripped_item_list.size();

          if (shouldFireBoardUpdate()) {
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
      }

      if (this.remove_unconnected_vias) {
        remove_tails(Item.StopConnectionOption.NONE);
      } else {
        remove_tails(Item.StopConnectionOption.FANOUT_VIA);
      }

      // Fire final update for this pass
      BoardStatistics boardStatistics = board.get_statistics();
      routerCounters.passCount = p_pass_no;
      routerCounters.queuedToBeRoutedCount = items_to_go_count;
      routerCounters.skippedCount = skipped;
      routerCounters.rippedCount = ripped_item_count;
      routerCounters.failedToBeRoutedCount = not_routed;
      routerCounters.routedCount = routed;
      routerCounters.incompleteCount = new RatsNest(board).incomplete_count();
      this.fireBoardUpdatedEvent(boardStatistics, routerCounters, this.board);

      long passDuration = System.currentTimeMillis() - passStartTime;
      int currentRipupCost = this.start_ripup_costs * p_pass_no;
      PerformanceProfiler.recordPass(p_pass_no, routerCounters.incompleteCount, passDuration, currentRipupCost);

      // DEBUG: Log detailed pass summary including per-net unrouted items breakdown
      // This helps diagnose performance regressions by showing exactly which nets are
      // problematic
      if (routerCounters.incompleteCount > 0) {
        job.logDebug("=== Pass #" + p_pass_no + " Summary ===");
        job.logDebug("  Duration: " + (passDuration / 1000.0) + " seconds");
        job.logDebug("  Ripup cost: " + currentRipupCost);
        job.logDebug("  Routed: " + routed + ", Failed: " + not_routed + ", Skipped: " + skipped);
        job.logDebug("  Total incomplete connections: " + routerCounters.incompleteCount);
        job.logDebug("  Unrouted items breakdown by net:");

        // Show which nets have unrouted items - use fresh RatsNest for accurate
        // end-of-pass state
        RatsNest finalRatsNest = new RatsNest(board);
        int netsWithIncompletes = 0;
        for (int netNo = 1; netNo <= board.rules.nets.max_net_no(); netNo++) {
          int netIncompletes = finalRatsNest.incomplete_count(netNo);
          if (netIncompletes > 0) {
            netsWithIncompletes++;
            Net net = board.rules.nets.get(netNo);
            String netName = (net != null) ? net.name : "net#" + netNo;
            int netItemCount = board.connectable_item_count(netNo);
            job.logDebug("    Net '" + netName + "' (#" + netNo + "): " + netIncompletes + " incomplete(s), "
                + netItemCount + " total items");
          }
        }
        job.logDebug("  Total nets with incomplete connections: " + netsWithIncompletes);
        job.logDebug("========================");
      } else {
        job.logDebug("=== Pass #" + p_pass_no + " completed successfully - all items routed! ===");
      }

      // We are done with this pass
      this.air_line = null;
      return routed > 0 || not_routed > 0;
    } catch (Exception e) {
      job.logError("Something went wrong during the auto-routing", e);
      this.air_line = null;
      return false;
    }
  }

  @Override
  public String getId() {
    return "freerouting-router";
  }

  @Override
  public String getName() {
    return "Freerouting Auto-router";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public String getDescription() {
    return "Freerouting Auto-router v1.0";
  }

  @Override
  public NamedAlgorithmType getType() {
    return NamedAlgorithmType.ROUTER;
  }

  /**
   * Returns the initial number of unrouted nets at the start of the routing
   * session.
   */
  public int getInitialUnroutedCount() {
    return this.initialUnroutedCount;
  }

  /**
   * Returns the time when the routing session started.
   */
  public Instant getSessionStartTime() {
    return this.sessionStartTime;
  }

  /**
   * Autoroutes ripup passes until the board is completed or the autorouter is
   * stopped by the user. Returns true if the board is completed.
   */
  public boolean runBatchLoop() {
    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

    // VALIDATION: Check routing settings before starting (clearances, trace widths,
    // pin spacing)
    job.logDebug("Validating routing settings...");
    RoutingSettingsValidator validator = new RoutingSettingsValidator(this.board);
    boolean settings_valid = validator.validate();

    if (!settings_valid) {
      job.logWarning("Routing settings validation found critical issues. " +
          "Routing may fail or produce poor results. Check debug log for details.");
    }

    // Capture initial state for session summary
    this.sessionStartTime = Instant.now();
    RatsNest initialRatsNest = new RatsNest(this.board);
    this.initialUnroutedCount = initialRatsNest.incomplete_count();

    boolean continueAutorouting = true;
    BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

    // Record configuration for profiler
    if (this.settings.isLayerActive != null) {
      int layerCount = this.settings.getLayerCount();
      double[] prefCosts = new double[layerCount];
      double[] againstCosts = new double[layerCount];
      for (int i = 0; i < layerCount; i++) {
        prefCosts[i] = this.settings.get_preferred_direction_trace_costs(i);
        againstCosts[i] = this.settings.get_against_preferred_direction_trace_costs(i);
      }
      PerformanceProfiler.recordConfiguration(
          this.settings.get_via_costs(),
          this.settings.get_plane_via_costs(),
          prefCosts,
          againstCosts);
    }

    int currentPass = 1;
    while (continueAutorouting && !this.thread.is_stop_auto_router_requested()) {
      if (job != null && job.state == RoutingJobState.TIMED_OUT) {
        this.thread.request_stop_auto_router();
      }

      String currentBoardHash = this.board.get_hash();

      if (currentPass > this.settings.maxPasses) {
        thread.request_stop_auto_router();
        break;
      }

      if (job != null) {
        job.setCurrentPass(currentPass);
      }

      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.RUNNING, currentPass, currentBoardHash));

      float boardScoreBefore = new BoardStatistics(this.board).getNormalizedScore(job.routerSettings.scoring);
      bh.add(this.board);

      FRLogger.traceEntry("BatchAutorouter.autoroute_pass #" + currentPass + " on board '" + currentBoardHash + "'");

      continueAutorouting = autoroute_pass(currentPass);

      BoardStatistics boardStatisticsAfter = new BoardStatistics(this.board);
      float boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);

      if ((bh.size() >= STOP_AT_PASS_MINIMUM) || (this.thread.is_stop_auto_router_requested())) {
        if (((currentPass % STOP_AT_PASS_MODULO == 0) && (currentPass >= STOP_AT_PASS_MINIMUM))
            || (this.thread.is_stop_auto_router_requested())) {
          // Check if the score improved compared to the previous passes, restore a
          // previous board if not
          if (bh.getMaxScore() >= boardScoreAfter) {
            var boardToRestore = bh.restoreBoard(MAXIMUM_TRIES_ON_THE_SAME_BOARD);
            if (boardToRestore == null) {
              job.logInfo("The router was not able to improve the board, stopping the auto-router.");
              thread.request_stop_auto_router();
              break;
            }

            int boardToRestoreRank = bh.getRank(boardToRestore);

            if (boardToRestoreRank > BOARD_RANK_LIMIT) {
              thread.request_stop_auto_router();
              break;
            }

            this.board = boardToRestore;
            var boardStatistics = this.board.get_statistics();
            job.logInfo(
                "Restoring an earlier board that has the score of "
                    + FRLogger.formatScore(boardStatistics.getNormalizedScore(job.routerSettings.scoring),
                        boardStatistics.connections.incompleteCount,
                        boardStatistics.clearanceViolations.totalCount)
                    + ".");
          }
        }
      }
      double autorouter_pass_duration = FRLogger
          .traceExit("BatchAutorouter.autoroute_pass #" + currentPass + " on board '" + currentBoardHash + "'");

      String passCompletedMessage = "Auto-router pass #" + currentPass + " on board '" + currentBoardHash
          + "' was completed in " + FRLogger.formatDuration(autorouter_pass_duration) + " with the score of "
          + FRLogger.formatScore(boardScoreAfter, boardStatisticsAfter.connections.incompleteCount,
              boardStatisticsAfter.clearanceViolations.totalCount);
      if (job.resourceUsage.cpuTimeUsed > 0) {
        passCompletedMessage += ", using " + FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed)
            + " CPU seconds and the job allocated "
            + FRLogger.defaultFloatFormat.format(job.resourceUsage.maxMemoryUsed / 1024.0f) + " GB of memory so far.";
      } else {
        passCompletedMessage += ".";
      }
      job.logInfo(passCompletedMessage);

      if (this.settings.save_intermediate_stages) {
        fireBoardSnapshotEvent(this.board);
      }

      // check if there are still unrouted items
      if (continueAutorouting && !this.thread.is_stop_auto_router_requested()) {
        currentPass++;
      }
    }

    job.board = this.board;

    if (!(this.remove_unconnected_vias || continueAutorouting || this.thread.is_stop_auto_router_requested())) {
      // clean up the route if the board is completed and if fanout is used.
      remove_tails(Item.StopConnectionOption.NONE);
    }

    bh.clear();

    // Print all profiling results at the end of session
    PerformanceProfiler.printResults();
    PerformanceProfiler.reset();

    if (!this.thread.is_stop_auto_router_requested()) {
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.FINISHED,
          currentPass, this.board.get_hash()));
    } else {
      // TODO: set it to TIMED_OUT if it was interrupted because of timeout
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.CANCELLED,
          currentPass, this.board.get_hash()));
    }

    return !this.thread.is_stop_auto_router_requested();
  }

  private void remove_tails(Item.StopConnectionOption p_stop_connection_option) {
    board.start_marking_changed_area();
    board.remove_trace_tails(-1, p_stop_connection_option);
    board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, this.trace_cost_arr, this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  // Tries to route an item on a specific net. Returns true, if the item is
  // routed.
  private AutorouteAttemptResult autoroute_item(Item p_item, int p_route_net_no, SortedSet<Item> p_ripped_item_list,
      int p_ripup_pass_no) {

    FRLogger.trace("BatchAutorouter.autoroute_item", "autoroute_item",
        "Routing item of class " + p_item.getClass().getSimpleName() + " on net #" + p_route_net_no + " at ripup pass #" + p_ripup_pass_no,
        p_item.toString());

    try {
      boolean contains_plane = false;

      // Get the net
      Net route_net = board.rules.nets.get(p_route_net_no);
      if (route_net != null) {
        contains_plane = route_net.contains_plane();
      }

      // Get the current via costs based on auto-router settings
      int curr_via_costs;
      if (contains_plane) {
        curr_via_costs = this.settings.get_plane_via_costs();
      } else {
        curr_via_costs = this.settings.get_via_costs();
      }

      // Get and calculate the auto-router settings based on the board and net we are
      // working on
      AutorouteControl autoroute_control = new AutorouteControl(this.board, p_route_net_no, settings, curr_via_costs,
          this.trace_cost_arr);
      autoroute_control.ripup_allowed = true;
      autoroute_control.ripup_costs = this.start_ripup_costs + (p_ripup_pass_no * this.start_ripup_costs / 5);
      autoroute_control.remove_unconnected_vias = this.remove_unconnected_vias;

      // Check if the item is already routed
      Set<Item> unconnected_set = p_item.get_unconnected_set(p_route_net_no);
      if (unconnected_set.isEmpty()) {
        return new AutorouteAttemptResult(AutorouteAttemptState.NO_UNCONNECTED_NETS);
      }

      Set<Item> connected_set = p_item.get_connected_set(p_route_net_no);
      Set<Item> route_start_set;
      Set<Item> route_dest_set;
      if (contains_plane) {
        for (Item curr_item : connected_set) {
          if (curr_item instanceof ConductionArea) {
            return new AutorouteAttemptResult(AutorouteAttemptState.CONNECTED_TO_PLANE);
          }
        }
      }
      if (contains_plane) {
        route_start_set = connected_set;
        route_dest_set = unconnected_set;
      } else {
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
      AutorouteEngine autoroute_engine = board.init_autoroute(p_route_net_no,
          autoroute_control.trace_clearance_class_no, this.thread, time_limit, this.retain_autoroute_database);

      // Do the auto-routing between the two sets of items
      AutorouteAttemptResult autoroute_result = autoroute_engine.autoroute_connection(route_start_set, route_dest_set,
          autoroute_control, p_ripped_item_list);

      // Update the changed area of the board
      if (autoroute_result.state == AutorouteAttemptState.ROUTED) {
        board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, autoroute_control.trace_costs,
            this.thread, TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
      }

      return autoroute_result;
    } catch (Exception e) {
      FRLogger.error("Error during routing passes", e);
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED);
    }
  }

  /**
   * Returns the airline of the current autorouted connection or null, if no such
   * airline exists
   */
  public FloatLine get_air_line() {
    if (this.air_line == null) {
      return null;
    }
    if (this.air_line.a == null || this.air_line.b == null) {
      return null;
    }
    return this.air_line;
  }

  // Calculates the shortest distance between two sets of items, specifically
  // between Pin and Via items (pins and vias are connectable DrillItems)
  private void calc_airline(Collection<Item> p_from_items, Collection<Item> p_to_items) {
    FloatPoint from_corner = null;
    FloatPoint to_corner = null;
    double min_distance = Double.MAX_VALUE;
    for (Item curr_from_item : p_from_items) {
      FloatPoint curr_from_corner;
      if (curr_from_item instanceof DrillItem item) {
        curr_from_corner = item
            .get_center()
            .to_float();
      } else if (curr_from_item instanceof PolylineTrace from_trace) {
        // Use trace endpoints as potential connection points
        if (from_trace.first_corner() != null)
          curr_from_corner = from_trace.first_corner().to_float();
        else
          continue;
      } else {
        continue;
      }

      for (Item curr_to_item : p_to_items) {
        FloatPoint curr_to_corner;
        if (curr_to_item instanceof DrillItem item) {
          curr_to_corner = item
              .get_center()
              .to_float();
        } else if (curr_to_item instanceof PolylineTrace to_trace) {
          if (to_trace.first_corner() != null)
            curr_to_corner = to_trace.first_corner().to_float();
          else
            continue;
        } else {
          continue;
        }
        double curr_dist = curr_from_corner.distance(curr_to_corner);
        if (curr_dist < min_distance) {
          min_distance = curr_dist;
          from_corner = curr_from_corner;
          to_corner = curr_to_corner;
        }
      }
    }

    // Check trace-to-trace and trace-to-drill connections
    for (Item curr_from_item : p_from_items) {
      if (!(curr_from_item instanceof PolylineTrace from_trace)) {
        continue;
      }

      for (Item curr_to_item : p_to_items) {
        FloatPoint curr_from_corner;
        FloatPoint curr_to_corner;

        if (curr_to_item instanceof DrillItem item) {
          // Trace to drill item
          curr_to_corner = item
              .get_center()
              .to_float();
          curr_from_corner = nearest_point_on_trace(from_trace, curr_to_corner);
        } else if (curr_to_item instanceof PolylineTrace to_trace) {
          // Trace to trace - find closest points between the two traces
          FloatPoint[] closest_points = find_closest_points_between_traces(from_trace, to_trace);
          curr_from_corner = closest_points[0];
          curr_to_corner = closest_points[1];
        } else {
          continue;
        }

        double curr_distance = curr_from_corner.distance(curr_to_corner);
        if (curr_distance < min_distance) {
          min_distance = curr_distance;
          from_corner = curr_from_corner;
          to_corner = curr_to_corner;
        }
      }
    }

    if (from_corner != null && to_corner != null) {
      this.air_line = new FloatLine(from_corner, to_corner);
    } else {
      this.air_line = null;
    }
  }

  /**
   * Finds the nearest point on a trace to the given point
   */
  private FloatPoint nearest_point_on_trace(PolylineTrace p_trace, FloatPoint p_point) {
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

    if (distance_to_first < min_distance) {
      min_distance = distance_to_first;
      nearest_point = first_corner;
    }

    if (distance_to_last < min_distance) {
      min_distance = distance_to_last;
      nearest_point = last_corner;
    }

    // Check distances to line segments
    for (int i = 0; i < p_trace.corner_count() - 1; i++) {
      FloatPoint segment_start = p_trace
          .polyline()
          .corner_approx(i);
      FloatPoint segment_end = p_trace
          .polyline()
          .corner_approx(i + 1);
      FloatLine segment = new FloatLine(segment_start, segment_end);

      FloatPoint projection = segment.perpendicular_projection(p_point);
      if (projection.is_contained_in_box(segment_start, segment_end, 0.01)) {
        double distance = p_point.distance(projection);
        if (distance < min_distance) {
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
   * @return an array with two FloatPoints: [point_on_first_trace,
   *         point_on_second_trace]
   */
  private FloatPoint[] find_closest_points_between_traces(PolylineTrace p_first_trace, PolylineTrace p_second_trace) {
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
    if (distance < min_distance) {
      min_distance = distance;
      result[0] = first_trace_start;
      result[1] = second_trace_start;
    }

    distance = first_trace_start.distance(second_trace_end);
    if (distance < min_distance) {
      min_distance = distance;
      result[0] = first_trace_start;
      result[1] = second_trace_end;
    }

    distance = first_trace_end.distance(second_trace_start);
    if (distance < min_distance) {
      min_distance = distance;
      result[0] = first_trace_end;
      result[1] = second_trace_start;
    }

    distance = first_trace_end.distance(second_trace_end);
    if (distance < min_distance) {
      min_distance = distance;
      result[0] = first_trace_end;
      result[1] = second_trace_end;
    }

    // Check all segment combinations for closest points
    for (int i = 0; i < p_first_trace.corner_count() - 1; i++) {
      FloatPoint first_segment_start = p_first_trace
          .polyline()
          .corner_approx(i);
      FloatPoint first_segment_end = p_first_trace
          .polyline()
          .corner_approx(i + 1);
      FloatLine first_segment = new FloatLine(first_segment_start, first_segment_end);

      for (int j = 0; j < p_second_trace.corner_count() - 1; j++) {
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
        if (!point_on_second.is_contained_in_box(second_segment_start, second_segment_end, 0.01)) {
          // If not, use the nearest endpoint
          double dist_to_start = point_on_first.distance(second_segment_start);
          double dist_to_end = point_on_first.distance(second_segment_end);
          point_on_second = dist_to_start < dist_to_end ? second_segment_start : second_segment_end;
        }

        // Recalculate the point on first segment based on the point on second segment
        point_on_first = first_segment.nearest_segment_point(point_on_second);

        distance = point_on_first.distance(point_on_second);
        if (distance < min_distance) {
          min_distance = distance;
          result[0] = point_on_first;
          result[1] = point_on_second;
        }
      }
    }

    return result;
  }

  /**
   * Return an uppercase one-letter, two-letter or three-letter string based on
   * the thread index (0 = A, 1 = B, 2 = C, ..., 26 = AA, 27 = AB, ...).
   *
   * @param threadIndex
   * @return
   */
  private String ThreadIndexToLetter(int threadIndex) {
    if (threadIndex < 0) {
      return "";
    }
    if (threadIndex < 26) {
      return String.valueOf((char) ('A' + threadIndex));
    } else if (threadIndex < 26 * 26) {
      int firstLetterIndex = threadIndex / 26;
      int secondLetterIndex = threadIndex % 26;
      return String.valueOf((char) ('A' + firstLetterIndex)) + (char) ('A' + secondLetterIndex);
    } else {
      int firstLetterIndex = threadIndex / (26 * 26);
      int secondLetterIndex = (threadIndex / 26) % 26;
      int thirdLetterIndex = threadIndex % 26;
      return String.valueOf((char) ('A' + firstLetterIndex)) + (char) ('A' + secondLetterIndex)
          + (char) ('A' + thirdLetterIndex);
    }
  }

  /**
   * Calculates the airline distance for an item to be routed.
   * Returns the shortest distance from the item to any item in its incomplete
   * connections.
   *
   * @param p_item The item to calculate distance for
   * @return The shortest airline distance, or Double.MAX_VALUE if no connections
   *         exist
   */
  private double calculateItemDistance(Item p_item) {
    if (p_item.net_count() == 0) {
      return Double.MAX_VALUE;
    }

    // Get the first net number (items typically have one net)
    int net_no = p_item.get_net_no(0);

    // Get incomplete items for this net
    Set<Item> unconnected_set = p_item.get_unconnected_set(net_no);
    Set<Item> connected_set = p_item.get_connected_set(net_no);

    if (unconnected_set.isEmpty()) {
      return 0; // Already connected, prioritize
    }

    // Calculate minimum distance from connected items to unconnected items
    return calculateMinDistance(connected_set.isEmpty() ? Set.of(p_item) : connected_set, unconnected_set);
  }

  /**
   * Helper method to calculate the minimum distance between two sets of items.
   */
  private double calculateMinDistance(Collection<Item> p_from_items, Collection<Item> p_to_items) {
    double min_distance = Double.MAX_VALUE;

    for (Item from_item : p_from_items) {
      FloatPoint from_point = getItemReferencePoint(from_item);
      if (from_point == null)
        continue;

      for (Item to_item : p_to_items) {
        FloatPoint to_point = getItemReferencePoint(to_item);
        if (to_point == null)
          continue;

        double distance = from_point.distance(to_point);
        if (distance < min_distance) {
          min_distance = distance;
        }
      }
    }

    return min_distance;
  }

  /**
   * Gets a representative point for an item (center for DrillItems, midpoint for
   * traces).
   */
  private FloatPoint getItemReferencePoint(Item p_item) {
    if (p_item instanceof DrillItem drillItem) {
      return drillItem.get_center().to_float();
    } else if (p_item instanceof PolylineTrace trace) {
      // Use the midpoint of the trace as a reference
      FloatPoint first = trace.first_corner().to_float();
      FloatPoint last = trace.last_corner().to_float();
      return new FloatPoint((first.x + last.x) / 2, (first.y + last.y) / 2);
    }
    return null;
  }

  /**
   * Sorts the items to be routed based on the selected strategy.
   * Defined strategies:
   * 1. Shortest Manhattan Distance First
   * 2. Longest Manhattan Distance First
   * 3. Smallest Bounding Box Area First
   * 4. Least Vias Potential First (same layer)
   * 5. Random Shuffle
   */
  private void sortItems(List<Item> items, int passNo) {
    // Select strategy based on pass number or randomization
    // Pass 1: Shortest (Clean up easy stuff)
    // Pass 2: Longest (Difficult paths)
    // Pass 3: Smallest Area (Dense areas)
    // Pass 4: Random
    // Pass 5+: Random (Cycle)

    int strategy = (passNo - 1) % 6;

    // Override: Use Natural Order (No Sort) for Pass 1 as it proved best in
    // benchmarks (69 unrouted vs 73-90 with sorting)
    if (passNo == 1)
      strategy = 5;

    switch (strategy) {
      case 5: // Natural (No Sort)
        FRLogger.info("Pass " + passNo + ": Sorted items by Natural Order (Insertion Order)");
        break;
      case 0: // Shortest Distance
        // Sort by min distance
        items.sort(Comparator.comparingDouble((Item item) -> this.calculateMinDistance(item)));
        FRLogger.info("Pass " + passNo + ": Sorted items by Shortest Distance First");
        break;
      case 1: // Longest Distance
        items.sort(Comparator.comparingDouble((Item item) -> this.calculateMinDistance(item)).reversed());
        FRLogger.info("Pass " + passNo + ": Sorted items by Longest Distance First");
        break;
      case 2: // Smallest Box Area
        items.sort(Comparator.comparingDouble(item -> item.bounding_box().area()));
        FRLogger.info("Pass " + passNo + ": Sorted items by Smallest Area First");
        break;
      case 3: // Least Vias Potential (Same Layer preferred)
        items.sort((item1, item2) -> {
          boolean item1SameLayer = isSameLayerAsTarget(item1);
          boolean item2SameLayer = isSameLayerAsTarget(item2);
          if (item1SameLayer && !item2SameLayer)
            return -1;
          if (!item1SameLayer && item2SameLayer)
            return 1;
          return 0;
        });
        FRLogger.info("Pass " + passNo + ": Sorted items by Least Vias Potential");
        break;
      default: // Random
        Collections.shuffle(items, this.random);
        FRLogger.info("Pass " + passNo + ": Sorted items Randomly");
        break;
    }
  }

  private boolean isSameLayerAsTarget(Item item) {
    if (item.net_count() == 0)
      return false;
    int netNo = item.get_net_no(0);
    Set<Item> destSet = item.get_unconnected_set(netNo);
    for (Item dest : destSet) {
      if (item.shares_layer(dest))
        return true;
    }
    return false;
  }

  private double calculateMinDistance(Item item) {
    double minInfoDistance = Double.MAX_VALUE;
    for (int i = 0; i < item.net_count(); i++) {
      int netNo = item.get_net_no(i);
      Set<Item> connected = item.get_connected_set(netNo);
      Set<Item> unconnected = item.get_unconnected_set(netNo);

      if (unconnected.isEmpty()) {
        minInfoDistance = 0;
        continue;
      }

      // Use existing calculateMinDistance(Collection, Collection)
      double dist = calculateMinDistance(connected.isEmpty() ? Set.of(item) : connected, unconnected);
      if (dist < minInfoDistance) {
        minInfoDistance = dist;
      }
    }
    return minInfoDistance;
  }
}
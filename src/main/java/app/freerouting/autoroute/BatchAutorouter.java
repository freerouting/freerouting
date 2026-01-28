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
 * Orchestrates batch autorouting passes for automatically routing all incomplete connections on a board.
 *
 * <p>This class manages the complete autorouting workflow in a separate thread, executing multiple
 * passes with progressively relaxed constraints until all connections are routed or pass limits
 * are reached.
 *
 * <p><strong>Key Responsibilities:</strong>
 * <ul>
 *   <li><strong>Multi-Pass Routing:</strong> Execute successive routing passes with different strategies</li>
 *   <li><strong>Connection Selection:</strong> Choose which incomplete connections to route each pass</li>
 *   <li><strong>Progress Tracking:</strong> Monitor routing statistics and completion status</li>
 *   <li><strong>Event Notification:</strong> Notify listeners about routing progress and state changes</li>
 *   <li><strong>Thread Management:</strong> Support interruption and time limits for responsive UI</li>
 * </ul>
 *
 * <p><strong>Routing Strategy:</strong>
 * <ol>
 *   <li><strong>Pass 1:</strong> Route shortest connections first with strict constraints
 *     <ul>
 *       <li>Minimal via usage</li>
 *       <li>Tight clearances</li>
 *       <li>Prefer simpler paths</li>
 *     </ul>
 *   </li>
 *   <li><strong>Pass 2+:</strong> Progressively relax constraints for remaining connections
 *     <ul>
 *       <li>Allow more vias</li>
 *       <li>Wider search space</li>
 *       <li>Accept longer paths</li>
 *     </ul>
 *   </li>
 *   <li><strong>Completion:</strong> Stop when all routed, pass limit reached, or interrupted</li>
 * </ol>
 *
 * <p><strong>Connection Prioritization:</strong>
 * <ul>
 *   <li><strong>Airline Distance:</strong> Route shortest connections first (easier to route)</li>
 *   <li><strong>Net Grouping:</strong> Route connections of the same net together</li>
 *   <li><strong>Component Fanout:</strong> Special handling for SMD component connections</li>
 * </ul>
 *
 * <p><strong>Progress Monitoring:</strong>
 * The autorouter provides real-time feedback through:
 * <ul>
 *   <li>{@link BoardUpdatedEvent}: Fired after each routing iteration with statistics</li>
 *   <li>{@link TaskStateChangedEvent}: Fired when passes start/stop</li>
 *   <li>Current airline being routed (via {@link #get_air_line()})</li>
 *   <li>Incomplete connection count tracking</li>
 * </ul>
 *
 * <p><strong>Interruption:</strong>
 * The autorouter checks {@link StoppableThread#isStopRequested()} at strategic points:
 * <ul>
 *   <li>Between routing passes</li>
 *   <li>Between individual connections</li>
 *   <li>During long-running maze searches</li>
 * </ul>
 *
 * <p><strong>Integration with UI:</strong>
 * <ul>
 *   <li>Runs in {@link app.freerouting.interactive.AutorouterAndRouteOptimizerThread}</li>
 *   <li>Updates GUI through event listeners</li>
 *   <li>Provides visual feedback (current airline, progress)</li>
 *   <li>Allows user interruption at any time</li>
 * </ul>
 *
 * <p><strong>Performance Considerations:</strong>
 * <ul>
 *   <li>Database maintenance mode improves multi-pass performance</li>
 *   <li>Connection sorting minimizes routing conflicts</li>
 *   <li>Time limits prevent hanging on difficult connections</li>
 *   <li>Via minimization reduces subsequent routing difficulty</li>
 * </ul>
 *
 * <p><strong>Typical Usage:</strong>
 * <pre>{@code
 * RoutingJob job = ...; // routing job with settings
 * BatchAutorouter autorouter = new BatchAutorouter(board, true, job, thread);
 *
 * autorouter.addBoardUpdatedEventListener(event -> {
 *     System.out.println("Routed: " + event.getCompletedCount());
 * });
 *
 * autorouter.runBatchLoop(); // Execute routing
 * }</pre>
 *
 * @see NamedAlgorithm
 * @see AutorouteEngine
 * @see AutorouteControl
 * @see app.freerouting.interactive.AutorouterAndRouteOptimizerThread
 */
public class BatchAutorouter extends NamedAlgorithm {

  /**
   * Minimum board ranking threshold to consider for backtracking strategies.
   *
   * <p>Boards with ranking below this limit are not considered for reverting
   * when routing becomes difficult. Helps limit memory and prevents excessive backtracking.
   */
  private static final int BOARD_RANK_LIMIT = 50;

  /**
   * Maximum attempts to route the same board configuration before giving up.
   *
   * <p>Prevents infinite loops when a particular board state cannot be improved.
   * After this many tries, the autorouter moves on or terminates.
   */
  private static final int MAXIMUM_TRIES_ON_THE_SAME_BOARD = 3;

  /**
   * Time limit in milliseconds to prevent endless routing loops.
   *
   * <p>If a single routing operation exceeds this limit, it's terminated to
   * prevent the autorouter from hanging indefinitely on difficult connections.
   */
  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;

  /**
   * Minimum number of passes before checking for completion criteria.
   *
   * <p>The autorouter must complete at least this many passes before it can
   * stop based on insufficient progress. Ensures reasonable routing attempts
   * even if early progress is slow.
   */
  private static final int STOP_AT_PASS_MINIMUM = 8;

  /**
   * Pass number interval for checking if progress is insufficient to continue.
   *
   * <p>Every STOP_AT_PASS_MODULO passes, the autorouter checks if improvements
   * are too small to justify continuing. Helps terminate on diminishing returns.
   */
  private static final int STOP_AT_PASS_MODULO = 4;

  /**
   * Flag indicating whether to remove unconnected vias after routing.
   *
   * <p>When true, vias that aren't part of any complete connection are removed
   * during cleanup. This reduces clutter and potential DRC violations.
   */
  private final boolean remove_unconnected_vias;

  /**
   * Array of trace cost factors per layer for routing strategy.
   *
   * <p>Each layer has minimum and maximum cost factors that influence routing
   * preferences. Higher costs discourage routing on that layer.
   *
   * <p><strong>Includes:</strong>
   * <ul>
   *   <li>Preferred direction costs (if enabled)</li>
   *   <li>Per-layer routing difficulty penalties</li>
   * </ul>
   *
   * @see AutorouteControl.ExpansionCostFactor
   */
  private final AutorouteControl.ExpansionCostFactor[] trace_cost_arr;

  /**
   * Flag controlling whether to maintain the autoroute database between passes.
   *
   * <p>When true:
   * <ul>
   *   <li>Expansion rooms are preserved between connections</li>
   *   <li>Performance improved for subsequent routing</li>
   *   <li>Memory usage increases</li>
   * </ul>
   *
   * <p>When false:
   * <ul>
   *   <li>Database rebuilt for each connection</li>
   *   <li>Lower memory footprint</li>
   *   <li>Slower multi-pass routing</li>
   * </ul>
   */
  private final boolean retain_autoroute_database;

  /**
   * Initial ripup cost value for the routing algorithm.
   *
   * <p>Ripup costs determine how aggressively the autorouter removes existing
   * traces to make new connections. Higher values make ripup less likely.
   *
   * <p>Typical values: 10-100 (lower = more aggressive ripup)
   */
  private final int start_ripup_costs;

  /**
   * Accuracy level for trace pull-tight optimization (1-4).
   *
   * <p>Higher values produce tighter, more optimized traces but take longer:
   * <ul>
   *   <li>1: Fast, minimal optimization</li>
   *   <li>2: Moderate optimization</li>
   *   <li>3: Good optimization (recommended)</li>
   *   <li>4: Maximum optimization (slow)</li>
   * </ul>
   */
  private final int trace_pull_tight_accuracy;

  /**
   * Reusable collection for items that were ripped up during routing.
   *
   * <p>Thread-safe reuse reduces memory allocation overhead. Cleared and
   * reused for each connection attempt.
   */
  private final SortedSet<Item> reusable_ripped_item_list = new TreeSet<>();

  /**
   * Reusable collection for items being auto-routed.
   *
   * <p>Thread-safe reuse reduces memory allocation overhead. Cleared and
   * reused for each routing iteration.
   */
  private final List<Item> reusable_autoroute_item_list = new ArrayList<>();

  /**
   * Reusable set tracking which items have already been processed.
   *
   * <p>Thread-safe reuse reduces memory allocation overhead. Prevents
   * duplicate routing attempts on the same items.
   */
  private final Set<Item> reusable_handled_items = new TreeSet<>();

  /**
   * The routing job context containing configuration and state.
   *
   * @see RoutingJob
   */
  protected RoutingJob job;

  /**
   * Random number generator for routing decisions requiring randomness.
   *
   * <p>Used for:
   * <ul>
   *   <li>Tie-breaking between equivalent routing options</li>
   *   <li>Shuffling connection order to avoid biases</li>
   *   <li>Introducing controlled randomness in heuristics</li>
   * </ul>
   */
  private Random random;

  /**
   * The airline (straight-line connection) of the current incomplete being routed.
   *
   * <p>Used for visual feedback in GUI mode to show which connection is currently
   * being processed. Updated before routing each connection.
   *
   * @see #get_air_line()
   */
  private FloatLine air_line;

  /**
   * Count of incomplete connections at the start of the routing session.
   *
   * <p>Used to calculate progress percentage and determine if routing is making
   * forward progress.
   */
  private int initialUnroutedCount;

  /**
   * Timestamp when the routing session began.
   *
   * <p>Used for:
   * <ul>
   *   <li>Calculating total routing duration</li>
   *   <li>Performance metrics and logging</li>
   *   <li>Session analytics</li>
   * </ul>
   */
  private Instant sessionStartTime;

  /**
   * Timestamp of the last board update event in milliseconds.
   *
   * <p>Used to throttle board update event frequency and prevent overwhelming
   * the UI with too many updates.
   */
  private long lastBoardUpdateTimestamp = 0;

  /**
   * Total count of items successfully routed during this session.
   *
   * <p>Tracks cumulative routing progress across all passes.
   */
  private int totalItemsRouted = 0;

  /**
   * Creates a batch autorouter from a routing job configuration.
   *
   * <p>Convenience constructor that extracts all necessary parameters from the
   * routing job. This is the primary constructor used in production code.
   *
   * <p><strong>Extracted Parameters:</strong>
   * <ul>
   *   <li>Thread: For interruption checks</li>
   *   <li>Board: The routing board to operate on</li>
   *   <li>Settings: Router configuration and preferences</li>
   *   <li>Ripup costs: Initial aggressiveness for removing existing traces</li>
   *   <li>Pull-tight accuracy: Trace optimization level</li>
   * </ul>
   *
   * @param job the routing job containing all configuration
   *
   * @see RoutingJob
   */
  public BatchAutorouter(RoutingJob job) {
    this(job.thread, job.board, job.routerSettings, true, true,
        job.routerSettings.get_start_ripup_costs(), job.routerSettings.trace_pull_tight_accuracy);
    this.job = job;
  }

  /**
   * Creates a batch autorouter with explicit configuration parameters.
   *
   * <p>Provides full control over autorouter behavior. This constructor is used
   * for testing or when custom configurations are needed.
   *
   * <p><strong>Configuration Details:</strong>
   * <ul>
   *   <li><strong>Unconnected Vias:</strong> When true, orphaned vias are removed after routing</li>
   *   <li><strong>Preferred Directions:</strong> When true, respects layer-specific routing preferences</li>
   *   <li><strong>Ripup Costs:</strong> Initial threshold for removing existing traces (10-100 typical)</li>
   *   <li><strong>Pull-tight Accuracy:</strong> Trace optimization level (1-4, higher = better/slower)</li>
   * </ul>
   *
   * <p><strong>Trace Cost Calculation:</strong>
   * If preferred directions are enabled, uses the full cost array from settings.
   * Otherwise, creates uniform costs across all layers (no directional preference).
   *
   * <p><strong>Database Retention:</strong>
   * Automatically determines whether to retain the autoroute database based on
   * board complexity and routing requirements.
   *
   * @param p_thread the stoppable thread for interruption checks
   * @param board the routing board to operate on
   * @param settings router configuration and preferences
   * @param p_remove_unconnected_vias true to remove orphaned vias after routing
   * @param p_with_preferred_directions true to respect layer-specific routing directions
   * @param p_start_ripup_costs initial ripup cost threshold (10-100 typical)
   * @param p_pull_tight_accuracy trace optimization level (1-4)
   *
   * @see RouterSettings#get_trace_cost_arr()
   */
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
   * Executes one autorouting pass, attempting to route all incomplete connections.
   *
   * <p>A single pass processes all incomplete connections on the board in priority order,
   * attempting to route each one using the maze search algorithm with the current pass
   * number's constraints.
   *
   * <p><strong>Pass Execution:</strong>
   * <ol>
   *   <li>Collect all items requiring routing (incomplete connections)</li>
   *   <li>Sort by priority (airline distance, net grouping)</li>
   *   <li>For each item:
   *     <ul>
   *       <li>Check for interruption</li>
   *       <li>Set up autoroute engine and control</li>
   *       <li>Attempt routing with rip-up if needed</li>
   *       <li>Update statistics and notify listeners</li>
   *     </ul>
   *   </li>
   *   <li>Clean up and prepare for next pass</li>
   * </ol>
   *
   * <p><strong>Progress Tracking:</strong>
   * Updates the following counters during execution:
   * <ul>
   *   <li><strong>routed:</strong> Successfully routed connections</li>
   *   <li><strong>not_routed:</strong> Failed routing attempts</li>
   *   <li><strong>ripped_item_count:</strong> Items removed to make room</li>
   *   <li><strong>skipped:</strong> Items skipped (already routed or invalid)</li>
   * </ul>
   *
   * <p><strong>Event Notifications:</strong>
   * Fires {@link BoardUpdatedEvent} periodically (throttled to prevent UI flooding)
   * with current routing statistics.
   *
   * <p><strong>Return Value:</strong>
   * Returns false if:
   * <ul>
   *   <li>No items need routing (board complete)</li>
   *   <li>User requested interruption</li>
   * </ul>
   * Returns true if routing was attempted (regardless of success).
   *
   * @param p_pass_no the current pass number (1-based, affects routing constraints)
   * @return false if no routing needed or interrupted, true if routing attempted
   *
   * @see #getAutorouteItems
   * @see AutorouteEngine#autoroute_connection
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

  /**
   * Returns the unique identifier for this autorouter algorithm.
   *
   * @return the algorithm ID: "freerouting-router"
   */
  @Override
  public String getId() {
    return "freerouting-router";
  }

  /**
   * Returns the human-readable name of this autorouter algorithm.
   *
   * @return the algorithm name: "Freerouting Auto-router"
   */
  @Override
  public String getName() {
    return "Freerouting Auto-router";
  }

  /**
   * Returns the version number of this autorouter algorithm.
   *
   * @return the version string: "1.0"
   */
  @Override
  public String getVersion() {
    return "1.0";
  }

  /**
   * Returns a description of this autorouter algorithm.
   *
   * @return the algorithm description including version
   */
  @Override
  public String getDescription() {
    return "Freerouting Auto-router v1.0";
  }

  /**
   * Returns the type classification of this algorithm.
   *
   * @return {@link NamedAlgorithmType#ROUTER}
   */
  @Override
  public NamedAlgorithmType getType() {
    return NamedAlgorithmType.ROUTER;
  }

  /**
   * Returns the initial count of incomplete connections at session start.
   *
   * <p>Used to calculate routing progress as a percentage:
   * <pre>
   * progress = (initialUnroutedCount - currentIncomplete) / initialUnroutedCount
   * </pre>
   *
   * @return the count of incomplete connections when routing began
   */
  public int getInitialUnroutedCount() {
    return this.initialUnroutedCount;
  }

  /**
   * Returns the timestamp when the routing session started.
   *
   * <p>Used for:
   * <ul>
   *   <li>Calculating total routing duration</li>
   *   <li>Performance metrics and analytics</li>
   *   <li>Session logging and debugging</li>
   * </ul>
   *
   * @return the session start time, or null if not started yet
   */
  public Instant getSessionStartTime() {
    return this.sessionStartTime;
  }

  /**
   * Executes the complete batch autorouting workflow until completion or interruption.
   *
   * <p>This is the main entry point for batch autorouting. It orchestrates the entire
   * routing process from initialization through multiple passes to completion.
   *
   * <p><strong>Execution Flow:</strong>
   * <ol>
   *   <li><strong>Initialization:</strong>
   *     <ul>
   *       <li>Validate routing settings (clearances, trace widths, etc.)</li>
   *       <li>Count initial incomplete connections</li>
   *       <li>Initialize statistics and timing</li>
   *       <li>Fire STARTED event</li>
   *     </ul>
   *   </li>
   *   <li><strong>Routing Passes:</strong>
   *     <ul>
   *       <li>Execute autoroute_pass() repeatedly with increasing pass numbers</li>
   *       <li>Pass number affects ripup costs: cost = start_ripup_costs × pass_no</li>
   *       <li>Higher pass numbers = more aggressive rip-up and rerouting</li>
   *       <li>Continue until all routed, pass limit reached, or interrupted</li>
   *     </ul>
   *   </li>
   *   <li><strong>Completion Checks:</strong>
   *     <ul>
   *       <li>After minimum passes, check if progress is insufficient</li>
   *       <li>Stop if no items routed in recent passes (diminishing returns)</li>
   *       <li>Stop if user requested interruption</li>
   *     </ul>
   *   </li>
   *   <li><strong>Finalization:</strong>
   *     <ul>
   *       <li>Fire FINISHED or ABORTED event</li>
   *       <li>Log final statistics and session summary</li>
   *       <li>Clean up resources</li>
   *     </ul>
   *   </li>
   * </ol>
   *
   * <p><strong>Validation:</strong>
   * Before routing, validates:
   * <ul>
   *   <li>Clearance settings are sane (not too tight)</li>
   *   <li>Trace widths are appropriate for board resolution</li>
   *   <li>Component pin spacing allows routing</li>
   * </ul>
   * Logs warnings for potential issues but continues routing.
   *
   * <p><strong>Stopping Criteria:</strong>
   * Routing stops when:
   * <ul>
   *   <li><strong>Complete:</strong> All connections successfully routed</li>
   *   <li><strong>Pass Limit:</strong> Maximum passes reached without completion</li>
   *   <li><strong>No Progress:</strong> Recent passes made insufficient improvements</li>
   *   <li><strong>Interrupted:</strong> User requested stop via {@link StoppableThread}</li>
   * </ul>
   *
   * <p><strong>Return Value:</strong>
   * <ul>
   *   <li><strong>true:</strong> Board completely routed (all connections successful)</li>
   *   <li><strong>false:</strong> Routing incomplete (stopped early or failed)</li>
   * </ul>
   *
   * <p><strong>Thread Safety:</strong>
   * This method should only be called from a single thread (the routing thread).
   * Event listeners may be called from this thread.
   *
   * @return true if board is completely routed, false otherwise
   *
   * @see #autoroute_pass
   * @see TaskStateChangedEvent
   * @see RoutingSettingsValidator
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

  /**
   * Removes unnecessary trace tails (stubs) and optimizes the changed area.
   *
   * <p>Trace tails are trace segments that don't contribute to connectivity:
   * <ul>
   *   <li>Dead-end traces that lead nowhere</li>
   *   <li>Redundant vias at trace endpoints</li>
   *   <li>Stubs created during rip-up and reroute</li>
   * </ul>
   *
   * <p><strong>Process:</strong>
   * <ol>
   *   <li>Mark the changed area for tracking</li>
   *   <li>Remove trace tails based on stop connection option</li>
   *   <li>Optimize the changed area (pull-tight, smooth corners)</li>
   * </ol>
   *
   * <p>Uses the configured trace pull-tight accuracy and time limit to prevent
   * excessive optimization time.
   *
   * @param p_stop_connection_option determines what type of connection endpoints to stop at
   *        (e.g., stop at vias, stop at pads, etc.)
   *
   * @see RoutingBoard#remove_trace_tails
   * @see RoutingBoard#opt_changed_area
   */
  private void remove_tails(Item.StopConnectionOption p_stop_connection_option) {
    board.start_marking_changed_area();
    board.remove_trace_tails(-1, p_stop_connection_option);
    board.opt_changed_area(new int[0], null, this.trace_pull_tight_accuracy, this.trace_cost_arr, this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  /**
   * Attempts to route a single item on a specific net using the autorouter.
   *
   * <p>This is the core method that performs autorouting for one incomplete connection.
   * It sets up the autoroute engine, calculates the connection endpoints, and attempts
   * to find and insert a routing path.
   *
   * <p><strong>Algorithm Steps:</strong>
   * <ol>
   *   <li><strong>Setup:</strong>
   *     <ul>
   *       <li>Determine via costs (plane vs. signal net)</li>
   *       <li>Configure autoroute control with ripup costs</li>
   *       <li>Calculate time limit based on pass number</li>
   *     </ul>
   *   </li>
   *   <li><strong>Connection Analysis:</strong>
   *     <ul>
   *       <li>Get unconnected items on the net</li>
   *       <li>Get connected items (already routed)</li>
   *       <li>Determine start and destination sets</li>
   *       <li>Calculate and display airline (for GUI feedback)</li>
   *     </ul>
   *   </li>
   *   <li><strong>Routing:</strong>
   *     <ul>
   *       <li>Initialize autoroute engine for the net</li>
   *       <li>Attempt connection with rip-up enabled</li>
   *       <li>Handle routing result (success, failure, error)</li>
   *     </ul>
   *   </li>
   * </ol>
   *
   * <p><strong>Via Costs:</strong>
   * Plane nets (power/ground) use higher via costs to discourage vias that
   * would disrupt the plane. Signal nets use standard via costs.
   *
   * <p><strong>Ripup Costs:</strong>
   * Calculated as: {@code start_ripup_costs + (pass_no × start_ripup_costs / 5)}
   * <br>Higher pass numbers allow more aggressive rip-up of existing traces.
   *
   * <p><strong>Time Limits:</strong>
   * Exponentially increases with pass number: {@code 100000 × 2^(pass_no - 1)} ms
   * <br>Prevents hanging on difficult connections in later passes.
   *
   * <p><strong>Return States:</strong>
   * <ul>
   *   <li><strong>NO_UNCONNECTED_NETS:</strong> Item already fully connected</li>
   *   <li><strong>ROUTED:</strong> Successfully routed the connection</li>
   *   <li><strong>FAILED:</strong> Could not find a valid route</li>
   *   <li><strong>ALREADY_CONNECTED:</strong> Connection exists (should not happen)</li>
   * </ul>
   *
   * @param p_item the board item to route (pad, via, or trace endpoint)
   * @param p_route_net_no the net number to route on
   * @param p_ripped_item_list collection to accumulate items ripped up during routing
   * @param p_ripup_pass_no the current ripup pass number (affects aggressiveness)
   * @return the routing attempt result indicating success or failure reason
   *
   * @see AutorouteEngine#autoroute_connection
   * @see AutorouteControl
   * @see #calc_airline
   */
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
   * Returns the airline of the current connection being routed, or null if none exists.
   *
   * <p>The airline is a straight line connecting the two items being routed,
   * used for visual feedback in GUI mode to show which connection the autorouter
   * is currently working on.
   *
   * <p><strong>Usage:</strong>
   * The GUI periodically calls this method to draw a line showing the current
   * routing target, helping users understand routing progress.
   *
   * <p><strong>Null Conditions:</strong>
   * Returns null when:
   * <ul>
   *   <li>No routing is currently in progress</li>
   *   <li>The airline hasn't been calculated yet</li>
   *   <li>Either endpoint is invalid</li>
   * </ul>
   *
   * @return the current airline as a line between two points, or null
   *
   * @see #calc_airline
   * @see FloatLine
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

  /**
   * Calculates the shortest airline distance between two sets of items for visual feedback.
   *
   * <p>Computes the straight-line connection between items in the "from" set (already
   * connected) and items in the "to" set (unconnected targets). The result is stored
   * in {@link #air_line} for GUI display.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li><strong>DrillItem to DrillItem:</strong> Simple center-to-center distance</li>
   *   <li><strong>DrillItem to Trace:</strong> Drill center to nearest trace endpoint</li>
   *   <li><strong>Trace to Trace:</strong> Find closest points between trace endpoints/segments</li>
   * </ol>
   *
   * <p><strong>Item Types Handled:</strong>
   * <ul>
   *   <li><strong>DrillItem:</strong> Pads and vias (uses center point)</li>
   *   <li><strong>PolylineTrace:</strong> Existing traces (uses endpoints or segments)</li>
   * </ul>
   *
   * <p><strong>Distance Calculation:</strong>
   * For each pair of items across the two sets:
   * <ul>
   *   <li>Calculate representative points (centers, endpoints, or segment points)</li>
   *   <li>Compute Euclidean distance</li>
   *   <li>Track minimum distance and corresponding points</li>
   * </ul>
   *
   * <p><strong>Result:</strong>
   * Sets {@link #air_line} to a line connecting the two closest points found,
   * or null if no valid connection points exist.
   *
   * <p><strong>Performance Note:</strong>
   * This is O(n×m) where n and m are the sizes of the item sets. For large
   * sets, this could be slow, but typically connection sets are small.
   *
   * @param p_from_items the set of already-connected items (source)
   * @param p_to_items the set of unconnected items (destination)
   *
   * @see #nearest_point_on_trace
   * @see #find_closest_points_between_traces
   */
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
   * Finds the nearest point on a trace to a given reference point.
   *
   * <p>Searches through all segments of the trace polyline to find the point
   * on the trace that is closest to the reference point.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Check distance to trace endpoints (first and last corners)</li>
   *   <li>For each segment, project the point onto the segment line</li>
   *   <li>Check if projection falls within segment bounds</li>
   *   <li>Track minimum distance and corresponding point</li>
   * </ol>
   *
   * <p><strong>Segment Projection:</strong>
   * Uses perpendicular projection to find the closest point on each segment.
   * If the projection falls outside the segment, uses the nearest endpoint instead.
   *
   * <p><strong>Edge Cases:</strong>
   * <ul>
   *   <li>Returns first corner if trace has fewer than 2 corners</li>
   *   <li>Returns endpoint if it's closer than any segment point</li>
   * </ul>
   *
   * @param p_trace the trace to find the nearest point on
   * @param p_point the reference point to measure from
   * @return the nearest point on the trace, or first corner if trace is too short
   *
   * @see FloatLine#nearest_segment_point
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
   * Finds the pair of closest points between two trace polylines.
   *
   * <p>Computes the minimum distance between any two points on the two traces,
   * considering both endpoints and interior points along trace segments.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li><strong>Endpoint Comparison:</strong>
   *     <ul>
   *       <li>Check all 4 combinations of trace endpoints</li>
   *       <li>Track minimum distance and point pair</li>
   *     </ul>
   *   </li>
   *   <li><strong>Segment-to-Segment:</strong>
   *     <ul>
   *       <li>For each segment pair, find closest approach points</li>
   *       <li>Use perpendicular projection for accurate distances</li>
   *       <li>Validate projections fall within segment bounds</li>
   *       <li>Fall back to endpoints if projection is out of bounds</li>
   *     </ul>
   *   </li>
   *   <li><strong>Result:</strong> Return the pair with minimum distance</li>
   * </ol>
   *
   * <p><strong>Complexity:</strong>
   * O(n×m) where n and m are the number of segments in each trace.
   * For typical traces with few corners, this is acceptable.
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Calculating airline distance for GUI feedback</li>
   *   <li>Determining optimal connection points between existing traces</li>
   *   <li>Analyzing trace proximity</li>
   * </ul>
   *
   * @param p_first_trace the first trace polyline
   * @param p_second_trace the second trace polyline
   * @return array of 2 points: [0] = point on first trace, [1] = point on second trace
   *
   * @see FloatLine#nearest_segment_point
   * @see FloatLine#perpendicular_projection
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
   * Converts a thread index to an alphabetic identifier for logging and debugging.
   *
   * <p>Generates compact thread labels using uppercase letters in Excel-style column notation:
   * <ul>
   *   <li>0-25: A-Z (single letter)</li>
   *   <li>26-701: AA-ZZ (two letters)</li>
   *   <li>702+: AAA-ZZZ (three letters)</li>
   * </ul>
   *
   * <p><strong>Examples:</strong>
   * <ul>
   *   <li>0 → "A"</li>
   *   <li>1 → "B"</li>
   *   <li>25 → "Z"</li>
   *   <li>26 → "AA"</li>
   *   <li>27 → "AB"</li>
   *   <li>701 → "ZZ"</li>
   *   <li>702 → "AAA"</li>
   * </ul>
   *
   * <p><strong>Use Case:</strong>
   * In multi-threaded routing scenarios, provides readable thread identifiers
   * for log messages and debugging output.
   *
   * @param threadIndex the zero-based thread index
   * @return alphabetic identifier string, or empty string if index is negative
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
   * Calculates the minimum airline distance between two sets of items.
   *
   * <p>Computes the shortest straight-line distance between any item in the
   * "from" set and any item in the "to" set. Uses representative points for
   * each item (centers for drills, midpoints for traces).
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>For each item in the from set, get its reference point</li>
   *   <li>For each item in the to set, get its reference point</li>
   *   <li>Calculate Euclidean distance between each pair</li>
   *   <li>Track and return the minimum distance found</li>
   * </ol>
   *
   * <p><strong>Null Handling:</strong>
   * Skips items that don't have valid reference points (e.g., unsupported item types).
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Item sorting by connection distance</li>
   *   <li>Prioritization of short connections</li>
   *   <li>Airline distance calculation for GUI</li>
   * </ul>
   *
   * @param p_from_items the source item set
   * @param p_to_items the destination item set
   * @return the minimum distance found, or Double.MAX_VALUE if no valid pairs exist
   *
   * @see #getItemReferencePoint
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
   * Gets a representative reference point for a board item for distance calculations.
   *
   * <p>Returns a single point that represents the item's position, used for:
   * <ul>
   *   <li>Calculating airline distances between items</li>
   *   <li>Sorting items by connection distance</li>
   *   <li>Determining routing priority</li>
   * </ul>
   *
   * <p><strong>Item Type Handling:</strong>
   * <ul>
   *   <li><strong>DrillItem (pads/vias):</strong> Returns the drill center point</li>
   *   <li><strong>PolylineTrace:</strong> Returns the midpoint between first and last corners</li>
   *   <li><strong>Other types:</strong> Returns null (not supported)</li>
   * </ul>
   *
   * <p><strong>Trace Midpoint Rationale:</strong>
   * Using the midpoint provides a reasonable approximation of the trace's
   * position for distance comparisons. More sophisticated approaches (e.g.,
   * center of mass) would be more accurate but significantly slower.
   *
   * @param p_item the board item to get a reference point for
   * @return the reference point, or null if item type is not supported
   *
   * @see DrillItem#get_center()
   * @see PolylineTrace#first_corner()
   * @see PolylineTrace#last_corner()
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
   * Sorts items to be routed using pass-specific strategies for optimal routing order.
   *
   * <p>Different passes use different sorting strategies to maximize routing success:
   * <ul>
   *   <li><strong>Pass 1:</strong> Natural order (insertion order) - benchmarked as optimal</li>
   *   <li><strong>Pass 2:</strong> Shortest distance first - route easy connections</li>
   *   <li><strong>Pass 3:</strong> Longest distance first - tackle difficult connections</li>
   *   <li><strong>Pass 4:</strong> Same-layer priority - minimize via usage</li>
   *   <li><strong>Pass 5:</strong> Random shuffle - escape local optima</li>
   *   <li><strong>Pass 6+:</strong> Natural order - cycle back to stable strategy</li>
   * </ul>
   *
   * <p><strong>Strategy Rationale:</strong>
   * <ul>
   *   <li><strong>Natural Order (Pass 1):</strong> Respects design structure, proved best
   *       in benchmarks (69 unrouted vs 73-90 with other strategies)</li>
   *   <li><strong>Shortest First (Pass 2):</strong> Quick wins on easy connections, builds
   *       momentum and reduces remaining work</li>
   *   <li><strong>Longest First (Pass 3):</strong> Difficult long connections may need
   *       specific paths; route before board gets too congested</li>
   *   <li><strong>Same Layer (Pass 4):</strong> Connections on same layer don't need vias,
   *       potentially simpler routing</li>
   *   <li><strong>Random (Pass 5):</strong> Break patterns that might be causing failures,
   *       explore different routing sequences</li>
   * </ul>
   *
   * <p><strong>Distance Calculation:</strong>
   * Uses airline (straight-line) distance from connected items to unconnected items
   * via {@link #calculateMinDistance(Item)}.
   *
   * <p><strong>Logging:</strong>
   * Logs the selected strategy for each pass to aid in debugging routing behavior.
   *
   * @param items the list of items to sort (modified in-place)
   * @param passNo the current pass number (1-based)
   *
   * @see #calculateMinDistance(Item)
   * @see #isSameLayerAsTarget
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

  /**
   * Checks if an item shares at least one layer with any of its unconnected targets.
   *
   * <p>Used for sorting strategy to prioritize same-layer connections, which don't
   * require vias and are generally easier to route.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Get the item's first net number</li>
   *   <li>Get all unconnected items on that net</li>
   *   <li>Check if item shares any layer with any unconnected item</li>
   * </ol>
   *
   * <p><strong>Use Case:</strong>
   * In "Least Vias Potential" sorting strategy (Pass 4), items that can connect
   * on the same layer are prioritized, potentially reducing via count.
   *
   * @param item the item to check
   * @return true if item shares a layer with at least one unconnected target, false otherwise
   *
   * @see Item#shares_layer
   * @see #sortItems
   */
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

  /**
   * Calculates the minimum airline distance for a single item to any of its targets.
   *
   * <p>Overloaded version that handles multi-net items by finding the shortest
   * connection across all nets the item belongs to.
   *
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>For each net the item belongs to:
   *     <ul>
   *       <li>Get connected items (already routed on this net)</li>
   *       <li>Get unconnected items (routing targets)</li>
   *       <li>Calculate minimum distance between the two sets</li>
   *     </ul>
   *   </li>
   *   <li>Return the shortest distance across all nets</li>
   * </ol>
   *
   * <p><strong>Special Cases:</strong>
   * <ul>
   *   <li>If unconnected set is empty: returns 0 (already connected, highest priority)</li>
   *   <li>If connected set is empty: uses the item itself as the starting point</li>
   * </ul>
   *
   * <p><strong>Use Cases:</strong>
   * <ul>
   *   <li>Sorting items by routing difficulty</li>
   *   <li>Prioritizing short connections in early passes</li>
   *   <li>Determining optimal routing order</li>
   * </ul>
   *
   * @param item the item to calculate distance for
   * @return minimum distance to any target, or 0 if already connected
   *
   * @see #calculateMinDistance(Collection, Collection)
   * @see #sortItems
   */
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
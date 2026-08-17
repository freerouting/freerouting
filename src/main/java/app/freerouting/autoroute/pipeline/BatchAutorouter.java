package app.freerouting.autoroute.pipeline;

import static java.util.Collections.shuffle;

import app.freerouting.autoroute.AutorouteAttemptResult;
import app.freerouting.autoroute.AutorouteAttemptState;
import app.freerouting.autoroute.BoardHistory;
import app.freerouting.autoroute.PerformanceProfiler;
import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.autoroute.maze.AutorouteControl;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Connectable;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Handles the sequencing of the auto-router passes. */
public final class BatchAutorouter extends NamedAlgorithm {

  // The lowest rank of the board to be selected to go back to.
  // Must not exceed BoardHistory.MAX_HISTORY_SIZE so the check can actually fire.
  private static final int BOARD_RANK_LIMIT = BoardHistory.MAX_HISTORY_SIZE;
  // Maximum number of tries on the same board
  private static final int MAXIMUM_TRIES_ON_THE_SAME_BOARD = 3;
  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;
  // The minimum number of passes to complete the board, unless all items are
  // routed
  private static final int STOP_AT_PASS_MINIMUM = 8;
  // The modulo of the pass number to check if the improvements were so small that
  // process should stop despite not all items are routed
  private static final int STOP_AT_PASS_MODULO = 4;
  // Number of consecutive passes with no meaningful score improvement before
  // aborting (prevents endless looping when items cannot be routed)
  private static final int STAGNATION_PASS_LIMIT = 10;
  // Number of no-improvement passes before attempting a one-time fanout-tail cleanup.
  private static final int FANOUT_RECOVERY_STAGNATION_PASSES = 3;
  // Progress statistics are informational only; avoid rebuilding the expensive snapshot for
  // every item while keeping the GUI reasonably current on large boards.
  private static final int PROGRESS_STATISTICS_ITEM_INTERVAL = 10;
  // Minimum score gain (on the 0–1000 normalized scale) that counts as a
  // meaningful improvement; gains smaller than this are treated as stagnation.
  private static final float STAGNATION_SCORE_THRESHOLD = 0.5F;
  private static final boolean BENCHMARK_PROFILE_ENABLED =
      Boolean.getBoolean("freerouting.benchmark.profile");
  private static final boolean BENCHMARK_RETAIN_AUTOROUTE_DATABASE =
      Boolean.getBoolean("freerouting.benchmark.retain_autoroute_database");

  private final boolean removeUnconnectedVias;
  private final AutorouteControl.ExpansionCostFactor[] traceCosts;
  private final boolean retainAutorouteDatabase;
  private final int startRipupCosts;
  private final int tracePullTightAccuracy;
  // Reusable collections to reduce memory churn (thread-safe as each thread has
  // its own BatchAutorouter instance)
  private final List<Item> reusableAutorouteItemList = new ArrayList<>();
  private final Set<Item> reusableHandledItems = new TreeSet<>();
  private final AutorouteConnectionRouter connectionRouter;
  protected RoutingJob job;
  private int totalItemsRouted;
  private boolean fanoutTimedOut;

  /** Time when the routing session started. */
  private Random random;

  /** Used to draw the airline of the current routed incomplete. */
  private FloatLine airLine;

  /** Initial number of unrouted nets at the start of the routing session. */
  private int initialUnroutedCount;

  /** Time when the routing session started. */
  private Instant sessionStartTime;

  private long lastBoardUpdateTimestamp;
  private boolean isOptimizerAutorouter;
  private long profileItemSelectionNanos;
  private long profileIntermediateStatisticsNanos;
  private long profileBoardStatisticsNanos;
  private long profileIncompleteDrcNanos;
  private long profileAutorouteItemNanos;
  private long profileMazeSearchNanos;
  private long profileOptChangedAreaNanos;
  private long profileTailRemovalNanos;
  private int profileRouteItemCount;
  private int profilePlaneItemCount;
  private BoardStatistics progressStatistics;
  private int progressItemsSinceStatistics;

  /** Creates a BatchAutorouter for the given routing job. */
  public BatchAutorouter(RoutingJob job) {
    this(
        job.thread,
        job.board,
        job.routerSettings,
        !job.routerSettings.isFanoutEnabled(),
        true,
        job.routerSettings.getStartRipupCosts(),
        job.routerSettings.tracePullTightAccuracy);
    this.job = job;
  }

  /** Creates a new BatchAutorouter instance. */
  public BatchAutorouter(
      StoppableThread thread,
      RoutingBoard board,
      RouterSettings settings,
      boolean removeUnconnectedVias,
      boolean withPreferredDirections,
      int startRipupCosts,
      int pullTightAccuracy) {
    super(thread, board, settings);

    this.random = new Random(0);

    this.removeUnconnectedVias = removeUnconnectedVias;
    if (withPreferredDirections) {
      this.traceCosts = this.settings.getTraceCosts();
    } else {
      // remove preferred direction
      this.traceCosts = new AutorouteControl.ExpansionCostFactor[this.board.getLayerCount()];
      for (int i = 0; i < this.traceCosts.length; i++) {
        double currentMinCost = this.settings.getPreferredDirectionTraceCosts(i);
        this.traceCosts[i] =
            new AutorouteControl.ExpansionCostFactor(currentMinCost, currentMinCost);
      }
    }

    this.startRipupCosts = startRipupCosts;
    this.tracePullTightAccuracy = pullTightAccuracy;
    // Retained state is deliberately opt-in for bounded benchmark experiments only. The default
    // remains the fresh-engine behavior used by production routing and parity comparisons.
    this.retainAutorouteDatabase = BENCHMARK_RETAIN_AUTOROUTE_DATABASE;
    this.connectionRouter = new AutorouteConnectionRouter(this);
  }

  static boolean isBenchmarkProfileEnabled() {
    return BENCHMARK_PROFILE_ENABLED;
  }

  boolean isRemoveUnconnectedVias() {
    return this.removeUnconnectedVias;
  }

  AutorouteControl.ExpansionCostFactor[] getTraceCosts() {
    return this.traceCosts;
  }

  boolean isRetainAutorouteDatabase() {
    return this.retainAutorouteDatabase;
  }

  int getStartRipupCosts() {
    return this.startRipupCosts;
  }

  int getTracePullTightAccuracy() {
    return this.tracePullTightAccuracy;
  }

  void addProfileMazeSearchNanos(long elapsedNanos) {
    this.profileMazeSearchNanos += elapsedNanos;
  }

  void addProfileOptChangedAreaNanos(long elapsedNanos) {
    this.profileOptChangedAreaNanos += elapsedNanos;
  }

  void setAirLine(FloatLine airLine) {
    this.airLine = airLine;
  }

  /**
   * Auto-routes ripup passes until the board is completed or the auto-router is stopped by the
   * user, or if maxPassCount is exceeded. Is currently used in the optimize via batch pass. Returns
   * the number of passes to complete the board or maxPassCount + 1, if the board is not completed.
   */
  public static int autoroutePassesForOptimizingItem(
      RoutingJob job,
      int maxPassCount,
      int ripupCosts,
      int tracePullTightAccuracy,
      boolean withPreferredDirections,
      RoutingBoard updatedRoutingBoard,
      RouterSettings routerSettings) {
    BatchAutorouter routerInstance =
        new BatchAutorouter(
            job.thread,
            updatedRoutingBoard,
            routerSettings,
            true,
            withPreferredDirections,
            ripupCosts,
            tracePullTightAccuracy);
    routerInstance.job = job;
    routerInstance.isOptimizerAutorouter = true;

    boolean stillUnroutedItems = true;
    int currentPassNo = 1;
    while (stillUnroutedItems
        && !job.thread.isStopAutoRouterRequested()
        && currentPassNo <= maxPassCount) {
      stillUnroutedItems = routerInstance.autoroutePass(currentPassNo);
      if (stillUnroutedItems
          && !job.thread.isStopAutoRouterRequested()
          && updatedRoutingBoard == null) {}
      ++currentPassNo;
    }
    routerInstance.removeTails(Item.StopConnectionOption.NONE);
    if (!stillUnroutedItems) {
      --currentPassNo;
    }
    return currentPassNo;
  }

  private static Point[] getImpactedPoints(Item item) {
    if (item instanceof Trace trace) {
      return new Point[] {trace.firstCorner(), trace.lastCorner()};
    }
    if (item instanceof Via via) {
      return new Point[] {via.getCenter()};
    }
    if (item instanceof Pin pin) {
      return new Point[] {pin.getCenter()};
    }
    if (item instanceof DrillItem drillItem) {
      return new Point[] {drillItem.getCenter()};
    }
    return new Point[0];
  }

  /**
   * Strict-DRC enforcement: if any trace/via inserted by the connection that just routed (item id
   * above {@code maxItemIdBefore}) carries a clearance violation, rip the whole set of new items
   * and report the connection FAILED, so the pass counts it as not routed and later passes (higher
   * ripup costs) retry it. Returns null when the connection is clean and may be kept.
   */
  public static AutorouteAttemptResult enforceStrictDrc(
      app.freerouting.board.RoutingBoard board, int routeNetNo, int maxItemIdBefore) {
    List<Item> newItems = new ArrayList<>();
    boolean hasViolation = false;
    for (Item currentItem : board.getConnectableItems(routeNetNo)) {
      if (currentItem.getId() <= maxItemIdBefore
          || !(currentItem instanceof Trace || currentItem instanceof app.freerouting.board.Via)) {
        continue;
      }
      newItems.add(currentItem);
      if (!hasViolation && !currentItem.clearanceViolations().isEmpty()) {
        hasViolation = true;
      }
    }
    if (!hasViolation) {
      return null;
    }
    board.removeItems(newItems);
    return new AutorouteAttemptResult(
        AutorouteAttemptState.FAILED,
        "strict_drc: connection ripped because "
            + newItems.size()
            + " new item(s) included clearance violations");
  }

  public boolean isFanoutTimedOut() {
    return this.fanoutTimedOut;
  }

  private boolean shouldFireBoardUpdate() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastBoardUpdateTimestamp
        > 250) { // Limit updates to 4 times per second (250ms)
      lastBoardUpdateTimestamp = currentTime;
      return true;
    }
    return false;
  }

  private List<Item> getAutorouteItems(RoutingBoard board) {
    // Reuse instance collections to reduce memory allocation
    reusableAutorouteItemList.clear();
    reusableHandledItems.clear();
    List<Item> autorouteItemList = reusableAutorouteItemList;
    Set<Item> handledItems = reusableHandledItems;
    Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
    for (; ; ) {
      UndoableObjects.Storable currentObject = board.itemList.readObject(it);
      if (currentObject == null) {
        break;
      }
      if (currentObject instanceof Connectable && currentObject instanceof Item currentItem) {
        // This is a connectable item, like PolylineTrace or Pin
        if (!currentItem.isRoutable()) {
          if (!handledItems.contains(currentItem)) {

            // Let's go through all nets of this item
            for (int i = 0; i < currentItem.netCount(); i++) {
              int currentNetNumber = currentItem.getNetNumber(i);
              Set<Item> connectedSet = currentItem.getConnectedSet(currentNetNumber);
              for (Item currentConnectedItem : connectedSet) {
                if (currentConnectedItem.netCount() <= 1) {
                  handledItems.add(currentConnectedItem);
                }
              }
              int netItemCount = board.connectableItemCount(currentNetNumber);

              // If the item is not connected to all other items of the net, we add it to the
              // auto-router's to-do list
              if ((connectedSet.size() < netItemCount) && (!currentItem.hasIgnoredNets())) {
                Net net = board.rules.nets.get(currentNetNumber);
                // For plane nets: skip items whose connected set already contains a
                // ConductionArea (copper pour). These items would immediately return
                // CONNECTED_TO_PLANE in autorouteItem(), wasting time and causing
                // spurious normalizeTraces() failures on nearby stub geometry.
                // Items not yet connected to the plane are still enqueued so they can
                // be routed to the pour in this pass.
                if (net != null && net.containsPlane()) {
                  boolean alreadyConnectedToPlane =
                      connectedSet.stream().anyMatch(ConductionArea.class::isInstance);
                  if (alreadyConnectedToPlane) {
                    continue;
                  }
                }
                autorouteItemList.add(currentItem);
                String netName = net != null ? net.name : "net#" + currentNetNumber;
                FRLogger.debug(
                    "Queuing item for routing: "
                        + currentItem.getClass().getSimpleName()
                        + " on net '"
                        + netName
                        + "' (connected: "
                        + connectedSet.size()
                        + "/"
                        + netItemCount
                        + ")");
              }
            }
          }
        }
      }
    }
    return autorouteItemList;
  }

  /**
   * Multi-threaded version of the router that routes one ripup pass of all items of the board.
   * WARNING: this version is not working as intended yet. It is a work in progress.
   *
   * <p>Returns false if the board is already completely routed.
   */
  private boolean autoroutePassMultiThread(int passNo) {
    try {
      List<Item> autorouteItemList = getAutorouteItems(this.board);

      // If there are no items to route, we're done
      if (autorouteItemList.isEmpty()) {
        this.airLine = null;
        return false;
      }

      boolean useSlowAlgorithm = false;

      BatchAutorouterThread[] autorouterThreads =
          new BatchAutorouterThread[job.routerSettings.maxThreads];
      final BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

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

        autorouterThreads[threadIndex] =
            new BatchAutorouterThread(
                clonedBoard,
                clonedAutorouteItemList,
                passNo,
                job.routerSettings,
                this.startRipupCosts,
                this.tracePullTightAccuracy,
                this.removeUnconnectedVias,
                true);
        autorouterThreads[threadIndex].setName(
            "Router thread #" + passNo + "." + threadIndexToLetter(threadIndex));
        autorouterThreads[threadIndex].setDaemon(true);
        autorouterThreads[threadIndex].setPriority(Thread.MIN_PRIORITY);
      }

      // Update the board on the GUI only based on the first thread
      autorouterThreads[0].addBoardUpdatedEventListener(
          new BoardUpdatedEventListener() {
            @Override
            public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
              airLine = autorouterThreads[0].latestAirLine;
              fireBoardUpdatedEvent(
                  event.getBoardStatistics(), event.getRouterCounters(), event.getBoard());
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
          job.logError(
              "Autorouter thread #"
                  + passNo
                  + "."
                  + threadIndexToLetter(threadIndex)
                  + " was interrupted",
              e);
          this.thread.requestStop();
          break;
        }

        bh.add(autorouterThread.getBoard());

        // calculate the new board score
        BoardStatistics clonedBoardStatistics = autorouterThread.getBoard().getStatistics();
        float clonedBoardScore =
            clonedBoardStatistics.getNormalizedScore(job.routerSettings.scoring);

        job.logDebug(
            "Router thread #"
                + passNo
                + "."
                + threadIndexToLetter(threadIndex)
                + " finished with score: "
                + FRLogger.formatScore(
                    clonedBoardScore,
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
        BoardStatistics stats = autorouterThreads[i].getBoard().getStatistics();
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
      this.airLine = null;
      return anyProgress;
    } catch (Exception e) {
      job.logError("Something went wrong during the auto-routing", e);
      this.airLine = null;
      return false;
    }
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the board is already
   * completely routed.
   */
  private boolean autoroutePass(int passNo) {
    long passStartTime = System.currentTimeMillis();
    if (BENCHMARK_PROFILE_ENABLED) {
      this.profileItemSelectionNanos = 0;
      this.profileIntermediateStatisticsNanos = 0;
      this.profileBoardStatisticsNanos = 0;
      this.profileIncompleteDrcNanos = 0;
      this.profileAutorouteItemNanos = 0;
      this.profileMazeSearchNanos = 0;
      this.profileOptChangedAreaNanos = 0;
      this.profileTailRemovalNanos = 0;
      this.profileRouteItemCount = 0;
      this.profilePlaneItemCount = 0;
    }
    try {
      long itemSelectionStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
      List<Item> autorouteItemList = getAutorouteItems(this.board);
      if (BENCHMARK_PROFILE_ENABLED) {
        this.profileItemSelectionNanos += System.nanoTime() - itemSelectionStart;
      }

      // If there are no items to route, we're done
      if (autorouteItemList.isEmpty()) {
        this.airLine = null;
        return false;
      }

      // Clearance DRC is quadratic on large boards and is not needed for an intermediate UI
      // update. The final pass statistics below still use board.getStatistics() unchanged.
      long initialProgressStatisticsStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
      this.progressStatistics = new BoardStatistics(board, null, false);
      this.progressItemsSinceStatistics = 0;
      final BoardStatistics stats = this.progressStatistics;
      if (BENCHMARK_PROFILE_ENABLED) {
        this.profileBoardStatisticsNanos += System.nanoTime() - initialProgressStatisticsStart;
      }
      int itemsToGoCount = autorouteItemList.size();
      RouterCounters routerCounters = new RouterCounters();
      routerCounters.phase = "autoroute";
      routerCounters.passCount = passNo;
      routerCounters.queuedToBeRoutedCount = itemsToGoCount;
      routerCounters.skippedCount = 0;
      routerCounters.rippedCount = 0;
      routerCounters.failedToBeRoutedCount = 0;
      routerCounters.routedCount = 0;
      long statisticsStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
      DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
      tempDrc.calculateAllIncompletes();
      routerCounters.incompleteCount = tempDrc.getIncompleteCount();
      if (BENCHMARK_PROFILE_ENABLED) {
        this.profileIntermediateStatisticsNanos += System.nanoTime() - statisticsStart;
      }

      // Log incomplete details for debugging
      if (routerCounters.incompleteCount > 0) {
        job.logDebug(
            "Pass #"
                + passNo
                + ": "
                + routerCounters.incompleteCount
                + " incompletes across "
                + itemsToGoCount
                + " items to route");
        for (int netNumber = 1; netNumber <= board.rules.nets.maxNetNumber(); netNumber++) {
          int netIncompletes = tempDrc.getIncompleteCount(netNumber);
          if (netIncompletes > 0) {
            Net net = board.rules.nets.get(netNumber);
            String netName = net != null ? net.name : "net#" + netNumber;
            job.logDebug("  Net '" + netName + "' has " + netIncompletes + " incomplete(s)");
          }
        }
      }

      this.fireBoardUpdatedEvent(stats, routerCounters, this.board);

      // Sort items by airline distance (shortest first) for deterministic routing
      // This prioritizes local connections which typically route faster
      // NOTE: Disabled in v2.3 because it negatively impacts convergence compared to
      // v1.9 (natural order)
      // autorouteItemList.sort(
      //     Comparator.comparingDouble(AutorouteAirlineCalculator::calculateItemDistance));

      int rippedItemCount = 0;
      int notRouted = 0;
      int routed = 0;
      int skipped = 0;
      // Let's go through all items to route
      for (Item currentItem : autorouteItemList) {
        // If the user requested to stop the auto-router, we stop it
        if (this.thread.isStopAutoRouterRequested()) {
          break;
        }

        // Let's go through all nets of this item
        for (int i = 0; i < currentItem.netCount(); i++) {
          // If the user requested to stop the auto-router, we stop it
          if (this.thread.isStopAutoRouterRequested()) {
            break;
          }

          if (this.settings.maxItems != null
              && this.settings.maxItems > 0
              && this.totalItemsRouted >= this.settings.maxItems) {
            job.logInfo(
                "Max items limit reached (" + this.settings.maxItems + "). Stopping auto-router.");
            // Call requestStop() (sets ALL) instead of requestStopAutoRouter() (sets
            // AUTO_ROUTER_ONLY) so the optimization stage is also skipped.  maxItems is a
            // debugging/test ceiling meant to bound the entire routing job; running the
            // optimizer on a deliberately-incomplete board is not useful and prevents the
            // process from terminating promptly.
            this.thread.requestStop();
            break;
          }
          this.totalItemsRouted++;

          // We visually mark the area of the board, which is changed by the auto-router
          board.startMarkingChangedArea();

          // Do the auto-routing step for this item (typically PolylineTrace or Pin)
          // Use a fresh set per item to mirror v1.9 behavior and avoid cross-item side effects.
          SortedSet<Item> rippedItemList = new TreeSet<>();
          Map<Item, Integer> rippedItemCosts = new LinkedHashMap<>();
          final int netItemsBefore = board.getConnectableItems(currentItem.getNetNumber(i)).size();
          if (BENCHMARK_PROFILE_ENABLED) {
            this.profileRouteItemCount++;
            Net routeNet = board.rules.nets.get(currentItem.getNetNumber(i));
            if (routeNet != null && routeNet.containsPlane()) {
              this.profilePlaneItemCount++;
            }
          }
          long routeItemStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
          PerformanceProfiler.start("autoroute_item");
          final var autorouterResult =
              autorouteItem(
                  currentItem,
                  currentItem.getNetNumber(i),
                  rippedItemList,
                  rippedItemCosts,
                  passNo);
          PerformanceProfiler.end("autoroute_item");
          if (BENCHMARK_PROFILE_ENABLED) {
            this.profileAutorouteItemNanos += System.nanoTime() - routeItemStart;
          }
          if (!rippedItemList.isEmpty()) {
            for (Item rippedItem : rippedItemList) {
              StringBuilder rippedNets = new StringBuilder();
              for (int netIx = 0; netIx < rippedItem.netCount(); netIx++) {
                if (netIx > 0) {
                  rippedNets.append('|');
                }
                rippedNets.append(rippedItem.getNetNumber(netIx));
              }
              int ripupCost = rippedItemCosts.getOrDefault(rippedItem, -1);
              FRLogger.trace(
                  "BatchAutorouter.autoroute_pass",
                  "compare_trace_ripped_item",
                  "source_item="
                      + currentItem.getId()
                      + ", source_net="
                      + currentItem.getNetNumber(i)
                      + ", ripped_id="
                      + rippedItem.getId()
                      + ", ripped_type="
                      + rippedItem.getClass().getSimpleName()
                      + ", ripped_net_count="
                      + rippedItem.netCount()
                      + ", ripped_nets="
                      + rippedNets
                      + ", ripupCost="
                      + ripupCost,
                  "Net #" + currentItem.getNetNumber(i) + ",Item #" + currentItem.getId(),
                  getImpactedPoints(rippedItem));
            }
          }
          if (FRLogger.isTraceEnabled()) {
            DesignRulesChecker innerDrc = new DesignRulesChecker(board, null);
            innerDrc.calculateAllIncompletes();
            int tempIncomp = innerDrc.getIncompleteCount();
            int tempNetIncomp = innerDrc.getIncompleteCount(currentItem.getNetNumber(i));
            int netItemsAfter = board.getConnectableItems(currentItem.getNetNumber(i)).size();
            int maxItemId = board.communication.idGenerator.maxGeneratedId();
            FRLogger.trace(
                "BatchAutorouter.autoroute_pass",
                "compare_trace_route_item",
                "Routing "
                    + currentItem.getClass().getSimpleName()
                    + " -> result="
                    + autorouterResult.state
                    + ", details="
                    + autorouterResult.details
                    + ", incompletes="
                    + tempIncomp
                    + ", netIncomplete="
                    + tempNetIncomp
                    + ", ripped="
                    + rippedItemList.size()
                    + ", netItems="
                    + netItemsBefore
                    + "->"
                    + netItemsAfter
                    + ", maxItemId="
                    + maxItemId,
                "Net #"
                    + currentItem.getNetNumber(i)
                    + ",Item #"
                    + currentItem.getId()
                    + ",Type="
                    + currentItem.getClass().getSimpleName(),
                getImpactedPoints(currentItem));
          }

          if (currentItem.getNetNumber(i) == 94) {
            FRLogger.trace(
                "BatchAutorouter.autoroute_pass",
                "compare_trace_dump_net_items",
                "Dump net 94 items",
                "Net #94",
                new Point[0]);
            for (Item netItem : board.getConnectableItems(94)) {
              if (netItem instanceof Trace) {
                Trace t = (Trace) netItem;
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Trace layer="
                        + t.getLayer()
                        + " corners="
                        + t.firstCorner()
                        + " to "
                        + t.lastCorner(),
                    "Net #94,Item #" + t.getId() + ",Type=Trace",
                    new Point[] {t.firstCorner(), t.lastCorner()});
              } else if (netItem instanceof Via) {
                Via v = (Via) netItem;
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Via center=" + v.getCenter(),
                    "Net #94,Item #" + v.getId() + ",Type=Via",
                    new Point[] {v.getCenter()});
              } else if (netItem instanceof Pin) {
                Pin p = (Pin) netItem;
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Pin center="
                        + p.getCenter()
                        + " name="
                        + p.name()
                        + " comp="
                        + p.componentName(),
                    "Net #94,Item #" + p.getId() + ",Type=Pin",
                    new Point[] {p.getCenter()});
              } else {
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Item " + netItem.getClass().getSimpleName(),
                    "Net #94,Item #"
                        + netItem.getId()
                        + ",Type="
                        + netItem.getClass().getSimpleName(),
                    getImpactedPoints(netItem));
              }
            }
          }

          if (autorouterResult.state == AutorouteAttemptState.ROUTED) {
            // The item was successfully routed
            ++routed;
          } else if ((autorouterResult.state == AutorouteAttemptState.ALREADY_CONNECTED)
              || (autorouterResult.state == AutorouteAttemptState.NO_UNCONNECTED_NETS)
              || (autorouterResult.state == AutorouteAttemptState.CONNECTED_TO_PLANE)) {
            // The item doesn't need to be routed
            ++skipped;
          } else {
            Net net = board.rules.nets.get(currentItem.getNetNumber(i));
            String netName = net != null ? net.name : "net#" + currentItem.getNetNumber(i);

            // Record the failure
            board.failureLog.recordFailure(
                currentItem, passNo, autorouterResult.state, autorouterResult.details);

            job.logDebug("Autorouter " + autorouterResult.details);
            // Log details when we're down to last few items or item has many failures
            int failureCount = board.failureLog.getFailureCount(currentItem);
            if (itemsToGoCount <= 5 || failureCount >= 3) {
              job.logDebug(
                  "Pass #"
                      + passNo
                      + ": Failed to route "
                      + currentItem.getClass().getSimpleName()
                      + " on net '"
                      + netName
                      + "' ("
                      + itemsToGoCount
                      + " items remaining, "
                      + failureCount
                      + " failures). State: "
                      + autorouterResult.state);
            }
            ++notRouted;
          }
          --itemsToGoCount;
          rippedItemCount += rippedItemList.size();

          // Progress events can fire several times while one item is being routed. Recompute the
          // expensive non-clearance statistics once per completed item and reuse them between
          // events; this does not participate in routing decisions or final statistics.
          this.progressItemsSinceStatistics++;
          if (this.progressItemsSinceStatistics >= PROGRESS_STATISTICS_ITEM_INTERVAL) {
            long progressStatisticsStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
            this.progressStatistics = new BoardStatistics(this.board, null, false);
            this.progressItemsSinceStatistics = 0;
            if (BENCHMARK_PROFILE_ENABLED) {
              this.profileBoardStatisticsNanos += System.nanoTime() - progressStatisticsStart;
            }
          }

          if (shouldFireBoardUpdate()) {
            final BoardStatistics boardStatistics = this.progressStatistics;
            routerCounters.passCount = passNo;
            routerCounters.queuedToBeRoutedCount = itemsToGoCount;
            routerCounters.skippedCount = skipped;
            routerCounters.rippedCount = rippedItemCount;
            routerCounters.failedToBeRoutedCount = notRouted;
            routerCounters.routedCount = routed;
            routerCounters.incompleteCount = calculateIncompleteCount(board);
            this.fireBoardUpdatedEvent(boardStatistics, routerCounters, this.board);
          }
        }
      }

      int incompletesBefore = calculateIncompleteCount(board);
      FRLogger.trace(
          "BatchAutorouter.autoroute_pass",
          "compare_trace_remove_tails",
          "Incompletes before remove_tails=" + incompletesBefore,
          "Autorouter pass #" + passNo,
          new Point[0]);

      if (this.removeUnconnectedVias) {
        removeTails(Item.StopConnectionOption.NONE);
      } else {
        removeTails(Item.StopConnectionOption.FANOUT_VIA);
      }

      int incompletesAfter = calculateIncompleteCount(board);
      FRLogger.trace(
          "BatchAutorouter.autoroute_pass",
          "compare_trace_remove_tails",
          "Incompletes after remove_tails=" + incompletesAfter,
          "Autorouter pass #" + passNo,
          new Point[0]);

      // Fire final update for this pass
      long finalStatisticsStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
      long finalBoardStatisticsStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
      final BoardStatistics boardStatistics = board.getStatistics();
      if (BENCHMARK_PROFILE_ENABLED) {
        this.profileBoardStatisticsNanos += System.nanoTime() - finalBoardStatisticsStart;
      }
      if (BENCHMARK_PROFILE_ENABLED) {
        this.profileIntermediateStatisticsNanos += System.nanoTime() - finalStatisticsStart;
      }
      routerCounters.passCount = passNo;
      routerCounters.queuedToBeRoutedCount = itemsToGoCount;
      routerCounters.skippedCount = skipped;
      routerCounters.rippedCount = rippedItemCount;
      routerCounters.failedToBeRoutedCount = notRouted;
      routerCounters.routedCount = routed;
      routerCounters.incompleteCount = calculateIncompleteCount(board);
      this.fireBoardUpdatedEvent(boardStatistics, routerCounters, this.board);

      long passDuration = System.currentTimeMillis() - passStartTime;
      int currentRipupCost = this.startRipupCosts * passNo;
      PerformanceProfiler.recordPass(
          passNo, routerCounters.incompleteCount, passDuration, currentRipupCost);
      if (BENCHMARK_PROFILE_ENABLED) {
        FRLogger.info(
            "BENCHMARK_PROFILE pass="
                + passNo
                + ", items="
                + this.profileRouteItemCount
                + ", plane_items="
                + this.profilePlaneItemCount
                + ", selection_ms="
                + AutorouteRuntimeMetrics.nanosToMillis(this.profileItemSelectionNanos)
                + ", autoroute_item_ms="
                + AutorouteRuntimeMetrics.nanosToMillis(this.profileAutorouteItemNanos)
                + ", maze_search_ms="
                + AutorouteRuntimeMetrics.nanosToMillis(this.profileMazeSearchNanos)
                + ", opt_changed_area_ms="
                + AutorouteRuntimeMetrics.nanosToMillis(this.profileOptChangedAreaNanos)
                + ", tail_removal_ms="
                + AutorouteRuntimeMetrics.nanosToMillis(this.profileTailRemovalNanos)
                + ", statistics_ms="
                + AutorouteRuntimeMetrics.nanosToMillis(this.profileIntermediateStatisticsNanos)
                + ", retain_database="
                + this.retainAutorouteDatabase
                + ", board_statistics_ms="
                + AutorouteRuntimeMetrics.nanosToMillis(this.profileBoardStatisticsNanos)
                + ", incomplete_drc_ms="
                + AutorouteRuntimeMetrics.nanosToMillis(this.profileIncompleteDrcNanos));
      }

      // We are done with this pass
      this.airLine = null;
      return routed > 0 || notRouted > 0;
    } catch (Exception e) {
      job.logError("Something went wrong during the auto-routing", e);
      this.airLine = null;
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

  /**
   * Builds a human-readable summary of all unrouted connections on the current board, grouped by
   * net. For each unrouted connection the component and pin names of both endpoints are listed so
   * that the user can identify exactly which connections are missing and address them in their
   * design.
   *
   * <p>Example output:
   *
   * <pre>
   *   Net 'GND' (1 unrouted connection):
   *     - J2-A1  ->  U1-1
   *   Net '/MIPI_CSI_D0_N' (1 unrouted connection):
   *     - J2-A2  ->  U1-2
   * </pre>
   *
   * @return a formatted, multi-line string describing every unrouted airline
   */
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
   */
  public boolean runBatchLoop() {
    boolean anyRoutable = false;
    for (int i = 0; i < this.settings.getLayerCount(); i++) {
      if (this.settings.getLayerActive(i) && this.board.layerStructure.layers[i].isSignal) {
        anyRoutable = true;
        break;
      }
    }
    if (!anyRoutable) {
      FRLogger.warn("Cannot start autorouter: all layers are disabled.");
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.CANCELLED, 0, this.board.getHash()));
      throw new IllegalArgumentException("Cannot start autorouter: all layers are disabled.");
    }

    this.fireTaskStateChangedEvent(
        new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.getHash()));

    // Capture initial state for session summary
    this.sessionStartTime = Instant.now();
    this.initialUnroutedCount = calculateIncompleteCount(this.board);

    final BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

    // Record configuration for profiler
    if (this.settings.getLayerCount() > 0) {
      int layerCount = this.settings.getLayerCount();
      double[] prefCosts = new double[layerCount];
      double[] againstCosts = new double[layerCount];
      for (int i = 0; i < layerCount; i++) {
        prefCosts[i] = this.settings.getPreferredDirectionTraceCosts(i);
        againstCosts[i] = this.settings.getAgainstPreferredDirectionTraceCosts(i);
      }
      PerformanceProfiler.recordConfiguration(
          this.settings.getViaCosts(), this.settings.getPlaneViaCosts(), prefCosts, againstCosts);
    }

    job.logDebug(
        "Checking fanout pre-pass. settings.fanout.enabled="
            + this.settings.isFanoutEnabled()
            + ", smdPins="
            + this.board.getSmdPins().size());
    // Run SMD fanout pre-pass when the board has SMD pins and fanout is enabled
    if (this.settings.isFanoutEnabled()) {
      if (this.board.getSmdPins().isEmpty()) {
        job.logInfo("Fanout stage is enabled but skipped because the board has no SMD pins.");
      } else {
        final float fanoutCpuSecondsStart = AutorouteRuntimeMetrics.currentThreadCpuSeconds();
        final float fanoutAllocatedMbStart = AutorouteRuntimeMetrics.currentThreadAllocatedMb();
        float fanoutPeakHeapMbAtStart = AutorouteRuntimeMetrics.currentHeapUsageMb();
        final float[] fanoutPeakHeapMbObserved = new float[] {fanoutPeakHeapMbAtStart};
        // Count pins that actually need fanout. BatchFanout only processes SMD pins that
        // belong to a net, so exclude netless pins from the total. Among net-connected
        // pins, count those that are already fully connected (empty unconnected set).
        int netConnectedSmdPins = 0;
        int alreadyConnectedAtStart = 0;
        for (app.freerouting.board.Pin pin : this.board.getSmdPins()) {
          if (pin.netCount() > 0) {
            netConnectedSmdPins++;
            if (pin.getUnconnectedSet(pin.getNetNumber(0)).isEmpty()) {
              alreadyConnectedAtStart++;
            }
          }
        }
        int pinsToFanout = netConnectedSmdPins - alreadyConnectedAtStart;
        job.logInfo(
            "Fanout stage started on board '"
                + this.board.getHash()
                + "' with "
                + pinsToFanout
                + " of "
                + this.board.getSmdPins().size()
                + " SMD pins needing fanout ("
                + alreadyConnectedAtStart
                + " already connected, "
                + (this.board.getSmdPins().size() - netConnectedSmdPins)
                + " netless).");
        BatchFanout.FanoutRunSummary fanoutSummary =
            BatchFanout.fanoutBoard(
                this.board,
                this.settings,
                this.thread,
                status -> {
                  fanoutPeakHeapMbObserved[0] =
                      Math.max(
                          fanoutPeakHeapMbObserved[0],
                          AutorouteRuntimeMetrics.currentHeapUsageMb());
                  RouterCounters fanoutCounters = new RouterCounters();
                  fanoutCounters.phase = "fanout";
                  fanoutCounters.passCount = status.passNo();
                  fanoutCounters.queuedToBeRoutedCount = status.pinsToGo();
                  fanoutCounters.routedCount = status.routedCount();
                  fanoutCounters.skippedCount = 0;
                  fanoutCounters.rippedCount = 0;
                  fanoutCounters.failedToBeRoutedCount =
                      status.notRoutedCount() + status.insertErrorCount();
                  fanoutCounters.incompleteCount =
                      status.boardStatistics().connections.incompleteCount;
                  fanoutCounters.fanoutExtraViasCount = status.extraViasThisPass();
                  this.fireBoardUpdatedEvent(status.boardStatistics(), fanoutCounters, this.board);

                  if (status.passCompleted()) {
                    String boardHash = this.board.getHash();
                    String fanoutMessage =
                        String.format(
                            java.util.Locale.US,
                            "Fanout pass #%d on board '%s' completed in %.2f seconds with "
                                + "%d SMD pin%s fanouted, %d not routed, %d insert error%s, "
                                + "+%d extra via%s (%d SMD pin%s still to check in pass, "
                                + "ripup costs=%d).",
                            status.passNo(),
                            boardHash,
                            status.passDurationMillis() / 1000.0,
                            status.routedCount(),
                            status.routedCount() == 1 ? "" : "s",
                            status.notRoutedCount(),
                            status.insertErrorCount(),
                            status.insertErrorCount() == 1 ? "" : "s",
                            status.extraViasThisPass(),
                            status.extraViasThisPass() == 1 ? "" : "s",
                            status.pinsToGo(),
                            status.pinsToGo() == 1 ? "" : "s",
                            status.ripupCosts());
                    job.logInfo(fanoutMessage);
                  }
                });
        this.fanoutTimedOut = fanoutSummary.isTimedOut();

        float fanoutCpuSecondsEnd = AutorouteRuntimeMetrics.currentThreadCpuSeconds();
        float fanoutAllocatedMbEnd = AutorouteRuntimeMetrics.currentThreadAllocatedMb();

        float fanoutCpuSecondsUsed;
        if (fanoutCpuSecondsStart >= 0f && fanoutCpuSecondsEnd >= fanoutCpuSecondsStart) {
          fanoutCpuSecondsUsed = fanoutCpuSecondsEnd - fanoutCpuSecondsStart;
        } else {
          fanoutCpuSecondsUsed = Math.max(0f, AutorouteRuntimeMetrics.cpuSecondsSnapshot(job));
        }

        float fanoutAllocatedMb;
        if (fanoutAllocatedMbStart >= 0f && fanoutAllocatedMbEnd >= fanoutAllocatedMbStart) {
          fanoutAllocatedMb = fanoutAllocatedMbEnd - fanoutAllocatedMbStart;
        } else {
          fanoutAllocatedMb =
              Math.max(0f, AutorouteRuntimeMetrics.allocatedMemoryMbSnapshot(job));
        }

        float fanoutPeakHeapMb =
            Math.max(fanoutPeakHeapMbObserved[0], AutorouteRuntimeMetrics.currentHeapUsageMb());
        fanoutPeakHeapMb =
            Math.max(fanoutPeakHeapMb, AutorouteRuntimeMetrics.peakHeapMbSnapshot(job));
        BatchFanout.EscapeStatistics finalEscape = fanoutSummary.escapeStatistics();
        String fanoutCompletionStatus =
            fanoutSummary.isTimedOut()
                ? "completed with timeout:"
                : (this.thread.isStopAutoRouterRequested() ? "interrupted:" : "completed:");
        String fanoutSummaryMessage =
            String.format(
                java.util.Locale.US,
                "Fanout stage %s started with %d total SMD pins, completed in %.2f seconds, "
                    + "escaped pins: %d/%d (%.1f%%), using %.2f total CPU seconds, "
                    + "%.2f GB total allocated, and %.1f MB peak heap usage.",
                fanoutCompletionStatus,
                finalEscape.totalSmdPins(),
                fanoutSummary.totalDurationMillis() / 1000.0,
                finalEscape.escapedCount(),
                finalEscape.totalSmdPins(),
                finalEscape.escapedPercentage(),
                fanoutCpuSecondsUsed,
                fanoutAllocatedMb / 1024.0f,
                fanoutPeakHeapMb);
        job.logInfo(fanoutSummaryMessage);
      }
    }

    int currentUnrouted = calculateIncompleteCount(this.board);
    boolean isRouterEnabled =
        this.settings.getRunRouter()
            && (this.settings.maxPasses == null || this.settings.maxPasses >= 0);
    if (isRouterEnabled) {
      job.logInfo(
          "Auto-routing stage started on board '"
              + this.board.getHash()
              + "' for "
              + currentUnrouted
              + " unrouted item"
              + (currentUnrouted == 1 ? "" : "s")
              + ".");
    }
    boolean continueAutorouting = isRouterEnabled;

    int currentPass = 1;
    int consecutiveNoImprovementPasses = 0;
    boolean fanoutRecoveryApplied = false;
    float lastBestScore = Float.NEGATIVE_INFINITY; // score at last board-restore or improvement
    float globalBestScore = Float.NEGATIVE_INFINITY; // best score seen across all passes
    int passOfBestScore = 0; // pass where globalBestScore was achieved
    int incompleteCountAtBestScore = 0; // incomplete count when globalBestScore was recorded
    // Track board hashes that have already been routed. If the board does not change between
    // two consecutive passes (same hash at pass start), the router is making no progress and
    // would produce identical decisions with identical ripup budgets — stop immediately rather
    // than waiting for the full stagnation window. This mirrors the v1.9 behaviour and catches
    // the degenerate case where plane-net items repeatedly fail or are inserted+removed each
    // pass without updating the board state.
    Set<String> alreadyRoutedBoardHashes = new java.util.HashSet<>();
    while (continueAutorouting && !this.thread.isStopAutoRouterRequested()) {
      if (job != null && job.state == RoutingJobState.TIMED_OUT) {
        this.thread.requestStopAutoRouter();
      }

      String currentBoardHash = this.board.getHash();

      // Same-hash stop disabled because ripup budgets and random seeds change per-pass, making
      // progress possible in later passes.
      // if (alreadyRoutedBoardHashes.contains(currentBoardHash)) {
      //   job.logInfo("Board state has not changed since pass #" + (currentPass - 1)
      //       + " (hash " + currentBoardHash + "). The auto-router cannot make further progress;
      // stopping.");
      //   thread.request_stop_auto_router();
      //   break;
      // }
      // alreadyRoutedBoardHashes.add(currentBoardHash);

      if (this.settings.maxPasses != null
          && this.settings.maxPasses > 0
          && currentPass > this.settings.maxPasses) {
        thread.requestStopAutoRouter();
        break;
      }

      if (job != null) {
        job.setCurrentPass(currentPass);
      }

      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.RUNNING, currentPass, currentBoardHash));

      float boardScoreBefore =
          new BoardStatistics(this.board).getNormalizedScore(job.routerSettings.scoring);
      bh.add(this.board);

      FRLogger.traceEntry(
          "BatchAutorouter.autoroute_pass #"
              + currentPass
              + " on board '"
              + currentBoardHash
              + "'");

      continueAutorouting = autoroutePass(currentPass);

      BoardStatistics boardStatisticsAfter = new BoardStatistics(this.board);
      float boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);

      if ((bh.size() >= STOP_AT_PASS_MINIMUM) || (this.thread.isStopAutoRouterRequested())) {
        if (((currentPass % STOP_AT_PASS_MODULO == 0) && (currentPass >= STOP_AT_PASS_MINIMUM))
            || (this.thread.isStopAutoRouterRequested())) {
          // Check if the score improved compared to the previous passes, restore a
          // previous board if not. Use strict ">" so that equally-scored boards do NOT
          // trigger a restore — if every board has the same (possibly zero) score the old
          // ">=" test would restore on every check cycle, growing the history unboundedly
          // and never stopping.
          if (bh.getMaxScore() > boardScoreAfter) {
            var boardToRestore = bh.restoreBoard(MAXIMUM_TRIES_ON_THE_SAME_BOARD);
            if (boardToRestore == null) {
              job.logInfo(
                  "The router was not able to improve the board, stopping the auto-router.");
              thread.requestStopAutoRouter();
              break;
            }

            int boardToRestoreRank = bh.getRank(boardToRestore);

            if (boardToRestoreRank > BOARD_RANK_LIMIT) {
              thread.requestStopAutoRouter();
              break;
            }

            this.board = boardToRestore;
            var boardStatistics = this.board.getStatistics();
            // Reset pass-local stagnation counter when restoring a previous board state
            consecutiveNoImprovementPasses = 0;
            boardStatisticsAfter = boardStatistics;
            boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);
            lastBestScore = boardScoreAfter;
            currentBoardHash = this.board.getHash();
            // Reset the same-hash set after a board restore: the restored board will be
            // routed with a higher ripup budget on subsequent passes, so earlier routing
            // decisions from the same hash may no longer apply.
            alreadyRoutedBoardHashes.clear();
            job.logDebug(
                "Restoring an earlier board that has the score of "
                    + FRLogger.formatScore(
                        boardScoreAfter,
                        boardStatisticsAfter.connections.incompleteCount,
                        boardStatisticsAfter.clearanceViolations.totalCount)
                    + ".");
          }
        }
      }
      double autorouterPassDuration =
          FRLogger.traceExit(
              "BatchAutorouter.autoroute_pass #"
                  + currentPass
                  + " on board '"
                  + currentBoardHash
                  + "'");

      String passCompletedMessage =
          String.format(
              java.util.Locale.US,
              "Auto-routing pass #%d on board '%s' was completed in %.2f seconds with score %s",
              currentPass,
              currentBoardHash,
              autorouterPassDuration,
              FRLogger.formatScore(
                  boardScoreAfter,
                  boardStatisticsAfter.connections.incompleteCount,
                  boardStatisticsAfter.clearanceViolations.totalCount));
      if (job.resourceUsage.cpuTimeUsed > 0) {
        passCompletedMessage +=
            String.format(
                java.util.Locale.US,
                ", using %.2f CPU seconds and the job allocated %.2f GB of memory so far.",
                job.resourceUsage.cpuTimeUsed,
                job.resourceUsage.maxMemoryUsed / 1024.0f);
      } else {
        passCompletedMessage += ".";
      }
      if (!isOptimizerAutorouter) {
        job.logInfo(passCompletedMessage);
      }

      DesignRulesChecker tempDrc = new DesignRulesChecker(this.board, null);
      tempDrc.calculateAllIncompletes();
      StringBuilder perNetBreakdown = new StringBuilder();
      for (int netNumber = 1; netNumber <= this.board.rules.nets.maxNetNumber(); netNumber++) {
        int netIncomplete = tempDrc.getIncompleteCount(netNumber);
        if (netIncomplete > 0) {
          FRLogger.trace(
              "BatchAutorouter.autoroute_pass",
              "compare_unrouted_net",
              "pass=" + currentPass + ", net=" + netNumber + ", incomplete=" + netIncomplete,
              "Net #" + netNumber,
              new Point[0]);
          if (!perNetBreakdown.isEmpty()) {
            perNetBreakdown.append(',');
          }
          perNetBreakdown.append(netNumber).append('=').append(netIncomplete);
        }
      }
      FRLogger.trace(
          "BatchAutorouter.autoroute_pass",
          "compare_unrouted_breakdown",
          "pass="
              + currentPass
              + ", total="
              + tempDrc.getIncompleteCount()
              + ", breakdown="
              + perNetBreakdown,
          "",
          new Point[0]);

      if (Boolean.TRUE.equals(this.settings.saveIntermediateStages)) {
        fireBoardSnapshotEvent(this.board);
      }

      // Stagnation detection: abort when the normalized score hasn't improved by
      // at least STAGNATION_SCORE_THRESHOLD over STAGNATION_PASS_LIMIT consecutive
      // passes. This now fires whenever the router is still actively running
      // (continueAutorouting == true) after the mandatory minimum passes, regardless
      // of incompleteCount.  The old condition guarded on incompleteCount > 0, which
      // caused the check to be bypassed — and the counter to be silently reset — for
      // boards where DRC shows 0 incompletes but the router keeps cycling (e.g. when
      // plane-net false-work items kept autoroutePass() returning true).  If the
      // board is genuinely done (continueAutorouting == false) the while-loop exits
      // naturally and we never reach this block.
      if (currentPass >= STOP_AT_PASS_MINIMUM && continueAutorouting) {

        // --- Pass-local counter (resets after board restores) ---
        if (boardScoreAfter > lastBestScore + STAGNATION_SCORE_THRESHOLD) {
          consecutiveNoImprovementPasses = 0;
          lastBestScore = boardScoreAfter;
        } else {
          consecutiveNoImprovementPasses++;

          // One-time recovery for fanout-enabled jobs: aggressively remove tails, including
          // fanout vias, when score plateaus with remaining incompletes. This gives the
          // autorouter a chance to escape local dead-ends introduced by pre-fanout geometry
          // while keeping fanout enabled as the default behavior.
          if (this.settings.isFanoutEnabled()
              && !fanoutRecoveryApplied
              && boardStatisticsAfter.connections.incompleteCount > 0
              && consecutiveNoImprovementPasses >= FANOUT_RECOVERY_STAGNATION_PASSES) {
            final int incompletesBeforeRecovery = boardStatisticsAfter.connections.incompleteCount;
            removeTails(Item.StopConnectionOption.NONE);
            boardStatisticsAfter = new BoardStatistics(this.board);
            boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);
            lastBestScore = boardScoreAfter;
            consecutiveNoImprovementPasses = 0;
            fanoutRecoveryApplied = true;
            alreadyRoutedBoardHashes.clear();
            job.logDebug(
                "Applied one-time fanout recovery cleanup (removed fanout tails/vias). "
                    + "Incompletes: "
                    + incompletesBeforeRecovery
                    + " -> "
                    + boardStatisticsAfter.connections.incompleteCount
                    + ".");
          }

          if (consecutiveNoImprovementPasses >= STAGNATION_PASS_LIMIT) {
            String report = buildUnroutedConnectionsReport();
            job.logInfo(
                "The router's score ("
                    + FRLogger.defaultFloatFormat.format(boardScoreAfter)
                    + ") has not improved by more than "
                    + STAGNATION_SCORE_THRESHOLD
                    + " points in the last "
                    + STAGNATION_PASS_LIMIT
                    + " passes ("
                    + boardStatisticsAfter.connections.incompleteCount
                    + " item"
                    + (boardStatisticsAfter.connections.incompleteCount == 1 ? "" : "s")
                    + " still unconnected). Stopping the auto-router.\n"
                    + "The following connections could not be routed -- please review your design "
                    + "(e.g. check pad clearances, trace width rules, and available routing "
                    + "space):\n"
                    + report);
            thread.requestStopAutoRouter();
            break;
          }
        }

        // --- Global best tracker (not reset by board restores) ---
        // Stops the router if no pass anywhere has meaningfully improved the score
        // in the last STAGNATION_PASS_LIMIT passes, even across board-restore cycles.
        if (boardScoreAfter > globalBestScore + STAGNATION_SCORE_THRESHOLD) {
          globalBestScore = boardScoreAfter;
          passOfBestScore = currentPass;
          incompleteCountAtBestScore = boardStatisticsAfter.connections.incompleteCount;
        } else if ((currentPass - passOfBestScore) >= STAGNATION_PASS_LIMIT) {
          String report = buildUnroutedConnectionsReport();
          job.logInfo(
              "The router's best score ("
                  + FRLogger.defaultFloatFormat.format(globalBestScore)
                  + ") has not improved by more than "
                  + STAGNATION_SCORE_THRESHOLD
                  + " points since pass #"
                  + passOfBestScore
                  + ". Stopping the auto-router after "
                  + currentPass
                  + " passes ("
                  + incompleteCountAtBestScore
                  + " item"
                  + (incompleteCountAtBestScore == 1 ? "" : "s")
                  + " still unconnected).\n"
                  + "The following connections could not be routed -- please review your design "
                  + "(e.g. check pad clearances, trace width rules, and available routing space):\n"
                  + report);
          thread.requestStopAutoRouter();
          break;
        }

      } else if (boardStatisticsAfter.connections.incompleteCount == 0
          && boardScoreAfter > STAGNATION_SCORE_THRESHOLD) {
        // Board is fully routed AND has a positive score (genuine success).
        // A fully-routed board with score == 0 (e.g. caused by clearance violations
        // from plane routing) must NOT reset the stagnation counter; it should keep
        // accumulating until the global tracker fires.
        consecutiveNoImprovementPasses = 0;
        lastBestScore = boardScoreAfter;
      }

      // check if there are still unrouted items
      if (continueAutorouting && !this.thread.isStopAutoRouterRequested()) {
        currentPass++;
      }
    }

    // Ensure we finish with the best board ever seen during this routing session.
    // When stagnation or the max-pass limit fires, the loop exits with the board from the last
    // completed pass, which may be worse than an earlier pass that was recorded in the history.
    float currentFinalScore =
        new BoardStatistics(this.board).getNormalizedScore(job.routerSettings.scoring);
    float bestHistoryScore = bh.getMaxScore();
    if (bestHistoryScore > currentFinalScore) {
      RoutingBoard bestBoard = bh.restoreBestBoard();
      if (bestBoard != null) {
        BoardStatistics currentStats = new BoardStatistics(this.board);
        this.board = bestBoard;
        BoardStatistics bestStats = new BoardStatistics(this.board);
        job.logDebug(
            "The final board state (score "
                + FRLogger.formatScore(
                    currentFinalScore,
                    currentStats.connections.incompleteCount,
                    currentStats.clearanceViolations.totalCount)
                + ") is worse than the best board seen during routing (score "
                + FRLogger.formatScore(
                    bestStats.getNormalizedScore(job.routerSettings.scoring),
                    bestStats.connections.incompleteCount,
                    bestStats.clearanceViolations.totalCount)
                + "). Restoring the best board as the final result.");
      }
    }

    job.board = this.board;

    boolean wasRouterRun =
        this.settings.getRunRouter()
            && (this.settings.maxPasses == null || this.settings.maxPasses >= 0);
    if (wasRouterRun
        && !(this.removeUnconnectedVias
            || continueAutorouting
            || this.thread.isStopAutoRouterRequested())) {
      // clean up the route if the board is completed and if fanout is used.
      removeTails(Item.StopConnectionOption.NONE);
    }

    bh.clear();

    // Print all profiling results at the end of session
    PerformanceProfiler.printResults();
    PerformanceProfiler.reset();

    if (!this.thread.isStopAutoRouterRequested()) {
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.FINISHED, currentPass, this.board.getHash()));
    } else {
      // Distinguish between a user-requested cancellation and a job timeout so that
      // API consumers can tell the two apart via TaskStateChangedEvent.
      boolean isTimedOut = (job != null) && (job.state == RoutingJobState.TIMED_OUT);
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(
              this,
              isTimedOut ? TaskState.TIMED_OUT : TaskState.CANCELLED,
              currentPass,
              this.board.getHash()));
    }

    return !this.thread.isStopAutoRouterRequested();
  }

  private String buildUnroutedConnectionsReport() {
    return AutorouteUnroutedReport.build(this.board);
  }

  private void removeTails(Item.StopConnectionOption stopConnectionOption) {
    final long tailRemovalStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
    board.startMarkingChangedArea();
    board.removeTraceTails(-1, stopConnectionOption);
    long pullTightStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
    board.optChangedArea(
        new int[0],
        null,
        this.tracePullTightAccuracy,
        this.traceCosts,
        this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
    if (BENCHMARK_PROFILE_ENABLED) {
      this.profileTailRemovalNanos += System.nanoTime() - tailRemovalStart;
      this.profileOptChangedAreaNanos += System.nanoTime() - pullTightStart;
    }
  }

  // Tries to route an item on a specific net. Returns true, if the item is
  // routed.
  private AutorouteAttemptResult autorouteItem(
      Item item,
      int routeNetNo,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int ripupPassNo) {
    return connectionRouter.route(item, routeNetNo, rippedItemList, ripupCosts, ripupPassNo);
  }

  /**
   * Returns the airline of the current autorouted connection, or null if no such airline exists.
   */
  public FloatLine getAirLine() {
    if (this.airLine == null) {
      return null;
    }
    if (this.airLine.a == null || this.airLine.b == null) {
      return null;
    }
    return this.airLine;
  }

  /**
   * Return an uppercase one-letter, two-letter or three-letter string based on the thread index (0
   * = A, 1 = B, 2 = C, ..., 26 = AA, 27 = AB, ...).
   *
   * @param threadIndex the thread index.
   * @return the letter label for the thread index.
   */
  private String threadIndexToLetter(int threadIndex) {
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
      return String.valueOf((char) ('A' + firstLetterIndex))
          + (char) ('A' + secondLetterIndex)
          + (char) ('A' + thirdLetterIndex);
    }
  }

  private int calculateIncompleteCount(RoutingBoard board) {
    long drcStart = BENCHMARK_PROFILE_ENABLED ? System.nanoTime() : 0;
    DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
    tempDrc.calculateAllIncompletes();
    if (BENCHMARK_PROFILE_ENABLED) {
      this.profileIncompleteDrcNanos += System.nanoTime() - drcStart;
    }
    return tempDrc.getIncompleteCount();
  }
}

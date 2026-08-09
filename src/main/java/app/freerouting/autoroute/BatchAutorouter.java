package app.freerouting.autoroute;

import static java.util.Collections.shuffle;

import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.BasicBoard;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.Connectable;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.TimeLimit;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.AirLine;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.rules.Net;
import app.freerouting.settings.RouterSettings;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** Handles the sequencing of the auto-router passes. */
public class BatchAutorouter extends NamedAlgorithm {

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
  // Minimum score gain (on the 0–1000 normalized scale) that counts as a
  // meaningful improvement; gains smaller than this are treated as stagnation.
  private static final float STAGNATION_SCORE_THRESHOLD = 0.5F;

  private final boolean removeUnconnectedVias;
  private final AutorouteControl.ExpansionCostFactor[] traceCostArr;
  private final boolean retainAutorouteDatabase;
  private final int startRipupCosts;
  private final int tracePullTightAccuracy;
  // Reusable collections to reduce memory churn (thread-safe as each thread has
  // its own BatchAutorouter instance)
  private final List<Item> reusableAutorouteItemList = new ArrayList<>();
  private final Set<Item> reusableHandledItems = new TreeSet<>();
  protected RoutingJob job;
  private int totalItemsRouted;
  private boolean fanoutTimedOut;

  public boolean isFanoutTimedOut() {
    return this.fanoutTimedOut;
  }

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
      this.traceCostArr = this.settings.getTraceCostArr();
    } else {
      // remove preferred direction
      this.traceCostArr = new AutorouteControl.ExpansionCostFactor[this.board.getLayerCount()];
      for (int i = 0; i < this.traceCostArr.length; i++) {
        double currMinCost = this.settings.getPreferredDirectionTraceCosts(i);
        this.traceCostArr[i] = new AutorouteControl.ExpansionCostFactor(currMinCost, currMinCost);
      }
    }

    this.startRipupCosts = startRipupCosts;
    this.tracePullTightAccuracy = pullTightAccuracy;
    this.retainAutorouteDatabase = false;
  }

  /**
   * Auto-routes ripup passes until the board is completed or the auto-router is stopped by the
   * user, or if p_max_pass_count is exceeded. Is currently used in the optimize via batch pass.
   * Returns the number of passes to complete the board or p_max_pass_count + 1, if the board is not
   * completed.
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
    int currPassNo = 1;
    while (stillUnroutedItems
        && !job.thread.isStopAutoRouterRequested()
        && currPassNo <= maxPassCount) {
      stillUnroutedItems = routerInstance.autoroutePass(currPassNo);
      if (stillUnroutedItems
          && !job.thread.isStopAutoRouterRequested()
          && updatedRoutingBoard == null) {}
      ++currPassNo;
    }
    routerInstance.removeTails(Item.StopConnectionOption.NONE);
    if (!stillUnroutedItems) {
      --currPassNo;
    }
    return currPassNo;
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

  private static float getCpuSecondsSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.cpuTimeUsed;
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the board is already
   * completely routed.
   */
  private static float getAllocatedMemoryMbSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.maxMemoryUsed;
  }

  private static float getPeakHeapMbSnapshot(RoutingJob job) {
    if (job == null || job.resourceUsage == null) {
      return 0f;
    }
    return job.resourceUsage.peakMemoryUsed;
  }

  private static float sampleCurrentThreadCpuSeconds() {
    try {
      ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      long cpuNanos = threadMxBean.getThreadCpuTime(Thread.currentThread().threadId());
      return cpuNanos < 0 ? -1f : cpuNanos / 1_000_000_000.0f;
    } catch (Throwable t) {
      return -1f;
    }
  }

  private static float sampleCurrentThreadAllocatedMb() {
    try {
      ThreadMXBean threadMxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      threadMxBean.setThreadAllocatedMemoryEnabled(true);
      long allocatedBytes = threadMxBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      return allocatedBytes < 0 ? -1f : allocatedBytes / (1024.0f * 1024.0f);
    } catch (Throwable t) {
      return -1f;
    }
  }

  private static float sampleHeapUsageMb() {
    try {
      long heapUsed = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
      return heapUsed / (1024.0f * 1024.0f);
    } catch (Throwable t) {
      return 0f;
    }
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
      UndoableObjects.Storable currOb = board.itemList.readObject(it);
      if (currOb == null) {
        break;
      }
      if (currOb instanceof Connectable && currOb instanceof Item currItem) {
        // This is a connectable item, like PolylineTrace or Pin
        if (!currItem.isRoutable()) {
          if (!handledItems.contains(currItem)) {

            // Let's go through all nets of this item
            for (int i = 0; i < currItem.netCount(); i++) {
              int currNetNo = currItem.getNetNo(i);
              Set<Item> connectedSet = currItem.getConnectedSet(currNetNo);
              for (Item currConnectedItem : connectedSet) {
                if (currConnectedItem.netCount() <= 1) {
                  handledItems.add(currConnectedItem);
                }
              }
              int netItemCount = board.connectableItemCount(currNetNo);

              // If the item is not connected to all other items of the net, we add it to the
              // auto-router's to-do list
              if ((connectedSet.size() < netItemCount) && (!currItem.hasIgnoredNets())) {
                Net net = board.rules.nets.get(currNetNo);
                // For plane nets: skip items whose connected set already contains a
                // ConductionArea (copper pour). These items would immediately return
                // CONNECTED_TO_PLANE in autoroute_item(), wasting time and causing
                // spurious normalize_traces() failures on nearby stub geometry.
                // Items not yet connected to the plane are still enqueued so they can
                // be routed to the pour in this pass.
                if (net != null && net.containsPlane()) {
                  boolean alreadyConnectedToPlane =
                      connectedSet.stream().anyMatch(ConductionArea.class::isInstance);
                  if (alreadyConnectedToPlane) {
                    continue;
                  }
                }
                autorouteItemList.add(currItem);
                String netName = net != null ? net.name : "net#" + currNetNo;
                FRLogger.debug(
                    "Queuing item for routing: "
                        + currItem.getClass().getSimpleName()
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
    try {
      List<Item> autorouteItemList = getAutorouteItems(this.board);

      // If there are no items to route, we're done
      if (autorouteItemList.isEmpty()) {
        this.airLine = null;
        return false;
      }

      int itemsToGoCount = autorouteItemList.size();
      final BoardStatistics stats = board.getStatistics();
      RouterCounters routerCounters = new RouterCounters();
      routerCounters.phase = "autoroute";
      routerCounters.passCount = passNo;
      routerCounters.queuedToBeRoutedCount = itemsToGoCount;
      routerCounters.skippedCount = 0;
      routerCounters.rippedCount = 0;
      routerCounters.failedToBeRoutedCount = 0;
      routerCounters.routedCount = 0;
      DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
      tempDrc.calculateAllIncompletes();
      routerCounters.incompleteCount = tempDrc.getIncompleteCount();

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
        for (int netNo = 1; netNo <= board.rules.nets.maxNetNo(); netNo++) {
          int netIncompletes = tempDrc.getIncompleteCount(netNo);
          if (netIncompletes > 0) {
            Net net = board.rules.nets.get(netNo);
            String netName = net != null ? net.name : "net#" + netNo;
            job.logDebug("  Net '" + netName + "' has " + netIncompletes + " incomplete(s)");
          }
        }
      }

      this.fireBoardUpdatedEvent(stats, routerCounters, this.board);

      // Sort items by airline distance (shortest first) for deterministic routing
      // This prioritizes local connections which typically route faster
      // NOTE: Disabled in v2.3 because it negatively impacts convergence compared to
      // v1.9 (natural order)
      // autorouteItemList.sort(Comparator.comparingDouble(this::calculateItemDistance));

      int rippedItemCount = 0;
      int notRouted = 0;
      int routed = 0;
      int skipped = 0;
      // Let's go through all items to route
      for (Item currItem : autorouteItemList) {
        // If the user requested to stop the auto-router, we stop it
        if (this.thread.isStopAutoRouterRequested()) {
          break;
        }

        // Let's go through all nets of this item
        for (int i = 0; i < currItem.netCount(); i++) {
          // If the user requested to stop the auto-router, we stop it
          if (this.thread.isStopAutoRouterRequested()) {
            break;
          }

          if (this.settings.maxItems != null
              && this.settings.maxItems > 0
              && this.totalItemsRouted >= this.settings.maxItems) {
            job.logInfo(
                "Max items limit reached (" + this.settings.maxItems + "). Stopping auto-router.");
            // Call requestStop() (sets ALL) instead of request_stop_auto_router() (sets
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
          final int netItemsBefore = board.getConnectableItems(currItem.getNetNo(i)).size();
          PerformanceProfiler.start("autoroute_item");
          var autorouterResult =
              autorouteItem(
                  currItem, currItem.getNetNo(i), rippedItemList, rippedItemCosts, passNo);
          PerformanceProfiler.end("autoroute_item");
          if (!rippedItemList.isEmpty()) {
            for (Item rippedItem : rippedItemList) {
              StringBuilder rippedNets = new StringBuilder();
              for (int netIx = 0; netIx < rippedItem.netCount(); netIx++) {
                if (netIx > 0) {
                  rippedNets.append('|');
                }
                rippedNets.append(rippedItem.getNetNo(netIx));
              }
              int ripupCost = rippedItemCosts.getOrDefault(rippedItem, -1);
              FRLogger.trace(
                  "BatchAutorouter.autoroute_pass",
                  "compare_trace_ripped_item",
                  "source_item="
                      + currItem.getIdNo()
                      + ", source_net="
                      + currItem.getNetNo(i)
                      + ", ripped_id="
                      + rippedItem.getIdNo()
                      + ", ripped_type="
                      + rippedItem.getClass().getSimpleName()
                      + ", ripped_net_count="
                      + rippedItem.netCount()
                      + ", ripped_nets="
                      + rippedNets
                      + ", ripupCost="
                      + ripupCost,
                  "Net #" + currItem.getNetNo(i) + ",Item #" + currItem.getIdNo(),
                  getImpactedPoints(rippedItem));
            }
          }
          if (FRLogger.isTraceEnabled()) {
            DesignRulesChecker innerDrc = new DesignRulesChecker(board, null);
            innerDrc.calculateAllIncompletes();
            int tempIncomp = innerDrc.getIncompleteCount();
            int tempNetIncomp = innerDrc.getIncompleteCount(currItem.getNetNo(i));
            int netItemsAfter = board.getConnectableItems(currItem.getNetNo(i)).size();
            int maxItemId = board.communication.idNoGenerator.maxGeneratedNo();
            FRLogger.trace(
                "BatchAutorouter.autoroute_pass",
                "compare_trace_route_item",
                "Routing "
                    + currItem.getClass().getSimpleName()
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
                    + currItem.getNetNo(i)
                    + ",Item #"
                    + currItem.getIdNo()
                    + ",Type="
                    + currItem.getClass().getSimpleName(),
                getImpactedPoints(currItem));
          }

          if (currItem.getNetNo(i) == 94) {
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
                    "Net #94,Item #" + t.getIdNo() + ",Type=Trace",
                    new Point[] {t.firstCorner(), t.lastCorner()});
              } else if (netItem instanceof Via) {
                Via v = (Via) netItem;
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Via center=" + v.getCenter(),
                    "Net #94,Item #" + v.getIdNo() + ",Type=Via",
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
                    "Net #94,Item #" + p.getIdNo() + ",Type=Pin",
                    new Point[] {p.getCenter()});
              } else {
                FRLogger.trace(
                    "BatchAutorouter.autoroute_pass",
                    "compare_trace_dump_net_item",
                    "Item " + netItem.getClass().getSimpleName(),
                    "Net #94,Item #"
                        + netItem.getIdNo()
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
            Net net = board.rules.nets.get(currItem.getNetNo(i));
            String netName = net != null ? net.name : "net#" + currItem.getNetNo(i);

            // Record the failure
            board.failureLog.recordFailure(
                currItem, passNo, autorouterResult.state, autorouterResult.details);

            job.logDebug("Autorouter " + autorouterResult.details);
            // Log details when we're down to last few items or item has many failures
            int failureCount = board.failureLog.getFailureCount(currItem);
            if (itemsToGoCount <= 5 || failureCount >= 3) {
              job.logDebug(
                  "Pass #"
                      + passNo
                      + ": Failed to route "
                      + currItem.getClass().getSimpleName()
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

          if (shouldFireBoardUpdate()) {
            final BoardStatistics boardStatistics = board.getStatistics();
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
      final BoardStatistics boardStatistics = board.getStatistics();
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
      if (this.settings.getLayerActive(i) && this.board.layerStructure.arr[i].isSignal) {
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
        final float fanoutCpuSecondsStart = sampleCurrentThreadCpuSeconds();
        final float fanoutAllocatedMbStart = sampleCurrentThreadAllocatedMb();
        float fanoutPeakHeapMbAtStart = sampleHeapUsageMb();
        final float[] fanoutPeakHeapMbObserved = new float[] {fanoutPeakHeapMbAtStart};
        // Count pins that actually need fanout. BatchFanout only processes SMD pins that
        // belong to a net, so exclude netless pins from the total. Among net-connected
        // pins, count those that are already fully connected (empty unconnected set).
        int netConnectedSmdPins = 0;
        int alreadyConnectedAtStart = 0;
        for (app.freerouting.board.Pin pin : this.board.getSmdPins()) {
          if (pin.netCount() > 0) {
            netConnectedSmdPins++;
            if (pin.getUnconnectedSet(pin.getNetNo(0)).isEmpty()) {
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
                      Math.max(fanoutPeakHeapMbObserved[0], sampleHeapUsageMb());
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

        float fanoutCpuSecondsEnd = sampleCurrentThreadCpuSeconds();
        float fanoutAllocatedMbEnd = sampleCurrentThreadAllocatedMb();

        float fanoutCpuSecondsUsed;
        if (fanoutCpuSecondsStart >= 0f && fanoutCpuSecondsEnd >= fanoutCpuSecondsStart) {
          fanoutCpuSecondsUsed = fanoutCpuSecondsEnd - fanoutCpuSecondsStart;
        } else {
          fanoutCpuSecondsUsed = Math.max(0f, getCpuSecondsSnapshot(job));
        }

        float fanoutAllocatedMb;
        if (fanoutAllocatedMbStart >= 0f && fanoutAllocatedMbEnd >= fanoutAllocatedMbStart) {
          fanoutAllocatedMb = fanoutAllocatedMbEnd - fanoutAllocatedMbStart;
        } else {
          fanoutAllocatedMb = Math.max(0f, getAllocatedMemoryMbSnapshot(job));
        }

        float fanoutPeakHeapMb = Math.max(fanoutPeakHeapMbObserved[0], sampleHeapUsageMb());
        fanoutPeakHeapMb = Math.max(fanoutPeakHeapMb, getPeakHeapMbSnapshot(job));
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
      for (int netNo = 1; netNo <= this.board.rules.nets.maxNetNo(); netNo++) {
        int netIncomplete = tempDrc.getIncompleteCount(netNo);
        if (netIncomplete > 0) {
          FRLogger.trace(
              "BatchAutorouter.autoroute_pass",
              "compare_unrouted_net",
              "pass=" + currentPass + ", net=" + netNo + ", incomplete=" + netIncomplete,
              "Net #" + netNo,
              new Point[0]);
          if (!perNetBreakdown.isEmpty()) {
            perNetBreakdown.append(',');
          }
          perNetBreakdown.append(netNo).append('=').append(netIncomplete);
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

      if (this.settings.saveIntermediateStages) {
        fireBoardSnapshotEvent(this.board);
      }

      // Stagnation detection: abort when the normalized score hasn't improved by
      // at least STAGNATION_SCORE_THRESHOLD over STAGNATION_PASS_LIMIT consecutive
      // passes. This now fires whenever the router is still actively running
      // (continueAutorouting == true) after the mandatory minimum passes, regardless
      // of incompleteCount.  The old condition guarded on incompleteCount > 0, which
      // caused the check to be bypassed — and the counter to be silently reset — for
      // boards where DRC shows 0 incompletes but the router keeps cycling (e.g. when
      // plane-net false-work items kept autoroute_pass() returning true).  If the
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
    DesignRulesChecker tempDrc = new DesignRulesChecker(this.board, null);
    tempDrc.calculateAllIncompletes();
    AirLine[] airlines = tempDrc.getAllAirlines();

    if (airlines == null || airlines.length == 0) {
      return "  (no unrouted connections found)";
    }

    // Group airlines by net name for a cleaner report
    Map<String, List<String>> byNet = new LinkedHashMap<>();
    for (AirLine al : airlines) {
      String netName = al.net != null ? al.net.name : "(unknown net)";
      String fromDesc = describeItem(al.fromItem);
      String toDesc = describeItem(al.toItem);
      byNet
          .computeIfAbsent(netName, k -> new ArrayList<>())
          .add("    - " + fromDesc + "  ->  " + toDesc);
    }

    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, List<String>> entry : byNet.entrySet()) {
      int count = entry.getValue().size();
      sb.append("  Net '")
          .append(entry.getKey())
          .append("' (")
          .append(count)
          .append(" unrouted connection")
          .append(count == 1 ? "" : "s")
          .append("):\n");
      for (String line : entry.getValue()) {
        sb.append(line).append('\n');
      }
    }
    return sb.toString().stripTrailing();
  }

  /**
   * Returns a short, user-friendly description of a board item suitable for the stagnation report.
   * For pins the format is {@code ComponentName-PinName} (e.g. {@code J2-A3}); for all other item
   * types a generic fallback is used.
   */
  private String describeItem(Item item) {
    if (item instanceof Pin pin) {
      try {
        app.freerouting.board.Component comp = board.components.get(pin.getComponentNo());
        if (comp != null) {
          app.freerouting.core.Package pkg = comp.getPackage();
          if (pkg != null) {
            app.freerouting.core.Package.Pin pkgPin = pkg.getPin(pin.pinNo);
            if (pkgPin != null) {
              return comp.name + "-" + pkgPin.name;
            }
          }
          return comp.name + " (pin #" + pin.pinNo + ")";
        }
      } catch (Exception e) {
        // fall through to generic
      }
    }
    return item != null ? item.toString() : "(unknown)";
  }

  private void removeTails(Item.StopConnectionOption stopConnectionOption) {
    board.startMarkingChangedArea();
    board.removeTraceTails(-1, stopConnectionOption);
    board.optChangedArea(
        new int[0],
        null,
        this.tracePullTightAccuracy,
        this.traceCostArr,
        this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
  }

  // Tries to route an item on a specific net. Returns true, if the item is
  // routed.
  private AutorouteAttemptResult autorouteItem(
      Item item,
      int routeNetNo,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int ripupPassNo) {
    try {
      boolean containsPlane = false;

      // Get the net
      Net routeNet = board.rules.nets.get(routeNetNo);
      if (routeNet != null) {
        containsPlane = routeNet.containsPlane();
      }

      // Get the current via costs based on auto-router settings
      int currViaCosts;
      if (containsPlane) {
        currViaCosts = this.settings.getPlaneViaCosts();
      } else {
        currViaCosts = this.settings.getViaCosts();
      }

      // Get and calculate the auto-router settings based on the board and net we are
      // working on
      AutorouteControl autorouteControl =
          new AutorouteControl(this.board, routeNetNo, settings, currViaCosts, this.traceCostArr);
      autorouteControl.ripupAllowed = true;
      autorouteControl.ripupCosts = this.startRipupCosts * ripupPassNo;
      autorouteControl.removeUnconnectedVias = this.removeUnconnectedVias;

      // Check if the item is already routed
      Set<Item> unconnectedSet = item.getUnconnectedSet(routeNetNo);
      if (unconnectedSet.isEmpty()) {
        return new AutorouteAttemptResult(AutorouteAttemptState.NO_UNCONNECTED_NETS);
      }

      Set<Item> connectedSet = item.getConnectedSet(routeNetNo);
      Set<Item> routeStartSet;
      Set<Item> routeDestSet;
      if (containsPlane) {
        for (Item currItem : connectedSet) {
          if (currItem instanceof ConductionArea) {
            return new AutorouteAttemptResult(AutorouteAttemptState.CONNECTED_TO_PLANE);
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
      calcAirline(routeStartSet, routeDestSet);

      // Calculate the maximum time for this autoroute pass
      double maxMilliseconds = 100000 * Math.pow(2, ripupPassNo - 1);
      maxMilliseconds = Math.min(maxMilliseconds, Integer.MAX_VALUE);
      TimeLimit timeLimit = new TimeLimit((int) maxMilliseconds);

      // Initialize the auto-router engine
      AutorouteEngine autorouteEngine =
          board.initAutoroute(
              routeNetNo,
              autorouteControl.traceClearanceClassNo,
              this.thread,
              timeLimit,
              this.retainAutorouteDatabase);

      int maxItemIdBeforeRoute = board.communication.idNoGenerator.maxGeneratedNo();

      byte[] strictDrcBoardSnapshot = this.settings.isStrictDrc() ? board.serialize(false) : null;

      // Do the auto-routing between the two sets of items
      AutorouteAttemptResult autorouteResult =
          autorouteEngine.autorouteConnection(
              routeStartSet, routeDestSet, autorouteControl, rippedItemList, ripupCosts);

      // Update the changed area of the board
      if (autorouteResult.state == AutorouteAttemptState.ROUTED) {
        int maxItemIdBeforeOpt = board.communication.idNoGenerator.maxGeneratedNo();
        FRLogger.trace(
            "compare_trace_opt_changed_area_before net="
                + routeNetNo
                + ", maxItemId="
                + maxItemIdBeforeOpt);
        board.optChangedArea(
            new int[0],
            null,
            this.tracePullTightAccuracy,
            autorouteControl.traceCosts,
            this.thread,
            TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
        int maxItemIdAfterOpt = board.communication.idNoGenerator.maxGeneratedNo();
        FRLogger.trace(
            "compare_trace_opt_changed_area_after net="
                + routeNetNo
                + ", maxItemId="
                + maxItemIdAfterOpt
                + ", delta="
                + (maxItemIdAfterOpt - maxItemIdBeforeOpt));
      }

      if ((autorouteResult.state == AutorouteAttemptState.FAILED
              || autorouteResult.state == AutorouteAttemptState.INSERT_ERROR)
          && this.settings.getNeckWidthUm() > 0) {
        AutorouteAttemptResult neckedResult =
            retryConnectionNecked(
                routeNetNo,
                autorouteControl,
                currViaCosts,
                routeStartSet,
                routeDestSet,
                rippedItemList,
                ripupCosts,
                ripupPassNo,
                timeLimit);
        if (neckedResult != null) {
          AutorouteAttemptResult strictResult =
              applyStrictDrcAfterRoute(routeNetNo, maxItemIdBeforeRoute, strictDrcBoardSnapshot);
          if (strictResult != null) {
            return strictResult;
          }
          return neckedResult;
        }
      }

      if (autorouteResult.state == AutorouteAttemptState.ROUTED) {
        AutorouteAttemptResult strictResult =
            applyStrictDrcAfterRoute(routeNetNo, maxItemIdBeforeRoute, strictDrcBoardSnapshot);
        if (strictResult != null) {
          return strictResult;
        }
      }

      return autorouteResult;
    } catch (Exception e) {
      FRLogger.error("Error during routing passes", e);
      return new AutorouteAttemptResult(AutorouteAttemptState.FAILED);
    }
  }

  /**
   * Width-necking retry: when a connection failed at its net-class trace width and the
   * neck_width_um setting is enabled, retry it ONCE with every layer's trace half-width clamped to
   * the neck width. Fine-pitch pads whose pitch is below (class width + clearance) are unroutable
   * at class width and fail as generic congestion; the operator supplies a legal manufacturable
   * neck width (e.g. the project's densest net class). Returns the retry result when it routed,
   * else null (keep the original failure).
   */
  private AutorouteAttemptResult retryConnectionNecked(
      int routeNetNo,
      AutorouteControl originalControl,
      int viaCosts,
      Set<Item> routeStartSet,
      Set<Item> routeDestSet,
      SortedSet<Item> rippedItemList,
      Map<Item, Integer> ripupCosts,
      int ripupPassNo,
      TimeLimit timeLimit) {
    int boardResolution = Math.max(1, board.communication.resolution);
    int neckWidth =
        (int)
            Math.round(
                app.freerouting.board.Unit.scale(
                    this.settings.getNeckWidthUm() * boardResolution,
                    app.freerouting.board.Unit.UM,
                    board.communication.unit));
    int neckHalfWidth = Math.max(1, neckWidth / 2);
    boolean narrowerSomewhere = false;
    for (int i = 0; i < originalControl.layerCount; i++) {
      if (originalControl.layerActive[i] && originalControl.traceHalfWidth[i] > neckHalfWidth) {
        narrowerSomewhere = true;
        break;
      }
    }
    if (!narrowerSomewhere) {
      return null;
    }
    AutorouteControl neckControl =
        new AutorouteControl(this.board, routeNetNo, settings, viaCosts, this.traceCostArr);
    neckControl.ripupAllowed = true;
    neckControl.ripupCosts = this.startRipupCosts * ripupPassNo;
    neckControl.removeUnconnectedVias = this.removeUnconnectedVias;
    for (int i = 0; i < neckControl.layerCount; i++) {
      int compensation = neckControl.compensatedTraceHalfWidth[i] - neckControl.traceHalfWidth[i];
      neckControl.traceHalfWidth[i] = Math.min(neckControl.traceHalfWidth[i], neckHalfWidth);
      neckControl.compensatedTraceHalfWidth[i] = neckControl.traceHalfWidth[i] + compensation;
    }
    AutorouteEngine neckEngine =
        board.initAutoroute(
            routeNetNo,
            neckControl.traceClearanceClassNo,
            this.thread,
            timeLimit,
            this.retainAutorouteDatabase);
    AutorouteAttemptResult neckResult =
        neckEngine.autorouteConnection(
            routeStartSet, routeDestSet, neckControl, rippedItemList, ripupCosts);
    if (neckResult.state != AutorouteAttemptState.ROUTED) {
      return null;
    }
    board.optChangedArea(
        new int[0],
        null,
        this.tracePullTightAccuracy,
        neckControl.traceCosts,
        this.thread,
        TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP);
    Net routeNet = board.rules.nets.get(routeNetNo);
    FRLogger.info(
        "Necked retry routed net '"
            + (routeNet != null ? routeNet.name : "#" + routeNetNo)
            + "' at "
            + this.settings.getNeckWidthUm()
            + " um trace width.");
    return neckResult;
  }

  /**
   * When {@code strict_drc} rejects a routed connection, restore the board snapshot taken before
   * {@link AutorouteEngine#autorouteConnection} so rip-up victims removed during routing are not
   * left torn up.
   */
  private AutorouteAttemptResult applyStrictDrcAfterRoute(
      int routeNetNo, int maxItemIdBefore, byte[] boardSnapshotBeforeRoute) {
    if (!this.settings.isStrictDrc()) {
      return null;
    }
    AutorouteAttemptResult rejection = enforceStrictDrc(board, routeNetNo, maxItemIdBefore);
    if (rejection != null && boardSnapshotBeforeRoute != null) {
      this.board = (RoutingBoard) BasicBoard.deserialize(boardSnapshotBeforeRoute);
    }
    return rejection;
  }

  /**
   * Strict-DRC enforcement: if any trace/via inserted by the connection that just routed (item id
   * above {@code p_max_item_id_before}) carries a clearance violation, rip the whole set of new
   * items and report the connection FAILED, so the pass counts it as not routed and later passes
   * (higher ripup costs) retry it. Returns null when the connection is clean and may be kept.
   */
  static AutorouteAttemptResult enforceStrictDrc(
      app.freerouting.board.RoutingBoard board, int routeNetNo, int maxItemIdBefore) {
    List<Item> newItems = new ArrayList<>();
    boolean hasViolation = false;
    for (Item currItem : board.getConnectableItems(routeNetNo)) {
      if (currItem.getIdNo() <= maxItemIdBefore
          || !(currItem instanceof Trace || currItem instanceof app.freerouting.board.Via)) {
        continue;
      }
      newItems.add(currItem);
      if (!hasViolation && !currItem.clearanceViolations().isEmpty()) {
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

  // Calculates the shortest distance between two sets of items, specifically
  // between Pin and Via items (pins and vias are connectable DrillItems)
  private void calcAirline(Collection<Item> fromItems, Collection<Item> toItems) {
    FloatPoint fromCorner = null;
    FloatPoint toCorner = null;
    double minDistance = Double.MAX_VALUE;
    for (Item currFromItem : fromItems) {
      if (!(currFromItem instanceof DrillItem)) {
        continue;
      }
      FloatPoint currFromCorner = ((DrillItem) currFromItem).getCenter().toFloat();

      for (Item currToItem : toItems) {
        if (!(currToItem instanceof DrillItem)) {
          continue;
        }
        FloatPoint currToCorner = ((DrillItem) currToItem).getCenter().toFloat();
        double currDistance = currFromCorner.distanceSquare(currToCorner);
        if (currDistance < minDistance) {
          minDistance = currDistance;
          fromCorner = currFromCorner;
          toCorner = currToCorner;
        }
      }
    }
    this.airLine = new FloatLine(fromCorner, toCorner);
  }

  /** Finds the nearest point on a trace to the given point. */
  private FloatPoint nearestPointOnTrace(PolylineTrace trace, FloatPoint point) {
    double minDistance = Double.MAX_VALUE;
    FloatPoint nearestPoint = null;

    // Get endpoints
    FloatPoint firstCorner = trace.firstCorner().toFloat();
    FloatPoint lastCorner = trace.lastCorner().toFloat();

    // Check distance to endpoints first
    double distanceToFirst = point.distance(firstCorner);
    double distanceToLast = point.distance(lastCorner);

    if (distanceToFirst < minDistance) {
      minDistance = distanceToFirst;
      nearestPoint = firstCorner;
    }

    if (distanceToLast < minDistance) {
      minDistance = distanceToLast;
      nearestPoint = lastCorner;
    }

    // Check distances to line segments
    for (int i = 0; i < trace.cornerCount() - 1; i++) {
      FloatPoint segmentStart = trace.polyline().cornerApprox(i);
      FloatPoint segmentEnd = trace.polyline().cornerApprox(i + 1);
      FloatLine segment = new FloatLine(segmentStart, segmentEnd);

      FloatPoint projection = segment.perpendicularProjection(point);
      if (projection.isContainedInBox(segmentStart, segmentEnd, 0.01)) {
        double distance = point.distance(projection);
        if (distance < minDistance) {
          minDistance = distance;
          nearestPoint = projection;
        }
      }
    }

    return nearestPoint;
  }

  /**
   * Finds the closest points between two traces.
   *
   * @return an array with two FloatPoints: [point_on_first_trace, point_on_second_trace]
   */
  private FloatPoint[] findClosestPointsBetweenTraces(
      PolylineTrace firstTrace, PolylineTrace secondTrace) {
    double minDistance = Double.MAX_VALUE;
    FloatPoint[] result = new FloatPoint[2];

    // Check endpoints to endpoints
    FloatPoint firstTraceStart = firstTrace.firstCorner().toFloat();
    final FloatPoint firstTraceEnd = firstTrace.lastCorner().toFloat();
    FloatPoint secondTraceStart = secondTrace.firstCorner().toFloat();
    FloatPoint secondTraceEnd = secondTrace.lastCorner().toFloat();

    // Check all endpoint combinations
    double distance = firstTraceStart.distance(secondTraceStart);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceStart;
      result[1] = secondTraceStart;
    }

    distance = firstTraceStart.distance(secondTraceEnd);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceStart;
      result[1] = secondTraceEnd;
    }

    distance = firstTraceEnd.distance(secondTraceStart);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceEnd;
      result[1] = secondTraceStart;
    }

    distance = firstTraceEnd.distance(secondTraceEnd);
    if (distance < minDistance) {
      minDistance = distance;
      result[0] = firstTraceEnd;
      result[1] = secondTraceEnd;
    }

    // Check all segment combinations for closest points
    for (int i = 0; i < firstTrace.cornerCount() - 1; i++) {
      FloatPoint firstSegmentStart = firstTrace.polyline().cornerApprox(i);
      FloatPoint firstSegmentEnd = firstTrace.polyline().cornerApprox(i + 1);
      FloatLine firstSegment = new FloatLine(firstSegmentStart, firstSegmentEnd);

      for (int j = 0; j < secondTrace.cornerCount() - 1; j++) {
        FloatPoint secondSegmentStart = secondTrace.polyline().cornerApprox(j);
        FloatPoint secondSegmentEnd = secondTrace.polyline().cornerApprox(j + 1);
        FloatLine secondSegment = new FloatLine(secondSegmentStart, secondSegmentEnd);

        // Find closest points between these two line segments
        FloatPoint pointOnFirst = firstSegment.nearestSegmentPoint(secondSegmentStart);
        FloatPoint pointOnSecond = secondSegment.perpendicularProjection(pointOnFirst);

        // Check if projection is on the segment
        if (!pointOnSecond.isContainedInBox(secondSegmentStart, secondSegmentEnd, 0.01)) {
          // If not, use the nearest endpoint
          double distToStart = pointOnFirst.distance(secondSegmentStart);
          double distToEnd = pointOnFirst.distance(secondSegmentEnd);
          pointOnSecond = distToStart < distToEnd ? secondSegmentStart : secondSegmentEnd;
        }

        // Recalculate the point on first segment based on the point on second segment
        pointOnFirst = firstSegment.nearestSegmentPoint(pointOnSecond);

        distance = pointOnFirst.distance(pointOnSecond);
        if (distance < minDistance) {
          minDistance = distance;
          result[0] = pointOnFirst;
          result[1] = pointOnSecond;
        }
      }
    }

    return result;
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

  /**
   * Calculates the airline distance for an item to be routed. Returns the shortest distance from
   * the item to any item in its incomplete connections.
   *
   * @param item The item to calculate distance for
   * @return The shortest airline distance, or Double.MAX_VALUE if no connections exist
   */
  private double calculateItemDistance(Item item) {
    if (item.netCount() == 0) {
      return Double.MAX_VALUE;
    }

    // Get the first net number (items typically have one net)
    int netNo = item.getNetNo(0);

    // Get incomplete items for this net
    Set<Item> unconnectedSet = item.getUnconnectedSet(netNo);
    Set<Item> connectedSet = item.getConnectedSet(netNo);

    if (unconnectedSet.isEmpty()) {
      return 0; // Already connected, prioritize
    }

    // Calculate minimum distance from connected items to unconnected items
    return calculateMinDistance(
        connectedSet.isEmpty() ? Set.of(item) : connectedSet, unconnectedSet);
  }

  /** Helper method to calculate the minimum distance between two sets of items. */
  private double calculateMinDistance(Collection<Item> fromItems, Collection<Item> toItems) {
    double minDistance = Double.MAX_VALUE;

    for (Item fromItem : fromItems) {
      FloatPoint fromPoint = getItemReferencePoint(fromItem);
      if (fromPoint == null) {
        continue;
      }

      for (Item toItem : toItems) {
        FloatPoint toPoint = getItemReferencePoint(toItem);
        if (toPoint == null) {
          continue;
        }

        double distance = fromPoint.distance(toPoint);
        if (distance < minDistance) {
          minDistance = distance;
        }
      }
    }

    return minDistance;
  }

  /** Gets a representative point for an item (center for DrillItems, midpoint for traces). */
  private FloatPoint getItemReferencePoint(Item item) {
    if (item instanceof DrillItem drillItem) {
      return drillItem.getCenter().toFloat();
    } else if (item instanceof PolylineTrace trace) {
      // Use the midpoint of the trace as a reference
      FloatPoint first = trace.firstCorner().toFloat();
      FloatPoint last = trace.lastCorner().toFloat();
      return new FloatPoint((first.x + last.x) / 2, (first.y + last.y) / 2);
    }
    return null;
  }

  private int calculateIncompleteCount(RoutingBoard board) {
    DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
    tempDrc.calculateAllIncompletes();
    return tempDrc.getIncompleteCount();
  }
}

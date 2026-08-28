package app.freerouting.autoroute.pipeline;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.autoroute.ItemRouteResult;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.ProgressThrottler;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/** Optimizes routes using a single thread on a board that has completed auto-routing. */
public non-sealed class BatchOptimizer extends NamedAlgorithm {

  protected final ProgressThrottler progressThrottler = new ProgressThrottler(1000);
  protected ReadSortedRouteItems sortedRouteItems;
  // in the first passes the ripup costs are increased for better performance.
  protected boolean useIncreasedRipupCosts;
  // the minimum cumulative trace length that was reached during the optimization
  protected double minCumulativeTraceLength = 0.0;
  protected RoutingJob job;
  protected int totalItemsOptimized;
  protected Long deadlineMs;
  protected boolean isTimedOut;

  /**
   * Creates a new instance of BatchOptRoute, which is used to optimize the board.
   *
   * @param job the routing job to optimize.
   */
  public BatchOptimizer(RoutingJob job) {
    super(job.thread, job.board, job.routerSettings);
    this.job = job;
  }

  /** Creates the optimizer used by headless jobs. */
  public static BatchOptimizer createForHeadless(RoutingJob job) {
    return new BatchOptimizer(job);
  }

  /** Creates the optimizer used by GUI jobs, honoring the GUI multithreading feature flag. */
  public static BatchOptimizer createForGui(RoutingJob job) {
    BatchOptimizer optimizer;
    if (globalSettings != null
        && globalSettings.featureFlags.multiThreading
        && job.routerSettings.optimizer.maxThreads > 1) {
      optimizer = new BatchOptimizerMultiThreaded(job);
    } else {
      optimizer = new BatchOptimizer(job);
    }

    normalizeAlgorithm(job, optimizer);
    return optimizer;
  }

  private static void normalizeAlgorithm(RoutingJob job, BatchOptimizer optimizer) {
    if (!optimizer.getId().equals(job.routerSettings.optimizer.algorithm)) {
      job.logWarning(
          "The algorithm '"
              + job.routerSettings.optimizer.algorithm
              + "' is not supported by the batch autorouter. The default algorithm '"
              + optimizer.getId()
              + "' will be used instead.");
      job.routerSettings.optimizer.algorithm = optimizer.getId();
    }
  }

  /** Returns true if timed out. */
  public boolean isTimedOut() {
    return this.isTimedOut;
  }

  static boolean containsOnlyUnfixedTraces(Collection<Item> itemList) {
    for (Item currentItem : itemList) {
      if (currentItem.isUserFixed() || !(currentItem instanceof Trace)) {
        return false;
      }
    }
    return true;
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

  /** Optimize the route on the board. */
  public void runBatchLoop() {
    job.logDebug(
        "Before optimization: Via count: "
            + board.getVias().size()
            + ", trace length: "
            + Math.round(board.cumulativeTraceLength()));

    useIncreasedRipupCosts = true;

    // Capture initial board state for session summary
    BoardStatistics initialStats = board.getStatistics();
    float initialScore = initialStats.getNormalizedScore(job.routerSettings.scoring);
    int initialIncomplete = initialStats.connections.incompleteCount;
    int initialViolations = initialStats.clearanceViolations.totalCount;

    job.logInfo(
        "Optimization stage started on board '"
            + this.board.getHash()
            + "' with score "
            + FRLogger.formatScore(initialScore, initialIncomplete, initialViolations)
            + ".");

    // Capture start-of-session resource usage baselines
    long sessionStartMs = System.currentTimeMillis();
    final float cpuSecondsStart = sampleCurrentThreadCpuSeconds();
    final float allocMbStart = sampleCurrentThreadAllocatedMb();
    float peakHeapMb = sampleHeapUsageMb();

    if (this.settings.optimizer != null && this.settings.optimizer.timeoutString != null) {
      Long timeoutSeconds =
          app.freerouting.util.TextManager.parseTimespanString(
              this.settings.optimizer.timeoutString);
      if (timeoutSeconds != null) {
        this.deadlineMs = sessionStartMs + timeoutSeconds * 1000;
      }
    }

    this.fireTaskStateChangedEvent(
        new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.getHash()));

    double scoreImprovement = -1;
    int currentPass = 0;
    while ((this.settings.optimizer.maxPasses == null
            || currentPass < this.settings.optimizer.maxPasses)
        && (this.settings.optimizer.maxItems == null
            || this.totalItemsOptimized < this.settings.optimizer.maxItems)
        && (!this.thread.isStopRequested())) {
      if (this.deadlineMs != null && System.currentTimeMillis() >= this.deadlineMs) {
        this.isTimedOut = true;
        job.logInfo("Optimizer stage timed out before starting pass #" + (currentPass + 1));
        break;
      }
      ++currentPass;

      float scoreBeforePass = board.getStatistics().getNormalizedScore(job.routerSettings.scoring);

      // Stop if potential improvement is less than threshold
      if (scoreBeforePass * (1 + this.settings.optimizer.optimizationImprovementThreshold)
          >= 1000.0f) {
        job.logInfo(
            String.format(
                java.util.Locale.US,
                "Stopping optimizer because the current board score (%.2f) is already close to the "
                    + "maximum score (1000). Remaining potential improvement is less than the "
                    + "threshold (%.2f%%).",
                scoreBeforePass,
                this.settings.optimizer.optimizationImprovementThreshold * 100));
        break;
      }

      String currentBoardHash = this.board.getHash();
      job.setCurrentPass(currentPass);
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.RUNNING, currentPass, currentBoardHash));

      boolean withPreferredDirections = currentPass % 2 != 0; // to create more variations
      optRoutePass(currentPass, withPreferredDirections);
      peakHeapMb = Math.max(peakHeapMb, sampleHeapUsageMb());

      if (this.isTimedOut) {
        break;
      }

      float scoreAfterPass = board.getStatistics().getNormalizedScore(job.routerSettings.scoring);
      double passImprovement =
          scoreBeforePass > 0 ? (double) (scoreAfterPass - scoreBeforePass) / scoreBeforePass : 0;

      if (this.useIncreasedRipupCosts && scoreAfterPass <= scoreBeforePass) {
        this.useIncreasedRipupCosts = false;
        // Keep the optimizer going to try with normal ripup costs
        scoreImprovement = -1;
      } else {
        scoreImprovement = passImprovement;
      }

      if (scoreImprovement != -1
          && scoreImprovement < this.settings.optimizer.optimizationImprovementThreshold) {
        job.logInfo(
            String.format(
                java.util.Locale.US,
                "Stopping optimizer because the improvement in this pass (%.4f%%) is below "
                    + "the threshold (%.2f%%).",
                scoreImprovement * 100,
                this.settings.optimizer.optimizationImprovementThreshold * 100));
        break;
      }
    }

    this.fireTaskStateChangedEvent(
        new TaskStateChangedEvent(this, TaskState.FINISHED, currentPass, this.board.getHash()));

    // Session summary
    double sessionDurationSeconds = (System.currentTimeMillis() - sessionStartMs) / 1000.0;
    float cpuSecondsEnd = sampleCurrentThreadCpuSeconds();
    float allocMbEnd = sampleCurrentThreadAllocatedMb();
    float cpuSecondsUsed =
        cpuSecondsStart >= 0f && cpuSecondsEnd >= cpuSecondsStart
            ? cpuSecondsEnd - cpuSecondsStart
            : Math.max(0f, cpuSecondsEnd);
    float allocMbUsed =
        allocMbStart >= 0f && allocMbEnd >= allocMbStart
            ? allocMbEnd - allocMbStart
            : Math.max(0f, allocMbEnd);
    peakHeapMb = Math.max(peakHeapMb, sampleHeapUsageMb());

    BoardStatistics finalStats = new BoardStatistics(this.board);
    float finalScore = finalStats.getNormalizedScore(job.routerSettings.scoring);
    String completionStatus =
        this.isTimedOut
            ? "completed with timeout:"
            : (this.thread.isStopRequested() ? "interrupted:" : "completed:");
    job.logInfo(
        String.format(
            java.util.Locale.US,
            "Optimization stage %s started with score %s, completed in %.2f seconds, "
                + "final score: %s, using %.2f total CPU seconds, %.2f GB total allocated, "
                + "and %.1f MB peak heap usage.",
            completionStatus,
            FRLogger.formatScore(initialScore, initialIncomplete, initialViolations),
            sessionDurationSeconds,
            FRLogger.formatScore(
                finalScore,
                finalStats.connections.incompleteCount,
                finalStats.clearanceViolations.totalCount),
            cpuSecondsUsed,
            allocMbUsed / 1024.0f,
            peakHeapMb));
  }

  /**
   * Tries to reduce the number of vias and the trace length of a completely routed board. Returns
   * the amount of improvements is made in percentage (expressed between 0.0 and 1.0). -1 if the
   * routing must go on no matter how much it improved.
   */
  protected float optRoutePass(int passNo, boolean withPreferredDirections) {

    BoardStatistics boardStatisticsBefore = board.getStatistics();
    RouterCounters routerCounters = new RouterCounters();
    routerCounters.passCount = passNo;
    progressThrottler.reset();
    this.fireBoardUpdatedEvent(boardStatisticsBefore, routerCounters, this.board);

    this.sortedRouteItems = new ReadSortedRouteItems();
    this.minCumulativeTraceLength = boardStatisticsBefore.traces.totalWeightedLength;
    String optimizationPassId =
        "BatchOptRoute.opt_route_pass #"
            + passNo
            + " with "
            + boardStatisticsBefore.items.viaCount
            + " vias and "
            + "%(,.2f".formatted(boardStatisticsBefore.traces.totalLength)
            + " trace length.";

    FRLogger.traceEntry(optimizationPassId);

    int consecutiveFailures = 0;
    int maxConsecutiveFailures =
        this.settings.optimizer.maxConsecutiveFailures != null
            ? this.settings.optimizer.maxConsecutiveFailures
            : 50;

    float routeImproved = 0.0F;
    while (true) {
      if (this.deadlineMs != null && System.currentTimeMillis() >= this.deadlineMs) {
        job.logInfo("Optimizer stage timed out.");
        this.isTimedOut = true;
        FRLogger.traceExit(optimizationPassId);
        return routeImproved;
      }
      if (this.thread.isStopRequested()) {
        FRLogger.traceExit(optimizationPassId);
        return routeImproved;
      }
      if (this.settings.optimizer.maxItems != null
          && this.settings.optimizer.maxItems > 0
          && this.totalItemsOptimized >= this.settings.optimizer.maxItems) {
        job.logInfo(
            "Max items limit reached ("
                + this.settings.optimizer.maxItems
                + "). Stopping optimizer.");
        break;
      }
      Item currentItem = sortedRouteItems.next();
      if (currentItem == null) {
        break;
      }
      ItemRouteResult result = optRouteItem(currentItem, withPreferredDirections, false);
      this.totalItemsOptimized++;
      if (result.improved()) {
        consecutiveFailures = 0;
        if (progressThrottler.shouldUpdate()) {
          BoardStatistics boardStatisticsAfter = board.getStatistics();
          this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCounters, board);
        }

        routeImproved =
            (float)
                (boardStatisticsBefore.items.viaCount != 0
                        && boardStatisticsBefore.traces.totalLength != 0
                    ? 1.0
                        - ((((float) result.viaCount() / boardStatisticsBefore.items.viaCount)
                                + (result.traceLength() / boardStatisticsBefore.traces.totalLength))
                            / 2)
                    : 0);
      } else {
        consecutiveFailures++;
        if (consecutiveFailures >= maxConsecutiveFailures) {
          job.logInfo(
              String.format(
                  java.util.Locale.US,
                  "Stopping optimization pass #%d early after %d consecutive items "
                      + "could not be improved.",
                  passNo,
                  consecutiveFailures));
          break;
        }
      }
    }

    this.sortedRouteItems = null;
    if (this.useIncreasedRipupCosts && (routeImproved == 0)) {
      this.useIncreasedRipupCosts = false;
      routeImproved = -1; // to keep the optimizer going with lower ripup costs
    }

    double routeoptimizerPassDuration = FRLogger.traceExit(optimizationPassId);
    BoardStatistics boardStatisticsAfter = new BoardStatistics(this.board);
    this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCounters, this.board);
    job.logInfo(
        String.format(
            java.util.Locale.US,
            "Optimizer pass #%d on board '%s' was completed in %.2f seconds with the score of %s.",
            passNo,
            this.board.getHash(),
            routeoptimizerPassDuration,
            FRLogger.formatScore(
                boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring),
                boardStatisticsAfter.connections.incompleteCount,
                boardStatisticsAfter.clearanceViolations.totalCount)));
    return routeImproved;
  }

  /**
   * Try to improve the route by re-routing the connections containing item.
   *
   * @param item the item to be re-routed
   * @param withPreferredDirections if true, the preferred directions are used for the traces
   * @param disableSnapshots if true, the snapshots are not used which means that the routing cannot
   *     be undone, but it's much more efficient
   */
  protected ItemRouteResult optRouteItem(
      Item item, boolean withPreferredDirections, boolean disableSnapshots) {
    // check if item.board is a RoutingBoard
    if (!(item.board instanceof RoutingBoard routingBoard)) {
      job.logWarning("The item to be optimized is not on a RoutingBoard.");
      return new ItemRouteResult(item.getId());
    }

    // calculate the statistics for the board before the routing
    BoardStatistics boardStatisticsBefore = new BoardStatistics(routingBoard, null, false);
    RouterCounters routerCountersBefore = new RouterCounters();
    routerCountersBefore.incompleteCount = calculateIncompleteCount(routingBoard);
    if (progressThrottler.shouldUpdate()) {
      this.fireBoardUpdatedEvent(boardStatisticsBefore, routerCountersBefore, routingBoard);
    }

    // collect the items to be re-routed
    Set<Item> rippedItems = new TreeSet<>();
    rippedItems.add(item);

    // add the contacts of the traces to the ripped items if it's a trace
    if (item instanceof Trace currentTrace) {
      // add also the fork items, especially because not all fork items may be
      // returned by ReadSortedRouteItems because of matching end points.
      Set<Item> currentContactList = currentTrace.getStartContacts();
      for (int i = 0; i < 2; i++) {
        if (containsOnlyUnfixedTraces(currentContactList)) {
          rippedItems.addAll(currentContactList);
        }
        currentContactList = currentTrace.getEndContacts();
      }
    }

    Set<Item> rippedConnections = new TreeSet<>();
    // add all the connections of the items to be re-routed
    for (Item currentItem : rippedItems) {
      rippedConnections.addAll(currentItem.getConnectionItems(Item.StopConnectionOption.NONE));
    }

    // check if the connections contain user fixed items, which should not be
    // re-routed
    for (Item currentItem : rippedConnections) {
      if (currentItem.isUserFixed()) {
        return new ItemRouteResult(item.getId());
      }
    }

    if (!disableSnapshots) {
      // make the current situation restorable by undo with the snapshot
      routingBoard.generateSnapshot();
    }

    // remove the items to be re-routed
    routingBoard.removeItems(rippedConnections);
    for (int i = 0; i < item.netCount(); i++) {
      routingBoard.combineTraces(item.getNetNumber(i));
    }

    // calculate the ripup costs
    int ripupCosts = this.settings.getStartRipupCosts();
    if (this.useIncreasedRipupCosts) {
      ripupCosts *= this.settings.optimizer.additionalRipupCostFactorAtStart;
    }

    // reduce the ripup costs for traces
    if (item instanceof Trace) {
      ripupCosts =
          (int) Math.round(this.settings.optimizer.traceRipupCostFactor * (double) ripupCosts);
    }

    // route the connections
    BatchAutorouter.autoroutePassesForOptimizingItem(
        job,
        this.settings.optimizer.maxAutoroutePasses,
        ripupCosts,
        settings.tracePullTightAccuracy,
        withPreferredDirections,
        routingBoard,
        settings);

    // check the result by generating the statistics for the board again after the
    // routing
    BoardStatistics boardStatisticsAfter = new BoardStatistics(routingBoard, null, false);
    RouterCounters routerCountersAfter = new RouterCounters();
    routerCountersAfter.incompleteCount = calculateIncompleteCount(routingBoard);
    if (progressThrottler.shouldUpdate()) {
      this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCountersAfter, routingBoard);
    }

    // check if the board was improved
    ItemRouteResult result =
        new ItemRouteResult(
            item.getId(),
            boardStatisticsBefore.items.viaCount,
            boardStatisticsAfter.items.viaCount,
            this.minCumulativeTraceLength,
            boardStatisticsAfter.traces.totalLength,
            routerCountersBefore.incompleteCount,
            routerCountersAfter.incompleteCount);
    boolean routeImproved = !this.thread.isStopRequested() && result.improved();
    result.updateImproved(routeImproved);

    if (routeImproved) {
      this.minCumulativeTraceLength =
          Math.min(this.minCumulativeTraceLength, boardStatisticsAfter.traces.totalWeightedLength);

      if (!disableSnapshots) {
        // this was a successful routing, so the snapshot can be removed
        routingBoard.popSnapshot();
      }
    } else {
      if (!disableSnapshots) {
        // this was not a successful routing, so we can undo the routing using the
        // snapshot
        routingBoard.undo(null);
      }
    }

    return result;
  }

  /**
   * Returns the current position of the item, which will be rerouted or null, if the optimizer is
   * not active.
   */
  public FloatPoint getCurrentPosition() {
    if (sortedRouteItems == null) {
      return null;
    }
    return sortedRouteItems.getCurrentPosition();
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

  private int calculateIncompleteCount(RoutingBoard board) {
    DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
    tempDrc.calculateAllIncompletes();
    return tempDrc.getIncompleteCount();
  }

  /**
   * Reads the vias and traces on the board in ascending x order. Because the vias and traces on the
   * board change while optimizing the item list of the board is read from scratch each time the
   * next route item is returned.
   */
  protected class ReadSortedRouteItems {

    protected FloatPoint minItemCoor;
    protected int minItemLayer;

    ReadSortedRouteItems() {
      minItemCoor = new FloatPoint(Integer.MIN_VALUE, Integer.MIN_VALUE);
      minItemLayer = -1;
    }

    Item next() {
      Item result = null;
      FloatPoint currentMinCoor = new FloatPoint(Integer.MAX_VALUE, Integer.MAX_VALUE);
      int currentMinLayer = Integer.MAX_VALUE;
      Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.startReadObject();
      for (; ; ) {
        UndoableObjects.Storable currentItem = board.itemList.readObject(it);
        if (currentItem == null) {
          break;
        }
        if (currentItem instanceof Via currentVia) {
          if (!currentVia.isUserFixed()) {
            FloatPoint currentViaCenter = currentVia.getCenter().toFloat();
            int currentViaMinLayer = currentVia.firstLayer();
            if (currentViaCenter.x > minItemCoor.x
                || currentViaCenter.x == minItemCoor.x
                    && (currentViaCenter.y > minItemCoor.y
                        || currentViaCenter.y == minItemCoor.y
                            && currentViaMinLayer > minItemLayer)) {
              if (currentViaCenter.x < currentMinCoor.x
                  || currentViaCenter.x == currentMinCoor.x
                      && (currentViaCenter.y < currentMinCoor.y
                          || currentViaCenter.y == currentMinCoor.y
                              && currentViaMinLayer < currentMinLayer)) {
                currentMinCoor = currentViaCenter;
                currentMinLayer = currentViaMinLayer;
                result = currentVia;
              }
            }
          }
        }
      }
      // Read traces last to prefer vias to traces at the same location
      it = board.itemList.startReadObject();
      for (; ; ) {
        UndoableObjects.Storable currentItem = board.itemList.readObject(it);
        if (currentItem == null) {
          break;
        }
        if (currentItem instanceof Trace currentTrace) {
          if (!currentTrace.isShoveFixed()) {
            FloatPoint firstCorner = currentTrace.firstCorner().toFloat();
            FloatPoint lastCorner = currentTrace.lastCorner().toFloat();
            FloatPoint compareCorner;
            if (firstCorner.x < lastCorner.x
                || firstCorner.x == lastCorner.x && firstCorner.y < lastCorner.y) {
              compareCorner = lastCorner;
            } else {
              compareCorner = firstCorner;
            }
            int currentTraceLayer = currentTrace.getLayer();
            if (compareCorner.x > minItemCoor.x
                || compareCorner.x == minItemCoor.x
                    && (compareCorner.y > minItemCoor.y
                        || compareCorner.y == minItemCoor.y && currentTraceLayer > minItemLayer)) {
              if (compareCorner.x < currentMinCoor.x
                  || compareCorner.x == currentMinCoor.x
                      && (compareCorner.y < currentMinCoor.y
                          || compareCorner.y == currentMinCoor.y
                              && currentTraceLayer < currentMinLayer)) {
                boolean isConnectedToVia = false;
                Set<Item> traceContacts = currentTrace.getNormalContacts();
                for (Item currentContact : traceContacts) {
                  if (currentContact instanceof Via && !currentContact.isUserFixed()) {
                    isConnectedToVia = true;
                    break;
                  }
                }
                if (!isConnectedToVia) {
                  currentMinCoor = compareCorner;
                  currentMinLayer = currentTraceLayer;
                  result = currentTrace;
                }
              }
            }
          }
        }
      }
      minItemCoor = currentMinCoor;
      minItemLayer = currentMinLayer;
      return result;
    }

    FloatPoint getCurrentPosition() {
      return minItemCoor;
    }
  }
}

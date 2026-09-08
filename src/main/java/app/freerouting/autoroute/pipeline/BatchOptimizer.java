package app.freerouting.autoroute.pipeline;

import app.freerouting.autoroute.ItemRouteResult;
import app.freerouting.autoroute.ItemSelectionStrategy;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Trace;
import app.freerouting.board.model.items.Via;
import app.freerouting.core.ProgressThrottler;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Optimizes routes using a deterministic thread pool pipeline on a board that has completed
 * auto-routing.
 */
public final class BatchOptimizer extends NamedAlgorithm {

  protected final ProgressThrottler progressThrottler = new ProgressThrottler(1000);
  protected ReadSortedRouteItems sortedRouteItems;
  // In the first passes the ripup costs are increased for better performance.
  protected boolean useIncreasedRipupCosts;
  // The minimum cumulative trace length that was reached during the optimization
  protected double minCumulativeTraceLength = 0.0;
  protected RoutingJob job;
  protected int totalItemsOptimized;
  protected Long deadlineMs;
  protected boolean isTimedOut;
  protected RoutingBoard bestBoard;
  protected float bestScore;
  protected final Map<Integer, ItemRouteResult> resultMap = new HashMap<>();
  protected final AtomicLong workerCpuNanos = new AtomicLong(0);
  protected final AtomicLong workerAllocBytes = new AtomicLong(0);
  protected volatile FloatPoint currentPosition = null;

  /**
   * Creates a new instance of BatchOptimizer, which is used to optimize the board.
   *
   * @param job the routing job to optimize.
   */
  public BatchOptimizer(RoutingJob job) {
    super(job.thread, job.board, job.routerSettings);
    this.job = job;
  }

  /** Creates the canonical optimizer for a routing job. */
  public static BatchOptimizer create(RoutingJob job) {
    BatchOptimizer optimizer = new BatchOptimizer(job);
    normalizeAlgorithm(job, optimizer);
    return optimizer;
  }

  /** Creates the optimizer used by headless jobs. */
  public static BatchOptimizer createForHeadless(RoutingJob job) {
    return create(job);
  }

  /** Creates the optimizer used by GUI jobs. */
  public static BatchOptimizer createForGui(RoutingJob job) {
    return create(job);
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

    // Capture initial board state for baseline and session summary
    BoardStatistics initialStats = board.getStatistics();
    float initialRouterScore = initialStats.getRouterScore(job.routerSettings);
    float initialOptimizerScore = initialStats.getOptimizerScore(job.routerSettings);
    int initialIncomplete = initialStats.connections.incompleteCount;
    int initialViolations = initialStats.clearanceViolations.totalCount;

    this.bestBoard = this.board.deepCopy();
    this.bestScore = initialOptimizerScore;

    job.logInfo(
        String.format(
            Locale.US,
            "Optimization stage started on board '%s'. Baseline router score: %.2f, "
                + "optimizer score: %.2f, incomplete connections: %d, "
                + "clearance violations: %d.",
            this.board.getHash(),
            initialRouterScore,
            initialOptimizerScore,
            initialIncomplete,
            initialViolations));

    // Capture start-of-session resource usage baselines
    long sessionStartMs = System.currentTimeMillis();
    final float cpuSecondsStart = sampleCurrentThreadCpuSeconds();
    final float allocMbStart = sampleCurrentThreadAllocatedMb();
    float peakHeapMb = sampleHeapUsageMb();
    workerCpuNanos.set(0);
    workerAllocBytes.set(0);

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

      float scoreBeforePass = board.getStatistics().getOptimizerScore(job.routerSettings);

      // Stop if potential improvement is less than threshold
      if (scoreBeforePass * (1 + this.settings.optimizer.optimizationImprovementThreshold)
          >= 1000.0f) {
        job.logInfo(
            String.format(
                Locale.US,
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

      BoardStatistics passStats = board.getStatistics();
      float scoreAfterPass = passStats.getOptimizerScore(job.routerSettings);
      if (scoreAfterPass > this.bestScore) {
        this.bestScore = scoreAfterPass;
        this.bestBoard = this.board.deepCopy();
      }

      double passImprovement =
          scoreBeforePass > 0 ? (double) (scoreAfterPass - scoreBeforePass) / scoreBeforePass : 0;
      String passOutcome =
          scoreAfterPass > scoreBeforePass
              ? "IMPROVED"
              : (scoreAfterPass < scoreBeforePass ? "REGRESSED" : "UNCHANGED");
      String passImprovementPercent =
          scoreBeforePass > 0
              ? String.format(Locale.US, "%.4f%%", passImprovement * 100)
              : "n/a (baseline was 0.00)";
      job.logInfo(
          String.format(
              Locale.US,
              "Optimizer pass #%d: optimizer score %.2f -> %.2f (%s, %s), router score: %.2f, "
                  + "incomplete connections: %d, clearance violations: %d.",
              currentPass,
              scoreBeforePass,
              scoreAfterPass,
              passOutcome,
              passImprovementPercent,
              passStats.getRouterScore(job.routerSettings),
              passStats.connections.incompleteCount,
              passStats.clearanceViolations.totalCount));

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
                Locale.US,
                "Stopping optimizer because the improvement in this pass (%.4f%%) is below "
                    + "the threshold (%.2f%%).",
                scoreImprovement * 100,
                this.settings.optimizer.optimizationImprovementThreshold * 100));
        break;
      }
    }

    this.currentPosition = null;

    // Restore best board achieved if final state regressed below best score
    float finalBoardScore = this.board.getStatistics().getOptimizerScore(job.routerSettings);
    if (finalBoardScore < this.bestScore && this.bestBoard != null) {
      job.logInfo(
          String.format(
              Locale.US,
              "Restoring best board achieved (score %.2f vs final %.2f).",
              this.bestScore,
              finalBoardScore));
      this.board = this.bestBoard;
      this.job.board = this.bestBoard;
      this.fireBoardUpdatedEvent(new BoardStatistics(this.board), null, this.board);
    }
    this.bestBoard = null;

    this.fireTaskStateChangedEvent(
        new TaskStateChangedEvent(this, TaskState.FINISHED, currentPass, this.board.getHash()));

    // Session summary
    double sessionDurationSeconds = (System.currentTimeMillis() - sessionStartMs) / 1000.0;
    float cpuSecondsEnd = sampleCurrentThreadCpuSeconds();
    float allocMbEnd = sampleCurrentThreadAllocatedMb();
    float cpuSecondsMain =
        cpuSecondsStart >= 0f && cpuSecondsEnd >= cpuSecondsStart
            ? cpuSecondsEnd - cpuSecondsStart
            : Math.max(0f, cpuSecondsEnd);
    float allocMbMain =
        allocMbStart >= 0f && allocMbEnd >= allocMbStart
            ? allocMbEnd - allocMbStart
            : Math.max(0f, allocMbEnd);
    float cpuSecondsUsed = cpuSecondsMain + (workerCpuNanos.get() / 1_000_000_000.0f);
    float allocMbUsed = allocMbMain + (workerAllocBytes.get() / (1024.0f * 1024.0f));
    peakHeapMb = Math.max(peakHeapMb, sampleHeapUsageMb());

    BoardStatistics finalStats = new BoardStatistics(this.board);
    float finalRouterScore = finalStats.getRouterScore(job.routerSettings);
    float finalOptimizerScore = finalStats.getOptimizerScore(job.routerSettings);
    String completionStatus =
        this.isTimedOut
            ? "completed with timeout:"
            : (this.thread.isStopRequested() ? "interrupted:" : "completed:");
    job.logInfo(
        String.format(
            Locale.US,
            "Optimization stage %s. Baseline router score: %.2f, baseline optimizer score: %.2f, "
                + "final router score: %.2f, final optimizer score: %.2f, completed in %.2f "
                + "seconds, using %.2f total CPU seconds, %.2f GB total allocated, and %.1f MB "
                + "peak heap usage.",
            completionStatus,
            initialRouterScore,
            initialOptimizerScore,
            finalRouterScore,
            finalOptimizerScore,
            sessionDurationSeconds,
            cpuSecondsUsed,
            allocMbUsed / 1024.0f,
            peakHeapMb));
  }

  private List<Integer> prepareCandidateItems() {
    List<Integer> itemIds = new ArrayList<>();
    this.sortedRouteItems = new ReadSortedRouteItems();

    ItemSelectionStrategy strategy =
        this.settings.optimizer != null ? this.settings.optimizer.itemSelectionStrategy : null;
    if (strategy == null) {
      strategy = ItemSelectionStrategy.SEQUENTIAL;
    }

    if (strategy == ItemSelectionStrategy.PRIORITIZED && !resultMap.isEmpty()) {
      List<Integer> nonPrioritized = new ArrayList<>();
      PriorityQueue<ItemRouteResult> pq = new PriorityQueue<>();

      for (Item item = sortedRouteItems.next(); item != null; item = sortedRouteItems.next()) {
        ItemRouteResult r = resultMap.get(item.getId());
        if (r != null) {
          pq.add(r);
        } else {
          nonPrioritized.add(item.getId());
        }
      }

      while (!pq.isEmpty()) {
        itemIds.add(pq.poll().itemId());
      }
      itemIds.addAll(nonPrioritized);
    } else {
      for (Item item = sortedRouteItems.next(); item != null; item = sortedRouteItems.next()) {
        itemIds.add(item.getId());
      }
    }

    this.sortedRouteItems = null;
    resultMap.clear();
    return itemIds;
  }

  /**
   * Tries to reduce the number of vias and the trace length of a completely routed board. Returns
   * the amount of improvements made in percentage (expressed between 0.0 and 1.0), or -1 if the
   * routing must go on no matter how much it improved.
   */
  protected float optRoutePass(int passNo, boolean withPreferredDirections) {
    BoardStatistics boardStatisticsBefore = board.getStatistics();
    RouterCounters routerCounters = new RouterCounters();
    routerCounters.passCount = passNo;
    progressThrottler.reset();
    this.fireBoardUpdatedEvent(boardStatisticsBefore, routerCounters, this.board);

    this.minCumulativeTraceLength = boardStatisticsBefore.traces.totalWeightedLength;
    List<Integer> candidateItemIds = prepareCandidateItems();

    if (this.settings.optimizer != null
        && this.settings.optimizer.maxItems != null
        && this.settings.optimizer.maxItems > 0) {
      int remaining = this.settings.optimizer.maxItems - this.totalItemsOptimized;
      if (remaining <= 0) {
        job.logInfo(
            "Max items limit reached ("
                + this.settings.optimizer.maxItems
                + "). Stopping optimizer.");
        return 0.0f;
      }
      if (candidateItemIds.size() > remaining) {
        candidateItemIds = new ArrayList<>(candidateItemIds.subList(0, remaining));
      }
    }

    if (candidateItemIds.isEmpty()) {
      return 0.0f;
    }

    int configuredThreads =
        (this.settings.optimizer != null && this.settings.optimizer.maxThreads != null)
            ? this.settings.optimizer.maxThreads
            : 1;
    int threadPoolSize = Math.max(1, configuredThreads);

    String optimizationPassId =
        "BatchOptRoute.opt_route_pass #"
            + passNo
            + " with "
            + candidateItemIds.size()
            + " items, "
            + boardStatisticsBefore.items.viaCount
            + " vias and "
            + "%(,.2f".formatted(boardStatisticsBefore.traces.totalLength)
            + " trace length running on "
            + threadPoolSize
            + " thread(s).";

    FRLogger.traceEntry(optimizationPassId);

    ExecutorService pool =
        Executors.newFixedThreadPool(
            threadPoolSize,
            r -> {
              Thread t = new Thread(r, "batch-optimizer-worker");
              t.setDaemon(true);
              t.setUncaughtExceptionHandler(
                  (t1, e) -> job.logError("Exception in thread pool worker thread: " + t1, e));
              return t;
            });

    CandidateResult winningCandidate = null;
    int chunkSize = Math.max(threadPoolSize * 4, 8);
    boolean stoppedOrTimedOut = false;
    int consecutiveFailures = 0;
    int maxConsecutiveFailures =
        this.settings.optimizer.maxConsecutiveFailures != null
            ? this.settings.optimizer.maxConsecutiveFailures
            : 50;

    try {
      for (int i = 0; i < candidateItemIds.size(); i += chunkSize) {
        if (this.deadlineMs != null && System.currentTimeMillis() >= this.deadlineMs) {
          job.logInfo("Optimizer stage timed out.");
          this.isTimedOut = true;
          stoppedOrTimedOut = true;
          break;
        }
        if (this.thread != null && this.thread.isStopRequested()) {
          stoppedOrTimedOut = true;
          break;
        }

        int end = Math.min(i + chunkSize, candidateItemIds.size());
        List<Integer> chunk = candidateItemIds.subList(i, end);
        List<Future<CandidateResult>> futures = new ArrayList<>(chunk.size());

        for (int itemId : chunk) {
          futures.add(
              pool.submit(
                  new OptimizeCandidateTask(
                      job,
                      this.board,
                      itemId,
                      this.minCumulativeTraceLength,
                      withPreferredDirections,
                      this.useIncreasedRipupCosts,
                      this.thread,
                      this.deadlineMs)));
        }

        for (Future<CandidateResult> future : futures) {
          try {
            CandidateResult res = future.get();
            this.totalItemsOptimized++;
            if (res != null) {
              this.workerCpuNanos.addAndGet(res.cpuUsedNanos);
              this.workerAllocBytes.addAndGet(res.allocUsedBytes);
              this.resultMap.put(res.result.itemId(), res.result);

              if (res.itemPosition != null) {
                this.currentPosition = res.itemPosition;
              }

              if (res.result.improved()) {
                consecutiveFailures = 0;
                if (winningCandidate == null || res.result.improvedOver(winningCandidate.result)) {
                  winningCandidate = res;
                }
              } else {
                consecutiveFailures++;
                if (consecutiveFailures >= maxConsecutiveFailures) {
                  job.logInfo(
                      String.format(
                          Locale.US,
                          "Stopping optimization pass #%d early after %d consecutive items could "
                              + "not be improved.",
                          passNo,
                          consecutiveFailures));
                  stoppedOrTimedOut = true;
                  break;
                }
              }
            }
          } catch (InterruptedException e) {
            stoppedOrTimedOut = true;
            Thread.currentThread().interrupt();
            break;
          } catch (ExecutionException e) {
            job.logError("Error in candidate optimization task", e.getCause());
          }
        }

        if (stoppedOrTimedOut) {
          break;
        }
      }
    } finally {
      pool.shutdownNow();
    }

    float routeImproved = 0.0f;

    if (!stoppedOrTimedOut && winningCandidate != null && winningCandidate.result.improved()) {
      this.board = winningCandidate.board;
      this.job.board = this.board;
      this.minCumulativeTraceLength = this.board.getStatistics().traces.totalWeightedLength;

      BoardStatistics boardStatisticsAfter = this.board.getStatistics();
      this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCounters, this.board);
      routeImproved = winningCandidate.result.improvementPercentage();
    }

    if (this.useIncreasedRipupCosts && (routeImproved == 0.0f)) {
      this.useIncreasedRipupCosts = false;
      routeImproved = -1.0f; // to keep the optimizer going with lower ripup costs
    }

    double routeoptimizerPassDuration = FRLogger.traceExit(optimizationPassId);
    BoardStatistics boardStatisticsAfter = new BoardStatistics(this.board);
    this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCounters, this.board);
    job.logInfo(
        String.format(
            Locale.US,
            "Optimizer pass #%d on board '%s' was completed in %.2f seconds with the score of %s.",
            passNo,
            this.board.getHash(),
            routeoptimizerPassDuration,
            FRLogger.formatScore(
                boardStatisticsAfter.getOptimizerScore(job.routerSettings),
                boardStatisticsAfter.connections.incompleteCount,
                boardStatisticsAfter.clearanceViolations.totalCount)));
    return routeImproved;
  }

  /**
   * Try to improve the route by re-routing the connections containing item.
   *
   * @param item the item to be re-routed
   * @param withPreferredDirections if true, the preferred directions are used for the traces
   */
  protected ItemRouteResult optRouteItem(Item item, boolean withPreferredDirections) {
    if (!(item.board instanceof RoutingBoard routingBoard)) {
      job.logWarning("The item to be optimized is not on a RoutingBoard.");
      return new ItemRouteResult(item.getId());
    }

    return optRouteItemOnBoard(
        job,
        routingBoard,
        item,
        this.minCumulativeTraceLength,
        withPreferredDirections,
        this.useIncreasedRipupCosts,
        this.thread,
        this.deadlineMs);
  }

  private static ItemRouteResult optRouteItemOnBoard(
      RoutingJob job,
      RoutingBoard routingBoard,
      Item item,
      double baselineTraceLength,
      boolean withPreferredDirections,
      boolean useIncreasedRipupCosts,
      StoppableThread thread,
      Long deadlineMs) {
    BoardStatistics boardStatisticsBefore = new BoardStatistics(routingBoard, null, false);
    RouterCounters routerCountersBefore = new RouterCounters();
    routerCountersBefore.incompleteCount = calculateIncompleteCount(routingBoard);

    Set<Item> rippedItems = new TreeSet<>();
    rippedItems.add(item);

    if (item instanceof Trace currentTrace) {
      Set<Item> currentContactList = currentTrace.getStartContacts();
      for (int i = 0; i < 2; i++) {
        if (containsOnlyUnfixedTraces(currentContactList)) {
          rippedItems.addAll(currentContactList);
        }
        currentContactList = currentTrace.getEndContacts();
      }
    }

    Set<Item> rippedConnections = new TreeSet<>();
    for (Item currentItem : rippedItems) {
      rippedConnections.addAll(currentItem.getConnectionItems(Item.StopConnectionOption.NONE));
    }

    for (Item currentItem : rippedConnections) {
      if (currentItem.isUserFixed()) {
        return new ItemRouteResult(item.getId());
      }
    }

    routingBoard.removeItems(rippedConnections);
    for (int i = 0; i < item.netCount(); i++) {
      routingBoard.combineTraces(item.getNetNumber(i));
    }

    int ripupCosts = job.routerSettings.getStartRipupCosts();
    if (useIncreasedRipupCosts && job.routerSettings.optimizer != null) {
      ripupCosts *= job.routerSettings.optimizer.additionalRipupCostFactorAtStart;
    }

    if (item instanceof Trace && job.routerSettings.optimizer != null) {
      ripupCosts =
          (int) Math.round(job.routerSettings.optimizer.traceRipupCostFactor * (double) ripupCosts);
    }

    int maxAutoroutePasses =
        job.routerSettings.optimizer != null ? job.routerSettings.optimizer.maxAutoroutePasses : 1;

    BatchAutorouter.autoroutePassesForOptimizingItem(
        job,
        maxAutoroutePasses,
        ripupCosts,
        job.routerSettings.tracePullTightAccuracy,
        withPreferredDirections,
        routingBoard,
        job.routerSettings);

    BoardStatistics boardStatisticsAfter = new BoardStatistics(routingBoard, null, false);
    RouterCounters routerCountersAfter = new RouterCounters();
    routerCountersAfter.incompleteCount = calculateIncompleteCount(routingBoard);

    ItemRouteResult result =
        new ItemRouteResult(
            item.getId(),
            boardStatisticsBefore.items.viaCount,
            boardStatisticsAfter.items.viaCount,
            baselineTraceLength,
            boardStatisticsAfter.traces.totalLength,
            routerCountersBefore.incompleteCount,
            routerCountersAfter.incompleteCount);
    boolean routeImproved =
        (thread == null || !thread.isStopRequested())
            && (deadlineMs == null || System.currentTimeMillis() < deadlineMs)
            && result.improved();
    result.updateImproved(routeImproved);
    return result;
  }

  private static FloatPoint getItemPosition(Item item) {
    if (item instanceof Via via) {
      return via.getCenter().toFloat();
    } else if (item instanceof Trace trace) {
      return trace.firstCorner().toFloat();
    }
    return null;
  }

  private static final class CandidateResult {
    final ItemRouteResult result;
    final RoutingBoard board;
    final FloatPoint itemPosition;
    final long cpuUsedNanos;
    final long allocUsedBytes;

    CandidateResult(
        ItemRouteResult result,
        RoutingBoard board,
        FloatPoint itemPosition,
        long cpuUsedNanos,
        long allocUsedBytes) {
      this.result = result;
      this.board = board;
      this.itemPosition = itemPosition;
      this.cpuUsedNanos = cpuUsedNanos;
      this.allocUsedBytes = allocUsedBytes;
    }
  }

  private static final class OptimizeCandidateTask implements Callable<CandidateResult> {
    private final RoutingJob job;
    private final RoutingBoard baselineBoard;
    private final int itemId;
    private final double baselineTraceLength;
    private final boolean withPreferredDirections;
    private final boolean useIncreasedRipupCosts;
    private final StoppableThread thread;
    private final Long deadlineMs;

    OptimizeCandidateTask(
        RoutingJob job,
        RoutingBoard baselineBoard,
        int itemId,
        double baselineTraceLength,
        boolean withPreferredDirections,
        boolean useIncreasedRipupCosts,
        StoppableThread thread,
        Long deadlineMs) {
      this.job = job;
      this.baselineBoard = baselineBoard;
      this.itemId = itemId;
      this.baselineTraceLength = baselineTraceLength;
      this.withPreferredDirections = withPreferredDirections;
      this.useIncreasedRipupCosts = useIncreasedRipupCosts;
      this.thread = thread;
      this.deadlineMs = deadlineMs;
    }

    @Override
    public CandidateResult call() {
      if ((thread != null && thread.isStopRequested())
          || (deadlineMs != null && System.currentTimeMillis() >= deadlineMs)) {
        return new CandidateResult(new ItemRouteResult(itemId), null, null, 0, 0);
      }

      long cpuStart = -1;
      long allocStart = -1;
      ThreadMXBean mxBean = null;
      try {
        mxBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        cpuStart = mxBean.getThreadCpuTime(Thread.currentThread().threadId());
        allocStart = mxBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
      } catch (Throwable ignored) {
      }

      RoutingBoard workerBoard = baselineBoard.deepCopy();
      Item item = workerBoard.getItem(itemId);
      if (item == null) {
        return new CandidateResult(new ItemRouteResult(itemId), null, null, 0, 0);
      }

      FloatPoint position = getItemPosition(item);

      ItemRouteResult result =
          optRouteItemOnBoard(
              job,
              workerBoard,
              item,
              baselineTraceLength,
              withPreferredDirections,
              useIncreasedRipupCosts,
              thread,
              deadlineMs);

      long cpuUsed = 0;
      long allocUsed = 0;
      if (mxBean != null) {
        try {
          long cpuEnd = mxBean.getThreadCpuTime(Thread.currentThread().threadId());
          if (cpuStart >= 0 && cpuEnd >= cpuStart) {
            cpuUsed = cpuEnd - cpuStart;
          }
          long allocEnd = mxBean.getThreadAllocatedBytes(Thread.currentThread().threadId());
          if (allocStart >= 0 && allocEnd >= allocStart) {
            allocUsed = allocEnd - allocStart;
          }
        } catch (Throwable ignored) {
        }
      }

      if (result.improved()) {
        return new CandidateResult(result, workerBoard, position, cpuUsed, allocUsed);
      } else {
        return new CandidateResult(result, null, position, cpuUsed, allocUsed);
      }
    }
  }

  /**
   * Returns the current position of the item, which will be rerouted or null, if the optimizer is
   * not active.
   */
  public FloatPoint getCurrentPosition() {
    return this.currentPosition;
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

  private static int calculateIncompleteCount(RoutingBoard board) {
    DesignRulesChecker tempDrc = new DesignRulesChecker(board, null);
    tempDrc.calculateAllIncompletes();
    return tempDrc.getIncompleteCount();
  }

  /** Reads the vias and traces on the board in ascending x order. */
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

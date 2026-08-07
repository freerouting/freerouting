package app.freerouting.autoroute;

import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
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
public class BatchOptimizer extends NamedAlgorithm {

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
   * @param job
   */
  public BatchOptimizer(RoutingJob job) {
    super(job.thread, job.board, job.routerSettings);
    this.job = job;
  }

  public boolean isTimedOut() {
    return this.isTimedOut;
  }

  static boolean contains_only_unfixed_traces(Collection<Item> p_item_list) {
    for (Item currItem : p_item_list) {
      if (currItem.is_user_fixed() || !(currItem instanceof Trace)) {
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
            + board.get_vias().size()
            + ", trace length: "
            + Math.round(board.cumulative_trace_length()));

    double scoreImprovement = -1;
    int currentPass = 0;
    useIncreasedRipupCosts = true;

    // Capture initial board state for session summary
    BoardStatistics initialStats = board.get_statistics();
    float initialScore = initialStats.getNormalizedScore(job.routerSettings.scoring);
    int initialIncomplete = initialStats.connections.incompleteCount;
    int initialViolations = initialStats.clearanceViolations.totalCount;

    job.logInfo(
        "Optimization stage started on board '"
            + this.board.get_hash()
            + "' with score "
            + FRLogger.formatScore(initialScore, initialIncomplete, initialViolations)
            + ".");

    // Capture start-of-session resource usage baselines
    long sessionStartMs = System.currentTimeMillis();
    float cpuSecondsStart = sampleCurrentThreadCpuSeconds();
    float allocMbStart = sampleCurrentThreadAllocatedMb();
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
        new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

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

      float scoreBeforePass = board.get_statistics().getNormalizedScore(job.routerSettings.scoring);

      // Stop if potential improvement is less than threshold
      if (scoreBeforePass * (1 + this.settings.optimizer.optimizationImprovementThreshold)
          >= 1000.0f) {
        job.logInfo(
            String.format(
                java.util.Locale.US,
                "Stopping optimizer because the current board score (%.2f) is already close to the maximum score (1000). Remaining potential improvement is less than the threshold (%.2f%%).",
                scoreBeforePass,
                this.settings.optimizer.optimizationImprovementThreshold * 100));
        break;
      }

      String currentBoardHash = this.board.get_hash();
      job.setCurrentPass(currentPass);
      this.fireTaskStateChangedEvent(
          new TaskStateChangedEvent(this, TaskState.RUNNING, currentPass, currentBoardHash));

      boolean withPreferredDirections = currentPass % 2 != 0; // to create more variations
      opt_route_pass(currentPass, withPreferredDirections);
      peakHeapMb = Math.max(peakHeapMb, sampleHeapUsageMb());

      if (this.isTimedOut) {
        break;
      }

      float scoreAfterPass = board.get_statistics().getNormalizedScore(job.routerSettings.scoring);
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
                "Stopping optimizer because the improvement in this pass (%.4f%%) is below the threshold (%.2f%%).",
                scoreImprovement * 100,
                this.settings.optimizer.optimizationImprovementThreshold * 100));
        break;
      }
    }

    this.fireTaskStateChangedEvent(
        new TaskStateChangedEvent(this, TaskState.FINISHED, currentPass, this.board.get_hash()));

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
            "Optimization stage %s started with score %s, completed in %.2f seconds, final score: %s, using %.2f total CPU seconds, %.2f GB total allocated, and %.1f MB peak heap usage.",
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
  protected float opt_route_pass(int p_pass_no, boolean p_with_preferred_directions) {
    float routeImproved = 0.0F;

    BoardStatistics boardStatisticsBefore = board.get_statistics();
    RouterCounters routerCounters = new RouterCounters();
    routerCounters.passCount = p_pass_no;
    progressThrottler.reset();
    this.fireBoardUpdatedEvent(boardStatisticsBefore, routerCounters, this.board);

    this.sortedRouteItems = new ReadSortedRouteItems();
    this.minCumulativeTraceLength = boardStatisticsBefore.traces.totalWeightedLength;
    String optimizationPassId =
        "BatchOptRoute.opt_route_pass #"
            + p_pass_no
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
      Item currItem = sortedRouteItems.next();
      if (currItem == null) {
        break;
      }
      ItemRouteResult result = opt_route_item(currItem, p_with_preferred_directions, false);
      this.totalItemsOptimized++;
      if (result.improved()) {
        consecutiveFailures = 0;
        if (progressThrottler.shouldUpdate()) {
          BoardStatistics boardStatisticsAfter = board.get_statistics();
          this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCounters, board);
        }

        routeImproved =
            (float)
                (boardStatisticsBefore.items.viaCount != 0
                        && boardStatisticsBefore.traces.totalLength != 0
                    ? 1.0
                        - ((((float) result.via_count() / boardStatisticsBefore.items.viaCount)
                                + (result.trace_length()
                                    / boardStatisticsBefore.traces.totalLength))
                            / 2)
                    : 0);
      } else {
        consecutiveFailures++;
        if (consecutiveFailures >= maxConsecutiveFailures) {
          job.logInfo(
              String.format(
                  java.util.Locale.US,
                  "Stopping optimization pass #%d early after %d consecutive items could not be improved.",
                  p_pass_no,
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
            p_pass_no,
            this.board.get_hash(),
            routeoptimizerPassDuration,
            FRLogger.formatScore(
                boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring),
                boardStatisticsAfter.connections.incompleteCount,
                boardStatisticsAfter.clearanceViolations.totalCount)));
    return routeImproved;
  }

  /**
   * Try to improve the route by re-routing the connections containing p_item.
   *
   * @param p_item the item to be re-routed
   * @param p_with_preferred_directions if true, the preferred directions are used for the traces
   * @param disableSnapshots if true, the snapshots are not used which means that the routing cannot
   *     be undone, but it's much more efficient
   */
  protected ItemRouteResult opt_route_item(
      Item p_item, boolean p_with_preferred_directions, boolean disableSnapshots) {
    // check if item.board is a RoutingBoard
    if (!(p_item.board instanceof RoutingBoard routingBoard)) {
      job.logWarning("The item to be optimized is not on a RoutingBoard.");
      return new ItemRouteResult(p_item.get_id_no());
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
    rippedItems.add(p_item);

    // add the contacts of the traces to the ripped items if it's a trace
    if (p_item instanceof Trace currTrace) {
      // add also the fork items, especially because not all fork items may be
      // returned by ReadSortedRouteItems because of matching end points.
      Set<Item> currContactList = currTrace.get_start_contacts();
      for (int i = 0; i < 2; i++) {
        if (contains_only_unfixed_traces(currContactList)) {
          rippedItems.addAll(currContactList);
        }
        currContactList = currTrace.get_end_contacts();
      }
    }

    Set<Item> rippedConnections = new TreeSet<>();
    // add all the connections of the items to be re-routed
    for (Item currItem : rippedItems) {
      rippedConnections.addAll(currItem.get_connection_items(Item.StopConnectionOption.NONE));
    }

    // check if the connections contain user fixed items, which should not be
    // re-routed
    for (Item currItem : rippedConnections) {
      if (currItem.is_user_fixed()) {
        return new ItemRouteResult(p_item.get_id_no());
      }
    }

    if (!disableSnapshots) {
      // make the current situation restorable by undo with the snapshot
      routingBoard.generate_snapshot();
    }

    // remove the items to be re-routed
    routingBoard.remove_items(rippedConnections);
    for (int i = 0; i < p_item.net_count(); i++) {
      routingBoard.combine_traces(p_item.get_net_no(i));
    }

    // calculate the ripup costs
    int ripupCosts = this.settings.get_start_ripup_costs();
    if (this.useIncreasedRipupCosts) {
      ripupCosts *= this.settings.optimizer.additionalRipupCostFactorAtStart;
    }

    // reduce the ripup costs for traces
    if (p_item instanceof Trace) {
      ripupCosts =
          (int) Math.round(this.settings.optimizer.traceRipupCostFactor * (double) ripupCosts);
    }

    // route the connections
    BatchAutorouter.autoroute_passes_for_optimizing_item(
        job,
        this.settings.optimizer.maxAutoroutePasses,
        ripupCosts,
        settings.tracePullTightAccuracy,
        p_with_preferred_directions,
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
            p_item.get_id_no(),
            boardStatisticsBefore.items.viaCount,
            boardStatisticsAfter.items.viaCount,
            this.minCumulativeTraceLength,
            boardStatisticsAfter.traces.totalLength,
            routerCountersBefore.incompleteCount,
            routerCountersAfter.incompleteCount);
    boolean routeImproved = !this.thread.isStopRequested() && result.improved();
    result.update_improved(routeImproved);

    if (routeImproved) {
      this.minCumulativeTraceLength =
          Math.min(this.minCumulativeTraceLength, boardStatisticsAfter.traces.totalWeightedLength);

      if (!disableSnapshots) {
        // this was a successful routing, so the snapshot can be removed
        routingBoard.pop_snapshot();
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
  public FloatPoint get_current_position() {
    if (sortedRouteItems == null) {
      return null;
    }
    return sortedRouteItems.get_current_position();
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
      FloatPoint currMinCoor = new FloatPoint(Integer.MAX_VALUE, Integer.MAX_VALUE);
      int currMinLayer = Integer.MAX_VALUE;
      Iterator<UndoableObjects.UndoableObjectNode> it = board.itemList.start_read_object();
      for (; ; ) {
        UndoableObjects.Storable currItem = board.itemList.read_object(it);
        if (currItem == null) {
          break;
        }
        if (currItem instanceof Via currVia) {
          if (!currVia.is_user_fixed()) {
            FloatPoint currViaCenter = currVia.get_center().to_float();
            int currViaMinLayer = currVia.first_layer();
            if (currViaCenter.x > minItemCoor.x
                || currViaCenter.x == minItemCoor.x
                    && (currViaCenter.y > minItemCoor.y
                        || currViaCenter.y == minItemCoor.y && currViaMinLayer > minItemLayer)) {
              if (currViaCenter.x < currMinCoor.x
                  || currViaCenter.x == currMinCoor.x
                      && (currViaCenter.y < currMinCoor.y
                          || currViaCenter.y == currMinCoor.y && currViaMinLayer < currMinLayer)) {
                currMinCoor = currViaCenter;
                currMinLayer = currViaMinLayer;
                result = currVia;
              }
            }
          }
        }
      }
      // Read traces last to prefer vias to traces at the same location
      it = board.itemList.start_read_object();
      for (; ; ) {
        UndoableObjects.Storable currItem = board.itemList.read_object(it);
        if (currItem == null) {
          break;
        }
        if (currItem instanceof Trace currTrace) {
          if (!currTrace.is_shove_fixed()) {
            FloatPoint firstCorner = currTrace.first_corner().to_float();
            FloatPoint lastCorner = currTrace.last_corner().to_float();
            FloatPoint compareCorner;
            if (firstCorner.x < lastCorner.x
                || firstCorner.x == lastCorner.x && firstCorner.y < lastCorner.y) {
              compareCorner = lastCorner;
            } else {
              compareCorner = firstCorner;
            }
            int currTraceLayer = currTrace.get_layer();
            if (compareCorner.x > minItemCoor.x
                || compareCorner.x == minItemCoor.x
                    && (compareCorner.y > minItemCoor.y
                        || compareCorner.y == minItemCoor.y && currTraceLayer > minItemLayer)) {
              if (compareCorner.x < currMinCoor.x
                  || compareCorner.x == currMinCoor.x
                      && (compareCorner.y < currMinCoor.y
                          || compareCorner.y == currMinCoor.y && currTraceLayer < currMinLayer)) {
                boolean isConnectedToVia = false;
                Set<Item> traceContacts = currTrace.get_normal_contacts();
                for (Item currContact : traceContacts) {
                  if (currContact instanceof Via && !currContact.is_user_fixed()) {
                    isConnectedToVia = true;
                    break;
                  }
                }
                if (!isConnectedToVia) {
                  currMinCoor = compareCorner;
                  currMinLayer = currTraceLayer;
                  result = currTrace;
                }
              }
            }
          }
        }
      }
      minItemCoor = currMinCoor;
      minItemLayer = currMinLayer;
      return result;
    }

    FloatPoint get_current_position() {
      return minItemCoor;
    }
  }
}

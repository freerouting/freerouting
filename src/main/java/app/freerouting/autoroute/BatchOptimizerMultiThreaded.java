package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.logger.FRLogger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Optimizes routes using multiple threads on a board that has completed auto-routing. */
public class BatchOptimizerMultiThreaded extends BatchOptimizer {

  private final BoardUpdateStrategy boardUpdateStrategy;
  private final ItemSelectionStrategy itemSelectionStrategy;
  private final int threadPoolSize;
  private final ArrayList<Integer> itemIds = new ArrayList<>();
  private final HashMap<Integer, ItemRouteResult> resultMap = new HashMap<>();
  private final ArrayList<BoardUpdateStrategy> hybridList = new ArrayList<>();
  private ThreadPoolExecutor pool;
  private ItemRouteResult bestRouteResult;
  private OptimizeRouteTask winningCandidate;
  private int numTasksFinished;
  private int updateCount;
  private CountDownLatch taskCompletionSignal = new CountDownLatch(1);
  private int hybridIndex = -1;

  /** Constructs a multi-threaded batch optimizer for the given routing job. */
  public BatchOptimizerMultiThreaded(RoutingJob job) {
    super(job);

    this.threadPoolSize = job.routerSettings.optimizer.maxThreads;
    this.boardUpdateStrategy = job.routerSettings.optimizer.boardUpdateStrategy;
    this.itemSelectionStrategy =
        job.routerSettings.optimizer.boardUpdateStrategy == BoardUpdateStrategy.GLOBAL_OPTIMAL
            ? ItemSelectionStrategy.SEQUENTIAL
            : job.routerSettings.optimizer.itemSelectionStrategy;

    bestRouteResult = new ItemRouteResult(-1);
    winningCandidate = null;

    if (this.boardUpdateStrategy == BoardUpdateStrategy.HYBRID) {
      int numOptimal = 1;
      int numPrioritized = 1;

      if (job.routerSettings.optimizer.hybridRatio != null
          && job.routerSettings.optimizer.hybridRatio.indexOf(":") >= 1) {
        String[] ratio = job.routerSettings.optimizer.hybridRatio.split(":");

        try {
          numOptimal = Integer.parseInt(ratio[0], 10);
          numPrioritized = Integer.parseInt(ratio[1], 10);
        } catch (NumberFormatException e) {
          job.logError("Invalid hybrid ratio", e);
          numOptimal = 1;
          numPrioritized = 1;
        }

        for (int i = 0; i < numOptimal; i++) {
          hybridList.add(BoardUpdateStrategy.GLOBAL_OPTIMAL);
        }

        for (int i = 0; i < numPrioritized; i++) {
          hybridList.add(BoardUpdateStrategy.GREEDY);
        }
      }
    }
  }

  /** Returns the total number of tasks in the current optimization pass. */
  public int getNumTasks() {
    return itemIds.size();
  }

  /** Returns the number of tasks completed so far. */
  public int getNumTasksFinished() {
    return numTasksFinished;
  }

  private BoardUpdateStrategy currentBoardUpdateStrategy() {
    if (this.boardUpdateStrategy == BoardUpdateStrategy.HYBRID) {
      return hybridList.get(hybridIndex);
    }

    return this.boardUpdateStrategy;
  }

  private ItemSelectionStrategy currentItemSelectionStrategy() {
    return currentBoardUpdateStrategy() == BoardUpdateStrategy.GLOBAL_OPTIMAL
        ? ItemSelectionStrategy.SEQUENTIAL
        : this.itemSelectionStrategy;
  }

  synchronized void prepareTaskCompletionSignal() {
    if (taskCompletionSignal.getCount() <= 0) {
      taskCompletionSignal = new CountDownLatch(1);
      // no other way to increase the count for repeated use
      // It's still simpler than general wait/notify
    }
  }

  /** Checks if task is the current best winning candidate in this pass. */
  public synchronized boolean isWinningCandidate(OptimizeRouteTask task) {
    ++numTasksFinished;

    ItemRouteResult r = task.getRouteResult();

    resultMap.put(r.itemId(), r);

    boolean won = false;

    if (r.improved()) {
      if (winningCandidate == null) {
        won = true;
        winningCandidate = task;
        bestRouteResult = r;

      } else {
        if (r.improvedOver(bestRouteResult)) {
          won = true;

          winningCandidate.clean();

          winningCandidate = task;
          bestRouteResult = r;
        }
      }
    }

    if (won && currentBoardUpdateStrategy() == BoardUpdateStrategy.GREEDY) {
      replaceMasterRoutingBoardWithTheWinningCandidate(); // new tasks will copy the updated board
    }

    taskCompletionSignal.countDown();
    return won;
  }

  private void replaceMasterRoutingBoardWithTheWinningCandidate() {
    this.board = winningCandidate.board;

    BoardStatistics boardStatistics = this.board.getStatistics();
    this.fireBoardUpdatedEvent(boardStatistics, null, this.board);

    this.minCumulativeTraceLength = boardStatistics.traces.totalWeightedLength;

    ++updateCount;
  }

  private void prepareNextRoundOfRouteItems() {
    if (this.boardUpdateStrategy == BoardUpdateStrategy.HYBRID) {
      hybridIndex = (hybridIndex + 1) % hybridList.size();
    }

    itemIds.clear();

    this.sortedRouteItems = new ReadSortedRouteItems();

    if (currentItemSelectionStrategy() == ItemSelectionStrategy.PRIORITIZED
        && !resultMap.isEmpty()) {
      ArrayList<Integer> newItemIds = new ArrayList<>();
      PriorityQueue<ItemRouteResult> pq = new PriorityQueue<>();

      for (Item item = sortedRouteItems.next(); item != null; item = sortedRouteItems.next()) {
        ItemRouteResult r = resultMap.get(item.getId());
        if (r != null) { // use PriorityQueue to sort item according to route result
          pq.add(r);
        } else {
          newItemIds.add(item.getId());
        }
      }

      for (ItemRouteResult r = pq.poll(); r != null; r = pq.poll()) {
        itemIds.add(r.itemId());
      }

      itemIds.addAll(newItemIds);
    } else {
      for (Item item = sortedRouteItems.next(); item != null; item = sortedRouteItems.next()) {
        itemIds.add(item.getId());
      }

      if (currentItemSelectionStrategy() == ItemSelectionStrategy.RANDOM) {
        Collections.shuffle(itemIds);
      }
    }

    this.sortedRouteItems = null;
    resultMap.clear();
  }

  @Override
  protected float optRoutePass(int passNo, boolean withPreferredDirections) {
    final long startTime = System.currentTimeMillis();
    updateCount = 0;
    numTasksFinished = 0;

    if (winningCandidate != null) {
      winningCandidate.clean();
      winningCandidate = null;
    }

    BoardStatistics boardStatisticsBefore = board.getStatistics();
    RouterCounters routerCounters = new RouterCounters();
    routerCounters.passCount = passNo;
    this.fireBoardUpdatedEvent(boardStatisticsBefore, routerCounters, this.board);

    this.minCumulativeTraceLength = boardStatisticsBefore.traces.totalWeightedLength;

    String optimizationPassId =
        "BatchOptRouteMT.opt_route_pass #"
            + passNo
            + " with "
            + itemIds.size()
            + " items, "
            + boardStatisticsBefore.items.viaCount
            + " vias and "
            + "%(,.2f".formatted(boardStatisticsBefore.traces.totalLength)
            + " trace length running on "
            + threadPoolSize
            + " threads.";
    FRLogger.traceEntry(optimizationPassId);

    prepareNextRoundOfRouteItems();

    bestRouteResult = new ItemRouteResult(-1);
    winningCandidate = null;

    pool =
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                threadPoolSize,
                r -> {
                  Thread t = new Thread(r);
                  t.setUncaughtExceptionHandler(
                      (t1, e) -> job.logError("Exception in thread pool worker thread: " + t1, e));
                  return t;
                });

    // One new optimizer task is initialized for each item to be re-rerouted, and we keep the best
    // result in the end
    for (int t = 0; t < itemIds.size(); t++) {
      int itemId = itemIds.get(t);
      job.logDebug(
          "Scheduling task #" + (t + 1) + " of " + itemIds.size() + " for item #" + itemId + ".");

      // We schedule just enough tasks to keep workers busy in order not to exhaust JVM memory so
      // that it can run on systems without huge amount of RAM using the pool
      OptimizeRouteTask newTask =
          new OptimizeRouteTask(this, this.job, itemId, passNo, withPreferredDirections);
      pool.execute(newTask);
    }

    job.logDebug("All items are queued for execution, waiting for the tasks to finish.");
    pool.shutdown();

    boolean interrupted = false;

    try {
      int i = 0;
      while (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
        job.logDebug(
            "Running route optimizer on "
                + pool.getActiveCount()
                + " thread(s). Completed "
                + pool.getCompletedTaskCount()
                + " of "
                + pool.getTaskCount()
                + " tasks.");

        if (this.thread.isStopRequested()) {
          pool.shutdownNow();
          return bestRouteResult.improvementPercentage();
        }
      }
    } catch (InterruptedException ie) {
      job.logError("Exception with pool.awaitTermination", ie);

      interrupted = true;
      pool.shutdownNow();

      // Thread.currentThread().interrupt(); // Preserve interrupt status
    }

    pool = null;

    if (!interrupted
        && bestRouteResult.improved()
        && currentBoardUpdateStrategy() == BoardUpdateStrategy.GLOBAL_OPTIMAL) {
      replaceMasterRoutingBoardWithTheWinningCandidate();
    }

    float routeImproved = bestRouteResult.improvementPercentage();

    if (this.useIncreasedRipupCosts && !bestRouteResult.improved()) {
      this.useIncreasedRipupCosts = false;
      routeImproved = -1; // to keep the optimizer going with lower ripup costs
    }

    long duration = System.currentTimeMillis() - startTime;
    long minutes = duration / 60000;
    float sec = (duration % 60000) / 1000.0F;

    String us =
        currentBoardUpdateStrategy() == BoardUpdateStrategy.GLOBAL_OPTIMAL
            ? "Global Optimal"
            : "Greedy";
    String is =
        currentItemSelectionStrategy() == ItemSelectionStrategy.SEQUENTIAL
            ? "Sequential"
            : (currentItemSelectionStrategy() == ItemSelectionStrategy.RANDOM
                ? "Random"
                : "Prioritized");

    BoardStatistics boardStatisticsAfter = board.getStatistics();
    this.fireBoardUpdatedEvent(boardStatisticsAfter, routerCounters, this.board);

    job.logDebug(
        "Finished optimizer pass #"
            + passNo
            + " in "
            + minutes
            + " minutes "
            + sec
            + " seconds with "
            + updateCount
            + " board updates using "
            + threadPoolSize
            + " thread(s) with '"
            + us
            + "' strategy and '"
            + is
            + "' item selection strategy.");
    job.logDebug(
        "Route optimizer pass summary - Improved: "
            + bestRouteResult.improved()
            + ", interrupted: "
            + interrupted
            + ", via count: "
            + bestRouteResult.viaCount()
            + ", trace length: "
            + boardStatisticsAfter.traces.totalLength
            + ", via count delta: "
            + (boardStatisticsBefore.items.viaCount - bestRouteResult.viaCount())
            + ", trace length delta: "
            + (boardStatisticsBefore.traces.totalLength - boardStatisticsAfter.traces.totalLength)
            + ".");

    FRLogger.traceExit(optimizationPassId);

    return routeImproved;
  }

  public double getWinningCandidateScore() {
    return this.board.getStatistics().traces.totalLength;
  }
}

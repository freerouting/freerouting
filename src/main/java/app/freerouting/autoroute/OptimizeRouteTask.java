package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.logger.FRLogger;

/** Task for optimizing a single route item in a multi-threaded routing pass. */
public class OptimizeRouteTask implements Runnable {

  public final RoutingBoard board;
  private final BatchOptimizerMultiThreaded optimizer;
  private final int passNo;
  private final boolean withPreferredDirections;
  private final RoutingJob job;
  private Item itemToOptimize;
  private ItemRouteResult optimizationResult;

  /** Constructs an OptimizeRouteTask for optimizing the specified item. */
  public OptimizeRouteTask(
      BatchOptimizerMultiThreaded optimizer,
      RoutingJob job,
      int itemId,
      int passNo,
      boolean withPreferredDirections) {
    this.optimizer = optimizer;

    this.job = job;
    this.board = job.board.deepCopy();
    itemToOptimize = this.board.getItem(itemId);

    this.passNo = passNo;
    this.withPreferredDirections = withPreferredDirections;
  }

  @Override
  public void run() {
    long startTime = System.currentTimeMillis();

    if (itemToOptimize == null) {
      return;
    }

    BatchOptimizer workerOptimizer = new BatchOptimizer(this.job);
    workerOptimizer.minCumulativeTraceLength =
        new BoardStatistics(this.board, null, false).traces.totalWeightedLength;
    optimizationResult =
        workerOptimizer.optRouteItem(itemToOptimize, withPreferredDirections, true);

    boolean winningCandidate = optimizer.isWinningCandidate(this);

    long duration = System.currentTimeMillis() - startTime;
    long minutes = duration / 60000;
    float sec = (duration % 60000) / 1000.0F;

    FRLogger.debug(
        "Finished   task #"
            + optimizer.getNumTasksFinished()
            + " of "
            + optimizer.getNumTasks()
            + " for item #"
            + itemToOptimize.getIdNo()
            + " on pass "
            + passNo
            + " in "
            + minutes
            + " m "
            + sec
            + "s."
            + " Best so far: "
            + winningCandidate
            + ", improved: "
            + optimizationResult.improved()
            + ", via reduction: "
            + optimizationResult.viaCountReduced()
            + (winningCandidate
                ? (", length reduction: " + (int) optimizationResult.lengthReduced())
                : "")
            + ", incomplete trace reduction: "
            + (optimizationResult.incompleteCountBefore() - optimizationResult.incompleteCount()));

    if (!winningCandidate) {
      clean();
    }
  }

  /** Returns the optimization result of this task. */
  public ItemRouteResult getRouteResult() {
    return this.optimizationResult;
  }

  /** Returns the item being optimized. */
  public Item getItem() {
    return itemToOptimize;
  }

  /** Cleans up resources to release memory quickly. */
  public void clean() { // try to speed up memory release
    itemToOptimize.board = null;
    itemToOptimize = null;
  }
}

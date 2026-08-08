package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;

public class OptimizeRouteTask implements Runnable {

  public final RoutingBoard board;
  private final BatchOptimizerMultiThreaded optimizer;
  private final int passNo;
  private final boolean withPreferredDirections;
  private final RoutingJob job;
  private Item itemToOptimize;
  private ItemRouteResult optimizationResult;

  public OptimizeRouteTask(
      BatchOptimizerMultiThreaded p_optimizer,
      RoutingJob job,
      int itemId,
      int p_pass_no,
      boolean p_with_preferred_directions) {
    optimizer = p_optimizer;

    this.job = job;
    this.board = job.board.deepCopy();
    itemToOptimize = this.board.getItem(itemId);

    passNo = p_pass_no;
    withPreferredDirections = p_with_preferred_directions;
  }

  @Override
  public void run() {
    long startTime = System.currentTimeMillis();

    if (itemToOptimize == null) {
      return;
    }

    optimizationResult =
        new BatchOptimizer(this.job).optRouteItem(itemToOptimize, withPreferredDirections, true);

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
            + (optimizationResult.incompleteCountBefore()
                - optimizationResult.incompleteCount()));

    if (!winningCandidate) {
      clean();
    }
  }

  public ItemRouteResult getRouteResult() {
    return this.optimizationResult;
  }

  public Item getItem() {
    return itemToOptimize;
  }

  public void clean() { // try to speed up memory release
    itemToOptimize.board = null;
    itemToOptimize = null;
  }
}

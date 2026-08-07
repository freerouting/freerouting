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
    itemToOptimize = this.board.get_item(itemId);

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
        new BatchOptimizer(this.job).opt_route_item(itemToOptimize, withPreferredDirections, true);

    boolean winningCandidate = optimizer.is_winning_candidate(this);

    long duration = System.currentTimeMillis() - startTime;
    long minutes = duration / 60000;
    float sec = (duration % 60000) / 1000.0F;

    FRLogger.debug(
        "Finished   task #"
            + optimizer.get_num_tasks_finished()
            + " of "
            + optimizer.get_num_tasks()
            + " for item #"
            + itemToOptimize.get_id_no()
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
            + optimizationResult.via_count_reduced()
            + (winningCandidate
                ? (", length reduction: " + (int) optimizationResult.length_reduced())
                : "")
            + ", incomplete trace reduction: "
            + (optimizationResult.incomplete_count_before()
                - optimizationResult.incomplete_count()));

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

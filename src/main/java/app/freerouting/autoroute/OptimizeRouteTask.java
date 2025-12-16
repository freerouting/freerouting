package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;

public class OptimizeRouteTask implements Runnable {

  public final RoutingBoard board;
  private final BatchOptimizerMultiThreaded optimizer;
  private final int pass_no;
  private final boolean with_preferred_directions;
  private final RoutingJob job;
  private Item itemToOptimize;
  private ItemRouteResult optimizationResult;

  public OptimizeRouteTask(BatchOptimizerMultiThreaded p_optimizer, RoutingJob job, int item_id, int p_pass_no, boolean p_with_preferred_directions) {
    optimizer = p_optimizer;

    this.job = job;
    this.board = job.board.deepCopy();
    itemToOptimize = this.board.get_item(item_id);

    pass_no = p_pass_no;
    with_preferred_directions = p_with_preferred_directions;
  }

  @Override
  public void run() {
    long startTime = System.currentTimeMillis();

    if (itemToOptimize == null) {
      return;
    }

    optimizationResult = new BatchOptimizer(this.job).opt_route_item(itemToOptimize, with_preferred_directions, true);

    boolean winning_candidate = optimizer.is_winning_candidate(this);

    long duration = System.currentTimeMillis() - startTime;
    long minutes = duration / 60000;
    float sec = (duration % 60000) / 1000.0F;

    FRLogger.debug(
        "Finished   task #" + optimizer.get_num_tasks_finished() + " of " + optimizer.get_num_tasks() + " for item #" + itemToOptimize.get_id_no() + " on pass " + pass_no + " in " + minutes + " m "
            + sec + "s." + " Best so far: " + winning_candidate + ", improved: " + optimizationResult.improved() + ", via reduction: " + optimizationResult.via_count_reduced() + (winning_candidate ? (
            ", length reduction: " + (int) optimizationResult.length_reduced()) : "") + ", incomplete trace reduction: " + (optimizationResult.incomplete_count_before()
            - optimizationResult.incomplete_count()));

    if (!winning_candidate) {
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
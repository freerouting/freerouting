package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.interactive.InteractiveActionThread;
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
public class BatchOptRouteMT extends BatchOptRoute {
  private final BoardUpdateStrategy board_update_strategy;
  private final ItemSelectionStrategy item_selection_strategy;
  private ThreadPoolExecutor pool;
  private ItemRouteResult best_route_result;
  private OptimizeRouteTask winning_candidate;
  private final int thread_pool_size;
  private int num_tasks_finished = 0;
  private int update_count = 0;
  private final ArrayList<Integer> item_ids = new ArrayList<Integer>();
  private final HashMap<Integer, ItemRouteResult> result_map = new HashMap<Integer, ItemRouteResult>();
  private CountDownLatch task_completion_signal = new CountDownLatch(1);
  private final ArrayList<BoardUpdateStrategy> hybrid_list = new ArrayList<BoardUpdateStrategy>();
  private int hybrid_index = -1;

  /**
   * @param p_thread
   */
  public BatchOptRouteMT(
      InteractiveActionThread p_thread,
      int p_thread_pool_size,
      BoardUpdateStrategy p_board_update_strategy,
      ItemSelectionStrategy p_item_selection_strategy,
      String p_hrid_ratio) {
    super(p_thread);

    this.thread_pool_size = p_thread_pool_size;
    this.board_update_strategy = p_board_update_strategy;
    this.item_selection_strategy =
        p_board_update_strategy == BoardUpdateStrategy.GLOBAL_OPTIMAL
            ? ItemSelectionStrategy.SEQUENTIAL
            : p_item_selection_strategy;

    best_route_result = new ItemRouteResult(-1);
    winning_candidate = null;

    if (this.board_update_strategy == BoardUpdateStrategy.HYBRID) {
      int num_optimal = 1, num_prioritized = 1;

      if (p_hrid_ratio != null && p_hrid_ratio.indexOf(":") > 0) {
        String[] ratio = p_hrid_ratio.split(":");

        try {
          num_optimal = Integer.parseInt(ratio[0], 10);
          num_prioritized = Integer.parseInt(ratio[1], 10);
        } catch (NumberFormatException e) {
          FRLogger.error("Invalid hybrid ratio", e);
          num_optimal = 1;
          num_prioritized = 1;
        }

        for (int i = 0; i < num_optimal; ++i) {
          hybrid_list.add(BoardUpdateStrategy.GLOBAL_OPTIMAL);
        }

        for (int i = 0; i < num_prioritized; ++i) {
          hybrid_list.add(BoardUpdateStrategy.GREEDY);
        }
      }
    }

    hybrid_index = -1;
  }

  public int get_num_tasks() {
    return item_ids.size();
  }

  public int get_num_tasks_finished() {
    return num_tasks_finished;
  }

  private BoardUpdateStrategy current_board_update_strategy() {
    if (this.board_update_strategy == BoardUpdateStrategy.HYBRID) {
      return hybrid_list.get(hybrid_index);
    }

    return this.board_update_strategy;
  }

  private ItemSelectionStrategy current_item_selection_strategy() {
    return current_board_update_strategy() == BoardUpdateStrategy.GLOBAL_OPTIMAL
        ? ItemSelectionStrategy.SEQUENTIAL
        : this.item_selection_strategy;
  }

  synchronized void prepare_task_completion_signal() {
    if (task_completion_signal.getCount() <= 0) {
      task_completion_signal = new CountDownLatch(1);
      // no other way to increase the count for repeated use
      // It's still simpler than general wait/notify
    }
  }

  public synchronized boolean is_winning_candidate(OptimizeRouteTask task) {
    ++num_tasks_finished;

    ItemRouteResult r = task.getRouteResult();

    result_map.put(r.item_id(), r);

    boolean won = false;

    if (r.improved()) {
      if (winning_candidate == null) {
        won = true;
        winning_candidate = task;
        best_route_result = r;

      } else {
        if (r.improved_over(best_route_result)) {
          won = true;

          winning_candidate.clean();

          winning_candidate = task;
          best_route_result = r;
        }
      }
    }

    if (won && current_board_update_strategy() == BoardUpdateStrategy.GREEDY) {
      update_master_routing_board(); // new tasks will copy the updated board
    }

    task_completion_signal.countDown();
    return won;
  }

  private void update_master_routing_board() {
    this.thread.hdlg.update_routing_board(winning_candidate.routing_board);
    this.routing_board = this.thread.hdlg.get_routing_board();

    this.min_cumulative_trace_length_before = calc_weighted_trace_length(this.routing_board);

    double new_trace_length =
        this.thread.hdlg.coordinate_transform.board_to_user(
            this.routing_board.cumulative_trace_length());
    this.thread.hdlg.screen_messages.set_post_route_info(
        this.routing_board.get_vias().size(), new_trace_length);

    ++update_count;
  }

  private void prepare_next_round_of_route_items() {
    if (this.board_update_strategy == BoardUpdateStrategy.HYBRID) {
      hybrid_index = (hybrid_index + 1) % hybrid_list.size();
    }

    item_ids.clear();

    this.sorted_route_items = new ReadSortedRouteItems();

    if (current_item_selection_strategy() == ItemSelectionStrategy.PRIORITIZED
        && result_map.size() > 0) {
      ArrayList<Integer> new_item_ids = new ArrayList<Integer>();
      PriorityQueue<ItemRouteResult> pq = new PriorityQueue<ItemRouteResult>();

      for (Item item = sorted_route_items.next(); item != null; item = sorted_route_items.next()) {
        ItemRouteResult r = result_map.get(item.get_id_no());
        if (r != null) { // use PriorityQueue to sort item according to route result
          pq.add(r);
        } else {
          new_item_ids.add(item.get_id_no());
        }
      }

      for (ItemRouteResult r = pq.poll(); r != null; r = pq.poll()) {
        item_ids.add(r.item_id());
      }

      item_ids.addAll(new_item_ids);
    } else {
      for (Item item = sorted_route_items.next(); item != null; item = sorted_route_items.next()) {
        item_ids.add(item.get_id_no());
      }

      if (current_item_selection_strategy() == ItemSelectionStrategy.RANDOM) {
        Collections.shuffle(item_ids);
      }
    }

    this.sorted_route_items = null;
    result_map.clear();
  }

  @Override
  protected float opt_route_pass(int p_pass_no, boolean p_with_prefered_directions) {
    long startTime = System.currentTimeMillis();
    update_count = 0;
    num_tasks_finished = 0;

    if (winning_candidate != null) {
      winning_candidate.clean();
      winning_candidate = null;
    }

    float route_improved = 0.0f;
    int via_count_before = this.routing_board.get_vias().size();
    double user_trace_length_before =
        this.thread.hdlg.coordinate_transform.board_to_user(
            this.routing_board.cumulative_trace_length());
    this.thread.hdlg.screen_messages.set_post_route_info(
        via_count_before, user_trace_length_before);
    this.min_cumulative_trace_length_before = calc_weighted_trace_length(routing_board);
    // double pass_trace_length_before = this.min_cumulative_trace_length_before;
    String optimizationPassId =
        "BatchOptRouteMT.opt_route_pass #"
            + p_pass_no
            + " with "
            + item_ids.size()
            + " items, "
            + via_count_before
            + " vias and "
            + String.format("%(,.2f", user_trace_length_before)
            + " trace length running on "
            + thread_pool_size
            + " threads.";
    FRLogger.traceEntry(optimizationPassId);

    prepare_next_round_of_route_items();

    best_route_result = new ItemRouteResult(-1);
    winning_candidate = null;

    pool =
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                thread_pool_size,
                r -> {
                  Thread t = new Thread(r);
                  t.setUncaughtExceptionHandler(
                      (t1, e) -> {
                        FRLogger.error(
                            "Exception in thread pool worker thread: " + t1.toString(), e);
                      });
                  return t;
                });

    for (int t = 0; t < item_ids.size(); t++) {
      // each task needs a copy of routing_board, so schedule just enough tasks
      // to keep workers busy in order not to exhaust JVM memory so that
      // it can run on systems without huge amount of RAM
        int item_id = item_ids.get(t);
        FRLogger.debug(
            "Scheduling task #"
                + (t+1)
                + " of "
                + item_ids.size()
                + " for item #"
                + item_id
                + ".");

        pool.execute(
            new OptimizeRouteTask(
                this,
                item_id,
                p_pass_no,
                p_with_prefered_directions,
                this.min_cumulative_trace_length_before));
    }

    FRLogger.debug("All items are queued for execution, waiting for the tasks to finish.");
    pool.shutdown();

    boolean interrupted = false;

    try {
      int i = 0;
      while (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
        FRLogger.debug(
            "Running route optimizer on "
                + pool.getActiveCount()
                + " thread(s). Completed "
                + pool.getCompletedTaskCount()
                + " of "
                + pool.getTaskCount()
                + " tasks.");

        if (this.thread.is_stop_requested()) {
          pool.shutdownNow();
          return best_route_result.improvement_percentage();
        }
      }
    } catch (InterruptedException ie) {
      FRLogger.error("Exception with pool.awaitTermination", ie);

      interrupted = true;
      pool.shutdownNow();

      // Thread.currentThread().interrupt(); // Preserve interrupt status
    }

    pool = null;

    route_improved = best_route_result.improvement_percentage();

    if (!interrupted
        && best_route_result.improved()
        && current_board_update_strategy() == BoardUpdateStrategy.GLOBAL_OPTIMAL) {
      update_master_routing_board();
    }

    if (this.use_increased_ripup_costs && !best_route_result.improved()) {
      this.use_increased_ripup_costs = false;
      route_improved = -1; // to keep the optimizer going with lower ripup costs
    }

    long duration = System.currentTimeMillis() - startTime;
    long minutes = duration / 60000;
    float sec = (duration % 60000) / 1000.0F;

    String us =
        current_board_update_strategy() == BoardUpdateStrategy.GLOBAL_OPTIMAL
            ? "Global Optimal"
            : "Greedy";
    String is =
        current_item_selection_strategy() == ItemSelectionStrategy.SEQUENTIAL
            ? "Sequential"
            : (current_item_selection_strategy() == ItemSelectionStrategy.RANDOM
                ? "Random"
                : "Prioritized");
    double user_trace_length_after =
        this.thread.hdlg.coordinate_transform.board_to_user(
            this.routing_board.cumulative_trace_length());

    FRLogger.debug(
        "Finished pass #"
            + p_pass_no
            + " in "
            + minutes
            + " minutes "
            + sec
            + " seconds with "
            + update_count
            + " board updates using "
            + thread_pool_size
            + " thread(s) with '"
            + us
            + "' strategy and '"
            + is
            + "' item selection strategy.");
    FRLogger.debug(
        "Route optimizer pass summary - Improved: "
            + best_route_result.improved()
            + ", interrupted: "
            + interrupted
            + ", via count: "
            + best_route_result.via_count()
            + ", trace length: "
            + (int)user_trace_length_after
            + ", via count delta: "
            + (via_count_before - best_route_result.via_count())
            + ", trace length delta: "
            + (int)(user_trace_length_before - user_trace_length_after)
            + ".");

    FRLogger.traceExit(optimizationPassId);

    return route_improved;
  }
}

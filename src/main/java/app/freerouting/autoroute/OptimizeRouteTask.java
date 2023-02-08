package app.freerouting.autoroute;

import app.freerouting.board.Item;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.datastructures.UndoableObjects.UndoableObjectNode;
import app.freerouting.interactive.RatsNest;
import app.freerouting.logger.FRLogger;
import java.util.Iterator;

public class OptimizeRouteTask extends BatchOptRoute implements Runnable {
  private Item curr_item;
  private final int pass_no;
  private final boolean with_prefered_directions;
  private ItemRouteResult route_result;
  private final BatchOptRouteMT optimizer;

  public OptimizeRouteTask(
      BatchOptRouteMT p_optimizer,
      int item_id,
      int p_pass_no,
      boolean p_with_preferred_directions,
      double p_min_cumulative_trace_length) {
    super(p_optimizer.thread, true);

    optimizer = p_optimizer;

    curr_item = findItemOnBoard(item_id);
    // curr_item.board = this.routing_board;

    pass_no = p_pass_no;
    with_prefered_directions = p_with_preferred_directions;
    this.min_cumulative_trace_length_before = p_min_cumulative_trace_length;
  }

  private Item findItemOnBoard(int item_id) {
    boolean found = false;

    Iterator<UndoableObjectNode> it = this.routing_board.item_list.start_read_object();

    while (it.hasNext()) {
      UndoableObjects.Storable curr_ob = routing_board.item_list.read_object(it);

      if (curr_ob instanceof Item) {
        Item item = (Item) curr_ob;

        if (item.get_id_no() == item_id) {
          return item;
        }
      }
    }

    return null;
  }

  @Override
  public void run() {
    long startTime = System.currentTimeMillis();

    if (curr_item == null) return;

//    FRLogger.debug(
//        "Start to run OptimizeRouteTask on pass "
//            + pass_no
//            + " for item #"
//            + curr_item.get_id_no()
//            + ".");

    route_result = opt_route_item(curr_item, pass_no, with_prefered_directions);

    boolean winning_candidate = optimizer.is_winning_candidate(this);

    long duration = System.currentTimeMillis() - startTime;
    long minutes = duration / 60000;
    float sec = (duration % 60000) / 1000.0F;

    FRLogger.debug(
        "Finished   task #"
            + optimizer.get_num_tasks_finished()
            + " of "
            + optimizer.get_num_tasks()
            + " for item #"
            + curr_item.get_id_no()
            + " on pass "
            + pass_no
            + " in "
            + minutes
            + " m "
            + sec
            + "s."
            + " Best so far: "
            + winning_candidate
            + ", improved: "
            + route_result.improved()
            + ", via reduction: "
            + route_result.via_count_reduced()
            + (winning_candidate ? (", length reduction: " + (int)route_result.length_reduced()) : "")
            + ", incomplete trace reduction: "
            + (route_result.incomplete_count_before() - route_result.incomplete_count()));

    if (!winning_candidate) {
      clean();
    }
  }

  public ItemRouteResult getRouteResult() {
    return this.route_result;
  }

  public Item getItem() {
    return curr_item;
  }

  public void clean() { // try to speed up memory release
    curr_item.board = null;
    curr_item = null;

    this.sorted_route_items = null;
    this.routing_board = null;
  }

  @Override
  protected void remove_ratsnest() {
    // do nothing as we create a new instance of ratsnest every time
    // assume it'll be only called twice: before and after routing
  }

  @Override
  protected RatsNest get_ratsnest() {
    return new RatsNest(this.routing_board, this.thread.hdlg.get_locale());
  }
}

package app.freerouting.autoroute;

import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.board.Connectable;
import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;

import java.util.*;

import static java.util.Collections.shuffle;

/**
 * Handles the sequencing of the auto-router passes.
 */
public class BatchAutorouter extends NamedAlgorithm
{
  // The lowest rank of the board to be selected to go back to
  private static final int BOARD_RANK_LIMIT = 50;
  // Maximum number of tries on the same board
  private static final int MAXIMUM_TRIES_ON_THE_SAME_BOARD = 3;
  private static final int TIME_LIMIT_TO_PREVENT_ENDLESS_LOOP = 1000;
  // The minimum number of passes to complete the board, unless all items are routed
  private static final int STOP_AT_PASS_MINIMUM = 8;
  // The modulo of the pass number to check if the improvements were so small that process should stop despite not all items are routed
  private static final int STOP_AT_PASS_MODULO = 4;

  private final boolean remove_unconnected_vias;
  private final AutorouteControl.ExpansionCostFactor[] trace_cost_arr;
  private final boolean retain_autoroute_database;
  private final int start_ripup_costs;
  private final int trace_pull_tight_accuracy;
  protected RoutingJob job;
  private boolean is_interrupted = false;
  /**
   * Used to draw the airline of the current routed incomplete.
   */
  private FloatLine air_line;

  public BatchAutorouter(RoutingJob job)
  {
    this(job.thread, job.board, job.routerSettings, !job.routerSettings.getRunFanout(), true, job.routerSettings.get_start_ripup_costs(), job.routerSettings.trace_pull_tight_accuracy);
    this.job = job;
  }

  public BatchAutorouter(StoppableThread p_thread, RoutingBoard board, RouterSettings settings, boolean p_remove_unconnected_vias, boolean p_with_preferred_directions, int p_start_ripup_costs, int p_pull_tight_accuracy)
  {
    super(p_thread, board, settings);

    this.remove_unconnected_vias = p_remove_unconnected_vias;
    if (p_with_preferred_directions)
    {
      this.trace_cost_arr = this.settings.get_trace_cost_arr();
    }
    else
    {
      // remove preferred direction
      this.trace_cost_arr = new AutorouteControl.ExpansionCostFactor[this.board.get_layer_count()];
      for (int i = 0; i < this.trace_cost_arr.length; ++i)
      {
        double curr_min_cost = this.settings.get_preferred_direction_trace_costs(i);
        this.trace_cost_arr[i] = new AutorouteControl.ExpansionCostFactor(curr_min_cost, curr_min_cost);
      }
    }

    this.start_ripup_costs = p_start_ripup_costs;
    this.trace_pull_tight_accuracy = p_pull_tight_accuracy;
    this.retain_autoroute_database = false;
  }

  /**
   * Auto-routes ripup passes until the board is completed or the auto-router is stopped by the user,
   * or if p_max_pass_count is exceeded. Is currently used in the optimize via batch pass. Returns
   * the number of passes to complete the board or p_max_pass_count + 1, if the board is not
   * completed.
   */
  public static int autoroute_passes_for_optimizing_item(RoutingJob job, int p_max_pass_count, int p_ripup_costs, int trace_pull_tight_accuracy, boolean p_with_preferred_directions, RoutingBoard updated_routing_board, RouterSettings routerSettings)
  {
    BatchAutorouter router_instance = new BatchAutorouter(job.thread, updated_routing_board, routerSettings, true, p_with_preferred_directions, p_ripup_costs, trace_pull_tight_accuracy);
    router_instance.job = job;

    boolean still_unrouted_items = true;
    int curr_pass_no = 1;
    while (still_unrouted_items && !router_instance.is_interrupted && curr_pass_no <= p_max_pass_count)
    {
      if (job.thread.is_stop_auto_router_requested())
      {
        router_instance.is_interrupted = true;
      }
      still_unrouted_items = router_instance.autoroute_pass(curr_pass_no);
      if (still_unrouted_items && !router_instance.is_interrupted && updated_routing_board == null)
      {
        routerSettings.increment_pass_no();
      }
      ++curr_pass_no;
    }

    if (!still_unrouted_items)
    {
      --curr_pass_no;
    }

    return curr_pass_no;
  }

  private static LinkedList<Item> getAutorouteItems(RoutingBoard board)
  {
    LinkedList<Item> autoroute_item_list = new LinkedList<>();
    Set<Item> handled_items = new TreeSet<>();
    Iterator<UndoableObjects.UndoableObjectNode> it = board.item_list.start_read_object();
    for (; ; )
    {
      UndoableObjects.Storable curr_ob = board.item_list.read_object(it);
      if (curr_ob == null)
      {
        break;
      }
      if (curr_ob instanceof Connectable && curr_ob instanceof Item curr_item)
      {
        // This is a connectable item, like PolylineTrace or Pin
        if (!curr_item.is_routable())
        {
          if (!handled_items.contains(curr_item))
          {

            // Let's go through all nets of this item
            for (int i = 0; i < curr_item.net_count(); ++i)
            {
              int curr_net_no = curr_item.get_net_no(i);
              Set<Item> connected_set = curr_item.get_connected_set(curr_net_no);
              for (Item curr_connected_item : connected_set)
              {
                if (curr_connected_item.net_count() <= 1)
                {
                  handled_items.add(curr_connected_item);
                }
              }
              int net_item_count = board.connectable_item_count(curr_net_no);

              // If the item is not connected to all other items of the net, we add it to the auto-router's to-do list
              if ((connected_set.size() < net_item_count) && (!curr_item.has_ignored_nets()))
              {
                autoroute_item_list.add(curr_item);
              }
            }
          }
        }
      }
    }
    return autoroute_item_list;
  }

  /**
   * Auto-routes one ripup pass of all items of the board. Returns false, if the board is already
   * completely routed.
   */
  private boolean autoroute_pass(int p_pass_no)
  {
    try
    {
      LinkedList<Item> autoroute_item_list = getAutorouteItems(this.board);

      // If there are no items to route, we're done
      if (autoroute_item_list.isEmpty())
      {
        this.air_line = null;
        return false;
      }

      boolean useSlowAlgorithm = p_pass_no % 4 == 0;

      // Start multiple instances of the following part in parallel, wait for the results and keep only the best one
      for (int threadIndex = 0; threadIndex < job.routerSettings.maxThreads; threadIndex++)
      {
        // deep copy the board
        RoutingBoard clonedBoard = this.board.deepCopy();

        // clone the autoroute item list to avoid concurrent modification
        List<Item> clonedAutorouteItemList = new ArrayList<>(getAutorouteItems(clonedBoard));

        // shuffle the items to route
        shuffle(clonedAutorouteItemList, new Random());

        BatchAutorouterThread autorouterThread = new BatchAutorouterThread(clonedBoard, clonedAutorouteItemList, p_pass_no, useSlowAlgorithm, job.routerSettings, this.start_ripup_costs, this.trace_pull_tight_accuracy, this.remove_unconnected_vias, true);

        // Update the board on the GUI only based on the first thread
        if (threadIndex == 0)
        {
          this.air_line = autorouterThread.latest_air_line;

          autorouterThread.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
          {
            @Override
            public void onBoardUpdatedEvent(BoardUpdatedEvent event)
            {
              fireBoardUpdatedEvent(event.getBoardStatistics(), event.getRouterCounters(), event.getBoard());
            }
          });
        }

        autorouterThread.start();
        autorouterThread.join();

        // calculate the new board score
        BoardStatistics clonedBoardStatistics = clonedBoard.get_statistics();
        float clonedBoardScore = clonedBoardStatistics.getNormalizedScore(job.routerSettings.scoring);

        job.logInfo("Router thread #" + p_pass_no + "." + ThreadIndexToLetter(threadIndex) + " finished with score: " + FRLogger.formatScore(clonedBoardScore, clonedBoardStatistics.connections.incompleteCount, clonedBoardStatistics.clearanceViolations.totalCount));
      }

      // We are done with this pass
      this.air_line = null;
      return true;
    } catch (Exception e)
    {
      job.logError("Something went wrong during the auto-routing", e);
      this.air_line = null;
      return false;
    }
  }

  /**
   * Return an uppercase one-letter, two-letter or three-letter string based on the thread index (0 = A, 1 = B, 2 = C, ..., 26 = AA, 27 = AB, ...).
   *
   * @param threadIndex
   * @return
   */
  private String ThreadIndexToLetter(int threadIndex)
  {
    if (threadIndex < 0)
    {
      return "";
    }
    if (threadIndex < 26)
    {
      return String.valueOf((char) ('A' + threadIndex));
    }
    else if (threadIndex < 26 * 26)
    {
      int firstLetterIndex = threadIndex / 26;
      int secondLetterIndex = threadIndex % 26;
      return String.valueOf((char) ('A' + firstLetterIndex)) + (char) ('A' + secondLetterIndex);
    }
    else
    {
      int firstLetterIndex = threadIndex / (26 * 26);
      int secondLetterIndex = (threadIndex / 26) % 26;
      int thirdLetterIndex = threadIndex % 26;
      return String.valueOf((char) ('A' + firstLetterIndex)) + (char) ('A' + secondLetterIndex) + (char) ('A' + thirdLetterIndex);
    }
  }

  @Override
  public String getId()
  {
    return "freerouting-router";
  }

  @Override
  public String getName()
  {
    return "Freerouting Auto-router";
  }

  @Override
  public String getVersion()
  {
    return "1.0";
  }

  @Override
  public String getDescription()
  {
    return "Freerouting Auto-router v1.0";
  }

  @Override
  public NamedAlgorithmType getType()
  {
    return NamedAlgorithmType.ROUTER;
  }

  /**
   * Autoroutes ripup passes until the board is completed or the autorouter is stopped by the user.
   * Returns true if the board is completed.
   */
  public boolean runBatchLoop()
  {
    this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.STARTED, 0, this.board.get_hash()));

    boolean continueAutorouting = true;
    BoardHistory bh = new BoardHistory(job.routerSettings.scoring);

    while (continueAutorouting && !this.is_interrupted)
    {
      if (thread.is_stop_auto_router_requested() || (job != null && job.state == RoutingJobState.TIMED_OUT))
      {
        this.is_interrupted = true;
      }

      String current_board_hash = this.board.get_hash();

      int curr_pass_no = this.settings.get_start_pass_no();
      if (curr_pass_no > this.settings.get_stop_pass_no())
      {
        thread.request_stop_auto_router();
        break;
      }

      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.RUNNING, curr_pass_no, current_board_hash));

      float boardScoreBefore = new BoardStatistics(this.board).getNormalizedScore(job.routerSettings.scoring);
      bh.add(this.board);

      FRLogger.traceEntry("BatchAutorouter.autoroute_pass #" + curr_pass_no + " on board '" + current_board_hash + "'");

      continueAutorouting = autoroute_pass(curr_pass_no);

      BoardStatistics boardStatisticsAfter = new BoardStatistics(this.board);
      float boardScoreAfter = boardStatisticsAfter.getNormalizedScore(job.routerSettings.scoring);

      if ((bh.size() >= STOP_AT_PASS_MINIMUM) || (thread.is_stop_auto_router_requested()))
      {
        if (((curr_pass_no % STOP_AT_PASS_MODULO == 0) && (curr_pass_no >= STOP_AT_PASS_MINIMUM)) || (thread.is_stop_auto_router_requested()))
        {
          // Check if the score improved compared to the previous passes, restore a previous board if not
          if (bh.getMaxScore() >= boardScoreAfter)
          {
            var boardToRestore = bh.restoreBoard(MAXIMUM_TRIES_ON_THE_SAME_BOARD);
            if (boardToRestore == null)
            {
              job.logInfo("The router was not able to improve the board, stopping the auto-router.");
              thread.request_stop_auto_router();
              break;
            }

            int boardToRestoreRank = bh.getRank(boardToRestore);

            if (boardToRestoreRank > BOARD_RANK_LIMIT)
            {
              thread.request_stop_auto_router();
              break;
            }

            this.board = boardToRestore;
            var boardStatistics = this.board.get_statistics();
            job.logInfo("Restoring an earlier board that has the score of " + FRLogger.formatScore(boardStatistics.getNormalizedScore(job.routerSettings.scoring), boardStatistics.connections.incompleteCount, boardStatistics.clearanceViolations.totalCount) + ".");
          }
        }
      }
      double autorouter_pass_duration = FRLogger.traceExit("BatchAutorouter.autoroute_pass #" + curr_pass_no + " on board '" + current_board_hash + "'");

      String passCompletedMessage = "Auto-router pass #" + curr_pass_no + " on board '" + current_board_hash + "' was completed in " + FRLogger.formatDuration(autorouter_pass_duration) + " with the score of " + FRLogger.formatScore(boardScoreAfter, boardStatisticsAfter.connections.incompleteCount, boardStatisticsAfter.clearanceViolations.totalCount);
      if (job.resourceUsage.cpuTimeUsed > 0)
      {
        passCompletedMessage += ", using " + FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed) + " CPU seconds and " + (int) job.resourceUsage.maxMemoryUsed + " MB memory.";
      }
      else
      {
        passCompletedMessage += ".";
      }
      job.logInfo(passCompletedMessage);

      if (this.settings.save_intermediate_stages)
      {
        fireBoardSnapshotEvent(this.board);
      }

      // check if there are still unrouted items
      if (continueAutorouting && !is_interrupted)
      {
        this.settings.increment_pass_no();
      }
    }

    job.board = this.board;

    bh.clear();

    if (!this.is_interrupted)
    {
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.FINISHED, this.settings.get_start_pass_no(), this.board.get_hash()));
    }
    else
    {
      // TODO: set it to TIMED_OUT if it was interrupted because of timeout
      this.fireTaskStateChangedEvent(new TaskStateChangedEvent(this, TaskState.CANCELLED, this.settings.get_start_pass_no(), this.board.get_hash()));
    }

    return !this.is_interrupted;
  }

  /**
   * Returns the airline of the current autorouted connection or null, if no such airline exists
   */
  public FloatLine get_air_line()
  {
    if (this.air_line == null)
    {
      return null;
    }
    if (this.air_line.a == null || this.air_line.b == null)
    {
      return null;
    }
    return this.air_line;
  }
}
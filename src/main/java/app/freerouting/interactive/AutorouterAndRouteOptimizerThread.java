package app.freerouting.interactive;

import app.freerouting.autoroute.*;
import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.autoroute.events.TaskStateChangedEventListener;
import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BoardStatistics;
import app.freerouting.board.Unit;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.FRAnalytics;
import app.freerouting.management.TextManager;
import app.freerouting.settings.RouterSettings;
import app.freerouting.tests.BoardValidator;

import java.awt.*;

/**
 * GUI interactive thread for the batch auto-router + route optimizer.
 */
public class AutorouterAndRouteOptimizerThread extends InteractiveActionThread
{
  private final BatchAutorouter batch_autorouter;
  private final BatchOptRoute batch_opt_route;

  /**
   * Creates a new instance of AutorouterAndRouteOptimizerThread
   */
  protected AutorouterAndRouteOptimizerThread(GuiBoardManager p_board_handling, RouterSettings routerSettings)
  {
    super(p_board_handling);
    this.batch_autorouter = new BatchAutorouter(this, this.hdlg.get_routing_board(), routerSettings, !routerSettings.get_with_fanout(), true, routerSettings.get_start_ripup_costs(), this.hdlg.settings.autoroute_settings.trace_pull_tight_accuracy);
    this.batch_autorouter.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
    {
      @Override
      public void onBoardUpdatedEvent(BoardUpdatedEvent event)
      {
        BoardStatistics boardStatistics = event.getBoardStatistics();
        hdlg.screen_messages.set_batch_autoroute_info(boardStatistics.unrouted_item_count, boardStatistics.routed_item_count, boardStatistics.ripped_item_count, boardStatistics.not_found_item_count);
        hdlg.repaint();
      }
    });

    this.batch_autorouter.addTaskStateChangedEventListener(new TaskStateChangedEventListener()
    {
      @Override
      public void onTaskStateChangedEvent(TaskStateChangedEvent event)
      {
        TaskState taskState = event.getTaskState();
        if (taskState == TaskState.RUNNING)
        {
          TextManager tm = new TextManager(InteractiveState.class, hdlg.get_locale());
          String start_message = tm.getText("autorouter_started", Integer.toString(event.getPassNumber()));
          hdlg.screen_messages.set_status_message(start_message);
        }
      }
    });

    if (routerSettings.maxThreads > 1)
    {
      FRLogger.warn("Multi-threaded route optimization is broken and it is known to generate clearance violations. It is highly recommended to use the single-threaded route optimization instead by setting the number of threads to 1 with the '-mt 1' command line argument.");
    }

    this.batch_opt_route = routerSettings.maxThreads > 1 ? new BatchOptRouteMT(this, hdlg.get_routing_board(), routerSettings) : new BatchOptRoute(this, false, hdlg.get_routing_board(), routerSettings);
  }

  @Override
  protected void thread_action()
  {
    for (ThreadActionListener hl : this.listeners)
    {
      hl.autorouterStarted();
    }

    FRLogger.traceEntry("BatchAutorouterThread.thread_action()");
    try
    {
      TextManager tm = new TextManager(InteractiveState.class, hdlg.get_locale());

      boolean saved_board_read_only = hdlg.is_board_read_only();
      hdlg.set_board_read_only(true);
      boolean ratsnest_hidden_before = hdlg.get_ratsnest().is_hidden();
      if (!ratsnest_hidden_before)
      {
        hdlg.get_ratsnest().hide();
      }

      FRLogger.info("Starting auto-routing...");
      FRLogger.traceEntry("BatchAutorouterThread.thread_action()-autorouting");
      FRAnalytics.autorouterStarted();

      String start_message = tm.getText("batch_autorouter") + " " + tm.getText("stop_message");
      hdlg.screen_messages.set_status_message(start_message);
      boolean fanout_first = hdlg.get_settings().autoroute_settings.get_with_fanout() && hdlg.get_settings().autoroute_settings.get_start_pass_no() <= 1;
      if (fanout_first)
      {
        BatchFanout fanout = new BatchFanout(this, hdlg.get_routing_board(), hdlg.get_settings().autoroute_settings);
        fanout.addTaskStateChangedEventListener(new TaskStateChangedEventListener()
        {
          @Override
          public void onTaskStateChangedEvent(TaskStateChangedEvent event)
          {
            hdlg.screen_messages.set_batch_fanout_info(event.getPassNumber(), 0);
          }
        });
        fanout.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
        {
          @Override
          public void onBoardUpdatedEvent(BoardUpdatedEvent event)
          {
            hdlg.repaint();
          }
        });
        fanout.runBatchLoop();
      }
      if (hdlg.get_settings().autoroute_settings.get_with_autoroute() && !this.is_stop_auto_router_requested())
      {
        batch_autorouter.runBatchLoop();
      }
      hdlg.get_routing_board().finish_autoroute();

      double autoroutingSecondsToComplete = FRLogger.traceExit("BatchAutorouterThread.thread_action()-autorouting");
      FRLogger.info("Auto-routing was completed in " + FRLogger.formatDuration(autoroutingSecondsToComplete) + ".");
      FRAnalytics.autorouterFinished();

      Thread.sleep(100);

      int num_threads = hdlg.get_num_threads();
      if (num_threads > 0)
      {
        FRLogger.info("Starting route optimization on " + (num_threads == 1 ? "1 thread" : num_threads + " threads") + "...");
        FRLogger.traceEntry("BatchAutorouterThread.thread_action()-routeoptimization");
        FRAnalytics.routeOptimizerStarted();

        int via_count_before = hdlg.get_routing_board().get_vias().size();
        double trace_length_before = hdlg.coordinate_transform.board_to_user(hdlg.get_routing_board().cumulative_trace_length());

        if (hdlg.get_settings().autoroute_settings.get_with_postroute() && !this.isStopRequested())
        {
          String opt_message = tm.getText("batch_optimizer") + " " + tm.getText("stop_message");
          hdlg.screen_messages.set_status_message(opt_message);
          this.batch_opt_route.runBatchLoop();
          String curr_message;
          if (this.isStopRequested())
          {
            curr_message = tm.getText("interrupted");
          }
          else
          {
            curr_message = tm.getText("completed");
          }
          String end_message = tm.getText("postroute") + " " + curr_message;
          hdlg.screen_messages.set_status_message(end_message);
        }
        else
        {
          hdlg.screen_messages.clear();
          String curr_message;
          if (this.isStopRequested())
          {
            curr_message = tm.getText("interrupted");
          }
          else
          {
            curr_message = tm.getText("completed");
          }
          int incomplete_count = hdlg.get_ratsnest().incomplete_count();
          String end_message = tm.getText("autoroute") + " " + curr_message + ", " + incomplete_count + " " + tm.getText("connections_not_found");
          hdlg.screen_messages.set_status_message(end_message);
        }

        int via_count_after = hdlg.get_routing_board().get_vias().size();
        double trace_length_after = hdlg.coordinate_transform.board_to_user(hdlg.get_routing_board().cumulative_trace_length());

        double percentage_improvement = (via_count_before != 0 && trace_length_before != 0) ? 1.0 - (((((float) via_count_after / via_count_before) + (trace_length_after / trace_length_before)) / 2)) : 0;

        double routeOptimizationSecondsToComplete = FRLogger.traceExit("BatchAutorouterThread.thread_action()-routeoptimization");
        FRLogger.info("Route optimization was completed in " + FRLogger.formatDuration(routeOptimizationSecondsToComplete) + (percentage_improvement > 0 ? " and it improved the design by ~" + String.format("%(,.2f", percentage_improvement * 100.0) + "%" : "") + ".");
        FRAnalytics.routeOptimizerFinished();

        if (!this.isStopRequested())
        {
          hdlg.get_panel().board_frame.delete_intermediate_stage_file();
        }
      }

      hdlg.set_board_read_only(saved_board_read_only);
      hdlg.update_ratsnest();
      if (!ratsnest_hidden_before)
      {
        hdlg.get_ratsnest().show();
      }

      hdlg.get_panel().board_frame.refresh_windows();
      if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
      {
        BoardValidator.doAllTracesHaveAnglesThatAreMultiplesOfFortyFiveDegrees("after autoroute: ", hdlg.get_routing_board());
      }
    } catch (Exception e)
    {
      FRLogger.error(e.getLocalizedMessage(), e);
    }

    FRLogger.traceExit("BatchAutorouterThread.thread_action()");

    for (ThreadActionListener hl : this.listeners)
    {
      if (this.isStopRequested())
      {
        hl.autorouterAborted();
      }
      else
      {
        hl.autorouterFinished();
      }
    }
  }

  @Override
  public void draw(Graphics p_graphics)
  {
    FloatLine curr_air_line = batch_autorouter.get_air_line();
    if (curr_air_line != null)
    {
      FloatPoint[] draw_line = new FloatPoint[2];
      draw_line[0] = curr_air_line.a;
      draw_line[1] = curr_air_line.b;
      // draw the incomplete
      Color draw_color = this.hdlg.graphics_context.get_incomplete_color();
      double draw_width = Math.min(this.hdlg.get_routing_board().communication.get_resolution(Unit.MIL) * 3, 300); // problem with low resolution on Kicad300;
      this.hdlg.graphics_context.draw(draw_line, draw_width, draw_color, p_graphics, 1);
    }
    FloatPoint current_opt_position = batch_opt_route.get_current_position();
    int radius = 10 * this.hdlg.get_routing_board().rules.get_default_trace_half_width(0);
    if (current_opt_position != null)
    {
      final int draw_width = 1;
      Color draw_color = this.hdlg.graphics_context.get_incomplete_color();
      FloatPoint[] draw_points = new FloatPoint[2];
      draw_points[0] = new FloatPoint(current_opt_position.x - radius, current_opt_position.y - radius);
      draw_points[1] = new FloatPoint(current_opt_position.x + radius, current_opt_position.y + radius);
      this.hdlg.graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, 1);
      draw_points[0] = new FloatPoint(current_opt_position.x + radius, current_opt_position.y - radius);
      draw_points[1] = new FloatPoint(current_opt_position.x - radius, current_opt_position.y + radius);
      this.hdlg.graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, 1);
      this.hdlg.graphics_context.draw_circle(current_opt_position, radius, draw_width, draw_color, p_graphics, 1);
    }
  }
}
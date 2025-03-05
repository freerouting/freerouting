package app.freerouting.interactive;

import app.freerouting.autoroute.*;
import app.freerouting.autoroute.events.*;
import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Unit;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.designforms.specctra.SpecctraSesFileWriter;
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.FileFormat;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.TextManager;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.management.gson.GsonProvider;
import app.freerouting.tests.BoardValidator;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.Instant;

import static app.freerouting.Freerouting.globalSettings;

/**
 * GUI interactive thread for the batch auto-router + route optimizer.
 */
public class AutorouterAndRouteOptimizerThread extends InteractiveActionThread
{
  private final BatchAutorouter batch_autorouter;
  private BatchOptimizer batch_opt_route;

  /**
   * Creates a new instance of AutorouterAndRouteOptimizerThread
   */
  protected AutorouterAndRouteOptimizerThread(GuiBoardManager p_board_handling, RoutingJob routingJob)
  {
    super(p_board_handling, routingJob);

    routingJob.thread = this;
    routingJob.board = p_board_handling.get_routing_board();

    this.batch_autorouter = new BatchAutorouter(routingJob);
    // Add event listener for the GUI updates
    this.batch_autorouter.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
    {
      @Override
      public void onBoardUpdatedEvent(BoardUpdatedEvent event)
      {
        boardManager.screen_messages.set_batch_autoroute_info(event.getRouterCounters());
        boardManager.repaint();
        routingJob.logDebug(GsonProvider.GSON.toJson(event.getRouterCounters()));
        routingJob.logDebug(GsonProvider.GSON.toJson(event.getBoardStatistics()));
      }
    });

    // Add another event listener for the job output object updates
    this.batch_autorouter.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
    {
      @Override
      public void onBoardUpdatedEvent(BoardUpdatedEvent event)
      {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
        {
          boolean wasSaveSuccessful = SpecctraSesFileWriter.write(boardManager.get_routing_board(), outputStream, routingJob.name);

          if (wasSaveSuccessful)
          {
            byte[] sesOutputData = outputStream.toByteArray();
            routingJob.output.setData(sesOutputData);
          }
        } catch (Exception e)
        {
          routingJob.logError("Couldn't save the SES output into the job object.", e);
        }
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
          TextManager tm = new TextManager(InteractiveState.class, boardManager.get_locale());
          String start_message = tm.getText("autorouter_started", Integer.toString(event.getPassNumber()));
          boardManager.screen_messages.set_status_message(start_message);
        }
      }
    });

    this.batch_autorouter.addBoardSnapshotEventListener(new BoardSnapshotEventListener()
    {
      @Override
      public void onBoardSnapshotEvent(BoardSnapshotEvent event)
      {
        boardManager.get_panel().board_frame.save_intermediate_stage_file();
      }
    });

    if (routingJob.routerSettings.maxThreads > 1)
    {
      routingJob.logWarning("Multi-threaded route optimization is broken and it is known to generate clearance violations. It is highly recommended to use the single-threaded route optimization instead by setting the number of threads to 1 with the '-mt 1' command line argument.");
    }

    this.batch_opt_route = null;

    if (routingJob.routerSettings.maxThreads == 1)
    {
      // Single-threaded route optimization
      this.batch_opt_route = new BatchOptimizer(routingJob);

      // Add event listener for the GUI updates
      this.batch_opt_route.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
      {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event)
        {
          BoardStatistics boardStatistics = event.getBoardStatistics();
          boardManager.screen_messages.set_post_route_info(boardStatistics.items.viaCount, boardStatistics.traces.totalLength, boardManager.coordinate_transform.user_unit);
          boardManager.repaint();
        }
      });

      this.batch_opt_route.addTaskStateChangedEventListener(new TaskStateChangedEventListener()
      {
        @Override
        public void onTaskStateChangedEvent(TaskStateChangedEvent event)
        {
          TaskState taskState = event.getTaskState();
          if (taskState == TaskState.RUNNING)
          {
            TextManager tm = new TextManager(InteractiveState.class, boardManager.get_locale());
            String start_message = tm.getText("optimizer_started", Integer.toString(event.getPassNumber()));
            boardManager.screen_messages.set_status_message(start_message);
          }
        }
      });
    }

    if (routingJob.routerSettings.maxThreads > 1)
    {
      // Multi-threaded route optimization
      this.batch_opt_route = new BatchOptimizerMultiThreaded(routingJob);

      this.batch_opt_route.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
      {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event)
        {
          BoardStatistics boardStatistics = event.getBoardStatistics();
          boardManager.replaceRoutingBoard(event.getBoard());
          boardManager.screen_messages.set_post_route_info(boardStatistics.items.viaCount, boardStatistics.traces.totalLength, boardManager.coordinate_transform.user_unit);
        }
      });

      this.batch_opt_route.addTaskStateChangedEventListener(new TaskStateChangedEventListener()
      {
        @Override
        public void onTaskStateChangedEvent(TaskStateChangedEvent event)
        {
          TaskState taskState = event.getTaskState();
          if (taskState == TaskState.RUNNING)
          {
            TextManager tm = new TextManager(InteractiveState.class, boardManager.get_locale());
            String start_message = tm.getText("optimizer_started", Integer.toString(event.getPassNumber()));
            boardManager.screen_messages.set_status_message(start_message);
          }
        }
      });

    }
  }

  @Override
  protected void thread_action()
  {
    routingJob.startedAt = Instant.now();
    routingJob.state = RoutingJobState.RUNNING;
    boardManager.set_num_threads(routingJob.routerSettings.maxThreads);

    for (ThreadActionListener hl : this.listeners)
    {
      hl.autorouterStarted();
    }

    FRLogger.traceEntry("BatchAutorouterThread.thread_action()");
    try
    {
      TextManager tm = new TextManager(InteractiveState.class, boardManager.get_locale());

      boolean saved_board_read_only = boardManager.is_board_read_only();
      boardManager.set_board_read_only(true);
      boolean ratsnest_hidden_before = boardManager
          .get_ratsnest()
          .is_hidden();
      if (!ratsnest_hidden_before)
      {
        boardManager
            .get_ratsnest()
            .hide();
      }

      routingJob.logInfo("Starting routing of '" + routingJob.name + "'...");
      FRLogger.traceEntry("BatchAutorouterThread.thread_action()-autorouting");

      globalSettings.statistics.incrementJobsCompleted();
      FRAnalytics.autorouterStarted();

      String start_message = tm.getText("batch_autorouter") + " " + tm.getText("stop_message");
      boardManager.screen_messages.set_status_message(start_message);

      // Let's run the fanout if it's enabled
      boolean fanout_first = boardManager.get_settings().autoroute_settings.getRunFanout() && boardManager.get_settings().autoroute_settings.get_start_pass_no() <= 1;
      if (fanout_first)
      {
        BatchFanout fanout = new BatchFanout(routingJob);
        fanout.addTaskStateChangedEventListener(new TaskStateChangedEventListener()
        {
          @Override
          public void onTaskStateChangedEvent(TaskStateChangedEvent event)
          {
            boardManager.screen_messages.set_batch_fanout_info(event.getPassNumber(), 0);
          }
        });
        fanout.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
        {
          @Override
          public void onBoardUpdatedEvent(BoardUpdatedEvent event)
          {
            boardManager.repaint();
          }
        });
        fanout.runBatchLoop();
      }

      // Let's run the autorouter
      if (boardManager.get_settings().autoroute_settings.getRunRouter() && !this.is_stop_auto_router_requested())
      {
        batch_autorouter.runBatchLoop();
      }
      boardManager
          .get_routing_board()
          .finish_autoroute();

      double autoroutingSecondsToComplete = FRLogger.traceExit("BatchAutorouterThread.thread_action()-autorouting");
      routingJob.logInfo("Auto-routing was completed in " + FRLogger.formatDuration(autoroutingSecondsToComplete) + ".");
      FRAnalytics.autorouterFinished();

      Thread.sleep(100);

      // Let's run the optimizer if it's enabled
      int num_threads = boardManager.get_num_threads();
      if (num_threads > 0)
      {
        routingJob.logInfo("Starting route optimization on " + (num_threads == 1 ? "1 thread" : num_threads + " threads") + "...");
        FRLogger.traceEntry("BatchAutorouterThread.thread_action()-routeoptimization");
        FRAnalytics.routeOptimizerStarted();

        int via_count_before = boardManager
            .get_routing_board()
            .get_vias()
            .size();
        double trace_length_before = boardManager.coordinate_transform.board_to_user(boardManager
            .get_routing_board()
            .cumulative_trace_length());

        if (boardManager.get_settings().autoroute_settings.getRunOptimizer() && !this.isStopRequested())
        {
          String opt_message = tm.getText("batch_optimizer") + " " + tm.getText("stop_message");
          boardManager.screen_messages.set_status_message(opt_message);
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
          boardManager.screen_messages.set_status_message(end_message);
        }

        int via_count_after = boardManager
            .get_routing_board()
            .get_vias()
            .size();
        double trace_length_after = boardManager.coordinate_transform.board_to_user(boardManager
            .get_routing_board()
            .cumulative_trace_length());

        double percentage_improvement = (via_count_before != 0 && trace_length_before != 0) ? 1.0 - (((((float) via_count_after / via_count_before) + (trace_length_after / trace_length_before)) / 2)) : 0;

        double routeOptimizationSecondsToComplete = FRLogger.traceExit("BatchAutorouterThread.thread_action()-routeoptimization");
        routingJob.logInfo("Route optimization was completed in " + FRLogger.formatDuration(routeOptimizationSecondsToComplete) + (percentage_improvement > 0 ? " and it improved the design by ~" + String.format("%(,.2f", percentage_improvement * 100.0) + "%" : "") + ".");
        FRAnalytics.routeOptimizerFinished();

        if (!this.isStopRequested())
        {
          boardManager.get_panel().board_frame.delete_intermediate_stage_file();
        }
      }

      // Restore the board read-only state
      boardManager.set_board_read_only(saved_board_read_only);

      // Save the result to the output field as a Specctra SES file
      if (routingJob.output.format == FileFormat.SES)
      {
        // Save the SES file after the auto-router has finished
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
        {
          if (boardManager.saveAsSpecctraSessionSes(baos, routingJob.name))
          {
            routingJob.output.setData(baos.toByteArray());
          }
        } catch (Exception e)
        {
          routingJob.logError("Couldn't save the output into the job object.", e);
        }
      }

      // Update the ratsnest
      boardManager.update_ratsnest();
      if (!ratsnest_hidden_before)
      {
        boardManager
            .get_ratsnest()
            .show();
      }

      // Update the message status bar, indicating that auto-routing is completed
      boardManager.screen_messages.clear();
      String curr_message;
      if (this.isStopRequested())
      {
        curr_message = tm.getText("interrupted");
      }
      else
      {
        curr_message = tm.getText("completed");
      }
      int incomplete_count = boardManager
          .get_ratsnest()
          .incomplete_count();
      String end_message = tm.getText("autoroute") + " " + curr_message + ", " + incomplete_count + " " + tm.getText("connections_not_found");
      boardManager.screen_messages.set_status_message(end_message);

      // Refresh the windows
      boardManager.get_panel().board_frame.refresh_windows();
      if (boardManager.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE)
      {
        BoardValidator.doAllTracesHaveAnglesThatAreMultiplesOfFortyFiveDegrees("after autoroute: ", boardManager.get_routing_board());
      }
    } catch (Exception e)
    {
      routingJob.logError(e.getLocalizedMessage(), e);
    }

    if (this.isStopRequested())
    {
      routingJob.finishedAt = Instant.now();
      routingJob.state = RoutingJobState.CANCELLED;
    }
    else
    {
      routingJob.finishedAt = Instant.now();
      routingJob.state = RoutingJobState.COMPLETED;
      globalSettings.statistics.incrementJobsCompleted();
    }

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

    FRLogger.traceExit("BatchAutorouterThread.thread_action()");
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
      Color draw_color = this.boardManager.graphics_context.get_incomplete_color();
      double draw_width = Math.min(this.boardManager.get_routing_board().communication.get_resolution(Unit.MIL) * 3, 300); // problem with low resolution on Kicad300;
      this.boardManager.graphics_context.draw(draw_line, draw_width, draw_color, p_graphics, 1);
    }
    FloatPoint current_opt_position = batch_opt_route.get_current_position();
    int radius = 10 * this.boardManager.get_routing_board().rules.get_default_trace_half_width(0);
    if (current_opt_position != null)
    {
      final int draw_width = 1;
      Color draw_color = this.boardManager.graphics_context.get_incomplete_color();
      FloatPoint[] draw_points = new FloatPoint[2];
      draw_points[0] = new FloatPoint(current_opt_position.x - radius, current_opt_position.y - radius);
      draw_points[1] = new FloatPoint(current_opt_position.x + radius, current_opt_position.y + radius);
      this.boardManager.graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, 1);
      draw_points[0] = new FloatPoint(current_opt_position.x + radius, current_opt_position.y - radius);
      draw_points[1] = new FloatPoint(current_opt_position.x - radius, current_opt_position.y + radius);
      this.boardManager.graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, 1);
      this.boardManager.graphics_context.draw_circle(current_opt_position, radius, draw_width, draw_color, p_graphics, 1);
    }
  }
}
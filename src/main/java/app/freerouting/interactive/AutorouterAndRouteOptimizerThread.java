package app.freerouting.interactive;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.autoroute.BatchAutorouter;
import app.freerouting.autoroute.BatchAutorouterV19;
import app.freerouting.autoroute.BatchOptimizer;
import app.freerouting.autoroute.BatchOptimizerMultiThreaded;
import app.freerouting.autoroute.NamedAlgorithm;
import app.freerouting.autoroute.TaskState;
import app.freerouting.autoroute.events.BoardSnapshotEvent;
import app.freerouting.autoroute.events.BoardSnapshotEventListener;
import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.autoroute.events.TaskStateChangedEvent;
import app.freerouting.autoroute.events.TaskStateChangedEventListener;
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
import java.awt.Color;
import java.awt.Graphics;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Objects;

/**
 * GUI interactive thread for the batch auto-router + route optimizer.
 */
public class AutorouterAndRouteOptimizerThread extends InteractiveActionThread {

  private final NamedAlgorithm batchAutorouter;
  private BatchOptimizer batchOptimizer;

  /**
   * Creates a new instance of AutorouterAndRouteOptimizerThread
   */
  protected AutorouterAndRouteOptimizerThread(GuiBoardManager p_board_handling, RoutingJob routingJob) {
    super(p_board_handling, routingJob);

    routingJob.thread = this;
    routingJob.board = p_board_handling.get_routing_board();

    // Select the appropriate router algorithm based on settings
    String algorithm = routingJob.routerSettings.algorithm;
    if (app.freerouting.settings.RouterSettings.ALGORITHM_V19.equals(algorithm)) {
      routingJob.logInfo("Using v1.9 router algorithm: " + algorithm);
      this.batchAutorouter = new BatchAutorouterV19(routingJob);
    } else {
      if (!app.freerouting.settings.RouterSettings.ALGORITHM_CURRENT.equals(algorithm)) {
        routingJob.logWarning(
            "The algorithm '" + algorithm + "' is not supported. The default algorithm '" +
                app.freerouting.settings.RouterSettings.ALGORITHM_CURRENT + "' will be used instead.");
        routingJob.routerSettings.algorithm = app.freerouting.settings.RouterSettings.ALGORITHM_CURRENT;
      }
      this.batchAutorouter = new BatchAutorouter(routingJob);
    }

    // Add event listener for the GUI updates
    this.batchAutorouter.addBoardUpdatedEventListener(new BoardUpdatedEventListener() {
      @Override
      public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
        float boardScore = event
            .getBoardStatistics()
            .getNormalizedScore(routingJob.routerSettings.scoring);

        boardManager.screen_messages.set_batch_autoroute_info(event.getRouterCounters());
        boardManager.screen_messages.set_board_score(boardScore, event.getBoardStatistics().connections.incompleteCount,
            event.getBoardStatistics().clearanceViolations.totalCount);
        boardManager.repaint();
      }
    });

    // Add another event listener for the job output object updates
    this.batchAutorouter.addBoardUpdatedEventListener(new BoardUpdatedEventListener() {
      @Override
      public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
          boolean wasSaveSuccessful = SpecctraSesFileWriter.write(boardManager.get_routing_board(), outputStream,
              routingJob.name);

          if (wasSaveSuccessful) {
            byte[] sesOutputData = outputStream.toByteArray();
            routingJob.output.setData(sesOutputData);
          }
        } catch (Exception e) {
          routingJob.logError("Couldn't save the SES output into the job object.", e);
        }
      }
    });

    this.batchAutorouter.addTaskStateChangedEventListener(new TaskStateChangedEventListener() {
      @Override
      public void onTaskStateChangedEvent(TaskStateChangedEvent event) {
        TaskState taskState = event.getTaskState();
        if (taskState == TaskState.RUNNING) {
          TextManager tm = new TextManager(InteractiveState.class, boardManager.get_locale());
          String start_message = tm.getText("autorouter_started", Integer.toString(event.getPassNumber()));
          boardManager.screen_messages.set_status_message(start_message);
        }
      }
    });

    this.batchAutorouter.addBoardSnapshotEventListener(new BoardSnapshotEventListener() {
      @Override
      public void onBoardSnapshotEvent(BoardSnapshotEvent event) {
        boardManager.get_panel().board_frame.save_intermediate_stage_file();
      }
    });

    this.batchOptimizer = null;

    if (routingJob.routerSettings.optimizer.enabled) {
      if ((!globalSettings.featureFlags.multiThreading) || (routingJob.routerSettings.optimizer.maxThreads == 1)) {
        // Single-threaded route optimization
        this.batchOptimizer = new BatchOptimizer(routingJob);

        if (!Objects.equals(routingJob.routerSettings.optimizer.algorithm, this.batchOptimizer.getId())) {
          routingJob.logWarning("The algorithm '" + routingJob.routerSettings.optimizer.algorithm
              + "' is not supported by the batch autorouter. The default algorithm '" + this.batchOptimizer.getId()
              + "' will be used instead.");
          routingJob.routerSettings.optimizer.algorithm = this.batchOptimizer.getId();
        }

        // Add event listener for the GUI updates
        this.batchOptimizer.addBoardUpdatedEventListener(new BoardUpdatedEventListener() {
          @Override
          public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
            BoardStatistics boardStatistics = event.getBoardStatistics();
            boardManager.screen_messages.set_post_route_info(boardStatistics.items.viaCount,
                boardStatistics.traces.totalLength, boardManager.coordinate_transform.user_unit);
            boardManager.screen_messages.set_board_score(
                boardStatistics.getNormalizedScore(routingJob.routerSettings.scoring),
                boardStatistics.connections.incompleteCount,
                boardStatistics.clearanceViolations.totalCount);
            boardManager.repaint();
          }
        });

        this.batchOptimizer.addTaskStateChangedEventListener(new TaskStateChangedEventListener() {
          @Override
          public void onTaskStateChangedEvent(TaskStateChangedEvent event) {
            TaskState taskState = event.getTaskState();
            if (taskState == TaskState.RUNNING) {
              TextManager tm = new TextManager(InteractiveState.class, boardManager.get_locale());
              String start_message = tm.getText("optimizer_started", Integer.toString(event.getPassNumber()));
              boardManager.screen_messages.set_status_message(start_message);
            }
          }
        });
      }

      if ((globalSettings.featureFlags.multiThreading) && (routingJob.routerSettings.optimizer.maxThreads > 1)) {
        // Multi-threaded route optimization
        this.batchOptimizer = new BatchOptimizerMultiThreaded(routingJob);

        if (!Objects.equals(routingJob.routerSettings.optimizer.algorithm, this.batchOptimizer.getId())) {
          routingJob.logWarning("The algorithm '" + routingJob.routerSettings.optimizer.algorithm
              + "' is not supported by the batch autorouter. The default algorithm '" + this.batchOptimizer.getId()
              + "' will be used instead.");
          routingJob.routerSettings.optimizer.algorithm = this.batchOptimizer.getId();
        }

        this.batchOptimizer.addBoardUpdatedEventListener(new BoardUpdatedEventListener() {
          @Override
          public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
            BoardStatistics boardStatistics = event.getBoardStatistics();
            boardManager.replaceRoutingBoard(event.getBoard());
            boardManager.screen_messages.set_post_route_info(boardStatistics.items.viaCount,
                boardStatistics.traces.totalLength, boardManager.coordinate_transform.user_unit);
          }
        });

        this.batchOptimizer.addTaskStateChangedEventListener(new TaskStateChangedEventListener() {
          @Override
          public void onTaskStateChangedEvent(TaskStateChangedEvent event) {
            TaskState taskState = event.getTaskState();
            if (taskState == TaskState.RUNNING) {
              TextManager tm = new TextManager(InteractiveState.class, boardManager.get_locale());
              String start_message = tm.getText("optimizer_started", Integer.toString(event.getPassNumber()));
              boardManager.screen_messages.set_status_message(start_message);
            }
          }
        });

      }
    }
  }

  @Override
  protected void thread_action() {
    routingJob.startedAt = Instant.now();
    routingJob.state = RoutingJobState.RUNNING;
    boardManager.set_num_threads(routingJob.routerSettings.maxThreads);

    for (ThreadActionListener hl : this.listeners) {
      hl.autorouterStarted();
    }

    FRLogger.traceEntry("BatchAutorouterThread.thread_action()");
    try {
      TextManager tm = new TextManager(InteractiveState.class, boardManager.get_locale());

      boolean saved_board_read_only = boardManager.is_board_read_only();
      boardManager.set_board_read_only(true);
      boolean ratsnest_hidden_before = boardManager
          .get_ratsnest()
          .is_hidden();
      if (!ratsnest_hidden_before) {
        boardManager
            .get_ratsnest()
            .hide();
      }

      int threadCount = routingJob.routerSettings.maxThreads;
      routingJob.logInfo("Starting routing of '" + routingJob.name + "' on "
          + (threadCount == 1 ? "1 thread" : threadCount + " threads") + "...");
      FRLogger.traceEntry("BatchAutorouterThread.thread_action()-autorouting");

      globalSettings.statistics.incrementJobsCompleted();
      FRAnalytics.autorouterStarted();

      String start_message = tm.getText("batch_autorouter") + " " + tm.getText("stop_message");
      boardManager.screen_messages.set_status_message(start_message);

      // Let's run the autorouter
      if (routingJob.routerSettings.getRunRouter() && !this.is_stop_auto_router_requested()) {
        // Cast to access runBatchLoop() which exists on both BatchAutorouter and
        // BatchAutorouterV19
        if (batchAutorouter instanceof BatchAutorouter) {
          ((BatchAutorouter) batchAutorouter).runBatchLoop();
        } else if (batchAutorouter instanceof BatchAutorouterV19) {
          ((BatchAutorouterV19) batchAutorouter).runBatchLoop();
        }
      }

      boardManager.replaceRoutingBoard(routingJob.board);

      boardManager
          .get_routing_board()
          .finish_autoroute();

      var bs = new BoardStatistics(boardManager.get_routing_board());
      var scoreBeforeOptimization = bs.getNormalizedScore(routingJob.routerSettings.scoring);

      double autoroutingSecondsToComplete = FRLogger.traceExit("BatchAutorouterThread.thread_action()-autorouting");

      // Log detailed session summary
      int initialUnroutedCount = 0;
      Instant sessionStartTime = null;
      int currentPassNo = 0; // Will be populated below

      if (batchAutorouter instanceof BatchAutorouter) {
        sessionStartTime = ((BatchAutorouter) batchAutorouter).getSessionStartTime();
        initialUnroutedCount = ((BatchAutorouter) batchAutorouter).getInitialUnroutedCount();
        // Note: currentPassNo should come from router but we don't have a getter yet
        currentPassNo = 1; // Placeholder - actual pass count tracked in router
      } else if (batchAutorouter instanceof BatchAutorouterV19) {
        sessionStartTime = ((BatchAutorouterV19) batchAutorouter).getSessionStartTime();
        initialUnroutedCount = ((BatchAutorouterV19) batchAutorouter).getInitialUnroutedCount();
        currentPassNo = 1; // Placeholder
      }

      if (sessionStartTime != null) {
        String completionStatus = this.isStopRequested() ? "interrupted:" : "completed:";
        if (currentPassNo > routingJob.routerSettings.maxPasses) {
          completionStatus = "completed with pass number limit hit:";
        }

        String sessionSummary = String.format(
            "Auto-router session %s started with %d unrouted nets, completed in %.2f seconds, final score: %.2f (%d unrouted, %d violations).",
            completionStatus,
            initialUnroutedCount,
            autoroutingSecondsToComplete,
            scoreBeforeOptimization,
            bs.connections.incompleteCount,
            bs.clearanceViolations.totalCount);

        routingJob.logInfo(sessionSummary);
      } else {
        // Fallback to simple logging if session info not available
        routingJob.logInfo("Auto-routing was completed in " + FRLogger.formatDuration(autoroutingSecondsToComplete)
            + " with the score of " + FRLogger.formatScore(scoreBeforeOptimization,
                bs.connections.incompleteCount, bs.clearanceViolations.totalCount)
            + ".");
      }
      FRAnalytics.autorouterFinished();

      Thread.sleep(100);

      // Let's run the optimizer if it's enabled
      int num_threads = boardManager.get_num_threads();
      if ((num_threads > 0) && (routingJob.routerSettings.optimizer.enabled)) {
        routingJob
            .logInfo("Starting optimization on " + (num_threads == 1 ? "1 thread" : num_threads + " threads") + "...");
        if (num_threads > 1) {
          routingJob.logWarning(
              "Multi-threaded route optimization is broken and it is known to generate clearance violations. It is highly recommended to use the single-threaded route optimization instead by setting the number of threads to 1 with the '-mt 1' command line argument.");
        }

        FRLogger.traceEntry("BatchAutorouterThread.thread_action()-routeoptimization");
        FRAnalytics.routeOptimizerStarted();

        if (routingJob.routerSettings.getRunOptimizer() && !this.isStopRequested()) {
          String opt_message = tm.getText("batch_optimizer") + " " + tm.getText("stop_message");
          boardManager.screen_messages.set_status_message(opt_message);
          this.batchOptimizer.runBatchLoop();
          String curr_message;
          if (this.isStopRequested()) {
            curr_message = tm.getText("interrupted");
          } else {
            curr_message = tm.getText("completed");
          }
          String end_message = tm.getText("postroute") + " " + curr_message;
          boardManager.screen_messages.set_status_message(end_message);
        }

        bs = new BoardStatistics(boardManager.get_routing_board());
        var scoreAfterOptimization = bs.getNormalizedScore(routingJob.routerSettings.scoring);

        double percentage_improvement = ((scoreAfterOptimization / scoreBeforeOptimization) * 100.0) - 100.0;

        double routeOptimizationSecondsToComplete = FRLogger
            .traceExit("BatchAutorouterThread.thread_action()-routeoptimization");
        routingJob
            .logInfo("Optimization was completed in " + FRLogger.formatDuration(routeOptimizationSecondsToComplete)
                + " with the score of " + FRLogger.formatScore(scoreBeforeOptimization,
                    bs.connections.incompleteCount, bs.clearanceViolations.totalCount)
                + (percentage_improvement > 0 ? " and an improvement of " + FRLogger.defaultSignedFloatFormat.format(
                    percentage_improvement) + "%." : "."));
        FRAnalytics.routeOptimizerFinished();

        if (!this.isStopRequested()) {
          boardManager.get_panel().board_frame.delete_intermediate_stage_file();
        }
      }

      // Restore the board read-only state
      boardManager.set_board_read_only(saved_board_read_only);

      // Save the result to the output field as a Specctra SES file
      if (routingJob.output.format == FileFormat.SES) {
        // Save the SES file after the auto-router has finished
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
          if (boardManager.saveAsSpecctraSessionSes(baos, routingJob.name)) {
            routingJob.output.setData(baos.toByteArray());
          }
        } catch (Exception e) {
          routingJob.logError("Couldn't save the output into the job object.", e);
        }
      }

      // Update the ratsnest
      boardManager.update_ratsnest();
      if (!ratsnest_hidden_before) {
        boardManager
            .get_ratsnest()
            .show();
      }

      // Update the message status bar, indicating that auto-routing is completed
      boardManager.screen_messages.clear();
      String curr_message;
      if (this.isStopRequested()) {
        curr_message = tm.getText("interrupted");
      } else {
        curr_message = tm.getText("completed");
      }
      int incomplete_count = boardManager
          .get_ratsnest()
          .incomplete_count();
      String end_message = tm.getText("autoroute") + " " + curr_message + ", " + incomplete_count + " "
          + tm.getText("connections_not_found");
      boardManager.screen_messages.set_status_message(end_message);

      // Refresh the windows
      boardManager.get_panel().board_frame.refresh_windows();
      if (boardManager.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE) {
        int non45DegreeCount = boardManager.get_routing_board().getNon45DegreeTraceCount();
        if (non45DegreeCount > 1) {
          routingJob.logWarning("after autoroute: " + non45DegreeCount + " traces not 45 degree");
        }
      }
    } catch (Exception e) {
      routingJob.logError(e.getLocalizedMessage(), e);
    }

    if (this.isStopRequested()) {
      routingJob.finishedAt = Instant.now();
      routingJob.state = RoutingJobState.CANCELLED;
    } else {
      routingJob.finishedAt = Instant.now();
      routingJob.state = RoutingJobState.COMPLETED;
      globalSettings.statistics.incrementJobsCompleted();
    }

    for (ThreadActionListener hl : this.listeners) {
      if (this.isStopRequested()) {
        hl.autorouterAborted();
      } else {
        hl.autorouterFinished();
      }
    }

    FRLogger.traceExit("BatchAutorouterThread.thread_action()");
  }

  @Override
  public void draw(Graphics p_graphics) {
    // Cast to access get_air_line() which exists on both BatchAutorouter and
    // BatchAutorouterV19
    FloatLine curr_air_line = null;
    if (batchAutorouter instanceof BatchAutorouter) {
      curr_air_line = ((BatchAutorouter) batchAutorouter).get_air_line();
    } else if (batchAutorouter instanceof BatchAutorouterV19) {
      curr_air_line = ((BatchAutorouterV19) batchAutorouter).get_air_line();
    }
    if (curr_air_line != null) {
      FloatPoint[] draw_line = new FloatPoint[2];
      draw_line[0] = curr_air_line.a;
      draw_line[1] = curr_air_line.b;
      // draw the incomplete
      Color draw_color = this.boardManager.graphics_context.get_incomplete_color();
      double draw_width = Math.min(this.boardManager.get_routing_board().communication.get_resolution(Unit.MIL) * 3,
          300); // problem with low resolution on Kicad300;
      this.boardManager.graphics_context.draw(draw_line, draw_width, draw_color, p_graphics, 1);
    }

    if (this.batchOptimizer != null) {
      // draw the current optimization position
      FloatPoint current_opt_position = batchOptimizer.get_current_position();
      int radius = 10 * this.boardManager.get_routing_board().rules.get_default_trace_half_width(0);
      if (current_opt_position != null) {
        final int draw_width = 1;
        Color draw_color = this.boardManager.graphics_context.get_incomplete_color();
        FloatPoint[] draw_points = new FloatPoint[2];
        draw_points[0] = new FloatPoint(current_opt_position.x - radius, current_opt_position.y - radius);
        draw_points[1] = new FloatPoint(current_opt_position.x + radius, current_opt_position.y + radius);
        this.boardManager.graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, 1);
        draw_points[0] = new FloatPoint(current_opt_position.x + radius, current_opt_position.y - radius);
        draw_points[1] = new FloatPoint(current_opt_position.x - radius, current_opt_position.y + radius);
        this.boardManager.graphics_context.draw(draw_points, draw_width, draw_color, p_graphics, 1);
        this.boardManager.graphics_context.draw_circle(current_opt_position, radius, draw_width, draw_color, p_graphics,
            1);
      }
    }
  }
}
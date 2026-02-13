package app.freerouting.interactive;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.autoroute.BatchAutorouter;
import app.freerouting.autoroute.BatchAutorouterV19;
import app.freerouting.autoroute.BatchOptimizer;
import app.freerouting.autoroute.BatchOptimizerMultiThreaded;
import app.freerouting.autoroute.NamedAlgorithm;
import app.freerouting.autoroute.TaskState;
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
 * Interactive thread managing the combined execution of batch autorouting and route optimization.
 *
 * <p>This thread orchestrates a complete automated routing workflow in GUI mode, consisting of:
 * <ol>
 *   <li><strong>Batch Autorouting:</strong> Automatically routes all incomplete connections</li>
 *   <li><strong>Route Optimization:</strong> Post-processes routes to improve quality (if enabled)</li>
 * </ol>
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li><strong>Algorithm Selection:</strong> Supports both current and v1.9 router algorithms</li>
 *   <li><strong>Multi-threading:</strong> Can leverage multiple CPU cores for faster routing</li>
 *   <li><strong>Real-time Feedback:</strong> Updates GUI with progress, statistics, and visual indicators</li>
 *   <li><strong>Event-driven Updates:</strong> Responds to routing events to update display and job state</li>
 *   <li><strong>Optimization Variants:</strong> Single-threaded or multi-threaded optimization modes</li>
 *   <li><strong>Interruptible:</strong> User can stop the process at any time</li>
 * </ul>
 *
 * <p><strong>Workflow:</strong>
 * <pre>
 * 1. Initialize autorouter (BatchAutorouter or BatchAutorouterV19)
 * 2. Set up event listeners for GUI updates
 * 3. Initialize optimizer if enabled (BatchOptimizer or BatchOptimizerMultiThreaded)
 * 4. Run autorouting passes until completion or interruption
 * 5. Run optimization passes if enabled and not interrupted
 * 6. Update job output with SES file data
 * 7. Display completion statistics and restore board state
 * </pre>
 *
 * <p><strong>GUI Integration:</strong>
 * <ul>
 *   <li>Updates status messages showing current operation</li>
 *   <li>Displays routing statistics (via count, incomplete count, violations)</li>
 *   <li>Shows board score in real-time</li>
 *   <li>Draws current airline being routed and optimization position</li>
 *   <li>Maintains board read-only state during routing</li>
 * </ul>
 *
 * <p><strong>Algorithm Selection:</strong>
 * <ul>
 *   <li><strong>Current Algorithm:</strong> Default modern routing algorithm with latest improvements</li>
 *   <li><strong>v1.9 Algorithm:</strong> Legacy algorithm for compatibility with older designs</li>
 * </ul>
 *
 * <p><strong>Optimization Modes:</strong>
 * <ul>
 *   <li><strong>Single-threaded:</strong> Safe, reliable optimization using {@link BatchOptimizer}</li>
 *   <li><strong>Multi-threaded:</strong> Faster but may generate violations ({@link BatchOptimizerMultiThreaded})</li>
 * </ul>
 *
 * <p><strong>Event Handling:</strong>
 * The thread registers listeners for:
 * <ul>
 *   <li>{@link BoardUpdatedEvent}: Triggered after each routing/optimization iteration</li>
 *   <li>{@link TaskStateChangedEvent}: Triggered when routing phases start/stop</li>
 * </ul>
 *
 * <p><strong>Output:</strong>
 * Upon completion, generates:
 * <ul>
 *   <li>Specctra SES file with routing results</li>
 *   <li>Routing statistics and performance metrics</li>
 *   <li>Board score and quality indicators</li>
 * </ul>
 *
 * <p><strong>Performance Tracking:</strong>
 * <ul>
 *   <li>Records start/finish timestamps</li>
 *   <li>Measures autorouting and optimization durations separately</li>
 *   <li>Calculates score improvement percentage</li>
 *   <li>Logs detailed session summaries</li>
 * </ul>
 *
 * <p><strong>Known Issues:</strong>
 * <ul>
 *   <li>Multi-threaded optimization may generate clearance violations</li>
 *   <li>Single-threaded optimization recommended for production use</li>
 * </ul>
 *
 * <p><strong>TODO:</strong> This class should be deprecated in favor of a more modern
 * job scheduler architecture for better job management.
 *
 * @see InteractiveActionThread
 * @see BatchAutorouter
 * @see BatchAutorouterV19
 * @see BatchOptimizer
 * @see BatchOptimizerMultiThreaded
 * @see RoutingJob
 */
public class AutorouterAndRouteOptimizerThread extends InteractiveActionThread {

  /**
   * The batch autorouter instance executing the routing algorithm.
   *
   * <p>Can be either:
   * <ul>
   *   <li>{@link BatchAutorouter}: Current/modern routing algorithm</li>
   *   <li>{@link BatchAutorouterV19}: Legacy v1.9 algorithm for compatibility</li>
   * </ul>
   *
   * <p>Both implement {@link NamedAlgorithm} interface for consistent access.
   */
  private final NamedAlgorithm batchAutorouter;

  /**
   * The batch optimizer instance for post-routing optimization, or null if disabled.
   *
   * <p>Can be either:
   * <ul>
   *   <li>{@link BatchOptimizer}: Single-threaded, safe optimization</li>
   *   <li>{@link BatchOptimizerMultiThreaded}: Multi-threaded, faster but may create violations</li>
   * </ul>
   *
   * <p>Set to null if optimization is disabled in router settings.
   */
  private BatchOptimizer batchOptimizer;

  /**
   * Creates a new autorouter and optimizer thread for GUI-based routing.
   *
   * <p>Initialization process:
   * <ol>
   *   <li>Selects appropriate router algorithm based on settings</li>
   *   <li>Configures board references in routing job</li>
   *   <li>Registers event listeners for GUI updates</li>
   *   <li>Sets up SES file generation on routing updates</li>
   *   <li>Initializes optimizer if enabled (single or multi-threaded)</li>
   * </ol>
   *
   * <p><strong>Algorithm Selection:</strong>
   * <ul>
   *   <li>If algorithm is "v1.9": Uses {@link BatchAutorouterV19}</li>
   *   <li>Otherwise: Uses {@link BatchAutorouter} (current algorithm)</li>
   *   <li>Invalid algorithm names fall back to current with warning</li>
   * </ul>
   *
   * <p><strong>Event Listeners:</strong>
   * Sets up listeners for:
   * <ul>
   *   <li>Board updates: Updates GUI statistics, score, and display</li>
   *   <li>SES generation: Saves routing results to job output</li>
   *   <li>Task state changes: Updates status messages for phase transitions</li>
   * </ul>
   *
   * <p><strong>Optimizer Setup:</strong>
   * If optimization is enabled:
   * <ul>
   *   <li>Single thread or multi-threading disabled: Uses {@link BatchOptimizer}</li>
   *   <li>Multiple threads enabled: Uses {@link BatchOptimizerMultiThreaded}</li>
   * </ul>
   *
   * <p><strong>Warning:</strong> Multi-threaded optimization is known to potentially
   * generate clearance violations. Single-threaded mode is recommended for production.
   *
   * @param p_board_handling the GUI board manager for display updates
   * @param routingJob the routing job containing configuration and board data
   *
   * @see BatchAutorouter
   * @see BatchAutorouterV19
   * @see BatchOptimizer
   * @see BatchOptimizerMultiThreaded
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

  /**
   * Executes the complete autorouting and optimization workflow.
   *
   * <p><strong>Execution Flow:</strong>
   * <ol>
   *   <li><strong>Initialization:</strong>
   *     <ul>
   *       <li>Set job start time and state to RUNNING</li>
   *       <li>Configure thread count</li>
   *       <li>Notify listeners that autorouting started</li>
   *       <li>Set board to read-only mode</li>
   *       <li>Hide rats nest during routing</li>
   *     </ul>
   *   </li>
   *   <li><strong>Autorouting Phase:</strong>
   *     <ul>
   *       <li>Display status message</li>
   *       <li>Execute batch autorouting passes</li>
   *       <li>Track routing time and statistics</li>
   *       <li>Log session summary with initial/final counts</li>
   *       <li>Send analytics event</li>
   *     </ul>
   *   </li>
   *   <li><strong>Optimization Phase (if enabled):</strong>
   *     <ul>
   *       <li>Check if optimization is enabled and not interrupted</li>
   *       <li>Display optimization status message</li>
   *       <li>Execute optimization passes</li>
   *       <li>Calculate improvement percentage</li>
   *       <li>Log optimization results</li>
   *       <li>Send analytics event</li>
   *     </ul>
   *   </li>
   *   <li><strong>Finalization:</strong>
   *     <ul>
   *       <li>Generate SES output file if required</li>
   *       <li>Update rats nest display</li>
   *       <li>Restore board read-only state</li>
   *       <li>Display completion message with statistics</li>
   *       <li>Refresh GUI windows</li>
   *       <li>Check for non-45-degree traces if applicable</li>
   *       <li>Set job completion time and state</li>
   *       <li>Notify listeners of completion or abortion</li>
   *     </ul>
   *   </li>
   * </ol>
   *
   * <p><strong>Performance Tracking:</strong>
   * <ul>
   *   <li>Measures autorouting duration separately from optimization</li>
   *   <li>Logs detailed session summaries with routing statistics</li>
   *   <li>Calculates score improvement from optimization</li>
   *   <li>Tracks completion status (completed, interrupted, or pass limit hit)</li>
   * </ul>
   *
   * <p><strong>GUI Updates:</strong>
   * Throughout execution:
   * <ul>
   *   <li>Status messages show current phase (autorouting/optimizing)</li>
   *   <li>Board statistics display via count, incomplete count, violations</li>
   *   <li>Board score updates in real-time</li>
   *   <li>Progress indicators through event listeners</li>
   * </ul>
   *
   * <p><strong>Interruption Handling:</strong>
   * <ul>
   *   <li>Checks {@link #isStopRequested()} at key points</li>
   *   <li>Allows clean exit from autorouting phase</li>
   *   <li>Allows clean exit from optimization phase</li>
   *   <li>Sets job state to CANCELLED if interrupted</li>
   *   <li>Logs interruption status in messages</li>
   * </ul>
   *
   * <p><strong>Output Generation:</strong>
   * <ul>
   *   <li>Generates Specctra SES file with routing results</li>
   *   <li>Stores SES data in job output object</li>
   *   <li>Updates output after autorouting (via events)</li>
   *   <li>Final output update after optimization completes</li>
   * </ul>
   *
   * <p><strong>Analytics:</strong>
   * Sends the following analytics events:
   * <ul>
   *   <li>autorouterStarted: When autorouting begins</li>
   *   <li>autorouterFinished: When autorouting completes</li>
   *   <li>routeOptimizerStarted: When optimization begins</li>
   *   <li>routeOptimizerFinished: When optimization completes</li>
   * </ul>
   *
   * <p><strong>Error Handling:</strong>
   * <ul>
   *   <li>Catches all exceptions and logs them</li>
   *   <li>Ensures job state is updated even on errors</li>
   *   <li>Guarantees listeners are notified of completion</li>
   * </ul>
   *
   * @see BatchAutorouter#runBatchLoop()
   * @see BatchOptimizer#runBatchLoop()
   * @see RoutingJobState
   */
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
      if (boardManager.get_settings().autoroute_settings.getRunRouter() && !this.is_stop_auto_router_requested()) {
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
            "Auto-router session %s started with %d unrouted nets, completed in %s, final score: %s.",
            completionStatus,
            initialUnroutedCount,
            FRLogger.formatDuration(autoroutingSecondsToComplete),
            FRLogger.formatScore(scoreBeforeOptimization, bs.connections.incompleteCount,
                bs.clearanceViolations.totalCount));

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

        if (boardManager.get_settings().autoroute_settings.getRunOptimizer() && !this.isStopRequested()) {
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

  /**
   * Draws visual indicators showing current autorouting and optimization progress.
   *
   * <p>This method provides real-time visual feedback during routing operations by
   * drawing overlay graphics on the board display.
   *
   * <p><strong>Autorouting Indicator:</strong>
   * If autorouting is active, draws the current airline being processed:
   * <ul>
   *   <li><strong>Appearance:</strong> Line connecting two unconnected points</li>
   *   <li><strong>Color:</strong> Incomplete connection color from graphics context</li>
   *   <li><strong>Width:</strong> 3 mil or 300 board units (whichever is smaller)</li>
   *   <li><strong>Purpose:</strong> Shows which connection is currently being routed</li>
   * </ul>
   *
   * <p><strong>Optimization Indicator:</strong>
   * If optimization is active, draws crosshair and circle at current position:
   * <ul>
   *   <li><strong>Crosshair:</strong> Two diagonal lines (X pattern)</li>
   *   <li><strong>Circle:</strong> Surrounds the optimization point</li>
   *   <li><strong>Radius:</strong> 10× the default trace half-width</li>
   *   <li><strong>Color:</strong> Incomplete connection color</li>
   *   <li><strong>Width:</strong> 1 pixel lines</li>
   *   <li><strong>Purpose:</strong> Shows which area is being optimized</li>
   * </ul>
   *
   * <p><strong>Performance Note:</strong>
   * This method is called frequently during routing to update the display.
   * Drawing operations are kept lightweight to maintain responsive GUI.
   *
   * <p><strong>Implementation Details:</strong>
   * <ul>
   *   <li>Uses instanceof checks to access algorithm-specific methods</li>
   *   <li>Handles null cases when no airline or position is available</li>
   *   <li>Delegates actual drawing to graphics context methods</li>
   *   <li>Scales indicators based on board resolution and trace widths</li>
   * </ul>
   *
   * @param p_graphics the graphics context for rendering overlay indicators
   *
   * @see BatchAutorouter#get_air_line()
   * @see BatchAutorouterV19#get_air_line()
   * @see BatchOptimizer#get_current_position()
   */
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
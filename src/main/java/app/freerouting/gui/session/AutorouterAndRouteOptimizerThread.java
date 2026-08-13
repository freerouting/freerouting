package app.freerouting.gui.session;

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
import app.freerouting.geometry.planar.FloatLine;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.io.FileFormat;
import app.freerouting.io.specctra.SesWriter;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.ThreadActionListener;
import app.freerouting.management.analytics.FRAnalytics;
import app.freerouting.util.TextManager;
import com.sun.management.ThreadMXBean;
import java.awt.Color;
import java.awt.Graphics;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Objects;

/**
 * Interactive thread managing the combined execution of batch autorouting and route optimization.
 *
 * <p>This thread orchestrates a complete automated routing workflow in GUI mode, consisting of:
 *
 * <ol>
 *   <li><strong>Batch Autorouting:</strong> Automatically routes all incomplete connections
 *   <li><strong>Route Optimization:</strong> Post-processes routes to improve quality (if enabled)
 * </ol>
 *
 * <p><strong>Key Features:</strong>
 *
 * <ul>
 *   <li><strong>Algorithm Selection:</strong> Supports both current and v1.9 router algorithms
 *   <li><strong>Multi-threading:</strong> Can leverage multiple CPU cores for faster routing
 *   <li><strong>Real-time Feedback:</strong> Updates GUI with progress, statistics, and visual
 *       indicators
 *   <li><strong>Event-driven Updates:</strong> Responds to routing events to update display and job
 *       state
 *   <li><strong>Optimization Variants:</strong> Single-threaded or multi-threaded optimization
 *       modes
 *   <li><strong>Interruptible:</strong> User can stop the process at any time
 * </ul>
 *
 * <p><strong>Workflow:</strong>
 *
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
 *
 * <ul>
 *   <li>Updates status messages showing current operation
 *   <li>Displays routing statistics (via count, incomplete count, violations)
 *   <li>Shows board score in real-time
 *   <li>Draws current airline being routed and optimization position
 *   <li>Maintains board read-only state during routing
 * </ul>
 *
 * <p><strong>Algorithm Selection:</strong>
 *
 * <ul>
 *   <li><strong>Current Algorithm:</strong> Default modern routing algorithm with latest
 *       improvements
 *   <li><strong>v1.9 Algorithm:</strong> Legacy algorithm for compatibility with older designs
 * </ul>
 *
 * <p><strong>Optimization Modes:</strong>
 *
 * <ul>
 *   <li><strong>Single-threaded:</strong> Safe, reliable optimization using {@link BatchOptimizer}
 *   <li><strong>Multi-threaded:</strong> Faster but may generate violations ({@link
 *       BatchOptimizerMultiThreaded})
 * </ul>
 *
 * <p><strong>Event Handling:</strong> The thread registers listeners for:
 *
 * <ul>
 *   <li>{@link BoardUpdatedEvent}: Triggered after each routing/optimization iteration
 *   <li>{@link TaskStateChangedEvent}: Triggered when routing stages start/stop
 * </ul>
 *
 * <p><strong>Output:</strong> Upon completion, generates:
 *
 * <ul>
 *   <li>Specctra SES file with routing results
 *   <li>Routing statistics and performance metrics
 *   <li>Board score and quality indicators
 * </ul>
 *
 * <p><strong>Performance Tracking:</strong>
 *
 * <ul>
 *   <li>Records start/finish timestamps
 *   <li>Measures autorouting and optimization durations separately
 *   <li>Calculates score improvement percentage
 *   <li>Logs detailed session summaries
 * </ul>
 *
 * <p><strong>Known Issues:</strong>
 *
 * <ul>
 *   <li>Multi-threaded optimization may generate clearance violations
 *   <li>Single-threaded optimization recommended for production use
 * </ul>
 *
 * <p><strong>TODO:</strong> This class should be deprecated in favor of a more modern job scheduler
 * architecture for better job management.
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
   *
   * <ul>
   *   <li>{@link BatchAutorouter}: Current/modern routing algorithm
   *   <li>{@link BatchAutorouterV19}: Legacy v1.9 algorithm for compatibility
   * </ul>
   *
   * <p>Both implement {@link NamedAlgorithm} interface for consistent access.
   */
  private final NamedAlgorithm batchAutorouter;

  /**
   * The batch optimizer instance for post-routing optimization, or null if disabled.
   *
   * <p>Can be either:
   *
   * <ul>
   *   <li>{@link BatchOptimizer}: Single-threaded, safe optimization
   *   <li>{@link BatchOptimizerMultiThreaded}: Multi-threaded, faster but may create violations
   * </ul>
   *
   * <p>Set to null if optimization is disabled in router settings.
   */
  private BatchOptimizer batchOptimizer;

  /**
   * Creates a new autorouter and optimizer thread for GUI-based routing.
   *
   * <p>Initialization process:
   *
   * <ol>
   *   <li>Selects appropriate router algorithm based on settings
   *   <li>Configures board references in routing job
   *   <li>Registers event listeners for GUI updates
   *   <li>Sets up SES file generation on routing updates
   *   <li>Initializes optimizer if enabled (single or multi-threaded)
   * </ol>
   *
   * <p><strong>Algorithm Selection:</strong>
   *
   * <ul>
   *   <li>If algorithm is "v1.9": Uses {@link BatchAutorouterV19}
   *   <li>Otherwise: Uses {@link BatchAutorouter} (current algorithm)
   *   <li>Invalid algorithm names fall back to current with warning
   * </ul>
   *
   * <p><strong>Event Listeners:</strong> Sets up listeners for:
   *
   * <ul>
   *   <li>Board updates: Updates GUI statistics, score, and display
   *   <li>SES generation: Saves routing results to job output
   *   <li>Task state changes: Updates status messages for stage transitions
   * </ul>
   *
   * <p><strong>Optimizer Setup:</strong> If optimization is enabled:
   *
   * <ul>
   *   <li>Single thread or multi-threading disabled: Uses {@link BatchOptimizer}
   *   <li>Multiple threads enabled: Uses {@link BatchOptimizerMultiThreaded}
   * </ul>
   *
   * <p><strong>Warning:</strong> Multi-threaded optimization is known to potentially generate
   * clearance violations. Single-threaded mode is recommended for production.
   *
   * @param boardHandling the GUI board manager for display updates
   * @param routingJob the routing job containing configuration and board data
   * @see BatchAutorouter
   * @see BatchAutorouterV19
   * @see BatchOptimizer
   * @see BatchOptimizerMultiThreaded
   */
  protected AutorouterAndRouteOptimizerThread(
      GuiBoardManager boardHandling, RoutingJob routingJob) {
    super(boardHandling, routingJob);

    routingJob.thread = this;
    routingJob.board = boardHandling.getRoutingBoard();

    // Select the appropriate router algorithm based on settings
    String algorithm = routingJob.routerSettings.algorithm;
    if (app.freerouting.settings.RouterSettings.ALGORITHM_V19.equals(algorithm)) {
      routingJob.logInfo("Using v1.9 router algorithm: " + algorithm);
      this.batchAutorouter = new BatchAutorouterV19(routingJob);
    } else {
      if (!app.freerouting.settings.RouterSettings.ALGORITHM_CURRENT.equals(algorithm)) {
        routingJob.logWarning(
            "The algorithm '"
                + algorithm
                + "' is not supported. The default algorithm '"
                + app.freerouting.settings.RouterSettings.ALGORITHM_CURRENT
                + "' will be used instead.");
        routingJob.routerSettings.algorithm =
            app.freerouting.settings.RouterSettings.ALGORITHM_CURRENT;
      }
      this.batchAutorouter = new BatchAutorouter(routingJob);
    }

    // Add event listener for the GUI updates
    this.batchAutorouter.addBoardUpdatedEventListener(
        new BoardUpdatedEventListener() {
          @Override
          public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
            float boardScore =
                event.getBoardStatistics().getNormalizedScore(routingJob.routerSettings.scoring);

            if (event.getRouterCounters() != null
                && "fanout".equals(event.getRouterCounters().phase)) {
              int extraVias =
                  event.getRouterCounters().fanoutExtraViasCount == null
                      ? 0
                      : event.getRouterCounters().fanoutExtraViasCount;
              boardManager.screenMessages.setStatusMessage(
                  "Fanout pass #"
                      + event.getRouterCounters().passCount
                      + " (routed "
                      + event.getRouterCounters().routedCount
                      + ", failed "
                      + event.getRouterCounters().failedToBeRoutedCount
                      + ", +"
                      + extraVias
                      + " vias)");
            }

            boardManager.screenMessages.setBatchAutorouteInfo(event.getRouterCounters());
            boardManager.screenMessages.setBoardScore(
                boardScore,
                event.getBoardStatistics().connections.incompleteCount,
                event.getBoardStatistics().clearanceViolations.totalCount);
            boardManager.repaint();
          }
        });

    // Add another event listener for the job output object updates
    this.batchAutorouter.addBoardUpdatedEventListener(
        new BoardUpdatedEventListener() {
          @Override
          public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
              SesWriter.write(boardManager.getRoutingBoard(), outputStream, routingJob.name);
              routingJob.output.setData(outputStream.toByteArray());
            } catch (Exception e) {
              routingJob.logError("Couldn't save the SES output into the job object.", e);
            }
          }
        });

    this.batchAutorouter.addTaskStateChangedEventListener(
        new TaskStateChangedEventListener() {
          @Override
          public void onTaskStateChangedEvent(TaskStateChangedEvent event) {
            TaskState taskState = event.getTaskState();
            if (taskState == TaskState.RUNNING) {
              TextManager tm = new TextManager(ScreenMessages.class, boardManager.getLocale());
              String startMessage =
                  tm.getText("autorouter_started", Integer.toString(event.getPassNumber()));
              boardManager.screenMessages.setStatusMessage(startMessage);
            }
          }
        });

    this.batchOptimizer = null;

    if (routingJob.routerSettings.optimizer.enabled) {
      if ((!globalSettings.featureFlags.multiThreading)
          || (routingJob.routerSettings.optimizer.maxThreads == 1)) {
        // Single-threaded route optimization
        this.batchOptimizer = new BatchOptimizer(routingJob);

        if (!Objects.equals(
            routingJob.routerSettings.optimizer.algorithm, this.batchOptimizer.getId())) {
          routingJob.logWarning(
              "The algorithm '"
                  + routingJob.routerSettings.optimizer.algorithm
                  + "' is not supported by the batch autorouter. The default algorithm '"
                  + this.batchOptimizer.getId()
                  + "' will be used instead.");
          routingJob.routerSettings.optimizer.algorithm = this.batchOptimizer.getId();
        }

        // Add event listener for the GUI updates
        this.batchOptimizer.addBoardUpdatedEventListener(
            new BoardUpdatedEventListener() {
              @Override
              public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
                BoardStatistics boardStatistics = event.getBoardStatistics();
                boardManager.screenMessages.setPostRouteInfo(
                    boardStatistics.items.viaCount,
                    boardStatistics.traces.totalLength,
                    boardManager.coordinateTransform.userUnit);
                boardManager.screenMessages.setBoardScore(
                    boardStatistics.getNormalizedScore(routingJob.routerSettings.scoring),
                    boardStatistics.connections.incompleteCount,
                    boardStatistics.clearanceViolations.totalCount);
                boardManager.repaint();
              }
            });

        this.batchOptimizer.addTaskStateChangedEventListener(
            new TaskStateChangedEventListener() {
              @Override
              public void onTaskStateChangedEvent(TaskStateChangedEvent event) {
                TaskState taskState = event.getTaskState();
                if (taskState == TaskState.RUNNING) {
                  TextManager tm = new TextManager(ScreenMessages.class, boardManager.getLocale());
                  String startMessage =
                      tm.getText("optimizer_started", Integer.toString(event.getPassNumber()));
                  boardManager.screenMessages.setStatusMessage(startMessage);
                }
              }
            });
      }

      if ((globalSettings.featureFlags.multiThreading)
          && (routingJob.routerSettings.optimizer.maxThreads > 1)) {
        // Multi-threaded route optimization
        this.batchOptimizer = new BatchOptimizerMultiThreaded(routingJob);

        if (!Objects.equals(
            routingJob.routerSettings.optimizer.algorithm, this.batchOptimizer.getId())) {
          routingJob.logWarning(
              "The algorithm '"
                  + routingJob.routerSettings.optimizer.algorithm
                  + "' is not supported by the batch autorouter. The default algorithm '"
                  + this.batchOptimizer.getId()
                  + "' will be used instead.");
          routingJob.routerSettings.optimizer.algorithm = this.batchOptimizer.getId();
        }

        this.batchOptimizer.addBoardUpdatedEventListener(
            new BoardUpdatedEventListener() {
              @Override
              public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
                BoardStatistics boardStatistics = event.getBoardStatistics();
                boardManager.replaceRoutingBoard(event.getBoard());
                boardManager.screenMessages.setPostRouteInfo(
                    boardStatistics.items.viaCount,
                    boardStatistics.traces.totalLength,
                    boardManager.coordinateTransform.userUnit);
              }
            });

        this.batchOptimizer.addTaskStateChangedEventListener(
            new TaskStateChangedEventListener() {
              @Override
              public void onTaskStateChangedEvent(TaskStateChangedEvent event) {
                TaskState taskState = event.getTaskState();
                if (taskState == TaskState.RUNNING) {
                  TextManager tm = new TextManager(ScreenMessages.class, boardManager.getLocale());
                  String startMessage =
                      tm.getText("optimizer_started", Integer.toString(event.getPassNumber()));
                  boardManager.screenMessages.setStatusMessage(startMessage);
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
   *
   * <ol>
   *   <li><strong>Initialization:</strong>
   *       <ul>
   *         <li>Set job start time and state to RUNNING
   *         <li>Configure thread count
   *         <li>Notify listeners that autorouting started
   *         <li>Set board to read-only mode
   *         <li>Hide rats nest during routing
   *       </ul>
   *   <li><strong>Auto-routing Stage:</strong>
   *       <ul>
   *         <li>Display status message
   *         <li>Execute batch autorouting passes
   *         <li>Track routing time and statistics
   *         <li>Log session summary with initial/final counts
   *         <li>Send analytics event
   *       </ul>
   *   <li><strong>Optimization Stage (if enabled):</strong>
   *       <ul>
   *         <li>Check if optimization is enabled and not interrupted
   *         <li>Display optimization status message
   *         <li>Execute optimization passes
   *         <li>Calculate improvement percentage
   *         <li>Log optimization results
   *         <li>Send analytics event
   *       </ul>
   *   <li><strong>Finalization:</strong>
   *       <ul>
   *         <li>Generate SES output file if required
   *         <li>Update rats nest display
   *         <li>Restore board read-only state
   *         <li>Display completion message with statistics
   *         <li>Refresh GUI windows
   *         <li>Check for non-45-degree traces if applicable
   *         <li>Set job completion time and state
   *         <li>Notify listeners of completion or abortion
   *       </ul>
   * </ol>
   *
   * <p><strong>Performance Tracking:</strong>
   *
   * <ul>
   *   <li>Measures autorouting duration separately from optimization
   *   <li>Logs detailed session summaries with routing statistics
   *   <li>Calculates score improvement from optimization
   *   <li>Tracks completion status (completed, interrupted, or pass limit hit)
   * </ul>
   *
   * <p><strong>GUI Updates:</strong> Throughout execution:
   *
   * <ul>
   *   <li>Status messages show current stage (auto-routing/optimizing)
   *   <li>Board statistics display via count, incomplete count, violations
   *   <li>Board score updates in real-time
   *   <li>Progress indicators through event listeners
   * </ul>
   *
   * <p><strong>Interruption Handling:</strong>
   *
   * <ul>
   *   <li>Checks {@link #isStopRequested()} at key points
   *   <li>Allows clean exit from auto-routing stage
   *   <li>Allows clean exit from optimization stage
   *   <li>Sets job state to CANCELLED if interrupted
   *   <li>Logs interruption status in messages
   * </ul>
   *
   * <p><strong>Output Generation:</strong>
   *
   * <ul>
   *   <li>Generates Specctra SES file with routing results
   *   <li>Stores SES data in job output object
   *   <li>Updates output after autorouting (via events)
   *   <li>Final output update after optimization completes
   * </ul>
   *
   * <p><strong>Analytics:</strong> Sends the following analytics events:
   *
   * <ul>
   *   <li>autorouterStarted: When autorouting begins
   *   <li>autorouterFinished: When autorouting completes
   *   <li>routeOptimizerStarted: When optimization begins
   *   <li>routeOptimizerFinished: When optimization completes
   * </ul>
   *
   * <p><strong>Error Handling:</strong>
   *
   * <ul>
   *   <li>Catches all exceptions and logs them
   *   <li>Ensures job state is updated even on errors
   *   <li>Guarantees listeners are notified of completion
   * </ul>
   *
   * @see BatchAutorouter#runBatchLoop()
   * @see BatchOptimizer#runBatchLoop()
   * @see RoutingJobState
   */
  @Override
  protected void threadAction() {
    routingJob.startedAt = Instant.now();
    routingJob.state = RoutingJobState.RUNNING;
    boardManager.setNumThreads(routingJob.routerSettings.maxThreads);

    // Start a background thread that periodically samples CPU time, total allocated
    // memory, and peak heap usage — mirroring the headless RoutingJobSchedulerActionThread.
    Thread resourceMonitor =
        new Thread(
            () -> {
              while (routingJob.state == app.freerouting.core.RoutingJobState.RUNNING
                  || routingJob.state == app.freerouting.core.RoutingJobState.STOPPING) {
                try {
                  Thread.sleep(1000);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  break;
                }
                monitorCpuAndMemoryUsage(routingJob);
              }
            });
    resourceMonitor.setDaemon(true);
    resourceMonitor.setName("resource-monitor-gui");
    resourceMonitor.start();

    for (ThreadActionListener hl : this.listeners) {
      hl.autorouterStarted();
    }

    FRLogger.traceEntry("BatchAutorouterThread.thread_action()");
    try {
      final boolean savedBoardReadOnly = boardManager.isBoardReadOnly();
      boardManager.setBoardReadOnly(true);
      boolean ratsnestHiddenBefore = boardManager.getRatsnest().isHidden();
      if (!ratsnestHiddenBefore) {
        boardManager.getRatsnest().hide();
      }

      boolean isRouterEnabled =
          routingJob.routerSettings.getRunRouter()
              && (routingJob.routerSettings.maxPasses == null
                  || routingJob.routerSettings.maxPasses >= 0);
      if (isRouterEnabled) {
        int threadCount = routingJob.routerSettings.maxThreads;
        routingJob.logInfo(
            "Starting routing of '"
                + routingJob.name
                + "' on "
                + (threadCount == 1 ? "1 thread" : threadCount + " threads")
                + "...");
      } else if (routingJob.routerSettings.isFanoutEnabled()) {
        routingJob.logInfo("Starting fanout of '" + routingJob.name + "'...");
      }
      FRLogger.traceEntry("BatchAutorouterThread.thread_action()-autorouting");

      globalSettings.statistics.incrementJobsCompleted();
      FRAnalytics.autorouterStarted();

      TextManager tm = new TextManager(ScreenMessages.class, boardManager.getLocale());
      String startMessage = tm.getText("batch_autorouter_start_message");
      boardManager.screenMessages.setStatusMessage(startMessage);

      // Let's run the autorouter
      if (isRouterEnabled && !this.isStopAutoRouterRequested()) {
        // Cast to access runBatchLoop() which exists on both BatchAutorouter and
        // BatchAutorouterV19
        if (batchAutorouter instanceof BatchAutorouter) {
          ((BatchAutorouter) batchAutorouter).runBatchLoop();
        } else if (batchAutorouter instanceof BatchAutorouterV19) {
          ((BatchAutorouterV19) batchAutorouter).runBatchLoop();
        }
      } else if (routingJob.routerSettings.isFanoutEnabled() && !this.isStopAutoRouterRequested()) {
        // Run only the fanout pre-pass
        Integer originalMaxPasses = routingJob.routerSettings.maxPasses;
        try {
          routingJob.routerSettings.maxPasses = 0;
          if (batchAutorouter instanceof BatchAutorouter) {
            ((BatchAutorouter) batchAutorouter).runBatchLoop();
          } else if (batchAutorouter instanceof BatchAutorouterV19) {
            ((BatchAutorouterV19) batchAutorouter).runBatchLoop();
          }
        } finally {
          routingJob.routerSettings.maxPasses = originalMaxPasses;
        }
      }

      boardManager.replaceRoutingBoard(routingJob.board);

      boardManager.getRoutingBoard().finishAutoroute();

      var bs = new BoardStatistics(boardManager.getRoutingBoard());
      var scoreBeforeOptimization = bs.getNormalizedScore(routingJob.routerSettings.scoring);

      double autoroutingSecondsToComplete =
          FRLogger.traceExit("BatchAutorouterThread.thread_action()-autorouting");

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

      if (isRouterEnabled) {
        if (sessionStartTime != null) {
          String completionStatus = this.isStopRequested() ? "interrupted:" : "completed:";
          if (routingJob.routerSettings.maxPasses != null
              && routingJob.routerSettings.maxPasses > 0
              && currentPassNo > routingJob.routerSettings.maxPasses) {
            completionStatus = "completed with pass number limit hit:";
          }

          String sessionSummary =
              String.format(
                  java.util.Locale.US,
                  "Auto-routing stage %s started with %d unrouted nets, completed in %.2f seconds, "
                      + "final score: %s, using %.2f total CPU seconds, %.2f GB total allocated, "
                      + "and %.1f MB peak heap usage.",
                  completionStatus,
                  initialUnroutedCount,
                  autoroutingSecondsToComplete,
                  FRLogger.formatScore(
                      scoreBeforeOptimization,
                      bs.connections.incompleteCount,
                      bs.clearanceViolations.totalCount),
                  routingJob.resourceUsage.cpuTimeUsed,
                  routingJob.resourceUsage.maxMemoryUsed / 1024.0f,
                  routingJob.resourceUsage.peakMemoryUsed);

          routingJob.logInfo(sessionSummary);
        } else {
          // Fallback to simple logging if session info not available
          routingJob.logInfo(
              String.format(
                  "Auto-routing was completed in %.2f seconds with the score of %s.",
                  autoroutingSecondsToComplete,
                  FRLogger.formatScore(
                      scoreBeforeOptimization,
                      bs.connections.incompleteCount,
                      bs.clearanceViolations.totalCount)));
        }
      }
      FRAnalytics.autorouterFinished(
          bs.nets.totalCount,
          bs.connections.incompleteCount,
          bs.clearanceViolations.totalCount,
          boardManager.getRoutingBoard().getHash(),
          scoreBeforeOptimization);

      Thread.sleep(100);

      // Let's run the optimizer if it's enabled
      int numThreads = boardManager.getNumThreads();
      if ((numThreads > 0) && (routingJob.routerSettings.optimizer.enabled)) {
        routingJob.logInfo(
            "Starting optimization on "
                + (numThreads == 1 ? "1 thread" : numThreads + " threads")
                + "...");
        if (numThreads > 1) {
          routingJob.logWarning(
              "Multi-threaded route optimization is broken and it is known to generate clearance "
                  + "violations. It is highly recommended to use the single-threaded route "
                  + "optimization instead by setting the number of threads to 1 with the '-mt 1' "
                  + "command line argument.");
        }

        FRLogger.traceEntry("BatchAutorouterThread.thread_action()-routeoptimization");
        FRAnalytics.routeOptimizerStarted();

        if (routingJob.routerSettings.getRunOptimizer() && !this.isStopRequested()) {
          String optMessage = tm.getText("batch_optimizer_start_message");
          boardManager.screenMessages.setStatusMessage(optMessage);
          this.batchOptimizer.runBatchLoop();
          String currMessage;
          if (this.isStopRequested()) {
            currMessage = tm.getText("interrupted");
          } else {
            currMessage = tm.getText("completed");
          }
          String endMessage = tm.getText("optimization_end_message", currMessage);
          boardManager.screenMessages.setStatusMessage(endMessage);
        }

        bs = new BoardStatistics(boardManager.getRoutingBoard());
        var scoreAfterOptimization = bs.getNormalizedScore(routingJob.routerSettings.scoring);

        double percentageImprovement =
            ((scoreAfterOptimization / scoreBeforeOptimization) * 100.0) - 100.0;
        double routeOptimizationSecondsToComplete =
            FRLogger.traceExit("BatchAutorouterThread.thread_action()-routeoptimization");
        routingJob.logInfo(
            "Optimization was completed in "
                + FRLogger.formatDuration(routeOptimizationSecondsToComplete)
                + " with the score of "
                + FRLogger.formatScore(
                    scoreBeforeOptimization,
                    bs.connections.incompleteCount,
                    bs.clearanceViolations.totalCount)
                + (percentageImprovement > 0
                    ? " and an improvement of "
                        + FRLogger.defaultSignedFloatFormat.format(percentageImprovement)
                        + "%."
                    : "."));
        FRAnalytics.routeOptimizerFinished();
      }

      // Restore the board read-only state
      boardManager.setBoardReadOnly(savedBoardReadOnly);

      // Save the result to the output field as a Specctra SES file
      if (routingJob.output.format == FileFormat.SES) {
        // Save the SES file after the auto-router has finished
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
          if (boardManager.saveAsSpecctraSessionSes(baos, routingJob.name)) {
            routingJob.output.setData(baos.toByteArray());
            FRAnalytics.fileSaved("SES", routingJob.name);
          }
        } catch (Exception e) {
          routingJob.logError("Couldn't save the output into the job object.", e);
        }
      }

      // Update the ratsnest
      boardManager.updateRatsnest();
      if (!ratsnestHiddenBefore) {
        boardManager.getRatsnest().show();
      }

      // Update the message status bar, indicating that auto-routing is completed
      boardManager.screenMessages.clear();
      String currMessage;
      if (this.isStopRequested()) {
        currMessage = tm.getText("interrupted");
      } else {
        currMessage = tm.getText("completed");
      }
      int incompleteCount = boardManager.getRatsnest().incompleteCount();
      String endMessage =
          tm.getText("autoroute_end_message", currMessage, Integer.toString(incompleteCount));
      boardManager.screenMessages.setStatusMessage(endMessage);

      // Refresh the windows
      boardManager.getPanel().boardFrame.refreshWindows();
      if (boardManager.getRoutingBoard().rules.getTraceAngleRestriction()
          == AngleRestriction.FORTYFIVE_DEGREE) {
        int non45DegreeCount = boardManager.getRoutingBoard().getNon45DegreeTraceCount();
        if (non45DegreeCount > 1) {
          routingJob.logWarning(
              "Invalid traces after autoroute: " + non45DegreeCount + " traces not 45 degree");
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
   * Samples CPU time, total allocated memory, and peak heap usage for the current routing job
   * thread and updates {@code routingJob.resourceUsage}. Mirrors the implementation in {@link
   * app.freerouting.management.RoutingJobSchedulerActionThread}.
   */
  private void monitorCpuAndMemoryUsage(app.freerouting.core.RoutingJob job) {
    try {
      ThreadMXBean threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
      long[] threadIds = threadBean.getAllThreadIds();
      for (long threadId : threadIds) {
        if (job.thread != null && threadId == job.thread.threadId()) {
          float cpuTime = threadBean.getThreadCpuTime(threadId) / 1_000_000_000.0f;
          threadBean.setThreadAllocatedMemoryEnabled(true);
          long allocatedMemory = threadBean.getThreadAllocatedBytes(threadId);
          float allocatedMegabytes = allocatedMemory / (1024.0f * 1024.0f);
          job.resourceUsage.cpuTimeUsed = cpuTime;
          job.resourceUsage.maxMemoryUsed = allocatedMegabytes;
        }
      }
      java.lang.management.MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
      float heapUsedMegabytes = memoryBean.getHeapMemoryUsage().getUsed() / (1024.0f * 1024.0f);
      if (heapUsedMegabytes > job.resourceUsage.peakMemoryUsed) {
        job.resourceUsage.peakMemoryUsed = heapUsedMegabytes;
      }
    } catch (Throwable t) {
      // java.management or jdk.management module not available in this JRE build
    }
  }

  /**
   * Draws visual indicators showing current autorouting and optimization progress.
   *
   * <p>This method provides real-time visual feedback during routing operations by drawing overlay
   * graphics on the board display.
   *
   * <p><strong>Autorouting Indicator:</strong> If autorouting is active, draws the current airline
   * being processed:
   *
   * <ul>
   *   <li><strong>Appearance:</strong> Line connecting two unconnected points
   *   <li><strong>Color:</strong> Incomplete connection color from graphics context
   *   <li><strong>Width:</strong> 3 mil or 300 board units (whichever is smaller)
   *   <li><strong>Purpose:</strong> Shows which connection is currently being routed
   * </ul>
   *
   * <p><strong>Optimization Indicator:</strong> If optimization is active, draws crosshair and
   * circle at current position:
   *
   * <ul>
   *   <li><strong>Crosshair:</strong> Two diagonal lines (X pattern)
   *   <li><strong>Circle:</strong> Surrounds the optimization point
   *   <li><strong>Radius:</strong> 10× the default trace half-width
   *   <li><strong>Color:</strong> Incomplete connection color
   *   <li><strong>Width:</strong> 1 pixel lines
   *   <li><strong>Purpose:</strong> Shows which area is being optimized
   * </ul>
   *
   * <p><strong>Performance Note:</strong> This method is called frequently during routing to update
   * the display. Drawing operations are kept lightweight to maintain responsive GUI.
   *
   * <p><strong>Implementation Details:</strong>
   *
   * <ul>
   *   <li>Uses instanceof checks to access algorithm-specific methods
   *   <li>Handles null cases when no airline or position is available
   *   <li>Delegates actual drawing to graphics context methods
   *   <li>Scales indicators based on board resolution and trace widths
   * </ul>
   *
   * @param graphics the graphics context for rendering overlay indicators
   * @see BatchAutorouter#getAirLine()
   * @see BatchAutorouterV19#getAirLine()
   * @see BatchOptimizer#getCurrentPosition()
   */
  @Override
  public void draw(Graphics graphics) {
    // Cast to access get_air_line() which exists on both BatchAutorouter and
    // BatchAutorouterV19
    FloatLine currAirLine = null;
    if (batchAutorouter instanceof BatchAutorouter) {
      currAirLine = ((BatchAutorouter) batchAutorouter).getAirLine();
    } else if (batchAutorouter instanceof BatchAutorouterV19) {
      currAirLine = ((BatchAutorouterV19) batchAutorouter).getAirLine();
    }
    if (currAirLine != null) {
      FloatPoint[] drawLine = new FloatPoint[2];
      drawLine[0] = currAirLine.a;
      drawLine[1] = currAirLine.b;
      // draw the incomplete
      Color drawColor = this.boardManager.graphicsContext.getIncompleteColor();
      double drawWidth =
          Math.min(
              this.boardManager.getRoutingBoard().communication.getResolution(Unit.MIL) * 3,
              300); // problem with low resolution on Kicad300;
      this.boardManager.graphicsContext.draw(drawLine, drawWidth, drawColor, graphics, 1);
    }

    if (this.batchOptimizer != null) {
      // draw the current optimization position
      FloatPoint currentOptPosition = batchOptimizer.getCurrentPosition();
      int radius = 10 * this.boardManager.getRoutingBoard().rules.getDefaultTraceHalfWidth(0);
      if (currentOptPosition != null) {
        final int drawWidth = 1;
        Color drawColor = this.boardManager.graphicsContext.getIncompleteColor();
        FloatPoint[] drawPoints = new FloatPoint[2];
        drawPoints[0] =
            new FloatPoint(currentOptPosition.x - radius, currentOptPosition.y - radius);
        drawPoints[1] =
            new FloatPoint(currentOptPosition.x + radius, currentOptPosition.y + radius);
        this.boardManager.graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, 1);
        drawPoints[0] =
            new FloatPoint(currentOptPosition.x + radius, currentOptPosition.y - radius);
        drawPoints[1] =
            new FloatPoint(currentOptPosition.x - radius, currentOptPosition.y + radius);
        this.boardManager.graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, 1);
        this.boardManager.graphicsContext.drawCircle(
            currentOptPosition, radius, drawWidth, drawColor, graphics, 1);
      }
    }
  }
}

package app.freerouting.management;

import app.freerouting.Freerouting;
import app.freerouting.autoroute.BatchAutorouter;
import app.freerouting.autoroute.BatchOptimizer;
import app.freerouting.autoroute.NamedAlgorithm;
import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.core.BoardFileDetails;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.RoutingStage;
import app.freerouting.core.StoppableThread;
import app.freerouting.gui.FileFormat;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.RouterSettings;
import com.sun.management.ThreadMXBean;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.time.Instant;

/**
 * Used for running an action in a separate thread, that can be stopped by the
 * user. This typically represents an action that is triggered by job scheduler
 */
public class RoutingJobSchedulerActionThread extends StoppableThread {

  private final long MAX_TIMEOUT = 24 * 60 * 60; // 24 hours
  private final int GRACE_PERIOD = 30; // 30 seconds
  RoutingJob job;

  public RoutingJobSchedulerActionThread(RoutingJob job) {
    this.job = job;
  }

  @Override
  protected void thread_action() {
    job.startedAt = Instant.now();
    // Use ISO standard time format
    job.logInfo("Job '" + job.shortName + "' started at " + job.startedAt.toString() + ".");

    // check if we need to check for timeout
    Long timeout = TextManager.parseTimespanString(job.routerSettings.jobTimeoutString);
    if (timeout != null) {
      // maximize the timeout to 24 hours
      if (timeout > MAX_TIMEOUT) {
        timeout = MAX_TIMEOUT;
      }

      job.timeoutAt = job.startedAt.plusSeconds(timeout);
    }

    // Start a new thread that will monitor the job thread
    new Thread(() -> {
      while ((job != null) && (job.thread != null)) {

        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        if (job.state == RoutingJobState.RUNNING || job.state == RoutingJobState.STOPPING) {
          // Get the CPU time and memory usage of the job thread
          this.monitorCpuAndMemoryUsage(job);

          // Check for timeout
          if (!Instant
              .now()
              .isBefore(job.timeoutAt)) {

            // signal the job thread to stop, and wait gracefully for up to 30 seconds for
            // it
            job.thread.requestStop();
            while ((job.state == RoutingJobState.RUNNING) && Instant
                .now()
                .isBefore(job.timeoutAt.plusSeconds(GRACE_PERIOD))) {
              try {
                Thread.sleep(1000);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
            job.state = RoutingJobState.TIMED_OUT;
          }
        }
      }
    }).start();

    // start the routing task if needed
    if (job.routerSettings.getRunRouter()) {
      job.stage = RoutingStage.ROUTING;

      // Select router implementation based on algorithm setting
      NamedAlgorithm router;
      String algorithm = job.routerSettings.algorithm;

      if (!RouterSettings.ALGORITHM_CURRENT.equals(algorithm)) {
        job.logInfo("Unknown router algorithm '" + algorithm + "', using default (freerouting-router)");
      }
      // Always use standard BatchAutorouter
      router = new BatchAutorouter(job);
      BatchAutorouter batchRouter = (BatchAutorouter) router;

      router.addBoardUpdatedEventListener(new BoardUpdatedEventListener() {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
          setJobOutputToSpecctraSes(job);
        }
      });

      // Call runBatchLoop
      batchRouter.runBatchLoop();

      // Log session summary
      Instant sessionStartTime = batchRouter.getSessionStartTime();
      int initialUnroutedCount = batchRouter.getInitialUnroutedCount();

      if (sessionStartTime != null) {
        Instant sessionEndTime = Instant.now();
        long totalSeconds = java.time.Duration.between(sessionStartTime, sessionEndTime).getSeconds();
        double totalTime = totalSeconds
            + (java.time.Duration.between(sessionStartTime, sessionEndTime).getNano() / 1000000000.0);

        var finalStats = job.board.get_statistics();

        String completionStatus = "completed:";
        // Check for timeout explicitly because job.state might not be updated to
        // TIMED_OUT yet due to race conditions
        boolean isTimedOut = (job.state == RoutingJobState.TIMED_OUT) ||
            ((job.timeoutAt != null) && !Instant.now().isBefore(job.timeoutAt) && job.thread.isStopRequested());

        if (isTimedOut) {
          completionStatus = "completed with timeout:";
        } else if (job.thread.isStopRequested()) {
          completionStatus = "interrupted:";
          if (job.isCancelledByUser()) {
            completionStatus = "cancelled:";
          }
        }

        String sessionSummary = String.format(
            "Auto-router session %s started with %d unrouted nets, completed in %s, final score: %s, using %s total CPU seconds, %s GB total allocated, and %s MB peak heap usage.",
            completionStatus,
            initialUnroutedCount,
            FRLogger.formatDuration(totalTime),
            FRLogger.formatScore(finalStats.getNormalizedScore(job.routerSettings.scoring),
                finalStats.connections.incompleteCount, finalStats.clearanceViolations.totalCount),
            FRLogger.defaultFloatFormat.format(job.resourceUsage.cpuTimeUsed),
            FRLogger.defaultFloatFormat.format(job.resourceUsage.maxMemoryUsed / 1024.0f),
            FRLogger.defaultFloatFormat.format(job.resourceUsage.peakMemoryUsed));

        job.logInfo(sessionSummary);
      }

      job.stage = RoutingStage.IDLE;
    }

    if (job.routerSettings.getRunOptimizer()) {
      job.stage = RoutingStage.OPTIMIZATION;
      // start the optimizer task
      BatchOptimizer optimizer = new BatchOptimizer(job);
      optimizer.addBoardUpdatedEventListener(new BoardUpdatedEventListener() {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event) {
          setJobOutputToSpecctraSes(job);
        }
      });
      optimizer.runBatchLoop();
      job.stage = RoutingStage.IDLE;
    }

    job.finishedAt = Instant.now();
    if (job.state == RoutingJobState.RUNNING) {
      job.state = RoutingJobState.COMPLETED;
      Freerouting.globalSettings.statistics.incrementJobsCompleted();
    } else if (job.state == RoutingJobState.STOPPING) {
      if (job.isCancelledByUser()) {
        job.state = RoutingJobState.CANCELLED;
      } else {
        job.state = RoutingJobState.COMPLETED;
      }
    }
  }

  private void monitorCpuAndMemoryUsage(RoutingJob job) {
    // Get the ThreadMXBean instance and cast it to com.sun.management.ThreadMXBean
    ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    // Get all live thread IDs
    long[] threadIds = threadMXBean.getAllThreadIds();

    // Iterate through the thread IDs and get memory usage
    for (long threadId : threadIds) {
      if (threadId == job.thread.threadId()) {
        // CPU time and memory usage
        float cpuTime = threadMXBean.getThreadCpuTime(threadId) / 1000.0f / 1000.0f / 1000.0f;

        // Enable thread memory allocation measurement
        threadMXBean.setThreadAllocatedMemoryEnabled(true);

        // Get the thread's allocated memory in bytes
        long allocatedMemory = threadMXBean.getThreadAllocatedBytes(threadId);
        float allocatedMB = allocatedMemory / (1024.0f * 1024.0f);

        // Update the job's resource usage
        // Fix: Use assignment instead of accumulation for total time, as
        // getThreadCpuTime returns cumulative time
        // Note: This only tracks the main thread. Worker threads add their stats
        // separately.
        job.resourceUsage.cpuTimeUsed = cpuTime;
        // Fix: maxMemoryUsed represents total allocated bytes here, so we accumulate if
        // we track partials,
        // but here it tracks the monotonically increasing allocation of the main
        // thread.
        job.resourceUsage.maxMemoryUsed = allocatedMB;
      }
    }

    // Track peak heap memory usage across all threads
    java.lang.management.MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    long heapUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
    float heapUsedMB = heapUsed / (1024.0f * 1024.0f);

    // Update peak memory if current usage is higher
    if (heapUsedMB > job.resourceUsage.peakMemoryUsed) {
      job.resourceUsage.peakMemoryUsed = heapUsedMB;
    }
  }

  private void setJobOutputToSpecctraSes(RoutingJob job) {
    if (job.output == null) {
      job.output = new BoardFileDetails(job.board);
      job.output.addUpdatedEventListener(_ -> job.fireOutputUpdatedEvent());
      job.output.format = FileFormat.SES;
      job.output.setFilename(job.input.getFilenameWithoutExtension() + ".ses");
    }

    // save the result to the output field as a Specctra SES file
    if (job.output.format == FileFormat.SES) {
      HeadlessBoardManager boardManager = new HeadlessBoardManager(job);
      boardManager.replaceRoutingBoard(job.board);

      // Save the SES file after the auto-router has finished
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
        if (boardManager.saveAsSpecctraSessionSes(baos, job.name)) {
          job.output.setData(baos.toByteArray());
        }
      } catch (Exception e) {
        FRLogger.error("Couldn't save the output into the job object.", e);
      }
    }
  }
}
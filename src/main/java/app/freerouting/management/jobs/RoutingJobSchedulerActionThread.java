package app.freerouting.management.jobs;

import app.freerouting.analytics.FRAnalytics;
import app.freerouting.analytics.model.JobLifecycleStatus;
import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.autoroute.pipeline.RoutingPipeline;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.StoppableThread;
import app.freerouting.logger.FRLogger;
import app.freerouting.settings.GlobalSettings;
import app.freerouting.util.TextManager;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.time.Instant;

/** Runs a stoppable routing action on behalf of the job scheduler. */
public class RoutingJobSchedulerActionThread extends StoppableThread {

  private static final long MAX_TIMEOUT = 24 * 60 * 60; // 24 hours
  private static final int GRACE_PERIOD = 30; // 30 seconds
  private final Object terminalStateLock = new Object();
  RoutingJob job;

  /**
   * Creates a scheduler action thread for a routing job.
   *
   * @param job the job to execute
   */
  public RoutingJobSchedulerActionThread(RoutingJob job) {
    this.job = job;
  }

  @Override
  protected void threadAction() {
    try {
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
      Thread monitorThread =
          new Thread(
              () -> {
                while (job != null
                    && job.thread != null
                    && job.thread.isAlive()
                    && (job.state == RoutingJobState.RUNNING
                        || job.state == RoutingJobState.STOPPING
                        || job.state == RoutingJobState.PAUSED)) {

                  try {
                    Thread.sleep(1000);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                  }

                  if (job.state == RoutingJobState.RUNNING
                      || job.state == RoutingJobState.STOPPING) {
                    // Get the CPU time and memory usage of the job thread
                    this.monitorCpuAndMemoryUsage(job);

                    // Check for timeout
                    if (job.timeoutAt != null && !Instant.now().isBefore(job.timeoutAt)) {

                      // signal the job thread to stop, and wait gracefully for up to 30 seconds for
                      // it
                      job.thread.requestStop();
                      while ((job.state == RoutingJobState.RUNNING)
                          && Instant.now().isBefore(job.timeoutAt.plusSeconds(GRACE_PERIOD))) {
                        try {
                          Thread.sleep(1000);
                        } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          return;
                        }
                      }
                      markTimedOutIfStillActive();
                    }
                  }
                }
              },
              "freerouting-job-monitor-" + job.id);
      monitorThread.setDaemon(true);
      monitorThread.start();

      boolean routerEnabled =
          job.routerSettings.getRunRouter()
              && (job.routerSettings.autorouter.maxPasses == null
                  || job.routerSettings.autorouter.maxPasses >= 0);
      if (routerEnabled) {
        FRAnalytics.autorouterStarted();
      }

      String inputFormat =
          job.input != null && job.input.format != null ? job.input.format.name() : null;
      FRAnalytics.recordJobLifecycle(
          job.id.toString(),
          job.sessionId != null ? job.sessionId.toString() : null,
          JobLifecycleStatus.STARTED,
          FRAnalytics.getCurrentPipeline(),
          FRAnalytics.getCurrentActorType(),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          job.getDetectedHost(),
          null,
          job.userId,
          job.apiKeyHash,
          inputFormat);

      RoutingPipeline pipeline = RoutingPipeline.createForHeadless(job);
      pipeline.addBoardUpdatedEventListener(event -> RoutingJobOutputWriter.write(job));
      pipeline.addStageListener(
          new RoutingPipeline.StageListener() {
            @Override
            public void afterRouting(BatchAutorouter batchRouter) {
              if (!routerEnabled) {
                return;
              }

              Instant sessionStartTime = batchRouter.getSessionStartTime();
              if (sessionStartTime == null) {
                return;
              }

              Instant sessionEndTime = Instant.now();
              double totalTime =
                  java.time.Duration.between(sessionStartTime, sessionEndTime).toMillis() / 1000.0;
              var finalStats = job.board.getStatistics();
              float normalizedScore = finalStats.getRouterScore(job.routerSettings);
              FRAnalytics.autorouterFinished(
                  finalStats.nets.totalCount,
                  finalStats.connections.incompleteCount,
                  finalStats.clearanceViolations.totalCount,
                  job.board.getHash(),
                  normalizedScore);

              String completionStatus = "completed:";
              boolean isTimedOut =
                  (job.state == RoutingJobState.TIMED_OUT)
                      || ((job.timeoutAt != null)
                          && !Instant.now().isBefore(job.timeoutAt)
                          && job.thread.isStopRequested());

              if (isTimedOut) {
                completionStatus = "completed with timeout:";
              } else if (job.thread.isStopRequested()) {
                completionStatus = job.isCancelledByUser() ? "cancelled:" : "interrupted:";
              }

              job.logInfo(
                  String.format(
                      java.util.Locale.US,
                      "Auto-routing stage %s started with %d unrouted nets, completed in %.2f "
                          + "seconds, final score: %s, using %.2f total CPU seconds, %.2f GB total "
                          + "allocated, and %.1f MB peak heap usage.",
                      completionStatus,
                      batchRouter.getInitialUnroutedCount(),
                      totalTime,
                      FRLogger.formatScore(
                          normalizedScore,
                          finalStats.connections.incompleteCount,
                          finalStats.clearanceViolations.totalCount),
                      job.resourceUsage.cpuTimeUsed,
                      job.resourceUsage.maxMemoryUsed / 1024.0f,
                      job.resourceUsage.peakMemoryUsed));
            }

            @Override
            public void beforeOptimization(BatchOptimizer optimizer) {
              FRAnalytics.routeOptimizerStarted();
            }

            @Override
            public void afterOptimization(BatchOptimizer optimizer) {
              FRAnalytics.routeOptimizerFinished();
            }
          });
      pipeline.run();
      boolean outputReady = RoutingJobOutputWriter.write(job);

      boolean fanoutTimedOut = pipeline.getAutorouter().isFanoutTimedOut();
      final boolean optimizerTimedOut =
          pipeline.getOptimizer() != null && pipeline.getOptimizer().isTimedOut();

      job.finishedAt = Instant.now();
      assignTerminalState(outputReady);

      long durationMs = java.time.Duration.between(job.startedAt, job.finishedAt).toMillis();
      double durationSec = durationMs / 1000.0;
      StringBuilder details = new StringBuilder();
      if (fanoutTimedOut) {
        details.append(" (fanout stage timed out)");
      }
      if (optimizerTimedOut) {
        details.append(" (optimizer stage timed out)");
      }
      job.logInfo(
          "Job '"
              + job.shortName
              + "' finished with state: "
              + job.state.toString()
              + details.toString()
              + " (elapsed: "
              + FRLogger.formatDuration(durationSec)
              + ", finished at UTC: "
              + job.finishedAt.toString()
              + ").");

      JobLifecycleStatus lifecycleStatus =
          switch (job.state) {
            case COMPLETED -> JobLifecycleStatus.SUCCEEDED;
            case TIMED_OUT -> JobLifecycleStatus.TIMED_OUT;
            case CANCELLED -> JobLifecycleStatus.CANCELLED;
            default -> JobLifecycleStatus.FAILED;
          };

      var finalBoardStats = job.board != null ? job.board.getStatistics() : null;
      Integer netsTotal =
          finalBoardStats != null && finalBoardStats.nets != null
              ? finalBoardStats.nets.totalCount
              : null;
      Integer netsIncomplete =
          finalBoardStats != null && finalBoardStats.connections != null
              ? finalBoardStats.connections.incompleteCount
              : null;
      Integer clearanceViolations =
          finalBoardStats != null && finalBoardStats.clearanceViolations != null
              ? finalBoardStats.clearanceViolations.totalCount
              : null;
      Float normalizedScore =
          finalBoardStats != null && job.routerSettings != null
              ? finalBoardStats.getRouterScore(job.routerSettings)
              : null;

      FRAnalytics.recordJobLifecycle(
          job.id.toString(),
          job.sessionId != null ? job.sessionId.toString() : null,
          lifecycleStatus,
          FRAnalytics.getCurrentPipeline(),
          FRAnalytics.getCurrentActorType(),
          details.length() > 0 ? details.toString().trim() : null,
          netsTotal,
          netsIncomplete,
          clearanceViolations,
          normalizedScore,
          durationSec,
          (double) job.resourceUsage.cpuTimeUsed,
          (double) job.resourceUsage.peakMemoryUsed,
          job.getDetectedHost(),
          null,
          job.userId,
          job.apiKeyHash,
          inputFormat);
    } catch (VirtualMachineError fatal) {
      markTerminatedByFailure(fatal);
      throw fatal;
    } catch (Throwable t) {
      FRLogger.error("Uncaught exception in routing job '" + job.shortName + "'", t);
      job.finishedAt = Instant.now();
      synchronized (terminalStateLock) {
        if (job.state == RoutingJobState.RUNNING || job.state == RoutingJobState.STOPPING) {
          job.state = RoutingJobState.TERMINATED;
        }
      }
      job.logError("Job '" + job.shortName + "' terminated due to an error: " + t.getMessage(), t);
    }
  }

  private void monitorCpuAndMemoryUsage(RoutingJob job) {
    try {
      // Get the ThreadMXBean instance and cast it to com.sun.management.ThreadMXBean
      ThreadMXBean threadManagementBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

      // Get all live thread IDs
      long[] threadIds = threadManagementBean.getAllThreadIds();

      // Iterate through the thread IDs and get memory usage
      for (long threadId : threadIds) {
        if (threadId == job.thread.threadId()) {
          // CPU time and memory usage
          float cpuTime =
              threadManagementBean.getThreadCpuTime(threadId) / 1000.0f / 1000.0f / 1000.0f;

          // Enable thread memory allocation measurement
          threadManagementBean.setThreadAllocatedMemoryEnabled(true);

          // Get the thread's allocated memory in bytes
          long allocatedMemory = threadManagementBean.getThreadAllocatedBytes(threadId);
          float allocatedMemoryMb = allocatedMemory / (1024.0f * 1024.0f);

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
          job.resourceUsage.maxMemoryUsed = allocatedMemoryMb;
        }
      }

      // Track peak heap memory usage across all threads
      java.lang.management.MemoryMXBean memoryManagementBean = ManagementFactory.getMemoryMXBean();
      long heapUsed = memoryManagementBean.getHeapMemoryUsage().getUsed();
      float heapUsedMb = heapUsed / (1024.0f * 1024.0f);

      // Update peak memory if current usage is higher
      if (heapUsedMb > job.resourceUsage.peakMemoryUsed) {
        job.resourceUsage.peakMemoryUsed = heapUsedMb;
      }
    } catch (Throwable t) {
      // java.management or jdk.management module may not be available in minimal JRE builds;
      // resource usage stats will remain at their current values.
    }
  }

  /**
   * Marks a job that is still running or stopping as timed out. A job that already reached {@link
   * RoutingJobState#COMPLETED} or another terminal state keeps that state.
   */
  /**
   * Marks a job that is still active as terminated after a fatal error such as {@link
   * OutOfMemoryError}. The CLI waiter treats that state as finished and exits. Swallowing the error
   * leaves the state at {@link RoutingJobState#RUNNING} while the process makes no further progress.
   */
  void markTerminatedByFailure(Throwable fatal) {
    try {
      job.finishedAt = Instant.now();
    } catch (Throwable ignored) {
      // Allocation can fail again while the heap is exhausted.
    }
    synchronized (terminalStateLock) {
      if (job.state == RoutingJobState.RUNNING
          || job.state == RoutingJobState.STOPPING
          || job.state == RoutingJobState.PAUSED) {
        job.state = RoutingJobState.TERMINATED;
      }
    }
    try {
      job.logError("Job '" + job.shortName + "' terminated due to a fatal error", fatal);
    } catch (Throwable ignored) {
      // Logging allocates. The terminal state above is what lets the process exit.
    }
  }

  void markTimedOutIfStillActive() {
    synchronized (terminalStateLock) {
      if (job.state == RoutingJobState.RUNNING || job.state == RoutingJobState.STOPPING) {
        job.state = RoutingJobState.TIMED_OUT;
      }
    }
  }

  /**
   * One terminal state for a finished pipeline. A job stopped because {@code timeoutAt} has passed
   * becomes {@link RoutingJobState#TIMED_OUT}. A user cancel stays {@link
   * RoutingJobState#CANCELLED}. Anything already terminal, including a timeout assigned by the
   * monitor, is left as it is.
   */
  void assignTerminalState(boolean outputReady) {
    synchronized (terminalStateLock) {
      if (job.state != RoutingJobState.RUNNING && job.state != RoutingJobState.STOPPING) {
        return;
      }
      if (job.isCancelledByUser()) {
        job.state = RoutingJobState.CANCELLED;
        return;
      }
      if (jobClockExpired()) {
        job.state = RoutingJobState.TIMED_OUT;
        return;
      }
      if (!outputReady) {
        job.state = RoutingJobState.INVALID;
        return;
      }
      job.state = RoutingJobState.COMPLETED;
      GlobalSettings settings = GlobalSettings.current();
      if (settings != null) {
        settings.statistics.incrementJobsCompleted();
      }
    }
  }

  private boolean jobClockExpired() {
    return job.timeoutAt != null && !Instant.now().isBefore(job.timeoutAt);
  }
}

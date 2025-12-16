package app.freerouting.management;

import static app.freerouting.Freerouting.globalSettings;

import app.freerouting.autoroute.BatchAutorouter;
import app.freerouting.autoroute.BatchFanout;
import app.freerouting.autoroute.BatchOptimizer;
import app.freerouting.autoroute.events.BoardUpdatedEvent;
import app.freerouting.autoroute.events.BoardUpdatedEventListener;
import app.freerouting.core.*;
import app.freerouting.gui.FileFormat;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.logger.FRLogger;
import com.sun.management.ThreadMXBean;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.time.Instant;

/**
 * Used for running an action in a separate thread, that can be stopped by the user.
 * This typically represents an action that is triggered by job scheduler
 */
public class RoutingJobSchedulerActionThread extends StoppableThread
{
  private final long MAX_TIMEOUT = 24 * 60 * 60; // 24 hours
  private final int GRACE_PERIOD = 30; // 30 seconds
  RoutingJob job;

  public RoutingJobSchedulerActionThread(RoutingJob job)
  {
    this.job = job;
  }

  @Override
  protected void thread_action()
  {
    job.startedAt = Instant.now();
    // Use ISO standard time format
    job.logInfo("Job '" + job.shortName + "' started at " + job.startedAt.toString() + " with the seed of " + TextManager.longToHexadecimalString(job.routerSettings.random_seed) + ".");

    // check if we need to check for timeout
    Long timeout = TextManager.parseTimespanString(job.routerSettings.jobTimeoutString);
    if (timeout != null)
    {
      // maximize the timeout to 24 hours
      if (timeout > MAX_TIMEOUT)
      {
        timeout = MAX_TIMEOUT;
      }

      job.timeoutAt = job.startedAt.plusSeconds(timeout);
    }

    // Start a new thread that will monitor the job thread
    new Thread(() ->
    {
      while ((job != null) && (job.thread != null))
      {

        try
        {
          Thread.sleep(1000);
        } catch (InterruptedException e)
        {
          e.printStackTrace();
        }

        if (job.state == RoutingJobState.RUNNING)
        {
          // Get the CPU time and memory usage of the job thread
          this.monitorCpuAndMemoryUsage(job);

          // Check for timeout
          if (!Instant
              .now()
              .isBefore(job.timeoutAt))
          {

            // signal the job thread to stop, and wait gracefully for up to 30 seconds for it
            job.thread.requestStop();
            while ((job.state == RoutingJobState.RUNNING) && Instant
                .now()
                .isBefore(job.timeoutAt.plusSeconds(GRACE_PERIOD)))
            {
              try
              {
                Thread.sleep(1000);
              } catch (InterruptedException e)
              {
                e.printStackTrace();
              }
            }
            job.state = RoutingJobState.TIMED_OUT;
          }
        }
      }
    }).start();

    // start the fanout, routing, optimizer task(s) if needed
    if (job.routerSettings.getRunFanout())
    {
      job.stage = RoutingStage.FANOUT;
      // start the fanout task
      BatchFanout fanout = new BatchFanout(job);
      fanout.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
      {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event)
        {
          setJobOutputToSpecctraSes(job);
        }
      });
      fanout.runBatchLoop();
      job.stage = RoutingStage.IDLE;
    }

    if (job.routerSettings.getRunRouter())
    {
      job.stage = RoutingStage.ROUTING;
      // start the routing task
      BatchAutorouter router = new BatchAutorouter(job);
      router.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
      {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event)
        {
          setJobOutputToSpecctraSes(job);
        }
      });
      router.runBatchLoop();
      job.stage = RoutingStage.IDLE;
    }

    if (job.routerSettings.getRunOptimizer())
    {
      job.stage = RoutingStage.OPTIMIZATION;
      // start the optimizer task
      BatchOptimizer optimizer = new BatchOptimizer(job);
      optimizer.addBoardUpdatedEventListener(new BoardUpdatedEventListener()
      {
        @Override
        public void onBoardUpdatedEvent(BoardUpdatedEvent event)
        {
          setJobOutputToSpecctraSes(job);
        }
      });
      optimizer.runBatchLoop();
      job.stage = RoutingStage.IDLE;
    }

    job.finishedAt = Instant.now();
    if (job.state == RoutingJobState.RUNNING)
    {
      job.state = RoutingJobState.COMPLETED;
      globalSettings.statistics.incrementJobsCompleted();
    }
  }

  private void monitorCpuAndMemoryUsage(RoutingJob job)
  {
    // Get the ThreadMXBean instance and cast it to com.sun.management.ThreadMXBean
    ThreadMXBean threadMXBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();

    // Get all live thread IDs
    long[] threadIds = threadMXBean.getAllThreadIds();

    // Iterate through the thread IDs and get memory usage
    for (long threadId : threadIds)
    {
      if (threadId == job.thread.threadId())
      {
        // CPU time and memory usage
        float cpuTime = threadMXBean.getThreadCpuTime(threadId) / 1000.0f / 1000.0f / 1000.0f;

        // Enable thread memory allocation measurement
        threadMXBean.setThreadAllocatedMemoryEnabled(true);

        // Get the thread's allocated memory in bytes
        long allocatedMemory = threadMXBean.getThreadAllocatedBytes(threadId);
        float allocatedMB = allocatedMemory / (1024.0f * 1024.0f);

        // Update the job's resource usage
        job.resourceUsage.cpuTimeUsed += cpuTime;
        job.resourceUsage.maxMemoryUsed = Math.max(job.resourceUsage.maxMemoryUsed, allocatedMB);
      }
    }
  }

  private void setJobOutputToSpecctraSes(RoutingJob job)
  {
    if (job.output == null)
    {
      job.output = new BoardFileDetails(job.board);
      job.output.addUpdatedEventListener(_ -> job.fireOutputUpdatedEvent());
      job.output.format = FileFormat.SES;
      job.output.setFilename(job.input.getFilenameWithoutExtension() + ".ses");
    }

    // save the result to the output field as a Specctra SES file
    if (job.output.format == FileFormat.SES)
    {
      HeadlessBoardManager boardManager = new HeadlessBoardManager(null, job);
      boardManager.replaceRoutingBoard(job.board);

      // Save the SES file after the auto-router has finished
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream())
      {
        if (boardManager.saveAsSpecctraSessionSes(baos, job.name))
        {
          job.output.setData(baos.toByteArray());
        }
      } catch (Exception e)
      {
        FRLogger.error("Couldn't save the output into the job object.", e);
      }
    }
  }
}

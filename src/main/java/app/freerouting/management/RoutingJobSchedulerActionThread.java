package app.freerouting.management;

import app.freerouting.autoroute.BatchAutorouter;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.RoutingStage;
import app.freerouting.core.StoppableThread;

import java.time.Instant;

import static app.freerouting.Freerouting.globalSettings;

/**
 * Used for running an action in a separate thread, that can be stopped by the user.
 * This typically represents an action that is triggered by job scheduler
 */
public class RoutingJobSchedulerActionThread extends StoppableThread
{
  RoutingJob job;

  public RoutingJobSchedulerActionThread(RoutingJob job)
  {
    this.job = job;
  }

  @Override
  protected void thread_action()
  {
    job.startedAt = Instant.now();

    // check if we need to check for timeout
    Long timeout = TextManager.parseTimespanString(job.routerSettings.jobTimeoutString);
    if (timeout != null)
    {
      // maximize the timeout to 24 hours
      if (timeout > 24 * 60 * 60)
      {
        timeout = 24 * 60 * 60L;
      }

      job.timeoutAt = job.startedAt.plusSeconds(timeout);

      // start a new thread that will check for timeout
      new Thread(() ->
      {
        while (Instant.now().isBefore(job.timeoutAt))
        {
          try
          {
            Thread.sleep(1000);
          } catch (InterruptedException e)
          {
            e.printStackTrace();
          }
        }

        if (job.state == RoutingJobState.RUNNING)
        {
          job.state = RoutingJobState.TIMED_OUT;
        }
      }).start();
    }

    // start the fanout, routing, optimizer task(s) if needed
    if (job.routerSettings.getRunFanout())
    {
      job.stage = RoutingStage.FANOUT;
      // TODO: start the fanout task
      job.stage = RoutingStage.IDLE;
    }

    if (job.routerSettings.getRunRouter())
    {
      job.stage = RoutingStage.ROUTING;
      // start the routing task
      BatchAutorouter router = new BatchAutorouter(job);
      router.runBatchLoop();
      job.stage = RoutingStage.IDLE;
    }

    if (job.routerSettings.getRunOptimizer())
    {
      job.stage = RoutingStage.OPTIMIZATION;
      // TODO: start the optimizer task
      job.stage = RoutingStage.IDLE;
    }

    job.finishedAt = Instant.now();
    if (job.state == RoutingJobState.RUNNING)
    {
      job.state = RoutingJobState.COMPLETED;
      globalSettings.statistics.incrementJobsCompleted();
    }
  }
}
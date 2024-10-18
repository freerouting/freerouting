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
      BatchAutorouter router = new BatchAutorouter(job.thread, job.board, job.routerSettings, !job.routerSettings.getRunFanout(), true, job.routerSettings.get_start_ripup_costs(), job.routerSettings.trace_pull_tight_accuracy);
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
    job.state = RoutingJobState.COMPLETED;
    globalSettings.statistics.incrementJobsCompleted();
  }
}
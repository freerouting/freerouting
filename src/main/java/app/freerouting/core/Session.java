package app.freerouting.core;

import java.io.Serializable;
import java.util.UUID;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Represents a user session that contains the jobs that will be processed by the router.
 */
public class Session implements Serializable
{
  public final UUID id = UUID.randomUUID();
  public final PriorityBlockingQueue<RoutingJob> jobs = new PriorityBlockingQueue<>();

  public RoutingJob queue(RoutingJob job)
  {
    this.jobs.add(job);
    return job;
  }
}
package app.freerouting.core;

import app.freerouting.management.RoutingJobScheduler;

import java.io.Serializable;
import java.util.UUID;

/**
 * Represents a user session that contains the jobs that will be processed by the router.
 */
public class Session implements Serializable
{
  public final UUID id = UUID.randomUUID();
  public final UUID userId;
  public boolean isGuiSession = false;

  /**
   * Creates a new session.
   *
   * @param userId The user ID that the session belongs to.
   */
  public Session(UUID userId)
  {
    this.userId = userId;
  }

  /**
   * Adds a job to the session.
   *
   * @param routingJob The job to add.
   */
  public void addJob(RoutingJob routingJob)
  {
    RoutingJobScheduler.getInstance().enqueueJob(routingJob);
  }
}
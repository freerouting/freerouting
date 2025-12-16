package app.freerouting.core.events;

import app.freerouting.core.RoutingJob;
import java.util.EventObject;

public class RoutingJobUpdatedEvent extends EventObject {

  private final RoutingJob job;

  public RoutingJobUpdatedEvent(Object source, RoutingJob job) {
    super(source);
    this.job = job;
  }

  public RoutingJob getJob() {
    return job;
  }
}
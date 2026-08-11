package app.freerouting.core.events;

import app.freerouting.core.RoutingJob;
import java.util.EventObject;

/** Event emitted when a routing job changes. */
public class RoutingJobUpdatedEvent extends EventObject {

  private final RoutingJob job;

  /** Creates an event for the changed routing job. */
  public RoutingJobUpdatedEvent(Object source, RoutingJob job) {
    super(source);
    this.job = job;
  }

  /** Returns the affected routing job. */
  public RoutingJob getJob() {
    return job;
  }
}

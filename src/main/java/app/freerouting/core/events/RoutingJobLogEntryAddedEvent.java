package app.freerouting.core.events;

import app.freerouting.core.RoutingJob;
import app.freerouting.logger.LogEntry;
import java.util.EventObject;

/** Event emitted when a routing job receives a log entry. */
public class RoutingJobLogEntryAddedEvent extends EventObject {

  private final RoutingJob job;
  private final LogEntry logEntry;

  /** Creates an event for a newly added log entry. */
  public RoutingJobLogEntryAddedEvent(Object source, RoutingJob job, LogEntry logEntry) {
    super(source);
    this.job = job;
    this.logEntry = logEntry;
  }

  /** Returns the affected routing job. */
  public RoutingJob getJob() {
    return job;
  }

  /** Returns the added log entry. */
  public LogEntry getLogEntry() {
    return logEntry;
  }
}

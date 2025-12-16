package app.freerouting.core.events;

import app.freerouting.core.RoutingJob;
import app.freerouting.logger.LogEntry;
import java.util.EventObject;

public class RoutingJobLogEntryAddedEvent extends EventObject {

  private final RoutingJob job;
  private final LogEntry logEntry;

  public RoutingJobLogEntryAddedEvent(Object source, RoutingJob job, LogEntry logEntry) {
    super(source);
    this.job = job;
    this.logEntry = logEntry;
  }

  public RoutingJob getJob() {
    return job;
  }

  public LogEntry getLogEntry() {
    return logEntry;
  }
}
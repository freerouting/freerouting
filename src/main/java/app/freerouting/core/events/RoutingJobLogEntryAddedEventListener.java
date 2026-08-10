package app.freerouting.core.events;

/** Receives events when routing jobs receive log entries. */
public interface RoutingJobLogEntryAddedEventListener {

  /** Handles a newly added routing job log entry. */
  void onLogEntryAdded(RoutingJobLogEntryAddedEvent event);
}

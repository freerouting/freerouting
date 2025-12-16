package app.freerouting.core.events;

public interface RoutingJobLogEntryAddedEventListener
{
  void onLogEntryAdded(RoutingJobLogEntryAddedEvent event);
}

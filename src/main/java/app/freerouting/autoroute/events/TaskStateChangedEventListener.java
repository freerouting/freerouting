package app.freerouting.autoroute.events;

public interface TaskStateChangedEventListener
{
  void onTaskStateChangedEvent(TaskStateChangedEvent event);
}
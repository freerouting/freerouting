package app.freerouting.autoroute.events;

/** Listener for task state changed events. */
public interface TaskStateChangedEventListener {

  /** Handles a task state changed event. */
  void onTaskStateChangedEvent(TaskStateChangedEvent event);
}

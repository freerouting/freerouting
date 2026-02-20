package app.freerouting.datastructures;

/** Interface for stoppable threads. */
public interface Stoppable {
  /** Requests this thread to be stopped. */
  void request_stop();

  /** Returns true, if this thread is requested to be stopped. */
  boolean is_stop_requested();
}

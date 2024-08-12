package app.freerouting.datastructures;

/**
 * Interface for stoppable threads.
 * TODO: Replace this interface with the java-native Runnable
 */
public interface Stoppable
{
  /**
   * Requests this thread to be stopped.
   */
  void request_stop();

  /**
   * Returns true, if this thread is requested to be stopped.
   */
  boolean is_stop_requested();
}
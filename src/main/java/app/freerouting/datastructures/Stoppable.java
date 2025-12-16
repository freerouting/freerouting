package app.freerouting.datastructures;

/**
 * Interface for stoppable threads.
 */
public interface Stoppable {

  /**
   * Requests this thread to be stopped.
   */
  void requestStop();

  /**
   * Returns true, if this thread is requested to be stopped.
   */
  boolean isStopRequested();
}
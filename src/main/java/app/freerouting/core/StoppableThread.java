package app.freerouting.core;

import app.freerouting.datastructures.Stoppable;

/**
 * Used for running an interactive action in a separate thread, that can be stopped by the user.
 */
public abstract class StoppableThread extends Thread implements Stoppable {

  private StopRequestState stopRequestState = StopRequestState.NONE;

  /**
   * Creates a new instance of InteractiveActionThread
   */
  protected StoppableThread() {
  }

  protected abstract void thread_action();

  @Override
  public void run() {
    thread_action();
  }

  // Request the thread to stop including the fanout, auto-router and optimizer tasks
  @Override
  public synchronized void requestStop() {
    this.stopRequestState = StopRequestState.ALL;
  }

  @Override
  public synchronized boolean isStopRequested() {
    return this.stopRequestState == StopRequestState.ALL;
  }

  // Request the thread to stop the auto-router, but continue with the optimizer and other tasks
  public synchronized void request_stop_auto_router() {
    if (this.stopRequestState == StopRequestState.NONE) {
      this.stopRequestState = StopRequestState.AUTO_ROUTER_ONLY;
    }
  }

  // Check if the thread should stop the auto router
  public synchronized boolean is_stop_auto_router_requested() {
    return this.stopRequestState != StopRequestState.NONE;
  }
}
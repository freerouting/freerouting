package app.freerouting.core;

import app.freerouting.datastructures.Stoppable;

/** Runs an interactive action in a separate thread that can be stopped by the user. */
public abstract class StoppableThread extends Thread implements Stoppable {

  private StopRequestState stopRequestState = StopRequestState.NONE;

  /** Creates a new stoppable thread. */
  protected StoppableThread() {}

  /** Performs the thread's action. */
  protected abstract void threadAction();

  @Override
  public void run() {
    threadAction();
  }

  /** Requests the thread to stop, including fanout, auto-router, and optimizer tasks. */
  @Override
  public synchronized void requestStop() {
    this.stopRequestState = StopRequestState.ALL;
  }

  @Override
  public synchronized boolean isStopRequested() {
    return this.stopRequestState == StopRequestState.ALL;
  }

  /** Requests the auto-router to stop while other tasks continue. */
  public synchronized void requestStopAutoRouter() {
    if (this.stopRequestState == StopRequestState.NONE) {
      this.stopRequestState = StopRequestState.AUTO_ROUTER_ONLY;
    }
  }

  /** Returns whether the auto-router should stop. */
  public synchronized boolean isStopAutoRouterRequested() {
    return this.stopRequestState != StopRequestState.NONE;
  }
}

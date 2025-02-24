package app.freerouting.core;

import app.freerouting.datastructures.Stoppable;

/**
 * Used for running an interactive action in a separate thread, that can be stopped by the user.
 */
public abstract class StoppableThread extends Thread implements Stoppable
{
  private boolean stop_requested = false;
  // TODO: why do we need this, can't we use stop_requested?
  private boolean stop_auto_router = false;

  /**
   * Creates a new instance of InteractiveActionThread
   */
  protected StoppableThread()
  {
  }

  protected abstract void thread_action();

  @Override
  public void run()
  {
    thread_action();
  }

  // Request the thread to stop including the fanout, auto-router and optimizer tasks
  @Override
  public synchronized void requestStop()
  {
    stop_requested = true;
    stop_auto_router = true;
  }

  @Override
  public synchronized boolean isStopRequested()
  {
    return stop_requested;
  }

  // Request the thread to stop the auto-router, but continue with the optimizer and other tasks
  public synchronized void request_stop_auto_router()
  {
    stop_auto_router = true;
  }

  // Check if the thread should stop the auto router
  public synchronized boolean is_stop_auto_router_requested()
  {
    return stop_auto_router;
  }
}
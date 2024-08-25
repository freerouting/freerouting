package app.freerouting.core;

import app.freerouting.datastructures.Stoppable;

/**
 * Used for running an interactive action in a separate thread, that can be stopped by the user.
 */
public abstract class StoppableThread extends Thread implements Stoppable
{
  private boolean stop_requested = false;
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

  @Override
  public synchronized void requestStop()
  {
    stop_requested = true;
  }

  @Override
  public synchronized boolean isStopRequested()
  {
    return stop_requested;
  }

  public synchronized void request_stop_auto_router()
  {
    stop_auto_router = true;
  }

  public synchronized boolean is_stop_auto_router_requested()
  {
    return stop_auto_router;
  }
}
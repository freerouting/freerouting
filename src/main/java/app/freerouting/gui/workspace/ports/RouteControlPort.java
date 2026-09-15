package app.freerouting.gui.workspace.ports;

import app.freerouting.core.RoutingJob;
import app.freerouting.gui.workspace.progress.RouteCompletion;
import app.freerouting.gui.workspace.session.RunGeneration;

/** Session-owned port for starting, stopping, and finishing route runs. */
public interface RouteControlPort {

  /** Starts a run and takes the board read-only on the EDT. */
  public RunGeneration beginRoute(RoutingJob job);

  /** Requests a stop while leaving terminal cleanup to the worker. */
  public void requestStop(RunGeneration generation);

  /** Completes the run and performs terminal EDT cleanup. */
  public void finishRoute(RouteCompletion completion);
}

package app.freerouting.gui.workspace;

import app.freerouting.core.RoutingJob;

/** Session-owned port for starting, stopping, and finishing route runs. */
public interface RouteControlPort {

  /** Starts a run and takes the board read-only on the EDT. */
  RunGeneration beginRoute(RoutingJob job);

  /** Requests a stop while leaving terminal cleanup to the worker. */
  void requestStop(RunGeneration generation);

  /** Completes the run and performs terminal EDT cleanup. */
  void finishRoute(RouteCompletion completion);
}

package app.freerouting.gui.workspace.ports;

import app.freerouting.gui.workspace.progress.RouteProgress;

/** Session-owned port for immutable worker progress events. */
public interface RouteProgressPort {

  /** Publishes progress on the EDT when the run is current. */
  public void publishProgress(RouteProgress progress);
}

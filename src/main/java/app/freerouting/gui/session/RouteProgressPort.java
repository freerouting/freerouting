package app.freerouting.gui.session;

/** Session-owned port for immutable worker progress events. */
public interface RouteProgressPort {

  /** Publishes progress on the EDT when the run is current. */
  void publishProgress(RouteProgress progress);
}

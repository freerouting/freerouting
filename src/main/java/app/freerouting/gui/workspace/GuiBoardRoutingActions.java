package app.freerouting.gui.workspace;

import app.freerouting.core.RoutingJob;

/**
 * Coordinates long-running autoroute actions for a GUI board session.
 *
 * <p>The manager keeps the public methods and read-only façade, while this collaborator owns worker
 * creation and stop requests. The worker still receives a detached settings snapshot and keeps all
 * concrete interactive-state boundaries unchanged.
 */
final class GuiBoardRoutingActions {

  private final GuiBoardManager manager;

  GuiBoardRoutingActions(GuiBoardManager manager) {
    this.manager = manager;
  }

  InteractiveActionThread start(RoutingJob job) {
    if (manager.isBoardReadOnly()) {
      return null;
    }

    manager.getRoutingBoard().generateSnapshot();
    job.setSettings(manager.getSessionPort().settingsSnapshot().copy());
    RunGeneration generation = manager.getSessionPort().beginRoute(job);
    InteractiveActionThread worker =
        InteractiveActionThread.getAutorouterAndRouteOptimizerInstance(
            manager.getSessionPort(), generation, job);
    manager.setInteractiveActionThread(worker);
    worker.start();
    return worker;
  }

  void stop() {
    if (manager.getSessionPort() instanceof WorkspacePortAdapter adapter) {
      adapter.requestStopCurrent();
    } else {
      manager.requestStopFromSessionPort();
    }
  }
}

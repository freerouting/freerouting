package app.freerouting.autoroute.maze;

import app.freerouting.logger.FRLogger;

/** Optional targeted diagnostics for fanout maze expansion. */
final class MazeFanoutDiagnostics {

  private final AutorouteControl ctrl;

  MazeFanoutDiagnostics(AutorouteControl ctrl) {
    this.ctrl = ctrl;
  }

  boolean enabled() {
    return ctrl.isFanout
        && ctrl.fanoutStartPinName != null
        && ctrl.fanoutStartPinName.startsWith("U27-");
  }

  private String label() {
    return ctrl.fanoutStartPinName == null
        ? "fanout-pin(net=" + ctrl.netNumber + ")"
        : ctrl.fanoutStartPinName;
  }

  String labelForLog() {
    return label();
  }

  void trace(String event, String message) {
    if (!enabled()) {
      return;
    }
    FRLogger.trace(
        "FANOUT_DIAG event="
            + event
            + ", pin="
            + label()
            + ", net="
            + ctrl.netNumber
            + ", "
            + message);
  }
}

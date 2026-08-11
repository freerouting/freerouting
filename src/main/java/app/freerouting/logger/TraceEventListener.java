package app.freerouting.logger;

/** Listener for FRLogger trace events that were marked as interesting. */
@FunctionalInterface
public interface TraceEventListener {

  /** Called when an interesting trace event is published. */
  void onTraceEvent(TraceEvent event);
}

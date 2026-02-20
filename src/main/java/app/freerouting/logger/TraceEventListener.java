package app.freerouting.logger;

/** Listener for FRLogger trace events that were marked as interesting. */
@FunctionalInterface
public interface TraceEventListener {

  void onTraceEvent(TraceEvent event);
}
package app.freerouting.gui.session;

/** Marker for a generation token owned by one GUI session. */
public sealed interface SessionGeneration permits LoadGeneration, RunGeneration {

  /** Returns the monotonically increasing generation value. */
  long value();
}

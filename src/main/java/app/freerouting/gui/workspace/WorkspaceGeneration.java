package app.freerouting.gui.workspace;

/** Marker for a generation token owned by one GUI session. */
public sealed interface WorkspaceGeneration permits LoadGeneration, RunGeneration {

  /** Returns the monotonically increasing generation value. */
  long value();
}

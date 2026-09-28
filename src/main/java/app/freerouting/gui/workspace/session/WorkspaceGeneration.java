package app.freerouting.gui.workspace.session;

/** Marker for a generation token owned by one GUI session. */
public sealed interface WorkspaceGeneration permits LoadGeneration, RunGeneration {

  /** Returns the monotonically increasing generation value. */
  public long value();
}

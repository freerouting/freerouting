package app.freerouting.gui.workspace.session;

/** Token identifying one autorouter/optimizer run. */
public record RunGeneration(long value) implements WorkspaceGeneration {

  /** Validates the generation value. */
  public RunGeneration {
    if (value < 0) {
      throw new IllegalArgumentException("Run generation must not be negative");
    }
  }
}

package app.freerouting.gui.workspace;

/** Token identifying one board-load attempt. */
public record LoadGeneration(long value) implements WorkspaceGeneration {

  /** Validates the generation value. */
  public LoadGeneration {
    if (value < 0) {
      throw new IllegalArgumentException("Load generation must not be negative");
    }
  }
}

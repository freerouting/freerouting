package app.freerouting.gui.workspace.progress;

import app.freerouting.board.model.structure.Unit;
import app.freerouting.gui.workspace.session.RunGeneration;

/** Immutable progress event emitted by a background routing operation. */
public record RouteProgress(
    RunGeneration generation,
    String statusMessage,
    BatchProgress batchProgress,
    float boardScore,
    int incompleteCount,
    int violationCount,
    Integer viaCount,
    Double traceLength,
    Unit unit,
    boolean clearMessages,
    boolean repaint) {

  /** Creates a status-only progress event. */
  public static RouteProgress status(RunGeneration generation, String message) {
    return new RouteProgress(generation, message, null, 0, 0, 0, null, null, null, false, true);
  }
}

package app.freerouting.gui.workspace.ports;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.gui.workspace.session.WorkspaceGeneration;
import java.util.Objects;

/** Immutable request to publish a newly prepared board to the active GUI session. */
public record BoardReplacement(WorkspaceGeneration generation, RoutingBoard board) {

  /** Validates the replacement payload. */
  public BoardReplacement {
    Objects.requireNonNull(generation, "generation");
    Objects.requireNonNull(board, "board");
  }
}

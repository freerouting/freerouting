package app.freerouting.gui.session;

import app.freerouting.board.RoutingBoard;
import java.util.Objects;

/** Immutable request to publish a newly prepared board to the active GUI session. */
public record BoardReplacement(SessionGeneration generation, RoutingBoard board) {

  /** Validates the replacement payload. */
  public BoardReplacement {
    Objects.requireNonNull(generation, "generation");
    Objects.requireNonNull(board, "board");
  }
}

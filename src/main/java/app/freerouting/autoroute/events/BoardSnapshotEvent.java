package app.freerouting.autoroute.events;

import app.freerouting.board.facade.RoutingBoard;
import java.util.EventObject;

/** BoardSnapshotEvent. */
public class BoardSnapshotEvent extends EventObject {

  private final RoutingBoard board;

  /** BoardSnapshotEvent. */
  public BoardSnapshotEvent(Object source, RoutingBoard board) {
    super(source);
    this.board = board;
  }

  public RoutingBoard getBoard() {
    return board;
  }
}

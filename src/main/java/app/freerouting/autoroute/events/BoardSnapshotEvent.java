package app.freerouting.autoroute.events;

import app.freerouting.board.RoutingBoard;

import java.util.EventObject;

public class BoardSnapshotEvent extends EventObject
{
  private final RoutingBoard board;

  public BoardSnapshotEvent(Object source, RoutingBoard board)
  {
    super(source);
    this.board = board;
  }

  public RoutingBoard getBoard()
  {
    return board;
  }
}
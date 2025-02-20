package app.freerouting.autoroute.events;

import app.freerouting.board.BoardStatistics;
import app.freerouting.board.RoutingBoard;

import java.util.EventObject;

public class BoardUpdatedEvent extends EventObject
{
  private final BoardStatistics boardStatistics;
  private final RoutingBoard board;

  public BoardUpdatedEvent(Object source, BoardStatistics statistics, RoutingBoard board)
  {
    super(source);
    this.boardStatistics = statistics;
    this.board = board;
  }

  public BoardStatistics getBoardStatistics()
  {
    return boardStatistics;
  }

  public RoutingBoard getBoard()
  {
    return board;
  }
}
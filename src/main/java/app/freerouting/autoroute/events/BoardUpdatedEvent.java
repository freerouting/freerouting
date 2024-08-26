package app.freerouting.autoroute.events;

import app.freerouting.board.BoardStatistics;

import java.util.EventObject;

public class BoardUpdatedEvent extends EventObject
{
  private final BoardStatistics boardStatistics;

  public BoardUpdatedEvent(Object source, BoardStatistics statistics)
  {
    super(source);
    this.boardStatistics = statistics;
  }

  public BoardStatistics getBoardStatistics()
  {
    return boardStatistics;
  }
}
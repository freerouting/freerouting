package app.freerouting.autoroute.events;

import app.freerouting.board.BoardStatistics;

import java.util.EventObject;

public class BoardUpdatedEvent extends EventObject
{
  private final BoardStatistics boardStatistics;

  public BoardUpdatedEvent(Object source, BoardStatistics message)
  {
    super(source);
    this.boardStatistics = message;
  }

  public BoardStatistics getBoardStatistics()
  {
    return boardStatistics;
  }
}
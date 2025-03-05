package app.freerouting.autoroute.events;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.RouterCounters;
import app.freerouting.core.scoring.BoardFileStatistics;

import java.util.EventObject;

public class BoardUpdatedEvent extends EventObject
{
  private final BoardFileStatistics boardStatistics;
  private final RouterCounters routerCounters;
  private final RoutingBoard board;

  public BoardUpdatedEvent(Object source, BoardFileStatistics statistics, RouterCounters routerCounters, RoutingBoard board)
  {
    super(source);
    this.boardStatistics = statistics;
    this.routerCounters = routerCounters;
    this.board = board;
  }

  public BoardFileStatistics getBoardStatistics()
  {
    return boardStatistics;
  }

  public RouterCounters getRouterCounters()
  {
    return routerCounters;
  }

  public RoutingBoard getBoard()
  {
    return board;
  }
}
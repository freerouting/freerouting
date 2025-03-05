package app.freerouting.board;

import app.freerouting.core.scoring.BoardFileStatisticsItems;
import app.freerouting.core.scoring.BoardFileStatisticsRouterCounters;

public class BoardStatistics
{
  public BoardFileStatisticsRouterCounters routerCounters = new BoardFileStatisticsRouterCounters();

  public BoardFileStatisticsItems items = new BoardFileStatisticsItems();

  public double totalTraceLength;
  public double weightedTraceLength;
}
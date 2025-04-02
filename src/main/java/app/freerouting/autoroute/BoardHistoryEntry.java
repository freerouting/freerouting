package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.RouterScoringSettings;

import java.time.Instant;

public class BoardHistoryEntry implements Comparable<BoardHistoryEntry>
{
  public final RoutingBoard board;
  public final float score;
  public final String hash;
  public final BoardStatistics statistics;
  public final Instant timestamp;
  public int restoreCount = 0;

  public BoardHistoryEntry(RoutingBoard board, RouterScoringSettings scoringSettings)
  {
    this.board = board;
    this.statistics = board.get_statistics();
    this.score = this.statistics.getNormalizedScore(scoringSettings);
    this.hash = board.get_hash();
    this.timestamp = Instant.now();
  }

  @Override
  public int compareTo(BoardHistoryEntry o)
  {
    return Float.compare(this.score, o.score);
  }
}
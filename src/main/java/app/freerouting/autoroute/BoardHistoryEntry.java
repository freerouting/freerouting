package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.ScoringSettings;
import java.time.Instant;

/**
 * Represents a historical snapshot entry of a board routing state with its score and statistics.
 */
public class BoardHistoryEntry implements Comparable<BoardHistoryEntry> {

  public final RoutingBoard board;
  public final float score;
  public final String hash;
  public final BoardStatistics statistics;
  public final Instant timestamp;
  public int restoreCount;

  /**
   * Constructs a BoardHistoryEntry for the given board and scoring settings.
   */
  public BoardHistoryEntry(RoutingBoard board, ScoringSettings scoringSettings) {
    this.board = board;
    this.statistics = board.getStatistics();
    this.score = this.statistics.getNormalizedScore(scoringSettings);
    this.hash = board.getHash();
    this.timestamp = Instant.now();
  }

  @Override
  public int compareTo(BoardHistoryEntry o) {
    return Float.compare(this.score, o.score);
  }
}

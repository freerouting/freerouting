package app.freerouting.autoroute;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.RoutingBoard;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.RouterScoringSettings;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Records, manages and ranks the boards that were generated during the routing process. This implementation is thread-safe.
 * <p>
 * The history is bounded to {@link #MAX_HISTORY_SIZE} entries. When the cap is reached,
 * the lowest-scoring entry is evicted before adding the new one. This prevents unbounded
 * memory growth during long routing sessions (see Issue #684).
 * </p>
 */
public class BoardHistory {

  /**
   * Maximum number of board snapshots retained in memory at any time.
   * Each snapshot stores the fully serialised board as a {@code byte[]}, which can be several
   * megabytes for complex designs. Keeping the cap low is critical for long routing sessions.
   */
  public static final int MAX_HISTORY_SIZE = 30;

  private final List<BoardHistoryEntry> boards = Collections.synchronizedList(new ArrayList<>());
  private final RouterScoringSettings scoringSettings;
  private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

  public BoardHistory(RouterScoringSettings scoringSettings) {
    this.scoringSettings = scoringSettings;
  }

  public synchronized void add(RoutingBoard board) {
    if (contains(board)) {
      return;
    }

    // Evict the lowest-scoring entry when the cap is reached so that the list never
    // grows beyond MAX_HISTORY_SIZE. We keep only the best-scoring snapshots.
    if (boards.size() >= MAX_HISTORY_SIZE) {
      // Sort descending (best score first) so the last element is the worst.
      boards.sort((o1, o2) -> Float.compare(o2.score, o1.score));
      boards.remove(boards.size() - 1);
    }

    boards.add(new BoardHistoryEntry(board, scoringSettings));
  }

  public synchronized void clear() {
    boards.clear();
  }

  public boolean contains(RoutingBoard board) {
    String hash = board.get_hash();
    rwLock
        .readLock()
        .lock();
    try {
      for (BoardHistoryEntry entry : boards) {
        if (entry.hash.equals(hash)) {
          return true;
        }
      }
      return false;
    } finally {
      rwLock
          .readLock()
          .unlock();
    }
  }

  public synchronized void remove(RoutingBoard board) {
    String hash = board.get_hash();
    for (int i = 0; i < boards.size(); i++) {
      if (boards.get(i).hash.equals(hash)) {
        boards.remove(i);
        return;
      }
    }
  }

  public float getMaxScore() {
    rwLock
        .readLock()
        .lock();
    try {
      float maxScore = 0;
      for (BoardHistoryEntry entry : boards) {
        if (entry.score > maxScore) {
          maxScore = entry.score;
        }
      }
      return maxScore;
    } finally {
      rwLock
          .readLock()
          .unlock();
    }
  }

  /**
   * Returns the best board in the history that has a restore count less than or equal to maxAllowedRestoreCount. Returns null if no such board exists.
   */
  public synchronized RoutingBoard restoreBoard(int maxAllowedRestoreCount) {
    if (maxAllowedRestoreCount <= 0) {
      maxAllowedRestoreCount = Integer.MAX_VALUE;
    }

    rwLock
        .readLock()
        .lock();

    try {
      // Sort the boards by score
      boards.sort((o1, o2) -> Float.compare(o2.score, o1.score));

      for (BoardHistoryEntry entry : boards) {
        if (entry.restoreCount <= maxAllowedRestoreCount) {
          entry.restoreCount++;
          return (RoutingBoard) BasicBoard.deserialize(entry.board);
        }
      }
      return null;
    } finally {
      rwLock
          .readLock()
          .unlock();
    }
  }

  public RoutingBoard restoreBestBoard() {
    return restoreBoard(0);
  }

  public int size() {
    rwLock
        .readLock()
        .lock();
    try {
      return boards.size();
    } finally {
      rwLock
          .readLock()
          .unlock();
    }
  }

  public int getRank(RoutingBoard board) {
    String hash = board.get_hash();
    rwLock
        .readLock()
        .lock();
    try {
      for (int i = 0; i < boards.size(); i++) {
        if (boards.get(i).hash.equals(hash)) {
          return i + 1;
        }
      }
      return -1;
    } finally {
      rwLock
          .readLock()
          .unlock();
    }
  }

  private static class BoardHistoryEntry implements Serializable {

    public final byte[] board;
    public final String hash;
    public final float score;
    public int restoreCount;

    public BoardHistoryEntry(RoutingBoard board, RouterScoringSettings scoringSettings) {
      this.board = board.serialize(false);
      this.hash = board.get_hash();
      this.score = new BoardStatistics(board).getNormalizedScore(scoringSettings);
      this.restoreCount = 0;
    }
  }
}
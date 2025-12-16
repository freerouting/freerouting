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
 */
public class BoardHistory {

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
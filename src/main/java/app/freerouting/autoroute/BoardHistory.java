package app.freerouting.autoroute;

import app.freerouting.board.RoutingBoard;
import app.freerouting.settings.RouterScoringSettings;

import java.util.LinkedList;

/**
 * Records, manages and ranks the boards that were generated during the routing process.
 */
public class BoardHistory
{
  private final LinkedList<BoardHistoryEntry> boards = new LinkedList<>();
  private final RouterScoringSettings scoringSettings;

  public BoardHistory(RouterScoringSettings scoringSettings)
  {
    this.scoringSettings = scoringSettings;
  }

  public void add(RoutingBoard board)
  {
    if (contains(board))
    {
      return;
    }

    boards.add(new BoardHistoryEntry(board.deepCopy(), scoringSettings));
  }

  public void clear()
  {
    boards.clear();
  }

  public boolean contains(RoutingBoard board)
  {
    String hash = board.get_hash();
    for (BoardHistoryEntry entry : boards)
    {
      if (entry.hash.equals(hash))
      {
        return true;
      }
    }
    return false;
  }

  public void remove(RoutingBoard board)
  {
    String hash = board.get_hash();
    for (int i = 0; i < boards.size(); i++)
    {
      if (boards.get(i).hash.equals(hash))
      {
        boards.remove(i);
        return;
      }
    }
  }

  public float getMaxScore()
  {
    float maxScore = 0;
    for (BoardHistoryEntry entry : boards)
    {
      if (entry.score > maxScore)
      {
        maxScore = entry.score;
      }
    }
    return maxScore;
  }

  /**
   * Returns the best board in the history that has a restore count less than or equal to maxAllowedRestoreCount.
   * Returns null if no such board exists.
   */
  public RoutingBoard restoreBoard(int maxAllowedRestoreCount)
  {
    // Sort the boards by score
    boards.sort((o1, o2) -> Float.compare(o2.score, o1.score));

    for (BoardHistoryEntry entry : boards)
    {
      if (entry.restoreCount <= maxAllowedRestoreCount)
      {
        entry.restoreCount++;
        return entry.board.deepCopy();
      }
    }
    return null;
  }

  public int size()
  {
    return boards.size();
  }

  public int getRank(RoutingBoard board)
  {
    String hash = board.get_hash();
    for (int i = 0; i < boards.size(); i++)
    {
      if (boards.get(i).hash.equals(hash))
      {
        return i + 1;
      }
    }
    return -1;
  }
}
package app.freerouting.board;

import app.freerouting.core.RoutingJob;
import app.freerouting.interactive.HeadlessBoardManager;
import app.freerouting.logger.FRLogger;

/**
 * Utility class for loading boards from routing jobs.
 */
public class BoardLoader
{
  /**
   * Loads a board from a routing job's input if not already loaded.
   * 
   * @param job The routing job to load the board from
   * @return true if board is loaded successfully, false otherwise
   */
  public static boolean loadBoardIfNeeded(RoutingJob job)
  {
    // Check if board is already loaded
    if (job.board != null)
    {
      return true;
    }
    
    // Check if input is available
    if (job.input == null)
    {
      FRLogger.error("Cannot load board: job has no input", null);
      return false;
    }
    
    // Only DSN format is supported for now
    if (job.input.format != app.freerouting.gui.FileFormat.DSN)
    {
      FRLogger.error("Cannot load board: only DSN format is supported, got " + job.input.format, null);
      return false;
    }
    
    // Load the board
    try
    {
      HeadlessBoardManager boardManager = new HeadlessBoardManager(null, job);
      boardManager.loadFromSpecctraDsn(
          job.input.getData(), 
          null, 
          new ItemIdentificationNumberGenerator());
      job.board = boardManager.get_routing_board();
      return job.board != null;
    } catch (Exception e)
    {
      FRLogger.error("Failed to load board from DSN file", e);
      return false;
    }
  }
}

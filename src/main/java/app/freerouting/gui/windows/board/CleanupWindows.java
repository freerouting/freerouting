package app.freerouting.gui.windows.board;

import app.freerouting.gui.board.BoardFrame;

/** Displays board items that can be removed by the cleanup workflow. */
public abstract class CleanupWindows extends WindowObjectListWithFilter {

  /**
   * Creates a new instance of ObjectListWindowWithFilter.
   *
   * @param boardFrame the board frame that owns this window
   */
  protected CleanupWindows(BoardFrame boardFrame) {
    super(boardFrame);
  }
}

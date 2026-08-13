package app.freerouting.boardgraphics;

import app.freerouting.board.BasicBoard;
import java.awt.Graphics;

/**
 * GUI-owned entry point for rendering a board.
 *
 * <p>During the first Phase 6 migration step this adapter delegates to the legacy board traversal.
 * Keeping the delegation behind a renderer-owned API establishes the GUI boundary without changing
 * draw ordering or item-family behavior. Later Phase 6 commits replace the delegation with the
 * renderer's own traversal and strategies.
 */
public final class BoardRenderer {

  private BoardRenderer() {}

  /** Draws the complete board using the current compatibility implementation. */
  @SuppressWarnings("deprecation")
  public static void draw(BasicBoard board, Graphics graphics, GraphicsContext graphicsContext) {
    if (board == null || graphics == null || graphicsContext == null) {
      return;
    }
    board.drawLegacy(graphics, graphicsContext);
  }
}

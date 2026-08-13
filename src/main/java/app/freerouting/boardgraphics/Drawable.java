package app.freerouting.boardgraphics;

import java.awt.Color;

/** Items drawn by {@link GraphicsContext} must implement this interface. */
public interface Drawable {

  int MIN_DRAW_PRIORITY = 1;
  int MIDDLE_DRAW_PRIORITY = 3;
  int MAX_DRAW_PRIORITY = 3;

  /**
   * Returns the priority for drawing an item. Items with higher priority are drawn later than items
   * with lower priority.
   */
  int getDrawPriority();

  /** Gets the drawing intensity in the alpha blending for this item. */
  double getDrawIntensity(GraphicsContext graphicsContext);

  /** Returns the draw colors for this object from the graphics context. */
  Color[] getDrawColors(GraphicsContext graphicsContext);
}

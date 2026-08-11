package app.freerouting.boardgraphics;

import java.awt.Color;
import java.awt.Graphics;

/** Items drawn by {@link GraphicsContext} must implement this interface. */
public interface Drawable {

  int MIN_DRAW_PRIORITY = 1;
  int MIDDLE_DRAW_PRIORITY = 3;
  int MAX_DRAW_PRIORITY = 3;

  /**
   * Draws this item using per-layer colors from {@code colorArr}.
   *
   * <p>{@code colorArr} has one entry per layer. {@code intensity} is between 0 and 1.
   */
  void draw(Graphics g, GraphicsContext graphicsContext, Color[] colorArr, double intensity);

  /**
   * Draws this item using the same color on every layer.
   *
   * <p>{@code intensity} is a number between 0 and 1.
   */
  void draw(Graphics g, GraphicsContext graphicsContext, Color color, double intensity);

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

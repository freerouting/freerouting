package app.freerouting.boardgraphics;

import java.awt.Color;
import java.awt.Graphics;

/** items to be drawn by the functions in GraphicsContext must implement this interface */
public interface Drawable {
  int MIN_DRAW_PRIORITY = 1;
  int MIDDLE_DRAW_PRIORITY = 3;
  int MAX_DRAW_PRIORITY = 3;

  /**
   * Draws this item to the device provided in p_graphics_context. p_color_arr is an array of
   * dimenssion layer_count. p_intensity is a number between between 0 and 1.
   */
  void draw(
      Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity);

  /**
   * Draws this item to the device provided in p_graphics_context. It is drawn on each layer with
   * the same color p_color. p_intensity is a number between 0 and 1.
   */
  void draw(Graphics p_g, GraphicsContext p_graphics_context, Color p_color, double p_intensity);

  /**
   * Returns the priority for drawing an item. Items with higher priority are drawn later than items
   * with lower priority.
   */
  int get_draw_priority();

  /** Gets the drawing intensity in the alpha blending for this item. */
  double get_draw_intensity(GraphicsContext p_graphics_context);

  /** gets the draw colors for this object from p_graphics_context */
  Color[] get_draw_colors(GraphicsContext p_graphics_context);
}

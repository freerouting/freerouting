package app.freerouting.boardgraphics;

import java.awt.Color;

/** Information for drawing an item on the screen. */
public class ItemDrawInfo {

  /** The color of the item on each layer */
  public final Color[] layer_color;
  // The translucency factor of the color. Must be between 0 and 1.
  public final double intensity;

  /** Creates a new instance of ItemDrawInfo */
  public ItemDrawInfo(Color[] p_layer_color, double p_intensity) {
    layer_color = p_layer_color;
    intensity = p_intensity;
  }
}

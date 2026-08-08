package app.freerouting.boardgraphics;

import java.awt.Color;

/** Information for drawing an item on the screen. */
public class ItemDrawInfo {

  /** The color of the item on each layer */
  public final Color[] layerColor;

  // The translucency factor of the color. Must be between 0 and 1.
  public final double intensity;

  /** Creates a new instance of ItemDrawInfo */
  public ItemDrawInfo(Color[] pLayerColor, double pIntensity) {
    layerColor = pLayerColor;
    intensity = pIntensity;
  }
}

package app.freerouting.boardgraphics;

import java.awt.Color;

/**
 * Freerouting website brand colors for the bundled {@code tutorial_board.dsn} design.
 *
 * <p>Values match {@code website/style.css}: primary dark green background and secondary gold
 * accent.
 */
public final class TutorialBoardPalette {

  /** Website primary — dark green ({@code rgb(1, 58, 32)}). */
  public static final Color PRIMARY = new Color(1, 58, 32);

  /** Website secondary — gold/cream ({@code rgb(232, 204, 135)}). */
  public static final Color SECONDARY = new Color(232, 204, 135);

  /** Website secondary hover / muted gold ({@code rgb(200, 176, 116)}). */
  public static final Color SECONDARY_HOVER = new Color(200, 176, 116);

  private static final String TUTORIAL_BOARD_FILENAME = "tutorial_board.dsn";

  private TutorialBoardPalette() {}

  public static boolean isTutorialBoard(String filename) {
    return TUTORIAL_BOARD_FILENAME.equals(filename);
  }

  /** Applies the website brand palette to the given graphics context. */
  public static void apply(GraphicsContext graphicsContext) {
    if (graphicsContext == null) {
      return;
    }

    int layerCount = Math.max(graphicsContext.layer_count(), 1);
    Color[] primaryLayerColors = alternatingLayerColors(layerCount, SECONDARY, SECONDARY_HOVER);
    Color[] mutedLayerColors = alternatingLayerColors(layerCount, SECONDARY_HOVER, SECONDARY);

    graphicsContext.otherColorTable.set_background_color(PRIMARY);
    graphicsContext.otherColorTable.set_outline_color(SECONDARY);
    graphicsContext.otherColorTable.set_hilight_color(SECONDARY);
    graphicsContext.otherColorTable.set_incomplete_color(SECONDARY_HOVER);
    graphicsContext.otherColorTable.set_violations_color(SECONDARY);
    graphicsContext.otherColorTable.set_component_color(SECONDARY, true);
    graphicsContext.otherColorTable.set_component_color(SECONDARY_HOVER, false);
    graphicsContext.otherColorTable.set_silkscreen_color(SECONDARY, true);
    graphicsContext.otherColorTable.set_silkscreen_color(SECONDARY_HOVER, false);
    graphicsContext.otherColorTable.set_drill_hole_color(new Color(0, 28, 15));

    graphicsContext.itemColorTable.set_trace_colors(primaryLayerColors, false);
    graphicsContext.itemColorTable.set_trace_colors(mutedLayerColors, true);
    graphicsContext.itemColorTable.set_via_colors(primaryLayerColors, false);
    graphicsContext.itemColorTable.set_via_colors(mutedLayerColors, true);
    graphicsContext.itemColorTable.set_pin_colors(primaryLayerColors);
    graphicsContext.itemColorTable.set_conduction_colors(primaryLayerColors);
    graphicsContext.itemColorTable.set_keepout_colors(
        repeatedColor(layerCount, new Color(0, 40, 22)));
    graphicsContext.itemColorTable.set_via_keepout_colors(
        repeatedColor(layerCount, new Color(0, 40, 22)));
    graphicsContext.itemColorTable.set_place_keepout_colors(
        repeatedColor(layerCount, new Color(0, 40, 22)));

    graphicsContext.colorIntensityTable.set_value(
        ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(), 0.85);
    graphicsContext.colorIntensityTable.set_value(
        ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal(), 0.35);
    graphicsContext.colorIntensityTable.set_value(
        ColorIntensityTable.ObjectNames.HILIGHT.ordinal(), 1.0);
    graphicsContext.colorIntensityTable.set_value(
        ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal(), 1.0);
  }

  public static Color backgroundColor() {
    return PRIMARY;
  }

  private static Color[] alternatingLayerColors(
      int layerCount, Color evenLayerColor, Color oddLayerColor) {
    Color[] colors = new Color[layerCount];
    for (int i = 0; i < layerCount; i++) {
      colors[i] = i % 2 == 0 ? evenLayerColor : oddLayerColor;
    }
    return colors;
  }

  private static Color[] repeatedColor(int layerCount, Color color) {
    Color[] colors = new Color[layerCount];
    for (int i = 0; i < layerCount; i++) {
      colors[i] = color;
    }
    return colors;
  }
}

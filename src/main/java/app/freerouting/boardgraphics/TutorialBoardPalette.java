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

    int layerCount = Math.max(graphicsContext.layerCount(), 1);
    Color[] primaryLayerColors = alternatingLayerColors(layerCount, SECONDARY, SECONDARY_HOVER);
    Color[] mutedLayerColors = alternatingLayerColors(layerCount, SECONDARY_HOVER, SECONDARY);

    graphicsContext.otherColorTable.setBackgroundColor(PRIMARY);
    graphicsContext.otherColorTable.setOutlineColor(SECONDARY);
    graphicsContext.otherColorTable.setHighlightColor(SECONDARY);
    graphicsContext.otherColorTable.setIncompleteColor(SECONDARY_HOVER);
    graphicsContext.otherColorTable.setViolationsColor(SECONDARY);
    graphicsContext.otherColorTable.setComponentColor(SECONDARY, true);
    graphicsContext.otherColorTable.setComponentColor(SECONDARY_HOVER, false);
    graphicsContext.otherColorTable.setSilkscreenColor(SECONDARY, true);
    graphicsContext.otherColorTable.setSilkscreenColor(SECONDARY_HOVER, false);
    graphicsContext.otherColorTable.setDrillHoleColor(new Color(0, 28, 15));

    graphicsContext.itemColorTable.setTraceColors(primaryLayerColors, false);
    graphicsContext.itemColorTable.setTraceColors(mutedLayerColors, true);
    graphicsContext.itemColorTable.setViaColors(primaryLayerColors, false);
    graphicsContext.itemColorTable.setViaColors(mutedLayerColors, true);
    graphicsContext.itemColorTable.setPinColors(primaryLayerColors);
    graphicsContext.itemColorTable.setConductionColors(primaryLayerColors);
    graphicsContext.itemColorTable.setKeepoutColors(
        repeatedColor(layerCount, new Color(0, 40, 22)));
    graphicsContext.itemColorTable.setViaKeepoutColors(
        repeatedColor(layerCount, new Color(0, 40, 22)));
    graphicsContext.itemColorTable.setPlaceKeepoutColors(
        repeatedColor(layerCount, new Color(0, 40, 22)));

    graphicsContext.colorIntensityTable.setValue(
        ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(), 0.85);
    graphicsContext.colorIntensityTable.setValue(
        ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal(), 0.35);
    graphicsContext.colorIntensityTable.setValue(
        ColorIntensityTable.ObjectNames.HIGHLIGHT.ordinal(), 1.0);
    graphicsContext.colorIntensityTable.setValue(
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

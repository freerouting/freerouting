package app.freerouting.boardgraphics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.Layer;
import app.freerouting.board.LayerStructure;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TutorialBoardPaletteTest {

  @Test
  void brandColorsMatchWebsiteStylesheet() {
    assertEquals(new Color(1, 58, 32), TutorialBoardPalette.PRIMARY);
    assertEquals(new Color(232, 204, 135), TutorialBoardPalette.SECONDARY);
    assertEquals(new Color(200, 176, 116), TutorialBoardPalette.SECONDARY_HOVER);
  }

  @Test
  void applyUsesPrimaryBackgroundAndSecondaryOutline() {
    LayerStructure layerStructure =
        new LayerStructure(new Layer[] {new Layer("F.Cu", true), new Layer("B.Cu", true)});
    GraphicsContext graphicsContext =
        new GraphicsContext(
            new IntBox(new IntPoint(0, 0), new IntPoint(1000, 1000)),
            new Dimension(800, 600),
            layerStructure,
            Locale.ENGLISH);

    TutorialBoardPalette.apply(graphicsContext);

    assertEquals(
        TutorialBoardPalette.PRIMARY, graphicsContext.otherColorTable.getBackgroundColor());
    assertEquals(
        TutorialBoardPalette.SECONDARY, graphicsContext.otherColorTable.getOutlineColor());
    assertEquals(
        TutorialBoardPalette.SECONDARY, graphicsContext.itemColorTable.getTraceColors(false)[0]);
    assertEquals(
        TutorialBoardPalette.SECONDARY_HOVER,
        graphicsContext.itemColorTable.getTraceColors(false)[1]);
  }

  @Test
  void recognizesTutorialFilename() {
    assertTrue(TutorialBoardPalette.isTutorialBoard("tutorial_board.dsn"));
  }
}

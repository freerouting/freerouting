package app.freerouting.gui.rendering;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.AutorouteDiagnostic;
import app.freerouting.board.BasicBoard;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Early Phase 6 renderer smoke test.
 *
 * <p>The test is component-only and runs in the forced-headless {@code testGui} task. It verifies
 * that the GUI renderer entry point can render a representative board into an offscreen image
 * before any board paint APIs are removed.
 */
@Tag("gui")
class BoardRendererOffscreenTest {

  private static final String FIXTURE = "Issue575-drc_dev-board_4_hole_clearance_violations.dsn";
  private static final int IMAGE_WIDTH = 800;
  private static final int IMAGE_HEIGHT = 600;

  private static BasicBoard loadBoard() throws Exception {
    BoardReadResult result;
    try (FileInputStream in = new FileInputStream("fixtures/" + FIXTURE)) {
      result = DsnReader.readBoard(in, null, null, "phase6-renderer");
    }
    return switch (result) {
      case BoardReadResult.Success s -> (BasicBoard) s.board();
      case BoardReadResult.OutlineMissing o -> (BasicBoard) o.board();
      default -> throw new IllegalStateException("Failed to read board: " + result);
    };
  }

  @Test
  void rendersRepresentativeBoardIntoOffscreenImage() throws Exception {
    BasicBoard board = loadBoard();
    IntBox designBounds = board.getBoundingBox();
    GraphicsContext graphicsContext =
        new GraphicsContext(
            designBounds,
            new Dimension(IMAGE_WIDTH, IMAGE_HEIGHT),
            board.layerStructure,
            Locale.ENGLISH);
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);

    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setClip(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
      BoardRenderer.draw(board, graphics, graphicsContext);
    } finally {
      graphics.dispose();
    }

    int[] pixels = image.getRGB(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null, 0, IMAGE_WIDTH);
    long nonTransparentPixels =
        Arrays.stream(pixels).filter(pixel -> ((pixel >>> 24) & 0xFF) != 0).count();
    assertTrue(
        nonTransparentPixels > 0,
        "renderer must draw at least one visible pixel into the offscreen image");
  }

  @Test
  void rendersAutorouteDiagnosticSnapshotIntoOffscreenImage() throws Exception {
    BasicBoard board = loadBoard();
    IntBox designBounds = board.getBoundingBox();
    GraphicsContext graphicsContext =
        new GraphicsContext(
            designBounds,
            new Dimension(IMAGE_WIDTH, IMAGE_HEIGHT),
            board.layerStructure,
            Locale.ENGLISH);
    int centerX = (designBounds.ll.x + designBounds.ur.x) / 2;
    int centerY = (designBounds.ll.y + designBounds.ur.y) / 2;
    TileShape shape =
        TileShape.getInstance(centerX - 1000, centerY - 1000, centerX + 1000, centerY + 1000);
    AutorouteDiagnostic diagnostic =
        new AutorouteDiagnostic(AutorouteDiagnostic.Kind.FREE_SPACE_ROOM, shape, 0, 1.0);
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);

    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setClip(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
      AutorouteDiagnosticRenderer.render(diagnostic, graphics, graphicsContext);
    } finally {
      graphics.dispose();
    }

    int[] pixels = image.getRGB(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null, 0, IMAGE_WIDTH);
    long nonTransparentPixels =
        Arrays.stream(pixels).filter(pixel -> ((pixel >>> 24) & 0xFF) != 0).count();
    assertTrue(
        nonTransparentPixels > 0,
        "diagnostic adapter must draw a snapshot into the offscreen image");
  }
}

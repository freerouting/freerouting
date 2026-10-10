package app.freerouting.gui.interactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.gui.rendering.GraphicsContext;
import app.freerouting.gui.workspace.GuiBoardManager;
import app.freerouting.gui.workspace.WorkspaceSettings;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Offscreen checks of detached component copy previews. */
@Tag("gui")
class CopyItemPreviewTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void previewFollowsCursorWithoutMovingSourceComponent(boolean keepout) throws Exception {
    try (var input = Files.newInputStream(Path.of("fixtures/Issue955-rotated-pad-contacts.dsn"))) {
      var board = ((BoardReadResult.Success) DsnReader.readBoard(input, null, null)).board();
      var pin =
          board.getPins().stream()
              .filter(p -> p.componentName().equals("S21") && p.name().equals("2@1"))
              .findFirst()
              .orElseThrow();
      var component = board.components.get(pin.getComponentId());
      final var sourceLocation = component.getExactLocation();
      final Item item =
          keepout
              ? new ObstacleArea(
                  new Circle(Point.ZERO, 3000),
                  0,
                  Vector.ZERO,
                  0,
                  false,
                  1,
                  0,
                  component.id,
                  "preview",
                  FixedState.UNFIXED,
                  board)
              : pin;
      var manager = mock(GuiBoardManager.class);
      when(manager.getLocale()).thenReturn(Locale.ENGLISH);
      when(manager.getWorkspaceSettings()).thenReturn(mock(WorkspaceSettings.class));
      manager.graphicsContext =
          new GraphicsContext(
              board.getBoundingBox(),
              new Dimension(1200, 900),
              board.layerStructure,
              Locale.ENGLISH);
      var start = component.getLocation().toFloat();
      var state = CopyItemState.getInstance(start, List.of(item), null, manager);
      var before = renderBounds(state);
      assertTrue(before.width > 0 && before.height > 0);
      // Drive the cursor geometry without needing an AWT window or status bar.
      var move = CopyItemState.class.getDeclaredMethod("changePosition", FloatPoint.class);
      move.setAccessible(true);
      for (int step = 1; step <= 2; step++) {
        var target = new FloatPoint(start.x + step * 10000, start.y - step * 20000);
        move.invoke(state, target);
        var after = renderBounds(state);
        var screenStart = manager.graphicsContext.coordinateTransform.boardToScreen(start);
        var screenTarget = manager.graphicsContext.coordinateTransform.boardToScreen(target);
        assertEquals(before.x + Math.round(screenTarget.getX() - screenStart.getX()), after.x);
        assertEquals(before.y + Math.round(screenTarget.getY() - screenStart.getY()), after.y);
        assertEquals(before.width, after.width);
        assertEquals(before.height, after.height);
        assertEquals(sourceLocation, component.getExactLocation());
      }
    }
  }

  private Rectangle renderBounds(CopyItemState state) {
    var image = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    try {
      graphics.setClip(0, 0, 1200, 900);
      state.draw(graphics);
    } finally {
      graphics.dispose();
    }
    Rectangle bounds = null;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) {
          if (bounds == null) {
            bounds = new Rectangle(x, y, 1, 1);
          } else {
            bounds.add(x, y);
          }
        }
      }
    }
    return bounds == null ? new Rectangle() : bounds;
  }
}

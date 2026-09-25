package app.freerouting.gui.workspace.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.gui.board.BoardPanel;
import app.freerouting.gui.rendering.GraphicsContext;
import app.freerouting.gui.rendering.ScreenTransform;
import app.freerouting.gui.workspace.GuiBoardManager;
import java.awt.Rectangle;
import org.junit.jupiter.api.Test;

class GuiBoardPresentationControllerTest {

  @Test
  void repaintRequestsFullPanelRepaintWhenNotReadOnly() {
    GuiBoardManager manager = mock(GuiBoardManager.class);
    BoardPanel panel = mock(BoardPanel.class);
    when(manager.getPanel()).thenReturn(panel);
    when(manager.isPaintImmediately()).thenReturn(false);
    when(manager.isBoardReadOnly()).thenReturn(false);

    GuiBoardPresentationController controller = new GuiBoardPresentationController(manager);
    controller.repaint();

    verify(panel).repaint();
    verify(panel, never()).repaint(any(Rectangle.class));
  }

  @Test
  void repaintWithRectangleDelegatesToPanelRepaintWithRectangle() {
    GuiBoardManager manager = mock(GuiBoardManager.class);
    BoardPanel panel = mock(BoardPanel.class);
    when(manager.getPanel()).thenReturn(panel);
    when(manager.isPaintImmediately()).thenReturn(false);

    GuiBoardPresentationController controller = new GuiBoardPresentationController(manager);
    Rectangle rect = new Rectangle(10, 20, 100, 200);
    controller.repaint(rect);

    verify(panel).repaint(rect);
  }

  @Test
  void getGraphicsUpdateRectangleReturnsEmptyWhenNoUpdateBox() {
    GuiBoardManager manager = mock(GuiBoardManager.class);
    when(manager.getPresentationBoard()).thenReturn(null);

    GuiBoardPresentationController controller = new GuiBoardPresentationController(manager);
    Rectangle rect = controller.getGraphicsUpdateRectangle();

    assertEquals(new Rectangle(0, 0, 0, 0), rect);
  }

  @Test
  void getGraphicsUpdateRectangleTransformsBoardBoxToScreen() {
    GuiBoardManager manager = mock(GuiBoardManager.class);
    RoutingBoard board = mock(RoutingBoard.class);
    GraphicsContext graphicsContext = mock(GraphicsContext.class);
    ScreenTransform transform = mock(ScreenTransform.class);

    IntBox updateBox = new IntBox(new IntPoint(100, 100), new IntPoint(500, 500));
    when(board.getGraphicsUpdateBox()).thenReturn(updateBox);
    when(board.getMaxTraceHalfWidth()).thenReturn(10);
    when(manager.getPresentationBoard()).thenReturn(board);
    when(manager.getPresentationGraphicsContext()).thenReturn(graphicsContext);
    graphicsContext.coordinateTransform = transform;

    IntBox expectedOffsetBox = updateBox.offset(10);
    Rectangle expectedScreenRect = new Rectangle(5, 5, 200, 200);
    when(transform.boardToScreen(any(IntBox.class))).thenReturn(expectedScreenRect);

    GuiBoardPresentationController controller = new GuiBoardPresentationController(manager);
    Rectangle actual = controller.getGraphicsUpdateRectangle();

    assertEquals(expectedScreenRect, actual);
  }
}

package app.freerouting.gui.workspace;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.Point;
import app.freerouting.gui.BoardPanel;
import app.freerouting.gui.BoardSavableSubWindow;
import app.freerouting.gui.rendering.BoardRenderer;
import app.freerouting.gui.rendering.GraphicsContext;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.SwingUtilities;

/**
 * Owns the board manager's presentation-only operations.
 *
 * <p>{@link GuiBoardManager} remains the public façade. This collaborator deliberately receives
 * presentation state through package-private accessors so rendering never needs to know about the
 * concrete interactive states or the {@link WorkspaceSettings} singleton.
 */
final class GuiBoardPresentationController {

  private static final long BACKGROUND_REPAINT_INTERVAL = 1000;
  private static final long INTERACTIVE_REPAINT_INTERVAL = 33;
  private static long lastRepaintedTime;

  private final GuiBoardManager manager;

  GuiBoardPresentationController(GuiBoardManager manager) {
    this.manager = manager;
  }

  void refreshGuiFromSettings() {
    WorkspaceSettings workspaceSettings = manager.getWorkspaceSettings();
    if (workspaceSettings == null || manager.getPanel() == null) {
      return;
    }
    if (manager.getBoardFrame() == null) {
      return;
    }

    for (BoardSavableSubWindow subwindow : manager.getBoardFrame().getPermanentSubwindows()) {
      if (subwindow == null) {
        continue;
      }
      workspaceSettings.addPropertyChangeListener(
          _ -> SwingUtilities.invokeLater(subwindow::refresh));
      subwindow.refresh();
    }
  }

  void repaint() {
    BoardPanel panel = manager.getPanel();
    if (manager.isPaintImmediately()) {
      panel.paintImmediately(new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE));
      return;
    }

    long interval =
        manager.isInInteractiveDrag() ? INTERACTIVE_REPAINT_INTERVAL : BACKGROUND_REPAINT_INTERVAL;
    long now = System.currentTimeMillis();
    if (lastRepaintedTime >= now - interval) {
      return;
    }
    lastRepaintedTime = now;

    Rectangle updateRectangle = getGraphicsUpdateRectangle();
    if (updateRectangle.width > 0 && updateRectangle.height > 0) {
      panel.repaint(updateRectangle);
    } else {
      panel.repaint();
    }
  }

  void repaint(Rectangle rectangle) {
    if (manager.isPaintImmediately()) {
      manager.getPanel().paintImmediately(rectangle);
    } else {
      manager.getPanel().repaint(rectangle);
    }
  }

  Rectangle getGraphicsUpdateRectangle() {
    RoutingBoard board = manager.getPresentationBoard();
    IntBox updateBox = board.getGraphicsUpdateBox();
    if (updateBox == null || updateBox.isEmpty()) {
      return new Rectangle(0, 0, 0, 0);
    }
    IntBox offsetBox = updateBox.offset(board.getMaxTraceHalfWidth());
    return manager.getPresentationGraphicsContext().coordinateTransform.boardToScreen(offsetBox);
  }

  void adjustDesignBounds() {
    RoutingBoard board = manager.getPresentationBoard();
    IntBox newBoundingBox = board.getBoundingBox();
    for (Item currentItem : board.getItems()) {
      IntBox currentBoundingBox = currentItem.boundingBox();
      if (currentBoundingBox.ur.x < Integer.MAX_VALUE) {
        newBoundingBox = newBoundingBox.union(currentBoundingBox);
      }
    }
    manager.getPresentationGraphicsContext().changeDesignBounds(newBoundingBox);
  }

  void draw(Graphics graphics) {
    RoutingBoard board = manager.getPresentationBoard();
    GraphicsContext graphicsContext = manager.getPresentationGraphicsContext();
    if (board == null) {
      return;
    }

    BoardRenderer.draw(board, graphics, graphicsContext);
    if (manager.getPresentationRatsNest() != null) {
      manager.getPresentationRatsNest().draw(graphics, graphicsContext);
    }
    if (manager.getPresentationClearanceViolations() != null) {
      manager.getPresentationClearanceViolations().draw(graphics, graphicsContext);
    }
    if (manager.getPresentationEditorStateController() != null) {
      manager.getPresentationEditorStateController().draw(graphics);
    }
    if (manager.getPresentationInteractiveActionThread() != null) {
      manager.getPresentationInteractiveActionThread().draw(graphics);
    }

    Point[] impactedPoints = manager.getImpactedPoints();
    if (impactedPoints != null && impactedPoints.length > 0) {
      drawImpactedPointsIndicators(graphics, board, graphicsContext, impactedPoints);
    }
  }

  private void drawImpactedPointsIndicators(
      Graphics graphics,
      RoutingBoard board,
      GraphicsContext graphicsContext,
      Point[] impactedPoints) {
    Color drawColor = graphicsContext.getHighlightColor();
    double drawIntensity = graphicsContext.getHighlightColorIntensity();
    int defaultTraceHalfWidth = board.rules.getDefaultTraceHalfWidth(0);
    double radius = Math.max(5 * defaultTraceHalfWidth / 10, 500);
    final double drawWidth = 50.0;

    for (Point point : impactedPoints) {
      if (point == null) {
        continue;
      }
      FloatPoint center = point.toFloat();
      FloatPoint[] drawPoints = new FloatPoint[2];
      drawPoints[0] = new FloatPoint(center.x - radius, center.y - radius);
      drawPoints[1] = new FloatPoint(center.x + radius, center.y + radius);
      graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);
      drawPoints[0] = new FloatPoint(center.x + radius, center.y - radius);
      drawPoints[1] = new FloatPoint(center.x - radius, center.y + radius);
      graphicsContext.draw(drawPoints, drawWidth, drawColor, graphics, drawIntensity);
      graphicsContext.drawCircle(center, radius, drawWidth, drawColor, graphics, drawIntensity);
    }
  }
}

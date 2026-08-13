package app.freerouting.boardgraphics;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.ComponentObstacleArea;
import app.freerouting.board.ComponentOutline;
import app.freerouting.board.Item;
import app.freerouting.geometry.planar.IntBox;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI-owned entry point for rendering a board.
 *
 * <p>The renderer owns board traversal, layer ordering, viewport culling, component labels, and
 * dispatch to the existing item paint APIs. Later Phase 6 commits replace those paint calls with
 * renderer-owned item-family strategies.
 */
public final class BoardRenderer {

  private BoardRenderer() {}

  /** Draws the complete board using renderer-owned traversal and ordering. */
  public static void draw(BasicBoard board, Graphics graphics, GraphicsContext graphicsContext) {
    if (board == null || graphics == null || graphicsContext == null) {
      return;
    }

    int activeLayer = graphicsContext.getFullyVisibleLayer();
    int activeVirtualLayer = graphicsContext.getFullyVisibleVirtualLayer();
    BasicBoard.DominantSide dominantSide =
        determineDominantSide(board, activeLayer, activeVirtualLayer);
    List<RenderStep> drawSteps =
        createDrawSteps(board.getLayerCount(), dominantSide, activeLayer, activeVirtualLayer);
    List<Item> allItems = new ArrayList<>(board.getItems());

    @SuppressWarnings("unchecked")
    List<Item>[] itemsByPriority = (List<Item>[]) new List[Drawable.MAX_DRAW_PRIORITY + 1];
    for (int priority = 0; priority <= Drawable.MAX_DRAW_PRIORITY; priority++) {
      itemsByPriority[priority] = new ArrayList<>();
    }
    for (Item item : allItems) {
      int priority = item.getDrawPriority();
      if (priority >= 0 && priority <= Drawable.MAX_DRAW_PRIORITY) {
        itemsByPriority[priority].add(item);
      }
    }

    java.awt.Rectangle clipRect = graphics.getClipBounds();
    IntBox clipBox =
        clipRect != null ? graphicsContext.coordinateTransform.screenToBoard(clipRect) : null;
    for (int priority = Drawable.MIN_DRAW_PRIORITY;
        priority <= Drawable.MAX_DRAW_PRIORITY;
        priority++) {
      for (RenderStep step : drawSteps) {
        for (Item item : itemsByPriority[priority]) {
          if (clipBox != null && !clipBox.intersects(item.boundingBox())) {
            continue;
          }
          renderItem(item, step, graphics, graphicsContext);
        }
      }
    }

    drawComponentPartNumbers(board, graphics, graphicsContext);
  }

  private static BasicBoard.DominantSide determineDominantSide(
      BasicBoard board, int activeLayer, int activeVirtualLayer) {
    if (activeVirtualLayer != -1) {
      return activeVirtualLayer % 2 == 0
          ? BasicBoard.DominantSide.FRONT
          : BasicBoard.DominantSide.BACK;
    }
    if (activeLayer == 0) {
      return BasicBoard.DominantSide.FRONT;
    }
    if (activeLayer == board.getLayerCount() - 1) {
      return BasicBoard.DominantSide.BACK;
    }
    return BasicBoard.DominantSide.NONE;
  }

  private static List<RenderStep> createDrawSteps(
      int layerCount,
      BasicBoard.DominantSide dominantSide,
      int activeLayer,
      int activeVirtualLayer) {
    List<RenderStep> drawSteps = new ArrayList<>();
    if (dominantSide == BasicBoard.DominantSide.BACK) {
      drawSteps.add(new RenderStep(true, 4));
      drawSteps.add(new RenderStep(true, 2));
      drawSteps.add(new RenderStep(true, 0));
      drawSteps.add(new RenderStep(false, 0));
      for (int layer = 1; layer < layerCount - 1; layer++) {
        drawSteps.add(new RenderStep(false, layer));
      }
      drawSteps.add(new RenderStep(true, 5));
      drawSteps.add(new RenderStep(true, 3));
      drawSteps.add(new RenderStep(true, 1));
      if (layerCount > 1) {
        drawSteps.add(new RenderStep(false, layerCount - 1));
      }
    } else {
      drawSteps.add(new RenderStep(true, 5));
      drawSteps.add(new RenderStep(true, 3));
      drawSteps.add(new RenderStep(true, 1));
      if (layerCount > 1) {
        drawSteps.add(new RenderStep(false, layerCount - 1));
      }
      for (int layer = layerCount - 2; layer >= 1; layer--) {
        drawSteps.add(new RenderStep(false, layer));
      }
      drawSteps.add(new RenderStep(true, 4));
      drawSteps.add(new RenderStep(true, 2));
      drawSteps.add(new RenderStep(true, 0));
      drawSteps.add(new RenderStep(false, 0));
    }

    RenderStep selectedStep =
        activeVirtualLayer != -1
            ? new RenderStep(true, activeVirtualLayer)
            : activeLayer != -1 ? new RenderStep(false, activeLayer) : null;
    if (selectedStep != null) {
      drawSteps.remove(selectedStep);
      drawSteps.add(selectedStep);
    }
    return drawSteps;
  }

  private static void renderItem(
      Item item, RenderStep step, Graphics graphics, GraphicsContext graphicsContext) {
    switch (item.getBoardItemType()) {
      case COMPONENT_OUTLINE ->
          renderComponentOutline((ComponentOutline) item, step, graphics, graphicsContext);
      case COMPONENT_OBSTACLE_AREA ->
          renderComponentObstacleArea(
              (ComponentObstacleArea) item, step, graphics, graphicsContext);
      case TRACE, PIN, VIA, OBSTACLE_AREA, CONDUCTION_AREA, BOARD_OUTLINE, OTHER ->
          renderPhysicalItem(item, step, graphics, graphicsContext);
      default -> renderPhysicalItem(item, step, graphics, graphicsContext);
    }
  }

  private static void renderComponentOutline(
      ComponentOutline outline,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext) {
    if (step.virtual() && virtualLayerFor(outline) == step.index()) {
      outline.draw(graphics, graphicsContext);
    }
  }

  private static void renderComponentObstacleArea(
      ComponentObstacleArea area,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext) {
    if (step.virtual() && virtualLayerFor(area) == step.index()) {
      area.draw(graphics, graphicsContext);
    }
  }

  private static void renderPhysicalItem(
      Item item, RenderStep step, Graphics graphics, GraphicsContext graphicsContext) {
    if (!step.virtual()) {
      item.drawLayer(graphics, graphicsContext, step.index());
    }
  }

  private static int virtualLayerFor(ComponentOutline outline) {
    if (outline.isCourtyard()) {
      return outline.isFront() ? 2 : 3;
    }
    if (outline.isFabrication()) {
      return outline.isFront() ? 4 : 5;
    }
    return outline.isFront() ? 0 : 1;
  }

  private static int virtualLayerFor(ComponentObstacleArea area) {
    return area.isFront() ? 2 : 3;
  }

  private static void drawComponentPartNumbers(
      BasicBoard board, Graphics graphics, GraphicsContext graphicsContext) {
    double frontIntensity = graphicsContext.getVirtualLayerVisibility(4);
    double backIntensity = graphicsContext.getVirtualLayerVisibility(5);
    if (frontIntensity <= 0 && backIntensity <= 0) {
      return;
    }

    Graphics2D graphics2D = (Graphics2D) graphics;
    java.awt.Font originalFont = graphics2D.getFont();
    java.awt.Composite originalComposite = graphics2D.getComposite();
    graphics2D.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12));
    try {
      for (var component : board.components.getAll()) {
        if (!component.isPlaced()
            || component.getPartNumber() == null
            || component.getPartNumber().isEmpty()) {
          continue;
        }
        boolean front = component.placedOnFront();
        double intensity = front ? frontIntensity : backIntensity;
        if (intensity <= 0) {
          continue;
        }
        java.awt.Color color =
            front
                ? graphicsContext.otherColorTable.getFabColor(true)
                : graphicsContext.otherColorTable.getFabColor(false);
        if (color == null) {
          continue;
        }
        graphics2D.setColor(color);
        graphics2D.setComposite(
            java.awt.AlphaComposite.getInstance(
                java.awt.AlphaComposite.SRC_OVER, (float) Math.min(1.0, intensity)));
        java.awt.geom.Point2D screenLocation =
            graphicsContext.coordinateTransform.boardToScreen(component.getLocation().toFloat());
        java.awt.FontMetrics metrics = graphics2D.getFontMetrics();
        int textWidth = metrics.stringWidth(component.getPartNumber());
        int textHeight = metrics.getAscent();
        graphics2D.drawString(
            component.getPartNumber(),
            (float) (screenLocation.getX() - textWidth / 2.0),
            (float) (screenLocation.getY() + textHeight / 2.0));
      }
    } finally {
      graphics2D.setComposite(originalComposite);
      graphics2D.setFont(originalFont);
    }
  }

  private record RenderStep(boolean virtual, int index) {}
}

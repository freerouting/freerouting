package app.freerouting.gui.rendering;

import app.freerouting.board.BasicBoard;
import app.freerouting.board.BoardOutline;
import app.freerouting.board.ComponentObstacleArea;
import app.freerouting.board.ComponentOutline;
import app.freerouting.board.ConductionArea;
import app.freerouting.board.DrillItem;
import app.freerouting.board.Item;
import app.freerouting.board.ObstacleArea;
import app.freerouting.board.Pin;
import app.freerouting.board.PolylineTrace;
import app.freerouting.board.Unit;
import app.freerouting.board.Via;
import app.freerouting.board.ViaObstacleArea;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Shape;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI-owned entry point for rendering a board.
 *
 * <p>The renderer owns board traversal, layer ordering, viewport culling, component labels, and
 * dispatch to renderer-owned item-family strategies.
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
    List<Item>[] itemsByPriority = (List<Item>[]) new List[MAX_DRAW_PRIORITY + 1];
    for (int priority = 0; priority <= MAX_DRAW_PRIORITY; priority++) {
      itemsByPriority[priority] = new ArrayList<>();
    }
    for (Item item : allItems) {
      int priority = drawPriority(item);
      if (priority >= 0 && priority <= MAX_DRAW_PRIORITY) {
        itemsByPriority[priority].add(item);
      }
    }

    java.awt.Rectangle clipRect = graphics.getClipBounds();
    IntBox clipBox =
        clipRect != null ? graphicsContext.coordinateTransform.screenToBoard(clipRect) : null;
    for (int priority = MIN_DRAW_PRIORITY; priority <= MAX_DRAW_PRIORITY; priority++) {
      for (RenderStep step : drawSteps) {
        for (Item item : itemsByPriority[priority]) {
          if (clipBox != null && !clipBox.intersects(item.boundingBox())) {
            continue;
          }
          renderItem(item, step, graphics, graphicsContext, null);
        }
      }
    }

    drawComponentPartNumbers(board, graphics, graphicsContext);
  }

  /**
   * Draws an interactive overlay item through the renderer boundary.
   *
   * <p>Overlay painting uses the same family strategies as normal board painting, with the
   * requested colors and intensity supplied as a renderer-owned style.
   */
  public static void drawOverlayItem(
      Item item, Graphics graphics, GraphicsContext graphicsContext) {
    if (item != null && graphics != null && graphicsContext != null) {
      drawOverlayItem(
          item,
          graphics,
          graphicsContext,
          drawColors(item, graphicsContext),
          drawIntensity(item, graphicsContext));
    }
  }

  /** Draws an interactive overlay item with caller-supplied colors and intensity. */
  public static void drawOverlayItem(
      Item item,
      Graphics graphics,
      GraphicsContext graphicsContext,
      Color color,
      double intensity) {
    if (item != null && graphics != null && graphicsContext != null) {
      Color[] colors = new Color[item.board.getLayerCount()];
      java.util.Arrays.fill(colors, color);
      drawOverlayItem(item, graphics, graphicsContext, colors, intensity);
    }
  }

  /** Draws an interactive overlay item with per-layer colors and intensity. */
  public static void drawOverlayItem(
      Item item,
      Graphics graphics,
      GraphicsContext graphicsContext,
      Color[] colors,
      double intensity) {
    if (item != null && graphics != null && graphicsContext != null) {
      RenderStyle style = new RenderStyle(colors, intensity);
      for (int layer = 0; layer < item.board.getLayerCount(); layer++) {
        renderItem(item, new RenderStep(false, layer), graphics, graphicsContext, style);
      }
      for (int virtualLayer = 0; virtualLayer < 6; virtualLayer++) {
        renderItem(item, new RenderStep(true, virtualLayer), graphics, graphicsContext, style);
      }
    }
  }

  /** Draws an interactive overlay item with per-layer colors and intensity. */
  public static void drawHighlightedOverlayItem(
      Item item, Graphics graphics, GraphicsContext graphicsContext) {
    if (item == null || graphics == null || graphicsContext == null) {
      return;
    }
    Color[] colors = drawColors(item, graphicsContext);
    double intensity = Math.min(1.0, drawIntensity(item, graphicsContext) * 1.5);
    drawOverlayItem(item, graphics, graphicsContext, colors, intensity);
  }

  private static final int MIN_DRAW_PRIORITY = 1;
  private static final int MAX_DRAW_PRIORITY = 3;

  /** Returns the renderer-owned draw priority for an item family. */
  private static int drawPriority(Item item) {
    return switch (item.getBoardItemType()) {
      case BOARD_OUTLINE, COMPONENT_OUTLINE, TRACE, PIN, VIA -> MAX_DRAW_PRIORITY;
      default -> MIN_DRAW_PRIORITY;
    };
  }

  /** Returns the renderer-owned colors for an item family. */
  private static Color[] drawColors(Item item, GraphicsContext graphicsContext) {
    return switch (item.getBoardItemType()) {
      case BOARD_OUTLINE -> {
        Color[] colors = new Color[item.board.getLayerCount()];
        java.util.Arrays.fill(colors, graphicsContext.getOutlineColor());
        yield colors;
      }
      case COMPONENT_OUTLINE -> componentOutlineColors((ComponentOutline) item, graphicsContext);
      case COMPONENT_OBSTACLE_AREA ->
          componentObstacleColors((ComponentObstacleArea) item, graphicsContext);
      case TRACE -> graphicsContext.getTraceColors(item.isUserFixed());
      case PIN -> {
        Pin pin = (Pin) item;
        if (pin.netCount() == 0) {
          yield graphicsContext.getObstacleColors();
        }
        yield pin.firstLayer() != pin.lastLayer()
            ? graphicsContext.getTraceColors(pin.isUserFixed())
            : graphicsContext.getPinColors();
      }
      case VIA -> {
        Via via = (Via) item;
        yield via.netCount() == 0
            ? graphicsContext.getObstacleColors()
            : graphicsContext.getTraceColors(via.isUserFixed());
      }
      case VIA_OBSTACLE_AREA -> graphicsContext.getViaObstacleColors();
      case CONDUCTION_AREA -> graphicsContext.getTraceColors(true);
      case OBSTACLE_AREA -> graphicsContext.getObstacleColors();
      default -> new Color[item.board.getLayerCount()];
    };
  }

  private static Color[] componentOutlineColors(
      ComponentOutline outline, GraphicsContext graphicsContext) {
    Color frontColor;
    Color backColor;
    if (outline.isCourtyard()) {
      frontColor = graphicsContext.otherColorTable.getCourtyardColor(true);
      backColor = graphicsContext.otherColorTable.getCourtyardColor(false);
    } else if (outline.isFabrication()) {
      frontColor = graphicsContext.otherColorTable.getFabColor(true);
      backColor = graphicsContext.otherColorTable.getFabColor(false);
    } else {
      frontColor = graphicsContext.otherColorTable.getSilkscreenColor(true);
      backColor = graphicsContext.otherColorTable.getSilkscreenColor(false);
    }
    return frontBackColors(outline.board.getLayerCount(), frontColor, backColor);
  }

  private static Color[] componentObstacleColors(
      ComponentObstacleArea area, GraphicsContext graphicsContext) {
    return frontBackColors(
        area.board.getLayerCount(),
        graphicsContext.otherColorTable.getCourtyardColor(true),
        graphicsContext.otherColorTable.getCourtyardColor(false));
  }

  private static Color[] frontBackColors(int layerCount, Color frontColor, Color backColor) {
    Color[] colors = new Color[layerCount];
    for (int layer = 0; layer < colors.length - 1; layer++) {
      colors[layer] = frontColor;
    }
    if (colors.length > 0) {
      colors[colors.length - 1] = backColor;
    }
    return colors;
  }

  /** Returns the renderer-owned intensity for an item family. */
  private static double drawIntensity(Item item, GraphicsContext graphicsContext) {
    return switch (item.getBoardItemType()) {
      case BOARD_OUTLINE -> 1;
      case COMPONENT_OUTLINE, COMPONENT_OBSTACLE_AREA ->
          graphicsContext.getComponentOutlineColorIntensity();
      case TRACE -> graphicsContext.getTraceColorIntensity();
      case PIN -> graphicsContext.getPinColorIntensity();
      case VIA -> {
        Via via = (Via) item;
        if (via.netCount() == 0) {
          yield graphicsContext.getObstacleColorIntensity();
        }
        yield via.firstLayer() >= via.lastLayer()
            ? graphicsContext.getPinColorIntensity()
            : graphicsContext.getViaColorIntensity();
      }
      case CONDUCTION_AREA -> graphicsContext.getConductionColorIntensity();
      case VIA_OBSTACLE_AREA -> graphicsContext.getViaObstacleColorIntensity();
      case OBSTACLE_AREA -> graphicsContext.getObstacleColorIntensity();
      default -> 0;
    };
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
      Item item,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext,
      RenderStyle style) {
    switch (item.getBoardItemType()) {
      case COMPONENT_OUTLINE ->
          renderComponentOutline((ComponentOutline) item, step, graphics, graphicsContext, style);
      case COMPONENT_OBSTACLE_AREA ->
          renderComponentObstacleArea(
              (ComponentObstacleArea) item, step, graphics, graphicsContext, style);
      case TRACE -> renderTrace((PolylineTrace) item, step, graphics, graphicsContext, style);
      case PIN, VIA -> renderDrillItem((DrillItem) item, step, graphics, graphicsContext, style);
      case OBSTACLE_AREA, VIA_OBSTACLE_AREA ->
          renderObstacleArea((ObstacleArea) item, step, graphics, graphicsContext, style);
      case BOARD_OUTLINE ->
          renderBoardOutline((BoardOutline) item, step, graphics, graphicsContext, style);
      case CONDUCTION_AREA ->
          renderConductionArea((ConductionArea) item, step, graphics, graphicsContext, style);
      case OTHER -> {}
      default -> {}
    }
  }

  /** Renders traces directly from their neutral geometry. */
  private static void renderTrace(
      PolylineTrace trace,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext,
      RenderStyle style) {
    if (step.virtual() || !trace.isOnLayer(step.index())) {
      return;
    }
    int layer = trace.getLayer();
    Color[] colors =
        style == null ? graphicsContext.getTraceColors(trace.isUserFixed()) : style.colors();
    Color color = colors[layer];
    double intensity =
        (style == null ? graphicsContext.getTraceColorIntensity() : style.intensity())
            * graphicsContext.getLayerVisibility(layer);
    graphicsContext.draw(
        trace.polyline().cornerApproxArr(), trace.getHalfWidth(), color, graphics, intensity);
  }

  /** Renders pins and vias directly from their drill-item geometry. */
  private static void renderDrillItem(
      DrillItem drillItem,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext,
      RenderStyle style) {
    if (step.virtual()) {
      return;
    }
    int layerNo = step.index();
    int fromLayer = drillItem.firstLayer();
    int toLayer = drillItem.lastLayer();
    if (layerNo < fromLayer || layerNo > toLayer) {
      return;
    }

    final Color[] colors = style == null ? drillColors(drillItem, graphicsContext) : style.colors();
    double intensity =
        style == null ? drillIntensity(drillItem, graphicsContext) : style.intensity();
    if (intensity <= 0) {
      return;
    }
    boolean isLastPhysicalLayer = false;
    if (drillItem instanceof Pin && fromLayer != toLayer) {
      int activeLayer = graphicsContext.getFullyVisibleLayer();
      int lastPhysicalLayer = activeLayer;
      if (activeLayer == -1) {
        int activeVirtual = graphicsContext.getFullyVisibleVirtualLayer();
        boolean isBack = activeVirtual != -1 && activeVirtual % 2 != 0;
        lastPhysicalLayer = isBack ? drillItem.board.getLayerCount() - 1 : 0;
      }
      isLastPhysicalLayer = layerNo == lastPhysicalLayer;
    }

    double visibilityFactor = 0;
    for (int layer = fromLayer; layer <= toLayer; layer++) {
      visibilityFactor += graphicsContext.getLayerVisibility(layer);
    }
    if (visibilityFactor >= 0.001) {
      double layerVisibility = graphicsContext.getLayerVisibility(layerNo);
      Shape shape = drillItem.getShape(layerNo - fromLayer);
      if (shape != null && layerVisibility > 0.001) {
        double layerIntensity = drillItem instanceof Pin ? intensity : intensity * layerVisibility;
        graphicsContext.fillArea(shape, graphics, colors[layerNo], layerIntensity);
      }
    }

    if (isLastPhysicalLayer) {
      Padstack padstack = drillItem.getPadstack();
      if (padstack != null && padstack.getDrillRadius() > 0) {
        java.awt.Color drillColor = graphicsContext.otherColorTable.getDrillHoleColor();
        double drillIntensity =
            graphicsContext.colorIntensityTable.getValue(
                ColorIntensityTable.ObjectNames.DRILL_HOLES.ordinal());
        IntPoint centerPoint = drillItem.getCenter().toFloat().round();
        Circle drillCircle = new Circle(centerPoint, (int) Math.round(padstack.getDrillRadius()));
        graphicsContext.fillCircle(drillCircle, graphics, drillColor, drillIntensity);
      }
    }
  }

  private static java.awt.Color[] drillColors(
      DrillItem drillItem, GraphicsContext graphicsContext) {
    if (drillItem instanceof Pin pin) {
      if (pin.netCount() > 0) {
        return pin.firstLayer() != pin.lastLayer()
            ? graphicsContext.getTraceColors(pin.isUserFixed())
            : graphicsContext.getPinColors();
      }
      return graphicsContext.getObstacleColors();
    }
    Via via = (Via) drillItem;
    return via.netCount() == 0
        ? graphicsContext.getObstacleColors()
        : graphicsContext.getTraceColors(via.isUserFixed());
  }

  private static double drillIntensity(DrillItem drillItem, GraphicsContext graphicsContext) {
    if (drillItem instanceof Pin) {
      return graphicsContext.getPinColorIntensity();
    }
    Via via = (Via) drillItem;
    if (via.netCount() == 0) {
      return graphicsContext.getObstacleColorIntensity();
    }
    if (via.firstLayer() >= via.lastLayer()) {
      return graphicsContext.getPinColorIntensity();
    }
    return graphicsContext.getViaColorIntensity();
  }

  private static void renderComponentOutline(
      ComponentOutline outline,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext,
      RenderStyle style) {
    if (step.virtual() && virtualLayerFor(outline) == step.index()) {
      Color[] colors = style == null ? null : style.colors();
      Color color =
          colors == null
              ? componentOutlineColor(outline, graphicsContext)
              : colors[outline.getLayer()];
      double visibility = graphicsContext.getVirtualLayerVisibility(step.index());
      double intensity =
          visibility
              * (style == null
                  ? graphicsContext.getComponentOutlineColorIntensity()
                  : style.intensity());
      double drawWidth = Math.min(outline.board.communication.getResolution(Unit.MIL), 100);
      if (outline.isCourtyard() || outline.isClosed()) {
        graphicsContext.drawBoundary(outline.getArea(), drawWidth, color, graphics, intensity);
      } else {
        graphicsContext.fillArea(outline.getArea(), graphics, color, intensity);
      }
    }
  }

  private static void renderComponentObstacleArea(
      ComponentObstacleArea area,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext,
      RenderStyle style) {
    if (step.virtual() && virtualLayerFor(area) == step.index()) {
      Color[] colors = style == null ? null : style.colors();
      Color color =
          colors == null
              ? area.isFront()
                  ? graphicsContext.otherColorTable.getCourtyardColor(true)
                  : graphicsContext.otherColorTable.getCourtyardColor(false)
              : colors[area.getLayer()];
      double visibility = graphicsContext.getVirtualLayerVisibility(step.index());
      double intensity =
          visibility
              * (style == null
                  ? graphicsContext.getComponentOutlineColorIntensity()
                  : style.intensity());
      double drawWidth = Math.min(area.board.communication.getResolution(Unit.MIL), 100);
      graphicsContext.drawBoundary(area.getArea(), drawWidth, color, graphics, intensity);
    }
  }

  private static Color componentOutlineColor(
      ComponentOutline outline, GraphicsContext graphicsContext) {
    if (outline.isCourtyard()) {
      return graphicsContext.otherColorTable.getCourtyardColor(outline.isFront());
    }
    if (outline.isFabrication()) {
      return graphicsContext.otherColorTable.getFabColor(outline.isFront());
    }
    return graphicsContext.otherColorTable.getSilkscreenColor(outline.isFront());
  }

  private static void renderObstacleArea(
      ObstacleArea area,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext,
      RenderStyle style) {
    if (step.virtual() || area.getLayer() != step.index()) {
      return;
    }
    Color[] colors =
        style == null
            ? area instanceof ViaObstacleArea
                ? graphicsContext.getViaObstacleColors()
                : graphicsContext.getObstacleColors()
            : style.colors();
    Color color = colors[step.index()];
    double intensity =
        style == null
            ? area instanceof ViaObstacleArea
                ? graphicsContext.getViaObstacleColorIntensity()
                : graphicsContext.getObstacleColorIntensity()
            : style.intensity();
    graphicsContext.fillArea(
        area.getArea(),
        graphics,
        color,
        intensity * graphicsContext.getLayerVisibility(step.index()));
  }

  private static void renderBoardOutline(
      BoardOutline outline,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext,
      RenderStyle style) {
    if (step.virtual()) {
      return;
    }
    Color color = style == null ? graphicsContext.getOutlineColor() : style.colors()[0];
    for (int index = 0; index < outline.shapeCount(); index++) {
      var shape = outline.getShape(index);
      if (shape == null) {
        continue;
      }
      var drawCorners = shape.cornerApproxArr();
      var closedDrawCorners =
          new app.freerouting.geometry.planar.FloatPoint[drawCorners.length + 1];
      System.arraycopy(drawCorners, 0, closedDrawCorners, 0, drawCorners.length);
      closedDrawCorners[closedDrawCorners.length - 1] = drawCorners[0];
      graphicsContext.draw(closedDrawCorners, 100, color, graphics, 1);
    }
  }

  private static void renderConductionArea(
      ConductionArea area,
      RenderStep step,
      Graphics graphics,
      GraphicsContext graphicsContext,
      RenderStyle style) {
    if (step.virtual() || area.getLayer() != step.index()) {
      return;
    }
    int layer = step.index();
    double layerVisibility = graphicsContext.getLayerVisibility(layer);
    if (layerVisibility <= 0) {
      return;
    }
    Color[] colors = style == null ? graphicsContext.getTraceColors(true) : style.colors();
    Color color = colors[layer];
    double intensity =
        style == null ? graphicsContext.getConductionColorIntensity() : style.intensity();
    if (area.getIsFilled()) {
      double fillOpacity = Math.min(layerVisibility * intensity * 2.5, 1.0);
      double maxClearanceLookupBoard = 2000.0 * area.board.communication.getResolution(Unit.UM);
      if (area.board.rules != null && area.board.rules.clearanceMatrix != null) {
        double maxMatrixClearance =
            area.board.rules.clearanceMatrix.maxValue(area.clearanceClassNo(), layer);
        maxClearanceLookupBoard =
            Math.max(
                maxClearanceLookupBoard,
                maxMatrixClearance + 100.0 * area.board.communication.getResolution(Unit.UM));
      }
      double clearanceScreenPx =
          graphicsContext.coordinateTransform.boardToScreen(maxClearanceLookupBoard);
      boolean useSimpleFill =
          clearanceScreenPx < 15.0 || graphicsContext.isSimplifiedPlaneRendering();
      if (useSimpleFill) {
        graphicsContext.fillArea(area.getArea(), graphics, color, fillOpacity);
      } else {
        java.awt.geom.Area cachedFill = area.getDetailedFillArea(maxClearanceLookupBoard, layer);
        if (cachedFill != null && !cachedFill.isEmpty()) {
          var p0 = graphicsContext.coordinateTransform.boardToScreen(FloatPoint.ZERO);
          var px = graphicsContext.coordinateTransform.boardToScreen(new FloatPoint(1, 0));
          var py = graphicsContext.coordinateTransform.boardToScreen(new FloatPoint(0, 1));
          var boardToScreen =
              new java.awt.geom.AffineTransform(
                  px.getX() - p0.getX(),
                  px.getY() - p0.getY(),
                  py.getX() - p0.getX(),
                  py.getY() - p0.getY(),
                  p0.getX(),
                  p0.getY());
          java.awt.geom.Area screenArea = cachedFill.createTransformedArea(boardToScreen);
          java.awt.Graphics2D graphics2D = (java.awt.Graphics2D) graphics;
          java.awt.Paint oldPaint = graphics2D.getPaint();
          java.awt.Composite oldComposite = graphics2D.getComposite();
          graphics2D.setColor(color);
          graphics2D.setComposite(
              java.awt.AlphaComposite.getInstance(
                  java.awt.AlphaComposite.SRC_OVER, (float) fillOpacity));
          graphics2D.setRenderingHint(
              java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
          graphics2D.fill(screenArea);
          graphics2D.setPaint(oldPaint);
          graphics2D.setComposite(oldComposite);
        }
      }
    }
    double hatchPitch = 500.0 * area.board.communication.getResolution(Unit.UM);
    graphicsContext.drawPlaneHatch(
        area.getArea(), graphics, color, layerVisibility * intensity * 0.85, hatchPitch);
    graphicsContext.drawBoundary(area.getArea(), 0.0, color, graphics, layerVisibility);
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

  private record RenderStyle(Color[] colors, double intensity) {}
}

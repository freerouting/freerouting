package app.freerouting.gui.rendering;

import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.Ellipse;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.logger.FRLogger;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;

/** Context for drawing items in the board package to the screen. */
public class GraphicsContext implements Serializable {

  private static final int update_offset = 10000;
  private static final int virtual_layer_count = 6;
  private static final boolean show_line_segments = false;
  private static final boolean show_area_division = false;
  public transient ItemColorTableModel itemColorTable;
  public transient OtherColorTableModel otherColorTable;
  public ColorIntensityTable colorIntensityTable;
  public ScreenTransform coordinateTransform;

  /** Layer visibility per board layer, where 0 is invisible and 1 is fully visible. */
  private double[] layerVisibilityArr;

  /**
   * The factor for automatic layer dimming of layers different from the current layer. Values are
   * between 0 and 1. If 1, there is no automatic layer dimming.
   */
  private double autoLayerDimFactor = 0.7;

  /** The layer, which is not automatically dimmed. */
  private int fullyVisibleLayer = -1;

  private boolean[] virtualLayerVisibilityArr = createDefaultVirtualLayerVisibilityArr();
  private int fullyVisibleVirtualLayer = -1;

  /** When true, copper pours use fast solid fills (used during the first paint after load). */
  private transient boolean simplifiedPlaneRendering;

  private transient java.awt.TexturePaint cachedHatchPaint;
  private transient double cachedHatchPitchPx = -1.0;
  private transient Color cachedHatchColor;

  /** Creates a graphics context for the given board bounds and layer structure. */
  public GraphicsContext(
      IntBox designBounds, Dimension panelBounds, LayerStructure layerStructure, Locale locale) {
    coordinateTransform = new ScreenTransform(designBounds, panelBounds);
    itemColorTable = new ItemColorTableModel(layerStructure, locale);
    otherColorTable = new OtherColorTableModel(locale);
    colorIntensityTable = new ColorIntensityTable();
    layerVisibilityArr = new double[layerStructure.layers.length];
    for (int i = 0; i < layerVisibilityArr.length; i++) {
      if (layerStructure.layers[i].isSignal) {
        layerVisibilityArr[i] = 1.00;
      } else {
        layerVisibilityArr[i] = 0.25;
      }
    }
  }

  /** Copy constructor. */
  public GraphicsContext(GraphicsContext graphicsContext) {
    this.coordinateTransform = new ScreenTransform(graphicsContext.coordinateTransform);
    this.itemColorTable = new ItemColorTableModel(graphicsContext.itemColorTable);
    this.otherColorTable = new OtherColorTableModel(graphicsContext.otherColorTable);
    this.colorIntensityTable = new ColorIntensityTable(graphicsContext.colorIntensityTable);
    this.layerVisibilityArr = graphicsContext.copyLayerVisibilityArr();
    this.virtualLayerVisibilityArr = graphicsContext.getVirtualLayerVisibilityArr().clone();
    this.fullyVisibleVirtualLayer = graphicsContext.fullyVisibleVirtualLayer;
  }

  private static boolean[] createDefaultVirtualLayerVisibilityArr() {
    return new boolean[] {true, true, true, true, true, true};
  }

  /** Initializes stroke and color settings on the given graphics context. */
  private static void initDrawGraphics(Graphics2D graphics, Color color, float width) {
    BasicStroke bs =
        new BasicStroke(Math.max(width, 0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    graphics.setStroke(bs);
    graphics.setColor(color);
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  }

  static void setTranslucency(Graphics2D g2, double factor) {
    AlphaComposite currentAlphaComposite;
    if (factor >= 0) {
      currentAlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) factor);
    } else {
      currentAlphaComposite = AlphaComposite.getInstance(AlphaComposite.DST_OVER, (float) -factor);
    }
    g2.setComposite(currentAlphaComposite);
  }

  private static void restoreGraphics2dState(
      Graphics2D g2,
      java.awt.Stroke stroke,
      java.awt.Shape clip,
      java.awt.Paint paint,
      java.awt.Composite composite) {
    g2.setStroke(stroke);
    g2.setClip(clip);
    g2.setPaint(paint);
    g2.setComposite(composite);
  }

  private static void restorePaintAndComposite(
      Graphics2D g2, java.awt.Paint paint, java.awt.Composite composite) {
    g2.setPaint(paint);
    g2.setComposite(composite);
  }

  private void prepareAndDrawHatch(
      Graphics2D g2,
      java.awt.geom.Area outerArea,
      Color color,
      double translucencyFactor,
      double pitchPx,
      int pitchPixels) {
    setTranslucency(g2, translucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (cachedHatchPaint == null
        || cachedHatchPitchPx != pitchPx
        || !color.equals(cachedHatchColor)) {
      java.awt.image.BufferedImage bi =
          new java.awt.image.BufferedImage(
              pitchPixels, pitchPixels, java.awt.image.BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2t = bi.createGraphics();
      g2t.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2t.setColor(color);
      g2t.setStroke(new BasicStroke(1.0f));
      g2t.drawLine(0, pitchPixels, pitchPixels, 0);
      g2t.drawLine(-1, pitchPixels + 1, pitchPixels + 1, -1);
      g2t.dispose();
      cachedHatchPaint =
          new java.awt.TexturePaint(bi, new Rectangle2D.Double(0, 0, pitchPixels, pitchPixels));
      cachedHatchPitchPx = pitchPx;
      cachedHatchColor = color;
    }

    g2.clip(outerArea);
    g2.setPaint(cachedHatchPaint);
    g2.setStroke(
        new BasicStroke((float) (2 * pitchPx), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
    g2.draw(outerArea);
  }

  private static void fillWithColor(
      Graphics2D g2, java.awt.geom.Area fillArea, Color color, double translucencyFactor) {
    g2.setColor(color);
    setTranslucency(g2, translucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(fillArea);
  }

  private boolean[] getVirtualLayerVisibilityArr() {
    if (virtualLayerVisibilityArr == null || virtualLayerVisibilityArr.length == 0) {
      virtualLayerVisibilityArr = createDefaultVirtualLayerVisibilityArr();
    }
    return virtualLayerVisibilityArr;
  }

  /**
   * Changes the bounds of the board design.
   *
   * <p>Useful when components are still placed outside the board.
   */
  public void changeDesignBounds(IntBox newDesignBounds) {
    if (newDesignBounds.equals(this.coordinateTransform.designBox)) {
      return;
    }
    Dimension screenBounds = this.coordinateTransform.screenBounds;
    this.coordinateTransform = new ScreenTransform(newDesignBounds, screenBounds);
  }

  /** Changes the size of the panel to {@code newBounds}. */
  public void changePanelSize(Dimension newBounds) {
    if (coordinateTransform == null) {
      return;
    }
    IntBox designBox = coordinateTransform.designBox;
    ScreenTransform updatedTransform = new ScreenTransform(designBox, newBounds);
    updatedTransform.setMirrorLeftRight(coordinateTransform.isMirrorLeftRight());
    updatedTransform.setMirrorTopBottom(coordinateTransform.isMirrorTopBottom());
    updatedTransform.setRotation(coordinateTransform.getRotation());
    coordinateTransform = updatedTransform;
  }

  /** Draws a polygon with the given corner points. */
  public void draw(
      FloatPoint[] points, double halfWidth, Color color, Graphics g, double translucencyFactor) {
    if (color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g;
    double scaledWidth = coordinateTransform.boardToScreen(halfWidth);

    initDrawGraphics(g2, color, (float) scaledWidth * 2);
    setTranslucency(g2, translucencyFactor);

    GeneralPath drawPath = null;
    if (!show_line_segments) {
      drawPath = new GeneralPath();
    }

    Rectangle clipShape = g.getClip().getBounds();
    IntBox clipBox = coordinateTransform.screenToBoard(clipShape);
    for (int i = 0; i < (points.length - 1); i++) {
      if (lineOutsideUpdateBox(points[i], points[i + 1], halfWidth + update_offset, clipBox)) {
        // this check should be unnecessary here,
        // the system should do it in the draw(line) function
        continue;
      }
      Point2D p1 = coordinateTransform.boardToScreen(points[i]);
      Point2D p2 = coordinateTransform.boardToScreen(points[i + 1]);
      Line2D line = new Line2D.Double(p1, p2);

      if (show_line_segments) {
        g2.draw(line);
      } else {
        drawPath.append(line, false);
      }
    }
    if (!show_line_segments) {
      g2.draw(drawPath);
    }
  }

  /*
   * draws the boundary of a circle.
   */
  /** DrawCircle. */
  public void drawCircle(
      FloatPoint center,
      double radius,
      double drawHalfWidth,
      Color color,
      Graphics g,
      double translucencyFactor) {
    if (color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g;
    Point2D screenCenter = coordinateTransform.boardToScreen(center);

    double screenRadius = coordinateTransform.boardToScreen(radius);
    double diameter = 2 * screenRadius;
    float drawWidth = (float) (2 * coordinateTransform.boardToScreen(drawHalfWidth));
    Ellipse2D circle =
        new Ellipse2D.Double(
            screenCenter.getX() - screenRadius,
            screenCenter.getY() - screenRadius,
            diameter,
            diameter);
    setTranslucency(g2, translucencyFactor);
    initDrawGraphics(g2, color, drawWidth);
    g2.draw(circle);
  }

  /*
   * draws a rectangle
   */
  /** DrawRectangle. */
  public void drawRectangle(
      FloatPoint corner1,
      FloatPoint corner2,
      double drawHalfWidth,
      Color color,
      Graphics g,
      double translucencyFactor) {
    if (color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g;
    Point2D screenCorner1 = coordinateTransform.boardToScreen(corner1);
    Point2D screenCorner2 = coordinateTransform.boardToScreen(corner2);

    double xmin = Math.min(screenCorner1.getX(), screenCorner2.getX());
    double ymin = Math.min(screenCorner1.getY(), screenCorner2.getY());

    float drawWidth = (float) (2 * coordinateTransform.boardToScreen(drawHalfWidth));
    double width = Math.abs(screenCorner2.getX() - screenCorner1.getX());
    double height = Math.abs(screenCorner2.getY() - screenCorner1.getY());
    Rectangle2D rectangle = new Rectangle2D.Double(xmin, ymin, width, height);
    setTranslucency(g2, translucencyFactor);
    initDrawGraphics(g2, color, drawWidth);
    g2.draw(rectangle);
  }

  /** Draws the boundary of shape. */
  public void drawBoundary(
      Shape shape, double drawHalfWidth, Color color, Graphics g, double translucencyFactor) {
    if (shape instanceof PolylineShape) {
      FloatPoint[] drawCorners = shape.cornerApproxArr();
      if (drawCorners.length <= 1) {
        return;
      }
      FloatPoint[] closedDrawCorners = new FloatPoint[drawCorners.length + 1];
      System.arraycopy(drawCorners, 0, closedDrawCorners, 0, drawCorners.length);
      closedDrawCorners[closedDrawCorners.length - 1] = drawCorners[0];
      this.draw(closedDrawCorners, drawHalfWidth, color, g, translucencyFactor);
    } else if (shape instanceof Circle currentCircle) {
      this.drawCircle(
          currentCircle.center.toFloat(),
          currentCircle.radius,
          drawHalfWidth,
          color,
          g,
          translucencyFactor);
    }
  }

  /** Draws the boundary of area. */
  public void drawBoundary(
      Area area, double drawHalfWidth, Color color, Graphics g, double translucencyFactor) {
    drawBoundary(area.getBorder(), drawHalfWidth, color, g, translucencyFactor);
    Shape[] holes = area.getHoles();
    for (int i = 0; i < holes.length; i++) {
      drawBoundary(holes[i], drawHalfWidth, color, g, translucencyFactor);
    }
  }

  /** GetAwtArea. */
  public java.awt.geom.Area getAwtArea(Area area) {
    if (area == null || area.isEmpty()) {
      return null;
    }
    if (area instanceof Circle circle) {
      Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
      double radius = coordinateTransform.boardToScreen(circle.radius);
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter));
    }

    Shape borderShape = area.getBorder();
    if (!(borderShape instanceof PolylineShape border) || !border.isBounded()) {
      return null;
    }

    java.awt.geom.Path2D.Double borderPath = new java.awt.geom.Path2D.Double();
    int count = border.borderLineCount();
    if (count > 0) {
      Point2D p0 = coordinateTransform.boardToScreen(border.cornerApprox(0));
      borderPath.moveTo(p0.getX(), p0.getY());
      for (int i = 1; i < count; i++) {
        Point2D pi = coordinateTransform.boardToScreen(border.cornerApprox(i));
        borderPath.lineTo(pi.getX(), pi.getY());
      }
      borderPath.closePath();
    }
    java.awt.geom.Area awtArea = new java.awt.geom.Area(borderPath);

    Shape[] holes = area.getHoles();
    for (Shape hole : holes) {
      if (hole instanceof PolylineShape holePoly) {
        int holeCount = holePoly.borderLineCount();
        if (holeCount > 0) {
          java.awt.geom.Path2D.Double holePath = new java.awt.geom.Path2D.Double();
          Point2D hp0 = coordinateTransform.boardToScreen(holePoly.cornerApprox(0));
          holePath.moveTo(hp0.getX(), hp0.getY());
          for (int i = 1; i < holeCount; i++) {
            Point2D hpi = coordinateTransform.boardToScreen(holePoly.cornerApprox(i));
            holePath.lineTo(hpi.getX(), hpi.getY());
          }
          holePath.closePath();
          awtArea.subtract(new java.awt.geom.Area(holePath));
        }
      } else if (hole instanceof Circle circle) {
        Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
        double radius = coordinateTransform.boardToScreen(circle.radius);
        double diameter = 2 * radius;
        awtArea.subtract(
            new java.awt.geom.Area(
                new Ellipse2D.Double(
                    center.getX() - radius, center.getY() - radius, diameter, diameter)));
      }
    }
    return awtArea;
  }

  /** Draws a hatched fill pattern inside a copper pour area. */
  public void drawPlaneHatch(
      Area area, Graphics g, Color color, double translucencyFactor, double pitchBoardUnits) {
    if (color == null || area == null || area.isEmpty() || translucencyFactor <= 0) {
      return;
    }
    double pitchPx = coordinateTransform.boardToScreen(pitchBoardUnits);
    if (pitchPx < 2.0) {
      return;
    }
    if (pitchPx > 1000.0) {
      pitchPx = 1000.0;
    }

    java.awt.geom.Area outerArea = getAwtArea(area);
    if (outerArea == null || outerArea.isEmpty()) {
      return;
    }

    int pitchPixels = (int) Math.round(pitchPx);

    Graphics2D g2 = (Graphics2D) g;
    java.awt.Paint oldPaint = g2.getPaint();
    java.awt.Composite oldComposite = g2.getComposite();
    java.awt.Shape oldClip = g2.getClip();
    java.awt.Stroke oldStroke = g2.getStroke();
    prepareAndDrawHatch(g2, outerArea, color, translucencyFactor, pitchPx, pitchPixels);
    restoreGraphics2dState(g2, oldStroke, oldClip, oldPaint, oldComposite);
  }

  /** GetAwtAreaFromShape. */
  public java.awt.geom.Area getAwtAreaFromShape(Shape shape) {
    if (shape == null) {
      return null;
    }
    if (shape instanceof Circle circle) {
      Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
      double radius = coordinateTransform.boardToScreen(circle.radius);
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter));
    }
    if (shape instanceof PolylineShape poly) {
      int count = poly.borderLineCount();
      if (count <= 0) {
        return null;
      }
      java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
      Point2D p0 = coordinateTransform.boardToScreen(poly.cornerApprox(0));
      path.moveTo(p0.getX(), p0.getY());
      for (int i = 1; i < count; i++) {
        Point2D pi = coordinateTransform.boardToScreen(poly.cornerApprox(i));
        path.lineTo(pi.getX(), pi.getY());
      }
      path.closePath();
      return new java.awt.geom.Area(path);
    }
    return null;
  }

  /** FillPlaneArea. */
  public void fillPlaneArea(
      Area area,
      Graphics g,
      Color color,
      double translucencyFactor,
      java.util.List<ClearanceItem> clearances,
      java.util.List<ThermalReliefItem> thermals) {
    if (color == null || area == null || area.isEmpty() || translucencyFactor <= 0) {
      return;
    }

    java.awt.geom.Area fillArea = getAwtArea(area);
    if (fillArea == null || fillArea.isEmpty()) {
      return;
    }

    // Subtract foreign clearances
    if (clearances != null) {
      for (ClearanceItem item : clearances) {
        if (item != null && item.area != null) {
          fillArea.subtract(item.area);
        }
      }
    }

    // Process thermal reliefs
    if (thermals != null) {
      for (ThermalReliefItem thermal : thermals) {
        if (thermal == null || thermal.clearanceArea == null) {
          continue;
        }
        fillArea.subtract(thermal.clearanceArea);

        // Create 4 diagonal spokes at 45 degrees
        double halfSpoke = thermal.spokeWidthPx / 2.0;
        double r = thermal.expansionRadiusPx;

        // Rotated rectangles (NE-SW and NW-SE)
        Rectangle2D.Double baseSpoke =
            new Rectangle2D.Double(
                thermal.cx - halfSpoke, thermal.cy - r, thermal.spokeWidthPx, 2 * r);

        AffineTransform rotP45 =
            AffineTransform.getRotateInstance(Math.PI / 4.0, thermal.cx, thermal.cy);
        AffineTransform rotM45 =
            AffineTransform.getRotateInstance(-Math.PI / 4.0, thermal.cx, thermal.cy);

        java.awt.geom.Area spokes =
            new java.awt.geom.Area(rotP45.createTransformedShape(baseSpoke));
        spokes.add(new java.awt.geom.Area(rotM45.createTransformedShape(baseSpoke)));

        // Restrict spokes to the clearance gap
        spokes.intersect(thermal.clearanceArea);

        fillArea.add(spokes);
      }
    }

    if (fillArea.isEmpty()) {
      return;
    }

    Graphics2D g2 = (Graphics2D) g;
    java.awt.Paint oldPaint = g2.getPaint();
    java.awt.Composite oldComposite = g2.getComposite();
    fillWithColor(g2, fillArea, color, translucencyFactor);
    restorePaintAndComposite(g2, oldPaint, oldComposite);
  }

  /** Draws the interior of a circle. */
  public void fillCircle(Circle circle, Graphics g, Color color, double translucencyFactor) {
    if (color == null) {
      return;
    }
    Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
    double radius = coordinateTransform.boardToScreen(circle.radius);
    if (!pointNearRectangle(center.getX(), center.getY(), g.getClip().getBounds(), radius)) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g;
    g2.setColor(color);
    setTranslucency(g2, translucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(
        new Ellipse2D.Double(
            center.getX() - radius, center.getY() - radius, 2 * radius, 2 * radius));
  }

  /** Draws the interior of an ellipse. */
  public void fillEllipse(Ellipse ellipse, Graphics g, Color color, double translucencyFactor) {
    Ellipse[] ellipseArr = new Ellipse[1];
    ellipseArr[0] = ellipse;
    fillEllipseArr(ellipseArr, g, color, translucencyFactor);
  }

  /**
   * Draws the interior of an array of ellipses.
   *
   * <p>Ellipses contained in another ellipse are treated as holes.
   */
  public void fillEllipseArr(
      Ellipse[] ellipseArr, Graphics g, Color color, double translucencyFactor) {
    if (color == null || ellipseArr.length == 0) {
      return;
    }
    GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (Ellipse currentEllipse : ellipseArr) {
      Point2D center = coordinateTransform.boardToScreen(currentEllipse.center);
      double biggerRadius = coordinateTransform.boardToScreen(currentEllipse.biggerRadius);
      if (!pointNearRectangle(
          center.getX(), center.getY(), g.getClip().getBounds(), biggerRadius)) {
        continue;
      }
      double smallerRadius = coordinateTransform.boardToScreen(currentEllipse.smallerRadius);
      Ellipse2D drawEllipse =
          new Ellipse2D.Double(
              center.getX() - biggerRadius,
              center.getY() - smallerRadius,
              2 * biggerRadius,
              2 * smallerRadius);
      double rotation = coordinateTransform.boardToScreenAngle(currentEllipse.rotation);
      AffineTransform affineTransform = new AffineTransform();
      affineTransform.rotate(rotation, center.getX(), center.getY());
      java.awt.Shape rotatedEllipse = affineTransform.createTransformedShape(drawEllipse);
      drawPath.append(rotatedEllipse, false);
    }
    Graphics2D g2 = (Graphics2D) g;
    g2.setColor(color);
    setTranslucency(g2, translucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPath);
  }

  /** Checks, if the distance of the point with coordinates x, y to rect is at most dist. */
  private boolean pointNearRectangle(double x, double y, Rectangle rect, double dist) {
    if (x < rect.x - dist) {
      return false;
    }
    if (y < rect.y - dist) {
      return false;
    }
    if (x > rect.x + rect.width + dist) {
      return false;
    }
    return y <= rect.y + rect.height + dist;
  }

  /** Fill the interior of the polygon shape represented by points. */
  public void fillShape(FloatPoint[] points, Graphics g, Color color, double translucencyFactor) {
    if (color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g;
    Polygon drawPolygon = new Polygon();
    for (int i = 0; i < points.length; i++) {
      Point2D currentCorner = coordinateTransform.boardToScreen(points[i]);
      drawPolygon.addPoint(
          (int) Math.round(currentCorner.getX()), (int) Math.round(currentCorner.getY()));
    }
    g2.setColor(color);
    setTranslucency(g2, translucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPolygon);
  }

  /**
   * Fill the interior of a list of polygons. Used for example with an area consisting of a border
   * polygon and some holes.
   */
  public void fillArea(
      FloatPoint[][] pointLists, Graphics g, Color color, double translucencyFactor) {
    if (color == null) {
      return;
    }
    GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (int j = 0; j < pointLists.length; j++) {
      Polygon drawPolygon = new Polygon();
      FloatPoint[] currentPointList = pointLists[j];
      for (int i = 0; i < currentPointList.length; i++) {
        Point2D currentCorner = coordinateTransform.boardToScreen(currentPointList[i]);
        drawPolygon.addPoint(
            (int) Math.round(currentCorner.getX()), (int) Math.round(currentCorner.getY()));
      }
      drawPath.append(drawPolygon, false);
    }
    Graphics2D g2 = (Graphics2D) g;
    g2.setColor(color);
    setTranslucency(g2, translucencyFactor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPath);
  }

  /** Draws the interior of a geometry {@link Area}. */
  public void fillArea(Area area, Graphics g, Color color, double translucencyFactor) {
    if (color == null || area.isEmpty()) {
      return;
    }
    if (area instanceof Circle circle) {
      fillCircle(circle, g, color, translucencyFactor);
    } else {
      PolylineShape border = (PolylineShape) area.getBorder();
      if (!border.isBounded()) {
        FRLogger.warn("GraphicsContext.fill_area: shape not bounded");
        return;
      }
      Rectangle clipShape = g.getClip().getBounds();
      IntBox clipBox = coordinateTransform.screenToBoard(clipShape);
      if (!border.boundingBox().intersects(clipBox)) {
        return;
      }
      Shape[] holes = area.getHoles();

      FloatPoint[][] drawPolygons = new FloatPoint[holes.length + 1][];
      for (int j = 0; j < drawPolygons.length; j++) {
        PolylineShape currentDrawShape;
        if (j == 0) {
          currentDrawShape = border;
        } else {
          currentDrawShape = (PolylineShape) holes[j - 1];
        }
        drawPolygons[j] = new FloatPoint[currentDrawShape.borderLineCount() + 1];
        FloatPoint[] currentDrawPolygon = drawPolygons[j];
        for (int i = 0; i < currentDrawPolygon.length - 1; i++) {
          currentDrawPolygon[i] = currentDrawShape.cornerApprox(i);
        }
        // close the polygon
        currentDrawPolygon[currentDrawPolygon.length - 1] = currentDrawPolygon[0];
      }
      fillArea(drawPolygons, g, color, translucencyFactor);
    }
    if (show_area_division) {
      TileShape[] tiles = area.splitToConvex();
      for (int i = 0; i < tiles.length; i++) {
        FloatPoint[] corners = new FloatPoint[tiles[i].borderLineCount() + 1];
        TileShape currentTile = tiles[i];
        for (int j = 0; j < corners.length - 1; j++) {
          corners[j] = currentTile.cornerApprox(j);
        }
        corners[corners.length - 1] = corners[0];
        draw(corners, 1, Color.white, g, 0.7);
      }
    }
  }

  public Color getBackgroundColor() {
    return otherColorTable.getBackgroundColor();
  }

  public Color getHighlightColor() {
    return otherColorTable.getHighlightColor();
  }

  public Color getIncompleteColor() {
    return otherColorTable.getIncompleteColor();
  }

  public Color getOutlineColor() {
    return otherColorTable.getOutlineColor();
  }

  /** GetComponentColor. */
  public Color getComponentColor(boolean front) {
    return otherColorTable.getComponentColor(front);
  }

  public Color getViolationsColor() {
    return otherColorTable.getViolationsColor();
  }

  public Color getLengthMatchingAreaColor() {
    return otherColorTable.getLengthMatchingAreaColor();
  }

  /** GetTraceColors. */
  public Color[] getTraceColors(boolean fixed) {

    return itemColorTable.getTraceColors(fixed);
  }

  /** GetViaColors. */
  public Color[] getViaColors(boolean fixed) {
    return itemColorTable.getViaColors(fixed);
  }

  public Color[] getPinColors() {
    return itemColorTable.getPinColors();
  }

  public Color[] getConductionColors() {
    return itemColorTable.getConductionColors();
  }

  public Color[] getObstacleColors() {
    return itemColorTable.getObstacleColors();
  }

  public Color[] getViaObstacleColors() {
    return itemColorTable.getViaObstacleColors();
  }

  public Color[] getPlaceObstacleColors() {
    return itemColorTable.getPlaceObstacleColors();
  }

  public double getTraceColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.TRACES.ordinal());
  }

  /** SetTraceColorIntensity. */
  public void setTraceColorIntensity(double value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.TRACES.ordinal(), value);
  }

  public double getViaColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.VIAS.ordinal());
  }

  /** SetViaColorIntensity. */
  public void setViaColorIntensity(double value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.VIAS.ordinal(), value);
  }

  public double getPinColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.PINS.ordinal());
  }

  /** SetPinColorIntensity. */
  public void setPinColorIntensity(double value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.PINS.ordinal(), value);
  }

  public double getConductionColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal());
  }

  /** SetConductionColorIntensity. */
  public void setConductionColorIntensity(double value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(), value);
  }

  public double getObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal());
  }

  /** SetObstacleColorIntensity. */
  public void setObstacleColorIntensity(double value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal(), value);
  }

  public double getViaObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal());
  }

  /** SetViaObstacleColorIntensity. */
  public void setViaObstacleColorIntensity(double value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal(), value);
  }

  public double getPlaceObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.PLACE_KEEPOUTS.ordinal());
  }

  public double getComponentOutlineColorIntensity() {
    return colorIntensityTable.getValue(
        ColorIntensityTable.ObjectNames.COMPONENT_OUTLINES.ordinal());
  }

  public double getHighlightColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.HIGHLIGHT.ordinal());
  }

  /** SetHighlightColorIntensity. */
  public void setHighlightColorIntensity(double value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.HIGHLIGHT.ordinal(), value);
  }

  public double getIncompleteColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal());
  }

  /** SetIncompleteColorIntensity. */
  public void setIncompleteColorIntensity(double value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal(), value);
  }

  public double getLengthMatchingAreaColorIntensity() {
    return colorIntensityTable.getValue(
        ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal());
  }

  /** SetLengthMatchingAreaColorIntensity. */
  public void setLengthMatchingAreaColorIntensity(double value) {
    colorIntensityTable.setValue(
        ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal(), value);
  }

  public Dimension getPanelSize() {
    return coordinateTransform.screenBounds;
  }

  /** Returns the center of the design on the screen. */
  public Point2D getDesignCenter() {
    FloatPoint center = coordinateTransform.designBoxWithOffset.centreOfGravity();
    return coordinateTransform.boardToScreen(center);
  }

  /** Returns the bounding box of the design in screen coordinates. */
  public Rectangle getDesignBounds() {
    return coordinateTransform.boardToScreen(coordinateTransform.designBox);
  }

  /** Returns the factor used for automatic layer dimming. */
  public double getAutoLayerDimFactor() {
    return this.autoLayerDimFactor;
  }

  /**
   * Sets the factor for automatic layer dimming.
   *
   * <p>Values are between 0 and 1. If 1, there is no automatic layer dimming.
   */
  public void setAutoLayerDimFactor(double value) {
    autoLayerDimFactor = value;
  }

  public int getFullyVisibleLayer() {
    return fullyVisibleLayer;
  }

  /** Sets the layer, which will be excluded from automatic layer dimming. */
  public void setFullyVisibleLayer(int layerIndex) {
    fullyVisibleLayer = layerIndex;
    if (layerIndex != -1) {
      fullyVisibleVirtualLayer = -1;
    }
  }

  public boolean isSimplifiedPlaneRendering() {
    return simplifiedPlaneRendering;
  }

  public void setSimplifiedPlaneRendering(boolean simplifiedPlaneRendering) {
    this.simplifiedPlaneRendering = simplifiedPlaneRendering;
  }

  /** GetFullyVisibleVirtualLayer. */
  public int getFullyVisibleVirtualLayer() {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (fullyVisibleVirtualLayer < -1 || fullyVisibleVirtualLayer >= visibilityArr.length) {
      return -1;
    }
    return fullyVisibleVirtualLayer;
  }

  /** SetFullyVisibleVirtualLayer. */
  public void setFullyVisibleVirtualLayer(int idx) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (idx < -1 || idx >= visibilityArr.length) {
      idx = -1;
    }
    fullyVisibleVirtualLayer = idx;
    if (idx != -1) {
      fullyVisibleLayer = -1;
    }
  }

  /** IsFrontSelected. */
  public boolean isFrontSelected() {
    int selectedVirtualLayer = getFullyVisibleVirtualLayer();
    if (selectedVirtualLayer != -1) {
      return selectedVirtualLayer % 2 == 0;
    }
    if (fullyVisibleLayer != -1) {
      return fullyVisibleLayer < layerVisibilityArr.length / 2;
    }
    return true;
  }

  /** GetVirtualLayerVisible. */
  public boolean getVirtualLayerVisible(int idx) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (idx >= 0 && idx < visibilityArr.length) {
      return visibilityArr[idx];
    }
    return true;
  }

  /** SetVirtualLayerVisible. */
  public void setVirtualLayerVisible(int idx, boolean visible) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (idx >= 0 && idx < visibilityArr.length) {
      visibilityArr[idx] = visible;
    }
  }

  /** GetVirtualLayerVisibility. */
  public double getVirtualLayerVisibility(int virtualLayerIdx) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (virtualLayerIdx < 0 || virtualLayerIdx >= visibilityArr.length) {
      return 1.0;
    }
    if (!visibilityArr[virtualLayerIdx]) {
      return 0.0;
    }
    if (fullyVisibleLayer != -1) {
      return this.autoLayerDimFactor;
    }
    int selectedVirtualLayer = getFullyVisibleVirtualLayer();
    if (selectedVirtualLayer != -1) {
      if (selectedVirtualLayer == virtualLayerIdx) {
        return 1.0;
      } else {
        return this.autoLayerDimFactor;
      }
    }
    return 1.0;
  }

  /**
   * Gets the visibility factor of the input layer. The result is between 0 and 1. If the result is
   * 0, the layer is invisible, if the result is 1, the layer is fully visible.
   */
  public double getLayerVisibility(int layerIndex) {
    double result;
    if (fullyVisibleVirtualLayer != -1) {
      result = this.autoLayerDimFactor * layerVisibilityArr[layerIndex];
    } else if (layerIndex == this.fullyVisibleLayer) {
      result = layerVisibilityArr[layerIndex];
    } else {
      result = this.autoLayerDimFactor * layerVisibilityArr[layerIndex];
    }
    return result;
  }

  /** Gets the visibility factor of the input layer without the automatic layer dimming. */
  public double getRawLayerVisibility(int layerIndex) {
    return layerVisibilityArr[layerIndex];
  }

  /**
   * Gets the visibility factor of the input layer. The value is expected between 0 and 1. If the
   * value is 0, the layer is invisible, if the value is 1, the layer is fully visible.
   */
  public void setLayerVisibility(int layerIndex, double value) {
    layerVisibilityArr[layerIndex] = Math.max(0, Math.min(value, 1));
  }

  public void setLayerVisibilityArr(double[] layerVisibilityArr) {
    this.layerVisibilityArr = layerVisibilityArr;
  }

  /** Returns a copy of the layer visibility array. */
  public double[] copyLayerVisibilityArr() {
    double[] result = new double[this.layerVisibilityArr.length];
    System.arraycopy(this.layerVisibilityArr, 0, result, 0, this.layerVisibilityArr.length);
    return result;
  }

  /** Returns the number of layers on the board. */
  public int layerCount() {
    return layerVisibilityArr.length;
  }

  /**
   * Returns whether a line segment is outside the update box.
   *
   * <p>Used to skip draw calls that cannot affect the visible region.
   */
  private boolean lineOutsideUpdateBox(
      FloatPoint p1, FloatPoint p2, double updateOffset, IntBox updateBox) {
    if (p1 == null || p2 == null) {
      return true;
    }
    if (Math.max(p1.x, p2.x) < updateBox.ll.x - updateOffset) {
      return true;
    }
    if (Math.max(p1.y, p2.y) < updateBox.ll.y - updateOffset) {
      return true;
    }
    if (Math.min(p1.x, p2.x) > updateBox.ur.x + updateOffset) {
      return true;
    }
    return Math.min(p1.y, p2.y) > updateBox.ur.y + updateOffset;
  }

  /** Writes an instance of this class to a file. */
  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.defaultWriteObject();
    itemColorTable.writeObject(stream);
    otherColorTable.writeObject(stream);
  }

  /** Reads an instance of this class from a file. */
  private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
    if (virtualLayerVisibilityArr == null
        || virtualLayerVisibilityArr.length != virtual_layer_count) {
      virtualLayerVisibilityArr = createDefaultVirtualLayerVisibilityArr();
    }
    fullyVisibleVirtualLayer = getFullyVisibleVirtualLayer();
    this.itemColorTable = new ItemColorTableModel(stream);
    this.otherColorTable = new OtherColorTableModel(stream);
  }

  /** Clearance shape used during thermal-relief rendering. */
  public record ClearanceItem(java.awt.geom.Area area) {}

  /** Thermal relief geometry used during plane fill rendering. */
  public record ThermalReliefItem(
      java.awt.geom.Area clearanceArea,
      double cx,
      double cy,
      double expansionRadiusPx,
      double spokeWidthPx) {}
}

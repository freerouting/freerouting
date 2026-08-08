package app.freerouting.boardgraphics;

import app.freerouting.board.LayerStructure;
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
  public CoordinateTransform coordinateTransform;

  /**
   * layerVisibilityArr[i] is between 0 and 1, for each layer i, 0 is invisible and 1 fully visible.
   */
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

  public GraphicsContext(
      IntBox p_design_bounds,
      Dimension p_panel_bounds,
      LayerStructure p_layer_structure,
      Locale p_locale) {
    coordinateTransform = new CoordinateTransform(p_design_bounds, p_panel_bounds);
    itemColorTable = new ItemColorTableModel(p_layer_structure, p_locale);
    otherColorTable = new OtherColorTableModel(p_locale);
    colorIntensityTable = new ColorIntensityTable();
    layerVisibilityArr = new double[p_layer_structure.arr.length];
    for (int i = 0; i < layerVisibilityArr.length; i++) {
      if (p_layer_structure.arr[i].isSignal) {
        layerVisibilityArr[i] = 1.00;
      } else {
        layerVisibilityArr[i] = 0.25;
      }
    }
  }

  /** Copy constructor */
  public GraphicsContext(GraphicsContext p_graphics_context) {
    this.coordinateTransform = new CoordinateTransform(p_graphics_context.coordinateTransform);
    this.itemColorTable = new ItemColorTableModel(p_graphics_context.itemColorTable);
    this.otherColorTable = new OtherColorTableModel(p_graphics_context.otherColorTable);
    this.colorIntensityTable = new ColorIntensityTable(p_graphics_context.colorIntensityTable);
    this.layerVisibilityArr = p_graphics_context.copyLayerVisibilityArr();
    this.virtualLayerVisibilityArr = p_graphics_context.getVirtualLayerVisibilityArr().clone();
    this.fullyVisibleVirtualLayer = p_graphics_context.fullyVisibleVirtualLayer;
  }

  private static boolean[] createDefaultVirtualLayerVisibilityArr() {
    return new boolean[] {true, true, true, true, true, true};
  }

  private boolean[] getVirtualLayerVisibilityArr() {
    if (virtualLayerVisibilityArr == null || virtualLayerVisibilityArr.length == 0) {
      virtualLayerVisibilityArr = createDefaultVirtualLayerVisibilityArr();
    }
    return virtualLayerVisibilityArr;
  }

  /** initialise some values in p_graphics */
  private static void initDrawGraphics(Graphics2D p_graphics, Color p_color, float p_width) {
    BasicStroke bs =
        new BasicStroke(Math.max(p_width, 0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    p_graphics.setStroke(bs);
    p_graphics.setColor(p_color);
    p_graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  }

  static void setTranslucency(Graphics2D p_g2, double p_factor) {
    AlphaComposite currAlphaComposite;
    if (p_factor >= 0) {
      currAlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) p_factor);
    } else {
      currAlphaComposite = AlphaComposite.getInstance(AlphaComposite.DST_OVER, (float) -p_factor);
    }
    p_g2.setComposite(currAlphaComposite);
  }

  /**
   * Changes the bounds of the board design to p_design_bounds. Useful when components are still
   * placed outside the board.
   */
  public void changeDesignBounds(IntBox p_new_design_bounds) {
    if (p_new_design_bounds.equals(this.coordinateTransform.designBox)) {
      return;
    }
    Dimension screenBounds = this.coordinateTransform.screenBounds;
    this.coordinateTransform = new CoordinateTransform(p_new_design_bounds, screenBounds);
  }

  /** changes the size of the panel to p_new_bounds */
  public void changePanelSize(Dimension p_new_bounds) {
    if (coordinateTransform == null) {
      return;
    }
    IntBox designBox = coordinateTransform.designBox;
    boolean leftRightSwapped = coordinateTransform.isMirrorLeftRight();
    boolean topBottomSwapped = coordinateTransform.isMirrorTopBottom();
    double rotation = coordinateTransform.getRotation();
    coordinateTransform = new CoordinateTransform(designBox, p_new_bounds);
    coordinateTransform.setMirrorLeftRight(leftRightSwapped);
    coordinateTransform.setMirrorTopBottom(topBottomSwapped);
    coordinateTransform.setRotation(rotation);
  }

  /** draws a polygon with corners p_points */
  public void draw(
      FloatPoint[] p_points,
      double p_half_width,
      Color p_color,
      Graphics p_g,
      double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) p_g;
    Rectangle clipShape = p_g.getClip().getBounds();
    // the class member updateBox cannot be used here, because
    // the dirty rectangle is internally enlarged by the system.
    // Therefore, we can not improve the performance by using an
    // update octagon instead of a box.
    IntBox clipBox = coordinateTransform.screenToBoard(clipShape);
    double scaledWidth = coordinateTransform.boardToScreen(p_half_width);

    initDrawGraphics(g2, p_color, (float) scaledWidth * 2);
    setTranslucency(g2, p_translucency_factor);

    GeneralPath drawPath = null;
    if (!show_line_segments) {
      drawPath = new GeneralPath();
    }

    for (int i = 0; i < (p_points.length - 1); i++) {
      if (lineOutsideUpdateBox(
          p_points[i], p_points[i + 1], p_half_width + update_offset, clipBox)) {
        // this check should be unnecessary here,
        // the system should do it in the draw(line) function
        continue;
      }
      Point2D p1 = coordinateTransform.boardToScreen(p_points[i]);
      Point2D p2 = coordinateTransform.boardToScreen(p_points[i + 1]);
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
   * draws the boundary of a circle
   */
  public void drawCircle(
      FloatPoint p_center,
      double p_radius,
      double p_draw_half_width,
      Color p_color,
      Graphics p_g,
      double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) p_g;
    Point2D center = coordinateTransform.boardToScreen(p_center);

    double radius = coordinateTransform.boardToScreen(p_radius);
    double diameter = 2 * radius;
    float drawWidth = (float) (2 * coordinateTransform.boardToScreen(p_draw_half_width));
    Ellipse2D circle =
        new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter);
    setTranslucency(g2, p_translucency_factor);
    initDrawGraphics(g2, p_color, drawWidth);
    g2.draw(circle);
  }

  /*
   * draws a rectangle
   */
  public void drawRectangle(
      FloatPoint p_corner1,
      FloatPoint p_corner2,
      double p_draw_half_width,
      Color p_color,
      Graphics p_g,
      double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) p_g;
    Point2D corner1 = coordinateTransform.boardToScreen(p_corner1);
    Point2D corner2 = coordinateTransform.boardToScreen(p_corner2);

    double xmin = Math.min(corner1.getX(), corner2.getX());
    double ymin = Math.min(corner1.getY(), corner2.getY());

    float drawWidth = (float) (2 * coordinateTransform.boardToScreen(p_draw_half_width));
    double width = Math.abs(corner2.getX() - corner1.getX());
    double height = Math.abs(corner2.getY() - corner1.getY());
    Rectangle2D rectangle = new Rectangle2D.Double(xmin, ymin, width, height);
    setTranslucency(g2, p_translucency_factor);
    initDrawGraphics(g2, p_color, drawWidth);
    g2.draw(rectangle);
  }

  /** Draws the boundary of p_shape. */
  public void drawBoundary(
      Shape p_shape,
      double p_draw_half_width,
      Color p_color,
      Graphics p_g,
      double p_translucency_factor) {
    if (p_shape instanceof PolylineShape) {
      FloatPoint[] drawCorners = p_shape.cornerApproxArr();
      if (drawCorners.length <= 1) {
        return;
      }
      FloatPoint[] closedDrawCorners = new FloatPoint[drawCorners.length + 1];
      System.arraycopy(drawCorners, 0, closedDrawCorners, 0, drawCorners.length);
      closedDrawCorners[closedDrawCorners.length - 1] = drawCorners[0];
      this.draw(closedDrawCorners, p_draw_half_width, p_color, p_g, p_translucency_factor);
    } else if (p_shape instanceof Circle curr_circle) {
      this.drawCircle(
          curr_circle.center.toFloat(),
          curr_circle.radius,
          p_draw_half_width,
          p_color,
          p_g,
          p_translucency_factor);
    }
  }

  /** Draws the boundary of p_area. */
  public void drawBoundary(
      Area p_area,
      double p_draw_half_width,
      Color p_color,
      Graphics p_g,
      double p_translucency_factor) {
    drawBoundary(p_area.getBorder(), p_draw_half_width, p_color, p_g, p_translucency_factor);
    Shape[] holes = p_area.getHoles();
    for (int i = 0; i < holes.length; i++) {
      drawBoundary(holes[i], p_draw_half_width, p_color, p_g, p_translucency_factor);
    }
  }

  private transient java.awt.TexturePaint cachedHatchPaint;
  private transient double cachedHatchPitchPx = -1.0;
  private transient Color cachedHatchColor;

  public java.awt.geom.Area getAwtArea(Area p_area) {
    if (p_area == null || p_area.isEmpty()) {
      return null;
    }
    if (p_area instanceof Circle circle) {
      Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
      double radius = coordinateTransform.boardToScreen(circle.radius);
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter));
    }

    Shape borderShape = p_area.getBorder();
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

    Shape[] holes = p_area.getHoles();
    for (Shape hole : holes) {
      if (hole instanceof PolylineShape holePoly) {
        int hCount = holePoly.borderLineCount();
        if (hCount > 0) {
          java.awt.geom.Path2D.Double holePath = new java.awt.geom.Path2D.Double();
          Point2D hp0 = coordinateTransform.boardToScreen(holePoly.cornerApprox(0));
          holePath.moveTo(hp0.getX(), hp0.getY());
          for (int i = 1; i < hCount; i++) {
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

  public void drawPlaneHatch(
      Area p_area,
      Graphics p_g,
      Color p_color,
      double p_translucency_factor,
      double p_pitch_board_units) {
    if (p_color == null || p_area == null || p_area.isEmpty() || p_translucency_factor <= 0) {
      return;
    }
    double pitchPx = coordinateTransform.boardToScreen(p_pitch_board_units);
    if (pitchPx < 2.0) {
      return;
    }
    if (pitchPx > 1000.0) {
      pitchPx = 1000.0;
    }
    int pInt = (int) Math.round(pitchPx);

    java.awt.geom.Area outerArea = getAwtArea(p_area);
    if (outerArea == null || outerArea.isEmpty()) {
      return;
    }

    Graphics2D g2 = (Graphics2D) p_g;
    java.awt.Paint oldPaint = g2.getPaint();
    java.awt.Composite oldComposite = g2.getComposite();

    setTranslucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (cachedHatchPaint == null
        || cachedHatchPitchPx != pitchPx
        || !p_color.equals(cachedHatchColor)) {
      java.awt.image.BufferedImage bi =
          new java.awt.image.BufferedImage(pInt, pInt, java.awt.image.BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2t = bi.createGraphics();
      g2t.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2t.setColor(p_color);
      g2t.setStroke(new BasicStroke(1.0f));
      g2t.drawLine(0, pInt, pInt, 0);
      g2t.drawLine(-1, pInt + 1, pInt + 1, -1);
      g2t.dispose();
      cachedHatchPaint = new java.awt.TexturePaint(bi, new Rectangle2D.Double(0, 0, pInt, pInt));
      cachedHatchPitchPx = pitchPx;
      cachedHatchColor = p_color;
    }

    java.awt.Shape oldClip = g2.getClip();
    java.awt.Stroke oldStroke = g2.getStroke();
    g2.clip(outerArea);
    g2.setPaint(cachedHatchPaint);
    g2.setStroke(
        new BasicStroke((float) (2 * pitchPx), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
    g2.draw(outerArea);

    g2.setStroke(oldStroke);
    g2.setClip(oldClip);
    g2.setPaint(oldPaint);
    g2.setComposite(oldComposite);
  }

  public java.awt.geom.Area getAwtAreaFromShape(Shape p_shape) {
    if (p_shape == null) {
      return null;
    }
    if (p_shape instanceof Circle circle) {
      Point2D center = coordinateTransform.boardToScreen(circle.center.toFloat());
      double radius = coordinateTransform.boardToScreen(circle.radius);
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter));
    }
    if (p_shape instanceof PolylineShape poly) {
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

  public record ClearanceItem(java.awt.geom.Area area) {}

  public record ThermalReliefItem(
      java.awt.geom.Area clearanceArea,
      double cx,
      double cy,
      double expansionRadiusPx,
      double spokeWidthPx) {}

  public void fillPlaneArea(
      Area p_area,
      Graphics p_g,
      Color p_color,
      double p_translucency_factor,
      java.util.List<ClearanceItem> p_clearances,
      java.util.List<ThermalReliefItem> p_thermals) {
    if (p_color == null || p_area == null || p_area.isEmpty() || p_translucency_factor <= 0) {
      return;
    }

    java.awt.geom.Area fillArea = getAwtArea(p_area);
    if (fillArea == null || fillArea.isEmpty()) {
      return;
    }

    // Subtract foreign clearances
    if (p_clearances != null) {
      for (ClearanceItem item : p_clearances) {
        if (item != null && item.area != null) {
          fillArea.subtract(item.area);
        }
      }
    }

    // Process thermal reliefs
    if (p_thermals != null) {
      for (ThermalReliefItem thermal : p_thermals) {
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

    Graphics2D g2 = (Graphics2D) p_g;
    java.awt.Paint oldPaint = g2.getPaint();
    java.awt.Composite oldComposite = g2.getComposite();

    g2.setColor(p_color);
    setTranslucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(fillArea);

    g2.setPaint(oldPaint);
    g2.setComposite(oldComposite);
  }

  /** Draws the interior of a circle */
  public void fillCircle(
      Circle p_circle, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    Point2D center = coordinateTransform.boardToScreen(p_circle.center.toFloat());
    double radius = coordinateTransform.boardToScreen(p_circle.radius);
    if (!pointNearRectangle(center.getX(), center.getY(), p_g.getClip().getBounds(), radius)) {
      return;
    }
    double diameter = 2 * radius;
    Ellipse2D circle =
        new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter);
    Graphics2D g2 = (Graphics2D) p_g;
    g2.setColor(p_color);
    setTranslucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(circle);
  }

  /** Draws the interior of an ellipse. */
  public void fillEllipse(
      Ellipse p_ellipse, Graphics p_g, Color p_color, double p_translucency_factor) {
    Ellipse[] ellipseArr = new Ellipse[1];
    ellipseArr[0] = p_ellipse;
    fillEllipseArr(ellipseArr, p_g, p_color, p_translucency_factor);
  }

  /**
   * Draws the interior of an array of ellipses. Ellipses contained in another ellipse are treated
   * as holes.
   */
  public void fillEllipseArr(
      Ellipse[] p_ellipse_arr, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null || p_ellipse_arr.length == 0) {
      return;
    }
    GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (Ellipse curr_ellipse : p_ellipse_arr) {
      Point2D center = coordinateTransform.boardToScreen(curr_ellipse.center);
      double biggerRadius = coordinateTransform.boardToScreen(curr_ellipse.biggerRadius);
      if (!pointNearRectangle(
          center.getX(), center.getY(), p_g.getClip().getBounds(), biggerRadius)) {
        continue;
      }
      double smallerRadius = coordinateTransform.boardToScreen(curr_ellipse.smallerRadius);
      Ellipse2D drawEllipse =
          new Ellipse2D.Double(
              center.getX() - biggerRadius,
              center.getY() - smallerRadius,
              2 * biggerRadius,
              2 * smallerRadius);
      double rotation = coordinateTransform.boardToScreenAngle(curr_ellipse.rotation);
      AffineTransform affineTransform = new AffineTransform();
      affineTransform.rotate(rotation, center.getX(), center.getY());
      java.awt.Shape rotatedEllipse = affineTransform.createTransformedShape(drawEllipse);
      drawPath.append(rotatedEllipse, false);
    }
    Graphics2D g2 = (Graphics2D) p_g;
    g2.setColor(p_color);
    setTranslucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPath);
  }

  /** Checks, if the distance of the point with coordinates p_x, p_y to p_rect is at most p_dist. */
  private boolean pointNearRectangle(double p_x, double p_y, Rectangle p_rect, double p_dist) {
    if (p_x < p_rect.x - p_dist) {
      return false;
    }
    if (p_y < p_rect.y - p_dist) {
      return false;
    }
    if (p_x > p_rect.x + p_rect.width + p_dist) {
      return false;
    }
    return p_y <= p_rect.y + p_rect.height + p_dist;
  }

  /** Fill the interior of the polygon shape represented by p_points. */
  public void fillShape(
      FloatPoint[] p_points, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) p_g;
    Polygon drawPolygon = new Polygon();
    for (int i = 0; i < p_points.length; i++) {
      Point2D currCorner = coordinateTransform.boardToScreen(p_points[i]);
      drawPolygon.addPoint(
          (int) Math.round(currCorner.getX()), (int) Math.round(currCorner.getY()));
    }
    g2.setColor(p_color);
    setTranslucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPolygon);
  }

  /**
   * Fill the interior of a list of polygons. Used for example with an area consisting of a border
   * polygon and some holes.
   */
  public void fillArea(
      FloatPoint[][] p_point_lists, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (int j = 0; j < p_point_lists.length; j++) {
      Polygon drawPolygon = new Polygon();
      FloatPoint[] currPointList = p_point_lists[j];
      for (int i = 0; i < currPointList.length; i++) {
        Point2D currCorner = coordinateTransform.boardToScreen(currPointList[i]);
        drawPolygon.addPoint(
            (int) Math.round(currCorner.getX()), (int) Math.round(currCorner.getY()));
      }
      drawPath.append(drawPolygon, false);
    }
    Graphics2D g2 = (Graphics2D) p_g;
    g2.setColor(p_color);
    setTranslucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPath);
  }

  /** draws the interior of an item of class geometry.planar.Area */
  public void fillArea(Area p_area, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null || p_area.isEmpty()) {
      return;
    }
    if (p_area instanceof Circle circle) {
      fillCircle(circle, p_g, p_color, p_translucency_factor);
    } else {
      PolylineShape border = (PolylineShape) p_area.getBorder();
      if (!border.isBounded()) {
        FRLogger.warn("GraphicsContext.fill_area: shape not bounded");
        return;
      }
      Rectangle clipShape = p_g.getClip().getBounds();
      IntBox clipBox = coordinateTransform.screenToBoard(clipShape);
      if (!border.boundingBox().intersects(clipBox)) {
        return;
      }
      Shape[] holes = p_area.getHoles();

      FloatPoint[][] drawPolygons = new FloatPoint[holes.length + 1][];
      for (int j = 0; j < drawPolygons.length; j++) {
        PolylineShape currDrawShape;
        if (j == 0) {
          currDrawShape = border;
        } else {
          currDrawShape = (PolylineShape) holes[j - 1];
        }
        drawPolygons[j] = new FloatPoint[currDrawShape.borderLineCount() + 1];
        FloatPoint[] currDrawPolygon = drawPolygons[j];
        for (int i = 0; i < currDrawPolygon.length - 1; i++) {
          currDrawPolygon[i] = currDrawShape.cornerApprox(i);
        }
        // close the polygon
        currDrawPolygon[currDrawPolygon.length - 1] = currDrawPolygon[0];
      }
      fillArea(drawPolygons, p_g, p_color, p_translucency_factor);
    }
    if (show_area_division) {
      TileShape[] tiles = p_area.splitToConvex();
      for (int i = 0; i < tiles.length; i++) {
        FloatPoint[] corners = new FloatPoint[tiles[i].borderLineCount() + 1];
        TileShape currTile = tiles[i];
        for (int j = 0; j < corners.length - 1; j++) {
          corners[j] = currTile.cornerApprox(j);
        }
        corners[corners.length - 1] = corners[0];
        draw(corners, 1, Color.white, p_g, 0.7);
      }
    }
  }

  public Color getBackgroundColor() {
    return otherColorTable.getBackgroundColor();
  }

  public Color getHilightColor() {
    return otherColorTable.getHilightColor();
  }

  public Color getIncompleteColor() {
    return otherColorTable.getIncompleteColor();
  }

  public Color getOutlineColor() {
    return otherColorTable.getOutlineColor();
  }

  public Color getComponentColor(boolean p_front) {
    return otherColorTable.getComponentColor(p_front);
  }

  public Color getViolationsColor() {
    return otherColorTable.getViolationsColor();
  }

  public Color getLengthMatchingAreaColor() {
    return otherColorTable.getLengthMatchingAreaColor();
  }

  public Color[] getTraceColors(boolean p_fixed) {

    return itemColorTable.getTraceColors(p_fixed);
  }

  public Color[] getViaColors(boolean p_fixed) {
    return itemColorTable.getViaColors(p_fixed);
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

  public void setTraceColorIntensity(double p_value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.TRACES.ordinal(), p_value);
  }

  public double getViaColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.VIAS.ordinal());
  }

  public void setViaColorIntensity(double p_value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.VIAS.ordinal(), p_value);
  }

  public double getPinColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.PINS.ordinal());
  }

  public void setPinColorIntensity(double p_value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.PINS.ordinal(), p_value);
  }

  public double getConductionColorIntensity() {
    return colorIntensityTable.getValue(
        ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal());
  }

  public void setConductionColorIntensity(double p_value) {
    colorIntensityTable.setValue(
        ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(), p_value);
  }

  public double getObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal());
  }

  public void setObstacleColorIntensity(double p_value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal(), p_value);
  }

  public double getViaObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal());
  }

  public void setViaObstacleColorIntensity(double p_value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal(), p_value);
  }

  public double getPlaceObstacleColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.PLACE_KEEPOUTS.ordinal());
  }

  public double getComponentOutlineColorIntensity() {
    return colorIntensityTable.getValue(
        ColorIntensityTable.ObjectNames.COMPONENT_OUTLINES.ordinal());
  }

  public double getHilightColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.HILIGHT.ordinal());
  }

  public void setHilightColorIntensity(double p_value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.HILIGHT.ordinal(), p_value);
  }

  public double getIncompleteColorIntensity() {
    return colorIntensityTable.getValue(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal());
  }

  public void setIncompleteColorIntensity(double p_value) {
    colorIntensityTable.setValue(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal(), p_value);
  }

  public double getLengthMatchingAreaColorIntensity() {
    return colorIntensityTable.getValue(
        ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal());
  }

  public void setLengthMatchingAreaColorIntensity(double p_value) {
    colorIntensityTable.setValue(
        ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal(), p_value);
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

  /** gets the factor for automatic layer dimming */
  public double getAutoLayerDimFactor() {
    return this.autoLayerDimFactor;
  }

  /**
   * Sets the factor for automatic layer dimming. Values are between 0 and 1. If 1, there is no
   * automatic layer dimming.
   */
  public void setAutoLayerDimFactor(double p_value) {
    autoLayerDimFactor = p_value;
  }

  /** Sets the layer, which will be excluded from automatic layer dimming. */
  public void setFullyVisibleLayer(int p_layer_no) {
    fullyVisibleLayer = p_layer_no;
    if (p_layer_no != -1) {
      fullyVisibleVirtualLayer = -1;
    }
  }

  public int getFullyVisibleLayer() {
    return fullyVisibleLayer;
  }

  public void setSimplifiedPlaneRendering(boolean simplifiedPlaneRendering) {
    this.simplifiedPlaneRendering = simplifiedPlaneRendering;
  }

  public boolean isSimplifiedPlaneRendering() {
    return simplifiedPlaneRendering;
  }

  public int getFullyVisibleVirtualLayer() {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (fullyVisibleVirtualLayer < -1 || fullyVisibleVirtualLayer >= visibilityArr.length) {
      return -1;
    }
    return fullyVisibleVirtualLayer;
  }

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

  public boolean getVirtualLayerVisible(int idx) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (idx >= 0 && idx < visibilityArr.length) {
      return visibilityArr[idx];
    }
    return true;
  }

  public void setVirtualLayerVisible(int idx, boolean visible) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (idx >= 0 && idx < visibilityArr.length) {
      visibilityArr[idx] = visible;
    }
  }

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

  public double getVirtualLayerVisibility(int virtual_layer_idx) {
    boolean[] visibilityArr = getVirtualLayerVisibilityArr();
    if (virtual_layer_idx < 0 || virtual_layer_idx >= visibilityArr.length) {
      return 1.0;
    }
    if (!visibilityArr[virtual_layer_idx]) {
      return 0.0;
    }
    if (fullyVisibleLayer != -1) {
      return this.autoLayerDimFactor;
    }
    int selectedVirtualLayer = getFullyVisibleVirtualLayer();
    if (selectedVirtualLayer != -1) {
      if (selectedVirtualLayer == virtual_layer_idx) {
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
  public double getLayerVisibility(int p_layer_no) {
    double result;
    if (fullyVisibleVirtualLayer != -1) {
      result = this.autoLayerDimFactor * layerVisibilityArr[p_layer_no];
    } else if (p_layer_no == this.fullyVisibleLayer) {
      result = layerVisibilityArr[p_layer_no];
    } else {
      result = this.autoLayerDimFactor * layerVisibilityArr[p_layer_no];
    }
    return result;
  }

  /** Gets the visibility factor of the input layer without the automatic layer dimming. */
  public double getRawLayerVisibility(int p_layer_no) {
    return layerVisibilityArr[p_layer_no];
  }

  /**
   * Gets the visibility factor of the input layer. The value is expected between 0 and 1. If the
   * value is 0, the layer is invisible, if the value is 1, the layer is fully visible.
   */
  public void setLayerVisibility(int p_layer_no, double p_value) {
    layerVisibilityArr[p_layer_no] = Math.max(0, Math.min(p_value, 1));
  }

  public void setLayerVisibilityArr(double[] p_layer_visibility_arr) {
    this.layerVisibilityArr = p_layer_visibility_arr;
  }

  public double[] copyLayerVisibilityArr() {
    double[] result = new double[this.layerVisibilityArr.length];
    System.arraycopy(this.layerVisibilityArr, 0, result, 0, this.layerVisibilityArr.length);
    return result;
  }

  /** Returns the number of layers on the board */
  public int layerCount() {
    return layerVisibilityArr.length;
  }

  /**
   * filter lines, which cannot touch the updateBox to improve the performance of the draw function
   * by avoiding unnecessary calls of draw (line)
   */
  private boolean lineOutsideUpdateBox(
      FloatPoint p_1, FloatPoint p_2, double p_update_offset, IntBox p_update_box) {
    if (p_1 == null || p_2 == null) {
      return true;
    }
    if (Math.max(p_1.x, p_2.x) < p_update_box.ll.x - p_update_offset) {
      return true;
    }
    if (Math.max(p_1.y, p_2.y) < p_update_box.ll.y - p_update_offset) {
      return true;
    }
    if (Math.min(p_1.x, p_2.x) > p_update_box.ur.x + p_update_offset) {
      return true;
    }
    return Math.min(p_1.y, p_2.y) > p_update_box.ur.y + p_update_offset;
  }

  /** Writes an instance of this class to a file. */
  private void writeObject(ObjectOutputStream p_stream) throws IOException {
    p_stream.defaultWriteObject();
    itemColorTable.writeObject(p_stream);
    otherColorTable.writeObject(p_stream);
  }

  /** Reads an instance of this class from a file */
  private void readObject(ObjectInputStream p_stream) throws IOException, ClassNotFoundException {
    p_stream.defaultReadObject();
    if (virtualLayerVisibilityArr == null
        || virtualLayerVisibilityArr.length != virtual_layer_count) {
      virtualLayerVisibilityArr = createDefaultVirtualLayerVisibilityArr();
    }
    fullyVisibleVirtualLayer = getFullyVisibleVirtualLayer();
    this.itemColorTable = new ItemColorTableModel(p_stream);
    this.otherColorTable = new OtherColorTableModel(p_stream);
  }
}

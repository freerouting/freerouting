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

  private boolean[] virtualLayerVisibilityArr = create_default_virtual_layer_visibility_arr();
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
    this.layerVisibilityArr = p_graphics_context.copy_layer_visibility_arr();
    this.virtualLayerVisibilityArr = p_graphics_context.get_virtual_layer_visibility_arr().clone();
    this.fullyVisibleVirtualLayer = p_graphics_context.fullyVisibleVirtualLayer;
  }

  private static boolean[] create_default_virtual_layer_visibility_arr() {
    return new boolean[] {true, true, true, true, true, true};
  }

  private boolean[] get_virtual_layer_visibility_arr() {
    if (virtualLayerVisibilityArr == null || virtualLayerVisibilityArr.length == 0) {
      virtualLayerVisibilityArr = create_default_virtual_layer_visibility_arr();
    }
    return virtualLayerVisibilityArr;
  }

  /** initialise some values in p_graphics */
  private static void init_draw_graphics(Graphics2D p_graphics, Color p_color, float p_width) {
    BasicStroke bs =
        new BasicStroke(Math.max(p_width, 0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    p_graphics.setStroke(bs);
    p_graphics.setColor(p_color);
    p_graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
  }

  static void set_translucency(Graphics2D p_g2, double p_factor) {
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
  public void change_design_bounds(IntBox p_new_design_bounds) {
    if (p_new_design_bounds.equals(this.coordinateTransform.designBox)) {
      return;
    }
    Dimension screenBounds = this.coordinateTransform.screenBounds;
    this.coordinateTransform = new CoordinateTransform(p_new_design_bounds, screenBounds);
  }

  /** changes the size of the panel to p_new_bounds */
  public void change_panel_size(Dimension p_new_bounds) {
    if (coordinateTransform == null) {
      return;
    }
    IntBox designBox = coordinateTransform.designBox;
    boolean leftRightSwapped = coordinateTransform.is_mirror_left_right();
    boolean topBottomSwapped = coordinateTransform.is_mirror_top_bottom();
    double rotation = coordinateTransform.get_rotation();
    coordinateTransform = new CoordinateTransform(designBox, p_new_bounds);
    coordinateTransform.set_mirror_left_right(leftRightSwapped);
    coordinateTransform.set_mirror_top_bottom(topBottomSwapped);
    coordinateTransform.set_rotation(rotation);
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
    IntBox clipBox = coordinateTransform.screen_to_board(clipShape);
    double scaledWidth = coordinateTransform.board_to_screen(p_half_width);

    init_draw_graphics(g2, p_color, (float) scaledWidth * 2);
    set_translucency(g2, p_translucency_factor);

    GeneralPath drawPath = null;
    if (!show_line_segments) {
      drawPath = new GeneralPath();
    }

    for (int i = 0; i < (p_points.length - 1); i++) {
      if (line_outside_update_box(
          p_points[i], p_points[i + 1], p_half_width + update_offset, clipBox)) {
        // this check should be unnecessary here,
        // the system should do it in the draw(line) function
        continue;
      }
      Point2D p1 = coordinateTransform.board_to_screen(p_points[i]);
      Point2D p2 = coordinateTransform.board_to_screen(p_points[i + 1]);
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
  public void draw_circle(
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
    Point2D center = coordinateTransform.board_to_screen(p_center);

    double radius = coordinateTransform.board_to_screen(p_radius);
    double diameter = 2 * radius;
    float drawWidth = (float) (2 * coordinateTransform.board_to_screen(p_draw_half_width));
    Ellipse2D circle =
        new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter);
    set_translucency(g2, p_translucency_factor);
    init_draw_graphics(g2, p_color, drawWidth);
    g2.draw(circle);
  }

  /*
   * draws a rectangle
   */
  public void draw_rectangle(
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
    Point2D corner1 = coordinateTransform.board_to_screen(p_corner1);
    Point2D corner2 = coordinateTransform.board_to_screen(p_corner2);

    double xmin = Math.min(corner1.getX(), corner2.getX());
    double ymin = Math.min(corner1.getY(), corner2.getY());

    float drawWidth = (float) (2 * coordinateTransform.board_to_screen(p_draw_half_width));
    double width = Math.abs(corner2.getX() - corner1.getX());
    double height = Math.abs(corner2.getY() - corner1.getY());
    Rectangle2D rectangle = new Rectangle2D.Double(xmin, ymin, width, height);
    set_translucency(g2, p_translucency_factor);
    init_draw_graphics(g2, p_color, drawWidth);
    g2.draw(rectangle);
  }

  /** Draws the boundary of p_shape. */
  public void draw_boundary(
      Shape p_shape,
      double p_draw_half_width,
      Color p_color,
      Graphics p_g,
      double p_translucency_factor) {
    if (p_shape instanceof PolylineShape) {
      FloatPoint[] drawCorners = p_shape.corner_approx_arr();
      if (drawCorners.length <= 1) {
        return;
      }
      FloatPoint[] closedDrawCorners = new FloatPoint[drawCorners.length + 1];
      System.arraycopy(drawCorners, 0, closedDrawCorners, 0, drawCorners.length);
      closedDrawCorners[closedDrawCorners.length - 1] = drawCorners[0];
      this.draw(closedDrawCorners, p_draw_half_width, p_color, p_g, p_translucency_factor);
    } else if (p_shape instanceof Circle curr_circle) {
      this.draw_circle(
          curr_circle.center.to_float(),
          curr_circle.radius,
          p_draw_half_width,
          p_color,
          p_g,
          p_translucency_factor);
    }
  }

  /** Draws the boundary of p_area. */
  public void draw_boundary(
      Area p_area,
      double p_draw_half_width,
      Color p_color,
      Graphics p_g,
      double p_translucency_factor) {
    draw_boundary(p_area.get_border(), p_draw_half_width, p_color, p_g, p_translucency_factor);
    Shape[] holes = p_area.get_holes();
    for (int i = 0; i < holes.length; i++) {
      draw_boundary(holes[i], p_draw_half_width, p_color, p_g, p_translucency_factor);
    }
  }

  private transient java.awt.TexturePaint cachedHatchPaint;
  private transient double cachedHatchPitchPx = -1.0;
  private transient Color cachedHatchColor;

  public java.awt.geom.Area get_awt_area(Area p_area) {
    if (p_area == null || p_area.is_empty()) {
      return null;
    }
    if (p_area instanceof Circle circle) {
      Point2D center = coordinateTransform.board_to_screen(circle.center.to_float());
      double radius = coordinateTransform.board_to_screen(circle.radius);
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter));
    }

    Shape borderShape = p_area.get_border();
    if (!(borderShape instanceof PolylineShape border) || !border.is_bounded()) {
      return null;
    }

    java.awt.geom.Path2D.Double borderPath = new java.awt.geom.Path2D.Double();
    int count = border.border_line_count();
    if (count > 0) {
      Point2D p0 = coordinateTransform.board_to_screen(border.corner_approx(0));
      borderPath.moveTo(p0.getX(), p0.getY());
      for (int i = 1; i < count; i++) {
        Point2D pi = coordinateTransform.board_to_screen(border.corner_approx(i));
        borderPath.lineTo(pi.getX(), pi.getY());
      }
      borderPath.closePath();
    }
    java.awt.geom.Area awtArea = new java.awt.geom.Area(borderPath);

    Shape[] holes = p_area.get_holes();
    for (Shape hole : holes) {
      if (hole instanceof PolylineShape holePoly) {
        int hCount = holePoly.border_line_count();
        if (hCount > 0) {
          java.awt.geom.Path2D.Double holePath = new java.awt.geom.Path2D.Double();
          Point2D hp0 = coordinateTransform.board_to_screen(holePoly.corner_approx(0));
          holePath.moveTo(hp0.getX(), hp0.getY());
          for (int i = 1; i < hCount; i++) {
            Point2D hpi = coordinateTransform.board_to_screen(holePoly.corner_approx(i));
            holePath.lineTo(hpi.getX(), hpi.getY());
          }
          holePath.closePath();
          awtArea.subtract(new java.awt.geom.Area(holePath));
        }
      } else if (hole instanceof Circle circle) {
        Point2D center = coordinateTransform.board_to_screen(circle.center.to_float());
        double radius = coordinateTransform.board_to_screen(circle.radius);
        double diameter = 2 * radius;
        awtArea.subtract(
            new java.awt.geom.Area(
                new Ellipse2D.Double(
                    center.getX() - radius, center.getY() - radius, diameter, diameter)));
      }
    }
    return awtArea;
  }

  public void draw_plane_hatch(
      Area p_area,
      Graphics p_g,
      Color p_color,
      double p_translucency_factor,
      double p_pitch_board_units) {
    if (p_color == null || p_area == null || p_area.is_empty() || p_translucency_factor <= 0) {
      return;
    }
    double pitchPx = coordinateTransform.board_to_screen(p_pitch_board_units);
    if (pitchPx < 2.0) {
      return;
    }
    if (pitchPx > 1000.0) {
      pitchPx = 1000.0;
    }
    int pInt = (int) Math.round(pitchPx);

    java.awt.geom.Area outerArea = get_awt_area(p_area);
    if (outerArea == null || outerArea.isEmpty()) {
      return;
    }

    Graphics2D g2 = (Graphics2D) p_g;
    java.awt.Paint oldPaint = g2.getPaint();
    java.awt.Composite oldComposite = g2.getComposite();

    set_translucency(g2, p_translucency_factor);
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

  public java.awt.geom.Area get_awt_area_from_shape(Shape p_shape) {
    if (p_shape == null) {
      return null;
    }
    if (p_shape instanceof Circle circle) {
      Point2D center = coordinateTransform.board_to_screen(circle.center.to_float());
      double radius = coordinateTransform.board_to_screen(circle.radius);
      double diameter = 2 * radius;
      return new java.awt.geom.Area(
          new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter));
    }
    if (p_shape instanceof PolylineShape poly) {
      int count = poly.border_line_count();
      if (count <= 0) {
        return null;
      }
      java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
      Point2D p0 = coordinateTransform.board_to_screen(poly.corner_approx(0));
      path.moveTo(p0.getX(), p0.getY());
      for (int i = 1; i < count; i++) {
        Point2D pi = coordinateTransform.board_to_screen(poly.corner_approx(i));
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

  public void fill_plane_area(
      Area p_area,
      Graphics p_g,
      Color p_color,
      double p_translucency_factor,
      java.util.List<ClearanceItem> p_clearances,
      java.util.List<ThermalReliefItem> p_thermals) {
    if (p_color == null || p_area == null || p_area.is_empty() || p_translucency_factor <= 0) {
      return;
    }

    java.awt.geom.Area fillArea = get_awt_area(p_area);
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
    set_translucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(fillArea);

    g2.setPaint(oldPaint);
    g2.setComposite(oldComposite);
  }

  /** Draws the interior of a circle */
  public void fill_circle(
      Circle p_circle, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    Point2D center = coordinateTransform.board_to_screen(p_circle.center.to_float());
    double radius = coordinateTransform.board_to_screen(p_circle.radius);
    if (!point_near_rectangle(center.getX(), center.getY(), p_g.getClip().getBounds(), radius)) {
      return;
    }
    double diameter = 2 * radius;
    Ellipse2D circle =
        new Ellipse2D.Double(center.getX() - radius, center.getY() - radius, diameter, diameter);
    Graphics2D g2 = (Graphics2D) p_g;
    g2.setColor(p_color);
    set_translucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(circle);
  }

  /** Draws the interior of an ellipse. */
  public void fill_ellipse(
      Ellipse p_ellipse, Graphics p_g, Color p_color, double p_translucency_factor) {
    Ellipse[] ellipseArr = new Ellipse[1];
    ellipseArr[0] = p_ellipse;
    fill_ellipse_arr(ellipseArr, p_g, p_color, p_translucency_factor);
  }

  /**
   * Draws the interior of an array of ellipses. Ellipses contained in another ellipse are treated
   * as holes.
   */
  public void fill_ellipse_arr(
      Ellipse[] p_ellipse_arr, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null || p_ellipse_arr.length == 0) {
      return;
    }
    GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (Ellipse curr_ellipse : p_ellipse_arr) {
      Point2D center = coordinateTransform.board_to_screen(curr_ellipse.center);
      double biggerRadius = coordinateTransform.board_to_screen(curr_ellipse.biggerRadius);
      if (!point_near_rectangle(
          center.getX(), center.getY(), p_g.getClip().getBounds(), biggerRadius)) {
        continue;
      }
      double smallerRadius = coordinateTransform.board_to_screen(curr_ellipse.smallerRadius);
      Ellipse2D drawEllipse =
          new Ellipse2D.Double(
              center.getX() - biggerRadius,
              center.getY() - smallerRadius,
              2 * biggerRadius,
              2 * smallerRadius);
      double rotation = coordinateTransform.board_to_screen_angle(curr_ellipse.rotation);
      AffineTransform affineTransform = new AffineTransform();
      affineTransform.rotate(rotation, center.getX(), center.getY());
      java.awt.Shape rotatedEllipse = affineTransform.createTransformedShape(drawEllipse);
      drawPath.append(rotatedEllipse, false);
    }
    Graphics2D g2 = (Graphics2D) p_g;
    g2.setColor(p_color);
    set_translucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPath);
  }

  /** Checks, if the distance of the point with coordinates p_x, p_y to p_rect is at most p_dist. */
  private boolean point_near_rectangle(double p_x, double p_y, Rectangle p_rect, double p_dist) {
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
  public void fill_shape(
      FloatPoint[] p_points, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) p_g;
    Polygon drawPolygon = new Polygon();
    for (int i = 0; i < p_points.length; i++) {
      Point2D currCorner = coordinateTransform.board_to_screen(p_points[i]);
      drawPolygon.addPoint(
          (int) Math.round(currCorner.getX()), (int) Math.round(currCorner.getY()));
    }
    g2.setColor(p_color);
    set_translucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPolygon);
  }

  /**
   * Fill the interior of a list of polygons. Used for example with an area consisting of a border
   * polygon and some holes.
   */
  public void fill_area(
      FloatPoint[][] p_point_lists, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null) {
      return;
    }
    GeneralPath drawPath = new GeneralPath(GeneralPath.WIND_EVEN_ODD);
    for (int j = 0; j < p_point_lists.length; j++) {
      Polygon drawPolygon = new Polygon();
      FloatPoint[] currPointList = p_point_lists[j];
      for (int i = 0; i < currPointList.length; i++) {
        Point2D currCorner = coordinateTransform.board_to_screen(currPointList[i]);
        drawPolygon.addPoint(
            (int) Math.round(currCorner.getX()), (int) Math.round(currCorner.getY()));
      }
      drawPath.append(drawPolygon, false);
    }
    Graphics2D g2 = (Graphics2D) p_g;
    g2.setColor(p_color);
    set_translucency(g2, p_translucency_factor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.fill(drawPath);
  }

  /** draws the interior of an item of class geometry.planar.Area */
  public void fill_area(Area p_area, Graphics p_g, Color p_color, double p_translucency_factor) {
    if (p_color == null || p_area.is_empty()) {
      return;
    }
    if (p_area instanceof Circle circle) {
      fill_circle(circle, p_g, p_color, p_translucency_factor);
    } else {
      PolylineShape border = (PolylineShape) p_area.get_border();
      if (!border.is_bounded()) {
        FRLogger.warn("GraphicsContext.fill_area: shape not bounded");
        return;
      }
      Rectangle clipShape = p_g.getClip().getBounds();
      IntBox clipBox = coordinateTransform.screen_to_board(clipShape);
      if (!border.bounding_box().intersects(clipBox)) {
        return;
      }
      Shape[] holes = p_area.get_holes();

      FloatPoint[][] drawPolygons = new FloatPoint[holes.length + 1][];
      for (int j = 0; j < drawPolygons.length; j++) {
        PolylineShape currDrawShape;
        if (j == 0) {
          currDrawShape = border;
        } else {
          currDrawShape = (PolylineShape) holes[j - 1];
        }
        drawPolygons[j] = new FloatPoint[currDrawShape.border_line_count() + 1];
        FloatPoint[] currDrawPolygon = drawPolygons[j];
        for (int i = 0; i < currDrawPolygon.length - 1; i++) {
          currDrawPolygon[i] = currDrawShape.corner_approx(i);
        }
        // close the polygon
        currDrawPolygon[currDrawPolygon.length - 1] = currDrawPolygon[0];
      }
      fill_area(drawPolygons, p_g, p_color, p_translucency_factor);
    }
    if (show_area_division) {
      TileShape[] tiles = p_area.split_to_convex();
      for (int i = 0; i < tiles.length; i++) {
        FloatPoint[] corners = new FloatPoint[tiles[i].border_line_count() + 1];
        TileShape currTile = tiles[i];
        for (int j = 0; j < corners.length - 1; j++) {
          corners[j] = currTile.corner_approx(j);
        }
        corners[corners.length - 1] = corners[0];
        draw(corners, 1, Color.white, p_g, 0.7);
      }
    }
  }

  public Color get_background_color() {
    return otherColorTable.get_background_color();
  }

  public Color get_hilight_color() {
    return otherColorTable.get_hilight_color();
  }

  public Color get_incomplete_color() {
    return otherColorTable.get_incomplete_color();
  }

  public Color get_outline_color() {
    return otherColorTable.get_outline_color();
  }

  public Color get_component_color(boolean p_front) {
    return otherColorTable.get_component_color(p_front);
  }

  public Color get_violations_color() {
    return otherColorTable.get_violations_color();
  }

  public Color get_length_matching_area_color() {
    return otherColorTable.get_length_matching_area_color();
  }

  public Color[] get_trace_colors(boolean p_fixed) {

    return itemColorTable.get_trace_colors(p_fixed);
  }

  public Color[] get_via_colors(boolean p_fixed) {
    return itemColorTable.get_via_colors(p_fixed);
  }

  public Color[] get_pin_colors() {
    return itemColorTable.get_pin_colors();
  }

  public Color[] get_conduction_colors() {
    return itemColorTable.get_conduction_colors();
  }

  public Color[] get_obstacle_colors() {
    return itemColorTable.get_obstacle_colors();
  }

  public Color[] get_via_obstacle_colors() {
    return itemColorTable.get_via_obstacle_colors();
  }

  public Color[] get_place_obstacle_colors() {
    return itemColorTable.get_place_obstacle_colors();
  }

  public double get_trace_color_intensity() {
    return colorIntensityTable.get_value(ColorIntensityTable.ObjectNames.TRACES.ordinal());
  }

  public void set_trace_color_intensity(double p_value) {
    colorIntensityTable.set_value(ColorIntensityTable.ObjectNames.TRACES.ordinal(), p_value);
  }

  public double get_via_color_intensity() {
    return colorIntensityTable.get_value(ColorIntensityTable.ObjectNames.VIAS.ordinal());
  }

  public void set_via_color_intensity(double p_value) {
    colorIntensityTable.set_value(ColorIntensityTable.ObjectNames.VIAS.ordinal(), p_value);
  }

  public double get_pin_color_intensity() {
    return colorIntensityTable.get_value(ColorIntensityTable.ObjectNames.PINS.ordinal());
  }

  public void set_pin_color_intensity(double p_value) {
    colorIntensityTable.set_value(ColorIntensityTable.ObjectNames.PINS.ordinal(), p_value);
  }

  public double get_conduction_color_intensity() {
    return colorIntensityTable.get_value(
        ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal());
  }

  public void set_conduction_color_intensity(double p_value) {
    colorIntensityTable.set_value(
        ColorIntensityTable.ObjectNames.CONDUCTION_AREAS.ordinal(), p_value);
  }

  public double get_obstacle_color_intensity() {
    return colorIntensityTable.get_value(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal());
  }

  public void set_obstacle_color_intensity(double p_value) {
    colorIntensityTable.set_value(ColorIntensityTable.ObjectNames.KEEPOUTS.ordinal(), p_value);
  }

  public double get_via_obstacle_color_intensity() {
    return colorIntensityTable.get_value(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal());
  }

  public void set_via_obstacle_color_intensity(double p_value) {
    colorIntensityTable.set_value(ColorIntensityTable.ObjectNames.VIA_KEEPOUTS.ordinal(), p_value);
  }

  public double get_place_obstacle_color_intensity() {
    return colorIntensityTable.get_value(ColorIntensityTable.ObjectNames.PLACE_KEEPOUTS.ordinal());
  }

  public double get_component_outline_color_intensity() {
    return colorIntensityTable.get_value(
        ColorIntensityTable.ObjectNames.COMPONENT_OUTLINES.ordinal());
  }

  public double get_hilight_color_intensity() {
    return colorIntensityTable.get_value(ColorIntensityTable.ObjectNames.HILIGHT.ordinal());
  }

  public void set_hilight_color_intensity(double p_value) {
    colorIntensityTable.set_value(ColorIntensityTable.ObjectNames.HILIGHT.ordinal(), p_value);
  }

  public double get_incomplete_color_intensity() {
    return colorIntensityTable.get_value(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal());
  }

  public void set_incomplete_color_intensity(double p_value) {
    colorIntensityTable.set_value(ColorIntensityTable.ObjectNames.INCOMPLETES.ordinal(), p_value);
  }

  public double get_length_matching_area_color_intensity() {
    return colorIntensityTable.get_value(
        ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal());
  }

  public void set_length_matching_area_color_intensity(double p_value) {
    colorIntensityTable.set_value(
        ColorIntensityTable.ObjectNames.LENGTH_MATCHING_AREAS.ordinal(), p_value);
  }

  public Dimension get_panel_size() {
    return coordinateTransform.screenBounds;
  }

  /** Returns the center of the design on the screen. */
  public Point2D get_design_center() {
    FloatPoint center = coordinateTransform.designBoxWithOffset.centre_of_gravity();
    return coordinateTransform.board_to_screen(center);
  }

  /** Returns the bounding box of the design in screen coordinates. */
  public Rectangle get_design_bounds() {
    return coordinateTransform.board_to_screen(coordinateTransform.designBox);
  }

  /** gets the factor for automatic layer dimming */
  public double get_auto_layer_dim_factor() {
    return this.autoLayerDimFactor;
  }

  /**
   * Sets the factor for automatic layer dimming. Values are between 0 and 1. If 1, there is no
   * automatic layer dimming.
   */
  public void set_auto_layer_dim_factor(double p_value) {
    autoLayerDimFactor = p_value;
  }

  /** Sets the layer, which will be excluded from automatic layer dimming. */
  public void set_fully_visible_layer(int p_layer_no) {
    fullyVisibleLayer = p_layer_no;
    if (p_layer_no != -1) {
      fullyVisibleVirtualLayer = -1;
    }
  }

  public int get_fully_visible_layer() {
    return fullyVisibleLayer;
  }

  public void setSimplifiedPlaneRendering(boolean simplifiedPlaneRendering) {
    this.simplifiedPlaneRendering = simplifiedPlaneRendering;
  }

  public boolean isSimplifiedPlaneRendering() {
    return simplifiedPlaneRendering;
  }

  public int get_fully_visible_virtual_layer() {
    boolean[] visibilityArr = get_virtual_layer_visibility_arr();
    if (fullyVisibleVirtualLayer < -1 || fullyVisibleVirtualLayer >= visibilityArr.length) {
      return -1;
    }
    return fullyVisibleVirtualLayer;
  }

  public boolean is_front_selected() {
    int selectedVirtualLayer = get_fully_visible_virtual_layer();
    if (selectedVirtualLayer != -1) {
      return selectedVirtualLayer % 2 == 0;
    }
    if (fullyVisibleLayer != -1) {
      return fullyVisibleLayer < layerVisibilityArr.length / 2;
    }
    return true;
  }

  public boolean get_virtual_layer_visible(int idx) {
    boolean[] visibilityArr = get_virtual_layer_visibility_arr();
    if (idx >= 0 && idx < visibilityArr.length) {
      return visibilityArr[idx];
    }
    return true;
  }

  public void set_virtual_layer_visible(int idx, boolean visible) {
    boolean[] visibilityArr = get_virtual_layer_visibility_arr();
    if (idx >= 0 && idx < visibilityArr.length) {
      visibilityArr[idx] = visible;
    }
  }

  public void set_fully_visible_virtual_layer(int idx) {
    boolean[] visibilityArr = get_virtual_layer_visibility_arr();
    if (idx < -1 || idx >= visibilityArr.length) {
      idx = -1;
    }
    fullyVisibleVirtualLayer = idx;
    if (idx != -1) {
      fullyVisibleLayer = -1;
    }
  }

  public double get_virtual_layer_visibility(int virtual_layer_idx) {
    boolean[] visibilityArr = get_virtual_layer_visibility_arr();
    if (virtual_layer_idx < 0 || virtual_layer_idx >= visibilityArr.length) {
      return 1.0;
    }
    if (!visibilityArr[virtual_layer_idx]) {
      return 0.0;
    }
    if (fullyVisibleLayer != -1) {
      return this.autoLayerDimFactor;
    }
    int selectedVirtualLayer = get_fully_visible_virtual_layer();
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
  public double get_layer_visibility(int p_layer_no) {
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
  public double get_raw_layer_visibility(int p_layer_no) {
    return layerVisibilityArr[p_layer_no];
  }

  /**
   * Gets the visibility factor of the input layer. The value is expected between 0 and 1. If the
   * value is 0, the layer is invisible, if the value is 1, the layer is fully visible.
   */
  public void set_layer_visibility(int p_layer_no, double p_value) {
    layerVisibilityArr[p_layer_no] = Math.max(0, Math.min(p_value, 1));
  }

  public void set_layer_visibility_arr(double[] p_layer_visibility_arr) {
    this.layerVisibilityArr = p_layer_visibility_arr;
  }

  public double[] copy_layer_visibility_arr() {
    double[] result = new double[this.layerVisibilityArr.length];
    System.arraycopy(this.layerVisibilityArr, 0, result, 0, this.layerVisibilityArr.length);
    return result;
  }

  /** Returns the number of layers on the board */
  public int layer_count() {
    return layerVisibilityArr.length;
  }

  /**
   * filter lines, which cannot touch the updateBox to improve the performance of the draw function
   * by avoiding unnecessary calls of draw (line)
   */
  private boolean line_outside_update_box(
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
    itemColorTable.write_object(p_stream);
    otherColorTable.write_object(p_stream);
  }

  /** Reads an instance of this class from a file */
  private void readObject(ObjectInputStream p_stream) throws IOException, ClassNotFoundException {
    p_stream.defaultReadObject();
    if (virtualLayerVisibilityArr == null
        || virtualLayerVisibilityArr.length != virtual_layer_count) {
      virtualLayerVisibilityArr = create_default_virtual_layer_visibility_arr();
    }
    fullyVisibleVirtualLayer = get_fully_visible_virtual_layer();
    this.itemColorTable = new ItemColorTableModel(p_stream);
    this.otherColorTable = new OtherColorTableModel(p_stream);
  }
}

package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * A ObstacleArea, which can be electrically connected to other items.
 */
public class ConductionArea extends ObstacleArea implements Connectable {

  private static final double PLANE_FILL_SCALE = 2.5;
  private static final double PLANE_HATCH_OPACITY = 0.85;

  private boolean is_obstacle;
  private boolean is_filled = true;

  public boolean get_is_filled() {
    return this.is_filled;
  }

  public void set_is_filled(boolean p_value) {
    this.is_filled = p_value;
    this.clear_derived_data();
  }

  private transient java.awt.geom.Area cached_fill_area = null;
  private transient app.freerouting.boardgraphics.CoordinateTransform cached_fill_transform = null;
  private transient int cached_board_revision = -1;
  private transient java.awt.geom.Area cached_board_fill_area = null;

  /**
   * Creates a new instance of ConductionArea
   */
  ConductionArea(Area p_area, int p_layer, Vector p_translation, double p_rotation_in_degree, boolean p_side_changed, int[] p_net_no_arr, int p_clearance_class, int p_id_no, int p_group_no,
      String p_name, boolean p_is_obstacle, FixedState p_fixed_state, BasicBoard p_board) {
    super(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed, p_net_no_arr, p_clearance_class, p_id_no, p_group_no, p_name, p_fixed_state, p_board);
    is_obstacle = p_is_obstacle;
  }

  @Override
  public void clear_derived_data() {
    super.clear_derived_data();
    this.cached_fill_area = null;
    this.cached_fill_transform = null;
    this.cached_board_revision = -1;
    this.cached_board_fill_area = null;
  }

  @Override
  public void draw(Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    int layerNo = this.get_layer();
    double layerVis = p_graphics_context.get_layer_visibility(layerNo);
    if (layerVis <= 0) {
      return;
    }

    Color color = p_color_arr[layerNo];
    if (this.is_filled) {
      double fillOpacity = Math.min(layerVis * p_intensity * PLANE_FILL_SCALE, 1.0);

      double maxClearanceLookupBoard = 2000.0 * this.board.communication.get_resolution(Unit.UM);
      if (this.board.rules != null && this.board.rules.clearance_matrix != null) {
        double maxMatrixClearance = this.board.rules.clearance_matrix.max_value(this.clearance_class_no(), layerNo);
        maxClearanceLookupBoard = Math.max(maxClearanceLookupBoard, maxMatrixClearance + 100.0 * this.board.communication.get_resolution(Unit.UM));
      }
      boolean zoomedOut = p_graphics_context.coordinate_transform.board_to_screen(maxClearanceLookupBoard) < 1.5;

      if (zoomedOut) {
        p_graphics_context.fill_area(this.get_area(), p_g, color, fillOpacity);
      } else {
        boolean boardChanged = this.board.get_revision() != cached_board_revision;

        if (cached_board_fill_area == null || boardChanged) {
          java.awt.geom.Area fillArea = get_awt_area_in_board_units(this.get_area());
          if (fillArea == null) {
            fillArea = new java.awt.geom.Area();
          }
          if (!fillArea.isEmpty()) {
            // Bounding box of conduction area
            IntBox bbox = this.bounding_box();
            double spokeWidth = 400.0 * this.board.communication.get_resolution(Unit.UM);
            int maxCl = (int) Math.round(maxClearanceLookupBoard);
            IntBox inflatedBbox = new IntBox(
                new IntPoint(bbox.ll.x - maxCl, bbox.ll.y - maxCl),
                new IntPoint(bbox.ur.x + maxCl, bbox.ur.y + maxCl)
            );

            // Gather items
            java.util.List<java.awt.geom.Area> foreignClearances = new java.util.ArrayList<>();
            java.util.List<java.awt.geom.Area> sameNetClearances = new java.util.ArrayList<>();
            java.util.List<java.awt.geom.Area> sameNetSpokesList = new java.util.ArrayList<>();

            Set<SearchTreeObject> overlaps = this.board.overlapping_objects(inflatedBbox, layerNo);
            for (SearchTreeObject ob : overlaps) {
              if (!(ob instanceof Item currItem) || currItem == this) {
                continue;
              }
              if (!currItem.shares_layer(this)) {
                continue;
              }

              // Skip traces and conduction areas for same-net (direct connection)
              if (currItem instanceof Trace || currItem instanceof ConductionArea) {
                if (currItem.shares_net(this)) {
                  continue;
                }
              }

              int clClass1 = this.clearance_class_no();
              int clClass2 = currItem.clearance_class_no();
              double clearanceDist = this.board.clearance_value(clClass1, clClass2, layerNo);

              if (currItem.shares_net(this)) {
                if (currItem instanceof DrillItem drillItem) {
                  FloatPoint center = drillItem.get_center().to_float();
                  Shape shape = drillItem.get_shape_on_layer(layerNo);
                  if (shape == null) {
                    continue;
                  }

                  Shape enlargedShape = shape.enlarge(clearanceDist);
                  java.awt.geom.Area clearanceAwt = get_awt_area_from_shape_in_board_units(enlargedShape);
                  if (clearanceAwt == null) {
                    continue;
                  }

                  IntBox itemBbox = drillItem.bounding_box();
                  double maxDim = Math.max(itemBbox.width(), itemBbox.height());
                  double expansionRadiusBoard = (maxDim / 2.0) + clearanceDist;

                  double halfSpoke = spokeWidth / 2.0;
                  java.awt.geom.Rectangle2D.Double baseSpoke = new java.awt.geom.Rectangle2D.Double(center.x - halfSpoke, center.y - expansionRadiusBoard, spokeWidth, 2 * expansionRadiusBoard);

                  java.awt.geom.AffineTransform rotP45 = java.awt.geom.AffineTransform.getRotateInstance(Math.PI / 4.0, center.x, center.y);
                  java.awt.geom.AffineTransform rotM45 = java.awt.geom.AffineTransform.getRotateInstance(-Math.PI / 4.0, center.x, center.y);

                  java.awt.geom.Area spokes = new java.awt.geom.Area(rotP45.createTransformedShape(baseSpoke));
                  spokes.add(new java.awt.geom.Area(rotM45.createTransformedShape(baseSpoke)));

                  spokes.intersect(clearanceAwt);

                  sameNetClearances.add(clearanceAwt);
                  sameNetSpokesList.add(spokes);
                }
              } else {
                // Foreign-net: clearance gap
                if (currItem instanceof DrillItem drillItem) {
                  Shape shape = drillItem.get_shape_on_layer(layerNo);
                  if (shape != null) {
                    Shape enlargedShape = shape.enlarge(clearanceDist);
                    java.awt.geom.Area clearanceAwt = get_awt_area_from_shape_in_board_units(enlargedShape);
                    if (clearanceAwt != null) {
                      foreignClearances.add(clearanceAwt);
                    }
                  }
                } else {
                  int shapeCount = currItem.tile_shape_count();
                  for (int i = 0; i < shapeCount; i++) {
                    if (currItem.shape_layer(i) == layerNo) {
                      TileShape tileShape = currItem.get_tile_shape(i);
                      if (tileShape != null) {
                        Shape enlargedShape = tileShape.enlarge(clearanceDist);
                        java.awt.geom.Area clearanceAwt = get_awt_area_from_shape_in_board_units(enlargedShape);
                        if (clearanceAwt != null) {
                          foreignClearances.add(clearanceAwt);
                        }
                      }
                    }
                  }
                }
              }
            }

            // Apply CSG
            for (java.awt.geom.Area fa : foreignClearances) {
              fillArea.subtract(fa);
            }
            for (java.awt.geom.Area sa : sameNetClearances) {
              fillArea.subtract(sa);
            }
            for (java.awt.geom.Area sp : sameNetSpokesList) {
              fillArea.add(sp);
            }
          }
          cached_board_fill_area = fillArea;
          cached_board_revision = this.board.get_revision();
        }

        if (cached_board_fill_area != null && !cached_board_fill_area.isEmpty()) {
          Point2D p0 = p_graphics_context.coordinate_transform.board_to_screen(FloatPoint.ZERO);
          Point2D px = p_graphics_context.coordinate_transform.board_to_screen(new FloatPoint(1, 0));
          Point2D py = p_graphics_context.coordinate_transform.board_to_screen(new FloatPoint(0, 1));
          
          double m00 = px.getX() - p0.getX();
          double m10 = px.getY() - p0.getY();
          double m01 = py.getX() - p0.getX();
          double m11 = py.getY() - p0.getY();
          double m02 = p0.getX();
          double m12 = p0.getY();
          
          java.awt.geom.AffineTransform boardToScreen = new java.awt.geom.AffineTransform(m00, m10, m01, m11, m02, m12);
          java.awt.geom.Area screenArea = cached_board_fill_area.createTransformedArea(boardToScreen);

          java.awt.Graphics2D g2 = (java.awt.Graphics2D) p_g;
          java.awt.Paint oldPaint = g2.getPaint();
          java.awt.Composite oldComposite = g2.getComposite();

          g2.setColor(color);
          g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) fillOpacity));
          g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
          g2.fill(screenArea);

          g2.setPaint(oldPaint);
          g2.setComposite(oldComposite);
        }
      }
    }

    // Hatch border (0.5 mm in board units)
    double hatchPitch = 500.0 * this.board.communication.get_resolution(Unit.UM);
    p_graphics_context.draw_plane_hatch(this.get_area(), p_g, color, layerVis * p_intensity * PLANE_HATCH_OPACITY, hatchPitch);

    // Border outline
    p_graphics_context.draw_boundary(this.get_area(), 0.0, color, p_g, layerVis);
  }

  private static java.awt.geom.Area get_awt_area_in_board_units(Area p_area) {
    if (p_area == null || p_area.is_empty()) {
      return null;
    }
    if (p_area instanceof app.freerouting.geometry.planar.Circle circle) {
      double radius = circle.radius;
      double diameter = 2 * radius;
      return new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(circle.center.x - radius, circle.center.y - radius, diameter, diameter));
    }

    Shape borderShape = p_area.get_border();
    if (!(borderShape instanceof app.freerouting.geometry.planar.PolylineShape border) || !border.is_bounded()) {
      return null;
    }
    
    java.awt.geom.Path2D.Double borderPath = new java.awt.geom.Path2D.Double();
    int count = border.border_line_count();
    if (count > 0) {
      FloatPoint p0 = border.corner_approx(0);
      borderPath.moveTo(p0.x, p0.y);
      for (int i = 1; i < count; i++) {
        FloatPoint pi = border.corner_approx(i);
        borderPath.lineTo(pi.x, pi.y);
      }
      borderPath.closePath();
    }
    java.awt.geom.Area awtArea = new java.awt.geom.Area(borderPath);

    Shape[] holes = p_area.get_holes();
    for (Shape hole : holes) {
      java.awt.geom.Area holeArea = get_awt_area_from_shape_in_board_units(hole);
      if (holeArea != null) {
        awtArea.subtract(holeArea);
      }
    }
    return awtArea;
  }

  private static java.awt.geom.Area get_awt_area_from_shape_in_board_units(Shape p_shape) {
    if (p_shape == null) {
      return null;
    }
    if (p_shape instanceof app.freerouting.geometry.planar.Circle circle) {
      double radius = circle.radius;
      double diameter = 2 * radius;
      return new java.awt.geom.Area(new java.awt.geom.Ellipse2D.Double(circle.center.x - radius, circle.center.y - radius, diameter, diameter));
    }
    if (p_shape instanceof IntBox box) {
      return new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(box.ll.x, box.ll.y, box.width(), box.height()));
    }
    if (p_shape instanceof app.freerouting.geometry.planar.PolylineShape poly) {
      java.awt.geom.Path2D.Double path = new java.awt.geom.Path2D.Double();
      int count = poly.border_line_count();
      if (count > 0) {
        FloatPoint p0 = poly.corner_approx(0);
        path.moveTo(p0.x, p0.y);
        for (int i = 1; i < count; i++) {
          FloatPoint pi = poly.corner_approx(i);
          path.lineTo(pi.x, pi.y);
        }
        path.closePath();
      }
      return new java.awt.geom.Area(path);
    }
    return null;
  }

  @Override
  public Item copy(int p_id_no) {
    if (this.net_count() != 1) {
      FRLogger.warn("ConductionArea.copy not yet implemented for areas with more than 1 net");
      return null;
    }
    return new ConductionArea(get_relative_area(), get_layer(), get_translation(), get_rotation_in_degree(), get_side_changed(), net_no_arr, clearance_class_no(), p_id_no, get_component_no(),
        this.name, is_obstacle, get_fixed_state(), board);
  }

  @Override
  public Set<Item> get_normal_contacts() {
    Set<Item> result = new TreeSet<>();
    for (int i = 0; i < tile_shape_count(); i++) {
      TileShape curr_shape = get_tile_shape(i);
      Set<SearchTreeObject> overlaps = board.overlapping_objects(curr_shape, get_layer());
      for (SearchTreeObject curr_ob : overlaps) {
        if (!(curr_ob instanceof Item curr_item)) {
          continue;
        }
        if (curr_item != this && curr_item.shares_net(this) && curr_item.shares_layer(this)) {
          if (curr_item instanceof Trace curr_trace) {
            if (curr_shape.contains(curr_trace.first_corner()) || curr_shape.contains(curr_trace.last_corner())) {
              result.add(curr_item);
            }
          } else if (curr_item instanceof DrillItem curr_drill_item) {
            if (curr_shape.contains(curr_drill_item.get_center())) {
              result.add(curr_item);
            }
          }
        }
      }
    }
    return result;
  }

  @Override
  public TileShape get_trace_connection_shape(ShapeSearchTree p_search_tree, int p_index) {
    if (p_index < 0 || p_index >= this.tree_shape_count(p_search_tree)) {
      FRLogger.warn("ConductionArea.get_trace_connection_shape p_index out of range");
      return null;
    }
    return this.get_tree_shape(p_search_tree, p_index);
  }

  @Override
  public Point[] get_ratsnest_corners() {
    Point[] result;
    FloatPoint[] corners = this.get_area().corner_approx_arr();
    result = new Point[corners.length];
    for (int i = 0; i < corners.length; i++) {
      result[i] = corners[i].round();
    }

    return result;
  }

  @Override
  public boolean is_obstacle(Item p_other) {
    if (this.is_obstacle) {
      return super.is_obstacle(p_other);
    }
    return false;
  }

  /**
   * Returns if this conduction area is regarded as obstacle to traces of foreign nets.
   */
  public boolean get_is_obstacle() {
    return this.is_obstacle;
  }

  /**
   * Sets, if this conduction area is regarded as obstacle to traces and vias of foreign nets.
   */
  public void set_is_obstacle(boolean p_value) {
    this.is_obstacle = p_value;
  }

  @Override
  public boolean is_trace_obstacle(int p_net_no) {
    return this.is_obstacle && !this.contains_net(p_net_no);
  }

  @Override
  public boolean is_drillable(int p_net_no) {
    return !this.is_obstacle || this.contains_net(p_net_no);
  }

  @Override
  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.CONDUCTION);
  }

  @Override
  public Color[] get_draw_colors(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_trace_colors(true);
  }

  @Override
  public double get_draw_intensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_conduction_color_intensity();
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("conduction_area"));
    this.print_shape_info(p_window, p_locale);
    this.print_connectable_item_info(p_window, p_locale);
    p_window.newline();
  }
}
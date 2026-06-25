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

  private transient java.awt.geom.Area cached_fill_area = null;
  private transient app.freerouting.boardgraphics.CoordinateTransform cached_fill_transform = null;
  private transient int cached_board_revision = -1;

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
      boolean canTranslate = !boardChanged && cached_fill_area != null && cached_fill_transform != null
          && p_graphics_context.coordinate_transform.is_zoom_invariant_state_equal(cached_fill_transform);

      if (canTranslate && !p_graphics_context.coordinate_transform.is_same_transform_state(cached_fill_transform)) {
        Point2D currentZero = p_graphics_context.coordinate_transform.board_to_screen(FloatPoint.ZERO);
        Point2D cachedZero = cached_fill_transform.board_to_screen(FloatPoint.ZERO);
        double tx = currentZero.getX() - cachedZero.getX();
        double ty = currentZero.getY() - cachedZero.getY();
        if (tx != 0 || ty != 0) {
          cached_fill_area.transform(java.awt.geom.AffineTransform.getTranslateInstance(tx, ty));
        }
        cached_fill_transform = new app.freerouting.boardgraphics.CoordinateTransform(p_graphics_context.coordinate_transform);
      }

      if (cached_fill_area == null || cached_fill_transform == null || boardChanged || !p_graphics_context.coordinate_transform.is_same_transform_state(cached_fill_transform)) {

        java.awt.geom.Area fillArea = p_graphics_context.get_awt_area(this.get_area());
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
                Point center = drillItem.get_center();
                Point2D centerPx = p_graphics_context.coordinate_transform.board_to_screen(center.to_float());

                Shape shape = drillItem.get_shape_on_layer(layerNo);
                if (shape == null) {
                  continue;
                }

                java.awt.geom.Area shapeAwt = p_graphics_context.get_awt_area_from_shape(shape);
                if (shapeAwt == null) {
                  continue;
                }

                double clearancePx = p_graphics_context.coordinate_transform.board_to_screen(clearanceDist);
                java.awt.geom.Area clearanceAwt = new java.awt.geom.Area(shapeAwt);
                if (clearancePx > 0) {
                  clearanceAwt.add(new java.awt.geom.Area(new BasicStroke((float)(2 * clearancePx), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(shapeAwt)));
                }

                IntBox itemBbox = drillItem.bounding_box();
                double maxDim = Math.max(itemBbox.width(), itemBbox.height());
                double expansionRadiusBoard = (maxDim / 2.0) + clearanceDist;
                double expansionRadiusPx = p_graphics_context.coordinate_transform.board_to_screen(expansionRadiusBoard);
                double spokeWidthPx = p_graphics_context.coordinate_transform.board_to_screen(spokeWidth);

                double halfSpoke = spokeWidthPx / 2.0;
                java.awt.geom.Rectangle2D.Double baseSpoke = new java.awt.geom.Rectangle2D.Double(centerPx.getX() - halfSpoke, centerPx.getY() - expansionRadiusPx, spokeWidthPx, 2 * expansionRadiusPx);

                java.awt.geom.AffineTransform rotP45 = java.awt.geom.AffineTransform.getRotateInstance(Math.PI / 4.0, centerPx.getX(), centerPx.getY());
                java.awt.geom.AffineTransform rotM45 = java.awt.geom.AffineTransform.getRotateInstance(-Math.PI / 4.0, centerPx.getX(), centerPx.getY());

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
                  java.awt.geom.Area shapeAwt = p_graphics_context.get_awt_area_from_shape(shape);
                  if (shapeAwt != null) {
                    double clearancePx = p_graphics_context.coordinate_transform.board_to_screen(clearanceDist);
                    java.awt.geom.Area clearanceAwt = new java.awt.geom.Area(shapeAwt);
                    if (clearancePx > 0) {
                      clearanceAwt.add(new java.awt.geom.Area(new BasicStroke((float)(2 * clearancePx), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(shapeAwt)));
                    }
                    foreignClearances.add(clearanceAwt);
                  }
                }
              } else {
                int shapeCount = currItem.tile_shape_count();
                for (int i = 0; i < shapeCount; i++) {
                  if (currItem.shape_layer(i) == layerNo) {
                    TileShape tileShape = currItem.get_tile_shape(i);
                    if (tileShape != null) {
                      java.awt.geom.Area shapeAwt = p_graphics_context.get_awt_area_from_shape(tileShape);
                      if (shapeAwt != null) {
                        double clearancePx = p_graphics_context.coordinate_transform.board_to_screen(clearanceDist);
                        java.awt.geom.Area clearanceAwt = new java.awt.geom.Area(shapeAwt);
                        if (clearancePx > 0) {
                          clearanceAwt.add(new java.awt.geom.Area(new BasicStroke((float)(2 * clearancePx), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(shapeAwt)));
                        }
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
        cached_fill_area = fillArea;
        cached_fill_transform = new app.freerouting.boardgraphics.CoordinateTransform(p_graphics_context.coordinate_transform);
        cached_board_revision = this.board.get_revision();
      }

      if (cached_fill_area != null && !cached_fill_area.isEmpty()) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) p_g;
        java.awt.Paint oldPaint = g2.getPaint();
        java.awt.Composite oldComposite = g2.getComposite();

        g2.setColor(color);
        g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) fillOpacity));
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.fill(cached_fill_area);

        g2.setPaint(oldPaint);
        g2.setComposite(oldComposite);
      }
    }

    // Hatch border (0.5 mm in board units)
    double hatchPitch = 500.0 * this.board.communication.get_resolution(Unit.UM);
    p_graphics_context.draw_plane_hatch(this.get_area(), p_g, color, layerVis * p_intensity * PLANE_HATCH_OPACITY, hatchPitch);

    // Border outline
    p_graphics_context.draw_boundary(this.get_area(), 0.0, color, p_g, layerVis);
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
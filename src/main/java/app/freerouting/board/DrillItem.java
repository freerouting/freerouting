package app.freerouting.board;

import app.freerouting.boardgraphics.ColorIntensityTable;
import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.core.Padstack;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.awt.Color;
import java.awt.Graphics;
import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;

/** Common superclass for Pins and Vias */
public abstract class DrillItem extends Item implements Connectable, Serializable {

  /** The center point of the drillitem */
  private Point center;

  /**
   * Contains the precalculated minimal width of the shapes of this DrillItem on all layers. If
   * {@literal <} 0, the value is not yet calculated
   */
  private double precalculatedMinWidth = -1;

  /**
   * Contains the precalculated first layer, where this DrillItem contains a pad shape. If {@literal
   * <} 0, the value is not yet calculated
   */
  private int precalculatedFirstLayer = -1;

  /**
   * Contains the precalculated last layer, where this DrillItem contains a pad shape. If {@literal
   * <} 0, the value is not yet calculated
   */
  private int precalculatedLastLayer = -1;

  protected DrillItem(
      Point p_center,
      int[] p_net_no_arr,
      int p_clearance_type,
      int p_id_no,
      int p_group_no,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(p_net_no_arr, p_clearance_type, p_id_no, p_group_no, p_fixed_state, p_board);
    this.center = p_center;
  }

  /** Works only for symmetric DrillItems */
  @Override
  public void translate_by(Vector p_vector) {
    if (center != null) {
      center = center.translate_by(p_vector);
    }
    this.clear_derived_data();
  }

  @Override
  public void turn_90_degree(int p_factor, IntPoint p_pole) {
    if (center != null) {
      center = center.turn_90_degree(p_factor, p_pole);
    }
    this.clear_derived_data();
  }

  @Override
  public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole) {
    if (center != null) {
      FloatPoint newCenter = center.to_float().rotate(Math.toRadians(p_angle_in_degree), p_pole);
      this.center = newCenter.round();
    }
    this.clear_derived_data();
  }

  @Override
  public void change_placement_side(IntPoint p_pole) {
    if (center != null) {
      center = center.mirror_vertical(p_pole);
    }
    this.clear_derived_data();
  }

  @Override
  public void move_by(Vector p_vector) {
    Point oldCenter = this.get_center();
    // remember the contact situation of this drillitem to traces on each layer
    Set<TraceInfo> contactTraceInfo = new TreeSet<>();
    Collection<Item> contacts = this.get_normal_contacts();
    for (Item currContact : contacts) {
      if (currContact instanceof Trace currTrace) {
        TraceInfo currTraceInfo =
            new TraceInfo(
                currTrace.get_layer(), currTrace.get_half_width(), currTrace.clearance_class_no());
        contactTraceInfo.add(currTraceInfo);
      }
    }
    super.move_by(p_vector);

    // Insert a Trace from the old center to the new center, on all layers, where
    // this DrillItem was connected to a Trace.
    Collection<Point> connectPointList = new LinkedList<>();
    connectPointList.add(oldCenter);
    Point newCenter = this.get_center();
    IntPoint addCorner = null;
    if (oldCenter instanceof IntPoint point && newCenter instanceof IntPoint point1) {
      // Make sure, that the traces will remain 90- or 45-degree.
      if (board.rules.get_trace_angle_restriction() == AngleRestriction.NINETY_DEGREE) {
        addCorner = point.ninety_degree_corner(point1, true);
      } else if (board.rules.get_trace_angle_restriction() == AngleRestriction.FORTYFIVE_DEGREE) {
        addCorner = point.fortyfive_degree_corner(point1, true);
      }
    }
    if (addCorner != null) {
      connectPointList.add(addCorner);
    }
    connectPointList.add(newCenter);
    Point[] connectPoints = new Point[connectPointList.size()];
    Iterator<Point> it3 = connectPointList.iterator();
    for (int i = 0; i < connectPoints.length; i++) {
      connectPoints[i] = it3.next();
    }
    for (TraceInfo currTraceInfo : contactTraceInfo) {
      board.insert_trace(
          connectPoints,
          currTraceInfo.layer,
          currTraceInfo.halfWidth,
          this.netNoArr,
          currTraceInfo.clearanceType,
          FixedState.UNFIXED);
    }
  }

  @Override
  public int shape_layer(int p_index) {
    int index = Math.max(p_index, 0);
    int fromLayer = first_layer();
    int toLayer = last_layer();
    index = Math.min(index, toLayer - fromLayer);
    return fromLayer + index;
  }

  @Override
  public boolean is_on_layer(int p_layer) {
    return p_layer >= first_layer() && p_layer <= last_layer();
  }

  @Override
  public int first_layer() {
    if (this.precalculatedFirstLayer < 0) {
      Padstack padstack = get_padstack();
      if (this.is_placed_on_front() || padstack.placedAbsolute) {
        this.precalculatedFirstLayer = padstack.from_layer();
      } else {
        this.precalculatedFirstLayer = padstack.board_layer_count() - padstack.to_layer() - 1;
      }
    }
    return this.precalculatedFirstLayer;
  }

  @Override
  public int last_layer() {
    if (this.precalculatedLastLayer < 0) {
      Padstack padstack = get_padstack();
      if (this.is_placed_on_front() || padstack.placedAbsolute) {
        this.precalculatedLastLayer = padstack.to_layer();
      } else {
        this.precalculatedLastLayer = padstack.board_layer_count() - padstack.from_layer() - 1;
      }
    }
    return this.precalculatedLastLayer;
  }

  public abstract Shape get_shape(int p_index);

  @Override
  public IntBox bounding_box() {
    IntBox result = IntBox.EMPTY;
    for (int i = 0; i < tile_shape_count(); i++) {
      Shape currShape = this.get_shape(i);
      if (currShape != null) {
        result = result.union(currShape.bounding_box());
      }
    }
    return result;
  }

  @Override
  public int tile_shape_count() {
    Padstack padstack = get_padstack();
    int fromLayer = padstack.from_layer();
    int toLayer = padstack.to_layer();
    return toLayer - fromLayer + 1;
  }

  @Override
  protected TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree) {
    return p_search_tree.calculate_tree_shapes(this);
  }

  /** Returns the smallest distance from the center to the border of the shape on any layer. */
  public double smallest_radius() {
    double result = Double.MAX_VALUE;
    FloatPoint c = get_center().to_float();
    for (int i = 0; i < tile_shape_count(); i++) {
      Shape currShape = get_shape(i);
      if (currShape != null) {
        result = Math.min(result, currShape.border_distance(c));
      }
    }
    return result;
  }

  /** Returns the center point of this DrillItem. */
  public Point get_center() {
    return center;
  }

  protected void set_center(Point p_center) {
    center = p_center;
  }

  /** Returns the padstack of this drillitem. */
  public abstract Padstack get_padstack();

  public TileShape get_tree_shape_on_layer(ShapeSearchTree p_tree, int p_layer) {
    int fromLayer = first_layer();
    int toLayer = last_layer();
    if (p_layer < fromLayer || p_layer > toLayer) {
      FRLogger.warn("DrillItem.get_tree_shape_on_layer: p_layer out of range");
      return null;
    }
    return get_tree_shape(p_tree, p_layer - fromLayer);
  }

  public TileShape get_tile_shape_on_layer(int p_layer) {
    int fromLayer = first_layer();
    int toLayer = last_layer();
    if (p_layer < fromLayer || p_layer > toLayer) {
      FRLogger.warn("DrillItem.get_tile_shape_on_layer: p_layer out of range");
      return null;
    }
    return get_tile_shape(p_layer - fromLayer);
  }

  public Shape get_shape_on_layer(int p_layer) {
    int fromLayer = first_layer();
    int toLayer = last_layer();
    if (p_layer < fromLayer || p_layer > toLayer) {
      FRLogger.warn("DrillItem.get_shape_on_layer: p_layer out of range");
      return null;
    }
    return get_shape(p_layer - fromLayer);
  }

  @Override
  public Set<Item> get_normal_contacts() {
    Point drillCenter = this.get_center();
    TileShape searchShape = TileShape.get_instance(drillCenter);
    Set<SearchTreeObject> overlaps = board.overlapping_objects(searchShape, -1);
    Set<Item> result = new TreeSet<>();
    for (SearchTreeObject currOb : overlaps) {
      if (!(currOb instanceof Item currItem)) {
        continue;
      }
      if (currItem != this && currItem.shares_net(this) && currItem.shares_layer(this)) {
        if (currItem instanceof Trace currTrace) {
          // Use exact matching to match trace endpoints to pin/via center.
          // Tolerance-based matching causes false cycle detection during trace normalization
          // when nearby trace endpoints (but not at pin center) are incorrectly treated as
          // contacts.
          if (drillCenter.equals(currTrace.first_corner())
              || drillCenter.equals(currTrace.last_corner())) {
            result.add(currItem);
          }
        } else if (currItem instanceof DrillItem curr_drill_item) {
          if (drillCenter.equals(curr_drill_item.get_center())) {
            result.add(currItem);
          }
        } else if (currItem instanceof ConductionArea currArea) {
          if (currArea.get_area().contains(drillCenter)) {
            result.add(currItem);
          }
        }
      }
    }
    return result;
  }

  /**
   * Checks if two points are within the specified tolerance distance. Uses Manhattan distance for
   * efficiency.
   */
  private boolean isWithinTolerance(Point p1, Point p2, int tolerance) {
    if (p1 == null || p2 == null) {
      return false;
    }
    // Convert to FloatPoint for distance calculation
    FloatPoint fp1 = p1.to_float();
    FloatPoint fp2 = p2.to_float();

    // Use Manhattan distance (|x1-x2| + |y1-y2|) which is faster than Euclidean
    // and sufficient for connectivity detection
    double dx = Math.abs(fp1.x - fp2.x);
    double dy = Math.abs(fp1.y - fp2.y);
    return (dx + dy) <= tolerance;
  }

  @Override
  public Point normal_contact_point(Item p_other) {
    return p_other.normal_contact_point(this);
  }

  @Override
  Point normal_contact_point(DrillItem p_other) {
    if (this.shares_layer(p_other) && this.get_center().equals(p_other.get_center())) {
      return this.get_center();
    }
    return null;
  }

  @Override
  Point normal_contact_point(Trace p_trace) {
    if (!this.shares_layer(p_trace)) {
      return null;
    }
    Point drillCenter = this.get_center();
    if (drillCenter.equals(p_trace.first_corner()) || drillCenter.equals(p_trace.last_corner())) {
      return drillCenter;
    }
    return null;
  }

  @Override
  public Point[] get_ratsnest_corners() {
    Point[] result = new Point[1];
    result[0] = this.get_center();
    return result;
  }

  @Override
  public TileShape get_trace_connection_shape(ShapeSearchTree p_search_tree, int p_index) {
    return TileShape.get_instance(this.get_center());
  }

  /** False, if this drillitem is places on the back side of the board */
  public boolean is_placed_on_front() {
    return true;
  }

  /** Return the minimal width of the shapes of this DrillItem on all signal layers. */
  public double min_width() {
    if (this.precalculatedMinWidth < 0) {
      double minWidth = Integer.MAX_VALUE;
      int beginLayer = this.first_layer();
      int endLayer = this.last_layer();
      for (int currLayer = beginLayer; currLayer <= endLayer; currLayer++) {
        if (this.board != null && !this.board.layerStructure.arr[currLayer].isSignal) {
          continue;
        }
        Shape currShape = this.get_shape_on_layer(currLayer);
        if (currShape != null) {
          IntBox currBoundingBox = currShape.bounding_box();
          minWidth = Math.min(minWidth, currBoundingBox.width());
          minWidth = Math.min(minWidth, currBoundingBox.height());
        }
      }
      this.precalculatedMinWidth = minWidth;
    }
    return this.precalculatedMinWidth;
  }

  @Override
  public void clear_derived_data() {
    super.clear_derived_data();
    this.precalculatedFirstLayer = -1;
    this.precalculatedLastLayer = -1;
  }

  @Override
  public int get_draw_priority() {
    return Drawable.MIDDLE_DRAW_PRIORITY;
  }

  @Override
  public void draw_layer(
      Graphics p_g,
      GraphicsContext p_graphics_context,
      Color[] p_color_arr,
      double p_intensity,
      int p_layer_no) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    int fromLayer = first_layer();
    int toLayer = last_layer();
    if (p_layer_no < fromLayer || p_layer_no > toLayer) {
      return;
    }

    // Determine if this is the last physical layer step drawn (for through-hole drill hole
    // rendering)
    boolean isLastPhysicalLayer = false;
    if (this instanceof Pin && fromLayer != toLayer) {
      int lastPhysicalLayer;
      int activeLayer = p_graphics_context.get_fully_visible_layer();
      if (activeLayer != -1) {
        lastPhysicalLayer = activeLayer;
      } else {
        int activeVirtual = p_graphics_context.get_fully_visible_virtual_layer();
        boolean isBack = false;
        if (activeVirtual != -1) {
          isBack =
              activeVirtual % 2
                  != 0; // odd indices are Back (B.Silkscreen=1, B.Courtyard=3, B.Fab=5)
        }
        int layerCount = board.get_layer_count();
        lastPhysicalLayer = isBack ? (layerCount - 1) : 0;
      }
      if (p_layer_no == lastPhysicalLayer) {
        isLastPhysicalLayer = true;
      }
    }

    double visibilityFactor = 0;
    for (int i = fromLayer; i <= toLayer; i++) {
      visibilityFactor += p_graphics_context.get_layer_visibility(i);
    }

    if (visibilityFactor >= 0.001) {
      double intensity = p_intensity;
      if (!(this instanceof Pin)) {
        intensity = p_intensity / Math.max(visibilityFactor, 1);
      }
      Shape currShape = this.get_shape(p_layer_no - fromLayer);
      if (currShape != null) {
        double layerVis = p_graphics_context.get_layer_visibility(p_layer_no);
        if (layerVis > 0.001) {
          Color color = p_color_arr[p_layer_no];
          double layerIntensity = this instanceof Pin ? intensity : intensity * layerVis;
          p_graphics_context.fill_area(currShape, p_g, color, layerIntensity);
        }
      }
    }

    // Render drill hole for through-hole pins only (not vias)
    if (isLastPhysicalLayer) {
      Padstack padstack = get_padstack();
      if (padstack != null) {
        double drillRadius = padstack.get_drill_radius();
        if (drillRadius > 0) {
          Color drillColor = p_graphics_context.otherColorTable.get_drill_hole_color();
          double drillIntensity =
              p_graphics_context.colorIntensityTable.get_value(
                  ColorIntensityTable.ObjectNames.DRILL_HOLES.ordinal());
          IntPoint centerPoint = get_center().to_float().round();
          Circle drillCircle = new Circle(centerPoint, (int) Math.round(drillRadius));
          p_graphics_context.fill_circle(drillCircle, p_g, drillColor, drillIntensity);
        }
      }
    }
  }

  @Override
  public void draw(
      Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    int fromLayer = first_layer();
    int toLayer = last_layer();
    // Decrease the drawing intensity for items with many layers.
    double visibilityFactor = 0;
    for (int i = fromLayer; i <= toLayer; i++) {
      visibilityFactor += p_graphics_context.get_layer_visibility(i);
    }

    if (visibilityFactor >= 0.001) {
      double intensity = p_intensity;
      if (!(this instanceof Pin)) {
        intensity = p_intensity / Math.max(visibilityFactor, 1);
      }
      for (int i = 0; i <= toLayer - fromLayer; i++) {
        Shape currShape = this.get_shape(i);
        if (currShape == null) {
          continue;
        }
        double layerVis = p_graphics_context.get_layer_visibility(fromLayer + i);
        if (layerVis <= 0.001) {
          continue;
        }
        Color color = p_color_arr[fromLayer + i];
        double layerIntensity = this instanceof Pin ? intensity : intensity * layerVis;
        p_graphics_context.fill_area(currShape, p_g, color, layerIntensity);
      }
    }

    // Render drill hole for through-hole pins only (not vias)
    if (this instanceof Pin && fromLayer != toLayer) {
      Padstack padstack = get_padstack();
      if (padstack != null) {
        double drillRadius = padstack.get_drill_radius();
        if (drillRadius > 0) {
          Color drillColor = p_graphics_context.otherColorTable.get_drill_hole_color();
          double drillIntensity =
              p_graphics_context.colorIntensityTable.get_value(
                  ColorIntensityTable.ObjectNames.DRILL_HOLES.ordinal());
          IntPoint centerPoint = get_center().to_float().round();
          Circle drillCircle = new Circle(centerPoint, (int) Math.round(drillRadius));
          p_graphics_context.fill_circle(drillCircle, p_g, drillColor, drillIntensity);
        }
      }
    }
  }

  /** Auxiliary class used in the method move_by */
  private static class TraceInfo implements Comparable<TraceInfo> {

    int layer;
    int halfWidth;
    int clearanceType;

    TraceInfo(int p_layer, int p_half_width, int p_clearance_type) {
      layer = p_layer;
      halfWidth = p_half_width;
      clearanceType = p_clearance_type;
    }

    /** Implements the comparable interface. */
    @Override
    public int compareTo(TraceInfo p_other) {
      return p_other.layer - this.layer;
    }
  }
}

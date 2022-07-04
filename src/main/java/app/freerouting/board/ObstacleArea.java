package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import java.awt.Color;

/**
 * An item on the board with an relative_area shape, for example keepout, conduction relative_area
 */
public class ObstacleArea extends Item implements java.io.Serializable {
  /** For debugging the division into tree shapes */
  private static final boolean display_tree_shapes = false;
  /**
   * The name of this ObstacleArea, which is null, if the ObstacleArea doos not belong to a
   * component.
   */
  public final String name;
  /** the layer of this relative_area */
  private int layer;
  private final Area relative_area;
  private transient Area precalculated_absolute_area = null;
  private Vector translation;
  private double rotation_in_degree;
  private boolean side_changed;

  /**
   * Creates a new relative_area item which may belong to several nets. p_name is null, if the
   * ObstacleArea does not belong to a component.
   */
  ObstacleArea(
      Area p_area,
      int p_layer,
      Vector p_translation,
      double p_rotation_in_degree,
      boolean p_side_changed,
      int[] p_net_no_arr,
      int p_clearance_type,
      int p_id_no,
      int p_cmp_no,
      String p_name,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(p_net_no_arr, p_clearance_type, p_id_no, p_cmp_no, p_fixed_state, p_board);
    this.relative_area = p_area;
    this.layer = p_layer;
    this.translation = p_translation;
    this.rotation_in_degree = p_rotation_in_degree;
    this.side_changed = p_side_changed;
    this.name = p_name;
  }

  /**
   * Creates a new relative_area item without net. p_name is null, if the ObstacleArea does not
   * belong to a component.
   */
  ObstacleArea(
      Area p_area,
      int p_layer,
      Vector p_translation,
      double p_rotation_in_degree,
      boolean p_side_changed,
      int p_clearance_type,
      int p_id_no,
      int p_group_no,
      String p_name,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    this(
        p_area,
        p_layer,
        p_translation,
        p_rotation_in_degree,
        p_side_changed,
        new int[0],
        p_clearance_type,
        p_id_no,
        p_group_no,
        p_name,
        p_fixed_state,
        p_board);
  }

  public Item copy(int p_id_no) {
    int[] copied_net_nos = new int[net_no_arr.length];
    System.arraycopy(net_no_arr, 0, copied_net_nos, 0, net_no_arr.length);
    return new ObstacleArea(
        relative_area,
        layer,
        translation,
        rotation_in_degree,
        side_changed,
        copied_net_nos,
        clearance_class_no(),
        p_id_no,
        get_component_no(),
        name,
        get_fixed_state(),
        board);
  }

  public Area get_area() {
    if (this.precalculated_absolute_area == null) {
      if (this.relative_area == null) {
        FRLogger.warn("ObstacleArea.get_area: area is null");
        return null;
      }
      Area turned_area = this.relative_area;
      if (this.side_changed && !this.board.components.get_flip_style_rotate_first()) {
        turned_area = turned_area.mirror_vertical(Point.ZERO);
      }
      if (this.rotation_in_degree != 0) {
        double rotation = this.rotation_in_degree;
        if (rotation % 90 == 0) {
          turned_area = turned_area.turn_90_degree(((int) rotation) / 90, Point.ZERO);
        } else {
          turned_area = turned_area.rotate_approx(Math.toRadians(rotation), FloatPoint.ZERO);
        }
      }
      if (this.side_changed && this.board.components.get_flip_style_rotate_first()) {
        turned_area = turned_area.mirror_vertical(Point.ZERO);
      }
      this.precalculated_absolute_area = turned_area.translate_by(this.translation);
    }
    return this.precalculated_absolute_area;
  }

  protected Area get_relative_area() {
    return this.relative_area;
  }

  public boolean is_on_layer(int p_layer) {
    return layer == p_layer;
  }

  public int first_layer() {
    return this.layer;
  }

  public int last_layer() {
    return this.layer;
  }

  public int get_layer() {
    return this.layer;
  }

  public IntBox bounding_box() {
    return this.get_area().bounding_box();
  }

  public boolean is_obstacle(Item p_other) {
    if (p_other.shares_net(this)) {
      return false;
    }
    return p_other instanceof Trace || p_other instanceof Via;
  }

  protected TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree) {
    return p_search_tree.calculate_tree_shapes(this);
  }

  public int tile_shape_count() {
    TileShape[] tile_shapes = this.split_to_convex();
    if (tile_shapes == null) {
      // an error accured while dividing the relative_area
      return 0;
    }
    return tile_shapes.length;
  }

  public TileShape get_tile_shape(int p_no) {
    TileShape[] tile_shapes = this.split_to_convex();
    if (tile_shapes == null || p_no < 0 || p_no >= tile_shapes.length) {
      FRLogger.warn("ConvexObstacle.get_tile_shape: p_no out of range");
      return null;
    }
    return tile_shapes[p_no];
  }

  public void translate_by(Vector p_vector) {
    this.translation = this.translation.add(p_vector);
    this.clear_derived_data();
  }

  public void turn_90_degree(int p_factor, IntPoint p_pole) {
    this.rotation_in_degree += p_factor * 90;
    while (this.rotation_in_degree >= 360) {
      this.rotation_in_degree -= 360;
    }
    while (this.rotation_in_degree < 0) {
      this.rotation_in_degree += 360;
    }
    Point rel_location = Point.ZERO.translate_by(this.translation);
    this.translation = rel_location.turn_90_degree(p_factor, p_pole).difference_by(Point.ZERO);
    this.clear_derived_data();
  }

  public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole) {
    double turn_angle = p_angle_in_degree;
    if (this.side_changed && this.board.components.get_flip_style_rotate_first()) {
      turn_angle = 360 - p_angle_in_degree;
    }
    this.rotation_in_degree += turn_angle;
    while (this.rotation_in_degree >= 360) {
      this.rotation_in_degree -= 360;
    }
    while (this.rotation_in_degree < 0) {
      this.rotation_in_degree += 360;
    }
    FloatPoint new_translation =
        this.translation.to_float().rotate(Math.toRadians(p_angle_in_degree), p_pole);
    this.translation = new_translation.round().difference_by(Point.ZERO);
    this.clear_derived_data();
  }

  public void change_placement_side(IntPoint p_pole) {
    this.side_changed = !this.side_changed;
    if (this.board != null) {
      this.layer = board.get_layer_count() - this.layer - 1;
    }
    Point rel_location = Point.ZERO.translate_by(this.translation);
    this.translation = rel_location.mirror_vertical(p_pole).difference_by(Point.ZERO);
    this.clear_derived_data();
  }

  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.KEEPOUT);
  }

  public Color[] get_draw_colors(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_obstacle_colors();
  }

  public double get_draw_intensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_obstacle_color_intensity();
  }

  public int get_draw_priority() {
    return app.freerouting.boardgraphics.Drawable.MIN_DRAW_PRIORITY;
  }

  public void draw(
      java.awt.Graphics p_g,
      GraphicsContext p_graphics_context,
      Color[] p_color_arr,
      double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    Color color = p_color_arr[this.layer];
    double intensity = p_graphics_context.get_layer_visibility(this.layer) * p_intensity;
    p_graphics_context.fill_area(this.get_area(), p_g, color, intensity);
    if (intensity > 0 && display_tree_shapes) {
      ShapeSearchTree default_tree = this.board.search_tree_manager.get_default_tree();
      for (int i = 0; i < this.tree_shape_count(default_tree); ++i) {
        p_graphics_context.draw_boundary(
            this.get_tree_shape(default_tree, i), 1, Color.white, p_g, 1);
      }
    }
  }

  public int shape_layer(int p_index) {
    return layer;
  }

  protected Vector get_translation() {
    return translation;
  }

  protected double get_rotation_in_degree() {
    return rotation_in_degree;
  }

  protected boolean get_side_changed() {
    return side_changed;
  }

  public void print_info(ObjectInfoPanel p_window, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append_bold(resources.getString("keepout"));
    int cmp_no = this.get_component_no();
    if (cmp_no > 0) {
      p_window.append(" " + resources.getString("of_component") + " ");
      Component component = board.components.get(cmp_no);
      p_window.append(component.name, resources.getString("component_info"), component);
    }
    this.print_shape_info(p_window, p_locale);
    this.print_item_info(p_window, p_locale);
    p_window.newline();
  }

  /** Used in the implementation of print_info for this class and derived classes. */
  protected final void print_shape_info(ObjectInfoPanel p_window, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append(" " + resources.getString("at") + " ");
    app.freerouting.geometry.planar.FloatPoint center =
        this.get_area().get_border().centre_of_gravity();
    p_window.append(center);
    Integer hole_count = this.relative_area.get_holes().length;
    if (hole_count > 0) {
      p_window.append(" " + resources.getString("with") + " ");
      java.text.NumberFormat nf = java.text.NumberFormat.getInstance(p_locale);
      p_window.append(nf.format(hole_count));
      if (hole_count == 1) {
        p_window.append(" " + resources.getString("hole"));
      } else {
        p_window.append(" " + resources.getString("holes"));
      }
    }
    p_window.append(" " + resources.getString("on_layer") + " ");
    p_window.append(this.board.layer_structure.arr[this.get_layer()].name);
  }

  TileShape[] split_to_convex() {
    if (this.relative_area == null) {
      FRLogger.warn("ObstacleArea.split_to_convex: area is null");
      return null;
    }
    return this.get_area().split_to_convex();
  }

  public void clear_derived_data() {
    super.clear_derived_data();
    this.precalculated_absolute_area = null;
  }

  public boolean write(java.io.ObjectOutputStream p_stream) {
    try {
      p_stream.writeObject(this);
    } catch (java.io.IOException e) {
      return false;
    }
    return true;
  }
}

package app.freerouting.board;

import app.freerouting.boardgraphics.Drawable;
import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

/** An item on the board with a relativeArea shape, for example keepout, conduction relativeArea */
public class ObstacleArea extends Item implements Serializable {

  /** For debugging the division into tree shapes */
  private static final boolean display_tree_shapes = false;

  /**
   * The name of this ObstacleArea, which is null, if the ObstacleArea does not belong to a
   * component.
   */
  public final String name;

  private final Area relativeArea;

  /** the layer of this relativeArea */
  private int layer;

  private transient Area precalculatedAbsoluteArea;
  private Vector translation;
  private double rotationInDegree;
  private boolean sideChanged;

  /**
   * Creates a new relativeArea item which may belong to several nets. p_name is null, if the
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
    this.relativeArea = p_area;
    this.layer = p_layer;
    this.translation = p_translation;
    this.rotationInDegree = p_rotation_in_degree;
    this.sideChanged = p_side_changed;
    this.name = p_name;
  }

  /**
   * Creates a new relativeArea item without net. p_name is null, if the ObstacleArea does not
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

  @Override
  public Item copy(int p_id_no) {
    int[] copiedNetNos = new int[netNoArr.length];
    System.arraycopy(netNoArr, 0, copiedNetNos, 0, netNoArr.length);
    return new ObstacleArea(
        relativeArea,
        layer,
        translation,
        rotationInDegree,
        sideChanged,
        copiedNetNos,
        clearance_class_no(),
        p_id_no,
        get_component_no(),
        name,
        get_fixed_state(),
        board);
  }

  public Area get_area() {
    if (this.precalculatedAbsoluteArea == null) {
      if (this.relativeArea == null) {
        FRLogger.warn("ObstacleArea.get_area: area is null");
        return null;
      }
      Area turnedArea = this.relativeArea;
      if (this.sideChanged && !this.board.components.get_flip_style_rotate_first()) {
        turnedArea = turnedArea.mirror_vertical(Point.ZERO);
      }
      if (this.rotationInDegree != 0) {
        double rotation = this.rotationInDegree;
        if (rotation % 90 == 0) {
          turnedArea = turnedArea.turn_90_degree(((int) rotation) / 90, Point.ZERO);
        } else {
          turnedArea = turnedArea.rotate_approx(Math.toRadians(rotation), FloatPoint.ZERO);
        }
      }
      if (this.sideChanged && this.board.components.get_flip_style_rotate_first()) {
        turnedArea = turnedArea.mirror_vertical(Point.ZERO);
      }
      this.precalculatedAbsoluteArea = turnedArea.translate_by(this.translation);
    }
    return this.precalculatedAbsoluteArea;
  }

  protected Area get_relative_area() {
    return this.relativeArea;
  }

  @Override
  public boolean is_on_layer(int p_layer) {
    return layer == p_layer;
  }

  @Override
  public int first_layer() {
    return this.layer;
  }

  @Override
  public int last_layer() {
    return this.layer;
  }

  public int get_layer() {
    return this.layer;
  }

  @Override
  public IntBox bounding_box() {
    return this.get_area().bounding_box();
  }

  @Override
  public boolean is_obstacle(Item p_other) {
    if (p_other.shares_net(this)) {
      return false;
    }
    return p_other instanceof Trace || p_other instanceof Via;
  }

  @Override
  protected TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree) {
    return p_search_tree.calculate_tree_shapes(this);
  }

  @Override
  public int tile_shape_count() {
    TileShape[] tileShapes = this.split_to_convex();
    if (tileShapes == null) {
      // an error occurred while dividing the relativeArea
      return 0;
    }
    return tileShapes.length;
  }

  @Override
  public TileShape get_tile_shape(int p_no) {
    TileShape[] tileShapes = this.split_to_convex();
    if (tileShapes == null || p_no < 0 || p_no >= tileShapes.length) {
      FRLogger.warn("ConvexObstacle.get_tile_shape: p_no out of range");
      return null;
    }
    return tileShapes[p_no];
  }

  @Override
  public void translate_by(Vector p_vector) {
    this.translation = this.translation.add(p_vector);
    this.clear_derived_data();
  }

  @Override
  public void turn_90_degree(int p_factor, IntPoint p_pole) {
    this.rotationInDegree += p_factor * 90;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    Point relLocation = Point.ZERO.translate_by(this.translation);
    this.translation = relLocation.turn_90_degree(p_factor, p_pole).difference_by(Point.ZERO);
    this.clear_derived_data();
  }

  @Override
  public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole) {
    double turnAngle = p_angle_in_degree;
    if (this.sideChanged && this.board.components.get_flip_style_rotate_first()) {
      turnAngle = 360 - p_angle_in_degree;
    }
    this.rotationInDegree += turnAngle;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    FloatPoint newTranslation =
        this.translation.to_float().rotate(Math.toRadians(p_angle_in_degree), p_pole);
    this.translation = newTranslation.round().difference_by(Point.ZERO);
    this.clear_derived_data();
  }

  @Override
  public void change_placement_side(IntPoint p_pole) {
    this.sideChanged = !this.sideChanged;
    if (this.board != null) {
      this.layer = board.get_layer_count() - this.layer - 1;
    }
    Point relLocation = Point.ZERO.translate_by(this.translation);
    this.translation = relLocation.mirror_vertical(p_pole).difference_by(Point.ZERO);
    this.clear_derived_data();
  }

  @Override
  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.KEEPOUT);
  }

  @Override
  public Color[] get_draw_colors(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_obstacle_colors();
  }

  @Override
  public double get_draw_intensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_obstacle_color_intensity();
  }

  @Override
  public int get_draw_priority() {
    return Drawable.MIN_DRAW_PRIORITY;
  }

  @Override
  public void draw(
      Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    Color color = p_color_arr[this.layer];
    double intensity = p_graphics_context.get_layer_visibility(this.layer) * p_intensity;
    p_graphics_context.fill_area(this.get_area(), p_g, color, intensity);
    if (intensity > 0 && display_tree_shapes) {
      ShapeSearchTree defaultTree = this.board.searchTreeManager.get_default_tree();
      for (int i = 0; i < this.tree_shape_count(defaultTree); i++) {
        p_graphics_context.draw_boundary(
            this.get_tree_shape(defaultTree, i), 1, Color.white, p_g, 1);
      }
    }
  }

  @Override
  public int shape_layer(int p_index) {
    return layer;
  }

  protected Vector get_translation() {
    return translation;
  }

  protected double get_rotation_in_degree() {
    return rotationInDegree;
  }

  protected boolean get_side_changed() {
    return sideChanged;
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("keepout"));
    int cmpNo = this.get_component_no();
    if (cmpNo > 0) {
      p_window.append(" " + tm.getText("of_component") + " ");
      Component component = board.components.get(cmpNo);
      p_window.append(component.name, tm.getText("component_info"), component);
    }
    this.print_shape_info(p_window, p_locale);
    this.print_item_info(p_window, p_locale);
    p_window.newline();
  }

  /** Used in the implementation of print_info for this class and derived classes. */
  protected final void print_shape_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append(" " + tm.getText("at") + " ");
    FloatPoint center = this.get_area().get_border().centre_of_gravity();
    p_window.append(center);
    Integer holeCount = this.relativeArea.get_holes().length;
    if (holeCount > 0) {
      p_window.append(" " + tm.getText("with") + " ");
      NumberFormat nf = NumberFormat.getInstance(p_locale);
      p_window.append(nf.format(holeCount));
      if (holeCount == 1) {
        p_window.append(" " + tm.getText("hole"));
      } else {
        p_window.append(" " + tm.getText("holes"));
      }
    }
    p_window.append(" " + tm.getText("on_layer") + " ");
    p_window.append(this.board.layerStructure.arr[this.get_layer()].name);
  }

  TileShape[] split_to_convex() {
    if (this.relativeArea == null) {
      FRLogger.warn("ObstacleArea.split_to_convex: area is null");
      return null;
    }
    return this.get_area().split_to_convex();
  }

  @Override
  public void clear_derived_data() {
    super.clear_derived_data();
    this.precalculatedAbsoluteArea = null;
  }

  @Override
  public boolean write(ObjectOutputStream p_stream) {
    try {
      p_stream.writeObject(this);
    } catch (IOException _) {
      return false;
    }
    return true;
  }
}

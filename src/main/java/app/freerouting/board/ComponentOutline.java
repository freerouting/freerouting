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
import java.awt.Color;
import java.awt.Graphics;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;

public class ComponentOutline extends Item implements Serializable {

  private final Area relativeArea;
  private transient Area precalculatedAbsoluteArea;
  private Vector translation;
  private double rotationInDegree;
  private boolean isFront;
  private final boolean isCourtyard;
  private final boolean isFabrication;
  private final boolean isClosed;

  /** Creates a new instance of ComponentOutline */
  public ComponentOutline(
      Area p_area,
      boolean p_is_front,
      Vector p_translation,
      double p_rotation_in_degree,
      int p_id_no,
      int p_component_no,
      boolean p_is_courtyard,
      boolean p_is_fabrication,
      boolean p_is_closed,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(new int[0], 0, p_id_no, p_component_no, p_fixed_state, p_board);
    this.relativeArea = p_area;
    this.isFront = p_is_front;
    this.translation = p_translation;
    this.rotationInDegree = p_rotation_in_degree;
    this.isCourtyard = p_is_courtyard;
    this.isFabrication = p_is_fabrication;
    this.isClosed = p_is_closed;
  }

  @Override
  public Item copy(int p_id_no) {
    return new ComponentOutline(
        this.relativeArea,
        this.isFront,
        this.translation,
        this.rotationInDegree,
        p_id_no,
        this.get_component_no(),
        this.isCourtyard,
        this.isFabrication,
        this.isClosed,
        this.get_fixed_state(),
        this.board);
  }

  public boolean is_front() {
    return this.isFront;
  }

  public boolean is_courtyard() {
    return this.isCourtyard;
  }

  public boolean is_fabrication() {
    return this.isFabrication;
  }

  public boolean is_closed() {
    return this.isClosed;
  }

  @Override
  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    return false;
  }

  public int get_layer() {
    int result;
    if (this.isFront) {
      result = 0;
    } else {
      result = this.board.get_layer_count() - 1;
    }
    return result;
  }

  @Override
  public int first_layer() {
    return get_layer();
  }

  @Override
  public int last_layer() {
    return get_layer();
  }

  @Override
  public boolean is_on_layer(int p_layer) {
    return get_layer() == p_layer;
  }

  @Override
  public boolean is_obstacle(Item p_item) {
    return false;
  }

  @Override
  public int shape_layer(int p_index) {
    return get_layer();
  }

  @Override
  public int tile_shape_count() {
    return 0;
  }

  @Override
  protected TileShape[] calculate_tree_shapes(ShapeSearchTree p_search_tree) {
    return new TileShape[0];
  }

  @Override
  public double get_draw_intensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_component_outline_color_intensity();
  }

  @Override
  public Color[] get_draw_colors(GraphicsContext p_graphics_context) {
    Color[] colorArr = new Color[this.board.layerStructure.arr.length];
    Color frontDrawColor;
    Color backDrawColor;
    if (this.isCourtyard) {
      frontDrawColor = p_graphics_context.otherColorTable.get_courtyard_color(true);
      backDrawColor = p_graphics_context.otherColorTable.get_courtyard_color(false);
    } else if (this.isFabrication) {
      frontDrawColor = p_graphics_context.otherColorTable.get_fab_color(true);
      backDrawColor = p_graphics_context.otherColorTable.get_fab_color(false);
    } else {
      frontDrawColor = p_graphics_context.otherColorTable.get_silkscreen_color(true);
      backDrawColor = p_graphics_context.otherColorTable.get_silkscreen_color(false);
    }
    for (int i = 0; i < colorArr.length - 1; i++) {
      colorArr[i] = frontDrawColor;
    }
    if (colorArr.length > 1) {
      colorArr[colorArr.length - 1] = backDrawColor;
    }
    return colorArr;
  }

  @Override
  public int get_draw_priority() {
    return Drawable.MIDDLE_DRAW_PRIORITY;
  }

  @Override
  public void draw(
      Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    int virtualLayerIdx;
    if (this.isCourtyard) {
      virtualLayerIdx = this.isFront ? 2 : 3;
    } else if (this.isFabrication) {
      virtualLayerIdx = this.isFront ? 4 : 5;
    } else {
      virtualLayerIdx = this.isFront ? 0 : 1;
    }
    double virtualVisibility = p_graphics_context.get_virtual_layer_visibility(virtualLayerIdx);
    if (virtualVisibility <= 0) {
      return;
    }

    Color color = p_color_arr[this.get_layer()];
    double intensity = virtualVisibility * p_intensity;

    if (this.isCourtyard || this.isClosed) {
      double drawWidth = Math.min(this.board.communication.get_resolution(Unit.MIL), 100);
      p_graphics_context.draw_boundary(this.get_area(), drawWidth, color, p_g, intensity);
    } else {
      p_graphics_context.fill_area(this.get_area(), p_g, color, intensity);
    }
  }

  @Override
  public IntBox bounding_box() {
    return get_area().bounding_box();
  }

  @Override
  public void translate_by(Vector p_vector) {
    this.translation = this.translation.add(p_vector);
    clear_derived_data();
  }

  @Override
  public void change_placement_side(IntPoint p_pole) {
    this.isFront = !this.isFront;
    Point relLocation = Point.ZERO.translate_by(this.translation);
    this.translation = relLocation.mirror_vertical(p_pole).difference_by(Point.ZERO);
    clear_derived_data();
  }

  @Override
  public void rotate_approx(double p_angle_in_degree, FloatPoint p_pole) {
    double turnAngle = p_angle_in_degree;
    if (!this.isFront && this.board.components.get_flip_style_rotate_first()) {
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
    clear_derived_data();
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
    clear_derived_data();
  }

  public Area get_area() {
    if (this.precalculatedAbsoluteArea == null) {
      if (this.relativeArea == null) {
        FRLogger.warn("ObstacleArea.get_area: area is null");
        return null;
      }
      Area turnedArea = this.relativeArea;
      if (!this.isFront && !this.board.components.get_flip_style_rotate_first()) {
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
      if (!this.isFront && this.board.components.get_flip_style_rotate_first()) {
        turnedArea = turnedArea.mirror_vertical(Point.ZERO);
      }
      this.precalculatedAbsoluteArea = turnedArea.translate_by(this.translation);
    }
    return this.precalculatedAbsoluteArea;
  }

  @Override
  public void clear_derived_data() {
    precalculatedAbsoluteArea = null;
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {}

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

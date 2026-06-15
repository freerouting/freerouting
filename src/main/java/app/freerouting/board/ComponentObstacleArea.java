package app.freerouting.board;

import app.freerouting.boardgraphics.GraphicsContext;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Locale;

/**
 * Describes areas of the board, where components are not allowed.
 */
public class ComponentObstacleArea extends ObstacleArea {

  /**
   * Creates a new instance of ComponentObstacleArea If p_is_obstacle ist false, the new instance is not regarded as obstacle and used only for displaying on the screen.
   */
  ComponentObstacleArea(Area p_area, int p_layer, Vector p_translation, double p_rotation_in_degree, boolean p_side_changed, int p_clearance_type, int p_id_no, int p_component_no, String p_name,
      FixedState p_fixed_state, BasicBoard p_board) {
    super(p_area, p_layer, p_translation, p_rotation_in_degree, p_side_changed, new int[0], p_clearance_type, p_id_no, p_component_no, p_name, p_fixed_state, p_board);
  }

  @Override
  public Item copy(int p_id_no) {
    return new ComponentObstacleArea(get_relative_area(), get_layer(), get_translation(), get_rotation_in_degree(), get_side_changed(), clearance_class_no(), p_id_no, get_component_no(), this.name,
        get_fixed_state(), board);
  }

  @Override
  public boolean is_obstacle(Item p_other) {
    return p_other != this && p_other instanceof ComponentObstacleArea && p_other.get_component_no() != this.get_component_no();
  }

  @Override
  public boolean is_trace_obstacle(int p_net_no) {
    return false;
  }

  @Override
  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.COMPONENT_KEEPOUT);
  }

  public boolean is_front() {
    Component component = board.components.get(this.get_component_no());
    return component == null || component.placed_on_front();
  }

  @Override
  public Color[] get_draw_colors(GraphicsContext p_graphics_context) {
    Color[] color_arr = new Color[this.board.layer_structure.arr.length];
    Color front_draw_color = p_graphics_context.other_color_table.get_courtyard_color(true);
    for (int i = 0; i < color_arr.length - 1; i++) {
      color_arr[i] = front_draw_color;
    }
    if (color_arr.length > 1) {
      color_arr[color_arr.length - 1] = p_graphics_context.other_color_table.get_courtyard_color(false);
    }
    return color_arr;
  }

  @Override
  public double get_draw_intensity(GraphicsContext p_graphics_context) {
    return p_graphics_context.get_component_outline_color_intensity();
  }

  @Override
  public void draw(Graphics p_g, GraphicsContext p_graphics_context, Color[] p_color_arr, double p_intensity) {
    if (p_graphics_context == null || p_intensity <= 0) {
      return;
    }
    int virtualLayerIdx = this.is_front() ? 2 : 3;
    double virtualVisibility = p_graphics_context.get_virtual_layer_visibility(virtualLayerIdx);
    if (virtualVisibility <= 0) {
      return;
    }

    Color color = p_color_arr[this.get_layer()];
    double intensity = virtualVisibility * p_intensity;

    double draw_width = Math.min(this.board.communication.get_resolution(Unit.MIL), 100);
    p_graphics_context.draw_boundary(this.get_area(), draw_width, color, p_g, intensity);
  }

  @Override
  public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.append_bold(tm.getText("component_keepout"));
    this.print_shape_info(p_window, p_locale);
    this.print_clearance_info(p_window, p_locale);
    this.print_clearance_violation_info(p_window, p_locale);
    p_window.newline();
  }
}

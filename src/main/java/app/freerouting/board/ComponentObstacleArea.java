package app.freerouting.board;

import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Vector;

/** Describes areas of the board, where components are not allowed. */
public class ComponentObstacleArea extends ObstacleArea {
  /**
   * Creates a new instance of ComponentObstacleArea If p_is_obstacle ist false, the new instance is
   * not regarded as obstacle and used only for displaying on the screen.
   */
  ComponentObstacleArea(
      Area p_area,
      int p_layer,
      Vector p_translation,
      double p_rotation_in_degree,
      boolean p_side_changed,
      int p_clearance_type,
      int p_id_no,
      int p_component_no,
      String p_name,
      FixedState p_fixed_state,
      BasicBoard p_board) {
    super(
        p_area,
        p_layer,
        p_translation,
        p_rotation_in_degree,
        p_side_changed,
        new int[0],
        p_clearance_type,
        p_id_no,
        p_component_no,
        p_name,
        p_fixed_state,
        p_board);
  }

  public Item copy(int p_id_no) {
    return new ComponentObstacleArea(
        get_relative_area(),
        get_layer(),
        get_translation(),
        get_rotation_in_degree(),
        get_side_changed(),
        clearance_class_no(),
        p_id_no,
        get_component_no(),
        this.name,
        get_fixed_state(),
        board);
  }

  public boolean is_obstacle(Item p_other) {
    return p_other != this
        && p_other instanceof ComponentObstacleArea
        && p_other.get_component_no() != this.get_component_no();
  }

  public boolean is_trace_obstacle(int p_net_no) {
    return false;
  }

  public boolean is_selected_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.COMPONENT_KEEPOUT);
  }

  public java.awt.Color[] get_draw_colors(
      app.freerouting.boardgraphics.GraphicsContext p_graphics_context) {
    return p_graphics_context.get_place_obstacle_colors();
  }

  public double get_draw_intensity(
      app.freerouting.boardgraphics.GraphicsContext p_graphics_context) {
    return p_graphics_context.get_place_obstacle_color_intensity();
  }

  public boolean is_selectrd_by_filter(ItemSelectionFilter p_filter) {
    if (!this.is_selected_by_fixed_filter(p_filter)) {
      return false;
    }
    return p_filter.is_selected(ItemSelectionFilter.SelectableChoices.COMPONENT_KEEPOUT);
  }

  public void print_info(ObjectInfoPanel p_window, java.util.Locale p_locale) {
    java.util.ResourceBundle resources =
        java.util.ResourceBundle.getBundle("app.freerouting.board.ObjectInfoPanel", p_locale);
    p_window.append_bold(resources.getString("component_keepout"));
    this.print_shape_info(p_window, p_locale);
    this.print_clearance_info(p_window, p_locale);
    this.print_clearance_violation_info(p_window, p_locale);
    p_window.newline();
  }
}

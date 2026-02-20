package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.NetClass;

import javax.swing.JPopupMenu;
import java.awt.Color;
import java.awt.Graphics;

/** State for interactive routing by adding corners with the left mouse button. */
public class StitchRouteState extends RouteState {

  /** Creates a new instance of StitchRouteState */
  protected StitchRouteState(
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_parent_state, p_board_handling, p_activityReplayFile);
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return add_corner(p_location);
  }

  @Override
  public InteractiveState add_corner(FloatPoint p_location) {
    // make the current situation restorable by undo
    hdlg.get_routing_board().generate_snapshot();
    return super.add_corner(p_location);
  }

  @Override
  public InteractiveState mouse_moved() {
    super.mouse_moved();
    this.route.calc_nearest_target_point(hdlg.get_current_mouse_position());
    hdlg.repaint();
    return this;
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popup_menu_stitch_route;
  }

  @Override
  public String get_help_id() {
    return "RouteState_StitchingRouteState";
  }

  @Override
  public void draw(Graphics p_graphics) {
    super.draw(p_graphics);
    if (route == null) {
      return;
    }
    // draw a line from the routing end point to the cursor
    FloatPoint[] draw_points = new FloatPoint[2];
    draw_points[0] = route.get_last_corner().to_float();
    draw_points[1] = hdlg.get_current_mouse_position();
    Color draw_color = hdlg.graphics_context.get_hilight_color();
    double display_width = hdlg.get_trace_halfwidth(route.net_no_arr[0], hdlg.settings.layer);
    int clearance_draw_width = 50;
    double radius_with_clearance = display_width;
    NetClass default_net_class =
        hdlg.get_routing_board().rules.get_default_net_class();
    int cl_class =
        default_net_class.default_item_clearance_classes.get(
            DefaultItemClearanceClasses.ItemClass.TRACE);
    radius_with_clearance +=
        hdlg.get_routing_board().clearance_value(cl_class, cl_class, hdlg.settings.layer);
    hdlg.graphics_context.draw(draw_points, display_width, draw_color, p_graphics, 0.5);
    // draw the clearance boundary around the end point
    hdlg.graphics_context.draw_circle(
        draw_points[1], radius_with_clearance, clearance_draw_width, draw_color, p_graphics, 0.5);
  }
}

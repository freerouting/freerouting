package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.NetClass;
import java.awt.Color;
import java.awt.Graphics;
import javax.swing.JPopupMenu;

/** State for interactive routing by adding corners with the left mouse button. */
public class StitchRouteState extends RouteState {

  /** Creates a new instance of StitchRouteState */
  protected StitchRouteState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
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
    return hdlg.get_panel().popupMenuStitchRoute;
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
    FloatPoint[] drawPoints = new FloatPoint[2];
    drawPoints[0] = route.get_last_corner().to_float();
    drawPoints[1] = hdlg.get_current_mouse_position();
    Color drawColor = hdlg.graphicsContext.get_hilight_color();
    double displayWidth =
        hdlg.get_trace_halfwidth(route.netNoArr[0], hdlg.getInteractiveSettings().get_layer());
    int clearanceDrawWidth = 50;
    double radiusWithClearance = displayWidth;
    NetClass defaultNetClass = hdlg.get_routing_board().rules.get_default_net_class();
    int clClass =
        defaultNetClass.defaultItemClearanceClasses.get(
            DefaultItemClearanceClasses.ItemClass.TRACE);
    radiusWithClearance +=
        hdlg.get_routing_board()
            .clearance_value(clClass, clClass, hdlg.getInteractiveSettings().get_layer());
    hdlg.graphicsContext.draw(drawPoints, displayWidth, drawColor, p_graphics, 0.5);
    // draw the clearance boundary around the end point
    hdlg.graphicsContext.draw_circle(
        drawPoints[1], radiusWithClearance, clearanceDrawWidth, drawColor, p_graphics, 0.5);
  }
}

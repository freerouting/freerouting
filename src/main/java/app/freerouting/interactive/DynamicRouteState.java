package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import javax.swing.JPopupMenu;

/** State for dynamic interactive routing, which is routing while moving the mouse pointer. */
public class DynamicRouteState extends RouteState {

  /** Creates a new instance of DynamicRouteState */
  protected DynamicRouteState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  @Override
  public InteractiveState mouse_moved() {
    super.mouse_moved();
    return add_corner(hdlg.get_current_mouse_position());
  }

  /** ends routing */
  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    if (this.observersActivated) {
      hdlg.get_routing_board().end_notify_observers();
      this.observersActivated = false;
    }
    for (int currNetNo : this.route.netNoArr) {
      hdlg.update_ratsnest(currNetNo);
    }
    return this.returnState;
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState key_typed(char p_key_char) {
    InteractiveState currReturnState = this;
    if (p_key_char == 's') {
      hdlg.generate_snapshot();
    } else {
      currReturnState = super.key_typed(p_key_char);
    }
    return currReturnState;
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popupMenuDynamicRoute;
  }

  @Override
  public String get_help_id() {
    return "RouteState_DynamicRouteState";
  }
}

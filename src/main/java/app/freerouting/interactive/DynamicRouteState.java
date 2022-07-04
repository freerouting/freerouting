package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/** State for dynamic interactive routing, which is routing while moving the mouse pointer. */
public class DynamicRouteState extends RouteState {

  /** Creates a new instance of DynamicRouteState */
  protected DynamicRouteState(
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_parent_state, p_board_handling, p_activityReplayFile);
  }

  public InteractiveState mouse_moved() {
    super.mouse_moved();
    return add_corner(hdlg.get_current_mouse_position());
  }

  /** ends routing */
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    if (this.observers_activated) {
      hdlg.get_routing_board().end_notify_observers();
      this.observers_activated = false;
    }
    if (activityReplayFile != null) {
      activityReplayFile.start_scope(ActivityReplayFileScope.COMPLETE_SCOPE);
    }
    for (int curr_net_no : this.route.net_no_arr) {
      hdlg.update_ratsnest(curr_net_no);
    }
    return this.return_state;
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  public InteractiveState key_typed(char p_key_char) {
    InteractiveState curr_return_state = this;
    if (p_key_char == 's') {
      hdlg.generate_snapshot();
    } else {
      curr_return_state = super.key_typed(p_key_char);
    }
    return curr_return_state;
  }

  public javax.swing.JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popup_menu_dynamic_route;
  }

  public String get_help_id() {
    return "RouteState_DynamicRouteState";
  }
}

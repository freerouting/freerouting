package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the route menu, especially the different
 * behaviour of the mouse button 1.
 */
public class RouteMenuState extends MenuState {
  /** Creates a new instance of RouteMenuState */
  private RouteMenuState(BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile) {
    super(p_board_handling, p_activityReplayFile);
  }

  /** Returns a new instance of RouteMenuState */
  public static RouteMenuState get_instance(
      BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile) {
    RouteMenuState new_state = new RouteMenuState(p_board_handling, p_activityReplayFile);
    return new_state;
  }

  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return RouteState.get_instance(p_location, this, hdlg, activityReplayFile);
  }

  public void display_default_message() {
    hdlg.screen_messages.set_status_message(" in route menu");
  }

  public String get_help_id() {
    return "MenuState_RouteMenuState";
  }
}

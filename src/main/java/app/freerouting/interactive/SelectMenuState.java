package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the select menu, especially the different
 * behaviour of the mouse button 1.
 */
public class SelectMenuState extends MenuState {
  /** Creates a new instance of SelectMenuState */
  private SelectMenuState(BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile) {
    super(p_board_handling, p_activityReplayFile);
  }

  /** Returns a new instance of SelectMenuState */
  public static SelectMenuState get_instance(
      BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile) {
    SelectMenuState new_state = new SelectMenuState(p_board_handling, p_activityReplayFile);
    return new_state;
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return select_items(p_location);
  }

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    return SelectItemsInRegionState.get_instance(
        hdlg.get_current_mouse_position(), this, hdlg, activityReplayFile);
  }

  @Override
  public void display_default_message() {
    hdlg.screen_messages.set_status_message(resources.getString("in_select_menu"));
  }

  @Override
  public String get_help_id() {
    return "MenuState_SelectMenuState";
  }
}

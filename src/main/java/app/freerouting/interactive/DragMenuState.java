package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/** Class implementing the different functionality in the drag menu */
public class DragMenuState extends MenuState {
  /** Creates a new instance of DragMenuState */
  public DragMenuState(BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile) {
    super(p_board_handling, p_activityReplayFile);
  }

  /** Returns a new instance of DragMenuState */
  public static DragMenuState get_instance(
      BoardHandling p_board_handling, ActivityReplayFile p_activityReplayFile) {
    DragMenuState new_state = new DragMenuState(p_board_handling, p_activityReplayFile);
    return new_state;
  }

  public InteractiveState mouse_pressed(FloatPoint p_point) {
    return DragState.get_instance(p_point, this, hdlg, activityReplayFile);
  }

  public String get_help_id() {
    return "MenuState_DragMenuState";
  }
}

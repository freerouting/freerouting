package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the drag menu
 */
public class DragMenuState extends MenuState {

  /**
   * Creates a new instance of DragMenuState
   */
  public DragMenuState(GuiBoardManager p_board_handling) {
    super(p_board_handling);
  }

  /**
   * Returns a new instance of DragMenuState
   */
  public static DragMenuState get_instance(GuiBoardManager p_board_handling) {
    DragMenuState new_state = new DragMenuState(p_board_handling);
    return new_state;
  }

  @Override
  public InteractiveState mouse_pressed(FloatPoint p_point) {
    return DragState.get_instance(p_point, this, hdlg);
  }

  @Override
  public String get_help_id() {
    return "MenuState_DragMenuState";
  }
}
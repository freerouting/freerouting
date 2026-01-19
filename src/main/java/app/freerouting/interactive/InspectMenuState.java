package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the inspect menu,
 * especially
 * the different behaviour of the mouse button 1.
 */
public class InspectMenuState extends MenuState {

  /**
   * Creates a new instance of InspectMenuState
   */
  private InspectMenuState(GuiBoardManager p_board_handling) {
    super(p_board_handling);
  }

  /**
   * Returns a new instance of InspectMenuState
   */
  public static InspectMenuState get_instance(GuiBoardManager p_board_handling) {
    InspectMenuState new_state = new InspectMenuState(p_board_handling);
    return new_state;
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return select_items(p_location);
  }

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    return InspectItemsInRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
  }

  @Override
  public void display_default_message() {
    hdlg.screen_messages.set_status_message(tm.getText("in_inspect_menu"));
  }

  @Override
  public String get_help_id() {
    return "MenuState_InspectMenuState";
  }
}
package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the select menu, especially
 * the different behaviour of the mouse button 1.
 */
public class SelectMenuState extends MenuState {

  /**
   * Creates a new instance of SelectMenuState
   */
  private SelectMenuState(GuiBoardManager p_board_handling) {
    super(p_board_handling);
  }

  /**
   * Returns a new instance of SelectMenuState
   */
  public static SelectMenuState get_instance(GuiBoardManager p_board_handling) {
    SelectMenuState new_state = new SelectMenuState(p_board_handling);
    return new_state;
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return select_items(p_location);
  }

  @Override
  public InteractiveState mouse_dragged(FloatPoint p_point) {
    return SelectItemsInRegionState.get_instance(hdlg.get_current_mouse_position(), this, hdlg);
  }

  @Override
  public void display_default_message() {
    hdlg.screen_messages.set_status_message(tm.getText("in_select_menu"));
  }

  @Override
  public String get_help_id() {
    return "MenuState_SelectMenuState";
  }
}
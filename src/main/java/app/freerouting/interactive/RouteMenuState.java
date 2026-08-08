package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the route menu, especially the different
 * behaviour of the mouse button 1.
 */
public final class RouteMenuState extends MenuState {

  /** Creates a new instance of RouteMenuState */
  private RouteMenuState(GuiBoardManager p_board_handling) {
    super(p_board_handling);
  }

  /** Returns a new instance of RouteMenuState */
  public static RouteMenuState getInstance(GuiBoardManager p_board_handling) {
    return new RouteMenuState(p_board_handling);
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint p_location) {
    return RouteState.getInstance(p_location, this, hdlg);
  }

  @Override
  public void displayDefaultMessage() {
    hdlg.screenMessages.setStatusMessage(" in route menu");
  }

  @Override
  public String getHelpId() {
    return "MenuState_RouteMenuState";
  }
}

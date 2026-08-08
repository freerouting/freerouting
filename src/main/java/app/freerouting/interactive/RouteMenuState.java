package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;

/**
 * Class implementing the different functionality in the route menu, especially the different
 * behaviour of the mouse button 1.
 */
public final class RouteMenuState extends MenuState {

  /** Creates a new instance of RouteMenuState */
  private RouteMenuState(GuiBoardManager pBoardHandling) {
    super(pBoardHandling);
  }

  /** Returns a new instance of RouteMenuState */
  public static RouteMenuState getInstance(GuiBoardManager pBoardHandling) {
    return new RouteMenuState(pBoardHandling);
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint pLocation) {
    return RouteState.getInstance(pLocation, this, hdlg);
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

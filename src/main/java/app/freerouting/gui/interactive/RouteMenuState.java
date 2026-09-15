package app.freerouting.gui.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.workspace.GuiBoardManager;

/**
 * Class implementing the different functionality in the route menu, especially the different
 * behaviour of the mouse button 1.
 */
public final class RouteMenuState extends MenuState {

  /** Creates a new instance of RouteMenuState. */
  private RouteMenuState(GuiBoardManager boardHandling) {
    super(boardHandling);
  }

  /** Returns a new instance of RouteMenuState. */
  public static RouteMenuState getInstance(GuiBoardManager boardHandling) {
    return new RouteMenuState(boardHandling);
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    return RouteState.getInstance(location, this, hdlg);
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

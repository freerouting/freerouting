package app.freerouting.gui.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.gui.workspace.GuiBoardManager;
import javax.swing.JPopupMenu;

/** State for dynamic interactive routing, which is routing while moving the mouse pointer. */
public class DynamicRouteState extends RouteState {

  /** Creates a new instance of DynamicRouteState. */
  protected DynamicRouteState(InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    return addCorner(hdlg.getCurrentMousePosition());
  }

  /** Ends routing. */
  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    if (this.observersActivated) {
      hdlg.getRoutingBoard().endNotifyObservers();
      this.observersActivated = false;
    }
    for (int currentNetNumber : this.route.netNumbers) {
      hdlg.updateRatsnest(currentNetNumber);
    }
    return this.returnState;
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState keyTyped(char keyChar) {
    InteractiveState currentReturnState = this;
    if (keyChar == 's') {
      hdlg.generateSnapshot();
    } else {
      currentReturnState = super.keyTyped(keyChar);
    }
    return currentReturnState;
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuDynamicRoute;
  }

  @Override
  public String getHelpId() {
    return "RouteState_DynamicRouteState";
  }
}

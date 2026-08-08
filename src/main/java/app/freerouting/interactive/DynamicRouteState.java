package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import javax.swing.JPopupMenu;

/** State for dynamic interactive routing, which is routing while moving the mouse pointer. */
public class DynamicRouteState extends RouteState {

  /** Creates a new instance of DynamicRouteState */
  protected DynamicRouteState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    return addCorner(hdlg.getCurrentMousePosition());
  }

  /** ends routing */
  @Override
  public InteractiveState leftButtonClicked(FloatPoint p_location) {
    if (this.observersActivated) {
      hdlg.getRoutingBoard().endNotifyObservers();
      this.observersActivated = false;
    }
    for (int currNetNo : this.route.netNoArr) {
      hdlg.updateRatsnest(currNetNo);
    }
    return this.returnState;
  }

  /** Action to be taken when a key is pressed (Shortcut). */
  @Override
  public InteractiveState keyTyped(char p_key_char) {
    InteractiveState currReturnState = this;
    if (p_key_char == 's') {
      hdlg.generateSnapshot();
    } else {
      currReturnState = super.keyTyped(p_key_char);
    }
    return currReturnState;
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

package app.freerouting.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.rules.Nets;
import app.freerouting.rules.ViaRule;
import java.awt.Graphics;

/**
 * Class for shoving items out of a region to make space to insert something else. For that purpose
 * traces of an invisible net are created temporary for shoving.
 */
public class MakeSpaceState extends DragState {

  private final Route route;

  /** Creates a new instance of MakeSpaceState */
  public MakeSpaceState(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_location, p_parent_state, p_board_handling);
    int[] shoveTraceWidthArr = new int[hdlg.getRoutingBoard().getLayerCount()];
    boolean[] layerActiveArr = new boolean[shoveTraceWidthArr.length];
    int shoveTraceWidth = Math.min(100, hdlg.getRoutingBoard().getMinTraceHalfWidth() / 10);
    shoveTraceWidth = Math.max(shoveTraceWidth, 5);
    for (int i = 0; i < shoveTraceWidthArr.length; i++) {
      shoveTraceWidthArr[i] = shoveTraceWidth;
      layerActiveArr[i] = true;
    }
    int[] routeNetNoArr = new int[1];
    routeNetNoArr[0] = Nets.hidden_net_no;
    route =
        new Route(
            p_location.round(),
            hdlg.getInteractiveSettings().getLayer(),
            shoveTraceWidthArr,
            layerActiveArr,
            routeNetNoArr,
            0,
            ViaRule.EMPTY,
            true,
            hdlg.getInteractiveSettings().getTracePullTightRegionWidth(),
            hdlg.getInteractiveSettings().getTracePullTightAccuracy(),
            null,
            null,
            hdlg.getRoutingBoard(),
            false,
            false,
            false,
            hdlg.getInteractiveSettings().getHilightRoutingObstacle());
  }

  @Override
  public InteractiveState moveTo(FloatPoint p_to_location) {
    if (!somethingDragged) {
      // initialisations for the first time dragging
      this.observersActivated = !hdlg.getRoutingBoard().observersActive();
      if (this.observersActivated) {
        hdlg.getRoutingBoard().startNotifyObservers();
      }
      // make the situation restorable by undo
      hdlg.getRoutingBoard().generateSnapshot();
      somethingDragged = true;
    }
    route.nextCorner(p_to_location);

    Point routeEnd = route.getLastCorner();
    if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction() == AngleRestriction.NONE
        && !routeEnd.equals(p_to_location.round())) {
      hdlg.moveMouse(routeEnd.toFloat());
    }
    hdlg.recalculateLengthViolations();
    hdlg.repaint();
    return this;
  }

  @Override
  public InteractiveState buttonReleased() {
    int deleteNetNo = Nets.hidden_net_no;
    BasicBoard board = hdlg.getRoutingBoard();
    board.removeItems(board.getConnectableItems(deleteNetNo));
    if (this.observersActivated) {
      hdlg.getRoutingBoard().endNotifyObservers();
      this.observersActivated = false;
    }
    hdlg.showRatsnest();
    return this.returnState;
  }

  @Override
  public void draw(Graphics p_graphics) {
    if (route != null) {
      route.draw(p_graphics, hdlg.graphicsContext);
    }
  }
}

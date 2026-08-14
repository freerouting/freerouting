package app.freerouting.gui.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.gui.session.GuiBoardManager;
import app.freerouting.rules.Nets;
import app.freerouting.rules.ViaRule;
import java.awt.Graphics;

/**
 * Class for shoving items out of a region to make space to insert something else. For that purpose
 * traces of an invisible net are created temporary for shoving.
 */
public class MakeSpaceState extends DragState {

  private final Route route;

  /** Creates a new instance of MakeSpaceState. */
  public MakeSpaceState(
      FloatPoint location, InteractiveState parentState, GuiBoardManager boardHandling) {
    super(location, parentState, boardHandling);
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
            location.round(),
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
            hdlg.getInteractiveSettings().getHighlightRoutingObstacle());
  }

  @Override
  public InteractiveState moveTo(FloatPoint toLocation) {
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
    route.nextCorner(toLocation);

    Point routeEnd = route.getLastCorner();
    if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction() == AngleRestriction.NONE
        && !routeEnd.equals(toLocation.round())) {
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
  public void draw(Graphics graphics) {
    if (route != null) {
      route.draw(graphics, hdlg.graphicsContext);
    }
  }
}

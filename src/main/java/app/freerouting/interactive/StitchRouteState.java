package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.rules.DefaultItemClearanceClasses;
import app.freerouting.rules.NetClass;
import java.awt.Color;
import java.awt.Graphics;
import javax.swing.JPopupMenu;

/** State for interactive routing by adding corners with the left mouse button. */
public class StitchRouteState extends RouteState {

  /** Creates a new instance of StitchRouteState. */
  protected StitchRouteState(InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    return addCorner(location);
  }

  @Override
  public InteractiveState addCorner(FloatPoint location) {
    // make the current situation restorable by undo
    hdlg.getRoutingBoard().generateSnapshot();
    return super.addCorner(location);
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    this.route.calcNearestTargetPoint(hdlg.getCurrentMousePosition());
    hdlg.repaint();
    return this;
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuStitchRoute;
  }

  @Override
  public String getHelpId() {
    return "RouteState_StitchingRouteState";
  }

  @Override
  public void draw(Graphics graphics) {
    super.draw(graphics);
    if (route == null) {
      return;
    }
    // draw a line from the routing end point to the cursor
    FloatPoint[] drawPoints = new FloatPoint[2];
    drawPoints[0] = route.getLastCorner().toFloat();
    drawPoints[1] = hdlg.getCurrentMousePosition();
    Color drawColor = hdlg.graphicsContext.getHighlightColor();
    double displayWidth =
        hdlg.getTraceHalfwidth(route.netNoArr[0], hdlg.getInteractiveSettings().getLayer());
    int clearanceDrawWidth = 50;
    double radiusWithClearance = displayWidth;
    NetClass defaultNetClass = hdlg.getRoutingBoard().rules.getDefaultNetClass();
    int clClass =
        defaultNetClass.defaultItemClearanceClasses.get(
            DefaultItemClearanceClasses.ItemClass.TRACE);
    radiusWithClearance +=
        hdlg.getRoutingBoard()
            .clearanceValue(clClass, clClass, hdlg.getInteractiveSettings().getLayer());
    hdlg.graphicsContext.draw(drawPoints, displayWidth, drawColor, graphics, 0.5);
    // draw the clearance boundary around the end point
    hdlg.graphicsContext.drawCircle(
        drawPoints[1], radiusWithClearance, clearanceDrawWidth, drawColor, graphics, 0.5);
  }
}

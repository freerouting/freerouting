package app.freerouting.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.FixedState;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.rules.BoardRules;
import java.awt.Color;
import java.awt.Graphics;
import javax.swing.JPopupMenu;

/** Interactive creation of a circle obstacle */
public final class CircleConstructionState extends InteractiveState {

  private final FloatPoint circleCenter;
  private double circleRadius = 0;
  private boolean observersActivated;

  /** Creates a new instance of CircleConstructionState */
  private CircleConstructionState(
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
    circleCenter = pLocation;
  }

  /**
   * Returns a new instance of this class. If p_logfile != null; the creation of this item is stored
   * in a logfile
   */
  public static CircleConstructionState getInstance(
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    pBoardHandling.removeRatsnest(); // inserting a circle may change the connectivity.
    return new CircleConstructionState(pLocation, pParentState, pBoardHandling);
  }

  @Override
  public InteractiveState leftButtonClicked(FloatPoint pLocation) {
    return this.complete();
  }

  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    hdlg.repaint();
    return this;
  }

  /** completes the circle construction state */
  @Override
  public InteractiveState complete() {
    IntPoint center = this.circleCenter.round();
    int radius = (int) Math.round(this.circleRadius);
    int layer = hdlg.getInteractiveSettings().getLayer();
    int clClass;
    RoutingBoard board = hdlg.getRoutingBoard();
    clClass = BoardRules.clearanceClassNone();
    boolean constructionSucceeded = this.circleRadius > 0;
    ConvexShape obstacleShape = null;
    if (constructionSucceeded) {

      obstacleShape = new Circle(center, radius);
      if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
          == AngleRestriction.NINETY_DEGREE) {
        obstacleShape = obstacleShape.boundingBox();
      } else if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
          == AngleRestriction.FORTYFIVE_DEGREE) {
        obstacleShape = obstacleShape.boundingOctagon();
      }
      constructionSucceeded = board.checkShape(obstacleShape, layer, new int[0], clClass);
    }
    if (constructionSucceeded) {
      hdlg.screenMessages.setStatusMessage(tm.getText("keepout_successful_completed"));

      // insert the new shape as keepout
      this.observersActivated = !hdlg.getRoutingBoard().observersActive();
      if (this.observersActivated) {
        hdlg.getRoutingBoard().startNotifyObservers();
      }
      board.generateSnapshot();
      board.insertObstacle(obstacleShape, layer, clClass, FixedState.UNFIXED);
      if (this.observersActivated) {
        hdlg.getRoutingBoard().endNotifyObservers();
        this.observersActivated = false;
      }
    } else {
      hdlg.screenMessages.setStatusMessage(tm.getText("keepout_cancelled_because_of_overlaps"));
    }
    hdlg.repaint();
    return this.returnState;
  }

  /**
   * Used when reading the next point from a logfile. Calls complete, because only 1 additional
   * point is stored in the logfile.
   */

  /** draws the graphic construction aid for the circle */
  @Override
  public void draw(Graphics pGraphics) {
    FloatPoint currentMousePosition = hdlg.getCurrentMousePosition();
    if (currentMousePosition == null) {
      return;
    }
    this.circleRadius = circleCenter.distance(currentMousePosition);
    hdlg.graphicsContext.drawCircle(circleCenter, circleRadius, 300, Color.white, pGraphics, 1);
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuInsertCancel;
  }

  @Override
  public void displayDefaultMessage() {
    hdlg.screenMessages.setStatusMessage(tm.getText("creating_circle"));
  }
}

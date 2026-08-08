package app.freerouting.interactive;

import app.freerouting.board.FixedState;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.rules.BoardRules;
import java.util.Iterator;

/** Interactive state for constructing an obstacle with a polygon shape. */
public final class PolygonShapeConstructionState extends CornerItemConstructionState {

  /** Creates a new instance of PolygonShapeConstructionState */
  private PolygonShapeConstructionState(
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
    this.addCorner(pLocation);
  }

  /**
   * Returns a new instance of this class If p_logfile != null; the creation of this item is stored
   * in a logfile
   */
  public static PolygonShapeConstructionState getInstance(
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    return new PolygonShapeConstructionState(pLocation, pParentState, pBoardHandling);
  }

  /** Inserts the polygon shape item into the board, if possible and returns to the main state */
  @Override
  public InteractiveState complete() {
    addCornerForSnapAngle();
    int cornerCount = cornerList.size();
    boolean constructionSucceeded = cornerCount > 2;
    if (constructionSucceeded) {
      IntPoint[] cornerArr = new IntPoint[cornerCount];
      Iterator<IntPoint> it = cornerList.iterator();
      for (int i = 0; i < cornerCount; i++) {
        cornerArr[i] = it.next();
      }
      PolygonShape obstacleShape = new PolygonShape(cornerArr);
      int clClass = BoardRules.clearanceClassNone();
      if (obstacleShape.splitToConvex() == null) {
        // shape is invalid, maybe it has selfintersections
        constructionSucceeded = false;
      } else {
        constructionSucceeded =
            hdlg.getRoutingBoard()
                .checkShape(
                    obstacleShape, hdlg.getInteractiveSettings().getLayer(), new int[0], clClass);
      }
      if (constructionSucceeded) {
        this.observersActivated = !hdlg.getRoutingBoard().observersActive();
        if (this.observersActivated) {
          hdlg.getRoutingBoard().startNotifyObservers();
        }
        hdlg.getRoutingBoard().generateSnapshot();
        hdlg.getRoutingBoard()
            .insertObstacle(
                obstacleShape,
                hdlg.getInteractiveSettings().getLayer(),
                clClass,
                FixedState.UNFIXED);
        hdlg.getRoutingBoard().endNotifyObservers();
        if (this.observersActivated) {
          hdlg.getRoutingBoard().endNotifyObservers();
          this.observersActivated = false;
        }
      }
    }
    if (constructionSucceeded) {
      hdlg.screenMessages.setStatusMessage(tm.getText("keepout_successful_completed"));
    } else {
      hdlg.screenMessages.setStatusMessage(tm.getText("keepout_cancelled_because_of_overlaps"));
    }
    return this.returnState;
  }

  @Override
  public void displayDefaultMessage() {
    hdlg.screenMessages.setStatusMessage(tm.getText("creating_polygonshape"));
  }
}

package app.freerouting.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Iterator;
import java.util.LinkedList;
import javax.swing.JPopupMenu;

/** Common class for constructing an obstacle with a polygonal shape. */
public class CornerItemConstructionState extends InteractiveState {

  /** stored corners of the shape of the item under construction */
  protected LinkedList<IntPoint> cornerList = new LinkedList<>();

  protected FloatPoint snappedMousePosition;
  protected boolean observersActivated;

  /** Creates a new instance of CornerItemConstructionState */
  protected CornerItemConstructionState(
      InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
    pBoardHandling.removeRatsnest(); // Constructing an item may change the connectivity.
  }

  /** adds a corner to the polygon of the item under construction */
  @Override
  public InteractiveState leftButtonClicked(FloatPoint pLocation) {
    return addCorner(pLocation);
  }

  /** adds a corner to the polygon of the item under construction */
  public InteractiveState addCorner(FloatPoint pLocation) {
    IntPoint location = this.snap(pLocation.round());
    // make sure that the coordinates are integer
    this.cornerList.add(location);
    hdlg.repaint();
    return this;
  }

  /** stores the location of the mouse pointer after snapping it to the snapAngle */
  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    IntPoint currMousePos = hdlg.getCurrentMousePosition().round();
    this.snappedMousePosition = this.snap(currMousePos).toFloat();
    hdlg.repaint();
    return this;
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuCorneritemConstruction;
  }

  /** draws the polygon constructed so far as a visual aid */
  @Override
  public void draw(Graphics pGraphics) {
    int cornerCount = cornerList.size();
    if (this.snappedMousePosition != null) {
      ++cornerCount;
    }
    FloatPoint[] corners = new FloatPoint[cornerCount];
    Iterator<IntPoint> it = cornerList.iterator();
    for (int i = 0; i < corners.length - 1; i++) {
      corners[i] = it.next().toFloat();
    }
    if (this.snappedMousePosition == null) {
      corners[corners.length - 1] = it.next().toFloat();
    } else {
      corners[corners.length - 1] = this.snappedMousePosition;
    }
    hdlg.graphicsContext.draw(corners, 300, Color.white, pGraphics, 0.5);
  }

  /** add a corner to make the last lines fulfil the snap angle restrictions */
  protected void addCornerForSnapAngle() {
    if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction() == AngleRestriction.NONE) {
      return;
    }
    IntPoint firstCorner = cornerList.getFirst();
    IntPoint lastCorner = cornerList.getLast();
    IntPoint addCorner = null;
    if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      addCorner = lastCorner.ninetyDegreeCorner(firstCorner, true);
    } else if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
        == AngleRestriction.FORTYFIVE_DEGREE) {
      addCorner = lastCorner.fortyfiveDegreeCorner(firstCorner, true);
    }
    if (addCorner != null) {
      cornerList.add(addCorner);
    }
  }

  /**
   * snaps the line from the last point in the cornerList to the input point according to
   * this.mouse_snap_angle
   */
  private IntPoint snap(IntPoint pPoint) {
    IntPoint result;
    boolean listEmpty = cornerList.isEmpty();
    if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE
        && !listEmpty) {
      IntPoint lastCorner = cornerList.getLast();
      result = pPoint.orthogonalProjection(lastCorner);
    } else if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
            == AngleRestriction.FORTYFIVE_DEGREE
        && !listEmpty) {
      IntPoint lastCorner = cornerList.getLast();
      result = pPoint.fortyfiveDegreeProjection(lastCorner);
    } else {
      result = pPoint;
    }
    return result;
  }
}

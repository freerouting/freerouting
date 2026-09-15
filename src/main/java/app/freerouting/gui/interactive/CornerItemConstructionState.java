package app.freerouting.gui.interactive;

import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.gui.workspace.GuiBoardManager;
import java.awt.Color;
import java.awt.Graphics;
import java.util.Iterator;
import java.util.LinkedList;
import javax.swing.JPopupMenu;

/** Common class for constructing an obstacle with a polygonal shape. */
public class CornerItemConstructionState extends InteractiveState {

  /** Stores the corners of the shape of the item under construction. */
  protected LinkedList<IntPoint> cornerList = new LinkedList<>();

  protected FloatPoint snappedMousePosition;
  protected boolean observersActivated;

  /** Creates a new instance of CornerItemConstructionState. */
  protected CornerItemConstructionState(
      InteractiveState parentState, GuiBoardManager boardHandling) {
    super(parentState, boardHandling);
    boardHandling.removeRatsnest(); // Constructing an item may change the connectivity.
  }

  /** Adds a corner to the polygon of the item under construction. */
  @Override
  public InteractiveState leftButtonClicked(FloatPoint location) {
    return addCorner(location);
  }

  /** Adds a corner to the polygon of the item under construction. */
  public InteractiveState addCorner(FloatPoint location) {
    IntPoint snappedLocation = this.snap(location.round());
    // make sure that the coordinates are integer
    this.cornerList.add(snappedLocation);
    hdlg.repaint();
    return this;
  }

  /** Stores the mouse pointer location after snapping it to the snap angle. */
  @Override
  public InteractiveState mouseMoved() {
    super.mouseMoved();
    IntPoint currentMousePos = hdlg.getCurrentMousePosition().round();
    this.snappedMousePosition = this.snap(currentMousePos).toFloat();
    hdlg.repaint();
    return this;
  }

  @Override
  public JPopupMenu getPopupMenu() {
    return hdlg.getPanel().popupMenuCorneritemConstruction;
  }

  /** Draws the polygon constructed so far as a visual aid. */
  @Override
  public void draw(Graphics graphics) {
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
    hdlg.graphicsContext.draw(corners, 300, Color.white, graphics, 0.5);
  }

  /** Adds a corner so the last lines fulfil the snap angle restrictions. */
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
   * Snaps the line from the last point in the corner list to the input point according to the mouse
   * snap angle.
   */
  private IntPoint snap(IntPoint point) {
    IntPoint result;
    boolean listEmpty = cornerList.isEmpty();
    if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE
        && !listEmpty) {
      IntPoint lastCorner = cornerList.getLast();
      result = point.orthogonalProjection(lastCorner);
    } else if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
            == AngleRestriction.FORTYFIVE_DEGREE
        && !listEmpty) {
      IntPoint lastCorner = cornerList.getLast();
      result = point.fortyfiveDegreeProjection(lastCorner);
    } else {
      result = point;
    }
    return result;
  }
}

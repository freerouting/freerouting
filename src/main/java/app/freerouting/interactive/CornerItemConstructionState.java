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
      InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    p_board_handling.remove_ratsnest(); // Constructing an item may change the connectivity.
  }

  /** adds a corner to the polygon of the item under construction */
  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return add_corner(p_location);
  }

  /** adds a corner to the polygon of the item under construction */
  public InteractiveState add_corner(FloatPoint p_location) {
    IntPoint location = this.snap(p_location.round());
    // make sure that the coordinates are integer
    this.cornerList.add(location);
    hdlg.repaint();
    return this;
  }

  /** stores the location of the mouse pointer after snapping it to the snapAngle */
  @Override
  public InteractiveState mouse_moved() {
    super.mouse_moved();
    IntPoint currMousePos = hdlg.get_current_mouse_position().round();
    this.snappedMousePosition = this.snap(currMousePos).to_float();
    hdlg.repaint();
    return this;
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popupMenuCorneritemConstruction;
  }

  /** draws the polygon constructed so far as a visual aid */
  @Override
  public void draw(Graphics p_graphics) {
    int cornerCount = cornerList.size();
    if (this.snappedMousePosition != null) {
      ++cornerCount;
    }
    FloatPoint[] corners = new FloatPoint[cornerCount];
    Iterator<IntPoint> it = cornerList.iterator();
    for (int i = 0; i < corners.length - 1; i++) {
      corners[i] = it.next().to_float();
    }
    if (this.snappedMousePosition == null) {
      corners[corners.length - 1] = it.next().to_float();
    } else {
      corners[corners.length - 1] = this.snappedMousePosition;
    }
    hdlg.graphicsContext.draw(corners, 300, Color.white, p_graphics, 0.5);
  }

  /** add a corner to make the last lines fulfil the snap angle restrictions */
  protected void add_corner_for_snap_angle() {
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.NONE) {
      return;
    }
    IntPoint firstCorner = cornerList.getFirst();
    IntPoint lastCorner = cornerList.getLast();
    IntPoint addCorner = null;
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
        == AngleRestriction.NINETY_DEGREE) {
      addCorner = lastCorner.ninety_degree_corner(firstCorner, true);
    } else if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
        == AngleRestriction.FORTYFIVE_DEGREE) {
      addCorner = lastCorner.fortyfive_degree_corner(firstCorner, true);
    }
    if (addCorner != null) {
      cornerList.add(addCorner);
    }
  }

  /**
   * snaps the line from the last point in the cornerList to the input point according to
   * this.mouse_snap_angle
   */
  private IntPoint snap(IntPoint p_point) {
    IntPoint result;
    boolean listEmpty = cornerList.isEmpty();
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
            == AngleRestriction.NINETY_DEGREE
        && !listEmpty) {
      IntPoint lastCorner = cornerList.getLast();
      result = p_point.orthogonal_projection(lastCorner);
    } else if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
            == AngleRestriction.FORTYFIVE_DEGREE
        && !listEmpty) {
      IntPoint lastCorner = cornerList.getLast();
      result = p_point.fortyfive_degree_projection(lastCorner);
    } else {
      result = p_point;
    }
    return result;
  }
}

package app.freerouting.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;

/** Common class for constructing an obstacle with a polygonal shape. */
public class CornerItemConstructionState extends InteractiveState {

  /** stored corners of the shape of the item under construction */
  protected java.util.LinkedList<IntPoint> corner_list = new java.util.LinkedList<IntPoint>();
  protected FloatPoint snapped_mouse_position;
  protected boolean observers_activated = false;

  /** Creates a new instance of CornerItemConstructionState */
  protected CornerItemConstructionState(
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_parent_state, p_board_handling, p_activityReplayFile);
    p_board_handling.remove_ratsnest(); // Constructing an item may change the connectivity.
  }

  /** adds a corner to the polygon of the item under construction */
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return add_corner(p_location);
  }

  /** adds a corner to the polygon of the item under construction */
  public InteractiveState add_corner(FloatPoint p_location) {
    IntPoint location = this.snap(p_location.round());
    // make shure that the coordinates are integer
    this.corner_list.add(location);
    hdlg.repaint();
    if (activityReplayFile != null) {
      activityReplayFile.add_corner(p_location);
    }
    return this;
  }

  public InteractiveState process_logfile_point(FloatPoint p_point) {
    return add_corner(p_point);
  }

  /** stores the location of the mouse pointer after snapping it to the snap_angle */
  public InteractiveState mouse_moved() {
    super.mouse_moved();
    IntPoint curr_mouse_pos = hdlg.get_current_mouse_position().round();
    this.snapped_mouse_position = (this.snap(curr_mouse_pos)).to_float();
    hdlg.repaint();
    return this;
  }

  public javax.swing.JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popup_menu_corneritem_construction;
  }

  /** draws the polygon constructed so far as a visual aid */
  public void draw(java.awt.Graphics p_graphics) {
    int corner_count = corner_list.size();
    if (this.snapped_mouse_position != null) {
      ++corner_count;
    }
    FloatPoint[] corners = new FloatPoint[corner_count];
    java.util.Iterator<IntPoint> it = corner_list.iterator();
    for (int i = 0; i < corners.length - 1; ++i) {
      corners[i] = (it.next()).to_float();
    }
    if (this.snapped_mouse_position == null) {
      corners[corners.length - 1] = it.next().to_float();
    } else {
      corners[corners.length - 1] = this.snapped_mouse_position;
    }
    hdlg.graphics_context.draw(corners, 300, java.awt.Color.white, p_graphics, 0.5);
  }

  /** add a corner to make the last lines fulfil the snap angle restrictions */
  protected void add_corner_for_snap_angle() {
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.NONE) {
      return;
    }
    IntPoint first_corner = corner_list.getFirst();
    IntPoint last_corner = corner_list.getLast();
    IntPoint add_corner = null;
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
        == AngleRestriction.NINETY_DEGREE) {
      add_corner = last_corner.ninety_degree_corner(first_corner, true);
    } else if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
        == AngleRestriction.FORTYFIVE_DEGREE) {
      add_corner = last_corner.fortyfive_degree_corner(first_corner, true);
    }
    if (add_corner != null) {
      corner_list.add(add_corner);
    }
  }

  /**
   * snaps the line from the last point in the corner_list to the input point according to
   * this.mouse_snap_angle
   */
  private IntPoint snap(IntPoint p_point) {
    IntPoint result;
    boolean list_empty = (corner_list.size() == 0);
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
            == AngleRestriction.NINETY_DEGREE
        && !list_empty) {
      IntPoint last_corner = corner_list.getLast();
      result = p_point.orthogonal_projection(last_corner);
    } else if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
            == AngleRestriction.FORTYFIVE_DEGREE
        && !list_empty) {
      IntPoint last_corner = corner_list.getLast();
      result = p_point.fortyfive_degree_projection(last_corner);
    } else {
      result = p_point;
    }
    return result;
  }
}

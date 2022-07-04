package app.freerouting.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.FixedState;
import app.freerouting.board.RoutingBoard;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.rules.BoardRules;

/** Interactive creation of a circle obstacle */
public class CircleConstructionState extends InteractiveState {
  private final FloatPoint circle_center;
  private double circle_radius = 0;
  private boolean observers_activated = false;

  /** Creates a new instance of CircleConstructionState */
  private CircleConstructionState(
      FloatPoint p_location,
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_parent_state, p_board_handling, p_activityReplayFile);
    circle_center = p_location;
    if (this.activityReplayFile != null) {
      activityReplayFile.start_scope(ActivityReplayFileScope.CREATING_CIRCLE, p_location);
    }
  }

  /**
   * Returns a new instance of this class. If p_logfile != null; the creation of this item is stored
   * in a logfile
   */
  public static CircleConstructionState get_instance(
      FloatPoint p_location,
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    p_board_handling.remove_ratsnest(); // inserting a circle may change the connectivity.
    return new CircleConstructionState(
        p_location, p_parent_state, p_board_handling, p_activityReplayFile);
  }

  public InteractiveState left_button_clicked(FloatPoint p_location) {
    if (activityReplayFile != null) {
      activityReplayFile.add_corner(p_location);
    }
    return this.complete();
  }

  public InteractiveState mouse_moved() {
    super.mouse_moved();
    hdlg.repaint();
    return this;
  }

  /** completes the circle construction state */
  public InteractiveState complete() {
    IntPoint center = this.circle_center.round();
    int radius = (int) Math.round(this.circle_radius);
    int layer = hdlg.settings.layer;
    int cl_class;
    RoutingBoard board = hdlg.get_routing_board();
    cl_class = BoardRules.clearance_class_none();
    boolean construction_succeeded = (this.circle_radius > 0);
    ConvexShape obstacle_shape = null;
    if (construction_succeeded) {

      obstacle_shape = new Circle(center, radius);
      if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
          == AngleRestriction.NINETY_DEGREE) {
        obstacle_shape = obstacle_shape.bounding_box();
      } else if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
          == AngleRestriction.FORTYFIVE_DEGREE) {
        obstacle_shape = obstacle_shape.bounding_octagon();
      }
      construction_succeeded = board.check_shape(obstacle_shape, layer, new int[0], cl_class);
    }
    if (construction_succeeded) {
      hdlg.screen_messages.set_status_message(resources.getString("keepout_successful_completed"));

      // insert the new shape as keepout
      this.observers_activated = !hdlg.get_routing_board().observers_active();
      if (this.observers_activated) {
        hdlg.get_routing_board().start_notify_observers();
      }
      board.generate_snapshot();
      board.insert_obstacle(obstacle_shape, layer, cl_class, FixedState.UNFIXED);
      if (this.observers_activated) {
        hdlg.get_routing_board().end_notify_observers();
        this.observers_activated = false;
      }
    } else {
      hdlg.screen_messages.set_status_message(
          resources.getString("keepout_cancelled_because_of_overlaps"));
    }
    if (activityReplayFile != null) {
      activityReplayFile.start_scope(ActivityReplayFileScope.COMPLETE_SCOPE);
    }
    hdlg.repaint();
    return this.return_state;
  }

  /**
   * Used when reading the next point from a logfile. Calls complete, because only 1 additional
   * point is stored in the logfile.
   */
  public InteractiveState process_logfile_point(FloatPoint p_point) {
    this.circle_radius = circle_center.distance(p_point);
    return this;
  }

  /** draws the graphic construction aid for the circle */
  public void draw(java.awt.Graphics p_graphics) {
    FloatPoint current_mouse_position = hdlg.get_current_mouse_position();
    if (current_mouse_position == null) {
      return;
    }
    this.circle_radius = circle_center.distance(current_mouse_position);
    hdlg.graphics_context.draw_circle(
        circle_center, circle_radius, 300, java.awt.Color.white, p_graphics, 1);
  }

  public javax.swing.JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popup_menu_insert_cancel;
  }

  public void display_default_message() {
    hdlg.screen_messages.set_status_message(resources.getString("creating_circle"));
  }
}

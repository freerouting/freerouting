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
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    circleCenter = p_location;
  }

  /**
   * Returns a new instance of this class. If p_logfile != null; the creation of this item is stored
   * in a logfile
   */
  public static CircleConstructionState get_instance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    p_board_handling.remove_ratsnest(); // inserting a circle may change the connectivity.
    return new CircleConstructionState(p_location, p_parent_state, p_board_handling);
  }

  @Override
  public InteractiveState left_button_clicked(FloatPoint p_location) {
    return this.complete();
  }

  @Override
  public InteractiveState mouse_moved() {
    super.mouse_moved();
    hdlg.repaint();
    return this;
  }

  /** completes the circle construction state */
  @Override
  public InteractiveState complete() {
    IntPoint center = this.circleCenter.round();
    int radius = (int) Math.round(this.circleRadius);
    int layer = hdlg.getInteractiveSettings().get_layer();
    int clClass;
    RoutingBoard board = hdlg.get_routing_board();
    clClass = BoardRules.clearance_class_none();
    boolean constructionSucceeded = this.circleRadius > 0;
    ConvexShape obstacleShape = null;
    if (constructionSucceeded) {

      obstacleShape = new Circle(center, radius);
      if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
          == AngleRestriction.NINETY_DEGREE) {
        obstacleShape = obstacleShape.bounding_box();
      } else if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
          == AngleRestriction.FORTYFIVE_DEGREE) {
        obstacleShape = obstacleShape.bounding_octagon();
      }
      constructionSucceeded = board.check_shape(obstacleShape, layer, new int[0], clClass);
    }
    if (constructionSucceeded) {
      hdlg.screenMessages.set_status_message(tm.getText("keepout_successful_completed"));

      // insert the new shape as keepout
      this.observersActivated = !hdlg.get_routing_board().observers_active();
      if (this.observersActivated) {
        hdlg.get_routing_board().start_notify_observers();
      }
      board.generate_snapshot();
      board.insert_obstacle(obstacleShape, layer, clClass, FixedState.UNFIXED);
      if (this.observersActivated) {
        hdlg.get_routing_board().end_notify_observers();
        this.observersActivated = false;
      }
    } else {
      hdlg.screenMessages.set_status_message(tm.getText("keepout_cancelled_because_of_overlaps"));
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
  public void draw(Graphics p_graphics) {
    FloatPoint currentMousePosition = hdlg.get_current_mouse_position();
    if (currentMousePosition == null) {
      return;
    }
    this.circleRadius = circleCenter.distance(currentMousePosition);
    hdlg.graphicsContext.draw_circle(circleCenter, circleRadius, 300, Color.white, p_graphics, 1);
  }

  @Override
  public JPopupMenu get_popup_menu() {
    return hdlg.get_panel().popupMenuInsertCancel;
  }

  @Override
  public void display_default_message() {
    hdlg.screenMessages.set_status_message(tm.getText("creating_circle"));
  }
}

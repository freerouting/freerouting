package app.freerouting.interactive;

import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.rules.BoardRules;
import java.util.Iterator;

/** Interactive state for constructing an obstacle with a polygon shape. */
public class PolygonShapeConstructionState extends CornerItemConstructionState {
  /** Creates a new instance of PolygonShapeConstructionState */
  private PolygonShapeConstructionState(
      FloatPoint p_location,
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_parent_state, p_board_handling, p_activityReplayFile);
    if (this.activityReplayFile != null) {
      activityReplayFile.start_scope(ActivityReplayFileScope.CREATING_POLYGONSHAPE);
    }
    this.add_corner(p_location);
  }

  /**
   * Returns a new instance of this class If p_logfile != null; the creation of this item is stored
   * in a logfile
   */
  public static PolygonShapeConstructionState get_instance(
      FloatPoint p_location,
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    return new PolygonShapeConstructionState(
        p_location, p_parent_state, p_board_handling, p_activityReplayFile);
  }

  /** Inserts the polygon shape item into the board, if possible and returns to the main state */
  public InteractiveState complete() {
    add_corner_for_snap_angle();
    int corner_count = corner_list.size();
    boolean construction_succeeded = (corner_count > 2);
    if (construction_succeeded) {
      IntPoint[] corner_arr = new IntPoint[corner_count];
      Iterator<IntPoint> it = corner_list.iterator();
      for (int i = 0; i < corner_count; ++i) {
        corner_arr[i] = it.next();
      }
      PolygonShape obstacle_shape = new PolygonShape(corner_arr);
      int cl_class = BoardRules.clearance_class_none();
      if (obstacle_shape.split_to_convex() == null) {
        // shape is invalid, maybe it has selfintersections
        construction_succeeded = false;
      } else {
        construction_succeeded =
            hdlg.get_routing_board()
                .check_shape(obstacle_shape, hdlg.settings.layer, new int[0], cl_class);
      }
      if (construction_succeeded) {
        this.observers_activated = !hdlg.get_routing_board().observers_active();
        if (this.observers_activated) {
          hdlg.get_routing_board().start_notify_observers();
        }
        hdlg.get_routing_board().generate_snapshot();
        hdlg.get_routing_board()
            .insert_obstacle(
                obstacle_shape,
                hdlg.settings.layer,
                cl_class,
                app.freerouting.board.FixedState.UNFIXED);
        hdlg.get_routing_board().end_notify_observers();
        if (this.observers_activated) {
          hdlg.get_routing_board().end_notify_observers();
          this.observers_activated = false;
        }
      }
    }
    if (construction_succeeded) {
      hdlg.screen_messages.set_status_message(resources.getString("keepout_successful_completed"));
    } else {
      hdlg.screen_messages.set_status_message(
          resources.getString("keepout_cancelled_because_of_overlaps"));
    }
    if (activityReplayFile != null) {
      activityReplayFile.start_scope(ActivityReplayFileScope.COMPLETE_SCOPE);
    }
    return this.return_state;
  }

  public void display_default_message() {
    hdlg.screen_messages.set_status_message(resources.getString("creating_polygonshape"));
  }
}

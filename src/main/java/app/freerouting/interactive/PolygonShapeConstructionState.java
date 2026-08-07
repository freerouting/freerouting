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
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
    this.add_corner(p_location);
  }

  /**
   * Returns a new instance of this class If p_logfile != null; the creation of this item is stored
   * in a logfile
   */
  public static PolygonShapeConstructionState get_instance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    return new PolygonShapeConstructionState(p_location, p_parent_state, p_board_handling);
  }

  /** Inserts the polygon shape item into the board, if possible and returns to the main state */
  @Override
  public InteractiveState complete() {
    add_corner_for_snap_angle();
    int cornerCount = cornerList.size();
    boolean constructionSucceeded = cornerCount > 2;
    if (constructionSucceeded) {
      IntPoint[] cornerArr = new IntPoint[cornerCount];
      Iterator<IntPoint> it = cornerList.iterator();
      for (int i = 0; i < cornerCount; i++) {
        cornerArr[i] = it.next();
      }
      PolygonShape obstacleShape = new PolygonShape(cornerArr);
      int clClass = BoardRules.clearance_class_none();
      if (obstacleShape.split_to_convex() == null) {
        // shape is invalid, maybe it has selfintersections
        constructionSucceeded = false;
      } else {
        constructionSucceeded =
            hdlg.get_routing_board()
                .check_shape(
                    obstacleShape, hdlg.getInteractiveSettings().get_layer(), new int[0], clClass);
      }
      if (constructionSucceeded) {
        this.observersActivated = !hdlg.get_routing_board().observers_active();
        if (this.observersActivated) {
          hdlg.get_routing_board().start_notify_observers();
        }
        hdlg.get_routing_board().generate_snapshot();
        hdlg.get_routing_board()
            .insert_obstacle(
                obstacleShape,
                hdlg.getInteractiveSettings().get_layer(),
                clClass,
                FixedState.UNFIXED);
        hdlg.get_routing_board().end_notify_observers();
        if (this.observersActivated) {
          hdlg.get_routing_board().end_notify_observers();
          this.observersActivated = false;
        }
      }
    }
    if (constructionSucceeded) {
      hdlg.screenMessages.set_status_message(tm.getText("keepout_successful_completed"));
    } else {
      hdlg.screenMessages.set_status_message(tm.getText("keepout_cancelled_because_of_overlaps"));
    }
    return this.returnState;
  }

  @Override
  public void display_default_message() {
    hdlg.screenMessages.set_status_message(tm.getText("creating_polygonshape"));
  }
}

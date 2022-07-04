package app.freerouting.interactive;

import app.freerouting.board.ItemSelectionFilter;
import app.freerouting.board.ObstacleArea;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.PolylineArea;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.Shape;
import java.util.Iterator;

/** Interactive cutting a hole into an obstacle shape */
public class HoleConstructionState extends CornerItemConstructionState {
  private ObstacleArea item_to_modify = null;

  /** Creates a new instance of HoleConstructionState */
  private HoleConstructionState(
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_parent_state, p_board_handling, p_activityReplayFile);
  }

  /**
   * Returns a new instance of this class or null, if that was not possible with the input
   * parameters. If p_logfile != null, the construction of this hole is stored in a logfile.
   */
  public static HoleConstructionState get_instance(
      FloatPoint p_location,
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    HoleConstructionState new_instance =
        new HoleConstructionState(p_parent_state, p_board_handling, p_activityReplayFile);
    if (!new_instance.start_ok(p_location)) {
      new_instance = null;
    }
    return new_instance;
  }

  /** Looks for an obstacle area to modify Returns false, if it cannot find one. */
  private boolean start_ok(FloatPoint p_location) {
    IntPoint pick_location = p_location.round();
    ItemSelectionFilter.SelectableChoices[] selectable_choices = {
      ItemSelectionFilter.SelectableChoices.KEEPOUT,
      ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT,
      ItemSelectionFilter.SelectableChoices.CONDUCTION
    };
    ItemSelectionFilter selection_filter = new ItemSelectionFilter(selectable_choices);
    java.util.Collection<app.freerouting.board.Item> found_items =
        hdlg.get_routing_board().pick_items(pick_location, hdlg.settings.layer, selection_filter);
    if (found_items.size() != 1) {
      hdlg.screen_messages.set_status_message(resources.getString("no_item_found_for_adding_hole"));
      return false;
    }
    app.freerouting.board.Item found_item = found_items.iterator().next();
    if (!(found_item instanceof ObstacleArea)) {
      hdlg.screen_messages.set_status_message(
          resources.getString("no_obstacle_area_found_for_adding_hole"));
      return false;
    }
    this.item_to_modify = (ObstacleArea) found_item;
    if (item_to_modify.get_area() instanceof Circle) {
      hdlg.screen_messages.set_status_message(
          resources.getString("adding_hole_to_circle_not_yet_implemented"));
      return false;
    }
    if (this.activityReplayFile != null) {
      activityReplayFile.start_scope(ActivityReplayFileScope.ADDING_HOLE);
    }
    this.add_corner(p_location);
    return true;
  }

  /** Adds a corner to the polygon of the the hole under construction. */
  public InteractiveState left_button_clicked(FloatPoint p_next_corner) {
    if (item_to_modify == null) {
      return this.return_state;
    }
    if (item_to_modify.get_area().contains(p_next_corner)) {
      super.add_corner(p_next_corner);
      hdlg.repaint();
    }
    return this;
  }

  /**
   * adds the just constructed hole to the item under modification, if that is possible without
   * clearance violations
   */
  public InteractiveState complete() {
    if (item_to_modify == null) {
      return this.return_state;
    }
    add_corner_for_snap_angle();
    int corner_count = corner_list.size();
    boolean construction_succeeded = (corner_count > 2);
    PolylineShape[] new_holes = null;
    PolylineShape new_border = null;
    if (construction_succeeded) {
      Area obs_area = item_to_modify.get_area();
      Shape[] old_holes = obs_area.get_holes();
      new_border = (PolylineShape) obs_area.get_border();
      if (new_border == null) {
        construction_succeeded = false;
      } else {
        new_holes = new PolylineShape[old_holes.length + 1];
        for (int i = 0; i < old_holes.length; ++i) {
          new_holes[i] = (PolylineShape) old_holes[i];
          if (new_holes[i] == null) {
            construction_succeeded = false;
            break;
          }
        }
      }
    }
    if (construction_succeeded) {
      IntPoint[] new_hole_corners = new IntPoint[corner_count];
      Iterator<IntPoint> it = corner_list.iterator();
      for (int i = 0; i < corner_count; ++i) {
        new_hole_corners[i] = it.next();
      }
      new_holes[new_holes.length - 1] = new PolygonShape(new_hole_corners);
      PolylineArea new_obs_area = new PolylineArea(new_border, new_holes);

      if (new_obs_area.split_to_convex() == null) {
        // shape is invalid, maybe it has selfintersections
        construction_succeeded = false;
      } else {
        this.observers_activated = !hdlg.get_routing_board().observers_active();
        if (this.observers_activated) {
          hdlg.get_routing_board().start_notify_observers();
        }
        hdlg.get_routing_board().generate_snapshot();
        hdlg.get_routing_board().remove_item(item_to_modify);
        hdlg.get_routing_board()
            .insert_obstacle(
                new_obs_area,
                item_to_modify.get_layer(),
                item_to_modify.clearance_class_no(),
                app.freerouting.board.FixedState.UNFIXED);
        if (this.observers_activated) {
          hdlg.get_routing_board().end_notify_observers();
          this.observers_activated = false;
        }
      }
    }
    if (construction_succeeded) {
      hdlg.screen_messages.set_status_message(resources.getString("adding_hole_completed"));
    } else {
      hdlg.screen_messages.set_status_message(resources.getString("adding_hole_failed"));
    }
    if (activityReplayFile != null) {
      activityReplayFile.start_scope(ActivityReplayFileScope.COMPLETE_SCOPE);
    }
    return this.return_state;
  }

  public void display_default_message() {
    hdlg.screen_messages.set_status_message(resources.getString("adding_hole_to_obstacle_area"));
  }
}

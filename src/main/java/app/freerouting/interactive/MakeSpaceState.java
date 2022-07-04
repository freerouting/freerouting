package app.freerouting.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;

/**
 * Class for shoving items out of a region to make space to insert something else. For that purpose
 * traces of an invisible net are created temporary for shoving.
 */
public class MakeSpaceState extends DragState {

  private final Route route;

  /** Creates a new instance of MakeSpaceState */
  public MakeSpaceState(
      FloatPoint p_location,
      InteractiveState p_parent_state,
      BoardHandling p_board_handling,
      ActivityReplayFile p_activityReplayFile) {
    super(p_location, p_parent_state, p_board_handling, p_activityReplayFile);
    int[] shove_trace_width_arr = new int[hdlg.get_routing_board().get_layer_count()];
    boolean[] layer_active_arr = new boolean[shove_trace_width_arr.length];
    int shove_trace_width = Math.min(100, hdlg.get_routing_board().get_min_trace_half_width() / 10);
    shove_trace_width = Math.max(shove_trace_width, 5);
    for (int i = 0; i < shove_trace_width_arr.length; ++i) {
      shove_trace_width_arr[i] = shove_trace_width;
      layer_active_arr[i] = true;
    }
    int[] route_net_no_arr = new int[1];
    route_net_no_arr[0] = app.freerouting.rules.Nets.hidden_net_no;
    route =
        new Route(
            p_location.round(),
            hdlg.settings.layer,
            shove_trace_width_arr,
            layer_active_arr,
            route_net_no_arr,
            0,
            app.freerouting.rules.ViaRule.EMPTY,
            true,
            hdlg.settings.trace_pull_tight_region_width,
            hdlg.settings.trace_pull_tight_accuracy,
            null,
            null,
            hdlg.get_routing_board(),
            false,
            false,
            false,
            hdlg.settings.hilight_routing_obstacle);
  }

  public InteractiveState move_to(FloatPoint p_to_location) {
    if (!something_dragged) {
      // initialisitions for the first time dragging
      this.observers_activated = !hdlg.get_routing_board().observers_active();
      if (this.observers_activated) {
        hdlg.get_routing_board().start_notify_observers();
      }
      // make the situation restorable by undo
      hdlg.get_routing_board().generate_snapshot();
      if (activityReplayFile != null) {
        // Delayed till here because otherwise the mouse
        // might have been only clicked for selecting
        // and not pressed for moving.
        activityReplayFile.start_scope(ActivityReplayFileScope.MAKING_SPACE, previous_location);
      }
      something_dragged = true;
    }
    route.next_corner(p_to_location);

    Point route_end = route.get_last_corner();
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.NONE
        && !route_end.equals(p_to_location.round())) {
      hdlg.move_mouse(route_end.to_float());
    }
    hdlg.recalculate_length_violations();
    hdlg.repaint();
    return this;
  }

  public InteractiveState button_released() {
    int delete_net_no = app.freerouting.rules.Nets.hidden_net_no;
    BasicBoard board = hdlg.get_routing_board();
    board.remove_items(board.get_connectable_items(delete_net_no), false);
    if (this.observers_activated) {
      hdlg.get_routing_board().end_notify_observers();
      this.observers_activated = false;
    }
    if (activityReplayFile != null && something_dragged) {
      activityReplayFile.start_scope(ActivityReplayFileScope.COMPLETE_SCOPE);
    }
    hdlg.show_ratsnest();
    return this.return_state;
  }

  public void draw(java.awt.Graphics p_graphics) {
    if (route != null) {
      route.draw(p_graphics, hdlg.graphics_context);
    }
  }
}

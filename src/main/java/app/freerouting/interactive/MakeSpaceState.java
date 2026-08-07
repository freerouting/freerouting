package app.freerouting.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.BasicBoard;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.rules.Nets;
import app.freerouting.rules.ViaRule;
import java.awt.Graphics;

/**
 * Class for shoving items out of a region to make space to insert something else. For that purpose
 * traces of an invisible net are created temporary for shoving.
 */
public class MakeSpaceState extends DragState {

  private final Route route;

  /** Creates a new instance of MakeSpaceState */
  public MakeSpaceState(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_location, p_parent_state, p_board_handling);
    int[] shoveTraceWidthArr = new int[hdlg.get_routing_board().get_layer_count()];
    boolean[] layerActiveArr = new boolean[shoveTraceWidthArr.length];
    int shoveTraceWidth = Math.min(100, hdlg.get_routing_board().get_min_trace_half_width() / 10);
    shoveTraceWidth = Math.max(shoveTraceWidth, 5);
    for (int i = 0; i < shoveTraceWidthArr.length; i++) {
      shoveTraceWidthArr[i] = shoveTraceWidth;
      layerActiveArr[i] = true;
    }
    int[] routeNetNoArr = new int[1];
    routeNetNoArr[0] = Nets.hidden_net_no;
    route =
        new Route(
            p_location.round(),
            hdlg.getInteractiveSettings().get_layer(),
            shoveTraceWidthArr,
            layerActiveArr,
            routeNetNoArr,
            0,
            ViaRule.EMPTY,
            true,
            hdlg.getInteractiveSettings().get_trace_pull_tight_region_width(),
            hdlg.getInteractiveSettings().get_trace_pull_tight_accuracy(),
            null,
            null,
            hdlg.get_routing_board(),
            false,
            false,
            false,
            hdlg.getInteractiveSettings().get_hilight_routing_obstacle());
  }

  @Override
  public InteractiveState move_to(FloatPoint p_to_location) {
    if (!somethingDragged) {
      // initialisations for the first time dragging
      this.observersActivated = !hdlg.get_routing_board().observers_active();
      if (this.observersActivated) {
        hdlg.get_routing_board().start_notify_observers();
      }
      // make the situation restorable by undo
      hdlg.get_routing_board().generate_snapshot();
      somethingDragged = true;
    }
    route.next_corner(p_to_location);

    Point routeEnd = route.get_last_corner();
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction() == AngleRestriction.NONE
        && !routeEnd.equals(p_to_location.round())) {
      hdlg.move_mouse(routeEnd.to_float());
    }
    hdlg.recalculate_length_violations();
    hdlg.repaint();
    return this;
  }

  @Override
  public InteractiveState button_released() {
    int deleteNetNo = Nets.hidden_net_no;
    BasicBoard board = hdlg.get_routing_board();
    board.remove_items(board.get_connectable_items(deleteNetNo));
    if (this.observersActivated) {
      hdlg.get_routing_board().end_notify_observers();
      this.observersActivated = false;
    }
    hdlg.show_ratsnest();
    return this.returnState;
  }

  @Override
  public void draw(Graphics p_graphics) {
    if (route != null) {
      route.draw(p_graphics, hdlg.graphicsContext);
    }
  }
}

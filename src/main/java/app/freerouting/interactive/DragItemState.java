package app.freerouting.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Item;
import app.freerouting.board.MoveComponent;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Vector;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Class for interactive dragging items with the mouse on a routing board */
public class DragItemState extends DragState {

  private final Item itemToMove;

  /** Creates a new instance of MoveItemState */
  protected DragItemState(
      Item p_item_to_move,
      FloatPoint p_location,
      InteractiveState p_parent_state,
      GuiBoardManager p_board_handling) {
    super(p_location, p_parent_state, p_board_handling);
    itemToMove = p_item_to_move;
  }

  @Override
  public void display_default_message() {
    hdlg.screenMessages.set_status_message(tm.getText("dragging_item"));
  }

  /**
   * Moves the items of the group to p_to_location. Return this.returnState, if an error occurred
   * while moving, so that an undo may be necessary.
   */
  @Override
  public InteractiveState move_to(FloatPoint p_to_location) {
    IntPoint toLocation = p_to_location.round();
    IntPoint fromLocation = this.previousLocation.round();
    if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
        == AngleRestriction.NINETY_DEGREE) {
      toLocation = toLocation.orthogonal_projection(fromLocation);
    } else if (hdlg.get_routing_board().rules.get_trace_angle_restriction()
        == AngleRestriction.FORTYFIVE_DEGREE) {
      toLocation = toLocation.fortyfive_degree_projection(fromLocation);
    }
    if (toLocation.equals(fromLocation)) {
      return this;
    }
    if (itemToMove.is_user_fixed()) {
      hdlg.screenMessages.set_status_message("Please unfix item before dragging");
      return this;
    }
    MoveComponent moveComponent = null;
    Vector relCoor = toLocation.difference_by(fromLocation);
    double length = relCoor.length_approx();
    boolean shoveOk = false;
    for (int i = 0; i < 2; i++) {
      moveComponent = new MoveComponent(itemToMove, relCoor, 99, 5);
      if (moveComponent.check()) {
        shoveOk = true;
        break;
      }
      if (i == 0) {
        // reduce evtl. the shove distance to make the check shove function
        // work properly, if more than 1 trace have to be shoved.
        double sampleWidth = 2 * hdlg.get_routing_board().get_min_trace_half_width();
        if (length > sampleWidth) {
          relCoor = relCoor.change_length_approx(sampleWidth);
        }
      }
    }

    if (shoveOk) {
      if (!this.somethingDragged) {
        // initialisations for the first time dragging
        this.observersActivated = !hdlg.get_routing_board().observers_active();
        if (this.observersActivated) {
          hdlg.get_routing_board().start_notify_observers();
        }
        // make the situation restorable by undo
        hdlg.get_routing_board().generate_snapshot();
        this.somethingDragged = true;
      }
      if (!moveComponent.insert(
          hdlg.getInteractiveSettings().get_trace_pull_tight_region_width(),
          hdlg.getInteractiveSettings().get_trace_pull_tight_accuracy())) {
        // an insert error occurred, end the drag state
        return this.returnState;
      }
      hdlg.repaint();
    }
    this.previousLocation = p_to_location; // (IntPoint)this.curr_location.translate_by(relCoor);
    return this;
  }

  @Override
  public InteractiveState button_released() {
    if (this.observersActivated) {
      hdlg.get_routing_board().end_notify_observers();
      this.observersActivated = false;
    }
    if (somethingDragged) {
      // Update the incompletes for the nets of the moved items.
      if (itemToMove.get_component_no() == 0) {
        for (int i = 0; i < itemToMove.net_count(); i++) {
          hdlg.update_ratsnest(itemToMove.get_net_no(i));
        }
      } else {
        Collection<Item> movedItems =
            hdlg.get_routing_board().get_component_items(itemToMove.get_component_no());
        Set<Integer> changedNets = new TreeSet<>();
        for (Item curr_moved_item : movedItems) {
          for (int i = 0; i < curr_moved_item.net_count(); i++) {
            changedNets.add(curr_moved_item.get_net_no(i));
          }
        }
        for (Integer currNetNo : changedNets) {
          hdlg.update_ratsnest(currNetNo);
        }
      }
    } else {
      hdlg.show_ratsnest();
    }
    hdlg.screenMessages.set_status_message("");
    return this.returnState;
  }
}

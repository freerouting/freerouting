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
  public void displayDefaultMessage() {
    hdlg.screenMessages.setStatusMessage(tm.getText("dragging_item"));
  }

  /**
   * Moves the items of the group to p_to_location. Return this.returnState, if an error occurred
   * while moving, so that an undo may be necessary.
   */
  @Override
  public InteractiveState moveTo(FloatPoint p_to_location) {
    IntPoint toLocation = p_to_location.round();
    IntPoint fromLocation = this.previousLocation.round();
    if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
        == AngleRestriction.NINETY_DEGREE) {
      toLocation = toLocation.orthogonalProjection(fromLocation);
    } else if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
        == AngleRestriction.FORTYFIVE_DEGREE) {
      toLocation = toLocation.fortyfiveDegreeProjection(fromLocation);
    }
    if (toLocation.equals(fromLocation)) {
      return this;
    }
    if (itemToMove.isUserFixed()) {
      hdlg.screenMessages.setStatusMessage("Please unfix item before dragging");
      return this;
    }
    MoveComponent moveComponent = null;
    Vector relCoor = toLocation.differenceBy(fromLocation);
    double length = relCoor.lengthApprox();
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
        double sampleWidth = 2 * hdlg.getRoutingBoard().getMinTraceHalfWidth();
        if (length > sampleWidth) {
          relCoor = relCoor.changeLengthApprox(sampleWidth);
        }
      }
    }

    if (shoveOk) {
      if (!this.somethingDragged) {
        // initialisations for the first time dragging
        this.observersActivated = !hdlg.getRoutingBoard().observersActive();
        if (this.observersActivated) {
          hdlg.getRoutingBoard().startNotifyObservers();
        }
        // make the situation restorable by undo
        hdlg.getRoutingBoard().generateSnapshot();
        this.somethingDragged = true;
      }
      if (!moveComponent.insert(
          hdlg.getInteractiveSettings().getTracePullTightRegionWidth(),
          hdlg.getInteractiveSettings().getTracePullTightAccuracy())) {
        // an insert error occurred, end the drag state
        return this.returnState;
      }
      hdlg.repaint();
    }
    this.previousLocation = p_to_location; // (IntPoint)this.curr_location.translate_by(relCoor);
    return this;
  }

  @Override
  public InteractiveState buttonReleased() {
    if (this.observersActivated) {
      hdlg.getRoutingBoard().endNotifyObservers();
      this.observersActivated = false;
    }
    if (somethingDragged) {
      // Update the incompletes for the nets of the moved items.
      if (itemToMove.getComponentNo() == 0) {
        for (int i = 0; i < itemToMove.netCount(); i++) {
          hdlg.updateRatsnest(itemToMove.getNetNo(i));
        }
      } else {
        Collection<Item> movedItems =
            hdlg.getRoutingBoard().getComponentItems(itemToMove.getComponentNo());
        Set<Integer> changedNets = new TreeSet<>();
        for (Item curr_moved_item : movedItems) {
          for (int i = 0; i < curr_moved_item.netCount(); i++) {
            changedNets.add(curr_moved_item.getNetNo(i));
          }
        }
        for (Integer currNetNo : changedNets) {
          hdlg.updateRatsnest(currNetNo);
        }
      }
    } else {
      hdlg.showRatsnest();
    }
    hdlg.screenMessages.setStatusMessage("");
    return this.returnState;
  }
}

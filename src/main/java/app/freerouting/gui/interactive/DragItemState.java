package app.freerouting.gui.interactive;

import app.freerouting.board.AngleRestriction;
import app.freerouting.board.Item;
import app.freerouting.board.MoveComponent;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.gui.session.GuiBoardManager;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Class for interactive dragging items with the mouse on a routing board. */
public class DragItemState extends DragState {

  private final Item itemToMove;

  /** Creates a new instance of DragItemState. */
  protected DragItemState(
      Item itemToMove,
      FloatPoint location,
      InteractiveState parentState,
      GuiBoardManager boardHandling) {
    super(location, parentState, boardHandling);
    this.itemToMove = itemToMove;
  }

  @Override
  public void displayDefaultMessage() {
    hdlg.screenMessages.setStatusMessage(tm.getText("dragging_item"));
  }

  /**
   * Moves the items of the group to the specified location.
   *
   * @return the parent state if an error occurred while moving; otherwise, this state
   */
  @Override
  public InteractiveState moveTo(FloatPoint toLocation) {
    IntPoint roundedLocation = toLocation.round();
    IntPoint fromLocation = this.previousLocation.round();
    if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction() == AngleRestriction.NINETY_DEGREE) {
      roundedLocation = roundedLocation.orthogonalProjection(fromLocation);
    } else if (hdlg.getRoutingBoard().rules.getTraceAngleRestriction()
        == AngleRestriction.FORTYFIVE_DEGREE) {
      roundedLocation = roundedLocation.fortyfiveDegreeProjection(fromLocation);
    }
    if (roundedLocation.equals(fromLocation)) {
      return this;
    }
    if (itemToMove.isUserFixed()) {
      hdlg.screenMessages.setStatusMessage("Please unfix item before dragging");
      return this;
    }
    MoveComponent moveComponent = null;
    Vector relCoor = roundedLocation.differenceBy(fromLocation);
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
    this.previousLocation = toLocation; // (IntPoint)this.curr_location.translate_by(relCoor);
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
        for (Item currMovedItem : movedItems) {
          for (int i = 0; i < currMovedItem.netCount(); i++) {
            changedNets.add(currMovedItem.getNetNo(i));
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

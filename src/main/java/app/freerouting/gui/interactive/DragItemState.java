package app.freerouting.gui.interactive;

import app.freerouting.board.actions.MoveComponent;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.structure.AngleRestriction;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.gui.workspace.GuiBoardManager;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.List;
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
        double minDrillWidth = moveComponent.getMinDrillItemWidthWithTraces();
        if (minDrillWidth > 0 && sampleWidth >= minDrillWidth) {
          sampleWidth = Math.max(1.0, minDrillWidth - 1.0);
        }
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
      Collection<Item> movedItems =
          itemToMove.getComponentId() > 0
              ? hdlg.getRoutingBoard().getComponentItems(itemToMove.getComponentId())
              : List.of(itemToMove);
      IntBox oldBox = hdlg.getRoutingBoard().getBoundingBox(movedItems);

      if (!moveComponent.insert(
          hdlg.getWorkspaceSettings().getTracePullTightRegionWidth(),
          hdlg.getWorkspaceSettings().getTracePullTightAccuracy())) {
        // an insert error occurred, end the drag state
        return this.returnState;
      }

      IntBox newBox = hdlg.getRoutingBoard().getBoundingBox(movedItems);
      IntBox changedBox = oldBox.union(newBox);
      IntBox updateBox = hdlg.getRoutingBoard().getGraphicsUpdateBox();
      if (updateBox != null && !updateBox.isEmpty()) {
        changedBox = changedBox.union(updateBox);
      }
      hdlg.getRoutingBoard().resetGraphicsUpdateBox();

      IntBox offsetBox = changedBox.offset(hdlg.getRoutingBoard().rules.getMaxTraceHalfWidth());
      Rectangle screenRect = hdlg.graphicsContext.coordinateTransform.boardToScreen(offsetBox);
      int padding = 20;
      if (itemToMove.getComponentId() > 0) {
        app.freerouting.board.model.structure.Component comp =
            hdlg.getRoutingBoard().components.get(itemToMove.getComponentId());
        if (comp != null && comp.getPartNumber() != null && !comp.getPartNumber().isEmpty()) {
          padding = Math.max(padding, comp.getPartNumber().length() * 8);
        }
      }
      screenRect.x -= padding;
      screenRect.y -= padding;
      screenRect.width += 2 * padding;
      screenRect.height += 2 * padding;
      hdlg.repaint(screenRect);
      this.previousLocation = this.previousLocation.add(relCoor.toFloat());
    }
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
      if (itemToMove.getComponentId() == 0) {
        for (int i = 0; i < itemToMove.netCount(); i++) {
          hdlg.updateRatsnest(itemToMove.getNetNumber(i));
        }
      } else {
        Collection<Item> movedItems =
            hdlg.getRoutingBoard().getComponentItems(itemToMove.getComponentId());
        Set<Integer> changedNets = new TreeSet<>();
        for (Item currentMovedItem : movedItems) {
          for (int i = 0; i < currentMovedItem.netCount(); i++) {
            changedNets.add(currentMovedItem.getNetNumber(i));
          }
        }
        for (Integer currentNetNumber : changedNets) {
          hdlg.updateRatsnest(currentNetNumber);
        }
      }
      hdlg.getRoutingBoard().resetGraphicsUpdateBox();
    } else {
      hdlg.showRatsnest();
    }
    hdlg.screenMessages.setStatusMessage("");
    hdlg.repaint();
    return this.returnState;
  }
}

package app.freerouting.interactive;

import app.freerouting.board.FixedState;
import app.freerouting.board.Item;
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
import java.util.Collection;
import java.util.Iterator;

/** Interactive cutting a hole into an obstacle shape */
public final class HoleConstructionState extends CornerItemConstructionState {

  private ObstacleArea itemToModify;

  /** Creates a new instance of HoleConstructionState */
  private HoleConstructionState(InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    super(pParentState, pBoardHandling);
  }

  /**
   * Returns a new instance of this class or null, if that was not possible with the input
   * parameters. If p_logfile != null, the construction of this hole is stored in a logfile.
   */
  public static HoleConstructionState getInstance(
      FloatPoint pLocation, InteractiveState pParentState, GuiBoardManager pBoardHandling) {
    HoleConstructionState newInstance = new HoleConstructionState(pParentState, pBoardHandling);
    if (!newInstance.startOk(pLocation)) {
      newInstance = null;
    }
    return newInstance;
  }

  /** Looks for an obstacle area to modify Returns false, if it cannot find one. */
  private boolean startOk(FloatPoint pLocation) {
    IntPoint pickLocation = pLocation.round();
    ItemSelectionFilter.SelectableChoices[] selectableChoices = {
      ItemSelectionFilter.SelectableChoices.KEEPOUT,
      ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT,
      ItemSelectionFilter.SelectableChoices.CONDUCTION
    };
    ItemSelectionFilter selectionFilter = new ItemSelectionFilter(selectableChoices);
    Collection<Item> foundItems =
        hdlg.getRoutingBoard()
            .pickItems(pickLocation, hdlg.getInteractiveSettings().getLayer(), selectionFilter);
    if (foundItems.size() != 1) {
      hdlg.screenMessages.setStatusMessage(tm.getText("no_item_found_for_adding_hole"));
      return false;
    }
    Item foundItem = foundItems.iterator().next();
    if (!(foundItem instanceof ObstacleArea)) {
      hdlg.screenMessages.setStatusMessage(tm.getText("no_obstacle_area_found_for_adding_hole"));
      return false;
    }
    this.itemToModify = (ObstacleArea) foundItem;
    if (itemToModify.getArea() instanceof Circle) {
      hdlg.screenMessages.setStatusMessage(tm.getText("adding_hole_to_circle_not_yet_implemented"));
      return false;
    }
    if (itemToModify.getArea() instanceof Circle) {
      hdlg.screenMessages.setStatusMessage(tm.getText("adding_hole_to_circle_not_yet_implemented"));
      return false;
    }
    this.addCorner(pLocation);
    return true;
  }

  /** Adds a corner to the polygon of the hole under construction. */
  @Override
  public InteractiveState leftButtonClicked(FloatPoint pNextCorner) {
    if (itemToModify == null) {
      return this.returnState;
    }
    if (itemToModify.getArea().contains(pNextCorner)) {
      super.addCorner(pNextCorner);
      hdlg.repaint();
    }
    return this;
  }

  /**
   * adds the just constructed hole to the item under modification, if that is possible without
   * clearance violations
   */
  @Override
  public InteractiveState complete() {
    if (itemToModify == null) {
      return this.returnState;
    }
    addCornerForSnapAngle();
    int cornerCount = cornerList.size();
    boolean constructionSucceeded = cornerCount > 2;
    PolylineShape[] newHoles = null;
    PolylineShape newBorder = null;
    if (constructionSucceeded) {
      Area obsArea = itemToModify.getArea();
      Shape[] oldHoles = obsArea.getHoles();
      newBorder = (PolylineShape) obsArea.getBorder();
      if (newBorder == null) {
        constructionSucceeded = false;
      } else {
        newHoles = new PolylineShape[oldHoles.length + 1];
        for (int i = 0; i < oldHoles.length; i++) {
          newHoles[i] = (PolylineShape) oldHoles[i];
          if (newHoles[i] == null) {
            constructionSucceeded = false;
            break;
          }
        }
      }
    }
    if (constructionSucceeded) {
      IntPoint[] newHoleCorners = new IntPoint[cornerCount];
      Iterator<IntPoint> it = cornerList.iterator();
      for (int i = 0; i < cornerCount; i++) {
        newHoleCorners[i] = it.next();
      }
      newHoles[newHoles.length - 1] = new PolygonShape(newHoleCorners);
      PolylineArea newObsArea = new PolylineArea(newBorder, newHoles);

      if (newObsArea.splitToConvex() == null) {
        // shape is invalid, maybe it has selfintersections
        constructionSucceeded = false;
      } else {
        this.observersActivated = !hdlg.getRoutingBoard().observersActive();
        if (this.observersActivated) {
          hdlg.getRoutingBoard().startNotifyObservers();
        }
        hdlg.getRoutingBoard().generateSnapshot();
        hdlg.getRoutingBoard().removeItem(itemToModify);
        hdlg.getRoutingBoard()
            .insertObstacle(
                newObsArea,
                itemToModify.getLayer(),
                itemToModify.clearanceClassNo(),
                FixedState.UNFIXED);
        if (this.observersActivated) {
          hdlg.getRoutingBoard().endNotifyObservers();
          this.observersActivated = false;
        }
      }
    }
    if (constructionSucceeded) {
      hdlg.screenMessages.setStatusMessage(tm.getText("adding_hole_completed"));
    } else {
      hdlg.screenMessages.setStatusMessage(tm.getText("adding_hole_failed"));
    }
    return this.returnState;
  }

  @Override
  public void displayDefaultMessage() {
    hdlg.screenMessages.setStatusMessage(tm.getText("adding_hole_to_obstacle_area"));
  }
}

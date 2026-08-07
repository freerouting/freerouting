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
  private HoleConstructionState(InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    super(p_parent_state, p_board_handling);
  }

  /**
   * Returns a new instance of this class or null, if that was not possible with the input
   * parameters. If p_logfile != null, the construction of this hole is stored in a logfile.
   */
  public static HoleConstructionState get_instance(
      FloatPoint p_location, InteractiveState p_parent_state, GuiBoardManager p_board_handling) {
    HoleConstructionState newInstance = new HoleConstructionState(p_parent_state, p_board_handling);
    if (!newInstance.start_ok(p_location)) {
      newInstance = null;
    }
    return newInstance;
  }

  /** Looks for an obstacle area to modify Returns false, if it cannot find one. */
  private boolean start_ok(FloatPoint p_location) {
    IntPoint pickLocation = p_location.round();
    ItemSelectionFilter.SelectableChoices[] selectableChoices = {
      ItemSelectionFilter.SelectableChoices.KEEPOUT,
      ItemSelectionFilter.SelectableChoices.VIA_KEEPOUT,
      ItemSelectionFilter.SelectableChoices.CONDUCTION
    };
    ItemSelectionFilter selectionFilter = new ItemSelectionFilter(selectableChoices);
    Collection<Item> foundItems =
        hdlg.get_routing_board()
            .pick_items(pickLocation, hdlg.getInteractiveSettings().get_layer(), selectionFilter);
    if (foundItems.size() != 1) {
      hdlg.screenMessages.set_status_message(tm.getText("no_item_found_for_adding_hole"));
      return false;
    }
    Item foundItem = foundItems.iterator().next();
    if (!(foundItem instanceof ObstacleArea)) {
      hdlg.screenMessages.set_status_message(tm.getText("no_obstacle_area_found_for_adding_hole"));
      return false;
    }
    this.itemToModify = (ObstacleArea) foundItem;
    if (itemToModify.get_area() instanceof Circle) {
      hdlg.screenMessages.set_status_message(
          tm.getText("adding_hole_to_circle_not_yet_implemented"));
      return false;
    }
    if (itemToModify.get_area() instanceof Circle) {
      hdlg.screenMessages.set_status_message(
          tm.getText("adding_hole_to_circle_not_yet_implemented"));
      return false;
    }
    this.add_corner(p_location);
    return true;
  }

  /** Adds a corner to the polygon of the hole under construction. */
  @Override
  public InteractiveState left_button_clicked(FloatPoint p_next_corner) {
    if (itemToModify == null) {
      return this.returnState;
    }
    if (itemToModify.get_area().contains(p_next_corner)) {
      super.add_corner(p_next_corner);
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
    add_corner_for_snap_angle();
    int cornerCount = cornerList.size();
    boolean constructionSucceeded = cornerCount > 2;
    PolylineShape[] newHoles = null;
    PolylineShape newBorder = null;
    if (constructionSucceeded) {
      Area obsArea = itemToModify.get_area();
      Shape[] oldHoles = obsArea.get_holes();
      newBorder = (PolylineShape) obsArea.get_border();
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

      if (newObsArea.split_to_convex() == null) {
        // shape is invalid, maybe it has selfintersections
        constructionSucceeded = false;
      } else {
        this.observersActivated = !hdlg.get_routing_board().observers_active();
        if (this.observersActivated) {
          hdlg.get_routing_board().start_notify_observers();
        }
        hdlg.get_routing_board().generate_snapshot();
        hdlg.get_routing_board().remove_item(itemToModify);
        hdlg.get_routing_board()
            .insert_obstacle(
                newObsArea,
                itemToModify.get_layer(),
                itemToModify.clearance_class_no(),
                FixedState.UNFIXED);
        if (this.observersActivated) {
          hdlg.get_routing_board().end_notify_observers();
          this.observersActivated = false;
        }
      }
    }
    if (constructionSucceeded) {
      hdlg.screenMessages.set_status_message(tm.getText("adding_hole_completed"));
    } else {
      hdlg.screenMessages.set_status_message(tm.getText("adding_hole_failed"));
    }
    return this.returnState;
  }

  @Override
  public void display_default_message() {
    hdlg.screenMessages.set_status_message(tm.getText("adding_hole_to_obstacle_area"));
  }
}

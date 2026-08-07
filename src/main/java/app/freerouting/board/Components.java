package app.freerouting.board;

import app.freerouting.core.Package;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Vector;

/** Contains the lists of components on the board. */
public class Components implements Serializable {

  private final UndoableObjects undoList = new UndoableObjects();
  private final Vector<Component> componentArr = new Vector<>();

  /**
   * If true, components on the back side are rotated before mirroring, else they are mirrored
   * before rotating.
   */
  private boolean flipStyleRotateFirst;

  /**
   * Inserts a component into the list. The items of the component have to be inserted separately
   * into the board. If p_on_front is false, the component will be placed on the back side, and
   * p_package_back is used instead of p_package_front.
   */
  public Component add(
      String p_name,
      Point p_location,
      double p_rotation_in_degree,
      boolean p_on_front,
      Package p_package_front,
      Package p_package_back,
      boolean p_position_fixed,
      String p_part_number) {

    Component newComponent =
        new Component(
            p_name,
            p_location,
            p_rotation_in_degree,
            p_on_front,
            p_package_front,
            p_package_back,
            componentArr.size() + 1,
            p_position_fixed,
            p_part_number);
    componentArr.add(newComponent);
    undoList.insert(newComponent);
    return newComponent;
  }

  /**
   * Adds a component to this object. The items of the component have to be inserted separately into
   * the board. If p_on_front is false, the component will be placed on the back side. The component
   * name is generated internally.
   */
  public Component add(Point p_location, double p_rotation, boolean p_on_front, Package p_package) {
    String componentName = "Component#" + (componentArr.size() + 1);
    return add(
        componentName, p_location, p_rotation, p_on_front, p_package, p_package, false, null);
  }

  /** Returns the component with the input name or null, if no such component exists. */
  public Component get(String p_name) {
    for (Component curr : componentArr) {
      if (curr.name.equals(p_name)) {
        return curr;
      }
    }
    return null;
  }

  /**
   * Returns the component with the input component number or null, if no such component exists.
   * Component numbers are from 1 to component count
   */
  public Component get(int p_component_no) {
    Component result = componentArr.elementAt(p_component_no - 1);
    if (result != null && result.no != p_component_no) {
      FRLogger.warn("Components.get: inconsistent component number");
    }
    return result;
  }

  public int count() {
    return componentArr.size();
  }

  public Iterable<Component> get_all() {
    return componentArr;
  }

  /** Generates a snapshot for the undo algorithm. */
  public void generate_snapshot() {
    this.undoList.generate_snapshot();
  }

  /**
   * Restores the situation at the previous snapshot. Returns false, if no more undo is possible.
   */
  public boolean undo(BoardObservers p_observers) {
    if (!this.undoList.undo(null, null)) {
      return false;
    }
    restore_component_arr_from_undo_list(p_observers);
    return true;
  }

  /** Restores the situation before the last undo. Returns false, if no more redo is possible. */
  public boolean redo(BoardObservers p_observers) {
    if (!this.undoList.redo(null, null)) {
      return false;
    }
    restore_component_arr_from_undo_list(p_observers);
    return true;
  }

  /*
   * Restore the components in componentArr from the undo list.
   */
  private void restore_component_arr_from_undo_list(BoardObservers p_observers) {
    Iterator<UndoableObjects.UndoableObjectNode> it = this.undoList.start_read_object();
    for (; ; ) {
      Component currComponent = (Component) this.undoList.read_object(it);
      if (currComponent == null) {
        break;
      }
      this.componentArr.setElementAt(currComponent, currComponent.no - 1);

      if (p_observers != null) {
        p_observers.notify_moved(currComponent);
      }
    }
  }

  /**
   * Moves the component with number p_component_no. Works contrary to Component.translate_by with
   * the undo algorithm of the board.
   */
  public void move(int p_component_no, app.freerouting.geometry.planar.Vector p_vector) {
    Component currComponent = this.get(p_component_no);
    this.undoList.save_for_undo(currComponent);
    currComponent.translate_by(p_vector);
  }

  /**
   * Turns the component with number p_component_no by p_factor times 90 degree around p_pole. Works
   * contrary to Component.turn_90_degree with the undo algorithm of the board.
   */
  public void turn_90_degree(int p_component_no, int p_factor, IntPoint p_pole) {
    Component currComponent = this.get(p_component_no);
    this.undoList.save_for_undo(currComponent);
    currComponent.turn_90_degree(p_factor, p_pole);
  }

  /**
   * Rotates the component with number p_component_no by p_rotation_in_degree around p_pole. Works
   * contrary to Component.rotate with the undo algorithm of the board.
   */
  public void rotate(int p_component_no, double p_rotation_in_degree, IntPoint p_pole) {
    Component currComponent = this.get(p_component_no);
    this.undoList.save_for_undo(currComponent);
    currComponent.rotate(p_rotation_in_degree, p_pole, flipStyleRotateFirst);
  }

  /**
   * Changes the placement side of the component with number p_component_no and mirrors it at the
   * vertical line through p_pole. Works contrary to Component.change_side the undo algorithm of the
   * board.
   */
  public void change_side(int p_component_no, IntPoint p_pole) {
    Component currComponent = this.get(p_component_no);
    this.undoList.save_for_undo(currComponent);
    currComponent.change_side(p_pole);
  }

  /**
   * If true, components on the back side are rotated before mirroring, else they are mirrored
   * before rotating.
   */
  public boolean get_flip_style_rotate_first() {
    return flipStyleRotateFirst;
  }

  /**
   * If true, components on the back side are rotated before mirroring, else they are mirrored
   * before rotating.
   */
  public void set_flip_style_rotate_first(boolean p_value) {
    flipStyleRotateFirst = p_value;
  }
}

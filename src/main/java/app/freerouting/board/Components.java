package app.freerouting.board;

import app.freerouting.core.library.Package;
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
   * into the board. If onFront is false, the component will be placed on the back side, and
   * packageBack is used instead of packageFront.
   */
  public Component add(
      String name,
      Point location,
      double rotationInDegree,
      boolean onFront,
      Package packageFront,
      Package packageBack,
      boolean positionFixed,
      String partNumber) {

    Component newComponent =
        new Component(
            name,
            location,
            rotationInDegree,
            onFront,
            packageFront,
            packageBack,
            componentArr.size() + 1,
            positionFixed,
            partNumber);
    componentArr.add(newComponent);
    undoList.insert(newComponent);
    return newComponent;
  }

  /**
   * Adds a component to this object. The items of the component have to be inserted separately into
   * the board. If onFront is false, the component will be placed on the back side. The component
   * name is generated internally.
   */
  public Component add(Point location, double rotation, boolean onFront, Package componentPackage) {
    String componentName = "Component#" + (componentArr.size() + 1);
    return add(
        componentName,
        location,
        rotation,
        onFront,
        componentPackage,
        componentPackage,
        false,
        null);
  }

  /** Returns the component with the input name or null, if no such component exists. */
  public Component get(String name) {
    for (Component current : componentArr) {
      if (current.name.equals(name)) {
        return current;
      }
    }
    return null;
  }

  /**
   * Returns the component with the input component ID or null, if no such component exists.
   * Component IDs are from 1 to component count.
   */
  public Component get(int componentId) {
    Component result = componentArr.elementAt(componentId - 1);
    if (result != null && result.id != componentId) {
      FRLogger.warn("Components.get: inconsistent component ID");
    }
    return result;
  }

  /** Count. */
  public int count() {
    return componentArr.size();
  }

  public Iterable<Component> getAll() {
    return componentArr;
  }

  /** Generates a snapshot for the undo algorithm. */
  public void generateSnapshot() {
    this.undoList.generateSnapshot();
  }

  /**
   * Restores the situation at the previous snapshot. Returns false, if no more undo is possible.
   */
  public boolean undo(BoardObservers observers) {
    if (!this.undoList.undo(null, null)) {
      return false;
    }
    restoreComponentArrFromUndoList(observers);
    return true;
  }

  /** Restores the situation before the last undo. Returns false, if no more redo is possible. */
  public boolean redo(BoardObservers observers) {
    if (!this.undoList.redo(null, null)) {
      return false;
    }
    restoreComponentArrFromUndoList(observers);
    return true;
  }

  /*
   * Restore the components in componentArr from the undo list.
   */
  private void restoreComponentArrFromUndoList(BoardObservers observers) {
    Iterator<UndoableObjects.UndoableObjectNode> it = this.undoList.startReadObject();
    for (; ; ) {
      Component currentComponent = (Component) this.undoList.readObject(it);
      if (currentComponent == null) {
        break;
      }
      this.componentArr.setElementAt(currentComponent, currentComponent.id - 1);

      if (observers != null) {
        observers.notifyMoved(currentComponent);
      }
    }
  }

  /**
   * Moves the component with ID componentId. Works contrary to Component.translate_by with the undo
   * algorithm of the board.
   */
  public void move(int componentId, app.freerouting.geometry.planar.Vector vector) {
    Component currentComponent = this.get(componentId);
    this.undoList.saveForUndo(currentComponent);
    currentComponent.translateBy(vector);
  }

  /**
   * Turns the component with ID componentId by factor times 90 degree around pole. Works contrary
   * to Component.turn_90_degree with the undo algorithm of the board.
   */
  public void turn90Degree(int componentId, int factor, IntPoint pole) {
    Component currentComponent = this.get(componentId);
    this.undoList.saveForUndo(currentComponent);
    currentComponent.turn90Degree(factor, pole);
  }

  /**
   * Rotates the component with ID componentId by rotationInDegree around pole. Works contrary to
   * Component.rotate with the undo algorithm of the board.
   */
  public void rotate(int componentId, double rotationInDegree, IntPoint pole) {
    Component currentComponent = this.get(componentId);
    this.undoList.saveForUndo(currentComponent);
    currentComponent.rotate(rotationInDegree, pole, flipStyleRotateFirst);
  }

  /**
   * Changes the placement side of the component with ID componentId and mirrors it at the vertical
   * line through pole. Works contrary to Component.change_side the undo algorithm of the board.
   */
  public void changeSide(int componentId, IntPoint pole) {
    Component currentComponent = this.get(componentId);
    this.undoList.saveForUndo(currentComponent);
    currentComponent.changeSide(pole);
  }

  /**
   * If true, components on the back side are rotated before mirroring, else they are mirrored
   * before rotating.
   */
  public boolean getFlipStyleRotateFirst() {
    return flipStyleRotateFirst;
  }

  /**
   * If true, components on the back side are rotated before mirroring, else they are mirrored
   * before rotating.
   */
  public void setFlipStyleRotateFirst(boolean value) {
    flipStyleRotateFirst = value;
  }
}

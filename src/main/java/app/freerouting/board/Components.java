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
      String pName,
      Point pLocation,
      double pRotationInDegree,
      boolean pOnFront,
      Package pPackageFront,
      Package pPackageBack,
      boolean pPositionFixed,
      String pPartNumber) {

    Component newComponent =
        new Component(
            pName,
            pLocation,
            pRotationInDegree,
            pOnFront,
            pPackageFront,
            pPackageBack,
            componentArr.size() + 1,
            pPositionFixed,
            pPartNumber);
    componentArr.add(newComponent);
    undoList.insert(newComponent);
    return newComponent;
  }

  /**
   * Adds a component to this object. The items of the component have to be inserted separately into
   * the board. If p_on_front is false, the component will be placed on the back side. The component
   * name is generated internally.
   */
  public Component add(Point pLocation, double pRotation, boolean pOnFront, Package pPackage) {
    String componentName = "Component#" + (componentArr.size() + 1);
    return add(componentName, pLocation, pRotation, pOnFront, pPackage, pPackage, false, null);
  }

  /** Returns the component with the input name or null, if no such component exists. */
  public Component get(String pName) {
    for (Component curr : componentArr) {
      if (curr.name.equals(pName)) {
        return curr;
      }
    }
    return null;
  }

  /**
   * Returns the component with the input component number or null, if no such component exists.
   * Component numbers are from 1 to component count
   */
  public Component get(int pComponentNo) {
    Component result = componentArr.elementAt(pComponentNo - 1);
    if (result != null && result.no != pComponentNo) {
      FRLogger.warn("Components.get: inconsistent component number");
    }
    return result;
  }

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
  public boolean undo(BoardObservers pObservers) {
    if (!this.undoList.undo(null, null)) {
      return false;
    }
    restoreComponentArrFromUndoList(pObservers);
    return true;
  }

  /** Restores the situation before the last undo. Returns false, if no more redo is possible. */
  public boolean redo(BoardObservers pObservers) {
    if (!this.undoList.redo(null, null)) {
      return false;
    }
    restoreComponentArrFromUndoList(pObservers);
    return true;
  }

  /*
   * Restore the components in componentArr from the undo list.
   */
  private void restoreComponentArrFromUndoList(BoardObservers pObservers) {
    Iterator<UndoableObjects.UndoableObjectNode> it = this.undoList.startReadObject();
    for (; ; ) {
      Component currComponent = (Component) this.undoList.readObject(it);
      if (currComponent == null) {
        break;
      }
      this.componentArr.setElementAt(currComponent, currComponent.no - 1);

      if (pObservers != null) {
        pObservers.notifyMoved(currComponent);
      }
    }
  }

  /**
   * Moves the component with number p_component_no. Works contrary to Component.translate_by with
   * the undo algorithm of the board.
   */
  public void move(int pComponentNo, app.freerouting.geometry.planar.Vector pVector) {
    Component currComponent = this.get(pComponentNo);
    this.undoList.saveForUndo(currComponent);
    currComponent.translateBy(pVector);
  }

  /**
   * Turns the component with number p_component_no by p_factor times 90 degree around p_pole. Works
   * contrary to Component.turn_90_degree with the undo algorithm of the board.
   */
  public void turn90Degree(int pComponentNo, int pFactor, IntPoint pPole) {
    Component currComponent = this.get(pComponentNo);
    this.undoList.saveForUndo(currComponent);
    currComponent.turn90Degree(pFactor, pPole);
  }

  /**
   * Rotates the component with number p_component_no by p_rotation_in_degree around p_pole. Works
   * contrary to Component.rotate with the undo algorithm of the board.
   */
  public void rotate(int pComponentNo, double pRotationInDegree, IntPoint pPole) {
    Component currComponent = this.get(pComponentNo);
    this.undoList.saveForUndo(currComponent);
    currComponent.rotate(pRotationInDegree, pPole, flipStyleRotateFirst);
  }

  /**
   * Changes the placement side of the component with number p_component_no and mirrors it at the
   * vertical line through p_pole. Works contrary to Component.change_side the undo algorithm of the
   * board.
   */
  public void changeSide(int pComponentNo, IntPoint pPole) {
    Component currComponent = this.get(pComponentNo);
    this.undoList.saveForUndo(currComponent);
    currComponent.changeSide(pPole);
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
  public void setFlipStyleRotateFirst(boolean pValue) {
    flipStyleRotateFirst = pValue;
  }
}

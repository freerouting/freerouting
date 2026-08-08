package app.freerouting.board;

import app.freerouting.core.LogicalPart;
import app.freerouting.core.Package;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Locale;

/**
 * Describes board components consisting of an array of pins and other stuff like component
 * keepouts.
 */
public class Component
    implements UndoableObjects.Storable, ObjectInfoPanel.Printable, Serializable {

  /** The name of the component. */
  public final String name;

  /** Internal generated unique identification number. */
  public final int no;

  /** If true, the component cannot be moved. */
  public final boolean positionFixed;

  /** The library package of the component if it is placed on the component side. */
  private final Package libPackageFront;

  /** The library package of the component if it is placed on the solder side. */
  private final Package libPackageBack;

  /** The location of the component. */
  private Point location;

  /** The rotation of the library package of the component in degree */
  private double rotationInDegree;

  /** Contains information for gate swapping and pin swapping, if != null */
  private LogicalPart logicalPart;

  /** If false, the component will be placed on the back side of the board. */
  private boolean onFront;

  private final String partNumber;

  /**
   * Creates a new instance of Component with the input parameters. If p_on_front is false, the
   * component will be placed on the back side.
   */
  Component(
      String pName,
      Point pLocation,
      double pRotationInDegree,
      boolean pOnFront,
      Package pPackageFront,
      Package pPackageBack,
      int pNo,
      boolean pPositionFixed,
      String pPartNumber) {
    name = pName;
    location = pLocation;
    rotationInDegree = pRotationInDegree;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    onFront = pOnFront;
    libPackageFront = pPackageFront;
    libPackageBack = pPackageBack;
    no = pNo;
    positionFixed = pPositionFixed;
    partNumber = pPartNumber;
  }

  /** Returns the location of this component. */
  public Point getLocation() {
    return location;
  }

  /** Returns the rotation of this component in degree. */
  public double getRotationInDegree() {
    return rotationInDegree;
  }

  public boolean isPlaced() {
    return location != null;
  }

  /** If false, the component will be placed on the back side of the board. */
  public boolean placedOnFront() {
    return this.onFront;
  }

  /**
   * Translates the location of this Component by p_p_vector. The Pins in the board must be moved
   * separately.
   */
  public void translateBy(Vector pVector) {
    if (location != null) {
      location = location.translateBy(pVector);
    }
  }

  /** Turns this component by p_factor times 90 degree around p_pole. */
  public void turn90Degree(int pFactor, IntPoint pPole) {
    if (pFactor == 0) {
      return;
    }
    this.rotationInDegree = this.rotationInDegree + pFactor * 90;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    if (location != null) {
      this.location = this.location.turn90Degree(pFactor, pPole);
    }
  }

  /** Rotates this component by p_angle_in_degree around p_pole. */
  public void rotate(double pAngleInDegree, IntPoint pPole, boolean pFlipStyleRotateFirst) {
    if (pAngleInDegree == 0) {
      return;
    }
    double turnAngle = pAngleInDegree;
    if (pFlipStyleRotateFirst && !this.placedOnFront()) {
      // take care of the order of mirroring and rotating on the back side of the board
      turnAngle = 360 - pAngleInDegree;
    }
    this.rotationInDegree = this.rotationInDegree + turnAngle;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    if (location != null) {
      this.location =
          this.location.toFloat().rotate(Math.toRadians(pAngleInDegree), pPole.toFloat()).round();
    }
  }

  /**
   * Changes the placement side of this component and mirrors it at the vertical line through
   * p_pole.
   */
  public void changeSide(IntPoint pPole) {
    this.onFront = !this.onFront;
    this.location = this.location.mirrorVertical(pPole);
  }

  /**
   * Compares 2 components by name. Useful for example to display components in alphabetic order.
   */
  @Override
  public int compareTo(Object pOther) {
    if (pOther instanceof Component component) {
      return this.name.compareToIgnoreCase(component.name);
    }
    return 1;
  }

  public String getPartNumber() {
    return this.partNumber;
  }

  /** Creates a copy of this component. */
  @Override
  public Component clone() {
    Component result =
        new Component(
            name,
            location,
            rotationInDegree,
            onFront,
            libPackageFront,
            libPackageBack,
            no,
            positionFixed,
            partNumber);
    result.logicalPart = this.logicalPart;
    return result;
  }

  @Override
  public String toString() {
    return this.name;
  }

  /** Returns information for pin swap and gate swap, if != null. */
  public LogicalPart getLogicalPart() {
    return this.logicalPart;
  }

  /** Sets the information for pin swap and gate swap. */
  public void setLogicalPart(LogicalPart pLogicalPart) {
    this.logicalPart = pLogicalPart;
  }

  @Override
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("component") + " ");
    pWindow.appendBold(this.name);
    if (this.location != null) {
      pWindow.append(" " + tm.getText("at") + " ");
      pWindow.append(this.location.toFloat());

      pWindow.append(", " + tm.getText("rotation") + " ");
      pWindow.appendWithoutTransforming(rotationInDegree);

      if (this.onFront) {
        pWindow.append(", " + tm.getText("front"));
      } else {
        pWindow.append(", " + tm.getText("back"));
      }
    } else {
      pWindow.append(" " + tm.getText("not_yet_placed"));
    }
    pWindow.append(", " + tm.getText("package"));
    Package libPackage = this.getPackage();
    pWindow.append(libPackage.name, tm.getText("package_info"), libPackage);
    if (this.logicalPart != null) {
      pWindow.append(", " + tm.getText("logicalPart") + " ");
      pWindow.append(this.logicalPart.name, tm.getText("logical_part_info"), this.logicalPart);
    }
    pWindow.newline();
  }

  /** Returns the library package of this component. */
  public Package getPackage() {
    Package result;
    if (this.onFront) {
      result = libPackageFront;
    } else {
      result = libPackageBack;
    }
    return result;
  }
}

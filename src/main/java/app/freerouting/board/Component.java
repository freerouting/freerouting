package app.freerouting.board;

import app.freerouting.core.library.LogicalPart;
import app.freerouting.core.library.Package;
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

  private final String partNumber;

  /** The location of the component. */
  private Point location;

  /** The rotation of the library package of the component in degree. */
  private double rotationInDegree;

  /** Contains information for gate swapping and pin swapping, if != null. */
  private LogicalPart logicalPart;

  /** If false, the component will be placed on the back side of the board. */
  private boolean onFront;

  /**
   * Creates a new instance of Component with the input parameters. If p_on_front is false, the
   * component will be placed on the back side.
   */
  Component(
      String name,
      Point location,
      double rotationInDegree,
      boolean onFront,
      Package packageFront,
      Package packageBack,
      int no,
      boolean positionFixed,
      String partNumber) {
    this.name = name;
    this.location = location;
    this.rotationInDegree = rotationInDegree;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    this.onFront = onFront;
    libPackageFront = packageFront;
    libPackageBack = packageBack;
    this.no = no;
    this.positionFixed = positionFixed;
    this.partNumber = partNumber;
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
  public void translateBy(Vector vector) {
    if (location != null) {
      location = location.translateBy(vector);
    }
  }

  /** Turns this component by p_factor times 90 degree around p_pole. */
  public void turn90Degree(int factor, IntPoint pole) {
    if (factor == 0) {
      return;
    }
    this.rotationInDegree = this.rotationInDegree + factor * 90;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    if (location != null) {
      this.location = this.location.turn90Degree(factor, pole);
    }
  }

  /** Rotates this component by p_angle_in_degree around p_pole. */
  public void rotate(double angleInDegree, IntPoint pole, boolean flipStyleRotateFirst) {
    if (angleInDegree == 0) {
      return;
    }
    double turnAngle = angleInDegree;
    if (flipStyleRotateFirst && !this.placedOnFront()) {
      // take care of the order of mirroring and rotating on the back side of the board
      turnAngle = 360 - angleInDegree;
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
          this.location.toFloat().rotate(Math.toRadians(angleInDegree), pole.toFloat()).round();
    }
  }

  /**
   * Changes the placement side of this component and mirrors it at the vertical line through
   * p_pole.
   */
  public void changeSide(IntPoint pole) {
    this.onFront = !this.onFront;
    this.location = this.location.mirrorVertical(pole);
  }

  /**
   * Compares 2 components by name. Useful for example to display components in alphabetic order.
   */
  @Override
  public int compareTo(Object other) {
    if (other instanceof Component component) {
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
  public void setLogicalPart(LogicalPart logicalPart) {
    this.logicalPart = logicalPart;
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("component") + " ");
    window.appendBold(this.name);
    if (this.location != null) {
      window.append(" " + tm.getText("at") + " ");
      window.append(this.location.toFloat());

      window.append(", " + tm.getText("rotation") + " ");
      window.appendWithoutTransforming(rotationInDegree);

      if (this.onFront) {
        window.append(", " + tm.getText("front"));
      } else {
        window.append(", " + tm.getText("back"));
      }
    } else {
      window.append(" " + tm.getText("not_yet_placed"));
    }
    window.append(", " + tm.getText("package"));
    Package libPackage = this.getPackage();
    window.append(libPackage.name, tm.getText("package_info"), libPackage);
    if (this.logicalPart != null) {
      window.append(", " + tm.getText("logicalPart") + " ");
      window.append(this.logicalPart.name, tm.getText("logical_part_info"), this.logicalPart);
    }
    window.newline();
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

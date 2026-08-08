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
      String p_name,
      Point p_location,
      double p_rotation_in_degree,
      boolean p_on_front,
      Package p_package_front,
      Package p_package_back,
      int p_no,
      boolean p_position_fixed,
      String p_part_number) {
    name = p_name;
    location = p_location;
    rotationInDegree = p_rotation_in_degree;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    onFront = p_on_front;
    libPackageFront = p_package_front;
    libPackageBack = p_package_back;
    no = p_no;
    positionFixed = p_position_fixed;
    partNumber = p_part_number;
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
  public void translateBy(Vector p_vector) {
    if (location != null) {
      location = location.translateBy(p_vector);
    }
  }

  /** Turns this component by p_factor times 90 degree around p_pole. */
  public void turn90Degree(int p_factor, IntPoint p_pole) {
    if (p_factor == 0) {
      return;
    }
    this.rotationInDegree = this.rotationInDegree + p_factor * 90;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    if (location != null) {
      this.location = this.location.turn90Degree(p_factor, p_pole);
    }
  }

  /** Rotates this component by p_angle_in_degree around p_pole. */
  public void rotate(double p_angle_in_degree, IntPoint p_pole, boolean p_flip_style_rotate_first) {
    if (p_angle_in_degree == 0) {
      return;
    }
    double turnAngle = p_angle_in_degree;
    if (p_flip_style_rotate_first && !this.placedOnFront()) {
      // take care of the order of mirroring and rotating on the back side of the board
      turnAngle = 360 - p_angle_in_degree;
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
          this.location
              .toFloat()
              .rotate(Math.toRadians(p_angle_in_degree), p_pole.toFloat())
              .round();
    }
  }

  /**
   * Changes the placement side of this component and mirrors it at the vertical line through
   * p_pole.
   */
  public void changeSide(IntPoint p_pole) {
    this.onFront = !this.onFront;
    this.location = this.location.mirrorVertical(p_pole);
  }

  /**
   * Compares 2 components by name. Useful for example to display components in alphabetic order.
   */
  @Override
  public int compareTo(Object p_other) {
    if (p_other instanceof Component component) {
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
  public void setLogicalPart(LogicalPart p_logical_part) {
    this.logicalPart = p_logical_part;
  }

  @Override
  public void printInfo(ObjectInfoPanel p_window, Locale p_locale) {
    TextManager tm = new TextManager(this.getClass(), p_locale);

    p_window.appendBold(tm.getText("component") + " ");
    p_window.appendBold(this.name);
    if (this.location != null) {
      p_window.append(" " + tm.getText("at") + " ");
      p_window.append(this.location.toFloat());

      p_window.append(", " + tm.getText("rotation") + " ");
      p_window.appendWithoutTransforming(rotationInDegree);

      if (this.onFront) {
        p_window.append(", " + tm.getText("front"));
      } else {
        p_window.append(", " + tm.getText("back"));
      }
    } else {
      p_window.append(" " + tm.getText("not_yet_placed"));
    }
    p_window.append(", " + tm.getText("package"));
    Package libPackage = this.getPackage();
    p_window.append(libPackage.name, tm.getText("package_info"), libPackage);
    if (this.logicalPart != null) {
      p_window.append(", " + tm.getText("logicalPart") + " ");
      p_window.append(this.logicalPart.name, tm.getText("logical_part_info"), this.logicalPart);
    }
    p_window.newline();
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

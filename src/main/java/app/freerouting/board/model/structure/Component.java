package app.freerouting.board.model.structure;

import app.freerouting.board.actions.ItemInfoPrinter;
import app.freerouting.core.library.LogicalPart;
import app.freerouting.core.library.Package;
import app.freerouting.datastructures.UndoableObjects;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.PlacementTransform;
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
    implements UndoableObjects.Storable, ItemInfoPrinter.Printable, Serializable {

  private static final long serialVersionUID = 8656938804017005076L;

  /** The name of the component. */
  public final String name;

  /** Internal generated unique identification number. */
  public final int id;

  /** If true, the component cannot be moved. */
  public final boolean positionFixed;

  /** The library package of the component if it is placed on the component side. */
  private final Package libPackageFront;

  /** The library package of the component if it is placed on the solder side. */
  private final Package libPackageBack;

  private final String partNumber;

  /** The integer location of the component (for serialization compatibility and integer grid). */
  private Point location;

  /** The high-precision location of the component. */
  private FloatPoint exactLocation;

  /** The rotation of the library package of the component in degree. */
  private double rotationInDegree;

  /** Contains information for gate swapping and pin swapping, if != null. */
  private LogicalPart logicalPart;

  /** If false, the component will be placed on the back side of the board. */
  private boolean onFront;

  /**
   * Creates a new instance of Component with exact location. If onFront is false, the component
   * will be placed on the back side.
   */
  Component(
      String name,
      FloatPoint exactLocation,
      double rotationInDegree,
      boolean onFront,
      Package packageFront,
      Package packageBack,
      int id,
      boolean positionFixed,
      String partNumber) {
    this.name = name;
    this.exactLocation = exactLocation;
    this.location = exactLocation == null ? null : exactLocation.round();
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
    this.id = id;
    this.positionFixed = positionFixed;
    this.partNumber = partNumber;
  }

  /**
   * Creates a new instance of Component with integer location. If onFront is false, the component
   * will be placed on the back side.
   */
  Component(
      String name,
      Point location,
      double rotationInDegree,
      boolean onFront,
      Package packageFront,
      Package packageBack,
      int id,
      boolean positionFixed,
      String partNumber) {
    this(
        name,
        location == null ? null : location.toFloat(),
        rotationInDegree,
        onFront,
        packageFront,
        packageBack,
        id,
        positionFixed,
        partNumber);
  }

  @java.io.Serial
  private void readObject(java.io.ObjectInputStream stream)
      throws java.io.IOException, ClassNotFoundException {
    stream.defaultReadObject();
    if (this.exactLocation == null && this.location != null) {
      this.exactLocation = this.location.toFloat();
    } else if (this.location == null && this.exactLocation != null) {
      this.location = this.exactLocation.round();
    }
  }

  /** Returns the location of this component. */
  public Point getLocation() {
    if (exactLocation != null) {
      return exactLocation.round();
    }
    return location;
  }

  /** Returns the high-precision location of this component, or null if unplaced. */
  public FloatPoint getExactLocation() {
    if (exactLocation != null) {
      return exactLocation;
    }
    return location == null ? null : location.toFloat();
  }

  /** Shared placement for pads, outlines and keepouts. */
  public PlacementTransform placementTransform(boolean rotateFirst) {
    return new PlacementTransform(getExactLocation(), rotationInDegree, !onFront, rotateFirst);
  }

  /** Restores a pose after a rejected detached-item transform. */
  public void restorePose(Component original) {
    exactLocation = original.exactLocation;
    location = original.location;
    rotationInDegree = original.rotationInDegree;
    onFront = original.onFront;
  }

  /** Returns the rotation of this component in degree. */
  public double getRotationInDegree() {
    return rotationInDegree;
  }

  public boolean isPlaced() {
    return exactLocation != null || location != null;
  }

  /** If false, the component will be placed on the back side of the board. */
  public boolean placedOnFront() {
    return this.onFront;
  }

  /**
   * Translates the location of this Component by pVector. The Pins in the board must be moved
   * separately.
   */
  public void translateBy(Vector vector) {
    if (exactLocation != null) {
      FloatPoint vf = vector.toFloat();
      exactLocation = new FloatPoint(exactLocation.x + vf.x, exactLocation.y + vf.y);
      location = exactLocation.round();
    } else if (location != null) {
      location = location.translateBy(vector);
      exactLocation = location.toFloat();
    }
  }

  /** Turns this component by factor times 90 degree around pole. */
  public void turn90Degree(int factor, IntPoint pole) {
    turn90Degree(factor, pole, false);
  }

  /** Turns in world coordinates, respecting the board mirror convention. */
  public void turn90Degree(int factor, IntPoint pole, boolean flipStyleRotateFirst) {
    if (factor == 0) {
      return;
    }
    this.rotationInDegree =
        this.rotationInDegree + (flipStyleRotateFirst && !onFront ? -factor : factor) * 90;
    while (this.rotationInDegree >= 360) {
      this.rotationInDegree -= 360;
    }
    while (this.rotationInDegree < 0) {
      this.rotationInDegree += 360;
    }
    if (exactLocation != null) {
      this.exactLocation = this.exactLocation.turn90Degree(factor, pole.toFloat());
      this.location = this.exactLocation.round();
    } else if (this.location != null) {
      this.location = this.location.turn90Degree(factor, pole);
      this.exactLocation = this.location.toFloat();
    }
  }

  /** Rotates this component by angleInDegree around pole. */
  public void rotate(double angleInDegree, IntPoint pole, boolean flipStyleRotateFirst) {
    if (angleInDegree == 0) {
      return;
    }
    if (angleInDegree % 90 == 0) {
      turn90Degree((int) (angleInDegree / 90), pole, flipStyleRotateFirst);
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
    if (exactLocation != null) {
      this.exactLocation = this.exactLocation.rotate(Math.toRadians(angleInDegree), pole.toFloat());
      this.location = this.exactLocation.round();
    } else if (this.location != null) {
      this.location =
          this.location.toFloat().rotate(Math.toRadians(angleInDegree), pole.toFloat()).round();
      this.exactLocation = this.location.toFloat();
    }
  }

  /**
   * Changes the placement side of this component and mirrors it at the vertical line through pole.
   */
  public void changeSide(IntPoint pole) {
    changeSide(pole, false);
  }

  /** Reflects the pose in world coordinates, respecting the board mirror convention. */
  public void changeSide(IntPoint pole, boolean flipStyleRotateFirst) {
    if (!flipStyleRotateFirst) {
      rotationInDegree = (360 - rotationInDegree) % 360;
    }
    this.onFront = !this.onFront;
    if (exactLocation != null) {
      this.exactLocation = new FloatPoint(2 * pole.x - this.exactLocation.x, this.exactLocation.y);
      this.location = this.exactLocation.round();
    } else if (this.location != null) {
      this.location = this.location.mirrorVertical(pole);
      this.exactLocation = this.location.toFloat();
    }
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
            exactLocation,
            rotationInDegree,
            onFront,
            libPackageFront,
            libPackageBack,
            id,
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
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("component") + " ");
    printer.appendBold(this.name);
    if (this.exactLocation != null) {
      printer.append(" " + tm.getText("at") + " ");
      printer.append(this.exactLocation);

      printer.append(", " + tm.getText("rotation") + " ");
      printer.appendWithoutTransforming(rotationInDegree);

      if (this.onFront) {
        printer.append(", " + tm.getText("front"));
      } else {
        printer.append(", " + tm.getText("back"));
      }
    } else {
      printer.append(" " + tm.getText("not_yet_placed"));
    }
    printer.append(", " + tm.getText("package"));
    Package libPackage = this.getPackage();
    printer.append(libPackage.name, tm.getText("package_info"), libPackage);
    if (this.logicalPart != null) {
      printer.append(", " + tm.getText("logicalPart") + " ");
      printer.append(this.logicalPart.name, tm.getText("logical_part_info"), this.logicalPart);
    }
    printer.newline();
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

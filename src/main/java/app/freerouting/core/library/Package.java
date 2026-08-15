package app.freerouting.core.library;

import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.Shape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Locale;

/**
 * Component package templates describing the padstacks and relative locations of the package pins,
 * and optional other stuff like an outline package keepouts.
 */
public class Package implements Comparable<Package>, ObjectInfoPanel.Printable, Serializable {

  /** The name of the package. */
  public final String name;

  /** Internally generated package number. */
  public final int no;

  /** The outline of the component, which may be null. */
  public final Shape[] outline;

  public final double[] outlineWidths;
  public final boolean[] outlineIsClosed;
  public final Keepout[] keepoutArr;
  public final Keepout[] viaKeepoutArr;
  public final Keepout[] placeKeepoutArr;

  /** If false, the package is placed on the back side of the board. */
  public final boolean isFront;

  /** The array of pins of this padstack. */
  private final Pin[] pinArr;

  private final Packages packageList;

  /** Creates a new instance of Package. The package list contains this package. */
  public Package(
      String name,
      int no,
      Pin[] pinArr,
      Shape[] outline,
      double[] outlineWidths,
      boolean[] outlineIsClosed,
      Keepout[] keepoutArr,
      Keepout[] viaKeepoutArr,
      Keepout[] placeKeepoutArr,
      boolean isFront,
      Packages packageList) {
    this.name = name;
    this.no = no;
    this.pinArr = pinArr;
    this.outline = outline;
    this.outlineWidths = outlineWidths;
    this.outlineIsClosed = outlineIsClosed;
    this.keepoutArr = keepoutArr;
    this.viaKeepoutArr = viaKeepoutArr;
    this.placeKeepoutArr = placeKeepoutArr;
    this.isFront = isFront;
    this.packageList = packageList;
  }

  /** Compares 2 packages by name. Useful for example to display packages in alphabetic order. */
  @Override
  public int compareTo(Package other) {
    return this.name.compareToIgnoreCase(other.name);
  }

  /** Returns the pin with the input number from this package. */
  public Pin getPin(int no) {
    if (no < 0 || no >= pinArr.length) {
      FRLogger.warn("Package.get_pin: no out of range");
      return null;
    }
    return pinArr[no];
  }

  /**
   * Returns the pin number of the pin with the input name from this package, or -1, if no such pin
   * exists Pin numbers are from 0 to pinCount - 1.
   */
  public int getPinNo(String name) {
    for (int i = 0; i < pinArr.length; i++) {
      if (pinArr[i].name.equals(name)) {
        return i;
      }
    }
    return -1;
  }

  /** Returns the pin count of this package. */
  public int pinCount() {
    return pinArr.length;
  }

  @Override
  public String toString() {
    return this.name;
  }

  @Override
  public void printInfo(ObjectInfoPanel window, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    window.appendBold(tm.getText("package") + " ");
    window.appendBold(this.name);
    for (int i = 0; i < this.pinArr.length; i++) {
      Pin currentPin = this.pinArr[i];
      window.newline();
      window.indent();
      window.append(tm.getText("pin") + " ");
      window.append(currentPin.name);
      window.append(", " + tm.getText("padstack") + " ");
      Padstack currentPadstack = this.packageList.padstackList.get(currentPin.padstackNo);
      window.append(currentPadstack.name, tm.getText("padstack_info"), currentPadstack);
      window.append(" " + tm.getText("at") + " ");
      window.append(currentPin.relativeLocation.toFloat());
      window.append(", " + tm.getText("rotation") + " ");
      window.appendWithoutTransforming(currentPin.rotationInDegree);
    }
    window.newline();
  }

  /** Describes a pin padstack of a package. */
  public static class Pin implements Serializable {

    /** The name of the pin. */
    public final String name;

    /** The number of the padstack mask of the pin. */
    public final int padstackNo;

    /** The location of the pin relative to its package. */
    public final Vector relativeLocation;

    /** The rotation of the pin padstack. */
    public final double rotationInDegree;

    /** Creates a new package pin with the input coordinates relative to the package location. */
    public Pin(String name, int padstackNo, Vector relativeLocation, double rotationInDegree) {
      this.name = name;
      this.padstackNo = padstackNo;
      this.relativeLocation = relativeLocation;
      this.rotationInDegree = rotationInDegree;
    }
  }

  /** Describes a named keepout belonging to a package. */
  public static class Keepout implements Serializable {

    public final String name;
    public final Area area;
    public final int layer;

    /** Creates a keepout with its area and layer. */
    public Keepout(String name, Area area, int layer) {
      this.name = name;
      this.area = area;
      this.layer = layer;
    }
  }
}

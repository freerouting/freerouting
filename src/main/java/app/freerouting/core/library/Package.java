package app.freerouting.core.library;

import app.freerouting.board.ItemInfoPrinter;
import app.freerouting.board.Pin;
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
public class Package implements Comparable<Package>, ItemInfoPrinter.Printable, Serializable {

  /** The name of the package. */
  public final String name;

  /** Internally generated package ID. */
  public final int id;

  /** The outline of the component, which may be null. */
  public final Shape[] outline;

  public final double[] outlineWidths;
  public final boolean[] outlineIsClosed;
  public final Keepout[] keepouts;
  public final Keepout[] viaKeepouts;
  public final Keepout[] placeKeepoutArr;

  /** If false, the package is placed on the back side of the board. */
  public final boolean isFront;

  /** The array of pins of this padstack. */
  private final Pin[] pins;

  private final Packages packageList;

  /** Creates a new instance of Package. The package list contains this package. */
  public Package(
      String name,
      int id,
      Pin[] pins,
      Shape[] outline,
      double[] outlineWidths,
      boolean[] outlineIsClosed,
      Keepout[] keepouts,
      Keepout[] viaKeepouts,
      Keepout[] placeKeepoutArr,
      boolean isFront,
      Packages packageList) {
    this.name = name;
    this.id = id;
    this.pins = pins;
    this.outline = outline;
    this.outlineWidths = outlineWidths;
    this.outlineIsClosed = outlineIsClosed;
    this.keepouts = keepouts;
    this.viaKeepouts = viaKeepouts;
    this.placeKeepoutArr = placeKeepoutArr;
    this.isFront = isFront;
    this.packageList = packageList;
  }

  /** Compares 2 packages by name. Useful for example to display packages in alphabetic order. */
  @Override
  public int compareTo(Package other) {
    return this.name.compareToIgnoreCase(other.name);
  }

  /** Returns the pin with the input index from this package. */
  public Pin getPin(int pinIndex) {
    if (pinIndex < 0 || pinIndex >= pins.length) {
      FRLogger.warn("Package.getPin: pinIndex out of range");
      return null;
    }
    return pins[pinIndex];
  }

  /**
   * Returns the pin number of the pin with the input name from this package, or -1, if no such pin
   * exists Pin numbers are from 0 to pinCount - 1.
   */
  public int getPinIndex(String name) {
    for (int i = 0; i < pins.length; i++) {
      if (pins[i].name.equals(name)) {
        return i;
      }
    }
    return -1;
  }

  /** Returns the pin count of this package. */
  public int pinCount() {
    return pins.length;
  }

  @Override
  public String toString() {
    return this.name;
  }

  @Override
  public void printInfo(ItemInfoPrinter printer, Locale locale) {
    TextManager tm = new TextManager(this.getClass(), locale);

    printer.appendBold(tm.getText("package") + " ");
    printer.appendBold(this.name);
    for (int i = 0; i < this.pins.length; i++) {
      Pin currentPin = this.pins[i];
      printer.newline();
      printer.indent();
      printer.append(tm.getText("pin") + " ");
      printer.append(currentPin.name);
      printer.append(", " + tm.getText("padstack") + " ");
      Padstack currentPadstack = this.packageList.padstackList.get(currentPin.padstackId);
      printer.append(currentPadstack.name, tm.getText("padstack_info"), currentPadstack);
      printer.append(" " + tm.getText("at") + " ");
      printer.append(currentPin.relativeLocation.toFloat());
      printer.append(", " + tm.getText("rotation") + " ");
      printer.appendWithoutTransforming(currentPin.rotationInDegree);
    }
    printer.newline();
  }

  /** Describes a pin padstack of a package. */
  public static class Pin implements Serializable {

    /** The name of the pin. */
    public final String name;

    /** The ID of the padstack mask of the pin. */
    public final int padstackId;

    /** The location of the pin relative to its package. */
    public final Vector relativeLocation;

    /** The rotation of the pin padstack. */
    public final double rotationInDegree;

    /** Creates a new package pin with the input coordinates relative to the package location. */
    public Pin(String name, int padstackId, Vector relativeLocation, double rotationInDegree) {
      this.name = name;
      this.padstackId = padstackId;
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

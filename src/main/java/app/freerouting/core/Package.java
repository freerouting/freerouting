package app.freerouting.core;

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

  /** If false, the package is placed on the back side of the board */
  public final boolean isFront;

  /** The array of pins of this padstack. */
  private final Pin[] pinArr;

  private final Packages packageList;

  /**
   * Creates a new instance of Package. p_package_list is the list of packages containing this
   * package.
   */
  public Package(
      String pName,
      int pNo,
      Pin[] pPinArr,
      Shape[] pOutline,
      double[] pOutlineWidths,
      boolean[] pOutlineIsClosed,
      Keepout[] pKeepoutArr,
      Keepout[] pViaKeepoutArr,
      Keepout[] pPlaceKeepoutArr,
      boolean pIsFront,
      Packages pPackageList) {
    name = pName;
    no = pNo;
    pinArr = pPinArr;
    outline = pOutline;
    outlineWidths = pOutlineWidths;
    outlineIsClosed = pOutlineIsClosed;
    keepoutArr = pKeepoutArr;
    viaKeepoutArr = pViaKeepoutArr;
    placeKeepoutArr = pPlaceKeepoutArr;
    isFront = pIsFront;
    packageList = pPackageList;
  }

  /** Compares 2 packages by name. Useful for example to display packages in alphabetic order. */
  @Override
  public int compareTo(Package pOther) {
    return this.name.compareToIgnoreCase(pOther.name);
  }

  /** Returns the pin with the input number from this package. */
  public Pin getPin(int pNo) {
    if (pNo < 0 || pNo >= pinArr.length) {
      FRLogger.warn("Package.get_pin: p_no out of range");
      return null;
    }
    return pinArr[pNo];
  }

  /**
   * Returns the pin number of the pin with the input name from this package, or -1, if no such pin
   * exists Pin numbers are from 0 to pinCount - 1.
   */
  public int getPinNo(String pName) {
    for (int i = 0; i < pinArr.length; i++) {
      if (pinArr[i].name.equals(pName)) {
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
  public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
    TextManager tm = new TextManager(this.getClass(), pLocale);

    pWindow.appendBold(tm.getText("package") + " ");
    pWindow.appendBold(this.name);
    for (int i = 0; i < this.pinArr.length; i++) {
      Pin currPin = this.pinArr[i];
      pWindow.newline();
      pWindow.indent();
      pWindow.append(tm.getText("pin") + " ");
      pWindow.append(currPin.name);
      pWindow.append(", " + tm.getText("padstack") + " ");
      Padstack currPadstack = this.packageList.padstackList.get(currPin.padstackNo);
      pWindow.append(currPadstack.name, tm.getText("padstack_info"), currPadstack);
      pWindow.append(" " + tm.getText("at") + " ");
      pWindow.append(currPin.relativeLocation.toFloat());
      pWindow.append(", " + tm.getText("rotation") + " ");
      pWindow.appendWithoutTransforming(currPin.rotationInDegree);
    }
    pWindow.newline();
  }

  /** Describes a pin padstack of a package. */
  public static class Pin implements Serializable {

    /** The name of the pin. */
    public final String name;

    /** The number of the padstack mask of the pin. */
    public final int padstackNo;

    /** The location of the pin relative to its package. */
    public final Vector relativeLocation;

    /** the rotation of the pin padstack */
    public final double rotationInDegree;

    /** Creates a new package pin with the input coordinates relative to the package location. */
    public Pin(String pName, int pPadstackNo, Vector pRelativeLocation, double pRotationInDegree) {
      name = pName;
      padstackNo = pPadstackNo;
      relativeLocation = pRelativeLocation;
      rotationInDegree = pRotationInDegree;
    }
  }

  /** Describes a named keepout belonging to a package, */
  public static class Keepout implements Serializable {

    public final String name;
    public final Area area;
    public final int layer;

    public Keepout(String pName, Area pArea, int pLayer) {
      name = pName;
      area = pArea;
      layer = pLayer;
    }
  }
}

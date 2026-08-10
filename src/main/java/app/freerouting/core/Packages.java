package app.freerouting.core;

import app.freerouting.geometry.planar.Shape;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Vector;

/** Describes a library of component packages. */
public class Packages implements Serializable {

  final Padstacks padstackList;

  /** The array of packages in this object. */
  private final Vector<Package> packageArr = new Vector<>();

  /**
   * Creates a new instance of Packages. p_padstack_list is the list of padstacks used for the pins
   * of the packages in this data structure.
   */
  public Packages(Padstacks padstackList) {
    this.padstackList = padstackList;
  }

  /**
   * Returns the package with the input name and the input side or null, if no such package exists.
   */
  public Package get(String name, boolean isFront) {
    if (name == null) {
      return null;
    }
    Package otherSidePackage = null;
    for (Package currPackage : packageArr) {
      if (currPackage != null && currPackage.name.equalsIgnoreCase(name)) {
        if (currPackage.isFront == isFront) {
          return currPackage;
        }
        otherSidePackage = currPackage;
      }
    }
    String baseName = name.replaceAll("::\\d+$", "");
    if (!baseName.equalsIgnoreCase(name)) {
      for (Package currPackage : packageArr) {
        if (currPackage != null && currPackage.name.equalsIgnoreCase(baseName)) {
          if (currPackage.isFront == isFront) {
            return currPackage;
          }
          otherSidePackage = currPackage;
        }
      }
    }
    return otherSidePackage;
  }

  /** Returns the package with the specified index. Package numbers start at 1. */
  public Package get(int packageNo) {
    Package result = packageArr.elementAt(packageNo - 1);
    if (result != null && result.no != packageNo) {
      FRLogger.warn("Padstacks.get: inconsistent padstack number");
    }
    return result;
  }

  /** Returns the count of packages in this object. */
  public int count() {
    return packageArr.size();
  }

  /** Appends a new package with the specified data to this object. */
  public Package add(
      String name,
      Package.Pin[] pinArr,
      Shape[] outline,
      double[] outlineWidths,
      boolean[] outlineIsClosed,
      Package.Keepout[] keepoutArr,
      Package.Keepout[] viaKeepoutArr,
      Package.Keepout[] placeKeepoutArr,
      boolean isFront) {
    Package newPackage =
        new Package(
            name,
            packageArr.size() + 1,
            pinArr,
            outline,
            outlineWidths,
            outlineIsClosed,
            keepoutArr,
            viaKeepoutArr,
            placeKeepoutArr,
            isFront,
            this);
    packageArr.add(newPackage);
    return newPackage;
  }

  /** Appends a new package with the specified pins. The package name is generated internally. */
  public Package add(Package.Pin[] pinArr) {
    String packageName = "Package#" + (packageArr.size() + 1);

    return add(
        packageName,
        pinArr,
        null,
        null,
        null,
        new Package.Keepout[0],
        new Package.Keepout[0],
        new Package.Keepout[0],
        true);
  }
}

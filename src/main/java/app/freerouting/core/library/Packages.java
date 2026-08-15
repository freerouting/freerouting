package app.freerouting.core.library;

import app.freerouting.geometry.planar.Shape;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Vector;

/** Describes a library of component packages. */
public class Packages implements Serializable {

  final Padstacks padstackList;

  /** The array of packages in this object. */
  private final Vector<Package> packages = new Vector<>();

  /**
   * Creates a new instance of Packages. padstackList is the list of padstacks used for the pins of
   * the packages in this data structure.
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
    for (Package currentPackage : packages) {
      if (currentPackage != null && currentPackage.name.equalsIgnoreCase(name)) {
        if (currentPackage.isFront == isFront) {
          return currentPackage;
        }
        otherSidePackage = currentPackage;
      }
    }
    String baseName = name.replaceAll("::\\d+$", "");
    if (!baseName.equalsIgnoreCase(name)) {
      for (Package currentPackage : packages) {
        if (currentPackage != null && currentPackage.name.equalsIgnoreCase(baseName)) {
          if (currentPackage.isFront == isFront) {
            return currentPackage;
          }
          otherSidePackage = currentPackage;
        }
      }
    }
    return otherSidePackage;
  }

  /** Returns the package with the specified index. Package numbers start at 1. */
  public Package get(int packageNo) {
    Package result = packages.elementAt(packageNo - 1);
    if (result != null && result.no != packageNo) {
      FRLogger.warn("Padstacks.get: inconsistent padstack number");
    }
    return result;
  }

  /** Returns the count of packages in this object. */
  public int count() {
    return packages.size();
  }

  /** Appends a new package with the specified data to this object. */
  public Package add(
      String name,
      Package.Pin[] pins,
      Shape[] outline,
      double[] outlineWidths,
      boolean[] outlineIsClosed,
      Package.Keepout[] keepouts,
      Package.Keepout[] viaKeepouts,
      Package.Keepout[] placeKeepoutArr,
      boolean isFront) {
    Package newPackage =
        new Package(
            name,
            packages.size() + 1,
            pins,
            outline,
            outlineWidths,
            outlineIsClosed,
            keepouts,
            viaKeepouts,
            placeKeepoutArr,
            isFront,
            this);
    packages.add(newPackage);
    return newPackage;
  }

  /** Appends a new package with the specified pins. The package name is generated internally. */
  public Package add(Package.Pin[] pins) {
    String packageName = "Package#" + (packages.size() + 1);

    return add(
        packageName,
        pins,
        null,
        null,
        null,
        new Package.Keepout[0],
        new Package.Keepout[0],
        new Package.Keepout[0],
        true);
  }
}

package app.freerouting.core;

import app.freerouting.geometry.planar.Shape;
import app.freerouting.logger.FRLogger;
import java.io.Serializable;
import java.util.Vector;

/** Describes a library of component packages. */
public class Packages implements Serializable {

  final Padstacks padstackList;

  /** The array of packages in this object */
  private final Vector<Package> packageArr = new Vector<>();

  /**
   * Creates a new instance of Packages. p_padstack_list is the list of padstacks used for the pins
   * of the packages in this data structure.
   */
  public Packages(Padstacks pPadstackList) {
    this.padstackList = pPadstackList;
  }

  /**
   * Returns the package with the input name and the input side or null, if no such package exists.
   */
  public Package get(String pName, boolean pIsFront) {
    if (pName == null) {
      return null;
    }
    Package otherSidePackage = null;
    for (Package currPackage : packageArr) {
      if (currPackage != null && currPackage.name.equalsIgnoreCase(pName)) {
        if (currPackage.isFront == pIsFront) {
          return currPackage;
        }
        otherSidePackage = currPackage;
      }
    }
    String baseName = pName.replaceAll("::\\d+$", "");
    if (!baseName.equalsIgnoreCase(pName)) {
      for (Package currPackage : packageArr) {
        if (currPackage != null && currPackage.name.equalsIgnoreCase(baseName)) {
          if (currPackage.isFront == pIsFront) {
            return currPackage;
          }
          otherSidePackage = currPackage;
        }
      }
    }
    return otherSidePackage;
  }

  /** Returns the package with index p_package_no. Packages numbers are from 1 to package count. */
  public Package get(int pPackageNo) {
    Package result = packageArr.elementAt(pPackageNo - 1);
    if (result != null && result.no != pPackageNo) {
      FRLogger.warn("Padstacks.get: inconsistent padstack number");
    }
    return result;
  }

  /** Returns the count of packages in this object. */
  public int count() {
    return packageArr.size();
  }

  /** Appends a new package with the input data to this object. */
  public Package add(
      String pName,
      Package.Pin[] pPinArr,
      Shape[] pOutline,
      double[] pOutlineWidths,
      boolean[] pOutlineIsClosed,
      Package.Keepout[] pKeepoutArr,
      Package.Keepout[] pViaKeepoutArr,
      Package.Keepout[] pPlaceKeepoutArr,
      boolean pIsFront) {
    Package newPackage =
        new Package(
            pName,
            packageArr.size() + 1,
            pPinArr,
            pOutline,
            pOutlineWidths,
            pOutlineIsClosed,
            pKeepoutArr,
            pViaKeepoutArr,
            pPlaceKeepoutArr,
            pIsFront,
            this);
    packageArr.add(newPackage);
    return newPackage;
  }

  /**
   * Appends a new package with pins p_pin_arr to this object. The package name is generated
   * internally.
   */
  public Package add(Package.Pin[] pPinArr) {
    String packageName = "Package#" + (packageArr.size() + 1);

    return add(
        packageName,
        pPinArr,
        null,
        null,
        null,
        new Package.Keepout[0],
        new Package.Keepout[0],
        new Package.Keepout[0],
        true);
  }
}

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
  public Packages(Padstacks p_padstack_list) {
    this.padstackList = p_padstack_list;
  }

  /**
   * Returns the package with the input name and the input side or null, if no such package exists.
   */
  public Package get(String p_name, boolean p_is_front) {
    if (p_name == null) {
      return null;
    }
    Package otherSidePackage = null;
    for (Package currPackage : packageArr) {
      if (currPackage != null && currPackage.name.equalsIgnoreCase(p_name)) {
        if (currPackage.isFront == p_is_front) {
          return currPackage;
        }
        otherSidePackage = currPackage;
      }
    }
    String baseName = p_name.replaceAll("::\\d+$", "");
    if (!baseName.equalsIgnoreCase(p_name)) {
      for (Package currPackage : packageArr) {
        if (currPackage != null && currPackage.name.equalsIgnoreCase(baseName)) {
          if (currPackage.isFront == p_is_front) {
            return currPackage;
          }
          otherSidePackage = currPackage;
        }
      }
    }
    return otherSidePackage;
  }

  /** Returns the package with index p_package_no. Packages numbers are from 1 to package count. */
  public Package get(int p_package_no) {
    Package result = packageArr.elementAt(p_package_no - 1);
    if (result != null && result.no != p_package_no) {
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
      String p_name,
      Package.Pin[] p_pin_arr,
      Shape[] p_outline,
      double[] p_outline_widths,
      boolean[] p_outline_is_closed,
      Package.Keepout[] p_keepout_arr,
      Package.Keepout[] p_via_keepout_arr,
      Package.Keepout[] p_place_keepout_arr,
      boolean p_is_front) {
    Package newPackage =
        new Package(
            p_name,
            packageArr.size() + 1,
            p_pin_arr,
            p_outline,
            p_outline_widths,
            p_outline_is_closed,
            p_keepout_arr,
            p_via_keepout_arr,
            p_place_keepout_arr,
            p_is_front,
            this);
    packageArr.add(newPackage);
    return newPackage;
  }

  /**
   * Appends a new package with pins p_pin_arr to this object. The package name is generated
   * internally.
   */
  public Package add(Package.Pin[] p_pin_arr) {
    String packageName = "Package#" + (packageArr.size() + 1);

    return add(
        packageName,
        p_pin_arr,
        null,
        null,
        null,
        new Package.Keepout[0],
        new Package.Keepout[0],
        new Package.Keepout[0],
        true);
  }
}

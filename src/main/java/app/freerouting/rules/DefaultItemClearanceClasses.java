package app.freerouting.rules;

import java.io.Serializable;

/** Stores the default clearance class for each item type. */
public class DefaultItemClearanceClasses implements Serializable {

  private final int[] clearanceClasses;

  /** Creates a new instance of {@code DefaultItemClearanceClasses}. */
  public DefaultItemClearanceClasses() {
    this.clearanceClasses = new int[ItemClass.values().length];
    this.setAll(1);
  }

  /** Creates a copy of the given default clearance classes. */
  public DefaultItemClearanceClasses(DefaultItemClearanceClasses classes) {
    this.clearanceClasses = classes.clearanceClasses.clone();
  }

  /** Returns the number of the default clearance class for the input item class. */
  public int get(ItemClass itemClass) {
    return this.clearanceClasses[itemClass.ordinal()];
  }

  /**
   * Sets the index of the default clearance class of the input item class in the clearance matrix
   * to {@code index}.
   */
  public void set(ItemClass itemClass, int index) {
    this.clearanceClasses[itemClass.ordinal()] = index;
  }

  /** Sets the indices of all default item clearance classes to {@code index}. */
  public void setAll(int index) {
    for (int i = 1; i < this.clearanceClasses.length; i++) {
      this.clearanceClasses[i] = index;
    }
  }

  /** Defines the item classes for which default clearance values are stored. */
  public enum ItemClass {
    NONE,
    TRACE,
    VIA,
    PIN,
    SMD,
    AREA
  }
}

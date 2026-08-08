package app.freerouting.rules;

import java.io.Serializable;

public class DefaultItemClearanceClasses implements Serializable {

  private final int[] arr;

  /** Creates a new instance of DefaultItemClearancesClasses */
  public DefaultItemClearanceClasses() {
    this.arr = new int[ItemClass.values().length];
    this.setAll(1);
  }

  public DefaultItemClearanceClasses(DefaultItemClearanceClasses pClasses) {
    this.arr = pClasses.arr.clone();
  }

  /** Returns the number of the default clearance class for the input item class. */
  public int get(ItemClass pItemClass) {
    return this.arr[pItemClass.ordinal()];
  }

  /**
   * Sets the index of the default clearance class of the input item class in the clearance matrix
   * to p_index.
   */
  public void set(ItemClass pItemClass, int pIndex) {
    this.arr[pItemClass.ordinal()] = pIndex;
  }

  /** Sets the indices of all default item clearance classes to p_index. */
  public void setAll(int pIndex) {
    for (int i = 1; i < this.arr.length; i++) {
      arr[i] = pIndex;
    }
  }

  /**
   * Used in the function get_default_clearance_class to get the default clearance classes for item
   * classes.
   */
  public enum ItemClass {
    NONE,
    TRACE,
    VIA,
    PIN,
    SMD,
    AREA
  }
}

package app.freerouting.rules;

import java.io.Serializable;

public class DefaultItemClearanceClasses implements Serializable {

  private final int[] arr;

  /** Creates a new instance of DefaultItemClearancesClasses */
  public DefaultItemClearanceClasses() {
    this.arr = new int[ItemClass.values().length];
    this.set_all(1);
  }

  public DefaultItemClearanceClasses(DefaultItemClearanceClasses p_classes) {
    this.arr = p_classes.arr.clone();
  }

  /** Returns the number of the default clearance class for the input item class. */
  public int get(ItemClass p_item_class) {
    return this.arr[p_item_class.ordinal()];
  }

  /**
   * Sets the index of the default clearance class of the input item class in the clearance matrix
   * to p_index.
   */
  public void set(ItemClass p_item_class, int p_index) {
    this.arr[p_item_class.ordinal()] = p_index;
  }

  /** Sets the indices of all default item clearance classes to p_index. */
  public void set_all(int p_index) {
    for (int i = 1; i < this.arr.length; ++i) {
      arr[i] = p_index;
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

package app.freerouting.rules;

public class DefaultItemClearanceClasses implements java.io.Serializable {

  private final int[] arr = new int[ItemClass.values().length];

  /** Creates a new instance of DefaultItemClearancesClasses */
  public DefaultItemClearanceClasses() {
    for (int i = 1; i < ItemClass.values().length; ++i) {
      arr[i] = 1;
    }
  }

  public DefaultItemClearanceClasses(DefaultItemClearanceClasses p_classes) {
    for (int i = 1; i < ItemClass.values().length; ++i) {
      arr[i] = p_classes.arr[i];
    }
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
   * Used in the function get_default_clearance_class to get the default claearance classes for item
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

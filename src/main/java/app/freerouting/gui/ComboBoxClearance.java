package app.freerouting.gui;

import app.freerouting.rules.ClearanceMatrix;
import javax.swing.DefaultComboBoxModel;

/** A Combo Box with an item for each clearance class of the board. */
public class ComboBoxClearance extends javax.swing.JComboBox<ComboBoxClearance.ClearanceClass> {

  private ClearanceClass[] class_arr;

  /** Creates a new instance of ClearanceComboBox */
  public ComboBoxClearance(ClearanceMatrix p_clearance_matrix) {
    this.class_arr = new ClearanceClass[p_clearance_matrix.get_class_count()];
    for (int i = 0; i < this.class_arr.length; ++i) {
      this.class_arr[i] = new ClearanceClass(p_clearance_matrix.get_name(i), i);
    }
    this.setModel(new DefaultComboBoxModel<>(this.class_arr));
    this.setSelectedIndex(1);
  }

  /** Adjusts this combo box to p_new_clearance_matrix. */
  public void adjust(ClearanceMatrix p_new_clearance_matrix) {
    int old_index = this.get_selected_class_index();
    this.class_arr = new ClearanceClass[p_new_clearance_matrix.get_class_count()];
    for (int i = 0; i < this.class_arr.length; ++i) {
      this.class_arr[i] = new ClearanceClass(p_new_clearance_matrix.get_name(i), i);
    }
    this.setModel(new javax.swing.DefaultComboBoxModel<>(this.class_arr));
    this.setSelectedIndex(Math.min(old_index, this.class_arr.length - 1));
  }

  /** Returns the index of the selected clearance class in the clearance matrix. */
  public int get_selected_class_index() {
    return ((ClearanceClass) this.getSelectedItem()).index;
  }

  /** Returns the number of clearance classes in this combo box. */
  public int get_class_count() {
    return this.class_arr.length;
  }

  /** Contains the name of a clearance class and its index in the clearance matrix. */
  protected static class ClearanceClass {
    public final String name;
    public final int index;

    public ClearanceClass(String p_name, int p_index) {
      this.name = p_name;
      this.index = p_index;
    }

    public String toString() {
      return name;
    }
  }
}

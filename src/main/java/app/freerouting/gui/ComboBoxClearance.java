package app.freerouting.gui;

import app.freerouting.rules.ClearanceMatrix;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

/** A Combo Box with an item for each clearance class of the board. */
public class ComboBoxClearance extends JComboBox<ComboBoxClearance.ClearanceClass> {

  private ClearanceClass[] classArr;

  /** Creates a new instance of ClearanceComboBox */
  public ComboBoxClearance(ClearanceMatrix pClearanceMatrix) {
    this.classArr = new ClearanceClass[pClearanceMatrix.getClassCount()];
    for (int i = 0; i < this.classArr.length; i++) {
      this.classArr[i] = new ClearanceClass(pClearanceMatrix.getName(i), i);
    }
    this.setModel(new DefaultComboBoxModel<>(this.classArr));
    this.setSelectedIndex(1);
  }

  /** Adjusts this combo box to p_new_clearance_matrix. */
  public void adjust(ClearanceMatrix pNewClearanceMatrix) {
    int oldIndex = this.getSelectedClassIndex();
    this.classArr = new ClearanceClass[pNewClearanceMatrix.getClassCount()];
    for (int i = 0; i < this.classArr.length; i++) {
      this.classArr[i] = new ClearanceClass(pNewClearanceMatrix.getName(i), i);
    }
    this.setModel(new DefaultComboBoxModel<>(this.classArr));
    this.setSelectedIndex(Math.min(oldIndex, this.classArr.length - 1));
  }

  /** Returns the index of the selected clearance class in the clearance matrix. */
  public int getSelectedClassIndex() {
    return ((ClearanceClass) this.getSelectedItem()).index;
  }

  /** Returns the number of clearance classes in this combo box. */
  public int getClassCount() {
    return this.classArr.length;
  }

  /** Contains the name of a clearance class and its index in the clearance matrix. */
  protected static class ClearanceClass {

    public final String name;
    public final int index;

    public ClearanceClass(String pName, int pIndex) {
      this.name = pName;
      this.index = pIndex;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}

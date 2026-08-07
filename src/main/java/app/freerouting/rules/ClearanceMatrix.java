package app.freerouting.rules;

import app.freerouting.board.LayerStructure;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.geometry.planar.Point;
import app.freerouting.logger.FRLogger;
import app.freerouting.util.TextManager;
import java.io.Serializable;
import java.util.Locale;

/**
 * NxN Matrix describing the spacing restrictions between N clearance classes on a fixed set of
 * layers.
 */
public class ClearanceMatrix implements Serializable {

  public static final int clearance_safety_margin = 16;
  private final LayerStructure layerStructure;
  private final int[] maxValueOnLayer; //  maximum clearance value for each layer

  /** count of clearance classes */
  private int classCount;

  private Row[] row; // vector of classCount rows of the clearance matrix

  /**
   * Creates a new instance for p_class_count clearance classes on p_layer_count layers. p_names is
   * an array of dimension p_class_count;
   */
  public ClearanceMatrix(int p_class_count, LayerStructure p_layer_structure, String[] p_name_arr) {
    classCount = Math.max(p_class_count, 1);
    layerStructure = p_layer_structure;
    row = new Row[classCount];
    for (int i = 0; i < classCount; i++) {
      row[i] = new Row(p_name_arr[i]);
    }
    this.maxValueOnLayer = new int[layerStructure.arr.length];
  }

  /**
   * Creates a new instance with the 2 clearance classes "none" and "default" and initializes it
   * with p_default_value.
   */
  public static ClearanceMatrix get_default_instance(
      LayerStructure p_layer_structure, int p_default_value) {
    String[] nameArr = new String[2];
    nameArr[0] = "null";
    nameArr[1] = "default";
    ClearanceMatrix result = new ClearanceMatrix(2, p_layer_structure, nameArr);
    result.set_default_value(p_default_value);
    return result;
  }

  /**
   * Returns the number of the clearance class with the input name, or -1, if no such clearance
   * class exists.
   */
  public int get_no(String p_name) {
    for (int i = 0; i < classCount; i++) {
      if (row[i].name.equalsIgnoreCase(p_name)) {
        return i;
      }
    }
    return -1;
  }

  /** Gets the name of the clearance class with the input number. */
  public String get_name(int p_cl_class) {
    if (p_cl_class < 0 || p_cl_class >= row.length) {
      FRLogger.warn("ClearanceMatrix.get_name: p_cl_class out of range");
      return null;
    }
    return row[p_cl_class].name;
  }

  /**
   * Sets the value of all clearance classes with number {@literal >}= 1 to p_value on all layers.
   */
  public void set_default_value(int p_value) {
    for (int i = 0; i < layerStructure.arr.length; i++) {
      set_default_value(i, p_value);
    }
  }

  /** Sets the value of all clearance classes with number {@literal >}= 1 to p_value on p_layer. */
  public void set_default_value(int p_layer, int p_value) {
    for (int i = 1; i < classCount; i++) {
      for (int j = 1; j < classCount; j++) {
        set_value(i, j, p_layer, p_value);
      }
    }
  }

  /** Sets the value of an entry in the clearance matrix to p_value on all layers. */
  public void set_value(int p_i, int p_j, int p_value) {
    for (int layer = 0; layer < layerStructure.arr.length; layer++) {
      set_value(p_i, p_j, layer, p_value);
    }
  }

  /** Sets the value of an entry in the clearance matrix to p_value on all inner layers. */
  public void set_inner_value(int p_i, int p_j, int p_value) {
    for (int layer = 1; layer < layerStructure.arr.length - 1; layer++) {
      set_value(p_i, p_j, layer, p_value);
    }
  }

  /** Sets the value of an entry in the clearance matrix to p_value. */
  public void set_value(int p_i, int p_j, int p_layer, int p_value) {
    Row currRow = row[p_j];
    MatrixEntry currEntry = currRow.column[p_i];

    // assure, that the clearance value is positive and even, and round it up, if it is odd
    // NOTE: why does it need to be even?
    int value = Math.max(p_value, 0);
    if (value % 2 != 0) {
      if (value == Integer.MAX_VALUE) {
        value--;
      } else {
        value++;
      }
    }

    currEntry.layer[p_layer] = value;
    currRow.maxValue[p_layer] = Math.max(currRow.maxValue[p_layer], value);
    this.maxValueOnLayer[p_layer] = Math.max(this.maxValueOnLayer[p_layer], value);
  }

  /**
   * Gets the required spacing of clearance classes with index p_i and p_j on p_layer. This value
   * will be always an even integer.
   */
  public int get_value(int p_i, int p_j, int p_layer, boolean p_add_safety_margin) {

    if (p_i < 0
        || p_i >= classCount
        || p_j < 0
        || p_j >= classCount
        || p_layer < 0
        || p_layer >= layerStructure.arr.length) {
      FRLogger.trace(
          "ClearanceMatrix.get_value",
          "out_of_bounds",
          "Clearance request out of bounds: class_i="
              + p_i
              + " (max="
              + (classCount - 1)
              + ")"
              + ", class_j="
              + p_j
              + " (max="
              + (classCount - 1)
              + ")"
              + ", layer="
              + p_layer
              + " (max="
              + (layerStructure.arr.length - 1)
              + ")"
              + ", returning 0",
          "Clearance Check",
          new Point[0]);
      return 0;
    }

    int valueFromTheMatrix = row[p_j].column[p_i].layer[p_layer];
    int finalValue =
        p_add_safety_margin ? valueFromTheMatrix + clearance_safety_margin : valueFromTheMatrix;

    FRLogger.trace(
        "ClearanceMatrix.get_value",
        "clearance_retrieved",
        "Clearance value: class_i="
            + p_i
            + " ("
            + (p_i < row.length ? row[p_i].name : "?")
            + ")"
            + ", class_j="
            + p_j
            + " ("
            + (p_j < row.length ? row[p_j].name : "?")
            + ")"
            + ", layer="
            + p_layer
            + " ("
            + (p_layer < layerStructure.arr.length ? layerStructure.arr[p_layer].name : "?")
            + ")"
            + ", base_value="
            + valueFromTheMatrix
            + " ("
            + (valueFromTheMatrix / 10000.0)
            + "mm)"
            + ", safety_margin="
            + (p_add_safety_margin ? clearance_safety_margin : 0)
            + ", finalValue="
            + finalValue
            + " ("
            + (finalValue / 10000.0)
            + "mm)",
        "Clearance Check",
        new Point[0]);

    return finalValue;
  }

  /**
   * Returns the maximal required spacing of clearance class with index p_i to all other clearance
   * classes on layer p_layer.
   */
  public int max_value(int p_i, int p_layer) {
    int i = Math.max(p_i, 0);
    i = Math.min(i, classCount - 1);
    int layer = Math.max(p_layer, 0);
    layer = Math.min(layer, layerStructure.arr.length - 1);
    return row[i].maxValue[layer];
  }

  public int max_value(int p_layer) {
    int layer = Math.max(p_layer, 0);
    layer = Math.min(layer, layerStructure.arr.length - 1);
    return this.maxValueOnLayer[layer];
  }

  /**
   * Returns true, if the values of the clearance matrix in the p_i-th column and the p_j-th row are
   * not equal on all layers.
   */
  public boolean is_layer_dependent(int p_i, int p_j) {
    int compareValue = row[p_j].column[p_i].layer[0];
    for (int l = 1; l < layerStructure.arr.length; l++) {
      if (row[p_j].column[p_i].layer[l] != compareValue) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true, if the values of the clearance matrix in the p_i-th column and the p_j-th row are
   * not equal on all inner layers.
   */
  public boolean is_inner_layer_dependent(int p_i, int p_j) {
    if (layerStructure.arr.length <= 2) {
      return false; // no inner layers
    }
    int compareValue = row[p_j].column[p_i].layer[1];
    for (int l = 2; l < layerStructure.arr.length - 1; l++) {
      if (row[p_j].column[p_i].layer[l] != compareValue) {
        return true;
      }
    }
    return false;
  }

  /** Returns the row with index p_no */
  public Row get_row(int p_no) {
    if (p_no < 0 || p_no >= this.row.length) {
      FRLogger.warn("ClearanceMatrix.get_row: p_no out of range");
      return null;
    }
    return this.row[p_no];
  }

  public int get_class_count() {
    return this.classCount;
  }

  /** Return the layer count of this clearance matrix;# */
  public int get_layer_count() {
    return layerStructure.arr.length;
  }

  /** Returns the clearance compensation value of p_clearance_class_no on layer p_layer. */
  public int clearance_compensation_value(int p_clearance_class_no, int p_layer) {
    return (this.get_value(p_clearance_class_no, p_clearance_class_no, p_layer, false) + 1) / 2;
  }

  /**
   * Appends a new clearance class to the clearance matrix and initializes it with the values of the
   * default class. Returns false, oif a clearance class with name p_class_name is already existing.
   */
  public boolean append_class(String p_class_name) {
    if (this.get_no(p_class_name) >= 0) {
      return false;
    }
    int oldClassCount = this.classCount;
    ++this.classCount;

    Row[] newRow = new Row[this.classCount];

    // append a matrix entry to each old row
    for (int i = 0; i < oldClassCount; i++) {
      Row currOldRow = this.row[i];
      newRow[i] = new Row(currOldRow.name);
      Row currNewRow = newRow[i];
      currNewRow.maxValue = currOldRow.maxValue;
      System.arraycopy(currOldRow.column, 0, currNewRow.column, 0, oldClassCount);

      currNewRow.column[oldClassCount] = new MatrixEntry();
    }

    // append the new row

    newRow[oldClassCount] = new Row(p_class_name);

    this.row = newRow;

    // Set the new matrix elements to default values.

    for (int i = 0; i < oldClassCount; i++) {
      for (int j = 0; j < this.layerStructure.arr.length; j++) {
        int defaultValue = this.get_value(1, i, j, false);
        this.set_value(oldClassCount, i, j, defaultValue);
        this.set_value(i, oldClassCount, j, defaultValue);
      }
    }

    for (int j = 0; j < this.layerStructure.arr.length; j++) {
      int defaultValue = this.get_value(1, 1, j, false);
      this.set_value(oldClassCount, oldClassCount, j, defaultValue);
    }
    return true;
  }

  /** Removes the class with index p_index from the clearance matrix. */
  void remove_class(int p_index) {
    int oldClassCount = this.classCount;
    --this.classCount;

    Row[] newRow = new Row[this.classCount];

    // remove the  matrix entry with index p_index in to each old row
    int newRowIndex = 0;
    for (int i = 0; i < oldClassCount; i++) {
      if (i == p_index) {
        continue;
      }
      Row currOldRow = this.row[i];
      newRow[newRowIndex] = new Row(currOldRow.name);
      Row currNewRow = newRow[newRowIndex];

      int newColumnIndex = 0;
      for (int j = 0; j < oldClassCount; j++) {
        if (j == p_index) {
          continue;
        }
        currNewRow.column[newColumnIndex] = currOldRow.column[j];
        ++newColumnIndex;
      }
      ++newRowIndex;
    }
    this.row = newRow;
  }

  /**
   * Returns true, if all clearance values of the class with index p_1 are equal to the clearance
   * values of index p_2.
   */
  public boolean is_equal(int p_1, int p_2) {
    if (p_1 == p_2) {
      return true;
    }
    if (p_1 < 0 || p_2 < 0 || p_1 >= this.classCount || p_2 >= this.classCount) {
      return false;
    }
    Row row1 = this.row[p_1];
    Row row2 = this.row[p_2];
    for (int i = 1; i < classCount; i++) {
      if (!row1.column[i].equals(row2.column[i])) {
        return false;
      }
    }
    return true;
  }

  /** contains a row of entries of the clearance matrix */
  private final class Row implements ObjectInfoPanel.Printable, Serializable {

    final String name;
    final MatrixEntry[] column;
    int[] maxValue;

    private Row(String p_name) {
      name = p_name;
      column = new MatrixEntry[classCount];
      for (int i = 0; i < classCount; i++) {
        column[i] = new MatrixEntry();
      }
      maxValue = new int[layerStructure.arr.length];
    }

    @Override
    public void print_info(ObjectInfoPanel p_window, Locale p_locale) {
      TextManager tm = new TextManager(this.getClass(), p_locale);

      p_window.append_bold(tm.getText("spacing_from_clearance_class") + " ");
      p_window.append_bold(this.name);
      for (int i = 1; i < this.column.length; i++) {
        p_window.newline();
        p_window.indent();
        p_window.append(" " + tm.getText("to_class") + " ");
        p_window.append(row[i].name);
        MatrixEntry currColumn = this.column[i];
        if (currColumn.is_layer_dependent()) {
          p_window.append(" " + tm.getText("on_layer") + " ");
          for (int j = 0; j < layerStructure.arr.length; j++) {
            p_window.newline();
            p_window.indent();
            p_window.indent();
            p_window.append(layerStructure.arr[j].name);
            p_window.append(" = ");
            p_window.append(currColumn.layer[j]);
          }
        } else {
          p_window.append(" = ");
          p_window.append(currColumn.layer[0]);
        }
      }
    }
  }

  /** a single entry of the clearance matrix */
  private final class MatrixEntry implements Serializable {

    int[] layer;

    private MatrixEntry() {
      layer = new int[layerStructure.arr.length];
      for (int i = 0; i < layerStructure.arr.length; i++) {
        layer[i] = 0;
      }
    }

    /** Returns true of all clearances values of this and p_other are equal. */
    boolean equals(MatrixEntry p_other) {
      for (int i = 0; i < layerStructure.arr.length; i++) {
        if (this.layer[i] != p_other.layer[i]) {
          return false;
        }
      }
      return true;
    }

    /** Return true, if not all layer values are equal. */
    boolean is_layer_dependent() {
      int compareValue = layer[0];
      for (int i = 1; i < layerStructure.arr.length; i++) {
        if (layer[i] != compareValue) {
          return true;
        }
      }
      return false;
    }
  }
}

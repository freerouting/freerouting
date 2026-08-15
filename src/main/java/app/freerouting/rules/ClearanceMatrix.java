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

  /** The count of clearance classes. */
  private int classCount;

  private Row[] row; // vector of classCount rows of the clearance matrix

  /**
   * Creates a new instance for {@code classCount} clearance classes on the layers in {@code
   * layerStructure}. {@code nameArr} must have one entry for each class.
   */
  public ClearanceMatrix(int classCount, LayerStructure layerStructure, String[] nameArr) {
    this.classCount = Math.max(classCount, 1);
    this.layerStructure = layerStructure;
    row = new Row[this.classCount];
    for (int i = 0; i < this.classCount; i++) {
      row[i] = new Row(nameArr[i]);
    }
    this.maxValueOnLayer = new int[layerStructure.arr.length];
  }

  /**
   * Creates a new instance with the 2 clearance classes "none" and "default" and initializes it
   * with {@code defaultValue}.
   */
  public static ClearanceMatrix getDefaultInstance(
      LayerStructure layerStructure, int defaultValue) {
    String[] nameArr = new String[2];
    nameArr[0] = "null";
    nameArr[1] = "default";
    ClearanceMatrix result = new ClearanceMatrix(2, layerStructure, nameArr);
    result.setDefaultValue(defaultValue);
    return result;
  }

  /**
   * Returns the number of the clearance class with the input name, or -1, if no such clearance
   * class exists.
   */
  public int getNo(String name) {
    for (int i = 0; i < classCount; i++) {
      if (row[i].name.equalsIgnoreCase(name)) {
        return i;
      }
    }
    return -1;
  }

  /** Gets the name of the clearance class with the input number. */
  public String getName(int clearanceClass) {
    if (clearanceClass < 0 || clearanceClass >= row.length) {
      FRLogger.warn("ClearanceMatrix.get_name: p_cl_class out of range");
      return null;
    }
    return row[clearanceClass].name;
  }

  /** Sets the value of all clearance classes with number {@literal >}= 1 to {@code value}. */
  public void setDefaultValue(int value) {
    for (int i = 0; i < layerStructure.arr.length; i++) {
      setDefaultValue(i, value);
    }
  }

  /** Sets the value of all clearance classes with number {@literal >}= 1 to {@code value}. */
  public void setDefaultValue(int layer, int value) {
    for (int i = 1; i < classCount; i++) {
      for (int j = 1; j < classCount; j++) {
        setValue(i, j, layer, value);
      }
    }
  }

  /** Sets the value of an entry in the clearance matrix to {@code value} on all layers. */
  public void setValue(int classI, int classJ, int value) {
    for (int layer = 0; layer < layerStructure.arr.length; layer++) {
      setValue(classI, classJ, layer, value);
    }
  }

  /** Sets the value of an entry in the clearance matrix to {@code value}. */
  public void setValue(int classI, int classJ, int layer, int value) {
    Row currentRow = row[classJ];
    MatrixEntry currentEntry = currentRow.column[classI];

    // assure, that the clearance value is positive and even, and round it up, if it is odd
    // NOTE: why does it need to be even?
    value = Math.max(value, 0);
    if (value % 2 != 0) {
      if (value == Integer.MAX_VALUE) {
        value--;
      } else {
        value++;
      }
    }

    currentEntry.layer[layer] = value;
    currentRow.maxValue[layer] = Math.max(currentRow.maxValue[layer], value);
    this.maxValueOnLayer[layer] = Math.max(this.maxValueOnLayer[layer], value);
  }

  /** Sets the value of an entry in the clearance matrix to {@code value} on all inner layers. */
  public void setInnerValue(int classI, int classJ, int value) {
    for (int layer = 1; layer < layerStructure.arr.length - 1; layer++) {
      setValue(classI, classJ, layer, value);
    }
  }

  /**
   * Gets the required spacing of clearance classes with index {@code classI} and {@code classJ} on
   * {@code layer}. This value will be always an even integer.
   */
  public int getValue(int classI, int classJ, int layer, boolean addSafetyMargin) {

    if (classI < 0
        || classI >= classCount
        || classJ < 0
        || classJ >= classCount
        || layer < 0
        || layer >= layerStructure.arr.length) {
      FRLogger.trace(
          "ClearanceMatrix.get_value",
          "out_of_bounds",
          "Clearance request out of bounds: class_i="
              + classI
              + " (max="
              + (classCount - 1)
              + ")"
              + ", class_j="
              + classJ
              + " (max="
              + (classCount - 1)
              + ")"
              + ", layer="
              + layer
              + " (max="
              + (layerStructure.arr.length - 1)
              + ")"
              + ", returning 0",
          "Clearance Check",
          new Point[0]);
      return 0;
    }

    int valueFromTheMatrix = row[classJ].column[classI].layer[layer];
    int finalValue =
        addSafetyMargin ? valueFromTheMatrix + clearance_safety_margin : valueFromTheMatrix;

    FRLogger.trace(
        "ClearanceMatrix.get_value",
        "clearance_retrieved",
        "Clearance value: class_i="
            + classI
            + " ("
            + (classI < row.length ? row[classI].name : "?")
            + ")"
            + ", class_j="
            + classJ
            + " ("
            + (classJ < row.length ? row[classJ].name : "?")
            + ")"
            + ", layer="
            + layer
            + " ("
            + (layer < layerStructure.arr.length ? layerStructure.arr[layer].name : "?")
            + ")"
            + ", base_value="
            + valueFromTheMatrix
            + " ("
            + (valueFromTheMatrix / 10000.0)
            + "mm)"
            + ", safety_margin="
            + (addSafetyMargin ? clearance_safety_margin : 0)
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
   * Returns the maximal required spacing of the given clearance class to all other clearance
   * classes on the given layer.
   */
  public int maxValue(int classI, int layer) {
    int i = Math.max(classI, 0);
    i = Math.min(i, classCount - 1);
    int layerIndex = Math.max(layer, 0);
    layerIndex = Math.min(layerIndex, layerStructure.arr.length - 1);
    return row[i].maxValue[layerIndex];
  }

  /** Returns the maximum clearance value on the given layer. */
  public int maxValue(int layer) {
    int layerIndex = Math.max(layer, 0);
    layerIndex = Math.min(layerIndex, layerStructure.arr.length - 1);
    return this.maxValueOnLayer[layerIndex];
  }

  /**
   * Returns true if the values of the clearance matrix in the {@code classI}-th column and the
   * {@code classJ}-th row are not equal on all layers.
   */
  public boolean isLayerDependent(int classI, int classJ) {
    int compareValue = row[classJ].column[classI].layer[0];
    for (int l = 1; l < layerStructure.arr.length; l++) {
      if (row[classJ].column[classI].layer[l] != compareValue) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the values of the clearance matrix in the {@code classI}-th column and the
   * {@code classJ}-th row are not equal on all inner layers.
   */
  public boolean isInnerLayerDependent(int classI, int classJ) {
    if (layerStructure.arr.length <= 2) {
      return false; // no inner layers
    }
    int compareValue = row[classJ].column[classI].layer[1];
    for (int l = 2; l < layerStructure.arr.length - 1; l++) {
      if (row[classJ].column[classI].layer[l] != compareValue) {
        return true;
      }
    }
    return false;
  }

  /** Returns the row with the given index. */
  public Row getRow(int index) {
    if (index < 0 || index >= this.row.length) {
      FRLogger.warn("ClearanceMatrix.get_row: p_no out of range");
      return null;
    }
    return this.row[index];
  }

  /** Returns the number of clearance classes. */
  public int getClassCount() {
    return this.classCount;
  }

  /** Returns the layer count of this clearance matrix. */
  public int getLayerCount() {
    return layerStructure.arr.length;
  }

  /** Returns the clearance compensation value of the given class on the given layer. */
  public int clearanceCompensationValue(int clearanceClass, int layer) {
    return (this.getValue(clearanceClass, clearanceClass, layer, false) + 1) / 2;
  }

  /**
   * Appends a new clearance class to the clearance matrix and initializes it with the values of the
   * default class. Returns false if a clearance class with the given name already exists.
   */
  public boolean appendClass(String className) {
    if (this.getNo(className) >= 0) {
      return false;
    }
    int oldClassCount = this.classCount;
    ++this.classCount;

    Row[] newRow = new Row[this.classCount];

    // append a matrix entry to each old row
    for (int i = 0; i < oldClassCount; i++) {
      Row currentOldRow = this.row[i];
      newRow[i] = new Row(currentOldRow.name);
      Row currentNewRow = newRow[i];
      currentNewRow.maxValue = currentOldRow.maxValue;
      System.arraycopy(currentOldRow.column, 0, currentNewRow.column, 0, oldClassCount);

      currentNewRow.column[oldClassCount] = new MatrixEntry();
    }

    // append the new row

    newRow[oldClassCount] = new Row(className);

    this.row = newRow;

    // Set the new matrix elements to default values.

    for (int i = 0; i < oldClassCount; i++) {
      for (int j = 0; j < this.layerStructure.arr.length; j++) {
        int defaultValue = this.getValue(1, i, j, false);
        this.setValue(oldClassCount, i, j, defaultValue);
        this.setValue(i, oldClassCount, j, defaultValue);
      }
    }

    for (int j = 0; j < this.layerStructure.arr.length; j++) {
      int defaultValue = this.getValue(1, 1, j, false);
      this.setValue(oldClassCount, oldClassCount, j, defaultValue);
    }
    return true;
  }

  /** Removes the class with the given index from the clearance matrix. */
  void removeClass(int index) {
    int oldClassCount = this.classCount;
    --this.classCount;

    Row[] newRow = new Row[this.classCount];

    // Remove the matrix entry with the given index from each old row.
    int newRowIndex = 0;
    for (int i = 0; i < oldClassCount; i++) {
      if (i == index) {
        continue;
      }
      Row currentOldRow = this.row[i];
      newRow[newRowIndex] = new Row(currentOldRow.name);
      Row currentNewRow = newRow[newRowIndex];

      int newColumnIndex = 0;
      for (int j = 0; j < oldClassCount; j++) {
        if (j == index) {
          continue;
        }
        currentNewRow.column[newColumnIndex] = currentOldRow.column[j];
        ++newColumnIndex;
      }
      ++newRowIndex;
    }
    this.row = newRow;
  }

  /**
   * Returns true if all clearance values of the class with index {@code first} are equal to the
   * values of the class with index {@code second}.
   */
  public boolean isEqual(int first, int second) {
    if (first == second) {
      return true;
    }
    if (first < 0 || second < 0 || first >= this.classCount || second >= this.classCount) {
      return false;
    }
    Row row1 = this.row[first];
    Row row2 = this.row[second];
    for (int i = 1; i < classCount; i++) {
      if (!row1.column[i].equals(row2.column[i])) {
        return false;
      }
    }
    return true;
  }

  /** Contains a row of entries of the clearance matrix. */
  private final class Row implements ObjectInfoPanel.Printable, Serializable {

    final String name;
    final MatrixEntry[] column;
    int[] maxValue;

    private Row(String name) {
      this.name = name;
      column = new MatrixEntry[classCount];
      for (int i = 0; i < classCount; i++) {
        column[i] = new MatrixEntry();
      }
      maxValue = new int[layerStructure.arr.length];
    }

    @Override
    public void printInfo(ObjectInfoPanel window, Locale locale) {
      TextManager tm = new TextManager(this.getClass(), locale);

      window.appendBold(tm.getText("spacing_from_clearance_class") + " ");
      window.appendBold(this.name);
      for (int i = 1; i < this.column.length; i++) {
        window.newline();
        window.indent();
        window.append(" " + tm.getText("to_class") + " ");
        window.append(row[i].name);
        MatrixEntry currentColumn = this.column[i];
        if (currentColumn.isLayerDependent()) {
          window.append(" " + tm.getText("on_layer") + " ");
          for (int j = 0; j < layerStructure.arr.length; j++) {
            window.newline();
            window.indent();
            window.indent();
            window.append(layerStructure.arr[j].name);
            window.append(" = ");
            window.append(currentColumn.layer[j]);
          }
        } else {
          window.append(" = ");
          window.append(currentColumn.layer[0]);
        }
      }
    }
  }

  /** Represents a single entry of the clearance matrix. */
  private final class MatrixEntry implements Serializable {

    int[] layer;

    private MatrixEntry() {
      layer = new int[layerStructure.arr.length];
      for (int i = 0; i < layerStructure.arr.length; i++) {
        layer[i] = 0;
      }
    }

    /** Returns true if all clearance values of this entry and {@code other} are equal. */
    boolean equals(MatrixEntry other) {
      for (int i = 0; i < layerStructure.arr.length; i++) {
        if (this.layer[i] != other.layer[i]) {
          return false;
        }
      }
      return true;
    }

    /** Returns true if not all layer values are equal. */
    boolean isLayerDependent() {
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

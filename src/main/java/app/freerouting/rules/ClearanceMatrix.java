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
  public ClearanceMatrix(int pClassCount, LayerStructure pLayerStructure, String[] pNameArr) {
    classCount = Math.max(pClassCount, 1);
    layerStructure = pLayerStructure;
    row = new Row[classCount];
    for (int i = 0; i < classCount; i++) {
      row[i] = new Row(pNameArr[i]);
    }
    this.maxValueOnLayer = new int[layerStructure.arr.length];
  }

  /**
   * Creates a new instance with the 2 clearance classes "none" and "default" and initializes it
   * with p_default_value.
   */
  public static ClearanceMatrix getDefaultInstance(
      LayerStructure pLayerStructure, int pDefaultValue) {
    String[] nameArr = new String[2];
    nameArr[0] = "null";
    nameArr[1] = "default";
    ClearanceMatrix result = new ClearanceMatrix(2, pLayerStructure, nameArr);
    result.setDefaultValue(pDefaultValue);
    return result;
  }

  /**
   * Returns the number of the clearance class with the input name, or -1, if no such clearance
   * class exists.
   */
  public int getNo(String pName) {
    for (int i = 0; i < classCount; i++) {
      if (row[i].name.equalsIgnoreCase(pName)) {
        return i;
      }
    }
    return -1;
  }

  /** Gets the name of the clearance class with the input number. */
  public String getName(int pClClass) {
    if (pClClass < 0 || pClClass >= row.length) {
      FRLogger.warn("ClearanceMatrix.get_name: p_cl_class out of range");
      return null;
    }
    return row[pClClass].name;
  }

  /**
   * Sets the value of all clearance classes with number {@literal >}= 1 to p_value on all layers.
   */
  public void setDefaultValue(int pValue) {
    for (int i = 0; i < layerStructure.arr.length; i++) {
      setDefaultValue(i, pValue);
    }
  }

  /** Sets the value of all clearance classes with number {@literal >}= 1 to p_value on p_layer. */
  public void setDefaultValue(int pLayer, int pValue) {
    for (int i = 1; i < classCount; i++) {
      for (int j = 1; j < classCount; j++) {
        setValue(i, j, pLayer, pValue);
      }
    }
  }

  /** Sets the value of an entry in the clearance matrix to p_value on all layers. */
  public void setValue(int pI, int pJ, int pValue) {
    for (int layer = 0; layer < layerStructure.arr.length; layer++) {
      setValue(pI, pJ, layer, pValue);
    }
  }

  /** Sets the value of an entry in the clearance matrix to p_value on all inner layers. */
  public void setInnerValue(int pI, int pJ, int pValue) {
    for (int layer = 1; layer < layerStructure.arr.length - 1; layer++) {
      setValue(pI, pJ, layer, pValue);
    }
  }

  /** Sets the value of an entry in the clearance matrix to p_value. */
  public void setValue(int pI, int pJ, int pLayer, int pValue) {
    Row currRow = row[pJ];
    MatrixEntry currEntry = currRow.column[pI];

    // assure, that the clearance value is positive and even, and round it up, if it is odd
    // NOTE: why does it need to be even?
    int value = Math.max(pValue, 0);
    if (value % 2 != 0) {
      if (value == Integer.MAX_VALUE) {
        value--;
      } else {
        value++;
      }
    }

    currEntry.layer[pLayer] = value;
    currRow.maxValue[pLayer] = Math.max(currRow.maxValue[pLayer], value);
    this.maxValueOnLayer[pLayer] = Math.max(this.maxValueOnLayer[pLayer], value);
  }

  /**
   * Gets the required spacing of clearance classes with index p_i and p_j on p_layer. This value
   * will be always an even integer.
   */
  public int getValue(int pI, int pJ, int pLayer, boolean pAddSafetyMargin) {

    if (pI < 0
        || pI >= classCount
        || pJ < 0
        || pJ >= classCount
        || pLayer < 0
        || pLayer >= layerStructure.arr.length) {
      FRLogger.trace(
          "ClearanceMatrix.get_value",
          "out_of_bounds",
          "Clearance request out of bounds: class_i="
              + pI
              + " (max="
              + (classCount - 1)
              + ")"
              + ", class_j="
              + pJ
              + " (max="
              + (classCount - 1)
              + ")"
              + ", layer="
              + pLayer
              + " (max="
              + (layerStructure.arr.length - 1)
              + ")"
              + ", returning 0",
          "Clearance Check",
          new Point[0]);
      return 0;
    }

    int valueFromTheMatrix = row[pJ].column[pI].layer[pLayer];
    int finalValue =
        pAddSafetyMargin ? valueFromTheMatrix + clearance_safety_margin : valueFromTheMatrix;

    FRLogger.trace(
        "ClearanceMatrix.get_value",
        "clearance_retrieved",
        "Clearance value: class_i="
            + pI
            + " ("
            + (pI < row.length ? row[pI].name : "?")
            + ")"
            + ", class_j="
            + pJ
            + " ("
            + (pJ < row.length ? row[pJ].name : "?")
            + ")"
            + ", layer="
            + pLayer
            + " ("
            + (pLayer < layerStructure.arr.length ? layerStructure.arr[pLayer].name : "?")
            + ")"
            + ", base_value="
            + valueFromTheMatrix
            + " ("
            + (valueFromTheMatrix / 10000.0)
            + "mm)"
            + ", safety_margin="
            + (pAddSafetyMargin ? clearance_safety_margin : 0)
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
  public int maxValue(int pI, int pLayer) {
    int i = Math.max(pI, 0);
    i = Math.min(i, classCount - 1);
    int layer = Math.max(pLayer, 0);
    layer = Math.min(layer, layerStructure.arr.length - 1);
    return row[i].maxValue[layer];
  }

  public int maxValue(int pLayer) {
    int layer = Math.max(pLayer, 0);
    layer = Math.min(layer, layerStructure.arr.length - 1);
    return this.maxValueOnLayer[layer];
  }

  /**
   * Returns true, if the values of the clearance matrix in the p_i-th column and the p_j-th row are
   * not equal on all layers.
   */
  public boolean isLayerDependent(int pI, int pJ) {
    int compareValue = row[pJ].column[pI].layer[0];
    for (int l = 1; l < layerStructure.arr.length; l++) {
      if (row[pJ].column[pI].layer[l] != compareValue) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true, if the values of the clearance matrix in the p_i-th column and the p_j-th row are
   * not equal on all inner layers.
   */
  public boolean isInnerLayerDependent(int pI, int pJ) {
    if (layerStructure.arr.length <= 2) {
      return false; // no inner layers
    }
    int compareValue = row[pJ].column[pI].layer[1];
    for (int l = 2; l < layerStructure.arr.length - 1; l++) {
      if (row[pJ].column[pI].layer[l] != compareValue) {
        return true;
      }
    }
    return false;
  }

  /** Returns the row with index p_no */
  public Row getRow(int pNo) {
    if (pNo < 0 || pNo >= this.row.length) {
      FRLogger.warn("ClearanceMatrix.get_row: p_no out of range");
      return null;
    }
    return this.row[pNo];
  }

  public int getClassCount() {
    return this.classCount;
  }

  /** Return the layer count of this clearance matrix;# */
  public int getLayerCount() {
    return layerStructure.arr.length;
  }

  /** Returns the clearance compensation value of p_clearance_class_no on layer p_layer. */
  public int clearanceCompensationValue(int pClearanceClassNo, int pLayer) {
    return (this.getValue(pClearanceClassNo, pClearanceClassNo, pLayer, false) + 1) / 2;
  }

  /**
   * Appends a new clearance class to the clearance matrix and initializes it with the values of the
   * default class. Returns false, oif a clearance class with name p_class_name is already existing.
   */
  public boolean appendClass(String pClassName) {
    if (this.getNo(pClassName) >= 0) {
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

    newRow[oldClassCount] = new Row(pClassName);

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

  /** Removes the class with index p_index from the clearance matrix. */
  void removeClass(int pIndex) {
    int oldClassCount = this.classCount;
    --this.classCount;

    Row[] newRow = new Row[this.classCount];

    // remove the  matrix entry with index p_index in to each old row
    int newRowIndex = 0;
    for (int i = 0; i < oldClassCount; i++) {
      if (i == pIndex) {
        continue;
      }
      Row currOldRow = this.row[i];
      newRow[newRowIndex] = new Row(currOldRow.name);
      Row currNewRow = newRow[newRowIndex];

      int newColumnIndex = 0;
      for (int j = 0; j < oldClassCount; j++) {
        if (j == pIndex) {
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
  public boolean isEqual(int p1, int p2) {
    if (p1 == p2) {
      return true;
    }
    if (p1 < 0 || p2 < 0 || p1 >= this.classCount || p2 >= this.classCount) {
      return false;
    }
    Row row1 = this.row[p1];
    Row row2 = this.row[p2];
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

    private Row(String pName) {
      name = pName;
      column = new MatrixEntry[classCount];
      for (int i = 0; i < classCount; i++) {
        column[i] = new MatrixEntry();
      }
      maxValue = new int[layerStructure.arr.length];
    }

    @Override
    public void printInfo(ObjectInfoPanel pWindow, Locale pLocale) {
      TextManager tm = new TextManager(this.getClass(), pLocale);

      pWindow.appendBold(tm.getText("spacing_from_clearance_class") + " ");
      pWindow.appendBold(this.name);
      for (int i = 1; i < this.column.length; i++) {
        pWindow.newline();
        pWindow.indent();
        pWindow.append(" " + tm.getText("to_class") + " ");
        pWindow.append(row[i].name);
        MatrixEntry currColumn = this.column[i];
        if (currColumn.isLayerDependent()) {
          pWindow.append(" " + tm.getText("on_layer") + " ");
          for (int j = 0; j < layerStructure.arr.length; j++) {
            pWindow.newline();
            pWindow.indent();
            pWindow.indent();
            pWindow.append(layerStructure.arr[j].name);
            pWindow.append(" = ");
            pWindow.append(currColumn.layer[j]);
          }
        } else {
          pWindow.append(" = ");
          pWindow.append(currColumn.layer[0]);
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
    boolean equals(MatrixEntry pOther) {
      for (int i = 0; i < layerStructure.arr.length; i++) {
        if (this.layer[i] != pOther.layer[i]) {
          return false;
        }
      }
      return true;
    }

    /** Return true, if not all layer values are equal. */
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

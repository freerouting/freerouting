package app.freerouting.rules;

import app.freerouting.board.LayerStructure;
import app.freerouting.board.ObjectInfoPanel;
import app.freerouting.logger.FRLogger;

import app.freerouting.management.TextManager;

import java.io.Serializable;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * NxN Matrix describing the spacing restrictions between N clearance classes on a fixed set of
 * layers.
 */
public class ClearanceMatrix implements Serializable
{
  public static final int clearance_safety_margin = 16;
  private final LayerStructure layer_structure;
  private final int[] max_value_on_layer; //  maximum clearance value for each layer
  /**
   * count of clearance classes
   */
  private int class_count;
  private Row[] row; // vector of class_count rows of the clearance matrix

  /**
   * Creates a new instance for p_class_count clearance classes on p_layer_count layers. p_names is
   * an array of dimension p_class_count;
   */
  public ClearanceMatrix(int p_class_count, LayerStructure p_layer_structure, String[] p_name_arr)
  {
    class_count = Math.max(p_class_count, 1);
    layer_structure = p_layer_structure;
    row = new Row[class_count];
    for (int i = 0; i < class_count; ++i)
    {
      row[i] = new Row(p_name_arr[i]);
    }
    this.max_value_on_layer = new int[layer_structure.arr.length];
  }

  /**
   * Creates a new instance with the 2 clearance classes "none" and "default" and initializes it with
   * p_default_value.
   */
  public static ClearanceMatrix get_default_instance(LayerStructure p_layer_structure, int p_default_value)
  {
    String[] name_arr = new String[2];
    name_arr[0] = "null";
    name_arr[1] = "default";
    ClearanceMatrix result = new ClearanceMatrix(2, p_layer_structure, name_arr);
    result.set_default_value(p_default_value);
    return result;
  }

  /**
   * Returns the number of the clearance class with the input name, or -1, if no such clearance
   * class exists.
   */
  public int get_no(String p_name)
  {
    for (int i = 0; i < class_count; ++i)
    {
      if (row[i].name.equalsIgnoreCase(p_name))
      {
        return i;
      }
    }
    return -1;
  }

  /**
   * Gets the name of the clearance class with the input number.
   */
  public String get_name(int p_cl_class)
  {
    if (p_cl_class < 0 || p_cl_class >= row.length)
    {
      FRLogger.warn("ClearanceMatrix.get_name: p_cl_class out of range");
      return null;
    }
    return row[p_cl_class].name;
  }

  /**
   * Sets the value of all clearance classes with number {@literal >}= 1 to p_value on all layers.
   */
  public void set_default_value(int p_value)
  {
    for (int i = 0; i < layer_structure.arr.length; ++i)
    {
      set_default_value(i, p_value);
    }
  }

  /**
   * Sets the value of all clearance classes with number {@literal >}= 1 to p_value on p_layer.
   */
  public void set_default_value(int p_layer, int p_value)
  {
    for (int i = 1; i < class_count; ++i)
    {
      for (int j = 1; j < class_count; ++j)
      {
        set_value(i, j, p_layer, p_value);
      }
    }
  }

  /**
   * Sets the value of an entry in the clearance matrix to p_value on all layers.
   */
  public void set_value(int p_i, int p_j, int p_value)
  {
    for (int layer = 0; layer < layer_structure.arr.length; ++layer)
    {
      set_value(p_i, p_j, layer, p_value);
    }
  }

  /**
   * Sets the value of an entry in the clearance matrix to p_value on all inner layers.
   */
  public void set_inner_value(int p_i, int p_j, int p_value)
  {
    for (int layer = 1; layer < layer_structure.arr.length - 1; ++layer)
    {
      set_value(p_i, p_j, layer, p_value);
    }
  }

  /**
   * Sets the value of an entry in the clearance matrix to p_value.
   */
  public void set_value(int p_i, int p_j, int p_layer, int p_value)
  {
    Row curr_row = row[p_j];
    MatrixEntry curr_entry = curr_row.column[p_i];

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

    curr_entry.layer[p_layer] = value;
    curr_row.max_value[p_layer] = Math.max(curr_row.max_value[p_layer], value);
    this.max_value_on_layer[p_layer] = Math.max(this.max_value_on_layer[p_layer], value);
  }

  /**
   * Gets the required spacing of clearance classes with index p_i and p_j on p_layer. This value
   * will be always an even integer.
   */
  public int get_value(int p_i, int p_j, int p_layer, boolean p_add_safety_margin)
  {

    if (p_i < 0 || p_i >= class_count || p_j < 0 || p_j >= class_count || p_layer < 0 || p_layer >= layer_structure.arr.length)
    {
      return 0;
    }

    int value_from_the_matrix = row[p_j].column[p_i].layer[p_layer];

    return p_add_safety_margin ? value_from_the_matrix + clearance_safety_margin : value_from_the_matrix;
  }

  /**
   * Returns the maximal required spacing of clearance class with index p_i to all other clearance
   * classes on layer p_layer.
   */
  public int max_value(int p_i, int p_layer)
  {
    int i = Math.max(p_i, 0);
    i = Math.min(i, class_count - 1);
    int layer = Math.max(p_layer, 0);
    layer = Math.min(layer, layer_structure.arr.length - 1);
    return row[i].max_value[layer];
  }

  public int max_value(int p_layer)
  {
    int layer = Math.max(p_layer, 0);
    layer = Math.min(layer, layer_structure.arr.length - 1);
    return this.max_value_on_layer[layer];
  }

  /**
   * Returns true, if the values of the clearance matrix in the p_i-th column and the p_j-th row are
   * not equal on all layers.
   */
  public boolean is_layer_dependent(int p_i, int p_j)
  {
    int compare_value = row[p_j].column[p_i].layer[0];
    for (int l = 1; l < layer_structure.arr.length; ++l)
    {
      if (row[p_j].column[p_i].layer[l] != compare_value)
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true, if the values of the clearance matrix in the p_i-th column and the p_j-th row are
   * not equal on all inner layers.
   */
  public boolean is_inner_layer_dependent(int p_i, int p_j)
  {
    if (layer_structure.arr.length <= 2)
    {
      return false; // no inner layers
    }
    int compare_value = row[p_j].column[p_i].layer[1];
    for (int l = 2; l < layer_structure.arr.length - 1; ++l)
    {
      if (row[p_j].column[p_i].layer[l] != compare_value)
      {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the row with index p_no
   */
  public Row get_row(int p_no)
  {
    if (p_no < 0 || p_no >= this.row.length)
    {
      FRLogger.warn("ClearanceMatrix.get_row: p_no out of range");
      return null;
    }
    return this.row[p_no];
  }

  public int get_class_count()
  {
    return this.class_count;
  }

  /**
   * Return the layer count of this clearance matrix;#
   */
  public int get_layer_count()
  {
    return layer_structure.arr.length;
  }

  /**
   * Returns the clearance compensation value of p_clearance_class_no on layer p_layer.
   */
  public int clearance_compensation_value(int p_clearance_class_no, int p_layer)
  {
    return (this.get_value(p_clearance_class_no, p_clearance_class_no, p_layer, false) + 1) / 2;
  }

  /**
   * Appends a new clearance class to the clearance matrix and initializes it with the values of the
   * default class. Returns false, oif a clearance class with name p_class_name is already existing.
   */
  public boolean append_class(String p_class_name)
  {
    if (this.get_no(p_class_name) >= 0)
    {
      return false;
    }
    int old_class_count = this.class_count;
    ++this.class_count;

    Row[] new_row = new Row[this.class_count];

    // append a matrix entry to each old row
    for (int i = 0; i < old_class_count; ++i)
    {
      Row curr_old_row = this.row[i];
      new_row[i] = new Row(curr_old_row.name);
      Row curr_new_row = new_row[i];
      curr_new_row.max_value = curr_old_row.max_value;
      System.arraycopy(curr_old_row.column, 0, curr_new_row.column, 0, old_class_count);

      curr_new_row.column[old_class_count] = new MatrixEntry();
    }

    // append the new row

    new_row[old_class_count] = new Row(p_class_name);

    this.row = new_row;

    // Set the new matrix elements to default values.

    for (int i = 0; i < old_class_count; ++i)
    {
      for (int j = 0; j < this.layer_structure.arr.length; ++j)
      {
        int default_value = this.get_value(1, i, j, false);
        this.set_value(old_class_count, i, j, default_value);
        this.set_value(i, old_class_count, j, default_value);
      }
    }

    for (int j = 0; j < this.layer_structure.arr.length; ++j)
    {
      int default_value = this.get_value(1, 1, j, false);
      this.set_value(old_class_count, old_class_count, j, default_value);
    }
    return true;
  }

  /**
   * Removes the class with index p_index from the clearance matrix.
   */
  void remove_class(int p_index)
  {
    int old_class_count = this.class_count;
    --this.class_count;

    Row[] new_row = new Row[this.class_count];

    // remove the  matrix entry with index p_index in to each old row
    int new_row_index = 0;
    for (int i = 0; i < old_class_count; ++i)
    {
      if (i == p_index)
      {
        continue;
      }
      Row curr_old_row = this.row[i];
      new_row[new_row_index] = new Row(curr_old_row.name);
      Row curr_new_row = new_row[new_row_index];

      int new_column_index = 0;
      for (int j = 0; j < old_class_count; ++j)
      {
        if (j == p_index)
        {
          continue;
        }
        curr_new_row.column[new_column_index] = curr_old_row.column[j];
        ++new_column_index;
      }
      ++new_row_index;
    }
    this.row = new_row;
  }

  /**
   * Returns true, if all clearance values of the class with index p_1 are equal to the clearance
   * values of index p_2.
   */
  public boolean is_equal(int p_1, int p_2)
  {
    if (p_1 == p_2)
    {
      return true;
    }
    if (p_1 < 0 || p_2 < 0 || p_1 >= this.class_count || p_2 >= this.class_count)
    {
      return false;
    }
    Row row_1 = this.row[p_1];
    Row row_2 = this.row[p_2];
    for (int i = 1; i < class_count; ++i)
    {
      if (!row_1.column[i].equals(row_2.column[i]))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * contains a row of entries of the clearance matrix
   */
  private class Row implements ObjectInfoPanel.Printable, Serializable
  {
    final String name;
    final MatrixEntry[] column;
    int[] max_value;

    private Row(String p_name)
    {
      name = p_name;
      column = new MatrixEntry[class_count];
      for (int i = 0; i < class_count; ++i)
      {
        column[i] = new MatrixEntry();
      }
      max_value = new int[layer_structure.arr.length];
    }

    @Override
    public void print_info(ObjectInfoPanel p_window, Locale p_locale)
    {
      TextManager tm = new TextManager(this.getClass(), p_locale);

      p_window.append_bold(tm.getText("spacing_from_clearance_class") + " ");
      p_window.append_bold(this.name);
      for (int i = 1; i < this.column.length; ++i)
      {
        p_window.newline();
        p_window.indent();
        p_window.append(" " + tm.getText("to_class") + " ");
        p_window.append(row[i].name);
        MatrixEntry curr_column = this.column[i];
        if (curr_column.is_layer_dependent())
        {
          p_window.append(" " + tm.getText("on_layer") + " ");
          for (int j = 0; j < layer_structure.arr.length; ++j)
          {
            p_window.newline();
            p_window.indent();
            p_window.indent();
            p_window.append(layer_structure.arr[j].name);
            p_window.append(" = ");
            p_window.append(curr_column.layer[j]);
          }
        }
        else
        {
          p_window.append(" = ");
          p_window.append(curr_column.layer[0]);
        }
      }
    }
  }

  /**
   * a single entry of the clearance matrix
   */
  private class MatrixEntry implements Serializable
  {
    int[] layer;

    private MatrixEntry()
    {
      layer = new int[layer_structure.arr.length];
      for (int i = 0; i < layer_structure.arr.length; ++i)
      {
        layer[i] = 0;
      }
    }

    /**
     * Returns true of all clearances values of this and p_other are equal.
     */
    boolean equals(MatrixEntry p_other)
    {
      for (int i = 0; i < layer_structure.arr.length; ++i)
      {
        if (this.layer[i] != p_other.layer[i])
        {
          return false;
        }
      }
      return true;
    }

    /**
     * Return true, if not all layer values are equal.
     */
    boolean is_layer_dependent()
    {
      int compare_value = layer[0];
      for (int i = 1; i < layer_structure.arr.length; ++i)
      {
        if (layer[i] != compare_value)
        {
          return true;
        }
      }
      return false;
    }
  }
}
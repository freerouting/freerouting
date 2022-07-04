package app.freerouting.boardgraphics;

import javax.swing.table.AbstractTableModel;

/** Abstract class to store colors used for drawing the board. */
public abstract class ColorTableModel extends AbstractTableModel {

  protected final Object[][] data;
  protected final java.util.Locale locale;

  protected ColorTableModel(int p_row_count, java.util.Locale p_locale) {
    this.data = new Object[p_row_count][];
    this.locale = p_locale;
  }

  protected ColorTableModel(java.io.ObjectInputStream p_stream)
      throws java.io.IOException, java.lang.ClassNotFoundException {
    this.data = (Object[][]) p_stream.readObject();
    this.locale = (java.util.Locale) p_stream.readObject();
  }

  public int getRowCount() {
    return data.length;
  }

  public Object getValueAt(int p_row, int p_col) {
    return data[p_row][p_col];
  }

  public void setValueAt(Object p_value, int p_row, int p_col) {
    data[p_row][p_col] = p_value;
    fireTableCellUpdated(p_row, p_col);
  }

  /**
   * JTable uses this method to determine the default renderer/ editor for each cell. If we didn't
   * implement this method, then the last column would contain text ("true"/"false"), rather than a
   * check box.
   */
  public Class<?> getColumnClass(int p_c) {
    return getValueAt(0, p_c).getClass();
  }

  protected void write_object(java.io.ObjectOutputStream p_stream) throws java.io.IOException {
    p_stream.writeObject(this.data);
    p_stream.writeObject(this.locale);
  }
}

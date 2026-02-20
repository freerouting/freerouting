package app.freerouting.boardgraphics;

import javax.swing.table.AbstractTableModel;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;

/** Abstract class to store colors used for drawing the board. */
public abstract class ColorTableModel extends AbstractTableModel {

  protected final Object[][] data;
  protected final Locale locale;

  protected ColorTableModel(int p_row_count, Locale p_locale) {
    this.data = new Object[p_row_count][];
    this.locale = p_locale;
  }

  protected ColorTableModel(ObjectInputStream p_stream)
      throws IOException, ClassNotFoundException {
    this.data = (Object[][]) p_stream.readObject();
    this.locale = (Locale) p_stream.readObject();
  }

  @Override
  public int getRowCount() {
    return data.length;
  }

  @Override
  public Object getValueAt(int p_row, int p_col) {
    return data[p_row][p_col];
  }

  @Override
  public void setValueAt(Object p_value, int p_row, int p_col) {
    data[p_row][p_col] = p_value;
    fireTableCellUpdated(p_row, p_col);
  }

  /**
   * JTable uses this method to determine the default renderer/ editor for each cell. If we didn't
   * implement this method, then the last column would contain text ("true"/"false"), rather than a
   * check box.
   */
  @Override
  public Class<?> getColumnClass(int p_c) {
    return getValueAt(0, p_c).getClass();
  }

  protected void write_object(ObjectOutputStream p_stream) throws IOException {
    p_stream.writeObject(this.data);
    p_stream.writeObject(this.locale);
  }
}

package app.freerouting.gui.rendering;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;
import javax.swing.table.AbstractTableModel;

/** Abstract class to store colors used for drawing the board. */
public abstract class ColorTableModel extends AbstractTableModel {

  protected final Object[][] data;
  protected final Locale locale;

  /** ColorTableModel. */
  protected ColorTableModel(int rowCount, Locale locale) {
    this.data = new Object[rowCount][];
    this.locale = locale;
  }

  /** ColorTableModel. */
  protected ColorTableModel(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    this.data = (Object[][]) stream.readObject();
    this.locale = (Locale) stream.readObject();
  }

  @Override
  public int getRowCount() {
    return data.length;
  }

  @Override
  public Object getValueAt(int row, int col) {
    return data[row][col];
  }

  @Override
  public void setValueAt(Object value, int row, int col) {
    data[row][col] = value;
    fireTableCellUpdated(row, col);
  }

  /**
   * JTable uses this method to determine the default renderer/ editor for each cell. If we didn't
   * implement this method, then the last column would contain text ("true"/"false"), rather than a
   * check box.
   */
  @Override
  public Class<?> getColumnClass(int c) {
    return getValueAt(0, c).getClass();
  }

  /** WriteObject. */
  protected void writeObject(ObjectOutputStream stream) throws IOException {
    stream.writeObject(this.data);
    stream.writeObject(this.locale);
  }
}

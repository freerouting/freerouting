package app.freerouting.boardgraphics;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Locale;
import javax.swing.table.AbstractTableModel;

/** Abstract class to store colors used for drawing the board. */
public abstract class ColorTableModel extends AbstractTableModel {

  protected final Object[][] data;
  protected final Locale locale;

  protected ColorTableModel(int pRowCount, Locale pLocale) {
    this.data = new Object[pRowCount][];
    this.locale = pLocale;
  }

  protected ColorTableModel(ObjectInputStream pStream) throws IOException, ClassNotFoundException {
    this.data = (Object[][]) pStream.readObject();
    this.locale = (Locale) pStream.readObject();
  }

  @Override
  public int getRowCount() {
    return data.length;
  }

  @Override
  public Object getValueAt(int pRow, int pCol) {
    return data[pRow][pCol];
  }

  @Override
  public void setValueAt(Object pValue, int pRow, int pCol) {
    data[pRow][pCol] = pValue;
    fireTableCellUpdated(pRow, pCol);
  }

  /**
   * JTable uses this method to determine the default renderer/ editor for each cell. If we didn't
   * implement this method, then the last column would contain text ("true"/"false"), rather than a
   * check box.
   */
  @Override
  public Class<?> getColumnClass(int pC) {
    return getValueAt(0, pC).getClass();
  }

  protected void writeObject(ObjectOutputStream pStream) throws IOException {
    pStream.writeObject(this.data);
    pStream.writeObject(this.locale);
  }
}

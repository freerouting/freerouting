package app.freerouting.boardgraphics;

import app.freerouting.board.LayerStructure;
import app.freerouting.util.TextManager;
import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Locale;

/** Stores the layer dependent colors used for drawing for the items on the board. */
public class ItemColorTableModel extends ColorTableModel implements Serializable {

  private transient boolean itemColorsPrecalculated;
  private transient Color[][] precalculatedItemColors;

  // Create the default color table for the layers
  public ItemColorTableModel(LayerStructure pLayerStructure, Locale pLocale) {
    super(pLayerStructure.arr.length, pLocale);

    int rowCount = pLayerStructure.arr.length;
    final int itemTypeCount = ColumnNames.values().length - 1;
    int signalLayerNo = 0;
    for (int layer = 0; layer < rowCount; layer++) {
      boolean isSignalLayer = pLayerStructure.arr[layer].isSignal;
      data[layer] = new Object[itemTypeCount + 1];
      Object[] currRow = data[layer];
      currRow[0] = pLayerStructure.arr[layer].name;
      if (layer == 0) {
        // F.Cu
        currRow[ColumnNames.PINS.ordinal()] = new Color(227, 183, 46);
        currRow[ColumnNames.TRACES.ordinal()] = new Color(200, 52, 52);
        currRow[ColumnNames.CONDUCTION_AREAS.ordinal()] = new Color(0, 150, 0);
        currRow[ColumnNames.KEEPOUTS.ordinal()] = new Color(26, 196, 210);
        currRow[ColumnNames.PLACE_KEEPOUTS.ordinal()] = new Color(150, 50, 0);
      } else if (layer == rowCount - 1) {
        // B.Cu
        currRow[ColumnNames.PINS.ordinal()] = new Color(227, 183, 46);
        currRow[ColumnNames.TRACES.ordinal()] = new Color(77, 127, 196);
        currRow[ColumnNames.CONDUCTION_AREAS.ordinal()] = new Color(100, 100, 0);
        currRow[ColumnNames.KEEPOUTS.ordinal()] = new Color(26, 196, 210);
        currRow[ColumnNames.PLACE_KEEPOUTS.ordinal()] = new Color(160, 80, 0);
      } else {
        // Inner layers like In1.Cu, In2.Cu, etc.
        if (isSignalLayer) {
          // currently 6 different default colors for traces on the inner layers
          final int differentInnerColors = 6;
          int remainder = signalLayerNo % differentInnerColors;
          currRow[ColumnNames.TRACES.ordinal()] =
              switch (remainder % differentInnerColors) {
                case 1 -> new Color(127, 200, 127);
                case 2 -> new Color(206, 125, 44);
                case 3 -> new Color(79, 203, 203);
                case 4 -> new Color(219, 98, 139);
                case 5 -> new Color(167, 165, 198);
                default -> new Color(40, 204, 217);
              };
        } else // power layer
        {
          currRow[ColumnNames.TRACES.ordinal()] = Color.BLACK;
        }
        currRow[ColumnNames.PINS.ordinal()] = new Color(255, 150, 0);
        currRow[ColumnNames.CONDUCTION_AREAS.ordinal()] = new Color(0, 200, 60);
        currRow[ColumnNames.KEEPOUTS.ordinal()] = new Color(26, 196, 210);
        currRow[ColumnNames.PLACE_KEEPOUTS.ordinal()] = new Color(150, 50, 0);
      }
      currRow[ColumnNames.VIAS.ordinal()] = new Color(227, 183, 46);
      currRow[ColumnNames.FIXED_VIAS.ordinal()] = currRow[ColumnNames.VIAS.ordinal()];
      currRow[ColumnNames.FIXED_TRACES.ordinal()] = currRow[ColumnNames.TRACES.ordinal()];
      currRow[ColumnNames.VIA_KEEPOUTS.ordinal()] = new Color(236, 236, 236);
      if (isSignalLayer) {
        ++signalLayerNo;
      }
    }
  }

  public ItemColorTableModel(ObjectInputStream pStream) throws IOException, ClassNotFoundException {
    super(pStream);
  }

  /** Copy constructor. */
  public ItemColorTableModel(ItemColorTableModel pItemColorModel) {
    super(pItemColorModel.data.length, pItemColorModel.locale);
    for (int i = 0; i < this.data.length; i++) {
      this.data[i] = new Object[pItemColorModel.data[i].length];
      System.arraycopy(pItemColorModel.data[i], 0, this.data[i], 0, this.data[i].length);
    }
  }

  @Override
  public int getColumnCount() {
    return ColumnNames.values().length;
  }

  @Override
  public int getRowCount() {
    return data.length;
  }

  @Override
  public String getColumnName(int pCol) {
    TextManager tm = new TextManager(ColorTableModel.class, this.locale);
    return tm.getText(ColumnNames.values()[pCol].toString());
  }

  @Override
  public void setValueAt(Object pValue, int pRow, int pCol) {
    super.setValueAt(pValue, pRow, pCol);
    this.itemColorsPrecalculated = false;
  }

  /** Don't need to implement this method unless your table's editable. */
  @Override
  public boolean isCellEditable(int pRow, int pCol) {
    // Note that the data/cell address is constant,
    // no matter where the cell appears onscreen.
    return pCol >= 1;
  }

  Color[] getTraceColors(boolean pFixed) {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    Color[] result;
    if (pFixed) {
      result = precalculatedItemColors[ColumnNames.FIXED_TRACES.ordinal() - 1];
    } else {
      result = precalculatedItemColors[ColumnNames.TRACES.ordinal() - 1];
    }
    return result;
  }

  Color[] getViaColors(boolean pFixed) {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    Color[] result;
    if (pFixed) {
      result = precalculatedItemColors[ColumnNames.FIXED_VIAS.ordinal() - 1];
    } else {
      result = precalculatedItemColors[ColumnNames.VIAS.ordinal() - 1];
    }
    return result;
  }

  Color[] getPinColors() {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    return precalculatedItemColors[ColumnNames.PINS.ordinal() - 1];
  }

  public void setPinColors(Color[] pColorArr) {
    setColors(ColumnNames.PINS.ordinal(), pColorArr);
  }

  Color[] getConductionColors() {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    return precalculatedItemColors[ColumnNames.CONDUCTION_AREAS.ordinal() - 1];
  }

  public void setConductionColors(Color[] pColorArr) {
    setColors(ColumnNames.CONDUCTION_AREAS.ordinal(), pColorArr);
  }

  Color[] getObstacleColors() {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    return precalculatedItemColors[ColumnNames.KEEPOUTS.ordinal() - 1];
  }

  Color[] getViaObstacleColors() {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    return precalculatedItemColors[ColumnNames.VIA_KEEPOUTS.ordinal() - 1];
  }

  Color[] getPlaceObstacleColors() {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    return precalculatedItemColors[ColumnNames.PLACE_KEEPOUTS.ordinal() - 1];
  }

  public void setTraceColors(Color[] pColorArr, boolean pFixed) {
    if (pFixed) {
      setColors(ColumnNames.FIXED_TRACES.ordinal(), pColorArr);
    } else {
      setColors(ColumnNames.TRACES.ordinal(), pColorArr);
    }
  }

  public void setViaColors(Color[] pColorArr, boolean pFixed) {
    if (pFixed) {
      setColors(ColumnNames.FIXED_VIAS.ordinal(), pColorArr);
    } else {
      setColors(ColumnNames.VIAS.ordinal(), pColorArr);
    }
  }

  public void setKeepoutColors(Color[] pColorArr) {
    setColors(ColumnNames.KEEPOUTS.ordinal(), pColorArr);
  }

  public void setViaKeepoutColors(Color[] pColorArr) {
    setColors(ColumnNames.VIA_KEEPOUTS.ordinal(), pColorArr);
  }

  public void setPlaceKeepoutColors(Color[] pColorArr) {
    setColors(ColumnNames.PLACE_KEEPOUTS.ordinal(), pColorArr);
  }

  private void setColors(int pItemType, Color[] pColorArr) {
    for (int layer = 0; layer < this.data.length - 1; layer++) {
      int colorIndex = layer % pColorArr.length;
      this.data[layer][pItemType] = pColorArr[colorIndex];
    }
    data[this.data.length - 1][pItemType] = pColorArr[pColorArr.length - 1];
    this.itemColorsPrecalculated = false;
  }

  private void precalculateItemColors() {
    precalculatedItemColors = new Color[ColumnNames.values().length - 1][];
    for (int i = 0; i < precalculatedItemColors.length; i++) {
      precalculatedItemColors[i] = new Color[data.length];
      Color[] currRow = precalculatedItemColors[i];
      for (int j = 0; j < data.length; j++) {
        currRow[j] = (Color) getValueAt(j, i + 1);
      }
    }
    this.itemColorsPrecalculated = true;
  }

  private enum ColumnNames {
    LAYER,
    TRACES,
    FIXED_TRACES,
    VIAS,
    FIXED_VIAS,
    PINS,
    CONDUCTION_AREAS,
    KEEPOUTS,
    VIA_KEEPOUTS,
    PLACE_KEEPOUTS
  }
}

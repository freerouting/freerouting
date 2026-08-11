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
  /** ItemColorTableModel. */
  public ItemColorTableModel(LayerStructure layerStructure, Locale locale) {
    super(layerStructure.arr.length, locale);

    int rowCount = layerStructure.arr.length;
    final int itemTypeCount = ColumnNames.values().length - 1;
    int signalLayerNo = 0;
    for (int layer = 0; layer < rowCount; layer++) {
      boolean isSignalLayer = layerStructure.arr[layer].isSignal;
      data[layer] = new Object[itemTypeCount + 1];
      Object[] currRow = data[layer];
      currRow[0] = layerStructure.arr[layer].name;
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
        } else { // power layer
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

  /** ItemColorTableModel. */
  public ItemColorTableModel(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    super(stream);
  }

  /** Copy constructor. */
  public ItemColorTableModel(ItemColorTableModel itemColorModel) {
    super(itemColorModel.data.length, itemColorModel.locale);
    for (int i = 0; i < this.data.length; i++) {
      this.data[i] = new Object[itemColorModel.data[i].length];
      System.arraycopy(itemColorModel.data[i], 0, this.data[i], 0, this.data[i].length);
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
  public String getColumnName(int col) {
    TextManager tm = new TextManager(ColorTableModel.class, this.locale);
    return tm.getText(ColumnNames.values()[col].toString());
  }

  @Override
  public void setValueAt(Object value, int row, int col) {
    super.setValueAt(value, row, col);
    this.itemColorsPrecalculated = false;
  }

  /** Don't need to implement this method unless your table's editable. */
  @Override
  public boolean isCellEditable(int row, int col) {
    // Note that the data/cell address is constant,
    // no matter where the cell appears onscreen.
    return col >= 1;
  }

  Color[] getTraceColors(boolean fixed) {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    Color[] result;
    if (fixed) {
      result = precalculatedItemColors[ColumnNames.FIXED_TRACES.ordinal() - 1];
    } else {
      result = precalculatedItemColors[ColumnNames.TRACES.ordinal() - 1];
    }
    return result;
  }

  Color[] getViaColors(boolean fixed) {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    Color[] result;
    if (fixed) {
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

  /** SetPinColors. */
  public void setPinColors(Color[] colorArr) {
    setColors(ColumnNames.PINS.ordinal(), colorArr);
  }

  Color[] getConductionColors() {
    if (!itemColorsPrecalculated) {
      precalculateItemColors();
    }
    return precalculatedItemColors[ColumnNames.CONDUCTION_AREAS.ordinal() - 1];
  }

  /** SetConductionColors. */
  public void setConductionColors(Color[] colorArr) {
    setColors(ColumnNames.CONDUCTION_AREAS.ordinal(), colorArr);
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

  /** SetTraceColors. */
  public void setTraceColors(Color[] colorArr, boolean fixed) {
    if (fixed) {
      setColors(ColumnNames.FIXED_TRACES.ordinal(), colorArr);
    } else {
      setColors(ColumnNames.TRACES.ordinal(), colorArr);
    }
  }

  /** SetViaColors. */
  public void setViaColors(Color[] colorArr, boolean fixed) {
    if (fixed) {
      setColors(ColumnNames.FIXED_VIAS.ordinal(), colorArr);
    } else {
      setColors(ColumnNames.VIAS.ordinal(), colorArr);
    }
  }

  /** SetKeepoutColors. */
  public void setKeepoutColors(Color[] colorArr) {
    setColors(ColumnNames.KEEPOUTS.ordinal(), colorArr);
  }

  /** SetViaKeepoutColors. */
  public void setViaKeepoutColors(Color[] colorArr) {
    setColors(ColumnNames.VIA_KEEPOUTS.ordinal(), colorArr);
  }

  /** SetPlaceKeepoutColors. */
  public void setPlaceKeepoutColors(Color[] colorArr) {
    setColors(ColumnNames.PLACE_KEEPOUTS.ordinal(), colorArr);
  }

  private void setColors(int itemType, Color[] colorArr) {
    for (int layer = 0; layer < this.data.length - 1; layer++) {
      int colorIndex = layer % colorArr.length;
      this.data[layer][itemType] = colorArr[colorIndex];
    }
    data[this.data.length - 1][itemType] = colorArr[colorArr.length - 1];
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

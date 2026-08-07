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
  public ItemColorTableModel(LayerStructure p_layer_structure, Locale p_locale) {
    super(p_layer_structure.arr.length, p_locale);

    int rowCount = p_layer_structure.arr.length;
    final int itemTypeCount = ColumnNames.values().length - 1;
    int signalLayerNo = 0;
    for (int layer = 0; layer < rowCount; layer++) {
      boolean isSignalLayer = p_layer_structure.arr[layer].isSignal;
      data[layer] = new Object[itemTypeCount + 1];
      Object[] currRow = data[layer];
      currRow[0] = p_layer_structure.arr[layer].name;
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

  public ItemColorTableModel(ObjectInputStream p_stream)
      throws IOException, ClassNotFoundException {
    super(p_stream);
  }

  /** Copy constructor. */
  public ItemColorTableModel(ItemColorTableModel p_item_color_model) {
    super(p_item_color_model.data.length, p_item_color_model.locale);
    for (int i = 0; i < this.data.length; i++) {
      this.data[i] = new Object[p_item_color_model.data[i].length];
      System.arraycopy(p_item_color_model.data[i], 0, this.data[i], 0, this.data[i].length);
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
  public String getColumnName(int p_col) {
    TextManager tm = new TextManager(ColorTableModel.class, this.locale);
    return tm.getText(ColumnNames.values()[p_col].toString());
  }

  @Override
  public void setValueAt(Object p_value, int p_row, int p_col) {
    super.setValueAt(p_value, p_row, p_col);
    this.itemColorsPrecalculated = false;
  }

  /** Don't need to implement this method unless your table's editable. */
  @Override
  public boolean isCellEditable(int p_row, int p_col) {
    // Note that the data/cell address is constant,
    // no matter where the cell appears onscreen.
    return p_col >= 1;
  }

  Color[] get_trace_colors(boolean p_fixed) {
    if (!itemColorsPrecalculated) {
      precalculate_item_colors();
    }
    Color[] result;
    if (p_fixed) {
      result = precalculatedItemColors[ColumnNames.FIXED_TRACES.ordinal() - 1];
    } else {
      result = precalculatedItemColors[ColumnNames.TRACES.ordinal() - 1];
    }
    return result;
  }

  Color[] get_via_colors(boolean p_fixed) {
    if (!itemColorsPrecalculated) {
      precalculate_item_colors();
    }
    Color[] result;
    if (p_fixed) {
      result = precalculatedItemColors[ColumnNames.FIXED_VIAS.ordinal() - 1];
    } else {
      result = precalculatedItemColors[ColumnNames.VIAS.ordinal() - 1];
    }
    return result;
  }

  Color[] get_pin_colors() {
    if (!itemColorsPrecalculated) {
      precalculate_item_colors();
    }
    return precalculatedItemColors[ColumnNames.PINS.ordinal() - 1];
  }

  public void set_pin_colors(Color[] p_color_arr) {
    set_colors(ColumnNames.PINS.ordinal(), p_color_arr);
  }

  Color[] get_conduction_colors() {
    if (!itemColorsPrecalculated) {
      precalculate_item_colors();
    }
    return precalculatedItemColors[ColumnNames.CONDUCTION_AREAS.ordinal() - 1];
  }

  public void set_conduction_colors(Color[] p_color_arr) {
    set_colors(ColumnNames.CONDUCTION_AREAS.ordinal(), p_color_arr);
  }

  Color[] get_obstacle_colors() {
    if (!itemColorsPrecalculated) {
      precalculate_item_colors();
    }
    return precalculatedItemColors[ColumnNames.KEEPOUTS.ordinal() - 1];
  }

  Color[] get_via_obstacle_colors() {
    if (!itemColorsPrecalculated) {
      precalculate_item_colors();
    }
    return precalculatedItemColors[ColumnNames.VIA_KEEPOUTS.ordinal() - 1];
  }

  Color[] get_place_obstacle_colors() {
    if (!itemColorsPrecalculated) {
      precalculate_item_colors();
    }
    return precalculatedItemColors[ColumnNames.PLACE_KEEPOUTS.ordinal() - 1];
  }

  public void set_trace_colors(Color[] p_color_arr, boolean p_fixed) {
    if (p_fixed) {
      set_colors(ColumnNames.FIXED_TRACES.ordinal(), p_color_arr);
    } else {
      set_colors(ColumnNames.TRACES.ordinal(), p_color_arr);
    }
  }

  public void set_via_colors(Color[] p_color_arr, boolean p_fixed) {
    if (p_fixed) {
      set_colors(ColumnNames.FIXED_VIAS.ordinal(), p_color_arr);
    } else {
      set_colors(ColumnNames.VIAS.ordinal(), p_color_arr);
    }
  }

  public void set_keepout_colors(Color[] p_color_arr) {
    set_colors(ColumnNames.KEEPOUTS.ordinal(), p_color_arr);
  }

  public void set_via_keepout_colors(Color[] p_color_arr) {
    set_colors(ColumnNames.VIA_KEEPOUTS.ordinal(), p_color_arr);
  }

  public void set_place_keepout_colors(Color[] p_color_arr) {
    set_colors(ColumnNames.PLACE_KEEPOUTS.ordinal(), p_color_arr);
  }

  private void set_colors(int p_item_type, Color[] p_color_arr) {
    for (int layer = 0; layer < this.data.length - 1; layer++) {
      int colorIndex = layer % p_color_arr.length;
      this.data[layer][p_item_type] = p_color_arr[colorIndex];
    }
    data[this.data.length - 1][p_item_type] = p_color_arr[p_color_arr.length - 1];
    this.itemColorsPrecalculated = false;
  }

  private void precalculate_item_colors() {
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

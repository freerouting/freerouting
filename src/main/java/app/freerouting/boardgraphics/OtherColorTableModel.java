package app.freerouting.boardgraphics;

import app.freerouting.util.TextManager;
import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Locale;

/** Stores the colors used for the background and highlighting. */
public class OtherColorTableModel extends ColorTableModel implements Serializable {

  public OtherColorTableModel(Locale p_locale) {
    super(1, p_locale);
    data[0] = new Color[ColumnNames.values().length];
    Object[] currRow = data[0];
    currRow[ColumnNames.BACKGROUND.ordinal()] = new Color(0, 16, 35);
    currRow[ColumnNames.HIGHLIGHT.ordinal()] = Color.white;
    currRow[ColumnNames.INCOMPLETES.ordinal()] = Color.white;
    currRow[ColumnNames.OUTLINE.ordinal()] = new Color(100, 150, 255);
    currRow[ColumnNames.VIOLATIONS.ordinal()] = Color.magenta;
    currRow[ColumnNames.COMPONENT_FRONT.ordinal()] = new Color(255, 38, 226);
    currRow[ColumnNames.COMPONENT_BACK.ordinal()] = new Color(38, 233, 255);
    currRow[ColumnNames.LENGTH_MATCHING_AREA.ordinal()] = Color.green;
    currRow[ColumnNames.DRILL_HOLE.ordinal()] = Color.black;
    currRow[ColumnNames.SILKSCREEN_FRONT.ordinal()] = new Color(242, 237, 161);
    currRow[ColumnNames.SILKSCREEN_BACK.ordinal()] = new Color(232, 178, 167);
    currRow[ColumnNames.COURTYARD_FRONT.ordinal()] = new Color(255, 38, 226);
    currRow[ColumnNames.COURTYARD_BACK.ordinal()] = new Color(38, 233, 255);
    currRow[ColumnNames.FAB_FRONT.ordinal()] = new Color(175, 175, 175);
    currRow[ColumnNames.FAB_BACK.ordinal()] = new Color(88, 93, 132);
  }

  public OtherColorTableModel(ObjectInputStream p_stream)
      throws IOException, ClassNotFoundException {
    super(p_stream);
  }

  /** Copy constructor. */
  public OtherColorTableModel(OtherColorTableModel p_item_color_model) {
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
  public String getColumnName(int p_col) {
    TextManager tm = new TextManager(ColorTableModel.class, this.locale);
    return tm.getText(ColumnNames.values()[p_col].toString());
  }

  @Override
  public boolean isCellEditable(int p_row, int p_col) {
    return true;
  }

  private Color getColorSafe(ColumnNames col, Color defaultColor) {
    int idx = col.ordinal();
    if (data != null && data.length > 0 && data[0] != null && idx >= 0 && idx < data[0].length) {
      Color c = (Color) data[0][idx];
      if (c != null) {
        return c;
      }
    }
    return defaultColor;
  }

  private void setColorSafe(ColumnNames col, Color p_color) {
    int idx = col.ordinal();
    if (data != null && data.length > 0 && data[0] != null && idx >= 0 && idx < data[0].length) {
      data[0][idx] = p_color;
    }
  }

  public Color getBackgroundColor() {
    return getColorSafe(ColumnNames.BACKGROUND, new Color(0, 16, 35));
  }

  public void setBackgroundColor(Color p_color) {
    setColorSafe(ColumnNames.BACKGROUND, p_color);
  }

  public Color getHilightColor() {
    return getColorSafe(ColumnNames.HIGHLIGHT, Color.white);
  }

  public void setHilightColor(Color p_color) {
    setColorSafe(ColumnNames.HIGHLIGHT, p_color);
  }

  public Color getIncompleteColor() {
    return getColorSafe(ColumnNames.INCOMPLETES, Color.white);
  }

  public void setIncompleteColor(Color p_color) {
    setColorSafe(ColumnNames.INCOMPLETES, p_color);
  }

  public Color getOutlineColor() {
    return getColorSafe(ColumnNames.OUTLINE, new Color(100, 150, 255));
  }

  public void setOutlineColor(Color p_color) {
    setColorSafe(ColumnNames.OUTLINE, p_color);
  }

  public Color getViolationsColor() {
    return getColorSafe(ColumnNames.VIOLATIONS, Color.magenta);
  }

  public void setViolationsColor(Color p_color) {
    setColorSafe(ColumnNames.VIOLATIONS, p_color);
  }

  public Color getComponentColor(boolean p_front) {
    if (p_front) {
      return getColorSafe(ColumnNames.COMPONENT_FRONT, new Color(255, 38, 226));
    } else {
      return getColorSafe(ColumnNames.COMPONENT_BACK, new Color(38, 233, 255));
    }
  }

  public Color getLengthMatchingAreaColor() {
    return getColorSafe(ColumnNames.LENGTH_MATCHING_AREA, Color.green);
  }

  public void setLengthMatchingAreaColor(Color p_color) {
    setColorSafe(ColumnNames.LENGTH_MATCHING_AREA, p_color);
  }

  public void setComponentColor(Color p_color, boolean p_front) {
    if (p_front) {
      setColorSafe(ColumnNames.COMPONENT_FRONT, p_color);
    } else {
      setColorSafe(ColumnNames.COMPONENT_BACK, p_color);
    }
  }

  public Color getDrillHoleColor() {
    return getColorSafe(ColumnNames.DRILL_HOLE, Color.black);
  }

  public void setDrillHoleColor(Color p_color) {
    setColorSafe(ColumnNames.DRILL_HOLE, p_color);
  }

  public Color getSilkscreenColor(boolean p_front) {
    if (p_front) {
      return getColorSafe(ColumnNames.SILKSCREEN_FRONT, new Color(242, 237, 161));
    } else {
      return getColorSafe(ColumnNames.SILKSCREEN_BACK, new Color(232, 178, 167));
    }
  }

  public void setSilkscreenColor(Color p_color, boolean p_front) {
    if (p_front) {
      setColorSafe(ColumnNames.SILKSCREEN_FRONT, p_color);
    } else {
      setColorSafe(ColumnNames.SILKSCREEN_BACK, p_color);
    }
  }

  public Color getCourtyardColor(boolean p_front) {
    if (p_front) {
      return getColorSafe(ColumnNames.COURTYARD_FRONT, new Color(255, 38, 226));
    } else {
      return getColorSafe(ColumnNames.COURTYARD_BACK, new Color(38, 233, 255));
    }
  }

  public void setCourtyardColor(Color p_color, boolean p_front) {
    if (p_front) {
      setColorSafe(ColumnNames.COURTYARD_FRONT, p_color);
    } else {
      setColorSafe(ColumnNames.COURTYARD_BACK, p_color);
    }
  }

  public Color getFabColor(boolean p_front) {
    if (p_front) {
      return getColorSafe(ColumnNames.FAB_FRONT, new Color(175, 175, 175));
    } else {
      return getColorSafe(ColumnNames.FAB_BACK, new Color(88, 93, 132));
    }
  }

  public void setFabColor(Color p_color, boolean p_front) {
    if (p_front) {
      setColorSafe(ColumnNames.FAB_FRONT, p_color);
    } else {
      setColorSafe(ColumnNames.FAB_BACK, p_color);
    }
  }

  public enum ColumnNames {
    BACKGROUND,
    HIGHLIGHT,
    INCOMPLETES,
    VIOLATIONS,
    OUTLINE,
    COMPONENT_FRONT,
    COMPONENT_BACK,
    LENGTH_MATCHING_AREA,
    DRILL_HOLE,
    SILKSCREEN_FRONT,
    SILKSCREEN_BACK,
    COURTYARD_FRONT,
    COURTYARD_BACK,
    FAB_FRONT,
    FAB_BACK
  }
}

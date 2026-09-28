package app.freerouting.gui.rendering;

import app.freerouting.util.TextManager;
import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Locale;

/** Stores the colors used for the background and highlighting. */
public class OtherColorTableModel extends ColorTableModel implements Serializable {

  /** OtherColorTableModel. */
  public OtherColorTableModel(Locale locale) {
    super(1, locale);
    data[0] = new Color[ColumnNames.values().length];
    Object[] currentRow = data[0];
    currentRow[ColumnNames.BACKGROUND.ordinal()] = new Color(0, 16, 35);
    currentRow[ColumnNames.HIGHLIGHT.ordinal()] = Color.white;
    currentRow[ColumnNames.INCOMPLETES.ordinal()] = Color.white;
    currentRow[ColumnNames.OUTLINE.ordinal()] = new Color(100, 150, 255);
    currentRow[ColumnNames.VIOLATIONS.ordinal()] = Color.magenta;
    currentRow[ColumnNames.COMPONENT_FRONT.ordinal()] = new Color(255, 38, 226);
    currentRow[ColumnNames.COMPONENT_BACK.ordinal()] = new Color(38, 233, 255);
    currentRow[ColumnNames.LENGTH_MATCHING_AREA.ordinal()] = Color.green;
    currentRow[ColumnNames.DRILL_HOLE.ordinal()] = Color.black;
    currentRow[ColumnNames.SILKSCREEN_FRONT.ordinal()] = new Color(242, 237, 161);
    currentRow[ColumnNames.SILKSCREEN_BACK.ordinal()] = new Color(232, 178, 167);
    currentRow[ColumnNames.COURTYARD_FRONT.ordinal()] = new Color(255, 38, 226);
    currentRow[ColumnNames.COURTYARD_BACK.ordinal()] = new Color(38, 233, 255);
    currentRow[ColumnNames.FAB_FRONT.ordinal()] = new Color(175, 175, 175);
    currentRow[ColumnNames.FAB_BACK.ordinal()] = new Color(88, 93, 132);
  }

  /** OtherColorTableModel. */
  public OtherColorTableModel(ObjectInputStream stream) throws IOException, ClassNotFoundException {
    super(stream);
  }

  /** Copy constructor. */
  public OtherColorTableModel(OtherColorTableModel itemColorModel) {
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
  public String getColumnName(int col) {
    TextManager tm = new TextManager(ColorTableModel.class, this.locale);
    return tm.getText(ColumnNames.values()[col].toString());
  }

  @Override
  public boolean isCellEditable(int row, int col) {
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

  private void setColorSafe(ColumnNames col, Color color) {
    int idx = col.ordinal();
    if (data != null && data.length > 0 && data[0] != null && idx >= 0 && idx < data[0].length) {
      data[0][idx] = color;
    }
  }

  public Color getBackgroundColor() {
    return getColorSafe(ColumnNames.BACKGROUND, new Color(0, 16, 35));
  }

  /** SetBackgroundColor. */
  public void setBackgroundColor(Color color) {
    setColorSafe(ColumnNames.BACKGROUND, color);
  }

  public Color getHighlightColor() {
    return getColorSafe(ColumnNames.HIGHLIGHT, Color.white);
  }

  /** SetHighlightColor. */
  public void setHighlightColor(Color color) {
    setColorSafe(ColumnNames.HIGHLIGHT, color);
  }

  public Color getIncompleteColor() {
    return getColorSafe(ColumnNames.INCOMPLETES, Color.white);
  }

  /** SetIncompleteColor. */
  public void setIncompleteColor(Color color) {
    setColorSafe(ColumnNames.INCOMPLETES, color);
  }

  public Color getOutlineColor() {
    return getColorSafe(ColumnNames.OUTLINE, new Color(100, 150, 255));
  }

  /** SetOutlineColor. */
  public void setOutlineColor(Color color) {
    setColorSafe(ColumnNames.OUTLINE, color);
  }

  public Color getViolationsColor() {
    return getColorSafe(ColumnNames.VIOLATIONS, Color.magenta);
  }

  /** SetViolationsColor. */
  public void setViolationsColor(Color color) {
    setColorSafe(ColumnNames.VIOLATIONS, color);
  }

  /** GetComponentColor. */
  public Color getComponentColor(boolean front) {
    if (front) {
      return getColorSafe(ColumnNames.COMPONENT_FRONT, new Color(255, 38, 226));
    } else {
      return getColorSafe(ColumnNames.COMPONENT_BACK, new Color(38, 233, 255));
    }
  }

  public Color getLengthMatchingAreaColor() {
    return getColorSafe(ColumnNames.LENGTH_MATCHING_AREA, Color.green);
  }

  /** SetLengthMatchingAreaColor. */
  public void setLengthMatchingAreaColor(Color color) {
    setColorSafe(ColumnNames.LENGTH_MATCHING_AREA, color);
  }

  /** SetComponentColor. */
  public void setComponentColor(Color color, boolean front) {
    if (front) {
      setColorSafe(ColumnNames.COMPONENT_FRONT, color);
    } else {
      setColorSafe(ColumnNames.COMPONENT_BACK, color);
    }
  }

  public Color getDrillHoleColor() {
    return getColorSafe(ColumnNames.DRILL_HOLE, Color.black);
  }

  /** SetDrillHoleColor. */
  public void setDrillHoleColor(Color color) {
    setColorSafe(ColumnNames.DRILL_HOLE, color);
  }

  /** GetSilkscreenColor. */
  public Color getSilkscreenColor(boolean front) {
    if (front) {
      return getColorSafe(ColumnNames.SILKSCREEN_FRONT, new Color(242, 237, 161));
    } else {
      return getColorSafe(ColumnNames.SILKSCREEN_BACK, new Color(232, 178, 167));
    }
  }

  /** SetSilkscreenColor. */
  public void setSilkscreenColor(Color color, boolean front) {
    if (front) {
      setColorSafe(ColumnNames.SILKSCREEN_FRONT, color);
    } else {
      setColorSafe(ColumnNames.SILKSCREEN_BACK, color);
    }
  }

  /** GetCourtyardColor. */
  public Color getCourtyardColor(boolean front) {
    if (front) {
      return getColorSafe(ColumnNames.COURTYARD_FRONT, new Color(255, 38, 226));
    } else {
      return getColorSafe(ColumnNames.COURTYARD_BACK, new Color(38, 233, 255));
    }
  }

  /** SetCourtyardColor. */
  public void setCourtyardColor(Color color, boolean front) {
    if (front) {
      setColorSafe(ColumnNames.COURTYARD_FRONT, color);
    } else {
      setColorSafe(ColumnNames.COURTYARD_BACK, color);
    }
  }

  /** GetFabColor. */
  public Color getFabColor(boolean front) {
    if (front) {
      return getColorSafe(ColumnNames.FAB_FRONT, new Color(175, 175, 175));
    } else {
      return getColorSafe(ColumnNames.FAB_BACK, new Color(88, 93, 132));
    }
  }

  /** SetFabColor. */
  public void setFabColor(Color color, boolean front) {
    if (front) {
      setColorSafe(ColumnNames.FAB_FRONT, color);
    } else {
      setColorSafe(ColumnNames.FAB_BACK, color);
    }
  }

  /** Column names for the other-color table. */
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

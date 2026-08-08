package app.freerouting.boardgraphics;

import app.freerouting.util.TextManager;
import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Locale;

/** Stores the colors used for the background and highlighting. */
public class OtherColorTableModel extends ColorTableModel implements Serializable {

  public OtherColorTableModel(Locale pLocale) {
    super(1, pLocale);
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

  public OtherColorTableModel(ObjectInputStream pStream)
      throws IOException, ClassNotFoundException {
    super(pStream);
  }

  /** Copy constructor. */
  public OtherColorTableModel(OtherColorTableModel pItemColorModel) {
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
  public String getColumnName(int pCol) {
    TextManager tm = new TextManager(ColorTableModel.class, this.locale);
    return tm.getText(ColumnNames.values()[pCol].toString());
  }

  @Override
  public boolean isCellEditable(int pRow, int pCol) {
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

  private void setColorSafe(ColumnNames col, Color pColor) {
    int idx = col.ordinal();
    if (data != null && data.length > 0 && data[0] != null && idx >= 0 && idx < data[0].length) {
      data[0][idx] = pColor;
    }
  }

  public Color getBackgroundColor() {
    return getColorSafe(ColumnNames.BACKGROUND, new Color(0, 16, 35));
  }

  public void setBackgroundColor(Color pColor) {
    setColorSafe(ColumnNames.BACKGROUND, pColor);
  }

  public Color getHighlightColor() {
    return getColorSafe(ColumnNames.HIGHLIGHT, Color.white);
  }

  public void setHighlightColor(Color pColor) {
    setColorSafe(ColumnNames.HIGHLIGHT, pColor);
  }

  public Color getIncompleteColor() {
    return getColorSafe(ColumnNames.INCOMPLETES, Color.white);
  }

  public void setIncompleteColor(Color pColor) {
    setColorSafe(ColumnNames.INCOMPLETES, pColor);
  }

  public Color getOutlineColor() {
    return getColorSafe(ColumnNames.OUTLINE, new Color(100, 150, 255));
  }

  public void setOutlineColor(Color pColor) {
    setColorSafe(ColumnNames.OUTLINE, pColor);
  }

  public Color getViolationsColor() {
    return getColorSafe(ColumnNames.VIOLATIONS, Color.magenta);
  }

  public void setViolationsColor(Color pColor) {
    setColorSafe(ColumnNames.VIOLATIONS, pColor);
  }

  public Color getComponentColor(boolean pFront) {
    if (pFront) {
      return getColorSafe(ColumnNames.COMPONENT_FRONT, new Color(255, 38, 226));
    } else {
      return getColorSafe(ColumnNames.COMPONENT_BACK, new Color(38, 233, 255));
    }
  }

  public Color getLengthMatchingAreaColor() {
    return getColorSafe(ColumnNames.LENGTH_MATCHING_AREA, Color.green);
  }

  public void setLengthMatchingAreaColor(Color pColor) {
    setColorSafe(ColumnNames.LENGTH_MATCHING_AREA, pColor);
  }

  public void setComponentColor(Color pColor, boolean pFront) {
    if (pFront) {
      setColorSafe(ColumnNames.COMPONENT_FRONT, pColor);
    } else {
      setColorSafe(ColumnNames.COMPONENT_BACK, pColor);
    }
  }

  public Color getDrillHoleColor() {
    return getColorSafe(ColumnNames.DRILL_HOLE, Color.black);
  }

  public void setDrillHoleColor(Color pColor) {
    setColorSafe(ColumnNames.DRILL_HOLE, pColor);
  }

  public Color getSilkscreenColor(boolean pFront) {
    if (pFront) {
      return getColorSafe(ColumnNames.SILKSCREEN_FRONT, new Color(242, 237, 161));
    } else {
      return getColorSafe(ColumnNames.SILKSCREEN_BACK, new Color(232, 178, 167));
    }
  }

  public void setSilkscreenColor(Color pColor, boolean pFront) {
    if (pFront) {
      setColorSafe(ColumnNames.SILKSCREEN_FRONT, pColor);
    } else {
      setColorSafe(ColumnNames.SILKSCREEN_BACK, pColor);
    }
  }

  public Color getCourtyardColor(boolean pFront) {
    if (pFront) {
      return getColorSafe(ColumnNames.COURTYARD_FRONT, new Color(255, 38, 226));
    } else {
      return getColorSafe(ColumnNames.COURTYARD_BACK, new Color(38, 233, 255));
    }
  }

  public void setCourtyardColor(Color pColor, boolean pFront) {
    if (pFront) {
      setColorSafe(ColumnNames.COURTYARD_FRONT, pColor);
    } else {
      setColorSafe(ColumnNames.COURTYARD_BACK, pColor);
    }
  }

  public Color getFabColor(boolean pFront) {
    if (pFront) {
      return getColorSafe(ColumnNames.FAB_FRONT, new Color(175, 175, 175));
    } else {
      return getColorSafe(ColumnNames.FAB_BACK, new Color(88, 93, 132));
    }
  }

  public void setFabColor(Color pColor, boolean pFront) {
    if (pFront) {
      setColorSafe(ColumnNames.FAB_FRONT, pColor);
    } else {
      setColorSafe(ColumnNames.FAB_BACK, pColor);
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

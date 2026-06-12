package app.freerouting.boardgraphics;

import app.freerouting.management.TextManager;
import java.awt.Color;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Locale;

/**
 * Stores the colors used for the background and highlighting.
 */
public class OtherColorTableModel extends ColorTableModel implements Serializable {

  public OtherColorTableModel(Locale p_locale) {
    super(1, p_locale);
    data[0] = new Color[ColumnNames.values().length];
    Object[] curr_row = data[0];
    curr_row[ColumnNames.BACKGROUND.ordinal()] = new Color(0, 16, 35);
    curr_row[ColumnNames.HIGHLIGHT.ordinal()] = Color.white;
    curr_row[ColumnNames.INCOMPLETES.ordinal()] = Color.white;
    curr_row[ColumnNames.OUTLINE.ordinal()] = new Color(100, 150, 255);
    curr_row[ColumnNames.VIOLATIONS.ordinal()] = Color.magenta;
    curr_row[ColumnNames.COMPONENT_FRONT.ordinal()] = new Color(255, 38, 226);
    curr_row[ColumnNames.COMPONENT_BACK.ordinal()] = new Color(38, 233, 255);
    curr_row[ColumnNames.LENGTH_MATCHING_AREA.ordinal()] = Color.green;
    curr_row[ColumnNames.DRILL_HOLE.ordinal()] = Color.black;
    curr_row[ColumnNames.SILKSCREEN_FRONT.ordinal()] = new Color(242, 237, 161);
    curr_row[ColumnNames.SILKSCREEN_BACK.ordinal()] = new Color(232, 178, 167);
    curr_row[ColumnNames.COURTYARD_FRONT.ordinal()] = new Color(255, 38, 226);
    curr_row[ColumnNames.COURTYARD_BACK.ordinal()] = new Color(38, 233, 255);
    curr_row[ColumnNames.FAB_FRONT.ordinal()] = new Color(175, 175, 175);
    curr_row[ColumnNames.FAB_BACK.ordinal()] = new Color(88, 93, 132);
  }

  public OtherColorTableModel(ObjectInputStream p_stream) throws IOException, ClassNotFoundException {
    super(p_stream);
  }

  /**
   * Copy constructor.
   */
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

  private Color get_color_safe(ColumnNames col, Color defaultColor) {
    int idx = col.ordinal();
    if (data != null && data.length > 0 && data[0] != null && idx >= 0 && idx < data[0].length) {
      Color c = (Color) data[0][idx];
      if (c != null) {
        return c;
      }
    }
    return defaultColor;
  }

  private void set_color_safe(ColumnNames col, Color p_color) {
    int idx = col.ordinal();
    if (data != null && data.length > 0 && data[0] != null && idx >= 0 && idx < data[0].length) {
      data[0][idx] = p_color;
    }
  }

  public Color get_background_color() {
    return get_color_safe(ColumnNames.BACKGROUND, new Color(0, 16, 35));
  }

  public void set_background_color(Color p_color) {
    set_color_safe(ColumnNames.BACKGROUND, p_color);
  }

  public Color get_hilight_color() {
    return get_color_safe(ColumnNames.HIGHLIGHT, Color.white);
  }

  public void set_hilight_color(Color p_color) {
    set_color_safe(ColumnNames.HIGHLIGHT, p_color);
  }

  public Color get_incomplete_color() {
    return get_color_safe(ColumnNames.INCOMPLETES, Color.white);
  }

  public void set_incomplete_color(Color p_color) {
    set_color_safe(ColumnNames.INCOMPLETES, p_color);
  }

  public Color get_outline_color() {
    return get_color_safe(ColumnNames.OUTLINE, new Color(100, 150, 255));
  }

  public void set_outline_color(Color p_color) {
    set_color_safe(ColumnNames.OUTLINE, p_color);
  }

  public Color get_violations_color() {
    return get_color_safe(ColumnNames.VIOLATIONS, Color.magenta);
  }

  public void set_violations_color(Color p_color) {
    set_color_safe(ColumnNames.VIOLATIONS, p_color);
  }

  public Color get_component_color(boolean p_front) {
    if (p_front) {
      return get_color_safe(ColumnNames.COMPONENT_FRONT, new Color(255, 38, 226));
    } else {
      return get_color_safe(ColumnNames.COMPONENT_BACK, new Color(38, 233, 255));
    }
  }

  public Color get_length_matching_area_color() {
    return get_color_safe(ColumnNames.LENGTH_MATCHING_AREA, Color.green);
  }

  public void set_length_matching_area_color(Color p_color) {
    set_color_safe(ColumnNames.LENGTH_MATCHING_AREA, p_color);
  }

  public void set_component_color(Color p_color, boolean p_front) {
    if (p_front) {
      set_color_safe(ColumnNames.COMPONENT_FRONT, p_color);
    } else {
      set_color_safe(ColumnNames.COMPONENT_BACK, p_color);
    }
  }

  public Color get_drill_hole_color() {
    return get_color_safe(ColumnNames.DRILL_HOLE, Color.black);
  }

  public void set_drill_hole_color(Color p_color) {
    set_color_safe(ColumnNames.DRILL_HOLE, p_color);
  }

  public Color get_silkscreen_color(boolean p_front) {
    if (p_front) {
      return get_color_safe(ColumnNames.SILKSCREEN_FRONT, new Color(242, 237, 161));
    } else {
      return get_color_safe(ColumnNames.SILKSCREEN_BACK, new Color(232, 178, 167));
    }
  }

  public void set_silkscreen_color(Color p_color, boolean p_front) {
    if (p_front) {
      set_color_safe(ColumnNames.SILKSCREEN_FRONT, p_color);
    } else {
      set_color_safe(ColumnNames.SILKSCREEN_BACK, p_color);
    }
  }

  public Color get_courtyard_color(boolean p_front) {
    if (p_front) {
      return get_color_safe(ColumnNames.COURTYARD_FRONT, new Color(255, 38, 226));
    } else {
      return get_color_safe(ColumnNames.COURTYARD_BACK, new Color(38, 233, 255));
    }
  }

  public void set_courtyard_color(Color p_color, boolean p_front) {
    if (p_front) {
      set_color_safe(ColumnNames.COURTYARD_FRONT, p_color);
    } else {
      set_color_safe(ColumnNames.COURTYARD_BACK, p_color);
    }
  }

  public Color get_fab_color(boolean p_front) {
    if (p_front) {
      return get_color_safe(ColumnNames.FAB_FRONT, new Color(175, 175, 175));
    } else {
      return get_color_safe(ColumnNames.FAB_BACK, new Color(88, 93, 132));
    }
  }

  public void set_fab_color(Color p_color, boolean p_front) {
    if (p_front) {
      set_color_safe(ColumnNames.FAB_FRONT, p_color);
    } else {
      set_color_safe(ColumnNames.FAB_BACK, p_color);
    }
  }

  public enum ColumnNames {
    BACKGROUND, HIGHLIGHT, INCOMPLETES, VIOLATIONS, OUTLINE, COMPONENT_FRONT, COMPONENT_BACK, LENGTH_MATCHING_AREA,
    DRILL_HOLE, SILKSCREEN_FRONT, SILKSCREEN_BACK, COURTYARD_FRONT, COURTYARD_BACK, FAB_FRONT, FAB_BACK
  }
}
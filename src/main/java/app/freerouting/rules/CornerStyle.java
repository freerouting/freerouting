package app.freerouting.rules;

import java.io.Serializable;

/** Geometric corner style for trace length tuning meander patterns. */
public enum CornerStyle implements Serializable {
  FILLET,
  CHAMFER;

  /**
   * Parses a corner style from a string in a case-insensitive manner.
   *
   * @param name string name of the corner style
   * @return matching CornerStyle, or FILLET by default if null or unrecognised
   */
  public static CornerStyle parse(String name) {
    if (name == null || name.isBlank()) {
      return FILLET;
    }
    try {
      return CornerStyle.valueOf(name.trim().toUpperCase());
    } catch (IllegalArgumentException _) {
      return FILLET;
    }
  }

  @Override
  public String toString() {
    return super.toString().toLowerCase();
  }
}

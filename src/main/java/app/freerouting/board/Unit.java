package app.freerouting.board;

import java.io.Serializable;

/** Enum for the user units inch, mil or millimeter. */
public enum Unit implements Serializable {
  MIL(25.4),
  INCH(25_400),
  MM(1000),
  UM(1);

  private final double micrometers;

  Unit(double micrometers) {
    this.micrometers = micrometers;
  }

  @Override
  public String toString() {
    return super.toString().toLowerCase();
  }

  /** Scales p_value from p_from_unit to p_to_unit */
  public static double scale(double p_value, Unit p_from_unit, Unit p_to_unit) {
    return p_value * p_from_unit.micrometers / p_to_unit.micrometers;
  }

  /**
   * Return the unit corresponding to the input string, or null, if the input string is different
   * from mil, inch and mm.
   */
  public static Unit from_string(String p_string) {
    try {
      return Unit.valueOf(p_string.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}

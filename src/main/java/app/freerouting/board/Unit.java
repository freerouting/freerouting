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

  /** Scales p_value from p_from_unit to p_to_unit */
  public static double scale(double pValue, Unit pFromUnit, Unit pToUnit) {
    return pValue * pFromUnit.micrometers / pToUnit.micrometers;
  }

  /**
   * Return the unit corresponding to the input string, or null, if the input string is different
   * from mil, inch and mm.
   */
  public static Unit fromString(String pString) {
    try {
      return Unit.valueOf(pString.toUpperCase());
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  @Override
  public String toString() {
    return super.toString().toLowerCase();
  }
}

package app.freerouting.board;

import java.io.Serializable;

/** Enum for the user units inch, mil or millimeter. */
public enum Unit implements Serializable {
  MIL {
    @Override
    public String toString() {
      return "mil";
    }
  },
  INCH {
    @Override
    public String toString() {
      return "inch";
    }
  },

  MM {
    @Override
    public String toString() {
      return "mm";
    }
  },

  UM {
    @Override
    public String toString() {
      return "um";
    }
  };

  public static final double INCH_TO_MM = 25.4;

  /** Scales p_value from p_from_unit to p_to_unit */
  public static double scale(double p_value, Unit p_from_unit, Unit p_to_unit) {
    double result;
    if (p_from_unit == p_to_unit) {
      result = p_value;
    } else if (p_from_unit == INCH) {
      if (p_to_unit == MIL) {
        result = p_value * 1000.0;
      } else if (p_to_unit == MM) {
        result = p_value * INCH_TO_MM;
      } else // um
      {
        result = p_value * INCH_TO_MM * 1000.0;
      }
    } else if (p_from_unit == MIL) {
      if (p_to_unit == INCH) {
        result = p_value / 1000.0;
      } else if (p_to_unit == MM) {
        result = p_value * INCH_TO_MM;
      } else // um
      {
        result = (p_value * INCH_TO_MM) * 1000.0;
      }
    } else if (p_from_unit == MM) {
      if (p_to_unit == INCH) {
        result = p_value / INCH_TO_MM;
      } else if (p_to_unit == UM) {
        result = p_value * 1000;
      } else // mil
      {
        result = (p_value * 1000.0) / INCH_TO_MM;
      }
    } else // UM
    {
      if (p_to_unit == INCH) {
        result = p_value / (INCH_TO_MM * 1000.0);
      } else if (p_to_unit == MM) {
        result = p_value / 1000.0;
      } else // mil
      {
        result = p_value / INCH_TO_MM;
      }
    }
    return result;
  }

  /**
   * Return the unit corresponding to the input string, or null, if the input string is different
   * from mil, inch and mm.
   */
  public static Unit from_string(String p_string) {
    Unit result;
    if (p_string.equalsIgnoreCase("mil")) {
      result = MIL;
    } else if (p_string.equalsIgnoreCase("inch")) {
      result = INCH;
    } else if (p_string.equalsIgnoreCase("mm")) {
      result = MM;
    } else if (p_string.equalsIgnoreCase("um")) {
      result = UM;
    } else {
      result = null;
    }
    return result;
  }
}

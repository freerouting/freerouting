package app.freerouting.board;

/** Enum for angle restrictions none, fortyfive degree and ninety degree. */
public enum AngleRestriction {
  // ordinal() and values() rely on the order
  NONE, FORTYFIVE_DEGREE, NINETY_DEGREE;

  /** Returns instance for given number */
  public static AngleRestriction valueOf(int i) {
    return values()[i];
  }

  /** Returns the number of this instance */
  public int getValue() {
    return ordinal();
  }
}

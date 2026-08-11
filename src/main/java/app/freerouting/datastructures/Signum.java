package app.freerouting.datastructures;

/** Implements the mathematical signum function. */
public final class Signum {

  public static final Signum POSITIVE = new Signum("positive");
  public static final Signum NEGATIVE = new Signum("negative");
  public static final Signum ZERO = new Signum("zero");
  private final String name;

  private Signum(String name) {
    this.name = name;
  }

  /** Returns the signum of value. Values are Signum.POSITIVE, Signum.NEGATIVE and Signum.ZERO. */
  public static Signum of(double value) {
    Signum result;

    if (value > 0) {
      result = POSITIVE;
    } else if (value < 0) {
      result = NEGATIVE;
    } else {
      result = ZERO;
    }
    return result;
  }

  /** Returns the signum of value as an int. Values are +1, 0 and -1. */
  public static int asInt(double value) {
    int result;

    if (value > 0) {
      result = 1;
    } else if (value < 0) {
      result = -1;
    } else {
      result = 0;
    }
    return result;
  }

  /** Returns the string of this instance. */
  public String toString() {
    return name;
  }

  /** Returns the opposite Signum of this Signum. */
  public final Signum negate() {
    Signum result;
    if (this == POSITIVE) {
      result = NEGATIVE;
    } else if (this == NEGATIVE) {
      result = POSITIVE;
    } else {
      result = this;
    }
    return result;
  }
}

package app.freerouting.geometry.planar;

import java.math.BigInteger;

/** Stores numerical limits and values used by planar geometry. */
public final class Limits {

  /**
   * An upper bound (2^25) so that the product of two integers with absolute value at most CRIT_COOR
   * is contained in the mantissa of a double with some space left for addition.
   */
  public static final int CRIT_INT = 33554432;

  /**
   * The biggest double value (2^53), so that all integers smaller than this value are represented
   * exactly as double values.
   */
  public static final double CRIT_DOUBLE = 9007199254740992.0;

  public static final BigInteger CRIT_INT_BIG = BigInteger.valueOf(CRIT_INT);

  public static final double sqrt2 = Math.sqrt(2);

  /** Prevents instantiation. */
  private Limits() {}
}

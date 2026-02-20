package app.freerouting.geometry.planar;

import java.math.BigInteger;

/** Some numerical limits and values are stored here. */
public class Limits {

  /**
   * An upper bound (2^25) so that the product of two integers with absolut value at most CRIT_COOR
   * is contained in the mantissa of a double with some space left for addition.
   */
  public static final int CRIT_INT = 33554432;

  /**
   * the biggest double value ( 2 ^53) , so that all integers smaller than this value are exact
   * represented as double value
   */
  public static final double CRIT_DOUBLE = 9007199254740992.0;

  public static final BigInteger CRIT_INT_BIG = BigInteger.valueOf(CRIT_INT);

  public static final double sqrt2 = Math.sqrt(2);

  private Limits() // disallow instantiation
      {}
}

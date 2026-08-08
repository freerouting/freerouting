package app.freerouting.datastructures;

import java.math.BigInteger;

/** Auxiliary functions with BigInteger Parameters */
public final class BigIntAux {

  /*
   * trailingZeroTable[i] is the number of trailing zero bits in the binary
   * representation of i.
   */
  static final byte[] trailingZeroTable = {
    -25, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
    0, 5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
    0, 6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
    0, 5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
    0, 7, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
    0, 5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
    0, 6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
    0, 5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0, 4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1,
    0
  };

  private BigIntAux() // disallow instantiation
      {}

  // the following function binaryGcd is copied from private parts of java.math
  // because we need it public.

  /** calculates the determinant of the vectors (p_x_1, p_y_1) and (p_x_2, p_y_2) */
  public static BigInteger determinant(
      BigInteger pX1, BigInteger pY1, BigInteger pX2, BigInteger pY2) {
    BigInteger tmp1 = pX1.multiply(pY2);
    BigInteger tmp2 = pX2.multiply(pY1);
    return tmp1.subtract(tmp2);
  }

  /**
   * auxiliary function to implement addition and translation in the classes RationalVector and
   * RationalPoint
   */
  public static BigInteger[] addRationalCoordinates(BigInteger[] pFirst, BigInteger[] pSecond) {
    BigInteger[] result = new BigInteger[3];
    if (pFirst[2].equals(pSecond[2]))
    // both rational numbers have the same denominator
    {
      result[2] = pFirst[2];
      result[0] = pFirst[0].add(pSecond[0]);
      result[1] = pFirst[1].add(pSecond[1]);
    } else
    // multiply both denominators for the new denominator
    // to be on the save side:
    // taking the least common multiple would be optimal
    {
      result[2] = pFirst[2].multiply(pSecond[2]);
      BigInteger tmp1 = pFirst[0].multiply(pSecond[2]);
      BigInteger tmp2 = pSecond[0].multiply(pFirst[2]);
      result[0] = tmp1.add(tmp2);
      tmp1 = pFirst[1].multiply(pSecond[2]);
      tmp2 = pSecond[1].multiply(pFirst[2]);
      result[1] = tmp1.add(tmp2);
    }
    return result;
  }

  /** Calculate GCD of a and b interpreted as unsigned integers. */
  public static int binaryGcd(int a, int b) {
    if (b == 0) {
      return a;
    }
    if (a == 0) {
      return b;
    }

    int x;
    int aZeros = 0;
    while ((x = a & 0xff) == 0) {
      a >>>= 8;
      aZeros += 8;
    }
    int y = trailingZeroTable[x];
    aZeros += y;
    a >>>= y;

    int bZeros = 0;
    while ((x = b & 0xff) == 0) {
      b >>>= 8;
      bZeros += 8;
    }
    y = trailingZeroTable[x];
    bZeros += y;
    b >>>= y;

    int t = Math.min(aZeros, bZeros);

    while (a != b) {
      if ((a + 0x80000000) > (b + 0x80000000)) { // a > b as unsigned
        a -= b;

        while ((x = a & 0xff) == 0) {
          a >>>= 8;
        }
        a >>>= trailingZeroTable[x];
      } else {
        b -= a;

        while ((x = b & 0xff) == 0) {
          b >>>= 8;
        }
        b >>>= trailingZeroTable[x];
      }
    }
    return a << t;
  }
}

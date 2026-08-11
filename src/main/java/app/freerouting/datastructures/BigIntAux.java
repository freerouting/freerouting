package app.freerouting.datastructures;

import java.math.BigInteger;

/** Auxiliary functions with BigInteger parameters. */
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

  private BigIntAux() {
    // disallow instantiation
  }

  // the following function binaryGcd is copied from private parts of java.math
  // because we need it public.

  /** Calculates the determinant of the vectors (x1, y1) and (x2, y2). */
  public static BigInteger determinant(BigInteger x1, BigInteger y1, BigInteger x2, BigInteger y2) {
    BigInteger tmp1 = x1.multiply(y2);
    BigInteger tmp2 = x2.multiply(y1);
    return tmp1.subtract(tmp2);
  }

  /**
   * Auxiliary function to implement addition and translation in the classes RationalVector and
   * RationalPoint.
   */
  public static BigInteger[] addRationalCoordinates(BigInteger[] first, BigInteger[] second) {
    BigInteger[] result = new BigInteger[3];
    if (first[2].equals(second[2])) {
      // both rational numbers have the same denominator
      result[2] = first[2];
      result[0] = first[0].add(second[0]);
      result[1] = first[1].add(second[1]);
    } else {
      // multiply both denominators for the new denominator
      // to be on the save side:
      // taking the least common multiple would be optimal
      result[2] = first[2].multiply(second[2]);
      BigInteger tmp1 = first[0].multiply(second[2]);
      BigInteger tmp2 = second[0].multiply(first[2]);
      result[0] = tmp1.add(tmp2);
      tmp1 = first[1].multiply(second[2]);
      tmp2 = second[1].multiply(first[2]);
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
    int leadingZeroCountA = 0;
    while ((x = a & 0xff) == 0) {
      a >>>= 8;
      leadingZeroCountA += 8;
    }
    int y = trailingZeroTable[x];
    leadingZeroCountA += y;
    a >>>= y;

    int leadingZeroCountB = 0;
    while ((x = b & 0xff) == 0) {
      b >>>= 8;
      leadingZeroCountB += 8;
    }
    y = trailingZeroTable[x];
    leadingZeroCountB += y;
    b >>>= y;

    int t = Math.min(leadingZeroCountA, leadingZeroCountB);

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

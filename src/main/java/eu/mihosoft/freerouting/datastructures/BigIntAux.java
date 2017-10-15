/*
 * This file contains a copy of the method binaryGcd in the class java.math.MutableBigInteger.
 * The reason is, that binaryGcD is not public and we needed to call it from outside the java.math package.
 * There is no aim to violate any copyright.
 * 
 * 
 * BigIntAux.java
 *
 * Created on 5. January 2003, 11:26
 */

package datastructures;

import java.math.BigInteger;

/**
 *
 * Auxiliary functions with BigInteger Parameters

 * @author Alfons Wirtz
 */

public class BigIntAux
{
    /**
     * calculates the determinant of the vectors
     * (p_x_1, p_y_1) and (p_x_2, p_y_2)
     */
    public static final BigInteger determinant (BigInteger p_x_1, BigInteger p_y_1,
                              BigInteger p_x_2, BigInteger p_y_2)
    {
        BigInteger tmp1 = p_x_1.multiply(p_y_2);
        BigInteger tmp2 = p_x_2.multiply(p_y_1);
        return tmp1.subtract(tmp2);
    }


    /**
     * auxiliary function to implement addition and translation in the
     * classes RationalVector and RationalPoint
     */
    public static final BigInteger[] add_rational_coordinates(BigInteger[] p_first,
                                              BigInteger [] p_second)
    {
        BigInteger[] result = new BigInteger[3];
        if (p_first[2].equals(p_second[2]))
        // both rational numbers have the same denominator
        {
            result[2] = p_first[2];
            result[0] = p_first[0].add(p_second[0]);
            result[1] = p_first[1].add(p_second[1]);
        }
        else
        // multiply both denominators for the new denominator
        // to be on the save side:
        // taking the leat common multiple whould be optimal
        {
            result[2] = p_first[2].multiply(p_second[2]);
            BigInteger tmp_1 = p_first[0].multiply(p_second[2]);
            BigInteger tmp_2 = p_second[0].multiply(p_first[2]);
            result[0] = tmp_1.add(tmp_2);
            tmp_1 = p_first[1].multiply(p_second[2]);
            tmp_2 = p_second[1].multiply(p_first[2]);
            result[1] = tmp_1.add(tmp_2);
        }
        return result;
    }

    // the following function binaryGcd is copied from private parts of java.math
    // because we need it public.

    /*
     * trailingZeroTable[i] is the number of trailing zero bits in the binary
     * representaion of i.
     */
    final static byte trailingZeroTable[] = {
      -25, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	7, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	6, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	5, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0,
	4, 0, 1, 0, 2, 0, 1, 0, 3, 0, 1, 0, 2, 0, 1, 0};

    /**
     * Calculate GCD of a and b interpreted as unsigned integers.
     */
    public static final int binaryGcd(int a, int b) {
        if (b==0)
            return a;
        if (a==0)
            return b;

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

        int t = (aZeros < bZeros ? aZeros : bZeros);

        while (a != b) {
            if ((a+0x80000000) > (b+0x80000000)) {  // a > b as unsigned
                a -= b;

                while ((x = a & 0xff) == 0)
                    a >>>= 8;
                a >>>= trailingZeroTable[x];
            } else {
                b -= a;

                while ((x = b & 0xff) == 0)
                    b >>>= 8;
                b >>>= trailingZeroTable[x];
            }
        }
        return a<<t;
    }

    private BigIntAux() // disallow instantiation
    {
    }
}
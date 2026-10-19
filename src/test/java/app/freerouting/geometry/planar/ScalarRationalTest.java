package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import app.freerouting.datastructures.BigIntAux;
import app.freerouting.datastructures.Signum;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying non-allocating homogeneous rational arithmetic in {@link RationalPoint} and
 * {@link RationalVector}. Ported from freerouting.net (Erik Gavriluk, Experimental Television
 * Center).
 */
class ScalarRationalTest {

  @Test
  void testRationalCoordinatesAddition() {
    BigInteger huge = BigInteger.ONE.shiftLeft(150);
    BigInteger[] values = {
      huge.negate(),
      BigInteger.valueOf(-11),
      BigInteger.ZERO,
      BigInteger.valueOf(13),
      huge.add(BigInteger.valueOf(3))
    };
    BigInteger[] denominators = {BigInteger.ONE, BigInteger.valueOf(3), huge};

    for (BigInteger x : values) {
      for (BigInteger z : denominators) {
        for (BigInteger w : denominators) {
          BigIntAux.RationalCoordinates sum =
              BigIntAux.addRationalCoordinates(
                  x, BigInteger.valueOf(7), z, BigInteger.valueOf(19), x.negate(), w);
          BigInteger[] legacySum =
              BigIntAux.addRationalCoordinates(
                  new BigInteger[] {x, BigInteger.valueOf(7), z},
                  new BigInteger[] {BigInteger.valueOf(19), x.negate(), w});

          assertEquals(legacySum[0], sum.x());
          assertEquals(legacySum[1], sum.y());
          assertEquals(legacySum[2], sum.z());
        }
      }
    }
  }

  @Test
  void testRationalPointAndVectorOperations() {
    RationalPoint p =
        new RationalPoint(BigInteger.valueOf(10), BigInteger.valueOf(20), BigInteger.valueOf(2));
    IntVector iv = new IntVector(5, -3);
    Point translated = p.translateBy(iv);
    assertInstanceOf(RationalPoint.class, translated);

    RationalPoint rpTranslated = (RationalPoint) translated;
    // 10/2 + 5 = 10, 20/2 - 3 = 7
    assertEquals(BigInteger.valueOf(20), rpTranslated.x);
    assertEquals(BigInteger.valueOf(14), rpTranslated.y);
    assertEquals(BigInteger.valueOf(2), rpTranslated.z);

    Vector diff = p.differenceBy(new IntPoint(1, 2));
    assertInstanceOf(RationalVector.class, diff);
    RationalVector rDiff = (RationalVector) diff;
    // (5 - 1, 10 - 2) = (4, 8)
    assertEquals(BigInteger.valueOf(8), rDiff.x);
    assertEquals(BigInteger.valueOf(16), rDiff.y);
    assertEquals(BigInteger.valueOf(2), rDiff.z);

    RationalVector rv =
        new RationalVector(BigInteger.valueOf(6), BigInteger.valueOf(8), BigInteger.valueOf(2));
    Side side = rv.sideOf(new IntVector(1, 0));
    assertEquals(Side.ON_THE_LEFT, side);

    Signum proj = rv.projection(new IntVector(1, 1));
    assertEquals(Signum.POSITIVE, proj);
  }
}

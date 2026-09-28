package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Tests for equals and hashCode contracts on {@link IntPoint} and {@link RationalPoint}. */
class PointEqualsHashCodeTest {

  @Test
  void intPointEqualsAndHashCodeContract() {
    final IntPoint p1 = new IntPoint(100, 200);
    final IntPoint p2 = new IntPoint(100, 200);
    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());

    final IntPoint p3 = new IntPoint(100, 201);
    assertNotEquals(p1, p3);

    final IntPoint p4 = new IntPoint(101, 200);
    assertNotEquals(p1, p4);

    assertNotEquals(p1, null);
    assertNotEquals(p1, "(100,200)");
  }

  @Test
  void rationalPointEqualsAndHashCodeContract() {
    final RationalPoint p1 =
        new RationalPoint(BigInteger.valueOf(100), BigInteger.valueOf(200), BigInteger.valueOf(50));
    final RationalPoint p2 =
        new RationalPoint(BigInteger.valueOf(2), BigInteger.valueOf(4), BigInteger.valueOf(1));
    assertEquals(p1, p2);
    assertEquals(p1.hashCode(), p2.hashCode());

    final RationalPoint p3 =
        new RationalPoint(BigInteger.valueOf(-4), BigInteger.valueOf(6), BigInteger.valueOf(2));
    final RationalPoint p4 =
        new RationalPoint(BigInteger.valueOf(-2), BigInteger.valueOf(3), BigInteger.valueOf(1));
    assertEquals(p3, p4);
    assertEquals(p3.hashCode(), p4.hashCode());

    assertNotEquals(p1, p3);
    assertNotEquals(p1, null);
  }

  @Test
  void rationalPointInfinitePoints() {
    final RationalPoint inf1 =
        new RationalPoint(BigInteger.valueOf(10), BigInteger.valueOf(20), BigInteger.ZERO);
    final RationalPoint inf2 =
        new RationalPoint(BigInteger.valueOf(30), BigInteger.valueOf(40), BigInteger.ZERO);

    assertEquals(inf1, inf2);
    assertEquals(inf1.hashCode(), inf2.hashCode());
  }
}

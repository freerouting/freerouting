package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LineSegmentContainmentTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void containsExactRationalPointsBetweenRationalEndpoints(boolean reverse) {
    Polyline polyline = rationalSegment();
    LineSegment segment = new LineSegment(reverse ? polyline.reverse() : polyline, 1);

    assertTrue(segment.contains(fraction(1, 1, 2)));
    assertTrue(segment.contains(fraction(3, 3, 4)));
    assertTrue(segment.contains(new IntPoint(1, 1)));
    assertTrue(segment.contains(fraction(3, 3, 2)));
    assertFalse(segment.contains(fraction(-1, -1, 2)));
    assertFalse(segment.contains(fraction(5, 5, 2)));
    assertFalse(segment.contains(fraction(750000, 750001, 1000000)));
  }

  @Test
  void rejectsNearMissesThatHaveTheSameFloatingApproximation() {
    BigInteger scale = BigInteger.ONE.shiftLeft(100);
    LineSegment segment = new LineSegment(rationalSegment(), 1);
    Point offLine = new RationalPoint(scale, scale.add(BigInteger.ONE), scale);
    BigInteger beyondEnd = scale.multiply(BigInteger.valueOf(3)).add(BigInteger.ONE);
    Point outside = new RationalPoint(beyondEnd, beyondEnd, scale.multiply(BigInteger.TWO));

    assertEquals(1.0, offLine.toFloat().y);
    assertEquals(segment.endPoint().toFloat().x, outside.toFloat().x);
    assertFalse(segment.contains(offLine));
    assertFalse(segment.contains(outside));
  }

  @Test
  void rejectsNullAndInfinitePoints() {
    LineSegment segment = new LineSegment(rationalSegment(), 1);

    assertFalse(segment.contains(null));
    assertFalse(
        segment.contains(new RationalPoint(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)));
  }

  @Test
  void containsRationalPointsOnHorizontalAndVerticalSegments() {
    LineSegment horizontal =
        new LineSegment(new Polyline(new IntPoint(-2, 0), new IntPoint(2, 0)), 1);
    LineSegment vertical =
        new LineSegment(new Polyline(new IntPoint(0, 2), new IntPoint(0, -2)), 1);

    assertTrue(horizontal.contains(fraction(-1, 0, 2)));
    assertTrue(vertical.contains(fraction(0, -1, 2)));
    assertFalse(horizontal.contains(fraction(5, 0, 2)));
    assertFalse(vertical.contains(fraction(0, -5, 2)));
  }

  @Test
  void rationalSplitCapsRetainExactPointAndIntegerSupports() {
    for (IntPoint end :
        new IntPoint[] {
          new IntPoint(1000, 0),
          new IntPoint(0, 1000),
          new IntPoint(1000, 1000),
          new IntPoint(-1000, -1000)
        }) {
      LineSegment segment = new LineSegment(new Polyline(new IntPoint(0, 0), end), 1);
      Point point = fraction(end.x, end.y, 2000);
      Line cap = segment.splitLine(point);
      assertNotNull(cap);
      assertTrue(cap.a instanceof IntPoint);
      assertTrue(cap.b instanceof IntPoint);
      assertFalse(cap.isParallel(segment.getLine()));
      assertEquals(point, cap.intersection(segment.getLine()));
    }
  }

  @Test
  void rationalSplitCapsRejectInvalidAndUnsupportedPoints() {
    LineSegment segment = new LineSegment(rationalSegment(), 1);
    assertNull(segment.splitLine(null));
    assertNull(segment.splitLine(fraction(1, 2, 2)));
    assertNull(segment.splitLine(fraction(5, 5, 2)));
    assertEquals(
        segment.startPoint(),
        segment.splitLine(segment.startPoint()).intersection(segment.getLine()));
    LineSegment vertical =
        new LineSegment(new Polyline(new IntPoint(0, -1), new IntPoint(0, 1)), 1);
    Point unsupported = fraction(0, 1, 2L * Limits.CRIT_INT + 1);
    assertTrue(vertical.contains(unsupported));
    assertNull(vertical.splitLine(unsupported));
  }

  private static Polyline rationalSegment() {
    // The finite portion of y=x between x+y=1 and x+y=3.
    return new Polyline(
        new Line[] {
          new Line(new IntPoint(0, 1), new IntPoint(1, 0)),
          new Line(new IntPoint(0, 0), new IntPoint(2, 2)),
          new Line(new IntPoint(0, 3), new IntPoint(3, 0))
        });
  }

  private static Point fraction(long x, long y, long denominator) {
    return new RationalPoint(
        BigInteger.valueOf(x), BigInteger.valueOf(y), BigInteger.valueOf(denominator));
  }
}

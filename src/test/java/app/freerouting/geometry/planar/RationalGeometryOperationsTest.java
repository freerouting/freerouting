package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RationalGeometryOperationsTest {
  private static Point point(long x, long y, long z) {
    return Point.getInstance(BigInteger.valueOf(x), BigInteger.valueOf(y), BigInteger.valueOf(z));
  }

  @ParameterizedTest
  @CsvSource({"0,1,1,1,1,2", "1,0,1,1,2,1", "0,0,1,1,1,1", "0,1,1,0,1,1"})
  void exactProjectionOnIntegerDefinedLines(int ax, int ay, int bx, int by, int x, int y) {
    Point query = point(1, 1, 2);
    Line line = new Line(ax, ay, bx, by);
    assertEquals(point(x, y, 2), query.perpendicularProjection(line));
    assertEquals(point(x, y, 2), query.perpendicularProjection(line.opposite()));
  }

  @Test
  void projectionPreservesNegativeAndIntegerEquivalentRationalCoordinates() {
    Point query =
        new RationalPoint(BigInteger.valueOf(-6), BigInteger.valueOf(3), BigInteger.valueOf(3));
    assertEquals(new IntPoint(-2, 5), query.perpendicularProjection(new Line(0, 5, 1, 5)));
    assertEquals(point(-1, 0, 3), point(-1, 2, 3).perpendicularProjection(new Line(0, 0, 1, 0)));
  }

  @Test
  void rationalLineConstructionUsesIntegerSupportsAndRetainsDirection() {
    for (int sign : new int[] {-1, 1}) {
      Point start = point(sign, sign, 3);
      Point end = point(5 * sign, 5 * sign, 3);
      Line line = new Line(start, end);
      assertInstanceOf(IntPoint.class, line.a);
      assertInstanceOf(IntPoint.class, line.b);
      assertEquals(Side.COLLINEAR, line.sideOf(start));
      assertEquals(Side.COLLINEAR, line.sideOf(end));
      assertEquals(Direction.getInstance(start, end), line.direction());
      assertDoesNotThrow(() -> line.intersection(new Line(0, 1, 1, 0)));
      Polyline polyline = new Polyline(new Point[] {start, end});
      assertEquals(start, polyline.firstCorner());
      assertEquals(end, polyline.lastCorner());
    }
  }

  @Test
  void rationalLineConstructionRejectsUnrepresentableSupportsBeforeCreatingInvalidGeometry() {
    assertThrows(IllegalArgumentException.class, () -> new Line(point(0, 1, 3), point(3, 1, 3)));
    assertThrows(IllegalArgumentException.class, () -> new Line(point(1, 1, 2), Direction.RIGHT));
    assertThrows(
        IllegalArgumentException.class, () -> new Polyline(point(0, 1, 3), point(3, 1, 3)));
  }

  @Test
  void rationalProjectionConnectorEitherPreservesExactGeometryOrDeclines() {
    Polyline diagonal = new Polyline(new IntPoint(0, 0), new IntPoint(5, 5));
    Point query = point(1, 2, 3);
    LineSegment connector = diagonal.projectionLine(query);
    assertNotNull(connector);
    assertEquals(query, connector.startPoint());
    assertEquals(point(1, 1, 2), connector.endPoint());
    Polyline vertical = new Polyline(new IntPoint(0, 0), new IntPoint(0, 2));
    assertNull(vertical.projectionLine(point(1, 1, 2)));
  }

  @Test
  void rationalBoundsRoundOutwardEvenWhenFloatingApproximationIsAnInteger() {
    BigInteger scale = BigInteger.ONE.shiftLeft(100);
    RationalPoint point =
        new RationalPoint(
            scale.add(BigInteger.ONE), scale.negate().subtract(BigInteger.ONE), scale);
    IntBox box = point.surroundingBox();
    assertEquals(new IntPoint(1, -2), box.ll);
    assertEquals(new IntPoint(2, -1), box.ur);
    assertTrue(point.isContainedIn(box));
    assertTrue(point.surroundingOctagon().contains(point));
  }

  @Test
  void rationalVectorLengthAndZeroDirectionAreNotSilentStubs() {
    Vector vector =
        new RationalVector(BigInteger.valueOf(3), BigInteger.valueOf(4), BigInteger.TWO);
    assertEquals(new IntVector(6, 8), vector.changeLengthApprox(10));
    assertEquals(
        Direction.NULL,
        new RationalVector(BigInteger.ZERO, BigInteger.ZERO, BigInteger.TWO)
            .toNormalizedDirection());
    assertThrows(IllegalArgumentException.class, () -> vector.changeLengthApprox(Double.NaN));
  }

  @Test
  void bigIntegerDirectionsRotateExactlyAndCompose() {
    BigInteger huge = BigInteger.ONE.shiftLeft(100);
    BigInteger x = huge.add(BigInteger.ONE);
    BigInteger twiceHugePlusOne = huge.shiftLeft(1).add(BigInteger.ONE);
    Direction direction = new BigIntDirection(x, huge);
    // Independent signed component expectations for each counterclockwise 45-degree step.
    Direction[] expected = {
      new BigIntDirection(x, huge),
      new BigIntDirection(BigInteger.ONE, twiceHugePlusOne),
      new BigIntDirection(huge.negate(), x),
      new BigIntDirection(twiceHugePlusOne.negate(), BigInteger.ONE),
      new BigIntDirection(x.negate(), huge.negate()),
      new BigIntDirection(BigInteger.ONE.negate(), twiceHugePlusOne.negate()),
      new BigIntDirection(huge, x.negate()),
      new BigIntDirection(twiceHugePlusOne, BigInteger.ONE.negate())
    };
    for (int factor = -9; factor <= 9; factor++) {
      Direction rotated = direction.turn45Degree(factor);
      assertEquals(expected[Math.floorMod(factor, 8)], rotated, "Rotation factor " + factor);
      assertEquals(direction, rotated.turn45Degree(-factor));
      assertEquals(rotated, direction.turn45Degree(1).turn45Degree(factor - 1));
    }
    Direction reduced = new BigIntDirection(huge, huge).turn45Degree(1);
    assertEquals(Direction.UP, reduced);
  }
}

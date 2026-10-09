package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CircleTest {

  @Test
  void testSmallCircleUsesOctagon() {
    Circle smallCircle = new Circle(new IntPoint(100, 100), 10);
    TileShape[] shapes = smallCircle.splitToConvex();
    assertEquals(1, shapes.length);
    assertInstanceOf(IntOctagon.class, shapes[0]);
  }

  @Test
  void testLargeCircleUses64Gon() {
    Circle circle = new Circle(new IntPoint(10000, -10000), 25000);
    TileShape[] shapes = circle.splitToConvex();
    assertEquals(1, shapes.length);
    assertInstanceOf(Simplex.class, shapes[0]);

    Simplex simplex = (Simplex) shapes[0];
    assertEquals(64, simplex.borderLineCount(), "Circumscribed polygon must have exactly 64 sides");
    assertEquals(
        64,
        simplex.cornerApproxArr().length,
        "Circumscribed polygon must have exactly 64 vertices");
  }

  @Test
  void test64GonEliminatesOctagonalOvershootAt22Degrees() {
    // 5.0 mm hole (radius 25000 units in 10-nm DSN space) centered at (10000, -10000)
    IntPoint center = new IntPoint(10000, -10000);
    int radius = 25000;
    Circle circle = new Circle(center, radius);

    TileShape octagon = circle.boundingOctagon();
    TileShape sixtyFourGon = circle.splitToConvex()[0];

    // At 22.5 degrees, an octagonal corner extends to R / cos(22.5 deg) ~ 1.08239 * R.
    // In contrast, the 64-gon corner extends to R / cos(2.8125 deg) ~ 1.00120 * R.
    // A point at 22.5 degrees and distance 1.04 * R is well outside the physical circle
    // and outside the 64-gon, but falls inside the coarse octagon.
    double angleRad = Math.toRadians(22.5);
    double distance = radius * 1.04;
    IntPoint testPoint =
        new IntPoint(
            (int) Math.round(center.x + distance * Math.cos(angleRad)),
            (int) Math.round(center.y + distance * Math.sin(angleRad)));

    assertTrue(
        octagon.contains(testPoint),
        "Coarse octagon must erroneously contain the point at 1.04 * radius");
    assertFalse(
        sixtyFourGon.contains(testPoint),
        "Refined 64-gon must correctly exclude the point at 1.04 * radius");

    assertTrue(sixtyFourGon.contains(center), "64-gon must contain circle center");
    assertTrue(
        sixtyFourGon.contains(new IntPoint(center.x + radius, center.y)),
        "64-gon must contain boundary point at 0 degrees");
    assertTrue(
        sixtyFourGon.contains(new IntPoint(center.x, center.y + radius)),
        "64-gon must contain boundary point at 90 degrees");
    assertTrue(
        sixtyFourGon.contains(new IntPoint(center.x - radius, center.y)),
        "64-gon must contain boundary point at 180 degrees");
    assertTrue(
        sixtyFourGon.contains(new IntPoint(center.x, center.y - radius)),
        "64-gon must contain boundary point at 270 degrees");

    double circleArea = circle.area();
    double polyArea = sixtyFourGon.area();
    assertTrue(
        polyArea >= circleArea,
        "Circumscribed polygon area must be greater than or equal to circle area");
    assertTrue(
        polyArea < circleArea * 1.002, "Circumscribed 64-gon area overshoot must be <= 0.2%");
  }

  @Test
  void testBoundingTileWithDivisions() {
    Circle circle = new Circle(Point.ZERO, 5000);
    TileShape oct = circle.boundingTileWithDivisions(2);
    assertInstanceOf(IntOctagon.class, oct);

    TileShape poly32 = circle.boundingTileWithDivisions(8);
    assertInstanceOf(Simplex.class, poly32);
    assertEquals(32, poly32.borderLineCount());

    TileShape poly64 = circle.boundingTileWithDivisions(16);
    assertInstanceOf(Simplex.class, poly64);
    assertEquals(64, poly64.borderLineCount());
  }

  @Test
  void testZeroOrSmallRadiusReturnsOctagon() {
    Circle zeroCircle = new Circle(Point.ZERO, 0);
    TileShape shape0 = zeroCircle.splitToConvex()[0];
    assertInstanceOf(IntOctagon.class, shape0);

    TileShape shape0Div = zeroCircle.boundingTileWithDivisions(16);
    assertInstanceOf(IntOctagon.class, shape0Div);

    Circle smallCircle = new Circle(Point.ZERO, 15);
    TileShape shapeSmall = smallCircle.splitToConvex()[0];
    assertInstanceOf(IntOctagon.class, shapeSmall);
  }

  @Test
  void testExcessiveDivisionsCapped() {
    Circle circle = new Circle(Point.ZERO, 5000);
    TileShape capped = circle.boundingTileWithDivisions(1000);
    assertInstanceOf(Simplex.class, capped);
    // 64 divisions per quadrant = 256 sides maximum
    assertEquals(256, capped.borderLineCount());
  }
}

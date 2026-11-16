package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/**
 * A point on the border of a polygon is on its border, not inside it. A hole of an area is a
 * polygon: a point on the edge of the hole belongs to the area, as it does to the convex tiles the
 * area is split into, so a trace that ends on that edge stays in contact with the area.
 */
class PolygonShapeBorderTest {

  /** An L-shaped (non convex) polygon: (0,0) (100,0) (100,40) (40,40) (40,100) (0,100). */
  private static PolygonShape lShape() {
    return new PolygonShape(
        new Point[] {
          new IntPoint(0, 0),
          new IntPoint(100, 0),
          new IntPoint(100, 40),
          new IntPoint(40, 40),
          new IntPoint(40, 100),
          new IntPoint(0, 100)
        });
  }

  @Test
  void pointOnAnEdgeIsOnTheBorder() {
    PolygonShape shape = lShape();
    assertTrue(shape.containsOnBorder(new IntPoint(50, 0)));
    assertTrue(shape.containsOnBorder(new IntPoint(70, 40)), "edge of the inner corner");
    assertTrue(shape.containsOnBorder(new IntPoint(40, 70)), "edge of the inner corner");
    assertTrue(shape.containsOnBorder(new IntPoint(40, 40)), "reflex corner");
    assertTrue(shape.containsOnBorder(new IntPoint(0, 100)), "corner");
  }

  @Test
  void pointOffTheEdgesIsNotOnTheBorder() {
    PolygonShape shape = lShape();
    assertFalse(shape.containsOnBorder(new IntPoint(20, 20)), "inside");
    assertFalse(shape.containsOnBorder(new IntPoint(70, 70)), "in the notch, outside");
    assertFalse(shape.containsOnBorder(new IntPoint(120, 40)), "on the line of an edge, past it");
    assertFalse(
        shape.containsOnBorder(new IntPoint(40, 20)), "on the line of a reflex edge, inside");
  }

  @Test
  void pointOnTheBorderIsNotInside() {
    PolygonShape shape = lShape();
    assertFalse(shape.containsInside(new IntPoint(70, 40)));
    assertTrue(shape.containsInside(new IntPoint(20, 20)));
    assertTrue(shape.contains(new IntPoint(70, 40)));
  }

  @Test
  void areaContainsThePointsOnTheEdgeOfAPolygonHole() {
    IntBox border = new IntBox(new IntPoint(-100, -100), new IntPoint(300, 300));
    PolylineArea area = new PolylineArea(border, new PolylineShape[] {lShape()});
    assertTrue(area.contains(new IntPoint(70, 40)), "edge of the hole");
    assertTrue(area.contains(new IntPoint(40, 40)), "reflex corner of the hole");
    assertFalse(area.contains(new IntPoint(20, 20)), "inside the hole");
    assertTrue(area.contains(new IntPoint(200, 200)), "away from the hole");
    // The convex tiles of the area are closed: the point is in one of them, as in the area.
    boolean inTile = false;
    for (TileShape tile : area.splitToConvex()) {
      inTile |= tile.contains(new IntPoint(70, 40));
    }
    assertTrue(inTile);
  }

  @Test
  void rationalPointOnEdgeUsesExactBounds() {
    PolygonShape shape = lShape();
    BigInteger scale = BigInteger.TEN.pow(400);
    // (70 + 1/10^400, 40) has a huge unreduced denominator that overflows double in toFloat()
    Point onEdge =
        Point.getInstance(
            BigInteger.valueOf(70).multiply(scale).add(BigInteger.ONE),
            BigInteger.valueOf(40).multiply(scale),
            scale);
    // (100 + 1/10^20, 40) is past the edge (40,40)-(100,40) even though double rounds it to 100.0
    BigInteger tinyScale = BigInteger.TEN.pow(20);
    Point justPastEdge =
        Point.getInstance(
            BigInteger.valueOf(100).multiply(tinyScale).add(BigInteger.ONE),
            BigInteger.valueOf(40).multiply(tinyScale),
            tinyScale);
    assertTrue(shape.containsOnBorder(onEdge));
    assertFalse(shape.containsOnBorder(justPastEdge));
  }
}

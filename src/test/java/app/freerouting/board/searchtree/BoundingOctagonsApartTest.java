package app.freerouting.board.searchtree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.Simplex;
import app.freerouting.geometry.planar.TileShape;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying {@link ShapeSearchTree#boundingOctagonsApart(ConvexShape, ConvexShape)}. Ported
 * from freerouting.net (Erik Gavriluk, Experimental Television Center).
 */
class BoundingOctagonsApartTest {

  private static TileShape randomSegment(Random random, int spread) {
    IntPoint a =
        new IntPoint(random.nextInt(2 * spread) - spread, random.nextInt(2 * spread) - spread);
    IntPoint b =
        new IntPoint(
            a.x + random.nextInt(spread / 2) + 1, a.y + random.nextInt(spread) - spread / 2);
    return new Polyline(new IntPoint[] {a, b}).offsetShape(random.nextInt(spread / 20 + 2) + 1, 0);
  }

  private static TileShape randomOctagon(Random random, int spread) {
    int x = random.nextInt(2 * spread) - spread;
    int y = random.nextInt(2 * spread) - spread;
    return new IntBox(x, y, x + random.nextInt(spread / 4) + 1, y + random.nextInt(spread / 4) + 1)
        .boundingOctagon()
        .enlarge(random.nextInt(spread / 20 + 1));
  }

  @Test
  void testApartOnlyWhenExactTestFindsNoCommonPoint() {
    Random random = new Random(20261011);
    int apart = 0;
    int simplexPairs = 0;
    for (int i = 0; i < 5000; i++) {
      int spread = random.nextInt(2) == 0 ? 2000 : 200000;
      TileShape first = randomSegment(random, spread);
      TileShape second =
          random.nextInt(2) == 0 ? randomSegment(random, spread) : randomOctagon(random, spread);
      int half = random.nextInt(spread / 10 + 1);
      ConvexShape a = (ConvexShape) first.enlarge(half);
      ConvexShape b = (ConvexShape) second.enlarge(half);
      if (a instanceof Simplex || b instanceof Simplex) {
        simplexPairs++;
      }
      if (ShapeSearchTree.boundingOctagonsApart(a, b)) {
        apart++;
        assertFalse(a.intersects(b), "pair " + i + ": " + a + " and " + b);
      }
    }
    assertTrue(simplexPairs > 500);
    assertTrue(apart > 50);
  }

  @Test
  void testOverlappingOrTouchingShapesAreNotApart() {
    TileShape segment =
        new Polyline(new IntPoint[] {new IntPoint(0, 0), new IntPoint(1000, 577)})
            .offsetShape(50, 0);
    assertInstanceOf(Simplex.class, segment);
    Simplex simplex = (Simplex) segment;
    IntOctagon bounds = simplex.boundingOctagon();
    assertFalse(ShapeSearchTree.boundingOctagonsApart(simplex, bounds));

    IntOctagon corner =
        new IntBox(bounds.rightX, bounds.topY - 10, bounds.rightX + 100, bounds.topY + 100)
            .boundingOctagon();
    assertEquals(
        bounds.intersects(corner), !ShapeSearchTree.boundingOctagonsApart(simplex, corner));
    assertFalse(ShapeSearchTree.boundingOctagonsApart(bounds, (IntOctagon) corner.enlarge(-1000)));
  }
}

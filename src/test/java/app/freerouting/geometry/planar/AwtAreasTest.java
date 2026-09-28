package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AwtAreasTest {

  /** Area overloads equals(Area) instead of overriding equals(Object), so compare explicitly. */
  private static void assertSameShape(Area expected, Area actual) {
    assertTrue(
        expected.equals(actual), "areas differ: expected " + expected + " but was " + actual);
  }

  private static List<Area> holes(int count) {
    List<Area> holes = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      double x = 10 + (i % 20) * 45;
      double y = 10 + (i / 20) * 45;
      holes.add(new Area(new Ellipse2D.Double(x, y, 20, 20)));
    }
    return holes;
  }

  @Test
  void unionAllOfNothingIsEmpty() {
    assertTrue(AwtAreas.unionAll(new ArrayList<>()).isEmpty());
  }

  @Test
  void unionAllJoinsEveryArea() {
    Area expected = new Area(new Rectangle2D.Double(0, 0, 10, 10));
    expected.add(new Area(new Rectangle2D.Double(20, 0, 10, 10)));
    expected.add(new Area(new Rectangle2D.Double(40, 0, 10, 10)));

    Area actual =
        AwtAreas.unionAll(
            new ArrayList<>(
                List.of(
                    new Area(new Rectangle2D.Double(0, 0, 10, 10)),
                    new Area(new Rectangle2D.Double(20, 0, 10, 10)),
                    new Area(new Rectangle2D.Double(40, 0, 10, 10)))));

    assertSameShape(expected, actual);
  }

  @Test
  void subtractAllRemovesTheSameShapeAsOneByOneSubtraction() {
    Area expected = new Area(new Rectangle2D.Double(0, 0, 1000, 1000));
    for (Area hole : holes(200)) {
      expected.subtract(hole);
    }

    Area actual = new Area(new Rectangle2D.Double(0, 0, 1000, 1000));
    AwtAreas.subtractAll(actual, holes(200));

    assertSameShape(expected, actual);
  }

  @Test
  void subtractAllOfNothingLeavesTheAreaUntouched() {
    Area area = new Area(new Rectangle2D.Double(0, 0, 10, 10));

    AwtAreas.subtractAll(area, new ArrayList<>());

    assertSameShape(new Area(new Rectangle2D.Double(0, 0, 10, 10)), area);
  }

  @Test
  void subtractAllHandlesOverlappingCutouts() {
    Area expected = new Area(new Rectangle2D.Double(0, 0, 100, 100));
    expected.subtract(new Area(new Rectangle2D.Double(0, 0, 60, 60)));
    expected.subtract(new Area(new Rectangle2D.Double(40, 40, 60, 60)));

    Area actual = new Area(new Rectangle2D.Double(0, 0, 100, 100));
    AwtAreas.subtractAll(
        actual,
        new ArrayList<>(
            List.of(
                new Area(new Rectangle2D.Double(0, 0, 60, 60)),
                new Area(new Rectangle2D.Double(40, 40, 60, 60)))));

    assertSameShape(expected, actual);
  }
}

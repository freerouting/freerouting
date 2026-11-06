package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlanarAreasTest {

  private static List<PlanarArea> holes(int count) {
    List<PlanarArea> holes = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      double x = 10 + (i % 20) * 45;
      double y = 10 + (i / 20) * 45;
      holes.add(
          PlanarArea.fromBox(
              new IntBox(
                  new IntPoint((int) x, (int) y), new IntPoint((int) x + 20, (int) y + 20))));
    }
    return holes;
  }

  @Test
  void unionAllOfNothingIsEmpty() {
    assertTrue(PlanarAreas.unionAll(new ArrayList<>()).isEmpty());
  }

  @Test
  void unionAllJoinsEveryArea() {
    PlanarArea a1 = PlanarArea.fromBox(new IntBox(new IntPoint(0, 0), new IntPoint(10, 10)));
    PlanarArea a2 = PlanarArea.fromBox(new IntBox(new IntPoint(20, 0), new IntPoint(30, 10)));
    PlanarArea a3 = PlanarArea.fromBox(new IntBox(new IntPoint(40, 0), new IntPoint(50, 10)));

    PlanarArea expected = a1.union(a2).union(a3);
    PlanarArea actual = PlanarAreas.unionAll(List.of(a1, a2, a3));

    assertEquals(expected.getArea(), actual.getArea(), 1e-4);
  }

  @Test
  void subtractAllRemovesTheSameShapeAsOneByOneSubtraction() {
    PlanarArea expected =
        PlanarArea.fromBox(new IntBox(new IntPoint(0, 0), new IntPoint(1000, 1000)));
    for (PlanarArea hole : holes(20)) {
      expected = expected.difference(hole);
    }

    PlanarArea actual =
        PlanarArea.fromBox(new IntBox(new IntPoint(0, 0), new IntPoint(1000, 1000)));
    actual = PlanarAreas.subtractAll(actual, holes(20));

    assertEquals(expected.getArea(), actual.getArea(), 1e-4);
  }

  @Test
  void subtractAllOfNothingLeavesTheAreaUntouched() {
    PlanarArea area = PlanarArea.fromBox(new IntBox(new IntPoint(0, 0), new IntPoint(10, 10)));
    double originalArea = area.getArea();

    PlanarArea result = PlanarAreas.subtractAll(area, new ArrayList<>());

    assertEquals(originalArea, result.getArea(), 1e-4);
  }

  @Test
  void subtractAllHandlesOverlappingCutouts() {
    PlanarArea base = PlanarArea.fromBox(new IntBox(new IntPoint(0, 0), new IntPoint(100, 100)));
    PlanarArea c1 = PlanarArea.fromBox(new IntBox(new IntPoint(0, 0), new IntPoint(60, 60)));
    PlanarArea c2 = PlanarArea.fromBox(new IntBox(new IntPoint(40, 40), new IntPoint(100, 100)));

    PlanarArea expected = base.difference(c1).difference(c2);
    PlanarArea actual = PlanarAreas.subtractAll(base, List.of(c1, c2));

    assertEquals(expected.getArea(), actual.getArea(), 1e-4);
  }

  @Test
  void decomposeIntoIslandsFindsDisjointPieces() {
    PlanarArea a1 = PlanarArea.fromBox(new IntBox(new IntPoint(0, 0), new IntPoint(10, 10)));
    PlanarArea a2 = PlanarArea.fromBox(new IntBox(new IntPoint(50, 50), new IntPoint(60, 60)));
    PlanarArea combined = a1.union(a2);

    List<PlanarArea> islands = combined.decomposeIntoIslands();
    assertEquals(2, islands.size());
    assertTrue(islands.get(0).contains(5, 5) || islands.get(1).contains(5, 5));
    assertTrue(islands.get(0).contains(55, 55) || islands.get(1).contains(55, 55));
    assertFalse(combined.contains(25, 25));
  }
}

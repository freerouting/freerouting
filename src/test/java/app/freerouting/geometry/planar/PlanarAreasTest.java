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

  @Test
  void circleApproximationIsCircumscribedEnclosingAllCirclePoints() {
    Circle circle = new Circle(new IntPoint(1000, 1000), 500);
    PlanarArea circlePoly = PlanarArea.fromCircle(circle, 32);

    double circleExactArea = Math.PI * 500.0 * 500.0;
    assertTrue(
        circlePoly.getArea() >= circleExactArea,
        "Circumscribed polygon area must be >= circle area");

    // All sample perimeter points must be strictly enclosed by the circumscribed polygon
    for (int i = 0; i < 72; i++) {
      double angle = i * 2.0 * Math.PI / 72.0;
      double px = 1000.0 + 499.9 * Math.cos(angle);
      double py = 1000.0 + 499.9 * Math.sin(angle);
      assertTrue(
          circlePoly.contains(px, py),
          "Circumscribed polygon must enclose circle point at angle " + angle);
    }
  }

  @Test
  void areaWithHolesAndContourExtractionPreservesTopology() {
    TileShape border = TileShape.getInstance(0, 0, 1000, 1000);
    TileShape hole = TileShape.getInstance(200, 200, 400, 400);
    Area domainArea = new PolylineArea(border, new PolylineShape[] {hole});

    PlanarArea planar = PlanarArea.fromArea(domainArea);
    assertEquals(1000.0 * 1000.0 - 200.0 * 200.0, planar.getArea(), 1e-4);
    assertTrue(planar.contains(100, 100));
    assertFalse(planar.contains(300, 300));

    List<PlanarArea.Contour> contours = planar.toContours();
    assertEquals(1, contours.size());
    assertEquals(1, contours.get(0).holes().size());
  }

  @Test
  void thermalSpokeCutoutTopologyLeavesCenterConnected() {
    PlanarArea pour = PlanarArea.fromBox(new IntBox(new IntPoint(0, 0), new IntPoint(1000, 1000)));
    Circle clearanceCircle = new Circle(new IntPoint(500, 500), 200);
    PlanarArea clearance = PlanarArea.fromCircle(clearanceCircle, 32);

    PlanarArea spoke1 = PlanarArea.createRotatedSpoke(500, 500, 40.0, 600.0, Math.PI / 4.0);
    PlanarArea spoke2 = PlanarArea.createRotatedSpoke(500, 500, 40.0, 600.0, -Math.PI / 4.0);
    PlanarArea spokes = spoke1.union(spoke2);

    PlanarArea moat = clearance.difference(spokes);
    PlanarArea remainingPour = pour.difference(moat);

    assertEquals(1, remainingPour.decomposeIntoIslands().size());
    assertTrue(remainingPour.contains(500, 500), "Pad center must remain connected");
    assertTrue(remainingPour.contains(100, 100), "Outer pour must remain connected");
    assertFalse(remainingPour.contains(500, 650), "Moat sector cutout must be removed");
  }

  @Test
  void unionAllDoesNotSilentlyOmitCutouts() {
    List<PlanarArea> cutouts = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      int x = i * 100;
      cutouts.add(PlanarArea.fromBox(new IntBox(new IntPoint(x, 0), new IntPoint(x + 50, 50))));
    }
    PlanarArea merged = PlanarArea.unionAll(cutouts);
    assertEquals(5 * 50.0 * 50.0, merged.getArea(), 1e-4);
    for (int i = 0; i < 5; i++) {
      assertTrue(merged.contains(i * 100 + 25, 25));
    }
  }
}

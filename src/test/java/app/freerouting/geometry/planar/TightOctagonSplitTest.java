package app.freerouting.geometry.planar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The 45-degree search tree stores obstacles as bounding octagons. These tests cover the splitting
 * that keeps the octagon of an oblique shape close to the shape.
 */
class TightOctagonSplitTest {

  /** A thin strip along the line from (0, 0) to (10000, 50000), slope 5:1. */
  private static TileShape obliqueStrip() {
    return TileShape.getInstance(
        new Point[] {
          new IntPoint(0, 0),
          new IntPoint(100, 0),
          new IntPoint(10100, 50000),
          new IntPoint(10000, 50000)
        });
  }

  @Test
  void octagonOfAnOrthogonalOrDiagonalShapeHasNoExcess() {
    TileShape box = new IntBox(0, 0, 1000, 500);
    TileShape diamond =
        TileShape.getInstance(
            new Point[] {
              new IntPoint(0, 0),
              new IntPoint(500, 500),
              new IntPoint(0, 1000),
              new IntPoint(-500, 500)
            });

    assertEquals(0, box.boundingOctagonExcess(), 1e-6);
    assertEquals(0, diamond.boundingOctagonExcess(), 1e-6);
    assertEquals(2, TileShape.splitToTightOctagons(new TileShape[] {box, diamond}, 10).length);
  }

  @Test
  void octagonOfAnObliqueStripReachesFarFromIt() {
    // The octagon spans the whole bounding box minus diagonal corners: a point 3 mm beside the
    // strip, well inside the board, is covered.
    TileShape strip = obliqueStrip();
    FloatPoint besideTheStrip = new FloatPoint(8000, 25000);

    assertTrue(strip.distance(besideTheStrip) > 2500);
    assertTrue(strip.boundingOctagon().contains(besideTheStrip));
    assertTrue(strip.boundingOctagonExcess() > 2500);
  }

  @Test
  void splitPiecesStayCloseToTheStripAndCoverIt() {
    TileShape strip = obliqueStrip();
    double tolerance = 250;

    TileShape[] pieces = TileShape.splitToTightOctagons(new TileShape[] {strip}, tolerance);

    assertTrue(pieces.length > 1);
    double area = 0;
    for (TileShape piece : pieces) {
      assertTrue(
          piece.boundingOctagonExcess() <= tolerance, "excess " + piece.boundingOctagonExcess());
      assertTrue(strip.contains(piece.centreOfGravity()));
      area += piece.area();
    }
    assertEquals(strip.area(), area, strip.area() * 1e-6);
    FloatPoint besideTheStrip = new FloatPoint(8000, 25000);
    for (TileShape piece : pieces) {
      assertTrue(!piece.boundingOctagon().contains(besideTheStrip));
    }
  }

  @Test
  void slicesCoverTheShapeAndKeepTheirCount() {
    TileShape strip = obliqueStrip();

    TileShape[] slices = strip.splitIntoSlices(7);

    assertEquals(7, slices.length);
    double area = 0;
    for (TileShape slice : slices) {
      area += slice.area();
    }
    assertEquals(strip.area(), area, strip.area() * 1e-6);
    assertEquals(1, strip.splitIntoSlices(1).length);
  }
}

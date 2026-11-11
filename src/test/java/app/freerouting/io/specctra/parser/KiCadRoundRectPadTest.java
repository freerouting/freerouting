package app.freerouting.io.specctra.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.structure.Unit;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.io.CoordinateTransform;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link KiCadRoundRectPad}. */
class KiCadRoundRectPadTest {

  private static final double LEGACY_COMPENSATION = 1.0 - Math.cos(Math.PI / 36.0);

  /** The 0603 pad of the KiCad standard library, as written by KiCad 10.0.8. */
  private static final double[] KICAD_10_0603_POLYGON = {
    -400.761, 275, -385.479, 351.828, -341.959, 416.959, -276.828, 460.479, -199.999, 475.76, 200,
    475.761, 276.828, 460.479, 341.959, 416.959, 385.479, 351.828, 400.76, 274.999, 400.761, -275,
    385.479, -351.828, 341.959, -416.959, 276.828, -460.479, 199.999, -475.76, -200, -475.761,
    -276.828, -460.479, -341.959, -416.959, -385.479, -351.828, -400.76, -274.999, -400.761, 275
  };

  private static final String KICAD_10_0603_NAME =
      "RoundRect[T]Pad_800.000000x950.000000_200.761000_um_0.000000_0";

  @Test
  void realRadiusIsRecoveredFromTheLegacyName() {
    assertEquals(200.0, KiCadRoundRectPad.legacyRealRadiusUm(200.761), 1e-9);
    assertEquals(150.0, KiCadRoundRectPad.legacyRealRadiusUm(150.571), 1e-9);
    assertEquals(250.0, KiCadRoundRectPad.legacyRealRadiusUm(250.951), 1e-9);
  }

  @Test
  void cornerSegmentCountFollowsTheRadius() {
    assertEquals(12, KiCadRoundRectPad.segmentsPerQuarter(200));
    assertEquals(1, KiCadRoundRectPad.segmentsPerQuarter(1));
  }

  /** The KiCad 10.0.8 polygon cuts up to 3 um into the real corner; the corrected one does not. */
  @Test
  void kicad10PolygonIsReplacedByOneContainingTheRealPad() {
    CoordinateTransform transform = new CoordinateTransform(10, 0, 0);
    Polygon exported = new Polygon(Layer.PCB, KICAD_10_0603_POLYGON);
    PolygonShape before = (PolygonShape) exported.transformToBoardRel(transform);
    // the middle of a chord, 196.9 um from the corner centre, is inside the real 200 um corner
    assertTrue(!before.contains(cornerPoint(10, 400, 475, 200, Math.toRadians(33.75))));

    Shape corrected = KiCadRoundRectPad.correct(KICAD_10_0603_NAME, exported, Unit.UM, transform);
    assertNotSame(exported, corrected);
    PolygonShape after = (PolygonShape) corrected.transformToBoardRel(transform);
    assertContainsRealPad(after, 10, 400, 475, 200);
    IntBox box = after.boundingBox();
    assertEquals(-4000, box.ll.x);
    assertEquals(4000, box.ur.x);
    assertEquals(-4750, box.ll.y);
    assertEquals(4750, box.ur.y);
  }

  @Test
  void correctedPadContainsTheRealPadForAnySizeAndResolution() {
    double[][] pads = {
      {800, 950, 200}, {600, 1450, 150}, {1000, 1000, 500}, {250, 400, 62.5}, {1750, 600, 25}
    };
    double[] scales = {1, 10, 100, 1000};
    double[][] offsets = {{0, 0}, {123.4, -56.7}};
    for (double[] pad : pads) {
      for (double scale : scales) {
        for (double[] offset : offsets) {
          double r = pad[2];
          double e = Math.round(r * 1000 * LEGACY_COMPENSATION) / 1000.0;
          String name =
              String.format(
                  java.util.Locale.ROOT,
                  "RoundRect[T][%s,%s]Pad_%fx%f_%f_um_0.000000_0",
                  offset[0],
                  offset[1],
                  pad[0],
                  pad[1],
                  r + e);
          double[] legacy =
              KiCadRoundRectPad.corners(
                  offset[0] - pad[0] / 2 - e,
                  offset[1] - pad[1] / 2 - e,
                  offset[0] + pad[0] / 2 + e,
                  offset[1] + pad[1] / 2 + e,
                  r + e,
                  4,
                  0);
          CoordinateTransform transform = new CoordinateTransform(scale, 0, 0);
          Shape corrected =
              KiCadRoundRectPad.correct(name, new Polygon(Layer.PCB, legacy), Unit.UM, transform);
          PolygonShape board = (PolygonShape) corrected.transformToBoardRel(transform);
          assertContainsRealPad(
              board, scale, pad[0] / 2, pad[1] / 2, r, offset[0] * scale, offset[1] * scale);
          // never more than 0.5 um plus a few board units outside the real corner
          double cornerX = offset[0] + pad[0] / 2 - r;
          double cornerY = offset[1] + pad[1] / 2 - r;
          double limit = r + 0.5 + 4 / scale;
          for (app.freerouting.geometry.planar.Point vertex : board.corners) {
            FloatPoint corner = vertex.toFloat();
            double dx = corner.x / scale - cornerX;
            double dy = corner.y / scale - cornerY;
            if (dx > 0 && dy > 0) {
              assertTrue(Math.hypot(dx, dy) <= limit, "corner too far out for " + name);
            }
          }
        }
      }
    }
  }

  @Test
  void chamferRatioWithoutChamferedCornersIsPlainRoundRect() {
    CoordinateTransform transform = new CoordinateTransform(10, 0, 0);
    Polygon polygon = new Polygon(Layer.PCB, KICAD_10_0603_POLYGON);
    assertNotSame(
        polygon,
        KiCadRoundRectPad.correct(
            "RoundRect[T]Pad_800.000000x950.000000_200.761000_um_0.200000_0",
            polygon,
            Unit.UM,
            transform));
  }

  @Test
  void millimetreUnitIsHandled() {
    CoordinateTransform transform = new CoordinateTransform(10000, 0, 0);
    double[] mm = new double[KICAD_10_0603_POLYGON.length];
    for (int i = 0; i < mm.length; i++) {
      mm[i] = KICAD_10_0603_POLYGON[i] / 1000;
    }
    Shape corrected =
        KiCadRoundRectPad.correct(
            KICAD_10_0603_NAME, new Polygon(Layer.PCB, mm), Unit.MM, transform);
    PolygonShape board = (PolygonShape) corrected.transformToBoardRel(transform);
    assertEquals(4000, board.boundingBox().ur.x);
    assertContainsRealPad(board, 10, 400, 475, 200);
  }

  /** A fixed exporter (KiCad 11) writes the nominal size and the real radius: nothing to do. */
  @Test
  void fixedExportIsLeftUntouched() {
    double[] fixed = KiCadRoundRectPad.corners(-400, -475, 400, 475, 200, 4, 0);
    Polygon polygon = new Polygon(Layer.PCB, fixed);
    assertSame(
        polygon,
        KiCadRoundRectPad.correct(
            "RoundRect[T]Pad_800.000000x950.000000_200.000000_um_0.000000_0",
            polygon,
            Unit.UM,
            new CoordinateTransform(10, 0, 0)));
  }

  @Test
  void otherPadstacksAreLeftUntouched() {
    CoordinateTransform transform = new CoordinateTransform(10, 0, 0);
    Polygon polygon = new Polygon(Layer.PCB, KICAD_10_0603_POLYGON);
    assertSame(
        polygon,
        KiCadRoundRectPad.correct(
            "RoundRect[T]Pad_800.000000x950.000000_200.761000_um_0.250000_5",
            polygon,
            Unit.UM,
            transform));
    assertSame(polygon, KiCadRoundRectPad.correct("Custom_Pad", polygon, Unit.UM, transform));
    Rectangle rectangle = new Rectangle(Layer.PCB, new double[] {-400, -475, 400, 475});
    assertSame(
        rectangle, KiCadRoundRectPad.correct(KICAD_10_0603_NAME, rectangle, Unit.UM, transform));
  }

  private static FloatPoint cornerPoint(
      double scale, double halfWidth, double halfHeight, double r, double angle) {
    return new FloatPoint(
        scale * (halfWidth - r + r * Math.cos(angle)),
        scale * (halfHeight - r + r * Math.sin(angle)));
  }

  private static void assertContainsRealPad(
      PolygonShape board, double scale, double halfWidth, double halfHeight, double r) {
    assertContainsRealPad(board, scale, halfWidth, halfHeight, r, 0, 0);
  }

  /** Samples the four corner arcs of the real pad (board coordinates = scale * um). */
  private static void assertContainsRealPad(
      PolygonShape board,
      double scale,
      double halfWidth,
      double halfHeight,
      double r,
      double centreX,
      double centreY) {
    double[] signX = {1, -1, -1, 1};
    double[] signY = {1, 1, -1, -1};
    for (int quarter = 0; quarter < 4; quarter++) {
      for (int i = 1; i < 720; i++) {
        double angle = Math.PI / 2 * i / 720;
        double x = halfWidth - r + r * Math.cos(angle);
        double y = halfHeight - r + r * Math.sin(angle);
        FloatPoint point =
            new FloatPoint(
                centreX + signX[quarter] * scale * x, centreY + signY[quarter] * scale * y);
        assertTrue(board.contains(point), "real pad point outside: " + point);
      }
    }
  }
}

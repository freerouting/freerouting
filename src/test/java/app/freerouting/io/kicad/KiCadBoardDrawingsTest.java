package app.freerouting.io.kicad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The copper drawings without net read from a KiCad board file. */
class KiCadBoardDrawingsTest {

  private static double min(double[] values) {
    return java.util.Arrays.stream(values).min().orElseThrow();
  }

  private static double max(double[] values) {
    return java.util.Arrays.stream(values).max().orElseThrow();
  }

  @Test
  void readsTheCopperDrawingsWithoutNetOfAKiCad10Board() {
    KiCadBoardDrawings board =
        KiCadBoardDrawings.parse(
            "(kicad_pcb (version 20260206) (generator \"pcbnew\")\n"
                + " (layers (0 \"F.Cu\" signal) (4 \"In1.Cu\" signal) (2 \"B.Cu\" signal)"
                + " (5 \"F.SilkS\" user))\n"
                + " (footprint \"NL\" (layer \"F.Cu\") (at 10 10 90)\n"
                + "  (property \"Reference\" \"NL1\" (at 0 0) (layer \"F.Cu\") (hide yes)\n"
                + "   (effects (font (size 1 1) (thickness 0.15))))\n"
                + "  (fp_line (start 1 0) (end 2 0) (stroke (width 0.2) (type solid))"
                + " (layer \"F.Cu\"))\n"
                + "  (fp_line (start 0 0) (end 5 0) (stroke (width 0.2) (type solid))"
                + " (layer \"F.SilkS\")))\n"
                + " (gr_poly (pts (xy 0 0) (xy 1 0) (xy 1 1)) (stroke (width 0) (type solid))"
                + " (fill yes) (layer \"In1.Cu\"))\n"
                + " (gr_line (start 0 0) (end 1 0) (stroke (width 0.2)) (layer \"B.Cu\")"
                + " (net \"GND\"))\n"
                + " (gr_text \"hidden\" (at 0 0) (layer \"F.Cu\") (hide yes)"
                + " (effects (font (size 1 1) (thickness 0.15)))))",
            "board.kicad_pcb");

    // the hidden reference, the silk screen line, the line of GND and the hidden text are left out
    assertEquals(2, board.netlessCopper().size());
    KiCadCopperGraphic line = board.netlessCopper().get(0);
    assertEquals("NL1", line.owner());
    assertEquals(List.of("F.Cu"), line.layers());
    assertEquals(200.0, line.widthUm(), 1e-9);
    assertFalse(line.filled());
    // turned by 90 degrees counter-clockwise on screen: (1, 0) goes up, to (0, -1)
    assertEquals(10000.0, line.xUm()[0], 1e-6);
    assertEquals(9000.0, line.yUm()[0], 1e-6);
    assertEquals(8000.0, line.yUm()[1], 1e-6);
    KiCadCopperGraphic polygon = board.netlessCopper().get(1);
    assertEquals("", polygon.owner());
    assertEquals(List.of("In1.Cu"), polygon.layers());
    assertTrue(polygon.filled());
    assertTrue(polygon.closed());
    assertEquals(3, polygon.cornerCount());
  }

  @Test
  void readsAKiCad5ArcOnARenamedLayerFromItsCentreAndAngle() {
    KiCadBoardDrawings board =
        KiCadBoardDrawings.parse(
            "(kicad_pcb (version 20171130) (host pcbnew 5.1.5)\n"
                + " (layers (0 Top signal) (31 Bottom signal) (37 F.SilkS user))\n"
                + " (module SW (layer Top) (at 100 50)\n"
                + "  (fp_text reference SW1 (at 0 2) (layer F.SilkS)"
                + " (effects (font (size 1 1) (thickness 0.15))))\n"
                + "  (fp_arc (start 0 0) (end 2 0) (angle 90) (layer Top) (width 0.25))))",
            "old.kicad_pcb");

    KiCadCopperGraphic arc = board.netlessCopper().getFirst();
    assertEquals("SW1", arc.owner());
    assertEquals(List.of("Top"), arc.layers());
    // from (2, 0) a quarter turn around the centre, clockwise on screen: to (0, 2)
    int last = arc.cornerCount() - 1;
    assertEquals(102000.0, arc.xUm()[0], 1e-6);
    assertEquals(100000.0, arc.xUm()[last], 1e-6);
    assertEquals(52000.0, arc.yUm()[last], 1e-6);
    // the corners are on the circle: the width grows by the sagitta of a segment of 10 degrees
    assertTrue(arc.widthUm() > 250.0 && arc.widthUm() < 250.0 + 2 * 2000 * 0.004);
  }

  @Test
  void eachCharacterOfACopperTextBecomesARectangleAroundItsStrokes() {
    KiCadBoardDrawings board =
        KiCadBoardDrawings.parse(
            "(kicad_pcb (version 20171130) (host pcbnew 5.1.5)\n"
                + " (gr_text \"AB\\nC\" (at 50 50 90) (layer B.Cu)"
                + " (effects (font (size 1 1) (thickness 0.15)) (justify mirror))))",
            "text.kicad_pcb");

    // one filled rectangle per character
    List<KiCadCopperGraphic> characters = board.netlessCopper();
    assertEquals(3, characters.size());
    double[] xs =
        characters.stream().flatMapToDouble(c -> java.util.Arrays.stream(c.xUm())).toArray();
    double[] ys =
        characters.stream().flatMapToDouble(c -> java.util.Arrays.stream(c.yUm())).toArray();
    for (KiCadCopperGraphic character : characters) {
      assertEquals("text", character.kind());
      assertTrue(character.filled());
    }
    // two lines, "AB" the longer one, turned by 90 degrees and mirrored: KiCad 10 draws the
    // strokes within x 48.572 to 51.338 and y 49.089 to 50.816
    assertTrue(min(xs) < 48572 && max(xs) > 51338);
    assertTrue(min(ys) < 49089 && max(ys) > 50816);
    assertTrue(max(ys) - min(ys) < 3000);
    assertTrue(max(xs) - min(xs) < 4500);
  }
}

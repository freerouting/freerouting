package app.freerouting.io.specctra.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.searchtree.ExactCopper;
import app.freerouting.core.library.PadOval;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Circle;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.CoordinateTransform;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A wide path of one or two corners is how KiCad writes a round or oval pad. Its board shape is the
 * bounding octagon of the path, which reaches 8% of the half width past each round end, at 22.5
 * degrees from the axes; the clearance checks measure the oval itself, so two pads or a pad and a
 * trace at a legal gap are not reported, and not refused to the router.
 */
class OvalPadExactCopperTest {

  private static FloatPoint p(double x, double y) {
    return new FloatPoint(x, y);
  }

  private static ExactCopper oval(double ax, double ay, double bx, double by, double radius) {
    return ExactCopper.ofOval(new PadOval(p(ax, ay), p(bx, by), radius), 0);
  }

  /** The distance from an oval to a circle is the distance to its centre line, less both radii. */
  @Test
  void ovalToCircle() {
    ExactCopper oval = oval(-100, 0, 100, 0, 50);
    assertEquals(30, ExactCopper.distance(oval, ExactCopper.of(new Circle(p(0, 100).round(), 20))));
    double diagonal = Math.hypot(100, 100) - 50 - 20;
    assertEquals(
        diagonal,
        ExactCopper.distance(oval, ExactCopper.of(new Circle(p(200, 100).round(), 20))),
        1e-9);
  }

  /** A round pad (a path of one corner) is a circle. */
  @Test
  void onePointOvalIsACircle() {
    ExactCopper round = oval(0, 0, 0, 0, 50);
    assertEquals(50, ExactCopper.distance(round, oval(150, 0, 150, 0, 50)));
  }

  /** Two ovals, end to end, side by side, and crossing. */
  @Test
  void ovalToOval() {
    ExactCopper a = oval(-100, 0, 100, 0, 50);
    assertEquals(100, ExactCopper.distance(a, oval(300, 0, 500, 0, 50)), 1e-9);
    assertEquals(20, ExactCopper.distance(a, oval(-50, 120, 50, 120, 50)), 1e-9);
    assertEquals(0, ExactCopper.distance(a, oval(0, -300, 0, 300, 1)));
    // the end of one against the flank of the other
    assertEquals(10, ExactCopper.distance(a, oval(0, 110, 0, 400, 50)), 1e-9);
  }

  /** An oval against a polygon: beside it, crossing it without a corner inside, and inside it. */
  @Test
  void ovalToPolygon() {
    ExactCopper box = ExactCopper.of(new IntBox(-50, -50, 50, 50));
    assertEquals(25, ExactCopper.distance(box, oval(100, -200, 100, 200, 25)), 1e-9);
    assertEquals(0, ExactCopper.distance(box, oval(-200, 0, 200, 0, 1)));
    assertEquals(0, ExactCopper.distance(box, oval(-10, 0, 10, 0, 1)));
    // a slanted oval whose end comes closest to a corner of the box
    double expected = Math.hypot(30, 40) - 5;
    assertEquals(expected, ExactCopper.distance(box, oval(80, 90, 300, 400, 5)), 1e-9);
  }

  private static RoutingBoard board(String placement, String wiring) {
    String dsn =
        """
        (pcb oval-pads
          (resolution um 10)
          (unit um)
          (structure
            (layer Top (type signal))
            (boundary (rect pcb -8000 -8000 10000 10000))
            (rule (width 250) (clearance 200)))
          (placement
            (component OVAL %s))
          (library
            (image OVAL (pin Oval 1 0 0))
            (padstack Oval (shape (path Top 1500 -750 0 750 0)) (attach off)))
          (network (net A (pins O1-1)) (net B (pins O2-1)))
          (wiring %s))
        """
            .formatted(placement, wiring);
    ByteArrayInputStream stream = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    return (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private static Collection<ClearanceViolation> violations(RoutingBoard board) {
    return new DesignRulesChecker(board, null).getAllClearanceViolations();
  }

  /**
   * Two 3 x 1.5 mm ovals of two nets, both turned by {@code rotation}, whose round ends face each
   * other along 67.5 degrees (before the turn), where the bounding octagon reaches furthest past
   * the copper: 8.2% of 750 um at each end.
   */
  private static RoutingBoard facingOvals(int gap, int rotation) {
    double distance = 1500 + gap;
    double x = 1500 + distance * Math.cos(Math.toRadians(67.5));
    double y = distance * Math.sin(Math.toRadians(67.5));
    double turn = Math.toRadians(rotation);
    long bx = Math.round(x * Math.cos(turn) - y * Math.sin(turn));
    long by = Math.round(x * Math.sin(turn) + y * Math.cos(turn));
    return board(
        "(place O1 0 0 front %d) (place O2 %d %d front %d)".formatted(rotation, bx, by, rotation),
        "");
  }

  /**
   * 201 to 300 um of copper gap pass a 200 um rule; the bounding octagons of the two ends take up
   * to 124 um of it, and master reports a violation that KiCad does not.
   */
  @ParameterizedTest
  @CsvSource({"201, 0", "210, 0", "260, 0", "300, 0", "201, 30", "260, 30", "201, 90", "260, 135"})
  void ovalPadsAtALegalGapHaveNoViolation(int gap, int rotation) {
    assertTrue(
        violations(facingOvals(gap, rotation)).isEmpty(),
        "false clearance violation at a gap of " + gap + " um, turned by " + rotation);
  }

  /** The control: a gap really below the rule is still a violation, turned or not. */
  @ParameterizedTest
  @CsvSource({"150, 0", "150, 30", "195, 30", "150, 135"})
  void ovalPadsBelowTheRuleStillViolate(int gap, int rotation) {
    assertFalse(violations(facingOvals(gap, rotation)).isEmpty());
  }

  /**
   * A 250 um trace across the 67.5-degree direction of an oval's end, {@code gap} um of copper away
   * from it.
   */
  private static RoutingBoard traceBesideAnEnd(int gap) {
    double ux = Math.cos(Math.toRadians(67.5));
    double uy = Math.sin(Math.toRadians(67.5));
    double cx = 750 + (750 + 125 + gap) * ux;
    double cy = (750 + 125 + gap) * uy;
    return board(
        "(place O1 0 0 front 0)",
        "(wire (path Top 250 %d %d %d %d) (net B))"
            .formatted(
                Math.round(cx - 1000 * uy),
                Math.round(cy + 1000 * ux),
                Math.round(cx + 1000 * uy),
                Math.round(cy - 1000 * ux)));
  }

  @ParameterizedTest
  @CsvSource({"201", "230", "260"})
  void traceAtALegalGapFromAnOvalHasNoViolation(int gap) {
    assertTrue(
        violations(traceBesideAnEnd(gap)).isEmpty(),
        "false clearance violation at a gap of " + gap + " um");
  }

  @Test
  void traceBelowTheRuleFromAnOvalStillViolates() {
    assertFalse(violations(traceBesideAnEnd(150)).isEmpty());
  }

  /** The pin carries its oval, placed as its shape: turned, moved, and on the same centre line. */
  @Test
  void pinCarriesItsPlacedOval() {
    RoutingBoard board = board("(place O1 2000 1000 front 30)", "");
    Pin pin = board.getPins().iterator().next();
    PadOval oval = pin.getPlacedOval(0);
    assertNotNull(oval);
    assertEquals(7500, oval.radius(), 1e-9);
    double ex = 7500 * Math.cos(Math.toRadians(30));
    double ey = 7500 * Math.sin(Math.toRadians(30));
    FloatPoint first = oval.start();
    FloatPoint second = oval.end();
    double centreX = (first.x + second.x) / 2;
    double centreY = (first.y + second.y) / 2;
    assertEquals(20000, centreX, 1e-6);
    assertEquals(10000, centreY, 1e-6);
    assertEquals(ex, Math.abs(second.x - centreX), 1e-6);
    assertEquals(ey, Math.abs(second.y - centreY), 1e-6);
    // the board shape is still the bounding polygon, around the oval
    assertTrue(((TileShape) pin.getShape(0)).contains(first));
    assertTrue(((TileShape) pin.getShape(0)).contains(second));
  }

  /** Paths of three or more corners, and zero-width paths, are not pads. */
  @Test
  void onlyWideOneOrTwoPointPathsAreOvals() {
    CoordinateTransform transform = new CoordinateTransform(10, 0, 0);
    assertNull(
        new PolygonPath(Layer.PCB, 100, new double[] {0, 0, 10, 0, 10, 10}).padOvalRel(transform));
    assertNull(new PolygonPath(Layer.PCB, 0, new double[] {0, 0, 10, 0}).padOvalRel(transform));
    assertNotNull(new PolygonPath(Layer.PCB, 100, new double[] {0, 0}).padOvalRel(transform));
  }
}

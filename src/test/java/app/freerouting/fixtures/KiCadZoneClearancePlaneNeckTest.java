package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.TestFixtures;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.kicad.KiCadBoardZones;
import app.freerouting.settings.sources.TestingSettings;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * KiCad does not write the clearance of a zone into the DSN: the plane gets the clearance of its
 * net class, and a neck of the plane that the refill of the zone will remove counts as copper
 * (issue #1076).
 *
 * <p>{@code Plane-zone-clearance-neck-2layer.dsn}: a GND plane on F.Cu with the GND pins J1 (left)
 * and J2 (right). Two fixed traces of another net cross the plane from top to bottom and leave a
 * 0.95 mm gap between their ends. With the 0.2 mm clearance of the net class, 0.55 mm of plane is
 * left in the gap and the plane is in one piece: nothing is left to route. The zone of the {@code
 * .kicad_pcb} asks for 0.5 mm: nothing is left of the neck, J1 and J2 are on two islands, and the
 * connection between them is incomplete.
 *
 * <p>The four cases were checked against the zone filler of KiCad 10.0.8 on the same geometry: one
 * piece of copper with 0.2 mm, and with 0.3 mm and a minimum width of 0.1 mm; two pieces with 0.5
 * mm, and with 0.3 mm and a minimum width of 0.5 mm.
 */
class KiCadZoneClearancePlaneNeckTest extends RoutingFixtureTest {

  private static final String BOARD = "Plane-zone-clearance-neck-2layer.dsn";

  /** Loads the board and counts, without routing anything. */
  private RoutingJob load(KiCadBoardZones zones) {
    TestingSettings settings = new TestingSettings();
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(false);
    settings.setOptimizerEnabled(false);
    RoutingJob job = getRoutingJob(BOARD, settings);
    job.kicadBoardZones = zones;
    runRoutingJob(job);
    assertEquals(RoutingJobState.COMPLETED, job.state);
    return job;
  }

  private static KiCadBoardZones zone(String clearance, String minThickness) {
    return KiCadBoardZones.parse(
        "(kicad_pcb (zone (net \"GND\") (layer \"F.Cu\") (connect_pads (clearance "
            + clearance
            + ")) (min_thickness "
            + minThickness
            + ") (polygon (pts (xy 1 1) (xy 39 1) (xy 39 24) (xy 1 24)))))",
        "test.kicad_pcb");
  }

  private static ConductionArea plane(RoutingJob job) {
    return job.board.getConductionAreas().iterator().next();
  }

  private static int incompletes(RoutingJob job) {
    return new BoardStatistics(job.board).connections.incompleteCount;
  }

  /** Without the KiCad board, the DSN alone says that the plane connects J1 and J2. */
  @Test
  void withTheNetClassClearanceThePlaneIsInOnePiece() {
    RoutingJob job = load(null);

    assertFalse(plane(job).isFractured());
    assertEquals(0, incompletes(job));
  }

  /** With the zone clearance of the KiCad board, the neck is no copper. */
  @Test
  void withTheZoneClearanceTheNeckIsCut() throws IOException {
    RoutingJob job =
        load(
            KiCadBoardZones.read(
                TestFixtures.resolvePath("Plane-zone-clearance-neck-2layer.kicad_pcb")));

    ConductionArea plane = plane(job);
    assertEquals(5000, plane.getFillClearance(), "zone clearance of 0.5 mm in board units");
    assertEquals(2500, plane.getFillMinWidth(), "zone minimum width of 0.25 mm in board units");
    assertTrue(plane.isFractured(), "the plane is still in one piece with a 0.5 mm clearance");
    assertEquals(1, incompletes(job), "J1 and J2 are on two islands of the plane");
  }

  /**
   * The minimum width counts too: with a zone clearance of 0.3 mm, 0.35 mm of plane is left in the
   * gap, but the zone removes every neck narrower than 0.5 mm.
   */
  @Test
  void aNeckNarrowerThanTheMinimumWidthOfTheZoneIsCut() {
    RoutingJob job = load(zone("0.3", "0.5"));

    assertTrue(plane(job).isFractured(), "the 0.35 mm neck counts as copper");
    assertEquals(1, incompletes(job));
  }

  /** The same clearance with a minimum width of 0.1 mm leaves the 0.35 mm neck. */
  @Test
  void aNeckWiderThanTheMinimumWidthIsKept() {
    RoutingJob job = load(zone("0.3", "0.1"));

    assertFalse(plane(job).isFractured(), "a 0.35 mm neck is cut by a 0.1 mm minimum width");
    assertEquals(0, incompletes(job));
  }

  /** A zone of another net or another layer changes nothing. */
  @Test
  void aZoneOfAnotherNetOrLayerIsNotApplied() {
    RoutingJob job =
        load(
            KiCadBoardZones.parse(
                "(kicad_pcb (zone (net \"SIG\") (layer \"F.Cu\") (connect_pads (clearance 0.5))"
                    + " (min_thickness 0.25) (polygon (pts (xy 1 1) (xy 39 1) (xy 39 24))))"
                    + " (zone (net \"GND\") (layer \"B.Cu\") (connect_pads (clearance 0.5))"
                    + " (min_thickness 0.25) (polygon (pts (xy 1 1) (xy 39 1) (xy 39 24)))))",
                "other.kicad_pcb"));

    assertEquals(0, plane(job).getFillClearance());
    assertFalse(plane(job).isFractured());
  }
}

package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.BoardItemType;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.items.WireObstacleArea;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.RoutingJob;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.settings.sources.TestingSettings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #1018: "KiCad rule areas exported as wire_keepout produce false
 * clearance violations on vias".
 *
 * <p>Specctra DSN specification states:
 *
 * <ul>
 *   <li>{@code keepout}: prohibits both tracks and vias.
 *   <li>{@code wire_keepout}: prohibits tracks/wires only; vias are allowed.
 *   <li>{@code via_keepout}: prohibits vias only; tracks are allowed.
 * </ul>
 */
class Issue1018WireKeepoutClearanceTest extends RoutingFixtureTest {

  @Override
  @BeforeEach
  protected void setUp() {
    super.setUp();
    System.setProperty("freerouting.logging.console.level", "INFO");
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = false;
  }

  @Test
  @DisplayName("Issue #1018: fixed via inside wire_keepout has 0 clearance violations")
  void testFixedViaInWireKeepoutHasZeroClearanceViolations() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue1018-synthetic-via-in-wire-keepout.dsn");
    assertNotNull(board, "Board must load successfully");

    // Verify WireObstacleArea was created on layer B.Cu (layer index 1)
    boolean foundWireKeepout = false;
    for (Item item : board.getItems()) {
      if (item instanceof WireObstacleArea) {
        foundWireKeepout = true;
        assertEquals(BoardItemType.WIRE_OBSTACLE_AREA, item.getBoardItemType());
        assertEquals(1, item.firstLayer(), "wire_keepout must be on B.Cu (layer 1)");
      }
    }
    assertTrue(foundWireKeepout, "Board must contain WireObstacleArea");

    // Full DRC clearance check: via inside wire_keepout must report 0 violations
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    Collection<ClearanceViolation> violations = drc.getAllClearanceViolations();
    assertEquals(
        0,
        violations.size(),
        "Fixed via inside wire_keepout must not report any clearance violations");
  }

  @Test
  @DisplayName("Issue #1018: baseline keepout correctly preserves clearance violation on via")
  void testBaselineGeneralKeepoutPreservesClearanceViolation() throws IOException {
    // Read the same synthetic DSN but replace wire_keepout with general keepout
    String dsnContent;
    try (InputStream in =
        DsnTestFixtures.openResource("Issue1018-synthetic-via-in-wire-keepout.dsn")) {
      dsnContent = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
    String keepoutDsn = dsnContent.replace("wire_keepout", "keepout");

    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(keepoutDsn.getBytes(StandardCharsets.UTF_8)), null, null);
    assertTrue(result instanceof BoardReadResult.Success, "Modified DSN must parse successfully");
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    Collection<ClearanceViolation> violations = drc.getAllClearanceViolations();
    assertEquals(
        1, violations.size(), "General keepout must strictly prohibit vias, reporting 1 violation");
  }

  @Test
  @DisplayName(
      "Issue #1018: trace entering wire_keepout is strictly detected as a clearance violation")
  void testTraceEnteringWireKeepoutTriggersClearanceViolation() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue1018-synthetic-via-in-wire-keepout.dsn");
    assertNotNull(board);

    // Insert an overlapping trace on B.Cu (layer 1) that crosses through wire_keepout
    // [90000..110000]
    IntPoint p1 = new IntPoint(80000, 100000);
    IntPoint p2 = new IntPoint(120000, 100000);
    Polyline polyline = new Polyline(new IntPoint[] {p1, p2});
    app.freerouting.rules.Net netA = board.rules.nets.get("A", 1);
    int clClass =
        netA.getNetClass()
            .defaultItemClearanceClasses
            .get(app.freerouting.rules.DefaultItemClearanceClasses.ItemClass.TRACE);
    int[] netNumbers = new int[] {netA.netNumber};
    board.insertTrace(polyline, 1, 750, netNumbers, clClass, FixedState.USER_FIXED);

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    Collection<ClearanceViolation> violations = drc.getAllClearanceViolations();
    assertFalse(
        violations.isEmpty(),
        "Traces entering wire_keepout must be detected as clearance violations");
  }

  @Test
  @DisplayName("Issue #1018: WireObstacleArea edge cases and null safety")
  void testWireObstacleAreaEdgeCasesAndNullSafety() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Issue1018-synthetic-via-in-wire-keepout.dsn");
    assertNotNull(board);

    // 1. BasicBoard.insertWireObstacle null guard
    assertNull(board.insertWireObstacle(null, 0, 0, FixedState.SYSTEM_FIXED));
    assertNull(
        board.insertWireObstacle(null, 0, null, 0, false, 0, 0, "test", FixedState.SYSTEM_FIXED));

    // 2. Locate WireObstacleArea on the board
    WireObstacleArea wireObstacle = null;
    for (Item item : board.getItems()) {
      if (item instanceof WireObstacleArea area) {
        wireObstacle = area;
        break;
      }
    }
    assertNotNull(wireObstacle, "WireObstacleArea must be present");

    // 3. Null and identity checks
    assertFalse(wireObstacle.isObstacle(null));
    assertFalse(wireObstacle.isObstacle(wireObstacle));

    // 4. Vias and pins are never obstacles to wire_keepout
    Via sampleVia = null;
    Pin samplePin = null;
    for (Item item : board.getItems()) {
      if (item instanceof Via v) {
        sampleVia = v;
      } else if (item instanceof Pin p) {
        samplePin = p;
      }
    }
    assertNotNull(sampleVia);
    assertNotNull(samplePin);

    assertFalse(
        wireObstacle.isObstacle(sampleVia), "Via must not be an obstacle to WireObstacleArea");
    assertFalse(
        sampleVia.isObstacle(wireObstacle), "WireObstacleArea must not be an obstacle to Via");
    assertFalse(
        wireObstacle.isObstacle(samplePin), "Pin must not be an obstacle to WireObstacleArea");

    // 5. Trace obstacle check
    assertTrue(wireObstacle.isTraceObstacle(999), "Unrelated net must be a trace obstacle");

    // 6. DrillPage skips drillable items, so a wire keepout must not carve a via hole.
    assertTrue(wireObstacle.isDrillable(999), "Vias must be placeable inside a wire keepout");
    assertTrue(wireObstacle.isDrillable(sampleVia.getNetNumber(0)));
  }

  @Test
  @DisplayName("Issue #1018: autorouting synthetic board completes with 0 clearance violations")
  void testAutorouterCompletesWithZeroViolations() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(2);
    RoutingJob job = getRoutingJob("Issue1018-synthetic-via-in-wire-keepout.dsn", settings);
    job = runRoutingJob(job);

    assertNotNull(job.board);
    DesignRulesChecker drc = new DesignRulesChecker(job.board, null);
    assertEquals(
        0, drc.getAllClearanceViolations().size(), "Routing must finish with 0 DRC violations");
  }
}

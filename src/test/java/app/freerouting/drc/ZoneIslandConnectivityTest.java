package app.freerouting.drc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.library.Padstack;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.Net;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.DesignRulesCheckerSettings;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests detection of fragmented zone islands and floating dead copper in DesignRulesChecker. */
class ZoneIslandConnectivityTest {

  private RoutingBoard board;
  private Net gndNet;
  private Net sigNet;
  private Padstack defaultPadstack;

  @BeforeEach
  void setUp() {
    Layer layer1 = new Layer("Top", false);
    Layer layer2 = new Layer("GND", false);
    Layer[] layers = new Layer[] {layer1, layer2};
    LayerStructure layerStructure = new LayerStructure(layers);

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    ViaRule dummyViaRule = new ViaRule("defaultViaRule");
    boardRules.viaRules.add(dummyViaRule);
    boardRules.getDefaultNetClass().setViaRule(dummyViaRule);

    Communication communication = new Communication();

    board =
        new RoutingBoard(
            new IntBox(0, 0, 10000000, 10000000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 10000000, 10000000)},
            0,
            boardRules,
            communication);

    gndNet = board.rules.nets.add("GND", 1, true);
    sigNet = board.rules.nets.add("SIG", 1, false);

    board.library.padstacks = new app.freerouting.core.library.Padstacks(layerStructure);
    // Create a padstack for via test items
    ConvexShape[] shapes =
        new ConvexShape[] {
          TileShape.getInstance(-100000, -100000, 100000, 100000),
          TileShape.getInstance(-100000, -100000, 100000, 100000)
        };
    defaultPadstack = board.library.padstacks.add("TestViaPadstack", shapes, true, false);
  }

  @Test
  void testContinuousPourHasNoViolations() {
    // Add a conduction area on GND (layer 1)
    Area area = TileShape.getInstance(100000, 100000, 9000000, 9000000);
    board.insertConductionArea(area, 1, new int[] {gndNet.netNumber}, 0, false, FixedState.UNFIXED);

    DesignRulesChecker drc = new DesignRulesChecker(board, new DesignRulesCheckerSettings());
    Collection<ZoneIslandViolation> violations = drc.getZoneIslandViolations();

    assertNotNull(violations);
    assertTrue(
        violations.isEmpty(),
        "Continuous pour without fragmentation should have zero zone island violations");
  }

  @Test
  void testSeveredPourDetectsIsolatedUnconnectedIsland() {
    // Large pour on layer 0 covering (0,0) to (4000000, 4000000)
    Area pourArea = TileShape.getInstance(0, 0, 4000000, 4000000);
    board.insertConductionArea(
        pourArea, 0, new int[] {gndNet.netNumber}, 0, false, FixedState.UNFIXED);

    // Insert two vias belonging to GND: one on left, one on right
    // Via 1 at (500000, 2000000)
    board.insertVia(
        defaultPadstack,
        new IntPoint(500000, 2000000),
        new int[] {gndNet.netNumber},
        0,
        FixedState.UNFIXED,
        true);

    // Via 2 at (3500000, 2000000)
    board.insertVia(
        defaultPadstack,
        new IntPoint(3500000, 2000000),
        new int[] {gndNet.netNumber},
        0,
        FixedState.UNFIXED,
        true);

    // Insert a foreign trace (SIG net) cutting all the way vertically across layer 0
    Point[] traceCorners = new Point[] {new IntPoint(2000000, 0), new IntPoint(2000000, 4000000)};
    board.insertTrace(traceCorners, 0, 100000, new int[] {sigNet.netNumber}, 0, FixedState.UNFIXED);

    DesignRulesChecker drc = new DesignRulesChecker(board, new DesignRulesCheckerSettings());
    Collection<ZoneIslandViolation> violations = drc.getZoneIslandViolations();

    assertNotNull(violations);
    assertFalse(violations.isEmpty(), "Severed pour should produce a zone island violation");

    boolean foundUnconnected = false;
    for (ZoneIslandViolation v : violations) {
      if ("isolated_island_unconnected".equals(v.type)) {
        foundUnconnected = true;
        assertEquals(0, v.layer);
        assertEquals(gndNet.netNumber, v.netNumber);
        assertFalse(v.itemsInIsland.isEmpty());
      }
    }
    assertTrue(foundUnconnected, "Should find isolated_island_unconnected violation");
  }

  @Test
  void testDeadCopperIslandDetection() {
    // Large pour on layer 0 covering (0,0) to (4000000, 4000000)
    Area pourArea = TileShape.getInstance(0, 0, 4000000, 4000000);
    board.insertConductionArea(
        pourArea, 0, new int[] {gndNet.netNumber}, 0, false, FixedState.UNFIXED);

    // Via 1 at (500000, 2000000) (anchors the main island)
    board.insertVia(
        defaultPadstack,
        new IntPoint(500000, 2000000),
        new int[] {gndNet.netNumber},
        0,
        FixedState.UNFIXED,
        true);

    // Foreign trace cuts off the right side where NO vias/pins exist
    Point[] traceCorners = new Point[] {new IntPoint(2500000, 0), new IntPoint(2500000, 4000000)};
    board.insertTrace(traceCorners, 0, 100000, new int[] {sigNet.netNumber}, 0, FixedState.UNFIXED);

    DesignRulesChecker drc = new DesignRulesChecker(board, new DesignRulesCheckerSettings());
    Collection<ZoneIslandViolation> violations = drc.getZoneIslandViolations();

    assertNotNull(violations);
    assertFalse(violations.isEmpty(), "Cut-off dead copper island should produce a violation");

    boolean foundDeadCopper = false;
    for (ZoneIslandViolation v : violations) {
      if ("isolated_island_dead_copper".equals(v.type)) {
        foundDeadCopper = true;
        assertEquals(0, v.layer);
        assertEquals(gndNet.netNumber, v.netNumber);
        assertTrue(v.itemsInIsland.isEmpty());
      }
    }
    assertTrue(foundDeadCopper, "Should find isolated_island_dead_copper violation");
  }
}

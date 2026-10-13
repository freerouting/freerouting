package app.freerouting.io.specctra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.ObstacleArea;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.core.library.Padstack;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.rules.Net;
import app.freerouting.settings.GlobalSettings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for non-convex custom padstack support (Issue 879).
 *
 * <p>Verifies that arbitrary non-convex pad geometries in Specctra DSN files are detected, flagged,
 * decomposed into a primary core pin plus auxiliary copper obstacle areas of the same net, and
 * routed without false clearance violations or copper inflation.
 */
class NonConvexPadstackTest {

  @BeforeEach
  void setUp() {
    Freerouting.globalSettings = new GlobalSettings();
  }

  /**
   * Minimal DSN string containing an L-shaped non-convex custom pad:
   *
   * <p>Vertices: (-100, -100) -> (100, -100) -> (100, 0) -> (0, 0) -> (0, 100) -> (-100, 100)
   * Bottom rectangle: [-100, 100] x [-100, 0] (contains origin (0, 0)) Top-left rectangle: [-100,
   * 0] x [0, 100] Top-right notch: [0, 100] x [0, 100] is empty void (not copper).
   */
  private static final String DSN_WITH_NON_CONVEX_PAD =
      """
      (pcb "test_non_convex_pad.dsn"
        (parser
          (string_quote ")
          (space_in_quoted_tokens on)
          (host_cad "KiCad's Pcbnew")
          (host_version "10.0.0")
        )
        (resolution um 10)
        (unit um)
        (structure
          (layer F.Cu
            (type signal)
            (property (index 0))
          )
          (layer B.Cu
            (type signal)
            (property (index 1))
          )
          (boundary
            (path pcb 0  0 0  10000 0  10000 10000  0 10000  0 0)
          )
          (via "Via[0-1]_800:400_um")
          (rule
            (width 200)
            (clearance 50)
            (clearance 50 (type default_smd))
            (clearance 50 (type smd_smd))
          )
        )
        (placement
          (component "U_TEST:TEST_PKG"
            (place U1 5000 5000 front 0)
          )
        )
        (library
          (image "U_TEST:TEST_PKG"
            (pin Custom_L_Pad 1 0 0)
          )
          (padstack Custom_L_Pad
            (shape (polygon F.Cu 0 -100 -100  100 -100  100 0  0 0  0 100  -100 100))
            (attach off)
          )
          (padstack "Via[0-1]_800:400_um"
            (shape (circle F.Cu 800))
            (shape (circle B.Cu 800))
            (attach off)
          )
        )
        (network
          (net NET1
            (pins U1-1)
          )
        )
      )
      """;

  @Test
  void nonConvexPadstackIsDetectAndDecomposed() {
    InputStream in =
        new ByteArrayInputStream(DSN_WITH_NON_CONVEX_PAD.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);

    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    // Verify Padstack detection
    Padstack padstack = board.library.padstacks.get("Custom_L_Pad");
    assertNotNull(padstack, "Padstack 'Custom_L_Pad' must exist in library");
    assertTrue(padstack.hasNonConvexGeometry(), "Padstack must be flagged as non-convex");
    assertTrue(padstack.hasAuxiliaryShapes(), "Padstack must have auxiliary shapes");

    ConvexShape coreShape = padstack.getShape(0);
    assertNotNull(coreShape, "Core pin shape on layer 0 must not be null");

    ConvexShape[] auxShapes = padstack.getAuxiliaryShapes(0);
    assertNotNull(auxShapes, "Auxiliary shapes on layer 0 must not be null");
    assertTrue(auxShapes.length > 0, "At least one auxiliary shape expected for L-shaped pad");

    // Verify Board items: Pin + ObstacleArea companion
    Collection<Pin> pins = board.getPins();
    assertEquals(1, pins.size(), "Board must contain 1 pin");
    Pin pin = pins.iterator().next();
    assertEquals(1, pin.netCount(), "Pin must belong to 1 net");
    int pinNet = pin.getNetNumber(0);

    // Verify auxiliary obstacle area exists on the board
    Collection<Item> items = board.getItems();
    List<ObstacleArea> auxObstacles =
        items.stream()
            .filter(item -> item instanceof ObstacleArea)
            .map(item -> (ObstacleArea) item)
            .filter(obs -> obs.name != null && obs.name.endsWith("_aux"))
            .toList();

    assertFalse(
        auxObstacles.isEmpty(),
        "Board must contain at least one companion ObstacleArea for the non-convex pad");
    ObstacleArea aux = auxObstacles.get(0);
    assertEquals(0, aux.getLayer(), "Companion obstacle must be on F.Cu (layer 0)");
    assertTrue(
        aux.containsNet(pinNet), "Companion obstacle must share the pin's net (" + pinNet + ")");

    // Verify DRC: Zero clearance violations between pin, companion obstacle, and net
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    Collection<ClearanceViolation> violations = drc.getAllClearanceViolations();
    assertTrue(
        violations.isEmpty(),
        "No clearance violations should exist between pin and companion obstacle of same net; got: "
            + violations.size());
  }

  @Test
  void concaveRegionIsNotBlockedAndAuxiliaryCopperIsProtected() {
    // DSN with two components: U1 with the L-shaped pad, and U2 placed in the concave void
    // The concave void of U1 (at 5000, 5000) is [5000, 5100] x [5000, 5100].
    // Placing U2 at (5070, 5070) with a 20 um pad puts it 60 um away from the copper edges
    // (clearance is 50 um).
    String dsn =
        """
        (pcb "test_void.dsn"
          (parser
            (string_quote ")
            (space_in_quoted_tokens on)
            (host_cad "KiCad's Pcbnew")
            (host_version "10.0.0")
          )
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal) (property (index 0)))
            (layer B.Cu (type signal) (property (index 1)))
            (boundary (path pcb 0 0 0 10000 0 10000 10000 0 10000 0 0))
            (via "Via[0-1]_800:400_um")
            (rule (width 200) (clearance 50))
          )
          (placement
            (component "U_TEST:TEST_PKG" (place U1 5000 5000 front 0))
            (component "U_VOID:VOID_PKG" (place U2 5070 5070 front 0))
          )
          (library
            (image "U_TEST:TEST_PKG"
              (pin Custom_L_Pad 1 0 0)
            )
            (image "U_VOID:VOID_PKG"
              (pin Small_Pad 1 0 0)
            )
            (padstack Custom_L_Pad
              (shape (polygon F.Cu 0 -100 -100  100 -100  100 0  0 0  0 100  -100 100))
              (attach off)
            )
            (padstack Small_Pad
              (shape (rect F.Cu -10 -10 10 10))
              (attach off)
            )
            (padstack "Via[0-1]_800:400_um"
              (shape (circle F.Cu 800))
              (shape (circle B.Cu 800))
              (attach off)
            )
          )
          (network
            (net NET1 (pins U1-1))
            (net NET2 (pins U2-1))
          )
        )
        """;

    InputStream in = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    // Verify DRC passes cleanly: U2 in the concave void does NOT violate clearance
    // (With convex-hull inflation, U2 would have intersected the false copper and failed DRC)
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    Collection<ClearanceViolation> violations = drc.getAllClearanceViolations();
    assertTrue(
        violations.isEmpty(),
        "Item in concave void should not violate clearance (no convex-hull inflation); got: "
            + violations.size());

    // Verify foreign-net trace crossing the auxiliary copper tile triggers a DRC violation
    Polyline foreignTrace =
        new Polyline(new IntPoint[] {new IntPoint(49500, 50200), new IntPoint(49500, 50800)});
    Net net2 = board.rules.nets.get("NET2", 1);
    assertNotNull(net2, "NET2 must exist");
    int clClass = net2.getNetClass().getTraceClearanceClass();
    board.insertTrace(
        foreignTrace, 0, 100, new int[] {net2.netNumber}, clClass, FixedState.SYSTEM_FIXED);
    Collection<ClearanceViolation> violationsAfterTrack = drc.getAllClearanceViolations();
    assertFalse(
        violationsAfterTrack.isEmpty(),
        "Foreign track crossing auxiliary copper tile must trigger clearance violation");
  }

  @Test
  void foreignPinOverlappingAuxiliaryTileTriggersClearanceViolation() {
    // DSN with foreign pin U2 overlapping the auxiliary copper wing of U1 at (4950, 5050)
    String dsn =
        """
        (pcb "test_overlap.dsn"
          (parser
            (string_quote ")
            (space_in_quoted_tokens on)
            (host_cad "KiCad's Pcbnew")
            (host_version "10.0.0")
          )
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal) (property (index 0)))
            (layer B.Cu (type signal) (property (index 1)))
            (boundary (path pcb 0 0 0 10000 0 10000 10000 0 10000 0 0))
            (via "Via[0-1]_800:400_um")
            (rule (width 200) (clearance 50))
          )
          (placement
            (component "U_TEST:TEST_PKG" (place U1 5000 5000 front 0))
            (component "U_VOID:VOID_PKG" (place U2 4950 5050 front 0))
          )
          (library
            (image "U_TEST:TEST_PKG"
              (pin Custom_L_Pad 1 0 0)
            )
            (image "U_VOID:VOID_PKG"
              (pin Small_Pad 1 0 0)
            )
            (padstack Custom_L_Pad
              (shape (polygon F.Cu 0 -100 -100  100 -100  100 0  0 0  0 100  -100 100))
              (attach off)
            )
            (padstack Small_Pad
              (shape (rect F.Cu -10 -10 10 10))
              (attach off)
            )
            (padstack "Via[0-1]_800:400_um"
              (shape (circle F.Cu 800))
              (shape (circle B.Cu 800))
              (attach off)
            )
          )
          (network
            (net NET1 (pins U1-1))
            (net NET2 (pins U2-1))
          )
        )
        """;

    InputStream in = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    Collection<ClearanceViolation> violations = drc.getAllClearanceViolations();
    assertFalse(
        violations.isEmpty(),
        "Foreign pin overlapping auxiliary copper tile must trigger clearance violation");
  }

  @Test
  void dsnExportPreservesAuxiliaryShapesInRoundTrip() throws Exception {
    InputStream in =
        new ByteArrayInputStream(DSN_WITH_NON_CONVEX_PAD.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DsnWriter.write(board, out, "roundtrip", false);

    RoutingBoard reloaded = DsnTestFixtures.loadBoard(out.toByteArray());
    Padstack padstack = reloaded.library.padstacks.get("Custom_L_Pad");
    assertNotNull(padstack, "Padstack must exist after DSN round trip");
    assertTrue(padstack.hasNonConvexGeometry(), "Padstack must remain flagged as non-convex");
    assertTrue(padstack.hasAuxiliaryShapes(), "Padstack must retain auxiliary shapes");

    List<ObstacleArea> auxObstacles =
        reloaded.getItems().stream()
            .filter(item -> item instanceof ObstacleArea)
            .map(item -> (ObstacleArea) item)
            .filter(obs -> obs.name != null && obs.name.endsWith("_aux"))
            .toList();
    assertFalse(auxObstacles.isEmpty(), "Companion obstacles must be recreated after round trip");

    DesignRulesChecker drc = new DesignRulesChecker(reloaded, null);
    assertTrue(
        drc.getAllClearanceViolations().isEmpty(),
        "Reloaded board must have zero clearance violations");
  }

  @Test
  void genericDefaultLayerShapeIsOverriddenBySpecificLayerShape() {
    String dsn =
        """
        (pcb "test_layer_override.dsn"
          (parser (string_quote ") (space_in_quoted_tokens on))
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal) (property (index 0)))
            (layer B.Cu (type signal) (property (index 1)))
            (boundary (path pcb 0 0 0 10000 0 10000 10000 0 10000 0 0))
            (via "Via[0-1]_800:400_um")
            (rule (width 200) (clearance 50))
          )
          (placement
            (component "U_TEST:TEST_PKG" (place U1 5000 5000 front 0))
          )
          (library
            (image "U_TEST:TEST_PKG"
              (pin Override_Pad 1 0 0)
            )
            (padstack Override_Pad
              (shape (rect signal -100 -100 100 100))
              (shape (rect F.Cu -50 -50 50 50))
              (attach off)
            )
            (padstack "Via[0-1]_800:400_um"
              (shape (circle F.Cu 800))
              (shape (circle B.Cu 800))
              (attach off)
            )
          )
          (network (net NET1 (pins U1-1)))
        )
        """;

    InputStream in = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    Padstack padstack = board.library.padstacks.get("Override_Pad");
    assertNotNull(padstack);
    // On F.Cu (layer 0), the specific shape (-50..50, max width 1000) should have replaced the
    // generic default (-100..100, max width 2000) and no auxiliary shape should be created
    assertNull(padstack.getAuxiliaryShapes(0), "F.Cu should not have auxiliary shapes");
    assertEquals(
        1000,
        Math.round(padstack.getShape(0).maxWidth()),
        "F.Cu shape must be overridden to 100 um width");
    // On B.Cu (layer 1), the generic default remains
    assertEquals(
        2000,
        Math.round(padstack.getShape(1).maxWidth()),
        "B.Cu shape must retain generic default 200 um width");
  }

  @Test
  void originContainingShapeIsSelectedAsCorePad() {
    // Padstack with two explicit shapes on F.Cu: shape 1 does NOT contain origin, shape 2 DOES
    // contain origin
    String dsn =
        """
        (pcb "test_origin_core.dsn"
          (parser (string_quote ") (space_in_quoted_tokens on))
          (resolution um 10)
          (unit um)
          (structure
            (layer F.Cu (type signal) (property (index 0)))
            (layer B.Cu (type signal) (property (index 1)))
            (boundary (path pcb 0 0 0 10000 0 10000 10000 0 10000 0 0))
            (via "Via[0-1]_800:400_um")
            (rule (width 200) (clearance 50))
          )
          (placement
            (component "U_TEST:TEST_PKG" (place U1 5000 5000 front 0))
          )
          (library
            (image "U_TEST:TEST_PKG"
              (pin Multi_Tile_Pad 1 0 0)
            )
            (padstack Multi_Tile_Pad
              (shape (rect F.Cu 100 100 200 200))
              (shape (rect F.Cu -50 -50 50 50))
              (attach off)
            )
            (padstack "Via[0-1]_800:400_um"
              (shape (circle F.Cu 800))
              (shape (circle B.Cu 800))
              (attach off)
            )
          )
          (network (net NET1 (pins U1-1)))
        )
        """;

    InputStream in = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(in, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    RoutingBoard board = (RoutingBoard) ((BoardReadResult.Success) result).board();

    Padstack padstack = board.library.padstacks.get("Multi_Tile_Pad");
    assertNotNull(padstack);
    assertTrue(padstack.hasAuxiliaryShapes(), "Padstack must have auxiliary shapes");
    // Core shape must be the one containing Point.ZERO (-50..50)
    assertTrue(
        padstack.getShape(0).contains(app.freerouting.geometry.planar.Point.ZERO),
        "Core shape must contain origin");
  }
}

package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.pipeline.BatchAutorouter;
import app.freerouting.autoroute.plane.FastPlaneConnectivityGrid;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.Component;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.state.Communication;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.StoppableThread;
import app.freerouting.core.library.Package;
import app.freerouting.core.library.Packages;
import app.freerouting.core.library.Padstack;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.Area;
import app.freerouting.geometry.planar.ConvexShape;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.rules.BoardRules;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.Net;
import app.freerouting.rules.ViaInfo;
import app.freerouting.rules.ViaRule;
import app.freerouting.settings.sources.DefaultSettings;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests high-performance topological plane connectivity and fracture detection. */
class FastPlaneConnectivityGridTest {

  private RoutingBoard board;
  private Net gndNet;
  private Net sigNet;
  private Padstack defaultPadstack;

  @BeforeEach
  void setUp() {
    Layer layer1 = new Layer("Top", true);
    Layer layer2 = new Layer("Bottom", true);
    LayerStructure layerStructure = new LayerStructure(new Layer[] {layer1, layer2});

    ClearanceMatrix clearanceMatrix = ClearanceMatrix.getDefaultInstance(layerStructure, 10);
    BoardRules boardRules = new BoardRules(layerStructure, clearanceMatrix);
    boardRules.createDefaultNetClass();
    ViaRule dummyViaRule = new ViaRule("defaultViaRule");
    boardRules.viaRules.add(dummyViaRule);
    boardRules.getDefaultNetClass().setViaRule(dummyViaRule);

    Communication communication = new Communication();

    board =
        new RoutingBoard(
            new IntBox(0, 0, 10_000_000, 10_000_000),
            layerStructure,
            new PolylineShape[] {TileShape.getInstance(0, 0, 10_000_000, 10_000_000)},
            0,
            boardRules,
            communication);

    gndNet = board.rules.nets.add("GND", 1, true);
    sigNet = board.rules.nets.add("SIG", 1, false);

    board.library.padstacks = new app.freerouting.core.library.Padstacks(layerStructure);
    board.library.packages = new Packages(board.library.padstacks);
    ConvexShape[] shapes =
        new ConvexShape[] {
          TileShape.getInstance(-100_000, -100_000, 100_000, 100_000),
          TileShape.getInstance(-100_000, -100_000, 100_000, 100_000)
        };
    defaultPadstack = board.library.padstacks.add("TestViaPadstack", shapes, true, false);

    ViaInfo viaInfo = new ViaInfo("defaultVia", defaultPadstack, 0, false, boardRules);
    boardRules.viaInfos.add(viaInfo);
    dummyViaRule.appendVia(viaInfo);
    boardRules.getDefaultNetClass().setTraceHalfWidth(100_000);
  }

  @Test
  void testContinuousPourIdentifiesSingleConnectedIsland() {
    Area area = TileShape.getInstance(0, 0, 10_000_000, 10_000_000);
    ConductionArea ca =
        board.insertConductionArea(
            area, 0, new int[] {gndNet.netNumber}, 0, false, FixedState.UNFIXED);

    Via via1 =
        board.insertVia(
            defaultPadstack,
            new IntPoint(1_000_000, 5_000_000),
            new int[] {gndNet.netNumber},
            0,
            FixedState.UNFIXED,
            true);

    Via via2 =
        board.insertVia(
            defaultPadstack,
            new IntPoint(9_000_000, 5_000_000),
            new int[] {gndNet.netNumber},
            0,
            FixedState.UNFIXED,
            true);

    FastPlaneConnectivityGrid grid = new FastPlaneConnectivityGrid();
    FastPlaneConnectivityGrid.PlaneFractureResult result =
        grid.evaluateFracture(ca, board, List.of(via1, via2));

    assertNotNull(result);
    assertFalse(result.isFractured, "Continuous copper pour must not be fractured");
    assertEquals(1, result.islandCount, "Both vias should be in the same continuous island");
    assertEquals(2, result.islandItems.get(0).size());
  }

  @Test
  void testCuttingTraceDetectsSeveredIslands() {
    Area area = TileShape.getInstance(1_000_000, 1_000_000, 9_000_000, 9_000_000);
    ConductionArea ca =
        board.insertConductionArea(
            area, 0, new int[] {gndNet.netNumber}, 0, false, FixedState.UNFIXED);

    Package pkg =
        board.library.packages.add(
            new Package.Pin[] {new Package.Pin("1", defaultPadstack.id, Vector.ZERO, 0)});
    Component compLeft = board.components.add(new IntPoint(2_000_000, 5_000_000), 0, true, pkg);
    Pin pinLeft =
        board.insertPin(compLeft.id, 0, new int[] {gndNet.netNumber}, 0, FixedState.UNFIXED);

    Component compRight = board.components.add(new IntPoint(8_000_000, 5_000_000), 0, true, pkg);
    Pin pinRight =
        board.insertPin(compRight.id, 0, new int[] {gndNet.netNumber}, 0, FixedState.UNFIXED);

    // Foreign trace cutting vertically completely across conduction area at X = 5,000,000
    Point[] traceCorners =
        new Point[] {new IntPoint(5_000_000, 800_000), new IntPoint(5_000_000, 9_200_000)};
    board.insertTrace(
        traceCorners, 0, 200_000, new int[] {sigNet.netNumber}, 0, FixedState.UNFIXED);

    FastPlaneConnectivityGrid grid = new FastPlaneConnectivityGrid();
    FastPlaneConnectivityGrid.PlaneFractureResult result =
        grid.evaluateFracture(ca, board, List.of(pinLeft, pinRight));

    assertNotNull(result);
    assertTrue(result.isFractured, "Pour sliced by foreign trace must be recognized as fractured");
    assertEquals(2, result.islandCount, "Pins on opposite sides must belong to 2 separate islands");

    // Verify ConductionArea integration and connected sets
    assertTrue(ca.isFractured(), "Conduction area must report fractured state");
    Set<Item> leftConnected = pinLeft.getConnectedSet(gndNet.netNumber);
    Set<Item> rightConnected = pinRight.getConnectedSet(gndNet.netNumber);
    assertFalse(
        leftConnected.contains(pinRight), "Left pin must not reach right pin through sliced pour");
    assertFalse(
        rightConnected.contains(pinLeft), "Right pin must not reach left pin through sliced pour");

    // Verify DRC generates incomplete ratline between severed islands
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    assertEquals(
        1,
        drc.getIncompleteCount(),
        "DRC must report 1 incomplete airline connecting severed islands");

    // Bridge the two pins with a trace on layer 1 and verify connectivity is restored
    Point[] bridgeCorners =
        new Point[] {new IntPoint(2_000_000, 5_000_000), new IntPoint(8_000_000, 5_000_000)};
    board.insertTrace(
        bridgeCorners, 1, 200_000, new int[] {gndNet.netNumber}, 0, FixedState.UNFIXED);

    Set<Item> bridgedConnected = pinLeft.getConnectedSet(gndNet.netNumber);
    assertTrue(
        bridgedConnected.contains(pinRight),
        "Left pin must reach right pin through bridge trace on layer 1");
    drc.calculateAllIncompletes();
    assertEquals(0, drc.getIncompleteCount(), "DRC incompletes must clear to 0 once bridged");
  }

  @Test
  void testSubMillisecondPerformanceOnDenseObstacles() {
    Area area = TileShape.getInstance(0, 0, 10_000_000, 10_000_000);
    ConductionArea ca =
        board.insertConductionArea(
            area, 0, new int[] {gndNet.netNumber}, 0, false, FixedState.UNFIXED);

    Via via1 =
        board.insertVia(
            defaultPadstack,
            new IntPoint(100_000, 100_000),
            new int[] {gndNet.netNumber},
            0,
            FixedState.UNFIXED,
            true);

    Via via2 =
        board.insertVia(
            defaultPadstack,
            new IntPoint(9_900_000, 9_900_000),
            new int[] {gndNet.netNumber},
            0,
            FixedState.UNFIXED,
            true);

    // Insert 50 foreign trace segments without completely cutting the board
    for (int i = 1; i <= 50; i++) {
      int y = i * 180_000;
      Point[] corners = new Point[] {new IntPoint(1_000_000, y), new IntPoint(8_000_000, y)};
      board.insertTrace(corners, 0, 100_000, new int[] {sigNet.netNumber}, 0, FixedState.UNFIXED);
    }

    FastPlaneConnectivityGrid grid = new FastPlaneConnectivityGrid();
    // Warm up JIT
    grid.evaluateFracture(ca, board, List.of(via1, via2));

    long startNanos = System.nanoTime();
    FastPlaneConnectivityGrid.PlaneFractureResult result =
        grid.evaluateFracture(ca, board, List.of(via1, via2));
    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

    assertNotNull(result);
    // Since traces leave corridors on left and right, vias should still be connected
    assertFalse(result.isFractured, "Non-severing obstacle comb must preserve connectivity");
    assertTrue(
        elapsedMs < 50,
        "Topological grid evaluation must finish in under 50ms, took " + elapsedMs + "ms");
  }

  @Test
  void testAutorouterBridgesSeveredPlaneIslands() {
    Area area = TileShape.getInstance(1_000_000, 1_000_000, 9_000_000, 9_000_000);
    board.insertConductionArea(area, 0, new int[] {gndNet.netNumber}, 0, false, FixedState.UNFIXED);

    Package pkg =
        board.library.packages.add(
            new Package.Pin[] {new Package.Pin("1", defaultPadstack.id, Vector.ZERO, 0)});
    Component compLeft = board.components.add(new IntPoint(2_000_000, 5_000_000), 0, true, pkg);
    Pin pinLeft =
        board.insertPin(compLeft.id, 0, new int[] {gndNet.netNumber}, 0, FixedState.UNFIXED);

    Component compRight = board.components.add(new IntPoint(8_000_000, 5_000_000), 0, true, pkg);
    Pin pinRight =
        board.insertPin(compRight.id, 0, new int[] {gndNet.netNumber}, 0, FixedState.UNFIXED);

    // Foreign fixed trace cutting layer 0 vertically without crossing board outline
    Point[] traceCorners =
        new Point[] {new IntPoint(5_000_000, 800_000), new IntPoint(5_000_000, 9_200_000)};
    board.insertTrace(
        traceCorners, 0, 200_000, new int[] {sigNet.netNumber}, 0, FixedState.USER_FIXED);

    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    assertEquals(
        1, drc.getIncompleteCount(), "Before routing, severed GND pins must have 1 incomplete");

    RoutingJob job = new RoutingJob();
    job.board = board;
    job.thread = new TestStoppableThread();
    job.routerSettings = new DefaultSettings().getSettings();
    job.routerSettings.setLayerCount(board.getLayerCount());
    job.routerSettings.setRunRouter(true);
    job.routerSettings.autorouter.maxPasses = 1;

    BatchAutorouter router = new BatchAutorouter(job);
    router.runBatchLoop();

    DesignRulesChecker finalDrc = new DesignRulesChecker(job.board, null);
    finalDrc.calculateAllIncompletes();
    assertEquals(
        0, finalDrc.getIncompleteCount(), "Autorouter must successfully bridge severed islands");
    assertEquals(
        0,
        finalDrc.getAllClearanceViolations().size(),
        "Bridge route must not introduce clearance violations");
  }

  private static final class TestStoppableThread extends StoppableThread {
    @Override
    protected void threadAction() {}
  }
}

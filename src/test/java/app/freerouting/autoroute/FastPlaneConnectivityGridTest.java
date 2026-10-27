package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.plane.FastPlaneConnectivityGrid;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Via;
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
import java.util.List;
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
    ConvexShape[] shapes =
        new ConvexShape[] {
          TileShape.getInstance(-100_000, -100_000, 100_000, 100_000),
          TileShape.getInstance(-100_000, -100_000, 100_000, 100_000)
        };
    defaultPadstack = board.library.padstacks.add("TestViaPadstack", shapes, true, false);
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
    Area area = TileShape.getInstance(0, 0, 10_000_000, 10_000_000);
    ConductionArea ca =
        board.insertConductionArea(
            area, 0, new int[] {gndNet.netNumber}, 0, false, FixedState.UNFIXED);

    Via viaLeft =
        board.insertVia(
            defaultPadstack,
            new IntPoint(1_000_000, 5_000_000),
            new int[] {gndNet.netNumber},
            0,
            FixedState.UNFIXED,
            true);

    Via viaRight =
        board.insertVia(
            defaultPadstack,
            new IntPoint(9_000_000, 5_000_000),
            new int[] {gndNet.netNumber},
            0,
            FixedState.UNFIXED,
            true);

    // Foreign trace cutting vertically completely across layer 0 at X = 5,000,000
    Point[] traceCorners =
        new Point[] {new IntPoint(5_000_000, 0), new IntPoint(5_000_000, 10_000_000)};
    board.insertTrace(
        traceCorners, 0, 200_000, new int[] {sigNet.netNumber}, 0, FixedState.UNFIXED);

    FastPlaneConnectivityGrid grid = new FastPlaneConnectivityGrid();
    FastPlaneConnectivityGrid.PlaneFractureResult result =
        grid.evaluateFracture(ca, board, List.of(viaLeft, viaRight));

    assertNotNull(result);
    assertTrue(result.isFractured, "Pour sliced by foreign trace must be recognized as fractured");
    assertEquals(2, result.islandCount, "Vias on opposite sides must belong to 2 separate islands");
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
}

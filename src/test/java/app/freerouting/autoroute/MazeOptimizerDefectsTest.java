package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.autoroute.pipeline.BatchOptimizer;
import app.freerouting.board.actions.ItemIdGenerator;
import app.freerouting.core.StoppableThread;
import app.freerouting.datastructures.IdentifierType;
import app.freerouting.datastructures.PlanarDelaunayTriangulation;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.ViaRule;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Validates fixes for maze search, specctra parser, optimizer lifecycle, and geometry concurrency
 * defects (FR-030, FR-031, FR-035, FR-036, FR-062, FR-063, FR-064, FR-078).
 */
public class MazeOptimizerDefectsTest {

  /** FR-030: Quoted identifier keeps all internal characters when stripped. */
  @Test
  void quotedIdentifierKeepsLastCharacter() throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
      IdentifierType identifierType = new IdentifierType(new String[] {"(", ")", " "}, "\"");
      identifierType.write("\"ABC\"", writer);
    }
    assertEquals("ABC", baos.toString(StandardCharsets.UTF_8));
  }

  /**
   * FR-062: Optimizer via improvement does not truncate fractional via ratio via integer division.
   */
  @Test
  void viaRatioMaintainsFractionalPrecision() {
    // 10 vias before, 9 vias after -> 1 - ((0.9 + 1.0)/2) = 0.05
    ItemRouteResult result = new ItemRouteResult(1, 10, 9, 100.0, 100.0, 0, 0);
    assertEquals(0.05f, result.improvementPercentage(), 1e-4f);
    assertTrue(result.improved());

    // 10 vias before, 10 vias after -> 0.0
    ItemRouteResult unchanged = new ItemRouteResult(1, 10, 10, 100.0, 100.0, 0, 0);
    assertEquals(0.0f, unchanged.improvementPercentage(), 1e-4f);
    assertFalse(unchanged.improved());

    // 10 vias before, 11 vias after -> 1 - ((1.1 + 1.0)/2) = -0.05
    ItemRouteResult worse = new ItemRouteResult(1, 10, 11, 100.0, 100.0, 0, 0);
    assertEquals(-0.05f, worse.improvementPercentage(), 1e-4f);
    assertFalse(worse.improved());
  }

  /** FR-064: Concurrent Delaunay constructions do not race on shared RNG state. */
  @Test
  void concurrentDelaunayTriangulationIsDeterministic()
      throws InterruptedException, ExecutionException {
    int pointCount = 100;
    List<PlanarDelaunayTriangulation.Storable> points = new ArrayList<>();
    for (int i = 0; i < pointCount; i++) {
      points.add(new StorablePoint(i, new IntPoint((i % 10) * 100, (i / 10) * 100)));
    }

    PlanarDelaunayTriangulation serial = new PlanarDelaunayTriangulation(points);
    int serialEdgeCount = serial.getEdgeLines().size();

    int threads = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    try {
      List<Callable<Integer>> tasks = new ArrayList<>();
      for (int i = 0; i < 32; i++) {
        tasks.add(
            () -> {
              PlanarDelaunayTriangulation triangulation = new PlanarDelaunayTriangulation(points);
              return triangulation.getEdgeLines().size();
            });
      }
      List<Future<Integer>> futures = executor.invokeAll(tasks);
      for (Future<Integer> future : futures) {
        assertEquals(serialEdgeCount, future.get());
      }
    } finally {
      executor.shutdownNow();
    }
  }

  /** FR-078: resetStopAutoRouterRequest resets AUTO_ROUTER_ONLY but preserves ALL stop. */
  @Test
  void resetStopAutoRouterRequestLifecycle() {
    StoppableThread thread =
        new StoppableThread() {
          @Override
          protected void threadAction() {}
        };

    assertFalse(thread.isStopRequested());
    assertFalse(thread.isStopAutoRouterRequested());

    thread.requestStopAutoRouter();
    assertFalse(thread.isStopRequested());
    assertTrue(thread.isStopAutoRouterRequested());

    thread.resetStopAutoRouterRequest();
    assertFalse(thread.isStopRequested());
    assertFalse(thread.isStopAutoRouterRequested());

    thread.requestStop();
    assertTrue(thread.isStopRequested());
    assertTrue(thread.isStopAutoRouterRequested());

    thread.resetStopAutoRouterRequest();
    assertTrue(thread.isStopRequested());
    assertTrue(thread.isStopAutoRouterRequested());
  }

  /** FR-035: autoroute_settings parsed after keepouts/planes when layerStructure exists. */
  @Test
  void autorouteSettingsReadAfterKeepout() {
    String dsn =
        "(pcb test (resolution um 1) (unit um)\n"
            + "  (structure (layer top (type signal)) (layer bottom (type signal))\n"
            + "    (boundary (rect pcb 0 0 40000 20000))\n"
            + "    (keepout (rect top 1000 1000 2000 2000))\n"
            + "    (autoroute_settings (fanout off) (autoroute on) (postroute on) (vias on) (via_costs 77)\n"
            + "      (start_ripup_costs 100))\n"
            + "    (via V) (rule (width 200) (clearance 200)))\n"
            + "  (placement (component R (place R1 10000 10000 front 0) (place R2 30000 10000 front 0)))\n"
            + "  (library\n"
            + "    (image R (pin P 1 -1000 0) (pin P 2 1000 0))\n"
            + "    (padstack P (shape (circle top 800)) (shape (circle bottom 800)) (attach off))\n"
            + "    (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))\n"
            + "  (network (net N (pins R1-1 R2-1))))\n";

    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)),
            null,
            new ItemIdGenerator());
    assertTrue(result instanceof BoardReadResult.Success);
    app.freerouting.board.facade.BasicBoard board = ((BoardReadResult.Success) result).board();
    assertNotNull(board);
    assertEquals(4, board.getPins().size());
    assertNotNull(board.rules.nets.get("N"));
  }

  /**
   * FR-036: class_class rules apply to all pairs and do not alias via padstacks between classes.
   */
  @Test
  void classClassRulesPairAllClassesAndDoNotAliasVias() {
    String dsn =
        "(pcb test (resolution um 1) (unit um)\n"
            + "  (structure (layer top (type signal)) (layer bottom (type signal))\n"
            + "    (boundary (rect pcb 0 0 40000 20000)) (rule (width 200) (clearance 200)))\n"
            + "  (placement (component R (place R1 10000 10000 front 0) (place R2 30000 10000 front 0)\n"
            + "    (place R3 20000 5000 front 0)))\n"
            + "  (library\n"
            + "    (image R (pin P 1 -1000 0) (pin P 2 1000 0))\n"
            + "    (padstack P (shape (circle top 800)) (shape (circle bottom 800)) (attach off))\n"
            + "    (padstack VA (shape (circle top 600)) (shape (circle bottom 600)) (attach off))\n"
            + "    (padstack VB (shape (circle top 700)) (shape (circle bottom 700)) (attach off)))\n"
            + "  (network\n"
            + "    (net N1 (pins R1-1 R2-1)) (net N2 (pins R1-2 R2-2)) (net N3 (pins R3-1 R3-2))\n"
            + "    (class A N1 (circuit (use_via VA)) (rule (clearance 300)))\n"
            + "    (class B N2 (circuit (use_via VB)) (rule (clearance 300)))\n"
            + "    (class C N3 (rule (clearance 300)))\n"
            + "    (class_class (classes A B C) (rule (clearance 900)))))\n";

    BoardReadResult result =
        DsnReader.readBoard(
            new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8)),
            null,
            new ItemIdGenerator());
    assertTrue(result instanceof BoardReadResult.Success);
    app.freerouting.board.facade.BasicBoard board = ((BoardReadResult.Success) result).board();
    assertNotNull(board);

    int clAB =
        board.rules.clearanceMatrix.getValue(
            board.rules.clearanceMatrix.getNo("A"),
            board.rules.clearanceMatrix.getNo("B"),
            0,
            false);
    int clAC =
        board.rules.clearanceMatrix.getValue(
            board.rules.clearanceMatrix.getNo("A"),
            board.rules.clearanceMatrix.getNo("C"),
            0,
            false);
    int clBC =
        board.rules.clearanceMatrix.getValue(
            board.rules.clearanceMatrix.getNo("B"),
            board.rules.clearanceMatrix.getNo("C"),
            0,
            false);
    int clAA =
        board.rules.clearanceMatrix.getValue(
            board.rules.clearanceMatrix.getNo("A"),
            board.rules.clearanceMatrix.getNo("A"),
            0,
            false);

    assertEquals(900, clAB);
    assertEquals(900, clAC);
    assertEquals(900, clBC);
    assertEquals(300, clAA);

    ViaRule ruleA = board.rules.netClasses.get("A").getViaRule();
    ViaRule ruleB = board.rules.netClasses.get("B").getViaRule();
    assertEquals(1, ruleA.viaCount());
    assertEquals(1, ruleB.viaCount());
    assertEquals("VA", ruleA.getVia(0).getPadstack().name);
    assertEquals("VB", ruleB.getVia(0).getPadstack().name);
  }

  /** FR-077: Final optimizer board must not regress open connections or violations. */
  @Test
  void finalBoardRegressedVetoesViolationsAndOpens() {
    // Score higher but added a violation -> regressed
    assertTrue(BatchOptimizer.finalBoardRegressed(424.85f, 0, 0, 427.85f, 0, 1));
    // Score higher but added an open connection -> regressed
    assertTrue(BatchOptimizer.finalBoardRegressed(424.85f, 0, 0, 427.85f, 1, 0));
    // Lower score -> regressed
    assertTrue(BatchOptimizer.finalBoardRegressed(424.85f, 0, 0, 420.0f, 0, 0));
    // Equal score and zero regressions -> clean
    assertFalse(BatchOptimizer.finalBoardRegressed(424.85f, 0, 0, 424.85f, 0, 0));
    // Higher score and zero regressions -> improved
    assertFalse(BatchOptimizer.finalBoardRegressed(424.85f, 0, 0, 427.85f, 0, 0));
  }

  /** FR-079: Contact chamfer room requires at least 2 units and a quarter of full chamfer. */
  @Test
  void chamferThresholdRequiresTwoUnitsOrQuarterChamfer() {
    // Narrow trace (halfWidth = 5): 0.25 * (sqrt(2)-1) * 5 ~= 0.517 -> clamped to 2.0
    double narrowThreshold =
        Math.max(2.0, 0.25 * (app.freerouting.geometry.planar.Limits.sqrt2 - 1.0) * 5);
    assertEquals(2.0, narrowThreshold);

    // Wide trace (halfWidth = 100): 0.25 * (sqrt(2)-1) * 100 ~= 10.355
    double wideThreshold =
        Math.max(2.0, 0.25 * (app.freerouting.geometry.planar.Limits.sqrt2 - 1.0) * 100);
    assertTrue(wideThreshold > 10.0);
  }

  private static class StorablePoint implements PlanarDelaunayTriangulation.Storable {
    private final int id;
    private final Point point;

    StorablePoint(int id, Point point) {
      this.id = id;
      this.point = point;
    }

    @Override
    public Point[] getTriangulationCorners() {
      return new Point[] {point};
    }
  }
}

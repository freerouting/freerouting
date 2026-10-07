package app.freerouting.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.actions.DrillItemMover;
import app.freerouting.board.actions.ForcedPadRouter;
import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.ComponentOutline;
import app.freerouting.board.model.items.ConductionArea;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.BoardOutline;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.model.structure.Layer;
import app.freerouting.board.model.structure.LayerStructure;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.core.library.Padstack;
import app.freerouting.drc.ClearanceBaseline;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.geometry.planar.FloatPoint;
import app.freerouting.geometry.planar.IntBox;
import app.freerouting.geometry.planar.IntOctagon;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.IntVector;
import app.freerouting.geometry.planar.Line;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.PolygonShape;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.geometry.planar.PolylineShape;
import app.freerouting.geometry.planar.TileShape;
import app.freerouting.geometry.planar.Vector;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import app.freerouting.rules.ClearanceMatrix;
import app.freerouting.rules.NetClass;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests covering Board Model, Connectivity, and DRC Integrity defect fixes (FR-016..FR-102). */
public class BoardModelDrcDefectsTest {

  private RoutingBoard createBoardFromDsn(String dsn) throws IOException {
    ByteArrayInputStream is = new ByteArrayInputStream(dsn.getBytes(StandardCharsets.UTF_8));
    BoardReadResult res = DsnReader.readBoard(is, null, null);
    if (res instanceof BoardReadResult.Success success) {
      return (RoutingBoard) success.board();
    }
    throw new IOException("Failed to parse board DSN: " + res);
  }

  @Test
  void testBasicBoardInsertViaSplitsTracesOnToLayer_FR017() throws IOException {
    String dsn =
        """
        (pcb test (resolution um 1) (unit um)
          (structure
            (layer top (type signal))
            (layer inner (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 40000 40000))
            (via V)
            (rule (width 200) (clearance 200)))
          (placement (component R (place R1 10000 10000 front 0)))
          (library
            (image R (pin P 1 0 0))
            (padstack P (shape (circle top 800)) (shape (circle bottom 800)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle inner 600)) (shape (circle bottom 600)) (attach off)))
          (network (net N (pins R1-1))))
        """;
    RoutingBoard board = createBoardFromDsn(dsn);
    Point p1 = Point.getInstance(5000, 20000);
    Point p2 = Point.getInstance(25000, 20000);
    // Insert trace on the bottom layer (layer index 2)
    board.insertTrace(
        new Polyline(new Point[] {p1, p2}), 2, 100, new int[] {1}, 0, FixedState.UNFIXED);
    assertEquals(1, board.getTraces().size());

    // Insert a via through top (0) to bottom (2) at the middle of the trace
    Padstack viaPadstack = board.library.padstacks.get(2); // V
    Point viaCenter = Point.getInstance(15000, 20000);
    board.insertVia(viaPadstack, viaCenter, new int[] {1}, 0, FixedState.UNFIXED, true);

    // The trace on bottom layer must be split into two traces at viaCenter
    assertEquals(2, board.getTraces().size());
  }

  @Test
  void testForcedPadRouterDiagonalCalculation_FR018() throws IOException {
    String dsn =
        """
        (pcb test (resolution um 1) (unit um)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 20000 20000))
            (via V)
            (rule (width 200) (clearance 200)))
          (placement (component R (place R1 10000 10000 front 0)))
          (library
            (image R (pin P 1 0 0))
            (padstack P (shape (circle top 800)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
          (network (net N (pins R1-1))))
        """;
    RoutingBoard board = createBoardFromDsn(dsn);
    ForcedPadRouter router = new ForcedPadRouter(board);
    TileShape padShape = new IntOctagon(10000, 10000, 10000, 10000, 20000, 20000, 0, 0);
    Point lineA = Point.getInstance(15000, 15000);
    Point lineB = Point.getInstance(16000, 14000);
    Line line = new Line(lineA, lineB);
    boolean result = ForcedPadRouter.inFrontOfPad(line, padShape, 0, 100, true);
    assertTrue(result);
  }

  @Test
  void testBoardOutlineTransformsUpdateShapes_FR019() {
    Point[] corners =
        new Point[] {
          Point.getInstance(0, 0),
          Point.getInstance(20000, 0),
          Point.getInstance(20000, 10000),
          Point.getInstance(0, 10000)
        };
    PolygonShape shape = new PolygonShape(corners);
    BoardOutline outline = new BoardOutline(new PolylineShape[] {shape}, 0, 1, null);

    outline.translateBy(new IntVector(100, 200));
    PolylineShape translated = outline.getShape(0);
    IntBox box = translated.boundingBox();
    assertEquals(100, (int) box.ll.x);
    assertEquals(200, (int) box.ll.y);

    outline.turn90Degree(1, IntPoint.ZERO);
    PolylineShape turned = outline.getShape(0);
    assertNotNull(turned);
  }

  @Test
  void testDrillItemMoverReturnsTidyRegion_FR020() throws IOException {
    String dsn =
        """
        (pcb test (resolution um 1) (unit um)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 40000 40000))
            (via V)
            (rule (width 200) (clearance 200)))
          (placement (component R (place R1 10000 10000 front 0)))
          (library
            (image R (pin P 1 0 0))
            (padstack P (shape (circle top 800)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
          (network (net N (pins R1-1))))
        """;
    RoutingBoard board = createBoardFromDsn(dsn);
    Padstack viaPadstack = board.library.padstacks.get(2);
    Via via =
        board.insertVia(
            viaPadstack,
            Point.getInstance(10000, 5000),
            new int[] {1},
            0,
            FixedState.UNFIXED,
            true);

    IntOctagon[] tidyHolder = new IntOctagon[] {IntOctagon.EMPTY};
    Vector delta = new IntVector(1000, 0);
    boolean ok = DrillItemMover.insertJoiningTidyRegion(via, delta, 1, 1, tidyHolder, board);
    assertTrue(ok);
    assertNotNull(tidyHolder[0]);
    assertFalse(tidyHolder[0].isEmpty());
    assertTrue(tidyHolder[0].contains(new FloatPoint(11000, 5000)));
  }

  @Test
  void testComponentOutlineClearDerivedData_FR021() {
    ComponentOutline outline =
        new ComponentOutline(
            new IntBox(0, 0, 1000, 1000),
            true,
            new IntVector(5000, 5000),
            0,
            1,
            1,
            true,
            false,
            true,
            FixedState.USER_FIXED,
            null);
    outline.translateBy(new IntVector(100, 0));
    outline.clearDerivedData();
    assertNotNull(outline);
  }

  @Test
  void testConductionAreaCopyWithMultipleNets_FR022() {
    ConductionArea area =
        new ConductionArea(
            new IntBox(1000, 1000, 3000, 3000),
            0,
            new IntVector(0, 0),
            0,
            false,
            new int[] {1, 2},
            1,
            1,
            0,
            "plane",
            true,
            FixedState.SYSTEM_FIXED,
            null);
    Item copy = area.copy(99);
    assertNotNull(copy);
    assertTrue(copy instanceof ConductionArea);
    assertEquals(2, copy.netCount());
    assertEquals(1, copy.getNetNumber(0));
    assertEquals(2, copy.getNetNumber(1));
  }

  @Test
  void testClearanceMatrixRemoveClassRecomputesMaxima_FR027() {
    LayerStructure layers =
        new LayerStructure(new Layer[] {new Layer("top", true), new Layer("bottom", true)});
    ClearanceMatrix matrix = ClearanceMatrix.getDefaultInstance(layers, 100);
    assertTrue(matrix.appendClass("wide"));
    int wide = matrix.getNo("wide");
    matrix.setValue(wide, wide, 1000);
    matrix.setValue(1, wide, 500);
    matrix.setValue(wide, 1, 500);

    assertEquals(1000, matrix.maxValue(0));
    assertEquals(500, matrix.maxValue(1, 0));

    matrix.removeClass(wide);
    assertEquals(100, matrix.maxValue(0));
    assertEquals(100, matrix.maxValue(1, 0));
  }

  @Test
  void testNetClassTraceWidthIsInnerLayerDependentNoSignalInner_FR028() {
    LayerStructure layers =
        new LayerStructure(
            new Layer[] {
              new Layer("L0", true),
              new Layer("L1", false),
              new Layer("L2", false),
              new Layer("L3", false)
            });
    NetClass netClass =
        new NetClass("test", layers, ClearanceMatrix.getDefaultInstance(layers, 100), false);
    // Must not throw IndexOutOfBoundsException and return false
    assertFalse(netClass.traceWidthIsInnerLayerDependent());
  }

  @Test
  void testPinOverlappingContactsConnected_FR051() throws IOException {
    String dsn =
        """
        (pcb slug (resolution um 1) (unit um)
          (structure (layer top (type signal)) (layer bottom (type signal))
            (boundary (rect pcb 0 0 20000 20000)) (via V) (rule (width 200) (clearance 200)))
          (placement (component P (place U1 10000 10000 front 0)))
          (library
            (image P (pin T 1@1 0 0) (pin B 1@2 0 0) (pin H 1@3 600 0) (pin T 2 0 4000))
            (padstack T (shape (rect top -1000 -500 1000 500)) (attach off))
            (padstack B (shape (rect bottom -1000 -500 1000 500)) (attach off))
            (padstack H (shape (circle top 500)) (shape (circle bottom 500)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
          (network (net G (pins U1-1@1 U1-1@2 U1-1@3)) (net S (pins U1-2))))
        """;
    RoutingBoard board = createBoardFromDsn(dsn);
    Pin pin1 = null;
    for (Pin p : board.getPins()) {
      if ("1@1".equals(p.name())) {
        pin1 = p;
        break;
      }
    }
    assertNotNull(pin1);
    Set<Item> connectedSet = pin1.getConnectedSet(pin1.getNetNumber(0));
    // The top, bottom, and overlapping hole pins are all connected together
    assertEquals(3, connectedSet.size());
  }

  @Test
  void testConnectNetlessPadsToTheirFootprintsNet_FR102() throws IOException {
    String dsn =
        """
        (pcb nettie (resolution um 1) (unit um)
          (structure (layer top (type signal)) (layer bottom (type signal))
            (boundary (rect pcb 0 0 40000 20000)) (via V)
            (rule (width 508) (clearance 254)))
          (placement
            (component T (place R3 5000 5000 front 0))
            (component P (place A1 20000 5000 front 0) (place B1 20000 12000 front 0)))
          (library
            (image T (pin R 2 533.4 0) (pin R 1 -533.4 0) (pin S @1 0 0))
            (image P (pin Q 1 0 0))
            (padstack R (shape (rect top -254 -279.4 254 279.4)) (attach off))
            (padstack S (shape (rect top -381 -127 381 127)) (attach off))
            (padstack Q (shape (rect top -500 -500 500 500)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
          (network (net GND (pins R3-1 A1-1)) (net /SOURCE (pins R3-2 B1-1))))
        """;
    RoutingBoard board = createBoardFromDsn(dsn);
    Pin tie = null;
    for (Pin p : board.getPins()) {
      if ("@1".equals(p.name())) {
        tie = p;
        break;
      }
    }
    assertNotNull(tie);
    assertEquals(0, tie.netCount());

    int assigned = board.connectNetlessPadsToTheirFootprintsNet();
    assertEquals(1, assigned);
    // Bridging pad became a net tie carrying both nets
    assertEquals(2, tie.netCount());
  }

  @Test
  void testClearanceBaselineDetectsNewViolations_FR096() throws IOException {
    String dsn =
        """
        (pcb test (resolution um 1) (unit um)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 40000 40000))
            (via V)
            (rule (width 200) (clearance 500)))
          (placement (component R (place R1 10000 10000 front 0)))
          (library
            (image R (pin P 1 0 0) (pin P 2 400 0))
            (padstack P (shape (circle top 300)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
          (network (net N1 (pins R1-1)) (net N2 (pins R1-2))))
        """;
    RoutingBoard board = createBoardFromDsn(dsn);
    app.freerouting.drc.DesignRulesChecker drc =
        new app.freerouting.drc.DesignRulesChecker(board, null);
    Collection<ClearanceViolation> initialViolations = drc.getAllClearanceViolations();
    assertEquals(1, initialViolations.size());

    ClearanceBaseline baseline = new ClearanceBaseline(initialViolations);
    assertEquals(0, baseline.countNewOrChanged(initialViolations));

    // Insert a via creating another violation with pin R1-2 (net 2)
    Padstack viaPadstack = board.library.padstacks.get(2);
    board.insertVia(
        viaPadstack, Point.getInstance(10200, 10000), new int[] {1}, 1, FixedState.UNFIXED, true);
    Collection<ClearanceViolation> laterViolations = drc.getAllClearanceViolations();
    assertTrue(laterViolations.size() > 1);
    assertTrue(baseline.countNewOrChanged(laterViolations) >= 1);
  }

  @Test
  void testItemConnectionCycleDetectionTerminates_FR061() throws IOException {
    String dsn =
        """
        (pcb ring (resolution um 1) (unit um)
          (structure
            (layer top (type signal))
            (layer bottom (type signal))
            (boundary (rect pcb 0 0 40000 40000))
            (via V)
            (rule (width 200) (clearance 200)))
          (placement (component R (place R1 10000 10000 front 0)))
          (library
            (image R (pin P 1 0 0))
            (padstack P (shape (circle top 800)) (attach off))
            (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
          (network (net N (pins R1-1))))
        """;
    RoutingBoard board = createBoardFromDsn(dsn);
    // Create a square ring of 4 traces
    Point p1 = Point.getInstance(10000, 10000);
    Point p2 = Point.getInstance(20000, 10000);
    Point p3 = Point.getInstance(20000, 20000);
    Point p4 = Point.getInstance(10000, 20000);
    PolylineTrace t1 =
        board.insertTraceWithoutCleaning(
            new Polyline(new Point[] {p1, p2}), 0, 100, new int[] {1}, 0, FixedState.UNFIXED);
    PolylineTrace t2 =
        board.insertTraceWithoutCleaning(
            new Polyline(new Point[] {p2, p3}), 0, 100, new int[] {1}, 0, FixedState.UNFIXED);
    PolylineTrace t3 =
        board.insertTraceWithoutCleaning(
            new Polyline(new Point[] {p3, p4}), 0, 100, new int[] {1}, 0, FixedState.UNFIXED);
    PolylineTrace t4 =
        board.insertTraceWithoutCleaning(
            new Polyline(new Point[] {p4, p1}), 0, 100, new int[] {1}, 0, FixedState.UNFIXED);

    // getConnectionItems on t1 must terminate and contain all 4 traces
    Set<Item> ringItems = t1.getConnectionItems(Item.StopConnectionOption.NONE);
    assertEquals(4, ringItems.size());
  }
}

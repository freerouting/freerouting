package app.freerouting.autoroute.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.geometry.planar.IntPoint;
import app.freerouting.geometry.planar.Point;
import app.freerouting.geometry.planar.Polyline;
import app.freerouting.io.BoardReadResult;
import app.freerouting.io.specctra.DsnReader;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DanglingCopper}: the cleanup before the session is written removes vias with copper on one
 * layer only and traces with a free end, with the stub behind them, keeps fixed copper and through
 * connections, and leaves the open count as it was.
 */
class DanglingCopperTest {

  // Net N1 joins the SMD pins A-1 (3000, 3000) and B-1 (17000, 3000) on top; net N2 joins D-1 and
  // E-1 at y 7000.
  private static final String DSN =
      """
      (pcb dangling (resolution um 1) (unit um)
        (structure (layer top (type signal)) (layer bottom (type signal))
          (boundary (rect pcb 0 0 20000 10000)) (via V) (rule (width 200) (clearance 100)))
        (placement (component C (place A 3000 3000 front 0) (place B 17000 3000 front 0)
          (place D 3000 7000 front 0) (place E 17000 7000 front 0)))
        (library (image C (pin S 1 0 0)) (padstack S (shape (circle top 600)))
          (padstack V (shape (circle top 600)) (shape (circle bottom 600)) (attach off)))
        (network (net N1 (pins A-1 B-1)) (net N2 (pins D-1 E-1))))
      """;

  private RoutingBoard board;

  @BeforeEach
  void setUp() {
    ByteArrayInputStream stream = new ByteArrayInputStream(DSN.getBytes(StandardCharsets.UTF_8));
    BoardReadResult result = DsnReader.readBoard(stream, null, null);
    assertInstanceOf(BoardReadResult.Success.class, result);
    this.board = (RoutingBoard) ((BoardReadResult.Success) result).board();
  }

  private IntPoint p(double x, double y) {
    return board.communication.coordinateTransform.dsnToBoard(new double[] {x, y}).round();
  }

  private int net(String name) {
    return board.rules.nets.get(name, 1).netNumber;
  }

  private Point centre(String component) {
    return pin(component).getCenter();
  }

  private Pin pin(String component) {
    return board.getPins().stream()
        .filter(p -> board.components.get(p.getComponentId()).name.equals(component))
        .findFirst()
        .orElseThrow();
  }

  private PolylineTrace trace(int layer, int net, FixedState fixedState, Point... corners) {
    return (PolylineTrace)
        board.insertTraceWithoutCleaning(
            new Polyline(corners), layer, 100, new int[] {net}, 1, fixedState);
  }

  private Via via(int net, IntPoint centre, FixedState fixedState) {
    return board.insertVia(
        board.library.getViaPadstack("V"), centre, new int[] {net}, 1, fixedState, false);
  }

  private Via via(int net, IntPoint centre) {
    return via(net, centre, FixedState.UNFIXED);
  }

  private int open() {
    return new DesignRulesChecker(board, null).getIncompleteCount();
  }

  @Test
  void viaWithCopperOnOneLayerOnlyGoesWithItsStub() {
    int n1 = net("N1");
    PolylineTrace stub = trace(0, n1, FixedState.UNFIXED, centre("A"), p(8000, 5000));
    Via via = via(n1, p(8000, 5000));
    assertTrue(via.isTail());
    assertFalse(stub.isTail());
    int openCount = open();

    String line = DanglingCopper.remove(board, "the test");

    assertNotNull(line);
    assertTrue(line.contains("removed 1 via(s) and 1 trace piece(s)"));
    assertFalse(via.isOnTheBoard());
    assertFalse(stub.isOnTheBoard());
    assertTrue(pin("A").isOnTheBoard());
    assertEquals(openCount, open());
  }

  @Test
  void viaCarryingAThroughConnectionStays() {
    int n1 = net("N1");
    trace(0, n1, FixedState.UNFIXED, centre("A"), p(8000, 3000));
    Via first = via(n1, p(8000, 3000));
    trace(1, n1, FixedState.UNFIXED, p(8000, 3000), p(12000, 3000));
    Via second = via(n1, p(12000, 3000));
    trace(0, n1, FixedState.UNFIXED, p(12000, 3000), centre("B"));
    assertEquals(1, open()); // N2 stays open
    assertFalse(first.isTail());
    assertFalse(second.isTail());

    assertNull(DanglingCopper.remove(board, "the test"));

    assertTrue(first.isOnTheBoard());
    assertTrue(second.isOnTheBoard());
    assertEquals(2, board.getVias().size());
    assertEquals(3, board.getTraces().size());
    assertEquals(1, open()); // N2 stays open
  }

  @Test
  void traceWithAFreeEndGoesAndTheConnectionStays() {
    int n1 = net("N1");
    PolylineTrace route = trace(0, n1, FixedState.UNFIXED, centre("A"), centre("B"));
    PolylineTrace tail = trace(0, n1, FixedState.UNFIXED, centre("B"), p(17000, 6000));
    assertEquals(1, open()); // N2 stays open
    assertTrue(tail.isTail());
    assertFalse(route.isTail());

    String line = DanglingCopper.remove(board, "the test");

    assertNotNull(line);
    assertTrue(line.contains("removed 0 via(s) and 1 trace piece(s)"));
    assertFalse(tail.isOnTheBoard());
    assertTrue(route.isOnTheBoard());
    assertEquals(1, open()); // N2 stays open
  }

  @Test
  void fixedCopperStays() {
    int n1 = net("N1");
    PolylineTrace placed = trace(0, n1, FixedState.USER_FIXED, centre("A"), p(8000, 5000));
    Via via = via(n1, p(8000, 5000), FixedState.USER_FIXED);
    PolylineTrace lone = trace(1, net("N2"), FixedState.USER_FIXED, p(6000, 9000), p(9000, 9000));
    int openCount = open();

    assertNull(DanglingCopper.remove(board, "the test"));

    assertTrue(placed.isOnTheBoard());
    assertTrue(via.isOnTheBoard());
    assertTrue(lone.isOnTheBoard());
    assertEquals(openCount, open());
  }

  @Test
  void removesIterativelyBehindAFork() {
    int n1 = net("N1");
    PolylineTrace toFirst = trace(0, n1, FixedState.UNFIXED, centre("A"), p(8000, 3000));
    Via first = via(n1, p(8000, 3000));
    PolylineTrace between = trace(1, n1, FixedState.UNFIXED, p(8000, 3000), p(12000, 3000));
    Via fork = via(n1, p(12000, 3000));
    PolylineTrace branchTop = trace(0, n1, FixedState.UNFIXED, p(12000, 3000), p(14000, 3000));
    PolylineTrace branchBottom = trace(1, n1, FixedState.UNFIXED, p(12000, 3000), p(12000, 6000));
    assertFalse(fork.isTail());
    assertFalse(first.isTail());
    int openCount = open();

    String line = DanglingCopper.remove(board, "the test");

    assertNotNull(line);
    assertTrue(line.contains("removed 2 via(s) and 4 trace piece(s)"));
    assertTrue(line.contains("2 pass(es)"));
    for (Item item : new Item[] {toFirst, first, between, fork, branchTop, branchBottom}) {
      assertFalse(item.isOnTheBoard());
    }
    assertEquals(openCount, open());
    assertTrue(board.getVias().isEmpty());
    assertTrue(board.getTraces().isEmpty());
  }
}

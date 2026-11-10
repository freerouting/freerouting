package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Via;
import app.freerouting.board.model.structure.FixedState;
import app.freerouting.board.trace.PolylineTrace;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.io.specctra.DsnTestFixtures;
import app.freerouting.rules.Net;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * {@code Plane-fork-vias.dsn}: pads joined through two vias and an inner-layer trace, both vias
 * passing through the plane of their net, as the router builds them for the plane pins of {@code
 * Issue145-smoothieboard.dsn}.
 *
 * <p>{@code Plane-cycle-tail-via.dsn}: a pad reaches the plane through a via, and an inner-layer
 * stub runs from that via to a closed loop of two traces (of different widths, so they are not
 * combined into one). Removing the loop leaves the stub as a new tail.
 */
class TraceCycleThroughPlaneTest {

  @Test
  void removingACycleThroughThePlaneKeepsTheVias() throws IOException {
    RoutingBoard board = loadUnfixed("Plane-fork-vias.dsn");
    Net gnd = board.rules.nets.get("GND").iterator().next();
    assertEquals(8, viaCount(board));
    assertEquals(12, traceCount(board));

    // The inner-layer traces close a cycle through the plane and go; the vias still join the
    // pad traces to the plane.
    board.normalizeTraces(gnd.netNumber);

    assertEquals(8, viaCount(board));
    assertEquals(8, traceCount(board), "The four inner-layer cycle traces must be removed");
    assertNoIncompletes(board);
  }

  @Test
  void removingTheTailLeftByACycleKeepsThePlaneVia() throws IOException {
    RoutingBoard board = loadUnfixed("Plane-cycle-tail-via.dsn");
    Net gnd = board.rules.nets.get("GND").iterator().next();
    assertEquals(2, viaCount(board));
    assertEquals(5, traceCount(board));

    // The inner-layer loop is a cycle and goes, then the stub left dangling goes with it. The via
    // at the far end of the stub still joins the pad trace to the plane and must stay.
    board.normalizeTraces(gnd.netNumber);

    assertEquals(2, viaCount(board));
    assertEquals(2, traceCount(board), "Only the two pad traces must remain");
    assertNoIncompletes(board);
  }

  private static RoutingBoard loadUnfixed(String fileName) throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard(fileName);
    // The DSN wiring loads fixed; the router's own connections are not.
    for (Item item : board.getItems()) {
      if (item instanceof Via || item instanceof PolylineTrace) {
        item.setFixedState(FixedState.UNFIXED);
      }
    }
    return board;
  }

  private static void assertNoIncompletes(RoutingBoard board) {
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    assertEquals(0, drc.getIncompleteCount(), "Every pad must stay connected to the plane");
  }

  private static long viaCount(RoutingBoard board) {
    return board.getItems().stream().filter(Via.class::isInstance).count();
  }

  private static long traceCount(RoutingBoard board) {
    return board.getItems().stream().filter(PolylineTrace.class::isInstance).count();
  }
}

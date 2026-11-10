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
 */
class TraceCycleThroughPlaneTest {

  @Test
  void removingACycleThroughThePlaneKeepsTheVias() throws IOException {
    RoutingBoard board = DsnTestFixtures.loadBoard("Plane-fork-vias.dsn");
    Net gnd = board.rules.nets.get("GND").iterator().next();
    assertEquals(8, viaCount(board));
    // The DSN wiring loads fixed; the router's own connections are not.
    for (Item item : board.getItems()) {
      if (item instanceof Via || item instanceof PolylineTrace) {
        item.setFixedState(FixedState.UNFIXED);
      }
    }

    // The inner-layer traces close a cycle through the plane and go; the vias still join the
    // pad traces to the plane.
    board.normalizeTraces(gnd.netNumber);

    assertEquals(8, viaCount(board));
    DesignRulesChecker drc = new DesignRulesChecker(board, null);
    drc.calculateAllIncompletes();
    assertEquals(0, drc.getIncompleteCount(), "Every pad must stay connected to the plane");
  }

  private static long viaCount(RoutingBoard board) {
    return board.getItems().stream().filter(Via.class::isInstance).count();
  }
}

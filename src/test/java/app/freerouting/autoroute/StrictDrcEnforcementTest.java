package app.freerouting.autoroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import app.freerouting.board.Item;
import app.freerouting.board.RoutingBoard;
import app.freerouting.board.Trace;
import app.freerouting.board.Via;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.io.specctra.DsnTestFixtures;
import org.junit.jupiter.api.Test;

/**
 * Unit-level checks of the strict-DRC rip logic against a fixture that ships with real
 * clearance violations in its wiring (Issue575 BBD Mars-64 snapshot, 76 violations).
 */
class StrictDrcEnforcementTest {

  private static final String FIXTURE = "Issue575-drc_BBD_Mars-64_6_track_1_hole_clearance_violations.dsn";

  /** Finds a net whose traces/vias include at least one clearance violation. */
  private static int violatingNet(RoutingBoard board) {
    for (Item item : board.get_items()) {
      if ((item instanceof Trace || item instanceof Via) && item.net_count() > 0
          && !item.clearance_violations().isEmpty()) {
        return item.get_net_no(0);
      }
    }
    return -1;
  }

  @Test
  void ripsNewItemsWhenTheyCarryViolations() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(FIXTURE);
    int netNo = violatingNet(board);
    assumeTrue(netNo > 0, "fixture must contain a violating routed net");
    // DSN-imported wiring is fixed; freshly routed items never are. Unfix so the rip
    // behaves as it does for router-inserted items.
    for (Item item : board.get_items()) {
      if ((item instanceof Trace || item instanceof Via) && item.contains_net(netNo)) {
        item.set_fixed_state(app.freerouting.board.FixedState.UNFIXED);
      }
    }
    long tracesBefore = board.get_items().stream()
        .filter(it -> it instanceof Trace && it.contains_net(netNo)).count();
    assumeTrue(tracesBefore > 0);

    // Treat the whole net's wiring as "newly inserted" (max id 0): the rip must fire.
    AutorouteAttemptResult result = BatchAutorouter.enforceStrictDrc(board, netNo, 0);

    assertTrue(result != null, "violating connection must be rejected");
    assertEquals(AutorouteAttemptState.FAILED, result.state);
    long tracesAfter = board.get_items().stream()
        .filter(it -> it instanceof Trace && it.contains_net(netNo)).count();
    assertTrue(tracesAfter < tracesBefore, "violating wiring must have been removed");
  }

  @Test
  void keepsConnectionsWhoseNewItemsAreClean() throws Exception {
    RoutingBoard board = DsnTestFixtures.loadBoard(FIXTURE);
    int netNo = violatingNet(board);
    assumeTrue(netNo > 0);
    int maxId = board.communication.id_no_generator.max_generated_no();
    BoardStatistics before = new BoardStatistics(board);

    // Nothing is newer than maxId, so nothing may be ripped regardless of violations.
    assertNull(BatchAutorouter.enforceStrictDrc(board, netNo, maxId));
    BoardStatistics after = new BoardStatistics(board);
    assertEquals(before.clearanceViolations.totalCount, after.clearanceViolations.totalCount);
  }
}

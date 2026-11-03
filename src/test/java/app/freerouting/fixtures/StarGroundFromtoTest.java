package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.model.items.Pin;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.core.scoring.BoardStatistics;
import app.freerouting.settings.sources.TestingSettings;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A net written as {@code (fromto)} pairs from one pin is routed as a star (issue #383).
 *
 * <p>The GND net of this board goes from R to A, B and C, which sit in a column on the other side.
 * As a plain {@code (pins)} list it is routed as a chain through B. Each {@code fromto} becomes a
 * subnet of its own, and R belongs to all three, so each of A, B and C needs its own trace to R,
 * and the three traces keep their clearance from each other up to R.
 */
class StarGroundFromtoTest extends RoutingFixtureTest {

  @Test
  void eachFromtoGetsItsOwnTraceToTheRoot() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(5);
    RoutingJob job = getRoutingJob("star-ground-fromto.dsn", ts);
    // getRoutingJob sets 100 passes over the testing settings.
    job.routerSettings.autorouter.maxPasses = 5;
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    BoardStatistics stats = new BoardStatistics(job.board);
    assertEquals(0, stats.connections.incompleteCount);
    // The branches are different nets: if two of them shared copper, that would be a violation.
    assertEquals(0, stats.clearanceViolations.totalCount);

    Pin root = pin(job, "R");
    assertEquals(3, root.netCount(), "the root carries one subnet per fromto");
    Set<Integer> leafNets = new HashSet<>();
    for (String leaf : new String[] {"A", "B", "C"}) {
      Pin pin = pin(job, leaf);
      assertEquals(1, pin.netCount());
      int net = pin.getNetNumber(0);
      leafNets.add(net);
      assertTrue(
          pin.getConnectedSet(net).contains(root), () -> leaf + " is not connected to the root");
    }
    assertEquals(3, leafNets.size(), "each leaf is on a subnet of its own");
  }

  private static Pin pin(RoutingJob job, String component) {
    return job.board.getPins().stream()
        .filter(p -> p.componentName().equals(component))
        .findFirst()
        .orElseThrow();
  }
}

package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.board.model.items.Item;
import app.freerouting.board.model.items.Pin;
import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.settings.sources.TestingSettings;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * A net can have several disjoint planes, for example two separate GND zones on the same layer. A
 * pad that touches one of them is connected to that plane only, not to the whole net: the
 * autorouter must still connect it to the pads that sit on the other plane (issue #1143).
 */
class PlaneSplitSameNetTest extends RoutingFixtureTest {

  private static TestingSettings routerOnly() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(20);
    settings.setFanoutEnabled(false);
    settings.setRouterEnabled(true);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  private static Pin pin(RoutingBoard board, String component) {
    return board.getPins().stream()
        .filter(
            p ->
                board.components.get(p.getComponentId()) != null
                    && component.equals(board.components.get(p.getComponentId()).name))
        .findFirst()
        .orElseThrow();
  }

  /**
   * {@code Plane-split-same-net-2layer.dsn} (KiCad 10.0.8 export of an invented board): two GND
   * planes on F.Cu, x 1..18 mm and x 22..39 mm, GND pin J1 on the left one and J2 on the right one,
   * B.Cu free. Both pins used to be skipped as "connected to a plane", and the connection was never
   * tried.
   */
  @Test
  void pinsOnTwoDisjointPlanesOfTheSameNetAreConnected() {
    RoutingJob job = getRoutingJob("Plane-split-same-net-2layer.dsn", routerOnly());
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    Pin j1 = pin(job.board, "J1");
    Pin j2 = pin(job.board, "J2");
    int gnd = j1.getNetNumber(0);
    Set<Item> connected = j1.getConnectedSet(gnd);
    assertTrue(connected.contains(j2), "J1 and J2 (GND, on two disjoint planes) are not connected");
    assertRoutingResult(job, "Plane-split-same-net-2layer.dsn")
        .exactIncompleteConnections(0)
        .check();
  }

  /**
   * {@code Plane-floating-fragment-2layer.dsn}: the same two GND planes, but J1 and J2 both sit on
   * the left one. The right plane is a floating fragment that no GND pin reaches, like the slivers
   * a zone fill leaves between obstacles: the net is complete without it, and nothing is routed to
   * it.
   */
  @Test
  void floatingPlaneFragmentIsNotRoutedTo() {
    RoutingJob job = getRoutingJob("Plane-floating-fragment-2layer.dsn", routerOnly());
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertEquals(0, job.board.getTraces().size(), "a trace was routed to the floating fragment");
    assertEquals(0, job.board.getVias().size(), "a via was routed to the floating fragment");
    Pin j1 = pin(job.board, "J1");
    Pin j2 = pin(job.board, "J2");
    assertTrue(
        j1.getConnectedSet(j1.getNetNumber(0)).contains(j2),
        "J1 and J2 (GND, on the same plane) are not connected");
  }
}

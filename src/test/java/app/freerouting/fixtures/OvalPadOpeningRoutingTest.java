package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.board.facade.RoutingBoard;
import app.freerouting.core.RoutingJob;
import app.freerouting.rules.Net;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * {@code Oval-pad-opening-garland.dsn}: a corridor between two keepouts, barred by two 3 x 1.5 mm
 * oval pads in a staircase. The only way through is between the round ends of the two pads, whose
 * centres line up at 67.5 degrees, where the bounding octagon of an oval reaches furthest past its
 * copper (62 um at each end). A 250 um trace with a 200 um clearance needs 650 um of copper gap,
 * 703 um across this opening for a trace at 0 or 45 degrees.
 *
 * <p>The opening is 810 um: net S passes. Measured on the octagons, it is 686 um, and the router
 * found no way through. {@code Oval-pad-opening-narrow-garland.dsn} is the control: 600 um, too
 * narrow for the trace in any case.
 */
class OvalPadOpeningRoutingTest extends RoutingFixtureTest {

  private static TestingSettings settings() {
    TestingSettings settings = new TestingSettings();
    settings.setJobTimeoutString("00:01:00");
    settings.setMaxPasses(5);
    settings.setFanoutEnabled(false);
    settings.setOptimizerEnabled(false);
    return settings;
  }

  private static long tracesOfS(RoutingBoard board) {
    long count = 0;
    for (Net net : board.rules.nets.get("S")) {
      count += board.getTraces().stream().filter(t -> t.containsNet(net.netNumber)).count();
    }
    return count;
  }

  @Test
  void aTracePassesBetweenTheRoundEndsOfTwoOvalPads() {
    RoutingJob job = runRoutingJob(getRoutingJob("Oval-pad-opening-garland.dsn", settings()));
    assertTrue(tracesOfS(job.board) > 0, "net S must pass the 810 um opening between the ovals");
  }

  @Test
  void aTooNarrowOpeningStaysClosed() {
    RoutingJob job =
        runRoutingJob(getRoutingJob("Oval-pad-opening-narrow-garland.dsn", settings()));
    assertEquals(0, tracesOfS(job.board), "net S must not pass a 600 um opening");
  }
}

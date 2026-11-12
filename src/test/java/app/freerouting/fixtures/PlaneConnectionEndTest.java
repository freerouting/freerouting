package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * {@code Issue367-Charger.dsn} keeps KiCad teardrops as planes of their nets. PGOOD is connected
 * through one of them: the router reaches it, but used to end the new trace in the clearance margin
 * around the teardrop instead of on its copper, so the connection was undone.
 */
class PlaneConnectionEndTest extends RoutingFixtureTest {

  @Test
  void connectionToATeardropEndsOnItsCopper() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(20);
    ts.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob("Issue367-Charger.dsn", ts);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertNotNull(job.board);
    int pgood = job.board.rules.nets.get("PGOOD").iterator().next().netNumber;
    DesignRulesChecker drc = new DesignRulesChecker(job.board, null);
    drc.calculateAllIncompletes();
    assertEquals(0, drc.getIncompleteCount(pgood), "PGOOD must be routed");
  }
}

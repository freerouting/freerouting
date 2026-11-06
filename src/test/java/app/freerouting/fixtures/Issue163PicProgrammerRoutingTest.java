package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import app.freerouting.core.RoutingJob;
import app.freerouting.core.RoutingJobState;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Test;

/**
 * Regression test for Issue #163 / Pic Programmer fixture routing stagnation.
 *
 * <p>Verifies that {@code Issue163-pic_programmer.dsn} routes to 100% completion (0 unrouted
 * connections) without stagnating on unchanged board hashes across passes.
 */
class Issue163PicProgrammerRoutingTest extends RoutingFixtureTest {

  private static final String FIXTURE = "Issue163-pic_programmer.dsn";

  @Test
  void testIssue163CompletesWithZeroUnrouted() {
    TestingSettings ts = new TestingSettings();
    ts.setMaxPasses(40);
    ts.setJobTimeoutString("00:01:00");

    RoutingJob job = getRoutingJob(FIXTURE, ts);
    runRoutingJob(job);

    assertEquals(RoutingJobState.COMPLETED, job.state);
    assertNotNull(job.board);

    DesignRulesChecker drc = new DesignRulesChecker(job.board, null);
    drc.calculateAllIncompletes();
    assertEquals(0, drc.getIncompleteCount(), "All connections must be routed");
  }
}

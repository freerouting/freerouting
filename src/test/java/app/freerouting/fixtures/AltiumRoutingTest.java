package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.drc.ClearanceViolation;
import app.freerouting.drc.DesignRulesChecker;
import app.freerouting.settings.sources.TestingSettings;
import java.time.Duration;
import java.util.Collection;
import org.junit.jupiter.api.Test;

/**
 * Validates end-to-end autorouting and DRC verification of a board exported by the Freerouting
 * Altium Designer integration.
 */
public class AltiumRoutingTest extends RoutingFixtureTest {

  @Test
  void testAltiumTwoLayerBenchmarkBoardRoutesToZeroViolations() {
    TestingSettings settings = new TestingSettings();
    settings.setMaxPasses(10);
    settings.setMaxItems(100);
    settings.setJobTimeoutString("00:02:00");
    settings.setCopperToEdgeClearanceUm(250.0);

    RoutingJob job = getRoutingJob("Altium-TwoLayerBenchmark.dsn", settings);
    job = runRoutingJob(job);

    // 1. Verify routing completion: 0 unrouted connections
    assertRoutingResult(job, "Altium-TwoLayerBenchmark.dsn")
        .maxIncompleteConnections(0)
        .maxDuration(Duration.ofMinutes(2))
        .check();

    // 2. Authoritative DRC check: strictly 0 clearance violations
    Collection<ClearanceViolation> violations =
        new DesignRulesChecker(job.board, null).getAllClearanceViolations();
    assertTrue(
        violations.isEmpty(),
        "Altium-exported board must produce 0 DRC violations after autorouting, found: "
            + violations.size());
  }
}

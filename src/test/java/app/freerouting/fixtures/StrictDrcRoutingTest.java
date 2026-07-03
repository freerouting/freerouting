package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * With {@code strict_drc} enabled the router must never ADD clearance violations. The CNH
 * functional tester fixture ships with 16 pre-existing violations in its design (present at
 * DSN load, before any routing) — strict mode cannot remove those, but the routed result
 * must not exceed them, and completion must stay reasonable.
 */
public class StrictDrcRoutingTest extends RoutingFixtureTest {

  @Test
  @Tag("slow")
  void strictDrcKeepsBoardViolationFree() {
    var testingSettings = new TestingSettings();
    testingSettings.setStrictDrc(true);
    testingSettings.setJobTimeoutString("00:05:00");
    RoutingJob job = GetRoutingJob("Issue555-CNH_Functional_Tester_1.dsn", testingSettings);

    job = RunRoutingJob(job);

    assertRoutingResult(job, "Issue555-CNH_Functional_Tester_1.dsn")
        .exactClearanceViolations(16) // the fixture's pre-existing violations, none added
        .maxIncompleteConnections(30)
        .check();
  }
}

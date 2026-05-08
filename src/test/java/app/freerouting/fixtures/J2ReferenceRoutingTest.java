package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class J2ReferenceRoutingTest extends RoutingFixtureTest {

  @Test
  void test_Issue_026_Autorouter_interrupted_and_connections_not_found() {
    var job = GetRoutingJob("Issue026-J2_reference.dsn");

    job = RunRoutingJob(job);

    var statsAfter = GetBoardStatistics(job);

    // The drill item count is a board-specific guard unrelated to routing quality;
    // keep as a standalone check since assertRoutingResult does not cover it.
    assertTrue(statsAfter.items.drillItemCount < 60,
        "The drill item count should be less than 60, but was " + statsAfter.items.drillItemCount + ".");

    // The board has 3 items that are genuinely hard to route due to tight geometry;
    // the router should stop promptly (stagnation detection) rather than loop endlessly.
    assertRoutingResult(job, "Issue026-J2_reference.dsn")
        .maxPasses(99)
        .maxIncompleteConnections(3)
        .exactClearanceViolations(0)
        .check();
  }
}
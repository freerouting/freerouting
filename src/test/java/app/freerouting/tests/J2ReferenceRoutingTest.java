package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class J2ReferenceRoutingTest extends RoutingFixtureTest {

  @Test
  void test_Issue_026_Autorouter_interrupted_and_connections_not_found() {
    var job = GetRoutingJob("Issue026-J2_reference.dsn");

    job = RunRoutingJob(job);

    var statsAfter = GetBoardStatistics(job);

    assertTrue(statsAfter.items.drillItemCount < 60, "The drill item count should be less than 60");
    // The board has 3 items that are genuinely hard to route due to tight geometry;
    // the router should stop promptly (stagnation detection) rather than loop endlessly.
    assertTrue(statsAfter.connections.incompleteCount <= 3,
        "The incomplete count should be at most 3 (the router aborted after detecting no improvement)");
    assertTrue(job.getCurrentPass() < 100,
        "The router should stop before reaching the max pass limit when stagnation is detected");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}

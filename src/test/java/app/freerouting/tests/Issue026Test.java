package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class Issue026Test extends TestBasedOnAnIssue {

  @Test
  void test_Issue_026_Autorouter_interrupted_and_connections_not_found() {
    // Fails with 12345L, passes with 123456789L
    var job = GetRoutingJob("Issue026-J2_reference.dsn", 123456789L);

    job = RunRoutingJob(job, job.routerSettings);

    var statsAfter = GetBoardStatistics(job);

    assertTrue(statsAfter.items.drillItemCount < 60, "The drill item count should be less than 60");
    assertEquals(0, statsAfter.connections.incompleteCount, "The incomplete count should be 0");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}
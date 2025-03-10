package app.freerouting.tests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Issue026Test extends TestBasedOnAnIssue
{
  @Test
  void test_Issue_026_Autorouter_interrupted_and_connections_not_found()
  {
    var job = GetRoutingJob("Issue026-J2_reference.dsn");
    job = RunRoutingJob(job, settings);
    var stats = GetBoardStatistics(job);

    assertTrue(stats.items.drillItemCount < 60, "The drill item count should be less than 60");
    assertEquals(0, stats.traces.incompleteCount, "The incomplete count should be 0");
    assertEquals(0, stats.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}
package app.freerouting.tests;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Issue026Test extends TestBasedOnAnIssue
{
  @Test
  void test_Issue_026_Autorouter_interrupted_and_connections_not_found()
  {
    var job = GetRoutingJob("Issue026-J2_reference.dsn");
    job = RunRoutingJob(job, settings);
    var stats = GetBoardStatistics(job);

    assertEquals(59, stats.drillItemCount, "The drill item count should be 59");
  }
}
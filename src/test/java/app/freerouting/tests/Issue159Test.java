package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class Issue159Test extends TestBasedOnAnIssue
{
  @Test
  void test_Issue_159_Out_of_memory_error()
  {
    var job = GetRoutingJob("Issue159-setonix_2hp-pcb.dsn", 12345L);

    job = RunRoutingJob(job, job.routerSettings);

    var statsAfter = GetBoardStatistics(job);

    assertEquals(0, statsAfter.connections.incompleteCount, "The incomplete count should be 0");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
  }
}

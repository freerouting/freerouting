package app.freerouting.tests;

import app.freerouting.logger.FRLogger;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class Issue555Test extends TestBasedOnAnIssue
{
  //  @Test
  //  void test_Issue_555_Routing_performance_with_BBD_Mars_64()
  //  {
  //    System.out.println("Testing performance by routing reference board 'Issue555-BBD_Mars-64.dsn' with default settings.");
  //    System.out.println("The benchmark times for Freerouting v1.8, v1.9 and v2.0 were 29.5 minutes, 27 minutes with failure and 9 minutes with partial failure.");
  //    System.out.println("The benchmark score for Freerouting v2.1 is 976.35, completed in 3.6 minutes.");
  //    var job = GetRoutingJob("Issue555-BBD_Mars-64.dsn");
  //    job.routerSettings.jobTimeoutString = "00:15:00";
  //    job = RunRoutingJob(job, job.routerSettings);
  //
  //    if (job.output == null)
  //    {
  //      fail("Routing job failed.");
  //    }
  //    else
  //    {
  //      var bs = job.board.get_statistics();
  //      var scoreBeforeOptimization = bs.getNormalizedScore(job.routerSettings.scoring);
  //      Duration routingDuration = Duration.between(job.startedAt, job.finishedAt);
  //
  //      System.out.println("Routing was completed in " + FRLogger.formatDuration(routingDuration.toSeconds()) + " with the score of " + FRLogger.formatScore(scoreBeforeOptimization, bs.connections.incompleteCount, bs.clearanceViolations.totalCount) + ".");
  //    }
  //
  //    assertTrue(Duration
  //        .between(job.startedAt, job.finishedAt)
  //        .compareTo(Duration.ofMinutes(5)) < 0, "Routing of the reference board 'BBD_Mars-64.dsn' should complete within 5 minutes.");
  //  }


  @Test
  void test_Issue_555_Routing_performance_with_CNH_Functional_Tester_1()
  {
    System.out.println("Testing performance by routing reference board 'Issue555-CNH_Functional_Tester_1.dsn' with default settings.");
    System.out.println("The benchmark times for Freerouting v1.8, v1.9 and v2.0 were 12 seconds (4 unrouted), 10 seconds (4 unrouted) and 11 seconds (4 unrouted) in 6 passes.");
    System.out.println("The benchmark score for Freerouting v2.1 is 962.18 (6 unrouted), completed in 54 seconds, hitting the 40 pass limit.");
    var job = GetRoutingJob("Issue555-CNH_Functional_Tester_1.dsn");
    job.routerSettings.jobTimeoutString = "00:03:00";
    job.routerSettings.maxPasses = 40;
    job = RunRoutingJob(job, job.routerSettings);

    if (job.output == null)
    {
      fail("Routing job failed.");
    }
    else
    {
      var bs = job.board.get_statistics();
      var scoreBeforeOptimization = bs.getNormalizedScore(job.routerSettings.scoring);
      Duration routingDuration = Duration.between(job.startedAt, job.finishedAt);

      System.out.println("Routing was completed in " + FRLogger.formatDuration(routingDuration.toSeconds()) + " with the score of " + FRLogger.formatScore(scoreBeforeOptimization, bs.connections.incompleteCount, bs.clearanceViolations.totalCount) + ".");
    }

    // Check if we could finish within 1 minute
    assertTrue(Duration
        .between(job.startedAt, job.finishedAt)
        .compareTo(Duration.ofMinutes(1)) < 0, "Routing of the reference board 'Issue555-CNH_Functional_Tester_1.dsn' should complete within 1 minute.");

    // Check if we could finish within 40 passes
    assertTrue(job.routerSettings.get_stop_pass_no() < 40, "Routing of the reference board 'Issue555-CNH_Functional_Tester_1.dsn' should complete within 40 passes.");

    // Check if we have at most 6 unrouted connections
    assertTrue(job.board.get_statistics().connections.incompleteCount <= 6, "Routing of the reference board 'Issue555-CNH_Functional_Tester_1.dsn' should leave at most 6 unrouted connections.");
  }
}
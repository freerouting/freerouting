package app.freerouting.tests;

import app.freerouting.logger.FRLogger;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PerformanceTest extends TestBasedOnAnIssue
{
  @Test
  void testRoutingPerformance()
  {
    System.out.println("Testing performance by routing reference board 'BBD_Mars-64.dsn' with default settings.");
    System.out.println("The benchmark times for Freerouting v1.8, v1.9 and v2.0 were 29.5 minutes, 27 minutes with failure and 9 minutes with partial failure.");
    System.out.println("The benchmark score for Freerouting v2.1 is 976.35, completed in 3.6 minutes.");
    var job = GetRoutingJob("BBD_Mars-64.dsn");
    job.routerSettings.jobTimeoutString = "00:15:00";
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

    assertTrue(Duration
        .between(job.startedAt, job.finishedAt)
        .compareTo(Duration.ofMinutes(5)) < 0, "Routing of the reference board 'BBD_Mars-64.dsn' should complete within 5 minutes.");
  }
}
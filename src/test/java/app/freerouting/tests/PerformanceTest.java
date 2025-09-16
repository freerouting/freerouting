package app.freerouting.tests;

import app.freerouting.logger.FRLogger;
import org.junit.jupiter.api.Test;

import java.time.Duration;

public class PerformanceTest extends TestBasedOnAnIssue
{
  @Test
  void testRoutingPerformance()
  {
    System.out.println("Testing routing performance by routing 'Issue326-Mars-64-revE.dsn' with default settings.");
    System.out.println("The benchmark score for v2.1 is 976.35, completed in 3.6 minutes.");
    for (int i = 0; i < 3; i++)
    {
      var job = GetRoutingJob("Issue326-Mars-64-revE.dsn");
      job = RunRoutingJob(job, job.routerSettings);

      if (job.output == null)
      {
        System.out.println("Run #" + (i + 1) + " failed.");
        continue;
      }

      var bs = job.output.statistics;
      var scoreBeforeOptimization = bs.getNormalizedScore(job.routerSettings.scoring);
      Duration routingDuration = Duration.between(job.startedAt, job.finishedAt);

      System.out.println("Run #" + (i + 1) + " was completed in " + FRLogger.formatDuration(routingDuration.toSeconds()) + " with the score of " + FRLogger.formatScore(scoreBeforeOptimization, bs.connections.incompleteCount, bs.clearanceViolations.totalCount) + ".");
    }
  }
}
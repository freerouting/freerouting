package app.freerouting.tests;

import app.freerouting.core.events.RoutingJobUpdatedEvent;
import app.freerouting.core.events.RoutingJobUpdatedEventListener;
import app.freerouting.logger.FRLogger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

public class PerformanceTest extends TestBasedOnAnIssue
{
  @Test
  void testRoutingPerformance()
  {
    System.out.println("[" + Instant.now() + "] Testing routing performance by routing 'Issue326-Mars-64-revE.dsn' with default settings.");
    System.out.println("[" + Instant.now() + "] The benchmark score for v2.1 is 976.35, completed in 3.6 minutes.");
    for (int i = 0; i < 3; i++)
    {
      var job = GetRoutingJob("Issue326-Mars-64-revE.dsn");
      job.addOutputUpdatedEventListener(new RoutingJobUpdatedEventListener()
      {
        Instant lastUpdate = Instant
            .now()
            .minusMillis(60 * 60 * 1000);

        @Override
        public void onRoutingJobUpdated(RoutingJobUpdatedEvent event)
        {
          if (Duration
              .between(lastUpdate, Instant.now())
              .toSeconds() < 30)
          {
            return;
          }

          var stats = event.getJob().board.get_statistics();
          System.out.println("[" + Instant.now() + "] Routing is in progress... Current score: " + FRLogger.formatScore(stats.getNormalizedScore(event.getJob().routerSettings.scoring), stats.connections.incompleteCount, stats.clearanceViolations.totalCount) + ".");
          System.out.flush();
          lastUpdate = Instant.now();
        }
      });
      job = RunRoutingJob(job, job.routerSettings);

      if (job.output == null)
      {
        System.out.println("[" + Instant.now() + "] Run #" + (i + 1) + " failed.");
        continue;
      }

      var bs = job.output.statistics;
      var scoreBeforeOptimization = bs.getNormalizedScore(job.routerSettings.scoring);
      Duration routingDuration = Duration.between(job.startedAt, job.finishedAt);

      System.out.println("[" + Instant.now() + "] Run #" + (i + 1) + " was completed in " + FRLogger.formatDuration(routingDuration.toSeconds()) + " with the score of " + FRLogger.formatScore(scoreBeforeOptimization, bs.connections.incompleteCount, bs.clearanceViolations.totalCount) + ".");
    }
  }
}
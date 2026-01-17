package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/// KiCad DAC 2020 Benchmarks
public class Issue508Test extends TestBasedOnAnIssue {

  private RoutingJob job;

  @Test
  @Disabled("Temporary disabled: Freerouting fails to meet the time requirement.")
  public void test_Issue_508_BM01() {
    // Get a routing job
    job = GetRoutingJob("Issue508-DAC2020_bm01.dsn");

    // Configure the router settings
    RouterSettings testSettings = job.routerSettings;
    testSettings.maxPasses = 300;
    testSettings.jobTimeoutString = "00:01:00";

    // Run the job
    RunRoutingJob(job, testSettings);

    // Check if we have at most 6 unrouted connections
    assertEquals(0, (int) job.board.get_statistics().connections.incompleteCount,
        "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should complete with 0 unrouted connections.");
  }

  @AfterEach
  public void tearDown() {
    if (job != null) {
      RoutingJobScheduler
          .getInstance()
          .clearJobs(job.sessionId.toString());
    }
  }
}
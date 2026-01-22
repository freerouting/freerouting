package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/// KiCad DAC 2020 Benchmarks
public class Issue508Test extends TestBasedOnAnIssue {

  private RoutingJob job;

  @Test
  public void test_Issue_508_BM01_first_pass_only() {
    // Get a routing job
    job = GetRoutingJob("Issue508-DAC2020_bm01.dsn");

    // Configure the router settings
    RouterSettings testSettings = job.routerSettings;
    testSettings.maxPasses = 1;
    testSettings.jobTimeoutString = "00:00:30";

    // Run the job
    RunRoutingJob(job, testSettings);

    assertTrue(job.board.get_statistics().connections.incompleteCount <= 86,
        "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should complete no more than 86 unrouted connections after the first pass.");
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
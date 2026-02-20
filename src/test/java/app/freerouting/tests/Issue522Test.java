package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class Issue522Test extends TestBasedOnAnIssue {

  private RoutingJob job;

  @Test
  public void test_Issue_522_Max_passes_setting_is_respected() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setMaxPasses(2);

    // Get a routing job
    job = GetRoutingJob("Issue026-J2_reference.dsn", testingSettings);

    // Run the job
    RunRoutingJob(job);

    // Verify that the maxPasses setting was respected
    assertEquals(2, job.getCurrentPass(), "The routing job should stop after 2 passes.");
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
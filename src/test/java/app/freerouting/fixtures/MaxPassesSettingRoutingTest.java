package app.freerouting.fixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.jobs.RoutingJobScheduler;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MaxPassesSettingRoutingTest extends RoutingFixtureTest {

  private RoutingJob job;

  @Test
  void issue522MaxPassesSettingIsRespected() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setMaxPasses(2);

    // Get a routing job
    job = getRoutingJob("Issue026-J2_reference.dsn", testingSettings);

    // Run the job
    runRoutingJob(job);

    // Verify that the maxPasses setting was respected
    assertEquals(2, job.getCurrentPass(), "The routing job should stop after 2 passes.");
  }

  @AfterEach
  void tearDown() {
    if (job != null) {
      RoutingJobScheduler.getInstance().clearJobs(job.sessionId.toString());
    }
  }
}

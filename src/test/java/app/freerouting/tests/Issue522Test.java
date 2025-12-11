package app.freerouting.tests;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Issue522Test extends TestBasedOnAnIssue
{

  private RoutingJob job;

  @Test
  public void test_Issue_522_Max_passes_setting_is_respected()
  {
    // Get a routing job
    job = GetRoutingJob("Issue026-J2_reference.dsn");

    // Configure the router settings
    RouterSettings testSettings = job.routerSettings;
    testSettings.maxPasses = 2;
    testSettings.set_stop_pass_no(testSettings.get_start_pass_no() + testSettings.maxPasses - 1);

    int startPassNoBefore = testSettings.get_start_pass_no();

    // Run the job
    RunRoutingJob(job, testSettings);

    int startPassNoAfter = job.routerSettings.get_start_pass_no();
    int passesRun = startPassNoAfter - startPassNoBefore;

    assertEquals(2, passesRun, "The number of autorouter passes run should be 2.");
  }

  @AfterEach
  public void tearDown()
  {
    if (job != null)
    {
      RoutingJobScheduler
          .getInstance()
          .clearJobs(job.sessionId.toString());
    }
  }
}
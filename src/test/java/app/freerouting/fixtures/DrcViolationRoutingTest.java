package app.freerouting.fixtures;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class DrcViolationRoutingTest extends RoutingFixtureTest {

  private RoutingJob job;

  @Test
  @Disabled("Temporary disabled: KiCad and Freerouting DRC don't agree.")
  public void test_Issue_575_6_track_and_1_hole_clearance_violations() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setEnabled(false);

    job = GetRoutingJob("Issue575-drc_BBD_Mars-64_6_track_1_hole_clearance_violations.dsn", testingSettings);
    RunRoutingJob(job);

    assertRoutingResult(job, "Issue575-drc_BBD_Mars-64_6_track_1_hole_clearance_violations.dsn")
        .exactIncompleteConnections(0)
        .exactClearanceViolations(7)
        .check();
  }

  @Test
  @Disabled("Temporary disabled: KiCad and Freerouting DRC don't agree.")
  public void test_Issue_575_4_hole_clearance_violations() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setEnabled(false);

    job = GetRoutingJob("Issue575-drc_dev-board_4_hole_clearance_violations.dsn", testingSettings);
    RunRoutingJob(job);

    assertRoutingResult(job, "Issue575-drc_dev-board_4_hole_clearance_violations.dsn")
        .exactIncompleteConnections(0)
        .exactClearanceViolations(4)
        .check();
  }

  @Test
  @Disabled("Temporary disabled: KiCad and Freerouting DRC don't agree.")
  public void test_Issue_575_7_unconnected_items() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setEnabled(false);

    job = GetRoutingJob("Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn", testingSettings);
    RunRoutingJob(job);

    assertRoutingResult(job, "Issue575-drc_Natural_Tone_Preamp_7_unconnected_items.dsn")
        .exactIncompleteConnections(7)
        .exactClearanceViolations(0)
        .check();
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
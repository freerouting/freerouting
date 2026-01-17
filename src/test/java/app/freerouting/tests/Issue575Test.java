package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.freerouting.core.RoutingJob;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.RouterSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class Issue575Test extends TestBasedOnAnIssue {

  private RoutingJob job;

  @Test
  public void test_Issue_575_6_track_and_1_hole_clearance_violations() {
    // Get a routing job
    job = GetRoutingJob("Issue575-drc_BBD_Mars-64_6_track_1_hole_clearance_violations.dsn");

    // Configure the router settings
    RouterSettings testSettings = job.routerSettings;
    testSettings.enabled = false;

    // Run the job
    RunRoutingJob(job, testSettings);

    var statsAfter = GetBoardStatistics(job);

    assertEquals(0, statsAfter.connections.incompleteCount, "The incomplete count should be 0");
    assertEquals(7, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 7");
  }

  @Test
  @Disabled("Temporary disabled: KiCad and Freerouting DRC don't agree.")
  public void test_Issue_575_4_hole_clearance_violations() {
    // Get a routing job
    job = GetRoutingJob("Issue575-drc_dev-board_4_hole_clearance_violations.dsn");

    // Configure the router settings
    RouterSettings testSettings = job.routerSettings;
    testSettings.enabled = false;

    // Run the job
    RunRoutingJob(job, testSettings);

    var statsAfter = GetBoardStatistics(job);

    assertEquals(0, statsAfter.connections.incompleteCount, "The incomplete count should be 0");
    assertEquals(4, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 4");
  }

  @Test
  public void test_Issue_575_7_unrouted_nets() {
    // Get a routing job
    job = GetRoutingJob("Issue575-drc_Natural_Tone_Preamp_7_unrouted_nets.dsn");

    // Configure the router settings
    RouterSettings testSettings = job.routerSettings;
    testSettings.enabled = false;

    // Run the job
    RunRoutingJob(job, testSettings);

    var statsAfter = GetBoardStatistics(job);

    // KiCad's "unconnected items" definition matches Freerouting's unroutedCount
    // (nets with incomplete connections), not "nets with no routing".
    // The difference is that KiCad excludes GND and +5V which have dangling violations.
    assertEquals(9, statsAfter.nets.unroutedCount,"The unrouted net count should be 9 (Freerouting) despite having 7 in KiCad");
    assertEquals(0, statsAfter.clearanceViolations.totalCount, "The total count of clearance violations should be 0");
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
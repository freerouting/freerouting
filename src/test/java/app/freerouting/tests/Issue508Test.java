package app.freerouting.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.freerouting.Freerouting;
import app.freerouting.core.RoutingJob;
import app.freerouting.logger.FRLogger;
import app.freerouting.management.RoutingJobScheduler;
import app.freerouting.settings.sources.TestingSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/// KiCad DAC 2020 Benchmarks
/// DAC2020_bm01.dsn: There are 195 connections in total on the board
public class Issue508Test extends TestBasedOnAnIssue {

  private RoutingJob job;

  @Test
  public void testIssue508_BM01_first_2_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(2);
    testSettingsSource.setJobTimeoutString("00:00:15");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertTrue(GetBoardStatistics(job).connections.incompleteCount <= 194,
        "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 194 or less incomplete connections with the target of 2 items to route.");
  }

  @Test
  public void testIssue508_BM01_first_43_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(43);
    testSettingsSource.setJobTimeoutString("00:01:00");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertTrue(GetBoardStatistics(job).connections.incompleteCount <= 161,
            "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 161 or less incomplete connections with the target of 43 items to route.");
  }

  @Test
  public void testIssue508_BM01_first_61_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(61);
    testSettingsSource.setJobTimeoutString("00:01:00");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertTrue(GetBoardStatistics(job).connections.incompleteCount <= 147,
            "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 147 or less incomplete connections with the target of 61 items to route.");
  }

  @Test
  public void testIssue508_BM01_first_111_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(111);
    testSettingsSource.setJobTimeoutString("00:01:30");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertTrue(GetBoardStatistics(job).connections.incompleteCount <= 134,
            "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 134 or less incomplete connections with the target of 111 items to route.");
  }

  @Test
  public void testIssue508_BM01_first_151_nets_only() {
    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(151);
    testSettingsSource.setJobTimeoutString("00:03:00");

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    assertTrue(GetBoardStatistics(job).connections.incompleteCount <= 126,
            "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 126 or less incomplete connections with the target of 151 items to route.");
  }

  @Test
  public void test_Issue_508_BM01_first_pass_only() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:03:00");
    testingSettings.setMaxPasses(1);

    // Get a routing job
    job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);

    // Run the job
    RunRoutingJob(job);

    assertTrue(job.board.get_statistics().connections.incompleteCount <= 97,
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
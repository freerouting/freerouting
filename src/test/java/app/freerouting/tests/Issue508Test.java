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
public class Issue508Test extends TestBasedOnAnIssue {

  private RoutingJob job;

  @Test
  public void testIssue508_BM01_first_2_nets_only() {
    // Set console logging level to TRACE for detailed output
    System.setProperty("freerouting.logging.console.level", "TRACE");
    FRLogger.granularTraceEnabled = true;

    // Enable detailed logging and filter to Net #98
    Freerouting.globalSettings.debugSettings.enableDetailedLogging = true;
    Freerouting.globalSettings.debugSettings.filterByNet.add("98");

    // Create testing settings
    TestingSettings testSettingsSource = new TestingSettings();
    testSettingsSource.setMaxPasses(1);
    testSettingsSource.setMaxItems(2);
    testSettingsSource.setJobTimeoutString("00:00:15"); // 15 seconds timeout

    // Get the job with injected settings
    RoutingJob job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testSettingsSource);

    RunRoutingJob(job);

    // There are 195 connections in total on the board
    // If both net (99 and 98) are routed, there should be 193 incomplete connections left
    // If only net 99 is routed, there should be 194 incomplete connections left
    assertTrue(GetBoardStatistics(job).connections.incompleteCount == 193,
        "Routing of the reference board 'Issue508-DAC2020_bm01.dsn' should result in 193 incomplete connections with the target of 2 items to route.");
  }

  @Test
  public void test_Issue_508_BM01_first_pass_only() {
    TestingSettings testingSettings = new TestingSettings();
    testingSettings.setJobTimeoutString("00:00:30");
    testingSettings.setMaxPasses(1);

    // Get a routing job
    job = GetRoutingJob("Issue508-DAC2020_bm01.dsn", testingSettings);

    // Run the job
    RunRoutingJob(job);

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